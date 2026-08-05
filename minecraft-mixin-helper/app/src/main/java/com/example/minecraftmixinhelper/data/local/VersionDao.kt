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
}