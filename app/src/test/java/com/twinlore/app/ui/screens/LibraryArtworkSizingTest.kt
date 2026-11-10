package com.twinlore.app.ui.screens

import com.twinlore.app.data.library.ArtworkThumbnailCache
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * All cases use the AYN Thor's real geometry: a 1920-px-wide window at density
 * 369, where 8.dp rounds to 18 px. Numbers cross-checked against an on-device screenshot.
 */
class LibraryArtworkSizingTest {

    private val thorWidth = 1920
    private val thorSpacing = 18
    private val ratio3x4 = 0.75f

    @Test
    fun `three column grid decodes at 608`() {
        // (1920 - 2*18 - 2*18) / 3 - 2*18 = 580, bucketed up to 608.
        val size = gridDecodeSize(thorWidth, cols = 3, aspectRatio = ratio3x4, spacingPx = thorSpacing)
        assertEquals(608, size.width)
        assertEquals(811, size.height)
    }

    @Test
    fun `five column grid decodes at 352`() {
        val size = gridDecodeSize(thorWidth, cols = 5, aspectRatio = ratio3x4, spacingPx = thorSpacing)
        assertEquals(352, size.width)
    }

    @Test
    fun `nine column grid decodes at 160, cheaper than the old fixed 256`() {
        val size = gridDecodeSize(thorWidth, cols = 9, aspectRatio = ratio3x4, spacingPx = thorSpacing)
        assertEquals(160, size.width)
        assertTrue("small grid must not cost more than the old fixed size", size.width < 256)
    }

    /**
     * LIST must stay at or below the thumbnail width: a wider request makes
     * buildGridArtRequest skip the thumb cache and decode the original multi-MB source on
     * the scroll path, which is exactly the first-scroll stutter the cache exists to
     * prevent. The 768 thumb drawn into the wider row is a mild upsample of a 3:1 crop.
     */
    @Test
    fun `single column LIST rides the thumbnail cache`() {
        val size = gridDecodeSize(thorWidth, cols = 1, aspectRatio = ratio3x4, spacingPx = thorSpacing)
        assertEquals(ArtworkThumbnailCache.TARGET_WIDTH_PX, size.width)
        assertEquals(1024, size.height)
    }

    @Test
    fun `LIST height is clamped for extreme ratios`() {
        // 3:1 gives 256, inside the clamp.
        assertEquals(256, gridDecodeSize(thorWidth, 1, 3f, thorSpacing).height)
        // A ratio pushing past the ceiling is clamped rather than allocating unbounded.
        assertEquals(1280, gridDecodeSize(thorWidth, 1, 0.5f, thorSpacing).height)
    }

    @Test
    fun `grid width is clamped to the thumbnail width`() {
        // A hypothetical very wide panel must not ask Coil for more pixels than
        // the thumb holds, or the tile would upsample from the thumb anyway.
        val size = gridDecodeSize(containerWidthPx = 6000, cols = 3, aspectRatio = ratio3x4, spacingPx = thorSpacing)
        assertEquals(ArtworkThumbnailCache.TARGET_WIDTH_PX, size.width)
    }

    @Test
    fun `a width already on a bucket boundary is not rounded up`() {
        // Container chosen so the art width lands exactly on 320:
        // (1140 - 36 - 36)/3 - 36 = 320
        val size = gridDecodeSize(containerWidthPx = 1140, cols = 3, aspectRatio = ratio3x4, spacingPx = thorSpacing)
        assertEquals(320, size.width)
    }

    @Test
    fun `small container-width differences land in the same bucket`() {
        // The whole design rests on this: the grid measures its container while the
        // cold-start preload derives the width from the window, and the two can differ
        // by a few pixels. If they land in different buckets the preload writes to a
        // Coil slot the grid never reads and silently becomes dead weight.
        //
        // NOTE this is a tolerance, not a guarantee: a round-up bucket only makes two
        // widths agree when both land in the same 32-wide band. At 1920 the 3-column
        // case sits 3 px above the 577 lower edge of bucket 608, so the usable downward
        // headroom is only ~9 container px. See the preload comment in LibraryScreen.
        val measured = gridDecodeSize(1920, 3, ratio3x4, thorSpacing)
        assertEquals(measured, gridDecodeSize(1919, 3, ratio3x4, thorSpacing))
        assertEquals(measured, gridDecodeSize(1914, 3, ratio3x4, thorSpacing))
        assertEquals(measured, gridDecodeSize(1912, 3, ratio3x4, thorSpacing))
    }

    @Test
    fun `degenerate inputs do not produce a zero or negative size`() {
        val size = gridDecodeSize(containerWidthPx = 40, cols = 9, aspectRatio = ratio3x4, spacingPx = thorSpacing)
        assertTrue("width must stay positive", size.width > 0)
        assertTrue("height must stay positive", size.height > 0)
    }
}
