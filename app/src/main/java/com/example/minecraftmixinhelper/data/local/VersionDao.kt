package com.example.minecraftmixinhelper.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface VersionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(versions: List<VersionEntity>)

    @Query("SELECT * FROM versions ORDER BY lastUpdated DESC")
    fun getAllVersions(): Flow<List<VersionEntity>>

    @Query("SELECT * FROM versions WHERE version = :version AND loader = :loader LIMIT 1")
    suspend fun getVersion(version: String, loader: String): VersionEntity?

    @Query("UPDATE versions SET isCached = 1, lastUpdated = :ts WHERE version = :version AND loader = :loader")
    suspend fun markCached(version: String, loader: String, ts: Long = System.currentTimeMillis())
}
