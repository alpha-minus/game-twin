package com.twinlore.app.journey.domain

import com.twinlore.app.journey.domain.model.AchievementType

/**
 * Rations the chart's icons by the grid the chart already draws.
 *
 * The x axis is cut into the divisions the gridlines mark out. Each division then carries as many
 * icons as actually *fit* above it: icons are added in priority order (awards, then win conditions,
 * then the achievements worth calling out, then the rest) while their stacked height still fits the
 * plot, and whatever is left over is not drawn. A count cap said "four icons, whatever size they
 * are"; a height budget says "as many as the plot can hold", which is the same guarantee for a
 * division of medals and a far better answer for a division of ordinary badges.
 *
 * Size comes from what an achievement *is* rather than from a continuous spread of what it scored
 * (see [Tier]): an award is a full [fitRadius], a win condition slightly less, a progression, a
 * missable or a top-quartile scorer less again, and the standard long tail is drawn at half. The
 * hierarchy is what makes the height budget worth having - four full-size discs and nine
 * half-size ones cover the same column, so a quiet stretch of standard unlocks now shows all of
 * itself instead of being thinned to the same four icons a medal-heavy moment gets.
 *
 * Every function here is pure and works in the chart's virtual x units, so the caller decides what
 * the boundaries are (calendar ticks, elapsed ticks, or the fallback) and this decides nothing
 * about time.
 */
object GridIconLayout {

    /**
     * The nominal stack the fitting radius is sized against.
     *
     * Not a cap on what a division draws - [selectPerCell]'s height budget is - but the answer to
     * "how big may one icon be". Four rather than ten: ten filled the plot's height with a single
     * moment's worth of badges, which is a wall rather than a chart, and it forced every icon on
     * the stage down to a size the art in it could not survive. Four leaves the fitting radius free
     * to rise to [R_MAX], and the tier scales below then buy the extra capacity back.
     */
    const val SIZING_CELL_ICONS = 4

    /**
     * A sanity bound on how many icons one division may draw, for the case the height budget alone
     * would not bind: at the size floor a column of half-size discs would take more than twenty,
     * which is a wall of thumbnails however well it fits.
     */
    const val MAX_PER_CELL = 16

    /**
     * The largest an icon may get. Without a ceiling a chart of three badges would size them to a
     * third of the plot's height each and read as three billboards rather than as a journey.
     */
    const val R_MAX = 44f

    /** The smallest an icon may get: below this the game art in it is no longer recognisable. */
    const val R_MIN = 8f

    /**
     * Room left between two stacked icons when the fitting radius is worked out.
     *
     * This is deliberately the same separation the relaxation pass keeps between any two icons
     * (`LayoutEngine`'s collision margin), so that "a full division fills the plot's height" describes
     * the icons as they are actually placed rather than as bare touching discs. Sized against a
     * smaller gap, a full division's stack would want more room than the plot has and would spill
     * into the headroom above it.
     */
    const val STACK_GAP = 7f

    /**
     * How many equal divisions to fall back to when the axis has too few gridlines to cut it up.
     * Matches what [TickGenerator] aims for, so the fallback grid looks like the real one.
     */
    const val FALLBACK_DIVISIONS = 11

    /**
     * Below this many tick-derived divisions the grid is degenerate - a one-session elapsed axis can
     * produce two ticks or none - and [divisions] falls back to [FALLBACK_DIVISIONS] equal ones.
     * Rationing a whole chart into one or two buckets would drop almost everything.
     */
    const val MIN_TICK_DIVISIONS = 3

    private const val EPSILON = 0.01f

    /**
     * One icon as the grid sees it: where it sits along x, whether it is an award, what it is worth
     * and what kind of achievement it was. [key] is the caller's own handle on the icon and doubles
     * as the tie-break, so two badges of equal points are always dropped in the same order.
     *
     * [type] is null for medals, which have no achievement type of their own.
     */
    data class Icon(
        val key: Int,
        val x: Float,
        val isMedal: Boolean,
        val points: Int,
        val type: AchievementType? = null,
        /**
         * Whose badge this is on a compare chart (null for the main player, and for every icon on
         * a solo one). [selectPerCell] shares a crowded division out between owners rather than
         * letting the highest scorer take the lot; see the round-robin there.
         */
        val owner: String? = null,
    )

