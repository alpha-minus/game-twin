package com.twinlore.app.journey.ui.chart

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.font.FontFamily
import com.twinlore.app.journey.domain.LayoutEngine
import com.twinlore.app.journey.domain.model.AchievementType
import com.twinlore.app.journey.domain.model.GameRef
import com.twinlore.app.journey.domain.model.JourneyFrame
import com.twinlore.app.journey.domain.model.JourneyMode
import com.twinlore.app.journey.domain.model.PlayerJourney
import com.twinlore.app.journey.domain.model.Unlock
import com.twinlore.app.journey.ui.JourneyUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The ring shown while the chart's images decode. It used to be drawn once and never again - a
 * deliberate trade against InfiniteTransition's per-vsync wakeups that read on a device as the
 * player freezing before the video began. While the ring is up nothing else is animating, and the
 * transition leaves composition the moment the bitmaps land, so the spin's wakeups are transient
 * by construction; what these tests pin is the mounting rule that makes that true.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class JourneyChartDecodeRingTest {

    @get:Rule
    val rule = createComposeRule()

    private fun ready(): JourneyUiState.Ready {
        val unlocks = (1..3).map { i ->
            Unlock(
                id = i, title = "A$i", description = "", points = 10, badgeName = "b$i",
                type = AchievementType.STANDARD, earnedAtMillis = i * 1_000L, numAwarded = 5, gameId = 1,
            )
        }
        val game = GameRef(
            id = 1, title = "G1", iconPath = null, artPaths = emptyList(),
            colorIndex = 0, players = 10, award = null, totalAchievements = 10,
        )
        val frame = JourneyFrame(
            mode = JourneyMode.SPAN,
            main = PlayerJourney("me", unlocks, mapOf(1 to game), emptyList()),
            rivals = emptyList(),
            spanFromMillis = 0L,
            spanToMillis = 4_000L,
        )
        val layout = LayoutEngine.layout(frame, includeNodes = true)
        return JourneyUiState.Ready(
            frame = frame, layout = layout, compactLayout = layout,
            failedCount = 0, rivalErrors = emptyList(), rank = null,
        )
    }

    private val style = ChartStyle(
        background = Color.Black, panel = Color.DarkGray, grid = Color.Gray,
        ink = Color.White, inkDim = Color.LightGray, accent = Color.Cyan,
        seriesColors = listOf(Color.Magenta), rivalColors = listOf(Color.Green),
        typeColors = AchievementType.values().associateWith { Color.Red },
        silver = Color.LightGray, gold = Color.Yellow, labelFont = FontFamily.Default,
    )

    private fun mount(bitmaps: Map<String, ImageBitmap>?) {
        val state = ready()
        rule.setContent {
            JourneyChart(
                ready = state,
                layout = state.layout,
                paths = ChartPaths(state.layout),
                userName = "me",
                progress = { 0f },
                playing = false,
                onProgressChange = {},
                onTogglePlay = {},
                bitmaps = bitmaps,
                style = style,
            )
        }
    }

    @Test
    fun `the ring is up while the bitmaps decode`() {
        mount(bitmaps = null)

        rule.onNodeWithTag("decodeRing", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `the ring leaves with everything it animates when the bitmaps land`() {
        mount(bitmaps = emptyMap())

        rule.onNodeWithTag("decodeRing", useUnmergedTree = true).assertDoesNotExist()
    }
}
