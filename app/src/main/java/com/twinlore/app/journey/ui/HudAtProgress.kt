package com.twinlore.app.journey.ui

import com.twinlore.app.journey.domain.ChartLayout
import com.twinlore.app.journey.domain.MedalLayout
import com.twinlore.app.journey.domain.model.AwardKind
import com.twinlore.app.journey.domain.model.AwardTally
import com.twinlore.app.journey.domain.model.JourneyFrame
import com.twinlore.app.journey.ui.chart.ChartConstants
import kotlin.math.hypot

/**
 * The HUD's counters at a given playback position: what the sweep has revealed so far, rather than
 * the journey's finished totals.
 *
 * Pure Kotlin on purpose (no Compose, no [com.twinlore.app.journey.ui.chart.ChartPaths]): the
 * chart's own reveal is measured on a smoothed cubic path, which needs Android's `PathMeasure` and
 * cannot be unit tested. The polyline through the same layout points is the same monotonic
 * parametrisation to within the smoothing, which is well inside the shell's ~1% progress
 * granularity, so the counters land on the same unlocks the badges pop in on.
 *
 * At progress 1 the values are read straight off the frame, so the end of playback always shows
 * exactly the journey's totals rather than an approximation of them.
 *
 * Every answer here used to be re-derived from zero on every call - the cumulative arc lengths
 * rebuilt, the whole unlock list rescanned into a boxing `HashSet<Int>`, the medal list refiltered -
 * about a hundred times per playback and once per exported frame. All of it is a pure function of
 * the layout and the frame, so it is now tabulated once per (layout, frame) and each call is a
 * binary search plus a couple of array reads. The numbers are identical: the tables are built by the
 * same accumulations in the same order, so even the floating-point sums are bit for bit what the
 * per-call loops produced.
 */
object HudAtProgress {

    private val tolerance = ChartConstants.REVEAL_TOLERANCE

    data class HudStats(
        val games: Int,
        val unlocked: Int,
        val points: Int,
        /** When the last revealed unlock was earned, or null before the first one. */
        val journeyDateMillis: Long?,
        /**
         * How many of [unlocked] were earned in hardcore.
         *
         * The single-game header's bar draws them as a gold overlay over the silver of the whole
         * count, exactly as the RA achievement tab's dual bar does (`RetroAchievementDrawer`,
         * `AchievementsBoard`), so the same game reads the same way in both places.
         */
        val hardcore: Int = 0,
    )

    /**
     * Everything about a [ChartLayout] that does not depend on where playback has got to.
     *
     * One instance per layout, held in a single-slot cache below. A slot rather than a map because
     * the callers walk one layout at a time: the player draws whichever chart is up, and an export
     * draws the compact one from the first frame to the last.
     */
    private class LayoutTables(val layout: ChartLayout) {
        val compare: Boolean = layout.rivals.any { it.bgTrack.isNotEmpty() }

        /** Cumulative arc length along `layout.main.points`, so `lengths.last()` is the total. */
        val lengths: FloatArray = FloatArray(layout.main.points.size).also { out ->
            val points = layout.main.points
            for (i in 1 until points.size) {
                out[i] = out[i - 1] + hypot(points[i].x - points[i - 1].x, points[i].y - points[i - 1].y)
            }
        }

        /**
         * For each `bgTrack` entry, the arc length at which the curve first *reaches its x* - not
         * the length of its own point.
         *
         * That is what the renderer measures (it bisects the path for the anchor's x), and the
         * difference is not academic: several unlocks earned at the same moment share an x and rise
         * vertically above one another, so counting their own arc length would hold the counter
         * back through a climb the sweep crosses in an instant.
         *
         * Non-decreasing by construction, which is what lets [revealedCount] binary-search it.
         */
        val entryLengths: FloatArray = run {
            val track = layout.main.bgTrack
            val points = layout.main.points
            val out = FloatArray(track.size)
            if (points.size >= 2) {
                var index = 0
                for (i in track.indices) {
                    val x = track[i].first
                    while (index < points.size - 1 && points[index].x < x) index++
                    out[i] = lengths[index]
                }
            }
            out
        }

