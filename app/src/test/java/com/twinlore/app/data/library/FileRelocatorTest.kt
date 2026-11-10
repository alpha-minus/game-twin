package com.twinlore.app.data.library

import com.twinlore.app.data.SettingsRepository
import com.twinlore.app.data.local.ResourceFileDao
import com.twinlore.app.data.local.ResourceFileEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

@RunWith(RobolectricTestRunner::class)
class FileRelocatorTest {

    @get:Rule val tempFolder = TemporaryFolder()

    private lateinit var home: File
    private lateinit var resourceDao: ResourceFileDao
    private lateinit var folder: LibraryFolderManager
    private lateinit var relocator: FileRelocator

    @Before
    fun setup() {
        home = tempFolder.newFolder("home")
        val settings: SettingsRepository = mock()
        whenever(settings.homeFolderUri).thenReturn(MutableStateFlow("file://${home.absolutePath}"))
        whenever(settings.vaultStorageFolderUri).thenReturn(MutableStateFlow(null))
        folder = LibraryFolderManager(RuntimeEnvironment.getApplication(), settings)
        resourceDao = mock()
        relocator = FileRelocator(resourceDao, folder)
    }

    private fun rowFor(absolutePath: String, kind: String = "artwork"): ResourceFileEntity =
        ResourceFileEntity(
            id = "row-1",
            vaultId = "vault-1",
            kind = kind,
            filename = File(absolutePath).name,
            sizeBytes = File(absolutePath).length(),
            homeRelativePath = null,
            absolutePath = absolutePath,
            mimeType = null,
            source = "migrated",
            lastSeenAt = 0L,
            createdAt = 0L
        )

    @Test
    fun relocate_copies_file_updates_row_deletes_source() = runTest {
        val legacy = tempFolder.newFolder("legacy")
        val src = File(legacy, "artwork.png").apply { writeBytes(ByteArray(42) { 0x77 }) }
        val row = rowFor(src.absolutePath)

        val ok = relocator.relocate(row, "MyVault", "artwork")

        assertTrue("relocate returned true", ok)
        val dest = File(home, "MyVault/artwork/artwork.png")
        assertTrue("destination exists", dest.isFile)
        assertEquals(42L, dest.length())
        assertArrayEquals(ByteArray(42) { 0x77 }, dest.readBytes())
        assertFalse("source deleted", src.exists())

        val captor = argumentCaptor<ResourceFileEntity>()
        verify(resourceDao).update(captor.capture())
        assertEquals("MyVault/artwork/artwork.png", captor.firstValue.homeRelativePath)
        assertNull("absolutePath cleared", captor.firstValue.absolutePath)
    }

    @Test
    fun relocate_returns_false_when_source_missing() = runTest {
        val row = rowFor("/does/not/exist.png")
        val ok = relocator.relocate(row, "MyVault", "art")
        assertFalse(ok)
    }

    @Test
    fun relocate_handles_nested_guide_subdir() = runTest {
        val legacy = tempFolder.newFolder("legacy")
        val src = File(legacy, "guide.mht").apply { writeBytes(ByteArray(7)) }
        val row = rowFor(src.absolutePath, kind = "guide")

        val ok = relocator.relocate(row, "Halo  Combat Evolved", "guide/bm-abc")

        assertTrue(ok)
        assertTrue(File(home, "Halo  Combat Evolved/guide/bm-abc/guide.mht").isFile)
    }
}
