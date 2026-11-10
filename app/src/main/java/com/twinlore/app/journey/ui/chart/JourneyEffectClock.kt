package com.twinlore.app.journey.ui.chart

import com.twinlore.app.journey.domain.model.AwardKind
import kotlin.math.max

/**
 * Remembers the instant each badge and medallion was first revealed, so the renderer can drive the
 * pop-in, the floating "+N", the confetti burst and the award flash from one clock.
 *
 * Two of those four are throttled - the "+N" ([nodePointsSeconds]) and the flash
 * ([medalWashSeconds]) - each against its own permanent suppressed set, so a dense span reveals
 * every badge and every medallion but does not narrate every single one of them. The pop-in and the
 * confetti are not: an icon that never grows in or never throws a piece of confetti reads as
 * missing, not as quieter.
 *
 * Deliberately *not* snapshot state: the draw pass is what discovers that an icon has appeared, and
 * writing observable state from a draw pass is how you get an invalidation loop. Nothing here is
 * read during composition; the composable keeps frames coming with a separate tick, and asks
 * [pendingAt] whether it still needs to.
 *
 * One clock per reveal: the chart rebuilds it whenever playback restarts, so a replay pops every
 * badge in again.
 */
class JourneyEffectClock {

    private val nodeAt = HashMap<Int, Long>()
    private val medalAt = HashMap<Int, Long>()

    // The award flash's own bookkeeping: when each flash that actually fired started, which medals
    // lost theirs to the cooldown, and when the last one fired. Plain state, like everything else
    // here, and rebuilt with the clock itself on a replay so a second watch flashes the same way
    // the first one did.
    private val washAt = HashMap<Int, Long>()
    private val washSuppressed = HashSet<Int>()
    private var lastWashNanos: Long? = null
    // A mastery's own cooldown clock, separate from [lastWashNanos] - see [medalWashSeconds] for
    // why a mastery is judged against this one instead of the shared one.
    private var lastMasteryWashNanos: Long? = null

    // The "+N" pop's own bookkeeping, same shape as the flash's for the same reason: which badges
    // actually popped, which lost theirs to the cooldown, and when the last one fired. A SEPARATE
    // map from [nodeAt] on purpose - see [nodePointsSeconds].
    private val pointsAt = HashMap<Int, Long>()
    private val pointsSuppressed = HashSet<Int>()
    private var lastPointsNanos: Long? = null

    /** Frame time after which nothing recorded so far can still be animating. */
    var busyUntilNanos: Long = 0L
        private set

    /**
     * Seconds since node [index] was revealed, or -1 when it has not been (and [arm] says this is
     * not the moment to start it, which is what pausing and reduced motion do).
     *
     * Feeds the pop-in ([JourneyChartRenderer.popProgress]) and is deliberately never throttled:
     * every badge that is drawn at all must grow in, or a suppressed one sits on the curve at full
     * size having never appeared, which reads as a missing icon rather than a quieter one. The
     * floating "+N" used to ride this same clock and is not throttled here any more - see
     * [nodePointsSeconds] for why it needed its own.
     */
    fun nodeSeconds(index: Int, now: Long, arm: Boolean): Float =
        seconds(nodeAt, index, now, arm, NODE_EFFECT_NANOS)

    /** Seconds since medal [index] was revealed, or -1. See [nodeSeconds]. */
    fun medalSeconds(index: Int, now: Long, arm: Boolean): Float =
        seconds(medalAt, index, now, arm, MEDAL_EFFECT_NANOS)

