package com.example.minecraftmixinhelper.domain.service

/**
 * 解析 JVM 方法/字段描述符，例如 `(Lnet/minecraft/entity/Entity;I)V`。
 * 拆分为参数类型列表与返回类型，支持数组 / 对象 / 基本类型。
 *
 * 与 [AsmDescriptorBuilder]（由 Java 类型「生成」描述符）相反，本类是「解析」已有描述符。
 */
object AsmDescriptorParser {

    private val PRIMITIVES = mapOf(
        'B' to "byte",
        'C' to "char",
        'D' to "double",
        'F' to "float",
        'I' to "int",
        'J' to "long",
        'S' to "short",
        'Z' to "boolean",
        'V' to "void"
    )

    data class ParsedDescriptor(
        val parameters: List<String>,
        val returnType: String
    )

    /** 解析完整方法描述符，如 `(Lnet/minecraft/entity/Entity;I)V`。 */
    fun parse(descriptor: String): ParsedDescriptor {
        require(descriptor.startsWith("(")) { "方法描述符必须以 '(' 开头: $descriptor" }
        val close = descriptor.indexOf(')')
        require(close > 0) { "方法描述符缺少 ')': $descriptor" }
        val paramsPart = descriptor.substring(1, close)
        val returnPart = descriptor.substring(close + 1)
        return ParsedDescriptor(parseTypes(paramsPart), parseType(returnPart))
    }

    /** 解析单个类型（用于字段描述符），如 `D` -> "double"、`Lnet/Foo;` -> "net.Foo"。 */
    fun parseType(descriptor: String): String {
        if (descriptor.isEmpty()) return "void"
        val (type, _) = parseOne(descriptor, 0)
        return type
    }

    private fun parseTypes(s: String): List<String> {
        val result = mutableListOf<String>()
        var i = 0
        while (i < s.length) {
            val (type, next) = parseOne(s, i)
            result.add(type)
            i = next
        }
        return result
    }

    private fun parseOne(s: String, start: Int): Pair<String, Int> {
        var i = start
        var dims = 0
        while (i < s.length && s[i] == '[') {
            dims++
            i++
        }
        // 先解析基础类型，再把数组维度以 `[]` 后缀拼在其后（`[[I` -> `int[][]`），
        // 避免把维度误拼在类型名前（`[I` 错误地变成 `[]int`）。
        val base = when {
            i < s.length && s[i] == 'L' -> {
                val end = s.indexOf(';', i)
                require(end > i) { "对象类型描述符未闭合: $s" }
                val className = s.substring(i + 1, end).replace('/', '.')
                i = end + 1
                className
            }
            i < s.length && s[i] in PRIMITIVES -> {
                val primitive = PRIMITIVES[s[i]]
                i += 1
                primitive
            }
            else -> error("无法解析描述符片段: '${if (i < s.length) s[i] else "EOF"}' in $s")
        }
        return base + "[]".repeat(dims) to i
    }
}
