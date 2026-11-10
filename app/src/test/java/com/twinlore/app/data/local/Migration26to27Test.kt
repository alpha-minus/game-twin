package com.twinlore.app.data.local

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * MIGRATION_26_27 test.
 *
 * Note: VaultDatabase has exportSchema = false, so the canonical
 * MigrationTestHelper.createDatabase(name, version) approach is not available.
 * Instead, we materialize the v26 baseline of the two affected tables via raw
 * SQL, invoke MIGRATION_26_27 directly, and verify the schema + data outcomes.
 *
 * The RA half of this migration rebuilds ra_game_cache to relax NOT NULL on
 * jsonSnapshot (ALTER TABLE cannot drop a NOT NULL constraint in SQLite), so
 * we explicitly assert (a) post-migration column nullability and (b) a NULL
 * jsonSnapshot insert succeeds.
 */
@RunWith(RobolectricTestRunner::class)
class Migration26to27Test {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val dbFile: File = File(context.cacheDir, "migration-test-26-to-27.db")
    private var helper: SupportSQLiteOpenHelper? = null

    @After
    fun tearDown() {
        helper?.close()
        if (dbFile.exists()) dbFile.delete()
    }

    private fun openV26Db(): SupportSQLiteDatabase {
        if (dbFile.exists()) dbFile.delete()
        val factory = FrameworkSQLiteOpenHelperFactory()
        val callback = object : SupportSQLiteOpenHelper.Callback(/* version = */ 1) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                seedV26Schema(db)
            }
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                // not used for this test
            }
        }
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbFile.absolutePath)
            .callback(callback)
            .build()
        val openHelper = factory.create(configuration)
        helper = openHelper
        return openHelper.writableDatabase
    }

    @Test
    fun migrate26To27_addsCountColumns_andPreservesExistingRows() {
        val db = openV26Db()
        db.execSQL(
            """
            INSERT INTO ra_game_cache (vaultId, raGameId, jsonSnapshot, lastSyncedAt, highestAwardKind, userRank)
            VALUES ('v1', 12345, '{"x":1}', 1000, 'mastered', 42)
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO steam_game_cache (vaultId, steamAppId, jsonSnapshot, lastSyncedAt)
            VALUES ('v1', 678, '{"y":1}', 2000)
            """.trimIndent()
        )

        VaultDatabaseMigration.MIGRATION_26_27.migrate(db)

        // RA: existing data preserved, new count columns default to 0.
        db.query(
            """SELECT raGameId, jsonSnapshot, lastSyncedAt, highestAwardKind, userRank,
                      numAwardedSoft, numAwardedHardcore, numAchievements
                 FROM ra_game_cache WHERE vaultId = 'v1'"""
        ).use { c ->
            assertEquals(1, c.count); c.moveToFirst()
            assertEquals(12345, c.getInt(0))
            assertEquals("{\"x\":1}", c.getString(1))
            assertEquals(1000L, c.getLong(2))
            assertEquals("mastered", c.getString(3))
            assertEquals(42, c.getInt(4))
            assertEquals(0, c.getInt(5))
            assertEquals(0, c.getInt(6))
            assertEquals(0, c.getInt(7))
        }

        // Steam: existing data preserved, new count columns default to 0.
        db.query(
            """SELECT steamAppId, jsonSnapshot, lastSyncedAt, numUnlocked, numAchievements
                 FROM steam_game_cache WHERE vaultId = 'v1'"""
        ).use { c ->
            assertEquals(1, c.count); c.moveToFirst()
            assertEquals(678, c.getInt(0))
            assertEquals("{\"y\":1}", c.getString(1))
            assertEquals(2000L, c.getLong(2))
            assertEquals(0, c.getInt(3))
            assertEquals(0, c.getInt(4))
        }

        // ra_game_cache.jsonSnapshot must now be nullable post-rebuild.
        db.query("PRAGMA table_info(ra_game_cache)").use { c ->
            var foundJsonSnapshot = false
            while (c.moveToNext()) {
                val name = c.getString(c.getColumnIndexOrThrow("name"))
                val notNull = c.getInt(c.getColumnIndexOrThrow("notnull"))
                if (name == "jsonSnapshot") {
                    foundJsonSnapshot = true
                    assertEquals(
                        "ra_game_cache.jsonSnapshot should be nullable post-migration",
                        0, notNull
                    )
                }
            }
            assertTrue("jsonSnapshot column present", foundJsonSnapshot)
        }
    }

    @Test
    fun migrate26To27_allowsNullJsonSnapshotInRaGameCache() {
        val db = openV26Db()
        VaultDatabaseMigration.MIGRATION_26_27.migrate(db)

        // Insert with NULL jsonSnapshot must succeed (this is what the table rebuild relaxed).
        db.execSQL(
            """
            INSERT INTO ra_game_cache
                (vaultId, raGameId, jsonSnapshot, lastSyncedAt,
                 numAwardedSoft, numAwardedHardcore, numAchievements)
            VALUES ('v_bulk_only', 42, NULL, 0, 5, 2, 10)
            """.trimIndent()
        )

        db.query(
            "SELECT jsonSnapshot, numAwardedSoft, numAwardedHardcore, numAchievements FROM ra_game_cache WHERE vaultId = 'v_bulk_only'"
        ).use { c ->
            assertEquals(1, c.count); c.moveToFirst()
            assertNull(c.getString(0))
            assertEquals(5, c.getInt(1))
            assertEquals(2, c.getInt(2))
            assertEquals(10, c.getInt(3))
        }
    }

    /**
     * Minimal v26 schema for the two tables this migration touches. Other tables
     * exist in the real v26 schema but the migration doesn't reference them, so
     * we don't need to seed them here.
     *
     * ra_game_cache schema mirrors what would exist after MIGRATION_4_5 +
     * MIGRATION_5_6 (adds highestAwardKind) + MIGRATION_6_7 (adds userRank).
     * In particular, jsonSnapshot is NOT NULL — relaxing this is the whole point
     * of MIGRATION_26_27's RA half.
     */
    private fun seedV26Schema(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS ra_game_cache (
                vaultId TEXT NOT NULL PRIMARY KEY,
                raGameId INTEGER NOT NULL,
                jsonSnapshot TEXT NOT NULL,
                lastSyncedAt INTEGER NOT NULL,
                highestAwardKind TEXT,
                userRank INTEGER
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS steam_game_cache (
                vaultId TEXT NOT NULL PRIMARY KEY,
                steamAppId INTEGER NOT NULL,
                jsonSnapshot TEXT NOT NULL,
                lastSyncedAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }
}
