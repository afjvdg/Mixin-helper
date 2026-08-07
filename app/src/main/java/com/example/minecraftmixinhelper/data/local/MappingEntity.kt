package com.example.minecraftmixinhelper.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "mappings",
    indices = [
        Index(value = ["version"]),
        Index(value = ["loader"]),
        Index(value = ["type"])
    ]
)
data class MappingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val className: String,
    val obfuscatedName: String,
    val deobfuscatedName: String,
    val type: String, // CLASS, METHOD, FIELD
    val descriptor: String? = null,
    val params: String? = null,           // 逗号分隔的参数类型列表
    val returnType: String? = null,
    val version: String = "",
    val loader: String = "",
    val paramNames: List<String>? = null, // 来自 Parchment，使用 Converters 序列化
    val javadoc: String? = null           // 来自 Parchment
)
