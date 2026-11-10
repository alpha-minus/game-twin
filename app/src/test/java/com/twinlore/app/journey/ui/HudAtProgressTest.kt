package com.twinlore.app.journey.ui

import com.twinlore.app.journey.domain.LayoutEngine
import com.twinlore.app.journey.domain.frameUpToElapsed
import com.twinlore.app.journey.domain.model.AchievementType
import com.twinlore.app.journey.domain.model.AwardKind
import com.twinlore.app.journey.domain.model.GameRef
import com.twinlore.app.journey.domain.model.AwardTally
import com.twinlore.app.journey.domain.model.Medal
import com.twinlore.app.journey.domain.model.JourneyFrame
import com.twinlore.app.journey.domain.model.JourneyMode
import com.twinlore.app.journey.domain.model.PlayerJourney
import com.twinlore.app.journey.domain.model.Unlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The HUD counters as the sweep passes unlocks. Pure layout maths, so this runs without Compose,
 * Robolectric or a `PathMeasure`.
 */
class HudAtProgressTest {

    private val day = 86_400_000L

    private fun unlock(id: Int, points: Int, atDay: Int, gameId: Int) = Unlock(
        id = id, title = "u$id", description = "d", points = points, badgeName = "b$id",
        type = AchievementType.STANDARD, earnedAtMillis = atDay * day, numAwarded = 3, gameId = gameId,
    )

    private fun game(id: Int, colorIndex: Int) = GameRef(
        id = id, title = "g$id", iconPath = null, artPaths = emptyList(),
        colorIndex = colorIndex, players = 10, award = null,
    )

    /**
     * Whether the sweep is inside one of the main player's streak runs, which is what the player
     * bar tints its scrub track by.
     *
     * The chart asks the same question in arc length along the smoothed cubic; this asks it in x on
     * the polyline through the same points, for the same reason every other answer in this file
     * does (no `PathMeasure` off a device). The two agree to well inside the whole-percent cadence
     * anything reads this at.
     */
    @Test
    fun `the sweep is inside a streak only while it is over the run`() {
        // Three days running, then a month's gap, then two more unlocks.
        val unlocks = (0 until 3).map { unlock(it, 10, it, 100) } +
            listOf(unlock(10, 10, 30, 100), unlock(11, 10, 60, 100))
        val frame = JourneyFrame(
            mode = JourneyMode.SPAN,
            main = PlayerJourney("me", unlocks, mapOf(100 to game(100, 0)), emptyList()),
            rivals = emptyList(),
            spanFromMillis = 0, spanToMillis = 60 * day,
        )
        val layout = LayoutEngine.layout(frame, includeNodes = true)
        assertEquals("the fixture must have exactly one streak", 1, layout.streaks.size)
        val run = layout.streaks.first()

        // The x each progress reaches, so the assertions are about the rule rather than about a
        // guessed fraction of the timeline.
        fun progressAtX(x: Float): Float =
            ((x - layout.padL) / (layout.width - layout.padL - layout.padR)).coerceIn(0f, 1f)

        val inside = progressAtX((run.startX + run.endX) / 2f)
        val past = progressAtX(run.endX + (layout.width - run.endX) / 2f)

        assertTrue("inside the run the track burns", HudAtProgress.inStreak(layout, inside))
        assertTrue("past it, it does not", !HudAtProgress.inStreak(layout, past))
        val noRun = LayoutEngine.layout(frame(), includeNodes = true)
        assertTrue("a journey with no run never burns", noRun.streaks.isEmpty())
        assertTrue(!HudAtProgress.inStreak(noRun, 0.5f))
    }

