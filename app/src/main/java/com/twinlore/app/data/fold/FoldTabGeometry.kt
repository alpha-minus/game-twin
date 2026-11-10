package com.twinlore.app.data.fold

/** Absolute window coordinates for a TOP|LEFT-gravity overlay window. */
data class TabPlacement(val x: Int, val y: Int)

/** A position as a fraction of each travel axis, both in 0..1. */
data class TabFraction(val x: Float, val y: Float)

/**
 * Where the floating tab sits, and where a finger just put it.
 *
 * The tab may hang off an edge by up to [OVERHANG] of itself, so it can be
 * tucked mostly out of the way and still be found and pulled back. That is
 * what the fractions measure: 0f is as far off the leading edge as it may go,
 * 1f as far off the trailing one, and the visible sliver at either extreme is
 * the rest of the tab.
 *
 * The window is added with `Gravity.TOP or Gravity.LEFT` and positioned
 * absolutely, so the numbers here are the numbers WindowManager receives and
 * RTL resolution never enters into it. The window also carries
 * FLAG_LAYOUT_NO_LIMITS, which is what lets a negative coordinate mean "off
 * the edge" rather than "clamped to zero".
 */
object FoldTabGeometry {

    /**
     * How much of the tab may leave the screen, as a fraction of its own size.
     *
     * Half would put the icon's centre exactly on the edge. A little less
     * leaves the centre on screen, which matters because the centre is what
     * the finger goes for and what the eye finds.
     */
    const val OVERHANG = 0.45f

    fun placement(
        xFraction: Float,
        yFraction: Float,
        displayWidthPx: Int,
        displayHeightPx: Int,
        tabWidthPx: Int,
        tabHeightPx: Int,
    ): TabPlacement {
        val overhangX = (tabWidthPx * OVERHANG).toInt()
        val overhangY = (tabHeightPx * OVERHANG).toInt()
        // Travel now runs from one overhang to the other, so it is the display
        // plus both overhangs less the tab itself.
        val horizontalTravel = (displayWidthPx - tabWidthPx + overhangX * 2).coerceAtLeast(0)
        val verticalTravel = (displayHeightPx - tabHeightPx + overhangY * 2).coerceAtLeast(0)
        return TabPlacement(
            x = -overhangX + (horizontalTravel * xFraction.coerceIn(0f, 1f)).toInt(),
            y = -overhangY + (verticalTravel * yFraction.coerceIn(0f, 1f)).toInt(),
        )
    }

    fun fractionFromTouch(
        rawX: Float,
        rawY: Float,
        displayWidthPx: Int,
        displayHeightPx: Int,
        tabWidthPx: Int,
        tabHeightPx: Int,
    ): TabFraction {
        val overhangX = (tabWidthPx * OVERHANG).toInt()
        val overhangY = (tabHeightPx * OVERHANG).toInt()
        val horizontalTravel = displayWidthPx - tabWidthPx + overhangX * 2
        val verticalTravel = displayHeightPx - tabHeightPx + overhangY * 2
        // The finger holds the middle of the tab, so subtract half of it, then
        // shift by the overhang the placement above starts from.
        return TabFraction(
            x = axisFraction(rawX - tabWidthPx / 2f + overhangX, horizontalTravel),
            y = axisFraction(rawY - tabHeightPx / 2f + overhangY, verticalTravel),
        )
    }

    private fun axisFraction(offset: Float, travel: Int): Float =
        if (travel <= 0) 0.5f else (offset / travel).coerceIn(0f, 1f)
}
