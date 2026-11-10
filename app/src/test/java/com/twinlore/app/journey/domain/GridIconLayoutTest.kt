package com.twinlore.app.journey.domain

import com.twinlore.app.journey.domain.model.AchievementType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GridIconLayoutTest {

    private var nextKey = 0
    private fun badge(
        x: Float,
        points: Int,
        type: AchievementType = AchievementType.STANDARD,
        owner: String? = null,
    ) = GridIconLayout.Icon(nextKey++, x, isMedal = false, points = points, type = type, owner = owner)

    private fun medal(x: Float) = GridIconLayout.Icon(nextKey++, x, isMedal = true, points = 0)

    /** The real plot height: 600 minus the 132/52 pads. Every capacity below is computed on it. */
    private val innerH = 416f

    // --- divisions ---

    @Test fun `divisions are the chart edges plus the interior ticks`() {
        val b = GridIconLayout.divisions(listOf(100f, 200f, 300f, 400f), x0 = 64f, x1 = 984f)
        assertEquals(listOf(64f, 100f, 200f, 300f, 400f, 984f), b)
    }

    @Test fun `ticks on or outside an edge do not make empty divisions`() {
        val b = GridIconLayout.divisions(listOf(0f, 64f, 200f, 400f, 600f, 984f, 1200f), x0 = 64f, x1 = 984f)
        assertEquals(listOf(64f, 200f, 400f, 600f, 984f), b)
    }

    @Test fun `coincident ticks collapse into one boundary`() {
        val b = GridIconLayout.divisions(listOf(300f, 300.001f, 300f, 600f, 800f), x0 = 64f, x1 = 984f)
        assertEquals(listOf(64f, 300f, 600f, 800f, 984f), b)
    }

    @Test fun `too few ticks falls back to eleven equal divisions`() {
        val b = GridIconLayout.divisions(listOf(500f), x0 = 64f, x1 = 984f)
        assertEquals(GridIconLayout.FALLBACK_DIVISIONS + 1, b.size)
        assertEquals(64f, b.first(), 0.01f)
        assertEquals(984f, b.last(), 0.01f)
        // Equal widths, so every step is the plot's width over eleven.
        val step = (984f - 64f) / 11f
        for (i in 0 until GridIconLayout.FALLBACK_DIVISIONS) {
            assertEquals(step, b[i + 1] - b[i], 0.01f)
        }
    }

    @Test fun `no ticks at all still falls back rather than making one huge cell`() {
        val b = GridIconLayout.divisions(emptyList(), x0 = 64f, x1 = 984f)
        assertEquals(GridIconLayout.FALLBACK_DIVISIONS + 1, b.size)
    }

    @Test fun `a zero-width plot is one division`() {
        assertEquals(listOf(64f, 64f), GridIconLayout.divisions(listOf(64f), x0 = 64f, x1 = 64f))
    }

    // --- divisions on a widened stage ---

    @Test fun `an unstretched stage keeps the grid the axis draws`() {
        val ticks = listOf(200f, 400f, 600f, 800f)
        assertEquals(
            GridIconLayout.divisions(ticks, 64f, 984f),
            GridIconLayout.divisionsFor(ticks, 64f, 984f, widthFactor = 1f),
        )
    }

    /** Ten interior gridlines, so the compact grid is exactly eleven divisions wide. */
    private val elevenTicks = (1..10).map { 64f + (3244f - 64f) * it / 11f }

    /**
     * The gridlines are a function of the time span alone, so a stretched stage handed the same
     * eleven buckets would carry the same icons and the extra width would buy nothing. Past one
     * screenful each division is split, to about eleven per screenful.
     *
     * A division can only be split a whole number of times, so the count lands on the target when
     * it divides and above it otherwise - never below, since falling short is the one failure that
     * would leave a wide chart carrying less than the compact one it was widened from.
     */
    @Test fun `a widened stage is cut into about eleven divisions per screenful`() {
        fun countFor(factor: Float) = GridIconLayout.divisionsFor(elevenTicks, 64f, 3244f, factor).size - 1
        assertEquals(11, countFor(1f))
        assertEquals(22, countFor(2f))       // 22 / 11 = 2 splits
        assertEquals(33, countFor(3f))
        assertEquals(44, countFor(4f))
        // 1.5 screenfuls asks for 17 across 11 divisions: two splits each, so 22 rather than 16.
        assertEquals(22, countFor(1.5f))
        assertTrue("a widened grid may never be coarser", countFor(1.5f) >= countFor(1f))
    }

    /**
     * The widened grid is a refinement of the compact one: every compact boundary survives, so
     * every wide division sits inside exactly one compact division. That nesting is what makes the
     * compact chart's icons a subset of the wide chart's.
     */
    @Test fun `a widened grid refines the compact one rather than replacing it`() {
        val compact = GridIconLayout.divisionsFor(elevenTicks, 64f, 3244f, widthFactor = 1f)
        val wide = GridIconLayout.divisionsFor(elevenTicks, 64f, 3244f, widthFactor = 3f)

        compact.forEach { boundary ->
            assertTrue(
                "compact boundary $boundary is not a boundary of the widened grid",
                wide.any { kotlin.math.abs(it - boundary) < 0.01f },
            )
        }
        assertEquals(64f, wide.first(), 0.01f)
        assertEquals(3244f, wide.last(), 0.01f)
        // Each compact division is split into equal parts.
        val step = (compact[1] - compact[0]) / 3f
        for (i in 0 until wide.size - 1) assertEquals(step, wide[i + 1] - wide[i], 0.01f)
    }

    /** An axis whose own grid is already finer than the target is left alone, never coarsened. */
    @Test fun `a widened stage never coarsens an already-fine axis`() {
        val many = (1..29).map { 64f + (3244f - 64f) * it / 30f }
        val compact = GridIconLayout.divisionsFor(many, 64f, 3244f, widthFactor = 1f)
        val wide = GridIconLayout.divisionsFor(many, 64f, 3244f, widthFactor = 1.5f)
        assertEquals(30, compact.size - 1)
        assertEquals(compact, wide)
    }

    @Test fun `a widened but zero-width plot is still one division`() {
        assertEquals(listOf(64f, 64f), GridIconLayout.divisionsFor(listOf(64f), 64f, 64f, widthFactor = 4f))
    }

    // --- cell assignment ---

    @Test fun `an icon exactly on a division edge goes to the right-hand cell`() {
        val b = listOf(0f, 100f, 200f, 300f)
        assertEquals(0, GridIconLayout.cellOf(99.9f, b))
        assertEquals(1, GridIconLayout.cellOf(100f, b))       // exactly on the edge: right cell
        assertEquals(1, GridIconLayout.cellOf(199.9f, b))
        assertEquals(2, GridIconLayout.cellOf(200f, b))
    }

    @Test fun `icons past either end belong to the nearest end cell`() {
        val b = listOf(0f, 100f, 200f, 300f)
        assertEquals(0, GridIconLayout.cellOf(-50f, b))
        assertEquals(2, GridIconLayout.cellOf(300f, b))       // the last cell owns its right edge
        assertEquals(2, GridIconLayout.cellOf(1000f, b))
    }

    // --- the size hierarchy ---

    /**
     * The top quartile by nearest rank, and a standard badge has to *beat* it rather than match it.
     *
     * The at-least reading falls apart on the shapes achievement scores actually come in: a chart
     * of 1, 1, 1 and 100 has a nearest-rank 75th percentile of 1, so at-least would promote every
     * badge on it while beat-it promotes the one badge anybody would point at.
     */
    @Test fun `the high-point threshold is the top quartile, and must be beaten`() {
        assertEquals(3, GridIconLayout.highPointThreshold(listOf(1, 2, 3, 4)))
        assertEquals(1, GridIconLayout.highPointThreshold(listOf(1, 1, 1, 100)))
        assertEquals(60, GridIconLayout.highPointThreshold((1..8).map { it * 10 }))
        // Order of the input never matters.
        assertEquals(60, GridIconLayout.highPointThreshold(listOf(80, 10, 50, 30, 70, 20, 60, 40)))
        // A chart where every badge scored the same has no top quartile to promote.
        assertEquals(5, GridIconLayout.highPointThreshold(listOf(5, 5, 5, 5)))
        assertEquals(7, GridIconLayout.highPointThreshold(listOf(7)))
        assertEquals(Int.MAX_VALUE, GridIconLayout.highPointThreshold(emptyList()))
    }

    /**
     * The distribution the quartile is taken over is the standard badges' own. The tiers above them
     * are decided by what they are, and their scores are systematically higher - counting them
     * lifts the bar the tail has to clear, so fewer than a quarter of the standard badges get
     * promoted, which is not what "top quartile of them" means.
     */
    @Test fun `the quartile is taken over the standard badges alone`() {
        val standards = (1..4).map { badge(50f, it) }                        // 1, 2, 3, 4
        val loud = (1..4).map { badge(50f, 100, AchievementType.PROGRESSION) }
        val kept = selected(standards + loud, fitRadius = 44f)

        // Counting the progressions would put the threshold at 100 and promote nothing; over the
        // standard badges alone it is 3, and the 4-pointer clears it.
        val four = kept.single { it.icon.points == 4 }
        assertEquals(GridIconLayout.Tier.HIGH, four.tier)
        assertEquals(GridIconLayout.Tier.STANDARD, kept.single { it.icon.points == 3 }.tier)
    }

    @Test fun `tiers come from what an achievement is, then from what it scored`() {
        val threshold = GridIconLayout.highPointThreshold(listOf(1, 2, 3, 4))   // 3
        fun tier(icon: GridIconLayout.Icon) = GridIconLayout.tierOf(icon, threshold)

        assertEquals(GridIconLayout.Tier.MEDAL, tier(medal(50f)))
        // A win condition is a win condition whatever it paid.
        assertEquals(GridIconLayout.Tier.WIN, tier(badge(50f, 1, AchievementType.WIN_CONDITION)))
        assertEquals(GridIconLayout.Tier.HIGH, tier(badge(50f, 1, AchievementType.PROGRESSION)))
        assertEquals(GridIconLayout.Tier.HIGH, tier(badge(50f, 1, AchievementType.MISSABLE)))
        // A standard badge is promoted only by beating the top quartile.
        assertEquals(GridIconLayout.Tier.HIGH, tier(badge(50f, 4)))
        assertEquals(GridIconLayout.Tier.STANDARD, tier(badge(50f, 3)))
        assertEquals(GridIconLayout.Tier.STANDARD, tier(badge(50f, 1)))
    }

    @Test fun `each tier is its own fraction of the fitting radius`() {
        assertEquals(40f, GridIconLayout.radiusFor(GridIconLayout.Tier.MEDAL, 40f), 0.001f)
        assertEquals(34f, GridIconLayout.radiusFor(GridIconLayout.Tier.WIN, 40f), 0.001f)
        assertEquals(28f, GridIconLayout.radiusFor(GridIconLayout.Tier.HIGH, 40f), 0.001f)
        assertEquals(20f, GridIconLayout.radiusFor(GridIconLayout.Tier.STANDARD, 40f), 0.001f)
        // Ordered largest first, which is also the priority order a crowded division fills in.
        assertEquals(
            GridIconLayout.Tier.values().toList(),
            GridIconLayout.Tier.values().sortedByDescending { it.scale },
        )
    }

    /** On a chart already at the floor there is nothing left to scale down: every tier draws it. */
    @Test fun `the size floor survives the tier scales`() {
        GridIconLayout.Tier.values().forEach {
            assertEquals(GridIconLayout.R_MIN, GridIconLayout.radiusFor(it, GridIconLayout.R_MIN), 0.001f)
        }
    }

    // --- the sizing count ---

    /**
     * Badges never push the sizing count past the nominal division, however many of them land in
     * one moment: they are rationed by the height budget instead, so a burst nothing will draw must
     * not shrink every icon on the chart.
     */
    @Test fun `a badge burst sizes against a nominal division, not against itself`() {
        val b = listOf(0f, 100f, 200f)
        val icons = (1..25).map { badge(50f, it) }
        assertEquals(GridIconLayout.SIZING_CELL_ICONS, GridIconLayout.sizingCellCount(icons, b))
        assertEquals(25, GridIconLayout.maxCellCount(icons, b))
    }

    /** Medals do: they are all one size and never yield, so the chart has to shrink until they fit. */
    @Test fun `a division of medals sizes the whole chart down until they fit`() {
        val b = listOf(0f, 100f, 200f)
        val icons = (1..13).map { medal(50f) } + (1..5).map { badge(50f, it) }
        assertEquals(13, GridIconLayout.sizingCellCount(icons, b))

        // ... and at that radius the whole column does fit the plot, which is the point of it.
        val r = GridIconLayout.uniformRadius(innerH, 13)
        assertTrue("13 medals at $r must fit the plot", drawnExtent(13, r) <= innerH)
    }

    @Test fun `a division under the nominal count sizes by what is in it`() {
        val b = listOf(0f, 100f, 200f)
        assertEquals(2, GridIconLayout.sizingCellCount(listOf(badge(50f, 1), badge(50f, 2)), b))
        assertEquals(1, GridIconLayout.sizingCellCount(emptyList(), b))
    }

    // --- the per-cell height budget ---

    private val oneCell = listOf(0f, 100f, 200f)

    private fun selected(
        icons: List<GridIconLayout.Icon>,
        fitRadius: Float = GridIconLayout.R_MAX,
        boundaries: List<Float> = oneCell,
        dropOverflow: Boolean = true,
    ) = GridIconLayout.selectPerCell(
        icons = icons,
        boundaries = boundaries,
        fitRadius = fitRadius,
        innerH = innerH,
        dropOverflow = dropOverflow,
    )

    /**
     * Hand-computed at the 44-unit ceiling: a standard badge is half of it, so 22 units of radius,
     * 44 of diameter and 51 of pitch once the 7-unit stack gap is counted. Eight of them cover
     * 8*44 + 7*7 = 401 of the plot's 416; a ninth would want 452 and is dropped.
     *
     * Twice what the old flat count cap drew, and the whole point of the hierarchy: small icons
     * should buy a busy moment more of its own history, not the same four badges further apart.
     */
    @Test fun `a division of standard badges fits eight, not four`() {
        val icons = (1..20).map { badge(50f, 5) }
        val kept = selected(icons)

        assertEquals(8, kept.size)
        assertTrue("every standard badge is half the fitting radius", kept.all { it.radius == 22f })
        assertEquals(401f, drawnExtent(kept.size, 22f), 0.01f)
        assertTrue("the drawn stack must fit the plot", drawnExtent(kept.size, 22f) <= innerH)
        assertTrue("one more must not fit", drawnExtent(kept.size + 1, 22f) > innerH)
        // Equal scores, so the tie-break is the key: the first eight the caller handed over.
        assertEquals(icons.take(8).map { it.key }, kept.map { it.icon.key })
        // Input order is preserved, so the caller's own ordering survives the thinning.
        assertEquals(kept.map { it.icon.key }.sorted(), kept.map { it.icon.key })
    }

    /**
     * Medals are never dropped, and they spend the budget: nine full-size awards run well past the
     * plot (4*88 + 3*7 = 373 is four of them, and a fifth already wants 468), and every one of them
     * is still drawn while the badges beside them get nothing.
     *
     * The overflow is not how a real chart gets here - [sizingCellCount] shrinks the whole stage
     * until a column of medals fits, and only the size floor stops it - but the rule has to hold
     * whatever radius it is handed.
     */
    @Test fun `medals are never dropped, and they spend the division's budget`() {
        val kept = selected((1..9).map { medal(50f) } + (1..5).map { badge(50f, it) })
        assertEquals(9, kept.size)
        assertTrue(kept.all { it.tier == GridIconLayout.Tier.MEDAL && it.radius == 44f })
    }

    /**
     * Mixed sizes fit more than the same number of full-size ones would: one medal (88) and one win
     * condition (74.8) leave 246.2 of the 416, which is five 44-unit standards and their gaps
     * (5*44 + 6*7 = 262 from the start of the third icon... measured as a whole column: 88 + 7 +
     * 74.8 + 5*(7 + 44) = 424.8 for five, so four fit at 373.8).
     */
    @Test fun `a mixed division fills up by height rather than by count`() {
        val icons = listOf(medal(50f), badge(50f, 1, AchievementType.WIN_CONDITION)) +
            (1..10).map { badge(50f, 5) }
        val kept = selected(icons)

        assertEquals(6, kept.size)
        assertEquals(1, kept.count { it.tier == GridIconLayout.Tier.MEDAL })
        assertEquals(1, kept.count { it.tier == GridIconLayout.Tier.WIN })
        assertEquals(4, kept.count { it.tier == GridIconLayout.Tier.STANDARD })
        val stacked = kept.sumOf { (it.radius * 2f).toDouble() }.toFloat() +
            GridIconLayout.STACK_GAP * (kept.size - 1)
        assertTrue("the mixed column covers $stacked of $innerH", stacked <= innerH)
    }

    /** The selection guarantee: what a crowded moment drops is the cheap end of it. */
    @Test fun `a one-point badge is dropped before a hundred-point one`() {
        val icons = (1..20).map { badge(50f, it * 5) }
        val kept = selected(icons)

        // Every kept badge outscores every dropped one.
        val keptPoints = kept.map { it.icon.points }
        val droppedPoints = icons.map { it.points } - keptPoints.toSet()
        assertTrue("kept $keptPoints vs dropped $droppedPoints", keptPoints.min() > droppedPoints.max())
        assertTrue("the top scorer must always survive", 100 in keptPoints)
        assertTrue("the cheapest must not", 5 !in keptPoints)
    }

    /** Tier beats points: an award or a win condition outranks any badge, whatever either scored. */
    @Test fun `tiers outrank points when a division has room for one icon`() {
        val icons = listOf(
            badge(50f, 100, AchievementType.PROGRESSION),
            badge(50f, 1, AchievementType.WIN_CONDITION),
            badge(50f, 90),
            medal(50f),
        )
        // A plot with room for exactly one icon: only the highest priority survives.
        val kept = GridIconLayout.selectPerCell(icons, oneCell, fitRadius = 44f, innerH = 100f)
        assertEquals(1, kept.size)
        assertEquals(GridIconLayout.Tier.MEDAL, kept.single().tier)

        val noMedal = GridIconLayout.selectPerCell(icons.dropLast(1), oneCell, fitRadius = 44f, innerH = 100f)
        assertEquals(GridIconLayout.Tier.WIN, noMedal.single().tier)
        assertEquals(1, noMedal.single().icon.points)
    }

    // --- sharing a crowded division between players ---

    /**
     * A compare chart puts one badge a day on the stage for each player, and the height budget used
     * to be able to take that promise back: a rival having a loud day filled the division with
     * their own badges and the main player was evicted from it entirely, silently.
     *
     * The division is filled a round at a time instead - everybody's best, then everybody's second
     * - so being outscored costs a player their second badge in a division, never their first.
     */
    @Test fun `a division shares itself out before any player gets a second badge`() {
        val loudRival = (1..8).map { badge(50f, 100, owner = "aria") }
        val quietMain = listOf(badge(50f, 1))
        // Room for two standard badges and no more: 2*44 + 7 = 95.
        val kept = GridIconLayout.selectPerCell(loudRival + quietMain, oneCell, fitRadius = 44f, innerH = 95f)

        assertEquals(2, kept.size)
        assertEquals(
            "both owners must be in the division",
            setOf(null, "aria"),
            kept.map { it.icon.owner }.toSet(),
        )
        assertTrue(
            "the main player's only badge must survive being outscored eight to one",
            kept.any { it.icon.owner == null },
        )
    }

    /** And the rounds keep going: three players get one each before anybody gets a second. */
    @Test fun `every owner present keeps a badge before any owner gets two`() {
        val icons = listOf(
            badge(50f, 100, owner = "aria"), badge(50f, 90, owner = "aria"),
            badge(50f, 80, owner = "brix"), badge(50f, 70, owner = "brix"),
            badge(50f, 2), badge(50f, 1),
        )
        // At the size floor every tier draws the same 8-unit radius, so the capacity is exact
        // however the points happen to promote: four icons span 4*16 + 3*7 = 85.
        val kept = GridIconLayout.selectPerCell(
            icons, oneCell, fitRadius = GridIconLayout.R_MIN, innerH = 85f,
        )

        assertEquals(4, kept.size)
        val owners = kept.map { it.icon.owner }
        assertEquals(setOf(null, "aria", "brix"), owners.toSet())
        // The fourth slot goes to the best second badge, which is the highest scorer's.
        assertEquals(2, owners.count { it == "aria" })
    }

    /**
     * A solo chart is one owner, so every badge is in its own round and the ordering is exactly
     * the priority order it always was: tier, then points, then the caller's key.
     */
    @Test fun `one owner is unaffected by the rounds`() {
        val icons = listOf(
            badge(50f, 5), badge(50f, 100), badge(50f, 1, AchievementType.WIN_CONDITION),
            badge(50f, 40), badge(50f, 3),
        )
        // Three icons at the size floor: 3*16 + 2*7 = 62.
        val kept = GridIconLayout.selectPerCell(
            icons, oneCell, fitRadius = GridIconLayout.R_MIN, innerH = 62f,
        )

        assertEquals(3, kept.size)
        assertEquals(GridIconLayout.Tier.WIN, kept.first { it.icon.points == 1 }.tier)
        assertEquals(
            "the survivors are the win condition and the two highest scorers",
            listOf(1, 100, 40),
            kept.sortedWith(compareBy({ it.tier.ordinal }, { -it.icon.points })).map { it.icon.points },
        )
    }

    /** Never nothing: a plot too short for even one icon is a broken stage, not a crowded moment. */
    @Test fun `every division draws at least one icon`() {
        val kept = GridIconLayout.selectPerCell((1..5).map { badge(50f, it) }, oneCell, 44f, innerH = 1f)
        assertEquals(1, kept.size)
        assertEquals(5, kept.single().icon.points)
    }

    @Test fun `the sanity bound caps a division of floor-sized icons`() {
        val kept = selected((1..40).map { badge(50f, 5) }, fitRadius = GridIconLayout.R_MIN)
        assertEquals(GridIconLayout.MAX_PER_CELL, kept.size)
    }

    @Test fun `each division fills up on its own`() {
        val b = listOf(0f, 100f, 200f, 300f)
        val left = (1..14).map { badge(10f, 5) }
        val middle = (1..3).map { badge(150f, 5) }
        val right = (1..12).map { badge(250f, 5) }
        val kept = selected(left + middle + right, boundaries = b)

        assertEquals(8, kept.count { GridIconLayout.cellOf(it.icon.x, b) == 0 })
        assertEquals(3, kept.count { GridIconLayout.cellOf(it.icon.x, b) == 1 })
        assertEquals(8, kept.count { GridIconLayout.cellOf(it.icon.x, b) == 2 })
    }

    @Test fun `a division inside its budget keeps everything it was given`() {
        val icons = (1..4).map { badge(50f, 5) } + (1..2).map { badge(150f, 5) }
        assertEquals(icons, selected(icons).map { it.icon })
    }

    /**
     * Single-game mode: the chart is one game's achievement list, so nothing is dropped at all. The
     * sizes are still the tiered ones - the column simply overflows, and the relaxation pass fans it
     * out sideways.
     */
    @Test fun `dropping can be turned off entirely, and only the dropping goes`() {
        val icons = (1..30).map { badge(50f, 5) } + medal(50f)
        val kept = selected(icons, dropOverflow = false)

        assertEquals(icons, kept.map { it.icon })
        assertEquals(22f, kept.first().radius, 0.001f)
        assertEquals(44f, kept.last().radius, 0.001f)
        assertTrue(
            "this fixture must be one the budget would otherwise have thinned",
            selected(icons).size < icons.size,
        )
    }

    @Test fun `the busiest division is what sizing keys off`() {
        val b = listOf(0f, 100f, 200f, 300f)
        val icons = (1..4).map { badge(10f, it) } + (1..7).map { badge(150f, it) } + badge(250f, 1)
        assertEquals(7, GridIconLayout.maxCellCount(icons, b))
        assertEquals(1, GridIconLayout.maxCellCount(emptyList(), b))
    }

    // --- the uniform radius ---

    /** What a stack of [n] icons of radius [r] actually covers: n discs and the n-1 gaps between. */
    private fun drawnExtent(n: Int, r: Float) = n * 2 * r + (n - 1) * GridIconLayout.STACK_GAP

    /**
     * Hand-computed on the real plot height (600 - 132 - 52 = 416) and the real 7-unit stack gap:
     * a full division of ten icons gets 416 / 20 - 3.5 = 17.3.
     *
     * The formula allots each icon a *pitch* of one diameter plus one gap, so ten pitches come to
     * exactly 416. What ten icons then cover is one gap less than that - 409 - because the last
     * icon in a stack has nothing below it to be separated from. The rule is "fill the axis and do
     * not exceed it", so that spare gap is slack in the right direction, and it is the drawn extent
     * rather than the pitch that has to fit.
     */
    @Test fun `a full division sizes icons to fill the y axis without exceeding it`() {
        val r = GridIconLayout.uniformRadius(innerH = 416f, maxCellCount = 10)
        assertEquals(17.3f, r, 0.001f)
        assertEquals(409f, drawnExtent(10, r), 0.01f)
        assertTrue("a full stack must fit the plot", drawnExtent(10, r) <= 416f)
        // And it genuinely fills it: one more icon at this size would not fit.
        assertTrue("the size must be the biggest that fits", drawnExtent(11, r) > 416f)
    }

    @Test fun `a sparse chart stops at the ceiling instead of becoming billboards`() {
        assertEquals(GridIconLayout.R_MAX, GridIconLayout.uniformRadius(416f, 3), 0.001f)
        assertEquals(GridIconLayout.R_MAX, GridIconLayout.uniformRadius(416f, 1), 0.001f)
        // The nominal division - four - is the last count that still wants more than the ceiling
        // allows: 416/8 - 3.5 = 48.5. So every chart the grid alone sizes draws at the ceiling, and
        // only a division of more medals than that ever sizes down.
        assertEquals(GridIconLayout.R_MAX, GridIconLayout.uniformRadius(416f, GridIconLayout.SIZING_CELL_ICONS), 0.001f)
        assertEquals(38.1f, GridIconLayout.uniformRadius(416f, 5), 0.01f)
        assertEquals(26.21f, GridIconLayout.uniformRadius(416f, 7), 0.01f)
    }

    @Test fun `a crammed division stops at the floor rather than vanishing`() {
        assertEquals(GridIconLayout.R_MIN, GridIconLayout.uniformRadius(416f, 40), 0.001f)
        assertEquals(GridIconLayout.R_MIN, GridIconLayout.uniformRadius(416f, 1000), 0.001f)
    }

    @Test fun `a division count of zero is treated as one`() {
        assertEquals(GridIconLayout.uniformRadius(416f, 1), GridIconLayout.uniformRadius(416f, 0), 0.001f)
    }

    // --- the division-width cap ---

    /**
     * The y-fit is only half the question. It asks "can a division's icons stack up the plot", and
     * at the ceiling radius the answer is yes while the icons are still wider than the division is
     * - 88 units of disc in an 84-unit cell on an unstretched stage - so neighbouring divisions'
     * icons start out inside each other and the relaxation spends its budget pulling them apart.
     */
    @Test fun `the fitting radius is capped by the division an icon has to sit in`() {
        // Eleven divisions of the real plot: 920 / 11 = 83.6 wide.
        val typical = 920f / 11f
        // The y axis alone would allow the 44-unit ceiling for four per division.
        assertEquals(GridIconLayout.R_MAX, GridIconLayout.uniformRadius(416f, 4), 0.001f)
        // The division is what actually decides it: (83.6 - 7) / 2.
        assertEquals(38.3f, GridIconLayout.fitRadius(416f, 4, typical), 0.05f)
    }

    /** Whichever of the two is tighter wins; a wide division leaves the y-fit in charge. */
    @Test fun `the tighter of the two axes decides the radius`() {
        assertEquals(
            GridIconLayout.uniformRadius(416f, 8),
            GridIconLayout.fitRadius(416f, 8, 400f),
            0.001f,
        )
        assertTrue(
            "a narrow division must beat a roomy y axis",
            GridIconLayout.fitRadius(416f, 2, 40f) < GridIconLayout.uniformRadius(416f, 2),
        )
    }

    /**
     * A sparse chart's divisions are exactly as narrow as a dense one's - the grid follows the time
     * span, not how much is drawn - so the cap has to stand down where there is no crowd, or three
     * badges on an empty stage would be shrunk for a collision that cannot happen.
     */
    @Test fun `one icon to a division is never capped by the division's width`() {
        assertEquals(GridIconLayout.R_MAX, GridIconLayout.fitRadius(416f, 1, 20f), 0.001f)
        assertEquals(GridIconLayout.R_MAX, GridIconLayout.fitRadius(416f, 1, 0f), 0.001f)
    }

    @Test fun `the size floor survives the division cap`() {
        assertEquals(GridIconLayout.R_MIN, GridIconLayout.fitRadius(416f, 4, 1f), 0.001f)
    }

    /**
     * The typical division, not the narrowest. The grid is cut by the axis's own gridlines, so the
     * end divisions are whatever is left between a plot edge and the nearest tick - regularly a
     * sliver, and regularly occupied. Sizing the whole chart by that sliver put an ordinary
     * 45-badge journey at the size floor when it was measured.
     */
    @Test fun `the typical division is the plot's width over its division count`() {
        // Four divisions across 300 units, one of them a 3-unit sliver: the answer is 75, the
        // pitch icons are actually placed at, not the sliver.
        assertEquals(75f, GridIconLayout.typicalDivision(listOf(0f, 3f, 100f, 200f, 300f)), 0.001f)
        assertEquals(0f, GridIconLayout.typicalDivision(listOf(50f)), 0.001f)
        assertEquals(0f, GridIconLayout.typicalDivision(emptyList()), 0.001f)
    }

    /**
     * "Don't exceed" at every count the grid can ask for. Past the point where the floor takes over
     * the stack does run off the plot, which is the floor's whole purpose: icons too small to read
     * would be worse than icons that need the headroom, and only a division stuffed with more
     * medals than the nominal division allows can get there.
     */
    @Test fun `the busiest division's stack never exceeds the plot height`() {
        for (n in 1..GridIconLayout.SIZING_CELL_ICONS) {
            val r = GridIconLayout.uniformRadius(416f, n)
            val extent = drawnExtent(n, r)
            assertTrue("a stack of $n at radius $r covers $extent, past the 416-unit plot", extent <= 416f)
        }
        // Only the floor lets a stack overflow, and only past the per-division cap.
        assertEquals(GridIconLayout.R_MIN, GridIconLayout.uniformRadius(416f, 30), 0.001f)
        assertTrue(drawnExtent(30, GridIconLayout.uniformRadius(416f, 30)) > 416f)
    }
}