    /** Four evenly spaced unlocks across two games, 10 points each. */
    private fun frame(
        mode: JourneyMode = JourneyMode.SPAN,
        rivalUnlocks: List<Unlock> = emptyList(),
    ): JourneyFrame {
        val unlocks = listOf(
            unlock(1, 10, 0, 100),
            unlock(2, 10, 10, 100),
            unlock(3, 10, 20, 200),
            unlock(4, 10, 30, 200),
        )
        return JourneyFrame(
            mode = mode,
            main = PlayerJourney(
                name = "tester", unlocks = unlocks,
                games = mapOf(100 to game(100, 0), 200 to game(200, 1)), medals = emptyList(),
            ),
            rivals = if (rivalUnlocks.isEmpty()) {
                emptyList()
            } else {
                listOf(
                    PlayerJourney(
                        name = "rival", unlocks = rivalUnlocks,
                        games = mapOf(100 to game(100, 0)), medals = emptyList(),
                    )
                )
            },
            spanFromMillis = 0L, spanToMillis = 30 * day,
        )
    }

    /**
     * The curve starts at the first unlock, so an unlock on the first day of the axis is already
     * under the sweep at progress 0. The chart draws that badge from frame one; the counter agrees
     * with it rather than with the intuition that zero means zero.
     */
    @Test fun `progress zero reveals only what sits on the start of the curve`() {
        val frame = frame()
        val layout = LayoutEngine.layout(frame, includeNodes = true)

        val stats = HudAtProgress.stats(frame, layout, 0f)

        assertEquals(1, stats.unlocked)
        assertEquals(10, stats.points)
        assertEquals(1, stats.games)
        assertEquals(0L, stats.journeyDateMillis)
    }

    /** Unlocks earned at the same moment share an x, so the sweep reaches all of them at once. */
    @Test fun `unlocks sharing a timestamp are revealed together`() {
        val unlocks = listOf(
            unlock(1, 10, 0, 100),
            unlock(2, 10, 10, 100),
            unlock(3, 10, 10, 100),
            unlock(4, 10, 10, 100),
            unlock(5, 10, 20, 100),
        )
        val frame = JourneyFrame(
            mode = JourneyMode.SPAN,
            main = PlayerJourney("tester", unlocks, mapOf(100 to game(100, 0)), emptyList()),
            rivals = emptyList(), spanFromMillis = 0L, spanToMillis = 20 * day,
        )
        val layout = LayoutEngine.layout(frame, includeNodes = true)

        // Nothing may stop inside the vertical stack: either the sweep has reached day 10 or it
        // has not, and the counter has to move by three when it does.
        val counts = (0..40).map { HudAtProgress.stats(frame, layout, it / 40f).unlocked }.distinct()
        assertEquals(listOf(1, 4, 5), counts)
    }

    @Test fun `progress one equals the frame's full totals`() {
        val frame = frame()
        val layout = LayoutEngine.layout(frame, includeNodes = true)

        val stats = HudAtProgress.stats(frame, layout, 1f)

        assertEquals(4, stats.unlocked)
        assertEquals(40, stats.points)
        assertEquals(2, stats.games)
        assertEquals(30 * day, stats.journeyDateMillis)
    }

    @Test fun `values above one are clamped to the totals`() {
        val frame = frame()
        val layout = LayoutEngine.layout(frame, includeNodes = true)

        assertEquals(HudAtProgress.stats(frame, layout, 1f), HudAtProgress.stats(frame, layout, 3f))
    }

    @Test fun `halfway through an even journey reveals about half of it`() {
        val frame = frame()
        val layout = LayoutEngine.layout(frame, includeNodes = true)

        val stats = HudAtProgress.stats(frame, layout, 0.5f)

        // Evenly spaced unlocks of equal value: the midpoint sits on the second of four.
        assertEquals(2, stats.unlocked)
        assertEquals(20, stats.points)
        assertEquals(1, stats.games)
        assertEquals(10 * day, stats.journeyDateMillis)
    }

