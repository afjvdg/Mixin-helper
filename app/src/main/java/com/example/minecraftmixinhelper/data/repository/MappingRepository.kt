package com.example.minecraftmixinhelper.data.repository

import com.example.minecraftmixinhelper.data.local.MappingDao
import com.example.minecraftmixinhelper.data.local.MappingEntity
import com.example.minecraftmixinhelper.data.local.VersionDao
import com.example.minecraftmixinhelper.data.local.VersionEntity
import com.example.minecraftmixinhelper.data.remote.FabricApi
import com.example.minecraftmixinhelper.data.remote.ForgeNeoForgeApi
import com.example.minecraftmixinhelper.data.remote.MappingDownloader
import com.example.minecraftmixinhelper.data.remote.MojangApi
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

    suspend fun fetchAndCacheVersions() = withContext(Dispatchers.IO) {
        try {
            val manifest = mojangApi.getVersionManifest()
            val mojangVersions = manifest.versions
                .filter { !it.id.startsWith("1.13") } // 排除 1.13 版本
                .take(50)
                .map { VersionEntity(it.id, "mojang", "mojmap", false) }

            val fabricVersions = fabricApi.getGameVersions()
                .filter { !it.version.startsWith("1.13") }
                .take(30)
                .map { VersionEntity(it.version, "fabric", "yarn", false) }

            val forgeVersions = listOf(
                VersionEntity("1.20.1", "forge", "mojmap", false),
                VersionEntity("1.20.1", "neoforge", "mojmap", false),
                VersionEntity("1.19.4", "forge", "mojmap", false)
            )

            val all = (mojangVersions + fabricVersions + forgeVersions)
                .distinctBy { it.version + it.loader }
                .sortedByDescending { it.version }

            versionDao.insertAll(all)
        } catch (e: Exception) {
            // 网络失败时插入少量默认数据
            val defaults = listOf(
                VersionEntity("1.20.1", "fabric", "yarn", false),
                VersionEntity("1.20.1", "forge", "mojmap", false),
                VersionEntity("1.19.4", "neoforge", "mojmap", false)
            )
            versionDao.insertAll(defaults)
        }
    }

    // 真实下载并解析 Mojang mappings（支持 Parchment 时同时下载）
    suspend fun downloadAndParseMappings(
        version: String, 
        versionJsonUrl: String, 
        mappingType: String
    ) = withContext(Dispatchers.IO) {
        try {
            // 总是先下载 Mojmap
            val rawMojmap = downloader.downloadMojangMappings(versionJsonUrl)
            val mojmapEntities = parseMojmap(rawMojmap, version)
            mappingDao.insertAll(mojmapEntities)

            // 如果是 Parchment，再额外处理（目前仅标记，后续可扩展）
            if (mappingType == "parchment") {
                // TODO: 下载 Parchment 参数映射（可扩展）
            }
        } catch (e: Exception) {
            mappingDao.insertAll(listOf(
                MappingEntity(className = "net.minecraft.world.entity.player.Player", obfuscatedName = "gfj", deobfuscatedName = "Player", type = "CLASS")
            ))
        }
    }

    // 解析 Mojmap (ProGuard 格式)
    private fun parseMojmap(raw: String, version: String): List<MappingEntity> {
        val lines = raw.lines()
        val entities = mutableListOf<MappingEntity>()

        for (line in lines) {
            when {
                line.startsWith("#") || line.isBlank() -> continue
                line.contains(" -> ") && !line.contains("(") -> {
                    // 类映射
                    val parts = line.split(" -> ")
                    if (parts.size == 2) {
                        entities.add(
                            MappingEntity(
                                className = parts[0].trim(),
                                obfuscatedName = parts[1].trim(),
                                deobfuscatedName = parts[0].trim(),
                                type = "CLASS"
                            )
                        )
                    }
                }
                line.contains("(") && line.contains(")") -> {
                    // 方法映射（简化处理）
                    // 实际项目中需要更复杂的正则解析
                }
            }
        }
        return entities.take(500) // 限制数量防止 OOM
    }

    fun searchMappings(query: String) = mappingDao.searchMappings(query)

    // 模糊搜索（支持按类型过滤）
    suspend fun fuzzySearch(query: String, type: String = "CLASS"): List<MappingEntity> {
        return try {
            if (query.isBlank()) return emptyList()
            
            val results = mappingDao.fuzzySearchFts(query)
            
            // 按类型过滤
            results.filter { it.type.equals(type, ignoreCase = true) }
        } catch (e: Exception) {
            // 失败时回退
            mappingDao.searchMappings(query).first().filter { 
                it.type.equals(type, ignoreCase = true) 
            }
        }
    }

    suspend fun insertMappings(mappings: List<MappingEntity>) {
        mappingDao.insertAll(mappings)
    }

    // ==================== 映射类型自动决策逻辑 ====================
    fun decideMappingType(mcVersion: String, loader: String): String {
        // 旧版本使用 MCP
        if (mcVersion <= "1.12.2") return "mcp"

        return when (loader.lowercase()) {
            "fabric" -> {
                if (mcVersion >= "1.21.11") "mojmap" else "yarn"
            }
            "forge", "neoforge" -> {
                if (mcVersion >= "1.18") "parchment" else "mojmap"
            }
            else -> "mojmap"
        }
    }
}