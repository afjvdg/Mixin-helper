package com.example.minecraftmixinhelper.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface MappingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(mappings: List<MappingEntity>)

    // 已下载版本列表（搜索页版本选择器数据源）
    @Query("SELECT DISTINCT version FROM mappings WHERE version <> '' ORDER BY version DESC")
    suspend fun getDownloadedVersions(): List<String>

    // 已下载的「版本 + 加载器」对（搜索页版本范围选择，同一 MC 版本不同加载器需区分标注）
    @Query("SELECT DISTINCT version, loader FROM mappings WHERE version <> '' ORDER BY version DESC, loader ASC")
    suspend fun getDownloadedVersionLoaders(): List<VersionLoaderRow>

    // 内存索引用的轻量全量行（不含大字段，节省内存；搜索后按需回填完整实体）
    @Query("SELECT id, className, obfuscatedName, deobfuscatedName, type, version, loader FROM mappings")
    suspend fun getAllIndexRows(): List<IndexRow>

    // 按 id 批量回填完整实体（前缀搜索命中后）
    @Query("SELECT * FROM mappings WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<MappingEntity>
}