        /** The track's x values, ascending, for the compare sweep (which is linear in x). */
        val trackX: FloatArray = FloatArray(layout.main.bgTrack.size) { layout.main.bgTrack[it].first }

        /**
         * The same, per rival, for [rivalPoints]: where each of their unlocks sits on the shared
         * axis. Index for index with `layout.rivals`, which the layout builds from the frame's
         * roster in order, so a rival's curve, track and unlock list all share one index.
         */
        val rivalTrackX: Array<FloatArray> = Array(layout.rivals.size) { r ->
            val track = layout.rivals[r].bgTrack
            FloatArray(track.size) { track[it].first }
        }

        /** Medal anchors, ascending, for [awardsRevealed]: the main player's, and everyone's. */
        val mainMedalAnchors: FloatArray =
            layout.medals.filter { it.medal.ownerName == null }.map { it.anchorX }.sorted().toFloatArray()
        val allMedalAnchors: FloatArray =
            layout.medals.map { it.anchorX }.sorted().toFloatArray()

        /**
         * The same anchors again, split by award kind, for [awardTally].
         *
         * Indexed by [AwardKind.ordinal] rather than kept in a map: this is read once per counter
         * update and the three arrays are built once per layout, so an enum-keyed lookup would be a
         * hash per kind per read for a fixed set of three.
         *
         * Counting each kind against its own array and summing gives exactly what
         * [awardsRevealed] returns from the combined one - the same anchors, the same comparison,
         * the same tolerance - which is what keeps the breakdown and the total from disagreeing
         * (pinned by `HudRevealCrossCheckTest`).
         */
        val mainKindAnchors: Array<FloatArray> = kindAnchors(layout.medals.filter { it.medal.ownerName == null })
        val allKindAnchors: Array<FloatArray> = kindAnchors(layout.medals)

        /**
         * Each rival's own award anchors, split by kind and sorted, keyed by name - the per-player
         * version of [mainKindAnchors]/[allKindAnchors], for [rivalAwardTally].
         *
         * Keyed off each medal's own [com.twinlore.app.journey.domain.model.Medal.ownerName] rather
         * than off `frame.rivals`' own index: that is the one thing a medal already carries that
         * ties it to a player, so this needs no assumption that a layout's rivals and a frame's
         * line up index for index - unlike [rivalTrackX], which the curves and tracks already share
         * that alignment for.
         */
        val rivalKindAnchors: Map<String, Array<FloatArray>> =
            layout.medals.mapNotNull { it.medal.ownerName }.distinct().associateWith { owner ->
                kindAnchors(layout.medals.filter { it.medal.ownerName == owner })
            }

        private fun kindAnchors(medals: List<MedalLayout>): Array<FloatArray> =
            Array(AwardKind.entries.size) { ordinal ->
                medals.filter { it.medal.kind.ordinal == ordinal }
                    .map { it.anchorX }
                    .sorted()
                    .toFloatArray()
            }
    }

    /** The unlock-list prefix sums, which depend on the frame rather than on the chart. */
    private class FrameTables(val frame: JourneyFrame) {
        /** `points[k]` is the score of the first k unlocks. */
        val points: IntArray = IntArray(frame.main.unlocks.size + 1).also { out ->
            frame.main.unlocks.forEachIndexed { i, unlock -> out[i + 1] = out[i] + unlock.points }
        }

        /** `hardcore[k]` is how many of the first k unlocks were earned in hardcore. */
        val hardcore: IntArray = IntArray(frame.main.unlocks.size + 1).also { out ->
            frame.main.unlocks.forEachIndexed { i, unlock ->
                out[i + 1] = out[i] + if (unlock.hardcore) 1 else 0
            }
        }

        /** `games[k]` is how many distinct games the first k unlocks came from. */
        val games: IntArray = IntArray(frame.main.unlocks.size + 1).also { out ->
            val seen = HashSet<Int>()
            frame.main.unlocks.forEachIndexed { i, unlock ->
                seen += unlock.gameId
                out[i + 1] = seen.size
            }
        }

