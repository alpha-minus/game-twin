package com.twinlore.app.ui.screens.pdf

import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import com.twinlore.app.ui.LocalThemePalette
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import com.twinlore.app.ui.navigation.NavigableButton
import com.twinlore.app.ui.navigation.NavigableTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.KeyboardType
import com.twinlore.app.ui.utils.FeedbackEvent
import com.twinlore.app.ui.utils.FeedbackManager
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.twinlore.app.data.FocusColor
import com.twinlore.app.ui.LocalCachedFocusColor
import com.twinlore.app.domain.PdfPageBookmark
import com.twinlore.app.domain.SavedTab
import com.twinlore.app.ui.MainViewModel
import com.twinlore.app.ui.screens.FindOnPageUI
import com.twinlore.app.ui.screens.SettingsViewModel
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun PdfGuideViewer(
    guide: SavedTab,
    @Suppress("UNUSED_PARAMETER") vaultId: String,
    onShowGuidePicker: () -> Unit,
    onOpenLinkInBrowser: (String) -> Unit,
    onSearchOnline: (String) -> Unit,
    onYouTube: (String) -> Unit,
    mainViewModel: MainViewModel? = null,
    /**
     * Reports whether a field wants the keyboard. A host whose window refuses
     * focus, like the fold panel's, has to take it for the duration and give
     * it back after: a search cannot take a character otherwise, and a game
     * underneath cannot take a button press while it is held.
     */
    onWantsKeyboard: (Boolean) -> Unit = {},
    /** What the hosting window can put on screen. See [GuideViewerCapabilities]. */
    capabilities: com.twinlore.app.ui.screens.guide.GuideViewerCapabilities =
        com.twinlore.app.ui.screens.guide.GuideViewerCapabilities.ACTIVITY,
    /**
     * A tap toggled this viewer's own chrome, and the host may have chrome of
     * its own to hide with it. The fold panel does; the vault session syncs its
     * bottom bar through mainViewModel instead and passes nothing.
     */
    onChromeToggle: (Boolean) -> Unit = {},
    isTabVisible: Boolean = true,
    /**
     * Moves each time the app comes back from Always on Top, and this viewer
     * re-reads the guide's page on each move: the panel hosted its own copy
     * over the same row and wrote the page it was left on. Zero, the default,
     * never moves, which is every host but the vault session.
     */
    positionSyncKey: Int = 0,
    contentFocusRequester: androidx.compose.ui.focus.FocusRequester? = null,
    bottomBarFocusRequester: androidx.compose.ui.focus.FocusRequester? = null,
    modifier: Modifier = Modifier,
    vm: PdfGuideViewModel = hiltViewModel()
) {
    // Not a ViewModel: this viewer is also hosted in the fold panel's overlay
    // window, where there is no activity for hiltViewModel() to scope one to.
    val uiSettings = com.twinlore.app.ui.rememberAppUiSettings()
    val focusColorState by uiSettings.focusColor.collectAsState(initial = FocusColor.CRIMSON)
    val cachedFocusColor = LocalCachedFocusColor.current?.value
    val focusColor = cachedFocusColor ?: focusColorState.color
    val palette = LocalThemePalette.current
    val view = LocalView.current
    val surfaceBg = MaterialTheme.colorScheme.surface
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface

    val currentPage by vm.currentPage.collectAsState()
    val totalPages by vm.totalPages.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    val documentLoaded by vm.documentLoaded.collectAsState()

    // Dismiss the GuideTabContent "Loading…" overlay set by openPdfForVault as
    // soon as this viewer is composed. PdfGuideViewer's own native progress
    // takes over from here; the overlay was just to cover the empty Guide tab
    // between the eager tab switch and our first frame.
    LaunchedEffect(Unit) {
        mainViewModel?.notifyPdfReady(vaultId)
    }
    val findCurrentMatch by vm.findCurrentMatch.collectAsState()
    val findTotalMatches by vm.findTotalMatches.collectAsState()
    val pageBookmarks by vm.pageBookmarks.collectAsState()
    val outline by vm.outline.collectAsState()

    // One PdfWebView per mount of PdfGuideViewer. guide.id is stable during a mount
    // (switching guides goes through the picker, which unmounts PdfGuideViewer entirely),
    // so there is no need to re-create the WebView per guide.
    val webViewHolder = remember { mutableStateOf<PdfWebView?>(null) }
    var showFind by remember { mutableStateOf(false) }
    LaunchedEffect(showFind) { onWantsKeyboard(showFind) }
    DisposableEffect(Unit) { onDispose { onWantsKeyboard(false) } }
    var findQuery by remember { mutableStateOf("") }
    var showGoToPage by remember { mutableStateOf(false) }
    var goToPageInput by remember { mutableStateOf("") }
    var showAddBookmark by remember { mutableStateOf(false) }
    var bookmarkNameInput by remember { mutableStateOf("") }
    var bookmarkToDelete by remember { mutableStateOf<PdfPageBookmark?>(null) }
    var showTocView by remember { mutableStateOf(false) }
    // Last-selected ToC overlay tab (0 = Contents, 1 = Bookmarks). Hoisted
    // here so the user's choice survives closing and reopening the overlay
    // within the same PDF session. Mirrors the offline guide viewers.
    var pdfTocTabIndex by remember { mutableStateOf(0) }
    // PDF view mode: 0 = single, 1 = spread, 2 = grid
    var viewMode by remember { mutableStateOf(0) }
    val hasExtractableText by vm.hasExtractableText.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    var lastToastedQuery by remember { mutableStateOf("") }

    // Auto-hide top bar state
    var showTopBar by remember { mutableStateOf(true) }
    var lastInteractionTime by remember { mutableStateOf(System.currentTimeMillis()) }

    fun updateInteraction() {
        lastInteractionTime = System.currentTimeMillis()
        showTopBar = true
    }

    // Auto-hide after 5 seconds of inactivity
    LaunchedEffect(showTopBar, lastInteractionTime) {
        if (showTopBar) {
            kotlinx.coroutines.delay(5000)
            if (System.currentTimeMillis() - lastInteractionTime >= 5000) {
                showTopBar = false
                // Reported as well as applied: a host hiding its own chrome
                // alongside this one would otherwise fall out of phase after
                // an auto-hide and invert on the next tap.
                onChromeToggle(false)
            }
        }
    }

    // Reset auto-hide timer when user interacts with top or bottom menu (e.g. bottom bar)
    LaunchedEffect(isTabVisible, mainViewModel) {
        if (mainViewModel == null || !isTabVisible) return@LaunchedEffect
        mainViewModel.chromeInteractionTrigger.collect {
            updateInteraction()
        }
    }

    // Sync bottom bar with top bar in vault session (same as Map tab)
    LaunchedEffect(showTopBar, isTabVisible, mainViewModel) {
        if (mainViewModel != null && isTabVisible) {
            mainViewModel.setVaultSessionGuideChromeVisible(showTopBar)
        }
    }

    // Restore WebView rendering after returning from another tab (prevents black screen)
    LaunchedEffect(isTabVisible) {
        if (isTabVisible) {
            webViewHolder.value?.let { wv ->
                wv.onResume()
                wv.invalidate()
            }
        } else {
            webViewHolder.value?.onPause()
        }
    }

    // Algorithmic darkening is latched at factory time (PdfWebView init); track
    // the palette live so a theme switch doesn't leave the renderer darkening a
    // light palette (dark-stuck viewer until recreated). Keyed on isTabVisible
    // so a switch made while the tab is hidden re-applies on return.
    LaunchedEffect(webViewHolder.value, palette, isTabVisible) {
        if (!isTabVisible) return@LaunchedEffect
        val wv = webViewHolder.value ?: return@LaunchedEffect
        if (androidx.webkit.WebViewFeature.isFeatureSupported(androidx.webkit.WebViewFeature.ALGORITHMIC_DARKENING)) {
            androidx.webkit.WebSettingsCompat.setAlgorithmicDarkeningAllowed(wv.settings, palette.isDark)
        }
    }

    // Single load sequence per guide. Keyed on guide.id (stable String) so it runs exactly
    // once per mount — previously this was keyed on `guide` + `webViewHolder.value`, which
    // restarted under some recomposition paths and caused a second loadUrl(viewer.html)
    // to race pdf.js's initialization, leaving the viewer permanently stuck.
    LaunchedEffect(guide.id) {
        vm.setGuide(guide)
        // Wait for AndroidView's factory to attach the WebView. snapshotFlow observes
        // the MutableState and suspends until it becomes non-null exactly once.
        val wv = androidx.compose.runtime.snapshotFlow { webViewHolder.value }
            .filterNotNull()
            .first()
        val data = vm.loadPdfFilePath(guide) ?: return@LaunchedEffect
        wv.loadPdf(data.first, data.second)
    }

    DisposableEffect(guide.id) {
        onDispose {
            vm.savePdfState(guide.id)
        }
    }

    // Back from Always on Top. The panel hosted its own copy of this viewer
    // over the same row and wrote the page it was left on; this one, still
    // composed behind it, is on the page it was left on. Read the row again
    // and go there. pdf.js reports the move back through onPageChanged, so
    // the view model's page follows without being set here.
    LaunchedEffect(positionSyncKey) {
        if (positionSyncKey == 0) return@LaunchedEffect
        val stored = vm.storedPage() ?: return@LaunchedEffect
        if (stored <= 0 || stored == vm.currentPage.value) return@LaunchedEffect
        webViewHolder.value?.goToPage(stored)
    }

    // Controller: D-pad/stick scroll, pan, and zoom (right stick)
    if (mainViewModel != null) {
        val browserScroll by mainViewModel.browserScroll.collectAsState()
        val browserPanX by mainViewModel.browserPanX.collectAsState()
        val browserZoom by mainViewModel.browserZoom.collectAsState()
        val deadzone = 0.1f
        // Remap stick value: scale out the deadzone so velocity starts at 0 (not a
        // sudden jump), then apply a quadratic curve for finer low-speed control.
        fun remapStick(raw: Float): Float {
            val abs = kotlin.math.abs(raw)
            if (abs <= deadzone) return 0f
            val norm = (abs - deadzone) / (1f - deadzone)
            return kotlin.math.sign(raw) * norm * norm
        }
        // Push stick velocity (px/sec) to the WebView only when it changes. The actual scroll
        // is driven by Choreographer frame callbacks that integrate velocity into synthetic
        // touch events for compositor-thread scrolling.
        val maxStickVelocity = 1500f // px/sec at full stick deflection
        LaunchedEffect(isTabVisible, browserScroll, browserPanX) {
            if (!isTabVisible) return@LaunchedEffect
            val wv = webViewHolder.value ?: return@LaunchedEffect
            val sy = remapStick(browserScroll) * maxStickVelocity
            val sx = remapStick(browserPanX) * maxStickVelocity
            wv.pdfSetStickScroll(sx, sy)
        }
        // Continuous zoom: push velocity to a JS-side rAF loop that smoothly scales.
        val maxZoomSpeed = 1.2f // ln-scale units/sec: ~3.3x per second at full deflection
        LaunchedEffect(isTabVisible, browserZoom) {
            if (!isTabVisible) return@LaunchedEffect
            val wv = webViewHolder.value ?: return@LaunchedEffect
            val speed = -remapStick(browserZoom) * maxZoomSpeed
            wv.pdfSetStickZoom(speed)
            if (speed != 0f) updateInteraction()
        }

        // Controller actions: R1/L1 = next/prev page bookmark, X = find, Y = guide picker,
        // R2/L2 = next/prev bookmark, L3 = first page, R3 = last page
        val controllerAction by mainViewModel.controllerAction.collectAsState()
        LaunchedEffect(controllerAction) {
            when (controllerAction) {
                MainViewModel.ControllerAction.BROWSER_SCROLL_MARKER_NEXT,
                MainViewModel.ControllerAction.BROWSER_NEXT_BOOKMARK -> {
                    if (pageBookmarks.isEmpty()) return@LaunchedEffect
                    val sorted = pageBookmarks.sortedBy { it.pageNumber }
                    val next = sorted.firstOrNull { it.pageNumber > currentPage }
                        ?: sorted.first()
                    webViewHolder.value?.goToPage(next.pageNumber)
                    updateInteraction()
                }
                MainViewModel.ControllerAction.BROWSER_SCROLL_MARKER_PREV,
                MainViewModel.ControllerAction.BROWSER_PREV_BOOKMARK -> {
                    if (pageBookmarks.isEmpty()) return@LaunchedEffect
                    val sorted = pageBookmarks.sortedBy { it.pageNumber }
                    val prev = sorted.lastOrNull { it.pageNumber < currentPage }
                        ?: sorted.last()
                    webViewHolder.value?.goToPage(prev.pageNumber)
                    updateInteraction()
                }
                MainViewModel.ControllerAction.OPEN_FIND_ON_PAGE -> {
                    showFind = !showFind
                    if (showFind) findQuery = ""
                    updateInteraction()
                }
                MainViewModel.ControllerAction.FIND_NEXT_RESULT -> {
                    if (showFind && findQuery.isNotEmpty()) webViewHolder.value?.findNext(findQuery)
                    updateInteraction()
                }
                MainViewModel.ControllerAction.FIND_PREVIOUS_RESULT -> {
                    if (showFind && findQuery.isNotEmpty()) webViewHolder.value?.findPrevious(findQuery)
                    updateInteraction()
                }
                MainViewModel.ControllerAction.OPEN_FAVORITE_MENU -> {
                    onShowGuidePicker()
                }
                MainViewModel.ControllerAction.BROWSER_SCROLL_TO_TOP -> {
                    webViewHolder.value?.goToPage(1)
                    updateInteraction()
                }
                MainViewModel.ControllerAction.BROWSER_SCROLL_TO_END -> {
                    if (totalPages > 0) webViewHolder.value?.goToPage(totalPages)
                    updateInteraction()
                }
                MainViewModel.ControllerAction.BROWSER_RESET_ZOOM -> {
                    webViewHolder.value?.evaluateJavascript(
                        "if(typeof PDFViewerApplication!=='undefined'){PDFViewerApplication.pdfViewer.currentScaleValue='auto';}",
                        null
                    )
                    updateInteraction()
                }
                else -> { }
            }
        }
    }

    val showTocHandle = documentLoaded && totalPages > 1

    Box(modifier = modifier.fillMaxSize().background(surfaceBg)) {
        AndroidView(
            factory = { ctx ->
                PdfWebView(ctx, isDark = palette.isDark).apply {
                    addJavascriptInterface(
                        PdfBridge(
                            pageChangedCallback = { page, total -> vm.onPageChanged(page, total) },
                            documentLoadedCallback = { total -> vm.onDocumentLoaded(total) },
                            outlineReadyCallback = { json -> vm.onOutlineReady(json) },
                            findResultCallback = { cur, tot -> vm.onFindResult(cur, tot) },
                            scrollPositionChangedCallback = { page, _ -> vm.onScrollPositionChanged(page, 0f) },
                            linkClickedCallback = { url -> onOpenLinkInBrowser(url) },
                            gridPageTapCallback = { page ->
                                post {
                                    pdfSetViewMode(0) { viewMode = it }
                                    goToPage(page)
                                }
                            },
                            textProbeCallback = { hasText -> vm.onTextProbeResult(hasText) },
                        ),
                        "Android"
                    )
                    customSelectionActions = PdfWebView.CustomSelectionActions(
                        onSearchOnline = { text -> onSearchOnline(text) },
                        onYouTube = { text -> onYouTube(text) },
                        onCopy = { text ->
                            val clipboard = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("text", text))
                        }
                    )
                    onLinkClicked = { url -> onOpenLinkInBrowser(url) }
                    var touchDownX = 0f
                    var touchDownY = 0f
                    var movedBeyondTapSlop = false
                    val tapSlopPx = android.view.ViewConfiguration.get(ctx).scaledTouchSlop
                    setOnTouchListener { _, event ->
                        when (event.action) {
                            android.view.MotionEvent.ACTION_DOWN -> {
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
                                    }
                                }
                            }
                            android.view.MotionEvent.ACTION_UP -> {
                                if (!movedBeyondTapSlop) {
                                    if (showFind) {
                                        showFind = false
                                    } else if (showTopBar) {
                                        showTopBar = false
                                        onChromeToggle(false)
                                    } else {
                                        updateInteraction()
                                        onChromeToggle(true)
                                    }
                                }
                            }
                        }
                        false
                    }
                    webViewHolder.value = this
                }
            },
            update = { view ->
                // Prevent WebView from receiving D-pad focus — scrolling is handled by left stick.
                // Focus should only move between top bar and bottom bar.
                view.isFocusable = false
                view.isFocusableInTouchMode = false
            },
            onRelease = { view ->
                // Fires when PdfGuideViewer leaves the composition (e.g. user opens the
                // guide picker, which sets activePdfGuide=null). Proactively free WebView
                // resources instead of waiting for GC — prevents cumulative GPU pressure.
                if (webViewHolder.value === view) {
                    webViewHolder.value = null
                }
                try {
                    view.stopLoading()
                    view.loadUrl("about:blank")
                    view.clearHistory()
                    view.removeAllViews()
                    view.destroy()
                } catch (_: Throwable) { }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Top bar (auto-hide)
        AnimatedVisibility(
            visible = showTopBar && !showFind && !showTocView,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Column {
                // Plain Surface + Row replaces TopAppBar to avoid Material3's internal
                // focus wrappers that create ghost focusable elements.
                val pdfConfiguration = LocalConfiguration.current
                val isPdfPortrait = pdfConfiguration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT
                val pdfTopBarContentPadding = if (isPdfPortrait) 4.dp else 12.dp
                val pdfTopBarIconSize = if (isPdfPortrait) 20.dp else 24.dp
                Surface(
                    color = surfaceBg.copy(alpha = 0.95f),
                    tonalElevation = 4.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = if (isPdfPortrait) 6.dp else 10.dp, vertical = 8.dp)
                            .focusProperties {
                                down = bottomBarFocusRequester ?: FocusRequester.Default
                            },
                        horizontalArrangement = if (isPdfPortrait) Arrangement.SpaceBetween else Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // The picker is an app action, and a host with no app
                        // behind it passes an empty lambda for it: a button
                        // that cannot do anything is worse than one that is not
                        // there. Same rule the viewers use for everything else
                        // reached through mainViewModel.
                        if (mainViewModel != null) {
                            com.twinlore.app.ui.navigation.NavigableIconButton(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = "Guide picker",
                                onClick = { onShowGuidePicker(); updateInteraction() },
                                focusId = "pdf_guide_picker",
                                iconSize = pdfTopBarIconSize,
                                contentPadding = pdfTopBarContentPadding,
                                externalFocusRequester = contentFocusRequester
                            )
                        }
                        // Page indicator
                        val pageInteraction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                        val pageFocused by pageInteraction.collectIsFocusedAsState()
                        Box(
                            modifier = Modifier
                                .focusable(interactionSource = pageInteraction)
                                .then(
                                    if (pageFocused) Modifier.border(2.dp, focusColor, RoundedCornerShape(8.dp))
                                    else Modifier
                                )
                                .clickable {
                                    FeedbackManager.play(view, FeedbackEvent.CONFIRM)
                                    goToPageInput = currentPage.coerceAtLeast(1).toString()
                                    showGoToPage = true
                                    updateInteraction()
                                }
                                .padding(horizontal = if (isPdfPortrait) 6.dp else 10.dp, vertical = 6.dp)
                                .onKeyEvent { keyEvent ->
                                    val code = keyEvent.nativeKeyEvent.keyCode
                                    if (keyEvent.nativeKeyEvent.action == android.view.KeyEvent.ACTION_UP &&
                                        code in listOf(android.view.KeyEvent.KEYCODE_DPAD_CENTER, android.view.KeyEvent.KEYCODE_BUTTON_A, android.view.KeyEvent.KEYCODE_ENTER)) {
                                        goToPageInput = currentPage.coerceAtLeast(1).toString()
                                        showGoToPage = true
                                        updateInteraction()
                                        true
                                    } else if (keyEvent.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN &&
                                        code in listOf(android.view.KeyEvent.KEYCODE_DPAD_CENTER, android.view.KeyEvent.KEYCODE_BUTTON_A, android.view.KeyEvent.KEYCODE_ENTER)) {
                                        true
                                    } else false
                                },
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                text = if (isPdfPortrait) "${currentPage.coerceAtLeast(1)}/${totalPages.coerceAtLeast(1)}"
                                       else "Page ${currentPage.coerceAtLeast(1)} / ${totalPages.coerceAtLeast(1)}",
                                style = MaterialTheme.typography.titleMedium,
                                color = if (pageFocused) focusColor else onSurfaceColor
                            )
                        }
                        if (!isPdfPortrait) Spacer(modifier = Modifier.weight(1f))
                        // Reset zoom (fit to current view)
                        com.twinlore.app.ui.navigation.NavigableIconButton(
                            imageVector = Icons.Default.FitScreen,
                            contentDescription = "Fit to View",
                            onClick = { webViewHolder.value?.pdfFitToView(); updateInteraction() },
                            focusId = "pdf_fit",
                            iconSize = pdfTopBarIconSize,
                            contentPadding = pdfTopBarContentPadding
                        )
                        // View mode toggle: Single → Spread → Grid → Single
                        LaunchedEffect(webViewHolder.value, documentLoaded) {
                            webViewHolder.value?.pdfGetViewMode { mode -> viewMode = mode }
                        }
                        com.twinlore.app.ui.navigation.NavigableIconButton(
                            imageVector = when (viewMode) {
                                1 -> Icons.Default.AutoStories
                                2 -> Icons.Default.GridView
                                else -> Icons.Default.Article
                            },
                            contentDescription = when (viewMode) {
                                1 -> "Grid View"
                                2 -> "Single Page"
                                else -> "Spread View"
                            },
                            onClick = {
                                val nextMode = (viewMode + 1) % 3
                                webViewHolder.value?.pdfSetViewMode(nextMode) { actual ->
                                    viewMode = actual
                                }
                                updateInteraction()
                            },
                            focusId = "pdf_view_mode",
                            iconSize = pdfTopBarIconSize,
                            contentPadding = pdfTopBarContentPadding
                        )
                        com.twinlore.app.ui.navigation.NavigableIconButton(
                            imageVector = Icons.Default.FindInPage,
                            contentDescription = "Find on Page",
                            onClick = { showFind = !showFind; updateInteraction() },
                            focusId = "pdf_find",
                            iconSize = pdfTopBarIconSize,
                            contentPadding = pdfTopBarContentPadding
                        )
                        if (showTocHandle) {
                            com.twinlore.app.ui.navigation.NavigableIconButton(
                                imageVector = Icons.Default.List,
                                contentDescription = "Table of contents",
                                onClick = { showTocView = true; showTopBar = false },
                                focusId = "pdf_toc",
                                iconSize = pdfTopBarIconSize,
                                contentPadding = pdfTopBarContentPadding
                            )
                        }
                        if (capabilities.subWindows) {
                            com.twinlore.app.ui.navigation.NavigableIconButton(
                                imageVector = Icons.Default.BookmarkAdd,
                                contentDescription = "Add bookmark",
                                onClick = {
                                    bookmarkNameInput = "Page ${currentPage.coerceAtLeast(1)}"
                                    showAddBookmark = true
                                    updateInteraction()
                                },
                                focusId = "pdf_bookmark",
                                iconSize = pdfTopBarIconSize,
                                contentPadding = pdfTopBarContentPadding
                            )
                        }
                    }
                }
            }
        }

        // Zoom controls moved to top bar

        // Full-screen 2-tab (Contents | Bookmarks) overlay. Replaces the
        // earlier right-side drawer and the expandable bookmark chip strip
        // in one shot — single place to navigate + manage bookmarks.
        if (showTocView && showTocHandle) {
            Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                PdfTocViewScreen(
                    outline = outline,
                    // The panel tucks the reader 8dp under its bar; this gives
                    // that back so the tabs start where the bar ends.
                    topSpacing = if (capabilities == com.twinlore.app.ui.screens.guide.GuideViewerCapabilities.OVERLAY) 8.dp else 24.dp,
                    bookmarks = pageBookmarks,
                    currentPage = currentPage,
                    focusColor = focusColor,
                    tabIndex = pdfTocTabIndex,
                    onTabIndexChange = { pdfTocTabIndex = it },
                    onClose = {
                        showTocView = false
                        showTopBar = false
                    },
                    onTocEntryTapped = { page ->
                        webViewHolder.value?.goToPage(page)
                        showTocView = false
                        showTopBar = false
                    },
                    onBookmarkTapped = { bm ->
                        webViewHolder.value?.goToPage(bm.pageNumber)
                        showTocView = false
                        showTopBar = false
                    },
                    onBookmarkLongPressDelete = { bm -> bookmarkToDelete = bm },
                )
            }
        }

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(surfaceBg.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                com.twinlore.app.ui.components.LoadingWithTips(
                    focusColor = focusColor,
                    showTips = false,
                )
            }
        }

        if (showFind) {
            // Straight under the panel's bar in the overlay, in compact form,
            // as the single-guide viewer does.
            val inPanel = capabilities == com.twinlore.app.ui.screens.guide.GuideViewerCapabilities.OVERLAY
            Column(modifier = Modifier.align(Alignment.TopCenter).padding(top = if (inPanel) 0.dp else 8.dp)) {
                FindOnPageUI(
                    compact = inPanel,
                    query = findQuery,
                    onQueryChange = { q ->
                        findQuery = q
                        if (q.isNotEmpty()) webViewHolder.value?.setFindQuery(q)
                    },
                    onSearch = { if (findQuery.isNotEmpty()) webViewHolder.value?.setFindQuery(findQuery) },
                    onClose = { showFind = false },
                    onSaveAsScrollMarker = {
                        bookmarkNameInput = "Page ${currentPage.coerceAtLeast(1)}"
                        showAddBookmark = true
                    },
                    onNext = { webViewHolder.value?.findNext(findQuery) },
                    onPrevious = { webViewHolder.value?.findPrevious(findQuery) },
                    matchesFound = if (findTotalMatches > 0) true else if (findQuery.isNotEmpty()) false else null,
                    matchCount = findTotalMatches,
                    currentIndex = findCurrentMatch,
                    isSearching = false,
                    recentSearches = true,
                    // Every keystroke searches, so the query counts when
                    // the bar closes rather than per letter.
                    liveSearch = true
                )
            }
        }

        // Clear PDF find highlights when find bar closes
        LaunchedEffect(showFind) {
            if (!showFind && findQuery.isNotEmpty()) {
                kotlinx.coroutines.delay(150)
                webViewHolder.value?.setFindQuery("")
                findQuery = ""
                lastToastedQuery = ""
            }
        }

        // 0-match find toast: 400ms debounce so live typing doesn't spam.
        // Differentiates scanned PDFs (no extractable text) from real misses.
        LaunchedEffect(findTotalMatches, findQuery, hasExtractableText, showFind) {
            if (!showFind || findQuery.isEmpty() || findQuery == lastToastedQuery) return@LaunchedEffect
            kotlinx.coroutines.delay(400)
            if (findTotalMatches == 0 && findQuery.isNotEmpty()) {
                lastToastedQuery = findQuery
                val msg = if (!hasExtractableText)
                    "This PDF has no extractable text (scanned image)."
                else "No matches found."
                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
            }
        }

        if (capabilities.subWindows && showGoToPage) {
            val goToPageFocusRequester = remember { FocusRequester() }
            AlertDialog(
                onDismissRequest = { showGoToPage = false },
                containerColor = surfaceBg,
                titleContentColor = onSurfaceColor,
                textContentColor = onSurfaceColor,
                title = { Text("Go to page") },
                text = {
                    Column {
                        NavigableTextField(
                            value = goToPageInput,
                            onValueChange = { goToPageInput = it.filter { c -> c.isDigit() } },
                            label = "Page",
                            keyboardType = KeyboardType.Number,
                            externalFocusRequester = goToPageFocusRequester,
                            startInEditMode = true,
                            onSubmit = {
                                val page = goToPageInput.toIntOrNull()
                                if (page != null && page in 1..totalPages) {
                                    webViewHolder.value?.goToPage(page)
                                    showGoToPage = false
                                }
                            }
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "of ${totalPages.coerceAtLeast(1)} pages",
                            color = onSurfaceColor.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                },
                confirmButton = {
                    NavigableButton(
                        text = "Go",
                        onClick = {
                            val page = goToPageInput.toIntOrNull()
                            if (page != null && page in 1..totalPages) {
                                webViewHolder.value?.goToPage(page)
                                showGoToPage = false
                            }
                        },
                        containerColor = Color.Transparent,
                        contentColor = focusColor,
                        compact = true
                    )
                },
                dismissButton = {
                    NavigableButton(
                        text = "Cancel",
                        onClick = { showGoToPage = false },
                        containerColor = Color.Transparent,
                        contentColor = onSurfaceColor.copy(alpha = 0.7f),
                        compact = true
                    )
                }
            )
        }

        if (capabilities.subWindows && showAddBookmark) {
            val bookmarkFocusRequester = remember { FocusRequester() }
            AlertDialog(
                onDismissRequest = { showAddBookmark = false },
                containerColor = surfaceBg,
                titleContentColor = onSurfaceColor,
                textContentColor = onSurfaceColor,
                title = { Text("Add bookmark") },
                text = {
                    Column {
                        Text("Current page: ${currentPage.coerceAtLeast(1)}", style = MaterialTheme.typography.bodyMedium, color = onSurfaceColor)
                        Spacer(modifier = Modifier.height(8.dp))
                        NavigableTextField(
                            value = bookmarkNameInput,
                            onValueChange = { bookmarkNameInput = it },
                            label = "Bookmark name",
                            modifier = Modifier.fillMaxWidth(),
                            externalFocusRequester = bookmarkFocusRequester,
                            onSubmit = {
                                if (bookmarkNameInput.isNotBlank()) {
                                    vm.addPageBookmark(bookmarkNameInput, currentPage.coerceAtLeast(1))
                                    showAddBookmark = false
                                }
                            }
                        )
                        LaunchedEffect(Unit) {
                            bookmarkFocusRequester.requestFocus()
                        }
                    }
                },
                confirmButton = {
                    NavigableButton(
                        text = "Save",
                        onClick = {
                            if (bookmarkNameInput.isNotBlank()) {
                                vm.addPageBookmark(bookmarkNameInput, currentPage.coerceAtLeast(1))
                                showAddBookmark = false
                            }
                        },
                        enabled = bookmarkNameInput.isNotBlank(),
                        containerColor = Color.Transparent,
                        contentColor = if (bookmarkNameInput.isNotBlank()) focusColor else onSurfaceColor.copy(alpha = 0.3f),
                        compact = true
                    )
                },
                dismissButton = {
                    NavigableButton(
                        text = "Cancel",
                        onClick = { showAddBookmark = false },
                        containerColor = Color.Transparent,
                        contentColor = onSurfaceColor.copy(alpha = 0.7f),
                        compact = true
                    )
                }
            )
        }

        if (capabilities.subWindows) {
        bookmarkToDelete?.let { bm ->
            AlertDialog(
                onDismissRequest = { bookmarkToDelete = null },
                containerColor = surfaceBg,
                titleContentColor = onSurfaceColor,
                textContentColor = onSurfaceColor,
                title = { Text("Delete bookmark?") },
                text = { Text("\"${bm.name}\" (page ${bm.pageNumber})") },
                confirmButton = {
                    TextButton(onClick = {
                        vm.deletePageBookmark(bm.id)
                        bookmarkToDelete = null
                    }) { Text("Delete", color = focusColor) }
                },
                dismissButton = {
                    TextButton(onClick = { bookmarkToDelete = null }) { Text("Cancel", color = onSurfaceColor.copy(alpha = 0.7f)) }
                }
            )
        }
        }

    }
}
