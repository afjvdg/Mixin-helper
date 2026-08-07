package com.example.minecraftmixinhelper.domain.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class McVersionComparatorTest {

    @Test
    fun `相同版本相等`() {
        assertEquals(0, McVersionComparator.compare("1.20.1", "1.20.1"))
        assertTrue(McVersionComparator.ge("1.20.1", "1.20.1"))
    }

    @Test
    fun `主版本号比较`() {
        assertTrue(McVersionComparator.ge("1.21", "1.20.1"))
        assertFalse(McVersionComparator.ge("1.19", "1.20.1"))
        assertTrue(McVersionComparator.ge("1.20.10", "1.20.9"))
        assertTrue(McVersionComparator.ge("26.2", "1.21.4"))
    }

    @Test
    fun `带后缀版本号比较`() {
        // 数字元组逐位比较，非数字片段按 0 处理
        assertTrue(McVersionComparator.ge("1.21.4", "1.21.4-pre1"))
        assertTrue(McVersionComparator.ge("1.20.1-47.2.0", "1.20.1"))
        assertTrue(McVersionComparator.ge("1.21.11", "1.21.4"))
    }

    @Test
    fun `forge 版本号解析 MC 版本`() {
        assertEquals("1.20.1", McVersionComparator.mcVersionOf("forge", "1.20.1-47.2.0"))
        assertEquals("1.21", McVersionComparator.mcVersionOf("forge", "1.21-51.0.33"))
        assertEquals("1.21.4", McVersionComparator.mcVersionOf("forge", "1.21.4-54.0.38"))
        assertNull(McVersionComparator.mcVersionOf("forge", "garbage-version"))
    }

    @Test
    fun `neoforge 版本号解析 MC 版本`() {
        assertEquals("1.21.1", McVersionComparator.mcVersionOf("neoforge", "21.1.78"))
        assertEquals("1.21", McVersionComparator.mcVersionOf("neoforge", "21.0.144"))
        assertEquals("1.20.4", McVersionComparator.mcVersionOf("neoforge", "20.4.237"))
        assertEquals("1.20.6", McVersionComparator.mcVersionOf("neoforge", "20.6.119"))
        assertEquals("26.2", McVersionComparator.mcVersionOf("neoforge", "26.2.0.49-beta"))
        assertNull(McVersionComparator.mcVersionOf("neoforge", "abc"))
    }

    @Test
    fun `映射类型决策`() {
        // fabric 始终 Yarn
        assertEquals("yarn", McVersionComparator.decideMappingType("1.20.1", "fabric"))
        assertEquals("yarn", McVersionComparator.decideMappingType("26.2", "fabric"))
        // forge / neoforge：1.18+ 用 Parchment，更早用 Mojmap
        assertEquals("parchment", McVersionComparator.decideMappingType("1.20.1", "forge"))
        assertEquals("parchment", McVersionComparator.decideMappingType("1.18", "neoforge"))
        assertEquals("mojmap", McVersionComparator.decideMappingType("1.17.1", "forge"))
        assertEquals("mojmap", McVersionComparator.decideMappingType("1.12.2", "forge"))
        // 其他加载器默认 Mojmap
        assertEquals("mojmap", McVersionComparator.decideMappingType("1.20.1", "mojang"))
        assertEquals("mojmap", McVersionComparator.decideMappingType("1.20.1", "parchment"))
    }
}
