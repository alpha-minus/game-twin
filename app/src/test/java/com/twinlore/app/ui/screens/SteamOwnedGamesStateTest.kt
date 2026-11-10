package com.twinlore.app.ui.screens

import com.twinlore.app.data.steam.SteamApi
import com.twinlore.app.data.steam.SteamApiClient
import com.twinlore.app.data.steam.SteamCredentials
import com.twinlore.app.data.steam.SteamCredentialsRepository
import com.twinlore.app.data.steam.SteamOwnedGame
import com.twinlore.app.data.steam.SteamOwnedGamesResponse
import com.twinlore.app.data.steam.SteamOwnedGamesResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.mock

/**
 * The "Choose from library" dialog tells loading, nothing to show and failure apart.
 *
 * All three used to be one empty list, drawn as a spinner. An account whose game
 * details are private comes back from Steam with no games at
 * all, and the dialog spun forever; a failed request did the same, with its
 * error set somewhere the dialog covered.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SteamOwnedGamesStateTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(api: SteamApi): SteamAchievementDrawerViewModel {
        val client = mock<SteamApiClient> { on { this.api } doReturn api }
        val creds = mock<SteamCredentialsRepository> {
            onBlocking { getCredentials() } doReturn SteamCredentials(vanityUrl = "example-user", steamId = "1")
        }
        return SteamAchievementDrawerViewModel(
            steamGameCacheRepository = mock(),
            credentialsRepository = creds,
            apiClient = client,
            vaultRepository = mock(),
            gameMatcher = mock(),
            networkConnectivityManager = mock(),
            checklistRepository = mock(),
        )
    }

    private fun apiAnswering(block: suspend () -> SteamOwnedGamesResponse) = mock<SteamApi> {
        onBlocking { getOwnedGames(any(), any(), any()) } doSuspendableAnswer { block() }
    }

    @Test
    fun `loading while the request runs`() = runTest(dispatcher) {
        val gate = CompletableDeferred<SteamOwnedGamesResponse>()
        val vm = viewModel(apiAnswering { gate.await() })
        vm.loadOwnedGames()
        advanceUntilIdle()
        assertEquals(SteamOwnedGamesState.Loading, vm.ownedGamesState.value)
        gate.complete(SteamOwnedGamesResponse(SteamOwnedGamesResult(games = listOf(SteamOwnedGame(1, "A")))))
    }

    @Test
    fun `a library loads`() = runTest(dispatcher) {
        val vm = viewModel(apiAnswering {
            SteamOwnedGamesResponse(SteamOwnedGamesResult(games = listOf(SteamOwnedGame(2, "b"), SteamOwnedGame(1, "A"))))
        })
        vm.loadOwnedGames()
        advanceUntilIdle()
        val state = vm.ownedGamesState.value
        assertTrue(state is SteamOwnedGamesState.Loaded)
        assertEquals(listOf("A", "b"), (state as SteamOwnedGamesState.Loaded).games.map { it.name })
    }

    @Test
    fun `no games from steam is empty, not loading`() = runTest(dispatcher) {
        val vm = viewModel(apiAnswering { SteamOwnedGamesResponse(SteamOwnedGamesResult()) })
        vm.loadOwnedGames()
        advanceUntilIdle()
        assertEquals(SteamOwnedGamesState.Empty, vm.ownedGamesState.value)
    }

    @Test
    fun `a failed request carries its message`() = runTest(dispatcher) {
        val vm = viewModel(apiAnswering { throw java.io.IOException("boom") })
        vm.loadOwnedGames()
        advanceUntilIdle()
        val state = vm.ownedGamesState.value
        assertTrue(state is SteamOwnedGamesState.Failed)
        assertEquals(
            "Connection to Steam failed. Check your internet connection.",
            (state as SteamOwnedGamesState.Failed).message,
        )
    }
}
