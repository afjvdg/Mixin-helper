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

    @Query("SELECT * FROM mappings WHERE deobfuscatedName LIKE '%' || :query || '%' OR obfuscatedName LIKE '%' || :query || '%' OR className LIKE '%' || :query || '%' LIMIT 100")
    fun searchMappings(query: String): Flow<List<MappingEntity>>

    @Query("""
        SELECT m.* FROM mappings m 
        JOIN mappings_fts fts ON m.id = fts.rowid 
        WHERE mappings_fts MATCH :query 
        LIMIT 100
    """)
    suspend fun fuzzySearchFts(query: String): List<MappingEntity>
}