    @Test fun `counters never go backwards as progress climbs`() {
        val frame = frame()
        val layout = LayoutEngine.layout(frame, includeNodes = true)

        var previous = HudAtProgress.stats(frame, layout, 0f)
        for (step in 1..20) {
            val next = HudAtProgress.stats(frame, layout, step / 20f)
            assertTrue(
                "unlocked dropped at ${step / 20f}: ${previous.unlocked} -> ${next.unlocked}",
                next.unlocked >= previous.unlocked,
            )
            assertTrue("points dropped at ${step / 20f}", next.points >= previous.points)
            previous = next
        }
        assertEquals(4, previous.unlocked)
    }

    @Test fun `compare mode counts against the shared sweep line`() {
        // A rival with data puts the chart in compare mode, where the sweep is linear in x.
        val frame = frame(rivalUnlocks = listOf(unlock(9, 10, 30, 100)))
        val layout = LayoutEngine.layout(frame, includeNodes = true)

        // The sweep starts at the left edge of the plot, which is where a first unlock on the very
        // first day of the axis already sits, so it counts from frame one, exactly as the chart
        // draws it.
        assertEquals(1, HudAtProgress.stats(frame, layout, 0f).unlocked)
        // The main player's unlocks sit at days 0, 10, 20 and 30 of a 30 day axis, so a sweep at
        // half the plot width has passed the ones at 0 and 10 and not the one at 20.
        assertEquals(2, HudAtProgress.stats(frame, layout, 0.5f).unlocked)
        assertEquals(4, HudAtProgress.stats(frame, layout, 1f).unlocked)
    }

    /**
     * The player's overview counts awards the same way the chart reveals them: on the medal's
     * anchor, which is where it was won, not on the relaxed circle the collision pass moved it to.
     */
    @Test fun `awards are counted as the sweep passes their anchors`() {
        val frame = frameWithMedals()
        val layout = LayoutEngine.layout(frame, includeNodes = true)
        assertEquals(2, layout.medals.size)

        assertEquals(0, HudAtProgress.awardsRevealed(layout, 0f))
        assertEquals(1, HudAtProgress.awardsRevealed(layout, 0.5f))
        assertEquals(2, HudAtProgress.awardsRevealed(layout, 1f))
    }

    @Test fun `the award count never goes backwards as progress climbs`() {
        val frame = frameWithMedals()
        val layout = LayoutEngine.layout(frame, includeNodes = true)

        var previous = 0
        for (step in 0..20) {
            val next = HudAtProgress.awardsRevealed(layout, step / 20f)
            assertTrue("awards dropped at ${step / 20f}: $previous -> $next", next >= previous)
            previous = next
        }
        assertEquals(2, previous)
    }

    @Test fun `a journey without awards counts none`() {
        val frame = frame()
        val layout = LayoutEngine.layout(frame, includeNodes = true)

        assertEquals(0, HudAtProgress.awardsRevealed(layout, 0.5f))
        assertEquals(0, HudAtProgress.awardsRevealed(layout, 1f))
    }

    /** Compare mode moves the sweep linearly across the plot, and the awards follow that line. */
    @Test fun `compare mode counts awards against the shared sweep line`() {
        val base = frameWithMedals()
        val frame = base.copy(
            rivals = listOf(
                PlayerJourney(
                    name = "rival", unlocks = listOf(unlock(9, 10, 30, 100)),
                    games = mapOf(100 to game(100, 0)), medals = emptyList(),
                ),
            ),
        )
        val layout = LayoutEngine.layout(frame, includeNodes = true)

        assertEquals(0, HudAtProgress.awardsRevealed(layout, 0f))
        assertEquals(1, HudAtProgress.awardsRevealed(layout, 0.5f))
        assertEquals(2, HudAtProgress.awardsRevealed(layout, 1f))
    }

