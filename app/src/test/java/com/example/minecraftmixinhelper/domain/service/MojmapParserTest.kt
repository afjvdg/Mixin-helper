package com.example.minecraftmixinhelper.domain.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MojmapParser 针对「真实 Mojang client_mappings.txt」格式的测试。
 *
 * 真实格式（依据 ParchmentMC/Feather 官方 io-proguard 解析器源码核对）：
 * ```
 * net.minecraft.world.entity.player.Player -> gfj:
 *     147:159:void add(net.minecraft.world.entity.Entity, int) -> method_1234
 *     net.minecraft.world.level.Level level -> field_1234
 * ```
 * 方法行带 `起始行:结束行:` 前缀、返回类型在前、参数逗号分隔、类型为可读形式。
 */
class MojmapParserTest {

    private val realStyleMappings = """
        # compiler: ProGuard
        # 示例：结构完全对齐 Mojang 官方 client_mappings.txt
        net.minecraft.world.entity.player.Player -> gfj:
            2:2:net.minecraft.world.entity.player.Inventory getInventory() -> method_1234
            1:1:boolean isCreative() -> method_5678
            3:4:void add(net.minecraft.world.entity.Entity, int, java.lang.String) -> method_9999
            net.minecraft.world.level.Level level -> field_100
            int x -> field_101
        net.minecraft.world.level.Level -> abb:
            7:9:net.minecraft.core.BlockPos getBlockPos(net.minecraft.core.BlockPos) -> method_777
            net.minecraft.core.BlockPos spawnPoint -> field_200
        net.minecraft.client.player.LocalPlayer -> qqq:
    """.trimIndent()

    @Test
    fun `解析真实格式类行`() {
        val parsed = MojmapParser.parse(realStyleMappings)
        val classes = parsed.filter { it.type == "CLASS" }
        assertEquals(3, classes.size)

        val player = classes[0]
        assertEquals("net.minecraft.world.entity.player.Player", player.className)
        assertEquals("gfj", player.obfuscatedName)
        assertEquals("net.minecraft.world.entity.player.Player", player.deobfuscatedName)

        // 无成员的类行（冒号结尾）也应解析
        assertTrue(classes.any { it.className == "net.minecraft.client.player.LocalPlayer" && it.obfuscatedName == "qqq" })
    }

    @Test
    fun `解析真实格式方法行（行号前缀 + 可读类型）`() {
        val parsed = MojmapParser.parse(realStyleMappings)
        val methods = parsed.filter { it.type == "METHOD" }
        assertEquals(3, methods.size)

        val getInventory = methods.first { it.deobfuscatedName == "getInventory" }
        assertEquals("method_1234", getInventory.obfuscatedName)
        assertEquals("net.minecraft.world.entity.player.Player", getInventory.className)
        assertEquals("()Lnet/minecraft/world/entity/player/Inventory;", getInventory.descriptor)
        assertEquals(emptyList<String>(), getInventory.params)
        assertEquals("net.minecraft.world.entity.player.Inventory", getInventory.returnType)

        val add = methods.first { it.deobfuscatedName == "add" }
        assertEquals(
            "(Lnet/minecraft/world/entity/Entity;ILjava/lang/String;)V",
            add.descriptor
        )
        assertEquals(
            listOf("net.minecraft.world.entity.Entity", "int", "java.lang.String"),
            add.params
        )
        assertEquals("void", add.returnType)

        val getBlockPos = methods.first { it.deobfuscatedName == "getBlockPos" }
        assertEquals("net.minecraft.world.level.Level", getBlockPos.className)
        assertEquals(
            "(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/BlockPos;",
            getBlockPos.descriptor
        )
    }

    @Test
    fun `解析真实格式字段行（类型 名称至混淆名）`() {
        val parsed = MojmapParser.parse(realStyleMappings)
        val fields = parsed.filter { it.type == "FIELD" }
        assertEquals(3, fields.size)

        val level = fields.first { it.deobfuscatedName == "level" }
        assertEquals("field_100", level.obfuscatedName)
        assertEquals("Lnet/minecraft/world/level/Level;", level.descriptor)
        assertEquals("net.minecraft.world.level.Level", level.returnType)

        val x = fields.first { it.deobfuscatedName == "x" }
        assertEquals("I", x.descriptor)
    }

    @Test
    fun `数组返回类型与方法名中的尖括号`() {
        val raw = """
            net.minecraft.world.entity.player.Player -> gfj:
                5:6:net.minecraft.core.BlockPos[] getPoses() -> method_1
                7:8:void <init>(int) -> method_2
        """.trimIndent()
        val parsed = MojmapParser.parse(raw)
        val methods = parsed.filter { it.type == "METHOD" }
        assertEquals(2, methods.size)

        val getPoses = methods.first { it.deobfuscatedName == "getPoses" }
        assertEquals("[Lnet/minecraft/core/BlockPos;", getPoses.descriptor)

        val init = methods.first { it.deobfuscatedName == "<init>" }
        assertEquals("(I)V", init.descriptor)
    }

    @Test
    fun `经典 ProGuard 格式回退仍可解析`() {
        val raw = """
            net.minecraft.world.entity.Entity -> va:
                method_1(Lnet/minecraft/world/level/Level;)Z -> m_1_
                field_1:I -> f_1_
        """.trimIndent()
        val parsed = MojmapParser.parse(raw)

        val method = parsed.first { it.type == "METHOD" }
        assertEquals("method_1", method.deobfuscatedName)
        assertEquals("m_1_", method.obfuscatedName)
        assertEquals("(Lnet/minecraft/world/level/Level;)Z", method.descriptor)
        assertEquals(listOf("net.minecraft.world.level.Level"), method.params)
        assertEquals("boolean", method.returnType)

        val field = parsed.first { it.type == "FIELD" }
        assertEquals("field_1", field.deobfuscatedName)
        assertEquals("f_1_", field.obfuscatedName)
        assertEquals("I", field.descriptor)
    }

    @Test
    fun `空输入与注释行安全返回`() {
        assertEquals(emptyList<MojmapParser.ParsedMapping>(), MojmapParser.parse(""))
        assertEquals(emptyList<MojmapParser.ParsedMapping>(), MojmapParser.parse("# 只有注释\n\n"))
    }
}
