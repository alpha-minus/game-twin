package com.twinlore.app.journey.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.twinlore.app.journey.domain.AwardGrading
import com.twinlore.app.journey.domain.ChartLayout
import com.twinlore.app.journey.domain.frameUpTo
import com.twinlore.app.journey.domain.frameUpToElapsed
import com.twinlore.app.journey.domain.model.AwardKind
import com.twinlore.app.journey.domain.model.JourneyFrame
import com.twinlore.app.journey.domain.model.JourneyMode
import com.twinlore.app.data.ambience.AmbienceAnimation
import com.twinlore.app.journey.domain.pluralize
import com.twinlore.app.ui.ambience.AmbienceOverlay
import com.twinlore.app.journey.ui.chart.ChartCursorHandle
import com.twinlore.app.journey.ui.chart.ChartPaths
import com.twinlore.app.journey.ui.chart.ChartStyle
import com.twinlore.app.journey.ui.chart.FLAME_OUTER
import com.twinlore.app.journey.ui.chart.ChartTapOutcome
import com.twinlore.app.journey.ui.chart.FollowCamera
import com.twinlore.app.journey.ui.chart.JourneyChart
import com.twinlore.app.journey.ui.chart.JourneyChartRenderer
import com.twinlore.app.ui.components.achievementBarBrush
import com.twinlore.app.ui.utils.HideStatusBarWhen
import com.twinlore.app.ui.utils.gamepadFocusable
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/** How long the control bar stays up after the last interaction while the journey is playing. */
private const val BAR_TIMEOUT_MILLIS = 3_000L

/**
 * How long the end state is left alone before the control bar comes back over it.
 *
 * The journey lands on its closing chart and its closing counters, and the bar used to arrive in the
 * same frame and cover the bottom of it. Three seconds is the moment the whole replay is for. Any
 * deliberate interaction - a tap, a key - takes the bar back immediately: this is a pause, not a
 * lockout.
 */
internal const val END_BAR_HOLD_MILLIS = 3_000L

/**
 * When the player was last touched. Deliberately not snapshot state: it is written on every
 * pointer event of a drag, and the only thing that needs to know is a timer loop that can read it
 * when it wakes up.
 */
private class LastTouch {
    private var atMillis = System.currentTimeMillis()

    fun touch() {
        atMillis = System.currentTimeMillis()
    }

    fun millisSince(): Long = (System.currentTimeMillis() - atMillis).coerceAtLeast(0L)
}

private const val PROGRESS_STEPS = 100

/**
 * Whether the control bar is up, and whether the settings sheet above it is open. One holder rather
 * than two flags, because the two are a single piece of furniture: the sheet is drawn on top of the
 * bar and inside the same fade, so a sheet whose bar has gone is a sheet nobody can see and nothing
 * can close - it simply reappears, still open, the next time the bar is asked for.
 *
 * The rules, in one place so a plain unit test can pin them:
 * - hiding the bar, however it happens, closes the sheet with it;
 * - while the sheet is open the bar does not time out ([autoHideSuppressed]), so the pair only
 *   leaves on an explicit tap;
 * - bringing the bar back does not reopen the sheet: closing it was a decision.
 */
@Stable
internal class PlayerBarState {
    var barVisible by mutableStateOf(true)
        private set
    var settingsOpen by mutableStateOf(false)
        private set

    /** The chart's key, its own sheet now: read on demand rather than under the settings. */
    var legendOpen by mutableStateOf(false)
        private set

    fun reveal() {
        // A sheet opened while the bar was already on its way out belongs to a bar the user has
        // just dismissed. PlayerControls stays composed and clickable through the exit fade, so a
        // tap that lands on the settings button during it sets this with the bar already gone - and
        // the auto-hide loop, which returns early on a hidden bar, never takes it back. The bar
        // then came back with the sheet up, which is the thing hide() exists to prevent.
        //
        // Only on the way back from hidden: reveal() is also every interaction's way of restarting
        // the countdown, and closing an open sheet because the user touched the slider would be a
        // different bug.
        if (!barVisible) closeSheets()
        barVisible = true
    }

    fun hide() {
        barVisible = false
        closeSheets()
    }

    fun toggleBar() {
        if (barVisible) hide() else reveal()
    }

    /** One sheet at a time: opening either takes the other's place rather than stacking on it. */
    fun toggleSettings() {
        settingsOpen = !settingsOpen
        if (settingsOpen) legendOpen = false
    }

    fun toggleLegend() {
        legendOpen = !legendOpen
        if (legendOpen) settingsOpen = false
    }

    /** Closes the sheet and leaves the bar where it is: what Back does with the sheet open. */
    fun closeSettings() {
        settingsOpen = false
    }

    fun closeLegend() {
        legendOpen = false
    }

    /** Both sheets at once, for the paths that dismiss whatever furniture is up. */
    fun closeSheets() {
        settingsOpen = false
        legendOpen = false
    }

    /** True while the auto-hide countdown must not run: a sheet is a conversation, not a glance. */
    val autoHideSuppressed: Boolean get() = settingsOpen || legendOpen
}

/**
 * Playback position and whether it is running, owned by the Ready shell and read only by the
 * player's own parts.
 *
 * A holder rather than two hoisted values on purpose: the summary above the player has nothing to
 * say about playback, and every composable that reads these recomposes about seven times a second
 * while the journey runs. Passing the holder lets those reads happen in the slider, the overview
 * and the backdrop rather than in the screen that contains them.
 */
@Stable
internal class JourneyPlaybackHolder {
    /**
     * Starts at zero, not at the finished chart: a settings toggle rebuilds the Ready state under
     * an open player, and a holder that began at 1 flashed the completed journey for a frame
     * before autoplay took it back to the start.
     */
    var progress by mutableFloatStateOf(0f)
    var playing by mutableStateOf(false)
}

/** What one press of Back (or B, or Escape) closes in the player. */
internal enum class PlayerBackTarget { SETTINGS, LEGEND, TOOLTIP, PLAYER }

/**
 * Which layer Back takes away: the innermost one that is open.
 *
 * Back used to go straight to closing the player from wherever it was pressed, so dismissing the
 * settings sheet threw the whole player away with it - and on a vault journey that is two levels in
 * one press, because closing the player there leaves the screen. Every other dismissible layer in
 * this app eats Back first; this is the one with the most expensive exit, so it does too.
 */
internal fun playerBackTarget(
    settingsOpen: Boolean,
    tooltipOpen: Boolean,
    legendOpen: Boolean = false,
): PlayerBackTarget = when {
    settingsOpen -> PlayerBackTarget.SETTINGS
    legendOpen -> PlayerBackTarget.LEGEND
    tooltipOpen -> PlayerBackTarget.TOOLTIP
    else -> PlayerBackTarget.PLAYER
}

/** Where a journey stops being "nearly done" and becomes the end state. */
private const val COMPLETE_AT = 0.999f

/**
 * The measured paths of both charts the player can draw: the wide timeline and the compact
 * one-screenful version of the same journey.
 *
 * Built once per loaded journey rather than per swap, because measuring a path is the expensive
 * part and the user can flip between the two views as often as they like. A journey that earned no
 * extra width has one layout object serving as both, and is measured once.
 */
private class PlayerPaths(state: JourneyUiState.Ready) {
    val wide = ChartPaths(state.layout)
    val compact = if (state.compactLayout === state.layout) wide else ChartPaths(state.compactLayout)
}

