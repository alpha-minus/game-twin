package com.twinlore.app.data.local

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class ChecklistMigration12to13Test {

    private val dbName = "migration-test-12-13"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        VaultDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migration_12_to_13_creates_tabs_sections_and_moves_items() {
        // Arrange: build a v12 DB with one vault, two color tags (Red=1 and Blue=2) and three items.
        // VaultEntity v12 columns: id, name, romPath, romFilename, artworkPath, artworkVibrantArgb,
        //   mapPath, guidePath, systemeid, gameId, source, raGameId, steamAppId, lastOpened, createdAt
        // Only id, name, and createdAt are NOT NULL without defaults; all others are nullable.
        helper.createDatabase(dbName, 12).use { db ->
            db.execSQL(
                """
                INSERT INTO vaults (id, name, artworkPath, createdAt)
                VALUES ('vault-1', 'Test', NULL, 0)
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO checklist_tags (id, vaultId, name, color, sortOrder, createdAt, updatedAt)
                VALUES
                  ('tag-red',  'vault-1', 'Red',  1, 0, 0, 0),
                  ('tag-blue', 'vault-1', 'Blue', 2, 1, 0, 0)
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO checklist_items (id, vaultId, tagId, title, completed, sortOrder, createdAt, updatedAt)
                VALUES
                  ('i1', 'vault-1', 'tag-red',  'Boss A', 0, 0, 0, 0),
                  ('i2', 'vault-1', 'tag-red',  'Boss B', 1, 1, 0, 0),
                  ('i3', 'vault-1', 'tag-blue', 'Spell A', 0, 0, 0, 0)
                """.trimIndent()
            )
        }

        // Act
        val migrated = helper.runMigrationsAndValidate(
            dbName,
            13,
            true,
            VaultDatabaseMigration.MIGRATION_12_13
        )

        migrated.use { db ->
            db.query("SELECT id, name, color FROM checklist_tabs WHERE vaultId = 'vault-1' ORDER BY sortOrder ASC").use { c ->
                assertEquals(2, c.count)
                c.moveToFirst()
                assertEquals("Red",  c.getString(c.getColumnIndexOrThrow("name")))
                assertEquals(0, c.getInt(c.getColumnIndexOrThrow("color")))
                c.moveToNext()
                assertEquals("Blue", c.getString(c.getColumnIndexOrThrow("name")))
                assertEquals(1, c.getInt(c.getColumnIndexOrThrow("color")))
            }

            db.query("SELECT COUNT(*) FROM checklist_sections WHERE vaultId = 'vault-1' AND name = 'General'").use { c ->
                c.moveToFirst()
                assertEquals(2, c.getInt(0))
            }

            db.query("SELECT COUNT(*) FROM checklist_items WHERE vaultId = 'vault-1' AND sectionId != ''").use { c ->
                c.moveToFirst()
                assertEquals(3, c.getInt(0))
            }

            db.query(
                """
                SELECT i.id
                FROM checklist_items i
                JOIN checklist_sections s ON s.id = i.sectionId
                JOIN checklist_tabs t ON t.id = s.tabId
                WHERE i.vaultId = 'vault-1' AND t.name = 'Red' AND s.name = 'General'
                """.trimIndent()
            ).use { c ->
                assertEquals(2, c.count)
            }
        }
    }

    @Test
    fun migration_12_to_13_handles_vault_with_no_items() {
        helper.createDatabase(dbName, 12).use { db ->
            db.execSQL(
                """
                INSERT INTO vaults (id, name, artworkPath, createdAt)
                VALUES ('vault-empty', 'Empty', NULL, 0)
                """.trimIndent()
            )
        }

        val migrated = helper.runMigrationsAndValidate(
            dbName,
            13,
            true,
            VaultDatabaseMigration.MIGRATION_12_13
        )

        migrated.use { db ->
            db.query("SELECT COUNT(*) FROM checklist_tabs WHERE vaultId = 'vault-empty'").use { c ->
                c.moveToFirst()
                assertEquals(0, c.getInt(0))
            }
            db.query("SELECT COUNT(*) FROM checklist_sections WHERE vaultId = 'vault-empty'").use { c ->
                c.moveToFirst()
                assertEquals(0, c.getInt(0))
            }
        }
    }
}
