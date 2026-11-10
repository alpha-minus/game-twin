package com.twinlore.app.ui.screens.browser

import androidx.compose.ui.graphics.toArgb
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.webkit.WebView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.hilt.navigation.compose.hiltViewModel
import com.twinlore.app.ui.MainViewModel
import com.twinlore.app.util.pauseHtmlMedia
import com.twinlore.app.data.FocusColor
import com.twinlore.app.data.HapticFeedbackIntensity
import com.twinlore.app.ui.screens.SettingsViewModel
import com.twinlore.app.ui.utils.FeedbackManager
import com.twinlore.app.ui.utils.FeedbackEvent
import com.twinlore.app.ui.utils.performHapticFeedback
import androidx.compose.ui.platform.LocalView
import com.twinlore.app.data.LandingEntry
import com.twinlore.app.data.defaultLandingEntries
import com.twinlore.app.data.SearchHistoryManager
import com.twinlore.app.data.SavedTabRepository
import com.twinlore.app.domain.SavedTab
import com.twinlore.app.di.SearchHistoryManagerEntryPoint
import com.twinlore.app.di.SavedTabRepositoryEntryPoint
import com.twinlore.app.di.BrowserSaveCoordinatorEntryPoint
import com.twinlore.app.di.ResourceResolverEntryPoint
import dagger.hilt.EntryPoints
import com.twinlore.app.domain.AssetType
import com.twinlore.app.domain.Vault
import com.twinlore.app.domain.VaultSessionMode
import java.io.File
import java.net.URL
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.SideEffect
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import android.util.Log
import androidx.compose.runtime.rememberUpdatedState
import com.twinlore.app.data.SearchEngine
import com.twinlore.app.ui.screens.GuideViewerScreenViewModel
import com.twinlore.app.ui.screens.MapViewerScreenViewModel
import com.twinlore.app.ui.screens.WebViewContent
import com.twinlore.app.ui.screens.GuideBrowserTopBar
import com.twinlore.app.ui.screens.vaultSessionTopBarChromeVisible
import com.twinlore.app.ui.screens.vaultSessionTopBarRenders
import com.twinlore.app.ui.screens.SaveTypeBottomSheet
import com.twinlore.app.ui.screens.SaveProgressOverlay
import com.twinlore.app.ui.screens.ToastOverlay
import com.twinlore.app.ui.screens.ImageDownloadOverlay
import com.twinlore.app.ui.screens.PdfDownloadOverlay
import com.twinlore.app.ui.screens.DeleteTabDialog
import com.twinlore.app.ui.screens.BrowserImageContextMenu
import com.twinlore.app.ui.screens.BrowserLinkContextMenu
import com.twinlore.app.ui.screens.SaveProgress
import com.twinlore.app.ui.screens.colorToHex
import com.twinlore.app.ui.screens.colorToRgba
import com.twinlore.app.ui.screens.colorToContrastHex
import com.twinlore.app.ui.screens.getLandingPageUrl
import com.twinlore.app.ui.screens.homeUrlForEngine
import com.twinlore.app.ui.screens.toFileUrl
import com.twinlore.app.ui.screens.validateFileExists
import com.twinlore.app.ui.screens.isAllowedWebViewUrl
import com.twinlore.app.ui.screens.isValidUrl
import com.twinlore.app.ui.screens.buildSearchUrlForEngine
import com.twinlore.app.ui.screens.isSearchUrlForEngine
import com.twinlore.app.ui.screens.resolveBestImageUrlForDownload
import com.twinlore.app.ui.components.LandingEntryEditDialog

