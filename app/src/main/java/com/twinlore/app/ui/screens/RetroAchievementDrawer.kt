package com.twinlore.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.layout.ContentScale
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.focus.onFocusChanged
import com.twinlore.app.ui.LocalCachedFocusColor
import com.twinlore.app.ui.components.GlowingAwardCircle
import com.twinlore.app.ui.components.HighestAwardIcon
import com.twinlore.app.ui.components.achievementBarBrush
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.twinlore.app.data.retroachievements.RaAchievementDisplay
import com.twinlore.app.data.retroachievements.RaGameProgress
import com.twinlore.app.data.retroachievements.RaLocalConsole
import com.twinlore.app.data.retroachievements.RaLocalGame
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import android.net.Uri
import android.view.KeyEvent
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.unit.sp
import com.twinlore.app.data.FocusColor
import com.twinlore.app.data.GameNameSuggestionProvider
import com.twinlore.app.ui.navigation.ControllerTextField
import com.twinlore.app.ui.navigation.NavigableActionButton
import com.twinlore.app.ui.navigation.NavigableButton
import com.twinlore.app.di.GameNameSuggestionEntryPoint
import com.twinlore.app.ui.theme.getContrastingTextColor
import com.twinlore.app.ui.theme.ChecklistColorPalette
import com.twinlore.app.domain.ChecklistTab
import com.twinlore.app.domain.RaAchievementSortField
import com.twinlore.app.domain.RaAchievementSorter
import dagger.hilt.EntryPoints
import coil.request.ImageRequest
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.sqrt
import java.text.NumberFormat

private const val RA_GAME_URL_PREFIX = "https://retroachievements.org/game/"
private const val RA_USER_PROFILE_PREFIX = "https://retroachievements.org/user/"
/** Base URL for game icons (e.g. header). Canonical CDN host shared app-wide via [RaUrls]. */
private const val RA_IMAGES_PREFIX = com.twinlore.app.data.retroachievements.RaUrls.IMAGES_BASE
/** CDN URL for achievement badges (one per achievement; use this for list items, not game icon). */
private const val RA_BADGE_CDN_PREFIX = com.twinlore.app.data.retroachievements.RaUrls.BADGE_BASE

private val nf = NumberFormat.getIntegerInstance()

/** Small circle bullet used as separator in header info (matches achievement list style). */
@Composable
private fun SmallCircleBullet(color: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(4.dp).background(color.copy(alpha = 0.7f), CircleShape))
}

/**
 * Icon for achievement type: progression = check, win_condition = medal, missable = exclamation (warning), null = none. Uses case-insensitive type.
 *
 * The vault tab's own icons, at the vault tab's own (larger) size - unaffected by what the journey
 * chart draws. The chart used to match this exactly for all three types; it now matches only for
 * progression, whose icon (`Icons.Default.TaskAlt`) is named here and in `ChartTypeIcons.markFor`
 * both. Missable and win condition stamp a single character on the chart instead ("!" and "★" -
 * see `ChartTypeIcons`'s own class doc for why), which is a deliberate difference from this list,
 * not a drift: the two surfaces are allowed to disagree about which alphabet reads best at their
 * very different sizes, as long as `ChartTypeIcons.markFor` is what both the chart's badges and the
 * settings-sheet legend read, so those two - the chart and the key that explains it - cannot drift
 * from EACH OTHER.
 */
@Composable
private fun AchievementTypeIcon(type: String?, tint: Color, modifier: Modifier = Modifier) {
    when (type?.lowercase()?.trim()) {
        "progression" -> Icon(Icons.Default.TaskAlt, contentDescription = "Progression", modifier = modifier.size(18.dp), tint = tint)
        "win_condition" -> Icon(Icons.Default.MilitaryTech, contentDescription = "Win condition", modifier = modifier.size(18.dp), tint = tint)
        "missable" -> Icon(Icons.Default.Warning, contentDescription = "Missable", modifier = modifier.size(18.dp), tint = tint)
        else -> {}
    }
}


