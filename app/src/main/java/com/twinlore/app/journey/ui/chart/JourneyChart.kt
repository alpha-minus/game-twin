package com.twinlore.app.journey.ui.chart

import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.twinlore.app.journey.domain.ChartLayout
import com.twinlore.app.journey.domain.LayoutEngine
import com.twinlore.app.journey.domain.TickGenerator
import com.twinlore.app.journey.ui.JourneyUiState
import com.twinlore.app.ui.utils.gamepadFocusable
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Where the end-of-journey glide takes over: past this progress a follow view is walked out to
 * the fitted overview instead of cutting to it. The same threshold the player calls the end state.
 */
private const val END_GLIDE_AT = 0.999f
private const val TAP_RADIUS = 24f

/**
 * The text measurer's LRU. A frame measures the grid labels, every tick, the type glyphs and the
 * tooltip, and during a dense reveal every distinct floating "+N" on screen joins them - a
 * ten-year span popping badges in clusters held more distinct strings than the old 96, and a
 * cache one entry too small re-lays out ALL of them every vsync.
 */
private const val TEXT_CACHE_SIZE = 192

/** The mark drawn over the empty stage while the badge images are still being decoded. */
private val DECODE_RING_SIZE = 28.dp
private val DECODE_RING_STROKE = 2.dp
private const val DECODE_RING_ALPHA = 0.5f
private const val DECODE_RING_SWEEP = 270f
private const val DECODE_RING_PERIOD_MILLIS = 1_000

/**
 * The achievement journey chart: curves, badge nodes, medals and axis ticks laid out in [layout],
 * scrubbed by [progress] and animated by [playing].
 *
 * [progress] is a lambda rather than a value so the read of it happens *here* and not in the shell
 * that hands it over. The shell reports about seven values a second while a journey runs, and a
 * read in its scope re-executes the whole player - backdrop, overview strip, every chip and this
 * chart's entire modifier chain - on each one. See [com.twinlore.app.journey.ui.JourneyPlaybackHolder].
 *
 * [layout] and its [paths] are passed in rather than read off [ready] because the player swaps
 * between two of them: the wide timeline while the journey runs, and the one-screenful compact
 * chart whenever it is not. Both are built once by the caller and handed over here, so a swap costs
 * no path measuring at all. Everything derived from the chart - the reveal, the sweep, the cursor
 * stops, the follow camera, the hit testing - reads whichever one is active, so the two can never
 * disagree about where the journey has got to.
 *
 * Playback is one [androidx.compose.animation.core.Animatable] read in the draw phase, so a running
 * journey costs a redraw per frame and nothing else; paused, finished or reduced-motion playback
 * costs nothing at all (the animation coroutine is cancelled, and there is no infinite transition
 * anywhere in this file). The reveal effects ride the same budget: a single [withFrameNanos] loop
 * keeps frames coming while an effect is in flight and stops the moment none is.
 *
 * The whole chart is a single focusable, single-semantics node: badge details are reached with
 * the D-pad cursor rather than by giving every badge its own composable, because accessibility
 * services on this handheld scale with tree size. Pinch, drag and double tap zoom into the same
 * virtual space the renderer draws in, so a magnified chart is re-rasterised rather than blown up.
 *
 * [bitmaps] (null while they are still decoding) is hoisted to the screen: the video export draws
 * through the same renderer and must reuse the images already in memory rather than fetch its own.
 *
 * [followMode] hands the view to [FollowCamera]: the camera is derived inside the draw pass from
 * the same progress the renderer reads there, so following the runner costs a redraw and never a
 * recomposition. A pinch or a drag takes the view back until playback is started again.
 *
 * [showChartChrome] draws the chart's own caption and running date inside the canvas. The
 * full-screen player passes false: its overview strip sits in that same top band and says the same
 * things, and the two on top of each other were unreadable.
 *
 * [onTap] reports what every tap the chart handled turned out to be about, which is how the
 * full-screen player tells "show me what that badge is" and "close that tooltip" from "get the
 * furniture out of my way": only a [ChartTapOutcome.MISSED] toggles the control bar.
 *
 */
