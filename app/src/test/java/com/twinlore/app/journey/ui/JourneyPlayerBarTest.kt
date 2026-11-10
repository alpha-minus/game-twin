package com.twinlore.app.journey.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The player bar's compact rule: whether the button row can hold every button at the standard
 * 48dp touch size, or must drop to the compact size so the LAST button (Settings) is not handed
 * whatever sliver the row has left (user report: the gear was squashed on a portrait phone).
 */
class JourneyPlayerBarTest {

    @Test fun `a portrait phone with the grid button on the bar is compact`() {
        // 360dp screen: 12dp surface margin x2 and 10dp column padding x2 leave 316dp for the
        // row; seven buttons at 48dp plus six 2dp gaps want 348dp.
        assertTrue(playerBarCompact(availableDp = 316f, buttonCount = 7))
    }

    @Test fun `the same width without the grid button keeps full-size buttons`() {
        // Six buttons want 298dp: a span journey's bar fits the same phone.
        assertFalse(playerBarCompact(availableDp = 316f, buttonCount = 6))
    }

    @Test fun `a landscape phone is never compact`() {
        assertFalse(playerBarCompact(availableDp = 736f, buttonCount = 7))
    }

    @Test fun `the boundary is exact - just enough room stays full size`() {
        // 7 * 48 + 6 * 2 = 348.
        assertFalse(playerBarCompact(availableDp = 348f, buttonCount = 7))
        assertTrue(playerBarCompact(availableDp = 347.9f, buttonCount = 7))
    }
}
