package com.twinlore.app.data.library

import android.net.Uri
import com.twinlore.app.data.MapMarkerManager
import com.twinlore.app.data.MapViewStateManager
import com.twinlore.app.data.local.ResourceFileDao
import com.twinlore.app.data.local.ResourceFileEntity
import com.twinlore.app.data.local.SavedMapDao
import com.twinlore.app.data.local.SavedMapEntity
import com.twinlore.app.data.local.SavedTabDao
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class VaultImageCleanerTest {
    private val savedMapDao: SavedMapDao = mock()
    private val savedTabDao: SavedTabDao = mock()
    private val resourceFileDao: ResourceFileDao = mock()
    private val resolver: ResourceResolver = mock()
    private val folder: LibraryFolderManager = mock()
    private val vaultDao: VaultDao = mock()
    private val thumbs: MapThumbnailCache = mock()
    private val markers: MapMarkerManager = mock()
    private val viewState: MapViewStateManager = mock()

    private val cleaner = VaultImageCleaner(
        savedMapDao, savedTabDao, resourceFileDao, resolver, folder, vaultDao, thumbs, markers, viewState
    )

    private fun res(id: String, kind: String) = ResourceFileEntity(
        id = id, vaultId = "v1", kind = kind, filename = "$id.png", sizeBytes = 1,
        homeRelativePath = "Game/$id.png", absolutePath = null, mimeType = null,
        source = "user_add", lastSeenAt = 0, createdAt = 0,
    )

    private fun map(id: String, resourceId: String) = SavedMapEntity(
        id = id, vaultId = "v1", title = id, resourceId = resourceId,
        createdAt = Date(0), lastAccessed = Date(0),
    )

    @Test
    fun removes_every_image_with_its_file_but_never_artwork_and_keeps_the_vault() = runTest {
        whenever(savedMapDao.getMapsByVaultIdOnce("v1"))
            .thenReturn(listOf(map("m1", "shot"), map("m2", "added")))
            .thenReturn(emptyList())
        whenever(savedTabDao.getTabsForVaultSync("v1")).thenReturn(emptyList())
        whenever(resourceFileDao.getForVaultAndKindOnce("v1", "map")).thenReturn(listOf(res("shot", "map"), res("added", "map")))
        whenever(resourceFileDao.getForVaultAndKindOnce("v1", "screenshot")).thenReturn(listOf(res("old", "screenshot")))
        val uri = Uri.parse("file:///lib/Game/x.png")
        whenever(resolver.resolve(any())).thenReturn(uri)
        whenever(folder.deleteFileUnderHome(any())).thenReturn(true)
        whenever(vaultDao.getVaultById("v1")).thenReturn(vault(mapResourceId = "added"))

        val removed = cleaner.deleteAllImagesForVault("v1")

        assertEquals(2, removed)
        verify(savedMapDao).deleteMap(eq("m1"))
        verify(savedMapDao).deleteMap(eq("m2"))
        verify(markers).clearMarkers(eq("v1"), eq("m1"))
        for (id in listOf("shot", "added", "old")) {
            verify(resourceFileDao).deleteById(eq(id))
            verify(thumbs).invalidate(eq(id))
        }
        verify(folder, org.mockito.kotlin.times(3)).deleteFileUnderHome(eq(uri))
        verify(resourceFileDao, never()).getForVaultAndKindOnce(eq("v1"), eq("artwork"))
        verify(vaultDao).updateMapResourceId(eq("v1"), eq(null))
        verify(vaultDao, never()).deleteVaultById(any())
        verify(viewState).saveSelectedMapId(eq("v1"), eq(null))
    }

    @Test
    fun an_image_file_a_guide_tab_still_uses_is_kept() = runTest {
        whenever(savedMapDao.getMapsByVaultIdOnce("v1")).thenReturn(emptyList())
        val tab = com.twinlore.app.data.local.SavedTabEntity(
            id = "t", vaultId = "v1", title = "t", url = "", isMap = true,
            createdAt = Date(0), lastAccessed = Date(0), resourceId = "shared",
        )
        whenever(savedTabDao.getTabsForVaultSync("v1")).thenReturn(listOf(tab))
        whenever(resourceFileDao.getForVaultAndKindOnce("v1", "map")).thenReturn(listOf(res("shared", "map")))
        whenever(resourceFileDao.getForVaultAndKindOnce("v1", "screenshot")).thenReturn(emptyList())
        whenever(vaultDao.getVaultById("v1")).thenReturn(vault(mapResourceId = "shared"))

        cleaner.deleteAllImagesForVault("v1")

        verify(resourceFileDao, never()).deleteById(any())
        verify(vaultDao, never()).updateMapResourceId(any(), any())
    }

    @Test
    fun the_image_tab_delete_takes_the_file_with_the_entry() = runTest {
        whenever(savedMapDao.getMapById("m1")).thenReturn(map("m1", "shot"))
        whenever(savedMapDao.getMapsByVaultIdOnce("v1")).thenReturn(listOf(map("m2", "other")))
        whenever(savedTabDao.getTabsForVaultSync("v1")).thenReturn(emptyList())
        val uri = Uri.parse("file:///lib/Game/shot.png")
        whenever(resolver.resolve("shot")).thenReturn(uri)
        whenever(folder.deleteFileUnderHome(any())).thenReturn(true)
        whenever(vaultDao.getVaultById("v1")).thenReturn(vault(mapResourceId = "shot"))

        cleaner.deleteImage("v1", "m1")

        verify(markers).clearMarkers(eq("v1"), eq("m1"))
        verify(savedMapDao).deleteMap(eq("m1"))
        verify(folder).deleteFileUnderHome(eq(uri))
        verify(resourceFileDao).deleteById(eq("shot"))
        verify(thumbs).invalidate(eq("shot"))
        verify(vaultDao).updateMapResourceId(eq("v1"), eq(null))
        verify(resourceFileDao, never()).deleteById(eq("other"))
    }

    @Test
    fun a_file_still_used_by_another_entry_or_as_artwork_stays() = runTest {
        whenever(savedMapDao.getMapById("m1")).thenReturn(map("m1", "shared"))
        whenever(savedMapDao.getMapById("m3")).thenReturn(map("m3", "art"))
        whenever(savedMapDao.getMapsByVaultIdOnce("v1")).thenReturn(listOf(map("m2", "shared")))
        whenever(savedTabDao.getTabsForVaultSync("v1")).thenReturn(emptyList())
        whenever(vaultDao.getVaultById("v1")).thenReturn(vault(mapResourceId = null))

        cleaner.deleteImage("v1", "m1")
        cleaner.deleteImage("v1", "m3")

        verify(savedMapDao).deleteMap(eq("m1"))
        verify(savedMapDao).deleteMap(eq("m3"))
        verify(folder, never()).deleteFileUnderHome(any())
        verify(resourceFileDao, never()).deleteById(any())
    }

    private fun vault(mapResourceId: String?) = VaultEntity(
        id = "v1", name = "Game", romPath = null, romFilename = null, artworkVibrantArgb = null,
        systemeid = null, gameId = null, source = null, raGameId = null, steamAppId = null,
        artworkResourceId = "art", guideResourceId = null, mapResourceId = mapResourceId,
        launchIntentJson = null, lastOpened = null, createdAt = Date(0L),
    )
}
