package com.twinlore.app.journey.export

import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.twinlore.app.journey.domain.ChartLayout
import com.twinlore.app.journey.domain.LayoutEngine
import com.twinlore.app.journey.domain.model.AwardTally
import com.twinlore.app.data.ambience.AmbienceAnimation
import com.twinlore.app.journey.domain.model.JourneyFrame
import com.twinlore.app.journey.domain.splitsForRivals
import com.twinlore.app.ui.ambience.AmbiencePainter
import androidx.compose.ui.graphics.Color
import com.twinlore.app.journey.export.ExportBackdrop.drawBackdropArt
import com.twinlore.app.journey.export.ExportBackdrop.drawScaledBackdropArt
import com.twinlore.app.journey.export.ExportStrip.drawExportStrip
import com.twinlore.app.journey.domain.model.JourneyMode
import com.twinlore.app.journey.ui.BackdropLogic
import com.twinlore.app.journey.ui.HudAtProgress
import com.twinlore.app.journey.ui.JourneyStripText
import com.twinlore.app.journey.ui.JourneyUrls
import com.twinlore.app.journey.ui.chart.ChartPaths
import com.twinlore.app.journey.ui.chart.ChartStyle
import com.twinlore.app.journey.ui.chart.ChartZoom
import com.twinlore.app.journey.ui.chart.JourneyChartRenderer
import com.twinlore.app.journey.ui.colorForIndex
import kotlin.math.min

/**
 * One exported frame: background, the game art behind it, the chart fitted and letterboxed into
 * 1920x1080, and the overview strip above it.
 *
 * Shared by the video and the still image rather than written twice, because "the same letterbox"
 * and "the same strip" are the whole promise of the image export - a shared picture has to be the
 * frame the video would have shown at that moment. The two differ in where the pixels go (the video
 * locks an encoder surface per frame, the image renders one bitmap) and in two things about the
 * picture itself, both of which come down to a video being watched and a picture being glanced at.
 * A video may follow the runner (the `view` parameter on [drawExportFrame]), and the still never
 * does: a crop is fine for a clip you watch and is not a summary of a journey. A video animates its
 * reveals (the `effects` parameter), and the still never does: a PNG is one instant, and one
 * instant of an animation is a badge caught half grown.
 */
internal object ExportFrame {

    const val WIDTH = 1920
    const val HEIGHT = 1080

    /** How big a TextMeasurer cache one export needs; the strip and the chart share it. */
    const val TEXT_CACHE_SIZE = 96

    /** "Every badge at full size", for a frame drawing no reveal effects. See [drawExportFrame]. */
    private val SETTLED: (Int) -> Float = { 1f }

    /**
     * The scale that fits a whole chart into the frame, letterboxed on whichever axis binds.
     *
     * Unlike the live chart, an export has no pan and no follow camera, so a wide chart
     * ([LayoutEngine.widthFactorFor]) is fitted by its full width rather than cropped to its first
     * screenful: something shared has to show everything.
     *
     * For an unstretched 1060x600 chart the height binds (fitting the width alone would need 1086.8
     * vertical pixels and quietly crop the top and bottom), leaving 6px bars left and right.
     */
    fun scaleFor(layout: ChartLayout): Float =
        min(WIDTH / layout.width, HEIGHT / LayoutEngine.H)

    /**
     * The scale a *followed* frame letterboxes at: the fixed 1060x600 stage into the frame, which
     * is the same box `JourneyChart`'s `withStage` fits and therefore the box a [ChartZoom]'s offset
     * and clamp are expressed in. The view describes everything else - where in the chart the camera
     * is, and how far past the stage a wide layout overflows it.
     *
     * Deliberately NOT [scaleFor]. On an unstretched chart the two are the same 1.8 and the same
     * 6px bars, which is exactly the trap: sharing them would look correct forever and then render
     * a wide layout at the layout's own tighter fit, with the view's offsets - measured in stage
     * units - applied on top of a scale that is not the stage's. The overview path fits the chart;
     * the followed path fits the stage. Two boxes, two transforms.
     */
    val STAGE_SCALE: Float = min(WIDTH / LayoutEngine.W, HEIGHT / LayoutEngine.H)

