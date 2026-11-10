package com.twinlore.app.journey.domain

import com.twinlore.app.journey.domain.model.JourneyFrame
import com.twinlore.app.journey.domain.model.JourneyMode
import com.twinlore.app.journey.domain.model.Medal
import com.twinlore.app.journey.domain.model.PlayerJourney
import com.twinlore.app.journey.domain.model.Unlock
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class P(val x: Float, val y: Float)

data class NodeLayout(
    val unlock: Unlock, val x: Float, val y: Float, val r: Float,
    val anchorX: Float, val anchorY: Float,
    /**
     * Whose badge this is: null for the main player, a rival's name on a compare chart.
     *
     * A compare chart draws badges for everybody on it (one a day each over a span, the whole list
     * each on a single game - see [layout]), and the badge itself has nothing on it that says whose
     * day it was, because the art is the achievement's. The renderer reads this for the identity
     * dot in the badge's corner, for the colour of everything around it, and for the reveal rule: a
     * rival's badge is gated on the shared sweep, because a rival's unlock has no length along the
     * main player's curve.
     */
    val ownerName: String? = null,
)

/**
 * The stretch of x one streak run covers on the main player's curve: first unlock of the run to
 * last, with the run's length in days.
 *
 * Precomputed rather than worked out at draw time: the chart burns the segment of line it is
 * currently revealing whenever the sweep is inside a run (see `JourneyChartRenderer.drawStreakFire`)
 * and names the run in the corner of the stage while it does, and working the runs out per frame
 * would rescan the whole unlock list sixty times a second to arrive at the same handful of ranges.
 * Disjoint and ascending, which is what lets the per-frame question be a walk that stops early.
 *
 * The only streak layout there is now. There used to be a flame placement beside it, one glyph per
 * run above the curve; the glyphs are gone (see `JourneyChartRenderer.drawStreakChip`) and so is the
 * pass that placed them.
 */
data class StreakSpan(val startX: Float, val endX: Float, val days: Int)
data class CurveLayout(
    val playerName: String?,        // null = main
    val points: List<P>,            // includes the leading zero point
    val maxX: Float,
    val finalPoints: Int,
    val bgTrack: List<Pair<Float, Int>>,  // (x, gameId) per unlock, ascending x
)
data class MedalLayout(
    val medal: Medal, val x: Float, val y: Float, val radius: Float,
    /** Where the medal belongs on its owner's curve; the drawn circle leaders back to this. */
    val anchorX: Float, val anchorY: Float,
)
data class ChartLayout(
    val width: Float, val height: Float,
    val padL: Float, val padR: Float, val padT: Float, val padB: Float,
    val raceMode: Boolean,
    val ceiling: Int,
    val axisT0: Long, val axisT1: Long,   // calendar range incl. all players (calendar mode)
    val eMaxMillis: Long,                 // race mode axis length
    val ticks: List<Tick>,
    val main: CurveLayout,
    val rivals: List<CurveLayout>,
    /**
     * The badges on the stage. On a solo chart these are the main player's, rationed by the grid;
     * on a compare chart they belong to every player on it, each carrying its owner (see
     * [NodeLayout.ownerName]).
     */
    val nodes: List<NodeLayout>,
    val medals: List<MedalLayout>,
    /**
     * The radius the grid sized this chart's icons against: what a medal is drawn at, and the
     * ceiling no badge may exceed (see [GridIconLayout.Tier]). Published because it is not
     * recoverable from the drawn icons - a chart with no awards on it has nothing at the full
     * radius - and because it is what says how big "big" is on this particular stage.
     */
    val fitRadius: Float = GridIconLayout.R_MAX,
    /** The main player's streak runs, in chronological order. See [StreakSpan]. */
    val streaks: List<StreakSpan> = emptyList(),
    /**
     * Each rival's streak runs, in [rivals] order. A rival's line burns the same way the main
     * player's does - the streak is a fact about a run of days, not about whose days they were.
     */
    val rivalStreaks: List<List<StreakSpan>> = emptyList(),
) {
    /** How many icons (badges plus medals) this chart draws. */
    val iconCount: Int get() = nodes.size + medals.size
}

data class CubicSeg(val c1: P, val c2: P, val end: P)         // preceded by moveTo(points[0])

