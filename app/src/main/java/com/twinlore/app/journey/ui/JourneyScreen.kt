package com.twinlore.app.journey.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import com.twinlore.app.data.AppFont
import com.twinlore.app.ui.theme.toFontFamily
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.twinlore.app.data.retroachievements.RaUrls
import com.twinlore.app.journey.domain.AwardGrading
import com.twinlore.app.journey.domain.ChartLayout
import com.twinlore.app.journey.domain.splitsForRivals
import com.twinlore.app.journey.domain.model.AchievementType
import com.twinlore.app.journey.domain.model.CompletionRow
import com.twinlore.app.journey.domain.model.JourneyMode
import com.twinlore.app.journey.ui.chart.ChartPaths
import com.twinlore.app.journey.ui.chart.ChartStyle
import com.twinlore.app.journey.ui.chart.FollowCamera
import com.twinlore.app.journey.ui.chart.JourneyBitmaps
import com.twinlore.app.journey.ui.chart.JourneyChartRenderer
import com.twinlore.app.ui.LocalCachedFocusColor
import com.twinlore.app.ui.LocalImageLoader
import com.twinlore.app.ui.components.AchievementsBoardBadge
import com.twinlore.app.ui.components.AchievementsBoardData
import com.twinlore.app.ui.components.AchievementsBoardView
import com.twinlore.app.ui.utils.FeedbackEvent
import com.twinlore.app.ui.utils.FeedbackManager
import com.twinlore.app.ui.utils.gamepadFocusable
import kotlin.math.roundToInt

/**
 * Achievement Journey screen. `JourneyScreen` is a thin wrapper that collects the ViewModel's
 * flows and delegates to [JourneyScreenContent], which is the stateless layer actually testable
 * without Hilt/network (see `JourneyScreenStateTest`).
 */
@Composable
fun JourneyScreen(
    lockedGameId: Int?,
    lockedGameTitle: String?,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenStats: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    viewModel: JourneyViewModel = hiltViewModel(),
    settingsViewModel: com.twinlore.app.ui.screens.SettingsViewModel = hiltViewModel(),
) {
    // Before the first read of any of the flows below, and not from a LaunchedEffect.
    //
    // The ViewModel is resolved against the activity (there is no nav back-stack entry here), so it
    // survives leaving the screen with the last visit's Ready state still in it. An effect runs
    // after the composition it belongs to, so the panel below would compose that stale Ready first
    // and open the full-screen player on the previous journey for a frame - decoding its badges and
    // arming its autoplay - before start() had said a word. start() does its own reset
    // synchronously, so calling it here means collectAsState's initial value is already this
    // visit's. Keyed on the entry, so a locked id that changes without the screen leaving (nothing
    // does this today) still begins a visit.
    remember(lockedGameId) { viewModel.start(lockedGameId) }

    val state by viewModel.uiState.collectAsState()
    val controls by viewModel.controls.collectAsState()
    val userName by viewModel.userName.collectAsState()
    val notice by viewModel.notice.collectAsState()
    val playedGames by viewModel.playedGames.collectAsState()
    val playedGamesError by viewModel.playedGamesError.collectAsState()
    val rivalRoster by viewModel.roster.collectAsState()
    val knownRivals by viewModel.knownRivals.collectAsState()
    val rivalColorIndex by viewModel.rivalColorIndex.collectAsState()
    val playerColorIndex by viewModel.playerColorIndex.collectAsState()
    val playerAmbience by viewModel.playerAmbience.collectAsState()
    val exportBackground by viewModel.exportBackground.collectAsState()
    val backdropDim by viewModel.backdropDim.collectAsState()
    val idleSigns by viewModel.idleSigns.collectAsState()

    // The library's own backdrop behind the setup screens (UI feedback): the journey is a doorway
    // off the library and should stand in the same room. The wallpaper draws whenever one is set,
    // not only under the library's WALLPAPER style - the BLURRED_ART style is per-focused-game
    // and this screen has no focused game, so the wallpaper is the room this screen can share.
    val wallpaperUri by settingsViewModel.appWallpaperImage.collectAsState(initial = null)
    val wallpaperDim by settingsViewModel.libraryWallpaperDim.collectAsState(initial = 0.5f)
    val wallpaperBlur by settingsViewModel.libraryWallpaperBlur.collectAsState(initial = 0f)
    val powerSaver by settingsViewModel.powerSaverMode.collectAsState(initial = false)
    // The Statistics door only exists where the statistics can: the play-stats screen reads
    // ES-DE's own records, so without a configured ES-DE folder it would open onto nothing.
    val esdeFolderUri by settingsViewModel.esdeFolderUri.collectAsState(initial = null)
    // The library folder: when one is set, "Save to device" writes journey exports into a
    // journey/ subfolder there instead of the MediaStore gallery.
    val homeFolderUri by settingsViewModel.homeFolderUri.collectAsState(initial = null)
    val wallpaperContext = LocalContext.current
    val wallpaperModel: Any? = remember(
        wallpaperUri, wallpaperBlur, powerSaver, wallpaperContext,
    ) {
        val uri = wallpaperUri
        when {
            powerSaver || uri == null -> null
            wallpaperBlur <= 0f -> uri
            else -> {
                val radius = (wallpaperBlur * com.twinlore.app.ui.screens.WALLPAPER_MAX_BLUR_RADIUS)
                    .toInt().coerceIn(1, com.twinlore.app.ui.screens.WALLPAPER_MAX_BLUR_RADIUS)
                coil.request.ImageRequest.Builder(wallpaperContext)
                    .data(uri)
                    .size(coil.size.Size(270, 480))
                    .transformations(
                        com.twinlore.app.ui.screens.WallpaperBlurTransformation(radius = radius),
                    )
                    .memoryCacheKey("library_wallpaper_b${radius}_${uri.hashCode()}")
                    .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                    .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                    .build()
            }
        }
    }

    BackHandler { onBack() }

    // Saves go to the gallery - Movies/GameTwin and Pictures/GameTwin - not the library's journey
    // folder : the gallery is where a
    // person looks for a video they just made, and the library folder buried it. The saveTreeUri
    // plumbing stays in the exporters for a future opt-in; nothing passes it any more.
    val export = rememberJourneyExportState(
        onExportSucceeded = viewModel::onExportSucceeded,
    )

    // Support prompt after an export lands. Held while an export is still in
    // flight, and while its "saved to..." notice bar is still visible, so it
    // can never cut between the export action and its reward or cover the
    // notice's own buttons. The share sheet case still works: the sheet is
    // its own activity, so the prompt greets the user on the way back, after
    // they've dismissed the notice.
    val supportPrompt by viewModel.supportPrompt.collectAsState()
    var supportPreviewIcon by remember { mutableStateOf<com.twinlore.app.data.AppIcon?>(null) }
    supportPrompt?.let { prompt ->
        if (export.progress == null && !export.imageRunning && export.message == null) {
            com.twinlore.app.ui.license.SupportDevDialog(
                onSupport = {
                    com.twinlore.app.ui.screens.openSupportDevPage(wallpaperContext)
                    viewModel.dismissSupportPrompt()
                },
                onDismiss = { viewModel.dismissSupportPrompt() },
                isFinal = prompt.isFinal,
                onIconTap = { supportPreviewIcon = it },
            )
        }
    }
    com.twinlore.app.ui.license.ThemePreviewHost(
        icon = supportPreviewIcon,
        settingsViewModel = settingsViewModel,
        onDismiss = { supportPreviewIcon = null },
    )

    JourneyScreenContent(
        state = state,
        controls = controls,
        userName = userName,
        notice = notice,
        lockedGameId = lockedGameId,
        lockedGameTitle = lockedGameTitle,
        playedGames = playedGames,
        playedGamesError = playedGamesError,
        rivalRoster = rivalRoster,
        knownRivals = knownRivals,
        onForgetRival = viewModel::forgetRival,
        onOpenPlayedGamesPicker = viewModel::loadPlayedGames,
        onSelectGame = viewModel::selectGame,
        onBack = onBack,
        onOpenSettings = onOpenSettings,
        onOpenStats = onOpenStats.takeIf { esdeFolderUri != null },
        onSetMode = viewModel::setMode,
        onSetSpanPreset = viewModel::setSpanPreset,
        onSetCustomRange = viewModel::setCustomRange,
        onAddRival = viewModel::addRival,
        onRemoveRival = viewModel::removeRival,
        rivalColorIndex = rivalColorIndex,
        onSetRivalColor = viewModel::setRivalColor,
        playerColorIndex = playerColorIndex,
        onSetPlayerColor = viewModel::setPlayerColor,
        playerAmbience = playerAmbience,
        onSetPlayerAmbience = viewModel::setPlayerAmbience,
        exportBackground = exportBackground,
        onSetExportBackground = viewModel::setExportBackground,
        backdropDim = backdropDim,
        onSetBackdropDim = viewModel::setBackdropDim,
        onPreviewBackdropDim = viewModel::previewBackdropDim,
        idleSigns = idleSigns,
        onSetIdleSigns = viewModel::setIdleSigns,
        onSetSpeed = viewModel::setSpeed,
        onSetFollowMode = viewModel::setFollowMode,
        onLoad = viewModel::load,
        onRetry = viewModel::retry,
        onDismissNotice = viewModel::dismissNotice,
        backdrop = {
            com.twinlore.app.ui.screens.LibraryWallpaperBackground(
                model = wallpaperModel,
                dim = wallpaperDim,
            )
        },
        modifier = modifier,
        export = export,
    )
}

