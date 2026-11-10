package com.twinlore.app.data.library

import com.twinlore.app.data.VaultRepository
import com.twinlore.app.data.local.ResourceFileDao
import com.twinlore.app.data.local.ResourceFileEntity
import com.twinlore.app.data.local.SavedTabDao
import com.twinlore.app.data.local.SavedTabEntity
import com.twinlore.app.domain.LocalMediaMatch
import com.twinlore.app.domain.ResourceKind
import com.twinlore.app.domain.ResourceSource
import com.twinlore.app.domain.Result
import com.twinlore.app.domain.Vault
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import java.util.Date

class LocalMediaBinderTest {

    private val resourceDao: ResourceFileDao = mock()
    private val vaultRepo: VaultRepository = mock()
    private val matcher: LocalMediaMatcher = mock()
    private val savedTabDao: SavedTabDao = mock {
        onBlocking { getTabsForVaultSync(any()) } doReturn emptyList()
    }
    private val savedMapDao: com.twinlore.app.data.local.SavedMapDao = mock()
    private val settings: com.twinlore.app.data.SettingsRepository = mock {
        on { defaultAssetType } doReturn kotlinx.coroutines.flow.flowOf(com.twinlore.app.data.DefaultAssetType.GRID)
        on { gridDisplayRatio } doReturn kotlinx.coroutines.flow.flowOf(com.twinlore.app.data.GridDisplayRatio.RATIO_2_3)
        on { preferScraperForArtwork } doReturn kotlinx.coroutines.flow.flowOf(false)
        on { surfaceLocalImagesInMapsTab } doReturn kotlinx.coroutines.flow.flowOf(true)
    }
    private val binder = LocalMediaBinder(matcher, resourceDao, vaultRepo, savedTabDao, savedMapDao, settings, bitmapContext = null, clock = { 1000L })

    private fun emptyVault(id: String = "v1") = Vault(
        id = id, name = "X", romPath = null, romFilename = null, systemeid = null,
        gameId = null, source = null, lastOpened = null, createdAt = Date(),
        artworkResourceId = null, mapResourceId = null, guideResourceId = null,
    )

    private fun art(file: String, prio: Int) = LocalMediaMatch(
        resourceKind = ResourceKind.ARTWORK,
        // Map legacy GRID-priority integer to the new tag string for compatibility:
        // 0=cover, 1=3dbox, 2=fanart, 3=miximage, 4=marquee, 5=screenshot, else=other
        artworkTag = when (prio) { 0 -> "cover"; 1 -> "3dbox"; 2 -> "fanart"; 3 -> "miximage"; 4 -> "marquee"; 5 -> "screenshot"; else -> "other" },
        absolutePath = "/tmp/$file", contentUriString = null, filename = file,
        sizeBytes = 100, extLower = file.substringAfterLast('.').lowercase(),
        searchPathId = "sp-test", relativePath = null,
    )
    private fun guide(file: String) = LocalMediaMatch(
        resourceKind = ResourceKind.GUIDE, artworkTag = "guide",
        absolutePath = "/tmp/$file", contentUriString = null, filename = file,
        sizeBytes = 100, extLower = file.substringAfterLast('.').lowercase(),
        searchPathId = "sp-test", relativePath = null,
    )

    @Test
    fun no_matches_returns_empty_report() = runTest {
        val v = emptyVault()
        whenever(matcher.findForVault(v)).thenReturn(emptyList())
        val r = binder.bindForVault(v)
        assertEquals("v1", r.vaultId)
        assertNull(r.artworkBound)
        assertEquals(0, r.artworkAvailable)
        verifyNoInteractions(vaultRepo)
    }

    @Test
    fun auto_binds_artwork_to_lowest_tag_priority() = runTest {
        val v = emptyVault()
        whenever(matcher.findForVault(v)).thenReturn(listOf(
            art("fanart.jpg", 1),
            art("cover.png", 0),     // wins
            art("screenshot.png", 3),
        ))
        whenever(resourceDao.getForVaultOnce("v1")).thenReturn(emptyList())
        whenever(vaultRepo.updateArtworkResourceId(any(), any())).thenReturn(Result.Success(Unit))

        val r = binder.bindForVault(v)
        assertEquals("cover.png", r.artworkBound)
        verify(vaultRepo).updateArtworkResourceId(eq("v1"), any())
    }