/**
 * Full-screen playback: the chart, the backdrop behind it, a permanent overview strip along the
 * top and a control bar that fades away while the journey runs.
 *
 * It is a mode inside the Ready state rather than a screen of its own, so nothing about navigation,
 * the ViewModel or the loaded frame changes when it opens. The configure panel is not composed
 * while it is up, so nothing behind it can take a stray tap or a D-pad hop.
 *
 * Dense everywhere, wide on request. Every state - before the first play, running, paused, finished
 * - is drawn on the compact one-screenful chart, which is the whole journey at a glance and the only
 * view in which nothing is off the edge of the screen. The wide timeline is what the button on the
 * bar offers, and pinning it lasts for the life of the loaded journey rather than until the next
 * play edge.
 *
 * It used to swap automatically: dense at rest, wide in motion. That put the reveal on a chart three
 * or four screenfuls wide with a camera panning across it, so most of the journey was off screen for
 * most of the playback, and the view changed under the viewer twice per press of Play. The follow
 * camera still exists and still only engages on the wide chart, which is now a deliberate choice
 * rather than the default.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun BoxScope.JourneyPlayer(
    state: JourneyUiState.Ready,
    controls: JourneyControls,
    userName: String,
    style: ChartStyle,
    bitmaps: Map<String, ImageBitmap>?,
    /** The ambience actually drawn (RANDOM already resolved to a concrete pick upstream). */
    ambience: AmbienceAnimation = AmbienceAnimation.NONE,
    /** What the user picked, for the settings row: shows "Random" when Random is the choice. */
    ambienceSelection: AmbienceAnimation = ambience,
    onSetAmbience: (AmbienceAnimation) -> Unit = {},
    exportBackground: ExportBackground = ExportBackground.TITLE,
    onSetExportBackground: (ExportBackground) -> Unit = {},
    /** How strong the backdrop art reads behind the chart, as an alpha percent 5..100. See [JourneyBackdrop]. */
    backdropDim: Int = 15,
    onSetBackdropDim: (Int) -> Unit = {},
    onPreviewBackdropDim: (Int) -> Unit = {},
    idleSigns: Boolean = true,
    onSetIdleSigns: (Boolean) -> Unit = {},
    rivalColorIndex: Int = 0,
    onSetRivalColor: (Int) -> Unit = {},
    /** The player's own colour: an index into the shared choices, or -1 for the theme accent. */
    playerColorIndex: Int = -1,
    onSetPlayerColor: (Int) -> Unit = {},
    playback: JourneyPlaybackHolder,
    export: JourneyExportState,
    onShareVideo: () -> Unit,
    onShareImage: () -> Unit,
    onSaveVideo: () -> Unit,
    onSaveImage: () -> Unit,
    onClose: () -> Unit,
    onRetry: () -> Unit,
    onSetSpeed: (Float) -> Unit,
    onSetFollowMode: (Boolean) -> Unit,
    onOpenGrid: (() -> Unit)? = null,
) {
    HideStatusBarWhen(true)

    // Both charts' paths, measured once per loaded journey: swapping between them mid-playback
    // must not cost a PathMeasure pass, and the compact chart is often the very same object as the
    // wide one (a journey that earned no extra width), in which case it is not measured twice.
    val paths = remember(state) { PlayerPaths(state) }

    val bar = remember { PlayerBarState() }
    // The chart's tooltip, reachable from here so that Back can dismiss it.
    val cursorHandle = remember { ChartCursorHandle() }

    // One press, one layer: see [playerBackTarget]. Shared by the system back gesture and by the
    // B/Back/Escape keys the preview handler below catches, so a handheld's button and a phone's
    // gesture cannot mean two different things.
    val handleBack: () -> Unit = {
        when (playerBackTarget(bar.settingsOpen, cursorHandle.isOpen, bar.legendOpen)) {
            PlayerBackTarget.SETTINGS -> bar.closeSettings()
            PlayerBackTarget.LEGEND -> bar.closeLegend()
            PlayerBackTarget.TOOLTIP -> cursorHandle.clear()
            PlayerBackTarget.PLAYER -> onClose()
        }
    }
    BackHandler(onBack = handleBack)
    // A control on the bar holds focus. Hiding the bar under it would take the focused node out of
    // composition, and a D-pad press with nowhere to go is a player that has stopped responding.
    var barFocused by remember { mutableStateOf(false) }
    // Nothing here is snapshot state. A finger dragging the slider produces a pointer event per
    // frame, and bumping observable state on each of them would recompose the whole player sixty
    // times a second; every interaction only touches [idle], which nothing observes.
    //
    // There used to be an interaction counter alongside it, read in this scope purely to re-key the
    // auto-hide effect - so every key press and every bar-reveal tap re-executed the player. It buys
    // nothing: the loop below re-reads [LastTouch] each time round and sleeps the exact remainder,
    // so a touch during the wait already extends the countdown without restarting the effect.
    val idle = remember { LastTouch() }
    // The end-of-journey pause before the bar arrives (see [END_BAR_HOLD_MILLIS]). Snapshot state,
    // because what it gates is a composable's visibility; written from exactly two places, the
    // timer that starts it and the interactions that end it early.
    var endBarHeld by remember(state) { mutableStateOf(false) }
    val reveal: () -> Unit = {
        idle.touch()
        bar.reveal()
    }
    // What a deliberate interaction does, as opposed to playback simply stopping: it ends the pause.
    // Kept apart from [reveal] because playback stopping calls that one, and the journey reaching
    // its end IS playback stopping - so a shared lambda would cancel the hold at the instant it
    // started it.
    val revealNow: () -> Unit = {
        endBarHeld = false
        reveal()
    }

    val playing = playback.playing
    // Handed to the chart instead of the value behind them, and remembered so the instances are
    // stable for the life of the holder. Progress is reported about seven times a second and read
    // by the draw pass and the slider; a read taken in *this* scope would re-execute the whole
    // player - backdrop, overview strip, chips, the chart's entire modifier chain - on every one of
    // them, which is what [JourneyPlaybackHolder]'s own doc says this shell exists not to do.
    val progressProvider: () -> Float = remember(playback) { { playback.progress } }
    val setProgress: (Float) -> Unit = remember(playback) { { playback.progress = it } }
    val togglePlay: () -> Unit = remember(playback) { { playback.playing = !playback.playing } }
    // Which chart the user asked for, or null while the default (dense, in every state) is in
    // charge. Keyed on the loaded journey, so a choice lasts as long as the chart it was made about
    // and a fresh load opens dense again.
    var densityOverride by remember(state) { mutableStateOf<Boolean?>(null) }
    // Playback stopping - paused, or simply finished - always brings the bar back. Without this a
    // journey that ran to the end left the player with no controls and no way to ask for them
    // other than guessing that a tap would work.
    LaunchedEffect(playing) {
        if (!playing) reveal()
    }

    // Dense unless the user said otherwise. Playback no longer takes the choice back on either
    // edge: a view pinned mid-journey is a view somebody asked for.
    val dense = densityOverride ?: true
    val layout = if (dense) state.compactLayout else state.layout
    val chartPaths = if (dense) paths.compact else paths.wide

    // The end state, read here rather than in a child because the share button on the bar is
    // emphasised by it too. derivedStateOf keeps that a single recomposition as the journey lands,
    // instead of one per reported progress value.
    val completed by remember(playback) { derivedStateOf { playback.progress >= COMPLETE_AT } }

    // Keyed on the frame, so a fresh load (or a rebuild that changed what was fetched) says it
    // again and a dismissal lasts for as long as the journey it was about.
    var failedDismissed by remember(state.frame) { mutableStateOf(false) }

    LaunchedEffect(bar.barVisible, playing, bar.autoHideSuppressed, barFocused) {
        if (!bar.barVisible || !playing || bar.autoHideSuppressed || barFocused) return@LaunchedEffect
        // Re-checks rather than sleeping once: a drag that never lifts a finger keeps touching the
        // clock without waking anything up, and this loop is what notices.
        while (true) {
            val quiet = idle.millisSince()
            if (quiet >= BAR_TIMEOUT_MILLIS) {
                bar.hide()
                return@LaunchedEffect
            }
            delay(BAR_TIMEOUT_MILLIS - quiet)
        }
    }

    val followMode = controls.followMode
        ?: FollowCamera.defaultOn(state.layout, state.frame.main.unlocks.size)

    val chartFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { chartFocus.requestFocus() } }

    // The bar and the closing counters both wait out the pause (see PlayerOverview's endHeld):
    // the landing used to flip the strip on the very frame the motion stopped, which read as a
    // hard cut over the picture the journey just finished drawing.
    //
    // Focus goes back to the chart on the way in, for the same reason hiding the bar by hand does:
    // a control on it may be holding focus, and taking the focused node out of composition leaves a
    // D-pad with nowhere to go for the length of the hold.
    LaunchedEffect(completed) {
        if (!completed) {
            endBarHeld = false
            return@LaunchedEffect
        }
        endBarHeld = true
        runCatching { chartFocus.requestFocus() }
        delay(END_BAR_HOLD_MILLIS)
        endBarHeld = false
    }

    // Tapping the chart toggles the bar: with the journey running, the controls are the thing in
    // the way, and waiting three seconds for them to fade is not an answer. A tap that lands on a
    // badge or a medal is asking what it is, not asking for the furniture, so the chart reports
    // that case separately and this never runs for it.
    val toggleBar: () -> Unit = {
        // A tap during the end-state pause is asking for the bar, not toggling it away.
        if (endBarHeld) {
            revealNow()
        } else if (bar.settingsOpen || bar.legendOpen) {
            // One layer per tap, the same rule back follows: with a sheet open, a tap
            // outside it takes the sheet and leaves the bar standing.
            bar.closeSheets()
        } else if (bar.barVisible) {
            // Takes the settings sheet with it: the sheet lives inside the bar's own fade, so
            // leaving it open would only park it out of sight until the bar came back.
            bar.toggleBar()
            // A control on the bar may hold focus, and hiding it would take the focused node out
            // of composition; handing focus back to the chart keeps the D-pad somewhere to go.
            runCatching { chartFocus.requestFocus() }
        } else {
            revealNow()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // Preview pass, so a finger anywhere on the player keeps the countdown from expiring
            // under it whatever it is doing - dragging the slider, holding a button, scrubbing.
            // Whether a press *shows* the bar is not decided here: a tap on the chart toggles it
            // (see the chart's onTap below), and a tap on the bar simply keeps it up.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.changes.any { it.pressed }) {
                            idle.touch()
                            // A finger on the player is somebody asking for the controls back, so
                            // the end-state pause ends here rather than running its three seconds
                            // out under them. Guarded, because this runs on every pointer event of
                            // every drag and an unconditional state write would recompose the
                            // player sixty times a second.
                            if (endBarHeld) endBarHeld = false
                        }
                    }
                }
            }
            // Likewise for keys: with the bar hidden the first press only brings it back, so a
            // D-pad never acts on a control the user cannot see.
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.ButtonB, Key.Back, Key.Escape -> {
                        handleBack()
                        true
                    }
                    else -> {
                        val wasHidden = !bar.barVisible || endBarHeld
                        revealNow()
                        wasHidden
                    }
                }
            },
    ) {
        // A finished picture opens the share sheet by itself, exactly as a finished video does.
        // The notice below still carries it if the chooser is backed out of.
        val context = LocalContext.current
        val imageUri = export.imageUri
        LaunchedEffect(imageUri) {
            if (imageUri != null && export.consumeImageShare()) shareJourneyImage(context, imageUri)
        }

        // What the stage has settled on showing, written by the backdrop's own dwell gate and read
        // by the span header, so the title and the picture cannot name different games (owner
        // report: two gates with the same rule still drifted). See backdropArgs' shownMainGame.
        val shownBackdropGame = remember(state.frame, layout) {
            androidx.compose.runtime.mutableStateOf<Int?>(null)
        }
        PlayerBackdrop(
            state, layout, chartPaths, playback, exportBackground, style, backdropDim / 100f,
            monochrome = ambience == AmbienceAnimation.NOIR,
            shownMainGame = shownBackdropGame,
        )

        // The ambience, between the backdrop art and the chart (UI feedback): in front of
        // the screenshot, behind the badges. The overlay runs on its own quantised 30fps
        // clock, which is the whole reason it is affordable here.
        if (ambience != AmbienceAnimation.NONE) {
            AmbienceOverlay(
                animation = ambience,
                accent = style.accent,
                modifier = Modifier.fillMaxSize(),
                randomSeed = state.frame,
            )
        }

        JourneyChart(
            ready = state,
            layout = layout,
            paths = chartPaths,
            userName = userName,
            progress = progressProvider,
            playing = playing,
            onProgressChange = setProgress,
            onTogglePlay = togglePlay,
            bitmaps = bitmaps,
            // Full screen, deliberately: an inset that started the stage under the band shrank
            // the one thing the mode exists to show. The band's scrim is what keeps its readouts
            // legible over whatever the chart puts up here.
            modifier = Modifier.fillMaxSize().focusRequester(chartFocus),
            style = style,
            speed = controls.speed,
            followMode = followMode,
            // The overview strip below occupies the chart's own top band, and carries the date
            // itself, so the in-canvas caption and date overlay would land underneath it.
            showChartChrome = false,
            showIdleSigns = idleSigns,
            // A tap that closed a tooltip has already been spent on the tooltip; only a tap with
            // nothing on the chart to answer for is about the bar.
            onTap = { outcome -> if (outcome == ChartTapOutcome.MISSED) toggleBar() },
            cursorHandle = cursorHandle,
        )

        // The top band: the way back, then the overview strip. The back arrow leads the band and
        // never leaves - the control bar fades away while the journey runs, so an exit on it was
        // there some of the time - and it is an arrow rather than an X because it does exactly
        // what the system back gesture does, said the same way the rest of the app says it.
        Row(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                // One scrim for the whole band, fading into the stage, instead of a plate per
                // readout: the readouts sit on the picture, and the picture stays one picture.
                .background(
                    // Stronger than the old 0.88-to-nothing ramp (owner request: the readouts
                    // washed out over bright art) - near-opaque at the readouts' own line, with a
                    // mid-stop so the falloff starts below them instead of across them. The
                    // export's band scrim carries the same three stops (ExportStrip.drawBandScrim).
                    Brush.verticalGradient(
                        0f to MaterialTheme.colorScheme.background.copy(alpha = 0.96f),
                        0.55f to MaterialTheme.colorScheme.background.copy(alpha = 0.78f),
                        1f to Color.Transparent,
                    ),
                )
                // Under the front camera, not behind it (owner report: portrait put the strip in
                // the punch-hole's row). The scrim above deliberately stays full-bleed - it is
                // the picture darkening, and it may run under the cutout - while the readouts
                // start below the safe line. Landscape pays nothing: its cutout is on a side
                // edge, and the top inset there is zero.
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // The arrow keeps the bar's own hours: while the journey runs the screen belongs to
            // the picture, and the system back gesture still works the whole time - the arrow is
            // the visible affordance, not the only door.
            AnimatedVisibility(visible = bar.barVisible && !endBarHeld, enter = fadeIn(), exit = fadeOut()) {
                FocusableIconButton(
                    icon = {
                        Icon(
                            Icons.Filled.ArrowBack,
                            contentDescription = "Back",
                            modifier = Modifier.size(BACK_ICON_SIZE),
                        )
                    },
                    onClick = onClose,
                    modifier = Modifier.size(BACK_TOUCH_TARGET),
                )
            }
            // Weighted so a long strip cannot spill off the screen, and `fill = false` so the
            // shield behind it still only covers the chips themselves: a shield stretched across
            // the whole band would swallow the taps that toggle the control bar.
            TapShield(Modifier.weight(1f, fill = false)) {
                PlayerOverview(
                    frame = state.frame,
                    layout = layout,
                    style = style,
                    playback = playback,
                    playerRanks = state.playerRanks,
                    endHeld = endBarHeld,
                    shownGame = shownBackdropGame,
                )
            }
        }

        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // What the journey is missing, said once per load. A partial fetch failure still draws
            // a chart, and a chart with three games quietly absent from it is a lie the player is
            // in no position to spot.
            if (state.failedCount > 0 && !failedDismissed) {
                TapShield {
                    MissingGamesNotice(
                        failedCount = state.failedCount,
                        onRetry = onRetry,
                        onDismiss = { failedDismissed = true },
                    )
                }
            }
            // The bar carries the ring and the cancel while an export runs, so the notice here only
            // reports what became of one.
            ExportNotice(export, showProgress = false)
            AnimatedVisibility(visible = bar.barVisible && !endBarHeld, enter = fadeIn(), exit = fadeOut()) {
                PlayerControls(
                    playback = playback,
                    layout = layout,
                    speed = controls.speed,
                    settingsOpen = bar.settingsOpen,
                    legendOpen = bar.legendOpen,
                    followMode = followMode,
                    // The selection, not the resolved pick: the row keeps saying "Random".
                    ambience = ambienceSelection,
                    onSetAmbience = onSetAmbience,
                    exportBackground = exportBackground,
                    onSetExportBackground = onSetExportBackground,
                    backdropDim = backdropDim,
                    onSetBackdropDim = onSetBackdropDim,
                    onPreviewBackdropDim = onPreviewBackdropDim,
                    idleSigns = idleSigns,
                    onSetIdleSigns = onSetIdleSigns,
                    rivalColorIndex = rivalColorIndex,
                    onSetRivalColor = onSetRivalColor,
                    playerColorIndex = playerColorIndex,
                    onSetPlayerColor = onSetPlayerColor,
                    dense = dense,
                    // Nothing to swap unless the journey earned a wider stage: a compare chart
                    // never does (its badges are rationed per day rather than stretched), and
                    // neither does a short solo one, so the two layouts are the same object and
                    // the button was a no-op the user could press all day. See the ViewModel's
                    // compactLayout.
                    canToggleDensity = state.compactLayout !== state.layout,
                    onToggleDensity = { densityOverride = !dense },
                    onToggleSettings = bar::toggleSettings,
                    onToggleLegend = bar::toggleLegend,
                    onOpenGrid = onOpenGrid,
                    onSetSpeed = onSetSpeed,
                    onSetFollowMode = onSetFollowMode,
                    // The same predicate the renderer gates the owner dot on, so the key cannot
                    // explain a mark this chart is not making.
                    compare = state.frame.rivals.any { it.unlocks.isNotEmpty() },
                    onBarFocusChanged = { barFocused = it },
                    exportSlot = { buttonMod ->
                        // The export's progress is read inside ExportControls, so an export in
                        // flight recomposes one button rather than the whole player.
                        ExportControls(
                            export = export,
                            canExport = bitmaps != null,
                            onShareVideo = onShareVideo,
                            onShareImage = onShareImage,
                            onSaveVideo = onSaveVideo,
                            onSaveImage = onSaveImage,
                            emphasized = completed,
                            buttonModifier = buttonMod,
                        )
                    },
                )
            }
        }
    }
}

