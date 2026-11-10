package com.twinlore.app.ui.screens

import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The showcase's cover art: bigger than it was, never blurry, never the
 * whole screen.
 */
class ShowcaseArtSizeTest {

    private fun assertDp(expected: Float, actual: androidx.compose.ui.unit.Dp) =
        assertEquals(expected, actual.value, 0.5f)

    // A scraped 300x450 cover at density 2 drew at 150x225dp before. The ask
    // was a third to a half larger, not a poster: 1.4x, and the slot has room.
    @Test
    fun `a small cover grows by the upscale factor and no more`() {
        val (w, h) = showcaseArtSize(
            slotWidth = 1000.dp, slotHeight = 500.dp,
            aspect = 300f / 450f, nativePx = IntSize(300, 450), density = 2f,
        )
        assertDp(210f, w)
        assertDp(315f, h)
    }

    // High-resolution art is never upscaled: the slot cap wins first.
    @Test
    fun `large art is capped by the slot, at its aspect`() {
        val (w, h) = showcaseArtSize(
            slotWidth = 1000.dp, slotHeight = 500.dp,
            aspect = 2f / 3f, nativePx = IntSize(1200, 1800), density = 2f,
        )
        assertDp(350f, h)
        assertDp(350f * 2f / 3f, w)
    }

    // A wide cover in a tall slot is width-limited instead.
    @Test
    fun `wide art is limited by width`() {
        val (w, h) = showcaseArtSize(
            slotWidth = 400.dp, slotHeight = 800.dp,
            aspect = 2f, nativePx = IntSize(2000, 1000), density = 2f,
        )
        assertDp(280f, w)
        assertDp(140f, h)
    }

    // No bounds means no resolution cap, but still not the whole slot.
    @Test
    fun `unknown resolution falls back to the slot cap`() {
        val (w, h) = showcaseArtSize(
            slotWidth = 1000.dp, slotHeight = 500.dp,
            aspect = 2f / 3f, nativePx = null, density = 2f,
        )
        assertDp(350f, h)
        assertDp(350f * 2f / 3f, w)
    }

    @Test
    fun `no art at all returns the unshaped cap for the placeholder text`() {
        val (w, h) = showcaseArtSize(
            slotWidth = 1000.dp, slotHeight = 500.dp,
            aspect = null, nativePx = null, density = 2f,
        )
        assertDp(700f, w)
        assertDp(350f, h)
    }
}
