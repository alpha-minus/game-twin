package com.twinlore.app.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration29to30Test {

    private val dbName = "migration-test-29-30"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        VaultDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migration_29_to_30_adds_history_columns_and_backfills_from_url_and_title() {
        helper.createDatabase(dbName, 29).use { db ->
            db.execSQL(
                """
                INSERT INTO vaults (id, name, createdAt)
                VALUES ('v1', 'TestVault', 0)
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO browser_tabs
                    (id, vaultId, url, title, faviconBytes, scrollY, tab_order, lastAccessed, createdAt)
                VALUES
                    ('t1', 'v1', 'https://example.com/page', 'Example Page', NULL, 0, 0, 100, 100)
                """.trimIndent()
            )
        }

        val migrated = helper.runMigrationsAndValidate(
            dbName,
            30,
            true,
            VaultDatabaseMigration.MIGRATION_29_30
        )

        migrated.use { db ->
            db.query("SELECT history_json, history_index FROM browser_tabs WHERE id = 't1'").use { c ->
                assertEquals(1, c.count)
                c.moveToFirst()
                val json = c.getString(0)
                val idx = c.getInt(1)
                assertEquals(0, idx)
                // SQLite's json_object preserves insertion order; assert both fields are present
                // and reference the row's url + title. We don't pin exact whitespace because
                // json_array()/json_object() output formatting can vary across SQLite versions.
                assertTrue("expected url in history_json: $json", json.contains("\"url\":\"https://example.com/page\""))
                assertTrue("expected title in history_json: $json", json.contains("\"title\":\"Example Page\""))
            }
        }
    }
}