/**
 * Browser Screen - Simple online WebView with landing page, search, save-as-bookmark.
 *
 * No: scroll restore, TOC sidebar, scroll markers, find-on-page, zoom controls,
 * text FAQ detection, offline toggle, guide picker.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, kotlinx.coroutines.FlowPreview::class)
@Composable
fun BrowserScreen(
    vaultId: String,
    mainViewModel: MainViewModel,
    guideViewModel: GuideViewerScreenViewModel = hiltViewModel(),
    sessionVault: Vault? = null,
    isTabVisible: Boolean = true,
    onBackFallback: (() -> Unit)? = null,
    contentFocusRequester: FocusRequester? = null,
    // Measured height of the outer VaultSessionTopBar (game name bar). Used to
    // offset this screen's own URL bar (GuideBrowserTopBar) down so the two top
    // chrome bars stack instead of overlapping at Y=0. Defaults to 0.dp so the
    // composable still works standalone (e.g. in previews).
    vaultTopBarHeightDp: androidx.compose.ui.unit.Dp = 0.dp,
    modifier: Modifier = Modifier
) {
    val vm: BrowserViewModel = hiltViewModel(key = "vault_session_browser")
    val tabsVm: BrowserTabsViewModel = hiltViewModel(key = "vault_session_browser_tabs")
    // Activity-scoped tabsVm keeps the prior vault's state until bind() runs
    // for this composition's vaultId — which happens in LaunchedEffect(vaultId)
    // below, AFTER first composition. Filter every read on the vaultId match so
    // a stale tab can never drive a wv.loadUrl whose onPageFinished would then
    // persist the wrong URL onto this vault's tab row. See BrowserTabsVaultFilter.kt.
    val rawActiveTab by tabsVm.activeTab.collectAsState()
    val rawTabsList by tabsVm.tabs.collectAsState()
    val activeTab = effectiveActiveTab(rawActiveTab, vaultId)
    val tabsList = effectiveTabsList(rawTabsList, vaultId)
    val canGoBack by tabsVm.canGoBack.collectAsState()
    val canGoForward by tabsVm.canGoForward.collectAsState()

    // Measured URL bar height (set by the AnimatedVisibility's
    // onGloballyPositioned further down) and the current display density,
    // hoisted up to function scope so the chrome/padding scroll-delta logic
    // below can read them without crossing scopes.
    val density = LocalDensity.current
    var measuredTopBarHeightDp by remember { mutableStateOf(0.dp) }

    // Publish the active tab's title to MainViewModel so VaultSessionTopBar can
    // prepend the page name to the game name. Landing pages don't count as a
    // document — null is published for those. Clear on dispose so leaving the
    // Browser tab restores the plain game name.
    LaunchedEffect(activeTab?.id, activeTab?.title, activeTab?.url) {
        val onLanding = activeTab?.url?.contains("landing.html") == true
        val title = activeTab?.title?.takeIf { it.isNotBlank() && !onLanding }
        mainViewModel.setActiveDocTitle(vaultId, com.twinlore.app.domain.VaultTab.Browser, title)
    }
    DisposableEffect(vaultId) {
        onDispose {
            mainViewModel.setActiveDocTitle(vaultId, com.twinlore.app.domain.VaultTab.Browser, null)
            mainViewModel.setBrowserUrlFieldFocused(false)
        }
    }
    val showSwitcher by tabsVm.showSwitcher.collectAsState()
    val lastClosedTab by tabsVm.lastClosedTab.collectAsState()
    val lastClosedTabs by tabsVm.lastClosedTabs.collectAsState()
    val vaultFlow = guideViewModel.getVault(vaultId)
    val vaultFromFlow by vaultFlow.collectAsState(initial = null)
    val effectiveVault = sessionVault ?: vaultFromFlow
    // Artwork lives in resource_files; resolve once at screen scope so all
    // landing-page calls (and getOnPageStartedJsExtras) see the same URI string.
    val effectiveVaultArtworkUri = com.twinlore.app.ui.utils.rememberResourceImage(effectiveVault?.artworkResourceId)
    val effectiveVaultArtworkPath = effectiveVaultArtworkUri?.toString()
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val screenPalette = com.twinlore.app.ui.LocalThemePalette.current
    val screenBgHex = remember(screenPalette) { String.format("#%06X", 0xFFFFFF and screenPalette.background.toArgb()) }
    val screenFgHex = remember(screenPalette) { String.format("#%06X", 0xFFFFFF and screenPalette.foreground.toArgb()) }

    val mapViewModel: MapViewerScreenViewModel = hiltViewModel()
    // Used by the bulk-save "Open" button to jump to the first saved chapter.
    val guideTabViewModel: com.twinlore.app.ui.screens.guide.GuideViewModel = hiltViewModel()

    val currentUrl by vm.currentUrl.collectAsState()
    val isLoading by vm.isLoading.collectAsState()

    val coroutineScope = rememberCoroutineScope()

    val state = remember { BrowserUiState() }

    // Local state for bookmark-from-star delete confirmation (not in BrowserUiState to avoid modifying shared class)
    var bookmarkToDeleteFromStar by remember { mutableStateOf<SavedTab?>(null) }
    var editingLandingSlot by remember { mutableStateOf<Int?>(null) }

    // Keep isTabVisible always up-to-date inside long-running LaunchedEffect(Unit) coroutines.
    val currentIsTabVisible by rememberUpdatedState(isTabVisible)

    // Clear save/download spinners once the guide tab takes over.
    LaunchedEffect(isTabVisible) {
        if (!isTabVisible) {
            if (state.isSavingBookmark.value) {
                state.isSavingBookmark.value = false
                state.saveProgress.value = null
            }
            state.pdfDownloadInProgress.value = false
            // Dismiss the post-save "Open / Close" card when the user
            // navigates away from the Browser tab so it doesn't reappear
            // as a ghost overlay on return.
            state.savedBookmarkForOverlay.value = null
        }
    }

    // Pause/resume WebView processing when tab visibility changes.
    LaunchedEffect(isTabVisible) {
        val wv = state.webView.value ?: return@LaunchedEffect
        if (isTabVisible) {
            wv.onResume()
            wv.evaluateJavascript("JSON.stringify({scrollY: window.scrollY || document.documentElement.scrollTop || 0, maxScrollY: Math.max(0, document.documentElement.scrollHeight - window.innerHeight)})") { result ->
                try {
                    val json = org.json.JSONObject(result.trim('"').replace("\\\"", "\""))
                    mainViewModel.updateWebViewScrollState(json.optInt("scrollY", 0), json.optInt("maxScrollY", 0))
                } catch (_: Throwable) {}
            }
        } else {
            tabsVm.flushActiveScrollY(wv.scrollY)
            // onPause() alone does not stop HTML5 media; pause it explicitly so a
            // background video stops decoding/compositing. See pauseHtmlMedia.
            wv.pauseHtmlMedia()
            wv.onPause()
        }
    }

    // SavedTabRepository for bookmark operations
    val savedTabRepository = remember {
        EntryPoints.get(
            context.applicationContext,
            SavedTabRepositoryEntryPoint::class.java
        ).savedTabRepository()
    }

    // BrowserSaveCoordinator routes saves into the home folder + resource_files/saved_tabs
    val browserSaveCoordinator = remember {
        EntryPoints.get(
            context.applicationContext,
            BrowserSaveCoordinatorEntryPoint::class.java
        ).browserSaveCoordinator()
    }

    // Bulk-save pipeline singletons used by the Save All Pages flow.
    // Mirrors the BrowserSaveCoordinatorEntryPoint access pattern used above so we
    // get a single resolution per screen entry, not per recomposition.
    val bulkSaveEntry = remember {
        EntryPoints.get(
            context.applicationContext,
            com.twinlore.app.di.BulkSaveEntryPoint::class.java
        )
    }
    val pageGroupSaveCoordinator = remember(bulkSaveEntry) { bulkSaveEntry.coordinator() }

    // Resolves a resource_files row id to a usable file:// or content:// Uri.
    val resourceResolver = remember {
        EntryPoints.get(
            context.applicationContext,
            ResourceResolverEntryPoint::class.java
        ).resourceResolver()
    }

    // Search history for landing page "Recent searches" section
    val searchHistoryManager = remember {
        EntryPoints.get(context.applicationContext, SearchHistoryManagerEntryPoint::class.java).searchHistoryManager()
    }
    val recentSearchesForLanding = remember(vaultId) { searchHistoryManager.getSearchHistory(vaultId).take(10) }

    // Track bookmarks for current vault
    val bookmarksFlow = remember(vaultId) { savedTabRepository.getTabsForVault(vaultId) }
    val bookmarksState by bookmarksFlow.collectAsState(initial = null as List<SavedTab>?)
    val bookmarks = bookmarksState ?: emptyList()

    val savedLinksFlow = remember(vaultId) { savedTabRepository.getLinkBookmarksForVault(vaultId) }
    val savedLinksState = savedLinksFlow.collectAsState(initial = emptyList())
    val savedLinks by savedLinksState

    // Cancel save operation when navigating away or vault changes
    DisposableEffect(vaultId) {
        onDispose {
            tabsVm.flushActiveScrollY(state.webView.value?.scrollY ?: 0)
            state.saveBookmarkJob.value?.cancel()
            state.saveBookmarkJob.value = null
            state.saveProgress.value = null
            state.isSavingBookmark.value = false
        }
    }

    // Focus color for highlights
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    // Drives showFoldButton below: this reproduces VaultSessionScreen's own
    // VaultSessionTopBar visibility rule (vaultSessionTopBarChromeVisible +
    // vaultSessionTopBarRenders, both in ui.screens.VaultSessionChrome.kt) from
    // Browser's own vantage point, so the embedded fold button steps in the moment
    // that bar is not actually on screen for ANY reason -- not just Hidden mode.
    val statusBarMode by settingsViewModel.statusBarMode.collectAsState(initial = com.twinlore.app.data.StatusBarMode.STANDARD)
    // Read the IME inset through derivedStateOf, not directly: its bottom is
    // snapshot state that ticks once per frame for the whole keyboard slide
    // animation, and a raw read here would invalidate this entire composable body
    // on every one of those frames. Only the open/closed flip matters to the fold
    // button, so derive the boolean and let Compose skip the frames in between.
    val isKeyboardVisibleForFoldButton by com.twinlore.app.ui.utils.rememberKeyboardVisible()
    val browserUrlFieldFocusedForFoldButton by mainViewModel.browserUrlFieldFocused.collectAsState()
    val browserChromeVisibleForFoldButton by mainViewModel.vaultSessionBrowserChromeVisible.collectAsState()
    val keepTopBarVisibleForFoldButton by settingsViewModel.keepTopBarVisible.collectAsState(initial = false)
    val vaultSessionTopBarVisible = vaultSessionTopBarRenders(
        chromeVisible = vaultSessionTopBarChromeVisible(
            isKeyboardVisible = isKeyboardVisibleForFoldButton,
            browserUrlFieldFocused = browserUrlFieldFocusedForFoldButton,
            // Evaluated from Browser's own vantage point: this only needs to be
            // correct for the moment Browser actually is the active tab, which is
            // the only moment this bar's fold button visibility matters.
            activeTab = com.twinlore.app.domain.VaultTab.Browser,
            anyWallpaperView = false, // Achievements-only term; neutralized above
            activeTabChromeVisible = browserChromeVisibleForFoldButton,
            keepTopBarVisible = keepTopBarVisibleForFoldButton,
        ),
    )
    val focusColorStateBrowser by settingsViewModel.focusColor.collectAsState(initial = FocusColor.CRIMSON)
    // Tap-game launched by double-tapping the guide-set download ring.
    var showTapGame by remember { mutableStateOf(false) }
    val tapGameHighScore by settingsViewModel.tapGameHighScore.collectAsState()
    val view = LocalView.current
    val focusSoundEnabled by settingsViewModel.focusSoundEnabled.collectAsState(initial = true)
    val hapticFeedbackIntensity by settingsViewModel.hapticFeedbackIntensity.collectAsState(initial = HapticFeedbackIntensity.MEDIUM)
    val cachedFocusColorBrowser = com.twinlore.app.ui.LocalCachedFocusColor.current?.value
    val focusColorBrowser: Color = cachedFocusColorBrowser ?: focusColorStateBrowser.color
    // Find-on-page highlight colors derived from focusColorBrowser. Mirrors GuideScreen so
    // both surfaces use the same visual treatment for matches.
    val focusColorHexBrowser = remember(focusColorBrowser) { colorToHex(focusColorBrowser) }
    val focusColorRgbaBrowser = remember(focusColorBrowser) { colorToRgba(focusColorBrowser) }
    val useBlueHighlight = focusColorBrowser == FocusColor.WHITE.color || focusColorBrowser == FocusColor.BLACK.color
    val highlightBlue = FocusColor.ELECTRIC_BLUE.color
    val effectiveHighlightHexBrowser = remember(focusColorBrowser) {
        if (useBlueHighlight) colorToHex(highlightBlue) else focusColorHexBrowser
    }
    val effectiveHighlightRgbaBrowser = remember(focusColorBrowser) {
        if (useBlueHighlight) colorToRgba(highlightBlue) else focusColorRgbaBrowser
    }
    val effectiveContrastHexBrowser = remember(focusColorBrowser) {
        colorToContrastHex(if (useBlueHighlight) highlightBlue else focusColorBrowser)
    }

    // Show chrome on user action. Idempotent when chrome is already visible.
    fun updateInteraction() {
        state.showTopBar.value = true
    }

    // Handle global controller actions — only when this tab is visible.
    // Subscribe inside a visibility-keyed LaunchedEffect rather than collectAsState
    // so hidden tabs don't recompose on every controllerAction emission.
    val urlFieldFocusRequester = remember { FocusRequester() }
    LaunchedEffect(isTabVisible) {
        if (!isTabVisible) return@LaunchedEffect
        mainViewModel.controllerAction.collect { action ->
        when (action) {
            MainViewModel.ControllerAction.TOGGLE_MAP_BROWSER -> {
                updateInteraction()
            }
            MainViewModel.ControllerAction.OPEN_FAVORITE_MENU -> {
                // No-op in Browser mode after bookmark dialog removal.
                // Guide-mode handling lives in GuideScreen.
                updateInteraction()
            }
            MainViewModel.ControllerAction.FOCUS_SEARCH_BAR -> {
                updateInteraction()
                state.focusSearchBarTrigger.value = true
            }
            // Guide-only actions — no-op in Browser
            MainViewModel.ControllerAction.OPEN_FIND_ON_PAGE,
            MainViewModel.ControllerAction.OPEN_TOC,
            MainViewModel.ControllerAction.FIND_NEXT_RESULT,
            MainViewModel.ControllerAction.FIND_PREVIOUS_RESULT,
            MainViewModel.ControllerAction.BROWSER_SCROLL_MARKER_PREV,
            MainViewModel.ControllerAction.BROWSER_SCROLL_MARKER_NEXT,
            MainViewModel.ControllerAction.BROWSER_PREV_BOOKMARK,
            MainViewModel.ControllerAction.BROWSER_NEXT_BOOKMARK -> { }
            MainViewModel.ControllerAction.BROWSER_BACK,
            MainViewModel.ControllerAction.BROWSER_FORWARD,
            MainViewModel.ControllerAction.BROWSER_SCROLL_TO_TOP,
            MainViewModel.ControllerAction.BROWSER_SCROLL_TO_END,
            MainViewModel.ControllerAction.BROWSER_RESET_ZOOM -> {
                // Handled in the LaunchedEffect below (after getLandingPageUrl/currentSearchEngine are in scope)
            }
            MainViewModel.ControllerAction.ACHIEVEMENTS_REFRESH,
            MainViewModel.ControllerAction.ACHIEVEMENTS_WALLPAPER,
            MainViewModel.ControllerAction.MAP_NEXT_FILE,
            MainViewModel.ControllerAction.MAP_PREV_FILE,
            MainViewModel.ControllerAction.CHECKLIST_ADD_ITEM,
            MainViewModel.ControllerAction.CHECKLIST_TAB_NEXT,
            MainViewModel.ControllerAction.CHECKLIST_TAB_PREV -> { }
            MainViewModel.ControllerAction.NONE -> { }
        }
        }
    }

    // Current search engine from settings
    val currentSearchEngine by settingsViewModel.searchEngine.collectAsState(initial = SearchEngine.GOOGLE)
    val privacyBlockingEnabled by settingsViewModel.privacyBlockingEnabled.collectAsState(initial = true)
    val landingEntriesList by settingsViewModel.landingEntries.collectAsState(
        initial = defaultLandingEntries()
    )
    SideEffect { state.landingEntriesRef.value = landingEntriesList }

    // Helper function to load file:// URLs properly
    fun loadFileUrl(webView: WebView, url: String) {
        if (url.startsWith("content://")) {
            webView.loadUrl(url)
            vm.setUrl(url)
            return
        }
        val fileUrl = toFileUrl(url)
        if (validateFileExists(fileUrl)) {
            webView.loadUrl(fileUrl)
            vm.setUrl(fileUrl)
        } else {
            Log.w("BrowserScreen", "File does not exist: $fileUrl")
            state.toastMessage.value = "Offline version not available"
            val landingPageUrl = getLandingPageUrl(context, vaultId, effectiveVaultArtworkPath, landingEntriesList, recentSearchesForLanding, savedLinks = savedLinks, paletteBgHex = screenBgHex, paletteFgHex = screenFgHex, gameName = effectiveVault?.displayName)
            val fallbackUrl = landingPageUrl ?: homeUrlForEngine(currentSearchEngine)
            webView.loadUrl(fallbackUrl)
            vm.setUrl(fallbackUrl)
        }
    }

    // rememberUpdatedState so the landing provider closure (passed to tabsVm.bind below)
    // reads the LATEST artwork path / entries / palette / savedLinks each time it's
    // invoked — not stale snapshots from first composition. Same vals are reused by
    // the tabReselected collect lambda further down.
    val currentArtworkPath by rememberUpdatedState(effectiveVaultArtworkPath)
    val currentLandingEntries by rememberUpdatedState(landingEntriesList)
    val currentRecentSearches by rememberUpdatedState(recentSearchesForLanding)
    val currentSavedLinks by rememberUpdatedState(savedLinks)
    val currentBgHex by rememberUpdatedState(screenBgHex)
    val currentFgHex by rememberUpdatedState(screenFgHex)
    val currentSearchEngineState by rememberUpdatedState(currentSearchEngine)
    LaunchedEffect(vaultId) {
        // Reset Activity-scoped BrowserViewModel state that carried over from
        // the previous vault session, so the "safety reload" LaunchedEffect at
        // the bottom of this file doesn't honor a stale URL during the brief
        // window before bind() repoints activeTab to this vault. Paired with
        // the activeTab-match gate down there. See BrowserTabsVaultFilter.kt.
        vm.setUrl(null)
        val landingProvider: () -> String = {
            getLandingPageUrl(
                context, vaultId, currentArtworkPath,
                currentLandingEntries, currentRecentSearches,
                savedLinks = currentSavedLinks,
                paletteBgHex = currentBgHex, paletteFgHex = currentFgHex,
                gameName = effectiveVault?.displayName
            ) ?: homeUrlForEngine(currentSearchEngineState)
        }
        tabsVm.bind(vaultId, landingProvider)

        val pendingFromMain = mainViewModel.consumePendingUrlForBrowserTab(vaultId)
        val prefs = context.getSharedPreferences("vault_temp", android.content.Context.MODE_PRIVATE)
        val pendingKey = "pending_search_url_$vaultId"
        val pendingFromPrefs = prefs.getString(pendingKey, null)?.also {
            prefs.edit().remove(pendingKey).apply()
        }
        val pendingUrl = pendingFromMain ?: pendingFromPrefs

        if (pendingUrl != null) {
            tabsVm.newTab(pendingUrl, background = false)
        }
        // else: do nothing. `bind()` seeds a landing tab via ensureAtLeastOne
        // if needed, and the observer Flow will deliver the persisted list
        // (head = most-recently-accessed tab). The existing
        // LaunchedEffect(state.webView.value, activeTab?.id) below loads it.
    }

    // Reactive: open a new tab whenever another vault-session tab (Map / Achievements /
    // Guide picker / etc.) queues a pending URL via MainViewModel.setPendingUrlForBrowserTab.
    // The initial emission is skipped via drop(1) because the LaunchedEffect(vaultId) above
    // already consumed any pre-existing pending URL on first composition.
    LaunchedEffect(vaultId) {
        mainViewModel.pendingUrlForBrowserTab
            .map { it[vaultId] }
            .distinctUntilChanged()
            .drop(1)
            .collect { pending ->
                if (pending != null) {
                    mainViewModel.consumePendingUrlForBrowserTab(vaultId)
                    // Flush the outgoing tab's scrollY onto ITS row before newTab makes
                    // the new tab active — otherwise a later flushActiveScrollY writes the
                    // old (often bottom-of-a-long-page) scroll onto the fresh tab, and
                    // onPageFinished "restores" the new page clamped to the bottom.
                    // Mirrors onOpenInNewTab / onOpenSavedLink.
                    tabsVm.flushActiveScrollY(state.webView.value?.scrollY ?: 0)
                    tabsVm.newTab(url = pending, background = false)
                }
            }
    }

    // Start a WebView-style download (bundle / pdf / image) programmatically.
    // Shared by the WebView DownloadListener and the code-wheel routing below so
    // both drive the identical banner + save-and-attach flow. [refererOverride]
    // sets the HTTP Referer, defaulting to the active tab's URL.
    fun beginDownload(
        url: String,
        mimetype: String,
        contentDisposition: String,
        refererOverride: String?,
    ) {
        val isPdf = mimetype.equals("application/pdf", ignoreCase = true) ||
            url.lowercase().endsWith(".pdf") ||
            contentDisposition.lowercase().contains(".pdf")
        val urlPathLower = url.lowercase().substringBefore('?')
        val dispoLower = contentDisposition.lowercase()
        val isImage = !isPdf && (
            mimetype.startsWith("image/", ignoreCase = true) ||
                IMAGE_DOWNLOAD_EXTENSIONS.any { urlPathLower.endsWith(it) } ||
                IMAGE_DOWNLOAD_EXTENSIONS.any { dispoLower.contains(it) }
            )
        val isBundle = !isPdf && !isImage && (
            mimetype.equals("application/zip", ignoreCase = true) ||
                mimetype.equals("application/x-zip-compressed", ignoreCase = true) ||
                urlPathLower.endsWith(".zip") ||
                dispoLower.contains(".zip")
            )
        state.downloadIsBundle.value = isBundle
        if (isPdf || isBundle) state.pdfDownloadInProgress.value = true
        if (isImage) state.imageDownloadInProgress.value = true
        val job = coroutineScope.launch {
            try {
                val refererUrl = refererOverride
                    ?: withContext(Dispatchers.Main) { state.webView.value?.url }
                val pageTitle = if (isImage) withContext(Dispatchers.Main) { state.webView.value?.title } else null
                val result = browserSaveCoordinator.handleDownload(
                    url = url,
                    contentType = mimetype,
                    contentDisposition = contentDisposition,
                    vaultId = vaultId,
                    referer = refererUrl
                )
                withContext(Dispatchers.Main) {
                    when (result) {
                        is com.twinlore.app.data.library.SaveResult.Success -> {
                            val savedTabId = result.savedTabId
                            when {
                                (isPdf || isBundle) && savedTabId != null -> {
                                    state.pdfDownloadInProgress.value = false
                                    state.pdfDownloadJob.value = null
                                    val tab = withContext(Dispatchers.IO) {
                                        savedTabRepository.getTabById(savedTabId)
                                    }
                                    if (tab != null) {
                                        state.pdfDownloadDoneBookmark.value = tab
                                    } else {
                                        // Fall back to legacy behaviour if DB lookup misses.
                                        mainViewModel.setPendingGuideBookmarkId(vaultId, savedTabId)
                                        mainViewModel.requestGuideTabSwitch(vaultId)
                                    }
                                }
                                isImage && result.resourceFileId.isNotBlank() -> {
                                    val mapName = pageTitle?.take(60)?.trim()?.ifBlank { null }
                                        ?: url.substringAfterLast('/').substringBeforeLast('.')
                                            .replace(Regex("[_\\-]+"), " ").trim().ifBlank { null }
                                        ?: "Image"
                                    mapViewModel.addMap(vaultId, mapName, result.resourceFileId)
                                    mapViewModel.notifyMapReplaced(vaultId)
                                    state.imageDownloadInProgress.value = false
                                    state.imageDownloadJob.value = null
                                    state.imageDownloadDoneMapName.value = mapName
                                }
                                else -> {
                                    if (isPdf || isBundle) state.pdfDownloadInProgress.value = false
                                    if (isImage) state.imageDownloadInProgress.value = false
                                    state.toastMessage.value = "Download saved"
                                }
                            }
                        }
                        is com.twinlore.app.data.library.SaveResult.Failed -> {
                            Log.e("BrowserScreen", "Download failed", result.cause)
                            if (isPdf || isBundle) state.pdfDownloadInProgress.value = false
                            if (isImage) state.imageDownloadInProgress.value = false
                            state.toastMessage.value = if (isPdf) {
                                "Failed to download PDF: ${result.cause.message ?: "unknown"}"
                            } else {
                                "Download failed: ${result.cause.message ?: "unknown"}"
                            }
                        }
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                withContext(Dispatchers.Main) {
                    if (isPdf || isBundle) state.pdfDownloadInProgress.value = false
                    if (isImage) state.imageDownloadInProgress.value = false
                }
                throw e
            }
        }
        if (isPdf || isBundle) state.pdfDownloadJob.value = job
        if (isImage) state.imageDownloadJob.value = job
    }

    // Code-wheel routing: when the Guide tab queues a code-wheel download, run it
    // through the same banner/download flow. The archive.org details page (opened
    // via the pending-URL channel above) stays visible behind the banner.
    LaunchedEffect(vaultId) {
        mainViewModel.pendingCodeWheelDownloadByVault
            .map { it[vaultId] }
            .distinctUntilChanged()
            .collect { pending ->
                if (pending != null) {
                    mainViewModel.consumePendingCodeWheelDownload(vaultId)
                    beginDownload(
                        url = pending.downloadUrl,
                        mimetype = "application/zip",
                        contentDisposition = "attachment; filename=\"${pending.filename}\"",
                        refererOverride = com.twinlore.app.data.codewheel.CodeWheelEntry.SUPPORT_URL,
                    )
                }
            }
    }

    // When the active tab changes (vault open, switchTo, newTab, closeTab → new active),
    // load it into the WebView. Re-keyed on the WebView itself so the first composition
    // (when state.webView.value is still null) re-fires once the AndroidView factory
    // produces the WebView.
    LaunchedEffect(state.webView.value, activeTab?.id, activeTab?.url) {
        val wv = state.webView.value ?: return@LaunchedEffect
        val tab = activeTab ?: return@LaunchedEffect
        // Skip reload only when URLs match AND it's not landing.html. Landing is
        // regenerated each call (artwork/savedLinks/recentSearches refresh) so we
        // always want to reload it; non-landing URLs preserve their loaded state.
        if (wv.url == tab.url && !tab.url.contains("landing.html")) return@LaunchedEffect
        tabsVm.markLoadingFor(tab.id)
        vm.setLoading(true)
        wv.loadUrl(tab.url)
        vm.setUrl(tab.url)
    }

    // Show chrome when the bottom bar (or any tracked chrome surface) receives focus.
    LaunchedEffect(isTabVisible) {
        if (!isTabVisible) return@LaunchedEffect
        mainViewModel.chromeInteractionTrigger.collect {
            updateInteraction()
        }
    }

    // Sync bottom bar with top bar in vault session. The switcher overlay
    // shows its own "Tabs (N)" header but the vault's game-name top bar still
    // belongs above it — hiding the chrome here used to leave the switcher
    // butted up against the status bar with no visual continuity with the
    // browser view.
    LaunchedEffect(state.showTopBar.value, state.isUrlFieldFocused.value, isTabVisible, showSwitcher) {
        if (isTabVisible) {
            // Mirrors guide-mode behavior: leave the outer game-name top bar visible even
            // while find-on-page is open. Only the inner URL/icons row hides; the find bar
            // then sits naturally below the outer bar instead of at the very top edge.
            //
            // Also force the outer bar visible while the URL field is focused: when the
            // user taps the search bar to type, the keyboard's appearance can shift the
            // layout enough that the scroll-driven chrome watcher hides the chrome. The
            // user is actively browsing to a new page and needs the page-name strip to
            // stay visible for context.
            val chromeVisible = state.showTopBar.value || state.isUrlFieldFocused.value
            mainViewModel.setVaultSessionBrowserChromeVisible(chromeVisible)
        }
    }

    // Surface VM toast events into the existing toast overlay state.
    LaunchedEffect(Unit) {
        tabsVm.toastEvent.collect { msg ->
            state.toastMessage.value = msg
        }
    }

    // Debounced scroll-Y flush per active tab.
    //
    // `drop(1)` is what makes this per-tab rather than per-collector: `vm.scrollPosition` is a
    // StateFlow, so restarting this effect on an activeTab change replays the outgoing page's
    // offset to the new collector, and half a second later that number was written onto the tab
    // that had just become active. On a tab opened from the vault's browse menu - a guide, say -
    // the fresh row then claimed a scroll of several thousand pixels that belonged to the page the
    // user had been reading. The first value after a switch is never news about the new tab; only
    // what the WebView reports afterwards is.
    LaunchedEffect(activeTab?.id) {
        vm.scrollPosition
            .drop(1)
            .debounce(500)
            .collect { y -> tabsVm.flushActiveScrollY(y) }
    }

    // Scroll-delta-driven chrome + WebView top-padding for the Browser tab.
    //
    // Single source of truth: `webViewTopPadPx` tracks how much top padding
    // the WebView currently has (0..totalChromeHeight). Every scroll delta is
    // subtracted from it (scroll-down shrinks the padding; scroll-up grows it
    // back), clamped at both ends. Chrome visibility is then derived from
    // padding > 0 — when the WebView has room reserved at the top, chrome
    // fills that room; once the padding drains to 0, chrome hides.
    //
    // Why: ties chrome show/hide motion to the finger 1:1 instead of a
    // timer-based animation. iOS Safari's URL bar uses the same coupling.
    // The earlier scroll-direction rule animated chrome alpha over 200 ms
    // while the user's finger kept moving, which read as detached motion.
    //
    // At-top and at-bottom anchors override: scrollY == 0 pins padding to
    // full (page-top stays visible just below the URL bar); at the document
    // bottom, chrome force-shows regardless of padding so the bottom tab bar
    // is reachable.
    val totalChromeHeightDp = vaultTopBarHeightDp + measuredTopBarHeightDp
    val totalChromeHeightPx = with(density) { totalChromeHeightDp.toPx() }
    val totalChromeHeightPxLive by rememberUpdatedState(totalChromeHeightPx)
    val isLoadingLive by rememberUpdatedState(isLoading)
    var webViewTopPadPx by remember { mutableStateOf(0f) }
    // Re-seed/clamp when the bars finally measure (they start at 0.dp).
    LaunchedEffect(totalChromeHeightPx) {
        if (vm.scrollPosition.value == 0 && totalChromeHeightPx > 0f) {
            webViewTopPadPx = totalChromeHeightPx
        } else {
            webViewTopPadPx = webViewTopPadPx.coerceAtMost(totalChromeHeightPx)
        }
    }
    // While a page is loading, force chrome (top bars + progress strip) visible
    // so the user sees they triggered a navigation. The scroll-delta consumer
    // above also short-circuits on isLoading so a downward scroll mid-load can't
    // drain webViewTopPadPx. When isLoading flips back to false the next scroll
    // delta resumes the normal rule from `maxPad`.
    //
    // We push the vault chrome flag directly here in addition to flipping
    // `state.showTopBar`, because the LaunchedEffect that derives the vault
    // flag is keyed on `state.showTopBar.value` — if that was already true
    // (e.g. the user tapped a link while chrome was visible), the keyed
    // effect won't re-fire and the vault flag could remain stale-false from
    // a prior interaction, leaving the URL bar visible but the outer
    // game-name strip blank.
    LaunchedEffect(isLoading) {
        if (isLoading) {
            state.showTopBar.value = true
            if (totalChromeHeightPx > 0f) webViewTopPadPx = totalChromeHeightPx
            if (isTabVisible) {
                mainViewModel.setVaultSessionBrowserChromeVisible(true)
            }
        }
    }
    // Consume scroll deltas into the padding.
    LaunchedEffect(Unit) {
        var prevScrollY = vm.scrollPosition.value
        vm.scrollPosition.collect { scrollY ->
            val delta = (scrollY - prevScrollY).toFloat()
            val maxPad = totalChromeHeightPxLive
            webViewTopPadPx = applyScrollDelta(
                currentPad = webViewTopPadPx,
                delta = delta,
                maxPad = maxPad,
                isLoading = isLoadingLive
            )
            prevScrollY = scrollY
            if (scrollY == 0 && maxPad > 0f) webViewTopPadPx = maxPad
        }
    }
    // Chrome visibility tracks the scroll-driven top padding only. (No at-bottom
    // anchor: reaching the page bottom no longer force-shows the chrome — if you
    // scrolled down to get there, the padding has drained and the chrome stays
    // hidden.) At-top pinning is handled in the scroll-delta consumer above.
    LaunchedEffect(Unit) {
        androidx.compose.runtime.snapshotFlow { webViewTopPadPx > 0f }
            .collect { nextVisible ->
                if (nextVisible != state.showTopBar.value) {
                    state.showTopBar.value = nextVisible
                }
            }
    }

    // Browser mode: do NOT load anything from WebViewContent's factory. The
    // BrowserTabsViewModel-driven LaunchedEffect(state.webView.value, activeTab?.id)
    // below loads the persisted head tab as soon as it arrives from the observer.
    // If we let the factory load landing here, its trailing onPageFinished races
    // with our load and overwrites the persisted URL back to landing.
    val initialUrl = ""

    // The artwork URI is resolved async via rememberResourceImage, so on the
    // first composition `effectiveVaultArtworkPath` is null and the initial
    // landing page was generated without artwork. When the URI arrives,
    // regenerate the landing HTML in place and reload the WebView if it's
    // currently on the landing page.
    val webViewForArtworkRefresh = state.webView.value
    LaunchedEffect(effectiveVaultArtworkPath, vaultId, webViewForArtworkRefresh) {
        val artwork = effectiveVaultArtworkPath ?: return@LaunchedEffect
        val wv = webViewForArtworkRefresh ?: return@LaunchedEffect
        val onLanding = (wv.url ?: vm.currentUrl.value ?: "").contains("landing.html")
        if (!onLanding) return@LaunchedEffect
        val refreshedUrl = getLandingPageUrl(
            context, vaultId, artwork, landingEntriesList, recentSearchesForLanding,
            savedLinks = savedLinks,
            paletteBgHex = screenBgHex, paletteFgHex = screenFgHex,
            gameName = effectiveVault?.displayName
        ) ?: return@LaunchedEffect
        vm.setLoading(true)
        if (refreshedUrl.startsWith("file://")) {
            loadFileUrl(wv, refreshedUrl)
        } else {
            wv.loadUrl(refreshedUrl)
            vm.setUrl(refreshedUrl)
        }
    }

    // When saved-links flow emits (add/delete/restore), regenerate landing HTML
    // and reload if the WebView is currently on the landing page.
    LaunchedEffect(savedLinks) {
        val wv = state.webView.value ?: return@LaunchedEffect
        val onLanding = (wv.url ?: vm.currentUrl.value ?: "").contains("landing.html")
        if (!onLanding) return@LaunchedEffect
        val refreshedUrl = getLandingPageUrl(
            context, vaultId, effectiveVaultArtworkPath,
            landingEntriesList, recentSearchesForLanding,
            savedLinks = savedLinks,
            paletteBgHex = screenBgHex, paletteFgHex = screenFgHex,
            gameName = effectiveVault?.displayName
        ) ?: return@LaunchedEffect
        vm.setLoading(true)
        if (refreshedUrl.startsWith("file://")) {
            loadFileUrl(wv, refreshedUrl)
        } else {
            wv.loadUrl(refreshedUrl)
            vm.setUrl(refreshedUrl)
        }
    }

    // When the Browser tab button is tapped while already active, navigate to the landing page.
    // Reuses the currentXxx state holders declared above (rememberUpdatedState).
    LaunchedEffect(Unit) {
        mainViewModel.tabReselected.collect { tab ->
            if (tab == com.twinlore.app.domain.VaultTab.Browser) {
                tabsVm.closeSwitcher()
                val landingUrl = getLandingPageUrl(
                    context, vaultId, currentArtworkPath,
                    currentLandingEntries, currentRecentSearches,
                    savedLinks = currentSavedLinks,
                    paletteBgHex = currentBgHex, paletteFgHex = currentFgHex,
                    gameName = effectiveVault?.displayName
                )
                if (landingUrl != null) {
                    val wv = state.webView.value
                    if (wv != null) {
                        vm.setLoading(true)
                        if (landingUrl.startsWith("file://")) {
                            loadFileUrl(wv, landingUrl)
                        } else {
                            wv.loadUrl(landingUrl)
                            vm.setUrl(landingUrl)
                        }
                    }
                }
            }
        }
    }

    // Handle system back gesture
    BackHandler(enabled = isTabVisible) {
        val webViewUrl = state.webView.value?.url
        val isLandingPage = webViewUrl != null && webViewUrl.contains("landing.html")

        if (isLandingPage) {
            onBackFallback?.invoke()
            return@BackHandler
        }
        if (canGoBack) {
            tabsVm.navigateBack()
        } else {
            // No history - navigate to landing page
            state.isUserNavigation.value = true
            val landingPageUrl = getLandingPageUrl(context, vaultId, effectiveVaultArtworkPath, landingEntriesList, searchHistoryManager.getSearchHistory(vaultId).take(10), savedLinks = savedLinks, paletteBgHex = screenBgHex, paletteFgHex = screenFgHex, gameName = effectiveVault?.displayName)
            if (landingPageUrl != null) {
                vm.setLoading(true)
                if (landingPageUrl.startsWith("file://")) {
                    loadFileUrl(state.webView.value!!, landingPageUrl)
                } else {
                    state.webView.value?.loadUrl(landingPageUrl)
                }
                vm.setUrl(landingPageUrl)
                state.webView.value?.postDelayed({
                    state.isUserNavigation.value = false
                }, 500)
            } else {
                val homeUrl = homeUrlForEngine(currentSearchEngine)
                vm.setLoading(true)
                state.webView.value?.loadUrl(homeUrl)
                vm.setUrl(homeUrl)
                state.webView.value?.postDelayed({
                    state.isUserNavigation.value = false
                }, 500)
            }
        }
    }

    // L1/R1 browser back/forward and scroll-to-top/end
    LaunchedEffect(isTabVisible) {
        if (!isTabVisible) return@LaunchedEffect
        mainViewModel.controllerAction.collect { action ->
        when (action) {
            MainViewModel.ControllerAction.BROWSER_BACK -> {
                val webViewUrl = state.webView.value?.url
                val isLandingPage = webViewUrl != null && webViewUrl.contains("landing.html")
                if (!isLandingPage) {
                    if (canGoBack) {
                        tabsVm.navigateBack()
                    } else {
                        state.isUserNavigation.value = true
                        val landingPageUrl = getLandingPageUrl(context, vaultId, effectiveVaultArtworkPath, landingEntriesList, recentSearchesForLanding, savedLinks = savedLinks, paletteBgHex = screenBgHex, paletteFgHex = screenFgHex, gameName = effectiveVault?.displayName)
                        if (landingPageUrl != null) {
                            vm.setLoading(true)
                            if (landingPageUrl.startsWith("file://")) {
                                state.webView.value?.let { loadFileUrl(it, landingPageUrl) }
                            } else {
                                state.webView.value?.loadUrl(landingPageUrl)
                            }
                            vm.setUrl(landingPageUrl)
                            state.webView.value?.postDelayed({ state.isUserNavigation.value = false }, 500)
                        } else {
                            val homeUrl = homeUrlForEngine(currentSearchEngine)
                            vm.setLoading(true)
                            state.webView.value?.loadUrl(homeUrl)
                            vm.setUrl(homeUrl)
                            state.webView.value?.postDelayed({ state.isUserNavigation.value = false }, 500)
                        }
                    }
                }
                updateInteraction()
            }
            MainViewModel.ControllerAction.BROWSER_FORWARD -> {
                if (canGoForward) {
                    tabsVm.navigateForward()
                }
                updateInteraction()
            }
            MainViewModel.ControllerAction.BROWSER_SCROLL_TO_TOP -> {
                state.webView.value?.evaluateJavascript("window.scrollTo(0,0);", null)
            }
            MainViewModel.ControllerAction.BROWSER_SCROLL_TO_END -> {
                state.webView.value?.evaluateJavascript("""
                    (function() {
                        var h = Math.max(
                            document.body.scrollHeight || 0,
                            document.documentElement.scrollHeight || 0,
                            document.body.offsetHeight || 0,
                            document.documentElement.offsetHeight || 0
                        );
                        window.scrollTo(0, h);
                        return h;
                    })();
                """.trimIndent(), null)
            }
            MainViewModel.ControllerAction.BROWSER_RESET_ZOOM -> {
                updateInteraction()
            }
            else -> { }
        }
        }
    }

    // Handle browser scroll from D-pad/analog stick
    LaunchedEffect(isTabVisible) {
        if (!isTabVisible) return@LaunchedEffect
        val deadzone = 0.1f
        while (true) {
            if (state.webView.value == null) { delay(200); continue }
            val scroll = mainViewModel.browserScroll.value
            val panX = mainViewModel.browserPanX.value
            val hasScrollInput = kotlin.math.abs(scroll) > deadzone || kotlin.math.abs(panX) > deadzone
            if (hasScrollInput) {
                val scrollY = (scroll * 56).toInt()
                val scrollX = (panX * 56).toInt()
                if (scrollY != 0 || scrollX != 0) {
                    state.webView.value?.scrollBy(scrollX, scrollY)
                }
                delay(16)
            } else {
                delay(200)
            }
        }
    }

    // Save image launcher
    val saveImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/*")
    ) { uri: Uri? ->
        val url = state.pendingSaveImageUrl.value
        state.pendingSaveImageUrl.value = null
        if (uri != null && url != null) {
            state.isAssigningImage.value = true
            state.assigningImageLabel.value = "Saving image\u2026"
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val connection = URL(url).openConnection()
                    connection.connectTimeout = 10000
                    connection.readTimeout = 10000
                    connection.connect()
                    val bytes = connection.getInputStream().readBytes()
                    context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                    withContext(Dispatchers.Main) {
                        state.isAssigningImage.value = false
                        state.assigningImageLabel.value = ""
                        state.selectedImageUrl.value = null
                        android.widget.Toast.makeText(context, "Image saved", android.widget.Toast.LENGTH_SHORT).show()
                        updateInteraction()
                    }
                } catch (e: Exception) {
                    Log.e("BrowserScreen", "Error saving image", e)
                    withContext(Dispatchers.Main) {
                        state.isAssigningImage.value = false
                        state.assigningImageLabel.value = ""
                        state.selectedImageUrl.value = null
                        android.widget.Toast.makeText(context, "Error: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                        updateInteraction()
                    }
                }
            }
        } else {
            state.selectedImageUrl.value = null
            updateInteraction()
        }
    }

    // Browser content
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .onKeyEvent { keyEvent ->
                val keyCode = keyEvent.nativeKeyEvent.keyCode
                if (keyCode != android.view.KeyEvent.KEYCODE_DPAD_UP && keyCode != android.view.KeyEvent.KEYCODE_DPAD_DOWN) {
                    updateInteraction()
                }
                false
            }
    ) {
        // density + measuredTopBarHeightDp hoisted to function scope above.

        val isOnLandingPage = (currentUrl ?: state.webView.value?.url ?: "").contains("landing.html")
        when {
            effectiveVault == null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Loading...")
                }
            }
            else -> {
                // WebView content
                key(vaultId) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        WebViewContent(
                            initialUrl = initialUrl,
                            webView = { wv ->
                                state.webView.value = wv
                                wv.setDownloadListener { url, _, contentDisposition, mimetype, _ ->
                                    beginDownload(url, mimetype, contentDisposition, refererOverride = null)
                                }
                            },
                            favoritePath = null,
                            onUrlChanged = { url ->
                                // Only update the URL-bar text. Do NOT force chrome
                                // visible here: onPageFinished fires onUrlChanged on
                                // every page-finish, and dynamic sites (ads, lazy
                                // load, infinite scroll, SPA history) re-finish
                                // repeatedly while the user scrolls, which would
                                // re-show the chrome and fight the scroll-hide rule.
                                // Genuine navigations still reveal chrome via the
                                // LaunchedEffect(isLoading) force-show above.
                                vm.setUrl(url)
                            },
                            onCanGoBackChanged = { /* per-tab VM-driven; ignore WebView's view */ },
                            onCanGoForwardChanged = { /* per-tab VM-driven; ignore WebView's view */ },
                            onLoadingChanged = { loading ->
                                vm.setLoading(loading)
                            },
                            zoomLevel = 1f,
                            onZoomChanged = { },
                            onZoomIn = { },
                            onZoomOut = { },
                            onResetZoom = null,
                            showTopBar = state.showTopBar.value,
                            hasToc = false,
                            onOpenTocDrawer = null,
                            onScrollChanged = { position, _ ->
                                vm.setScrollPosition(position)
                            },
                            getSavedScrollRestore = { null },
                            getScrollPosition = { 0 },
                            onNewWindowLoadInCurrentTab = { },
                            onScrollRestoreStarted = { },
                            onScrollRestoreCompleted = { },
                            isLoading = isLoading,
                            isRestoringScroll = false,
                            onTouch = {
                                // Tap toggles chrome by driving the SINGLE source of
                                // truth (webViewTopPadPx). Setting showTopBar directly
                                // here was the bug: it's derived from webViewTopPadPx
                                // via the snapshotFlow, which immediately overrode the
                                // tap — so tap-to-hide left the WebView padded and an
                                // empty strip uncleared at the top. Suppressed while a
                                // page loads (a link tap races the loading force-show).
                                // Reads vm.isLoading.value directly for the latest value
                                // (lambda captured at AndroidView factory time).
                                if (!vm.isLoading.value) {
                                    webViewTopPadPx =
                                        if (webViewTopPadPx > 0f) 0f else totalChromeHeightPxLive
                                }
                            },
                            onAsciiBlockToggled = {
                                // Expanding/shrinking an ASCII block surfaces chrome
                                // (the otherwise-suppressed onTouch toggle doesn't fire).
                                webViewTopPadPx = totalChromeHeightPxLive
                            },
                            onImageLongPress = { imageUrl ->
                                state.selectedImageUrl.value = imageUrl
                                state.showImageContextMenu.value = true
                                updateInteraction()
                            },
                            onLinkLongPress = { linkUrl, linkText ->
                                state.selectedLinkUrl.value = linkUrl
                                state.selectedLinkText.value = linkText
                                state.showLinkContextMenu.value = true
                                updateInteraction()
                            },
                            customSelectionActions = null,
                            vaultId = vaultId,
                            artworkPath = effectiveVaultArtworkPath,
                            gameName = effectiveVault?.displayName,
                            appendGameName = state.appendGameName.value,
                            currentSearchEngine = currentSearchEngine,
                            landingEntriesRef = state.landingEntriesRef,
                            savedLinksRef = savedLinksState,
                            onEditLandingSlot = { slotIndex -> editingLandingSlot = slotIndex },
                            onPageFinished = { wv, url ->
                                if (wv != null && url != null) {
                                    val active = tabsVm.activeTab.value
                                    // Belt-and-suspenders: the activeTab/tabsList filter above
                                    // already prevents stale-vault tabs from triggering a load,
                                    // but if a future code path bypasses that gate, never persist
                                    // a navigation into a tab that belongs to a previous vault.
                                    if (active != null && active.vaultId == vaultId) {
                                        tabsVm.onPageFinished(url, wv.title)
                                        if (tabsVm.consumeScrollRestoreFor(active.id)) {
                                            // Switch-induced load. Per-tab history lives in
                                            // BrowserTabRepository, not in WebView's native
                                            // back/forward list, so we no longer clear it —
                                            // the native list is ignored on read.
                                            //
                                            // Restore via the settle-polling routine rather than
                                            // a single scrollTo: a reopened page is re-laid-out
                                            // live and (with privacy blocking on) shrinks as
                                            // banner/ad containers are removed, so a one-shot
                                            // absolute scrollY would clamp out-of-range to the
                                            // bottom. See BrowserScrollRestoreJs.
                                            if (active.scrollY > 0) {
                                                wv.evaluateJavascript(
                                                    BrowserScrollRestoreJs.build(active.scrollY),
                                                    null,
                                                )
                                            }
                                        }
                                        tabsVm.markLoadingComplete(active.id)
                                    }
                                }
                            },
                            onOpenInNewTab = { url ->
                                // target="_blank" / "show in new window" links: switch to the new tab so
                                // the navigation is visible, and the activeTab change triggers the
                                // loading-bar pipeline (LaunchedEffect → wv.loadUrl → onPageStarted →
                                // setLoading(true)). Flush the outgoing tab's scrollY first so the user
                                // returns to the same spot via the tab switcher. Mirrors onOpenSavedLink.
                                tabsVm.flushActiveScrollY(state.webView.value?.scrollY ?: 0)
                                // pulseOnSuccess animates the [N] tab counter to draw attention to the
                                // new tab — same affordance as link-click new-tab in the kebab.
                                tabsVm.newTab(url = url, background = false, pulseOnSuccess = true)
                                // Page-content swap + progress bar alone are easy to miss mid-scroll on a
                                // long article. Toast makes the "the page jumped because a new tab opened"
                                // cause explicit so the user doesn't read it as a redirect.
                                state.toastMessage.value = "Opened in new tab"
                            },
                            onFaviconReceived = { bytes ->
                                tabsVm.onFaviconReceived(bytes)
                            },
                            onTitleReceived = { title ->
                                tabsVm.onTitleReceived(title)
                            },
                            onOpenSavedLink = { linkId ->
                                coroutineScope.launch {
                                    val link = savedTabRepository.getTabById(linkId) ?: return@launch
                                    if (link.url.isNotBlank()) {
                                        tabsVm.flushActiveScrollY(state.webView.value?.scrollY ?: 0)
                                        tabsVm.newTab(url = link.url, background = false)
                                    }
                                }
                            },
                            onDeleteSavedLink = { linkId ->
                                coroutineScope.launch {
                                    val link = savedTabRepository.getTabById(linkId) ?: return@launch
                                    savedTabRepository.deleteBookmark(linkId)
                                    tabsVm.armLinkUndo(link)
                                }
                            },
                            onTextFaqTocDetected = { },
                            onScrollMarkerHighlightTap = null,
                            vaultSessionMode = VaultSessionMode.Browser,
                            onOpenLinkInBrowser = null,
                            currentBookmarkUrl = null,
                            onDownloadRequested = null,
                            // Slide the WebView down by the scroll-tracked inset
                            // (webViewTopPadPx) via graphicsLayer translationY —
                            // a draw/placement-only transform, so the WebView is
                            // NOT remeasured/reflowed every scroll frame the way
                            // an animated `padding` would be. That per-frame
                            // reflow was the sluggish slide. The WebView keeps its
                            // full height (bottom slides just past the edge, under
                            // the bottom tab bar).
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { translationY = webViewTopPadPx }
                        )
                    }
                }
            }
        }

        // URL bar: scroll-direction driven; tap toggles; button press wakes.
        // Offset down by vaultTopBarHeightDp so it stacks under the outer
        // VaultSessionTopBar (game name) instead of overlapping it at Y=0 —
        // the WebView below is edge-to-edge, so without this offset both top
        // bars anchor to the same position just under the system status bar.
        AnimatedVisibility(
            visible = (state.showTopBar.value || state.isUrlFieldFocused.value) && !state.showFindOnPage.value,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(top = vaultTopBarHeightDp)
                .onGloballyPositioned { coordinates ->
                    with(density) {
                        measuredTopBarHeightDp = coordinates.size.height.toDp()
                    }
                }
        ) {
            val focusManager = LocalFocusManager.current
            val topBarContext = LocalContext.current
            LaunchedEffect(state.focusSearchBarTrigger.value) {
                if (state.focusSearchBarTrigger.value) {
                    delay(80)
                    urlFieldFocusRequester.requestFocus()
                    keyboardController?.show()
                    state.focusSearchBarTrigger.value = false
                }
            }
            val topBarSearchHistoryManager = remember {
                EntryPoints.get(
                    topBarContext.applicationContext,
                    SearchHistoryManagerEntryPoint::class.java
                ).searchHistoryManager()
            }
            val effectiveUrlForSave = state.webView.value?.url ?: currentUrl ?: ""
            val canSaveAsBookmark = effectiveUrlForSave.isNotBlank() &&
                !effectiveUrlForSave.startsWith("file://") &&
                !effectiveUrlForSave.startsWith("content://")
            GuideBrowserTopBar(
                currentUrl = currentUrl?.takeIf { it.startsWith("http") } ?: (state.webView.value?.url ?: currentUrl ?: ""),
                urlInput = state.urlInput.value,
                onUrlInputChange = { state.urlInput.value = it },
                appendGameName = state.appendGameName.value,
                onAppendGameNameChange = { newValue ->
                    state.appendGameName.value = newValue
                    val gameName = effectiveVault?.displayName ?: "game name"
                    val msg = if (newValue) "\"$gameName\" will be added to your searches for game-specific results" else "Game name will no longer be added to searches"
                    android.widget.Toast.makeText(topBarContext, msg, android.widget.Toast.LENGTH_SHORT).show()
                },
                onNavigate = { input, isCompleteQuery, finalQuery ->
                    val finalUrl = if (isValidUrl(input)) {
                        input
                    } else {
                        buildSearchUrlForEngine(currentSearchEngine, finalQuery)
                    }

                    // Save search to history (only if it's not a URL)
                    if (!isValidUrl(input) && vaultId.isNotBlank() && finalQuery.isNotBlank()) {
                        topBarSearchHistoryManager.saveSearch(vaultId, finalQuery)
                    }

                    vm.setLoading(true)
                    state.webView.value?.loadUrl(finalUrl)
                    keyboardController?.hide()
                    state.isUrlFieldFocused.value = false
                    state.urlInput.value = ""
                    state.showTopBar.value = false
                    coroutineScope.launch {
                        delay(100)
                        focusManager.clearFocus(force = true)
                        state.webView.value?.requestFocus()
                    }
                },
                canGoBack = canGoBack,
                canGoForward = canGoForward,
                isOnLandingPage = {
                    val webViewUrl = state.webView.value?.url
                    webViewUrl != null && webViewUrl.contains("landing.html")
                },
                onBack = {
                    mainViewModel.navigateToLibrary(focusedVaultId = vaultId)
                    updateInteraction()
                },
                onForward = {
                    tabsVm.navigateForward()
                    updateInteraction()
                },
                urlFieldFocusRequester = urlFieldFocusRequester,
                onUrlFieldFocused = {
                    state.urlInput.value = ""
                },
                onUrlFieldFocusChanged = { focused ->
                    state.isUrlFieldFocused.value = focused
                    // Publish to MainViewModel so VaultSessionScreen can exempt this state
                    // from the IME-driven top-bar hide.
                    mainViewModel.setBrowserUrlFieldFocused(focused)
                    updateInteraction()
                },
                onFavoriteClick = {
                    // Bookmark management dialog removed; saved links surface lives on landing.
                    keyboardController?.hide()
                    updateInteraction()
                },
                onAddNewGuide = null,
                onFavoriteLongPress = {
                    updateInteraction()
                },
                onSaveAsFavorite = {
                    if (state.isSavingBookmark.value || state.showSaveTypeSheet.value) {
                        return@GuideBrowserTopBar
                    }
                    state.webView.value?.let { wv ->
                        try {
                            wv.evaluateJavascript("""
                                (function() {
                                    var title = '';
                                    var titleEl = document.querySelector('.page-title') || document.querySelector('h2.title');
                                    if (titleEl) title = titleEl.textContent.trim();
                                    if (!title) title = document.title || '';
                                    var injected = document.querySelectorAll('[data-vault-injected]');
                                    for (var i = 0; i < injected.length; i++) injected[i].remove();
                                    if (document.body) document.body.removeAttribute('data-vault-enhanced');
                                    return title;
                                })();
                            """.trimIndent()) { titleResult ->
                                val pageTitle = titleResult?.trim()?.removeSurrounding("\"")
                                    ?.replace("\\\"", "\"")?.replace("\\n", " ")?.trim()
                                    ?.takeIf { it.isNotBlank() && it != "null" } ?: ""
                                val baseUrl = wv.url ?: ""
                                fun runSaveBookmarkJob(urlToSave: String) {
                                    val currentZoom = 1f
                                    val isLinkPage = urlToSave.contains("youtube.com/watch") ||
                                        urlToSave.contains("youtu.be/") ||
                                        urlToSave.contains("youtube.com/shorts/")
                                    if (isLinkPage) {
                                        state.isSavingBookmark.value = true
                                        state.saveBookmarkJob.value?.cancel()
                                        val job = coroutineScope.launch(Dispatchers.IO) {
                                            try {
                                                val faviconBytes: ByteArray? = withContext(Dispatchers.Main) {
                                                    state.webView.value?.favicon?.let { bmp ->
                                                        runCatching {
                                                            val baos = java.io.ByteArrayOutputStream()
                                                            bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, baos)
                                                            baos.toByteArray()
                                                        }.getOrNull()
                                                    }
                                                }
                                                val result = savedTabRepository.createLinkBookmark(
                                                    vaultId = vaultId,
                                                    url = urlToSave,
                                                    title = pageTitle,
                                                    zoomLevel = currentZoom,
                                                    faviconBytes = faviconBytes
                                                )
                                                result.onSuccess { bookmark ->
                                                    withContext(Dispatchers.Main) {
                                                        state.isSavingBookmark.value = false
                                                        state.saveProgress.value = null
                                                        state.saveBookmarkJob.value = null
                                                        state.toastShowCheckmark.value = true
                                                        state.toastMessage.value = "Added to home page: $pageTitle"
                                                    }
                                                }.onFailure { error ->
                                                    Log.e("BrowserScreen", "Failed to save link bookmark", error)
                                                    withContext(Dispatchers.Main) {
                                                        state.isSavingBookmark.value = false
                                                        state.saveProgress.value = null
                                                        state.saveBookmarkJob.value = null
                                                        state.toastMessage.value = "Failed to save bookmark"
                                                    }
                                                }
                                            } catch (e: kotlinx.coroutines.CancellationException) {
                                                withContext(Dispatchers.Main) {
                                                    state.isSavingBookmark.value = false
                                                    state.saveProgress.value = null
                                                    state.saveBookmarkJob.value = null
                                                }
                                                throw e
                                            } catch (e: Exception) {
                                                Log.e("BrowserScreen", "Error saving link bookmark", e)
                                                withContext(Dispatchers.Main) {
                                                    state.isSavingBookmark.value = false
                                                    state.saveProgress.value = null
                                                    state.saveBookmarkJob.value = null
                                                    state.toastMessage.value = "Failed to save bookmark"
                                                }
                                            }
                                        }
                                        state.saveBookmarkJob.value = job
                                    } else {
                                        state.pendingSaveTitle.value = pageTitle
                                        state.pendingSaveUrl.value = urlToSave
                                        state.pendingSaveZoom.value = currentZoom
                                        // Kick off ToC detection so the "Save All Pages" row
                                        // can show the chapter count (or grey out if none found).
                                        val detectWebView = state.webView.value
                                        val detectPageUrl = detectWebView?.url
                                        val detectPageTitle = detectWebView?.title.orEmpty()
                                        if (detectWebView != null && detectPageUrl != null) {
                                            // Skip detection if we already have a result for this exact URL.
                                            // Avoids re-running an expensive JS+JSON pipeline on big pages every
                                            // time the user opens the Save sheet, which was both wasteful and a
                                            // crash vector (OOM-ish on repeated Zelda-Dungeon-sized DOMs).
                                            val cached = state.bulkSaveDetection.value
                                            if (cached?.pageUrl != detectPageUrl) {
                                                state.bulkSaveChapterCount.value = 0
                                                state.bulkSaveDetection.value = null
                                                coroutineScope.launch {
                                                    val detected = try {
                                                        com.twinlore.app.data.library.ToCDetector.detect(
                                                            context = context,
                                                            webView = detectWebView,
                                                            pageUrl = detectPageUrl,
                                                            pageTitle = detectPageTitle
                                                        )
                                                    } catch (e: Throwable) {
                                                        android.util.Log.w("BrowserScreen", "ToCDetector.detect failed", e)
                                                        null
                                                    }
                                                    if (detected != null) {
                                                        state.bulkSaveDetection.value = detected
                                                        // Chapter count = named ToC entries (anchor sub-entries
                                                        // included, synthetic body-only page entries excluded —
                                                        // those are save targets but never appear in the page's
                                                        // visible ToC). Page count = distinct save targets,
                                                        // computed separately at the SaveTypeBottomSheet call
                                                        // site (includes synthetic). Save All Pages stays
                                                        // disabled when fewer than 2 pages exist.
                                                        state.bulkSaveChapterCount.value = detected.entries.count { !it.synthetic }
                                                    }
                                                }
                                            }
                                        }
                                        state.showSaveTypeSheet.value = true
                                    }
                                }
                                if (baseUrl.isNotBlank() && baseUrl.startsWith("http")) {
                                    runSaveBookmarkJob(baseUrl)
                                } else {
                                    wv.evaluateJavascript("(function(){ var m = document.querySelector('meta[name=\"original-url\"]'); return m ? m.getAttribute('content') : null; })();") { res ->
                                        val raw = res?.trim()?.removeSurrounding("\"")?.replace("\\\"", "\"") ?: ""
                                        val resolved = raw.takeIf { it.isNotBlank() && (it.startsWith("http://") || it.startsWith("https://")) } ?: baseUrl
                                        runSaveBookmarkJob(resolved)
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("BrowserScreen", "Error in save callback", e)
                            state.isSavingBookmark.value = false
                            state.toastMessage.value = "Failed to save"
                        }
                    } ?: run {
                        Log.w("BrowserScreen", "WebView is null, cannot save")
                        state.isSavingBookmark.value = false
                        state.toastMessage.value = "Failed to save: Page not loaded"
                    }
                    updateInteraction()
                },
                isCurrentPageFavorite = run {
                    val urlToCheck = currentUrl ?: state.webView.value?.url ?: ""
                    bookmarks.any { it.url == urlToCheck }
                },
                canSaveAsBookmark = canSaveAsBookmark,
                isSavingFavorite = state.isSavingBookmark.value,
                saveProgress = state.saveProgress.value,
                onFilledStarClick = {
                    val url = currentUrl ?: state.webView.value?.url ?: ""
                    val b = bookmarks.find { it.url == url }
                    if (b != null) bookmarkToDeleteFromStar = b
                },
                onCancelSave = if (state.isSavingBookmark.value) {
                    {
                        state.saveBookmarkJob.value?.cancel()
                        state.saveBookmarkJob.value = null
                        state.saveProgress.value = null
                        state.isSavingBookmark.value = false
                        state.toastMessage.value = "Guide save cancelled"
                    }
                } else null,
                hasFavorite = bookmarks.isNotEmpty(),
                gameName = effectiveVault?.displayName,
                vaultId = vaultId,
                onRefresh = {
                    state.webView.value?.reload()
                    updateInteraction()
                },
                zoomLevel = 1f,
                onZoomLevelChange = null,
                onResetZoom = null,
                onFindOnPage = {
                    state.showFindOnPage.value = true
                    state.findQuery.value = ""
                    state.findMatchesFound.value = null
                    state.findMatchCount.value = 0
                    state.findCurrentIndex.value = 0
                    updateInteraction()
                },
                onOpenToc = null,
                currentBookmark = null,
                onToastMessage = { message ->
                    state.toastMessage.value = message
                },
                scrollMarkers = emptyList(),
                focusedScrollMarkerIndex = null,
                onScrollMarkerTap = { },
                onScrollMarkerLongPress = { },
                scrollMarkersBarVisible = false,
                onToggleScrollMarkersBar = { },
                vaultSessionMode = VaultSessionMode.Browser,
                // BrowserScreen only ever runs embedded in VaultSessionScreen (no
                // standalone route), which normally renders its own fold button —
                // and steps back for any reason that bar is not actually on screen
                // (Hidden mode, keyboard open sans URL field focus, per-tab
                // auto-hide). See shouldShowFoldButtonInHostTopBar's doc.
                showFoldButton = com.twinlore.app.ui.fold.shouldShowFoldButtonInHostTopBar(
                    isEmbeddedInVaultSession = true, vaultSessionTopBarVisible
                ),
                onBrowserBack = { tabsVm.navigateBack(); updateInteraction() },
                onBrowserForward = { tabsVm.navigateForward(); updateInteraction() },
                onGoToLandingPage = {
                    val landingPageUrl = getLandingPageUrl(context, vaultId, effectiveVaultArtworkPath, landingEntriesList, recentSearchesForLanding, savedLinks = savedLinks, paletteBgHex = screenBgHex, paletteFgHex = screenFgHex, gameName = effectiveVault?.displayName)
                    if (landingPageUrl != null) {
                        val wv = state.webView.value
                        if (wv != null) {
                            vm.setLoading(true)
                            if (landingPageUrl.startsWith("file://")) {
                                loadFileUrl(wv, landingPageUrl)
                            } else {
                                wv.loadUrl(landingPageUrl)
                                vm.setUrl(landingPageUrl)
                            }
                        }
                    }
                    updateInteraction()
                },
                showOpenInBrowserButton = false,
                onOpenGuideInBrowser = null,
                firstButtonFocusRequester = contentFocusRequester,
                bottomBarFocusRequester = null,
                zoomControlsFocusRequester = null,
                browserTabCount = tabsList.size,
                onOpenBrowserTabSwitcher = { tabsVm.openSwitcher() },
                privacyBlockingEnabled = privacyBlockingEnabled,
                onTogglePrivacyBlocking = {
                    settingsViewModel.setPrivacyBlockingEnabled(!privacyBlockingEnabled)
                    // Reload so the new state takes effect on the page the user is looking at.
                    // shouldInterceptRequest decisions are evaluated per request, so without a
                    // reload the current page keeps whatever rewriting state it loaded with.
                    state.webView.value?.reload()
                    updateInteraction()
                },
                tabOpenedPulse = tabsVm.tabOpenedPulse,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
            )
        }

        // Horizontal progress bar below top bar
        val isLandingPageUrl = currentUrl.orEmpty().contains("landing.html") || state.webView.value?.url.orEmpty().contains("landing.html")
        if (isLoading && !isLandingPageUrl) {
            // Progress bar stays at a constant Y just under the URL bar's
            // resting position. The WebView is edge-to-edge and the URL bar
            // fades over it, so animating this strip's offset to follow the
            // bar would mix sliding motion with fading motion and read as
            // inconsistent. Hold steady; loading is brief.
            LinearProgressIndicator(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    // Sits just below the URL bar's bottom. The URL bar itself
                    // is offset down by vaultTopBarHeightDp (so it stacks under
                    // the outer game-name bar), so its bottom edge is at
                    // vaultTopBarHeightDp + measuredTopBarHeightDp.
                    .padding(top = vaultTopBarHeightDp + measuredTopBarHeightDp)
                    .height(3.dp),
                color = focusColorBrowser,
                trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            )
        }

        // MHT save: delegates to BrowserSaveCoordinator, which writes the archive into
        // the home folder and registers resource_files + saved_tabs rows. The MHT cache
        // is warmed when the guide tab loads the file, so we no longer prime it here.
        fun startMhtSave() {
            val wv = state.webView.value ?: return
            state.saveBookmarkJob.value?.cancel()
            state.isSavingBookmark.value = true
            state.saveProgress.value = SaveProgress(currentAsset = "Saving web archive\u2026")
            val job = coroutineScope.launch {
                try {
                    val result = browserSaveCoordinator.saveCurrentPage(wv, vaultId)
                    withContext(Dispatchers.Main) {
                        when (result) {
                            is com.twinlore.app.data.library.SaveResult.Success -> {
                                val savedTabId = result.savedTabId
                                if (savedTabId != null) {
                                    // Look up the newly-saved tab so the Done
                                    // overlay can show its title and route Open
                                    // through the new single-guide viewer.
                                    val savedTab = withContext(Dispatchers.IO) {
                                        savedTabRepository.getTabById(savedTabId)
                                    }
                                    state.isSavingBookmark.value = false
                                    state.saveProgress.value = null
                                    state.saveBookmarkJob.value = null
                                    if (savedTab != null) {
                                        state.savedBookmarkForOverlay.value = savedTab
                                    } else {
                                        // Tab not found \u2014 fall back to the old auto-open path.
                                        mainViewModel.setPendingGuideBookmarkId(vaultId, savedTabId)
                                        mainViewModel.requestGuideTabSwitch(vaultId)
                                    }
                                } else {
                                    state.isSavingBookmark.value = false
                                    state.saveProgress.value = null
                                    state.saveBookmarkJob.value = null
                                    state.toastMessage.value = "Saved"
                                }
                            }
                            is com.twinlore.app.data.library.SaveResult.Failed -> {
                                Log.e("BrowserScreen", "Failed to save MHT", result.cause)
                                state.isSavingBookmark.value = false
                                state.saveProgress.value = null
                                state.saveBookmarkJob.value = null
                                state.toastMessage.value = "Failed to save guide"
                            }
                        }
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    withContext(Dispatchers.Main) {
                        state.isSavingBookmark.value = false
                        state.saveProgress.value = null
                        state.saveBookmarkJob.value = null
                    }
                    throw e
                } catch (e: Exception) {
                    Log.e("BrowserScreen", "Error saving MHT", e)
                    withContext(Dispatchers.Main) {
                        state.isSavingBookmark.value = false
                        state.saveProgress.value = null
                        state.saveBookmarkJob.value = null
                        state.toastMessage.value = "Failed to save guide"
                    }
                }
            }
            state.saveBookmarkJob.value = job
        }

        // Confirmation dialog when saving while page is still loading
        if (state.showSaveWhileLoadingConfirm.value) {
            AlertDialog(
                onDismissRequest = { state.showSaveWhileLoadingConfirm.value = false },
                title = {
                    Text(
                        "Page still loading",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                text = {
                    Text(
                        text = "The page hasn\u2019t finished loading. Some images or content may be missing from the saved copy.",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                containerColor = MaterialTheme.colorScheme.surface,
                confirmButton = {
                    TextButton(
                        onClick = {
                            state.showSaveWhileLoadingConfirm.value = false
                            startMhtSave()
                        }
                    ) {
                        Text("Save anyway", color = focusColorBrowser)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { state.showSaveWhileLoadingConfirm.value = false }
                    ) {
                        Text("Wait", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                    }
                }
            )
        }

        // Save Type Bottom Sheet (offline copy vs. link)
        if (state.showSaveTypeSheet.value) {
            SaveTypeBottomSheet(
                saveAllAvailable = (state.bulkSaveDetection.value?.entries?.distinctBy { it.savedHref }?.size ?: 0) >= 2,
                saveAllChapterCount = state.bulkSaveChapterCount.value,
                saveAllPageCount = state.bulkSaveDetection.value?.entries?.distinctBy { it.savedHref }?.size ?: 0,
                onSaveOffline = {
                    state.showSaveTypeSheet.value = false
                    val wv = state.webView.value
                    if (wv == null) {
                        state.toastMessage.value = "Failed to save: Page not loaded"
                        return@SaveTypeBottomSheet
                    }
                    if (wv.progress < 100) {
                        state.showSaveWhileLoadingConfirm.value = true
                    } else {
                        startMhtSave()
                    }
                },
                onSaveAllPages = {
                    state.showSaveTypeSheet.value = false
                    val detection = state.bulkSaveDetection.value ?: return@SaveTypeBottomSheet
                    val foregroundWebView = state.webView.value ?: return@SaveTypeBottomSheet
                    val originalUrl = foregroundWebView.url
                    state.showBulkSaveProgressSheet.value = true
                    pageGroupSaveCoordinator.start(
                        vaultId = vaultId,
                        indexUrl = detection.pageUrl,
                        indexPageTitle = detection.pageTitle,
                        toc = detection.entries,
                        driverFactory = {
                            com.twinlore.app.data.library.AndroidPageGroupSaveDriver(
                                webView = foregroundWebView,
                                originalUrl = originalUrl
                            )
                        }
                    )
                },
                onSaveAsLink = {
                    state.showSaveTypeSheet.value = false
                    state.isSavingBookmark.value = true
                    state.saveBookmarkJob.value?.cancel()
                    val pendingTitle = state.pendingSaveTitle.value
                    val pendingUrl = state.pendingSaveUrl.value
                    val pendingZoom = state.pendingSaveZoom.value
                    val job = coroutineScope.launch(Dispatchers.IO) {
                        try {
                            val faviconBytes: ByteArray? = withContext(Dispatchers.Main) {
                                state.webView.value?.favicon?.let { bmp ->
                                    runCatching {
                                        val baos = java.io.ByteArrayOutputStream()
                                        bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, baos)
                                        baos.toByteArray()
                                    }.getOrNull()
                                }
                            }
                            val result = savedTabRepository.createLinkBookmark(
                                vaultId = vaultId,
                                url = pendingUrl,
                                title = pendingTitle,
                                zoomLevel = pendingZoom,
                                faviconBytes = faviconBytes
                            )
                            result.onSuccess { bookmark ->
                                withContext(Dispatchers.Main) {
                                    state.isSavingBookmark.value = false
                                    state.saveProgress.value = null
                                    state.saveBookmarkJob.value = null
                                    state.toastShowCheckmark.value = true
                                    state.toastMessage.value = "Added to home page: ${bookmark.title}"
                                }
                            }.onFailure { error ->
                                Log.e("BrowserScreen", "Failed to save link bookmark", error)
                                withContext(Dispatchers.Main) {
                                    state.isSavingBookmark.value = false
                                    state.saveProgress.value = null
                                    state.saveBookmarkJob.value = null
                                    state.toastMessage.value = "Failed to save bookmark"
                                }
                            }
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            withContext(Dispatchers.Main) {
                                state.isSavingBookmark.value = false
                                state.saveProgress.value = null
                                state.saveBookmarkJob.value = null
                            }
                            throw e
                        } catch (e: Exception) {
                            Log.e("BrowserScreen", "Error saving link bookmark", e)
                            withContext(Dispatchers.Main) {
                                state.isSavingBookmark.value = false
                                state.saveProgress.value = null
                                state.saveBookmarkJob.value = null
                                state.toastMessage.value = "Failed to save bookmark"
                            }
                        }
                    }
                    state.saveBookmarkJob.value = job
                },
                onDismiss = {
                    state.showSaveTypeSheet.value = false
                    state.pendingSaveTitle.value = ""
                    state.pendingSaveUrl.value = ""
                    state.pendingSaveZoom.value = 1f
                }
            )
        }

        // Bulk-save progress sheet: cancellable while running; offers "Open vault"
        // + "Done" buttons on completion.
        if (state.showBulkSaveProgressSheet.value) {
            BulkSaveProgressBanner(
                coordinator = pageGroupSaveCoordinator,
                onOpenVault = {
                    state.showBulkSaveProgressSheet.value = false
                    // Route to the new page-group's saved_tabs row (one group
                    // per run). lastGroupTabId is @Volatile so this UI-thread
                    // read sees the latest write from the coordinator.
                    val newGroupTabId = pageGroupSaveCoordinator.lastGroupTabId
                    if (newGroupTabId != null) {
                        // navigateToPageGroupViewer sets _openPageGroupByVault
                        // directly and switches to Guide tab — bypasses the
                        // one-shot maybeRestore gate so the bulk-save's new
                        // group surfaces immediately even on a vault we've
                        // already restored in this process.
                        mainViewModel.navigateToPageGroupViewer(vaultId, newGroupTabId)
                    } else {
                        mainViewModel.navigateToVaultSession(
                            vaultId = vaultId,
                            tab = com.twinlore.app.domain.VaultTab.Guide
                        )
                    }
                },
                onComplete = {
                    // Only fires from the Done or Cancelled row buttons — at this
                    // point the run is already finished, so we just hide the banner.
                    state.showBulkSaveProgressSheet.value = false
                },
                artworkPath = effectiveVaultArtworkPath,
                vaultId = vaultId,
                onRequestTapGame = { showTapGame = true }
            )
        }

        // Tap game (shared easter egg) — launched from the guide-set download
        // ring's double-tap. The bulk save keeps running underneath; the game
        // is a full-screen Dialog over everything.
        if (showTapGame) {
            com.twinlore.app.ui.screens.checklist.ChecklistTapGame(
                vaultArtworkPath = effectiveVaultArtworkPath,
                vaultId = vaultId,
                highScore = tapGameHighScore,
                onUpdateHighScore = { settingsViewModel.setTapGameHighScore(it) },
                onDismiss = { showTapGame = false },
            )
        }

        // Save-in-progress overlay, toast, PDF download overlay
        SaveProgressOverlay(
            visible = state.isSavingBookmark.value,
            progress = state.saveProgress.value,
            focusColor = focusColorBrowser,
            onRequestTapGame = { showTapGame = true },
            onCancel = {
                state.saveBookmarkJob.value?.cancel()
                state.saveBookmarkJob.value = null
                state.saveProgress.value = null
                state.isSavingBookmark.value = false
                state.toastMessage.value = "Guide save cancelled"
            },
            doneBookmark = state.savedBookmarkForOverlay.value,
            onOpen = {
                val bookmark = state.savedBookmarkForOverlay.value
                state.savedBookmarkForOverlay.value = null
                if (bookmark != null) {
                    mainViewModel.openSingleGuideForVault(vaultId, bookmark)
                    mainViewModel.navigateToVaultSession(
                        vaultId = vaultId,
                        tab = com.twinlore.app.domain.VaultTab.Guide
                    )
                }
            },
            onCloseDone = {
                state.savedBookmarkForOverlay.value = null
            },
            artworkPath = effectiveVaultArtworkPath,
            vaultId = vaultId,
        )
        PdfDownloadOverlay(
            visible = state.pdfDownloadInProgress.value,
            focusColor = focusColorBrowser,
            downloadingLabel = if (state.downloadIsBundle.value) "Downloading…" else "Downloading PDF…",
            savedLabel = when (state.pdfDownloadDoneBookmark.value?.fileType) {
                "wheel" -> "Code wheel saved"
                "htmlzip" -> "HTML page saved"
                else -> "PDF saved"
            },
            onCancel = {
                state.pdfDownloadJob.value?.cancel()
                state.pdfDownloadJob.value = null
                state.pdfDownloadInProgress.value = false
                state.toastMessage.value = "Download cancelled"
            },
            doneBookmarkTitle = state.pdfDownloadDoneBookmark.value?.displayTitle,
            onOpenDone = {
                val tab = state.pdfDownloadDoneBookmark.value ?: return@PdfDownloadOverlay
                state.pdfDownloadDoneBookmark.value = null
                // HTML-bundle guides (code wheels / htmlzip) render through
                // GuideScreen.activeWheelGuide, not the PDF viewer.
                if (tab.fileType == "wheel" || tab.fileType == "htmlzip") {
                    mainViewModel.openWheelForVault(vaultId, tab)
                    return@PdfDownloadOverlay
                }
                // PDFs render through GuideScreen.activePdfGuide, not the
                // MHT-only SingleGuideViewerScreen. openPdfForVault clears the
                // single-guide / page-group dispatch maps and queues the
                // bookmark via the pending-id pattern; GuideScreen's reactive
                // consumer polls the file, sets activePdfGuide, and flips the
                // tab once the PDF is ready.
                mainViewModel.openPdfForVault(vaultId, tab)
            },
            onCloseDone = {
                state.pdfDownloadDoneBookmark.value = null
            }
        )
        ImageDownloadOverlay(
            visible = state.imageDownloadInProgress.value,
            focusColor = focusColorBrowser,
            onCancel = {
                state.imageDownloadJob.value?.cancel()
                state.imageDownloadJob.value = null
                state.imageDownloadInProgress.value = false
                state.toastMessage.value = "Image download cancelled"
            },
            doneMapName = state.imageDownloadDoneMapName.value,
            onOpenDone = {
                state.imageDownloadDoneMapName.value = null
                mainViewModel.setVaultSessionTab(vaultId, com.twinlore.app.domain.VaultTab.Map)
            },
            onCloseDone = {
                state.imageDownloadDoneMapName.value = null
            }
        )

        // Delete bookmark from star confirmation
        bookmarkToDeleteFromStar?.let { bookmark ->
            DeleteTabDialog(
                tab = bookmark,
                onDismiss = { bookmarkToDeleteFromStar = null },
                onConfirm = {
                    coroutineScope.launch(Dispatchers.IO) {
                        savedTabRepository.deleteBookmark(bookmark.id)
                        withContext(Dispatchers.Main) {
                            bookmarkToDeleteFromStar = null
                        }
                    }
                }
            )
        }

        // Landing entry edit dialog
        editingLandingSlot?.let { slotIndex ->
            val entries = state.landingEntriesRef.value
            val entry = entries.getOrNull(slotIndex) ?: defaultLandingEntries().getOrNull(slotIndex)
            if (entry != null) {
                LandingEntryEditDialog(
                    slotIndex = slotIndex,
                    entry = entry,
                    onSave = { updatedEntry ->
                        settingsViewModel.setLandingEntry(slotIndex, updatedEntry)
                        editingLandingSlot = null
                    },
                    onDismiss = { editingLandingSlot = null },
                    focusColor = focusColorBrowser,
                    containerColor = MaterialTheme.colorScheme.surface
                )
            }
        }

        // Image context menu (long-press on image)
        val currentVault = effectiveVault
        if (state.showImageContextMenu.value && state.selectedImageUrl.value != null && currentVault != null) {
            BrowserImageContextMenu(
                imageUrl = state.selectedImageUrl.value!!,
                pageUrl = state.webView.value?.url,
                onAssignAsBoxArt = {
                    val pageUrl = state.selectedImageUrl.value!!
                    val v = currentVault
                    state.showImageContextMenu.value = false
                    state.isAssigningImage.value = true
                    state.assigningImageLabel.value = "Assigning as box art\u2026"
                    val refererUrl = state.webView.value?.url
                    resolveBestImageUrlForDownload(state.webView.value, pageUrl) { urlToUse ->
                        coroutineScope.launch(Dispatchers.IO) {
                            val vaultIdToUpdate = v.id
                            val result = browserSaveCoordinator.saveImage(
                                url = urlToUse,
                                kind = "artwork",
                                vaultId = vaultIdToUpdate,
                                referer = refererUrl
                            )
                            when (result) {
                                is com.twinlore.app.data.library.SaveResult.Success -> {
                                    guideViewModel.updateArtworkResourceId(
                                        vaultId = vaultIdToUpdate,
                                        resourceId = result.resourceFileId
                                    )
                                    mainViewModel.notifyArtworkAssigned(vaultIdToUpdate)
                                    withContext(Dispatchers.Main) {
                                        state.isAssigningImage.value = false
                                        state.assigningImageLabel.value = ""
                                        state.selectedImageUrl.value = null
                                        android.widget.Toast.makeText(context, "Box art assigned", android.widget.Toast.LENGTH_SHORT).show()
                                        updateInteraction()
                                    }
                                }
                                is com.twinlore.app.data.library.SaveResult.Failed -> {
                                    Log.e("BrowserScreen", "Error assigning box art", result.cause)
                                    withContext(Dispatchers.Main) {
                                        state.isAssigningImage.value = false
                                        state.assigningImageLabel.value = ""
                                        state.selectedImageUrl.value = null
                                        android.widget.Toast.makeText(context, "Error: ${result.cause.message}", android.widget.Toast.LENGTH_SHORT).show()
                                        updateInteraction()
                                    }
                                }
                            }
                        }
                    }
                },
                onAssignAsMap = {
                    val pageUrl = state.selectedImageUrl.value!!
                    val v = currentVault
                    state.showImageContextMenu.value = false
                    state.isAssigningImage.value = true
                    state.assigningImageLabel.value = "Saving to Image tab\u2026"
                    val refererUrl = state.webView.value?.url
                    resolveBestImageUrlForDownload(state.webView.value, pageUrl) { urlToUse ->
                        coroutineScope.launch(Dispatchers.IO) {
                            val vaultIdToUpdate = v.id
                            val result = browserSaveCoordinator.saveImage(
                                url = urlToUse,
                                kind = "map",
                                vaultId = vaultIdToUpdate,
                                referer = refererUrl
                            )
                            when (result) {
                                is com.twinlore.app.data.library.SaveResult.Success -> {
                                    val resolved = resourceResolver.resolve(result.resourceFileId)
                                    if (resolved != null) {
                                        val pageTitle = withContext(Dispatchers.Main) { state.webView.value?.title }
                                        val mapName = pageTitle?.take(60)?.trim()?.ifBlank { null }
                                            ?: urlToUse.substringAfterLast('/').substringBeforeLast('.').replace(Regex("[_\\-]+"), " ").trim().ifBlank { null }
                                            ?: "Map"
                                        mapViewModel.addMap(vaultIdToUpdate, mapName, result.resourceFileId)
                                        guideViewModel.updateMapResourceId(vaultId = vaultIdToUpdate, resourceId = result.resourceFileId)
                                        mapViewModel.notifyMapReplaced(vaultIdToUpdate)
                                        withContext(Dispatchers.Main) {
                                            state.imageDownloadDoneMapName.value = mapName
                                            state.isAssigningImage.value = false
                                            state.assigningImageLabel.value = ""
                                            state.selectedImageUrl.value = null
                                            updateInteraction()
                                        }
                                    } else {
                                        withContext(Dispatchers.Main) {
                                            state.isAssigningImage.value = false
                                            state.assigningImageLabel.value = ""
                                            state.selectedImageUrl.value = null
                                            android.widget.Toast.makeText(context, "Saved file is not accessible", android.widget.Toast.LENGTH_SHORT).show()
                                            updateInteraction()
                                        }
                                    }
                                }
                                is com.twinlore.app.data.library.SaveResult.Failed -> {
                                    Log.e("BrowserScreen", "Error assigning map", result.cause)
                                    withContext(Dispatchers.Main) {
                                        state.isAssigningImage.value = false
                                        state.assigningImageLabel.value = ""
                                        state.selectedImageUrl.value = null
                                        android.widget.Toast.makeText(context, "Error: ${result.cause.message}", android.widget.Toast.LENGTH_SHORT).show()
                                        updateInteraction()
                                    }
                                }
                            }
                        }
                    }
                },
                onDismiss = {
                    state.webView.value?.evaluateJavascript(
                        "if(window.getSelection)window.getSelection().removeAllRanges();",
                        null
                    )
                    state.showImageContextMenu.value = false
                    state.selectedImageUrl.value = null
                    updateInteraction()
                }
            )
        }

        // Link context menu (long-press on <a>)
        if (state.showLinkContextMenu.value && state.selectedLinkUrl.value != null) {
            BrowserLinkContextMenu(
                linkUrl = state.selectedLinkUrl.value!!,
                linkText = state.selectedLinkText.value,
                onOpenInNewTab = {
                    val url = state.selectedLinkUrl.value!!
                    state.showLinkContextMenu.value = false
                    state.selectedLinkUrl.value = null
                    state.selectedLinkText.value = null
                    // Mirror the existing onOpenInNewTab semantics at line 941 so the
                    // affordance matches target="_blank": flush scrollY, foreground the
                    // new tab with a pulse, and toast so the page-content swap is
                    // explained.
                    tabsVm.flushActiveScrollY(state.webView.value?.scrollY ?: 0)
                    tabsVm.newTab(url = url, background = false, pulseOnSuccess = true)
                    state.toastMessage.value = "Opened in new tab"
                    updateInteraction()
                },
                onDismiss = {
                    state.webView.value?.evaluateJavascript(
                        "if(window.getSelection)window.getSelection().removeAllRanges();",
                        null
                    )
                    state.showLinkContextMenu.value = false
                    state.selectedLinkUrl.value = null
                    state.selectedLinkText.value = null
                    updateInteraction()
                }
            )
        }

        // Find on Page UI (browser mode). Ported from GuideScreen without the scroll-marker
        // save path, which is bookmark-specific and not meaningful here.
        if (state.showFindOnPage.value) {
            val doFindNext = {
                if (state.findQuery.value.isNotBlank()) {
                    state.webView.value?.evaluateJavascript("""
                        (function() {
                            if (!window.findRanges || window.findRanges.length === 0) return JSON.stringify({count: 0, current: 0});
                            var nextIdx = (window.findCurrentIdx + 1) % window.findRanges.length;
                            window.findCurrentIdx = nextIdx;
                            CSS.highlights.set('find-current', new Highlight(window.findRanges[nextIdx]));
                            var rect = window.findRanges[nextIdx].getBoundingClientRect();
                            var targetY = window.pageYOffset + rect.top - (window.innerHeight / 2) + (rect.height / 2);
                            window.scrollTo({top: Math.max(0, targetY), behavior: 'smooth'});
                            return JSON.stringify({count: window.findRanges.length, current: nextIdx + 1});
                        })();
                    """.trimIndent()) { result ->
                        try {
                            val json = org.json.JSONObject(result?.removeSurrounding("\"")?.replace("\\\"", "\"") ?: "{}")
                            state.findMatchCount.value = json.getInt("count")
                            state.findCurrentIndex.value = json.getInt("current")
                        } catch (_: Exception) {}
                    }
                }
                updateInteraction()
            }
            val doFindPrevious = {
                if (state.findQuery.value.isNotBlank()) {
                    state.webView.value?.evaluateJavascript("""
                        (function() {
                            if (!window.findRanges || window.findRanges.length === 0) return JSON.stringify({count: 0, current: 0});
                            var prevIdx = (window.findCurrentIdx - 1 + window.findRanges.length) % window.findRanges.length;
                            window.findCurrentIdx = prevIdx;
                            CSS.highlights.set('find-current', new Highlight(window.findRanges[prevIdx]));
                            var rect = window.findRanges[prevIdx].getBoundingClientRect();
                            var targetY = window.pageYOffset + rect.top - (window.innerHeight / 2) + (rect.height / 2);
                            window.scrollTo({top: Math.max(0, targetY), behavior: 'smooth'});
                            return JSON.stringify({count: window.findRanges.length, current: prevIdx + 1});
                        })();
                    """.trimIndent()) { result ->
                        try {
                            val json = org.json.JSONObject(result?.removeSurrounding("\"")?.replace("\\\"", "\"") ?: "{}")
                            state.findMatchCount.value = json.getInt("count")
                            state.findCurrentIndex.value = json.getInt("current")
                        } catch (_: Exception) {}
                    }
                }
                updateInteraction()
            }
            BackHandler(enabled = isTabVisible) {
                state.findIsSearching.value = false
                state.showFindOnPage.value = false
                state.findQuery.value = ""
                state.findMatchesFound.value = null
                state.findMatchCount.value = 0
                state.findCurrentIndex.value = 0
                state.webView.value?.evaluateJavascript("(function(){ var se = document.getElementById('find-highlight-style'); if(se) se.remove(); if(typeof CSS !== 'undefined' && CSS.highlights) { CSS.highlights.delete('find-all'); CSS.highlights.delete('find-current'); } window.findRanges = []; window.findCurrentIdx = -1; })();", null)
                updateInteraction()
            }
            fun runFindSearch() {
                state.findSearchTrigger.value++
                state.findIsSearching.value = true
                state.findMatchesFound.value = null
                if (state.findQuery.value.isBlank()) {
                    state.findMatchesFound.value = false
                    state.findMatchCount.value = 0
                    state.findCurrentIndex.value = 0
                    state.findIsSearching.value = false
                    updateInteraction()
                    return
                }
                val searchTrigger = state.findSearchTrigger.value
                val escapedQuery = state.findQuery.value
                    .replace("\\", "\\\\")
                    .replace("'", "\\'")
                    .replace("\r", "").replace("\n", " ")
                val searchScript = """
                    (function() {
                        if (!document.body) return JSON.stringify({count: 0});
                        var query = '$escapedQuery';
                        if (!query || query.trim() === '') return JSON.stringify({count: 0});
                        var queryLower = query.toLowerCase();
                        if (typeof CSS === 'undefined' || !CSS.highlights) return JSON.stringify({count: 0});
                        CSS.highlights.delete('find-all');
                        CSS.highlights.delete('find-current');
                        window.findRanges = [];
                        window.findCurrentIdx = -1;
                        var focusRgba = '$effectiveHighlightRgbaBrowser';
                        var focusHex = '$effectiveHighlightHexBrowser';
                        var focusContrastHex = '$effectiveContrastHexBrowser';
                        var styleEl = document.getElementById('find-highlight-style');
                        if (styleEl) styleEl.remove();
                        var style = document.createElement('style');
                        style.id = 'find-highlight-style';
                        style.textContent = '::highlight(find-all) { background-color: ' + focusRgba + '; color: ' + focusContrastHex + '; } ::highlight(find-current) { background-color: ' + focusHex + '; color: ' + focusContrastHex + '; }';
                        document.head.appendChild(style);
                        function isTextNodeVisible(tn) {
                            var el = tn.parentElement;
                            if (!el) return false;
                            while (el && el !== document.body) {
                                var tag = (el.tagName || '').toUpperCase();
                                if (tag === 'SCRIPT' || tag === 'STYLE' || tag === 'NOSCRIPT' || tag === 'HEAD') return false;
                                var cs = window.getComputedStyle(el);
                                if (cs.display === 'none' || cs.visibility === 'hidden') return false;
                                el = el.parentElement;
                            }
                            return true;
                        }
                        var walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT, null, false);
                        var tn;
                        var ranges = [];
                        while (tn = walker.nextNode()) {
                            if (!isTextNodeVisible(tn)) continue;
                            var text = tn.textContent;
                            if (!text) continue;
                            var textLower = text.toLowerCase();
                            var idx = 0;
                            while ((idx = textLower.indexOf(queryLower, idx)) !== -1) {
                                var range = new Range();
                                range.setStart(tn, idx);
                                range.setEnd(tn, idx + query.length);
                                ranges.push(range);
                                idx += query.length;
                            }
                        }
                        window.findRanges = ranges;
                        if (ranges.length > 0) {
                            var allHighlight = new Highlight();
                            for (var i = 0; i < ranges.length; i++) allHighlight.add(ranges[i]);
                            CSS.highlights.set('find-all', allHighlight);
                            window.findCurrentIdx = 0;
                            CSS.highlights.set('find-current', new Highlight(ranges[0]));
                            var rect = ranges[0].getBoundingClientRect();
                            var targetY = window.pageYOffset + rect.top - (window.innerHeight / 2) + (rect.height / 2);
                            window.scrollTo({top: Math.max(0, targetY), behavior: 'smooth'});
                        }
                        return JSON.stringify({count: ranges.length});
                    })();
                """.trimIndent()
                state.webView.value?.evaluateJavascript(searchScript) { result ->
                    if (state.findSearchTrigger.value != searchTrigger) return@evaluateJavascript
                    var count = 0
                    try {
                        val json = org.json.JSONObject(result?.removeSurrounding("\"")?.replace("\\\"", "\"") ?: "{}")
                        count = json.optInt("count", 0)
                    } catch (_: Exception) {}
                    state.findMatchCount.value = count
                    state.findMatchesFound.value = count > 0
                    state.findCurrentIndex.value = if (count > 0) 1 else 0
                    state.findIsSearching.value = false
                    updateInteraction()
                }
                updateInteraction()
            }
            // Click-away dismisses
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null
                    ) {
                        state.findIsSearching.value = false
                        state.showFindOnPage.value = false
                        state.findQuery.value = ""
                        state.findMatchesFound.value = null
                        state.findMatchCount.value = 0
                        state.findCurrentIndex.value = 0
                        state.webView.value?.evaluateJavascript("(function(){ var se = document.getElementById('find-highlight-style'); if(se) se.remove(); if(typeof CSS !== 'undefined' && CSS.highlights) { CSS.highlights.delete('find-all'); CSS.highlights.delete('find-current'); } window.findRanges = []; window.findCurrentIdx = -1; })();", null)
                        updateInteraction()
                    }
            )
            com.twinlore.app.ui.screens.FindOnPageUI(
                query = state.findQuery.value,
                onQueryChange = { newQuery ->
                    state.findQuery.value = newQuery
                    state.findMatchesFound.value = null
                    state.findMatchCount.value = 0
                    state.findCurrentIndex.value = 0
                    state.webView.value?.evaluateJavascript("""
                        (function() {
                            if (typeof CSS !== 'undefined' && CSS.highlights) {
                                CSS.highlights.delete('find-all');
                                CSS.highlights.delete('find-current');
                            }
                            window.findRanges = [];
                            window.findCurrentIdx = -1;
                        })();
                    """.trimIndent(), null)
                    updateInteraction()
                },
                onSearch = { runFindSearch() },
                onClose = {
                    state.findIsSearching.value = false
                    state.showFindOnPage.value = false
                    state.findQuery.value = ""
                    state.findMatchesFound.value = null
                    state.findMatchCount.value = 0
                    state.findCurrentIndex.value = 0
                    state.webView.value?.evaluateJavascript("(function(){ var se = document.getElementById('find-highlight-style'); if(se) se.remove(); if(typeof CSS !== 'undefined' && CSS.highlights) { CSS.highlights.delete('find-all'); CSS.highlights.delete('find-current'); } window.findRanges = []; window.findCurrentIdx = -1; })();", null)
                    updateInteraction()
                },
                onSaveAsScrollMarker = { /* not supported in browser mode (no bookmark to attach to) */ },
                onNext = doFindNext,
                onPrevious = doFindPrevious,
                matchesFound = state.findMatchesFound.value,
                matchCount = state.findMatchCount.value,
                currentIndex = state.findCurrentIndex.value,
                searchTrigger = state.findSearchTrigger.value,
                isSearching = state.findIsSearching.value,
                requestFocusOnOpen = true,
                // Drop below the outer game-name bar so the find UI lands at the same
                // vertical position it does in guide mode (where GuideScreen content is
                // already padded down by topBarHeightDp from the parent).
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = vaultTopBarHeightDp)
            )
        }

        // Progress overlay while assigning image as map or box art
        if (state.isAssigningImage.value) {
            val focusColorStateForAssign by settingsViewModel.focusColor.collectAsState(initial = FocusColor.CRIMSON)
            val assigningIndicatorColor = focusColorStateForAssign.color
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier.padding(32.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(48.dp),
                            color = assigningIndicatorColor,
                            strokeWidth = 3.dp
                        )
                        Text(
                            text = state.assigningImageLabel.value,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        BackHandler(enabled = showSwitcher) { tabsVm.closeSwitcher() }

        if (showSwitcher) {
            BrowserTabSwitcher(
                tabs = tabsList,
                activeTabId = activeTab?.id,
                focusColor = focusColorBrowser,
                onTap = { tab ->
                    tabsVm.flushActiveScrollY(state.webView.value?.scrollY ?: 0)
                    tabsVm.switchTo(tab.id)
                },
                onClose = { tab ->
                    if (tabsList.size <= 1) {
                        // Mirror BrowserTabsViewModel.closeTab's last-tab refusal so the
                        // denial is felt, not just toasted.
                        FeedbackManager.play(view, FeedbackEvent.ERROR, focusSoundEnabled, hapticFeedbackIntensity)
                    }
                    tabsVm.closeTab(tab.id)
                },
                onNewTab = {
                    tabsVm.flushActiveScrollY(state.webView.value?.scrollY ?: 0)
                    tabsVm.newTab()
                },
                onDismiss = { tabsVm.closeSwitcher() },
                onCloseOthers = { tabsVm.closeAllExceptActive() },
                vaultTopBarHeightDp = vaultTopBarHeightDp
            )
        }

        val lastDeletedLink by tabsVm.lastDeletedLink.collectAsState()

        val undoMessage: String? = when {
            lastClosedTabs != null  -> {
                val n = lastClosedTabs!!.size
                if (n == 1) "Closed 1 tab" else "Closed $n tabs"
            }
            lastDeletedLink != null -> "Bookmark removed"
            lastClosedTab != null   -> "Tab closed"
            else -> null
        }
        val undoAction: () -> Unit = when {
            lastClosedTabs != null  -> { { tabsVm.undoBulkClose() } }
            lastDeletedLink != null -> { { tabsVm.undoLinkDelete() } }
            lastClosedTab != null   -> { { tabsVm.undoClose() } }
            else -> { { } }
        }

        BrowserUndoBanner(
            visible = undoMessage != null,
            message = undoMessage.orEmpty(),
            onUndo = undoAction,
            focusColor = focusColorBrowser,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 72.dp)
        )

        // Toast overlay is rendered LAST so it draws on top of every other
        // overlay in this Box — including the full-screen BrowserTabSwitcher.
        // Without this, switcher-context toasts ("At least one tab must stay
        // open") are hidden behind the switcher Surface.
        ToastOverlay(
            message = state.toastMessage.value,
            onDismiss = {
                state.toastMessage.value = null
                state.toastShowCheckmark.value = false
            },
            icon = if (state.toastShowCheckmark.value) {
                {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = focusColorBrowser
                    )
                }
            } else null
        )

    }

    // Load URL when explicitly set
    LaunchedEffect(currentUrl, state.webView.value) {
        if (state.isUserNavigation.value) return@LaunchedEffect
        // Cross-vault leak guard: BrowserViewModel is Activity-scoped, so its
        // currentUrl carries over the previous vault's URL when this screen
        // mounts for a new vaultId. Without this gate, that stale URL would
        // drive wv.loadUrl into the new vault's fresh WebView and the resulting
        // onPageFinished would persist the wrong URL onto the new vault's tab
        // row. activeTab is already filtered through this vaultId (see
        // BrowserTabsVaultFilter.kt); requiring currentUrl to match the active
        // tab's URL implicitly anchors this load on this-vault state.
        val tab = activeTab ?: return@LaunchedEffect
        if (currentUrl != tab.url) return@LaunchedEffect

        state.webView.value?.let { wv ->
            currentUrl?.let { url ->
                val currentWebViewUrl = wv.url
                if (currentWebViewUrl != url && url.isNotBlank()) {
                    val isSearchUrl = isSearchUrlForEngine(currentSearchEngine, url)
                    val isLandingPage = url.contains("landing.html")
                    val isFavoriteFile = false // No favorites in Browser

                    val shouldLoad = isFavoriteFile || isSearchUrl || isLandingPage
                    if (shouldLoad) {
                        vm.setLoading(true)
                        try {
                            if (url.startsWith("file://")) {
                                loadFileUrl(wv, url)
                            } else {
                                if (isAllowedWebViewUrl(url)) {
                                    wv.loadUrl(url)
                                } else {
                                    Log.w("BrowserScreen", "Rejected URL with unsupported scheme: $url")
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("BrowserScreen", "LaunchedEffect: Error loading URL from view model", e)
                        }
                    }
                }
            }
        }
    }
}

/** Desktop Chrome UA used only for image/image-result URLs so CDNs accept the request. */
private const val DESKTOP_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

/**
 * File extensions the download listener treats as images when the MIME type is
 * absent or generic (some CDNs serve `application/octet-stream` even for PNG/JPG).
 */
private val IMAGE_DOWNLOAD_EXTENSIONS = listOf(
    ".jpg", ".jpeg", ".png", ".gif", ".webp", ".bmp", ".svg", ".heic", ".avif"
)
