package com.twinlore.app.domain

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class StateManagerPersistenceTest {

    private lateinit var stateManager: StateManager

    @Before
    fun setUp() {
        stateManager = StateManager()
    }

    @Test
    fun `serialize and deserialize Library`() {
        val state = AppState.Library
        val serialized = stateManager.serializeState(state)
        assertEquals("Library", serialized)
        assertEquals(state, stateManager.deserializeState(serialized))
    }

    @Test
    fun `serialize and deserialize VaultSession`() {
        val state = AppState.VaultSession("vault-123", VaultTab.Guide)
        val serialized = stateManager.serializeState(state)
        assertEquals("VaultSession:vault-123:Guide", serialized)
        assertEquals(state, stateManager.deserializeState(serialized))
    }

    @Test
    fun `serialize and deserialize GuideViewer with tabId`() {
        val state = AppState.GuideViewer("vault-123", "tab-1")
        val serialized = stateManager.serializeState(state)
        assertEquals("GuideViewer:vault-123:tab-1", serialized)
        assertEquals(state, stateManager.deserializeState(serialized))
    }

    @Test
    fun `serialize and deserialize GuideViewer without tabId`() {
        val state = AppState.GuideViewer("vault-123")
        val serialized = stateManager.serializeState(state)
        assertEquals("GuideViewer:vault-123:", serialized)
        val deserialized = stateManager.deserializeState(serialized) as AppState.GuideViewer
        assertEquals("vault-123", deserialized.vaultId)
        assertNull(deserialized.tabId)
    }

    @Test
    fun `serialize and deserialize VaultSession with colon in vaultId`() {
        val state = AppState.VaultSession("vault:123:special", VaultTab.Guide)
        val serialized = stateManager.serializeState(state)
        assertEquals(state, stateManager.deserializeState(serialized))
    }

    @Test
    fun `serialize and deserialize GuideViewer with colon in vaultId`() {
        val state = AppState.GuideViewer("vault:123:special", "tab-1")
        val serialized = stateManager.serializeState(state)
        assertEquals(state, stateManager.deserializeState(serialized))
    }

    @Test
    fun `serialize and deserialize MapViewer with colon in vaultId`() {
        val state = AppState.MapViewer("vault:123:special")
        val serialized = stateManager.serializeState(state)
        assertEquals("MapViewer:vault:123:special", serialized)
        // MapViewer deserialization uses the full rest after the first colon
        assertEquals(state, stateManager.deserializeState(serialized))
    }

    @Test
    fun `deserialize unknown string returns null`() {
        assertNull(stateManager.deserializeState("UnknownState"))
    }

    @Test
    fun `deserialize malformed string returns null`() {
        assertNull(stateManager.deserializeState("VaultSession:"))
    }
}
