package com.twinlore.app.data.steam

import com.twinlore.app.data.local.SteamGameCacheDao
import com.twinlore.app.data.local.SteamGameCacheEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

/**
 * "N% of players" survives a cache read.
 *
 * The snapshot held the schema and the player's unlocks but not the global
 * percentages, so every read that did not go to the network came back with no
 * rarity at all: opening a Steam game showed no percentages until Refresh was
 * pressed, and folding back to the cache dropped them again.
 */
class SteamCachedPercentTest {

    private val schema = SteamSchemaResponse(
        game = SteamSchemaGame(
            gameName = "Isaac",
            availableGameStats = SteamGameStats(
                achievements = listOf(
                    SteamSchemaAchievement(name = "a1", displayName = "One", description = "d"),
                    SteamSchemaAchievement(name = "a2", displayName = "Two", description = "d"),
                ),
            ),
        ),
    )
    private val player = SteamPlayerAchievementsResponse(
        playerStats = SteamPlayerStats(
            gameName = "Isaac",
            achievements = listOf(
                SteamPlayerAchievement("a1", 1, 100L),
                SteamPlayerAchievement("a2", 0, 0L),
            ),
        ),
    )
    private val global = SteamGlobalAchievementPercentagesResponse(
        achievementPercentages = SteamGlobalAchievementPercentages(
            achievements = listOf(
                SteamGlobalAchievementPercent("a1", 42.5),
                SteamGlobalAchievementPercent("a2", 3.25),
            ),
        ),
    )

    private fun api() = mock<SteamApi> {
        onBlocking { getSchemaForGame(eq(250900), any()) } doReturn schema
        onBlocking { getPlayerAchievements(any(), eq(250900), any()) } doReturn player
        onBlocking { getGlobalAchievementPercentages(eq(250900)) } doReturn global
    }

    private fun repo(dao: SteamGameCacheDao): SteamGameCacheRepository {
        // Built first: creating a mock inside another mock's stubbing confuses Mockito.
        val api = api()
        val client = mock<SteamApiClient> { on { this.api } doReturn api }
        val creds = mock<SteamCredentialsRepository> {
            onBlocking { getCredentials() } doReturn SteamCredentials(vanityUrl = "me", steamId = "1")
        }
        return SteamGameCacheRepository(dao, client, creds)
    }

    @Test
    fun `a cached read carries the same percentages the fetch did`() = runTest {
        val dao = mock<SteamGameCacheDao> { onBlocking { getByVaultId(any()) } doReturn null }
        val fresh = repo(dao).getGameProgress("v1", 250900, forceRefresh = true)
        assertEquals(listOf(42.5, 3.25), fresh?.achievements?.map { it.globalPercent })

        // What was written is what a later open reads back, with no network call.
        val captor = argumentCaptor<SteamGameCacheEntity>()
        verify(dao).insert(captor.capture())
        val stored = captor.firstValue
        val readingDao = mock<SteamGameCacheDao> {
            onBlocking { getByVaultId("v1") } doReturn stored.copy(lastSyncedAt = System.currentTimeMillis())
        }
        val cached = repo(readingDao).getGameProgress("v1", 250900, forceRefresh = false)
        assertEquals(listOf(42.5, 3.25), cached?.achievements?.map { it.globalPercent })
    }
}
