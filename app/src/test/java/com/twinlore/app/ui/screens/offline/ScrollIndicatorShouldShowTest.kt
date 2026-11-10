package com.twinlore.app.ui.screens.offline

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The scroll-position chip surfaces on the first movement, with no
 * velocity or distance threshold. A recompose that leaves scrollY where it
 * was (including the chip's own first composition) must not show it.
 */
class ScrollIndicatorShouldShowTest {

    @Test
    fun `a single pixel of movement shows the chip`() {
        assertTrue(scrollIndicatorShouldShow(prevScrollY = 1200, scrollY = 1201))
        assertTrue(scrollIndicatorShouldShow(prevScrollY = 1200, scrollY = 1199))
    }

    @Test
    fun `slow reading-pace steps show the chip that the old 400px gate hid`() {
        assertTrue(scrollIndicatorShouldShow(prevScrollY = 0, scrollY = 12))
        assertTrue(scrollIndicatorShouldShow(prevScrollY = 12, scrollY = 24))
    }

    @Test
    fun `large jumps still show the chip`() {
        assertTrue(scrollIndicatorShouldShow(prevScrollY = 0, scrollY = 50_000))
    }

    @Test
    fun `no movement does not show the chip`() {
        assertFalse(scrollIndicatorShouldShow(prevScrollY = 0, scrollY = 0))
        assertFalse(scrollIndicatorShouldShow(prevScrollY = 4321, scrollY = 4321))
    }
}