/** The way back: one 48dp target leading the player's top band, with a glyph big enough to see. */
private val BACK_TOUCH_TARGET = 48.dp
private val BACK_ICON_SIZE = 28.dp

/**
 * Wraps [content] in a layer that catches taps landing on it and nothing else.
 *
 * The chart runs the full height of the player and toggles the control bar on a tap, so without
 * this a tap on the panel between two buttons - or on the overview strip - would reach the chart
 * and hide the furniture the user was aiming at.
 *
 * The catcher is a sibling *behind* the content rather than a handler on the container around it.
 * A container that consumes pointer events in the initial or main pass breaks its own children:
 * the main pass runs children first, but a child's tap detector re-checks the same event on the
 * final pass (which runs parents first), sees the container's consumption there and cancels the
 * press. That is dead buttons and a slider that cannot be scrubbed, for every gesture that moves
 * even a pixel between finger down and finger up - which is every real one. Behind the content,
 * the hit test never reaches it for a tap that lands on a control, and the tap that lands on the
 * panel stops here instead of falling through to the chart.
 */
@Composable
private fun TapShield(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier) {
        Spacer(
            Modifier
                .matchParentSize()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false).consume()
                        do {
                            val event = awaitPointerEvent()
                            event.changes.forEach { it.consume() }
                        } while (event.changes.any { it.pressed })
                    }
                },
        )
        content()
    }
}

/**
 * The control bar and the settings sheet above it: one focus group, a tap shield per surface, one
 * thing that fades in and out.
 *
 * Stateless apart from the playback holder it reads, so a test can drive it with its own holder
 * and callbacks rather than through the chart, the image cache and autoplay.
 */
