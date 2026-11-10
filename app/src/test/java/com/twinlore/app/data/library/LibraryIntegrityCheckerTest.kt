package com.twinlore.app.data.library

import android.net.Uri
import com.twinlore.app.data.local.MediaSearchIndexDao
import com.twinlore.app.data.local.MediaSearchIndexEntity
import com.twinlore.app.data.local.ResourceFileDao
import com.twinlore.app.data.local.ResourceFileEntity
import com.twinlore.app.data.local.SavedMapDao
import com.twinlore.app.data.local.SavedTabDao
import com.twinlore.app.data.local.VaultDao
import com.twinlore.app.data.local.VaultEntity
import com.twinlore.app.domain.LibraryIntegrityState
import com.twinlore.app.domain.ResourceSource
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import java.util.Date

/**
 * Robolectric is needed because [Uri] is part of the Android framework — both the
 * tests' homeFolderUri stubs and the checker's null-guard depend on it.
 */
@RunWith(RobolectricTestRunner::class)
class LibraryIntegrityCheckerTest {

    private val resourceFileDao: ResourceFileDao = mock()
    private val vaultDao: VaultDao = mock()
    private val savedTabDao: SavedTabDao = mock()
    private val savedMapDao: SavedMapDao = mock()
    private val resolver: ResourceResolver = mock()
    private val folder: LibraryFolderManager = mock()
    private val mediaSearchIndexDao: MediaSearchIndexDao = mock()
    private val artworkThumbnailCache: ArtworkThumbnailCache = mock()
    private val mapThumbnailCache: MapThumbnailCache = mock()
    private val uriProbe: com.twinlore.app.data.UriProbe = mock()

    private fun checker() = LibraryIntegrityChecker(
        resourceFileDao = resourceFileDao,
        vaultDao = vaultDao,
        savedTabDao = savedTabDao,
        savedMapDao = savedMapDao,
        resolver = resolver,
        libraryFolderManager = folder,
        mediaSearchIndexDao = mediaSearchIndexDao,
        artworkThumbnailCache = artworkThumbnailCache,
        mapThumbnailCache = mapThumbnailCache,
        uriProbe = uriProbe
    )

    private fun row(
        id: String,
        vaultId: String?,
        filename: String = "$id.bin"
    ) = ResourceFileEntity(
        id = id,
        vaultId = vaultId,
        kind = "artwork",
        filename = filename,
        sizeBytes = 1L,
        homeRelativePath = filename,
        absolutePath = null,
        mimeType = null,
        source = "USER_ADD",
        lastSeenAt = 0L,
        createdAt = 0L
    )

    private fun vault(id: String, name: String, artworkResourceId: String? = null) = VaultEntity(
        id = id,
        name = name,
        romPath = null,
        romFilename = null,
        artworkVibrantArgb = null,
        systemeid = null,
        gameId = null,
        source = null,
        raGameId = null,
        steamAppId = null,
        artworkResourceId = artworkResourceId,
        guideResourceId = null,
        mapResourceId = null,
        launchIntentJson = null,
        lastOpened = null,
        createdAt = Date(0L)
    )

    // By default, the index has no entry for any (filename, size) pair.
    private suspend fun noIndexHits() {
        whenever(mediaSearchIndexDao.findByFilenameAndSize(any(), any())).thenReturn(null)
    }

    // --- Rows blocked by a revoked SAF grant are unreadable, not missing ---

    /** A row backed by a document URI under a tree the app no longer holds. */
    private fun cocoonRow(id: String, vaultId: String?) = row(id, vaultId).copy(
        homeRelativePath = null,
        absolutePath = "content://com.android.externalstorage.documents/" +
            "tree/primary%3ACocoon/document/primary%3ACocoon%2Fart%2F$id.png"
    )

    @Test
    fun missingCountsByVault_counts_gone_files_per_vault_and_leaves_the_banner_state_alone() = runTest {
        whenever(folder.homeFolderUri()).thenReturn(Uri.parse("file:///x"))
        whenever(resourceFileDao.getAllOnce()).thenReturn(
            listOf(row("a", "v1"), row("b", "v1"), row("c", "v2"), row("d", "v3"), cocoonRow("e", "v4"))
        )
        val present = mock<Uri>()
        whenever(resolver.resolve(any())).thenReturn(null)
        whenever(resolver.resolve("c")).thenReturn(present)
        noIndexHits()
        // v3's file is relinkable through the media index; v4's is only grant-blocked.
        whenever(mediaSearchIndexDao.findByFilenameAndSize(eq("d.bin"), any())).thenReturn(
            MediaSearchIndexEntity(
                id = "i", searchPathId = "p", absolutePath = "/x/d.bin", relativePath = null,
                filename = "d.bin", normalizedBasename = "d", normalizedPathTokens = "x d bin",
                extLower = "bin", sizeBytes = 1L, mtime = 0L
            )
        )
        whenever(uriProbe.hasCoveringPersistedPermission(any())).thenReturn(false)
        whenever(vaultDao.getVaultById(any())).thenReturn(vault("v1", "Zelda"))

        val c = checker()
        val counts = c.missingCountsByVault()

        assertEquals(mapOf("v1" to 2), counts)
        assertEquals(LibraryIntegrityState.Idle, c.state.value)
    }