/**
 * Stateless rendering of the journey's four screens: locked, configure, loading and the player.
 *
 * There is no summary page any more and no preview of the journey to press Watch on. Load runs
 * straight through the loading screen into the full-screen player, and backing out of the player
 * lands on the configure panel with every choice still made. Empty and error are the configure
 * panel carrying a line about what happened, because what the user does next is change something
 * there and press Load again.
 *
 * No ViewModel/Hilt/network dependency, so Robolectric compose tests can drive it directly with
 * fake states and controls. [showPlayer] is what a freshly loaded journey opens into; a test can
 * pass false to see the configure panel behind a Ready state instead of backing out of the player
 * to reach it.
 */
@Composable
internal fun JourneyScreenContent(
    state: JourneyUiState,
    controls: JourneyControls,
    userName: String,
    notice: String?,
    lockedGameId: Int?,
    lockedGameTitle: String?,
    playedGames: List<CompletionRow>,
    playedGamesError: Boolean,
    onOpenPlayedGamesPicker: () -> Unit,
    onSelectGame: (Int, String) -> Unit,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenStats: (() -> Unit)? = null,
    onSetMode: (JourneyMode) -> Unit,
    onSetSpanPreset: (SpanPreset) -> Unit,
    onSetCustomRange: (Int, Int) -> Unit,
    onAddRival: (String) -> Unit,
    onRemoveRival: (String) -> Unit,
    onSetSpeed: (Float) -> Unit,
    onSetFollowMode: (Boolean) -> Unit,
    onLoad: () -> Unit,
    onRetry: () -> Unit,
    onDismissNotice: () -> Unit,
    /**
     * The compare roster, as the user has typed it into the panel.
     *
     * It comes from the ViewModel rather than off the loaded chart. The roster is configure state
     * now: names go on it before anything is fetched, and `load()` is what turns them into curves.
     */
    rivalRoster: List<String> = emptyList(),
    /** Rivals this account has compared against before, offered as recents. See the panel. */
    knownRivals: List<String> = emptyList(),
    /** Drops one of those recents, cached data and all. */
    onForgetRival: (String) -> Unit = {},
    /** Drops every one of them. */
    /** Which of [JourneyPalette.RIVAL_CHOICES] the rival is drawn in; see the panel's picker. */
    rivalColorIndex: Int = 0,
    onSetRivalColor: (Int) -> Unit = {},
    /** The player's own colour: an index into the same choices, or -1 for the theme accent. */
    playerColorIndex: Int = 2,
    onSetPlayerColor: (Int) -> Unit = {},
    /** The library's own backdrop, drawn behind every non-player state. See [JourneyScreen]. */
    backdrop: @Composable () -> Unit = {},
    /** The ambience animation drawn between the player's backdrop art and the chart. */
    playerAmbience: com.twinlore.app.data.ambience.AmbienceAnimation = com.twinlore.app.data.ambience.AmbienceAnimation.NONE,
    onSetPlayerAmbience: (com.twinlore.app.data.ambience.AmbienceAnimation) -> Unit = {},
    /** Which RetroAchievements art the stage and its exports rest on. See [ExportBackground]. */
    exportBackground: ExportBackground = ExportBackground.TITLE,
    onSetExportBackground: (ExportBackground) -> Unit = {},
    /** How strong that art reads, as an alpha percent 5..100. See [JourneyBackdrop] and [JourneyExportRequest]. */
    backdropDim: Int = 15,
    onSetBackdropDim: (Int) -> Unit = {},
    onPreviewBackdropDim: (Int) -> Unit = {},
    /** Whether a flat line carries a sign over the player's face. See [IdleSigns]. */
    idleSigns: Boolean = true,
    onSetIdleSigns: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
    showPlayer: Boolean = true,
    /**
     * The export, owned here rather than inside the Ready branch.
     *
     * An encode is minutes of software rendering, and the state that runs it used to be created
     * inside `ReadyHost`: pressing Load or Retry - or the player's own retry on the missing-games
     * notice - moved the screen to Loading, took that host out of composition, cancelled the job
     * and left nothing on screen to say the export had died. Hoisted here it survives every state
     * this screen can be in, so an encode started in the player is still running (and still says
     * so, and can still be cancelled) on the loading screen and on the panel afterwards.
     *
     * Leaving the Journey screen entirely does still cancel it: this scope is the screen's. That is
     * the deliberate limit of the fix - the exporters delete their pending MediaStore row on the
     * way out (JourneyVideoExporter/JourneyImageExporter), so nothing is left half-written, and a
     * user who has navigated away from the journey is no longer looking at anything the notice
     * could be shown on.
     */
    export: JourneyExportState = rememberJourneyExportState(),
) {
    // The configure panel is a slot rather than a call site because the Ready state has to be able
    // to show it *or* the player, and the player is a full-screen sibling of the panel rather than
    // anything inside it. The trailing slot is what a state adds to it: the notice about an export
    // that outlived the player, or the loading screen, it was started from.
    val configure: @Composable (
        onWatchAgain: (() -> Unit)?,
        trailing: @Composable ColumnScope.() -> Unit,
    ) -> Unit = { onWatchAgain, trailing ->
        ConfigurePanel(
            state = state,
            controls = controls,
            notice = notice,
            lockedGameId = lockedGameId,
            lockedGameTitle = lockedGameTitle,
            playedGames = playedGames,
            playedGamesError = playedGamesError,
            rivalRoster = rivalRoster,
            knownRivals = knownRivals,
            onForgetRival = onForgetRival,
            onOpenPlayedGamesPicker = onOpenPlayedGamesPicker,
            onSelectGame = onSelectGame,
            onBack = onBack,
            onOpenStats = onOpenStats,
            onSetMode = onSetMode,
            onSetSpanPreset = onSetSpanPreset,
            onSetCustomRange = onSetCustomRange,
            onAddRival = onAddRival,
            onRemoveRival = onRemoveRival,
            onLoad = onLoad,
            onWatchAgain = onWatchAgain,
            onRetry = onRetry,
            onDismissNotice = onDismissNotice,
            trailing = trailing,
        )
    }

    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Whatever the library shows behind its own grid (UI feedback): the journey's setup is a
        // doorway off the library and should stand in the same room. The player draws over this
        // with its own opaque stage.
        backdrop()
        when (state) {
            is JourneyUiState.Locked -> LockedCard(
                reason = state.reason,
                onOpenSettings = onOpenSettings,
                onBack = onBack,
                modifier = Modifier.align(Alignment.Center).fillMaxWidth(0.86f).padding(24.dp),
            )
            is JourneyUiState.Loading -> LoadingPanel(state, onBack) { ExportNotice(export) }
            is JourneyUiState.Ready -> ReadyHost(
                state = state,
                controls = controls,
                userName = userName,
                rivalRoster = rivalRoster,
                rivalColorIndex = rivalColorIndex,
                onSetRivalColor = onSetRivalColor,
                playerColorIndex = playerColorIndex,
                onSetPlayerColor = onSetPlayerColor,
                playerAmbience = playerAmbience,
                onSetPlayerAmbience = onSetPlayerAmbience,
                exportBackground = exportBackground,
                onSetExportBackground = onSetExportBackground,
                backdropDim = backdropDim,
                onSetBackdropDim = onSetBackdropDim,
                onPreviewBackdropDim = onPreviewBackdropDim,
                idleSigns = idleSigns,
                onSetIdleSigns = onSetIdleSigns,
                initialShowPlayer = showPlayer,
                export = export,
                onRetry = onRetry,
                // A vault entry never had a configure panel to go back to: it picked the game and
                // loaded itself, so leaving the player leaves the journey.
                closeExits = lockedGameId != null,
                onBack = onBack,
                onSetSpeed = onSetSpeed,
                onSetFollowMode = onSetFollowMode,
                configure = configure,
            )
            // No journey loaded, so nothing to watch again; the export notice still belongs here,
            // because this is where a user who pressed Load on top of one is now standing.
            else -> configure(null) { ExportNotice(export) }
        }
    }
}

