package com.twinlore.app.ui.fold.guide

import com.twinlore.app.data.VaultRepository
import com.twinlore.app.data.retroachievements.RaGameCacheRepository
import com.twinlore.app.data.retroachievements.RaGameProgress
import com.twinlore.app.data.steam.SteamAchievementDisplay
import com.twinlore.app.data.steam.SteamGameCacheRepository
import com.twinlore.app.data.steam.SteamGameProgress
import com.twinlore.app.data.steam.SteamProfilePrivateException
import com.twinlore.app.domain.Vault
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

/**
 * The panel's achievements come from whichever service the vault is linked to.
 *
 * It used to read RetroAchievements only, so a Steam-linked vault, whose
 * achievements the app itself shows, opened the panel on "not linked".
 */
class AndroidFoldGuideDataAwardsTest {

    private fun vault(raGameId: String? = null, steamAppId: String? = null) = Vault(
        id = "v1", name = "Isaac", romPath = null, romFilename = null,
        systemeid = null, gameId = null, source = null, lastOpened = null,
        createdAt = java.util.Date(), raGameId = raGameId, steamAppId = steamAppId,
    )

    private fun steamAchievement(name: String, achieved: Boolean) = SteamAchievementDisplay(
        apiName = name, title = name, description = "d", iconUrl = "on", iconGrayUrl = "off",
        achieved = achieved, unlockTime = if (achieved) 100L else null, hidden = false,
    )

    private val steamProgress = SteamGameProgress(
        steamAppId = 250900,
        gameTitle = "The Binding of Isaac: Rebirth",
        numAchievements = 3,
        numUnlocked = 1,
        achievements = listOf(
            steamAchievement("a", true), steamAchievement("b", false), steamAchievement("c", false),
        ),
        recentUnlocks = emptyList(),
        lastSyncedAt = 1L,
        playtimeMinutes = 90,
        headerImageUrl = "https://cdn.akamai.steamstatic.com/steam/apps/250900/header.jpg",
    )

    private fun data(
        vault: Vault,
        ra: RaGameCacheRepository = mock(),
        steam: SteamGameCacheRepository = mock(),
    ): AndroidFoldGuideData {
        val vaults = mock<VaultRepository> { onBlocking { getVaultById(any()) } doReturn vault }
        return AndroidFoldGuideData(
            savedTabRepository = mock(),
            guideViewStateManager = mock(),
            savedMapRepository = mock(),
            resourceResolver = mock(),
            settingsRepository = mock(),
            mapViewStateManager = mock(),
            vaultRepository = vaults,
            raGameCacheRepository = ra,
            raCommentsRepository = mock(),
            steamGameCacheRepository = steam,
            syncLatestPlayedGameUseCase = mock(),
            contentUriFileWatcher = mock(),
            markerManager = mock(),
            mapThumbnailCache = mock(),
            imageCleaner = mock(),
        )
    }

    @Test
    fun `a steam-linked vault opens on its cached steam achievements`() = runTest {
        val steam = mock<SteamGameCacheRepository> {
            onBlocking { getCachedProgress("v1") } doReturn steamProgress
        }
        val awards = data(vault(steamAppId = "250900"), steam = steam).awards("v1")

        assertEquals(FoldAwardSource.STEAM, awards.source)
        assertTrue(awards.linked)
        assertEquals(listOf("a", "b", "c"), awards.steamAchievements.map { it.apiName })
        assertEquals(1, awards.unlocked)
        assertEquals(3, awards.total)
        assertEquals(90, awards.playtimeMinutes)
        assertEquals("The Binding of Isaac: Rebirth", awards.gameTitle)
        // The app's Steam drawer heads the list with the store banner; so does the panel.
        assertEquals("https://cdn.akamai.steamstatic.com/steam/apps/250900/header.jpg", awards.gameIconUrl)
        assertFalse(awards.isEmpty)
    }

    @Test
    fun `a steam-linked vault with nothing cached is linked and empty`() = runTest {
        val awards = data(vault(steamAppId = "250900")).awards("v1")

        assertEquals(FoldAwardSource.STEAM, awards.source)
        assertTrue(awards.linked)
        assertTrue(awards.isEmpty)
    }

    @Test
    fun `a steam refresh goes through the app's forced refresh`() = runTest {
        val steam = mock<SteamGameCacheRepository> {
            onBlocking { getGameProgress(eq("v1"), eq(250900), eq(true), anyOrNull()) } doReturn steamProgress
        }
        val awards = data(vault(steamAppId = "250900"), steam = steam).refreshAwards("v1")

        assertEquals(FoldAwardSource.STEAM, awards?.source)
        assertEquals(3, awards?.steamAchievements?.size)
    }

    @Test
    fun `a steam refresh that throws keeps what the panel had`() = runTest {
        val steam = mock<SteamGameCacheRepository> {
            onBlocking { getGameProgress(any(), any(), any(), anyOrNull()) } doSuspendableAnswer {
                throw SteamProfilePrivateException()
            }
        }
        assertNull(data(vault(steamAppId = "250900"), steam = steam).refreshAwards("v1"))
    }

    @Test
    fun `a vault linked to both stays on retroachievements`() = runTest {
        val ra = mock<RaGameCacheRepository> {
            onBlocking { getCachedProgress("v1") } doReturn raProgress()
        }
        val steam = mock<SteamGameCacheRepository>()
        val awards = data(vault(raGameId = "7", steamAppId = "250900"), ra = ra, steam = steam).awards("v1")

        assertEquals(FoldAwardSource.RA, awards.source)
        assertTrue(awards.linked)
        verify(steam, never()).getCachedProgress(any())
    }

    @Test
    fun `an unlinked vault says so`() = runTest {
        val awards = data(vault()).awards("v1")

        assertFalse(awards.linked)
        assertNull(data(vault()).refreshAwards("v1"))
    }

    private fun raProgress() = RaGameProgress(
        raGameId = 7,
        gameTitle = "RA game",
        totalPoints = 0,
        numAchievements = 0,
        userPoints = 0,
        userNumAchieved = 0,
        userHardcorePoints = 0,
        userNumHardcore = 0,
        recentUnlocks = emptyList(),
        achievements = emptyList(),
        lastSyncedAt = 1L,
    )
}