    /**
     * The things one export run works out once and then reuses across its frames.
     *
     * Passed in rather than kept on this object, because [ExportFrame] is a stateless renderer two
     * exporters draw through and a still image renders exactly one frame. A run without one (the
     * still export, the tests) simply pays the per-frame price for its single frame.
     */
    class RunCache {
        /** The frame-sized, colour-filtered copy of the game under the sweep. See [ExportBackdrop.ScaledArt]. */
        val art = ExportBackdrop.ScaledArt(WIDTH, HEIGHT)

        /**
         * The backdrop's minimum dwell, one gate per band, so the exported file holds each
         * picture the way the watched stage did; see [BackdropLogic.DwellGate]. On the run
         * cache because the gates are the run's memory of what is on its stage - a still
         * export has no run and no dwell to owe.
         */
        val mainArtDwell = BackdropLogic.DwellGate()
        val rivalArtDwell = BackdropLogic.DwellGate()

        /**
         * The plate's own quarter-dwell (owner request): the header names games four times as
         * fast as the stage swaps pictures, exactly as the live header does - see
         * [com.twinlore.app.journey.ui.HEADER_DWELL_MILLIS]. Fed the same targets as
         * [mainArtDwell], so the plate leads the art by design.
         */
        val headerDwell = BackdropLogic.DwellGate(com.twinlore.app.journey.ui.HEADER_DWELL_MILLIS)

        private var weightsFrame: JourneyFrame? = null
        private var mainWeightsCache: List<Int> = emptyList()
        private var rivalWeightsCache: List<List<Int>> = emptyList()

        /**
         * What each unlock was worth, index-parallel with the curves' tracks, for the gates'
         * heaviest-of-window adoption ([BackdropLogic.heaviestGameBetween]) - built once per run
         * rather than once per encoded frame. Rival weights matched by curve name: a rival with
         * an empty fetch has a roster slot but no curve.
         */
        fun mainWeights(frame: JourneyFrame): List<Int> {
            cacheWeights(frame)
            return mainWeightsCache
        }

        fun rivalWeights(frame: JourneyFrame, layout: ChartLayout): List<List<Int>> {
            cacheWeights(frame)
            if (rivalWeightsCache.isEmpty() && layout.rivals.isNotEmpty()) {
                rivalWeightsCache = layout.rivals.map { curve ->
                    frame.rivals.firstOrNull { it.name == curve.playerName }
                        ?.unlocks?.map { it.points } ?: emptyList()
                }
            }
            return rivalWeightsCache
        }

        private fun cacheWeights(frame: JourneyFrame) {
            if (weightsFrame === frame) return
            weightsFrame = frame
            mainWeightsCache = frame.main.unlocks.map { it.points }
            rivalWeightsCache = emptyList()
        }

        private var stripKey: ChipKey? = null
        private var strip: StripState? = null

        /**
         * Everything the strip needs from the journey at one moment of it: its counters, and the
         * bar a single game's plate carries at its far end.
         *
         * The two travel together because they are built from the same numbers at the same instant
         * and are stale under exactly the same conditions - see [ChipKey].
         */
        data class StripState(
            val chips: List<JourneyStripText.Chip>,
            val bar: ExportStrip.BarState?,
        )

        /**
         * The strip's state, rebuilt only when what it says changes.
         *
         * It changes a few dozen times across four hundred and fifty frames, and building it is
         * not cheap at either end: the end state runs two whole-journey statistical passes
         * (`longestStreakDays`, `longestPlayedGameId`) for every one of the thirty hold frames, and
         * a compare strip rescans every player's unlocks for a line that does not depend on
         * progress at all.
         *
         * The bar was outside this memo for a while and cost a whole `JourneyStripText.gameHeader`
         * - a date formatted included - on every one of those four hundred and fifty frames, on
         * the encoder's own background thread, to arrive at two integers that had not moved.
         */
        fun stripFor(key: ChipKey, build: () -> StripState): StripState {
            val cached = strip
            if (cached != null && stripKey == key) return cached
            return build().also { strip = it; stripKey = key }
        }