@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
fun RetroAchievementDrawer(
    vaultId: String,
    gameTitle: String,
    raGameId: Int?,
    gameLogoModel: Any? = null,
    onDismiss: () -> Unit,
    onOpenInBrowser: (String) -> Unit,
    viewModel: AchievementDrawerViewModel = hiltViewModel()
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val sheetBg = MaterialTheme.colorScheme.background

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxSize(),
        sheetState = sheetState,
        containerColor = sheetBg,
        shape = RoundedCornerShape(0.dp),
        dragHandle = null
    ) {
        val configuration = LocalConfiguration.current
        LaunchedEffect(configuration) {
            sheetState.expand()
        }
        RetroAchievementContent(
            vaultId = vaultId,
            gameTitle = gameTitle,
            raGameId = raGameId,
            gameLogoModel = gameLogoModel,
            onOpenInBrowser = onOpenInBrowser,
            onDismiss = onDismiss,
            viewModel = viewModel
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun RetroAchievementContent(
    vaultId: String,
    gameTitle: String,
    raGameId: Int?,
    gameLogoModel: Any? = null,
    onOpenInBrowser: (String) -> Unit,
    onDismiss: () -> Unit,
    viewModel: AchievementDrawerViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel? = null,
    showHeaderActionRow: Boolean = true,
    mainViewModel: com.twinlore.app.ui.MainViewModel? = null,
    isTabVisible: Boolean = true,
    contentFocusRequester: androidx.compose.ui.focus.FocusRequester? = null,
    sourceChipsFocusRequester: androidx.compose.ui.focus.FocusRequester? = null,
    bottomBarFocusRequester: androidx.compose.ui.focus.FocusRequester? = null
) {
    val settingsVm = settingsViewModel ?: hiltViewModel()
    val progress by viewModel.progress.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val error by viewModel.error.collectAsState()
    val isNetworkAvailable by viewModel.isNetworkAvailable.collectAsState()
    val autoAssignInProgress by viewModel.autoAssignInProgress.collectAsState()
    var manualGameIdInput by remember { mutableStateOf("") }
    var showUnlinkConfirm by remember { mutableStateOf(false) }
    var tabPickerOptions by remember { mutableStateOf<List<ChecklistTab>>(emptyList()) }
    var showBulkChecklistConfirm by remember { mutableStateOf(false) }
    var bulkChecklistList by remember { mutableStateOf<List<com.twinlore.app.data.retroachievements.RaAchievementDisplay>>(emptyList()) }
    val guideUrlValue by viewModel.guideUrl.collectAsState()
    val promptGuideDownload by viewModel.promptGuideDownload.collectAsState()
    var showGuideOpenConfirm by remember { mutableStateOf(false) }
    var showNoGuideDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val focusColor by settingsVm.focusColor.collectAsState(initial = FocusColor.CRIMSON)
    val sheetBg = MaterialTheme.colorScheme.background
    val sheetText = MaterialTheme.colorScheme.onBackground
    val cachedFocusColor = LocalCachedFocusColor.current?.value
    val accentColor = cachedFocusColor ?: focusColor.color
    val showWallpaperView by viewModel.showWallpaperView.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.checkConnectivity()
    }

    // Show guide open prompt after linking a game
    LaunchedEffect(promptGuideDownload) {
        if (promptGuideDownload) {
            showGuideOpenConfirm = true
            viewModel.clearGuidePrompt()
        }
    }

    LaunchedEffect(vaultId, raGameId, isTabVisible) {
        if (raGameId != null && isTabVisible) {
            // Refresh whenever the achievements tab becomes the visible tab (and on open).
            viewModel.refresh(vaultId, raGameId)
        }
    }

    // Listen for controller actions (X = wallpaper grid view, Y = refresh)
    if (mainViewModel != null && isTabVisible) {
        val controllerAction by mainViewModel.controllerAction.collectAsState()
        LaunchedEffect(controllerAction) {
            when (controllerAction) {
                com.twinlore.app.ui.MainViewModel.ControllerAction.ACHIEVEMENTS_WALLPAPER -> {
                    if (raGameId != null) viewModel.setShowWallpaperView(!showWallpaperView)
                }
                com.twinlore.app.ui.MainViewModel.ControllerAction.ACHIEVEMENTS_REFRESH -> {
                    if (raGameId != null) viewModel.refresh(vaultId, raGameId)
                }
                else -> {}
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        if (raGameId == null) {
            LaunchedEffect(Unit) { viewModel.loadConsoles() }
            val suggestionProvider = remember(context) {
                try {
                    EntryPoints.get(context.applicationContext, GameNameSuggestionEntryPoint::class.java).gameNameSuggestionProvider()
                } catch (e: Throwable) {
                    null
                }
            }
            UnlinkedGameStateContent(
                vaultId = vaultId,
                gameTitle = gameTitle,
                gameLogoModel = gameLogoModel,
                viewModel = viewModel,
                manualGameIdInput = manualGameIdInput,
                onManualGameIdChange = { manualGameIdInput = it },
                onLinkManual = { currentValue ->
                    val id = currentValue.trim().toIntOrNull()
                    if (id != null) {
                        scope.launch {
                            viewModel.validateAndLinkGame(vaultId, id)
                                .onSuccess { manualGameIdInput = "" }
                                .onFailure { e ->
                                    android.widget.Toast.makeText(
                                        context,
                                        e.message ?: "Could not link game",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }
                        }
                    } else {
                        android.widget.Toast.makeText(context, "Enter a valid Game ID", android.widget.Toast.LENGTH_SHORT).show()
                    }
                },
                suggestionProvider = suggestionProvider,
                settingsViewModel = settingsVm,
                contentFocusRequester = contentFocusRequester,
                mainViewModel = mainViewModel
            )
        } else {
        when {
            loading && progress == null -> {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = accentColor)
                }
            }
            error != null -> {
                Column(Modifier.padding(16.dp)) {
                    Text(error!!, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { viewModel.refresh(vaultId, raGameId!!) }) {
                        Text("Retry", color = accentColor)
                    }
                }
            }
            progress != null -> {
                val p = progress!!
                // Focus comes from bottom bar UP action — do not auto-focus here
                val sortField by viewModel.sortField.collectAsState()
                val sortDirection by viewModel.sortDirection.collectAsState()
                val typeFilter by viewModel.achievementTypeFilter.collectAsState()
                val filterLockedOnly by viewModel.filterLockedOnly.collectAsState()
                val scrollIndex by viewModel.scrollIndex.collectAsState()
                val scrollOffset by viewModel.scrollOffset.collectAsState()
                val raUsername by viewModel.raUsername.collectAsState()
                val searchQuery by viewModel.searchQuery.collectAsState()
                val listState = rememberLazyListState()

                // The query is the view model's, alongside the sort and the chips, because this
                // whole composable is disposed when the source chip flips to Steam or the drawer
                // closes, and a search should survive that the same way a sort does.

                val gridFocusRequester = contentFocusRequester ?: remember { FocusRequester() }

                // Left-stick scroll: 60 Hz loop reads the shared flow's .value to avoid
                // recomposition on every motion event. Matches GuideScreen browser scroll.
                if (mainViewModel != null) {
                    LaunchedEffect(isTabVisible) {
                        if (!isTabVisible) return@LaunchedEffect
                        val analogDeadzone = 0.1f
                        val pxPerFrame = 56f
                        while (true) {
                            val v = mainViewModel.vaultSessionListScroll.value
                            if (kotlin.math.abs(v) > analogDeadzone) {
                                listState.scrollBy(v * pxPerFrame)
                                kotlinx.coroutines.delay(16)
                            } else {
                                kotlinx.coroutines.delay(200)
                            }
                        }
                    }
                }

                // --- Auto-hide timer and chrome sync ---
                // Scroll-direction chrome: show on scroll-up, hide on scroll-down, always show at top
                var showChrome by remember { mutableStateOf(true) }
                LaunchedEffect(showChrome, isTabVisible) {
                    if (isTabVisible) {
                        mainViewModel?.setVaultSessionAchievementsChromeVisible(showChrome)
                    }
                }
                LaunchedEffect(listState, isTabVisible) {
                    if (!isTabVisible) return@LaunchedEffect
                    var prevIndex = listState.firstVisibleItemIndex
                    var prevOffset = listState.firstVisibleItemScrollOffset
                    snapshotFlow {
                        listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
                    }.collect { (index, offset) ->
                        val next = when {
                            index == 0 && offset == 0 -> true
                            index < prevIndex || (index == prevIndex && offset < prevOffset) -> true
                            index > prevIndex || (index == prevIndex && offset > prevOffset) -> false
                            else -> showChrome
                        }
                        prevIndex = index
                        prevOffset = offset
                        if (next != showChrome) showChrome = next
                    }
                }

                // What the chips say, in the chips' order. The wallpaper board draws THIS list, not
                // the searched one below: the chips are on screen beside the board's own button, so
                // a board narrowed by them is a board the user can see the reason for, while the
                // search box can be scrolled out of view (or fired from the controller's wallpaper
                // button) and would silently export a picture claiming a game has four badges.
                val chipFilteredAchievements = remember(
                    p.achievements, sortField, sortDirection, typeFilter, filterLockedOnly,
                ) {
                    var list = p.achievements
                    list = list.filter { typeFilter.matches(it.type) }
                    if (filterLockedOnly) list = list.filter { it.dateAwarded == null }
                    RaAchievementSorter.sort(list, sortField, sortDirection)
                }
                // And what the list on screen shows: the same, narrowed by the search box. Applied
                // after the sort because narrowing preserves order, so the two lists always agree
                // about which achievement comes first.
                val filteredAchievements = remember(chipFilteredAchievements, searchQuery) {
                    com.twinlore.app.domain.RaAchievementSearch.filter(chipFilteredAchievements, searchQuery)
                }
                val sortHasBeenChanged = remember { mutableStateOf(false) }
                LaunchedEffect(sortField, sortDirection) {
                    if (sortHasBeenChanged.value) {
                        listState.animateScrollToItem(0)
                    }
                    sortHasBeenChanged.value = true
                }
                // 3 non-achievement items live at the top of the LazyColumn (header + search +
                // filters), and a fourth when a search matches nothing - which is a floor for this
                // count, never a shortfall, so the clamp below stays inside the list either way.
                // Save/restore use raw firstVisibleItemIndex so the top header position round-trips cleanly.
                val totalItemCount = filteredAchievements.size + 3
                val scrollRestoredForVault = remember(vaultId, raGameId) { mutableStateOf(false) }
                LaunchedEffect(p) {
                    if (!scrollRestoredForVault.value) {
                        if (!listState.isScrollInProgress) {
                            val restoredIndex = scrollIndex.coerceIn(0, (totalItemCount - 1).coerceAtLeast(0))
                            listState.scrollToItem(
                                index = restoredIndex,
                                scrollOffset = scrollOffset
                            )
                        }
                        scrollRestoredForVault.value = true
                    }
                }
                // (auto-hide and chrome sync handled above)
                LaunchedEffect(listState, isTabVisible) {
                    if (!isTabVisible) return@LaunchedEffect
                    snapshotFlow {
                        listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
                    }.collect { (idx, off) ->
                        viewModel.setScrollPosition(idx, off)
                    }
                }
                // Focus requester for filter chips — header DOWN links to this
                val filterChipsRowRequester = remember { FocusRequester() }

                // Precompute header values outside LazyColumn items
                val headerTitle = p.gameTitle.ifBlank { gameTitle }
                val headerLogoModel: Any? = if (!p.gameImageIcon.isNullOrBlank()) {
                    val icon = p.gameImageIcon!!.trim()
                    when {
                        icon.startsWith("/") -> "https://retroachievements.org$icon"
                        icon.contains('.') -> "$RA_IMAGES_PREFIX$icon"
                        else -> "$RA_IMAGES_PREFIX$icon.png"
                    }
                } else gameLogoModel

                val listInteraction = remember { MutableInteractionSource() }
                val listFocused by listInteraction.collectIsFocusedAsState()
                Box(
                    Modifier.fillMaxSize()
                        .then(
                            if (bottomBarFocusRequester != null) Modifier.focusProperties { down = bottomBarFocusRequester }
                            else Modifier
                        )
                        .pointerInput(Unit) {
                            // Tap on empty space toggles the top chrome. Taps on
                            // list items/buttons are consumed by them, so only
                            // background taps reach this handler.
                            detectTapGestures(onTap = { showChrome = !showChrome })
                        }
                ) {
                    LazyColumn(
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        contentPadding = PaddingValues(bottom = 140.dp),
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .focusable(interactionSource = listInteraction)
                            .then(
                                if (listFocused) Modifier.border(2.dp, accentColor, RoundedCornerShape(8.dp))
                                else Modifier
                            ),
                    ) {
                        // --- Header item ---
                        item(key = "header") {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Game logo on the left (from RA API when available, else vault artwork)
                        if (headerLogoModel != null) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(
                                    model = headerLogoModel,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                        }
                        Row(
                            Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Left: stats text (wraps to 2 lines)
                            val timeStr = p.userTotalPlaytimeMinutes?.let { totalMinutes ->
                                val h = totalMinutes / 60
                                val m = totalMinutes % 60
                                "${h}h ${m}m"
                            } ?: ""
                            Row(
                                Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                val statsText = buildString {
                                    append("${p.userNumAchieved}/${p.numAchievements} · ${nf.format(p.userPoints)}/${nf.format(p.totalPoints)} pts")
                                    if (timeStr.isNotEmpty()) append(" · $timeStr")
                                }
                                Text(statsText, style = MaterialTheme.typography.bodySmall, color = sheetText, maxLines = 2)
                            }
                            // Right: action buttons
                            if (showHeaderActionRow) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    if (!isNetworkAvailable) {
                                        Icon(Icons.Default.CloudOff, contentDescription = null, modifier = Modifier.size(18.dp), tint = sheetText.copy(alpha = 0.8f))
                                        Spacer(Modifier.width(2.dp))
                                    }
                                    // Refresh (hot action)
                                    NavigableActionButton(
                                        icon = Icons.Default.Refresh,
                                        contentDescription = "Refresh achievements",
                                        onClick = { if (!loading) viewModel.refreshCurrentSet(vaultId) },
                                        accentColor = accentColor,
                                        defaultTint = if (loading) sheetText.copy(alpha = 0.4f) else sheetText,
                                        focusRequester = gridFocusRequester,
                                        iconSize = 28.dp
                                    )
                                    // Achievement journey (hot action). No key gating here: the
                                    // Journey screen renders its own Locked state.
                                    if (raGameId != null && mainViewModel != null) {
                                        NavigableActionButton(
                                            icon = Icons.Default.Timeline,
                                            contentDescription = "Achievement journey",
                                            onClick = { mainViewModel.navigateToJourney(raGameId, gameTitle) },
                                            accentColor = accentColor,
                                            defaultTint = sheetText,
                                            iconSize = 28.dp
                                        )
                                    }
                                    // Kebab dropdown menu (cold actions)
                                    var kebabExpanded by remember { mutableStateOf(false) }
                                    Box {
                                        NavigableActionButton(
                                            icon = Icons.Default.MoreVert,
                                            contentDescription = "More actions",
                                            onClick = { kebabExpanded = true },
                                            accentColor = accentColor,
                                            defaultTint = sheetText,
                                            iconSize = 28.dp
                                        )
                                        DropdownMenu(
                                            expanded = kebabExpanded,
                                            onDismissRequest = { kebabExpanded = false }
                                        ) {
                                            // Achievement wallpaper. Lives here rather than in the
                                            // row; the controller's wallpaper button is the other
                                            // way in, and the drawer is used by touch too.
                                            DropdownMenuItem(
                                                text = { Text("Achievements grid", style = MaterialTheme.typography.bodySmall) },
                                                onClick = {
                                                    viewModel.setShowWallpaperView(true)
                                                    kebabExpanded = false
                                                },
                                                leadingIcon = { Icon(Icons.Default.ViewModule, contentDescription = null, modifier = Modifier.size(20.dp)) }
                                            )
                                            // Profile
                                            raUsername?.let { username ->
                                                DropdownMenuItem(
                                                    text = { Text("Profile", style = MaterialTheme.typography.bodySmall) },
                                                    onClick = {
                                                        onOpenInBrowser("$RA_USER_PROFILE_PREFIX$username")
                                                        kebabExpanded = false
                                                    },
                                                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(20.dp)) }
                                                )
                                            }
                                            // View on RA
                                            DropdownMenuItem(
                                                text = { Text("View on RA", style = MaterialTheme.typography.bodySmall) },
                                                onClick = {
                                                    onOpenInBrowser("$RA_GAME_URL_PREFIX${p.raGameId}")
                                                    kebabExpanded = false
                                                },
                                                leadingIcon = { Icon(Icons.Default.TravelExplore, contentDescription = null, modifier = Modifier.size(20.dp)) }
                                            )
                                            // Achievement guide
                                            DropdownMenuItem(
                                                text = { Text("Achievement guide", style = MaterialTheme.typography.bodySmall) },
                                                onClick = {
                                                    if (guideUrlValue != null) showGuideOpenConfirm = true
                                                    else showNoGuideDialog = true
                                                    kebabExpanded = false
                                                },
                                                leadingIcon = { Icon(Icons.Default.MenuBook, contentDescription = null, modifier = Modifier.size(20.dp)) }
                                            )
                                            // Copy to checklist
                                            DropdownMenuItem(
                                                text = { Text("Copy to checklist", style = MaterialTheme.typography.bodySmall) },
                                                onClick = {
                                                    bulkChecklistList = filteredAchievements
                                                    showBulkChecklistConfirm = true
                                                    kebabExpanded = false
                                                },
                                                leadingIcon = { Icon(Icons.Default.PlaylistAdd, contentDescription = null, modifier = Modifier.size(20.dp)) }
                                            )
                                            // Unlink game
                                            DropdownMenuItem(
                                                text = { Text("Unlink game", style = MaterialTheme.typography.bodySmall) },
                                                onClick = {
                                                    showUnlinkConfirm = true
                                                    kebabExpanded = false
                                                },
                                                leadingIcon = { Icon(Icons.Default.LinkOff, contentDescription = null, modifier = Modifier.size(20.dp)) }
                                            )
                                            // Support RetroAchievements
                                            DropdownMenuItem(
                                                text = { Text("Support RetroAchievements", style = MaterialTheme.typography.bodySmall) },
                                                onClick = {
                                                    onOpenInBrowser("https://www.patreon.com/join/retroachievements")
                                                    kebabExpanded = false
                                                },
                                                leadingIcon = { Icon(Icons.Filled.VolunteerActivism, contentDescription = null, modifier = Modifier.size(20.dp)) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        }
                    // Progress bar: track = sheet bg, softcore = silver gradient, hardcore = gold gradient
                    val progressBarTrack = sheetBg
                    val progressBarFill = achievementBarBrush(mastered = false)
                    val progressBarFillHardcore = achievementBarBrush(mastered = true)
                    val progressBarBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f)
                    val progressPct = if (p.numAchievements > 0) p.userNumAchieved.toFloat() / p.numAchievements else 0f
                    val hardcorePct = if (p.numAchievements > 0) p.userNumHardcore.toFloat() / p.numAchievements else 0f
                    // The bar never had an end knob (that lived only on the fullscreen board, since
                    // removed there too - user request). What moves here is the award badge itself:
                    // it used to sit up by the stats text, and now sits after the bar as a sibling
                    // instead, so both surfaces put the award at the bar's right rather than in one
                    // place on the board and another on the drawer. No badge, no gap: the Spacer
                    // rides along with the icon inside the same null check, so the bar takes the
                    // full row rather than stopping short for a mark that isn't there.
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .weight(1f)
                                .border(1.dp, progressBarBorderColor, CircleShape)
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .background(progressBarTrack, CircleShape)
                            ) {
                                Box(
                                    Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(progressPct.coerceIn(0f, 1f))
                                        .background(progressBarFill, CircleShape)
                                )
                                Box(
                                    Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(hardcorePct.coerceIn(0f, 1f))
                                        .background(progressBarFillHardcore, CircleShape)
                                )
                            }
                        }
                        if (p.highestAwardKind != null) {
                            Spacer(Modifier.width(8.dp))
                            HighestAwardIcon(p.highestAwardKind)
                        }
                    }
                    // Completion % and rank; only show ranking when user has progress (avoids wrong rank when 0 achievements)
                    val completionSoft = if (p.numAchievements > 0) "${100 * p.userNumAchieved / p.numAchievements}%" else "0%"
                    val completionHard = if (p.numAchievements > 0) "${100 * p.userNumHardcore / p.numAchievements}%" else "0%"
                    val hasScore = (p.userPoints > 0) || (p.userNumAchieved > 0)
                    val rankTopStr = if (hasScore) p.userRank?.let { rank ->
                        val total = p.numDistinctPlayers ?: return@let null
                        val base = "Ranking: ${nf.format(rank)}/${nf.format(total)}"
                        val topPercent = 100.0 * rank / total.coerceAtLeast(1)
                        val topPct = when {
                            topPercent >= 99.995 -> "1%"
                            topPercent >= 1 -> "${topPercent.toInt()}%"
                            topPercent >= 0.1 -> "${"%.1f".format(topPercent)}%"
                            else -> "<0.1%"
                        }
                        "$base Top $topPct."
                    } else null
                    Row(
                        Modifier.padding(bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        // When the same set was earned in hardcore (soft count == hard
                        // count) the two percentages are identical — collapse to just
                        // the hard value. Show both only when they actually differ.
                        if (p.userNumAchieved == p.userNumHardcore) {
                            Text("$completionHard (Hard)", style = MaterialTheme.typography.labelSmall, color = sheetText)
                        } else {
                            Text("$completionSoft (Soft)", style = MaterialTheme.typography.labelSmall, color = sheetText)
                            SmallCircleBullet(sheetText, modifier = Modifier.padding(horizontal = 6.dp))
                            Text("$completionHard (Hard)", style = MaterialTheme.typography.labelSmall, color = sheetText)
                        }
                        rankTopStr?.let { str ->
                            SmallCircleBullet(sheetText, modifier = Modifier.padding(horizontal = 6.dp))
                            Text(str, style = MaterialTheme.typography.labelSmall, color = sheetText)
                        }
                    }
                    val authorsByContribution = remember(p.achievements) {
                        p.achievements
                            .mapNotNull { it.author?.takeIf { a -> a.isNotBlank() } }
                            .groupingBy { it }
                            .eachCount()
                            .entries
                            .sortedByDescending { it.value }
                            .map { it.key }
                    }
                    if (authorsByContribution.isNotEmpty()) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(bottom = 4.dp)
                                .horizontalScroll(rememberScrollState()),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Created by: ${authorsByContribution.joinToString(", ")}",
                                style = MaterialTheme.typography.labelSmall,
                                color = sheetText.copy(alpha = 0.6f),
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                        } // end item("header")

                        // --- Search item ---
                        // Always on screen above the chips, as the overlay has it; matching stays
                        // RaAchievementSearch's. D-pad focus rests on it without a keyboard.
                        item(key = "search") {
                            AchievementDrawerSearch(
                                query = searchQuery,
                                onQueryChanged = viewModel::setSearchQuery,
                                accentColor = accentColor,
                                textColor = sheetText,
                            )
                        }

                        // --- Filter chips item ---
                        item(key = "filters") {
                    // Single scrollable horizontal row: Set + Sort + Filter chips (carousel)
                    val chipColors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                        containerColor = sheetText.copy(alpha = 0.15f),
                        labelColor = sheetText,
                        selectedContainerColor = accentColor,
                        selectedLabelColor = sheetBg
                    )
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        var sortExpanded by remember { mutableStateOf(false) }
                        val sortChipInteraction = remember { MutableInteractionSource() }
                        val sortChipFocused by sortChipInteraction.collectIsFocusedAsState()
                        Box(
                            modifier = Modifier
                                .focusRequester(filterChipsRowRequester)
                                .focusable(interactionSource = sortChipInteraction)
                                .onKeyEvent { keyEvent: androidx.compose.ui.input.key.KeyEvent ->
                                    if (keyEvent.type == KeyEventType.KeyDown || keyEvent.type == KeyEventType.KeyUp) {
                                        when (keyEvent.nativeKeyEvent.keyCode) {
                                            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_BUTTON_A,
                                            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_SPACE -> {
                                                if (keyEvent.type == KeyEventType.KeyDown) {
                                                    sortExpanded = true
                                                }
                                                true
                                            }
                                            else -> false
                                        }
                                    } else false
                                }
                                .then(
                                    if (sortChipFocused) Modifier.border(2.dp, accentColor, RoundedCornerShape(8.dp))
                                    else Modifier
                                )
                        ) {
                            FilterChip(
                                selected = false,
                                onClick = { sortExpanded = true },
                                label = { Text("Sort", style = MaterialTheme.typography.labelSmall) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Filled.Sort,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                modifier = Modifier.height(28.dp),
                                colors = chipColors
                            )
                            DropdownMenu(expanded = sortExpanded, onDismissRequest = { sortExpanded = false }) {
                                RaAchievementSortField.entries.forEach { field ->
                                    val selected = field == sortField
                                    AchievementSortMenuItem(
                                        label = field.label,
                                        selected = selected,
                                        accentColor = accentColor,
                                        onClick = {
                                            // Re-picking the current field only flips its direction,
                                            // so keep the menu open to show the flip.
                                            if (!selected) sortExpanded = false
                                            viewModel.selectSort(field)
                                        }
                                    )
                                }
                            }
                        }
                        // Count for whichever type the chip is currently showing, so the number
                        // always describes the list you are about to see.
                        val typeCount = remember(p.achievements, typeFilter) {
                            if (typeFilter == com.twinlore.app.domain.RaAchievementTypeFilter.ALL) 0
                            else p.achievements.count { typeFilter.matches(it.type) }
                        }
                        val lockedCount = p.achievements.count { it.dateAwarded == null }
                        val typeChipInteraction = remember { MutableInteractionSource() }
                        val typeChipFocused by typeChipInteraction.collectIsFocusedAsState()
                        Box(
                            modifier = Modifier
                                .focusable(interactionSource = typeChipInteraction)
                                .onKeyEvent { keyEvent: androidx.compose.ui.input.key.KeyEvent ->
                                    if (keyEvent.type == KeyEventType.KeyDown || keyEvent.type == KeyEventType.KeyUp) {
                                        when (keyEvent.nativeKeyEvent.keyCode) {
                                            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_BUTTON_A,
                                            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_SPACE -> {
                                                if (keyEvent.type == KeyEventType.KeyDown) {
                                                    viewModel.cycleAchievementTypeFilter()
                                                }
                                                true
                                            }
                                            else -> false
                                        }
                                    } else false
                                }
                                .then(
                                    if (typeChipFocused) Modifier.border(2.dp, accentColor, RoundedCornerShape(8.dp))
                                    else Modifier
                                )
                        ) {
                            FilterChip(
                                selected = typeFilter != com.twinlore.app.domain.RaAchievementTypeFilter.ALL,
                                onClick = { viewModel.cycleAchievementTypeFilter() },
                                label = {
                                    Text(
                                        typeFilter.label + if (typeCount > 0) " $typeCount" else "",
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                },
                                leadingIcon = when (typeFilter) {
                                    com.twinlore.app.domain.RaAchievementTypeFilter.PROGRESSION -> {
                                        {
                                            Icon(
                                                Icons.Default.TaskAlt,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp),
                                            )
                                        }
                                    }
                                    com.twinlore.app.domain.RaAchievementTypeFilter.MISSABLE -> {
                                        {
                                            Icon(
                                                Icons.Default.Warning,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp),
                                            )
                                        }
                                    }
                                    com.twinlore.app.domain.RaAchievementTypeFilter.ALL -> null
                                },
                                modifier = Modifier.height(28.dp),
                                colors = chipColors
                            )
                        }
                        val lockedChipInteraction = remember { MutableInteractionSource() }
                        val lockedChipFocused by lockedChipInteraction.collectIsFocusedAsState()
                        Box(
                            modifier = Modifier
                                .focusable(interactionSource = lockedChipInteraction)
                                .onKeyEvent { keyEvent: androidx.compose.ui.input.key.KeyEvent ->
                                    if (keyEvent.type == KeyEventType.KeyDown || keyEvent.type == KeyEventType.KeyUp) {
                                        when (keyEvent.nativeKeyEvent.keyCode) {
                                            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_BUTTON_A,
                                            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_SPACE -> {
                                                if (keyEvent.type == KeyEventType.KeyDown) {
                                                    viewModel.setFilterLockedOnly(!filterLockedOnly)
                                                }
                                                true
                                            }
                                            else -> false
                                        }
                                    } else false
                                }
                                .then(
                                    if (lockedChipFocused) Modifier.border(2.dp, accentColor, RoundedCornerShape(8.dp))
                                    else Modifier
                                )
                        ) {
                            FilterChip(
                                selected = filterLockedOnly,
                                onClick = { viewModel.setFilterLockedOnly(!filterLockedOnly) },
                                leadingIcon = {
                                    Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp), tint = if (filterLockedOnly) sheetBg else sheetText)
                                },
                                label = { Text("Locked Only${if (lockedCount > 0) " $lockedCount" else ""}", style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.height(28.dp),
                                colors = chipColors
                            )
                        }
                    }
                        } // end item("filters")

                        // A search that matches nothing would otherwise leave the header and the
                        // chips standing over an empty page, which reads as a load that failed
                        // rather than as an answer. The other filters have their counts on their
                        // own chips, so this is the one case that needs saying out loud.
                        if (filteredAchievements.isEmpty() && searchQuery.isNotBlank()) {
                            item(key = "no-search-matches") {
                                Text(
                                    drawerSearchNoMatchText(searchQuery),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = sheetText.copy(alpha = 0.7f),
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                                )
                            }
                        }

                        // --- Achievement tiles ---
                        items(
                            items = filteredAchievements,
                            key = { it.id }
                        ) { a ->
                            RowAchievementItem(
                                achievement = a,
                                gameTitle = p.gameTitle,
                                totalDistinctPlayers = p.numDistinctPlayers ?: 0,
                                accentColor = accentColor,
                                sheetBg = sheetBg,
                                textColor = sheetText,
                                variantColor = sheetText.copy(alpha = 0.75f),
                                borderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f),
                                onShowComments = {
                                    viewModel.showCommentsForAchievement(a)
                                },
                                // Tapping a row toggles the top chrome, matching a
                                // tap on the list's empty background.
                                onTap = { showChrome = !showChrome },
                                onLongPress = {
                                    // No in-app toast — Android 13+ shows its own system
                                    // clipboard confirmation, so ours would be a duplicate.
                                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                                        as? android.content.ClipboardManager
                                    clipboard?.setPrimaryClip(
                                        android.content.ClipData.newPlainText("Achievement", a.title)
                                    )
                                },
                            )
                        }
                    } // end LazyColumn

                    // Scroll-to-top FAB
                    val showFab by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }
                    AnimatedVisibility(
                        visible = showFab,
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(bottom = 80.dp, end = 16.dp)
                    ) {
                        SmallFloatingActionButton(
                            onClick = { scope.launch { listState.animateScrollToItem(0) } },
                            containerColor = accentColor,
                            contentColor = sheetBg
                        ) {
                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Scroll to top")
                        }
                    }
                } // end Box

                    if (showWallpaperView) {
                        // Shared board: header + progress bar + full auto-fit badge grid +
                        // share-as-PNG.
                        com.twinlore.app.ui.components.AchievementsBoardView(
                            data = com.twinlore.app.ui.components.AchievementsBoardData(
                                title = p.gameTitle.ifBlank { gameTitle },
                                gameImage = p.gameImageIcon?.takeIf { it.isNotBlank() }
                                    ?.let { com.twinlore.app.data.retroachievements.RaUrls.gameImage(it) }
                                    ?: gameLogoModel,
                                earned = p.userNumAchieved,
                                total = p.numAchievements,
                                hardcoreEarned = p.userNumHardcore,
                                pointsLine = "${p.userPoints} of ${p.totalPoints} points",
                                dualBar = true,
                                highestAwardKind = p.highestAwardKind,
                                playtimeMinutes = p.userTotalPlaytimeMinutes,
                                // The newest unlock, off the full list rather than the filtered
                                // one: what the board is about is the game, and a type filter is
                                // about the tiles. Remembered because it parses a date per
                                // achievement and the drawer around it recomposes on scroll and
                                // on every filter change.
                                lastPlayedMillis = remember(p.achievements) {
                                    com.twinlore.app.ui.components.latestUnlockMillis(
                                        p.achievements.map { it.dateAwarded },
                                    )
                                },
                                // The far end of the same run, off the same list and remembered for
                                // the same reason.
                                firstUnlockMillis = remember(p.achievements) {
                                    com.twinlore.app.ui.components.earliestUnlockMillis(
                                        p.achievements.map { it.dateAwarded },
                                    )
                                },
                                // The chips' list, deliberately not the searched one - see where
                                // the two are built.
                                badges = chipFilteredAchievements.map {
                                    com.twinlore.app.ui.components.AchievementsBoardBadge(
                                        "$RA_BADGE_CDN_PREFIX${it.badgeName}.png",
                                        // Either counts as an earn - the same rule
                                        // BoardAchievements.forGame uses for the journey's board -
                                        // so a hardcore date with no softcore one still unlocks the
                                        // tile instead of leaving it locked-but-hardcore.
                                        it.dateAwarded != null || it.hardcoreAchieved,
                                        grayscaleWhenLocked = true,
                                        hardcore = it.hardcoreAchieved,
                                    )
                                },
                            ),
                            backgroundArt = gameLogoModel,
                            onDismiss = { viewModel.setShowWallpaperView(false) },
                        )
                    }
            }
            else -> {
                Text("No data", modifier = Modifier.padding(16.dp))
            }
        }
        }
    }

    if (showUnlinkConfirm) {
        AlertDialog(
            onDismissRequest = { showUnlinkConfirm = false },
            containerColor = sheetBg,
            title = { Text("Unlink RetroAchievements game?", color = sheetText) },
            text = {
                Text(
                    "This will unlink the RetroAchievements game. You can link a different game later from this screen.",
                    color = sheetText
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.unlinkGame(vaultId)
                        showUnlinkConfirm = false
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = accentColor, contentColor = sheetBg)
                ) {
                    Text("Unlink")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnlinkConfirm = false }) {
                    Text("Cancel", color = accentColor)
                }
            }
        )
    }

    if (showBulkChecklistConfirm) {
        AlertDialog(
            onDismissRequest = { showBulkChecklistConfirm = false },
            containerColor = sheetBg,
            title = { Text("Add All to Checklist", color = sheetText) },
            text = {
                Text(
                    "Add ${bulkChecklistList.size} visible achievement${if (bulkChecklistList.size == 1) "" else "s"} to this vault's checklist?\n\nAchievements will be organized into separate tabs by type (Progression, Missable, Achievements).",
                    color = sheetText
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.copyAchievementsToChecklist(vaultId, bulkChecklistList)
                        showBulkChecklistConfirm = false
                        android.widget.Toast.makeText(context, "${bulkChecklistList.size} items added to Checklist tab", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor, contentColor = sheetBg)
                ) {
                    Text("Add All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBulkChecklistConfirm = false }) {
                    Text("Cancel", color = accentColor)
                }
            }
        )
    }

    if (showGuideOpenConfirm) {
        AlertDialog(
            onDismissRequest = { showGuideOpenConfirm = false },
            containerColor = sheetBg,
            title = { Text("View Achievement Guide", color = sheetText) },
            text = {
                Text(
                    "Open the RetroAchievements community guide for this game in the Browser tab?",
                    color = sheetText
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        guideUrlValue?.let { url ->
                            mainViewModel?.setPendingUrlForBrowserTab(vaultId, url)
                            mainViewModel?.setVaultSessionTab(vaultId, com.twinlore.app.domain.VaultTab.Browser)
                        }
                        showGuideOpenConfirm = false
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = accentColor, contentColor = sheetBg)
                ) {
                    Text("Open")
                }
            },
            dismissButton = {
                TextButton(onClick = { showGuideOpenConfirm = false }) {
                    Text("Cancel", color = accentColor)
                }
            }
        )
    }

    // No-guide dialog: shown when user taps the guide button but no guide is available
    if (showNoGuideDialog) {
        AlertDialog(
            onDismissRequest = { showNoGuideDialog = false },
            containerColor = sheetBg,
            title = { Text("No guide available", color = sheetText) },
            text = {
                Text(
                    "No guide available in the RetroAchievements database. " +
                        "You can browse and save a guide manually from the Guide tab.",
                    color = sheetText
                )
            },
            confirmButton = {
                TextButton(onClick = { showNoGuideDialog = false }) {
                    Text("OK", color = accentColor)
                }
            }
        )
    }

    // Achievement comments bottom sheet
    val commentAchievement by viewModel.selectedCommentAchievement.collectAsState()
    val commentsState by viewModel.commentsState.collectAsState()
    commentAchievement?.let { ach ->
        AchievementCommentsSheet(
            achievement = ach,
            gameTitle = gameTitle,
            commentsState = commentsState,
            accentColor = accentColor,
            sheetBg = sheetBg,
            textColor = sheetText,
            variantColor = sheetText.copy(alpha = 0.75f),
            onDismiss = { viewModel.dismissComments() },
            onRetry = { viewModel.retryComments() },
            onOpenAchievementPage = {
                val url = "https://retroachievements.org/achievement/${ach.id}"
                mainViewModel?.setPendingUrlForBrowserTab(vaultId, url)
                mainViewModel?.setVaultSessionTab(vaultId, com.twinlore.app.domain.VaultTab.Browser)
                viewModel.dismissComments()
            },
            onOpenLink = { url ->
                mainViewModel?.setPendingUrlForBrowserTab(vaultId, url)
                mainViewModel?.setVaultSessionTab(vaultId, com.twinlore.app.domain.VaultTab.Browser)
                viewModel.dismissComments()
            }
        )
    }
}