    @Test
    fun does_not_clobber_filled_artwork_slot() = runTest {
        val v = emptyVault().copy(artworkResourceId = "rf-existing")
        whenever(matcher.findForVault(v)).thenReturn(listOf(art("cover.png", 0)))
        whenever(resourceDao.getForVaultOnce("v1")).thenReturn(emptyList())

        val r = binder.bindForVault(v)
        assertNull(r.artworkBound)
        assertEquals(1, r.artworkAvailable)
        verify(vaultRepo, never()).updateArtworkResourceId(any(), any())
    }

    @Test
    fun guide_pdf_beats_mht_beats_html_beats_txt() = runTest {
        val v = emptyVault()
        whenever(matcher.findForVault(v)).thenReturn(listOf(
            guide("a.txt"),
            guide("b.mht"),
            guide("c.pdf"),       // wins
            guide("d.html"),
        ))
        whenever(resourceDao.getForVaultOnce("v1")).thenReturn(emptyList())
        whenever(vaultRepo.updateGuideResourceId(any(), any())).thenReturn(Result.Success(Unit))
        whenever(savedTabDao.getMaxOrder("v1")).thenReturn(null)
        whenever(savedTabDao.getTabsForVaultSync("v1")).thenReturn(emptyList())

        val r = binder.bindForVault(v)
        assertEquals("c.pdf", r.guideBound)
    }

    @Test
    fun never_auto_binds_map_slot() = runTest {
        val v = emptyVault()
        whenever(matcher.findForVault(v)).thenReturn(listOf(art("cover.png", 0)))
        whenever(resourceDao.getForVaultOnce("v1")).thenReturn(emptyList())
        whenever(vaultRepo.updateArtworkResourceId(any(), any())).thenReturn(Result.Success(Unit))

        binder.bindForVault(v)
        verify(vaultRepo, never()).updateMapResourceId(any(), any())
    }

    @Test
    fun dedups_already_inserted_resource_rows() = runTest {
        val v = emptyVault()
        whenever(matcher.findForVault(v)).thenReturn(listOf(art("cover.png", 0)))
        // Pretend the row already exists for this vault
        whenever(resourceDao.getForVaultOnce("v1")).thenReturn(listOf(
            ResourceFileEntity(
                id = "rf-existing", vaultId = "v1", kind = "artwork",
                filename = "cover.png", sizeBytes = 100,
                homeRelativePath = null, absolutePath = "/tmp/cover.png",
                mimeType = null, source = ResourceSource.LOCAL_LINK.raw,
                lastSeenAt = 0, createdAt = 0,
            )
        ))
        whenever(vaultRepo.updateArtworkResourceId(any(), any())).thenReturn(Result.Success(Unit))

        binder.bindForVault(v)
        // Should NOT insert a second row
        verify(resourceDao, never()).insert(any())
    }

    @Test
    fun artwork_bound_is_null_when_update_fails() = runTest {
        val v = emptyVault()
        whenever(matcher.findForVault(v)).thenReturn(listOf(art("cover.png", 0)))
        whenever(resourceDao.getForVaultOnce("v1")).thenReturn(emptyList())
        whenever(vaultRepo.updateArtworkResourceId(any(), any())).thenReturn(Result.Failure(Exception("DB write failed")))

        val r = binder.bindForVault(v)
        assertNull(r.artworkBound)
        assertEquals(1, r.artworkAvailable)
    }

    @Test
    fun guide_bound_is_null_when_update_fails() = runTest {
        val v = emptyVault()
        whenever(matcher.findForVault(v)).thenReturn(listOf(guide("a.pdf")))
        whenever(resourceDao.getForVaultOnce("v1")).thenReturn(emptyList())
        whenever(vaultRepo.updateGuideResourceId(any(), any())).thenReturn(Result.Failure(Exception("DB write failed")))
        whenever(savedTabDao.getMaxOrder("v1")).thenReturn(null)

        val r = binder.bindForVault(v)
        assertNull(r.guideBound)
        assertEquals(1, r.guideAvailable)
    }