        private var painterKey: Pair<AmbienceAnimation, Color>? = null
        private var painter: AmbiencePainter? = null

        /** The ambience painter, built once per run: it precomputes its brushes on construction. */
        fun painterFor(animation: AmbienceAnimation, accent: Color): AmbiencePainter {
            val key = animation to accent
            painter?.takeIf { painterKey == key }?.let { return it }
            return AmbiencePainter(animation, accent).also { painter = it; painterKey = key }
        }

        /**
         * Frees the run's frame-sized native buffers now rather than at some future GC.
         *
         * The cache's bitmaps are native-backed and their Java shells are a few dozen bytes, so
         * the collector - which is what normally frees bitmap pixel memory - sees no pressure from
         * them and takes its time. A session of repeated exports then parks tens of megabytes of
         * dead pixel memory between runs, exactly the kind of native-heap headroom the next
         * export's encoder allocation dies without. Called by the exporter when its run ends,
         * success and failure alike; the cache must not be used again after.
         */
        fun release() {
            art.release()
            strip = null
            stripKey = null
            painter = null
            painterKey = null
        }
    }

    /** Everything the strip's text is a function of. Anything else about a frame cannot change it. */
    data class ChipKey(
        val journeyDateMillis: Long?,
        val points: Int,
        val awards: AwardTally,
        val revealed: Int,
        val games: Int,
        val completed: Boolean,
        /** A compare span's strip is one score per player, so the rivals' move it too. */
        val rivalPoints: List<Int> = emptyList(),
        /**
         * The gold share of a single game's bar, which the counters themselves never say.
         *
         * It can only move when [revealed] moves - a hardcore unlock is an unlock - so it adds no
         * rebuilds in practice. It is here because the bar rides in the same memo as the chips
         * now, and a key that did not name one of the two numbers that memo holds would be a
         * silent staleness waiting for the day the two counts stop moving together.
         */
        val hardcore: Int = 0,
        /**
         * How many unlocks the sweep has revealed of each rival's own run.
         *
         * A single-game compare strip is cut at the sweep per player (`JourneyStripText`'s
         * `exportChips` through `frameUpToElapsed`), and what a rival's row then says - their
         * duration, their unlock count, their award dots - moves with this count rather than with
         * anything about the main player. [rivalPoints] beside it is nearly the same signal and
         * not quite: an achievement worth no points moves the row and leaves the score alone.
         *
         * Their medals need no field of their own: a single game's award is timestamped on the
         * achievement that earned it (`JourneyBuilder.computeGameMilestones` takes the max/min of
         * its own unlock times), so a medal can only cross the sweep on a frame where an unlock
         * crosses it too.
         */
        val rivalRevealed: List<Int> = emptyList(),
        /**
         * Each rival's own award breakdown at this point of the sweep, index for index with
         * `frame.rivals` - the award half of [rivalRevealed]'s and [rivalPoints]'s own story, for
         * a running compare span's rows (`JourneyStripText.comparePlaybackChips`).
         */
        val rivalAwards: List<AwardTally> = emptyList(),
        /**
         * The title of the game under the sweep, which a running span's header names on its first
         * line ("3 - Zelda").
         *
         * The one thing on the strip that moves with the SWEEP rather than with the counters. A key
         * without it would hold the header on the game the sweep was over when the last counter
         * last changed, which for a span whose games run back to back is most of the clip: the icon
         * (looked up per frame, outside this memo) would change and the name beside it would not.
         */
        val nowPlaying: String? = null,
    )

    /** A measurer for an export's text, resolved against the app's own fonts. */
    fun textMeasurer(context: Context): TextMeasurer = TextMeasurer(
        fallbackFontFamilyResolver = createFontFamilyResolver(context),
        fallbackDensity = Density(1f),
        fallbackLayoutDirection = LayoutDirection.Ltr,
        cacheSize = TEXT_CACHE_SIZE,
    )