/**
 * The configure screen: what to chart, over what window, against whom, and Load. One centred panel
 * rather than a page, because it is the whole screen now instead of a strip above a summary.
 *
 * Scrollable and centred at once: a custom range with a rival roster is taller than a handheld in
 * landscape, and the panel that fits has no business sitting at the top of an empty screen.
 */
@Composable
private fun ConfigurePanel(
    state: JourneyUiState,
    controls: JourneyControls,
    notice: String?,
    lockedGameId: Int?,
    lockedGameTitle: String?,
    playedGames: List<CompletionRow>,
    playedGamesError: Boolean,
    rivalRoster: List<String>,
    knownRivals: List<String>,
    onForgetRival: (String) -> Unit,
    onOpenPlayedGamesPicker: () -> Unit,
    onSelectGame: (Int, String) -> Unit,
    onBack: () -> Unit,
    onOpenStats: (() -> Unit)? = null,
    onSetMode: (JourneyMode) -> Unit,
    onSetSpanPreset: (SpanPreset) -> Unit,
    onSetCustomRange: (Int, Int) -> Unit,
    onAddRival: (String) -> Unit,
    onRemoveRival: (String) -> Unit,
    onLoad: () -> Unit,
    onWatchAgain: (() -> Unit)?,
    onRetry: () -> Unit,
    onDismissNotice: () -> Unit,
    trailing: @Composable ColumnScope.() -> Unit,
) {
    val rivalErrors = (state as? JourneyUiState.Ready)?.rivalErrors ?: emptyList()
    val rivalErrorsRetryable = (state as? JourneyUiState.Ready)?.rivalErrorsRetryable ?: true
    val pending = pendingRivals(rivalRoster, state)

    CentredPanel {
        HeaderRow(onBack, onOpenStats)

        if (notice != null) NoticeRow(notice, onDismissNotice)

        JourneyControlsRow(
            lockedGameId = lockedGameId,
            lockedGameTitle = lockedGameTitle,
            controls = controls,
            rivalRoster = rivalRoster,
            rivalErrors = rivalErrors,
            rivalErrorsRetryable = rivalErrorsRetryable,
            pendingRivals = pending,
            playedGames = playedGames,
            playedGamesError = playedGamesError,
            knownRivals = knownRivals,
            onForgetRival = onForgetRival,
            onOpenPlayedGamesPicker = onOpenPlayedGamesPicker,
            onSelectGame = onSelectGame,
            onSetMode = onSetMode,
            onSetSpanPreset = onSetSpanPreset,
            onSetCustomRange = onSetCustomRange,
            onAddRival = onAddRival,
            onRemoveRival = onRemoveRival,
            onLoad = onLoad,
            onWatchAgain = onWatchAgain,
        )

        when (state) {
            is JourneyUiState.Empty -> Text(state.reason, style = MaterialTheme.typography.bodyMedium)
            is JourneyUiState.Error -> ErrorLines(state, onRetry)
            else -> Unit
        }

        trailing()
    }
}

/**
 * The shared shape of every screen that is not the player: one column, pinned to the top under
 * the header (UI feedback: the setup panel floated in the middle of the screen, and a menu that
 * moves with its own content length reads as unanchored), scrollable when it does not fit.
 */
@Composable
private fun CentredPanel(content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = PANEL_MAX_WIDTH)
                .fillMaxWidth()
                // targetSdk 35 enforces edge-to-edge on Android 15+, so the manifest's
                // `adjustResize` is effectively ignored: the window does not shrink when the
                // keyboard opens, it is simply covered by it. Without imePadding this scroll
                // viewport stays full height behind the IME, so the rival field - the last control
                // on the panel - sits underneath the keyboard that was opened to type into it, and
                // bringIntoView has nothing to scroll against. Same fix, and same reason, as
                // SettingsScreen's. The field's own requester (see RivalRow) does the scrolling
                // once there is a shortened viewport to scroll within.
                .imePadding()
                // And below the front camera at the other end (owner report: the portrait panel's
                // "Journey" heading and Statistics button crowded the punch-hole) - the same
                // enforced edge-to-edge that makes imePadding necessary above puts this column
                // behind the cutout without it. Before the scroll, so it is a fixed clearance the
                // content scrolls under rather than a band that travels with it.
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content,
        )
    }
}

/** Wide enough for the span dropdown and the rival field, narrow enough to read as a panel. */
private val PANEL_MAX_WIDTH = 560.dp

@Composable
private fun HeaderRow(onBack: () -> Unit, onOpenStats: (() -> Unit)? = null) {
    // The pixel font's glyphs sit askew in their own line box (no descenders, ascent past the
    // em), so the baseline-aligned heading rode visibly high beside the box-centred back arrow
    // (owner report: "Journey" skewed toward the top). The same cure the strip's award dots got:
    // in that font the ARROW declares its alignment line at centre + em/2, which lands its centre
    // exactly on the heading glyphs' own middle - the baseline pair with the Statistics button is
    // untouched. Ordinary fonts keep the centring they always had.
    val headline = MaterialTheme.typography.headlineSmall
    val headerContext = androidx.compose.ui.platform.LocalContext.current
    val pixelFont = remember(headline.fontFamily, headerContext) {
        headline.fontFamily == AppFont.PIXEL.toFontFamily(headerContext)
    }
    val headlineEmPx = with(androidx.compose.ui.platform.LocalDensity.current) { headline.fontSize.toPx() }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        FocusableIconButton(
            icon = { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") },
            onClick = onBack,
            modifier = if (pixelFont) {
                Modifier.alignBy { it.measuredHeight / 2 + (headlineEmPx / 2f).roundToInt() }
            } else {
                Modifier
            },
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "Journey",
            style = headline,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f).alignByBaseline(),
        )
        // The doorway to the play-statistics screen: the two are the app's twin readings of
        // "what have I been playing", and the stats had no way in from here. Baseline-aligned
        // with the heading, not box-centred against it: the two type sizes centre to different
        // baselines, and the button read as sitting low beside the word it shares a line with.
        if (onOpenStats != null) {
            OutlinedButton(
                onClick = onOpenStats,
                modifier = Modifier.gamepadFocusable().alignByBaseline(),
            ) {
                Text("Statistics")
            }
        }
    }
}

