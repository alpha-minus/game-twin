package com.twinlore.app.data.library

import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

@RunWith(RobolectricTestRunner::class)
class LibrarySnapshotResolverTest {

    @get:Rule val tempFolder = TemporaryFolder()

    private fun snapshot(home: File, name: String, lastWrittenAt: Long, deviceId: String): File {
        val file = File(home, name)
        val db = SQLiteDatabase.openOrCreateDatabase(file, null)
        db.execSQL(
            "CREATE TABLE library_meta (id INTEGER PRIMARY KEY, schemaVersion INTEGER NOT NULL, " +
            "lastWrittenAtMillis INTEGER NOT NULL, lastWrittenDeviceId TEXT NOT NULL, " +
            "lastWrittenDeviceLabel TEXT NOT NULL)"
        )
        db.execSQL(
            "INSERT INTO library_meta VALUES (1, 24, ?, ?, '')",
            arrayOf<Any>(lastWrittenAt, deviceId)
        )
        db.close()
        return file
    }

    private val ctx = RuntimeEnvironment.getApplication()

    @Test
    fun findSnapshotsNewestFirst_empty_folder_returns_empty() = runTest {
        val home = tempFolder.newFolder("home")
        val out = findSnapshotsNewestFirst(ctx, Uri.fromFile(home), "abcdef01")
        assertTrue(out.isEmpty())
    }

    @Test
    fun findSnapshotsNewestFirst_returns_bare_and_per_device_sorted_newest_first() = runTest {
        val home = tempFolder.newFolder("home")
        snapshot(home, "library.gametwin", 100L, "old-device")
        snapshot(home, "library.bbbbbbbb.gametwin", 300L, "device-B")
        snapshot(home, "library.cccccccc.gametwin", 200L, "device-C")

        val out = findSnapshotsNewestFirst(ctx, Uri.fromFile(home), "ffffffff")
        assertEquals(3, out.size)
        assertEquals("library.bbbbbbbb.gametwin", File(out[0].path!!).name)
        assertEquals("library.cccccccc.gametwin", File(out[1].path!!).name)
        assertEquals("library.gametwin", File(out[2].path!!).name)
    }

    @Test
    fun findSnapshotsNewestFirst_tie_breaker_prefers_our_deviceId() = runTest {
        val home = tempFolder.newFolder("home")
        snapshot(home, "library.aaaaaaaa.gametwin", 500L, "aaaaaaaa-0000-0000-0000-000000000000")
        snapshot(home, "library.bbbbbbbb.gametwin", 500L, "bbbbbbbb-0000-0000-0000-000000000000")

        val out = findSnapshotsNewestFirst(ctx, Uri.fromFile(home), "bbbbbbbb")
        assertEquals("library.bbbbbbbb.gametwin", File(out[0].path!!).name)
        assertEquals("library.aaaaaaaa.gametwin", File(out[1].path!!).name)
    }

    @Test
    fun findSnapshotsNewestFirst_skips_unreadable_and_non_matching_files() = runTest {
        val home = tempFolder.newFolder("home")
        snapshot(home, "library.aaaaaaaa.gametwin", 100L, "device-A")
        File(home, "library.bbbbbbbb.gametwin").writeBytes(byteArrayOf(0x00, 0x01))
        File(home, "library.aaaaaaaa.gametwin.partial").writeBytes(byteArrayOf(0x00))
        File(home, "library.NOTGOOD.gametwin").writeBytes(byteArrayOf(0x00))

        val out = findSnapshotsNewestFirst(ctx, Uri.fromFile(home), "ffffffff")
        assertEquals(1, out.size)
        assertEquals("library.aaaaaaaa.gametwin", File(out[0].path!!).name)
    }

    @Test
    fun readSnapshotShortDeviceId_returns_8_hex_chars() = runTest {
        val home = tempFolder.newFolder("home")
        val file = snapshot(home, "library.gametwin", 1L, "A3F2C8E1-b4d5-4e6f-9a0b-1c2d3e4f5060")
        assertEquals("a3f2c8e1", readSnapshotShortDeviceId(ctx, Uri.fromFile(file)))
    }

    @Test
    fun readSnapshotShortDeviceId_returns_null_for_non_sqlite() = runTest {
        val home = tempFolder.newFolder("home")
        val bogus = File(home, "library.gametwin").apply { writeBytes(byteArrayOf(0x00, 0x01)) }
        assertNull(readSnapshotShortDeviceId(ctx, Uri.fromFile(bogus)))
    }

    /** Writes a snapshot with explicit library_meta.schemaVersion AND PRAGMA user_version. */
    private fun snapshotWithVersions(home: File, name: String, metaSchema: Int, userVersion: Int): File {
        val file = File(home, name)
        val db = SQLiteDatabase.openOrCreateDatabase(file, null)
        db.execSQL("PRAGMA user_version = $userVersion")
        db.execSQL(
            "CREATE TABLE library_meta (id INTEGER PRIMARY KEY, schemaVersion INTEGER NOT NULL, " +
            "lastWrittenAtMillis INTEGER NOT NULL, lastWrittenDeviceId TEXT NOT NULL, " +
            "lastWrittenDeviceLabel TEXT NOT NULL)"
        )
        db.execSQL("INSERT INTO library_meta VALUES (1, $metaSchema, 1, 'dev', '')")
        db.close()
        return file
    }

    @Test
    fun readSnapshotSchemaVersion_uses_room_user_version_when_meta_lags() = runTest {
        // The loop trigger: library_meta says an old/compatible version but Room's
        // on-disk user_version is newer. The authoritative read must surface the
        // higher user_version so the import guard can refuse it.
        val home = tempFolder.newFolder("home")
        val file = snapshotWithVersions(home, "library.gametwin", metaSchema = 35, userVersion = 40)
        assertEquals(40, readSnapshotSchemaVersion(ctx, Uri.fromFile(file)))
    }

    @Test
    fun readSnapshotSchemaVersion_uses_meta_when_higher_than_user_version() = runTest {
        val home = tempFolder.newFolder("home")
        val file = snapshotWithVersions(home, "library.gametwin", metaSchema = 36, userVersion = 0)
        assertEquals(36, readSnapshotSchemaVersion(ctx, Uri.fromFile(file)))
    }
}
