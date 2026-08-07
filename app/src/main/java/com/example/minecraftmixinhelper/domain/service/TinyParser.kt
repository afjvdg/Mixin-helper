package com.example.minecraftmixinhelper.domain.service

/**
 * 解析 Yarn mappings.tiny，支持 Tiny v1 / v2 两种格式。
 *
 * ## Tiny v2 行格式（Fabric Wiki 官方规范，字段/方法行的 owner 从最近的类块继承）
 *
 * ```
 * tiny   2   0   official   intermediary   named
 * c      a   class_123   pkg/SomeClass
 *     f   [I      a   field_789   someField        # f <desc> <ns0名> <ns1名> ...
 *     m   (III)V  a   method_456  someMethod       # m <desc> <ns0名> <ns1名> ...
 *         p   1       param_0  x                   # 参数行（本解析器暂不消费）
 * ```
 *
 * - 读取表头确定命名空间（如 v2: official / intermediary / named）。
 * - `className` / `deobfuscatedName` 统一输出为「可读」命名空间（named / mojang / official）。
 * - 描述符按规范引用「源」命名空间（intermediary / official）的类名，
 *   通过类映射重映射为可读命名空间，保证与 Mojmap 一致。
 */
object TinyParser {

    private val HEADER_RE = Regex("""^tiny\s+(\d+)\s+(\d+)\s+(.+)$""")
    private val DESC_CLASS_RE = Regex("""L([\w/$]+);""")

    fun parse(raw: String): List<MojmapParser.ParsedMapping> {
        val lines = raw.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .toList()
        if (lines.isEmpty()) return emptyList()

        val headerMatch = HEADER_RE.find(lines.first())
        require(headerMatch != null) { "不是合法的 Tiny 映射文件: ${lines.first()}" }

        val namespaces = headerMatch.groupValues[3].split(Regex("""\s+""")).filter { it.isNotEmpty() }
        val sourceIdx = pickIndex(namespaces, "intermediary", "named", "yarn", "hashed")
            ?: 0
        val targetIdx = pickIndex(namespaces, "mojang", "official")
            ?: (namespaces.lastIndex)

        val result = mutableListOf<MojmapParser.ParsedMapping>()
        val classMap = mutableMapOf<String, String>() // source class (slashes) -> target class (slashes)
        var currentClass: String? = null // 当前类（源命名空间，斜杠形式）

        for (line in lines.drop(1)) {
            val token = line.firstOrNull() ?: continue
            val parts = line.substring(1).trim().split(Regex("""\s+"""))
            if (parts.isEmpty()) continue

            when (token) {
                'c' -> {
                    // c <ns0名> <ns1名> ...（所有者从本行起继承）
                    if (parts.size > maxOf(sourceIdx, targetIdx)) {
                        val source = parts[sourceIdx]
                        val target = parts[targetIdx]
                        currentClass = source
                        classMap[source] = target
                        result.add(
                            MojmapParser.ParsedMapping(
                                type = "CLASS",
                                className = target.replace('/', '.'),
                                obfuscatedName = source.replace('/', '.'),
                                deobfuscatedName = target.replace('/', '.')
                            )
                        )
                    }
                }
                'f' -> {
                    // f <desc> <ns0名> <ns1名> ...（owner = 最近的类）
                    if (currentClass != null && parts.size >= 1 + maxOf(sourceIdx, targetIdx) + 1) {
                        val desc = parts[0]
                        val obf = parts[1 + sourceIdx]
                        val named = parts[1 + targetIdx]
                        result.add(
                            MojmapParser.ParsedMapping(
                                type = "FIELD",
                                className = remapClass(currentClass, classMap),
                                obfuscatedName = obf,
                                deobfuscatedName = named,
                                descriptor = remapDescriptor(desc, classMap)
                            )
                        )
                    }
                }
                'm' -> {
                    // m <desc> <ns0名> <ns1名> ...（owner = 最近的类）
                    if (currentClass != null && parts.size >= 1 + maxOf(sourceIdx, targetIdx) + 1) {
                        val desc = parts[0]
                        val obf = parts[1 + sourceIdx]
                        val named = parts[1 + targetIdx]
                        val parsed = try {
                            AsmDescriptorParser.parse(desc)
                        } catch (e: Exception) {
                            null
                        }
                        result.add(
                            MojmapParser.ParsedMapping(
                                type = "METHOD",
                                className = remapClass(currentClass, classMap),
                                obfuscatedName = obf,
                                deobfuscatedName = named,
                                descriptor = remapDescriptor(desc, classMap),
                                params = parsed?.parameters ?: emptyList(),
                                returnType = parsed?.returnType
                            )
                        )
                    }
                }
                // 'p'（参数行）与 'v'（局部变量行）暂不消费
            }
        }
        return result
    }

    private fun pickIndex(namespaces: List<String>, vararg candidates: String): Int? {
        for (c in candidates) {
            val idx = namespaces.indexOfFirst { it.equals(c, ignoreCase = true) }
            if (idx >= 0) return idx
        }
        return null
    }

    private fun remapClass(name: String, classMap: Map<String, String>): String {
        val mapped = classMap[name] ?: name
        return mapped.replace('/', '.')
    }

    private fun remapDescriptor(desc: String, classMap: Map<String, String>): String {
        return DESC_CLASS_RE.replace(desc) { m ->
            val cls = m.groupValues[1]
            val mapped = classMap[cls] ?: cls
            "L$mapped;"
        }
    }
}
