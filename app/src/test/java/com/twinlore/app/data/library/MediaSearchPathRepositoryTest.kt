package com.twinlore.app.data.library

import android.content.Context
import androidx.room.Room
import com.twinlore.app.data.SettingsRepository
import com.twinlore.app.data.local.VaultDatabase
import com.twinlore.app.domain.MediaSearchPath
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class MediaSearchPathRepositoryTest {

    @get:Rule val tempFolder = TemporaryFolder()

    private lateinit var ctx: Context
    private lateinit var db: VaultDatabase
    private lateinit var settings: SettingsRepository
    private val esdeFlow = MutableStateFlow<String?>(null)
    private val cocoonFlow = MutableStateFlow<String?>(null)
    private val homeFlow = MutableStateFlow<String?>(null)
    private lateinit var vaultRepo: com.twinlore.app.data.VaultRepository
    private lateinit var repo: MediaSearchPathRepository

    @Before
    fun setup() {
        ctx = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(ctx, VaultDatabase::class.java).allowMainThreadQueries().build()
        settings = mock()
        whenever(settings.esdeFolderUri).thenReturn(esdeFlow)
        whenever(settings.cocoonFolderUri).thenReturn(cocoonFlow)
        whenever(settings.homeFolderUri).thenReturn(homeFlow)
        vaultRepo = com.twinlore.app.data.VaultRepository(
            context = ctx,
            vaultDao = db.vaultDao(),
            savedTabRepository = mock(),
            mapMarkerManager = mock(),
            mapViewStateManager = mock(),
            guideViewStateManager = mock(),
            fuzzyNameMatcher = mock(),
            checklistRepository = mock(),
            settingsRepository = mock(),
            resourceFileDao = db.resourceFileDao(),
            libraryFolderManager = mock(),
            resourceResolver = mock(),
            artworkThumbnailCache = mock(),
            mapThumbnailCache = mock(),
            syncTombstoneDao = db.syncTombstoneDao(),
            database = db,
        )
        repo = MediaSearchPathRepository(
            pathDao = db.mediaSearchPathDao(),
            indexDao = db.mediaSearchIndexDao(),
            dirDao = db.mediaSearchDirDao(),
            resourceDao = db.resourceFileDao(),
            savedTabDao = db.savedTabDao(),
            savedMapDao = db.savedMapDao(),
            vaultRepo = vaultRepo,
            settings = settings,
            context = ctx,
            clock = { 1000L },
            mapThumbnailCache = null,
        )
    }

    @After
    fun tearDown() { db.close() }

    @Test
    fun add_manual_path_shows_up_in_getAll() = runTest {
        repo.add(android.net.Uri.parse("file:///tmp/foo"), "My folder")
        val all = repo.getAll()
        assertEquals(1, all.size)
        assertEquals(MediaSearchPath.Origin.MANUAL, all[0].origin)
        assertEquals("My folder", all[0].label)
    }

    @Test
    fun remove_manual_path_succeeds() = runTest {
        val path = repo.add(android.net.Uri.parse("file:///tmp/foo"), "X")
        repo.remove(path.id)
        assertEquals(0, repo.getAll().size)
    }

    @Test
    fun remove_auto_path_is_a_noop() = runTest {
        val esde = tempFolder.newFolder("esde")
        java.io.File(esde, "downloaded_media").mkdirs()
        esdeFlow.value = "file://${esde.absolutePath}"

        repo.syncAutoFromSettings()
        val auto = repo.getAll().first { it.origin == MediaSearchPath.Origin.AUTO_ESDE }
        repo.remove(auto.id)
        assertEquals(1, repo.getAll().count { it.origin == MediaSearchPath.Origin.AUTO_ESDE })
    }

    @Test
    fun syncAutoFromSettings_upserts_AUTO_ESDE_when_subdir_exists() = runTest {
        val esde = tempFolder.newFolder("esde2")
        java.io.File(esde, "downloaded_media").mkdirs()
        esdeFlow.value = "file://${esde.absolutePath}"

        repo.syncAutoFromSettings()
        val auto = repo.getAll().firstOrNull { it.origin == MediaSearchPath.Origin.AUTO_ESDE }
        assertNotNull(auto)
        assertEquals("file://${esde.absolutePath}/downloaded_media", auto!!.uri)
    }

    @Test
    fun syncAutoFromSettings_no_AUTO_when_subdir_missing() = runTest {
        val esde = tempFolder.newFolder("esde-no-sub")
        // intentionally do NOT create downloaded_media
        esdeFlow.value = "file://${esde.absolutePath}"

        repo.syncAutoFromSettings()
        assertNull(repo.getAll().firstOrNull { it.origin == MediaSearchPath.Origin.AUTO_ESDE })
    }

    @Test
    fun syncAutoFromSettings_removes_AUTO_when_subdir_disappears() = runTest {
        val esde = tempFolder.newFolder("esde3")
        java.io.File(esde, "downloaded_media").mkdirs()
        esdeFlow.value = "file://${esde.absolutePath}"

        repo.syncAutoFromSettings()
        assertNotNull(repo.getAll().firstOrNull { it.origin == MediaSearchPath.Origin.AUTO_ESDE })

        esdeFlow.value = null
        repo.syncAutoFromSettings()
        assertNull(repo.getAll().firstOrNull { it.origin == MediaSearchPath.Origin.AUTO_ESDE })
    }

    @Test
    fun syncAutoFromSettings_is_idempotent() = runTest {
        val esde = tempFolder.newFolder("esde4")
        java.io.File(esde, "downloaded_media").mkdirs()
        esdeFlow.value = "file://${esde.absolutePath}"

        repo.syncAutoFromSettings()
        repo.syncAutoFromSettings()
        repo.syncAutoFromSettings()
        assertEquals(1, repo.getAll().count { it.origin == MediaSearchPath.Origin.AUTO_ESDE })
    }

    @Test
    fun cocoon_flavor_works_too() = runTest {
        val cocoon = tempFolder.newFolder("cocoon1")
        java.io.File(cocoon, "downloaded_media").mkdirs()
        cocoonFlow.value = "file://${cocoon.absolutePath}"

        repo.syncAutoFromSettings()
        val auto = repo.getAll().firstOrNull { it.origin == MediaSearchPath.Origin.AUTO_COCOON }
        assertNotNull(auto)
        assertEquals("Cocoon downloaded_media", auto!!.label)
    }

    @Test
    fun removeWithCleanup_clears_bound_slots_and_deletes_resource_files() = runTest {
        val sp = repo.add(android.net.Uri.parse("file:///tmp/foo"), "Custom")
        val resourceId = "rf1"
        val absPath = "/tmp/foo/cover.png"
        val sizeBytes = 100L

        // Seed a vault first (FK target for resource_files.vault_id).
        db.vaultDao().insertVault(testVault(id = "v1", artworkResourceId = null))
        // Seed an index entry for this search path.
        db.mediaSearchIndexDao().insertAll(listOf(
            com.twinlore.app.data.local.MediaSearchIndexEntity(
                id = "i1", searchPathId = sp.id, absolutePath = absPath, relativePath = "cover.png",
                filename = "cover.png", normalizedBasename = "cover", normalizedPathTokens = "",
                extLower = "png", sizeBytes = sizeBytes, mtime = 0,
            )
        ))
        // Seed a LOCAL_LINK resource_files row that fingerprint-matches the index entry.
        db.resourceFileDao().insert(
            com.twinlore.app.data.local.ResourceFileEntity(
                id = resourceId, vaultId = "v1", kind = com.twinlore.app.domain.ResourceKind.ARTWORK.raw,
                filename = "cover.png", sizeBytes = sizeBytes, homeRelativePath = null,
                absolutePath = absPath, mimeType = null,
                source = com.twinlore.app.domain.ResourceSource.LOCAL_LINK.raw,
                lastSeenAt = 0, createdAt = 0,
            )
        )
        // Bind the artwork slot now that the resource row exists.
        db.vaultDao().updateArtworkResourceId("v1", resourceId)

        repo.removeWithCleanup(sp.id)

        assertNull(db.mediaSearchPathDao().getById(sp.id))
        val vault = db.vaultDao().getVaultById("v1")
        assertNull(vault?.artworkResourceId)
        assertNull(db.resourceFileDao().getById(resourceId))
    }

    @Test
    fun disableWithCleanup_keeps_row_and_clears_index() = runTest {
        val esdeFolder = tempFolder.newFolder("esde-disable")
        java.io.File(esdeFolder, "downloaded_media").mkdirs()
        esdeFlow.value = "file://${esdeFolder.absolutePath}"
        repo.syncAutoFromSettings()
        val auto = repo.getAll().first { it.origin == MediaSearchPath.Origin.AUTO_ESDE }

        // Seed an index row.
        db.mediaSearchIndexDao().insertAll(listOf(
            com.twinlore.app.data.local.MediaSearchIndexEntity(
                id = "i1", searchPathId = auto.id, absolutePath = "/tmp/x", relativePath = "x",
                filename = "x", normalizedBasename = "x", normalizedPathTokens = "",
                extLower = "png", sizeBytes = 0, mtime = 0,
            )
        ))

        repo.disableWithCleanup(auto.id)

        val row = db.mediaSearchPathDao().getById(auto.id)
        assertNotNull(row)
        assertEquals(true, row!!.disabled)
        assertEquals(0, db.mediaSearchIndexDao().getForPath(auto.id).size)
    }

    @Test
    fun enable_sets_disabled_flag_false() = runTest {
        val sp = repo.add(android.net.Uri.parse("file:///tmp/foo"), "Custom")
        val row = db.mediaSearchPathDao().getById(sp.id)!!
        db.mediaSearchPathDao().update(row.copy(disabled = true))
        assertEquals(true, db.mediaSearchPathDao().getById(sp.id)!!.disabled)

        repo.enable(sp.id)
        assertEquals(false, db.mediaSearchPathDao().getById(sp.id)!!.disabled)
    }

    @Test
    fun syncAutoFromSettings_preserves_disabled_flag() = runTest {
        val esdeFolder = tempFolder.newFolder("esde-preserve")
        java.io.File(esdeFolder, "downloaded_media").mkdirs()
        esdeFlow.value = "file://${esdeFolder.absolutePath}"
        repo.syncAutoFromSettings()
        val auto = repo.getAll().first { it.origin == MediaSearchPath.Origin.AUTO_ESDE }
        val row = db.mediaSearchPathDao().getById(auto.id)!!
        db.mediaSearchPathDao().update(row.copy(disabled = true))

        repo.syncAutoFromSettings()
        val after = db.mediaSearchPathDao().getById(auto.id)!!
        assertEquals(true, after.disabled)
    }

    @Test
    fun countAffectedVaults_returns_zero_when_no_links() = runTest {
        val sp = repo.add(android.net.Uri.parse("file:///tmp/foo"), "C")
        val n = repo.countAffectedVaults(sp.id)
        assertEquals(0, n)
    }

    /** Helper: minimal VaultEntity. Must match VaultEntity.kt:12-95 field list. */
    private fun testVault(id: String, artworkResourceId: String? = null) =
        com.twinlore.app.data.local.VaultEntity(
            id = id,
            name = "Test",
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
            createdAt = java.util.Date(0),
        )
}
