package com.example.minecraftmixinhelper.domain.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TinyParser 测试：使用 tiny-remapper（FabricMC）官方测试资源中的真实 tiny v2 文件，
 * 覆盖类 / 方法 / 字段 / 参数行、多命名空间与描述符类名重映射。
 */
class TinyParserTest {

    private fun resource(name: String): String {
        val stream = TinyParserTest::class.java.getResourceAsStream("/$name")
            ?: error("测试资源缺失: $name")
        return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    @Test
    fun `解析真实 tiny v2 纯类文件（mapping1）`() {
        val parsed = TinyParser.parse(resource("mapping1.tiny"))
        assertEquals(6, parsed.size)
        assertTrue(parsed.all { it.type == "CLASS" })
        assertEquals("com.github.logicf.App", parsed[0].className)
        assertEquals("com.github.logicf.App", parsed[0].obfuscatedName)
        assertEquals("com.github.logicf.Main", parsed[0].deobfuscatedName)
    }

    @Test
    fun `解析真实 tiny v2 包迁移（mapping2）`() {
        val parsed = TinyParser.parse(resource("mapping2.tiny"))
        assertEquals(3, parsed.size)
        assertEquals("com.github.logicf.pkg1.D1", parsed[1].className)
    }

    @Test
    fun `解析真实 tiny v2 类方法字段（mapping3）`() {
        val parsed = TinyParser.parse(resource("mapping3.tiny"))
        val classes = parsed.filter { it.type == "CLASS" }
        val methods = parsed.filter { it.type == "METHOD" }
        val fields = parsed.filter { it.type == "FIELD" }

        assertEquals(4, classes.size)
        assertEquals(14, methods.size)
        assertEquals(3, fields.size)

        // 方法：owner 类名从源命名空间重映射到目标命名空间
        val someAbstract = methods.first { it.deobfuscatedName == "some_abstract_method" }
        assertEquals("pkg.c_test", someAbstract.className)
        assertEquals("someAbstractMethod", someAbstract.obfuscatedName)
        assertEquals("(ILjava/lang/String;DZ)V", someAbstract.descriptor)
        assertEquals(listOf("int", "java.lang.String", "double", "boolean"), someAbstract.params)
        assertEquals("void", someAbstract.returnType)

        // 描述符中的类名重映射（test/TestEnum -> pkg/c_test_enum）
        val enumVal = methods.first { it.deobfuscatedName == "m_enum_val" }
        assertEquals("()Lpkg/c_test_enum;", enumVal.descriptor)

        // 字段
        val field = fields.first { it.deobfuscatedName == "f_test1" }
        assertEquals("pkg.c_test_enum", field.className)
        assertEquals("Test1", field.obfuscatedName)
        assertEquals("Lpkg/c_test_enum;", field.descriptor)
    }

    @Test
    fun `解析 Yarn 风格 tiny v2（intermediary to named）`() {
        val raw = """
            tiny	2	0	intermediary	named
            c	net/minecraft/class_1	net/minecraft/world/entity/Entity
            	f	Lnet/minecraft/class_1;	field_1	level
            	m	(Lnet/minecraft/class_1;)Lnet/minecraft/class_1;	method_1	getLevel
        """.trimIndent()
        val parsed = TinyParser.parse(raw)

        val cls = parsed.first { it.type == "CLASS" }
        assertEquals("net.minecraft.world.entity.Entity", cls.className)
        assertEquals("net.minecraft.class_1", cls.obfuscatedName)
        assertEquals("net.minecraft.world.entity.Entity", cls.deobfuscatedName)

        val field = parsed.first { it.type == "FIELD" }
        assertEquals("level", field.deobfuscatedName)
        assertEquals("field_1", field.obfuscatedName)
        assertEquals("Lnet/minecraft/world/entity/Entity;", field.descriptor)

        val method = parsed.first { it.type == "METHOD" }
        assertEquals("getLevel", method.deobfuscatedName)
        assertEquals("(Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/world/entity/Entity;", method.descriptor)
    }

    @Test
    fun `非 tiny 文件抛异常`() {
        assertThrows(IllegalArgumentException::class.java) {
            TinyParser.parse("not a tiny file")
        }
    }
}
