package com.twinlore.app.data.library

import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

@RunWith(RobolectricTestRunner::class)
class LibraryImporterTest {
    @get:Rule val tempFolder = TemporaryFolder()

    private fun writeSnapshot(
        file: File,
        lastWrittenAt: Long,
        sentinel: Byte,
        vaultCount: Int? = null,
        schemaVersion: Int = 24,
    ) {
        val db = SQLiteDatabase.openOrCreateDatabase(file, null)
        db.execSQL(
            "CREATE TABLE library_meta (id INTEGER PRIMARY KEY, schemaVersion INTEGER NOT NULL, " +
            "lastWrittenAtMillis INTEGER NOT NULL, lastWrittenDeviceId TEXT NOT NULL, " +
            "lastWrittenDeviceLabel TEXT NOT NULL)"
        )
        db.execSQL(
            "INSERT INTO library_meta VALUES (1, ?, ?, 'd', '')",
            arrayOf<Any>(schemaVersion, lastWrittenAt)
        )
        if (vaultCount != null) {
            db.execSQL("CREATE TABLE vaults (id TEXT PRIMARY KEY, name TEXT, createdAt INTEGER)")
            for (i in 1..vaultCount) {
                db.execSQL("INSERT INTO vaults VALUES ('v$i', 'Vault $i', 0)")
            }
        }
        db.close()
        java.io.RandomAccessFile(file, "rw").use { raf ->
            raf.seek(raf.length()); raf.write(byteArrayOf(sentinel))
        }
    }

    private fun newImporter(target: File) = LibraryImporter(
        context = RuntimeEnvironment.getApplication(),
        targetDbProvider = { target },
        checkpoint = {},
        closeDb = {},
        artworkThumbnailCache = ArtworkThumbnailCache(RuntimeEnvironment.getApplication()),
        mapThumbnailCache = MapThumbnailCache(RuntimeEnvironment.getApplication(), { RuntimeEnvironment.getApplication().cacheDir }),
    )

    @Test
    fun importFrom_picks_newest_per_device_snapshot() = runTest {
        val source = tempFolder.newFolder("source")
        writeSnapshot(File(source, "library.aaaaaaaa.gametwin"), 100L, 0x01)
        writeSnapshot(File(source, "library.bbbbbbbb.gametwin"), 200L, 0x02)
        val targetDb = tempFolder.newFile("target.db").apply { writeBytes(ByteArray(8)) }

        newImporter(targetDb).importFrom(Uri.fromFile(source))

        val targetBytes = targetDb.readBytes()
        assertEquals("imported the newest (sentinel byte 0x02)", 0x02.toByte(), targetBytes.last())
    }

    @Test
    fun importFrom_falls_back_to_bare_when_only_legacy_present() = runTest {
        val source = tempFolder.newFolder("source")
        writeSnapshot(File(source, "library.gametwin"), 500L, 0x03)
        val targetDb = tempFolder.newFile("target.db").apply { writeBytes(ByteArray(8)) }

        newImporter(targetDb).importFrom(Uri.fromFile(source))

        assertEquals(File(source, "library.gametwin").length(), targetDb.length())
    }

    @Test
    fun importFrom_proceeds_when_unlicensed_and_snapshot_exceeds_former_cap() = runTest {
        // An unlicensed install loads a library of any size (the former 2-vault cap no longer blocks import).
        val source = tempFolder.newFolder("source")
        writeSnapshot(File(source, "library.aaaaaaaa.gametwin"), 100L, 0x01, vaultCount = 3)
        val targetDb = tempFolder.newFile("target.db").apply { writeBytes(ByteArray(8)) }

        newImporter(targetDb).importFrom(Uri.fromFile(source))

        assertEquals(0x01.toByte(), targetDb.readBytes().last())
    }

    @Test
    fun importFrom_proceeds_when_licensed_and_snapshot_exceeds_cap() = runTest {
        val source = tempFolder.newFolder("source")
        writeSnapshot(File(source, "library.aaaaaaaa.gametwin"), 100L, 0x07, vaultCount = 3)
        val targetDb = tempFolder.newFile("target.db").apply { writeBytes(ByteArray(8)) }

        newImporter(targetDb).importFrom(Uri.fromFile(source))

        assertEquals(0x07.toByte(), targetDb.readBytes().last())
    }

    @Test
    fun importFrom_proceeds_when_unlicensed_and_snapshot_is_within_cap() = runTest {
        val source = tempFolder.newFolder("source")
        writeSnapshot(File(source, "library.aaaaaaaa.gametwin"), 100L, 0x08, vaultCount = 2)
        val targetDb = tempFolder.newFile("target.db").apply { writeBytes(ByteArray(8)) }

        newImporter(targetDb).importFrom(Uri.fromFile(source))

        assertEquals(0x08.toByte(), targetDb.readBytes().last())
    }

    @Test
    fun importFrom_refuses_too_new_snapshot_without_touching_target() = runTest {
        // A snapshot whose schema version exceeds this build's SCHEMA_VERSION (35)
        // must be refused BEFORE the DB is closed/copied, so the live library the
        // caller is about to switch away from is left byte-for-byte intact.
        val source = tempFolder.newFolder("source")
        writeSnapshot(File(source, "library.aaaaaaaa.gametwin"), 100L, 0x0F, schemaVersion = 99)
        val original = ByteArray(8) { 0x2A }
        val targetDb = tempFolder.newFile("target.db").apply { writeBytes(original) }

        var thrown: LibrarySnapshotTooNewException? = null
        try { newImporter(targetDb).importFrom(Uri.fromFile(source)) }
        catch (e: LibrarySnapshotTooNewException) { thrown = e }

        assert(thrown != null) { "Expected LibrarySnapshotTooNewException for schemaVersion 99" }
        assertEquals(99, thrown!!.savedVersion)
        assert(targetDb.readBytes().contentEquals(original)) {
            "target DB must be untouched when the snapshot is refused"
        }
    }

    @Test
    fun importFrom_throws_when_folder_is_empty() = runTest {
        val source = tempFolder.newFolder("empty")
        val targetDb = tempFolder.newFile("target.db").apply { writeBytes(ByteArray(8)) }

        var threw = false
        try { newImporter(targetDb).importFrom(Uri.fromFile(source)) }
        catch (_: IllegalStateException) { threw = true }

        assert(threw) { "Expected IllegalStateException when no snapshot found" }
        assertEquals(8L, targetDb.length())
    }
}
