package com.twinlore.app.data.library

import com.twinlore.app.data.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
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
class LibraryFolderManagerCopyTest {

    @get:Rule val tempFolder = TemporaryFolder()

    private lateinit var home: File
    private lateinit var settings: SettingsRepository
    private lateinit var folder: LibraryFolderManager

    @Before
    fun setup() {
        home = tempFolder.newFolder("home")
        settings = mock()
        whenever(settings.homeFolderUri).thenReturn(MutableStateFlow("file://${home.absolutePath}"))
        whenever(settings.vaultStorageFolderUri).thenReturn(MutableStateFlow(null))
        folder = LibraryFolderManager(RuntimeEnvironment.getApplication(), settings)
    }

    @Test
    fun copyFileIntoTree_creates_subdir_and_writes_bytes() = runTest {
        val src = tempFolder.newFile("source.bin").apply { writeBytes(ByteArray(64) { 0x55 }) }

        val resultUri = folder.copyFileIntoTree(src, "MyVault/art", "artwork.png")

        assertNotNull(resultUri)
        val dest = File(home, "MyVault/art/artwork.png")
        assertTrue("destination exists", dest.isFile)
        assertEquals(64L, dest.length())
        assertArrayEquals(ByteArray(64) { 0x55 }, dest.readBytes())
    }

    @Test
    fun copyFileIntoTree_overwrites_existing_file() = runTest {
        val target = File(home, "MyVault/art/artwork.png").apply {
            parentFile?.mkdirs()
            writeBytes(ByteArray(16) { 0xAA.toByte() })
        }
        val src = tempFolder.newFile("source.bin").apply { writeBytes(ByteArray(32) { 0x77 }) }

        folder.copyFileIntoTree(src, "MyVault/art", "artwork.png")

        assertEquals(32L, target.length())
        assertArrayEquals(ByteArray(32) { 0x77 }, target.readBytes())
    }

    @Test
    fun copyFileIntoTree_sweeps_orphan_partial_files() = runTest {
        val targetDir = File(home, "MyVault/art").apply { mkdirs() }
        File(targetDir, "artwork.png.partial").writeBytes(ByteArray(99))
        val src = tempFolder.newFile("source.bin").apply { writeBytes(ByteArray(8)) }

        folder.copyFileIntoTree(src, "MyVault/art", "artwork.png")

        val partials = targetDir.listFiles { _, name -> name.endsWith(".partial") } ?: emptyArray()
        assertEquals("orphan .partial swept", 0, partials.size)
    }

    @Test
    fun copyFileIntoTree_only_sweeps_matching_partial_filename() = runTest {
        val targetDir = File(home, "MyVault/art").apply { mkdirs() }
        File(targetDir, "artwork.png.partial").writeBytes(ByteArray(99))
        File(targetDir, "other.png.partial").writeBytes(ByteArray(50))
        val src = tempFolder.newFile("source.bin").apply { writeBytes(ByteArray(8)) }

        folder.copyFileIntoTree(src, "MyVault/art", "artwork.png")

        val partials = targetDir.listFiles { _, name -> name.endsWith(".partial") } ?: emptyArray()
        assertEquals("unrelated .partial preserved", 1, partials.size)
        assertEquals("other.png.partial", partials[0].name)
    }

    @Test
    fun copyFileIntoTree_returns_null_when_home_unset() = runTest {
        whenever(settings.homeFolderUri).thenReturn(MutableStateFlow(null))
        val freshFolder = LibraryFolderManager(RuntimeEnvironment.getApplication(), settings)
        val src = tempFolder.newFile("source.bin").apply { writeBytes(ByteArray(8)) }

        assertNull(freshFolder.copyFileIntoTree(src, "MyVault/art", "artwork.png"))
    }

    @Test
    fun copyFileIntoTree_creates_nested_subdirs() = runTest {
        val src = tempFolder.newFile("source.bin").apply { writeBytes(ByteArray(4) { 0x11 }) }

        folder.copyFileIntoTree(src, "MyVault/guide/bookmark-abc", "guide.mht")

        val dest = File(home, "MyVault/guide/bookmark-abc/guide.mht")
        assertTrue(dest.isFile)
        assertEquals(4L, dest.length())
    }
}
