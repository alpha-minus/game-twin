package com.twinlore.app.ui.screens.browser

import com.twinlore.app.domain.BrowserTab

/**
 * Screen-side filters that gate [BrowserTab] data on the BrowserScreen's
 * current vaultId.
 *
 * `BrowserTabsViewModel` is Activity-scoped (see reference_hilt_viewmodel_scoping
 * memory: Crossfade-based nav shares one `hiltViewModel()` instance across
 * vault swaps). Its `bind(vaultId)` runs from `LaunchedEffect(vaultId)`, i.e.
 * AFTER the first composition of the new vault's BrowserScreen.
 *
 * During that gap, `activeTab.collectAsState()` snapshots the previous vault's
 * head tab. If a load-effect or persistence write consumed that snapshot, it
 * would load the prior vault's URL into the new vault's WebView, then
 * `onPageFinished` would persist that URL onto the new vault's tab row in
 * `browser_tabs` — the "browser tab URL leak" reproduced when the user leaves
 * a vault session with a non-Browser tab active and the next-opened vault was
 * last on Browser.
 *
 * Filtering by `vaultId` at every BrowserScreen consumer suppresses that brief
 * window without changing VM emission shape (the prior failed fix attempted
 * a VM-side MutableStateFlow rewrite and produced an alternating-write loop —
 * see project_browser_tab_url_leak memory).
 */
internal fun effectiveActiveTab(activeTab: BrowserTab?, currentVaultId: String): BrowserTab? =
    activeTab?.takeIf { it.vaultId == currentVaultId }

/** See [effectiveActiveTab]. List-level analogue used for tabsList / tab switcher counts. */
internal fun effectiveTabsList(tabs: List<BrowserTab>, currentVaultId: String): List<BrowserTab> =
    tabs.filter { it.vaultId == currentVaultId }
