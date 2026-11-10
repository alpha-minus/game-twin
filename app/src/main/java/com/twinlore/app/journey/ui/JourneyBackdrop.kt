package com.twinlore.app.journey.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import com.twinlore.app.journey.domain.JourneyParsing
import com.twinlore.app.journey.domain.model.GameRef
import com.twinlore.app.journey.ui.chart.ChartConstants
import kotlinx.coroutines.delay
import androidx.compose.runtime.withFrameNanos

private const val CROSSFADE_MILLIS = 800

/**
 * See [JourneyBackdrop]: how still the sweep has to be before the stage answers it.
 *
 * Deliberately short, and shorter than it wants to be. The swap it guards is already an 800ms
 * crossfade, so most of the smoothing comes from there; this only has to swallow the burst of
 * targets a dense stretch throws in the same breath.
 *
 * The ceiling is a test one, and worth knowing before raising it: a longer wait leaves a pending
 * coroutine across most of the player's screen tests, which walk the compose clock by hand, and at
 * 450ms it made `JourneyScreenStateTest` flaky (2, 2, 1, 0 failures over four runs, different
 * tests each time; three clean runs at 0ms and at this value). If the backdrop ever needs real
 * coalescing, the honest place for it is inside [BackdropSwapState] - which already sequences the
 * crossfade off load events rather than off a timer - not a second timer stacked on top.
 */
private const val SETTLE_MILLIS = 150L

/**
 * The least time a landed backdrop image keeps the stage, however fast the sweep crosses games
 * behind it (owner request): the settle alone stopped the strobe of a BUSY burst, but a merely
 * brisk pace - a game every few hundred milliseconds - still swapped the picture on every one.
 *
 * Yes, this is the "second timer" [SETTLE_MILLIS]'s note warns about, and the deviation is
 * deliberate: a minimum dwell is irreducibly temporal, so no load-event sequencing inside
 * [BackdropSwapState] can express it. The test hazard that note records does not arm here -
 * the dwell delay only runs when a swap follows another swap within two seconds, and the
 * screen tests compose the stage with a single stable target, so the timer never starts in
 * them (verified over repeated suite runs when this landed).
 */
private const val MIN_DWELL_MILLIS = 2_000L

/**
 * The span header's own dwell: a quarter of the stage's (owner request). The full dwell made the
 * header name too few of the games going by - a picture needs two seconds to be seen, a name does
 * not - so the header leads the backdrop deliberately, on the same target stream through the same
 * gate rule, and the stage catches up at its own pace. Shared with the export
 * (`ExportFrame.RunCache`) so the file's plate paces exactly like the watched one.
 */
internal const val HEADER_DWELL_MILLIS = MIN_DWELL_MILLIS / 4

/**
 * How much of [MIN_DWELL_MILLIS] is still owed, given the frame-clock stamp of the last swap.
 * Pure arithmetic so the dwell rule is testable without Compose; -1 means nothing has landed
 * yet, which owes nothing.
 */
internal fun remainingDwellMillis(nowNanos: Long, shownSinceNanos: Long, minDwellMillis: Long): Long =
    if (shownSinceNanos < 0) 0
    else (minDwellMillis - (nowNanos - shownSinceNanos) / 1_000_000L).coerceAtLeast(0)

/** How much of the shorter edge a game's ICON takes when it is all the art there is. */
private const val ICON_FRACTION = 0.5f

// Saturation is knocked down rather than removed entirely: a fully desaturated backdrop reads as
// flat behind the chart's own colours, while a modest desaturation keeps it recognisably art
// without competing with the curves drawn on top of it.
private val backdropColorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0.6f) })

/** The Noir ambience's stage: fully desaturated art, on screen and in every export alike. */
private val monochromeBackdropFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