/** Filterable dropdown: text field + list of suggestions. Uses same normalize/rank logic as add-game when suggestionProvider is set. */
@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
private fun FilterableDropdown(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    options: List<String>,
    optionDisplay: (String) -> String = { it },
    onOptionSelected: (String) -> Unit,
    isSelected: Boolean = false,
    modifier: Modifier = Modifier,
    maxListHeight: androidx.compose.ui.unit.Dp = 180.dp,
    suggestionProvider: GameNameSuggestionProvider? = null,
    focusColor: Color = MaterialTheme.colorScheme.primary,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    dropdownBackgroundColor: Color = MaterialTheme.colorScheme.surface,
    keyboardController: androidx.compose.ui.platform.SoftwareKeyboardController? = null,
    focusManager: androidx.compose.ui.focus.FocusManager? = null
) {
    val filtered = remember(value, options, suggestionProvider) {
        if (value.isBlank()) options
        else if (suggestionProvider != null) {
            val ranked = suggestionProvider.filterAndRankCandidates(value, options, 100)
            // Fallback: if ranking returns nothing (e.g. very short query), show simple substring matches
            if (ranked.isEmpty() && options.isNotEmpty()) {
                val q = value.trim().lowercase()
                options.filter { optionDisplay(it).lowercase().contains(q) }
            } else ranked
        } else {
            val q = value.trim().lowercase()
            options.filter { optionDisplay(it).lowercase().contains(q) }
        }
    }
    var isEditing by remember { mutableStateOf(false) }
    var hasBeenEdited by remember { mutableStateOf(false) } // Track if we ever entered edit mode
    val navFocusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    Column(modifier = modifier) {
        if (isEditing) {
            // --- EDIT MODE: real OutlinedTextField + suggestion dropdown ---
            val editFocusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
            var hasFocusedOnce by remember { mutableStateOf(false) }
            OutlinedTextField(
                value = value,
                onValueChange = { onValueChange(it) },
                label = { Text(label, color = MaterialTheme.colorScheme.onSurface) },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(editFocusRequester)
                    .onFocusChanged { state ->
                        if (state.isFocused) {
                            hasFocusedOnce = true
                        } else if (hasFocusedOnce) {
                            isEditing = false
                            keyboardController?.hide()
                        }
                    }
                    .onPreviewKeyEvent { keyEvent ->
                        val code = keyEvent.nativeKeyEvent.keyCode
                        val action = keyEvent.nativeKeyEvent.action
                        if (action == android.view.KeyEvent.ACTION_DOWN && code in listOf(
                                android.view.KeyEvent.KEYCODE_BUTTON_B,
                                android.view.KeyEvent.KEYCODE_BACK
                            )
                        ) {
                            isEditing = false
                            keyboardController?.hide()
                            true
                        } else false
                    },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    isEditing = false
                    keyboardController?.hide()
                }),
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = focusColor,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedLabelColor = focusColor,
                    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    cursorColor = focusColor,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                )
            )
            LaunchedEffect(Unit) { editFocusRequester.requestFocus() }
        } else {
            // --- NAV MODE: focusable Box, no keyboard, no suggestions ---
            val interactionSource = remember { MutableInteractionSource() }
            val isFocused by interactionSource.collectIsFocusedAsState()
            val borderColor = if (isFocused) focusColor else MaterialTheme.colorScheme.outline
            val labelColor = if (isFocused) focusColor else MaterialTheme.colorScheme.onSurfaceVariant
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(navFocusRequester)
                    .border(1.dp, borderColor, RoundedCornerShape(4.dp))
                    .clickable { isEditing = true; hasBeenEdited = true }
                    .padding(horizontal = 16.dp, vertical = 16.dp)
                    .focusable(interactionSource = interactionSource)
                    .onPreviewKeyEvent { keyEvent ->
                        val code = keyEvent.nativeKeyEvent.keyCode
                        val action = keyEvent.nativeKeyEvent.action
                        if (action == android.view.KeyEvent.ACTION_DOWN || action == android.view.KeyEvent.ACTION_UP) {
                            when {
                                code in listOf(
                                    android.view.KeyEvent.KEYCODE_DPAD_CENTER,
                                    android.view.KeyEvent.KEYCODE_BUTTON_A,
                                    android.view.KeyEvent.KEYCODE_ENTER
                                ) -> {
                                    if (action == android.view.KeyEvent.ACTION_DOWN) { isEditing = true; hasBeenEdited = true }
                                    true
                                }
                                code == android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                                    if (action == android.view.KeyEvent.ACTION_DOWN) focusManager?.moveFocus(androidx.compose.ui.focus.FocusDirection.Up)
                                    true
                                }
                                code == android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                                    if (action == android.view.KeyEvent.ACTION_DOWN) focusManager?.moveFocus(androidx.compose.ui.focus.FocusDirection.Down)
                                    true
                                }
                                else -> false
                            }
                        } else false
                    },
                contentAlignment = Alignment.CenterStart
            ) {
                if (value.isEmpty()) {
                    Text(label, color = labelColor, style = MaterialTheme.typography.bodyLarge)
                } else {
                    Text(value, color = textColor, style = MaterialTheme.typography.bodyLarge)
                }
            }
            // Re-focus nav Box only after returning from edit mode (not on initial composition)
            if (hasBeenEdited) {
                LaunchedEffect(Unit) {
                    try { navFocusRequester.requestFocus() } catch (_: Throwable) {}
                }
            }
        }

        // Suggestion list — matches the add-game dialog: it stays visible below the field
        // whenever there's a query with matches, so confirming the keyboard (which leaves edit
        // mode) no longer hides the options. It only collapses once a value is actually picked.
        val showSuggestions = filtered.isNotEmpty() && (isEditing || (value.isNotBlank() && !isSelected))
        if (showSuggestions) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxListHeight)
                    .background(dropdownBackgroundColor)
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    items(filtered.take(100)) { option ->
                        Text(
                            text = optionDisplay(option),
                            style = MaterialTheme.typography.bodyMedium,
                            color = textColor,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onOptionSelected(option)
                                    isEditing = false
                                    keyboardController?.hide()
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun UnlinkedGameStateContent(
    vaultId: String,
    gameTitle: String,
    gameLogoModel: Any? = null,
    viewModel: AchievementDrawerViewModel,
    manualGameIdInput: String,
    onManualGameIdChange: (String) -> Unit,
    onLinkManual: (currentValue: String) -> Unit,
    suggestionProvider: GameNameSuggestionProvider?,
    settingsViewModel: SettingsViewModel,
    contentFocusRequester: androidx.compose.ui.focus.FocusRequester? = null,
    mainViewModel: com.twinlore.app.ui.MainViewModel? = null
) {
    val settingsVm = settingsViewModel
    val consoles by viewModel.consoles.collectAsState()
    val games by viewModel.games.collectAsState()
    val selectedConsoleId by viewModel.selectedConsoleId.collectAsState()
    val autoAssignInProgress by viewModel.autoAssignInProgress.collectAsState()
    val menuBg = MaterialTheme.colorScheme.background
    val menuText = MaterialTheme.colorScheme.onBackground
    val focusColor by settingsVm.focusColor.collectAsState(initial = FocusColor.CRIMSON)
    val cachedFocusColor = LocalCachedFocusColor.current?.value
    val accentColor = cachedFocusColor ?: focusColor.color
    val accentContentColor = accentColor.getContrastingTextColor()
    // Match guide/map picker button style
    val paletteLinking = com.twinlore.app.ui.LocalThemePalette.current
    val isDarkLinking = paletteLinking.isDark
    val buttonSurface = paletteLinking.background.copy(alpha = 0.95f)
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    fun dismissKeyboard() {
        focusManager.clearFocus()
        keyboardController?.hide()
    }

    var consoleQuery by remember { mutableStateOf("") }
    var gameQuery by remember { mutableStateOf("") }
    var selectedConsoleName by remember { mutableStateOf<String?>(null) }
    var selectedGame by remember { mutableStateOf<RaLocalGame?>(null) }
    var autoAssignFeedback by remember(vaultId, gameTitle) { mutableStateOf<String?>(null) }

    val consoleNames = remember(consoles) { consoles.map { it.name } }

    val headerCardBg = if (menuText == Color.White) {
        Color.White.copy(alpha = 0.08f)
    } else {
        Color.Black.copy(alpha = 0.06f)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(menuBg)
    ) {
        if (gameLogoModel != null) {
            val blurContext = LocalContext.current
            val blurRequest = remember(gameLogoModel) {
                ImageRequest.Builder(blurContext)
                    .data(gameLogoModel)
                    .size(200)
                    .crossfade(true)
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
                    .background(menuBg.copy(alpha = 0.50f))
            )
        }
    // Request focus on first button so D-pad navigation works immediately
    val firstButtonFocus = contentFocusRequester ?: remember { androidx.compose.ui.focus.FocusRequester() }
    // Focus comes from bottom bar UP action — do not auto-focus here
    val unlinkScrollState = rememberScrollState()
    // Keep bottom bar always visible (same strategy as file picker)
    LaunchedEffect(Unit) {
        mainViewModel?.setVaultSessionAchievementsChromeVisible(true)
    }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(unlinkScrollState)
            .padding(horizontal = 16.dp)
            .padding(top = 14.dp, bottom = 80.dp)
    ) {
        // --- Auto assign ---
        NavigableButton(
            text = if (autoAssignInProgress) "Matching…" else if (autoAssignFeedback != null) "Auto assign — ${autoAssignFeedback!!}" else "Auto assign",
            onClick = {
                dismissKeyboard()
                autoAssignFeedback = null
                viewModel.runAutoAssign(vaultId, gameTitle) { result ->
                    autoAssignFeedback = if (result == null) "No match found" else null
                }
            },
            enabled = !autoAssignInProgress && gameTitle.isNotBlank(),
            containerColor = accentColor,
            contentColor = accentContentColor,
            focusId = "ra_auto_assign",
            externalFocusRequester = firstButtonFocus
        )
        Spacer(Modifier.height(16.dp))

        // --- Divider ---
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(Modifier.weight(1f).height(1.dp).background(menuText.copy(alpha = 0.2f)))
            Text(
                text = "  or assign manually  ",
                style = MaterialTheme.typography.labelSmall,
                color = menuText.copy(alpha = 0.5f)
            )
            Box(Modifier.weight(1f).height(1.dp).background(menuText.copy(alpha = 0.2f)))
        }
        Spacer(Modifier.height(16.dp))

        // --- Choose from list ---
        Text("Choose from list", style = MaterialTheme.typography.labelMedium, color = menuText)
        Spacer(Modifier.height(6.dp))
        FilterableDropdown(
            value = selectedConsoleName ?: consoleQuery,
            onValueChange = { q ->
                consoleQuery = q
                if (selectedConsoleName != null && selectedConsoleName != q) {
                    selectedConsoleName = null
                    selectedGame = null
                    viewModel.selectConsole(null)
                }
            },
            label = "Console",
            options = consoleNames,
            optionDisplay = { it },
            onOptionSelected = { name ->
                val c = consoles.find { it.name == name }
                if (c != null) {
                    selectedConsoleName = name
                    consoleQuery = ""
                    viewModel.selectConsole(c.id)
                    gameQuery = ""
                    selectedGame = null
                }
            },
            isSelected = selectedConsoleName != null,
            suggestionProvider = suggestionProvider,
            focusColor = accentColor,
            textColor = menuText,
            dropdownBackgroundColor = menuBg,
            keyboardController = keyboardController,
            focusManager = focusManager
        )
        Spacer(Modifier.height(8.dp))
        if (selectedConsoleId != null) {
            val gameTitles = remember(games) { games.map { it.title } }
            FilterableDropdown(
                value = selectedGame?.title ?: gameQuery,
                onValueChange = { q ->
                    gameQuery = q
                    if (selectedGame != null) selectedGame = null
                },
                label = "Game",
                options = gameTitles,
                optionDisplay = { it },
                onOptionSelected = { title ->
                    val g = games.find { it.title == title }
                    if (g != null) {
                        selectedGame = g
                        gameQuery = ""
                    }
                },
                isSelected = selectedGame != null,
                suggestionProvider = suggestionProvider,
                focusColor = accentColor,
                textColor = menuText,
                dropdownBackgroundColor = menuBg,
                keyboardController = keyboardController,
                focusManager = focusManager
            )
            if (selectedGame != null) {
                val game = selectedGame!!
                Spacer(Modifier.height(8.dp))
                NavigableButton(
                    text = "Link \"${game.title}\"",
                    onClick = {
                        dismissKeyboard()
                        viewModel.linkGameFromList(vaultId, game.id)
                    },
                    containerColor = buttonSurface,
                    contentColor = accentColor,
                    focusId = "ra_link_from_list"
                )
            }
        }
        Spacer(Modifier.height(20.dp))

        // --- Manual Game ID ---
        Text("Or enter Game ID manually", style = MaterialTheme.typography.labelMedium, color = menuText)
        Spacer(Modifier.height(6.dp))
        com.twinlore.app.ui.navigation.NavigableTextField(
            value = manualGameIdInput,
            onValueChange = { onManualGameIdChange(it.filter { c -> c.isDigit() }) },
            label = "Game ID",
            keyboardType = KeyboardType.Number,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))
        NavigableButton(
            text = "Link game",
            onClick = {
                dismissKeyboard()
                onLinkManual(manualGameIdInput)
            },
            containerColor = buttonSurface,
            contentColor = accentColor,
            focusId = "ra_link_manual"
        )
        Spacer(Modifier.height(80.dp))
    }
    }
}

