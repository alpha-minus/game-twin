package com.twinlore.app.journey.ui.chart

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/**
 * The settled-icon layer: every badge and medallion that has finished appearing, composited once
 * into an offscreen bitmap and blitted back as a single image per frame.
 *
 * The icons are the bulk of what a frame records - a dense journey draws hundreds of them, each a
 * halo, a plate, an image, a ring and a chip - and almost all of them are static: a badge pops in
 * over half a second and then never changes again for the life of the chart. This layer is where
 * they retire to. Per frame the renderer adds any icon that has just settled, blits the bitmap,
 * and live-draws only what still moves: icons mid-pop, and the cursor ring.
 *
 * The bitmap is in CANVAS pixels with the stage transform baked in, which is why it only engages
 * at the overview ([ChartZoom.isOverview]): that is the one view whose transform holds still from
 * frame to frame, and it is also the view that shows every icon at once - zoomed in, the follow
 * camera moves the transform every frame and viewport culling already bounds the live cost.
 * [ensure] clears the layer whenever the transform or the canvas it was built for changes.
 *
 * Lifetime is owned by the chart composable: it remembers one per (journey, layout, style,
 * bitmaps, pop epoch), so a replay, a scrub backwards, a theme change or the art arriving all
 * start a fresh layer rather than trusting invalidation logic to notice.
 */
class IconLayer(nodeCount: Int, medalCount: Int) {

    /** Which icons are already composited; the renderer skips these in the live pass. */
    val nodeDrawn = BooleanArray(nodeCount)
    val medalDrawn = BooleanArray(medalCount)

    var bitmap: ImageBitmap? = null
        private set
    private var canvas: Canvas? = null
    private val scope = CanvasDrawScope()

    /** The transform the bitmap was drawn under: virtual -> pixel is `p * k + t`. */
    var k = 0f
        private set
    var tx = 0f
        private set
    var ty = 0f
        private set

    /**
     * Makes the layer usable for a canvas of [widthPx] x [heightPx] under the given transform,
     * clearing it when either has changed. Returns false when the geometry is degenerate (a
     * zero-sized canvas), in which case the frame should draw without the layer.
     */
    fun ensure(widthPx: Int, heightPx: Int, k: Float, tx: Float, ty: Float): Boolean {
        if (widthPx <= 0 || heightPx <= 0 || k <= 0f) return false
        val current = bitmap
        if (current == null || current.width != widthPx || current.height != heightPx) {
            val fresh = ImageBitmap(widthPx, heightPx)
            bitmap = fresh
            canvas = Canvas(fresh)
            reset(k, tx, ty)
            return true
        }
        if (k != this.k || tx != this.tx || ty != this.ty) {
            current.asAndroidBitmap().eraseColor(android.graphics.Color.TRANSPARENT)
            reset(k, tx, ty)
        }
        return true
    }

    /**
     * Draws into the layer in the chart's virtual coordinates, through the same transform the
     * live canvas is using - so an icon added here lands on exactly the pixels it was drawn on
     * live the frame before. [density] must be the live scope's (the renderer's text sizes go
     * through it), which is why the caller passes it rather than this class defaulting one.
     */
    fun update(density: Density, layoutDirection: LayoutDirection, block: DrawScope.() -> Unit) {
        val target = canvas ?: return
        val b = bitmap ?: return
        scope.draw(density, layoutDirection, target, Size(b.width.toFloat(), b.height.toFloat())) {
            withTransform({
                translate(tx, ty)
                scale(k, k, pivot = Offset.Zero)
            }) { block() }
        }
    }

    private fun reset(k: Float, tx: Float, ty: Float) {
        this.k = k
        this.tx = tx
        this.ty = ty
        nodeDrawn.fill(false)
        medalDrawn.fill(false)
    }
}
