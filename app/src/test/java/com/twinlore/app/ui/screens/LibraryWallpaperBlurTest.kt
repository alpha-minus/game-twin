package com.twinlore.app.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryWallpaperBlurTest {

    private fun argb(r: Int, g: Int, b: Int) =
        (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    private fun red(p: Int) = (p shr 16) and 0xFF

    @Test
    fun `a flat image is unchanged by blurring`() {
        val pixels = IntArray(8 * 8) { argb(40, 90, 200) }
        blurArgb(pixels, 8, 8, radius = 3)
        pixels.forEach { assertEquals(argb(40, 90, 200), it) }
    }

    @Test
    fun `a bright block bleeds outside its own bounds`() {
        // A 4x4 white block in the middle of a 16x16 black field. Deliberately a block and
        // not a single pixel: three integer box passes divide by the window each time, so a
        // lone bright pixel truncates to nothing and would test the rounding, not the blur.
        val w = 16
        val pixels = IntArray(w * w) { argb(0, 0, 0) }
        for (y in 6..9) for (x in 6..9) pixels[y * w + x] = argb(255, 255, 255)
        blurArgb(pixels, w, w, radius = 2)
        assertTrue("the block's centre dims", red(pixels[7 * w + 7]) < 255)
        assertTrue("light spreads outside the block", red(pixels[7 * w + 4]) > 0)
        assertTrue("the far corner stays black", red(pixels[0]) == 0)
    }

    @Test
    fun `a hard edge becomes a monotonic ramp`() {
        // Left half white, right half black, wide enough that three radius-2 passes (a nine
        // pixel reach) cannot touch either outer edge from the seam.
        val w = 32
        val pixels = IntArray(w * 4) { i -> if (i % w < w / 2) argb(255, 255, 255) else argb(0, 0, 0) }
        blurArgb(pixels, w, 4, radius = 2)
        val row = (0 until w).map { red(pixels[it]) }
        assertTrue("left edge stays bright", row.first() > 240)
        assertTrue("right edge stays dark", row.last() < 15)
        assertTrue("the seam is a ramp", row[15] in 1..254 && row[16] in 1..254)
        assertTrue("monotonically decreasing", row.zipWithNext().all { (a, b) -> a >= b })
    }

    @Test
    fun `radius zero is a no-op`() {
        val pixels = IntArray(4 * 4) { it * 1000 or (0xFF shl 24) }
        val copy = pixels.copyOf()
        blurArgb(pixels, 4, 4, radius = 0)
        assertTrue(pixels.contentEquals(copy))
    }

    @Test
    fun `blurring a mirror-symmetric image keeps it mirror-symmetric`() {
        // A sliding box window is inherently mirror-equivariant: the edge clamp
        // (`coerceIn(0, srcWidth - 1)`) is itself symmetric about the image's centre, so any
        // correct window of width `2*radius+1` centred on x sees exactly the mirror of what it
        // sees centred on `width-1-x`. A window that has been shifted by one pixel (entering
        // the wrong index) breaks that equivariance in one direction but not the other, so a
        // symmetric input stops coming out symmetric. This is invisible to the four tests above
        // because none of them checks a value against its own mirror image.
        //
        // The pattern below is deliberately not flat and not a single symmetric blob: value
        // depends on distance from the nearest vertical and horizontal edge, so it climbs
        // step-wise toward the centre on both axes without ever repeating the flat-image case.
        val w = 16
        val h = 16
        val pixels = IntArray(w * h) { i ->
            val x = i % w
            val y = i / w
            val dx = minOf(x, w - 1 - x)
            val dy = minOf(y, h - 1 - y)
            argb(dx * 16 + dy * 8, dx * 16 + dy * 8, dx * 16 + dy * 8)
        }
        // Sanity check: the pattern actually varies, so a kernel that flattened everything
        // (which would trivially stay "symmetric") could not pass this test by accident.
        assertTrue("the pattern is not flat", pixels.toSet().size > 1)

        blurArgb(pixels, w, h, radius = 3)

        for (y in 0 until h) {
            for (x in 0 until w) {
                assertEquals(
                    "horizontal mirror symmetry broken at x=$x, y=$y",
                    red(pixels[y * w + x]),
                    red(pixels[y * w + (w - 1 - x)]),
                )
                assertEquals(
                    "vertical mirror symmetry broken at x=$x, y=$y",
                    red(pixels[y * w + x]),
                    red(pixels[(h - 1 - y) * w + x]),
                )
            }
        }
    }
}