    /**
     * A rival's award tally moves with the shared sweep exactly the way [HudAtProgress.rivalPoints]
     * and [HudAtProgress.rivalRevealed] already do - a running compare row's award dots have to
     * follow the curve rather than sit on the journey's final tally from frame one.
     */
    @Test fun `rival awards move with the shared sweep, one tally per rival`() {
        val games = mapOf(100 to game(100, 0))
        val main = PlayerJourney(
            "tester",
            listOf(unlock(1, 10, 0, 100), unlock(2, 10, 30, 100)),
            games,
            medals = listOf(Medal(AwardKind.MASTERED, 30 * day, 100, null, "g100")),
        )
        val rival = PlayerJourney(
            "rival",
            listOf(unlock(9, 10, 0, 100), unlock(10, 10, 30, 100)),
            games,
            medals = listOf(Medal(AwardKind.BEATEN, 10 * day, 100, "rival", "rival · g100")),
        )
        val frame = JourneyFrame(JourneyMode.SPAN, main, listOf(rival), 0L, 30 * day)
        val layout = LayoutEngine.layout(frame, includeNodes = true)

        assertEquals(listOf(AwardTally()), HudAtProgress.rivalAwardTally(frame, layout, 0f))
        // The rival's medal lands on day 10 of a 30 day axis, well before the main player's own on
        // day 30 - and the main player's own award must never leak into the rival's tally.
        assertEquals(listOf(AwardTally(beaten = 1)), HudAtProgress.rivalAwardTally(frame, layout, 0.5f))
        assertEquals(listOf(AwardTally(beaten = 1)), HudAtProgress.rivalAwardTally(frame, layout, 1f))
    }

    /** No rivals, no tallies - the empty list rather than a crash on an index that has nothing at it. */
    @Test fun `a solo journey's rival award tally is simply empty`() {
        val layout = LayoutEngine.layout(frame(), includeNodes = true)
        assertEquals(emptyList<AwardTally>(), HudAtProgress.rivalAwardTally(frame(), layout, 1f))
    }

    /**
     * The breakdown counts the same awards the total does, kind by kind: the strip draws three dot
     * counters now, and a breakdown that disagreed with the number beside it would be two answers
     * to one question. Gated on the same anchors, so this holds at every point of playback and not
     * only at the ends.
     */
    @Test fun `the award breakdown always adds up to the award count`() {
        val frame = frameWithMedals()
        val layout = LayoutEngine.layout(frame, includeNodes = true)

        for (step in 0..20) {
            val progress = step / 20f
            val tally = HudAtProgress.awardTally(layout, progress)
            assertEquals(
                "the breakdown must equal the count at $progress",
                HudAtProgress.awardsRevealed(layout, progress),
                tally.total,
            )
        }
        val end = HudAtProgress.awardTally(layout, 1f)
        assertEquals("one beaten award on this journey", 1, end.beaten)
        assertEquals("and one mastery", 1, end.mastered)
        assertEquals(0, end.completed)
    }

    /** A kind arrives when its own medal does, not when the first medal of any kind does. */
    @Test fun `each kind is revealed on its own anchor`() {
        val frame = frameWithMedals()
        val layout = LayoutEngine.layout(frame, includeNodes = true)

        assertEquals(AwardTally(), HudAtProgress.awardTally(layout, 0f))
        assertEquals(AwardTally(beaten = 1), HudAtProgress.awardTally(layout, 0.5f))
        assertEquals(AwardTally(mastered = 1, beaten = 1), HudAtProgress.awardTally(layout, 1f))
    }

    @Test fun `a journey without awards has an empty breakdown`() {
        val layout = LayoutEngine.layout(frame(), includeNodes = true)
        assertEquals(AwardTally(), HudAtProgress.awardTally(layout, 1f))
    }

