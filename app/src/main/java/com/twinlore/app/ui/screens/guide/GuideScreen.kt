package com.twinlore.app.ui.screens.guide

import androidx.compose.ui.graphics.toArgb
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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.zIndex
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.flow.first
import com.twinlore.app.ui.MainViewModel
import com.twinlore.app.util.pauseHtmlMedia
import com.twinlore.app.ui.navigation.NavigableIconButton
import com.twinlore.app.ui.navigation.NavigableButton
import com.twinlore.app.ui.navigation.NavigableMenuItem
import com.twinlore.app.ui.navigation.longPressHandler
import com.twinlore.app.data.FocusColor
import com.twinlore.app.data.HapticFeedbackIntensity
import com.twinlore.app.thor.led.ThorLedManager
import com.twinlore.app.ui.screens.pdf.PdfGuideViewer
import com.twinlore.app.ui.utils.performHapticFeedback
import com.twinlore.app.ui.utils.runWithHapticAndLed
import com.twinlore.app.ui.theme.getFocusContainer
import com.twinlore.app.data.defaultLandingEntries
import com.twinlore.app.ui.components.LandingEntryEditDialog
import com.twinlore.app.data.SearchEngine
import com.twinlore.app.data.SearchHistoryManager
import com.twinlore.app.data.SavedTabRepository
import com.twinlore.app.domain.SavedTab
import com.twinlore.app.di.SearchHistoryManagerEntryPoint
import com.twinlore.app.di.SavedTabRepositoryEntryPoint
import com.twinlore.app.di.OfflineGuideLoaderEntryPoint
import com.twinlore.app.di.ResourceResolverEntryPoint
import com.twinlore.app.data.OfflineGuideLoader
import dagger.hilt.EntryPoints
import com.twinlore.app.domain.Vault
import com.twinlore.app.domain.ScrollRestoreData
import com.twinlore.app.domain.ScrollMarker
import com.twinlore.app.domain.VaultSessionMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.SideEffect
import java.io.File
import java.net.URL
import java.io.FileOutputStream
import java.io.InputStream
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import android.util.Log
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import com.twinlore.app.data.MhtCacheManager
import com.twinlore.app.ui.screens.GuideBrowserTopBar
import com.twinlore.app.ui.screens.vaultSessionTopBarChromeVisible
import com.twinlore.app.ui.screens.vaultSessionTopBarRenders
import com.twinlore.app.ui.screens.GuideViewerScreenViewModel
import com.twinlore.app.ui.screens.MapViewerScreenViewModel
import com.twinlore.app.ui.screens.SettingsViewModel
import com.twinlore.app.ui.screens.TocEntry
import com.twinlore.app.ui.screens.SaveProgress
import com.twinlore.app.ui.screens.colorToHex
import com.twinlore.app.ui.screens.colorToRgba
import com.twinlore.app.ui.screens.colorToContrastHex
import com.twinlore.app.ui.screens.toFileUrl
import com.twinlore.app.ui.screens.validateFileExists
import com.twinlore.app.ui.screens.extractTocEntries
import com.twinlore.app.ui.screens.loadScrollMarkersForBookmark
import com.twinlore.app.ui.screens.highlightScrollMarkerInPage
import com.twinlore.app.ui.screens.getLandingPageUrl
import com.twinlore.app.ui.screens.convertTxtToGameFaqHtml
import com.twinlore.app.ui.screens.isTextFaqFile
import com.twinlore.app.ui.screens.extractTextFaqFromFile
import com.twinlore.app.ui.screens.WebViewContent
import com.twinlore.app.ui.screens.scrollToMarkerByText
import com.twinlore.app.ui.screens.clearScrollMarkerHighlight
import com.twinlore.app.ui.screens.buildSearchUrlForEngine
import com.twinlore.app.ui.screens.isValidUrl
import com.twinlore.app.ui.screens.isAllowedWebViewUrl
import com.twinlore.app.ui.screens.resolveBestImageUrlForDownload
import com.twinlore.app.ui.screens.GuidePickerContent
import com.twinlore.app.ui.screens.TocDrawerOverlay
import com.twinlore.app.ui.screens.BookmarkManagementDialog
import com.twinlore.app.ui.screens.SaveProgressOverlay
import com.twinlore.app.ui.screens.ToastOverlay
import com.twinlore.app.ui.screens.PdfDownloadOverlay
import com.twinlore.app.ui.screens.ScrollMarkerContextMenu
import com.twinlore.app.ui.screens.DeleteAllScrollMarkersConfirmDialog
import com.twinlore.app.ui.screens.DeleteTabDialog
import com.twinlore.app.ui.screens.FindOnPageUI
import com.twinlore.app.ui.screens.BrowserImageContextMenu
import com.twinlore.app.ui.screens.GuideWebView
import com.twinlore.app.data.LandingEntry
import com.twinlore.app.domain.FilePickerSortField
import com.twinlore.app.domain.SortDirection
/**
 * Guide Screen — offline guide viewer with deterministic state machine.
 *
 * Single restore path:
 *   compose → read persisted bookmarkId → found → load → Viewing
 *                                       → not found → Picker
 *
 * Supports: HTML, MHT (cached), TXT (WebView), PDF (own viewer).
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun GuideScreen(
    vaultId: String,
    mainViewModel: MainViewModel,
    guideViewModel: GuideViewerScreenViewModel = hiltViewModel(),
    sessionVault: Vault? = null,
    // True only when mounted inside VaultSessionScreen's Guide tab (GuideTabContent).
    // GuideViewerScreen (the standalone AppState.GuideViewer route, reachable from
    // MapViewer's back navigation) leaves this false: it has no VaultSessionTopBar
    // of its own, so its GuideBrowserTopBar is the only place a fold button can go.
    isEmbeddedInVaultSession: Boolean = false,
    onAddGuideViaSearch: (() -> Unit)? = null,
    onOpenLinkInBrowser: ((String) -> Unit)? = null,
    isTabVisible: Boolean = true,
    onBackFallback: (() -> Unit)? = null,
    contentFocusRequester: FocusRequester? = null,
    bottomBarFocusRequester: FocusRequester? = null,
    /**
     * Moves each time the app comes back from Always on Top. Handed to the
     * PDF viewer, which re-reads its page on each move; see
     * [com.twinlore.app.ui.screens.pdf.PdfGuideViewer].
     */
    positionSyncKey: Int = 0,
    modifier: Modifier = Modifier
) {
    val vm: GuideViewModel = hiltViewModel(key = "vault_session_guide")
    val vaultFlow = guideViewModel.getVault(vaultId)
    val vaultFromFlow by vaultFlow.collectAsState(initial = null)
    val effectiveVault = sessionVault ?: vaultFromFlow
    // Artwork lives in resource_files; resolve to a concrete URI so the
    // landing-page builder + page-loaded JS can keep using a string path.
    val effectiveVaultArtworkUri = com.twinlore.app.ui.utils.rememberResourceImage(effectiveVault?.artworkResourceId)
    val effectiveVaultArtworkPath = effectiveVaultArtworkUri?.toString()
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val screenPalette = com.twinlore.app.ui.LocalThemePalette.current
    val screenBgHex = remember(screenPalette) { String.format("#%06X", 0xFFFFFF and screenPalette.background.toArgb()) }
    val screenFgHex = remember(screenPalette) { String.format("#%06X", 0xFFFFFF and screenPalette.foreground.toArgb()) }

    // Legacy storage manager has been removed; new flow uses ResourceResolver below.

    // Offline guide loader (new-style HTML loading with manifest)
    val guideLoaderEntryPoint = remember {
        EntryPoints.get(context.applicationContext, OfflineGuideLoaderEntryPoint::class.java)
    }
    val offlineGuideLoader = remember { guideLoaderEntryPoint.offlineGuideLoader() }
    val mhtParser = remember { guideLoaderEntryPoint.mhtParser() }

    // MHT cache manager
    val mhtCacheManager = remember { guideLoaderEntryPoint.mhtCacheManager() }
    val txtConversionCache = remember { guideLoaderEntryPoint.txtConversionCache() }

    // EPUB cache manager
    val epubCacheManager = remember { guideLoaderEntryPoint.epubCacheManager() }

    // ResourceResolver: turns SavedTab.resourceId into a usable Uri (file:// or content://).
    // Used by the MHT/PDF loaders to honor new-flow saves from BrowserSaveCoordinator.
    val resourceResolver = remember {
        EntryPoints.get(
            context.applicationContext,
            ResourceResolverEntryPoint::class.java
        ).resourceResolver()
    }

    // BrowserSaveCoordinator: long-press box-art / map saves go through this in the new flow.
    val browserSaveCoordinator = remember {
        EntryPoints.get(
            context.applicationContext,
            com.twinlore.app.di.BrowserSaveCoordinatorEntryPoint::class.java
        ).browserSaveCoordinator()
    }

    val mapViewModel: MapViewerScreenViewModel = hiltViewModel()

    val currentUrl by vm.currentUrl.collectAsState()
    val zoomLevel by vm.zoomLevel.collectAsState()
    val minZoomLevel by vm.minZoomLevel.collectAsState()
    val scrollPosition by vm.scrollPosition.collectAsState()
    val maxScrollY by vm.maxScrollY.collectAsState()
    val isLoading by vm.isLoading.collectAsState()

    val coroutineScope = rememberCoroutineScope()

    // Initialize ViewModel for this vault (loads persisted bookmark ID)
    vm.initForVault(vaultId)

    val state = remember { GuideUiState() }

    // Sync active bookmark back to ViewModel when guide changes.
    //
    // Guard: when a page group is the active doc for this vault, GuideScreen
    // may briefly remount (e.g. between PageGroupViewer close and the next
    // dispatch) with `state.currentBookmark` still holding whatever single-
    // page guide was open before the group. Writing that stale id here would
    // clobber the group's persisted activeBookmarkId and break resume on the
    // next vault entry. Skip the write while a group occupies the slot.
    val openPageGroupForVault = mainViewModel.openPageGroupByVault
        .collectAsState().value[vaultId]
    LaunchedEffect(state.activePdfGuide.value, state.currentBookmark.value) {
        if (openPageGroupForVault != null) return@LaunchedEffect
        val id = state.activePdfGuide.value?.id ?: state.currentBookmark.value?.id
        if (id != null) {
            vm.setActiveBookmark(vaultId, id)
        }
    }

    // Keep isTabVisible always up-to-date inside long-running LaunchedEffect(Unit) coroutines.
    // Without this, the Boolean parameter gets captured by value on first composition and never updates.
    val currentIsTabVisible by rememberUpdatedState(isTabVisible)

    // Pause/resume WebView processing when tab visibility changes to reduce CPU/GPU usage.
    // Also refresh the scroll state so D-pad UP/DOWN boundary escape works after tab switch.
    LaunchedEffect(isTabVisible) {
        val wv = state.webView.value ?: return@LaunchedEffect
        if (isTabVisible) {
            wv.onResume()
            // Refresh scroll state from WebView so dispatchKeyEvent has accurate boundary info
            wv.evaluateJavascript("JSON.stringify({scrollY: window.scrollY || document.documentElement.scrollTop || 0, maxScrollY: Math.max(0, document.documentElement.scrollHeight - window.innerHeight)})") { result ->
                try {
                    val json = org.json.JSONObject(result.trim('"').replace("\\\"", "\""))
                    mainViewModel.updateWebViewScrollState(json.optInt("scrollY", 0), json.optInt("maxScrollY", 0))
                } catch (_: Throwable) {}
            }
        } else {
            // onPause() alone does not stop HTML5 media; pause it explicitly so a
            // background video stops decoding/compositing. See pauseHtmlMedia.
            wv.pauseHtmlMedia()
            wv.onPause()
        }
    }

    // When guide picker becomes visible, request focus on its first button so D-pad works immediately.
    // GuidePickerContent handles its own focus via LaunchedEffect(Unit) — no parent push needed.

    // SavedTabRepository for bookmark operations
    val savedTabRepository = remember {
        EntryPoints.get(
            context.applicationContext,
            SavedTabRepositoryEntryPoint::class.java
        ).savedTabRepository()
    }
    
    // Search history (used for Search Online / YouTube from Guide selection actions)
    val searchHistoryManager = remember {
        EntryPoints.get(context.applicationContext, SearchHistoryManagerEntryPoint::class.java).searchHistoryManager()
    }
    val recentSearchesForLanding = remember(vaultId) { searchHistoryManager.getSearchHistory(vaultId).take(10) }

    // Track bookmarks for current vault (null = not yet loaded; avoid showing "No guide" on first frame)
    val bookmarksFlow = remember(vaultId) { savedTabRepository.getTabsForVault(vaultId) }
    val bookmarksState by bookmarksFlow.collectAsState(initial = null as List<SavedTab>?)
    val bookmarks = bookmarksState ?: emptyList()
    // Distinguish "not loaded yet" from true empty list to avoid first-open blank/incorrect initial URL.
    val hasNoBookmarks = bookmarksState?.isEmpty() == true
    // navigableBookmarks is defined below, after settingsViewModel is in scope (sort state needed).

    // SINGLE RESTORE PATH is placed after loadGuideBookmark() definition below.

    // Cancel save operation when navigating away or vault changes
    androidx.compose.runtime.DisposableEffect(vaultId) {
        onDispose {
            state.saveBookmarkJob.value?.cancel()
            state.saveBookmarkJob.value = null
            state.saveProgress.value = null
            state.isSavingBookmark.value = false
        }
    }

    // Focus color for scroll-marker and find-on-page highlights (use cache when available to avoid pink flash)
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    // Drives showFoldButton below: this reproduces VaultSessionScreen's own
    // VaultSessionTopBar visibility rule (vaultSessionTopBarChromeVisible +
    // vaultSessionTopBarRenders, both in ui.screens.VaultSessionChrome.kt) from
    // Guide's own vantage point, so the embedded fold button steps in the moment
    // that bar is not actually on screen for ANY reason -- not just Hidden mode.
    val statusBarMode by settingsViewModel.statusBarMode.collectAsState(initial = com.twinlore.app.data.StatusBarMode.STANDARD)
    // Read the IME inset through derivedStateOf, not directly: its bottom is
    // snapshot state that ticks once per frame for the whole keyboard slide
    // animation, and a raw read here would invalidate this entire (very large)
    // composable body on every one of those frames. Only the open/closed flip
    // matters to the fold button, so derive the boolean and let Compose skip the
    // frames in between. Same value, far fewer recompositions.
    val isKeyboardVisibleForFoldButton by com.twinlore.app.ui.utils.rememberKeyboardVisible()
    val browserUrlFieldFocusedForFoldButton by mainViewModel.browserUrlFieldFocused.collectAsState()
    val guideChromeVisibleForFoldButton by mainViewModel.vaultSessionGuideChromeVisible.collectAsState()
    val keepTopBarVisibleForFoldButton by settingsViewModel.keepTopBarVisible.collectAsState(initial = false)
    val vaultSessionTopBarVisible = vaultSessionTopBarRenders(
        chromeVisible = vaultSessionTopBarChromeVisible(
            isKeyboardVisible = isKeyboardVisibleForFoldButton,
            browserUrlFieldFocused = browserUrlFieldFocusedForFoldButton,
            // Evaluated from Guide's own vantage point: this only needs to be
            // correct for the moment Guide actually is the active tab, which is
            // the only moment this bar's fold button visibility matters.
            activeTab = com.twinlore.app.domain.VaultTab.Guide,
            anyWallpaperView = false, // Achievements-only term; neutralized above
            activeTabChromeVisible = guideChromeVisibleForFoldButton,
            keepTopBarVisible = keepTopBarVisibleForFoldButton,
        ),
    )
    val guideSortField by settingsViewModel.guidePickerSortField.collectAsState(initial = FilePickerSortField.DATE_ADDED)
    val guideSortDirection by settingsViewModel.guidePickerSortDirection.collectAsState(initial = SortDirection.DESC)
    // Palette + font snapshot for pre-render CSS injection (TXT loads). The
    // post-`onPageFinished` `evaluateJavascript` path inside
    // [GuideBrowserWebViewContent] still runs for live palette / font swaps;
    // this snapshot is just so the *initial* render of a TXT guide already
    // carries the theme rules and doesn't flash the original colours.
    val guideAppFont by settingsViewModel.appFont.collectAsState(initial = com.twinlore.app.data.AppFont.DEFAULT)
    // LINK bookmarks point at external URLs and don't render as content; both
    // the L2/R2 controller handler and the on-screen overlay arrows skip them.
    // Sort matches the picker's order so next/prev navigation is consistent with the list displayed.
    // SIZE sort falls back to repo order (file sizes are not lifted to GuideScreen scope).
    val navigableBookmarks = remember(bookmarks, guideSortField, guideSortDirection) {
        bookmarks
            .filter { it.fileType != "link" }
            .sortedWith(
                when (guideSortField) {
                    FilePickerSortField.NAME -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.displayTitle }
                    FilePickerSortField.LAST_OPENED -> compareBy { it.lastAccessed }
                    FilePickerSortField.DATE_ADDED -> compareBy { it.createdAt }
                    FilePickerSortField.SIZE -> compareBy { 0L }
                    FilePickerSortField.TYPE -> compareBy<com.twinlore.app.domain.SavedTab> { it.fileType }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.displayTitle }
                }.let { if (guideSortDirection == SortDirection.DESC) it.reversed() else it }
            )
    }
    val focusColorStateBrowser by settingsViewModel.focusColor.collectAsState(initial = FocusColor.CRIMSON)
    val cachedFocusColorBrowser = com.twinlore.app.ui.LocalCachedFocusColor.current?.value
    val focusColorBrowser: Color = cachedFocusColorBrowser ?: focusColorStateBrowser.color
    val focusColorHexBrowser = remember(focusColorBrowser) { colorToHex(focusColorBrowser) }
    val focusColorRgbaBrowser = remember(focusColorBrowser) { colorToRgba(focusColorBrowser) }
    val focusContrastHexBrowser = remember(focusColorBrowser) { colorToContrastHex(focusColorBrowser) }
    // When focus color is white or black, use blue for highlight background so it stays visible on any page
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
    
    // Hoisted scroll state for TOC drawer so position persists between open/close
    val tocDrawerScrollState = rememberScrollState()

    // Track if scroll position is being restored - in state
    // Scroll restore payload - in state
    // state.scrollCaptureJob.value - in state
    
    // Legacy favorite guide discovery removed — Guide tab uses bookmark-based restore only.

    // UI state - all in state holder below
    
    // Show chrome on user action and reset the 5 s auto-hide timer.
    fun updateInteraction() {
        state.lastInteractionTime.value = System.currentTimeMillis()
        state.showTopBar.value = true
    }

    // Handle global controller actions — only the currently visible tab should react.
    // Subscribe inside a visibility-keyed LaunchedEffect rather than collectAsState so
    // hidden tabs don't recompose on every controllerAction emission.
    val urlFieldFocusRequester = remember { FocusRequester() }
    val zoomControlsFocusRequester = remember { FocusRequester() }
    val pdfActive = state.activePdfGuide.value != null
    LaunchedEffect(isTabVisible, pdfActive) {
        if (!isTabVisible || pdfActive) return@LaunchedEffect
        mainViewModel.controllerAction.collect { action ->
        when (action) {
            MainViewModel.ControllerAction.TOGGLE_MAP_BROWSER -> {
                updateInteraction()
            }
            MainViewModel.ControllerAction.OPEN_FAVORITE_MENU -> {
                // Y button: no action in guide mode (use Back to return to picker).
                return@collect
            }
            MainViewModel.ControllerAction.FOCUS_SEARCH_BAR -> {
                updateInteraction()
                coroutineScope.launch {
                    kotlinx.coroutines.delay(80)
                    try { urlFieldFocusRequester.requestFocus() } catch (_: Throwable) {}
                    keyboardController?.show()
                }
            }
            MainViewModel.ControllerAction.OPEN_FIND_ON_PAGE -> {
                // (Browser-only: skipped in GuideScreen)
                // Don't open find-on-page when the file picker is showing or when no guide is being viewed
                if (state.showGuidePicker.value) return@collect
                if (state.currentBookmark.value == null && state.activePdfGuide.value == null) return@collect
                state.showFindOnPage.value = true
                state.findQuery.value = ""
                state.findOpenedWithSelection.value = false
                state.findMatchesFound.value = null
                state.findMatchCount.value = 0
                state.findCurrentIndex.value = 0
                state.findSearchTrigger.value = 0
                updateInteraction()
            }
            MainViewModel.ControllerAction.OPEN_TOC -> {
                // (Browser-only: skipped in GuideScreen)
                if (state.showGuidePicker.value) return@collect
                if (state.tocEntries.value.isNotEmpty() || state.textFaqTocActive.value) {
                    state.showTocDrawer.value = !state.showTocDrawer.value
                    if (state.showTocDrawer.value) state.showTopBar.value = false
                } else {
                    // Try extracting TOC first
                    state.webView.value?.let { wv ->
                        extractTocEntries(wv) { entries ->
                            if (entries.isNotEmpty()) {
                                state.tocEntries.value = entries
                                state.showTocDrawer.value = true
                                state.showTopBar.value = false
                            }
                        }
                    }
                }
                updateInteraction()
            }
            MainViewModel.ControllerAction.FIND_NEXT_RESULT -> {
                // (Browser-only: skipped in GuideScreen)
                mainViewModel.triggerFindNext()
                updateInteraction()
            }
            MainViewModel.ControllerAction.FIND_PREVIOUS_RESULT -> {
                // (Browser-only: skipped in GuideScreen)
                mainViewModel.triggerFindPrevious()
                updateInteraction()
            }
            MainViewModel.ControllerAction.BROWSER_BACK,
            MainViewModel.ControllerAction.BROWSER_FORWARD,
            MainViewModel.ControllerAction.BROWSER_SCROLL_MARKER_PREV,
            MainViewModel.ControllerAction.BROWSER_SCROLL_MARKER_NEXT,
            MainViewModel.ControllerAction.BROWSER_PREV_BOOKMARK,
            MainViewModel.ControllerAction.BROWSER_NEXT_BOOKMARK,
            MainViewModel.ControllerAction.BROWSER_SCROLL_TO_TOP,
            MainViewModel.ControllerAction.BROWSER_SCROLL_TO_END,
            MainViewModel.ControllerAction.BROWSER_RESET_ZOOM -> {
                // Handled in LaunchedEffect below (after loadFileUrl/bookmarks are in scope)
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

    LaunchedEffect(state.showBookmarkManagementDialog.value) {
        mainViewModel.setBookmarkDialogVisible(state.showBookmarkManagementDialog.value)
        if (state.showBookmarkManagementDialog.value) {
            state.bookmarksForDialog.value = withContext(Dispatchers.IO) {
                savedTabRepository.getTabsForVaultSync(vaultId)
            }
        } else {
            state.bookmarksForDialog.value = null
        }
    }
    // When bookmarks become empty (all deleted), show picker
    LaunchedEffect(bookmarksState) {
        if (bookmarksState?.isEmpty() == true) {
            state.showGuidePicker.value = true
        }
    }
    // Guide tab re-tap toggles between guide view and picker.
    // guide/PDF → picker; picker (with saved guides) → last guide view.
    // (The page-group viewer handles its own re-tap inside PageGroupViewerScreen
    // — onBack closes openPageGroupByVault, which dismounts this screen.)
    LaunchedEffect(Unit) {
        mainViewModel.tabReselected.collect { tab ->
            if (tab == com.twinlore.app.domain.VaultTab.Guide) {
                if (state.showGuidePicker.value) {
                    // Skip LINK entries — they open in the Browser tab, not as a
                    // guide doc, so they must never be the re-tap restore target.
                    val guides = bookmarksState?.filter { it.fileType != "link" }
                    if (!guides.isNullOrEmpty()) {
                        val lastAccessed = guides.maxByOrNull { it.lastAccessed.time }
                        when (lastAccessed?.fileType) {
                            null -> {}
                            "group" -> {
                                // Route through MainViewModel so openPageGroupByVault
                                // gets set + GuideTabContent re-mounts the viewer.
                                // navigateToPageGroupViewer also persists the group
                                // as activeBookmarkId for cold-start resume.
                                mainViewModel.navigateToPageGroupViewer(vaultId, lastAccessed.id)
                            }
                            "pdf" -> {
                                state.activeWheelGuide.value = null
                                state.activePdfGuide.value = lastAccessed
                                state.currentBookmark.value = null
                                state.showGuidePicker.value = false
                            }
                            "wheel", "htmlzip" -> {
                                state.activePdfGuide.value = null
                                state.activeWheelGuide.value = lastAccessed
                                state.currentBookmark.value = null
                                state.showGuidePicker.value = false
                            }
                            else -> {
                                // mht/txt MUST route through MainViewModel so
                                // GuideTabContent mounts SingleGuideViewerScreen.
                                // Setting state.currentBookmark directly mounted
                                // the LEGACY in-GuideScreen viewer (old
                                // GuideBrowserTopBar) — the "old guide viewer
                                // pops up on repeated Guide-tab presses" bug.
                                mainViewModel.openSingleGuideForVault(vaultId, lastAccessed)
                            }
                        }
                    }
                } else {
                    state.activePdfGuide.value = null
                    state.activeWheelGuide.value = null
                    state.currentBookmark.value = null
                    state.showGuidePicker.value = true
                }
            }
        }
    }

    LaunchedEffect(state.showFindOnPage.value) {
        mainViewModel.setFindOnPageVisible(state.showFindOnPage.value)
        if (!state.showFindOnPage.value) mainViewModel.setFindOnPageActions(null, null, null)
    }

    // Restore focus to bottom bar when TOC drawer closes
    var previousTocVisible by remember { mutableStateOf(false) }
    var editingLandingSlot by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(state.showTocDrawer.value) {
        if (previousTocVisible && !state.showTocDrawer.value) {
            mainViewModel.requestBottomBarFocus()
        }
        previousTocVisible = state.showTocDrawer.value
    }

    // Restore focus to bottom bar when find-on-page closes
    var previousFindVisible by remember { mutableStateOf(false) }
    LaunchedEffect(state.showFindOnPage.value) {
        if (previousFindVisible && !state.showFindOnPage.value) {
            mainViewModel.requestBottomBarFocus()
        }
        previousFindVisible = state.showFindOnPage.value
    }

    // When bookmarks arrive from the database Flow after the page has already loaded,
    // retroactively match the current URL to set currentBookmark (fixes toggle/TOC disappearing).
    // Also triggers TOC extraction if entries are empty.
    LaunchedEffect(bookmarks) {

        if (state.currentBookmark.value != null) return@LaunchedEffect
        val currentUrl = vm.currentUrl.value ?: return@LaunchedEffect
        val matched = if (currentUrl.startsWith("file:///txt-guide/")) {
            val bookmarkId = currentUrl.removePrefix("file:///txt-guide/")
            bookmarks.find { it.id == bookmarkId }
        } else {
            bookmarks.find { b ->
                currentUrl == b.url
                    || (currentUrl.startsWith("file://") && b.id != null && currentUrl.contains(b.id))
                    || (currentUrl.startsWith("content://") && b.id != null && currentUrl.contains(b.id))
            }
        }
        if (matched != null) {
            state.currentBookmark.value = matched
            if (state.tocEntries.value.isEmpty() && !state.textFaqTocActive.value) {
                state.webView.value?.let { wv ->
                    wv.postDelayed({
                        if (!state.textFaqTocActive.value) {
                            extractTocEntries(wv) { state.tocEntries.value = it }
                        }
                    }, 500L)
                }
            }
        }
    }

    // Load scroll markers and restore last focused marker when viewing a bookmark.
    // Only load when we have a bookmark; do not clear markers when currentBookmark becomes null here
    // (clearing is done in onUrlChanged when navigating to a non-bookmark so we don't wipe during load).
    // Guide mode only: Browser tab never loads or displays scroll markers.
    LaunchedEffect(state.currentBookmark.value?.id) {

        val bookmark = state.currentBookmark.value ?: return@LaunchedEffect
        val (markers, focusedIndex) = loadScrollMarkersForBookmark(vaultId, bookmark.id, savedTabRepository)
        state.scrollMarkers.value = markers
        state.focusedScrollMarkerIndex.value = focusedIndex
    }

    // Publish the active document title to MainViewModel so VaultSessionTopBar can
    // show it in place of the game name. Clear on dispose so leaving the guide tab
    // (or closing the document) hides it again. The top bar now carries this info,
    // so we no longer surface a transient toast for the title.
    LaunchedEffect(state.currentBookmark.value?.id) {
        val title = state.currentBookmark.value?.displayTitle?.takeIf { it.isNotBlank() }
        mainViewModel.setActiveDocTitle(vaultId, com.twinlore.app.domain.VaultTab.Guide, title)
    }
    DisposableEffect(vaultId) {
        onDispose { mainViewModel.setActiveDocTitle(vaultId, com.twinlore.app.domain.VaultTab.Guide, null) }
    }

    // When page finishes loading in Guide mode, ensure scroll markers are populated if we're viewing a bookmark URL
    // (covers first display and cases where currentBookmark was set after markers were already cleared).
    LaunchedEffect(isLoading, currentUrl, bookmarks.size) {
        if (isLoading) return@LaunchedEffect
        val url = currentUrl ?: return@LaunchedEffect
        if (url.isBlank() || url.startsWith("http")) return@LaunchedEffect
        val resolved = bookmarks.find { b ->
            url == b.url || (url.startsWith("file://") && b.id != null && url.contains(b.id))
                || (url.startsWith("content://") && b.id != null && url.contains(b.id))
        } ?: return@LaunchedEffect
        if (state.scrollMarkers.value.isEmpty()) {
            // Race guard: if currentBookmark is already set to a DIFFERENT bookmark, a
            // load is in progress for that bookmark (e.g. a freshly-imported EPUB whose
            // 400 ms parse is still running). The WebView's URL is still the previous
            // guide's, so URL-matching here would clobber the in-flight load and cause
            // the new bookmark to never render. Only adopt `resolved` when it agrees
            // with the in-progress bookmark (or no bookmark is currently set).
            val current = state.currentBookmark.value
            if (current != null && current.id != resolved.id) return@LaunchedEffect
            state.currentBookmark.value = resolved
            val (markers, focusedIndex) = loadScrollMarkersForBookmark(vaultId, resolved.id, savedTabRepository)
            state.scrollMarkers.value = markers
            state.focusedScrollMarkerIndex.value = focusedIndex
        }
    }
    
    // Handle browser scroll (U/D), pan (L/R), and zoom (right stick) from D-pad/analog stick.
    // Only process when this tab is visible to prevent hidden-tab scroll corruption.
    // Read directly from StateFlow.value inside the loop to avoid Compose recomposition on every
    // controller motion event (collectAsState would recompose the entire composable at 60Hz).
    LaunchedEffect(isTabVisible) {
        if (!isTabVisible) return@LaunchedEffect
        var lastZoomTime = 0L
        val deadzone = 0.1f
        while (true) {
            if (state.webView.value == null || state.activePdfGuide.value != null || state.activeWheelGuide.value != null) { delay(200); continue }
            val scroll = mainViewModel.browserScroll.value
            val panX = mainViewModel.browserPanX.value
            val zoom = mainViewModel.browserZoom.value
            val hasScrollInput = kotlin.math.abs(scroll) > deadzone || kotlin.math.abs(panX) > deadzone
            val hasZoomInput = kotlin.math.abs(zoom) > deadzone
            if (hasScrollInput) {
                val scrollY = (scroll * 56).toInt()
                val scrollX = (panX * 56).toInt()
                if (scrollY != 0 || scrollX != 0) {
                    state.webView.value?.scrollBy(scrollX, scrollY)
                }
            }
            if (hasZoomInput) {
                val now = System.currentTimeMillis()
                if (now - lastZoomTime >= 100) {
                    lastZoomTime = now
                    if (zoom < 0f) vm.setZoom(vm.zoomLevel.value + 0.1f) else vm.setZoom(vm.zoomLevel.value - 0.1f)
                }
            }
            delay(if (hasScrollInput || hasZoomInput) 16L else 200L)
        }
    }
    
    // Save image launcher: user picks where to save, then we download and write
    val saveImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/*")
    ) { uri: Uri? ->
        val url = state.pendingSaveImageUrl.value
        state.pendingSaveImageUrl.value = null
        if (uri != null && url != null) {
            state.isAssigningImage.value = true
            state.assigningImageLabel.value = "Saving image…"
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
                    android.util.Log.e("GuideBrowser", "Error saving image", e)
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

    // Current search engine (used for Search Online / YouTube from Guide selection actions and top bar)
    val currentSearchEngine by settingsViewModel.searchEngine.collectAsState(initial = SearchEngine.GOOGLE)
    val guideThemeInject by settingsViewModel.guideThemeInject.collectAsState(initial = true)
    // Persisted scroll-markers bar visibility — sync the in-memory MutableState
    // (state.showScrollMarkersBar) with the DataStore-backed Flow so the bar's
    // expanded/collapsed state survives screen recomposition and process death.
    val persistedScrollMarkersBarVisible by settingsViewModel.guideScrollMarkersBarVisible
        .collectAsState(initial = state.showScrollMarkersBar.value)
    LaunchedEffect(persistedScrollMarkersBarVisible) {
        state.showScrollMarkersBar.value = persistedScrollMarkersBarVisible
    }
    val setScrollMarkersBarVisible: (Boolean) -> Unit = { visible ->
        state.showScrollMarkersBar.value = visible
        settingsViewModel.setGuideScrollMarkersBarVisible(visible)
    }
    val landingEntriesList by settingsViewModel.landingEntries.collectAsState(
        initial = defaultLandingEntries()
    )
    SideEffect { state.landingEntriesRef.value = landingEntriesList }

    // State loading is handled by vm.initForVault() above.
    // Load pending scroll restore from disk for the SINGLE RESTORE PATH.
    LaunchedEffect(vaultId) {
        state.pendingScrollRestore.value = withContext(Dispatchers.IO) {
            vm.stateManager.getGuideScrollRestore(vaultId)
        }
    }
    
    // Show chrome when the bottom bar (or any tracked chrome surface) receives focus.
    LaunchedEffect(isTabVisible) {
        if (!isTabVisible) return@LaunchedEffect
        mainViewModel.chromeInteractionTrigger.collect {
            updateInteraction()
        }
    }

    // The TOC drawer forces chrome hidden while it owns dpad focus — publish that so the
    // activity/session-level "chrome hidden → swallow keys to wake chrome" logic lets its
    // dpad presses through to the drawer's focusable entries.
    LaunchedEffect(state.showTocDrawer.value, isTabVisible) {
        mainViewModel.setVaultSessionGuideOverlayOpen(isTabVisible && state.showTocDrawer.value)
    }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { mainViewModel.setVaultSessionGuideOverlayOpen(false) }
    }

    // Sync bottom bar with top bar in vault session (same as Map tab)
    // Always show bottom bar when guide picker or TOC drawer is visible
    LaunchedEffect(state.showTopBar.value, isTabVisible, state.activePdfGuide.value, state.showGuidePicker.value, state.showTocDrawer.value) {
        if (isTabVisible && state.activePdfGuide.value == null) {
            val forceVisible = state.showGuidePicker.value
            val forceHidden = state.showTocDrawer.value
            mainViewModel.setVaultSessionGuideChromeVisible(
                when {
                    forceVisible -> true
                    forceHidden -> false
                    else -> state.showTopBar.value
                }
            )
        }
    }

    // Reader-mode chrome: tap (in WebView onTouch) toggles, button presses show,
    // and a 5-second inactivity timer auto-hides when chrome is visible.
    // Don't hide while the search field is focused or the guide picker is open.
    LaunchedEffect(state.showTopBar.value, state.lastInteractionTime.value, state.isUrlFieldFocused.value, state.showGuidePicker.value) {
        if (state.showTopBar.value && !state.isUrlFieldFocused.value && !state.showGuidePicker.value) {
            delay(5000)
            val currentTime = System.currentTimeMillis()
            if (!state.isUrlFieldFocused.value && !state.showGuidePicker.value && currentTime - state.lastInteractionTime.value >= 5000) {
                state.showTopBar.value = false
            }
        }
    }

    // One-way scroll-down → hide chrome. Reading downward is a strong "get out of my way"
    // signal; reading upward is not (the user just wants to revisit earlier content).
    // Skipped while a programmatic scroll restore is in flight (and for 1200 ms after
    // it completes) so the restore-induced scroll delta doesn't spuriously hide chrome.
    // Also skipped for 500 ms after any user interaction. Tapping an ASCII block triggers
    // a JS scrollBy() to adjust the viewport, which would otherwise hide chrome that
    // updateInteraction() just surfaced (same goes for marker-jump and other programmatic
    // scrollTo calls paired with updateInteraction).
    LaunchedEffect(Unit) {
        var prevScrollY = 0
        vm.scrollPosition.collect { scrollY ->
            val isRestoring = state.isRestoringScroll.value
            val msSinceRestore = System.currentTimeMillis() - state.scrollRestoreCompletedAt.value
            if (isRestoring || msSinceRestore < 1200L) {
                prevScrollY = scrollY
                return@collect
            }
            val msSinceInteraction = System.currentTimeMillis() - state.lastInteractionTime.value
            if (msSinceInteraction < 500L) {
                prevScrollY = scrollY
                return@collect
            }
            if (scrollY > prevScrollY && state.showTopBar.value &&
                !state.isUrlFieldFocused.value && !state.showGuidePicker.value
            ) {
                state.showTopBar.value = false
            }
            prevScrollY = scrollY
        }
    }
    
    // Determine initial URL - Guide always starts with about:blank
    // The bookmark restoration LaunchedEffect handles loading the correct offline content
    // via loadGuideBookmark(). Loading the saved HTTP URL here caused a redundant network
    // request followed by a second load, producing a visible white flash and loading-bar stutter.
    val initialUrl = remember(vaultId) {
        val savedUrl = vm.stateManager.getLastUrl(vaultId)
        val savedBookmarkId = vm.stateManager.getActiveBookmarkId(vaultId)
        // Show picker immediately when there's no bookmark to resume.
        // Without this, closing the new single-guide viewer (which clears
        // the active bookmark id) leaves an empty WebView + dense top bar
        // visible for one frame before the bookmark-restore LaunchedEffect
        // falls through to the picker. Covers both the "no bookmarks yet"
        // case (hasNoBookmarks) and the "just-closed-the-new-viewer" case
        // (active id was cleared but lastUrl still points at the prior MHT).
        if (savedBookmarkId == null) {
            state.showGuidePicker.value = true
        } else if (hasNoBookmarks && (savedUrl.isNullOrBlank() || savedUrl.contains("landing.html") || savedUrl == "about:blank")) {
            state.showGuidePicker.value = true
        }
        "about:blank"
    }
    
    // Helper function to load file:// URLs properly
    // CRITICAL: Use loadUrl with file:// directly, NOT loadDataWithBaseURL
    // This ensures WebView treats it as HTML, not plain text
    fun loadFileUrl(webView: WebView, url: String) {
        // Handle content:// URIs (SAF) - they don't need File validation
        if (url.startsWith("content://")) {
            webView.loadUrl(url)
            vm.setUrl(url)
            return
        }
        
        // Handle file:// URIs - validate file exists
        val fileUrl = toFileUrl(url)
        if (validateFileExists(fileUrl)) {
            webView.loadUrl(fileUrl)
            vm.setUrl(fileUrl)
        } else {
            android.util.Log.w("GuideBrowser", "File does not exist: $fileUrl")
            state.toastMessage.value = "Offline version not available"
            // Show guide picker instead of blank screen
            state.showGuidePicker.value = true
            webView.loadUrl("about:blank")
            vm.setUrl("about:blank")
        }
    }

    // Prepend palette-override `<style>` block to a converted-TXT HTML string
    // so the browser parses our theme rules before first paint. Mirrors the
    // page-group viewer's flash-kill (see PageGroupViewerScreen.kt around the
    // `loadDataWithBaseURL` call). The `onPageFinished` evaluateJavascript
    // path inside GuideBrowserWebViewContent still re-injects to support
    // live palette / font changes.
    fun applyPalettePrelude(html: String): String {
        val cssRules = com.twinlore.app.ui.screens.buildPaletteCss(
            screenBgHex, screenFgHex, guideAppFont
        )
        val styleTag = "<style id=\"palette-override\">$cssRules</style>"
        val headMatch = Regex("<head\\b[^>]*>", RegexOption.IGNORE_CASE).find(html)
        return if (headMatch != null) {
            val insertAt = headMatch.range.last + 1
            html.substring(0, insertAt) + styleTag + html.substring(insertAt)
        } else {
            styleTag + html
        }
    }

    // Load a TXT bookmark into the WebView. TXT guides are always offline (no online/offline toggle).
    // New flow only: bookmark.resourceId resolves via ResourceResolver.
    fun loadTxtBookmark(webView: WebView, bookmark: com.twinlore.app.domain.SavedTab) {
        coroutineScope.launch(Dispatchers.IO) {
            // Check cache first
            val cachedHtml = txtConversionCache.readCachedHtml(bookmark.id)
            if (cachedHtml != null) {
                txtConversionCache.touch(bookmark.id)
                withContext(Dispatchers.Main) {
                    if (state.currentBookmark.value?.id != bookmark.id) return@withContext
                    state.pendingScrollRestore.value = bookmark.scrollRestoreData
                    state.isRestoringScroll.value = false
                    vm.setZoom(bookmark.zoomLevel)
                    webView.loadDataWithBaseURL("file:///", applyPalettePrelude(cachedHtml), "text/html", "UTF-8", null)
                    vm.setUrl("file:///txt-guide/${bookmark.id}")
                    state.isDataUrlLoaded.value = true
                    val bookmarkIdForToc = bookmark.id
                    webView.postDelayed({
                        if (state.currentBookmark.value?.id == bookmarkIdForToc) {
                            extractTocEntries(webView) { state.tocEntries.value = it }
                        }
                    }, 500L)
                }
                savedTabRepository.updateLastAccessed(bookmark.id)
                return@launch
            }

            // Cache miss — convert and cache. New flow only.
            state.isConvertingTextFaq.value = true
            val resourceId = bookmark.resourceId
            val sourcePath: String? = if (resourceId != null) {
                val uri = resourceResolver.resolve(resourceId)
                when (uri?.scheme) {
                    "file" -> uri.path
                    "content" -> {
                        val tmp = java.io.File(context.cacheDir, "txt_source/${bookmark.id}.txt")
                        tmp.parentFile?.mkdirs()
                        val ok = context.contentResolver.openInputStream(uri)?.use { input ->
                            tmp.outputStream().use { input.copyTo(it) }
                            true
                        } ?: false
                        if (ok) tmp.absolutePath else null
                    }
                    else -> null
                }
            } else null
            if (sourcePath == null) {
                withContext(Dispatchers.Main) {
                    state.toastMessage.value = if (resourceId != null) {
                        "Waiting for file to sync. It will appear when your sync app delivers it."
                    } else {
                        "Guide file not found"
                    }
                    state.isConvertingTextFaq.value = false
                }
                return@launch
            }
            val content = java.io.File(sourcePath).readText()
            val html = convertTxtToGameFaqHtml(content)
            txtConversionCache.writeCachedHtml(bookmark.id, html)

            withContext(Dispatchers.Main) {
                if (state.currentBookmark.value?.id != bookmark.id) return@withContext
                state.pendingScrollRestore.value = bookmark.scrollRestoreData
                state.isRestoringScroll.value = false
                vm.setZoom(bookmark.zoomLevel)
                webView.loadDataWithBaseURL("file:///", applyPalettePrelude(html), "text/html", "UTF-8", null)
                vm.setUrl("file:///txt-guide/${bookmark.id}")
                state.isDataUrlLoaded.value = true
                state.isConvertingTextFaq.value = false
                val bookmarkIdForToc = bookmark.id
                webView.postDelayed({
                    if (state.currentBookmark.value?.id == bookmarkIdForToc) {
                        extractTocEntries(webView) { state.tocEntries.value = it }
                    }
                }, 1000L)
            }
            savedTabRepository.updateLastAccessed(bookmark.id)
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // UNIFIED bookmark loader: the SINGLE place that decides how to display a guide.
    // Rules:
    //   TXT  → always offline (loadTxtBookmark, loadDataWithBaseURL)
    //   PDF  → always offline (PdfGuideViewer, no WebView)
    //   HTML → always offline (load from saved file; use "Open Online" button to open in Browser tab)
    // All other call-sites must delegate here instead of duplicating the logic.
    // ──────────────────────────────────────────────────────────────────────────
    // Job tracking for loadGuideBookmark so we can cancel the previous load when switching guides.
    // This prevents race conditions where a slow TXT coroutine overwrites state after a fast HTML switch.
    var activeGuideLoadJob: kotlinx.coroutines.Job? = null

    fun loadGuideBookmark(
        wv: WebView?,
        bookmark: com.twinlore.app.domain.SavedTab,
        restoreScroll: Boolean = true
    ) {
        // Cancel any in-flight guide load (prevents race when switching quickly between guides)
        activeGuideLoadJob?.cancel()

        // Immediately reset stale state so UI reflects the new guide right away.
        // Dismiss any loading overlay from a previous guide (e.g., TXT overlay still showing
        // when switching to PDF/HTML/MHT). Only TXT branch re-enables it below.
        state.isConvertingTextFaq.value = false
        state.isRestoringScroll.value = false
        state.tocEntries.value = emptyList()
        state.textFaqTocActive.value = false
        state.showTocDrawer.value = false
        state.scrollMarkers.value = emptyList()
        state.focusedScrollMarkerIndex.value = null

        when (bookmark.fileType) {
            "link" -> {
                // URL shortcut (e.g. YouTube): route directly to the Browser tab with the URL.
                // Deliberately does NOT touch guide-tab state — so when the user returns to the
                // Guide tab they see whatever was there before (previous HTML/TXT/PDF guide,
                // or the picker). LINK guides are pure "open in browser" shortcuts.
                mainViewModel.setPendingUrlForBrowserTab(vaultId, bookmark.url)
                mainViewModel.setVaultSessionTab(vaultId, com.twinlore.app.domain.VaultTab.Browser)
                coroutineScope.launch(Dispatchers.IO) { savedTabRepository.updateLastAccessed(bookmark.id) }
            }
            "pdf" -> {
                // PDF uses its own viewer — just set state, no WebView load.
                // Pre-flight resolve so the user sees a toast (and the picker
                // stays put) rather than a blank/crashing viewer when the
                // underlying PDF file is missing in the home folder.
                val rid = bookmark.resourceId
                if (rid == null) {
                    state.toastMessage.value = "File not found. Restore it in the storage folder and restart."
                } else {
                    activeGuideLoadJob = coroutineScope.launch(Dispatchers.IO) {
                        val resolved = resourceResolver.resolve(rid)
                        withContext(Dispatchers.Main) {
                            if (resolved == null) {
                                state.toastMessage.value = "Waiting for file to sync. It will appear when your sync app delivers it."
                            } else {
                                state.activeWheelGuide.value = null
                                state.activePdfGuide.value = bookmark
                                state.currentBookmark.value = null
                                state.showGuidePicker.value = false
                                state.assetManifest.value = emptyMap()
                                state.assetBaseDir.value = null
                                state.isDataUrlLoaded.value = false
                                savedTabRepository.updateLastAccessed(bookmark.id)
                            }
                        }
                    }
                }
            }
            "wheel", "htmlzip" -> {
                // Code wheel: uses its own viewer. Pre-flight resolve to show a
                // toast (and keep the picker) if the underlying file is missing.
                val rid = bookmark.resourceId
                if (rid == null) {
                    state.toastMessage.value = "File not found. Restore it in the storage folder and restart."
                } else {
                    activeGuideLoadJob = coroutineScope.launch(Dispatchers.IO) {
                        val resolved = resourceResolver.resolve(rid)
                        withContext(Dispatchers.Main) {
                            if (resolved == null) {
                                state.toastMessage.value = "Waiting for file to sync. It will appear when your sync app delivers it."
                            } else {
                                state.activeWheelGuide.value = bookmark
                                state.currentBookmark.value = null
                                state.activePdfGuide.value = null
                                state.showGuidePicker.value = false
                                savedTabRepository.updateLastAccessed(bookmark.id)
                            }
                        }
                    }
                }
            }
            "txt" -> {
                // TXT guides are ALWAYS offline — delegate to loadTxtBookmark
                if (wv == null) return
                state.currentBookmark.value = bookmark
                state.activePdfGuide.value = null
                state.activeWheelGuide.value = null
                state.assetManifest.value = emptyMap()
                state.assetBaseDir.value = null
                state.isDataUrlLoaded.value = false
                loadTxtBookmark(wv, bookmark)
            }
            "mht" -> {
                // MHT: serve from cache if available, otherwise parse and cache.
                if (wv == null) return
                state.currentBookmark.value = bookmark
                state.activePdfGuide.value = null
                state.activeWheelGuide.value = null
                vm.setZoom(bookmark.zoomLevel)
                activeGuideLoadJob = coroutineScope.launch(Dispatchers.IO) {
                    val t0 = System.currentTimeMillis()
                    Log.d("PERF_DEBUG", "loadGuideBookmark MHT IO START (${bookmark.id})")
                    // New flow only: bookmark.resourceId resolves via ResourceResolver. The MHT
                    // lives in the home folder; cacheDir holds the parsed index.html + assets.
                    val resourceId = bookmark.resourceId
                    if (resourceId == null) {
                        withContext(Dispatchers.Main) {
                            state.toastMessage.value = "MHT file not available"
                            state.showGuidePicker.value = true
                        }
                        return@launch
                    }
                    val bookmarkDir = java.io.File(context.cacheDir, "guide_cache/${bookmark.id}").apply { mkdirs() }

                    if (!mhtCacheManager.hasCachedResult(bookmarkDir)) {
                        val uri = resourceResolver.resolve(resourceId)
                        // When the source lives behind a content:// URI we have to spill
                        // it to a real File first. That spill is only needed for parsing
                        // — keep a separate handle so we can delete it once MhtCacheManager
                        // has produced bookmarkDir/index.html.
                        var tmpToDelete: java.io.File? = null
                        val mhtFile: java.io.File? = when (uri?.scheme) {
                            "file" -> uri.path?.let { java.io.File(it) }
                            "content" -> {
                                val tmp = java.io.File(context.cacheDir, "mht_source/${bookmark.id}.mht")
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
                        if (!parsed) {
                            withContext(Dispatchers.Main) {
                                state.toastMessage.value = "MHT file not available"
                                state.showGuidePicker.value = true
                            }
                            return@launch
                        }
                    }
                    mhtCacheManager.touchCache(bookmarkDir)

                    // New-flow saves have no legacy scroll-restore metadata; restoreScroll is a no-op here.
                    @Suppress("UNUSED_PARAMETER") val unusedRestoreScroll = restoreScroll

                    val indexFile = java.io.File(bookmarkDir, "index.html")
                    if (!indexFile.exists()) {
                        withContext(Dispatchers.Main) {
                            state.toastMessage.value = "MHT file not available"
                            state.showGuidePicker.value = true
                        }
                        return@launch
                    }

                    // Text FAQ check: stream the file line by line, never load full HTML into memory.
                    val cachedConversion = txtConversionCache.readCachedHtml(bookmark.id)
                    val isTextFaqGuide: Boolean
                    val convertedHtml: String?
                    if (cachedConversion != null) {
                        txtConversionCache.touch(bookmark.id)
                        isTextFaqGuide = true
                        convertedHtml = cachedConversion
                    } else if (isTextFaqFile(indexFile)) {
                        isTextFaqGuide = true
                        val textContent = extractTextFaqFromFile(indexFile)
                        convertedHtml = if (textContent != null) {
                            val converted = convertTxtToGameFaqHtml(textContent)
                            txtConversionCache.writeCachedHtml(bookmark.id, converted)
                            converted
                        } else null
                    } else {
                        isTextFaqGuide = false
                        convertedHtml = null
                    }
                    Log.d("PERF_DEBUG", "loadGuideBookmark MHT IO END ${System.currentTimeMillis() - t0}ms, textFaq=$isTextFaqGuide, converted=${convertedHtml != null}")

                    withContext(Dispatchers.Main) {
                        if (state.currentBookmark.value?.id != bookmark.id) return@withContext
                        state.pendingScrollRestore.value = bookmark.scrollRestoreData
                        state.isRestoringScroll.value = false

                        if (convertedHtml != null) {
                            // Text FAQ: load converted HTML from cache file
                            state.assetManifest.value = emptyMap()
                            state.assetBaseDir.value = null
                            state.isDataUrlLoaded.value = false
                            val cacheFile = java.io.File(context.cacheDir, "guide_html_cache/${bookmark.id}/index.html")
                            val fileUri = "file://${cacheFile.absolutePath}"
                            vm.setUrl(fileUri)
                            wv.loadUrl(fileUri)
                        } else {
                            // Regular MHT content — load cached index.html via file:// URL.
                            // No OfflineGuideLoader needed: MhtCacheManager already produced clean HTML.
                            val manifest = offlineGuideLoader.loadManifestOnly(bookmarkDir)
                            state.assetManifest.value = manifest
                            state.assetBaseDir.value = if (manifest.isNotEmpty()) bookmarkDir.absolutePath else null
                            state.isDataUrlLoaded.value = false
                            val fileUri = "file://${indexFile.absolutePath}"
                            vm.setUrl(fileUri)
                            wv.loadUrl(fileUri)
                        }
                    }
                    savedTabRepository.updateLastAccessed(bookmark.id)
                }
            }
            "epub" -> {
                // EPUB: serve from cache if available, otherwise parse and cache.
                // The synthetic index.html already sets data-gamefaqs-converted="true" and
                // id="sec-N" sections, so the TOC extractor fires automatically — no extra
                // text-FAQ conversion logic is needed here.
                if (wv == null) return
                state.currentBookmark.value = bookmark
                state.activePdfGuide.value = null
                state.activeWheelGuide.value = null
                vm.setZoom(bookmark.zoomLevel)
                activeGuideLoadJob = coroutineScope.launch(Dispatchers.IO) {
                    val t0 = System.currentTimeMillis()
                    Log.d("PERF_DEBUG", "loadGuideBookmark EPUB IO START (${bookmark.id})")
                    val resourceId = bookmark.resourceId
                    if (resourceId == null) {
                        withContext(Dispatchers.Main) {
                            state.toastMessage.value = "EPUB file not available"
                            state.showGuidePicker.value = true
                        }
                        return@launch
                    }
                    val bookmarkDir = java.io.File(context.cacheDir, "guide_cache/${bookmark.id}").apply { mkdirs() }

                    if (!epubCacheManager.hasCachedResult(bookmarkDir)) {
                        val uri = resourceResolver.resolve(resourceId)
                        // Same pattern as the MHT branch above: content:// requires a
                        // temp spill that we must delete after parseAndCache so
                        // cacheDir/epub_source doesn't accumulate one .epub per opened guide.
                        var tmpToDelete: java.io.File? = null
                        val epubFile: java.io.File? = when (uri?.scheme) {
                            "file" -> uri.path?.let { java.io.File(it) }
                            "content" -> {
                                val tmp = java.io.File(context.cacheDir, "epub_source/${bookmark.id}.epub")
                                tmp.parentFile?.mkdirs()
                                val copied = context.contentResolver.openInputStream(uri)?.use { input ->
                                    tmp.outputStream().use { input.copyTo(it) }
                                    true
                                } ?: false
                                if (copied) { tmpToDelete = tmp; tmp } else null
                            }
                            else -> null
                        }
                        val parsed = epubFile != null && epubCacheManager.parseAndCache(epubFile, bookmarkDir)
                        tmpToDelete?.delete()
                        if (!parsed) {
                            withContext(Dispatchers.Main) {
                                state.toastMessage.value = "EPUB file not available"
                                state.showGuidePicker.value = true
                            }
                            return@launch
                        }
                    }
                    epubCacheManager.touchCache(bookmarkDir)

                    @Suppress("UNUSED_PARAMETER") val unusedRestoreScroll = restoreScroll

                    val indexFile = java.io.File(bookmarkDir, "index.html")
                    if (!indexFile.exists()) {
                        withContext(Dispatchers.Main) {
                            state.toastMessage.value = "EPUB file not available"
                            state.showGuidePicker.value = true
                        }
                        return@launch
                    }

                    Log.d("PERF_DEBUG", "loadGuideBookmark EPUB IO END ${System.currentTimeMillis() - t0}ms")

                    withContext(Dispatchers.Main) {
                        if (state.currentBookmark.value?.id != bookmark.id) return@withContext
                        state.pendingScrollRestore.value = bookmark.scrollRestoreData
                        state.isRestoringScroll.value = false

                        val manifest = offlineGuideLoader.loadManifestOnly(bookmarkDir)
                        state.assetManifest.value = manifest
                        state.assetBaseDir.value = if (manifest.isNotEmpty()) bookmarkDir.absolutePath else null
                        state.isDataUrlLoaded.value = false
                        val fileUri = "file://${indexFile.absolutePath}"
                        vm.setUrl(fileUri)
                        wv.loadUrl(fileUri)
                    }
                    savedTabRepository.updateLastAccessed(bookmark.id)
                }
            }
            else -> {
                // HTML: load offline with in-memory processing
                // NOTE: This is the legacy path for guides saved before the MHT migration.
                // New saves use MHT (see "mht" branch above). This path will eventually be
                // removed once all users have migrated or a migration tool is provided.
                if (wv == null) return
                state.currentBookmark.value = bookmark
                state.activePdfGuide.value = null
                state.activeWheelGuide.value = null
                vm.setZoom(bookmark.zoomLevel)
                activeGuideLoadJob = coroutineScope.launch(Dispatchers.IO) {
                    val t0 = System.currentTimeMillis()
                    Log.d("PERF_DEBUG", "loadGuideBookmark HTML IO START (${bookmark.id})")

                    if (bookmark.resourceId != null) {
                        // New-flow HTML save: a single .html file in the home folder. No
                        // manifest, no assets directory, no legacy bookmark_metadata.json.
                        val uri = resourceResolver.resolve(bookmark.resourceId)
                        if (uri == null) {
                            withContext(Dispatchers.Main) {
                                state.toastMessage.value = "Offline file not available"
                                state.isRestoringScroll.value = false
                                state.showGuidePicker.value = true
                            }
                            return@launch
                        }
                        val htmlFile = when (uri.scheme) {
                            "file" -> uri.path?.let { java.io.File(it) }
                            "content" -> {
                                val tmp = java.io.File(context.cacheDir, "html_source/${bookmark.id}.html")
                                tmp.parentFile?.mkdirs()
                                val ok = context.contentResolver.openInputStream(uri)?.use { input ->
                                    tmp.outputStream().use { input.copyTo(it) }
                                    true
                                } ?: false
                                if (ok) tmp else null
                            }
                            else -> null
                        }
                        if (htmlFile == null || !htmlFile.exists()) {
                            withContext(Dispatchers.Main) {
                                state.toastMessage.value = "Offline file not available"
                                state.isRestoringScroll.value = false
                                state.showGuidePicker.value = true
                            }
                            return@launch
                        }

                        // Text FAQ check on the single HTML file. The helpers stream the
                        // file so this is cheap even for large guides.
                        val cachedConversion = txtConversionCache.readCachedHtml(bookmark.id)
                        val isTextFaqGuide: Boolean
                        val convertedHtml: String?
                        if (cachedConversion != null) {
                            txtConversionCache.touch(bookmark.id)
                            isTextFaqGuide = true
                            convertedHtml = cachedConversion
                        } else if (isTextFaqFile(htmlFile)) {
                            isTextFaqGuide = true
                            val textContent = extractTextFaqFromFile(htmlFile)
                            convertedHtml = if (textContent != null) {
                                val converted = convertTxtToGameFaqHtml(textContent)
                                txtConversionCache.writeCachedHtml(bookmark.id, converted)
                                converted
                            } else null
                        } else {
                            isTextFaqGuide = false
                            convertedHtml = null
                        }
                        Log.d("PERF_DEBUG", "loadGuideBookmark HTML(new-flow) END ${System.currentTimeMillis() - t0}ms, textFaq=$isTextFaqGuide")

                        // A downloaded .html is third-party content loaded from file://
                        // with JavaScript on, so it is served from a stripped copy in
                        // the cache, never from the library file itself. Guarded on the
                        // same condition the load path below uses to pick the converted
                        // text-FAQ cache (isTextFaqGuide && convertedHtml != null), not
                        // just isTextFaqGuide: a text-FAQ sniff can match while extraction
                        // still yields null (blank extracted text, or a parse exception),
                        // in which case the load path falls through to the raw-file
                        // branch below, and that branch still needs a stripped copy.
                        val strippedHtmlFile: java.io.File? = if (!(isTextFaqGuide && convertedHtml != null)) {
                            val stripped = runCatching {
                                java.io.File(context.cacheDir, "guide_html_stripped/${bookmark.id}/index.html").also { out ->
                                    out.parentFile?.mkdirs()
                                    out.writeText(
                                        com.twinlore.app.data.HtmlScriptStripper.strip(htmlFile.readText(Charsets.UTF_8)),
                                        Charsets.UTF_8,
                                    )
                                }
                            }.getOrNull()
                            if (stripped == null) {
                                // Fail closed: an IO failure producing the stripped copy
                                // must never fall through to loading the raw third-party
                                // file with JavaScript enabled.
                                withContext(Dispatchers.Main) {
                                    state.toastMessage.value = "Offline file not available"
                                    state.isRestoringScroll.value = false
                                    state.showGuidePicker.value = true
                                }
                                return@launch
                            }
                            stripped
                        } else null

                        // Per-bookmark scroll restore: SavedTab.scrollRestoreData is hydrated
                        // from the row's scrollNormalizedAnchor / scrollOccurrenceIndex /
                        // scrollRatio columns by the entity mapper. Null means the bookmark
                        // has never been scrolled, so the WebView starts at the top.
                        withContext(Dispatchers.Main) {
                            if (state.currentBookmark.value?.id != bookmark.id) return@withContext
                            state.pendingScrollRestore.value = bookmark.scrollRestoreData
                            state.isRestoringScroll.value = false
                            state.assetManifest.value = emptyMap()
                            state.assetBaseDir.value = null
                            state.isDataUrlLoaded.value = false
                            if (isTextFaqGuide && convertedHtml != null) {
                                val cacheFile = java.io.File(context.cacheDir, "guide_html_cache/${bookmark.id}/index.html")
                                val fileUri = "file://${cacheFile.absolutePath}"
                                vm.setUrl(fileUri)
                                wv.loadUrl(fileUri)
                                state.isConvertingTextFaq.value = false
                            } else {
                                val fileUri = "file://${(strippedHtmlFile ?: htmlFile).absolutePath}"
                                vm.setUrl(fileUri)
                                loadFileUrl(wv, fileUri)
                                state.isConvertingTextFaq.value = false
                            }
                        }
                        savedTabRepository.updateLastAccessed(bookmark.id)
                        return@launch
                    }

                    // Legacy HTML path removed; new flow requires a resourceId. Force the picker.
                    Log.w("PERF_DEBUG", "loadGuideBookmark: bookmark ${bookmark.id} has no resourceId; legacy HTML path removed")
                    withContext(Dispatchers.Main) {
                        state.toastMessage.value = "Offline file not available"
                        state.isRestoringScroll.value = false
                        state.showGuidePicker.value = true
                    }
                }
            }
        }
    }

    // ── SINGLE RESTORE PATH ──
    // One LaunchedEffect, one decision: bookmark ID exists → load it, else → picker.
    // No URL-based restore. No auto-open-most-recent. No competing paths.
    // Placed here (after loadGuideBookmark definition) because Kotlin requires
    // local functions to be defined before they are referenced.
    LaunchedEffect(vaultId) {
        // Read from disk, not from `vm.activeBookmarkId` — that cache stays
        // stale across `MainViewModel.navigateToPageGroupViewer` calls (which
        // only update the SharedPrefs side), so after a group is opened
        // then closed the cached value is still the previously-active single-
        // page bookmark id, which would then be (incorrectly) reopened here.
        val bookmarkId = withContext(Dispatchers.IO) {
            vm.stateManager.getActiveBookmarkId(vaultId)
        }
        if (bookmarkId != null && state.activePdfGuide.value == null && state.currentBookmark.value == null) {
            val bookmark = withContext(Dispatchers.IO) { savedTabRepository.getTabById(bookmarkId) }
            if (bookmark != null) {
                if (bookmark.fileType.equals("link", ignoreCase = true)) {
                    // A link should never be the restored "active guide" — it
                    // opens in the Browser tab. Clear it and show the picker so
                    // the Guide tab doesn't try to mount it as a single page.
                    vm.setActiveBookmark(vaultId, null)
                    state.showGuidePicker.value = true
                    return@LaunchedEffect
                }
                if (bookmark.fileType.equals("group", ignoreCase = true)) {
                    // Group restore is handled by GuideTabContent's effect.
                    // Two cases land here:
                    //  (a) First vault entry — openPageGroupByVault hasn't been
                    //      set yet; GuideTabContent's effect is about to swap us
                    //      out for the viewer. Showing the picker briefly is
                    //      fine; it's replaced the next frame.
                    //  (b) User just closed the group via back/tab-reselect —
                    //      we're staying mounted and need to surface the picker
                    //      ourselves; otherwise the screen is blank.
                    state.showGuidePicker.value = true
                    return@LaunchedEffect
                }
                state.showGuidePicker.value = false
                if (bookmark.fileType == "pdf") {
                    state.activeWheelGuide.value = null
                    state.activePdfGuide.value = bookmark
                } else {
                    state.currentBookmark.value = bookmark
                    val wv = state.webView.value
                        ?: snapshotFlow { state.webView.value }.first { it != null }
                    loadGuideBookmark(wv, bookmark, restoreScroll = true)
                }
            } else {
                // Bookmark was deleted — clear persisted ID, show picker
                vm.setActiveBookmark(vaultId, null)
                state.showGuidePicker.value = true
            }
        } else if (state.activePdfGuide.value == null && state.currentBookmark.value == null) {
            state.showGuidePicker.value = true
        }
    }

    // Bookmark file URLs (file:///…/guide/<uuid>/index.html) MUST go through
    // loadGuideBookmark: that is the path that serves a downloaded .html from
    // the HtmlScriptStripper copy in the cache rather than from the library
    // file itself (and fails closed when the stripped copy cannot be written).
    // Loading the raw file directly via WebView.loadUrl(file://…) would hand
    // third-party script a file:// origin with JavaScript on, and is also how
    // heavy GameFAQs-style pages OOM the WebView and ANR the
    // app. Returns true if the URL was recognized as a bookmark and a
    // stripped load was initiated; false otherwise so the caller can fall
    // through to the regular loadFileUrl path for non-bookmark URLs (landing
    // page, txt-guide bridge, etc.).
    val bookmarkFileUrlRegex = remember {
        Regex("/guide/([0-9a-fA-F-]{36})/index\\.html")
    }

    // When Guide tab is shown with a pending bookmark from Browser tab save, load it.
    // Uses a stable key (vaultId) and collects from the StateFlow directly so that
    // consumePendingGuideBookmarkId() — which clears the StateFlow — doesn't cancel
    // the running coroutine (the old LaunchedEffect(pendingId) pattern was cancelled
    // at the next delay() because pendingId changed to null).
    LaunchedEffect(vaultId) {
        mainViewModel.pendingGuideBookmarkId.collect { pendingMap ->
            val bookmarkId = pendingMap[vaultId] ?: return@collect
            mainViewModel.consumePendingGuideBookmarkId(vaultId)
            // Query the DB directly — bookmark was inserted before the pending ID was set
            var target: com.twinlore.app.domain.SavedTab? = null
            repeat(15) { // up to ~1.5s (usually immediate)
                target = withContext(Dispatchers.IO) { savedTabRepository.getTabById(bookmarkId) }
                if (target != null) return@repeat
                kotlinx.coroutines.delay(100)
            }
            val found = target ?: return@collect
            // For PDFs, wait until the resource pointer resolves (file write may still be flushing).
            if (found.fileType == "pdf") {
                val rid = found.resourceId
                if (rid != null) {
                    repeat(20) { // up to ~2s extra
                        val ok = withContext(Dispatchers.IO) {
                            val u = resourceResolver.resolve(rid)
                            when (u?.scheme) {
                                "file" -> u.path?.let { java.io.File(it).let { f -> f.exists() && f.length() > 0 } } == true
                                "content" -> {
                                    try { context.contentResolver.openInputStream(u)?.use { true } == true } catch (_: Exception) { false }
                                }
                                else -> false
                            }
                        }
                        if (ok) return@repeat
                        kotlinx.coroutines.delay(100)
                    }
                }
            }
            state.showGuidePicker.value = false
            val ft = found.fileType.lowercase()
            when {
                ft == "pdf" -> {
                    state.activeWheelGuide.value = null
                    state.currentBookmark.value = null
                    state.activePdfGuide.value = found
                    // PDF renders immediately in Compose — switch tabs now
                    if (mainViewModel.consumePendingGuideTabSwitch(vaultId)) {
                        mainViewModel.setVaultSessionTab(vaultId, com.twinlore.app.domain.VaultTab.Guide)
                    }
                }
                ft == "mht" || ft == "html" || ft == "txt" -> {
                    // Route through the new single-guide viewer dispatch so
                    // GuideTabContent picks it up via openSingleGuideByVault.
                    // Without this, Browser-tab save would re-mount the OLD
                    // GuideScreen WebView with the just-saved bookmark.
                    state.activePdfGuide.value = null
                    state.activeWheelGuide.value = null
                    state.currentBookmark.value = null
                    mainViewModel.openSingleGuideForVault(vaultId, found)
                    if (mainViewModel.consumePendingGuideTabSwitch(vaultId)) {
                        mainViewModel.setVaultSessionTab(vaultId, com.twinlore.app.domain.VaultTab.Guide)
                    }
                }
                else -> {
                    // EPUB / legacy HTML / other — keep the OLD viewer path
                    // until those types are migrated.
                    state.activePdfGuide.value = null
                    state.activeWheelGuide.value = null
                    state.currentBookmark.value = found
                    // Wait reactively for the WebView (may not exist yet if Guide
                    // tab is opening for the first time after a Browser-tab save).
                    // snapshotFlow observes the Compose state and emits when it changes.
                    val wv = state.webView.value
                        ?: snapshotFlow { state.webView.value }
                            .first { it != null }
                    // Tab switch is deferred to onPageFinished (see below) so the
                    // user stays on the Browser tab until the guide is rendered.
                    loadGuideBookmark(wv, found, restoreScroll = false)
                }
            }
        }
    }

    // Handle system back gesture — only when this tab is visible to prevent hidden tabs from consuming back.
    BackHandler(enabled = isTabVisible) {
        if (state.activeWheelGuide.value != null) {
            state.activeWheelGuide.value = null
            state.showGuidePicker.value = true
            updateInteraction()
            return@BackHandler
        }
        if (state.activePdfGuide.value != null) {
            // PDF viewer → picker. Focus bottom bar so user has control.
            state.activePdfGuide.value = null
            state.showGuidePicker.value = true
            updateInteraction()
            mainViewModel.setVaultSessionGuideChromeVisible(true)
            coroutineScope.launch {
                kotlinx.coroutines.delay(200)
                try { bottomBarFocusRequester?.requestFocus() } catch (_: Throwable) {}
            }
            return@BackHandler
        }
        if (state.showGuidePicker.value) {
            onBackFallback?.invoke() ?: return@BackHandler
            return@BackHandler
        }
        // HTML viewer → picker. Focus bottom bar so user has control.
        state.showGuidePicker.value = true
        updateInteraction()
        mainViewModel.setVaultSessionGuideChromeVisible(true)
        // Delay focus request to allow bottom bar AnimatedVisibility to start
        coroutineScope.launch {
            kotlinx.coroutines.delay(200)
            try { bottomBarFocusRequester?.requestFocus() } catch (_: Throwable) {}
        }
    }
    
    // L1/R1 browser back/forward and other controller actions
    LaunchedEffect(isTabVisible, pdfActive) {
        if (!isTabVisible || pdfActive) return@LaunchedEffect
        mainViewModel.controllerAction.collect { action ->
        when (action) {
            MainViewModel.ControllerAction.BROWSER_BACK -> {
                // Guide mode: do not allow going back in WebView history
                updateInteraction()
            }
            MainViewModel.ControllerAction.BROWSER_FORWARD -> {
                // Guide mode: do not allow going forward in WebView history
                updateInteraction()
            }
            MainViewModel.ControllerAction.BROWSER_SCROLL_MARKER_PREV,
            MainViewModel.ControllerAction.BROWSER_SCROLL_MARKER_NEXT -> {
                // (Browser-only: skipped in GuideScreen)
                if (state.scrollMarkers.value.isEmpty()) {
                    state.toastMessage.value = "No scroll markers"
                } else {
                    val dir = if (action == MainViewModel.ControllerAction.BROWSER_SCROLL_MARKER_NEXT) 1 else -1
                    val current = state.focusedScrollMarkerIndex.value ?: 0
                    val next = (current + dir + state.scrollMarkers.value.size) % state.scrollMarkers.value.size
                    state.focusedScrollMarkerIndex.value = next
                    val marker = state.scrollMarkers.value[next]
                    state.currentBookmark.value?.let { b ->
                        coroutineScope.launch { savedTabRepository.setFocusedScrollMarkerId(vaultId, b.id, marker.id) }
                    }
                    scrollToMarkerByText(state.webView.value, marker.label, marker.scrollY, marker.occurrenceIndex) { y ->
                        vm.setScrollPosition(y)
                        highlightScrollMarkerInPage(state.webView.value, state.scrollMarkers.value, state.focusedScrollMarkerIndex.value, effectiveHighlightHexBrowser, effectiveContrastHexBrowser)
                        state.toastMessage.value = "Marker: ${marker.label}"
                        updateInteraction()
                    }
                }
                updateInteraction()
            }
            MainViewModel.ControllerAction.BROWSER_SCROLL_TO_TOP -> {
                state.webView.value?.evaluateJavascript("window.scrollTo(0,0);", null)
                vm.setScrollPosition(0)
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
                vm.setZoom(1f)
                updateInteraction()
            }
            MainViewModel.ControllerAction.BROWSER_PREV_BOOKMARK,
            MainViewModel.ControllerAction.BROWSER_NEXT_BOOKMARK -> {
                // (Browser-only: skipped in GuideScreen)
                if (navigableBookmarks.isEmpty()) {
                    state.toastMessage.value = "No bookmarks"
                    updateInteraction()
                    return@collect
                }
                val currentUrl = state.webView.value?.url ?: vm.currentUrl.value ?: ""
                val currentIndex = state.currentBookmark.value?.let { b -> navigableBookmarks.indexOfFirst { it.id == b.id } }
                    ?: navigableBookmarks.indexOfFirst { b ->
                        currentUrl == b.url ||
                            (b.url.startsWith("file://") && currentUrl.contains(b.id)) ||
                            (currentUrl.startsWith("file://") && currentUrl.contains(b.id))
                    }
                val idx = if (currentIndex >= 0) currentIndex else -1
                val targetIndex = when (action) {
                    MainViewModel.ControllerAction.BROWSER_NEXT_BOOKMARK ->
                        if (idx < 0) 0 else (idx + 1) % navigableBookmarks.size
                    MainViewModel.ControllerAction.BROWSER_PREV_BOOKMARK ->
                        if (idx < 0) navigableBookmarks.size - 1 else (idx - 1 + navigableBookmarks.size) % navigableBookmarks.size
                    else -> 0
                }
                val target = navigableBookmarks[targetIndex]
                val bookmarkWeAreLeaving = if (idx >= 0) navigableBookmarks[idx] else null
                val wv = state.webView.value
                if (wv != null || target.fileType == "pdf") {
                    // Save scroll state of the bookmark we're leaving
                    if (bookmarkWeAreLeaving != null) {
                        launch(Dispatchers.IO) {
                            val scrollData = vm.scrollRestoreData.value
                                ?: com.twinlore.app.domain.ScrollRestoreData.ratioOnly(0.0)
                            savedTabRepository.updateBookmarkScrollRestore(vaultId, bookmarkWeAreLeaving.id, scrollData)
                        }
                    }
                    loadGuideBookmark(wv ?: state.webView.value!!, target)
                } else {
                    // LaunchedEffect on currentBookmark.id will fire the title overlay once
                    // loadGuideBookmark updates currentBookmark.
                }
                updateInteraction()
            }
            else -> { }
        }
        }
    }

    // Browser content - always in composition
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .onKeyEvent { keyEvent ->
                // Show chrome on button presses. Dpad UP/DOWN scrolls the page; the scroll-down watcher may hide chrome as a result.
                val keyCode = keyEvent.nativeKeyEvent.keyCode
                if (keyCode != android.view.KeyEvent.KEYCODE_DPAD_UP && keyCode != android.view.KeyEvent.KEYCODE_DPAD_DOWN) {
                    updateInteraction()
                }
                false
            }
    ) {
        // Measured top bar height (updated via onGloballyPositioned) — used for WebView padding and progress bar offset
        val density = LocalDensity.current
        var measuredTopBarHeightDp by remember { mutableStateOf(0.dp) }

        // Track if we're on the landing page (starts hidden on first load via state.showTopBar.value state)
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
                // WebView content - use key to keep WebView alive when switching between map and browser
                androidx.compose.runtime.key(vaultId) {
                val hasToc = state.currentBookmark.value != null
                    Box(modifier = Modifier.fillMaxSize()) {
                        WebViewContent(
                            initialUrl = initialUrl,
                            webView = { wv -> state.webView.value = wv },
                    favoritePath = null,
                    onUrlChanged = onUrlChanged@{ url ->
                        // When loadDataWithBaseURL is active, WebView reports unreliable URLs
                        // (about:blank, data:, file:///). Don't let these overwrite the real URL.
                        if (state.isDataUrlLoaded.value) {
                            val vmUrl = vm.currentUrl.value
                            if (vmUrl != null && vmUrl.startsWith("http") &&
                                (url == "about:blank" || url.startsWith("data:") || url == "file:///")) {
                                return@onUrlChanged
                            }
                        }
                        // Resolve current bookmark from URL. Set scroll markers and TOC accordingly.
                        val resolved = state.currentBookmark.value?.takeIf { cb ->
                            url == cb.url || (url.startsWith("file://") && cb.id != null && url.contains(cb.id))
                                || (url.startsWith("content://") && cb.id != null && url.contains(cb.id))
                        } ?: bookmarks.find { b ->
                            url == b.url || (url.startsWith("file://") && b.id != null && url.contains(b.id))
                                || (url.startsWith("content://") && b.id != null && url.contains(b.id))
                        }
                        // Only clear currentBookmark when navigating to a non-bookmark page (http or empty).
                        // When loading a bookmark's file:// URL, resolved can be null briefly or URL may not match;
                        // don't clear so scroll markers and TOC stay visible.
                        // Do not clear during scroll restore: onUrlChanged can fire after restore completes but before we re-load markers.
                        if (resolved != null) {
                            state.currentBookmark.value = resolved
                        } else if ((url.isBlank() || url.startsWith("http")) && !state.isRestoringScroll.value) {
                            // Do not clear when a TXT guide is actively loaded (loadDataWithBaseURL may fire blank/about:blank URL changes).
                            val viewModelUrl = vm.currentUrl.value
                            val isTxtGuideActive = viewModelUrl != null && viewModelUrl.startsWith("file:///txt-guide/")
                            if (!isTxtGuideActive) {
                                state.currentBookmark.value = null
                                state.scrollMarkers.value = emptyList()
                                state.focusedScrollMarkerIndex.value = null
                                state.tocEntries.value = emptyList()
                                state.textFaqTocActive.value = false
                                state.showTocDrawer.value = false
                            }
                        }
                        // Keep ViewModel URL in sync with WebView so last-visited URL is saved (including regular http(s) sites).
                        // Don't overwrite a txt-guide URL with the generic base URL ("file:///") from loadDataWithBaseURL.
                        val currentViewModelUrl = vm.currentUrl.value
                        when {
                            url.startsWith("file://") || url.startsWith("content://") -> {
                                val isTxtGuideUrl = currentViewModelUrl != null && currentViewModelUrl.startsWith("file:///txt-guide/")
                                val isGenericBaseUrl = url == "file:///" || url == "file:///android_asset/" || url == "about:blank"
                                if (!(isTxtGuideUrl && isGenericBaseUrl)) {
                                    vm.setUrl(url)
                                }
                            }
                            url.startsWith("http") -> {
                                if (currentViewModelUrl != null && (currentViewModelUrl.startsWith("file://") || currentViewModelUrl.startsWith("content://"))) {
                                    // Keep file/content URL: WebView is reporting the base URL of a converted page, not a real navigation
                                } else {
                                    // Only update the URL if it matches the current bookmark's URL.
                                    // External links (e.g. YouTube) are opened in the browser tab via shouldOverrideUrlLoading,
                                    // but onPageFinished can still fire in edge cases — never let an external URL overwrite
                                    // the guide's saved position.
                                    val bookmarkUrl = state.currentBookmark.value?.url
                                    val bookmarkBase = bookmarkUrl?.substringBefore("#")?.substringBefore("?")
                                    val urlBase = url.substringBefore("#").substringBefore("?")
                                    if (bookmarkUrl == null || urlBase == bookmarkBase) {
                                        vm.setUrl(url)
                                    }
                                    // else: external URL — do not update vm.currentUrl
                                }
                            }
                            else -> vm.setUrl(url)
                        }
                        // Don't show top bar on URL changes during page load (page load is not user interaction)
                        updateInteraction()
                    },
                    onCanGoBackChanged = { /* Guide doesn't use back/forward */ },
                    onCanGoForwardChanged = { /* Guide doesn't use back/forward */ },
                    onLoadingChanged = { loading ->
                        vm.setLoading(loading)
                    },
                    zoomLevel = zoomLevel,
                    onZoomChanged = { vm.setZoom(it) },
                    onZoomIn = {
                        vm.setZoom(vm.zoomLevel.value + 0.1f)
                        updateInteraction()
                    },
                    onZoomOut = {
                        vm.setZoom(vm.zoomLevel.value - 0.1f)
                        updateInteraction()
                    },
                    onResetZoom = {
                        vm.setZoom(1f)
                        updateInteraction()
                    },
                    // Allow showing the top bar on the landing page after the first interaction.
                    // We still start hidden on first load via state.showTopBar.value initial state.
                    showTopBar = state.showTopBar.value,
                    hasToc = hasToc && !state.showTocDrawer.value,
                    onOpenTocDrawer = { state.showTocDrawer.value = true; state.showTopBar.value = false },
                    onScrollChanged = { position, maxScrollY ->
                        vm.setScrollPosition(position)
                        vm.setMaxScrollY(maxScrollY)
                        mainViewModel.updateWebViewScrollState(position, maxScrollY)
                        // Don't capture scroll during restore or within 1200ms after — prevents wrong position from overwriting saved data
                        // (ratio restore may re-verify at +500ms after initial scrollTo, so need wider window)
                        val isRestoring = state.isRestoringScroll.value
                        val msSinceComplete = System.currentTimeMillis() - state.scrollRestoreCompletedAt.value
                        if (!isRestoring && msSinceComplete > 1200L) {
                        state.scrollCaptureJob.value?.cancel()
                        state.scrollCaptureJob.value = coroutineScope.launch {
                            kotlinx.coroutines.delay(200L)
                            if (state.isRestoringScroll.value) return@launch // Double-check after delay
                            val wv = state.webView.value ?: return@launch
                            wv.evaluateJavascript("""
                                (function() {
                                    // Skip scroll capture if user has active text selection — the anchor-finding
                                    // logic below calls removeAllRanges()/window.find() which would clear it.
                                    var sel = window.getSelection();
                                    if (sel && sel.rangeCount > 0 && !sel.isCollapsed) return null;
                                    var scrollY = window.pageYOffset || 0;
                                    var docElScrollY = document.documentElement.scrollTop || 0;
                                    var bodyScrollY = document.body.scrollTop || 0;
                                    var pageHeight = Math.max(document.body.scrollHeight || 0, document.documentElement.scrollHeight || 0) || 1;
                                    var isGameFaqs = !!(document.body && document.body.getAttribute('data-gamefaqs-converted'));
                                    if (isGameFaqs && pageHeight > 0) {
                                        return JSON.stringify({ scrollY: scrollY, pageHeight: pageHeight });
                                    }
                                    // Helper: check if an element or any ancestor has fixed/sticky positioning
                                    // (these overlay the viewport and return the same text regardless of scroll)
                                    function isFixedOrSticky(el) {
                                        while (el && el !== document.body && el !== document.documentElement) {
                                            var pos = window.getComputedStyle(el).position;
                                            if (pos === 'fixed' || pos === 'sticky') return true;
                                            el = el.parentElement;
                                        }
                                        return false;
                                    }
                                    // Try multiple y-coordinates to find a non-fixed element
                                    var x = window.innerWidth / 2;
                                    var yPositions = [60, 120, 200, 300];
                                    var range = null, raw = null, useElementFallback = false;
                                    for (var yi = 0; yi < yPositions.length; yi++) {
                                        var y = yPositions[yi];
                                        range = null; raw = null; useElementFallback = false;
                                        // Check if the element at this point is fixed/sticky
                                        var hitEl = document.elementFromPoint(x, y);
                                        if (hitEl && isFixedOrSticky(hitEl)) continue;
                                        if (document.caretRangeFromPoint) range = document.caretRangeFromPoint(x, y);
                                        else if (document.caretPositionFromPoint) {
                                            var pos = document.caretPositionFromPoint(x, y);
                                            if (pos && pos.offsetNode) {
                                                range = document.createRange();
                                                range.setStart(pos.offsetNode, pos.offset);
                                                range.collapse(true);
                                            }
                                        }
                                        if (range) {
                                            // Verify the range's parent isn't fixed/sticky either
                                            var rangeParent = range.startContainer;
                                            if (rangeParent && rangeParent.nodeType === 3) rangeParent = rangeParent.parentElement;
                                            if (rangeParent && isFixedOrSticky(rangeParent)) { range = null; continue; }
                                            try { if (range.expand) range.expand("paragraph"); } catch (e) {}
                                            raw = (range.toString() || "").trim();
                                            if (raw) break;
                                        }
                                    }
                                    if (!raw) {
                                        // Fallback: try elementFromPoint at each y, skip fixed/sticky
                                        for (var yi2 = 0; yi2 < yPositions.length; yi2++) {
                                            var el = document.elementFromPoint(x, yPositions[yi2]);
                                            while (el && (!el.textContent || el.textContent.trim().length < 2)) el = el.parentElement;
                                            if (el && el.textContent && !isFixedOrSticky(el)) {
                                                raw = el.textContent.trim().substring(0, 600);
                                                useElementFallback = true;
                                                break;
                                            }
                                        }
                                    }
                                    if (!raw) return null;
                                    var normalized = raw.replace(/[^a-zA-Z0-9]/g, "").toLowerCase();
                                    if (useElementFallback && normalized.length > 120) normalized = normalized.substring(0, 120);
                                    if (!normalized) return null;
                                    window.getSelection().removeAllRanges();
                                    var count = 0, foundIndex = useElementFallback ? 1 : 0;
                                    var targetNode = range ? range.startContainer : null, targetOffset = range ? range.startOffset : 0;
                                    while (window.find(normalized)) {
                                        count++;
                                        if (useElementFallback) { foundIndex = 1; break; }
                                        var sel = window.getSelection();
                                        if (sel.rangeCount) {
                                            var r = sel.getRangeAt(0);
                                            if (r.startContainer === targetNode && r.startOffset === targetOffset) { foundIndex = count; break; }
                                        }
                                    }
                                    if (!useElementFallback) foundIndex = foundIndex || count;
                                    return JSON.stringify({ normalizedAnchor: normalized, occurrenceIndex: foundIndex || count || 1, scrollY: scrollY, pageHeight: pageHeight });
                                })();
                            """.trimIndent()) { result ->
                                fun saveScrollData(data: ScrollRestoreData) {
                                    val url = currentUrl ?: wv.url ?: ""
                                    val zoom = vm.zoomLevel.value
                                    // Never save an external HTTP URL as the guide's last position.
                                    // This guards against edge cases where an external link (e.g. YouTube) somehow
                                    // triggers a scroll capture before shouldOverrideUrlLoading can intercept it.
                                    if (url.startsWith("http")) {
                                        val bookmarkUrl = state.currentBookmark.value?.url
                                        val bookmarkBase = bookmarkUrl?.substringBefore("#")?.substringBefore("?")
                                        val urlBase = url.substringBefore("#").substringBefore("?")
                                        if (bookmarkUrl != null && urlBase != bookmarkBase) {
                                            return // External URL — do not overwrite the guide's saved state
                                        }
                                    }
                                    // Prefer state.currentBookmark (always up-to-date via MutableState) over bookmarks list
                                    // which may be stale if captured in a lambda before the Flow emitted.
                                    val bookmark = state.currentBookmark.value?.takeIf { cb ->
                                        url == cb.url || (url.startsWith("file://") && cb.id != null && url.contains(cb.id))
                                            || (url.startsWith("content://") && cb.id != null && url.contains(cb.id))
                                    } ?: bookmarks.find { url == it.url || (url.startsWith("file://") && url.contains(it.id)) || (url.startsWith("content://") && url.contains(it.id)) }

                                    vm.setScrollRestoreData(data)
                                    coroutineScope.launch(Dispatchers.IO) {
                                        if (bookmark != null) {
                                            savedTabRepository.updateBookmarkScrollRestore(vaultId, bookmark.id, data, updatedZoomLevel = zoom)
                                        }
                                        vm.stateManager.saveLastPageState(vaultId, url, data)
                                    }
                                }
                                val json = result?.removeSurrounding("\"")?.replace("\\\"", "\"")
                                if (json.isNullOrBlank() || json == "null") {
                                    // Full capture failed (e.g. caretRangeFromPoint unavailable); get at least scroll ratio
                                    wv.evaluateJavascript("""
                                        (function() {
                                            var sy = window.pageYOffset || 0;
                                            var ph = Math.max(document.body.scrollHeight || 0, document.documentElement.scrollHeight || 0) || 1;
                                            return JSON.stringify({ scrollY: sy, pageHeight: ph });
                                        })();
                                    """.trimIndent()) { fallbackResult ->
                                        val fb = fallbackResult?.removeSurrounding("\"")?.replace("\\\"", "\"") ?: return@evaluateJavascript
                                        val scrollY = Regex(""""scrollY"\s*:\s*(\d+)""").find(fb)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                                        val pageHeight = Regex(""""pageHeight"\s*:\s*(\d+)""").find(fb)?.groupValues?.get(1)?.toIntOrNull() ?: 1
                                        val ratio = if (pageHeight > 0) scrollY.toDouble() / pageHeight else 0.0
                                        saveScrollData(ScrollRestoreData.ratioOnly(ratio.coerceIn(0.0, 1.0)))
                                    }
                                    return@evaluateJavascript
                                }
                                val scrollY = Regex(""""scrollY"\s*:\s*(\d+)""").find(json)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                                val pageHeight = Regex(""""pageHeight"\s*:\s*(\d+)""").find(json)?.groupValues?.get(1)?.toIntOrNull() ?: 1
                                val anchor = Regex(""""normalizedAnchor"\s*:\s*"([^"]*)""").find(json)?.groupValues?.get(1)
                                val occIdx = Regex(""""occurrenceIndex"\s*:\s*(\d+)""").find(json)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                                val ratio = if (pageHeight > 0) scrollY.toDouble() / pageHeight else 0.0
                                val data = ScrollRestoreData(anchor, occIdx.coerceAtLeast(0), ratio.coerceIn(0.0, 1.0))
                                saveScrollData(data)
                            }
                        }
                        } // end if (!isRestoringScroll)
                    },
                    getSavedScrollRestore = { state.pendingScrollRestore.value }, // Read at onPageFinished so offline switch gets current scroll
                    getScrollPosition = { vm.scrollPosition.value }, // For capture (scroll Y)
                    onNewWindowLoadInCurrentTab = {
                        vm.setScrollPosition(0)
                        vm.setMaxScrollY(0)
                    }, // New-tab link in current tab: start at top
                    onScrollRestoreStarted = { state.isRestoringScroll.value = true },
                    onScrollRestoreCompleted = {
                        // Reveal WebView and dismiss loading spinner now that content is
                        // fully ready (CSS injected, scroll position restored).
                        state.isConvertingTextFaq.value = false
                        state.pendingScrollRestore.value = null
                        // Re-resolve bookmark and load scroll markers + highlights after restore.
                        val url = state.webView.value?.url ?: currentUrl ?: ""
                        if (url.isNotBlank()) {
                            val resolved = state.currentBookmark.value?.takeIf { cb ->
                                url == cb.url || (url.startsWith("file://") && cb.id != null && url.contains(cb.id))
                                    || (url.startsWith("content://") && cb.id != null && url.contains(cb.id))
                            } ?: bookmarks.find { b ->
                                url == b.url || (url.startsWith("file://") && b.id != null && url.contains(b.id))
                                    || (url.startsWith("content://") && b.id != null && url.contains(b.id))
                            }
                            if (resolved != null) {
                                if (state.currentBookmark.value?.id != resolved.id) {
                                    state.currentBookmark.value = resolved
                                }
                                coroutineScope.launch {
                                    val (markers, focusedIndex) = loadScrollMarkersForBookmark(vaultId, resolved.id, savedTabRepository)
                                    state.scrollMarkers.value = markers
                                    state.focusedScrollMarkerIndex.value = focusedIndex
                                    if (markers.isNotEmpty()) {
                                        highlightScrollMarkerInPage(state.webView.value, markers, state.focusedScrollMarkerIndex.value, effectiveHighlightHexBrowser, effectiveContrastHexBrowser)
                                    }
                                    state.isRestoringScroll.value = false; state.scrollRestoreCompletedAt.value = System.currentTimeMillis()
                                }
                            } else {
                                state.isRestoringScroll.value = false; state.scrollRestoreCompletedAt.value = System.currentTimeMillis()
                            }
                        } else {
                            state.isRestoringScroll.value = false; state.scrollRestoreCompletedAt.value = System.currentTimeMillis()
                        }
                        if (state.scrollMarkers.value.isNotEmpty()) {
                            highlightScrollMarkerInPage(state.webView.value, state.scrollMarkers.value, state.focusedScrollMarkerIndex.value, effectiveHighlightHexBrowser, effectiveContrastHexBrowser)
                        }
                    },
                    isLoading = isLoading, // Pass loading state to prevent restoration during loading
                    isRestoringScroll = state.isRestoringScroll.value, // Pass restoration state to prevent nested restoration
                    onTouch = {
                        // Reader-mode chrome: tap toggles; updateInteraction resets the 5 s timer
                        // so chrome stays visible while you're interacting.
                        if (state.showTopBar.value) {
                            state.showTopBar.value = false
                        } else {
                            updateInteraction()
                        }
                    },
                    onAsciiBlockToggled = {
                        // Expanding/shrinking an ASCII block is a deliberate interaction; surface chrome
                        // so the user can navigate without the otherwise-suppressed tap-toggle.
                        updateInteraction()
                    },
                    onImageLongPress = { imageUrl ->
                        state.selectedImageUrl.value = imageUrl
                        state.showImageContextMenu.value = true
                        updateInteraction()
                    },
                    onGuideImageTap = { srcUrl, localPath ->
                        // Tap on an inlined <img> in a saved guide opens the in-app pinch-to-zoom
                        // viewer instead of following any wrapping <a href> (which previously
                        // foreground-switched to the Browser tab and lost Guide context). Prefer
                        // the manifest-resolved local file:// path so the image loads from disk
                        // with no network; fall back to the original URL if the image isn't in
                        // the MHT cache (Coil will fetch in that case).
                        state.guideImageZoomModel.value = localPath ?: srcUrl
                        updateInteraction()
                    },
                    customSelectionActions = run {
                        val urlForBookmark = currentUrl ?: state.webView.value?.url ?: ""
                        val resolvedBookmark = bookmarks.find { b ->
                            urlForBookmark == b.url || (urlForBookmark.startsWith("file://") && b.id != null && urlForBookmark.contains(b.id))
                        } ?: state.currentBookmark.value?.takeIf { b ->
                            urlForBookmark == b.url || (urlForBookmark.startsWith("file://") && b.id != null && urlForBookmark.contains(b.id))
                        }
                        GuideWebView.CustomSelectionActions(
                            showSaveMarker = resolvedBookmark != null,
                            onSaveMarker = { text, occurrenceIndex ->
                                state.webView.value?.evaluateJavascript("window.getSelection().removeAllRanges()", null)
                                if (resolvedBookmark != null) {
                                    val scrollY = vm.scrollPosition.value
                                    val wasEmpty = state.scrollMarkers.value.isEmpty()
                                    coroutineScope.launch {
                                        savedTabRepository.addScrollMarker(vaultId, resolvedBookmark.id, text, scrollY, occurrenceIndex)
                                            .onSuccess { newMarker ->
                                                val updated = savedTabRepository.getScrollMarkers(vaultId, resolvedBookmark.id)
                                                withContext(Dispatchers.Main) {
                                                    state.scrollMarkers.value = updated.toList()
                                                    val newFocused = updated.indexOfFirst { it.id == newMarker.id }.takeIf { it >= 0 } ?: (updated.size - 1).coerceAtLeast(0)
                                                    state.focusedScrollMarkerIndex.value = newFocused
                                                    state.toastMessage.value = "Scroll marker saved: ${newMarker.label.take(30)}${if (newMarker.label.length > 30) "…" else ""}"
                                                    highlightScrollMarkerInPage(state.webView.value, updated, newFocused, effectiveHighlightHexBrowser, effectiveContrastHexBrowser)
                                                    // Show scroll markers bar and top bar on first marker save
                                                    if (wasEmpty) {
                                                        setScrollMarkersBarVisible(true)
                                                        state.showTopBar.value = true
                                                        updateInteraction()
                                                    }
                                                }
                                            }
                                            .onFailure { e ->
                                                state.toastMessage.value = e.message ?: "Failed to save marker"
                                            }
                                    }
                                } else {
                                    state.toastMessage.value = "Open a bookmark to save scroll markers"
                                }
                            },
                            onFindInPage = { text ->
                                state.findQuery.value = text
                                state.findOpenedWithSelection.value = true
                                state.showFindOnPage.value = true
                                state.findMatchesFound.value = null
                                state.findMatchCount.value = 0
                                state.findCurrentIndex.value = 0
                                state.webView.value?.evaluateJavascript("window.getSelection().removeAllRanges()", null)
                                updateInteraction()
                            },
                            onYouTube = { text ->
                                var query = text.trim()
                                if (state.appendGameName.value && effectiveVault?.displayName?.isNotBlank() == true && !query.contains(effectiveVault!!.displayName!!, ignoreCase = true)) {
                                    query = "$query ${effectiveVault!!.displayName!!.trim()}".trim()
                                }
                                if (vaultId.isNotBlank() && query.isNotBlank()) {
                                    searchHistoryManager.saveSearch(vaultId, query)
                                }
                                val youtubeUrl = "https://www.youtube.com/results?search_query=" + Uri.encode(query)
                                mainViewModel.setPendingUrlForBrowserTab(vaultId, youtubeUrl)
                                mainViewModel.setVaultSessionTab(vaultId, com.twinlore.app.domain.VaultTab.Browser)
                                state.webView.value?.evaluateJavascript("window.getSelection().removeAllRanges()", null)
                            },
                            onSearchOnline = { text ->
                                var query = text.trim()
                                if (state.appendGameName.value && effectiveVault?.displayName?.isNotBlank() == true &&
                                    !query.contains(effectiveVault!!.displayName!!, ignoreCase = true)) {
                                    query = "$query ${effectiveVault!!.displayName!!.trim()}".trim()
                                }
                                if (vaultId.isNotBlank() && query.isNotBlank()) {
                                    searchHistoryManager.saveSearch(vaultId, query)
                                }
                                val searchUrl = buildSearchUrlForEngine(currentSearchEngine, query)
                                mainViewModel.setPendingUrlForBrowserTab(vaultId, searchUrl)
                                mainViewModel.setVaultSessionTab(vaultId, com.twinlore.app.domain.VaultTab.Browser)
                                state.webView.value?.evaluateJavascript("window.getSelection().removeAllRanges()", null)
                            },
                            onCopy = { text ->
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                clipboard?.setPrimaryClip(ClipData.newPlainText("selection", text))
                                state.toastMessage.value = "Copied to clipboard"
                                state.webView.value?.evaluateJavascript("window.getSelection().removeAllRanges()", null)
                            }
                        )
                    },
                    vaultId = vaultId,
                    artworkPath = effectiveVaultArtworkPath,
                    gameName = effectiveVault?.displayName,
                    appendGameName = state.appendGameName.value,
                    currentSearchEngine = currentSearchEngine,
                    landingEntriesRef = state.landingEntriesRef,
                    onEditLandingSlot = { slotIndex -> editingLandingSlot = slotIndex },
                    onMinZoomComputed = { vm.setMinZoomLevel(it) },
                    onPageFinished = { wv, url ->

                        // Prefer state.currentBookmark (always up-to-date via MutableState) over bookmarks list
                        // which may be stale if captured in a lambda before the Flow emitted.
                        val matchingBookmark = if (url != null) {
                            state.currentBookmark.value?.takeIf { cb ->
                                url == cb.url || (url.startsWith("file://") && cb.id != null && url.contains(cb.id))
                                    || (url.startsWith("content://") && cb.id != null && url.contains(cb.id))
                            } ?: bookmarks.find { b ->
                                url == b.url || (url.startsWith("file://") && b.id != null && url.contains(b.id))
                                    || (url.startsWith("content://") && b.id != null && url.contains(b.id))
                            }
                        } else null
                        val urlMatchesBookmark = wv != null && matchingBookmark != null
                        val isConvertedPage = false
                        val isTxtGuideLoaded = state.currentBookmark.value?.fileType == "txt" &&
                            vm.currentUrl.value?.startsWith("file:///txt-guide/") == true

                        if (urlMatchesBookmark || (wv != null && isConvertedPage) || (wv != null && isTxtGuideLoaded)) {
                            // Ensure currentBookmark is always set when a bookmark page loads
                            if (state.currentBookmark.value == null && matchingBookmark != null) {
                                state.currentBookmark.value = matchingBookmark
                            }
                            // Capture bookmark ID now; guard the extraction so a stale delayed call from
                            // a previously loaded guide cannot overwrite the newly selected guide's TOC.
                            // Skip if text FAQ TOC was already detected from body text (GameFAQs/Neoseeker text pages).
                            val bookmarkIdForToc = (matchingBookmark ?: state.currentBookmark.value)?.id
                            wv?.postDelayed({
                                if (state.currentBookmark.value?.id == bookmarkIdForToc && !state.textFaqTocActive.value) {
                                    extractTocEntries(wv) { state.tocEntries.value = it }
                                }
                            }, 900L)
                            // Deferred tab switch: guide has finished rendering, safe to show it now
                            if (mainViewModel.consumePendingGuideTabSwitch(vaultId)) {
                                mainViewModel.setVaultSessionTab(vaultId, com.twinlore.app.domain.VaultTab.Guide)
                            }
                        }
                    },
                    onTextFaqTocDetected = { entries ->
                        // Text FAQ section detection feeds directly into the TOC sidebar.
                        // No DOM modification — sections are detected in Kotlin, scroll positions
                        // measured via Range API, and entries populate the sidebar drawer.
                        if (entries.isNotEmpty()) {
                            state.tocEntries.value = entries
                            state.textFaqTocActive.value = true
                        }
                    },
                    onScrollMarkerHighlightTap = { markerIndex ->
                        val marker = state.scrollMarkers.value.getOrNull(markerIndex)
                        if (marker != null) {
                            state.scrollMarkerLongPressed.value = marker
                            state.showScrollMarkerDeleteAllMenu.value = true
                        }
                    },
                    vaultSessionMode = VaultSessionMode.Guide,
                    currentBookmarkUrl = state.currentBookmark.value?.url,
                    onOpenLinkInBrowser = if (onOpenLinkInBrowser != null) {
                        // When opening a link from Guide in Browser tab, persist Guide's current URL first
                        // so it won't be overwritten and the online/offline toggle stays visible when returning
                        { url: String ->
                            val guideUrl = vm.currentUrl.value ?: state.webView.value?.url
                            if (!guideUrl.isNullOrBlank()) {
                                vm.stateManager.saveLastPageState(
                                    vaultId, guideUrl, vm.scrollRestoreData.value
                                )
                            }
                            onOpenLinkInBrowser!!(url)
                        }
                    } else {
                        onOpenLinkInBrowser
                    },
                    browserOneOffLoad = null,
                    oneOffUrlBeingLoaded = null,
                    assetManifestState = state.assetManifest,
                    assetBaseDirState = state.assetBaseDir,
                    isDataUrlLoadedState = state.isDataUrlLoaded,
                    guideThemeInject = guideThemeInject,
                    zoomControlsFocusRequester = zoomControlsFocusRequester,
                    // Release the shared content requester while the wheel overlay is
                    // up so the wheel's capture layer is the sole binding (D-pad UP
                    // from the bottom bar then lands on the wheel, not this hidden
                    // persistent top bar). PDF path is intentionally unchanged.
                    topBarFocusRequester = if (state.activeWheelGuide.value == null) contentFocusRequester else null,
                    bottomBarFocusRequester = bottomBarFocusRequester,
                    onDownloadRequested = null,
                    modifier = Modifier.fillMaxSize()
                )

                        if (state.showTocDrawer.value && hasToc) {
                            val tocEntriesList = state.tocEntries.value
                            if (tocEntriesList.isEmpty() && !state.textFaqTocActive.value) {
                                // No TOC entries: try re-extraction, then show toast
                                LaunchedEffect(Unit) {
                                    state.webView.value?.let { wv ->
                                        extractTocEntries(wv) { entries ->
                                            if (entries.isNotEmpty()) {
                                                state.tocEntries.value = entries
                                            } else {
                                                state.toastMessage.value = "No sections found"
                                                state.showTocDrawer.value = false
                                            }
                                        }
                                    } ?: run {
                                        state.toastMessage.value = "No sections found"
                                        state.showTocDrawer.value = false
                                    }
                                }
                            } else {
                            val isGraphicalToc = tocEntriesList.size == 7 &&
                                tocEntriesList.getOrNull(0)?.label == "Top" &&
                                tocEntriesList.getOrNull(6)?.label == "Bottom" &&
                                tocEntriesList.getOrNull(1)?.label?.startsWith("Section ") == true
                            TocDrawerOverlay(
                                entries = tocEntriesList,
                                webView = state.webView.value,
                                currentScrollY = scrollPosition,
                                maxScrollY = maxScrollY,
                                focusColor = focusColorBrowser,
                                onClose = { state.showTocDrawer.value = false },
                                tocScrollState = tocDrawerScrollState,
                                basicView = state.tocBasicView.value,
                                onToggleView = { state.tocBasicView.value = !state.tocBasicView.value },
                                onEntryClick = { entry ->
                                    // Offset so the selected section heading appears slightly below the top of the viewport
                                    val topOffsetPx = 80
                                    state.webView.value?.let { wv ->
                                        // Scroll slightly above the target (80px offset) so the section heading
                                        // appears below the top of the screen, not flush against it
                                        val scrollOffset = 80
                                        if (isGraphicalToc && tocEntriesList.isNotEmpty()) {
                                            val lastScroll = tocEntriesList.maxOf { it.scrollY }.coerceAtLeast(1)
                                            val ratio = entry.scrollY.toFloat() / lastScroll
                                            wv.evaluateJavascript("(function(){ var h = Math.max(document.body.scrollHeight, document.documentElement.scrollHeight); window.scrollTo(0, Math.max(0, Math.round(h * $ratio) - $scrollOffset)); })();", null)
                                        } else if (entry.lineIndex >= 0) {
                                            // Text FAQ / converted page: scroll to heading by DOM ID.
                                            // Uses getBoundingClientRect() at scroll time so position is
                                            // always correct regardless of current zoom level.
                                            val secId = "sec-${entry.lineIndex}"
                                            val accentRgba = colorToRgba(focusColorBrowser, 1f)
                                            wv.evaluateJavascript("""
                                                (function() {
                                                    var el = document.getElementById('$secId');
                                                    if (el) {
                                                        var rect = el.getBoundingClientRect();
                                                        var targetY = Math.round(rect.top + window.scrollY);
                                                        window.scrollTo(0, Math.max(0, targetY - $scrollOffset));
                                                    } else {
                                                        window.scrollTo(0, Math.max(0, ${entry.scrollY} - $scrollOffset));
                                                    }
                                                    if (!el) return;
                                                    var next = el.nextSibling;
                                                    if (!next || next.nodeType !== 3) return;
                                                    var text = next.textContent;
                                                    var nl = text.indexOf('\n');
                                                    var first = nl >= 0 ? text.substring(0, nl) : text;
                                                    var rest = nl >= 0 ? text.substring(nl) : '';
                                                    var hl = document.createElement('span');
                                                    hl.textContent = first;
                                                    hl.style.cssText = 'color:$accentRgba!important;transition:color 1s ease-out';
                                                    next.textContent = rest;
                                                    el.after(hl);
                                                    setTimeout(function(){ hl.style.setProperty('color','inherit','important'); }, 100);
                                                    setTimeout(function(){
                                                        hl.replaceWith(document.createTextNode(first));
                                                        el.parentNode.normalize();
                                                    }, 1500);
                                                })();
                                            """.trimIndent(), null)
                                        } else {
                                            val targetY = (entry.scrollY - scrollOffset).coerceAtLeast(0)
                                            wv.evaluateJavascript("window.scrollTo(0, $targetY);", null)
                                        }
                                    }
                                    // Hide top/bottom bars after TOC entry selection (user can tap to show again)
                                    state.showTopBar.value = false
                                    state.showTocDrawer.value = false
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                            }
                        }
                    }
                }
            }
        }

        if ((state.showGuidePicker.value || hasNoBookmarks) && bookmarksState == null) {
            // Room flow (re)subscribed — data lands next frame. Painting the bare
            // background for that one frame beats flashing an empty picker.
            androidx.compose.foundation.layout.Box(
                Modifier
                    .fillMaxSize()
                    .background(screenPalette.background)
            )
        } else if (state.showGuidePicker.value || hasNoBookmarks) {
            GuidePickerContent(
                vaultId = vaultId,
                effectiveVault = effectiveVault,
                // Full list incl. LINK entries so saved links show in the picker.
                // navigableBookmarks (links filtered out) still drives L2/R2 +
                // arrow nav, which deliberately skip links.
                bookmarks = bookmarks,
                savedTabRepository = savedTabRepository,
                mainViewModel = mainViewModel,
                landingEntriesList = landingEntriesList,
                recentSearchesForLanding = recentSearchesForLanding,
                currentSearchEngine = currentSearchEngine,
                buildSearchUrlForEngine = { buildSearchUrlForEngine(currentSearchEngine, it) },
                toastMessage = state.toastMessage,
                activePdfGuide = state.activePdfGuide,
                currentBookmark = state.currentBookmark,
                showGuidePicker = state.showGuidePicker,
                coroutineScope = coroutineScope,
                updateInteraction = { updateInteraction() },
                onOpenBookmark = { bookmark ->
                    // pdf doesn't need a WebView; html/txt do. LINK entries point
                    // at external URLs — open them in the Browser tab.
                    val ft = bookmark.fileType.lowercase()
                    when (ft) {
                        "link" -> {
                            // Switch to the Browser tab and open the saved URL there
                            // (same path as the picker's "Browse Guide" menu). Leave
                            // the picker mounted (showGuidePicker stays true) so the
                            // Guide tab stays on the picker — NOT a single-page guide
                            // viewer — when the user returns to it. Closing it here let
                            // the active-doc restore mount the link as a guide.
                            mainViewModel.setPendingUrlForBrowserTab(vaultId, bookmark.url)
                            mainViewModel.setVaultSessionTab(vaultId, com.twinlore.app.domain.VaultTab.Browser)
                        }
                        "mht", "html", "txt" -> {
                            // Route MHT/HTML/TXT through the new single-guide
                            // viewer. GuideTabContent's
                            // dispatch picks up the openSingleGuideByVault
                            // state and mounts SingleGuideViewerScreen.
                            mainViewModel.openSingleGuideForVault(vaultId, bookmark)
                            state.showGuidePicker.value = false
                        }
                        else -> {
                            // PDF + anything else: existing path (PDF mounts
                            // PdfGuideViewer via state.activePdfGuide).
                            val wv = state.webView.value
                            if (wv != null || bookmark.fileType == "pdf") {
                                loadGuideBookmark(wv, bookmark)
                            }
                            state.showGuidePicker.value = false
                        }
                    }
                    updateInteraction()
                },
                onRename = { bookmark, newTitle ->
                    coroutineScope.launch(Dispatchers.IO) {
                        val updated = bookmark.copy(title = newTitle)
                        savedTabRepository.updateTab(updated)
                    }
                    updateInteraction()
                },
                onDelete = { bookmark ->
                    val isCurrent = bookmark.id == state.currentBookmark.value?.id
                    val isActivePdf = bookmark.id == state.activePdfGuide.value?.id
                    val isActiveWheel = bookmark.id == state.activeWheelGuide.value?.id
                    val wasViewingOffline = (currentUrl ?: state.webView.value?.url ?: "").startsWith("file://") || (currentUrl ?: state.webView.value?.url ?: "").startsWith("content://")
                    // Stay in guide picker after deletion
                    if (isActivePdf) state.activePdfGuide.value = null
                    if (isActiveWheel) state.activeWheelGuide.value = null
                    if (isCurrent) state.currentBookmark.value = null
                    state.showGuidePicker.value = true
                    coroutineScope.launch(Dispatchers.IO) {
                        savedTabRepository.deleteBookmark(bookmark.id)
                        // Clean up the code-wheel render dir (harmless no-op for other types).
                        java.io.File(context.cacheDir, "code_wheel_render/${bookmark.id}").deleteRecursively()
                        if (isCurrent && wasViewingOffline) {
                            val landingPageUrl = getLandingPageUrl(context, vaultId, effectiveVaultArtworkPath, landingEntriesList, recentSearchesForLanding, paletteBgHex = screenBgHex, paletteFgHex = screenFgHex, gameName = effectiveVault?.displayName)
                            withContext(Dispatchers.Main) {
                                state.currentBookmark.value = null
                                val wv = state.webView.value
                                if (wv != null && landingPageUrl != null) {
                                    if (landingPageUrl.startsWith("file://")) {
                                        loadFileUrl(wv, landingPageUrl)
                                    } else {
                                        wv.loadUrl(landingPageUrl)
                                    }
                                    vm.setUrl(landingPageUrl)
                                    vm.setScrollPosition(0)
                                    vm.setMaxScrollY(0)
                                }
                            }
                        } else if (isCurrent) {
                            withContext(Dispatchers.Main) {
                                state.currentBookmark.value = null
                            }
                        }
                    }
                    updateInteraction()
                },
                onDismiss = {
                    state.showGuidePicker.value = false
                    updateInteraction()
                },
                contentFocusRequester = contentFocusRequester,
                isTabVisible = isTabVisible,
                bottomBarFocusRequester = bottomBarFocusRequester
            )
        }

        state.activePdfGuide.value?.let { pdfGuide ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(10f)
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                PdfGuideViewer(
                    guide = pdfGuide,
                    vaultId = vaultId,
                    mainViewModel = mainViewModel,
                    isTabVisible = isTabVisible,
                    positionSyncKey = positionSyncKey,
                    contentFocusRequester = contentFocusRequester,
                    bottomBarFocusRequester = bottomBarFocusRequester,
                    onShowGuidePicker = {
                        state.activePdfGuide.value = null
                        state.showGuidePicker.value = true
                        updateInteraction()
                    },
                    onOpenLinkInBrowser = { url ->
                        mainViewModel.setPendingUrlForBrowserTab(vaultId, url)
                        mainViewModel.setVaultSessionTab(vaultId, com.twinlore.app.domain.VaultTab.Browser)
                        updateInteraction()
                    },
                    onSearchOnline = { text ->
                        var query = text.trim()
                        if (state.appendGameName.value && effectiveVault?.displayName?.isNotBlank() == true &&
                            !query.contains(effectiveVault!!.displayName!!, ignoreCase = true)) {
                            query = "$query ${effectiveVault!!.displayName!!.trim()}".trim()
                        }
                        if (vaultId.isNotBlank() && query.isNotBlank()) {
                            searchHistoryManager.saveSearch(vaultId, query)
                        }
                        val searchUrl = buildSearchUrlForEngine(currentSearchEngine, query)
                        mainViewModel.setPendingUrlForBrowserTab(vaultId, searchUrl)
                        mainViewModel.setVaultSessionTab(vaultId, com.twinlore.app.domain.VaultTab.Browser)
                        updateInteraction()
                    },
                    onYouTube = { text ->
                        var query = text.trim()
                        if (state.appendGameName.value && effectiveVault?.displayName?.isNotBlank() == true &&
                            !query.contains(effectiveVault!!.displayName!!, ignoreCase = true)) {
                            query = "$query ${effectiveVault!!.displayName!!.trim()}".trim()
                        }
                        if (vaultId.isNotBlank() && query.isNotBlank()) {
                            searchHistoryManager.saveSearch(vaultId, query)
                        }
                        val youtubeUrl = "https://www.youtube.com/results?search_query=" + android.net.Uri.encode(query)
                        mainViewModel.setPendingUrlForBrowserTab(vaultId, youtubeUrl)
                        mainViewModel.setVaultSessionTab(vaultId, com.twinlore.app.domain.VaultTab.Browser)
                        updateInteraction()
                    }
                )
            }
        }

        state.activeWheelGuide.value?.let { wheelGuide ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(10f)
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                CodeWheelViewerScreen(
                    guide = wheelGuide,
                    vaultId = vaultId,
                    mainViewModel = mainViewModel,
                    isTabVisible = isTabVisible,
                    contentFocusRequester = contentFocusRequester,
                    bottomBarFocusRequester = bottomBarFocusRequester,
                    onShowGuidePicker = {
                        state.activeWheelGuide.value = null
                        state.showGuidePicker.value = true
                        updateInteraction()
                    },
                    onClose = {
                        state.activeWheelGuide.value = null
                        state.showGuidePicker.value = true
                        updateInteraction()
                    },
                )
            }
        }

        // Overlay prev/next arrows (touch-only, not focusable). Structurally
        // present when there are at least 2 navigable bookmarks and we're not
        // on the landing page; visibility within that gate fades with the
        // guide chrome (matches the top bar's predicate at the next block).
        val isArrowsLandingPage = currentUrl.orEmpty().contains("landing.html") ||
            state.webView.value?.url.orEmpty().contains("landing.html")
        if (!isArrowsLandingPage && navigableBookmarks.size >= 2) {
            val arrowsChromeVisible = state.showTopBar.value &&
                !state.showFindOnPage.value &&
                !state.showTocDrawer.value &&
                !state.showGuidePicker.value &&
                state.activePdfGuide.value == null
            val currentIndex = navigableBookmarks.indexOfFirst { it.id == state.currentBookmark.value?.id }
            val prevIndex = if (currentIndex > 0) currentIndex - 1 else navigableBookmarks.size - 1
            val nextIndex = if (currentIndex >= 0 && currentIndex < navigableBookmarks.size - 1) currentIndex + 1 else 0

            val goToBookmark: (com.twinlore.app.domain.SavedTab) -> Unit = { target ->
                val wv = state.webView.value
                val bookmarkWeAreLeaving = if (currentIndex >= 0) navigableBookmarks[currentIndex] else null
                if (wv != null || target.fileType == "pdf") {
                    if (bookmarkWeAreLeaving != null) {
                        coroutineScope.launch(Dispatchers.IO) {
                            val scrollData = vm.scrollRestoreData.value
                                ?: ScrollRestoreData.ratioOnly(0.0)
                            savedTabRepository.updateBookmarkScrollRestore(
                                vaultId, bookmarkWeAreLeaving.id, scrollData
                            )
                        }
                    }
                    loadGuideBookmark(wv ?: state.webView.value!!, target)
                }
                updateInteraction()
            }

            AnimatedVisibility(
                visible = arrowsChromeVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                Surface(
                    onClick = { goToBookmark(navigableBookmarks[prevIndex]) },
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(40.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.ChevronLeft,
                            contentDescription = "Previous guide"
                        )
                    }
                }
            }
            AnimatedVisibility(
                visible = arrowsChromeVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                Surface(
                    onClick = { goToBookmark(navigableBookmarks[nextIndex]) },
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(40.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "Next guide"
                        )
                    }
                }
            }
        }

        // MHT save: launches saveWebArchive and stores result as guide.mht
        fun startMhtSave() {
            val wv = state.webView.value ?: return
            state.isSavingBookmark.value = true
            state.saveBookmarkJob.value?.cancel()
            val pendingTitle = state.pendingSaveTitle.value
            val pendingUrl = state.pendingSaveUrl.value
            val job = coroutineScope.launch {
                val tempFile = java.io.File(context.cacheDir, "temp_mht_${System.currentTimeMillis()}.mht")
                try {
                    withContext(Dispatchers.Main) {
                        state.saveProgress.value = SaveProgress(currentAsset = "Saving web archive…")
                    }
                    // Stash iframe srcs into data-original-src, strip lazy on every <img>,
                    // sweep-scroll the page, and wait until every image reports .complete
                    // (capped by an 8s budget + 1s settle). See [MhtSaveHelpers].
                    withContext(Dispatchers.Main) {
                        com.twinlore.app.data.library.MhtSaveHelpers.preloadForArchive(wv)
                    }
                    val saved = withContext(Dispatchers.Main) {
                        kotlinx.coroutines.withTimeoutOrNull(30_000L) {
                            kotlinx.coroutines.suspendCancellableCoroutine<Boolean> { cont ->
                                // The WebView callback can still fire and write bytes after
                                // the continuation is cancelled — delete the temp on cancel
                                // so we don't leak it into cacheDir.
                                cont.invokeOnCancellation { runCatching { tempFile.delete() } }
                                wv.saveWebArchive(tempFile.absolutePath, false) { savedPath ->
                                    if (cont.isActive) cont.resumeWith(kotlin.Result.success(savedPath != null))
                                }
                            }
                        } ?: false
                    }
                    if (!saved || !tempFile.exists() || tempFile.length() == 0L) {
                        runCatching { tempFile.delete() }
                        withContext(Dispatchers.Main) {
                            state.isSavingBookmark.value = false
                            state.saveProgress.value = null
                            state.saveBookmarkJob.value = null
                            state.toastMessage.value = "Failed to save web archive"
                        }
                        return@launch
                    }

                    // Show file size while storing
                    val sizeKb = tempFile.length() / 1024
                    val sizeLabel = if (sizeKb >= 1024) "${sizeKb / 1024}MB" else "${sizeKb}KB"
                    withContext(Dispatchers.Main) {
                        state.saveProgress.value = SaveProgress(currentAsset = "Storing guide ($sizeLabel)…")
                    }
                    val fileName = (pendingTitle.ifBlank { "page" }) + ".mht"
                    val result = withContext(Dispatchers.IO) {
                        savedTabRepository.createMhtGuide(
                            vaultId = vaultId,
                            sourceFile = tempFile,
                            fileName = fileName,
                            url = pendingUrl
                        )
                    }
                    tempFile.delete()
                    result.onSuccess { bookmark ->
                        // TODO: warm the MHT cache via ResourceResolver if this ever needs to open instantly.
                        withContext(Dispatchers.Main) {
                            state.isSavingBookmark.value = false
                            state.saveProgress.value = null
                            state.saveBookmarkJob.value = null
                            state.toastMessage.value = "Guide saved: ${bookmark.title} ($sizeLabel)"
                            state.webView.value?.let { innerWv ->
                                loadGuideBookmark(innerWv, bookmark, restoreScroll = false)
                            }
                        }
                    }.onFailure { error ->
                        android.util.Log.e("GuideBrowser", "Failed to save MHT", error)
                        withContext(Dispatchers.Main) {
                            state.isSavingBookmark.value = false
                            state.saveProgress.value = null
                            state.saveBookmarkJob.value = null
                            state.toastMessage.value = "Failed to save guide"
                        }
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    runCatching { tempFile.delete() }
                    withContext(Dispatchers.Main) {
                        state.isSavingBookmark.value = false
                        state.saveProgress.value = null
                        state.saveBookmarkJob.value = null
                    }
                    throw e
                } catch (e: Exception) {
                    runCatching { tempFile.delete() }
                    android.util.Log.e("GuideBrowser", "Error saving MHT", e)
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

        // Top bar visibility: 5 s auto-hide timer + tap toggle + scroll-down hide. Always hidden while Find on Page or the Guide Picker is shown.
        AnimatedVisibility(
            visible = state.showTopBar.value && !state.showFindOnPage.value &&
                !state.showGuidePicker.value &&
                !state.showTocDrawer.value &&
                state.activePdfGuide.value == null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .onGloballyPositioned { coordinates ->
                    with(density) {
                        measuredTopBarHeightDp = coordinates.size.height.toDp()
                    }
                }
        ) {
            val focusManager = LocalFocusManager.current
            val context = LocalContext.current
            // Focus search bar trigger handled by controller action directly
            val searchHistoryManager = remember {
                EntryPoints.get(
                    context.applicationContext,
                    SearchHistoryManagerEntryPoint::class.java
                ).searchHistoryManager()
            }
            val effectiveUrlForSave = state.webView.value?.url ?: currentUrl ?: ""
            val canSaveAsBookmark = effectiveUrlForSave.isNotBlank() &&
                !effectiveUrlForSave.startsWith("file://") &&
                !effectiveUrlForSave.startsWith("content://")
            GuideBrowserTopBar(
                // Show currentUrl from view model if it's an HTTP/HTTPS URL (explicitly set),
                // otherwise show WebView's actual URL (for file:// URLs or when navigating)
                currentUrl = currentUrl?.takeIf { it.startsWith("http") } ?: (state.webView.value?.url ?: currentUrl ?: ""),
                urlInput = state.urlInput.value,
                onUrlInputChange = { state.urlInput.value = it },
                appendGameName = state.appendGameName.value,
                onAppendGameNameChange = { newValue ->
                    state.appendGameName.value = newValue
                    val gameName = effectiveVault?.displayName ?: "game name"
                    val msg = if (newValue) "\"$gameName\" will be added to your searches for game-specific results" else "Game name will no longer be added to searches"
                    android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                },
                onNavigate = { input, isCompleteQuery, finalQuery ->
                    val finalUrl = if (isValidUrl(input)) {
                        input
                    } else {
                        buildSearchUrlForEngine(currentSearchEngine, finalQuery)
                    }
                    
                    // Clear current bookmark when user navigates away
                    state.currentBookmark.value = null
                    
                    // Save search to history (only if it's not a URL)
                    if (!isValidUrl(input) && vaultId.isNotBlank() && finalQuery.isNotBlank()) {
                        searchHistoryManager.saveSearch(vaultId, finalQuery)
                    }
                    
                    state.webView.value?.loadUrl(finalUrl)
                    keyboardController?.hide()
                    state.isUrlFieldFocused.value = false
                    state.urlInput.value = ""
                    // Hide top bar and move focus away from search field.
                    // NavigableTextField re-requests focus after switching from EDIT→NAV mode,
                    // so we delay the focus move to run after that LaunchedEffect.
                    state.showTopBar.value = false
                    coroutineScope.launch {
                        delay(100)
                        focusManager.clearFocus(force = true)
                        // Move focus to WebView so it receives input
                        state.webView.value?.requestFocus()
                    }
                },
                canGoBack = false,
                canGoForward = false,
                isOnLandingPage = {
                    val webViewUrl = state.webView.value?.url
                    webViewUrl != null && webViewUrl.contains("landing.html")
                },
                onBack = {
                    // Navigate back to vault library
                    mainViewModel.navigateToLibrary(focusedVaultId = vaultId)
                    updateInteraction()
                },
                onForward = { 
                    state.webView.value?.goForward()
                    updateInteraction()
                },
                urlFieldFocusRequester = urlFieldFocusRequester,
                onUrlFieldFocused = {
                    state.urlInput.value = ""
                },
                onUrlFieldFocusChanged = { focused ->
                    state.isUrlFieldFocused.value = focused // Track focus state in parent
                    updateInteraction()
                },
                onFavoriteClick = {
                    keyboardController?.hide()
                    state.showGuidePicker.value = true
                    updateInteraction()
                },
                onAddNewGuide = {
                    keyboardController?.hide()
                    state.showGuidePicker.value = true
                    updateInteraction()
                },
                onFavoriteLongPress = {
                    // No long-press behavior for bookmark management
                    updateInteraction()
                },
                onSaveAsFavorite = {
                    if (state.isSavingBookmark.value) {
                        return@GuideBrowserTopBar
                    }
                    state.webView.value?.let { wv ->
                        try {
                            // Strip vault-injected enhancements fire-and-forget before saving,
                            // so the saved file is the clean original page. Both JS eval and
                            // saveWebArchive are queued on the same WebView thread, so cleanup
                            // runs before the archive is captured. Re-applied at view time.
                            wv.evaluateJavascript(
                                "(function(){" +
                                    "var i=document.querySelectorAll('[data-vault-injected]');" +
                                    "for(var j=0;j<i.length;j++)i[j].remove();" +
                                    "if(document.body)document.body.removeAttribute('data-vault-enhanced');" +
                                "})()",
                                null
                            )
                            // Always do offline save — no link/bookmark path in Guide.
                            // Stage pending title/url BEFORE either path so the
                            // "Save anyway" confirm dialog's startMhtSave() reads them.
                            state.pendingSaveTitle.value = ""
                            state.pendingSaveUrl.value = wv.url ?: ""
                            if (wv.progress < 100) {
                                state.showSaveWhileLoadingConfirm.value = true
                            } else {
                                startMhtSave()
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("GuideBrowser", "Error in save callback", e)
                            state.isSavingBookmark.value = false
                            state.toastMessage.value = "Failed to save"
                        }
                    } ?: run {
                        android.util.Log.w("GuideBrowser", "WebView is null, cannot save")
                        state.isSavingBookmark.value = false
                        state.toastMessage.value = "Failed to save: Page not loaded"
                    }
                    updateInteraction()
                },
                isCurrentPageFavorite = run {
                    val urlToCheck = currentUrl ?: state.webView.value?.url ?: ""
                    val viewingOffline = urlToCheck.startsWith("file://") || urlToCheck.startsWith("content://")
                    val starResult = bookmarks.any { it.url == urlToCheck } ||
                        (viewingOffline && state.currentBookmark.value != null && bookmarks.any { it.id == state.currentBookmark.value!!.id })
                    starResult
                },
                canSaveAsBookmark = canSaveAsBookmark,
                isSavingFavorite = state.isSavingBookmark.value,
                saveProgress = state.saveProgress.value,
                onFilledStarClick = {
                    val url = currentUrl ?: state.webView.value?.url ?: ""
                    val b = bookmarks.find { it.url == url } ?: state.currentBookmark.value
                    if (b != null) state.bookmarkToDeleteFromStar.value = b
                },
                onCancelSave = if (state.isSavingBookmark.value) {
                    {
                        state.saveBookmarkJob.value?.cancel()
                        state.saveBookmarkJob.value = null
                        state.saveProgress.value = null
                        state.isSavingBookmark.value = false
                        state.toastMessage.value = "Bookmark save cancelled"
                    }
                } else null,
                hasFavorite = bookmarks.isNotEmpty(), // Has any saved guides (bookmarks)
                gameName = effectiveVault?.displayName,
                vaultId = vaultId,
                onRefresh = {
                    state.webView.value?.reload()
                    updateInteraction()
                },
                zoomLevel = zoomLevel,
                minZoomLevel = minZoomLevel,
                onZoomLevelChange = { vm.setZoom(it); updateInteraction() },
                onResetZoom = {
                    vm.setZoom(1f)
                    // Also reset pinch zoom so the button always returns to a fully zoomed-out state
                    state.webView.value?.run {
                        @Suppress("DEPRECATION")
                        val currentScale = scale
                        if (currentScale > 0f && currentScale != 1f) zoomBy(1f / currentScale)
                    }
                    updateInteraction()
                },
                onFindOnPage = {
                    state.showFindOnPage.value = true
                    state.findQuery.value = ""
                    state.findOpenedWithSelection.value = false
                    state.findMatchesFound.value = null
                    updateInteraction()
                },
                onOpenToc = {
                    if (state.tocEntries.value.isNotEmpty() || state.textFaqTocActive.value) {
                        state.showTocDrawer.value = !state.showTocDrawer.value
                        if (state.showTocDrawer.value) state.showTopBar.value = false
                    } else {
                        state.webView.value?.let { wv ->
                            extractTocEntries(wv) { entries ->
                                if (entries.isNotEmpty()) {
                                    state.tocEntries.value = entries
                                    state.showTocDrawer.value = true
                                    state.showTopBar.value = false
                                }
                            }
                        }
                    }
                    updateInteraction()
                },
                currentBookmark = state.currentBookmark.value,
                onToastMessage = { message ->
                    state.toastMessage.value = message
                },
                scrollMarkers = state.scrollMarkers.value,
                focusedScrollMarkerIndex = state.focusedScrollMarkerIndex.value,
                onScrollMarkerTap = { marker ->
                    scrollToMarkerByText(state.webView.value, marker.label, marker.scrollY, marker.occurrenceIndex) { y ->
                        vm.setScrollPosition(y)
                        state.focusedScrollMarkerIndex.value = state.scrollMarkers.value.indexOfFirst { it.id == marker.id }.takeIf { it >= 0 }
                        highlightScrollMarkerInPage(state.webView.value, state.scrollMarkers.value, state.focusedScrollMarkerIndex.value, effectiveHighlightHexBrowser, effectiveContrastHexBrowser)
                        state.currentBookmark.value?.let { b ->
                            coroutineScope.launch { savedTabRepository.setFocusedScrollMarkerId(vaultId, b.id, marker.id) }
                        }
                        updateInteraction()
                    }
                },
                onScrollMarkerLongPress = { marker ->
                    state.scrollMarkerLongPressed.value = marker
                    state.showScrollMarkerDeleteAllMenu.value = true
                },
                scrollMarkersBarVisible = state.showScrollMarkersBar.value,
                onToggleScrollMarkersBar = { setScrollMarkersBarVisible(!state.showScrollMarkersBar.value) },
                vaultSessionMode = VaultSessionMode.Guide,
                // See shouldShowFoldButtonInHostTopBar's doc: suppress the fold button
                // here when VaultSessionTopBar already renders one, and step back in
                // for any reason that bar is not actually on screen (Hidden mode,
                // keyboard open, Achievements wallpaper, or per-tab auto-hide).
                showFoldButton = com.twinlore.app.ui.fold.shouldShowFoldButtonInHostTopBar(
                    isEmbeddedInVaultSession, vaultSessionTopBarVisible
                ),
                onBrowserBack = null,
                onBrowserForward = null,
                onGoToLandingPage = null,
                showOpenInBrowserButton = (state.currentBookmark.value?.fileType == "html" || state.currentBookmark.value?.fileType == "mht") && state.currentBookmark.value?.url?.startsWith("http") == true,
                guideThemeInjectionActive = guideThemeInject,
                onToggleGuideThemeInjection = {
                    val newState = !guideThemeInject
                    settingsViewModel.setGuideThemeInject(newState)
                    val msg = if (newState) "App theme active — change in Settings"
                              else "App theme disabled — change in Settings"
                    android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                    updateInteraction()
                },
                onOpenGuideInBrowser = run {
                    val bookmark = state.currentBookmark.value
                    if (bookmark?.url?.startsWith("http") == true && (bookmark.fileType == "html" || bookmark.fileType == "mht")) {
                        { onOpenLinkInBrowser?.invoke(bookmark.url) }
                    } else null
                },
                // When in viewer mode (not picker), contentFocusRequester targets the top bar's first button
                // so D-pad UP from bottom bar can reach the top bar.
                firstButtonFocusRequester = if (contentFocusRequester != null && !state.showGuidePicker.value && state.activeWheelGuide.value == null) contentFocusRequester else null,
                bottomBarFocusRequester = bottomBarFocusRequester,
                zoomControlsFocusRequester = zoomControlsFocusRequester,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
            )
        }
        
        // Horizontal progress bar below top bar — less obtrusive than the old centered overlay.
        // Hide for landing page and when Guide Picker is shown.
        val isLandingPageUrl = currentUrl.orEmpty().contains("landing.html") || state.webView.value?.url.orEmpty().contains("landing.html")
        val showGuidePicker = state.showGuidePicker.value
        if ((isLoading || state.isRestoringScroll.value) && !isLandingPageUrl && !showGuidePicker) {
            // Position below top bar when visible, at very top when hidden
            val topBarVisible = state.showTopBar.value && !state.showFindOnPage.value &&
                !state.showTocDrawer.value &&
                !state.showGuidePicker.value &&
                state.activePdfGuide.value == null
            val barTopOffset by androidx.compose.animation.core.animateDpAsState(
                targetValue = if (topBarVisible) measuredTopBarHeightDp else 0.dp,
                animationSpec = androidx.compose.animation.core.tween(durationMillis = 200),
                label = "progress_bar_offset"
            )
            LinearProgressIndicator(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .padding(top = barTopOffset)
                    .height(3.dp),
                color = focusColorBrowser,
                trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            )
        }

        // Full-screen loading overlay while guide page loads behind the scenes.
        // Artwork is resolved outside the if-block so the image is warm when the overlay appears.
        val guideLoadArtwork = remember(effectiveVaultArtworkPath, vaultId) { mutableStateOf<Any?>(null) }
        LaunchedEffect(effectiveVaultArtworkPath, vaultId) {
            guideLoadArtwork.value = if (effectiveVaultArtworkPath != null) {
                withContext(Dispatchers.IO) {
                    com.twinlore.app.ui.utils.ImagePathUtils.toImageData(effectiveVaultArtworkPath, vaultId)
                }
            } else null
        }
        // Safety timeout: if loading overlay stays visible for >15s, force-dismiss.
        // Prevents stuck blank screen if onScrollRestoreCompleted or text FAQ callbacks never fire.
        LaunchedEffect(state.isConvertingTextFaq.value) {
            if (state.isConvertingTextFaq.value) {
                kotlinx.coroutines.delay(15_000)
                if (state.isConvertingTextFaq.value) {
                    Log.w("GuideBrowser", "Safety timeout: forcing loading overlay dismiss after 15s")
                    state.isConvertingTextFaq.value = false
                    state.isRestoringScroll.value = false
                }
            }
        }
        if (state.isConvertingTextFaq.value) {
            val blurMod = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                Modifier.graphicsLayer {
                    renderEffect = android.graphics.RenderEffect
                        .createBlurEffect(200f, 200f, android.graphics.Shader.TileMode.CLAMP)
                        .asComposeRenderEffect()
                }
            } else Modifier.blur(radius = 16.dp)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center
            ) {
                guideLoadArtwork.value?.let { artData ->
                    val guideArtworkRid = effectiveVault?.artworkResourceId
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(artData)
                            .apply {
                                if (guideArtworkRid != null) {
                                    placeholderMemoryCacheKey("grid_art_${vaultId}_${guideArtworkRid}_256x341_0")
                                }
                            }
                            .crossfade(180)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().then(blurMod),
                        contentScale = ContentScale.Crop
                    )
                    // Semi-transparent scrim so spinner remains legible
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.5f))
                    )
                }
                com.twinlore.app.ui.components.LoadingWithTips(
                    focusColor = focusColorBrowser,
                    // Splitting a large text guide into pages takes long enough that a static
                    // "Loading" reads as a hang. Say why it is slow, then keep acknowledging it.
                    statusMessages = com.twinlore.app.ui.components.TEXT_GUIDE_CONVERSION_STATUS,
                )
            }
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

        // Bookmark Management Dialog
        if (state.showBookmarkManagementDialog.value) {
            BookmarkManagementDialog(
                bookmarks = state.bookmarksForDialog.value ?: emptyList(),
                bookmarksLoading = state.bookmarksForDialog.value == null,
                onBookmarkClick = { bookmark ->
                    val wv = state.webView.value
                    if (wv != null || bookmark.fileType == "pdf" || bookmark.fileType == "link") {
                        loadGuideBookmark(wv, bookmark)
                    }
                    state.showBookmarkManagementDialog.value = false
                    updateInteraction()
                },
                onBookmarkLoadOffline = { bookmark ->
                    val wv = state.webView.value
                    if (wv != null || bookmark.fileType == "pdf" || bookmark.fileType == "link") {
                        loadGuideBookmark(wv, bookmark)
                    }
                    state.showBookmarkManagementDialog.value = false
                    updateInteraction()
                },
                onBookmarkRename = { bookmark, newTitle ->
                    coroutineScope.launch(Dispatchers.IO) {
                        val updated = bookmark.copy(title = newTitle)
                        savedTabRepository.updateTab(updated)
                        val freshList = savedTabRepository.getTabsForVaultSync(vaultId)
                        withContext(Dispatchers.Main) {
                            state.bookmarksForDialog.value = freshList
                        }
                    }
                    updateInteraction()
                },
                onBookmarkDelete = { bookmark ->
                    val isCurrent = (bookmark.id == state.currentBookmark.value?.id)
                    val wasViewingOffline = run {
                        val url = currentUrl ?: state.webView.value?.url ?: ""
                        url.startsWith("file://") || url.startsWith("content://")
                    }
                    coroutineScope.launch(Dispatchers.IO) {
                        savedTabRepository.deleteBookmark(bookmark.id)
                        val freshList = savedTabRepository.getTabsForVaultSync(vaultId)
                        if (isCurrent && wasViewingOffline) {
                            val landingPageUrl = getLandingPageUrl(context, vaultId, effectiveVaultArtworkPath, landingEntriesList, recentSearchesForLanding, paletteBgHex = screenBgHex, paletteFgHex = screenFgHex, gameName = effectiveVault?.displayName)
                            withContext(Dispatchers.Main) {
                                state.bookmarksForDialog.value = freshList
                                state.currentBookmark.value = null
                                val wv = state.webView.value
                                if (wv != null && landingPageUrl != null) {
                                    if (landingPageUrl.startsWith("file://")) {
                                        loadFileUrl(wv, landingPageUrl)
                                    } else {
                                        wv.loadUrl(landingPageUrl)
                                    }
                                    vm.setUrl(landingPageUrl)
                                    vm.setScrollPosition(0)
                                    vm.setMaxScrollY(0)
                                }
                            }
                        } else if (isCurrent) {
                            withContext(Dispatchers.Main) {
                                state.bookmarksForDialog.value = freshList
                                state.currentBookmark.value = null
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                state.bookmarksForDialog.value = freshList
                            }
                        }
                    }
                    updateInteraction()
                },
                onDismiss = {
                    state.showBookmarkManagementDialog.value = false
                    updateInteraction()
                },
                vaultId = vaultId,
                onSearchOnline = {
                    state.showBookmarkManagementDialog.value = false
                    mainViewModel.setVaultSessionTab(vaultId, com.twinlore.app.domain.VaultTab.Browser)
                    updateInteraction()
                }
            )
        }

        // Save-in-progress overlay, toast, scroll marker menus (see GuideBrowserDialogs.kt)
        SaveProgressOverlay(
            visible = state.isSavingBookmark.value,
            progress = state.saveProgress.value,
            focusColor = focusColorBrowser,
            onCancel = {
                state.saveBookmarkJob.value?.cancel()
                state.saveBookmarkJob.value = null
                state.saveProgress.value = null
                state.isSavingBookmark.value = false
                state.toastMessage.value = "Bookmark save cancelled"
            },
            artworkPath = effectiveVaultArtworkPath,
            vaultId = vaultId,
        )
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
        // PdfDownloadOverlay removed — Guide tab doesn't download PDFs from the web
        ScrollMarkerContextMenu(
            visible = state.showScrollMarkerDeleteAllMenu.value,
            markerForDelete = state.scrollMarkerLongPressed.value,
            onDismiss = {
                state.showScrollMarkerDeleteAllMenu.value = false
                state.scrollMarkerLongPressed.value = null
            },
            onDeleteOne = { marker ->
                val bookmark = state.currentBookmark.value ?: return@ScrollMarkerContextMenu
                coroutineScope.launch {
                    savedTabRepository.deleteScrollMarker(vaultId, bookmark.id, marker.id)
                        .onSuccess {
                            clearScrollMarkerHighlight(state.webView.value)
                            state.scrollMarkers.value = savedTabRepository.getScrollMarkers(vaultId, bookmark.id)
                            state.focusedScrollMarkerIndex.value = null
                            state.toastMessage.value = "Marker deleted"
                        }
                        .onFailure { state.toastMessage.value = "Failed to delete marker" }
                }
            },
            onDeleteAll = {
                val bookmark = state.currentBookmark.value ?: return@ScrollMarkerContextMenu
                state.scrollMarkerDeleteAllBookmarkId.value = bookmark.id
                state.showScrollMarkerDeleteAllConfirm.value = true
                state.showScrollMarkerDeleteAllMenu.value = false
                state.scrollMarkerLongPressed.value = null
            }
        )
        DeleteAllScrollMarkersConfirmDialog(
            visible = state.showScrollMarkerDeleteAllConfirm.value,
            bookmarkId = state.scrollMarkerDeleteAllBookmarkId.value,
            onDismiss = {
                state.showScrollMarkerDeleteAllConfirm.value = false
                state.scrollMarkerDeleteAllBookmarkId.value = null
            },
            onConfirm = { bookmarkId ->
                coroutineScope.launch {
                    savedTabRepository.deleteAllScrollMarkers(vaultId, bookmarkId)
                        .onSuccess {
                            clearScrollMarkerHighlight(state.webView.value)
                            state.scrollMarkers.value = savedTabRepository.getScrollMarkers(vaultId, bookmarkId)
                            state.focusedScrollMarkerIndex.value = null
                            state.toastMessage.value = "Scroll markers cleared"
                        }
                        .onFailure { state.toastMessage.value = "Failed to clear markers" }
                }
            }
        )
        
        // Delete bookmark from star (searchbar) confirmation — same dialog as bookmark menu delete
        state.bookmarkToDeleteFromStar.value?.let { bookmark ->
            DeleteTabDialog(
                tab = bookmark,
                onDismiss = { state.bookmarkToDeleteFromStar.value = null },
                onConfirm = {
                    val isCurrent = (bookmark.id == state.currentBookmark.value?.id)
                    coroutineScope.launch(Dispatchers.IO) {
                        savedTabRepository.deleteBookmark(bookmark.id)
                        withContext(Dispatchers.Main) {
                            if (isCurrent) state.currentBookmark.value = null
                            state.bookmarkToDeleteFromStar.value = null
                        }
                    }
                }
            )
        }
        
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

        // Find on Page UI
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
                        val jsonResult = result?.removeSurrounding("\"")?.replace("\\\"", "\"")
                        try {
                            val json = org.json.JSONObject(jsonResult ?: "{}")
                            state.findMatchCount.value = json.getInt("count")
                            state.findCurrentIndex.value = json.getInt("current")
                        } catch (e: Exception) { }
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
                        val jsonResult = result?.removeSurrounding("\"")?.replace("\\\"", "\"")
                        try {
                            val json = org.json.JSONObject(jsonResult ?: "{}")
                            state.findMatchCount.value = json.getInt("count")
                            state.findCurrentIndex.value = json.getInt("current")
                        } catch (e: Exception) { }
                    }
                }
                updateInteraction()
            }
            // Scroll markers always use 1-based DOM occurrence index (same as scrollToMarkerByText and hold-to-save).
            val doSaveAsScrollMarker = {
                val u = currentUrl ?: state.webView.value?.url ?: ""
                // Include state.currentBookmark.value fallback so save-marker works immediately after saving as bookmark (before Flow emits).
                val bookmark = bookmarks.find { b ->
                    u == b.url || (u.startsWith("file://") && b.id != null && u.contains(b.id))
                } ?: state.currentBookmark.value?.takeIf { b ->
                    u == b.url || (u.startsWith("file://") && b.id != null && u.contains(b.id))
                }
                val wasMarkersEmpty = state.scrollMarkers.value.isEmpty()
                if (bookmark != null) {
                    val label = state.findQuery.value.ifBlank { "Scroll position" }
                    val saveQuery = state.findQuery.value.trim().take(80)
                        .replace("\\", "\\\\")
                        .replace("'", "\\'")
                        .replace("\r", "")
                        .replace("\n", " ")
                    state.webView.value?.evaluateJavascript("""
                        (function() {
                            var occurrenceIndex = 1;
                            var scrollY = Math.round(Math.max(0, Math.min((window.pageYOffset || 0), document.documentElement.scrollHeight - window.innerHeight)));
                            if (window.findRanges && window.findCurrentIdx >= 0 && window.findCurrentIdx < window.findRanges.length) {
                                occurrenceIndex = window.findCurrentIdx + 1;
                                var rect = window.findRanges[window.findCurrentIdx].getBoundingClientRect();
                                var targetY = window.pageYOffset + rect.top - (window.innerHeight / 2) + (rect.height / 2);
                                var maxScroll = Math.max(0, document.documentElement.scrollHeight - window.innerHeight);
                                scrollY = Math.round(Math.max(0, Math.min(targetY, maxScroll)));
                            }
                            return JSON.stringify({ scrollY: scrollY, occurrenceIndex: occurrenceIndex });
                        })();
                    """.trimIndent()) { result ->
                        var scrollY = vm.scrollPosition.value
                        var occurrenceIndex = 1
                        try {
                            val json = org.json.JSONObject(result?.removeSurrounding("\"")?.replace("\\\"", "\"") ?: "{}")
                            scrollY = json.optInt("scrollY", scrollY)
                            occurrenceIndex = json.optInt("occurrenceIndex", 1).coerceAtLeast(0)
                        } catch (_: Exception) {}
                        coroutineScope.launch {
                            savedTabRepository.addScrollMarker(vaultId, bookmark.id, label, scrollY, occurrenceIndex)
                                .onSuccess { newMarker ->
                                    val updated = savedTabRepository.getScrollMarkers(vaultId, bookmark.id)
                                    withContext(Dispatchers.Main) {
                                        state.scrollMarkers.value = updated.toList()
                                        val newFocused = updated.indexOfFirst { it.id == newMarker.id }.takeIf { it >= 0 } ?: (updated.size - 1).coerceAtLeast(0)
                                        state.focusedScrollMarkerIndex.value = newFocused
                                        state.toastMessage.value = "Scroll marker saved: ${newMarker.label.take(30)}${if (newMarker.label.length > 30) "…" else ""}"
                                        state.showFindOnPage.value = false
                                        state.findMatchesFound.value = null
                                        state.findMatchCount.value = 0
                                        state.findCurrentIndex.value = 0
                                        state.webView.value?.evaluateJavascript("(function(){ var se = document.getElementById('find-highlight-style'); if(se) se.remove(); if(typeof CSS !== 'undefined' && CSS.highlights) { CSS.highlights.delete('find-all'); CSS.highlights.delete('find-current'); } window.findRanges = []; window.findCurrentIdx = -1; })();", null)
                                        highlightScrollMarkerInPage(state.webView.value, updated, newFocused, effectiveHighlightHexBrowser, effectiveContrastHexBrowser)
                                        if (wasMarkersEmpty) {
                                            setScrollMarkersBarVisible(true)
                                            state.showTopBar.value = true
                                            updateInteraction()
                                        }
                                    }
                                }
                                .onFailure { e ->
                                    state.toastMessage.value = e.message ?: "Failed to save marker"
                                }
                        }
                    } ?: run {
                        val scrollY = vm.scrollPosition.value
                        coroutineScope.launch {
                            savedTabRepository.addScrollMarker(vaultId, bookmark.id, label, scrollY, 1)
                                .onSuccess { newMarker ->
                                    val updated = savedTabRepository.getScrollMarkers(vaultId, bookmark.id)
                                    withContext(Dispatchers.Main) {
                                        state.scrollMarkers.value = updated.toList()
                                        val newFocused = updated.indexOfFirst { it.id == newMarker.id }.takeIf { it >= 0 } ?: (updated.size - 1).coerceAtLeast(0)
                                        state.focusedScrollMarkerIndex.value = newFocused
                                        state.toastMessage.value = "Scroll marker saved: ${newMarker.label.take(30)}${if (newMarker.label.length > 30) "…" else ""}"
                                        state.showFindOnPage.value = false
                                        state.findMatchesFound.value = null
                                        state.findMatchCount.value = 0
                                        state.findCurrentIndex.value = 0
                                        state.webView.value?.evaluateJavascript("(function(){ var se = document.getElementById('find-highlight-style'); if(se) se.remove(); if(typeof CSS !== 'undefined' && CSS.highlights) { CSS.highlights.delete('find-all'); CSS.highlights.delete('find-current'); } window.findRanges = []; window.findCurrentIdx = -1; })();", null)
                                        highlightScrollMarkerInPage(state.webView.value, updated, newFocused, effectiveHighlightHexBrowser, effectiveContrastHexBrowser)
                                        if (wasMarkersEmpty) {
                                            setScrollMarkersBarVisible(true)
                                            state.showTopBar.value = true
                                            updateInteraction()
                                        }
                                    }
                                }
                                .onFailure { e ->
                                    state.toastMessage.value = e.message ?: "Failed to save marker"
                                }
                        }
                    }
                } else {
                    state.toastMessage.value = "Open a bookmark to save scroll markers"
                }
                updateInteraction()
            }
            LaunchedEffect(Unit) {
                mainViewModel.setFindOnPageActions(doFindNext, doFindPrevious, doSaveAsScrollMarker)
            }
            BackHandler(enabled = isTabVisible) {
                state.findIsSearching.value = false
                state.showFindOnPage.value = false
                state.findQuery.value = ""
                state.findOpenedWithSelection.value = false
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
                        var isGameFaqs = !!(document.body && document.body.getAttribute('data-gamefaqs-converted'));
                        var walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT, null, false);
                        var tn;
                        var ranges = [];
                        while (tn = walker.nextNode()) {
                            if (!isGameFaqs && !isTextNodeVisible(tn)) continue;
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
            LaunchedEffect(state.findOpenedWithSelection.value, state.findQuery.value) {
                if (state.findOpenedWithSelection.value && state.findQuery.value.isNotBlank()) {
                    state.findOpenedWithSelection.value = false
                    runFindSearch()
                }
            }
            // Dismiss find-on-page when touching elsewhere
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
                        state.findOpenedWithSelection.value = false
                        state.findMatchesFound.value = null
                        state.findMatchCount.value = 0
                        state.findCurrentIndex.value = 0
                        state.webView.value?.evaluateJavascript("(function(){ var se = document.getElementById('find-highlight-style'); if(se) se.remove(); if(typeof CSS !== 'undefined' && CSS.highlights) { CSS.highlights.delete('find-all'); CSS.highlights.delete('find-current'); } window.findRanges = []; window.findCurrentIdx = -1; })();", null)
                        updateInteraction()
                    }
            )
            FindOnPageUI(
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
                onSearch = {
                    runFindSearch()
                },
                onClose = {
                    state.findIsSearching.value = false
                    state.showFindOnPage.value = false
                    state.findQuery.value = ""
                    state.findOpenedWithSelection.value = false
                    state.findMatchesFound.value = null
                    state.findMatchCount.value = 0
                    state.findCurrentIndex.value = 0
                    state.webView.value?.evaluateJavascript("""
                        (function() {
                            var se = document.getElementById('find-highlight-style');
                            if (se) se.remove();
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
                onSaveAsScrollMarker = doSaveAsScrollMarker,
                onNext = doFindNext,
                onPrevious = doFindPrevious,
                matchesFound = state.findMatchesFound.value,
                matchCount = state.findMatchCount.value,
                currentIndex = state.findCurrentIndex.value,
                searchTrigger = state.findSearchTrigger.value,
                isSearching = state.findIsSearching.value,
                requestFocusOnOpen = !state.findOpenedWithSelection.value,
                onImeVisibilityChange = { mainViewModel.setFindOnPageImeVisible(it) },
                recentSearches = true,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
        
        // Guide image zoom viewer (tap on inlined <img>).
        state.guideImageZoomModel.value?.let { model ->
            GuideImageZoomViewer(
                model = model,
                onDismiss = {
                    state.guideImageZoomModel.value = null
                    updateInteraction()
                }
            )
        }

        // Image context menu (long-press on image)
        val currentVault = effectiveVault
        if (state.showImageContextMenu.value && state.selectedImageUrl.value != null && currentVault != null) {
            BrowserImageContextMenu(
                imageUrl = state.selectedImageUrl.value!!,
                pageUrl = state.webView.value?.url,
                onAssignAsBoxArt = {
                    val pageUrl = state.selectedImageUrl.value!!
                    val v = currentVault!!
                    state.showImageContextMenu.value = false
                    state.isAssigningImage.value = true
                    state.assigningImageLabel.value = "Assigning as box art…"
                    resolveBestImageUrlForDownload(state.webView.value, pageUrl) { urlToUse ->
                        coroutineScope.launch(Dispatchers.IO) {
                            try {
                                val url = URL(urlToUse)
                                val connection = url.openConnection() as? java.net.HttpURLConnection ?: url.openConnection()
                                connection.connectTimeout = 10000
                                connection.readTimeout = 10000
                                if (connection is java.net.HttpURLConnection) {
                                    connection.setRequestProperty("User-Agent", DESKTOP_USER_AGENT)
                                    connection.setRequestProperty("Accept", "image/webp,image/apng,image/*,*/*;q=0.8")
                                }
                                connection.connect()
                                val contentType = connection.contentType?.lowercase() ?: ""
                                val path = url.path?.lowercase() ?: ""
                                val ext = when {
                                    "png" in contentType || path.endsWith(".png") -> ".png"
                                    "webp" in contentType || path.endsWith(".webp") -> ".webp"
                                    "gif" in contentType || path.endsWith(".gif") -> ".gif"
                                    "bmp" in contentType || path.endsWith(".bmp") -> ".bmp"
                                    else -> ".jpg"
                                }
                                val inputStream = connection.getInputStream()
                                val tempFile = File.createTempFile("boxart_${v.id}_", ext)
                            java.io.FileOutputStream(tempFile).use { output ->
                                inputStream.use { input ->
                                    input.copyTo(output)
                                }
                            }
                            
                            if (tempFile.exists()) {
                                val vaultIdToUpdate = v.id
                                tempFile.delete() // BrowserSaveCoordinator re-downloads via OkHttp; cleanup probe file.
                                when (val sr = browserSaveCoordinator.saveImage(urlToUse, com.twinlore.app.domain.ResourceKind.ARTWORK.raw, vaultIdToUpdate, referer = null)) {
                                    is com.twinlore.app.data.library.SaveResult.Success -> {
                                        guideViewModel.updateArtworkResourceId(vaultIdToUpdate, sr.resourceFileId)
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
                                        withContext(Dispatchers.Main) {
                                            state.isAssigningImage.value = false
                                            state.assigningImageLabel.value = ""
                                            state.selectedImageUrl.value = null
                                            android.widget.Toast.makeText(context, "Error: ${sr.cause.message}", android.widget.Toast.LENGTH_SHORT).show()
                                            updateInteraction()
                                        }
                                    }
                                }
                            } else {
                                withContext(Dispatchers.Main) {
                                    state.isAssigningImage.value = false
                                    state.assigningImageLabel.value = ""
                                    state.selectedImageUrl.value = null
                                    android.widget.Toast.makeText(
                                        context,
                                        "Failed to download image",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                    updateInteraction()
                                }
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("GuideBrowser", "Error assigning box art", e)
                            withContext(Dispatchers.Main) {
                                state.isAssigningImage.value = false
                                state.assigningImageLabel.value = ""
                                state.selectedImageUrl.value = null
                                android.widget.Toast.makeText(
                                    context,
                                    "Error: ${e.message}",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                                updateInteraction()
                            }
                        }
                    }
                    }
                },
                onAssignAsMap = {
                    val pageUrl = state.selectedImageUrl.value!!
                    val v = currentVault!!
                    state.showImageContextMenu.value = false
                    state.isAssigningImage.value = true
                    state.assigningImageLabel.value = "Saving to Image tab…"
                    resolveBestImageUrlForDownload(state.webView.value, pageUrl) { urlToUse ->
                        coroutineScope.launch(Dispatchers.IO) {
                            try {
                                val url = URL(urlToUse)
                                val connection = url.openConnection() as? java.net.HttpURLConnection ?: url.openConnection()
                                connection.connectTimeout = 10000
                                connection.readTimeout = 10000
                                if (connection is java.net.HttpURLConnection) {
                                    connection.setRequestProperty("User-Agent", DESKTOP_USER_AGENT)
                                    connection.setRequestProperty("Accept", "image/webp,image/apng,image/*,*/*;q=0.8")
                                }
                                connection.connect()
                                val contentType = connection.contentType?.lowercase() ?: ""
                                val path = url.path?.lowercase() ?: ""
                                val ext = when {
                                    "png" in contentType || path.endsWith(".png") -> ".png"
                                    "webp" in contentType || path.endsWith(".webp") -> ".webp"
                                    "gif" in contentType || path.endsWith(".gif") -> ".gif"
                                    "bmp" in contentType || path.endsWith(".bmp") -> ".bmp"
                                    else -> ".jpg"
                                }
                                val inputStream = connection.getInputStream()
                                val tempFile = File.createTempFile("map_${v.id}_", ext)
                                java.io.FileOutputStream(tempFile).use { output ->
                                    inputStream.use { input -> input.copyTo(output) }
                                }
                                if (tempFile.exists()) {
                                    val vaultIdToUpdate = v.id
                                    tempFile.delete() // BrowserSaveCoordinator re-downloads via OkHttp; cleanup probe file.
                                    when (val sr = browserSaveCoordinator.saveImage(urlToUse, com.twinlore.app.domain.ResourceKind.MAP.raw, vaultIdToUpdate, referer = null)) {
                                        is com.twinlore.app.data.library.SaveResult.Success -> {
                                            val pageTitle = withContext(Dispatchers.Main) { state.webView.value?.title }
                                            val mapName = pageTitle?.take(60)?.trim()?.ifBlank { null }
                                                ?: urlToUse.substringAfterLast('/').substringBeforeLast('.').replace(Regex("[_\\-]+"), " ").trim().ifBlank { null }
                                                ?: "Map"
                                            mapViewModel.addMap(vaultIdToUpdate, mapName, sr.resourceFileId)
                                            guideViewModel.updateMapResourceId(vaultId = vaultIdToUpdate, resourceId = sr.resourceFileId)
                                            mapViewModel.notifyMapReplaced(vaultIdToUpdate)
                                            withContext(Dispatchers.Main) {
                                                state.isAssigningImage.value = false
                                                state.assigningImageLabel.value = ""
                                                state.selectedImageUrl.value = null
                                                android.widget.Toast.makeText(context, "Image assigned", android.widget.Toast.LENGTH_SHORT).show()
                                                mainViewModel.setVaultSessionTab(vaultId, com.twinlore.app.domain.VaultTab.Map)
                                                updateInteraction()
                                            }
                                        }
                                        is com.twinlore.app.data.library.SaveResult.Failed -> {
                                            withContext(Dispatchers.Main) {
                                                state.isAssigningImage.value = false
                                                state.assigningImageLabel.value = ""
                                                state.selectedImageUrl.value = null
                                                android.widget.Toast.makeText(context, "Error: ${sr.cause.message}", android.widget.Toast.LENGTH_SHORT).show()
                                                updateInteraction()
                                            }
                                        }
                                    }
                                } else {
                                    withContext(Dispatchers.Main) {
                                        state.isAssigningImage.value = false
                                        state.assigningImageLabel.value = ""
                                        state.selectedImageUrl.value = null
                                        android.widget.Toast.makeText(context, "Failed to download image", android.widget.Toast.LENGTH_SHORT).show()
                                        updateInteraction()
                                    }
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("GuideBrowser", "Error assigning map", e)
                                withContext(Dispatchers.Main) {
                                    state.isAssigningImage.value = false
                                    state.assigningImageLabel.value = ""
                                    state.selectedImageUrl.value = null
                                    android.widget.Toast.makeText(context, "Error: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                                    updateInteraction()
                                }
                            }
                        }
                    }
                },
                onDismiss = {
                    state.showImageContextMenu.value = false
                    state.selectedImageUrl.value = null
                    updateInteraction()
                }
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
        
    }
    
    // Guide tab uses bookmark-based loading only — no URL-driven auto-load.

    // Save last page state (and per-bookmark zoom) when leaving
    DisposableEffect(vaultId) {
        onDispose {
            vm.saveState(vaultId)
            // Also persist current zoom to the active bookmark so it's restored per-guide
            val bookmark = state.currentBookmark.value
            val zoom = vm.zoomLevel.value
            val scrollData = vm.scrollRestoreData.value
            if (bookmark != null && scrollData != null) {
                kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                    savedTabRepository.updateBookmarkScrollRestore(vaultId, bookmark.id, scrollData, updatedZoomLevel = zoom)
                }
            }
        }
    }
}

/** Desktop Chrome UA used only for image/image-result URLs so CDNs accept the request. */
private const val DESKTOP_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"