/**
 * Advances through a list of art candidate URLs as each one fails to load, e.g. a game whose title
 * screen is missing or broken falls through to the next [BackdropLogic.artCandidatesFor] entry.
 * Deliberately
 * plain Kotlin (no Coil, no Compose) so the candidate-selection logic is testable without a fake
 * `ImageLoader`; see `JourneyBackdropTest`.
 */
class ArtChain(private val urls: List<String>) {
    private var index = 0

    /** The candidate currently being attempted, or null once every candidate has failed. */
    fun current(): String? = urls.getOrNull(index)

    /** Moves to the next candidate. Returns false when there is none left (current() is then null). */
    fun advance(): Boolean {
        if (index >= urls.size) return false
        index++
        return index < urls.size
    }
}

/**
 * The sequencing decision behind [BackdropSlot]'s crossfade: which url sits on the held-steady
 * back layer and which sits on the fading-in front layer, and whether each should currently be
 * visible. [BackdropSlot] owns the actual alpha animation (`animateFloatAsState`) and Coil's load
 * state; this class only decides what to render in response to those events, so an incoming image
 * is never composited on top of the backdrop before it has actually finished loading — that gap
 * between "the crossfade started" and "the bitmap is ready" was the flash the crossfade was meant
 * to hide in the first place.
 *
 * The back layer is the last *confirmed-displayed* image, held at full alpha for as long as it
 * takes the front layer to load; it is never cleared just because a swap was requested. The front
 * layer is the in-flight candidate: invisible until [incomingLoaded] confirms it, at which point
 * the two layers dissolve into each other (the back starts fading out only once the incoming
 * image is actually on screen) and [settle] later promotes the front onto the back layer, freeing
 * the front slot for the next swap. Deliberately plain Kotlin (no Compose, no Coil) so the
 * sequencing is testable without a fake `ImageLoader`; see `JourneyBackdropTest`.
 *
 * The invariant every transition here preserves: at rest exactly one layer holds a url and is
 * visible. Whatever the user can actually see is always the *back* layer by the time a swap is
 * requested ([targetChanged] commits a visible front first), so a sweep that moves between games
 * faster than the crossfade can never leave two loaded images stacked at full alpha, which is what
 * "multiple background images at the same time" looked like on screen.
 */
data class BackdropSwapState(
    val backUrl: String? = null,
    val backVisible: Boolean = false,
    val frontUrl: String? = null,
    val frontVisible: Boolean = false,
) {
    /**
     * The sweep moved onto a new target — [url] is the next candidate to try, or null once every
     * candidate for the current game has failed ([ArtChain] exhausted). Anything already on screen
     * is committed to the back layer first ([commitFront]), so the layer the new candidate fades in
     * over is always the image the user is currently looking at. Then:
     *  - a null [url] fades the back layer out gracefully rather than holding it forever, since
     *    there is nothing left to replace it with;
     *  - retargeting away from an in-flight, not-yet-loaded front candidate discards it rather
     *    than letting it pop in later against the wrong back image;
     *  - coming back to the image already on the back layer re-holds it, which matters when it was
     *    part way through fading out: the sweep leaving a game and returning to it inside one
     *    crossfade would otherwise let the fade run to nothing and leave the stage blank while it
     *    was showing exactly the right image.
     */
    fun targetChanged(url: String?): BackdropSwapState {
        val base = commitFront()
        return when {
            url == null -> base.copy(frontUrl = null, frontVisible = false, backVisible = false)
            // Already settled on this candidate; only the hold is (re)asserted.
            url == base.backUrl && base.frontUrl == null -> base.copy(backVisible = true)
            else -> base.copy(frontUrl = url, frontVisible = false, backVisible = base.backUrl != null)
        }
    }

    /**
     * [url] finished loading. Ignored if it is no longer the front candidate (stale callback from a
     * candidate we've since retargeted away from) — starts the crossfade otherwise.
     *
     * The back layer starts fading out at this exact moment and not a frame earlier: holding it at
     * full alpha until the incoming image was confirmed on screen is what keeps a slow load from
     * baring the empty stage, and dissolving it out from here is what keeps two images from sitting
     * at full alpha on top of each other once the fade is done.
     */
    fun incomingLoaded(url: String): BackdropSwapState =
        if (url != frontUrl) this else copy(frontVisible = true, backVisible = false)

    /**
     * Moves a front layer the user can already see onto the back layer, freeing the front slot.
     * A front layer that has not loaded yet is invisible, so it is simply dropped by the caller.
     */
    private fun commitFront(): BackdropSwapState =
        if (!frontVisible) this
        else copy(backUrl = frontUrl, backVisible = true, frontUrl = null, frontVisible = false)

    /** [url] failed to load. Ignored if it is no longer the front candidate; otherwise clears the
     * front slot — the back layer is untouched, so the caller (which should immediately retarget
     * to the next [ArtChain] candidate, or null once exhausted) never exposes a blank stage. */
    fun incomingFailed(url: String): BackdropSwapState =
        if (url != frontUrl) this else copy(frontUrl = null, frontVisible = false)

    /**
     * Called after the crossfade has had time to finish. Promotes a fully-faded-in front layer
     * onto the back layer (freeing the front slot for the next swap) or, symmetrically, drops a
     * fully-faded-out back layer once it has nothing left to hold — otherwise a no-op. Two
     * different images would otherwise stay stacked at the same alpha once a fade settles, which
     * reads as a permanent double exposure instead of a clean cut.
     */
    fun settle(): BackdropSwapState = when {
        frontVisible -> commitFront()
        !backVisible && backUrl != null -> copy(backUrl = null)
        else -> this
    }
}

