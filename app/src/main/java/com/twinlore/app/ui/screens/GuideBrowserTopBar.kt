package com.twinlore.app.ui.screens

import android.content.res.Configuration
import com.twinlore.app.domain.VaultSessionMode
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ShortText
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material.icons.filled.Cookie
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.twinlore.app.data.FocusColor
import com.twinlore.app.data.HapticFeedbackIntensity
import com.twinlore.app.data.LedTurnOffTime
import com.twinlore.app.data.SearchHistoryManager
import com.twinlore.app.di.SearchHistoryManagerEntryPoint
import com.twinlore.app.domain.ScrollMarker
import com.twinlore.app.domain.SavedTab
import com.twinlore.app.ui.navigation.NavigableIconButton
import com.twinlore.app.ui.navigation.NavigableTextField
import com.twinlore.app.ui.LocalCachedFocusColor
import com.twinlore.app.ui.theme.getContrastingTextColor
import com.twinlore.app.ui.theme.getFocusContainer
import com.twinlore.app.ui.utils.FeedbackEvent
import com.twinlore.app.ui.utils.FeedbackManager
import com.twinlore.app.ui.utils.runWithHapticAndLed
import dagger.hilt.EntryPoints
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.focusProperties
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.lerp as lerpDp

/**
 * Top bar with navigation controls per new PRD
 * Layout (Browser): [ ← ] [ Search / URL ] [ back ] [ fwd ] [ home ] [ refresh ] [ download (save) ] [ 💤 ]
 * Save as bookmark uses download icon and is far right before Sleep in Browser mode; no filled-star state.
 */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
