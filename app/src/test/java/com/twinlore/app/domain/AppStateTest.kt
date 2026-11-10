package com.twinlore.app.domain

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for AppState sealed class
 * Test Strategy: Verify all 6 states exist, parameter validation for states with vaultId, and proper equality comparisons
 */
class AppStateTest {

    @Test
    fun `all states exist`() {
        // Verify states are defined
        assertNotNull(AppState.Library)
        assertNotNull(AppState.Settings)
        assertNotNull(AppState.Sleep)

        val vaultSessionState = AppState.VaultSession("test-id", VaultTab.Guide)
        val mapViewerState = AppState.MapViewer("test-id")
        val guideViewerState = AppState.GuideViewer("test-id")

        assertNotNull(vaultSessionState)
        assertNotNull(mapViewerState)
        assertNotNull(guideViewerState)
    }

    @Test
    fun `Library state is singleton object`() {
        val library1 = AppState.Library
        val library2 = AppState.Library
        
        assertEquals(library1, library2)
        assertSame(library1, library2)
    }

    @Test
    fun `Settings state is singleton object`() {
        val settings1 = AppState.Settings
        val settings2 = AppState.Settings
        
        assertEquals(settings1, settings2)
        assertSame(settings1, settings2)
    }

    @Test
    fun `Sleep state is singleton object`() {
        val sleep1 = AppState.Sleep
        val sleep2 = AppState.Sleep
        
        assertEquals(sleep1, sleep2)
        assertSame(sleep1, sleep2)
    }

    @Test
    fun `VaultSession state with same vaultId and tab are equal`() {
        val vault1 = AppState.VaultSession("vault-123", VaultTab.Guide)
        val vault2 = AppState.VaultSession("vault-123", VaultTab.Guide)

        assertEquals(vault1, vault2)
        assertEquals(vault1.hashCode(), vault2.hashCode())
    }

    @Test
    fun `VaultSession state with different vaultId are not equal`() {
        val vault1 = AppState.VaultSession("vault-123", VaultTab.Guide)
        val vault2 = AppState.VaultSession("vault-456", VaultTab.Guide)

        assertNotEquals(vault1, vault2)
    }

    @Test
    fun `MapViewer state with same vaultId are equal`() {
        val map1 = AppState.MapViewer("vault-123")
        val map2 = AppState.MapViewer("vault-123")
        
        assertEquals(map1, map2)
        assertEquals(map1.hashCode(), map2.hashCode())
    }

    @Test
    fun `GuideViewer state with same vaultId are equal`() {
        val guide1 = AppState.GuideViewer("vault-123")
        val guide2 = AppState.GuideViewer("vault-123")
        
        assertEquals(guide1, guide2)
        assertEquals(guide1.hashCode(), guide2.hashCode())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `VaultSession state rejects blank vaultId`() {
        AppState.VaultSession("", VaultTab.Guide)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `MapViewer state rejects blank vaultId`() {
        AppState.MapViewer("")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `GuideViewer state rejects blank vaultId`() {
        AppState.GuideViewer("")
    }

    @Test
    fun `VaultSession state toString returns correct format`() {
        val vault = AppState.VaultSession("test-id", VaultTab.Guide)
        assertTrue(vault.toString().contains("test-id"))
    }

    @Test
    fun `Library state toString returns Library`() {
        assertEquals("Library", AppState.Library.toString())
    }

    @Test
    fun `Settings state toString returns Settings`() {
        assertEquals("Settings", AppState.Settings.toString())
    }

    @Test
    fun `Sleep state toString returns Sleep`() {
        assertEquals("Sleep", AppState.Sleep.toString())
    }
}



