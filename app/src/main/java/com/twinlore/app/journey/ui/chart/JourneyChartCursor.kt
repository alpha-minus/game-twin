package com.twinlore.app.journey.ui.chart

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.ui.geometry.Offset
import com.twinlore.app.journey.domain.ChartLayout

/**
 * Where the chart's virtual cursor is sitting. The chart is a single semantics node, so the D-pad
 * walks this rather than a focusable per icon; both kinds of icon are reachable because on a
 * handheld with no touch habit the medallions were otherwise unreachable entirely, and in compare
 * and awards modes (which draw no badge nodes) that meant no path to a tooltip at all.
 */
sealed interface ChartCursor {
    data class Node(val index: Int) : ChartCursor
    data class Medal(val index: Int) : ChartCursor
}

/**
 * A way in to the chart's tooltip from outside it, for the one caller that has to know: the
 * player's Back handling, which dismisses an open tooltip before it will close the player.
 *
 * The cursor itself stays inside the chart, keyed on the journey and the layout the way everything
 * else there is (the two charts draw different icon subsets, so a tooltip must not survive a swap).
 * This binds to whichever cursor is current rather than owning one, so that keying is untouched and
 * there is no second copy of the tooltip's state to keep in step.
 */
@Stable
class ChartCursorHandle {
    private var state: MutableState<ChartCursor?>? = null

    /** True while a tooltip is open. Read on a key press, not during composition. */
    val isOpen: Boolean get() = state?.value != null

    /** Closes the tooltip, if there is one. */
    fun clear() {
        state?.value = null
    }

    internal fun bind(cursor: MutableState<ChartCursor?>) {
        state = cursor
    }

    /** The chart has left composition: there is no tooltip to speak for any more. */
    internal fun unbind() {
        state = null
    }
}

/** What a tap on the chart turned out to be about. */
enum class ChartTapOutcome {
    /** It landed on a badge or a medal, and the tooltip moved there. */
    SELECTED,

    /** It missed, and closing the tooltip that was open is what it was for. */
    DISMISSED,

    /** It missed with no tooltip to close, so it was about the chart itself. */
    MISSED,
}

/**
 * Which of the three a tap was, given the cursor before it ([previous]) and the icon it landed on
 * ([hit], null for a miss).
 *
 * Dismissing a tooltip *consumes* the tap. A tooltip is closed by tapping away from it, so a
 * dismissal and a plain miss arrive as exactly the same gesture; treating both as "the user wants
 * the chrome out of the way" meant one tap closed the tooltip and flipped the control bar at the
 * same time, and there was no way to do either on its own.
 */
fun tapOutcome(previous: ChartCursor?, hit: ChartCursor?): ChartTapOutcome = when {
    hit != null -> ChartTapOutcome.SELECTED
    previous != null -> ChartTapOutcome.DISMISSED
    else -> ChartTapOutcome.MISSED
}

/**
 * Every icon the cursor can currently land on, ordered by where it belongs on the curve.
 *
 * Both index lists are the *painted* icons - `visibleNodeIndices` and `drawnMedalIndices` - so a
 * cursor stop always has a disc under it. Ordering, on the other hand, is by anchor and not by
 * drawn position: relaxation fans a crowded cluster out by up to ~90 virtual units, and stepping
 * through medals in fanned-out order rather than chronological order would make the D-pad walk
 * look like it was jumping around in time.
 */
fun mergeCursorStops(
    layout: ChartLayout,
    drawnNodes: List<Int>,
    drawnMedals: List<Int>,
): List<ChartCursor> {
    if (drawnNodes.isEmpty() && drawnMedals.isEmpty()) return emptyList()
    val stops = ArrayList<Pair<Float, ChartCursor>>(drawnNodes.size + drawnMedals.size)
    drawnNodes.forEach { i ->
        layout.nodes.getOrNull(i)?.let { stops += it.anchorX to ChartCursor.Node(i) }
    }
    drawnMedals.forEach { i ->
        layout.medals.getOrNull(i)?.let { stops += it.anchorX to ChartCursor.Medal(i) }
    }
    // Medals sort ahead of badges at the same anchor: the award is the headline of that moment.
    stops.sortWith(
        compareBy<Pair<Float, ChartCursor>> { it.first }
            .thenBy { if (it.second is ChartCursor.Medal) 0 else 1 },
    )
    return stops.map { it.second }
}

/** The stop a left/right press moves to, or null when there is nowhere further to go. */
fun nextCursorStop(stops: List<ChartCursor>, current: ChartCursor?, forward: Boolean): ChartCursor? {
    if (stops.isEmpty()) return null
    if (current == null) return if (forward) stops.first() else stops.last()
    val at = stops.indexOf(current)
    if (at < 0) return if (forward) stops.first() else stops.last()
    return stops.getOrNull(if (forward) at + 1 else at - 1)
}

/**
 * Where a stop is actually drawn, for panning it back into a zoomed viewport. The drawn centre is
 * the right answer for both kinds of icon *because* [mergeCursorStops] is fed painted icons only:
 * every stop this can be asked about has its disc on stage at the position it returns.
 */
fun ChartCursor.drawnAt(layout: ChartLayout): Offset? = when (this) {
    is ChartCursor.Node -> layout.nodes.getOrNull(index)?.let { Offset(it.x, it.y) }
    is ChartCursor.Medal -> layout.medals.getOrNull(index)?.let { Offset(it.x, it.y) }
}
