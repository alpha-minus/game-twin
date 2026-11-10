package com.twinlore.app.ui.screens

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.display.DisplayManager
import android.net.Uri
import android.app.Activity
import android.app.Dialog
import android.content.ContextWrapper
import android.content.res.Configuration
import android.media.projection.MediaProjectionManager
import android.os.Build
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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
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
import com.twinlore.app.ui.navigation.NavigableTextField
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
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
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
import androidx.compose.ui.state.ToggleableState
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
import com.twinlore.app.ui.screens.LibraryViewModel
import com.twinlore.app.ui.screens.SettingsViewModel
import com.twinlore.app.ui.utils.performHapticFeedback
import com.twinlore.app.ui.utils.runWithHapticAndLed
import com.twinlore.app.ui.LocalThemePalette
import com.twinlore.app.ui.LocalCachedFocusColor
import com.twinlore.app.ui.theme.getContrastingTextColor
import com.twinlore.app.ui.theme.getFocusContainer
import com.twinlore.app.data.MapMarkerManager
import com.twinlore.app.data.LandingSection
import com.twinlore.app.data.LandingEntry
import com.twinlore.app.data.buildLandingNavHtml
import com.twinlore.app.data.buildRecentSearchesHtml
import com.twinlore.app.data.defaultLandingEntries
import com.twinlore.app.data.SearchHistoryManager
import com.twinlore.app.data.SavedTabRepository
import com.twinlore.app.data.SavedMapRepository
import com.twinlore.app.domain.SavedMap
import com.twinlore.app.domain.SavedTab
import com.twinlore.app.di.SearchHistoryManagerEntryPoint
import com.twinlore.app.di.SavedTabRepositoryEntryPoint
import com.twinlore.app.di.NetworkConnectivityEntryPoint
import dagger.hilt.EntryPoints
import com.twinlore.app.domain.AssetType
import com.twinlore.app.domain.MapMarker
import com.twinlore.app.domain.Vault
import com.twinlore.app.domain.MarkerStyle
import com.twinlore.app.domain.ScrollRestoreData
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.focus.onFocusChanged
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
import com.twinlore.app.services.ScreenCaptureService
import com.twinlore.app.domain.FilePickerViewMode
import com.twinlore.app.domain.FilePickerSortField
import com.twinlore.app.domain.SortDirection
import com.twinlore.app.ui.utils.FeedbackEvent
import com.twinlore.app.ui.utils.FeedbackManager
import com.twinlore.app.ui.utils.ImagePathUtils
import com.twinlore.app.ui.utils.rememberMapThumbnail
import com.twinlore.app.di.VgmapsEntryPoint
import kotlinx.coroutines.suspendCancellableCoroutine

/** Tags this tab's screen-capture requests so the package-wide result broadcast is
 *  consumed here (saved as a map) and not by another tab's receiver. */
private const val SCREENSHOT_TAG_MAP = "map"

/**
 * Map tab content for Vault Session: reuses the same map UI as the guide browser bottom sheet.
 * Use this in the Map tab of VaultSessionScreen instead of the deprecated standalone MapViewerScreen.
 */