@Composable
fun JourneyChart(
    ready: JourneyUiState.Ready,
    layout: ChartLayout,
    paths: ChartPaths,
    userName: String,
    progress: () -> Float,
    playing: Boolean,
    onProgressChange: (Float) -> Unit,
    onTogglePlay: () -> Unit,
    bitmaps: Map<String, ImageBitmap>?,
    modifier: Modifier = Modifier,
    style: ChartStyle,
    speed: Float = 1f,
    followMode: Boolean = false,
    showChartChrome: Boolean = true,
    /** Whether a flat line carries a sign over a player's face; see [JourneyChartRenderer]. */
    showIdleSigns: Boolean = true,
    onTap: (ChartTapOutcome) -> Unit = {},
    /** Lets the player's Back dismiss an open tooltip before it closes the player. */
    cursorHandle: ChartCursorHandle? = null,
) {
    val context = LocalContext.current
    // A generous cache: a frame measures the grid labels, every tick, the medal
    // captions, the type glyphs and the tooltip, which blows straight through the
    // default LRU of 8 and would re-lay out text on every vsync.
    val textMeasurer = rememberTextMeasurer(cacheSize = TEXT_CACHE_SIZE)
    val haptics = LocalHapticFeedback.current

    val reducedMotion = remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }

    // Read without observation on purpose. This is a starting value, not a subscription: a read
    // taken here would be recorded against this composable's scope and put the whole chart back on
    // the shell's progress, which is precisely what [progress] being a lambda exists to prevent.
    val controller = remember(ready) {
        JourneyPlaybackController(Snapshot.withoutReadObservation { progress() })
    }
    // The cursor is keyed on the layout as well: the two charts draw different subsets of the same
    // journey, so a tooltip carried across a swap could be pointing at an icon that is no longer
    // on the stage.
    val cursorState = remember(ready, layout) { mutableStateOf<ChartCursor?>(null) }
    var cursor by cursorState
    if (cursorHandle != null) {
        // Re-bound on every composition rather than once: the state object above is rebuilt
        // whenever the journey or the layout changes, and a handle still pointing at the old one
        // would be clearing a tooltip nobody can see.
        SideEffect { cursorHandle.bind(cursorState) }
        DisposableEffect(cursorHandle) { onDispose { cursorHandle.unbind() } }
    }
    var focused by remember { mutableStateOf(false) }
    // Keyed on the pop epoch so a replay lets every badge pop in and every award fire again, and
    // on the layout because the clock remembers effects by icon index: the two charts draw
    // different subsets of the journey, so an index means a different badge on each of them.
    val clock = remember(ready, controller.popEpoch, layout) { JourneyEffectClock() }
    // The settled-icon layer, one per everything that can change what a settled icon looks like:
    // the journey, the layout it is drawn on, the theme, the decoded art, and the pop epoch (a
    // replay or a backwards scrub un-settles icons, so the clock's key doubles as this one's).
    val iconLayer = remember(ready, layout, controller.popEpoch, style, bitmaps) {
        IconLayer(layout.nodes.size, layout.medals.size)
    }
    var autoStarted by remember(ready) { mutableStateOf(false) }
    var zoom by remember(ready, layout) { mutableStateOf(ChartZoom.overviewFor(layout.width)) }
    // The fitted whole-picture view, hoisted: the draw pass compares against it every frame.
    val overviewView = remember(ready, layout) { ChartZoom.overviewFor(layout.width) }

    // The follow camera's view is deliberately NOT Compose state: it is written once per frame
    // inside the draw pass, and a state write there would either be illegal or would recompose the
    // screen sixty times a second. The gestures read it back as "where the view actually is".
    val camera = remember(ready, layout) { CameraHolder(layout.width) }
    // A pinch or a drag is the user taking the camera off the runner; playback, or switching follow
    // back on, hands it over again.
    var followSuspended by remember(ready, layout) { mutableStateOf(false) }
    LaunchedEffect(playing) { if (playing) followSuspended = false }
    LaunchedEffect(followMode) { if (followMode) followSuspended = false }
    // Gated on playback: see [FollowCamera.engaged]. A stopped chart is always the fitted view of
    // whichever layout is active, which is what makes the compact chart at rest actually compact.
    //
    // Read through rememberUpdatedState rather than off the parameter: the pinch and drag handlers
    // are keyed on the layout and the follow setting, so the closure they captured outlives a
    // play/pause edge, and a gesture that started from the wrong base view is a chart that jumps
    // under the fingers. The delegate is read live; the raw parameter would be whatever it was when
    // that pointerInput was last keyed.
    val livePlaying by rememberUpdatedState(playing)
    val following = { FollowCamera.engaged(followMode, followSuspended, livePlaying) }

    // Callbacks are re-created by the shell on every recomposition; the long-lived effects below
    // must always call the current ones rather than the ones captured when they launched.
    val currentOnProgressChange by rememberUpdatedState(onProgressChange)
    val currentOnTogglePlay by rememberUpdatedState(onTogglePlay)
    val currentOnTap by rememberUpdatedState(onTap)
    val reportProgress: (Float) -> Unit = { value -> currentOnProgressChange(value) }

    // The shell owns progress (the scrub slider lives there); mirror its scrubs into the
    // animation. The controller ignores the echo of values it reported itself.
    //
    // The read happens inside the snapshotFlow, which is the whole point: progress moves about
    // seven times a second, and taking it in a composable's own scope would re-execute that
    // composable (and everything under it) on each one. Here it wakes a coroutine and nothing else.
    //
    // The effect deliberately does NOT key on [progress] and does not read it through
    // rememberUpdatedState, so the lambda it captures is whichever instance was current when the
    // effect launched. That is safe only because of an invariant the shell must keep: [progress] is
    // remembered on the same playback holder this composable is keyed on, so a stale instance reads
    // exactly the same state as a fresh one. If the shell ever passes an unremembered lambda, or
    // one that closes over a *different* holder per ready state, this stops mirroring scrubs.
    LaunchedEffect(ready) {
        snapshotFlow { progress() }.distinctUntilChanged().collect { controller.scrubTo(it) }
    }

    // A replay starts the journey over; the zoomed-in view belongs to the chart the user was
    // exploring, not to the one about to be drawn from scratch. Keyed on the replay epoch rather
    // than the pop epoch: a scrub to the left also replays the badge pop-in, and having the view
    // jump out to 1x every time the slider is dragged backwards is not the same idea at all.
    LaunchedEffect(controller.replayEpoch, layout) {
        zoom = ChartZoom.overviewFor(layout.width)
        camera.reset()
        camera.resetFollowZoom()
    }

    // Reduced motion: no reveal at all, the finished chart appears immediately.
    LaunchedEffect(ready, reducedMotion) {
        if (reducedMotion) controller.jumpToEnd(reportProgress)
    }

    // Autoplay once the images are in, so the journey plays itself rather than snapping to a
    // half-drawn chart while badges are still decoding.
    LaunchedEffect(ready, bitmaps, reducedMotion) {
        if (bitmaps == null || reducedMotion || autoStarted) return@LaunchedEffect
        autoStarted = true
        if (!playing) {
            controller.restart(reportProgress)
            currentOnTogglePlay()
        }
    }

    // The one owner of playback. Cancelling this coroutine (pause, or leaving the screen) is the
    // only thing that stops the reveal: scrubbing and replaying interrupt the animation, and the
    // controller resumes through that rather than dying with it.
    LaunchedEffect(playing, speed, bitmaps, reducedMotion, layout.width) {
        if (!playing || bitmaps == null) return@LaunchedEffect
        if (reducedMotion) {
            controller.jumpToEnd(reportProgress)
            currentOnTogglePlay()
            return@LaunchedEffect
        }
        // A wide timeline gets proportionally longer to cross, so the sweep travels at the same
        // pace whatever stage the journey earned; a years-long calendar slows the pass further so
        // the dates do not spin. See [journeyPlaybackMillis]. A race chart's axis is elapsed time,
        // not calendar - zero leaves its pace alone.
        val finished = controller.play(
            speed = speed,
            totalMillis = journeyPlaybackMillis(
                layoutWidth = layout.width,
                axisSpanMillis = if (layout.raceMode) 0L else layout.axisT1 - layout.axisT0,
            ),
            onProgress = reportProgress,
        )
        if (finished) currentOnTogglePlay()
    }

    // The reveal effects' frame pump. Playback's own Animatable already invalidates draw on every
    // vsync while it runs; this exists for the tail, where the last award lands as playback ends
    // and its flash would otherwise freeze mid-flare. It burns no frames at all once the last
    // effect has expired, and never starts if there is nothing pending.
    var effectTick by remember(clock) { mutableLongStateOf(0L) }
    LaunchedEffect(clock, playing, bitmaps, reducedMotion) {
        if (reducedMotion) return@LaunchedEffect
        // bitmaps == null is the cold-cache decode window: the shell has already flipped playing
        // to true, but the reveal has not started and there is nothing to animate. Pumping frames
        // through it is pure waste on a device whose idle floor is already the problem.
        val revealing = playing && bitmaps != null
        if (!revealing && !clock.pendingAt(System.nanoTime())) return@LaunchedEffect
        while (true) {
            val now = withFrameNanos { it }
            effectTick = now
            if (!revealing && !clock.pendingAt(now)) return@LaunchedEffect
        }
    }

    // The end glide's own frame pump. The glide runs in the draw phase and needs a redraw per
    // step, but by the time it starts the reveal pump has usually gone quiet (a window axis that
    // outlives the last unlock leaves seconds of bare curve at the end, long past every effect) -
    // so without this the camera got one lerp step and froze on a magnified slice. Derived from
    // the playback holder's own state, so a scrub to the end while paused arms it too, and it
    // stops the moment the camera lands (or the user takes it: a pan suspends).
    val endGlideWanted by remember(layout) {
        derivedStateOf { !livePlaying && progress() >= END_GLIDE_AT }
    }
    LaunchedEffect(endGlideWanted, followSuspended, layout) {
        if (!endGlideWanted || followSuspended) return@LaunchedEffect
        while (!FollowCamera.arrived(camera.view, overviewView)) {
            effectTick = withFrameNanos { it }
        }
    }

    val summary = remember(ready) { summaryOf(ready) }

    Box(
        modifier
            .semantics(mergeDescendants = true) { contentDescription = summary }
            .gamepadFocusable()
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionLeft, Key.DirectionRight -> {
                        // Both sets are what is actually painted at this instant: the D-pad must
                        // not walk onto an icon that has not landed yet, and panIntoView below
                        // would otherwise carry the camera off to a blank patch of stage.
                        val stops = mergeCursorStops(
                            layout,
                            JourneyChartRenderer.visibleNodeIndices(layout, paths, controller.progress),
                            JourneyChartRenderer.drawnMedalIndices(layout, paths, controller.progress),
                        )
                        val current = cursor
                        val next = nextCursorStop(stops, current, event.key == Key.DirectionRight)
                        if (next != null && next != current) {
                            cursor = next
                            // Stepping onto an icon the zoom has pushed off screen brings it back.
                            // While following, the camera owns the view and will bring the runner
                            // (and the icon it is passing) back on its own.
                            if (!following()) {
                                next.drawnAt(layout)?.let { zoom = zoom.panIntoView(it) }
                            }
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            true
                        } else {
                            false
                        }
                    }
                    Key.ButtonA, Key.Enter, Key.DirectionCenter -> {
                        currentOnTogglePlay()
                        true
                    }
                    else -> false
                }
            }
            .pointerInput(layout, paths) {
                detectTapGestures(
                    onDoubleTap = { tap ->
                        val stage = toStage(tap, size.width.toFloat(), size.height.toFloat())
                        val view = if (following()) camera.view else zoom
                        zoom = if (view.isOverview) {
                            view.zoomedTo(ChartZoom.DOUBLE_TAP, stage)
                        } else {
                            view.overview()
                        }
                        // Framing the chart by hand is the user taking the camera over.
                        followSuspended = true
                        // The view just moved out from under it; a tooltip left pointing at the
                        // old framing is worse than none.
                        cursor = null
                    },
                    onTap = { tap ->
                        // A tap only asks what an icon is, so it reads the view rather than
                        // claiming it: the camera keeps following through a tooltip.
                        val view = if (following()) camera.view else zoom
                        val virtual = view.toVirtual(
                            toStage(tap, size.width.toFloat(), size.height.toFloat()),
                        )
                        // Divide the hit radius by the zoom so a magnified chart does not also
                        // magnify how far away a tap is allowed to land.
                        val radius = TAP_RADIUS / view.zoom
                        val node = JourneyChartRenderer.nodeAt(
                            layout, paths, controller.progress, virtual.x, virtual.y, radius,
                        )
                        val hit = when {
                            node != null -> ChartCursor.Node(node)
                            else -> JourneyChartRenderer.medalAt(
                                layout, paths, controller.progress, virtual.x, virtual.y, radius,
                            )?.let { ChartCursor.Medal(it) }
                        }
                        // Read before the write: what the tap was about depends on whether there
                        // was a tooltip open for it to close.
                        val outcome = tapOutcome(cursor, hit)
                        cursor = hit
                        currentOnTap(outcome)
                    },
                )
            }
            .pointerInput(layout, followMode) {
                // Pinch only: this must never swallow a one-finger drag, because the journey
                // screen scrolls vertically and the chart is 320dp of it.
                detectPinch { centroid, pan, gestureZoom ->
                    val fit = fitScale(size.width.toFloat(), size.height.toFloat())
                    if (fit <= 0f) return@detectPinch
                    val stage = toStage(centroid, size.width.toFloat(), size.height.toFloat())
                    if (following()) {
                        // A pinch during the follow RE-TUNES it (UI feedback): the camera keeps
                        // chasing the runner, at the new magnification. Pinching out past the
                        // exit threshold is still the way to ask for the whole picture - that
                        // takes the camera with it, exactly as it always did.
                        val tuned = (camera.followZoom * gestureZoom)
                            .coerceIn(FollowCamera.EXIT_ZOOM, ChartZoom.MAX)
                        if (tuned <= FollowCamera.EXIT_ZOOM + 0.001f && gestureZoom < 1f) {
                            // Straight to the whole picture, on every chart. Handing the exit to
                            // pinched() got there in one step on a dense chart (its floor is a
                            // whisker under the exit zoom) and left a WIDE chart parked at 1.3x -
                            // its floor is a quarter of that, so the same gesture needed several
                            // more full pinches and read as zoom-out simply not working.
                            zoom = camera.view.overview()
                            followSuspended = true
                        } else {
                            camera.followZoom = tuned
                        }
                        return@detectPinch
                    }
                    // Pinching back IN from the whole picture with follow still switched on is
                    // the user asking for the chase back (owner report: a full zoom-out during a
                    // follow left zooming-in stranded in manual framing, with no way back short
                    // of toggling follow or pause). Re-engage at the standard follow zoom - the
                    // exit pinch parked followZoom at the exit threshold, and resuming THERE
                    // would disengage again on the next breath of a pinch-out.
                    if (followMode && livePlaying && gestureZoom > 1f && camera.view.isOverview) {
                        camera.followZoom = FollowCamera.ZOOM
                        followSuspended = false
                        return@detectPinch
                    }
                    // Taking over by hand starts where the view actually is - the draw pass keeps
                    // camera.view current in every branch - so the chart cannot snap under the
                    // fingers, paused mid-follow included.
                    followSuspended = true
                    zoom = camera.view.pinched(
                        focus = stage,
                        gestureZoom = gestureZoom,
                        pan = pan / fit,
                        followMode = followMode,
                    )
                }
            }
            .pointerInput(layout, followMode) {
                // One-finger pan. Whether there is anything to pan around is read inside the
                // gesture, never in the key: the first drag write changes the zoom, and a key that
                // moved with it would tear this pointerInput down in the middle of the gesture it
                // was handling, so a pan lasted exactly one frame. (The live chart is full screen
                // now, so this no longer has to leave one-finger drags to a scrolling page.)
                detectDragGestures { change, drag ->
                    val base = camera.view
                    val fit = fitScale(size.width.toFloat(), size.height.toFloat())
                    if (fit > 0f && !base.isOverview) {
                        // Taking the camera stops the show: a pan against a running follow was
                        // two hands on one wheel, the glide dragging the picture back while the
                        // finger pulled it away. Play re-engages the follow (see the effect on
                        // [playing] above), so the pause is one press deep.
                        if (following()) currentOnTogglePlay()
                        zoom = base.pannedBy(drag / fit)
                        followSuspended = true
                    }
                    change.consume()
                }
            },
    ) {
        val images = bitmaps
        Canvas(Modifier.fillMaxSize()) {
            val value = controller.progress
            val running = playing && !reducedMotion
            // effectTick is read purely to keep the draw phase invalidating while effects run; the
            // frame clock and System.nanoTime() share a base, so the max of the two is just "now".
            val now = max(effectTick, System.nanoTime())
            // The camera is resolved here, in the draw phase, from the progress the renderer is
            // about to draw with. Paused or scrubbed, it snaps (the view has to track the slider
            // exactly); playing, it glides, and the glide is measured against how long the frame
            // actually took so a chart managing 30fps does not also glide at half speed.
            val view = if (following()) {
                val runner = paths.positionAt(
                    ChartPaths.MAIN,
                    JourneyChartRenderer.revealedLength(layout, paths, value),
                )
                FollowCamera.step(
                    current = camera.view,
                    target = FollowCamera.target(runner, layout.width, camera.followZoom),
                    factor = if (running) FollowCamera.factorFor(camera.frameSeconds(now)) else 1f,
                ).also { camera.view = it }
            } else if (
                value >= END_GLIDE_AT && !followSuspended &&
                !FollowCamera.arrived(camera.view, overviewView)
            ) {
                // The journey has landed: the camera lets the runner go and GLIDES out to the
                // whole picture instead of cutting to it. It hands the view over the moment it
                // arrives - or the moment the user takes it (a pan suspends, as always).
                FollowCamera.glide(
                    camera.view, overviewView,
                    FollowCamera.factorFor(camera.frameSeconds(now)),
                ).also { camera.view = it }
            } else if (
                followMode && !followSuspended && !livePlaying &&
                !camera.view.isOverview && value < END_GLIDE_AT
            ) {
                // Paused mid-follow: the view stays at the runner's side, still snap-tracking a
                // scrub, instead of cutting out to the overview on every pause and back in on
                // every resume.
                val runner = paths.positionAt(
                    ChartPaths.MAIN,
                    JourneyChartRenderer.revealedLength(layout, paths, value),
                )
                FollowCamera.step(camera.view, FollowCamera.target(runner, layout.width, camera.followZoom), 1f)
                    .also { camera.view = it }
            } else {
                camera.reset()
                // A landed, unsuspended journey rests on the overview the glide just arrived at -
                // NOT on the stale manual zoom from before the follow took over. Restoring that
                // zoomed view here re-armed the glide, which re-arrived, which restored it again:
                // the "zooms out multiple times" loop. A pinch after landing suspends and writes
                // [zoom] fresh, so the user can still take the end state anywhere they like.
                val rest = if (value >= END_GLIDE_AT && !followSuspended) overviewView else zoom
                rest.also { camera.view = it }
            }
            // Captured out here, where `size` is unambiguously the canvas: the cull rect has to be
            // derived from the same letterboxing withStage is about to apply, and a portrait canvas
            // shows most of a stage's height again above and below the fitted chart.
            val canvasSize = size
            // The settled-icon layer only engages at the overview, the one view whose transform
            // holds still frame to frame; ensure() clears it if the canvas or transform moved.
            // The k/tx/ty here MUST mirror withStage's transform composition exactly.
            val stageFit = fitScale(canvasSize.width, canvasSize.height)
            val settledLayer = if (view.isOverview && stageFit > 0f) {
                iconLayer.takeIf {
                    it.ensure(
                        widthPx = canvasSize.width.toInt(),
                        heightPx = canvasSize.height.toInt(),
                        k = stageFit * view.zoom,
                        tx = (canvasSize.width - LayoutEngine.W * stageFit) / 2f + stageFit * view.offset.x,
                        ty = (canvasSize.height - LayoutEngine.H * stageFit) / 2f + stageFit * view.offset.y,
                    )
                }
            } else {
                null
            }
            withStage(view, LayoutEngine.W) { drawScale ->
                with(JourneyChartRenderer) {
                    drawJourney(
                        layout = layout,
                        paths = paths,
                        frame = ready.frame,
                        style = style,
                        textMeasurer = textMeasurer,
                        bitmaps = images ?: emptyMap(),
                        userName = userName,
                        progress = value,
                        cursorNodeIndex = (cursor as? ChartCursor.Node)?.index,
                        nodeAppearProgress = { index ->
                            if (!running) 1f else JourneyChartRenderer.popProgress(clock.nodeSeconds(index, now, true))
                        },
                        focused = focused,
                        scale = drawScale,
                        cursorMedalIndex = (cursor as? ChartCursor.Medal)?.index,
                        showChartChrome = showChartChrome,
                        showIdleSigns = showIdleSigns,
                        // The "+N" rides its own throttled clock entry, not the pop-in's - see
                        // [JourneyEffectClock.nodePointsSeconds].
                        nodeEffectSeconds = { index -> clock.nodePointsSeconds(index, now, running) },
                        medalEffectSeconds = { index -> clock.medalSeconds(index, now, running) },
                        // The kind decides whether this medal is judged against the shared cooldown
                        // or its own mastery-only one - see [JourneyEffectClock.medalWashSeconds].
                        medalWashSeconds = { index ->
                            clock.medalWashSeconds(index, layout.medals[index].medal.kind, now, running)
                        },
                        view = view,
                        canvasSize = canvasSize,
                        iconLayer = settledLayer,
                    )
                }
            }
        }
        if (images == null) {
            // A spinning arc, but still not Material's indeterminate indicator. The transition's
            // per-vsync wakeups are the ones the effect pump above refuses to make at rest - here
            // they are the only wakeups being made at all (nothing plays before the bitmaps land),
            // and the whole node leaves composition with the decode, taking the transition and its
            // wakeups with it. A ring drawn once instead read on the device as the player freezing.
            val spin = rememberInfiniteTransition(label = "decodeRing")
            val angle by spin.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    tween(durationMillis = DECODE_RING_PERIOD_MILLIS, easing = LinearEasing),
                ),
                label = "decodeRingAngle",
            )
            Canvas(
                Modifier
                    .align(Alignment.Center)
                    .size(DECODE_RING_SIZE)
                    .testTag("decodeRing"),
            ) {
                val stroke = DECODE_RING_STROKE.toPx()
                drawArc(
                    color = style.accent,
                    startAngle = angle,
                    sweepAngle = DECODE_RING_SWEEP,
                    useCenter = false,
                    topLeft = Offset(stroke / 2f, stroke / 2f),
                    size = Size(size.width - stroke, size.height - stroke),
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                    alpha = DECODE_RING_ALPHA,
                )
            }
        }
    }
}