    @Test
    fun guide_match_creates_saved_tab() = runTest {
        val v = emptyVault()
        whenever(matcher.findForVault(v)).thenReturn(listOf(guide("manual.pdf")))
        whenever(resourceDao.getForVaultOnce("v1")).thenReturn(emptyList())
        whenever(vaultRepo.updateGuideResourceId(any(), any())).thenReturn(Result.Success(Unit))
        whenever(savedTabDao.getMaxOrder("v1")).thenReturn(null)

        binder.bindForVault(v)
        verify(savedTabDao).insertTab(any())
    }

    @Test
    fun guide_match_does_not_duplicate_saved_tab() = runTest {
        val v = emptyVault()
        val existingResourceId = "rf-existing"
        whenever(matcher.findForVault(v)).thenReturn(listOf(guide("manual.pdf")))
        whenever(resourceDao.getForVaultOnce("v1")).thenReturn(listOf(
            ResourceFileEntity(
                id = existingResourceId, vaultId = "v1", kind = "guide",
                filename = "manual.pdf", sizeBytes = 100,
                homeRelativePath = null, absolutePath = "/tmp/manual.pdf",
                mimeType = null, source = ResourceSource.LOCAL_LINK.raw,
                lastSeenAt = 0, createdAt = 0,
            )
        ))
        // Existing SavedTab already points at this resource
        whenever(savedTabDao.getTabsForVaultSync("v1")).thenReturn(listOf(
            SavedTabEntity(
                id = "tab1", vaultId = "v1", title = "manual",
                url = "", scrollPosition = 0, zoomLevel = 1f,
                isFavorite = false, isMap = false, isCurrentContent = false,
                order = 0, createdAt = Date(0), lastAccessed = Date(0),
                fileType = "pdf", fileName = "manual.pdf",
                lastPdfPage = 0, pdfScrollOffset = 0f,
                resourceId = existingResourceId,
            )
        ))
        whenever(vaultRepo.updateGuideResourceId(any(), any())).thenReturn(Result.Success(Unit))

        binder.bindForVault(v)
        verify(savedTabDao, never()).insertTab(any())
    }

    @Test
    fun refreshNeverOverwritesAlreadyAssignedLocalArtwork() = runTest {
        // Repro for the "refresh re-sets my chosen local artwork" bug: a vault with a
        // user-assigned LOCAL_LINK artwork must survive a refresh even when a different
        // local file is a "better" ratio match.
        val v = emptyVault().copy(artworkResourceId = "rf-user")
        whenever(resourceDao.getForVaultOnce("v1")).thenReturn(listOf(
            ResourceFileEntity(
                id = "rf-user", vaultId = "v1", kind = ResourceKind.ARTWORK.raw,
                filename = "usercover.png", sizeBytes = 100, homeRelativePath = null,
                absolutePath = "/tmp/usercover.png", mimeType = null,
                source = ResourceSource.LOCAL_LINK.raw, lastSeenAt = 0, createdAt = 0,
            )
        ))
        // A different, "better" (cover-tag) local file the old picker would have chosen.
        whenever(matcher.findForVault(v)).thenReturn(listOf(art("better.png", 0)))
        whenever(vaultRepo.updateArtworkResourceId(any(), any())).thenReturn(Result.Success(Unit))

        val r = binder.bindForVault(v)

        assertNull(r.artworkBound)
        verify(vaultRepo, never()).updateArtworkResourceId(any(), any())
    }

    @Test
    fun refreshLeavesAssignedArtworkSlotAloneEvenWhenNoLongerMatches() = runTest {
        // Under the "never touch an already-assigned slot" policy, refresh no longer
        // clears an assigned artwork pointer whose backing file no longer matches.
        // (Stale rows are cleaned up by re-index / re-scan, not by refresh.)
        val v = emptyVault().copy(artworkResourceId = "rf-stale")
        whenever(resourceDao.getForVaultOnce("v1")).thenReturn(listOf(
            ResourceFileEntity(
                id = "rf-stale", vaultId = "v1", kind = ResourceKind.ARTWORK.raw,
                filename = "stale.jpg", sizeBytes = 100, homeRelativePath = null,
                absolutePath = "/tmp/stale.jpg", mimeType = null,
                source = ResourceSource.LOCAL_LINK.raw, lastSeenAt = 0, createdAt = 0,
            )
        ))
        whenever(matcher.findForVault(v)).thenReturn(emptyList())
        whenever(vaultRepo.updateArtworkResourceId(any(), any())).thenReturn(Result.Success(Unit))

        val r = binder.bindForVault(v)

        assertNull(r.artworkBound)
        verify(vaultRepo, never()).updateArtworkResourceId(any(), any())
    }

