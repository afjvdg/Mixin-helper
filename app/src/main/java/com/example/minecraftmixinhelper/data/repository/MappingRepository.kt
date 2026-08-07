package com.example.minecraftmixinhelper.data.repository

import com.example.minecraftmixinhelper.data.local.MappingDao
import com.example.minecraftmixinhelper.data.local.MappingEntity
import com.example.minecraftmixinhelper.data.local.VersionDao
import com.example.minecraftmixinhelper.data.local.VersionEntity
import com.example.minecraftmixinhelper.data.remote.FabricApi
import com.example.minecraftmixinhelper.data.remote.ForgeNeoForgeApi
import com.example.minecraftmixinhelper.data.remote.MappingDownloader
import com.example.minecraftmixinhelper.data.remote.MojangApi
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
            { fetchMojangVersions() },
            { fetchFabricVersions() },
            { fetchForgeVersions() },
            { fetchNeoForgeVersions() },
            { fetchParchmentVersions() }
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

    private suspend fun fetchMojangVersions(): List<VersionEntity> {
        val manifest = mojangApi.getVersionManifest()
        return manifest.versions
            .filter { it.type == "release" }
            .filter { !it.id.startsWith("1.13") }
            .take(40)
            .map {
                VersionEntity(
                    id = "${it.id}|mojang",
                    version = it.id,
                    loader = "mojang",
                    mappingType = "mojmap",
                    versionJsonUrl = it.url
                )
            }
    }

    private suspend fun fetchFabricVersions(): List<VersionEntity> {
        return fabricApi.getYarnVersions()
            .filter { it.stable }
            .take(30)
            .map {
                VersionEntity(
                    id = "${it.gameVersion}|fabric",
                    version = it.gameVersion,
                    loader = "fabric",
                    mappingType = "yarn"
                )
            }
    }

    private suspend fun fetchForgeVersions(): List<VersionEntity> {
        return extractMavenVersions(forgeApi.getForgeMetadata())
            .take(10)
            .map {
                VersionEntity(
                    id = "${it}|forge",
                    version = it,
                    loader = "forge",
                    mappingType = "mojmap"
                )
            }
    }

    private suspend fun fetchNeoForgeVersions(): List<VersionEntity> {
        return extractMavenVersions(forgeApi.getNeoForgeMetadata())
            .take(10)
            .map {
                VersionEntity(
                    id = "${it}|neoforge",
                    version = it,
                    loader = "neoforge",
                    mappingType = "mojmap"
                )
            }
    }

    private suspend fun fetchParchmentVersions(): List<VersionEntity> {
        return extractMavenVersions(forgeApi.getParchmentMetadata())
            .take(20)
            .map {
                VersionEntity(
                    id = "${it}|parchment",
                    version = it,
                    loader = "parchment",
                    mappingType = "parchment"
                )
            }
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
                val rawJson = downloader.downloadParchmentJson(version)
                val enrich = ParchmentParser.parse(rawJson)
                val rawMojmap = downloader.downloadMojangMappings(versionJsonUrl)
                applyParchment(MojmapParser.parse(rawMojmap), enrich)
            }
            else -> { // mojmap
                val rawMojmap = downloader.downloadMojangMappings(versionJsonUrl)
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

    private fun applyParchment(
        mojmap: List<MojmapParser.ParsedMapping>,
        enrich: ParchmentParser.ParchmentData
    ): List<MojmapParser.ParsedMapping> {
        return mojmap.map { pm ->
            when (pm.type) {
                "METHOD" -> {
                    val key = "${pm.className}|${pm.deobfuscatedName}|${pm.descriptor}"
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

    // ==================== 映射类型自动决策（数字元组比较，仅兜底） ====================

    fun decideMappingType(mcVersion: String, loader: String): String {
        val tuple = versionTuple(mcVersion)
        return when (loader.lowercase()) {
            "fabric" -> if (tuple >= versionTuple("1.21.11")) "mojmap" else "yarn"
            "forge", "neoforge" -> if (tuple >= versionTuple("1.18")) "parchment" else "mojmap"
            else -> "mojmap"
        }
    }

    private fun versionTuple(v: String): List<Int> =
        v.split(Regex("""[.\-]""")).map { part ->
            part.filter { it.isDigit() }.toIntOrNull() ?: 0
        }

    private companion object {
        val MAVEN_VERSION_RE = Regex("""<version>(.+?)</version>""")
    }
}
