package com.twinlore.app.thor.led

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pins the LED pulse to the showcase glow's rate: dim at 0, full at 1200ms, dim again at 2400ms. */
class LedPulseTest {

    @Test
    fun startsDim() {
        assertEquals(LedPulse.MIN_FACTOR, LedPulse.factor(0), 1e-4f)
    }

    @Test
    fun peaksAfterOneHalfPeriod() {
        assertEquals(1f, LedPulse.factor(LedPulse.HALF_PERIOD_MS), 1e-4f)
    }

    @Test
    fun returnsDimAndRepeats() {
        val period = LedPulse.HALF_PERIOD_MS * 2
        assertEquals(LedPulse.MIN_FACTOR, LedPulse.factor(period), 1e-4f)
        assertEquals(LedPulse.factor(300), LedPulse.factor(period * 5 + 300), 1e-4f)
    }

    @Test
    fun staysWithinRange() {
        for (ms in 0L..5000L step 16) {
            val f = LedPulse.factor(ms)
            assertTrue("factor $f at $ms", f >= LedPulse.MIN_FACTOR - 1e-4f && f <= 1f + 1e-4f)
        }
    }
}
