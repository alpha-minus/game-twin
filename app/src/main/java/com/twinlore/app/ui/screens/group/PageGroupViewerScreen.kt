package com.twinlore.app.ui.screens.group

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.LocalConfiguration
import android.content.res.Configuration
import com.twinlore.app.ui.navigation.NavigableIconButton
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import com.twinlore.app.ui.utils.FeedbackEvent
import com.twinlore.app.ui.utils.FeedbackManager
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.webkit.WebSettings
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import android.widget.Toast
import com.twinlore.app.data.library.PageGroupFindEngine
import com.twinlore.app.data.local.PageGroupEntryEntity
import com.twinlore.app.di.NetworkConnectivityEntryPoint
import com.twinlore.app.di.OfflineGuideLoaderEntryPoint
import com.twinlore.app.di.PageGroupFindEntryPoint
import com.twinlore.app.di.ResourceResolverEntryPoint
import com.twinlore.app.data.FocusColor
import com.twinlore.app.domain.ScrollMarker
import com.twinlore.app.ui.LocalCachedFocusColor
import com.twinlore.app.ui.LocalThemePalette
import com.twinlore.app.ui.MainViewModel
import com.twinlore.app.ui.screens.DeleteAllScrollMarkersConfirmDialog
import com.twinlore.app.ui.screens.GuideWebView
import com.twinlore.app.ui.screens.REMOVE_PALETTE_CSS_JS
import com.twinlore.app.ui.screens.SELECTION_RECT_JS
import com.twinlore.app.ui.screens.ScrollMarkerContextMenu
import com.twinlore.app.ui.screens.SettingsViewModel
import com.twinlore.app.ui.screens.buildIframeGateJs
import com.twinlore.app.ui.screens.buildLocalLinkMarkerJs
import com.twinlore.app.ui.screens.buildPaletteCssJs
import com.twinlore.app.ui.screens.clearScrollMarkerHighlight
import com.twinlore.app.ui.screens.colorToContrastHex
import com.twinlore.app.ui.screens.colorToHex
import com.twinlore.app.ui.screens.highlightScrollMarkerInPage
import com.twinlore.app.ui.screens.scrollToMarkerByText
import com.twinlore.app.ui.screens.offline.ANDROID_INTERFACE_LANDING_CALL
import com.twinlore.app.ui.screens.offline.NavigationHistoryStrip
import com.twinlore.app.ui.screens.offline.REMOVE_READING_MODE_JS
import com.twinlore.app.ui.screens.offline.SCROLL_SETTLE_MAX_TICKS
import com.twinlore.app.ui.screens.offline.SCROLL_SETTLE_TICK_MS
import com.twinlore.app.ui.screens.offline.SETTLE_SCROLL_REARM_JS
import com.twinlore.app.ui.screens.offline.ScrollIndicatorChip
import com.twinlore.app.ui.screens.offline.applyChromeStripPrelude
import com.twinlore.app.ui.screens.offline.applyOfflineChromeGestureListeners
import com.twinlore.app.ui.screens.offline.applyPalettePrelude
import com.twinlore.app.ui.screens.offline.buildMhtAssetInterceptor
import com.twinlore.app.ui.screens.offline.buildOfflineWebChromeClient
import com.twinlore.app.ui.screens.offline.SCROLL_PAD_JS
import com.twinlore.app.ui.screens.offline.buildReadingModeJs
import com.twinlore.app.ui.screens.offline.buildSettlingScrollJs
import com.twinlore.app.ui.screens.offline.copyAnchorTextToClipboard
import com.twinlore.app.ui.screens.offline.filterForHost
import com.twinlore.app.ui.screens.offline.isExternalAnchor
import com.twinlore.app.ui.screens.offline.mayPersistScrollRatio
import com.twinlore.app.ui.screens.offline.spliceInlineDecorationsScript
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Page-group viewer. Walks every ToC entry (anchors included) via the
 * top-bar prev/next chevrons, exposes the full ToC in a modal bottom
 * sheet, and offers Rename + Delete from the kebab. Find is a v1
 * placeholder — T12 wires the real search sheet.
 *
 * Single WebView is reused across entry transitions: anchor-sibling
 * transitions (same `mhtResourceId`) jump via `location.hash` without
 * reloading; cross-file transitions parse the MHT through
 * [com.twinlore.app.data.MhtCacheManager] and reload via
 * `loadDataWithBaseURL`. Per-entry scroll position is captured as a
 * ratio `scrollY/contentHeight` on every transition and on ON_PAUSE.
 * The richer anchor-text restore model (see GuideScreen.loadGuideBookmark)
 * is deliberately deferred: the entity columns are written, only the
 * ratio is consumed at restore time.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageGroupViewerScreen(
    vaultId: String,
    groupTabId: String,
    onBack: () -> Unit,
    onYouTubeSearch: (String) -> Unit = {},
    onWebSearch: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    /**
     * How much chrome the host draws below this viewer. The default clears the
     * vault session's bottom bar; the fold panel draws its own bar outside the
     * viewer and passes zero.
     */
    chromeBelow: androidx.compose.ui.unit.Dp = 66.dp,
    /**
     * Reports whether a field wants the keyboard. A host whose window refuses
     * focus, like the fold panel's, has to take it for the duration and give
     * it back after: a search cannot take a character otherwise, and a game
     * underneath cannot take a button press while it is held.
     */
    onWantsKeyboard: (Boolean) -> Unit = {},
    /** A text selection wants the window focused, with no keyboard. */
    onWantsFocus: (Boolean) -> Unit = {},
    /** What the hosting window can put on screen. See [GuideViewerCapabilities]. */
    capabilities: com.twinlore.app.ui.screens.guide.GuideViewerCapabilities =
        com.twinlore.app.ui.screens.guide.GuideViewerCapabilities.ACTIVITY,
    /**
     * A link that would leave the reader, when
     * [com.twinlore.app.ui.screens.guide.GuideViewerCapabilities.externalNavigation]
     * is false. The load has already been consumed; the host decides what to
     * offer instead. Never called in the app.
     */
    onExternalLink: (String) -> Unit = {},
    /** Text size, when the host owns the control. See the single-guide viewer. */
    textZoomOverride: Float? = null,
    /** False where there is nothing behind this viewer to go back to. */
    showBack: Boolean = true,
    /**
     * Moves each time the app comes back from Always on Top, and this viewer
     * re-reads the group's rows on each move: the panel hosted its own copy
     * over them and wrote the chapter and position it was left at. Zero, the
     * default, never moves, which is every host but the vault session.
     */
    positionSyncKey: Int = 0,
    /**
     * The app's own state. Null when this viewer is hosted somewhere with no
     * activity behind it, which today means the fold panel's overlay window.
     * Everything reached through it is an app action rather than a reading
     * action: handing a link to the Browser tab, naming the open document in
     * the vault top bar, assigning an image as artwork. A null host loses
     * those and keeps the reading.
     */
    mainViewModel: MainViewModel? = hiltViewModel(),
    viewModel: PageGroupViewerViewModel = hiltViewModel(key = "page_group_$groupTabId"),
    /** Drives the inner top bar's animated visibility. Standalone hosts can
     *  leave the default `true` to keep the bar permanently visible. */
    chromeVisible: Boolean = true,
    /** Called when the user taps an idle area of the body — toggles the
     *  surrounding chrome (top + bottom). Default no-op. */
    onChromeToggle: () -> Unit = {},
    /** Called when the WebView is scrolled downward past a small threshold
     *  — hides chrome (iOS-Safari style). Default no-op. */
    onChromeHide: () -> Unit = {},
    /** Called on any other interaction we want to surface chrome for (Back
     *  pressed, ToC opened, find pick, etc.). Default no-op. */
    onChromeReveal: () -> Unit = {},
    /** True while the Guide tab is the active tab in the vault session.
     *  Gates the controller-action / stick wiring so a hidden tab doesn't
     *  fight the visible one for the global controller flow. */
    isActive: Boolean = true,
    /** Controller focus anchors plumbed from the vault session so D-pad UP from
     *  the bottom bar reaches the top bar and D-pad DOWN returns from it. */
    contentFocusRequester: androidx.compose.ui.focus.FocusRequester? = null,
    bottomBarFocusRequester: androidx.compose.ui.focus.FocusRequester? = null,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    // Bumped by Copy in the panel, where a toast would land on the game's display.
    var copiedPillTrigger by remember { mutableStateOf(0) }
    val state by viewModel.state.collectAsState()

    LaunchedEffect(groupTabId) { viewModel.bind(groupTabId) }

    // Guide tab re-tap exits the page-group viewer back to the picker, matching
    // GuideScreen's behaviour for single-page guides. onBack already routes
    // through MainViewModel.closePageGroupForVault so the dispatch in
    // GuideTabContent re-mounts GuideScreen, which then opens the picker.
    LaunchedEffect(Unit) {
        mainViewModel?.tabReselected?.collect { tab ->
            if (tab == com.twinlore.app.domain.VaultTab.Guide) onBack()
        }
    }

    val resolver = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            ResourceResolverEntryPoint::class.java
        ).resourceResolver()
    }
    val mhtCacheManager = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            OfflineGuideLoaderEntryPoint::class.java
        ).mhtCacheManager()
    }

    val findEp = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            PageGroupFindEntryPoint::class.java
        )
    }
    // Network manager — re-checked on every onPageFinished so a user who toggles
    // connectivity mid-session sees fresh iframes on the next page navigation.
    val networkConnectivityManager = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            NetworkConnectivityEntryPoint::class.java
        ).networkConnectivityManager()
    }
    // Cosmetic element-hide layer (CosmeticFilters): same gate + idiom as the
    // live browser. See the matching block in SingleGuideViewerScreen for
    // rationale.
    val privacyPreferences = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            com.twinlore.app.di.PrivacyPreferencesEntryPoint::class.java
        ).privacyPreferences()
    }
    val sidecarCache = remember(findEp) { findEp.sidecarCache() }
    val findEngine = remember(groupTabId, sidecarCache) {
        PageGroupFindEngine(sidecarResolver = { mhtResourceId ->
            // Sidecars live in the per-app cache, keyed by mhtResourceId.
            // Missing files mean the cache was evicted (or never built on
            // this device) — the find-sheet ensure pass below regenerates them.
            sidecarCache.sidecarFile(groupTabId, mhtResourceId).takeIf { it.exists() }
        })
    }

    var showTocView by remember(groupTabId) { mutableStateOf(false) }
    // Last-selected ToC overlay tab (0 = Contents, 1 = Markers). Hoisted
    // here so the user's choice survives closing and reopening the overlay
    // within the same group session. Resets to Contents when the group
    // changes (the remember(groupTabId) key).
    var tocTabIndex by remember(groupTabId) { mutableStateOf(0) }

    // System-back closes the ToC overlay (matches the back-arrow icon in
    // the overlay header) rather than navigating away from the viewer.
    // Enabled only while the overlay is up so the normal viewer-exit back
    // behaviour is preserved otherwise.
    androidx.activity.compose.BackHandler(enabled = showTocView) {
        showTocView = false
    }

    // L1 / R1 scroll-marker stepping focus.
    var focusedMarkerIndex by remember(groupTabId) { mutableStateOf<Int?>(null) }
    var showFind by remember(groupTabId) { mutableStateOf(false) }
    // A selection in the panel needs the window focused too: Chromium hides
    // its drag handles in a window that never has focus, and the panel is
    // otherwise kept unfocusable so the game keeps its input.
    var panelSelecting by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(showFind) { onWantsKeyboard(showFind) }
    androidx.compose.runtime.LaunchedEffect(panelSelecting) { onWantsFocus(panelSelecting) }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { onWantsKeyboard(false); onWantsFocus(false) } }
    // Browser-style page-load progress. TRUE from the moment a cross-file
    // entry switch starts its Kotlin-side prep (MHT resolve + extract +
    // parse) until the WebView's onPageFinished fires for the new page —
    // the same window the in-app browser covers with the thin strip under
    // its URL bar. Same-file anchor jumps and same-page fragment links
    // never set it (no load happens, the jump is instant). Covers every
    // initiator because they all funnel through the activeEntry/
    // lastTapSerial LaunchedEffect: top-bar Prev/Next, ToC picks, tapped
    // local links (viewModel.onEntryTapped), marker taps, the floating
    // next-page card, and the history strip.
    var pageLoading by remember(groupTabId) { mutableStateOf(false) }
    // Measured height of the floating top bar (px) so the progress strip
    // can pin to its bottom edge — same approach as BrowserScreen's
    // measuredTopBarHeightDp.
    var topBarHeightPx by remember(groupTabId) { mutableStateOf(0) }
    // Safety: a stuck load (corrupt MHT, renderer hang, onPageFinished
    // never firing) must not strand the bar. Mirrors MainViewModel's 8s
    // pdfLoadingByVault safety timeout.
    LaunchedEffect(pageLoading) {
        if (pageLoading) {
            delay(8_000L)
            pageLoading = false
        }
    }
    // True when the WebView's scroll position is within ~500dp of the end
    // of the body. Used only to render the floating "Next page" affordance
    // at the bottom. Reset to false on each entry-change.
    var nearEndOfPage by remember(groupTabId) { mutableStateOf(false) }
    val bottomEdgeThresholdPx = with(androidx.compose.ui.platform.LocalDensity.current) {
        500.dp.toPx().toInt()
    }
    // Live wv.scrollY (WebView coordinate space — CSS-px × pinch scale). Used by the ScrollIndicatorChip
    // position. Updated by the scroll listener.
    var liveScrollY by remember(groupTabId) { mutableStateOf(0) }
    // Browser-style back/forward history. Snapshot pushed BEFORE each
    // explicit jump (Prev/Next, ToC tap, anchor click, marker tap). Pure
    // finger-scroll is transparent to history. Back/Forward replay the
    // snapshot — restoring entry + scroll position. Forward stack survives
    // scrolling; only cleared on the next explicit jump that follows a
    // Back (standard browser). The stack rules live in [PageGroupHistory].
    var history by remember(groupTabId) { mutableStateOf(PageGroupHistory()) }
    // Single-use scroll target applied after the next entry load. Same-MHT
    // restores can scroll synchronously; cross-MHT restores have to wait
    // for onPageFinished and re-use the existing pendingScrollRatio pipe.
    var pendingHistoryScrollY by remember(groupTabId) { mutableStateOf<Int?>(null) }
    // Cross-file fallback when the entry has no fragment / saved ratio /
    // Set when the user explicitly navigates to an adjacent entry via the
    // top bar's Prev/Next chevrons. Suppresses the entry-load ratio restore
    // on the destination so the user lands at the chapter title rather than
    // at whatever scroll position they previously left this entry. Consumed
    // by the entry-change LaunchedEffect; cleared
    // there in the same pass.
    var skipScrollRestoreOnce by remember(groupTabId) { mutableStateOf(false) }
    // True while the landing script spliced into the current entry still has
    // the page, so a scroll read now is the restore's progress and not the
    // reader's place. Set when the landing is spliced, since the script does
    // not start until DOMContentLoaded and a capture before that would record
    // the blank document's 0; maintained afterwards by the script itself via
    // the onScrollLandingChanged bridge.
    val scrollLandingPending = remember(groupTabId) { mutableStateOf(false) }
    // Backstop, same reasoning as the single-page viewer: if the script never
    // reports back, this entry's position would silently stop being saved.
    LaunchedEffect(groupTabId, scrollLandingPending.value) {
        if (!scrollLandingPending.value) return@LaunchedEffect
        delay(SCROLL_SETTLE_MAX_TICKS * SCROLL_SETTLE_TICK_MS + 2000L)
        scrollLandingPending.value = false
    }
    // Suppresses onChromeHide for ~1s after a programmatic chapter scroll so
    // the top bar stays visible while the WebView animates to the new heading.
    var suppressChromeHideUntil by remember(groupTabId) { mutableStateOf(0L) }
    // A small slack so the chrome-reveal at top fires for the last few px
    // of scroll wind-down (rubber-banding, momentum scrolls etc.).
    val topRevealThresholdPx = with(androidx.compose.ui.platform.LocalDensity.current) {
        8.dp.toPx().toInt()
    }
    // Chapter-pick activation distance: a chapter becomes "current" once
    // its heading is within ~100 CSS px below the viewport top — sized to
    // clear the 80-CSS-px scroll-margin-top jump offset (Prev/Next lands
    // the heading at +80) without skipping past typical multi-hundred-CSS
    // inter-chapter spacing. The actual slop lives in the picker callsite
    // (PageGroupViewerScreen scroll listener) along with the viewport
    // fallback for end-of-document chapters at low zoom.

    // Hide the surrounding vault-session chrome (top + bottom bars) whenever
    // the find or ToC overlays are open OR the floating Prev/Next chapter
    // bar is showing. Their own header / button rows replace the navigation
    // context and stacking them on top of the vault chrome just clashes
    // visually. Restored as soon as the gates clear.
    LaunchedEffect(showFind, showTocView) {
        if (showFind || showTocView) onChromeHide() else onChromeReveal()
    }
    var showConfirmDelete by remember(groupTabId) { mutableStateOf(false) }
    // Display sheet (driven by the kebab) — holds Theme, Apply-theme,
    // Font, Text size in one place. textZoom is an Int percentage
    // (Android WebView API); we wrap a Float slider 0.5..2.0 around it.
    var showDisplaySheet by remember(groupTabId) { mutableStateOf(false) }
    // Set from the loaded chunk, so the Display sheet can dim Smart Formatting
    // on groups whose pages never went through the text converter.
    var isTextGuide by remember(groupTabId) { mutableStateOf(false) }

    // System-back outside of any overlay closes the page-group viewer and
    // returns to the Guide-tab file picker — matches how the legacy
    // browser-based viewer behaved. Placed AFTER the find/tocView/display
    // state is declared so the lambda closes over fresh references.
    androidx.activity.compose.BackHandler(enabled = !showTocView && !showFind && !showDisplaySheet) {
        onBack()
    }
    // Per-group text zoom — seeded from Room (the group's SavedTab row
    // shares the same zoomLevel column we use for single bookmarks)
    // and persisted via updateBookmarkZoomLevel on slider change.
    val savedTabRepositoryForZoom = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            com.twinlore.app.di.SavedTabRepositoryEntryPoint::class.java
        ).savedTabRepository()
    }
    var textZoomFraction by remember(groupTabId) { mutableStateOf(1.0f) }
    LaunchedEffect(textZoomOverride) {
        if (textZoomOverride != null) textZoomFraction = textZoomOverride
    }
    LaunchedEffect(groupTabId) {
        val fresh = withContext(Dispatchers.IO) {
            savedTabRepositoryForZoom.getTabById(groupTabId)?.zoomLevel
        }
        if (fresh != null) textZoomFraction = fresh
    }
    var pendingRemoveEntryId by remember(groupTabId) { mutableStateOf<String?>(null) }
    var pendingDeleteMarkerId by remember(groupTabId) { mutableStateOf<String?>(null) }
    // F9: tapping a highlighted marker span surfaces a context menu with
    // Delete-one / Delete-all. The bridge posts the marker index back to the
    // main thread which sets this state. We also need the "delete all"
    // confirm dialog.
    var markerSpanTapped by remember(groupTabId) { mutableStateOf<ScrollMarker?>(null) }
    var showMarkerDeleteAllConfirm by remember(groupTabId) { mutableStateOf(false) }
    // K2: hoisted find-screen query. Persists across close-reopen of the
    // find view so the input stays populated. Also pre-filled by the
    // WebView text-selection "Find in page" action (see `update` lambda).
    var findQuery by remember(groupTabId) { mutableStateOf("") }
    // K3: tracks the query whose findAllAsync overlay is currently live on
    // the page. Surfaces a "Clear" banner so the user can dismiss the
    // yellow-box highlights without re-opening the find view.
    var activeSearchQuery by remember(groupTabId) { mutableStateOf<String?>(null) }
    // What the find searches: the cross-page result list, or only the open
    // page through the single guide's find bar. Kept for the group's visit,
    // so the Find button reopens whichever the user last chose.
    var findScope by remember(groupTabId) { mutableStateOf(PageGroupFindScope.ALL_PAGES) }
    val pageFindOpen = showFind && findScope == PageGroupFindScope.THIS_PAGE
    val pageFindOpenRef by rememberUpdatedState(pageFindOpen)
    // This-page find bar state, fed by the WebView's find listener, the same
    // fields the single guide viewer keeps.
    var pageFindMatchesFound by remember(groupTabId) { mutableStateOf<Boolean?>(null) }
    var pageFindMatchCount by remember(groupTabId) { mutableStateOf(0) }
    var pageFindCurrentIndex by remember(groupTabId) { mutableStateOf(0) }
    var pageFindSearchTrigger by remember(groupTabId) { mutableStateOf(0) }
    var pageFindIsSearching by remember(groupTabId) { mutableStateOf(false) }
    fun resetPageFind() {
        pageFindMatchesFound = null
        pageFindMatchCount = 0
        pageFindCurrentIndex = 0
        pageFindIsSearching = false
    }

    // Theme palette — same source `GuideScreen` reads so the page-group viewer
    // matches the rest of the app's offline-content theming (background colour
    // for the pre-paint flash + algorithmic-darkening hint for the renderer).
    val palette = LocalThemePalette.current

    // Palette CSS injection: same settings source the single-page guide viewer
    // uses, and not a ViewModel, because this screen is also hosted in the fold
    // panel's overlay window where hiltViewModel() cannot resolve.
    val uiSettings = com.twinlore.app.ui.rememberAppUiSettings()
    val guideThemeInject by uiSettings.guideThemeInject.collectAsState(initial = true)
    val smartFormatting by uiSettings.guideSmartFormatting.collectAsState(initial = true)
    val appFont by uiSettings.appFont.collectAsState(initial = com.twinlore.app.data.AppFont.DEFAULT)
    val paletteBgHex = remember(palette) { String.format("#%06X", 0xFFFFFF and palette.background.toArgb()) }
    val paletteFgHex = remember(palette) { String.format("#%06X", 0xFFFFFF and palette.foreground.toArgb()) }
    val currentPaletteBgHex by rememberUpdatedState(paletteBgHex)
    val currentPaletteFgHex by rememberUpdatedState(paletteFgHex)
    val currentAppFont by rememberUpdatedState(appFont)
    val currentGuideThemeInject by rememberUpdatedState(guideThemeInject)
    val currentSmartFormatting by rememberUpdatedState(smartFormatting)
    val currentTextZoomFraction by rememberUpdatedState(textZoomFraction)

    // Focus color — same source the single-page guide viewer uses so highlighted
    // markers render with an identical tint across both viewer types.
    val focusColorState by uiSettings.focusColor.collectAsState(initial = FocusColor.CRIMSON)
    val cachedFocusColor = LocalCachedFocusColor.current?.value
    val focusColor = cachedFocusColor ?: focusColorState.color
    // Fall back to electric-blue only when the chosen black/white accent would be
    // invisible on the CURRENT theme background — black on dark, white on light.
    // Black on a light theme (or white on dark) is readable and kept as-is, so the
    // chrome (display sheet toggle/reset, nav strip) reflects the real choice.
    val useBlueHighlight = (focusColor == FocusColor.WHITE.color && !palette.isDark) ||
        (focusColor == FocusColor.BLACK.color && palette.isDark)
    val effectiveFocusColor = if (useBlueHighlight) FocusColor.ELECTRIC_BLUE.color else focusColor
    val effectiveHighlightHex = remember(effectiveFocusColor) { colorToHex(effectiveFocusColor) }
    val effectiveContrastHex = remember(effectiveFocusColor) { colorToContrastHex(effectiveFocusColor) }
    // Keep latest hex values accessible from factory-captured lambdas (WebViewClient,
    // JS-bridge callbacks) without triggering a factory re-run.
    val currentHighlightHex by rememberUpdatedState(effectiveHighlightHex)
    val currentContrastHex by rememberUpdatedState(effectiveContrastHex)

    // Collect markers as Compose state so they're available for filtering below.
    val markers by viewModel.markers.collectAsState()

    // Helper: maps the active entry's ScrollMarkerEntity rows to the ScrollMarker
    // domain type that highlightScrollMarkerInPage expects, filtered to the active
    // entry only so markers from other entries in the same group are not injected.
    fun activeEntryMarkersDomain(activeEntryId: String?): List<ScrollMarker> {
        if (activeEntryId == null) return emptyList()
        return markers
            .filter { it.pageGroupEntryId == activeEntryId }
            .sortedBy { it.markerOrder }
            .map { e ->
                ScrollMarker(
                    id = e.id,
                    label = e.label,
                    scrollY = e.scrollPosition,
                    order = e.markerOrder,
                    occurrenceIndex = e.occurrenceIndex
                )
            }
    }

    // After onPick from the find sheet, this holds the query to re-issue
    // as WebView.findAllAsync once the new entry finishes loading. For
    // anchor-sibling transitions (same file) the call happens inline.
    val pendingFindQuery = remember(groupTabId) { mutableStateOf<String?>(null) }

    // Single WebView held across the screen's lifetime. AndroidView's factory
    // runs once; everything else mutates the instance via update / effects.
    val webViewRef = remember { mutableStateOf<GuideWebView?>(null) }

    // This-page find, as the single guide viewer runs it: findAllAsync
    // highlights every match in place and the find listener reports the
    // count back.
    fun runPageFind() {
        val wv = webViewRef.value ?: return
        if (findQuery.isBlank()) {
            wv.clearMatches()
            resetPageFind()
            return
        }
        pageFindIsSearching = true
        pageFindSearchTrigger++
        wv.findAllAsync(findQuery)
    }
    // Closing drops the highlights with the bar, as the single guide viewer
    // does. The query stays (K2), so reopening picks up where it was.
    fun closePageFind() {
        webViewRef.value?.clearMatches()
        // A re-search queued for a page still loading would highlight it
        // with the bar gone.
        pendingFindQuery.value = null
        resetPageFind()
        activeSearchQuery = null
        showFind = false
    }
    fun switchFindScope(to: PageGroupFindScope) {
        if (to == findScope) return
        if (findScope == PageGroupFindScope.THIS_PAGE) {
            // The result list covers the page; leave no highlights behind
            // it that the Clear banner does not know about.
            webViewRef.value?.clearMatches()
            resetPageFind()
        }
        findScope = to
    }
    // Opening the bar (or flipping to it) with a query already in the field
    // searches it straight away, so the page shows where it stands.
    LaunchedEffect(pageFindOpen) {
        if (pageFindOpen) {
            if (findQuery.isNotBlank()) runPageFind() else resetPageFind()
        }
    }
    // The bar floats over the page with the vault chrome hidden: publish it
    // as an open overlay, as the single guide viewer does, so D-pad presses
    // reach its controls instead of being eaten to wake the chrome.
    LaunchedEffect(pageFindOpen) {
        mainViewModel?.setVaultSessionGuideOverlayOpen(pageFindOpen)
    }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { if (pageFindOpenRef) mainViewModel?.setVaultSessionGuideOverlayOpen(false) }
    }
    androidx.activity.compose.BackHandler(enabled = pageFindOpen) { closePageFind() }
    // The WebView factory runs once; these keep its callbacks on this
    // composition's state.
    val currentClosePageFind by rememberUpdatedState<() -> Unit>({ closePageFind() })
    val currentOnPageFindResult by rememberUpdatedState<(Int, Int) -> Unit>({ activeMatch, numMatches ->
        pageFindIsSearching = false
        pageFindMatchCount = numMatches
        pageFindCurrentIndex = if (numMatches > 0) activeMatch + 1 else 0
        pageFindMatchesFound = numMatches > 0
    })

    // Track the previous entry so we can capture its scroll ratio before
    // the WebView's content changes. Re-initialized when entries first load.
    var previousEntryId by remember(groupTabId) { mutableStateOf<String?>(null) }

    // The mht resource currently loaded into the WebView. Used to decide
    // anchor-jump vs full reload on transitions.
    var loadedResourceId by remember(groupTabId) { mutableStateOf<String?>(null) }

    // MHT manifest + parsed-cache dir for the resource currently loaded into
    // the WebView. Used by the image-tap handler to swap `http(s)://…` src
    // URLs for the on-disk extracted file so the zoom viewer loads instantly.
    // Backing MutableState refs are exposed (not just via `by` delegation) so
    // shouldInterceptRequest — which runs on a non-main WebView worker thread —
    // can read the current value off the snapshot without going through the
    // delegated property getter.
    val loadedAssetBaseDirState = remember(groupTabId) { mutableStateOf<java.io.File?>(null) }
    var loadedAssetBaseDir by loadedAssetBaseDirState
    val loadedAssetManifestState = remember(groupTabId) { mutableStateOf<Map<String, String>>(emptyMap()) }
    var loadedAssetManifest by loadedAssetManifestState

    // Image-zoom overlay: tap on an <img> inside the saved page populates this
    // with a `file://` (manifest-resolved) or `http(s)://` (fallback) model;
    // the GuideImageZoomViewer renders over the body.
    var guideImageZoomModel by remember(groupTabId) { mutableStateOf<String?>(null) }

    // Long-press on an image surfaces BrowserImageContextMenu (same dialog the
    // in-app browser uses). Set by setOnLongClickListener once
    // requestImageRef resolves the real <img>.src.
    var pendingImageLongPressUrl by remember(groupTabId) { mutableStateOf<String?>(null) }

    // When set, the next onPageFinished should restore this scroll ratio.
    // Cleared after the restore runs so subsequent page-finishes (e.g. iframe
    // loads inside the page) don't re-trigger it.
    val pendingScrollRatio = remember(groupTabId) { mutableStateOf<Double?>(null) }
    val pendingFragment = remember(groupTabId) { mutableStateOf<String?>(null) }

    // Scroll-driven chapter title: map from anchor-sibling entry id -> the
    // CSS-px Y where that fragment's heading sits. Populated by
    // PgAnchorBridge after each cross-file load settles, and re-extracted
    // by the layout-invalidating-settings LaunchedEffect. Cleared when the
    // loaded MHT changes. The scroll listener converts wv.scrollY to CSS px
    // (÷ scale) and compares against this map. Purely a display refinement;
    // does NOT route through viewModel.onEntryTapped or persist anywhere.
    val anchorPositions = remember(groupTabId) { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var scrollDisplayEntryId by remember(groupTabId) { mutableStateOf<String?>(null) }

    // WebView's current pinch-zoom scale factor. Updated from
    // WebViewClient.onScaleChanged. The scroll listener divides scrollY by
    // (density * webViewScale) so the CSS-px comparison against
    // anchorPositions stays correct after the user pinch-zooms. Without
    // this the chapter picker drifts on every pinch — same fix as the
    // single-guide viewer.
    var webViewScale by remember(groupTabId) { mutableStateOf(1f) }

    val activeEntry = state.activeEntry

    // Last chapter label the scroll-driven detector found across page
    // boundaries. Used as fallback when the current page has no detected
    // chapter of its own — a logical chapter often spans multiple pages
    // (e.g. "Bosses" continues onto a body-only sub-page) and the user
    // expects the title to carry forward.
    var lastDetectedChapterLabel by remember(groupTabId) { mutableStateOf<String?>(null) }

    // displayEntry / displayIndex: refine the top-bar identity by the user's
    // current scroll position within the loaded MHT. scrollDisplayEntryId is
    // updated by the scroll listener as anchor headings cross the viewport
    // top; until it's set (or after a cross-file load before anchors are
    // measured) we fall back to state.activeEntry.
    val displayEntry = scrollDisplayEntryId
        ?.let { id -> state.entries.firstOrNull { it.id == id } }
        ?: activeEntry
    val displayIndex = state.entries.indexOfFirst { it.id == displayEntry?.id }
        .takeIf { it >= 0 }
        ?: state.activeIndex

    // Controller bindings: left-stick scroll + Find / ToC / Top / End /
    // marker prev/next + L2/R2 → goPrev / goNext (the group-only override
    // — single viewer keeps L2/R2 reserved for cross-bookmark cycling).
    //
    // if, never an early return: returning out of an inline composable lambda
    // leaves Compose's slot table unbalanced. No app state means no controller
    // flow to bind to, which is right for a panel that is not the focused app.
    if (mainViewModel != null) {
    com.twinlore.app.ui.screens.offline.OfflineViewerControllerWiring(
        isActive = isActive,
        mainViewModel = mainViewModel,
        webViewProvider = { webViewRef.value },
        onOpenFind = { showFind = true },
        onToggleToc = { showTocView = !showTocView },
        onMarkerPrev = {
            val list = markers
            val wv = webViewRef.value
            if (list.isNotEmpty() && wv != null) {
                val cur = focusedMarkerIndex ?: 0
                val next = (cur - 1 + list.size) % list.size
                focusedMarkerIndex = next
                val m = list[next]
                scrollToMarkerByText(
                    webView = wv,
                    label = m.label,
                    fallbackScrollY = m.scrollPosition,
                    occurrenceIndex = m.occurrenceIndex,
                    flashFocusHex = currentHighlightHex
                ) { /* resolved Y unused */ }
            }
        },
        onMarkerNext = {
            val list = markers
            val wv = webViewRef.value
            if (list.isNotEmpty() && wv != null) {
                val cur = focusedMarkerIndex ?: -1
                val next = (cur + 1) % list.size
                focusedMarkerIndex = next
                val m = list[next]
                scrollToMarkerByText(
                    webView = wv,
                    label = m.label,
                    fallbackScrollY = m.scrollPosition,
                    occurrenceIndex = m.occurrenceIndex,
                    flashFocusHex = currentHighlightHex
                ) { /* resolved Y unused */ }
            }
        },
        onPrevAction = {
            suppressChromeHideUntil = System.currentTimeMillis() + 1000L
            skipScrollRestoreOnce = true
            viewModel.goPrev(fromEntryId = displayEntry?.id)
        },
        onNextAction = {
            suppressChromeHideUntil = System.currentTimeMillis() + 1000L
            skipScrollRestoreOnce = true
            viewModel.goNext(fromEntryId = displayEntry?.id)
        },
    )
    } // end if (mainViewModel != null)

    // Push the GROUP TITLE (file name) to the vault session top bar — the
    // file name anchors the doc-switcher chevron there. Chapter-level
    // navigation context lives in the inner viewer top bar only; doubling
    // it up here makes the chrome feel busy.
    LaunchedEffect(vaultId, state.title) {
        val title = state.title.ifBlank { null }
        mainViewModel?.setActiveDocTitle(vaultId, com.twinlore.app.domain.VaultTab.Guide, title)
    }
    DisposableEffect(vaultId) {
        onDispose {
            mainViewModel?.setActiveDocTitle(vaultId, com.twinlore.app.domain.VaultTab.Guide, null)
        }
    }

    // Re-inject or remove palette CSS whenever the toggle, palette, or font changes.
    // Mirrors the LaunchedEffect in GuideBrowserWebViewContent (line 374).
    // Apply Resize-text slider value to the WebView. Reapplied on entry
    // changes via the same webView ref so cross-file loads pick it up.
    LaunchedEffect(webViewRef.value, textZoomFraction) {
        val wv = webViewRef.value ?: return@LaunchedEffect
        // textZoom still drives saved web guides, which never opt out of the
        // autosizer. Converted text guides ignore it entirely (see
        // buildTextZoomJs) and take their size from the injected font-size.
        wv.settings.textZoom = (textZoomFraction * 100).toInt().coerceIn(50, 200)
        wv.evaluateJavascript(
            com.twinlore.app.ui.screens.offline.buildTextZoomJs(textZoomFraction), null
        )
    }

    // isActive MUST be a key: a palette change while the Guide tab is hidden
    // evaluates into a hidden/paused WebView and is never re-applied on
    // return (dark-stuck guide after switching theme elsewhere). Keying on
    // isActive re-runs the apply the moment the viewer is visible again.
    LaunchedEffect(webViewRef.value, guideThemeInject, smartFormatting, palette, appFont, isActive) {
        if (!isActive) return@LaunchedEffect
        val wv = webViewRef.value ?: return@LaunchedEffect
        // Smart formatting is a class swap on the loaded chunk, so flipping it
        // (or changing font, which changes the fit) never reloads or loses scroll.
        wv.evaluateJavascript(
            com.twinlore.app.ui.screens.offline.buildSmartFormatJs(currentSmartFormatting), null
        )
        // Algorithmic darkening is latched at factory time from the THEN-current
        // palette. A WebView created in dark mode keeps renderer-level darkening
        // on, which re-darkens the light palette CSS injected below — the guide
        // looks stuck on dark until recreated. Track the palette live, like
        // GuideBrowserWebViewContent does.
        if (androidx.webkit.WebViewFeature.isFeatureSupported(androidx.webkit.WebViewFeature.ALGORITHMIC_DARKENING)) {
            androidx.webkit.WebSettingsCompat.setAlgorithmicDarkeningAllowed(wv.settings, palette.isDark)
        }
        wv.setBackgroundColor(palette.background.toArgb())
        if (currentGuideThemeInject) {
            wv.evaluateJavascript(buildPaletteCssJs(currentPaletteBgHex, currentPaletteFgHex, currentAppFont), null)
            // Reading-mode chrome strip rides on the same toggle. The
            // active entry's savedHref drives the host filter; null filter
            // (unknown host or empty href) skips silently.
            val activeHref = viewModel.state.value.activeEntry?.savedHref
            filterForHost(activeHref)?.let {
                wv.evaluateJavascript(buildReadingModeJs(it), null)
            }
        } else {
            wv.evaluateJavascript(REMOVE_PALETTE_CSS_JS, null)
            wv.evaluateJavascript(REMOVE_READING_MODE_JS, null)
        }
        // Bottom scroll padding stays on regardless of the reader-mode toggle:
        // re-assert it after the branch above (REMOVE_READING_MODE_JS may have
        // just hidden a body-level spacer, and a re-enabled keep-chain can hide
        // it too — SCROLL_PAD_JS restores it either way).
        wv.evaluateJavascript(SCROLL_PAD_JS, null)
    }

    // Re-apply marker highlights whenever the markers list changes OR the active
    // entry switches (cross-entry navigation brings different markers into scope).
    // This covers the case where onPageFinished already fired but the Room write
    // for a just-added marker hasn't been observed yet — the Flow emission drives
    // a second pass automatically.
    LaunchedEffect(markers, activeEntry?.id) {
        val wv = webViewRef.value ?: return@LaunchedEffect
        val entryId = activeEntry?.id ?: return@LaunchedEffect
        highlightScrollMarkerInPage(
            webView = wv,
            markers = activeEntryMarkersDomain(entryId),
            focusedIndex = null,
            focusColorHex = currentHighlightHex,
            focusContrastHex = currentContrastHex
        )
    }

    /**
     * Captures the OUTGOING entry's scroll ratio. Asynchronous: JS reads
     * both `window.scrollY` and `document.documentElement.scrollHeight` in
     * CSS pixels (same coordinate space), avoiding the device-pixel /
     * CSS-pixel mismatch you get from `WebView.scrollY` + `contentHeight`.
     * Must be called BEFORE the WebView's content is changed.
     */
    fun captureCurrentScrollRatio(entryId: String?) {
        if (entryId == null) return
        val wv = webViewRef.value ?: return
        // Synchronous Android-side read. The earlier evaluateJavascript path
        // was racing the entry-change LaunchedEffect: the callback fired
        // AFTER loadDataWithBaseURL had already wiped the DOM, so we read
        // scrollY=0 against the fresh page and persisted ratio=0 for the
        // OUTGOING entry — every Prev/Next nav silently destroyed the
        // previous entry's saved scroll position.
        // Same denominator the restore JS uses (Math.round(h * ratio) where
        // h = scrollHeight). verticalScrollRangePx() returns
        // View.computeVerticalScrollRange — equivalent to scrollHeight in CSS
        // pixels.
        val scrollHeight = wv.verticalScrollRangePx().coerceAtLeast(1)
        val y = wv.scrollY
        val ratio = y.toDouble() / scrollHeight.toDouble()
        // A zero is saved like any other position - the top of a chapter is
        // somewhere a reader chooses to be, and refusing it left them with the
        // deeper position they had scrolled up FROM. The read that must be
        // refused is one taken while the landing script still has the page, and
        // that is what scrollLandingPending tracks.
        if (mayPersistScrollRatio(ratio, scrollLandingPending.value)) {
            viewModel.persistScrollRatio(entryId, ratio.coerceIn(0.0, 1.0))
        }
    }

    /**
     * Snapshot the current location for history. Reads the visible entry
     * (scrollDisplayEntryId falling back to activeEntry) and the WebView's
     * current scroll position. Returns null if there's nothing to capture
     * (no active entry yet on first compose).
     *
     * Everything here is read at call time, from state objects, never from
     * a value computed during composition: the WebView client's link taps
     * call this from the AndroidView factory, which captured the first
     * composition's locals. See [historyLocationEntryId].
     */
    fun snapshotHistory(): HistoryEntry? {
        val id = historyLocationEntryId(scrollDisplayEntryId, viewModel.state.value)
            ?: return null
        val wv = webViewRef.value
        // Store CSS-px (not WebView device-px = CSS × scale) so a restore
        // at a different pinch-zoom scale than the save still lands on
        // the same visual position.
        val scale = (wv?.scale ?: 1f).coerceAtLeast(0.01f)
        val yCss = ((wv?.scrollY ?: 0) / scale).toInt()
        return HistoryEntry(entryId = id, scrollY = yCss)
    }

    /**
     * Push the current location to back-history before an explicit jump.
     * See [PageGroupHistory.pushBeforeJump].
     */
    fun pushHistoryBeforeJump() {
        val snap = snapshotHistory() ?: return
        history = history.pushBeforeJump(snap)
    }

    /**
     * Restore a history frame; the stacks have already been stepped. Two
     * paths:
     *
     *   - Target entry == active entry → activeEntryId wouldn't change, so
     *     onEntryTapped is a no-op and the LaunchedEffect doesn't fire.
     *     Scroll the WebView directly. (Typical case for back/forward across
     *     in-page anchor clicks within the same chapter.)
     *   - Different entry → set pendingHistoryScrollY + onEntryTapped; the
     *     entry-load effect honours the pending Y over fragment/ratio.
     */
    fun applyHistoryFrame(target: HistoryEntry) {
        val activeId = viewModel.state.value.activeEntry?.id
        if (target.entryId == activeId) {
            // target.scrollY is CSS-px; wv.scrollTo wants WebView device-px
            // (CSS × current scale).
            val wv = webViewRef.value
            val scale = (wv?.scale ?: 1f).coerceAtLeast(0.01f)
            wv?.scrollTo(0, (target.scrollY * scale).toInt())
            return
        }
        skipScrollRestoreOnce = true
        // pendingHistoryScrollY carries CSS-px from here on; the same-MHT
        // and cross-file consumers handle the scale conversion at their
        // call site (their scale = the new MHT's current scale at restore
        // time).
        pendingHistoryScrollY = target.scrollY
        viewModel.onEntryTapped(target.entryId)
    }

    fun navigateHistoryBack() {
        val step = history.stepBack(snapshotHistory()) ?: return
        history = step.history
        applyHistoryFrame(step.target)
    }

    fun navigateHistoryForward() {
        val step = history.stepForward(snapshotHistory()) ?: return
        history = step.history
        applyHistoryFrame(step.target)
    }

    /**
     * Walk the loaded MHT's anchor siblings and post each fragment's
     * DOM Y back through [PgAnchorBridge.onAnchorPositions], which writes
     * into [anchorPositions]. Called from two places:
     *   - onPageFinished after a cross-file load (delayMs=400 to let the
     *     ratio-restore poller settle).
     *   - The textZoom LaunchedEffect after the user changes the font-size
     *     slider — settings.textZoom reflows the document so the cached
     *     anchor positions go stale and the scroll-driven chapter picker
     *     drifts; this re-runs the scan against the new layout.
     *
     * No-op when there's no active MHT or no siblings (initial composition
     * before Room emits). JS errors are swallowed — the chapter label just
     * stays pinned at state.activeEntry until the next successful scan.
     */
    fun scheduleAnchorPositionScan(wv: android.webkit.WebView, delayMs: Long) {
        val vmState = viewModel.state.value
        val activeMht = vmState.activeEntry?.mhtResourceId ?: return
        val siblings = vmState.entries.filter { it.mhtResourceId == activeMht }
        if (siblings.isEmpty()) return
        val descriptors = org.json.JSONArray()
        for (e in siblings) {
            val obj = org.json.JSONObject()
            obj.put("entryId", e.id)
            obj.put("fragment", e.fragment ?: "")
            descriptors.put(obj)
        }
        val descriptorsJson = descriptors.toString()
            .replace("\\", "\\\\")
            .replace("'", "\\'")
        wv.postDelayed({
            wv.evaluateJavascript(
                """
                (function() {
                    try {
                        var descriptors = JSON.parse('$descriptorsJson');
                        var results = [];
                        for (var i = 0; i < descriptors.length; i++) {
                            var d = descriptors[i];
                            var frag = d.fragment || '';
                            if (!frag) {
                                results.push({ entryId: d.entryId, scrollY: 0 });
                                continue;
                            }
                            var bare = frag.charAt(0) === '#' ? frag.substring(1) : frag;
                            if (!bare) continue;
                            try { bare = decodeURIComponent(bare); } catch (e) {}
                            var el = null;
                            try { el = document.getElementById(bare); } catch (e) {}
                            // GitHub wiki prefixes heading ids with 'user-content-'
                            // (its client JS bridges the bare slug online, never
                            // runs offline) — try the prefixed id before [name].
                            if (!el) { try { el = document.getElementById('user-content-' + bare.toLowerCase()); } catch (e) {} }
                            if (!el) {
                                try {
                                    el = document.querySelector("a[name='" + bare + "']");
                                } catch (e) {}
                            }
                            if (!el) continue;
                            var rect = el.getBoundingClientRect();
                            var y = Math.round(rect.top + window.scrollY);
                            results.push({ entryId: d.entryId, scrollY: y });
                        }
                        if (window.PgAnchorBridge && window.PgAnchorBridge.onAnchorPositions) {
                            window.PgAnchorBridge.onAnchorPositions(JSON.stringify(results));
                        }
                    } catch (e) {
                        // Bridge call silently fails: title stays pinned at
                        // state.activeEntry, no crash.
                    }
                })();
                """.trimIndent(),
                null
            )
        }, delayMs)
    }

    // Re-scan anchor positions whenever a display setting reflows the
    // document out from under the cached anchorPositions map:
    //   - textZoomFraction: settings.textZoom resizes text in place.
    //   - appFont: buildPaletteCssJs rewrites font-family (different
    //     glyph metrics → different line heights).
    //   - guideThemeInject: buildReadingModeJs hides chrome with
    //     display:none (or REMOVE_READING_MODE_JS un-hides it) — large
    //     layout shift either direction.
    // (palette swap is colour-only — no reflow — so it's not a key.)
    //
    // 500ms delay both debounces sliders (key change cancels the prior
    // coroutine; only the final drag value survives) and gives the
    // reflow time to settle. Initial composition is a no-op because
    // anchorPositions is empty until the first cross-file load completes.
    LaunchedEffect(textZoomFraction, appFont, guideThemeInject, webViewScale, groupTabId) {
        val wv = webViewRef.value ?: return@LaunchedEffect
        if (anchorPositions.value.isEmpty()) return@LaunchedEffect
        delay(500L)
        scheduleAnchorPositionScan(wv, 0L)
    }

    // Reset the near-end flag on every entry change — fresh entry lands
    // at scrollY=0, far from the end. The scroll listener flips it back
    // on once the user reaches the last 500dp.
    LaunchedEffect(activeEntry?.id) {
        nearEndOfPage = false
    }

    // React to active-entry changes. Two paths:
    //   1. Same mhtResourceId as currently loaded → anchor-only jump.
    //   2. Different (or first load) → full MHT parse + loadDataWithBaseURL.
    //
    // Keyed on state.lastTapSerial in addition to activeEntry?.id: when the
    // user has scrolled back from a previously-tapped chapter so that the
    // displayEntry differs from activeEntry, tapping Next can resolve to
    // the entry that is already active. Without the tap serial, the
    // StateFlow doesn't emit (data-class copy is structurally equal) and
    // this effect would never fire — Next/Prev appeared dead.
    LaunchedEffect(activeEntry?.id, state.lastTapSerial) {
        val entry = activeEntry ?: return@LaunchedEffect
        val wv = webViewRef.value ?: return@LaunchedEffect
        val resourceId = entry.mhtResourceId ?: return@LaunchedEffect

        // Capture outgoing scroll before mutating the WebView. If we're on
        // the same file (anchor jump), capture still applies — the user may
        // have scrolled within the page.
        if (previousEntryId != null && previousEntryId != entry.id) {
            captureCurrentScrollRatio(previousEntryId)
        }
        previousEntryId = entry.id

        if (loadedResourceId == resourceId) {
            // History restore takes precedence over everything else when
            // present — we want the user's previous scroll position exactly.
            val histY = pendingHistoryScrollY
            if (histY != null) {
                pendingHistoryScrollY = null
                // histY is CSS-px; wv.scrollTo wants device-px (CSS × scale).
                val scale = wv.scale.coerceAtLeast(0.01f)
                wv.scrollTo(0, (histY * scale).toInt())
                return@LaunchedEffect
            }
            // Anchor-sibling fast path.
            // Mirror the precedence onPageFinished uses on cross-file loads:
            // a queued marker/anchor target wins over the entry's
            // fragment/ratio. Without this, a marker that targeted an anchor
            // sibling (same MHT, different fragment) would scroll to the
            // fragment and leave the queued target sitting in the VM, where
            // a later transition would consume it incorrectly.
            val pendingMarker = viewModel.consumePendingScrollToMarker()
            if (pendingMarker != null) {
                // Walk the DOM to find the actual label text and center it.
                // Falls back to the saved pixel Y if the text isn't found.
                // G1: saved-marker taps pass flashHex=null (the F3 highlight
                // span already marks the spot); J3 find picks pass the focus
                // hex so the specific tapped occurrence briefly tints.
                scrollToMarkerByText(
                    webView = wv,
                    label = pendingMarker.label,
                    fallbackScrollY = pendingMarker.fallbackScrollY,
                    occurrenceIndex = pendingMarker.occurrenceIndex,
                    flashFocusHex = pendingMarker.flashHex
                ) { /* resolved Y not needed here */ }
            } else {
                // Scroll via JS `element.scrollIntoView({ block: 'start' })`.
                // The browser handles visual viewport / layout viewport /
                // pinch scale / rounding correctly — same primitive every
                // standard reader uses for anchor nav (Wikipedia mobile,
                // Pocket, Confluence, GitHub). Replaces an earlier Kotlin-
                // side `wv.scrollTo(target × density × scale)` path that
                // stacked badly with the user's pinch state: device-px
                // rounding could tip the picker into the previous chapter
                // by one CSS-px, leaving Next/Prev looking like a no-op.
                val fragment = entry.fragment
                if (!fragment.isNullOrEmpty()) {
                    wv.evaluateJavascript(
                        """
                        (function(){
                            try {
                                var frag = '${fragment.replace("\\", "\\\\").replace("'", "\\'")}';
                                if (frag.charAt(0) === '#') frag = frag.substring(1);
                                try { frag = decodeURIComponent(frag); } catch (e) {}
                                var el = document.getElementById(frag) ||
                                         document.getElementById('user-content-' + frag.toLowerCase()) ||
                                         document.querySelector('a[name="' + frag.replace(/"/g, '\\"') + '"]');
                                if (el) {
                                    // 80-CSS-px breathing room above the heading.
                                    // scroll-margin-top is the CSS scroll-into-view
                                    // offset spec — honoured by the browser's own
                                    // scrollIntoView so it composes correctly with
                                    // visual viewport / pinch zoom. The picker's
                                    // activeInLandingWindow tolerance is 120, comfortably
                                    // covering this 80-px offset.
                                    el.style.scrollMarginTop = '80px';
                                    el.scrollIntoView({ block: 'start', behavior: 'auto' });
                                } else {
                                    location.hash = '#' + frag;
                                }
                            } catch (e) {}
                        })();
                        """.trimIndent(),
                        null
                    )
                } else if (!skipScrollRestoreOnce && entry.scrollRatio > 0.0) {
                    // Resume-reading path: restore the user's saved scroll
                    // position. ONLY taken when the LaunchedEffect was NOT
                    // triggered by explicit Prev/Next nav — those set
                    // skipScrollRestoreOnce=true because the user is asking
                    // for the chapter's anchor, not their last reading
                    // position. Without this guard, Prev from a fragment
                    // entry to the bare-top of the MHT teleports the user
                    // to wherever they last left off — which loops through
                    // a different chapter and then loops back to here
                    // (repro 06-07 19:04, "40 39 38 37 40 39 …" pattern).
                    val scrollHeight = wv.verticalScrollRangePx().coerceAtLeast(1)
                    val targetDeviceY = (scrollHeight * entry.scrollRatio).toInt()
                    wv.scrollTo(0, targetDeviceY)
                } else {
                    wv.scrollTo(0, 0)
                }
                // Single-use: subsequent ToC tap / auto-resume on this
                // entry should restore its saved scroll position normally.
                skipScrollRestoreOnce = false
            }
            // If the user picked a find result on the same file, re-issue the
            // on-page highlight now (no onPageFinished fires for same-file jumps).
            val fq = pendingFindQuery.value
            if (fq != null) {
                wv.findAllAsync(fq)
                pendingFindQuery.value = null
            }
            return@LaunchedEffect
        }

        // Cross-file path: resolve MHT, parse to cache, load. Returns the
        // parsed HTML on success or null on any failure (resolution miss,
        // parse failure, missing index.html).
        //
        // Show the page-load bar from here: the IO block below (MHT
        // resolve + extract + parse) is the bulk of the perceived wait,
        // so flipping the flag only at loadDataWithBaseURL would leave
        // the slow phase uncovered. Cleared in onPageFinished, on the
        // failure paths below, and by the 8s safety timeout.
        pageLoading = true
        // An open This-page find bar follows the page: the new document has
        // none of the old matches, so queue the query for onPageFinished to
        // re-search and drop the old count meanwhile. Same-file jumps never
        // get here; their document, and its matches, stay put.
        if (pageFindOpen) {
            val requery = pageFindRequeryOnLoad(findQuery, pendingFindQuery.value)
            resetPageFind()
            if (requery != null) {
                pageFindIsSearching = true
                pendingFindQuery.value = requery
            }
        }
        val html: String? = withContext(Dispatchers.IO) {
            val uri = resolver.resolve(resourceId)
            val bookmarkDir = java.io.File(
                context.cacheDir,
                "group_render/$groupTabId/$resourceId"
            ).apply { mkdirs() }

            if (!mhtCacheManager.hasCachedResult(bookmarkDir)) {
                var tmpToDelete: java.io.File? = null
                val mhtFile: java.io.File? = when (uri?.scheme) {
                    "file" -> uri.path?.let { java.io.File(it) }
                    "content" -> {
                        val tmp = java.io.File(context.cacheDir, "mht_source/group_$resourceId.mht")
                        tmp.parentFile?.mkdirs()
                        val copied = context.contentResolver.openInputStream(uri)?.use { input ->
                            tmp.outputStream().use { input.copyTo(it) }
                            true
                        } ?: false
                        if (copied) { tmpToDelete = tmp; tmp } else null
                    }
                    else -> null
                }
                val parsed = mhtFile != null && mhtCacheManager.parseAndCache(mhtFile, bookmarkDir)
                tmpToDelete?.delete()
                if (!parsed) return@withContext null
            }
            mhtCacheManager.touchCache(bookmarkDir)

            val indexFile = java.io.File(bookmarkDir, "index.html")
            if (!indexFile.exists()) null else indexFile.readText(Charsets.UTF_8)
        }

        if (html == null) {
            pageLoading = false
            Toast.makeText(context, "Page not available", Toast.LENGTH_SHORT).show()
            return@LaunchedEffect
        }
        // Final guard: if the user tapped past us while parsing, skip the load.
        // Don't clear pageLoading: a newer effect run for the tapped-past
        // entry owns the flag now (LaunchedEffect cancelled this coroutine's
        // successor scenario aside, the newer run re-sets it on entry).
        if (viewModel.state.value.activeEntry?.id != entry.id) return@LaunchedEffect
        loadedResourceId = resourceId
        // Cross-file load: the previous MHT's anchor map is stale (different
        // siblings, different DOM coords). Clear both so the top bar falls
        // back to state.activeEntry until the JS bridge reports back with
        // the new MHT's anchor Ys from onPageFinished.
        anchorPositions.value = emptyMap()
        scrollDisplayEntryId = null
        // Capture the MHT manifest so the image-tap bridge can resolve
        // `<img src="http://…">` to the on-disk extracted file under the
        // bookmarkDir. Manifest format (written by MhtCacheManager): JSON
        // object of `{ contentLocationUrl: "assets/<file>" }`.
        val bookmarkDirForEntry = java.io.File(
            context.cacheDir, "group_render/$groupTabId/$resourceId"
        )
        loadedAssetBaseDir = bookmarkDirForEntry
        loadedAssetManifest = runCatching {
            val manifestFile = java.io.File(bookmarkDirForEntry, "manifest.json")
            if (!manifestFile.exists()) emptyMap()
            else {
                val obj = org.json.JSONObject(manifestFile.readText(Charsets.UTF_8))
                val map = mutableMapOf<String, String>()
                for (k in obj.keys()) map[k] = obj.optString(k)
                map.toMap()
            }
        }.getOrDefault(emptyMap())
        // Prefer the saved scrollRatio when it exists, regardless of whether
        // the entry has a fragment. Anchor sub-entries land at their fragment
        // on FIRST visit (scrollRatio is 0), but once the user scrolls past
        // the anchor's natural position we save a ratio for that entry — and
        // on reopen we want to honour that ratio rather than re-jumping to
        // the fragment (which would land them back at the section header,
        // wiping the position they'd scrolled to).
        // History restore wins over everything else. Suppress fragment +
        // ratio so onPageFinished doesn't double-scroll; the cross-file
        // consumer scrolls directly to pendingHistoryScrollY once layout
        // settles.
        if (pendingHistoryScrollY != null) {
            pendingScrollRatio.value = null
            pendingFragment.value = null
        } else {
            // An explicit cross-document anchor link tap wins over the saved
            // resume ratio: the user asked for this section, not "where I last
            // left this page". Drained single-shot; null when the tap that
            // triggered this load carried no fragment, in which case the
            // entry's own fragment / saved ratio drive the landing as before.
            val tappedFragment = viewModel.consumePendingTappedFragment()
            if (viewModel.pendingScrollToMarker.value != null) {
                // A queued cross-entry marker owns the landing. Peeked, not consumed -
                // onPageFinished's consumer stays the executor of the DOM text search.
                // Without this, the entry's resume ratio was baked into the settling
                // script below, which kept re-asserting "where I last left this page"
                // after the marker search had already centred the right spot - the
                // page ended up at its old position instead of the marker (user
                // report). Same precedence a tapped anchor fragment already has.
                pendingScrollRatio.value = null
                pendingFragment.value = null
            } else if (!tappedFragment.isNullOrBlank()) {
                pendingScrollRatio.value = null
                pendingFragment.value = tappedFragment
            } else {
                pendingScrollRatio.value = when {
                    skipScrollRestoreOnce -> null
                    entry.scrollRatio > 0.0 -> entry.scrollRatio
                    else -> null
                }
                pendingFragment.value = if (pendingScrollRatio.value == null) entry.fragment else null
            }
            // No fragment + no ratio → page lands at scrollY=0 naturally.
            // Reading-mode chrome strip removes the banner that the old
            // 400-CSS-px heading-by-label fallback was here to skip past,
            // so we no longer need to chase a chapter heading manually.
        }
        // Single-use flag: clear so a subsequent Contents-tap or auto-resume
        // honours its saved scroll position normally.
        skipScrollRestoreOnce = false
        // Base URL is a fake https origin (not file:///) so YouTube and other
        // embed players that validate the parent's origin (file:// reports as
        // "null" origin in modern browsers) don't throw error 153.
        //
        // Images / CSS / fonts are served from the on-disk MHT cache via the
        // shouldInterceptRequest override on this WebViewClient (below). The
        // interceptor reads loadedAssetManifestState + loadedAssetBaseDirState,
        // both updated when an entry loads. Resources not in the manifest fall
        // through to the network, so online still works for anything Chrome
        // didn't capture in the original MHT.
        //
        // All saved guides share the https://localhost.invalid origin and thus
        // share localStorage/cookies/IndexedDB. Accepted because saved guides
        // are user-curated and almost never use those APIs.
        //
        // Flash-kill: prepend the palette-override `<style>` block to the HTML
        // so the browser parses our theme CSS before first paint. Without this
        // the page renders briefly with the original site's white/dark colours
        // before the onPageFinished JS injection (~50-150ms later) recolours
        // it, producing a visible flicker on every chapter change. The same
        // [buildPaletteCssJs] path still runs in onPageFinished below to cover
        // palette / font changes triggered while a page is already loaded.
        val themedHtml = applyPalettePrelude(html, currentPaletteBgHex, currentPaletteFgHex, currentAppFont)
        // Reading-mode chrome strip splices BEFORE the scroll-restore script
        // when the theme-inject toggle is ON at load (parallel to the
        // single-guide viewer). Per-entry: each chapter's savedHref drives
        // its own host filter, so a page group spanning multiple sources
        // still strips correctly per page.
        val themedAndStripped0 = if (currentGuideThemeInject)
            applyChromeStripPrelude(themedHtml, entry.savedHref)
        else
            themedHtml
        // Chunks produced by TextGuideSplitter carry the converter's markup, so
        // mark the body raw before first paint rather than letting the reflowed
        // layout show and then swap.
        val themedAndStripped1 = com.twinlore.app.ui.screens.offline.applySmartFormatPrelude(
            themedAndStripped0, currentSmartFormatting
        )
        // And the text size before the paint: from onPageFinished alone the
        // chunk painted at its own size and then jumped to the saved one.
        val themedAndStripped = com.twinlore.app.ui.screens.offline.applyTextZoomPrelude(
            themedAndStripped1, currentTextZoomFraction
        )
        // Build inline scroll-restore JS that runs during HTML parse so
        // the first paint is already at the landing position. Covers
        // the three common targets: history Y, entry.fragment,
        // entry.scrollRatio. Pending marker tap is rare and stays a
        // post-load text search; brief snap accepted.
        // pendingHistoryScrollY is already in CSS-px; window.scrollTo in JS
        // takes CSS-px directly so no conversion needed.
        val historyYCssPx = pendingHistoryScrollY
        val scrollJs = buildPageGroupInlineScrollJs(
            historyYCssPx = historyYCssPx,
            fragment = pendingFragment.value,
            scrollRatio = pendingScrollRatio.value,
        )
        // Closed until the script reports back: everything between here and its
        // first report belongs to the landing, not to the reader.
        scrollLandingPending.value = scrollJs.isNotEmpty()
        // Bottom scroll padding (#gt-botpad) on EVERY group-viewer page, reader
        // mode or not — the same spacer the single guide viewer adds only in
        // reader mode. Runs after the reading-mode prelude (idempotent shared
        // id, so it never stacks) and before scroll-restore so the spacer is
        // in place when the landing scroll is computed.
        // The strip, the padding and the landing scroll all happen behind the
        // cloak. The uncloak itself runs from onPageFinished, after that pass
        // has reflowed the document too, so the first frame anyone sees is the
        // page as it will stay. See READER_CLOAK_ID.
        val inlineJs = SCROLL_PAD_JS + scrollJs
        val finalHtml = spliceInlineDecorationsScript(themedAndStripped, inlineJs)
        // Pendings get consumed by the inline JS at parse time — clear
        // them so onPageFinished's post-load consumer doesn't re-apply
        // and cause a double-scroll. The pendingMarker path is preserved
        // (it does a DOM text search that can't easily be pre-baked).
        pendingHistoryScrollY = null
        pendingFragment.value = null
        pendingScrollRatio.value = null
        wv.loadDataWithBaseURL("https://localhost.invalid/", finalHtml, "text/html", "UTF-8", null)
    }

    // ON_PAUSE: persist the current entry's scroll position. The
    // DisposableEffect cleanup also covers screen exit via back navigation.
    DisposableEffect(lifecycleOwner, groupTabId) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                captureCurrentScrollRatio(viewModel.state.value.activeEntry?.id)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            captureCurrentScrollRatio(viewModel.state.value.activeEntry?.id)
            lifecycleOwner.lifecycle.removeObserver(observer)
            // Drop any queued cross-file scroll target so a marker tap that
            // never completed (user exited the viewer before onPageFinished
            // fired) doesn't bleed into the next viewer session. The VM is
            // Activity-scoped and outlives this screen.
            viewModel.clearPendingScrollToMarker()
            viewModel.clearPendingTappedFragment()
        }
    }

    // Back from Always on Top. The panel hosted its own copy of this viewer
    // over the same rows and wrote the chapter and position it was left at;
    // this one, still composed behind it, is where it was left. Read the rows
    // again and go there.
    //
    // Another chapter is followed the way a tap is, minus two things. It is
    // not written back, because the store already says it. And the outgoing
    // chapter is not captured, because the WebView's position for it is the
    // one from before the fold and the store's is newer: previousEntryId is
    // cleared so the entry effect's capture stands down. The entries stream
    // carries the chapter's ratio, so the landing restores it as usual.
    //
    // The same chapter is scrolled in place, the way a ratio restore lands.
    // Zoom rides along: the panel's Settings surface writes the same column.
    LaunchedEffect(positionSyncKey) {
        if (positionSyncKey == 0) return@LaunchedEffect
        val fresh = withContext(Dispatchers.IO) {
            savedTabRepositoryForZoom.getTabById(groupTabId)?.zoomLevel
        }
        if (fresh != null && textZoomOverride == null) textZoomFraction = fresh
        val stored = viewModel.storedPosition() ?: return@LaunchedEffect
        if (stored.entryId != viewModel.state.value.activeEntry?.id) {
            previousEntryId = null
            skipScrollRestoreOnce = false
            viewModel.adoptStoredEntry(stored.entryId)
            return@LaunchedEffect
        }
        val wv = webViewRef.value ?: return@LaunchedEffect
        if (scrollLandingPending.value) return@LaunchedEffect
        val scrollHeight = wv.verticalScrollRangePx()
        if (scrollHeight <= 0) return@LaunchedEffect
        wv.scrollTo(0, (scrollHeight * stored.ratio.coerceIn(0.0, 1.0)).toInt())
    }

    // Box (not Scaffold) so the top bar floats over the body and the body
    // fills the entire viewport when chrome is hidden. Scaffold's topBar slot
    // reserves system-bar insets even when the slot content is null, which
    // left a visible black band above the WebView the moment the user scrolled.
    Box(modifier = modifier.fillMaxSize()) {
        val allFailed = state.entries.isNotEmpty() && state.entries.all { it.mhtResourceId == null }
        Box(modifier = Modifier.fillMaxSize()) {
            if (allFailed) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = state.title.ifEmpty { "Page group" },
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "0 of ${state.entries.size} pages saved",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = {
                            FeedbackManager.play(view, FeedbackEvent.CONFIRM)
                            showConfirmDelete = true
                        }) { Text("Delete group") }
                    }
                }
            } else {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        GuideWebView(ctx).apply {
                            // Match the palette background so there's no black flash before
                            // content loads — same source GuideScreen uses for offline guides.
                            setBackgroundColor(palette.background.toArgb())
                            // Suppress the native vertical scrollbar — our
                            // ScrollIndicatorChip overlay replaces it.
                            isVerticalScrollBarEnabled = false
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                // YouTube's embed player uses IndexedDB / WebSQL for
                                // playback state; without this the player can render
                                // its preview thumbnail but tap-to-play silently fails.
                                @Suppress("DEPRECATION") databaseEnabled = true
                                // The user IS tapping play; a strict gesture requirement
                                // sometimes drops the click when it crosses the iframe
                                // boundary on an https://localhost.invalid base page.
                                mediaPlaybackRequiresUserGesture = false
                                @Suppress("DEPRECATION") setAllowFileAccess(true)
                                setAllowContentAccess(true)
                                // N2: Permit https iframe content embedded in our
                                // https://localhost.invalid base page so YouTube / Twitch /
                                // etc. embeds can load fresh when the user is online. The
                                // IFRAME_GATE_JS step in onPageFinished swaps these for
                                // placeholders when offline.
                                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                // setAllowFileAccess stays on: the image-tap zoom viewer
                                // hands GuideImageZoomViewer a file:// URL resolved through
                                // the MHT manifest, and plain subresource loads need it.
                                // Reading one file: document FROM another does not, and a
                                // saved page's own script must never be able to do it — this
                                // viewer loads through loadDataWithBaseURL with an https
                                // base today, so the flag is inert, but it would go live the
                                // moment anything here loads a file: URL.
                                @Suppress("DEPRECATION") setAllowFileAccessFromFileURLs(false)
                                @Suppress("DEPRECATION") setAllowUniversalAccessFromFileURLs(false)
                                useWideViewPort = true
                                loadWithOverviewMode = true
                                builtInZoomControls = true
                                displayZoomControls = false
                                setSupportZoom(true)
                                // Reflow text to viewport width (Chrome-style). Saved guide
                                // HTML is typically desktop-oriented; without this the user
                                // pinches to read every page.
                                layoutAlgorithm = WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING
                            }
                            // Algorithmic darkening so generic web pages render with sensible
                            // contrast in dark themes (matches the rest of the guide pipeline).
                            if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
                                WebSettingsCompat.setAlgorithmicDarkeningAllowed(this.settings, palette.isDark)
                            }
                            // JS bridge for image-tap → zoom viewer. Resolves the
                            // tapped <img>'s src against the current entry's MHT
                            // manifest so the viewer loads the on-disk extracted
                            // file rather than re-fetching over the network.
                            addJavascriptInterface(
                                object {
                                    @android.webkit.JavascriptInterface
                                    fun onGuideImageTap(srcUrl: String) {
                                        val baseDir = loadedAssetBaseDir
                                        val manifest = loadedAssetManifest
                                        val resolved: String? = manifest[srcUrl]?.let { rel ->
                                            val file = java.io.File(baseDir, rel)
                                            if (file.exists()) "file://${file.absolutePath}" else null
                                        }
                                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                                            guideImageZoomModel = resolved ?: srcUrl
                                        }
                                    }

                                    @android.webkit.JavascriptInterface
                                    fun onScrollLandingChanged(active: Boolean) {
                                        // Posted by the spliced landing script
                                        // when it starts moving the page and
                                        // when it stops. While it is active the
                                        // scroll offset is the landing's, so
                                        // captureCurrentScrollRatio leaves the
                                        // entry's stored position alone.
                                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                                            scrollLandingPending.value = active
                                        }
                                    }
                                },
                                "androidInterface"
                            )
                            // Scroll-driven chapter title: after each cross-file
                            // load, JS walks the loaded MHT's anchor siblings
                            // and posts back each fragment's CSS-px Y. The
                            // scroll listener converts wv.scrollY to CSS-px
                            // (÷ scale) and uses this map to pick the entry
                            // whose heading is currently at/just-above the
                            // viewport top. Stored in screen-level
                            // anchorPositions; the
                            // displayEntry derivation reads it on every recomp.
                            addJavascriptInterface(
                                object {
                                    @android.webkit.JavascriptInterface
                                    fun onAnchorPositions(jsonPositions: String) {
                                        // Parse off the WebView thread, hop to
                                        // main to update Compose state. Errors
                                        // are swallowed — the title just stays
                                        // pinned at state.activeEntry if the
                                        // JSON is malformed for any reason.
                                        val parsed: Map<String, Int> = runCatching {
                                            val arr = org.json.JSONArray(jsonPositions)
                                            val map = mutableMapOf<String, Int>()
                                            for (i in 0 until arr.length()) {
                                                val obj = arr.optJSONObject(i) ?: continue
                                                val id = obj.optString("entryId").takeIf { it.isNotEmpty() }
                                                    ?: continue
                                                map[id] = obj.optInt("scrollY", 0)
                                            }
                                            map.toMap()
                                        }.getOrDefault(emptyMap())
                                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                                            anchorPositions.value = parsed
                                        }
                                    }
                                },
                                "PgAnchorBridge"
                            )
                            // JS bridge for scroll-marker highlight taps. The JS injected by
                            // highlightScrollMarkerInPage calls window.Android.onScrollMarkerHighlightClicked(idx)
                            // when the user taps a highlighted span. We scroll to that marker's
                            // pixel position — it's already on the correct entry (we only inject
                            // markers for the active entry), so this is effectively idempotent.
                            // The ref pattern (scrollMarkerTapCallbackRef) keeps the factory
                            // closure stable while allowing the callback to read the latest state.
                            val scrollMarkerTapCallbackRef = mutableStateOf<((Int) -> Unit)?>(null)
                            addJavascriptInterface(
                                object {
                                    @android.webkit.JavascriptInterface
                                    fun onScrollMarkerHighlightClicked(markerIndex: Int) {
                                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                                            scrollMarkerTapCallbackRef.value?.invoke(markerIndex)
                                        }
                                    }
                                },
                                "Android"
                            )
                            // Wire callback: open the F9 context menu with the tapped marker
                            // pre-selected. Reads directly from the ViewModel's StateFlow so
                            // the lambda always sees the current markers even though the factory
                            // closure ran once.
                            scrollMarkerTapCallbackRef.value = { markerIndex ->
                                val entryId = viewModel.state.value.activeEntry?.id
                                val domainMarkers = if (entryId == null) emptyList() else
                                    viewModel.markers.value
                                        .filter { it.pageGroupEntryId == entryId }
                                        .sortedBy { it.markerOrder }
                                        .map { e ->
                                            ScrollMarker(
                                                id = e.id,
                                                label = e.label,
                                                scrollY = e.scrollPosition,
                                                order = e.markerOrder,
                                                occurrenceIndex = e.occurrenceIndex
                                            )
                                        }
                                val marker = domainMarkers.getOrNull(markerIndex)
                                if (marker != null) {
                                    markerSpanTapped = marker
                                }
                            }
                            // Chrome reveal/hide gestures — iOS-Safari style.
                            // The shared helper handles tap-toggle, scroll-down-hide, and
                            // top-reveal. Page-group-specific scroll work (scroll-driven
                            // chapter title) is in the onScrollY callback.
                            applyOfflineChromeGestureListeners(
                                webView = this,
                                topRevealThresholdPx = topRevealThresholdPx,
                                // A body tap dismisses the this-page find bar, as in
                                // the single guide viewer. Otherwise toggle chrome.
                                onTapToggle = {
                                    if (pageFindOpenRef) currentClosePageFind() else onChromeToggle()
                                },
                                onScrollDownHide = {
                                    if (System.currentTimeMillis() >= suppressChromeHideUntil) {
                                        onChromeHide()
                                    }
                                },
                                onTopReveal = onChromeReveal,
                                onScrollY = { scrollY ->
                                    val wv = this@apply
                                    liveScrollY = scrollY
                                    // Track distance-to-end for the floating Next-page
                                    // button. Fires when there's <500dp of body left.
                                    val maxScroll = (wv.verticalScrollRangePx() - wv.height)
                                        .coerceAtLeast(1)
                                    nearEndOfPage = (maxScroll - scrollY) < bottomEdgeThresholdPx
                                    // Scroll-driven chapter pick. Convert WebView.scrollY
                                    // (CSS-px × scale, see the note at scrollYCss below)
                                    // to CSS px once so we share the bridge's unit.
                                    // Then pick the latest entry whose CSS-px Y is
                                    // at or above the activation line — scrollY + a small
                                    // CSS slop. If nothing qualifies (user is above the
                                    // first chapter heading), fall back to the first
                                    // entry of the current MHT so the ToC highlight
                                    // doesn't stick on a stale pick from before.
                                    //
                                    // Scale handling: read view.scale live (per scroll
                                    // event) rather than trusting the onScaleChanged
                                    // callback alone, because on some WebView builds it
                                    // doesn't fire reliably for pinch gestures. Updating
                                    // webViewScale state from here also keys the
                                    // re-extract LaunchedEffect — TEXT_AUTOSIZING can
                                    // reflow on zoom, so a scale change is treated as a
                                    // potential layout-invalidating event.
                                    val liveScale = wv.scale.coerceAtLeast(0.01f)
                                    if (kotlin.math.abs(liveScale - webViewScale) > 0.05f) {
                                        webViewScale = liveScale
                                    }
                                    val positions = anchorPositions.value
                                    if (positions.isNotEmpty()) {
                                        val vmState = viewModel.state.value
                                        val activeMht = vmState.activeEntry?.mhtResourceId
                                        if (activeMht != null) {
                                            // wv.scrollY (Chromium WebView) is in
                                            // CSS-px × scale — NOT density × CSS × scale.
                                            // Empirically verified 06-07 18:44 logs:
                                            // scrollY=22798 at scale=3.0 ⇒ JS
                                            // window.scrollY=7599.33 ⇒ ratio=3.0 exactly.
                                            // No density factor. The old `/ (density * scale)`
                                            // over-divided by density (~3.5), pinning the
                                            // picker to whichever chapter sat in the
                                            // accidentally-correct range — at scale 3.0 the
                                            // picker glued to the previous chapter, making
                                            // every Next tap loop back to the same target.
                                            val scrollYCss = (scrollY / liveScale).toInt()
                                            // Three-priority picker (resolves the Metroid
                                            // 'stuck' bug):
                                            //
                                            //   1. ACTIVE ENTRY in landing window — if the
                                            //      user last navigated to entry X and its
                                            //      heading sits within 80 CSS px of
                                            //      scrollY (either side), trust the tap.
                                            //      This covers the post-jump landing
                                            //      (heading 80 px below top) without
                                            //      letting a slightly earlier chapter
                                            //      "above" steal the pick.
                                            //   2. LATEST ABOVE — chapter whose heading
                                            //      is at or above scrollY, largest Y.
                                            //      Standard mid-chapter reading.
                                            //   3. FIRST OF MHT — user is above the first
                                            //      chapter heading (banner / breadcrumbs).
                                            //
                                            // The earlier 'largest Y <= scrollY + slop'
                                            // rule over-picked the next chapter when two
                                            // headings were within slop of each other.
                                            val activeId = vmState.activeEntry?.id
                                            val activeY = positions[activeId]
                                            // Trust the active entry while its heading is in
                                                // the visible area:
                                                //   (a) Landing-window above: within 120 CSS-px
                                                //       — covers the post-Next scroll-margin-top
                                                //       80-px gap with safety margin.
                                                //   (b) Anywhere in the visible viewport — covers
                                                //       end-of-document chapters at zoom-out
                                                //       where the page can't scroll far enough
                                                //       to put the heading at the top, so it
                                                //       lands lower in the viewport.
                                                // Both decay automatically as soon as the user
                                                // scrolls the heading off-screen, at which point
                                                // the fallback aboveId/firstId rule takes over.
                                            val viewportHeightCss = (wv.height / liveScale)
                                                .toInt()
                                                .coerceAtLeast(1)
                                            val viewportBottom = scrollYCss + viewportHeightCss
                                            val activeInLandingWindow = activeId != null &&
                                                activeY != null && (
                                                    kotlin.math.abs(activeY - scrollYCss) <= 120 ||
                                                        activeY in scrollYCss..viewportBottom
                                                )
                                            var aboveId: String? = null
                                            var aboveY = Int.MIN_VALUE
                                            var firstId: String? = null
                                            for (e in vmState.entries) {
                                                if (e.mhtResourceId != activeMht) continue
                                                val y = positions[e.id] ?: continue
                                                if (firstId == null) firstId = e.id
                                                if (y <= scrollYCss && y > aboveY) {
                                                    aboveY = y; aboveId = e.id
                                                }
                                            }
                                            val picked = when {
                                                activeInLandingWindow -> activeId
                                                else -> aboveId ?: firstId
                                            }
                                            if (picked != null && picked != scrollDisplayEntryId) {
                                                scrollDisplayEntryId = picked
                                            }
                                            // Carry the label across page boundaries so
                                            // a body-only continuation can fall back to
                                            // the last named chapter.
                                            val labelNow = picked?.let { id ->
                                                vmState.entries.firstOrNull { it.id == id }
                                                    ?.label
                                                    ?.takeIf { it.isNotBlank() }
                                            }
                                            if (labelNow != null) {
                                                lastDetectedChapterLabel = labelNow
                                            }
                                        }
                                    }
                                }
                            )
                            // Long-press: intercept via HitTestResult and
                            // surface our own action dialog.
                            //
                            // SRC_IMAGE_ANCHOR_TYPE is host-discriminated
                            // via [isExternalAnchor]:
                            //   - same host  → image dialog (MediaWiki /
                            //                  Bulbapedia File: thumbs,
                            //                  OmegaMetroid sidebar tiles)
                            //   - different  → no dialog (PayPal donate
                            //                  buttons after the GameFAQs
                            //                  tipjar rewrite, sponsored
                            //                  banners). The page-group
                            //                  viewer has no link-long-press
                            //                  menu by design — the inline
                            //                  link badge already signals
                            //                  tap-to-navigate. Returning
                            //                  true consumes the long-press
                            //                  so the wrong image dialog
                            //                  doesn't surface.
                            // Bare IMAGE_TYPE always goes to the image dialog.
                            setOnLongClickListener { v ->
                                val webView = v as? android.webkit.WebView ?: return@setOnLongClickListener false
                                val hit = webView.hitTestResult
                                val type = hit?.type ?: android.webkit.WebView.HitTestResult.UNKNOWN_TYPE
                                when (type) {
                                    android.webkit.WebView.HitTestResult.IMAGE_TYPE -> {
                                        val handler = android.os.Handler(android.os.Looper.getMainLooper()) { msg ->
                                            val imageUrl = msg.data.getString("url")
                                            if (!imageUrl.isNullOrBlank()) {
                                                pendingImageLongPressUrl = imageUrl
                                            }
                                            true
                                        }
                                        webView.requestImageRef(handler.obtainMessage())
                                        true
                                    }
                                    android.webkit.WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> {
                                        val anchorHref = hit?.extra
                                        val activeHref = viewModel.state.value.activeEntry?.savedHref
                                        if (isExternalAnchor(activeHref, anchorHref)) {
                                            // External anchor — suppress the
                                            // wrong image dialog. Tap still
                                            // navigates via the WebView's
                                            // normal click handling.
                                            true
                                        } else {
                                            val handler = android.os.Handler(android.os.Looper.getMainLooper()) { msg ->
                                                val imageUrl = msg.data.getString("url")
                                                if (!imageUrl.isNullOrBlank()) {
                                                    pendingImageLongPressUrl = imageUrl
                                                }
                                                true
                                            }
                                            webView.requestImageRef(handler.obtainMessage())
                                            true
                                        }
                                    }
                                    android.webkit.WebView.HitTestResult.SRC_ANCHOR_TYPE -> {
                                        // Copy the held link's TEXT (URL as
                                        // fallback) — this viewer has no link
                                        // dialog, so the copy gesture applies
                                        // to every anchor, local or external.
                                        // (requestFocusNodeHref resolves the
                                        // text; HitTestResult only carries
                                        // the href.)
                                        val linkUrl = hit?.extra
                                        if (!linkUrl.isNullOrBlank()) {
                                            copyAnchorTextToClipboard(webView)
                                            true
                                        } else {
                                            false
                                        }
                                    }
                                    else -> false
                                }
                            }
                            isLongClickable = true

                            // WebChromeClient with onShowCustomView / onHideCustomView so the
                            // HTML5 fullscreen button on embedded video players (YouTube etc.)
                            // actually opens fullscreen. Shared helper mirrors the pattern from
                            // GuideBrowserWebViewContent (Dialog + Theme_Black_NoTitleBar_Fullscreen;
                            // dismiss restores the inline iframe and underlying WebView scroll Y).
                            webChromeClient = buildOfflineWebChromeClient(ctx, webViewRef)
                            // NOTE: setCustomSelectionMenuActions is applied in the `update`
                            // lambda below so `showSaveMarker` (which depends on the live
                            // `state.activeEntry`) re-evaluates on every recomposition. If we
                            // set it here in `factory` it captures `state.activeEntry` at first
                            // composition — which is null before Room emits — so Save Marker
                            // would never show up.
                            val mhtInterceptor = buildMhtAssetInterceptor(loadedAssetManifestState, loadedAssetBaseDirState)
                            webViewClient = object : android.webkit.WebViewClient() {
                                override fun shouldInterceptRequest(
                                    view: android.webkit.WebView?,
                                    request: android.webkit.WebResourceRequest?
                                ) = request?.url?.toString()?.let(mhtInterceptor)
                                    ?: super.shouldInterceptRequest(view, request)

                                /**
                                 * N1: Intercept cross-page link clicks. If the requested URL matches
                                 * the `savedHref` of another entry in this group (fragment-insensitive),
                                 * switch to that entry instead of letting the WebView attempt a normal
                                 * network load. State is read live from `viewModel.state.value.entries`
                                 * — the VM is captured in the factory closure and is stable for the
                                 * WebView's lifetime; the entries list it exposes is whatever Room has
                                 * most recently emitted.
                                 */
                                override fun shouldOverrideUrlLoading(
                                    view: android.webkit.WebView?,
                                    request: android.webkit.WebResourceRequest?
                                ): Boolean {
                                    val url = request?.url ?: return false
                                    val urlStr = url.toString()
                                    val scheme = url.scheme?.lowercase()
                                    val ctx = view?.context

                                    // Schemes the WebView handles natively — let them fall through
                                    // (we only intercept the http/https main-frame case below).
                                    // cid: is MHTML's own internal reference: a saved
                                    // page's frames and parts address each other with
                                    // it, and the WebView resolves them itself. It is
                                    // not a link anybody tapped, and handing it to
                                    // ACTION_VIEW finds no handler.
                                    val webViewNative = scheme == "http" || scheme == "https" ||
                                        scheme == "file" || scheme == "data" ||
                                        scheme == "about" || scheme == "javascript" ||
                                        scheme == "blob" || scheme == "cid"

                                    // A window with no activity behind it has nowhere to send
                                    // these: startActivity would drop a player out of a running
                                    // game to open YouTube. The in-group branches further down
                                    // are untouched, so chapter and anchor links still resolve.
                                    if (!capabilities.externalNavigation &&
                                        request.isForMainFrame &&
                                        (scheme == "intent" || !webViewNative)
                                    ) {
                                        onExternalLink(urlStr)
                                        return true
                                    }

                                    // intent:// — typical for YouTube / app deep links. Parse and
                                    // try to launch the resolved Intent; silently swallow if no
                                    // app handles it. Consuming the load prevents the WebView's
                                    // "unknown url scheme" error page.
                                    if (scheme == "intent" && ctx != null) {
                                        runCatching {
                                            val intent = android.content.Intent.parseUri(
                                                urlStr,
                                                android.content.Intent.URI_INTENT_SCHEME
                                            )
                                            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                            intent.component = null
                                            intent.selector = null
                                            ctx.startActivity(intent)
                                        }
                                        return true
                                    }
                                    // Any other custom scheme (youtube://, vnd.youtube://, mailto:,
                                    // tel:, etc.) — hand off to the OS via ACTION_VIEW. Consume the
                                    // load either way so the WebView doesn't render its error page.
                                    if (!webViewNative && ctx != null) {
                                        runCatching {
                                            ctx.startActivity(
                                                android.content.Intent(
                                                    android.content.Intent.ACTION_VIEW,
                                                    url
                                                ).apply { addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK) }
                                            )
                                        }
                                        return true
                                    }

                                    // http / https beyond this point. Only intercept main-frame
                                    // nav — iframe loads (Twitter widgets, etc.) fall through.
                                    if (!request.isForMainFrame) return false

                                    // Known external-video hosts: launch the platform app/web
                                    // instead of trying to render under our
                                    // https://localhost.invalid base — YouTube/Twitch players
                                    // can't reliably initialize without a real https origin.
                                    val host = url.host?.lowercase().orEmpty()
                                    val externalHost = host.endsWith("youtube.com") ||
                                        host == "youtu.be" || host.endsWith("twitch.tv") ||
                                        host.endsWith("vimeo.com")
                                    if (externalHost && !capabilities.externalNavigation) {
                                        onExternalLink(urlStr)
                                        return true
                                    }
                                    if (externalHost && ctx != null) {
                                        runCatching {
                                            ctx.startActivity(
                                                android.content.Intent(
                                                    android.content.Intent.ACTION_VIEW,
                                                    url
                                                ).apply { addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK) }
                                            )
                                        }
                                        return true
                                    }

                                    val target = urlStr.substringBefore('#')
                                    val fragment = url.fragment.orEmpty()
                                    // entries includes synthetic body-only-discovered pages
                                    // (filtered from Contents/picker, but kept here so
                                    // inline body links to ?page=N URLs land on the
                                    // local MHT instead of falling through to the
                                    // in-app browser).
                                    val entries = viewModel.state.value.entries
                                    // Normalized matching, mirroring the JS decorator
                                    // (buildLocalLinkMarkerJs/gtSplit): relative hrefs
                                    // resolve against the localhost.invalid base, so
                                    // they must be matched by PATH against the saved
                                    // entries; fully-qualified hrefs are compared with
                                    // host lowercased, www./trailing-slash stripped.
                                    val tapped = com.twinlore.app.ui.screens.offline
                                        .splitNormalizedHttpUrl(urlStr)
                                    val match = entries.firstOrNull { entry ->
                                        val href = entry.savedHref
                                        if (href.isBlank()) return@firstOrNull false
                                        if (href == urlStr || href.substringBefore('#') == target) {
                                            return@firstOrNull true
                                        }
                                        val saved = com.twinlore.app.ui.screens.offline
                                            .splitNormalizedHttpUrl(href)
                                            ?: return@firstOrNull false
                                        tapped != null && if (tapped.first == "localhost.invalid") {
                                            // Empty path = bare "#frag" or "/" — too
                                            // ambiguous to claim as a cross-entry match
                                            // (the fragment branch below handles it).
                                            tapped.second.isNotEmpty() &&
                                                saved.second == tapped.second
                                        } else {
                                            saved == tapped
                                        }
                                    }
                                    // Same-page fragment link: bare "#frag" resolved
                                    // against our localhost base with NO cross-entry
                                    // match, OR the matched entry IS the currently
                                    // loaded MHT. Either way the right thing is to
                                    // scroll to the fragment in the loaded page — no
                                    // navigation. (A localhost-resolved RELATIVE link
                                    // to ANOTHER saved page with a fragment must NOT
                                    // land here — it has a non-active match and falls
                                    // through to the entry switch below.)
                                    val activeMht = viewModel.state.value.activeEntry?.mhtResourceId
                                    val isSamePageAnchor = fragment.isNotEmpty() && (
                                        (host == "localhost.invalid" && match == null) ||
                                        (match != null && match.mhtResourceId != null &&
                                            match.mhtResourceId == activeMht)
                                    )
                                    if (isSamePageAnchor && view != null) {
                                        val escaped = fragment.replace("\\", "\\\\").replace("'", "\\'")
                                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                                            pushHistoryBeforeJump()
                                            view.evaluateJavascript(
                                                """
                                                (function(){
                                                    var frag = '$escaped';
                                                    try { frag = decodeURIComponent(frag); } catch (e) { /* keep raw */ }
                                                    var el = document.getElementById(frag) ||
                                                             document.getElementById('user-content-' + frag.toLowerCase()) ||
                                                             document.querySelector('[name="' + frag.replace(/"/g, '\\"') + '"]');
                                                    if (el) {
                                                        var rect = el.getBoundingClientRect();
                                                        var y = Math.round(rect.top + (window.pageYOffset || 0)) - 80;
                                                        window.scrollTo(0, Math.max(0, y));
                                                    } else {
                                                        location.hash = '#' + frag;
                                                        window.scrollBy(0, -80);
                                                    }
                                                })();
                                                """.trimIndent(),
                                                null
                                            )
                                        }
                                        return true
                                    }
                                    if (match != null) {
                                        // Carry the link's #fragment so the
                                        // destination lands at the anchored
                                        // section, not the saved resume scroll.
                                        // (Same-MHT anchors were handled above
                                        // by isSamePageAnchor; this is always a
                                        // cross-document load.)
                                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                                            pushHistoryBeforeJump()
                                            viewModel.onEntryTapped(match.id, fragment)
                                        }
                                        return true
                                    }
                                    // No in-group match — route the click through the vault
                                    // session's Browser tab so the user stays inside the app
                                    // instead of having the WebView attempt a foreign
                                    // navigation (which silently fails under our
                                    // https://localhost.invalid base URL).
                                    if (!capabilities.externalNavigation) {
                                        // No Browser tab to route to from here, and a
                                        // null mainViewModel would make this a tap that
                                        // does nothing at all.
                                        onExternalLink(urlStr)
                                        return true
                                    }
                                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                                        mainViewModel?.setPendingUrlForBrowserTab(vaultId, urlStr)
                                        mainViewModel?.setVaultSessionTab(
                                            vaultId,
                                            com.twinlore.app.domain.VaultTab.Browser
                                        )
                                    }
                                    return true
                                }

                                override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    // Page rendered — drop the page-load progress bar. No-op
                                    // when the bar isn't showing (e.g. theme-change reloads).
                                    pageLoading = false
                                    // Does this chunk have runs / scroll-blocks for Smart
                                    // Formatting to act on? Group pages can come from a text
                                    // split or from a bulk web save.
                                    view?.evaluateJavascript(
                                        com.twinlore.app.ui.screens.offline.DETECT_TEXT_GUIDE_JS
                                    ) { result -> isTextGuide = result == "true" }
                                    // Font-size zoom lives on the document, so
                                    // every freshly loaded chunk needs it re-applied
                                    // (settings.textZoom used to persist for free).
                                    // The load-time prelude already set it, so on
                                    // a fresh chunk this writes the same value.
                                    view?.evaluateJavascript(
                                        com.twinlore.app.ui.screens.offline
                                            .buildTextZoomJs(currentTextZoomFraction),
                                        null
                                    )
                                    // Reveal the chunk now that this pass has
                                    // reflowed it too. See READER_CLOAK_ID.
                                    view?.evaluateJavascript(
                                        com.twinlore.app.ui.screens.offline.READER_UNCLOAK_JS,
                                        null
                                    )
                                    // Install (or refresh) the <img>-click handler that bridges
                                    // through to the Kotlin-side zoom viewer.
                                    view?.evaluateJavascript(
                                        """
                                        if (window._pageGroupImgTap) {
                                            document.removeEventListener('click', window._pageGroupImgTap, true);
                                        }
                                        window._pageGroupImgTap = function(e) {
                                            var t = e.target;
                                            if (!t) return;
                                            // Image-only-wrapper detection — same shape as
                                            // SingleGuideViewerScreen. Catches MediaWiki
                                            // `<a><img/></a>` sprite links + thumbnail
                                            // figures without misfiring on text taps that
                                            // happen to have an <img> elsewhere in their
                                            // ancestor tree.
                                            var img = null;
                                            if (t.tagName === 'IMG') {
                                                img = t;
                                            } else {
                                                var node = t;
                                                for (var i = 0; i < 3 && node; i++) {
                                                    if (node.querySelectorAll) {
                                                        var imgs = node.querySelectorAll('img');
                                                        if (imgs.length === 1) {
                                                            var text = (node.textContent || '').replace(/\s| /g, '');
                                                            if (text.length === 0) {
                                                                img = imgs[0];
                                                                break;
                                                            }
                                                        }
                                                    }
                                                    node = node.parentElement;
                                                }
                                            }
                                            if (img && img.src) {
                                                e.preventDefault();
                                                e.stopPropagation();
                                                if (window.androidInterface && window.androidInterface.onGuideImageTap) {
                                                    window.androidInterface.onGuideImageTap(img.src);
                                                }
                                            }
                                        };
                                        document.addEventListener('click', window._pageGroupImgTap, true);
                                        """.trimIndent(),
                                        null
                                    )
                                    // History restore wins over marker/fragment/ratio —
                                    // we want the user's previous scroll position
                                    // exactly. Apply once the page has had a chance to
                                    // settle (the same delay the ratio-restore tick uses).
                                    val histY = pendingHistoryScrollY
                                    if (histY != null && view != null) {
                                        pendingHistoryScrollY = null
                                        viewModel.consumePendingScrollToMarker()
                                        pendingFragment.value = null
                                        pendingScrollRatio.value = null
                                        // histY is CSS-px; view.scrollTo wants
                                        // device-px (CSS × scale at the time of
                                        // restore, which is the new MHT's current
                                        // scale just after the cross-file load).
                                        val scale = view.scale.coerceAtLeast(0.01f)
                                        view.postDelayed({
                                            view.scrollTo(0, (histY * scale).toInt())
                                        }, 250L)
                                        return
                                    }
                                    // Marker tap fired across entries: search the
                                    // DOM for the saved label and center it once the new
                                    // page finishes loading. Saved-text search wins over
                                    // saved scroll ratio (text-based is robust against
                                    // MHT-render reflows; ratios are approximate) so we
                                    // consume it first and skip the fragment/ratio
                                    // branches entirely when it's set to avoid the
                                    // double-scroll flicker. G1: no flash here — the
                                    // F3 marker span highlight is already visible, so
                                    // adding a viewport flash would double-paint.
                                    val pendingMarker = viewModel.consumePendingScrollToMarker()
                                    if (pendingMarker != null && view != null) {
                                        scrollToMarkerByText(
                                            webView = view,
                                            label = pendingMarker.label,
                                            fallbackScrollY = pendingMarker.fallbackScrollY,
                                            occurrenceIndex = pendingMarker.occurrenceIndex,
                                            flashFocusHex = pendingMarker.flashHex
                                        ) { /* resolved Y not needed here */ }
                                        // Prevent the entry's own fragment / ratio from firing.
                                        pendingFragment.value = null
                                        pendingScrollRatio.value = null
                                    } else {
                                        val frag = pendingFragment.value
                                        val ratio = pendingScrollRatio.value
                                        if (!frag.isNullOrEmpty()) {
                                            view?.evaluateJavascript(
                                                """
                                                (function(){
                                                    var frag = '${frag.replace("\\", "\\\\").replace("'", "\\'")}';
                                                    if (frag.charAt(0) === '#') frag = frag.substring(1);
                                                    try { frag = decodeURIComponent(frag); } catch (e) { /* keep raw */ }
                                                    var el = document.getElementById(frag) ||
                                                             document.getElementById('user-content-' + frag.toLowerCase()) ||
                                                             document.querySelector('[name="' + frag.replace(/"/g, '\\"') + '"]');
                                                    if (el) {
                                                        var rect = el.getBoundingClientRect();
                                                        var y = Math.round(rect.top + (window.pageYOffset || 0)) - 80;
                                                        window.scrollTo(0, Math.max(0, y));
                                                    } else {
                                                        location.hash = '#' + frag;
                                                        window.scrollBy(0, -80);
                                                    }
                                                })();
                                                """.trimIndent(),
                                                null
                                            )
                                            pendingFragment.value = null
                                            pendingScrollRatio.value = null
                                        } else if (ratio != null && ratio > 0.0) {
                                            // Lifted from GuideBrowserWebViewContent's
                                            // doRatioFallback (line ~1644) — the proven
                                            // single-page guide restore pattern.
                                            //
                                            //   - 250ms postDelayed gives readyState a chance
                                            //     to advance past 'loading' before we attach.
                                            //   - __gtScrollRestoreDone flag blocks any double-
                                            //     entry (e.g. if onPageFinished re-fires).
                                            //   - setScroll verifies window.pageYOffset after
                                            //     scrollTo and falls back to
                                            //     documentElement.scrollTop / body.scrollTop
                                            //     when scrollTo was ignored (happens on some
                                            //     MHT-rendered pages).
                                            //   - Tick poller re-applies ONLY when scrollHeight
                                            //     changes; exits after 5 stable samples (500ms)
                                            //     or 3s ceiling.
                                            view?.postDelayed({
                                                view.evaluateJavascript(
                                                    """
                                                    (function() {
                                                        if (window.__gtScrollRestoreDone) return;
                                                        function doRestore() {
                                                            if (window.__gtScrollRestoreDone) return;
                                                            window.__gtScrollRestoreDone = true;
                                                            var ratio = $ratio;
                                                            function getH() {
                                                                return Math.max(document.body.scrollHeight || 0, document.documentElement.scrollHeight || 0);
                                                            }
                                                            function setScroll(y) {
                                                                window.scrollTo(0, y);
                                                                var actualY = window.pageYOffset || document.documentElement.scrollTop || document.body.scrollTop || 0;
                                                                if (Math.abs(actualY - y) > 10) {
                                                                    document.documentElement.scrollTop = y;
                                                                    document.body.scrollTop = y;
                                                                }
                                                            }
                                                            var lastH = -1;
                                                            var stableTicks = 0;
                                                            var startTime = Date.now();
                                                            function tick() {
                                                                var h = getH();
                                                                if (h > 0 && h !== lastH) {
                                                                    lastH = h;
                                                                    stableTicks = 0;
                                                                    setScroll(Math.round(h * ratio));
                                                                } else if (h === lastH) {
                                                                    stableTicks++;
                                                                }
                                                                var elapsed = Date.now() - startTime;
                                                                if (stableTicks >= 5 || elapsed >= 3000) return;
                                                                setTimeout(tick, 100);
                                                            }
                                                            tick();
                                                        }
                                                        if (document.readyState === 'loading') {
                                                            window.__gtScrollRestoreFunc = doRestore;
                                                            document.addEventListener('DOMContentLoaded', doRestore, { once: true });
                                                        } else {
                                                            doRestore();
                                                        }
                                                    })();
                                                    """.trimIndent(),
                                                    null
                                                )
                                            }, 250L)
                                            pendingScrollRatio.value = null
                                            pendingFragment.value = null
                                        } else if (view != null) {
                                            // Nothing pending here is the COMMON case, not an
                                            // empty one: the entry's target was consumed by the
                                            // JS spliced into the HTML before the load, and
                                            // that JS is still holding the landing. Everything
                                            // this callback has just issued moves the document
                                            // under it — the text-size font-size alone costs a
                                            // converted guide about a sixth of its height, and
                                            // the refit it triggers moves it again — so tell it
                                            // to re-measure. Without this the reader comes back
                                            // `ratio × heightLost` too far down, worse the
                                            // deeper they were.
                                            //
                                            // A page with no landing spliced in has no hook to
                                            // call, so this is a no-op there and the page stays
                                            // at scrollY=0: reading-mode chrome strip means the
                                            // visible content starts at the top, so the old
                                            // 400-CSS-px banner-skip fallback is not needed.
                                            view.evaluateJavascript(SETTLE_SCROLL_REARM_JS, null)
                                        }
                                    }
                                    // Scroll target is applied inline by the JS spliced
                                    // into the HTML at load time, so no post-load reveal
                                    // is needed for the common paths. The pendingMarker
                                    // text-search branch (rare) is the only post-load
                                    // scroller; it accepts a brief visible snap.
                                    // Re-issue on-page highlight for a find-sheet pick after
                                    // the new page has finished loading (cross-file transition).
                                    // Defensive delay matches GuideScreen's restore-then-find pattern:
                                    // loadDataWithBaseURL is synchronous so the DOM is up at
                                    // onPageFinished, but giving the render pipeline a beat before
                                    // findAllAsync avoids races on content-heavy pages.
                                    val fq = pendingFindQuery.value
                                    if (fq != null) {
                                        view?.postDelayed({ view.findAllAsync(fq) }, 100)
                                        pendingFindQuery.value = null
                                    }
                                    // Selection rect: feed SelectionRectBridge so the custom
                                    // action-mode menu floats next to the selected text rather
                                    // than defaulting to the bottom of the WebView.
                                    if (view != null && url != null) {
                                        view.evaluateJavascript(SELECTION_RECT_JS, null)
                                    }
                                    // N1: decorate every <a href> — accent-colored link text for
                                    // anchors that point at another entry in this group, accent +
                                    // open_in_new badge for external links. Idempotent via
                                    // data-pg-local-marked / data-pg-external-marked so
                                    // re-injection on later onPageFinished calls (theme changes,
                                    // etc.) doesn't accumulate decorations.
                                    if (view != null) {
                                        val savedHrefs = viewModel.state.value.entries
                                            .map { it.savedHref }
                                            .filter { it.isNotBlank() }
                                            .distinct()
                                        if (savedHrefs.isNotEmpty()) {
                                            view.evaluateJavascript(
                                                buildLocalLinkMarkerJs(savedHrefs, currentHighlightHex),
                                                null
                                            )
                                        }
                                    }
                                    // N2: iframe gating. Re-check network state on every page-finish
                                    // so a user who toggles connectivity mid-session sees fresh
                                    // iframes (or placeholders) on the next entry navigation.
                                    if (view != null) {
                                        val online = networkConnectivityManager.isNetworkAvailable()
                                        view.evaluateJavascript(buildIframeGateJs(online), null)
                                    }
                                    // Cosmetic element-hide layer — collapses leftover ad-iframe
                                    // / ad-slot placeholders. Same gate as the live browser; needed
                                    // here on every cross-file load (loadDataWithBaseURL resets the
                                    // DOM and the injected style is gone).
                                    if (view != null && privacyPreferences.enabled) {
                                        view.evaluateJavascript(
                                            com.twinlore.app.data.privacy.CosmeticFilters.injectionJs(),
                                            null
                                        )
                                    }
                                    // Theme palette CSS: re-inject after every cross-file load
                                    // (loadDataWithBaseURL wipes the DOM, so the style element
                                    // injected by the LaunchedEffect is gone). Mirrors the
                                    // onPageFinished injection in GuideBrowserWebViewContent.
                                    //
                                    // Reading-mode chrome strip rides alongside on the same
                                    // toggle. The prelude splice in the load LaunchedEffect
                                    // already did this work during DOM parse when toggle was
                                    // ON at load — but this re-injection covers the
                                    // toggle-flipped-ON-mid-view case for newly-loaded entries.
                                    // Per-entry filter from activeEntry's savedHref.
                                    if (view != null) {
                                        if (currentGuideThemeInject) {
                                            view.evaluateJavascript(
                                                buildPaletteCssJs(currentPaletteBgHex, currentPaletteFgHex, currentAppFont),
                                                null
                                            )
                                            val activeHref = viewModel.state.value.activeEntry?.savedHref
                                            filterForHost(activeHref)?.let {
                                                view.evaluateJavascript(buildReadingModeJs(it), null)
                                            }
                                        } else {
                                            view.evaluateJavascript(REMOVE_PALETTE_CSS_JS, null)
                                            view.evaluateJavascript(REMOVE_READING_MODE_JS, null)
                                        }
                                    }
                                    // Scroll-marker highlights: inject after theme CSS so the
                                    // highlight style (with !important) beats the palette CSS.
                                    // Filter to the active entry only — markers from other entries
                                    // in the same group must not appear in this page's DOM.
                                    val activeId = viewModel.state.value.activeEntry?.id
                                    highlightScrollMarkerInPage(
                                        webView = view,
                                        markers = activeEntryMarkersDomain(activeId),
                                        focusedIndex = null,
                                        focusColorHex = currentHighlightHex,
                                        focusContrastHex = currentContrastHex
                                    )
                                    // Scroll-driven chapter title: walk the loaded
                                    // MHT's anchor siblings and post each fragment's
                                    // DOM Y back via PgAnchorBridge → anchorPositions.
                                    // Posted at the END of onPageFinished so it runs
                                    // after the ratio-restore polling JS is queued
                                    // (the polling runs on its own setTimeout chain,
                                    // so they don't fight). 400ms delay gives the
                                    // ratio-restore poller time to land the DOM in
                                    // a stable shape for getBoundingClientRect.
                                    // The same helper is re-invoked by the textZoom
                                    // LaunchedEffect when the user changes the
                                    // font-size slider, since textZoom reflow
                                    // invalidates these Y positions.
                                    if (view != null) {
                                        scheduleAnchorPositionScan(view, 400L)
                                    }
                                }

                                // Track the WebView's pinch-zoom scale so the
                                // scroll listener can convert WebView.scrollY
                                // (device px, scaled) back to CSS px. The
                                // scroll listener also reads view.scale live
                                // because this callback is unreliable on
                                // some WebView builds.
                                override fun onScaleChanged(
                                    view: android.webkit.WebView?,
                                    oldScale: Float,
                                    newScale: Float
                                ) {
                                    super.onScaleChanged(view, oldScale, newScale)
                                    if (newScale > 0f) webViewScale = newScale
                                }
                            }
                            // Counts for the this-page find bar. A cross-page pick's
                            // findAllAsync reports here too, which is harmless: the
                            // bar re-searches whenever it opens.
                            setFindListener { activeMatch, numMatches, isDoneCounting ->
                                if (isDoneCounting) currentOnPageFindResult(activeMatch, numMatches)
                            }
                            webViewRef.value = this
                        }
                    },
                    update = { wv ->
                        // Defensive: re-apply the WebView native bg colour on every
                        // recomposition so theme swaps always reach the WebView,
                        // even if the palette LaunchedEffect doesn't re-fire for
                        // some reason. Idempotent; same value across most
                        // recompositions.
                        wv.setBackgroundColor(palette.background.toArgb())
                        // Re-apply on every recomposition so `showSaveMarker` tracks the
                        // live `state.activeEntry` (factory captures only first-composition
                        // values; entries arrive from Room after the WebView is built).
                        // Lambdas close over the current `state`, `coroutineScope`, ctx, and
                        // viewModel since `update` runs on every recomposition.
                        val ctx = wv.context
                        // A hold is the WebView's own: it selects, with handles
                        // to adjust, and opens the custom menu below. That holds
                        // in the fold panel too, measured on the Thor (see
                        // GuideViewerCapabilities); a long-press handler there
                        // once took the hold first and so blocked selection.
                        wv.setOnLongClickListener(null)
                        if (!capabilities.subWindows) {
                            (wv as? GuideWebView)?.onSelectionModeChanged = { panelSelecting = it }
                        }
                        wv.isLongClickable = capabilities.textSelection
                        wv.setCustomSelectionMenuActions(
                            if (!capabilities.textSelection) null else
                            GuideWebView.CustomSelectionActions(
                                showSaveMarker = state.activeEntry?.mhtResourceId != null,
                                onSaveMarker = { text, occurrenceIndex ->
                                    wv.evaluateJavascript(
                                        // Math.round: window.pageYOffset is fractional on hi-DPI;
                                        // toIntOrNull would otherwise return null and the marker
                                        // would save at y=0.
                                        "(function(){return Math.round(window.pageYOffset||0);})()"
                                    ) { result ->
                                        val y = result?.toIntOrNull() ?: 0
                                        coroutineScope.launch {
                                            viewModel.addMarker(text, y, occurrenceIndex)
                                            Toast.makeText(
                                                ctx,
                                                "Scroll marker saved: " + text.take(30) +
                                                    if (text.length > 30) "…" else "",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                },
                                onFindInPage = { text ->
                                    wv.evaluateJavascript("window.getSelection().removeAllRanges()", null)
                                    findQuery = text
                                    showFind = true
                                },
                                onYouTube = { text ->
                                    wv.evaluateJavascript("window.getSelection().removeAllRanges()", null)
                                    if (text.isNotBlank()) onYouTubeSearch(text)
                                },
                                onSearchOnline = { text ->
                                    wv.evaluateJavascript("window.getSelection().removeAllRanges()", null)
                                    if (text.isNotBlank()) onWebSearch(text)
                                },
                                copyAndMarkerOnly = !capabilities.subWindows,
                                onCopy = { text ->
                                    val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    clipboard?.setPrimaryClip(ClipData.newPlainText("selection", text))
                                    wv.evaluateJavascript("window.getSelection().removeAllRanges()", null)
                                    // A toast from the panel lands on the game's
                                    // display; the pill shows on the panel itself.
                                    if (capabilities.subWindows) {
                                        Toast.makeText(ctx, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                                    } else {
                                        copiedPillTrigger++
                                    }
                                }
                            )
                        )
                    }
                )
            }
        }

        // Top bar floats above the body. Conditional render keeps it out of the
        // layout when chrome is hidden so the body fills the whole viewport
        // (no reserved system-bar inset). The bar is sized by its own content;
        // the body underneath isn't padded — it scrolls under the bar like
        // iOS Safari does. Suppressed while the find / ToC overlays are open
        // (their own headers replace it). The floating end-of-page Prev/Next
        // bar at BottomCenter is INDEPENDENT — it doesn't gate this top bar
        // either way; chrome visibility is purely the user's tap-and-scroll
        // gesture state.
        if (chromeVisible && !showFind && !showTocView) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .onSizeChanged { topBarHeightPx = it.height }
            ) {
                // If the current displayEntry's label is blank (typical for a
                // body-only continuation sub-page), prefer the last detected
                // chapter label so the user keeps reading inside the same
                // logical chapter without the title resetting to "Untitled".
                val effectiveLabel = displayEntry?.label?.takeIf { it.isNotBlank() }
                    ?: lastDetectedChapterLabel
                val displayEntryWithLabel = displayEntry?.let { e ->
                    if (e.label.isNotBlank() || effectiveLabel == null) e
                    else e.copy(label = effectiveLabel)
                }
                PageGroupTopBar(
                    title = state.title,
                    // Scroll-driven: reflects the chapter the user is
                    // currently inside (anchor sub-entries within one MHT)
                    // rather than just the entry they explicitly opened.
                    activeEntry = displayEntryWithLabel ?: displayEntry,
                    activeIndex = displayIndex,
                    totalEntries = state.entries.size,
                    onBack = onBack,
                    onPrev = {
                        suppressChromeHideUntil = System.currentTimeMillis() + 1000L
                        skipScrollRestoreOnce = true
                        // Pivot on the displayed chapter (= what's actually on
                        // screen), not the last-tapped activeEntryId. Linear
                        // chapter walk is NOT pushed to history (history is
                        // for jumps: ToC, anchor, marker, find).
                        viewModel.goPrev(fromEntryId = displayEntry?.id)
                    },
                    onNext = {
                        suppressChromeHideUntil = System.currentTimeMillis() + 1000L
                        skipScrollRestoreOnce = true
                        viewModel.goNext(fromEntryId = displayEntry?.id)
                    },
                    onTocClicked = { showTocView = true },
                    onFindClicked = { showFind = true },
                    onDisplayClicked = { showDisplaySheet = true },
                    showBack = showBack,
                    showDisplay = capabilities.subWindows,
                    firstButtonFocusRequester = contentFocusRequester,
                    bottomBarFocusRequester = bottomBarFocusRequester
                )
            }
        }

        // Browser-style horizontal page-load progress bar — same 3.dp strip,
        // focus color, and faint surfaceVariant track BrowserScreen pins
        // under its URL bar. Pinned just below the floating top bar's bottom
        // edge when the chrome is showing; at the very top edge of the
        // viewport otherwise, so an entry switch initiated with hidden
        // chrome (e.g. a tapped local link mid-page) still gets feedback.
        // Indeterminate: the bulk of the wait is Kotlin-side MHT extraction
        // + parse, which emits no progress events to drive a determinate bar.
        if (pageLoading) {
            val topBarPad = if (chromeVisible && !showFind && !showTocView) {
                with(androidx.compose.ui.platform.LocalDensity.current) { topBarHeightPx.toDp() }
            } else 0.dp
            androidx.compose.material3.LinearProgressIndicator(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .padding(top = topBarPad)
                    .height(3.dp),
                color = effectiveFocusColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            )
        }

        // K3: Clear-search banner. Surfaces while a find query has a live
        // findAllAsync overlay on the page so the user can dismiss the
        // yellow highlights without re-opening the find view. Anchored
        // BottomCenter inside this outer Box — sits just above the vault
        // session's bottom chrome (which lives outside this screen).
        // Hidden while the find / ToC overlays are open: each owns the
        // bottom strip exclusively so stacking would just be visual noise.
        if (!showFind && !showTocView) {
            activeSearchQuery?.let { q ->
                // Solid Card matching the VgmapsDownloadBanner: surface color,
                // 12dp rounded, 8dp elevation, 95% width, padded clear of the
                // vault session bottom chrome. Prev / Next buttons drive
                // WebView.findNext; Clear dismisses the overlay.
                androidx.compose.material3.Card(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(0.95f)
                        .padding(start = 12.dp, end = 12.dp, bottom = 80.dp),
                    colors = androidx.compose.material3.CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                    elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "\"$q\"",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        // findNext(forward) walks through the live findAllAsync
                        // match list. Direction matches Android's WebView API.
                        IconButton(onClick = {
                            FeedbackManager.play(view, FeedbackEvent.SWITCH)
                            webViewRef.value?.findNext(false)
                        }) {
                            Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Previous match")
                        }
                        IconButton(onClick = {
                            FeedbackManager.play(view, FeedbackEvent.SWITCH)
                            webViewRef.value?.findNext(true)
                        }) {
                            Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Next match")
                        }
                        TextButton(onClick = {
                            FeedbackManager.play(view, FeedbackEvent.BACK)
                            // clearMatches dismisses the native findAllAsync
                            // overlay regardless of focus state.
                            webViewRef.value?.clearMatches()
                            activeSearchQuery = null
                            // K2/K3: also clear the persisted find query so
                            // re-opening the find view starts fresh.
                            findQuery = ""
                        }) { Text("Clear") }
                    }
                }
            }
        }

        // Bottom history strip — auto-hides with the chrome (same rule as
        // the top bar). The find/ToC overlays own the bottom strip while
        // open, so suppress while either is visible.
        com.twinlore.app.ui.components.CopiedPill(
            trigger = copiedPillTrigger,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = chromeBelow + 56.dp),
        )
        if (!capabilities.subWindows) {
            markerSpanTapped?.let { marker ->
                com.twinlore.app.ui.components.InlineMarkerDeletePrompt(
                    label = marker.label,
                    onDelete = {
                        markerSpanTapped = null
                        coroutineScope.launch {
                            viewModel.deleteMarker(marker.id)
                            clearScrollMarkerHighlight(webViewRef.value)
                        }
                    },
                    onCancel = { markerSpanTapped = null },
                )
            }
        }
        if (chromeVisible && !showFind && !showTocView) {
            // Resolve the destination chapter labels for the strip.
            // "Untitled" is the fallback for body-only continuation entries
            // (no original label) so the user still sees something tappable.
            fun labelOf(id: String?): String? = id?.let { eid ->
                state.entries.firstOrNull { it.id == eid }?.label
                    ?.ifBlank { "Untitled" }
            }
            NavigationHistoryStrip(
                chromeBelow = chromeBelow,
                backLabel = labelOf(history.back.lastOrNull()?.entryId),
                forwardLabel = labelOf(history.forward.lastOrNull()?.entryId),
                accentColor = effectiveFocusColor,
                onBack = {
                    suppressChromeHideUntil = System.currentTimeMillis() + 1000L
                    navigateHistoryBack()
                },
                onForward = {
                    suppressChromeHideUntil = System.currentTimeMillis() + 1000L
                    navigateHistoryForward()
                },
                onClear = { history = PageGroupHistory() }
            )
        }

        // Right-edge scroll-position chip — outside the chrome gate so
        // it appears on every scroll, fades after 1.5s of inactivity.
        if (!showFind && !showTocView) {
            val wvRef = webViewRef.value
            val maxScroll = if (wvRef != null)
                (wvRef.verticalScrollRangePx() - wvRef.height).coerceAtLeast(0)
            else 0
            if (maxScroll > 0) {
                ScrollIndicatorChip(
                    scrollY = liveScrollY,
                    maxScroll = maxScroll,
                    onScrollTo = { y -> wvRef?.scrollTo(0, y) }
                )
            }
        }

        // Floating "Next page" button — appears within ~500dp of the body
        // end. Advances to the NEXT MHT page, not the next chapter (so
        // body-only continuations and anchor siblings on the current page
        // are skipped). Hidden when the find / ToC overlays are open OR
        // there's no next page to advance to. Stays available even when
        // chrome is hidden so reading flow isn't interrupted.
        val currentMht = state.activeEntry?.mhtResourceId
        val currentIdx = state.activeIndex
        val nextPageEntry = if (currentMht != null && currentIdx != null) {
            state.entries.drop(currentIdx + 1).firstOrNull {
                it.mhtResourceId != null && it.mhtResourceId != currentMht
            }
        } else null
        AnimatedVisibility(
            visible = nearEndOfPage && nextPageEntry != null && !showFind && !showTocView,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(
                    bottom = WindowInsets.navigationBars
                        .asPaddingValues()
                        .calculateBottomPadding() + 24.dp
                )
        ) {
            if (nextPageEntry != null) {
                NextPageFloatingButton(
                    label = nextPageEntry.label.ifBlank { "Next page" },
                    focusColor = effectiveFocusColor,
                    onClick = {
                        suppressChromeHideUntil = System.currentTimeMillis() + 1000L
                        skipScrollRestoreOnce = true
                        pushHistoryBeforeJump()
                        viewModel.onEntryTapped(nextPageEntry.id)
                    }
                )
            }
        }

    }

    if (showTocView) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            PageGroupTocViewScreen(
                entries = state.entries,
                // The panel tucks the reader 8dp under its bar; this gives
                // that back so the tabs start where the bar ends.
                topSpacing = if (capabilities == com.twinlore.app.ui.screens.guide.GuideViewerCapabilities.OVERLAY) 8.dp else 24.dp,
                // Highlight the chapter the user is currently SCROLLED INTO,
                // not the one they last opened from Contents. scrollDisplayEntryId
                // is updated by the scroll listener as the user crosses anchor
                // boundaries within an MHT; falls back to state.activeEntry
                // when no anchor positions have been resolved yet.
                activeEntryId = displayEntry?.id,
                markers = markers,
                focusColor = effectiveFocusColor,
                tabIndex = tocTabIndex,
                onTabIndexChange = { tocTabIndex = it },
                onClose = { showTocView = false },
                onEntryTapped = { id ->
                    showTocView = false
                    // Tapping a ToC entry is an explicit navigation, not a
                    // session resume. Skip the saved-scroll-ratio restore so
                    // we land at the chapter (or its fragment), not at
                    // whatever position the previous visit persisted.
                    skipScrollRestoreOnce = true
                    pushHistoryBeforeJump()
                    val activeId = state.activeEntry?.id
                    val tappedEntry = state.entries.firstOrNull { it.id == id }
                    val fragment = tappedEntry?.fragment?.takeIf { it.isNotBlank() }
                    val sameMht = tappedEntry?.mhtResourceId != null &&
                        tappedEntry.mhtResourceId == state.activeEntry?.mhtResourceId
                    if (id == activeId) {
                        // Re-tap on the currently active entry. activeEntryId
                        // doesn't change so the LaunchedEffect won't refire —
                        // we have to scroll here. Use the fragment if there
                        // is one, otherwise top of page. We deliberately do
                        // NOT use scrollToMarkerByText(label) because the
                        // label often matches inline-ToC link text earlier
                        // in the DOM than the actual chapter heading (eg
                        // GameFAQs pages). Text search would land the user
                        // on the inline ToC instead of the chapter.
                        val wv = webViewRef.value
                        if (wv != null) {
                            if (fragment != null) {
                                wv.evaluateJavascript(
                                    """
                                    (function(){
                                        var frag = '${fragment.replace("\\", "\\\\").replace("'", "\\'")}';
                                        if (frag.charAt(0) === '#') frag = frag.substring(1);
                                        try { frag = decodeURIComponent(frag); } catch (e) { /* keep raw */ }
                                        var el = document.getElementById(frag) ||
                                                 document.getElementById('user-content-' + frag.toLowerCase()) ||
                                                 document.querySelector('[name="' + frag.replace(/"/g, '\\"') + '"]');
                                        if (el) {
                                            var rect = el.getBoundingClientRect();
                                            var y = Math.round(rect.top + (window.pageYOffset || 0)) - 80;
                                            window.scrollTo(0, Math.max(0, y));
                                        } else {
                                            location.hash = '#' + frag;
                                            window.scrollBy(0, -80);
                                        }
                                    })();
                                    """.trimIndent(),
                                    null
                                )
                            } else {
                                wv.evaluateJavascript("window.scrollTo(0, 0);", null)
                            }
                        }
                    } else if (sameMht) {
                        // Different entry, same MHT: the LaunchedEffect on
                        // activeEntry?.id fires and the same-file fast path
                        // (line ~510) does the fragment scroll (or scrollTo
                        // 0 if no fragment). Nothing to do here.
                    } else if (fragment != null) {
                        // Cross-file with fragment: queue the fragment so
                        // onPageFinished scrolls to the anchor element.
                        // Reuse the marker pipe — scrollToMarkerByText
                        // doesn't know about fragments, so we fall through
                        // to the entry.fragment path the LaunchedEffect
                        // applies on cross-file loads.
                    }
                    // For cross-file without fragment, the LaunchedEffect
                    // loads the MHT and leaves the user at scrollY=0
                    // (top of the new page) — exactly what we want for a
                    // whole-page chapter like Zelda's "Introduction".
                    viewModel.onEntryTapped(id)
                },
                onEntryLongPressRemove = { id ->
                    showTocView = false
                    pendingRemoveEntryId = id
                },
                onMarkerTapped = { m ->
                    showTocView = false
                    pushHistoryBeforeJump()
                    val activeId = state.activeEntry?.id
                    if (m.pageGroupEntryId != null && m.pageGroupEntryId != activeId) {
                        // Cross-entry: enqueue label + saved Y; the DOM text walk
                        // fires in onPageFinished after the load completes. No
                        // flash — F3 highlight on the marker span is already visible.
                        viewModel.enqueueScrollToMarker(m.label, m.scrollPosition, m.occurrenceIndex)
                        viewModel.onEntryTapped(m.pageGroupEntryId)
                    } else {
                        // Same entry: search-and-center inline. No flash for the
                        // same reason as the cross-entry path.
                        scrollToMarkerByText(
                            webView = webViewRef.value,
                            label = m.label,
                            fallbackScrollY = m.scrollPosition,
                            occurrenceIndex = m.occurrenceIndex,
                            flashFocusHex = null
                        ) { /* resolved Y not needed here */ }
                    }
                },
                onMarkerLongPressDelete = { m ->
                    showTocView = false
                    pendingDeleteMarkerId = m.id
                }
            )
        }
    }

    if (capabilities.subWindows) {
    pendingDeleteMarkerId?.let { id ->
        AlertDialog(
            onDismissRequest = { pendingDeleteMarkerId = null },
            title = { Text("Delete marker?") },
            confirmButton = {
                TextButton(onClick = {
                    coroutineScope.launch {
                        viewModel.deleteMarker(id)
                        pendingDeleteMarkerId = null
                    }
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteMarkerId = null }) { Text("Cancel") }
            }
        )
    }
    }

    // F9: highlighted-span tap menu. Shared composable from GuideBrowserDialogs,
    // mirrors single-page's Delete-one / Delete-all UI. Positioning is the
    // DropdownMenu default (centered Dialog) — anchoring to the tapped span's
    // screen coords would require threading the rect through the JS bridge,
    // which we don't do yet.
    // In a window with no sub-windows the menu is a crash rather than a menu:
    // it is a Dialog, and it resolves a SettingsViewModel that needs an
    // activity. There a tap asks in the page instead, and deletes on a yes.
    // In the panel the tap asks in the page instead; see the prompt beside the Copied pill.
    ScrollMarkerContextMenu(
        visible = capabilities.subWindows && markerSpanTapped != null,
        markerForDelete = markerSpanTapped,
        onDismiss = { markerSpanTapped = null },
        onDeleteOne = { marker ->
            coroutineScope.launch {
                viewModel.deleteMarker(marker.id)
                // Strip the active-entry highlight so the just-deleted span
                // doesn't keep its tinted background until the next Flow emit.
                clearScrollMarkerHighlight(webViewRef.value)
            }
        },
        onDeleteAll = {
            markerSpanTapped = null
            showMarkerDeleteAllConfirm = true
        }
    )

    // F9: "Delete all" confirm. Passes groupTabId as the id; the dialog
    // doesn't care that this isn't a single-page bookmark id — it just
    // round-trips through onConfirm.
    DeleteAllScrollMarkersConfirmDialog(
        visible = capabilities.subWindows && showMarkerDeleteAllConfirm,
        bookmarkId = groupTabId,
        onDismiss = { showMarkerDeleteAllConfirm = false },
        onConfirm = { _ ->
            coroutineScope.launch {
                viewModel.deleteAllMarkersInGroup()
                clearScrollMarkerHighlight(webViewRef.value)
            }
        }
    )

    // Image-zoom overlay (tap on inlined <img>). Same component the regular
    // guide viewer uses, so behaviour matches across the app.
    guideImageZoomModel?.let { model ->
        // The zoom viewer is a Dialog, which an overlay window cannot host, so
        // there it draws inline over the page instead.
        if (capabilities.subWindows) {
            com.twinlore.app.ui.screens.guide.GuideImageZoomViewer(
                model = model,
                onDismiss = { guideImageZoomModel = null }
            )
        } else {
            com.twinlore.app.ui.components.ImageZoomSurface(
                model = model,
                onDismiss = { guideImageZoomModel = null }
            )
        }
    }

    // Image long-press action menu. Same dialog the in-app browser uses
    // (image-quality readout + "Assign as Artwork" / "Save Image"). For
    // offline content we resolve the URL to an MHT-cache file:// URI first
    // and route through libraryViewModel.assignArtworkFromFile /
    // mapViewModel.assignMapFile, both of which copy locally — no
    // re-download, works offline. Mirrors the single-guide viewer's dialog.
    if (capabilities.subWindows) {
    pendingImageLongPressUrl?.let { imageUrl ->
        // Save-to-vault view models for this dialog, the same handles the
        // browser uses. Inside the branch rather than at the top of the
        // composable: only a long press reaches them, and the fold panel's
        // window cannot resolve a hiltViewModel() at all.
        val libraryViewModel: com.twinlore.app.ui.screens.LibraryViewModel = hiltViewModel()
        val mapViewModel: com.twinlore.app.ui.screens.MapViewerScreenViewModel = hiltViewModel()
        val resolvedFile = remember(imageUrl) {
            com.twinlore.app.ui.screens.offline.resolveSavedGuideImageFile(
                baseDir = loadedAssetBaseDir,
                manifest = loadedAssetManifest,
                srcUrl = imageUrl
            )
        }
        val menuImageUrl = resolvedFile?.let { "file://${it.absolutePath}" } ?: imageUrl
        com.twinlore.app.ui.screens.BrowserImageContextMenu(
            imageUrl = menuImageUrl,
            pageUrl = state.activeEntry?.savedHref,
            displayName = com.twinlore.app.ui.screens.offline.suggestedImageFilename(
                srcUrl = imageUrl,
                sourceExtension = resolvedFile?.extension
            ),
            onAssignAsBoxArt = {
                if (resolvedFile == null) {
                    android.widget.Toast.makeText(
                        context,
                        "Image not in saved guide",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                    pendingImageLongPressUrl = null
                } else {
                    val fileUri = android.net.Uri.fromFile(resolvedFile)
                    pendingImageLongPressUrl = null
                    libraryViewModel.assignArtworkFromFile(
                        vaultId = vaultId,
                        fileUri = fileUri,
                        context = context,
                        onSuccess = {
                            android.widget.Toast.makeText(
                                context,
                                "Artwork assigned",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                            mainViewModel?.notifyArtworkAssigned(vaultId)
                        },
                        onError = { err ->
                            android.widget.Toast.makeText(
                                context,
                                "Artwork failed: $err",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                    )
                }
            },
            onAssignAsMap = {
                if (resolvedFile == null) {
                    android.widget.Toast.makeText(
                        context,
                        "Image not in saved guide",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                    pendingImageLongPressUrl = null
                } else {
                    val fileUri = android.net.Uri.fromFile(resolvedFile)
                    val title = com.twinlore.app.ui.screens.offline.suggestedImageFilename(
                        srcUrl = imageUrl,
                        sourceExtension = resolvedFile.extension
                    ).substringBeforeLast('.')
                    pendingImageLongPressUrl = null
                    mapViewModel.assignMapFile(
                        vaultId = vaultId,
                        uri = fileUri,
                        title = title.ifBlank { "Map" },
                        context = context,
                    ) { saved ->
                        val msg = if (saved != null) "Image saved" else "Save failed"
                        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onDismiss = { pendingImageLongPressUrl = null }
        )
    }
    }

    if (pageFindOpen) {
        // This page only: the single guide viewer's find bar over the page,
        // placed as it places it (under the panel's bar in the overlay, in
        // compact form; 24dp clear of the status bar in the app).
        val inPanel = capabilities == com.twinlore.app.ui.screens.guide.GuideViewerCapabilities.OVERLAY
        // Fixed for this opening: the bar reads it once. A query carried in
        // (from All pages, or the selection menu) is already being searched,
        // so the keyboard only comes up for an empty field.
        val openWithKeyboard = remember { findQuery.isBlank() }
        Box(modifier = Modifier.fillMaxSize()) {
            com.twinlore.app.ui.screens.FindOnPageUI(
                query = findQuery,
                onQueryChange = { findQuery = it },
                onSearch = { runPageFind() },
                onClose = { closePageFind() },
                onSaveAsScrollMarker = {
                    // As in the single guide viewer: marker-from-find is not
                    // wired yet, so the button closes find.
                    closePageFind()
                },
                onNext = { webViewRef.value?.findNext(true) },
                onPrevious = { webViewRef.value?.findNext(false) },
                matchesFound = pageFindMatchesFound,
                matchCount = pageFindMatchCount,
                currentIndex = pageFindCurrentIndex,
                searchTrigger = pageFindSearchTrigger,
                isSearching = pageFindIsSearching,
                requestFocusOnOpen = openWithKeyboard,
                compact = inPanel,
                recentSearches = true,
                extraContent = {
                    PageGroupFindScopeSwitch(
                        scope = PageGroupFindScope.THIS_PAGE,
                        focusColor = effectiveFocusColor,
                        onScopeChange = { switchFindScope(it) },
                        modifier = Modifier.padding(top = 6.dp)
                    )
                },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = if (inPanel) 0.dp else 24.dp)
            )
        }
    } else if (showFind) {
        // Lazily rebuild any missing sidecars (LRU evicted, or this device never
        // ran the save). Materializes each unique MHT exactly once via the same
        // SAF spill the cross-file load path uses, then strips it through
        // PageGroupTextIndex. Silently skips unresolvable resources so the
        // sheet still opens and shows whatever it can.
        LaunchedEffect(state.entries) {
            sidecarCache.ensureForGroup(
                groupTabId = groupTabId,
                entries = state.entries
            ) { mhtResourceId ->
                val uri = resolver.resolve(mhtResourceId)
                when (uri?.scheme) {
                    "file" -> uri.path?.let { java.io.File(it) }
                    "content" -> {
                        val tmp = java.io.File(
                            context.cacheDir,
                            "mht_source/sidecar_$mhtResourceId.mht"
                        )
                        tmp.parentFile?.mkdirs()
                        val copied = context.contentResolver.openInputStream(uri)?.use { input ->
                            tmp.outputStream().use { input.copyTo(it) }
                            true
                        } ?: false
                        if (copied) tmp else null
                    }
                    else -> null
                }
            }
            sidecarCache.touch(groupTabId)
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            PageGroupFindScreen(
                entries = state.entries,
                // The panel's bar is right above; the field's floating label
                // is room enough.
                topSpacing = if (capabilities == com.twinlore.app.ui.screens.guide.GuideViewerCapabilities.OVERLAY) 0.dp else 24.dp,
                engine = findEngine,
                focusColor = effectiveFocusColor,
                query = findQuery,
                onScopeChange = { switchFindScope(it) },
                onQueryChange = {
                    findQuery = it
                    // K3: keep the page-view banner in sync with the input.
                    // Blanking the input clears the active highlight target;
                    // the banner-side `clearMatches` will fire on next tap.
                    activeSearchQuery = it.ifBlank { null }
                },
                onDismiss = {
                    showFind = false
                    // K2: do NOT clear `findQuery` — re-opening should
                    // restore the previous query in the input.
                },
                onPick = { entryId, query, occurrenceInEntry ->
                    showFind = false
                    pushHistoryBeforeJump()
                    // K3: a navigated-to query is the "live" one for the
                    // findAllAsync overlay; surface the clear banner.
                    activeSearchQuery = query.ifBlank { null }
                    pendingFindQuery.value = query
                    val activeId = state.activeEntry?.id
                    // scrollToMarkerByText uses 1-based occurrenceIndex; the
                    // engine emits 0-based, so we shift by +1.
                    val oneBasedOccurrence = occurrenceInEntry + 1
                    if (activeId == entryId) {
                        // Same entry: no cross-file load fires, so the queued
                        // PendingScrollMarker would never be consumed. Scroll
                        // inline; then re-issue findAllAsync so the native
                        // yellow-box overlay reflects the current query.
                        // K1: no flash — the findAllAsync overlay highlights
                        // every match, so an extra per-tap flash is noise.
                        scrollToMarkerByText(
                            webView = webViewRef.value,
                            label = query,
                            fallbackScrollY = 0,
                            occurrenceIndex = oneBasedOccurrence,
                            flashFocusHex = null
                        ) { /* resolved Y not needed */ }
                        webViewRef.value?.findAllAsync(query)
                    } else {
                        // Cross-entry (anchor-sibling OR cross-file): enqueue
                        // the marker; the existing consumer in onPageFinished /
                        // anchor-sibling LaunchedEffect handles the scroll.
                        // K1: flashHex=null — the findAllAsync overlay shows
                        // the matches, so an extra viewport flash is noise.
                        viewModel.enqueueScrollToMarker(
                            label = query,
                            scrollY = 0,
                            occurrenceIndex = oneBasedOccurrence,
                            flashHex = null
                        )
                        viewModel.onEntryTapped(entryId)
                    }
                }
            )
        }
    }

    if (capabilities.subWindows && showDisplaySheet) {
        // Unified Display sheet — replaces the old App-theme + Resize-text
        // kebab pair. Surfaces palette, apply-theme switch, font, and
        // text-size slider in one place. textZoom hot-state stays here so
        // the LaunchedEffect above continues to apply zoom to the WebView.
        PageGroupDisplaySheet(
            // The sheet is a ModalBottomSheet, so it is only ever opened where
            // sub-windows work, which is also the only place this resolves.
            settingsViewModel = hiltViewModel(),
            palette = palette,
            focusColor = effectiveFocusColor,
            textZoomFraction = textZoomFraction,
            onTextZoomChange = { newZoom ->
                textZoomFraction = newZoom
                // Survivor scope: a rememberCoroutineScope job would be
                // cancelled if the sheet closes mid-write. The slider only
                // fires for committed changes (Slider's onValueChange
                // coalesces while dragging), so single-shot fire-and-forget
                // is fine here.
                val tabId = groupTabId
                kotlinx.coroutines.CoroutineScope(
                    kotlinx.coroutines.SupervisorJob() + Dispatchers.IO
                ).launch {
                    savedTabRepositoryForZoom.updateBookmarkZoomLevel(tabId, newZoom)
                }
            },
            isTextGuide = isTextGuide,
            onDismiss = { showDisplaySheet = false }
        )
    }

    if (capabilities.subWindows && showConfirmDelete) {
        ConfirmDeleteDialog(
            onConfirm = {
                showConfirmDelete = false
                coroutineScope.launch {
                    viewModel.delete()
                    onBack()
                }
            },
            onCancel = { showConfirmDelete = false }
        )
    }

    if (capabilities.subWindows) {
    pendingRemoveEntryId?.let { entryId ->
        val entryLabel = state.entries.firstOrNull { it.id == entryId }?.label.orEmpty()
        AlertDialog(
            onDismissRequest = { pendingRemoveEntryId = null },
            title = { Text("Remove from group?") },
            text = {
                Text("\"$entryLabel\" will be removed. If no other entry points at the same saved page, the page is also deleted.")
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeEntry(entryId)
                    pendingRemoveEntryId = null
                }) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemoveEntryId = null }) { Text("Cancel") }
            }
        )
    }
    }
}

/**
 * Top bar with prev/next chevrons flanking a tight label column
 * (current entry's display name + "n / N"), followed by ToC / Find /
 * kebab icon buttons.
 */
/**
 * Floating Prev/Next chapter pair surfaced near either edge of an entry's
 * body. Caller positions via [alignment] (TopCenter or BottomCenter) and
 * matching padding. Same visual chrome as the K3 search-clear banner —
 * solid Card on a surface background, 12dp rounded, 8dp elevation. Drawn
 * 95% width so the elevation shadow has breathing room.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PageGroupTopBar(
    title: String,
    activeEntry: PageGroupEntryEntity?,
    activeIndex: Int?,
    totalEntries: Int,
    onBack: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onTocClicked: () -> Unit,
    onFindClicked: () -> Unit,
    onDisplayClicked: () -> Unit,
    /** False where there is nothing behind the viewer to go back to. */
    showBack: Boolean = true,
    /** False where the display sheet cannot open, so the button is not shown. */
    showDisplay: Boolean = true,
    // D-pad UP from the vault-session bottom bar targets this requester so focus
    // lands on the first top-bar button; null outside the vault session.
    firstButtonFocusRequester: androidx.compose.ui.focus.FocusRequester? = null,
    // D-pad DOWN from anywhere in the top bar returns focus here (the bottom bar)
    // so the body WebView — which has no Compose focusable — can't trap focus.
    bottomBarFocusRequester: androidx.compose.ui.focus.FocusRequester? = null
) {
    // Custom Row (not Material3 TopAppBar) so the bar is ~40dp tall instead
    // of 64dp — Material3's small TopAppBar reserves vertical space for an
    // optional second line of text, leaving a visible band between this
    // bar and the vault-session top bar above it.
    // NavigableIconButton (same component the browser/picker top bar uses)
    // draws the focus ring + focusColor tint and plays focus/confirm feedback,
    // so the bar's buttons highlight like every other focusable control.
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val iconSize = if (isLandscape) 24.dp else 20.dp
    val iconPadding = 4.dp
    androidx.compose.material3.Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = androidx.compose.ui.Modifier
                .fillMaxWidth()
                .heightIn(min = 40.dp)
                .padding(horizontal = 4.dp)
                .focusProperties {
                    down = bottomBarFocusRequester
                        ?: androidx.compose.ui.focus.FocusRequester.Default
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showBack) {
                NavigableIconButton(
                    imageVector = androidx.compose.material.icons.Icons.Default.FolderOpen,
                    contentDescription = "Files",
                    onClick = onBack,
                    focusId = "page_group_files",
                    iconSize = iconSize,
                    contentPadding = iconPadding,
                    externalFocusRequester = firstButtonFocusRequester
                )
            }
            val labelText = activeEntry?.label?.ifBlank { null }
                ?: title.ifBlank { "Page group" }
            val prefix = if (activeIndex != null && totalEntries > 0)
                "${activeIndex + 1}/$totalEntries  " else ""
            Text(
                text = prefix + labelText,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = androidx.compose.ui.Modifier
                    .weight(1f)
                    // A tap on the chapter opens the contents, the same as the
                    // list button. Touch only: D-pad already has that button,
                    // and a focus stop on a label would only get in the way.
                    .pointerInput(onTocClicked) {
                        detectTapGestures { onTocClicked() }
                    }
                    .padding(horizontal = 8.dp)
            )
            val canPrev = activeIndex != null && activeIndex > 0
            val canNext = activeIndex != null && activeIndex < totalEntries - 1
            Row(
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                NavigableIconButton(
                    imageVector = Icons.Filled.KeyboardArrowLeft,
                    contentDescription = "Previous entry",
                    onClick = onPrev,
                    enabled = canPrev,
                    focusId = "page_group_prev",
                    iconSize = iconSize,
                    contentPadding = iconPadding
                )
                NavigableIconButton(
                    imageVector = Icons.Filled.KeyboardArrowRight,
                    contentDescription = "Next entry",
                    onClick = onNext,
                    enabled = canNext,
                    focusId = "page_group_next",
                    iconSize = iconSize,
                    contentPadding = iconPadding
                )
                NavigableIconButton(
                    imageVector = Icons.Filled.Menu,
                    contentDescription = "Contents",
                    onClick = onTocClicked,
                    focusId = "page_group_toc",
                    iconSize = iconSize,
                    contentPadding = iconPadding
                )
                NavigableIconButton(
                    imageVector = Icons.Filled.Search,
                    contentDescription = "Find in group",
                    onClick = onFindClicked,
                    focusId = "page_group_find",
                    iconSize = iconSize,
                    contentPadding = iconPadding
                )
                if (showDisplay) {
                    NavigableIconButton(
                        imageVector = Icons.Default.Palette,
                        contentDescription = "Display settings",
                        onClick = onDisplayClicked,
                        focusId = "page_group_display",
                        iconSize = iconSize,
                        contentPadding = iconPadding
                    )
                }
            }
        }
    }
}

