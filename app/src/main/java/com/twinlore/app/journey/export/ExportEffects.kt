package com.twinlore.app.journey.export

import com.twinlore.app.journey.domain.ChartLayout
import com.twinlore.app.journey.domain.model.AwardKind
import com.twinlore.app.journey.ui.chart.ChartPaths
import com.twinlore.app.journey.ui.chart.JourneyChartRenderer
import kotlin.math.ceil

/**
 * When every badge pops, every award bursts and every stage flash fires, across a whole export.
 *
 * On screen these come from [com.twinlore.app.journey.ui.chart.JourneyEffectClock], which stamps
 * "this icon appeared now" off wall-clock nanos the first time the draw pass asks about one. That
 * is correct on a device and unusable here: a shared video has to be the same file every run, on
 * any machine, whatever the encoder's throughput was that day - so for a long time an export simply
 * turned the effects off and shipped a clip with none of what the watcher saw.
 *
 * An export does not need a wall clock. It knows each frame's progress ([ExportTimeline]) and its
 * own fixed cadence, so "seconds since this icon was revealed" is a pure function of the frame
 * index. This is the same problem the follow camera had and the same answer: see [ExportCamera].
 *
 * Worked out once per run, before a frame is drawn, for two reasons. The cheap one is cost - the
 * reveal walk measures the curve, and doing it inside the render loop would measure it twice per
 * frame. The one that matters is that the award flash - and, the same way, the floating "+N" - is a
 * *decision* rather than a value: whether a medal gets its flash depends on which medals fired
 * before it (see [JourneyChartRenderer.WASH_COOLDOWN_SECONDS]), and whether a badge gets its "+N"
 * depends on which badges fired before it (see
 * [JourneyChartRenderer.FLOATING_POINTS_COOLDOWN_SECONDS]), so evaluating either "at frame i" on
 * demand would either re-derive the whole history each time or smuggle a running clock back into
 * the render loop, which is exactly the mutable, order-dependent state this class exists to keep
 * out of the pixels.
 */
