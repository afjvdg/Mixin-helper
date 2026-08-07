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
        val sourceIdx = pickIndex(namespaces, "intermediary", "named", "yarn", "hashed") ?: 0
        val targetIdx = pickIndex(namespaces, "mojang", "official") ?: namespaces.lastIndex
        val classMap = mutableMapOf<String, String>()
        val result = mutableListOf<MojmapParser.ParsedMapping>()

        // Tiny 文件的类通常按字典序排列。成员描述符可以引用稍后才出现的类，
        // 所以先完整收集类映射，再解析成员并重映射其描述符。
        for (line in lines.drop(1)) {
            if (line.firstOrNull() != 'c') continue
            val parts = line.substring(1).trim().split(Regex("""\s+"""))
            if (parts.size <= maxOf(sourceIdx, targetIdx)) continue
            val source = parts[sourceIdx]
            val target = parts[targetIdx]
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

        var currentClass: String? = null
        for (line in lines.drop(1)) {
            val token = line.firstOrNull() ?: continue
            val parts = line.substring(1).trim().split(Regex("""\s+"""))
            when (token) {
                'c' -> currentClass = parts.getOrNull(sourceIdx)
                'f', 'm' -> {
                    val owner = currentClass ?: continue
                    if (parts.size < 1 + maxOf(sourceIdx, targetIdx) + 1) continue
                    val descriptor = remapDescriptor(parts[0], classMap)
                    val mapping = MojmapParser.ParsedMapping(
                        type = if (token == 'f') "FIELD" else "METHOD",
                        className = remapClass(owner, classMap),
                        obfuscatedName = parts[1 + sourceIdx],
                        deobfuscatedName = parts[1 + targetIdx],
                        descriptor = descriptor
                    )
                    result.add(
                        if (token == 'm') {
                            val parsed = runCatching { AsmDescriptorParser.parse(descriptor) }.getOrNull()
                            mapping.copy(
                                params = parsed?.parameters ?: emptyList(),
                                returnType = parsed?.returnType
                            )
                        } else mapping
                    )
                }
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