object LayoutEngine {
    const val W = 1060f
    const val H = 600f
    const val PAD_L = 64f
    const val PAD_R = 76f
    const val PAD_T = 132f
    const val PAD_B = 52f
    val E_MIN_MILLIS = 15L * 60 * 1000

    /**
     * The virtual widths a chart may stretch to, as multiples of [W]. Discrete steps rather than a
     * continuous factor so that two journeys of similar density get the same stage, and so the
     * stretch reads as a handful of recognisable sizes rather than an arbitrary number.
     */
    val WIDTH_FACTORS = listOf(1f, 1.5f, 2f, 3f, 4f)

    /** How many icons one [W]-wide screenful of chart carries before it reads as cramped. */
    const val ICONS_PER_SCREEN = 90f

    private const val RELAX_ITERATIONS = 160
    private const val FACTOR_EPSILON = 0.0001f
    private const val COLLISION_MARGIN = 7f

    /** How much of a collision push is spent sideways. See [pushIfColliding]. */
    private const val PUSH_X_DAMP = 0.35f

    /**
     * How hard an icon is pulled back to the point on the curve it belongs to, per axis.
     *
     * Anisotropic on purpose. In x the pull stays firm: x is *when* the achievement was won, the
     * reveal gates on it, and a badge that drifts sideways is a badge lying about its moment.
     * In y it is deliberately slack, because y carries no per-icon meaning at all - the curve
     * says what the score was, and a badge is only a marker near its moment.
     *
     * Measured against the pull being equal on both axes (both 0.02): the first tenth of a dense
     * 550-day journey packed its icons into 17-32% of the plot band with ~300 virtual units of
     * empty plot above them, because a cumulative curve starts at the bottom and every push apart
     * was immediately dragged back to it. Slackening y alone lets a crowded cluster fan up into
     * the height that is going spare, without moving a single icon along the timeline: the same
     * fixture then opens on 47% of the band on the compact chart, against 32% before.
     *
     * A stronger version of this was tried and taken back out: seeding each division's overflow
     * into an explicit vertical stack before relaxing. It bought two points on the compact chart
     * (49% against this 47%) and turned the WIDE one - whose divisions are narrower than two icons
     * - into rows of tall branches growing off the line, which is not what this chart is (user
     * feedback). The pull is the whole of the fix; the relaxation settles the rest into a cloud
     * that still reads as following the curve.
     */
    private const val ANCHOR_PULL_X = 0.02f
    private const val ANCHOR_PULL_Y = 0.002f

    /**
     * How far a badge is held clear of its own anchor's height, as a fraction of its radius, so
     * the curve cannot run through the middle of its art. Just under 1: at exactly 1 the line
     * would graze the plate's edge, and a shade inside that keeps the badge visibly ON the line's
     * shoulder rather than floating off it - the cloud still reads as following the curve.
     *
     * Applied to medallions as well as badges. An award is the biggest icon on the stage and
     * always sits on the curve, so it is the one the line ruins most visibly; the clearance is a
     * fixed offset from the anchor rather than the slack pull the badges get, so everything that
     * reasons about how far a medal moved (the reveal gate, the confetti's origin, hit testing)
     * still finds it where it expects.
     */
    private const val CURVE_CLEARANCE = 0.9f
    private const val NODE_Y_CLAMP_OFFSET = 24f

    /**
     * How much wider than [W] the chart for [frame] should be: one step of [WIDTH_FACTORS] per
     * [ICONS_PER_SCREEN] icons the journey is asking to draw, capped at 4x.
     *
     * A dense journey drawn on a fixed 1060-unit stage is not a chart of a journey, it is a wall of
     * badges. Stretching the virtual space instead keeps every icon at a readable size and hands
     * the extra timeline to the pan/zoom and the follow camera, which already exist to explore it.
     *
     * The count this keys off is the *candidate* density - what [IconBudget] would select, before
     * [GridIconLayout] thins it per division - and that is deliberate rather than an oversight. It
     * is the demand for width, not the supply: a journey with four screenfuls' worth of unlocks
     * wanting icons is exactly the journey that should be given four screenfuls, and the grid then
     * hands the extra room back out as extra capacity (see [GridIconLayout.divisionsFor]). Keying
     * off the drawn count instead would be circular - the width decides the grid, the grid decides
     * the count.
     *
     * [includeNodes] is the same flag [layout] takes, and it has to be decided first: an awards-only
     * chart, or a compare chart (which draws no badges at all), has a handful of medallions on it,
     * and stretching that to four screenfuls would be four screenfuls of bare curve to pan across.
     */
    fun widthFactorFor(frame: JourneyFrame, includeNodes: Boolean = true): Float {
        if (!includeNodes) return WIDTH_FACTORS.first()
        val need = (IconBudget.iconCountFor(frame) / ICONS_PER_SCREEN)
            .coerceIn(WIDTH_FACTORS.first(), WIDTH_FACTORS.last())
        return WIDTH_FACTORS.firstOrNull { it >= need - FACTOR_EPSILON } ?: WIDTH_FACTORS.last()
    }