    /**
     * How big an icon is drawn and how hard it is to displace, as one idea: the scale is a fraction
     * of the fitting radius, and the declaration order is the priority order a crowded division
     * fills up in.
     *
     * - [MEDAL] is an award, the headline of a journey, and is drawn at the full fitting radius.
     * - [WIN] is a win condition: the achievement that says the game was beaten.
     * - [HIGH] is everything else worth calling out - a progression step, a missable, or a standard
     *   achievement in the top quartile of what this chart's badges scored (see
     *   [highPointThreshold]).
     * - [STANDARD] is the long tail, at half the fitting radius, which is what lets a quiet
     *   division draw nine of them where it used to draw four.
     */
    enum class Tier(val scale: Float) {
        MEDAL(1f),
        WIN(0.85f),
        HIGH(0.7f),
        STANDARD(0.5f),
    }

    /** One icon the grid decided to draw, with the size its tier gives it. */
    data class Placed(val icon: Icon, val tier: Tier, val radius: Float)

    /**
     * The division boundaries for a stage stretched to [widthFactor] screenfuls: about
     * [FALLBACK_DIVISIONS] divisions per screenful, reached by splitting each of the axis's own
     * divisions into equal parts.
     *
     * At one screenful the grid is exactly the one the axis draws, so what the eye reads as a
     * division and what rations the icons are the same thing (see [divisions]). Past that they have
     * to part company: the gridlines are a function of the time span alone, so a stretched stage
     * handed the same eleven-ish buckets would draw the same icons further apart and the extra width
     * would buy no capacity at all - which is the one thing the stretch exists for.
     *
     * Splitting the existing divisions rather than laying down a fresh set of equal ones is what
     * makes the widened grid a *refinement* of the compact one: every boundary the compact chart has
     * is still a boundary here, so every wide cell sits inside exactly one compact cell. Two
     * properties follow, and both are load-bearing rather than incidental:
     *
     * - a wide chart never draws fewer icons than the compact one, which is the whole point of
     *   giving it the room;
     * - the compact chart's icons are always a *subset* of the wide chart's. A badge among the best
     *   few of a compact cell is among the best few of the smaller cell it lands in, since that
     *   cell holds a subset of the same icons. This matters because the badge art is preloaded from
     *   the wide layout while the export and the still preview draw the compact one: a compact-only
     *   badge would be drawn with art nobody ever requested.
     *
     * Independent equal divisions have neither property. Measured over 336 journey shapes they left
     * the compact chart drawing icons the wide one lacked in 124 of them (up to 60 badges at a
     * time), and at 1.5 screenfuls a chart whose axis already had 18 gridlines was cut into 17
     * coarser divisions and drew *fewer* icons than the compact one it was widened from.
     */
    fun divisionsFor(tickXs: List<Float>, x0: Float, x1: Float, widthFactor: Float): List<Float> {
        val base = divisions(tickXs, x0, x1)
        if (widthFactor <= 1f + EPSILON) return base
        val baseCells = base.size - 1
        // A plot with no width has nothing to refine, and splitting it would hand [cellOf] a run of
        // identical boundaries naming divisions no icon can ever be in.
        if (baseCells < 1 || base.last() - base.first() <= EPSILON) return base

        val target = Math.round(FALLBACK_DIVISIONS * widthFactor)
        val split = Math.round(target.toFloat() / baseCells).coerceAtLeast(1)
        if (split == 1) return base

        val out = ArrayList<Float>(baseCells * split + 1)
        out += base.first()
        for (i in 0 until baseCells) {
            val lo = base[i]
            val hi = base[i + 1]
            for (k in 1..split) out += lo + (hi - lo) * k / split
        }
        return out
    }

