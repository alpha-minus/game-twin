package com.twinlore.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.app.Activity
import android.app.Dialog
import android.content.ContextWrapper
import android.content.res.Configuration
import android.os.Message
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewParent
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import org.json.JSONArray
import org.json.JSONObject
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.indication
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.twinlore.app.ui.MainViewModel
import com.twinlore.app.ui.navigation.NavigableIconButton
import com.twinlore.app.ui.navigation.NavigableButton
import com.twinlore.app.ui.navigation.NavigableMenuItem
import com.twinlore.app.ui.navigation.longPressHandler
import com.twinlore.app.data.FocusColor
import com.twinlore.app.data.HapticFeedbackIntensity
import com.twinlore.app.data.LedTurnOffTime
import com.twinlore.app.thor.led.ThorLedManager
import com.twinlore.app.ui.screens.SettingsViewModel
import com.twinlore.app.ui.utils.performHapticFeedback
import com.twinlore.app.ui.utils.runWithHapticAndLed
import androidx.compose.ui.graphics.toArgb
import com.twinlore.app.ui.LocalThemePalette
import com.twinlore.app.data.ThemePalette
import com.twinlore.app.ui.LocalCachedFocusColor
import com.twinlore.app.ui.theme.getFocusContainer
import com.twinlore.app.data.MapMarkerManager
import com.twinlore.app.data.LandingSection
import com.twinlore.app.data.LandingEntry
import com.twinlore.app.data.buildLandingNavHtml
import com.twinlore.app.data.buildRecentSearchesHtml
import com.twinlore.app.data.buildSearchQuery
import com.twinlore.app.data.defaultLandingEntries
import com.twinlore.app.data.SearchHistoryManager
import com.twinlore.app.data.SavedTabRepository
import com.twinlore.app.domain.SavedTab
import com.twinlore.app.di.SearchHistoryManagerEntryPoint
import com.twinlore.app.di.SavedTabRepositoryEntryPoint
import com.twinlore.app.di.ResourceResolverEntryPoint
import com.twinlore.app.data.privacy.CosmeticFilters
import com.twinlore.app.di.PrivacyPreferencesEntryPoint
import com.twinlore.app.di.NetworkConnectivityEntryPoint
import dagger.hilt.EntryPoints
import com.twinlore.app.domain.AssetType
import com.twinlore.app.domain.MapMarker
import com.twinlore.app.domain.Vault
import com.twinlore.app.domain.MarkerStyle
import com.twinlore.app.domain.ScrollRestoreData
import com.twinlore.app.domain.VaultSessionMode
import com.twinlore.app.domain.ScrollMarker
import com.twinlore.app.ui.screens.MapMarkerOverlay
import com.twinlore.app.ui.screens.MarkerStyleSelectionDialog
import com.twinlore.app.ui.screens.MarkerCarousel
import com.twinlore.app.ui.screens.MarkerManagementMenu
import com.twinlore.app.ui.screens.MapMarkerCoordinateUtils
import com.twinlore.app.ui.screens.DeleteAllMarkersDialog
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.SideEffect
import java.io.File
import java.net.URL
import java.io.FileOutputStream
import java.io.InputStream
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import android.util.Log
import android.graphics.BitmapFactory
import com.twinlore.app.data.SearchEngine
import com.twinlore.app.ui.utils.ImagePathUtils

/** Desktop Chrome UA used only for image/image-result URLs so CDNs accept the request. */
private const val DESKTOP_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

/**
 * WebView content composable with long-press support
 */
// buildPaletteCssJs and REMOVE_PALETTE_CSS_JS live in GuideBrowserHelpers.kt (internal visibility)
// so they can be shared with PageGroupViewerScreen without duplication.

/**
 * Probe the WebView for the smallest useful textZoom on this page.
 *
 * textZoom only shrinks text, not images/layout. WebView also clamps rendered
 * text at minimumFontSize, so below some knee textZoom the page stops shrinking
 * entirely. The naive vpH/docH formula doesn't see either constraint and lets
 * the slider expose unrealizable values. This probe finds the knee by:
 *  1. Reading docHeight at the floor (textZoom 10, all text fully clamped).
 *  2. Trying candidate zooms 0.5..0.9 — the first one where docHeight is still
 *     meaningfully above the floor is the smallest useful slider value.
 * Body opacity is suppressed during the probe to hide the textZoom flicker.
 */
private suspend fun probeMinUsefulZoom(view: android.webkit.WebView): Float {
    suspend fun evalFloat(script: String): Float {
        val def = kotlinx.coroutines.CompletableDeferred<Float>()
        view.evaluateJavascript(script) { def.complete(it?.toFloatOrNull() ?: 0f) }
        return def.await()
    }
    val docScript = "(function(){return Math.max(document.body.scrollHeight||0,document.documentElement.scrollHeight||0);})();"
    val originalTextZoom = view.settings.textZoom
    val currentZoom = (originalTextZoom / 100f).coerceIn(0.5f, 3.0f)

    view.evaluateJavascript("document.body && (document.body.style.opacity='0');", null)
    try {
        val docHCurrent = evalFloat(docScript)
        val vpH = evalFloat("(function(){return window.innerHeight||0;})();")
        if (docHCurrent <= 0f || vpH <= 0f) return 0.5f
        if (docHCurrent <= vpH) return 0.5f // already fits; full slider range is fine

        view.settings.textZoom = 10
        kotlinx.coroutines.delay(80)
        val floor = evalFloat(docScript)
        if (floor <= 0f || floor >= docHCurrent) return currentZoom

        val epsilon = (docHCurrent - floor) * 0.08f
        for (candidate in listOf(0.5f, 0.6f, 0.7f, 0.8f, 0.9f)) {
            if (candidate >= currentZoom) break
            view.settings.textZoom = (candidate * 100).toInt()
            kotlinx.coroutines.delay(60)
            val docHAt = evalFloat(docScript)
            if (docHAt > floor + epsilon) return candidate
        }
        return currentZoom
    } finally {
        view.settings.textZoom = originalTextZoom
        kotlinx.coroutines.delay(40)
        view.evaluateJavascript("document.body && (document.body.style.opacity='');", null)
    }
}