@Composable
fun MapTabContent(
    vaultId: String,
    mainViewModel: MainViewModel,
    onBackToLibrary: () -> Unit,
    sessionVault: com.twinlore.app.domain.Vault? = null,
    isTabVisible: Boolean = true,
    contentFocusRequester: androidx.compose.ui.focus.FocusRequester? = null,
    bottomBarFocusRequester: androidx.compose.ui.focus.FocusRequester? = null,
    /**
     * Moves each time the app comes back from Always on Top, and this tab
     * re-reads which image is open and where it is panned on each move: the
     * panel showed the vault's images over the same records and wrote to them.
     * Zero, the default, never moves.
     */
    positionSyncKey: Int = 0,
    modifier: Modifier = Modifier
) {
    val mapViewModel: MapViewerScreenViewModel = hiltViewModel()
    val vaultFlow = mapViewModel.getVault(vaultId)
    val vaultFromFlow by vaultFlow.collectAsState(initial = null)
    val effectiveVault = sessionVault ?: vaultFromFlow
    val context = LocalContext.current
    val rootView = androidx.compose.ui.platform.LocalView.current
    val scope = rememberCoroutineScope()

    // Saved maps tracking
    val savedMaps by mapViewModel.savedMaps.collectAsState()
    val selectedMapId by mapViewModel.selectedMapId.collectAsState()

    // Observe saved maps for this vault
    LaunchedEffect(vaultId) {
        mapViewModel.observeSavedMaps(vaultId)
    }

    // Back from Always on Top: the image the panel paged to. A different one
    // changes the selection, and everything keyed on it below re-reads its
    // pan from the store as it would on any selection; the same one re-reads
    // its pan through positionSyncKey in those keys.
    LaunchedEffect(positionSyncKey) {
        if (positionSyncKey == 0) return@LaunchedEffect
        mapViewModel.adoptStoredSelection(vaultId)
    }

    // Sort state — shared between picker display and controller/chevron navigation.
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val imagesSortField by settingsViewModel.imagesPickerSortField.collectAsState(initial = FilePickerSortField.DATE_ADDED)
    val imagesSortDirection by settingsViewModel.imagesPickerSortDirection.collectAsState(initial = SortDirection.DESC)

    // File metadata for sort-by-size and sort-by-type (async loaded; needed for navigation order).
    val libraryViewModel: LibraryViewModel = hiltViewModel()
    var imageSizes by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }
    var imageFilenames by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    val savedMapIds = remember(savedMaps) { savedMaps.map { it.id }.toSet() }
    LaunchedEffect("imageMetadata", savedMapIds) {
        // Incremental load: only fetch metadata for SavedMap ids whose
        // resourceId we haven't already cached. Together with the batch DAO
        // query this turns bulk-save thrash from O(N) queries per save into
        // O(1) per save (and one O(N) initial load). Stale ids (maps the
        // user deleted) get pruned from the maps below.
        val needed = savedMaps.mapNotNull { m ->
            val rid = m.resourceId ?: return@mapNotNull null
            if (m.id in imageSizes && m.id in imageFilenames) null else m.id to rid
        }
        val keepIds = savedMapIds
        if (needed.isEmpty()) {
            // Just prune stale entries if any savedMaps were removed.
            if (imageSizes.keys.any { it !in keepIds } || imageFilenames.keys.any { it !in keepIds }) {
                imageSizes = imageSizes.filterKeys { it in keepIds }
                imageFilenames = imageFilenames.filterKeys { it in keepIds }
            }
            return@LaunchedEffect
        }
        try {
            val ridToMapId = needed.associate { (mapId, rid) -> rid to mapId }
            val fetched = libraryViewModel.getResourceFiles(needed.map { it.second })
            val newSizes = imageSizes.toMutableMap()
            val newNames = imageFilenames.toMutableMap()
            for (rf in fetched) {
                val mapId = ridToMapId[rf.id] ?: continue
                newSizes[mapId] = rf.sizeBytes
                newNames[mapId] = rf.filename
            }
            // Prune any cached entries whose SavedMap was deleted in the
            // meantime so the maps don't grow unbounded across long sessions.
            imageSizes = newSizes.filterKeys { it in keepIds }
            imageFilenames = newNames.filterKeys { it in keepIds }
        } catch (_: Exception) {
            // Best-effort; sort-by-size/type just falls back to defaults.
        }
    }

    // Sorted map list — used by controller handler and chevron buttons via mapSelection.
    val filteredAndSortedMaps = remember(
        savedMaps, imagesSortField, imagesSortDirection, imageSizes, imageFilenames
    ) {
        savedMaps.sortedWith(
            when (imagesSortField) {
                FilePickerSortField.NAME -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }
                FilePickerSortField.LAST_OPENED -> compareBy { it.lastAccessed }
                FilePickerSortField.DATE_ADDED -> compareBy { it.createdAt }
                FilePickerSortField.SIZE -> compareBy { imageSizes[it.id] ?: 0L }
                FilePickerSortField.TYPE -> compareBy<SavedMap> {
                    imageFilenames[it.id]?.substringAfterLast('.', "")?.uppercase() ?: ""
                }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }
            }.let { if (imagesSortDirection == SortDirection.DESC) it.reversed() else it }
        )
    }

    // Maps live exclusively in saved_maps + resource_files.

    // Derive selected map
    val selectedMap = remember(savedMaps, selectedMapId) {
        savedMaps.find { it.id == selectedMapId }
    }

    // Publish the active map's title to MainViewModel so VaultSessionTopBar can
    // prepend it to the game name. Cleared on dispose so leaving the Map tab
    // (or having no map selected) restores the plain game name.
    LaunchedEffect(selectedMap?.id) {
        val title = selectedMap?.title?.takeIf { it.isNotBlank() }
        mainViewModel.setActiveDocTitle(vaultId, com.twinlore.app.domain.VaultTab.Map, title)
    }
    DisposableEffect(vaultId) {
        onDispose { mainViewModel.setActiveDocTitle(vaultId, com.twinlore.app.domain.VaultTab.Map, null) }
    }

    // Show map selection panel
    var showMapSelection by remember { mutableStateOf(false) }

    // Restore map view state synchronously so initialZoom/Offset are correct from first composition.
    // positionSyncKey is a key here for the same reason selectedMapId is: a
    // return from Always on Top is a moment the store may say something new
    // about this image, and re-reading is what these do when their keys move.
    val restoredMapState = remember(vaultId, selectedMapId, positionSyncKey) {
        mapViewModel.stateManager.getState(vaultId, selectedMapId)
    }
    var mapZoom by remember(vaultId, selectedMapId, positionSyncKey) { mutableStateOf(restoredMapState?.zoom ?: 1f) }
    var mapOffsetX by remember(vaultId, selectedMapId, positionSyncKey) { mutableStateOf(restoredMapState?.offsetX ?: 0f) }
    var mapOffsetY by remember(vaultId, selectedMapId, positionSyncKey) { mutableStateOf(restoredMapState?.offsetY ?: 0f) }
    var mapUpdateVersion by remember(vaultId) { mutableStateOf(0) }
    var mapStateHydrated by remember(vaultId, selectedMapId, positionSyncKey) { mutableStateOf(true) }

    // Persist map tab state after restore has completed.
    LaunchedEffect(vaultId, selectedMapId, mapZoom, mapOffsetX, mapOffsetY, mapStateHydrated) {
        if (!mapStateHydrated) return@LaunchedEffect
        delay(500)
        mapViewModel.stateManager.saveState(vaultId, mapZoom, mapOffsetX, mapOffsetY, selectedMapId)
    }

    // When the Map tab button is tapped while already active, show the assign map page
    var forceShowAssignPage by remember { mutableStateOf(false) }

    val mapImagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val mapTitle = "Image ${savedMaps.size + 1}"
            mapViewModel.assignMapFile(
                vaultId = vaultId,
                uri = uri,
                title = mapTitle,
                context = context
            ) { savedMap ->
                if (savedMap == null) {
                    android.util.Log.e("MapTabContent", "Failed to assign map file")
                } else {
                    forceShowAssignPage = false
                    showMapSelection = false
                }
            }
        }
    }

    // --- Screenshot capture ---
    val displayManager = remember { context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager }
    val hasMultipleDisplays = remember {
        val allDisplays = displayManager.getDisplays()
        val presentationDisplays = displayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
        android.util.Log.d("MapTabContent", "Displays: total=${allDisplays.size}, presentation=${presentationDisplays.size}, ids=${allDisplays.map { it.displayId }}")
        true // Always show for now; refine detection later
    }

    // Capture the display the APP'S WINDOW is actually on — not a hardcoded
    // `displays[1]` (the old code always targeted the 2nd display in the list,
    // so the screenshot was the same screen regardless of where the app was
    // running). Resolved fresh at capture time via the root view's attached
    // display, so it stays correct if the app moves between displays
    // (built-in <-> external/desktop) within a session.
    val resolveCaptureDisplayId: () -> Int = {
        rootView.display?.displayId ?: android.view.Display.DEFAULT_DISPLAY
    }

    // MediaProjection — service stays alive so the permission dialog only shows once per session.
    // Subsequent taps send ACTION_CAPTURE to the running service.
    val screenshotPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val saveDir = File(context.cacheDir, "screenshots").absolutePath
            val serviceIntent = Intent(context, ScreenCaptureService::class.java).apply {
                putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(ScreenCaptureService.EXTRA_DATA, result.data)
                putExtra(ScreenCaptureService.EXTRA_SAVE_DIR, saveDir)
                putExtra(ScreenCaptureService.EXTRA_DISPLAY_ID, resolveCaptureDisplayId())
                putExtra(ScreenCaptureService.EXTRA_REQUEST_TAG, SCREENSHOT_TAG_MAP)
            }
            context.startForegroundService(serviceIntent)
        }
    }

    // Broadcast receiver for screenshot result and projection-lost
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                when (intent?.action) {
                    ScreenCaptureService.ACTION_SCREENSHOT_TAKEN -> {
                        // Ignore captures another tab (e.g. AI) requested — this is a
                        // package-wide broadcast. A null tag is treated as ours for
                        // backward compatibility (the Map tab was the original caller).
                        val tag = intent.getStringExtra(ScreenCaptureService.EXTRA_REQUEST_TAG)
                        if (tag != null && tag != SCREENSHOT_TAG_MAP) return
                        val filePath = intent.getStringExtra(ScreenCaptureService.EXTRA_FILE_PATH) ?: return
                        val screenshotFile = File(filePath)
                        if (!screenshotFile.exists()) return

                        val mapTitle = screenshotFile.nameWithoutExtension.replace("_", " ")
                        mapViewModel.assignMapFile(
                            vaultId = vaultId,
                            uri = Uri.fromFile(screenshotFile),
                            title = mapTitle,
                            context = context
                        ) { savedMap ->
                            if (savedMap == null) {
                                android.util.Log.e("MapTabContent", "Failed to assign screenshot as map")
                            } else {
                                forceShowAssignPage = false
                                showMapSelection = false
                            }
                            screenshotFile.delete()
                        }
                    }
                    ScreenCaptureService.ACTION_PROJECTION_LOST -> {
                        android.util.Log.d("MapTabContent", "MediaProjection lost — will re-prompt on next screenshot")
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(ScreenCaptureService.ACTION_SCREENSHOT_TAKEN)
            addAction(ScreenCaptureService.ACTION_PROJECTION_LOST)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        onDispose {
            try { context.unregisterReceiver(receiver) } catch (_: Exception) {}
            // Let ScreenCaptureService live for the entire app session so the user
            // doesn't have to re-grant MediaProjection permission every time they
            // navigate away from the map tab and come back.
        }
    }

    val onTakeScreenshot: () -> Unit = {
        if (ScreenCaptureService.isRunning) {
            // Service already has a live projection — just trigger another capture
            val serviceIntent = Intent(context, ScreenCaptureService::class.java).apply {
                action = ScreenCaptureService.ACTION_CAPTURE
                putExtra(ScreenCaptureService.EXTRA_SAVE_DIR, File(context.cacheDir, "screenshots").absolutePath)
                putExtra(ScreenCaptureService.EXTRA_DISPLAY_ID, resolveCaptureDisplayId())
            }
            context.startForegroundService(serviceIntent)
        } else {
            // First time or projection was lost — request permission
            val mediaProjectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            screenshotPermissionLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
        }
    }

    // Map tab re-tap toggles between map view and map selection panel.
    androidx.compose.runtime.LaunchedEffect(Unit) {
        mainViewModel.tabReselected.collect { tab ->
            if (tab == com.twinlore.app.domain.VaultTab.Map) {
                if (showMapSelection || forceShowAssignPage) {
                    val vault = effectiveVault
                    if (savedMaps.isNotEmpty() && selectedMap != null) {
                        showMapSelection = false
                        forceShowAssignPage = false
                    }
                } else {
                    showMapSelection = true
                    mainViewModel.setVaultSessionMapChromeVisible(true)
                    try { bottomBarFocusRequester?.requestFocus() } catch (_: Throwable) {}
                }
            }
        }
    }

    // L2/R2: switch between saved maps (like guide bookmark cycling).
    // Subscribe inside a visibility-keyed LaunchedEffect rather than collectAsState
    // so hidden tabs don't recompose on every controllerAction emission.
    LaunchedEffect(isTabVisible) {
        if (!isTabVisible) return@LaunchedEffect
        mainViewModel.controllerAction.collect { action ->
            if (filteredAndSortedMaps.size < 2) return@collect
            val currentIndex = filteredAndSortedMaps.indexOfFirst { it.id == selectedMapId }
            when (action) {
                com.twinlore.app.ui.MainViewModel.ControllerAction.MAP_NEXT_FILE -> {
                    val nextIndex = when {
                        currentIndex < 0 -> 0
                        currentIndex < filteredAndSortedMaps.size - 1 -> currentIndex + 1
                        else -> 0
                    }
                    if (mapStateHydrated && selectedMapId != null) {
                        mapViewModel.stateManager.saveState(vaultId, mapZoom, mapOffsetX, mapOffsetY, selectedMapId)
                    }
                    mapViewModel.selectMap(filteredAndSortedMaps[nextIndex].id)
                }
                com.twinlore.app.ui.MainViewModel.ControllerAction.MAP_PREV_FILE -> {
                    val prevIndex = when {
                        currentIndex < 0 -> filteredAndSortedMaps.size - 1
                        currentIndex > 0 -> currentIndex - 1
                        else -> filteredAndSortedMaps.size - 1
                    }
                    if (mapStateHydrated && selectedMapId != null) {
                        mapViewModel.stateManager.saveState(vaultId, mapZoom, mapOffsetX, mapOffsetY, selectedMapId)
                    }
                    mapViewModel.selectMap(filteredAndSortedMaps[prevIndex].id)
                }
                else -> {}
            }
        }
    }

    val hasActiveMap = savedMaps.isNotEmpty() && selectedMap != null
    val showingAssignPage = forceShowAssignPage || showMapSelection || !hasActiveMap

    // Focus is self-managed: assign page focuses its first button via LaunchedEffect(Unit).
    // When viewer is showing: no Compose focus → Activity handles D-pad for pan.
    // When picker/assign page is showing: self-focuses first button on composition.

    // Back / B button: if viewing a map, return to selection panel; if on selection panel, go to library
    BackHandler(enabled = isTabVisible) {
        if (!showingAssignPage && hasActiveMap) {
            // Map viewer → picker. Focus bottom bar so user has control.
            showMapSelection = true
            mainViewModel.setVaultSessionMapChromeVisible(true)
            // Delay to let bottom bar AnimatedVisibility start before requesting focus
            scope.launch {
                kotlinx.coroutines.delay(200)
                try { bottomBarFocusRequester?.requestFocus() } catch (_: Throwable) {}
            }
        } else {
            onBackToLibrary()
        }
    }

    if (effectiveVault == null) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    MapBottomSheetContent(
        vault = effectiveVault,
        vaultId = vaultId,
        mapViewModel = mapViewModel,
        mainViewModel = mainViewModel,
        initialZoom = mapZoom,
        initialOffsetX = mapOffsetX,
        initialOffsetY = mapOffsetY,
        mapUpdateVersion = mapUpdateVersion,
        onBumpMapVersion = { mapUpdateVersion++ },
        onStateChange = { z, x, y ->
            mapZoom = z
            mapOffsetX = x
            mapOffsetY = y
        },
        onAssignImage = { mapImagePickerLauncher.launch(arrayOf("image/*")) },
        onTakeScreenshot = onTakeScreenshot,
        hasMultipleDisplays = hasMultipleDisplays,
        onRemoveMap = { mapViewModel.removeMap(vaultId) },
        onDismiss = onBackToLibrary,
        inVaultSessionMapTab = true,
        forceShowAssignPage = forceShowAssignPage || showMapSelection,
        onDismissAssignPage = {
            forceShowAssignPage = false
            showMapSelection = false
        },
        isTabVisible = isTabVisible,
        contentFocusRequester = contentFocusRequester,
        bottomBarFocusRequester = bottomBarFocusRequester,
        positionSyncKey = positionSyncKey,
        imageSizes = imageSizes,
        imageFilenames = imageFilenames,
        mapSelection = MapSelectionCallbacks(
            savedMaps = filteredAndSortedMaps,
            selectedMapId = selectedMapId,
            selectedMap = selectedMap,
            onSelectMap = { mapId ->
                // Pre-flight resolve: if the picked map's file is missing in
                // the home folder, surface a toast pointing the user at how
                // to recover and don't change the selected map. The integrity
                // banner on the Library handles bulk recovery; this toast
                // covers the per-row case.
                val target = savedMaps.firstOrNull { it.id == mapId }
                val rid = target?.resourceId
                if (rid != null) {
                    scope.launch {
                        val present = mapViewModel.isResourcePresent(rid)
                        if (!present) {
                            android.widget.Toast.makeText(
                                context,
                                "Waiting for file to sync. It will appear when your sync app delivers it.",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                            return@launch
                        }
                        if (mapStateHydrated && selectedMapId != null) {
                            mapViewModel.stateManager.saveState(vaultId, mapZoom, mapOffsetX, mapOffsetY, selectedMapId)
                        }
                        mapViewModel.selectMap(mapId)
                        showMapSelection = false
                        forceShowAssignPage = false
                    }
                } else {
                    if (mapStateHydrated && selectedMapId != null) {
                        mapViewModel.stateManager.saveState(vaultId, mapZoom, mapOffsetX, mapOffsetY, selectedMapId)
                    }
                    mapViewModel.selectMap(mapId)
                    showMapSelection = false
                    forceShowAssignPage = false
                }
            },
            onDeleteSavedMap = { mapId -> mapViewModel.deleteSavedMap(vaultId, mapId) },
            onRenameSavedMap = { mapId, newTitle -> mapViewModel.renameSavedMap(mapId, newTitle) },
            onShowMapSelection = {
                showMapSelection = true
                mainViewModel.setVaultSessionMapChromeVisible(true)
                try { bottomBarFocusRequester?.requestFocus() } catch (_: Throwable) {}
            }
        ),
        modifier = modifier
    )

}

/** Grouped settings state to reduce register pressure in large composables (DEX 256-register limit). */
internal data class MapSettingsState(
    val currentSearchEngine: SearchEngine,
    val hapticFeedbackIntensity: HapticFeedbackIntensity,
    val focusColor: Color,
    val ledEnabled: Boolean,
    val ledBrightness: Int,
    val ledSaturationBoost: Float,
    val ledTurnOffTime: LedTurnOffTime
)

@Composable
internal fun rememberMapSettings(): MapSettingsState {
    val vm: SettingsViewModel = hiltViewModel()
    val searchEngine by vm.searchEngine.collectAsState(initial = SearchEngine.GOOGLE)
    val haptic by vm.hapticFeedbackIntensity.collectAsState(initial = HapticFeedbackIntensity.MEDIUM)
    val focusColorState by vm.focusColor.collectAsState(initial = FocusColor.CRIMSON)
    val cachedFocusColor = LocalCachedFocusColor.current?.value
    val focusColor = cachedFocusColor ?: focusColorState.color
    val ledEnabled by vm.ledEnabled.collectAsState(initial = true)
    val ledBrightness by vm.ledBrightness.collectAsState(initial = 127)
    val ledSaturationBoost by vm.ledSaturationBoost.collectAsState(initial = 0.75f)
    val ledTurnOffTime by vm.ledTurnOffTime.collectAsState(initial = LedTurnOffTime.SEC_15)
    return MapSettingsState(searchEngine, haptic, focusColor, ledEnabled, ledBrightness, ledSaturationBoost, ledTurnOffTime)
}

/** Grouped parameters for multi-map support to avoid exceeding DEX method parameter limits. */
internal data class MapSelectionCallbacks(
    val savedMaps: List<SavedMap> = emptyList(),
    val selectedMapId: String? = null,
    val selectedMap: SavedMap? = null,
    val onSelectMap: (String) -> Unit = {},
    val onDeleteSavedMap: (String) -> Unit = {},
    val onRenameSavedMap: (String, String) -> Unit = { _, _ -> },
    val onShowMapSelection: () -> Unit = {}
)

/**
 * Map content for bottom sheet - extracted from MapViewerScreen
 */
@OptIn(ExperimentalComposeUiApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class, ExperimentalFoundationApi::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@Composable
internal fun MapBottomSheetContent(
    vault: com.twinlore.app.domain.Vault,
    vaultId: String,
    mapViewModel: MapViewerScreenViewModel,
    mainViewModel: MainViewModel,
    initialZoom: Float,
    initialOffsetX: Float,
    initialOffsetY: Float,
    mapUpdateVersion: Int,
    onBumpMapVersion: () -> Unit,
    onStateChange: (Float, Float, Float) -> Unit,
    onAssignImage: () -> Unit,
    onTakeScreenshot: () -> Unit = {},
    hasMultipleDisplays: Boolean = false,
    @Suppress("UNUSED_PARAMETER") onRemoveMap: () -> Unit,
    onDismiss: () -> Unit,
    inVaultSessionMapTab: Boolean = false,
    forceShowAssignPage: Boolean = false,
    onDismissAssignPage: () -> Unit = {},
    isTabVisible: Boolean = true,
    contentFocusRequester: FocusRequester? = null,
    bottomBarFocusRequester: FocusRequester? = null,
    /** See MapTabContent. Keys the pan state below alongside the selection. */
    positionSyncKey: Int = 0,
    mapSelection: MapSelectionCallbacks = MapSelectionCallbacks(),
    imageSizes: Map<String, Long> = emptyMap(),
    imageFilenames: Map<String, String> = emptyMap(),
    modifier: Modifier = Modifier
) {
    // Access mapSelection fields directly as mapSelection.xxx to reduce DEX register count
    // Settings state (grouped to reduce DEX register pressure — avoids 256-register VerifyError)
    val stg = rememberMapSettings()
    val view = LocalView.current

    // File picker toolbar state
    val libraryViewModel: LibraryViewModel = hiltViewModel()
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val imagesViewMode by settingsViewModel.imagesPickerViewMode.collectAsState(initial = FilePickerViewMode.GRID)
    val imagesSortField by settingsViewModel.imagesPickerSortField.collectAsState(initial = FilePickerSortField.DATE_ADDED)
    val imagesSortDirection by settingsViewModel.imagesPickerSortDirection.collectAsState(initial = SortDirection.DESC)
    val focusSoundEnabled by settingsViewModel.focusSoundEnabled.collectAsState(initial = true)

    // Get context for file operations
    val context = LocalContext.current
    val configuration = LocalConfiguration.current

    // Show toast when local media bind completes
    LaunchedEffect(Unit) {
        libraryViewModel.localBindToast.collect { msg ->
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    // Determine which map to display: the selected saved map (resource-id based)
    // is the only source.
    //
    // No fallback to `vault.mapResourceId` — that field is a pre-Stage-4
    // vestige that's no longer the source of truth. The bug: after deleting
    // the currently-selected map, the VM's auto-select can briefly land on
    // null while the next maps emission is in flight; the old fallback then
    // rolled back to whatever `vault.mapResourceId` cached, which often
    // pointed at a still-on-disk resource_files row for the deleted map and
    // surfaced the deleted thumbnail (see memory:
    // project_screenshot_open_wrong_image_active).
    val activeMapResourceId = mapSelection.selectedMap?.resourceId
    val (rawMapUri, mapResolved) =
        com.twinlore.app.ui.utils.rememberResourceImageWithResolution(activeMapResourceId)
    // Keep the last non-null URI so prev/next swaps don't blink the picker
    // while the new resourceId is still resolving. Scoped to vaultId only —
    // NOT activeMapResourceId, because re-keying on the resource id would
    // reset the bridge to null on the very frame of the swap (before the new
    // URI has resolved), which is exactly the flash we're trying to prevent.
    // The LaunchedEffect below handles clearing the bridge on genuine delete
    // or failed resolve: rememberResourceImageWithResolution unconditionally
    // flips `resolved` to true once its LaunchedEffect runs, so the
    // `mapResolved || activeMapResourceId == null` branch always fires and
    // collapses the bridge back to null in those cases.
    var lastMapPath by remember(vault.id) {
        mutableStateOf<String?>(null)
    }
    LaunchedEffect(rawMapUri, mapResolved, activeMapResourceId) {
        when {
            rawMapUri != null -> lastMapPath = rawMapUri.toString()
            mapResolved || activeMapResourceId == null -> lastMapPath = null
        }
    }
    val activeMapPath = rawMapUri?.toString() ?: lastMapPath
    val hasAnyMap = mapSelection.selectedMap != null || vault.hasMap
    // Only treat the screen as "showing the map image" when an actual image is
    // rendered. If hasAnyMap is true but activeMapPath is null (e.g. the file
    // is missing in the home folder), the assign page is shown instead, and
    // the chrome must NOT auto-hide — otherwise the bottom bar disappears
    // and the user has no way to bring it back via taps.
    val showingMapImage = hasAnyMap && activeMapPath != null && !forceShowAssignPage

    // Marker state
    val markers by mapViewModel.markers.collectAsState()
    var imageWidth by remember { mutableStateOf(0f) }
    var imageHeight by remember { mutableStateOf(0f) }
    var screenWidth by remember { mutableStateOf(0f) }
    var screenHeight by remember { mutableStateOf(0f) }
    var currentZoom by remember(mapSelection.selectedMapId, positionSyncKey) { mutableStateOf(initialZoom) }
    var currentOffsetX by remember(mapSelection.selectedMapId, positionSyncKey) { mutableStateOf(initialOffsetX) }
    var currentOffsetY by remember(mapSelection.selectedMapId, positionSyncKey) { mutableStateOf(initialOffsetY) }

    // Dialog and menu state
    var showStyleDialog by remember { mutableStateOf(false) }
    var markerForStyleChange by remember { mutableStateOf<MapMarker?>(null) } // Marker being changed
    var longPressPosition by remember { mutableStateOf<Offset?>(null) }
    var selectedMarkerForMenu by remember { mutableStateOf<MapMarker?>(null) }
    var showLimitDialog by remember { mutableStateOf(false) }
    var showFilterDialog by remember { mutableStateOf(false) }
    var selectedFilterStyle by remember { mutableStateOf<MarkerStyle?>(null) }
    var showDeleteAllMarkersDialog by remember { mutableStateOf(false) }
    var showRemoveMapConfirmation by remember { mutableStateOf(false) }

    // VGMaps picker + downloader wiring.
    var showVgmapsPicker by rememberSaveable { mutableStateOf(false) }
    val vgmapsDownloader = remember(context) {
        VgmapsEntryPoint.get(context).vgmapsBulkDownloader()
    }

    // Use initial values directly - MapZoomableImage will only sync on first composition or cacheBustVersion change
    // This prevents recomposition during gestures while still allowing correct initial values
    
    // Top bar visibility
    var showTopBar by remember { mutableStateOf(true) }
    var lastInteractionTime by remember { mutableStateOf(System.currentTimeMillis()) }

    // Load markers on mount (scoped to selected map)
    LaunchedEffect(vaultId, mapSelection.selectedMapId) {
        mapViewModel.loadMarkers(vaultId, mapSelection.selectedMapId)
    }
    
    // Bump parent map version when map path changes (for cache busting)
    // This ensures fresh image load when path changes from flow (e.g. same-filename replace)
    var previousMapPath by remember(vaultId) { mutableStateOf<String?>(null) }
    LaunchedEffect(activeMapPath) {
        if (activeMapPath != previousMapPath && activeMapPath != null) {
            onBumpMapVersion()
            previousMapPath = activeMapPath
        } else if (previousMapPath == null && activeMapPath != null) {
            previousMapPath = activeMapPath
        } else if (activeMapPath == null) {
            previousMapPath = null
        }
    }
    // When map is replaced in-place (same path, e.g. reassign from browser), force image reload
    val mapReplacedSignal by mapViewModel.mapReplacedSignal.collectAsState(initial = Pair("", 0))
    LaunchedEffect(mapReplacedSignal) {
        if (mapReplacedSignal.first == vaultId) {
            onBumpMapVersion()
        }
    }
    
    // Validate selectedMarkerForMenu and markerForStyleChange still exist in markers list
    // This fixes the bug where deleting a marker leaves a stale reference
    LaunchedEffect(markers) {
        selectedMarkerForMenu?.let { selectedMarker ->
            val exists = markers.any { it.id == selectedMarker.id }
            if (!exists) {
                selectedMarkerForMenu = null
            }
        }
        markerForStyleChange?.let { markerToChange ->
            if (markers.none { it.id == markerToChange.id }) {
                markerForStyleChange = null
                showStyleDialog = false
            }
        }
    }
    
    // Track if this is the first composition to sync initial values only once
    var isFirstComposition by remember(mapSelection.selectedMapId) { mutableStateOf(true) }
    
    // Only sync initial values on first composition, not during normal pan/zoom
    // This prevents feedback loop where pan/zoom updates parent state, which flows back and resets local state
    LaunchedEffect(Unit) {
        if (isFirstComposition) {
            currentZoom = initialZoom
            currentOffsetX = initialOffsetX
            currentOffsetY = initialOffsetY
            isFirstComposition = false
        }
    }
    
    fun updateInteraction() {
        lastInteractionTime = System.currentTimeMillis()
        showTopBar = true
    }
    
    // Apply a zoom/pan state change WITHOUT touching the chrome. Pinch/pan
    // gestures route here so panning and zooming never reveal the top/bottom
    // bars — only an explicit tap toggles chrome (see onTouch wiring below).
    fun applyStateChange(zoom: Float, offsetX: Float, offsetY: Float) {
        currentZoom = zoom
        currentOffsetX = offsetX
        currentOffsetY = offsetY
        onStateChange(zoom, offsetX, offsetY)
    }

    // Track state changes — used for deliberate navigation (e.g. restoring a
    // marker view) that SHOULD wake the chrome.
    fun handleStateChange(zoom: Float, offsetX: Float, offsetY: Float) {
        applyStateChange(zoom, offsetX, offsetY)
        updateInteraction()
    }
    
    // Restore marker view - center around marker at saved zoom level
    fun restoreMarkerView(marker: MapMarker) {
        if (imageWidth > 0 && imageHeight > 0 && screenWidth > 0 && screenHeight > 0) {
            // Calculate offset to center the marker on screen at the saved zoom level
            val (offsetX, offsetY) = MapMarkerCoordinateUtils.calculateOffsetToCenter(
                marker.normalizedX,
                marker.normalizedY,
                screenWidth,
                screenHeight,
                imageWidth,
                imageHeight,
                marker.zoom
            )
            
            // Restore zoom and center around marker (don't restore scroll position)
            handleStateChange(marker.zoom, offsetX, offsetY)
        } else {
            // Fallback: if image size not loaded yet, just restore zoom (will center later)
            Log.w("MapBottomSheet", "Image size not loaded, only restoring zoom")
            handleStateChange(marker.zoom, 0f, 0f)
        }
        
        updateInteraction()
    }
    
    // Vault session map image: auto-hide top bar after 5s (with or without markers), like Guide/HTML.
    // Guide map sheet (non-session): keep top bar if markers exist.
    LaunchedEffect(showTopBar, lastInteractionTime, inVaultSessionMapTab, showingMapImage, markers.size) {
        if (!showTopBar) return@LaunchedEffect
        val autoHide = inVaultSessionMapTab && showingMapImage || !inVaultSessionMapTab && markers.isEmpty()
        if (!autoHide) return@LaunchedEffect
        delay(5000)
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastInteractionTime >= 5000) {
            showTopBar = false
        }
    }

    // Returning to Map tab or opening the map view: show chrome again.
    LaunchedEffect(isTabVisible, inVaultSessionMapTab, showingMapImage, vaultId) {
        if (inVaultSessionMapTab && isTabVisible && showingMapImage) {
            showTopBar = true
            lastInteractionTime = System.currentTimeMillis()
        }
    }

    // Reset auto-hide timer when user interacts with top or bottom menu (e.g. bottom bar)
    LaunchedEffect(isTabVisible) {
        if (!isTabVisible) return@LaunchedEffect
        mainViewModel.chromeInteractionTrigger.collect {
            updateInteraction()
        }
    }

    // Sync bottom bar with top bar in vault session (same as Guide/Browser intent).
    LaunchedEffect(showTopBar, inVaultSessionMapTab, isTabVisible, showingMapImage) {
        if (!inVaultSessionMapTab) return@LaunchedEffect
        if (!isTabVisible || !showingMapImage) {
            mainViewModel.setVaultSessionMapChromeVisible(true)
        } else {
            mainViewModel.setVaultSessionMapChromeVisible(showTopBar)
        }
    }

    // When chrome is hidden externally (e.g. D-pad UP from bottom bar enters viewer mode),
    // also hide the top bar so both bars disappear together.
    val mapChromeVisibleForSync by mainViewModel.vaultSessionMapChromeVisible.collectAsState()
    LaunchedEffect(mapChromeVisibleForSync, inVaultSessionMapTab, showingMapImage) {
        if (!inVaultSessionMapTab || !showingMapImage) return@LaunchedEffect
        if (!mapChromeVisibleForSync && showTopBar) {
            showTopBar = false
        }
    }

    // Handle controller input for panning and zooming.
    // Read from mainViewModel.controllerInput.value each frame so we get live input (collectAsState
    // in a LaunchedEffect(Unit) loop would see stale values and make pan/zoom feel unresponsive).
    // Key on selectedMapId too: currentZoom/OffsetX/OffsetY are remember(selectedMapId), so a map
    // switch (e.g. after adding a screenshot) creates fresh MutableState instances. Without this key
    // the coroutine keeps writing to the orphaned delegates and the viewer appears frozen. The same
    // goes for positionSyncKey, which is in those keys for the same reason.
    LaunchedEffect(isTabVisible, mapSelection.selectedMapId, positionSyncKey) {
        if (!isTabVisible) return@LaunchedEffect
        val panSpeed = 10f // Pixels per frame for panning
        val zoomSpeed = 0.02f // Zoom change per frame
        val deadzone = 0.1f
        var lastNotifyTime = 0L
        var hadInputLastFrame = false
        val notifyThrottleMs = 100L

        while (true) {
            val input = mainViewModel.controllerInput.value
            val hasPanInput = kotlin.math.abs(input.panX) > deadzone || kotlin.math.abs(input.panY) > deadzone
            val hasZoomInput = kotlin.math.abs(input.zoom) > deadzone
            val hasInput = hasPanInput || hasZoomInput

            if (hasInput) {
                var newOffsetX = currentOffsetX
                var newOffsetY = currentOffsetY
                var newZoom = currentZoom

                // Apply panning from controller input (left stick or D-pad)
                if (hasPanInput) {
                    newOffsetX += input.panX * panSpeed
                    newOffsetY += input.panY * panSpeed
                }

                // Apply zooming from right analog stick
                // Negative zoom value = zoom in, positive = zoom out
                if (hasZoomInput) {
                    val oldZoom = newZoom
                    val zoomChange = -input.zoom * zoomSpeed // Invert so negative = zoom in
                    newZoom = (newZoom + zoomChange).coerceIn(1f, 12f)

                    // Adjust offset to keep screen center fixed while zooming
                    if (oldZoom > 0f && screenWidth > 0f && screenHeight > 0f) {
                        val zoomRatio = newZoom / oldZoom
                        newOffsetX *= zoomRatio
                        newOffsetY *= zoomRatio
                    }
                }

                // Update local state every frame for smooth rendering (MapZoomableImage reads these)
                currentZoom = newZoom
                currentOffsetX = newOffsetX
                currentOffsetY = newOffsetY
                // Do not call updateInteraction() here: controller pan must not block auto-hide.

                // Notify parent at most every notifyThrottleMs to avoid recomposing parent every frame
                val now = System.currentTimeMillis()
                if (now - lastNotifyTime >= notifyThrottleMs) {
                    onStateChange(newZoom, newOffsetX, newOffsetY)
                    lastNotifyTime = now
                }
                hadInputLastFrame = true
                delay(16)
            } else {
                // Input released: send final state once so parent has correct values for next open
                if (hadInputLastFrame) {
                    onStateChange(currentZoom, currentOffsetX, currentOffsetY)
                    hadInputLastFrame = false
                }
                delay(200)
            }
        }
    }
    
    // Filter markers by style
    val filteredMarkers = remember(markers, selectedFilterStyle) {
        if (selectedFilterStyle == null) {
            markers
        } else {
            markers.filter { it.style == selectedFilterStyle }
        }
    }
    
    // Track currently focused marker index for R1/L1 navigation
    var focusedMarkerIndex by remember { mutableStateOf<Int?>(null) }
    
    // Restore or reset focused marker index when markers list changes
    LaunchedEffect(vaultId, filteredMarkers.size) {
        // If current focused index is out of bounds, reset it
        focusedMarkerIndex?.let { index ->
            if (index >= filteredMarkers.size) {
                focusedMarkerIndex = if (filteredMarkers.isNotEmpty()) 0 else null
            }
        }
        // If no focused marker and markers exist, restore from last focused id or default to first
        if (focusedMarkerIndex == null && filteredMarkers.isNotEmpty()) {
            val focusedId = mapViewModel.getFocusedMarkerId(vaultId, mapSelection.selectedMapId)
            focusedMarkerIndex = filteredMarkers.indexOfFirst { it.id == focusedId }.takeIf { it >= 0 } ?: 0
        }
    }
    
    // Handle R1/L1 marker navigation
    val markerNavigation by mainViewModel.markerNavigation.collectAsState()
    LaunchedEffect(markerNavigation) {
        if (filteredMarkers.isEmpty()) {
            focusedMarkerIndex = null
            return@LaunchedEffect
        }
        
        when (markerNavigation) {
            MainViewModel.MarkerNavigationDirection.NEXT -> {
                // Navigate to next marker (circular)
                val currentIndex = focusedMarkerIndex ?: -1
                val nextIndex = (currentIndex + 1) % filteredMarkers.size
                focusedMarkerIndex = nextIndex
                val nextMarker = filteredMarkers[nextIndex]
                mapViewModel.setFocusedMarkerId(vaultId, nextMarker.id, mapSelection.selectedMapId)
                restoreMarkerView(nextMarker)
                updateInteraction() // Show top bar when navigating
            }
            MainViewModel.MarkerNavigationDirection.PREVIOUS -> {
                // Navigate to previous marker (circular)
                val currentIndex = focusedMarkerIndex ?: filteredMarkers.size
                val prevIndex = if (currentIndex <= 0) filteredMarkers.size - 1 else currentIndex - 1
                focusedMarkerIndex = prevIndex
                val prevMarker = filteredMarkers[prevIndex]
                mapViewModel.setFocusedMarkerId(vaultId, prevMarker.id, mapSelection.selectedMapId)
                restoreMarkerView(prevMarker)
                updateInteraction() // Show top bar when navigating
            }
            MainViewModel.MarkerNavigationDirection.NONE -> {
                // No action needed
            }
        }
    }
    
    // L2/R2 marker filter navigation: cycle selectedFilterStyle (All -> PIN -> FLAG -> STAR -> CROSSHAIR -> All)
    val markerFilterNavigation by mainViewModel.markerFilterNavigation.collectAsState()
    val filterOptions = remember { listOf(null as MarkerStyle?) + MarkerStyle.values().toList() }
    LaunchedEffect(markerFilterNavigation) {
        when (markerFilterNavigation) {
            MainViewModel.MarkerFilterNavigationDirection.NEXT -> {
                val currentIndex = filterOptions.indexOf(selectedFilterStyle).let { if (it < 0) 0 else it }
                val nextIndex = (currentIndex + 1) % filterOptions.size
                selectedFilterStyle = filterOptions[nextIndex]
                updateInteraction()
            }
            MainViewModel.MarkerFilterNavigationDirection.PREVIOUS -> {
                val currentIndex = filterOptions.indexOf(selectedFilterStyle).let { if (it < 0) 0 else it }
                val prevIndex = if (currentIndex <= 0) filterOptions.lastIndex else currentIndex - 1
                selectedFilterStyle = filterOptions[prevIndex]
                updateInteraction()
            }
            MainViewModel.MarkerFilterNavigationDirection.NONE -> { }
        }
    }
    
    // L3: add marker at center (same outcome as long-press at screen center)
    val addMarkerAtCenterTrigger by mainViewModel.addMarkerAtCenterTrigger.collectAsState()
    LaunchedEffect(addMarkerAtCenterTrigger) {
        if (!addMarkerAtCenterTrigger) return@LaunchedEffect
        if (screenWidth <= 0f || screenHeight <= 0f) return@LaunchedEffect
        updateInteraction()
        if (mapViewModel.isLimitReached(vaultId, mapSelection.selectedMapId) && markers.size >= MapMarkerManager.MAX_MARKERS) {
            showLimitDialog = true
        } else {
            longPressPosition = Offset(screenWidth / 2f, screenHeight / 2f)
            showStyleDialog = true
        }
    }
    
    // Long-press handler
    fun handleLongPress(position: Offset) {
        view.performHapticFeedback(stg.hapticFeedbackIntensity)
        if (stg.ledEnabled) {
            ThorLedManager.setColor(
                stg.focusColor,
                true,
                stg.ledBrightness,
                stg.ledSaturationBoost,
                stg.ledTurnOffTime.inactivityMs
            )
        }
        updateInteraction()
        if (mapViewModel.isLimitReached(vaultId, mapSelection.selectedMapId) && markers.size >= MapMarkerManager.MAX_MARKERS) {
            showLimitDialog = true
        } else {
            longPressPosition = position
            showStyleDialog = true
        }
    }
    
    // Create marker
    fun createMarker(style: MarkerStyle) {
        val position = longPressPosition ?: return
        if (imageWidth > 0 && imageHeight > 0 && screenWidth > 0 && screenHeight > 0) {
            // Save the touch position and current zoom level
            val (normalizedX, normalizedY) = MapMarkerCoordinateUtils.screenToNormalized(
                position.x,
                position.y,
                screenWidth,
                screenHeight,
                imageWidth,
                imageHeight,
                currentZoom,
                currentOffsetX,
                currentOffsetY
            )
            
            // Save marker with zoom and position only (offsetX/offsetY will be calculated on restore)
            val marker = MapMarker(
                id = UUID.randomUUID().toString(),
                normalizedX = normalizedX,
                normalizedY = normalizedY,
                zoom = currentZoom,
                offsetX = 0f, // Not used - will be calculated on restore
                offsetY = 0f, // Not used - will be calculated on restore
                styleId = style.id
            )
            
            val success = mapViewModel.saveMarker(vaultId, marker, mapSelection.selectedMapId)
            if (!success) {
                showLimitDialog = true
            }
        } else {
        }
        showStyleDialog = false
        markerForStyleChange = null
        longPressPosition = null
    }
    
    // Theme-coloured base so partial-alpha frames of the viewer↔picker dissolve
    // (below) never expose the window background (#3A096E). MaterialTheme.colorScheme
    // .background == the picker's own background, so it's invisible once content draws.
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Dissolve between viewer and picker — the same top-layer fade the guide
        // tab uses (GuideTabContent). A plain `when` swap flashes the picker's
        // bare background during its first compose frames; here the outgoing
        // image stays opaque on top while the incoming screen composes underneath.
        // Keyed on showingMapImage only, so image↔image switches fall through to
        // the inner Crossfade below rather than re-triggering this dissolve.
        val mapSwitchZ = remember { floatArrayOf(0f) }
        AnimatedContent(
            targetState = showingMapImage,
            transitionSpec = {
                mapSwitchZ[0] += 1f
                (
                    fadeIn(tween(durationMillis = 200, delayMillis = 90)) togetherWith
                        fadeOut(tween(durationMillis = 80, delayMillis = 300))
                    ).apply { targetContentZIndex = mapSwitchZ[0] }
            },
            label = "MapViewerPickerSwitch"
        ) { showImage ->
            if (showImage) {
                // Explicit full-screen Box so the overlay chrome below (top bar +
                // prev/next arrows) aligns within a real fillMaxSize BoxScope. The
                // AnimatedContent content scope alone doesn't give a full-height
                // box, which collapsed the arrows' CenterStart/CenterEnd toward the
                // top-left (regression from the viewer↔picker dissolve).
                androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize()) {
                // Outgoing image keeps rendering while it fades out; activeMapPath
                // can go null mid-fade (e.g. last map deleted), so guard, not !!.
                val imgPath = activeMapPath
                if (imgPath != null) {
                // Track screen size
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .onGloballyPositioned { coordinates ->
                            screenWidth = coordinates.size.width.toFloat()
                            screenHeight = coordinates.size.height.toFloat()
                        }
                ) {
                    // Show map with zoom and pan
                    // Crossfade for smooth transition when switching between images
                    // Pass live currentZoom/currentOffsetX/currentOffsetY so map image pans at same rate as markers
                    androidx.compose.animation.Crossfade(targetState = activeMapPath to mapUpdateVersion, animationSpec = androidx.compose.animation.core.tween(300), label = "mapImageCrossfade") { (path, version) ->
                    androidx.compose.runtime.key(path, version) {
                        MapZoomableImage(
                            imagePath = activeMapPath!!,
                            vaultId = vault.id,
                            initialZoom = currentZoom,
                            initialOffsetX = currentOffsetX,
                            initialOffsetY = currentOffsetY,
                            // Pinch/pan updates state but must NOT show chrome.
                            onStateChange = { zoom, x, y -> applyStateChange(zoom, x, y) },
                            onTouch = {
                                if (inVaultSessionMapTab && showingMapImage) {
                                    // Toggle: tap shows if hidden, hides if shown
                                    if (showTopBar) {
                                        showTopBar = false
                                    } else {
                                        updateInteraction()
                                    }
                                } else {
                                    updateInteraction()
                                }
                            },
                            onLongPress = { position ->
                                handleLongPress(position)
                            },
                            onImageSizeLoaded = { width, height ->
                                imageWidth = width
                                imageHeight = height
                            },
                            cacheBustVersion = mapUpdateVersion, // Pass version to bust cache
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    
                    // Marker overlay
                    if (imageWidth > 0 && imageHeight > 0 && screenWidth > 0 && screenHeight > 0) {
                        MapMarkerOverlay(
                            markers = filteredMarkers,
                            screenWidth = screenWidth,
                            screenHeight = screenHeight,
                            imageWidth = imageWidth,
                            imageHeight = imageHeight,
                            zoom = currentZoom,
                            offsetX = currentOffsetX,
                            offsetY = currentOffsetY,
                            onMarkerTap = { marker ->
                                val index = filteredMarkers.indexOfFirst { it.id == marker.id }
                                if (index >= 0) {
                                    focusedMarkerIndex = index
                                    mapViewModel.setFocusedMarkerId(vaultId, marker.id, mapSelection.selectedMapId)
                                }
                                selectedMarkerForMenu = marker
                                updateInteraction()
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    }

                }

                // Top bar: [ Remove Map ] [ Carousel ] [ Filter ] [ 💤 ]
                val mapTopBarVisible =
                    if (inVaultSessionMapTab && showingMapImage) showTopBar
                    else showTopBar || markers.isNotEmpty()
                MapSheetTopBar(
                    visible = mapTopBarVisible,
                    inVaultSessionMapTab = inVaultSessionMapTab,
                    hasAnyMap = hasAnyMap,
                    hasMultipleDisplays = hasMultipleDisplays,
                    markers = markers,
                    filteredMarkers = filteredMarkers,
                    selectedFilterStyle = selectedFilterStyle,
                    contentFocusRequester = contentFocusRequester,
                    bottomBarFocusRequester = bottomBarFocusRequester,
                    onDismiss = onDismiss,
                    updateInteraction = { updateInteraction() },
                    onShowMapSelection = mapSelection.onShowMapSelection,
                    onTakeScreenshot = onTakeScreenshot,
                    onCarouselMarkerTap = { marker ->
                        val index = filteredMarkers.indexOfFirst { it.id == marker.id }
                        if (index >= 0) {
                            focusedMarkerIndex = index
                            mapViewModel.setFocusedMarkerId(vaultId, marker.id, mapSelection.selectedMapId)
                        }
                        restoreMarkerView(marker)
                    },
                    onCarouselMarkerLongPress = { marker ->
                        selectedMarkerForMenu = marker
                        updateInteraction()
                    },
                    onShowFilterDialog = { showFilterDialog = true },
                )

                // Overlay prev/next arrows (touch-only, not focusable)
                if (inVaultSessionMapTab && showingMapImage && mapSelection.savedMaps.size > 1) {
                    MapSheetPrevNextArrows(
                        visible = mapTopBarVisible,
                        savedMaps = mapSelection.savedMaps,
                        selectedMapId = mapSelection.selectedMapId,
                        onSelectMap = mapSelection.onSelectMap,
                        updateInteraction = { updateInteraction() },
                    )
                }

                // Style selection dialog for creating new markers (when long-pressing on map)
                if (showStyleDialog && markerForStyleChange == null) {
                    MarkerStyleSelectionDialog(
                        onStyleSelected = { style -> createMarker(style) },
                        onDismiss = {
                            showStyleDialog = false
                            longPressPosition = null
                        }
                    )
                }
                
                // Limit reached dialog
                if (showLimitDialog) {
                    MapSheetLimitDialog(onDismiss = { showLimitDialog = false })
                }
                
                // Filter dialog
                if (showFilterDialog) {
                    MapSheetFilterDialog(
                        onSelectStyle = { style ->
                            selectedFilterStyle = style
                            showFilterDialog = false
                        },
                        onDismiss = { showFilterDialog = false }
                    )
                }
                
                // Marker management menu
                selectedMarkerForMenu?.let { marker ->
                    // Capture marker ID to avoid closure issues
                    val markerIdToDelete = marker.id
                    MarkerManagementMenu(
                        marker = marker,
                        onDelete = {
                            mapViewModel.deleteMarker(vaultId, markerIdToDelete, mapSelection.selectedMapId)
                            selectedMarkerForMenu = null
                        },
                        onChangeStyle = {
                            // Store marker for style change and show dialog
                            markerForStyleChange = marker
                            showStyleDialog = true
                            longPressPosition = null
                            selectedMarkerForMenu = null // Close management menu
                        },
                        onDeleteAll = {
                            showDeleteAllMarkersDialog = true
                        },
                        onDismiss = { selectedMarkerForMenu = null }
                    )
                }
                
                // Remove map confirmation dialog
                if (showRemoveMapConfirmation) {
                    MapSheetRemoveMapDialog(
                        vaultDisplayName = vault.displayName,
                        onConfirm = {
                            // Clear markers before removing map (markers are tied to specific map)
                            mapViewModel.deleteAllMarkers(vaultId, mapSelection.selectedMapId)

                            // Remove map pointer; on-disk files are user-owned in the home folder.
                            mapViewModel.removeMap(vaultId)

                            onBumpMapVersion()

                            showRemoveMapConfirmation = false
                            updateInteraction()
                        },
                        onDismiss = { showRemoveMapConfirmation = false }
                    )
                }
                
                // Delete all markers confirmation dialog
                if (showDeleteAllMarkersDialog) {
                    DeleteAllMarkersDialog(
                        markerCount = markers.size,
                        onDismiss = { 
                            showDeleteAllMarkersDialog = false
                            selectedMarkerForMenu = null
                        },
                        onConfirm = {
                            mapViewModel.deleteAllMarkers(vaultId, mapSelection.selectedMapId)
                            showDeleteAllMarkersDialog = false
                            selectedMarkerForMenu = null
                        }
                    )
                }
                
                // Style selection dialog (shown independently of management menu)
                if (showStyleDialog && markerForStyleChange != null) {
                    val markerToChange = markerForStyleChange!!
                    MarkerStyleSelectionDialog(
                        onStyleSelected = { style ->
                            mapViewModel.updateMarkerStyle(vaultId, markerToChange.id, style.id, mapSelection.selectedMapId)
                            showStyleDialog = false
                            markerForStyleChange = null
                        },
                        onDismiss = {
                            showStyleDialog = false
                            markerForStyleChange = null
                        }
                    )
                }
                }
                } // end full-screen overlay Box
            } else {
                // Map selection panel / assign page
                MapSheetAssignPage(
                    vault = vault,
                    vaultId = vaultId,
                    stg = stg,
                    mapSelection = mapSelection,
                    markersCount = markers.size,
                    mapViewModel = mapViewModel,
                    settingsViewModel = settingsViewModel,
                    libraryViewModel = libraryViewModel,
                    imagesViewMode = imagesViewMode,
                    imagesSortField = imagesSortField,
                    imagesSortDirection = imagesSortDirection,
                    imageSizes = imageSizes,
                    imageFilenames = imageFilenames,
                    hasMultipleDisplays = hasMultipleDisplays,
                    contentFocusRequester = contentFocusRequester,
                    bottomBarFocusRequester = bottomBarFocusRequester,
                    onAssignImage = onAssignImage,
                    onTakeScreenshot = onTakeScreenshot,
                    onPickBrowseUrl = { url ->
                        mainViewModel.setPendingUrlForBrowserTab(vaultId, url)
                        mainViewModel.setVaultSessionTab(vaultId, com.twinlore.app.domain.VaultTab.Browser)
                        onDismissAssignPage()
                    },
                    onOpenVgmapsPicker = { showVgmapsPicker = true },
                    updateInteraction = { updateInteraction() },
                )
            }
        }

        // VGMaps picker dialog. Placed outside the when{} so it renders regardless of which
        // branch is active (active-map view vs. assign page). The trigger lives in the
        // assign-page branch (MapBrowseMenu.onOpenVgmapsPicker), but must be visible here too.
        if (showVgmapsPicker) {
            VgmapsPickerScreen(
                gameName = vault.displayName,
                onDismiss = { showVgmapsPicker = false },
                onOpenHomepage = {
                    showVgmapsPicker = false
                    mainViewModel.setPendingUrlForBrowserTab(vaultId, "https://www.vgmaps.com/")
                    mainViewModel.setVaultSessionTab(vaultId, com.twinlore.app.domain.VaultTab.Browser)
                    onDismissAssignPage()
                },
                onDownload = { selected ->
                    showVgmapsPicker = false
                    onDismissAssignPage()
                    // TODO: mapViewModel.assignMapFile is viewModelScope-launched.
                    // If the user exits the vault session mid-save (after the picker dismissed), the
                    // viewModelScope is cancelled and the assignMapFile callback never fires, leaving
                    // suspendCancellableCoroutine hung. The downloader's cancel-button still works.
                    // Proper fix: move per-image save into a repository the @Singleton downloader can
                    // call directly, decoupling save lifecycle from the tab ViewModel.
                    vgmapsDownloader.start(selected) { cacheFile, caption ->
                        suspendCancellableCoroutine { cont ->
                            cont.invokeOnCancellation { cacheFile.delete() }
                            mapViewModel.assignMapFile(
                                vaultId = vaultId,
                                uri = Uri.fromFile(cacheFile),
                                title = caption,
                                context = context,
                            ) { saved ->
                                if (cont.isActive) cont.resume(saved != null) {}
                            }
                        }
                    }
                },
                onOpenDirectory = {
                    showVgmapsPicker = false
                    mainViewModel.setPendingUrlForBrowserTab(vaultId, "https://www.vgmaps.com/Atlas/index.htm")
                    mainViewModel.setVaultSessionTab(vaultId, com.twinlore.app.domain.VaultTab.Browser)
                },
            )
        }

        // VGMaps download banner (subscribes to downloader StateFlow, survives tab switches).
        // Also placed outside the when{} so it persists while the active-map view is showing.
        VgmapsDownloadBanner(
            downloader = vgmapsDownloader,
            onDismissSummary = { vgmapsDownloader.reset() },
        )
    }
}

// ---------------------------------------------------------------------------
// MapBottomSheetContent decomposition. These are mechanical extractions whose
// only purpose is register relief in the parent method: the monolithic
// composable exceeded the 255-register dex limit in debug builds (no R8),
// which made ART's verifier reject the whole class with a VerifyError.
// State stays hoisted in MapBottomSheetContent; values + lambdas are passed
// down unchanged.
// ---------------------------------------------------------------------------

@Composable
private fun MapSheetAssignPage(
    vault: com.twinlore.app.domain.Vault,
    vaultId: String,
    stg: MapSettingsState,
    mapSelection: MapSelectionCallbacks,
    markersCount: Int,
    mapViewModel: MapViewerScreenViewModel,
    settingsViewModel: SettingsViewModel,
    libraryViewModel: LibraryViewModel,
    imagesViewMode: FilePickerViewMode,
    imagesSortField: FilePickerSortField,
    imagesSortDirection: SortDirection,
    imageSizes: Map<String, Long>,
    imageFilenames: Map<String, String>,
    hasMultipleDisplays: Boolean,
    contentFocusRequester: FocusRequester?,
    bottomBarFocusRequester: FocusRequester?,
    onAssignImage: () -> Unit,
    onTakeScreenshot: () -> Unit,
    onPickBrowseUrl: (String) -> Unit,
    onOpenVgmapsPicker: () -> Unit,
    updateInteraction: () -> Unit,
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current

    // Focus comes from the bottom bar UP action via contentFocusRequester — no self-focus needed.

    val paletteAssign = LocalThemePalette.current
    val isDarkTheme = paletteAssign.isDark
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val accentColor = stg.focusColor
    // Match guide picker colors for consistent look
    val topMenuSurface = paletteAssign.background
    val topMenuOnSurface = paletteAssign.foreground
    val textColor = topMenuOnSurface
    val secondaryTextColor = topMenuOnSurface.copy(alpha = 0.65f)
    val headerCardBg = if (isDarkTheme) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)
    val buttonSurface = topMenuSurface.copy(alpha = 0.95f)
    val cardBg = topMenuSurface.copy(alpha = 0.95f)

    // State for delete and rename
    var mapToDelete by remember { mutableStateOf<SavedMap?>(null) }
    var mapToRename by remember { mutableStateOf<SavedMap?>(null) }

    // Selection mode state
    var selectionMode by remember(vaultId) { mutableStateOf(false) }
    val selectedIds = remember(vaultId) { mutableStateListOf<String>() }
    var showBulkDeleteConfirm by remember(vaultId) { mutableStateOf(false) }

    val artworkUri = com.twinlore.app.ui.utils.rememberResourceImage(vault.artworkResourceId)
    val noMapLogoModel = remember(artworkUri, vaultId) {
        artworkUri?.let { com.twinlore.app.ui.utils.ImagePathUtils.toImageData(it.toString(), vaultId) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter
    ) {
        if (noMapLogoModel != null) {
            val blurContext = LocalContext.current
            val artworkRid = vault.artworkResourceId
            val blurRequest = remember(noMapLogoModel) {
                ImageRequest.Builder(blurContext)
                    .data(noMapLogoModel)
                    .size(200)
                    .apply {
                        if (artworkRid != null) {
                            placeholderMemoryCacheKey("grid_art_${vaultId}_${artworkRid}_256x341_0")
                        }
                    }
                    .crossfade(180)
                    .build()
            }
            AsyncImage(
                model = blurRequest,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(radius = 24.dp)
                    .alpha(0.5f),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.50f))
            )
        }
        val mapPickerScrollState = rememberScrollState()
        var mapPickerViewportHeight by remember { mutableStateOf(0) }
        // In file pickers the bottom bar should always be visible for tab navigation.
        // The parent LaunchedEffect already forces chrome visible when !showingMapImage.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { mapPickerViewportHeight = it.height }
                .verticalScroll(mapPickerScrollState)
                .padding(horizontal = 16.dp)
                .padding(top = 14.dp, bottom = 80.dp)
        ) {
            MapSheetAssignHeaderCard(
                gameName = vault.displayName,
                currentSearchEngine = stg.currentSearchEngine,
                hasMultipleDisplays = hasMultipleDisplays,
                savedMapsEmpty = mapSelection.savedMaps.isEmpty(),
                contentFocusRequester = contentFocusRequester,
                bottomBarFocusRequester = bottomBarFocusRequester,
                headerCardBg = headerCardBg,
                buttonSurface = buttonSurface,
                accentColor = accentColor,
                textColor = textColor,
                onAssignImage = onAssignImage,
                onTakeScreenshot = onTakeScreenshot,
                onPickBrowseUrl = onPickBrowseUrl,
                onOpenVgmapsPicker = onOpenVgmapsPicker,
            )

            // Incremental marker-count cache: only the active map is re-counted on
            // marker edits; new map ids from a bulk save fetch just those ids.
            var mapMarkerCounts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
            val savedMapIds = remember(mapSelection.savedMaps) { mapSelection.savedMaps.map { it.id }.toSet() }
            LaunchedEffect(savedMapIds, markersCount, mapSelection.selectedMapId) {
                val needed = savedMapIds.filter { it !in mapMarkerCounts || it == mapSelection.selectedMapId }
                if (needed.isEmpty()) {
                    if (mapMarkerCounts.keys.any { it !in savedMapIds }) {
                        mapMarkerCounts = mapMarkerCounts.filterKeys { it in savedMapIds }
                    }
                    return@LaunchedEffect
                }
                val fresh = mapViewModel.getMarkerCounts(vaultId, needed)
                mapMarkerCounts = (mapMarkerCounts + fresh).filterKeys { it in savedMapIds }
            }

            // mapSelection.savedMaps is already filtered and sorted (sorted at MapTabContent scope).
            val filteredAndSortedMaps = mapSelection.savedMaps

            // "Saved images" section
            Spacer(modifier = Modifier.height(14.dp))
            if (selectionMode) {
                MapSelectionTopBar(
                    selectedCount = selectedIds.size,
                    totalVisible = filteredAndSortedMaps.size,
                    onClose = {
                        selectionMode = false
                        selectedIds.clear()
                    },
                    onToggleSelectAll = {
                        if (selectedIds.size == filteredAndSortedMaps.size) {
                            selectedIds.clear()
                        } else {
                            selectedIds.clear()
                            selectedIds.addAll(filteredAndSortedMaps.map { it.id })
                        }
                    },
                    onDeleteSelected = { showBulkDeleteConfirm = true },
                    accentColor = accentColor,
                    textColor = textColor
                )
            } else {
                FilePickerToolbar(
                    viewMode = imagesViewMode,
                    onViewModeChange = { settingsViewModel.setImagesPickerViewMode(it) },
                    sortField = imagesSortField,
                    sortDirection = imagesSortDirection,
                    onSortChange = { field, dir ->
                        settingsViewModel.setImagesPickerSortField(field)
                        settingsViewModel.setImagesPickerSortDirection(dir)
                    },
                    onSyncClick = { libraryViewModel.rescanLocalMediaForVault(vaultId) },
                    focusColor = accentColor,
                    textColor = textColor,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

            if (filteredAndSortedMaps.isEmpty()) {
                Text(
                    "No saved images. Use Add File or search online.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = secondaryTextColor
                )
            } else {
                val mapFocusRequesters = remember(filteredAndSortedMaps.size) {
                    filteredAndSortedMaps.map { FocusRequester() }
                }

                when (imagesViewMode) {
                    FilePickerViewMode.LIST -> {
                        filteredAndSortedMaps.forEachIndexed { index, map ->
                            MapSheetSavedImageListItem(
                                map = map,
                                focusRequester = mapFocusRequesters.getOrNull(index),
                                selectionMode = selectionMode,
                                selectedIds = selectedIds,
                                onSelectionModeChange = { selectionMode = it },
                                stg = stg,
                                mapPickerScrollState = mapPickerScrollState,
                                viewportHeightProvider = { mapPickerViewportHeight },
                                cardBg = cardBg,
                                accentColor = accentColor,
                                textColor = textColor,
                                secondaryTextColor = secondaryTextColor,
                                imageFilenames = imageFilenames,
                                imageSizes = imageSizes,
                                markerCount = mapMarkerCounts[map.id] ?: 0,
                                onSelectMap = mapSelection.onSelectMap,
                                updateInteraction = updateInteraction,
                                onRename = { mapToRename = it },
                                onDelete = { mapToDelete = it },
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    } // end LIST

                    FilePickerViewMode.GRID -> {
                        val gridColumns = 3
                        val chunkedMaps = filteredAndSortedMaps.chunked(gridColumns)
                        chunkedMaps.forEach { rowMaps ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                rowMaps.forEach { map ->
                                    MapSheetSavedImageGridCard(
                                        map = map,
                                        selectionMode = selectionMode,
                                        selectedIds = selectedIds,
                                        onSelectionModeChange = { selectionMode = it },
                                        stg = stg,
                                        cardBg = cardBg,
                                        accentColor = accentColor,
                                        textColor = textColor,
                                        secondaryTextColor = secondaryTextColor,
                                        imageSizes = imageSizes,
                                        onSelectMap = mapSelection.onSelectMap,
                                        updateInteraction = updateInteraction,
                                        onRename = { mapToRename = it },
                                        onDelete = { mapToDelete = it },
                                    )
                                }
                                // Fill remaining cells with empty spacers
                                repeat(gridColumns - rowMaps.size) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                    } // end GRID
                } // end when(imagesViewMode)
            }
        }

        // Rename map dialog
        mapToRename?.let { mapForRename ->
            MapSheetRenameDialog(
                map = mapForRename,
                accentColor = accentColor,
                onRename = mapSelection.onRenameSavedMap,
                onDismiss = { mapToRename = null }
            )
        }

        // Delete map confirmation dialog
        mapToDelete?.let { mapToRemove ->
            MapSheetDeleteMapDialog(
                map = mapToRemove,
                accentColor = accentColor,
                onConfirm = {
                    mapSelection.onDeleteSavedMap(mapToRemove.id)
                    mapToDelete = null
                },
                onDismiss = { mapToDelete = null }
            )
        }

        // Bulk delete confirmation dialog
        if (showBulkDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showBulkDeleteConfirm = false },
                title = { Text("Delete ${selectedIds.size} images?") },
                text = { Text("This cannot be undone.") },
                confirmButton = {
                    TextButton(onClick = {
                        val toDelete = selectedIds.toList()
                        showBulkDeleteConfirm = false
                        toDelete.forEach { id ->
                            mapSelection.onDeleteSavedMap(id)
                        }
                        android.widget.Toast.makeText(context, "Deleted ${toDelete.size} images", android.widget.Toast.LENGTH_SHORT).show()
                        selectionMode = false
                        selectedIds.clear()
                    }) { Text("Delete") }
                },
                dismissButton = {
                    TextButton(onClick = { showBulkDeleteConfirm = false }) { Text("Cancel") }
                }
            )
        }
    }
}

@Composable
private fun BoxScope.MapSheetTopBar(
    visible: Boolean,
    inVaultSessionMapTab: Boolean,
    hasAnyMap: Boolean,
    hasMultipleDisplays: Boolean,
    markers: List<MapMarker>,
    filteredMarkers: List<MapMarker>,
    selectedFilterStyle: MarkerStyle?,
    contentFocusRequester: FocusRequester?,
    bottomBarFocusRequester: FocusRequester?,
    onDismiss: () -> Unit,
    updateInteraction: () -> Unit,
    onShowMapSelection: () -> Unit,
    onTakeScreenshot: () -> Unit,
    onCarouselMarkerTap: (MapMarker) -> Unit,
    onCarouselMarkerLongPress: (MapMarker) -> Unit,
    onShowFilterDialog: () -> Unit,
) {
    val configuration = LocalConfiguration.current
    val isLandscapeMap = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val mapIconSize = if (isLandscapeMap) 24.dp else 20.dp
    // Match the guide-group / browser top bars (~40dp tall) that stack with this
    // one: icon + 4dp content padding, row min-height 40dp. Previously 12dp padding
    // plus a 44dp back button made this bar visibly taller than its neighbours.
    val mapIconPadding = 4.dp
    // Footprint of a NavigableIconButton (icon + 8dp focus ring + padding both sides),
    // used to size the placeholder that keeps alignment when the back button is hidden.
    val mapButtonFootprint = mapIconSize + 8.dp + mapIconPadding * 2
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            // Match the Guide/Browser top bar's elevation treatment (drop shadow, not
            // a tonal tint) so the two bars look identical when switching tabs.
            shadowElevation = 4.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 40.dp)
                    // 4dp horizontal matches the guide viewers (page-group / single-guide
                    // top bars) so the leading folder icon lands at the same x when
                    // switching between the Guide and Image tabs.
                    .padding(horizontal = 4.dp)
                    .then(
                        if (bottomBarFocusRequester != null) Modifier.focusProperties { down = bottomBarFocusRequester }
                        else Modifier
                    ),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back (to Library) - only when not in vault session Map tab (session uses bottom bar for back)
                if (!inVaultSessionMapTab) {
                NavigableIconButton(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    onClick = {
                        onDismiss()
                        updateInteraction()
                    },
                    focusId = "map_back",
                    iconSize = mapIconSize,
                    contentPadding = mapIconPadding
                )
                }
                // Maps folder icon - show map selection panel
                if (hasAnyMap) {
                    NavigableIconButton(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = "Image List",
                        onClick = {
                            onShowMapSelection()
                            updateInteraction()
                        },
                        focusId = "map_folder",
                        iconSize = mapIconSize,
                        contentPadding = mapIconPadding,
                        // Wire contentFocusRequester so D-pad UP from bottom bar
                        // reaches the map top bar in viewer mode.
                        externalFocusRequester = contentFocusRequester
                    )
                } else {
                    Spacer(modifier = Modifier.size(mapButtonFootprint))
                }

                // Screenshot button — capture the game on the secondary display,
                // next to the image-list (picker) button. Landscape + secondary
                // display only, mirroring the Add-image sheet's screenshot gate.
                if (hasMultipleDisplays && isLandscapeMap) {
                    NavigableIconButton(
                        imageVector = Icons.Filled.PhotoCamera,
                        contentDescription = "Take Screenshot",
                        onClick = {
                            onTakeScreenshot()
                            updateInteraction()
                        },
                        focusId = "map_screenshot",
                        iconSize = mapIconSize,
                        contentPadding = mapIconPadding
                    )
                }

                // Marker carousel (only show if markers exist)
                if (filteredMarkers.isNotEmpty()) {
                    MarkerCarousel(
                        markers = filteredMarkers,
                        maxMarkers = MapMarkerManager.MAX_MARKERS,
                        onMarkerTap = onCarouselMarkerTap,
                        onMarkerLongPress = onCarouselMarkerLongPress,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }

                // Filter icon (only show if markers exist); highlight when a filter is active
                if (markers.isNotEmpty()) {
                    NavigableIconButton(
                        imageVector = Icons.Default.FilterList,
                        contentDescription = if (selectedFilterStyle != null) "Filter active: ${selectedFilterStyle?.displayName ?: "All"}" else "Filter Markers",
                        onClick = {
                            onShowFilterDialog()
                            updateInteraction()
                        },
                        focusId = "map_filter",
                        iconSize = mapIconSize,
                        contentPadding = mapIconPadding
                    )
                }

            }
        }
    }
}

@Composable
private fun BoxScope.MapSheetPrevNextArrows(
    visible: Boolean,
    savedMaps: List<SavedMap>,
    selectedMapId: String?,
    onSelectMap: (String) -> Unit,
    updateInteraction: () -> Unit,
) {
    val currentMapIndex = savedMaps.indexOfFirst { it.id == selectedMapId }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.align(Alignment.CenterStart)
    ) {
        if (currentMapIndex > 0) {
            Surface(
                onClick = {
                    onSelectMap(savedMaps[currentMapIndex - 1].id)
                    updateInteraction()
                },
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(40.dp),
                shape = androidx.compose.foundation.shape.CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.ChevronLeft,
                        contentDescription = "Previous image"
                    )
                }
            }
        }
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.align(Alignment.CenterEnd)
    ) {
        if (currentMapIndex < savedMaps.size - 1) {
            Surface(
                onClick = {
                    onSelectMap(savedMaps[currentMapIndex + 1].id)
                    updateInteraction()
                },
                modifier = Modifier
                    .padding(end = 8.dp)
                    .size(40.dp),
                shape = androidx.compose.foundation.shape.CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Next image"
                    )
                }
            }
        }
    }
}

