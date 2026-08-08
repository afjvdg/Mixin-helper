package com.example.minecraftmixinhelper.domain.service

import com.example.minecraftmixinhelper.domain.service.MappingIndex.MappingEntityRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MappingIndexTest {

    private fun ref(
        id: Long, deobf: String, obf: String, cls: String,
        type: String = "METHOD", version: String = "1.20.1", loader: String = "mojmap"
    ) = MappingEntityRef(id, cls, obf, deobf, type, version, loader)

    private val sample = listOf(
        ref(1, "Player", "gfj", "net.minecraft.world.entity.player.Player", "CLASS"),
        ref(2, "getHealth", "func_1000_a", "net.minecraft.world.entity.player.Player"),
        ref(3, "getBlock", "func_2000_b", "net.minecraft.world.level.block.Block"),
        ref(4, "playInventory", "func_3000_c", "net.minecraft.world.entity.player.Inventory"),
        ref(5, "getHealth", "func_1000_a", "net.minecraft.world.entity.player.Player", "METHOD", "1.21.1"),
        ref(6, "isOnGround", "field_6277", "net.minecraft.world.entity.Entity", "FIELD")
    )

    @Test
    fun `前缀匹配命中正确`() {
        val idx = MappingIndex()
        idx.rebuild(sample)
        // "get" 命中 getHealth(2,5) + getBlock(3)
        val (get, _) = idx.search("get", "", "", "", "", 100)
        assertEquals(setOf(2L, 3L, 5L), get.map { it.id }.toSet())
        // 大小写不敏感：Player 命中 player
        val (p, _) = idx.search("player", "deobf", "", "", "", 100)
        assertEquals(listOf(1L), p.map { it.id })
        // pl 命中 playInventory(4) 与 Player(1)
        val (pl, _) = idx.search("pl", "deobf", "", "", "", 100)
        assertEquals(setOf(1L, 4L), pl.map { it.id }.toSet())
    }

    @Test
    fun `类名搜索支持简单类名`() {
        val idx = MappingIndex()
        idx.rebuild(sample)
        // 简单类名 "player" 应命中 Player(1,2,5)（className 以 ...Player 结尾）
        val (bySimple, _) = idx.search("player", "class", "", "", "", 100)
        val simpleIds = bySimple.map { it.id }.toSet()
        assertTrue(simpleIds.contains(1L))
        assertTrue(simpleIds.contains(2L))
        assertTrue(simpleIds.contains(5L))
        // 完整点分前缀也可命中
        val (byFull, _) = idx.search("net.minecraft.world", "class", "", "", "", 100)
        assertTrue(byFull.map { it.id }.contains(1L))
        // "Inventory" 简单名命中 4
        val (inv, _) = idx.search("inventory", "class", "", "", "", 100)
        assertTrue(inv.map { it.id }.contains(4L))
    }

    @Test
    fun `全部字段是各单独字段的并集超集`() {
        val idx = MappingIndex()
        idx.rebuild(sample)
        val (all, _) = idx.search("get", "", "", "", "", 100)
        val (deobf, _) = idx.search("get", "deobf", "", "", "", 100)
        val (obf, _) = idx.search("get", "obf", "", "", "", 100)
        val (cls, _) = idx.search("get", "class", "", "", "", 100)
        val allIds = all.map { it.id }.toSet()
        assertTrue(allIds.containsAll(deobf.map { it.id }))
        assertTrue(allIds.containsAll(obf.map { it.id }))
        assertTrue(allIds.containsAll(cls.map { it.id }))
        // "全部" 至少不比任一单独字段少
        assertTrue(allIds.size >= deobf.size)
        assertTrue(allIds.size >= obf.size)
        assertTrue(allIds.size >= cls.size)
    }

    @Test
    fun `类型与版本过滤器生效`() {
        val idx = MappingIndex()
        idx.rebuild(sample)
        val (all, _) = idx.search("getHealth", "deobf", "", "", "", 100)
        assertEquals(setOf(2L, 5L), all.map { it.id }.toSet())
        // 限定版本 1.21.1 -> 只剩 5
        val (v, _) = idx.search("getHealth", "deobf", "", "1.21.1", "", 100)
        assertEquals(listOf(5L), v.map { it.id })
        // 限定 type=METHOD -> 2 和 5 都是 METHOD
        val (t, _) = idx.search("getHealth", "deobf", "METHOD", "", "", 100)
        assertEquals(setOf(2L, 5L), t.map { it.id }.toSet())
    }

    @Test
    fun `结果截断与 tooMany`() {
        val idx = MappingIndex()
        idx.rebuild(sample)
        val (res, tooMany) = idx.search("get", "", "", "", "", 2)
        assertEquals(2, res.size)
        assertTrue(tooMany)
        // limit 足够时 not tooMany
        val (res2, tm2) = idx.search("get", "", "", "", "", 100)
        assertTrue(res2.size >= 3)
        assertFalse(tm2)
    }

    @Test
    fun `空前缀返回空`() {
        val idx = MappingIndex()
        idx.rebuild(sample)
        val (res, _) = idx.search("", "", "", "", "", 100)
        assertTrue(res.isEmpty())
    }
}
