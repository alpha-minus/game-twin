package com.twinlore.app.journey.export

import com.twinlore.app.journey.domain.ChartLayout
import com.twinlore.app.journey.ui.chart.ChartPaths
import com.twinlore.app.journey.ui.chart.ChartZoom
import com.twinlore.app.journey.ui.chart.FollowCamera
import com.twinlore.app.journey.ui.chart.JourneyChartRenderer

/**
 * Where the exported camera is on each frame, when the video follows the runner.
 *
 * The live camera glides toward its target by [FollowCamera.factorFor], which takes the real time
 * the last frame took - correct on a device, and unusable here: a shared video has to be the same
 * file whatever the encoder's throughput was that run. So the export integrates the same pure glide
 * at its own fixed cadence, one step per emitted frame, and the path is a function of the journey
 * alone.
 *
 * Computed once per export rather than per frame. Not for the arithmetic, which is nothing: the
 * glide is a recurrence, so frame i cannot be evaluated without every frame before it, and asking
 * for "the view at frame i" on its own would either be quadratic or would smuggle a running camera
 * back into the render loop - which is exactly the mutable, order-dependent state this object
 * exists to keep out of the pixels.
 */
object ExportCamera {

    /**
     * The progress at which the journey has landed and the camera lets the runner go.
     *
     * Mirrors `JourneyChart`'s own `END_GLIDE_AT` (private there, and deliberately not shared: the
     * live constant is about a playback value the user can scrub past, this one about the frame the
     * export's hold tail begins on - see [ExportTimeline.HOLD_SECONDS]). Keeping them equal is what
     * makes the clip end the way the watched journey ended.
     */
    const val LANDED = 0.999f

    /**
     * The camera's view for each of [frames] emitted frames, following the runner and then gliding
     * out to the whole chart over the hold tail.
     *
     * [speed] is the playback speed the same run's [ExportTimeline] is walked at: frame i is drawn
     * at [ExportTimeline.progressForFrame], so the camera and the renderer are always reading the
     * same instant of the journey. A caller passing a [frames] that is not [ExportTimeline
     * .frameCount] for that speed simply gets that many steps of the same path, which is what the
     * tests want and what nothing else should ask for.
     *
     * [paths] is accepted so an exporter can hand over the one it is already drawing through; the
     * default builds a private copy, because measuring the runner is the only thing this needs it
     * for and a caller without one should not have to care.
     */
    fun path(
        layout: ChartLayout,
        frames: Int,
        speed: Float = 1f,
        paths: ChartPaths = ChartPaths(layout),
    ): List<ChartZoom> {
        if (frames <= 0) return emptyList()
        val overview = ChartZoom.overviewFor(layout.width)
        // The one number the whole path is glided by, and the only place the export's cadence
        // enters the maths: one frame of the encoder's fixed 30fps, never a measured frame time.
        val factor = FollowCamera.factorFor(1f / ExportTimeline.FPS)
        // Seeded on the target rather than on the overview, so the first frame is already beside
        // the runner. Gliding in from the fitted view would open every followed video with half a
        // second of zoom the watcher never saw: on screen the camera is on the runner from the
        // first frame of playback.
        var view = FollowCamera.target(runnerAt(layout, paths, 0f), layout.width)
        val out = ArrayList<ChartZoom>(frames)
        for (i in 0 until frames) {
            val progress = ExportTimeline.progressForFrame(i, speed)
            view = if (progress >= LANDED) {
                // The landing, as playback plays it: the camera hands the picture back rather than
                // cutting to it, and the clip ends on the whole chart - which is also the picture
                // the still export of the same journey shows. Never [FollowCamera.step] here: the
                // overview is always further away than its snap fraction, so a step would make the
                // landing the one cut this glide exists to avoid.
                FollowCamera.glide(view, overview, factor)
            } else {
                val runner = runnerAt(layout, paths, progress)
                FollowCamera.step(view, FollowCamera.target(runner, layout.width), factor)
            }
            out += view
        }
        return out
    }

    /** The runner's position at [progress], taken from the source the renderer draws it from. */
    private fun runnerAt(layout: ChartLayout, paths: ChartPaths, progress: Float) =
        paths.positionAt(ChartPaths.MAIN, JourneyChartRenderer.revealedLength(layout, paths, progress))
}
