package com.twinlore.app.journey.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import com.twinlore.app.journey.domain.ChartLayout
import com.twinlore.app.journey.domain.LayoutEngine
import com.twinlore.app.journey.domain.model.AchievementType
import com.twinlore.app.journey.domain.model.GameRef
import com.twinlore.app.journey.domain.model.JourneyFrame
import com.twinlore.app.journey.domain.model.JourneyMode
import com.twinlore.app.journey.domain.model.PlayerJourney
import com.twinlore.app.journey.domain.model.Unlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Input routing for the player's control bar, driven statelessly: a playback holder and callbacks
 * of this test's own, with a stand-in for the chart behind the bar. No chart, no image cache and
 * no autoplay, so what these assert is where a finger's events end up and nothing else.
 *
 * The bar sits on top of a chart that covers the whole player and toggles the bar on a tap, so it
 * has to swallow taps that land on its own panel without swallowing its children's. The first cut
 * of that (Wave E1) put a consuming pointer handler on the *container*: children do run the main
 * pass first, but a tap detector re-checks the same event on the final pass, which runs parents
 * first, and cancels the press when it sees the container's consumption there. Every gesture that
 * moved a pixel between finger down and finger up - which is every real one - was cancelled, so
 * the whole bar was dead on the device while a zero-movement synthetic click still passed. Hence
 * the deliberate jitter in the taps below.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class JourneyPlayerInputTest {

    @get:Rule
    val rule = createComposeRule()

    private val playback = JourneyPlaybackHolder()
    private var chartTaps = 0
    private var speed = 1f
    private var settingsOpen = false
    private var legendOpen = false
    private var canToggleDensity = true
    private var dense = false
    private var densityToggles = 0

    /**
     * Any chart at all. The bar reads exactly one thing off it - the streak runs, for the colour of
     * the scrub track - so this fixture only has to be a layout, not a particular journey.
     */
    private val layout: ChartLayout = run {
        val day = 86_400_000L
        val unlocks = (1..3).map { i ->
            Unlock(
                id = i, title = "A$i", description = "", points = 10, badgeName = "b$i",
                type = AchievementType.STANDARD, earnedAtMillis = i * day, numAwarded = 5, gameId = 1,
            )
        }
        val games = mapOf(1 to GameRef(1, "G1", null, emptyList(), 0, 10, null))
        LayoutEngine.layout(
            JourneyFrame(
                JourneyMode.SPAN, PlayerJourney("me", unlocks, games, emptyList()), emptyList(),
                0, 4 * day,
            ),
            includeNodes = true,
        )
    }

    @Composable
    private fun Player() {
        Box(Modifier.fillMaxSize()) {
            // Stands in for the chart: full screen, under the bar, and taps on it toggle the bar.
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) { detectTapGestures { chartTaps++ } },
            )
            PlayerControls(
                playback = playback,
                layout = layout,
                speed = speed,
                settingsOpen = settingsOpen,
                legendOpen = legendOpen,
                followMode = false,
                dense = dense,
                canToggleDensity = canToggleDensity,
                onToggleDensity = { densityToggles++ },
                onToggleSettings = { settingsOpen = !settingsOpen },
                onToggleLegend = { legendOpen = !legendOpen },
                onSetSpeed = { speed = it },
                onSetFollowMode = {},
                modifier = Modifier.align(Alignment.BottomCenter).testTag("bar"),
            )
        }
    }

    /**
     * The density button is disabled where there is nothing to swap to.
     *
     * A journey only earns a second, wider chart when its icon density asks for one; a compare
     * chart never does (its badges are rationed a day at a time rather than stretched) and neither
     * does a short solo journey, so the ViewModel hands out the same layout object for both views
     * and the button was a control the user could press all day for no effect.
     */
    @Test
    fun `the density button is dead when the journey has only one chart`() {
        canToggleDensity = false
        rule.setContent { Player() }

        // The fixture starts on the wide chart, so the button offers the compact one.
        rule.onNodeWithContentDescription("Fit the whole journey").tapWithJitter()

        assertEquals("a disabled density button must not toggle anything", 0, densityToggles)
    }

    /** A real finger never holds still between down and up. */
    private fun androidx.compose.ui.test.SemanticsNodeInteraction.tapWithJitter() =
        performTouchInput {
            down(center)
            moveBy(Offset(1f, 1f))
            up()
        }

    @Test
    fun `a tap on play reaches the handler`() {
        rule.setContent { Player() }

        rule.onNodeWithContentDescription("Play").tapWithJitter()

        assertTrue("play must have toggled playback", playback.playing)
        assertEquals("the chart behind the bar must not see it", 0, chartTaps)
    }

    @Test
    fun `a tap on replay reaches the handler`() {
        playback.progress = 0.7f
        rule.setContent { Player() }

        rule.onNodeWithContentDescription("Replay").tapWithJitter()

        assertEquals(0f, playback.progress, 0.0001f)
        assertTrue(playback.playing)
    }

    @Test
    fun `a swipe on the scrub slider moves playback`() {
        rule.setContent { Player() }

        rule.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress)).performTouchInput { swipeRight() }

        assertTrue("progress ${playback.progress} did not move", playback.progress > 0f)
    }

    /**
     * The bar carries no way out. The player has one in its top-right corner that never leaves,
     * which is what a full-screen mode needs; a second one on furniture that fades away was the
     * same action offered twice, in the corner of the bar a thumb finds by accident. The remaining
     * one is covered where it lives: see JourneyScreenStateTest.
     */
    @Test
    fun `the bar has no close of its own`() {
        rule.setContent { Player() }

        rule.onNodeWithContentDescription("Close player").assertDoesNotExist()
        rule.onNodeWithContentDescription("Close").assertDoesNotExist()
    }

    /**
     * The panel is furniture, not chart: a tap that lands on it between two buttons stops there
     * rather than reaching the chart underneath and hiding the bar the user was aiming at.
     */
    @Test
    fun `a tap on the bar panel does not reach the chart`() {
        rule.setContent { Player() }

        rule.onNodeWithTag("bar").performTouchInput {
            down(Offset(width / 2f, height - 2f))
            moveBy(Offset(1f, 0f))
            up()
        }

        assertEquals(0, chartTaps)
        assertTrue("nothing on the bar should have fired", !playback.playing)
    }

    /** UI 3: the bar carries playback controls only; the speed moved into the settings sheet. */
    @Test
    fun `the speed is not on the bar`() {
        rule.setContent { Player() }

        rule.onNodeWithText("1x").assertDoesNotExist()
        rule.onNodeWithText(".5x").assertDoesNotExist()
        rule.onNodeWithContentDescription("Settings").assertExists()
    }

    /**
     * The view button pins whichever chart the automatic rule would not have chosen. It says what
     * it will do, not what is on screen: pressed while the whole journey is fitted, it offers the
     * wide timeline.
     */
    @Test
    fun `the bar offers the other view and reports the press`() {
        dense = true
        rule.setContent { Player() }

        rule.onNodeWithContentDescription("Wide timeline").tapWithJitter()

        assertEquals(1, densityToggles)
        rule.onNodeWithContentDescription("Fit the whole journey").assertDoesNotExist()
    }

    @Test
    fun `the bar offers the fitted view while the wide one is up`() {
        dense = false
        rule.setContent { Player() }

        rule.onNodeWithContentDescription("Fit the whole journey").assertExists()
        rule.onNodeWithContentDescription("Wide timeline").assertDoesNotExist()
    }

    /**
     * The shields hug the surfaces, not the band: the sheet's panel is capped at 460dp while the
     * band spans the screen, and on a landscape screen the gutter beside the panel is most of the
     * width. A tap there is a tap outside the sheet's UI and must fall through to the chart (where
     * the player closes the sheet), not die on an invisible full-width shield.
     */
    @Test
    @Config(qualifiers = "w640dp-h360dp-land")
    fun `with the sheet open a tap beside its panel reaches the chart`() {
        settingsOpen = true
        rule.setContent { Player() }
        rule.onNodeWithText("Speed").assertExists()

        rule.onNodeWithTag("bar").performTouchInput {
            down(Offset(width - 2f, 2f))
            moveBy(Offset(-1f, 1f))
            up()
        }

        assertEquals(1, chartTaps)
    }

    @Test
    fun `the settings sheet offers the three speeds`() {
        settingsOpen = true
        rule.setContent { Player() }

        rule.onNodeWithText("Speed").assertExists()
        rule.onNodeWithText(".5x").assertExists()
        rule.onNodeWithText("1x").assertExists()
        rule.onNodeWithText("2x").assertExists()

        rule.onNodeWithText("2x").tapWithJitter()

        assertEquals(2f, speed, 0.0001f)
    }

    /**
     * The sheet is where the chart's visual language is explained, and it was explained nowhere at
     * all: gold ring, silver dot, corner stamps and streak flames all had to be inferred or dug out
     * of a tooltip one at a time.
     *
     * Asserted as one merged node rather than as eight rows of text, because that is the shape it
     * is deliberately built in: a screen reader gets the key as a single passage, and the swatches
     * beside the words say nothing out loud.
     */
    @Test
    fun `the legend sheet carries the chart's key as one node`() {
        legendOpen = true
        rule.setContent { Player() }

        rule.onNodeWithText(LEGEND_TITLE).assertExists()
        val spoken = LEGEND_TITLE + ". " + legendEntries(compare = false).joinToString(". ") { it.text }
        rule.onNodeWithContentDescription(spoken).assertExists()
    }

    /** House rule for user-facing copy, and the legend is eight new strings of it. */
    @Test
    fun `no legend line uses an em dash`() {
        assertTrue(legendEntries(compare = true).none { it.text.contains('—') })
        assertTrue(!LEGEND_TITLE.contains('—'))
    }

    /**
     * The key may not explain a mark the chart is not making. The owner dot is drawn only when a
     * rival has unlocks - "a solo chart's badges all belong to the one player, and a dot saying so
     * on every one of them is noise" - and a solo journey is the common case: the vault's
     * Achievements entry cannot even add a rival. The row was sending most readers looking for
     * something the renderer was deliberately suppressing, over three coloured dots that were
     * themselves a claim that more than one player was on the chart.
     */
    @Test
    fun `the owner dot is explained only on a chart that draws it`() {
        val solo = legendEntries(compare = false).map { it.text }
        val compare = legendEntries(compare = true).map { it.text }

        assertTrue(solo.none { it.contains("whose badge it is") })
        assertTrue(compare.any { it.contains("whose badge it is") })
        assertEquals("nothing else moves with it", compare.size - 1, solo.size)
    }

    /**
     * Gold means two things on this chart - a mastered award's dot and a win condition's corner
     * stamp are the identical hex, deliberately, pinned by ChartColorsTest - and the key is the
     * first surface that stacks them in one list. What tells them apart is the shape, so both rows
     * name theirs.
     */
    @Test
    fun `the two gold rows name the shapes that tell them apart`() {
        val texts = legendEntries(compare = false).map { it.text }

        assertTrue(texts.any { it == "Gold, filled dot: mastered" })
        assertTrue(texts.any { it == "Gold, hollow dot: completed" })
        // A star, not a medal: win condition went back to its "★" glyph (the chart's Material icon
        // for it read too small at the chip's size), and the key may not name a shape the chart has
        // stopped drawing.
        assertTrue(texts.any { it == "Gold star stamp: win condition" })
    }
}
