package com.twinlore.app.ui.screens

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import android.view.KeyEvent
import coil.compose.AsyncImage
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.twinlore.app.data.LandingEntry
import com.twinlore.app.data.SavedTabRepository
import com.twinlore.app.domain.GuideFileType
import com.twinlore.app.domain.SavedTab
import com.twinlore.app.domain.Vault
import com.twinlore.app.ui.MainViewModel
import com.twinlore.app.ui.navigation.NavigableButton
import com.twinlore.app.ui.utils.performHapticFeedback
import com.twinlore.app.ui.utils.runWithHapticAndLed
import com.twinlore.app.ui.utils.FeedbackEvent
import com.twinlore.app.ui.utils.FeedbackManager
import com.twinlore.app.ui.theme.getFocusContainer
import com.twinlore.app.ui.utils.ImagePathUtils
import com.twinlore.app.data.FocusColor
import com.twinlore.app.ui.LocalThemePalette
import com.twinlore.app.ui.LocalCachedFocusColor
import com.twinlore.app.data.HapticFeedbackIntensity
import com.twinlore.app.data.LedTurnOffTime
import com.twinlore.app.data.SearchEngine
import com.twinlore.app.domain.FilePickerViewMode
import com.twinlore.app.domain.FilePickerSortField
import com.twinlore.app.domain.SortDirection

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import dagger.hilt.EntryPoints
import com.twinlore.app.di.PageGroupEntryDaoEntryPoint
import kotlinx.coroutines.flow.map

