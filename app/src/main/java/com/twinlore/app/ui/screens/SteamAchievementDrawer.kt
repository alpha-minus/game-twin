package com.twinlore.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.view.KeyEvent
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.twinlore.app.data.FocusColor
import com.twinlore.app.data.steam.SteamAchievementDisplay
import com.twinlore.app.data.steam.SteamOwnedGame
import com.twinlore.app.ui.LocalCachedFocusColor
import com.twinlore.app.ui.components.accentBarBrush
import com.twinlore.app.ui.navigation.ControllerTextField
import com.twinlore.app.ui.navigation.NavigableActionButton
import com.twinlore.app.ui.navigation.NavigableButton
import com.twinlore.app.ui.theme.getContrastingTextColor
import com.twinlore.app.ui.theme.ChecklistColorPalette
import com.twinlore.app.domain.ChecklistTab
import com.twinlore.app.domain.SteamAchievementSortField
import com.twinlore.app.domain.SteamAchievementSorter
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.sqrt

private const val STEAM_STORE_PREFIX = "https://store.steampowered.com/app/"

// ---------- Achievement tile ----------

private val steamUnlockDateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SteamAchievementTile(
    achievement: SteamAchievementDisplay,
    gameTitle: String,
    accentColor: Color,
    sheetBg: Color,
    textColor: Color,
    variantColor: Color,
    onSearchUnlock: () -> Unit,
    onAddToChecklist: () -> Unit,
) {
    val isUnlocked = achievement.achieved
    val titleColor = if (isUnlocked) accentColor else textColor
    val iconUrl = if (isUnlocked) achievement.iconUrl else achievement.iconGrayUrl
    val context = LocalContext.current
    val imageRequest = remember(iconUrl, achievement.apiName) {
        if (!iconUrl.isNullOrBlank()) {
            ImageRequest.Builder(context).data(iconUrl).crossfade(true).build()
        } else null
    }
    val grayscale = ColorMatrix().apply { setToSaturation(if (isUnlocked) 1f else 0f) }

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
                onLongClick = { onAddToChecklist() },
                onClick = {}
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
                                onSearchUnlock()
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
            AsyncImage(
                model = imageRequest,
                contentDescription = null,
                modifier = Modifier.size(64.dp).padding(end = 8.dp),
                colorFilter = if (imageRequest != null && !isUnlocked) ColorFilter.colorMatrix(grayscale) else null
            )
            Column(Modifier.weight(1f)) {
                Text(
                    achievement.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = titleColor
                )
                val desc = achievement.description
                if (!desc.isNullOrBlank()) {
                    Text(
                        desc,
                        style = MaterialTheme.typography.bodySmall,
                        color = variantColor
                    )
                }
                if (isUnlocked && achievement.unlockTime != null && achievement.unlockTime > 0) {
                    Text(
                        "Unlocked ${steamUnlockDateFormat.format(Date(achievement.unlockTime * 1000))}",
                        style = MaterialTheme.typography.labelSmall,
                        color = variantColor
                    )
                }
                if (achievement.globalPercent != null) {
                    Text(
                        "${"%.1f".format(achievement.globalPercent)}% of players",
                        style = MaterialTheme.typography.labelSmall,
                        color = variantColor.copy(alpha = 0.7f)
                    )
                }
            }
            Column(
                modifier = Modifier.width(48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if (isUnlocked) {
                    Icon(Icons.Default.TaskAlt, contentDescription = "Unlocked", modifier = Modifier.size(20.dp), tint = accentColor)
                }
                // Search icon on ALL items (unlocked and locked)
                IconButton(
                    onClick = onSearchUnlock,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Default.TravelExplore, contentDescription = "Search how to unlock", modifier = Modifier.size(20.dp), tint = accentColor)
                }
            }
        }
    }
}

