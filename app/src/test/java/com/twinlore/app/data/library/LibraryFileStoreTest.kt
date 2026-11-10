package com.twinlore.app.data.library

import android.database.sqlite.SQLiteDatabase
import com.twinlore.app.data.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

@RunWith(RobolectricTestRunner::class)
class LibraryFileStoreTest {

    @get:Rule val tempFolder = TemporaryFolder()

    private lateinit var home: File
    private lateinit var settings: SettingsRepository
    private lateinit var folder: LibraryFolderManager
    private lateinit var store: LibraryFileStore

    private val deviceUuid = "a3f2c8e1-b4d5-4e6f-9a0b-1c2d3e4f5060"
    private val expectedName = "library.a3f2c8e1.gametwin"

    @Before
    fun setup() {
        home = tempFolder.newFolder("home")
        settings = mock()
        whenever(settings.homeFolderUri).thenReturn(MutableStateFlow("file://${home.absolutePath}"))
        whenever(settings.vaultStorageFolderUri).thenReturn(MutableStateFlow(null))
        folder = LibraryFolderManager(RuntimeEnvironment.getApplication(), settings)
        store = LibraryFileStore(RuntimeEnvironment.getApplication(), folder)
    }

    private fun writeFakeBare(deviceId: String) {
        val file = File(home, "library.gametwin")
        val db = SQLiteDatabase.openOrCreateDatabase(file, null)
        db.execSQL(
            "CREATE TABLE library_meta (id INTEGER PRIMARY KEY, schemaVersion INTEGER NOT NULL, " +
            "lastWrittenAtMillis INTEGER NOT NULL, lastWrittenDeviceId TEXT NOT NULL, " +
            "lastWrittenDeviceLabel TEXT NOT NULL)"
        )
        db.execSQL(
            "INSERT INTO library_meta VALUES (1, 24, 1, ?, '')",
            arrayOf<Any>(deviceId)
        )
        db.close()
    }

    @Test
    fun shortDeviceId_strips_dashes_and_lowercases_first_8() {
        assertEquals("a3f2c8e1",
            LibraryFileStore.shortDeviceId("A3F2C8E1-b4d5-4e6f-9a0b-1c2d3e4f5060"))
        assertEquals("", LibraryFileStore.shortDeviceId(""))
    }

    @Test
    fun fileName_per_device_or_bare_fallback() {
        assertEquals(expectedName, LibraryFileStore.fileName(deviceUuid))
        assertEquals("library.gametwin", LibraryFileStore.fileName(""))
    }

    @Test
    fun isPerDeviceSnapshotName_strict_pattern() {
        assertTrue(LibraryFileStore.isPerDeviceSnapshotName(expectedName))
        assertFalse(LibraryFileStore.isPerDeviceSnapshotName("library.gametwin"))
        assertFalse(LibraryFileStore.isPerDeviceSnapshotName("library.abc.gametwin"))
        assertFalse(LibraryFileStore.isPerDeviceSnapshotName("library.A3F2C8E1.gametwin"))
        assertFalse(LibraryFileStore.isPerDeviceSnapshotName("library.a3f2c8e1.gametwin.partial"))
    }

    @Test
    fun save_writes_per_device_filename() = runTest {
        val source = tempFolder.newFile("vault_database.db").apply {
            writeBytes(ByteArray(42) { 0x77 })
        }
        val outcome = store.save(source, deviceUuid)

        assertNotNull(outcome)
        assertEquals("file", outcome!!.fileUri.scheme)
        val written = File(home, expectedName)
        assertTrue(written.isFile)
        assertArrayEquals(ByteArray(42) { 0x77 }, written.readBytes())
    }

    @Test
    fun save_overwrites_existing_same_device_file() = runTest {
        val source1 = tempFolder.newFile("db1.db").apply { writeBytes(ByteArray(8) { 0xAA.toByte() }) }
        val source2 = tempFolder.newFile("db2.db").apply { writeBytes(ByteArray(16) { 0xBB.toByte() }) }
        store.save(source1, deviceUuid)
        store.save(source2, deviceUuid)
        assertEquals(16L, File(home, expectedName).length())
    }

    @Test
    fun save_leaves_other_device_file_alone() = runTest {
        File(home, "library.bbbbbbbb.gametwin").writeBytes(ByteArray(99) { 0xCC.toByte() })
        val source = tempFolder.newFile("db.db").apply { writeBytes(ByteArray(16)) }
        store.save(source, deviceUuid)
        assertTrue(File(home, expectedName).isFile)
        assertEquals(99L, File(home, "library.bbbbbbbb.gametwin").length())
    }

    @Test
    fun save_promotes_bare_to_per_device_when_it_was_ours() = runTest {
        writeFakeBare(deviceUuid)
        val source = tempFolder.newFile("db.db").apply { writeBytes(ByteArray(16) { 0x77 }) }
        store.save(source, deviceUuid)
        assertFalse("bare file moved away", File(home, "library.gametwin").exists())
        val target = File(home, expectedName)
        assertTrue(target.isFile)
        assertEquals("contents are the latest save, not the renamed bare", 16L, target.length())
    }

    @Test
    fun save_does_not_promote_bare_when_per_device_already_exists() = runTest {
        writeFakeBare(deviceUuid)
        File(home, expectedName).writeBytes(ByteArray(8) { 0xAA.toByte() })
        val source = tempFolder.newFile("db.db").apply { writeBytes(ByteArray(16) { 0x77 }) }
        store.save(source, deviceUuid)
        assertFalse("bare deleted as redundant", File(home, "library.gametwin").exists())
        assertEquals("per-device overwritten by save", 16L, File(home, expectedName).length())
    }

    @Test
    fun save_leaves_bare_alone_when_it_belongs_to_another_device() = runTest {
        writeFakeBare("other-uuid")
        val source = tempFolder.newFile("db.db").apply { writeBytes(ByteArray(16)) }
        store.save(source, deviceUuid)
        assertTrue(File(home, "library.gametwin").isFile)
        assertTrue(File(home, expectedName).isFile)
    }

    @Test
    fun save_returns_null_when_home_folder_unset() = runTest {
        whenever(settings.homeFolderUri).thenReturn(MutableStateFlow(null))
        val freshFolder = LibraryFolderManager(RuntimeEnvironment.getApplication(), settings)
        val freshStore = LibraryFileStore(RuntimeEnvironment.getApplication(), freshFolder)
        val source = tempFolder.newFile("db.db").apply { writeBytes(ByteArray(8)) }
        assertNull(freshStore.save(source, deviceUuid))
    }

    @Test
    fun deleteIfPresent_targets_per_device_file_only() = runTest {
        File(home, expectedName).writeBytes(ByteArray(8))
        File(home, "library.bbbbbbbb.gametwin").writeBytes(ByteArray(8))
        File(home, "library.gametwin").writeBytes(ByteArray(8))

        assertTrue(store.deleteIfPresent(deviceUuid))

        assertFalse(File(home, expectedName).exists())
        assertTrue(File(home, "library.bbbbbbbb.gametwin").isFile)
        assertTrue(File(home, "library.gametwin").isFile)
    }

    @Test
    fun deleteIfPresent_returns_false_when_our_file_missing() = runTest {
        File(home, "library.bbbbbbbb.gametwin").writeBytes(ByteArray(8))
        assertFalse(store.deleteIfPresent(deviceUuid))
    }
}