    /**
     * The overview reads "your awards", so a rival's mastery of their own game does not belong in
     * it. The chart still draws that medal; only the count filters it out.
     */
    @Test fun `awards count the main player's own medals, not a rival's`() {
        val base = frameWithMedals()
        val frame = base.copy(
            rivals = listOf(
                PlayerJourney(
                    name = "rival", unlocks = listOf(unlock(9, 10, 30, 100)),
                    games = mapOf(100 to game(100, 0)),
                    medals = listOf(Medal(AwardKind.MASTERED, 5 * day, 100, "rival", "rival · g100")),
                ),
            ),
        )
        val layout = LayoutEngine.layout(frame, includeNodes = true)
        assertEquals("the chart still draws all three", 3, layout.medals.size)

        assertEquals(2, HudAtProgress.awardsRevealed(layout, 1f))
        assertEquals(3, HudAtProgress.awardsRevealed(layout, 1f, mainOnly = false))
        // The rival's medal lands on day 5, before either of the main player's.
        assertEquals(0, HudAtProgress.awardsRevealed(layout, 0.2f))
        assertEquals(1, HudAtProgress.awardsRevealed(layout, 0.2f, mainOnly = false))
    }

    /** The same four unlocks, plus a beaten award on day 10 and a mastered one on day 30. */
    private fun frameWithMedals(): JourneyFrame {
        val base = frame()
        return base.copy(
            main = base.main.copy(
                medals = listOf(
                    Medal(AwardKind.BEATEN, 10 * day, 100, null, "g100"),
                    Medal(AwardKind.MASTERED, 30 * day, 200, null, "g200"),
                ),
            ),
        )
    }

    /**
     * The counters are tabulated per (layout, frame) and then binary-searched, rather than
     * re-derived by a full scan on every call. This pins the two against each other: the naive
     * implementations below are literally what the file used to do, and every answer has to match
     * exactly - not approximately, since these numbers are read out to the user as counts.
     *
     * A long journey on purpose. The tables only pay for themselves at length, and the failure the
     * incremental version could plausibly have (an off-by-one at a segment boundary, or a wrong
     * answer when several unlocks share an x) needs the ties this fixture has.
     */
    @Test fun `the tabulated counters agree with a full rescan at every step`() {
        val unlocks = buildList {
            var id = 1
            for (d in 0 until 120) {
                // Some days carry several unlocks at the same moment, which is the case that shares
                // an x between points and used to hold the counter back through a vertical climb.
                repeat(if (d % 7 == 0) 3 else 1) {
                    add(unlock(id, 5 + (id % 11), d, 100 + (d % 5) * 100))
                    id++
                }
            }
        }
        val games = unlocks.map { it.gameId }.distinct().associateWith { game(it, it % 6) }
        val medals = listOf(
            Medal(AwardKind.BEATEN, 20 * day, 100, null, "g100"),
            Medal(AwardKind.COMPLETED, 60 * day, 300, null, "g300"),
            Medal(AwardKind.MASTERED, 110 * day, 500, null, "g500"),
        )
        val big = JourneyFrame(
            mode = JourneyMode.SPAN,
            main = PlayerJourney("tester", unlocks, games, medals),
            rivals = emptyList(), spanFromMillis = 0L, spanToMillis = 120 * day,
        )
        val layout = LayoutEngine.layout(big, includeNodes = true)

        for (step in 0..200) {
            val p = step / 200f
            assertEquals("revealedCount at $p", naiveRevealedCount(layout, p), HudAtProgress.revealedCount(layout, p))
            assertEquals("sweepX at $p", naiveSweepX(layout, p), HudAtProgress.sweepX(layout, p))
            assertEquals("awards at $p", naiveAwards(layout, p), HudAtProgress.awardsRevealed(layout, p))

            val stats = HudAtProgress.stats(big, layout, p)
            val revealed = stats.unlocked
            assertEquals("points at $p", unlocks.take(revealed).sumOf { it.points }, stats.points)
            assertEquals("games at $p", unlocks.take(revealed).map { it.gameId }.distinct().size, stats.games)
        }
    }

