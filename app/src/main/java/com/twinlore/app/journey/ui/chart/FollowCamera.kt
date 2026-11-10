package com.twinlore.app.journey.ui.chart

import androidx.compose.ui.geometry.Offset
import com.twinlore.app.journey.domain.ChartLayout
import com.twinlore.app.journey.domain.LayoutEngine
import kotlin.math.abs

/**
 * The follow-the-runner camera: where the viewport belongs when the chart is tracking the runner
 * rather than showing the whole journey, and how far one frame moves toward it.
 *
 * Pure floats and [ChartZoom] on purpose. The camera is evaluated inside the draw pass, once per
 * frame, from the same progress value the renderer already reads there, so following costs a
 * redraw and never a recomposition; a Compose `Animatable` per axis would cost both. Being pure
 * also means the clamping and the glide are unit tested rather than eyeballed on a device.
 */
object FollowCamera {

    /** The fixed magnification the camera follows at. */
    const val ZOOM = 2.2f

    /**
     * Zooming out past this abandons the follow and shows the whole chart again: a user pinching
     * back out is asking for the overview, and snapping them back in would fight them.
     */
    const val EXIT_ZOOM = 1.3f

    /**
     * Fraction of the remaining distance covered by one frame at [REFERENCE_FPS]. The glide is
     * expressed per frame because that is how it reads, and applied per second (see [factorFor])
     * because a chart that is only managing 30fps must not also glide at half speed.
     */
    const val LERP = 0.15f

    /** The frame rate [LERP] is quoted at. */
    const val REFERENCE_FPS = 60f

    /**
     * How far the camera may be behind its target before gliding stops being a glide. A scrub
     * across the journey moves the runner further than this in one frame, and following it at
     * [LERP] would fly the whole chart past the viewport for half a second afterwards.
     */
    const val SNAP_FRACTION = 0.4f


    /**
     * The view that centres [runner] (a point in the chart's virtual space) at [zoom], clamped by
     * [ChartZoom.clamped] so the content always covers the viewport. Near an edge the clamp wins
     * and the runner sits off centre, which is what showing empty space instead would cost.
     *
     * [contentWidth] is the chart's own virtual width (see [ChartZoom.contentWidth]): the viewport
     * being centred on is always the 1060x600 stage, but how far the camera may travel is not.
     */
    fun target(
        runner: Offset,
        contentWidth: Float = LayoutEngine.W,
        zoom: Float = ZOOM,
    ): ChartZoom {
        val base = ChartZoom(contentWidth = contentWidth)
        val z = zoom.coerceIn(base.minZoom, ChartZoom.MAX)
        val centre = Offset(LayoutEngine.W / 2f, LayoutEngine.H / 2f)
        return base.copy(zoom = z, offset = centre - runner * z).clamped()
    }

    /**
     * The fraction of the remaining distance to cover in a frame that lasted [dtSeconds].
     *
     * Exponential smoothing, so it composes: two frames of half the length glide exactly as far as
     * one full frame, and the camera reaches the runner in the same wall-clock time whatever the
     * device is managing. A frame long enough for the answer to round to 1 is a stall, and landing
     * on the target is the right thing to do after one.
     */
    fun factorFor(dtSeconds: Float, perFrame: Float = LERP): Float {
        if (dtSeconds <= 0f) return 0f
        val keep = (1f - perFrame.coerceIn(0f, 1f)).toDouble()
        return (1.0 - Math.pow(keep, (dtSeconds * REFERENCE_FPS).toDouble())).toFloat().coerceIn(0f, 1f)
    }

    /**
     * One frame of glide from [current] toward [target]. [factor] 1 lands on the target (used when
     * playback is paused or being scrubbed, where the camera must track the slider exactly);
     * [factorFor] is the playing case.
     *
     * A target further than [SNAP_FRACTION] of the stage away is not glided to at all: that is a
     * scrub or a replay rather than a runner moving, and the intervening frames would be a blur of
     * chart flying past. The result is re-clamped, because an intermediate zoom between two legal
     * views is not automatically a legal view of its own.
     */
    fun step(current: ChartZoom, target: ChartZoom, factor: Float = LERP): ChartZoom {
        if (abs(target.offset.x - current.offset.x) > LayoutEngine.W * SNAP_FRACTION ||
            abs(target.offset.y - current.offset.y) > LayoutEngine.H * SNAP_FRACTION
        ) {
            return target
        }
        val f = factor.coerceIn(0f, 1f)
        return target.copy(
            zoom = lerp(current.zoom, target.zoom, f),
            offset = Offset(
                x = lerp(current.offset.x, target.offset.x, f),
                y = lerp(current.offset.y, target.offset.y, f),
            ),
        ).clamped()
    }

    /**
     * One frame of the end-of-journey glide out to [target]: the same lerp [step] moves with, but
     * never the snap - the whole point of this glide is that the landing is not a cut, and from a
     * follow view on a wide chart the overview is always further away than [SNAP_FRACTION].
     */
    fun glide(current: ChartZoom, target: ChartZoom, factor: Float): ChartZoom {
        val f = factor.coerceIn(0f, 1f)
        return target.copy(
            zoom = lerp(current.zoom, target.zoom, f),
            offset = Offset(
                x = lerp(current.offset.x, target.offset.x, f),
                y = lerp(current.offset.y, target.offset.y, f),
            ),
        ).clamped()
    }

    /** Whether [current] has, for the eye, arrived on [target]. */
    fun arrived(current: ChartZoom, target: ChartZoom): Boolean =
        abs(current.zoom - target.zoom) < 0.01f &&
            abs(current.offset.x - target.offset.x) < 1f &&
            abs(current.offset.y - target.offset.y) < 1f

    /**
     * Whether the camera is actually driving the view right now.
     *
     * Following is a property of a journey *in motion*: the runner is only somewhere in particular
     * while the reveal is running, and a stopped chart has no runner to chase. Without the
     * [playing] gate a paused or finished chart kept drawing through a 2.2x runner-centred camera,
     * which on a wide journey (where [defaultOn] is always true) meant the compact at-rest chart
     * was shown as a magnified slice of itself - the exact opposite of what dropping to the compact
     * layout at rest is for.
     *
     * [suspended] is the user having taken the camera over with a pinch or a drag.
     */
    fun engaged(followMode: Boolean, suspended: Boolean, playing: Boolean): Boolean =
        followMode && !suspended && playing

    /**
     * Whether a journey should open the player already following: always. This used to be a per-layout rule - wide, long-span or dense journeys
     * followed, short compact ones opened fitted - but the split read as inconsistency rather
     * than intent. The signature stays, both so call sites keep reading "the default rule for
     * this journey" and so a future per-layout nuance has its seam back.
     *
     * The stored preference still overrides this once the user touches the toggle, and a
     * stopped journey still never follows regardless (see [engaged]).
     */
    @Suppress("UNUSED_PARAMETER")
    fun defaultOn(layout: ChartLayout, unlockCount: Int): Boolean = true

    private fun lerp(a: Float, b: Float, f: Float): Float = a + (b - a) * f
}