        /** The same prefix sums, per rival, for [rivalPoints]. */
        val rivalPoints: Array<IntArray> = Array(frame.rivals.size) { r ->
            val unlocks = frame.rivals[r].unlocks
            IntArray(unlocks.size + 1).also { out ->
                unlocks.forEachIndexed { i, unlock -> out[i + 1] = out[i] + unlock.points }
            }
        }
    }

    // Two slots each, and volatile because the video export builds frames on a background thread
    // while nothing stops the chart from drawing on the main one. One reference write is atomic, so
    // a reader either sees a whole table set or the previous one - never a half-built mixture.
    //
    // Two rather than one because two charts are genuinely live at the same time: an export renders
    // the COMPACT layout while the player is still drawing the WIDE one, and the player itself
    // alternates between them on every play/pause edge. A single slot would be missed by both
    // callers on every call and rebuild the tables about thirty-seven times a second - slower than
    // the scans it replaced. Displacing into the second slot rather than overwriting is what keeps
    // two alternating keys both resident.
    @Volatile private var layoutTablesA: LayoutTables? = null
    @Volatile private var layoutTablesB: LayoutTables? = null
    @Volatile private var frameTablesA: FrameTables? = null
    @Volatile private var frameTablesB: FrameTables? = null

    private fun tablesFor(layout: ChartLayout): LayoutTables {
        layoutTablesA?.let { if (it.layout === layout) return it }
        layoutTablesB?.let { if (it.layout === layout) return it }
        val built = LayoutTables(layout)
        layoutTablesB = layoutTablesA
        layoutTablesA = built
        return built
    }

    private fun tablesFor(frame: JourneyFrame): FrameTables {
        frameTablesA?.let { if (it.frame === frame) return it }
        frameTablesB?.let { if (it.frame === frame) return it }
        val built = FrameTables(frame)
        frameTablesB = frameTablesA
        frameTablesA = built
        return built
    }

    fun stats(frame: JourneyFrame, layout: ChartLayout, progress: Float): HudStats {
        val unlocks = frame.main.unlocks
        val p = progress.coerceIn(0f, 1f)
        if (p >= 1f) {
            return HudStats(
                games = frame.main.games.size,
                unlocked = unlocks.size,
                points = unlocks.sumOf { it.points },
                journeyDateMillis = unlocks.lastOrNull()?.earnedAtMillis,
                hardcore = unlocks.count { it.hardcore },
            )
        }
        val revealed = revealedCount(layout, p).coerceIn(0, unlocks.size)
        val tables = tablesFor(frame)
        return HudStats(
            games = tables.games[revealed],
            unlocked = revealed,
            points = tables.points[revealed],
            journeyDateMillis = if (revealed == 0) null else unlocks[revealed - 1].earnedAtMillis,
            hardcore = tables.hardcore[revealed],
        )
    }

    /**
     * What each rival has scored at [progress], index for index with `frame.rivals`.
     *
     * The running counters are about the signed-in player, which is the right readout for a solo
     * journey and the wrong one for a chart whose whole point is who is ahead. Gated on the shared
     * sweep, the way a compare chart's own reveal is: a rival's unlock is drawn when the vertical
     * sweep line passes it, so the score beside their name moves with the dots on their curve.
     *
     * Tabulated per (layout, frame) like everything else here, so a call is a binary search and one
     * array read per rival.
     */
    fun rivalPoints(frame: JourneyFrame, layout: ChartLayout, progress: Float): IntArray {
        if (frame.rivals.isEmpty()) return IntArray(0)
        val revealed = rivalRevealed(frame, layout, progress)
        val frameTables = tablesFor(frame)
        return IntArray(frame.rivals.size) { r ->
            val prefix = frameTables.rivalPoints.getOrNull(r) ?: return@IntArray 0
            prefix[revealed[r].coerceIn(0, prefix.size - 1)]
        }
    }