/**
 * The follow camera's current view. A plain holder rather than Compose state because it is written
 * inside the draw pass: it must not invalidate composition, and the gesture handlers need to read
 * back where the camera actually left the view before they take it over.
 */
private class CameraHolder(private val contentWidth: Float) {
    var view: ChartZoom = ChartZoom.overviewFor(contentWidth)

    /**
     * The magnification the follow camera chases the runner at. [FollowCamera.ZOOM] until a pinch
     * during the follow re-tunes it: zooming does not take the camera away any more, it adjusts
     * the ride (UI feedback). Plain like [view]: written in a gesture, read in the draw pass.
     */
    var followZoom: Float = FollowCamera.ZOOM
    private var lastFrameNanos = 0L

    /**
     * How long the frame being drawn took, in seconds, clamped to something a glide can be
     * measured against: the first frame after a pause or a replay has no meaningful length, and
     * the camera should arrive on it rather than crawl or teleport.
     */
    fun frameSeconds(nowNanos: Long): Float {
        val previous = lastFrameNanos
        lastFrameNanos = nowNanos
        if (previous <= 0L || nowNanos <= previous) return 1f / FollowCamera.REFERENCE_FPS
        return ((nowNanos - previous) / 1_000_000_000.0).toFloat().coerceIn(0f, MAX_FRAME_SECONDS)
    }