    /**
     * The same cross-check with a rival on the chart, which is a different code path end to end.
     *
     * Compare mode sweeps a shared vertical line across the plot area rather than revealing the
     * main curve by arc length, so `revealedCount` counts along the track's x values, `sweepX` is
     * linear, and `awardsRevealed(mainOnly = false)` counts a medal list the solo fixtures never
     * populate. None of that is touched by the solo property test above.
     */
    @Test fun `the tabulated counters agree with a full rescan on a compare chart`() {
        val mine = (0 until 90).map { unlock(it + 1, 4 + (it % 9), it, 100 + (it % 3) * 100) }
        // The rival keeps unlocking after the main player stops, which is what makes the shared
        // sweep run past the end of the main curve.
        val theirs = (0 until 110).map { unlock(1000 + it, 6, it, 100) }
        val games = mine.map { it.gameId }.distinct().associateWith { game(it, it % 6) }
        val compare = JourneyFrame(
            mode = JourneyMode.SPAN,
            main = PlayerJourney(
                "tester", mine, games,
                listOf(Medal(AwardKind.BEATEN, 30 * day, 100, null, "g100")),
            ),
            rivals = listOf(
                PlayerJourney(
                    "rival", theirs, mapOf(100 to game(100, 1)),
                    listOf(Medal(AwardKind.MASTERED, 70 * day, 100, "rival", "rival · g100")),
                ),
            ),
            spanFromMillis = 0L, spanToMillis = 110 * day,
        )
        val layout = LayoutEngine.layout(compare, includeNodes = true)
        assertTrue("the fixture has to actually be a compare chart", isCompare(layout))

        for (step in 0..200) {
            val p = step / 200f
            assertEquals("revealedCount at $p", naiveRevealedCount(layout, p), HudAtProgress.revealedCount(layout, p))
            assertEquals("sweepX at $p", naiveSweepX(layout, p), HudAtProgress.sweepX(layout, p))
            assertEquals("own awards at $p", naiveAwards(layout, p), HudAtProgress.awardsRevealed(layout, p))
            assertEquals(
                "every player's awards at $p",
                naiveAwards(layout, p, mainOnly = false),
                HudAtProgress.awardsRevealed(layout, p, mainOnly = false),
            )

            val stats = HudAtProgress.stats(compare, layout, p)
            val revealed = stats.unlocked
            assertEquals("points at $p", mine.take(revealed).sumOf { it.points }, stats.points)
            assertEquals("games at $p", mine.take(revealed).map { it.gameId }.distinct().size, stats.games)
        }
    }

    /**
     * Two layouts read alternately, which is what the player does every time it swaps between the
     * wide timeline and the compact chart. A single-slot table cache must notice the swap rather
     * than answer the second layout's question out of the first one's arrays.
     */
    @Test fun `alternating between two layouts does not answer from the wrong table`() {
        val f = frame()
        val wide = LayoutEngine.layout(f, includeNodes = true, widthFactor = 3f)
        val compact = LayoutEngine.layout(f, includeNodes = true)

        repeat(4) {
            for (p in listOf(0f, 0.25f, 0.5f, 0.75f)) {
                assertEquals(naiveRevealedCount(wide, p), HudAtProgress.revealedCount(wide, p))
                assertEquals(naiveRevealedCount(compact, p), HudAtProgress.revealedCount(compact, p))
                assertEquals(naiveSweepX(wide, p), HudAtProgress.sweepX(wide, p))
                assertEquals(naiveSweepX(compact, p), HudAtProgress.sweepX(compact, p))
            }
        }
    }

    // ---- the pre-tabulation implementations, kept as the reference the fast path must match ----

    private val tolerance = com.twinlore.app.journey.ui.chart.ChartConstants.REVEAL_TOLERANCE

    private fun isCompare(layout: com.twinlore.app.journey.domain.ChartLayout): Boolean =
        layout.rivals.any { it.bgTrack.isNotEmpty() }

