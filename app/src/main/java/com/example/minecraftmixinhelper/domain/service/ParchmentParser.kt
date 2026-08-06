package com.example.minecraftmixinhelper.domain.service

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 解析 parchment.json（Feather MDC 官方规范）：类 / 方法 / 字段的 Javadoc 与参数名。
 *
 * 解析后与 Mojmap 结果合并（见 [mergeInto]）：通过 `类名|方法名|描述符` 定位方法，
 * 填充 paramNames / javadoc。参数 index 的复杂情况（this 占位、long/double 双位、
 * static 回退）由 Parchment 的参数列表顺序隐式表达，这里按序取出参数名即可。
 */
object ParchmentParser {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val METHOD_KEY_RE = Regex("""^([\w$<>]+)\((.*)\)([^()]*)$""")

    @Serializable
    private data class ParchmentFile(
        val classes: Map<String, ParchmentClass> = emptyMap()
    )

    @Serializable
    private data class ParchmentClass(
        val methods: Map<String, ParchmentMember> = emptyMap(),
        val fields: Map<String, ParchmentMember> = emptyMap(),
        val javadoc: String? = null
    )

    @Serializable
    private data class ParchmentMember(
        val parameters: List<ParchmentParam> = emptyList(),
        val javadoc: String? = null
    )

    @Serializable
    private data class ParchmentParam(
        val name: String = "",
        val index: Int? = null
    )

    data class ParchmentMemberInfo(
        val paramNames: List<String>,
        val javadoc: String?
    )

    data class ParchmentData(
        // key = "$dottedClass|$methodName|$descriptor"
        val byMethod: Map<String, ParchmentMemberInfo>,
        // key = dottedClass
        val classJavadoc: Map<String, String>
    )

    fun parse(raw: String): ParchmentData {
        val file = json.decodeFromString<ParchmentFile>(raw)
        val byMethod = mutableMapOf<String, ParchmentMemberInfo>()
        val classJavadoc = mutableMapOf<String, String>()

        for ((classSlash, cls) in file.classes) {
            val dotted = classSlash.replace('/', '.')
            if (!cls.javadoc.isNullOrBlank()) classJavadoc[dotted] = cls.javadoc

            for ((methodKey, member) in cls.methods) {
                val (name, descriptor) = splitMethodKey(methodKey)
                val key = "$dotted|$name|$descriptor"
                byMethod[key] = ParchmentMemberInfo(
                    paramNames = member.parameters.map { it.name },
                    javadoc = member.javadoc
                )
            }
        }
        return ParchmentData(byMethod, classJavadoc)
    }

    private fun splitMethodKey(key: String): Pair<String, String> {
        val m = METHOD_KEY_RE.find(key)
        return if (m != null) {
            val name = m.groupValues[1]
            val params = m.groupValues[2]
            val ret = m.groupValues[3].ifEmpty { "V" }
            name to "($params)$ret"
        } else {
            key to ""
        }
    }
}