@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
internal fun PlayerControls(
    playback: JourneyPlaybackHolder,
    /** The chart being scrubbed, for the one thing the bar reads off it: the streak runs. */
    layout: ChartLayout,
    speed: Float,
    settingsOpen: Boolean,
    legendOpen: Boolean,
    followMode: Boolean,
    ambience: AmbienceAnimation = AmbienceAnimation.NONE,
    onSetAmbience: (AmbienceAnimation) -> Unit = {},
    exportBackground: ExportBackground = ExportBackground.TITLE,
    onSetExportBackground: (ExportBackground) -> Unit = {},
    backdropDim: Int = 15,
    onSetBackdropDim: (Int) -> Unit = {},
    onPreviewBackdropDim: (Int) -> Unit = {},
    idleSigns: Boolean = true,
    onSetIdleSigns: (Boolean) -> Unit = {},
    rivalColorIndex: Int = 0,
    onSetRivalColor: (Int) -> Unit = {},
    playerColorIndex: Int = -1,
    onSetPlayerColor: (Int) -> Unit = {},
    dense: Boolean,
    canToggleDensity: Boolean = true,
    onToggleDensity: () -> Unit,
    onToggleSettings: () -> Unit,
    onToggleLegend: () -> Unit,
    onOpenGrid: (() -> Unit)? = null,
    onSetSpeed: (Float) -> Unit,
    onSetFollowMode: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    /** Whether anybody else is on this chart, for the one key row that is about that. */
    compare: Boolean = false,
    onBarFocusChanged: (Boolean) -> Unit = {},
    exportSlot: @Composable (Modifier) -> Unit = {},
) {
    Column(
        modifier
            .fillMaxWidth()
            .onFocusChanged { onBarFocusChanged(it.hasFocus) }
            .focusGroup(),
    ) {
        // Each surface shields itself rather than one shield spanning the band: the sheet's panel
        // is capped at 460dp while the band is as wide as the screen, and a full-width shield made
        // the gutter beside the panel swallow the tap that was asking the sheet to go away.
        if (settingsOpen) {
            TapShield {
                PlayerSettings(
                    speed = speed,
                    onSetSpeed = onSetSpeed,
                    followMode = followMode,
                    onSetFollowMode = onSetFollowMode,
                    ambience = ambience,
                    onSetAmbience = onSetAmbience,
                    exportBackground = exportBackground,
                    onSetExportBackground = onSetExportBackground,
                    backdropDim = backdropDim,
                    onSetBackdropDim = onSetBackdropDim,
                    onPreviewBackdropDim = onPreviewBackdropDim,
                    idleSigns = idleSigns,
                    onSetIdleSigns = onSetIdleSigns,
                    rivalColorIndex = rivalColorIndex,
                    onSetRivalColor = onSetRivalColor,
                    playerColorIndex = playerColorIndex,
                    onSetPlayerColor = onSetPlayerColor,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
        }
        if (legendOpen) {
            TapShield {
                LegendSheet(compare, modifier = Modifier.padding(horizontal = 12.dp))
            }
        }
        TapShield(Modifier.fillMaxWidth()) {
            PlayerBar(
                playback = playback,
                layout = layout,
                settingsOpen = settingsOpen,
                legendOpen = legendOpen,
                dense = dense,
                canToggleDensity = canToggleDensity,
                onToggleDensity = onToggleDensity,
                onToggleSettings = onToggleSettings,
                onToggleLegend = onToggleLegend,
                onOpenGrid = onOpenGrid,
                exportSlot = exportSlot,
            )
        }
    }
}

/**
 * What the journey is missing, on the player where the journey is.
 *
 * A partial fetch failure is not an error state: the chart is drawn, it is just drawn without the
 * games that never arrived. Nothing else on this screen can say so any more (the ranking list that
 * used to carry the footer is gone), and a chart silently missing a few games is worse than one
 * that admits it. Dismissible, because it is a fact about the load rather than something to fix,
 * and it offers the retry because reloading is the fix.
 */
@Composable
private fun MissingGamesNotice(failedCount: Int, onRetry: () -> Unit, onDismiss: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = chartPanelColor(scheme.surfaceVariant, scheme.background).copy(alpha = 0.92f),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
    ) {
        Row(
            Modifier.padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "${pluralize(failedCount, "game")} couldn't be fetched.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            FocusableButton("Retry", onRetry, outlined = true)
            FocusableIconButton(
                icon = { Icon(Icons.Filled.Close, contentDescription = "Dismiss") },
                onClick = onDismiss,
            )
        }
    }
}

/**
 * The art behind the chart, following the sweep. Its own composable so the progress read that
 * drives it recomposes the backdrop alone.
 */
@Composable
private fun PlayerBackdrop(
    state: JourneyUiState.Ready,
    layout: ChartLayout,
    paths: ChartPaths,
    playback: JourneyPlaybackHolder,
    background: ExportBackground,
    style: ChartStyle,
    dimAlpha: Float = 0.25f,
    /** True under the Noir ambience: the stage renders its art black and white. */
    monochrome: Boolean = false,
    /** The stage's settled game, reported outward for the header; see backdropArgs. */
    shownMainGame: androidx.compose.runtime.MutableState<Int?>? = null,
) {
    JourneyBackdrop(
        art = backdropArgs(state, layout, paths, playback.progress, background, style, shownMainGame),
        modifier = Modifier.fillMaxSize(),
        dimAlpha = dimAlpha,
        monochrome = monochrome,
    )
}

/**
 * The permanent strip along the top: where the journey has got to, what it has scored and how many
 * awards have landed. Quantised to whole percent, the granularity playback reports at and the
 * granularity a counter can be read at, so a full journey recomputes these about a hundred times.
 *
 * Internal rather than private so a test can drive it at a point of playback. Every fixture that
 * renders the whole screen pins reduced motion (otherwise the reveal races the test clock), which
 * jumps playback straight to the end - so the strip's running state has no coverage at all unless
 * something can hand it a holder that is halfway through.
 */
@Composable
internal fun PlayerOverview(
    frame: JourneyFrame,
    layout: ChartLayout,
    style: ChartStyle,
    playback: JourneyPlaybackHolder,
    playerRanks: Map<String, Int>,
    modifier: Modifier = Modifier,
    /**
     * The end-state pause is running: the journey has landed but the picture is resting. The strip
     * keeps saying what it was saying mid-flight, so the closing counters arrive with the control
     * bar when the pause runs out, rather than flipping in on the very frame the motion stops.
     */
    endHeld: Boolean = false,
    /**
     * The game the span header names, from the backdrop's quarter-dwell header gate (see
     * backdropArgs.shownMainGame): the same throttled target stream the stage follows, a beat
     * ahead of the picture (owner request). Null (the tests) reads the sweep directly instead;
     * see the now-playing block below.
     */
    shownGame: androidx.compose.runtime.State<Int?>? = null,
) {
    val rawStep = (playback.progress.coerceIn(0f, 1f) * PROGRESS_STEPS).toInt()
    val step = if (endHeld) minOf(rawStep, PROGRESS_STEPS - 1) else rawStep
    val stats = remember(frame, layout, step) {
        HudAtProgress.stats(frame, layout, step.toFloat() / PROGRESS_STEPS)
    }
    val awards = remember(layout, step) {
        HudAtProgress.awardTally(layout, step.toFloat() / PROGRESS_STEPS)
    }
    // While the journey runs, the date the header says is the date under the runner's icon
    // (HudAtProgress.axisDateMillis) rather than the timestamp of the last unlock the sweep
    // passed - on a clustered chart those disagreed by weeks and the strip looked detached from
    // the motion. The end state keeps the real last-unlock date.
    val shownDateMillis = remember(frame, layout, step) {
        if (step == PROGRESS_STEPS) stats.journeyDateMillis
        else HudAtProgress.axisDateMillis(layout, step.toFloat() / PROGRESS_STEPS)
            ?: stats.journeyDateMillis
    }

    // A single game with rivals on it never shows the generic counters, before or after the
    // journey lands: there is one game, one achievement list and one date range on that chart, and
    // the question it exists to answer is how the players did against each other - said the same
    // way the span says it, one compact line per player on one bar. The numbers move with the
    // sweep too: who was ahead is a question with a different answer at every point of playback,
    // not just at the end.
    //
    // Cut by each player's own ELAPSED time rather than by one instant, because this chart is a
    // race - its axis is time since each player's own first unlock, so the shared sweep means "this
    // much of their run" to each of them separately. Cutting the rivals by the main player's
    // calendar (shownDateMillis) is what showed a rival who played the game a year earlier as
    // already finished on frame one, and dropped a rival who played it later off the bar entirely.
    // See [frameUpToElapsed].
    if (JourneyStripText.isGameCompare(frame)) {
        val truncated = remember(frame, layout, step) {
            frameUpToElapsed(frame, HudAtProgress.sweepElapsedMillis(layout, step.toFloat() / PROGRESS_STEPS))
        }
        // The rank is a spoiler: it is where the race ENDS, not a running readout, so the row
        // withholds it until the same `step == PROGRESS_STEPS` instant every other "has this
        // landed" chip in this file waits for - `endHeld` included, via `step` above.
        val ranks = if (step == PROGRESS_STEPS) playerRanks else emptyMap()
        PlayerCompareBar(truncated, style, ranks, modifier)
        return
    }

    // A solo single-game journey gets a header rather than a row of counters, from the first frame
    // to the last: the game it is about, how far through its achievement list the replay has got,
    // what that is worth and where in time it is. The counters it replaces said the same things in
    // four separate boxes without ever naming the game the whole chart is of.
    if (frame.mode == JourneyMode.GAME) {
        val header = remember(frame, layout, stats, awards, step) {
            JourneyStripText.gameHeader(
                frame = frame,
                layout = layout,
                journeyDateMillis = shownDateMillis,
                revealed = stats.unlocked,
                points = stats.points,
                completed = step == PROGRESS_STEPS,
                awards = awards,
                hardcore = stats.hardcore,
            )
        }
        GameHeaderBar(header, JourneyStripText.awardParts(awards), style, modifier)
        return
    }
    // Folded into the same per-step memo the counters use. Unremembered it ran on every parent
    // recomposition as well as every step, and each call builds a temporal object and formats a
    // date - the formatters are hoisted, the work around them was not.
    val whenLabel = remember(frame, layout, shownDateMillis, step) {
        JourneyStripText.whenLabel(frame, layout, shownDateMillis, step == PROGRESS_STEPS)
    }

    // A finished span has different things worth saying (see JourneyStripText.endChips): the date
    // has become the whole window and the points are the chart's own axis, so the strip turns into
    // what the journey came to - games, achievements, awards and the longest run of days.
    if (step == PROGRESS_STEPS) {
        // A compare span finishes on the per-player rows alone - one line per player, the mode's
        // whole point. The owner's totals cards that used to stack above them were one player's
        // counters at the top of a chart about who did what against whom. GAME mode never reaches
        // here - its compare row replaces the counters from the first frame.
        if (JourneyStripText.hasRivals(frame)) {
            PlayerCompareBar(frame, style, playerRanks, modifier)
            return
        }
        val chips = remember(frame, awards, stats) {
            JourneyStripText.endChips(frame, awards, stats.unlocked, stats.games, stats.points)
        }
        EndPanel(chips, style, modifier)
        return
    }

    // A running compare span wears the SAME per-player bar the end state wears (UI feedback),
    // with running numbers: the frame is truncated to the time under the sweep and handed to the
    // very composable the landing uses, so "who is ahead, on what" reads identically at every
    // point of playback. The moving date lives on the chart's own corner readout, like solo mode.
    if (JourneyStripText.hasRivals(frame)) {
        val runningFrame = remember(frame, step) {
            val t = shownDateMillis ?: Long.MAX_VALUE
            frameUpTo(frame, t)
        }
        // No ranks here, ever: this branch only runs before `step == PROGRESS_STEPS` (the landed
        // case returned above), and the rank is a spoiler until then - see the sibling comment on
        // the single-game race's own `PlayerCompareBar` call.
        PlayerCompareBar(runningFrame, style, emptyMap(), modifier)
        return
    }

    // Which game the header names: the one the STAGE has settled on showing, read straight off
    // the backdrop's own dwell gate (see backdropArgs.shownMainGame) so the title and the picture
    // cannot disagree - the header briefly ran a gate of its own with the same rule, and two
    // gates stamping their dwells at separate instants still named different games for seconds at
    // a time (owner report). A caller with no stage to follow (the tests) reads the sweep
    // directly, which is the unthrottled behaviour they pin. The counters and awards below stay
    // on the sweep either way, untouched.
    val nowPlayingId = if (shownGame != null) {
        shownGame.value
    } else {
        remember(frame, layout, step) {
            val sweep = HudAtProgress.sweepX(layout, step.toFloat() / PROGRESS_STEPS)
            BackdropLogic.gameAtSweep(layout.main.bgTrack, sweep)
        }
    }
    val nowPlaying = remember(frame, layout, style, nowPlayingId) {
        nowPlayingId?.let { frame.main.games[it] }
            ?.let { game ->
                NowPlaying(
                    title = game.title,
                    iconUrl = game.iconPath?.let(JourneyUrls::media),
                )
            }
    }
    // The count in front of the name freezes WITH the name (owner report: the name held under
    // the throttle while the count kept climbing, so the header read "19 - <the 18th game>").
    // Captured at each adoption - the recomposition the shown id changes on still sees that
    // instant's running count. Unthrottled this is indistinguishable from the live count: the
    // games tally increments exactly when the sweep enters a game it has not seen, which is the
    // same instant the id changes, so the two can only come apart while the gate is holding.
    val nowPlayingGames = remember(frame, layout, nowPlayingId) { stats.games }

    // A running span gets the same two-line header a single game does: the running game count in
    // front of the game under the sweep ("3 - Zelda") on the first line, and the date with what
    // the journey has come to on the second - achievements, points and the award dots. It was
    // four chips in a `Row` that could not wrap, so on a phone the awards counter was simply
    // pushed off the right of the screen.
    //
    // Built through JourneyStripText.spanHeader rather than out of three separate calls here: the
    // exported frame draws the same two lines from the same object (`spanHeaderChips`), so the
    // shared picture and the watched one cannot come to word - or shape - this differently.
    val header = JourneyStripText.spanHeader(
        whenLabel = whenLabel,
        // The frozen pair, not the live tally: the count belongs to the game named beside it.
        games = nowPlayingGames,
        gameTitle = nowPlaying?.title,
        achievements = stats.unlocked,
        points = stats.points,
        awards = awards,
    )
    PlaybackHeader(
        iconUrl = nowPlaying?.iconUrl,
        // Theme accent, not the game's series colour: the game
        // name cycling through per-game palette colours as the sweep crossed games read as
        // random recolouring, on a header whose ink everywhere else follows the theme.
        iconRing = style.accent,
        title = header.title,
        count = "",
        facts = header.facts,
        // Fixed slots, so a count gaining a digit cannot shuffle the line mid-playback. No date
        // here any more: the running date lives on the chart's own zoomed-in overlay (see
        // JourneyChartRenderer.zoomDateLabel), quantized to the axis ticks so the two can never
        // disagree - which the header's interpolated readout and the axis regularly did.
        factSlots = listOf(
            pluralize(stats.unlocked, "achievement") to SPAN_FACT_SLOTS.achievements.dp,
            pluralize(stats.points, "pt") to SPAN_FACT_SLOTS.points.dp,
        ),
        awards = header.awards,
        style = style,
        modifier = modifier,
        // Slightly larger than the shared default (owner request): the span header's icon is the
        // one picture of the game under the sweep, and at the shared size it read as a bullet
        // point. The fonts and slots around it are untouched.
        iconSize = SPAN_HEADER_ICON,
    )
}

/**
 * The header a single-game journey carries instead of a row of counters: the game's own icon and
 * title on the first line; how far through the achievement list the replay has got, what that is
 * worth and where in time it is on the second; and on the right, a slim bar with the percentage and
 * the award the player took under it.
 *
 * It replaces the counters at every point of playback, the end included. Four boxes reading "Date",
 * "Points", "Awards" and "12/42" never once named the game the whole chart is of, which on a chart
 * of exactly one game is the first thing anybody looks for. The percentage is the achievement
 * list's, not playback's: it is what the bar beside it is a bar of.
 *
 * One award, not a breakdown. A per-kind tally is what a span needs, where forty games earned a mix
 * of them; one game has one highest award, and drawing it as "1 mastered" beside a bar was a count
 * of one taking the width of a sentence. It is the same circle the chart's own medallion carries
 * (metal, fill and glow through [AwardGrading] and [JourneyChartRenderer]), which is what makes it
 * readable without a word next to it.
 *
 * One merged semantics node, like every other piece of strip furniture, so a screen reader reads the
 * header as one sentence rather than as five fragments - the award included, since a drawn circle
 * says nothing out loud.
 */
@Composable
private fun GameHeaderBar(
    header: JourneyStripText.GameHeader,
    awards: List<JourneyStripText.AwardPart>,
    /** The screen's style, "Your Color" override included: the ring and the percent follow it. */
    style: ChartStyle,
    modifier: Modifier = Modifier,
) {
    val accent = style.accent
    // Best first (see JourneyStripText.awardParts), so the head of the list is the highest award
    // the sweep has reached.
    val highest = awards.firstOrNull()?.kind
    PlaybackHeader(
        iconUrl = header.iconUrl,
        iconRing = accent,
        title = header.title,
        count = "",
        facts = header.countsLine,
        // The same fixed slots the running span's header uses, for the same reason: the counts
        // change several times a second and a line laid out at their natural widths shuffled every
        // fact after them - and the bar at the end of the row - along with them (user report). The
        // cost slot only appears once the journey has landed, which is when the fact does.
        factSlots = listOfNotNull(
            header.achievementsLine to GAME_FACT_SLOTS.achievements.dp,
            header.pointsLine to GAME_FACT_SLOTS.points.dp,
            header.cost?.let { it to GAME_FACT_SLOTS.cost.dp },
        ),
        awards = emptyList(),
        style = style,
        modifier = modifier,
        spoken = listOfNotNull(header.spoken, highest?.let(AwardGrading::title)).joinToString(", "),
        trailing = {
            // Pinned to the TOP of the header rather than centred in it (user request): the bar
            // reads as belonging to the title's line, and it no longer drifts down when the facts
            // line gains its cost run at the end of the journey.
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.align(Alignment.Top),
            ) {
                // The award sits at the END OF THE BAR, not under it beside the number (user
                // request): it is where the vault's achievement tab and the library's board both
                // put it (`AchievementsBoard` draws the same pairing - the bar taking the row and
                // the circle after it as a sibling, percentage underneath), and this header is a
                // third view of the very same completion. The three used to disagree about where
                // the award lives while agreeing about everything else it says.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(HEADER_AWARD_GAP),
                ) {
                    GameProgressBar(header.percent, header.hardcorePercent)
                    // The circle's room is held WHETHER OR NOT there is an award yet. The block is
                    // right-aligned, so a row that only reserved it once an award existed jerked
                    // the bar sideways the instant one landed, mid-replay (user report). An award
                    // arrives once in a journey; the bar should not be what announces it.
                    Box(Modifier.size(HEADER_AWARD_CIRCLE), contentAlignment = Alignment.Center) {
                        if (highest != null) AwardCircle(highest, style, HEADER_AWARD_CIRCLE)
                    }
                }
                Text(
                    "${header.percent}%",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    // The theme's own ink, like every other value on the header (user request):
                    // the percentage is a fact, and it was the one fact wearing the player's colour.
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
            }
        },
    )
}

