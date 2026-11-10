package com.twinlore.app.journey.export

import kotlin.math.ceil
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * The export's frame timeline: how long a rendered journey runs, and what playback progress each
 * frame draws at.
 *
 * Deliberately pure and clock free. The encoder decides nothing about timing: every frame's
 * position comes from its index, so the same journey exported twice produces the same frames, and
 * the whole thing is unit testable without a codec.
 *
 * A journey reveals over [REVEAL_SECONDS] divided by the playback speed, then holds the finished
 * chart for [HOLD_SECONDS] so a video that loops or ends does not cut on the last unlock.
 */
object ExportTimeline {

    const val FPS = 30
    const val HOLD_SECONDS = 1
    const val REVEAL_SECONDS = 15f

    /** Total frames written for [speed], reveal plus the hold tail. */
    fun frameCount(speed: Float): Int = revealFrames(speed) + HOLD_SECONDS * FPS

    /** Playback progress frame [index] draws at, clamped to 1 through the hold tail. */
    fun progressForFrame(index: Int, speed: Float): Float {
        val last = revealFrames(speed) - 1
        if (last <= 0) return 1f
        return (index.toFloat() / last).coerceIn(0f, 1f)
    }

    /**
     * Walks the whole timeline: reports one based progress, then hands the frame index and its
     * playback progress to [render]. Cancellation is checked once per frame, so cancelling an
     * export stops it within a frame rather than at the end of the video.
     */
    suspend fun forEachFrame(
        speed: Float,
        onProgress: (Int, Int) -> Unit,
        render: suspend (index: Int, progress: Float) -> Unit,
    ) {
        val total = frameCount(speed)
        for (i in 0 until total) {
            currentCoroutineContext().ensureActive()
            onProgress(i + 1, total)
            render(i, progressForFrame(i, speed))
        }
    }

    /** Frames spent revealing the journey, before the hold tail. */
    private fun revealFrames(speed: Float): Int {
        val s = if (speed.isFinite() && speed > 0f) speed else 1f
        return ceil(REVEAL_SECONDS / s * FPS).toInt().coerceAtLeast(1)
    }
}