    private fun naiveRevealedCount(layout: com.twinlore.app.journey.domain.ChartLayout, progress: Float): Int {
        val track = layout.main.bgTrack
        if (track.isEmpty()) return 0
        val p = progress.coerceIn(0f, 1f)
        if (isCompare(layout)) {
            val sweepX = layout.padL + p * (layout.width - layout.padL - layout.padR)
            return track.count { (x, _) -> x <= sweepX + tolerance }
        }
        val points = layout.main.points
        if (points.size < 2) return 0
        val lengths = FloatArray(points.size)
        for (i in 1 until points.size) {
            lengths[i] = lengths[i - 1] +
                kotlin.math.hypot(points[i].x - points[i - 1].x, points[i].y - points[i - 1].y)
        }
        val total = lengths.last()
        if (total <= 0f) return if (p > 0f) track.size else 0
        val revealed = p * total
        var count = 0
        var index = 0
        for ((x, _) in track) {
            while (index < points.size - 1 && points[index].x < x) index++
            if (lengths[index] <= revealed + tolerance) count++ else break
        }
        return count
    }

    private fun naiveSweepX(layout: com.twinlore.app.journey.domain.ChartLayout, progress: Float): Float {
        val p = progress.coerceIn(0f, 1f)
        if (isCompare(layout)) return layout.padL + p * (layout.width - layout.padL - layout.padR)
        val points = layout.main.points
        if (points.isEmpty()) return layout.padL
        if (points.size == 1) return points[0].x
        var total = 0f
        for (i in 1 until points.size) {
            total += kotlin.math.hypot(points[i].x - points[i - 1].x, points[i].y - points[i - 1].y)
        }
        if (total <= 0f) return points.last().x
        var walked = 0f
        val target = p * total
        for (i in 1 until points.size) {
            val segment = kotlin.math.hypot(points[i].x - points[i - 1].x, points[i].y - points[i - 1].y)
            if (walked + segment >= target) {
                val t = if (segment <= 0f) 0f else (target - walked) / segment
                return points[i - 1].x + (points[i].x - points[i - 1].x) * t
            }
            walked += segment
        }
        return points.last().x
    }

    private fun naiveAwards(
        layout: com.twinlore.app.journey.domain.ChartLayout,
        progress: Float,
        mainOnly: Boolean = true,
    ): Int {
        if (layout.medals.isEmpty()) return 0
        val counted = if (mainOnly) layout.medals.filter { it.medal.ownerName == null } else layout.medals
        if (counted.isEmpty()) return 0
        val p = progress.coerceIn(0f, 1f)
        if (p >= 1f) return counted.size
        val sweep = naiveSweepX(layout, p)
        return counted.count { it.anchorX <= sweep + tolerance }
    }

    @Test fun `a journey with no unlocks stays empty`() {
        val empty = JourneyFrame(
            mode = JourneyMode.SPAN,
            main = PlayerJourney("tester", emptyList(), emptyMap(), emptyList()),
            rivals = emptyList(), spanFromMillis = 0L, spanToMillis = day,
        )
        val layout = LayoutEngine.layout(empty, includeNodes = true)

        assertEquals(0, HudAtProgress.stats(empty, layout, 0.5f).unlocked)
        assertEquals(0, HudAtProgress.stats(empty, layout, 1f).unlocked)
    }

    // ---- the date under the runner ----

    /**
     * UI feedback: the header's date used to be the timestamp of the last unlock the sweep had
     * passed, while the eye follows the runner's icon along the curve - on a chart whose unlocks
     * cluster, the two disagreed by weeks. The axis date is the time under the runner's x.
     */
    @Test fun `the axis date tracks the runner, not the last unlock behind it`() {
        // Two unlocks in the first two days, then one two months later: for most of the
        // playback the runner is crossing the long empty middle.
        val unlocks = listOf(unlock(1, 10, 0, 100), unlock(2, 10, 1, 100), unlock(3, 10, 59, 100))
        val frame = JourneyFrame(
            mode = JourneyMode.SPAN,
            main = PlayerJourney("me", unlocks, mapOf(100 to game(100, 0)), emptyList()),
            rivals = emptyList(), spanFromMillis = 0, spanToMillis = 59 * day,
        )
        val layout = LayoutEngine.layout(frame, includeNodes = true)

        val halfway = HudAtProgress.axisDateMillis(layout, 0.5f)!!
        val statsDate = HudAtProgress.stats(frame, layout, 0.5f).journeyDateMillis!!

        assertEquals("the old readout sits on the last unlock passed", 1 * day, statsDate)
        assertTrue(
            "the runner is well into the empty middle (was at day ${halfway / day})",
            halfway > 20 * day,
        )
    }