@Composable
internal fun WebViewContent(
    initialUrl: String,
    webView: (WebView) -> Unit,
    onUrlChanged: (String) -> Unit,
    onCanGoBackChanged: (Boolean) -> Unit,
    onCanGoForwardChanged: (Boolean) -> Unit,
    onLoadingChanged: (Boolean) -> Unit,
    zoomLevel: Float,
    @Suppress("UNUSED_PARAMETER") onZoomChanged: (Float) -> Unit, // Unused - zoom handled via WebView settings
    onZoomIn: () -> Unit, // Zoom in callback
    onZoomOut: () -> Unit, // Zoom out callback
    onResetZoom: (() -> Unit)? = null, // Reset zoom to 1x (e.g. on double-tap, like map view)
    showTopBar: Boolean, // Whether to show top bar (and zoom buttons)
    hasToc: Boolean = false, // Whether to show TOC button alongside zoom
    onOpenTocDrawer: (() -> Unit)? = null, // Callback to open TOC drawer
    @Suppress("UNUSED_PARAMETER") onScrollChanged: (Int, Int) -> Unit, // Called to report scroll position and maxScrollY (for TOC ratio at any zoom)
    getSavedScrollRestore: () -> ScrollRestoreData? = { null }, // Current scroll restore (read when page finishes so offline switch gets latest)
    getScrollPosition: () -> Int = { 0 }, // Current scroll Y (for capture)
    onNewWindowLoadInCurrentTab: () -> Unit = {}, // Called when loading a new-tab link in current tab so scroll can be reset to top
    onScrollRestoreStarted: () -> Unit = {}, // Callback when scroll restoration starts
    onScrollRestoreCompleted: () -> Unit = {}, // Callback when scroll restoration completes
    isLoading: Boolean = false, // Current loading state (to prevent restoration during loading)
    isRestoringScroll: Boolean = false, // Whether scroll restoration is in progress
    onTouch: (() -> Unit)? = null,
    onAsciiBlockToggled: (() -> Unit)? = null, // Fired when an ASCII scroll-block is tapped to expand/shrink, used by callers to surface chrome
    onImageLongPress: ((String) -> Unit)? = null, // Callback for image long-press
    onLinkLongPress: ((String, String) -> Unit)? = null, // Callback for link long-press (Browser mode only)
    onGuideImageTap: ((srcUrl: String, localPath: String?) -> Unit)? = null, // Guide mode only: tap on <img> opens in-Guide zoom viewer. localPath is the manifest-resolved file:// URI when the image is in the MHT cache, null otherwise.
    onOpenInNewTab: ((String) -> Unit)? = null,
    onFaviconReceived: ((ByteArray) -> Unit)? = null,
    onTitleReceived: ((String) -> Unit)? = null,
    onOpenSavedLink: ((String) -> Unit)? = null,
    onDeleteSavedLink: ((String) -> Unit)? = null,
    customSelectionActions: GuideWebView.CustomSelectionActions? = null, // Guide tab only: custom selection menu (Copy, Save Marker, Find, YouTube, Search online, Select All). Browser tab passes null for default bar.
    favoritePath: String? = null, // Local file path for fallback on network errors
    vaultId: String, // Vault ID for landing page generation
    artworkPath: String? = null, // Artwork path for landing page
    gameName: String? = null, // Game name for search queries
    appendGameName: Boolean = true, // Auto-append game name for YouTube search (same toggle as search bar)
    currentSearchEngine: SearchEngine, // Current search engine from settings
    landingEntriesRef: MutableState<List<LandingEntry>>, // entries for vault://section/ (label, appendWord, useImageSearch, icon)
    savedLinksRef: androidx.compose.runtime.State<List<com.twinlore.app.domain.SavedTab>>? = null, // bookmarks for the landing page (Browser mode); null in Guide mode
    onEditLandingSlot: ((Int) -> Unit)? = null, // Called when user taps pencil icon on a landing page chip (slot index)
    onMinZoomComputed: ((Float) -> Unit)? = null, // Reports minimum effective zoom level after page load
    onPageFinished: ((WebView?, String?) -> Unit)? = null, // Called when page load finishes (e.g. for TOC extraction on bookmarked HTML)
    onTextFaqTocDetected: (List<TocEntry>) -> Unit = {}, // Called when text FAQ sections are detected (for TOC sidebar, no DOM modification)
    onScrollMarkerHighlightTap: ((Int) -> Unit)? = null, // Called when user taps a scroll marker highlight in the page (marker index)
    vaultSessionMode: VaultSessionMode? = null, // When Guide, intercept link clicks and open in browser tab
    onOpenLinkInBrowser: ((String) -> Unit)? = null, // When in Guide mode, open this URL in Browser tab instead of loading in guide
    currentBookmarkUrl: String? = null, // Original HTTP URL of the current offline bookmark (used to reconstruct links)
    browserOneOffLoad: MutableState<Boolean>? = null, // When in Browser mode: one-off load flag (clear on user nav)
    oneOffUrlBeingLoaded: MutableState<String?>? = null, // When in Browser mode: URL we're loading as one-off (don't clear one-off for this URL)
    onDownloadRequested: ((url: String, userAgent: String, contentDisposition: String, mimeType: String, contentLength: Long) -> Unit)? = null, // Called when WebView encounters a downloadable file (e.g. PDF)
    assetManifestState: State<Map<String, String>> = remember { mutableStateOf(emptyMap()) }, // originalUrl → local asset path
    assetBaseDirState: State<String?> = remember { mutableStateOf(null) }, // Absolute path to guide dir
    isDataUrlLoadedState: State<Boolean> = remember { mutableStateOf(false) }, // Whether loadDataWithBaseURL is active (view.url is unreliable when true)
    guideThemeInject: Boolean = true,
    zoomControlsFocusRequester: FocusRequester? = null, // Focus routing: top bar DOWN → zoom controls
    topBarFocusRequester: FocusRequester? = null, // Focus routing: zoom controls UP → top bar
    bottomBarFocusRequester: FocusRequester? = null, // Focus routing: zoom controls DOWN → bottom bar
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val context = LocalContext.current
    
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val palette = LocalThemePalette.current
    val appFont by settingsViewModel.appFont.collectAsState(initial = com.twinlore.app.data.AppFont.DEFAULT)

    // Palette + font CSS injection — reused in LaunchedEffect and onPageFinished
    val paletteBgHex = remember(palette) { String.format("#%06X", 0xFFFFFF and palette.background.toArgb()) }
    val paletteFgHex = remember(palette) { String.format("#%06X", 0xFFFFFF and palette.foreground.toArgb()) }
    val currentPaletteBgHex by rememberUpdatedState(paletteBgHex)
    val currentPaletteFgHex by rememberUpdatedState(paletteFgHex)
    val currentAppFont by rememberUpdatedState(appFont)
    val currentGuideThemeInject by rememberUpdatedState(guideThemeInject)

    // Store WebView reference
    var webViewInstance: WebView? by remember { mutableStateOf(null) }
    
    // Search history for landing page recent searches section
    val searchHistoryManager = remember {
        EntryPoints.get(context.applicationContext, SearchHistoryManagerEntryPoint::class.java).searchHistoryManager()
    }
    
    // ResourceResolver: turns SavedTab.resourceId into a usable Uri (file:// or content://).
    val resourceResolver = remember {
        EntryPoints.get(context.applicationContext, ResourceResolverEntryPoint::class.java)
            .resourceResolver()
    }
    val savedTabRepository = remember {
        EntryPoints.get(context.applicationContext, SavedTabRepositoryEntryPoint::class.java)
            .savedTabRepository()
    }
    val privacyPreferences = remember {
        EntryPoints.get(context.applicationContext, PrivacyPreferencesEntryPoint::class.java)
            .privacyPreferences()
    }

    // Use algorithmic darkening for regular web pages (based on palette.isDark).
    // Reader mode pages (converted text FAQs) get CSS injection in onPageFinished instead.
    LaunchedEffect(webViewInstance, palette) {
        webViewInstance?.let { view ->
            if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
                WebSettingsCompat.setAlgorithmicDarkeningAllowed(view.settings, palette.isDark)
            }
        }
    }

    // Re-inject or remove palette CSS when toggle state or palette/font changes.
    // vaultSessionMode MUST be a key: a palette change while another tab is
    // active hits the guard below, and without the key the effect never
    // re-runs when the user returns to the Guide tab — the WebView stays on
    // the old palette until the whole session is rebuilt (dark-stuck guide
    // after switching theme from Settings).
    LaunchedEffect(webViewInstance, guideThemeInject, palette, appFont, vaultSessionMode) {
        val wv = webViewInstance ?: return@LaunchedEffect
        if (vaultSessionMode != VaultSessionMode.Guide) return@LaunchedEffect
        val url = wv.url ?: return@LaunchedEffect
        if (url.contains("landing.html")) return@LaunchedEffect
        if (guideThemeInject) {
            wv.evaluateJavascript(buildPaletteCssJs(currentPaletteBgHex, currentPaletteFgHex, currentAppFont), null)
        } else {
            wv.evaluateJavascript(REMOVE_PALETTE_CSS_JS, null)
        }
    }

    // Reload landing page when landing entries change (e.g. after inline edit)
    LaunchedEffect(landingEntriesRef.value) {
        val view = webViewInstance ?: return@LaunchedEffect
        val currentUrl = view.url ?: return@LaunchedEffect
        if (currentUrl.contains("landing.html")) {
            val freshUrl = getLandingPageUrl(
                context, vaultId, artworkPath, landingEntriesRef.value,
                searchHistoryManager.getSearchHistory(vaultId).take(3),
                savedLinks = savedLinksRef?.value.orEmpty(),
                paletteBgHex = currentPaletteBgHex,
                paletteFgHex = currentPaletteFgHex,
                gameName = gameName
            )
            if (freshUrl != null) {
                view.loadUrl(freshUrl)
            }
        }
    }

    // Get focus color and haptic/LED from settings for zoom buttons
    val focusColorState by settingsViewModel.focusColor.collectAsState(initial = FocusColor.CRIMSON)
    val cachedFocusColor = LocalCachedFocusColor.current?.value
    val focusColor = cachedFocusColor ?: focusColorState.color
    val view = LocalView.current
    val hapticFeedbackIntensity by settingsViewModel.hapticFeedbackIntensity.collectAsState(initial = HapticFeedbackIntensity.MEDIUM)
    val ledEnabled by settingsViewModel.ledEnabled.collectAsState(initial = true)
    val ledBrightness by settingsViewModel.ledBrightness.collectAsState(initial = 127)
    val ledSaturationBoost by settingsViewModel.ledSaturationBoost.collectAsState(initial = 0.75f)
    val ledTurnOffTime by settingsViewModel.ledTurnOffTime.collectAsState(initial = LedTurnOffTime.SEC_15)

    // Ref so JS interface can call latest onScrollMarkerHighlightTap (factory runs once)
    val scrollMarkerHighlightTapRef = remember { mutableStateOf<((Int) -> Unit)?>(null) }
    scrollMarkerHighlightTapRef.value = onScrollMarkerHighlightTap

    // Ref so WebViewClient can call latest onOpenLinkInBrowser (factory captures once, ref stays current)
    val openLinkInBrowserRef = remember { mutableStateOf(onOpenLinkInBrowser) }
    openLinkInBrowserRef.value = onOpenLinkInBrowser

    // Ref so WebViewClient can resolve offline file:// links back to original HTTP URLs
    val currentBookmarkUrlRef = remember { mutableStateOf(currentBookmarkUrl) }
    currentBookmarkUrlRef.value = currentBookmarkUrl

    // Ref so onPageFinished (factory closure) always reads the latest zoom level
    val zoomLevelRef = remember { mutableStateOf(zoomLevel) }
    zoomLevelRef.value = zoomLevel

    // Read State objects directly — no SideEffect needed. The caller passes
    // MutableState refs so shouldInterceptRequest sees updates immediately
    // (before Compose recomposition), avoiding blank pages on first load.
    val assetManifestRef = assetManifestState
    val assetBaseDirRef = assetBaseDirState
    val isDataUrlLoadedRef = isDataUrlLoadedState

    // WebView fills full area; top bar is drawn as overlay on top (no padding/shift)
    // requestDisallowInterceptTouchEvent is set in the WebView touch listener (DOWN + first MOVE past slop) so we don't add a full-screen pointerInput here (it was breaking TOC visibility/hit-testing).
    Box(modifier = modifier) {
        AndroidView(
            factory = { ctx ->
                val wvT0 = System.currentTimeMillis()
                android.util.Log.d("PERF_DEBUG", "WebView factory START (${vaultSessionMode})")
                val defaultUserAgent = WebSettings.getDefaultUserAgent(ctx)
                // Capture currentSearchEngine parameter for use in nested scopes
                val searchEngine = currentSearchEngine

                GuideWebView(ctx).apply {
                    webView(this)
                    // Match the palette background so there's no black flash before content loads.
                    setBackgroundColor(android.graphics.Color.parseColor(currentPaletteBgHex))
                    // Enable hardware acceleration for better performance
                    setLayerType(WebView.LAYER_TYPE_HARDWARE, null)
                settings.apply {
                    // Enable file access for local HTML files
                    setAllowFileAccess(true)
                    setAllowContentAccess(true)
                    // Off on purpose. Relative <img>/<link>/<script src> loads from a
                    // file:// document to sibling files do not need this flag; only
                    // XHR/fetch reads of other file:// URLs do, and that is exactly
                    // what a saved third-party page must not be able to do.
                    @Suppress("DEPRECATION")
                    setAllowFileAccessFromFileURLs(false)
                    @Suppress("DEPRECATION")
                    setAllowUniversalAccessFromFileURLs(false)
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    setSupportMultipleWindows(true)
                    @Suppress("DEPRECATION")
                    // Match Chrome's default: JS-initiated window.open requires a user gesture.
                    // With this true, ad scripts on game-guide sites (GameFAQs/Fandom etc.) fire
                    // popunders via window.open on timers and we'd open them as visible tabs,
                    // which the user reads as "every link spawns a new tab".
                    setJavaScriptCanOpenWindowsAutomatically(false)
                    builtInZoomControls = true
                    displayZoomControls = false
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    setSupportZoom(true)
                    // TEXT_AUTOSIZING reflows text to fit viewport width (like Chrome does)
                    // This fixes rendering of saved HTML guides (GameFAQs, Neoseeker, etc.)
                    // that have desktop-oriented layouts with fixed widths
                    if (vaultSessionMode == VaultSessionMode.Guide) {
                        layoutAlgorithm = WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING
                    }
                    // Use default cache behavior to avoid appearing as a new session on each load
                    cacheMode = WebSettings.LOAD_DEFAULT
                    // Allow passive mixed content (e.g. images) so image search result pages can show images
                    mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                    // Browser mode: enable media playback without user gesture (for inline images/videos on Reddit etc.)
                    // and allow third-party cookies for CDN image authentication
                    if (vaultSessionMode == VaultSessionMode.Browser) {
                        mediaPlaybackRequiresUserGesture = false
                        // Enable database storage for modern web apps
                        databaseEnabled = true
                    }
                    // Keep default (mobile) UA; desktop UA is set only for image/image-result URLs in shouldOverrideUrlLoading

                    // Algorithmic darkening for regular web pages (based on palette.isDark).
                    // Reader mode pages override this with CSS injection in onPageFinished.
                    if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
                        WebSettingsCompat.setAlgorithmicDarkeningAllowed(this, palette.isDark)
                    }
                }

                // Browser mode: allow third-party cookies (needed for image CDNs on sites like Reddit, Imgur)
                if (vaultSessionMode == VaultSessionMode.Browser) {
                    android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                }

                // Add JavaScript for find on page, image long-press, and scroll tracking
                // Expose functions as window methods so they can be called from evaluateJavascript
                val imageCallback = onImageLongPress
                val linkCallback = onLinkLongPress
                val guideImageTapCallback = onGuideImageTap
                val scrollCallback = onScrollChanged
                val resetZoomCallback = onResetZoom
                val focusColorHex = colorToHex(focusColor)
                // Flag shared between JS interface and touch listener: when JS notifies
                // that an ASCII block was toggled, the pending top-bar toggle is suppressed.
                var blockTappedFlag = false
                
                addJavascriptInterface(object {
                    @android.webkit.JavascriptInterface
                    fun onBlockToggled() {
                        // Called from JS when an ASCII scroll-block is tapped to expand/shrink.
                        // Posts to main thread so the flag is set before the delayed touch runnable fires.
                        // Also notifies the caller so chrome can be surfaced. The suppression of the
                        // normal onTouch (toggle) still applies, but onAsciiBlockToggled fires once.
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            blockTappedFlag = true
                            onAsciiBlockToggled?.invoke()
                        }
                    }
                    @android.webkit.JavascriptInterface
                    fun onImageLongPress(imageUrl: String) {
                        // Called when user long-presses on an image
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            imageCallback?.invoke(imageUrl)
                        }
                    }
                    @android.webkit.JavascriptInterface
                    fun onLinkLongPress(url: String, linkText: String) {
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            linkCallback?.invoke(url, linkText)
                        }
                    }
                    @android.webkit.JavascriptInterface
                    fun onGuideImageTap(srcUrl: String) {
                        // Resolve the image's HTTP src against the MHT cache manifest so the
                        // viewer can load from disk instead of going back over the network.
                        // Manifest values are paths relative to assetBaseDir (typically
                        // "assets/asset_<n>.<ext>"). If the entry is missing or the file no
                        // longer exists, fall back to the original URL — Coil will fetch it.
                        val manifest = assetManifestRef.value
                        val baseDir = assetBaseDirRef.value
                        val relativePath = manifest[srcUrl]
                        val resolved: String? = if (relativePath != null && baseDir != null) {
                            val file = java.io.File(baseDir, relativePath)
                            if (file.exists()) "file://${file.absolutePath}" else null
                        } else {
                            null
                        }
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            guideImageTapCallback?.invoke(srcUrl, resolved)
                        }
                    }
                    @android.webkit.JavascriptInterface
                    fun onScrollChanged(scrollPosition: Int, maxScrollY: Int) {
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            scrollCallback(scrollPosition, maxScrollY)
                        }
                    }
                    @android.webkit.JavascriptInterface
                    fun onScrollRestoreComplete() {
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            onScrollRestoreCompleted()
                        }
                    }
                    @android.webkit.JavascriptInterface
                    fun onNavigateToSection(sectionId: String) {
                        // Landing page icon tap: load search URL from Kotlin (file:// + custom scheme often doesn't trigger shouldOverrideUrlLoading)
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            val entries = landingEntriesRef.value
                            val section = LandingSection.fromSectionId(sectionId)
                            val entry = if (section != null && section.slotIndex < entries.size) {
                                entries[section.slotIndex]
                            } else {
                                null
                            }
                            val query = if (entry != null) {
                                buildSearchQuery(gameName, entry)
                            } else {
                                if (gameName.isNullOrBlank()) sectionId else "$gameName $sectionId"
                            }
                            val searchUrl = if (entry?.useImageSearch == true) {
                                buildImageSearchUrlForEngine(searchEngine, query)
                            } else {
                                buildSearchUrlForEngine(searchEngine, query)
                            }
                            this@apply.loadUrl(searchUrl)
                            onUrlChanged(searchUrl)
                        }
                    }
                    @android.webkit.JavascriptInterface
                    fun onRunSearch(query: String) {
                        // Recent search tap: run search with the saved query (full query string)
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            val q = query?.takeIf { it.isNotBlank() } ?: return@post
                            val searchUrl = buildSearchUrlForEngine(searchEngine, q)
                            this@apply.loadUrl(searchUrl)
                            onUrlChanged(searchUrl)
                        }
                    }

                    @android.webkit.JavascriptInterface
                    fun onEditLandingEntry(slotIndex: Int) {
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            onEditLandingSlot?.invoke(slotIndex)
                        }
                    }

                    @android.webkit.JavascriptInterface
                    fun onOpenSavedLink(linkId: String) {
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            onOpenSavedLink?.invoke(linkId)
                        }
                    }

                    @android.webkit.JavascriptInterface
                    fun onDeleteSavedLink(linkId: String) {
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            onDeleteSavedLink?.invoke(linkId)
                        }
                    }

                    @android.webkit.JavascriptInterface
                    fun findNext(query: String) {
                        val quotedQuery = JSONObject.quote(query)
                        // Better text search: find text nodes and highlight parent elements
                        evaluateJavascript("""
                            (function() {
                                var query = ($quotedQuery || '').toLowerCase();
                                if (!query) return;
                                
                                // Ensure consistent highlight styling that stands out on light/dark pages
                                var styleId = 'find-highlight-style';
                                if (!document.getElementById(styleId)) {
                                    var style = document.createElement('style');
                                    style.id = styleId;
                                    style.textContent = `
                                        .find-highlight {
                                            background-color: rgba(255, 235, 59, 0.38) !important;
                                            outline: 2px solid rgba(255, 193, 7, 0.85) !important;
                                            box-shadow: 0 0 0 2px rgba(255, 193, 7, 0.65), 0 0 12px rgba(255, 152, 0, 0.7);
                                            border-radius: 4px;
                                            transition: background-color 0.15s ease, outline-color 0.15s ease;
                                        }
                                        .find-highlight-current {
                                            background-color: rgba(255, 193, 7, 0.9) !important;
                                            outline: 3px solid rgba(255, 87, 34, 0.95) !important;
                                            box-shadow: 0 0 0 3px rgba(255, 87, 34, 0.75), 0 0 16px rgba(255, 87, 34, 0.85);
                                            border-radius: 4px;
                                        }
                                    `;
                                    document.head.appendChild(style);
                                }
                                
                                // Capture current highlighted element before clearing
                                var previouslyFocused = document.querySelector('.find-highlight-current') || document.querySelector('.find-highlight');
                                
                                // Clear previous highlights and inline overrides
                                document.querySelectorAll('.find-highlight, .find-highlight-current').forEach(function(el) {
                                    el.classList.remove('find-highlight');
                                    el.classList.remove('find-highlight-current');
                                    el.style.backgroundColor = '';
                                    el.style.outline = '';
                                    el.style.boxShadow = '';
                                });
                                
                                // Find all text nodes containing the query
                                var walker = document.createTreeWalker(
                                    document.body,
                                    NodeFilter.SHOW_TEXT,
                                    null,
                                    false
                                );
                                
                                var textNode;
                                var matches = [];
                                while (textNode = walker.nextNode()) {
                                    var text = textNode.textContent.toLowerCase();
                                    if (text.includes(query)) {
                                        var parent = textNode.parentElement;
                                        if (parent && !matches.includes(parent)) {
                                            matches.push(parent);
                                        }
                                    }
                                }
                                
                                if (matches.length === 0) return;
                                
                                // Mark all matches with the base highlight for visibility
                                matches.forEach(function(el) { el.classList.add('find-highlight'); });
                                
                                // Find current highlighted element
                                var currentIndex = previouslyFocused ? matches.indexOf(previouslyFocused) : -1;
                                
                                // Get next index
                                var nextIndex = (currentIndex + 1) % matches.length;
                                var nextElement = matches[nextIndex];
                                
                                // Highlight next match prominently
                                nextElement.classList.add('find-highlight-current');
                                nextElement.scrollIntoView({ behavior: 'smooth', block: 'center' });
                            })();
                        """.trimIndent(), null)
                    }
                    
                    @android.webkit.JavascriptInterface
                    fun findPrevious(query: String) {
                        val quotedQuery = JSONObject.quote(query)
                        // Similar to findNext but previous
                        evaluateJavascript("""
                            (function() {
                                var query = ($quotedQuery || '').toLowerCase();
                                if (!query) return;
                                
                                // Ensure consistent highlight styling that stands out on light/dark pages
                                var styleId = 'find-highlight-style';
                                if (!document.getElementById(styleId)) {
                                    var style = document.createElement('style');
                                    style.id = styleId;
                                    style.textContent = `
                                        .find-highlight {
                                            background-color: rgba(255, 235, 59, 0.38) !important;
                                            outline: 2px solid rgba(255, 193, 7, 0.85) !important;
                                            box-shadow: 0 0 0 2px rgba(255, 193, 7, 0.65), 0 0 12px rgba(255, 152, 0, 0.7);
                                            border-radius: 4px;
                                            transition: background-color 0.15s ease, outline-color 0.15s ease;
                                        }
                                        .find-highlight-current {
                                            background-color: rgba(255, 193, 7, 0.9) !important;
                                            outline: 3px solid rgba(255, 87, 34, 0.95) !important;
                                            box-shadow: 0 0 0 3px rgba(255, 87, 34, 0.75), 0 0 16px rgba(255, 87, 34, 0.85);
                                            border-radius: 4px;
                                        }
                                    `;
                                    document.head.appendChild(style);
                                }
                                
                                // Capture current highlighted element before clearing
                                var previouslyFocused = document.querySelector('.find-highlight-current') || document.querySelector('.find-highlight');
                                
                                // Clear previous highlights and inline overrides
                                document.querySelectorAll('.find-highlight, .find-highlight-current').forEach(function(el) {
                                    el.classList.remove('find-highlight');
                                    el.classList.remove('find-highlight-current');
                                    el.style.backgroundColor = '';
                                    el.style.outline = '';
                                    el.style.boxShadow = '';
                                });
                                
                                // Find all text nodes containing the query
                                var walker = document.createTreeWalker(
                                    document.body,
                                    NodeFilter.SHOW_TEXT,
                                    null,
                                    false
                                );
                                
                                var textNode;
                                var matches = [];
                                while (textNode = walker.nextNode()) {
                                    var text = textNode.textContent.toLowerCase();
                                    if (text.includes(query)) {
                                        var parent = textNode.parentElement;
                                        if (parent && !matches.includes(parent)) {
                                            matches.push(parent);
                                        }
                                    }
                                }
                                
                                if (matches.length === 0) return;
                                
                                // Mark all matches with the base highlight for visibility
                                matches.forEach(function(el) { el.classList.add('find-highlight'); });
                                
                                // Find current highlighted element
                                var currentIndex = previouslyFocused ? matches.indexOf(previouslyFocused) : -1;
                                
                                // Get previous index
                                var prevIndex = (currentIndex - 1 + matches.length) % matches.length;
                                var prevElement = matches[prevIndex];
                                
                                // Highlight previous match prominently
                                prevElement.classList.add('find-highlight-current');
                                prevElement.scrollIntoView({ behavior: 'smooth', block: 'center' });
                            })();
                        """.trimIndent(), null)
                    }
                    
                    @android.webkit.JavascriptInterface
                    fun clearHighlights() {
                        evaluateJavascript("""
                            (function() {
                                document.querySelectorAll('.find-highlight, .find-highlight-current').forEach(function(el) {
                                    el.classList.remove('find-highlight');
                                    el.classList.remove('find-highlight-current');
                                    el.style.backgroundColor = '';
                                    el.style.outline = '';
                                    el.style.boxShadow = '';
                                });
                            })();
                        """.trimIndent(), null)
                    }
                    @android.webkit.JavascriptInterface
                    fun onScrollMarkerHighlightClicked(markerIndex: Int) {
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            scrollMarkerHighlightTapRef.value?.invoke(markerIndex)
                        }
                    }
                }, "Android")
                
                // Expose functions as window methods for easier calling
                evaluateJavascript("""
                    window.findNext = function(query) {
                        Android.findNext(query);
                    };
                    window.findPrevious = function(query) {
                        Android.findPrevious(query);
                    };
                    window.clearHighlights = function() {
                        Android.clearHighlights();
                    };
                """.trimIndent(), null)
                
                var lastTapTime = 0L
                var lastTapX = 0f
                var lastTapY = 0f
                var touchDownX = 0f
                var touchDownY = 0f
                var movedBeyondTapSlop = false
                val tapSlopPx = android.view.ViewConfiguration.get(ctx).scaledTouchSlop
                // Delayed top-bar toggle: allows JS block-tap handler to cancel it
                val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
                var pendingTouchRunnable: Runnable? = null
                if (vaultSessionMode == VaultSessionMode.Browser) {
                    // Consume long-click ONLY when the target is an image or anchor — that
                    // suppresses Android's native action-mode pass (the source of the
                    // image-menu freeze, where the WebView entered a selection state that
                    // swallowed the next tap as "dismiss selection") without breaking the
                    // long-press-to-select-text affordance on regular page text. Returning
                    // false for non-image/non-anchor targets lets the WebView's native text
                    // selection menu fire normally. JS touch handlers still fire either way
                    // (they live at the WebKit layer, below this listener).
                    setOnLongClickListener { view ->
                        val type = (view as? WebView)?.hitTestResult?.type
                        type == WebView.HitTestResult.IMAGE_TYPE ||
                            type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE ||
                            type == WebView.HitTestResult.SRC_ANCHOR_TYPE
                    }
                    isLongClickable = true
                }
                setOnTouchListener { _, event ->
                    when (event.action) {
                        android.view.MotionEvent.ACTION_DOWN -> {
                            if (vaultSessionMode == VaultSessionMode.Guide) {
                                var parent: ViewParent? = this@apply.parent
                                while (true) {
                                    val current = parent ?: break
                                    (current as? android.view.ViewGroup)?.requestDisallowInterceptTouchEvent(true)
                                    parent = current.parent
                                }
                            } else if (vaultSessionMode == VaultSessionMode.Browser) {
                                var parent: ViewParent? = this@apply.parent
                                while (true) {
                                    val current = parent ?: break
                                    (current as? android.view.ViewGroup)?.requestDisallowInterceptTouchEvent(true)
                                    parent = current.parent
                                }
                            }
                            touchDownX = event.x
                            touchDownY = event.y
                            movedBeyondTapSlop = false
                        }
                        android.view.MotionEvent.ACTION_MOVE -> {
                            if (!movedBeyondTapSlop) {
                                val dx = event.x - touchDownX
                                val dy = event.y - touchDownY
                                if (dx * dx + dy * dy > tapSlopPx * tapSlopPx) {
                                    movedBeyondTapSlop = true
                                    if (vaultSessionMode == VaultSessionMode.Guide || vaultSessionMode == VaultSessionMode.Browser) {
                                        var parent: ViewParent? = this@apply.parent
                                        while (true) {
                                            val current = parent ?: break
                                            (current as? android.view.ViewGroup)?.requestDisallowInterceptTouchEvent(true)
                                            parent = current.parent
                                        }
                                    }
                                }
                            }
                        }
                        android.view.MotionEvent.ACTION_UP -> {
                            val now = System.currentTimeMillis()
                            val x = event.x
                            val y = event.y
                            val doubleTapTimeout = 300L
                            val doubleTapSlopPx = 50 * ctx.resources.displayMetrics.density
                            if (lastTapTime > 0 &&
                                now - lastTapTime < doubleTapTimeout &&
                                kotlin.math.abs(x - lastTapX) <= doubleTapSlopPx &&
                                kotlin.math.abs(y - lastTapY) <= doubleTapSlopPx) {
                                // Reset view (pinch) zoom to 1x, not page/text zoom
                                this@apply.run {
                                    @Suppress("DEPRECATION")
                                    val currentScale = scale
                                    if (currentScale > 0f) zoomBy(1f / currentScale)
                                }
                                resetZoomCallback?.invoke()
                                lastTapTime = 0L
                            } else {
                                lastTapTime = now
                                lastTapX = x
                                lastTapY = y
                            }
                            // Delay top-bar toggle to let JS block-tap handler suppress it.
                            // The JS click event fires after native ACTION_UP; if the tap was
                            // on an ASCII scroll-block, onBlockToggled() sets the flag and we skip.
                            if (!movedBeyondTapSlop) {
                                pendingTouchRunnable?.let { mainHandler.removeCallbacks(it) }
                                blockTappedFlag = false
                                val runnable = Runnable {
                                    if (!blockTappedFlag) {
                                        onTouch?.invoke()
                                    }
                                    blockTappedFlag = false
                                }
                                pendingTouchRunnable = runnable
                                mainHandler.postDelayed(runnable, 120L)
                            }
                        }
                    }
                    false
                }
                
                webChromeClient = object : WebChromeClient() {
                    private var customView: View? = null
                    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
                    private var customViewDialog: Dialog? = null
                    
                    // Links with target="_blank" (new tab): open in current tab. Reusing the same WebView causes a crash
                    // in Chromium after return, so we use a temporary WebView to capture the URL then load it in the main view.
                    override fun onCreateWindow(
                        view: WebView?,
                        isDialog: Boolean,
                        isUserGesture: Boolean,
                        resultMsg: Message?
                    ): Boolean {
                        // Defense-in-depth alongside setJavaScriptCanOpenWindowsAutomatically(false):
                        // even if a popunder slips through (e.g. attached to a synthetic event), refuse
                        // to surface it unless the user actively gestured. Genuine target="_blank"/_new
                        // anchor taps and JS window.open() inside a click handler both report true.
                        if (!isUserGesture) return false
                        return try {
                            if (resultMsg == null || view == null) {
                                false
                            } else {
                                val transport = resultMsg.obj as? WebView.WebViewTransport
                                if (transport == null) {
                                    false
                                } else {
                                    val parentView = view
                                    val ctx = parentView.context
                                    var tempUrlCaptured = false
                                    val tempWebView = WebView(ctx).apply {
                                        settings.javaScriptEnabled = true
                                        settings.domStorageEnabled = true
                                        webViewClient = object : WebViewClient() {
                                            override fun shouldOverrideUrlLoading(
                                                wv: WebView?,
                                                request: android.webkit.WebResourceRequest?
                                            ): Boolean {
                                                if (request?.isForMainFrame == true && !tempUrlCaptured) {
                                                    val url = request.url?.toString()
                                                    if (!url.isNullOrBlank()) {
                                                        tempUrlCaptured = true
                                                        if (onOpenInNewTab != null) {
                                                            onOpenInNewTab(url)
                                                        } else {
                                                            onNewWindowLoadInCurrentTab()
                                                            parentView.post { parentView.loadUrl(url) }
                                                        }
                                                    }
                                                    wv?.post { wv.destroy() }
                                                    return true
                                                }
                                                return false
                                            }
                                            // Fallback: some sites (Reddit, etc.) don't trigger shouldOverrideUrlLoading
                                            // for JavaScript-initiated navigations. Capture the URL when the page starts loading.
                                            override fun onPageStarted(wv: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                                super.onPageStarted(wv, url, favicon)
                                                if (!tempUrlCaptured && !url.isNullOrBlank() && url != "about:blank") {
                                                    tempUrlCaptured = true
                                                    if (onOpenInNewTab != null) {
                                                        onOpenInNewTab(url)
                                                    } else {
                                                        onNewWindowLoadInCurrentTab()
                                                        parentView.post { parentView.loadUrl(url) }
                                                    }
                                                    wv?.post { wv.stopLoading(); wv.destroy() }
                                                }
                                            }
                                        }
                                    }
                                    transport.setWebView(tempWebView)
                                    resultMsg.sendToTarget()
                                    true
                                }
                            }
                        } catch (e: Exception) {
                            false
                        }
                    }
                    
                    override fun onShowCustomView(view: View?, callback: WebChromeClient.CustomViewCallback?) {
                        if (customView != null) {
                            onHideCustomView()
                            return
                        }
                        if (view == null || callback == null) return
                        val activity = contextAsActivity(ctx) ?: return
                        customView = view
                        customViewCallback = callback
                        customViewDialog = Dialog(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen).apply {
                            setCancelable(true)
                            setCanceledOnTouchOutside(true)
                            setContentView(view, ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            ))
                            setOnDismissListener {
                                customViewCallback?.onCustomViewHidden()
                                customViewCallback = null
                                customView = null
                                customViewDialog = null
                            }
                        }
                        customViewDialog?.show()
                    }
                    
                    override fun onHideCustomView() {
                        customViewDialog?.dismiss()
                        customViewDialog = null
                    }

                    override fun onReceivedTitle(view: WebView?, title: String?) {
                        super.onReceivedTitle(view, title)
                        val cb = onTitleReceived ?: return
                        val t = title ?: return
                        if (t.isNotBlank()) cb(t)
                    }

                    override fun onReceivedIcon(view: WebView?, icon: android.graphics.Bitmap?) {
                        super.onReceivedIcon(view, icon)
                        val cb = onFaviconReceived ?: return
                        val bmp = icon ?: return
                        try {
                            val baos = java.io.ByteArrayOutputStream()
                            bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, baos)
                            cb(baos.toByteArray())
                        } catch (_: Throwable) {
                            // Best-effort: drop on encode failure
                        }
                    }

                    override fun onJsAlert(view: WebView?, url: String?, message: String?, result: android.webkit.JsResult?): Boolean {
                        return super.onJsAlert(view, url, message, result)
                    }
                    override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: android.webkit.JsResult?): Boolean {
                        return super.onJsConfirm(view, url, message, result)
                    }
                    override fun onJsPrompt(view: WebView?, url: String?, message: String?, defaultValue: String?, result: android.webkit.JsPromptResult?): Boolean {
                        return super.onJsPrompt(view, url, message, defaultValue, result)
                    }
                }
                
                webViewClient = object : WebViewClient() {
                    // Track the last base URL (without fragment) that completed a full
                    // onPageFinished cycle. Fragment-only changes (e.g. Reddit #lightbox,
                    // Neoseeker #&gid=1&pid=2) fire onPageFinished but should not re-run
                    // textZoom, scroll restore, image-handler injection, or delayed callbacks.
                    var lastFinishedBaseUrl: String? = null

                    override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                        val url = request?.url?.toString()
                        val isMain = request?.isForMainFrame == true
                        // Browser-mode only: surface the spinner at navigation initiation
                        // (vs. waiting for onPageStarted, which has perceptible lag). Skip
                        // for Guide mode because external link clicks there are intercepted
                        // below (routed to the Browser tab) and never reach onPageStarted/
                        // onPageFinished on the Guide's own WebView — firing setLoading=true
                        // here would leave the Guide's progress bar stuck on indefinitely.
                        if (vaultSessionMode == VaultSessionMode.Browser &&
                            url != null && url != "about:blank" && isMain
                        ) {
                            onLoadingChanged(true)
                        }
                        if (isMain && !url.isNullOrBlank() && !isAllowedWebViewUrl(url)) {
                            android.util.Log.w("GuideBrowser", "Blocked unsupported URL scheme: $url")
                            return true
                        }
                        // Guide mode: guides load offline, handle links appropriately
                        if (vaultSessionMode == VaultSessionMode.Guide && isMain && url != null) {
                            // Allow anchor/fragment links (#section) — navigate within the page.
                            // When loadDataWithBaseURL is active, view.url is unreliable (may be about:blank or data:).
                            // Use the original bookmark URL instead.
                            val currentBase = if (isDataUrlLoadedRef.value) {
                                currentBookmarkUrlRef.value?.substringBefore("#")?.substringBefore("?") ?: ""
                            } else {
                                view?.url?.substringBefore("#") ?: ""
                            }
                            val targetBase = url.substringBefore("#")
                            if (url.contains("#") && (targetBase.isBlank() || targetBase == currentBase)) {
                                return false // Let WebView handle in-page anchor navigation
                            }
                            // Same-page anchor with a fully-qualified URL (or url+query) that
                            // matches the saved bookmark's original URL — e.g. MHT-saved pages
                            // whose internal links are written as
                            // <a href="https://www.gamefaqs.com/.../faqs/77032#section5">.
                            // Without this, the comparison above fails (currentBase is the
                            // local file:// URL of the cached index.html) and the link would
                            // fall through to the "External HTTP link" branch below and open
                            // in the Browser tab. Scroll to the anchor inside the offline doc
                            // instead.
                            val bookmarkBase = currentBookmarkUrlRef.value
                                ?.substringBefore("#")?.substringBefore("?")
                            if (url.contains("#") && !bookmarkBase.isNullOrBlank() &&
                                url.substringBefore("#").substringBefore("?") == bookmarkBase
                            ) {
                                val fragment = url.substringAfter("#", "")
                                if (fragment.isNotEmpty()) {
                                    view?.evaluateJavascript(anchorScrollScript(fragment), null)
                                }
                                return true
                            }
                            // Local file:// or content:// links: only allow same-file navigation.
                            // Relative links in saved HTML resolve to file:// paths for pages we don't have.
                            if (url.startsWith("file://") || url.startsWith("content://")) {
                                // Same file (different anchor or reload) — allow
                                if (targetBase == currentBase) return false
                                // Check if this is a same-page link that resolved to a different
                                // file:// path. This happens when saved HTML contains full-path
                                // links like href="/snes/.../faqs/77032#section5" which resolve
                                // to file:///snes/.../faqs/77032#section5 instead of the local
                                // index.html. Compare the link's path against the original page
                                // URL's path to detect this case.
                                val originalUrl = currentBookmarkUrlRef.value
                                val originalPath = try {
                                    android.net.Uri.parse(originalUrl ?: "").path?.trimEnd('/')
                                } catch (_: Exception) { null }
                                if (originalPath != null) {
                                    val linkPath = try {
                                        android.net.Uri.parse(url.substringBefore("#").substringBefore("?")).path?.trimEnd('/')
                                    } catch (_: Exception) { null }
                                    if (linkPath != null && linkPath == originalPath) {
                                        // Same original page — handle as in-page navigation
                                        val fragment = if (url.contains("#")) url.substringAfter("#", "") else ""
                                        if (fragment.isNotEmpty()) {
                                            // Scroll to the anchor element
                                            view?.evaluateJavascript(anchorScrollScript(fragment), null)
                                        }
                                        // Either scrolled to anchor or same-page reload — consume the event
                                        return true
                                    }
                                }
                                // Different file — check if it actually exists on disk
                                try {
                                    val path = android.net.Uri.parse(url).path
                                    if (path != null && java.io.File(path).exists()) return false
                                } catch (_: Exception) {}
                                // File doesn't exist — try to reconstruct the original HTTP URL
                                // and open it in the browser tab so the user can view it online.
                                val bookmarkOriginalUrl = currentBookmarkUrlRef.value
                                if (bookmarkOriginalUrl != null && bookmarkOriginalUrl.startsWith("http")) {
                                    try {
                                        val originalUri = android.net.Uri.parse(bookmarkOriginalUrl)
                                        val filePath = android.net.Uri.parse(url).path ?: ""
                                        // Reconstruct: use the original page's scheme + authority + the link's path
                                        val reconstructedUrl = originalUri.buildUpon()
                                            .path(filePath)
                                            .build()
                                            .toString()
                                        val openInBrowser = openLinkInBrowserRef.value
                                        if (openInBrowser != null) {
                                            openInBrowser.invoke(reconstructedUrl)
                                        } else {
                                            try {
                                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(reconstructedUrl))
                                                view?.context?.startActivity(intent)
                                            } catch (_: Exception) {}
                                        }
                                        return true
                                    } catch (e: Exception) {
                                        android.util.Log.w("GuideLink", "Failed to reconstruct URL from offline link: $url", e)
                                    }
                                }
                                // Fallback: could not reconstruct URL — show toast
                                android.widget.Toast.makeText(view?.context ?: return true, "Linked page not saved offline", android.widget.Toast.LENGTH_SHORT).show()
                                return true
                            }
                            // External HTTP link — open in the browser tab
                            val openInBrowser = openLinkInBrowserRef.value
                            if (openInBrowser != null) {
                                openInBrowser.invoke(url)
                            } else {
                                try {
                                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                                    view?.context?.startActivity(intent)
                                } catch (_: Exception) {}
                            }
                            return true
                        }
                        if (url != null && url.startsWith("vault://section/")) {
                            // Handle navigation icon clicks from landing page: <game-name> <appendWord>, web or image search
                            val sectionId = url.removePrefix("vault://section/")
                            val entries = landingEntriesRef.value
                            val section = LandingSection.fromSectionId(sectionId)
                            val entry = if (section != null && section.slotIndex < entries.size) {
                                entries[section.slotIndex]
                            } else {
                                null
                            }
                            val query = if (entry != null) {
                                buildSearchQuery(gameName, entry)
                            } else {
                                if (gameName.isNullOrBlank()) sectionId else "$gameName $sectionId"
                            }
                            val searchUrl = if (entry?.useImageSearch == true) {
                                buildImageSearchUrlForEngine(searchEngine, query)
                            } else {
                                buildSearchUrlForEngine(searchEngine, query)
                            }
                            view?.loadUrl(searchUrl)
                            onUrlChanged(searchUrl)
                            return true // Intercept the URL
                        }
                        // Use desktop UA only for image/image-result URLs so CDNs accept; keep mobile UA for search etc.
                        if (isMain && url != null) {
                            view?.settings?.userAgentString = if (isImageOrImageResultUrl(url)) DESKTOP_USER_AGENT else defaultUserAgent
                            // Algorithmic darkening is disabled globally — CSS injection handles theming
                            // User navigated in Browser (link click etc.); clear one-off so we save this page when leaving
                            if (vaultSessionMode == VaultSessionMode.Browser && url != oneOffUrlBeingLoaded?.value) {
                                browserOneOffLoad?.value = false
                            }
                        }
                        val overridden = super.shouldOverrideUrlLoading(view, request)
                        return overridden
                    }
                    
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        // Fragment-only change detection: if the base URL (without #fragment) is
                        // the same as the last page we fully processed, this is an in-page hash
                        // change (e.g. Reddit #lightbox, Neoseeker image gallery). Skip all heavy
                        // processing (textZoom, scroll restore, JS re-injection, delayed callbacks)
                        // to avoid disrupting the site's own overlay/lightbox behavior.
                        val currentBaseUrl = url?.substringBefore("#")
                        if (currentBaseUrl != null && currentBaseUrl == lastFinishedBaseUrl) {
                            if (url != null) onUrlChanged(url)
                            view?.post {
                                onCanGoBackChanged(view.canGoBack())
                                onCanGoForwardChanged(view.canGoForward())
                            }
                            onPageFinished?.invoke(view, url)
                            return
                        }
                        lastFinishedBaseUrl = currentBaseUrl

                        // Theme CSS injection for all guide pages when toggle is ON
                        if (view != null && url != null && !url.contains("landing.html") &&
                            vaultSessionMode == VaultSessionMode.Guide && currentGuideThemeInject) {
                            view.evaluateJavascript(buildPaletteCssJs(currentPaletteBgHex, currentPaletteFgHex, currentAppFont), null)
                        }
                        // Clear one-off marker once the page we loaded as one-off has finished
                        if (url != null && url == oneOffUrlBeingLoaded?.value) {
                            oneOffUrlBeingLoaded?.value = null
                        }
                        // Landing page: inject current recent searches (so list is fresh after a search) and nav handlers
                        if (view != null && url != null && url.contains("landing.html")) {
                            val recentSearches = searchHistoryManager.getSearchHistory(vaultId).take(3)
                            val recentHtml = buildRecentSearchesHtml(recentSearches)
                            val recentHtmlQuoted = org.json.JSONObject.quote(recentHtml)
                            view.evaluateJavascript("(function(){ var list = document.querySelector('.recent-searches-list'); if (list) list.innerHTML = $recentHtmlQuoted; if (typeof attachNavHandlers === 'function') attachNavHandlers(); })();", null)
                        }
                        // Selection rect for floating action bar: report selection bounds so the toolbar can be positioned above/below the selection
                        if (view != null && url != null) {
                            view.evaluateJavascript(SELECTION_RECT_JS, null)
                        }
                        
                        // GameFAQs HTML guides (Guide mode only): clean up header, make images
                        // fit-to-width with tap-to-expand, and apply palette CSS.
                        // NOTE: GameFAQs DOM cleanup (nav/footer removal, image-fit, palette CSS)
                        // was previously injected here for all Guide-mode pages. Removed — offline
                        // guides (MHT/HTML) now render as-is without modification. Palette CSS
                        // injection is limited to text-based guides via the isReaderMode check above.
                        
                        // TOC extraction for already-converted text FAQ pages (loaded from cache).
                        // Text FAQ detection and conversion has moved to Kotlin-side (GuideScreen.kt loadGuideBookmark).
                        // Only for OFFLINE guides (file://, loadDataWithBaseURL) in Guide mode.
                        val isOfflineGuide = url != null && !url.startsWith("http://") && !url.startsWith("https://")
                        if (view != null && url != null && isOfflineGuide && vaultSessionMode != VaultSessionMode.Browser) {
                            val targetView = view
                            // Check if this is an already-converted text FAQ (loaded from cache).
                            // If so, extract TOC entries from the generated anchors.
                            targetView.evaluateJavascript("""
                                (function() {
                                    if (document.body && document.body.getAttribute('data-gamefaqs-converted')) {
                                        return 'already_converted';
                                    }
                                    return 'not_converted';
                                })();
                            """.trimIndent()) { detectResult ->
                                val result = detectResult?.trim()?.removeSurrounding("\"")
                                if (result == "already_converted") {
                                    targetView.evaluateJavascript("""
                                        (function() {
                                            var headings = document.querySelectorAll('.toc-heading[id]');
                                            var entries = [];
                                            for (var i = 0; i < headings.length; i++) {
                                                var h = headings[i];
                                                var rect = h.getBoundingClientRect();
                                                var scrollY = Math.round(rect.top + window.scrollY);
                                                var idStr = h.id || '';
                                                var label = h.getAttribute('data-title') || h.textContent || ('Section ' + (i+1));
                                                entries.push({ label: label, scrollY: scrollY, id: idStr });
                                            }
                                            return JSON.stringify(entries);
                                        })();
                                    """.trimIndent()) { tocResult ->
                                        val jsonStr = unescapeJsonString(tocResult)?.trim() ?: "[]"
                                        val entries = try {
                                            val arr = org.json.JSONArray(jsonStr)
                                            (0 until arr.length()).map { i ->
                                                val obj = arr.getJSONObject(i)
                                                val idStr = obj.optString("id", "")
                                                val secIndex = idStr.removePrefix("sec-").toIntOrNull() ?: -1
                                                TocEntry(
                                                    label = obj.optString("label", "Section ${i + 1}"),
                                                    scrollY = obj.optInt("scrollY", 0),
                                                    lineIndex = secIndex
                                                )
                                            }
                                        } catch (_: Exception) { emptyList() }
                                        if (entries.isNotEmpty()) {
                                            val withBoundaries = buildList {
                                                add(TocEntry(label = "Top", scrollY = 0, lineIndex = -1))
                                                addAll(entries)
                                                add(TocEntry(label = "End", scrollY = Int.MAX_VALUE, lineIndex = -1))
                                            }
                                            onTextFaqTocDetected(withBoundaries)
                                        }
                                    }
                                }
                            }
                        }
                        
                        if (url != null) {
                            onUrlChanged(url)
                        }
                        
                        // If no scroll restore payload, hide loading immediately (use getter so offline switch sees updated value)
                        // BUT: only call onScrollRestoreCompleted for actual guide pages, NOT for landing.html
                        // or other non-guide pages, because calling it clears pendingScrollRestore which may be
                        // needed by a guide page that's loading concurrently.
                        val payloadAtFinish = getSavedScrollRestore()
                        val isLandingOrNonGuide = url != null && (url.contains("landing.html") || url.contains("/guide/"))
                        // Skip early completion for loadDataWithBaseURL pages — onPageFinished fires
                        // before the coroutine sets pendingScrollRestore, so payloadAtFinish is null
                        // prematurely. Let the delayed restore block handle the full flow.
                        if (payloadAtFinish == null && !isLandingOrNonGuide && !isDataUrlLoadedRef.value) {
                            onScrollRestoreCompleted()
                        }
                        // For landing page, ensure it's treated as the first page in history (home)
                        // Don't clear history here - let it build naturally
                        // But ensure back button behavior treats it as home
                        // Update back/forward state - this is critical for back button behavior
                        // Use a small delay to ensure WebView has updated its history state
                        view?.post {
                            onCanGoBackChanged(view.canGoBack())
                            onCanGoForwardChanged(view.canGoForward())
                        }
                        // Keep loading indicator visible until restore runs; defer onLoadingChanged(false) to the restore block so UX is one flow: Loading… → Restoring…
                        if (payloadAtFinish == null || isImageOrNonDocumentUrl(url)) {
                            onLoadingChanged(false)
                        }
                        view?.settings?.textZoom = (zoomLevelRef.value * 100).toInt()

                        // Add image long-press detection
                        val linkHandlerJs = if (vaultSessionMode == VaultSessionMode.Browser) {
                            """
                            // Link long-press: mirrors the image handler but targets the nearest
                            // enclosing <a> element. The native long-press / context menu is
                            // already suppressed by setOnLongClickListener at the WebView level
                            //, so no preventDefault on touchstart is needed here
                            // (which would block scroll gestures starting on a link).
                            if (window._linkContextMenuHandler) {
                                document.removeEventListener('touchstart', window._linkTouchStartHandler, true);
                                document.removeEventListener('touchmove', window._linkTouchMoveHandler, true);
                                document.removeEventListener('touchend', window._linkTouchEndHandler, true);
                            }

                            var linkTouchStartTime = 0;
                            var linkTouchStartAnchor = null;
                            var linkTouchStartX = 0;
                            var linkTouchStartY = 0;
                            var linkHasMoved = false;

                            window._linkTouchStartHandler = function(e) {
                                // If the touch target is an IMG, defer to the image handler entirely.
                                if (e.target.tagName === 'IMG') return;
                                var a = e.target.closest && e.target.closest('a');
                                if (a && a.href) {
                                    // Intentionally NO preventDefault here — that would block scroll
                                    // gestures that begin on a link. The native long-press / context
                                    // menu is already suppressed by setOnLongClickListener at the
                                    // WebView level.
                                    linkTouchStartTime = Date.now();
                                    linkTouchStartAnchor = a;
                                    linkTouchStartX = e.touches[0].clientX;
                                    linkTouchStartY = e.touches[0].clientY;
                                    linkHasMoved = false;
                                }
                            };

                            window._linkTouchMoveHandler = function(e) {
                                if (linkTouchStartAnchor) {
                                    var currentX = e.touches[0].clientX;
                                    var currentY = e.touches[0].clientY;
                                    var deltaX = Math.abs(currentX - linkTouchStartX);
                                    var deltaY = Math.abs(currentY - linkTouchStartY);
                                    var movement = Math.sqrt(deltaX * deltaX + deltaY * deltaY);
                                    if (movement > 15) {
                                        linkHasMoved = true;
                                        linkTouchStartTime = 0;
                                        linkTouchStartAnchor = null;
                                    }
                                }
                            };

                            window._linkTouchEndHandler = function(e) {
                                if (linkTouchStartAnchor && !linkHasMoved) {
                                    var touchDuration = Date.now() - linkTouchStartTime;
                                    if (touchDuration >= 500) {
                                        e.preventDefault();
                                        e.stopPropagation();
                                        if (androidInterface && androidInterface.onLinkLongPress) {
                                            androidInterface.onLinkLongPress(
                                                linkTouchStartAnchor.href,
                                                linkTouchStartAnchor.textContent || ''
                                            );
                                        }
                                    }
                                }
                                linkTouchStartTime = 0;
                                linkTouchStartAnchor = null;
                                linkTouchStartX = 0;
                                linkTouchStartY = 0;
                                linkHasMoved = false;
                            };

                            // Marker var used by the install-once check above on the next injection.
                            window._linkContextMenuHandler = true;

                            document.addEventListener('touchstart', window._linkTouchStartHandler, true);
                            document.addEventListener('touchmove', window._linkTouchMoveHandler, true);
                            document.addEventListener('touchend', window._linkTouchEndHandler, true);
                            """.trimIndent()
                        } else {
                            ""
                        }
                        // Guide-mode only: tap on <img> opens the in-app pinch-to-zoom viewer
                        // instead of falling through to any wrapping <a href> navigation. Runs
                        // in capture phase so it fires before the browser's default link-click
                        // navigation; preventDefault stops that navigation. Long-press still
                        // works (the image touch-end handler above fires after 500ms hold and
                        // is responsible for the existing image context menu — when present in
                        // Guide mode, it's a no-op because onImageLongPress is null there).
                        val guideImageTapHandlerJs = if (vaultSessionMode == VaultSessionMode.Guide) {
                            """
                            if (window._guideImageTapHandler) {
                                document.removeEventListener('click', window._guideImageTapHandler, true);
                            }
                            window._guideImageTapHandler = function(e) {
                                var t = e.target;
                                if (t && t.tagName === 'IMG' && t.src) {
                                    e.preventDefault();
                                    e.stopPropagation();
                                    if (androidInterface && androidInterface.onGuideImageTap) {
                                        androidInterface.onGuideImageTap(t.src);
                                    }
                                }
                            };
                            document.addEventListener('click', window._guideImageTapHandler, true);
                            """.trimIndent()
                        } else {
                            ""
                        }
                        view?.postDelayed({
                            view.evaluateJavascript("""
                                (function() {
                                    var FOCUS_COLOR = '${focusColorHex}';
                                    // Store reference to Android interface
                                    var androidInterface = window.Android;
                                    
                                    // Remove existing listeners if any
                                    if (window._imageContextMenuHandler) {
                                        document.removeEventListener('contextmenu', window._imageContextMenuHandler);
                                        document.removeEventListener('touchstart', window._imageTouchStartHandler);
                                        document.removeEventListener('touchmove', window._imageTouchMoveHandler);
                                        document.removeEventListener('touchend', window._imageTouchEndHandler);
                                    }
                                    
                                    // Context menu handler for image long-press
                                    window._imageContextMenuHandler = function(e) {
                                        var target = e.target;
                                        if (target.tagName === 'IMG' && target.src) {
                                            e.preventDefault();
                                            e.stopPropagation();
                                            if (androidInterface && androidInterface.onImageLongPress) {
                                                androidInterface.onImageLongPress(target.src);
                                            }
                                            return false;
                                        }
                                    };
                                    
                                    // Touch long-press handlers with scroll detection
                                    var touchStartTime = 0;
                                    var touchStartTarget = null;
                                    var touchStartX = 0;
                                    var touchStartY = 0;
                                    var hasMoved = false;
                                    
                                    window._imageTouchStartHandler = function(e) {
                                        if (e.target.tagName === 'IMG' && e.target.src) {
                                            touchStartTime = Date.now();
                                            touchStartTarget = e.target;
                                            touchStartX = e.touches[0].clientX;
                                            touchStartY = e.touches[0].clientY;
                                            hasMoved = false;
                                        }
                                    };
                                    
                                    window._imageTouchMoveHandler = function(e) {
                                        if (touchStartTarget && e.target === touchStartTarget) {
                                            var currentX = e.touches[0].clientX;
                                            var currentY = e.touches[0].clientY;
                                            var deltaX = Math.abs(currentX - touchStartX);
                                            var deltaY = Math.abs(currentY - touchStartY);
                                            var movement = Math.sqrt(deltaX * deltaX + deltaY * deltaY);
                                            
                                            // If moved more than threshold (15px), cancel long-press
                                            if (movement > 15) {
                                                hasMoved = true;
                                                touchStartTime = 0;
                                                touchStartTarget = null;
                                            }
                                        }
                                    };
                                    
                                    window._imageTouchEndHandler = function(e) {
                                        if (touchStartTarget && e.target === touchStartTarget && !hasMoved) {
                                            var touchDuration = Date.now() - touchStartTime;
                                            // Only trigger if held for 500ms AND no movement detected
                                            if (touchDuration >= 500) {
                                                e.preventDefault();
                                                e.stopPropagation();
                                                if (androidInterface && androidInterface.onImageLongPress) {
                                                    androidInterface.onImageLongPress(touchStartTarget.src);
                                                }
                                            }
                                        }
                                        // Reset
                                        touchStartTime = 0;
                                        touchStartTarget = null;
                                        touchStartX = 0;
                                        touchStartY = 0;
                                        hasMoved = false;
                                    };
                                    
                                    // Add event listeners
                                    document.addEventListener('contextmenu', window._imageContextMenuHandler, true);
                                    document.addEventListener('touchstart', window._imageTouchStartHandler, true);
                                    document.addEventListener('touchmove', window._imageTouchMoveHandler, true);
                                    document.addEventListener('touchend', window._imageTouchEndHandler, true);
                                    
                                    // Scroll position tracking - report scroll position changes to Android.
                                    // Uses requestAnimationFrame throttling so the Kotlin watcher (chrome auto-hide,
                                    // TOC ratio) gets updates DURING the scroll gesture, not only after it ends.
                                    var lastReportedScroll = -1;
                                    var scrollRafScheduled = false;

                                    function reportScrollPosition() {
                                        var scrollTop = window.pageYOffset || document.documentElement.scrollTop || document.body.scrollTop || 0;
                                        var docHeight = Math.max(document.body.scrollHeight || 0, document.documentElement.scrollHeight || 0);
                                        var viewportHeight = window.innerHeight || 0;
                                        var maxScroll = Math.max(0, docHeight - viewportHeight);
                                        // Filter pixel-noise so a single finger-down doesn't trigger a spurious report,
                                        // but keep the threshold small enough that direction changes are seen quickly.
                                        if (Math.abs(scrollTop - lastReportedScroll) > 4) {
                                            lastReportedScroll = scrollTop;
                                            if (androidInterface && androidInterface.onScrollChanged) {
                                                androidInterface.onScrollChanged(Math.round(scrollTop), Math.round(maxScroll));
                                            }
                                        }
                                    }

                                    // requestAnimationFrame throttle: at most one report per frame (~16 ms).
                                    window._scrollHandler = function() {
                                        if (scrollRafScheduled) return;
                                        scrollRafScheduled = true;
                                        requestAnimationFrame(function() {
                                            scrollRafScheduled = false;
                                            reportScrollPosition();
                                        });
                                    };

                                    // Listen to scroll events (passive to avoid blocking the scroll).
                                    window.addEventListener('scroll', window._scrollHandler, { passive: true });
                                    // touchmove uses the same rAF-throttled handler.
                                    window.addEventListener('touchmove', window._scrollHandler, { passive: true });

                                    // Report initial scroll position
                                    setTimeout(reportScrollPosition, 100);

                                    $linkHandlerJs
                                    $guideImageTapHandlerJs
                                })();
                            """.trimIndent(), null)
                        }, 500) // Delay to ensure page is fully loaded
                        
                        // Restore scroll (anchor + ratio) when we have a payload
                        // Skip entirely for landing pages — they should never trigger scroll restore
                        // or clear pending restore state (a guide page may be loading concurrently)
                        val targetView = view
                        val isLandingPage = url != null && (url.contains("landing.html") || url.contains("/guide/landing"))
                        if (isLandingPage) {
                            onLoadingChanged(false)
                            // Do NOT call onScrollRestoreCompleted — don't clear pending restore for the actual guide
                            // Skip the entire restore block below
                        }
                        // Treat loadDataWithBaseURL pages as offline (they have https:// URLs but are local)
                        val isOnlinePage = url != null && (url.startsWith("http://") || url.startsWith("https://")) && !isDataUrlLoadedRef.value

                        // Add a small delay to ensure state has loaded (especially important on initial load from vault)
                        // Then read the scroll position using the callback to get the latest value
                        val stateLoadDelay = 200L
                        // Skip restore for non-content URLs. about:blank fires as the WebView's
                        // initial state before loadUrl takes effect (and also as the loadDataWithBaseURL
                        // intermediate); data:/null URLs are never the bookmark content. Letting these
                        // through schedules a spurious restore against the (eventual) real document.
                        if (url == null || url == "about:blank" || url.startsWith("data:")) {
                            onPageFinished?.invoke(view, url)
                            return
                        }
                        val restorePayload = if (isLandingPage) null else getSavedScrollRestore()
                        if (!isLandingPage) {
                        targetView?.postDelayed({
                            onLoadingChanged(false)
                            if (isImageOrNonDocumentUrl(url)) {
                                onScrollRestoreCompleted()
                                return@postDelayed
                            }
                            if (restorePayload == null) {
                                onScrollRestoreCompleted()
                                return@postDelayed
                            }
                            // Keep overlay visible as "Restoring…" for the whole restore flow (including async checks)
                            onScrollRestoreStarted()
                            targetView.evaluateJavascript("""
                                (function() {
                                    if (window.__gtScrollRestoreDone) return 'already_restored';
                                    return 'ok';
                                })();
                            """.trimIndent()) { skipResult ->
                                val result = skipResult?.trim()?.removeSurrounding("\"")
                                // Already restored via DOMContentLoaded — skip onPageFinished path
                                if (result == "already_restored") {
                                    onScrollRestoreCompleted()
                                    return@evaluateJavascript
                                }
                                val anchor = restorePayload.normalizedAnchor?.replace("\\", "\\\\")?.replace("\"", "\\\"")?.replace("\r", "\\r")?.replace("\n", "\\n") ?: ""
                                val occIdx = restorePayload.occurrenceIndex
                                val ratio = restorePayload.scrollRatio
                                fun doRatioFallback() {
                                    targetView.evaluateJavascript("""
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
                                                // MHT/HTML guides render progressively: text first, images later, fonts last.
                                                // body.scrollHeight grows from a few kpx to the final size over ~1.5s. A single
                                                // scroll + 500ms re-check lands at a stale intermediate height and leaves the
                                                // WebView scrolled to the wrong y (or 0 if the intermediate target exceeded
                                                // max-scroll at the time). Poll for layout shifts and re-apply on each change
                                                // until the height has been stable for ~500ms, ceiling 3s.
                                                var lastH = -1;
                                                var stableTicks = 0;
                                                var startTime = Date.now();
                                                var completed = false;
                                                function complete() {
                                                    if (completed) return;
                                                    completed = true;
                                                    if (window.Android && window.Android.onScrollRestoreComplete) window.Android.onScrollRestoreComplete();
                                                }
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
                                                    if (stableTicks >= 5 || elapsed >= 3000) {
                                                        complete();
                                                        return;
                                                    }
                                                    setTimeout(tick, 100);
                                                }
                                                tick();
                                            }
                                            if (document.readyState === 'loading') {
                                                window.__gtScrollRestoreFunc = doRestore;
                                            } else {
                                                doRestore();
                                            }
                                        })();
                                    """.trimIndent(), null)
                                }
                                if (result == "ratio_only") {
                                    if (ratio in 0.001..0.999) doRatioFallback()
                                    else onScrollRestoreCompleted()
                                    return@evaluateJavascript
                                }
                                // For offline (saved) guides, always use ratio — page height is deterministic so ratio is reliable.
                                // Anchor-based restore is fragile (fixed elements, nested scroll containers) and only helps when page height changes (online pages).
                                if (anchor.isBlank() || occIdx < 1 || !isOnlinePage) {
                                    if (ratio in 0.001..0.999) {
                                        targetView.postDelayed({ doRatioFallback() }, if (isOnlinePage) 400L else 250L)
                                    } else {
                                        onScrollRestoreCompleted()
                                    }
                                    return@evaluateJavascript
                                }
                                val initialDelay = if (isOnlinePage) 800L else 200L
                                targetView.postDelayed({
                                targetView.evaluateJavascript("""
                                    (function() {
                                        if (window.__gtScrollRestoreDone) return JSON.stringify({status:'already_restored'});
                                        var anchor = "$anchor";
                                        var idx = $occIdx;
                                        var ratio = $ratio;
                                        function norm(s) { return (s || "").replace(/[^a-zA-Z0-9]/g, "").toLowerCase(); }
                                        function normToRawOffset(raw, normIndex) {
                                            var n = 0;
                                            for (var r = 0; r < raw.length; r++) {
                                                if (/[a-zA-Z0-9]/.test(raw[r])) {
                                                    if (n === normIndex) return r;
                                                    n++;
                                                }
                                            }
                                            return raw.length;
                                        }
                                        function doRestore() {
                                            if (window.__gtScrollRestoreDone) return JSON.stringify({status:'already_restored'});
                                            window.__gtScrollRestoreDone = true;
                                            try {
                                                var count = 0;
                                                // Skip text nodes inside non-visible elements (script, style, noscript, template, etc.)
                                                var SKIP_TAGS = {SCRIPT:1, STYLE:1, NOSCRIPT:1, TEMPLATE:1, SVG:1};
                                                var walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT, {
                                                    acceptNode: function(node) {
                                                        var p = node.parentElement;
                                                        while (p && p !== document.body) {
                                                            if (SKIP_TAGS[p.tagName]) return NodeFilter.FILTER_REJECT;
                                                            p = p.parentElement;
                                                        }
                                                        return NodeFilter.FILTER_ACCEPT;
                                                    }
                                                }, false);
                                                var textNode, foundRange = null;
                                                while (textNode = walker.nextNode()) {
                                                    var raw = textNode.textContent || "";
                                                    var n = norm(raw);
                                                    var start = 0, pos;
                                                    while ((pos = n.indexOf(anchor, start)) !== -1) {
                                                        count++;
                                                        if (count === idx) {
                                                            var rawStart = normToRawOffset(raw, pos);
                                                            var rawEnd = normToRawOffset(raw, pos + anchor.length);
                                                            var testRange = document.createRange();
                                                            testRange.setStart(textNode, rawStart);
                                                            testRange.setEnd(textNode, rawEnd);
                                                            // Validate the range has a visible bounding rect (not inside a hidden element)
                                                            var testRect = testRange.getBoundingClientRect();
                                                            if (testRect.width > 0 || testRect.height > 0) {
                                                                foundRange = testRange;
                                                            } else {
                                                                // Found in hidden element — skip this occurrence and keep searching
                                                                count--;
                                                            }
                                                            break;
                                                        }
                                                        start = pos + anchor.length;
                                                    }
                                                    if (foundRange) break;
                                                }
                                                if (!foundRange) return JSON.stringify({status:'not_found'});
                                                var rect = foundRange.getBoundingClientRect();
                                                var anchorY = Math.max(0, (window.pageYOffset || 0) + rect.top - 20);
                                                var h = Math.max(document.body.scrollHeight || 0, document.documentElement.scrollHeight || 0) || 1;
                                                var anchorRatio = anchorY / h;
                                                var ratioY = Math.round(h * ratio);
                                                // If anchor position disagrees with saved ratio by more than 5%, anchor is unreliable (e.g. fixed/overlay element) — use ratio
                                                var useRatio = (ratio > 0.001 && Math.abs(anchorRatio - ratio) > 0.05);
                                                var targetY = useRatio ? ratioY : anchorY;
                                                var Y1 = useRatio ? targetY : (rect.top + (window.pageYOffset || 0));
                                                var done = false;
                                                function doStabilityThenScroll() {
                                                    if (done) return;
                                                    if (useRatio) {
                                                        done = true;
                                                        window.scrollTo(0, targetY);
                                                        if (window.Android && window.Android.onScrollRestoreComplete) window.Android.onScrollRestoreComplete();
                                                        return;
                                                    }
                                                    var r2 = foundRange.getBoundingClientRect();
                                                    var Y2 = r2.top + (window.pageYOffset || 0);
                                                    if (Y1 === Y2) {
                                                        done = true;
                                                        window.scrollTo(0, targetY);
                                                        if (window.Android && window.Android.onScrollRestoreComplete) window.Android.onScrollRestoreComplete();
                                                        return;
                                                    }
                                                    setTimeout(doStabilityThenScroll, 100);
                                                }
                                                setTimeout(doStabilityThenScroll, 100);
                                                setTimeout(function() {
                                                    if (done) return;
                                                    done = true;
                                                    var h2 = Math.max(document.body.scrollHeight || 0, document.documentElement.scrollHeight || 0);
                                                    if (h2 > 0) {
                                                        window.scrollTo(0, Math.round(h2 * ratio));
                                                    }
                                                    if (window.Android && window.Android.onScrollRestoreComplete) window.Android.onScrollRestoreComplete();
                                                }, 5000);
                                                return JSON.stringify({status:'waiting'});
                                            } catch (e) {
                                                return JSON.stringify({status:'not_found'});
                                            }
                                        }
                                        if (document.readyState === 'loading') {
                                            // DOM not ready yet — register for DOMContentLoaded
                                            window.__gtScrollRestoreFunc = doRestore;
                                            return JSON.stringify({status:'waiting'});
                                        } else {
                                            // DOM already ready — execute immediately
                                            return doRestore();
                                        }
                                    })();
                                """.trimIndent()) { result ->
                                    val json = result?.removeSurrounding("\"")?.replace("\\\"", "\"") ?: "{}"
                                    val status = when {
                                        json.contains("\"status\":\"not_found\"") -> "not_found"
                                        json.contains("\"status\":\"not_ready\"") -> "not_ready"
                                        json.contains("\"status\":\"already_restored\"") -> "already_restored"
                                        else -> "waiting"
                                    }
                                    if (status == "already_restored") {
                                        // Restored via DOMContentLoaded — skip onPageFinished path
                                        onScrollRestoreCompleted()
                                    } else if (status == "not_found" || status == "not_ready") {
                                        doRatioFallback()
                                    }
                                }
                            }, initialDelay)
                            }
                        }, stateLoadDelay)
                        } // end if (!isLandingPage)
                        // Cosmetic / element-hide safety net — re-apply in case onPageStarted
                        // ran before document.documentElement existed. Idempotent.
                        if (privacyPreferences.enabled && url != "about:blank") {
                            view?.evaluateJavascript(CosmeticFilters.injectionJs(), null)
                        }
                        onPageFinished?.invoke(view, url)
                    }

                    override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                        super.onPageStarted(view, url, favicon)
                        // Real navigation (not fragment change) — allow full onPageFinished processing
                        lastFinishedBaseUrl = null
                        // Skip loading indicator for about:blank (Guide mode placeholder)
                        if (url != "about:blank") onLoadingChanged(true)
                        // Inject early scroll restore hook — fires on DOMContentLoaded (before images load)
                        view?.evaluateJavascript("""
                            (function() {
                                window.__gtScrollRestoreDone = false;
                                document.addEventListener('DOMContentLoaded', function() {
                                    if (window.__gtScrollRestoreFunc && !window.__gtScrollRestoreDone) {
                                        window.__gtScrollRestoreDone = true;
                                        window.__gtScrollRestoreFunc();
                                    }
                                });
                            })();
                        """.trimIndent(), null)
                        // Cosmetic / element-hide layer for the privacy feature. CSS rules
                        // collapse empty space left by request-layer ad blocking + hide the
                        // GameFAQs "Recommend this Guide?" overlay. Idempotent guard inside.
                        if (privacyPreferences.enabled && url != "about:blank") {
                            view?.evaluateJavascript(CosmeticFilters.injectionJs(), null)
                        }
                    }

                    override fun shouldInterceptRequest(
                        view: WebView?,
                        request: android.webkit.WebResourceRequest?
                    ): android.webkit.WebResourceResponse? {
                        val reqUrl = request?.url?.toString() ?: return super.shouldInterceptRequest(view, request)
                        val manifest = assetManifestRef.value
                        val baseDir = assetBaseDirRef.value

                        // Bundled guide fonts. The palette CSS asks for them by
                        // `file:///android_asset/fonts/…`, but guide documents load
                        // from a file:// cache path and every file: URL is an opaque
                        // origin now that setAllowFileAccessFromFileURLs is off — a
                        // CORS-anonymous @font-face fetch across two opaque origins
                        // has no way to succeed. Answer it here with an allow-all
                        // header instead of relying on the engine's file handling.
                        bundledFontResponse(context, reqUrl)?.let { return it }

                        // Flash-kill for MHT bookmarks: intercept the main-frame load of
                        // bookmarkDir/index.html, splice the palette-override `<style>`
                        // block into `<head>`, and serve the modified HTML inline. The
                        // browser parses our theme rules before first paint, so a fresh
                        // MHT open / bookmark switch no longer flashes the original
                        // site's colours before the onPageFinished JS injection
                        // recolours it. Asset URLs and anchor-scroll plumbing are
                        // untouched because the request URL itself is unchanged.
                        if (baseDir != null && request.isForMainFrame &&
                            reqUrl == "file://$baseDir/index.html") {
                            val indexFile = java.io.File(baseDir, "index.html")
                            if (indexFile.exists()) {
                                try {
                                    val html = indexFile.readText(Charsets.UTF_8)
                                    val cssRules = buildPaletteCss(
                                        currentPaletteBgHex, currentPaletteFgHex, currentAppFont
                                    )
                                    val styleTag = "<style id=\"palette-override\">$cssRules</style>"
                                    val headMatch = Regex("<head\\b[^>]*>", RegexOption.IGNORE_CASE).find(html)
                                    val themedHtml = if (headMatch != null) {
                                        val insertAt = headMatch.range.last + 1
                                        html.substring(0, insertAt) + styleTag + html.substring(insertAt)
                                    } else {
                                        styleTag + html
                                    }
                                    return android.webkit.WebResourceResponse(
                                        "text/html",
                                        "UTF-8",
                                        themedHtml.toByteArray(Charsets.UTF_8).inputStream()
                                    )
                                } catch (e: Exception) {
                                    android.util.Log.w(
                                        "GuidePaletteInject",
                                        "Failed to splice palette CSS into $reqUrl",
                                        e
                                    )
                                    // Fall through to default handling — WebView reads the
                                    // raw file and the onPageFinished JS path still applies
                                    // the palette (with the original flicker).
                                }
                            }
                        }

                        // Serve local assets for URLs in our manifest (offline guide mode)
                        if (manifest.isNotEmpty() && baseDir != null) {
                            var relativePath = manifest[reqUrl]
                            // Root-relative paths in file:// mode (e.g. file:///a/css/…):
                            // reconstruct original HTTP URL using the bookmark origin.
                            if (relativePath == null && reqUrl.startsWith("file:///")
                                && !reqUrl.contains("/storage/") && !reqUrl.contains("/data/")) {
                                val rootPath = reqUrl.removePrefix("file://").substringBefore("?")
                                val origin = currentBookmarkUrlRef.value?.let { u ->
                                    try { val p = java.net.URL(u); "${p.protocol}://${p.host}" }
                                    catch (_: Exception) { null }
                                }
                                if (origin != null) relativePath = manifest["$origin$rootPath"]
                            }
                            if (relativePath != null) {
                                val localFile = java.io.File(baseDir, relativePath)
                                if (localFile.exists()) {
                                    android.util.Log.d("AssetIntercept", "HIT ${reqUrl.takeLast(60)} -> ${localFile.absolutePath}")
                                    val extension = relativePath.substringAfterLast('.', "").lowercase()
                                    val mimeType = android.webkit.MimeTypeMap.getSingleton()
                                        .getMimeTypeFromExtension(extension)
                                        ?: "application/octet-stream"
                                    return android.webkit.WebResourceResponse(
                                        mimeType,
                                        "UTF-8",
                                        localFile.inputStream()
                                    )
                                } else {
                                    android.util.Log.w("AssetIntercept", "FILE_MISSING baseDir=$baseDir relPath=$relativePath")
                                }
                            } else if (reqUrl.startsWith("http")) {
                                android.util.Log.w("AssetIntercept", "MISS reqUrl=${reqUrl.takeLast(80)}")
                            }
                        }
                        return super.shouldInterceptRequest(view, request)
                    }

                    override fun onReceivedError(
                        view: WebView?,
                        request: android.webkit.WebResourceRequest?,
                        error: android.webkit.WebResourceError?
                    ) {
                        super.onReceivedError(view, request, error)
                        val errorCode = error?.errorCode
                        val errorDescription = error?.description
                        android.util.Log.e("GuideBrowser", "WebView error: code=$errorCode, description=$errorDescription, URL: ${request?.url?.host}")
                        onLoadingChanged(false)
                        val urlScheme = request?.url?.scheme
                        val isMainFrame = request?.isForMainFrame == true
                        // On main frame http(s) failure, fall back to local file when available
                        if (isMainFrame && (urlScheme == "http" || urlScheme == "https") && favoritePath != null && validateFileExists(favoritePath)) {
                            android.util.Log.w("GuideBrowser", "Network error, falling back to local file: $favoritePath")
                            view?.loadUrl(favoritePath)
                            onUrlChanged(favoritePath)
                        }
                    }
                }

                // Download listener: handle downloadable files (e.g. PDF links)
                if (onDownloadRequested != null) {
                    setDownloadListener { url, userAgent, contentDisposition, mimeType, contentLength ->
                        onDownloadRequested.invoke(url, userAgent, contentDisposition, mimeType, contentLength)
                    }
                }

                settings.textZoom = (zoomLevelRef.value * 100).toInt()

                // CRITICAL: When returning from map mode, the WebView is recreated (composable was removed)
                // We cannot prevent this with the current navigation structure
                // However, we can minimize the reload impact by:
                // 1. Always loading (WebView is new when returning from map)
                // 2. Restoring scroll position immediately after load in onPageFinished
                // The key insight: WebView is always new when returning from map, so we must load
                // But we can make it feel seamless by restoring scroll quickly
                
                // Always load - WebView is new when returning from map, so it needs to load
                // Scroll restoration in onPageFinished will handle restoring position
                
                // Browser mode owns its own URL state via BrowserTabsViewModel +
                // BrowserScreen's LaunchedEffect(state.webView.value, activeTab?.id).
                // Loading anything from the factory races with that load: the factory's
                // trailing onPageFinished for landing.html overwrites the persisted head
                // tab URL back to landing. So when called from Browser mode with
                // initialUrl == "" (the contract), we skip the factory load entirely.
                val skipInitialLoad = vaultSessionMode == VaultSessionMode.Browser && initialUrl.isBlank()

                // Ensure we have a valid URL to load
                val urlToLoad = if (initialUrl.isNotBlank()) {
                    initialUrl
                } else {
                    getLandingPageUrl(context, vaultId, artworkPath, landingEntriesRef.value, searchHistoryManager.getSearchHistory(vaultId).take(3), savedLinks = savedLinksRef?.value.orEmpty(), paletteBgHex = currentPaletteBgHex, paletteFgHex = currentPaletteFgHex, gameName = gameName) ?: homeUrlForEngine(searchEngine)
                }
                
                // Load URL - this will trigger onPageFinished which will restore scroll
                // Skip loading indicator for about:blank (Guide mode placeholder);
                // the real indicator will start when loadGuideBookmark fires.
                if (!skipInitialLoad && urlToLoad != "about:blank") onLoadingChanged(true)
                if (!skipInitialLoad) coroutineScope.launch {
                    try {
                        // For landing page, regenerate it to ensure fresh content
                        val finalUrl = if (urlToLoad.contains("landing.html")) {
                            val freshUrl = getLandingPageUrl(context, vaultId, artworkPath, landingEntriesRef.value, searchHistoryManager.getSearchHistory(vaultId).take(3), savedLinks = savedLinksRef?.value.orEmpty(), paletteBgHex = currentPaletteBgHex, paletteFgHex = currentPaletteFgHex, gameName = gameName)
                            if (freshUrl != null) {
                                onUrlChanged(freshUrl)
                                freshUrl
                            } else {
                                urlToLoad
                            }
                        } else {
                            urlToLoad
                        }
                        if (finalUrl.startsWith("file://")) {
                            // file:///txt-guide/{bookmarkId} is a virtual URL; load TXT from disk (offline only)
                            if (finalUrl.startsWith("file:///txt-guide/")) {
                                val bookmarkId = finalUrl.removePrefix("file:///txt-guide/")
                                coroutineScope.launch(Dispatchers.IO) {
                                    // Resolve via ResourceResolver: new-flow saves only.
                                    val tab = savedTabRepository.getTabById(bookmarkId)
                                    val resourceId = tab?.resourceId
                                    val text: String? = if (resourceId != null) {
                                        val resUri = resourceResolver.resolve(resourceId)
                                        resUri?.let { u ->
                                            try {
                                                when (u.scheme) {
                                                    "file" -> u.path?.let { java.io.File(it).readText() }
                                                    "content" -> context.contentResolver.openInputStream(u)?.use { it.bufferedReader().readText() }
                                                    else -> null
                                                }
                                            } catch (_: Exception) { null }
                                        }
                                    } else null
                                    if (text != null) {
                                        val html = convertTxtToGameFaqHtml(text)
                                        // Pre-render palette injection: prepend the theme `<style>`
                                        // block so the browser parses our rules before first
                                        // paint. Without this every TXT bookmark switch flashed
                                        // the original site colours for ~50-150ms before the
                                        // onPageFinished evaluateJavascript path recoloured it.
                                        // (The onPageFinished path is still needed for live
                                        // palette / font swaps while a page is already loaded.)
                                        val cssRules = buildPaletteCss(
                                            currentPaletteBgHex, currentPaletteFgHex, currentAppFont
                                        )
                                        val styleTag = "<style id=\"palette-override\">$cssRules</style>"
                                        val headMatch = Regex("<head\\b[^>]*>", RegexOption.IGNORE_CASE).find(html)
                                        val themedHtml = if (headMatch != null) {
                                            val insertAt = headMatch.range.last + 1
                                            html.substring(0, insertAt) + styleTag + html.substring(insertAt)
                                        } else {
                                            styleTag + html
                                        }
                                        withContext(Dispatchers.Main) {
                                            this@apply.loadDataWithBaseURL("file:///", themedHtml, "text/html", "UTF-8", null)
                                            onUrlChanged(finalUrl)
                                            onLoadingChanged(false)
                                        }
                                    } else {
                                        withContext(Dispatchers.Main) {
                                            val landingPageUrl = getLandingPageUrl(context, vaultId, artworkPath, landingEntriesRef.value, searchHistoryManager.getSearchHistory(vaultId).take(3), savedLinks = savedLinksRef?.value.orEmpty(), paletteBgHex = currentPaletteBgHex, paletteFgHex = currentPaletteFgHex, gameName = gameName)
                                            val fallbackUrl = landingPageUrl ?: homeUrlForEngine(searchEngine)
                                            this@apply.loadUrl(fallbackUrl)
                                            onLoadingChanged(false)
                                        }
                                    }
                                }
                                return@launch
                            }
                            val filePath = finalUrl.removePrefix("file://").substringBefore("?")
                            val file = File(filePath)
                            // Legacy bookmark_cache re-extraction removed; new flow resolves via ResourceResolver upstream.
                            if (file.exists() && file.isFile) {
                                withContext(Dispatchers.Main) {
                                    this@apply.loadUrl(finalUrl)
                                }
                            } else {
                                android.util.Log.w("GuideBrowser", "File does not exist: $filePath")
                                withContext(Dispatchers.Main) {
                                    val landingPageUrl = getLandingPageUrl(context, vaultId, artworkPath, landingEntriesRef.value, searchHistoryManager.getSearchHistory(vaultId).take(3), savedLinks = savedLinksRef?.value.orEmpty(), paletteBgHex = currentPaletteBgHex, paletteFgHex = currentPaletteFgHex, gameName = gameName)
                                    val fallbackUrl = landingPageUrl ?: homeUrlForEngine(searchEngine)
                                    this@apply.loadUrl(fallbackUrl)
                                }
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                this@apply.loadUrl(finalUrl)
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("GuideBrowser", "Error loading URL: $urlToLoad", e)
                        withContext(Dispatchers.Main) {
                            // Fallback to landing page or configured search engine home
                            val landingPageUrl = getLandingPageUrl(context, vaultId, artworkPath, landingEntriesRef.value, searchHistoryManager.getSearchHistory(vaultId).take(3), savedLinks = savedLinksRef?.value.orEmpty(), paletteBgHex = currentPaletteBgHex, paletteFgHex = currentPaletteFgHex, gameName = gameName)
                            val fallbackUrl = landingPageUrl ?: homeUrlForEngine(searchEngine)
                            loadUrl(fallbackUrl)
                        }
                    }
                }
                android.util.Log.d("PERF_DEBUG", "WebView factory END (${vaultSessionMode}) ${System.currentTimeMillis() - wvT0}ms")
                webView(this)
            }
        },
        update = { view ->
            webViewInstance = view
            // Guide mode: prevent WebView from receiving D-pad focus — scrolling is handled by left stick.
            // Without this, touching the screen gives focus to the WebView and D-pad becomes
            // scroll navigation instead of Compose focus traversal.
            // Browser mode: allow focus so users can tap into web page input fields (e.g. Google search bar)
            // and have the soft keyboard appear.
            if (vaultSessionMode == VaultSessionMode.Guide) {
                view.isFocusable = false
                view.isFocusableInTouchMode = false
            } else {
                view.isFocusable = true
                view.isFocusableInTouchMode = true
            }
            // Guide tab: custom selection menu (Copy, Save Marker, Find, YouTube, Search online, Select All). Browser tab: null = default Android bar.
            (view as? GuideWebView)?.setCustomSelectionMenuActions(customSelectionActions)
            // Algorithmic darkening is disabled globally — CSS injection (LaunchedEffect) handles theming
        },
        onRelease = { view ->
            (view as? android.webkit.WebView)?.destroy()
        },
        modifier = Modifier.fillMaxSize()
    )
    
    // Apply zoom changes immediately when zoomLevel changes, preserving scroll position.
    // textZoom alters document height which shifts scroll; we capture the scroll ratio
    // beforehand, apply the new textZoom, and restore after layout settles via rAF.
    // The slider's minimum is computed once per page in LaunchedEffect(isLoading) via
    // probeMinUsefulZoom — the knee is page-dependent, not zoom-dependent.
    LaunchedEffect(zoomLevel, webViewInstance) {
        webViewInstance?.let { view ->
            // Capture current scroll ratio in JS, then apply textZoom on the UI thread
            view.evaluateJavascript("""
                (function() {
                    var scrollTop = window.pageYOffset || document.documentElement.scrollTop || 0;
                    var docHeight = Math.max(document.body.scrollHeight || 0, document.documentElement.scrollHeight || 0);
                    var viewportHeight = window.innerHeight || 0;
                    var maxScroll = docHeight - viewportHeight;
                    window._zoomScrollRatio = maxScroll > 0 ? (scrollTop / maxScroll) : -1;
                })();
            """.trimIndent(), null)
            view.settings.textZoom = (zoomLevel * 100).toInt()
            // Restore scroll position after layout updates
            view.evaluateJavascript("""
                (function() {
                    var ratio = window._zoomScrollRatio;
                    if (ratio >= 0) {
                        requestAnimationFrame(function() {
                            var docHeight = Math.max(document.body.scrollHeight || 0, document.documentElement.scrollHeight || 0);
                            var viewportHeight = window.innerHeight || 0;
                            var maxScroll = docHeight - viewportHeight;
                            if (maxScroll > 0) window.scrollTo(0, Math.round(ratio * maxScroll));
                        });
                    }
                })();
            """.trimIndent(), null)
        }
    }

    // Track the last time isLoading changed from true to false (page finished loading)
    // This helps us detect when a page has just finished loading vs. ongoing scrolling
    var lastPageLoadTime by remember { mutableStateOf(0L) }
    
    // Update lastPageLoadTime when page finishes loading
    LaunchedEffect(isLoading) {
        if (!isLoading) {
            lastPageLoadTime = System.currentTimeMillis()
            if (onMinZoomComputed != null) {
                webViewInstance?.let { view ->
                    kotlinx.coroutines.delay(300)
                    val minZoom = probeMinUsefulZoom(view).coerceIn(0.5f, 1.0f)
                    onMinZoomComputed(minZoom)
                }
            }
        }
    }
    
    // Bottom-right controls: TOC + Zoom (auto-hide like top menu)
    AnimatedVisibility(
        visible = showTopBar,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.align(Alignment.BottomEnd)
    ) {
        val controlsBottomLift = (configuration.screenHeightDp * 0.2f).dp
        val liftedPad = 16.dp + controlsBottomLift
        val controlsBottomPad = if (liftedPad.value < 88f) 88.dp else liftedPad
        val tocZoomCircle = 40.dp
        val tocZoomSpacing = 12.dp
        Column(
            modifier = Modifier
                .then(if (zoomControlsFocusRequester != null) Modifier.focusRequester(zoomControlsFocusRequester) else Modifier)
                .focusProperties {
                    up = topBarFocusRequester ?: FocusRequester.Default
                }
                .padding(start = 16.dp, end = 16.dp, bottom = controlsBottomPad),
            verticalArrangement = Arrangement.spacedBy(tocZoomSpacing),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

        // Zoom controls moved to top bar
        }
    }
    }
}
