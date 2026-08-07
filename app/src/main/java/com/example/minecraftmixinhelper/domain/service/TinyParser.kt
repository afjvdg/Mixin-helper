package com.example.minecraftmixinhelper.domain.service

/**
 * 解析 Yarn mappings.tiny，支持 Tiny v1 / v2 两种格式。
 *
 * - 读取表头确定命名空间（如 v2: intermediate / hashed / mojang）。
 * - `className` / `deobfuscatedName` 统一输出为「可读」命名空间（official / mojang）。
 * - 描述符中的类名从源命名空间（intermediary）重映射为可读命名空间，保证与 Mojmap 一致。
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

        for (line in lines.drop(1)) {
            val token = line.firstOrNull() ?: continue
            val parts = line.substring(1).trim().split(Regex("""\s+"""))
            if (parts.isEmpty()) continue

            when (token) {
                'c' -> {
                    // c <ns0> <ns1> ...
                    if (parts.size > maxOf(sourceIdx, targetIdx)) {
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
                }
                'f' -> {
                    // f <owner> <desc> <ns0> <ns1> ...
                    if (parts.size >= 2 + maxOf(sourceIdx, targetIdx) + 1) {
                        val owner = parts[0]
                        val desc = parts[1]
                        val obf = parts[2 + sourceIdx]
                        val named = parts[2 + targetIdx]
                        result.add(
                            MojmapParser.ParsedMapping(
                                type = "FIELD",
                                className = remapClass(owner, classMap),
                                obfuscatedName = obf,
                                deobfuscatedName = named,
                                descriptor = remapDescriptor(desc, classMap)
                            )
                        )
                    }
                }
                'm' -> {
                    // m <owner> <desc> <ns0> <ns1> ...
                    if (parts.size >= 2 + maxOf(sourceIdx, targetIdx) + 1) {
                        val owner = parts[0]
                        val desc = parts[1]
                        val obf = parts[2 + sourceIdx]
                        val named = parts[2 + targetIdx]
                        val parsed = AsmDescriptorParser.parse(desc)
                        result.add(
                            MojmapParser.ParsedMapping(
                                type = "METHOD",
                                className = remapClass(owner, classMap),
                                obfuscatedName = obf,
                                deobfuscatedName = named,
                                descriptor = remapDescriptor(desc, classMap),
                                params = parsed.parameters,
                                returnType = parsed.returnType
                            )
                        )
                    }
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