/**
 * The card the screen shows when it cannot run yet. Two variants, because there are two different
 * settings it can be waiting on and sending someone to add a key they already have is worse than
 * saying nothing. Both offer the same way out: the Settings screen, opened at the achievements
 * section.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun LockedCard(
    reason: LockedReason,
    onOpenSettings: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = chartPanelColor(scheme.surfaceVariant, scheme.background),
        modifier = modifier,
    ) {
        Column(
            Modifier
                .padding(24.dp)
                // The card must never outgrow the screen: on a short viewport the buttons
                // were pushed past the card's clip and Back rendered broken. The column
                // scrolls instead of clipping.
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Achievement Journey",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                when (reason) {
                    LockedReason.NO_API_KEY ->
                        "This screen replays your unlock history and can make many " +
                            "RetroAchievements API calls. To use it, add your own " +
                            "RetroAchievements API key in Settings."
                    LockedReason.NO_ACCOUNT ->
                        "Add your RetroAchievements username in Settings to use the journey."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            // FlowRow, not Row: on a narrow portrait card the two buttons do not fit side
            // by side, and a Row squeezed the second one into a broken sliver. Wrapping
            // puts Back on its own line instead.
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FocusableButton("Open settings", onOpenSettings)
                FocusableButton("Back", onBack, outlined = true)
            }
        }
    }
}

@Composable
private fun NoticeRow(text: String, onDismiss: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = chartPanelColor(scheme.surfaceVariant, scheme.background),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            FocusableIconButton(
                icon = { Icon(Icons.Filled.Close, contentDescription = "Dismiss") },
                onClick = onDismiss,
            )
        }
    }
}

/**
 * The heading every loading step sits under; the step's own label must not repeat it. Internal so
 * the tests that pin "printed exactly once" and "the opening label is not this" both name the same
 * string rather than two copies of a literal that could drift apart.
 *
 * No ellipsis. It used to have one while the step directly under it ("Preparing your journey") had
 * neither ellipsis nor period, so the two lines read as mismatched registers stacked on top of each
 * other. Progress labels here carry no trailing punctuation; complete sentences (the errors, the
 * notices) end in a period.
 */
internal const val LOADING_TITLE = "Loading your journey"

/**
 * Loading is a screen of its own now: the journey it is fetching is what comes next, not a page.
 *
 * It carries the [trailing] slot for the same reason the configure panel does: an export started in
 * the player outlives a Load, and this is the screen it has to keep saying so on.
 */
