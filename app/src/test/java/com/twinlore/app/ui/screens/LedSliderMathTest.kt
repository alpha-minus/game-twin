package com.twinlore.app.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class LedSliderMathTest {

    @Test
    fun brightnessToFraction_mapsEndpointsAndMidpoint() {
        assertEquals(0f, LedSliderMath.brightnessToFraction(0), 0.0001f)
        assertEquals(1f, LedSliderMath.brightnessToFraction(255), 0.0001f)
        assertEquals(0.5019f, LedSliderMath.brightnessToFraction(128), 0.001f)
    }

    @Test
    fun brightnessToFraction_coercesOutOfRange() {
        assertEquals(0f, LedSliderMath.brightnessToFraction(-40), 0.0001f)
        assertEquals(1f, LedSliderMath.brightnessToFraction(999), 0.0001f)
    }

    @Test
    fun fractionToBrightness_roundTripsEndpoints() {
        assertEquals(0, LedSliderMath.fractionToBrightness(0f))
        assertEquals(255, LedSliderMath.fractionToBrightness(1f))
        assertEquals(128, LedSliderMath.fractionToBrightness(0.5f))
    }

    @Test
    fun brightnessPercentLabel_rendersWholePercents() {
        assertEquals("0%", LedSliderMath.brightnessPercentLabel(0))
        assertEquals("100%", LedSliderMath.brightnessPercentLabel(255))
        assertEquals("5%", LedSliderMath.brightnessPercentLabel(13))
    }

    @Test
    fun saturationLabel_zeroShowsOff() {
        assertEquals("Off", LedSliderMath.saturationLabel(0f))
        assertEquals("Off", LedSliderMath.saturationLabel(0.0005f))
        assertEquals("100%", LedSliderMath.saturationLabel(1f))
        assertEquals("33%", LedSliderMath.saturationLabel(0.33f))
    }

    @Test
    fun snapFraction_snapsToNearestStep() {
        // 20 steps → 5% increments
        assertEquals(0.55f, LedSliderMath.snapFraction(0.53f, 20), 0.0001f)
        assertEquals(0.05f, LedSliderMath.snapFraction(0.04f, 20), 0.0001f)
        assertEquals(0f, LedSliderMath.snapFraction(0.01f, 20), 0.0001f)
        assertEquals(1f, LedSliderMath.snapFraction(0.99f, 20), 0.0001f)
        // 10 steps → 10% increments
        assertEquals(0.3f, LedSliderMath.snapFraction(0.34f, 10), 0.0001f)
    }
}
