package com.twinlore.app.data

import android.content.Context
import com.twinlore.app.data.ChecklistRepository
import com.twinlore.app.data.FuzzyNameMatcher
import com.twinlore.app.data.local.SyncTombstoneEntity
import com.twinlore.app.data.local.VaultDao
import com.twinlore.app.data.local.VaultDatabase
import com.twinlore.app.data.local.VaultEntity
import com.twinlore.app.data.local.VaultEntityMapper
import com.twinlore.app.domain.Result
import com.twinlore.app.domain.Vault
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*
import org.mockito.Mockito.doAnswer
import java.util.Date
import kotlinx.coroutines.flow.Flow

/**
 * Unit tests for VaultRepository
 * Test Strategy: CRUD operations, identity resolution logic, sorting by last opened date, database migrations, and data integrity constraints
 */
class VaultRepositoryTest {

    private lateinit var vaultDao: VaultDao
    private lateinit var savedTabRepository: SavedTabRepository
    private lateinit var mapMarkerManager: MapMarkerManager
    private lateinit var mapViewStateManager: MapViewStateManager
    private lateinit var guideViewStateManager: GuideViewStateManager
    private lateinit var checklistRepository: ChecklistRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var syncTombstoneDao: com.twinlore.app.data.local.SyncTombstoneDao
    private lateinit var resourceFileDao: com.twinlore.app.data.local.ResourceFileDao
    private lateinit var artworkThumbnailCache: com.twinlore.app.data.library.ArtworkThumbnailCache
    private lateinit var mapThumbnailCache: com.twinlore.app.data.library.MapThumbnailCache
    private lateinit var libraryFolderManager: com.twinlore.app.data.library.LibraryFolderManager
    private lateinit var fuzzyNameMatcher: FuzzyNameMatcher
    private lateinit var resourceResolver: com.twinlore.app.data.library.ResourceResolver
    private lateinit var repository: VaultRepository

    /**
     * Subclass that bypasses the Room [withTransaction] seam — identical to
     * the pattern used in [AchievementSyncRaBulkTest].
     */
    private inner class TestVaultRepository(
        context: Context,
        vaultDao: VaultDao,
        savedTabRepository: SavedTabRepository,
        mapMarkerManager: MapMarkerManager,
        mapViewStateManager: MapViewStateManager,
        guideViewStateManager: GuideViewStateManager,
        fuzzyNameMatcher: FuzzyNameMatcher,
        checklistRepository: ChecklistRepository,
        settingsRepository: SettingsRepository,
        resourceFileDao: com.twinlore.app.data.local.ResourceFileDao,
        libraryFolderManager: com.twinlore.app.data.library.LibraryFolderManager,
        resourceResolver: com.twinlore.app.data.library.ResourceResolver,
        artworkThumbnailCache: com.twinlore.app.data.library.ArtworkThumbnailCache,
        mapThumbnailCache: com.twinlore.app.data.library.MapThumbnailCache,
        syncTombstoneDao: com.twinlore.app.data.local.SyncTombstoneDao,
        database: VaultDatabase,
    ) : VaultRepository(
        context = context,
        vaultDao = vaultDao,
        savedTabRepository = savedTabRepository,
        mapMarkerManager = mapMarkerManager,
        mapViewStateManager = mapViewStateManager,
        guideViewStateManager = guideViewStateManager,
        fuzzyNameMatcher = fuzzyNameMatcher,
        checklistRepository = checklistRepository,
        settingsRepository = settingsRepository,
        resourceFileDao = resourceFileDao,
        libraryFolderManager = libraryFolderManager,
        resourceResolver = resourceResolver,
        artworkThumbnailCache = artworkThumbnailCache,
        mapThumbnailCache = mapThumbnailCache,
        syncTombstoneDao = syncTombstoneDao,
        database = database,
    ) {
        override suspend fun runDbTransaction(block: suspend () -> Unit) {
            // Skip real Room transaction wrapper; just invoke the block.
            block()
        }
    }

