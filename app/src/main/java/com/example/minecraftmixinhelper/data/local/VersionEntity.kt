package com.example.minecraftmixinhelper.data.local

import androidx.room.Entity

/**
 * 版本条目。主键 `id` 为复合键 `$version|$loader` 的字符串形式。
 * `versionJsonUrl` 保存真实映射 URL，下载时直接使用（修复原来写死的假 URL）。
 */
@Entity(tableName = "versions", primaryKeys = ["id"])
data class VersionEntity(
    val id: String,               // "$version|$loader"
    val version: String,
    val loader: String,           // mojang / fabric / forge / neoforge / parchment
    val mappingType: String,      // mojmap / yarn / mcp / parchment
    val isCached: Boolean = false,
    val versionJsonUrl: String = "",
    val lastUpdated: Long = System.currentTimeMillis()
)
