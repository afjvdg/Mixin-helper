package com.example.minecraftmixinhelper.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "versions")
data class VersionEntity(
    @PrimaryKey val version: String,
    val loader: String,
    val mappingType: String,
    val isCached: Boolean = false,
    val lastUpdated: Long = System.currentTimeMillis()
)