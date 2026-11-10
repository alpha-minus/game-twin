package com.twinlore.app.journey.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which sign a flat stretch of line gets, and - just as importantly - that it holds still.
 *
 * The sign is decoration, but a decoration drawn every frame has to be a pure function of the gap
 * it belongs to: one that re-rolled per frame would flicker over the player's face, and a shared
 * video has to show whoever watches it exactly what the watcher saw.
 */
class IdleSignsTest {

    private val hour = 3_600_000L
    private val day = 24 * hour

    @Test fun `a short pause is not a story`() {
        assertNull(IdleSigns.signFor(0L, seed = 1))
        assertNull(IdleSigns.signFor(2 * hour, seed = 1))
        assertNull(IdleSigns.signFor(IdleSigns.minimumGapMillis - 1, seed = 1))
        assertNotNull(IdleSigns.signFor(IdleSigns.minimumGapMillis, seed = 1))
    }

    /**
     * The floor moved from six hours to three: an evening's errands now clear it, a mid-afternoon
     * hour-long break still does not.
     */
    @Test fun `a three-hour gap gets a sign, a one-hour gap does not`() {
        assertNull(IdleSigns.signFor(hour, seed = 1))
        assertNotNull(IdleSigns.signFor(3 * hour, seed = 1))
    }

    /**
     * Three hours is an evening's errands, not a night's sleep: it must land in the same pool as
     * an eight-hour gap (CHORES), never in DAILY's "they went to bed" - so lowering the floor must
     * not have also lowered the CHORES/DAILY boundary, which stays pinned to a full day.
     */
    @Test fun `three hours is chores, not a night's sleep`() {
        val choresPool = (0 until 40).map { IdleSigns.signFor(8 * hour, seed = it) }.toSet()
        val threeHourSigns = (0 until 40).map { IdleSigns.signFor(3 * hour, seed = it) }

        assertTrue("three hours must actually get a sign", threeHourSigns.none { it == null })
        assertTrue(
            "a three-hour gap landed outside the CHORES pool: $threeHourSigns vs $choresPool",
            choresPool.containsAll(threeHourSigns.filterNotNull()),
        )
    }

    /** The three bands: an evening's errands, an ordinary day, and a stretch away from it all. */
    @Test fun `the sign follows how long the gap is`() {
        val chores = (0 until 40).map { IdleSigns.signFor(8 * hour, seed = it) }.toSet()
        val daily = (0 until 40).map { IdleSigns.signFor(2 * day, seed = it) }.toSet()
        val away = (0 until 40).map { IdleSigns.signFor(30 * day, seed = it) }.toSet()

        listOf(chores, daily, away).forEach { band ->
            assertTrue("a band must offer some variety, got $band", band.size > 1)
            assertTrue("no band may answer null in its own range", band.none { it == null })
        }
        assertTrue("an evening off is not a holiday: $chores vs $away", (chores intersect away).isEmpty())
        assertTrue("a night in is not a holiday: $daily vs $away", (daily intersect away).isEmpty())
    }

    /**
     * The same gap answers the same sign every time it is asked. This is what keeps the picture
     * still while the sweep crosses a gap - it is asked once per frame - and what makes an export
     * of a journey identical to the replay it was taken from.
     */
    @Test fun `one gap keeps one sign`() {
        repeat(50) { seed ->
            val first = IdleSigns.signFor(3 * day, seed)
            repeat(5) { assertEquals(first, IdleSigns.signFor(3 * day, seed)) }
        }
    }

    /** And neighbouring gaps do not walk the list in order, which would read as a cartoon strip. */
    @Test fun `consecutive gaps do not march through the list`() {
        val walk = (0 until 8).map { IdleSigns.signFor(2 * day, seed = it) }
        assertTrue("neighbouring gaps repeated a pattern: $walk", walk.toSet().size > 2)
    }

    /**
     * A long wait tells a small story rather than holding one picture: as the sweep travels
     * through the gap the sign moves on to the next in the SAME tier - they slept, then they went
     * to work - so a night out can never become a holiday halfway through the night.
     */
    @Test fun `a long wait moves on to the next sign in its own tier`() {
        val gap = 2 * day
        val steps = (0 until 4).map { IdleSigns.signFor(gap, seed = 7, step = it) }
        assertEquals("every step still answers", 4, steps.filterNotNull().size)
        assertTrue("the sign must change as the wait goes on: $steps", steps.toSet().size > 1)

        // Same tier throughout: every sign a 2-day gap shows is one a 2-day gap can show.
        val tier = (0 until 60).mapNotNull { IdleSigns.signFor(gap, seed = it, step = 0) }.toSet()
        assertTrue("a step wandered out of its tier: $steps vs $tier", tier.containsAll(steps.filterNotNull()))

        // And a step is still deterministic: the same place in the same gap is the same sign.
        assertEquals(IdleSigns.signFor(gap, seed = 7, step = 2), IdleSigns.signFor(gap, seed = 7, step = 2))
    }
}
