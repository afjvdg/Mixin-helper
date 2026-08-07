package com.example.minecraftmixinhelper.data.repository

import com.example.minecraftmixinhelper.data.local.MappingDao
import com.example.minecraftmixinhelper.data.local.MappingEntity
import com.example.minecraftmixinhelper.data.local.VersionDao
import com.example.minecraftmixinhelper.data.local.VersionEntity
import com.example.minecraftmixinhelper.data.local.VersionLoaderRow
import com.example.minecraftmixinhelper.data.remote.FabricApi
import com.example.minecraftmixinhelper.data.remote.ForgeNeoForgeApi
import com.example.minecraftmixinhelper.data.remote.MappingDownloader
import com.example.minecraftmixinhelper.data.remote.MojangApi
import com.example.minecraftmixinhelper.domain.service.McVersionComparator
import com.example.minecraftmixinhelper.domain.service.McpParser
import com.example.minecraftmixinhelper.domain.service.MojmapParser
import com.example.minecraftmixinhelper.domain.service.ParchmentParser
import com.example.minecraftmixinhelper.domain.service.TinyParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject

class MappingRepository @Inject constructor(
    private val mappingDao: MappingDao,
    private val versionDao: VersionDao,
    private val mojangApi: MojangApi,
    private val fabricApi: FabricApi,
    private val forgeApi: ForgeNeoForgeApi,
    private val downloader: MappingDownloader
) {

    fun getVersions(): Flow<List<VersionEntity>> = versionDao.getAllVersions()

    // ==================== 多源版本列表 ====================

    suspend fun fetchAndCacheVersions() = withContext(Dispatchers.IO) {
        // 先取 Minecraft 官方「正式版」版本列表作为权威集合，供各加载器源对照过滤，
        // 从而剔除预览版（如 1.7.10pre4）与垃圾版本号。
        val supported = getSupportedMcVersions()
        val sources = listOf<suspend () -> List<VersionEntity>>(
            { fetchFabricVersions(supported) },
            { fetchForgeVersions(supported) },
            { fetchNeoForgeVersions(supported) }
        )
        val collected = mutableListOf<VersionEntity>()
        for (src in sources) {
            try {
                collected += src()
            } catch (e: Exception) {
                // 单源失败不影响其他源
            }
        }
        if (collected.isNotEmpty()) {
            versionDao.insertAll(collected.distinctBy { it.id })
        } else {
            versionDao.insertAll(defaultVersions())
        }
    }

    /**
     * 受支持的 MC 正式版集合（作为各加载器版本列表的过滤基准）：
     * - 仅正式版（type == release，天然排除 1.7.10pre4 等预览版）
     * - 自 1.7.10 起
     * - 排除 1.13.x 系列（Mojang 未提供该系列官方映射）
     * - 排除 26.x 及以上（MC 26+ 不再混淆，无需映射）
     */
    private suspend fun getSupportedMcVersions(): Set<String> {
        val manifest = mojangApi.getVersionManifest()
        return manifest.versions
            .asSequence()
            .filter { it.type == "release" }
            .map { it.id }
            .filter { isSupportedMcVersion(it) }
            .toSet()
    }

    /** Fabric：以启动器支持的「游戏版本列表」（/v2/versions/game）为源，对照正式版集合过滤。 */
    private suspend fun fetchFabricVersions(supported: Set<String>): List<VersionEntity> {
        return fabricApi.getGameVersions()
            .filter { it.stable }
            .map { it.version }
            .filter { it in supported }
            .sortedWith { a, b -> McVersionComparator.compare(b, a) }
            .map {
                VersionEntity(
                    id = "$it|fabric",
                    version = it,
                    loader = "fabric",
                    mappingType = "yarn"
                )
            }
    }

    /**
     * Forge 版本形如 `1.20.1-47.2.0`，归一化为 MC 版本（`1.20.1`）并与正式版集合
     * 对照（剔除预览版/垃圾版本）。映射类型按版本区分：
     * - <1.17 → MCP
     * - >=1.17 → mojmap + Parchment（捆绑下载）
     */
    private suspend fun fetchForgeVersions(supported: Set<String>): List<VersionEntity> {
        return extractMavenVersions(forgeApi.getForgeMetadata())
            .mapNotNull { McVersionComparator.mcVersionOf("forge", it) }
            .filter { it in supported }
            .distinct()
            .sortedWith { a, b -> McVersionComparator.compare(b, a) }
            .map {
                VersionEntity(
                    id = "$it|forge",
                    version = it,
                    loader = "forge",
                    mappingType = McVersionComparator.decideMappingType(it, "forge")
                )
            }
    }

    /**
     * NeoForge 版本形如 `21.1.78`（对应 MC 1.21.1）、`26.2.0.49-beta`（对应 MC 26.2），
     * 归一化为 MC 版本并与正式版集合对照。1.0.3 / 1.0.4 等早期垃圾版本号会因
     * 归一化后不在正式版集合中而被剔除。始终基于 mojmap + Parchment。
     */
    private suspend fun fetchNeoForgeVersions(supported: Set<String>): List<VersionEntity> {
        return extractMavenVersions(forgeApi.getNeoForgeMetadata())
            .mapNotNull { McVersionComparator.mcVersionOf("neoforge", it) }
            .filter { it in supported }
            .distinct()
            .sortedWith { a, b -> McVersionComparator.compare(b, a) }
            .map {
                VersionEntity(
                    id = "$it|neoforge",
                    version = it,
                    loader = "neoforge",
                    mappingType = McVersionComparator.decideMappingType(it, "neoforge")
                )
            }
    }

    /**
     * 版本支持过滤：
     * - 自 1.7.10 起（更早版本各加载器/官方映射均不适用）
     * - 排除 1.13.x 系列（Mojang 未提供官方映射）
     * - 排除 26.x 及以上（MC 26+ 不再混淆）
     */
    private fun isSupportedMcVersion(v: String): Boolean {
        if (McVersionComparator.compare(v, "1.7.10") < 0) return false
        if (v.startsWith("1.13")) return false
        return McVersionComparator.compare(v, "26") < 0
    }

    private fun extractMavenVersions(xml: String): List<String> =
        MAVEN_VERSION_RE.findAll(xml).map { it.groupValues[1] }.toList()

    private fun defaultVersions(): List<VersionEntity> = listOf(
        VersionEntity("1.20.1|fabric", "1.20.1", "fabric", "yarn"),
        VersionEntity("1.20.1|forge", "1.20.1", "forge", "parchment"),
        VersionEntity("1.21.1|neoforge", "1.21.1", "neoforge", "parchment")
    )

    // ==================== 下载并解析映射 ====================

    suspend fun downloadAndParseMappings(
        version: String,
        versionJsonUrl: String,
        mappingType: String,
        loader: String
    ) = withContext(Dispatchers.IO) {
        val parsed: List<MojmapParser.ParsedMapping> = when (mappingType) {
            "mcp" -> {
                // MCP 映射（Forge <1.17）：来源为 Forge Maven（mcpbot 已下线）。
                // MCP 官方仅发布到 1.15，1.16.x 无稳定 MCP → 回退 Mojang 官方映射。
                val mcp = try {
                    val srg = downloader.downloadMcpSrg(version)
                    val csv = downloader.downloadMcpStable(version)
                    McpParser.parse(srg, csv)
                } catch (e: Exception) {
                    null
                }
                if (mcp != null) mcp else {
                    MojmapParser.parse(
                        downloader.downloadMojangMappings(
                            resolveMojangVersionJsonUrl(version, versionJsonUrl, loader)
                        )
                    )
                }
            }
            "yarn" -> {
                val rawTiny = downloader.downloadYarnMappings(version)
                TinyParser.parse(rawTiny)
            }
            "parchment" -> {
                // Forge / NeoForge 的 Parchment 数据可能缺失，优雅降级为纯 Mojmap；
                // 专门的 Parchment 源则如实报错
                val enrich = try {
                    ParchmentParser.parse(downloader.downloadParchmentJson(version))
                } catch (e: Exception) {
                    if (loader in setOf("forge", "neoforge")) null else throw e
                }
                val rawMojmap = downloader.downloadMojangMappings(
                    resolveMojangVersionJsonUrl(version, versionJsonUrl, loader)
                )
                val mojmap = MojmapParser.parse(rawMojmap)
                if (enrich != null) applyParchment(mojmap, enrich) else mojmap
            }
            else -> { // mojmap
                val rawMojmap = downloader.downloadMojangMappings(
                    resolveMojangVersionJsonUrl(version, versionJsonUrl, loader)
                )
                MojmapParser.parse(rawMojmap)
            }
        }

        val entities = parsed.map { pm ->
            MappingEntity(
                className = pm.className,
                obfuscatedName = pm.obfuscatedName,
                deobfuscatedName = pm.deobfuscatedName,
                type = pm.type,
                descriptor = pm.descriptor,
                params = pm.params.joinToString(","),
                returnType = pm.returnType,
                paramNames = pm.paramNames,
                javadoc = pm.javadoc,
                version = version,
                loader = loader
            )
        }
        mappingDao.insertAll(entities)
        versionDao.markCached(version, loader)
    }

    /**
     * 解析 Mojang 官方映射的 version.json URL：
     * - Mojmap 源在版本列表阶段已保存真实 URL，直接使用；
     * - Forge / NeoForge 源没有该 URL，按 MC 版本查一次 manifest 得到。
     */
    private suspend fun resolveMojangVersionJsonUrl(
        version: String,
        versionJsonUrl: String,
        loader: String
    ): String {
        if (versionJsonUrl.isNotBlank()) return versionJsonUrl
        if (loader !in setOf("forge", "neoforge")) {
            throw Exception("缺少 version.json URL，无法下载 Mojang 官方映射")
        }
        val manifest = mojangApi.getVersionManifest()
        val entry = manifest.versions.firstOrNull { it.type == "release" && it.id == version }
            ?: throw Exception(
                "Mojang 版本清单中未找到 $version（$loader），无法下载官方映射，" +
                    "该加载器版本可能对应过旧的 MC 版本"
            )
        return entry.url
    }

    private fun applyParchment(
        mojmap: List<MojmapParser.ParsedMapping>,
        enrich: ParchmentParser.ParchmentData
    ): List<MojmapParser.ParsedMapping> {
        return mojmap.map { pm ->
            when (pm.type) {
                "METHOD" -> {
                    val key = "${pm.className}|${pm.deobfuscatedName}|" +
                        ParchmentParser.canonicalDescriptor(pm.descriptor.orEmpty())
                    val info = enrich.byMethod[key]
                    if (info != null) pm.copy(paramNames = info.paramNames, javadoc = info.javadoc) else pm
                }
                "CLASS" -> {
                    val jd = enrich.classJavadoc[pm.className]
                    if (jd != null) pm.copy(javadoc = jd) else pm
                }
                else -> pm
            }
        }
    }

    // ==================== 搜索（FTS4 前缀 + LIKE 回退） ====================

    /**
     * 实时搜索。
     * [field] 限定搜索范围：deobf / obf / class / all（空 = 全部字段）。
     * [type] 限定类型：CLASS / METHOD / FIELD / all（空或 ALL = 全部类型，默认）。
     * 子串匹配（LIKE）保证「get」能命中 getX 等方法。
     */
    suspend fun fuzzySearch(
        query: String,
        type: String = "",
        version: String = "",
        loader: String = "",
        field: String = ""
    ): List<MappingEntity> {
        if (query.isBlank()) return emptyList()
        val raw = query.trim()
        val matched = when (field.lowercase()) {
            "deobf" -> mappingDao.searchByDeobfuscated(raw)
            "obf" -> mappingDao.searchByObfuscated(raw)
            "class" -> mappingDao.searchByClassName(raw)
            else -> {
                // 全部字段：FTS 前缀 + LIKE 子串合并
                val fts = try {
                    mappingDao.fuzzySearchFts(toFtsMatchQuery(raw))
                } catch (e: Exception) {
                    emptyList()
                }
                val like = try {
                    mappingDao.searchMappings(raw).first()
                } catch (e: Exception) {
                    emptyList()
                }
                (fts + like).distinctBy { it.id }
            }
        }
        return filterResults(matched, type, version, loader)
    }

    private fun filterResults(
        results: List<MappingEntity>,
        type: String,
        version: String,
        loader: String = ""
    ): List<MappingEntity> {
        return results.filter { m ->
            (type.isBlank() || type.equals("ALL", true) || m.type.equals(type, ignoreCase = true)) &&
                (version.isBlank() || m.version == version) &&
                (loader.isBlank() || m.loader.equals(loader, ignoreCase = true))
        }
    }

    // FTS4 前缀匹配：清洗输入 -> 逐词用双引号包裹 + 前缀 *，多词空格连接
    private fun toFtsMatchQuery(raw: String): String {
        return raw.split(Regex("""\s+"""))
            .filter { it.isNotEmpty() }
            .joinToString(" ") { word ->
                val cleaned = word.replace(Regex("""["*\\-]"""), "")
                if (cleaned.isEmpty()) "" else "\"$cleaned\"*"
            }
    }

    suspend fun getDownloadedVersions(): List<String> = mappingDao.getDownloadedVersions()

    /** 已下载的「版本 + 加载器」对（搜索页版本范围选择用）。 */
    suspend fun getDownloadedVersionLoaders(): List<VersionLoaderRow> =
        mappingDao.getDownloadedVersionLoaders()

    // ==================== 映射类型自动决策（仅兜底） ====================

    fun decideMappingType(mcVersion: String, loader: String): String =
        McVersionComparator.decideMappingType(mcVersion, loader)

    private companion object {
        val MAVEN_VERSION_RE = Regex("""<version>(.+?)</version>""")
    }
}
