package com.twinlore.app.data

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The LED follows the detected colour, not its palette match.
 *
 * Detection stores two things: the exact colour from the artwork, and the
 * nearest [FocusColor] for the pickers. Several LED paths (sleep, the vault
 * dialogs, the LED settings' own preview) drove the rings with the match, so a
 * red cover whose nearest entry is Crimson lit the rings crimson: the "red
 * turned pink" that could never be pinned to one action.
 */
class EffectiveAccentColorTest {

    private val red = Color(0xFFC81E28)

    @Test
    fun `adaptive with a detected colour is that colour`() {
        assertEquals(red, effectiveAccentColor(FocusColor.CRIMSON, red.toArgb(), autoDetect = true))
    }

    @Test
    fun `adaptive with nothing detected yet falls back to the chosen colour`() {
        assertEquals(FocusColor.CRIMSON.color, effectiveAccentColor(FocusColor.CRIMSON, null, autoDetect = true))
    }

    @Test
    fun `manual ignores a stale detected colour`() {
        assertEquals(FocusColor.GOLD.color, effectiveAccentColor(FocusColor.GOLD, red.toArgb(), autoDetect = false))
    }
}
