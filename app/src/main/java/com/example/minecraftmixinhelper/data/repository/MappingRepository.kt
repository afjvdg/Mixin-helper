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
            // Store real Mojang versionUrl for each entry; no longer filtering 1.13 arbitrarily
            val mojangVersions = manifest.versions
                .take(50)
                .map {
                    VersionEntity(
                        version = it.id,
                        loader = "mojang",
                        mappingType = decideMappingType(it.id, "mojang"),
                        versionUrl = it.url,
                        isCached = false
                    )
                }

            val fabricVersions = try {
                fabricApi.getGameVersions()
                    .take(30)
                    .map {
                        VersionEntity(
                            version = it.version,
                            loader = "fabric",
                            mappingType = decideMappingType(it.version, "fabric"),
                            versionUrl = null,
                            isCached = false
                        )
                    }
            } catch (_: Exception) {
                emptyList()
            }

            val forgeVersions = listOf(
                VersionEntity("1.20.1", "forge", decideMappingType("1.20.1", "forge"), null, false),
                VersionEntity("1.20.1", "neoforge", decideMappingType("1.20.1", "neoforge"), null, false),
                VersionEntity("1.19.4", "forge", decideMappingType("1.19.4", "forge"), null, false)
            )

            val all = (mojangVersions + fabricVersions + forgeVersions)
                .distinctBy { it.version + ":" + it.loader }
                .sortedWith { a, b -> compareVersion(b.version, a.version) }

            versionDao.insertAll(all)
        } catch (e: Exception) {
            // 网络失败时插入少量默认数据
            val defaults = listOf(
                VersionEntity("1.20.1", "fabric", "yarn", null, false),
                VersionEntity("1.20.1", "forge", "mojmap", null, false),
                VersionEntity("1.19.4", "neoforge", "mojmap", null, false)
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
            if (versionJsonUrl.isBlank()) throw IllegalArgumentException("versionJsonUrl is blank for $version")
            // 总是先下载 Mojmap
            val rawMojmap = downloader.downloadMojangMappings(versionJsonUrl)
            val mojmapEntities = parseMojmap(rawMojmap, version)
            // 分批插入防止单次事务过大
            mojmapEntities.chunked(1000).forEach { chunk ->
                mappingDao.insertAll(chunk)
            }

            // 如果是 Parchment，再额外处理（目前仅标记，后续可扩展）
            if (mappingType == "parchment") {
                // TODO: 下载 Parchment 参数映射（可扩展）
            }
            // 标记版本已缓存
            try {
                val existing = versionDao.getAllVersions().first()
                    .find { it.version == version }
                if (existing != null) {
                    versionDao.insertAll(listOf(existing.copy(isCached = true, lastUpdated = System.currentTimeMillis())))
                }
            } catch (_: Exception) { }
        } catch (e: Exception) {
            // 兜底插入一条示例数据，避免搜索页完全空白；同时抛出异常让上层显示错误
            // 不再吞掉异常导致误判成功
            if (e is IllegalArgumentException) throw e
            mappingDao.insertAll(listOf(
                MappingEntity(className = "net.minecraft.world.entity.player.Player", obfuscatedName = "gfj", deobfuscatedName = "Player", type = "CLASS")
            ))
            throw e
        }
    }

    // 解析 Mojmap (ProGuard 格式) — 支持类 / 方法 / 字段
    // 格式参考: https://linkie.shedaniel.me/mappings
    // 类:  a.b.ClassName -> a:
    // 字段:  int fieldName -> a
    // 方法:  1:10:void methodName(int,java.lang.String) -> a
    // 或 3:3:void method() -> a (无参)
    internal fun parseMojmap(raw: String, version: String): List<MappingEntity> {
        val lines = raw.lines()
        val entities = mutableListOf<MappingEntity>()
        var currentClassDeobf: String? = null
        var currentClassObf: String? = null

        // 预编译正则
        val classRegex = Regex("""^(\S+) -> (\S+):$""")
        // 字段:  修饰符? 类型 字段名 -> obf
        // 方法:  修饰符? 返回类型 方法名(参数) -> obf
        val memberRegex = Regex("""^\s+(?:\d+:\d+:)?(.+?)\s+(\S+?)(?:\((.*)\))? -> (\S+)$""")

        for (rawLine in lines) {
            val line = rawLine.trimEnd()
            if (line.isBlank() || line.startsWith("#")) continue

            // 类映射行
            val classMatch = classRegex.find(line)
            if (classMatch != null) {
                val deobf = classMatch.groupValues[1].trim()
                var obf = classMatch.groupValues[2].trim()
                // 去掉末尾冒号（正则已去掉），但防御性处理
                if (obf.endsWith(":")) obf = obf.removeSuffix(":")
                currentClassDeobf = deobf
                currentClassObf = obf
                entities.add(
                    MappingEntity(
                        className = deobf,
                        obfuscatedName = obf,
                        deobfuscatedName = deobf.substringAfterLast('.').substringAfterLast('/'),
                        type = "CLASS",
                        descriptor = null
                    )
                )
                continue
            }

            // 成员映射（方法/字段）—— 必须在类上下文中
            if (currentClassDeobf == null) continue
            if (!line.contains("->")) continue

            val memberMatch = memberRegex.find(line.trim())
            if (memberMatch != null) {
                val typePart = memberMatch.groupValues[1].trim() // 可能是修饰符+类型 或 返回类型
                val namePart = memberMatch.groupValues[2].trim()
                val paramsPart = memberMatch.groupValues[3] // 可能为空
                val obfName = memberMatch.groupValues[4].trim()

                val isMethod = memberMatch.groupValues[3].isNotEmpty() || line.contains("(")

                if (isMethod) {
                    // 方法: paramsPart 是参数列表（逗号分隔的 Java 类型名）
                    val params = if (paramsPart.isBlank()) emptyList() else paramsPart.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    // 尝试还原 descriptor（简化：用 JVM 描述符）
                    val descriptor = try {
                        buildDescriptorFromJavaTypes(params, typePart.substringAfterLast(' '))
                    } catch (_: Exception) {
                        "($paramsPart)$typePart"
                    }
                    entities.add(
                        MappingEntity(
                            className = currentClassDeobf,
                            obfuscatedName = obfName,
                            deobfuscatedName = namePart,
                            type = "METHOD",
                            descriptor = descriptor,
                            params = params.joinToString(","),
                            returnType = typePart.substringAfterLast(' ')
                        )
                    )
                } else {
                    // 字段
                    entities.add(
                        MappingEntity(
                            className = currentClassDeobf,
                            obfuscatedName = obfName,
                            deobfuscatedName = namePart,
                            type = "FIELD",
                            descriptor = typePart.substringAfterLast(' '),
                            returnType = typePart.substringAfterLast(' ')
                        )
                    )
                }
            }
        }
        return entities
    }

    private fun buildDescriptorFromJavaTypes(params: List<String>, returnType: String): String {
        fun toDesc(javaType: String): String {
            val t = javaType.trim()
            if (t.isEmpty()) return ""
            if (t.endsWith("[]")) return "[" + toDesc(t.removeSuffix("[]"))
            return when (t) {
                "void" -> "V"
                "int" -> "I"
                "boolean" -> "Z"
                "byte" -> "B"
                "char" -> "C"
                "short" -> "S"
                "long" -> "J"
                "float" -> "F"
                "double" -> "D"
                else -> "L" + t.replace('.', '/') + ";"
            }
        }
        val paramDesc = params.joinToString("") { toDesc(it) }
        return "($paramDesc)${toDesc(returnType)}"
    }

    fun searchMappings(query: String) = mappingDao.searchMappings(query)

    // 模糊搜索（支持按类型过滤）—— 带 FTS 转义与回退
    suspend fun fuzzySearch(query: String, type: String = "CLASS"): List<MappingEntity> {
        if (query.isBlank()) return emptyList()
        val sanitized = escapeFtsQuery(query)
        return try {
            val results = mappingDao.fuzzySearchFts(sanitized)
            if (type.isBlank()) results else results.filter { it.type.equals(type, ignoreCase = true) }
        } catch (e: Exception) {
            // 失败时回退到 LIKE
            try {
                mappingDao.searchMappings(query).first().filter {
                    if (type.isBlank()) true else it.type.equals(type, ignoreCase = true)
                }
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    /**
     * 转义 FTS5 查询输入，防止特殊字符导致语法错误。
     * 策略：用双引号包裹每个 token 并转义内部双引号，末尾加 * 实现前缀匹配
     */
    internal fun escapeFtsQuery(raw: String): String {
        // 去除首尾空白，按空白分词
        val tokens = raw.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return "\"\""
        // 每个 token 去除 FTS 特殊字符，留字母数字下划线与点斜杠
        return tokens.joinToString(" ") { token ->
            val cleaned = token.replace(Regex("[\"*\\-:()^]"), " ").trim()
                .replace(Regex("\\s+"), " ")
                .replace("\"", "\"\"")
            if (cleaned.isBlank()) "\"\""
            else "\"$cleaned\"*"
        }
    }

    suspend fun insertMappings(mappings: List<MappingEntity>) {
        mappingDao.insertAll(mappings)
    }

    // ==================== 映射类型自动决策逻辑 ====================
    fun decideMappingType(mcVersion: String, loader: String): String {
        if (isVersionLessOrEqual(mcVersion, "1.12.2")) return "mcp"

        return when (loader.lowercase()) {
            "fabric" -> {
                // Fabric Yarn 在 1.14+ 才稳定，1.21.11 后 Mojang 官方映射更完整
                if (compareVersion(mcVersion, "1.21.11") >= 0) "mojmap" else "yarn"
            }
            "forge", "neoforge" -> {
                if (compareVersion(mcVersion, "1.18") >= 0) "parchment" else "mojmap"
            }
            else -> "mojmap"
        }
    }

    // ---------- 版本号工具 ----------
    private fun parseVersion(version: String): List<Int> {
        // 支持 1.20.1, 1.21-rc1, 1.19.3-pre1 等，提取数字部分
        return Regex("\\d+").findAll(version).map { it.value.toIntOrNull() ?: 0 }.toList()
    }

    /**
     * 比较两个 Minecraft 版本号，返回 负数/0/正数 表示 a < b / a == b / a > b
     */
    fun compareVersion(a: String, b: String): Int {
        val pa = parseVersion(a)
        val pb = parseVersion(b)
        val max = maxOf(pa.size, pb.size)
        for (i in 0 until max) {
            val av = pa.getOrNull(i) ?: 0
            val bv = pb.getOrNull(i) ?: 0
            if (av != bv) return av.compareTo(bv)
        }
        return 0
    }

    private fun isVersionLessOrEqual(a: String, b: String): Boolean = compareVersion(a, b) <= 0
}
