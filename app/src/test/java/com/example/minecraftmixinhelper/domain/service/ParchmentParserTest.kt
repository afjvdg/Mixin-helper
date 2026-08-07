package com.example.minecraftmixinhelper.domain.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ParchmentParser 测试：覆盖官方 versioned MDC 数组形态（Feather 规范）与
 * 旧 Map 形态，以及参数 index 的 this/long/double 槽位语义。
 */
class ParchmentParserTest {

    /** 官方数组形态样本（结构对齐 Feather MappingDataContainer 规范）。 */
    private val arrayFormJson = """
        {
          "version": "1.1.0",
          "packages": [
            { "name": "net/minecraft/world/entity/player", "javadoc": ["Player package"] }
          ],
          "classes": [
            {
              "name": "net/minecraft/world/entity/player/Player",
              "javadoc": ["A player.", ""],
              "methods": [
                {
                  "name": "getInventory",
                  "descriptor": "()Lnet/minecraft/world/entity/player/Inventory;",
                  "javadoc": ["Gets the inventory."],
                  "parameters": []
                },
                {
                  "name": "add",
                  "descriptor": "(Lnet/minecraft/world/entity/Entity;I)V",
                  "parameters": [
                    { "index": 1, "name": "entity" },
                    { "index": 2, "name": "amount" }
                  ]
                }
              ],
              "fields": [
                {
                  "name": "inventory",
                  "descriptor": "Lnet/minecraft/world/entity/player/Inventory;",
                  "javadoc": ["The inventory."]
                }
              ]
            }
          ]
        }
    """.trimIndent()

    @Test
    fun `解析官方数组形态`() {
        val data = ParchmentParser.parse(arrayFormJson)

        // 方法键：类名(点分)|方法名|描述符(斜杠)
        val getInventory = data.byMethod["net.minecraft.world.entity.player.Player|getInventory|()Lnet/minecraft/world/entity/player/Inventory;"]
        assertTrue("应能通过合并键找到方法", getInventory != null)
        assertEquals(emptyList<String>(), getInventory!!.paramNames)
        assertEquals("Gets the inventory.", getInventory.javadoc)

        val add = data.byMethod["net.minecraft.world.entity.player.Player|add|(Lnet/minecraft/world/entity/Entity;I)V"]
        assertTrue(add != null)
        assertEquals(listOf("entity", "amount"), add!!.paramNames)
        assertTrue(add.javadoc == null)

        // 类 javadoc（数组按行合并）
        assertEquals("A player.", data.classJavadoc["net.minecraft.world.entity.player.Player"])
    }

    @Test
    fun `参数 index 语义 long double 双槽位`() {
        val raw = """
            {
              "classes": [
                {
                  "name": "a/b/C",
                  "methods": [
                    {
                      "name": "m",
                      "descriptor": "(JID)V",
                      "parameters": [
                        { "index": 1, "name": "a" },
                        { "index": 3, "name": "b" },
                        { "index": 4, "name": "c" }
                      ]
                    }
                  ]
                }
              ]
            }
        """.trimIndent()
        val data = ParchmentParser.parse(raw)
        // 非静态方法：index 从 1 起（0 为隐式 this）；long(J) 占 2 个槽位、double(D) 占 2 个
        // 槽位：this=0, J=1(2), I=3, D=4(5) -> 参数名依次落在 index 1 / 3 / 4
        val info = data.byMethod["a.b.C|m|(JID)V"]
        assertTrue(info != null)
        assertEquals(listOf("a", "b", "c"), info!!.paramNames)
    }

    @Test
    fun `解析旧 Map 形态`() {
        val raw = """
            {
              "classes": {
                "net/minecraft/world/entity/player/Player": {
                  "javadoc": "A player.",
                  "methods": {
                    "getInventory()Lnet/minecraft/world/entity/player/Inventory;": {
                      "javadoc": "Gets inventory.",
                      "parameters": [ { "name": "self" } ]
                    }
                  },
                  "fields": {}
                }
              }
            }
        """.trimIndent()
        val data = ParchmentParser.parse(raw)

        val info = data.byMethod["net.minecraft.world.entity.player.Player|getInventory|()Lnet/minecraft/world/entity/player/Inventory;"]
        assertTrue(info != null)
        assertEquals(listOf("self"), info!!.paramNames)
        assertEquals("Gets inventory.", info.javadoc)
        assertEquals("A player.", data.classJavadoc["net.minecraft.world.entity.player.Player"])
    }

    @Test
    fun `Map 形态类名为斜杠形式时键归一化`() {
        val raw = """
            {
              "classes": {
                "net/minecraft/world/entity/player/Player": {
                  "methods": {
                    "getX()I": { "javadoc": "X." }
                  }
                }
              }
            }
        """.trimIndent()
        val data = ParchmentParser.parse(raw)
        assertTrue(data.byMethod.containsKey("net.minecraft.world.entity.player.Player|getX|()I"))
    }

    @Test
    fun `空或畸形输入安全返回`() {
        assertEquals(0, ParchmentParser.parse("").byMethod.size)
        assertEquals(0, ParchmentParser.parse("{}").byMethod.size)
        assertEquals(0, ParchmentParser.parse("not json").byMethod.size)
    }

    @Test
    fun `canonicalDescriptor 点斜杠归一`() {
        assertEquals(
            "(Lnet/minecraft/world/entity/Entity;)V",
            ParchmentParser.canonicalDescriptor("(Lnet.minecraft.world.entity.Entity;)V")
        )
        assertEquals(
            "(Lnet/minecraft/world/entity/Entity;)V",
            ParchmentParser.canonicalDescriptor("(Lnet/minecraft/world/entity/Entity;)V")
        )
    }
}
