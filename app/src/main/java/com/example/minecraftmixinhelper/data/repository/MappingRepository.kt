package com.example.minecraftmixinhelper.data.repository

import com.example.minecraftmixinhelper.data.local.MappingDao
import com.example.minecraftmixinhelper.data.local.MappingEntity
import com.example.minecraftmixinhelper.data.local.VersionDao
import com.example.minecraftmixinhelper.data.local.VersionEntity
import com.example.minecraftmixinhelper.data.remote.FabricApi
import com.example.minecraftmixinhelper.data.remote.ForgeNeoForgeApi
import com.example.minecraftmixinhelper.data.remote.MappingDownloader
import com.example.minecraftmixinhelper.data.remote.MojangApi
import com.example.minecraftmixinhelper.domain.service.McVersionComparator
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
        val sources = listOf<suspend () -> List<VersionEntity>>(
            { fetchMojmapVersions() },
            { fetchFabricVersions() },
            { fetchForgeVersions() },
            { fetchNeoForgeVersions() }
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
     * Mojang 官方映射（mojmap）源。
     * 过滤规则：只删去 1.13.x 系列（Mojang 未提供该系列官方映射）与 26.x 及以上
     * （MC 26+ 不再混淆，无需映射）。其余（含 1.12.2 及更早）全部保留。
     */
    private suspend fun fetchMojmapVersions(): List<VersionEntity> {
        val manifest = mojangApi.getVersionManifest()
        return manifest.versions
            .filter { it.type == "release" }
            .filter { isSupportedMcVersion(it.id) }
            .map {
                VersionEntity(
                    id = "${it.id}|mojmap",
                    version = it.id,
                    loader = "mojmap",
                    mappingType = "mojmap",
                    versionJsonUrl = it.url
                )
            }
    }

    private suspend fun fetchFabricVersions(): List<VersionEntity> {
        return fabricApi.getYarnVersions()
            .filter { it.stable }
            .filter { isSupportedMcVersion(it.gameVersion) }
            .map {
                VersionEntity(
                    id = "${it.gameVersion}|fabric",
                    version = it.gameVersion,
                    loader = "fabric",
                    mappingType = "yarn"
                )
            }
    }

    /**
     * Forge 版本形如 `1.20.1-47.2.0`，这里把 `version` 归一化为 MC 版本
     * （`1.20.1`），下载时按 MC 版本解析 Mojang 官方映射。
     */
    private suspend fun fetchForgeVersions(): List<VersionEntity> {
        return extractMavenVersions(forgeApi.getForgeMetadata())
            .mapNotNull { McVersionComparator.mcVersionOf("forge", it) }
            .filter { isSupportedMcVersion(it) }
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
     * 同样归一化为 MC 版本。
     */
    private suspend fun fetchNeoForgeVersions(): List<VersionEntity> {
        return extractMavenVersions(forgeApi.getNeoForgeMetadata())
            .mapNotNull { McVersionComparator.mcVersionOf("neoforge", it) }
            .filter { isSupportedMcVersion(it) }
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
     * 版本支持过滤：只删去 1.13.x 系列（`1.13` / `1.13.1` / `1.13.2`）与 26.x 及以上。
     */
    private fun isSupportedMcVersion(v: String): Boolean {
        if (v.startsWith("1.13")) return false
        return McVersionComparator.compare(v, "26") < 0
    }

    private fun extractMavenVersions(xml: String): List<String> =
        MAVEN_VERSION_RE.findAll(xml).map { it.groupValues[1] }.toList()

    private fun defaultVersions(): List<VersionEntity> = listOf(
        VersionEntity("1.20.1|fabric", "1.20.1", "fabric", "yarn"),
        VersionEntity("1.20.1|forge", "1.20.1", "forge", "mojmap"),
        VersionEntity("1.19.4|neoforge", "1.19.4", "neoforge", "mojmap")
    )

    // ==================== 下载并解析映射 ====================

    suspend fun downloadAndParseMappings(
        version: String,
        versionJsonUrl: String,
        mappingType: String,
        loader: String
    ) = withContext(Dispatchers.IO) {
        val parsed: List<MojmapParser.ParsedMapping> = when (mappingType) {
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

    suspend fun fuzzySearch(query: String, type: String = "ALL", version: String = ""): List<MappingEntity> {
        if (query.isBlank()) return emptyList()
        return try {
            val results = mappingDao.fuzzySearchFts(toFtsMatchQuery(query))
            filterResults(results, type, version)
        } catch (e: Exception) {
            filterResults(mappingDao.searchMappings(query).first(), type, version)
        }
    }

    /** 实时建议：与搜索同源的轻量查询，仅返回前 [limit] 条。 */
    suspend fun suggest(
        query: String,
        type: String = "ALL",
        version: String = "",
        limit: Int = 10
    ): List<MappingEntity> {
        if (query.isBlank()) return emptyList()
        return try {
            filterResults(mappingDao.suggestFts(toFtsMatchQuery(query), limit), type, version)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun filterResults(
        results: List<MappingEntity>,
        type: String,
        version: String
    ): List<MappingEntity> {
        return results.filter { m ->
            (type == "ALL" || m.type.equals(type, ignoreCase = true)) &&
                (version.isBlank() || m.version == version)
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

    // ==================== 映射类型自动决策（仅兜底） ====================

    fun decideMappingType(mcVersion: String, loader: String): String =
        McVersionComparator.decideMappingType(mcVersion, loader)

    private companion object {
        val MAVEN_VERSION_RE = Regex("""<version>(.+?)</version>""")
    }
}
