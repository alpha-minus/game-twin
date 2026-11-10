package com.twinlore.app.journey.domain

import com.twinlore.app.journey.domain.model.AchievementType
import com.twinlore.app.journey.domain.model.AwardKind
import com.twinlore.app.journey.domain.model.GameRef
import com.twinlore.app.journey.domain.model.JourneyFrame
import com.twinlore.app.journey.domain.model.JourneyMode
import com.twinlore.app.journey.domain.model.Medal
import com.twinlore.app.journey.domain.model.PlayerJourney
import com.twinlore.app.journey.domain.model.Unlock
import kotlin.random.Random
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The one invariant that is expensive to lose and impossible to see: a widened stage draws
 * everything the compact stage draws.
 *
 * The badge art is preloaded from the WIDE layout while the export and the still preview draw the
 * COMPACT one. A compact-only badge is therefore drawn with art nobody ever requested - a picture
 * that is quietly worse, not a failure anything catches. Both times this broke it was a change to
 * how the fitting radius is chosen, and both times the shape needed to expose it was specific
 * enough that no hand-written fixture happened to have it (the second one needed five medals inside
 * a single compact division). So it is asserted over a seeded sweep rather than over examples.
 *
 * Two levels, because they fail for different reasons:
 *
 * - [the pure sweep][a widened grid never sizes up or drops what the compact grid kept] drives the
 *   grid functions directly over a few hundred random icon sets. No relaxation, no curves, no
 *   layout: milliseconds, and it isolates the arithmetic that actually decides this.
 * - [the end-to-end sweep][every widened layout draws every icon its compact layout drew] runs real
 *   journeys through [LayoutEngine.layout], which is the only thing that proves the two grids are
 *   wired up the way the pure functions assume.
 *
 * Seeded, so a failure is reproducible from the case index alone.
 */
class IconContainmentPropertyTest {

    private val innerH = LayoutEngine.H - LayoutEngine.PAD_T - LayoutEngine.PAD_B
    private val compactInnerW = LayoutEngine.W - LayoutEngine.PAD_L - LayoutEngine.PAD_R
    private val x0 = LayoutEngine.PAD_L
    private val compactX1 = LayoutEngine.W - LayoutEngine.PAD_R

    /**
     * The same x on a stage stretched to [factor] screenfuls: the pads do not scale, so every x is
     * the compact one with the plot area stretched under it. This is exactly what
     * [LayoutEngine.layout] does to its ticks and its curve points, and it is what makes a wide
     * grid a refinement of the compact one rather than a different grid.
     */
    private fun scaled(x: Float, factor: Float): Float {
        val wideInner = LayoutEngine.W * factor - LayoutEngine.PAD_L - LayoutEngine.PAD_R
        return x0 + (x - x0) * wideInner / compactInnerW
    }

    /**
     * Ticks on a coarse lattice, well clear of both plot edges and of each other.
     *
     * Deliberately not adversarial about float noise. [GridIconLayout.divisions] drops a tick within
     * an absolute 0.01 of an edge, and that epsilon does not scale with the stage - so a tick 0.005
     * inside the compact edge survives at 4x and the two grids stop being refinements of each other.
     * A real axis puts its gridlines whole days or months apart ([TickGenerator]); generating ticks
     * that sit an ulp off an edge would be testing the epsilon rather than the invariant, and the
     * epsilon is a separate (and much smaller) question.
     */
    private fun ticks(random: Random): List<Float> {
        val count = random.nextInt(0, 30)
        val margin = 8f
        val lo = x0 + margin
        val hi = compactX1 - margin
        return (0 until count)
            .map { lo + Math.round((hi - lo) * random.nextFloat() / 4f) * 4f }
            .distinct()
            .sorted()
    }

