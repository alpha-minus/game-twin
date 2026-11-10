package com.twinlore.app.domain

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import com.twinlore.app.domain.VaultTab
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for StateManager
 * Test Strategy: Thread-safety, state observation, initial state verification, and concurrent state changes
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StateManagerTest {

    private lateinit var stateManager: StateManager
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        stateManager = StateManager()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is Library`() {
        assertEquals(AppState.Library, stateManager.getCurrentState())
    }

    @Test
    fun `currentState flow emits initial Library state`() = runTest(testDispatcher) {
        val initialState = stateManager.currentState.value
        assertEquals(AppState.Library, initialState)
    }

    @Test
    fun `setState updates current state`() = runTest(testDispatcher) {
        val newState = AppState.VaultSession("test-vault", VaultTab.Guide)
        val result = stateManager.setState(newState)
        advanceUntilIdle()

        assertTrue(result)
        assertEquals(newState, stateManager.getCurrentState())
    }

    @Test
    fun `setState emits new state to flow`() = runTest(testDispatcher) {
        val newState = AppState.Settings
        stateManager.setState(newState)
        advanceUntilIdle()
        
        assertEquals(newState, stateManager.currentState.value)
    }

    @Test
    fun `valid transition from Library to VaultSession succeeds`() = runTest(testDispatcher) {
        val vaultState = AppState.VaultSession("test-vault", VaultTab.Guide)
        val result = stateManager.setState(vaultState)
        advanceUntilIdle()

        assertTrue(result)
        assertEquals(vaultState, stateManager.getCurrentState())
    }

    @Test
    fun `valid transition from Library to Settings succeeds`() = runTest(testDispatcher) {
        val result = stateManager.setState(AppState.Settings)
        advanceUntilIdle()
        
        assertTrue(result)
        assertEquals(AppState.Settings, stateManager.getCurrentState())
    }

    @Test
    fun `valid transition from VaultSession to MapViewer with same vaultId succeeds`() = runTest(testDispatcher) {
        val vaultId = "test-vault"
        stateManager.setState(AppState.VaultSession(vaultId, VaultTab.Guide))
        advanceUntilIdle()

        val mapViewerState = AppState.MapViewer(vaultId)
        val result = stateManager.setState(mapViewerState)
        advanceUntilIdle()

        assertTrue(result)
        assertEquals(mapViewerState, stateManager.getCurrentState())
    }

    @Test
    fun `invalid transition from VaultSession to MapViewer with different vaultId fails`() = runTest(testDispatcher) {
        stateManager.setState(AppState.VaultSession("vault-1", VaultTab.Guide))
        advanceUntilIdle()

        val mapViewerState = AppState.MapViewer("vault-2")
        val result = stateManager.setState(mapViewerState)
        advanceUntilIdle()

        assertFalse(result)
        assertEquals(AppState.VaultSession("vault-1", VaultTab.Guide), stateManager.getCurrentState())
    }

    @Test
    fun `transition to Settings from any state succeeds`() = runTest(testDispatcher) {
        stateManager.setState(AppState.VaultSession("test-vault", VaultTab.Guide))
        advanceUntilIdle()

        val result = stateManager.setState(AppState.Settings)
        advanceUntilIdle()

        assertTrue(result)
        assertEquals(AppState.Settings, stateManager.getCurrentState())
    }

    @Test
    fun `transition to Sleep from any state succeeds`() = runTest(testDispatcher) {
        stateManager.setState(AppState.VaultSession("test-vault", VaultTab.Guide))
        advanceUntilIdle()

        val result = stateManager.setState(AppState.Sleep)
        advanceUntilIdle()

        assertTrue(result)
        assertEquals(AppState.Sleep, stateManager.getCurrentState())
    }

    @Test
    fun `same state transition is always valid`() = runTest(testDispatcher) {
        val vaultState = AppState.VaultSession("test-vault", VaultTab.Guide)
        stateManager.setState(vaultState)
        advanceUntilIdle()

        val result = stateManager.setState(vaultState)
        advanceUntilIdle()

        assertTrue(result)
    }

    @Test
    fun `transition from MapViewer to GuideViewer with same vaultId succeeds`() = runTest(testDispatcher) {
        val vaultId = "test-vault"
        stateManager.setState(AppState.VaultSession(vaultId, VaultTab.Guide))
        advanceUntilIdle()
        stateManager.setState(AppState.MapViewer(vaultId))
        advanceUntilIdle()

        val guideViewerState = AppState.GuideViewer(vaultId)
        val result = stateManager.setState(guideViewerState)
        advanceUntilIdle()

        assertTrue(result)
        assertEquals(guideViewerState, stateManager.getCurrentState())
    }

    @Test
    fun `transition from GuideViewer to MapViewer with same vaultId succeeds`() = runTest(testDispatcher) {
        val vaultId = "test-vault"
        stateManager.setState(AppState.VaultSession(vaultId, VaultTab.Guide))
        advanceUntilIdle()
        stateManager.setState(AppState.GuideViewer(vaultId))
        advanceUntilIdle()

        val mapViewerState = AppState.MapViewer(vaultId)
        val result = stateManager.setState(mapViewerState)
        advanceUntilIdle()

        assertTrue(result)
        assertEquals(mapViewerState, stateManager.getCurrentState())
    }

    @Test
    fun `transition from MapViewer back to VaultSession with same vaultId succeeds`() = runTest(testDispatcher) {
        val vaultId = "test-vault"
        val vaultState = AppState.VaultSession(vaultId, VaultTab.Guide)
        stateManager.setState(vaultState)
        advanceUntilIdle()
        stateManager.setState(AppState.MapViewer(vaultId))
        advanceUntilIdle()

        val result = stateManager.setState(vaultState)
        advanceUntilIdle()

        assertTrue(result)
        assertEquals(vaultState, stateManager.getCurrentState())
    }

    @Test
    fun `transition from MapViewer to Library succeeds`() = runTest(testDispatcher) {
        val vaultId = "test-vault"
        stateManager.setState(AppState.VaultSession(vaultId, VaultTab.Guide))
        advanceUntilIdle()
        stateManager.setState(AppState.MapViewer(vaultId))
        advanceUntilIdle()

        val result = stateManager.setState(AppState.Library)
        advanceUntilIdle()

        assertTrue(result)
        assertEquals(AppState.Library, stateManager.getCurrentState())
    }

    @Test
    fun `setState with validate false bypasses validation`() = runTest(testDispatcher) {
        stateManager.setState(AppState.VaultSession("vault-1", VaultTab.Guide))
        advanceUntilIdle()

        // This would normally be invalid (different vaultId)
        val result = stateManager.setState(AppState.MapViewer("vault-2"), validate = false)
        advanceUntilIdle()

        assertTrue(result)
        assertEquals(AppState.MapViewer("vault-2"), stateManager.getCurrentState())
    }

    @Test
    fun `valid transition from Library to GameStats succeeds`() = runTest(testDispatcher) {
        val result = stateManager.setState(AppState.GameStats)
        advanceUntilIdle()

        assertTrue(result)
        assertEquals(AppState.GameStats, stateManager.getCurrentState())
    }

    @Test
    fun `valid transition from GameStats back to Library succeeds`() = runTest(testDispatcher) {
        stateManager.setState(AppState.GameStats)
        advanceUntilIdle()

        val result = stateManager.setState(AppState.Library)
        advanceUntilIdle()

        assertTrue(result)
        assertEquals(AppState.Library, stateManager.getCurrentState())
    }

    @Test
    fun `observeState returns currentState flow`() = runTest(testDispatcher) {
        val flow = stateManager.observeState()

        assertEquals(AppState.Library, flow.value)

        stateManager.setState(AppState.Settings)
        advanceUntilIdle()
        assertEquals(AppState.Settings, flow.value)
    }

    @Test
    fun `valid transition from Library to Journey succeeds`() = runTest(testDispatcher) {
        val result = stateManager.setState(AppState.Journey(lockedGameId = 1, lockedGameTitle = "Chrono Trigger"))
        advanceUntilIdle()

        assertTrue(result)
        assertEquals(AppState.Journey(lockedGameId = 1, lockedGameTitle = "Chrono Trigger"), stateManager.getCurrentState())
    }

    @Test
    fun `valid transition from VaultSession to Journey succeeds`() = runTest(testDispatcher) {
        stateManager.setState(AppState.VaultSession("test-vault", VaultTab.Guide))
        advanceUntilIdle()

        val result = stateManager.setState(AppState.Journey())
        advanceUntilIdle()

        assertTrue(result)
        assertEquals(AppState.Journey(), stateManager.getCurrentState())
    }

    @Test
    fun `valid transition from Journey to Library succeeds`() = runTest(testDispatcher) {
        stateManager.setState(AppState.Journey())
        advanceUntilIdle()

        val result = stateManager.setState(AppState.Library)
        advanceUntilIdle()

        assertTrue(result)
        assertEquals(AppState.Library, stateManager.getCurrentState())
    }

    @Test
    fun `valid transition from Journey to VaultSession succeeds`() = runTest(testDispatcher) {
        stateManager.setState(AppState.Journey())
        advanceUntilIdle()

        val vaultState = AppState.VaultSession("test-vault", VaultTab.Guide)
        val result = stateManager.setState(vaultState)
        advanceUntilIdle()

        assertTrue(result)
        assertEquals(vaultState, stateManager.getCurrentState())
    }

    /**
     * The locked journey's only call to action is "Open settings" - it is how a user without an API
     * key or a username unlocks the screen - so back out of Settings has to be able to return to
     * the journey it was opened from. It used to land in the library, leaving the whole vault path
     * to walk again to see the journey they had just unlocked.
     */
    @Test
    fun `valid transition from Settings back to Journey succeeds`() = runTest(testDispatcher) {
        stateManager.setState(AppState.Journey(lockedGameId = 1447, lockedGameTitle = "Zelda"))
        advanceUntilIdle()
        stateManager.setState(AppState.Settings)
        advanceUntilIdle()
        assertEquals(AppState.Settings, stateManager.getCurrentState())

        val journey = AppState.Journey(lockedGameId = 1447, lockedGameTitle = "Zelda")
        val result = stateManager.setState(journey)
        advanceUntilIdle()

        assertTrue("Settings must be able to hand the user back to the journey", result)
        assertEquals(journey, stateManager.getCurrentState())
    }

    /** The library journey is the same round trip without a locked game. */
    @Test
    fun `valid transition from Settings back to a library Journey succeeds`() = runTest(testDispatcher) {
        stateManager.setState(AppState.Settings)
        advanceUntilIdle()

        val result = stateManager.setState(AppState.Journey())
        advanceUntilIdle()

        assertTrue(result)
        assertEquals(AppState.Journey(), stateManager.getCurrentState())
    }

    @Test
    fun `serializeState for Journey returns Library`() {
        val serialized = stateManager.serializeState(AppState.Journey(lockedGameId = 1, lockedGameTitle = "Chrono Trigger"))
        assertEquals("Library", serialized)
    }
}