    /**
     * The same breakdown [awardTally] gives the main player, one per rival, index for index with
     * `frame.rivals` - a rival's award dots move with the sweep the same way their score does.
     *
     * Gated exactly as [awardTally] and [awardsRevealed] are: a rival's medal counts once the
     * sweep passes ITS OWN anchor, not the main player's. Without this a running compare row's
     * awards would have to wait for the whole journey to land, the same bug [rivalPoints] fixed
     * for a rival's score.
     */
    fun rivalAwardTally(frame: JourneyFrame, layout: ChartLayout, progress: Float): List<AwardTally> {
        if (frame.rivals.isEmpty()) return emptyList()
        if (layout.medals.isEmpty()) return frame.rivals.map { AwardTally() }
        val tables = tablesFor(layout)
        val p = progress.coerceIn(0f, 1f)
        val limit = if (p >= 1f) Float.POSITIVE_INFINITY else sweepX(layout, p) + tolerance
        return frame.rivals.map { rival ->
            val anchors = tables.rivalKindAnchors[rival.name] ?: return@map AwardTally()
            AwardTally(
                mastered = countAtMost(anchors[AwardKind.MASTERED.ordinal], limit),
                completed = countAtMost(anchors[AwardKind.COMPLETED.ordinal], limit),
                beaten = countAtMost(anchors[AwardKind.BEATEN.ordinal], limit),
                beatenCasual = countAtMost(anchors[AwardKind.BEATEN_CASUAL.ordinal], limit),
            )
        }
    }

    /**
     * How many of each rival's unlocks the sweep has passed at [progress], index for index with
     * `frame.rivals` - what [rivalPoints] scores, before it is turned into a score.
     *
     * Split out because the count is a fact of its own: it is what a compare row's unlock count and
     * its duration are read off, and what an exported frame's strip cache has to be keyed on (see
     * `ExportFrame.ChipKey`) - a rival can cross an unlock worth no points at all, which moves both
     * of those and leaves [rivalPoints] exactly where it was.
     */
    fun rivalRevealed(frame: JourneyFrame, layout: ChartLayout, progress: Float): IntArray {
        if (frame.rivals.isEmpty()) return IntArray(0)
        val p = progress.coerceIn(0f, 1f)
        val layoutTables = tablesFor(layout)
        val limit = if (p >= 1f) Float.POSITIVE_INFINITY else sweepX(layout, p) + tolerance
        return IntArray(frame.rivals.size) { r ->
            val count = frame.rivals[r].unlocks.size
            val track = layoutTables.rivalTrackX.getOrNull(r) ?: return@IntArray 0
            countAtMost(track, limit).coerceIn(0, count)
        }
    }

    /**
     * How many award medallions the sweep has passed at [progress], gated on the anchor exactly as
     * [com.twinlore.app.journey.ui.chart.JourneyChartRenderer] draws them: a fanned-out cluster is
     * drawn far from the moment it was won, and the counter has to follow the moment.
     *
     * [mainOnly] (the default) counts the main player's own awards, which is what a readout about
     * this player's journey means; passing false counts every medal the chart has drawn, the
     * rivals' included.
     */
    fun awardsRevealed(layout: ChartLayout, progress: Float, mainOnly: Boolean = true): Int {
        if (layout.medals.isEmpty()) return 0
        val tables = tablesFor(layout)
        val anchors = if (mainOnly) tables.mainMedalAnchors else tables.allMedalAnchors
        if (anchors.isEmpty()) return 0
        val p = progress.coerceIn(0f, 1f)
        if (p >= 1f) return anchors.size
        return countAtMost(anchors, sweepX(layout, p) + tolerance)
    }