@Composable
private fun MapSheetLimitDialog(onDismiss: () -> Unit) {
    // Get focus color for button
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val focusColorState by settingsViewModel.focusColor.collectAsState(initial = FocusColor.CRIMSON)
    val cachedFocusColor = LocalCachedFocusColor.current?.value
    val focusColor = cachedFocusColor ?: focusColorState.color

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Maximum Markers Reached") },
        text = { Text("You can only have 100 markers. Please delete some markers to create new ones.") },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = focusColor
                )
            ) {
                Text("OK")
            }
        }
    )
}

@Composable
private fun MapSheetFilterDialog(
    onSelectStyle: (MarkerStyle?) -> Unit,
    onDismiss: () -> Unit,
) {
    // Get focus color and system theme
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val focusColorState by settingsViewModel.focusColor.collectAsState(initial = FocusColor.CRIMSON)
    val cachedFocusColor = LocalCachedFocusColor.current?.value
    val focusColor = cachedFocusColor ?: focusColorState.color
    val backgroundColor = MaterialTheme.colorScheme.surface

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = backgroundColor
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Filter by Style",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // All option
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onSelectStyle(null)
                        }
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("All Markers", style = MaterialTheme.typography.bodyMedium)
                }

                Divider()

                // Style options
                MarkerStyle.values().forEach { style ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelectStyle(style)
                            }
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val icon = when (style) {
                            MarkerStyle.PIN -> Icons.Default.Place
                            MarkerStyle.FLAG -> Icons.Default.Flag
                            MarkerStyle.STAR -> Icons.Default.RadioButtonUnchecked // Circle icon
                            MarkerStyle.CROSSHAIR -> Icons.Default.Person // Person icon
                        }
                        Icon(
                            imageVector = icon,
                            contentDescription = style.displayName,
                            modifier = Modifier.size(24.dp),
                            tint = focusColor
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(style.displayName, style = MaterialTheme.typography.bodyMedium)
                    }
                }

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = focusColor
                    )
                ) {
                    Text("Cancel")
                }
            }
        }
    }
}

