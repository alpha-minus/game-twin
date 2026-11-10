package com.twinlore.app.data.library

import com.twinlore.app.data.SettingsRepository
import com.twinlore.app.data.local.ResourceFileDao
import com.twinlore.app.data.local.ResourceFileEntity
import com.twinlore.app.data.local.SavedTabDao
import com.twinlore.app.data.local.SavedTabEntity
import com.twinlore.app.data.local.ScrollMarkerDao
import com.twinlore.app.data.local.VaultDao
import com.twinlore.app.data.local.VaultEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeast
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File
import java.util.Date
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
class MigrationCoordinatorTest {

    @get:Rule val tempFolder = TemporaryFolder()

    private lateinit var home: File
    private lateinit var legacyRoot: File
    private lateinit var vaultDao: VaultDao
    private lateinit var resourceDao: ResourceFileDao
    private lateinit var savedTabDao: SavedTabDao
    private lateinit var scrollMarkerDao: ScrollMarkerDao
    private lateinit var folder: LibraryFolderManager
    private lateinit var fileRelocator: FileRelocator
    private lateinit var sidecarImporter: JsonSidecarImporter
    private val libraryAutoSaveObserver: LibraryAutoSaveObserver = mock()

    @Before
    fun setup() {
        home = tempFolder.newFolder("home")
        legacyRoot = tempFolder.newFolder("legacy_vaults")
        val settings: SettingsRepository = mock()
        whenever(settings.homeFolderUri).thenReturn(MutableStateFlow("file://${home.absolutePath}"))
        whenever(settings.vaultStorageFolderUri).thenReturn(MutableStateFlow(null))
        folder = LibraryFolderManager(RuntimeEnvironment.getApplication(), settings)
        vaultDao = mock()
        resourceDao = mock()
        savedTabDao = mock()
        scrollMarkerDao = mock()
        fileRelocator = FileRelocator(resourceDao, folder)
        sidecarImporter = mock()
    }

    private fun makeCoordinator(): MigrationCoordinator =
        MigrationCoordinator(
            vaultDao = vaultDao,
            resourceDao = resourceDao,
            savedTabDao = savedTabDao,
            scrollMarkerDao = scrollMarkerDao,
            libraryAutoSaveObserver = libraryAutoSaveObserver,
            clock = { 0L },
            uuid = { it },
            sizeProbe = { File(it).length() }
        )

    private fun vaultRow(id: String, name: String): VaultEntity =
        VaultEntity(
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
            artworkResourceId = null,
            guideResourceId = null,
            mapResourceId = null,
            launchIntentJson = null,
            lastOpened = null,
            createdAt = Date(0L)
        )

    private fun savedTabRow(id: String, vaultId: String): SavedTabEntity =
        SavedTabEntity(
            id = id,
            vaultId = vaultId,
            title = "t",
            url = "https://x.com",
            createdAt = Date(0L),
            lastAccessed = Date(0L)
        )

    private fun writeLegacyArtwork(vaultId: String): File {
        val artDir = File(legacyRoot, "$vaultId/art").apply { mkdirs() }
        return File(artDir, "artwork.png").apply { writeBytes(ByteArray(11) { 0xAA.toByte() }) }
    }

    private fun writeLegacyMap(vaultId: String, filename: String): File {
        val mapDir = File(legacyRoot, "$vaultId/map").apply { mkdirs() }
        return File(mapDir, filename).apply { writeBytes(ByteArray(22) { 0xBB.toByte() }) }
    }

    private fun writeLegacyMht(vaultId: String, bookmarkId: String): File {
        val guideDir = File(legacyRoot, "$vaultId/guide/$bookmarkId").apply { mkdirs() }
        return File(guideDir, "guide.mht").apply { writeBytes(ByteArray(33) { 0xCC.toByte() }) }
    }

    private fun writeLegacyZipBookmark(vaultId: String, bookmarkId: String): File {
        val guideDir = File(legacyRoot, "$vaultId/guide/$bookmarkId").apply { mkdirs() }
        val zip = File(guideDir, "bookmark.zip")
        ZipOutputStream(zip.outputStream()).use { zout ->
            zout.putNextEntry(ZipEntry("index.html"))
            zout.write("<html><body>cached</body></html>".toByteArray())
            zout.closeEntry()
            zout.putNextEntry(ZipEntry("assets/img.png"))
            zout.write(ByteArray(4) { 0x99.toByte() })
            zout.closeEntry()
        }
        return zip
    }

    private fun writeLegacyMetadata(vaultId: String, artworkRel: String? = null, mapRel: String? = null) {
        val dir = File(legacyRoot, vaultId).apply { mkdirs() }
        val sb = StringBuilder("{")
        if (artworkRel != null) sb.append("\"artworkPath\":\"$artworkRel\",")
        if (mapRel != null) sb.append("\"mapPath\":\"$mapRel\",")
        sb.append("\"name\":\"v\"}")
        File(dir, "vault_metadata.json").writeText(sb.toString())
    }

