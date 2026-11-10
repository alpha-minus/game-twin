package com.twinlore.app.data

import com.twinlore.app.data.local.RaGameCacheDao
import com.twinlore.app.data.local.RaGameCacheEntity
import com.twinlore.app.data.local.VaultDatabase
import com.twinlore.app.data.retroachievements.RaCredentials
import com.twinlore.app.data.retroachievements.RaUserCompletionProgressEntry
import com.twinlore.app.data.retroachievements.RaUserCompletionProgressResponse
import com.twinlore.app.data.retroachievements.RetroAchievementsApi
import com.twinlore.app.data.retroachievements.RetroAchievementsApiClient
import com.twinlore.app.data.retroachievements.RetroAchievementsCredentialsRepository
import com.twinlore.app.data.steam.SteamApiClient
import com.twinlore.app.data.steam.SteamCredentialsRepository
import com.twinlore.app.data.steam.SteamGameCacheRepository
import com.twinlore.app.domain.Vault
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Date

/**
 * Behavioural tests for [AchievementSyncCoordinator.syncRaBulk].
 *
 * Strategy: cover the merge semantics, pagination, and the no-link guard.
 * Room's `withTransaction` extension function is unmockable here, so we
 * subclass the coordinator and override the [runInDbTransaction] seam.
 * The integration test on device exercises the real transaction wrapper.
 */
class AchievementSyncRaBulkTest {

    private fun makeVault(
        id: String,
        raGameId: String?,
    ): Vault = Vault(
        id = id,
        name = id,
        romPath = null,
        romFilename = null,
        systemeid = null,
        gameId = null,
        source = null,
        raGameId = raGameId,
        steamAppId = null,
        lastOpened = null,
        createdAt = Date(0),
    )

    private fun makeCreds(): RaCredentials = RaCredentials(username = "u", apiKey = "k")

    private fun makeEntry(
        gameId: Int,
        numAwarded: Int? = 0,
        numAwardedHardcore: Int? = 0,
        maxPossible: Int? = 0,
        highestAwardKind: String? = null,
    ): RaUserCompletionProgressEntry = RaUserCompletionProgressEntry(
        gameId = gameId,
        numAwarded = numAwarded,
        numAwardedHardcore = numAwardedHardcore,
        maxPossible = maxPossible,
        highestAwardKind = highestAwardKind,
    )

    private class TestCoordinator(
        raCredentialsRepository: RetroAchievementsCredentialsRepository,
        raApiClient: RetroAchievementsApiClient,
        raGameCacheDao: RaGameCacheDao,
        steamCredentialsRepository: SteamCredentialsRepository,
        steamApiClient: SteamApiClient,
        steamGameCacheRepository: SteamGameCacheRepository,
        vaultRepository: VaultRepository,
        settingsRepository: SettingsRepository,
        appDatabase: VaultDatabase,
    ) : AchievementSyncCoordinator(
        raCredentialsRepository = raCredentialsRepository,
        raApiClient = raApiClient,
        raGameCacheDao = raGameCacheDao,
        steamCredentialsRepository = steamCredentialsRepository,
        steamApiClient = steamApiClient,
        steamGameCacheRepository = steamGameCacheRepository,
        vaultRepository = vaultRepository,
        settingsRepository = settingsRepository,
        appDatabase = appDatabase,
    ) {
        override suspend fun runInDbTransaction(block: suspend () -> Unit) {
            // Skip real Room transaction wrapper; just invoke the block.
            block()
        }
    }