    /**
     * Lays [frame] out into a virtual space [W] * [widthFactor] wide and [H] tall.
     *
     * The pads do not scale, so the whole stretch lands on the plot area and every x - curve points,
     * ticks, icon anchors, the backdrop track - follows from `innerW` without any of them having to
     * know about the factor. The chart's own drawing is unchanged; what changes is that the content
     * is now wider than the viewport it is fitted into, and the view transform pans across it.
     */
    fun layout(
        frame: JourneyFrame,
        includeNodes: Boolean,
        widthFactor: Float = 1f,
        /**
         * The icon budget this chart selects under. Defaults to its own width's budget; the
         * compact twin of a widened chart passes the WIDE budget instead, so both charts pick
         * from the same candidate list and the compact grid's coarser cells - not a smaller
         * budget - are what thin the dense view. Two different candidate lists are how the
         * containment invariant (compact draws a subset of wide) breaks.
         */
        iconBudget: Int = IconBudget.budgetFor(frame, widthFactor),
    ): ChartLayout {
        val width = W * widthFactor.coerceAtLeast(1f)
        val allPlayers = listOf(frame.main) + frame.rivals
        val raceMode = frame.mode == JourneyMode.GAME && frame.rivals.any { it.unlocks.isNotEmpty() }
        // A chart with somebody else on it. [includeNodes] does not decide this: the caller turns
        // that off for compare mode because the stage must not be stretched for a handful of
        // per-day badges (see [widthFactorFor]), which is a question about width rather than about
        // whether anything is drawn.
        val compare = frame.rivals.any { it.unlocks.isNotEmpty() }

        // The calendar axis is the SELECTED window when there is one: a 90-day span shows 90
        // days of axis whether the unlocks cover nine of them or all ninety - time is the
        // selection, not the achievement extent. "All time" (the 0 sentinel) and GAME mode keep
        // the unlock extent, because there the data is the only statement of the range there is.
        val allUnlocks = allPlayers.flatMap { it.unlocks }
        val windowAxis = frame.mode == JourneyMode.SPAN &&
            frame.spanFromMillis > 0L && frame.spanToMillis > frame.spanFromMillis
        val axisT0: Long
        val axisT1: Long
        if (windowAxis) {
            axisT0 = frame.spanFromMillis
            axisT1 = frame.spanToMillis
        } else if (allUnlocks.isEmpty()) {
            axisT0 = frame.spanFromMillis
            axisT1 = frame.spanToMillis
        } else {
            axisT0 = allUnlocks.minOf { it.earnedAtMillis }
            axisT1 = allUnlocks.maxOf { it.earnedAtMillis }
        }
        val axisSpan = (axisT1 - axisT0).coerceAtLeast(1)

        // Race mode: eMax = max over players of (last - first unlock), floor E_MIN_MILLIS.
        val eMax = allPlayers
            .mapNotNull { p ->
                if (p.unlocks.isEmpty()) null
                else p.unlocks.last().earnedAtMillis - p.unlocks.first().earnedAtMillis
            }
            .maxOrNull()
            ?.let { max(it, E_MIN_MILLIS) }
            ?: E_MIN_MILLIS

        val innerW = width - PAD_L - PAD_R
        val innerH = H - PAD_T - PAD_B

        fun xCal(t: Long): Float = PAD_L + (t - axisT0).toFloat() / axisSpan.toFloat() * innerW
        fun xLin(e: Long): Float = PAD_L + innerW * e.toFloat() / eMax.toFloat()

        // ceiling = max over all players of (sum of points in frame), min 1.
        val ceiling = allPlayers
            .maxOfOrNull { p -> p.unlocks.sumOf { it.points } }
            ?.coerceAtLeast(1) ?: 1

        fun yFor(c: Int): Float = PAD_T + innerH - c.toFloat() / ceiling.toFloat() * innerH

        fun xForPlayer(player: PlayerJourney, t: Long): Float =
            if (raceMode) {
                val first = player.unlocks.firstOrNull()?.earnedAtMillis ?: t
                xLin(max(t - first, 0L))
            } else {
                xCal(t)
            }

        fun buildCurve(player: PlayerJourney, isMain: Boolean): CurveLayout {
            val zeroX = if (raceMode) PAD_L else xCal(player.unlocks.firstOrNull()?.earnedAtMillis ?: axisT0)
            val points = mutableListOf(P(zeroX, yFor(0)))
            val bgTrack = mutableListOf<Pair<Float, Int>>()
            var cumulative = 0
            for (u in player.unlocks) {
                cumulative += u.points
                val x = xForPlayer(player, u.earnedAtMillis)
                points += P(x, yFor(cumulative))
                bgTrack += x to u.gameId
            }
            val maxX = points.maxOf { it.x }
            return CurveLayout(
                playerName = if (isMain) null else player.name,
                points = points,
                maxX = maxX,
                finalPoints = cumulative,
                bgTrack = bgTrack,
            )
        }

        val mainCurve = buildCurve(frame.main, isMain = true)
        val rivalCurves = frame.rivals.map { buildCurve(it, isMain = false) }

        // Medals, in chronological order, each anchored on its own owner's curve rather than in a
        // band above the plot: a 31-game span used to stack thirty medallions into two alternating
        // rows that overlapped each other and the caption.
        val allMedals = (frame.main.medals.map { Pair(it, frame.main) } +
            frame.rivals.flatMap { r -> r.medals.map { Pair(it, r) } })
            .sortedBy { it.first.whenMillis }

        // Identity, not equality: PlayerJourney is a data class, so indexOf would deep-walk every
        // rival's unlock list and hand two rivals with identical journeys the same curve.
        val curveOf: (PlayerJourney) -> CurveLayout = { owner ->
            if (owner === frame.main) {
                mainCurve
            } else {
                rivalCurves.getOrElse(frame.rivals.indexOfFirst { it === owner }) { mainCurve }
            }
        }

        val medalAnchors = allMedals.map { (medal, owner) ->
            val x = if (raceMode) {
                val first = owner.unlocks.firstOrNull()?.earnedAtMillis ?: medal.whenMillis
                xLin(max(medal.whenMillis - first, 0L))
            } else {
                xCal(medal.whenMillis.coerceIn(axisT0, axisT1))
            }
            P(x, curveYAt(curveOf(owner), x, yFor(0)))
        }

        // Icon budget: every medal, then the highest-scoring unlocks with whatever is left. The
        // cap scales with the journey AND with the stage this layout was granted - see
        // [IconBudget.budgetFor] - so a widened timeline carries more badges than the compact
        // chart built from the same frame, without drawing more than the viewport shows.
        val selection = IconBudget.select(
            medals = allMedals.map { it.first },
            unlocks = if (includeNodes && !compare) frame.main.unlocks else emptyList(),
            budget = iconBudget,
        )

        // Which badges each player puts on the stage.
        //
        // A compare chart draws one a day per player rather than none at all. Two players' full
        // badge lists on one stage was the wall of thumbnails that had them taken away; a day's
        // highest scorer is few enough to read and is still the badge anyone would point at, and it
        // is what lets the chart answer "who was playing, and when" at a glance rather than only
        // through two lines. Every other chart is the main player's own, rationed by the grid.
        val mainAnchors = mainCurve.points.drop(1)
        val badgeSlots = ArrayList<BadgeSlot>()
        if (compare) {
            // One badge a day per player over a SPAN, and every badge on a single GAME.
            //
            // The daily rationing is what keeps a multi-month compare span from being a wall of
            // thumbnails, and on a span it is the right answer: the day speaks through its highest
            // scorer. On one game it is a mistake, and a visible one - a game beaten in an evening
            // is a single calendar day, so a two-player race put exactly ONE badge on the stage per
            // player and the rivals were two bare lines for the whole of the animation. Single-game
            // mode already draws the main player's entire achievement list for the same reason (an
            // achievement missing from the game the chart is *about* is a hole in the subject); its
            // rivals get the same treatment, and [GridIconLayout.selectPerCell] - which fills a busy
            // division a round at a time, so every player present keeps a badge before anyone gets a
            // second - is what keeps a long game from walling the stage.
            val perDay = frame.mode != JourneyMode.GAME
            fun badgeIndices(unlocks: List<Unlock>): List<Int> =
                if (perDay) JourneyStats.dailyBadgeIndices(unlocks) else unlocks.indices.toList()

            badgeIndices(frame.main.unlocks).forEach { i ->
                mainAnchors.getOrNull(i)?.let {
                    badgeSlots += BadgeSlot(frame.main.unlocks[i], it, null)
                }
            }
            frame.rivals.forEachIndexed { index, rival ->
                val anchors = rivalCurves.getOrNull(index)?.points?.drop(1) ?: return@forEachIndexed
                badgeIndices(rival.unlocks).forEach { i ->
                    anchors.getOrNull(i)?.let {
                        badgeSlots += BadgeSlot(rival.unlocks[i], it, rival.name)
                    }
                }
            }
        } else {
            selection.unlockIndices.forEach { i ->
                mainAnchors.getOrNull(i)?.let {
                    badgeSlots += BadgeSlot(frame.main.unlocks[i], it, null)
                }
            }
        }

        // The ticks come first now: the grid the axis draws is the same grid the icons are
        // rationed by, so the gridlines have to exist before anything is placed.
        val ticks = if (raceMode) TickGenerator.elapsedTicks(eMax) else TickGenerator.timeTicks(axisT0, axisT1)
        val tickXs = ticks.map { if (raceMode) xLin(it.tMillis) else xCal(it.tMillis) }
        val boundaries = GridIconLayout.divisionsFor(tickXs, PAD_L, width - PAD_R, width / W)
        // The grid the axis's own gridlines make, i.e. the one the unstretched chart is rationed
        // by. A widened stage refines it, and sizing has to be capped against it on BOTH axes so a
        // stretch can never grow an icon - see [GridIconLayout.sizingRadius].
        val compactBoundaries = GridIconLayout.divisions(tickXs, PAD_L, width - PAD_R)

        val placement = placeIcons(
            width = width,
            innerH = innerH,
            badgeSlots = badgeSlots,
            allMedals = allMedals,
            medalAnchors = medalAnchors,
            medalIndices = selection.medalIndices,
            boundaries = boundaries,
            compactBoundaries = compactBoundaries,
            // Single-game mode draws the whole achievement list: the chart is one game, and an
            // achievement quietly missing from it is a hole in the very thing being charted. The
            // adaptive tiers are what make that affordable - a standard badge is half the fitting
            // radius - and a game that still overflows a division simply overflows it.
            //
            // Except with rivals on it, where the same list is now built two or three times over
            // (see the badge slots above). Letting all of that overflow would be a genuine wall, so
            // a compare chart keeps the per-division budget in every mode - and that budget is the
            // one that shares a crowded division out between owners a round at a time, which is
            // exactly what a race needs.
            dropOverflow = frame.mode != JourneyMode.GAME || compare,
        )

        // Streak runs: the main player's only. They belong to one curve, and a mark over a stack of
        // three lines would say nothing about which of them kept going.
        //
        // Placed through the same function the curve was, so the burning stretch of line covers
        // exactly the days the run is made of: a race axis is elapsed time from the player's own
        // first unlock, which x in millis is not.
        val streaks = JourneyStats.streaks(frame.main.unlocks).map { run ->
            StreakSpan(
                startX = xForPlayer(frame.main, run.startMillis),
                endX = xForPlayer(frame.main, run.endMillis),
                days = run.days,
            )
        }
        // Rivals burn too, each run placed through their own axis mapping (a race axis is elapsed
        // time from that player's own first unlock).
        val rivalStreaks = frame.rivals.map { rival ->
            JourneyStats.streaks(rival.unlocks).map { run ->
                StreakSpan(
                    startX = xForPlayer(rival, run.startMillis),
                    endX = xForPlayer(rival, run.endMillis),
                    days = run.days,
                )
            }
        }

        return ChartLayout(
            width = width, height = H,
            padL = PAD_L, padR = PAD_R, padT = PAD_T, padB = PAD_B,
            raceMode = raceMode,
            ceiling = ceiling,
            axisT0 = axisT0, axisT1 = axisT1,
            eMaxMillis = eMax,
            ticks = ticks,
            main = mainCurve,
            rivals = rivalCurves,
            nodes = placement.nodes,
            medals = placement.medals,
            fitRadius = placement.fitRadius,
            streaks = streaks,
            rivalStreaks = rivalStreaks,
        )
    }