    /**
     * Draws the whole frame at [progress] into a scope already sized [WIDTH] x [HEIGHT].
     *
     * The background is filled first because an encoder surface hands back whatever was in that
     * buffer two frames ago; the art then draws in frame pixels under the chart's own transform,
     * and the strip last, over the letterbox band above the plot.
     */
    fun DrawScope.drawExportFrame(
        layout: ChartLayout,
        paths: ChartPaths,
        frame: JourneyFrame,
        style: ChartStyle,
        textMeasurer: TextMeasurer,
        bitmaps: Map<String, ImageBitmap>,
        art: Map<Int, ImageBitmap>,
        userName: String,
        progress: Float,
        ranks: JourneyStripText.Ranks = JourneyStripText.Ranks(),
        cache: RunCache? = null,
        /**
         * The ambience the player was showing, drawn where the player draws it: over the
         * backdrop art, under the chart. Concrete by the time it gets here (the screen resolves
         * RANDOM once, so the shared file shows the same weather the watcher saw), and clocked
         * off [progress] so every export of the same journey is the same video.
         */
        ambience: AmbienceAnimation = AmbienceAnimation.NONE,
        /** Whether a flat line carries a sign over the player's face; see [IdleSigns]. */
        idleSigns: Boolean = true,
        /**
         * Where the camera is, when the video follows the runner ([ExportCamera]); null is the
         * fitted whole chart this export has always drawn.
         *
         * Null is not the same thing as an overview view. Handing one in switches the frame to the
         * stage transform ([STAGE_SCALE]) for the whole clip, including the frames its end glide
         * arrives at the overview on: a followed video that swapped transform families as it landed
         * would show the chart jump scale on the last frames of the glide, which is the one moment
         * the camera is meant to be handing the picture over smoothly. The still export passes
         * null - a crop is fine for a clip you watch and is not a summary of a journey.
         */
        view: ChartZoom? = null,
        /**
         * This frame's reveal effects: the pop, the floating "+N", the confetti and the award
         * flash, read off a plan built once for the whole run ([ExportEffects]).
         *
         * Null draws the settled picture - every revealed icon at full size, no effect in flight -
         * and is what the still image export passes. That is not an oversight kept for old times:
         * a PNG is one instant and is a summary of a journey, so it must never catch a badge half
         * popped or the stage mid-flash. A video is the opposite case: it is the thing the watcher
         * watched, and it was arriving without a single one of the celebrations in it.
         */
        effects: ExportEffects.Frame? = null,
        /** The art alpha the watcher's stage used; see [ExportBackdrop.drawBackdropArt]. */
        backdropDim: Float = 0.25f,
        /**
         * This frame's position in the encoded video, for the backdrop's minimum dwell - the
         * frame index over [ExportTimeline.FPS], NOT journey time, so a faster export speed
         * does not quietly shorten how long each picture holds. Negative (the still export,
         * the tests) draws the art exactly at the sweep with no throttle: one frame owes no
         * dwell to a frame that never existed.
         */
        videoTimeMillis: Long = -1L,
    ) {
        // The letterbox, and the box it fits: the whole chart when the frame is the overview, the
        // fixed stage the view is expressed in when it follows. See [STAGE_SCALE].
        val fit = if (view != null) STAGE_SCALE else scaleFor(layout)
        val boxWidth = if (view != null) LayoutEngine.W else layout.width
        // What the renderer sizes its hairlines against, which is the whole composed magnification
        // - the letterbox and the camera's own zoom - exactly as `withStage` hands it to the live
        // chart. A followed frame draws its lines at 2.2x and must not draw them 2.2x thick.
        val scale = if (view != null) fit * view.zoom else fit
        val sweep = JourneyChartRenderer.sweepX(layout, paths, progress)

        drawRect(color = style.background, size = size)
        // A landed span rests on no picture, exactly as the live stage does (see backdropArgs):
        // the art belongs to the game under the sweep, and the still is taken from the closing
        // frame - it used to carry whatever game happened to be unlocked last (user request).
        val ended = frame.mode == JourneyMode.SPAN && progress >= 0.999f
        if (!ended) {
            drawArtBehind(
                layout, art, sweep, cache, frame.mode, backdropDim,
                // The Noir ambience desaturates the stage; the export matches the watched frame.
                monochrome = ambience == AmbienceAnimation.NOIR,
                videoTimeMillis = videoTimeMillis,
                frame = frame,
            )
        }
        if (ambience != AmbienceAnimation.NONE && ambience != AmbienceAnimation.RANDOM) {
            val painter = cache?.painterFor(ambience, style.accent) ?: AmbiencePainter(ambience, style.accent)
            painter.draw(this, (progress * ExportTimeline.REVEAL_SECONDS * 1000f).toLong())
        }
        withTransform({
            translate(
                (WIDTH - boxWidth * fit) / 2f,
                (HEIGHT - LayoutEngine.H * fit) / 2f,
            )
            scale(fit, fit, pivot = Offset.Zero)
            // The camera, composed inside the letterbox in exactly the order `JourneyChart`'s
            // withStage composes it: the view's offset is a pan in stage units, so it is applied
            // after the fit and before the zoom. Reversing the two would scale the pan.
            if (view != null) {
                translate(view.offset.x, view.offset.y)
                scale(view.zoom, view.zoom, pivot = Offset.Zero)
            }
        }) {
            with(JourneyChartRenderer) {
                drawJourney(
                    layout = layout,
                    paths = paths,
                    frame = frame,
                    style = style,
                    textMeasurer = textMeasurer,
                    bitmaps = bitmaps,
                    userName = userName,
                    progress = progress,
                    cursorNodeIndex = null,
                    nodeAppearProgress = effects?.nodeAppearProgress ?: SETTLED,
                    // "Off" is the renderer's own NO_EFFECT rather than an equivalent lambda of
                    // ours: drawFloatingPoints tests that exact instance to skip its whole pass,
                    // so a private `{ -1f }` here would quietly cost a still frame a walk over
                    // every node in the layout to draw nothing.
                    //
                    // pointsSeconds, not nodeSeconds: the "+N" is throttled and the pop-in is not,
                    // exactly as the live chart keeps the two apart. See [ExportEffects.Frame].
                    nodeEffectSeconds = effects?.pointsSeconds ?: JourneyChartRenderer.NO_EFFECT,
                    medalEffectSeconds = effects?.medalSeconds ?: JourneyChartRenderer.NO_EFFECT,
                    medalWashSeconds = effects?.medalWashSeconds ?: JourneyChartRenderer.NO_EFFECT,
                    focused = false,
                    scale = scale,
                    // No caption and no running date over the exported chart: the strip drawn
                    // into the frame below already carries the date, and "points and time" is
                    // what the axes themselves say - the top band was saying everything twice.
                    //
                    // Compare plates are not an exception to this, even though their own strip is
                    // rows rather than a single baseline: the date still rides the first row's
                    // own right edge there (see ExportStrip.drawComparePlate) rather than this
                    // chart-drawn corner readout, because the chart has no way to draw it here.
                    //
                    // The corner readout is a different question, and its answer changed when the
                    // video learned to follow. drawJourney only draws it while genuinely zoomed in
                    // (gated on `!view.isOverview`), so on the fitted path it is dead either way -
                    // a version of this flag that turned on for compare plates was tried and
                    // reverted, because it read as correct and drew nothing. A followed frame IS
                    // zoomed in, so the flag is live there, and it is turned on: the followed
                    // on-screen view shows the readout beside its own strip, and a video that has
                    // no axis on screen for most of its run is precisely where "which week is this"
                    // stops being answerable from the picture. The two dates are quantized
                    // differently (this one to the half-gridline, the strip's to the sweep), which
                    // is true on the device as well and is why the readout sits in the opposite
                    // corner from the strip rather than beside it.
                    showChartChrome = false,
                    showZoomDate = view != null,
                    showIdleSigns = idleSigns,
                    view = view ?: ChartZoom.NONE,
                    // Only meaningful once a view is in play: it is what tells the cull how much
                    // stage the letterbox leaves on screen around the fitted chart. Zero (the
                    // overview path, which culls nothing anyway) means "a canvas that fits the
                    // stage exactly"; the real frame size is what makes the followed path's cull
                    // rect describe the region actually being drawn, letterbox bars included.
                    canvasSize = if (view != null) Size(WIDTH.toFloat(), HEIGHT.toFloat()) else Size.Zero,
                )
            }
        }
        drawStrip(layout, frame, style, textMeasurer, bitmaps, sweep, progress, ranks, cache, videoTimeMillis)
    }

