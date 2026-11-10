package com.twinlore.app.journey.ui.chart

import com.twinlore.app.journey.domain.ChartLayout
import com.twinlore.app.journey.domain.LayoutEngine
import com.twinlore.app.journey.domain.model.AchievementType
import com.twinlore.app.journey.domain.model.AwardKind
import com.twinlore.app.journey.domain.model.GameRef
import com.twinlore.app.journey.domain.model.JourneyFrame
import com.twinlore.app.journey.domain.model.JourneyMode
import com.twinlore.app.journey.domain.model.Medal
import com.twinlore.app.journey.domain.model.PlayerJourney
import com.twinlore.app.journey.domain.model.Unlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JourneyChartCursorTest {

    private val day = 86_400_000L
    private val t0 = 1_760_000_000_000L

    private fun unlock(id: Int, at: Long, pts: Int) = Unlock(
        id = id, title = "A$id", description = "", points = pts, badgeName = "b$id",
        type = AchievementType.STANDARD, earnedAtMillis = at, numAwarded = 10, gameId = 1,
    )

    private fun layoutOf(medals: List<Medal>, includeNodes: Boolean): ChartLayout {
        val unlocks = (0 until 4).map { unlock(it, t0 + it * 10 * day, 10) }
        val main = PlayerJourney(
            name = "me", unlocks = unlocks,
            games = mapOf(1 to GameRef(1, "G1", null, emptyList(), 0, 100, null)),
            medals = medals,
        )
        val frame = JourneyFrame(
            JourneyMode.SPAN, main, emptyList(), 0, t0 + 100 * day,
        )
        return LayoutEngine.layout(frame, includeNodes = includeNodes)
    }

    @Test fun `stops merge badges and medals in curve order`() {
        val layout = layoutOf(
            listOf(Medal(AwardKind.MASTERED, t0 + 15 * day, 1, null, "G1")),
            includeNodes = true,
        )
        val stops = mergeCursorStops(layout, layout.nodes.indices.toList(), layout.medals.indices.toList())

        assertEquals(layout.nodes.size + layout.medals.size, stops.size)
        // Ordered by anchor along the curve, medal slotted into its chronological place.
        val anchors = stops.map { stop ->
            when (stop) {
                is ChartCursor.Node -> layout.nodes[stop.index].anchorX
                is ChartCursor.Medal -> layout.medals[stop.index].anchorX
            }
        }
        assertEquals(anchors.sorted(), anchors)
        assertTrue("the medal must be reachable", stops.any { it is ChartCursor.Medal })
    }

    /** Compare and awards modes draw no badges; without medal stops the D-pad had nowhere to go. */
    @Test fun `medals alone are still reachable`() {
        val layout = layoutOf(
            listOf(
                Medal(AwardKind.BEATEN, t0 + 5 * day, 1, null, "G1"),
                Medal(AwardKind.MASTERED, t0 + 25 * day, 1, null, "G1"),
            ),
            includeNodes = false,
        )
        assertTrue("fixture must have no badge nodes", layout.nodes.isEmpty())

        val stops = mergeCursorStops(layout, emptyList(), layout.medals.indices.toList())
        assertEquals(2, stops.size)
        assertTrue(stops.all { it is ChartCursor.Medal })

        val first = nextCursorStop(stops, null, forward = true)
        assertEquals(stops.first(), first)
        assertEquals(stops.last(), nextCursorStop(stops, null, forward = false))
    }

    @Test fun `stepping walks the merged list and stops at the ends`() {
        val stops = listOf(
            ChartCursor.Node(0), ChartCursor.Medal(0), ChartCursor.Node(1),
        )
        assertEquals(ChartCursor.Medal(0), nextCursorStop(stops, ChartCursor.Node(0), forward = true))
        assertEquals(ChartCursor.Node(1), nextCursorStop(stops, ChartCursor.Medal(0), forward = true))
        assertNull(nextCursorStop(stops, ChartCursor.Node(1), forward = true))
        assertEquals(ChartCursor.Medal(0), nextCursorStop(stops, ChartCursor.Node(1), forward = false))
        assertNull(nextCursorStop(stops, ChartCursor.Node(0), forward = false))
    }

    @Test fun `a cursor that is no longer a stop restarts from the end it came from`() {
        val stops = listOf(ChartCursor.Node(0), ChartCursor.Node(1))
        assertEquals(ChartCursor.Node(0), nextCursorStop(stops, ChartCursor.Medal(7), forward = true))
        assertEquals(ChartCursor.Node(1), nextCursorStop(stops, ChartCursor.Medal(7), forward = false))
        assertNull(nextCursorStop(emptyList(), null, forward = true))
    }

    // --- tap outcomes ---

    @Test fun `a tap on an icon selects it`() {
        assertEquals(
            ChartTapOutcome.SELECTED,
            tapOutcome(previous = null, hit = ChartCursor.Node(2)),
        )
        // Moving straight from one icon's tooltip to another's is still a selection.
        assertEquals(
            ChartTapOutcome.SELECTED,
            tapOutcome(previous = ChartCursor.Medal(0), hit = ChartCursor.Node(2)),
        )
    }

    /**
     * The tap that closes a tooltip is spent on the tooltip. It is the same gesture as a plain miss
     * - tapping away from the icon - so reporting it as one made a single tap both close the
     * tooltip and toggle the player's control bar, with no way to ask for either on its own.
     */
    @Test fun `a miss with a tooltip open dismisses it and nothing else`() {
        assertEquals(
            ChartTapOutcome.DISMISSED,
            tapOutcome(previous = ChartCursor.Node(1), hit = null),
        )
        assertEquals(
            ChartTapOutcome.DISMISSED,
            tapOutcome(previous = ChartCursor.Medal(3), hit = null),
        )
    }

    @Test fun `a miss with nothing open is about the chart itself`() {
        assertEquals(ChartTapOutcome.MISSED, tapOutcome(previous = null, hit = null))
    }

    @Test fun `a stop knows where it is drawn`() {
        val layout = layoutOf(
            listOf(Medal(AwardKind.MASTERED, t0 + 15 * day, 1, null, "G1")),
            includeNodes = true,
        )
        val medal = layout.medals.first()
        assertEquals(medal.x, ChartCursor.Medal(0).drawnAt(layout)!!.x, 0.01f)
        assertEquals(medal.y, ChartCursor.Medal(0).drawnAt(layout)!!.y, 0.01f)
        val node = layout.nodes.first()
        assertEquals(node.x, ChartCursor.Node(0).drawnAt(layout)!!.x, 0.01f)
        assertNull(ChartCursor.Node(99).drawnAt(layout))
    }
}
