package com.twinlore.app.ui.screens.guide

import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.style.TextOverflow
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.twinlore.app.data.FocusColor
import com.twinlore.app.di.NetworkConnectivityEntryPoint
import com.twinlore.app.di.OfflineGuideLoaderEntryPoint
import com.twinlore.app.di.ResourceResolverEntryPoint
import com.twinlore.app.domain.SavedTab
import com.twinlore.app.domain.VaultTab
import com.twinlore.app.ui.LocalCachedFocusColor
import com.twinlore.app.ui.LocalThemePalette
import com.twinlore.app.ui.MainViewModel
import com.twinlore.app.ui.screens.FindOnPageUI
import com.twinlore.app.ui.screens.GuideWebView
import com.twinlore.app.ui.screens.REMOVE_PALETTE_CSS_JS
import com.twinlore.app.ui.screens.SELECTION_RECT_JS
import com.twinlore.app.ui.screens.SettingsViewModel
import com.twinlore.app.ui.screens.buildIframeGateJs
import com.twinlore.app.ui.screens.buildPaletteCssJs
import com.twinlore.app.ui.screens.colorToContrastHex
import com.twinlore.app.ui.screens.colorToHex
import com.twinlore.app.ui.screens.group.PageGroupDisplaySheet
import com.twinlore.app.ui.screens.buildScrollMarkerHighlightJs
import com.twinlore.app.ui.screens.highlightScrollMarkerInPage
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
import com.twinlore.app.ui.screens.offline.buildReadingModeJs
import com.twinlore.app.ui.screens.offline.buildSettlingScrollJs
import com.twinlore.app.ui.screens.offline.copyAnchorTextToClipboard
import com.twinlore.app.ui.screens.offline.filterForHost
import com.twinlore.app.ui.screens.offline.isExternalAnchor
import com.twinlore.app.ui.screens.offline.mayPersistScrollRatio
import com.twinlore.app.ui.screens.offline.isLocalAnchor
import com.twinlore.app.ui.screens.offline.spliceInlineDecorationsScript
import com.twinlore.app.ui.screens.scrollToMarkerByText
import dagger.hilt.android.EntryPointAccessors
import com.twinlore.app.domain.ScrollMarker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Single-guide viewer. One saved bookmark, in-page anchor ToC, scroll-
 * driven chapter title, iOS-Safari chrome hide, YouTube embeds via
 * https://localhost.invalid/ base URL. Mirrors `PageGroupViewerScreen`
 * shape; differences are the single-bookmark data source and the
 * Prev/Next semantics (walk the JS-extracted anchor list, not group
 * entries).
 *
 * The host (GuideScreen router) passes `chromeVisible` + chrome
 * callbacks so this screen integrates with the vault-session chrome
 * the same way page-group does.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SingleGuideViewerScreen(
    vaultId: String,
    bookmark: SavedTab,
    onBack: () -> Unit,
    chromeVisible: Boolean = true,
    onChromeToggle: () -> Unit = {},
    onChromeHide: () -> Unit = {},
    onChromeReveal: () -> Unit = {},
    isActive: Boolean = true,
    // Controller focus anchors plumbed from the vault session so D-pad UP from
    // the bottom bar reaches the top bar and D-pad DOWN returns from it.
    contentFocusRequester: androidx.compose.ui.focus.FocusRequester? = null,
    bottomBarFocusRequester: androidx.compose.ui.focus.FocusRequester? = null,
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
    capabilities: GuideViewerCapabilities = GuideViewerCapabilities.ACTIVITY,
    /**
     * A link that would leave the reader, when
     * [GuideViewerCapabilities.externalNavigation] is false. The load has
     * already been consumed by the time this runs; the host decides what to
     * offer instead. Never called in the app, where links go where they always
     * went.
     */
    onExternalLink: (String) -> Unit = {},
    /**
     * Text size, when the host owns the control rather than this viewer.
     *
     * The app opens the display sheet for it; the fold panel cannot host a
     * sheet, so its own Settings surface drives this instead. Null means the
     * viewer keeps the bookmark's stored zoom, which is every case but that one.
     */
    textZoomOverride: Float? = null,
    /** False where there is nothing behind this viewer to go back to. */
    showBack: Boolean = true,
    /**
     * Moves each time the app comes back from Always on Top, and this viewer
     * re-reads its bookmark on each move: the panel hosted its own copy over
     * the same row and wrote the position it was left at. Zero, the default,
     * never moves, which is every host but the vault session.
     */
    positionSyncKey: Int = 0,
    /**
     * The app's own state. Null when this viewer is hosted somewhere with no
     * activity behind it, which today means the fold panel's overlay window.
     * Everything reached through it is an app action rather than a reading
     * action: handing a link to the Browser tab, naming the open document in
     * the vault top bar, splitting an oversized text guide into a page group.
     * A null host loses those and keeps the reading.
     */
    mainViewModel: MainViewModel? = hiltViewModel(),
    /** Scroll markers. Null where there is no view model store; no markers then. */
    viewModel: SingleGuideViewerViewModel? =
        hiltViewModel(key = "single_guide_${bookmark.id}")
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(bookmark.id) { viewModel?.bind(bookmark.id) }

    // Re-tap of Guide tab while in viewer exits to picker.
    LaunchedEffect(Unit) {
        mainViewModel?.tabReselected?.collect { tab ->
            if (tab == VaultTab.Guide) onBack()
        }
    }

    val savedTabRepository = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            com.twinlore.app.di.SavedTabRepositoryEntryPoint::class.java
        ).savedTabRepository()
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
    val txtConversionCache = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            OfflineGuideLoaderEntryPoint::class.java
        ).txtConversionCache()
    }
    val networkConnectivityManager = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            NetworkConnectivityEntryPoint::class.java
        ).networkConnectivityManager()
    }
    // Cosmetic element-hide layer (CosmeticFilters): hides ad-iframe / ad-slot
    // placeholders that survive the request-layer block. Mirrors what the live
    // browser does at onPageStarted + onPageFinished — same gate
    // (`privacyPreferences.enabled`), same injection idiom. Saved guides
    // baked in DOM ad-wrappers at save time; this is the only way to collapse
    // the empty space they leave once we refuse to load the ad subresources.
    val privacyPreferences = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            com.twinlore.app.di.PrivacyPreferencesEntryPoint::class.java
        ).privacyPreferences()
    }

    var showTocView by remember(bookmark.id) { mutableStateOf(false) }
    // Last-selected ToC overlay tab (0 = Contents, 1 = Markers). Hoisted
    // here so the user's choice survives closing and reopening the overlay
    // within the same bookmark session. Resets to Contents when the
    // bookmark changes (the remember(bookmark.id) key).
    var tocTabIndex by remember(bookmark.id) { mutableStateOf(0) }

    // System-back closes the ToC overlay (matches the back-arrow icon in the
    // overlay header) rather than navigating away from the viewer entirely.
    // Only enabled while the overlay is up so the normal viewer-exit back
    // behaviour is preserved otherwise.
    androidx.activity.compose.BackHandler(enabled = showTocView) {
        showTocView = false
    }

    // L1 / R1 scroll-marker stepping focus.
    var focusedMarkerIndex by remember(bookmark.id) { mutableStateOf<Int?>(null) }
    var showFind by remember(bookmark.id) { mutableStateOf(false) }
    // A selection in the panel needs the window focused too: Chromium hides
    // its drag handles in a window that never has focus, and the panel is
    // otherwise kept unfocusable so the game keeps its input.
    var panelSelecting by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(showFind) { onWantsKeyboard(showFind) }
    androidx.compose.runtime.LaunchedEffect(panelSelecting) { onWantsFocus(panelSelecting) }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { onWantsKeyboard(false); onWantsFocus(false) } }
    var findQuery by remember(bookmark.id) { mutableStateOf("") }
    var findMatchesFound by remember(bookmark.id) { mutableStateOf<Boolean?>(null) }
    var findMatchCount by remember(bookmark.id) { mutableStateOf(0) }
    var findCurrentIndex by remember(bookmark.id) { mutableStateOf(0) }
    var findSearchTrigger by remember(bookmark.id) { mutableStateOf(0) }
    var findIsSearching by remember(bookmark.id) { mutableStateOf(false) }
    // Bumped by Copy in the panel, where a toast would land on the game's display.
    var copiedPillTrigger by remember { mutableStateOf(0) }
    var showDisplaySheet by remember(bookmark.id) { mutableStateOf(false) }
    // Set from the loaded document, so the Display sheet can dim Smart Formatting
    // on guides that never went through the text converter.
    var isTextGuide by remember(bookmark.id) { mutableStateOf(false) }
    var pendingDeleteMarkerId by remember(bookmark.id) { mutableStateOf<String?>(null) }

    // System-back outside of any overlay closes the single-guide viewer and
    // returns to the Guide-tab file picker — matches how the legacy
    // browser-based viewer behaved. Placed AFTER the find/tocView state is
    // declared so the lambda closes over fresh references.
    androidx.activity.compose.BackHandler(enabled = !showTocView && !showFind) {
        onBack()
    }

    // WebView + asset manifest refs. Declared before the textZoom effect that
    // reads `webViewRef.value` so the closure binds the right symbol.
    val webViewRef = remember { mutableStateOf<GuideWebView?>(null) }
    val loadedAssetBaseDirState = remember(bookmark.id) { mutableStateOf<java.io.File?>(null) }
    val loadedAssetManifestState = remember(bookmark.id) {
        mutableStateOf<Map<String, String>>(emptyMap())
    }
    // First open of a non-cached text guide does real work (parse + extract +
    // convert, or split into a group) before the WebView has anything to show.
    // Track that so we can cover the blank wait with a spinner. Cleared at
    // onPageFinished; a short delay before showing it avoids a flash on fast
    // (cached) loads.
    var isPreparingGuide by remember(bookmark.id) { mutableStateOf(true) }
    var showPrepareSpinner by remember(bookmark.id) { mutableStateOf(false) }
    LaunchedEffect(bookmark.id, isPreparingGuide) {
        if (isPreparingGuide) {
            delay(250)
            if (isPreparingGuide) showPrepareSpinner = true
            // Safety net: never leave the spinner stuck if a callback never fires.
            delay(15_000)
            isPreparingGuide = false
        } else {
            showPrepareSpinner = false
        }
    }

    // Text zoom slider (display sheet) — Int % applied to WebView.settings.textZoom.
    // Seeded from the cached SavedTab.zoomLevel for an instant initial
    // value; the next effect re-fetches from Room because the cached
    // arg can be stale after another bookmark write.
    var textZoomFraction by remember(bookmark.id) { mutableStateOf(bookmark.zoomLevel) }
    LaunchedEffect(bookmark.id) {
        val fresh = withContext(Dispatchers.IO) {
            savedTabRepository.getTabById(bookmark.id)?.zoomLevel
        }
        if (fresh != null) textZoomFraction = fresh
    }
    LaunchedEffect(textZoomOverride) {
        if (textZoomOverride != null) textZoomFraction = textZoomOverride
    }
    val currentTextZoomFraction by rememberUpdatedState(textZoomFraction)
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

    // Image-tap zoom viewer model — set by the JS bridge when the user taps
    // an <img> in the body. Resolved via the MHT manifest to a file:// URL
    // when possible; falls back to the http(s) src.
    var guideImageZoomModel by remember(bookmark.id) { mutableStateOf<String?>(null) }

    // Detected ToC entries (from anchor_cluster_extract.js). Refreshed
    // on every onPageFinished.
    var tocEntries by remember(bookmark.id) { mutableStateOf<List<SingleGuideTocEntry>>(emptyList()) }

    // Scroll-driven chapter title: index picked by the scroll listener.
    // Default rule: largest targetScrollY ≤ scrollY + slop. End-of-doc
    // fallback: when the standard pick is strictly above the visible
    // viewport, prefer the first heading currently visible (covers
    // zoom-out near the end where the page can't scroll the heading to
    // the top). See the picker callsite in the onScrollY lambda below.
    var activeChapterIndex by remember(bookmark.id) { mutableStateOf<Int?>(null) }
    val activeChapterLabel = activeChapterIndex
        ?.let { idx -> tocEntries.getOrNull(idx)?.label }
    // Live wv.scrollY (WebView coordinate space — CSS-px × pinch scale).
    // Feeds the ScrollIndicatorChip position. Updated by the scroll
    // listener. The chip reads it as a fraction of the total scroll range,
    // so the exact unit doesn't matter as long as it's consistent.
    var liveScrollY by remember(bookmark.id) { mutableStateOf(0) }

    // WebView's current pinch-zoom scale factor. Updated from
    // WebViewClient.onScaleChanged AND on every scroll event (live read
    // covers WebView builds where onScaleChanged doesn't fire reliably).
    // Keys the anchor re-extract LaunchedEffect so a layout-shifting
    // pinch — e.g. TEXT_AUTOSIZING — refreshes the cached Y positions.
    var webViewScale by remember(bookmark.id) { mutableStateOf(1f) }

    // Browser-style back/forward history. Snapshot pushed BEFORE each
    // explicit jump (ToC tap, in-body anchor, marker tap). Pure finger-
    // scroll is transparent. Single-guide stays in one document, so a
    // history frame is just `(scrollY, label)`.
    var backHistory by remember(bookmark.id) {
        mutableStateOf<List<Pair<Int, String?>>>(emptyList())
    }
    var forwardHistory by remember(bookmark.id) {
        mutableStateOf<List<Pair<Int, String?>>>(emptyList())
    }

    // Hide vault chrome while overlays are open. Publish overlay-open so the vault
    // session's "chrome hidden → swallow keys to wake chrome" logic lets dpad presses
    // through to the overlay's focusables (TOC rows, find bar).
    LaunchedEffect(showFind, showTocView) {
        val overlayOpen = showFind || showTocView
        mainViewModel?.setVaultSessionGuideOverlayOpen(overlayOpen)
        if (overlayOpen) onChromeHide() else onChromeReveal()
    }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { mainViewModel?.setVaultSessionGuideOverlayOpen(false) }
    }

    // Palette CSS sources (same as page-group).
    val palette = LocalThemePalette.current
    // Not a ViewModel: this composable is also hosted in the fold panel's
    // overlay window, where there is no activity for hiltViewModel() to scope
    // one to. See AppUiSettings.
    val uiSettings = com.twinlore.app.ui.rememberAppUiSettings()
    val guideThemeInject by uiSettings.guideThemeInject.collectAsState(initial = true)
    val smartFormatting by uiSettings.guideSmartFormatting.collectAsState(initial = true)
    val appFont by uiSettings.appFont.collectAsState(
        initial = com.twinlore.app.data.AppFont.DEFAULT
    )
    val paletteBgHex = remember(palette) { String.format("#%06X", 0xFFFFFF and palette.background.toArgb()) }
    val paletteFgHex = remember(palette) { String.format("#%06X", 0xFFFFFF and palette.foreground.toArgb()) }
    val currentPaletteBgHex by rememberUpdatedState(paletteBgHex)
    val currentPaletteFgHex by rememberUpdatedState(paletteFgHex)
    val currentAppFont by rememberUpdatedState(appFont)
    val currentGuideThemeInject by rememberUpdatedState(guideThemeInject)
    val currentSmartFormatting by rememberUpdatedState(smartFormatting)

    val focusColorState by uiSettings.focusColor.collectAsState(initial = FocusColor.CRIMSON)
    val cachedFocusColor = LocalCachedFocusColor.current?.value
    val focusColor = cachedFocusColor ?: focusColorState.color
    // Only fall back to electric-blue when the chosen black/white accent would be
    // invisible on the CURRENT theme background — black on dark, white on light.
    // Black on a light theme (or white on dark) is readable and kept as-is, so
    // the chrome (sheet toggle/reset, nav strip) reflects the real choice.
    val useBlueHighlight = (focusColor == FocusColor.WHITE.color && !palette.isDark) ||
        (focusColor == FocusColor.BLACK.color && palette.isDark)
    val effectiveFocusColor = if (useBlueHighlight) FocusColor.ELECTRIC_BLUE.color else focusColor
    val effectiveHighlightHex = remember(effectiveFocusColor) { colorToHex(effectiveFocusColor) }
    val effectiveContrastHex = remember(effectiveFocusColor) { colorToContrastHex(effectiveFocusColor) }
    val currentHighlightHex by rememberUpdatedState(effectiveHighlightHex)
    val currentContrastHex by rememberUpdatedState(effectiveContrastHex)

    // Hoisted rather than allocated inline: without a view model there are no
    // markers, and a fresh flow per recomposition would restart the collector.
    val noMarkers = remember { MutableStateFlow(emptyList<ScrollMarker>()) }
    val markers by (viewModel?.markers ?: noMarkers).collectAsState()

    // Controller bindings: left-stick scroll + Find / ToC / Top / End /
    // marker-prev/next / prev-next bookmark — matches what the legacy
    // GuideScreen handled before this offline viewer took its place.
    // L2 / R2 (prev/next bookmark) are deliberately no-ops here for now;
    // the group viewer overrides them with goPrev / goNext.
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
                    fallbackScrollY = m.scrollY,
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
                    fallbackScrollY = m.scrollY,
                    occurrenceIndex = m.occurrenceIndex,
                    flashFocusHex = currentHighlightHex
                ) { /* resolved Y unused */ }
            }
        },
    )
    } // end if (mainViewModel != null)

    // Re-apply marker highlights whenever the markers list emits — covers
    // newly-saved markers without waiting for the next onPageFinished
    // (page is already loaded; the user just hit Save Marker / Save as
    // marker and expects the highlight to appear immediately).
    LaunchedEffect(markers) {
        val wv = webViewRef.value ?: return@LaunchedEffect
        highlightScrollMarkerInPage(
            webView = wv,
            markers = markers,
            focusedIndex = null,
            focusColorHex = currentHighlightHex,
            focusContrastHex = currentContrastHex
        )
        // After highlights settle (wraps matched text in spans → shifts
        // subsequent content), re-extract ToC entries so cached Ys reflect
        // the post-mutation layout. Without this the scroll-driven chapter
        // tracker drifts after every marker add / delete.
        wv.postDelayed({
            extractAnchorClusterEntries(wv.context, wv) { entries ->
                if (entries.isNotEmpty()) {
                    tocEntries = entries
                    // Don't touch activeChapterIndex — the scroll listener
                    // owns it. Just refresh the Y cache.
                }
            }
        }, 400L)
    }

    // Re-apply palette CSS whenever the palette or theme settings change.
    // The WebView's native bg colour was set once in the AndroidView factory;
    // without this re-apply, theme switches re-theme the body via CSS but the
    // underlying view colour stays at the old palette. Visible as a flash of
    // the previous colour during scroll.
    //
    // Reading-mode chrome strip rides alongside on the same toggle: the
    // theme-inject flag governs both palette restyle and chrome strip.
    // The host filter is null for guides whose URL is unknown or whose
    // host has no registry entry — in that case [buildReadingModeJs] isn't
    // called and the page renders unchanged.
    //
    // Source-URL resolution, in order:
    //   1. `bookmark.url` — set for guides saved through the in-app browser.
    //   2. The archive's own recorded Content-Location, persisted next to the
    //      parsed cache by MhtCacheManager. Guides adopted from a scanned
    //      media folder (a sync app dropping .mht files into a vault) are
    //      inserted with `url = ""` by LocalMediaBinder, so without this
    //      fallback reading mode no-opped for them on every host — not just
    //      the ones with an interesting filter.
    //
    // Starts at `bookmark.url` and is upgraded by the MHT load effect below
    // once the cache directory has been read; blank until then, which the
    // null-filter path already handles safely.
    val sourceUrlState = remember(bookmark.id) { mutableStateOf(bookmark.url) }
    val sourceUrl = sourceUrlState.value
    val readingFilter = remember(sourceUrl) { filterForHost(sourceUrl) }
    // isActive MUST be a key: a palette change while the Guide tab is hidden
    // evaluates into a hidden/paused WebView and is never re-applied on
    // return (dark-stuck guide after switching theme elsewhere). Keying on
    // isActive re-runs the apply the moment the viewer is visible again.
    // readingFilter IS a key: for a scanned guide the filter resolves only after
    // the MHT load effect recovers the archive's source URL, which lands after
    // this effect's first run. Without the key, a late-resolved filter would
    // never be applied to an already-loaded document.
    LaunchedEffect(webViewRef.value, guideThemeInject, smartFormatting, palette, appFont, isActive, readingFilter) {
        if (!isActive) return@LaunchedEffect
        val wv = webViewRef.value ?: return@LaunchedEffect
        // Smart formatting is a class swap on the loaded document, so flipping it
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
            readingFilter?.let { wv.evaluateJavascript(buildReadingModeJs(it), null) }
        } else {
            wv.evaluateJavascript(REMOVE_PALETTE_CSS_JS, null)
            wv.evaluateJavascript(REMOVE_READING_MODE_JS, null)
        }
        // Everything above rewrites the document: smart formatting refits a
        // text guide's blocks, the reading-mode strip removes nodes, the
        // palette CSS re-lays it out. Marker highlights are spans inside those
        // text nodes and do not survive it, so they go back on afterwards.
        highlightScrollMarkerInPage(
            webView = wv,
            markers = markers,
            focusedIndex = null,
            focusColorHex = currentHighlightHex,
            focusContrastHex = currentContrastHex,
        )
    }

    val pendingFindQuery = remember(bookmark.id) { mutableStateOf<String?>(null) }

    val topRevealThresholdPx = with(LocalDensity.current) { 8.dp.toPx().toInt() }
    // Chapter-pick activation slop. A chapter becomes "current" once its
    // heading is within 100 CSS px below the viewport top (scroll listener
    // converts wv.scrollY to CSS px via the live pinch-zoom scale). Sized
    // to comfortably clear the 80-CSS-px landing offset Prev/Next uses
    // via scroll-margin-top, without picking the next chapter when
    // chapters are densely packed.
    val chapterPickSlopCss = 100
    // Re-extract TOC positions whenever a setting reflows the document
    // out from under the cached targetScrollY values:
    //   - rotation: width changes → different text wrap → heading Ys shift.
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
    // tocEntries is empty until onPageFinished runs the first extraction.
    val configuration = LocalConfiguration.current
    LaunchedEffect(
        configuration.orientation,
        textZoomFraction,
        appFont,
        guideThemeInject,
        webViewScale,
        bookmark.id
    ) {
        val wv = webViewRef.value ?: return@LaunchedEffect
        if (tocEntries.isEmpty()) return@LaunchedEffect
        delay(500L)
        extractAnchorClusterEntries(wv.context, wv) { entries ->
            if (entries.isNotEmpty()) tocEntries = entries
        }
    }

    // Long-press on a link in the guide surfaces a small action dialog.
    // Set by the WebView's OnLongClickListener when HitTestResult is a link.
    var pendingLinkLongPressUrl by remember(bookmark.id) { mutableStateOf<String?>(null) }

    // Long-press on an image (including images inside an <a> anchor — common
    // on Bulbapedia / MediaWiki, where the natural HitTestResult points at the
    // anchor href, not the image src). Set by the WebView's OnLongClickListener
    // after `requestImageRef` resolves the real img src.
    var pendingImageLongPressUrl by remember(bookmark.id) { mutableStateOf<String?>(null) }

    // Push the bookmark title to the vault-session top bar's doc switcher.
    LaunchedEffect(vaultId, bookmark.title) {
        mainViewModel?.setActiveDocTitle(vaultId, VaultTab.Guide, bookmark.title.ifBlank { null })
    }
    DisposableEffect(vaultId) {
        onDispose {
            mainViewModel?.setActiveDocTitle(vaultId, VaultTab.Guide, null)
        }
    }
    // True while the spliced landing script is still moving the page into
    // position, so a scroll read now describes the restore rather than the
    // reader. Set here the moment a landing is spliced (the script does not
    // start until DOMContentLoaded, and a dispose before that must not save
    // the blank document's 0) and maintained from then on by the script
    // itself, through the onScrollLandingChanged bridge below.
    val scrollLandingPending = remember(bookmark.id) { mutableStateOf(false) }
    // Backstop. The script clears the flag itself, and its own ceiling bounds
    // how long that can take, but if it never reports back - a JS error, a
    // WebView torn down mid-pass - this reader's position would silently stop
    // being saved for the rest of the session. Time out instead: the worst that
    // costs is one landing's worth of exposure, which is where we were before.
    LaunchedEffect(bookmark.id, scrollLandingPending.value) {
        if (!scrollLandingPending.value) return@LaunchedEffect
        delay(SCROLL_SETTLE_MAX_TICKS * SCROLL_SETTLE_TICK_MS + 2000L)
        scrollLandingPending.value = false
    }

    // Save scroll position when leaving the bookmark (back, tab switch,
    // viewer dispose) and on ON_PAUSE. Captured into the bookmark's
    // scrollRestoreData and applied on next open in the onPageFinished
    // restore block.
    //
    // ON_PAUSE as well as dispose, as the group viewer does: folding into
    // Always on Top pauses the activity without disposing anything, and the
    // panel's own copy of this viewer then reads the bookmark. With a
    // dispose-only save it read whatever was written the last time the guide
    // was left, which could be a session ago.
    fun persistScrollPosition() {
        val wv = webViewRef.value ?: return
        val scrollHeight = wv.verticalScrollRangePx()
        if (scrollHeight <= 0) return
        val ratio = (wv.scrollY.toDouble() / scrollHeight.toDouble()).coerceIn(0.0, 1.0)
        // Zero is a position like any other - the top of the guide - and
        // refusing it used to send a reader who had scrolled back up right
        // back down to where they had been. What must not be saved is a read
        // taken while the landing is still moving the page;
        // scrollLandingPending is the page's own word on that.
        if (!mayPersistScrollRatio(ratio, scrollLandingPending.value)) return
        // CRITICAL: do NOT use the composable's coroutineScope here — that
        // scope is cancelled the moment the DisposableEffect fires, killing
        // the Room write before it lands. Use a one-off CoroutineScope that
        // finishes its job and is then GC'd.
        val bookmarkId = bookmark.id
        val vault = vaultId
        kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.SupervisorJob() + Dispatchers.IO
        ).launch {
            savedTabRepository.updateBookmarkScrollRestore(
                vault,
                bookmarkId,
                com.twinlore.app.domain.ScrollRestoreData.ratioOnly(ratio)
            )
        }
    }
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, bookmark.id) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) persistScrollPosition()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            persistScrollPosition()
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Back from Always on Top. The panel hosted its own copy of this viewer
    // over the same bookmark and wrote where it got to, and this one, still
    // composed behind it, is where it was left. Read the bookmark again and
    // go there. Zoom rides along: the panel's Settings surface writes the
    // same column. A page still landing is left to land.
    LaunchedEffect(positionSyncKey) {
        if (positionSyncKey == 0) return@LaunchedEffect
        val wv = webViewRef.value ?: return@LaunchedEffect
        val fresh = withContext(Dispatchers.IO) { savedTabRepository.getTabById(bookmark.id) }
            ?: return@LaunchedEffect
        if (textZoomOverride == null) textZoomFraction = fresh.zoomLevel
        val ratio = fresh.scrollRestoreData?.scrollRatio ?: return@LaunchedEffect
        if (scrollLandingPending.value) return@LaunchedEffect
        val scrollHeight = wv.verticalScrollRangePx()
        if (scrollHeight <= 0) return@LaunchedEffect
        wv.scrollTo(0, (scrollHeight * ratio.coerceIn(0.0, 1.0)).toInt())
    }

    // Load the bookmark once per bookmark id. Branches on fileType:
    //   - TXT: convert raw text → HTML via TxtConversionCache + convertTxtToGameFaqHtml
    //     (same pipeline GuideScreen.loadTxtBookmark uses).
    //   - MHT / HTML: parse into /cache/single_guide_render/<bookmarkId>/ via the
    //     MHT cache manager, read index.html, splice palette prelude.
    LaunchedEffect(bookmark.id) {
        val wv = webViewRef.value ?: return@LaunchedEffect
        val resourceId = bookmark.resourceId ?: return@LaunchedEffect

        // Pull the latest scroll-restore ratio from Room. The bookmark arg
        // is the cached SavedTab from MainViewModel and doesn't refresh
        // after we persist scrollRestoreData on dispose. Splicing the
        // restore JS into the HTML below makes the browser repaint at the
        // saved position on first paint — no loading screen, no snap.
        val restoreRatio: Double? = withContext(Dispatchers.IO) {
            savedTabRepository.getTabById(bookmark.id)
                ?.scrollRestoreData
                ?.scrollRatio
                ?.takeIf { it > 0.0 }
        }
        val scrollRestoreJs = buildInlineScrollRestoreJs(restoreRatio)
        // Closed until the script says otherwise: everything between here and
        // its first report belongs to the restore, not to the reader.
        scrollLandingPending.value = scrollRestoreJs.isNotEmpty()

        // TXT branch: convert raw text → HTML via the same pipeline GuideScreen
        // uses (TxtConversionCache + convertTxtToGameFaqHtml). The resulting HTML
        // emits `<div class="toc-heading" id="…">` markers that Pass 1 of the
        // anchor-cluster extractor picks up.
        if (bookmark.fileType.equals("txt", ignoreCase = true)) {
            val rawContent: String? = withContext(Dispatchers.IO) {
                val uri = resolver.resolve(resourceId)
                val sourcePath: String? = when (uri?.scheme) {
                    "file" -> uri.path
                    "content" -> {
                        val tmp = java.io.File(context.cacheDir, "txt_source/single_${bookmark.id}.txt")
                        tmp.parentFile?.mkdirs()
                        val ok = context.contentResolver.openInputStream(uri)?.use { input ->
                            tmp.outputStream().use { input.copyTo(it) }
                            true
                        } ?: false
                        if (ok) tmp.absolutePath else null
                    }
                    else -> null
                }
                if (sourcePath == null) null else java.io.File(sourcePath).readText()
            }
            if (rawContent == null) {
                Toast.makeText(context, "Waiting for file to sync. It will appear when your sync app delivers it.", Toast.LENGTH_SHORT).show()
                return@LaunchedEffect
            }
            // Too large to render as one document → split into a memory-safe
            // page-group, replace this tab, and hand off to the group viewer.
            if (mainViewModel?.splitHugeTextGuideToGroup(vaultId, bookmark, rawContent) == true) {
                return@LaunchedEffect
            }
            val html: String = withContext(Dispatchers.IO) {
                txtConversionCache.readCachedHtml(bookmark.id)?.also { txtConversionCache.touch(bookmark.id) }
                    ?: com.twinlore.app.ui.screens.convertTxtToGameFaqHtml(rawContent)
                        .also { txtConversionCache.writeCachedHtml(bookmark.id, it) }
            }
            // TXT pages don't carry MHT assets, so the manifest stays empty
            // and the asset interceptor is a no-op for these loads.
            loadedAssetBaseDirState.value = null
            loadedAssetManifestState.value = emptyMap()
            val themedHtml = applyPalettePrelude(html, currentPaletteBgHex, currentPaletteFgHex, currentAppFont)
            // Reading-mode chrome strip is gated on the per-vault theme-inject
            // toggle. When the toggle is OFF at load, we skip the prelude
            // splice so the first paint is the full saved page; toggling ON
            // mid-view runs [buildReadingModeJs] via the theme LaunchedEffect.
            val themedAndStripped0 = if (currentGuideThemeInject)
                applyChromeStripPrelude(themedHtml, bookmark.url)
            else
                themedHtml
            // Mark the body raw before first paint so a guide opened with smart
            // formatting off never flashes the reflowed layout first.
            val themedAndStripped1 = com.twinlore.app.ui.screens.offline.applySmartFormatPrelude(
                themedAndStripped0, currentSmartFormatting
            )
            // And the text size, for the same reason: set from onPageFinished
            // alone, the page painted at its own size and then jumped.
            val themedAndStripped = com.twinlore.app.ui.screens.offline.applyTextZoomPrelude(
                themedAndStripped1, currentTextZoomFraction
            )
            // Splice anchor badge + marker highlight scripts so they execute
            // during HTML parsing → decorations appear with the first frame.
            // We take a snapshot of the markers list here; mid-session marker
            // changes are still handled by the existing LaunchedEffect(markers)
            // -> highlightScrollMarkerInPage path (idempotent via guards).
            // The uncloak is not among them: it runs from onPageFinished, once
            // that pass has had its turn too. See READER_CLOAK_ID.
            val markersAtLoad = viewModel?.markers?.value.orEmpty()
            val decorations = buildString {
                append(buildInternalAnchorBadgeJs(bookmark.url, currentHighlightHex))
                append("\n")
                append(buildScrollMarkerHighlightJs(markersAtLoad, currentHighlightHex, currentContrastHex))
                append("\n")
                append(buildInlineTocExtractorJs(context))
            }
            val withDecorations = spliceInlineDecorationsScript(themedAndStripped, decorations)
            val finalHtml = if (scrollRestoreJs.isNotEmpty())
                spliceScrollRestoreScript(withDecorations, scrollRestoreJs)
            else
                withDecorations
            wv.loadDataWithBaseURL("https://localhost.invalid/", finalHtml, "text/html", "UTF-8", null)
            return@LaunchedEffect
        }

        // MHT / HTML branch — existing logic.
        // sourceUrl: `bookmark.url` when the DB knows it, otherwise the URL the
        // archive itself recorded (see sourceUrlState above). Blank when neither
        // is available — the safe no-filter path.
        data class LoadResult(val html: String?, val isTextFaq: Boolean, val sourceUrl: String = "")

        // Set inside the IO block when an extracted text-FAQ is too large to render
        // as one document; handled (split → group) after the block.
        var splitTextCandidate: String? = null
        val loadResult: LoadResult = withContext(Dispatchers.IO) {
            val uri = resolver.resolve(resourceId)
            val bookmarkDir = java.io.File(
                context.cacheDir, "single_guide_render/${bookmark.id}"
            ).apply { mkdirs() }
            if (!mhtCacheManager.hasCachedResult(bookmarkDir)) {
                var tmpToDelete: java.io.File? = null
                val mhtFile: java.io.File? = when (uri?.scheme) {
                    "file" -> uri.path?.let { java.io.File(it) }
                    "content" -> {
                        val tmp = java.io.File(context.cacheDir, "mht_source/single_${bookmark.id}.mht")
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
                if (!parsed) return@withContext LoadResult(null, false)
            }
            mhtCacheManager.touchCache(bookmarkDir)
            val indexFile = java.io.File(bookmarkDir, "index.html")
            if (!indexFile.exists()) return@withContext LoadResult(null, false)

            // The DB's URL wins when it has one (an in-app browser save records
            // the exact page it captured). Only when it's blank do we fall back
            // to the URL the archive recorded for itself.
            val resolvedUrl = bookmark.url.ifBlank {
                mhtCacheManager.readSourceUrl(bookmarkDir).orEmpty()
            }

            // Text-FAQ check: if the saved HTML is actually a plain-text walkthrough
            // wrapped in HTML (GameFAQs / Neoseeker text FAQs etc.), convert it via
            // the same TxtConversionCache + convertTxtToGameFaqHtml pipeline the TXT
            // branch uses. Mirrors GuideScreen.loadGuideBookmark (line 1138-1158).
            val cachedConversion = txtConversionCache.readCachedHtml(bookmark.id)
            // A cached *capped* huge guide must NOT short-circuit here — fall through
            // so it gets split into a group instead of re-serving the capped page.
            if (cachedConversion != null && !cachedConversion.contains("Full-guide paging is coming")) {
                txtConversionCache.touch(bookmark.id)
                return@withContext LoadResult(cachedConversion, true, resolvedUrl)
            }
            if (com.twinlore.app.ui.screens.isTextFaqFile(indexFile)) {
                val textContent = com.twinlore.app.ui.screens.extractTextFaqFromFile(indexFile)
                if (textContent != null) {
                    if (com.twinlore.app.ui.screens.TextGuideChunker.shouldSplit(textContent)) {
                        splitTextCandidate = textContent
                        return@withContext LoadResult(null, true)
                    }
                    val converted = com.twinlore.app.ui.screens.convertTxtToGameFaqHtml(textContent)
                    txtConversionCache.writeCachedHtml(bookmark.id, converted)
                    return@withContext LoadResult(converted, true, resolvedUrl)
                }
            }
            LoadResult(indexFile.readText(Charsets.UTF_8), false, resolvedUrl)
        }
        // Publish before the splice below so the theme LaunchedEffect (keyed on
        // readingFilter) can re-apply the strip to an already-loaded document.
        if (loadResult.sourceUrl.isNotBlank()) sourceUrlState.value = loadResult.sourceUrl
        // Huge text-FAQ → split into a memory-safe page-group and hand off.
        splitTextCandidate?.let { txt ->
            if (mainViewModel?.splitHugeTextGuideToGroup(vaultId, bookmark, txt) == true) {
                return@LaunchedEffect
            }
        }
        val isTextFaqGuide = loadResult.isTextFaq
        // If a split was attempted but didn't take, fall back to the capped single
        // render (convertTxtToGameFaqHtml caps oversized text) so it still opens.
        val html = loadResult.html ?: splitTextCandidate?.let {
            com.twinlore.app.ui.screens.convertTxtToGameFaqHtml(it)
        }
        if (html == null) {
            Toast.makeText(context, "Waiting for file to sync. It will appear when your sync app delivers it.", Toast.LENGTH_SHORT).show()
            return@LaunchedEffect
        }
        val bookmarkDirForEntry = java.io.File(
            context.cacheDir, "single_guide_render/${bookmark.id}"
        )
        // For text-FAQ guides the converted HTML has no MHT assets — skip the
        // manifest load so the asset interceptor doesn't try to serve from a
        // non-existent base dir.
        if (isTextFaqGuide) {
            loadedAssetBaseDirState.value = null
            loadedAssetManifestState.value = emptyMap()
        } else {
            loadedAssetBaseDirState.value = bookmarkDirForEntry
            loadedAssetManifestState.value = runCatching {
                val manifestFile = java.io.File(bookmarkDirForEntry, "manifest.json")
                if (!manifestFile.exists()) emptyMap()
                else {
                    val obj = org.json.JSONObject(manifestFile.readText(Charsets.UTF_8))
                    val map = mutableMapOf<String, String>()
                    for (k in obj.keys()) map[k] = obj.optString(k)
                    map.toMap()
                }
            }.getOrDefault(emptyMap())
        }
        val themedHtml = applyPalettePrelude(html, currentPaletteBgHex, currentPaletteFgHex, currentAppFont)
        // Reading-mode chrome strip gated on the per-vault theme-inject
        // toggle (see TXT branch for rationale).
        val themedAndStripped0 = if (currentGuideThemeInject)
            applyChromeStripPrelude(themedHtml, loadResult.sourceUrl.ifBlank { bookmark.url })
        else
            themedHtml
        // Text-FAQ MHTs come through this branch too, so they need the same
        // pre-paint raw mark as the TXT branch above.
        val themedAndStripped1 = com.twinlore.app.ui.screens.offline.applySmartFormatPrelude(
            themedAndStripped0, currentSmartFormatting
        )
        // And the text size before the paint, as in the TXT branch.
        val themedAndStripped = com.twinlore.app.ui.screens.offline.applyTextZoomPrelude(
            themedAndStripped1, currentTextZoomFraction
        )
        // Splice anchor badge + marker highlight scripts so they execute
        // during HTML parsing → decorations appear with the first frame.
        // We take a snapshot of the markers list here; mid-session marker
        // changes are still handled by the existing LaunchedEffect(markers)
        // -> highlightScrollMarkerInPage path (idempotent via guards).
        // The uncloak runs from onPageFinished instead; see READER_CLOAK_ID.
        val markersAtLoad = viewModel?.markers?.value.orEmpty()
        val decorations = buildString {
            // Same recovered URL as the strip above: with an empty base every
            // absolute link classifies as external, so a scanned guide badged
            // its own in-archive links with the open-in-new arrow.
            append(buildInternalAnchorBadgeJs(loadResult.sourceUrl.ifBlank { bookmark.url }, currentHighlightHex))
            append("\n")
            append(buildScrollMarkerHighlightJs(markersAtLoad, currentHighlightHex, currentContrastHex))
            append("\n")
            append(buildInlineTocExtractorJs(context))
        }
        val withDecorations = spliceInlineDecorationsScript(themedAndStripped, decorations)
        // Scroll-restore JS spliced as a SEPARATE script block so a
        // synchronous exception in one of the decoration scripts can't
        // suppress it. Browser runs it during DOMContentLoaded so the
        // first paint is already at the saved position.
        val finalHtml = if (scrollRestoreJs.isNotEmpty())
            spliceScrollRestoreScript(withDecorations, scrollRestoreJs)
        else
            withDecorations
        wv.loadDataWithBaseURL("https://localhost.invalid/", finalHtml, "text/html", "UTF-8", null)
    }

    // Snapshot label is the active chapter name. The lastAnchorLabel
    // refinement felt wrong in practice — chapter names are the
    // intuitive grouping. Reads activeChapterIndex + tocEntries
    // directly (delegate-backed, so the captured-closure closure sees
    // live values).
    fun snapshotHistory(): Pair<Int, String?>? {
        val y = webViewRef.value?.scrollY ?: return null
        val idx = activeChapterIndex
        val entries = tocEntries
        val label = idx?.let { entries.getOrNull(it)?.label }
            ?.takeIf { it.isNotBlank() }
            ?: entries.firstOrNull()?.label?.takeIf { it.isNotBlank() }
        return y to label
    }

    fun pushHistoryBeforeJump() {
        val snap = snapshotHistory() ?: return
        val last = backHistory.lastOrNull()
        if (last != null && last.first == snap.first) {
            if (forwardHistory.isNotEmpty()) forwardHistory = emptyList()
            return
        }
        backHistory = backHistory + snap
        if (forwardHistory.isNotEmpty()) forwardHistory = emptyList()
    }

    fun applyHistoryFrame(target: Pair<Int, String?>) {
        webViewRef.value?.scrollTo(0, target.first)
    }

    fun navigateHistoryBack() {
        val target = backHistory.lastOrNull() ?: return
        val current = snapshotHistory()
        backHistory = backHistory.dropLast(1)
        if (current != null) forwardHistory = forwardHistory + current
        applyHistoryFrame(target)
    }

    fun navigateHistoryForward() {
        val target = forwardHistory.lastOrNull() ?: return
        val current = snapshotHistory()
        forwardHistory = forwardHistory.dropLast(1)
        if (current != null) backHistory = backHistory + current
        applyHistoryFrame(target)
    }

    // Jump to entry by index.
    //
    // Scroll via JS `element.scrollIntoView({ block: 'start' })`. The browser
    // handles visual viewport vs layout viewport, pinch-zoom scale, sticky
    // headers and rounding correctly — every standard reader (Wikipedia
    // mobile, Pocket, Confluence, GitHub anchor links) uses this primitive
    // for the same reason. The Kotlin `wv.scrollTo(target × density × scale)`
    // approach we used to have stacked badly with `wv.zoomBy` (the user's
    // pinch state) and lost a CSS-px to rounding, so the picker stuck on
    // the previous chapter.
    fun jumpToChapter(index: Int) {
        val entry = tocEntries.getOrNull(index) ?: return
        val wv = webViewRef.value ?: return
        val safeFragment = entry.fragment
            .replace("\\", "\\\\")
            .replace("'", "\\'")
        val highlightHex = currentHighlightHex
        wv.evaluateJavascript(
            """
            (function() {
                try {
                    var frag = '$safeFragment';
                    var el = document.getElementById(frag) ||
                             document.getElementById('user-content-' + frag.toLowerCase()) ||
                             document.querySelector('[name="' + frag.replace(/"/g, '\\\\"') + '"]');
                    if (!el) return;
                    // 80-CSS-px breathing room above the heading. The browser's
                    // own scrollIntoView honours scroll-margin-top, so this
                    // composes with the visual viewport / pinch state correctly.
                    // Picker's chapterPickSlopCss is 100 (> 80) so the chapter
                    // still resolves correctly post-scroll.
                    el.style.scrollMarginTop = '80px';
                    el.scrollIntoView({ block: 'start', behavior: 'auto' });
                    var accent = '$highlightHex';
                    // Retarget: if the matched element is a structural wrapper
                    // (Steam Community Guides set id="<N>" on the entire section
                    // div), flash the first heading descendant instead so we
                    // don't flatten the chapter body into a single text span.
                    if (!/^H[1-6]$/.test(el.tagName)) {
                        var headingChild = el.querySelector('h1, h2, h3, h4, h5, h6');
                        if (headingChild) el = headingChild;
                    }
                    var hasOwnText = el.offsetHeight > 0 && (el.textContent || '').trim().length > 0;
                    // Safety: if the target text is too long, scroll only.
                    // Heading anchors are typically < 100 chars; anything bigger
                    // is almost certainly a wrapper we failed to retarget out of.
                    if (hasOwnText && (el.textContent || '').length > 300) return;
                    if (hasOwnText) {
                        // Case B: visible heading element. Save original innerHTML,
                        // replace with a coloured span, restore after the fade.
                        var originalHtml = el.innerHTML;
                        var originalText = (el.textContent || '');
                        var hl = document.createElement('span');
                        hl.textContent = originalText;
                        hl.style.cssText = 'color:' + accent + ' !important;transition:color 1s ease-out';
                        el.innerHTML = '';
                        el.appendChild(hl);
                        setTimeout(function() {
                            hl.style.setProperty('color', 'inherit', 'important');
                        }, 100);
                        setTimeout(function() {
                            try { el.innerHTML = originalHtml; } catch (e) {}
                        }, 1500);
                    } else {
                        // Case A: invisible TXT toc-heading span. Split the next
                        // text node, take its first line, insert a coloured span
                        // between the anchor and the rest — the OLD pattern from
                        // GuideScreen.kt TocDrawerOverlay onEntryClick.
                        var next = el.nextSibling;
                        if (!next || next.nodeType !== 3) return;
                        var text = next.textContent;
                        var nl = text.indexOf('\n');
                        var first = nl >= 0 ? text.substring(0, nl) : text;
                        var rest = nl >= 0 ? text.substring(nl) : '';
                        if (first.trim().length === 0) return;
                        var hl = document.createElement('span');
                        hl.textContent = first;
                        hl.style.cssText = 'color:' + accent + ' !important;transition:color 1s ease-out';
                        next.textContent = rest;
                        el.after(hl);
                        setTimeout(function() {
                            hl.style.setProperty('color', 'inherit', 'important');
                        }, 100);
                        setTimeout(function() {
                            try {
                                var parent = hl.parentNode;
                                if (parent) {
                                    hl.replaceWith(document.createTextNode(first));
                                    parent.normalize();
                                }
                            } catch (e) {}
                        }, 1500);
                    }
                } catch (e) { /* swallow */ }
            })();
            """.trimIndent(),
            null
        )
    }

    // Latest-value snapshots for the chapter-nav buttons. Without
    // rememberUpdatedState the captured lambdas might close over a stale
    // activeChapterIndex if SingleGuideTopBar skips a recomposition — the
    // user reported Prev/Next not following the scroll-driven chapter
    // change, which this pattern eliminates.
    val activeChapterIndexRef by rememberUpdatedState(activeChapterIndex)
    val tocEntriesRef by rememberUpdatedState(tocEntries)

    // Suppresses onChromeHide for ~1s after a programmatic chapter scroll so
    // the top bar stays visible while the WebView animates to the new heading.
    var suppressChromeHideUntil by remember(bookmark.id) { mutableStateOf(0L) }

    val goPrevChapter: () -> Unit = {
        val cur = activeChapterIndexRef ?: 0
        if (cur > 0) {
            suppressChromeHideUntil = System.currentTimeMillis() + 1000L
            jumpToChapter(cur - 1)
        }
    }

    val goNextChapter: () -> Unit = {
        val cur = activeChapterIndexRef
        val next = if (cur == null) 0 else cur + 1
        if (next < tocEntriesRef.size) {
            suppressChromeHideUntil = System.currentTimeMillis() + 1000L
            jumpToChapter(next)
        }
    }

    val showFindRef by rememberUpdatedState(showFind)
    fun closeFind() {
        webViewRef.value?.clearMatches()
        showFind = false
        findQuery = ""
        findMatchCount = 0
        findCurrentIndex = 0
        findMatchesFound = null
    }

    Box(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    GuideWebView(ctx).apply {
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
                            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            // A file: document must not be able to fetch sibling files.
                            // Inert while this viewer loads through loadDataWithBaseURL
                            // with an https base, but live the moment anything here
                            // loads a file: URL — off by default, like the guide WebView.
                            @Suppress("DEPRECATION") setAllowFileAccessFromFileURLs(false)
                            @Suppress("DEPRECATION") setAllowUniversalAccessFromFileURLs(false)
                            useWideViewPort = true
                            loadWithOverviewMode = true
                            builtInZoomControls = true
                            displayZoomControls = false
                            setSupportZoom(true)
                            layoutAlgorithm = WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING
                        }
                        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
                            WebSettingsCompat.setAlgorithmicDarkeningAllowed(this.settings, palette.isDark)
                        }
                        // Image-tap zoom bridge — same as page-group. Resolves the
                        // tapped <img>'s src against the current bookmark's MHT
                        // manifest so the viewer loads the on-disk extracted file
                        // rather than re-fetching over the network.
                        // A tap on a marker's highlight. The highlight script
                        // reports its index into the markers it was given,
                        // which are the view model's, in order.
                        if (!capabilities.subWindows) {
                            addJavascriptInterface(
                                object {
                                    @android.webkit.JavascriptInterface
                                    fun onScrollMarkerHighlightClicked(markerIndex: Int) {
                                        Handler(Looper.getMainLooper()).post {
                                            viewModel?.markers?.value?.getOrNull(markerIndex)?.let {
                                                pendingDeleteMarkerId = it.id
                                            }
                                        }
                                    }
                                },
                                "Android",
                            )
                        }
                        addJavascriptInterface(
                            object {
                                @android.webkit.JavascriptInterface
                                fun onGuideImageTap(srcUrl: String) {
                                    val baseDir = loadedAssetBaseDirState.value
                                    val manifest = loadedAssetManifestState.value
                                    val resolved: String? = manifest[srcUrl]?.let { rel ->
                                        val file = java.io.File(baseDir, rel)
                                        if (file.exists()) "file://${file.absolutePath}" else null
                                    }
                                    Handler(Looper.getMainLooper()).post {
                                        guideImageZoomModel = resolved ?: srcUrl
                                    }
                                }

                                @android.webkit.JavascriptInterface
                                fun onScrollLandingChanged(active: Boolean) {
                                    // Posted by the spliced landing script when
                                    // it starts moving the page and when it
                                    // stops. While it is active, the WebView's
                                    // scroll offset is the restore's, so the
                                    // dispose-time save leaves the stored
                                    // position alone.
                                    Handler(Looper.getMainLooper()).post {
                                        scrollLandingPending.value = active
                                    }
                                }

                                @android.webkit.JavascriptInterface
                                fun onTocReady(json: String) {
                                    // Posted by the inline pre-load extractor.
                                    // Parse on main thread and write into tocEntries;
                                    // the post-load extractAnchorClusterEntries calls
                                    // stay as a safety net but only overwrite with
                                    // non-empty results (see applyToc guard below).
                                    Handler(Looper.getMainLooper()).post {
                                        val parsed = parseAnchorClusterJson(json)
                                        if (parsed.isNotEmpty()) {
                                            tocEntries = parsed
                                            if (activeChapterIndex == null) {
                                                activeChapterIndex = 0
                                            }
                                        }
                                    }
                                }
                            },
                            "androidInterface"
                        )
                        // Long-press on a link or image: intercept via HitTestResult
                        // and surface our own action dialog instead of the WebView's
                        // built-in "Open / Copy / Share" popup.
                        //
                        // SRC_IMAGE_ANCHOR_TYPE (image inside an anchor) is
                        // host-discriminated via [isExternalAnchor]:
                        //   - same host  → image dialog (MediaWiki / Bulbapedia
                        //                  File: thumbs, OmegaMetroid sidebar
                        //                  tiles — the anchor is decoration)
                        //   - different  → link dialog (PayPal donate buttons
                        //                  after the GameFAQs tipjar rewrite,
                        //                  sponsored banners — the anchor IS
                        //                  the meaningful action)
                        // Bare IMAGE_TYPE always goes to the image dialog.
                        // requestImageRef resolves <img>.src — `extra` would
                        // give us the wrapping anchor's href which is the
                        // wrong URL for "save image".
                        setOnLongClickListener { v ->
                            val wv = v as? android.webkit.WebView ?: return@setOnLongClickListener false
                            val hit = wv.hitTestResult
                            val type = hit?.type ?: android.webkit.WebView.HitTestResult.UNKNOWN_TYPE
                            when (type) {
                                android.webkit.WebView.HitTestResult.IMAGE_TYPE -> {
                                    // Bare image — no wrapping anchor.
                                    // requestImageRef resolves <img>.src
                                    // even for cross-origin srcs MHT-backed
                                    // via cid:.
                                    val handler = Handler(Looper.getMainLooper()) { msg ->
                                        val imageUrl = msg.data.getString("url")
                                        if (!imageUrl.isNullOrBlank()) {
                                            pendingImageLongPressUrl = imageUrl
                                        }
                                        true
                                    }
                                    wv.requestImageRef(handler.obtainMessage())
                                    true
                                }
                                android.webkit.WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> {
                                    // Image wrapped in an anchor. Two
                                    // disjoint UX cases:
                                    //   - Same host (MediaWiki File: thumbs,
                                    //     OmegaMetroid sidebar tiles): the
                                    //     wrapping anchor is decoration —
                                    //     surface the image action dialog.
                                    //   - External host (PayPal donate
                                    //     buttons after the GameFAQs
                                    //     tipjar rewrite, sponsored banners):
                                    //     the anchor IS the meaningful
                                    //     action — surface the link dialog.
                                    val anchorHref = hit?.extra
                                    if (isExternalAnchor(bookmark.url, anchorHref)) {
                                        val href = anchorHref!!
                                        Handler(Looper.getMainLooper()).post {
                                            pendingLinkLongPressUrl = href
                                        }
                                        true
                                    } else {
                                        val handler = Handler(Looper.getMainLooper()) { msg ->
                                            val imageUrl = msg.data.getString("url")
                                            if (!imageUrl.isNullOrBlank()) {
                                                pendingImageLongPressUrl = imageUrl
                                            }
                                            true
                                        }
                                        wv.requestImageRef(handler.obtainMessage())
                                        true
                                    }
                                }
                                android.webkit.WebView.HitTestResult.SRC_ANCHOR_TYPE -> {
                                    val linkUrl = hit?.extra
                                    if (linkUrl.isNullOrBlank()) {
                                        false
                                    } else if (isLocalAnchor(bookmark.url, linkUrl)) {
                                        // In-doc anchor: the href is a scroll
                                        // target, not a destination worth a
                                        // link dialog — copy the link's TEXT
                                        // instead (requestFocusNodeHref
                                        // resolves it; HitTestResult only
                                        // carries the href).
                                        copyAnchorTextToClipboard(wv)
                                        true
                                    } else {
                                        Handler(Looper.getMainLooper()).post {
                                            pendingLinkLongPressUrl = linkUrl
                                        }
                                        true
                                    }
                                }
                                else -> false
                            }
                        }
                        isLongClickable = true
                        webChromeClient = buildOfflineWebChromeClient(ctx, webViewRef)
                        applyOfflineChromeGestureListeners(
                            webView = this,
                            topRevealThresholdPx = topRevealThresholdPx,
                            onTapToggle = {
                                // If Find is open, a body tap dismisses it (matches
                                // the old GuideScreen Find UX). Otherwise toggle chrome.
                                if (showFindRef) closeFind() else onChromeToggle()
                            },
                            onScrollDownHide = {
                                if (System.currentTimeMillis() >= suppressChromeHideUntil) {
                                    onChromeHide()
                                }
                            },
                            onTopReveal = onChromeReveal,
                            onScrollY = { scrollY ->
                                liveScrollY = scrollY
                                // Scroll-driven chapter index. Both sides in CSS px:
                                //   targetScrollY comes from the extractor in CSS px;
                                //   wv.scrollY is CSS-px × scale → divide by the
                                //   current pinch-zoom scale to get the same unit
                                //   (Chromium WebView normalises this; density is
                                //   NOT a factor — empirically verified, see comments
                                //   below at the scrollYCss computation).
                                //   Standard pick rule: largest Y ≤ scrollYCss + slop.
                                //   End-of-doc fallback: when the page can't scroll
                                //   far enough to put the target heading at the top
                                //   (zoom-out near end), prefer the first heading
                                //   visible in the viewport. Falls back to the FIRST
                                //   entry when nothing qualifies so the history strip
                                //   + top-bar chapter label never go null
                                //   mid-document.
                                //
                                //   Scale handling: read view.scale live (per scroll
                                //   event) rather than trusting the onScaleChanged
                                //   callback alone, because on some WebView builds it
                                //   doesn't fire reliably for pinch gestures. Updating
                                //   webViewScale state from here also keys the
                                //   re-extract LaunchedEffect — TEXT_AUTOSIZING can
                                //   reflow on zoom, so a scale change is treated as a
                                //   potential layout-invalidating event.
                                val wv = webViewRef.value
                                val liveScale = (wv?.scale ?: 1f).coerceAtLeast(0.01f)
                                if (kotlin.math.abs(liveScale - webViewScale) > 0.05f) {
                                    webViewScale = liveScale
                                }
                                val entries = tocEntries
                                if (entries.isEmpty()) return@applyOfflineChromeGestureListeners
                                // wv.scrollY (Chromium WebView) is in CSS-px × scale,
                                // NOT density × CSS × scale. The old `/ (density * scale)`
                                // over-divided and pinned the picker to a wrong chapter at
                                // any zoom != 1.0 — see PageGroupViewerScreen for the
                                // empirical verification (logs 06-07 18:44).
                                val scrollYCss = (scrollY / liveScale).toInt()
                                val viewportHeightCss = ((wv?.height ?: 0) / liveScale)
                                    .toInt()
                                    .coerceAtLeast(1)
                                val viewportBottom = scrollYCss + viewportHeightCss
                                val threshold = scrollYCss + chapterPickSlopCss
                                var bestIdx: Int? = null
                                var bestY = Int.MIN_VALUE
                                var firstInViewportIdx: Int? = null
                                var firstInViewportY = Int.MAX_VALUE
                                for (i in entries.indices) {
                                    val y = entries[i].targetScrollY
                                    if (y <= threshold && y > bestY) {
                                        bestY = y; bestIdx = i
                                    }
                                    if (y in scrollYCss..viewportBottom && y < firstInViewportY) {
                                        firstInViewportY = y; firstInViewportIdx = i
                                    }
                                }
                                // End-of-document fallback: when the page can't scroll
                                // far enough to put the target heading at the top
                                // (happens at zoom-out near the end), the standard
                                // "largest Y ≤ scrollY + slop" rule picks a heading
                                // above the visible viewport instead. Prefer the
                                // first heading currently visible when the standard
                                // pick is strictly above the viewport top.
                                val picked = if (
                                    firstInViewportIdx != null &&
                                    (bestIdx == null || entries[bestIdx].targetScrollY < scrollYCss)
                                ) {
                                    firstInViewportIdx
                                } else {
                                    bestIdx ?: 0
                                }
                                if (picked != activeChapterIndex) {
                                    activeChapterIndex = picked
                                }
                            }
                        )
                        val interceptor = buildMhtAssetInterceptor(loadedAssetManifestState, loadedAssetBaseDirState)
                        webViewClient = object : WebViewClient() {
                            override fun shouldInterceptRequest(
                                view: WebView?,
                                request: WebResourceRequest?
                            ) = request?.url?.toString()?.let(interceptor)
                                ?: super.shouldInterceptRequest(view, request)

                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean {
                                val url = request?.url ?: return false
                                val urlStr = url.toString()
                                val scheme = url.scheme?.lowercase()
                                val ctx = view?.context
                                // cid: is MHTML's own internal reference: a saved
                                // page's frames and parts address each other with
                                // it, and the WebView resolves them itself. It is
                                // not a link anybody tapped, and handing it to
                                // ACTION_VIEW finds no handler.
                                val webViewNative = scheme == "http" || scheme == "https" ||
                                    scheme == "file" || scheme == "data" ||
                                    scheme == "about" || scheme == "javascript" ||
                                    scheme == "blob" || scheme == "cid"
                                // Everything below this line that is neither an
                                // in-document jump nor a local document ends up
                                // outside this reader, and in a window with no
                                // activity behind it there is nowhere outside to
                                // go: startActivity drops a player out of a
                                // running game, and the Browser-tab branch
                                // reaches a null mainViewModel and does nothing
                                // at all. The host is told and the load is
                                // consumed instead.
                                if (!capabilities.externalNavigation &&
                                    request.isForMainFrame &&
                                    !isInDocumentJump(urlStr, bookmark.url, url)
                                ) {
                                    val localDocument = scheme == "file" || scheme == "data" ||
                                        scheme == "about" || scheme == "blob" || scheme == "cid"
                                    if (!localDocument) {
                                        onExternalLink(urlStr)
                                        return true
                                    }
                                }
                                if (scheme == "intent" && ctx != null) {
                                    runCatching {
                                        val intent = Intent.parseUri(urlStr, Intent.URI_INTENT_SCHEME)
                                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        intent.component = null
                                        intent.selector = null
                                        ctx.startActivity(intent)
                                    }
                                    return true
                                }
                                if (!webViewNative && ctx != null) {
                                    runCatching {
                                        ctx.startActivity(
                                            Intent(Intent.ACTION_VIEW, url)
                                                .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                                        )
                                    }
                                    return true
                                }
                                // `request` is smart-cast non-null after the `url = request?.url
                                // ?: return false` guard above; matches the page-group viewer.
                                if (!request.isForMainFrame) return false

                                val host = url.host?.lowercase().orEmpty()
                                val externalHost = host.endsWith("youtube.com") ||
                                    host == "youtu.be" || host.endsWith("twitch.tv") ||
                                    host.endsWith("vimeo.com")
                                if (externalHost && ctx != null) {
                                    runCatching {
                                        ctx.startActivity(
                                            Intent(Intent.ACTION_VIEW, url)
                                                .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                                        )
                                    }
                                    return true
                                }

                                // Same-page anchor: either a full URL that points back at
                                // the current bookmark, or a bare "#frag" link. In both
                                // cases scroll to the target with an 80 CSS-px breathing
                                // gap above the heading so it doesn't sit flush against
                                // the top bar (matches the page-group viewer's behaviour).
                                val bookmarkBase = bookmark.url
                                    .substringBefore("#").substringBefore("?")
                                val sameBookmarkMatch = urlStr.contains("#") &&
                                    bookmarkBase.isNotBlank() &&
                                    urlStr.substringBefore("#").substringBefore("?") == bookmarkBase
                                val bareFragment = urlStr.startsWith("#") ||
                                    (host == "localhost.invalid" && url.fragment?.isNotEmpty() == true)
                                if (sameBookmarkMatch || bareFragment) {
                                    val rawFragment = if (urlStr.startsWith("#"))
                                        urlStr.substring(1)
                                    else
                                        urlStr.substringAfter("#", "")
                                    if (rawFragment.isNotEmpty() && view != null) {
                                        pushHistoryBeforeJump()
                                        val escaped = rawFragment
                                            .replace("\\", "\\\\")
                                            .replace("'", "\\'")
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

                                // External http(s) link → Browser tab.
                                Handler(Looper.getMainLooper()).post {
                                    mainViewModel?.setPendingUrlForBrowserTab(vaultId, urlStr)
                                    mainViewModel?.setVaultSessionTab(vaultId, VaultTab.Browser)
                                }
                                return true
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                if (view == null) return
                                // Content is on screen now — drop the prepare spinner.
                                isPreparingGuide = false
                                // Scroll restore is handled by the inline JS spliced
                                // into the HTML at load time — runs during page parse
                                // so the first paint is already at the saved position.
                                // No post-load scroll work or loading overlay needed.
                                // Image-tap click handler (bridges to androidInterface.onGuideImageTap).
                                // Idempotent via window._singleGuideImgTap.
                                //
                                // Walks up the DOM looking for an "image-only wrapper" —
                                // an ancestor that contains exactly one <img> and has no
                                // non-whitespace text content. Catches MediaWiki sprite
                                // links (`<a><img/></a>`) and MW thumbnail figures
                                // (`<figure><a class="mw-file-description"><img/></a></figure>`)
                                // without misfiring on text taps. The previous version
                                // used `querySelector('img')` which would match the FIRST
                                // <img> anywhere in the ancestor tree — tapping a body
                                // paragraph walked up to `.post-content` and opened
                                // whatever the top chapter banner was.
                                view.evaluateJavascript(
                                    """
                                    if (window._singleGuideImgTap) {
                                        document.removeEventListener('click', window._singleGuideImgTap, true);
                                    }
                                    window._singleGuideImgTap = function(e) {
                                        var t = e.target;
                                        if (!t) return;
                                        var img = null;
                                        if (t.tagName === 'IMG') {
                                            img = t;
                                        } else {
                                            var node = t;
                                            for (var i = 0; i < 3 && node; i++) {
                                                if (node.querySelectorAll) {
                                                    var imgs = node.querySelectorAll('img');
                                                    if (imgs.length === 1) {
                                                        var text = (node.textContent || '').replace(/\s| /g, '');
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
                                    document.addEventListener('click', window._singleGuideImgTap, true);
                                    """.trimIndent(),
                                    null
                                )
                                // Selection rect → SelectionRectBridge.
                                view.evaluateJavascript(SELECTION_RECT_JS, null)
                                // Cosmetic element-hide layer — same idiom the live
                                // browser uses to collapse leftover ad-placeholders.
                                if (privacyPreferences.enabled) {
                                    view.evaluateJavascript(
                                        com.twinlore.app.data.privacy.CosmeticFilters.injectionJs(),
                                        null
                                    )
                                }
                                // Iframe gate (online/offline).
                                val online = networkConnectivityManager.isNetworkAvailable()
                                view.evaluateJavascript(buildIframeGateJs(online), null)
                                // Palette CSS re-injection.
                                if (currentGuideThemeInject) {
                                    view.evaluateJavascript(
                                        buildPaletteCssJs(currentPaletteBgHex, currentPaletteFgHex, currentAppFont),
                                        null
                                    )
                                } else {
                                    view.evaluateJavascript(REMOVE_PALETTE_CSS_JS, null)
                                }
                                // Does this document have runs / scroll-blocks for
                                // Smart Formatting to act on?
                                view.evaluateJavascript(
                                    com.twinlore.app.ui.screens.offline.DETECT_TEXT_GUIDE_JS
                                ) { result -> isTextGuide = result == "true" }
                                // Font-size zoom lives on the document, so a
                                // freshly loaded page needs it re-applied
                                // (settings.textZoom used to persist for free).
                                // The load-time prelude already set this size,
                                // so on a fresh page this writes the same value
                                // and moves nothing.
                                view.evaluateJavascript(
                                    com.twinlore.app.ui.screens.offline
                                        .buildTextZoomJs(currentTextZoomFraction),
                                    null
                                )
                                // Reveal the page now, not at parse time: the
                                // cosmetic filter and the size above both reflow
                                // the document, and a page revealed before them
                                // was watched changing. Everything past this
                                // point only decorates. See READER_CLOAK_ID.
                                view.evaluateJavascript(
                                    com.twinlore.app.ui.screens.offline.READER_UNCLOAK_JS,
                                    null
                                )
                                // Anchor decoration: accent-colored in-doc
                                // links + open_in_new badge on external ones.
                                view.evaluateJavascript(
                                    buildInternalAnchorBadgeJs(bookmark.url, currentHighlightHex),
                                    null
                                )
                                // Scroll-marker highlights.
                                highlightScrollMarkerInPage(
                                    webView = view,
                                    markers = markers,
                                    focusedIndex = null,
                                    focusColorHex = currentHighlightHex,
                                    focusContrastHex = currentContrastHex
                                )
                                // ToC extraction. TXT-converted pages reshape after
                                // initial layout — convertTxtToGameFaqHtml scales
                                // `.scroll-block` divs via transform: scale(N) once
                                // fitBlocks() runs, and again whenever ResizeObserver
                                // re-fires (fonts / images). A single one-shot
                                // extraction at 900ms captured pre-fit Ys; the scroll
                                // listener then picked chapters too far ahead because
                                // the stored Ys were smaller than the post-reflow
                                // positions. Two extractions: 1500ms (initial fit
                                // settles) + 3500ms (catches late reflows). The
                                // activeChapterIndex preservation below keeps the
                                // user's current chapter sticky across the refresh.
                                val applyToc: (List<SingleGuideTocEntry>) -> Unit = { entries ->
                                    if (entries.isNotEmpty()) {
                                        tocEntries = entries
                                        if (activeChapterIndex == null) {
                                            activeChapterIndex = 0
                                        }
                                    }
                                    // Empty result from late extraction — keep the
                                    // previous (likely good) pre-load data.
                                }
                                view.postDelayed({
                                    extractAnchorClusterEntries(view.context, view, applyToc)
                                }, 2000L)
                                view.postDelayed({
                                    extractAnchorClusterEntries(view.context, view, applyToc)
                                }, 4000L)
                                // Pending marker scroll (find→tap, etc.).
                                val pm = viewModel?.consumePendingScrollToMarker()
                                if (pm != null) {
                                    scrollToMarkerByText(
                                        webView = view,
                                        label = pm.label,
                                        fallbackScrollY = pm.fallbackScrollY,
                                        occurrenceIndex = pm.occurrenceIndex,
                                        flashFocusHex = pm.flashHex
                                    ) { /* resolved Y not needed */ }
                                }
                                // Pending find query.
                                val fq = pendingFindQuery.value
                                if (fq != null) {
                                    view.postDelayed({ view.findAllAsync(fq) }, 100)
                                    pendingFindQuery.value = null
                                }
                                // Everything above this line moves the document
                                // out from under the resume landing the spliced
                                // scroll-restore script computed: the text-size
                                // font-size alone costs a converted guide about
                                // a sixth of its height, and the refit it
                                // triggers moves it again. Re-measure now that
                                // they have all been issued, or the reader comes
                                // back `ratio × heightLost` too far down — the
                                // deeper they were, the worse it reads.
                                //
                                // Skipped when the load was aimed somewhere
                                // specific: a marker tap or a find result is the
                                // user asking for a place, and it outranks where
                                // they last left off.
                                if (pm == null && fq == null) {
                                    view.evaluateJavascript(SETTLE_SCROLL_REARM_JS, null)
                                }
                            }

                            // Track the WebView's pinch-zoom scale so the
                            // scroll listener can convert WebView.scrollY
                            // (device px, scaled) back to CSS px. The
                            // scroll listener also reads view.scale live
                            // because this callback is unreliable on some
                            // WebView builds.
                            override fun onScaleChanged(
                                view: WebView?,
                                oldScale: Float,
                                newScale: Float
                            ) {
                                super.onScaleChanged(view, oldScale, newScale)
                                if (newScale > 0f) webViewScale = newScale
                            }
                        }
                        setFindListener { activeMatch, numMatches, isDoneCounting ->
                            if (isDoneCounting) {
                                findIsSearching = false
                                findMatchCount = numMatches
                                findCurrentIndex = if (numMatches > 0) activeMatch + 1 else 0
                                findMatchesFound = numMatches > 0
                            }
                        }
                        webViewRef.value = this
                    }
                },
                update = { wv ->
                    val ctxLocal = wv.context
                    // A hold is the WebView's own: it selects, with handles to
                    // adjust, and opens the custom menu below. That holds in the
                    // fold panel too, measured on the Thor (see
                    // GuideViewerCapabilities); a long-press handler there once
                    // took the hold first and so blocked selection entirely.
                    wv.setOnLongClickListener(null)
                    if (!capabilities.subWindows) {
                        (wv as? GuideWebView)?.onSelectionModeChanged = { panelSelecting = it }
                    }
                    wv.isLongClickable = capabilities.textSelection
                    wv.setCustomSelectionMenuActions(
                        if (!capabilities.textSelection) null else
                        GuideWebView.CustomSelectionActions(
                            showSaveMarker = bookmark.resourceId != null,
                            onSaveMarker = { text, occurrenceIndex ->
                                wv.evaluateJavascript(
                                    "(function(){return Math.round(window.pageYOffset||0);})()"
                                ) { result ->
                                    val y = result?.toIntOrNull() ?: 0
                                    coroutineScope.launch {
                                        savedTabRepository.addScrollMarker(
                                            vaultId = vaultId,
                                            bookmarkId = bookmark.id,
                                            label = text,
                                            scrollY = y,
                                            occurrenceIndex = occurrenceIndex
                                        )
                                        android.widget.Toast.makeText(
                                            ctxLocal,
                                            "Scroll marker saved: " + text.take(30) +
                                                if (text.length > 30) "…" else "",
                                            android.widget.Toast.LENGTH_SHORT
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
                                if (text.isNotBlank()) {
                                    val q = text.trim()
                                    val url = "https://www.youtube.com/results?search_query=" + android.net.Uri.encode(q)
                                    mainViewModel?.setPendingUrlForBrowserTab(vaultId, url)
                                    mainViewModel?.setVaultSessionTab(vaultId, com.twinlore.app.domain.VaultTab.Browser)
                                }
                            },
                            onSearchOnline = { text ->
                                wv.evaluateJavascript("window.getSelection().removeAllRanges()", null)
                                if (text.isNotBlank()) {
                                    val q = text.trim()
                                    val url = "https://www.google.com/search?q=" + android.net.Uri.encode(q)
                                    mainViewModel?.setPendingUrlForBrowserTab(vaultId, url)
                                    mainViewModel?.setVaultSessionTab(vaultId, com.twinlore.app.domain.VaultTab.Browser)
                                }
                            },
                            copyAndMarkerOnly = !capabilities.subWindows,
                            onCopy = { text ->
                                val clipboard = ctxLocal.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("selection", text))
                                wv.evaluateJavascript("window.getSelection().removeAllRanges()", null)
                                // A toast from the panel lands on the game's
                                // display; the pill shows on the panel itself.
                                if (capabilities.subWindows) {
                                    android.widget.Toast.makeText(ctxLocal, "Copied to clipboard", android.widget.Toast.LENGTH_SHORT).show()
                                } else {
                                    copiedPillTrigger++
                                }
                            }
                        )
                    )
                }
            )

            if (chromeVisible && !showFind && !showTocView) {
                Box(modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth()) {
                    SingleGuideTopBar(
                        bookmarkTitle = bookmark.title,
                        activeChapterLabel = activeChapterLabel,
                        activeChapterIndex = activeChapterIndex,
                        totalChapters = tocEntries.size,
                        canPrev = (activeChapterIndex ?: 0) > 0 && tocEntries.isNotEmpty(),
                        canNext = activeChapterIndex != null &&
                            activeChapterIndex!! < tocEntries.size - 1,
                        onBack = onBack,
                        onPrev = goPrevChapter,
                        onNext = goNextChapter,
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
        }
        if (showFind) {
            FindOnPageUI(
                query = findQuery,
                onQueryChange = { findQuery = it },
                onSearch = {
                    val wv = webViewRef.value
                    if (wv != null) {
                        findIsSearching = true
                        findSearchTrigger++
                        wv.findAllAsync(findQuery)
                    }
                },
                onClose = { closeFind() },
                onSaveAsScrollMarker = {
                    // Save-as-marker from find: full marker-from-find wiring is
                    // a follow-up; close find for now.
                    closeFind()
                },
                onNext = { webViewRef.value?.findNext(true) },
                onPrevious = { webViewRef.value?.findNext(false) },
                matchesFound = findMatchesFound,
                matchCount = findMatchCount,
                currentIndex = findCurrentIndex,
                searchTrigger = findSearchTrigger,
                isSearching = findIsSearching,
                // 24dp clears the status bar in the app, where find hides the
                // chrome. The fold panel keeps its top bar and already pads the
                // viewer below it, so there the bar goes straight under it, in
                // its compact form.
                compact = capabilities == GuideViewerCapabilities.OVERLAY,
                recentSearches = true,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = if (capabilities == GuideViewerCapabilities.OVERLAY) 0.dp else 24.dp)
            )
        }

        com.twinlore.app.ui.components.CopiedPill(
            trigger = copiedPillTrigger,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = chromeBelow + 56.dp),
        )
        // Asks before a marker goes: a tap on its highlight in the panel, or a
        // long press on it in the contents. Drawn in the page, not a Dialog,
        // so the panel's overlay window can show it too.
        pendingDeleteMarkerId?.let { id ->
            val marker = markers.firstOrNull { it.id == id }
            if (marker == null) {
                LaunchedEffect(id) { pendingDeleteMarkerId = null }
            } else {
                com.twinlore.app.ui.components.InlineMarkerDeletePrompt(
                    label = marker.label,
                    onDelete = {
                        pendingDeleteMarkerId = null
                        coroutineScope.launch {
                            savedTabRepository.deleteScrollMarker(vaultId, bookmark.id, id)
                            com.twinlore.app.ui.screens.clearScrollMarkerHighlight(webViewRef.value)
                        }
                    },
                    onCancel = { pendingDeleteMarkerId = null },
                )
            }
        }

        // Bottom history strip — flush above the vault chrome.
        // Auto-hides with chrome; suppressed while find / ToC overlays are
        // open (those own the bottom of the screen).
        if (chromeVisible && !showFind && !showTocView) {
            // Each history entry stores the chapter label that was active
            // when the snapshot was taken — that's the chapter Back/Forward
            // would put the user back into. Empty / null falls back to a
            // generic word so the strip stays tappable for un-labelled
            // positions.
            NavigationHistoryStrip(
                chromeBelow = chromeBelow,
                backLabel = backHistory.lastOrNull()?.let {
                    it.second?.takeIf { s -> s.isNotBlank() } ?: "Previous"
                },
                forwardLabel = forwardHistory.lastOrNull()?.let {
                    it.second?.takeIf { s -> s.isNotBlank() } ?: "Next"
                },
                accentColor = effectiveFocusColor,
                onBack = {
                    suppressChromeHideUntil = System.currentTimeMillis() + 1000L
                    navigateHistoryBack()
                },
                onForward = {
                    suppressChromeHideUntil = System.currentTimeMillis() + 1000L
                    navigateHistoryForward()
                },
                onClear = {
                    backHistory = emptyList()
                    forwardHistory = emptyList()
                }
            )
        }

        // Right-edge scroll-position chip. Drag to scrub through the
        // doc. Lives outside the chrome gate — appears whenever
        // scrolling and fades after 1.5s of inactivity, independent
        // of chrome visibility.
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

        // First-open prepare spinner, over the WebView, themed to the guide palette.
        if (showPrepareSpinner) {
            val spinnerBg = runCatching { Color(android.graphics.Color.parseColor(currentPaletteBgHex)) }
                .getOrDefault(MaterialTheme.colorScheme.background)
            val spinnerFg = runCatching { Color(android.graphics.Color.parseColor(currentPaletteFgHex)) }
                .getOrDefault(MaterialTheme.colorScheme.onBackground)
            Box(
                modifier = Modifier.fillMaxSize().background(spinnerBg),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = spinnerFg)
            }
        }
    }

    if (showTocView) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            SingleGuideTocViewScreen(
                entries = tocEntries,
                // The panel tucks the reader 8dp under its bar; this gives
                // that back so the tabs start where the bar ends.
                topSpacing = if (capabilities == GuideViewerCapabilities.OVERLAY) 8.dp else 24.dp,
                activeFragment = activeChapterIndex?.let { tocEntries.getOrNull(it)?.fragment },
                markers = markers,
                focusColor = effectiveFocusColor,
                tabIndex = tocTabIndex,
                onTabIndexChange = { tocTabIndex = it },
                onClose = { showTocView = false },
                onEntryTapped = { fragment ->
                    showTocView = false
                    val idx = tocEntries.indexOfFirst { it.fragment == fragment }
                    if (idx >= 0) {
                        suppressChromeHideUntil = System.currentTimeMillis() + 1000L
                        pushHistoryBeforeJump()
                        jumpToChapter(idx)
                    }
                },
                onMarkerTapped = { marker ->
                    showTocView = false
                    pushHistoryBeforeJump()
                    scrollToMarkerByText(
                        webView = webViewRef.value,
                        label = marker.label,
                        fallbackScrollY = marker.scrollY,
                        occurrenceIndex = marker.occurrenceIndex,
                        flashFocusHex = currentHighlightHex
                    ) { /* resolved Y not needed */ }
                },
                onMarkerLongPressDelete = { marker ->
                    pendingDeleteMarkerId = marker.id
                }
            )
        }
    }

    // Image long-press action menu. Same shape + buttons as the in-app
    // browser's image context menu (image-quality readout + "Assign as
    // Artwork" / "Save Image"). The offline twist: we resolve the URL to
    // an MHT-cache file:// URI first, then route through
    // libraryViewModel.assignArtworkFromFile / mapViewModel.assignMapFile
    // — both copy the file into the home folder and update the vault
    // resource pointers without ever re-downloading, so this works offline.
    if (capabilities.subWindows) {
    pendingImageLongPressUrl?.let { imageUrl ->
        // The same view models the live browser uses for its Assign-as-Artwork
        // and Save-Image actions, so BrowserImageContextMenu is reused
        // untouched. Deliberately inside the branch rather than at the top of
        // the composable: a long press is the only thing that reaches them, and
        // the fold panel's window cannot resolve a hiltViewModel() at all.
        val libraryViewModel: com.twinlore.app.ui.screens.LibraryViewModel = hiltViewModel()
        val mapViewModel: com.twinlore.app.ui.screens.MapViewerScreenViewModel = hiltViewModel()
        val resolvedFile = remember(imageUrl) {
            com.twinlore.app.ui.screens.offline.resolveSavedGuideImageFile(
                baseDir = loadedAssetBaseDirState.value,
                manifest = loadedAssetManifestState.value,
                srcUrl = imageUrl
            )
        }
        // Pass the file:// URL when the image is in the MHT cache so the
        // menu can decode dimensions locally; otherwise let it try the
        // http(s) URL (the coordinator's online fetch path).
        val menuImageUrl = resolvedFile?.let { "file://${it.absolutePath}" } ?: imageUrl
        com.twinlore.app.ui.screens.BrowserImageContextMenu(
            imageUrl = menuImageUrl,
            pageUrl = bookmark.url,
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

    // Link long-press action dialog.
    if (capabilities.subWindows) {
    pendingLinkLongPressUrl?.let { linkUrl ->
        AlertDialog(
            onDismissRequest = { pendingLinkLongPressUrl = null },
            title = {
                Text(
                    text = linkUrl,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall
                )
            },
            text = {
                Column {
                    TextButton(onClick = {
                        // Resolve the link's text via JS (more readable label than
                        // the raw URL). The URL is encoded inside a JS literal so
                        // arbitrary user content can't break the evaluation. Falls
                        // back to the URL when the link is unfindable in the DOM.
                        val safeUrl = org.json.JSONObject.quote(linkUrl)
                        val wv = webViewRef.value
                        if (wv == null) {
                            pendingLinkLongPressUrl = null
                            return@TextButton
                        }
                        wv.evaluateJavascript(
                            """
                            (function() {
                                try {
                                    var url = $safeUrl;
                                    var links = document.querySelectorAll('a[href]');
                                    for (var i = 0; i < links.length; i++) {
                                        if (links[i].href === url) {
                                            var t = (links[i].textContent || '').trim();
                                            if (t) return JSON.stringify({ text: t, y: Math.round(window.pageYOffset || 0) });
                                        }
                                    }
                                    return JSON.stringify({ text: '', y: Math.round(window.pageYOffset || 0) });
                                } catch (e) {
                                    return JSON.stringify({ text: '', y: Math.round(window.pageYOffset || 0) });
                                }
                            })();
                            """.trimIndent()
                        ) { result ->
                            val cleaned = result
                                ?.trim()
                                ?.removeSurrounding("\"")
                                ?.replace("\\\"", "\"")
                                ?.replace("\\\\", "\\")
                                ?: "{}"
                            val (label, y) = runCatching {
                                val obj = org.json.JSONObject(cleaned)
                                val t = obj.optString("text").ifBlank { linkUrl.take(80) }
                                val yy = obj.optInt("y", 0)
                                t to yy
                            }.getOrDefault(linkUrl.take(80) to 0)
                            coroutineScope.launch {
                                savedTabRepository.addScrollMarker(
                                    vaultId = vaultId,
                                    bookmarkId = bookmark.id,
                                    label = label,
                                    scrollY = y,
                                    occurrenceIndex = 0
                                )
                                android.widget.Toast.makeText(
                                    context,
                                    "Scroll marker saved: " + label.take(30) +
                                        if (label.length > 30) "…" else "",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                            pendingLinkLongPressUrl = null
                        }
                    }) { Text("Save as marker") }
                    TextButton(onClick = {
                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                            as? android.content.ClipboardManager
                        clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("link", linkUrl))
                        android.widget.Toast.makeText(context, "Link copied", android.widget.Toast.LENGTH_SHORT).show()
                        pendingLinkLongPressUrl = null
                    }) { Text("Copy link") }
                    TextButton(onClick = {
                        mainViewModel?.setPendingUrlForBrowserTab(vaultId, linkUrl)
                        mainViewModel?.setVaultSessionTab(vaultId, VaultTab.Browser)
                        pendingLinkLongPressUrl = null
                    }) { Text("Open in Browser") }
                    TextButton(onClick = {
                        val url = "https://www.youtube.com/results?search_query=" +
                            android.net.Uri.encode(linkUrl.trim())
                        mainViewModel?.setPendingUrlForBrowserTab(vaultId, url)
                        mainViewModel?.setVaultSessionTab(vaultId, VaultTab.Browser)
                        pendingLinkLongPressUrl = null
                    }) { Text("Search YouTube") }
                    TextButton(onClick = {
                        val url = "https://www.google.com/search?q=" +
                            android.net.Uri.encode(linkUrl.trim())
                        mainViewModel?.setPendingUrlForBrowserTab(vaultId, url)
                        mainViewModel?.setVaultSessionTab(vaultId, VaultTab.Browser)
                        pendingLinkLongPressUrl = null
                    }) { Text("Search online") }
                }
            },
            confirmButton = {
                TextButton(onClick = { pendingLinkLongPressUrl = null }) { Text("Cancel") }
            }
        )
    }
    }

    if (capabilities.subWindows && showDisplaySheet) {
        PageGroupDisplaySheet(
            // The sheet is a ModalBottomSheet, so it is only ever opened where
            // sub-windows work, which is also the only place this resolves.
            settingsViewModel = hiltViewModel(),
            palette = palette,
            focusColor = effectiveFocusColor,
            textZoomFraction = textZoomFraction,
            onTextZoomChange = { newZoom ->
                textZoomFraction = newZoom
                // Persist per-bookmark zoom on a survivor scope so a
                // quick slider drag + exit doesn't lose the write.
                val bookmarkId = bookmark.id
                kotlinx.coroutines.CoroutineScope(
                    kotlinx.coroutines.SupervisorJob() + Dispatchers.IO
                ).launch {
                    savedTabRepository.updateBookmarkZoomLevel(bookmarkId, newZoom)
                }
            },
            isTextGuide = isTextGuide,
            onDismiss = { showDisplaySheet = false }
        )
    }

    // Image-tap zoom overlay — same component page-group uses.
    guideImageZoomModel?.let { model ->
        // The zoom viewer is a Dialog, which an overlay window cannot host, so
        // there it draws inline over the page instead. Same viewer either way;
        // only the window it lives in differs.
        if (capabilities.subWindows) {
            GuideImageZoomViewer(
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
}

/**
 * Build JS that walks `<a href>` and decorates each anchor:
 *
 *  - **Internal** — href is a fragment-only `#frag` (or
 *    path-with-fragment) and `document.getElementById(fragment)`
 *    resolves in this saved document. Tapping scrolls in-page. No badge;
 *    the link text is colored with the user's effective focus color
 *    ([accentHex]) plus the shared dotted underline.
 *  - **External** (open_in_new icon, 12 px trailing SVG badge) — real
 *    `http(s):` link that will hand off to the browser tab. Same accent
 *    color + dotted underline as internal; the badge is the affordance
 *    distinguishing "leaves to browser". Skips `javascript:`, `mailto:`,
 *    `tel:`, empty hrefs, and dead fragment-only anchors. See
 *    [com.twinlore.app.ui.screens.offline.EXTERNAL_HREF_CLASSIFIER_JS].
 *
 * The accent color is applied via `style.setProperty(…, 'important')` so
 * it wins over the palette-override stylesheet's `a{color:…!important}`
 * rule. It's baked into the generated JS, so a focus-color change picks
 * up on the next page load — same lifecycle as the rest of the injection.
 *
 * Idempotent: tagged via `data-pg-local-marked` (internal) and
 * `data-pg-external-marked` (external) so re-injection on every
 * onPageFinished is a no-op.
 */
internal fun buildInternalAnchorBadgeJs(bookmarkBase: String, accentHex: String): String {
    val base = org.json.JSONObject.quote(bookmarkBase)
    val externalSvg = org.json.JSONObject.quote(
        com.twinlore.app.ui.screens.offline.EXTERNAL_LINK_SVG_INNER
    )
    val badgeSpanStyle = org.json.JSONObject.quote(
        com.twinlore.app.ui.screens.offline.ANCHOR_BADGE_SPAN_STYLE
    )
    val decorationStyle = org.json.JSONObject.quote(
        ";" + com.twinlore.app.ui.screens.offline.ANCHOR_DECORATION_STYLE
    )
    val accent = org.json.JSONObject.quote(accentHex)
    val externalClassifier =
        com.twinlore.app.ui.screens.offline.EXTERNAL_HREF_CLASSIFIER_JS
    return """
(function() {
    try {
        var base = $base;
        $externalClassifier
        // svgInner is null for internal anchors (accent color only) and the
        // open_in_new markup for external ones.
        function gtDecorate(a, svgInner, markedAttr) {
            if (svgInner) {
                var marker = document.createElement('span');
                // markedAttr ends with '-marked'; the badge span uses '-marker'.
                marker.setAttribute(markedAttr.replace('-marked', '-marker'), '1');
                marker.setAttribute('style', $badgeSpanStyle);
                marker.innerHTML = svgInner;
                a.appendChild(marker);
            }
            var prev = a.getAttribute('style') || '';
            a.setAttribute('style', prev + $decorationStyle);
            // 'important' so the accent wins over the palette-override
            // stylesheet's a{color:…!important} rule.
            a.style.setProperty('color', $accent, 'important');
            // Inline color on the <a> only INHERITS into children — a direct
            // rule on a child (palette CSS on span/font wrappers) always beats
            // inherited color, so the visible text stayed unpainted. Paint
            // every descendant explicitly — including the badge marker span,
            // whose SVG uses fill="currentColor" and so tints to the accent.
            var kids = a.querySelectorAll('*');
            for (var k = 0; k < kids.length; k++) {
                var kid = kids[k];
                if ((kid.tagName || '').toUpperCase() === 'IMG') continue;
                kid.style.setProperty('color', $accent, 'important');
            }
            a.setAttribute(markedAttr, '1');
        }
        var anchors = document.querySelectorAll('a[href]');
        for (var i = 0; i < anchors.length; i++) {
            var a = anchors[i];
            try {
                if (a.getAttribute('data-pg-local-marked') === '1') continue;
                if (a.getAttribute('data-pg-external-marked') === '1') continue;
                // Skip image-wrapping anchors universally: <a href="…"><img…></a>
                // shapes (Bulbapedia / MediaWiki thumbs, Omegametroid sidebar
                // tiles, Steam Workshop file tiles, etc.) get treated as images
                // by the long-press handler (Save / Assign as Artwork), so the
                // link affordance is misleading — and the badge would sit
                // underneath the image inside the anchor box anyway.
                var firstChild = a.firstElementChild;
                if (firstChild && firstChild.tagName === 'IMG') continue;
                var href = a.getAttribute('href') || '';
                if (!href) continue;
                // Internal-anchor classification: fragment resolves to a
                // live target in the current document.
                var fragment = '';
                if (href.charAt(0) === '#') {
                    fragment = href.substring(1);
                } else {
                    try {
                        var resolved = new URL(a.href, window.location.href);
                        if (resolved.pathname && resolved.hash) {
                            fragment = resolved.hash.substring(1);
                        }
                    } catch (e) { /* fall through to external pass */ }
                }
                if (fragment) {
                    try { fragment = decodeURIComponent(fragment); } catch (e) { /* keep raw */ }
                    // GitHub wiki prefixes heading/author-anchor ids with
                    // 'user-content-' AND lowercases them, while the in-page
                    // links keep the author's original case (e.g. href
                    // '#ShowdownChallenges' -> id 'user-content-showdownchallenges').
                    // Its client JS bridges the two online but never runs
                    // offline, so try the lowercased prefixed id before giving
                    // up to the external pass (harmless on other hosts — no
                    // such id exists).
                    var target = document.getElementById(fragment) ||
                                 document.getElementById('user-content-' + fragment.toLowerCase()) ||
                                 document.querySelector('[name="' + fragment.replace(/"/g, '\\"') + '"]');
                    if (target) {
                        gtDecorate(a, null, 'data-pg-local-marked');
                        continue;
                    }
                }
                // External pass: real http(s) link that will hand off to
                // the browser tab. Dead fragment-only anchors fall through
                // here too (no live target above) — gtClassifyExternal
                // returns false for `#...`, so they're skipped.
                if (gtClassifyExternal(href)) {
                    gtDecorate(a, $externalSvg, 'data-pg-external-marked');
                }
            } catch (e) { /* swallow per-anchor errors */ }
        }
    } catch (e) { /* swallow */ }
})();
""".trimIndent()
}

/**
 * Splice [scriptBody] into [html] as its own `<script id="sgv-scroll-restore">`
 * block, placed just before `</body>`. Separate from the
 * decorations-bundle splice so a synchronous exception in one of the
 * decoration scripts can't suppress us.
 */
internal fun spliceScrollRestoreScript(html: String, scriptBody: String): String {
    val scriptBlock = "<script id=\"sgv-scroll-restore\">$scriptBody</script>"
    val bodyCloseMatch = Regex("</body\\s*>", RegexOption.IGNORE_CASE).find(html)
    return if (bodyCloseMatch != null) {
        val insertAt = bodyCloseMatch.range.first
        html.substring(0, insertAt) + scriptBlock + html.substring(insertAt)
    } else {
        html + scriptBlock
    }
}

/**
 * JS that puts the reader back where they left the guide, at
 * `ratio × scrollHeight`. Spliced into the HTML before
 * loadDataWithBaseURL so the browser handles the scroll during page
 * parse (no post-load snap, no alpha-hide / loading overlay).
 *
 * The ratio is re-applied against the live height for as long as the
 * document keeps resizing — see [buildSettlingScrollJs] for why a
 * single shot, even one at window.load, lands too far down.
 *
 * Empty string when [ratio] is null or zero (no restore needed).
 */
internal fun buildInlineScrollRestoreJs(ratio: Double?): String {
    if (ratio == null || ratio <= 0.0) return ""
    return buildSettlingScrollJs(
        """
        var h = Math.max(
            document.body ? document.body.scrollHeight : 0,
            document.documentElement ? document.documentElement.scrollHeight : 0);
        if (h > 0) window.scrollTo(0, Math.round(h * $ratio));
        """.trimIndent(),
        landingCall = ANDROID_INTERFACE_LANDING_CALL,
    )
}

/**
 * Inline script that runs the anchor-cluster extractor at HTML-parse time
 * and posts the resulting entries to androidInterface.onTocReady. Mirrors
 * [extractAnchorClusterEntries] but fires during HTML parsing so chapters
 * are available with the first paint instead of after a 2s post-load delay.
 *
 * The post-load [extractAnchorClusterEntries] calls in onPageFinished remain
 * as a safety net for late reflows (TXT fitBlocks, font load).
 */
internal fun buildInlineTocExtractorJs(context: android.content.Context): String {
    val extractorJs = runCatching {
        context.assets.open("anchor_cluster_extract.js").bufferedReader()
            .use { it.readText() }
    }.getOrNull() ?: return "/* anchor_cluster_extract.js asset missing */"
    // The asset is itself an IIFE that returns a JSON string. Assign its
    // result directly to a variable, then post via the bridge.
    return """
(function() {
    try {
        var json = $extractorJs
        if (json && window.androidInterface && window.androidInterface.onTocReady) {
            window.androidInterface.onTocReady(json);
        }
    } catch (e) { /* swallow */ }
})();
""".trimIndent()
}

/**
 * Whether [urlStr] points inside the document already on screen: an anchor in
 * the same bookmark, or a bare fragment.
 *
 * Extracted so the overlay's link guard can ask the question before the
 * leaving branches run. The viewer's own anchor handling asks it again further
 * down, and the two must agree: an in-document jump that the guard refused
 * would break contents navigation in the fold panel, which is most of what a
 * long walkthrough's links are.
 */
internal fun isInDocumentJump(
    urlStr: String,
    bookmarkUrl: String,
    url: android.net.Uri,
): Boolean {
    val bookmarkBase = bookmarkUrl.substringBefore("#").substringBefore("?")
    val sameBookmarkMatch = urlStr.contains("#") &&
        bookmarkBase.isNotBlank() &&
        urlStr.substringBefore("#").substringBefore("?") == bookmarkBase
    val host = url.host?.lowercase().orEmpty()
    val bareFragment = urlStr.startsWith("#") ||
        (host == "localhost.invalid" && url.fragment?.isNotEmpty() == true)
    return sameBookmarkMatch || bareFragment
}
