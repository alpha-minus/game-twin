package com.twinlore.app.journey.domain

import com.twinlore.app.journey.domain.model.JourneyFrame
import com.twinlore.app.journey.domain.model.JourneyMode
import com.twinlore.app.journey.domain.model.Medal
import com.twinlore.app.journey.domain.model.Unlock
import kotlin.math.roundToInt

/**
 * Decides which icons a chart is allowed to draw.
 *
 * A 689-unlock span drew 689 badge discs into a 1060x600 stage: unreadable, and a relaxation pass
 * quadratic in the icon count. Rather than dropping badges wholesale past a threshold (which is
 * what the old auto "awards only" mode did, and which left the chart with nothing to look at), the
 * chart now always draws badges up to a budget and picks the ones worth showing. The budget itself
 * scales with the journey ([budgetFor]): a long or dense one earns a bigger one, made safe by the
 * renderer's viewport culling, which keeps the *drawn* icon count bounded by the viewport rather
 * than by the budget.
 *
 * 1. every medal, unconditionally. An award is the headline of a journey, and a headline the chart
 *    quietly declines to draw is one nothing on the stage would say had happened - so the budget
 *    does not apply to them at all. It cuts badges to make room instead, and a journey whose awards
 *    alone exceed the whole budget simply draws its awards and no badges;
 * 2. whatever budget is left goes to the main player's highest-scoring unlocks, earliest first on
 *    a tie, so the rare 50-pointers survive and the long tail of 1-pointers does not.
 *
 * Unselected unlocks are not discarded from the journey: they still shape the curve, the backdrop
 * track and every HUD total. They simply do not get an icon.
 *
 * This runs first and answers "how much may this chart draw at all". [GridIconLayout] then answers
 * "how much may one moment of it show", thinning the selection again to a few icons per division of
 * the x axis. The two do not know about each other, so a budget can be spent on badges a full
 * division goes on to drop while badges that would have fitted an empty division were never picked
 * - accepted, and unchanged in kind from when the column cap did the thinning: making the budget
 * grid-aware would tie a question about the journey to a question about the stage it is drawn on.
 */
object IconBudget {

    /** How many badge and medal icons a short, sparse journey may draw. */
    const val TOTAL_ICONS = 200

    /** How many a long or dense one may draw instead, once [budgetFor] raises the cap. */
    const val EXPANDED_ICONS = 400

    /**
     * The absolute ceiling, whatever the width factor multiplies the base up to. Bitmap memory
     * rides the wide layout's budget (badge art is preloaded from it - see JourneyBitmaps), so
     * the scale the extra screenfuls earn has to stay bounded.
     */
    const val WIDE_ICONS_CAP = 600

    /** Above this many days, a journey is "the longer mode" the expanded budget is for. */
    const val LONG_SPAN_DAYS = 183

    /** Above this many of the main player's own unlocks, likewise, regardless of the span. */
    const val LONG_MAIN_UNLOCKS = 300

    private const val DAY_MILLIS = 86_400_000L

    /**
     * The icon budget for [frame]: [TOTAL_ICONS] while it is both short (at most [LONG_SPAN_DAYS])
     * and sparse (the main player has at most [LONG_MAIN_UNLOCKS] unlocks), [EXPANDED_ICONS]
     * otherwise. Viewport culling in the renderer is what makes raising the cap safe - the draw
     * cost a frame actually pays is bounded by what is on screen, not by how many icons the layout
     * placed - so this only has to answer "does the journey deserve more badges", not "can the
     * chart afford to draw them".
     *
     * The span is measured from the journey's own data (every player's unlock timestamps), not
     * from the requested window ([JourneyFrame.spanFromMillis]/[spanToMillis]): those same fields
     * double as an "all time" sentinel (0 = no lower bound) and as the still-open upper bound of an
     * in-progress custom range, neither of which says anything about how much the chart actually
     * has to draw. A player who picked "All time" but only has three unlocks should not pay for a
     * budget sized for a multi-year chart.
     *
     * [widthFactor] is the stage multiple the layout was granted ([LayoutEngine.WIDTH_FACTORS]):
     * a widened timeline has two to four screenfuls of room, so it earns proportionally more
     * badges, up to [WIDE_ICONS_CAP]. The compact chart passes 1 and keeps the base budget -
     * which is what hides the extras again when the player collapses to the dense view, and what
     * keeps the compact selection a prefix of the wide one ([select]'s ordering is deterministic,
     * so the containment invariant holds by construction).
     */
    fun budgetFor(frame: JourneyFrame, widthFactor: Float = 1f): Int {
        // Single-game mode has no budget at all. The chart there is one game's achievement list,
        // and every achievement in it is the point: a badge dropped from a span of forty games is
        // one of hundreds the eye never misses, while a badge dropped from the game the chart is
        // *about* is a hole in the subject. What used to make that unaffordable was drawing every
        // icon at one size; the adaptive tiers draw the standard tail at half the fitting radius,
        // so a normal game's worth of unlocks fits the stage it is given (see [GridIconLayout]).
        if (frame.mode == JourneyMode.GAME) return uncappedCount(frame)

        val allUnlocks = (listOf(frame.main) + frame.rivals).flatMap { it.unlocks }
        val spanDays = if (allUnlocks.size < 2) {
            0L
        } else {
            (allUnlocks.maxOf { it.earnedAtMillis } - allUnlocks.minOf { it.earnedAtMillis }) / DAY_MILLIS
        }
        val mainUnlocks = frame.main.unlocks.size
        val base = if (spanDays <= LONG_SPAN_DAYS && mainUnlocks <= LONG_MAIN_UNLOCKS) {
            TOTAL_ICONS
        } else {
            EXPANDED_ICONS
        }
        return (base * widthFactor.coerceAtLeast(1f)).roundToInt().coerceAtMost(WIDE_ICONS_CAP)
    }

