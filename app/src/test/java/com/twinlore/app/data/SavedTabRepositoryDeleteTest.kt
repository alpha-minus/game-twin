package com.twinlore.app.data

import android.net.Uri
import com.twinlore.app.data.library.LibraryFolderManager
import com.twinlore.app.data.library.ResourceResolver
import com.twinlore.app.data.local.ResourceFileDao
import com.twinlore.app.data.local.SavedTabDao
import com.twinlore.app.data.local.SavedTabEntity
import com.twinlore.app.data.local.VaultDao
import com.twinlore.app.data.local.VaultEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date

/**
 * Deleting a guide has to delete the guide, not just the row that mentions it.
 *
 * A tab is one of two things: a link to somewhere on the web, which owns no
 * file, or a file in the library folder that the tab is the only handle on.
 * For the second kind the row was all that got deleted, and the file stayed
 * where it was. Nothing noticed until the next rescan, which is exactly the
 * moment `LocalMediaBinder.ensureSavedTab` looks for a tab pointing at the
 * resource, finds none, and puts the guide back. The deleted guide returned
 * on the picker's Sync and on the reindex a rebuilt library runs.
 *
 * The group path already did this properly: `PageGroupRepository.deleteGroup`
 * takes the folder off disk and drops the `resource_files` rows with it. This
 * is the same contract for the single-file kinds.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SavedTabRepositoryDeleteTest {

    private val savedTabDao: SavedTabDao = mock()
    private val resourceFileDao: ResourceFileDao = mock()
    private val resourceResolver: ResourceResolver = mock()
    private val libraryFolderManager: LibraryFolderManager = mock()
    private val vaultDao: VaultDao = mock()
    private val pageGroupRepository: PageGroupRepository = mock()

    private val repo = SavedTabRepository(
        savedTabDao = savedTabDao,
        scrollMarkerDao = mock(),
        pageBookmarkDao = mock(),
        resourceFileDao = resourceFileDao,
        resourceResolver = resourceResolver,
        libraryFolderManager = libraryFolderManager,
        vaultDao = vaultDao,
        pageGroupRepository = pageGroupRepository,
        context = org.robolectric.RuntimeEnvironment.getApplication(),
    )

    private val fileUri: Uri = Uri.parse("file:///library/Chrono%20Trigger/manual.pdf")

    @Test
    fun `deleting a file-backed guide takes the file and its resource row with it`() = runTest {
        whenever(savedTabDao.getTabById("t1")).thenReturn(fileTab())
        whenever(resourceResolver.resolve("rf-1")).thenReturn(fileUri)
        whenever(libraryFolderManager.deleteFileUnderHome(any())).thenReturn(true)
        whenever(vaultDao.getVaultById("v1")).thenReturn(vault(guideResourceId = null))

        repo.deleteTab("t1")

        verify(libraryFolderManager).deleteFileUnderHome(eq(fileUri))
        verify(resourceFileDao).deleteById(eq("rf-1"))
        verify(savedTabDao).deleteTabById(eq("t1"))
    }

    /**
     * The vault's own guide slot points at `resource_files` rows, so a slot
     * left pointing at a row that no longer exists is a phantom binding: the
     * vault claims a guide it cannot open.
     */
    @Test
    fun `the vault's guide slot is cleared when it pointed at the deleted file`() = runTest {
        whenever(savedTabDao.getTabById("t1")).thenReturn(fileTab())
        whenever(resourceResolver.resolve("rf-1")).thenReturn(fileUri)
        whenever(libraryFolderManager.deleteFileUnderHome(any())).thenReturn(true)
        whenever(vaultDao.getVaultById("v1")).thenReturn(vault(guideResourceId = "rf-1"))

        repo.deleteTab("t1")

        verify(vaultDao).updateGuideResourceId(eq("v1"), eq(null))
    }

    /** A link owns no file, so there is nothing on disk to take with it. */
    @Test
    fun `deleting a link deletes nothing on disk`() = runTest {
        whenever(savedTabDao.getTabById("t2")).thenReturn(fileTab(id = "t2", resourceId = null))

        repo.deleteTab("t2")

        verify(libraryFolderManager, never()).deleteFileUnderHome(any())
        verify(resourceFileDao, never()).deleteById(any())
        verify(savedTabDao).deleteTabById(eq("t2"))
    }

    /** Groups already clean up after themselves; that path must not change. */
    @Test
    fun `a group still goes through the group repository`() = runTest {
        whenever(savedTabDao.getTabById("g1")).thenReturn(fileTab(id = "g1", fileType = "group"))

        repo.deleteTab("g1")

        verify(pageGroupRepository).deleteGroup(eq("g1"))
        verify(libraryFolderManager, never()).deleteFileUnderHome(any())
    }

    /**
     * The picker deletes through [SavedTabRepository.deleteBookmark], and that
     * path did reach for the file — with `DocumentFile.fromSingleUri(...).delete()`.
     * [ResourceResolver] hands back tree-document URIs, the shape
     * `DocumentsContract.buildDocumentUriUsingTree` produces, and DocumentFile
     * silently no-ops on those and returns a false that nothing reads. On a
     * SAF library folder the row went and the file stayed, which is the whole
     * bug: the next rescan finds a guide with no tab pointing at it and
     * registers it again.
     *
     * `LibraryFolderManager.deleteFileUnderHome` is the one place that gets this right,
     * using `DocumentsContract.deleteDocument` for content URIs. The same trap
     * is documented at `PageGroupSaveTargets.kt:95`.
     */
    @Test
    fun `deleting a bookmark deletes a SAF-backed file through the folder manager`() = runTest {
        val treeDocUri = Uri.parse(
            "content://com.android.externalstorage.documents/tree/primary%3ALibrary/document/primary%3ALibrary%2FChrono%20Trigger%2Fmanual.pdf"
        )
        whenever(savedTabDao.getTabById("t1")).thenReturn(fileTab())
        whenever(resourceResolver.resolve("rf-1")).thenReturn(treeDocUri)
        whenever(libraryFolderManager.deleteFileUnderHome(any())).thenReturn(true)
        whenever(vaultDao.getVaultById("v1")).thenReturn(vault(guideResourceId = null))

        repo.deleteBookmark("t1")

        verify(libraryFolderManager).deleteFileUnderHome(eq(treeDocUri))
        verify(resourceFileDao).deleteById(eq("rf-1"))
        verify(savedTabDao).deleteTabById(eq("t1"))
    }

    /** The picker's path owes the vault slot the same tidy-up as [deleteTab]. */
    @Test
    fun `deleting a bookmark clears the vault's guide slot`() = runTest {
        whenever(savedTabDao.getTabById("t1")).thenReturn(fileTab())
        whenever(resourceResolver.resolve("rf-1")).thenReturn(fileUri)
        whenever(libraryFolderManager.deleteFileUnderHome(any())).thenReturn(true)
        whenever(vaultDao.getVaultById("v1")).thenReturn(vault(guideResourceId = "rf-1"))

        repo.deleteBookmark("t1")

        verify(vaultDao).updateGuideResourceId(eq("v1"), eq(null))
    }

    /**
     * `sanitizeFilename` strips the same illegal character set as
     * `LibraryFolderManager.sanitizeForPath`, and shares its blind spot: "."
     * and ".." survive that strip and would name a directory reference
     * instead of a new guide file. This is that fix's own regression test,
     * plus proof a backslash separator can't smuggle a multi-segment path
     * through and an ordinary name survives untouched.
     */
    @Test
    fun `sanitizeFilename never returns a dot or dot-dot name`() {
        assertEquals("guide", repo.sanitizeFilename(".."))
        assertEquals("guide", repo.sanitizeFilename("."))
        assertEquals("guide", repo.sanitizeFilename(""))
    }

    @Test
    fun `sanitizeFilename drops a backslash separator instead of keeping a multi-segment name`() {
        assertEquals("a b.pdf", repo.sanitizeFilename("a\\b.pdf"))
    }

    @Test
    fun `sanitizeFilename leaves an ordinary name unchanged`() {
        assertEquals("Zelda Guide.pdf", repo.sanitizeFilename("Zelda Guide.pdf"))
    }

    /**
     * "Guides only" in the library's Delete multiple: every guide goes, through the
     * same per-guide delete as the picker, the map tab stays, and a guide file no
     * tab points at any more is removed too (the point is freeing space).
     */
    @Test
    fun `deleteAllGuidesForVault removes guides and stray guide files but keeps the map tab`() = runTest {
        val guide = fileTab(id = "t1", resourceId = "rf-1")
        val link = fileTab(id = "t2", resourceId = null, fileType = "html")
        val map = fileTab(id = "m1", resourceId = "rf-map").copy(isMap = true)
        whenever(savedTabDao.getTabsForVaultSync("v1"))
            .thenReturn(listOf(guide, link, map))
            .thenReturn(listOf(map))
        whenever(savedTabDao.getTabById("t1")).thenReturn(guide)
        whenever(savedTabDao.getTabById("t2")).thenReturn(link)
        val strayUri = Uri.parse("file:///library/Chrono%20Trigger/old.txt")
        whenever(resourceFileDao.getForVaultAndKindOnce("v1", "guide")).thenReturn(
            listOf(
                com.twinlore.app.data.local.ResourceFileEntity(
                    id = "rf-stray", vaultId = "v1", kind = "guide", filename = "old.txt", sizeBytes = 1,
                    homeRelativePath = "Chrono Trigger/old.txt", absolutePath = null, mimeType = null,
                    source = "USER_ADD", lastSeenAt = 0, createdAt = 0,
                )
            )
        )
        whenever(resourceResolver.resolve("rf-1")).thenReturn(fileUri)
        whenever(resourceResolver.resolve("rf-stray")).thenReturn(strayUri)
        whenever(libraryFolderManager.deleteFileUnderHome(any())).thenReturn(true)
        whenever(vaultDao.getVaultById("v1")).thenReturn(vault(guideResourceId = null))

        val removed = repo.deleteAllGuidesForVault("v1")

        assertEquals(2, removed)
        verify(savedTabDao).deleteTabById(eq("t1"))
        verify(savedTabDao).deleteTabById(eq("t2"))
        verify(savedTabDao, never()).deleteTabById(eq("m1"))
        verify(libraryFolderManager).deleteFileUnderHome(eq(fileUri))
        verify(libraryFolderManager).deleteFileUnderHome(eq(strayUri))
        verify(resourceFileDao).deleteById(eq("rf-stray"))
        verify(resourceFileDao, never()).deleteById(eq("rf-map"))
        verify(vaultDao, never()).deleteVaultById(any())
    }

    private fun fileTab(
        id: String = "t1",
        resourceId: String? = "rf-1",
        fileType: String = "pdf",
    ) = SavedTabEntity(
        id = id, vaultId = "v1", title = "manual", url = "",
        scrollPosition = 0, zoomLevel = 1f,
        isFavorite = false, isMap = false, isCurrentContent = false,
        order = 0, createdAt = Date(0), lastAccessed = Date(0),
        fileType = fileType, fileName = "manual.pdf",
        lastPdfPage = 0, pdfScrollOffset = 0f,
        resourceId = resourceId,
    )

    private fun vault(guideResourceId: String?) = VaultEntity(
        id = "v1", name = "Chrono Trigger", romPath = null, romFilename = null,
        artworkVibrantArgb = null, systemeid = null, gameId = null, source = null,
        raGameId = null, steamAppId = null,
        artworkResourceId = null, guideResourceId = guideResourceId, mapResourceId = null,
        launchIntentJson = null, lastOpened = null, createdAt = Date(0L),
    )
}
