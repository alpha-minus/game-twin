package com.twinlore.app.journey.export

import com.twinlore.app.journey.domain.ChartLayout
import com.twinlore.app.journey.domain.LayoutEngine
import com.twinlore.app.journey.domain.model.AchievementType
import com.twinlore.app.journey.domain.model.AwardKind
import com.twinlore.app.journey.domain.model.GameRef
import com.twinlore.app.journey.domain.model.JourneyFrame
import com.twinlore.app.journey.domain.model.JourneyMode
import com.twinlore.app.journey.domain.model.Medal
import com.twinlore.app.journey.domain.model.PlayerJourney
import com.twinlore.app.journey.domain.model.Unlock
import com.twinlore.app.journey.ui.chart.ChartPaths
import com.twinlore.app.journey.ui.chart.FollowCamera
import com.twinlore.app.journey.ui.chart.JourneyChartRenderer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The exported camera's path.
 *
 * Robolectric only because [ChartPaths] measures real `android.graphics` paths to find the runner;
 * nothing here draws a pixel. The camera is arithmetic, which is the point of testing it this way:
 * a followed video is only shareable if it is the same file every time, and that is a property of
 * numbers rather than of anything the encoder did.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ExportCameraTest {

    private val day = 86_400_000L
    private val t0 = 1_760_000_000_000L

    private fun unlock(id: Int, at: Long, pts: Int) = Unlock(
        id = id, title = "A$id", description = "d$id", points = pts, badgeName = "b$id",
        type = AchievementType.STANDARD, earnedAtMillis = at, numAwarded = 10, gameId = 1,
    )

    private fun fixtureFrame(): JourneyFrame {
        val unlocks = (0 until 24).map { unlock(it, t0 + it * 5 * day, (it + 1) * 5) }
        val main = PlayerJourney(
            name = "me",
            unlocks = unlocks,
            games = mapOf(
                1 to GameRef(
                    1, "Game One", null, emptyList(), colorIndex = 0, players = 100, award = null,
                    totalAchievements = 40,
                ),
            ),
            medals = listOf(Medal(AwardKind.MASTERED, t0 + 60 * day, 1, null, "Game One")),
        )
        return JourneyFrame(
            mode = JourneyMode.SPAN, main = main, rivals = emptyList(),
            spanFromMillis = 0, spanToMillis = t0 + 130 * day,
        )
    }

    private fun fixtureLayout(): ChartLayout =
        LayoutEngine.layout(fixtureFrame(), includeNodes = true)

    /** Where the renderer puts the runner at [progress], which is what the camera has to chase. */
    private fun runnerAt(layout: ChartLayout, paths: ChartPaths, progress: Float) =
        paths.positionAt(ChartPaths.MAIN, JourneyChartRenderer.revealedLength(layout, paths, progress))

    /**
     * The exported camera is a pure function of the journey and the frame index: the same journey
     * has to encode to the same video every time, on any device. The live camera glides toward its
     * target by a factor derived from real elapsed time; the export's uses its own fixed cadence
     * instead, so nothing about how fast the encoder ran can reach the pixels.
     */
    @Test fun `the exported camera path is reproducible`() {
        val layout = fixtureLayout()
        val first = ExportCamera.path(layout, frames = 60)
        val second = ExportCamera.path(layout, frames = 60)
        assertEquals(first, second)
        assertEquals(60, first.size)
    }

    /** Following means the whole chart is NOT on screen; that is the point and the cost. */
    @Test fun `a followed camera is not the overview`() {
        val layout = fixtureLayout()
        val mid = ExportCamera.path(layout, frames = 60)[30]
        assertEquals(false, mid.isOverview)
    }

    /**
     * The first emitted frame is already beside the runner. Starting from the fitted view instead
     * would open every followed video with half a second of zooming in, which is a thing the
     * watcher never saw: on screen the camera is already on the runner when playback starts.
     */
    @Test fun `the first frame is already on the runner`() {
        val layout = fixtureLayout()
        val paths = ChartPaths(layout)

        val first = ExportCamera.path(layout, frames = 60).first()

        val target = FollowCamera.target(runnerAt(layout, paths, 0f), layout.width)
        assertEquals(target.zoom, first.zoom, 0.0001f)
        assertEquals(target.offset.x, first.offset.x, 0.01f)
        assertEquals(target.offset.y, first.offset.y, 0.01f)
    }

    /**
     * The runner is on screen in every frame of the path. The whole promise of following is that
     * the thing being followed is visible; an integration that drifted, or one that glided at a
     * fraction of the runner's own pace, would leave it behind without changing anything else.
     */
    @Test fun `the runner stays inside the viewport for the whole path`() {
        val layout = fixtureLayout()
        val paths = ChartPaths(layout)
        val speed = 1f
        val frames = ExportTimeline.frameCount(speed)

        val path = ExportCamera.path(layout, frames = frames, speed = speed)

        path.forEachIndexed { i, view ->
            val progress = ExportTimeline.progressForFrame(i, speed)
            val at = view.toStage(runnerAt(layout, paths, progress))
            assertTrue("runner off the left at frame $i (${at.x})", at.x >= -1f)
            assertTrue("runner off the right at frame $i (${at.x})", at.x <= LayoutEngine.W + 1f)
            assertTrue("runner off the top at frame $i (${at.y})", at.y >= -1f)
            assertTrue("runner off the bottom at frame $i (${at.y})", at.y <= LayoutEngine.H + 1f)
        }
    }

    /**
     * The landing glides out to the whole chart and the video ends on it, exactly as playback does
     * (see `JourneyChart`'s end glide). A clip that ended frozen at 2.2x on whichever badge came
     * last would end on a crop - and the still image of the same journey is the whole picture.
     */
    @Test fun `the hold tail glides out to the whole chart`() {
        val layout = fixtureLayout()
        val speed = 1f
        val frames = ExportTimeline.frameCount(speed)

        val path = ExportCamera.path(layout, frames = frames, speed = speed)

        assertEquals(frames, path.size)
        assertTrue("the clip must land on the whole chart", path.last().isOverview)
        // Not a cut: the frame the journey lands on is still the followed view.
        val holdFrames = ExportTimeline.HOLD_SECONDS * ExportTimeline.FPS
        val landing = path[frames - holdFrames - 1]
        assertEquals(false, landing.isOverview)
    }

    /** No frames, no path: a degenerate timeline must not throw on a background thread. */
    @Test fun `an empty timeline has an empty path`() {
        assertTrue(ExportCamera.path(fixtureLayout(), frames = 0).isEmpty())
    }
}