/**
 * The header's achievement bar, in the metals the rest of the app draws a RetroAchievements game's
 * completion in: the whole revealed count in silver, the hardcore share of it in gold over the top.
 *
 * Not this file's rule and not this file's colours - it is the achievement tab's dual bar
 * (`ui/screens/RetroAchievementDrawer.kt`'s "softcore = silver gradient, hardcore = gold gradient",
 * and `ui/components/AchievementsBoard.kt`'s same two layers), drawn through the very same
 * [achievementBarBrush]. A journey and the drawer for the same game are two views of one number, and
 * an accent-coloured bar in one of them said nothing about the mode the achievements were taken in.
 *
 * The track is the accent at low alpha rather than a theme surface, because this bar sits on the
 * header's own plate over the chart rather than in a sheet.
 */
@Composable
private fun GameProgressBar(
    percent: Int,
    hardcorePercent: Int,
    width: Dp = GAME_BAR_WIDTH,
) {
    val accent = accentColor()
    Box(
        Modifier
            .width(width)
            .height(GAME_BAR_HEIGHT)
            .clip(RoundedCornerShape(GAME_BAR_HEIGHT / 2))
            .background(accent.copy(alpha = 0.22f)),
    ) {
        Box(
            Modifier
                .fillMaxWidth((percent / 100f).coerceIn(0f, 1f))
                .fillMaxHeight()
                .background(achievementBarBrush(mastered = false)),
        )
        if (hardcorePercent > 0) {
            Box(
                Modifier
                    .fillMaxWidth((hardcorePercent / 100f).coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(achievementBarBrush(mastered = true)),
            )
        }
    }
}

/**
 * The two-line header both modes carry while the journey plays: the game on top with its count, and
 * underneath it where in time the sweep is, what it has scored, and which awards have landed.
 *
 * Two lines exactly, and slim ones. What this replaces was four stacked lines on a single-game
 * journey - title, achievements, points, date, each at its own size - and on a span a row of four
 * chips that could not wrap; the first was half a handheld's screen tall over the chart it was
 * about, and the second pushed its last counter off the right edge. Everything here is
 * constraint-based rather than width-capped: the title takes the space the count and the bar leave
 * and ellipsizes into it, so the same header fits a phone held upright and a handheld held sideways
 * without either being measured.
 *
 * The panel is opaque. It used to be painted at 86% over the chart, which put the chart's own grid
 * lines and axis labels *through* the text on it - and the line that suffered was the date, the one
 * line drawn in `onSurfaceVariant`, which in this app's theme is the foreground at 70% alpha. A
 * translucent glyph on a translucent panel over a chart is two text runs in the same place, which is
 * exactly what it looked like. Every line here is full-strength ink on a solid plate.
 *
 * One merged semantics node, like every other piece of strip furniture, so a screen reader reads the
 * header as one sentence rather than as five fragments.
 */
@Composable
private fun PlaybackHeader(
    iconUrl: String?,
    iconRing: Color,
    title: String,
    count: String,
    facts: String,
    awards: List<JourneyStripText.AwardPart>,
    style: ChartStyle,
    modifier: Modifier = Modifier,
    spoken: String? = null,
    /**
     * Fixed slots drawn in place of [facts] when non-null: each fact and the SHARE OF THE LINE it
     * claims, whatever it currently says.
     *
     * The counts change width every reported step, and a line laid out from their natural widths
     * shuffled everything after them - and the bar at the end of the row - about seven times a
     * second (user report). A slot pins each fact's x.
     *
     * Dp, and the same numbers the export reserves in pixels ([GAME_FACT_SLOTS],
     * [SPAN_FACT_SLOTS]), so the plate and the player lay the line out identically.
     *
     * They were briefly a SHARE of the line instead, which fits any width and is wrong: on a
     * landscape handheld three shares spread the facts across the whole band with a hand's width of
     * nothing between them. A slot is meant to reserve enough room for a fact, not to spread it.
     *
     * The cost of absolute slots is that a narrow portrait header cannot hold all of them - Compose
     * gives the overflow to the first children and nothing to the last, so the points and the cost
     * measured zero and vanished. [factSlotsFit] is the guard: too little room and the header falls
     * back to [facts], the one ellipsizing run it drew before slots existed.
     *
     * [facts] still feeds the spoken description.
     */
    factSlots: List<Pair<String, Dp>>? = null,
    /**
     * Drawn at the end of the row. Given the [RowScope] so it can pin itself with `align` - the
     * single game's bar takes `Alignment.Top` (user request), where a block centred against a
     * two-line header sat low against the title it belongs to.
     */
    trailing: @Composable (RowScope.() -> Unit)? = null,
    /**
     * The game icon's edge. The span header passes a larger one (owner request: the icon reads
     * small beside two lines of facts); everything else about the row - fonts, slots, padding -
     * stays exactly as it is, and the row is already taller than either icon so neither size
     * moves the header's own height.
     */
    iconSize: Dp = HEADER_ICON,
) {
    val scheme = MaterialTheme.colorScheme
    val description = spoken ?: listOfNotNull(
        title,
        count.takeIf { it.isNotEmpty() },
        facts,
        JourneyStripText.awardsWords(awards).takeIf { it.isNotEmpty() },
    ).joinToString(", ")
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = headerPanelColor(scheme),
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = description },
    ) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val shape = RoundedCornerShape(HEADER_ICON_CORNER)
            if (iconUrl != null) {
                AsyncImage(
                    model = iconUrl,
                    contentDescription = null,
                    modifier = Modifier.size(iconSize).clip(shape).border(1.dp, iconRing, shape),
                )
            } else {
                // A game with no art still has a place in the row; leaving it out would shift the
                // whole block sideways between one journey and the next.
                Box(Modifier.size(iconSize).clip(shape).background(iconRing.copy(alpha = 0.3f)))
            }
            Column(Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        // No colour override: the title inherits the same ink the strip's
                        // values use (owner request), leaving iconRing to the ring alone.
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        // Weighted rather than capped: the count beside it is measured first and
                        // the title takes whatever is left, so a long name ellipsizes instead of
                        // pushing the count off the line.
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    // Empty on the single-game header, which carries its date on the line below with
                    // the counts: an empty run would still take the row's leading gap.
                    if (count.isNotEmpty()) {
                        Text(
                            count,
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onSurface,
                            maxLines = 1,
                        )
                    }
                }
                // BoxWithConstraints, because whether the slots fit is a fact about the room this
                // header actually got - which differs between a landscape handheld and a phone in
                // portrait, and is not knowable where the slots are chosen.
                BoxWithConstraints {
                    val slotted = factSlots != null && factSlotsFit(maxWidth, factSlots)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(HEADER_FACT_GAP),
                    ) {
                        if (slotted && factSlots != null) {
                            factSlots.forEachIndexed { index, (text, width) ->
                                // The "·" between slotted facts (owner request): without it
                                // "271 achievements   1428 pts" read as one blur across the gap,
                                // while the unslotted fallback string below always carried one.
                                // A constant-width glyph between constant-width slots, so nothing
                                // regains the jitter the slots exist to prevent.
                                if (index > 0) {
                                    Text(
                                        "·",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = scheme.onSurfaceVariant,
                                        maxLines = 1,
                                    )
                                }
                                Text(
                                    text,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = scheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.width(width),
                                )
                            }
                        } else {
                            Text(
                                facts,
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                        }
                        // PlaybackHeader's own [awards] is only ever fed by a running span (see
                        // its call site below); GameHeaderBar hands it an empty list, so counts
                        // default on. The "·" leads the dots the way it leads them everywhere a
                        // fact precedes them (owner request) - the end strip gets its own from
                        // the separators between its chips.
                        if (awards.isNotEmpty()) {
                            Text(
                                "·",
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                            AwardDots(awards, style)
                        }
                    }
                }
            }
            trailing?.invoke(this)
        }
    }
}

/**
 * The panel every piece of strip furniture is drawn on.
 *
 * Opaque, unlike the 86% it used to be: the strip sits over a chart that draws grid lines, axis
 * labels and badge art, and at 86% all of it read through the counters on top. See [PlaybackHeader].
 */
@Composable
/**
 * The top band's readouts sit straight on the stage now: one shared scrim behind the whole band
 * (see the player's top Row) does the legibility work, and a plate per readout on top of it read
 * as a row of widgets parked over the picture.
 */
private fun headerPanelColor(
    @Suppress("UNUSED_PARAMETER") scheme: androidx.compose.material3.ColorScheme,
): Color = Color.Transparent

/** The game's own art, at the size a two-line header can carry without setting its height. */
private val HEADER_ICON = 28.dp

/**
 * The span header's own icon size - the two-line header is ~40dp tall either way, so the bigger
 * icon fills the row it already had instead of floating in it. See [PlaybackHeader]'s iconSize.
 */
private val SPAN_HEADER_ICON = 36.dp
private val HEADER_ICON_CORNER = 4.dp

/**
 * How tall the end panel's single line is: tall enough for its `titleMedium` values plus padding,
 * so nothing is clipped at the default text scale.
 */
private val END_CHIP_HEIGHT = 40.dp

/**
 * The two playback headers' fact slots, in the order each draws them.
 *
 * Wide enough for the longest the fact can get, because a slot narrower than its text ellipsizes:
 * the achievement and point runs carry an "x of y" pair whose halves both grow ("342 of 656
 * points"), and a single game's cost line has to hold the longest award name in front of the hours
 * ("Mastered in 100h 59m").
 *
 * `JourneyStripText.Chip.slotDp` carries these to the export as plain numbers, so the exported
 * plate reserves the same room the player does and the two lay the line out identically.
 *
 * See PlaybackHeader.factSlots for what a slot is for: a counter that takes its own width drags
 * every fact after it - and the bar at the end of the row - sideways as its digits change.
 */
/**
 * Whether a header's fixed fact slots fit the room it was given, gaps included.
 *
 * The alternative is worse than a fallback: Compose measures a Row's fixed-width children in order
 * and hands the overflow nothing, so slots that do not fit do not shrink - the last facts measure
 * zero and disappear off the header entirely. Below this bar the header draws its one ellipsizing
 * run instead, which is what it drew before slots existed and still says everything, just tighter.
 */
