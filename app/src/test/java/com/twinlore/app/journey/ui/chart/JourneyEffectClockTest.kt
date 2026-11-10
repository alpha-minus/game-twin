package com.twinlore.app.journey.ui.chart

import com.twinlore.app.journey.domain.model.AwardKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JourneyEffectClockTest {

    private val second = 1_000_000_000L
    private val t0 = 5_000_000_000L

    @Test fun `an unarmed icon has no effect and costs no frames`() {
        val clock = JourneyEffectClock()
        assertEquals(-1f, clock.nodeSeconds(0, t0, arm = false), 0f)
        assertEquals(-1f, clock.medalSeconds(0, t0, arm = false), 0f)
        assertFalse(clock.pendingAt(t0))
    }

    @Test fun `arming records the reveal instant and elapsed runs from it`() {
        val clock = JourneyEffectClock()
        assertEquals(0f, clock.nodeSeconds(3, t0, arm = true), 0.0001f)
        assertEquals(0.5f, clock.nodeSeconds(3, t0 + second / 2, arm = true), 0.0001f)
        // A second icon keeps its own clock.
        assertEquals(0f, clock.nodeSeconds(4, t0 + second, arm = true), 0.0001f)
        assertEquals(1f, clock.nodeSeconds(3, t0 + second, arm = true), 0.0001f)
    }

    /** The tail: once armed, an effect keeps running even after playback stops arming new ones. */
    @Test fun `an armed icon keeps ticking once arming stops`() {
        val clock = JourneyEffectClock()
        clock.nodeSeconds(1, t0, arm = true)
        assertEquals(0.25f, clock.nodeSeconds(1, t0 + second / 4, arm = false), 0.0001f)
        assertEquals(-1f, clock.nodeSeconds(2, t0 + second / 4, arm = false), 0f)
    }

    @Test fun `pending covers the longest effect and then stops`() {
        val clock = JourneyEffectClock()
        clock.medalSeconds(0, t0, arm = true)
        assertTrue(clock.pendingAt(t0))
        assertTrue(clock.pendingAt(t0 + second))
        val washNanos = (JourneyChartRenderer.MEDAL_WASH_SECONDS * second).toLong()
        assertFalse(clock.pendingAt(t0 + washNanos))
        assertFalse(clock.pendingAt(t0 + washNanos + second))
    }

    /**
     * The strobe guard. Awards arrive in clusters (three mastered games inside a second is an
     * ordinary span), and one full-stage flash per award turns the end of a journey into a strobe.
     * A flash only starts once the cooldown since the last one that *fired* has run out.
     */
    @Test fun `clustered awards flash once, then again after the cooldown`() {
        val clock = JourneyEffectClock()
        val cooldown = JourneyChartRenderer.WASH_COOLDOWN_SECONDS.toLong()   // 6 seconds

        assertEquals(0f, clock.medalWashSeconds(0, AwardKind.BEATEN, t0, arm = true), 0.0001f)
        assertEquals(-1f, clock.medalWashSeconds(1, AwardKind.BEATEN, t0 + second, arm = true), 0f)
        assertEquals(0f, clock.medalWashSeconds(2, AwardKind.BEATEN, t0 + 8 * second, arm = true), 0.0001f)

        assertTrue("the cooldown under test is $cooldown seconds", cooldown == 6L)
    }

    @Test fun `a suppressed flash never starts on a later frame`() {
        val clock = JourneyEffectClock()
        clock.medalWashSeconds(0, AwardKind.BEATEN, t0, arm = true)
        assertEquals(-1f, clock.medalWashSeconds(1, AwardKind.BEATEN, t0 + second, arm = true), 0f)
        // Well past the cooldown, but this medal's moment has been and gone.
        assertEquals(-1f, clock.medalWashSeconds(1, AwardKind.BEATEN, t0 + 20 * second, arm = true), 0f)
    }

    @Test fun `a suppressed flash does not cost the medal its confetti`() {
        val clock = JourneyEffectClock()
        clock.medalWashSeconds(0, AwardKind.BEATEN, t0, arm = true)
        clock.medalSeconds(0, t0, arm = true)
        assertEquals(-1f, clock.medalWashSeconds(1, AwardKind.BEATEN, t0 + second, arm = true), 0f)
        assertEquals(0f, clock.medalSeconds(1, t0 + second, arm = true), 0.0001f)
    }

    @Test fun `an unarmed flash neither fires nor spends the cooldown`() {
        val clock = JourneyEffectClock()
        assertEquals(-1f, clock.medalWashSeconds(0, AwardKind.BEATEN, t0, arm = false), 0f)
        // Nothing fired, so the next award still gets its flash.
        assertEquals(0f, clock.medalWashSeconds(1, AwardKind.BEATEN, t0 + second, arm = true), 0.0001f)
    }

    @Test fun `a flash keeps running for as long as it is drawn`() {
        val clock = JourneyEffectClock()
        clock.medalWashSeconds(0, AwardKind.BEATEN, t0, arm = true)
        assertEquals(0.5f, clock.medalWashSeconds(0, AwardKind.BEATEN, t0 + second / 2, arm = false), 0.0001f)
        assertTrue(clock.pendingAt(t0 + second))
    }

    // ---- the mastery carve-out: a MASTERED award always beats a lesser one's cooldown ----

    /**
     * The bug the user reported, reproduced directly against the clock: a rival's lesser award
     * fires first and starts the shared cooldown, and the main player's mastery a moment later must
     * still flash rather than being silenced by it. A mastery is judged against its own cooldown
     * clock, not the shared one every other kind uses - see [JourneyChartRenderer
     * .WASH_COOLDOWN_SECONDS]'s doc for the full reasoning.
     */
    @Test fun `a mastery is not silenced by a lesser award that fired a moment before it`() {
        val clock = JourneyEffectClock()
        assertEquals(0f, clock.medalWashSeconds(0, AwardKind.BEATEN_CASUAL, t0, arm = true), 0.0001f)
        // Well inside the shared cooldown - a non-mastery here would be suppressed (see the
        // "clustered awards flash once" test above), but a mastery is not.
        assertEquals(
            0f,
            clock.medalWashSeconds(1, AwardKind.MASTERED, t0 + second, arm = true),
            0.0001f,
        )
    }

    /**
     * The carve-out is not "always": a mastery still respects a cooldown against ANOTHER mastery.
     * Three games mastered in one sitting is ordinary on a busy span, and three full-strength
     * flashes back to back is exactly the strobe [JourneyChartRenderer.WASH_COOLDOWN_SECONDS]
     * exists to prevent - a mastery does not get to ignore that just because it is a mastery.
     */
    @Test fun `two masteries close together still respect a cooldown between themselves`() {
        val clock = JourneyEffectClock()
        assertEquals(0f, clock.medalWashSeconds(0, AwardKind.MASTERED, t0, arm = true), 0.0001f)
        assertEquals(
            -1f,
            clock.medalWashSeconds(1, AwardKind.MASTERED, t0 + second, arm = true),
            0f,
        )
        val cooldown = (JourneyChartRenderer.WASH_COOLDOWN_SECONDS * second).toLong()
        assertEquals(
            0f,
            clock.medalWashSeconds(2, AwardKind.MASTERED, t0 + cooldown, arm = true),
            0.0001f,
        )
    }

    /**
     * The mastery clock and the shared clock are genuinely separate: a mastery firing does not
     * exempt the NEXT lesser award from the cooldown the mastery itself just started. Only masteries
     * get to skip a lesser award's turn; a lesser award still has to wait out whatever fired before
     * it, mastery included.
     */
    @Test fun `a lesser award still waits out the cooldown a mastery just started`() {
        val clock = JourneyEffectClock()
        clock.medalWashSeconds(0, AwardKind.MASTERED, t0, arm = true)
        assertEquals(
            -1f,
            clock.medalWashSeconds(1, AwardKind.BEATEN, t0 + second, arm = true),
            0f,
        )
    }

    @Test fun `a later reveal extends how long frames are needed`() {
        val clock = JourneyEffectClock()
        clock.nodeSeconds(0, t0, arm = true)
        val nodeNanos = (JourneyChartRenderer.FLOATING_POINTS_SECONDS * second).toLong()
        assertFalse(clock.pendingAt(t0 + nodeNanos))
        clock.nodeSeconds(1, t0 + second, arm = true)
        assertTrue(clock.pendingAt(t0 + nodeNanos))
    }

    // ---- the "+N" pop's own throttle, mirroring the flash's tests above ----

    /**
     * The same noise guard as the flash, sized for a smaller effect: badges reveal in clusters -
     * half a dozen inside a couple of seconds is ordinary on a dense span - and a "+N" per badge in
     * that window is not half a dozen little celebrations, it is a wall of overlapping numbers
     * nobody reads. A pop only starts once the cooldown since the last one that *fired* has run out.
     */
    @Test fun `clustered badges pop once, then again after the cooldown`() {
        val clock = JourneyEffectClock()
        val cooldown = JourneyChartRenderer.FLOATING_POINTS_COOLDOWN_SECONDS.toLong()   // 1 second

        assertEquals(0f, clock.nodePointsSeconds(0, t0, arm = true), 0.0001f)
        assertEquals(-1f, clock.nodePointsSeconds(1, t0 + second / 2, arm = true), 0f)
        assertEquals(0f, clock.nodePointsSeconds(2, t0 + 2 * second, arm = true), 0.0001f)

        assertTrue("the cooldown under test is $cooldown seconds", cooldown == 1L)
    }

    @Test fun `a suppressed pop never starts on a later frame`() {
        val clock = JourneyEffectClock()
        clock.nodePointsSeconds(0, t0, arm = true)
        assertEquals(-1f, clock.nodePointsSeconds(1, t0 + second / 2, arm = true), 0f)
        // Well past the cooldown, but this badge's moment has been and gone.
        assertEquals(-1f, clock.nodePointsSeconds(1, t0 + 20 * second, arm = true), 0f)
    }

    /**
     * The regression this pair of clocks exists to guard: [nodeSeconds] and [nodePointsSeconds]
     * used to be the same map, so throttling the "+N" would have throttled the pop-in along with
     * it - a badge that lost its number would never have grown to size either. They are separate
     * bookkeeping now, and a suppressed pop must not touch the pop-in at all.
     */
    @Test fun `a suppressed pop does not touch the badge's pop-in`() {
        val clock = JourneyEffectClock()
        clock.nodePointsSeconds(0, t0, arm = true)
        assertEquals(-1f, clock.nodePointsSeconds(1, t0 + second / 2, arm = true), 0f)
        // The pop-in is a different clock entry, and is not throttled at all.
        assertEquals(0f, clock.nodeSeconds(1, t0 + second / 2, arm = true), 0.0001f)
    }

    @Test fun `an unarmed pop neither fires nor spends the cooldown`() {
        val clock = JourneyEffectClock()
        assertEquals(-1f, clock.nodePointsSeconds(0, t0, arm = false), 0f)
        // Nothing fired, so the next badge still gets its pop.
        assertEquals(0f, clock.nodePointsSeconds(1, t0 + second, arm = true), 0.0001f)
    }
}
