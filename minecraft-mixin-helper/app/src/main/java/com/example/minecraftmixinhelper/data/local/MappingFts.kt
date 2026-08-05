package com.example.minecraftmixinhelper.data.local

import androidx.room.Entity
import androidx.room.Fts4

@Fts4(contentEntity = MappingEntity::class)
@Entity(tableName = "mappings_fts")
data class MappingFts(
    val className: String,
    val deobfuscatedName: String,
    val obfuscatedName: String
)