    /**
     * THE INVARIANT a race chart's rows are cut by: a player's row agrees with their own curve at
     * every point of playback. If their dot is on the chart, their row counts it; if it is not, it
     * does not.
     *
     * The curve's side of that is [HudAtProgress.rivalRevealed] - the gate the renderer draws a
     * rival's dots by, the shared sweep against that rival's own x. The row's side is
     * [frameUpToElapsed], which cuts each player by their own elapsed time at
     * [HudAtProgress.sweepElapsedMillis]. The two are the same statement in different units, and
     * this is what says so - over a fixture whose three players ran at the game YEARS apart, so an
     * agreement here cannot be an accident of everybody sharing a calendar week.
     */
    @Test fun `a race chart's cut keeps exactly the unlocks its curves have drawn`() {
        val year = 365L
        val games = mapOf(100 to game(100, 0))
        val main = PlayerJourney("me", listOf(unlock(1, 10, 400, 100), unlock(2, 20, 402, 100)), games, emptyList())
        val before = PlayerJourney(
            "before",
            listOf(unlock(3, 5, (400 - year).toInt(), 100), unlock(4, 15, (401 - year).toInt(), 100)),
            games, emptyList(),
        )
        val after = PlayerJourney(
            "after",
            listOf(unlock(5, 7, (400 + year).toInt(), 100), unlock(6, 21, (403 + year).toInt(), 100)),
            games, emptyList(),
        )
        val frame = JourneyFrame(JourneyMode.GAME, main, listOf(before, after), 0, 0)
        val layout = LayoutEngine.layout(frame, includeNodes = false)
        assertTrue("the fixture has to actually be a race, or it proves nothing", layout.raceMode)

        (0..40).forEach { step ->
            val p = step / 40f
            val cut = frameUpToElapsed(frame, HudAtProgress.sweepElapsedMillis(layout, p))
            val drawn = HudAtProgress.rivalRevealed(frame, layout, p)

            assertEquals(
                "the main player's row and their own curve at progress $p",
                HudAtProgress.revealedCount(layout, p),
                cut.main.unlocks.size,
            )
            frame.rivals.indices.forEach { r ->
                assertEquals(
                    "${frame.rivals[r].name}'s row and their own curve at progress $p",
                    drawn[r],
                    cut.rivals[r].unlocks.size,
                )
            }
            assertTrue(
                "and nobody is dropped off the chart at progress $p",
                cut.rivals.all { it.unlocks.isNotEmpty() },
            )
        }
    }

    @Test fun `the axis date spans first to last unlock across playback`() {
        val unlocks = (0 until 4).map { unlock(it + 1, 10, it * 20, 100) }
        val frame = JourneyFrame(
            mode = JourneyMode.SPAN,
            main = PlayerJourney("me", unlocks, mapOf(100 to game(100, 0)), emptyList()),
            rivals = emptyList(), spanFromMillis = 0, spanToMillis = 60 * day,
        )
        val layout = LayoutEngine.layout(frame, includeNodes = true)

        val start = HudAtProgress.axisDateMillis(layout, 0f)!!
        val end = HudAtProgress.axisDateMillis(layout, 1f)!!
        val mid = HudAtProgress.axisDateMillis(layout, 0.5f)!!

        // The curve runs from the first unlock to the last, so the readout does too - within a
        // day of slack for the smoothing and the node radii at either end.
        assertTrue("starts at the first unlock (day ${start / day})", start < day)
        assertTrue("ends at the last unlock (day ${end / day})", end > 58 * day)
        assertTrue("and never runs backwards", mid in start..end)
    }
}
