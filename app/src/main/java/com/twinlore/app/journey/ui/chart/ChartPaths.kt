package com.twinlore.app.journey.ui.chart

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import com.twinlore.app.data.retroachievements.RaUrls
import com.twinlore.app.journey.domain.ChartLayout
import com.twinlore.app.journey.domain.CurveLayout
import com.twinlore.app.journey.domain.LayoutEngine
import com.twinlore.app.journey.domain.P
import kotlin.math.abs

/**
 * The smoothed curves of a [ChartLayout] as measurable [Path]s, built once per layout and reused
 * by every frame of playback.
 *
 * Playback reveals a curve by arc length, but everything the chart has to line up against the
 * reveal (rival curves, badge nodes, medals) is positioned in x. [lengthAtX] bridges the two by
 * bisecting the path's arc length: the curves are x-monotonic by construction (LayoutEngine's
 * cubic control points sit at the segment midpoints), so 24 halvings put the answer well inside
 * a pixel on a 1060-unit-wide chart.
 */
class ChartPaths(layout: ChartLayout) {

    private class Curve(
        val path: Path,
        val measure: PathMeasure,
        val length: Float,
        val startX: Float,
        val endX: Float,
    )

    private val curves: List<Curve> = buildList {
        add(build(layout.main))
        layout.rivals.forEach { add(build(it)) }
    }

    val mainPath: Path get() = curves[0].path
    val mainLength: Float get() = curves[0].length

    /** Rival curve paths in [ChartLayout.rivals] order, each with its total arc length. */
    val rivalPaths: List<Pair<Path, Float>> =
        curves.drop(1).map { it.path to it.length }

    /**
     * Arc length along the main curve of every badge node's anchor, in [ChartLayout.nodes] order.
     * A node becomes visible once the reveal has passed its entry here.
     *
     * The main player's badges only. A rival's badge belongs to a different line, so an arc length
     * on this one would be a meaningless number that the renderer must never use (it gates those
     * on the shared sweep instead, like the medallions) - and computing it would spend two dozen
     * bisections per rival badge to produce it. Zero says "not measured here" and, being behind
     * every reveal, could only ever fail safe.
     */
    val nodeLengths: FloatArray = FloatArray(layout.nodes.size) { i ->
        val node = layout.nodes[i]
        if (node.ownerName == null) lengthAtX(MAIN, node.anchorX) else 0f
    }

    /**
     * Per-unlock marker positions on each rival curve, as (x, point) pairs in ascending x.
     * Precomputed because the alternative is bisecting every rival's path for every dot on every
     * frame of playback, which is thousands of native path calls per vsync in a span with rivals.
     */
    private val rivalDots: List<List<Pair<Float, Offset>>> =
        layout.rivals.mapIndexed { index, rival ->
            rival.bgTrack.map { (x, _) -> x to positionAt(index, lengthAtX(index, x)) }
        }

    /**
     * The key each badge node's art is stored under in the decoded-image map, in
     * [ChartLayout.nodes] order.
     *
     * Built here rather than in the draw pass because the draw pass ran it per node per frame:
     * `RaUrls.badge` is a concatenation, so four hundred icons at sixty frames a second was
     * ~24,000 throwaway Strings a second, each one then hashed from scratch for the map lookup. A
     * String caches its own hash after the first time, so a key built once here costs nothing to
     * look up ever again.
     */
    val nodeBadgeUrls: List<String> = layout.nodes.map { RaUrls.badge(it.unlock.badgeName) }

    /**
     * Scratch paths the renderer reuses every frame: the revealed segment of whichever curve it
     * is stroking, and the circular clip for badge/avatar images. They live here, alongside the
     * paths themselves, because this object is remembered per layout while the draw pass runs
     * sixty times a second - allocating a Path per curve and per image per frame is exactly the
     * kind of steady garbage this device cannot afford.
     */
    internal val revealScratch: Path = Path()
    internal val clipScratch: Path = Path()

    /**
     * The main curve's arc-length bounds of every streak run the layout found, in order. Two
     * bisections per run, once per layout, so the fire's per-frame question ("which stretches of
     * the line burn, and how much of each is revealed") is a walk over a handful of float pairs.
     */
    val streakLengths: List<Pair<Float, Float>> =
        layout.streaks.map { lengthAtX(MAIN, it.startX) to lengthAtX(MAIN, it.endX) }