    /**
     * Linearly interpolates a player's cumulative-points curve at [x]. Curve points are ascending
     * in x by construction, so the bracketing pair is found by a plain scan; outside the curve the
     * nearest endpoint wins, and a player with no unlocks at all sits on [fallbackY].
     */
    internal fun curveYAt(curve: CurveLayout, x: Float, fallbackY: Float): Float {
        val points = curve.points
        if (points.isEmpty()) return fallbackY
        if (x <= points.first().x) return points.first().y
        if (x >= points.last().x) return points.last().y
        for (i in 0 until points.size - 1) {
            val a = points[i]
            val b = points[i + 1]
            if (x in a.x..b.x) {
                val span = b.x - a.x
                if (span <= 0.0001f) return b.y
                return a.y + (b.y - a.y) * ((x - a.x) / span)
            }
        }
        return points.last().y
    }

    /**
     * Places every icon the chart draws - badge nodes and medallions together - and fans them out
     * of each other with one shared relaxation pass. One pass rather than two is the whole point:
     * medals now live on the curves alongside the badges, so relaxing them separately would just
     * let the two sets settle on top of each other.
     *
     * Sizes are decided here too, and before the thinning rather than after it, because under the
     * adaptive hierarchy the size is what decides how much fits: the fitting radius is sized
     * against a nominal full division ([GridIconLayout.sizingCellCount]) and each icon's tier then
     * scales it (see [GridIconLayout.Tier]), so a division of half-size standard badges holds more
     * of them than a division of awards does. See [GridIconLayout.selectPerCell] for the budget.
     */
    private fun placeIcons(
        width: Float,
        innerH: Float,
        badgeSlots: List<BadgeSlot>,
        allMedals: List<Pair<Medal, PlayerJourney>>,
        medalAnchors: List<P>,
        medalIndices: List<Int>,
        boundaries: List<Float>,
        compactBoundaries: List<Float>,
        dropOverflow: Boolean,
    ): Placement {
        val medalSlots = medalIndices.mapNotNull { i -> medalAnchors.getOrNull(i)?.let { i to it } }

        val candidates = ArrayList<GridIconLayout.Icon>(medalSlots.size + badgeSlots.size)
        medalSlots.forEachIndexed { k, (_, anchor) ->
            candidates += GridIconLayout.Icon(key = k, x = anchor.x, isMedal = true, points = 0)
        }
        badgeSlots.forEachIndexed { k, slot ->
            // Keys run in the order the slots were built - the journey's own order, and on a
            // compare chart the main player's day before each rival's - so two badges worth the
            // same are always dropped in the same order. The tiers and the fitting radius are
            // worked out over the combined set, so a compare chart's badges are sized by exactly
            // the rule a solo chart's are.
            candidates += GridIconLayout.Icon(
                key = medalSlots.size + k,
                x = slot.anchor.x,
                isMedal = false,
                points = slot.unlock.points,
                type = slot.unlock.type,
                owner = slot.ownerName,
            )
        }

        // Sized against both axes and against both grids: see [GridIconLayout.sizingRadius].
        val fitRadius = GridIconLayout.sizingRadius(
            icons = candidates,
            boundaries = boundaries,
            compactBoundaries = compactBoundaries,
            innerH = innerH,
            compactInnerW = W - PAD_L - PAD_R,
        )

        val kept = GridIconLayout.selectPerCell(
            icons = candidates,
            boundaries = boundaries,
            fitRadius = fitRadius,
            innerH = innerH,
            dropOverflow = dropOverflow,
        )

        val nodeMuts = ArrayList<Pair<BadgeSlot, Circle>>(kept.size)
        val medalMuts = ArrayList<Pair<Int, Circle>>(medalSlots.size)
        for (placed in kept) {
            val icon = placed.icon
            if (icon.key < medalSlots.size) {
                val (i, anchor) = medalSlots[icon.key]
                medalMuts += i to Circle(
                    anchor.x, anchor.y, placed.radius,
                    // A medallion is the icon the line most often ruins: it is the biggest thing
                    // on the stage and it sits exactly on the curve. The pull
                    // stays firm - an award's position is read as a fact - and only the clearance
                    // moves it, which is a fixed offset rather than a licence to drift.
                    clearY = placed.radius * CURVE_CLEARANCE,
                )
            } else {
                val slot = badgeSlots[icon.key - medalSlots.size]
                nodeMuts += slot to Circle(
                    slot.anchor.x, slot.anchor.y, placed.radius,
                    pullY = ANCHOR_PULL_Y,
                    clearY = placed.radius * CURVE_CLEARANCE,
                )
            }
        }

        val muts = ArrayList<Circle>(nodeMuts.size + medalMuts.size)
        medalMuts.forEach { muts += it.second }
        nodeMuts.forEach { muts += it.second }
        relax(muts, width)

        val nodes = nodeMuts.map { (slot, m) ->
            NodeLayout(
                unlock = slot.unlock, x = m.x, y = m.y, r = m.r,
                anchorX = m.anchorX, anchorY = m.anchorY, ownerName = slot.ownerName,
            )
        }
        val medals = medalMuts.map { (i, m) ->
            MedalLayout(
                medal = allMedals[i].first, x = m.x, y = m.y, radius = m.r,
                anchorX = m.anchorX, anchorY = m.anchorY,
            )
        }
        return Placement(nodes, medals, fitRadius)
    }