    @Before
    fun setup() {
        vaultDao = mock()
        savedTabRepository = mock()
        mapMarkerManager = mock()
        mapViewStateManager = mock()
        guideViewStateManager = mock()
        checklistRepository = mock()
        settingsRepository = mock()
        syncTombstoneDao = mock()
        resourceFileDao = mock()
        artworkThumbnailCache = mock()
        mapThumbnailCache = mock()
        libraryFolderManager = mock()
        fuzzyNameMatcher = mock()
        resourceResolver = mock()
        repository = TestVaultRepository(
            context = mock(),
            vaultDao = vaultDao,
            savedTabRepository = savedTabRepository,
            mapMarkerManager = mapMarkerManager,
            mapViewStateManager = mapViewStateManager,
            guideViewStateManager = guideViewStateManager,
            fuzzyNameMatcher = fuzzyNameMatcher,
            checklistRepository = checklistRepository,
            settingsRepository = settingsRepository,
            resourceFileDao = resourceFileDao,
            libraryFolderManager = libraryFolderManager,
            resourceResolver = resourceResolver,
            artworkThumbnailCache = artworkThumbnailCache,
            mapThumbnailCache = mapThumbnailCache,
            syncTombstoneDao = syncTombstoneDao,
            database = mock(),
        )
    }

    @Test
    fun `getAllVaultsSortedByLastOpened returns sorted vaults`() = runTest {
        val entity1 = createTestEntity("vault-1", "Game 1", lastOpened = Date(1000))
        val entity2 = createTestEntity("vault-2", "Game 2", lastOpened = Date(2000))
        // DAO returns sorted by lastOpened DESC, so most recent first
        val entities = listOf(entity2, entity1) // vault-2 (2000) comes before vault-1 (1000)
        
        val flow: Flow<List<VaultEntity>> = flowOf(entities)
        whenever(vaultDao.getAllVaultsSortedByLastOpened()).thenReturn(flow)
        
        val vaults = repository.getAllVaultsSortedByLastOpened().first()
        
        assertEquals(2, vaults.size)
        assertEquals("vault-2", vaults[0].id) // Most recently opened first
        assertEquals("vault-1", vaults[1].id)
    }

    @Test
    fun `getAllVaults returns all vaults`() {
        runBlocking {
            val entities = listOf(
                createTestEntity("vault-1", "Game 1"),
                createTestEntity("vault-2", "Game 2")
            )
            
            doAnswer { entities }.whenever(vaultDao).getAllVaults()
            
            val vaults = repository.getAllVaults()
            
            assertEquals(2, vaults.size)
        }
    }

    @Test
    fun `getVaultById returns vault when exists`() {
        runBlocking {
            val entity = createTestEntity("vault-1", "Game 1")
            doAnswer { entity }.whenever(vaultDao).getVaultById("vault-1")
            
            val vault = repository.getVaultById("vault-1")
            
            assertNotNull(vault)
            assertEquals("vault-1", vault?.id)
            assertEquals("Game 1", vault?.name)
        }
    }

    @Test
    fun `getVaultById returns null when not exists`() {
        runBlocking {
            doAnswer { null }.whenever(vaultDao).getVaultById("nonexistent")
            
            val vault = repository.getVaultById("nonexistent")
            
            assertNull(vault)
        }
    }

    @Test
    fun `resolveVaultIdentity finds vault by ROM path priority 1`() {
        runBlocking {
            val entity = createTestEntity("vault-1", "Game 1", romPath = "/path/to/game.rom")
            doAnswer { entity }.whenever(vaultDao).findVaultByRomPath("/path/to/game.rom")
            
            val vault = repository.resolveVaultIdentity(
                romPath = "/path/to/game.rom",
                romFilename = "game.rom"
            )
            
            assertNotNull(vault)
            assertEquals("vault-1", vault?.id)
            verify(vaultDao).findVaultByRomPath("/path/to/game.rom")
            verify(vaultDao, never()).findVaultByRomFilename(any())
        }
    }

    @Test
    fun `resolveVaultIdentity finds vault by ROM filename priority 2`() {
        runBlocking {
            val entity = createTestEntity("vault-1", "Game 1", romFilename = "game.rom")
            doAnswer { null }.whenever(vaultDao).findVaultByRomPath(any())
            doAnswer { entity }.whenever(vaultDao).findVaultByRomFilename("game.rom")
            
            val vault = repository.resolveVaultIdentity(
                romFilename = "game.rom"
            )
            
            assertNotNull(vault)
            assertEquals("vault-1", vault?.id)
            verify(vaultDao).findVaultByRomFilename("game.rom")
        }
    }

    // ── findVaultForGame: the read-side twin of createVaultWithFuzzyCheck ──────
    // The live view binds a game to a vault by path, then filename, then name. Reading the
    // binding back used to stop after filename, so a vault matched by name (a library-scanned
    // vault whose ROM path ES-DE does not know) resolved on open and vanished on read. That is
    // what kept freshly assigned achievements out of the live view.