    @Test
    fun rows_whose_grant_was_revoked_are_reported_unreadable_not_missing() = runTest {
        // A reinstall drops every persisted URI grant, so files that are perfectly
        // present on disk stop resolving. Reporting those as "missing" invites the
        // user to delete links to files that were never gone.
        whenever(folder.homeFolderUri()).thenReturn(Uri.parse("file:///x"))
        whenever(resourceFileDao.getAllOnce()).thenReturn(listOf(cocoonRow("a", "v1")))
        whenever(resolver.resolve(any())).thenReturn(null)
        noIndexHits()
        whenever(uriProbe.hasCoveringPersistedPermission(any())).thenReturn(false)
        whenever(vaultDao.getVaultById("v1")).thenReturn(vault("v1", "Zelda"))

        val c = checker()
        c.runCheck()

        val report = (c.state.value as LibraryIntegrityState.Issues).report
        assertEquals(0, report.totalMissing)
        assertTrue(report.perVault.isEmpty())
        assertEquals(listOf("a"), report.unreadable.map { it.id })
    }

    @Test
    fun rows_are_still_missing_when_the_grant_is_held_but_the_file_is_gone() = runTest {
        // The grant is intact, so a failure to resolve really does mean the file
        // went away — this is the case cleanup exists for.
        whenever(folder.homeFolderUri()).thenReturn(Uri.parse("file:///x"))
        whenever(resourceFileDao.getAllOnce()).thenReturn(listOf(cocoonRow("a", "v1")))
        whenever(resolver.resolve(any())).thenReturn(null)
        noIndexHits()
        whenever(uriProbe.hasCoveringPersistedPermission(any())).thenReturn(true)
        whenever(vaultDao.getVaultById("v1")).thenReturn(vault("v1", "Zelda"))

        val c = checker()
        c.runCheck()

        val report = (c.state.value as LibraryIntegrityState.Issues).report
        assertEquals(1, report.totalMissing)
        assertTrue(report.unreadable.isEmpty())
    }

    @Test
    fun applyRemoval_never_deletes_an_unreadable_row() = runTest {
        whenever(folder.homeFolderUri()).thenReturn(Uri.parse("file:///x"))
        whenever(resourceFileDao.getAllOnce())
            .thenReturn(listOf(cocoonRow("blocked", "v1"), row("gone", "v1")))
        whenever(resolver.resolve(any())).thenReturn(null)
        noIndexHits()
        whenever(uriProbe.hasCoveringPersistedPermission(any())).thenReturn(false)
        whenever(vaultDao.getVaultById("v1")).thenReturn(vault("v1", "Zelda"))

        val c = checker()
        c.runCheck()
        c.applyRemoval((c.state.value as LibraryIntegrityState.Issues).report)

        verify(resourceFileDao, never()).deleteById("blocked")
        verify(resourceFileDao).deleteById("gone")
    }

    @Test
    fun a_vault_is_not_cascade_deleted_when_its_only_other_row_is_merely_unreadable() = runTest {
        // willBeDeleted compares missing.size against the vault's total row count.
        // An unreadable row must still count towards that total, or a vault with one
        // dead file and one temporarily-unreadable file looks entirely dead.
        whenever(folder.homeFolderUri()).thenReturn(Uri.parse("file:///x"))
        whenever(resourceFileDao.getAllOnce())
            .thenReturn(listOf(cocoonRow("blocked", "v1"), row("gone", "v1")))
        whenever(resolver.resolve(any())).thenReturn(null)
        noIndexHits()
        whenever(uriProbe.hasCoveringPersistedPermission(any())).thenReturn(false)
        whenever(vaultDao.getVaultById("v1")).thenReturn(vault("v1", "Zelda"))

        val c = checker()
        c.runCheck()
        val group = (c.state.value as LibraryIntegrityState.Issues).report.perVault.single()

        assertEquals(2, group.totalResourceCount)
        assertFalse(group.willBeDeleted)
        c.applyRemoval((c.state.value as LibraryIntegrityState.Issues).report)
        verify(vaultDao, never()).deleteVaultById("v1")
    }