internal fun factSlotsFit(available: Dp, slots: List<Pair<String, Dp>>): Boolean {
    if (slots.isEmpty()) return false
    // Each boundary carries a "·" between two gaps now, not one bare gap: the fit check has to
    // charge what the row actually draws, or a header that just fits its slots would clip the
    // separator it gained.
    val gaps = (HEADER_FACT_GAP * 2 + HEADER_FACT_SEP) * (slots.size - 1)
    return slots.fold(gaps) { sum, (_, w) -> sum + w } <= available
}

/** The gap between one fact slot and the next, and what [factSlotsFit] charges for it. */
internal val HEADER_FACT_GAP = 6.dp

/** The room the "·" between two slots takes, generously, for [factSlotsFit]'s arithmetic. */
internal val HEADER_FACT_SEP = 8.dp

internal val SPAN_FACT_SLOTS = JourneyStripText.FactSlots(achievements = 150f, points = 90f)
internal val GAME_FACT_SLOTS =
    JourneyStripText.FactSlots(achievements = 190f, points = 165f, cost = 175f)


/** Slim, because it is a readout rather than a control. */
private val GAME_BAR_WIDTH = 64.dp
private val GAME_BAR_HEIGHT = 4.dp

/**
 * The finished span's counters as one panel: a single surface, every fact on the same baseline,
 * a dim dot between them. It replaces a row of four separate cards - four boxes at the top of the
 * screen read as furniture, one line reads as a sentence, and a screen reader gets it as one
 * passage rather than eight unattached fragments.
 */
@Composable
private fun EndPanel(
    chips: List<JourneyStripText.Chip>,
    style: ChartStyle,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = headerPanelColor(scheme),
        modifier = modifier.testTag("endPanel").semantics(mergeDescendants = true) {},
    ) {
        Row(
            Modifier.height(END_CHIP_HEIGHT).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            chips.forEachIndexed { index, chip ->
                if (index > 0) {
                    Text("·", style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
                }
                if (chip.label.isNotEmpty()) {
                    Text(
                        chip.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                if (chip.value.isNotEmpty()) {
                    Text(
                        chip.value,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = SPAN_CHIP_TEXT_MAX),
                    )
                }
                // The circle and its count at the panel's own text size, and no kind words:
                // the metal and the fill already say mastered/completed/beaten (the legend
                // spells it out), and the words were most of the panel's width. EndPanel only
                // ever draws a finished span's chips (single-game mode has its own header), so
                // counts default on.
                if (chip.awards.isNotEmpty()) {
                    AwardDots(
                        chip.awards, style,
                        diameter = HEADER_AWARD_CIRCLE,
                        textStyle = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}

/**
 * An award breakdown as the chart draws it: one kind dot per kind, with its count beside it.
 *
 * The dots are the chart's own - same colour, same fill, through
 * [JourneyChartRenderer.medalColor] and [AwardGrading] - so the counter and the medallions it
 * counts cannot come to disagree about which award is which. That is the whole reason this is drawn
 * rather than written: "3 awards" said nothing about whether they were masteries, and three words
 * saying so took more of a handheld's width than the rest of the row put together.
 *
 * [counts] used to be `words`, a flag that would carry the kind's NAME after the count ("3
 * mastered" rather than "3") - a mode nothing called for any more, since every surface leaves the
 * dot to say which kind and the name was most of the width wherever it was tried. Rather than add a
 * third flag beside a second one nobody used, this repurposes it: [counts] now says whether the
 * count is drawn at all, which is what a single-game compare row needs - one award per kind at
 * most, so its number could only ever read "1". [JourneyStripText.awardCountsShown] is where that
 * decision is made; the exported strip's own count draws the same way for the same reason
 * (`ExportStrip.awardText`).
 */
@Composable
private fun AwardDots(
    parts: List<JourneyStripText.AwardPart>,
    style: ChartStyle,
    counts: Boolean = true,
    diameter: Dp = AWARD_CIRCLE,
    textStyle: androidx.compose.ui.text.TextStyle? = null,
) {
    val countStyle = textStyle ?: MaterialTheme.typography.labelSmall
    // The pixel font's digits sit low in their line box (no descenders, ascent past its em), so
    // a box-centred circle rides visibly high beside them - the same quirk the exported strip
    // corrects in ExportStrip.awardDotCenterY, done here the Compose way: the count aligns by its
    // own real baseline and the circle declares its alignment line at centre + em/2, which puts
    // the circle's centre exactly on the digits' em square. Ordinary fonts keep the box centring
    // they always had.
    val alignToDigits = style.pixelFont && counts
    val emPx = with(androidx.compose.ui.platform.LocalDensity.current) { countStyle.fontSize.toPx() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        parts.forEach { part ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                AwardCircle(
                    part.kind, style, diameter,
                    modifier = if (alignToDigits) {
                        Modifier.alignBy { it.measuredHeight / 2 + (emPx / 2f).roundToInt() }
                    } else {
                        Modifier
                    },
                )
                // Omitted rather than blanked to "": a blank Text still claims the spacedBy gap
                // beside the dot, which on a single-game compare row is exactly the width this
                // exists to give back.
                if (counts) {
                    Text(
                        "${part.count}",
                        style = countStyle,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        modifier = if (alignToDigits) Modifier.alignByBaseline() else Modifier,
                    )
                }
            }
        }
    }
}

/**
 * One award, as the chart's own medallion wears it: the metal, the fill, and the glow behind it.
 *
 * Everywhere an award is drawn outside the chart draws it through here - the strip's breakdown, the
 * single-game header, a compare line - so a mastery is the same mark on every surface in the
 * feature. It used to be a flat disc off the chart and a glowing one on it, which made the one mark
 * the whole feature is about the only one that changed depending on where it was drawn.
 *
 * The glow is the medallion's own falloff ([JourneyChartRenderer.AWARD_GLOW_ALPHA]) fitted inside
 * this box rather than spilling out of it: a readout has words either side of its circle, and the
 * chart's 2.6 radii of halo would wash them. Which is why the disc is a fraction of the box rather
 * than the whole of it - the box is the glow, the disc is the award.
 */
@Composable
private fun AwardCircle(
    kind: AwardKind,
    style: ChartStyle,
    diameter: Dp = AWARD_CIRCLE,
    modifier: Modifier = Modifier,
) {
    val color = JourneyChartRenderer.medalColor(kind, style)
    val filled = AwardGrading.filledDot(kind)
    Canvas(modifier.size(diameter)) {
        val glow = size.minDimension / 2f
        val r = glow / JourneyChartRenderer.AWARD_GLOW_SPAN
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    color.copy(alpha = JourneyChartRenderer.AWARD_GLOW_ALPHA),
                    Color.Transparent,
                ),
                center = center,
                radius = glow,
            ),
            radius = glow,
            center = center,
        )
        if (filled) {
            drawCircle(color, r, center)
        } else {
            // The same outline the chart's own hollow dot is drawn with, in proportion: it is what
            // separates a completion from the mastery it shares a metal with.
            val stroke = r * 0.44f
            drawCircle(color, r - stroke / 2f, center, style = Stroke(stroke))
        }
    }
}

/**
 * How much room an award circle takes in a line of text, and on the single-game header where it is
 * the one award on the panel rather than one of three.
 *
 * The disc inside them is [JourneyChartRenderer.AWARD_GLOW_SPAN] smaller, which puts the ordinary
 * one back at the 9dp the flat dot used to be.
 */
private val AWARD_CIRCLE = 16.dp
private val HEADER_AWARD_CIRCLE = 22.dp

/**
 * The gap between the single-game header's bar and the award circle at its end.
 *
 * `AchievementsBoard`'s own `Spacer(Modifier.width(8.dp))` between the same two things, so the
 * header and the board space the pairing alike. See [ExportStrip.BAR_AWARD_GAP], which is this
 * measured through the export's own density.
 */
private val HEADER_AWARD_GAP = 8.dp



/**
 * How a compare span ends: one compact bar, one line per player, and every line the same five
 * marks - their face, their name, their score, the awards they took and where they stand on the
 * site.
 *
 * It replaces a `FlowRow` of two-line chips. Those carried four counters and a rank each, which on
 * a phone held sideways wrapped to two lines per player and then wrapped again onto a second row of
 * chips, so a three-player comparison was most of the screen and the awards - the last thing on each
 * chip - were regularly off the bottom of it. Everything here is constraint-based: the name is the
 * only weighted element, so the score, the circles and the rank are measured first and the name
 * ellipsizes into whatever is left. Three players with long names fit a landscape phone's width.
 *
 * One merged semantics node per line, so a screen reader reads "aria, 430 pts, 2 mastered, Rank
 * #91" as one player rather than as five unattached fragments.
 */
@Composable
private fun PlayerCompareBar(
    frame: JourneyFrame,
    style: ChartStyle,
    ranks: Map<String, Int>,
    modifier: Modifier = Modifier,
) {
    val lines = remember(frame, ranks) { JourneyStripText.compareLines(frame, ranks) }
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = headerPanelColor(scheme),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            lines.forEach { line ->
                val color = if (line.isMain) style.accent else style.colorForRivalIndex(line.rivalIndex)
                PlayerCompareLine(line, color, style)
            }
        }
    }
}

/** One player's line on the end bar. See [PlayerCompareBar] for what it is laid out against. */
@Composable
private fun PlayerCompareLine(
    line: JourneyStripText.PlayerLine,
    color: Color,
    style: ChartStyle,
) {
    val scheme = MaterialTheme.colorScheme
    val awards = JourneyStripText.compareAwards(line)
    val points = JourneyStripText.compareLineFacts(line)
    val spoken = listOfNotNull(
        line.name,
        points.replace(" · ", ", "),
        line.percent?.let { "$it%" },
        JourneyStripText.awardsWords(awards).replace(" · ", ", ").takeIf { it.isNotEmpty() },
        line.rank,
    ).joinToString(", ")
    Row(
        Modifier
            .fillMaxWidth()
            .height(COMPARE_LINE_HEIGHT)
            .semantics(mergeDescendants = true) { contentDescription = spoken },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // The picture over a disc of the player's own colour, so a missing or misspelled UserPic
        // path (the media host is case sensitive) leaves their colour rather than a hole.
        Box(
            Modifier
                .size(COMPARE_LINE_AVATAR)
                .clip(CircleShape)
                .background(color),
        ) {
            AsyncImage(
                model = JourneyUrls.userPic(line.name),
                contentDescription = null,
                modifier = Modifier.matchParentSize().clip(CircleShape),
            )
        }
        // Fixed columns, so every fact sits directly over the same fact on the line below and
        // directly after the name beside it. A weighted name pushed each line's facts to wherever
        // that name happened to end - three players made three ragged columns of numbers.
        Text(
            line.name,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(COMPARE_NAME_COL),
        )
        Text(
            points,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = scheme.onSurface,
            maxLines = 1,
            // Ellipsis, not the silent default Clip: the column swallowing a word whole is how
            // "12 achievements" briefly read "12" with nothing to say something was missing.
            overflow = TextOverflow.Ellipsis,
            // FIXED again (owner request, round two): the floor version let each row's facts
            // breathe with their digits, which walked the bar, the percent and the dots sideways
            // through the whole replay and misaligned them between rows. The column is sized for
            // the widest realistic run instead ("8000 pts · 200 achievements" in the pixel font),
            // so it both fits the word and holds every later column still.
            modifier = Modifier.width(COMPARE_FACTS_COL),
        )
        // How far through the game's list this player is - the solo header's bar, per player
        // (owner request), in fixed columns like everything else on the line so the bars stack
        // exactly under each other. Only a single-game race carries one; see [PlayerLine.percent].
        line.percent?.let { pct ->
            GameProgressBar(pct, line.hardcorePercent, width = COMPARE_BAR_WIDTH)
            // The award rides the END OF THE BAR, exactly as the solo header's circle does
            // (owner request): the bar and the award are two readings of the same completion,
            // and the dots parked out in the middle of the line separated them for nothing.
            // At most one dot here - a game compare row already carries only the highest.
            if (awards.isNotEmpty()) {
                AwardDots(awards, style, counts = false)
            }
            Text(
                "$pct%",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = scheme.onSurface,
                maxLines = 1,
                modifier = Modifier.width(COMPARE_PERCENT_COL),
            )
        }
        // A SPAN row's awards take the flexible middle with their "·" lead - it has no bar for
        // them to ride - and the rank owns the end of the line in both modes. Start-aligned, so
        // the dots still line up under each other; whether they may show counts is
        // [line.mode]'s call ([JourneyStripText.awardCountsShown]).
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (line.percent == null && awards.isNotEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        "·",
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                    AwardDots(awards, style, counts = JourneyStripText.awardCountsShown(line.mode))
                }
            }
        }
        line.rank?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/**
 * Whether the bar's button row must drop to [PLAYER_BAR_COMPACT_BUTTON]: true when the row cannot
 * hold [buttonCount] standard 48dp touch targets and the gaps between them. Checked against what
 * the buttons genuinely want rather than a magic screen width, so a span bar - one button fewer,
 * no achievements grid - keeps full-size buttons on the same phone a game bar has to shrink on.
 */