    /**
     * The same count as [awardsRevealed], split into the three kinds - which awards the journey has
     * come to, rather than only how many.
     *
     * Gated identically, award for award: each kind is counted against its own sorted anchors with
     * the same sweep and the same tolerance, so [AwardTally.total] is [awardsRevealed] exactly, at
     * every point of playback and in both modes.
     */
    fun awardTally(layout: ChartLayout, progress: Float, mainOnly: Boolean = true): AwardTally {
        if (layout.medals.isEmpty()) return AwardTally()
        val tables = tablesFor(layout)
        val anchors = if (mainOnly) tables.mainKindAnchors else tables.allKindAnchors
        val p = progress.coerceIn(0f, 1f)
        val limit = if (p >= 1f) Float.POSITIVE_INFINITY else sweepX(layout, p) + tolerance
        return AwardTally(
            mastered = countAtMost(anchors[AwardKind.MASTERED.ordinal], limit),
            completed = countAtMost(anchors[AwardKind.COMPLETED.ordinal], limit),
            beaten = countAtMost(anchors[AwardKind.BEATEN.ordinal], limit),
            beatenCasual = countAtMost(anchors[AwardKind.BEATEN_CASUAL.ordinal], limit),
        )
    }

    /**
     * The time under the runner: the axis date at the sweep's x, which is where the viewer's eye
     * is - the profile icon riding the curve - rather than the timestamp of the last unlock it
     * passed. On a chart whose unlocks cluster, the two can be weeks apart; the header follows
     * this one so the date it says and the position it shows are the same fact.
     *
     * Race mode's axis is elapsed rather than calendar time, and the one surface that draws a
     * race (the single-game compare row) has no date on it - so a race answers null and callers
     * keep whatever they were showing.
     */
    fun axisDateMillis(layout: ChartLayout, progress: Float): Long? {
        if (layout.raceMode) return null
        val inner = layout.width - layout.padL - layout.padR
        val span = layout.axisT1 - layout.axisT0
        if (inner <= 0f || span <= 0L) return null
        val frac = ((sweepX(layout, progress) - layout.padL) / inner).coerceIn(0f, 1f)
        return layout.axisT0 + (frac * span).toLong()
    }

    /**
     * How much ELAPSED time the shared sweep stands for at [progress]: the cut a race chart's rows
     * are taken at ([com.twinlore.app.journey.domain.frameUpToElapsed]).
     *
     * The same gate [rivalRevealed] applies, said in the axis's own units instead of in x. A race
     * chart puts every player's unlock at `padL + innerW * elapsed / eMax` measured from that
     * player's own first one ([com.twinlore.app.journey.domain.LayoutEngine]'s `xForPlayer`), so
     * keeping the unlocks at or left of `sweepX + tolerance` is exactly keeping the ones whose own
     * elapsed time is at or under this - for each player independently, which is the whole point:
     * one absolute instant would cut a rival by the main player's calendar.
     *
     * [Long.MAX_VALUE] once playback lands, so the end state is the journey itself rather than an
     * arithmetic approximation of it, and for a layout with no axis to measure against.
     *
     * Only meaningful on a race chart, which is the only place it is called from: `isGameCompare`
     * (GAME with rivals) and `ChartLayout.raceMode` are the same predicate over the same frame.
     */
    fun sweepElapsedMillis(layout: ChartLayout, progress: Float): Long {
        val p = progress.coerceIn(0f, 1f)
        if (p >= 1f) return Long.MAX_VALUE
        val inner = layout.width - layout.padL - layout.padR
        if (inner <= 0f || layout.eMaxMillis <= 0L) return Long.MAX_VALUE
        val frac = ((sweepX(layout, p) + tolerance - layout.padL) / inner).coerceAtLeast(0f)
        // In double: a float product would quantize a year-long axis to whole seconds, and this
        // number is compared against raw unlock timestamps.
        return (frac.toDouble() * layout.eMaxMillis).toLong()
    }

