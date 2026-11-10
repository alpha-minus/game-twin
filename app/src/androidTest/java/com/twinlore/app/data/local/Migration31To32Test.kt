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
class Migration31To32Test {
    private val dbName = "migration-31-32-test.db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        VaultDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrate31To32_preserves_group_rows_and_adds_folder_name_column() {
        // Seed v31 with one group saved_tabs row (legacy, no group_folder_name yet).
        helper.createDatabase(dbName, 31).use { db ->
            db.execSQL(
                "INSERT INTO vaults (id, name, createdAt) VALUES ('v1', 'Vault One', 0)"
            )
            db.execSQL(
                """
                INSERT INTO saved_tabs (id, vaultId, title, url, scrollPosition, zoomLevel,
                    isFavorite, isMap, isCurrentContent, tab_order, createdAt, lastAccessed,
                    fileType, lastPdfPage, pdfScrollOffset, scroll_occurrence_index, scroll_ratio,
                    group_active_entry_id)
                VALUES ('g1', 'v1', 'A Link to the Past', 'https://example.com/', 0, 1.0,
                    0, 0, 0, 0, 0, 0, 'group', 0, 0.0, 0, 0.0, NULL)
                """.trimIndent()
            )
        }

        helper.runMigrationsAndValidate(dbName, 32, true, VaultDatabaseMigration.MIGRATION_31_32).use { db ->
            db.query(
                "SELECT id, title, fileType, group_folder_name FROM saved_tabs WHERE id = 'g1'"
            ).use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("g1", c.getString(0))
                assertEquals("A Link to the Past", c.getString(1))
                assertEquals("group", c.getString(2))
                // Legacy rows leave group_folder_name null — the resolver falls back
                // to the v0 groups/<groupTabId>/ layout for these.
                assertTrue(c.isNull(3))
            }
            // New rows can record a folder name.
            db.execSQL(
                """
                INSERT INTO saved_tabs (id, vaultId, title, url, scrollPosition, zoomLevel,
                    isFavorite, isMap, isCurrentContent, tab_order, createdAt, lastAccessed,
                    fileType, lastPdfPage, pdfScrollOffset, scroll_occurrence_index, scroll_ratio,
                    group_active_entry_id, group_folder_name)
                VALUES ('g2', 'v1', 'New Group', 'https://x/', 0, 1.0,
                    0, 0, 0, 1, 0, 0, 'group', 0, 0.0, 0, 0.0, NULL, 'New Group')
                """.trimIndent()
            )
            db.query("SELECT group_folder_name FROM saved_tabs WHERE id = 'g2'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("New Group", c.getString(0))
            }
        }
    }
}
