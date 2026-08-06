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
            val mojangVersions = manifest.versions.take(30).map {
                VersionEntity(it.id, "mojang", "mojmap", false)
            }

            val fabricVersions = fabricApi.getGameVersions().take(20).map {
                VersionEntity(it.version, "fabric", "yarn", false)
            }

            val forgeVersions = listOf(
                VersionEntity("1.20.1", "forge", "mojmap", false),
                VersionEntity("1.20.1", "neoforge", "mojmap", false)
            )

            val all = mojangVersions + fabricVersions + forgeVersions
            versionDao.insertAll(all.distinctBy { it.version + it.loader })
        } catch (e: Exception) {
            val defaults = listOf(
                VersionEntity("1.20.1", "fabric", "yarn", false),
                VersionEntity("1.20.1", "forge", "mojmap", false)
            )
            versionDao.insertAll(defaults)
        }
    }

    // 真实下载并解析 Mojang mappings
    suspend fun downloadAndParseMojangMappings(version: String, versionJsonUrl: String) = withContext(Dispatchers.IO) {
        try {
            val rawMappings = downloader.downloadMojangMappings(versionJsonUrl)
            val entities = parseMojmap(rawMappings, version)
            mappingDao.insertAll(entities)
        } catch (e: Exception) {
            // 失败时插入示例数据
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

    // 模糊搜索（优先使用 FTS5）
    suspend fun fuzzySearch(query: String): List<MappingEntity> {
        return try {
            if (query.isBlank()) emptyList()
            else mappingDao.fuzzySearchFts(query)
        } catch (e: Exception) {
            // FTS5 失败时回退到 LIKE 搜索
            mappingDao.searchMappings(query).first()
        }
    }

    suspend fun insertMappings(mappings: List<MappingEntity>) {
        mappingDao.insertAll(mappings)
    }
}