    /**
     * A random icon set, on the same coarse lattice and with the shapes that have actually broken
     * this: medals clustered into one division (five or more of them is what makes the compact and
     * refined grids disagree about how busy a division is), a spread of types, and a points
     * distribution skewed the way achievement scores really are.
     */
    private fun icons(random: Random): List<GridIconLayout.Icon> {
        val out = ArrayList<GridIconLayout.Icon>()
        var key = 0
        fun x() = x0 + Math.round((compactX1 - x0) * random.nextFloat() / 2f) * 2f

        // A cluster of medals somewhere on the axis, sometimes big enough to matter.
        val medalCluster = random.nextInt(0, 9)
        if (medalCluster > 0) {
            val at = x()
            repeat(medalCluster) {
                out += GridIconLayout.Icon(key++, at, isMedal = true, points = 0)
            }
        }
        // ... and a few scattered ones.
        repeat(random.nextInt(0, 4)) {
            out += GridIconLayout.Icon(key++, x(), isMedal = true, points = 0)
        }

        val types = AchievementType.values()
        repeat(random.nextInt(0, 240)) {
            val type = when (random.nextInt(0, 10)) {
                0 -> AchievementType.WIN_CONDITION
                1 -> AchievementType.PROGRESSION
                2 -> AchievementType.MISSABLE
                else -> types[random.nextInt(types.size)]
            }
            // Skewed: a long tail of cheap ones and the occasional 50 or 100.
            val points = when (random.nextInt(0, 10)) {
                0 -> random.nextInt(25, 101)
                1, 2 -> random.nextInt(5, 26)
                else -> random.nextInt(1, 6)
            }
            out += GridIconLayout.Icon(key++, x(), isMedal = false, points = points, type = type)
        }
        return out
    }

    /** What one stage of one case works out to: the radius it sized against and the keys it kept. */
    private class Stage(val fitRadius: Float, val keptKeys: Set<Int>, val cells: Int)

    private fun stageFor(
        icons: List<GridIconLayout.Icon>,
        tickXs: List<Float>,
        factor: Float,
    ): Stage {
        val width = LayoutEngine.W * factor
        val x1 = width - LayoutEngine.PAD_R
        val scaledTicks = tickXs.map { scaled(it, factor) }
        val scaledIcons = icons.map { it.copy(x = scaled(it.x, factor)) }
        // Exactly what LayoutEngine builds: the active grid, and the axis's own grid expressed in
        // the same (possibly stretched) coordinates.
        val boundaries = GridIconLayout.divisionsFor(scaledTicks, x0, x1, factor)
        val compactBoundaries = GridIconLayout.divisions(scaledTicks, x0, x1)
        val fitRadius = GridIconLayout.sizingRadius(
            icons = scaledIcons,
            boundaries = boundaries,
            compactBoundaries = compactBoundaries,
            innerH = innerH,
            compactInnerW = compactInnerW,
        )
        val kept = GridIconLayout.selectPerCell(scaledIcons, boundaries, fitRadius, innerH)
        return Stage(fitRadius, kept.map { it.icon.key }.toSet(), boundaries.size - 1)
    }

    @Test fun `a widened grid never sizes up or drops what the compact grid kept`() {
        val random = Random(42)
        var widerStages = 0
        var biggestSet = 0

        repeat(300) { case ->
            val tickXs = ticks(random)
            val iconSet = icons(random)
            val compact = stageFor(iconSet, tickXs, 1f)
            biggestSet = maxOf(biggestSet, iconSet.size)

            LayoutEngine.WIDTH_FACTORS.drop(1).forEach { factor ->
                val wide = stageFor(iconSet, tickXs, factor)
                if (wide.cells > compact.cells) widerStages++

                assertTrue(
                    "case $case at ${factor}x sized its icons up: ${wide.fitRadius} > ${compact.fitRadius}",
                    wide.fitRadius <= compact.fitRadius + 0.001f,
                )
                val missing = compact.keptKeys - wide.keptKeys
                assertTrue(
                    "case $case at ${factor}x dropped ${missing.size} icons the compact grid kept " +
                        "(radius ${wide.fitRadius} vs ${compact.fitRadius}, " +
                        "cells ${wide.cells} vs ${compact.cells}): $missing",
                    missing.isEmpty(),
                )
            }
        }

        // The sweep has to actually reach the interesting shapes, or it is 300 assertions about
        // empty charts: some cases must genuinely refine the grid, and some must be busy.
        assertTrue("the sweep never refined a grid", widerStages > 100)
        assertTrue("the sweep never built a busy chart", biggestSet > 150)
    }

    // ---- end to end ----

    private val day = 86_400_000L
    private val t0 = 1_760_000_000_000L

