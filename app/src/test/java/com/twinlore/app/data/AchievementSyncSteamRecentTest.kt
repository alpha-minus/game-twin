package com.twinlore.app.data

import com.twinlore.app.data.local.RaGameCacheDao
import com.twinlore.app.data.local.VaultDatabase
import com.twinlore.app.data.retroachievements.RetroAchievementsApiClient
import com.twinlore.app.data.retroachievements.RetroAchievementsCredentialsRepository
import com.twinlore.app.data.steam.SteamApi
import com.twinlore.app.data.steam.SteamApiClient
import com.twinlore.app.data.steam.SteamCredentials
import com.twinlore.app.data.steam.SteamCredentialsRepository
import com.twinlore.app.data.steam.SteamGameCacheRepository
import com.twinlore.app.data.steam.SteamRecentGame
import com.twinlore.app.data.steam.SteamRecentlyPlayedResponse
import com.twinlore.app.data.steam.SteamRecentlyPlayedResult
import com.twinlore.app.domain.Vault
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.verifyNoInteractions
import java.util.Date

/**
 * Behavioural tests for [AchievementSyncCoordinator.syncSteamRecent].
 *
 * Verifies: top-5 cap on candidate set, pre-filter by linked steamAppId,
 * and the no-creds short-circuit (no API or repo call).
 */
class AchievementSyncSteamRecentTest {

    private fun makeVault(
        id: String,
        steamAppId: String?,
    ): Vault = Vault(
        id = id,
        name = id,
        romPath = null,
        romFilename = null,
        systemeid = null,
        gameId = null,
        source = null,
        raGameId = null,
        steamAppId = steamAppId,
        lastOpened = null,
        createdAt = Date(0),
    )

    private fun makeCreds(): SteamCredentials =
        SteamCredentials(vanityUrl = "vu", steamId = "76561198000000000")

    private fun recentResponse(appIds: List<Int>): SteamRecentlyPlayedResponse =
        SteamRecentlyPlayedResponse(
            response = SteamRecentlyPlayedResult(
                totalCount = appIds.size,
                games = appIds.map { SteamRecentGame(appId = it, name = "game$it") }
            )
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
            block()
        }
    }

    private data class Built(
        val coord: AchievementSyncCoordinator,
        val steamApi: SteamApi,
        val steamRepo: SteamGameCacheRepository,
    )

    private fun buildCoordinator(
        steamCreds: SteamCredentials?,
        recent: SteamRecentlyPlayedResponse?,
        vaults: List<Vault>,
    ): Built {
        val steamApi = mock<SteamApi> {
            if (recent != null) {
                onBlocking { getRecentlyPlayedGames(any(), any()) } doReturn recent
            }
        }
        val steamApiClient = mock<SteamApiClient> { on { api } doReturn steamApi }
        val steamCredsRepo = mock<SteamCredentialsRepository> {
            onBlocking { getCredentials() } doReturn steamCreds
        }
        val steamRepo = mock<SteamGameCacheRepository>()
        val vaultRepo = mock<VaultRepository> {
            onBlocking { getAllVaults() } doReturn vaults
        }
        val settings = mock<SettingsRepository> {
            on { lastAchievementSyncAt } doReturn flowOf(0L) // long-expired
        }
        val raCredsRepo = mock<RetroAchievementsCredentialsRepository> {
            onBlocking { getCredentials() } doReturn null
        }
        val coord = TestCoordinator(
            raCredentialsRepository = raCredsRepo,
            raApiClient = mock<RetroAchievementsApiClient>(),
            raGameCacheDao = mock<RaGameCacheDao>(),
            steamCredentialsRepository = steamCredsRepo,
            steamApiClient = steamApiClient,
            steamGameCacheRepository = steamRepo,
            vaultRepository = vaultRepo,
            settingsRepository = settings,
            appDatabase = mock<VaultDatabase>(),
        )
        return Built(coord, steamApi, steamRepo)
    }

    @Test
    fun syncs_only_linked_recently_played_games() = runTest {
        val built = buildCoordinator(
            steamCreds = makeCreds(),
            recent = recentResponse(listOf(11, 22, 33)),
            vaults = listOf(makeVault("vA", steamAppId = "22")),
        )
        built.coord.syncAtStartup()

        // Linked vault should have been fetched exactly once with forceRefresh=true.
        verifyBlocking(built.steamRepo) {
            getGameProgress(vaultId = eq("vA"), steamAppId = eq(22), forceRefresh = eq(true), minRefreshIntervalMs = anyOrNull())
        }
        // Unlinked appIds 11 and 33 must NOT trigger a fetch.
        verifyBlocking(built.steamRepo, never()) {
            getGameProgress(vaultId = any(), steamAppId = eq(11), forceRefresh = any(), minRefreshIntervalMs = anyOrNull())
        }
        verifyBlocking(built.steamRepo, never()) {
            getGameProgress(vaultId = any(), steamAppId = eq(33), forceRefresh = any(), minRefreshIntervalMs = anyOrNull())
        }
    }

    @Test
    fun take5_caps_the_candidate_set() = runTest {
        val appIds = (1..10).toList()
        val vaults = appIds.map { makeVault("v$it", steamAppId = it.toString()) }
        val built = buildCoordinator(
            steamCreds = makeCreds(),
            recent = recentResponse(appIds),
            vaults = vaults,
        )
        built.coord.syncAtStartup()

        // First five recents should be fetched.
        for (i in 1..5) {
            verifyBlocking(built.steamRepo) {
                getGameProgress(vaultId = eq("v$i"), steamAppId = eq(i), forceRefresh = eq(true), minRefreshIntervalMs = anyOrNull())
            }
        }
        // The tail (6..10) must NOT be fetched.
        for (i in 6..10) {
            verifyBlocking(built.steamRepo, never()) {
                getGameProgress(vaultId = eq("v$i"), steamAppId = eq(i), forceRefresh = any(), minRefreshIntervalMs = anyOrNull())
            }
        }
    }

    @Test
    fun no_creds_skips_all_steam_work() = runTest {
        val built = buildCoordinator(
            steamCreds = null,
            recent = null,
            vaults = listOf(makeVault("vA", steamAppId = "22")),
        )
        built.coord.syncAtStartup()

        // Steam API must not be called at all.
        verify(built.steamApi, never()).getRecentlyPlayedGames(any(), any())
        // Steam repo must not be called at all.
        verifyNoInteractions(built.steamRepo)
    }
}
