package com.twinlore.app.data.library

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.twinlore.app.data.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.whenever
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

@RunWith(RobolectricTestRunner::class)
class LibraryFolderManagerTest {

    @get:Rule val tempFolder = TemporaryFolder()

    private lateinit var ctx: Context
    private lateinit var settings: SettingsRepository
    private lateinit var fm: LibraryFolderManager

    @Before
    fun setup() {
        ctx = RuntimeEnvironment.getApplication()
        settings = mock()
        whenever(settings.homeFolderUri).thenReturn(MutableStateFlow("file://${tempFolder.root.absolutePath}"))
        whenever(settings.vaultStorageFolderUri).thenReturn(MutableStateFlow(null))
        fm = LibraryFolderManager(ctx, settings)
    }

    @Test
    fun findByNameAndSizeInVault_returns_match_when_name_and_size_align() = runTest {
        val sub = tempFolder.newFolder("Zelda OoT")
        val target = File(sub, "guide.mht").apply { writeBytes(ByteArray(123) { it.toByte() }) }

        val hit = fm.findByNameAndSizeInVault("Zelda OoT", "guide.mht", 123L)

        assertEquals(target.absolutePath, hit?.absolutePath)
        assertEquals("Zelda OoT/guide.mht", hit?.relativePath)
    }

    @Test
    fun findByNameAndSizeInVault_null_when_size_mismatches() = runTest {
        val sub = tempFolder.newFolder("Mario 64")
        File(sub, "map.png").apply { writeBytes(ByteArray(50)) }

        val hit = fm.findByNameAndSizeInVault("Mario 64", "map.png", 100L)

        assertNull(hit)
    }

    @Test
    fun findByNameAndSizeInVault_recurses_within_vault_dir() = runTest {
        val deep = tempFolder.newFolder("Mario 64", "sub", "deeper")
        val target = File(deep, "deep.bin").apply { writeBytes(ByteArray(7)) }

        val hit = fm.findByNameAndSizeInVault("Mario 64", "deep.bin", 7L)

        assertEquals(target.absolutePath, hit?.absolutePath)
        assertEquals("Mario 64/sub/deeper/deep.bin", hit?.relativePath)
    }

    @Test
    fun findByNameAndSizeInVault_does_not_match_other_vault_dirs() = runTest {
        val a = tempFolder.newFolder("Vault A")
        val b = tempFolder.newFolder("Vault B")
        File(a, "cover.jpg").apply { writeBytes(ByteArray(99)) }
        val targetB = File(b, "cover.jpg").apply { writeBytes(ByteArray(99)) }

        val hitFromB = fm.findByNameAndSizeInVault("Vault B", "cover.jpg", 99L)

        assertEquals(targetB.absolutePath, hitFromB?.absolutePath)
        assertEquals("Vault B/cover.jpg", hitFromB?.relativePath)
    }

    @Test
    fun findByNameAndSizeInVault_null_when_vault_dir_missing() = runTest {
        val hit = fm.findByNameAndSizeInVault("DoesNotExist", "anything.txt", 0L)
        assertNull(hit)
    }

    @Test
    fun findByNameAndSizeInVault_null_when_home_folder_unset() = runTest {
        whenever(settings.homeFolderUri).thenReturn(MutableStateFlow(null))
        whenever(settings.vaultStorageFolderUri).thenReturn(MutableStateFlow(null))
        val freshFm = LibraryFolderManager(ctx, settings)

        assertNull(freshFm.findByNameAndSizeInVault("AnyVault", "anything.txt", 0L))
    }

    @Test
    fun deleteFileUnderHome_deletes_a_regular_file_inside_home() = runTest {
        val f = File(tempFolder.newFolder("Zelda"), "guide.mht").apply { writeText("x") }

        assertTrue(fm.deleteFileUnderHome(Uri.fromFile(f)))
        assertFalse(f.exists())
    }

    @Test
    fun deleteFileUnderHome_refuses_a_directory_inside_home() = runTest {
        val dir = tempFolder.newFolder("Zelda")
        File(dir, "keep.txt").writeText("x")

        assertFalse(fm.deleteFileUnderHome(Uri.fromFile(dir)))
        assertTrue(File(dir, "keep.txt").exists())
    }

    @Test
    fun deleteFileUnderHome_refuses_a_file_outside_home() = runTest {
        val outside = File.createTempFile("outside", ".txt")
        try {
            assertFalse(fm.deleteFileUnderHome(Uri.fromFile(outside)))
            assertTrue(outside.exists())
        } finally {
            outside.delete()
        }
    }

    @Test
    fun deleteFileUnderHome_refuses_dot_dot_traversal() = runTest {
        val sibling = File(tempFolder.root.parentFile, "escape-${System.nanoTime()}.txt").apply { writeText("x") }
        try {
            val traversal = Uri.parse("file://${tempFolder.root.absolutePath}/../${sibling.name}")
            assertFalse(fm.deleteFileUnderHome(traversal))
            assertTrue(sibling.exists())
        } finally {
            sibling.delete()
        }
    }