    /** A whole journey, of the shapes the chart is actually asked to draw. */
    private fun journey(random: Random): JourneyFrame {
        val count = random.nextInt(5, 160)
        val spanDays = random.nextInt(1, 400)
        val types = AchievementType.values()
        val gameCount = random.nextInt(1, 6)
        val unlocks = (0 until count).map { i ->
            Unlock(
                id = i,
                title = "A$i",
                description = "",
                points = when (random.nextInt(0, 10)) {
                    0 -> random.nextInt(25, 101)
                    1, 2 -> random.nextInt(5, 26)
                    else -> random.nextInt(1, 6)
                },
                badgeName = "b$i",
                type = types[random.nextInt(types.size)],
                earnedAtMillis = t0 + (spanDays * day * random.nextFloat()).toLong(),
                numAwarded = 10,
                gameId = random.nextInt(1, gameCount + 1),
            )
        }.sortedBy { it.earnedAtMillis }

        // Awards, sometimes clustered into one moment - the shape that made the two grids disagree.
        val clustered = random.nextBoolean()
        val medalAt = t0 + (spanDays * day * random.nextFloat()).toLong()
        val medals = (0 until random.nextInt(0, 8)).map { m ->
            Medal(
                kind = AwardKind.values()[random.nextInt(AwardKind.values().size)],
                whenMillis = if (clustered) medalAt + m * 60_000L else t0 + (spanDays * day * random.nextFloat()).toLong(),
                gameId = random.nextInt(1, gameCount + 1),
                ownerName = null,
                title = "G$m",
            )
        }

        val main = PlayerJourney(
            name = "me",
            unlocks = unlocks,
            games = (1..gameCount).associateWith {
                GameRef(it, "G$it", null, emptyList(), colorIndex = it, players = 100, award = null)
            },
            medals = medals,
        )
        // A rival on a GAME frame is what puts the chart into race mode, whose axis is a different
        // function of time entirely; both modes have to hold the invariant.
        val mode = if (random.nextInt(0, 3) == 0) JourneyMode.GAME else JourneyMode.SPAN
        val rivals = if (mode == JourneyMode.GAME && random.nextBoolean()) {
            listOf(main.copy(name = "rival", medals = emptyList()))
        } else {
            emptyList()
        }
        return JourneyFrame(mode, main, rivals, 0, t0 + spanDays * day)
    }

    /**
     * What identifies a badge across two layouts of the same journey.
     *
     * Not the achievement id on its own. A compare chart draws badges for the rival as well, and a
     * rival's unlock list is a different player's copy of the same game - the ids collide by
     * construction, and the generator below hands the rival the main player's own unlocks. Keyed
     * on the id alone, a wide layout that had dropped one of the main player's badges would still
     * appear to contain the compact set as long as the rival happened to keep the same id, which
     * is exactly the case this property exists to catch.
     */
    private fun badgeKey(node: NodeLayout): Pair<String?, Int> = node.ownerName to node.unlock.id

    @Test fun `every widened layout draws every icon its compact layout drew`() {
        val random = Random(42)
        var raceCharts = 0
        var stretched = 0
        var compareCharts = 0

        repeat(30) { case ->
            val frame = journey(random)
            val compact = LayoutEngine.layout(frame, includeNodes = true)
            val compactIds = compact.nodes.map(::badgeKey).toSet()
            if (compact.raceMode) raceCharts++
            if (compact.nodes.any { it.ownerName != null }) compareCharts++

            LayoutEngine.WIDTH_FACTORS.drop(1).forEach { factor ->
                val wide = LayoutEngine.layout(frame, includeNodes = true, widthFactor = factor)
                if (wide.nodes.size > compact.nodes.size) stretched++

                assertTrue(
                    "case $case at ${factor}x sized its icons up: ${wide.fitRadius} > ${compact.fitRadius}",
                    wide.fitRadius <= compact.fitRadius + 0.001f,
                )
                val missing = compactIds - wide.nodes.map(::badgeKey).toSet()
                assertTrue(
                    "case $case at ${factor}x has no art for ${missing.size} badges the compact chart draws: $missing",
                    missing.isEmpty(),
                )
                // Awards are never rationed, on either stage.
                assertTrue(
                    "case $case at ${factor}x lost an award",
                    wide.medals.size == compact.medals.size,
                )
            }
        }

        assertTrue("the sweep never built a race chart", raceCharts > 0)
        assertTrue("the sweep never gained capacity from the extra width", stretched > 10)
        // Compare charts are laid out here at every width factor, even though the player only ever
        // draws them at one screenful: the invariant is about the grid arithmetic, and a chart
        // carrying two owners' badges is exactly the shape that could break the containment.
        assertTrue("the sweep never drew a rival's badges", compareCharts > 0)
    }
}
