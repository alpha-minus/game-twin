package com.twinlore.app.journey.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The custom span's calendar maths: a picked calendar date (the UTC midnight a Material date
 * picker reports) becomes the days-ago count the ViewModel's span bounds already run on, and the
 * pair is normalized so "From" never begins after "To" ends whichever field was edited last.
 */
class JourneyCustomRangeTest {

    private val day = 86_400_000L

    /** A Tuesday afternoon, UTC: 2026-08-25T15:00:00Z. */
    private val now = 1_787_410_800_000L

    /** The UTC midnight [d] days before [now]'s own date, which is what the picker hands back. */
    private fun pickedMidnight(d: Int): Long = (now / day) * day - d * day

    @Test fun `picking today is zero days ago`() {
        assertEquals(0, customDaysAgo(now, pickedMidnight(0)))
    }

    @Test fun `a picked date counts whole days back from now`() {
        assertEquals(3, customDaysAgo(now, pickedMidnight(3)))
        assertEquals(365, customDaysAgo(now, pickedMidnight(365)))
    }

    /** Beyond the old slider's 5-year cap: the calendar has no reason to inherit it. */
    @Test fun `dates older than the retired slider cap still convert`() {
        assertEquals(2600, customDaysAgo(now, pickedMidnight(2600)))
    }

    @Test fun `a future pick clamps to today rather than going negative`() {
        assertEquals(0, customDaysAgo(now, pickedMidnight(-2)))
    }

    @Test fun `an ordered range passes through untouched`() {
        assertEquals(365 to 30, normalizedCustomRange(fromDaysAgo = 365, toDaysAgo = 30))
    }

    @Test fun `an inverted range swaps so From stays the earlier date`() {
        assertEquals(365 to 30, normalizedCustomRange(fromDaysAgo = 30, toDaysAgo = 365))
    }

    @Test fun `a single-day range is legal`() {
        assertEquals(7 to 7, normalizedCustomRange(fromDaysAgo = 7, toDaysAgo = 7))
    }
}