    /**
     * One badge asking to be drawn: the achievement, the point on its owner's curve it belongs to,
     * and whose curve that is (null for the main player).
     */
    private class BadgeSlot(val unlock: Unlock, val anchor: P, val ownerName: String?)

    /** What [placeIcons] worked out: the icons it placed and the radius it sized them against. */
    private class Placement(
        val nodes: List<NodeLayout>,
        val medals: List<MedalLayout>,
        val fitRadius: Float,
    )

    /**
     * One icon while the relaxation pass is moving it; [anchorX]/[anchorY] never move.
     *
     * [pullY] is how hard this icon is dragged back to its anchor vertically. Badges are slack
     * ([ANCHOR_PULL_Y]) so a crowded moment can fan up into the plot height going spare; medals
     * keep the firm pull, because an award's drawn position is read as a fact - the reveal gate,
     * the confetti's origin and the hit testing all reason about how far the relaxation moved a
     * medallion off the point on the curve where it was won.
     */
    internal class Circle(
        var x: Float,
        var y: Float,
        val r: Float,
        val pullY: Float = ANCHOR_PULL_X,
        /**
         * How far this icon must stay from the height of its own anchor, so the curve does not
         * run through the middle of it. Zero leaves the icon free to sit on the line. See
         * [CURVE_CLEARANCE].
         */
        val clearY: Float = 0f,
    ) {
        val anchorX = x
        val anchorY = y

        /** Whether anything pushed this icon during the sweep just run. See [relax]. */
        var crowded = false
    }

