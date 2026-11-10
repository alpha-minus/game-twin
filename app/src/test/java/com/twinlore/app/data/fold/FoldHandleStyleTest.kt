package com.twinlore.app.data.fold

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two numbers the folded handle is drawn from.
 *
 * They were two enums, Visible / Faint / Transparent and Small / Medium / Big,
 * and the argument for names over numbers was that "Faint" said what it was
 * where 0.35 did not. What the names could not do is land between two of
 * themselves. The tests that matter here are the snapping (so a slider stops
 * on tidy values without Material drawing fifty tick marks) and the legacy
 * mapping (so an installed copy keeps the handle it was left with).
 */
class FoldHandleStyleTest {

    @Test
    fun `alpha snaps to five percent and clamps to the range`() {
        assertEquals(0.35f, FoldHandleStyle.alpha(0.34f), 1e-4f)
        assertEquals(0.35f, FoldHandleStyle.alpha(0.36f), 1e-4f)
        assertEquals(0.4f, FoldHandleStyle.alpha(0.38f), 1e-4f)
        assertEquals(1f, FoldHandleStyle.alpha(4f), 1e-4f)
        assertEquals(0f, FoldHandleStyle.alpha(-2f), 1e-4f)
    }

    /**
     * A zero is a value, not a floor to round away from. A handle at 0 is
     * invisible and still tappable, which is a real thing to want over a game.
     */
    @Test
    fun `a true zero survives the snapping`() {
        assertEquals(0f, FoldHandleStyle.alpha(0f), 1e-4f)
        assertEquals(0f, FoldHandleStyle.alpha(0.01f), 1e-4f)
    }

    @Test
    fun `a NaN alpha falls back to the default rather than propagating`() {
        assertEquals(FoldHandleStyle.DEFAULT_ALPHA, FoldHandleStyle.alpha(Float.NaN), 1e-4f)
    }

    @Test
    fun `size snaps to two dp and clamps to the range`() {
        assertEquals(56, FoldHandleStyle.sizeDp(56))
        assertEquals(56, FoldHandleStyle.sizeDp(55))
        assertEquals(58, FoldHandleStyle.sizeDp(58))
        // Exactly between two steps, so it rounds up rather than truncating.
        assertEquals(58, FoldHandleStyle.sizeDp(57))
        assertEquals(FoldHandleStyle.MIN_SIZE_DP, FoldHandleStyle.sizeDp(10))
        assertEquals(FoldHandleStyle.MAX_SIZE_DP, FoldHandleStyle.sizeDp(400))
    }

    /**
     * 44dp is the platform's minimum touch target. The handle is the only
     * thing on screen while the panel is down, so the floor is not negotiable
     * however far left the slider is dragged.
     */
    @Test
    fun `the handle can never be smaller than the minimum touch target`() {
        (-100..200).forEach { raw ->
            assertTrue(
                "a $raw dp handle came out below the touch target",
                FoldHandleStyle.sizeDp(raw) >= 44,
            )
        }
    }

    @Test
    fun `the size fraction round trips through the slider`() {
        (FoldHandleStyle.MIN_SIZE_DP..FoldHandleStyle.MAX_SIZE_DP step 2).forEach { dp ->
            val fraction = FoldHandleStyle.sizeFraction(dp)
            assertEquals(
                "a $dp dp handle did not survive the trip through the slider",
                dp,
                FoldHandleStyle.sizeDpFromFraction(fraction),
            )
        }
    }

    @Test
    fun `the ends of the slider are the ends of the range`() {
        assertEquals(FoldHandleStyle.MIN_SIZE_DP, FoldHandleStyle.sizeDpFromFraction(0f))
        assertEquals(FoldHandleStyle.MAX_SIZE_DP, FoldHandleStyle.sizeDpFromFraction(1f))
        assertEquals(0f, FoldHandleStyle.sizeFraction(FoldHandleStyle.MIN_SIZE_DP), 1e-4f)
        assertEquals(1f, FoldHandleStyle.sizeFraction(FoldHandleStyle.MAX_SIZE_DP), 1e-4f)
    }

