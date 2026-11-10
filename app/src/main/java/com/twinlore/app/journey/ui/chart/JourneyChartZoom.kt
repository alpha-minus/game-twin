package com.twinlore.app.journey.ui.chart

import androidx.compose.ui.geometry.Offset
import com.twinlore.app.journey.domain.LayoutEngine

/**
 * The chart's zoom and pan, expressed inside the chart's virtual space so the whole thing stays
 * part of the draw transform: vectors and text are re-rasterised at the zoomed scale rather than
 * magnified, and the tap/cursor maths only has to invert one affine.
 *
 * A screen point maps to a virtual point as `screen = origin + fit * (offset + zoom * virtual)`,
 * where `fit`/`origin` are the letterboxing that fits the 1060x600 *viewport* stage into the canvas.
 * That viewport is always [LayoutEngine.W] x [LayoutEngine.H]; a wide chart ([contentWidth] greater
 * than W, see [LayoutEngine.widthFactorFor]) simply does not fit in it, which is the whole point -
 * the extra timeline is explored by panning, by the follow camera, or by zooming out below 1.
 *
 * [offset] is clamped so the content always covers the viewport on an axis where it is bigger, and
 * is centred on an axis where it is smaller.
 */
data class ChartZoom(
    val zoom: Float = MIN,
    val offset: Offset = Offset.Zero,
    /**
     * Width of the virtual space being viewed: [LayoutEngine.W] for an unstretched chart, W times
     * the width factor for a wide one. Part of the view rather than a parameter on every call
     * because the view is remembered, handed to the camera and read back by the gestures, and one
     * of those forgetting which chart it was looking at is a silent pan clamp bug.
     */
    val contentWidth: Float = LayoutEngine.W,
) {

    /**
     * The zoom at which the whole chart is on screen: 1 for an unstretched one, less than 1 for a
     * wide one. This is the floor a pinch can reach, so "zoomed all the way out" always means
     * "showing everything" rather than "showing 1060 units of it".
     */
    val minZoom: Float
        get() = if (contentWidth <= LayoutEngine.W) MIN else LayoutEngine.W / contentWidth

    /** Whether this view is the whole chart: nothing is off screen, so nothing can be panned to. */
    val isOverview: Boolean get() = zoom <= minZoom + 0.001f

    /** The whole chart, centred: what a double tap comes back to and what an overview pinch lands on. */
    fun overview(): ChartZoom = ChartZoom(zoom = minZoom, contentWidth = contentWidth).clamped()

    /** Re-clamps [offset] for the current [zoom] so no content edge can leave the viewport. */
    fun clamped(): ChartZoom {
        val z = zoom.coerceIn(minZoom, MAX)
        return copy(
            zoom = z,
            offset = Offset(
                x = clampAxis(offset.x, LayoutEngine.W, contentWidth * z),
                y = clampAxis(offset.y, LayoutEngine.H, LayoutEngine.H * z),
            ),
        )
    }

    /**
     * One axis of the clamp: content larger than the viewport may slide, but only far enough to
     * keep the viewport covered; content smaller than it (a wide chart zoomed out to its floor)
     * has one legal position, centred.
     */
    private fun clampAxis(value: Float, viewport: Float, content: Float): Float =
        if (content <= viewport) (viewport - content) / 2f else value.coerceIn(viewport - content, 0f)

    /** Scales to [target] while keeping the virtual point currently under [focus] under it. */
    fun zoomedTo(target: Float, focus: Offset): ChartZoom {
        val z = target.coerceIn(minZoom, MAX)
        val virtual = toVirtual(focus)
        return copy(zoom = z, offset = focus - virtual * z).clamped()
    }

    /** Drags the content by [delta] virtual units (as measured before the zoom is applied). */
    fun pannedBy(delta: Offset): ChartZoom = copy(offset = offset + delta).clamped()

    /**
     * One pinch event applied to this view: scale by [gestureZoom] about [focus] (a point in the
     * fitted stage space), then drag by [pan] (already divided by the fit scale, so in stage units).
     *
     * [followMode] carries the overview rule: pinching back *out* past [FollowCamera.EXIT_ZOOM]
     * times the floor lands on the whole chart rather than on an almost-fitted view the follow
     * camera would fight over. That rule is deliberately one-directional. Applied to every event
     * regardless of direction it also cancelled the first steps of a pinch *in*: from the fitted
     * view a pinch grows the zoom a couple of percent per event, every one of those intermediate
     * views is below the exit zoom, and each was snapped straight back - so a fully zoomed-out
     * follow-mode chart could not be zoomed in at all. Judging by [gestureZoom] means the fingers
     * always win while they are down, whichever way they are moving.
     */
    fun pinched(focus: Offset, gestureZoom: Float, pan: Offset, followMode: Boolean): ChartZoom {
        var next = this
        if (gestureZoom != 1f) next = next.zoomedTo(next.zoom * gestureZoom, focus)
        if (!next.isOverview) next = next.pannedBy(pan)
        if (followMode && gestureZoom < 1f && next.zoom < next.minZoom * FollowCamera.EXIT_ZOOM) {
            return overview()
        }
        return next
    }

    /** Maps a point in the fitted stage space to the virtual point drawn there. */
    fun toVirtual(point: Offset): Offset = (point - offset) / zoom

    /** Maps a virtual point to where it is drawn in the fitted stage space. */
    fun toStage(point: Offset): Offset = offset + point * zoom

    /**
     * Nudges the view so [point] (virtual) sits inside the viewport with [margin] to spare. Used
     * when the D-pad cursor steps onto a badge that the current view has pushed off screen.
     */
    fun panIntoView(point: Offset, margin: Float = 60f): ChartZoom {
        if (isOverview) return this
        val at = toStage(point)
        var dx = 0f
        var dy = 0f
        if (at.x < margin) dx = margin - at.x
        if (at.x > LayoutEngine.W - margin) dx = (LayoutEngine.W - margin) - at.x
        if (at.y < margin) dy = margin - at.y
        if (at.y > LayoutEngine.H - margin) dy = (LayoutEngine.H - margin) - at.y
        if (dx == 0f && dy == 0f) return this
        return pannedBy(Offset(dx, dy))
    }

    companion object {
        /** The zoom an unstretched chart is fitted at; a wide one's floor is [minZoom]. */
        const val MIN = 1f
        const val MAX = 8f
        /** What a double tap zooms to, and what a second double tap comes back from. */
        const val DOUBLE_TAP = 2.5f
        val NONE = ChartZoom()

        /** The whole chart of [contentWidth] virtual units, which is where every view starts. */
        fun overviewFor(contentWidth: Float): ChartZoom =
            ChartZoom(contentWidth = contentWidth).overview()
    }
}
