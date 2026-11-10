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
 * MIGRATION_23_24 test.
 *
 * Note: VaultDatabase has exportSchema = false, so the canonical
 * MigrationTestHelper.createDatabase(name, version) approach is not available.
 * Instead, we materialize a minimal v23 schema via raw SQL, invoke
 * MIGRATION_23_24 directly on the database, and verify the library_meta
 * table is created with the expected columns.
 */
@RunWith(RobolectricTestRunner::class)
class Migration23to24Test {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val dbFile: File = File(context.cacheDir, "migration-test-23-to-24.db")
    private var helper: SupportSQLiteOpenHelper? = null

    @After
    fun tearDown() {
        helper?.close()
        if (dbFile.exists()) dbFile.delete()
    }

    @Test
    fun migrate23To24_createsLibraryMetaTable() {
        if (dbFile.exists()) dbFile.delete()

        val factory = FrameworkSQLiteOpenHelperFactory()
        val callback = object : SupportSQLiteOpenHelper.Callback(/* version = */ 1) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                seedV23Schema(db)
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

        // Materialize the v23 db (triggers onCreate -> seedV23Schema).
        openHelper.writableDatabase

        // Run the migration.
        VaultDatabase.MIGRATION_23_24.migrate(openHelper.writableDatabase)

        val db = openHelper.writableDatabase

        // library_meta table exists and is empty.
        db.query("SELECT count(*) FROM library_meta").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(0, c.getInt(0))
        }

        // All five expected columns are present.
        db.query("PRAGMA table_info(library_meta)").use { c ->
            val cols = mutableListOf<String>()
            while (c.moveToNext()) cols += c.getString(c.getColumnIndexOrThrow("name"))
            assertTrue("id column present", "id" in cols)
            assertTrue("schemaVersion column present", "schemaVersion" in cols)
            assertTrue("lastWrittenAtMillis column present", "lastWrittenAtMillis" in cols)
            assertTrue("lastWrittenDeviceId column present", "lastWrittenDeviceId" in cols)
            assertTrue("lastWrittenDeviceLabel column present", "lastWrittenDeviceLabel" in cols)
        }
    }

    /**
     * Minimal v23 schema seed — only the tables that exist at v23 and that the
     * migration does not touch are needed here. We create one representative
     * table so the database is non-empty and the open-helper does not treat it
     * as a fresh install.
     */
    private fun seedV23Schema(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS media_search_paths (
                id TEXT NOT NULL PRIMARY KEY,
                uri TEXT NOT NULL,
                label TEXT NOT NULL,
                origin TEXT NOT NULL,
                last_scanned_at INTEGER,
                created_at INTEGER NOT NULL,
                disabled INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
    }
}