    @Test
    fun `every step the d-pad walks lands on a value the store will keep`() {
        val step = 1f / FoldHandleStyle.ALPHA_STEP_COUNT
        (0..FoldHandleStyle.ALPHA_STEP_COUNT).forEach { i ->
            val walked = FoldHandleStyle.alpha(i * step)
            assertEquals("step $i moved when it was stored", walked, FoldHandleStyle.alpha(walked), 1e-4f)
        }
        assertEquals(
            (FoldHandleStyle.MAX_SIZE_DP - FoldHandleStyle.MIN_SIZE_DP) / 2,
            FoldHandleStyle.SIZE_STEP_COUNT,
        )
    }

    @Test
    fun `the readouts say what the sliders are set to`() {
        assertEquals("35%", FoldHandleStyle.alphaPercentLabel(0.35f))
        assertEquals("0%", FoldHandleStyle.alphaPercentLabel(0f))
        assertEquals("100%", FoldHandleStyle.alphaPercentLabel(1f))
        assertEquals("56dp", FoldHandleStyle.sizeLabel(56))
    }

    /**
     * The reading size: how big the handle is drawn while the panel is over
     * it, as a fraction of the size above. The default is a full 1f, so the
     * handle keeps the size it was chosen at unless somebody asks for less.
     */
    @Test
    fun `the reading size defaults to no shrink at all`() {
        assertEquals(1f, FoldHandleStyle.DEFAULT_RECESS, 1e-4f)
        assertEquals(1f, FoldHandleStyle.MAX_RECESS, 1e-4f)
    }

    @Test
    fun `the reading size snaps to five percent and clamps to its range`() {
        assertEquals(0.6f, FoldHandleStyle.recess(0.62f), 1e-4f)
        assertEquals(0.65f, FoldHandleStyle.recess(0.64f), 1e-4f)
        assertEquals(FoldHandleStyle.MAX_RECESS, FoldHandleStyle.recess(3f), 1e-4f)
        assertEquals(FoldHandleStyle.MIN_RECESS, FoldHandleStyle.recess(0f), 1e-4f)
        assertEquals(FoldHandleStyle.MIN_RECESS, FoldHandleStyle.recess(-1f), 1e-4f)
    }

    /**
     * The floor is a quarter rather than zero because this scales the mark and
     * not the window: a handle drawn smaller keeps its whole touch target, so
     * the only thing a lower floor would buy is a handle nobody can find.
     */
    @Test
    fun `the reading size never goes below a quarter`() {
        assertEquals(0.25f, FoldHandleStyle.MIN_RECESS, 1e-4f)
        (-20..30).forEach { i ->
            assertTrue(
                "a raw ${i / 10f} came out below the floor",
                FoldHandleStyle.recess(i / 10f) >= FoldHandleStyle.MIN_RECESS,
            )
        }
    }

    @Test
    fun `a NaN reading size falls back to the default`() {
        assertEquals(FoldHandleStyle.DEFAULT_RECESS, FoldHandleStyle.recess(Float.NaN), 1e-4f)
    }

    @Test
    fun `the reading size round trips through the slider`() {
        var scale = FoldHandleStyle.MIN_RECESS
        while (scale <= FoldHandleStyle.MAX_RECESS + 1e-4f) {
            val snapped = FoldHandleStyle.recess(scale)
            assertEquals(
                "a $snapped reading size did not survive the trip through the slider",
                snapped,
                FoldHandleStyle.recessFromFraction(FoldHandleStyle.recessFraction(snapped)),
                1e-4f,
            )
            scale += 0.05f
        }
        assertEquals(FoldHandleStyle.MIN_RECESS, FoldHandleStyle.recessFromFraction(0f), 1e-4f)
        assertEquals(FoldHandleStyle.MAX_RECESS, FoldHandleStyle.recessFromFraction(1f), 1e-4f)
    }

