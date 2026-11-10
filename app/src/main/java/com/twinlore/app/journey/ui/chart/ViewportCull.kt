package com.twinlore.app.journey.ui.chart

import androidx.compose.ui.geometry.Offset

/** An axis-aligned rectangle in the chart's virtual space. */
data class VirtualRect(val left: Float, val top: Float, val right: Float, val bottom: Float)

/**
 * Bounds a zoomed-in chart's per-frame draw cost to what is actually on screen, which is what
 * makes raising [com.twinlore.app.journey.domain.IconBudget]'s cap for a long or dense journey
 * safe: the layout may place 400 icons, but a frame only ever pays to draw the handful inside the
 * viewport.
 *
 * [visibleRect] inverts the same transform [ChartZoom.toVirtual] does for a tap: `screen = origin
 * + fit * (offset + zoom * virtual)`. The letterboxing is centred on both axes, so the canvas shows
 * the whole `(0,0)..(viewportWidth,viewportHeight)` stage box *plus* a margin of stage on whichever
 * axis the canvas is roomier than the stage's aspect ratio: a 1060x600 stage on a portrait canvas is
 * fitted by its width, and everything above and below it is still on screen. Culling against the
 * fitted content box alone therefore hid icons the user could see - by three quarters of the height
 * on a phone-shaped canvas - so [stageViewport] works out the stage box the canvas really shows and
 * [visibleRect] maps that back through [view].
 *
 * The viewport is the *unstretched* 1060x600 stage even for a wide chart: that box is what the
 * letterbox fits, and a wide chart's extra width is content overflowing it, which the [view]'s own
 * offset and zoom already describe.
 */
object ViewportCull {

    /**
     * The stage-space rectangle a [canvasWidth]x[canvasHeight] canvas shows of a
     * [stageWidth]x[stageHeight] stage, matching `JourneyChart`'s `withStage`: fit by the tighter
     * axis, centre the result, so the visible box is the stage's centre plus half the canvas in
     * fitted units on each side.
     *
     * A canvas of unknown size (a fitted export frame, the render tests) is taken to fit the stage
     * exactly, which is what those callers draw into; a followed export frame passes its real
     * 1920x1080, whose letterbox is the stage's own.
     */
    fun stageViewport(
        viewportWidth: Float,
        viewportHeight: Float,
        canvasWidth: Float,
        canvasHeight: Float,
    ): VirtualRect {
        if (canvasWidth <= 0f || canvasHeight <= 0f || viewportWidth <= 0f || viewportHeight <= 0f) {
            return VirtualRect(0f, 0f, viewportWidth, viewportHeight)
        }
        val fit = minOf(canvasWidth / viewportWidth, canvasHeight / viewportHeight)
        if (fit <= 0f) return VirtualRect(0f, 0f, viewportWidth, viewportHeight)
        val halfW = canvasWidth / (2f * fit)
        val halfH = canvasHeight / (2f * fit)
        return VirtualRect(
            left = viewportWidth / 2f - halfW,
            top = viewportHeight / 2f - halfH,
            right = viewportWidth / 2f + halfW,
            bottom = viewportHeight / 2f + halfH,
        )
    }

    /**
     * The virtual-space rectangle [view] currently shows of a [viewportWidth]x[viewportHeight]
     * stage on a [canvasWidth]x[canvasHeight] canvas. At the overview view every icon is on screen,
     * so callers should skip this (and the [intersects] check) entirely rather than call it just to
     * get everything back.
     */
    fun visibleRect(
        view: ChartZoom,
        viewportWidth: Float,
        viewportHeight: Float,
        canvasWidth: Float = 0f,
        canvasHeight: Float = 0f,
    ): VirtualRect {
        val stage = stageViewport(viewportWidth, viewportHeight, canvasWidth, canvasHeight)
        val topLeft = view.toVirtual(Offset(stage.left, stage.top))
        val bottomRight = view.toVirtual(Offset(stage.right, stage.bottom))
        return VirtualRect(topLeft.x, topLeft.y, bottomRight.x, bottomRight.y)
    }

    /**
     * Whether a circle drawn at ([x], [y]) with radius [r] touches [rect], padded by [allowance]
     * virtual units on every side to cover what is drawn around the circle itself (a leader line
     * back to an anchor, a caption) rather than just the disc.
     */
    fun intersects(rect: VirtualRect, x: Float, y: Float, r: Float, allowance: Float = 0f): Boolean {
        val pad = r + allowance
        return x + pad >= rect.left && x - pad <= rect.right &&
            y + pad >= rect.top && y - pad <= rect.bottom
    }
}