    @Test
    fun `findVaultForGame falls back to the name when path and filename miss`() {
        runBlocking {
            val entity = createTestEntity("vault-1", "Chrono Trigger")
            doAnswer { null }.whenever(vaultDao).findVaultByRomPath(any())
            doAnswer { null }.whenever(vaultDao).findVaultByRomFilename(any())
            doAnswer { listOf(entity) }.whenever(vaultDao).getAllVaults()
            val domain = VaultEntityMapper.toDomain(entity)
            doAnswer { FuzzyNameMatcher.MatchResult(domain, 1.0) }
                .whenever(fuzzyNameMatcher).findBestMatch(eq("Chrono Trigger"), any(), any())

            val vault = repository.findVaultForGame(
                romPath = "/roms/snes/ct.sfc",
                romFilename = "ct.sfc",
                name = "Chrono Trigger",
            )

            assertNotNull(vault)
            assertEquals("vault-1", vault?.id)
        }
    }

    @Test
    fun `findVaultForGame prefers an exact ROM path over the name`() {
        runBlocking {
            val byPath = createTestEntity("vault-path", "Game 1", romPath = "/roms/snes/ct.sfc")
            doAnswer { byPath }.whenever(vaultDao).findVaultByRomPath("/roms/snes/ct.sfc")

            val vault = repository.findVaultForGame(
                romPath = "/roms/snes/ct.sfc",
                romFilename = "ct.sfc",
                name = "Chrono Trigger",
            )

            assertEquals("vault-path", vault?.id)
            verify(fuzzyNameMatcher, never()).findBestMatch(any(), any(), any())
        }
    }

    @Test
    fun `findVaultForGame returns null when nothing matches, and never creates`() {
        runBlocking {
            doAnswer { null }.whenever(vaultDao).findVaultByRomPath(any())
            doAnswer { null }.whenever(vaultDao).findVaultByRomFilename(any())
            doAnswer { emptyList<VaultEntity>() }.whenever(vaultDao).getAllVaults()
            doAnswer { null }.whenever(fuzzyNameMatcher).findBestMatch(any(), any(), any())

            val vault = repository.findVaultForGame(
                romPath = "/roms/snes/ct.sfc",
                romFilename = "ct.sfc",
                name = "Chrono Trigger",
            )

            assertNull(vault)
            verify(vaultDao, never()).insertVault(any())
        }
    }

    @Test
    fun `resolveVaultIdentity returns null when not found`() {
        runBlocking {
            doAnswer { null }.whenever(vaultDao).findVaultByRomPath(any())
            doAnswer { null }.whenever(vaultDao).findVaultByRomFilename(any())
            
            val vault = repository.resolveVaultIdentity(
                romPath = "/path/to/game.rom",
                romFilename = "game.rom"
            )
            
            assertNull(vault)
        }
    }

    @Test
    fun `createVault creates new vault when not exists`() {
        runBlocking {
            doAnswer { null }.whenever(vaultDao).findVaultByRomPath(any())
            doAnswer { null }.whenever(vaultDao).findVaultByRomFilename(any())
            doAnswer { emptyList<com.twinlore.app.data.local.VaultEntity>() }.whenever(vaultDao).getAllVaults()
            doAnswer { }.whenever(vaultDao).insertVault(any())

            val result = repository.createVault(
                name = "New Game",
                romPath = "/path/to/game.rom",
                romFilename = "game.rom"
            )

            assertTrue(result.isSuccess())
            val vault = result.getOrNull()
            assertNotNull(vault)
            assertEquals("New Game", vault?.name)
            assertEquals("/path/to/game.rom", vault?.romPath)
            verify(vaultDao).insertVault(any())
        }
    }

    @Test
    fun `createVault returns existing vault when found`() {
        runBlocking {
            val entity = createTestEntity("vault-1", "Existing Game", romPath = "/path/to/game.rom")
            doAnswer { entity }.whenever(vaultDao).findVaultByRomPath("/path/to/game.rom")
            
            val result = repository.createVault(
                name = "New Game",
                romPath = "/path/to/game.rom"
            )
            
            assertTrue(result.isSuccess())
            val vault = result.getOrNull()
            assertEquals("vault-1", vault?.id)
            assertEquals("Existing Game", vault?.name)
            verify(vaultDao, never()).insertVault(any())
        }
    }

