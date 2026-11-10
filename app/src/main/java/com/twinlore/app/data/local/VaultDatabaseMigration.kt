package com.twinlore.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Database migrations for VaultDatabase
 */
object VaultDatabaseMigration {
    /**
     * Migration from version 1 to 2
     * Adds systemeid, gameId, and source fields
     */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Add new columns with default null values
            database.execSQL("ALTER TABLE vaults ADD COLUMN systemeid TEXT")
            database.execSQL("ALTER TABLE vaults ADD COLUMN gameId TEXT")
            database.execSQL("ALTER TABLE vaults ADD COLUMN source TEXT")
        }
    }
    
    /**
     * Migration from version 2 to 3
     * Adds saved_tabs table for browser tabs
     */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS saved_tabs (
                    id TEXT NOT NULL PRIMARY KEY,
                    vaultId TEXT NOT NULL,
                    title TEXT NOT NULL,
                    url TEXT NOT NULL,
                    scrollPosition INTEGER NOT NULL DEFAULT 0,
                    zoomLevel REAL NOT NULL DEFAULT 1.0,
                    isFavorite INTEGER NOT NULL DEFAULT 0,
                    isMap INTEGER NOT NULL DEFAULT 0,
                    isCurrentContent INTEGER NOT NULL DEFAULT 0,
                    tab_order INTEGER NOT NULL DEFAULT 0,
                    createdAt INTEGER NOT NULL,
                    lastAccessed INTEGER NOT NULL,
                    FOREIGN KEY(vaultId) REFERENCES vaults(id) ON DELETE CASCADE
                )
            """.trimIndent())
            database.execSQL("CREATE INDEX IF NOT EXISTS index_saved_tabs_vaultId ON saved_tabs(vaultId)")
        }
    }

    /**
     * Migration from version 3 to 4
     * Adds raGameId to vaults for RetroAchievements game matching
     */
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE vaults ADD COLUMN raGameId TEXT")
        }
    }

    /**
     * Migration from version 4 to 5
     * Adds ra_game_cache table for per-game achievement cache
     */
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS ra_game_cache (
                    vaultId TEXT NOT NULL PRIMARY KEY,
                    raGameId INTEGER NOT NULL,
                    jsonSnapshot TEXT NOT NULL,
                    lastSyncedAt INTEGER NOT NULL
                )
            """.trimIndent())
        }
    }

    /**
     * Migration from version 5 to 6
     * Adds highestAwardKind to ra_game_cache (from API_GetUserCompletionProgress)
     */
    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE ra_game_cache ADD COLUMN highestAwardKind TEXT")
        }
    }

    /**
     * Migration from version 6 to 7
     * Adds userRank to ra_game_cache (from API_GetUserGameRankAndScore) so cached progress shows correct rank per vault.
     */
    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE ra_game_cache ADD COLUMN userRank INTEGER")
        }
    }

    /**
     * Migration from version 7 to 8
     * Adds artworkVibrantArgb to vaults for per-vault focus/glow color (computed once when artwork is assigned).
     */
    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE vaults ADD COLUMN artworkVibrantArgb INTEGER")
        }
    }

    /**
     * Migration from version 8 to 9
     * Adds guide file type support: fileType, fileName, lastPdfPage, pdfScrollOffset on saved_tabs.
     */
    val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE saved_tabs ADD COLUMN fileType TEXT NOT NULL DEFAULT 'html'")
            database.execSQL("ALTER TABLE saved_tabs ADD COLUMN fileName TEXT")
            database.execSQL("ALTER TABLE saved_tabs ADD COLUMN lastPdfPage INTEGER NOT NULL DEFAULT 0")
            database.execSQL("ALTER TABLE saved_tabs ADD COLUMN pdfScrollOffset REAL NOT NULL DEFAULT 0.0")
        }
    }

    val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE vaults ADD COLUMN steamAppId TEXT")
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS steam_game_cache (
                    vaultId TEXT NOT NULL PRIMARY KEY,
                    steamAppId INTEGER NOT NULL,
                    jsonSnapshot TEXT NOT NULL,
                    lastSyncedAt INTEGER NOT NULL
                )
            """.trimIndent())
        }
    }

    /**
     * Migration from version 10 to 11
     * Adds saved_maps table for multiple maps per vault and migrates existing mapPath data.
     */
    val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS saved_maps (
                    id TEXT NOT NULL PRIMARY KEY,
                    vaultId TEXT NOT NULL,
                    title TEXT NOT NULL,
                    filePath TEXT NOT NULL,
                    map_order INTEGER NOT NULL DEFAULT 0,
                    createdAt INTEGER NOT NULL,
                    lastAccessed INTEGER NOT NULL,
                    FOREIGN KEY(vaultId) REFERENCES vaults(id) ON DELETE CASCADE
                )
            """.trimIndent())
            database.execSQL("CREATE INDEX IF NOT EXISTS index_saved_maps_vaultId ON saved_maps(vaultId)")

            // Migrate existing mapPath data: create a saved_map entry for each vault that has a mapPath
            val now = System.currentTimeMillis()
            database.query("SELECT id, name, mapPath FROM vaults WHERE mapPath IS NOT NULL AND mapPath != ''").use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow("id")
                val nameIdx = cursor.getColumnIndexOrThrow("name")
                val mapPathIdx = cursor.getColumnIndexOrThrow("mapPath")
                while (cursor.moveToNext()) {
                    val vaultId = cursor.getString(idIdx)
                    val vaultName = cursor.getString(nameIdx) ?: "Map"
                    val mapPath = cursor.getString(mapPathIdx)
                    val mapId = java.util.UUID.randomUUID().toString()
                    database.execSQL(
                        "INSERT INTO saved_maps (id, vaultId, title, filePath, map_order, createdAt, lastAccessed) VALUES (?, ?, ?, ?, 0, ?, ?)",
                        arrayOf(mapId, vaultId, "$vaultName Map", mapPath, now, now)
                    )
                }
            }
        }
    }

    val MIGRATION_11_12 = object : Migration(11, 12) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS checklist_tags (
                    id TEXT NOT NULL PRIMARY KEY,
                    vaultId TEXT NOT NULL,
                    name TEXT NOT NULL,
                    color INTEGER NOT NULL,
                    sortOrder INTEGER NOT NULL,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL,
                    FOREIGN KEY(vaultId) REFERENCES vaults(id) ON DELETE CASCADE
                )
                """.trimIndent()
            )
            database.execSQL("CREATE INDEX IF NOT EXISTS index_checklist_tags_vaultId ON checklist_tags(vaultId)")

            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS checklist_items (
                    id TEXT NOT NULL PRIMARY KEY,
                    vaultId TEXT NOT NULL,
                    tagId TEXT NOT NULL,
                    title TEXT NOT NULL,
                    completed INTEGER NOT NULL DEFAULT 0,
                    sortOrder INTEGER NOT NULL,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL,
                    FOREIGN KEY(vaultId) REFERENCES vaults(id) ON DELETE CASCADE,
                    FOREIGN KEY(tagId) REFERENCES checklist_tags(id) ON DELETE NO ACTION
                )
                """.trimIndent()
            )
            database.execSQL("CREATE INDEX IF NOT EXISTS index_checklist_items_vaultId ON checklist_items(vaultId)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_checklist_items_tagId ON checklist_items(tagId)")
        }
    }

    val MIGRATION_12_13 = object : Migration(12, 13) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Schema changes
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS checklist_tabs (
                    id TEXT NOT NULL PRIMARY KEY,
                    vaultId TEXT NOT NULL,
                    name TEXT NOT NULL,
                    color INTEGER NOT NULL,
                    sortOrder INTEGER NOT NULL,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL,
                    FOREIGN KEY(vaultId) REFERENCES vaults(id) ON DELETE CASCADE
                )
                """.trimIndent()
            )
            database.execSQL("CREATE INDEX IF NOT EXISTS index_checklist_tabs_vaultId ON checklist_tabs(vaultId)")

            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS checklist_sections (
                    id TEXT NOT NULL PRIMARY KEY,
                    vaultId TEXT NOT NULL,
                    tabId TEXT NOT NULL,
                    name TEXT NOT NULL,
                    expanded INTEGER NOT NULL,
                    sortOrder INTEGER NOT NULL,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL,
                    FOREIGN KEY(vaultId) REFERENCES vaults(id) ON DELETE CASCADE,
                    FOREIGN KEY(tabId) REFERENCES checklist_tabs(id) ON DELETE CASCADE
                )
                """.trimIndent()
            )
            database.execSQL("CREATE INDEX IF NOT EXISTS index_checklist_sections_vaultId ON checklist_sections(vaultId)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_checklist_sections_tabId ON checklist_sections(tabId)")

            // Create tabs/sections first; the data migration needs them before populating
            // checklist_items.sectionId, and the final table recreation below needs them for its FK.
            val now = System.currentTimeMillis()
            val legacyNames = listOf("Red", "Blue", "Green", "Purple", "Amber", "Teal", "Pink")

            val vaultIds = mutableListOf<String>()
            database.query("SELECT DISTINCT vaultId FROM checklist_items").use { c ->
                while (c.moveToNext()) vaultIds.add(c.getString(0))
            }

            // (itemId -> sectionId) plan computed before we recreate the items table.
            val itemIdToSectionId = mutableMapOf<String, String>()

            for (vaultId in vaultIds) {
                val usedLegacyColors = mutableListOf<Int>()
                database.query(
                    "SELECT DISTINCT ct.color FROM checklist_items i JOIN checklist_tags ct ON ct.id = i.tagId WHERE i.vaultId = ? ORDER BY ct.color ASC",
                    arrayOf(vaultId)
                ).use { c ->
                    while (c.moveToNext()) usedLegacyColors.add(c.getInt(0))
                }

                val sectionIdByLegacyColor = mutableMapOf<Int, String>()
                for ((sortIdx, legacyColor) in usedLegacyColors.withIndex()) {
                    val paletteIndex = (legacyColor - 1).coerceIn(0, 11)
                    val tabName = legacyNames.getOrElse(paletteIndex) { "Group ${paletteIndex + 1}" }
                    val tabId = java.util.UUID.randomUUID().toString()
                    database.execSQL(
                        "INSERT INTO checklist_tabs (id, vaultId, name, color, sortOrder, createdAt, updatedAt) VALUES (?, ?, ?, ?, ?, ?, ?)",
                        arrayOf<Any>(tabId, vaultId, tabName, paletteIndex, sortIdx, now, now)
                    )

                    val sectionId = java.util.UUID.randomUUID().toString()
                    database.execSQL(
                        "INSERT INTO checklist_sections (id, vaultId, tabId, name, expanded, sortOrder, createdAt, updatedAt) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                        arrayOf<Any>(sectionId, vaultId, tabId, "General", 1, 0, now, now)
                    )
                    sectionIdByLegacyColor[legacyColor] = sectionId
                }

                // Build the itemId -> sectionId plan using the old schema (no sectionId column yet).
                for ((legacyColor, sectionId) in sectionIdByLegacyColor) {
                    database.query(
                        "SELECT i.id FROM checklist_items i JOIN checklist_tags ct ON ct.id = i.tagId WHERE i.vaultId = ? AND ct.color = ?",
                        arrayOf(vaultId, legacyColor)
                    ).use { c ->
                        while (c.moveToNext()) {
                            itemIdToSectionId[c.getString(0)] = sectionId
                        }
                    }
                }
            }

            // Recreate checklist_items with the proper schema — including the sectionId FK.
            // SQLite's ALTER TABLE can't add a foreign key, so the table-rebuild pattern is required.
            database.execSQL("DROP INDEX IF EXISTS index_checklist_items_vaultId")
            database.execSQL("DROP INDEX IF EXISTS index_checklist_items_tagId")

            database.execSQL(
                """
                CREATE TABLE checklist_items_new (
                    id TEXT NOT NULL PRIMARY KEY,
                    vaultId TEXT NOT NULL,
                    tagId TEXT NOT NULL,
                    sectionId TEXT NOT NULL,
                    title TEXT NOT NULL,
                    completed INTEGER NOT NULL,
                    sortOrder INTEGER NOT NULL,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL,
                    FOREIGN KEY(vaultId) REFERENCES vaults(id) ON DELETE CASCADE,
                    FOREIGN KEY(tagId) REFERENCES checklist_tags(id) ON DELETE NO ACTION,
                    FOREIGN KEY(sectionId) REFERENCES checklist_sections(id) ON DELETE CASCADE
                )
                """.trimIndent()
            )

            // Copy existing rows, resolving sectionId from the precomputed plan.
            // Orphan rows (unexpected but defensive) get an empty sectionId — they'd be inaccessible
            // until the user manually fixes them, but at least the migration doesn't crash.
            database.query("SELECT id, vaultId, tagId, title, completed, sortOrder, createdAt, updatedAt FROM checklist_items").use { c ->
                while (c.moveToNext()) {
                    val id = c.getString(0)
                    val sectionId = itemIdToSectionId[id] ?: ""
                    database.execSQL(
                        "INSERT INTO checklist_items_new (id, vaultId, tagId, sectionId, title, completed, sortOrder, createdAt, updatedAt) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        arrayOf<Any>(
                            id,
                            c.getString(1),
                            c.getString(2),
                            sectionId,
                            c.getString(3),
                            c.getInt(4),
                            c.getInt(5),
                            c.getLong(6),
                            c.getLong(7)
                        )
                    )
                }
            }

            database.execSQL("DROP TABLE checklist_items")
            database.execSQL("ALTER TABLE checklist_items_new RENAME TO checklist_items")

            database.execSQL("CREATE INDEX IF NOT EXISTS index_checklist_items_vaultId ON checklist_items(vaultId)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_checklist_items_tagId ON checklist_items(tagId)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_checklist_items_sectionId ON checklist_items(sectionId)")
        }
    }

    /**
     * Migration from version 13 to 14.
     *
     * Heals devices that landed on a broken v13 schema produced by the original
     * MIGRATION_12_13 (which used `ALTER TABLE ADD COLUMN sectionId ... DEFAULT ''`
     * and could not attach the `sectionId -> checklist_sections` foreign key).
     *
     * This migration is idempotent: if the v13 schema is already correct (fresh
     * installs of the fixed MIGRATION_12_13), the recreate step is a no-op from
     * the user's perspective — data is preserved byte-for-byte and the indices
     * are re-created at the end.
     *
     * Orphan-healing: items whose `sectionId` is empty or references a section
     * that doesn't exist get resolved via their legacy `tagId -> color` mapping,
     * auto-creating color-named tabs + `General` sections as needed (same logic
     * as MIGRATION_12_13). Existing tabs / sections are reused, not duplicated.
     */
    val MIGRATION_13_14 = object : Migration(13, 14) {
        override fun migrate(database: SupportSQLiteDatabase) {
            val now = System.currentTimeMillis()
            val legacyNames = listOf("Red", "Blue", "Green", "Purple", "Amber", "Teal", "Pink")

            // Collect vaults that have at least one orphan item (empty or dangling sectionId).
            val orphanVaults = mutableSetOf<String>()
            database.query(
                """
                SELECT DISTINCT vaultId FROM checklist_items
                WHERE sectionId = ''
                   OR sectionId NOT IN (SELECT id FROM checklist_sections)
                """.trimIndent()
            ).use { c ->
                while (c.moveToNext()) orphanVaults.add(c.getString(0))
            }

            for (vaultId in orphanVaults) {
                // Cache existing tabs by palette-index color for reuse.
                val tabIdByColor = mutableMapOf<Int, String>()
                var maxTabSortOrder = -1
                database.query(
                    "SELECT id, color, sortOrder FROM checklist_tabs WHERE vaultId = ?",
                    arrayOf(vaultId)
                ).use { c ->
                    while (c.moveToNext()) {
                        tabIdByColor[c.getInt(1)] = c.getString(0)
                        maxTabSortOrder = maxOf(maxTabSortOrder, c.getInt(2))
                    }
                }

                // Cache existing `General` sections by tab.
                val generalSectionIdByTab = mutableMapOf<String, String>()
                database.query(
                    "SELECT tabId, id FROM checklist_sections WHERE vaultId = ? AND name = 'General'",
                    arrayOf(vaultId)
                ).use { c ->
                    while (c.moveToNext()) generalSectionIdByTab[c.getString(0)] = c.getString(1)
                }

                // Determine which legacy colors this vault's orphan items need.
                val orphanColors = mutableListOf<Int>()
                database.query(
                    """
                    SELECT DISTINCT ct.color
                    FROM checklist_items i
                    JOIN checklist_tags ct ON ct.id = i.tagId
                    WHERE i.vaultId = ?
                      AND (i.sectionId = '' OR i.sectionId NOT IN (SELECT id FROM checklist_sections))
                    ORDER BY ct.color ASC
                    """.trimIndent(),
                    arrayOf(vaultId)
                ).use { c ->
                    while (c.moveToNext()) orphanColors.add(c.getInt(0))
                }

                for (legacyColor in orphanColors) {
                    val paletteIndex = (legacyColor - 1).coerceIn(0, 11)

                    val tabId = tabIdByColor[paletteIndex] ?: run {
                        val newId = java.util.UUID.randomUUID().toString()
                        val tabName = legacyNames.getOrElse(paletteIndex) { "Group ${paletteIndex + 1}" }
                        maxTabSortOrder += 1
                        database.execSQL(
                            "INSERT INTO checklist_tabs (id, vaultId, name, color, sortOrder, createdAt, updatedAt) VALUES (?, ?, ?, ?, ?, ?, ?)",
                            arrayOf<Any>(newId, vaultId, tabName, paletteIndex, maxTabSortOrder, now, now)
                        )
                        tabIdByColor[paletteIndex] = newId
                        newId
                    }

                    val sectionId = generalSectionIdByTab[tabId] ?: run {
                        val newId = java.util.UUID.randomUUID().toString()
                        database.execSQL(
                            "INSERT INTO checklist_sections (id, vaultId, tabId, name, expanded, sortOrder, createdAt, updatedAt) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                            arrayOf<Any>(newId, vaultId, tabId, "General", 1, 0, now, now)
                        )
                        generalSectionIdByTab[tabId] = newId
                        newId
                    }

                    database.execSQL(
                        """
                        UPDATE checklist_items
                        SET sectionId = ?, updatedAt = ?
                        WHERE vaultId = ?
                          AND (sectionId = '' OR sectionId NOT IN (SELECT id FROM checklist_sections))
                          AND tagId IN (SELECT id FROM checklist_tags WHERE vaultId = ? AND color = ?)
                        """.trimIndent(),
                        arrayOf<Any>(sectionId, now, vaultId, vaultId, legacyColor)
                    )
                }
            }

            // Recreate checklist_items with the correct schema (no bogus DEFAULT '',
            // proper sectionId FK). Idempotent: if the table already had the right
            // schema, the end state is the same.
            database.execSQL("DROP INDEX IF EXISTS index_checklist_items_vaultId")
            database.execSQL("DROP INDEX IF EXISTS index_checklist_items_tagId")
            database.execSQL("DROP INDEX IF EXISTS index_checklist_items_sectionId")

            database.execSQL(
                """
                CREATE TABLE checklist_items_new (
                    id TEXT NOT NULL PRIMARY KEY,
                    vaultId TEXT NOT NULL,
                    tagId TEXT NOT NULL,
                    sectionId TEXT NOT NULL,
                    title TEXT NOT NULL,
                    completed INTEGER NOT NULL,
                    sortOrder INTEGER NOT NULL,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL,
                    FOREIGN KEY(vaultId) REFERENCES vaults(id) ON DELETE CASCADE,
                    FOREIGN KEY(tagId) REFERENCES checklist_tags(id) ON DELETE NO ACTION,
                    FOREIGN KEY(sectionId) REFERENCES checklist_sections(id) ON DELETE CASCADE
                )
                """.trimIndent()
            )

            database.execSQL(
                """
                INSERT INTO checklist_items_new (id, vaultId, tagId, sectionId, title, completed, sortOrder, createdAt, updatedAt)
                SELECT id, vaultId, tagId, sectionId, title, completed, sortOrder, createdAt, updatedAt FROM checklist_items
                """.trimIndent()
            )

            database.execSQL("DROP TABLE checklist_items")
            database.execSQL("ALTER TABLE checklist_items_new RENAME TO checklist_items")

            database.execSQL("CREATE INDEX IF NOT EXISTS index_checklist_items_vaultId ON checklist_items(vaultId)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_checklist_items_tagId ON checklist_items(tagId)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_checklist_items_sectionId ON checklist_items(sectionId)")
        }
    }

    val MIGRATION_14_15 = object : Migration(14, 15) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS favorite_apps (
                    packageName TEXT NOT NULL PRIMARY KEY,
                    addedAt INTEGER NOT NULL DEFAULT 0
                )
            """.trimIndent())
        }
    }

    /**
     * Migration 15 → 16: Add emulator launch data columns to vaults table.
     * Supports game launching from vault tap in launcher mode.
     */
    val MIGRATION_15_16 = object : Migration(15, 16) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE vaults ADD COLUMN emulatorCorePath TEXT DEFAULT NULL")
            database.execSQL("ALTER TABLE vaults ADD COLUMN emulatorCoreName TEXT DEFAULT NULL")
            database.execSQL("ALTER TABLE vaults ADD COLUMN emulatorPackage TEXT DEFAULT NULL")
            database.execSQL("ALTER TABLE vaults ADD COLUMN emulatorConfigPath TEXT DEFAULT NULL")
        }
    }

    /**
     * Migration 16 → 17: Add launchIntentJson column to store complete ES-DE launch intents.
     */
    val MIGRATION_16_17 = object : Migration(16, 17) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE vaults ADD COLUMN launchIntentJson TEXT DEFAULT NULL")
        }
    }

    /**
     * Migration 17 → 18: Consolidate emulator launch fields into launchIntentJson.
     * The separate emulatorCorePath/Name/Package/ConfigPath columns are no longer
     * read by the entity; they remain in the SQLite schema for one release for
     * safety (SQLite cannot DROP COLUMN without a full table rebuild). They will
     * be removed in a future migration once all devices have migrated.
     * This is a schema no-op migration — the entity simply stops mapping those columns.
     */
    val MIGRATION_17_18 = object : Migration(17, 18) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // No DDL changes: emulatorCorePath/Name/Package/ConfigPath remain as
            // orphan columns. Room ignores unmapped columns on reads/writes.
            // This fixes pre-existing missing migration after commit 3ec02cf bumped DB to 18
            // without DDL; relied on fallbackToDestructiveMigration which would silently wipe user data.
        }
    }

    /**
     * MIGRATION_18_19: Library Folder Refactor.
     *
     * - Rebuilds the vaults table to drop orphan columns left over from before commit
     *   3ec02cf (emulatorCorePath, emulatorCoreName, emulatorPackage, emulatorConfigPath)
     *   and to add the three new resource FK columns. The rebuild dance is required
     *   because Room's strict schema validation rejects orphan columns; SQLite before
     *   3.35 also lacks ALTER TABLE DROP COLUMN, so we use the standard
     *   create-new + copy + drop + rename pattern.
     * - Adds resource_files, map_markers, page_bookmarks, scroll_markers tables.
     * - Adds resource_id FK column on saved_tabs and saved_maps (no orphan columns
     *   there, so plain ALTER TABLE ADD COLUMN is fine).
     * - Old path columns (artworkPath, mapPath, guidePath, bookmark_dir, filePath)
     *   remain on the rebuilt vaults table for back-compat with existing reads;
     *   they will be dropped in 19_20.
     * - Resource_files row backfill from existing path columns is performed by
     *   MigrationCoordinator in Kotlin code post-Room-migration.
     */
    val MIGRATION_18_19 = object : Migration(18, 19) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Rebuild vaults to match the v19 entity schema exactly (drop orphan cols,
            // add resource FK cols). Detect whether launchIntentJson is present in the
            // current table so we can preserve it when migrating from v18 (it was added
            // in 16_17 and is missing on devices that came through the buggy 17->18 path).
            val hasLaunchIntent = db.query("PRAGMA table_info(vaults)").use { c ->
                var found = false
                val nameIdx = c.getColumnIndexOrThrow("name")
                while (c.moveToNext()) {
                    if (c.getString(nameIdx) == "launchIntentJson") {
                        found = true
                        break
                    }
                }
                found
            }

            db.execSQL(
                """
                CREATE TABLE vaults_new (
                    id TEXT NOT NULL PRIMARY KEY,
                    name TEXT NOT NULL,
                    romPath TEXT,
                    romFilename TEXT,
                    artworkPath TEXT,
                    artworkVibrantArgb INTEGER,
                    mapPath TEXT,
                    guidePath TEXT,
                    systemeid TEXT,
                    gameId TEXT,
                    source TEXT,
                    raGameId TEXT,
                    steamAppId TEXT,
                    launchIntentJson TEXT,
                    lastOpened INTEGER,
                    createdAt INTEGER NOT NULL,
                    artwork_resource_id TEXT,
                    guide_resource_id TEXT,
                    map_resource_id TEXT
                )
                """.trimIndent()
            )

            val baseCols = "id, name, romPath, romFilename, artworkPath, artworkVibrantArgb, " +
                "mapPath, guidePath, systemeid, gameId, source, raGameId, steamAppId, " +
                "lastOpened, createdAt"
            val launchIntentExpr = if (hasLaunchIntent) "launchIntentJson" else "NULL"
            db.execSQL(
                """
                INSERT INTO vaults_new ($baseCols, launchIntentJson)
                SELECT $baseCols, $launchIntentExpr FROM vaults
                """.trimIndent()
            )
            db.execSQL("DROP TABLE vaults")
            db.execSQL("ALTER TABLE vaults_new RENAME TO vaults")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS resource_files (
                    id TEXT NOT NULL PRIMARY KEY,
                    vault_id TEXT,
                    kind TEXT NOT NULL,
                    filename TEXT NOT NULL,
                    size_bytes INTEGER NOT NULL,
                    home_relative_path TEXT,
                    absolute_path TEXT,
                    mime_type TEXT,
                    source TEXT NOT NULL,
                    last_seen_at INTEGER NOT NULL,
                    created_at INTEGER NOT NULL,
                    FOREIGN KEY(vault_id) REFERENCES vaults(id) ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_resource_files_vault_id ON resource_files(vault_id)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_resource_files_vault_id_kind ON resource_files(vault_id, kind)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_resource_files_filename_size_bytes ON resource_files(filename, size_bytes)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS map_markers (
                    id TEXT NOT NULL PRIMARY KEY,
                    resource_file_id TEXT NOT NULL,
                    label TEXT,
                    x_norm REAL NOT NULL,
                    y_norm REAL NOT NULL,
                    color INTEGER,
                    created_at INTEGER NOT NULL,
                    FOREIGN KEY(resource_file_id) REFERENCES resource_files(id) ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_map_markers_resource_file_id ON map_markers(resource_file_id)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS page_bookmarks (
                    id TEXT NOT NULL PRIMARY KEY,
                    saved_tab_id TEXT NOT NULL,
                    label TEXT,
                    scroll_position INTEGER NOT NULL,
                    created_at INTEGER NOT NULL,
                    FOREIGN KEY(saved_tab_id) REFERENCES saved_tabs(id) ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_page_bookmarks_saved_tab_id ON page_bookmarks(saved_tab_id)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS scroll_markers (
                    id TEXT NOT NULL PRIMARY KEY,
                    saved_tab_id TEXT NOT NULL,
                    scroll_position INTEGER NOT NULL,
                    marker_order INTEGER NOT NULL,
                    created_at INTEGER NOT NULL,
                    FOREIGN KEY(saved_tab_id) REFERENCES saved_tabs(id) ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_scroll_markers_saved_tab_id ON scroll_markers(saved_tab_id)")

            db.execSQL("ALTER TABLE saved_tabs ADD COLUMN resource_id TEXT")
            db.execSQL("ALTER TABLE saved_maps ADD COLUMN resource_id TEXT")
        }
    }

    /**
     * MIGRATION_19_20: Bookmark metadata sidecar replacement.
     *
     * Replaces the per-bookmark `bookmark_metadata.json` sidecar with Room columns:
     *  - saved_tabs gains scroll_normalized_anchor / scroll_occurrence_index /
     *    scroll_ratio (the ScrollRestoreData triple) and focused_scroll_marker_id.
     *  - scroll_markers gains label + occurrence_index to match the
     *    [com.twinlore.app.domain.ScrollMarker] domain shape.
     *  - map_markers gains zoom + offset_x + offset_y + style_id to match the
     *    [com.twinlore.app.domain.MapMarker] domain shape.
     *
     * All adds; no rebuild needed. Legacy path columns (vaults.artworkPath/mapPath/
     * guidePath, saved_maps.filePath) stay for now and are dropped in a later
     * migration once all readers and writers have moved to the resource-id columns.
     */
    val MIGRATION_19_20 = object : Migration(19, 20) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE saved_tabs ADD COLUMN scroll_normalized_anchor TEXT")
            db.execSQL("ALTER TABLE saved_tabs ADD COLUMN scroll_occurrence_index INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE saved_tabs ADD COLUMN scroll_ratio REAL NOT NULL DEFAULT 0.0")
            db.execSQL("ALTER TABLE saved_tabs ADD COLUMN focused_scroll_marker_id TEXT")

            db.execSQL("ALTER TABLE scroll_markers ADD COLUMN label TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE scroll_markers ADD COLUMN occurrence_index INTEGER NOT NULL DEFAULT 0")

            db.execSQL("ALTER TABLE map_markers ADD COLUMN zoom REAL NOT NULL DEFAULT 1.0")
            db.execSQL("ALTER TABLE map_markers ADD COLUMN offset_x REAL NOT NULL DEFAULT 0.0")
            db.execSQL("ALTER TABLE map_markers ADD COLUMN offset_y REAL NOT NULL DEFAULT 0.0")
            db.execSQL("ALTER TABLE map_markers ADD COLUMN style_id INTEGER NOT NULL DEFAULT 1")
        }
    }

    /**
     * MIGRATION_20_21: drop legacy file-path columns now that the resource_files
     * indirection has fully replaced them.
     *
     *  - vaults: drop artworkPath / mapPath / guidePath. Room rejects orphan
     *    columns, so the only legal way to drop a column is the table-rebuild
     *    dance (CREATE _new + INSERT SELECT + DROP + RENAME). See MIGRATION_18_19
     *    for the same pattern.
     *  - saved_maps: drop filePath. resource_id is preserved as-is; saved_maps
     *    rows with no resource_id (legacy entries that never got migrated to a
     *    resource_files row) will have an empty filePath value lost — accepted
     *    for these pre-release legacy rows.
     */
    val MIGRATION_20_21 = object : Migration(20, 21) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // ── Rebuild vaults without artworkPath / mapPath / guidePath ──
            db.execSQL(
                """
                CREATE TABLE vaults_new (
                    id TEXT NOT NULL PRIMARY KEY,
                    name TEXT NOT NULL,
                    romPath TEXT,
                    romFilename TEXT,
                    artworkVibrantArgb INTEGER,
                    systemeid TEXT,
                    gameId TEXT,
                    source TEXT,
                    raGameId TEXT,
                    steamAppId TEXT,
                    launchIntentJson TEXT,
                    lastOpened INTEGER,
                    createdAt INTEGER NOT NULL,
                    artwork_resource_id TEXT,
                    guide_resource_id TEXT,
                    map_resource_id TEXT
                )
                """.trimIndent()
            )

            db.execSQL(
                """
                INSERT INTO vaults_new (
                    id, name, romPath, romFilename, artworkVibrantArgb,
                    systemeid, gameId, source, raGameId, steamAppId,
                    launchIntentJson, lastOpened, createdAt,
                    artwork_resource_id, guide_resource_id, map_resource_id
                )
                SELECT
                    id, name, romPath, romFilename, artworkVibrantArgb,
                    systemeid, gameId, source, raGameId, steamAppId,
                    launchIntentJson, lastOpened, createdAt,
                    artwork_resource_id, guide_resource_id, map_resource_id
                FROM vaults
                """.trimIndent()
            )
            db.execSQL("DROP TABLE vaults")
            db.execSQL("ALTER TABLE vaults_new RENAME TO vaults")

            // ── Rebuild saved_maps without filePath ──
            db.execSQL("DROP INDEX IF EXISTS index_saved_maps_vaultId")
            db.execSQL(
                """
                CREATE TABLE saved_maps_new (
                    id TEXT NOT NULL PRIMARY KEY,
                    vaultId TEXT NOT NULL,
                    title TEXT NOT NULL,
                    resource_id TEXT,
                    map_order INTEGER NOT NULL DEFAULT 0,
                    createdAt INTEGER NOT NULL,
                    lastAccessed INTEGER NOT NULL,
                    FOREIGN KEY(vaultId) REFERENCES vaults(id) ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO saved_maps_new (
                    id, vaultId, title, resource_id, map_order, createdAt, lastAccessed
                )
                SELECT
                    id, vaultId, title, resource_id, map_order, createdAt, lastAccessed
                FROM saved_maps
                """.trimIndent()
            )
            db.execSQL("DROP TABLE saved_maps")
            db.execSQL("ALTER TABLE saved_maps_new RENAME TO saved_maps")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_saved_maps_vaultId ON saved_maps(vaultId)")
        }
    }

    /**
     * MIGRATION_21_22: Local Media Bind feature.
     * Adds three additive tables for the media-scan index.
     * No existing table is touched. No backfill — the indexer populates
     * media_search_index lazily when search paths are configured.
     */
    val MIGRATION_21_22 = object : Migration(21, 22) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS media_search_paths (
                    id TEXT NOT NULL PRIMARY KEY,
                    uri TEXT NOT NULL,
                    label TEXT NOT NULL,
                    origin TEXT NOT NULL,
                    last_scanned_at INTEGER,
                    created_at INTEGER NOT NULL
                )
            """.trimIndent())

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS media_search_index (
                    id TEXT NOT NULL PRIMARY KEY,
                    search_path_id TEXT NOT NULL,
                    absolute_path TEXT,
                    relative_path TEXT,
                    filename TEXT NOT NULL,
                    normalized_basename TEXT NOT NULL,
                    normalized_path_tokens TEXT NOT NULL,
                    ext_lower TEXT NOT NULL,
                    size_bytes INTEGER NOT NULL,
                    mtime INTEGER NOT NULL,
                    FOREIGN KEY(search_path_id) REFERENCES media_search_paths(id) ON DELETE CASCADE
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS index_media_search_index_search_path_id ON media_search_index(search_path_id)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_media_search_index_search_path_id_normalized_basename ON media_search_index(search_path_id, normalized_basename)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_media_search_index_search_path_id_normalized_path_tokens ON media_search_index(search_path_id, normalized_path_tokens)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_media_search_index_filename_size_bytes ON media_search_index(filename, size_bytes)")

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS media_search_dirs (
                    id TEXT NOT NULL PRIMARY KEY,
                    search_path_id TEXT NOT NULL,
                    relative_dir TEXT NOT NULL,
                    mtime INTEGER NOT NULL,
                    FOREIGN KEY(search_path_id) REFERENCES media_search_paths(id) ON DELETE CASCADE
                )
            """.trimIndent())
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_media_search_dirs_search_path_id_relative_dir ON media_search_dirs(search_path_id, relative_dir)")
        }
    }

    /**
     * MIGRATION_22_23: Local Media Bind refinements.
     * - Adds `disabled` column to media_search_paths (soft-disable for AUTO entries).
     * - Adds `normalized_name` column to media_search_dirs and an index for
     *   the new strict-equality folder match.
     * - Clears media_search_dirs so the next scanDelta re-walks every dir and
     *   populates normalized_name. media_search_index rows survive; they are
     *   reconciled file-by-file as scanDelta treats every dir as new.
     */
    val MIGRATION_22_23 = object : Migration(22, 23) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE media_search_paths ADD COLUMN disabled INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE media_search_dirs ADD COLUMN normalized_name TEXT NOT NULL DEFAULT ''")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_media_search_dirs_search_path_id_normalized_name ON media_search_dirs(search_path_id, normalized_name)")
            db.execSQL("DELETE FROM media_search_dirs")
        }
    }

    /**
     * MIGRATION_24_25: Multi-tab browser feature.
     * Adds the browser_tabs table; per-vault open-tab persistence
     * (URL + title + favicon + scrollY). No backfill: each vault starts
     * with zero rows and gets a default landing tab on first open.
     */
    val MIGRATION_24_25 = object : Migration(24, 25) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS browser_tabs (
                    id TEXT NOT NULL PRIMARY KEY,
                    vaultId TEXT NOT NULL,
                    url TEXT NOT NULL,
                    title TEXT NOT NULL,
                    faviconBytes BLOB,
                    scrollY INTEGER NOT NULL DEFAULT 0,
                    tab_order INTEGER NOT NULL DEFAULT 0,
                    lastAccessed INTEGER NOT NULL,
                    createdAt INTEGER NOT NULL,
                    FOREIGN KEY(vaultId) REFERENCES vaults(id) ON DELETE CASCADE
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS index_browser_tabs_vaultId ON browser_tabs(vaultId)")
        }
    }

    /**
     * MIGRATION_25_26: Add favicon_bytes column to saved_tabs.
     * Populated from webView.favicon at link save time. Existing rows
     * get null and fall back to a globe icon on the landing page.
     */
    val MIGRATION_25_26 = object : Migration(25, 26) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE saved_tabs ADD COLUMN favicon_bytes BLOB")
        }
    }

    /**
     * MIGRATION_26_27: Achievement progress sync — relax ra_game_cache.jsonSnapshot
     * to nullable + add count columns to both achievement caches. The library bars
     * and drawer headline numbers read counts directly without JSON parsing.
     * Bulk RA sync writes count-only rows (jsonSnapshot=null) for vaults whose
     * drawer has never been opened.
     *
     * RA half requires a table rebuild because ALTER TABLE cannot relax NOT NULL.
     * Steam half is purely additive.
     */
    val MIGRATION_26_27 = object : Migration(26, 27) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // RA: rebuild ra_game_cache to make jsonSnapshot nullable + add count columns.
            db.execSQL(
                """
                CREATE TABLE ra_game_cache_new (
                    vaultId TEXT NOT NULL PRIMARY KEY,
                    raGameId INTEGER NOT NULL,
                    jsonSnapshot TEXT,
                    lastSyncedAt INTEGER NOT NULL,
                    highestAwardKind TEXT,
                    userRank INTEGER,
                    numAwardedSoft INTEGER NOT NULL DEFAULT 0,
                    numAwardedHardcore INTEGER NOT NULL DEFAULT 0,
                    numAchievements INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO ra_game_cache_new
                    (vaultId, raGameId, jsonSnapshot, lastSyncedAt,
                     highestAwardKind, userRank,
                     numAwardedSoft, numAwardedHardcore, numAchievements)
                SELECT vaultId, raGameId, jsonSnapshot, lastSyncedAt,
                       highestAwardKind, userRank,
                       0, 0, 0
                  FROM ra_game_cache
                """.trimIndent()
            )
            db.execSQL("DROP TABLE ra_game_cache")
            db.execSQL("ALTER TABLE ra_game_cache_new RENAME TO ra_game_cache")

            // Steam: purely additive (no NOT NULL relaxation needed).
            db.execSQL("ALTER TABLE steam_game_cache ADD COLUMN numUnlocked INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE steam_game_cache ADD COLUMN numAchievements INTEGER NOT NULL DEFAULT 0")
        }
    }

    /**
     * MIGRATION_27_28: heal browser_tabs.scrollY / tab_order DEFAULT.
     *
     * MIGRATION_24_25 originally creates `browser_tabs` with `scrollY` and
     * `tab_order` declared `INTEGER NOT NULL DEFAULT 0`. For devices that
     * already had the table from a destructive recreation (Room's
     * `createAllTables` path) before the entity carried matching
     * `@ColumnInfo(defaultValue = "0")` annotations, the table on disk lacks
     * those DEFAULTs, and `CREATE TABLE IF NOT EXISTS` in MIGRATION_24_25
     * was a no-op for them.
     *
     * Room's post-migration schema validation now compares actual vs expected
     * column defaultValue and throws "Migration didn't properly handle:
     * browser_tabs" on the affected devices. SQLite can't ALTER a column's
     * DEFAULT clause in place, so this migration does the table-rebuild
     * dance to bring browser_tabs to the canonical schema. Rows are copied.
     */
    val MIGRATION_27_28 = object : Migration(27, 28) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE browser_tabs_new (
                    id TEXT NOT NULL PRIMARY KEY,
                    vaultId TEXT NOT NULL,
                    url TEXT NOT NULL,
                    title TEXT NOT NULL,
                    faviconBytes BLOB,
                    scrollY INTEGER NOT NULL DEFAULT 0,
                    tab_order INTEGER NOT NULL DEFAULT 0,
                    lastAccessed INTEGER NOT NULL,
                    createdAt INTEGER NOT NULL,
                    FOREIGN KEY(vaultId) REFERENCES vaults(id) ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO browser_tabs_new
                    (id, vaultId, url, title, faviconBytes, scrollY, tab_order, lastAccessed, createdAt)
                SELECT id, vaultId, url, title, faviconBytes, scrollY, tab_order, lastAccessed, createdAt
                  FROM browser_tabs
                """.trimIndent()
            )
            db.execSQL("DROP TABLE browser_tabs")
            db.execSQL("ALTER TABLE browser_tabs_new RENAME TO browser_tabs")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_browser_tabs_vaultId ON browser_tabs(vaultId)")
        }
    }

    /**
     * MIGRATION_28_29: add `folder_name` to `vaults`.
     *
     * Stable on-disk folder identity captured at vault create time, used by
     * save + delete paths so they don't drift when the user renames a vault
     * (which is metadata-only — the folder stays put). Nullable: existing
     * rows stay null and fall back to `sanitizeForPath(name)` at the call
     * site, preserving pre-fix behavior for vaults that have never been
     * renamed. Rows that HAVE been renamed before this migration keep the
     * pre-fix behavior too (we have no way to recover the original name), so
     * those vaults' orphans remain — fix is preventive going forward.
     *
     * ALTER TABLE ADD COLUMN is safe for a nullable column with no default,
     * no table-rebuild dance needed.
     */
    val MIGRATION_28_29 = object : Migration(28, 29) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE vaults ADD COLUMN folder_name TEXT")
        }
    }

    /**
     * MIGRATION_29_30: add `history_json` + `history_index` to `browser_tabs`.
     *
     * Per-tab back/forward history.
     * Existing rows are backfilled with a single-entry history pointing at
     * the row's current `(url, title)` and `history_index = 0` so the
     * "history is always non-empty" domain invariant holds from row 1.
     *
     * The backfill uses SQLite's JSON1 extension (`json_array` /
     * `json_object`). JSON1 is NOT universally available on Android: AYN's
     * Android 13 image on the Thor handheld ships a SQLite without it, and
     * presumably other OEM builds do too. When the UPDATE throws, we
     * swallow it: `history_json` stays at the column default `'[]'`, and
     * [BrowserTabHistorySerializer.fromJson] returns a one-entry list
     * synthesized from the caller-supplied `(url, title)` on first read,
     * preserving the invariant without the SQL-side backfill. Anything
     * else in the migrate { } block must run synchronously before this
     * try-block so a JSON1 failure can't roll the rest back.
     */
    val MIGRATION_29_30 = object : Migration(29, 30) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE browser_tabs ADD COLUMN history_json TEXT NOT NULL DEFAULT '[]'")
            db.execSQL("ALTER TABLE browser_tabs ADD COLUMN history_index INTEGER NOT NULL DEFAULT 0")
            try {
                db.execSQL(
                    """
                    UPDATE browser_tabs
                    SET history_json = json_array(json_object('url', url, 'title', title))
                    """.trimIndent()
                )
            } catch (e: android.database.sqlite.SQLiteException) {
                android.util.Log.w(
                    "VaultDatabaseMigration",
                    "MIGRATION_29_30 JSON1 backfill skipped (likely missing json_array/json_object on this device); BrowserTabHistorySerializer will synthesize from (url, title) on first read",
                    e,
                )
            }
        }
    }

    /**
     * MIGRATION_30_31:
     *  - Add `group_active_entry_id` to `saved_tabs` (nullable; non-group rows leave null).
     *  - Create `page_group_entries` table + the two indices Room expects.
     *
     * Backed by the page-group bulk save redesign.
     */
    val MIGRATION_30_31 = object : Migration(30, 31) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE saved_tabs ADD COLUMN group_active_entry_id TEXT")
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS page_group_entries (
                    id TEXT NOT NULL PRIMARY KEY,
                    group_tab_id TEXT NOT NULL,
                    position INTEGER NOT NULL,
                    label TEXT NOT NULL,
                    saved_href TEXT NOT NULL,
                    fragment TEXT,
                    mht_resource_id TEXT,
                    scroll_anchor TEXT,
                    scroll_occurrence_index INTEGER NOT NULL DEFAULT 0,
                    scroll_ratio REAL NOT NULL DEFAULT 0.0,
                    FOREIGN KEY(group_tab_id) REFERENCES saved_tabs(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(mht_resource_id) REFERENCES resource_files(id) ON UPDATE NO ACTION ON DELETE SET NULL
                )
                """.trimIndent()
            )
            database.execSQL("CREATE INDEX IF NOT EXISTS index_page_group_entries_group_tab_id ON page_group_entries(group_tab_id)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_page_group_entries_mht_resource_id ON page_group_entries(mht_resource_id)")
        }
    }

    /**
     * MIGRATION_31_32: add `group_folder_name` to `saved_tabs`.
     *
     * Captured at save time as the sanitized group title so the on-disk
     * folder reads like `<vault>/<group title>/page-N.mht` instead of
     * `<vault>/groups/<uuid>/page-N.mht`. Null for non-group rows and for
     * legacy v0 groups created before this migration — the resolver falls
     * back to the old `groups/<groupTabId>/` layout in that case so existing
     * page-groups continue to load until the user deletes and re-saves.
     */
    val MIGRATION_31_32 = object : Migration(31, 32) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE saved_tabs ADD COLUMN group_folder_name TEXT")
        }
    }

    /**
     * MIGRATION_32_33: introduce per-entry anchors + per-group scroll markers.
     *
     *  - Add `page_group_entry_id` to `scroll_markers` (nullable). Single-page
     *    saved-tab markers leave it null; group markers set it to the entry
     *    that was active when the user hit Save Marker. No FK — single-page
     *    markers need it null and Room can't express SET_NULL cleanly with
     *    the existing CASCADE FK on `saved_tab_id`. Cleanup on entry removal
     *    is handled explicitly inside PageGroupRepository.removeEntry.
     *
     *  - Create `page_group_entry_anchors` table — one row per header element
     *    captured at bulk-save time. Cascades from page_group_entries.
     */
    val MIGRATION_32_33 = object : Migration(32, 33) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE scroll_markers ADD COLUMN page_group_entry_id TEXT")
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS page_group_entry_anchors (
                    id TEXT NOT NULL PRIMARY KEY,
                    page_group_entry_id TEXT NOT NULL,
                    position INTEGER NOT NULL,
                    label TEXT NOT NULL,
                    scroll_position INTEGER NOT NULL,
                    FOREIGN KEY(page_group_entry_id) REFERENCES page_group_entries(id) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS index_page_group_entry_anchors_page_group_entry_id ON page_group_entry_anchors(page_group_entry_id)"
            )
        }
    }

    /**
     * MIGRATION_33_34: add `synthetic` column to `page_group_entries`.
     *
     * Synthetic entries are body-only-discovered save targets (see ToCDetector
     * pass 3). Existing rows are all named-chapter entries created before
     * pass 3 existed, so the DEFAULT 0 backfill is correct: every legacy row
     * becomes `synthetic = false` automatically.
     */
    val MIGRATION_33_34 = object : Migration(33, 34) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE page_group_entries ADD COLUMN synthetic INTEGER NOT NULL DEFAULT 0")
        }
    }

    /**
     * MIGRATION_34_35: add vault_sync_state and sync_tombstones tables for
     * cross-device per-vault merge sync.
     *
     * vault_sync_state has a FK CASCADE on vaultId so orphan rows are cleaned
     * up automatically when a vault is deleted. sync_tombstones intentionally
     * has NO foreign key — tombstones must outlive the vault row.
     */
    val MIGRATION_34_35 = object : Migration(34, 35) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS `vault_sync_state` (" +
                    "`vaultId` TEXT NOT NULL, " +
                    "`fingerprint` TEXT NOT NULL, " +
                    "`lastChangedAt` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`vaultId`), " +
                    "FOREIGN KEY(`vaultId`) REFERENCES `vaults`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
            )
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS sync_tombstones (" +
                    "vaultId TEXT NOT NULL PRIMARY KEY, " +
                    "deletedAt INTEGER NOT NULL, " +
                    "deviceId TEXT NOT NULL)"
            )
        }
    }

    /**
     * MIGRATION_35_36: add play_sessions for the ES-DE play-stats screen.
     *
     * Keyed by ES-DE rom identity (system + romPath), NOT a vault FK — sessions
     * must outlive vault deletion and cover games that never got a vault.
     */
    val MIGRATION_35_36 = object : Migration(35, 36) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS `play_sessions` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`system` TEXT NOT NULL, " +
                    "`romPath` TEXT NOT NULL, " +
                    "`gameName` TEXT NOT NULL, " +
                    "`startedAtEpochSeconds` INTEGER NOT NULL, " +
                    "`endedAtEpochSeconds` INTEGER NOT NULL, " +
                    "`durationSeconds` INTEGER NOT NULL)"
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_play_sessions_startedAtEpochSeconds` " +
                    "ON `play_sessions` (`startedAtEpochSeconds`)"
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_play_sessions_system_romPath` " +
                    "ON `play_sessions` (`system`, `romPath`)"
            )
        }
    }

    /**
     * MIGRATION_36_37: deliberately empty. Version 37 was claimed by the frozen
     * feat/achievement-journey build (journey_game_cache + journey_completion_cache),
     * which ran on real devices — their DBs sit at v37 with that schema, so main must
     * never reuse 37 for anything else. v36 devices pass through here untouched; the
     * stray journey tables on v37 devices are unknown to Room and ignored. A journey
     * revival must renumber its migration to the then-current version.
     */
    val MIGRATION_36_37 = object : Migration(36, 37) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // No-op: see kdoc.
        }
    }

    /**
     * MIGRATION_37_38: favorite_apps gains `position` so the ES-DE app strip and the
     * launcher view share an explicit, user-editable pin order. Seeded from the addedAt
     * rank (the previous implicit order), rowid breaking ties — batch pins can land on
     * the same millisecond. DEFAULT 0 mirrors the entity's @ColumnInfo default; Room's
     * post-migration identity check rejects the table without it.
     */
    val MIGRATION_37_38 = object : Migration(37, 38) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                "ALTER TABLE favorite_apps ADD COLUMN position INTEGER NOT NULL DEFAULT 0"
            )
            database.execSQL(
                """
                UPDATE favorite_apps SET position = (
                    SELECT COUNT(*) FROM favorite_apps AS o
                    WHERE o.addedAt < favorite_apps.addedAt
                       OR (o.addedAt = favorite_apps.addedAt AND o.rowid < favorite_apps.rowid)
                )
                """.trimIndent()
            )
        }
    }

    /**
     * MIGRATION_38_39: add journey_game_cache and journey_completion_cache tables
     * for the Achievement Journey on-disk cache (per-game snapshots + per-user
     * completion list). Purely additive; no backfill. IF NOT EXISTS is load-bearing:
     * devices that ran the frozen journey build already hold these exact tables from
     * their burned-v37 era (see MIGRATION_36_37 kdoc) and must pass through untouched.
     */
    val MIGRATION_38_39 = object : Migration(38, 39) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS `journey_game_cache` (" +
                    "`userName` TEXT NOT NULL, `gameId` INTEGER NOT NULL, " +
                    "`fetchedAt` INTEGER NOT NULL, `validatorJson` TEXT NOT NULL, " +
                    "`payloadJson` TEXT NOT NULL, PRIMARY KEY(`userName`, `gameId`))"
            )
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS `journey_completion_cache` (" +
                    "`userName` TEXT NOT NULL, `fetchedAt` INTEGER NOT NULL, " +
                    "`payloadJson` TEXT NOT NULL, PRIMARY KEY(`userName`))"
            )
        }
    }

    /** Every migration in order. Single source of truth for DatabaseModule and LibrarySyncEngine. */
    val ALL: Array<Migration> = arrayOf(
        MIGRATION_1_2,
        MIGRATION_2_3,
        MIGRATION_3_4,
        MIGRATION_4_5,
        MIGRATION_5_6,
        MIGRATION_6_7,
        MIGRATION_7_8,
        MIGRATION_8_9,
        MIGRATION_9_10,
        MIGRATION_10_11,
        MIGRATION_11_12,
        MIGRATION_12_13,
        MIGRATION_13_14,
        MIGRATION_14_15,
        MIGRATION_15_16,
        MIGRATION_16_17,
        MIGRATION_17_18,
        MIGRATION_18_19,
        MIGRATION_19_20,
        MIGRATION_20_21,
        MIGRATION_21_22,
        MIGRATION_22_23,
        VaultDatabase.MIGRATION_23_24,
        MIGRATION_24_25,
        MIGRATION_25_26,
        MIGRATION_26_27,
        MIGRATION_27_28,
        MIGRATION_28_29,
        MIGRATION_29_30,
        MIGRATION_30_31,
        MIGRATION_31_32,
        MIGRATION_32_33,
        MIGRATION_33_34,
        MIGRATION_34_35,
        MIGRATION_35_36,
        MIGRATION_36_37,
        MIGRATION_37_38,
        MIGRATION_38_39,
    )
}