    /**
     * The dimmed art behind the chart at this frame's sweep position, in full frame pixels rather
     * than in the chart's virtual space: it fills the export the way the screen's backdrop fills the
     * stage.
     *
     * One image on a solo chart, the main player's current game; a compare SPAN splits the frame
     * between the two players' games the way the live stage does (user request), main player on
     * top - the screen's split is horizontal for the same reason this one is: the two curves run
     * left to right together, so cutting the stage across keeps each player's picture behind their
     * own line. A compare GAME chart has both players on the same game, so it takes the whole-frame
     * path instead (see [splitsForRivals]) - splitting it drew the identical picture twice with a
     * seam across the middle.
     *
     * The per-game scaled cache only serves the whole-frame case. A half-height band is a different
     * destination rectangle, and a second cache for it would double the buffer memory to save a
     * resample the compare export is not the hot path for.
     */
    private fun DrawScope.drawArtBehind(
        layout: ChartLayout,
        art: Map<Int, ImageBitmap>,
        sweepX: Float,
        cache: RunCache?,
        mode: JourneyMode,
        alpha: Float,
        monochrome: Boolean = false,
        videoTimeMillis: Long = -1L,
        frame: JourneyFrame? = null,
    ) {
        if (art.isEmpty()) return
        val mainId = throttled(
            cache?.mainArtDwell, videoTimeMillis, BackdropLogic.gameAtSweep(layout.main.bgTrack, sweepX),
            sweepX, layout.main.bgTrack, if (cache != null && frame != null) cache.mainWeights(frame) else null,
        )
        val mainArt = mainId?.let { art[it] }
        val rivalTrack = layout.rivals.firstOrNull()?.bgTrack
        if (rivalTrack != null && splitsForRivals(mode, hasRivalTrack = true)) {
            val half = HEIGHT / 2
            drawBackdropArt(mainArt, top = 0, width = WIDTH, height = half, alpha = alpha, monochrome = monochrome)
            val rivalArt = throttled(
                cache?.rivalArtDwell, videoTimeMillis, BackdropLogic.gameAtSweep(rivalTrack, sweepX),
                sweepX, rivalTrack,
                if (cache != null && frame != null) {
                    cache.rivalWeights(frame, layout).getOrElse(0) { emptyList() }
                } else {
                    null
                },
            )
                ?.let { art[it] }
            drawBackdropArt(rivalArt, top = half, width = WIDTH, height = HEIGHT - half, alpha = alpha, monochrome = monochrome)
            return
        }
        if (cache == null) {
            drawBackdropArt(mainArt, top = 0, width = WIDTH, height = HEIGHT, alpha = alpha, monochrome = monochrome)
            return
        }
        // Scaled and filtered once per game, then blitted one to one - see [ExportBackdrop.ScaledArt].
        val scaled = cache.art.forGame(mainId, mainArt, monochrome) ?: return
        drawScaledBackdropArt(scaled, top = 0, alpha = alpha)
    }