@OptIn(ExperimentalFoundationApi::class)
// internal, not private: the fold panel renders the same row, so the badge
// artwork, the greyscale on locked achievements and the accent on unlocked
// ones are one implementation rather than two that drift.
@Composable
internal fun RowAchievementItem(
    achievement: com.twinlore.app.data.retroachievements.RaAchievementDisplay,
    @Suppress("UNUSED_PARAMETER") gameTitle: String,
    totalDistinctPlayers: Int,
    accentColor: androidx.compose.ui.graphics.Color,
    sheetBg: androidx.compose.ui.graphics.Color,
    textColor: androidx.compose.ui.graphics.Color,
    variantColor: androidx.compose.ui.graphics.Color,
    borderColor: androidx.compose.ui.graphics.Color,
    onShowComments: () -> Unit,
    onLongPress: () -> Unit,
    onTap: () -> Unit = {},
) {
    // Either date is an earn, not just dateAwarded - the same rule BoardAchievements.forGame and
    // the wallpaper board's own badge construction above already use (see the "Either counts as
    // an earn" comment where filteredAchievements builds AchievementsBoardBadge), so all three
    // read as one decision. dateAwarded and hardcoreAchieved come from different sources upstream
    // (RaGameCacheRepository), and an achievement earned only in hardcore was reading as locked
    // and getting greyscaled here.
    val isUnlocked = achievement.dateAwarded != null || achievement.hardcoreAchieved
    val pointsDisplay = achievement.points
    val trueRatioDisplay = achievement.trueRatio
    val titleColor = if (isUnlocked) accentColor else textColor
    val badgeName = achievement.badgeName
    val badgeUrl = if (badgeName.isNotBlank()) "$RA_BADGE_CDN_PREFIX$badgeName.png" else null
    val context = LocalContext.current
    val imageRequest = remember(badgeUrl, achievement.id) {
        if (badgeUrl != null) {
            ImageRequest.Builder(context)
                .data(badgeUrl)
                .build()
        } else null
    }
    val grayscale = ColorMatrix().apply { setToSaturation(if (isUnlocked) 1f else 0f) }
    val totalPlayers = totalDistinctPlayers.coerceAtLeast(1)
    val softWon = achievement.numAwarded ?: 0
    val hardWon = achievement.numAwardedHardcore ?: 0
    val softPct = if (totalPlayers > 0) 100.0 * softWon / totalPlayers else 0.0
    val hardPct = if (totalPlayers > 0) 100.0 * hardWon / totalPlayers else 0.0

    val itemInteractionSource = remember { MutableInteractionSource() }
    val itemFocused by itemInteractionSource.collectIsFocusedAsState()

    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .then(
                if (itemFocused) Modifier.border(2.dp, accentColor, RoundedCornerShape(8.dp))
                else Modifier
            )
            .combinedClickable(
                interactionSource = itemInteractionSource,
                indication = null,
                onLongClick = { onLongPress() },
                onClick = { onTap() }
            )
            .focusable(interactionSource = itemInteractionSource)
            .onKeyEvent { keyEvent: androidx.compose.ui.input.key.KeyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown || keyEvent.type == KeyEventType.KeyUp) {
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_CENTER,
                        KeyEvent.KEYCODE_BUTTON_A,
                        KeyEvent.KEYCODE_ENTER,
                        KeyEvent.KEYCODE_SPACE -> {
                            if (keyEvent.type == KeyEventType.KeyDown) {
                                onShowComments()
                            }
                            true
                        }
                        else -> false
                    }
                } else false
            }
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Ringed in RA's own hardcore yellow, same rule and same shape as the wallpaper
            // board's grid (com.twinlore.app.ui.components.RingedBadge, shared by both so this
            // is one implementation rather than two that can drift): only an earned achievement
            // can be ringed, and the ring is always reserved space - see RingedBadge's own doc -
            // so a hardcore row never sits at a different width than its neighbours.
            com.twinlore.app.ui.components.RingedBadge(
                art = 75.dp,
                ringed = isUnlocked && achievement.hardcoreAchieved,
                modifier = Modifier.padding(end = 8.dp),
            ) {
                AsyncImage(
                    model = imageRequest,
                    contentDescription = null,
                    modifier = Modifier.size(75.dp),
                    colorFilter = if (imageRequest != null && !isUnlocked) ColorFilter.colorMatrix(grayscale) else null
                )
            }
            Column(Modifier.weight(1f)) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        achievement.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = titleColor,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "($pointsDisplay)",
                        style = MaterialTheme.typography.labelSmall,
                        color = variantColor
                    )
                    Text(
                        "($trueRatioDisplay)",
                        style = MaterialTheme.typography.labelSmall,
                        color = titleColor
                    )
                }
                if (achievement.description.isNotBlank()) {
                    Text(
                        achievement.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = variantColor
                    )
                }
                // One Text, not a Row of two: on a narrow screen the players count took the
                // whole width and the percent got squeezed into a one-character-per-line
                // vertical sliver. A single string wraps at word boundaries instead.
                Text(
                    "${nf.format(softWon)} (${nf.format(hardWon)}) of ${nf.format(totalPlayers)} - ${"%.1f".format(softPct)}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = variantColor
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp)
                        .height(6.dp)
                        .border(1.dp, borderColor, CircleShape)
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .fillMaxHeight()
                            .background(sheetBg, CircleShape)
                    ) {
                        Box(
                            Modifier
                                .fillMaxHeight()
                                .fillMaxWidth((softPct / 100.0).toFloat().coerceIn(0f, 1f))
                                .background(achievementBarBrush(mastered = false), CircleShape)
                        )
                        Box(
                            Modifier
                                .fillMaxHeight()
                                .fillMaxWidth((hardPct / 100.0).toFloat().coerceIn(0f, 1f))
                                .background(achievementBarBrush(mastered = true), CircleShape)
                        )
                    }
                }
            }
            Column(
                modifier = Modifier.width(48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                AchievementTypeIcon(
                    achievement.type,
                    tint = if (isUnlocked) accentColor else variantColor,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
                if (isUnlocked) {
                    // When unlocked: type icon already above; show check only if no type (so one icon total, no duplicate).
                    if (achievement.type == null) {
                        Icon(Icons.Default.TaskAlt, contentDescription = "Unlocked", modifier = Modifier.size(20.dp), tint = accentColor)
                    } else {
                        Spacer(Modifier.height(2.dp))
                    }
                }
                // Comments icon on ALL items (unlocked and locked)
                IconButton(
                    onClick = onShowComments,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Default.ChatBubble, contentDescription = "View comments", modifier = Modifier.size(20.dp), tint = accentColor)
                }
            }
        }
    }
}