internal fun playerBarCompact(availableDp: Float, buttonCount: Int): Boolean =
    availableDp < buttonCount * STANDARD_BUTTON_DP + (buttonCount - 1) * BAR_BUTTON_GAP_DP

/** Material's minimum touch target, which is what an unconstrained IconButton measures. */
private const val STANDARD_BUTTON_DP = 48f

/** The gap the bar's row places between buttons; shared with [playerBarCompact]'s arithmetic. */
internal const val BAR_BUTTON_GAP_DP = 2f

/**
 * The bar's buttons when the row cannot hold them at 48dp: seven of these plus gaps want 292dp,
 * which fits the 316dp a 360dp portrait phone leaves the row. Still a comfortable touch target -
 * the icon inside stays its standard 24dp either way.
 */
private val PLAYER_BAR_COMPACT_BUTTON = 40.dp

/** One line of the end bar: the same height for every player, whatever their line carries. */
private val COMPARE_LINE_HEIGHT = 26.dp

/** The compare row's completion bar: narrower than the header's, it shares a line with more. */
private val COMPARE_BAR_WIDTH = 48.dp

/** Room for "100%" at labelSmall, fixed so every row's percent sits in the same column. */
private val COMPARE_PERCENT_COL = 32.dp
private val COMPARE_LINE_AVATAR = 20.dp

/**
 * The compare bar's columns. Fixed rather than weighted so the lines read as a table: name,
 * then unlocks and points, each starting at the same x on every line. The awards flex in the
 * leftover middle and the rank sits at the line's end, so neither can collide.
 */
private val COMPARE_NAME_COL = 132.dp
// 240, up from 200: "240 pts · 12 achievements" is five characters longer than the "unlocks" it
// replaced (owner request), and in the pixel font - fixed-width glyphs, ~an em per character even
// at its 0.7 scale - the old column cut the word off entirely. Sized for the widest realistic run
// ("8000 pts · 200 achievements", ~227dp in the pixel font) rather than the average one.
private val COMPARE_FACTS_COL = 240.dp

/** Big enough to recognise a face, small enough for three of these to share a handheld's width. */
private val COMPARE_AVATAR = 26.dp

/**
 * What one player's line may claim of the strip before it ellipsizes.
 *
 * Wider than it was (150dp), because the facts line grew from two counters to four and a rank. Two
 * of these still fit a handheld held in landscape side by side, and the third wraps, which is what
 * the row's `FlowRow` is for.
 */
private val COMPARE_TEXT_MAX = 200.dp

/** The game the sweep is currently over, as the chip draws it. */
private class NowPlaying(val title: String, val iconUrl: String?)

/**
 * Enough for a good few words of a title before the ellipsis, and no more of the strip than that.
 *
 * 150dp, not 180: five chips on the end row have to fit two lines of a handheld held in landscape,
 * and the title's own cut ([JourneyStripText.TITLE_MAX]) is what decides the usual width - this is
 * the ceiling for a wide glyph set, and 180 left the arithmetic short of a second row.
 */
private val SPAN_CHIP_TEXT_MAX = 150.dp


/**
 * Play, replay, scrub, share and settings, on one panel across the bottom of the player. The
 * progress read lives here rather than in the player because the slider is the only thing on
 * screen that has to move with every reported value.
 *
 * Two rows, the way a video player lays them out: the scrub gets the full width to itself on top,
 * and the buttons sit under it. On one row the slider was squeezed between six controls into a
 * couple of centimetres, which is neither a usable scrub nor a legible set of buttons. The rows are
 * one focus group (see the caller), so the D-pad walks the scrub and the buttons as a single bar,
 * and one auto-hide covers both.
 *
 * The bottom row carries what playback needs and nothing else. The speed lives in the settings
 * sheet: it is set once for a viewing, while play and replay are pressed over and over, and the
 * bar reads as a set of buttons rather than a strip of controls now that they are apart.
 *
 * And no close. The player has one in the top-right corner that never leaves, which is what a
 * full-screen mode needs; a second one on furniture that fades away was the same action offered
 * twice, in the corner of the bar where a fat thumb finds it by accident.
 */
@Composable
private fun PlayerBar(
    playback: JourneyPlaybackHolder,
    layout: ChartLayout,
    settingsOpen: Boolean,
    legendOpen: Boolean,
    dense: Boolean,
    canToggleDensity: Boolean,
    onToggleDensity: () -> Unit,
    onToggleSettings: () -> Unit,
    onToggleLegend: () -> Unit,
    /** Opens the achievements grid for this game, or null on a journey that has no single game. */
    onOpenGrid: (() -> Unit)?,
    /** The share button, given the bar's per-button size modifier so it shrinks with its row. */
    exportSlot: @Composable (Modifier) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val accent = accentColor()
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = chartPanelColor(scheme.surfaceVariant, scheme.background).copy(alpha = 0.92f),
        modifier = Modifier.fillMaxWidth().padding(12.dp),
    ) {
        Column(
            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            // Whether the sweep is inside a streak run, at the whole-percent cadence everything
            // else on this bar reads playback at. The answer is a walk over a handful of
            // precomputed ranges (see HudAtProgress.inStreak), memoised per step, so a running
            // journey asks it about a hundred times rather than once a frame - and the bar already
            // recomposes at that cadence for the slider itself.
            val step = (playback.progress.coerceIn(0f, 1f) * PROGRESS_STEPS).toInt()
            val burning = remember(layout, step) {
                HudAtProgress.inStreak(layout, step.toFloat() / PROGRESS_STEPS)
            }
            // The same fire the line itself is drawn with while a streak is running, so the two
            // marks are one idea rather than two.
            val track = if (burning) FLAME_OUTER else accent
            // Held to a thin band: a full-height slider on its own row would push the buttons and
            // the chart apart for no more scrub precision than the width already gives.
            Slider(
                value = playback.progress,
                onValueChange = { playback.progress = it },
                valueRange = 0f..1f,
                colors = SliderDefaults.colors(thumbColor = track, activeTrackColor = track),
                modifier = Modifier.fillMaxWidth().height(28.dp).gamepadFocusable(),
            )
            // Sized from what the row actually has: Compose measures a Row's children in order,
            // so when the fixed 48dp buttons overran a portrait phone's width it was the LAST
            // one - Settings - that got handed the sliver left over and arrived squashed (user
            // report). Under the compact rule every button drops to the same smaller size
            // together instead.
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val buttons = if (onOpenGrid != null) 7 else 6
                val buttonMod = if (playerBarCompact(maxWidth.value, buttons)) {
                    Modifier.size(PLAYER_BAR_COMPACT_BUTTON)
                } else {
                    Modifier
                }
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(BAR_BUTTON_GAP_DP.dp),
                ) {
                FocusableIconButton(
                    icon = {
                        Icon(
                            if (playback.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (playback.playing) "Pause" else "Play",
                        )
                    },
                    onClick = { playback.playing = !playback.playing },
                    modifier = buttonMod,
                )
                FocusableIconButton(
                    icon = { Icon(Icons.Filled.Replay, contentDescription = "Replay") },
                    onClick = {
                        playback.progress = 0f
                        playback.playing = true
                    },
                    modifier = buttonMod,
                )
                // Pins the view the automatic rule would not have chosen: the whole journey while
                // it runs, or the wide timeline while it is stopped. The next play takes the
                // decision back.
                FocusableIconButton(
                    icon = {
                        Icon(
                            if (dense) Icons.Filled.Fullscreen else Icons.Filled.FullscreenExit,
                            contentDescription = if (dense) "Wide timeline" else "Fit the whole journey",
                        )
                    },
                    onClick = onToggleDensity,
                    // Disabled means unfocusable too (see FocusableIconButton): a D-pad must not
                    // park on a button that cannot do anything.
                    enabled = canToggleDensity,
                    modifier = buttonMod,
                )
                Spacer(Modifier.weight(1f))
                // The same grid the RA drawer's own button opens, on the journey of the same game:
                // the chart says when the badges landed, the grid says which they were.
                if (onOpenGrid != null) {
                    FocusableIconButton(
                        icon = { Icon(Icons.Filled.ViewModule, contentDescription = "Achievement grid") },
                        onClick = onOpenGrid,
                        modifier = buttonMod,
                    )
                }
                exportSlot(buttonMod)
                // The chart's key, one press away rather than buried under the settings: what a
                // mark means is asked mid-watch, and the sheet it used to ride made the settings
                // a scroll to reach.
                FocusableIconButton(
                    icon = {
                        Icon(
                            Icons.Outlined.Info,
                            contentDescription = if (legendOpen) "Hide legend" else "Legend",
                        )
                    },
                    onClick = onToggleLegend,
                    modifier = buttonMod,
                )
                FocusableIconButton(
                    icon = {
                        Icon(
                            Icons.Filled.Settings,
                            contentDescription = if (settingsOpen) "Hide settings" else "Settings",
                        )
                    },
                    onClick = onToggleSettings,
                    modifier = buttonMod,
                )
                }
            }
        }
    }
}

/**
 * The view settings that are genuinely a matter of taste, reachable without leaving the player:
 * how fast the journey plays, and whether the camera follows the runner. Speed is set once for a
 * viewing rather than pressed repeatedly, which is why it sits here rather than on the bar.
 *
 * The image share used to sit here, which put the two ways of sending a journey on in two different
 * places: one on the bar, one two taps inside a sheet nobody opens for it. Both are now behind the
 * one Share button on the bar (see [ExportControls]).
 *
 */