    // --- The audit must not run its SAF walk on the caller's thread ---

    @Test
    fun runCheck_does_its_scanning_off_the_calling_thread() {
        // runIntegrityCheck() launches on viewModelScope (Dispatchers.Main.immediate),
        // and resolve() walks SAF synchronously. Without an IO hop inside runCheck that
        // walk lands on the main thread and freezes the UI for the whole rescan.
        val single = java.util.concurrent.Executors.newSingleThreadExecutor()
        try {
            val dispatcher = single.asCoroutineDispatcher()
            val seen = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
            kotlinx.coroutines.runBlocking {
                whenever(folder.homeFolderUri()).thenReturn(Uri.parse("file:///x"))
                whenever(resourceFileDao.getAllOnce()).thenReturn(listOf(row("a", "v1")))
                whenever(resolver.resolve(any())).then {
                    seen += Thread.currentThread().name
                    Uri.parse("file:///x/a")
                }
                val callerThread = withContext(dispatcher) { Thread.currentThread().name }
                withContext(dispatcher) { checker().runCheck() }
                assertFalse(
                    "resolve() ran on the calling thread ($callerThread)",
                    seen.contains(callerThread)
                )
            }
        } finally {
            single.shutdown()
        }
    }

    @Test
    fun state_is_Healthy_when_home_folder_is_unset() = runTest {
        whenever(folder.homeFolderUri()).thenReturn(null)
        val c = checker()
        c.runCheck()
        assertEquals(LibraryIntegrityState.Healthy, c.state.value)
    }

    @Test
    fun state_is_Healthy_when_no_rows_are_missing() = runTest {
        whenever(folder.homeFolderUri()).thenReturn(Uri.parse("file:///x"))
        whenever(resourceFileDao.getAllOnce()).thenReturn(listOf(row("a", "v1"), row("b", "v1")))
        whenever(resolver.resolve(any())).thenReturn(Uri.parse("file:///x/a"))
        val c = checker()
        c.runCheck()
        assertEquals(LibraryIntegrityState.Healthy, c.state.value)
    }

    @Test
    fun vault_with_all_files_missing_is_flagged_willBeDeleted() = runTest {
        whenever(folder.homeFolderUri()).thenReturn(Uri.parse("file:///x"))
        val rows = listOf(row("a", "v1"), row("b", "v1"))
        whenever(resourceFileDao.getAllOnce()).thenReturn(rows)
        whenever(resolver.resolve(any())).thenReturn(null)
        noIndexHits()
        whenever(vaultDao.getVaultById("v1")).thenReturn(vault("v1", "Mario 64"))
        val c = checker()
        c.runCheck()
        val state = c.state.value as LibraryIntegrityState.Issues
        assertEquals(1, state.report.perVault.size)
        assertTrue(state.report.perVault[0].willBeDeleted)
        assertEquals(2, state.report.totalMissing)
    }

    @Test
    fun vault_with_partial_misses_keeps_shell() = runTest {
        whenever(folder.homeFolderUri()).thenReturn(Uri.parse("file:///x"))
        val rows = listOf(row("a", "v1"), row("b", "v1"), row("c", "v1"))
        whenever(resourceFileDao.getAllOnce()).thenReturn(rows)
        whenever(resolver.resolve("a")).thenReturn(Uri.parse("file:///x/a"))
        whenever(resolver.resolve("b")).thenReturn(null)
        whenever(resolver.resolve("c")).thenReturn(null)
        noIndexHits()
        whenever(vaultDao.getVaultById("v1")).thenReturn(vault("v1", "Zelda"))
        val c = checker()
        c.runCheck()
        val group = (c.state.value as LibraryIntegrityState.Issues).report.perVault.single()
        assertEquals(2, group.missing.size)
        assertEquals(3, group.totalResourceCount)
        assertFalse(group.willBeDeleted)
    }

    @Test
    fun orphan_rows_are_bucketed_separately() = runTest {
        whenever(folder.homeFolderUri()).thenReturn(Uri.parse("file:///x"))
        val rows = listOf(row("a", null), row("b", null))
        whenever(resourceFileDao.getAllOnce()).thenReturn(rows)
        whenever(resolver.resolve(any())).thenReturn(null)
        noIndexHits()
        val c = checker()
        c.runCheck()
        val report = (c.state.value as LibraryIntegrityState.Issues).report
        assertTrue(report.perVault.isEmpty())
        assertEquals(2, report.orphans.size)
        assertEquals(2, report.totalMissing)
    }