internal fun GuideBrowserTopBar(
    currentUrl: String,
    urlInput: String,
    onUrlInputChange: (String) -> Unit,
                onNavigate: (String, Boolean, String) -> Unit, // input, isCompleteQuery, finalQuery (final search query to save)
    canGoBack: Boolean,
    canGoForward: Boolean,
    isOnLandingPage: () -> Boolean, // Function to check if we're on landing page
    onBack: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onForward: () -> Unit, // Forward button not shown in current UI (unused)
                onFavoriteClick: () -> Unit,
    onAddNewGuide: (() -> Unit)? = null, // In Guide session mode: add new guide (opens dialog or flow)
    onFavoriteLongPress: (() -> Unit)? = null, // Long-press handler for favorite menu
    onSaveAsFavorite: () -> Unit, // Save current page as bookmark (Browser: download icon far right)
    @Suppress("UNUSED_PARAMETER") isCurrentPageFavorite: Boolean = false, // Unused: no longer switch icon to filled star
    canSaveAsBookmark: Boolean = true, // false when viewing offline — disable save button
    @Suppress("UNUSED_PARAMETER") onFilledStarClick: (() -> Unit)? = null, // Unused: no longer used for remove-bookmark tap
    isSavingFavorite: Boolean = false, // Loading state for saving favorite
    saveProgress: SaveProgress? = null, // Progress during bookmark save
    onCancelSave: (() -> Unit)? = null, // Cancel ongoing save operation
    onRefresh: () -> Unit, // Reload current page (replaces pull-up-to-refresh)
    zoomLevel: Float = 1f,
    minZoomLevel: Float = 0.5f,
    onZoomLevelChange: ((Float) -> Unit)? = null,
    onResetZoom: (() -> Unit)? = null,
    onFindOnPage: () -> Unit,
    onOpenToc: (() -> Unit)? = null,
    hasFavorite: Boolean,
    appendGameName: Boolean = true, // Auto-append game name (shared with WebView YouTube search)
    onAppendGameNameChange: (Boolean) -> Unit = {}, // Callback when toggle changes
    urlFieldFocusRequester: androidx.compose.ui.focus.FocusRequester? = null,
    onUrlFieldFocused: (() -> Unit)? = null,
    onUrlFieldFocusChanged: ((Boolean) -> Unit)? = null, // New: callback for focus state changes
    gameName: String? = null, // Game name for search suggestions
    vaultId: String = "", // Vault ID for search history
    currentBookmark: SavedTab? = null, // Current bookmark for title display
    onToastMessage: ((String) -> Unit)? = null, // Callback to show toast messages
    scrollMarkers: List<ScrollMarker> = emptyList(), // Scroll markers for current bookmark (L1/R1)
    focusedScrollMarkerIndex: Int? = null,
    onScrollMarkerTap: (ScrollMarker) -> Unit = {}, // Scroll to marker
    onScrollMarkerLongPress: (ScrollMarker) -> Unit = {}, // Show menu (e.g. Delete all)
    scrollMarkersBarVisible: Boolean = true, // When false, scroll markers bar is hidden; toggle via top bar button
    onToggleScrollMarkersBar: () -> Unit = {},
    vaultSessionMode: VaultSessionMode? = null, // When set, top bar adapts for Guide or Browser tab in VaultSession
    // True only when this bar is the one that must carry the fold button; false
    // when the host screen is embedded in VaultSessionScreen and VaultSessionTopBar
    // is on screen rendering its own — without that, folding lands two identical
    // chevrons on screen at once (see FoldButton() call below). Defaults to false
    // so the duplicate is the case a new call site has to ask for, not the one it
    // gets by forgetting. Both existing call sites pass it explicitly, from
    // com.twinlore.app.ui.fold.shouldShowFoldButtonInHostTopBar.
    showFoldButton: Boolean = false,
    onBrowserBack: (() -> Unit)? = null, // In Browser session mode: go back in history
    onBrowserForward: (() -> Unit)? = null, // In Browser session mode: go forward in history
    onGoToLandingPage: (() -> Unit)? = null, // In Browser session mode: load landing page (reset to landing.css)
    showOpenInBrowserButton: Boolean = false, // When true (Guide + HTML bookmark with http URL), show "Open in Browser" button
    onOpenGuideInBrowser: (() -> Unit)? = null,
    guideThemeInjectionActive: Boolean = false,
    onToggleGuideThemeInjection: (() -> Unit)? = null,
    privacyBlockingEnabled: Boolean = true,
    onTogglePrivacyBlocking: () -> Unit = {},
    firstButtonFocusRequester: androidx.compose.ui.focus.FocusRequester? = null, // D-pad UP from bottom bar targets this
    bottomBarFocusRequester: androidx.compose.ui.focus.FocusRequester? = null, // D-pad DOWN from top bar goes to bottom bar
    zoomControlsFocusRequester: androidx.compose.ui.focus.FocusRequester? = null, // D-pad DOWN from top bar goes to zoom controls (if visible)
    browserTabCount: Int? = null,                       // Browser mode only: rendered when non-null
    onOpenBrowserTabSwitcher: (() -> Unit)? = null,     // Browser mode only: tap of [N] button
    tabOpenedPulse: SharedFlow<Unit>? = null,           // Browser mode only: pulse on link-click new tab
    modifier: Modifier = Modifier
) {
    // Get focus color and haptic/LED from settings
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val focusColorState by settingsViewModel.focusColor.collectAsState(initial = FocusColor.CRIMSON)
    val cachedFocusColor = LocalCachedFocusColor.current?.value
    val focusColor = cachedFocusColor ?: focusColorState.color
    val view = LocalView.current
    val hapticFeedbackIntensity by settingsViewModel.hapticFeedbackIntensity.collectAsState(initial = HapticFeedbackIntensity.MEDIUM)
    val ledEnabled by settingsViewModel.ledEnabled.collectAsState(initial = true)
    val ledBrightness by settingsViewModel.ledBrightness.collectAsState(initial = 127)
    val ledSaturationBoost by settingsViewModel.ledSaturationBoost.collectAsState(initial = 0.75f)
    val ledTurnOffTime by settingsViewModel.ledTurnOffTime.collectAsState(initial = LedTurnOffTime.SEC_15)
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    // Compact top bar: smaller in portrait so all icons fit; larger in landscape
    // Browser mode has fewer icons + a weighted Spacer, so portrait can use the
    // larger landscape size comfortably (was 20dp/4dp in portrait — too small
    // for thumb taps now that the layout has breathing room).
    val isBrowser = vaultSessionMode == VaultSessionMode.Browser
    val topBarIconSize = if (isLandscape || isBrowser) 24.dp else 20.dp
    // Tighter vertical padding so the bar doesn't pile dead space between
    // itself and the vault-session top bar above. The 24dp icon + 8dp focus
    // ring still has a comfortable 4dp halo with this value, and the row
    // works out to ~40dp instead of ~48dp.
    val topBarContentPadding = if (isLandscape || isBrowser) 4.dp else 4.dp
    val trailingButtonSize = if (isLandscape) 32.dp else 30.dp
    val trailingIconSize = if (isLandscape) 22.dp else 20.dp
    // Browser mode always collapses the search bar to an icon; expand on tap
    val compactSearchBar = vaultSessionMode == VaultSessionMode.Browser
    var searchBarExpanded by remember { mutableStateOf(false) }

    val dialogBackgroundColor = MaterialTheme.colorScheme.surface
    val dialogTextColor = MaterialTheme.colorScheme.onSurface
    
    // Inject SearchHistoryManager
    val context = LocalContext.current
            val searchHistoryManager: SearchHistoryManager = remember {
                EntryPoints.get(
                    context.applicationContext,
                    SearchHistoryManagerEntryPoint::class.java
                ).searchHistoryManager()
            }
    
    // Helper function to check if input already contains game name (improved detection)
    fun inputContainsGameName(input: String, gameName: String?): Boolean {
        if (gameName.isNullOrBlank() || input.isBlank()) return false
        val inputLower = input.lowercase().trim()
        val gameNameLower = gameName.lowercase().trim()
        // Check for exact match or word-boundary match
        val gameNameWords = gameNameLower.split("\\s+".toRegex())
        // Check if all words of game name appear in input (in order)
        var lastIndex = -1
        for (word in gameNameWords) {
            val index = inputLower.indexOf(word, lastIndex + 1)
            if (index == -1) return false
            lastIndex = index
        }
        return true
    }
    
    // Search suggestions state
    var isUrlFieldFocusedLocal by remember { mutableStateOf(false) }
    var focusedSuggestionIndex by remember { mutableStateOf(-1) } // -1 = no suggestion focused
    var shouldMoveFocusToSuggestion by remember { mutableStateOf(false) } // Flag to trigger focus move
    var showDeleteDialog by remember { mutableStateOf<String?>(null) } // Query to delete (null = dialog hidden)
    var refreshHistoryTrigger by remember { mutableIntStateOf(0) } // Trigger to refresh history
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()
    
    // Get search history for current vault (refresh when vaultId changes or trigger is incremented)
    val searchHistory = remember(vaultId, refreshHistoryTrigger) {
        if (vaultId.isNotBlank()) {
            searchHistoryManager.getSearchHistory(vaultId)
        } else {
            emptyList()
        }
    }
    
    // Use search history instead of hardcoded suggestions
    val allSuggestions = remember(searchHistory) {
        searchHistory
    }
    
    val suggestionFocusRequesters = remember(allSuggestions.size) {
        List(allSuggestions.size) { androidx.compose.ui.focus.FocusRequester() }
    }
    Surface(
        modifier = modifier
            .then(if (isUrlFieldFocusedLocal) Modifier.imePadding() else Modifier),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        shadowElevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                // No vertical padding: the top Row is sized to a 40dp min-height so this
                // bar matches the image (Map) and page-group top bars exactly — switching
                // between tabs shows no height jump. 4dp horizontal matches the guide
                // viewers so the leading icon aligns at the same x across all tabs.
                .padding(horizontal = 4.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 40.dp)
                    .then(
                        // Guide mode icons fit with reduced sizes in portrait; Browser
                        // mode uses a single web-search icon on the left + a weighted
                        // Spacer that pushes the right-side cluster (Home, Back, Tabs,
                        // Save, Kebab) flush to the end. The expanded search bar still
                        // takes the whole row.
                        if (vaultSessionMode == VaultSessionMode.Browser &&
                            compactSearchBar && searchBarExpanded
                        ) Modifier.horizontalScroll(rememberScrollState())
                        else Modifier
                    )
                    .focusProperties {
                        down = bottomBarFocusRequester ?: FocusRequester.Default
                    },
                // Browser mode always uses fixed inter-icon spacing because the
                // weighted Spacer between Search and the right cluster eats the
                // leftover width; SpaceBetween would distribute nothing, packing
                // the right-cluster icons against each other. Guide mode keeps
                // its original spacedBy(12dp) landscape / SpaceBetween portrait.
                horizontalArrangement = when {
                    isBrowser -> Arrangement.spacedBy(if (isLandscape) 12.dp else 8.dp)
                    isLandscape -> Arrangement.spacedBy(12.dp)
                    else -> Arrangement.SpaceBetween
                },
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showFoldButton) {
                    com.twinlore.app.ui.fold.FoldButton()
                }
                // First icon on the left (Guide mode only): folder/guide picker
                if (vaultSessionMode != VaultSessionMode.Browser) {
                NavigableIconButton(
                    imageVector = Icons.Default.FolderOpen,
                    contentDescription = "Guides – add or choose a guide",
                    onClick = onFavoriteClick,
                    focusId = "guide_favorite",
                    iconSize = topBarIconSize,
                    contentPadding = topBarContentPadding,
                    externalFocusRequester = firstButtonFocusRequester
                )
                }
                // Open in Browser button (Guide mode — HTML guides always load offline; this opens the online version in Browser tab)
                if (showOpenInBrowserButton && onOpenGuideInBrowser != null) {
                    NavigableIconButton(
                        imageVector = Icons.Default.Language,
                        contentDescription = "Open Online",
                        onClick = { onOpenGuideInBrowser() },
                        focusId = "guide_open_online",
                        iconSize = topBarIconSize,
                        contentPadding = topBarContentPadding
                    )
                }
                // Theme CSS injection toggle (Guide mode only)
                if (vaultSessionMode == VaultSessionMode.Guide && onToggleGuideThemeInjection != null) {
                    NavigableIconButton(
                        imageVector = Icons.Default.FormatColorFill,
                        contentDescription = if (guideThemeInjectionActive) "Theme active – tap to disable" else "Inject app theme",
                        onClick = { onToggleGuideThemeInjection() },
                        focusId = "guide_theme_inject",
                        iconSize = topBarIconSize,
                        contentPadding = topBarContentPadding,
                        tint = if (guideThemeInjectionActive) null else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                }
                if (vaultSessionMode == VaultSessionMode.Guide && isLandscape) {
                    Spacer(modifier = Modifier.weight(1f))
                }
                
                // Back to Library - only when NOT in vault session (session uses bottom bar for back)
                if (vaultSessionMode == null) {
                NavigableIconButton(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back to Library",
                    onClick = onBack,
                    enabled = true,
                    focusId = "guide_back",
                    iconSize = topBarIconSize,
                    contentPadding = topBarContentPadding
                )
                }
                
                // URL/Search field - hide in Guide mode
                if (vaultSessionMode != VaultSessionMode.Guide) {
                // URL/Search field - use focus color for border
                // Compute display text: show bookmark title if available and URL matches, otherwise show URL
                val displayText = remember(currentUrl, currentBookmark) {
                    // Landing page shows a cryptic file path — display nothing instead
                    if (currentUrl.contains("landing.html")) ""
                    else {
                        val raw = currentBookmark?.takeIf { bookmark: SavedTab ->
                            val normalizedCurrentUrl = currentUrl.removeSuffix("/").lowercase()
                            val normalizedBookmarkUrl = bookmark.url.removeSuffix("/").lowercase()
                            normalizedCurrentUrl == normalizedBookmarkUrl ||
                                currentUrl.contains(bookmark.id) ||
                                (currentUrl.startsWith("file://") && bookmark.url.startsWith("file://") && currentUrl.contains(bookmark.id))
                        }?.title ?: currentUrl
                            .removePrefix("https://www.")
                            .removePrefix("http://www.")
                            .removePrefix("https://")
                            .removePrefix("http://")
                        // Show the tail so the meaningful end is visible (e.g. page path, not protocol)
                        if (raw.length > 50) "…${raw.takeLast(49)}" else raw
                    }
                }

                // Compute placeholder text: show current URL/bookmark title when field is empty
                val placeholderText = remember(urlInput, displayText) {
                    displayText
                }

                if (compactSearchBar && searchBarExpanded) {
                    // Expanded search bar: close button + full-width text field (hides all other buttons)
                    NavigableIconButton(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close search",
                        onClick = {
                            searchBarExpanded = false
                            onUrlInputChange("")
                            keyboardController?.hide()
                            focusManager.clearFocus()
                            onUrlFieldFocusChanged?.invoke(false)
                        },
                        focusId = "browser_search_close",
                        iconSize = topBarIconSize,
                        contentPadding = topBarContentPadding
                    )
                    NavigableTextField(
                        value = urlInput,
                        onValueChange = onUrlInputChange,
                        placeholder = placeholderText,
                        leadingIcon = Icons.Default.TravelExplore,
                        imeAction = ImeAction.Go,
                        // The user just tapped Search expecting to type, so jump straight into
                        // edit mode. NavigableTextField handles the focus + IME open from there.
                        startInEditMode = true,
                        onSubmit = {
                            if (urlInput.isNotBlank()) {
                                val input = urlInput.trim()
                                val finalQuery = if (appendGameName && gameName != null && gameName.isNotBlank() && !inputContainsGameName(input, gameName)) {
                                    "${input} ${gameName.trim()}".trim()
                                } else {
                                    input
                                }
                                keyboardController?.hide()
                                focusManager.clearFocus()
                                onNavigate(input, false, finalQuery)
                                searchBarExpanded = false
                            }
                        },
                        externalFocusRequester = urlFieldFocusRequester,
                        modifier = Modifier
                            .weight(1f)
                            .onFocusChanged { focusState ->
                                isUrlFieldFocusedLocal = focusState.isFocused
                                if (focusState.isFocused) {
                                    if (vaultId.isNotBlank()) refreshHistoryTrigger++
                                    onUrlFieldFocusChanged?.invoke(true)
                                    focusedSuggestionIndex = -1
                                    onUrlFieldFocused?.invoke()
                                } else {
                                    if (urlInput.isNotBlank()) onUrlInputChange("")
                                    onUrlFieldFocusChanged?.invoke(false)
                                    focusedSuggestionIndex = -1
                                }
                            }
                    )
                    // Auto-append toggle — shown inline when search bar is expanded in portrait
                    if (gameName != null && gameName.isNotBlank()) {
                        NavigableIconButton(
                            imageVector = Icons.Default.ShortText,
                            contentDescription = if (appendGameName) "Auto-append \"$gameName\" ON" else "Auto-append game name OFF",
                            onClick = { onAppendGameNameChange(!appendGameName) },
                            focusId = "browser_append_game_name_expanded",
                            iconSize = topBarIconSize,
                            contentPadding = topBarContentPadding,
                            tint = if (appendGameName) focusColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                    // Copy current address. Reads `currentUrl` (the live page URL,
                    // not whatever's typed in the input) so the user can grab the
                    // page they're on, even mid-edit.
                    if (currentUrl.isNotBlank() && !currentUrl.contains("landing.html")) {
                        val copyCtx = LocalContext.current
                        NavigableIconButton(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy address",
                            onClick = {
                                val cm = copyCtx.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                                    as? android.content.ClipboardManager
                                cm?.setPrimaryClip(
                                    android.content.ClipData.newPlainText("address", currentUrl)
                                )
                                android.widget.Toast.makeText(
                                    copyCtx, "Address copied", android.widget.Toast.LENGTH_SHORT
                                ).show()
                            },
                            focusId = "browser_copy_address_expanded",
                            iconSize = topBarIconSize,
                            contentPadding = topBarContentPadding
                        )
                    }
                } else {
                // Normal layout: search icon (compact) or inline text field + all buttons
                if (compactSearchBar) {
                    NavigableIconButton(
                        imageVector = Icons.Default.TravelExplore,
                        contentDescription = "Search the web",
                        onClick = { searchBarExpanded = true },
                        focusId = "browser_search_expand",
                        iconSize = topBarIconSize,
                        contentPadding = topBarContentPadding,
                        externalFocusRequester = firstButtonFocusRequester
                    )
                    // Browser mode only: weighted Spacer pushes the right cluster
                    // (Home → Back → Tabs → Save → Kebab) flush to the end of the
                    // bar, with Home just before Back.
                    if (vaultSessionMode == VaultSessionMode.Browser) {
                        Spacer(modifier = Modifier.weight(1f))
                        if (onGoToLandingPage != null) {
                            NavigableIconButton(
                                imageVector = Icons.Default.Home,
                                contentDescription = "Back to landing page",
                                onClick = { onGoToLandingPage() },
                                focusId = "browser_landing",
                                iconSize = topBarIconSize,
                                contentPadding = topBarContentPadding
                            )
                        }
                    }
                } else {
                    NavigableTextField(
                        value = urlInput,
                        onValueChange = onUrlInputChange,
                        placeholder = placeholderText,
                        leadingIcon = Icons.Default.TravelExplore,
                        imeAction = ImeAction.Go,
                        onSubmit = {
                            if (urlInput.isNotBlank()) {
                                val input = urlInput.trim()
                                val finalQuery = if (appendGameName && gameName != null && gameName.isNotBlank() && !inputContainsGameName(input, gameName)) {
                                    "${input} ${gameName.trim()}".trim()
                                } else {
                                    input
                                }
                                keyboardController?.hide()
                                focusManager.clearFocus()
                                onNavigate(input, false, finalQuery)
                            }
                        },
                        externalFocusRequester = urlFieldFocusRequester,
                        modifier = Modifier
                            .weight(1f)
                            .then(if (isLandscape) Modifier.widthIn(max = 320.dp) else Modifier)
                            .onFocusChanged { focusState ->
                                isUrlFieldFocusedLocal = focusState.isFocused
                                if (focusState.isFocused) {
                                    if (vaultId.isNotBlank()) refreshHistoryTrigger++
                                    onUrlFieldFocusChanged?.invoke(true)
                                    focusedSuggestionIndex = -1
                                    onUrlFieldFocused?.invoke()
                                } else {
                                    if (urlInput.isNotBlank()) onUrlInputChange("")
                                    onUrlFieldFocusChanged?.invoke(false)
                                    focusedSuggestionIndex = -1
                                }
                            }
                    )
                }

                // Browser back - right of search bar, only in Browser session mode.
                // Home has moved to the leftmost slot; Forward + Refresh live in the kebab.
                if (vaultSessionMode == VaultSessionMode.Browser && !(compactSearchBar && searchBarExpanded)) {
                    if (onBrowserBack != null) {
                        NavigableIconButton(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back in browser",
                            onClick = { onBrowserBack() },
                            enabled = canGoBack,
                            focusId = "browser_back",
                            iconSize = topBarIconSize,
                            contentPadding = topBarContentPadding
                        )
                    }
                }

                // Refresh - reload current page (Guide mode only; Browser has its own
                // refresh in the back/forward cluster above).
                if (vaultSessionMode != VaultSessionMode.Browser) {
                    NavigableIconButton(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh page",
                        onClick = onRefresh,
                        focusId = "guide_refresh",
                        iconSize = topBarIconSize,
                        contentPadding = topBarContentPadding
                    )
                }

                // Save as bookmark (download icon) - far right in Browser mode only; no filled-star state
                if (vaultSessionMode == VaultSessionMode.Browser) {
                    // Tab counter [N]
                    if (browserTabCount != null && onOpenBrowserTabSwitcher != null) {
                        val tabCounterInteractionSource = remember { MutableInteractionSource() }
                        val isTabCounterFocused by tabCounterInteractionSource.collectIsFocusedAsState()
                        val restingColor = if (isTabCounterFocused) focusColor else MaterialTheme.colorScheme.onSurface
                        val restingBorderWidth = if (isTabCounterFocused) 2.dp else 1.5.dp

                        // Pulse animation: scale 1.0 -> 1.30 (140ms) -> 1.0 (200ms),
                        // driven by tabOpenedPulse emissions (fired by BrowserTabsViewModel.newTab
                        // when pulseOnSuccess = true).
                        val pulseScale = remember { Animatable(1f) }
                        LaunchedEffect(tabOpenedPulse) {
                            tabOpenedPulse?.collect {
                                pulseScale.snapTo(1f)
                                pulseScale.animateTo(1.30f, tween(140, easing = FastOutSlowInEasing))
                                pulseScale.animateTo(1f, tween(200, easing = FastOutSlowInEasing))
                            }
                        }
                        // 0f at rest, 1f at peak. (peak - rest) / (peakScale - 1) = (x - 1) / 0.30
                        val pulseProgress = ((pulseScale.value - 1f) / 0.30f).coerceIn(0f, 1f)
                        val borderWidth = lerpDp(restingBorderWidth, 2.dp, pulseProgress)
                        val borderColor = lerpColor(restingColor, focusColor, pulseProgress)
                        val textColor = lerpColor(restingColor, focusColor, pulseProgress)

                        // Wrap in a Box with topBarContentPadding so the bounding-box footprint
                        // matches NavigableIconButton (icon + 8dp focus indicator + contentPadding).
                        // The Surface keeps its compact 24/28dp size; only its surrounding
                        // gap matches the icon buttons so SpaceBetween distributes evenly.
                        Box(
                            modifier = Modifier.padding(topBarContentPadding),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                onClick = { onOpenBrowserTabSwitcher() },
                                shape = RoundedCornerShape(4.dp),
                                color = Color.Transparent,
                                border = BorderStroke(borderWidth, borderColor),
                                interactionSource = tabCounterInteractionSource,
                                modifier = Modifier
                                    .size(if (isLandscape) 28.dp else 24.dp)
                                    .scale(pulseScale.value)
                                    .focusable(interactionSource = tabCounterInteractionSource)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = browserTabCount.toString(),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = textColor
                                    )
                                }
                            }
                        }
                    }

                    if (isSavingFavorite && onCancelSave != null) {
                        IconButton(
                            onClick = {
                                runWithHapticAndLed(view, hapticFeedbackIntensity, ledEnabled, focusColor, ledBrightness, ledSaturationBoost, ledTurnOffTime.inactivityMs, event = FeedbackEvent.BACK, action = onCancelSave)
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(trailingIconSize),
                                strokeWidth = 2.dp,
                                color = focusColor
                            )
                        }
                    } else if (isSavingFavorite) {
                        Box(modifier = Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(trailingIconSize),
                                strokeWidth = 2.dp,
                                color = focusColor
                            )
                        }
                    } else {
                        NavigableIconButton(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Save as bookmark",
                            onClick = onSaveAsFavorite,
                            enabled = canSaveAsBookmark,
                            focusId = "browser_save_bookmark",
                            iconSize = topBarIconSize,
                            contentPadding = topBarContentPadding
                        )
                    }

                    // Browser-mode overflow kebab: Forward, Refresh, Block trackers.
                    var browserOverflowOpen by remember { mutableStateOf(false) }
                    Box {
                        NavigableIconButton(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = "Browser options",
                            onClick = { browserOverflowOpen = true },
                            focusId = "browser_overflow_menu",
                            iconSize = topBarIconSize,
                            contentPadding = topBarContentPadding,
                        )
                        DropdownMenu(
                            expanded = browserOverflowOpen,
                            onDismissRequest = { browserOverflowOpen = false },
                        ) {
                            if (onBrowserForward != null) {
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ArrowForward,
                                                contentDescription = null,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Text(text = "Forward")
                                        }
                                    },
                                    enabled = canGoForward,
                                    onClick = {
                                        FeedbackManager.play(view, FeedbackEvent.SWITCH)
                                        browserOverflowOpen = false
                                        onBrowserForward()
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(text = "Refresh")
                                    }
                                },
                                onClick = {
                                    FeedbackManager.play(view, FeedbackEvent.REFRESH)
                                    browserOverflowOpen = false
                                    onRefresh()
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.FindInPage,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(text = "Find in page")
                                    }
                                },
                                onClick = {
                                    FeedbackManager.play(view, FeedbackEvent.CONFIRM)
                                    browserOverflowOpen = false
                                    onFindOnPage()
                                }
                            )
                            // Open the current page in the system default browser.
                            // Only for live http(s) pages — saved guides (file://)
                            // and the landing page have nothing external to open.
                            val canOpenExternally = currentUrl.startsWith("http")
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Language,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(text = "Open in default browser")
                                    }
                                },
                                enabled = canOpenExternally,
                                onClick = {
                                    FeedbackManager.play(view, FeedbackEvent.CONFIRM)
                                    browserOverflowOpen = false
                                    runCatching {
                                        context.startActivity(
                                            android.content.Intent(
                                                android.content.Intent.ACTION_VIEW,
                                                android.net.Uri.parse(currentUrl),
                                            )
                                                .addCategory(android.content.Intent.CATEGORY_BROWSABLE)
                                                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                        )
                                    }
                                }
                            )
                            Divider()
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Cookie,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(text = "Block ads + banners")
                                        Spacer(modifier = Modifier.weight(1f))
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Switch(
                                            checked = privacyBlockingEnabled,
                                            onCheckedChange = null,
                                            modifier = Modifier.scale(0.75f)
                                        )
                                    }
                                },
                                onClick = {
                                    FeedbackManager.play(view, FeedbackEvent.CONFIRM)
                                    onTogglePrivacyBlocking()
                                }
                            )
                        }
                    }
                }
                } // end normal (non-expanded) layout
                } // end if (vaultSessionMode != VaultSessionMode.Guide)

                // Font size controls - hide in Browser mode
                if (vaultSessionMode != VaultSessionMode.Browser && onZoomLevelChange != null) {
                    var showFontSizeSlider by remember { mutableStateOf(false) }
                    Box {
                        NavigableIconButton(
                            imageVector = Icons.Default.FormatSize,
                            contentDescription = "Font Size",
                            onClick = { showFontSizeSlider = !showFontSizeSlider },
                            focusId = "guide_font_size",
                            iconSize = topBarIconSize,
                            contentPadding = topBarContentPadding
                        )
                        // Font size slider popup with reset button
                        androidx.compose.material3.DropdownMenu(
                            expanded = showFontSizeSlider,
                            onDismissRequest = { showFontSizeSlider = false },
                            modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                        ) {
                            // Local slider state for smooth visual feedback; actual zoom
                            // is applied via throttled callback to avoid overwhelming the WebView.
                            var localSliderValue by remember { mutableFloatStateOf(zoomLevel) }
                            var isDragging by remember { mutableStateOf(false) }
                            // Sync from external changes (bookmark restore, programmatic reset)
                            LaunchedEffect(zoomLevel) {
                                if (!isDragging) localSliderValue = zoomLevel
                            }
                            // Throttled updates during drag: sample current position every 200ms.
                            // On release, sync slider to actual zoom (handles clamping at min).
                            LaunchedEffect(isDragging) {
                                if (isDragging) {
                                    while (true) {
                                        kotlinx.coroutines.delay(200)
                                        onZoomLevelChange(localSliderValue)
                                    }
                                } else {
                                    localSliderValue = zoomLevel
                                }
                            }
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                    .widthIn(min = 336.dp, max = 408.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (onResetZoom != null) {
                                    IconButton(
                                        onClick = {
                                            FeedbackManager.play(view, FeedbackEvent.CONFIRM)
                                            onResetZoom()
                                            showFontSizeSlider = false
                                        },
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = "Reset Zoom",
                                            modifier = Modifier.size(20.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Text(
                                    "A",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Slider(
                                    value = localSliderValue,
                                    onValueChange = { newValue ->
                                        localSliderValue = newValue
                                        isDragging = true
                                    },
                                    onValueChangeFinished = {
                                        isDragging = false
                                        onZoomLevelChange(localSliderValue)
                                    },
                                    valueRange = minZoomLevel..3.0f,
                                    modifier = Modifier.weight(1f),
                                    colors = SliderDefaults.colors(
                                        thumbColor = MaterialTheme.colorScheme.onSurface,
                                        activeTrackColor = MaterialTheme.colorScheme.onSurface,
                                        inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                    )
                                )
                                Text(
                                    "A",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "${(localSliderValue * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.widthIn(min = 40.dp),
                                    textAlign = TextAlign.End
                                )
                            }
                        }
                    }
                }

                // Find on Page - hide in Browser mode (must be outside the Guide-hidden block)
                if (vaultSessionMode != VaultSessionMode.Browser) {
                    NavigableIconButton(
                        imageVector = Icons.Default.FindInPage,
                        contentDescription = "Find on Page",
                        onClick = onFindOnPage,
                        focusId = "guide_find",
                        iconSize = topBarIconSize,
                        contentPadding = topBarContentPadding
                    )
                }

                // TOC button (Guide mode only)
                if (vaultSessionMode != VaultSessionMode.Browser && onOpenToc != null) {
                    NavigableIconButton(
                        imageVector = Icons.Default.List,
                        contentDescription = "Table of Contents",
                        onClick = onOpenToc,
                        focusId = "guide_toc",
                        iconSize = topBarIconSize,
                        contentPadding = topBarContentPadding
                    )
                }

                // Scroll markers bar toggle (Guide mode only). Filled bookmark
                // when expanded, outline when collapsed — mirrors the bar's
                // visibility state at a glance.
                if (vaultSessionMode != VaultSessionMode.Browser) {
                    NavigableIconButton(
                        imageVector = if (scrollMarkersBarVisible) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        contentDescription = if (scrollMarkersBarVisible) "Hide scroll markers" else "Show scroll markers",
                        onClick = onToggleScrollMarkersBar,
                        focusId = "guide_scroll_markers_toggle",
                        iconSize = topBarIconSize,
                        contentPadding = topBarContentPadding
                    )
                }

            }
            
            // Scroll markers bar - hide in Browser mode; also hide when user toggled off via top bar button
            if (vaultSessionMode != VaultSessionMode.Browser && scrollMarkersBarVisible) {
            // Scroll markers bar (fixed height): same background as browser top menu (surface 0.95f); empty state and chips use theme + focus colors
            val scrollMarkersInteraction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            val scrollMarkersFocused by scrollMarkersInteraction.collectIsFocusedAsState()
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .padding(top = 4.dp)
                    .focusable(interactionSource = scrollMarkersInteraction)
                    .then(
                        if (scrollMarkersFocused) Modifier.border(2.dp, focusColor, RoundedCornerShape(6.dp))
                        else Modifier
                    ),
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                tonalElevation = 0.dp
            ) {
                if (scrollMarkers.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            text = "No scroll markers. Select text → Save Scroll Marker in menu.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        contentPadding = PaddingValues(vertical = 6.dp)
                    ) {
                        itemsIndexed(scrollMarkers) { index, marker ->
                            val isFocused = focusedScrollMarkerIndex == index
                            Surface(
                                modifier = Modifier
                                    .heightIn(max = 42.dp)
                                    .then(
                                        if (isFocused) Modifier.border(2.dp, focusColor, RoundedCornerShape(6.dp))
                                        else Modifier
                                    )
                                    .combinedClickable(
                                        onClick = {
                                            runWithHapticAndLed(view, hapticFeedbackIntensity, ledEnabled, focusColor, ledBrightness, ledSaturationBoost, ledTurnOffTime.inactivityMs) {
                                                onScrollMarkerTap(marker)
                                            }
                                        },
                                        onLongClick = { onScrollMarkerLongPress(marker) }
                                    ),
                                shape = RoundedCornerShape(6.dp),
                                color = if (isFocused) focusColor.getFocusContainer() else MaterialTheme.colorScheme.surfaceVariant,
                                tonalElevation = 0.dp
                            ) {
                                Text(
                                    text = marker.label.take(25).let { if (marker.label.length > 25) "$it…" else it },
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (isFocused) focusColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
            }
            
            // Search suggestions dropdown (shown when URL field is focused)
            // Only show if we have search history
            if (isUrlFieldFocusedLocal && allSuggestions.isNotEmpty()) {
                // LaunchedEffect to handle focus request when dropdown appears or when shouldMoveFocusToSuggestion is true
                LaunchedEffect(shouldMoveFocusToSuggestion, focusedSuggestionIndex) {
                    if (shouldMoveFocusToSuggestion && focusedSuggestionIndex >= 0 && focusedSuggestionIndex < suggestionFocusRequesters.size) {
                        delay(50) // Small delay to ensure dropdown is rendered
                        suggestionFocusRequesters[focusedSuggestionIndex].requestFocus()
                        shouldMoveFocusToSuggestion = false
                    }
                }
                
                SearchSuggestionsDropdown(
                    suggestions = allSuggestions,
                    onSuggestionSelected = { suggestion ->
                        // History entry selected - use as-is (already contains game name)
                        onNavigate(suggestion, true, suggestion)
                        isUrlFieldFocusedLocal = false
                        onUrlFieldFocusChanged?.invoke(false)
                        focusedSuggestionIndex = -1
                    },
                    onSuggestionLongPress = { query ->
                        showDeleteDialog = query
                    },
                    onSuggestionDelete = { query ->
                        showDeleteDialog = query
                    },
                    focusedIndex = focusedSuggestionIndex,
                    focusRequesters = suggestionFocusRequesters,
                    onFocusedIndexChange = { index ->
                        focusedSuggestionIndex = index
                    },
                    onDismiss = {
                        isUrlFieldFocusedLocal = false
                        onUrlFieldFocusChanged?.invoke(false)
                        focusedSuggestionIndex = -1
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp) // Slight offset below text field
                        .imePadding() // Respect keyboard insets to position above keyboard
                )
            }
            
            // Delete confirmation dialog
            showDeleteDialog?.let { queryToDelete ->
                AlertDialog(
                    onDismissRequest = { showDeleteDialog = null },
                    containerColor = dialogBackgroundColor,
                    title = { 
                        Text(
                            "Delete entry?",
                            color = dialogTextColor
                        ) 
                    },
                    text = { 
                        Text(
                            queryToDelete,
                            color = dialogTextColor
                        ) 
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                if (vaultId.isNotBlank()) {
                                    searchHistoryManager.deleteSearch(vaultId, queryToDelete)
                                    refreshHistoryTrigger++
                                }
                                showDeleteDialog = null
                            },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = focusColor
                            )
                        ) {
                            Text("Delete")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { showDeleteDialog = null },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = focusColor
                            )
                        ) {
                            Text("Cancel")
                        }
                    }
                )
            }
        }
    }
}