    /**
     * The division boundaries for a plot running from [x0] to [x1] with gridlines at [tickXs]:
     * the two edges plus whichever ticks fall strictly between them, ascending.
     *
     * Ticks on or outside an edge are dropped rather than kept as zero-width cells, and ticks that
     * land on the same x are collapsed. When what is left cannot make [MIN_TICK_DIVISIONS]
     * divisions the axis is cut into [FALLBACK_DIVISIONS] equal ones instead.
     */
    fun divisions(tickXs: List<Float>, x0: Float, x1: Float): List<Float> {
        val lo = minOf(x0, x1)
        val hi = maxOf(x0, x1)
        if (hi - lo <= EPSILON) return listOf(lo, hi)

        val interior = ArrayList<Float>(tickXs.size)
        for (x in tickXs.sorted()) {
            if (x <= lo + EPSILON || x >= hi - EPSILON) continue
            if (interior.isNotEmpty() && x - interior.last() <= EPSILON) continue
            interior += x
        }
        val fromTicks = ArrayList<Float>(interior.size + 2)
        fromTicks += lo
        fromTicks += interior
        fromTicks += hi
        if (fromTicks.size - 1 >= MIN_TICK_DIVISIONS) return fromTicks

        return equalDivisions(lo, hi, FALLBACK_DIVISIONS)
    }

    /** [count] equally wide divisions between [lo] and [hi], as [count] + 1 boundaries. */
    fun equalDivisions(lo: Float, hi: Float, count: Int): List<Float> {
        val n = count.coerceAtLeast(1)
        return (0..n).map { lo + (hi - lo) * it / n }
    }

    /**
     * Which division of [boundaries] an icon at [x] belongs to. [boundaries] must be ascending and
     * free of duplicates, which is what [divisions] and [equalDivisions] both return; the scan
     * stops at the first boundary [x] is left of, so an unsorted list would answer nonsense and a
     * repeated boundary would name a division no icon can ever be in.
     *
     * A cell owns its left edge and not its right one, so an icon landing exactly on a gridline
     * always goes to the cell on the right of it - one deterministic answer rather than a coin
     * flip that would make two runs of the same chart drop different badges. Anything left of the
     * first boundary or right of the last belongs to the nearest end cell.
     */
    fun cellOf(x: Float, boundaries: List<Float>): Int {
        val cells = boundaries.size - 1
        if (cells <= 1) return 0
        for (i in 0 until cells) {
            if (x < boundaries[i + 1]) return i
        }
        return cells - 1
    }

    /**
     * The score a standard achievement has to *beat* to count as high-point, i.e. the top of the
     * third quartile of [points] by the nearest-rank method.
     *
     * Strictly greater rather than at-least, which is what makes the answer mean something on the
     * shapes achievement scores actually come in. A chart of `[1, 1, 1, 100]` has a nearest-rank
     * 75th percentile of 1; promoting everything at or above it would promote the whole chart,
     * while promoting what beats it promotes the 100 - the one badge anybody would point at. A
     * chart where every badge scored the same promotes nothing, which is the honest answer to
     * "which of these is in the top quartile".
     *
     * The distribution is the standard badges' own (see [selectPerCell]), not every badge's. The
     * question this answers is "which of the ordinary achievements is worth promoting out of the
     * tail", and the progressions and win conditions are already out of it: leaving them in lifts
     * the threshold by their own scores, which are systematically higher, and the promotion then
     * goes to fewer standard badges than a quarter of them.
     */
    fun highPointThreshold(points: List<Int>): Int {
        if (points.isEmpty()) return Int.MAX_VALUE
        val sorted = points.sorted()
        // Nearest rank: ceil(0.75 * n), one-based, which for n = 4 is the third value.
        val rank = ((sorted.size * 3 + 3) / 4).coerceIn(1, sorted.size)
        return sorted[rank - 1]
    }

    /**
     * Which [Tier] an icon belongs to. [highPointThreshold] is the value from [highPointThreshold]
     * over the points of every badge the chart is considering, so "top quartile" is a fact about
     * this chart rather than about RetroAchievements at large.
     */
    fun tierOf(icon: Icon, highPointThreshold: Int): Tier = when {
        icon.isMedal -> Tier.MEDAL
        icon.type == AchievementType.WIN_CONDITION -> Tier.WIN
        icon.type == AchievementType.PROGRESSION || icon.type == AchievementType.MISSABLE -> Tier.HIGH
        icon.points > highPointThreshold -> Tier.HIGH
        else -> Tier.STANDARD
    }

    /**
     * How big a [tier]'s icon is drawn on a chart whose fitting radius is [fitRadius], never below
     * [rMin]: shrinking a floored icon further would take the art in it past being recognisable,
     * and on a chart already at the floor every tier simply draws the same size.
     */
    fun radiusFor(tier: Tier, fitRadius: Float, rMin: Float = R_MIN): Float =
        (fitRadius * tier.scale).coerceAtLeast(minOf(rMin, fitRadius))