    @Test
    fun clearsAutoBoundGuideWhenNoLongerMatches() = runTest {
        val v = emptyVault().copy(guideResourceId = "rf-stale-g")
        whenever(resourceDao.getForVaultOnce("v1")).thenReturn(listOf(
            ResourceFileEntity(
                id = "rf-stale-g", vaultId = "v1", kind = ResourceKind.GUIDE.raw,
                filename = "old.pdf", sizeBytes = 100, homeRelativePath = null,
                absolutePath = "/tmp/old.pdf", mimeType = null,
                source = ResourceSource.LOCAL_LINK.raw, lastSeenAt = 0, createdAt = 0,
            )
        ))
        whenever(matcher.findForVault(v)).thenReturn(emptyList())
        whenever(vaultRepo.updateGuideResourceId(any(), any())).thenReturn(Result.Success(Unit))

        val r = binder.bindForVault(v)

        assertNull(r.guideBound)
        verify(vaultRepo).updateGuideResourceId(eq("v1"), eq(null))
    }

    @Test
    fun skipsGuideMatchInsideGroupFolder() = runTest {
        // A page-group's page-N.mht files are owned by the group tab; the
        // binder must never register them as standalone guides.
        val v = emptyVault()
        val groupTab = SavedTabEntity(
            id = "tab-g", vaultId = "v1", title = "Super Metroid Walkthrough",
            url = "", scrollPosition = 0, zoomLevel = 1f,
            isFavorite = false, isMap = false, isCurrentContent = false,
            order = 0, createdAt = Date(0), lastAccessed = Date(0),
            fileType = "group", fileName = null,
            lastPdfPage = 0, pdfScrollOffset = 0f,
            resourceId = null, groupFolderName = "Super Metroid Walkthrough",
        )
        whenever(savedTabDao.getTabsForVaultSync("v1")).thenReturn(listOf(groupTab))
        whenever(matcher.findForVault(v)).thenReturn(listOf(
            guide("page-2.mht").copy(
                absolutePath = "/storage/GameTwin/Lic/Super Metroid/Super Metroid Walkthrough/page-2.mht",
            )
        ))
        whenever(resourceDao.getForVaultOnce("v1")).thenReturn(emptyList())

        val r = binder.bindForVault(v)

        assertEquals(0, r.guideAvailable)
        verify(resourceDao, never()).insert(any())
        verify(savedTabDao, never()).insertTab(any())
        verify(vaultRepo, never()).updateGuideResourceId(any(), any())
    }

    @Test
    fun skipsLogicalDuplicateByNameAndSizeAcrossSources() = runTest {
        // The same guide already exists as a browser_save row at a different
        // path: the binder must not re-register it as a local_link, and the
        // empty slot binds to the existing row instead.
        val v = emptyVault()
        whenever(matcher.findForVault(v)).thenReturn(listOf(
            guide("Super Metroid - GameFAQs.mht").copy(
                absolutePath = "/storage/GameTwin/Lic/super metroid (6)/Super Metroid - GameFAQs.mht",
            )
        ))
        whenever(resourceDao.getForVaultOnce("v1")).thenReturn(listOf(
            ResourceFileEntity(
                id = "rf-bs", vaultId = "v1", kind = "guide",
                filename = "Super Metroid - GameFAQs.mht", sizeBytes = 100,
                homeRelativePath = null,
                absolutePath = "/data/vaults/v1/guides/Super Metroid - GameFAQs.mht",
                mimeType = null, source = ResourceSource.BROWSER_SAVE.raw,
                lastSeenAt = 0, createdAt = 0,
            )
        ))
        whenever(vaultRepo.updateGuideResourceId(any(), any())).thenReturn(Result.Success(Unit))

        binder.bindForVault(v)

        verify(resourceDao, never()).insert(any())
        verify(savedTabDao, never()).insertTab(any())
        verify(vaultRepo).updateGuideResourceId(eq("v1"), eq("rf-bs"))
    }

