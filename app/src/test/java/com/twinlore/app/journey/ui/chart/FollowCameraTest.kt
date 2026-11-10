package com.twinlore.app.journey.ui.chart

import androidx.compose.ui.geometry.Offset
import com.twinlore.app.journey.domain.LayoutEngine
import com.twinlore.app.journey.domain.model.AchievementType
import com.twinlore.app.journey.domain.model.GameRef
import com.twinlore.app.journey.domain.model.JourneyFrame
import com.twinlore.app.journey.domain.model.JourneyMode
import com.twinlore.app.journey.domain.model.PlayerJourney
import com.twinlore.app.journey.domain.model.Unlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The follow camera's maths. Pure: given a runner position it answers where the viewport belongs,
 * and how far a single frame moves toward it, with no Compose animation and no draw scope.
 */
class FollowCameraTest {

    private val w = LayoutEngine.W
    private val h = LayoutEngine.H
    private val day = 86_400_000L

    @Test fun `the target centres the runner when it is clear of the edges`() {
        val runner = Offset(w / 2f, h / 2f)

        val camera = FollowCamera.target(runner)

        assertEquals(FollowCamera.ZOOM, camera.zoom, 0.001f)
        val at = camera.toStage(runner)
        assertEquals(w / 2f, at.x, 0.5f)
        assertEquals(h / 2f, at.y, 0.5f)
    }

    /** Centring a runner in a corner would show empty space; the clamp wins and the edge stays put. */
    @Test fun `the target never lets a content edge leave the viewport`() {
        listOf(Offset(0f, 0f), Offset(w, h), Offset(w, 0f), Offset(20f, h - 10f)).forEach { runner ->
            val camera = FollowCamera.target(runner)
            assertTrue("left gap at $runner", camera.offset.x <= 0.01f)
            assertTrue("top gap at $runner", camera.offset.y <= 0.01f)
            assertTrue("right gap at $runner", camera.offset.x + w * camera.zoom >= w - 0.01f)
            assertTrue("bottom gap at $runner", camera.offset.y + h * camera.zoom >= h - 0.01f)
        }
    }

    @Test fun `a step moves a fixed fraction of the way toward the target`() {
        val from = FollowCamera.target(Offset(500f, 300f))
        val target = FollowCamera.target(Offset(560f, 300f))

        val stepped = FollowCamera.step(from, target, 0.15f)

        val expected = from.offset.x + 0.15f * (target.offset.x - from.offset.x)
        assertEquals(expected, stepped.offset.x, 0.01f)
        assertEquals(from.zoom, stepped.zoom, 0.0001f)
    }

    @Test fun `a full step lands exactly on the target`() {
        val from = FollowCamera.target(Offset(300f, 220f))
        val target = FollowCamera.target(Offset(340f, 240f))

        val stepped = FollowCamera.step(from, target, 1f)

        assertEquals(target.zoom, stepped.zoom, 0.0001f)
        assertEquals(target.offset.x, stepped.offset.x, 0.01f)
        assertEquals(target.offset.y, stepped.offset.y, 0.01f)
    }

    @Test fun `repeated steps converge on the target and stay there`() {
        val from = FollowCamera.target(Offset(640f, 180f))
        val target = FollowCamera.target(Offset(700f, 180f))
        var camera = from
        repeat(90) { camera = FollowCamera.step(camera, target, FollowCamera.LERP) }

        assertEquals(target.zoom, camera.zoom, 0.01f)
        assertEquals(target.offset.x, camera.offset.x, 1f)
        assertEquals(target.offset.y, camera.offset.y, 1f)

        val again = FollowCamera.step(camera, target, FollowCamera.LERP)
        assertEquals(camera.offset.x, again.offset.x, 0.5f)
    }

    /** Every intermediate frame is a legal view: the lerp cannot open a gap the clamp would not. */
    @Test fun `every intermediate step keeps the content covering the viewport`() {
        val target = FollowCamera.target(Offset(w - 30f, 20f))
        var camera = FollowCamera.target(Offset(w - 200f, 120f))
        repeat(40) {
            camera = FollowCamera.step(camera, target, FollowCamera.LERP)
            assertTrue("left gap on step $it", camera.offset.x <= 0.01f)
            assertTrue("right gap on step $it", camera.offset.x + w * camera.zoom >= w - 0.01f)
        }
    }