    /**
     * Pushes overlapping icons apart, pulling each back towards its anchor, inside the stage.
     *
     * Gauss-Seidel over the original i-ascending, j-from-i+1-ascending pair order: a pair's push
     * (see [pushIfColliding]) mutates the shared [Circle]s immediately, so the next pair checked
     * sees the update. Two attempts at a spatial prune on top of this both turned
     * out to cost more than they saved: a frozen-snapshot batched (Jacobi) update converged
     * incorrectly (it limit-cycled on dense charts instead of settling, leaving several times more
     * overlapping icons than this loop leaves at the same budget - changing which pairs push each
     * other in what order changes where the system settles, not just how fast it gets there); a
     * live update with a sorted-index broad phase converged correctly but measured *slower* than
     * this plain loop, because the 400 badges the expanded icon budget actually places are packed
     * densely enough on the fixed 1060x600 stage that a search structure's overhead was pure loss.
     * At this problem size, O(n^2) over cheap float math is simply not the bottleneck it looks like
     * on paper: measured at roughly 10ms for 400 icons, off the main thread. [sweepAll] is that
     * plain loop, kept as its own function only for readability.
     */
    internal fun relax(circles: List<Circle>, width: Float = W) {
        if (circles.size < 2) return
        val clampMinX = PAD_L
        val clampMaxX = width - PAD_R
        val clampMinY = PAD_T - NODE_Y_CLAMP_OFFSET
        val clampMaxY = H - PAD_B

        for (iter in 0 until RELAX_ITERATIONS) {
            val moved = sweepAll(circles)

            circles.forEachIndexed { index, m ->
                m.x += (m.anchorX - m.x) * ANCHOR_PULL_X
                m.y += (m.anchorY - m.y) * m.pullY
                // Off the line, not on it. An icon's anchor IS a point on its owner's curve, and
                // the curves draw above the icons on purpose (a line broken by the badges parked
                // on it stops reading as a line) - so a badge that settles on its anchor is a
                // badge with the curve painted straight through the middle of its art, which on a
                // long dense span is most of them. Holding each one a radius clear
                // of its anchor's height lets the line graze an edge instead. The side is
                // whichever way it already drifted; a badge still sitting exactly on the line
                // takes the side its index gives it, so a cluster splits above and below rather
                // than piling on one side.
                //
                // Measured against the anchor's height, not against the curve's height at the
                // icon's final x - the two differ by whatever the icon drifted sideways, and the
                // relaxation has no cheap way to re-evaluate a cubic mid-sweep. The approximation
                // is what the x damping in [pushIfColliding] buys: drift is a few units for the
                // median icon and bounded at tens for the worst (pinned in IconHeightUsageTest),
                // over which a cumulative curve moves far less than the clearance itself. A
                // steeply climbing stretch is where it is loosest.
                if (m.clearY > 0f) {
                    val delta = m.y - m.anchorY
                    if (abs(delta) < m.clearY) {
                        val side = when {
                            delta > 0.01f -> 1f
                            delta < -0.01f -> -1f
                            // Alternating sides splits a PILE, which is what the parity is for.
                            // An icon with nothing near it is not in a pile, and alternating it
                            // strung a sparse chart's badges along the line like beads on either
                            // side of it for no gain; those go above, consistently.
                            m.crowded -> if (index % 2 == 0) -1f else 1f
                            else -> -1f
                        }
                        m.y = m.anchorY + side * m.clearY
                    }
                }
                val minX = clampMinX + m.r
                val maxX = clampMaxX - m.r
                val minY = clampMinY + m.r
                val maxY = clampMaxY - m.r
                m.x = m.x.coerceIn(min(minX, maxX), max(minX, maxX))
                m.y = m.y.coerceIn(min(minY, maxY), max(minY, maxY))
            }
            if (!moved) break
        }
    }