// ---------- Not-linked UI ----------

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun SteamUnlinkedContent(
    vaultId: String,
    gameTitle: String,
    gameLogoModel: Any?,
    viewModel: SteamAchievementDrawerViewModel,
    manualAppIdInput: String,
    onManualAppIdChange: (String) -> Unit,
    onLinkManual: (String) -> Unit,
    settingsViewModel: SettingsViewModel,
    contentFocusRequester: FocusRequester? = null,
    mainViewModel: com.twinlore.app.ui.MainViewModel? = null
) {
    val autoAssignInProgress by viewModel.autoAssignInProgress.collectAsState()
    val ownedGamesState by viewModel.ownedGamesState.collectAsState()
    val menuBg = MaterialTheme.colorScheme.background
    val menuText = MaterialTheme.colorScheme.onBackground
    val focusColor by settingsViewModel.focusColor.collectAsState(initial = FocusColor.CRIMSON)
    val cachedFocusColor = LocalCachedFocusColor.current?.value
    val accentColor = cachedFocusColor ?: focusColor.color
    val accentContentColor = accentColor.getContrastingTextColor()
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val paletteLinking = com.twinlore.app.ui.LocalThemePalette.current
    val isDarkLinking = paletteLinking.isDark
    val buttonSurface = paletteLinking.background.copy(alpha = 0.95f)

    fun dismissKeyboard() {
        focusManager.clearFocus()
        keyboardController?.hide()
    }

    var autoAssignFeedback by remember(vaultId, gameTitle) { mutableStateOf<String?>(null) }
    var showOwnedGamesDialog by remember { mutableStateOf(false) }

    val headerCardBg = if (menuText == Color.White) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)

    Box(
        modifier = Modifier.fillMaxSize().background(menuBg)
    ) {
        if (gameLogoModel != null) {
            val blurContext = LocalContext.current
            val blurRequest = remember(gameLogoModel) {
                ImageRequest.Builder(blurContext).data(gameLogoModel).size(200).crossfade(true).build()
            }
            AsyncImage(
                model = blurRequest,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().blur(radius = 24.dp).alpha(0.5f),
                contentScale = ContentScale.Crop
            )
            Box(modifier = Modifier.fillMaxSize().background(menuBg.copy(alpha = 0.50f)))
        }
        // Request focus on first button so D-pad navigation works immediately
        val firstButtonFocus = contentFocusRequester ?: remember { androidx.compose.ui.focus.FocusRequester() }
        // Focus comes from bottom bar UP action — do not auto-focus here
        val unlinkScrollState = rememberScrollState()
        // Scroll-based bottom bar: show at top, hide when scrolled
        val unlinkChromeVisible = unlinkScrollState.value < 10
        LaunchedEffect(unlinkChromeVisible) {
            mainViewModel?.setVaultSessionAchievementsChromeVisible(unlinkChromeVisible)
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
                text = if (autoAssignInProgress) "Matching..." else if (autoAssignFeedback != null) "Auto assign — ${autoAssignFeedback!!}" else "Auto assign",
                onClick = {
                    dismissKeyboard()
                    autoAssignFeedback = null
                    viewModel.runAutoAssign(vaultId, gameTitle) { result ->
                        autoAssignFeedback = if (result == null) "No match found" else null
                    }
                },
                enabled = !autoAssignInProgress && gameTitle.isNotBlank(),
                containerColor = accentColor,
                contentColor = accentColor.getContrastingTextColor(),
                focusId = "steam_auto_assign",
                externalFocusRequester = firstButtonFocus
            )
            // Show assignment error (e.g. no match found, API error)
            val assignError by viewModel.assignError.collectAsState()
            if (assignError != null) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = assignError!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
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

            // --- Choose from library ---
            Text("Choose from library", style = MaterialTheme.typography.labelMedium, color = menuText)
            Spacer(Modifier.height(6.dp))
            NavigableButton(
                text = "Choose from library",
                onClick = {
                    dismissKeyboard()
                    viewModel.loadOwnedGames()
                    showOwnedGamesDialog = true
                },
                containerColor = buttonSurface,
                contentColor = accentColor,
                focusId = "steam_choose_library"
            )
            Spacer(Modifier.height(20.dp))

            // --- Manual App ID ---
            Text("Or enter App ID manually", style = MaterialTheme.typography.labelMedium, color = menuText)
            Text(
                "The number from the Steam store URL: store.steampowered.com/app/{AppID}/...",
                style = MaterialTheme.typography.bodySmall,
                color = menuText.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(6.dp))
            com.twinlore.app.ui.navigation.NavigableTextField(
                value = manualAppIdInput,
                onValueChange = { onManualAppIdChange(it.filter { c -> c.isDigit() }) },
                label = "App ID",
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                modifier = Modifier.fillMaxWidth(),
                onSubmit = { onLinkManual(manualAppIdInput) }
            )
            Spacer(Modifier.height(10.dp))
            NavigableButton(
                text = "Link game",
                onClick = {
                    dismissKeyboard()
                    onLinkManual(manualAppIdInput)
                },
                containerColor = buttonSurface,
                contentColor = accentColor,
                focusId = "steam_link_manual"
            )
            Spacer(Modifier.height(80.dp))
        }
    }

    if (showOwnedGamesDialog) {
        SteamOwnedGamesDialog(
            state = ownedGamesState,
            onRetry = viewModel::loadOwnedGames,
            accentColor = accentColor,
            onSelect = { game ->
                showOwnedGamesDialog = false
                viewModel.linkGameFromList(vaultId, game.appId)
            },
            onDismiss = { showOwnedGamesDialog = false }
        )
    }
}