    /** Fifteen 5% steps from a quarter to a whole, which is what the d-pad walks. */
    @Test
    fun `the reading size has a step per five percent of its range`() {
        assertEquals(15, FoldHandleStyle.RECESS_STEP_COUNT)
        val step = 1f / FoldHandleStyle.RECESS_STEP_COUNT
        (0..FoldHandleStyle.RECESS_STEP_COUNT).forEach { i ->
            val walked = FoldHandleStyle.recessFromFraction(i * step)
            assertEquals("step $i moved when it was stored", walked, FoldHandleStyle.recess(walked), 1e-4f)
        }
    }

    @Test
    fun `the reading size reads out as a percentage`() {
        assertEquals("100%", FoldHandleStyle.recessPercentLabel(1f))
        assertEquals("60%", FoldHandleStyle.recessPercentLabel(0.6f))
        assertEquals("25%", FoldHandleStyle.recessPercentLabel(0.1f))
    }

    /** The whole look in one value, every field snapped on the way in. */
    @Test
    fun `a look snaps all three of its numbers`() {
        val look = FoldHandleLook.of(alpha = 0.63f, sizeDp = 4000, recessScale = 0.62f)

        assertEquals(0.65f, look.alpha, 1e-4f)
        assertEquals(FoldHandleStyle.MAX_SIZE_DP, look.sizeDp)
        assertEquals(0.6f, look.recessScale, 1e-4f)
    }

    @Test
    fun `a default look is the defaults`() {
        val look = FoldHandleLook()

        assertEquals(FoldHandleStyle.DEFAULT_ALPHA, look.alpha, 1e-4f)
        assertEquals(FoldHandleStyle.DEFAULT_SIZE_DP, look.sizeDp)
        assertEquals(FoldHandleStyle.DEFAULT_RECESS, look.recessScale, 1e-4f)
    }

    /**
     * The migration. An installed copy has a `fold_dim` of FAINT and a
     * `fold_tab_size` of MEDIUM and no numeric keys at all; without these it
     * would open on the defaults, which for anybody who had chosen otherwise
     * is their handle silently resetting.
     */
    @Test
    fun `a stored enum name reads as the value that name meant`() {
        assertEquals(1f, FoldHandleStyle.alphaFromLegacyName("VISIBLE"))
        assertEquals(0.35f, FoldHandleStyle.alphaFromLegacyName("FAINT"))
        assertEquals(0f, FoldHandleStyle.alphaFromLegacyName("TRANSPARENT"))
        assertEquals(44, FoldHandleStyle.sizeDpFromLegacyName("SMALL"))
        assertEquals(56, FoldHandleStyle.sizeDpFromLegacyName("MEDIUM"))
        assertEquals(72, FoldHandleStyle.sizeDpFromLegacyName("BIG"))
    }

    /**
     * Null rather than a guess. A corrupt string is not evidence about what
     * the user wanted, so it falls through to the default rather than to
     * whichever value it happened to be nearest.
     */
    @Test
    fun `an unknown or missing stored name reads as nothing at all`() {
        assertNull(FoldHandleStyle.alphaFromLegacyName("SPARKLY"))
        assertNull(FoldHandleStyle.alphaFromLegacyName(null))
        assertNull(FoldHandleStyle.sizeDpFromLegacyName("ENORMOUS"))
        assertNull(FoldHandleStyle.sizeDpFromLegacyName(null))
    }

    /**
     * The size default is what "Medium" meant, so a fresh install matches an
     * upgraded one there. The opacity default is not: a fresh handle is fully
     * visible, where the old "Faint" default hid the way back out of Overlay
     * Mode from anyone who had not yet learned where the handle sits.
     */
    @Test
    fun `a fresh handle is fully visible and medium sized`() {
        assertEquals(FoldHandleStyle.alphaFromLegacyName("VISIBLE"), FoldHandleStyle.DEFAULT_ALPHA)
        assertEquals(FoldHandleStyle.sizeDpFromLegacyName("MEDIUM"), FoldHandleStyle.DEFAULT_SIZE_DP)
    }
}
