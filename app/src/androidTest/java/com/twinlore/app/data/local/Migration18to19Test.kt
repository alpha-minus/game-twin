package com.twinlore.app.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration18to19Test {

    private val dbName = "migration-test-18-19"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        VaultDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migration_18_to_19_creates_all_new_tables_and_preserves_existing_data() {
        helper.createDatabase(dbName, 18).use { db ->
            db.execSQL(
                """
                INSERT INTO vaults (id, name, artworkPath, createdAt)
                VALUES ('v1', 'Test', '/legacy/v1/art/cover.jpg', 0)
                """.trimIndent()
            )
        }

        val migrated = helper.runMigrationsAndValidate(
            dbName,
            19,
            true,
            VaultDatabaseMigration.MIGRATION_18_19
        )

        migrated.use { db ->
            db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='resource_files'").use {
                assertEquals(1, it.count)
            }
            db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='map_markers'").use {
                assertEquals(1, it.count)
            }
            db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='page_bookmarks'").use {
                assertEquals(1, it.count)
            }
            db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='scroll_markers'").use {
                assertEquals(1, it.count)
            }

            db.query("SELECT artwork_resource_id, guide_resource_id, map_resource_id FROM vaults WHERE id = 'v1'").use { c ->
                assertEquals(1, c.count)
                c.moveToFirst()
                assertNotNull(c)
            }

            db.query("SELECT artworkPath FROM vaults WHERE id = 'v1'").use { c ->
                c.moveToFirst()
                assertEquals("/legacy/v1/art/cover.jpg", c.getString(0))
            }

            db.query("SELECT launchIntentJson FROM vaults WHERE id = 'v1'").use { c ->
                assertEquals(1, c.count)
            }
            db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='favorite_apps'").use {
                assertEquals(1, it.count)
            }
        }
    }
}