    /**
     * And for the burning stretches inside streaks. Its own path rather than [revealScratch]: the
     * fire is drawn in the same pass as the main curve, which is still holding that one.
     */
    internal val fireScratch: Path = Path()

    /**
     * Where each streak run starts and ends on the stage, in [streakLengths] order. Static per
     * layout, so the fire's gradient endpoints never cost a native getPosition during a frame.
     */
    internal val streakEnds: List<Pair<Offset, Offset>> =
        streakLengths.map { (from, to) -> positionAt(MAIN, from) to positionAt(MAIN, to) }

    /** The rivals' streak runs, per rival, in the same arc-length shape as [streakLengths]. */
    internal val rivalStreakLengths: List<List<Pair<Float, Float>>> =
        layout.rivalStreaks.mapIndexed { i, runs ->
            runs.map { lengthAtX(i, it.startX) to lengthAtX(i, it.endX) }
        }

    /** And their endpoint offsets, per rival; see [streakEnds]. */
    internal val rivalStreakEnds: List<List<Pair<Offset, Offset>>> =
        rivalStreakLengths.mapIndexed { i, runs ->
            runs.map { (from, to) -> positionAt(i, from) to positionAt(i, to) }
        }

    // A fully revealed streak's stretch of curve is a fixed shape under a fixed gradient, and at
    // any instant of playback every streak behind the sweep is fully revealed - only the one the
    // sweep is inside is still growing. Uncached, a ten-year journey's dozens of finished runs
    // each paid a native getSegment, a Path reset and a Brush allocation per frame to rebuild the
    // same geometry.
    private val completeFire = arrayOfNulls<Pair<Path, Brush>>(streakLengths.size)
    private val rivalCompleteFire: List<Array<Pair<Path, Brush>?>> =
        rivalStreakLengths.map { arrayOfNulls(it.size) }

    /**
     * The finished segment of streak [index] with its gradient, built on first ask and reused
     * every frame after.
     */
    internal fun completeFireStretch(index: Int): Pair<Path, Brush>? {
        completeFire[index]?.let { return it }
        val (from, to) = streakLengths.getOrNull(index) ?: return null
        val built = buildFire(MAIN, from, to, streakEnds[index]) ?: return null
        completeFire[index] = built
        return built
    }

    /** [completeFireStretch] for rival [rival]'s run [index]. */
    internal fun completeRivalFireStretch(rival: Int, index: Int): Pair<Path, Brush>? {
        val cache = rivalCompleteFire.getOrNull(rival) ?: return null
        cache[index]?.let { return it }
        val (from, to) = rivalStreakLengths[rival].getOrNull(index) ?: return null
        val built = buildFire(rival, from, to, rivalStreakEnds[rival][index]) ?: return null
        cache[index] = built
        return built
    }

    private fun buildFire(curve: Int, from: Float, to: Float, ends: Pair<Offset, Offset>): Pair<Path, Brush>? {
        val path = Path()
        if (!segmentInto(curve, from, to, path)) return null
        return path to Brush.linearGradient(
            colors = listOf(FLAME_OUTER, FLAME_INNER),
            start = ends.first,
            end = ends.second,
        )
    }

    /** Marker positions for rival [index], as (x, point) pairs in ascending x. */
    fun dotsOf(index: Int): List<Pair<Float, Offset>> = rivalDots.getOrElse(index) { emptyList() }

    /** Arc length at which [x] is reached on the given curve ([MAIN] or a rival index). */
    fun lengthAtX(pathIndex: Int, x: Float): Float {
        val curve = curveAt(pathIndex) ?: return 0f
        if (curve.length <= 0f) return 0f
        if (x <= curve.startX) return 0f
        if (x >= curve.endX) return curve.length

        var lo = 0f
        var hi = curve.length
        repeat(BISECTION_STEPS) {
            val mid = (lo + hi) / 2f
            if (curve.measure.getPosition(mid).x < x) lo = mid else hi = mid
        }
        return (lo + hi) / 2f
    }

    /** Point at [distance] along the given curve; the curve's origin when it has no length. */
    fun positionAt(pathIndex: Int, distance: Float): Offset {
        val curve = curveAt(pathIndex) ?: return Offset.Zero
        if (curve.length <= 0f) return curve.measure.getPosition(0f)
        return curve.measure.getPosition(distance.coerceIn(0f, curve.length))
    }

