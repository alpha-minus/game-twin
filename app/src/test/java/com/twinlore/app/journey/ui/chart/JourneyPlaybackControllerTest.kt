package com.twinlore.app.journey.ui.chart

import com.twinlore.app.journey.domain.LayoutEngine
import com.twinlore.app.journey.domain.model.AchievementType
import com.twinlore.app.journey.domain.model.GameRef
import com.twinlore.app.journey.domain.model.JourneyFrame
import com.twinlore.app.journey.domain.model.JourneyMode
import com.twinlore.app.journey.domain.model.PlayerJourney
import com.twinlore.app.journey.domain.model.Unlock

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.TestMonotonicFrameClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Playback state machine tests, on a virtual frame clock.
 *
 * The two interruption tests are the regression guard for the bug that scrubbing or replaying
 * mid-playback used to kill the reveal for good: `Animatable.snapTo` evicts the running
 * `animateTo` through the shared `MutatorMutex`, and if that cancellation escapes, the coroutine
 * driving playback ends without its keys changing, so nothing ever restarts it. Here the chart
 * freezes with the button still reading "Pause"; in the test, `play` simply never returns true.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class JourneyPlaybackControllerTest {

    private val pass = 1_000f   // a one-second playback pass keeps the frame count small

    /**
     * A scrub to the left replays the badge pop-in (it is showing unlocks again) but is not a
     * replay of the journey, and the view the user framed - their zoom, where the camera had got
     * to - must survive it. Only [JourneyPlaybackController.restart] resets those.
     */
    @Test
    fun `only a restart counts as a replay`() = runTest {
        val controller = JourneyPlaybackController(0.8f)
        assertEquals(0, controller.replayEpoch)

        controller.scrubTo(0.2f)
        assertEquals("a backwards scrub replays the pop-in", 1, controller.popEpoch)
        assertEquals("but it is not a replay of the journey", 0, controller.replayEpoch)

        controller.scrubTo(0.6f)
        assertEquals(0, controller.replayEpoch)

        controller.restart {}
        assertEquals(1, controller.replayEpoch)
        assertEquals(2, controller.popEpoch)
    }

    @Test
    fun `scrubbing mid playback keeps playing through to the end`() = runTest {
        withContext(TestMonotonicFrameClock(this)) {
            val controller = JourneyPlaybackController(0f)
            val reports = mutableListOf<Float>()
            var finished: Boolean? = null

            val job = launch { finished = controller.play(speed = 1f, totalMillis = pass) { reports += it } }
            testScheduler.advanceTimeBy(300L)
            testScheduler.runCurrent()
            assertTrue("playback should have started", controller.progress > 0f)
            assertTrue("playback should not be over yet", controller.progress < 1f)

            controller.scrubTo(0.5f)
            assertEquals(0.5f, controller.progress, 0.001f)

            job.join()
            assertEquals("playback must survive the scrub and finish", 1f, controller.progress, 0.001f)
            assertEquals(true, finished)
            assertEquals(1f, reports.last(), 0.0001f)
        }
    }

    @Test
    fun `replaying mid playback restarts the reveal and still finishes`() = runTest {
        withContext(TestMonotonicFrameClock(this)) {
            val controller = JourneyPlaybackController(0f)
            val reports = mutableListOf<Float>()
            var finished: Boolean? = null

            val job = launch { finished = controller.play(speed = 1f, totalMillis = pass) { reports += it } }
            testScheduler.advanceTimeBy(600L)
            testScheduler.runCurrent()
            val beforeReplay = controller.progress
            assertTrue("playback should be well underway", beforeReplay > 0.3f)
            val epochBefore = controller.popEpoch

            controller.restart { reports += it }
            assertEquals(0f, controller.progress, 0.001f)
            assertTrue("badges must pop again on replay", controller.popEpoch > epochBefore)

            job.join()
            assertEquals(1f, controller.progress, 0.001f)
            assertEquals(true, finished)
            assertTrue("the reveal must actually rewind", reports.any { it < beforeReplay - 0.1f })
        }
    }

    @Test
    fun `pausing stops playback and leaves the position alone`() = runTest {
        withContext(TestMonotonicFrameClock(this)) {
            val controller = JourneyPlaybackController(0f)
            val job = launch { controller.play(speed = 1f, totalMillis = pass) {} }
            testScheduler.advanceTimeBy(400L)
            testScheduler.runCurrent()
            assertTrue("playback should be underway", controller.progress > 0f)

            job.cancel()
            job.join()
            val stopped = controller.progress
            assertTrue("pausing must not jump to the end", stopped < 1f)

            testScheduler.advanceTimeBy(2_000L)
            testScheduler.runCurrent()
            // One frame may already have been in flight when the cancellation landed; what must
            // not happen is playback carrying on through two seconds of frames.
            assertTrue(
                "a paused chart must not keep advancing, went $stopped -> ${controller.progress}",
                controller.progress <= stopped + 0.02f,
            )
        }
    }

    @Test
    fun `pressing play on a finished chart replays from the start`() = runTest {
        withContext(TestMonotonicFrameClock(this)) {
            val controller = JourneyPlaybackController(1f)
            val reports = mutableListOf<Float>()

            val job = launch { controller.play(speed = 1f, totalMillis = pass) { reports += it } }
            testScheduler.advanceTimeBy(100L)
            testScheduler.runCurrent()
            assertTrue("the reveal must rewind before replaying", controller.progress < 0.5f)

            job.join()
            assertEquals(1f, controller.progress, 0.001f)
        }
    }

    /**
     * Evidence for the reporting rate: a full pass drives the shell (and with it the chart's
     * semantics node) about a hundred times, not once per animation frame.
     */
    @Test
    fun `progress is reported coarsely and always lands exactly on one`() = runTest {
        withContext(TestMonotonicFrameClock(this)) {
            val controller = JourneyPlaybackController(0f)
            val reports = mutableListOf<Float>()

            launch { controller.play(speed = 1f, totalMillis = pass) { reports += it } }.join()

            assertEquals(1f, reports.last(), 0.0001f)
            assertTrue("reports should be throttled, got ${reports.size}", reports.size <= 110)
            reports.zipWithNext().forEach { (a, b) ->
                assertTrue("reports must advance, saw $a then $b", b > a)
            }
        }
    }

    @Test
    fun `echoes of our own reports are not treated as scrubs`() = runTest {
        withContext(TestMonotonicFrameClock(this)) {
            val controller = JourneyPlaybackController(0f)
            var reported = 0f
            launch { controller.play(speed = 1f, totalMillis = pass) { reported = it } }
            testScheduler.advanceTimeBy(300L)
            testScheduler.runCurrent()

            assertTrue("the shell's echo must be ignored", !controller.scrubTo(reported))
            assertTrue("a real scrub must be honoured", controller.scrubTo(0.9f))
        }
    }
    // --- how long a pass takes ---

    /**
     * The reveal's pace, not its length, is what has to stay constant. A dense span earns three or
     * four screenfuls of stage, and a fixed 15-second pass ran the sweep across them three or four
     * times as fast - which is exactly the journey with the most on it to look at.
     */
    @Test
    fun `a pass lasts fifteen seconds per screenful of chart`() {
        assertEquals(JOURNEY_PLAYBACK_MILLIS, journeyPlaybackMillis(LayoutEngine.W), 0.01f)
        assertEquals(JOURNEY_PLAYBACK_MILLIS * 1.5f, journeyPlaybackMillis(LayoutEngine.W * 1.5f), 0.01f)
        assertEquals(60_000f, journeyPlaybackMillis(LayoutEngine.W * 4f), 0.01f)
    }

    /** The stage is never narrower than one screenful, and a degenerate width must not speed it up. */
    @Test
    fun `a chart narrower than the stage still takes a whole pass`() {
        assertEquals(JOURNEY_PLAYBACK_MILLIS, journeyPlaybackMillis(LayoutEngine.W / 2f), 0.01f)
        assertEquals(JOURNEY_PLAYBACK_MILLIS, journeyPlaybackMillis(0f), 0.01f)
    }

    /** The duration comes off a real layout, so the wiring cannot drift from the width factor. */
    @Test
    fun `the duration derives from the layout the chart is drawn on`() {
        val unlocks = (0 until 300).map {
            Unlock(
                id = it, title = "A$it", description = "", points = 5, badgeName = "b$it",
                type = AchievementType.STANDARD, earnedAtMillis = 1_000L + it * 86_400_000L,
                numAwarded = 1, gameId = 1,
            )
        }
        val main = PlayerJourney(
            name = "me", unlocks = unlocks,
            games = mapOf(1 to GameRef(1, "G", null, emptyList(), 0, 10, null)),
            medals = emptyList(),
        )
        val frame = JourneyFrame(JourneyMode.SPAN, main, emptyList(), 0L, 1_000L + 300 * 86_400_000L)

        val factor = LayoutEngine.widthFactorFor(frame)
        val wide = LayoutEngine.layout(frame, includeNodes = true, widthFactor = factor)
        val compact = LayoutEngine.layout(frame, includeNodes = true)

        assertTrue("fixture must have earned a wider stage", factor > 1f)
        assertEquals(JOURNEY_PLAYBACK_MILLIS * factor, journeyPlaybackMillis(wide.width), 0.01f)
        // Swapping to the compact chart at rest takes the pass back to one screenful.
        assertEquals(JOURNEY_PLAYBACK_MILLIS, journeyPlaybackMillis(compact.width), 0.01f)
    }

    /**
     * A years-long calendar slows the pass on its own axis: the width factor keys off icon
     * density, so a decade of steady play on the compact chart was still a 15-second pass with a
     * month of dates flying under the sweep every sixth of a second.
     */
    @Test
    fun `a long calendar span stretches the pass up to double`() {
        val year = 365L * 86_400_000L
        // At or below the ramp's start, nothing changes.
        assertEquals(JOURNEY_PLAYBACK_MILLIS, journeyPlaybackMillis(LayoutEngine.W, year), 0.01f)
        assertEquals(
            JOURNEY_PLAYBACK_MILLIS,
            journeyPlaybackMillis(LayoutEngine.W, SPAN_SLOW_START_MILLIS),
            0.01f,
        )
        // Halfway up the ramp: six years is halfway between two and ten.
        assertEquals(
            JOURNEY_PLAYBACK_MILLIS * 1.5f,
            journeyPlaybackMillis(LayoutEngine.W, 6L * year),
            0.01f,
        )
        // The full slowdown is what 0.5x used to be, and it saturates there.
        assertEquals(
            JOURNEY_PLAYBACK_MILLIS * SPAN_SLOW_MAX_FACTOR,
            journeyPlaybackMillis(LayoutEngine.W, SPAN_SLOW_FULL_MILLIS),
            0.01f,
        )
        assertEquals(
            JOURNEY_PLAYBACK_MILLIS * SPAN_SLOW_MAX_FACTOR,
            journeyPlaybackMillis(LayoutEngine.W, 25L * year),
            0.01f,
        )
        // The two factors compose: a wide, decade-long chart earns both.
        assertEquals(
            JOURNEY_PLAYBACK_MILLIS * 4f * SPAN_SLOW_MAX_FACTOR,
            journeyPlaybackMillis(LayoutEngine.W * 4f, 10L * year),
            0.01f,
        )
    }

}
