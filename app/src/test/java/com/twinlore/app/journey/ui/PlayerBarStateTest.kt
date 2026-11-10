package com.twinlore.app.journey.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The control bar and the settings sheet are one piece of furniture, pinned at the state level.
 *
 * The sheet is drawn inside the bar's own fade, so a sheet whose bar has gone is not a sheet the
 * user can see or close: it simply comes back, still open, the next time the bar is asked for. That
 * is what was happening on the device, and these are the two halves of the fix - hiding takes both
 * away, and an open sheet stops the countdown that would hide them behind the user's back.
 */
class PlayerBarStateTest {

    @Test fun `starts with the bar up and the sheet closed`() {
        val bar = PlayerBarState()
        assertTrue(bar.barVisible)
        assertFalse(bar.settingsOpen)
        assertFalse(bar.autoHideSuppressed)
    }

    @Test fun `a tap that hides the bar closes the sheet with it`() {
        val bar = PlayerBarState()
        bar.toggleSettings()
        assertTrue(bar.settingsOpen)

        bar.toggleBar()

        assertFalse(bar.barVisible)
        assertFalse("the sheet must not be left open behind a hidden bar", bar.settingsOpen)
    }

    @Test fun `the auto-hide path closes the sheet too`() {
        val bar = PlayerBarState()
        bar.toggleSettings()

        bar.hide()

        assertFalse(bar.barVisible)
        assertFalse(bar.settingsOpen)
    }

    @Test fun `an open sheet suppresses the auto-hide countdown`() {
        val bar = PlayerBarState()
        assertFalse(bar.autoHideSuppressed)

        bar.toggleSettings()
        assertTrue(bar.autoHideSuppressed)

        bar.toggleSettings()
        assertFalse(bar.autoHideSuppressed)
    }

    @Test fun `bringing the bar back does not reopen the sheet`() {
        val bar = PlayerBarState()
        bar.toggleSettings()
        bar.toggleBar() // hides both

        bar.reveal()

        assertTrue(bar.barVisible)
        assertFalse("closing the sheet was a decision, not a side effect", bar.settingsOpen)
    }

    /**
     * The gap the rule above was leaking through. `PlayerControls` stays composed and clickable
     * through the bar's exit fade, so a tap that lands on the settings button during it opens the
     * sheet with the bar already gone. The auto-hide loop returns early on a hidden bar, so nothing
     * takes it back, and the next reveal brought the bar up with the sheet on it.
     */
    @Test fun `a sheet opened during the bar's exit fade does not ride back in on it`() {
        val bar = PlayerBarState()
        bar.hide()
        bar.toggleSettings() // the tap that lands on the fading-out button
        assertTrue(bar.settingsOpen)

        bar.reveal()

        assertTrue(bar.barVisible)
        assertFalse("the user dismissed this bar; its sheet does not come back with it", bar.settingsOpen)
    }

    /**
     * And reveal() is not a way to close the sheet. It is what every interaction calls to restart
     * the auto-hide countdown, so a finger on the slider with the sheet up must leave it up.
     */
    @Test fun `reveal on an already visible bar leaves the sheet alone`() {
        val bar = PlayerBarState()
        bar.toggleSettings()

        bar.reveal()

        assertTrue(bar.barVisible)
        assertTrue("an interaction is not a dismissal", bar.settingsOpen)
    }

    /** What Back does with the sheet up: the sheet goes, the bar it was opened from stays. */
    @Test fun `closeSettings leaves the bar where it is`() {
        val bar = PlayerBarState()
        bar.toggleSettings()

        bar.closeSettings()

        assertFalse(bar.settingsOpen)
        assertTrue("Back dismissed a layer, it did not hide the furniture", bar.barVisible)
    }

    /**
     * One press, one layer. Back used to close the player from wherever it was pressed, so
     * dismissing the settings sheet threw the whole player away with it - and on a vault journey
     * that is two levels in one press, because closing the player there leaves the screen.
     */
    @Test fun `back takes the settings sheet before anything else`() {
        assertEquals(
            PlayerBackTarget.SETTINGS,
            playerBackTarget(settingsOpen = true, tooltipOpen = true),
        )
    }

    @Test fun `back takes an open tooltip before the player`() {
        assertEquals(
            PlayerBackTarget.TOOLTIP,
            playerBackTarget(settingsOpen = false, tooltipOpen = true),
        )
    }

    @Test fun `back closes the player once nothing is in front of it`() {
        assertEquals(
            PlayerBackTarget.PLAYER,
            playerBackTarget(settingsOpen = false, tooltipOpen = false),
        )
    }

    @Test fun `toggleBar walks hidden and back`() {
        val bar = PlayerBarState()
        bar.toggleBar()
        assertFalse(bar.barVisible)
        bar.toggleBar()
        assertTrue(bar.barVisible)
    }

    // ---- the legend sheet: its own button now, same furniture rules as the settings ----

    @Test fun `one sheet at a time - opening either closes the other`() {
        val bar = PlayerBarState()
        bar.toggleSettings()
        bar.toggleLegend()
        assertTrue(bar.legendOpen)
        assertFalse("the legend takes the settings sheet's place, not a spot on top of it", bar.settingsOpen)

        bar.toggleSettings()
        assertTrue(bar.settingsOpen)
        assertFalse(bar.legendOpen)
    }

    @Test fun `an open legend suppresses the auto-hide countdown and hides with the bar`() {
        val bar = PlayerBarState()
        bar.toggleLegend()
        assertTrue(bar.autoHideSuppressed)

        bar.hide()
        assertFalse(bar.legendOpen)
        assertFalse(bar.autoHideSuppressed)
    }

    @Test fun `back takes an open legend before the tooltip and the player`() {
        assertEquals(
            PlayerBackTarget.LEGEND,
            playerBackTarget(settingsOpen = false, tooltipOpen = true, legendOpen = true),
        )
        val bar = PlayerBarState()
        bar.toggleLegend()
        bar.closeLegend()
        assertFalse(bar.legendOpen)
        assertTrue("Back dismissed a layer, it did not hide the furniture", bar.barVisible)
    }
}
