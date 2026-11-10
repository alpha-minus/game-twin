package com.twinlore.app.ui.screens.browser

import com.twinlore.app.domain.BrowserTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for the screen-side vault-id filter that compensates for the
 * Activity-scoped BrowserTabsViewModel briefly serving the previous vault's
 * state during vault swaps (see reference_hilt_viewmodel_scoping memory).
 *
 * Without these filters, a stale activeTab snapshotted at first composition
 * drives a wv.loadUrl() into the new vault's WebView, and the resulting
 * onPageFinished writes the wrong URL onto the new vault's tab row.
 */
class BrowserTabsVaultFilterTest {

    private fun tab(id: String, vaultId: String) = BrowserTab(
        id = id,
        vaultId = vaultId,
        url = "https://example.com/$id",
        title = "tab $id",
        faviconBytes = null,
        scrollY = 0,
        tabOrder = 0,
        lastAccessed = 1L,
        createdAt = 1L
    )

    @Test fun `effectiveActiveTab strips a stale tab from the previous vault`() {
        val staleFromPriorVault = tab("a", vaultId = "v1")
        assertNull(effectiveActiveTab(staleFromPriorVault, currentVaultId = "v2"))
    }

    @Test fun `effectiveActiveTab passes through a tab whose vaultId matches`() {
        val freshTab = tab("a", vaultId = "v2")
        assertEquals(freshTab, effectiveActiveTab(freshTab, currentVaultId = "v2"))
    }

    @Test fun `effectiveActiveTab returns null when the raw tab is null`() {
        assertNull(effectiveActiveTab(null, currentVaultId = "v1"))
    }

    @Test fun `effectiveTabsList drops tabs from other vaults`() {
        val v1Tab = tab("a", vaultId = "v1")
        val v2Tab = tab("b", vaultId = "v2")
        assertEquals(
            listOf(v2Tab),
            effectiveTabsList(listOf(v1Tab, v2Tab), currentVaultId = "v2")
        )
    }

    @Test fun `effectiveTabsList returns empty when no tabs match the current vault`() {
        val v1Tab = tab("a", vaultId = "v1")
        assertEquals(
            emptyList<BrowserTab>(),
            effectiveTabsList(listOf(v1Tab), currentVaultId = "v2")
        )
    }
}
