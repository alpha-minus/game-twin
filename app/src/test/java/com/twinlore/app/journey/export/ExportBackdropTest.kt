package com.twinlore.app.journey.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The export backdrop's centre crop: the video draws game art to fill 1920x1080 (or half of it in
 * compare mode), and the source rectangle it reads from is the only piece of that with any maths in
 * it, so it is a pure function tested without a canvas.
 */
class ExportBackdropTest {

    @Test fun `a source wider than the target is trimmed left and right`() {
        val crop = ExportBackdrop.cropRect(srcWidth = 1000, srcHeight = 250, dstWidth = 1920, dstHeight = 1080)

        assertEquals(250, crop.height)
        assertEquals(444, crop.width) // 250 * 16/9
        assertEquals(crop.left, 1000 - crop.right)
        assertTrue("the crop must stay inside the source", crop.right <= 1000)
    }

    @Test fun `a source taller than the target is trimmed top and bottom`() {
        val crop = ExportBackdrop.cropRect(srcWidth = 900, srcHeight = 1200, dstWidth = 1920, dstHeight = 1080)

        assertEquals(900, crop.width)
        assertEquals(506, crop.height) // 900 * 9/16
        assertEquals(crop.top, 1200 - crop.bottom)
    }

    @Test fun `a source already at the target ratio is used whole`() {
        val crop = ExportBackdrop.cropRect(srcWidth = 960, srcHeight = 540, dstWidth = 1920, dstHeight = 1080)

        assertEquals(0, crop.left)
        assertEquals(0, crop.top)
        assertEquals(960, crop.width)
        assertEquals(540, crop.height)
    }

    @Test fun `the compare split crops to a half height band`() {
        val crop = ExportBackdrop.cropRect(srcWidth = 960, srcHeight = 540, dstWidth = 1920, dstHeight = 540)

        // A 32:9 band out of a 16:9 image keeps the full width and half the height.
        assertEquals(960, crop.width)
        assertEquals(270, crop.height)
        assertEquals(135, crop.top)
    }

    @Test fun `degenerate sizes never produce a negative rectangle`() {
        val crop = ExportBackdrop.cropRect(srcWidth = 0, srcHeight = 0, dstWidth = 1920, dstHeight = 1080)

        assertEquals(0, crop.width)
        assertEquals(0, crop.height)
    }

    // --- the fit, which is what the backdrop actually draws with -------------------------------

    /**
     * The rule the screen follows and the file therefore has to: the whole picture survives, and
     * the frame letterboxes around it. A 4:3 title screen that lost its top and bottom to a centre
     * crop was the report this replaced.
     */
    @Test fun `a four by three source keeps all of itself inside a sixteen by nine frame`() {
        val fit = ExportBackdrop.fitRect(srcWidth = 640, srcHeight = 480, dstWidth = 1920, dstHeight = 1080)

        assertEquals("the height is what runs out first, so it is filled", 1080, fit.height)
        assertEquals(1440, fit.width) // 480 -> 1080 is 2.25x, and 640 * 2.25
        assertEquals("centred, so the bars are equal", fit.left, 1920 - fit.right)
        assertEquals(0, fit.top)
        assertEquals(
            "the aspect ratio must survive the fit",
            640f / 480f,
            fit.width.toFloat() / fit.height,
            0.01f,
        )
    }

    @Test fun `a very wide source letterboxes top and bottom`() {
        val fit = ExportBackdrop.fitRect(srcWidth = 1000, srcHeight = 250, dstWidth = 1920, dstHeight = 1080)

        assertEquals(1920, fit.width)
        assertEquals(480, fit.height)
        assertEquals(fit.top, 1080 - fit.bottom)
        assertEquals(0, fit.left)
    }

    @Test fun `a source already at the target ratio fills it exactly`() {
        val fit = ExportBackdrop.fitRect(srcWidth = 960, srcHeight = 540, dstWidth = 1920, dstHeight = 1080)

        assertEquals(0, fit.left)
        assertEquals(0, fit.top)
        assertEquals(1920, fit.width)
        assertEquals(1080, fit.height)
    }

    /** A compare export gives each player half the height; the fit has to respect the band. */
    @Test fun `the compare split fits inside a half height band`() {
        val fit = ExportBackdrop.fitRect(srcWidth = 640, srcHeight = 480, dstWidth = 1920, dstHeight = 540)

        assertEquals(540, fit.height)
        assertEquals(720, fit.width)
        assertTrue("the fit must stay inside the band", fit.right <= 1920 && fit.bottom <= 540)
    }

    @Test fun `a fit never overflows its destination`() {
        listOf(
            Triple(4000, 30, 1920) to 1080,
            Triple(30, 4000, 1920) to 1080,
            Triple(1, 1, 1920) to 1080,
        ).forEach { (src, dstHeight) ->
            val (w, h, dstWidth) = src
            val fit = ExportBackdrop.fitRect(w, h, dstWidth, dstHeight)
            assertTrue(
                "a ${w}x$h source overflowed: $fit",
                fit.left >= 0 && fit.top >= 0 && fit.right <= dstWidth && fit.bottom <= dstHeight,
            )
        }
    }

    @Test fun `degenerate sizes never produce a negative fit`() {
        val fit = ExportBackdrop.fitRect(srcWidth = 0, srcHeight = 0, dstWidth = 1920, dstHeight = 1080)

        assertEquals(0, fit.width)
        assertEquals(0, fit.height)
    }

    // Whether a rival splits the backdrop is `splitsForRivals` in `journey.domain` now, shared with
    // the screen's own backdrop - see `BackdropSplitTest`.
}
