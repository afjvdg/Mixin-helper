package com.example.minecraftmixinhelper.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "mappings")
data class MappingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val className: String,
    val obfuscatedName: String,
    val deobfuscatedName: String,
    val type: String, // CLASS, METHOD, FIELD
    val descriptor: String? = null,
    val params: String? = null, // JSON array string
    val returnType: String? = null
)