// ---------- Owned games selection dialog ----------

/** What the dialog says when Steam returns no games for the connected account. */
internal const val STEAM_OWNED_GAMES_EMPTY =
    "Steam doesn't share this account's games. Set Game details to Public in your Steam privacy settings, or enter the App ID instead."

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun SteamOwnedGamesDialog(
    state: SteamOwnedGamesState,
    onRetry: () -> Unit,
    accentColor: Color,
    onSelect: (SteamOwnedGame) -> Unit,
    onDismiss: () -> Unit
) {
    val dialogBg = MaterialTheme.colorScheme.background
    val dialogText = MaterialTheme.colorScheme.onBackground

    var searchQuery by remember { mutableStateOf("") }
    val ownedGames = (state as? SteamOwnedGamesState.Loaded)?.games.orEmpty()
    val sortedGames = remember(ownedGames) {
        // Other accounts' owned games carry no last-played date under the bundled
        // key (see SteamLastPlayed), so playtime orders what the date cannot.
        ownedGames.sortedWith(
            compareByDescending<com.twinlore.app.data.steam.SteamOwnedGame> { it.rtimeLastPlayed }
                .thenByDescending { it.playtimeForever }
        )
    }
    val filtered = remember(sortedGames, searchQuery) {
        if (searchQuery.isBlank()) sortedGames
        else {
            val q = searchQuery.trim().lowercase()
            sortedGames.filter { it.name?.lowercase()?.contains(q) == true }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = dialogBg,
        title = { Text("Choose a game", color = dialogText) },
        text = {
            Column {
                com.twinlore.app.ui.navigation.NavigableTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = "Search",
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                if (state is SteamOwnedGamesState.Loading) {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = accentColor)
                    }
                } else if (state is SteamOwnedGamesState.Empty) {
                    Text(
                        text = STEAM_OWNED_GAMES_EMPTY,
                        style = MaterialTheme.typography.bodyMedium,
                        color = dialogText,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                } else if (state is SteamOwnedGamesState.Failed) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        TextButton(onClick = onRetry) { Text("Retry", color = accentColor) }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
                        verticalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        items(filtered) { game ->
                            Text(
                                text = game.name ?: "App ${game.appId}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = dialogText,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelect(game) }
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = accentColor)
            }
        }
    )
}

// ---------- Main composables ----------

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun SteamAchievementContent(
    vaultId: String,
    gameTitle: String,
    steamAppId: Int?,
    gameLogoModel: Any? = null,
    onOpenInBrowser: (String) -> Unit,
    onDismiss: () -> Unit,
    viewModel: SteamAchievementDrawerViewModel = hiltViewModel(),
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
    var manualAppIdInput by remember { mutableStateOf("") }
    var showUnlinkConfirm by remember { mutableStateOf(false) }
    var checklistSingleAchievement by remember { mutableStateOf<com.twinlore.app.data.steam.SteamAchievementDisplay?>(null) }
    var showBulkChecklistConfirm by remember { mutableStateOf(false) }
    var bulkChecklistList by remember { mutableStateOf<List<com.twinlore.app.data.steam.SteamAchievementDisplay>>(emptyList()) }
    var showNoGuideDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val focusColor by settingsVm.focusColor.collectAsState(initial = FocusColor.CRIMSON)
    val sheetBg = MaterialTheme.colorScheme.background
    val sheetText = MaterialTheme.colorScheme.onBackground
    val cachedFocusColor = LocalCachedFocusColor.current?.value
    val accentColor = cachedFocusColor ?: focusColor.color
    val showWallpaperView by viewModel.showWallpaperView.collectAsState()
    val duplicateLinkMessage by viewModel.duplicateLinkMessage.collectAsState()

    LaunchedEffect(duplicateLinkMessage) {
        duplicateLinkMessage?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.clearDuplicateLinkMessage()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.checkConnectivity()
    }

    LaunchedEffect(vaultId, steamAppId) {
        if (steamAppId != null) {
            // On-open auto-refresh, but skip the network if refreshed within the last
            // minute (e.g. reopening the tab). The manual Refresh button stays forced.
            viewModel.refresh(vaultId, steamAppId, minRefreshIntervalMs = 60_000L)
        }
    }

    // Listen for controller actions (X = wallpaper grid view, Y = refresh)
    if (mainViewModel != null && isTabVisible) {
        val controllerAction by mainViewModel.controllerAction.collectAsState()
        LaunchedEffect(controllerAction) {
            when (controllerAction) {
                com.twinlore.app.ui.MainViewModel.ControllerAction.ACHIEVEMENTS_WALLPAPER -> {
                    if (steamAppId != null) viewModel.setShowWallpaperView(!showWallpaperView)
                }
                com.twinlore.app.ui.MainViewModel.ControllerAction.ACHIEVEMENTS_REFRESH -> {
                    if (steamAppId != null) viewModel.refresh(vaultId, steamAppId)
                }
                else -> {}
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        if (steamAppId == null) {
            SteamUnlinkedContent(
                vaultId = vaultId,
                gameTitle = gameTitle,
                gameLogoModel = gameLogoModel,
                viewModel = viewModel,
                manualAppIdInput = manualAppIdInput,
                onManualAppIdChange = { manualAppIdInput = it },
                onLinkManual = { currentValue ->
                    val id = currentValue.trim().toIntOrNull()
                    if (id != null) {
                        scope.launch {
                            viewModel.validateAndLinkGame(vaultId, id)
                                .onSuccess { manualAppIdInput = "" }
                                .onFailure { e ->
                                    android.widget.Toast.makeText(
                                        context,
                                        e.message ?: "Could not link game",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }
                        }
                    } else {
                        android.widget.Toast.makeText(context, "Enter a valid App ID", android.widget.Toast.LENGTH_SHORT).show()
                    }
                },
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
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            error!!,
                            color = MaterialTheme.colorScheme.onBackground,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(onClick = { viewModel.refresh(vaultId, steamAppId) }) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Retry")
                            }
                            OutlinedButton(onClick = { showUnlinkConfirm = true }) {
                                Icon(Icons.Default.LinkOff, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Unlink Game")
                            }
                        }
                    }
                }
                progress != null -> {
                    val p = progress!!
                    // Focus comes from bottom bar UP action — do not auto-focus here
                    val sortField by viewModel.sortField.collectAsState()
                    val sortDirection by viewModel.sortDirection.collectAsState()
                    val filterLockedOnly by viewModel.filterLockedOnly.collectAsState()
                    val scrollIndex by viewModel.scrollIndex.collectAsState()
                    val scrollOffset by viewModel.scrollOffset.collectAsState()
                    val searchQuery by viewModel.searchQuery.collectAsState()
                    val listState = rememberLazyListState()

                    // The query is the view model's; see RetroAchievementContent for why.

                    val gridFocusRequester = contentFocusRequester ?: remember { FocusRequester() }

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

                    // What the chips say, and what the wallpaper board draws - deliberately not the
                    // searched list below, for the reason spelled out in RetroAchievementContent:
                    // a board narrowed by a query the user cannot see exports a picture that
                    // misstates the game.
                    val sortedAchievements = remember(p.achievements, sortField, sortDirection, filterLockedOnly) {
                        var list = SteamAchievementSorter.sort(p.achievements, sortField, sortDirection)
                        if (filterLockedOnly) list = list.filter { !it.achieved }
                        list
                    }
                    // And what the list on screen shows.
                    val filteredAchievements = remember(sortedAchievements, searchQuery) {
                        com.twinlore.app.domain.SteamAchievementSearch.filter(sortedAchievements, searchQuery)
                    }
                    val sortHasBeenChanged = remember { mutableStateOf(false) }
                    LaunchedEffect(sortField, sortDirection) {
                        if (sortHasBeenChanged.value) {
                            listState.animateScrollToItem(0)
                        }
                        sortHasBeenChanged.value = true
                    }
                    // 3 non-achievement items live at the top of the LazyColumn (header + search +
                    // filters).
                    // Save/restore use raw firstVisibleItemIndex so the top header position round-trips cleanly.
                    val totalItemCount = sortedAchievements.size + 3
                    val scrollRestoredForVault = remember(vaultId, steamAppId) { mutableStateOf(false) }
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
                    val headerLogoModel: Any? = p.headerImageUrl ?: gameLogoModel

                    val listInteraction = remember { MutableInteractionSource() }
                    val listFocused by listInteraction.collectIsFocusedAsState()
                    Box(
                        Modifier.fillMaxSize()
                            .then(
                                if (bottomBarFocusRequester != null) Modifier.focusProperties { down = bottomBarFocusRequester }
                                else Modifier
                            )
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
                                // Left: game hero image
                                if (headerLogoModel != null) {
                                    Box(
                                        modifier = Modifier
                                            .heightIn(max = 100.dp)
                                            .clip(RoundedCornerShape(6.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        AsyncImage(
                                            model = headerLogoModel,
                                            contentDescription = null,
                                            contentScale = ContentScale.Fit
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                }
                                Spacer(Modifier.weight(1f))
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
                                                    // Achievement wallpaper, kept reachable by touch
                                                    // as well as by the controller's button.
                                                    DropdownMenuItem(
                                                        text = { Text("Achievements grid", style = MaterialTheme.typography.bodySmall) },
                                                        onClick = {
                                                            viewModel.setShowWallpaperView(true)
                                                            kebabExpanded = false
                                                        },
                                                        leadingIcon = { Icon(Icons.Default.ViewModule, contentDescription = null, modifier = Modifier.size(20.dp)) }
                                                    )
                                                    // View on Steam Store
                                                    DropdownMenuItem(
                                                        text = { Text("View on Steam Store", style = MaterialTheme.typography.bodySmall) },
                                                        onClick = {
                                                            onOpenInBrowser("$STEAM_STORE_PREFIX${p.steamAppId}")
                                                            kebabExpanded = false
                                                        },
                                                        leadingIcon = { Icon(Icons.Default.TravelExplore, contentDescription = null, modifier = Modifier.size(20.dp)) }
                                                    )
                                                    // Achievement guide
                                                    DropdownMenuItem(
                                                        text = { Text("Achievement guide", style = MaterialTheme.typography.bodySmall) },
                                                        onClick = {
                                                            showNoGuideDialog = true
                                                            kebabExpanded = false
                                                        },
                                                        leadingIcon = { Icon(Icons.Default.MenuBook, contentDescription = null, modifier = Modifier.size(20.dp)) }
                                                    )
                                                    // Copy to checklist
                                                    DropdownMenuItem(
                                                        text = { Text("Copy to checklist", style = MaterialTheme.typography.bodySmall) },
                                                        onClick = {
                                                            bulkChecklistList = sortedAchievements
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
                                                }
                                            }
                                        }
                                    }
                                }
                            // Progress bar (accent gradient fill) — no end knob; status is shown above.
                            val progressPct = if (p.numAchievements > 0) p.numUnlocked.toFloat() / p.numAchievements else 0f
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp)
                                    .border(1.dp, accentColor.copy(alpha = 0.5f), CircleShape)
                            ) {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .background(sheetBg, CircleShape)
                                ) {
                                    Box(
                                        Modifier
                                            .fillMaxHeight()
                                            .fillMaxWidth(progressPct.coerceIn(0f, 1f))
                                            .background(accentBarBrush(accentColor), CircleShape)
                                    )
                                }
                            }
                            // Completion: count + percentage
                            val completionStr = if (p.numAchievements > 0) "${100 * p.numUnlocked / p.numAchievements}%" else "0%"
                            Row(
                                Modifier.padding(bottom = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("${p.numUnlocked}/${p.numAchievements} · $completionStr complete", style = MaterialTheme.typography.labelSmall, color = sheetText)
                            }
                            } // end item("header")

                            // --- Search item ---
                            // Above the chips and always there; the RA drawer's twin, matching
                            // through SteamAchievementSearch.
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
                            // Sort + filter chip row
                            val chipColors = FilterChipDefaults.filterChipColors(
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
                                        SteamAchievementSortField.entries.forEach { field ->
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
                                // Locked only filter chip
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
                                        label = { Text("Locked only", style = MaterialTheme.typography.labelSmall) },
                                        modifier = Modifier.height(28.dp),
                                        colors = chipColors,
                                        leadingIcon = {
                                            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp), tint = if (filterLockedOnly) sheetBg else sheetText)
                                        }
                                    )
                                }
                            }
                            } // end item("filters")

                            // A search that matches nothing must say so rather than leaving the
                            // header and chips standing over a blank page. Same as the RA drawer.
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
                                key = { it.apiName }
                            ) { a ->
                                SteamAchievementTile(
                                    achievement = a,
                                    gameTitle = p.gameTitle.ifBlank { gameTitle },
                                    accentColor = accentColor,
                                    sheetBg = sheetBg,
                                    textColor = sheetText,
                                    variantColor = sheetText.copy(alpha = 0.75f),
                                    onSearchUnlock = {
                                        val query = "\"${p.gameTitle.ifBlank { gameTitle }}\" \"${a.title}\" steam achievement how to unlock"
                                        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
                                        onOpenInBrowser("https://www.google.com/search?q=$encoded")
                                    },
                                    onAddToChecklist = {
                                        checklistSingleAchievement = a
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
                                    gameImage = p.headerImageUrl ?: gameLogoModel,
                                    // The store banner is 460x215; vault artwork's shape is unknown.
                                    gameImageAspect = if (p.headerImageUrl != null) 460f / 215f else 1f,
                                    earned = p.numUnlocked,
                                    total = p.numAchievements,
                                    dualBar = false,
                                    // Steam stamps an unlock in epoch SECONDS; zero means locked.
                                    lastPlayedMillis = remember(sortedAchievements) {
                                        sortedAchievements
                                            .mapNotNull { a -> a.unlockTime?.takeIf { it > 0 } }
                                            .maxOrNull()?.times(1000L)
                                    },
                                    // The far end of the same run, off the same list and for the
                                    // same reason. Hand-rolled rather than a call to
                                    // earliestUnlockMillis(): that helper parses RA's
                                    // "yyyy-MM-dd HH:mm:ss" strings, and Steam's unlock is already
                                    // an epoch-seconds Long.
                                    firstUnlockMillis = remember(sortedAchievements) {
                                        sortedAchievements
                                            .mapNotNull { a -> a.unlockTime?.takeIf { it > 0 } }
                                            .minOrNull()?.times(1000L)
                                    },
                                    badges = sortedAchievements.map {
                                        com.twinlore.app.ui.components.AchievementsBoardBadge(
                                            if (it.achieved) it.iconUrl else (it.iconGrayUrl ?: it.iconUrl),
                                            it.achieved,
                                            grayscaleWhenLocked = false,
                                        )
                                    },
                                ),
                                backgroundArt = gameLogoModel ?: p.headerImageUrl,
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
            title = { Text("Unlink Steam game?", color = sheetText) },
            text = {
                Text(
                    "This will unlink the Steam game. You can link a different game later from this screen.",
                    color = sheetText
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.unlinkGame(vaultId)
                        showUnlinkConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor, contentColor = sheetBg)
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

    var singleAchTabName by remember { mutableStateOf("") }
    LaunchedEffect(checklistSingleAchievement) {
        if (checklistSingleAchievement != null) {
            val tabs = viewModel.getChecklistTabs(vaultId)
            singleAchTabName = tabs.minByOrNull { it.sortOrder }?.name ?: "Tab 1"
        }
    }
    checklistSingleAchievement?.let { ach ->
        AlertDialog(
            onDismissRequest = { checklistSingleAchievement = null },
            containerColor = sheetBg,
            title = { Text("Add to checklist?", color = sheetText) },
            text = {
                Text(
                    "Add \"${ach.title}\" to tab \"$singleAchTabName\"?",
                    color = sheetText,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.addSingleAchievementToFirstTab(vaultId, ach)
                        checklistSingleAchievement = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor, contentColor = sheetBg),
                ) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { checklistSingleAchievement = null }) {
                    Text("Cancel", color = accentColor)
                }
            },
        )
    }

    if (showBulkChecklistConfirm) {
        AlertDialog(
            onDismissRequest = { showBulkChecklistConfirm = false },
            containerColor = sheetBg,
            title = { Text("Add All to Checklist", color = sheetText) },
            text = {
                Text(
                    "Add ${bulkChecklistList.size} visible achievement${if (bulkChecklistList.size == 1) "" else "s"} to this vault's checklist?",
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

    // No-guide dialog: shown when user taps the guide button (Steam has no guide concept)
    if (showNoGuideDialog) {
        AlertDialog(
            onDismissRequest = { showNoGuideDialog = false },
            containerColor = sheetBg,
            title = { Text("No guide available", color = sheetText) },
            text = {
                Text(
                    "Add a guide to this vault from the Guide tab, then come back here.",
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
}
