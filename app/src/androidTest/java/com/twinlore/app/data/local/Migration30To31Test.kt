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
class Migration30To31Test {
    private val dbName = "migration-30-31-test.db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        VaultDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrate30To31_preserves_saved_tabs_and_adds_table() {
        helper.createDatabase(dbName, 30).use { db ->
            db.execSQL("INSERT INTO vaults (id, name, createdAt) VALUES ('v1', 'Vault One', 0)")
            db.execSQL(
                """
                INSERT INTO saved_tabs (id, vaultId, title, url, scrollPosition, zoomLevel,
                    isFavorite, isMap, isCurrentContent, tab_order, createdAt, lastAccessed,
                    fileType, lastPdfPage, pdfScrollOffset, scroll_occurrence_index, scroll_ratio)
                VALUES ('t1', 'v1', 'Tab', 'https://example.com', 0, 1.0,
                    0, 0, 0, 0, 0, 0, 'html', 0, 0.0, 0, 0.0)
                """.trimIndent()
            )
        }

        helper.runMigrationsAndValidate(dbName, 31, true, VaultDatabaseMigration.MIGRATION_30_31).use { db ->
            db.query("SELECT id, group_active_entry_id FROM saved_tabs WHERE id = 't1'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("t1", c.getString(0))
                assertTrue(c.isNull(1))
            }
            db.execSQL(
                """
                INSERT INTO page_group_entries
                  (id, group_tab_id, position, label, saved_href, fragment, mht_resource_id,
                   scroll_anchor, scroll_occurrence_index, scroll_ratio)
                VALUES ('e1', 't1', 0, 'Index', 'https://example.com', NULL, NULL,
                   NULL, 0, 0.0)
                """.trimIndent()
            )
            db.query("SELECT COUNT(*) FROM page_group_entries").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(1, c.getInt(0))
            }
            db.query("SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name = 'page_group_entries'").use { c ->
                val indexNames = mutableListOf<String>()
                while (c.moveToNext()) indexNames += c.getString(0)
                assertTrue(indexNames.any { it.contains("group_tab_id") })
                assertTrue(indexNames.any { it.contains("mht_resource_id") })
            }
        }
    }
}