    /**
     * The app's normal configuration: a SAF (`content://`) home folder plus All
     * Files Access, which makes [ResourceResolver]'s fast path hand
     * [LibraryFolderManager.deleteFileUnderHome] a `file://` URI derived from
     * [LibraryFolderManager.homeFolderFsRoot]. Before the mixed-scheme arm
     * existed, that pair fell through to `else -> false` and every saved-guide
     * delete was refused: the row and the tab went away, the file stayed, and
     * the next rescan re-registered the guide.
     */
    @Test
    fun deleteFileUnderHome_deletes_a_file_uri_under_a_saf_home_when_the_fs_root_resolves() = runTest {
        whenever(settings.homeFolderUri).thenReturn(
            MutableStateFlow("content://com.android.externalstorage.documents/tree/primary%3AGameTwin")
        )
        val spyFm = spy(LibraryFolderManager(ctx, settings))
        doReturn(tempFolder.root).whenever(spyFm).homeFolderFsRoot()
        val f = File(tempFolder.newFolder("Zelda"), "guide.mht").apply { writeText("x") }

        assertTrue(spyFm.deleteFileUnderHome(Uri.fromFile(f)))
        assertFalse(f.exists())
    }

    /** The same mixed pair must still refuse a target outside the resolved root. */
    @Test
    fun deleteFileUnderHome_refuses_a_file_uri_outside_a_saf_homes_fs_root() = runTest {
        whenever(settings.homeFolderUri).thenReturn(
            MutableStateFlow("content://com.android.externalstorage.documents/tree/primary%3AGameTwin")
        )
        val spyFm = spy(LibraryFolderManager(ctx, settings))
        doReturn(tempFolder.root).whenever(spyFm).homeFolderFsRoot()
        val outside = File.createTempFile("outside", ".txt")
        try {
            assertFalse(spyFm.deleteFileUnderHome(Uri.fromFile(outside)))
            assertTrue(outside.exists())
        } finally {
            outside.delete()
        }
    }

    @Test
    fun isFileUnderHome_refuses_the_saf_tree_root_and_other_trees() {
        val home = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3AGameTwin")
        val root = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3AGameTwin/document/primary%3AGameTwin")
        val otherTree = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3AGameTwin/document/primary%3AGameTwin2%2Fguide.mht")

        assertFalse(fm.isFileUnderHome(root, home))
        assertFalse(fm.isFileUnderHome(otherTree, home))
    }

    /**
     * The document-id containment check on [root] and [otherTree] above both
     * short-circuit the `&&` in [LibraryFolderManager.isFileUnderHome] before
     * the SAF `isDirectory` conjunct is ever evaluated: `root`'s doc id equals
     * the home doc id, and `otherTree`'s doc id fails `startsWith("$homeDocId/")`.
     * So neither proves the directory guard does anything. This test reaches
     * a doc id that passes both id checks, with the registered mime type
     * proving the guard: it accepts a plain nested file.
     */
    @Test
    fun isFileUnderHome_accepts_a_regular_file_nested_in_the_saf_tree() {
        FakeExternalStorageDocumentsProvider.mimeTypesByDocId.clear()
        FakeExternalStorageDocumentsProvider.mimeTypesByDocId["primary:GameTwin/guide.mht"] = "text/html"
        Robolectric.buildContentProvider(FakeExternalStorageDocumentsProvider::class.java)
            .create(FakeExternalStorageDocumentsProvider.AUTHORITY)

        val home = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3AGameTwin")
        val child = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3AGameTwin/document/primary%3AGameTwin%2Fguide.mht")

        assertTrue(fm.isFileUnderHome(child, home))
    }

    /**
     * Same doc-id shape as the accept case above -- same home, a doc id that
     * passes both id checks -- but the stub reports it as a directory. This is
     * the one assertion that would fail today if the `isDirectory` conjunct
     * were removed from [LibraryFolderManager.isFileUnderHome]: without it,
     * the id checks alone would let this SAF directory through, and
     * `deleteAt` would hand it to `DocumentsContract.deleteDocument`, which
     * removes a directory and everything inside it.
     */
    @Test
    fun isFileUnderHome_refuses_a_directory_nested_in_the_saf_tree() {
        FakeExternalStorageDocumentsProvider.mimeTypesByDocId.clear()
        FakeExternalStorageDocumentsProvider.mimeTypesByDocId["primary:GameTwin/Subfolder"] =
            DocumentsContract.Document.MIME_TYPE_DIR
        Robolectric.buildContentProvider(FakeExternalStorageDocumentsProvider::class.java)
            .create(FakeExternalStorageDocumentsProvider.AUTHORITY)

        val home = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3AGameTwin")
        val childDir = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3AGameTwin/document/primary%3AGameTwin%2FSubfolder")

        assertFalse(fm.isFileUnderHome(childDir, home))
    }

    @Test
    fun sanitizeForPath_never_yields_dot_or_dot_dot() {
        assertEquals("Unnamed", LibraryFolderManager.sanitizeForPath(".."))
        assertEquals("Unnamed", LibraryFolderManager.sanitizeForPath("."))
        assertEquals("Unnamed", LibraryFolderManager.sanitizeForPath("   "))
        assertEquals("Zelda OoT", LibraryFolderManager.sanitizeForPath("Zelda: OoT").trim())
    }
}
