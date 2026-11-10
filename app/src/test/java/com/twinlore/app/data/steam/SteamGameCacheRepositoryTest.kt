package com.twinlore.app.data.steam

import com.twinlore.app.data.local.SteamGameCacheDao
import com.twinlore.app.data.local.SteamGameCacheEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Verifies that the drawer-write path of SteamGameCacheRepository populates
 * the dedicated numUnlocked / numAchievements columns on SteamGameCacheEntity
 * (so the library-bar hot path can read them without parsing jsonSnapshot).
 */
class SteamGameCacheRepositoryTest {

    @Test
    fun getGameProgress_writes_new_count_columns_alongside_jsonSnapshot() = runTest {
        val dao = mock<SteamGameCacheDao>()
        val apiClient = mock<SteamApiClient>()
        val credsRepo = mock<SteamCredentialsRepository>()
        val api = mock<SteamApi>()

        whenever(credsRepo.getCredentials())
            .thenReturn(SteamCredentials(vanityUrl = "tester", steamId = "76561197960287930"))
        whenever(dao.getByVaultId(any())).thenReturn(null)
        whenever(apiClient.api).thenReturn(api)

        // Schema: 10 achievements.
        whenever(api.getSchemaForGame(eq(123), any())).thenReturn(
            SteamSchemaResponse(
                game = SteamSchemaGame(
                    gameName = "TestGame",
                    availableGameStats = SteamGameStats(
                        achievements = (1..10).map { i ->
                            SteamSchemaAchievement(
                                name = "ach$i",
                                displayName = "A$i",
                                description = "d",
                                icon = "",
                                iconGray = "",
                                hidden = 0,
                            )
                        }
                    )
                )
            )
        )

        // Player: 4 unlocked.
        whenever(api.getPlayerAchievements(any(), eq(123), any())).thenReturn(
            SteamPlayerAchievementsResponse(
                playerStats = SteamPlayerStats(
                    gameName = "TestGame",
                    achievements = (1..10).map { i ->
                        SteamPlayerAchievement(
                            apiname = "ach$i",
                            achieved = if (i <= 4) 1 else 0,
                            unlocktime = if (i <= 4) 100L else 0L,
                        )
                    }
                )
            )
        )

        // Global percentages are optional; throw to mimic real "data unavailable" path.
        whenever(api.getGlobalAchievementPercentages(eq(123)))
            .thenThrow(RuntimeException("optional"))

        val repo = SteamGameCacheRepository(dao, apiClient, credsRepo)

        repo.getGameProgress(vaultId = "v1", steamAppId = 123)

        val captor = argumentCaptor<SteamGameCacheEntity>()
        verify(dao).insert(captor.capture())
        val written = captor.firstValue

        assertEquals("v1", written.vaultId)
        assertEquals(123, written.steamAppId)
        assertEquals(4, written.numUnlocked)
        assertEquals(10, written.numAchievements)
    }
}
