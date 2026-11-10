package com.twinlore.app.journey.domain

import com.twinlore.app.journey.domain.model.AchievementType
import com.twinlore.app.journey.domain.model.GameRef
import com.twinlore.app.journey.domain.model.JourneyFrame
import com.twinlore.app.journey.domain.model.JourneyMode
import com.twinlore.app.journey.domain.model.PlayerJourney
import com.twinlore.app.journey.domain.model.Unlock
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How much of the plot's height the icons actually occupy, at the start of a journey.
 *
 * A cumulative-points curve begins at the bottom of the stage, so the early stretch is where every
 * badge is anchored low with the whole plot empty above it. The placement used to drop each icon
 * straight onto the curve and leave the collision pass to sort them out - and collisions stop
 * pushing the moment two discs are no longer touching, so a crowded opening settled into a tight
 * blob hugging the line: measured on this fixture, the first tenth of the timeline used a third of
 * the band on the compact chart and a sixth of it on the wide one.
 *
 * [LayoutEngine] answers it by slackening the pull that drags an icon back to its anchor in y
 * while leaving it firm in x, so a crowded moment fans into the height going spare without any
 * badge moving along the timeline. These are the floors that keep it that way; they sit well
 * under what the fixture currently achieves, so ordinary retuning does not trip them and a
 * collapse back to a blob does.
 */
class IconHeightUsageTest {

    private val day = 86_400_000L

    /** A steady climber: 550 days, fifteen unlocks a day, ~110 games - a dense real-world span. */
    private fun denseFrame(): JourneyFrame {
        var id = 0
        val unlocks = (0 until 550).flatMap { d ->
            (0 until 15).map { n ->
                Unlock(
                    id = id++, title = "A$id", description = "", points = 5 + (id % 20),
                    badgeName = "b$id", type = AchievementType.STANDARD,
                    earnedAtMillis = d * day + n * 3_600_000L, numAwarded = 10, gameId = d / 5 + 1,
                )
            }
        }
        val games = (1..120).associateWith { GameRef(it, "G$it", null, emptyList(), it % 12, 100, null) }
        return JourneyFrame(
            JourneyMode.SPAN,
            PlayerJourney("me", unlocks, games, emptyList()),
            emptyList(), 0L, 550 * day,
        )
    }

    /** What fraction of the plot band the icons left of [xFraction] of the axis span between them. */
    private fun bandUsage(layout: ChartLayout, xFraction: Float): Float {
        val plotTop = LayoutEngine.PAD_T
        val plotBottom = LayoutEngine.H - LayoutEngine.PAD_B
        val cut = layout.padL + (layout.width - layout.padL - layout.padR) * xFraction
        val early = layout.nodes.filter { it.x <= cut }
        if (early.size < 2) return 1f
        return (early.maxOf { it.y } - early.minOf { it.y }) / (plotBottom - plotTop)
    }

    @Test
    fun `the opening stretch spreads its icons over the plot rather than hugging the curve`() {
        val frame = denseFrame()
        // Per stage, because the two are different pictures. The compact chart is where the
        // crowding is - one screenful holding the lot - and it is what the opening complaint was
        // about; the wide chart has already answered most of it with width, and pushing its icons
        // further off the line turns them into branches rather than a cloud.
        listOf(1f to (0.40f to 0.42f), 4f to (0.20f to 0.26f)).forEach { (widthFactor, floors) ->
            val (tenthFloor, quarterFloor) = floors
            val layout = LayoutEngine.layout(frame, includeNodes = true, widthFactor = widthFactor)
            val tenth = bandUsage(layout, 0.1f)
            val quarter = bandUsage(layout, 0.25f)
            assertTrue(
                "widthFactor $widthFactor: the first tenth uses only ${(tenth * 100).toInt()}% of the plot band",
                tenth >= tenthFloor,
            )
            assertTrue(
                "widthFactor $widthFactor: the first quarter uses only ${(quarter * 100).toInt()}% of the band",
                quarter >= quarterFloor,
            )
        }
    }

