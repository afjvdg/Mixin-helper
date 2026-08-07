package com.example.minecraftmixinhelper.domain.service

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

/**
 * 解析 parchment.json：类 / 方法 / 字段的 Javadoc 与参数名。
 *
 * 同时支持两种真实存在的序列化形态（官方规范见
 * ParchmentMC/Feather `docs/specs/MappingDataContainer.md`）：
 *
 * 1. **数组形态（versioned MDC，官方导出格式）**：
 *    ```
 *    { "version": "1.1.0", "packages": [...],
 *      "classes": [ { "name": "net/minecraft/.../Player", "javadoc": [...],
 *        "methods": [ { "name": "getX", "descriptor": "()I", "javadoc": [...],
 *                       "parameters": [ { "index": 1, "name": "..." } ] } ],
 *        "fields": [ { "name": "x", "descriptor": "I", "javadoc": [...] } ] } ] }
 *    ```
 * 2. **Map 形态（早期导出/部分工具）**：`"classes": { "<类名>": { "methods": { "<name>(<desc>)<ret>": {...} } } }`
 *
 * 解析后与 Mojmap 结果合并（见 [MappingRepository.applyParchment]）：通过
 * `类名|方法名|规范描述符` 定位方法，填充 paramNames / javadoc。描述符统一为斜杠
 * 形式做键，避免点/斜杠不一致导致合并失败。
 *
 * 参数 index 语义（官方规范）：非静态方法 index 从 1 开始（0 为隐式 this），
 * long/double 占两个槽位；解析时按 index 精确落位到描述符参数位置。
 */
object ParchmentParser {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val METHOD_KEY_RE = Regex("""^([\w$<>]+)\((.*)\)([^()]*)$""")

    data class ParchmentMemberInfo(
        val paramNames: List<String>,
        val javadoc: String?
    )

    data class ParchmentData(
        // key = "$dottedClass|$methodName|$descriptor(slash form)"
        val byMethod: Map<String, ParchmentMemberInfo>,
        // key = dottedClass
        val classJavadoc: Map<String, String>
    )

    fun parse(raw: String): ParchmentData {
        val root = try {
            json.parseToJsonElement(raw)
        } catch (e: Exception) {
            return ParchmentData(emptyMap(), emptyMap())
        }
        if (root !is JsonObject) return ParchmentData(emptyMap(), emptyMap())
        val classes = root["classes"]
        return when (classes) {
            is JsonArray -> parseArrayForm(classes)
            is JsonObject -> parseMapForm(classes)
            else -> ParchmentData(emptyMap(), emptyMap())
        }
    }

    // ---------------- 数组形态（官方 versioned MDC 导出） ----------------

    private fun parseArrayForm(classes: JsonArray): ParchmentData {
        val byMethod = mutableMapOf<String, ParchmentMemberInfo>()
        val classJavadoc = mutableMapOf<String, String>()

        for (classEl in classes) {
            val cls = classEl as? JsonObject ?: continue
            val name = cls["name"]?.jsonPrimitive?.contentOrNull ?: continue
            val dotted = name.replace('/', '.')
            javadocOf(cls["javadoc"])?.let { classJavadoc[dotted] = it }

            (cls["methods"] as? JsonArray)?.forEach { methodEl ->
                val method = methodEl as? JsonObject ?: return@forEach
                val methodName = method["name"]?.jsonPrimitive?.contentOrNull
                    ?: return@forEach
                val descriptor = method["descriptor"]?.jsonPrimitive?.contentOrNull
                    ?: return@forEach
                val key = "$dotted|$methodName|${canonicalDescriptor(descriptor)}"
                byMethod[key] = ParchmentMemberInfo(
                    paramNames = paramNamesFor(descriptor, method["parameters"] as? JsonArray),
                    javadoc = javadocOf(method["javadoc"])
                )
            }
        }
        return ParchmentData(byMethod, classJavadoc)
    }

    // ---------------- Map 形态（早期导出） ----------------

    private fun parseMapForm(classes: JsonObject): ParchmentData {
        val byMethod = mutableMapOf<String, ParchmentMemberInfo>()
        val classJavadoc = mutableMapOf<String, String>()

        for ((classKey, classEl) in classes) {
            if (classEl !is JsonObject) continue
            val dotted = classKey.replace('/', '.')
            javadocOf(classEl["javadoc"])?.let { classJavadoc[dotted] = it }

            (classEl["methods"] as? JsonObject)?.forEach { (methodKey, memberEl) ->
                if (memberEl !is JsonObject) return@forEach
                val (name, descriptor) = splitMethodKey(methodKey)
                if (name.isEmpty() || descriptor.isEmpty()) return@forEach
                val key = "$dotted|$name|${canonicalDescriptor(descriptor)}"
                byMethod[key] = ParchmentMemberInfo(
                    paramNames = paramNamesOf(memberEl["parameters"] as? JsonArray),
                    javadoc = javadocOf(memberEl["javadoc"])
                )
            }
        }
        return ParchmentData(byMethod, classJavadoc)
    }

    // ---------------- 公共工具 ----------------

    /** javadoc 可能为字符串或字符串数组（官方规范为按行数组）。 */
    private fun javadocOf(el: JsonElement?): String? = when (el) {
        null, is JsonNull -> null
        is JsonPrimitive -> el.contentOrNull?.takeIf { it.isNotBlank() }
        is JsonArray -> el.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            .joinToString("\n")
            .takeIf { it.isNotBlank() }
        else -> null
    }

    /**
     * 按官方 index 语义把参数名落到描述符参数位置：
     * 第一个参数的 index 为 1 表示非静态方法（0 被隐式 this 占用），
     * long/double 占两个槽位。若 JSON 未提供 index（旧格式），按数组顺序取。
     */
    private fun paramNamesFor(descriptor: String, params: JsonArray?): List<String> {
        if (params == null || params.isEmpty()) return emptyList()

        val entries = params.mapNotNull { p ->
            val obj = p as? JsonObject ?: return@mapNotNull null
            val name = obj["name"]?.jsonPrimitive?.contentOrNull
            if (name.isNullOrEmpty()) null
            else (obj["index"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()) to name
        }

        // 无 index：按数组顺序取
        if (entries.any { it.first == null }) {
            return entries.map { it.second }
        }

        val nameByIndex = entries.associate { it.first!! to it.second }
        val parsed = try {
            AsmDescriptorParser.parse(descriptor)
        } catch (e: Exception) {
            return emptyList()
        }
        val nonStatic = nameByIndex.keys.minOrNull() == 1
        var slot = if (nonStatic) 1 else 0
        val names = mutableListOf<String>()
        for (param in parsed.parameters) {
            names.add(nameByIndex[slot] ?: "")
            slot += if (param == "long" || param == "double") 2 else 1
        }
        return names
    }

    /** 旧 Map 形态的 parameters 为 `[{ "name": ... }, ...]`。 */
    private fun paramNamesOf(el: JsonArray?): List<String> {
        if (el == null) return emptyList()
        return el.mapNotNull { (it as? JsonObject)?.get("name")?.jsonPrimitive?.contentOrNull }
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

    /** 点分与斜杠形式统一为斜杠，保证与 Mojmap 侧合并键一致。 */
    internal fun canonicalDescriptor(desc: String): String = desc.replace('.', '/')
}