    @Test
    fun runFullMigration_inserts_resource_rows_for_artwork_and_map() = runTest {
        val vaultId = "vault-A"
        writeLegacyArtwork(vaultId)
        writeLegacyMap(vaultId, "map_42.png")
        writeLegacyMetadata(vaultId, artworkRel = "art/artwork.png", mapRel = "map/map_42.png")
        whenever(vaultDao.getAllVaults()).thenReturn(listOf(vaultRow(vaultId, "VaultA")))
        whenever(resourceDao.getForVaultOnce(vaultId)).thenReturn(emptyList())
        whenever(savedTabDao.getTabsForVaultSync(vaultId)).thenReturn(emptyList())

        makeCoordinator().runFullMigration(
            legacyVaultsRoots = listOf(legacyRoot),
            sidecarImporter = sidecarImporter,
            fileRelocator = fileRelocator
        ) { /* ignore progress */ }

        // Phase A0 inserts at least 2 resource_files rows (artwork + map).
        val insertCaptor = argumentCaptor<ResourceFileEntity>()
        verify(resourceDao, atLeast(2)).insert(insertCaptor.capture())
        val kinds = insertCaptor.allValues.map { it.kind }.toSet()
        assertTrue("artwork inserted", "artwork" in kinds)
        assertTrue("map inserted", "map" in kinds)
        verify(vaultDao).updateArtworkResourceId(eq(vaultId), any())
        verify(vaultDao).updateMapResourceId(eq(vaultId), any())
    }

    @Test
    fun runFullMigration_handles_mht_bookmark() = runTest {
        val vaultId = "vault-mht"
        val bookmarkId = "bm-mht"
        writeLegacyMht(vaultId, bookmarkId)
        writeLegacyMetadata(vaultId)
        whenever(vaultDao.getAllVaults()).thenReturn(listOf(vaultRow(vaultId, "VaultMht")))
        whenever(savedTabDao.getTabsForVaultSync(vaultId)).thenReturn(listOf(savedTabRow(bookmarkId, vaultId)))
        whenever(resourceDao.getForVaultOnce(vaultId)).thenReturn(emptyList())

        makeCoordinator().runFullMigration(
            legacyVaultsRoots = listOf(legacyRoot),
            sidecarImporter = sidecarImporter,
            fileRelocator = fileRelocator
        ) { /* ignore */ }

        val insertCaptor = argumentCaptor<ResourceFileEntity>()
        verify(resourceDao, atLeast(1)).insert(insertCaptor.capture())
        val guideRow = insertCaptor.allValues.firstOrNull { it.kind == "guide" }
        assertNotNull("guide row inserted", guideRow)
        assertTrue("guide filename is .mht", guideRow!!.filename.endsWith("guide.mht"))
        verify(savedTabDao).updateResourceId(eq(bookmarkId), any())
    }

    @Test
    fun runFullMigration_extracts_html_from_zip_bookmark() = runTest {
        val vaultId = "vault-zip"
        val bookmarkId = "bm-zip"
        writeLegacyZipBookmark(vaultId, bookmarkId)
        writeLegacyMetadata(vaultId)
        whenever(vaultDao.getAllVaults()).thenReturn(listOf(vaultRow(vaultId, "VaultZip")))
        whenever(savedTabDao.getTabsForVaultSync(vaultId)).thenReturn(listOf(savedTabRow(bookmarkId, vaultId)))
        whenever(resourceDao.getForVaultOnce(vaultId)).thenReturn(emptyList())

        makeCoordinator().runFullMigration(
            legacyVaultsRoots = listOf(legacyRoot),
            sidecarImporter = sidecarImporter,
            fileRelocator = fileRelocator
        ) { /* ignore */ }

        val insertCaptor = argumentCaptor<ResourceFileEntity>()
        verify(resourceDao, atLeast(1)).insert(insertCaptor.capture())
        val guideRow = insertCaptor.allValues.firstOrNull { it.kind == "guide" }
        assertNotNull("guide row inserted", guideRow)
        assertTrue("guide filename ends in .html", guideRow!!.filename.endsWith(".html"))
        verify(savedTabDao).updateResourceId(eq(bookmarkId), any())
    }

    @Test
    fun runFullMigration_unions_external_and_internal_roots() = runTest {
        val internalRoot = tempFolder.newFolder("internal_vaults")
        val vaultId = "vault-C"
        File(internalRoot, "$vaultId/art").mkdirs()
        File(internalRoot, "$vaultId/art/artwork.png").writeBytes(ByteArray(5))
        File(internalRoot, "$vaultId/vault_metadata.json").writeText("{\"artworkPath\":\"art/artwork.png\"}")
        whenever(vaultDao.getAllVaults()).thenReturn(listOf(vaultRow(vaultId, "VaultC")))
        whenever(resourceDao.getForVaultOnce(vaultId)).thenReturn(emptyList())
        whenever(savedTabDao.getTabsForVaultSync(vaultId)).thenReturn(emptyList())

        makeCoordinator().runFullMigration(
            legacyVaultsRoots = listOf(legacyRoot, internalRoot),
            sidecarImporter = sidecarImporter,
            fileRelocator = fileRelocator
        ) { /* ignore */ }

        verify(resourceDao, atLeast(1)).insert(any())
        verify(vaultDao).updateArtworkResourceId(eq(vaultId), any())
    }
}