    @Test
    fun applyRemoval_cascade_deletes_a_fully_missing_vault() = runTest {
        whenever(folder.homeFolderUri()).thenReturn(Uri.parse("file:///x"))
        val rows = listOf(row("a", "v1"))
        whenever(resourceFileDao.getAllOnce()).thenReturn(rows)
        whenever(resolver.resolve(any())).thenReturn(null)
        noIndexHits()
        whenever(vaultDao.getVaultById("v1")).thenReturn(vault("v1", "Mario"))
        val c = checker()
        c.runCheck()
        val report = (c.state.value as LibraryIntegrityState.Issues).report
        c.applyRemoval(report)
        verify(vaultDao).deleteVaultById("v1")
        verify(resourceFileDao, never()).deleteById(eq("a"))
    }

    @Test
    fun applyRemoval_clears_artwork_pointer_when_it_matches_a_dead_row() = runTest {
        whenever(folder.homeFolderUri()).thenReturn(Uri.parse("file:///x"))
        val rows = listOf(row("a", "v1"), row("b", "v1"))
        whenever(resourceFileDao.getAllOnce()).thenReturn(rows)
        whenever(resolver.resolve("a")).thenReturn(null)
        whenever(resolver.resolve("b")).thenReturn(Uri.parse("file:///x/b"))
        noIndexHits()
        whenever(vaultDao.getVaultById("v1")).thenReturn(vault("v1", "Zelda", artworkResourceId = "a"))
        val c = checker()
        c.runCheck()
        val report = (c.state.value as LibraryIntegrityState.Issues).report
        c.applyRemoval(report)
        verify(vaultDao).updateArtworkResourceId("v1", null)
        verify(savedTabDao).deleteByResourceId("a")
        verify(savedMapDao).deleteByResourceId("a")
        verify(resourceFileDao).deleteById("a")
    }

    @Test
    fun applyRemoval_handles_orphans() = runTest {
        whenever(folder.homeFolderUri()).thenReturn(Uri.parse("file:///x"))
        val rows = listOf(row("o1", null))
        whenever(resourceFileDao.getAllOnce()).thenReturn(rows)
        whenever(resolver.resolve(any())).thenReturn(null)
        noIndexHits()
        val c = checker()
        c.runCheck()
        val report = (c.state.value as LibraryIntegrityState.Issues).report
        c.applyRemoval(report)
        verify(savedTabDao).deleteByResourceId("o1")
        verify(savedMapDao).deleteByResourceId("o1")
        verify(resourceFileDao).deleteById("o1")
    }

    @Test
    fun missing_resource_heals_via_media_search_index() = runTest {
        // A resource_files row whose absolutePath won't resolve, but the
        // media_search_index has a hit for the same (filename, sizeBytes).
        // After runCheck() the row should be rewritten (healed) and state Healthy.
        val resourceRow = row("r1", "v1", filename = "x.png").copy(
            sizeBytes = 100L,
            absolutePath = "/nonexistent/x.png"
        )
        val indexHit = MediaSearchIndexEntity(
            id = "idx-1",
            searchPathId = "sp-1",
            absolutePath = "/new/path/x.png",
            relativePath = null,
            filename = "x.png",
            normalizedBasename = "x",
            normalizedPathTokens = "new path x png",
            extLower = "png",
            sizeBytes = 100L,
            mtime = 0L
        )

        whenever(folder.homeFolderUri()).thenReturn(Uri.parse("file:///x"))
        whenever(resourceFileDao.getAllOnce()).thenReturn(listOf(resourceRow))
        // Resolver cannot find the row (path is stale).
        whenever(resolver.resolve("r1")).thenReturn(null)
        // Index has a matching entry.
        whenever(mediaSearchIndexDao.findByFilenameAndSize("x.png", 100L)).thenReturn(indexHit)

        val c = checker()
        c.runCheck()

        // State should be Healthy because the row was healed, not reported missing.
        assertEquals(LibraryIntegrityState.Healthy, c.state.value)

        // Verify the DAO was asked to persist the healed row pointing at the new path.
        val captor = argumentCaptor<ResourceFileEntity>()
        verify(resourceFileDao).update(captor.capture())
        val healed = captor.firstValue
        assertEquals("/new/path/x.png", healed.absolutePath)
        assertEquals(ResourceSource.LOCAL_LINK.raw, healed.source)
        // homeRelativePath should be null when absolutePath is set.
        assertEquals(null, healed.homeRelativePath)
    }
}