@Composable
private fun ConfirmDeleteDialog(
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Delete page-group?") },
        text = {
            Text(
                "This deletes every saved page in the group plus the on-disk folder. " +
                    "The action cannot be undone."
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Delete") }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text("Cancel") }
        }
    )
}

/**
 * One frame of browser-style navigation history. Captures where the user
 * was just before they took an explicit jump (Prev/Next, ToC tap, anchor
 * click, marker tap, find result) so Back / Forward can restore both the
 * entry and the scroll position.
 *
 * [scrollY] is in CSS pixels — pinch-zoom-independent so the user lands at
 * the same visual position even if they pinched between save and restore.
 * Consumers multiply by the WebView's current scale to convert back to
 * device-px for `wv.scrollTo`, or pass directly to JS `window.scrollTo`
 * (which takes CSS-px).
 */
internal data class HistoryEntry(
    val entryId: String,
    val scrollY: Int
)

/**
 * Compact pill-shaped floating button shown near the end of a page.
 * Mirrors the surface alpha + tonal style of the history strip so
 * the chrome family reads as one system.
 */
@Composable
private fun NextPageFloatingButton(
    label: String,
    focusColor: Color,
    onClick: () -> Unit
) {
    val view = LocalView.current
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)
    androidx.compose.material3.Surface(
        onClick = {
            FeedbackManager.play(view, FeedbackEvent.CONFIRM)
            onClick()
        },
        shape = shape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        tonalElevation = 2.dp,
        shadowElevation = 4.dp,
        border = BorderStroke(1.dp, focusColor)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Next page: $label",
                style = MaterialTheme.typography.labelMedium,
                color = focusColor,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = focusColor,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * Build an inline JS snippet that scrolls to the chosen target during
 * DOMContentLoaded. Splicing this into the HTML before
 * loadDataWithBaseURL makes the browser repaint already at the landing
 * position — no post-load scroll work, no loading overlay needed.
 *
 * Priority order matches the LaunchedEffect's pending selection:
 * history > fragment > ratio. Returns empty string when no target is
 * set (the WebView stays at scrollY=0 — reading-mode chrome strip
 * means the visible content already starts at the top).
 *
 * All three targets are recomputed from the live layout on every height
 * change until the page settles ([buildSettlingScrollJs]). The document
 * is still moving at DOMContentLoaded — most of all because the
 * text-size slider goes on as a body font-size in onPageFinished and
 * takes about a sixth of the height with it — and a saved ratio applied
 * against the pre-shrink height lands `ratio × heightLost` too far
 * down, which is what "coming back to a group guide scrolls further
 * than I left it" was.
 */
internal fun buildPageGroupInlineScrollJs(
    historyYCssPx: Int?,
    fragment: String?,
    scrollRatio: Double?,
): String {
    val body = when {
        historyYCssPx != null && historyYCssPx > 0 ->
            "window.scrollTo(0, $historyYCssPx);"
        !fragment.isNullOrBlank() -> {
            val safe = fragment.removePrefix("#")
                .replace("\\", "\\\\")
                .replace("'", "\\'")
            """
            var frag = '$safe';
            try { frag = decodeURIComponent(frag); } catch (e) {}
            var el = document.getElementById(frag) ||
                     document.getElementById('user-content-' + frag.toLowerCase()) ||
                     document.querySelector('[name="' + frag.replace(/"/g, '\\"') + '"]');
            if (el) {
                var rect = el.getBoundingClientRect();
                window.scrollTo(0, Math.max(0, Math.round(rect.top + (window.pageYOffset || 0)) - 80));
            }
            """.trimIndent()
        }
        scrollRatio != null && scrollRatio > 0.0 ->
            """
            var h = Math.max(
                document.body ? document.body.scrollHeight : 0,
                document.documentElement ? document.documentElement.scrollHeight : 0);
            if (h > 0) window.scrollTo(0, Math.round(h * $scrollRatio));
            """.trimIndent()
        else -> return ""
    }
    return buildSettlingScrollJs(body, landingCall = ANDROID_INTERFACE_LANDING_CALL)
}
