package com.twinlore.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Single source of truth for the Room schema version. Referenced by
 *  the @Database annotation AND by LibraryAutoSaveObserver when it
 *  writes the library_meta row, so a future schema bump only edits one
 *  place. */
const val SCHEMA_VERSION = 39

/**
 * Room database for vault storage
 */
@Database(
    entities = [
        VaultEntity::class,
        SavedTabEntity::class,
        RaGameCacheEntity::class,
        SteamGameCacheEntity::class,
        SavedMapEntity::class,
        ChecklistTagEntity::class,
        ChecklistItemEntity::class,
        ChecklistTabEntity::class,
        ChecklistSectionEntity::class,
        FavoriteAppEntity::class,
        ResourceFileEntity::class,
        MapMarkerEntity::class,
        PageBookmarkEntity::class,
        ScrollMarkerEntity::class,
        MediaSearchPathEntity::class,
        MediaSearchIndexEntity::class,
        MediaSearchDirEntity::class,
        LibraryMetaEntity::class,
        BrowserTabEntity::class,
        PageGroupEntryEntity::class,
        PageGroupEntryAnchorEntity::class,
        VaultSyncStateEntity::class,
        SyncTombstoneEntity::class,
        PlaySessionEntity::class,
        JourneyGameCacheEntity::class,
        JourneyCompletionCacheEntity::class
    ],
    version = SCHEMA_VERSION,
    exportSchema = true
)
@TypeConverters(DateConverters::class)
abstract class VaultDatabase : RoomDatabase() {
    abstract fun vaultDao(): VaultDao
    abstract fun savedTabDao(): SavedTabDao
    abstract fun raGameCacheDao(): RaGameCacheDao
    abstract fun steamGameCacheDao(): SteamGameCacheDao
    abstract fun savedMapDao(): SavedMapDao
    abstract fun checklistTagDao(): ChecklistTagDao
    abstract fun checklistItemDao(): ChecklistItemDao
    abstract fun checklistTabDao(): ChecklistTabDao
    abstract fun checklistSectionDao(): ChecklistSectionDao
    abstract fun favoriteAppDao(): FavoriteAppDao
    abstract fun resourceFileDao(): ResourceFileDao
    abstract fun mapMarkerDao(): MapMarkerDao
    abstract fun pageBookmarkDao(): PageBookmarkDao
    abstract fun scrollMarkerDao(): ScrollMarkerDao
    abstract fun mediaSearchPathDao(): MediaSearchPathDao
    abstract fun mediaSearchIndexDao(): MediaSearchIndexDao
    abstract fun mediaSearchDirDao(): MediaSearchDirDao
    abstract fun libraryMetaDao(): LibraryMetaDao
    abstract fun browserTabDao(): BrowserTabDao
    abstract fun pageGroupEntryDao(): PageGroupEntryDao
    abstract fun pageGroupEntryAnchorDao(): PageGroupEntryAnchorDao
    abstract fun vaultSyncStateDao(): VaultSyncStateDao
    abstract fun syncTombstoneDao(): SyncTombstoneDao
    abstract fun playSessionDao(): PlaySessionDao
    abstract fun journeyCacheDao(): JourneyCacheDao

    companion object {
        val MIGRATION_23_24 = object : Migration(23, 24) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS library_meta (
                        id INTEGER PRIMARY KEY NOT NULL,
                        schemaVersion INTEGER NOT NULL,
                        lastWrittenAtMillis INTEGER NOT NULL,
                        lastWrittenDeviceId TEXT NOT NULL,
                        lastWrittenDeviceLabel TEXT NOT NULL
                    )
                """.trimIndent())
            }
        }
    }
}
