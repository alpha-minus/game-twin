package com.twinlore.app.journey.ui

import androidx.compose.ui.test.junit4.createComposeRule
import com.twinlore.app.journey.domain.LayoutEngine
import com.twinlore.app.journey.domain.model.AchievementType
import com.twinlore.app.journey.domain.model.GameRef
import com.twinlore.app.journey.domain.model.JourneyFrame
import com.twinlore.app.journey.domain.model.JourneyMode
import com.twinlore.app.journey.domain.model.PlayerJourney
import com.twinlore.app.journey.domain.model.Unlock
import com.twinlore.app.journey.ui.chart.ChartPaths
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [backdropArgs] wiring [BackdropArgs.rival] to [com.twinlore.app.journey.domain.splitsForRivals]
 * - the same rule [com.twinlore.app.journey.export.ExportFrame] draws by. The predicate itself is
 * pinned once in `BackdropSplitTest`; what is worth pinning here is that the screen actually calls
 * it. It used to have its own copy ("is there a rival track at all", no mode in it), which is
 * exactly how the export got fixed and the screen kept splitting a single-game compare - the same
 * picture drawn twice with a seam across the middle.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class JourneyScreenBackdropTest {

    @get:Rule
    val rule = createComposeRule()

    private val day = 86_400_000L

    /** Main and rival on the same one game, so a split (if it happened) would paint it twice. */
    private fun frame(mode: JourneyMode): JourneyFrame {
        val game = GameRef(
            id = 1, title = "Shared Game", iconPath = null, artPaths = emptyList(),
            colorIndex = 0, players = 10, award = null,
        )
        val unlock = Unlock(
            id = 1, title = "A", description = "d", points = 10, badgeName = "b",
            type = AchievementType.STANDARD, earnedAtMillis = day, numAwarded = 5, gameId = 1,
        )
        val main = PlayerJourney("me", listOf(unlock), mapOf(1 to game), emptyList())
        val rival = PlayerJourney("rival", listOf(unlock.copy(id = 2)), mapOf(1 to game), emptyList())
        return JourneyFrame(mode, main, listOf(rival), spanFromMillis = 0, spanToMillis = 2 * day)
    }

    private fun compareArgs(mode: JourneyMode, progress: Float = 0.5f): BackdropArgs? {
        val loadedFrame = frame(mode)
        val layout = LayoutEngine.layout(loadedFrame, includeNodes = true)
        val paths = ChartPaths(layout)
        val state = JourneyUiState.Ready(
            frame = loadedFrame, layout = layout, compactLayout = layout,
            failedCount = 0, rivalErrors = emptyList(),
        )
        var result: BackdropArgs? = null
        rule.setContent {
            result = backdropArgs(state = state, layout = layout, paths = paths, progress = progress)
        }
        return result
    }

    private fun rivalSide(mode: JourneyMode): BackdropArgs? = compareArgs(mode)?.rival

    @Test fun `a single-game compare never splits the backdrop`() {
        assertNull(rivalSide(JourneyMode.GAME))
    }

    @Test fun `a span compare still splits the backdrop`() {
        assertNotNull(rivalSide(JourneyMode.SPAN))
    }

    /**
     * The tint follows the same rule the split does. The player wash says whose half of a SPLIT
     * stage this is; a single-game compare has no halves - both players are on the same game - so
     * its art dims the way a solo chart's does. It used to keep the always-on player wash from a
     * second copy of the "is there a rival" check with no mode in it (user report, seen on device).
     */
    @Test fun `a single-game compare dims like a solo chart, not a player wash`() {
        assertEquals(false, compareArgs(JourneyMode.GAME)?.tintAlways)
    }

    @Test fun `a span compare still washes each half in its player's colour`() {
        assertEquals(true, compareArgs(JourneyMode.SPAN)?.tintAlways)
    }

    /**
     * A LANDED span compare rests on the bare theme background too: the player wash used to survive the landing to say whose half was whose,
     * but the stopped chart's curves and compare rows already say it, and the export has
     * always ended on the bare background - the live stage matches it exactly now.
     */
    @Test fun `a landed span compare rests on the bare theme background`() {
        val landed = compareArgs(JourneyMode.SPAN, progress = 1f)

        assertEquals(emptyList<String>(), landed?.artUrls)
        assertEquals(Color.Unspecified, landed?.tint)
        assertEquals(false, landed?.tintAlways)
        assertNull("no split survives the landing", landed?.rival)
    }

    /** A solo journey, one game, so nothing splits and the main side is the whole stage. */
    private fun soloSide(mode: JourneyMode, progress: Float): BackdropArgs? {
        val game = GameRef(
            id = 1, title = "Solo Game", iconPath = null, artPaths = emptyList(),
            colorIndex = 0, players = 10, award = null,
        )
        val unlock = Unlock(
            id = 1, title = "A", description = "d", points = 10, badgeName = "b",
            type = AchievementType.STANDARD, earnedAtMillis = day, numAwarded = 5, gameId = 1,
        )
        val loadedFrame = JourneyFrame(
            mode, PlayerJourney("me", listOf(unlock), mapOf(1 to game), emptyList()),
            emptyList(), spanFromMillis = 0, spanToMillis = 2 * day,
        )
        val layout = LayoutEngine.layout(loadedFrame, includeNodes = true)
        val paths = ChartPaths(layout)
        val state = JourneyUiState.Ready(
            frame = loadedFrame, layout = layout, compactLayout = layout,
            failedCount = 0, rivalErrors = emptyList(),
        )
        var result: BackdropArgs? = null
        rule.setContent {
            result = backdropArgs(
                state = state, layout = layout, paths = paths, progress = progress,
                style = rememberChartStyle(),
            )
        }
        return result
    }

    /**
     * A LANDED solo span rests on nothing at all - no art and no colour.
     *
     * Dropping the art alone was not enough: `BackdropSide` paints the tint whenever `artUrls` is
     * empty, so taking the picture away is what switched the colour on. The stage went from the
     * last game's art to a flat accent wash and stayed there, while the export ended on bare
     * background (user report, seen on device).
     */
    @Test fun `a landed solo span rests on no colour either`() {
        val landed = soloSide(JourneyMode.SPAN, progress = 1f)

        assertEquals(emptyList<String>(), landed?.artUrls)
        assertEquals(
            "an empty art list is what turns the tint on, so it has to be unspecified",
            Color.Unspecified,
            landed?.tint,
        )
    }

    /** While it still plays, a solo span keeps the colour under its art - that tier is unchanged. */
    @Test fun `a running solo span still rests on its game's colour`() {
        val running = soloSide(JourneyMode.SPAN, progress = 0.5f)

        assertNotEquals(Color.Unspecified, running?.tint)
    }

    /**
     * And a single game keeps its picture to the last frame: the whole journey IS that game, so
     * there is no leftover to drop.
     */
    @Test fun `a landed single game keeps its backdrop`() {
        val landed = soloSide(JourneyMode.GAME, progress = 1f)

        assertNotEquals(Color.Unspecified, landed?.tint)
    }
}