class ExportEffects private constructor(
    /** Frame each badge first appeared on, [NEVER] for one this timeline never reveals. */
    private val nodeFrame: IntArray,
    /** Frame each award first crossed the sweep. */
    private val medalFrame: IntArray,
    /** Frame each award's stage flash fired, or [SUPPRESSED] for one the cooldown swallowed. */
    private val washFrame: IntArray,
    /** Frame each badge's floating "+N" fired, or [SUPPRESSED] for one the cooldown swallowed. */
    private val pointsFrame: IntArray,
) {

    /**
     * The effect seconds every icon reports on frame [frameIndex], in the shape the renderer takes
     * them: one lambda per effect, exactly as the live chart hands it its clock.
     */
    fun at(frameIndex: Int): Frame = Frame(frameIndex)

    /** One frame's reading of the plan. Cheap enough to build per frame; nothing here measures. */
    inner class Frame internal constructor(private val index: Int) {
        /**
         * Seconds since each badge was revealed. Feeds [nodeAppearProgress] and nothing else - the
         * floating "+N" used to read this too and now has its own throttled clock, [pointsSeconds],
         * for the same reason the live [com.twinlore.app.journey.ui.chart.JourneyEffectClock] keeps
         * the two apart: a pop-in that shared the "+N"'s cooldown would leave a throttled badge
         * sitting on the curve having never grown to size.
         */
        val nodeSeconds: (Int) -> Float = { i -> secondsSince(nodeFrame, i, index) }

        /**
         * Seconds since each badge's floating "+N" fired, which is not the same question as
         * [nodeSeconds]: a badge that arrived inside the cooldown of the previous pop never starts
         * one and reports [OFF] for the whole clip. See
         * [JourneyChartRenderer.FLOATING_POINTS_COOLDOWN_SECONDS].
         */
        val pointsSeconds: (Int) -> Float = { i -> secondsSince(pointsFrame, i, index) }

        /** Seconds since each award crossed the sweep, driving its confetti. */
        val medalSeconds: (Int) -> Float = { i -> secondsSince(medalFrame, i, index) }

        /**
         * Seconds since each award's full-stage flash fired, which is not the same question as
         * [medalSeconds]: an award that arrived inside the cooldown of the previous flash never
         * starts one and reports [OFF] for the whole clip.
         */
        val medalWashSeconds: (Int) -> Float = { i -> secondsSince(washFrame, i, index) }

        /** How far into its pop-in each badge is; 1 for a settled one. */
        val nodeAppearProgress: (Int) -> Float =
            { i -> JourneyChartRenderer.popProgress(secondsSince(nodeFrame, i, index)) }
    }

    /**
     * Export seconds between [frameIndex] and the frame the icon was stamped on, or [OFF] when it
     * has no stamp - which is what the renderer reads as "no effect", exactly as a live clock's
     * negative does.
     *
     * A frame before the stamp reports [OFF] too. The export never asks (it walks forward), but a
     * plan is a plain function of its arguments and "minus a fifth of a second since it appeared"
     * is not a thing any effect should be handed.
     */
    private fun secondsSince(stamps: IntArray, index: Int, frameIndex: Int): Float {
        val at = stamps.getOrElse(index) { NEVER }
        if (at < 0 || frameIndex < at) return OFF
        return (frameIndex - at) / ExportTimeline.FPS.toFloat()
    }

    companion object {
        /** What an icon with no live effect reports; the renderer's own "effects off" value. */
        const val OFF = -1f

        /** No frame revealed this icon. */
        private const val NEVER = -1

        /**
         * This award's flash, or this badge's "+N", lost to the cooldown, permanently - see [plan].
         * Shared between [washFrame] and [pointsFrame]: the two arrays never share an index space,
         * so one sentinel for "lost its slot" is unambiguous in both.
         */
        private const val SUPPRESSED = -2

        /**
         * Quiet time between two stage flashes, in frames. The live clock compares nanos against
         * [JourneyChartRenderer.WASH_COOLDOWN_SECONDS]; at the export's fixed cadence the same
         * interval is a count of frames, and counting them is what keeps a float clock out of it.
         */
        private val COOLDOWN_FRAMES =
            ceil(JourneyChartRenderer.WASH_COOLDOWN_SECONDS * ExportTimeline.FPS).toInt()

        /**
         * Quiet time between two "+N" pops, in frames - the same conversion as [COOLDOWN_FRAMES],
         * against [JourneyChartRenderer.FLOATING_POINTS_COOLDOWN_SECONDS] instead.
         */
        private val POINTS_COOLDOWN_FRAMES =
            ceil(JourneyChartRenderer.FLOATING_POINTS_COOLDOWN_SECONDS * ExportTimeline.FPS).toInt()

        /**
         * Walks [frames] emitted frames of [layout] and records the frame each icon appears on.
         *
         * [speed] is the playback speed the same run's [ExportTimeline] is walked at, so the plan
         * and the renderer always read the same instant of the journey - the same contract
         * [ExportCamera.path] takes it under.
         *
         * Effect seconds count the *video's* own time (one frame is 1/[ExportTimeline.FPS] of a
         * second) rather than a fraction of playback progress. The two are the same thing at
         * speed 1 and deliberately not at any other: on screen the clock is real time whatever
         * speed playback runs at, so a 2x journey's confetti is over in the same second of footage
         * it always was. It is also the only reading that survives the hold tail, where progress
         * stops at 1 and thirty frames of video keep coming - the last award's burst plays out
         * there instead of freezing on the frame the journey landed.
         */
        fun plan(
            layout: ChartLayout,
            frames: Int,
            speed: Float = 1f,
            paths: ChartPaths = ChartPaths(layout),
        ): ExportEffects {
            val nodeFrame = IntArray(layout.nodes.size) { NEVER }
            val medalFrame = IntArray(layout.medals.size) { NEVER }
            val washFrame = IntArray(layout.medals.size) { NEVER }
            val pointsFrame = IntArray(layout.nodes.size) { NEVER }
            var pendingNodes = nodeFrame.size
            var pendingMedals = medalFrame.size
            // The frame the last flash that actually fired started on. The cooldown is measured
            // from a flash rather than from an award, exactly as the live clock measures it.
            var lastWash = NEVER
            // A mastery's own cooldown clock, separate from [lastWash] - see the mastery branch
            // below and [JourneyChartRenderer.WASH_COOLDOWN_SECONDS] for why.
            var lastMasteryWash = NEVER
            // Same idea for the "+N": the frame the last pop that actually fired started on.
            var lastPoints = NEVER
            for (i in 0 until frames) {
                // Everything is stamped: the rest of the clip reveals nothing new, and on a
                // compare chart each of these walks costs a path bisection. Always true through
                // the hold tail, whose frames all sit at progress 1.
                if (pendingNodes == 0 && pendingMedals == 0) break
                val progress = ExportTimeline.progressForFrame(i, speed)
                JourneyChartRenderer.forEachEffectReveal(
                    layout = layout,
                    paths = paths,
                    progress = progress,
                    onNode = { index ->
                        if (nodeFrame[index] == NEVER) {
                            nodeFrame[index] = i
                            pendingNodes--
                            // The "+N", decided the same frame the badge is revealed and never
                            // revised - not restricted to the main player: every badge already
                            // throws its own number on screen, and every award's flash shares the
                            // same shape now, so rivals share the one cooldown pool on both rather
                            // than being skipped from either. See
                            // [JourneyChartRenderer.FLOATING_POINTS_COOLDOWN_SECONDS].
                            if (lastPoints == NEVER || i - lastPoints >= POINTS_COOLDOWN_FRAMES) {
                                pointsFrame[index] = i
                                lastPoints = i
                            } else {
                                // Permanently, like the flash's suppressed set: a badge that lost
                                // its pop must not be handed one late.
                                pointsFrame[index] = SUPPRESSED
                            }
                        }
                    },
                    onMedal = { index ->
                        if (medalFrame[index] == NEVER) {
                            medalFrame[index] = i
                            pendingMedals--
                        }
                        // The flash, decided on the frame the award is revealed and never revised.
                        // A rival's award competes for the same slot as the main player's now,
                        // rather than being skipped before it ever reaches the cooldown: the flash
                        // means "somebody just mastered a game", not "the main player just did", so
                        // whichever award actually landed first is the one that earns the quiet
                        // time everyone after it shares - main player's or rival's alike. See
                        // [JourneyChartRenderer.WASH_COOLDOWN_SECONDS] for why a shared pool won
                        // over one cooldown per player.
                        //
                        // One exception: a MASTERED award is judged against [lastMasteryWash], not
                        // [lastWash] - it must never lose its flash to a lesser award that merely
                        // happened to land a moment earlier, but it still respects a cooldown
                        // against ANOTHER mastery, so a run of several in one sitting still throws
                        // one flash rather than a strobe of them. See
                        // [JourneyChartRenderer.WASH_COOLDOWN_SECONDS]'s own doc for the full case.
                        if (washFrame[index] == NEVER) {
                            val isMastery = layout.medals[index].medal.kind == AwardKind.MASTERED
                            val last = if (isMastery) lastMasteryWash else lastWash
                            if (last == NEVER || i - last >= COOLDOWN_FRAMES) {
                                washFrame[index] = i
                                lastWash = i
                                if (isMastery) lastMasteryWash = i
                            } else {
                                // Permanently, like the live clock's suppressed set: an award that
                                // lost its flash must not be handed one when the cooldown expires
                                // several seconds after the moment it was celebrating.
                                washFrame[index] = SUPPRESSED
                            }
                        }
                    },
                )
            }
            return ExportEffects(nodeFrame, medalFrame, washFrame, pointsFrame)
        }
    }
}