    /**
     * The count the fitting radius is sized against: what the busiest division would stack if every
     * icon in it were drawn at full size.
     *
     * Per division it is [nominal] icons, or the division's own medals when there are more of them
     * than that. Medals are the one thing that can genuinely fill a column - they are first in
     * priority and they are all the same size - so a moment that carries twelve awards has to size
     * the whole chart down until twelve of them fit, exactly as it always did. Badges never push
     * this up: they are rationed by the height budget instead, which is what lets a division draw
     * more of them than [nominal] without shrinking every icon on the stage to make room.
     */
    fun sizingCellCount(
        icons: List<Icon>,
        boundaries: List<Float>,
        nominal: Int = SIZING_CELL_ICONS,
    ): Int {
        if (icons.isEmpty()) return 1
        return icons.groupBy { cellOf(it.x, boundaries) }
            .values
            .maxOf { cell -> maxOf(cell.count { it.isMedal }, minOf(cell.size, nominal)) }
            .coerceAtLeast(1)
    }

    /**
     * Which icons each division actually draws, and how big.
     *
     * Per division, icons are taken in priority order - [Tier] first, then points descending, then
     * [Icon.key] - while their stacked height (the diameters plus a [gap] between each pair) still
     * fits [innerH]. The first icon that does not fit ends the division: what is left is dropped,
     * budget and all, which is the same wasted-budget bargain the count cap made and for the same
     * reason (the order is the priority order, so what follows a dropped icon is worth less than
     * it). At least one icon per division always survives, however tall it is against however short
     * a plot.
     *
     * The ordering is the selection guarantee: the highest scorers of a crowded moment are the ones
     * that survive it, and an award or a win condition survives a badge whatever either scored. On
     * a chart with more than one owner ([Icon.owner]) the division is filled a round at a time
     * instead, so every player present in it keeps a badge before any player gets a second.
     *
     * Medals are the one thing the budget cannot drop, unchanged from when a count cap did the
     * thinning: an award is the headline of a journey, and a chart that hides one because the week
     * it was won was busy is answering the wrong question. They are sized for rather than rationed
     * ([sizingCellCount] shrinks the whole chart until a column of them fits), so the only way they
     * overflow at all is a moment carrying so many that the size floor stops the shrinking - and
     * there the stack running into the headroom is the better of two bad answers. They do spend the
     * budget, so a division whose medals fill it draws no badges.
     *
     * [dropOverflow] is what single-game mode turns off. There the chart is one game's achievement
     * list and every unlock in it is meant to be on the stage, so a division that overflows simply
     * overflows: the relaxation pass fans the column out sideways and the y clamp lets it use the
     * headroom above the plot. The sizes are still computed the same way, and the standard tier's
     * half radius is what makes most games fit anyway.
     */
    fun selectPerCell(
        icons: List<Icon>,
        boundaries: List<Float>,
        fitRadius: Float,
        innerH: Float,
        gap: Float = STACK_GAP,
        maxPerCell: Int = MAX_PER_CELL,
        dropOverflow: Boolean = true,
    ): List<Placed> {
        if (icons.isEmpty()) return emptyList()
        // Over the standard badges alone: the tiers above them are already decided by what they
        // are, and counting their scores here would only lift the bar the tail has to clear.
        val threshold = highPointThreshold(
            icons.filter { !it.isMedal && it.type == AchievementType.STANDARD }.map { it.points },
        )
        val tiers = icons.map { tierOf(it, threshold) }
        val radii = tiers.map { radiusFor(it, fitRadius) }
        fun placed(i: Int) = Placed(icons[i], tiers[i], radii[i])

        if (!dropOverflow) return icons.indices.map(::placed)

        // What one owner's badges are worth relative to each other: the priority order a solo
        // chart has always filled a division in.
        val priority = compareBy<Int> { tiers[it].ordinal }
            .thenByDescending { icons[it].points }
            .thenBy { icons[it].key }

        val kept = HashSet<Int>()
        icons.indices
            .groupBy { cellOf(icons[it].x, boundaries) }
            .forEach { (_, cell) ->
                // How far down its own owner's list each badge is. A division is then filled a
                // round at a time - everybody's best, then everybody's second - so a rival having
                // a loud day cannot take a whole division and leave the main player with nothing
                // in it. Without this, "one badge a day per player" was true of the selection and
                // false of the chart: the height budget quietly evicted whoever scored less that
                // day, which on a compare chart is the one thing that must not happen silently.
                //
                // On a solo chart every badge has the same owner, so the round IS the priority
                // order and the ordering below is exactly what it always was.
                val round = HashMap<Int, Int>()
                cell.filter { !icons[it].isMedal }
                    .groupBy { icons[it].owner }
                    .forEach { (_, own) ->
                        own.sortedWith(priority).forEachIndexed { r, i -> round[i] = r }
                    }
                val ordered = cell.sortedWith(
                    // Medals stay ahead of everything: they are never dropped, and the budget loop
                    // below stops at the first icon that does not fit, so a medal sorted behind a
                    // badge could be cut off by that break.
                    compareBy<Int> { if (icons[it].isMedal) 0 else 1 }
                        .thenBy { round[it] ?: 0 }
                        .then(priority),
                )
                var stacked = 0f
                var drawn = 0
                for (i in ordered) {
                    val next = if (drawn == 0) radii[i] * 2f else stacked + gap + radii[i] * 2f
                    // Two things the budget may not refuse: a medal, and the first icon of a
                    // division (a plot too short for even one is a broken stage rather than a
                    // crowded moment, and drawing nothing there would hide the chart, not thin it).
                    val forced = icons[i].isMedal || drawn == 0
                    if (!forced && (drawn >= maxPerCell || next > innerH + EPSILON)) break
                    stacked = next
                    drawn++
                    kept += i
                }
            }
        return icons.indices.filter { it in kept }.map(::placed)
    }

