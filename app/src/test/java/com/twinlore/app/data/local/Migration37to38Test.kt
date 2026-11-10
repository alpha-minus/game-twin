package com.twinlore.app.data.local

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * MIGRATION_37_38: favorite_apps gains `position`, seeded from addedAt rank with rowid
 * breaking ties (batch pins can share a millisecond). Runs behind the no-op
 * MIGRATION_36_37 — version 37 was burned by the frozen achievement-journey build —
 * so the chain from a v36 core schema is exercised here too.
 */
@RunWith(RobolectricTestRunner::class)
class Migration37to38Test {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val dbFile: File = File(context.cacheDir, "migration-test-37-to-38.db")
    private var helper: SupportSQLiteOpenHelper? = null

    @After
    fun tearDown() {
        helper?.close()
        if (dbFile.exists()) dbFile.delete()
    }

    @Test
    fun migrate36Through38_seedsPositionFromAddedAtRank() {
        if (dbFile.exists()) dbFile.delete()

        val factory = FrameworkSQLiteOpenHelperFactory()
        val callback = object : SupportSQLiteOpenHelper.Callback(/* version = */ 1) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS favorite_apps (
                        packageName TEXT NOT NULL PRIMARY KEY,
                        addedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                // b and c share addedAt; b was inserted first, so rowid orders b before c.
                db.execSQL("INSERT INTO favorite_apps (packageName, addedAt) VALUES ('app.a', 100)")
                db.execSQL("INSERT INTO favorite_apps (packageName, addedAt) VALUES ('app.b', 200)")
                db.execSQL("INSERT INTO favorite_apps (packageName, addedAt) VALUES ('app.c', 200)")
            }
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
        }
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbFile.absolutePath)
            .callback(callback)
            .build()
        val openHelper = factory.create(configuration)
        helper = openHelper
        openHelper.writableDatabase

        // The full path a v36 device takes: the burned-version no-op, then the column.
        VaultDatabaseMigration.MIGRATION_36_37.migrate(openHelper.writableDatabase)
        VaultDatabaseMigration.MIGRATION_37_38.migrate(openHelper.writableDatabase)

        val db = openHelper.writableDatabase
        db.query(
            "SELECT packageName, position FROM favorite_apps ORDER BY position ASC"
        ).use { c ->
            val rows = mutableListOf<Pair<String, Int>>()
            while (c.moveToNext()) rows += c.getString(0) to c.getInt(1)
            assertEquals(
                listOf("app.a" to 0, "app.b" to 1, "app.c" to 2),
                rows,
            )
        }
    }
}