    /**
     * How many icons a chart for [frame] is asking to place: every medal on the stage plus the
     * badges [budgetFor]'s cap leaves room for, which is exactly the size of what [select] returns.
     *
     * Medals are added outside the cap rather than inside it, exactly as [select] treats them, so a
     * journey with more awards than budget reports more than the budget - which is the honest answer
     * to "how much is this chart asking to draw" and is what this feeds.
     *
     * This is the *candidate* count, before [GridIconLayout] thins it per division, which is what
     * makes it the right signal for [LayoutEngine.widthFactorFor]: how much room a journey is
     * asking for, not how much of it survives being drawn on the room it was given.
     */
    fun iconCountFor(frame: JourneyFrame): Int {
        val medals = medalCount(frame)
        val forBadges = (budgetFor(frame) - medals).coerceAtLeast(0)
        return medals + minOf(forBadges, frame.main.unlocks.size)
    }

    /** Every icon [frame] could ask for: all the medals on the stage plus the main player's badges. */
    private fun uncappedCount(frame: JourneyFrame): Int = medalCount(frame) + frame.main.unlocks.size

    /** Awards on the stage: the main player's and every rival's. */
    private fun medalCount(frame: JourneyFrame): Int =
        frame.main.medals.size + frame.rivals.sumOf { it.medals.size }

    /**
     * Indices into the lists passed to [select], both ascending so callers can keep their own
     * ordering (curve anchors, chronological medals) without re-sorting.
     */
    data class Selection(val medalIndices: List<Int>, val unlockIndices: List<Int>)

    /**
     * Which icons the chart may draw: every award, plus the highest-scoring badges the budget has
     * room for after them.
     *
     * The cap is a cap on BADGES. Awards are the one thing on this chart that cannot be inferred
     * from anything else drawn on it - a badge left out of a span of forty games is a thumbnail
     * nobody counts, while an award left out is a fact about the journey that has silently gone
     * missing - so they are never rationed, in any mode, at any density. A journey whose awards
     * alone exceed the whole budget draws its awards and no badges, which is the right end of that
     * trade: the icons it is left with are the ones it would have chosen first anyway.
     *
     * Both index lists come back ascending, so callers can index straight back into their own
     * (curve-anchor, chronological) ordering without re-sorting.
     */
    fun select(
        medals: List<Medal>,
        unlocks: List<Unlock>,
        budget: Int = TOTAL_ICONS,
    ): Selection {
        val cap = budget.coerceAtLeast(0)
        val medalIndices = medals.indices.toList()

        val remaining = (cap - medalIndices.size).coerceAtLeast(0)
        val unlockIndices = if (unlocks.size <= remaining) {
            unlocks.indices.toList()
        } else {
            // Coverage rounds, then points. A global top-by-points pick spent the whole budget on
            // the dense clusters and left a quiet month of timeline without a single badge (UI
            // feedback: the wide view's long empty stretches). The timeline is cut into
            // [COVERAGE_BUCKETS] slices of the unlocks' own extent; round 0 is every slice's best
            // unlock, round 1 everybody's second, and the budget is spent in round order with
            // points deciding inside a round - so a lone 2-pointer in an empty fortnight beats a
            // cluster's fiftieth badge, and a spread-out journey (one unlock per slice, all round
            // 0) keeps the pure points order this replaces. The order does not depend on the
            // budget, so the compact chart's smaller pick stays a prefix of the wide one's and
            // the containment invariant holds by construction.
            val first = unlocks.minOf { it.earnedAtMillis }
            val span = (unlocks.maxOf { it.earnedAtMillis } - first).coerceAtLeast(1)
            fun bucketOf(i: Int): Int =
                (((unlocks[i].earnedAtMillis - first) * COVERAGE_BUCKETS) / (span + 1)).toInt()
                    .coerceIn(0, COVERAGE_BUCKETS - 1)
            val inBucket = compareByDescending<Int> { unlocks[it].points }
                .thenBy { unlocks[it].earnedAtMillis }
                .thenBy { it }
            val round = IntArray(unlocks.size)
            unlocks.indices.groupBy(::bucketOf).values.forEach { bucket ->
                bucket.sortedWith(inBucket).forEachIndexed { r, i -> round[i] = r }
            }
            unlocks.indices
                .sortedWith(compareBy<Int> { round[it] }.then(inBucket))
                .take(remaining)
                .sorted()
        }

        return Selection(medalIndices = medalIndices, unlockIndices = unlockIndices)
    }

    /**
     * How many slices the coverage pass cuts the timeline into; see [select]. Coarse enough that
     * a real cluster shares a slice, fine enough that a badge every ~2% of the axis reads as an
     * inhabited chart.
     */
    private const val COVERAGE_BUCKETS = 48
}