    /**
     * Seconds since medal [index]'s full-stage award flash fired, or -1 when it has not fired and
     * will not.
     *
     * The flash is throttled where the confetti and the pop are not. A span full of awards reveals
     * medals in clusters - three mastered games inside a second is ordinary - and one full-stage
     * tint per medal turns the end of a journey into a strobe. A flash therefore only starts if
     * [JourneyChartRenderer.WASH_COOLDOWN_SECONDS] have passed since the last one *fired*, not
     * since the last award; a medal that loses its flash is remembered as suppressed so a later
     * frame cannot hand it one out of time.
     *
     * One exception: a `MASTERED` award is judged against [lastMasteryWashNanos], its own cooldown
     * clock, rather than [lastWashNanos], the shared one every other kind uses. A mastery is the
     * single biggest moment the whole feature exists to celebrate, and losing it because a rival's
     * beaten-casual award happened to land a second earlier was the worst trade the shared pool
     * could make - the report that started this was exactly that. It still respects a cooldown
     * against OTHER masteries, though: three mastered games inside a second is ordinary on a busy
     * span, and three full-strength flashes back to back is precisely the strobe this cooldown
     * exists to prevent, mastery or not. So a mastery always beats a lesser award's cooldown, but
     * not another mastery's.
     */
    fun medalWashSeconds(index: Int, kind: AwardKind, now: Long, arm: Boolean): Float {
        if (index in washSuppressed) return -1f
        val start = washAt[index] ?: run {
            if (!arm) return -1f
            val isMastery = kind == AwardKind.MASTERED
            val last = if (isMastery) lastMasteryWashNanos else lastWashNanos
            if (last != null && now - last < WASH_COOLDOWN_NANOS) {
                washSuppressed += index
                return -1f
            }
            washAt[index] = now
            lastWashNanos = now
            if (isMastery) lastMasteryWashNanos = now
            busyUntilNanos = max(busyUntilNanos, now + MEDAL_EFFECT_NANOS)
            now
        }
        return (now - start) / NANOS_PER_SECOND
    }

    /**
     * Seconds since badge [index]'s floating "+N" started rising, or -1 when it has not started and
     * will not.
     *
     * A separate clock entry from [nodeSeconds] on purpose, not a second reading of the same one.
     * The two used to share [nodeAt] - every badge that popped in also threw its "+N" off the same
     * timestamp - which was fine right up until this needed a cooldown: throttling [nodeSeconds]
     * itself would have frozen a suppressed badge's pop-in along with its number, so it never grows
     * to size at all, which reads as a badge that failed to load rather than one whose number was
     * skipped. Splitting the bookkeeping means the pop-in stays [nodeSeconds]'s alone, unthrottled,
     * and only the number rides this cooldown.
     *
     * Shaped exactly like [medalWashSeconds]: a badge only starts a "+N" if
     * [JourneyChartRenderer.FLOATING_POINTS_COOLDOWN_SECONDS] have passed since the last one that
     * actually *fired*, not since the last badge revealed, and a badge that loses its slot is
     * remembered as suppressed so a later frame cannot hand it one out of order - see
     * [medalWashSeconds]'s own doc for why that matters as much here as it does there.
     */
    fun nodePointsSeconds(index: Int, now: Long, arm: Boolean): Float {
        if (index in pointsSuppressed) return -1f
        val start = pointsAt[index] ?: run {
            if (!arm) return -1f
            val last = lastPointsNanos
            if (last != null && now - last < FLOATING_POINTS_COOLDOWN_NANOS) {
                pointsSuppressed += index
                return -1f
            }
            pointsAt[index] = now
            lastPointsNanos = now
            busyUntilNanos = max(busyUntilNanos, now + NODE_EFFECT_NANOS)
            now
        }
        return (now - start) / NANOS_PER_SECOND
    }

    /** Whether some recorded effect is still in flight at [now]. */
    fun pendingAt(now: Long): Boolean = now < busyUntilNanos

    private fun seconds(
        into: HashMap<Int, Long>,
        index: Int,
        now: Long,
        arm: Boolean,
        spanNanos: Long,
    ): Float {
        val start = into[index]
            ?: if (!arm) {
                return -1f
            } else {
                into[index] = now
                busyUntilNanos = max(busyUntilNanos, now + spanNanos)
                now
            }
        return (now - start) / NANOS_PER_SECOND
    }

    private companion object {
        const val NANOS_PER_SECOND = 1_000_000_000f
        /** Longest node effect: the pop-in settles well before the "+N" has finished rising. */
        val NODE_EFFECT_NANOS = (JourneyChartRenderer.FLOATING_POINTS_SECONDS * 1e9f).toLong()
        /** Longest medal effect: the award wash outlives the confetti. */
        val MEDAL_EFFECT_NANOS = (JourneyChartRenderer.MEDAL_WASH_SECONDS * 1e9f).toLong()
        /** Quiet time the stage gets between two award flashes. */
        val WASH_COOLDOWN_NANOS = (JourneyChartRenderer.WASH_COOLDOWN_SECONDS * 1e9f).toLong()
        /** Quiet time the stage gets between two "+N" pops. */
        val FLOATING_POINTS_COOLDOWN_NANOS =
            (JourneyChartRenderer.FLOATING_POINTS_COOLDOWN_SECONDS * 1e9f).toLong()
    }
}