    /** Back to the fitted view, with no frame history for the next glide to measure against. */
    /** Back to the fitted view. [followZoom] deliberately survives: the rest branch calls this
     *  every frame, and a pause-by-panning that wiped the pinch-tuned magnification while a
     *  pause-by-button kept it was two gestures with two different memories. [resetFollowZoom]
     *  is the replay's. */
    fun reset() {
        view = ChartZoom.overviewFor(contentWidth)
        lastFrameNanos = 0L
    }

    fun resetFollowZoom() {
        followZoom = FollowCamera.ZOOM
    }

    private companion object {
        /** Longer than this is a stall, not a frame; the glide treats it as arriving. */
        const val MAX_FRAME_SECONDS = 0.25f
    }
}

/**
 * Letterboxes the 1060x600 stage into this canvas, applies [view], and hands the block the scale
 * the renderer should size its hairlines against. Shared by the live chart and the preview so the
 * two can never disagree about where the stage sits.
 */
private inline fun DrawScope.withStage(
    view: ChartZoom,
    stageWidth: Float,
    block: DrawScope.(drawScale: Float) -> Unit,
) {
    val scale = fitScale(size.width, size.height, stageWidth)
    if (scale <= 0f) return
    val dx = (size.width - stageWidth * scale) / 2f
    val dy = (size.height - LayoutEngine.H * scale) / 2f
    withTransform({
        translate(dx, dy)
        scale(scale, scale, pivot = Offset.Zero)
        translate(view.offset.x, view.offset.y)
        scale(view.zoom, view.zoom, pivot = Offset.Zero)
    }) {
        block(scale * view.zoom)
    }
}

