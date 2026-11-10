package com.twinlore.app.journey.export

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The deterministic half of the video export: how many frames a journey is worth, what progress
 * each of them draws at, and that the frame driver walks exactly that sequence. Nothing here
 * touches MediaCodec, a Surface or the clock, which is the entire point: the encoder is device
 * verified, the timeline is unit verified.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class JourneyExportTimelineTest {

    @Test
    fun `frame count at 1x is fifteen seconds plus the hold tail`() {
        assertEquals(480, ExportTimeline.frameCount(1f))
    }

    @Test
    fun `frame count at 2x halves the reveal but keeps the hold tail`() {
        assertEquals(255, ExportTimeline.frameCount(2f))
    }

    @Test
    fun `frame count at half speed doubles the reveal`() {
        assertEquals(930, ExportTimeline.frameCount(0.5f))
    }

    @Test
    fun `a non positive or non finite speed falls back to 1x`() {
        assertEquals(ExportTimeline.frameCount(1f), ExportTimeline.frameCount(0f))
        assertEquals(ExportTimeline.frameCount(1f), ExportTimeline.frameCount(-2f))
        assertEquals(ExportTimeline.frameCount(1f), ExportTimeline.frameCount(Float.NaN))
    }

    @Test
    fun `the first frame draws nothing revealed`() {
        assertEquals(0f, ExportTimeline.progressForFrame(0, 1f), 0f)
    }

    @Test
    fun `the last reveal frame is fully revealed`() {
        assertEquals(1f, ExportTimeline.progressForFrame(449, 1f), 1e-3f)
    }

    @Test
    fun `progress never goes backwards`() {
        listOf(0.5f, 1f, 2f).forEach { speed ->
            var previous = -1f
            for (i in 0 until ExportTimeline.frameCount(speed)) {
                val p = ExportTimeline.progressForFrame(i, speed)
                assertTrue(
                    "frame $i at ${speed}x went backwards: $p after $previous",
                    p >= previous,
                )
                previous = p
            }
        }
    }

    @Test
    fun `every frame stays inside zero to one`() {
        listOf(0.5f, 1f, 2f).forEach { speed ->
            for (i in -3 until ExportTimeline.frameCount(speed) + 3) {
                val p = ExportTimeline.progressForFrame(i, speed)
                assertTrue("frame $i at ${speed}x was $p", p in 0f..1f)
            }
        }
    }

    @Test
    fun `the hold tail sits at exactly one`() {
        val total = ExportTimeline.frameCount(1f)
        for (i in total - ExportTimeline.FPS * ExportTimeline.HOLD_SECONDS until total) {
            assertEquals("frame $i", 1f, ExportTimeline.progressForFrame(i, 1f), 0f)
        }
    }

    @Test
    fun `the driver renders every frame at its timeline progress`() = runTest {
        val rendered = mutableListOf<Pair<Int, Float>>()
        ExportTimeline.forEachFrame(speed = 2f, onProgress = { _, _ -> }) { index, progress ->
            rendered += index to progress
        }

        val total = ExportTimeline.frameCount(2f)
        assertEquals(total, rendered.size)
        rendered.forEachIndexed { i, (index, progress) ->
            assertEquals(i, index)
            assertEquals(ExportTimeline.progressForFrame(i, 2f), progress, 0f)
        }
    }

    @Test
    fun `the driver reports one based progress against the total`() = runTest {
        val reports = mutableListOf<Pair<Int, Int>>()
        ExportTimeline.forEachFrame(speed = 2f, onProgress = { done, total -> reports += done to total }) { _, _ -> }

        val total = ExportTimeline.frameCount(2f)
        assertEquals(total, reports.size)
        assertEquals(1 to total, reports.first())
        assertEquals(total to total, reports.last())
    }
}
