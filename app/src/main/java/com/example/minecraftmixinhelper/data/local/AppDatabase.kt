package com.example.minecraftmixinhelper.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [VersionEntity::class, MappingEntity::class, MappingFts::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun mappingDao(): MappingDao
    abstract fun versionDao(): VersionDao
}