/**
 * A pinch detector that only ever consumes multi-touch. [detectTransformGestures] consumes any
 * moved pointer once it is past touch slop, one finger included, which would have made the chart a
 * dead zone in the middle of a scrolling screen.
 */
private suspend fun PointerInputScope.detectPinch(
    onPinch: (centroid: Offset, pan: Offset, zoom: Float) -> Unit,
) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        do {
            val event = awaitPointerEvent()
            val down = event.changes.count { it.pressed }
            if (down >= 2) {
                val gestureZoom = event.calculateZoom()
                val pan = event.calculatePan()
                if (gestureZoom != 1f || pan != Offset.Zero) {
                    onPinch(event.calculateCentroid(useCurrent = false), pan, gestureZoom)
                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                }
            }
        } while (event.changes.any { it.pressed })
    }
}

/**
 * The letterbox scale for a [stageWidth]x[LayoutEngine.H] stage on this canvas.
 *
 * The live chart always passes the unstretched [LayoutEngine.W], never the layout's own width: a
 * wide chart must overflow the viewport (that is what the extra timeline is *for*), and fitting its
 * full width in would shrink every icon by the factor the widening just earned them. The still
 * preview, which has no way to pan, passes the layout width instead and shows the whole thing.
 */
private fun fitScale(width: Float, height: Float, stageWidth: Float = LayoutEngine.W): Float =
    if (width <= 0f || height <= 0f) 0f
    else min(width / stageWidth, height / LayoutEngine.H)

/** Screen point to the letterboxed 1060x600 viewport, before the zoom transform is undone. */
private fun toStage(point: Offset, width: Float, height: Float): Offset {
    val scale = fitScale(width, height)
    if (scale <= 0f) return Offset.Zero
    val dx = (width - LayoutEngine.W * scale) / 2f
    val dy = (height - LayoutEngine.H * scale) / 2f
    return Offset((point.x - dx) / scale, (point.y - dy) / scale)
}

private fun summaryOf(ready: JourneyUiState.Ready): String {
    val layout = ready.layout
    val unlocks = ready.frame.main.unlocks
    val points = unlocks.sumOf { it.points }
    val achievements = if (unlocks.size == 1) "1 achievement" else "${unlocks.size} achievements"
    val range = if (layout.raceMode) {
        "over ${TickGenerator.fmtElapsedApprox(layout.eMaxMillis)}"
    } else {
        "between ${TickGenerator.fmtDate(layout.axisT0)} and ${TickGenerator.fmtDate(layout.axisT1)}"
    }
    return "Journey chart. $achievements, $points points $range."
}