/** Format ISO 8601 timestamp to a relative or friendly format. */
internal fun formatCommentTime(submitted: String?): String {
    if (submitted.isNullOrBlank()) return ""
    return try {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS", java.util.Locale.US)
        sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
        val date = sdf.parse(submitted) ?: run {
            // Fallback: try without fractional seconds
            val sdf2 = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
            sdf2.timeZone = java.util.TimeZone.getTimeZone("UTC")
            sdf2.parse(submitted)
        } ?: return submitted
        val now = System.currentTimeMillis()
        val diff = now - date.time
        val minutes = diff / 60_000
        val hours = minutes / 60
        val days = hours / 24
        when {
            minutes < 1 -> "just now"
            minutes < 60 -> "${minutes}m ago"
            hours < 24 -> "${hours}h ago"
            days < 7 -> "${days}d ago"
            days < 365 -> {
                val outFmt = java.text.SimpleDateFormat("MMM d", java.util.Locale.US)
                outFmt.format(date)
            }
            else -> {
                val outFmt = java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.US)
                outFmt.format(date)
            }
        }
    } catch (_: Exception) {
        submitted
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AchievementCommentsSheet(
    achievement: RaAchievementDisplay,
    gameTitle: String,
    commentsState: AchievementDrawerViewModel.CommentsState,
    accentColor: Color,
    sheetBg: Color,
    textColor: Color,
    variantColor: Color,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onOpenAchievementPage: () -> Unit,
    onOpenLink: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = sheetBg,
        dragHandle = null
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Achievement header
            val badgeUrl = if (achievement.badgeName.isNotBlank())
                "$RA_BADGE_CDN_PREFIX${achievement.badgeName}.png" else null
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (badgeUrl != null) {
                    AsyncImage(
                        model = badgeUrl,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp).padding(end = 12.dp)
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        achievement.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = accentColor
                    )
                    if (achievement.description.isNotBlank()) {
                        Text(
                            achievement.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = variantColor
                        )
                    }
                    Text(
                        "${achievement.points} points (${achievement.trueRatio} RetroPoints)",
                        style = MaterialTheme.typography.labelSmall,
                        color = variantColor
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Open achievement page button
            Button(
                onClick = onOpenAchievementPage,
                colors = ButtonDefaults.buttonColors(
                    containerColor = accentColor,
                    contentColor = sheetBg
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.TravelExplore, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Open on RetroAchievements")
            }

            Spacer(Modifier.height(8.dp))

            // Search YouTube for this achievement (game name + achievement title) —
            // handy for finding how-to-unlock videos. Opens in the in-app Browser tab.
            OutlinedButton(
                onClick = {
                    val query = listOf(gameTitle, achievement.title, "retro achievements")
                        .filter { it.isNotBlank() }
                        .joinToString(" ")
                    val url = "https://www.youtube.com/results?search_query=${Uri.encode(query)}"
                    onOpenLink(url)
                },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = accentColor),
                border = BorderStroke(1.dp, accentColor.copy(alpha = 0.6f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.SmartDisplay, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Search on YouTube")
            }

            Spacer(Modifier.height(16.dp))

            // Comments section
            Text(
                "Comments",
                style = MaterialTheme.typography.titleSmall,
                color = textColor
            )
            Spacer(Modifier.height(8.dp))

            when (commentsState) {
                is AchievementDrawerViewModel.CommentsState.Loading -> {
                    Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = accentColor, modifier = Modifier.size(32.dp))
                    }
                }
                is AchievementDrawerViewModel.CommentsState.Error -> {
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(commentsState.message, color = variantColor, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = onRetry) {
                            Text("Retry", color = accentColor)
                        }
                    }
                }
                is AchievementDrawerViewModel.CommentsState.Success -> {
                    if (commentsState.comments.isEmpty()) {
                        Text(
                            "No comments yet.",
                            color = variantColor,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.heightIn(max = 400.dp)
                        ) {
                            // No key. Both fields come back from RA through a
                            // nullable, so two comments missing a user or
                            // sharing a second collide on one key and
                            // LazyColumn throws. The list is set once per load
                            // and never reordered, so position is the honest
                            // identity here.
                            items(commentsState.comments) { comment ->
                                Column(Modifier.fillMaxWidth()) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            comment.user ?: "Unknown",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = accentColor
                                        )
                                        Text(
                                            formatCommentTime(comment.submitted),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = variantColor
                                        )
                                    }
                                    LinkifiedCommentText(
                                        text = comment.commentText ?: "",
                                        textColor = textColor,
                                        linkColor = accentColor,
                                        style = MaterialTheme.typography.bodySmall,
                                        onLinkClick = onOpenLink,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                else -> {}
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

private val COMMENT_URL_REGEX = Regex("""(https?://\S+|www\.\S+)""")

// internal, not private: the fold panel draws the same comment threads, and a
// link has to look like a link in both places even though only one of them can
// follow it. The panel hands this an onLinkClick that refuses instead.
@Composable
internal fun LinkifiedCommentText(
    text: String,
    textColor: Color,
    linkColor: Color,
    style: androidx.compose.ui.text.TextStyle,
    onLinkClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val annotated = remember(text, textColor, linkColor) {
        buildAnnotatedString {
            var lastIdx = 0
            COMMENT_URL_REGEX.findAll(text).forEach { match ->
                if (match.range.first > lastIdx) append(text.substring(lastIdx, match.range.first))
                val raw = match.value
                val trailing = raw.takeLastWhile { it in ".,;:!?)]" }
                val urlPart = raw.dropLast(trailing.length)
                if (urlPart.length < 5) {
                    append(raw)
                } else {
                    val href = if (urlPart.startsWith("www.", ignoreCase = true)) "https://$urlPart" else urlPart
                    pushStringAnnotation(tag = "URL", annotation = href)
                    withStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)) {
                        append(urlPart)
                    }
                    pop()
                    if (trailing.isNotEmpty()) append(trailing)
                }
                lastIdx = match.range.last + 1
            }
            if (lastIdx < text.length) append(text.substring(lastIdx))
        }
    }
    if (annotated.getStringAnnotations("URL", 0, annotated.length).isEmpty()) {
        Text(text = text, style = style, color = textColor, modifier = modifier)
    } else {
        ClickableText(
            text = annotated,
            style = style.copy(color = textColor),
            modifier = modifier,
            onClick = { offset ->
                annotated.getStringAnnotations(tag = "URL", start = offset, end = offset)
                    .firstOrNull()
                    ?.let { onLinkClick(it.item) }
            }
        )
    }
}
