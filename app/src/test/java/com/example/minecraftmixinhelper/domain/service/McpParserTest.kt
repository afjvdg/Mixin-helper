package com.example.minecraftmixinhelper.domain.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class McpParserTest {

    private val sampleSrg = """
        CL: aaa net/minecraft/server/MinecraftServer
        CL: bbb net/minecraft/entity/Entity
        FD: aaa/field_1234 net/minecraft/server/MinecraftServer/field_1234
        MD: aaa/func_1000 (Lbbb;)V net/minecraft/server/MinecraftServer/func_1000 (Lnet/minecraft/entity/Entity;)V
        MD: bbb/func_2000 ()Z net/minecraft/entity/Entity/func_2000 ()Z
    """.trimIndent()

    private val sampleMethodsCsv = """
        searge,name,side,desc
        func_1000,getServer,0,"Returns the server instance"
        func_2000,isOnGround,0,"isOnGround, something"
    """.trimIndent()

    private val sampleFieldsCsv = """
        searge,name,side,desc
        field_1234,serverInstance,0,The server instance
    """.trimIndent()

    @Test
    fun `解析 MCP joined srg 与 CSV`() {
        val (methodNames, methodJavadocs) = McpParser.parseNameCsv(sampleMethodsCsv, withJavadoc = true)
        val (fieldNames, _) = McpParser.parseNameCsv(sampleFieldsCsv)
        val csv = McpParser.McpCsv(
            methods = methodNames,
            fields = fieldNames,
            methodJavadoc = methodJavadocs
        )
        val parsed = McpParser.parse(sampleSrg, csv)

        val classes = parsed.filter { it.type == "CLASS" }
        assertEquals(2, classes.size)
        assertEquals("net.minecraft.server.MinecraftServer", classes[0].className)
        assertEquals("aaa", classes[0].obfuscatedName)

        val field = parsed.first { it.type == "FIELD" }
        assertEquals("net.minecraft.server.MinecraftServer", field.className)
        assertEquals("field_1234", field.obfuscatedName)
        assertEquals("serverInstance", field.deobfuscatedName)
        assertNull(field.descriptor)

        val getServer = parsed.first { it.deobfuscatedName == "getServer" }
        assertEquals("func_1000", getServer.obfuscatedName)
        assertEquals("net.minecraft.server.MinecraftServer", getServer.className)
        assertEquals("(Lnet/minecraft/entity/Entity;)V", getServer.descriptor)
        assertEquals(listOf("net.minecraft.entity.Entity"), getServer.params)
        assertEquals("void", getServer.returnType)

        // 描述里带逗号（带引号）也能正确解析出 MCP 名
        val isOnGround = parsed.first { it.obfuscatedName == "func_2000" }
        assertEquals("isOnGround, something", isOnGround.javadoc)
        assertEquals("net.minecraft.entity.Entity", isOnGround.className)

        // 类 javadoc / 参数名（MCP 无 paramNames 合并）默认为空
        assertTrue(getServer.paramNames.isNullOrEmpty())
    }

    @Test
    fun `CSV 解析跳过表头并忽略空行`() {
        // 列：searge,name,side,desc —— MCP 名在 name（index 1）
        val (names, _) = McpParser.parseNameCsv(
            "searge,name,side,desc\n\nfunc_1,doSomething,0,Some javadoc\n"
        )
        assertEquals(mapOf("func_1" to "doSomething"), names)
    }
}