@Composable
private fun MapSheetRemoveMapDialog(
    vaultDisplayName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val settingsViewModelForDialog: SettingsViewModel = hiltViewModel()
    val focusColorState by settingsViewModelForDialog.focusColor.collectAsState(initial = FocusColor.CRIMSON)
    val cachedFocusColor = LocalCachedFocusColor.current?.value
    val focusColor = cachedFocusColor ?: focusColorState.color
    val containerColor = MaterialTheme.colorScheme.surface
    val textColor = MaterialTheme.colorScheme.onSurface

    AlertDialog(
        containerColor = containerColor,
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Remove Image",
                color = textColor
            )
        },
        text = {
            Text(
                text = "Are you sure you want to remove the image for \"$vaultDisplayName\"?\n\nThis will also delete all markers. This action cannot be undone.",
                style = MaterialTheme.typography.bodyMedium,
                color = textColor
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = focusColor
                )
            ) {
                Text("Remove")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = focusColor
                )
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun MapSheetAssignHeaderCard(
    gameName: String,
    currentSearchEngine: SearchEngine,
    hasMultipleDisplays: Boolean,
    savedMapsEmpty: Boolean,
    contentFocusRequester: FocusRequester?,
    bottomBarFocusRequester: FocusRequester?,
    headerCardBg: Color,
    buttonSurface: Color,
    accentColor: Color,
    textColor: Color,
    onAssignImage: () -> Unit,
    onTakeScreenshot: () -> Unit,
    onPickBrowseUrl: (String) -> Unit,
    onOpenVgmapsPicker: () -> Unit,
) {
    val firstButtonFocusRequester = remember { FocusRequester() }
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = headerCardBg
    ) {
        Column(
            modifier = Modifier
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Three-button row: Add file + (optional) Screenshot + Browse Image.
            // Browse Image opens MapBrowseMenu with curated map sources.
            // Screenshot is hidden in portrait to keep the buttons legible —
            // three full-width buttons would otherwise crowd a narrow viewport.
            val mapPickerOrientation = LocalConfiguration.current.orientation
            val isPortrait = mapPickerOrientation == android.content.res.Configuration.ORIENTATION_PORTRAIT
            val showScreenshot = hasMultipleDisplays && !isPortrait
            val addFileFocusRequester = contentFocusRequester ?: firstButtonFocusRequester
            val screenshotFocusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
            val browseFocusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
            val buttonCount = if (showScreenshot) 3 else 2
            // Browse is always the last button — index 2 when Screenshot is
            // visible (3 buttons) or index 1 when it's not (2 buttons).
            val browseIndex = buttonCount - 1
            var focusedButtonIndex by remember { mutableStateOf(0) }
            var browseMenuExpanded by remember { mutableStateOf(false) }

            fun requesterFor(idx: Int): androidx.compose.ui.focus.FocusRequester = when {
                idx == 0 -> addFileFocusRequester
                idx == 1 && showScreenshot -> screenshotFocusRequester
                idx == browseIndex -> browseFocusRequester
                else -> error("requesterFor: unexpected idx=$idx, buttonCount=$buttonCount")
            }

            Row(
                modifier = Modifier
                    .width(IntrinsicSize.Max)
                    // Equalise button heights — "Browse Image"
                    // wraps at narrow widths, would otherwise
                    // leave Add-File / Screenshot stranded at
                    // single-line height. IntrinsicSize.Max +
                    // fillMaxHeight on each child stretches all
                    // buttons to the tallest one.
                    .height(IntrinsicSize.Max)
                    .onPreviewKeyEvent { keyEvent ->
                        val code = keyEvent.nativeKeyEvent.keyCode
                        val isDown = keyEvent.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN
                        when (code) {
                            android.view.KeyEvent.KEYCODE_DPAD_UP -> true
                            android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                                if (savedMapsEmpty && bottomBarFocusRequester != null) {
                                    if (isDown) try { bottomBarFocusRequester.requestFocus() } catch (_: Exception) {}
                                    true
                                } else false
                            }
                            android.view.KeyEvent.KEYCODE_DPAD_LEFT -> {
                                if (isDown && focusedButtonIndex > 0) {
                                    try { requesterFor(focusedButtonIndex - 1).requestFocus() } catch (_: Exception) {}
                                }
                                true
                            }
                            android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                if (isDown) {
                                    if (focusedButtonIndex < buttonCount - 1) {
                                        try { requesterFor(focusedButtonIndex + 1).requestFocus() } catch (_: Exception) {}
                                    } else if (bottomBarFocusRequester != null) {
                                        try { bottomBarFocusRequester.requestFocus() } catch (_: Exception) {}
                                    }
                                }
                                true
                            }
                            else -> false
                        }
                    },
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 0. Add File
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .onFocusChanged { if (it.hasFocus) focusedButtonIndex = 0 }
                ) {
                    NavigableButton(
                        text = "Add File",
                        onClick = onAssignImage,
                        modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                        focusId = "map_picker_add_file",
                        externalFocusRequester = addFileFocusRequester,
                        suppressInitialFocusSound = true,
                        containerColor = buttonSurface,
                        contentColor = accentColor,
                        icon = Icons.Filled.NoteAdd,
                    )
                }
                // 1. Screenshot (optional — secondary display + landscape only)
                if (showScreenshot) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .onFocusChanged { if (it.hasFocus) focusedButtonIndex = 1 }
                    ) {
                        NavigableButton(
                            text = "Screenshot",
                            onClick = onTakeScreenshot,
                            modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                            focusId = "map_picker_screenshot",
                            externalFocusRequester = screenshotFocusRequester,
                            containerColor = buttonSurface,
                            contentColor = accentColor,
                            icon = Icons.Filled.PhotoCamera,
                        )
                    }
                }
                // 2 (or 1). Browse
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .onFocusChanged { if (it.hasFocus) focusedButtonIndex = browseIndex }
                ) {
                    NavigableButton(
                        text = "Browse Image",
                        onClick = {
                            browseMenuExpanded = true
                        },
                        modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                        focusId = "map_picker_browse",
                        externalFocusRequester = browseFocusRequester,
                        containerColor = buttonSurface,
                        contentColor = textColor,
                        icon = Icons.Filled.TravelExplore,
                    )
                    MapBrowseMenu(
                        expanded = browseMenuExpanded,
                        gameName = gameName,
                        currentSearchEngine = currentSearchEngine,
                        buildImageSearchUrlForEngine = ::buildImageSearchUrlForEngine,
                        onPickUrl = { url ->
                            browseMenuExpanded = false
                            onPickBrowseUrl(url)
                        },
                        onOpenVgmapsPicker = {
                            browseMenuExpanded = false
                            onOpenVgmapsPicker()
                        },
                        onDismiss = { browseMenuExpanded = false },
                        onOpenSupportUrl = { url ->
                            browseMenuExpanded = false
                            onPickBrowseUrl(url)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun MapSheetSavedImageListItem(
    map: SavedMap,
    focusRequester: FocusRequester?,
    selectionMode: Boolean,
    selectedIds: androidx.compose.runtime.snapshots.SnapshotStateList<String>,
    onSelectionModeChange: (Boolean) -> Unit,
    stg: MapSettingsState,
    mapPickerScrollState: androidx.compose.foundation.ScrollState,
    viewportHeightProvider: () -> Int,
    cardBg: Color,
    accentColor: Color,
    textColor: Color,
    secondaryTextColor: Color,
    imageFilenames: Map<String, String>,
    imageSizes: Map<String, Long>,
    markerCount: Int,
    onSelectMap: (String) -> Unit,
    updateInteraction: () -> Unit,
    onRename: (SavedMap) -> Unit,
    onDelete: (SavedMap) -> Unit,
) {
    val view = LocalView.current
    val context = LocalContext.current
    val mapItemFocusRequester = focusRequester ?: remember { FocusRequester() }
    val mapInteractionSource = remember(map.id) { MutableInteractionSource() }
    val isMapItemFocused by mapInteractionSource.collectIsFocusedAsState()
    var itemBottomY by remember { mutableStateOf(0) }
    LaunchedEffect(isMapItemFocused) {
        if (isMapItemFocused) {
            FeedbackManager.play(view, FeedbackEvent.FOCUS)
            val viewportHeight = viewportHeightProvider()
            val bottomBarClearance = 200
            val itemBottomInViewport = itemBottomY - mapPickerScrollState.value
            if (itemBottomInViewport > viewportHeight - bottomBarClearance) {
                val scrollTarget = mapPickerScrollState.value + (itemBottomInViewport - (viewportHeight - bottomBarClearance))
                mapPickerScrollState.animateScrollTo(scrollTarget)
            }
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { coordinates ->
                itemBottomY = coordinates.positionInParent().y.toInt() + coordinates.size.height
            }
            .pointerInput(map.id, selectionMode) {
                detectTapGestures(
                    onLongPress = {
                        if (!selectionMode) {
                            onSelectionModeChange(true)
                            if (!selectedIds.contains(map.id)) selectedIds.add(map.id)
                            view.performHapticFeedback(stg.hapticFeedbackIntensity)
                        }
                    },
                    onTap = {
                        if (selectionMode) {
                            if (selectedIds.contains(map.id)) {
                                selectedIds.remove(map.id)
                                if (selectedIds.isEmpty()) onSelectionModeChange(false)
                            } else {
                                selectedIds.add(map.id)
                            }
                        } else {
                            onSelectMap(map.id)
                        }
                    }
                )
            },
        shape = RoundedCornerShape(8.dp),
        color = cardBg
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selectionMode) {
                Checkbox(
                    checked = selectedIds.contains(map.id),
                    onCheckedChange = { checked ->
                        if (checked) {
                            if (!selectedIds.contains(map.id)) selectedIds.add(map.id)
                        } else {
                            selectedIds.remove(map.id)
                            if (selectedIds.isEmpty()) onSelectionModeChange(false)
                        }
                    }
                )
                Spacer(Modifier.width(4.dp))
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(mapItemFocusRequester)
                    .focusable(interactionSource = mapInteractionSource)
                    .then(
                        if (isMapItemFocused) Modifier
                            .border(2.dp, accentColor, RoundedCornerShape(6.dp))
                        else Modifier
                    )
                    .onKeyEvent { keyEvent ->
                        if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                            keyEvent.nativeKeyEvent.keyCode in listOf(
                                KeyEvent.KEYCODE_DPAD_CENTER,
                                KeyEvent.KEYCODE_BUTTON_A,
                                KeyEvent.KEYCODE_ENTER,
                                KeyEvent.KEYCODE_SPACE
                            )
                        ) {
                            FeedbackManager.play(view, FeedbackEvent.CONFIRM)
                            if (stg.ledEnabled) {
                                ThorLedManager.setColor(
                                    stg.focusColor, true, stg.ledBrightness, stg.ledSaturationBoost, stg.ledTurnOffTime.inactivityMs
                                )
                            }
                            onSelectMap(map.id)
                            true
                        } else false
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Image thumbnail (cached 512px WebP via MapThumbnailCache)
                val imageModel = rememberMapThumbnail(map.resourceId)
                AsyncImage(
                    model = imageModel,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(4.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(10.dp))
                // Title + metadata
                Column(modifier = Modifier.weight(1f)) {
                    // Title with format badge
                    val mapFilename = imageFilenames[map.id]
                    val fileExt = mapFilename
                        ?.substringAfterLast('.', "")
                        ?.takeIf { it.isNotBlank() }
                        ?.uppercase()
                        ?: "IMG"
                    val (badgeLabel, badgeColor) = when (fileExt) {
                        "JPG", "JPEG" -> "JPG" to Color(0xFF2196F3)
                        "PNG" -> "PNG" to accentColor
                        "WEBP" -> "WEBP" to Color(0xFF9C27B0)
                        "GIF" -> "GIF" to Color(0xFFFF9800)
                        "BMP" -> "BMP" to Color(0xFF4CAF50)
                        else -> "IMG" to MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Text(
                        map.title,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = textColor,
                        modifier = Modifier.fillMaxWidth()
                    )
                    val relTime = remember(map.createdAt) {
                        val diff = System.currentTimeMillis() - map.createdAt.time
                        val min = diff / 60_000
                        val hrs = diff / 3_600_000
                        val days = diff / 86_400_000
                        when {
                            min < 1 -> "Just now"
                            min < 60 -> "${min}m ago"
                            hrs < 24 -> "${hrs}h ago"
                            days < 30 -> "${days}d ago"
                            else -> java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault()).format(map.createdAt)
                        }
                    }
                    val otherMetaParts = buildList {
                        add(relTime)
                        if (markerCount > 0) add("$markerCount marker${if (markerCount != 1) "s" else ""}")
                    }
                    val sizeBytes = imageSizes[map.id]
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            badgeLabel,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            color = badgeColor
                        )
                        Text(
                            " · " + otherMetaParts.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            color = secondaryTextColor,
                            modifier = Modifier.weight(1f)
                        )
                        if (sizeBytes != null && sizeBytes > 0L) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                android.text.format.Formatter.formatShortFileSize(context, sizeBytes),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                color = secondaryTextColor
                            )
                        }
                    }
                }
            }
            if (!selectionMode) {
                val kebabInteraction = remember(map.id) { MutableInteractionSource() }
                val isKebabFocused by kebabInteraction.collectIsFocusedAsState()
                var kebabMenuExpanded by remember(map.id) { mutableStateOf(false) }
                Box {
                    IconButton(
                        onClick = {
                            kebabMenuExpanded = true
                            updateInteraction()
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .focusable(interactionSource = kebabInteraction)
                            .then(if (isKebabFocused) Modifier.border(2.dp, accentColor, RoundedCornerShape(8.dp)) else Modifier)
                    ) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "More actions",
                            modifier = Modifier.size(18.dp),
                            tint = if (isKebabFocused) accentColor else textColor.copy(alpha = 0.6f)
                        )
                    }
                    DropdownMenu(
                        expanded = kebabMenuExpanded,
                        onDismissRequest = { kebabMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            onClick = {
                                FeedbackManager.play(view, FeedbackEvent.CONFIRM)
                                kebabMenuExpanded = false
                                onRename(map)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            onClick = {
                                FeedbackManager.play(view, FeedbackEvent.BACK)
                                kebabMenuExpanded = false
                                onDelete(map)
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RowScope.MapSheetSavedImageGridCard(
    map: SavedMap,
    selectionMode: Boolean,
    selectedIds: androidx.compose.runtime.snapshots.SnapshotStateList<String>,
    onSelectionModeChange: (Boolean) -> Unit,
    stg: MapSettingsState,
    cardBg: Color,
    accentColor: Color,
    textColor: Color,
    secondaryTextColor: Color,
    imageSizes: Map<String, Long>,
    onSelectMap: (String) -> Unit,
    updateInteraction: () -> Unit,
    onRename: (SavedMap) -> Unit,
    onDelete: (SavedMap) -> Unit,
) {
    val view = LocalView.current
    val context = LocalContext.current
    val gridFocusRequester = remember(map.id) { FocusRequester() }
    val gridInteractionSource = remember(map.id) { MutableInteractionSource() }
    val isGridFocused by gridInteractionSource.collectIsFocusedAsState()
    var gridKebabExpanded by remember(map.id) { mutableStateOf(false) }
    LaunchedEffect(isGridFocused) {
        if (isGridFocused) {
            FeedbackManager.play(view, FeedbackEvent.FOCUS)
        }
    }
    Surface(
        modifier = Modifier
            .weight(1f)
            .focusRequester(gridFocusRequester)
            .focusable(interactionSource = gridInteractionSource)
            .then(
                if (isGridFocused) Modifier.border(2.dp, accentColor, RoundedCornerShape(8.dp))
                else Modifier
            )
            .combinedClickable(
                interactionSource = gridInteractionSource,
                indication = null,
                onClick = {
                    if (selectionMode) {
                        if (selectedIds.contains(map.id)) {
                            selectedIds.remove(map.id)
                            if (selectedIds.isEmpty()) onSelectionModeChange(false)
                        } else {
                            selectedIds.add(map.id)
                        }
                    } else {
                        FeedbackManager.play(view, FeedbackEvent.CONFIRM)
                        onSelectMap(map.id)
                    }
                },
                onLongClick = {
                    view.performHapticFeedback(stg.hapticFeedbackIntensity)
                    if (!selectionMode) {
                        onSelectionModeChange(true)
                        if (!selectedIds.contains(map.id)) selectedIds.add(map.id)
                    }
                }
            )
            .onKeyEvent { keyEvent ->
                if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                    keyEvent.nativeKeyEvent.keyCode in listOf(
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_BUTTON_A,
                        KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_SPACE
                    )
                ) {
                    FeedbackManager.play(view, FeedbackEvent.CONFIRM)
                    if (stg.ledEnabled) {
                        ThorLedManager.setColor(
                            stg.focusColor, true, stg.ledBrightness, stg.ledSaturationBoost, stg.ledTurnOffTime.inactivityMs
                        )
                    }
                    if (selectionMode) {
                        if (selectedIds.contains(map.id)) {
                            selectedIds.remove(map.id)
                            if (selectedIds.isEmpty()) onSelectionModeChange(false)
                        } else {
                            selectedIds.add(map.id)
                        }
                    } else {
                        onSelectMap(map.id)
                    }
                    true
                } else false
            },
        shape = RoundedCornerShape(8.dp),
        color = cardBg
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(6.dp),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Image thumbnail filling square (cached 512px WebP via MapThumbnailCache)
                val imageModel = rememberMapThumbnail(map.resourceId)
                AsyncImage(
                    model = imageModel,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(4.dp)),
                    contentScale = ContentScale.Crop
                )
                // Bottom info: title+meta column on the left, tall kebab on the right.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            map.title,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            color = textColor,
                            modifier = Modifier.fillMaxWidth()
                        )
                        val sizeBytes = imageSizes[map.id]
                        if (sizeBytes != null && sizeBytes > 0L) {
                            Text(
                                android.text.format.Formatter.formatShortFileSize(context, sizeBytes),
                                style = MaterialTheme.typography.labelSmall,
                                color = secondaryTextColor
                            )
                        }
                    }
                    if (!selectionMode) {
                        IconButton(
                            onClick = {
                                gridKebabExpanded = true
                                updateInteraction()
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = "More actions",
                                modifier = Modifier.size(18.dp),
                                tint = textColor.copy(alpha = 0.6f)
                            )
                        }
                        DropdownMenu(
                            expanded = gridKebabExpanded,
                            onDismissRequest = { gridKebabExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Rename") },
                                onClick = {
                                    FeedbackManager.play(view, FeedbackEvent.CONFIRM)
                                    gridKebabExpanded = false
                                    onRename(map)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete") },
                                onClick = {
                                    FeedbackManager.play(view, FeedbackEvent.BACK)
                                    gridKebabExpanded = false
                                    onDelete(map)
                                }
                            )
                        }
                    }
                }
            }
            // Selection-mode tickbox at top-start (over image; scrim for contrast)
            if (selectionMode) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                        .background(
                            color = Color.Black.copy(alpha = 0.45f),
                            shape = CircleShape
                        )
                ) {
                    Checkbox(
                        checked = selectedIds.contains(map.id),
                        onCheckedChange = { checked ->
                            if (checked) {
                                if (!selectedIds.contains(map.id)) selectedIds.add(map.id)
                            } else {
                                selectedIds.remove(map.id)
                                if (selectedIds.isEmpty()) onSelectionModeChange(false)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun MapSheetRenameDialog(
    map: SavedMap,
    accentColor: Color,
    onRename: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var newName by remember { mutableStateOf(map.title) }
    val textFieldFocusRequester = remember { FocusRequester() }
    AlertDialog(
        containerColor = MaterialTheme.colorScheme.surface,
        onDismissRequest = onDismiss,
        title = { Text("Rename Image") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                NavigableTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = "Image name",
                    modifier = Modifier.fillMaxWidth(),
                    externalFocusRequester = textFieldFocusRequester,
                    onSubmit = {
                        val trimmed = newName.trim()
                        if (trimmed.isNotBlank()) {
                            onRename(map.id, trimmed)
                            onDismiss()
                        }
                    }
                )
                LaunchedEffect(Unit) {
                    textFieldFocusRequester.requestFocus()
                }
            }
        },
        confirmButton = {
            NavigableButton(
                text = "Rename",
                onClick = {
                    val trimmed = newName.trim()
                    if (trimmed.isNotBlank()) {
                        onRename(map.id, trimmed)
                        onDismiss()
                    }
                },
                enabled = newName.trim().isNotBlank(),
                containerColor = Color.Transparent,
                contentColor = accentColor,
                compact = true
            )
        },
        dismissButton = {
            NavigableButton(
                text = "Cancel",
                onClick = onDismiss,
                containerColor = Color.Transparent,
                contentColor = accentColor,
                compact = true
            )
        }
    )
}

@Composable
private fun MapSheetDeleteMapDialog(
    map: SavedMap,
    accentColor: Color,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        containerColor = MaterialTheme.colorScheme.surface,
        onDismissRequest = onDismiss,
        title = { Text("Delete Image") },
        text = {
            Text("Are you sure you want to delete \"${map.title}\"?\n\nThis will also delete all markers for this image. This action cannot be undone.")
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = accentColor)
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = accentColor)
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun MapSelectionTopBar(
    selectedCount: Int,
    totalVisible: Int,
    onClose: () -> Unit,
    onToggleSelectAll: () -> Unit,
    onDeleteSelected: () -> Unit,
    accentColor: androidx.compose.ui.graphics.Color,
    textColor: androidx.compose.ui.graphics.Color,
) {
    val selectAllState = when {
        selectedCount == 0 -> ToggleableState.Off
        selectedCount == totalVisible -> ToggleableState.On
        else -> ToggleableState.Indeterminate
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onClose) {
            Icon(Icons.Default.Close, contentDescription = "Exit selection", tint = textColor)
        }
        Text(
            "$selectedCount selected",
            style = MaterialTheme.typography.bodyMedium,
            color = textColor,
            modifier = Modifier.padding(start = 4.dp)
        )
        Spacer(Modifier.weight(1f))
        TriStateCheckbox(state = selectAllState, onClick = onToggleSelectAll)
        Text(
            "All",
            style = MaterialTheme.typography.bodySmall,
            color = textColor.copy(alpha = 0.8f),
            modifier = Modifier.padding(end = 8.dp)
        )
        IconButton(onClick = onDeleteSelected, enabled = selectedCount > 0) {
            Icon(
                Icons.Default.DeleteSweep,
                contentDescription = "Delete selected",
                tint = if (selectedCount > 0) accentColor else textColor.copy(alpha = 0.4f)
            )
        }
    }
}

/**
 * Coil memory-cache key for the main map image. MUST include the image
 * identity ([imagePath]) — the old key "map_<vaultId>_<version>" omitted it,
 * so two distinct screenshots in the same vault at the same version collided
 * on one key and Coil returned the first-cached bitmap for every later image
 * (the "tap a screenshot, see a previous one" bug). [cacheBustVersion] is kept
 * as a suffix so an in-place replace (same path, new bytes) still busts.
 * The `?…` query (the `?v=` cache-bust suffix added to the data) is stripped
 * so the same file maps to one stable key.
 */
internal fun mapImageMemoryCacheKey(
    vaultId: String?,
    imagePath: String,
    cacheBustVersion: Int,
): String = "map_${vaultId.orEmpty()}_${imagePath.substringBefore("?")}_$cacheBustVersion"

/**
 * Simplified zoomable image for map in bottom sheet
 */
@Composable
internal fun MapZoomableImage(
    imagePath: String,
    vaultId: String? = null,
    initialZoom: Float,
    initialOffsetX: Float,
    initialOffsetY: Float,
    onStateChange: (Float, Float, Float) -> Unit,
    onTouch: (() -> Unit)? = null,
    onLongPress: ((Offset) -> Unit)? = null,
    onImageSizeLoaded: ((Float, Float) -> Unit)? = null,
    cacheBustVersion: Int = 0, // Version number to bust cache when map is replaced
    /**
     * The decode finished, either way. False means Coil gave up on this image.
     *
     * The surface under the picture is black, so a failed decode is
     * indistinguishable from a picture that is simply dark: the fold panel
     * reported "the image is just black" for exactly that reason. Hosts that
     * pass this can say so instead. Null keeps the old silent behaviour.
     */
    onLoadResult: ((Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    // Get theme for background color
    val paletteZoom = LocalThemePalette.current
    val isDarkTheme = paletteZoom.isDark
    val backgroundColor = MaterialTheme.colorScheme.background
    
    // Initialize state from initial values - these are stable (captured by remember in parent)
    // The key() modifier in parent ensures this composable is only recreated when map path changes
    var scale by remember { mutableStateOf(initialZoom) }
    var offsetX by remember { mutableStateOf(initialOffsetX) }
    var offsetY by remember { mutableStateOf(initialOffsetY) }
    
    // Track the cacheBustVersion when we last synced to avoid unnecessary resets
    var lastSyncedCacheBustVersion by remember { mutableStateOf(cacheBustVersion) }
    
    
    // Double tap detection state
    var lastTapTime by remember { mutableStateOf(0L) }
    var lastTapPosition by remember { mutableStateOf(androidx.compose.ui.geometry.Offset(0f, 0f)) }
    val doubleTapTimeout = 300L // milliseconds
    var touchGestureInProgress by remember { mutableStateOf(false) }
    
    // Sync from initial when not in a touch gesture so controller-driven pan/zoom applies.
    LaunchedEffect(initialZoom, initialOffsetX, initialOffsetY, cacheBustVersion, touchGestureInProgress) {
        val isMapReplaced = cacheBustVersion != lastSyncedCacheBustVersion
        if (!touchGestureInProgress) {
            val validZoom = if (initialZoom > 0f && initialZoom <= 12f && !initialZoom.isNaN()) initialZoom else 1f
            val validOffsetX = if (!initialOffsetX.isNaN() && !initialOffsetX.isInfinite()) initialOffsetX else 0f
            val validOffsetY = if (!initialOffsetY.isNaN() && !initialOffsetY.isInfinite()) initialOffsetY else 0f
            scale = validZoom
            offsetX = validOffsetX
            offsetY = validOffsetY
            if (isMapReplaced) lastSyncedCacheBustVersion = cacheBustVersion
        }
    }
    
    LaunchedEffect(touchGestureInProgress) {
        if (!touchGestureInProgress) {
            if (cacheBustVersion != lastSyncedCacheBustVersion) {
                val validZoom = if (initialZoom > 0f && initialZoom <= 12f && !initialZoom.isNaN()) initialZoom else 1f
                val validOffsetX = if (!initialOffsetX.isNaN() && !initialOffsetX.isInfinite()) initialOffsetX else 0f
                val validOffsetY = if (!initialOffsetY.isNaN() && !initialOffsetY.isInfinite()) initialOffsetY else 0f
                scale = validZoom
                offsetX = validOffsetX
                offsetY = validOffsetY
                lastSyncedCacheBustVersion = cacheBustVersion
            } else {
                val zoomDiff = kotlin.math.abs(initialZoom - scale)
                val offsetXDiff = kotlin.math.abs(initialOffsetX - offsetX)
                val offsetYDiff = kotlin.math.abs(initialOffsetY - offsetY)
                if (zoomDiff > 0.1f || offsetXDiff > 50f || offsetYDiff > 50f) {
                    val validZoom = if (initialZoom > 0f && initialZoom <= 12f && !initialZoom.isNaN()) initialZoom else 1f
                    val validOffsetX = if (!initialOffsetX.isNaN() && !initialOffsetX.isInfinite()) initialOffsetX else 0f
                    val validOffsetY = if (!initialOffsetY.isNaN() && !initialOffsetY.isInfinite()) initialOffsetY else 0f
                    scale = validZoom
                    offsetX = validOffsetX
                    offsetY = validOffsetY
                }
            }
        }
    }
    
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    var longPressTriggered by remember { mutableStateOf(false) }
    var transformTimeoutJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor) // Background to hide browser screen behind map
            .pointerInput(Unit) {
                // Use Final pass so we don't block touch delivery: child (scroll/pan/zoom) gets the event in Main first.
                // We only update touchGestureInProgress after the child has handled the event, so scroll/pan stays smooth.
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Final)
                        when (event.type) {
                            PointerEventType.Press -> touchGestureInProgress = true
                            PointerEventType.Release -> touchGestureInProgress = false
                            else -> {}
                        }
                    }
                }
            }
            .pointerInput(scope) {
                detectTransformGestures(panZoomLock = false) { centroid, pan, zoom, _ ->
                    transformTimeoutJob?.cancel()
                    touchGestureInProgress = true
                    transformTimeoutJob = scope.launch {
                        kotlinx.coroutines.delay(200)
                        touchGestureInProgress = false
                    }
                    val centerX = size.width / 2f
                    val centerY = size.height / 2f
                    val newScale = (scale * zoom).coerceIn(1f, 12f)
                    val newOffsetX = (centroid.x - centerX) * (1f - newScale / scale) + offsetX * (newScale / scale) + pan.x
                    val newOffsetY = (centroid.y - centerY) * (1f - newScale / scale) + offsetY * (newScale / scale) + pan.y
                    scale = newScale
                    offsetX = newOffsetX
                    offsetY = newOffsetY
                    onStateChange(scale, offsetX, offsetY)
                }
            }
            .pointerInput(Unit) {
                val doubleTapDistanceThreshold = with(density) { 50.dp.toPx() } // pixels
                // Only provide onLongPress if callback is not null to avoid event consumption
                if (onLongPress != null) {
                    detectTapGestures(
                        onTap = { tapPosition ->
                            if (!longPressTriggered) {
                                val currentTime = System.currentTimeMillis()
                                val timeSinceLastTap = currentTime - lastTapTime
                                val distance = (tapPosition - lastTapPosition).getDistance()
                                
                                // Check if this is a double tap
                                if (timeSinceLastTap < doubleTapTimeout && distance < doubleTapDistanceThreshold) {
                                    // Double tap detected - toggle zoom
                                    val oldScale = scale
                                    val targetZoom = if (scale <= 1.1f) {
                                            3.5f
                                        } else {
                                            1f
                                        }
                                        
                                        val centerX = size.width / 2f
                                        val centerY = size.height / 2f
                                        
                                        val pointInImageX = (tapPosition.x - centerX - offsetX) / oldScale
                                        val pointInImageY = (tapPosition.y - centerY - offsetY) / oldScale
                                        
                                        scale = targetZoom.coerceIn(1f, 12f)
                                        
                                        if (targetZoom > 1f) {
                                            offsetX = -pointInImageX * scale
                                            offsetY = -pointInImageY * scale
                                        } else {
                                            offsetX = 0f
                                            offsetY = 0f
                                        }
                                        
                                        onStateChange(scale, offsetX, offsetY)
                                        
                                        lastTapTime = 0L
                                    } else {
                                        lastTapTime = currentTime
                                        lastTapPosition = tapPosition
                                    }
                                    
                                    onTouch?.invoke()
                            }
                            longPressTriggered = false
                        },
                        onLongPress = { position ->
                            longPressTriggered = true
                            onLongPress.invoke(position)
                        }
                    )
                } else {
                    detectTapGestures(
                        onTap = { tapPosition ->
                            val currentTime = System.currentTimeMillis()
                            val timeSinceLastTap = currentTime - lastTapTime
                            val distance = (tapPosition - lastTapPosition).getDistance()
                            
                            if (timeSinceLastTap < doubleTapTimeout && distance < doubleTapDistanceThreshold) {
                                val oldScale = scale
                                val targetZoom = if (scale <= 1.1f) {
                                        3.5f
                                    } else {
                                        1f
                                    }
                                    
                                    // Get screen center (like landing page: 50%, 50%)
                                    val centerX = size.width / 2f
                                    val centerY = size.height / 2f
                                    
                                    // Calculate the point in image coordinates before zoom
                                    val pointInImageX = (tapPosition.x - centerX - offsetX) / oldScale
                                    val pointInImageY = (tapPosition.y - centerY - offsetY) / oldScale
                                    
                                    // Update scale
                                    scale = targetZoom.coerceIn(1f, 12f)
                                    
                                    // Adjust offset so the tapped point moves to center of screen after zoom
                                    if (targetZoom > 1f) {
                                        offsetX = -pointInImageX * scale
                                        offsetY = -pointInImageY * scale
                                    } else {
                                        offsetX = 0f
                                        offsetY = 0f
                                    }
                                    
                                    onStateChange(scale, offsetX, offsetY)
                                    lastTapTime = 0L
                                } else {
                                    lastTapTime = currentTime
                                    lastTapPosition = tapPosition
                                }
                                
                                onTouch?.invoke()
                        }
                    )
                }
            }
    ) {
        val context = LocalContext.current
        // Resolve relative paths (e.g. "map/map.jpg") via ImagePathUtils so Coil/BitmapFactory can load from vault storage.
        // When vaultId is set, use only resolved data; do not fall back to raw path (Coil cannot load relative paths).
        val imageData = remember(imagePath, vaultId) {
            ImagePathUtils.toImageData(imagePath, vaultId)
                ?: if (imagePath.startsWith("content://") || imagePath.startsWith("file://")) imagePath else null
        }
        // Load image size asynchronously using resolved path/URI
        LaunchedEffect(imageData) {
            onImageSizeLoaded?.let { callback ->
                withContext(Dispatchers.IO) {
                    try {
                        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        when (imageData) {
                            is Uri -> {
                                context.contentResolver.openInputStream(imageData)?.use { stream ->
                                    BitmapFactory.decodeStream(stream, null, options)
                                }
                            }
                            is File -> BitmapFactory.decodeFile(imageData.absolutePath, options)
                            is String -> {
                                if (imageData.startsWith("content://")) {
                                    context.contentResolver.openInputStream(Uri.parse(imageData))?.use { stream ->
                                        BitmapFactory.decodeStream(stream, null, options)
                                    }
                                } else {
                                    BitmapFactory.decodeFile(imageData, options)
                                }
                            }
                            else -> Unit
                        }
                        val width = options.outWidth.toFloat()
                        val height = options.outHeight.toFloat()
                        if (width > 0 && height > 0) {
                            withContext(Dispatchers.Main) {
                                callback(width, height)
                            }
                        } else {
                            Log.e("MapZoomableImage", "Image size decode failed for resolved path (w=$width h=$height)")
                        }
                    } catch (e: Exception) {
                        Log.e("MapZoomableImage", "Failed to load image size", e)
                    }
                }
            }
        }
        
        // Add cache-busting parameter to force reload when map is replaced
        val cacheBustedImageData = remember(imageData, cacheBustVersion) {
            when (val d = imageData) {
                is Uri -> Uri.parse("${d}?v=$cacheBustVersion")
                is String -> if (d.startsWith("content://")) "$d?v=$cacheBustVersion" else d
                else -> d
            }
        }
        // When resolve failed (imageData null), show placeholder so Coil doesn't get invalid data
        val requestData = cacheBustedImageData ?: android.R.drawable.ic_menu_gallery
        // Through rememberUpdatedState: the request below is remembered across
        // recompositions, so a listener baked into it would hold the first
        // lambda forever.
        val currentOnLoadResult by androidx.compose.runtime.rememberUpdatedState(onLoadResult)
        val imageRequest = remember(requestData, cacheBustVersion) {
            ImageRequest.Builder(context)
                .data(requestData)
                .size(coil.size.Size(4096, 4096))
                .crossfade(false)
                .memoryCacheKey(mapImageMemoryCacheKey(vaultId, imagePath, cacheBustVersion))
                .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                // Map source is already on disk inside the vault folder — putting
                // a copy in Coil's image_cache is duplication. A single full-res
                // VGMaps poster (~160 MB) used to monopolise the disk cache.
                .diskCachePolicy(coil.request.CachePolicy.DISABLED)
                .listener(
                    onError = { _, _ -> currentOnLoadResult?.invoke(false) },
                    onSuccess = { _, _ -> currentOnLoadResult?.invoke(true) }
                )
                .build()
        }
        AsyncImage(
            model = imageRequest,
            contentDescription = "Map",
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY
                ),
            contentScale = ContentScale.Fit
        )
    }
}

