package com.example.minecraftmixinhelper.domain.service

/**
 * 解析 Mojang client_mappings.txt（ProGuard 变体格式）的 类 / 方法 / 字段。
 *
 * 方向：可读名(官方名) -> 混淆名（与 Mojang 官方映射一致）。
 *
 * ## 真实文件格式（依据 ParchmentMC/Feather 官方 io-proguard 解析器源码核对）
 *
 * 与经典 ProGuard 不同，Mojang 官方映射使用「可读类型」而非 JVM 描述符：
 *
 * ```
 * net.minecraft.world.entity.player.Player -> gfj:          # 类行（点分官方名 -> 混淆名 + 冒号）
 *     147:159:void add(net.minecraft.world.entity.Entity, int) -> method_1234
 *     net.minecraft.world.level.Level level -> field_1234
 * ```
 *
 * - 方法行：可选 `{起始行}:{结束行}:` 前缀 + `{返回类型} {方法名}({参数1},{参数2},...) -> {混淆名}`
 * - 字段行：`{类型} {字段名} -> {混淆名}`
 * - 类型为可读形式：`int` / `boolean` / `net.minecraft.world.level.Level` / `int[]`，
 *   描述符由 [AsmDescriptorBuilder] 生成 JVM 形式。
 *
 * 同时保留经典 ProGuard 格式（`name(params)ret -> obf` / `name:desc -> obf`）作为回退，
 * 以便兼容其他工具产出的映射文件。
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

    // 类行：pkg.Class -> obf:  （冒号或分号结尾均可）
    private val CLASS_RE = Regex("""^([\w$.]+)\s*->\s*([\w$]+)\s*[:;]\s*$""")
    // 方法行（真实 Mojang 格式）：[start:end:]返回类型 名称(参数1,参数2) -> obf
    private val METHOD_RE = Regex(
        """^(?:(\d+):(\d+):)?\s*([\w$.<>\[\]]+)\s+([\w$<>]+)\(([^()]*)\)\s*->\s*([\w$]+)\s*$"""
    )
    // 字段行（真实 Mojang 格式）：类型 名称 -> obf
    private val FIELD_RE = Regex("""^([\w$.<>\[\]]+)\s+([\w$]+)\s*->\s*([\w$]+)\s*$""")

    // 经典 ProGuard 回退：方法 name(paramsJvm)retJvm -> obf
    private val METHOD_LEGACY_RE = Regex("""^([\w$<>]+)\((.*)\)([^()]*?)\s*->\s*([\w$]+)\s*$""")
    // 经典 ProGuard 回退：字段 name:descJvm -> obf
    private val FIELD_LEGACY_RE = Regex("""^([\w$]+):([\w./$\[\];]+)\s*->\s*([\w$]+)\s*$""")

    fun parse(raw: String): List<ParsedMapping> {
        val result = mutableListOf<ParsedMapping>()
        var currentClass: String? = null

        for (rawLine in raw.lineSequence()) {
            // trim 两端：真实文件成员行可能有缩进（ProGuard 风格）
            val line = rawLine.trim()
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
                val returnToken = methodMatch.groupValues[3]
                val name = methodMatch.groupValues[4]
                val paramsPart = methodMatch.groupValues[5]
                val obf = methodMatch.groupValues[6]
                result.add(buildMethod(currentClass, name, obf, returnToken, paramsPart))
                continue
            }

            val fieldMatch = FIELD_RE.find(line)
            if (fieldMatch != null && currentClass != null) {
                val typeToken = fieldMatch.groupValues[1]
                val name = fieldMatch.groupValues[2]
                val obf = fieldMatch.groupValues[3]
                result.add(
                    ParsedMapping(
                        type = "FIELD",
                        className = currentClass,
                        obfuscatedName = obf,
                        deobfuscatedName = name,
                        descriptor = AsmDescriptorBuilder.toDescriptor(typeToken),
                        params = emptyList(),
                        returnType = readableType(typeToken)
                    )
                )
                continue
            }

            // ---- 经典 ProGuard 回退 ----
            val methodLegacy = METHOD_LEGACY_RE.find(line)
            if (methodLegacy != null && currentClass != null) {
                val name = methodLegacy.groupValues[1]
                val paramsJvm = methodLegacy.groupValues[2]
                val retJvm = methodLegacy.groupValues[3].ifEmpty { "V" }
                val obf = methodLegacy.groupValues[4]
                val descriptor = "($paramsJvm)$retJvm"
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

            val fieldLegacy = FIELD_LEGACY_RE.find(line)
            if (fieldLegacy != null && currentClass != null) {
                val name = fieldLegacy.groupValues[1]
                val desc = fieldLegacy.groupValues[2]
                val obf = fieldLegacy.groupValues[3]
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

    private fun buildMethod(
        className: String,
        name: String,
        obf: String,
        returnToken: String,
        paramsPart: String
    ): ParsedMapping {
        // 参数以逗号分隔（真实文件如 `(net.minecraft.world.level.Level, int)`），
        // 每个参数可能带前导空格
        val paramTokens = paramsPart.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        val descriptor =
            "(${paramTokens.joinToString("") { AsmDescriptorBuilder.toDescriptor(it) }})" +
                AsmDescriptorBuilder.toDescriptor(returnToken)
        return ParsedMapping(
            type = "METHOD",
            className = className,
            obfuscatedName = obf,
            deobfuscatedName = name,
            descriptor = descriptor,
            params = paramTokens.map { readableType(it) },
            returnType = readableType(returnToken)
        )
    }

    /** `net/minecraft/...` -> `net.minecraft...`（保留基本类型原样）。 */
    private fun readableType(token: String): String = token.replace('/', '.')
}