    /** One pass over every pair: i ascending, j ascending from i+1. */
    private fun sweepAll(circles: List<Circle>): Boolean {
        var moved = false
        circles.forEach { it.crowded = false }
        for (i in circles.indices) {
            val a = circles[i]
            for (j in i + 1 until circles.size) {
                if (pushIfColliding(i, a, circles[j])) moved = true
            }
        }
        return moved
    }

    /** The collision response for one pair. */
    private fun pushIfColliding(i: Int, a: Circle, b: Circle): Boolean {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val dist = sqrt(dx * dx + dy * dy)
        val minDist = a.r + b.r + COLLISION_MARGIN
        if (dist >= minDist) return false
        if (dist < 0.01f) {
            val sign = if (i % 2 == 0) 1f else -1f
            a.y -= sign
            b.y += sign
            a.crowded = true
            b.crowded = true
        } else {
            val push = (minDist - dist) / 2f / dist
            // Damped in x, full strength in y. An icon's x IS its moment - it is what the reveal
            // gates on and what the eye reads off the axis - while its y is only where it was
            // parked to be legible, and the whole point of the spread is to use the height going
            // spare. An isotropic response spent that budget in both directions, and on a dense
            // compact chart the worst-placed badge ended up a tenth of the timeline from the day
            // it was earned. Separation still resolves, because what x gives up y takes: the pair
            // is pushed the same distance apart, just more of it vertically.
            val pushX = dx * push * PUSH_X_DAMP
            val pushY = dy * push
            a.x -= pushX
            a.y -= pushY
            b.x += pushX
            b.y += pushY
        }
        a.crowded = true
        b.crowded = true
        return true
    }

    fun smoothPathSegments(points: List<P>): List<CubicSeg> {
        if (points.size < 3) {
            if (points.size < 2) return emptyList()
            val b = points[1]
            return listOf(CubicSeg(c1 = b, c2 = b, end = b))
        }
        val segs = mutableListOf<CubicSeg>()
        for (i in 0 until points.size - 1) {
            val a = points[i]
            val b = points[i + 1]
            val mx = (a.x + b.x) / 2f
            segs += CubicSeg(c1 = P(mx, a.y), c2 = P(mx, b.y), end = b)
        }
        return segs
    }
}
