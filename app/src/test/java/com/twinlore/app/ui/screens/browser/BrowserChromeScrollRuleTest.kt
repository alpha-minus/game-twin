package com.twinlore.app.ui.screens.browser

import org.junit.Assert.assertEquals
import org.junit.Test

class BrowserChromeScrollRuleTest {

    @Test
    fun `scroll down shrinks padding when not loading`() {
        val next = applyScrollDelta(
            currentPad = 100f,
            delta = 30f,
            maxPad = 100f,
            isLoading = false
        )
        assertEquals(70f, next, 0.001f)
    }

    @Test
    fun `scroll up grows padding back to max when not loading`() {
        val next = applyScrollDelta(
            currentPad = 40f,
            delta = -100f,
            maxPad = 100f,
            isLoading = false
        )
        assertEquals(100f, next, 0.001f)
    }

    @Test
    fun `padding clamps at zero when scrolling down past floor`() {
        val next = applyScrollDelta(
            currentPad = 10f,
            delta = 50f,
            maxPad = 100f,
            isLoading = false
        )
        assertEquals(0f, next, 0.001f)
    }

    @Test
    fun `scroll delta is ignored while loading - padding stays at max`() {
        val next = applyScrollDelta(
            currentPad = 100f,
            delta = 80f,
            maxPad = 100f,
            isLoading = true
        )
        assertEquals(100f, next, 0.001f)
    }

    @Test
    fun `scroll delta is ignored while loading even when padding is partial`() {
        // While loading, the LaunchedEffect(isLoading) will re-seed pad to maxPad,
        // but in the same tick a scroll event may still arrive. Verify it's
        // ignored regardless of starting pad.
        val next = applyScrollDelta(
            currentPad = 50f,
            delta = 30f,
            maxPad = 100f,
            isLoading = true
        )
        assertEquals(50f, next, 0.001f)
    }
}
