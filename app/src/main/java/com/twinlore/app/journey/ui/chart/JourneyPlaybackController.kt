package com.twinlore.app.journey.ui.chart

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.twinlore.app.journey.domain.LayoutEngine
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.yield

/**
 * How long one screenful of chart takes to cross at 1x speed, before the speed control divides
 * into it. A whole journey is this times however many screenfuls it was given - see
 * [journeyPlaybackMillis].
 */
const val JOURNEY_PLAYBACK_MILLIS = 15_000f

/**
 * Where the calendar-span slowdown starts and where it tops out. A journey whose axis covers at
 * most [SPAN_SLOW_START_MILLIS] plays at the base pace; [SPAN_SLOW_FULL_MILLIS] and beyond earn
 * the full [SPAN_SLOW_MAX_FACTOR], with a linear ramp between. Ten years at the base pace ran a
 * month of dates under the sweep every sixth of a second, which read as the clock spinning rather
 * than time passing; doubling the pass makes a decade play the way 0.5x used to, while the speed
 * buttons stay a multiplier on top.
 */
private const val YEAR_MILLIS = 365L * 86_400_000L
const val SPAN_SLOW_START_MILLIS = 2L * YEAR_MILLIS
const val SPAN_SLOW_FULL_MILLIS = 10L * YEAR_MILLIS
const val SPAN_SLOW_MAX_FACTOR = 2f

/**
 * A full playback pass for a chart [layoutWidth] virtual units wide: 15 seconds per screenful, so
 * a 4x timeline takes a minute and a 1x one is unchanged.
 *
 * A fixed 15 seconds meant the reveal's *pace* was a function of how much journey there was. A
 * dense span earns three or four screenfuls of stage ([LayoutEngine.widthFactorFor]),
 * and cramming those into the same quarter of a minute ran the sweep across them three or four
 * times as fast - which is exactly the journey with the most on it to look at, and the one the
 * follow camera is panning across. Scaling with the width holds the traversal rate constant, so
 * playback reads the same whatever the journey turned out to be, and the speed buttons stay what
 * they always were: a multiplier on top.
 *
 * [axisSpanMillis] is the second axis of "how much journey": the calendar the sweep crosses. The
 * width factor keys off icon density, so a decade of steady play on the compact chart was still a
 * 15-second pass - see [SPAN_SLOW_START_MILLIS]. Zero (a race chart, whose axis is elapsed time,
 * or a caller with no calendar) leaves the pace alone.
 */
fun journeyPlaybackMillis(
    layoutWidth: Float,
    axisSpanMillis: Long = 0L,
    screenfulMillis: Float = JOURNEY_PLAYBACK_MILLIS,
): Float {
    val factor = (layoutWidth / LayoutEngine.W).coerceAtLeast(1f)
    val ramp = (axisSpanMillis - SPAN_SLOW_START_MILLIS).toFloat() /
        (SPAN_SLOW_FULL_MILLIS - SPAN_SLOW_START_MILLIS).toFloat()
    val spanFactor = 1f + (SPAN_SLOW_MAX_FACTOR - 1f) * ramp.coerceIn(0f, 1f)
    return screenfulMillis * factor * spanFactor
}

/**
 * The journey chart's playback state machine, deliberately kept out of the composable so it can
 * be driven by a unit test with a virtual frame clock.
 *
 * Two things here are load bearing:
 *
 * 1. **Playback survives being interrupted.** The scrub slider and the replay button write
 *    straight into the same [Animatable], and [Animatable.snapTo] evicts a running `animateTo`
 *    through the shared `MutatorMutex` by throwing `MutationInterruptedException` (a
 *    [CancellationException]) at the animating call. That does *not* cancel the coroutine that
 *    called it, so [play] catches it, re-reads the position and carries on. Letting it escape is
 *    what previously killed playback for good: the `LaunchedEffect` driving it ended, and its
 *    keys had not changed, so it never relaunched.
 * 2. **Progress is reported coarsely.** The shell owns the progress value, so every report
 *    recomposes the screen (and invalidates the chart's semantics node). Reporting every
 *    animation frame meant 60 recompositions a second; [REPORT_STEP] cuts that to about seven,
 *    while the canvas keeps reading [progress] directly in the draw phase for a smooth reveal.
 */
class JourneyPlaybackController(initialProgress: Float = 0f) {

    val animatable = Animatable(initialProgress.coerceIn(0f, 1f))

    /** Where playback is right now. Reading this inside a draw lambda invalidates draw only. */
    val progress: Float get() = animatable.value