/**
 * The pure math behind the backdrop's "what game is the chart over right now" question: the last
 * unlock on a [com.twinlore.app.journey.domain.CurveLayout.bgTrack]-shaped track whose x has been
 * reached by the sweep line, with the same half-unit playback tolerance the renderer itself uses
 * for reveal comparisons.
 */
object BackdropLogic {
    private val SWEEP_TOLERANCE = ChartConstants.REVEAL_TOLERANCE

    /**
     * A binary search rather than a backwards scan. `lastOrNull { }` walks from the end, and the
     * track is ascending in x by construction (see `CurveLayout.bgTrack`), so at low progress -
     * which is where every playback starts - it walked nearly the whole list, destructuring a Pair
     * per element, before the first match. There is one entry per unlock, and this is asked once
     * per progress step by the backdrop, once more by the player's now-playing chip and once per
     * frame by the export.
     */
    fun gameAtSweep(track: List<Pair<Float, Int>>, sweepX: Float): Int? {
        val limit = sweepX + SWEEP_TOLERANCE
        var lo = 0
        var hi = track.size - 1
        var found = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (track[mid].first <= limit) {
                found = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return if (found < 0) null else track[found].second
    }

    /**
     * The export's minimum dwell: [gameAtSweep] answered per encoded frame swaps the picture the
     * instant the sweep crosses an unlock, which is exactly the strobe [MIN_DWELL_MILLIS] was added
     * to stop on the live stage (owner request - the throttle was not carrying into the file).
     *
     * The live stage cannot use this - its dwell rides the frame clock inside [JourneyBackdrop]'s
     * LaunchedEffect, entangled with the settle and the crossfade - but the rule is the same one:
     * whatever landed keeps the stage for the dwell, and when the dwell runs out the gate adopts
     * whatever is under the sweep NOW, not the intermediate games it held through. Clocked on video
     * time (frame index over the export frame rate), the time the person watching experiences, so
     * a faster export speed does not quietly shorten the hold. One instance per band per run:
     * plain mutable state, single-threaded by construction like the rest of the encode loop.
     */
    class DwellGate(private val minDwellMillis: Long = MIN_DWELL_MILLIS) {
        private var shownId: Int? = null
        private var shownSinceMillis = 0L
        private var shownSinceX = 0f

        fun shownAt(timeMillis: Long, targetId: Int?): Int? {
            // An empty stage has nothing to hold (the same fresh-stage rule as
            // [remainingDwellMillis]): before the first unlock the gate passes targets through.
            val adopt = shownId == null || (targetId != shownId && timeMillis - shownSinceMillis >= minDwellMillis)
            if (adopt) {
                shownId = targetId
                shownSinceMillis = timeMillis
            }
            return shownId
        }

        /**
         * [shownAt] adopting the game that EARNED the window rather than whichever was last under
         * the sweep (owner request): on expiry, [pickInWindow] is asked for the stretch of sweep
         * the hold covered - `(x at the last adoption, x now]` - and its answer wins; the raw
         * target is the fallback for a window with nothing in it (a scrub backwards, a replay).
         * The fresh stage still takes its first target directly - there was no window before it.
         * See [heaviestGameBetween], the picker every caller uses.
         */
        fun shownAt(
            timeMillis: Long,
            targetId: Int?,
            sweepX: Float,
            pickInWindow: (fromX: Float, toX: Float) -> Int?,
        ): Int? {
            val fresh = shownId == null
            val adopt = fresh || (targetId != shownId && timeMillis - shownSinceMillis >= minDwellMillis)
            if (adopt) {
                shownId = if (fresh) targetId else pickInWindow(shownSinceX, sweepX) ?: targetId
                shownSinceMillis = timeMillis
                shownSinceX = sweepX
            }
            return shownId
        }
    }

    /**
     * The game that earned a stretch of the sweep: most points summed per game across the track
     * entries in `(fromX, toX]`, ties to the later game so a tie behaves like the unweighted
     * adopt. Exclusive at the start because the unlock AT fromX belongs to the previous window -
     * it was already shown for. Null for a window with nothing in it.
     *
     * [track] and [points] are index-parallel by construction: `LayoutEngine.buildCurve` appends
     * one entry to each per unlock, so `points[i]` is what `track[i]`'s unlock was worth.
     */
    fun heaviestGameBetween(
        track: List<Pair<Float, Int>>,
        points: List<Int>,
        fromX: Float,
        toX: Float,
    ): Int? {
        var bestId: Int? = null
        var bestSum = 0
        val sums = HashMap<Int, Int>()
        for (i in track.indices) {
            val (x, gameId) = track[i]
            if (x <= fromX) continue
            if (x > toX) break // ascending by construction
            val sum = (sums[gameId] ?: 0) + points.getOrElse(i) { 0 }
            sums[gameId] = sum
            // >= : a tie goes to the game reached later, like the unweighted adopt would.
            if (sum >= bestSum) {
                bestSum = sum
                bestId = gameId
            }
        }
        return bestId
    }

    /**
     * The art for one [source] of the Background picker: the picked field leads, and a game that
     * does not have it falls back through the rest - title screen, then box art, then icon - so a
     * stretch of the journey is only ever plain when the game has no usable art at all. NONE is
     * nothing, deliberately: it is the off switch, not a preference.
     */
    internal fun artCandidatesFor(game: GameRef, source: ExportBackground): List<String> {
        if (source == ExportBackground.NONE) return emptyList()
        val preferred = when (source) {
            ExportBackground.TITLE -> game.titlePath
            ExportBackground.INGAME -> game.ingamePath
            ExportBackground.BOX_ART -> game.boxArtPath
            ExportBackground.NONE -> null
        }
        // Filtered here as well as where the snapshot is written: a validator-matched cache row
        // never refetches, so a game already on disk would keep serving the site's "No screenshot
        // found" card for good if only the mapper knew about it.
        return listOfNotNull(preferred, game.titlePath, game.boxArtPath, game.iconPath)
            .distinct()
            .filterNot(JourneyParsing::isPlaceholderArt)
    }

    /**
     * [artCandidatesFor] without the icon: art big enough to fill a stage.
     *
     * The icon is a 64px thumbnail. It is a real tier of the backdrop, but its own tier - drawn
     * small and centred over the game's colour - rather than one of the images that fill the frame,
     * because blown up to a whole stage it is a smear. Shared with the export's preload so a game
     * whose only art is its icon does not end up as a full-frame thumbnail in the file while the
     * screen the user watched drew it properly.
     */
    internal fun bigArtCandidatesFor(game: GameRef, source: ExportBackground): List<String> =
        artCandidatesFor(game, source).filter { it != game.iconPath }
}

/**
 * The dimmed crossfading art layer behind the journey chart: the sweep's current game, full-stage,
 * or - on a compare SPAN only - that plus the rival's own current game stacked underneath.
 *
 * Whether [art] carries a [BackdropArgs.rival] at all is decided by the caller
 * ([com.twinlore.app.journey.ui.backdropArgs], via [com.twinlore.app.journey.domain.splitsForRivals]),
 * not here: this composable only draws whatever it is handed. A single-game compare chart used to
 * reach this with a rival side attached anyway - both players are on the same game there, so the
 * split painted the identical picture twice with a seam across the middle.
 *
 * Takes a [BackdropArgs] rather than the bare `List<String>` inside it. A List is unstable to the
 * Compose compiler at this version, which made this composable unconditionally non-skippable: it
 * re-executed, with both AsyncImages under it, on every one of the ~7 progress reports a second
 * even when the game behind the sweep had not changed. The holder is declared stable, so the same
 * candidates now cost nothing at all.
 */
@Composable
internal fun JourneyBackdrop(
    art: BackdropArgs,
    modifier: Modifier = Modifier,
    /**
     * How strong the art reads: its own alpha, in the live stage and (via the same stored percent)
     * in both exports. See [com.twinlore.app.data.SettingsRepository.journeyBackdropDim] - the
     * default here is that setting's own default, for a composable a test drives directly.
     */
    dimAlpha: Float = 0.15f,
    /** True under the Noir ambience: the art renders black and white (owner request). */
    monochrome: Boolean = false,
) {
    // The stage waits for the sweep to settle before it answers. A busy stretch of a span can
    // cross several games a second, and a backdrop that tried to keep up was a strobe rather than
    // a picture.
    //
    // A debounce AND a minimum dwell now. The settle keeps the burst behaviour - each new
    // target restarts the wait, so a run of games crossed quickly produces ONE swap - and the
    // dwell keeps whatever landed on the stage for at least [MIN_DWELL_MILLIS], because a
    // merely brisk pace (a game every few hundred milliseconds) sailed through the settle and
    // still flickered (owner request). The dwell is measured with [withFrameNanos] - the SAME
    // clock delay() suspends against in a composition - because the first cut of a dwell used
    // System.currentTimeMillis() beside delay() and the two clocks tore apart the moment a
    // test drove frames by hand.
    var shown by remember { mutableStateOf(art) }
    var shownSinceNanos by remember { mutableStateOf(-1L) }
    LaunchedEffect(art) {
        // The stable-target path touches no clock at all - no delay, no frame await. Screen
        // tests compose the stage exactly once with one target, and any pending frame await
        // here was enough to tip the hand-walked-clock tests (see SETTLE_MILLIS's note).
        // The frame awaits live only on the swap path, where playback is running anyway.
        // First composition keeps the -1 stamp: the first swap owes no dwell, matching the
        // fresh-stage rule in [remainingDwellMillis].
        if (shown !== art) {
            delay(SETTLE_MILLIS)
            val now = withFrameNanos { it }
            val owed = remainingDwellMillis(now, shownSinceNanos, MIN_DWELL_MILLIS)
            if (owed > 0) delay(owed)
            shown = art
            shownSinceNanos = withFrameNanos { it }
        } else {
            shown = art
        }
    }

    Box(modifier.fillMaxSize()) {
        val rival = shown.rival
        if (rival == null) {
            BackdropSide(shown, Modifier.fillMaxSize().clipToBounds(), dimAlpha, monochrome)
        } else {
            // A compare chart carries both players' games (user request): the signed-in player's
            // above, the rival's below, the same way their curves stack on the chart.
            Column(Modifier.fillMaxSize()) {
                BackdropSide(shown, Modifier.fillMaxWidth().weight(1f).clipToBounds(), dimAlpha, monochrome)
                BackdropSide(rival, Modifier.fillMaxWidth().weight(1f).clipToBounds(), dimAlpha, monochrome)
            }
        }
    }
}

/**
 * One player's half of the stage, in the order the art is worth having: the game's own big art,
 * then its icon, then its colour.
 *
 * The icon is drawn small and centred rather than cropped to fill. It is a 64px thumbnail; blown
 * up to a whole stage it is a smear that says less about the game than its colour does (user
 * feedback), so it sits at [ICON_FRACTION] of the shorter edge over the colour instead.
 *
 * Falling through is on failure, not only on absence: a game can have a title screen listed and
 * still fail to load every candidate it has, and the tiers below are exactly as right an answer
 * then as they are for a game that never listed one.
 */
@Composable
private fun BackdropSide(side: BackdropArgs, modifier: Modifier, dimAlpha: Float, monochrome: Boolean = false) {
    Box(modifier) {
        // On a solo chart the colour stands in for art rather than sitting behind it: it is the
        // current GAME's colour, and painting it under every picture made the stage change hue
        // each time the sweep crossed into the next game. On a compare chart it is
        // the PLAYER's colour, which does not change as the journey plays - there it is painted
        // throughout, because saying whose half of the stage this is is the whole point of the
        // split. See [BackdropArgs.tintAlways].
        // Reset per game: a chain that ran out for one game says nothing about the next one's.
        var artExhausted by remember(side.artUrls) { mutableStateOf(false) }
        if (side.tint != Color.Unspecified && (side.tintAlways || side.artUrls.isEmpty() || artExhausted)) {
            // How strongly a game's own colour shows through when there is no art, or none loaded
            // yet: a shade stronger than the art itself, so the colour still reads as a presence
            // rather than a wash once the art is dialed down toward this setting's low end.
            // Capped well under opaque: at "0% dim" (alpha 1) a compare stage's always-on
            // player wash would otherwise turn its half solid colour, hiding the very art the
            // user just asked for at full strength.
            val tintAlpha = (dimAlpha * 1.2f).coerceAtMost(0.5f)
            Box(Modifier.fillMaxSize().background(side.tint.copy(alpha = tintAlpha)))
        }
        if (side.artUrls.isNotEmpty() && !artExhausted) {
            // Fitted, never cropped (user request): the art is a picture of the game, and filling
            // a handheld's landscape stage with a 4:3 title screen threw away its top and bottom.
            BackdropSlot(
                side.artUrls,
                dimAlpha,
                Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                onExhausted = { artExhausted = true },
                monochrome = monochrome,
            )
        } else if (side.iconUrl != null) {
            BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val edge = minOf(maxWidth, maxHeight) * ICON_FRACTION
                BackdropSlot(
                    listOf(side.iconUrl),
                    dimAlpha,
                    Modifier.size(edge),
                    contentScale = ContentScale.Fit,
                    monochrome = monochrome,
                )
            }
        }
    }
}

