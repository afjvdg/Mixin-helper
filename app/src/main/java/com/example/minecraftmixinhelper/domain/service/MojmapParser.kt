package com.example.minecraftmixinhelper.domain.service

/**
 * 解析 Mojang client_mappings.txt（ProGuard 格式）的 类 / 方法 / 字段。
 *
 * 方向：可读名 -> 混淆名（与 Mojang 官方映射一致，例如
 * `net.minecraft.world.entity.player.Player -> gfj`）。
 * 原始实现只解析类且 `take(500)` 截断，这里全量解析并填充
 * descriptor / params / returnType。
 */
object MojmapParser {

    data class ParsedMapping(
        val type: String,             // CLASS / METHOD / FIELD
        val className: String,        // 可读类全名（package.Class）
        val obfuscatedName: String,   // 混淆名
        val deobfuscatedName: String, // 可读名
        val descriptor: String? = null,
        val params: List<String> = emptyList(),
        val returnType: String? = null,
        val paramNames: List<String>? = null, // 来自 Parchment
        val javadoc: String? = null           // 来自 Parchment
    )

    // 类行：pkg.Class -> obf:
    private val CLASS_RE = Regex("""^([\w$.]+)\s*->\s*([\w$.]+):\s*$""")
    // 方法行：name(params)ret -> obf   （ret 可能与参数之间被 ")" 分隔）
    private val METHOD_RE = Regex("""^([\w$<>]+)\((.*)\)([^()]*?)\s*->\s*([\w$]+)\s*$""")
    // 字段行：name:descriptor -> obf
    private val FIELD_RE = Regex("""^([\w$]+):([\w./$\[\];]+)\s*->\s*([\w$]+)\s*$""")

    fun parse(raw: String): List<ParsedMapping> {
        val result = mutableListOf<ParsedMapping>()
        var currentClass: String? = null

        for (rawLine in raw.lineSequence()) {
            val line = rawLine.trimEnd()
            if (line.isBlank() || line.startsWith("#")) continue

            val classMatch = CLASS_RE.find(line)
            if (classMatch != null) {
                val readable = classMatch.groupValues[1]
                val obf = classMatch.groupValues[2]
                currentClass = readable
                result.add(ParsedMapping("CLASS", readable, obf, readable))
                continue
            }

            val methodMatch = METHOD_RE.find(line)
            if (methodMatch != null && currentClass != null) {
                val name = methodMatch.groupValues[1]
                val paramsPart = methodMatch.groupValues[2]
                val returnPart = methodMatch.groupValues[3].ifEmpty { "V" }
                val obf = methodMatch.groupValues[4]
                val descriptor = "($paramsPart)$returnPart"
                val parsed = AsmDescriptorParser.parse(descriptor)
                result.add(
                    ParsedMapping(
                        type = "METHOD",
                        className = currentClass,
                        obfuscatedName = obf,
                        deobfuscatedName = name,
                        descriptor = descriptor,
                        params = parsed.parameters,
                        returnType = parsed.returnType
                    )
                )
                continue
            }

            val fieldMatch = FIELD_RE.find(line)
            if (fieldMatch != null && currentClass != null) {
                val name = fieldMatch.groupValues[1]
                val desc = fieldMatch.groupValues[2]
                val obf = fieldMatch.groupValues[3]
                result.add(
                    ParsedMapping(
                        type = "FIELD",
                        className = currentClass,
                        obfuscatedName = obf,
                        deobfuscatedName = name,
                        descriptor = desc,
                        params = emptyList(),
                        returnType = null
                    )
                )
            }
        }
        return result
    }
}
