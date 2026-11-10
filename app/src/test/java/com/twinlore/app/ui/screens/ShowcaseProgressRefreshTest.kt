package com.twinlore.app.ui.screens

import com.twinlore.app.data.retroachievements.RaGameCacheRepository
import com.twinlore.app.data.steam.SteamGameCacheRepository
import com.twinlore.app.data.steam.SteamGameNoAchievementsException
import com.twinlore.app.data.steam.SteamGameNotOwnedException
import com.twinlore.app.data.steam.SteamProfilePrivateException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.mock

/**
 * The detection showcase force-refreshes the focused vault's achievements from a
 * LaunchedEffect. SteamGameCacheRepository reports a private profile, a game with
 * no stats and an unowned game by throwing, and anything escaping that effect
 * takes the whole app down (and, with "show last played on start", again on every
 * launch). The refresh must turn those into "nothing fresh to show".
 */
class ShowcaseProgressRefreshTest {

    private val raRepo = mock<RaGameCacheRepository>()

    private fun steamRepoThrowing(e: Exception) = mock<SteamGameCacheRepository> {
        onBlocking { getGameProgress(any(), any(), any(), anyOrNull()) } doSuspendableAnswer { throw e }
    }

    private suspend fun refreshSteam(steamRepo: SteamGameCacheRepository) =
        refreshShowcaseProgress("v1", raGameId = null, steamAppId = 250900, raRepo, steamRepo)

    @Test
    fun private_steam_profile_yields_null_instead_of_throwing() = runTest {
        assertNull(refreshSteam(steamRepoThrowing(SteamProfilePrivateException())))
    }

    @Test
    fun steam_game_without_achievements_yields_null_instead_of_throwing() = runTest {
        assertNull(refreshSteam(steamRepoThrowing(SteamGameNoAchievementsException())))
    }

    @Test
    fun unowned_steam_game_yields_null_instead_of_throwing() = runTest {
        assertNull(refreshSteam(steamRepoThrowing(SteamGameNotOwnedException())))
    }

    @Test
    fun ra_failure_yields_null_instead_of_throwing() = runTest {
        val ra = mock<RaGameCacheRepository> {
            onBlocking { getGameProgress(any(), any(), any(), anyOrNull()) } doSuspendableAnswer {
                throw IllegalStateException("boom")
            }
        }
        assertNull(refreshShowcaseProgress("v1", raGameId = 1, steamAppId = null, ra, mock()))
    }

    @Test
    fun cancellation_still_propagates() = runTest {
        try {
            refreshSteam(steamRepoThrowing(CancellationException("showcase left")))
            fail("cancellation must not be swallowed")
        } catch (_: CancellationException) {
        }
    }
}
