package com.example.minecraftmixinhelper.domain.service

import org.junit.Assert.assertEquals
import org.junit.Test

class AsmDescriptorBuilderTest {

    @Test
    fun `基本类型转描述符`() {
        assertEquals("V", AsmDescriptorBuilder.toDescriptor("void"))
        assertEquals("I", AsmDescriptorBuilder.toDescriptor("int"))
        assertEquals("Z", AsmDescriptorBuilder.toDescriptor("boolean"))
        assertEquals("J", AsmDescriptorBuilder.toDescriptor("long"))
        assertEquals("D", AsmDescriptorBuilder.toDescriptor("double"))
    }

    @Test
    fun `对象类型点分转斜杠`() {
        assertEquals(
            "Lnet/minecraft/world/entity/Entity;",
            AsmDescriptorBuilder.toDescriptor("net.minecraft.world.entity.Entity")
        )
        // 斜杠输入原样保留
        assertEquals(
            "Lnet/minecraft/world/entity/Entity;",
            AsmDescriptorBuilder.toDescriptor("net/minecraft/world/entity/Entity")
        )
    }

    @Test
    fun `数组递归转换`() {
        assertEquals("[I", AsmDescriptorBuilder.toDescriptor("int[]"))
        assertEquals("[[Z", AsmDescriptorBuilder.toDescriptor("boolean[][]"))
        assertEquals(
            "[Ljava/lang/String;",
            AsmDescriptorBuilder.toDescriptor("java.lang.String[]")
        )
    }

    @Test
    fun `构建方法描述符`() {
        assertEquals(
            "(Lnet/minecraft/world/entity/Entity;I)V",
            AsmDescriptorBuilder.buildMethodDescriptor(
                listOf("net.minecraft.world.entity.Entity", "int"), "void"
            )
        )
        assertEquals(
            "()Lnet/minecraft/world/entity/player/Inventory;",
            AsmDescriptorBuilder.buildMethodDescriptor(
                emptyList(), "net.minecraft.world.entity.player.Inventory"
            )
        )
    }
}