    private fun buildCoordinator(
        raCredsRepo: RetroAchievementsCredentialsRepository,
        raApi: RetroAchievementsApi,
        dao: RaGameCacheDao,
        vaults: List<Vault>,
    ): AchievementSyncCoordinator {
        val raClient = mock<RetroAchievementsApiClient> { on { api } doReturn raApi }
        val vaultRepo = mock<VaultRepository> {
            onBlocking { getAllVaults() } doReturn vaults
        }
        val settings = mock<SettingsRepository> {
            on { lastAchievementSyncAt } doReturn flowOf(0L) // long-expired
        }
        val steamCredsRepo = mock<SteamCredentialsRepository> {
            onBlocking { getCredentials() } doReturn null
        }
        return TestCoordinator(
            raCredentialsRepository = raCredsRepo,
            raApiClient = raClient,
            raGameCacheDao = dao,
            steamCredentialsRepository = steamCredsRepo,
            steamApiClient = mock<SteamApiClient>(),
            steamGameCacheRepository = mock<SteamGameCacheRepository>(),
            vaultRepository = vaultRepo,
            settingsRepository = settings,
            appDatabase = mock<VaultDatabase>(),
        )
    }

    @Test
    fun bulk_updates_existing_row_preserving_jsonSnapshot_and_lastSyncedAt() = runTest {
        val raCreds = mock<RetroAchievementsCredentialsRepository> {
            onBlocking { getCredentials() } doReturn makeCreds()
        }
        val existing = RaGameCacheEntity(
            vaultId = "v1",
            raGameId = 555,
            jsonSnapshot = """{"snapshot":"yes"}""",
            lastSyncedAt = 9999L,
            highestAwardKind = "beaten-softcore",
            userRank = 42,
            numAwardedSoft = 1,
            numAwardedHardcore = 0,
            numAchievements = 10,
        )
        val dao = mock<RaGameCacheDao> {
            onBlocking { getByVaultId("v1") } doReturn existing
        }
        val raApi = mock<RetroAchievementsApi> {
            onBlocking { getUserCompletionProgress(eq("u"), eq("k"), any(), eq(0)) } doReturn
                RaUserCompletionProgressResponse(
                    count = 1,
                    total = 1,
                    results = listOf(
                        makeEntry(
                            gameId = 555,
                            numAwarded = 7,
                            numAwardedHardcore = 3,
                            maxPossible = 20,
                            highestAwardKind = "mastered",
                        )
                    )
                )
        }
        val vaults = listOf(makeVault("v1", "555"))

        val coord = buildCoordinator(raCreds, raApi, dao, vaults)
        coord.syncAtStartup()

        val captor = argumentCaptor<RaGameCacheEntity>()
        verify(dao).insert(captor.capture())
        val written = captor.firstValue

        assertEquals("v1", written.vaultId)
        assertEquals(555, written.raGameId)
        // Preserved fields:
        assertEquals("""{"snapshot":"yes"}""", written.jsonSnapshot)
        assertEquals(9999L, written.lastSyncedAt)
        assertEquals(42, written.userRank)
        // Overwritten count columns:
        assertEquals(7, written.numAwardedSoft)
        assertEquals(3, written.numAwardedHardcore)
        assertEquals(20, written.numAchievements)
        // highestAwardKind overwritten from bulk response:
        assertEquals("mastered", written.highestAwardKind)
    }

    @Test
    fun bulk_inserts_counts_only_row_when_no_existing_cache_for_linked_vault() = runTest {
        val raCreds = mock<RetroAchievementsCredentialsRepository> {
            onBlocking { getCredentials() } doReturn makeCreds()
        }
        val dao = mock<RaGameCacheDao> {
            onBlocking { getByVaultId("v2") } doReturn null
        }
        val raApi = mock<RetroAchievementsApi> {
            onBlocking { getUserCompletionProgress(eq("u"), eq("k"), any(), eq(0)) } doReturn
                RaUserCompletionProgressResponse(
                    count = 1,
                    total = 1,
                    results = listOf(
                        makeEntry(
                            gameId = 777,
                            numAwarded = 4,
                            numAwardedHardcore = 1,
                            maxPossible = 12,
                            highestAwardKind = "beaten-hardcore",
                        )
                    )
                )
        }
        val vaults = listOf(makeVault("v2", "777"))

        val coord = buildCoordinator(raCreds, raApi, dao, vaults)
        coord.syncAtStartup()

        val captor = argumentCaptor<RaGameCacheEntity>()
        verify(dao).insert(captor.capture())
        val written = captor.firstValue

        assertEquals("v2", written.vaultId)
        assertEquals(777, written.raGameId)
        assertNull(written.jsonSnapshot)
        assertEquals(0L, written.lastSyncedAt)
        assertNull(written.userRank)
        assertEquals(4, written.numAwardedSoft)
        assertEquals(1, written.numAwardedHardcore)
        assertEquals(12, written.numAchievements)
        assertEquals("beaten-hardcore", written.highestAwardKind)
    }