    /** Total arc length of the given curve. */
    fun lengthOf(pathIndex: Int): Float = curveAt(pathIndex)?.length ?: 0f

    /**
     * Writes the first [length] units of the given curve into [destination] (cleared first), and
     * reports whether anything was written. This is the shape playback actually strokes.
     */
    fun revealInto(pathIndex: Int, length: Float, destination: Path): Boolean {
        val curve = curveAt(pathIndex) ?: return false
        destination.reset()
        if (curve.length <= 0f || length <= 0f) return false
        return curve.measure.getSegment(0f, length.coerceAtMost(curve.length), destination, true)
    }

    /**
     * Writes the stretch of the given curve between [start] and [end] into [destination] (cleared
     * first), and reports whether anything was written. [revealInto] with a left edge, for the fire
     * treatment inside a streak.
     */
    fun segmentInto(pathIndex: Int, start: Float, end: Float, destination: Path): Boolean {
        val curve = curveAt(pathIndex) ?: return false
        destination.reset()
        if (curve.length <= 0f) return false
        val from = start.coerceIn(0f, curve.length)
        val to = end.coerceIn(0f, curve.length)
        if (to - from <= 0f) return false
        return curve.measure.getSegment(from, to, destination, true)
    }

    private fun curveAt(pathIndex: Int): Curve? =
        curves.getOrNull(if (pathIndex == MAIN) 0 else pathIndex + 1)

    private fun build(curve: CurveLayout): Curve {
        val path = Path()
        val points = decimate(curve.points)
        if (points.isNotEmpty()) {
            path.moveTo(points[0].x, points[0].y)
            LayoutEngine.smoothPathSegments(points).forEach { seg ->
                path.cubicTo(seg.c1.x, seg.c1.y, seg.c2.x, seg.c2.y, seg.end.x, seg.end.y)
            }
        }
        val measure = PathMeasure()
        measure.setPath(path, false)
        return Curve(
            path = path,
            measure = measure,
            length = measure.length,
            startX = points.firstOrNull()?.x ?: 0f,
            endX = points.lastOrNull()?.x ?: 0f,
        )
    }

    /**
     * [points] with runs the eye cannot resolve collapsed: a point lands in the path only when it
     * has moved at least [DECIMATE_UNITS] in x or y from the last one kept. First and last always
     * survive, so the curve's extent - and with it [lengthAtX]'s edge answers - is exact.
     *
     * The curve carries one point per unlock, and a span of thousands of unlocks put several
     * points per virtual unit into a cubic per point. Every per-frame cost downstream scales with
     * that count - the reveal's segment copy, the stroke's tessellation, the bisections - and all
     * of it was being spent on wiggles smaller than the stroke drawn over them. The error is
     * bounded by the threshold (each skip is measured from the last KEPT point), which is well
     * under the main line's own width. Everything that measures the curve - node reveal lengths,
     * the runner, the sweep - reads the decimated path through this same object, so the chart
     * stays consistent with itself.
     */
    private fun decimate(points: List<P>): List<P> {
        if (points.size <= 2) return points
        val out = ArrayList<P>(points.size)
        out += points.first()
        for (i in 1 until points.size - 1) {
            val last = out.last()
            val p = points[i]
            if (abs(p.x - last.x) >= DECIMATE_UNITS || abs(p.y - last.y) >= DECIMATE_UNITS) out += p
        }
        out += points.last()
        return out
    }

    companion object {
        /** [lengthAtX]/[positionAt] index for the main player's curve. */
        const val MAIN = -1
        private const val BISECTION_STEPS = 24

        /**
         * See [decimate]. In VIRTUAL units, which is what makes it hold at every zoom: a skip is
         * bounded by this in x and in y, so ~2.1 units diagonally, against a main line 4.5 units
         * wide - under half a stroke, whatever the magnification, because zoom scales the drawn
         * error and the drawn stroke by the same factor. (It is emphatically not sub-pixel at the
         * top of the range, which an earlier version of this comment claimed; at 8x on a landscape
         * canvas one unit is around fifteen pixels. Staying inside the line is the property that
         * matters and the one that is zoom-invariant.)
         */
        private const val DECIMATE_UNITS = 1.5f
    }
}
