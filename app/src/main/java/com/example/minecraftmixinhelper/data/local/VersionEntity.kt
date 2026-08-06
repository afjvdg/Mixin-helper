package com.example.minecraftmixinhelper.data.local

import androidx.room.Entity

@Entity(tableName = "versions", primaryKeys = ["version", "loader"])
data class VersionEntity(
    val version: String,
    val loader: String,
    val mappingType: String,
    val versionUrl: String? = null,
    val isCached: Boolean = false,
    val lastUpdated: Long = System.currentTimeMillis()
)