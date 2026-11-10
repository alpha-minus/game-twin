package com.twinlore.app.data

import com.twinlore.app.data.local.RaGameCacheDao
import com.twinlore.app.data.local.VaultDatabase
import com.twinlore.app.data.retroachievements.RaCredentials
import com.twinlore.app.data.retroachievements.RetroAchievementsApiClient
import com.twinlore.app.data.retroachievements.RetroAchievementsCredentialsRepository
import com.twinlore.app.data.steam.SteamApiClient
import com.twinlore.app.data.steam.SteamCredentials
import com.twinlore.app.data.steam.SteamCredentialsRepository
import com.twinlore.app.data.steam.SteamGameCacheRepository
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

/**
 * TTL gate + stamping-rule unit tests for
 * [AchievementSyncCoordinator.syncAtStartup]. The two sync methods
 * ([AchievementSyncCoordinator.syncRaBulk] and
 * [AchievementSyncCoordinator.syncSteamRecent]) are covered by their own
 * test classes.
 *
 * Scenarios pinned here:
 *  1. TTL not expired -> early return, no stamp written.
 *  2. TTL expired + no credentials configured -> stamp written
 *     (because the noCreds branch of the rule applies).
 */
class AchievementSyncCoordinatorTtlTest {

    private fun makeCoordinator(
        lastSync: Long,
        raCreds: RaCredentials? = null,
        steamCreds: SteamCredentials? = null,
    ): Pair<AchievementSyncCoordinator, SettingsRepository> {
        val settings = mock<SettingsRepository> {
            on { lastAchievementSyncAt } doReturn flowOf(lastSync)
        }
        val raCredsRepo = mock<RetroAchievementsCredentialsRepository> {
            onBlocking { getCredentials() } doReturn raCreds
        }
        val steamCredsRepo = mock<SteamCredentialsRepository> {
            onBlocking { getCredentials() } doReturn steamCreds
        }

        val coord = AchievementSyncCoordinator(
            raCredentialsRepository = raCredsRepo,
            raApiClient = mock<RetroAchievementsApiClient>(),
            raGameCacheDao = mock<RaGameCacheDao>(),
            steamCredentialsRepository = steamCredsRepo,
            steamApiClient = mock<SteamApiClient>(),
            steamGameCacheRepository = mock<SteamGameCacheRepository>(),
            vaultRepository = mock<VaultRepository>(),
            settingsRepository = settings,
            appDatabase = mock<VaultDatabase>(),
        )
        return coord to settings
    }

    @Test
    fun syncAtStartup_returns_early_when_within_TTL() = runTest {
        val now = System.currentTimeMillis()
        val (coord, settings) = makeCoordinator(lastSync = now - 60_000L) // 1 min ago
        coord.syncAtStartup()
        verify(settings, never()).setLastAchievementSyncAt(any())
    }

    @Test
    fun syncAtStartup_stamps_TTL_when_no_credentials_configured() = runTest {
        val now = System.currentTimeMillis()
        val (coord, settings) = makeCoordinator(lastSync = now - 7L * 60 * 60 * 1000L) // 7h ago
        coord.syncAtStartup()
        verify(settings).setLastAchievementSyncAt(any())
    }
}
