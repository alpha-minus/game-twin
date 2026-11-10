package com.twinlore.app.data.library

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Pins the schemaVersion read path used by [LibraryImporter] to refuse
 * newer-schema snapshots before they would be destructively migrated.
 *
 * The resolver is internal so the test lives in the same package.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LibrarySnapshotSchemaVersionTest {

    private lateinit var context: Context
    private lateinit var folder: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        folder = File(context.cacheDir, "snap-schema-${System.nanoTime()}").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        folder.deleteRecursively()
    }

    @Test
    fun `readSnapshotSchemaVersion returns the persisted version`() = runTest {
        val snapshot = createSnapshot(
            folder,
            // matches LibraryFileStore.shortDeviceId conventions
            // (8-hex-char short id); any 8-char hex string works.
            fileName = "library.abc12345.gametwin",
            schemaVersion = 99,
        )

        val v = readSnapshotSchemaVersion(context, Uri.fromFile(snapshot))

        assertEquals(99, v)
    }

    @Test
    fun `readSnapshotSchemaVersion returns null for missing file`() = runTest {
        val ghost = File(folder, "library.deadbeef.gametwin")

        val v = readSnapshotSchemaVersion(context, Uri.fromFile(ghost))

        assertNull(v)
    }

    @Test
    fun `readSnapshotSchemaVersion returns null when library_meta row is missing`() = runTest {
        val snapshot = File(folder, "library.cafebabe.gametwin")
        SQLiteDatabase.openOrCreateDatabase(snapshot, null).use { db ->
            // Table present but no row — the SELECT's moveToFirst() fails.
            db.execSQL(
                "CREATE TABLE library_meta (" +
                    "id INTEGER PRIMARY KEY, " +
                    "schemaVersion INTEGER, " +
                    "lastWrittenAtMillis INTEGER, " +
                    "lastWrittenDeviceId TEXT, " +
                    "lastWrittenDeviceLabel TEXT)"
            )
        }

        val v = readSnapshotSchemaVersion(context, Uri.fromFile(snapshot))

        assertNull(v)
    }

    private fun createSnapshot(
        dir: File,
        fileName: String,
        schemaVersion: Int,
        deviceId: String = "abc12345-aaaa-bbbb-cccc-1234567890ab",
        timestamp: Long = 1_700_000_000_000L,
    ): File {
        val file = File(dir, fileName)
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL(
                "CREATE TABLE library_meta (" +
                    "id INTEGER PRIMARY KEY, " +
                    "schemaVersion INTEGER, " +
                    "lastWrittenAtMillis INTEGER, " +
                    "lastWrittenDeviceId TEXT, " +
                    "lastWrittenDeviceLabel TEXT)"
            )
            db.execSQL(
                "INSERT INTO library_meta VALUES (1, ?, ?, ?, ?)",
                arrayOf(schemaVersion, timestamp, deviceId, "TestDevice"),
            )
        }
        return file
    }
}