    @Test fun `the step is deterministic`() {
        val from = FollowCamera.target(Offset(400f, 380f))
        val target = FollowCamera.target(Offset(420f, 380f))
        val a = FollowCamera.step(from, target, FollowCamera.LERP)
        val b = FollowCamera.step(from, target, FollowCamera.LERP)

        assertEquals(a, b)
    }

    /**
     * A scrub moves the runner across the chart between two frames. Gliding after it would fly the
     * whole journey through the viewport; the camera is simply already there on the next frame.
     */
    @Test fun `a jump across the chart snaps instead of gliding`() {
        val from = FollowCamera.target(Offset(60f, 300f))
        val target = FollowCamera.target(Offset(w - 60f, 300f))

        val stepped = FollowCamera.step(from, target, FollowCamera.LERP)

        assertEquals(target, stepped)
    }

    /** Opening the player is the same kind of jump: the camera arrives rather than swoops. */
    @Test fun `taking up the follow from the fitted view snaps`() {
        val target = FollowCamera.target(Offset(900f, 400f))

        assertEquals(target, FollowCamera.step(ChartZoom.NONE, target, FollowCamera.LERP))
    }

    @Test fun `the glide fraction is quoted per reference frame`() {
        assertEquals(FollowCamera.LERP, FollowCamera.factorFor(1f / FollowCamera.REFERENCE_FPS), 0.0001f)
    }

    /** Two half-length frames have to cover exactly as much ground as one full one. */
    @Test fun `the glide covers the same ground however the frames fall`() {
        val full = FollowCamera.factorFor(1f / 30f)
        val half = FollowCamera.factorFor(1f / 60f)
        val twice = 1f - (1f - half) * (1f - half)

        assertEquals(full, twice, 0.0001f)
    }

    @Test fun `a stalled frame lands on the target rather than crawling`() {
        assertEquals(1f, FollowCamera.factorFor(2f), 0.001f)
        assertEquals(0f, FollowCamera.factorFor(0f), 0.0001f)
        assertEquals(0f, FollowCamera.factorFor(-1f), 0.0001f)
    }

    // --- wide charts ---

    /**
     * The camera's viewport is always the 1060x600 stage; what changes on a wide chart is how far
     * it may travel before the clamp stops it.
     */
    @Test fun `the target centres a runner far down a wide timeline`() {
        val contentWidth = w * 3f
        val runner = Offset(contentWidth / 2f, h / 2f)

        val camera = FollowCamera.target(runner, contentWidth)

        assertEquals(FollowCamera.ZOOM, camera.zoom, 0.001f)
        assertEquals(contentWidth, camera.contentWidth, 0.01f)
        val at = camera.toStage(runner)
        assertEquals(w / 2f, at.x, 0.5f)
        assertEquals(h / 2f, at.y, 0.5f)
    }

    @Test fun `the target never lets a wide chart's edges leave the viewport`() {
        val contentWidth = w * 3f
        listOf(0f, contentWidth / 2f, contentWidth).forEach { x ->
            val camera = FollowCamera.target(Offset(x, h / 2f), contentWidth)
            assertTrue("left gap at $x", camera.offset.x <= 0.01f)
            assertTrue(
                "right gap at $x",
                camera.offset.x + contentWidth * camera.zoom >= w - 0.01f,
            )
        }
    }

    @Test fun `a step keeps the content width of the chart being followed`() {
        val contentWidth = w * 3f
        val from = FollowCamera.target(Offset(400f, 300f), contentWidth)
        val target = FollowCamera.target(Offset(460f, 300f), contentWidth)

        assertEquals(contentWidth, FollowCamera.step(from, target, 0.15f).contentWidth, 0.01f)
    }

    /** A chart the layout had to widen is one the fitted view cannot render legibly. */
    // --- engaged: the camera only drives a journey that is actually moving ---

    /**
     * The bug this pins: `defaultOn` is always true for a wide journey, so a paused or finished
     * wide journey kept drawing through a 2.2x runner-centred camera. The player drops to the
     * compact chart at rest, and a magnified slice of it is the opposite of what that is for.
     */
    @Test fun `a stopped journey never follows, whatever the setting says`() {
        assertFalse(FollowCamera.engaged(followMode = true, suspended = false, playing = false))
        assertFalse(FollowCamera.engaged(followMode = true, suspended = true, playing = false))
    }