    /**
     * [BackdropLogic.DwellGate.shownAt] when this run keeps gates and this frame has a video
     * position; the unthrottled target otherwise (the still export, the tests). The gate is
     * consulted even when [BackdropLogic.gameAtSweep] answers null, so the pre-first-unlock
     * stretch stamps nothing and the first game still lands undelayed. With [weights] in hand the
     * gate adopts the heaviest game of the window it held through rather than the raw target,
     * exactly as the live stage does; see [BackdropLogic.heaviestGameBetween].
     */
    private fun throttled(
        gate: BackdropLogic.DwellGate?,
        videoTimeMillis: Long,
        targetId: Int?,
        sweepX: Float = 0f,
        track: List<Pair<Float, Int>> = emptyList(),
        weights: List<Int>? = null,
    ): Int? = when {
        gate == null || videoTimeMillis < 0 -> targetId
        weights == null -> gate.shownAt(videoTimeMillis, targetId)
        else -> gate.shownAt(videoTimeMillis, targetId, sweepX) { a, b ->
            BackdropLogic.heaviestGameBetween(track, weights, a, b)
        }
    }

    /** The counters, read off the journey at exactly the progress this frame is drawn at. */
    private fun DrawScope.drawStrip(
        layout: ChartLayout,
        frame: JourneyFrame,
        style: ChartStyle,
        textMeasurer: TextMeasurer,
        bitmaps: Map<String, ImageBitmap>,
        sweepX: Float,
        progress: Float,
        ranks: JourneyStripText.Ranks,
        cache: RunCache?,
        videoTimeMillis: Long = -1L,
    ) {
        val stats = HudAtProgress.stats(frame, layout, progress)
        val awards = HudAtProgress.awardTally(layout, progress)
        val completed = progress >= 1f
        // What a running compare span's strip says instead of the counters: one score per player.
        // Empty for every other journey, and the call is a binary search per rival, so a solo
        // export pays nothing for it.
        val rivalPoints = HudAtProgress.rivalPoints(frame, layout, progress).toList()
        // The unlock count and award breakdown behind that score, for the same running compare
        // row: both move with the shared sweep exactly as the score does (see
        // `HudAtProgress.rivalRevealed`, `HudAtProgress.rivalAwardTally`), so a rival's row reads
        // no further ahead than their curve has been drawn.
        val rivalRevealed = HudAtProgress.rivalRevealed(frame, layout, progress).toList()
        val rivalAwards = HudAtProgress.rivalAwardTally(frame, layout, progress)
        // A single game's plate carries the same bar its on-screen header does. Every other
        // journey's is null, and the plate then ends at its facts. Built inside the memo below
        // rather than beside it: `gameHeader` formats a date, and four hundred and fifty frames of
        // an export are four hundred and fifty of them for two integers that move a few dozen times.
        val singleGame = frame.mode == JourneyMode.GAME && !JourneyStripText.isGameCompare(frame)
        // The game under the sweep: its icon leads the plate, and on a running span its title IS
        // the plate's first line ("3 - Zelda"). Resolved once, above the memo, so the name and the
        // art beside it are the same game's by construction rather than by two lookups agreeing.
        // Already decoded: JourneyBitmaps preloads every game's icon for the medallions that fall
        // back to it, so this is a map lookup rather than a fetch.
        // Through the plate's own quarter-dwell gate (owner request, round two: matching the art's
        // full dwell named too few of the games going by), fed the same targets the art gate gets,
        // so the plate leads the picture by a beat exactly as the live header does. Only the
        // plate: the counters and awards beside it keep moving with the sweep.
        val game = throttled(
            cache?.headerDwell, videoTimeMillis, BackdropLogic.gameAtSweep(layout.main.bgTrack, sweepX),
            sweepX, layout.main.bgTrack, cache?.mainWeights(frame),
        )
            ?.let { frame.main.games[it] }
        val icon = game?.iconPath?.let { bitmaps[JourneyUrls.media(it)] }
        val build = {
            RunCache.StripState(
                chips = JourneyStripText.exportChips(
                    frame = frame,
                    layout = layout,
                    journeyDateMillis = stats.journeyDateMillis,
                    points = stats.points,
                    awards = awards,
                    revealed = stats.unlocked,
                    games = stats.games,
                    completed = completed,
                    progress = progress,
                    ranks = ranks,
                    rivalPoints = rivalPoints,
                    rivalRevealed = rivalRevealed,
                    rivalAwards = rivalAwards,
                    nowPlaying = game?.title,
                ),
                bar = if (!singleGame) {
                    null
                } else {
                    val header = JourneyStripText.gameHeader(
                        frame = frame,
                        layout = layout,
                        journeyDateMillis = stats.journeyDateMillis,
                        revealed = stats.unlocked,
                        points = stats.points,
                        completed = completed,
                        awards = awards,
                        hardcore = stats.hardcore,
                    )
                    ExportStrip.BarState(
                        percent = header.percent,
                        hardcorePercent = header.hardcorePercent,
                        // The highest award, drawn as a dot beside the percentage the way the
                        // screen's own header draws it. The plate used to get it as words on the
                        // title chip; see JourneyStripText.gameHeaderChips.
                        award = JourneyStripText.awardParts(awards).firstOrNull()?.kind,
                    )
                },
            )
        }
        val key = ChipKey(
            stats.journeyDateMillis, stats.points, awards, stats.unlocked, stats.games, completed,
            rivalPoints, stats.hardcore,
            rivalRevealed = rivalRevealed,
            rivalAwards = rivalAwards,
            nowPlaying = game?.title,
        )
        val state = cache?.stripFor(key, build) ?: build()
        // The ring the screen draws around that icon, which is not the same colour in both modes:
        // a span's header rings the game under the sweep in that game's own series colour, so the
        // ring changes with the picture behind it (`PlayerOverview`'s `nowPlaying`), while a single
        // game's header rings it in the accent - which is what carries the user's "Your Color"
        // override (`GameHeaderBar`). The plate used to draw the series colour in both, so a solo
        // game's exported plate ignored a colour the watcher had chosen.
        val ring = if (singleGame) style.accent else game?.let { style.colorForIndex(it.colorIndex) }
        // The one ordinary-plate state whose chips are a finished span's (`JourneyStripText.
        // endChips`, via `exportChips`'s `if (completed) return endChipsOrCompare(...)` branch) -
        // mirrored here rather than threaded out of `exportChips` itself, since `drawExportStrip`
        // has no other way to tell a finished span's chips apart from a running one's or a single
        // game's (see `finishedSpan`'s own doc). A compare chart's chips carry their own tell
        // ([JourneyStripText.Chip.rivalIndex]) and never reach this flag at all - `drawExportStrip`
        // routes to `drawComparePlate` before it is read.
        val finishedSpan = completed && !singleGame && !JourneyStripText.hasRivals(frame)
        drawExportStrip(
            state.chips, style, textMeasurer, frameWidth = WIDTH.toFloat(), bitmaps = bitmaps,
            // No icon once a span has landed: the counters it lands on are `EndPanel`, a different
            // widget from the header that ran, and `EndPanel` has no icon. The icon slot belongs to
            // the running header, and it went on being drawn after the chips underneath it had been
            // swapped - so the finished span's plate led with whichever game the sweep happened to
            // stop on, in front of totals that are about every game in the span. A single game's
            // header keeps its icon, because on screen it keeps its header to the last frame.
            icon = if (finishedSpan) null else icon,
            iconRing = ring, bar = state.bar, finishedSpan = finishedSpan,
        )
    }