/**
 * Search suggestions dropdown
 */
@Composable
internal fun SearchSuggestionsDropdown(
    suggestions: List<String>,
    onSuggestionSelected: (String) -> Unit,
    onSuggestionLongPress: ((String) -> Unit)? = null,
    onSuggestionDelete: ((String) -> Unit)? = null,
    focusedIndex: Int,
    focusRequesters: List<androidx.compose.ui.focus.FocusRequester>,
    onFocusedIndexChange: (Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Get focus color from settings
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val focusColorState by settingsViewModel.focusColor.collectAsState(initial = FocusColor.ELECTRIC_BLUE)
    val cachedFocusColor = LocalCachedFocusColor.current?.value
    val focusColor = cachedFocusColor ?: focusColorState.color
    val view = LocalView.current

    val cardBackgroundColor = MaterialTheme.colorScheme.surface
    
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = cardBackgroundColor
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        if (suggestions.isEmpty()) {
            // Show empty state message
            Text(
                text = "No recent searches",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 200.dp), // Limit height to show max 4 items (~48dp per item)
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                itemsIndexed(suggestions) { index, suggestion ->
                    val isFocused = focusedIndex == index
                    val focusRequester = focusRequesters[index]
                    val interactionSource = remember(index) { androidx.compose.foundation.interaction.MutableInteractionSource() }
                    
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                            .focusable(interactionSource = interactionSource)
                            .pointerInput(suggestion, onSuggestionLongPress) {
                                // Handle both tap and long press gestures
                                detectTapGestures(
                                    onTap = {
                                        // Regular tap - select the suggestion
                                        onSuggestionSelected(suggestion)
                                    },
                                    onLongPress = {
                                        // Long press - delete the suggestion
                                        onSuggestionLongPress?.invoke(suggestion)
                                    }
                                )
                            }
                        .onKeyEvent { keyEvent ->
                            val nativeKeyEvent = keyEvent.nativeKeyEvent
                            if (nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN) {
                                when (nativeKeyEvent.keyCode) {
                                    android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                                        val newIndex = if (index < suggestions.size - 1) index + 1 else 0
                                        onFocusedIndexChange(newIndex)
                                        focusRequesters[newIndex].requestFocus()
                                        true
                                    }
                                    android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                                        val newIndex = if (index > 0) index - 1 else suggestions.size - 1
                                        onFocusedIndexChange(newIndex)
                                        focusRequesters[newIndex].requestFocus()
                                        true
                                    }
                                    android.view.KeyEvent.KEYCODE_DPAD_CENTER,
                                    android.view.KeyEvent.KEYCODE_BUTTON_A,
                                    android.view.KeyEvent.KEYCODE_ENTER -> {
                                        onSuggestionSelected(suggestion)
                                        true
                                    }
                                    android.view.KeyEvent.KEYCODE_BACK -> {
                                        onDismiss()
                                        true
                                    }
                                    else -> false
                                }
                            } else {
                                false
                            }
                        },
                    color = if (isFocused) {
                        focusColor.getFocusContainer()
                    } else {
                        cardBackgroundColor
                    }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = suggestion,
                            modifier = Modifier.weight(1f).padding(vertical = 8.dp),
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (isFocused) {
                                focusColor.getContrastingTextColor()
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (onSuggestionDelete != null) {
                            IconButton(
                                onClick = {
                                    FeedbackManager.play(view, FeedbackEvent.CONFIRM)
                                    onSuggestionDelete(suggestion)
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Delete",
                                    modifier = Modifier.size(16.dp),
                                    tint = if (isFocused) {
                                        focusColor.getContrastingTextColor().copy(alpha = 0.7f)
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
        }
    }
}