    @Test
    fun bulk_skips_games_with_no_linked_vault() = runTest {
        val raCreds = mock<RetroAchievementsCredentialsRepository> {
            onBlocking { getCredentials() } doReturn makeCreds()
        }
        val dao = mock<RaGameCacheDao>()
        val raApi = mock<RetroAchievementsApi> {
            onBlocking { getUserCompletionProgress(eq("u"), eq("k"), any(), eq(0)) } doReturn
                RaUserCompletionProgressResponse(
                    count = 2,
                    total = 2,
                    results = listOf(
                        makeEntry(gameId = 1, numAwarded = 1, maxPossible = 5),
                        makeEntry(gameId = 2, numAwarded = 2, maxPossible = 10),
                    )
                )
        }
        // None of the vaults link to RA.
        val vaults = listOf(
            makeVault("v1", raGameId = null),
            makeVault("v2", raGameId = null),
        )

        val coord = buildCoordinator(raCreds, raApi, dao, vaults)
        coord.syncAtStartup()

        verify(dao, never()).insert(any())
    }

    @Test
    fun bulk_paginates_until_page_below_size() = runTest {
        val raCreds = mock<RetroAchievementsCredentialsRepository> {
            onBlocking { getCredentials() } doReturn makeCreds()
        }
        // Page 1: 500 entries — none of which match the single linked vault.
        // Page 2: 1 entry — matches the linked vault (raGameId 999).
        val page1 = RaUserCompletionProgressResponse(
            count = 500,
            total = 501,
            results = (1..500).map { makeEntry(gameId = it, numAwarded = 0, maxPossible = 0) }
        )
        val page2 = RaUserCompletionProgressResponse(
            count = 1,
            total = 501,
            results = listOf(
                makeEntry(
                    gameId = 999,
                    numAwarded = 5,
                    numAwardedHardcore = 0,
                    maxPossible = 5,
                    highestAwardKind = "completed",
                )
            )
        )
        val raApi = mock<RetroAchievementsApi>()
        whenever(raApi.getUserCompletionProgress(eq("u"), eq("k"), any(), eq(0))).thenReturn(page1)
        whenever(raApi.getUserCompletionProgress(eq("u"), eq("k"), any(), eq(500))).thenReturn(page2)

        val dao = mock<RaGameCacheDao> {
            onBlocking { getByVaultId("vp") } doReturn null
        }
        val vaults = listOf(makeVault("vp", "999"))

        val coord = buildCoordinator(raCreds, raApi, dao, vaults)
        coord.syncAtStartup()

        // Exactly two API calls: offset=0 and offset=500. No call at offset=1000.
        verify(raApi).getUserCompletionProgress(eq("u"), eq("k"), any(), eq(0))
        verify(raApi).getUserCompletionProgress(eq("u"), eq("k"), any(), eq(500))
        verify(raApi, never()).getUserCompletionProgress(eq("u"), eq("k"), any(), eq(1000))

        // The page-2 entry that matched the linked vault was written.
        val captor = argumentCaptor<RaGameCacheEntity>()
        verify(dao).insert(captor.capture())
        val written = captor.firstValue
        assertEquals("vp", written.vaultId)
        assertEquals(999, written.raGameId)
        assertEquals(5, written.numAwardedSoft)
        assertEquals(5, written.numAchievements)
        assertEquals("completed", written.highestAwardKind)
        assertNotNull(written) // sanity
    }
}