@Composable
private fun LoadingPanel(
    state: JourneyUiState.Loading,
    onBack: () -> Unit,
    trailing: @Composable ColumnScope.() -> Unit,
) {
    CentredPanel {
        // No stats doorway mid-load: leaving a fetch for another screen is Back's job.
        HeaderRow(onBack)
        Text(
            LOADING_TITLE,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        // The second line is the step, not the heading again: a label that says what the heading
        // already says is blanked rather than drawn under it. The slot itself is always two lines
        // tall: the per-game step names whatever game is being fetched, and a label that grew and
        // shrank with each title moved the progress bar under it on every game of the load.
        Text(
            if (state.label != LOADING_TITLE) state.label else "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        // Always the determinate bar, which draws statically. Material's indeterminate one is an
        // InfiniteTransition under the covers: it invalidates draw every vsync for as long as it is
        // composed, which on a slow or stalled fetch is minutes of 60 Hz wakeups on a screen with
        // nothing else moving on it. A load with no total yet reports an empty bar rather than a
        // spinning one - the step underneath the heading is what says work is happening.
        LinearProgressIndicator(
            progress = if (state.total > 0) state.done.toFloat() / state.total else 0f,
            modifier = Modifier.fillMaxWidth(),
        )
        trailing()
    }
}

@Composable
private fun ErrorLines(state: JourneyUiState.Error, onRetry: () -> Unit) {
    Text(
        state.message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
    )
    if (state.retryable) FocusableButton("Retry", onRetry)
}

/**
 * Everything the Ready state owns beyond the loaded frame: the palette resolved from the theme,
 * the decoded images, the export, the playback position and whether the player is up.
 *
 * A freshly loaded journey opens straight into the player; backing out of it composes the
 * configure panel instead, from the state that never went anywhere. Nothing here is keyed on the
 * state itself, so a rival added from the panel rebuilds the chart without throwing the user into
 * the player for asking, and a load that starts over takes this whole host out of composition,
 * which is what makes the next journey open playing again.
 */
@Composable
private fun BoxScope.ReadyHost(
    state: JourneyUiState.Ready,
    controls: JourneyControls,
    userName: String,
    /** The typed roster, for the one question this host asks of it: is anybody on it not loaded. */
    rivalRoster: List<String>,
    rivalColorIndex: Int,
    onSetRivalColor: (Int) -> Unit,
    playerColorIndex: Int,
    onSetPlayerColor: (Int) -> Unit,
    playerAmbience: com.twinlore.app.data.ambience.AmbienceAnimation,
    onSetPlayerAmbience: (com.twinlore.app.data.ambience.AmbienceAnimation) -> Unit,
    exportBackground: ExportBackground,
    onSetExportBackground: (ExportBackground) -> Unit,
    backdropDim: Int,
    onSetBackdropDim: (Int) -> Unit,
    onPreviewBackdropDim: (Int) -> Unit,
    idleSigns: Boolean,
    onSetIdleSigns: (Boolean) -> Unit,
    initialShowPlayer: Boolean,
    closeExits: Boolean,
    export: JourneyExportState,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onSetSpeed: (Float) -> Unit,
    onSetFollowMode: (Boolean) -> Unit,
    configure: @Composable (
        onWatchAgain: (() -> Unit)?,
        trailing: @Composable ColumnScope.() -> Unit,
    ) -> Unit,
) {
    // A single-game compare where nobody on the rival side has anything for the game is an
    // answer, not a replay: the player would run a race with one runner in it. The panel stays
    // up - the empty-rival notice is what does the telling - and the animation never starts.
    val emptyGameCompare = state.frame.mode == JourneyMode.GAME &&
        state.frame.rivals.isNotEmpty() &&
        state.frame.rivals.none { it.unlocks.isNotEmpty() }
    var showPlayer by remember { mutableStateOf(initialShowPlayer && !emptyGameCompare) }
    // Playback belongs to the player. It is a holder rather than two values so that the reads
    // happen inside the slider, the overview and the backdrop; this host never reads them, and so
    // never recomposes while the journey runs.
    val playback = remember(state) { JourneyPlaybackHolder() }
    // The picker's colour drives the whole compare rendering through the style: the curve, the
    // owner tint on badges, the hardcore ring, the compare bar's lines and the export all read
    // colorForRivalIndex(0) off this.
    // Off MaterialTheme, not LocalThemePalette: the latter throws when unprovided, which is
    // exactly how the journey's own UI tests compose this screen.
    val paletteIsDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val style = rememberChartStyle(
        rivalColor = JourneyPalette.choiceColor(rivalColorIndex, paletteIsDark),
        // -1 was the retired theme-accent option; any real pick resolves through the same
        // theme-aware choice the swatches show.
        accentOverride = playerColorIndex.takeIf { it >= 0 }
            ?.let { JourneyPalette.choiceColor(it, paletteIsDark) },
    )

    // Badge/avatar images are decoded here rather than inside the chart so the video export can
    // draw with the exact same map: the exporter fetches nothing, it only re-renders.
    val context = LocalContext.current
    val imageLoader = LocalImageLoader.current
    var bitmaps by remember(state.frame) { mutableStateOf<Map<String, ImageBitmap>?>(null) }
    // Backdrop art for the export, keyed by game id. Decoded after the badges so the chart is
    // never held up by it, and left empty when a game has no usable art: the exporter then draws
    // that stretch on the plain background rather than fetching anything of its own.
    var backdropArt by remember(state.frame) { mutableStateOf<Map<Int, ImageBitmap>>(emptyMap()) }
    LaunchedEffect(state.frame, userName) {
        // Always the wide layout: its grid is the finer one, so its icon set is the superset both
        // layouts draw from.
        bitmaps = JourneyBitmaps.preload(context, imageLoader, state.frame, state.layout, userName)
    }
    // Its own effect, keyed on the export-background choice as well: picking a different art
    // source re-decodes the map without throwing away the badge bitmaps above.
    LaunchedEffect(state.frame, exportBackground) {
        // Emptied first. The export request is rebuilt the instant the choice changes, so an
        // export started while the new source is still decoding would otherwise pair the new
        // choice's name with the previous choice's pictures. A plain background is a truthful
        // answer for that window; the old art is not.
        backdropArt = emptyMap()
        backdropArt = JourneyBitmaps.preloadArt(
            context, imageLoader, state.frame, state.layout, exportBackground,
        )
    }

    // One retry pass for images the first decode did not get, taken when the player is up. Nothing
    // negative-caches a failed image, so a badge emptied by a timeout or a transient 404 would
    // otherwise stay empty for as long as this journey is loaded. Guarded by a flag rather than by
    // the map itself: a url that is genuinely gone must cost one extra request, not one per time
    // the player is opened, and certainly not a loop.
    var retriedMissingArt by remember(state.frame) { mutableStateOf(false) }
    LaunchedEffect(showPlayer, state.frame, bitmaps != null) {
        if (!showPlayer || retriedMissingArt) return@LaunchedEffect
        val loaded = bitmaps ?: return@LaunchedEffect
        val missing = JourneyBitmaps.missingUrls(state.frame, state.layout, userName, loaded.keys)
        if (missing.isEmpty()) {
            retriedMissingArt = true
            return@LaunchedEffect
        }
        val topped = JourneyBitmaps.retryMissing(context, imageLoader, missing, loaded)
        // Latched only now: leaving the player cancels this effect mid-fetch, and burning the one
        // attempt on a pass that never finished would leave the icons empty for good.
        retriedMissingArt = true
        if (topped.size > loaded.size) bitmaps = topped
    }

    // The request is rebuilt whenever the chart inputs change, but the button itself stays the
    // same lambda instance so the player's bar keeps skipping recomposition.
    //
    // remember on identity keys, not rememberUpdatedState. That writes unconditionally, and the
    // state's default policy then compares old with new - a deep structural walk over a ChartLayout,
    // a JourneyFrame and the map of every decoded badge, run on every recomposition of this host
    // (a speed chip, a notice, a played-games emission) after allocating a fresh request to compare
    // against. The instances below only change when something is genuinely rebuilt.
    // RANDOM is rolled once, here, and the same concrete pick feeds the player AND the export:
    // a shared video has to show the weather the watcher was looking at.
    val resolvedAmbience = com.twinlore.app.ui.ambience.rememberResolvedAnimation(
        playerAmbience, state.frame,
    )
    // Whether the watcher was following the runner, resolved exactly as the player resolves it
    // (see JourneyPlayer): the user's switch when they have touched it, and the chart's own default
    // rule for this journey otherwise. Against state.layout rather than the compact chart the
    // export draws, because that is the layout the player asks the same question about - the
    // point is to answer "was the picture on screen following", not "would a fitted chart".
    val followMode = controls.followMode
        ?: FollowCamera.defaultOn(state.layout, state.frame.main.unlocks.size)
    val exportRequest = remember(
        state.compactLayout, state.frame, style, bitmaps, backdropArt, userName,
        controls.speed, state.rank, state.playerRanks, resolvedAmbience, exportBackground, idleSigns,
        followMode, backdropDim,
    ) {
        bitmaps?.let {
            JourneyExportRequest(
                // The video is watched, not explored, so it never draws the wide chart's
                // letterboxed ribbon: it renders the compact layout, whole when the watcher was
                // not following and through the same follow camera when they were.
                layout = state.compactLayout,
                frame = state.frame,
                style = style,
                bitmaps = it,
                art = backdropArt,
                userName = userName,
                speed = controls.speed,
                ranks = JourneyStripText.Ranks(state.rank, state.playerRanks),
                ambience = resolvedAmbience,
                idleSigns = idleSigns,
                followMode = followMode,
                backdropDim = backdropDim / 100f,
            )
        }
    }
    // The lambdas the player's bar gets must not change identity, or the bar stops skipping - so
    // the request they read is held behind a state that is updated in place instead of captured.
    val currentExportRequest by rememberUpdatedState(exportRequest)
    val shareVideo: () -> Unit = remember(export) { { currentExportRequest?.let { export.start(it, share = true) } } }
    val shareImage: () -> Unit = remember(export) { { currentExportRequest?.let { export.startImage(it, share = true) } } }
    val saveVideo: () -> Unit = remember(export) { { currentExportRequest?.let { export.start(it, share = false) } } }
    val saveImage: () -> Unit = remember(export) { { currentExportRequest?.let { export.startImage(it, share = false) } } }

    // The player is a mode, not a layer: while it is up the panel underneath is not composed at
    // all, so nothing behind it can take a stray tap or a D-pad hop into a control the user cannot
    // see.
    if (!showPlayer) {
        // The loaded journey is watchable more than once. The affordance is offered only while the
        // controls still describe what is loaded: change the span or pick another game and it goes,
        // because what it would replay is no longer what the panel says.
        // Also withheld while a roster name has never been fetched. The roster is configure state
        // now, so it can be changed without pressing Load - and re-entering the player would replay
        // a chart that is missing whoever was just added, under a panel showing their chip.
        val watchAgain: (() -> Unit)? =
            if (state.context.matches(controls) && pendingRivals(rivalRoster, state).isEmpty()) {
                ({ showPlayer = true })
            } else {
                null
            }
        configure(watchAgain) {
            // An export outlives the player it was started from, so its notice belongs to whatever
            // the user is looking at next.
            ExportNotice(export)
        }
    } else {
        // The grid is a full-screen dialog over the player. Playback pauses while it is up -
        // nothing on the chart is visible behind it, and a reveal running into a dialog is a minute
        // of the journey nobody watched - and resumes only if it was running when it opened.
        var gridOpen by remember { mutableStateOf(false) }
        var resumeAfterGrid by remember { mutableStateOf(false) }
        // Held behind remember, like shareVideo/saveImage above: a fresh lambda every recomposition
        // would change onOpenGrid's identity every time this host recomposes (a speed chip, a
        // notice), which stops the bar from skipping just as surely as a fresh export lambda would.
        // Keyed on playback rather than export, since what this closes over is the holder, not a
        // rebuilt request - and a new playback (a freshly loaded journey) is exactly when the old
        // closure would be reaching for a holder nothing reads any more.
        val openGridAction = remember(playback) {
            {
                resumeAfterGrid = playback.playing
                playback.playing = false
                gridOpen = true
            }
        }
        val openGrid: (() -> Unit)? = openGridAction.takeIf { state.gameAchievements.isNotEmpty() }
        if (gridOpen) {
            val game = state.frame.main.games.values.firstOrNull()
            val earned = state.gameAchievements.count { it.earned }
            val earnedPoints = state.frame.main.unlocks.sumOf { it.points }
            // What the whole list is worth, off the board's own achievements - the same list the
            // badges below are drawn from. Zero means nothing knows (a game cached before the
            // field existed), and then the line stays the single number it has always been rather
            // than printing "300 of 0 points".
            val totalPoints = state.gameAchievements.sumOf { it.points }
            AchievementsBoardView(
                data = AchievementsBoardData(
                    title = game?.title.orEmpty(),
                    gameImage = game?.iconPath?.let(JourneyUrls::media),
                    earned = earned,
                    total = state.gameAchievements.size,
                    hardcoreEarned = state.frame.main.unlocks.count { it.hardcore },
                    // The shape the RA drawer's own board uses for the same game
                    // ("305 of 570 points"), which is what the field's doc asks for: two ways into
                    // one board must not read as two different boards.
                    pointsLine = if (totalPoints > 0) {
                        "$earnedPoints of $totalPoints points"
                    } else {
                        "$earnedPoints points"
                    },
                    dualBar = true,
                    // The award off the sweep's own medals, not off GameRef.award. Only the SPAN
                    // builder ever fills that field in; JourneyBuilder.buildGamePlayer hardcodes it
                    // null and puts the game's milestones in the medals instead - and GAME mode is
                    // the only mode this board is reachable from, so reading the GameRef meant the
                    // journey's board could never name an award, while the RA drawer's board for
                    // the very same game named it.
                    highestAwardKind = AwardGrading
                        .highest(state.frame.main.medals.map { it.kind })
                        ?.let(AwardGrading::slug),
                    playtimeMinutes = game?.playtimeSeconds?.let { it / 60 },
                    firstUnlockMillis = state.frame.main.unlocks.minOfOrNull { it.earnedAtMillis },
                    lastPlayedMillis = state.frame.main.unlocks.maxOfOrNull { it.earnedAtMillis },
                    badges = state.gameAchievements.map {
                        AchievementsBoardBadge(
                            url = RaUrls.badge(it.badgeName),
                            unlocked = it.earned,
                            grayscaleWhenLocked = true,
                            hardcore = it.hardcore,
                        )
                    },
                ),
                backgroundArt = game?.artPaths?.firstOrNull()?.let(JourneyUrls::media),
                onDismiss = {
                    gridOpen = false
                    if (resumeAfterGrid) playback.playing = true
                },
            )
        }
        JourneyPlayer(
            state = state,
            controls = controls,
            userName = userName,
            style = style,
            ambience = resolvedAmbience,
            ambienceSelection = playerAmbience,
            onSetAmbience = onSetPlayerAmbience,
            exportBackground = exportBackground,
            onSetExportBackground = onSetExportBackground,
            backdropDim = backdropDim,
            onSetBackdropDim = onSetBackdropDim,
            idleSigns = idleSigns,
            onSetIdleSigns = onSetIdleSigns,
            rivalColorIndex = rivalColorIndex,
            onSetRivalColor = onSetRivalColor,
            playerColorIndex = playerColorIndex,
            onSetPlayerColor = onSetPlayerColor,
            bitmaps = bitmaps,
            playback = playback,
            export = export,
            onShareVideo = shareVideo,
            onShareImage = shareImage,
            onSaveVideo = saveVideo,
            onSaveImage = saveImage,
            onRetry = onRetry,
            onClose = {
                // Leaving the player stops the reveal; where it had got to does not matter, since
                // the next Load starts a fresh one from the beginning.
                playback.playing = false
                if (closeExits) onBack() else showPlayer = false
            },
            onSetSpeed = onSetSpeed,
            onSetFollowMode = onSetFollowMode,
            onOpenGrid = openGrid,
        )
    }
}

/**
 * Roster names the loaded chart knows nothing about: added since the last Load, or added when there
 * was no Load at all.
 *
 * They are neither drawn nor errored - nobody has tried to fetch them yet - so the frame leaves
 * them out entirely (see `buildRivalJourney`) and this is what the panel styles differently and
 * what withholds "Watch again". Case-insensitively, because a name is re-spelled the way
 * RetroAchievements spells it once a response says so.
 *
 * Internal so a test names the same rule the panel applies.
 */
internal fun pendingRivals(roster: List<String>, state: JourneyUiState): List<String> {
    val ready = state as? JourneyUiState.Ready ?: return roster
    val known = ready.frame.rivals.map { it.name } + ready.rivalErrors
    return roster.filter { name -> known.none { it.equals(name, ignoreCase = true) } }
}

/**
 * What the backdrop draws behind the chart at a given playback position: one player's side of it.
 *
 * Three tiers, best first, because a game that has no screenshot is not a game with no identity
 *. [artUrls] is the big art the stage is cropped to; [iconUrl] is the game's
 * low-resolution thumbnail, drawn small and centred rather than smeared across the stage; [tint]
 * is the game's own colour, which is what a game with neither rests on and what anything still
 * loading rests on. [rival] is the other player's side, non-null only on a compare SPAN
 * ([splitsForRivals]) - that is the one shape where splitting the stage between them is right,
 * because they are each on a different game at that moment.
 *
 * Stable so the composable can skip: a List alone is not, and this is handed over about seven
 * times a second while a journey runs.
 */
@androidx.compose.runtime.Stable
internal class BackdropArgs(
    val artUrls: List<String>,
    val iconUrl: String? = null,
    val tint: Color = Color.Unspecified,
    /**
     * Whether [tint] is painted under the art as well as in place of it.
     *
     * False on a solo chart, where the tint is the current GAME's colour and standing it behind
     * every picture made the whole stage change hue each time the sweep crossed into the next game
     *. True on a compare chart, where it is the PLAYER's own colour instead: that
     * does not change as the journey plays, so it reads as whose half of the stage this is rather
     * than as a flicker.
     */
    val tintAlways: Boolean = false,
    val rival: BackdropArgs? = null,
)

/**
 * Which game's art belongs behind the chart at [progress]: the main player's current game, and on
 * a compare SPAN, the rival's own current game on the other half of the stage - each player is on
 * a different game at that moment, so each half carries the game behind its own line (see
 * [splitsForRivals]). A compare GAME chart has one game between both players, and used to split
 * for it too: painted the identical picture twice with a seam across the middle.
 *
 * Read off whichever chart is on screen ([layout] and its already-measured [paths]), so the art
 * behind the stage is the art of the game the visible sweep is over, at the shell's coarse
 * progress granularity rather than as a per-frame draw-phase read.
 */
@Composable
internal fun backdropArgs(
    state: JourneyUiState.Ready,
    layout: ChartLayout,
    paths: ChartPaths,
    progress: Float,
    /**
     * Which art the stage rests on - the same choice the exports follow, so the shared file and
     * the live player can never disagree. NONE turns the layer off entirely.
     */
    background: ExportBackground = ExportBackground.TITLE,
    /** The chart's palette, for the colour a game with no usable art at all rests on. */
    style: ChartStyle? = null,
    /**
     * Where the stage reports which game it has settled on, for the span header to NAME the game
     * whose picture is actually up. The header briefly ran a dwell gate of its own with the same
     * rule, and the two still drifted (owner report: the title and the backdrop named different
     * games mid-throttle) - separate gates stamp their dwells at separate instants, and identical
     * rules on offset clocks disagree for seconds at a time. One gate, one answer: it lives here,
     * where the stage's art is chosen, and the header reads the result. Null for a caller with no
     * header to feed (the tests).
     */
    shownMainGame: androidx.compose.runtime.MutableState<Int?>? = null,
): BackdropArgs {
    val sweepX = JourneyChartRenderer.sweepX(layout, paths, progress)
    // The stage's minimum dwell, applied to the TARGET rather than left to the swap machinery
    // alone: with the gate ahead of it, JourneyBackdrop's own settle-and-dwell rarely engages
    // (targets now arrive at least a dwell apart), and the header can share the exact id the
    // stage is showing. Keyed to the journey so a new load starts with nothing held; a fresh
    // gate adopts its first target immediately, so composition-per-progress tests see no hold.
    val mainGate = remember(state.frame, layout) { BackdropLogic.DwellGate() }
    val rivalGate = remember(state.frame, layout) { BackdropLogic.DwellGate() }
    // The header's gate runs a QUARTER of the stage's dwell (owner request: at the full dwell the
    // header named too few of the games going by). Same target stream, same clock instant, so the
    // header leads the stage by design rather than drifting beside it - the stage shows what the
    // header named a beat ago.
    val headerGate = remember(state.frame, layout) { BackdropLogic.DwellGate(HEADER_DWELL_MILLIS) }
    // What each unlock was worth, index-parallel with the tracks (LayoutEngine.buildCurve appends
    // both per unlock): a gate that held through a burst adopts the game that EARNED the window -
    // most points across the stretch - rather than whichever happened to be last under the sweep
    // (owner request). The rival weights are matched by curve name, since a rival with an empty
    // fetch has a roster slot but no curve.
    val mainWeights = remember(state.frame) { state.frame.main.unlocks.map { it.points } }
    val rivalWeights = remember(state.frame, layout) {
        layout.rivals.map { curve ->
            state.frame.rivals.firstOrNull { it.name == curve.playerName }
                ?.unlocks?.map { it.points } ?: emptyList()
        }
    }
    val now = System.currentTimeMillis()
    val target = BackdropLogic.gameAtSweep(layout.main.bgTrack, sweepX)
    val pickMain = { a: Float, b: Float ->
        BackdropLogic.heaviestGameBetween(layout.main.bgTrack, mainWeights, a, b)
    }
    val mainGameId = mainGate.shownAt(now, target, sweepX, pickMain)
    // A compare chart carries both players' games (user request), so the rival's current game is
    // read off their own track with the same shared sweep.
    val rivalTrack = layout.rivals.firstOrNull()?.bgTrack
    val rivalGameId = rivalTrack?.let { track ->
        rivalGate.shownAt(now, BackdropLogic.gameAtSweep(track, sweepX), sweepX) { a, b ->
            BackdropLogic.heaviestGameBetween(track, rivalWeights.getOrElse(0) { emptyList() }, a, b)
        }
    }
    if (shownMainGame != null) {
        val headerGameId = headerGate.shownAt(now, target, sweepX, pickMain)
        androidx.compose.runtime.SideEffect { shownMainGame.value = headerGameId }
    }
    // Keyed on the games, not on the progress that found them. The candidates are a couple of list
    // allocations and a string concat each, and the result is handed to a composable that can only
    // skip if it gets the same object back - so rebuilding it seven times a second for a game that
    // has not changed re-ran the whole backdrop, both AsyncImages included, for nothing.
    // A finished SPAN rests on no picture at all (user request). The art belongs to the game under
    // the sweep, and a journey over forty games is not "about" whichever one happened to be last -
    // so the closing frame, which is also the frame a shared image is taken from, keeps the stage
    // rather than the leftover. A single game keeps its art: the whole journey IS that game.
    val ended = progress >= 0.999f && state.frame.mode == JourneyMode.SPAN
    return remember(state.frame, mainGameId, rivalGameId, background, style, ended) {
        if (background == ExportBackground.NONE) return@remember BackdropArgs(emptyList())
        // A SPLIT compare chart tints each half in its player's own colour, always; a solo chart
        // tints only where a game has no art, and in that game's colour. The tint follows the same
        // one rule the split does ([splitsForRivals]): the player wash says whose half of a split
        // stage this is, and a single-game compare has no halves - both players are on the same
        // game, so it dims like a solo chart. This used to be `rivalTrack != null` on its own,
        // which is the exact same drift the split's own check already suffered once (see below):
        // a second copy of "is there a rival" with no mode in it.
        val compare = splitsForRivals(state.frame.mode, hasRivalTrack = rivalTrack != null)
        fun sideFor(
            game: com.twinlore.app.journey.domain.model.GameRef?,
            playerColor: Color = Color.Unspecified,
        ): BackdropArgs {
            // A finished span rests on NOTHING - no picture, and on a solo chart no colour either.
            //
            // Dropping the art alone was not enough, and this is the bug it left: `BackdropSide`
            // paints the tint whenever `artUrls` is empty, so taking the picture away is what
            // switched the colour on. The stage went from the last game's art straight to a flat
            // accent wash and stayed there, while the export - which has no tint layer at all -
            // ended on bare background. Whichever game the sweep stopped on does not own a journey
            // over forty games, and neither does its colour.
            //
            // On a COMPARE chart the colour used to survive the landing to say whose half of
            // the stage was whose - superseded: the stopped
            // chart's curves and compare rows already say it, the export has always ended on
            // the bare background, and the live stage now matches it - solid theme colour,
            // no wash, in every mode.
            if (ended) {
                return BackdropArgs(emptyList())
            }
            // Mid-playback, a sweep between games or on a game with nothing to show still rests on
            // a colour: that is the tier below the art, not the end of the journey.
            if (game == null) {
                return BackdropArgs(emptyList(), tint = playerColor, tintAlways = compare)
            }
            // The icon is pulled OUT of the crop chain and kept as its own tier: it is a 64px
            // thumbnail, and cropping it to a whole stage is a smear rather than a backdrop.
            val icon = game.iconPath
            val big = BackdropLogic.bigArtCandidatesFor(game, background).map(JourneyUrls::media)
            return BackdropArgs(
                artUrls = big,
                iconUrl = icon?.let(JourneyUrls::media),
                // Player colour in both modes: the per-game
                // series tint recoloured the stage on every sweep crossing, which is the
                // same adaptive-colour noise removed from the header's game name.
                tint = playerColor,
                tintAlways = compare,
            )
        }
        val main = sideFor(
            mainGameId?.let { state.frame.main.games[it] },
            playerColor = style?.accent ?: Color.Unspecified,
        )
        // Split only where the export does too - see [splitsForRivals]. This used to be `rivalTrack
        // != null` on its own, with no mode in it, which is exactly why the export got fixed and
        // the screen did not: the two checks had drifted apart onto separate copies of the rule.
        // `compare` above is that same rule now, shared by the split and the tint alike.
        // A landed span has no halves to mark any more, so the split goes with the wash.
        val rival = if (!compare || ended) {
            null
        } else {
            sideFor(
                rivalGameId?.let { state.frame.rivals.firstOrNull()?.games?.get(it) },
                playerColor = style?.colorForRivalIndex(0) ?: Color.Unspecified,
            )
        }
        BackdropArgs(main.artUrls, main.iconUrl, main.tint, main.tintAlways, rival)
    }
}

/**
 * The chart's palette, resolved once from the theme: the structural half (background, panel, grid,
 * ink, accent) off the colour scheme, the fixed half out of [JourneyPalette].
 *
 * The split is deliberate. Series and rival colours have to stay recognisable per game and per
 * player in every theme, so they are not derived from the scheme, and keeping them in a plain
 * Kotlin object is what lets a test assert that no two of them collide.
 */
@Composable
internal fun rememberChartStyle(rivalColor: Color? = null, accentOverride: Color? = null): ChartStyle {
    val scheme = MaterialTheme.colorScheme
    val accent = accentOverride ?: accentColor()
    val font = MaterialTheme.typography.bodySmall.fontFamily ?: FontFamily.Default
    // Whether the theme's font is the pixel one, asked by comparing families rather than by reading
    // the setting: the chart is handed a resolved theme, not a preference, and `toFontFamily`
    // caches its result so this is an identity check against the very object the typography was
    // built from. The chart's own text sizes bypass `Typography`, so the 0.7 scale every pixel-font
    // style in the app already gets never reached them - see [ChartStyle.pixelFont].
    val context = LocalContext.current
    val pixelFont = remember(font, context) { font == AppFont.PIXEL.toFontFamily(context) }
    return remember(scheme, accent, font, rivalColor, pixelFont) {
        ChartStyle(
            background = scheme.background,
            panel = chartPanelColor(scheme.surfaceVariant, scheme.background),
            grid = scheme.outlineVariant,
            ink = scheme.onSurface,
            inkDim = scheme.onSurfaceVariant,
            accent = accent,
            seriesColors = JourneyPalette.SERIES,
            // Rival compare has one rival, and the picker chose their colour; the default list
            // keeps tests and callers with no picker (the summary preview) drawing what they did.
            rivalColors = rivalColor?.let { listOf(it) } ?: JourneyPalette.RIVALS,
            // STANDARD has no glyph, so its chip is never drawn; the entry is the theme's dim ink
            // so the map is total rather than a lookup that can miss.
            typeColors = JourneyPalette.TYPES +
                (AchievementType.STANDARD to scheme.onSurfaceVariant),
            silver = JourneyPalette.SILVER,
            gold = JourneyPalette.GOLD,
            labelFont = font,
            pixelFont = pixelFont,
        )
    }
}

/**
 * The chart's opaque panel colour: badge discs, medallion discs and the tooltip all sit on it.
 *
 * This app's theme defines `surfaceVariant` as the *foreground* colour at 12% alpha, which is fine
 * for a Material surface composited over the background by the framework and is a trap for a
 * canvas: painting it directly (or, worse, re-alpha-ing it to 0.96 the way the tooltip used to)
 * produced a near-opaque foreground-coloured panel, and every line of tooltip text drawn in `ink`
 * or `inkDim` disappeared into it. Compositing here makes the panel a real, solid, dark surface,
 * so ink on panel is ink on panel at any alpha the palette chooses.
 */
internal fun chartPanelColor(surfaceVariant: Color, background: Color): Color =
    surfaceVariant.compositeOver(background)

/**
 * Resolves a game's series colour the same way [JourneyChartRenderer] does (safe modulo, so an
 * out-of-range or negative index still lands in the palette rather than throwing), so anything
 * outside the canvas stays on the exact colours the chart itself draws with.
 */
internal fun ChartStyle.colorForIndex(index: Int): Color {
    if (seriesColors.isEmpty()) return ink
    val i = ((index % seriesColors.size) + seriesColors.size) % seriesColors.size
    return seriesColors[i]
}

/** Same idea as [colorForIndex], but into the rival palette [JourneyChartRenderer] draws curves with. */
internal fun ChartStyle.colorForRivalIndex(index: Int): Color {
    if (rivalColors.isEmpty()) return accent
    val i = ((index % rivalColors.size) + rivalColors.size) % rivalColors.size
    return rivalColors[i]
}

// ---- shared focus idioms, mirrored from GameStatsScreen.kt so every interactive control here
// takes D-pad focus the same way the rest of the app does. `internal` so JourneyControlsRow.kt
// and JourneyPlayer.kt (same package, different files) can reuse them. ----

/** Accent used for focus rings. */
@Composable
internal fun accentColor(): Color =
    LocalCachedFocusColor.current?.value ?: MaterialTheme.colorScheme.primary

/** Shared activate-on-A/Enter key handling for focusable controls. */
internal fun Modifier.activateOnA(onClick: () -> Unit): Modifier = onKeyEvent { e ->
    if (e.type == KeyEventType.KeyDown &&
        (e.key == Key.ButtonA || e.key == Key.DirectionCenter || e.key == Key.Enter)
    ) {
        onClick(); true
    } else false
}

@Composable
internal fun FocusableIconButton(
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val accent = accentColor()
    val view = LocalView.current
    JourneyFocusFeedback(focused)
    // Disabled means disabled on every route in: the click, and the A/Enter key that stands in for
    // it while the button holds D-pad focus.
    val activate: () -> Unit = {
        if (enabled) {
            FeedbackManager.play(view, FeedbackEvent.CONFIRM)
            onClick()
        }
    }
    IconButton(
        onClick = activate,
        enabled = enabled,
        interactionSource = interaction,
        modifier = modifier
            // Not focusable while disabled: the override that makes these reachable by D-pad would
            // otherwise park focus on a button that does nothing when pressed.
            .gamepadFocusable(canFocus = enabled)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .activateOnA(activate)
            .then(
                if (focused) {
                    Modifier
                        .background(accent.copy(alpha = 0.22f), CircleShape)
                        .border(2.dp, accent, CircleShape)
                } else Modifier
            ),
    ) { icon() }
}

@Composable
internal fun FocusableButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    outlined: Boolean = false,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val accent = accentColor()
    val view = LocalView.current
    JourneyFocusFeedback(focused)
    val press: () -> Unit = {
        FeedbackManager.play(view, FeedbackEvent.CONFIRM)
        onClick()
    }
    val shape = RoundedCornerShape(10.dp)
    val focusModifier = modifier
        .gamepadFocusable()
        .activateOnA(press)
        .then(if (focused) Modifier.border(2.dp, accent, shape) else Modifier)
    if (outlined) {
        OutlinedButton(
            onClick = press,
            interactionSource = interaction,
            enabled = enabled,
            shape = shape,
            modifier = focusModifier,
        ) { Text(text) }
    } else {
        Button(
            onClick = press,
            interactionSource = interaction,
            enabled = enabled,
            shape = shape,
            modifier = focusModifier,
        ) { Text(text) }
    }
}

/**
 * The focus half of the app-wide feedback contract, for the journey's own focus idioms: the same
 * sound and haptic every Navigable* control plays when the D-pad lands on it, through
 * [FeedbackManager]'s process-wide cached settings - so these composables stay renderable without
 * Hilt, which is what lets the screen-state tests drive them directly.
 */
@Composable
internal fun JourneyFocusFeedback(focused: Boolean) {
    val view = LocalView.current
    LaunchedEffect(focused) {
        if (focused) FeedbackManager.play(view, FeedbackEvent.FOCUS)
    }
}

/**
 * A segmented-style toggle: the selected option carries the theme accent (tinted container, accent
 * label and border), the rest read as quiet outlines. Same focus idiom as [FocusableButton], so a
 * D-pad still walks the segments and A activates them.
 */
@Composable
internal fun AccentToggle(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val accent = accentColor()
    val view = LocalView.current
    JourneyFocusFeedback(focused)
    val press: () -> Unit = {
        FeedbackManager.play(view, FeedbackEvent.CONFIRM)
        onClick()
    }
    val shape = RoundedCornerShape(10.dp)
    val outline = MaterialTheme.colorScheme.outline
    OutlinedButton(
        onClick = press,
        interactionSource = interaction,
        shape = shape,
        border = BorderStroke(
            width = if (focused || selected) 2.dp else 1.dp,
            color = if (focused) accent else if (selected) accent.copy(alpha = 0.8f) else outline,
        ),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (selected) accent.copy(alpha = 0.22f) else Color.Transparent,
            contentColor = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        contentPadding = if (compact) {
            PaddingValues(horizontal = 10.dp, vertical = 4.dp)
        } else {
            ButtonDefaults.ContentPadding
        },
        modifier = modifier
            .gamepadFocusable()
            .activateOnA(press),
    ) {
        Text(text, style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge)
    }
}

/** A focusable row for controls without a dedicated Material component (dropdown trigger, switch row). */
@Composable
internal fun FocusableRow(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(10.dp),
    containerColor: Color = Color.Transparent,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val accent = accentColor()
    val view = LocalView.current
    JourneyFocusFeedback(focused)
    val press: () -> Unit = {
        FeedbackManager.play(view, FeedbackEvent.CONFIRM)
        onClick()
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(shape)
            .background(containerColor, shape)
            .then(if (focused) Modifier.border(2.dp, accent, shape) else Modifier)
            .gamepadFocusable()
            .activateOnA(press)
            .clickable(interactionSource = interaction, indication = null, onClick = press)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        content = content,
    )
}