    /**
     * And the cloud still follows the curve. The spread is a slackened pull, not a licence to
     * leave: an explicit vertical stack per division scored better on paper and drew branches
     * growing off the line, so this pins the average icon near it.
     */
    @Test
    fun `icons stay close to the curve on the wide stage`() {
        val layout = LayoutEngine.layout(denseFrame(), includeNodes = true, widthFactor = 4f)
        val meanDrift = layout.nodes.map { kotlin.math.abs(it.y - it.anchorY) }.average()
        assertTrue(
            "icons drift ${meanDrift.toInt()}u off the curve on average, which reads as branches",
            meanDrift <= 35.0,
        )
    }

    /**
     * The curve is drawn over the icons on purpose, so no badge may sit centred on it: the line
     * would be painted straight through the middle of the art. On a long dense span that was most
     * of them.
     *
     * Asserted against each badge's own anchor, which IS a point on its owner's curve - the line
     * passes through that height by construction, so clearance from it is clearance from the line.
     * Icons pinned against the top or bottom of the stage are exempt: there is nowhere left for
     * them to go, and the clamp has the last word.
     */
    @Test
    fun `no badge sits centred on the curve`() {
        listOf(1f, 4f).forEach { widthFactor ->
            val layout = LayoutEngine.layout(denseFrame(), includeNodes = true, widthFactor = widthFactor)
            val top = LayoutEngine.PAD_T - 24f
            val bottom = LayoutEngine.H - LayoutEngine.PAD_B
            val crossed = layout.nodes.filter { node ->
                val pinned = node.y <= top + node.r + 1f || node.y >= bottom - node.r - 1f
                !pinned && kotlin.math.abs(node.y - node.anchorY) < node.r * 0.5f
            }
            assertTrue(
                "widthFactor $widthFactor: ${crossed.size} badges have the curve through their middle",
                crossed.isEmpty(),
            )
        }
    }

    /**
     * The fan is vertical only. x is *when* an achievement was won - the reveal gates on it, and
     * the badge would be lying about its moment if the spread moved it along the timeline.
     */
    @Test
    fun `spreading never moves an icon along the timeline`() {
        // Measured, not guessed: on this fixture the worst badge drifts ~43 units on the compact
        // stage and ~28 on the wide one, with medians of 5.7 and 2.0. The bound is set just above
        // the worse of the two so that a real regression trips it - an earlier 120 was 11% of the
        // 1060-unit stage, which would have passed a build that moved every badge a tenth of the
        // timeline off the day it was earned, and passed it silently.
        listOf(1f, 4f).forEach { widthFactor ->
            val layout = LayoutEngine.layout(denseFrame(), includeNodes = true, widthFactor = widthFactor)
            val strayed = layout.nodes.filter { kotlin.math.abs(it.x - it.anchorX) > 60f }
            assertTrue(
                "icons must stay near their moment in x, ${strayed.size} did not at widthFactor=$widthFactor" +
                    " (worst ${layout.nodes.maxOf { kotlin.math.abs(it.x - it.anchorX) }})",
                strayed.isEmpty(),
            )
        }
    }

    /** And the medians stay small, so the bound above cannot be met by a handful of good cases. */
    @Test
    fun `most icons sit within a few units of their moment`() {
        val layout = LayoutEngine.layout(denseFrame(), includeNodes = true)
        val drifts = layout.nodes.map { kotlin.math.abs(it.x - it.anchorX) }.sorted()
        val median = drifts[drifts.size / 2]
        assertTrue("the median icon drifted $median units along the timeline", median <= 12f)
    }

    /** And every icon stays on the stage, however crowded the moment it belongs to. */
    @Test
    fun `every icon stays inside the stage`() {
        listOf(1f, 4f).forEach { widthFactor ->
            val layout = LayoutEngine.layout(denseFrame(), includeNodes = true, widthFactor = widthFactor)
            val top = LayoutEngine.PAD_T - 24f
            val bottom = LayoutEngine.H - LayoutEngine.PAD_B
            layout.nodes.forEach { node ->
                assertTrue(
                    "an icon escaped the stage at y=${node.y}",
                    node.y >= top - 0.5f && node.y <= bottom + 0.5f,
                )
            }
        }
    }
}
