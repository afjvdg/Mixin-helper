package com.example.minecraftmixinhelper.domain.service

object AsmDescriptorBuilder {

    private val primitives = mapOf(
        "void" to "V", "int" to "I", "boolean" to "Z",
        "byte" to "B", "char" to "C", "short" to "S",
        "long" to "J", "float" to "F", "double" to "D"
    )

    fun toDescriptor(javaType: String): String {
        val trimmed = javaType.trim()
        require(trimmed.isNotEmpty()) { "javaType must not be empty" }
        return when {
            trimmed.endsWith("[]") -> "[" + toDescriptor(trimmed.removeSuffix("[]"))
            trimmed in primitives -> primitives[trimmed]!!
            trimmed.contains("/") -> "L$trimmed;"
            else -> "L${trimmed.replace('.', '/')};"
        }
    }

    fun buildMethodDescriptor(params: List<String>, returnType: String): String {
        val paramDesc = params.joinToString("") { toDescriptor(it) }
        return "($paramDesc)${toDescriptor(returnType)}"
    }
}