    // The vault the app reopens on a foreground is read from
    // now_playing_vault_id, and before this it was only ever written by
    // detection. So adding a game by hand left the record pointing at the last
    // *played* game, and the next return to the foreground threw the user back
    // into that one rather than into what they had just added. Reproduced on
    // the Thor: create "Zzz Probe Game", background, foreground, land in
    // Castlevania.
    @Test
    fun `a newly created vault becomes the one the app reopens`() {
        runBlocking {
            doAnswer { null }.whenever(vaultDao).findVaultByRomPath(any())
            doAnswer { null }.whenever(vaultDao).findVaultByRomFilename(any())
            doAnswer { emptyList<com.twinlore.app.data.local.VaultEntity>() }
                .whenever(vaultDao).getAllVaults()
            doAnswer { }.whenever(vaultDao).insertVault(any())

            val result = repository.createVault(name = "Zzz Probe Game")

            assertTrue(result.isSuccess())
            verify(settingsRepository).setNowPlayingVaultId(result.getOrNull()!!.id)
        }
    }

    // A creation that resolved to a vault that already exists is not a new
    // game, so the record must not move: adding a duplicate name would
    // otherwise silently retarget the auto-open onto it.
    @Test
    fun `a creation that resolves to an existing vault leaves the record alone`() {
        runBlocking {
            val entity = createTestEntity("vault-1", "Existing Game", romPath = "/path/to/game.rom")
            doAnswer { entity }.whenever(vaultDao).findVaultByRomPath("/path/to/game.rom")

            repository.createVault(name = "New Game", romPath = "/path/to/game.rom")

            verify(settingsRepository, never()).setNowPlayingVaultId(any())
        }
    }

    @Test
    fun `createVault handles errors`() {
        runBlocking {
            doAnswer { null }.whenever(vaultDao).findVaultByRomPath(any())
            doAnswer { null }.whenever(vaultDao).findVaultByRomFilename(any())
            doAnswer { throw RuntimeException("Database error") }.whenever(vaultDao).insertVault(any())
            
            val result = repository.createVault(name = "New Game")
            
            assertTrue(result.isFailure())
            assertTrue(result is Result.Failure)
        }
    }

    @Test
    fun `insertVault inserts successfully`() {
        runBlocking {
            val vault = createTestVault("vault-1", "Game 1")
            doAnswer { }.whenever(vaultDao).insertVault(any())
            
            val result = repository.insertVault(vault)
            
            assertTrue(result.isSuccess())
            verify(vaultDao).insertVault(any())
        }
    }

    @Test
    fun `updateVault updates successfully`() {
        runBlocking {
            val vault = createTestVault("vault-1", "Updated Game")
            doAnswer { }.whenever(vaultDao).updateVault(any())
            
            val result = repository.updateVault(vault)
            
            assertTrue(result.isSuccess())
            verify(vaultDao).updateVault(any())
        }
    }

    @Test
    fun `deleteVault deletes successfully`() {
        runBlocking {
            doAnswer { }.whenever(vaultDao).deleteVaultById("vault-1")
            whenever(settingsRepository.getOrCreateDeviceId()).thenReturn("test-device-id")
            whenever(resourceFileDao.getForVaultAndKindOnce(eq("vault-1"), any()))
                .thenReturn(emptyList())

            val result = repository.deleteVault("vault-1")

            assertTrue(result.isSuccess())
            verify(vaultDao).deleteVaultById("vault-1")
            verify(syncTombstoneDao).upsert(argThat { vaultId == "vault-1" })
        }
    }

    @Test
    fun `deleteVault removes the vault's on-disk folder`() {
        runBlocking {
            val entity = createTestEntity("vault-1", "Super Metroid")
            whenever(vaultDao.getVaultById("vault-1")).thenReturn(entity)
            doAnswer { }.whenever(vaultDao).deleteVaultById("vault-1")
            whenever(settingsRepository.getOrCreateDeviceId()).thenReturn("test-device-id")
            whenever(resourceFileDao.getForVaultAndKindOnce(eq("vault-1"), any()))
                .thenReturn(emptyList())

            val result = repository.deleteVault("vault-1")

            assertTrue(result.isSuccess())
            // The dedicated folder (resolveFolderName -> sanitized name) is deleted;
            // linked files in other folders are outside this dir and untouched.
            verify(libraryFolderManager).deleteChildDir("Super Metroid")
        }
    }

