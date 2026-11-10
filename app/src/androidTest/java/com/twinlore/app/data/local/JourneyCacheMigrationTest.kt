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
class JourneyCacheMigrationTest {
    private val dbName = "migration-38-39-test.db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        VaultDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrate38To39_addsJourneyCacheTables() {
        // v38 has neither table; just create it to establish the starting schema.
        helper.createDatabase(dbName, 38).close()

        helper.runMigrationsAndValidate(dbName, 39, true, VaultDatabaseMigration.MIGRATION_38_39).use { db ->
            // journey_game_cache: composite PK, accepts inserts, columns as expected.
            db.execSQL(
                """
                INSERT INTO journey_game_cache (userName, gameId, fetchedAt, validatorJson, payloadJson)
                VALUES ('player1', 1, 1000, '{}', '{"id":1}')
                """.trimIndent()
            )
            db.query("SELECT userName, gameId, fetchedAt, validatorJson, payloadJson FROM journey_game_cache").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("player1", c.getString(0))
                assertEquals(1, c.getInt(1))
                assertEquals(1000L, c.getLong(2))
                assertEquals("{}", c.getString(3))
                assertEquals("{\"id\":1}", c.getString(4))
            }

            // journey_completion_cache: single-column PK, accepts inserts, columns as expected.
            db.execSQL(
                """
                INSERT INTO journey_completion_cache (userName, fetchedAt, payloadJson)
                VALUES ('player1', 2000, '[]')
                """.trimIndent()
            )
            db.query("SELECT userName, fetchedAt, payloadJson FROM journey_completion_cache").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("player1", c.getString(0))
                assertEquals(2000L, c.getLong(1))
                assertEquals("[]", c.getString(2))
            }

            // Table names present in sqlite_master.
            db.query(
                "SELECT name FROM sqlite_master WHERE type = 'table' AND name IN " +
                    "('journey_game_cache', 'journey_completion_cache')"
            ).use { c ->
                val names = mutableListOf<String>()
                while (c.moveToNext()) names += c.getString(0)
                assertTrue(names.contains("journey_game_cache"))
                assertTrue(names.contains("journey_completion_cache"))
            }
        }
    }

    /**
     * Devices that ran the frozen journey build sit at the burned v37 WITH the journey tables
     * already present (see MIGRATION_36_37 kdoc): a v36 schema plus the two tables its own
     * migration created. Their upgrade must add favorite_apps.position (37>38) and leave the
     * existing journey tables and their rows untouched (38>39's IF NOT EXISTS), and still pass
     * Room's schema validation at 39.
     *
     * Built from 36.json rather than 37.json: the checked-in 37.json is a stale export that
     * already carries the position column (it was written while main briefly ran version 37 with
     * the entity change, before the renumber to 38), so it matches no real v37 device. Starting
     * at the true v36 schema and running the no-op 36>37 first reproduces exactly what the frozen
     * build left on a device.
     */
    @Test
    fun migrate36To39_burnedV37DeviceKeepsExistingJourneyTables() {
        helper.createDatabase("burned-v37-test.db", 36).apply {
            execSQL(
                "CREATE TABLE IF NOT EXISTS `journey_game_cache` (" +
                    "`userName` TEXT NOT NULL, `gameId` INTEGER NOT NULL, " +
                    "`fetchedAt` INTEGER NOT NULL, `validatorJson` TEXT NOT NULL, " +
                    "`payloadJson` TEXT NOT NULL, PRIMARY KEY(`userName`, `gameId`))"
            )
            execSQL(
                "CREATE TABLE IF NOT EXISTS `journey_completion_cache` (" +
                    "`userName` TEXT NOT NULL, `fetchedAt` INTEGER NOT NULL, " +
                    "`payloadJson` TEXT NOT NULL, PRIMARY KEY(`userName`))"
            )
            execSQL(
                "INSERT INTO journey_game_cache (userName, gameId, fetchedAt, validatorJson, payloadJson) " +
                    "VALUES ('thor', 7, 500, '{}', '{}')"
            )
            close()
        }

        helper.runMigrationsAndValidate(
            "burned-v37-test.db", 39, true,
            VaultDatabaseMigration.MIGRATION_36_37,
            VaultDatabaseMigration.MIGRATION_37_38,
            VaultDatabaseMigration.MIGRATION_38_39
        ).use { db ->
            db.query("SELECT userName, gameId FROM journey_game_cache").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("thor", c.getString(0))
                assertEquals(7, c.getInt(1))
            }
        }
    }
}