    /** How many icons the busiest division of [boundaries] holds; at least 1. */
    fun maxCellCount(icons: List<Icon>, boundaries: List<Float>): Int =
        icons.groupingBy { cellOf(it.x, boundaries) }
            .eachCount()
            .values
            .maxOrNull()
            ?.coerceAtLeast(1)
            ?: 1

    /**
     * The one radius every icon on the chart is drawn at: as big as the plot can carry, given that
     * the busiest division has to stack [maxCellCount] of them up the full [innerH] and no further.
     *
     * [gap] is the room left between two stacked icons (see [STACK_GAP]), so a stack of n spans
     * exactly `n * (2r + gap)` - which is [innerH] by construction. Clamped to [rMin]..[rMax]:
     * a nearly empty chart would otherwise size its handful of icons to a third of the plot each,
     * and a very busy one would shrink them past the point where the art in them is readable.
     */
    fun uniformRadius(
        innerH: Float,
        maxCellCount: Int,
        rMax: Float = R_MAX,
        rMin: Float = R_MIN,
        gap: Float = STACK_GAP,
    ): Float {
        val n = maxCellCount.coerceAtLeast(1)
        val fitting = innerH / (2f * n) - gap / 2f
        val lo = minOf(rMin, rMax)
        val hi = maxOf(rMin, rMax)
        return fitting.coerceIn(lo, hi)
    }

    /**
     * The fitting radius for a chart whose busiest division holds [maxCellCount] icons and whose
     * divisions are [divisionWidth] wide: [uniformRadius]'s answer for the y axis, capped so an
     * icon also fits the division it belongs to.
     *
     * The y-fit alone is only half the question. It asks "can a division's icons stack up the plot",
     * and at the ceiling radius the answer is yes while the icons are still wider than the division
     * is - 88 units of disc in an 84-unit cell on an unstretched stage - so neighbouring divisions'
     * icons start out inside each other and the relaxation pass spends its whole budget pushing
     * them apart. Capping by the division's own width means a full chart is placed rather than
     * merely fitted, and the residue the relaxation is left holding drops with it.
     *
     * The cap is skipped when no division carries more than one icon (a [maxCellCount] of 1). A
     * sparse chart's divisions are exactly as narrow as a dense one's - the grid is a function of
     * the time span, not of how much is drawn - so applying it there would shrink three badges on
     * an empty stage for a crowding that cannot happen: with one icon per division there is nothing
     * on either side to collide with, which is the only thing this cap exists to prevent. See
     * [typicalDivision] for which width to measure.
     */
    fun fitRadius(
        innerH: Float,
        maxCellCount: Int,
        divisionWidth: Float,
        rMax: Float = R_MAX,
        rMin: Float = R_MIN,
        gap: Float = STACK_GAP,
    ): Float {
        val byHeight = uniformRadius(innerH, maxCellCount, rMax, rMin, gap)
        if (maxCellCount <= 1 || divisionWidth <= 0f) return byHeight
        val byWidth = (divisionWidth - gap) / 2f
        return minOf(byHeight, byWidth).coerceAtLeast(minOf(rMin, rMax))
    }