    @Test
    fun `updateLastOpened updates timestamp`() {
        runBlocking {
            doAnswer { }.whenever(vaultDao).updateLastOpened(eq("vault-1"), any())
            
            val result = repository.updateLastOpened("vault-1")
            
            assertTrue(result.isSuccess())
            verify(vaultDao).updateLastOpened(eq("vault-1"), any())
        }
    }

    @Test
    fun `updateArtworkResourceId delegates to dao`() {
        runBlocking {
            doAnswer { }.whenever(vaultDao).updateArtworkResourceId(eq("vault-1"), any())

            val result = repository.updateArtworkResourceId("vault-1", "resource-123")

            assertTrue(result.isSuccess())
            verify(vaultDao).updateArtworkResourceId(eq("vault-1"), eq("resource-123"))
        }
    }

    /**
     * The library rebuilds a vault's tile the moment the new artwork pointer lands in
     * the DB, and the tile reads the thumb by vault id only. If the thumb still holds
     * the PREVIOUS artwork at that moment, Coil memory-caches the old image under the
     * new resource id's key, and it resurfaces the next time the tile's cache buster
     * resets (assigning artwork to any other vault does that). So the thumb must be
     * refreshed before the pointer write, never after.
     */
    @Test
    fun `updateArtworkResourceId regenerates the thumb before writing the pointer`() {
        runBlocking {
            val uri: android.net.Uri = mock()
            whenever(resourceResolver.resolve("resource-123")).thenReturn(uri)

            val result = repository.updateArtworkResourceId("vault-1", "resource-123")

            assertTrue(result.isSuccess())
            inOrder(artworkThumbnailCache, vaultDao) {
                verify(artworkThumbnailCache).generate("vault-1", uri)
                verify(vaultDao).updateArtworkResourceId("vault-1", "resource-123")
            }
        }
    }

    /** When the new source can't be resolved, the stale thumb is dropped rather than left to be served under the new id. */
    @Test
    fun `updateArtworkResourceId drops the stale thumb before writing the pointer when generate is impossible`() {
        runBlocking {
            whenever(resourceResolver.resolve("resource-123")).thenReturn(null)

            val result = repository.updateArtworkResourceId("vault-1", "resource-123")

            assertTrue(result.isSuccess())
            inOrder(artworkThumbnailCache, vaultDao) {
                verify(artworkThumbnailCache).invalidate("vault-1")
                verify(vaultDao).updateArtworkResourceId("vault-1", "resource-123")
            }
            verify(artworkThumbnailCache, never()).generate(any(), any())
        }
    }

    @Test
    fun `renameVault renames successfully`() {
        runBlocking {
            val entity = createTestEntity("vault-1", "Old Name")
            doAnswer { entity }.whenever(vaultDao).getVaultById("vault-1")
            doAnswer { }.whenever(vaultDao).updateVault(any())
            
            val result = repository.renameVault("vault-1", "New Name")
            
            assertTrue(result.isSuccess())
            verify(vaultDao).updateVault(argThat { name == "New Name" })
        }
    }

    @Test
    fun `vaultExists returns true when exists`() {
        runBlocking {
            doAnswer { true }.whenever(vaultDao).vaultExists("vault-1")
            
            val exists = repository.vaultExists("vault-1")
            
            assertTrue(exists)
        }
    }

    @Test
    fun `vaultExists returns false when not exists`() {
        runBlocking {
            doAnswer { false }.whenever(vaultDao).vaultExists("nonexistent")
            
            val exists = repository.vaultExists("nonexistent")
            
            assertFalse(exists)
        }
    }

    // Helper functions
    private fun createTestEntity(
        id: String,
        name: String,
        romPath: String? = null,
        romFilename: String? = null,
        lastOpened: Date? = null
    ): VaultEntity {
        return VaultEntity(
            id = id,
            name = name,
            romPath = romPath,
            romFilename = romFilename,
            artworkVibrantArgb = null,
            systemeid = null,
            gameId = null,
            source = null,
            raGameId = null,
            steamAppId = null,
            lastOpened = lastOpened,
            createdAt = Date()
        )
    }

    private fun createTestVault(
        id: String,
        name: String,
        romPath: String? = null
    ): Vault {
        return Vault(
            id = id,
            name = name,
            romPath = romPath,
            romFilename = null,
            systemeid = null,
            gameId = null,
            source = null,
            raGameId = null,
            lastOpened = null,
            createdAt = Date()
        )
    }
}
