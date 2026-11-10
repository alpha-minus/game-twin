package com.twinlore.app.journey.domain

import com.twinlore.app.journey.domain.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class LayoutEngineTest {
    private val D = 86_400_000L
    private val t0 = 1_760_000_000_000L

    /**
     * The most overlapping pairs a converged relaxation may leave on the dense fixture below.
     *
     * Calibrated against that fixture rather than guessed: it starts with 123 raw overlapping pairs,
     * the relaxation as it stands leaves 3, and the same relaxation cut to five iterations - a
     * deliberately broken one, standing in for whatever future change stops it converging - leaves
     * 26. The bound is the midpoint of those two, so the guard has real discriminating power in
     * both directions: nearly five times the current residue before it complains, and well under
     * what a relaxation that has stopped working leaves behind.
     *
     * Absolute rather than a fraction of the raw count: the raw count swings with the fixture while
     * the residue does not, so a ratio would pass or fail a genuine regression depending on which
     * fixture it happened to be given.
     */
    private val MAX_RESIDUAL_OVERLAPS = 14

    private fun unlock(id: Int, at: Long, pts: Int, gameId: Int = 1) = Unlock(
        id = id, title = "A$id", description = "", points = pts, badgeName = "b$id",
        type = AchievementType.STANDARD, earnedAtMillis = at, numAwarded = 10, gameId = gameId,
    )
    private fun game(id: Int) = GameRef(id, "G$id", null, emptyList(), colorIndex = id, players = 100, award = null)
    private fun journey(unlocks: List<Unlock>) = PlayerJourney(
        name = "me", unlocks = unlocks.sortedBy { it.earnedAtMillis },
        games = unlocks.map { it.gameId }.distinct().associateWith { game(it) }, medals = emptyList(),
    )
    private fun frame(unlocks: List<Unlock>, rivals: List<PlayerJourney> = emptyList()) = JourneyFrame(
        mode = JourneyMode.SPAN, main = journey(unlocks), rivals = rivals,
        spanFromMillis = 0, spanToMillis = t0 + 100 * D,
    )

    /**
     * UI feedback: a selected window IS the axis. Ninety days selected shows ninety days of axis
     * whether the unlocks cover nine of them or all - time is the selection, not the achievement
     * extent. The "all time" sentinel (spanFromMillis = 0) keeps the extent, because there the
     * data is the only statement of the range there is.
     */
    @Test
    fun `a selected window is the axis, not the unlock extent`() {
        val unlocks = (10..19).map { unlock(it, t0 + it * D, 10) }
        val windowed = JourneyFrame(
            mode = JourneyMode.SPAN, main = journey(unlocks), rivals = emptyList(),
            spanFromMillis = t0, spanToMillis = t0 + 90 * D,
        )

        val layout = LayoutEngine.layout(windowed, includeNodes = true)
        assertEquals(t0, layout.axisT0)
        assertEquals(t0 + 90 * D, layout.axisT1)

        // The all-time sentinel still spans the data.
        val allTime = windowed.copy(spanFromMillis = 0)
        val extent = LayoutEngine.layout(allTime, includeNodes = true)
        assertEquals(t0 + 10 * D, extent.axisT0)
        assertEquals(t0 + 19 * D, extent.axisT1)
    }

    /**
     * Every award of every kind reaches the stage, however dense the span around it.
     *
     * The pipeline that could drop one has four gates, and this walks all four on the same fixture:
     * [IconBudget.select] (the icon cap), [GridIconLayout.selectPerCell] (the per-division height
     * budget), the placement that turns the selection into circles, and the compact grid the export
     * and the still preview draw on. Thirty games awarded inside a busy span, the kinds interleaved,
     * with six hundred unlocks piled on top of them competing for the same budget.
     *
     * All three kinds, not only mastered: an award is the one thing on this chart that cannot be
     * inferred from anything else drawn on it, so a beaten one quietly missing is the same failure
     * as a mastery quietly missing, and it is a failure the chart itself cannot show.
     */
    @Test fun `a dense span never drops an award of any kind`() {
        val awards = 30
        val kinds = AwardKind.values()
        val medals = (0 until awards).map {
            Medal(kinds[it % kinds.size], t0 + it * 3 * D, gameId = it + 1, ownerName = null, title = "G$it")
        }
        val unlocks = (0 until 600).map {
            unlock(id = it, at = t0 + (it * 3 * D) / 5, pts = 5 + it % 30, gameId = (it % awards) + 1)
        }
        val main = journey(unlocks).copy(medals = medals)
        val f = JourneyFrame(
            mode = JourneyMode.SPAN, main = main, rivals = emptyList(),
            spanFromMillis = 0, spanToMillis = t0 + awards * 3 * D,
        )
        // The fixture has to be one the budget genuinely bites on, or none of this is a test.
        assertTrue(
            "the fixture must exceed the icon budget",
            medals.size + unlocks.size > IconBudget.budgetFor(f),
        )

        // 1. The icon budget keeps every medal, and pays for them out of the badges.
        val selection = IconBudget.select(medals, unlocks, IconBudget.budgetFor(f))
        assertEquals("the icon budget dropped an award", awards, selection.medalIndices.size)
        assertTrue("and badges are what it cut instead", selection.unlockIndices.size < unlocks.size)

        // 2. The per-division height budget keeps every medal, whatever the badges around them do.
        val widthFactor = LayoutEngine.widthFactorFor(f, includeNodes = true)
        val layout = LayoutEngine.layout(f, includeNodes = true, widthFactor = widthFactor)

        // 3. The placement turns every one of them into a drawn medallion, kind for kind.
        assertEquals("an award was dropped before it reached the stage", awards, layout.medals.size)
        kinds.forEach { kind ->
            assertEquals(
                "a $kind award was dropped",
                medals.count { it.kind == kind },
                layout.medals.count { it.medal.kind == kind },
            )
        }
        assertEquals(
            "each award is drawn exactly once",
            medals.map { it.gameId }.toSet(),
            layout.medals.map { it.medal.gameId }.toSet(),
        )

        // 4. And the compact chart the export and the still preview draw is not allowed to lose one
        // either: its grid is the coarser of the two, so it is the one that could.
        val compact = LayoutEngine.layout(f, includeNodes = true, widthFactor = 1f)
        assertEquals("the compact chart dropped an award", awards, compact.medals.size)
        kinds.forEach { kind ->
            assertEquals(
                "the compact chart dropped a $kind award",
                medals.count { it.kind == kind },
                compact.medals.count { it.medal.kind == kind },
            )
        }
    }

    /**
     * The height budget's own guarantee, isolated: a division carrying twenty medals - more than
     * could possibly be stacked in the plot - plus twenty badges competing with them, still draws
     * every medal.
     *
     * The grid knows nothing about award KINDS ([GridIconLayout.Icon] carries `isMedal` and nothing
     * else about them), which is exactly why this one reading holds for all three. The badges are
     * what the budget exists to ration, and the loop that rations them stops at the first icon that
     * does not fit - so a medal sorted behind a badge would be cut off by that break. Medals sort
     * first and are exempt from the budget outright; this is the test that fails if either half of
     * that is ever relaxed.
     */
    @Test fun `a division of nothing but medals keeps all of them`() {
        val boundaries = listOf(0f, 100f)
        val medals = (0 until 20).map {
            GridIconLayout.Icon(key = it, x = 50f, isMedal = true, points = 0)
        }
        val badges = (20 until 40).map {
            GridIconLayout.Icon(key = it, x = 50f, isMedal = false, points = 50, type = AchievementType.STANDARD)
        }

        val kept = GridIconLayout.selectPerCell(
            icons = medals + badges,
            boundaries = boundaries,
            fitRadius = GridIconLayout.R_MAX,
            innerH = 100f,
        )

        assertEquals(
            "the height budget dropped a medal from a medals-only division",
            20,
            kept.count { it.icon.isMedal },
        )
    }

    /**
     * And the same thing said end to end, kind by kind: one moment carrying awards of every kind,
     * more of them than the plot is tall, draws all of them.
     *
     * Twenty-one full-radius discs stack to several times the plot's height, so this is the case
     * the height budget would bite on if it were ever allowed to.
     */
    @Test fun `a single moment crammed with every award kind draws all of them`() {
        val kinds = AwardKind.values()
        // All within twenty minutes of each other, so they land in one division.
        val medals = (0 until 21).map {
            Medal(kinds[it % kinds.size], t0 + 10 * D + it * 60_000L, it + 1, null, "G$it")
        }
        val main = journey((0 until 40).map { unlock(it, t0 + it * D, 10) }).copy(medals = medals)
        val f = JourneyFrame(JourneyMode.SPAN, main, emptyList(), 0, t0 + 100 * D)

        val l = LayoutEngine.layout(f, includeNodes = true)

        assertEquals("an award was dropped from a crowded moment", 21, l.medals.size)
        kinds.forEach { kind ->
            assertEquals(
                "a $kind award was dropped from a crowded moment",
                medals.count { it.kind == kind },
                l.medals.count { it.medal.kind == kind },
            )
        }
    }

    @Test fun `golden coordinates on a fixed dataset`() {
        val f = frame(listOf(unlock(1, t0, 10), unlock(2, t0 + 10 * D, 20), unlock(3, t0 + 20 * D, 30)))
        val l = LayoutEngine.layout(f, includeNodes = true)
        assertEquals(60, l.ceiling)
        val innerW = LayoutEngine.W - LayoutEngine.PAD_L - LayoutEngine.PAD_R
        val innerH = LayoutEngine.H - LayoutEngine.PAD_T - LayoutEngine.PAD_B
        // curve: zero point + 3 anchors; x linear in time, y cumulative points
        assertEquals(4, l.main.points.size)
        assertEquals(LayoutEngine.PAD_L, l.main.points[0].x, 0.01f)
        assertEquals(LayoutEngine.PAD_T + innerH, l.main.points[0].y, 0.01f)
        assertEquals(LayoutEngine.PAD_L + innerW / 2f, l.main.points[2].x, 0.01f)
        assertEquals(LayoutEngine.PAD_T + innerH - (30f / 60f) * innerH, l.main.points[2].y, 0.01f)
        assertEquals(LayoutEngine.PAD_T, l.main.points[3].y, 0.01f)   // full ceiling at last point
        // Three badges in three different divisions leave room to spare, so the fitting radius is
        // the ceiling. All three are plain standard achievements and none of them beats the top
        // quartile of the three (30 is the quartile itself, not above it), so all three are drawn
        // at the standard tier's half of the ceiling.
        val rs = l.nodes.map { it.r }
        val standard = GridIconLayout.R_MAX * GridIconLayout.Tier.STANDARD.scale
        assertEquals(listOf(standard, standard, standard), rs)
    }

    /**
     * Size says what an achievement *is*, not a continuous reading of what it paid: an award is
     * full size, a win condition just under it, a progression/missable/top-quartile badge less
     * again, and the standard tail is half.
     */
    @Test fun `an icon's size comes from its tier`() {
        val unlocks = listOf(
            unlock(1, t0, 5),
            unlock(2, t0 + 10 * D, 5).copy(type = AchievementType.WIN_CONDITION),
            unlock(3, t0 + 20 * D, 5).copy(type = AchievementType.PROGRESSION),
            unlock(4, t0 + 30 * D, 5).copy(type = AchievementType.MISSABLE),
            unlock(5, t0 + 40 * D, 100),
            // The promotion is a quartile of the STANDARD badges, so there have to be enough of
            // them for a quarter of them to mean anything: with two, the top quartile by nearest
            // rank is the top scorer itself and nothing can beat it.
            unlock(6, t0 + 5 * D, 5),
            unlock(7, t0 + 15 * D, 5),
        )
        // Mid-span, so the award has a division to itself and nothing on this chart is crowded.
        val main = journey(unlocks).copy(medals = listOf(Medal(AwardKind.MASTERED, t0 + 25 * D, 1, null, "G1")))
        val f = JourneyFrame(JourneyMode.SPAN, main, emptyList(), 0, t0 + 100 * D)
        val l = LayoutEngine.layout(f, includeNodes = true)

        val fit = fitRadiusOf(l)
        fun radiusOf(id: Int) = l.nodes.single { it.unlock.id == id }.r
        assertEquals(fit, l.medals.single().radius, 0.001f)
        assertEquals(fit * GridIconLayout.Tier.WIN.scale, radiusOf(2), 0.001f)
        assertEquals(fit * GridIconLayout.Tier.HIGH.scale, radiusOf(3), 0.001f)
        assertEquals(fit * GridIconLayout.Tier.HIGH.scale, radiusOf(4), 0.001f)
        // A standard badge that beats the top quartile of what this chart's badges scored is
        // promoted; one that does not is the standard tail.
        assertEquals(fit * GridIconLayout.Tier.HIGH.scale, radiusOf(5), 0.001f)
        assertEquals(fit * GridIconLayout.Tier.STANDARD.scale, radiusOf(1), 0.001f)
    }

    @Test fun `same-day cluster does not overlap after relaxation`() {
        val cluster = (0 until 8).map { unlock(it, t0 + it * 60_000L, 5) }
        val l = LayoutEngine.layout(frame(cluster), includeNodes = true)
        for (i in l.nodes.indices) for (j in i + 1 until l.nodes.size) {
            val a = l.nodes[i]; val b = l.nodes[j]
            val dist = hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble())
            assertTrue("nodes $i,$j overlap: $dist", dist >= (a.r + b.r).toDouble() - 0.5)
        }
    }

    @Test fun `calendar axis range includes rival unlocks`() {
        val rival = journey(listOf(unlock(9, t0 - 30 * D, 5), unlock(10, t0 + 50 * D, 5))).copy(name = "r")
        val l = LayoutEngine.layout(frame(listOf(unlock(1, t0, 10)), rivals = listOf(rival)), includeNodes = false)
        assertEquals(t0 - 30 * D, l.axisT0)
        assertEquals(t0 + 50 * D, l.axisT1)
    }

    @Test fun `ceiling is shared max across players and curves start at zero`() {
        val rival = journey(listOf(unlock(9, t0, 50), unlock(10, t0 + D, 50))).copy(name = "r")
        val l = LayoutEngine.layout(frame(listOf(unlock(1, t0, 10)), rivals = listOf(rival)), includeNodes = false)
        assertEquals(100, l.ceiling)
        assertEquals(LayoutEngine.H - LayoutEngine.PAD_B, l.main.points[0].y, 0.01f)
        assertEquals(LayoutEngine.H - LayoutEngine.PAD_B, l.rivals[0].points[0].y, 0.01f)
    }

    @Test fun `race mode measures each player from own first unlock`() {
        val main = listOf(unlock(1, t0, 10), unlock(2, t0 + 2 * D, 10))
        val rival = journey(listOf(unlock(9, t0 + 100 * D, 10), unlock(10, t0 + 104 * D, 10))).copy(name = "r")
        val f = JourneyFrame(JourneyMode.GAME, journey(main), listOf(rival), 0, t0 + 200 * D)
        val l = LayoutEngine.layout(f, includeNodes = false)
        assertTrue(l.raceMode)
        assertEquals(4 * D, l.eMaxMillis)   // rival took 4 days, main 2
        // main's last anchor lands at half the axis (2d of 4d)
        val innerW = LayoutEngine.W - LayoutEngine.PAD_L - LayoutEngine.PAD_R
        assertEquals(LayoutEngine.PAD_L + innerW / 2f, l.main.points.last().x, 0.5f)
        // both curves' zero point at PAD_L
        assertEquals(LayoutEngine.PAD_L, l.rivals[0].points[0].x, 0.01f)
    }

    // --- medals on the curve ---

    /**
     * Hand-computed: ceiling 60, innerW 920, innerH 416. The curve runs (64, 478.667) at t0 to
     * (524, 340) at t0+10d, and a medal at t0+5d sits at x = 64 + 920/4 = 294, exactly halfway
     * between them, so its anchor y is the midpoint of those two y values.
     */
    @Test fun `a medal anchors on its owner's curve at its own time`() {
        val unlocks = listOf(unlock(1, t0, 10), unlock(2, t0 + 10 * D, 20), unlock(3, t0 + 20 * D, 30))
        val main = journey(unlocks).copy(
            medals = listOf(Medal(AwardKind.MASTERED, t0 + 5 * D, 1, null, "G1")),
        )
        val f = JourneyFrame(JourneyMode.SPAN, main, emptyList(), 0, t0 + 100 * D)
        val l = LayoutEngine.layout(f, includeNodes = true)

        assertEquals(1, l.medals.size)
        val m = l.medals.first()
        assertEquals(294f, m.anchorX, 0.01f)
        assertEquals(409.333f, m.anchorY, 0.05f)
        // A medal is drawn at the fitting radius itself, which is also the ceiling no badge on the
        // chart may exceed.
        assertEquals(GridIconLayout.R_MAX, m.radius, 0.01f)
        assertTrue("a badge outgrew an award", l.nodes.all { it.r <= m.radius })
        // The medal has room here, so relaxation leaves it where its anchor puts it in x...
        assertEquals(m.anchorX, m.x, 0.5f)
        // ...and a fixed clearance above or below it in y, so the curve - which passes through the
        // anchor by construction, and draws over the icons - cannot run through the middle of the
        // award's art. Still well inside its own radius, so the disc keeps covering the moment it
        // belongs to.
        val clearance = kotlin.math.abs(m.y - m.anchorY)
        assertTrue("the curve must not cross the middle of an award ($clearance)", clearance > m.radius * 0.5f)
        assertTrue("an award must not float off its own moment ($clearance)", clearance < m.radius)
    }

    /**
     * Every icon is one size now, medals included: a mastery is told apart by its kind dot and its
     * ring, not by being drawn bigger than the award next to it.
     */
    @Test fun `medal radii do not follow the award kind`() {
        val unlocks = listOf(unlock(1, t0, 10), unlock(2, t0 + 20 * D, 10))
        val main = journey(unlocks).copy(
            medals = listOf(
                Medal(AwardKind.MASTERED, t0 + 2 * D, 1, null, "G1"),
                Medal(AwardKind.COMPLETED, t0 + 10 * D, 1, null, "G1"),
                Medal(AwardKind.BEATEN, t0 + 18 * D, 1, null, "G1"),
            ),
        )
        val f = JourneyFrame(JourneyMode.SPAN, main, emptyList(), 0, t0 + 100 * D)
        val l = LayoutEngine.layout(f, includeNodes = true)
        assertEquals(1, l.medals.map { it.radius }.distinct().size)
        assertTrue("a badge may never outgrow an award", l.nodes.all { it.r <= l.medals.first().radius })
        // No medal is parked in the old top band any more.
        assertTrue(l.medals.all { it.y > LayoutEngine.PAD_T - 40f })
    }

    @Test fun `a rival's medal anchors on the rival's curve, not the main one`() {
        val rivalUnlocks = listOf(unlock(9, t0, 40), unlock(10, t0 + 20 * D, 40))
        val rival = journey(rivalUnlocks).copy(
            name = "r",
            medals = listOf(Medal(AwardKind.BEATEN, t0 + 10 * D, 1, "r", "r · G1")),
        )
        val main = journey(listOf(unlock(1, t0, 5), unlock(2, t0 + 20 * D, 5)))
        val f = JourneyFrame(JourneyMode.SPAN, main, listOf(rival), 0, t0 + 100 * D)
        val l = LayoutEngine.layout(f, includeNodes = false)

        val m = l.medals.single()
        val expected = LayoutEngine.curveYAt(l.rivals[0], m.anchorX, 0f)
        assertEquals(expected, m.anchorY, 0.01f)
        val onMain = LayoutEngine.curveYAt(l.main, m.anchorX, 0f)
        assertTrue("rival medal must not sit on the main curve", kotlin.math.abs(onMain - m.anchorY) > 10f)
    }

    @Test fun `medals and nodes do not overlap after the shared relaxation pass`() {
        // Two clusters far enough apart to land in different divisions, each with a couple of
        // medals in it, so both kinds of icon meet each other inside a busy cell. Both sit well
        // inside the axis rather than on its first moment: an icon whose anchor is in the very
        // corner of the stage is held against two clamp edges at once, where a fan-out has nowhere
        // to fan to, and that is a fact about the corner rather than about the relaxation.
        val cluster = (0 until 10).map { unlock(it, t0 + 10 * D + it * 60_000L, (it % 5) + 1) } +
            (0 until 10).map { unlock(100 + it, t0 + 30 * D + it * 60_000L, (it % 5) + 1) }
        val edges = listOf(unlock(900, t0, 5), unlock(901, t0 + 60 * D, 5))
        val main = journey(cluster + edges).copy(
            medals = (0 until 2).map { Medal(AwardKind.MASTERED, t0 + 10 * D + it * 60_000L, 1, null, "G1") } +
                (0 until 2).map { Medal(AwardKind.COMPLETED, t0 + 30 * D + it * 60_000L, 1, null, "G1") },
        )
        val f = JourneyFrame(JourneyMode.SPAN, main, emptyList(), 0, t0 + 100 * D)
        val l = LayoutEngine.layout(f, includeNodes = true)

        data class C(val x: Float, val y: Float, val r: Float, val tag: String)
        val circles = l.nodes.mapIndexed { i, n -> C(n.x, n.y, n.r, "node$i") } +
            l.medals.mapIndexed { i, m -> C(m.x, m.y, m.radius, "medal$i") }
        assertEquals(4, l.medals.size)
        assertTrue("fixture drew only ${circles.size} icons", circles.size >= 8)
        for (i in circles.indices) for (j in i + 1 until circles.size) {
            val a = circles[i]; val b = circles[j]
            val dist = hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble())
            assertTrue("${a.tag} and ${b.tag} overlap: $dist", dist >= (a.r + b.r).toDouble() - 0.5)
        }
    }

    // --- the x-axis grid ---

    /**
     * The boundaries [LayoutEngine.layout] rationed this chart's icons by, rebuilt from what the
     * layout published: the gridlines the axis draws, mapped to x the same way every other x on the
     * chart is. Reading them back rather than recomputing the ticks is the point - a test that
     * derived its own grid could agree with itself while disagreeing with the picture.
     */
    private fun boundariesOf(l: ChartLayout): List<Float> {
        val innerW = l.width - l.padL - l.padR
        val xs = l.ticks.map { t ->
            if (l.raceMode) {
                l.padL + innerW * t.tMillis.toFloat() / l.eMaxMillis.toFloat()
            } else {
                val span = (l.axisT1 - l.axisT0).coerceAtLeast(1)
                l.padL + (t.tMillis - l.axisT0).toFloat() / span.toFloat() * innerW
            }
        }
        // Width-aware, like the layout itself: a stretched stage is cut into equal divisions per
        // screenful rather than by the gridlines, and a helper that forgot that would check the
        // wide chart against a grid it was never rationed by.
        return GridIconLayout.divisionsFor(xs, l.padL, l.width - l.padR, l.width / LayoutEngine.W)
    }

    /** How many icons each division of [l] ended up drawing, keyed by division. */
    private fun cellCounts(l: ChartLayout): Map<Int, Int> {
        val b = boundariesOf(l)
        val xs = l.nodes.map { it.anchorX } + l.medals.map { it.anchorX }
        return xs.groupingBy { GridIconLayout.cellOf(it, b) }.eachCount()
    }

    /**
     * How tall each division's drawn icons actually stack: the diameters plus one gap between each
     * pair, which is the quantity the per-division budget is spent in.
     */
    private fun stackHeights(l: ChartLayout): Map<Int, Float> {
        val b = boundariesOf(l)
        val icons = l.nodes.map { it.anchorX to it.r } + l.medals.map { it.anchorX to it.radius }
        return icons.groupBy { GridIconLayout.cellOf(it.first, b) }
            .mapValues { (_, cell) ->
                cell.sumOf { (it.second * 2f).toDouble() }.toFloat() +
                    GridIconLayout.STACK_GAP * (cell.size - 1)
            }
    }

    /** Every division of [l] whose drawn stack fits the plot, i.e. all of them once badges thin. */
    private fun assertEveryStackFits(l: ChartLayout) {
        val innerH = LayoutEngine.H - LayoutEngine.PAD_T - LayoutEngine.PAD_B
        stackHeights(l).forEach { (cell, height) ->
            assertTrue("division $cell stacks $height into a $innerH plot", height <= innerH + 0.5f)
        }
    }

    /**
     * The fitting radius the grid chose for [l]: the size medals are drawn at, and the ceiling no
     * badge may exceed. Asserts both of those on the way past, so every caller below is also
     * checking that the size rule held.
     */
    private fun fitRadiusOf(l: ChartLayout): Float {
        val medalRadii = l.medals.map { it.radius }.distinct()
        if (medalRadii.isNotEmpty()) {
            assertEquals("medals must all be one size, got $medalRadii", 1, medalRadii.size)
            assertEquals("a medal is drawn at the fitting radius", l.fitRadius, medalRadii.single(), 0.001f)
        }
        val biggest = (l.nodes.map { it.r } + l.medals.map { it.radius }).max()
        assertTrue("an icon outgrew the fitting radius: $biggest > ${l.fitRadius}", biggest <= l.fitRadius + 0.001f)
        return l.fitRadius
    }

    /**
     * The height budget, on a real chart: a division draws as many icons as fit above it and not
     * one more. What that number is depends on the sizes the tiers hand out, which is the whole
     * point of it - so the guard is the invariant (every stack fits) plus the fact that the busiest
     * division genuinely filled up rather than merely being small.
     */
    @Test fun `no division of the axis ever stacks more than the plot can hold`() {
        val burst = (0 until 400).map { unlock(it, t0 + it * 60_000L, (it % 40) + 1) }
        val l = LayoutEngine.layout(frame(burst), includeNodes = true)
        assertTrue("chart drew nothing", l.nodes.isNotEmpty())
        assertEveryStackFits(l)
        assertTrue("this burst must have filled a division", stackHeights(l).values.max() > 300f)
    }

    /**
     * A full division on a busy chart: the icons are sized by whichever axis is tighter, and on
     * this fixture that is the x one. A nominal division of four would stack inside the plot's
     * height at the 44-unit ceiling, so the y-fit alone would hand them the ceiling; the division
     * they have to sit in is narrower than that, and it is what actually decides the radius.
     */
    @Test fun `a full division's icons are sized by whichever axis is tighter`() {
        val burst = (0 until 400).map { unlock(it, t0 + it * 60_000L, (it % 40) + 1) }
        val l = LayoutEngine.layout(frame(burst), includeNodes = true)

        val innerH = LayoutEngine.H - LayoutEngine.PAD_T - LayoutEngine.PAD_B
        val fit = fitRadiusOf(l)
        val division = GridIconLayout.typicalDivision(boundariesOf(l))
        val nominal = GridIconLayout.SIZING_CELL_ICONS
        assertEquals(GridIconLayout.fitRadius(innerH, nominal, division), fit, 0.001f)
        assertTrue(
            "fixture must be one the division width decides, not the y axis",
            fit < GridIconLayout.uniformRadius(innerH, nominal),
        )
        assertEveryStackFits(l)
    }

    /**
     * The capacity the hierarchy buys back: a division of ordinary standard badges draws twice what
     * a count cap of four allowed, because a standard badge is half the fitting radius.
     */
    @Test fun `a division of standard badges draws more than the old flat cap`() {
        val burst = (0 until 200).map { unlock(it, t0 + it * 60_000L, 5) }
        val l = LayoutEngine.layout(frame(burst), includeNodes = true)

        val busiest = cellCounts(l).values.max()
        assertTrue("a division drew only $busiest standard badges", busiest > GridIconLayout.SIZING_CELL_ICONS)
        assertEveryStackFits(l)
    }

    @Test fun `a sparse chart draws its icons at the ceiling, not as billboards`() {
        val sparse = (0 until 3).map { unlock(it, t0 + it * 10 * D, (it + 1) * 10) }
        val l = LayoutEngine.layout(frame(sparse), includeNodes = true)
        assertEquals(3, l.nodes.size)
        assertEquals(GridIconLayout.R_MAX, fitRadiusOf(l), 0.001f)
    }

    /**
     * A burst of unlocks all earned in the same moment lands in one division, so the ones that fit
     * above it are drawn and the rest are not - highest scores kept, and every medal kept whatever
     * it costs the badges. The curve, the HUD and the facts are computed from the journey, never
     * from what the chart draws, so nothing but the picture changes.
     */
    @Test fun `a same-timestamp burst fills its division, medals first`() {
        val burst = (1..25).map { unlock(it, t0, it) }
        val later = unlock(99, t0 + 50 * D, 5)
        val main = journey(burst + later).copy(
            medals = listOf(
                Medal(AwardKind.MASTERED, t0, 1, null, "G1"),
                Medal(AwardKind.COMPLETED, t0, 1, null, "G1"),
                Medal(AwardKind.BEATEN, t0, 1, null, "G1"),
            ),
        )
        val f = JourneyFrame(JourneyMode.SPAN, main, emptyList(), 0, t0 + 100 * D)
        val l = LayoutEngine.layout(f, includeNodes = true)

        val b = boundariesOf(l)
        val firstCell = l.nodes.filter { GridIconLayout.cellOf(it.anchorX, b) == 0 }
        // Three full-size medals eat most of the division; what is left goes to the top scorers of
        // the burst, in order, and the 1-pointers at the bottom of it are never drawn.
        assertEquals(3, l.medals.size)
        assertTrue("the burst's division drew nothing at all", firstCell.isNotEmpty())
        assertTrue("the burst's division drew all 25 badges", firstCell.size < 25)
        val kept = firstCell.map { it.unlock.points }.sortedDescending()
        assertEquals((25 downTo 26 - kept.size).toList(), kept)
        assertEveryStackFits(l)
        // The unlock away from the burst is untouched.
        assertTrue(l.nodes.any { it.unlock.id == 99 })
    }

    /** Medals fill a division on their own before any badge gets a place in it. */
    @Test fun `a division full of medals draws no badges at all`() {
        val burst = (1..8).map { unlock(it, t0, it) }
        val main = journey(burst + unlock(99, t0 + 50 * D, 5)).copy(
            medals = (1..12).map { Medal(AwardKind.BEATEN, t0, it, null, "G$it") },
        )
        val f = JourneyFrame(JourneyMode.SPAN, main, emptyList(), 0, t0 + 100 * D)
        val l = LayoutEngine.layout(f, includeNodes = true)

        val b = boundariesOf(l)
        assertEquals(12, l.medals.size)                                        // never dropped
        assertEquals(0, l.nodes.count { GridIconLayout.cellOf(it.anchorX, b) == 0 })
        assertTrue(l.nodes.any { it.unlock.id == 99 })
    }

    /** A chart spread thinly enough that no division fills keeps every badge it selected. */
    @Test fun `a spread chart keeps every badge it selected`() {
        val spread = (0 until 40).map { unlock(it, t0 + it * D, (it % 7) + 1) }
        val l = LayoutEngine.layout(frame(spread), includeNodes = true)

        assertEquals(40, l.nodes.size)
        assertEveryStackFits(l)
    }

    // --- icon budget ---

    /**
     * The budget still runs first and the grid thins what it picked, so both bites show: the drawn
     * set is smaller than the budget allowed, and none of the journey's long tail of low scorers is
     * in it. (The two caps do not know about each other, so the budget can spend itself on badges a
     * full division then drops - accepted: the budget is about how much a chart may draw at all, the
     * grid about how much one moment of it may show.)
     */
    @Test fun `the icon budget picks the highest scorers before the grid thins them`() {
        // 800 unlocks: main unlocks alone (> 300) push this frame past IconBudget.budgetFor's
        // sparse threshold, so the budget here is the expanded 400, not the default 200.
        val many = (0 until 800).map { unlock(it, t0 + it * 60_000L, (it % 40) + 1) }
        val f = frame(many)
        val l = LayoutEngine.layout(f, includeNodes = true)
        assertEquals(IconBudget.EXPANDED_ICONS, IconBudget.budgetFor(f))

        assertTrue("the chart drew nothing at all", l.nodes.isNotEmpty())
        assertTrue("the grid did not thin a 800-unlock burst", l.nodes.size < IconBudget.EXPANDED_ICONS)
        assertEveryStackFits(l)
        // Only the top 400 by points were ever candidates, so the tail never reaches the chart.
        val kept = l.nodes.map { it.unlock.points }
        assertTrue("lowest kept score ${kept.min()}", kept.min() >= 20)
        // The curve still knows about every unlock even though only a fraction got an icon.
        assertEquals(801, l.main.points.size)
        assertEquals(800, l.main.bgTrack.size)
    }

    /**
     * Single-game mode draws the whole achievement list. The chart there is one game, and a badge
     * quietly missing from it is a hole in the very thing being charted - so neither the budget nor
     * the per-division height budget takes anything away, however tightly the unlocks are clustered.
     */
    @Test fun `a single-game chart draws every achievement it earned`() {
        val all = (0 until 100).map { unlock(it, t0 + it * 3_600_000L, (it % 10) + 1) }
        val f = JourneyFrame(JourneyMode.GAME, journey(all), emptyList(), 0, t0 + 100 * D)
        val l = LayoutEngine.layout(f, includeNodes = true)

        assertEquals(100, l.nodes.size)
        assertEquals(100, IconBudget.budgetFor(f))
        // Same tiers as anywhere else: the standard tail at half the fitting radius is what makes
        // drawing all of them affordable in the first place.
        assertTrue(l.nodes.map { it.r }.distinct().size > 1)
        assertTrue("no icon may be drawn below the size floor", l.nodes.all { it.r >= GridIconLayout.R_MIN })
    }

    /** A same-moment single-game burst overflows its division rather than losing achievements. */
    @Test fun `a single-game burst overflows instead of dropping`() {
        val all = (0 until 60).map { unlock(it, t0 + it * 1000L, 5) }
        val f = JourneyFrame(JourneyMode.GAME, journey(all), emptyList(), 0, t0 + 100 * D)
        val l = LayoutEngine.layout(f, includeNodes = true)
        assertEquals(60, l.nodes.size)
    }

    // --- how big an icon is drawn ---

    /**
     * A badge's size says which tier it is in, and the top quartile of what a chart's badges scored
     * is one of the ways into the tier above the tail. Nothing is ever drawn bigger than the fitting
     * radius the grid computed, which is what keeps "a division's stack fits above it" true.
     */
    @Test fun `a badge is drawn by the tier its score puts it in`() {
        val busy = (0 until 45).map { unlock(it, t0 + it * D, it + 1) }
        val l = LayoutEngine.layout(frame(busy), includeNodes = true)

        val fit = fitRadiusOf(l)
        val cheapest = l.nodes.minBy { it.unlock.points }
        val dearest = l.nodes.maxBy { it.unlock.points }
        assertEquals(fit * GridIconLayout.Tier.HIGH.scale, dearest.r, 0.001f)
        assertEquals(fit * GridIconLayout.Tier.STANDARD.scale, cheapest.r, 0.001f)
        assertTrue("a badge may never outgrow the fitting radius", l.nodes.all { it.r <= fit + 0.001f })
        // A quarter of them, give or take the rank rounding, are in the promoted tier.
        val promoted = l.nodes.count { it.r > fit * GridIconLayout.Tier.STANDARD.scale + 0.001f }
        assertTrue("$promoted of ${l.nodes.size} promoted", promoted in 1 until l.nodes.size / 2)
    }

    /** Medals do not: an award is told apart by its kind dot and its ring, never by being bigger. */
    @Test fun `medals are all one size whatever the badges around them do`() {
        val busy = (0 until 45).map { unlock(it, t0 + it * D, it + 1) }
        val main = journey(busy).copy(
            medals = (0 until 3).map { Medal(AwardKind.MASTERED, t0 + it * 10 * D, 1, null, "G1") },
        )
        val f = JourneyFrame(JourneyMode.SPAN, main, emptyList(), 0, t0 + 100 * D)
        val l = LayoutEngine.layout(f, includeNodes = true)

        assertEquals(1, l.medals.map { it.radius }.distinct().size)
        assertEquals(fitRadiusOf(l), l.medals.first().radius, 0.001f)
        assertTrue("badges vary, so this fixture proves medals do not", l.nodes.map { it.r }.distinct().size > 1)
    }

    /**
     * Only medals can push a division past the cap, so only a medal-heavy moment ever sizes a chart
     * down: everything the grid alone rations fits at the ceiling.
     */
    @Test fun `a division medals push past the cap draws smaller icons than a sparse one`() {
        val sparse = LayoutEngine.layout(
            frame((0 until 12).map { unlock(it, t0 + it * D, it + 1) }), includeNodes = true,
        )
        val crowdedMain = journey(listOf(unlock(1, t0, 10), unlock(2, t0 + 60 * D, 10))).copy(
            medals = (1..8).map { Medal(AwardKind.BEATEN, t0, it, null, "G$it") },
        )
        val crowded = LayoutEngine.layout(
            JourneyFrame(JourneyMode.SPAN, crowdedMain, emptyList(), 0, t0 + 100 * D), includeNodes = true,
        )
        assertEquals(GridIconLayout.R_MAX, fitRadiusOf(sparse), 0.001f)
        assertTrue(fitRadiusOf(crowded) < fitRadiusOf(sparse))
        assertTrue(fitRadiusOf(crowded) >= GridIconLayout.R_MIN)
    }

    /**
     * Medals are the one thing that can push a division past ten, because they are never dropped.
     * Thirty in one moment would size every icon on the chart down to five units, so the floor is
     * what stops the art becoming unreadable - the stack simply runs past the plot instead.
     */
    @Test fun `a division crammed with medals stops at the size floor`() {
        val main = journey(listOf(unlock(1, t0, 10), unlock(2, t0 + 60 * D, 10))).copy(
            medals = (1..30).map { Medal(AwardKind.BEATEN, t0, it, null, "G$it") },
        )
        val f = JourneyFrame(JourneyMode.SPAN, main, emptyList(), 0, t0 + 100 * D)
        val l = LayoutEngine.layout(f, includeNodes = true)

        assertEquals(30, l.medals.size)
        assertEquals(GridIconLayout.R_MIN, fitRadiusOf(l), 0.001f)
        // The spread cannot take a badge below the floor either: shrinking a floored icon further
        // would take the art past being recognisable.
        assertTrue(l.nodes.all { it.r >= GridIconLayout.R_MIN - 0.001f })
    }

    /**
     * Each rival's medal resolves to that rival's own curve. The lookup is by identity rather than
     * by `indexOf`: PlayerJourney is a data class, so structural equality deep-walks every rival's
     * whole unlock list for every medal on the chart, and two rivals that happen to compare equal
     * would both resolve to the first one's curve.
     */
    @Test fun `each rival's medal lands on that rival's own curve`() {
        val slow = journey(listOf(unlock(9, t0, 10), unlock(10, t0 + 20 * D, 10))).copy(
            name = "slow",
            medals = listOf(Medal(AwardKind.BEATEN, t0 + 10 * D, 1, "slow", "slow · G1")),
        )
        val fast = journey(listOf(unlock(11, t0, 60), unlock(12, t0 + 20 * D, 60))).copy(
            name = "fast",
            medals = listOf(Medal(AwardKind.MASTERED, t0 + 10 * D, 1, "fast", "fast · G1")),
        )
        val main = journey(listOf(unlock(1, t0, 5), unlock(2, t0 + 20 * D, 5)))
        val f = JourneyFrame(JourneyMode.SPAN, main, listOf(slow, fast), 0, t0 + 100 * D)
        val l = LayoutEngine.layout(f, includeNodes = false)

        assertEquals(2, l.medals.size)
        l.medals.forEach { m ->
            val ownIndex = if (m.medal.ownerName == "slow") 0 else 1
            val otherIndex = 1 - ownIndex
            assertEquals(
                "${m.medal.ownerName}'s medal must sit on its own curve",
                LayoutEngine.curveYAt(l.rivals[ownIndex], m.anchorX, 0f), m.anchorY, 0.01f,
            )
            val onOther = LayoutEngine.curveYAt(l.rivals[otherIndex], m.anchorX, 0f)
            assertTrue(
                "fixture curves must be far enough apart to tell them apart",
                kotlin.math.abs(onOther - m.anchorY) > 10f,
            )
        }
    }

    @Test fun `medals eat the budget before badges do`() {
        // 400 main unlocks alone crosses budgetFor's 300-unlock threshold, so this frame's cap is
        // the expanded 400 - still fewer than the 400 unlocks on offer, so the cap still bites.
        val many = (0 until 400).map { unlock(it, t0 + it * 60_000L, (it % 40) + 1) }
        val main = journey(many).copy(
            medals = (0 until 30).map { Medal(AwardKind.BEATEN, t0 + it * 120_000L, 1, null, "G1") },
        )
        val f = JourneyFrame(JourneyMode.SPAN, main, emptyList(), 0, t0 + 100 * D)
        assertEquals(IconBudget.EXPANDED_ICONS, IconBudget.budgetFor(f))

        // At the budget: every medal, then the badges with what is left.
        val selection = IconBudget.select(main.medals, main.unlocks, IconBudget.budgetFor(f))
        assertEquals(30, selection.medalIndices.size)
        assertEquals(IconBudget.EXPANDED_ICONS - 30, selection.unlockIndices.size)

        // On the stage: the grid then thins the badges again, and never a medal.
        val l = LayoutEngine.layout(f, includeNodes = true)
        assertEquals(30, l.medals.size)
        assertTrue(l.nodes.size < selection.unlockIndices.size)
    }

    /**
     * 250 unlocks is under [IconBudget.LONG_MAIN_UNLOCKS] but the 249-day span crosses
     * [IconBudget.LONG_SPAN_DAYS], so `budgetFor` raises the cap to 400: generous enough that the
     * budget drops none of these 250 unlocks. What thins the chart is the grid alone, and only
     * where a fortnight of the axis carried more than ten.
     */
    @Test fun `a journey spanning more than 183 days draws the expanded budget`() {
        val many = (0 until 250).map { unlock(it, t0 + it * D, (it % 40) + 1) }   // 249 days
        val f = frame(many)
        assertEquals(IconBudget.EXPANDED_ICONS, IconBudget.budgetFor(f))
        assertEquals(250, IconBudget.select(emptyList(), many, IconBudget.budgetFor(f)).unlockIndices.size)

        val l = LayoutEngine.layout(f, includeNodes = true)
        assertTrue("the grid should have thinned a 250-badge chart", l.nodes.size in 60..249)
        assertEveryStackFits(l)
    }

    /**
     * Compare mode draws badges again, one a day per player, whoever the caller says about the
     * main player's own set: a handful of per-day badges is what makes a compare chart readable,
     * and the [includeNodes] flag is about how wide the stage gets rather than about whether
     * anything is drawn on it.
     */
    @Test fun `compare mode draws one badge a day for every player`() {
        val rival = journey(
            listOf(
                unlock(9, t0, 5),
                unlock(11, t0 + 3_600_000L, 40),   // same day as 9, and worth more
                unlock(10, t0 + 50 * D, 5),
            ),
        ).copy(name = "r")
        val main = journey(listOf(unlock(1, t0, 10), unlock(2, t0 + 20 * D, 10))).copy(
            medals = listOf(Medal(AwardKind.MASTERED, t0 + 10 * D, 1, null, "G1")),
        )
        val f = JourneyFrame(JourneyMode.SPAN, main, listOf(rival), 0, t0 + 100 * D)
        val l = LayoutEngine.layout(f, includeNodes = false)

        assertEquals(1, l.medals.size)
        assertEquals(2, l.nodes.count { it.ownerName == null })
        val rivalNodes = l.nodes.filter { it.ownerName == "r" }
        assertEquals(2, rivalNodes.size)
        assertTrue(
            "the rival's day must be spoken for by its highest scorer",
            rivalNodes.any { it.unlock.id == 11 } && rivalNodes.none { it.unlock.id == 9 },
        )
    }

    /**
     * The same guarantee end to end, on the chart that needs it: a span compare where the rival
     * outscores the main player twenty to one on every single day.
     *
     * The per-day selection promises one badge a day per player, and the height budget is what
     * could quietly take it back - a division holds what it holds, and before the round-robin the
     * bigger scores took all of it. See GridIconLayoutTest for the rule itself.
     */
    @Test fun `a span compare keeps the main player in every division a rival dominates`() {
        val days = 24
        val main = journey((0 until days).map { unlock(it, t0 + it * D + 3_600_000L, 5) })
        val rival = journey((0 until days).map { unlock(100 + it, t0 + it * D + 7_200_000L, 100) })
            .copy(name = "r")
        val f = JourneyFrame(JourneyMode.SPAN, main, listOf(rival), 0, t0 + days * D)
        val l = LayoutEngine.layout(f, includeNodes = false)

        val boundaries = GridIconLayout.divisions(
            l.ticks.map { LayoutEngine.PAD_L + (it.tMillis - l.axisT0).toFloat() / (l.axisT1 - l.axisT0) * (l.width - LayoutEngine.PAD_L - LayoutEngine.PAD_R) },
            LayoutEngine.PAD_L,
            l.width - LayoutEngine.PAD_R,
        )
        val byCell = l.nodes.groupBy { GridIconLayout.cellOf(it.anchorX, boundaries) }
        assertTrue("fixture must crowd several divisions", byCell.any { it.value.size > 2 })
        byCell.forEach { (cell, nodes) ->
            assertTrue(
                "division $cell drew ${nodes.size} badges and none of them the main player's",
                nodes.any { it.ownerName == null },
            )
        }
        assertTrue("both players must be on the chart", l.nodes.any { it.ownerName == "r" })
    }

    // --- wide timeline ---

    @Test fun `widthFactorFor stays at 1 while the chart is not crowded`() {
        val sparse = (0 until 40).map { unlock(it, t0 + it * D, 5) }
        assertEquals(1f, LayoutEngine.widthFactorFor(frame(sparse)), 0.0001f)
        // Exactly one screenful of icons is still one screenful.
        val exactly = (0 until 90).map { unlock(it, t0 + it * D, 5) }
        assertEquals(1f, LayoutEngine.widthFactorFor(frame(exactly)), 0.0001f)
    }

    @Test fun `widthFactorFor picks the nearest step that covers the density`() {
        fun factorFor(n: Int) = LayoutEngine.widthFactorFor(frame((0 until n).map { unlock(it, t0 + it * D, 5) }))
        assertEquals(1.5f, factorFor(100), 0.0001f)    // 1.11 screenfuls
        assertEquals(1.5f, factorFor(135), 0.0001f)    // exactly 1.5
        assertEquals(2f, factorFor(150), 0.0001f)      // 1.67
        assertEquals(3f, factorFor(200), 0.0001f)      // 2.22
        assertEquals(3f, factorFor(270), 0.0001f)      // exactly 3
        assertEquals(4f, factorFor(280), 0.0001f)      // 3.11
    }

    /** The budget caps the icons, so it caps the stretch too: a huge journey stops at 4x. */
    @Test fun `widthFactorFor is capped at 4x by the icon budget`() {
        val huge = (0 until 2000).map { unlock(it, t0 + it * 60_000L, 5) }
        assertEquals(4f, LayoutEngine.widthFactorFor(frame(huge)), 0.0001f)
    }

    // --- badges are always drawn now ---

    /**
     * The density-based awards-only fallback is gone. It existed because several hundred badges on
     * one stage turned into soup; the grid cap is what prevents that now, and it does it by drawing
     * ten per division rather than by taking every badge on the chart away. Even the 689-unlock
     * burst that used to trip the old rule draws its badges - just not all of them.
     */
    @Test fun `even the densest journey still draws badges`() {
        val burst = (0 until 689).map { unlock(it, t0 + it * 60_000L, 5) }
        val f = frame(burst)
        assertEquals(4f, LayoutEngine.widthFactorFor(f), 0.0001f)

        val l = LayoutEngine.layout(f, includeNodes = true, widthFactor = LayoutEngine.widthFactorFor(f))
        assertTrue("the densest chart must still show badges", l.nodes.isNotEmpty())
        assertEveryStackFits(l)
    }

    /**
     * What gets drawn decides how much room it needs, never the other way round: a chart with no
     * badges on it (compare mode) would otherwise be four screenfuls of bare curve.
     */
    @Test fun `widthFactorFor is 1 for a chart that draws no badges`() {
        val dense = frame((0 until 300).map { unlock(it, t0 + it * D, 5) })
        assertEquals(4f, LayoutEngine.widthFactorFor(dense, includeNodes = true), 0.0001f)
        assertEquals(1f, LayoutEngine.widthFactorFor(dense, includeNodes = false), 0.0001f)
        assertEquals(LayoutEngine.W, LayoutEngine.layout(dense, includeNodes = false).width, 0.01f)
    }

    /**
     * The stretch now buys real capacity: a widened stage is cut into more divisions, so it draws
     * more icons than the compact one rather than the same icons further apart.
     *
     * The compact chart's icons are a subset of the wide chart's, which is the direction that
     * matters and is pinned here because the consequence is silent. The bitmaps are preloaded from
     * the WIDE layout; the export and the still preview draw the compact one. Were the compact
     * chart to draw an icon the wide one does not, its art would never have been requested and the
     * export would fall back to the game icon or a plain disc - a picture that is quietly worse, not
     * a failure anything would catch.
     */
    @Test fun `the compact layout draws a subset of the wide one's icons`() {
        val many = (0 until 250).map { unlock(it, t0 + it * D, (it % 40) + 1) }
        val main = journey(many).copy(
            medals = (0 until 5).map { Medal(AwardKind.BEATEN, t0 + it * 10 * D, 1, null, "G1") },
        )
        val f = JourneyFrame(JourneyMode.SPAN, main, emptyList(), 0, t0 + 300 * D)

        val compact = LayoutEngine.layout(f, includeNodes = true)
        val compactIds = compact.nodes.map { it.unlock.id }.toSet()

        LayoutEngine.WIDTH_FACTORS.drop(1).forEach { factor ->
            val wide = LayoutEngine.layout(f, includeNodes = true, widthFactor = factor)
            val wideIds = wide.nodes.map { it.unlock.id }.toSet()
            assertTrue(
                "at ${factor}x, compact drew ${(compactIds - wideIds).size} badges the wide layout has no art for",
                wideIds.containsAll(compactIds),
            )
            assertTrue("a ${factor}x stage may never carry fewer icons", wideIds.size >= compactIds.size)
            // Medals are never dropped by either grid, so both charts carry all of them.
            assertEquals(compact.medals.map { it.medal.whenMillis }, wide.medals.map { it.medal.whenMillis })
        }

        // And the extra room is real capacity, not just more space between the same icons.
        val widest = LayoutEngine.layout(f, includeNodes = true, widthFactor = 4f)
        assertTrue("the widest stage must carry more icons", widest.nodes.size > compact.nodes.size)
    }

    /**
     * The containment guard, on the shape that broke it.
     *
     * Capping only the *width* term of the fitting radius was not enough. The height term keys off
     * how many icons a division holds, and a refined wide grid splits a busy division into quieter
     * ones - so the wide chart read a smaller count, sized its icons back up, and lost capacity.
     * Five medals inside one compact division is what makes the two counts differ (badges are
     * bounded by the nominal division, medals are not), and measured before the fix a 1.5x stage
     * came out at 29.5 against the compact chart's 26.2 and drew badges the compact chart did not.
     *
     * That is silent damage rather than a wrong picture: the badge art is preloaded from the WIDE
     * layout while the export and the still preview draw the compact one, so a compact-only badge
     * is drawn with art nobody ever requested.
     */
    @Test fun `a stretched stage never sizes its icons up, whatever the medals do`() {
        val many = (0 until 200).map { unlock(it, t0 + it * D, (it % 40) + 1) }
        // Five awards inside a single compact division, and badges all around them.
        val main = journey(many).copy(
            medals = (0 until 5).map { Medal(AwardKind.BEATEN, t0 + 40 * D + it * 60_000L, it, null, "G$it") },
        )
        val f = JourneyFrame(JourneyMode.SPAN, main, emptyList(), 0, t0 + 300 * D)

        val compact = LayoutEngine.layout(f, includeNodes = true)
        val compactIds = compact.nodes.map { it.unlock.id }.toSet()

        LayoutEngine.WIDTH_FACTORS.drop(1).forEach { factor ->
            val wide = LayoutEngine.layout(f, includeNodes = true, widthFactor = factor)
            assertTrue(
                "at ${factor}x the stretch grew the icons: ${wide.fitRadius} > ${compact.fitRadius}",
                wide.fitRadius <= compact.fitRadius + 0.001f,
            )
            val wideIds = wide.nodes.map { it.unlock.id }.toSet()
            assertTrue(
                "at ${factor}x, compact drew ${(compactIds - wideIds).size} badges the wide layout has no art for",
                wideIds.containsAll(compactIds),
            )
        }
    }

    @Test fun `a width factor stretches the virtual space and every x with it`() {
        val f = frame(listOf(unlock(1, t0, 10), unlock(2, t0 + 10 * D, 20), unlock(3, t0 + 20 * D, 30)))
        val narrow = LayoutEngine.layout(f, includeNodes = true)
        val wide = LayoutEngine.layout(f, includeNodes = true, widthFactor = 3f)

        assertEquals(LayoutEngine.W * 3f, wide.width, 0.01f)
        assertEquals(LayoutEngine.H, wide.height, 0.01f)
        // Pads are untouched, so the inner plot area carries the whole stretch.
        assertEquals(LayoutEngine.PAD_L, wide.padL, 0.01f)
        assertEquals(LayoutEngine.PAD_R, wide.padR, 0.01f)
        val wideInner = LayoutEngine.W * 3f - LayoutEngine.PAD_L - LayoutEngine.PAD_R
        assertEquals(LayoutEngine.PAD_L + wideInner / 2f, wide.main.points[2].x, 0.01f)
        // The y axis is unchanged: only the timeline got longer.
        assertEquals(narrow.main.points.map { it.y }, wide.main.points.map { it.y })
        assertEquals(narrow.ceiling, wide.ceiling)
    }

    /** The relaxation pass has to know about the wider stage, or every icon piles up before 1060. */
    @Test fun `icons may be placed across the whole widened stage`() {
        val many = (0 until 120).map { unlock(it, t0 + it * D, (it % 40) + 1) }
        val wide = LayoutEngine.layout(frame(many), includeNodes = true, widthFactor = 3f)
        assertTrue(
            "no icon was placed past the unstretched stage",
            wide.nodes.any { it.x > LayoutEngine.W },
        )
        assertTrue(
            "an icon was pushed off the widened stage",
            wide.nodes.all { it.x <= wide.width - LayoutEngine.PAD_R + 0.5f },
        )
    }

    @Test fun `smooth path uses midpoint control points`() {
        val segs = LayoutEngine.smoothPathSegments(listOf(P(0f, 0f), P(10f, 10f), P(20f, 0f)))
        assertEquals(2, segs.size)
        assertEquals(5f, segs[0].c1.x, 0.01f); assertEquals(0f, segs[0].c1.y, 0.01f)
        assertEquals(5f, segs[0].c2.x, 0.01f); assertEquals(10f, segs[0].c2.y, 0.01f)
        assertEquals(P(10f, 10f), segs[0].end)
    }

    // --- relax convergence ---

    /**
     * Anchor positions and radii the way `LayoutEngine.layout` actually produces them for a
     * journey shaped like a real one: [burstCount] play sessions of [perBurst] unlocks each,
     * [secondsWithinBurst] apart inside a session, spread evenly across [spanDays] - not one giant
     * burst and not one unlock every few seconds for the whole span. `NodeLayout.anchorX`/`anchorY`
     * are the pre-relax anchor (relax never mutates them once placed), so reading them back out is
     * safe even though `layout()` already ran a relax pass to get there.
     *
     * The fixture takes whatever the grid cap leaves rather than insisting on every unlock: the
     * whole point of the cap is that a busy division does not get to place more icons than fit, and
     * what this guard needs is a dense set of real anchors, not a particular count.
     */
    private fun burstyAnchorFixture(
        burstCount: Int,
        perBurst: Int,
        spanDays: Long,
        secondsWithinBurst: Long,
        pointsMod: Int = 5,
    ): List<Triple<Float, Float, Float>> {
        val unlocks = mutableListOf<Unlock>()
        var id = 0
        repeat(burstCount) { b ->
            val baseT = t0 + spanDays * D * b / burstCount
            repeat(perBurst) { k ->
                unlocks += unlock(id++, baseT + k * secondsWithinBurst * 1000L, (id % pointsMod) + 1)
            }
        }
        val l = LayoutEngine.layout(frame(unlocks), includeNodes = true)
        // An eighth rather than the half this asked for while ten icons fitted a division. The
        // per-division cap is four now, so the ceiling is the division count times four - 13 x 4 =
        // 52 for this fixture's 1170-day span, which is exactly what it places. Anything near that
        // ceiling is a trap rather than a guard: the axis picks 3-month gridlines here by a hair
        // (4-month ticks score better and are only rejected for falling one tick short of the
        // generator's ten-tick floor), and a nudge over that cliff drops the grid to 11 divisions
        // and the ceiling to 44 - so a tighter bound would not fail loudly, it would THROW out of
        // the fixture and take the relaxation guard with it. What actually guarantees the fixture
        // is worth relaxing is the raw overlap count its caller asserts on.
        check(l.nodes.size >= unlocks.size / 8) {
            "fixture must stay dense, got ${l.nodes.size} of ${unlocks.size}"
        }
        return l.nodes.map { Triple(it.anchorX, it.anchorY, it.r) }
    }

    private fun circlesFrom(fixture: List<Triple<Float, Float, Float>>) =
        fixture.map { (x, y, r) -> LayoutEngine.Circle(x, y, r) }

    /** How many pairs are still closer than their radii allow, beyond floating-point slack. */
    private fun overlapCount(circles: List<LayoutEngine.Circle>): Int {
        var n = 0
        for (i in circles.indices) for (j in i + 1 until circles.size) {
            val a = circles[i]
            val b = circles[j]
            val dist = hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble())
            if (dist < (a.r + b.r).toDouble() - 0.5) n++
        }
        return n
    }

    /**
     * The guard a since-reverted speed-up attempt (a Jacobi rewrite that
     * limit-cycled instead of converging) did not have: every other test in this file asserts on a
     * few specific icons or a general invariant, never that a *dense* fixture's converged result is
     * substantially resolved - which is exactly the gap that let a broken relaxation through review
     * undetected. Forty short sessions' worth of icons, all now at one radius: hundreds of raw
     * pairwise overlaps on the unrelaxed anchors, real collisions [LayoutEngine]'s fixed iteration
     * budget has to actually resolve, not just avoid a particular assertion.
     *
     * It asks for "all but a handful" rather than for none only because the anchor pull can hold a
     * pair a whisker inside each other at equilibrium; in practice this fixture settles at one. See
     * [MAX_RESIDUAL_OVERLAPS] for how the bound was calibrated.
     */
    @Test fun `relax resolves all but a handful of overlaps on a dense fixture`() {
        // burstCount/perBurst/spanDays/secondsWithinBurst describe a journey shaped like a real
        // one - forty short sessions spread over three years - rather than a worst case built to
        // make the number come out.
        val fixture = burstyAnchorFixture(burstCount = 40, perBurst = 5, spanDays = 1200, secondsWithinBurst = 1500)
        val rawOverlaps = overlapCount(circlesFrom(fixture))
        assertTrue("fixture must actually need resolving, found $rawOverlaps raw overlaps", rawOverlaps > 50)

        val circles = circlesFrom(fixture)
        LayoutEngine.relax(circles)
        val left = overlapCount(circles)
        assertTrue("relax left $left of $rawOverlaps overlaps unresolved", left <= MAX_RESIDUAL_OVERLAPS)
    }
}