    /**
     * How wide a division of [boundaries] typically is: the plot's width over its division count.
     *
     * The average rather than the narrowest, which is what [fitRadius] wants and is not the same
     * thing. The grid is cut by the axis's own gridlines, so the first and last divisions are
     * whatever is left between a plot edge and the nearest tick - regularly a sliver a few units
     * wide, and regularly occupied. Sizing every icon on the chart by that sliver is not
     * conservative, it is broken: measured, it puts an ordinary 45-badge journey and a 400-badge
     * one both at the size floor, which is worse than having no cap at all. A sliver's own crowding
     * is a local problem, and pushing two icons sideways out of it is exactly what the relaxation
     * pass is for. What the cap is protecting against is the *global* case - every division at once
     * holding icons wider than it is - and the pitch icons are actually placed at is what says
     * whether that is happening.
     */
    /**
     * The fitting radius for a chart drawn on [boundaries], never larger than the one the same
     * journey would get on the unstretched grid [compactBoundaries].
     *
     * That cap is what keeps a widened stage worth widening, and it has to hold on *both* terms of
     * [fitRadius] or it does not hold at all:
     *
     * - **Width.** An axis whose gridlines already make a fine grid is never refined further
     *   ([divisionsFor] would only coarsen it), so stretching that chart leaves the same divisions
     *   two or three times as wide, and the icons in them would grow to match.
     * - **Height.** [sizingCellCount] counts icons per division, and a refined grid splits a busy
     *   division into quieter ones, so the wide grid's own count is the *smaller* of the two - which
     *   sizes the icons back up again. Capping the width alone left this term free, and a compact
     *   division holding five or more medals took it: measured, a 1.5x stage came out at radius 29.5
     *   against the compact chart's 26.2.
     *
     * Either way the consequence is the same and it is silent. Bigger icons cost a division's
     * height budget, so a stretched chart draws *less* of the journey than the compact one it was
     * widened from, and the compact chart's badges stop being the subset of the wide chart's that
     * the art preloading relies on (see [divisionsFor]) - which leaves the export and the still
     * preview drawing badges whose art nobody ever requested.
     *
     * So the stretch buys spacing and, where the grid does refine, capacity - never size. Pinching
     * into the chart is what makes an icon bigger, and that costs nothing.
     *
     * A no-op on the compact chart itself, where the two grids are the same list.
     */
    fun sizingRadius(
        icons: List<Icon>,
        boundaries: List<Float>,
        compactBoundaries: List<Float>,
        innerH: Float,
        compactInnerW: Float,
        rMax: Float = R_MAX,
        rMin: Float = R_MIN,
        gap: Float = STACK_GAP,
    ): Float {
        // The busier of the two readings, which is the compact grid's: its divisions are unions of
        // the refined ones, so they can only hold more. Taking the max rather than simply using it
        // keeps this true of any future grid that is not a strict refinement.
        val cellCount = maxOf(
            sizingCellCount(icons, boundaries),
            sizingCellCount(icons, compactBoundaries),
        )
        val compactCells = compactBoundaries.size - 1
        val width = typicalDivision(boundaries).let { active ->
            if (compactCells < 1 || compactInnerW <= 0f) active
            else minOf(active, compactInnerW / compactCells)
        }
        return fitRadius(innerH, cellCount, width, rMax, rMin, gap)
    }

    fun typicalDivision(boundaries: List<Float>): Float {
        val cells = boundaries.size - 1
        if (cells < 1) return 0f
        return ((boundaries.last() - boundaries.first()) / cells).coerceAtLeast(0f)
    }

}
