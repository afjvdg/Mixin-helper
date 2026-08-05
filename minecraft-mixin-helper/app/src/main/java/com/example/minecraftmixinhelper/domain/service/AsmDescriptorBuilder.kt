package com.example.minecraftmixinhelper.domain.service

object AsmDescriptorBuilder {

    private val primitives = mapOf(
        "void" to "V", "int" to "I", "boolean" to "Z",
        "byte" to "B", "char" to "C", "short" to "S",
        "long" to "J", "float" to "F", "double" to "D"
    )

    fun toDescriptor(javaType: String): String {
        return when {
            javaType.endsWith("[]") -> "[" + toDescriptor(javaType.removeSuffix("[]"))
            javaType in primitives -> primitives[javaType]!!
            else -> "L${javaType.replace('.', '/')};"
        }
    }

    fun buildMethodDescriptor(params: List<String>, returnType: String): String {
        val paramDesc = params.joinToString("") { toDescriptor(it) }
        return "($paramDesc)${toDescriptor(returnType)}"
    }
}