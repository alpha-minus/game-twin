package com.twinlore.app.data.fold

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The tab floats freely and may hang off an edge by [FoldTabGeometry.OVERHANG]
 * of itself, so a position is an (x, y) fraction of a travel that runs from one
 * overhang to the other. The numbers here are the actual coordinates handed to
 * WindowManager for a TOP|LEFT-gravity window. Display is 1920x1080, tab 96x96.
 */
class FoldTabGeometryTest {

    private val dw = 1920
    private val dh = 1080
    private val tw = 96
    private val th = 96
    private val overhang = (96 * FoldTabGeometry.OVERHANG).toInt()

    @Test
    fun `centre of the screen is half of each travel axis`() {
        val p = FoldTabGeometry.placement(0.5f, 0.5f, dw, dh, tw, th)
        assertEquals((dw - tw) / 2, p.x)
        assertEquals((dh - th) / 2, p.y)
    }

    @Test
    fun `each corner hangs off by the overhang, not flush`() {
        assertEquals(
            TabPlacement(-overhang, -overhang),
            FoldTabGeometry.placement(0f, 0f, dw, dh, tw, th),
        )
        assertEquals(
            TabPlacement(dw - tw + overhang, dh - th + overhang),
            FoldTabGeometry.placement(1f, 1f, dw, dh, tw, th),
        )
    }

    // The point of the overhang: tucked as far as it goes, more than half the
    // tab is still on screen, so there is something to see and something to
    // pull back. A tab that could leave entirely would be a lost tab.
    @Test
    fun `at either extreme most of the tab is still on screen`() {
        val left = FoldTabGeometry.placement(0f, 0.5f, dw, dh, tw, th)
        val visibleAtLeft = tw + left.x
        assertTrue("$visibleAtLeft of $tw visible", visibleAtLeft > tw / 2)

        val right = FoldTabGeometry.placement(1f, 0.5f, dw, dh, tw, th)
        val visibleAtRight = dw - right.x
        assertTrue("$visibleAtRight of $tw visible", visibleAtRight > tw / 2)
    }

    @Test
    fun `the axes are independent`() {
        val p = FoldTabGeometry.placement(0.25f, 0.75f, dw, dh, tw, th)
        val travelX = dw - tw + overhang * 2
        val travelY = dh - th + overhang * 2
        assertEquals(-overhang + (travelX * 0.25f).toInt(), p.x)
        assertEquals(-overhang + (travelY * 0.75f).toInt(), p.y)
    }

    @Test
    fun `out of range fractions clamp to the overhang, not past it`() {
        assertEquals(
            TabPlacement(-overhang, -overhang),
            FoldTabGeometry.placement(-3f, -3f, dw, dh, tw, th),
        )
        assertEquals(
            TabPlacement(dw - tw + overhang, dh - th + overhang),
            FoldTabGeometry.placement(7f, 7f, dw, dh, tw, th),
        )
    }

    // A tab wider than its display has nowhere sensible to sit, so the middle
    // of the travel puts its own middle at the middle of the screen and lets
    // it hang off both sides equally. Not a case any real size produces; it is
    // here because the arithmetic must not produce a wild number if one does.
    @Test
    fun `a tab larger than the display centres rather than lurching to a corner`() {
        val p = FoldTabGeometry.placement(0.5f, 0.5f, 100, 50, 200, 200)
        assertEquals(100 / 2, p.x + 200 / 2)
        assertEquals(50 / 2, p.y + 200 / 2)
    }

    @Test
    fun `portrait works the same way`() {
        val p = FoldTabGeometry.placement(1f, 0.5f, 1080, 1920, tw, th)
        assertEquals(1080 - tw + overhang, p.x)
        assertEquals((1920 - th) / 2, p.y)
    }

    @Test
    fun `a touch maps to a fraction with the tab centred on the finger`() {
        val f = FoldTabGeometry.fractionFromTouch(960f, 540f, dw, dh, tw, th)
        val travelX = dw - tw + overhang * 2
        val travelY = dh - th + overhang * 2
        assertEquals((960f - tw / 2f + overhang) / travelX, f.x, 1e-4f)
        assertEquals((540f - th / 2f + overhang) / travelY, f.y, 1e-4f)
    }

    @Test
    fun `a touch past any side clamps into range`() {
        val low = FoldTabGeometry.fractionFromTouch(-500f, -500f, dw, dh, tw, th)
        assertEquals(0f, low.x, 1e-4f)
        assertEquals(0f, low.y, 1e-4f)

        val high = FoldTabGeometry.fractionFromTouch(9000f, 9000f, dw, dh, tw, th)
        assertEquals(1f, high.x, 1e-4f)
        assertEquals(1f, high.y, 1e-4f)
    }

    @Test
    fun `zero travel yields a stable half rather than a divide by zero`() {
        // Tab and display the same size, and the overhangs cancelled out by a
        // tab big enough to fill the travel they add.
        val f = FoldTabGeometry.fractionFromTouch(0f, 0f, 10, 10, 100, 100)
        assertEquals(0.5f, f.x, 1e-4f)
        assertEquals(0.5f, f.y, 1e-4f)
    }

    @Test
    fun `placement round trips through fractionFromTouch`() {
        val p = FoldTabGeometry.placement(0.3f, 0.7f, dw, dh, tw, th)
        val back = FoldTabGeometry.fractionFromTouch(
            p.x + tw / 2f, p.y + th / 2f, dw, dh, tw, th,
        )
        assertEquals(0.3f, back.x, 1e-3f)
        assertEquals(0.7f, back.y, 1e-3f)
    }
}
