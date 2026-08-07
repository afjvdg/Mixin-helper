package com.example.minecraftmixinhelper.domain.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AsmDescriptorParserTest {

    @Test
    fun `解析基本类型方法描述符`() {
        val parsed = AsmDescriptorParser.parse("()V")
        assertEquals(emptyList<String>(), parsed.parameters)
        assertEquals("void", parsed.returnType)
    }

    @Test
    fun `解析对象与基本类型混合参数`() {
        val parsed = AsmDescriptorParser.parse("(Lnet/minecraft/world/entity/Entity;I)V")
        assertEquals(listOf("net.minecraft.world.entity.Entity", "int"), parsed.parameters)
        assertEquals("void", parsed.returnType)
    }

    @Test
    fun `解析数组与多维数组`() {
        val parsed = AsmDescriptorParser.parse("([I[[Ljava/lang/String;Z)J")
        assertEquals(listOf("int[]", "java.lang.String[][]", "boolean"), parsed.parameters)
        assertEquals("long", parsed.returnType)
    }

    @Test
    fun `解析无参有返回值`() {
        val parsed = AsmDescriptorParser.parse("()Lnet/minecraft/core/BlockPos;")
        assertEquals(emptyList<String>(), parsed.parameters)
        assertEquals("net.minecraft.core.BlockPos", parsed.returnType)
    }

    @Test
    fun `字段描述符单类型`() {
        assertEquals("double", AsmDescriptorParser.parseType("D"))
        assertEquals("net.minecraft.world.level.Level", AsmDescriptorParser.parseType("Lnet/minecraft/world/level/Level;"))
        assertEquals("void", AsmDescriptorParser.parseType(""))
        assertEquals("java.lang.String[]", AsmDescriptorParser.parseType("[Ljava/lang/String;"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `非法描述符缺少左括号时抛异常`() {
        AsmDescriptorParser.parse("I)V")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `非法描述符缺少右括号时抛异常`() {
        AsmDescriptorParser.parse("(I")
    }

    @Test
    fun `对象描述符未闭合时抛异常`() {
        assertThrows(IllegalArgumentException::class.java) {
            AsmDescriptorParser.parse("(Lnet/minecraft/Foo)V")
        }
    }

    @Test
    fun `合法对象描述符不抛异常`() {
        val parsed = AsmDescriptorParser.parse("(Lnet/minecraft/Foo;)V")
        assertEquals(listOf("net.minecraft.Foo"), parsed.parameters)
    }
}
