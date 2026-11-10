package com.twinlore.app.journey.ui.chart

import com.twinlore.app.journey.domain.ChartLayout
import com.twinlore.app.journey.domain.IdleSigns
import com.twinlore.app.journey.domain.LayoutEngine
import com.twinlore.app.journey.domain.model.AchievementType
import com.twinlore.app.journey.domain.model.GameRef
import com.twinlore.app.journey.domain.model.JourneyFrame
import com.twinlore.app.journey.domain.model.JourneyMode
import com.twinlore.app.journey.domain.model.PlayerJourney
import com.twinlore.app.journey.domain.model.Unlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [JourneyChartRenderer.idleSignAt] is asked once per player per frame (see `drawRivalMarkers`,
 * `drawRunner`) with that player's own [com.twinlore.app.journey.domain.CurveLayout] - the same
 * function either way, nothing in it branching on whose curve was passed. This is what pins down
 * that the "main player only" gap reported on device is not something the function does: a rival's
 * curve earns a sign under exactly the rule the main player's does, a rival too short or too dense
 * to earn one is refused it exactly as a short solo journey is, and one player's gap has no bearing
 * on another's answer.
 *
 * No `DrawScope` involved (the placement math doesn't need one), so no Robolectric here - unlike
 * `JourneyChartRendererTest`, which draws.
 */
class JourneyChartIdleSignsTest {

    private val day = 86_400_000L
    private val t0 = 1_760_000_000_000L

    private fun unlock(id: Int, at: Long, pts: Int, gameId: Int = 1) = Unlock(
        id = id, title = "A$id", description = "d$id", points = pts, badgeName = "b$id",
        type = AchievementType.STANDARD, earnedAtMillis = at, numAwarded = 10, gameId = gameId,
    )

    private fun player(name: String, unlocks: List<Unlock>) = PlayerJourney(
        name = name,
        unlocks = unlocks.sortedBy { it.earnedAtMillis },
        games = unlocks.map { it.gameId }.distinct().associateWith {
            GameRef(it, "G$it", null, emptyList(), colorIndex = it, players = 100, award = null)
        },
        medals = emptyList(),
    )

    /**
     * A wide gap for each of the main player and one rival, deliberately placed apart in time so a
     * test can stand in the middle of either one without landing in the other's window:
     *
     * - main: two close unlocks, then a 40-day silence.
     * - rival: one early unlock, then an 85-day silence running to the end of the whole span.
     *
     * (There is no separate `spanFromMillis`/`spanToMillis` window here: `LayoutEngine` only reads
     * those when `spanFromMillis > 0`, which this fixture leaves at 0, so the axis is simply the
     * earliest-to-latest unlock across both players - the same rule `compareLayout()` in
     * `JourneyChartRendererTest` relies on.)
     */
    private fun twoGapLayout(): ChartLayout {
        val main = player(
            "me",
            listOf(unlock(1, t0, 10), unlock(2, t0 + 2 * day, 20), unlock(3, t0 + 42 * day, 30)),
        )
        val rival = player(
            "rival",
            listOf(unlock(11, t0 + 5 * day, 5), unlock(12, t0 + 90 * day, 25)),
        )
        return LayoutEngine.layout(
            JourneyFrame(
                mode = JourneyMode.SPAN, main = main, rivals = listOf(rival),
                spanFromMillis = 0, spanToMillis = 0,
            ),
            includeNodes = false,
        )
    }

    /** The x midway through a curve's own gap - deep enough inside it to clear the edge margin. */
    private fun midOfGap(from: Float, to: Float): Float = (from + to) / 2f

    @Test fun `a rival's own quiet stretch earns a sign, the same as the main player's`() {
        val layout = twoGapLayout()
        val main = layout.main
        val rival = layout.rivals.single()

        // main's gap: unlock 2 (t0+2d) to unlock 3 (t0+42d) - bgTrack index 1 to 2.
        val mainGapMid = midOfGap(main.bgTrack[1].first, main.bgTrack[2].first)
        assertNotNull(
            "the main player's 40-day gap must still earn a sign",
            JourneyChartRenderer.idleSignAt(layout, main, mainGapMid),
        )

        // rival's gap: unlock 11 (t0+5d) to unlock 12 (t0+90d) - bgTrack index 0 to 1.
        val rivalGapMid = midOfGap(rival.bgTrack[0].first, rival.bgTrack[1].first)
        assertNotNull(
            "a rival's 85-day gap must earn a sign exactly as a solo one would",
            JourneyChartRenderer.idleSignAt(layout, rival, rivalGapMid),
        )
    }

    @Test fun `one player's gap says nothing about another player's sweep position`() {
        val layout = twoGapLayout()
        val main = layout.main
        val rival = layout.rivals.single()

        // The main player is mid-unlock (not idle) while the rival is deep in their own silence -
        // the rival's sign must not leak onto the main curve asked at the same x.
        val rivalGapMid = midOfGap(rival.bgTrack[0].first, rival.bgTrack[1].first)
        assertNull(
            "the main curve has no gap under the rival's sweep position",
            JourneyChartRenderer.idleSignAt(layout, main, rivalGapMid),
        )
    }

    @Test fun `a rival with only one unlock never gets a sign - no gap to measure`() {
        val main = player("me", listOf(unlock(1, t0, 10), unlock(2, t0 + 40 * day, 20)))
        val rival = player("rival", listOf(unlock(11, t0 + 10 * day, 5)))
        val layout = LayoutEngine.layout(
            JourneyFrame(
                mode = JourneyMode.SPAN, main = main, rivals = listOf(rival),
                spanFromMillis = 0, spanToMillis = 0,
            ),
            includeNodes = false,
        )
        val rivalCurve = layout.rivals.single()

        // Every x on the plot, not just one: a single-point track has no "before the last unlock"
        // window at all.
        val left = layout.padL
        val right = layout.width - layout.padR
        var x = left
        while (x <= right) {
            assertNull(
                "a one-unlock rival has no gap at x=$x",
                JourneyChartRenderer.idleSignAt(layout, rivalCurve, x),
            )
            x += (right - left) / 20f
        }
    }

    @Test fun `a rival's short pause gets no sign, the same floor the main player's gaps clear`() {
        // Both players pause for 2 hours - under IdleSigns.minimumGapMillis (3h) - and the whole
        // axis is only 12 hours wide, so in x terms the gap is a healthy 150+ units (well past
        // IDLE_GAP_X's 46-unit floor): what refuses the sign has to be the TIME floor, not width.
        val twoHours = 2 * 3_600_000L
        val main = player("me", listOf(unlock(1, t0, 10), unlock(2, t0 + twoHours, 20)))
        val rival = player("rival", listOf(unlock(11, t0 + 10 * 3_600_000L, 5), unlock(12, t0 + 12 * 3_600_000L, 15)))
        val layout = LayoutEngine.layout(
            JourneyFrame(
                mode = JourneyMode.SPAN, main = main, rivals = listOf(rival),
                spanFromMillis = 0, spanToMillis = 0,
            ),
            includeNodes = false,
        )
        val rivalCurve = layout.rivals.single()

        val mainGapMid = midOfGap(layout.main.bgTrack[0].first, layout.main.bgTrack[1].first)
        assertNull(
            "a 2-hour main-player pause is below the sign floor",
            JourneyChartRenderer.idleSignAt(layout, layout.main, mainGapMid),
        )
        val rivalGapMid = midOfGap(rivalCurve.bgTrack[0].first, rivalCurve.bgTrack[1].first)
        assertNull(
            "a rival's 2-hour pause is refused a sign by the same floor",
            JourneyChartRenderer.idleSignAt(layout, rivalCurve, rivalGapMid),
        )
    }

    @Test fun `two players' signs are drawn from independent gap seeds`() {
        // Same gap length on both curves at different bgTrack positions: if the seed were shared
        // (or derived from something global like the sweep position alone) both would always agree.
        val gapDays = 20L
        val main = player(
            "me",
            listOf(
                unlock(1, t0, 10),
                unlock(2, t0 + 1 * day, 20),
                unlock(3, t0 + (1 + gapDays) * day, 30),
                unlock(4, t0 + 95 * day, 40),
            ),
        )
        val rival = player(
            "rival",
            listOf(unlock(11, t0 + 40 * day, 5), unlock(12, t0 + (40 + gapDays) * day, 15)),
        )
        val layout = LayoutEngine.layout(
            JourneyFrame(
                mode = JourneyMode.SPAN, main = main, rivals = listOf(rival),
                spanFromMillis = 0, spanToMillis = 0,
            ),
            includeNodes = false,
        )
        val rivalCurve = layout.rivals.single()

        // Same gap length (20 days -> the same AWAY_FROM_HOME tier), same step (both gaps are the
        // same x-width on this linear axis, so both land in switch-window 0), different bgTrack
        // index (1 for the main gap, 0 for the rival's) - the only thing left to produce different
        // glyphs is the seed being the CURVE's own index rather than something shared across
        // players. If the seed were shared (or the picks were forced to agree) these would collide.
        val mainGapMid = midOfGap(layout.main.bgTrack[1].first, layout.main.bgTrack[2].first)
        val rivalGapMid = midOfGap(rivalCurve.bgTrack[0].first, rivalCurve.bgTrack[1].first)
        val mainSign = JourneyChartRenderer.idleSignAt(layout, layout.main, mainGapMid)
        val rivalSign = JourneyChartRenderer.idleSignAt(layout, rivalCurve, rivalGapMid)
        assertNotNull(mainSign)
        assertNotNull(rivalSign)
        assertNotEquals(
            "two curves at different bgTrack indices must not be forced into lockstep",
            mainSign,
            rivalSign,
        )
    }

    /**
     * A single-game chart with a rival on it ([JourneyMode.GAME] plus a rival with unlocks) is
     * "race mode" in [ChartLayout.raceMode] - its x axis is each player's own elapsed time from
     * their own first unlock, not calendar time. `idleSignAt` used to bail on this unconditionally
     * (`if (layout.raceMode) return null`), which is the device bug this pins: on a one-game
     * compare chart nobody - not the rival, not the main player - ever got a "life happens" sign,
     * settings and months-long flat stretches notwithstanding. Same fixture shape as
     * [twoGapLayout], only [JourneyMode.GAME] instead of [JourneyMode.SPAN].
     */
    private fun raceLayout(): ChartLayout {
        val main = player(
            "me",
            listOf(unlock(1, t0, 10), unlock(2, t0 + 2 * day, 20), unlock(3, t0 + 42 * day, 30)),
        )
        val rival = player(
            "rival",
            listOf(unlock(11, t0 + 5 * day, 5), unlock(12, t0 + 90 * day, 25)),
        )
        return LayoutEngine.layout(
            JourneyFrame(
                mode = JourneyMode.GAME, main = main, rivals = listOf(rival),
                spanFromMillis = 0, spanToMillis = 0,
            ),
            includeNodes = false,
        )
    }

    @Test fun `a race chart still earns a sign for the main player's quiet stretch`() {
        val layout = raceLayout()
        assertTrue("fixture must actually be race mode", layout.raceMode)
        val main = layout.main
        val mainGapMid = midOfGap(main.bgTrack[1].first, main.bgTrack[2].first)
        assertNotNull(
            "a race chart must not blanket-refuse the main player a sign",
            JourneyChartRenderer.idleSignAt(layout, main, mainGapMid),
        )
    }

    @Test fun `a race chart still earns a sign for a rival's quiet stretch`() {
        val layout = raceLayout()
        assertTrue("fixture must actually be race mode", layout.raceMode)
        val rival = layout.rivals.single()
        val rivalGapMid = midOfGap(rival.bgTrack[0].first, rival.bgTrack[1].first)
        assertNotNull(
            "a race chart must not blanket-refuse a rival a sign either",
            JourneyChartRenderer.idleSignAt(layout, rival, rivalGapMid),
        )
    }

    /**
     * Pins the actual bug, not just its symptom: it is not enough for a race chart to answer
     * non-null, the gap's duration has to be read off the axis the chart actually draws (elapsed
     * time from the player's own first unlock, [ChartLayout.eMaxMillis]) rather than the calendar
     * span ([ChartLayout.axisT0]/[ChartLayout.axisT1]), which on a race chart is a leftover from
     * calendar mode and means something different.
     *
     * The fixture makes the two axis lengths disagree by nearly 100x on purpose, so a gap that is
     * genuinely a 12-hour pause (real elapsed time between the main player's two unlocks) would
     * read as a multi-day absence if the code mistakenly divided by the calendar span instead:
     *
     * - rival: two unlocks 5 days apart -> [LayoutEngine]'s eMax (the race axis length) is 5 days,
     *   since it is the largest of any single player's own (last - first).
     * - main: two unlocks 12 hours apart, but placed 45 days before the rival's window opens, so
     *   the CALENDAR range across every unlock on the chart (what `axisT0`/`axisT1` still measure
     *   in race mode) balloons to 50 days without moving the main player's own elapsed range at
     *   all - elapsed time only cares about a player's own first unlock, never anyone else's.
     *
     * Read against the correct axis (eMax = 5 days), the main player's gap is exactly what it
     * looks like: 12 hours, [IdleSigns]'s CHORES tier (>= the 3-hour floor, < the 1-day ceiling).
     * Read against the calendar span (50 days) by mistake, the same x-width gap inflates to 5 days,
     * crossing IdleSigns' 4-day AWAY tier - a different tier, so a different pool of glyphs, so
     * (bar an astronomically unlikely pool collision, ruled out below) a different sign. Asserting
     * equality against the correctly-computed sign - and inequality against the wrongly-computed
     * one - is what a bare `assertNotNull` cannot tell apart: this fails today not because the
     * function returns null, but because if the guard were merely deleted without fixing the axis,
     * it would return the WRONG sign.
     */
    @Test fun `a race chart measures the gap against its own elapsed axis, not the calendar span`() {
        val hour = 3_600_000L
        val rival = player("rival", listOf(unlock(11, t0, 5), unlock(12, t0 + 5 * day, 25)))
        val mainStart = t0 - 45 * day
        val main = player("me", listOf(unlock(1, mainStart, 10), unlock(2, mainStart + 12 * hour, 20)))
        val layout = LayoutEngine.layout(
            JourneyFrame(
                mode = JourneyMode.GAME, main = main, rivals = listOf(rival),
                spanFromMillis = 0, spanToMillis = 0,
            ),
            includeNodes = false,
        )
        assertTrue("fixture must actually be race mode", layout.raceMode)

        // eMax is 5 days (the rival's own span; the main player's 12-hour span loses), but the
        // calendar range across both players is 50 days (main's window opens 45 days before the
        // rival's and the rival's own span adds another 5) - the two axis lengths this test needs
        // to disagree.
        assertEquals(5 * day, layout.eMaxMillis)
        assertEquals(50 * day, layout.axisT1 - layout.axisT0)

        val correctGapMillis = 12 * hour
        val wrongGapMillis = 12 * hour * (50 * day) / (5 * day) // what the calendar span would give: 5 days
        assertEquals(5 * day, wrongGapMillis)

        // bgTrack index 0 is the only gap on this 2-unlock curve, and the gap's x-width (well
        // under IDLE_SIGN_SWITCH_X) keeps the switch-window step at 0 - both are exactly what
        // idleSignAt itself would use as (seed, step), so this reproduces its call to
        // IdleSigns.signFor directly rather than guessing at the tier from outside.
        val correctSign = IdleSigns.signFor(correctGapMillis, seed = 0, step = 0)
        val wrongSign = IdleSigns.signFor(wrongGapMillis, seed = 0, step = 0)
        assertNotNull("fixture sanity: the correct tier must actually award a sign", correctSign)
        assertNotEquals(
            "fixture sanity: the two axis lengths must select genuinely different tiers, or this " +
                "test cannot tell a fixed axis from a still-broken one",
            correctSign,
            wrongSign,
        )

        val mainGapMid = midOfGap(layout.main.bgTrack[0].first, layout.main.bgTrack[1].first)
        val actual = JourneyChartRenderer.idleSignAt(layout, layout.main, mainGapMid)
        assertEquals(
            "a 12-hour pause must read as 12 hours, not as the 50-day calendar range it is not",
            correctSign,
            actual,
        )
    }

    @Test fun `calendar mode is unaffected by the race-axis fix - the gap still reads off axisT0-axisT1`() {
        val layout = twoGapLayout()
        assertTrue("fixture must actually be calendar mode", !layout.raceMode)
        val main = layout.main

        // main's gap (bgTrack index 1 to 2, see twoGapLayout) is exactly the 40-day silence the
        // fixture's own comment describes, and the axis is the same span the whole chart uses -
        // calendar mode has only ever had one axis, so this is the same computation the passing
        // tests above already exercise; spelling it out here is what proves the race-mode branch
        // added by this fix left it alone rather than changing which value calendar mode reads.
        val from = main.bgTrack[1].first
        val to = main.bgTrack[2].first
        val calendarSpan = layout.axisT1 - layout.axisT0
        val innerW = layout.width - layout.padL - layout.padR
        val mainGapMid = midOfGap(from, to)
        val expectedGapMillis = ((to - from) / innerW * calendarSpan.toFloat()).toLong()
        // IDLE_SIGN_SWITCH_X (110f, private to the renderer): this gap is wide on the compare
        // chart's shared 90-day axis (rival unlocks push the axis well past main's own 40 days),
        // so unlike the tighter fixtures elsewhere in this file the sweep has already crossed a
        // switch window by the middle of it - reproducing that here, rather than assuming step 0,
        // is what this test needs the calendar-mode step to genuinely match production's.
        val expectedStep = ((mainGapMid - from) / 110f).toInt()
        val expected = IdleSigns.signFor(expectedGapMillis, seed = 1, step = expectedStep)
        assertEquals(expected, JourneyChartRenderer.idleSignAt(layout, main, mainGapMid))
    }
}
