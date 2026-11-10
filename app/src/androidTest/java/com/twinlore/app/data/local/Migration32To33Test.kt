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
class Migration32To33Test {
    private val dbName = "migration-32-33-test.db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        VaultDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrate32To33_preserves_markers_and_adds_anchors_table() {
        // Seed v32: a vault, a group saved_tabs row, one page_group_entries row,
        // and one legacy scroll_markers row (page_group_entry_id should be null
        // after migration).
        helper.createDatabase(dbName, 32).use { db ->
            db.execSQL(
                "INSERT INTO vaults (id, name, createdAt) VALUES ('v1', 'Vault One', 0)"
            )
            db.execSQL(
                """
                INSERT INTO saved_tabs (id, vaultId, title, url, scrollPosition, zoomLevel,
                    isFavorite, isMap, isCurrentContent, tab_order, createdAt, lastAccessed,
                    fileType, lastPdfPage, pdfScrollOffset, scroll_occurrence_index, scroll_ratio,
                    group_active_entry_id, group_folder_name)
                VALUES ('g1', 'v1', 'Group', 'https://x/', 0, 1.0,
                    0, 0, 0, 0, 0, 0, 'group', 0, 0.0, 0, 0.0, NULL, 'Group')
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO page_group_entries (id, group_tab_id, position, label, saved_href,
                    fragment, mht_resource_id, scroll_anchor, scroll_occurrence_index, scroll_ratio)
                VALUES ('e1', 'g1', 0, 'Index', 'https://x/', NULL, NULL, NULL, 0, 0.0)
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO scroll_markers (id, saved_tab_id, scroll_position, marker_order,
                    created_at, label, occurrence_index)
                VALUES ('m1', 'g1', 1200, 0, 0, 'legacy marker', 0)
                """.trimIndent()
            )
        }

        helper.runMigrationsAndValidate(dbName, 33, true, VaultDatabaseMigration.MIGRATION_32_33).use { db ->
            // Legacy marker preserved + new column is null on it.
            db.query("SELECT id, page_group_entry_id FROM scroll_markers WHERE id = 'm1'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("m1", c.getString(0))
                assertTrue(c.isNull(1))
            }
            // New anchors table reachable + accepts inserts.
            db.execSQL(
                """
                INSERT INTO page_group_entry_anchors (id, page_group_entry_id, position, label, scroll_position)
                VALUES ('a1', 'e1', 0, 'Intro', 80)
                """.trimIndent()
            )
            db.query("SELECT COUNT(*) FROM page_group_entry_anchors").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(1, c.getInt(0))
            }
            // FK cascade works: deleting the entry removes its anchor.
            // SQLite foreign key enforcement is OFF by default; enable it for this assertion.
            db.execSQL("PRAGMA foreign_keys = ON")
            db.execSQL("DELETE FROM page_group_entries WHERE id = 'e1'")
            db.query("SELECT COUNT(*) FROM page_group_entry_anchors").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(0, c.getInt(0))
            }
            // Index present.
            db.query(
                "SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name = 'page_group_entry_anchors'"
            ).use { c ->
                val names = mutableListOf<String>()
                while (c.moveToNext()) names += c.getString(0)
                assertTrue(names.any { it.contains("page_group_entry_id") })
            }
        }
    }
}