@Composable
private fun PlayerSettings(
    speed: Float,
    onSetSpeed: (Float) -> Unit,
    followMode: Boolean,
    onSetFollowMode: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    ambience: AmbienceAnimation = AmbienceAnimation.NONE,
    onSetAmbience: (AmbienceAnimation) -> Unit = {},
    exportBackground: ExportBackground = ExportBackground.TITLE,
    onSetExportBackground: (ExportBackground) -> Unit = {},
    backdropDim: Int = 15,
    onSetBackdropDim: (Int) -> Unit = {},
    onPreviewBackdropDim: (Int) -> Unit = {},
    idleSigns: Boolean = true,
    onSetIdleSigns: (Boolean) -> Unit = {},
    rivalColorIndex: Int = 0,
    onSetRivalColor: (Int) -> Unit = {},
    playerColorIndex: Int = -1,
    onSetPlayerColor: (Int) -> Unit = {},
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = chartPanelColor(scheme.surfaceVariant, scheme.background).copy(alpha = 0.94f),
        modifier = modifier.widthIn(max = 460.dp),
    ) {
        // Scrollable, because the sheet is no longer a fixed three rows: the key below adds four
        // rows of its own, and a landscape handheld with the control bar under it does not have
        // room for all of it. Without this the last of the legend is simply cut off the bottom.
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            PlayerSpeedRow(speed, onSetSpeed)
            PlayerSwitchRow("Follow the player", followMode, onSetFollowMode)
            PlayerAmbienceRow(ambience, onSetAmbience)
            ExportBackgroundRow(exportBackground, onSetExportBackground)
            BackdropDimRow(backdropDim, onPreviewBackdropDim, onSetBackdropDim)
            PlayerSwitchRow(IDLE_SIGNS_LABEL, idleSigns, onSetIdleSigns)
            // The colours moved in from the setup panel (UI feedback): they restyle the loaded
            // chart in place, which is a thing to do while looking at the chart.
            // No theme-accent/adaptive swatch any more: the
            // player picks from the same fixed palette the rival does, blue by default.
            ColorSwatchRow(
                label = "Your Color",
                selectedIndex = playerColorIndex,
                onSelect = onSetPlayerColor,
                labelFor = ::playerColorLabel,
            )
            ColorSwatchRow(
                label = "Rival Color",
                selectedIndex = rivalColorIndex,
                onSelect = onSetRivalColor,
                labelFor = ::rivalColorLabel,
            )
        }
    }
}

/**
 * The chart's key on its own sheet, opened from the bar's legend button. It used to ride the
 * bottom of the settings sheet, which made the actual settings a scroll to reach and hid the key
 * behind furniture nobody opens to read.
 */
@Composable
private fun LegendSheet(compare: Boolean, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = chartPanelColor(scheme.surfaceVariant, scheme.background).copy(alpha = 0.94f),
        modifier = modifier.widthIn(max = 460.dp),
    ) {
        // Scrollable for the same reason the settings sheet is: a landscape handheld with the
        // control bar under it does not always have room for the whole key.
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
        ) {
            JourneyLegend(compare)
        }
    }
}

/** The three speeds as chips: all of them on screen at once, rather than a button that walks a ring. */
@Composable
private fun PlayerSpeedRow(speed: Float, onSetSpeed: (Float) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Speed", modifier = Modifier.weight(1f))
        SPEEDS.forEach { option ->
            AccentToggle(
                text = speedLabel(option),
                selected = speedLabel(speed) == speedLabel(option),
                onClick = { onSetSpeed(option) },
                compact = true,
            )
        }
    }
}

@Composable
private fun PlayerSwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val accent = accentColor()
    FocusableRow(onClick = { onChange(!checked) }, modifier = Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = accent,
                checkedTrackColor = accent.copy(alpha = 0.5f),
            ),
        )
    }
}

/**
 * The ambience picker: the ambience animations, on the journey's own stage. A dropdown rather than
 * chips - this many options do not fit a sheet that already holds the legend.
 *
 * Beam is left off this menu (user request): a straight sweeping bar over a chart full of straight
 * lines reads as part of the chart rather than as weather.
 */
@Composable
private fun PlayerAmbienceRow(ambience: AmbienceAnimation, onSet: (AmbienceAnimation) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val accent = accentColor()
    Box {
        FocusableRow(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Ambience", modifier = Modifier.weight(1f))
            Text(ambience.displayName, color = accent, fontWeight = FontWeight.Bold)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            // The options get a bounded scroll area of their own, the same way the game picker's
            // list does: the menu opens from a sheet parked at the bottom of the screen, and the
            // stock menu clipped the tail of the list to the window with no way to scroll it into
            // view - the last animation simply could not be picked.
            Column(
                Modifier
                    .heightIn(max = AMBIENCE_LIST_MAX_HEIGHT)
                    .verticalScroll(rememberScrollState()),
            ) {
                AmbienceAnimation.entries.filter { it != AmbienceAnimation.BEAM }.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.displayName) },
                        onClick = {
                            onSet(option)
                            open = false
                        },
                        modifier = Modifier.gamepadFocusable(),
                    )
                }
            }
        }
    }
}

/**
 * Bounded like the game picker's list, so every option stays reachable on a landscape handheld -
 * and deliberately NOT a whole number of rows.
 *
 * A menu item is 48dp, so the old 240dp fitted exactly five of them and the list looked complete
 * when it was not: nothing on screen said the rest were a scroll away. Half a row
 * short of six leaves the next one visibly cut off, which is the one affordance a scrolling list
 * gets for free.
 */
private val AMBIENCE_LIST_MAX_HEIGHT = 264.dp

/**
 * Which RetroAchievements art the stage rests on - the live player's backdrop and both exports
 * follow the same choice, so a shared file always shows what the watcher saw. The screen's own
 * pick, one of the game's art fields by name, or none at all. See [ExportBackground].
 */
@Composable
private fun ExportBackgroundRow(selected: ExportBackground, onSet: (ExportBackground) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val accent = accentColor()
    Box {
        FocusableRow(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Background", modifier = Modifier.weight(1f))
            Text(selected.label, color = accent, fontWeight = FontWeight.Bold)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            ExportBackground.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        onSet(option)
                        open = false
                    },
                    modifier = Modifier.gamepadFocusable(),
                )
            }
        }
    }
}

/**
 * How far the backdrop art is dimmed, live and in both exports (see [JourneyBackdrop] and
 * `JourneyExportRequest.backdropDim`) - a slider rather than the row above's dropdown, because the
 * useful values here are a continuum rather than a short list of named choices. The drag drives
 * the stage's backdrop LIVE through [onPreview] - a plain in-memory state write, so the image
 * behind the sheet follows the finger - while the DataStore write stays on the release edge
 * ([onSet]), so scrubbing still does not commit a preference on every frame.
 *
 * The row SPEAKS in dim percent (40..95%, higher = darker) while the stored value stays the
 * art's alpha percent (5..60, higher = brighter): "Background dim 15%" over art at 15%
 * visibility would read exactly backwards, so the two are inverted here at the edge and
 * nowhere else.
 */
@Composable
private fun BackdropDimRow(percent: Int, onPreview: (Int) -> Unit, onSet: (Int) -> Unit) {
    val accent = accentColor()
    // The local float owns the thumb during a drag; onPreview echoes back through [percent] as
    // the same rounded int, so syncing from percent only on a real (>= 1%) divergence lets an
    // external restore move the thumb without the echo fighting the finger mid-drag.
    var dim by remember { mutableFloatStateOf((100 - percent).toFloat()) }
    LaunchedEffect(percent) {
        val external = (100 - percent).toFloat()
        if (kotlin.math.abs(external - dim) >= 1f) dim = external
    }
    // One line in the SIBLINGS' own voice and their own indent: FocusableRow pads its
    // content 12dp, so this row does too - "Background dim" starts exactly where
    // "Background" starts above it. No percent readout (owner request, round three): the
    // preview IS the feedback, and the slider runs to the same right edge the sibling rows'
    // values end at.
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Background dim")
        Slider(
            value = dim,
            onValueChange = {
                dim = it
                onPreview(100 - it.toInt())
            },
            onValueChangeFinished = { onSet(100 - dim.toInt()) },
            valueRange = 40f..95f,
            colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent),
            modifier = Modifier.weight(1f).gamepadFocusable(),
        )
    }
}

/** What a colour swatch says out loud, and the string a test finds it by. */
internal fun rivalColorLabel(index: Int, selected: Boolean): String =
    "Rival color ${index + 1}" + if (selected) ", selected" else ""

/** The player's own swatches; -1 is the theme accent and reads as the default. */
internal fun playerColorLabel(index: Int, selected: Boolean): String =
    (if (index < 0) "Your color, theme accent" else "Your color ${index + 1}") +
        if (selected) ", selected" else ""

/**
 * One row of colour swatches from [JourneyPalette.RIVAL_CHOICES]. [defaultSwatch] non-null adds a
 * leading swatch at index -1 in that colour (the theme accent, for the player's own row). Pure
 * presentation: a pick restyles the loaded chart in place and no Load is owed.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ColorSwatchRow(
    label: String,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    labelFor: (Int, Boolean) -> String,
    defaultSwatch: Color? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(SWATCH_LABEL_COL),
        )
        val swatchIsDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
        val choices = buildList {
            if (defaultSwatch != null) add(-1 to defaultSwatch)
            JourneyPalette.RIVAL_CHOICES.indices.forEach { i ->
                add(i to JourneyPalette.choiceColor(i, swatchIsDark))
            }
        }
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
        choices.forEach { (index, color) ->
            val selected = index == selectedIndex
            Box(
                Modifier
                    .size(RIVAL_SWATCH)
                    .clip(CircleShape)
                    .then(
                        // The theme-accent entry wears the same rainbow "adaptive" face the guide
                        // appearance sheet leads its accent row with, instead of a
                        // solid circle that could collide exactly with one of the fixed choices
                        // beside it and read as a duplicate.
                        if (index < 0) Modifier.background(Brush.sweepGradient(ADAPTIVE_SWEEP))
                        else Modifier.background(color),
                    )
                    .border(
                        width = if (selected) 2.dp else 0.dp,
                        color = if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                        shape = CircleShape,
                    )
                    .clickable { onSelect(index) }
                    .gamepadFocusable()
                    .semantics { contentDescription = labelFor(index, selected) },
                contentAlignment = Alignment.Center,
            ) {
                if (index < 0) {
                    Icon(
                        Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(RIVAL_SWATCH / 2),
                    )
                }
            }
        }
        } // end FlowRow (wraps all nine swatches on a phone-width sheet)
    }
}

/**
 * The rainbow the adaptive/default swatch wears: the exact stops the guide viewer's Display sheet
 * uses for its own, crimson repeated at the end so the sweep has no seam.
 */
private val ADAPTIVE_SWEEP = listOf(
    Color(0xFFDC143C), Color(0xFFFFD700), Color(0xFF00C853),
    Color(0xFF00D4FF), Color(0xFF7C4DFF), Color(0xFFDC143C),
)

private val RIVAL_SWATCH = 22.dp
private val SWATCH_LABEL_COL = 80.dp

/**
 * What the idle-sign switch is called: the small picture a player's face carries while their line
 * is flat (asleep, at work, out for the evening). See [com.twinlore.app.journey.domain.IdleSigns].
 *
 * The one decorated label in the sheet, and deliberately so. Every other row here says what it
 * changes in its own name ("Speed", "Follow the player", "Background"); this one cannot without a
 * sentence, so it borrows the feature's own vocabulary instead - the glyph is a sample of what the
 * switch draws. Static rather than cycling through the pool: a settings label that changes while
 * nobody is touching it reads as a bug.
 */
internal const val IDLE_SIGNS_LABEL = "Life happens 💤"

/** Every speed the player offers, in the order the settings sheet lays them out. */
internal val SPEEDS = listOf(0.5f, 1f, 2f)

internal fun speedLabel(speed: Float): String = when {
    speed < 0.75f -> ".5x"
    speed < 1.5f -> "1x"
    else -> "2x"
}