/**
 * Full-screen Guide Picker: available guides (sorted by last open), Add file + Find buttons (Find opens curated source list — Neoseeker / Steam / Game manual / GameFAQs, with a series-specific row for Metroid / Zelda / Pokémon when the vault name matches), rename/delete per guide.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
internal fun GuidePickerContent(
    vaultId: String,
    effectiveVault: Vault?,
    bookmarks: List<SavedTab>,
    savedTabRepository: SavedTabRepository,
    mainViewModel: MainViewModel,
    landingEntriesList: List<LandingEntry>,
    recentSearchesForLanding: List<String>,
    currentSearchEngine: SearchEngine,
    buildSearchUrlForEngine: (String) -> String,
    toastMessage: MutableState<String?>,
    activePdfGuide: MutableState<SavedTab?>,
    currentBookmark: MutableState<SavedTab?>,
    showGuidePicker: MutableState<Boolean>,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    updateInteraction: () -> Unit,
    onOpenBookmark: (SavedTab) -> Unit,
    onRename: (SavedTab, String) -> Unit,
    onDelete: (SavedTab) -> Unit,
    onDismiss: () -> Unit,
    contentFocusRequester: FocusRequester? = null,
    isTabVisible: Boolean = true,
    bottomBarFocusRequester: FocusRequester? = null
) {
    val context = LocalContext.current
    val view = LocalView.current
    val pageGroupEntryDao = remember {
        EntryPoints.get(context.applicationContext, PageGroupEntryDaoEntryPoint::class.java).pageGroupEntryDao()
    }
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val libraryViewModel: LibraryViewModel = hiltViewModel()
    val viewMode by settingsViewModel.guidePickerViewMode.collectAsState(initial = FilePickerViewMode.LIST)
    val sortField by settingsViewModel.guidePickerSortField.collectAsState(initial = FilePickerSortField.DATE_ADDED)
    val sortDirection by settingsViewModel.guidePickerSortDirection.collectAsState(initial = SortDirection.DESC)
    val showLinks by settingsViewModel.guidePickerShowLinks.collectAsState(initial = true)
    val focusColorState by settingsViewModel.focusColor.collectAsState(initial = FocusColor.CRIMSON)
    val focusSoundEnabled by settingsViewModel.focusSoundEnabled.collectAsState(initial = true)
    val cachedFocusColor = LocalCachedFocusColor.current?.value
    val focusColor = cachedFocusColor ?: focusColorState.color
    val hapticFeedbackIntensity by settingsViewModel.hapticFeedbackIntensity.collectAsState(initial = HapticFeedbackIntensity.MEDIUM)
    val ledEnabled by settingsViewModel.ledEnabled.collectAsState(initial = true)
    val ledBrightness by settingsViewModel.ledBrightness.collectAsState(initial = 127)
    val ledSaturationBoost by settingsViewModel.ledSaturationBoost.collectAsState(initial = 0.75f)
    val ledTurnOffTime by settingsViewModel.ledTurnOffTime.collectAsState(initial = LedTurnOffTime.SEC_15)
    val palette = LocalThemePalette.current
    val isDarkTheme = palette.isDark
    // Match PDF/HTML guide top bar (not MaterialTheme.surface — Dialog overlays can stay light incorrectly)
    val topMenuSurface = palette.background
    val topMenuOnSurface = palette.foreground
    val textColor = topMenuOnSurface
    val secondaryTextColor = topMenuOnSurface.copy(alpha = 0.65f)
    val cardBg = topMenuSurface.copy(alpha = 0.95f)
    val buttonSurface = topMenuSurface.copy(alpha = 0.95f)

    var bookmarkToRename by remember { mutableStateOf<SavedTab?>(null) }
    var bookmarkToDelete by remember { mutableStateOf<SavedTab?>(null) }

    var selectionMode by remember(vaultId) { mutableStateOf(false) }
    val selectedIds = remember(vaultId) { mutableStateListOf<String>() }
    var showBulkDeleteConfirm by remember(vaultId) { mutableStateOf(false) }

    // Show toast when local media bind completes
    androidx.compose.runtime.LaunchedEffect(Unit) {
        libraryViewModel.localBindToast.collect { msg ->
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    // Use parent's contentFocusRequester when available, fall back to local.
    val guidePickerFirstButtonRequester = contentFocusRequester ?: remember { FocusRequester() }

    // No auto-focus: focus stays on the bottom bar when switching to Guide tab.
    // User navigates UP from bottom bar to reach picker content via D-pad.

    val gameName = effectiveVault?.displayName?.trim() ?: ""

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val displayName = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: uri.lastPathSegment ?: "guide"
        val mimeType = context.contentResolver.getType(uri) ?: ""
        val isPdf = displayName.endsWith(".pdf", ignoreCase = true) || mimeType == "application/pdf"
        val isTxt = displayName.endsWith(".txt", ignoreCase = true) || mimeType == "text/plain"
        val isMht = displayName.endsWith(".mht", ignoreCase = true) || displayName.endsWith(".mhtml", ignoreCase = true) || mimeType == "multipart/related" || mimeType == "message/rfc822" || mimeType == "application/x-mimearchive"
        val isEpub = displayName.endsWith(".epub", ignoreCase = true) || mimeType == "application/epub+zip"
        val isZip = displayName.endsWith(".zip", ignoreCase = true) ||
            mimeType == "application/zip" || mimeType == "application/x-zip-compressed"
        coroutineScope.launch(Dispatchers.IO) {
            try {
                when {
                    isPdf -> {
                        // Stream PDF to temp file to avoid OOM on large files (no size limit — pdf.js renders on-demand from disk)
                        val tempFile = java.io.File(context.cacheDir, "temp_pdf_import_${System.currentTimeMillis()}.pdf")
                        try {
                            withContext(Dispatchers.Main) { toastMessage.value = "Importing PDF..." }
                            val inputStream = context.contentResolver.openInputStream(uri)
                            if (inputStream == null) {
                                withContext(Dispatchers.Main) { toastMessage.value = "Failed to open PDF file" }
                                return@launch
                            }
                            inputStream.use { input -> tempFile.outputStream().buffered().use { output -> input.copyTo(output) } }
                            val result = savedTabRepository.createPdfGuideFromFile(vaultId, tempFile, displayName)
                            withContext(Dispatchers.Main) {
                                result.onSuccess { savedTab ->
                                    toastMessage.value = "Guide added: ${savedTab.title}"
                                    // Auto-open the imported PDF
                                    activePdfGuide.value = savedTab
                                    currentBookmark.value = null
                                    showGuidePicker.value = false
                                }
                                result.onFailure { toastMessage.value = it.message ?: "Failed to add PDF" }
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                toastMessage.value = "Failed to import PDF: ${e.message ?: "Unknown error"}"
                            }
                        } finally {
                            tempFile.delete()
                        }
                    }
                    isTxt -> {
                        val content = context.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() } ?: return@launch
                        val result = savedTabRepository.createTxtGuide(vaultId, content, displayName)
                        withContext(Dispatchers.Main) {
                            result.onSuccess { toastMessage.value = "Guide added: ${it.title}" }
                                .onFailure { toastMessage.value = it.message ?: "Failed to add TXT" }
                        }
                    }
                    isMht -> {
                        val tempFile = java.io.File(context.cacheDir, "temp_mht_import_${System.currentTimeMillis()}.mht")
                        try {
                            withContext(Dispatchers.Main) { toastMessage.value = "Importing MHT\u2026" }
                            val inputStream = context.contentResolver.openInputStream(uri)
                            if (inputStream == null) {
                                withContext(Dispatchers.Main) { toastMessage.value = "Failed to open MHT file" }
                                return@launch
                            }
                            inputStream.use { input -> tempFile.outputStream().buffered().use { output -> input.copyTo(output) } }
                            // Extract the original URL from the MHT Content-Location header
                            val originalUrl = com.twinlore.app.data.MhtParser().extractOriginalUrl(tempFile) ?: ""
                            val result = savedTabRepository.createMhtGuide(vaultId, tempFile, displayName, url = originalUrl)
                            // TODO: warm the MHT cache via ResourceResolver if this ever needs to open instantly.
                            withContext(Dispatchers.Main) {
                                result.onSuccess { savedTab ->
                                    toastMessage.value = "Guide added: ${savedTab.title}"
                                    onOpenBookmark(savedTab)
                                }
                                result.onFailure { toastMessage.value = it.message ?: "Failed to add MHT" }
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                toastMessage.value = "Failed to import MHT: ${e.message ?: "Unknown error"}"
                            }
                        } finally {
                            tempFile.delete()
                        }
                    }
                    isEpub -> {
                        val tempFile = java.io.File(context.cacheDir, "temp_epub_import_${System.currentTimeMillis()}.epub")
                        try {
                            withContext(Dispatchers.Main) { toastMessage.value = "Importing EPUB…" }
                            val inputStream = context.contentResolver.openInputStream(uri)
                            if (inputStream == null) {
                                withContext(Dispatchers.Main) { toastMessage.value = "Failed to open EPUB file" }
                                return@launch
                            }
                            inputStream.use { input -> tempFile.outputStream().buffered().use { output -> input.copyTo(output) } }
                            val result = savedTabRepository.createEpubGuide(vaultId, tempFile, displayName)
                            withContext(Dispatchers.Main) {
                                result.onSuccess { savedTab ->
                                    toastMessage.value = "Guide added: ${savedTab.title}"
                                    onOpenBookmark(savedTab)
                                }
                                result.onFailure { toastMessage.value = it.message ?: "Failed to add EPUB" }
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                toastMessage.value = "Failed to import EPUB: ${e.message ?: "Unknown error"}"
                            }
                        } finally {
                            tempFile.delete()
                        }
                    }
                    isZip -> {
                        val raw = java.io.File(context.cacheDir, "temp_zip_import_${System.currentTimeMillis()}.zip")
                        val slim = java.io.File(context.cacheDir, "temp_zip_slim_${System.currentTimeMillis()}.zip")
                        try {
                            withContext(Dispatchers.Main) { toastMessage.value = "Importing…" }
                            val inputStream = context.contentResolver.openInputStream(uri)
                            if (inputStream == null) {
                                withContext(Dispatchers.Main) { toastMessage.value = "Failed to open file" }
                                return@launch
                            }
                            inputStream.use { input -> raw.outputStream().buffered().use { output -> input.copyTo(output) } }
                            when (val outcome = com.twinlore.app.data.codewheel.HtmlZipImporter.classify(raw, slim)) {
                                is com.twinlore.app.data.codewheel.HtmlZipImporter.Outcome.Bundle -> {
                                    val result = savedTabRepository.createHtmlZipGuide(vaultId, slim, displayName, outcome.fileType)
                                    withContext(Dispatchers.Main) {
                                        result.onSuccess { savedTab ->
                                            toastMessage.value = "Guide added: ${savedTab.title}"
                                            onOpenBookmark(savedTab)
                                        }
                                        result.onFailure { toastMessage.value = it.message ?: "Failed to add file" }
                                    }
                                }
                                com.twinlore.app.data.codewheel.HtmlZipImporter.Outcome.NotAnHtmlBundle -> {
                                    withContext(Dispatchers.Main) { toastMessage.value = "Not a viewable HTML bundle" }
                                }
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                toastMessage.value = "Failed to import: ${e.message ?: "Unknown error"}"
                            }
                        } finally {
                            raw.delete()
                            slim.delete()
                        }
                    }
                    else -> {
                        // Treat anything else as HTML — copy bytes into the home folder
                        // and create a SavedTab. Asset references inside the HTML may
                        // break (saved as a single .html file); MHT round-tripping
                        // through the browser's Save button is the way to capture
                        // assets inline.
                        val tempFile = java.io.File(context.cacheDir, "temp_html_import_${System.currentTimeMillis()}.html")
                        try {
                            withContext(Dispatchers.Main) { toastMessage.value = "Importing HTML…" }
                            val inputStream = context.contentResolver.openInputStream(uri)
                            if (inputStream == null) {
                                withContext(Dispatchers.Main) { toastMessage.value = "Failed to open HTML file" }
                                return@launch
                            }
                            inputStream.use { input -> tempFile.outputStream().buffered().use { output -> input.copyTo(output) } }
                            val result = savedTabRepository.createHtmlGuide(vaultId, tempFile, displayName)
                            withContext(Dispatchers.Main) {
                                result.onSuccess { savedTab ->
                                    toastMessage.value = "Guide added: ${savedTab.title}"
                                    onOpenBookmark(savedTab)
                                }
                                result.onFailure { toastMessage.value = it.message ?: "Failed to add HTML" }
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                toastMessage.value = "Failed to import HTML: ${e.message ?: "Unknown error"}"
                            }
                        } finally {
                            tempFile.delete()
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    toastMessage.value = e.message ?: "Failed to read file"
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val headerCardBg = if (isDarkTheme) {
            Color.White.copy(alpha = 0.08f)
        } else {
            Color.Black.copy(alpha = 0.06f)
        }
        val emptyStateArtworkUri = com.twinlore.app.ui.utils.rememberResourceImage(effectiveVault?.artworkResourceId)
        val emptyStateLogoModel = remember(emptyStateArtworkUri, vaultId) {
            emptyStateArtworkUri?.let { ImagePathUtils.toImageData(it.toString(), vaultId) }
        }
        if (emptyStateLogoModel != null) {
            val blurContext = LocalContext.current
            val artworkRid = effectiveVault?.artworkResourceId
            val blurRequest = remember(emptyStateLogoModel) {
                coil.request.ImageRequest.Builder(blurContext)
                    .data(emptyStateLogoModel)
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
        val pickerScrollState = rememberScrollState()
        // In file pickers the bottom bar should always be visible for tab navigation.
        // The parent (GuideBrowserScreen) already forces chrome visible when showGuidePicker=true.
        var pickerViewportHeight by remember { mutableStateOf(0) }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { pickerViewportHeight = it.height }
                .verticalScroll(pickerScrollState)
                .padding(horizontal = 16.dp)
                .padding(top = 14.dp, bottom = 80.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = headerCardBg
            ) {
                Column(
                    modifier = Modifier
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Two-button row: Add file + Find ▾.
                    // Find ▾ opens a DropdownMenu (GuideFindMenu) with curated sources.
                    val addFileFocusRequester = guidePickerFirstButtonRequester
                    val findFocusRequester = remember { FocusRequester() }
                    var focusedButtonIndex by remember { mutableStateOf(0) }
                    var findMenuExpanded by remember { mutableStateOf(false) }
                    Row(
                        modifier = Modifier
                            .width(IntrinsicSize.Max)
                            // Match button heights: when "Browse Guide" wraps to two
                            // lines at narrow widths, the Add-File button used to stay
                            // single-height and leave a noticeable height mismatch in
                            // the row. IntrinsicSize.Max picks the tallest child;
                            // fillMaxHeight on the two Boxes below stretches both
                            // children to that height.
                            .height(IntrinsicSize.Max)
                            .onPreviewKeyEvent { keyEvent ->
                                val code = keyEvent.nativeKeyEvent.keyCode
                                val isDown = keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN
                                when (code) {
                                    KeyEvent.KEYCODE_DPAD_UP -> true // Nothing above
                                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                                        if (bookmarks.isEmpty() && bottomBarFocusRequester != null) {
                                            if (isDown) try { bottomBarFocusRequester.requestFocus() } catch (_: Exception) {}
                                            true
                                        } else false
                                    }
                                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                                        if (isDown && focusedButtonIndex == 1) {
                                            try { addFileFocusRequester.requestFocus() } catch (_: Exception) {}
                                        }
                                        true
                                    }
                                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                        if (isDown) {
                                            if (focusedButtonIndex == 0) {
                                                try { findFocusRequester.requestFocus() } catch (_: Exception) {}
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
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .onFocusChanged { if (it.hasFocus) focusedButtonIndex = 0 }
                        ) {
                            NavigableButton(
                                text = "Add File",
                                onClick = {
                                    filePickerLauncher.launch(arrayOf("application/pdf", "text/plain", "text/html", "multipart/related", "message/rfc822", "application/x-mimearchive", "application/epub+zip", "application/zip", "application/octet-stream"))
                                    updateInteraction()
                                },
                                modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                                focusId = "guide_picker_pick_file",
                                externalFocusRequester = addFileFocusRequester,
                                suppressInitialFocusSound = true,
                                containerColor = buttonSurface,
                                contentColor = focusColor,
                                icon = Icons.Filled.NoteAdd,
                            )
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .onFocusChanged { if (it.hasFocus) focusedButtonIndex = 1 }
                        ) {
                            NavigableButton(
                                text = "Browse Guide",
                                onClick = {
                                    findMenuExpanded = true
                                    updateInteraction()
                                },
                                modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                                focusId = "guide_picker_find",
                                externalFocusRequester = findFocusRequester,
                                containerColor = buttonSurface,
                                contentColor = topMenuOnSurface,
                                icon = Icons.Filled.TravelExplore,
                            )
                            GuideFindMenu(
                                expanded = findMenuExpanded,
                                gameName = gameName,
                                buildSearchUrlForEngine = buildSearchUrlForEngine,
                                onPickUrl = { url ->
                                    // Don't call the outer picker's onDismiss() here. The
                                    // user is jumping to the Browser tab; the Guide tab's
                                    // picker should stay mounted (alpha=0 while Browser is
                                    // active) so tabbing back to Guide lands on the picker,
                                    // not on GuideScreen's stale legacy WebView shell
                                    // (showGuidePicker=false + currentBookmark=null +
                                    // activePdfGuide=null = empty old GuideBrowserTopBar).
                                    findMenuExpanded = false
                                    mainViewModel.setPendingUrlForBrowserTab(vaultId, url)
                                    mainViewModel.setVaultSessionTab(vaultId, com.twinlore.app.domain.VaultTab.Browser)
                                    updateInteraction()
                                },
                                onDismiss = { findMenuExpanded = false },
                                onOpenSupportUrl = { url ->
                                    // Same reason as onPickUrl above for not calling the
                                    // outer onDismiss(): keep the picker mounted so
                                    // returning to the Guide tab doesn't surface the
                                    // empty legacy viewer.
                                    findMenuExpanded = false
                                    mainViewModel.setPendingUrlForBrowserTab(vaultId, url)
                                    mainViewModel.setVaultSessionTab(vaultId, com.twinlore.app.domain.VaultTab.Browser)
                                    updateInteraction()
                                },
                                onDownloadCodeWheel = { entry ->
                                    // Route to the Browser tab: open the archive.org
                                    // details page and let the standard browser
                                    // download banner drive the zip download (which
                                    // still lands as a playable "wheel" guide).
                                    findMenuExpanded = false
                                    mainViewModel.openCodeWheelInBrowser(vaultId, entry)
                                    updateInteraction()
                                },
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(14.dp))

            // File type filter chips — always visible so the user sees every
            // supported type at a glance. Single-select model: All + one type
            // active at a time (null = All).
            var selectedFileType by remember { mutableStateOf<GuideFileType?>(null) }

            // Load scroll marker counts per bookmark — keyed on bookmark IDs so
            // metadata updates (lastAccessed) don't trigger a reload and flash
            var markerCounts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
            val bookmarkIds = remember(bookmarks) { bookmarks.map { it.id }.toSet() }
            androidx.compose.runtime.LaunchedEffect(bookmarkIds) {
                val counts = mutableMapOf<String, Int>()
                bookmarks.forEach { bm ->
                    try {
                        val markers = savedTabRepository.getScrollMarkers(vaultId, bm.id)
                        counts[bm.id] = markers.size
                    } catch (_: Exception) { }
                }
                markerCounts = counts
            }

            // Load file sizes for sort-by-size support. For GROUP rows the
            // displayed size is the sum of every entry's MHT resource size;
            // single-page rows fall through to the existing single-resource
            // path. Both are gathered with one batched DAO call to avoid an
            // N+1 query when a group has many entries.
            var fileSizes by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }
            androidx.compose.runtime.LaunchedEffect("fileSizes", bookmarkIds) {
                val sizes = mutableMapOf<String, Long>()
                // Collect: per-bookmark list of resource ids contributing to its size.
                val resourceIdsByBookmark = mutableMapOf<String, List<String>>()
                bookmarks.forEach { bm ->
                    if (GuideFileType.fromFileTypeColumn(bm.fileType) == GuideFileType.GROUP) {
                        try {
                            val entries = pageGroupEntryDao.getForGroup(bm.id)
                            // distinct(): anchor sub-entries (multiple ToC entries
                            // pointing at the same mhtResourceId) would otherwise
                            // count their shared MHT file once per entry, inflating
                            // the displayed size by 5-10x for guides with anchored ToCs.
                            val rids = entries.mapNotNull { it.mhtResourceId }.distinct()
                            if (rids.isNotEmpty()) resourceIdsByBookmark[bm.id] = rids
                        } catch (_: Exception) {}
                    } else {
                        bm.resourceId?.let { rid -> resourceIdsByBookmark[bm.id] = listOf(rid) }
                    }
                }
                // One round-trip for every resource referenced by every visible row.
                val allResourceIds = resourceIdsByBookmark.values.flatten().distinct()
                if (allResourceIds.isNotEmpty()) {
                    try {
                        val files = libraryViewModel.getResourceFiles(allResourceIds)
                        val sizeByRid = files.associate { it.id to it.sizeBytes }
                        resourceIdsByBookmark.forEach { (bmId, rids) ->
                            val total = rids.sumOf { sizeByRid[it] ?: 0L }
                            if (total > 0L) sizes[bmId] = total
                        }
                    } catch (_: Exception) {}
                }
                fileSizes = sizes
            }

            val filteredAndSortedBookmarks = remember(bookmarks, selectedFileType, showLinks, sortField, sortDirection, fileSizes) {
                bookmarks
                    .filter { bm ->
                        selectedFileType == null || GuideFileType.fromFileTypeColumn(bm.fileType) == selectedFileType
                    }
                    .filter { bm ->
                        showLinks || GuideFileType.fromFileTypeColumn(bm.fileType) != GuideFileType.LINK
                    }
                    .sortedWith(
                        when (sortField) {
                            FilePickerSortField.NAME -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.displayTitle }
                            FilePickerSortField.LAST_OPENED -> compareBy { it.lastAccessed }
                            FilePickerSortField.DATE_ADDED -> compareBy { it.createdAt }
                            FilePickerSortField.SIZE -> compareBy { fileSizes[it.id] ?: 0L }
                            FilePickerSortField.TYPE -> compareBy<SavedTab> { it.fileType }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.displayTitle }
                        }.let { if (sortDirection == SortDirection.DESC) it.reversed() else it }
                    )
            }

            if (selectionMode) {
                GuideSelectionTopBar(
                    selectedCount = selectedIds.size,
                    totalVisible = filteredAndSortedBookmarks.size,
                    onClose = {
                        selectionMode = false
                        selectedIds.clear()
                    },
                    onToggleSelectAll = {
                        if (selectedIds.size == filteredAndSortedBookmarks.size) {
                            selectedIds.clear()
                        } else {
                            selectedIds.clear()
                            selectedIds.addAll(filteredAndSortedBookmarks.map { it.id })
                        }
                    },
                    onDeleteSelected = { showBulkDeleteConfirm = true },
                    focusColor = focusColor,
                    textColor = textColor
                )
            } else {
            FilePickerToolbar(
                viewMode = viewMode,
                onViewModeChange = { settingsViewModel.setGuidePickerViewMode(it) },
                sortField = sortField,
                sortDirection = sortDirection,
                onSortChange = { field, dir ->
                    settingsViewModel.setGuidePickerSortField(field)
                    settingsViewModel.setGuidePickerSortDirection(dir)
                },
                onSyncClick = { libraryViewModel.rescanLocalMediaForVault(vaultId) },
                focusColor = focusColor,
                textColor = textColor,
                showLinks = showLinks,
                onToggleShowLinks = { settingsViewModel.setGuidePickerShowLinks(!showLinks) },
                extraContent = null,
            )
            } // end else (not selectionMode)
            Spacer(modifier = Modifier.height(8.dp))

            if (filteredAndSortedBookmarks.isEmpty()) {
                Text(
                    if (bookmarks.isEmpty()) "No saved guides. Use Search online or Pick file to add one."
                    else "No guides match the selected filter.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = secondaryTextColor
                )
            } else {
                when (viewMode) {
                    FilePickerViewMode.LIST -> {
                val bookmarkFocusRequesters = remember(filteredAndSortedBookmarks.size) {
                    filteredAndSortedBookmarks.map { androidx.compose.ui.focus.FocusRequester() }
                }
                filteredAndSortedBookmarks.forEachIndexed { index, bookmark ->
                    val fileType = GuideFileType.fromFileTypeColumn(bookmark.fileType)
                    val badgeLabel = fileType.displayLabel
                    val badgeColor = when (fileType) {
                        GuideFileType.PDF -> MaterialTheme.colorScheme.error
                        GuideFileType.TXT -> MaterialTheme.colorScheme.onSurfaceVariant
                        GuideFileType.LINK -> MaterialTheme.colorScheme.tertiary
                        GuideFileType.MHT -> focusColor
                        GuideFileType.EPUB -> focusColor
                        GuideFileType.GROUP -> MaterialTheme.colorScheme.secondary
                        GuideFileType.HTML -> focusColor
                        GuideFileType.WHEEL -> focusColor
                        GuideFileType.HTMLZIP -> focusColor
                    }
                    // For GROUP rows, observe (total entries, 1-based active position)
                    // reactively from the DB. The active entry id lives on the
                    // bookmark itself (read from the saved_tabs row that produced
                    // it), so changes there propagate via the bookmarks flow.
                    val groupCounts by remember(bookmark.id, bookmark.groupActiveEntryId) {
                        if (fileType == GuideFileType.GROUP) {
                            pageGroupEntryDao.observeForGroup(bookmark.id).map { entries ->
                                // Filter synthetic body-only-discovered entries:
                                // they're real saved pages but don't appear in
                                // the source ToC, so showing them inflates the
                                // displayed "N pages" count vs what the user sees
                                // in the in-app Contents view.
                                val displayEntries = entries.filter { !it.synthetic }
                                displayEntries.size to (displayEntries.indexOfFirst { it.id == bookmark.groupActiveEntryId } + 1)
                            }
                        } else {
                            kotlinx.coroutines.flow.flowOf(0 to 0)
                        }
                    }.collectAsState(initial = 0 to 0)
                    val groupPageCount = groupCounts.first
                    val groupActivePos = groupCounts.second
                    val bookmarkFocusRequester = bookmarkFocusRequesters.getOrNull(index)
                        ?: remember { androidx.compose.ui.focus.FocusRequester() }
                    val bookmarkInteractionSource = remember(bookmark.id) { MutableInteractionSource() }
                    val isBookmarkFocused by bookmarkInteractionSource.collectIsFocusedAsState()
                    // Track item position for focus-driven scrolling
                    var itemBottomY by remember { mutableStateOf(0) }
                    androidx.compose.runtime.LaunchedEffect(isBookmarkFocused) {
                        if (isBookmarkFocused) {
                            FeedbackManager.play(view, FeedbackEvent.FOCUS)
                            // Scroll so the focused item is well above the bottom bar (~160dp from screen bottom).
                            // itemBottomY is relative to the scrollable content; compare with viewport.
                            val viewportHeight = pickerViewportHeight
                            val bottomBarClearance = 200 // px
                            val itemBottomInViewport = itemBottomY - pickerScrollState.value
                            if (itemBottomInViewport > viewportHeight - bottomBarClearance) {
                                val scrollTarget = pickerScrollState.value + (itemBottomInViewport - (viewportHeight - bottomBarClearance))
                                pickerScrollState.animateScrollTo(scrollTarget)
                            }
                        }
                    }
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onGloballyPositioned { coordinates ->
                                itemBottomY = coordinates.positionInParent().y.toInt() + coordinates.size.height
                            }
                            .pointerInput(bookmark.id, selectionMode) {
                                detectTapGestures(
                                    onLongPress = {
                                        if (!selectionMode) {
                                            selectionMode = true
                                            if (!selectedIds.contains(bookmark.id)) selectedIds.add(bookmark.id)
                                            view.performHapticFeedback(hapticFeedbackIntensity)
                                        }
                                    },
                                    onTap = {
                                        if (selectionMode) {
                                            if (selectedIds.contains(bookmark.id)) {
                                                selectedIds.remove(bookmark.id)
                                                if (selectedIds.isEmpty()) selectionMode = false
                                            } else {
                                                selectedIds.add(bookmark.id)
                                            }
                                        } else if (fileType == GuideFileType.GROUP) {
                                            mainViewModel.navigateToPageGroupViewer(vaultId, bookmark.id)
                                        } else {
                                            if (bookmark.isCurrentContent) onDismiss() else onOpenBookmark(bookmark)
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
                                    checked = selectedIds.contains(bookmark.id),
                                    onCheckedChange = { checked ->
                                        if (checked) {
                                            if (!selectedIds.contains(bookmark.id)) selectedIds.add(bookmark.id)
                                        } else {
                                            selectedIds.remove(bookmark.id)
                                            if (selectedIds.isEmpty()) selectionMode = false
                                        }
                                    }
                                )
                                Spacer(Modifier.width(4.dp))
                            }
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .focusRequester(bookmarkFocusRequester)
                                    .focusable(interactionSource = bookmarkInteractionSource)
                                    .then(
                                        if (isBookmarkFocused) Modifier
                                            .border(2.dp, focusColor, RoundedCornerShape(6.dp))
                                            .padding(horizontal = 6.dp, vertical = 4.dp)
                                        else Modifier
                                    )
                                    .onKeyEvent { keyEvent ->
                                        if ((keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN || keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_UP) &&
                                            keyEvent.nativeKeyEvent.keyCode in listOf(
                                                KeyEvent.KEYCODE_DPAD_CENTER,
                                                KeyEvent.KEYCODE_BUTTON_A,
                                                KeyEvent.KEYCODE_ENTER,
                                                KeyEvent.KEYCODE_SPACE
                                            )
                                        ) {
                                            if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                                                FeedbackManager.play(view, FeedbackEvent.CONFIRM)
                                                if (ledEnabled) {
                                                    com.twinlore.app.thor.led.ThorLedManager.setColor(
                                                        focusColor, true, ledBrightness, ledSaturationBoost, ledTurnOffTime.inactivityMs
                                                    )
                                                }
                                                if (fileType == GuideFileType.GROUP) {
                                                    mainViewModel.navigateToPageGroupViewer(vaultId, bookmark.id)
                                                } else if (bookmark.isCurrentContent) {
                                                    onDismiss()
                                                } else {
                                                    onOpenBookmark(bookmark)
                                                }
                                                updateInteraction()
                                            }
                                            true
                                        } else false
                                    },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Title + metadata
                                Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    bookmark.displayTitle,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    color = textColor
                                )
                                // Metadata line: type + relative time + marker count
                                val relTime = remember(bookmark.lastAccessed) {
                                    val diff = System.currentTimeMillis() - bookmark.lastAccessed.time
                                    val min = diff / 60_000
                                    val hrs = diff / 3_600_000
                                    val days = diff / 86_400_000
                                    when {
                                        min < 1 -> "Just now"
                                        min < 60 -> "${min}m ago"
                                        hrs < 24 -> "${hrs}h ago"
                                        days < 30 -> "${days}d ago"
                                        else -> java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault()).format(bookmark.lastAccessed)
                                    }
                                }
                                val mCount = markerCounts[bookmark.id]
                                val otherMetaParts = buildList {
                                    add(relTime)
                                    if (fileType == GuideFileType.GROUP) {
                                        // GROUP rows surface the user's place in the group instead
                                        // of marker count — markers don't span entries today.
                                        if (groupActivePos > 0 && groupPageCount > 0) {
                                            add("$groupActivePos/$groupPageCount")
                                        }
                                    } else if (mCount != null && mCount > 0) {
                                        add("$mCount marker${if (mCount != 1) "s" else ""}")
                                    }
                                }
                                val sizeBytes = fileSizes[bookmark.id]
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // Colored type token first, then the rest in the muted color
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
                                    // Single render path: GROUP rows pre-sum entry sizes into
                                    // fileSizes during the LaunchedEffect above, so the same
                                    // formatter handles both kinds of rows.
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
                            val kebabInteraction = remember(bookmark.id) { MutableInteractionSource() }
                            val isKebabFocused by kebabInteraction.collectIsFocusedAsState()
                            var kebabMenuExpanded by remember(bookmark.id) { mutableStateOf(false) }
                            Box {
                                IconButton(
                                    onClick = {
                                        runWithHapticAndLed(view, hapticFeedbackIntensity, ledEnabled, focusColor, ledBrightness, ledSaturationBoost, ledTurnOffTime.inactivityMs) {
                                            kebabMenuExpanded = true
                                        }
                                        updateInteraction()
                                    },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .focusable(interactionSource = kebabInteraction)
                                        .then(if (isKebabFocused) Modifier.border(2.dp, focusColor, RoundedCornerShape(8.dp)) else Modifier)
                                ) {
                                    Icon(
                                        Icons.Default.MoreVert,
                                        contentDescription = "More actions",
                                        modifier = Modifier.size(18.dp),
                                        tint = if (isKebabFocused) focusColor else textColor.copy(alpha = 0.6f)
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
                                            bookmarkToRename = bookmark
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Delete") },
                                        onClick = {
                                            FeedbackManager.play(view, FeedbackEvent.BACK)
                                            kebabMenuExpanded = false
                                            bookmarkToDelete = bookmark
                                        }
                                    )
                                }
                            }
                            } // end if (!selectionMode)
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
                    } // end LIST

                    FilePickerViewMode.GRID -> {
                        // Manual grid using chunked rows to avoid LazyVerticalGrid nesting issues
                        val gridColumns = 3
                        val chunkedBookmarks = filteredAndSortedBookmarks.chunked(gridColumns)
                        chunkedBookmarks.forEach { rowBookmarks ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                rowBookmarks.forEach { bookmark ->
                                    val fileType = GuideFileType.fromFileTypeColumn(bookmark.fileType)
                                    val badgeLabel = fileType.displayLabel
                                    val badgeColor = when (fileType) {
                                        GuideFileType.PDF -> MaterialTheme.colorScheme.error
                                        GuideFileType.TXT -> MaterialTheme.colorScheme.onSurfaceVariant
                                        GuideFileType.LINK -> MaterialTheme.colorScheme.tertiary
                                        GuideFileType.MHT -> focusColor
                                        GuideFileType.EPUB -> focusColor
                                        GuideFileType.GROUP -> MaterialTheme.colorScheme.secondary
                                        GuideFileType.HTML -> focusColor
                                        GuideFileType.WHEEL -> focusColor
                                        GuideFileType.HTMLZIP -> focusColor
                                    }
                                    val groupCounts by remember(bookmark.id, bookmark.groupActiveEntryId) {
                                        if (fileType == GuideFileType.GROUP) {
                                            pageGroupEntryDao.observeForGroup(bookmark.id).map { entries ->
                                                // Filter synthetic — see list-mode counterpart above.
                                                val displayEntries = entries.filter { !it.synthetic }
                                                displayEntries.size to (displayEntries.indexOfFirst { it.id == bookmark.groupActiveEntryId } + 1)
                                            }
                                        } else {
                                            kotlinx.coroutines.flow.flowOf(0 to 0)
                                        }
                                    }.collectAsState(initial = 0 to 0)
                                    val groupPageCount = groupCounts.first
                                    val groupActivePos = groupCounts.second
                                    val gridFocusRequester = remember(bookmark.id) { FocusRequester() }
                                    val gridInteractionSource = remember(bookmark.id) { MutableInteractionSource() }
                                    val isGridFocused by gridInteractionSource.collectIsFocusedAsState()
                                    var gridKebabExpanded by remember(bookmark.id) { mutableStateOf(false) }
                                    androidx.compose.runtime.LaunchedEffect(isGridFocused) {
                                        if (isGridFocused) {
                                            FeedbackManager.play(view, FeedbackEvent.FOCUS)
                                        }
                                    }
                                    Surface(
                                        modifier = Modifier
                                            .weight(1f)
                                            // Fixed height so every guide grid card matches its row neighbors
                                            // *and* its row above/below — uniform grid regardless of title length.
                                            .height(96.dp)
                                            .focusRequester(gridFocusRequester)
                                            .focusable(interactionSource = gridInteractionSource)
                                            .then(
                                                if (isGridFocused) Modifier.border(2.dp, focusColor, RoundedCornerShape(8.dp))
                                                else Modifier
                                            )
                                            .combinedClickable(
                                                interactionSource = gridInteractionSource,
                                                indication = null,
                                                onClick = {
                                                    if (selectionMode) {
                                                        if (selectedIds.contains(bookmark.id)) {
                                                            selectedIds.remove(bookmark.id)
                                                            if (selectedIds.isEmpty()) selectionMode = false
                                                        } else {
                                                            selectedIds.add(bookmark.id)
                                                        }
                                                    } else {
                                                        FeedbackManager.play(view, FeedbackEvent.CONFIRM)
                                                        if (fileType == GuideFileType.GROUP) {
                                                            mainViewModel.navigateToPageGroupViewer(vaultId, bookmark.id)
                                                            updateInteraction()
                                                        } else {
                                                            if (bookmark.isCurrentContent) onDismiss() else onOpenBookmark(bookmark)
                                                            updateInteraction()
                                                        }
                                                    }
                                                },
                                                onLongClick = {
                                                    view.performHapticFeedback(hapticFeedbackIntensity)
                                                    if (!selectionMode) {
                                                        selectionMode = true
                                                        if (!selectedIds.contains(bookmark.id)) selectedIds.add(bookmark.id)
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
                                                    if (ledEnabled) {
                                                        com.twinlore.app.thor.led.ThorLedManager.setColor(
                                                            focusColor, true, ledBrightness, ledSaturationBoost, ledTurnOffTime.inactivityMs
                                                        )
                                                    }
                                                    if (selectionMode) {
                                                        if (selectedIds.contains(bookmark.id)) {
                                                            selectedIds.remove(bookmark.id)
                                                            if (selectedIds.isEmpty()) selectionMode = false
                                                        } else {
                                                            selectedIds.add(bookmark.id)
                                                        }
                                                    } else if (fileType == GuideFileType.GROUP) {
                                                        mainViewModel.navigateToPageGroupViewer(vaultId, bookmark.id)
                                                    } else {
                                                        if (bookmark.isCurrentContent) onDismiss() else onOpenBookmark(bookmark)
                                                    }
                                                    updateInteraction()
                                                    true
                                                } else false
                                            },
                                        shape = RoundedCornerShape(8.dp),
                                        color = cardBg
                                    ) {
                                        Box(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .fillMaxHeight()
                                                    .padding(10.dp),
                                                horizontalAlignment = Alignment.Start,
                                                verticalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                // Title can wrap up to 3 lines; the fixed cell height keeps all cards uniform.
                                                Text(
                                                    bookmark.displayTitle,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    maxLines = 3,
                                                    overflow = TextOverflow.Ellipsis,
                                                    color = textColor,
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                                // Push the metadata row to the bottom of the fixed-height cell.
                                                Spacer(modifier = Modifier.weight(1f))
                                                // Metadata row: colored type + size on the left, kebab on the right.
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        badgeLabel,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = badgeColor
                                                    )
                                                    // GROUP: prepend active-position "Y/N" chip;
                                                    // size text shared with single-page rows below.
                                                    if (fileType == GuideFileType.GROUP && groupActivePos > 0 && groupPageCount > 0) {
                                                        Text(
                                                            " · $groupActivePos/$groupPageCount",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = secondaryTextColor
                                                        )
                                                    }
                                                    val sizeBytes = fileSizes[bookmark.id]
                                                    if (sizeBytes != null && sizeBytes > 0L) {
                                                        Text(
                                                            " · " + android.text.format.Formatter.formatShortFileSize(context, sizeBytes),
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = secondaryTextColor
                                                        )
                                                    }
                                                    Spacer(modifier = Modifier.weight(1f))
                                                    if (!selectionMode) {
                                                        Box {
                                                            IconButton(
                                                                onClick = {
                                                                    gridKebabExpanded = true
                                                                    updateInteraction()
                                                                },
                                                                modifier = Modifier.size(24.dp)
                                                            ) {
                                                                Icon(
                                                                    Icons.Default.MoreVert,
                                                                    contentDescription = "More actions",
                                                                    modifier = Modifier.size(16.dp),
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
                                                                        bookmarkToRename = bookmark
                                                                    }
                                                                )
                                                                DropdownMenuItem(
                                                                    text = { Text("Delete") },
                                                                    onClick = {
                                                                        FeedbackManager.play(view, FeedbackEvent.BACK)
                                                                        gridKebabExpanded = false
                                                                        bookmarkToDelete = bookmark
                                                                    }
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                            // Selection-mode tickbox at top-start
                                            if (selectionMode) {
                                                Checkbox(
                                                    checked = selectedIds.contains(bookmark.id),
                                                    onCheckedChange = { checked ->
                                                        if (checked) {
                                                            if (!selectedIds.contains(bookmark.id)) selectedIds.add(bookmark.id)
                                                        } else {
                                                            selectedIds.remove(bookmark.id)
                                                            if (selectedIds.isEmpty()) selectionMode = false
                                                        }
                                                    },
                                                    modifier = Modifier
                                                        .align(Alignment.TopStart)
                                                        .padding(2.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                                // Fill remaining cells in the last row with empty spacers
                                repeat(gridColumns - rowBookmarks.size) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                    } // end GRID
                } // end when(viewMode)
            }
        }
    }

    bookmarkToRename?.let { bookmark ->
        BookmarkRenameDialog(
            currentName = bookmark.title.ifBlank { bookmark.displayTitle },
            onDismiss = { bookmarkToRename = null },
            onConfirm = { newName ->
                onRename(bookmark, newName)
                bookmarkToRename = null
            }
        )
    }
    bookmarkToDelete?.let { bookmark ->
        DeleteTabDialog(
            tab = bookmark,
            title = "Delete Guide",
            onDismiss = { bookmarkToDelete = null },
            onConfirm = {
                onDelete(bookmark)
                bookmarkToDelete = null
            }
        )
    }
    if (showBulkDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showBulkDeleteConfirm = false },
            title = { Text("Delete ${selectedIds.size} guides?") },
            text = { Text("This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    val toDelete = selectedIds.toList()
                    showBulkDeleteConfirm = false
                    toDelete.forEach { id ->
                        val bm = bookmarks.firstOrNull { it.id == id } ?: return@forEach
                        onDelete(bm)
                    }
                    toastMessage.value = "Deleted ${toDelete.size} guides"
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

@Composable
private fun GuideSelectionTopBar(
    selectedCount: Int,
    totalVisible: Int,
    onClose: () -> Unit,
    onToggleSelectAll: () -> Unit,
    onDeleteSelected: () -> Unit,
    focusColor: Color,
    textColor: Color,
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
        TriStateCheckbox(
            state = selectAllState,
            onClick = onToggleSelectAll
        )
        Text(
            "All",
            style = MaterialTheme.typography.bodySmall,
            color = textColor.copy(alpha = 0.8f),
            modifier = Modifier.padding(end = 8.dp)
        )
        IconButton(
            onClick = onDeleteSelected,
            enabled = selectedCount > 0
        ) {
            Icon(
                Icons.Default.DeleteSweep,
                contentDescription = "Delete selected",
                tint = if (selectedCount > 0) focusColor else textColor.copy(alpha = 0.4f)
            )
        }
    }
}