    @Test fun `a running journey follows unless the user took the camera over`() {
        assertTrue(FollowCamera.engaged(followMode = true, suspended = false, playing = true))
        assertFalse(FollowCamera.engaged(followMode = true, suspended = true, playing = true))
        assertFalse(FollowCamera.engaged(followMode = false, suspended = false, playing = true))
    }

    /**
     * Follow is on by default for EVERY journey now: the toggle
     * used to default per layout (wide/long/dense on, short compact off), and the split read
     * as inconsistency rather than intent. The stored preference still wins once the user
     * has touched the toggle; and a stopped journey still never follows (see `engaged`).
     */
    @Test fun `every journey follows by default, whatever its shape`() {
        assertTrue(FollowCamera.defaultOn(wideLayout(), unlockCount = 4))
        assertTrue(FollowCamera.defaultOn(layoutOverDays(30), unlockCount = 12))
        assertTrue(FollowCamera.defaultOn(layoutOverDays(400), unlockCount = 12))
        val race = raceLayout()
        assertTrue(race.raceMode)
        assertTrue(FollowCamera.defaultOn(race, unlockCount = 4))
    }

    private fun unlock(id: Int, atMillis: Long, gameId: Int = 100) = Unlock(
        id = id, title = "u$id", description = "d", points = 10, badgeName = "b$id",
        type = AchievementType.STANDARD, earnedAtMillis = atMillis, numAwarded = 3, gameId = gameId,
    )

    private fun game(id: Int) = GameRef(
        id = id, title = "g$id", iconPath = null, artPaths = emptyList(),
        colorIndex = 0, players = 10, award = null,
    )

    private fun layoutOverDays(days: Int) = LayoutEngine.layout(
        JourneyFrame(
            mode = JourneyMode.SPAN,
            main = PlayerJourney(
                "tester", listOf(unlock(1, 0L), unlock(2, days * day)),
                mapOf(100 to game(100)), emptyList(),
            ),
            rivals = emptyList(), spanFromMillis = 0L, spanToMillis = days * day,
        ),
        includeNodes = true,
    )

    /**
     * UI feedback: the end of the journey zooms out SMOOTHLY. [FollowCamera.step] snaps when the
     * target is far - right for tracking a scrub, wrong for the landing - so the end glide has its
     * own walk with no shortcut: every frame is partial, and iterating converges on the overview.
     */
    @Test
    fun `the end glide walks to the overview without the snap shortcut`() {
        val layout = wideLayout()
        val overview = ChartZoom.overviewFor(layout.width)
        val followView = FollowCamera.target(Offset(layout.width - 80f, 200f), layout.width)

        // From a follow view most of a wide chart away, step() would teleport...
        assertEquals(overview, FollowCamera.step(followView, overview, FollowCamera.LERP))
        // ...the glide does not: one frame is partial, and two hundred arrive.
        val one = FollowCamera.glide(followView, overview, FollowCamera.LERP)
        assertTrue(!FollowCamera.arrived(one, overview))
        var v = followView
        repeat(200) { v = FollowCamera.glide(v, overview, FollowCamera.LERP) }
        assertTrue(FollowCamera.arrived(v, overview))
    }

    private fun wideLayout() = LayoutEngine.layout(
        JourneyFrame(
            mode = JourneyMode.SPAN,
            main = PlayerJourney(
                "tester", listOf(unlock(1, 0L), unlock(2, 30 * day)),
                mapOf(100 to game(100)), emptyList(),
            ),
            rivals = emptyList(), spanFromMillis = 0L, spanToMillis = 30 * day,
        ),
        includeNodes = true,
        widthFactor = 2f,
    )

    private fun raceLayout() = LayoutEngine.layout(
        JourneyFrame(
            mode = JourneyMode.GAME,
            main = PlayerJourney(
                "tester", listOf(unlock(1, 0L), unlock(2, 400 * day)),
                mapOf(100 to game(100)), emptyList(),
            ),
            rivals = listOf(
                PlayerJourney(
                    "rival", listOf(unlock(9, 5 * day)), mapOf(100 to game(100)), emptyList(),
                ),
            ),
            spanFromMillis = 0L, spanToMillis = 400 * day,
        ),
        includeNodes = true,
    )
}