    /** A frame rendered into its own bitmap, for the still image export and for tests. */
    fun render(
        context: Context,
        layout: ChartLayout,
        frame: JourneyFrame,
        style: ChartStyle,
        bitmaps: Map<String, ImageBitmap>,
        art: Map<Int, ImageBitmap>,
        userName: String,
        progress: Float,
        ranks: JourneyStripText.Ranks = JourneyStripText.Ranks(),
        ambience: AmbienceAnimation = AmbienceAnimation.NONE,
        idleSigns: Boolean = true,
        /**
         * A followed camera, for the tests that render one. The still image export deliberately
         * passes nothing: see the [view] parameter on [drawExportFrame].
         */
        view: ChartZoom? = null,
        /**
         * Reveal effects, for the tests that render one. The still image export deliberately
         * passes nothing: see the [effects] parameter on [drawExportFrame].
         */
        effects: ExportEffects.Frame? = null,
        /** The art alpha the watcher's stage used; see [drawExportFrame]. */
        backdropDim: Float = 0.25f,
    ): ImageBitmap {
        val target = ImageBitmap(WIDTH, HEIGHT)
        CanvasDrawScope().draw(
            density = Density(1f),
            layoutDirection = LayoutDirection.Ltr,
            canvas = Canvas(target),
            size = Size(WIDTH.toFloat(), HEIGHT.toFloat()),
        ) {
            drawExportFrame(
                layout = layout,
                paths = ChartPaths(layout),
                frame = frame,
                style = style,
                textMeasurer = textMeasurer(context),
                bitmaps = bitmaps,
                art = art,
                userName = userName,
                progress = progress,
                ranks = ranks,
                ambience = ambience,
                idleSigns = idleSigns,
                view = view,
                effects = effects,
                backdropDim = backdropDim,
            )
        }
        return target
    }
}
