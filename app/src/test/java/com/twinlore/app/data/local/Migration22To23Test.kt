package com.twinlore.app.data.local

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * MIGRATION_22_23 test.
 *
 * Note: VaultDatabase has exportSchema = false, so the canonical
 * MigrationTestHelper.createDatabase(name, version) approach is not available.
 * Instead, we materialize the v22 baseline by issuing the same SQL the v21-to-22
 * migration runs, then invoke MIGRATION_22_23 directly on the v22 db and verify
 * the schema/data outcomes.
 */
@RunWith(RobolectricTestRunner::class)
class Migration22To23Test {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val dbFile: File = File(context.cacheDir, "migration-test-22-to-23.db")
    private var helper: SupportSQLiteOpenHelper? = null

    @After
    fun tearDown() {
        helper?.close()
        if (dbFile.exists()) dbFile.delete()
    }

    @Test
    fun migrate22To23_addsDisabledColumn_andNormalizedNameColumn() {
        if (dbFile.exists()) dbFile.delete()

        val factory = FrameworkSQLiteOpenHelperFactory()
        val callback = object : SupportSQLiteOpenHelper.Callback(/* version = */ 1) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                seedV22Schema(db)
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

        // Materialize the v22 db and seed one row in each affected table.
        openHelper.writableDatabase.apply {
            execSQL("""INSERT INTO media_search_paths
                (id, uri, label, origin, last_scanned_at, created_at)
                VALUES ('p1', 'file:///x', 'L', 'MANUAL', null, 0)""")
            execSQL("""INSERT INTO media_search_dirs
                (id, search_path_id, relative_dir, mtime)
                VALUES ('d1', 'p1', 'sub', 0)""")
        }

        // Run the migration.
        VaultDatabaseMigration.MIGRATION_22_23.migrate(openHelper.writableDatabase)

        val db = openHelper.writableDatabase

        // disabled column exists on media_search_paths and defaults to 0 (false).
        db.query("SELECT disabled FROM media_search_paths WHERE id = 'p1'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(0, c.getInt(0))
        }
        // media_search_dirs has been cleared and the new normalized_name column exists.
        db.query("SELECT COUNT(*) FROM media_search_dirs").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(0, c.getInt(0))
        }
        db.query("PRAGMA table_info(media_search_dirs)").use { c ->
            val cols = mutableListOf<String>()
            while (c.moveToNext()) cols += c.getString(c.getColumnIndexOrThrow("name"))
            assertTrue("normalized_name column present", "normalized_name" in cols)
        }

        // The new index on (search_path_id, normalized_name) should exist.
        db.query("PRAGMA index_list('media_search_dirs')").use { c ->
            val indexNames = mutableListOf<String>()
            while (c.moveToNext()) indexNames += c.getString(c.getColumnIndexOrThrow("name"))
            assertTrue(
                "normalized_name index present",
                indexNames.any { it == "index_media_search_dirs_search_path_id_normalized_name" }
            )
        }
    }

    /**
     * Mirrors the v21-to-22 migration's CREATE TABLE / CREATE INDEX statements
     * for the three media_search_* tables. Only those three tables are needed
     * because MIGRATION_22_23 only touches them.
     */
    private fun seedV22Schema(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS media_search_paths (
                id TEXT NOT NULL PRIMARY KEY,
                uri TEXT NOT NULL,
                label TEXT NOT NULL,
                origin TEXT NOT NULL,
                last_scanned_at INTEGER,
                created_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
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
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS media_search_dirs (
                id TEXT NOT NULL PRIMARY KEY,
                search_path_id TEXT NOT NULL,
                relative_dir TEXT NOT NULL,
                mtime INTEGER NOT NULL,
                FOREIGN KEY(search_path_id) REFERENCES media_search_paths(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_media_search_dirs_search_path_id_relative_dir " +
                "ON media_search_dirs(search_path_id, relative_dir)"
        )
    }
}