    /** The last value pushed to the shell; also how an echo is told apart from a real scrub. */
    var reported: Float = initialProgress.coerceIn(0f, 1f)
        private set

    /** Bumped whenever the reveal restarts or jumps backwards, so badge pop-in can replay. */
    var popEpoch by mutableIntStateOf(0)
        private set

    /**
     * Bumped only by [restart]: the journey is genuinely starting over, rather than the sweep
     * having been dragged backwards. Anything that belongs to the chart the user was looking at -
     * the zoom they framed, where the camera had got to - is reset on this and not on [popEpoch],
     * which a scrub to the left also bumps.
     */
    var replayEpoch by mutableIntStateOf(0)
        private set

    /**
     * Mirrors an external scrub (slider drag, replay) into the animation. Returns false for the
     * echo of a value this controller itself reported, which is most of what the shell sends.
     */
    suspend fun scrubTo(value: Float): Boolean {
        val target = value.coerceIn(0f, 1f)
        // The tolerance matches the reporting granularity: anything within one [REPORT_STEP] of
        // what we last pushed is, at most, a late echo of our own value. A tighter window let an
        // echo that arrived a frame behind read as a backwards scrub and bump popEpoch mid-play.
        //
        // Backwards is judged BEFORE that, and against the animation rather than the report: a
        // drag of less than one report step is still a drag, and returning early on it left the
        // settled-icon layer holding badges in front of the reveal line, where they stayed until
        // something else invalidated the layer. Cheap to be right about - the epoch only costs a
        // rebuild when it actually moves.
        val backwards = target < animatable.value - BACKWARD_EPSILON
        if (backwards) popEpoch++
        if (abs(target - reported) <= REPORT_STEP && !backwards) return false
        reported = target
        animatable.snapTo(target)
        return true
    }

    /** Jumps to the finished chart (reduced motion, or playback that is being skipped). */
    suspend fun jumpToEnd(onProgress: (Float) -> Unit) {
        if (animatable.value >= 1f && reported >= 1f) return
        animatable.snapTo(1f)
        report(1f, onProgress, force = true)
    }

    /** Restarts the reveal from zero and lets the badges pop in again. */
    suspend fun restart(onProgress: (Float) -> Unit) {
        popEpoch++
        replayEpoch++
        animatable.snapTo(0f)
        report(0f, onProgress, force = true)
    }

    /**
     * Runs the reveal to the end, resuming through any interruption, and returns true when it
     * actually reached 1 (the caller then tells the shell playback is over). Cancelling the
     * calling coroutine - which is how pausing works - stops it and rethrows.
     */
    suspend fun play(
        speed: Float,
        totalMillis: Float = JOURNEY_PLAYBACK_MILLIS,
        onProgress: (Float) -> Unit,
    ): Boolean {
        var first = true
        while (currentCoroutineContext().isActive) {
            if (first && animatable.value >= 1f) restart(onProgress)
            first = false

            val remaining = (1f - animatable.value).coerceIn(0f, 1f)
            if (remaining <= 0f) {
                report(1f, onProgress, force = true)
                return true
            }
            val duration = (remaining * totalMillis / speed.coerceAtLeast(MIN_SPEED))
                .toInt().coerceAtLeast(1)
            try {
                animatable.animateTo(1f, tween(durationMillis = duration, easing = LinearEasing)) {
                    report(value, onProgress)
                }
            } catch (interrupted: CancellationException) {
                // A scrub or a replay snapped the animation out from under us. Our own coroutine
                // is untouched, so pick up the new position and keep playing; a genuine
                // cancellation (pause, leaving the screen) clears isActive and rethrows.
                if (!currentCoroutineContext().isActive) throw interrupted
                yield()
                continue
            }
            report(animatable.value, onProgress, force = true)
            return animatable.value >= 1f
        }
        return false
    }

    private fun report(value: Float, onProgress: (Float) -> Unit, force: Boolean = false) {
        if (!force && abs(value - reported) < REPORT_STEP) return
        reported = value
        onProgress(value)
    }

    private companion object {
        const val REPORT_STEP = 0.01f

        /**
         * Below this, "backwards" is float noise rather than a drag: [scrubTo] is handed values
         * that have been through a slider and a coerce, and treating a rounding difference as a
         * scrub would rebuild the settled-icon layer on ordinary echoes.
         */
        const val BACKWARD_EPSILON = 0.0005f
        const val MIN_SPEED = 0.1f
    }
}
