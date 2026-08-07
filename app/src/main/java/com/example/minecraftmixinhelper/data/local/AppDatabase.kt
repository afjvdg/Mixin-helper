package com.example.minecraftmixinhelper.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [VersionEntity::class, MappingEntity::class, MappingFts::class],
    version = 3,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun mappingDao(): MappingDao
    abstract fun versionDao(): VersionDao

    companion object {
        // v1 -> v2: mappings 增加 version / loader 列；versions 改为复合主键 id(="$version|$loader") 并增加 versionJsonUrl
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE mappings ADD COLUMN version TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE mappings ADD COLUMN loader TEXT NOT NULL DEFAULT ''")

                db.execSQL(
                    """
                    CREATE TABLE versions_new (
                        id TEXT NOT NULL,
                        version TEXT NOT NULL,
                        loader TEXT NOT NULL,
                        mappingType TEXT NOT NULL,
                        isCached INTEGER NOT NULL,
                        versionJsonUrl TEXT NOT NULL,
                        lastUpdated INTEGER NOT NULL,
                        PRIMARY KEY(id)
                    )
                    """
                )
                db.execSQL(
                    """
                    INSERT INTO versions_new (id, version, loader, mappingType, isCached, versionJsonUrl, lastUpdated)
                    SELECT (version || '|' || loader), version, loader, mappingType, isCached, '', lastUpdated
                    FROM versions
                    """
                )
                db.execSQL("DROP TABLE versions")
                db.execSQL("ALTER TABLE versions_new RENAME TO versions")
            }
        }

        // v2 -> v3: mappings 增加 paramNames / javadoc 列（来自 Parchment）
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE mappings ADD COLUMN paramNames TEXT")
                db.execSQL("ALTER TABLE mappings ADD COLUMN javadoc TEXT")
            }
        }
    }
}