    @Test
    fun skipsMatchesUnderDotPrefixedDirectories() = runTest {
        // Stale index rows can still point into .stversions; the binder must
        // not turn them into resources even before the index is rebuilt.
        val v = emptyVault()
        whenever(matcher.findForVault(v)).thenReturn(listOf(
            guide("old-guide.mht").copy(
                absolutePath = "/storage/GameTwin/Lic/.stversions/super metroid (2)/old-guide.mht",
                relativePath = "Lic/.stversions/super metroid (2)/old-guide.mht",
            )
        ))
        whenever(resourceDao.getForVaultOnce("v1")).thenReturn(emptyList())

        val r = binder.bindForVault(v)

        assertEquals(0, r.guideAvailable)
        verify(resourceDao, never()).insert(any())
        verify(savedTabDao, never()).insertTab(any())
    }

    @Test
    fun cleanupLocalLinkDuplicatesDeletesPlannedRowsTabsAndMaps() = runTest {
        val base = ResourceFileEntity(
            id = "rf-base", vaultId = "v1", kind = "guide",
            filename = "guide.mht", sizeBytes = 42, homeRelativePath = null,
            absolutePath = "/storage/GameTwin/Lic/Super Metroid/guide.mht",
            mimeType = null, source = ResourceSource.LOCAL_LINK.raw,
            lastSeenAt = 0, createdAt = 0,
        )
        val variant = base.copy(
            id = "rf-6",
            absolutePath = "/storage/GameTwin/Lic/super metroid (6)/guide.mht",
        )
        whenever(resourceDao.getForVaultOnce("v1")).thenReturn(listOf(base, variant))
        whenever(savedTabDao.getTabsForVaultSync("v1")).thenReturn(emptyList())

        val summary = binder.cleanupLocalLinkDuplicates("v1")

        assertEquals(1, summary.logicalDuplicates)
        assertEquals(0, summary.syncArtifacts)
        assertEquals(0, summary.groupOwned)
        verify(vaultRepo).clearAnySlotPointingAt(eq("rf-6"))
        verify(savedTabDao).deleteByResourceIds(eq(listOf("rf-6")))
        verify(savedMapDao).deleteByResourceIds(eq(listOf("rf-6")))
        verify(resourceDao).deleteByIds(eq(listOf("rf-6")))
    }

    @Test
    fun cleanupLocalLinkDuplicatesIsNoOpOnCleanVault() = runTest {
        whenever(resourceDao.getForVaultOnce("v1")).thenReturn(emptyList())
        whenever(savedTabDao.getTabsForVaultSync("v1")).thenReturn(emptyList())

        val summary = binder.cleanupLocalLinkDuplicates("v1")

        assertEquals(0, summary.total)
        verify(resourceDao, never()).deleteByIds(any())
        verify(savedTabDao, never()).deleteByResourceIds(any())
        verifyNoInteractions(vaultRepo)
    }

    @Test
    fun doesNotClearArtworkSlotWhenSourceIsNotLocalLink() = runTest {
        // Regression: a manually-assigned (USER_ADD) or scraper artwork must NOT
        // be cleared even when matcher returns no matches.
        val v = emptyVault().copy(artworkResourceId = "rf-user")
        whenever(resourceDao.getForVaultOnce("v1")).thenReturn(listOf(
            ResourceFileEntity(
                id = "rf-user", vaultId = "v1", kind = ResourceKind.ARTWORK.raw,
                filename = "user.jpg", sizeBytes = 100, homeRelativePath = null,
                absolutePath = "/tmp/user.jpg", mimeType = null,
                source = ResourceSource.USER_ADD.raw, lastSeenAt = 0, createdAt = 0,
            )
        ))
        whenever(matcher.findForVault(v)).thenReturn(emptyList())

        binder.bindForVault(v)

        verify(vaultRepo, never()).updateArtworkResourceId(any(), any())
    }
}
