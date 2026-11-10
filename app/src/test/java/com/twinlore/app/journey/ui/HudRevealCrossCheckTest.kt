package com.twinlore.app.journey.ui

import com.twinlore.app.journey.domain.LayoutEngine
import com.twinlore.app.journey.domain.model.AchievementType
import com.twinlore.app.journey.domain.model.GameRef
import com.twinlore.app.journey.domain.model.JourneyFrame
import com.twinlore.app.journey.domain.model.JourneyMode
import com.twinlore.app.journey.domain.model.PlayerJourney
import com.twinlore.app.journey.domain.model.Unlock
import com.twinlore.app.journey.ui.chart.ChartPaths
import com.twinlore.app.journey.ui.chart.JourneyChartRenderer
import kotlin.math.abs
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The guard on [HudAtProgress]'s solo branch.
 *
 * The HUD counts what the sweep has revealed with plain polyline maths, because it has to stay
 * unit testable; the chart reveals the same journey along a smoothed cubic measured by Android's
 * `PathMeasure`. Those are two different curves through the same points, so this test is what
 * keeps them honest: across the whole timeline, on a deliberately awkward journey (bunched
 * unlocks, long gaps, and point values that swing the curve from nearly flat to nearly vertical),
 * the HUD's count must never be more than one unlock away from the badges the renderer has
 * actually popped in.
 *
 * One is the allowance: at any sampled position a single node can sit on the wrong side of the
 * boundary between the two parametrisations, and a counter one ahead of or behind the badge
 * appearing at that instant is invisible at the shell's ~1% progress granularity. Two would be a
 * visible lie and means the solo branch has to consume the renderer's reveal directly instead.
 *
 * What is compared is the *reveal* - how far along the curve each badge's anchor is - and not how
 * many discs are on stage. Those stopped being the same thing when badges started waiting for the
 * sweep to reach where the collision pass drew them: a cluster can hold four discs back at once
 * while the curve underneath them, which is what the HUD counts against, has already gone past.
 * [JourneyChartRenderer.visibleNodeIndices] is the drawn set, and is checked here only to stay a
 * subset of the reveal.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HudRevealCrossCheckTest {

    private val day = 86_400_000L
    private val t0 = 1_760_000_000_000L

    private fun unlock(id: Int, atDay: Int, points: Int, gameId: Int) = Unlock(
        id = id, title = "A$id", description = "d$id", points = points, badgeName = "b$id",
        type = AchievementType.STANDARD, earnedAtMillis = t0 + atDay * day,
        numAwarded = 10, gameId = gameId,
    )

    /**
     * Bunched starts, dead stretches, and a point spread from 1 to 100 so the curve alternates
     * between long flat runs and near vertical jumps: the shape the polyline approximation has the
     * most trouble with.
     */
    private fun awkwardFrame(): JourneyFrame {
        val spec = listOf(
            Triple(0, 5, 100), Triple(0, 10, 100), Triple(1, 1, 100), Triple(1, 100, 100),
            Triple(2, 3, 100), Triple(14, 50, 200), Triple(15, 2, 200), Triple(15, 75, 200),
            Triple(16, 1, 200), Triple(45, 25, 300), Triple(46, 100, 300), Triple(47, 5, 300),
            Triple(47, 5, 300), Triple(48, 40, 300), Triple(90, 10, 400), Triple(91, 90, 400),
            Triple(92, 1, 400), Triple(120, 60, 500), Triple(121, 3, 500), Triple(180, 100, 500),
        )
        val unlocks = spec.mapIndexed { index, (atDay, points, gameId) ->
            unlock(index + 1, atDay, points, gameId)
        }
        val games = unlocks.map { it.gameId }.distinct().associateWith { id ->
            GameRef(
                id = id, title = "Game $id", iconPath = null, artPaths = emptyList(),
                colorIndex = id % 4, players = 100, award = null,
            )
        }
        return JourneyFrame(
            mode = JourneyMode.SPAN,
            main = PlayerJourney("tester", unlocks, games, emptyList()),
            rivals = emptyList(),
            spanFromMillis = t0, spanToMillis = t0 + 180 * day,
        )
    }

    @Test
    fun `the HUD count tracks the badges the renderer has revealed`() {
        val frame = awkwardFrame()
        val layout = LayoutEngine.layout(frame, includeNodes = true)
        val paths = ChartPaths(layout)

        var worst = 0
        var worstAt = 0f
        for (step in 0..20) {
            val progress = step / 20f
            val hud = HudAtProgress.revealedCount(layout, progress)
            val revealed = revealedBadgeCount(layout, paths, progress)
            val drift = abs(hud - revealed)
            if (drift > worst) {
                worst = drift
                worstAt = progress
            }
            assertTrue(
                "the drawn badges must be a subset of the revealed ones at $progress",
                JourneyChartRenderer.visibleNodeIndices(layout, paths, progress).size <= revealed,
            )
        }

        assertTrue(
            "HUD counter drifted $worst unlocks from the revealed badges at progress $worstAt",
            worst <= 1,
        )
    }

    /**
     * Badges whose anchor the reveal has passed, which is the quantity the HUD counter mirrors.
     * Deliberately spelled out here rather than taken from the renderer: the renderer's visible set
     * also waits on where each disc was drawn, and that gate is not what this test is about.
     */
    private fun revealedBadgeCount(
        layout: com.twinlore.app.journey.domain.ChartLayout,
        paths: ChartPaths,
        progress: Float,
    ): Int {
        val revealed = JourneyChartRenderer.revealedLength(layout, paths, progress)
        return paths.nodeLengths.count { it <= revealed + 0.5f }
    }

    @Test
    fun `an evenly spaced journey tracks the badges exactly`() {
        val unlocks = (1..24).map { unlock(it, atDay = it * 3, points = 10, gameId = 1) }
        val games = mapOf(
            1 to GameRef(1, "Game 1", null, emptyList(), 0, 100, null),
        )
        val frame = JourneyFrame(
            mode = JourneyMode.SPAN,
            main = PlayerJourney("tester", unlocks, games, emptyList()),
            rivals = emptyList(), spanFromMillis = t0, spanToMillis = t0 + 72 * day,
        )
        val layout = LayoutEngine.layout(frame, includeNodes = true)
        val paths = ChartPaths(layout)

        for (step in 0..20) {
            val progress = step / 20f
            val hud = HudAtProgress.revealedCount(layout, progress)
            val revealed = revealedBadgeCount(layout, paths, progress)
            assertTrue(
                "at $progress the HUD said $hud and the chart had revealed $revealed",
                abs(hud - revealed) <= 1,
            )
        }
    }

    @Test
    fun `both agree that a finished journey is finished`() {
        val frame = awkwardFrame()
        val layout = LayoutEngine.layout(frame, includeNodes = true)
        val paths = ChartPaths(layout)

        assertTrue(
            JourneyChartRenderer.visibleNodeIndices(layout, paths, 1f).size == layout.nodes.size,
        )
        assertTrue(
            HudAtProgress.revealedCount(layout, 1f) == frame.main.unlocks.size,
        )
    }
}
