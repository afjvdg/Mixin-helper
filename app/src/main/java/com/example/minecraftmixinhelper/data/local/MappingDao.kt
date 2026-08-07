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

    // 按字段搜索（可读名 / 混淆名 / 类名），用于「搜索字段」选择
    @Query("SELECT * FROM mappings WHERE deobfuscatedName LIKE '%' || :query || '%' LIMIT 300")
    suspend fun searchByDeobfuscated(query: String): List<MappingEntity>

    @Query("SELECT * FROM mappings WHERE obfuscatedName LIKE '%' || :query || '%' LIMIT 300")
    suspend fun searchByObfuscated(query: String): List<MappingEntity>

    @Query("SELECT * FROM mappings WHERE className LIKE '%' || :query || '%' LIMIT 300")
    suspend fun searchByClassName(query: String): List<MappingEntity>

    // 使用子查询避免 FTS 表别名混用（修复原 JOIN 写法）
    @Query(
        """
        SELECT m.* FROM mappings m
        WHERE m.id IN (SELECT rowid FROM mappings_fts WHERE mappings_fts MATCH :query)
        LIMIT 200
        """
    )
    suspend fun fuzzySearchFts(query: String): List<MappingEntity>

    // 实时建议：与 fuzzySearchFts 同构，仅取前 limit 条（搜索框下拉建议）
    @Query(
        """
        SELECT m.* FROM mappings m
        WHERE m.id IN (SELECT rowid FROM mappings_fts WHERE mappings_fts MATCH :query)
        LIMIT :limit
        """
    )
    suspend fun suggestFts(query: String, limit: Int): List<MappingEntity>

    // 已下载版本列表（搜索页版本选择器数据源）
    @Query("SELECT DISTINCT version FROM mappings WHERE version <> '' ORDER BY version DESC")
    suspend fun getDownloadedVersions(): List<String>

    // 已下载的「版本 + 加载器」对（搜索页版本范围选择，同一 MC 版本不同加载器需区分标注）
    @Query("SELECT DISTINCT version, loader FROM mappings WHERE version <> '' ORDER BY version DESC, loader ASC")
    suspend fun getDownloadedVersionLoaders(): List<VersionLoaderRow>
}
