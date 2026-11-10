package com.twinlore.app.ui.screens.browser

import androidx.lifecycle.viewModelScope
import com.twinlore.app.data.BrowserTabRepository
import com.twinlore.app.domain.BrowserTab
import com.twinlore.app.domain.Result
import com.twinlore.app.ui.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * One ViewModel per vault session (keyed via hiltViewModel(key = "vault_session_browser_tabs")).
 *
 * Setter-style init: caller invokes bind(vaultId, landingUrlProvider) after construction.
 * Mirrors sibling BrowserViewModel.
 */
@HiltViewModel
class BrowserTabsViewModel @Inject constructor(
    private val repo: BrowserTabRepository,
    private val savedTabRepo: com.twinlore.app.data.SavedTabRepository,
    private val sourceIconCache: com.twinlore.app.data.browser.SourceIconCache,
) : BaseViewModel() {

    private var vaultId: String? = null
    private var landingUrlProvider: (() -> String)? = null

    private val _tabs = MutableStateFlow<List<BrowserTab>>(emptyList())
    val tabs: StateFlow<List<BrowserTab>> = _tabs.asStateFlow()

    val activeTab: StateFlow<BrowserTab?> = _tabs
        .map { it.firstOrNull() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val canGoBack: StateFlow<Boolean> = activeTab
        .map { it?.canGoBack == true }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val canGoForward: StateFlow<Boolean> = activeTab
        .map { it?.canGoForward == true }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val showSwitcher: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val toastEvent: MutableSharedFlow<String> = MutableSharedFlow(extraBufferCapacity = 1)

    /**
     * Fires whenever a foreground tab is created via a path that asked for a pulse
     * (currently: link-click new-tab opens). Top bar collects this to animate the
     * [N] counter. SharedFlow with replay = 0 so pulses queued before the top bar
     * is composed are dropped, not replayed later.
     */
    private val _tabOpenedPulse = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 1,
    )
    val tabOpenedPulse: SharedFlow<Unit> = _tabOpenedPulse.asSharedFlow()

    // Tab id whose scroll position should be restored on the next onPageFinished.
    //
    // Only a SWITCH arms this. A brand-new tab must never restore anything: there is no position
    // of its own to go back to, and its row's `scrollY` can still be carrying a number that
    // belongs to the page the user was looking at when they opened it (the debounced flush in
    // BrowserScreen writes the live scroll onto whichever tab is active when the debounce
    // fires). Arming it here is what put a freshly opened guide - GameFAQs from the vault's
    // browse menu, say - on screen already scrolled halfway down.
    private var scrollRestorePendingForId: String? = null

    // Tab id whose current load is a re-load of a page the tab was already on, rather than a
    // navigation to a new one. onPageFinished updates the title in place for these instead of
    // pushing a history entry; both a switch and a new tab qualify, since a new tab's row is
    // created already carrying its first URL.
    //
    // Separate from the scroll flag above, which those two do NOT agree about. They were one
    // field, which is how a new tab came to ask for a scroll restore at all.
    private var switchReloadPendingForId: String? = null

    // Tab id whose URL is currently being loaded. Page event callbacks only write
    // if this still matches the active tab, guarding against cross-tab corruption.
    private var loadingForTabId: String? = null

    // Tab id whose current loadUrl was triggered by a back/forward replay.
    // Used by onPageFinished to suppress recording the load as a new navigation.
    private var navigatingViaHistoryFor: String? = null

    private var observeJob: Job? = null

    val lastClosedTab: MutableStateFlow<BrowserTab?> = MutableStateFlow(null)
    private var undoDismissJob: Job? = null

    val lastDeletedLink: MutableStateFlow<com.twinlore.app.domain.SavedTab?> = MutableStateFlow(null)
    private var linkUndoDismissJob: Job? = null

    val lastClosedTabs: MutableStateFlow<List<BrowserTab>?> = MutableStateFlow(null)
    private var bulkUndoDismissJob: Job? = null

    fun bind(vaultId: String, landingUrlProvider: () -> String) {
        if (this.vaultId == vaultId) return
        this.vaultId = vaultId
        this.landingUrlProvider = landingUrlProvider
        // Reset transient state from the previous vault: the new vault's
        // tabs flow will repopulate _tabs on its first emission, but until
        // then, callers must not see the prior vault's list.
        _tabs.value = emptyList()
        showSwitcher.value = false
        scrollRestorePendingForId = null
        switchReloadPendingForId = null
        loadingForTabId = null
        navigatingViaHistoryFor = null
        lastClosedTab.value = null
        lastDeletedLink.value = null
        undoDismissJob?.cancel()
        linkUndoDismissJob?.cancel()
        // Replace the previous observer (if any) with one targeting the new vault.
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            repo.observeForVault(vaultId).collect { _tabs.value = it }
        }
        // Self-heal: if no tabs exist for this vault, seed a landing tab.
        // ensureAtLeastOne is idempotent (a no-op when tabs exist), so this is
        // safe to call on every vault entry. Runs in its own coroutine because
        // observeJob's collect suspends forever.
        viewModelScope.launch {
            val landing = landingUrlProvider?.invoke() ?: return@launch
            repo.ensureAtLeastOne(vaultId, landing)
        }
    }

    fun openSwitcher() { showSwitcher.value = true }
    fun closeSwitcher() { showSwitcher.value = false }

    fun newTab(
        url: String? = null,
        background: Boolean = false,
        pulseOnSuccess: Boolean = false,
    ): Job = viewModelScope.launch {
        val vid = vaultId ?: return@launch
        if (url != null && !isOpenableInTab(url)) {
            toastEvent.tryEmit("Only web links open in a tab")
            return@launch
        }
        val target = url ?: landingUrlProvider?.invoke() ?: return@launch
        val result = repo.createTab(vid, target, "", background)
        when (result) {
            is Result.Success -> {
                if (!background) {
                    // The history flag only - a new tab has no scroll position of its own to
                    // restore, and asking for one restores somebody else's. See the fields.
                    switchReloadPendingForId = result.data.id
                    loadingForTabId = result.data.id
                    closeSwitcher()
                }
                if (pulseOnSuccess) {
                    _tabOpenedPulse.tryEmit(Unit)
                }
            }
            is Result.Failure -> {
                toastEvent.tryEmit("Close some tabs to open another (cap reached)")
            }
            else -> { /* unreachable for createTab */ }
        }
    }

    fun closeTab(id: String): Job = viewModelScope.launch {
        if (_tabs.value.size <= 1) {
            toastEvent.tryEmit("At least one tab must stay open")
            return@launch
        }
        val closed = _tabs.value.firstOrNull { it.id == id } ?: return@launch
        repo.closeTab(id)
        armUndo(closed)
    }

    internal fun armUndo(tab: BrowserTab) {
        undoDismissJob?.cancel()
        linkUndoDismissJob?.cancel()
        lastDeletedLink.value = null
        bulkUndoDismissJob?.cancel()
        lastClosedTabs.value = null
        lastClosedTab.value = tab
        undoDismissJob = viewModelScope.launch {
            kotlinx.coroutines.delay(5000)
            if (lastClosedTab.value?.id == tab.id) {
                lastClosedTab.value = null
            }
        }
    }

    fun undoClose(): Job = viewModelScope.launch {
        val tab = lastClosedTab.value ?: return@launch
        undoDismissJob?.cancel()
        lastClosedTab.value = null
        repo.restoreTab(tab)
    }

    fun dismissUndo() {
        undoDismissJob?.cancel()
        lastClosedTab.value = null
    }

    fun closeAllExceptActive(): Job = viewModelScope.launch {
        // Snapshot _tabs.value once so activeId and toClose derive from the same list.
        // activeTab is a derived StateFlow that lags _tabs.value by one dispatcher frame;
        // reading it separately would risk a torn snapshot if a new tab landed between
        // the two reads (e.g., a background target=_blank arrival).
        val snapshot = _tabs.value
        if (snapshot.size <= 1) return@launch
        val activeId = snapshot.firstOrNull()?.id ?: return@launch
        val toClose = snapshot.filter { it.id != activeId }
        toClose.forEach { repo.closeTab(it.id) }
        armBulkUndo(toClose)
    }

    internal fun armBulkUndo(tabs: List<BrowserTab>) {
        // Mutex: clear any singular-undo or link-undo state so only one banner is live.
        undoDismissJob?.cancel()
        lastClosedTab.value = null
        linkUndoDismissJob?.cancel()
        lastDeletedLink.value = null
        bulkUndoDismissJob?.cancel()
        lastClosedTabs.value = tabs
        bulkUndoDismissJob = viewModelScope.launch {
            kotlinx.coroutines.delay(5000)
            if (lastClosedTabs.value === tabs) lastClosedTabs.value = null
        }
    }

    fun undoBulkClose(): Job = viewModelScope.launch {
        val tabs = lastClosedTabs.value ?: return@launch
        bulkUndoDismissJob?.cancel()
        lastClosedTabs.value = null
        // Defensive: per-tab runCatching keeps a partial failure from cancelling the rest.
        tabs.forEach { runCatching { repo.restoreTab(it) } }
    }

    fun dismissBulkUndo() {
        bulkUndoDismissJob?.cancel()
        lastClosedTabs.value = null
    }

    fun armLinkUndo(link: com.twinlore.app.domain.SavedTab) {
        linkUndoDismissJob?.cancel()
        undoDismissJob?.cancel()
        lastClosedTab.value = null
        bulkUndoDismissJob?.cancel()
        lastClosedTabs.value = null
        lastDeletedLink.value = link
        linkUndoDismissJob = viewModelScope.launch {
            kotlinx.coroutines.delay(5000)
            if (lastDeletedLink.value?.id == link.id) {
                lastDeletedLink.value = null
            }
        }
    }

    fun undoLinkDelete(): Job = viewModelScope.launch {
        val link = lastDeletedLink.value ?: return@launch
        linkUndoDismissJob?.cancel()
        lastDeletedLink.value = null
        savedTabRepo.restoreLinkBookmark(link)
    }

    fun dismissLinkUndo() {
        linkUndoDismissJob?.cancel()
        lastDeletedLink.value = null
    }

    fun switchTo(id: String): Job = viewModelScope.launch {
        repo.touch(id)
        // Both: the tab is going back to a page it was already on, at the place it was left.
        scrollRestorePendingForId = id
        switchReloadPendingForId = id
        loadingForTabId = id
        closeSwitcher()
    }

    /**
     * Called from BrowserScreen on tab switch or dispose with the live WebView's scrollY.
     * Synchronous flush: caller must await before changing the active tab.
     */
    /**
     * Records where the active tab is scrolled to.
     *
     * Refuses while that tab's saved position is still waiting to be put back. Between a switch
     * and its `onPageFinished`, every scroll the WebView reports belongs to a page that is being
     * rebuilt - 0 on a fresh document, then whatever the layout passes through - and writing any
     * of it over the row would erase the very number the restore is about to read. That is not
     * hypothetical: it is what a first attempt at fixing the fresh-guide bug did, and the tab
     * came back to the top of the page instead of to the paragraph it was left on.
     */
    fun flushActiveScrollY(currentScrollY: Int): Job = viewModelScope.launch {
        val active = activeTab.value ?: return@launch
        if (scrollRestorePendingForId == active.id) return@launch
        repo.updateScrollY(active.id, currentScrollY)
    }

    fun onPageStarted(url: String): Job = viewModelScope.launch {
        // Reserved for future progress UI; no-op for v1.
    }

    fun onPageFinished(url: String, title: String?): Job {
        // Snapshot flags + active tab id synchronously BEFORE launching, so
        // the call site's immediate consumeScrollRestoreFor / markLoadingComplete
        // calls (which run after onPageFinished returns but before the launched
        // coroutine executes) don't clobber the values the coroutine needs to
        // read. Without this snapshot every tab switch would misclassify as a
        // genuine navigation and push a duplicate history entry.
        val active = activeTab.value ?: return viewModelScope.launch { /* no-op */ }
        val isSwitchReload = switchReloadPendingForId == active.id
        val isHistoryReplay = navigatingViaHistoryFor == active.id
        val activeIdSnapshot = active.id
        val loadingSnapshot = loadingForTabId

        return viewModelScope.launch {
            if (loadingSnapshot != null && loadingSnapshot != activeIdSnapshot) return@launch
            val resolvedTitle = title ?: ""

            if (isSwitchReload || isHistoryReplay) {
                // Title-only update at the current history entry; do NOT push.
                repo.updateUrl(activeIdSnapshot, url, resolvedTitle)
            } else {
                // Genuine navigation: push, truncate forward, dedupe, cap.
                repo.recordNavigation(activeIdSnapshot, url, resolvedTitle)
            }
        }
    }

    fun onTitleReceived(title: String): Job = viewModelScope.launch {
        val active = activeTab.value ?: return@launch
        if (loadingForTabId != null && loadingForTabId != active.id) return@launch
        // Only update title; keep URL as-is.
        repo.updateUrl(active.id, active.url, title)
    }

    fun onFaviconReceived(bytes: ByteArray?): Job = viewModelScope.launch {
        val active = activeTab.value ?: return@launch
        if (loadingForTabId != null && loadingForTabId != active.id) return@launch
        repo.updateFavicon(active.id, bytes)
        // Harvest for the source menus: a visit the user made is the only place their
        // icons may come from (see SourceIconCache).
        if (bytes != null) sourceIconCache.store(active.url, bytes)
    }

    /** Returns true exactly once per pending switch; clears the flag. */
    fun consumeScrollRestoreFor(id: String): Boolean {
        if (scrollRestorePendingForId == id) {
            scrollRestorePendingForId = null
            return true
        }
        return false
    }

    /**
     * Clears the per-load guards once a page has settled for the given tab.
     *
     * The switch-reload flag is cleared here rather than by its own consume call: onPageFinished
     * reads it, and BrowserScreen calls this immediately afterwards, so this is the one point
     * where the load it describes is over. Leaving it set would make the tab's NEXT navigation
     * look like a reload and cost it its history entry.
     */
    fun markLoadingComplete(id: String) {
        if (loadingForTabId == id) loadingForTabId = null
        if (navigatingViaHistoryFor == id) navigatingViaHistoryFor = null
        if (switchReloadPendingForId == id) switchReloadPendingForId = null
    }

    /**
     * Called by BrowserScreen when it kicks off webView.loadUrl(activeTab.url)
     * for a tab change triggered by the reactive activeTab LaunchedEffect.
     * (switchTo/newTab set this already; this is for vault-open and closeTab-induced switches.)
     */
    fun markLoadingFor(id: String) {
        loadingForTabId = id
    }

    /**
     * Navigate the active tab one step back in its history. Returns true if
     * the move was possible. Caller is responsible for the "no, exit browser"
     * fallback when this returns false (e.g. the existing landing-page path
     * in BrowserScreen's BackHandler).
     *
     * Sets [navigatingViaHistoryFor] so the resulting onPageFinished does NOT
     * record a new history entry. The repo writes the new index + denormalized
     * url+title; the reactive LaunchedEffect in BrowserScreen sees the URL
     * change and issues wv.loadUrl.
     */
    fun navigateBack(): Boolean {
        val tab = activeTab.value ?: return false
        if (!tab.canGoBack) return false
        navigatingViaHistoryFor = tab.id
        loadingForTabId = tab.id
        viewModelScope.launch { repo.setHistoryIndex(tab.id, tab.historyIndex - 1) }
        return true
    }

    /** See [navigateBack]. */
    fun navigateForward(): Boolean {
        val tab = activeTab.value ?: return false
        if (!tab.canGoForward) return false
        navigatingViaHistoryFor = tab.id
        loadingForTabId = tab.id
        viewModelScope.launch { repo.setHistoryIndex(tab.id, tab.historyIndex + 1) }
        return true
    }

    companion object {
        /**
         * What a tab may be asked to open from outside: web pages. The landing
         * page is the one file:// document a tab shows, and it is only ever
         * chosen by [bind]'s provider, never passed in as [newTab]'s url.
         */
        fun isOpenableInTab(url: String): Boolean =
            url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)
    }
}