/**
 * One art slot: two stacked [AsyncImage]s so a change of [urls] (a new current game) crossfades
 * over ~800ms instead of popping, and a load failure silently advances to the next candidate
 * rather than leaving a broken image up. The sequencing of which layer shows what — in particular,
 * holding the outgoing image at full alpha until the incoming one has actually finished loading —
 * is delegated to [BackdropSwapState]; see its kdoc for why.
 */
@Composable
private fun BackdropSlot(
    urls: List<String>,
    targetAlpha: Float,
    modifier: Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    /** Called when every candidate in [urls] has failed to load, so the caller can fall back. */
    onExhausted: () -> Unit = {},
    /** True under the Noir ambience: this slot's art renders black and white. */
    monochrome: Boolean = false,
) {
    val artFilter = if (monochrome) monochromeBackdropFilter else backdropColorFilter
    val chain = remember(urls) { ArtChain(urls) }
    var swap by remember { mutableStateOf(BackdropSwapState()) }

    // Fires whenever `urls`'s identity changes (a new chain), i.e. the sweep has moved onto a
    // different game: target the new chain's first candidate. The previously-displayed image
    // stays at full alpha — untouched — until that candidate reports back as loaded.
    LaunchedEffect(chain) {
        swap = swap.targetChanged(chain.current())
    }

    // Runs after every sequencing change and, once the dust has settled (nothing changed again
    // within one crossfade duration), asks the state machine to tidy up: promote a fully-faded-in
    // front layer onto the back layer, or drop a fully-faded-out back layer. Keyed on `swap`
    // itself so a rapid retarget or a load finishing restarts the wait rather than settling too
    // early against a state that's already moved on.
    LaunchedEffect(swap) {
        delay(CROSSFADE_MILLIS.toLong())
        swap = swap.settle()
    }

    Box(modifier) {
        // Each layer's alpha lives inside its own key(url) block, so a slot taking new content
        // starts from a fresh animation rather than from whatever the outgoing image left behind.
        // Without it, a url arriving on a layer whose alpha was still decaying was painted at that
        // residual alpha the instant Coil answered from its memory cache - one frame of a second
        // game over the first, which is the report this whole file is about.
        swap.backUrl?.let { url ->
            key(url) {
                // The back layer fades OUT (the dissolve, and the graceful exit when a game has no
                // art left to try) and snaps IN, because the only way content enters this slot is
                // [BackdropSwapState.settle] promoting a front layer that is already on screen at
                // full alpha. Animating that direction would fade the image the user is looking at
                // back out and in again.
                val alpha by animateFloatAsState(
                    targetValue = if (swap.backVisible) targetAlpha else 0f,
                    animationSpec = if (swap.backVisible) snap() else tween(CROSSFADE_MILLIS),
                    label = "journey-backdrop-back",
                )
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = contentScale,
                    alpha = alpha,
                    colorFilter = artFilter,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        swap.frontUrl?.let { url ->
            key(url) {
                // The mirror image: the incoming candidate fades IN over the crossfade and snaps to
                // zero the moment it stops being visible, which is always because its content has
                // just been promoted onto the back layer (or never loaded at all).
                val alpha by animateFloatAsState(
                    targetValue = if (swap.frontVisible) targetAlpha else 0f,
                    animationSpec = if (swap.frontVisible) tween(CROSSFADE_MILLIS) else snap(),
                    label = "journey-backdrop-front",
                )
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = contentScale,
                    alpha = alpha,
                    colorFilter = artFilter,
                    modifier = Modifier.fillMaxSize(),
                    onState = { state ->
                        when (state) {
                            is AsyncImagePainter.State.Success -> swap = swap.incomingLoaded(url)
                            is AsyncImagePainter.State.Error -> {
                                swap = swap.incomingFailed(url)
                                val next = if (chain.advance()) chain.current() else null
                                swap = swap.targetChanged(next)
                                // Nothing left to try. The caller has tiers below this one - the
                                // icon, then the colour - and without being told, a game whose art
                                // all 404s left the stage bare: having candidates is not the same
                                // as having art, and only the candidate list was being checked.
                                if (next == null) onExhausted()
                            }
                            else -> Unit
                        }
                    },
                )
            }
        }
    }
}
