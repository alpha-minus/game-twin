package com.twinlore.app.data.steam

import com.twinlore.app.data.local.SteamGameCacheDao
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock

/**
 * The achievement list survives a schema that does not arrive.
 *
 * GetPlayerAchievements already carries each achievement's name and description
 * (verified 2026-09-18 against Hades: "Escaped Tartarus", "Clear Tartarus"), so
 * a failed schema call costs the icons and the hidden flag, not the list.
 */
class SteamSchemalessNamesTest {

    private val player = SteamPlayerAchievementsResponse(
        playerStats = SteamPlayerStats(
            gameName = "Hades",
            achievements = listOf(
                SteamPlayerAchievement("AchClearTartarus", 1, 100L, "Escaped Tartarus", "Clear Tartarus"),
                SteamPlayerAchievement("AchClearAsphodel", 0, 0L, "Escaped Asphodel", "Clear Asphodel"),
            ),
        ),
    )

    private fun repo(schemaFails: Boolean, schema: SteamSchemaResponse? = null): SteamGameCacheRepository {
        val api = mock<SteamApi> {
            if (schemaFails) {
                onBlocking { getSchemaForGame(any(), any()) } doSuspendableAnswer { throw java.io.IOException("no schema") }
            } else {
                onBlocking { getSchemaForGame(any(), any()) } doReturn schema!!
            }
            onBlocking { getPlayerAchievements(any(), eq(1145360), any()) } doReturn player
            onBlocking { getGlobalAchievementPercentages(any()) } doSuspendableAnswer { throw java.io.IOException("none") }
        }
        val client = mock<SteamApiClient> { on { this.api } doReturn api }
        val creds = mock<SteamCredentialsRepository> {
            onBlocking { getCredentials() } doReturn SteamCredentials(vanityUrl = "me", steamId = "1")
        }
        val dao = mock<SteamGameCacheDao> { onBlocking { getByVaultId(any()) } doReturn null }
        return SteamGameCacheRepository(dao, client, creds)
    }

    @Test
    fun `without a schema the list still has its names`() = runTest {
        val progress = repo(schemaFails = true).getGameProgress("v1", 1145360, forceRefresh = true)

        assertEquals(2, progress?.numAchievements)
        assertEquals(1, progress?.numUnlocked)
        assertEquals(listOf("Escaped Tartarus", "Escaped Asphodel"), progress?.achievements?.map { it.title })
        assertEquals(listOf("Clear Tartarus", "Clear Asphodel"), progress?.achievements?.map { it.description })
        assertEquals("Hades", progress?.gameTitle)
    }

    // A schema entry with no display name of its own takes the player's.
    @Test
    fun `a nameless schema entry falls back to the player's name`() = runTest {
        val schema = SteamSchemaResponse(
            game = SteamSchemaGame(
                gameName = "Hades",
                availableGameStats = SteamGameStats(
                    achievements = listOf(
                        SteamSchemaAchievement(name = "AchClearTartarus", icon = "on", iconGray = "off"),
                        SteamSchemaAchievement(name = "AchClearAsphodel", displayName = "From the schema", icon = "on", iconGray = "off"),
                    ),
                ),
            ),
        )
        val progress = repo(schemaFails = false, schema = schema).getGameProgress("v1", 1145360, forceRefresh = true)

        assertEquals(listOf("Escaped Tartarus", "From the schema"), progress?.achievements?.map { it.title })
        assertEquals("on", progress?.achievements?.first()?.iconUrl)
    }
}
