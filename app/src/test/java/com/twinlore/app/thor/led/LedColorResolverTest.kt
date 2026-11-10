package com.twinlore.app.thor.led

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the adaptive LED colour math: hue is preserved, and saturation/brightness only move when
 * the user's own controls ask them to. The key regression guard is [doesNotNormalizeChannels] —
 * the removed channel-normalization used to silently brighten and saturate dim colours.
 */
class LedColorResolverTest {

    @Test
    fun neutralSettingsPassFocusColorThrough() {
        // Pure red, no boost, full brightness → exactly red.
        val (r, g, b) = LedColorResolver.resolve(Color(1f, 0f, 0f), brightness = 255, saturationBoost = 0f)
        assertEquals(255, r)
        assertEquals(0, g)
        assertEquals(0, b)
    }

    @Test
    fun doesNotNormalizeChannels() {
        // A dim, low-saturation colour must stay dim. The old normalization scaled the max channel
        // to 255 (≈ (255,128,64) here), changing both brightness and saturation. It must not.
        val (r, g, b) = LedColorResolver.resolve(Color(0.4f, 0.2f, 0.1f), brightness = 255, saturationBoost = 0f)
        assertEquals(102, r)
        assertEquals(51, g)
        assertEquals(26, b)
    }

    @Test
    fun brightnessScalesLinearlyAndPreservesHue() {
        val full = LedColorResolver.resolve(Color(0.8f, 0.4f, 0.2f), brightness = 255, saturationBoost = 0f)
        val half = LedColorResolver.resolve(Color(0.8f, 0.4f, 0.2f), brightness = 128, saturationBoost = 0f)

        // Full: 4:2:1 ratio preserved.
        assertEquals(204, full.first)
        assertEquals(102, full.second)
        assertEquals(51, full.third)

        // Half brightness ≈ 0.502 of full, ratios still 4:2:1.
        assertEquals(102, half.first)
        assertEquals(51, half.second)
        assertEquals(26, half.third)
    }

    @Test
    fun saturationBoostWidensChannelSpreadAroundAverage() {
        val base = Color(0.6f, 0.5f, 0.4f) // avg 0.5
        val plain = LedColorResolver.resolve(base, brightness = 255, saturationBoost = 0f)
        val boosted = LedColorResolver.resolve(base, brightness = 255, saturationBoost = 0.5f)

        // High channel pushed up, low channel pushed down, mid (== avg) unchanged.
        assertTrue("red should increase", boosted.first > plain.first)
        assertEquals("green at average stays put", plain.second, boosted.second)
        assertTrue("blue should decrease", boosted.third < plain.third)
    }

    @Test
    fun zeroBrightnessIsOff() {
        val (r, g, b) = LedColorResolver.resolve(Color(1f, 1f, 1f), brightness = 0, saturationBoost = 0.5f)
        assertEquals(0, r)
        assertEquals(0, g)
        assertEquals(0, b)
    }

    @Test
    fun boostClampsWithoutOverflow() {
        // Already-saturated colour: boosting must clamp at 255 / 0, never wrap.
        val (r, g, b) = LedColorResolver.resolve(Color(1f, 0f, 0f), brightness = 255, saturationBoost = 1f)
        assertEquals(255, r)
        assertEquals(0, g)
        assertEquals(0, b)
    }
}
