package com.twinlore.app.ui.ambience

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AmbienceColorMathTest {

    private fun assertColorNear(expected: Color, actual: Color) {
        assertEquals(expected.red, actual.red, 0.01f)
        assertEquals(expected.green, actual.green, 0.01f)
        assertEquals(expected.blue, actual.blue, 0.01f)
    }

    @Test
    fun `hsv round trip returns the original colour`() {
        listOf(
            Color(0xFFFF3B30), Color(0xFF34C759), Color(0xFF3B7BFF),
            Color(0xFF808080), Color.White, Color.Black,
        ).forEach { c ->
            val hsv = rgbToHsv(c)
            assertColorNear(c, hsvToRgb(hsv[0], hsv[1], hsv[2]))
        }
    }

    @Test
    fun `pure red reads as hue zero, full saturation, full value`() {
        val hsv = rgbToHsv(Color.Red)
        assertEquals(0f, hsv[0], 0.01f)
        assertEquals(1f, hsv[1], 0.01f)
        assertEquals(1f, hsv[2], 0.01f)
    }

    @Test
    fun `a negative hue shift wraps around the circle instead of clamping`() {
        val shifted = Color.Red.shiftHsv(degrees = -22f, satMul = 1f, valMul = 1f)
        assertEquals(338f, rgbToHsv(shifted)[0], 0.5f)
    }

    @Test
    fun `value never falls below the floor, so a dark accent still shows embers`() {
        val hsv = rgbToHsv(Color.Black.shiftHsv(degrees = 0f, satMul = 1f, valMul = 1f))
        assertTrue("value floor should lift black", hsv[2] >= 0.34f)
    }

    @Test
    fun `saturation multiplier is clamped to one`() {
        val hsv = rgbToHsv(Color.Red.shiftHsv(degrees = 0f, satMul = 4f, valMul = 1f))
        assertTrue(hsv[1] <= 1f)
    }

    @Test
    fun `the ember palette leads with the accent and gives three distinct colours`() {
        val accent = Color(0xFF2B7FFF)
        val palette = emberPalette(accent)
        assertEquals(3, palette.size)
        assertColorNear(accent, palette[0])
        assertEquals(3, palette.distinct().size)
    }
}
