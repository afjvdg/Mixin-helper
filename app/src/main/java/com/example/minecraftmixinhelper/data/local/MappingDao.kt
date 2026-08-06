package com.example.minecraftmixinhelper.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MappingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(mappings: List<MappingEntity>)

    @Query(
        "SELECT * FROM mappings " +
            "WHERE deobfuscatedName LIKE '%' || :query || '%' " +
            "OR obfuscatedName LIKE '%' || :query || '%' " +
            "OR className LIKE '%' || :query || '%' LIMIT 200"
    )
    fun searchMappings(query: String): Flow<List<MappingEntity>>

    // 使用子查询避免 FTS 表别名混用（修复原 JOIN 写法）
    @Query(
        """
        SELECT m.* FROM mappings m
        WHERE m.id IN (SELECT rowid FROM mappings_fts WHERE mappings_fts MATCH :query)
        LIMIT 200
        """
    )
    suspend fun fuzzySearchFts(query: String): List<MappingEntity>

    // 已下载版本列表（搜索页版本选择器数据源）
    @Query("SELECT DISTINCT version FROM mappings WHERE version <> '' ORDER BY version DESC")
    suspend fun getDownloadedVersions(): List<String>
}
