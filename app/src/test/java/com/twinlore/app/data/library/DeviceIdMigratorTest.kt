package com.twinlore.app.data.library

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DeviceIdMigratorTest {

    private lateinit var context: Context
    private lateinit var home: File
    private val oldUuid = "abc12345-aaaa-bbbb-cccc-1234567890ab" // short: abc12345
    private val newId = "fedcba9876543210"                       // short: fedcba98

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        home = File(context.cacheDir, "home-${System.nanoTime()}").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        home.deleteRecursively()
    }

    private fun snapshot(name: String, writerDeviceId: String): File {
        val f = File(home, name)
        SQLiteDatabase.openOrCreateDatabase(f, null).use { db ->
            db.execSQL(
                "CREATE TABLE library_meta (id INTEGER PRIMARY KEY, schemaVersion INTEGER, " +
                    "lastWrittenAtMillis INTEGER, lastWrittenDeviceId TEXT, lastWrittenDeviceLabel TEXT)"
            )
            db.execSQL("INSERT INTO library_meta VALUES (1, 35, 100, '$writerDeviceId', 'X')")
        }
        return f
    }

    @Test
    fun `renames our old-uuid snapshot to the new id`() = runTest {
        snapshot("library.abc12345.gametwin", oldUuid)
        migrateSnapshotFileName(context, Uri.fromFile(home), oldUuid, newId)
        assertFalse(File(home, "library.abc12345.gametwin").exists())
        assertTrue(File(home, "library.fedcba98.gametwin").exists())
    }

    @Test
    fun `deletes old snapshot when new name already exists`() = runTest {
        snapshot("library.abc12345.gametwin", oldUuid)
        snapshot("library.fedcba98.gametwin", newId)
        migrateSnapshotFileName(context, Uri.fromFile(home), oldUuid, newId)
        assertFalse(File(home, "library.abc12345.gametwin").exists())
        assertTrue(File(home, "library.fedcba98.gametwin").exists())
    }

    /**
     * The migration's whole trigger: it may fire only on ANDROID_ID-shaped ids (16 lowercase
     * hex chars), never on UUIDs, and it detects them by shape because reading the live
     * ANDROID_ID to compare would itself be the § 25 TDDG access it exists to remove.
     */
    @Test
    fun `the android-id shape matches only what the migration must replace`() {
        assertTrue(DeviceIdMigrator.ANDROID_ID_SHAPE.matches("fedcba9876543210"))
        assertFalse(DeviceIdMigrator.ANDROID_ID_SHAPE.matches(oldUuid))
        assertFalse(DeviceIdMigrator.ANDROID_ID_SHAPE.matches(""))
        assertFalse(DeviceIdMigrator.ANDROID_ID_SHAPE.matches("FEDCBA9876543210"))
        assertFalse(DeviceIdMigrator.ANDROID_ID_SHAPE.matches("fedcba98765432100"))
    }

    /** The live direction since the § 25 revert: ANDROID_ID-named file → UUID name. */
    @Test
    fun `renames an android-id snapshot to its new uuid name`() = runTest {
        snapshot("library.fedcba98.gametwin", newId)
        migrateSnapshotFileName(context, Uri.fromFile(home), newId, oldUuid)
        assertFalse(File(home, "library.fedcba98.gametwin").exists())
        assertTrue(File(home, "library.abc12345.gametwin").exists())
    }

    @Test
    fun `leaves a different device's snapshot untouched`() = runTest {
        // file named like ours but its embedded writer id is another device
        snapshot("library.abc12345.gametwin", "deadbeef-9999-8888-7777-666666666666")
        migrateSnapshotFileName(context, Uri.fromFile(home), oldUuid, newId)
        assertTrue(File(home, "library.abc12345.gametwin").exists())
        assertEquals(1, home.listFiles()!!.size)
    }
}