    /**
     * The x the sweep has reached, on the polyline through the layout's own points rather than on
     * the smoothed cubic the renderer measures - the same approximation, and for the same reason,
     * as [revealedCount] above.
     */
    fun sweepX(layout: ChartLayout, progress: Float): Float {
        val p = progress.coerceIn(0f, 1f)
        val tables = tablesFor(layout)
        if (tables.compare) return layout.padL + p * (layout.width - layout.padL - layout.padR)

        val points = layout.main.points
        if (points.isEmpty()) return layout.padL
        if (points.size == 1) return points[0].x

        val lengths = tables.lengths
        val total = lengths[lengths.size - 1]
        if (total <= 0f) return points.last().x

        val target = p * total
        // The first segment whose far end is at or past the target. The walk-and-accumulate this
        // replaces summed the very same hypot terms in the very same order, so `lengths[i - 1]` is
        // bit for bit the `walked` it would have arrived at.
        val i = firstAtLeast(lengths, target, from = 1)
        if (i >= points.size) return points.last().x
        val walked = lengths[i - 1]
        // Recomputed rather than differenced out of the table: `lengths[i] - walked` is only
        // approximately the segment's own hypot, and this value divides.
        val segment = hypot(points[i].x - points[i - 1].x, points[i].y - points[i - 1].y)
        val t = if (segment <= 0f) 0f else (target - walked) / segment
        return points[i - 1].x + (points[i].x - points[i - 1].x) * t
    }

    /**
     * Whether the main player's reveal is currently inside one of their streak runs.
     *
     * The same question the chart answers when it sets the line on fire (see
     * `JourneyChartRenderer.drawStreakFire`), asked in x rather than in arc length because that is
     * what this file can measure without Android's `PathMeasure` - the two agree to well inside the
     * 1% granularity anything reads this at. Measured against the MAIN player's tip: on a compare
     * chart the shared sweep runs on past the end of their curve, and a streak that ended when they
     * stopped playing must not stay lit for the rest of the journey.
     *
     * Cheap on purpose. The runs are precomputed at layout time ([ChartLayout.streaks]), so this is
     * a walk over a handful of float pairs, and callers are expected to ask at a coarse cadence.
     */
    fun inStreak(layout: ChartLayout, progress: Float): Boolean {
        val streaks = layout.streaks
        if (streaks.isEmpty()) return false
        val sweep = sweepX(layout, progress)
        val x = if (tablesFor(layout).compare) minOf(sweep, layout.main.maxX) else sweep
        return streaks.any { x >= it.startX - tolerance && x <= it.endX + tolerance }
    }

    /**
     * How many of the main player's unlocks the sweep has passed at [progress].
     *
     * Compare mode sweeps a shared vertical line across the plot area (so a rival who kept
     * unlocking still finishes), which is linear in x; solo playback reveals the main curve by arc
     * length, which is not.
     */
    fun revealedCount(layout: ChartLayout, progress: Float): Int {
        val track = layout.main.bgTrack
        if (track.isEmpty()) return 0
        val p = progress.coerceIn(0f, 1f)
        val tables = tablesFor(layout)
        if (tables.compare) {
            val sweepX = layout.padL + p * (layout.width - layout.padL - layout.padR)
            return countAtMost(tables.trackX, sweepX + tolerance)
        }
        // points[0] is the curve's leading zero point, so unlock i sits on points[i + 1].
        if (layout.main.points.size < 2) return 0
        val lengths = tables.lengths
        val total = lengths[lengths.size - 1]
        if (total <= 0f) return if (p > 0f) track.size else 0
        return countAtMost(tables.entryLengths, p * total + tolerance)
    }

    /**
     * How many leading entries of an ascending [values] are at most [limit].
     *
     * The array is non-decreasing in every caller here, so this is exactly the "count until the
     * first one that is too far" the scans it replaces did - without walking the whole thing.
     */
    private fun countAtMost(values: FloatArray, limit: Float): Int {
        var lo = 0
        var hi = values.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (values[mid] <= limit) lo = mid + 1 else hi = mid
        }
        return lo
    }

    /** The first index at or after [from] whose value is at least [target]; [values].size if none. */
    private fun firstAtLeast(values: FloatArray, target: Float, from: Int): Int {
        var lo = from
        var hi = values.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (values[mid] >= target) hi = mid else lo = mid + 1
        }
        return lo
    }
}
