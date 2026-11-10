package com.twinlore.app.journey.ui

import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import com.twinlore.app.journey.domain.LayoutEngine
import com.twinlore.app.journey.domain.model.AchievementType
import com.twinlore.app.journey.domain.model.CompletionRow
import com.twinlore.app.journey.domain.model.GameRef
import com.twinlore.app.journey.domain.model.JourneyFrame
import com.twinlore.app.journey.domain.model.JourneyMode
import com.twinlore.app.journey.domain.model.PlayerJourney
import com.twinlore.app.journey.domain.model.Unlock
import com.twinlore.app.journey.ui.chart.ChartStyle
import com.twinlore.app.ui.LocalImageLoader
import coil.ImageLoader
import coil.intercept.Interceptor
import coil.request.ErrorResult
import kotlin.math.abs
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * State-rendering tests for [JourneyScreenContent], the stateless layer of [JourneyScreen].
 * Drives it directly with fake states/controls (no Hilt, no ViewModel, no network) the way the
 * rest of the app's Robolectric compose tests do.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class JourneyScreenStateTest {

    // An Android rule rather than the plain one: the player's back handling is part of what this
    // covers, and that needs the activity's own back dispatcher.
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    /**
     * Reduced motion, so the chart never autoplays under the test clock. Without it the player
     * opens playing, its control bar auto-hides three seconds later, and whether the assertions
     * run before or after that is a race with however far the test clock has been advanced.
     */
    @Before
    fun disableAnimations() {
        Settings.Global.putFloat(
            RuntimeEnvironment.getApplication().contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            0f,
        )
    }

    /** [readyFixture]'s span, with a second player's curve on it. */
    private fun spanCompareReadyFixture(): JourneyUiState.Ready {
        val base = readyFixture(unlockSpacing = 86_400_000L)
        val rivalUnlocks = base.frame.main.unlocks.map { it.copy(id = it.id + 100) }
        val rival = PlayerJourney(
            name = "aria", unlocks = rivalUnlocks,
            games = base.frame.main.games, medals = emptyList(),
        )
        val frame = base.frame.copy(rivals = listOf(rival))
        val layout = LayoutEngine.layout(frame, includeNodes = false)
        return base.copy(frame = frame, layout = layout, compactLayout = layout)
    }

    private fun readyFixture(
        rank: RankChip? = null,
        failedCount: Int = 0,
        mode: JourneyMode = JourneyMode.SPAN,
        totalAchievements: Int = 0,
        /**
         * How far apart the three unlocks sit. Seconds by default - a journey that happened in one
         * sitting, which is what the date-chip tests below are about. A day apart makes it a
         * three-day run instead, which is what the end state needs before it has a streak to name:
         * a run of one day is not a streak and the chip is left off entirely.
         */
        unlockSpacing: Long = 1_000L,
    ): JourneyUiState.Ready {
        val unlock1 = Unlock(
            id = 1, title = "First Steps", description = "d1", points = 10, badgeName = "b1",
            type = AchievementType.STANDARD, earnedAtMillis = unlockSpacing, numAwarded = 5, gameId = 100,
        )
        val unlock2 = Unlock(
            id = 2, title = "Halfway There", description = "d2", points = 20, badgeName = "b2",
            type = AchievementType.STANDARD, earnedAtMillis = 2 * unlockSpacing, numAwarded = 3, gameId = 100,
        )
        val unlock3 = Unlock(
            id = 3, title = "The End", description = "d3", points = 15, badgeName = "b3",
            type = AchievementType.STANDARD, earnedAtMillis = 3 * unlockSpacing, numAwarded = 2, gameId = 100,
        )
        val game = GameRef(
            id = 100, title = "Test Game", iconPath = null, artPaths = emptyList(),
            colorIndex = 0, players = 10, award = null, totalAchievements = totalAchievements,
        )
        val main = PlayerJourney(
            name = "tester", unlocks = listOf(unlock1, unlock2, unlock3),
            games = mapOf(100 to game), medals = emptyList(),
        )
        val frame = JourneyFrame(
            mode = mode, main = main, rivals = emptyList(),
            spanFromMillis = 0L, spanToMillis = 3 * unlockSpacing,
        )
        val layout = LayoutEngine.layout(frame, includeNodes = true)
        return JourneyUiState.Ready(
            frame = frame, layout = layout, compactLayout = layout,
            failedCount = failedCount, rivalErrors = emptyList(), rank = rank,
        )
    }

    /**
     * A span across two games, spread over a fortnight, so a sweep halfway along it is genuinely
     * over one of them and the running strip has something to name.
     */
    private fun spanReadyFixture(): JourneyUiState.Ready {
        val day = 86_400_000L
        val games = mapOf(
            1 to GameRef(1, "Game One", null, emptyList(), 0, 10, null),
            2 to GameRef(2, "Game Two", null, emptyList(), 1, 10, null),
        )
        val unlocks = (0 until 12).map { i ->
            Unlock(
                id = i, title = "A$i", description = "", points = 10, badgeName = "b$i",
                type = AchievementType.STANDARD, earnedAtMillis = day + i * day,
                numAwarded = 5, gameId = if (i < 8) 1 else 2,
            )
        }
        val main = PlayerJourney("tester", unlocks, games, emptyList())
        val frame = JourneyFrame(JourneyMode.SPAN, main, emptyList(), 0L, day + 12 * day)
        val layout = LayoutEngine.layout(frame, includeNodes = true)
        return JourneyUiState.Ready(
            frame = frame, layout = layout, compactLayout = layout,
            failedCount = 0, rivalErrors = emptyList(),
        )
    }

    /**
     * One game, two players: the shape the compare row is for. The rival's rank is known and the
     * main player's is not, which is the ordinary case for a rival added mid-session.
     */
    private fun compareReadyFixture(): JourneyUiState.Ready {
        val base = readyFixture(mode = JourneyMode.GAME, totalAchievements = 12)
        val rival = base.frame.main.copy(
            name = "rival",
            unlocks = base.frame.main.unlocks.map { it.copy(id = it.id + 100, points = 5) },
        )
        val frame = base.frame.copy(rivals = listOf(rival))
        val layout = LayoutEngine.layout(frame, includeNodes = false)
        return base.copy(
            frame = frame,
            layout = layout,
            compactLayout = layout,
            playerRanks = mapOf("rival" to 7),
        )
    }

    /**
     * UI feedback: a single-game compare where the rival has nothing for the game is an answer,
     * not a replay - the player would run a race with one runner in it. The panel stays up, where
     * the empty-rival notice does the telling, and the animation never starts.
     */
    @Test
    fun `a game compare with an empty rival stays on the panel instead of playing`() {
        val base = compareReadyFixture()
        val emptyRival = base.frame.rivals.single().copy(unlocks = emptyList())
        val frame = base.frame.copy(rivals = listOf(emptyRival))
        val state = base.copy(
            frame = frame,
            layout = LayoutEngine.layout(frame, includeNodes = true),
            compactLayout = LayoutEngine.layout(frame, includeNodes = true),
        )

        setContent(state = state, controls = JourneyControls(mode = JourneyMode.GAME))

        composeRule.onNodeWithText("Load").assertExists()
        composeRule.onNodeWithContentDescription("Replay").assertDoesNotExist()
    }

    /** And a rival who did play keeps the ordinary straight-into-the-player behavior. */
    @Test
    fun `a game compare with a real rival still opens in the player`() {
        setContent(state = compareReadyFixture(), controls = JourneyControls(mode = JourneyMode.GAME))

        composeRule.onNodeWithContentDescription("Replay").assertExists()
    }

    /**
     * A journey dense enough to have earned a wider timeline: `layout` is several screenfuls, and
     * `compactLayout` is a genuinely different object fitted into one.
     *
     * The absence of this fixture is what let two bugs through H1 at once - a follow camera still
     * driving the at-rest chart, and closing cards placed against a transform the chart was not
     * using - because every other fixture here has one layout serving as both, where all the
     * arithmetic happens to agree.
     */
    private fun wideReadyFixture(rank: RankChip? = null): JourneyUiState.Ready {
        val day = 86_400_000L
        val game = GameRef(
            id = 100, title = "Dense Game", iconPath = null, artPaths = emptyList(),
            colorIndex = 0, players = 1000, award = null,
        )
        val unlocks = (0 until 400).map { i ->
            Unlock(
                id = i, title = "Unlock $i", description = "d$i", points = 5 + i % 20,
                badgeName = "b$i", type = AchievementType.STANDARD,
                earnedAtMillis = day + i * 3_600_000L, numAwarded = 5 + i, gameId = 100,
            )
        }
        val main = PlayerJourney(
            name = "tester", unlocks = unlocks, games = mapOf(100 to game), medals = emptyList(),
        )
        val frame = JourneyFrame(
            mode = JourneyMode.SPAN, main = main, rivals = emptyList(),
            spanFromMillis = 0L, spanToMillis = day + 400 * 3_600_000L,
        )
        val widthFactor = LayoutEngine.widthFactorFor(frame, includeNodes = true)
        assertTrue("fixture must earn a wider timeline, got $widthFactor", widthFactor > 1f)
        val layout = LayoutEngine.layout(frame, includeNodes = true, widthFactor = widthFactor)
        val compact = LayoutEngine.layout(frame, includeNodes = true, widthFactor = 1f)
        return JourneyUiState.Ready(
            frame = frame, layout = layout, compactLayout = compact, failedCount = 0,
            rivalErrors = emptyList(), rank = rank,
        )
    }

    private fun setContent(
        state: JourneyUiState = JourneyUiState.Idle,
        /** For the tests that move the screen from one state to another mid-test. */
        stateHolder: State<JourneyUiState>? = null,
        /** For the tests that need an export in flight; otherwise the screen builds its own. */
        export: JourneyExportState? = null,
        controls: JourneyControls = JourneyControls(),
        lockedGameId: Int? = null,
        lockedGameTitle: String? = null,
        notice: String? = null,
        playedGames: List<CompletionRow> = emptyList(),
        playedGamesError: Boolean = false,
        rivalRoster: List<String> = emptyList(),
        knownRivals: List<String> = emptyList(),
        showPlayer: Boolean = true,
        /** See the note at the end of this function; the hold's own test turns it off. */
        advanceEndHold: Boolean = true,
        onBack: () -> Unit = {},
        onAddRival: (String) -> Unit = {},
        onForgetRival: (String) -> Unit = {},
        onSelectGame: (Int, String) -> Unit = { _, _ -> },
        onSetRivalColor: (Int) -> Unit = {},
        onSetPlayerColor: (Int) -> Unit = {},
    ) {
        composeRule.setContent {
            // The chart resolves its badge/avatar images through LocalImageLoader; this one fails
            // every request immediately so the tests neither touch the network nor wait on it.
            val loader = ImageLoader.Builder(LocalContext.current)
                .components {
                    add(
                        Interceptor { chain ->
                            ErrorResult(null, chain.request, IllegalStateException("no images in tests"))
                        }
                    )
                }
                .build()
            CompositionLocalProvider(LocalImageLoader provides loader) {
            JourneyScreenContent(
                state = stateHolder?.value ?: state,
                controls = controls,
                userName = "tester",
                notice = notice,
                lockedGameId = lockedGameId,
                lockedGameTitle = lockedGameTitle,
                playedGames = playedGames,
                playedGamesError = playedGamesError,
                rivalRoster = rivalRoster,
                knownRivals = knownRivals,
                onForgetRival = onForgetRival,
                onOpenPlayedGamesPicker = {},
                onSelectGame = onSelectGame,
                onBack = onBack,
                onOpenSettings = {},
                onSetMode = {},
                onSetSpanPreset = {},
                onSetCustomRange = { _, _ -> },
                onAddRival = onAddRival,
                onRemoveRival = {},
                onSetRivalColor = onSetRivalColor,
                onSetPlayerColor = onSetPlayerColor,
                onSetSpeed = {},
                onSetFollowMode = {},
                onLoad = {},
                onRetry = {},
                onDismissNotice = {},
                showPlayer = showPlayer,
                export = export ?: rememberJourneyExportState(),
            )
            }
        }
        // The player holds its control bar back for a moment once the journey lands (see
        // [END_BAR_HOLD_MILLIS]), and these fixtures run with animations off - which the chart
        // treats as reduced motion and jumps straight to the end - so every one of them opens
        // inside that hold. The clock is walked past it here rather than in twenty tests, and the
        // hold itself is pinned by its own test below.
        if (showPlayer && advanceEndHold) {
            composeRule.mainClock.advanceTimeBy(END_BAR_HOLD_MILLIS + 100L)
            composeRule.waitForIdle()
        }
    }

    @Test
    fun `locked state shows the settings CTA`() {
        setContent(state = JourneyUiState.Locked(LockedReason.NO_API_KEY))

        composeRule.onNodeWithText("Open settings").assertIsDisplayed()
        composeRule.onNodeWithText(
            "This screen replays your unlock history and can make many RetroAchievements " +
                "API calls. To use it, add your own RetroAchievements API key in Settings.",
        ).assertExists()
    }

    /**
     * A key stored with no account behind it: the card must ask for the thing that is actually
     * missing rather than for the key the user has already entered.
     */
    @Test
    fun `locked without an account asks for the username`() {
        setContent(state = JourneyUiState.Locked(LockedReason.NO_ACCOUNT))

        composeRule.onNodeWithText(
            "Add your RetroAchievements username in Settings to use the journey.",
        ).assertExists()
        composeRule.onNodeWithText("Open settings").assertIsDisplayed()
    }

    @Test
    fun `loading state shows the progress label`() {
        setContent(state = JourneyUiState.Loading("Fetching 1 of 2: Test Game", 1, 2))

        // The label sits below the header and full controls panel, past what the
        // Robolectric test window shows without scrolling — existence is the signal here,
        // not current viewport visibility.
        composeRule.onNodeWithText("Fetching 1 of 2: Test Game").assertExists()
    }

    /**
     * The loading screen draws a fixed heading with the step's label under it. The first step used
     * to carry that exact heading as its label, so a load - and a single-game load for its whole
     * duration, since one game reports no progress - printed the same line twice.
     */
    @Test
    fun `the loading heading is never printed twice`() {
        setContent(state = JourneyUiState.Loading(PREPARING_LABEL, 0, 0))

        composeRule.onAllNodesWithText(LOADING_TITLE).assertCountEquals(1)
        composeRule.onNodeWithText(PREPARING_LABEL).assertExists()
    }

    /** A step that somehow arrives carrying the heading itself is dropped rather than echoed. */
    @Test
    fun `a step label equal to the heading is not drawn under it`() {
        setContent(state = JourneyUiState.Loading(LOADING_TITLE, 0, 0))

        composeRule.onAllNodesWithText(LOADING_TITLE).assertCountEquals(1)
    }

    /**
     * The roster is a configure control: the chips are on the panel with nothing loaded, and the
     * add button is always live because adding a name costs nothing until Load is pressed.
     *
     * There is no chart here at all - the state is Idle - which is exactly what the panel could not
     * show before: the roster was read off the loaded frame, so a name could not exist until it had
     * been fetched, and the field was gated behind "Load your journey first, then add rivals".
     */
    @Test
    fun `the roster is shown on the configure panel with nothing loaded`() {
        setContent(state = JourneyUiState.Idle, rivalRoster = listOf("Rival1"))

        composeRule.onNodeWithText("Rival1").assertExists()
        composeRule.onNodeWithContentDescription("Remove Rival1").assertExists()
        // One rival: with the slot taken, the chip stands where the field was, and the field
        // comes back when the chip is removed.
        composeRule.onNodeWithContentDescription("Add rival").assertDoesNotExist()
    }

    /** The empty slot offers the field and no explanation line; the colour pickers live in the
     * player's settings sheet now, beside the chart they restyle. */
    @Test
    fun `an empty rival slot offers the field and no colour picker`() {
        setContent(state = JourneyUiState.Idle)

        composeRule.onNodeWithText(RIVAL_COMPARE_LABEL).assertExists()
        composeRule.onNodeWithText("Add one rival", substring = true).assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Add rival").assertIsEnabled()
        composeRule.onNodeWithContentDescription(rivalColorLabel(0, selected = true)).assertDoesNotExist()
    }

    /** Tapping a swatch in the settings sheet reports the pick; the ViewModel holds it. */
    @Test
    fun `tapping a colour swatch reports the pick`() {
        var picked = -1
        var pickedPlayer = -99
        setContent(
            state = readyFixture(),
            onSetRivalColor = { picked = it },
            onSetPlayerColor = { pickedPlayer = it },
        )
        composeRule.onNodeWithContentDescription("Settings").performClick()

        // Invoked through the node's own OnClick action: the sheet sits over the full player
        // here, and coordinate injection through that stack is what JourneyPlayerInputTest
        // covers; this test is about the wiring from swatch to callback.
        composeRule.onNodeWithContentDescription(rivalColorLabel(2, selected = false))
            .performScrollTo()
            .performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(2, picked)

        // The theme-accent/adaptive swatch is retired: the player's row offers only the
        // fixed palette, defaulting to the plain blue (index 2 in spectrum order).
        composeRule.onNodeWithContentDescription(playerColorLabel(-1, selected = true))
            .assertDoesNotExist()
        composeRule.onNodeWithContentDescription(playerColorLabel(2, selected = true))
            .performScrollTo().assertExists()
        composeRule.onNodeWithContentDescription(playerColorLabel(1, selected = false))
            .performScrollTo()
            .performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(1, pickedPlayer)
    }


    @Test
    fun `an empty roster draws no chips`() {
        setContent(state = JourneyUiState.Idle)

        composeRule.onNodeWithText("Rival1").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Add rival").assertIsEnabled()
    }

    /**
     * A loaded journey opens straight into the player: there is no summary page and no Watch button
     * to press any more.
     */
    @Test
    fun `a loaded journey opens in the player`() {
        setContent(state = readyFixture())

        composeRule.onNodeWithContentDescription("Back").assertExists()
        composeRule.onNodeWithText("Watch").assertDoesNotExist()
        composeRule.onNodeWithText("Load").assertDoesNotExist()
    }

    /** Backing out of the player lands on the configure panel with every choice still made. */
    @Test
    fun `leaving the player lands on the configure panel`() {
        setContent(state = readyFixture())

        composeRule.onNodeWithContentDescription("Back").performClick()

        composeRule.onNodeWithText("Load").assertExists()
        composeRule.onNodeWithText("Time span").assertExists()
        composeRule.onNodeWithContentDescription("Replay").assertDoesNotExist()
    }

    /**
     * One share affordance, in the player's bar, and it opens a menu with the two things it can
     * make. The picture used to be a row inside the settings sheet, which put the two ways of
     * sending a journey on in two unrelated places.
     */
    @Test
    fun `the player offers one share action with both kinds behind it`() {
        setContent(state = readyFixture(), showPlayer = true)

        composeRule.onNodeWithText(SHARE_VIDEO_LABEL).assertDoesNotExist()
        composeRule.onNodeWithContentDescription(SHARE_LABEL).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText(SHARE_VIDEO_LABEL).assertExists()
        composeRule.onNodeWithText(SHARE_IMAGE_LABEL).assertExists()
    }

    /** And the settings sheet no longer carries a second one. */
    @Test
    fun `the settings sheet has no share entry of its own`() {
        setContent(state = readyFixture(), showPlayer = true)
        composeRule.onNodeWithContentDescription("Settings").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Speed").assertExists()
        composeRule.onNodeWithText("Share as image").assertDoesNotExist()
        composeRule.onNodeWithText("Picture of the finished chart").assertDoesNotExist()
    }

    /**
     * The journey lands on its closing chart and rests there: for the length of the pause nothing
     * arrives over the finished picture - not the bar, and not the closing counters either, which
     * used to flip in on the very frame the motion stopped and made the landing read as a hard
     * cut. Both come in together when the pause runs out. A tap takes them at once, which is what
     * makes this a pause rather than a lockout.
     */
    @Test
    fun `the end state rests before the bar and the closing counters arrive`() {
        setContent(state = readyFixture(), showPlayer = true, advanceEndHold = false)

        composeRule.onNodeWithText("Longest streak").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Replay").assertDoesNotExist()
        // The back arrow keeps the bar's hours: while the picture rests, nothing is over it.
        composeRule.onNodeWithContentDescription("Back").assertDoesNotExist()

        composeRule.mainClock.advanceTimeBy(END_BAR_HOLD_MILLIS + 100L)
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Longest streak").assertExists()
        composeRule.onNodeWithContentDescription("Replay").assertExists()
        composeRule.onNodeWithContentDescription("Back").assertExists()
    }

    /**
     * The player is a mode rather than a layer: the page it was opened from is not composed while
     * it is up, so the controls panel's own toggles cannot be reached behind it.
     */
    @Test
    fun `the player carries the playback controls and the overview strip`() {
        setContent(state = readyFixture(unlockSpacing = 86_400_000L), showPlayer = true)

        composeRule.onNodeWithContentDescription("Replay").assertExists()
        composeRule.onNodeWithContentDescription("Settings").assertExists()
        // Reduced motion (see the setup) puts the player straight into its end state, so the strip
        // here is the one a finished journey shows. The running counters are pinned in
        // JourneyStripTextTest, which can ask for a point of playback that a rendered test cannot.
        composeRule.onNodeWithText("Longest streak").assertExists()
        composeRule.onNodeWithText("Awards").assertExists()
    }

    /**
     * The view settings: the speed (moved off the bar, which is for playback alone) and the follow
     * camera. Badges or awards, the width of the timeline and the running date all follow from the
     * journey now, so they are not on offer here.
     */
    @Test
    fun `the player settings sheet carries the speed and the follow toggle`() {
        setContent(state = readyFixture(), showPlayer = true)

        composeRule.onNodeWithContentDescription("Settings").performClick()

        composeRule.onNodeWithText("Speed").assertExists()
        composeRule.onNodeWithText("2x").assertExists()
        composeRule.onNodeWithText("Follow the player").assertExists()
        // The idle-sign switch, named by its constant rather than by a copy of the string: it is
        // the one row here that carries a glyph, and a test spelling that out by hand is a test
        // that breaks on a character nobody can see the difference in.
        composeRule.onNodeWithText(IDLE_SIGNS_LABEL).assertExists()
        composeRule.onNodeWithText("Show date overlay").assertDoesNotExist()
        composeRule.onNodeWithText("Hardcore").assertDoesNotExist()
    }

    /**
     * One way out, in the top-right corner, and it never leaves. The bar used to carry a second
     * one, which was the same action offered twice on furniture that fades away while the journey
     * runs.
     */
    @Test
    fun `the player carries exactly one back arrow, leading the top band`() {
        setContent(state = readyFixture(), showPlayer = true)

        composeRule.onNodeWithContentDescription("Back").assertExists()
        // The X in the top right corner is gone: back is one affordance, said one way.
        composeRule.onNodeWithContentDescription("Close").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Close player").assertDoesNotExist()

        // It leads the band rather than hiding in the far corner: top of the screen, start edge.
        val bounds = composeRule.onNodeWithContentDescription("Back").fetchSemanticsNode().boundsInRoot
        val root = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        assertTrue("the back arrow sits in the start half", bounds.left < root.width / 2f)

        composeRule.onNodeWithContentDescription("Back").performClick()

        composeRule.onNodeWithContentDescription("Replay").assertDoesNotExist()
        composeRule.onNodeWithText("Load").assertExists()
    }

    /**
     * And it closes on a tap that moves, which a tap from a real finger always does. The routing
     * fix this pins is described in JourneyPlayerInputTest; this is the top band's back arrow,
     * which sits outside the bar and so outside that test's reach.
     */
    @Test
    fun `the back arrow survives a tap that moves`() {
        setContent(state = readyFixture(), showPlayer = true)

        composeRule.onNodeWithContentDescription("Back").performTouchInput {
            down(center)
            moveBy(Offset(1f, 1f))
            up()
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Replay").assertDoesNotExist()
        composeRule.onNodeWithText("Load").assertExists()
    }

    /**
     * The end state. These tests run with animations off, which is also what the chart treats as
     * reduced motion: it jumps the journey straight to the finished chart, so the player is in its
     * end state as soon as it opens.
     *
     * The closing site rank is off the end row entirely now: a rank is a fact about the whole site,
     * and what a finished span is asked to say is what the journey itself came to. Where a rank
     * still belongs is on a compare row, beside the player it ranks.
     */
    @Test
    fun `a finished journey names no closing rank, whatever it was given`() {
        setContent(
            state = readyFixture(rank = RankChip(RANK_LABEL, "#7"), mode = JourneyMode.GAME),
            controls = JourneyControls(mode = JourneyMode.GAME),
        )

        composeRule.onNodeWithText("#7").assertDoesNotExist()
        composeRule.onNodeWithText(RANK_LABEL).assertDoesNotExist()
    }

    @Test
    fun `a finished span names no closing rank either`() {
        setContent(state = readyFixture(rank = RankChip(RANK_LABEL, "#812")))

        composeRule.onNodeWithText("#812").assertDoesNotExist()
    }

    /** A rank that never arrived is left out entirely rather than shown as an empty chip. */
    @Test
    fun `no rank means no rank chip`() {
        setContent(state = readyFixture())

        composeRule.onNodeWithText(RANK_LABEL).assertDoesNotExist()
    }

    /**
     * The fun facts are gone: the end state is the rank card and nothing else. They restated what
     * the overview strip along the top already counts, at the one moment the chart had just
     * finished saying everything it had to say.
     */
    @Test
    fun `a finished journey shows no fun fact cards`() {
        setContent(state = readyFixture())

        composeRule.onNodeWithText("Rarest Gem").assertDoesNotExist()
        composeRule.onNodeWithText("Biggest Day").assertDoesNotExist()
        composeRule.onNodeWithText("Hot Streak").assertDoesNotExist()
    }

    /**
     * The strip while the journey is still running, which every other test here is blind to: they
     * all pin reduced motion (otherwise the reveal races the test clock) and that jumps playback
     * straight to the end, so the running counters and the now-playing chip were rendered by
     * nothing at all.
     *
     * Driven by handing the overview its own playback holder at the halfway mark, which is exactly
     * what the player hands it.
     */
    @Test
    fun `the running strip counts the date, the points and the game being played`() {
        val state = spanReadyFixture()
        val playback = JourneyPlaybackHolder().apply { progress = 0.5f }

        composeRule.setContent {
            PlayerOverview(
                frame = state.frame,
                layout = state.layout,
                style = rememberChartStyle(),
                playback = playback,
                playerRanks = emptyMap(),
            )
        }

        // Two lines: the running game count in front of the game under the sweep ("1 - Game One"),
        // then the date with the counts. The four labelled chips this replaces could not wrap, so
        // on a phone the last of them was pushed off the right of the screen.
        composeRule.onNodeWithText("- Game One", substring = true).assertExists()
        // The second line is fixed-width slots now - date-time, achievements, points, each its
        // own node so a growing string cannot shuffle its neighbours - and no game count, which
        // rides the title.
        composeRule.onNodeWithText("achievements", substring = true).assertExists()
        val points = allText().single { it.endsWith("pts") || it.endsWith("pt") }
        assertTrue("the points slot is just the points: $points", !points.contains("game"))
        assertTrue("no game count anywhere on the line", allText().none { it.endsWith("games") })
        // And none of the end state, which is a different set of words entirely.
        composeRule.onNodeWithText("Longest").assertDoesNotExist()
        composeRule.onNodeWithText("Streak").assertDoesNotExist()
    }

    /** The same strip at the end, driven the same way, so the two states are pinned side by side. */
    @Test
    fun `the strip swaps to the end state once playback lands`() {
        val state = spanReadyFixture()
        val playback = JourneyPlaybackHolder().apply { progress = 1f }

        composeRule.setContent {
            PlayerOverview(
                frame = state.frame,
                layout = state.layout,
                style = rememberChartStyle(),
                playback = playback,
                playerRanks = emptyMap(),
            )
        }

        composeRule.onNodeWithText("Games").assertExists()
        composeRule.onNodeWithText("Achievements").assertExists()
        composeRule.onNodeWithText("Longest streak").assertExists()
        // No Points chip: the chart's labelled ceiling line carries the exact total now, in
        // the accent and with its own "pts" - the strip repeating it was the redundancy.
        composeRule.onNodeWithText("Points").assertDoesNotExist()
        composeRule.onNodeWithText("Date").assertDoesNotExist()
    }

    /**
     * What a finished span is left saying: the game it kept going back to, the longest run of days
     * it managed, and how much of both there was. The running counters it replaces have nothing
     * left to add at that point - the date has become the whole window, and the points are the
     * chart's own axis.
     */
    @Test
    fun `a finished span names its games, its achievements, its awards and its streak`() {
        setContent(state = readyFixture(unlockSpacing = 86_400_000L))

        composeRule.onNodeWithText("Games").assertExists()
        composeRule.onNodeWithText("Achievements").assertExists()
        composeRule.onNodeWithText("Awards").assertExists()
        composeRule.onNodeWithText("Longest streak").assertExists()
        composeRule.onNodeWithText("Points").assertDoesNotExist()
        // The running date is gone with the landing.
        composeRule.onNodeWithText("Date").assertDoesNotExist()
    }

    /**
     * UI feedback: a span with somebody else on it finishes on the per-player rows alone - one
     * line per player, the mode's whole point. The block of this player's own totals that used to
     * stack above them is gone: one player's counters are the wrong headline for a chart about
     * who did what against whom, and the two blocks together were most of a handheld's screen.
     */
    @Test
    fun `a compare span finishes on the per-player comparison alone`() {
        val state = spanCompareReadyFixture()
        val playback = JourneyPlaybackHolder().apply { progress = 1f }

        composeRule.setContent {
            PlayerOverview(
                frame = state.frame,
                layout = state.layout,
                style = rememberChartStyle(),
                playback = playback,
                playerRanks = emptyMap(),
            )
        }

        composeRule.onNodeWithText("tester").assertExists()
        composeRule.onNodeWithText("aria").assertExists()
        // The owner's own totals cards are gone with the running counters they replaced.
        composeRule.onNodeWithText("Games").assertDoesNotExist()
        composeRule.onNodeWithText("Longest streak").assertDoesNotExist()
        composeRule.onNodeWithText("Date").assertDoesNotExist()
    }

    /**
     * And a journey that happened in one sitting dashes the counter rather than dropping it. The
     * end row is four chips, always the same four; a run of one day is not a run, and a dash is how
     * a fixed row says so.
     */
    @Test
    fun `a one sitting journey finishes on a dashed streak`() {
        setContent(state = readyFixture())

        composeRule.onNodeWithText("Longest streak").assertExists()
        composeRule.onNodeWithText("-").assertExists()
    }

    /**
     * UI feedback: the end counters are one panel, not four cards. A single surface with every
     * fact on the same line replaces the row of boxes, so the end state reads as one sentence -
     * and a screen reader gets it as one passage too.
     */
    @Test
    fun `the span end counters sit on one panel`() {
        setContent(state = readyFixture())

        composeRule.onNodeWithTag("endPanel", useUnmergedTree = false)
            .assert(hasText("Games"))
            .assert(hasText("Achievements"))
            .assert(hasText("Awards"))
            .assert(hasText("Longest streak"))
    }

    /**
     * A single-game chart counts its own achievement list instead. The games count there is 1 from
     * the first badge to the last, while "how many of this game's achievements have landed" is the
     * one number that chart cannot show any other way.
     */
    @Test
    fun `a single game journey carries the game's own header`() {
        setContent(
            state = readyFixture(mode = JourneyMode.GAME, totalAchievements = 12),
            controls = JourneyControls(mode = JourneyMode.GAME),
        )

        composeRule.onNodeWithText("Test Game").assertExists()
        // Both counts on the second line, the achievements first: the header is two slim lines,
        // not four, and the date rides the title's own line.
        composeRule.onNodeWithText("3 of 12 achievements", substring = true).assertExists()
        composeRule.onNodeWithText("25%").assertExists()
        // And none of the counters it replaced.
        composeRule.onNodeWithText("Games").assertDoesNotExist()
        composeRule.onNodeWithText("Awards").assertDoesNotExist()
    }

    /**
     * A game cached before the total existed has nothing to divide by, so the header counts the
     * player's own unlocks instead - which for a finished journey is all of them.
     */
    @Test
    fun `an unknown achievement total falls back to the unlock count`() {
        setContent(
            state = readyFixture(mode = JourneyMode.GAME),
            controls = JourneyControls(mode = JourneyMode.GAME),
        )

        composeRule.onNodeWithText("All 3 achievements", substring = true).assertExists()
        composeRule.onNodeWithText("100%").assertExists()
    }

    /**
     * A single-game chart with rivals on it shows the players against each other instead of the
     * generic counters: there is one game, one achievement list and one date range there, and how
     * the players did next to each other is the only question the mode is for.
     */
    @Test
    fun `a single game compare chart shows a line per player`() {
        setContent(state = compareReadyFixture())

        composeRule.onNodeWithText("tester").assertExists()
        composeRule.onNodeWithText("rival").assertExists()
        // One line per player, so the rank sits in the same string as the duration and the score.
        composeRule.onNodeWithText("Rank #7", substring = true).assertExists()
        composeRule.onNodeWithText("Achievements").assertDoesNotExist()
        composeRule.onNodeWithText("Streak").assertDoesNotExist()
    }

    /**
     * The wide journey is the one all this is for. At rest - which is where a finished journey
     * ends up - the player must be on the compact chart, fitted whole, rather than on a magnified
     * slice of the wide one under a follow camera that has nothing left to follow.
     */
    @Test
    fun `a wide journey settles onto the fitted compact chart`() {
        setContent(state = wideReadyFixture())

        // "Wide timeline" is the offer, which means the compact chart is what is on screen.
        composeRule.onNodeWithContentDescription("Wide timeline").assertExists()
        composeRule.onNodeWithContentDescription("Fit the whole journey").assertDoesNotExist()
    }

    /**
     * In the strip the rank is a counter like the others, so the label the ViewModel resolves is
     * what names it. Still no denominator: a leaderboard rank and a game's player count are two
     * different populations.
     */
    @Test
    fun `a wide journey ends on the same four counters a narrow one does`() {
        setContent(
            state = wideReadyFixture(rank = RankChip(RANK_LABEL, "#3")),
            controls = JourneyControls(mode = JourneyMode.SPAN),
        )

        composeRule.onNodeWithText("Games").assertIsDisplayed()
        composeRule.onNodeWithText("Achievements").assertIsDisplayed()
        composeRule.onNodeWithText("Longest streak").assertIsDisplayed()
        composeRule.onNodeWithText("#3").assertDoesNotExist()
    }

    /**
     * A partial fetch failure still draws a chart, and nothing else on this screen can say what is
     * missing from it now that the ranking list is gone.
     */
    @Test
    fun `a partial fetch failure is admitted to on the player`() {
        setContent(state = readyFixture(failedCount = 2))

        composeRule.onNodeWithText("2 games couldn't be fetched.").assertExists()
        composeRule.onNodeWithText("Retry").assertExists()
    }

    @Test
    fun `the missing games notice can be dismissed`() {
        setContent(state = readyFixture(failedCount = 1))

        composeRule.onNodeWithText("1 game couldn't be fetched.").assertExists()
        composeRule.onNodeWithContentDescription("Dismiss").performClick()

        composeRule.onNodeWithText("1 game couldn't be fetched.").assertDoesNotExist()
    }

    @Test
    fun `a complete journey says nothing about missing games`() {
        setContent(state = readyFixture())

        composeRule.onNodeWithText("Retry").assertDoesNotExist()
    }

    /**
     * The default is the compact chart in every state, playback included, and the wide timeline is
     * what the button offers. It used to swap automatically - dense at rest, wide in motion - which
     * put the reveal on a chart three or four screenfuls wide with a camera panning across it, and
     * took the user's choice back on every play and pause edge.
     *
     * Pinned wide, the choice survives a replay: it lasts as long as the loaded journey.
     */
    @Test
    fun `a chart pinned wide stays wide through a replay`() {
        setContent(state = wideReadyFixture())

        composeRule.onNodeWithContentDescription("Wide timeline").performClick()
        composeRule.onNodeWithContentDescription("Fit the whole journey").assertExists()

        composeRule.onNodeWithContentDescription("Replay").performClick()
        composeRule.mainClock.advanceTimeBy(END_BAR_HOLD_MILLIS + 100L)
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Fit the whole journey").assertExists()
        composeRule.onNodeWithContentDescription("Wide timeline").assertDoesNotExist()
    }

    /**
     * The end row is a row: every chip the same height, whatever it carries. They measured to their
     * own contents - a label and a value, a value alone, or nothing but award circles - so the row
     * read as boxes at three different sizes with their tops and bottoms in four different places.
     *
     * Asserted through the text runs' vertical centres, which is what a fixed height and a centred
     * row give: fake font metrics move the glyphs, not the box they are centred in.
     */
    @Test
    fun `every chip on the end row is the same height`() {
        val state = awardedSpanFixture()
        val playback = JourneyPlaybackHolder().apply { progress = 1f }

        composeRule.setContent {
            PlayerOverview(
                frame = state.frame,
                layout = state.layout,
                style = rememberChartStyle(),
                playback = playback,
                playerRanks = emptyMap(),
            )
        }

        // Every run on the row: the labels, the values, and the award counts beside the circles.
        val centres = endRowTextCentres()
        assertTrue("the end row has chips to compare: $centres", centres.size >= 4)
        val spread = centres.max() - centres.min()
        assertTrue("every chip must share one centre line, spread was $spread", spread < 1.5f)
    }

    /** Every text run currently on screen. */
    private fun allText(): List<String> {
        val nodes = composeRule.onAllNodes(
            androidx.compose.ui.test.SemanticsMatcher("has text") {
                it.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Text)?.isNotEmpty() == true
            },
            useUnmergedTree = true,
        )
        return (0 until nodes.fetchSemanticsNodes().size).map { i ->
            nodes[i].fetchSemanticsNode()
                .config[androidx.compose.ui.semantics.SemanticsProperties.Text]
                .joinToString(" ")
        }
    }

    /** The vertical centre of every text run the end row draws. */
    private fun endRowTextCentres(): List<Float> {
        val nodes = composeRule.onAllNodes(
            androidx.compose.ui.test.SemanticsMatcher("has text") {
                it.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Text)?.isNotEmpty() == true
            },
            useUnmergedTree = true,
        )
        return (0 until nodes.fetchSemanticsNodes().size).map { i ->
            nodes[i].fetchSemanticsNode().boundsInRoot.center.y
        }
    }

    /**
     * A span that actually won something, so the end row carries the awards chip - the one that has
     * no label and no value and draws circles instead, and so the one that measured differently
     * from every other chip beside it.
     */
    private fun awardedSpanFixture(): JourneyUiState.Ready {
        val day = 86_400_000L
        val games = mapOf(
            1 to GameRef(1, "Game One", null, emptyList(), 0, 10, null),
            2 to GameRef(2, "Game Two", null, emptyList(), 1, 10, null),
        )
        val unlocks = (0 until 8).map { i ->
            Unlock(
                id = i, title = "A$i", description = "", points = 10, badgeName = "b$i",
                type = AchievementType.STANDARD, earnedAtMillis = day + i * day,
                numAwarded = 5, gameId = if (i < 5) 1 else 2,
            )
        }
        val medals = listOf(
            com.twinlore.app.journey.domain.model.Medal(
                com.twinlore.app.journey.domain.model.AwardKind.MASTERED, 3 * day, 1, null, "Game One",
            ),
        )
        val main = PlayerJourney("tester", unlocks, games, medals)
        val frame = JourneyFrame(JourneyMode.SPAN, main, emptyList(), 0L, 9 * day)
        val layout = LayoutEngine.layout(frame, includeNodes = true)
        return JourneyUiState.Ready(
            frame = frame, layout = layout, compactLayout = layout,
            failedCount = 0, rivalErrors = emptyList(),
        )
    }

    /** The view button is on the bar, and it offers the view that is not on screen. */
    @Test
    fun `the player bar offers the other chart`() {
        setContent(state = readyFixture())

        composeRule.onNodeWithContentDescription("Wide timeline").assertExists()
    }

    /** The speed is a setting now, not a control on the bar. */
    @Test
    fun `the player bar does not carry the speed`() {
        setContent(state = readyFixture(), showPlayer = true)

        composeRule.onNodeWithText("1x").assertDoesNotExist()
        composeRule.onNodeWithText("2x").assertDoesNotExist()
    }

    @Test
    fun `locked game id hides the time span mode option`() {
        setContent(
            state = JourneyUiState.Idle,
            lockedGameId = 100,
            lockedGameTitle = "Test Game",
        )

        composeRule.onNodeWithText("Time span").assertDoesNotExist()
        composeRule.onNodeWithText("Game id").assertDoesNotExist()
        composeRule.onNodeWithText("Load").assertDoesNotExist()
        composeRule.onNodeWithText("Test Game").assertIsDisplayed()
    }

    @Test
    fun `GAME mode shows the choose-a-game placeholder`() {
        setContent(
            state = JourneyUiState.Idle,
            controls = JourneyControls(mode = JourneyMode.GAME),
        )

        composeRule.onNodeWithText("Choose a game").assertIsDisplayed()
    }

    @Test
    fun `GAME mode picker lists played games once opened`() {
        val playedGames = listOf(
            CompletionRow(
                gameId = 42, title = "Chrono Trigger", iconPath = null,
                numAwarded = 10, numAwardedHardcore = 0,
                mostRecentAwardedDate = null, highestAwardKind = null, highestAwardDate = null,
            ),
        )
        setContent(
            state = JourneyUiState.Idle,
            controls = JourneyControls(mode = JourneyMode.GAME),
            playedGames = playedGames,
        )

        composeRule.onNodeWithText("Choose a game").performClick()

        composeRule.onNodeWithText("Chrono Trigger").assertExists()
    }

    /** Back is the way out of the player, and it lands on the configure panel, not on the library. */
    @Test
    fun `back closes the player and returns to the configure panel`() {
        setContent(state = readyFixture(), showPlayer = true)
        composeRule.onNodeWithContentDescription("Back").assertExists()

        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Replay").assertDoesNotExist()
        composeRule.onNodeWithText("Load").assertExists()
    }

    /**
     * Back takes away the innermost layer that is open, not the whole player. Dismissing the
     * settings sheet used to throw the player away with it, and on a vault journey that is two
     * levels in one press.
     */
    @Test
    fun `back with the settings sheet open closes the sheet only`() {
        setContent(state = readyFixture(), showPlayer = true)
        composeRule.onNodeWithContentDescription("Settings").performClick()
        composeRule.onNodeWithText("Speed").assertExists()

        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Speed").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Back").assertExists()

        // And the next one is the player's, now that there is nothing in front of it.
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Replay").assertDoesNotExist()
    }

    /**
     * The same one-layer rule for a finger: a tap anywhere outside the sheet's panel takes the
     * sheet and only the sheet. Before this, the whole bottom band shielded itself full-width, so
     * on a landscape screen the wide gutter beside the sheet swallowed the tap and nothing moved.
     */
    @Test
    @org.robolectric.annotation.Config(qualifiers = "w640dp-h360dp-land")
    fun `a tap outside the settings sheet closes the sheet only`() {
        setContent(state = readyFixture(), showPlayer = true)
        composeRule.onNodeWithContentDescription("Settings").performClick()
        composeRule.onNodeWithText("Speed").assertExists()

        // The gutter to the right of the sheet's 460dp panel, just above the bar: outside the
        // sheet's UI area, on none of the furniture, and in the emptiest corner of a chart whose
        // curve runs bottom-left to top-right - so the tap can only be a MISSED one.
        composeRule.onRoot().performTouchInput {
            down(androidx.compose.ui.geometry.Offset(width - 6f, height - 80f))
            moveBy(androidx.compose.ui.geometry.Offset(-1f, -1f))
            up()
        }
        // The chart's tap detector also listens for double taps, so a single tap only lands
        // after the double-tap window has run out; the window lives on the compose clock.
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Speed").assertDoesNotExist()
        // The bar itself survives the tap: one layer per tap, exactly like back.
        composeRule.onNodeWithContentDescription("Settings").assertExists()
    }

    /**
     * A vault entry never had a configure panel: it picked the game and loaded itself, so leaving
     * the player leaves the journey rather than dropping the user on controls that are all dead.
     */
    @Test
    fun `a vault entry leaves the screen when the player is closed`() {
        var backs = 0
        setContent(
            state = readyFixture(),
            controls = JourneyControls(mode = JourneyMode.GAME),
            lockedGameId = 100,
            lockedGameTitle = "Test Game",
            onBack = { backs++ },
        )

        composeRule.onNodeWithContentDescription("Back").performClick()

        assertEquals(1, backs)
    }

    /** An error is the configure panel carrying a line about what went wrong, plus a way to retry. */
    @Test
    fun `retryable error shows retry beside the controls`() {
        setContent(state = JourneyUiState.Error("Could not load data", retryable = true))

        composeRule.onNodeWithText("Retry").assertExists()
        composeRule.onNodeWithText("Load").assertExists()
    }

    @Test
    fun `an empty result keeps the controls on screen`() {
        setContent(state = JourneyUiState.Empty("No unlocks found here."))

        composeRule.onNodeWithText("No unlocks found here.").assertExists()
        composeRule.onNodeWithText("Load").assertExists()
    }


    // ---- watching the loaded journey again ----

    /**
     * Closing the player used to be a one-way door. The Ready state, its decoded badges and its
     * measured paths were all still in composition and unreachable: the only way back to the
     * journey on screen was a Load that refetched every player on it.
     */
    @Test
    fun `the loaded journey can be watched again from the panel`() {
        setContent(state = readyFixture(), showPlayer = false)

        // The panel scrolls (the colour picker pushed the Load row below a 470px viewport), so
        // the button is brought on screen the way a finger would have to.
        composeRule.onNodeWithText("Watch again").performScrollTo().performClick()
        // Let the player mount (and its end-state hold START) before walking the clock past the
        // hold; advancing in the same breath as the click spends the time before the hold exists.
        composeRule.waitForIdle()
        // The player opens on a finished journey (reduced motion), so its bar is inside the
        // end-state hold. Waited for rather than walked to with one fixed advance: the hold is not
        // the only timed effect the player mounts (the backdrop holds a picture for a moment before
        // swapping, see JourneyBackdrop), so "enough clock" is not a number this test should be
        // guessing at.
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.onAllNodesWithContentDescription("Replay").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithContentDescription("Replay").assertExists()
    }

    /**
     * And only while the controls still describe what is loaded. Change the span and the button
     * goes: what it would replay is no longer the journey the panel is asking for.
     */
    @Test
    fun `changing a control takes the watch again affordance away`() {
        setContent(
            state = readyFixture(),
            controls = JourneyControls(spanPreset = SpanPreset.Y1),
            showPlayer = false,
        )

        composeRule.onNodeWithText("Load").assertExists()
        composeRule.onNodeWithText("Watch again").assertDoesNotExist()
    }

    @Test
    fun `nothing loaded offers nothing to watch`() {
        setContent(state = JourneyUiState.Idle)

        composeRule.onNodeWithText("Watch again").assertDoesNotExist()
    }

    /**
     * Nor while a roster name has never been fetched. The roster is configure state, so it can be
     * changed without pressing Load - and re-entering the player would replay a chart missing
     * whoever was just added, under a panel showing their chip.
     */
    @Test
    fun `a rival added since the load takes the watch again affordance away`() {
        setContent(state = readyFixture(), rivalRoster = listOf("aria"), showPlayer = false)

        composeRule.onNodeWithText("Load").assertExists()
        composeRule.onNodeWithText("Watch again").assertDoesNotExist()
    }

    // ---- the roster's three states ----

    /**
     * A name typed since the last Load is on no curve anywhere, so its chip must not look like one
     * that is: it is outlined and dimmed rather than solid, and it says so out loud, because an
     * outline is nothing to a screen reader.
     *
     * It is emphatically not an error either - nobody has tried to fetch it - so no error notice
     * appears and no red chip.
     */
    @Test
    fun `a roster name the chart has never seen reads as pending`() {
        setContent(state = readyFixture(), rivalRoster = listOf("aria"), showPlayer = false)

        composeRule.onNodeWithContentDescription(pendingChipText("aria")).assertExists()
        composeRule.onNodeWithText("Could not fetch data for aria.").assertDoesNotExist()
    }

    /** A rival that IS on the loaded chart is an ordinary chip, with nothing pending said about it. */
    @Test
    fun `a loaded rival is not marked pending`() {
        val state = spanCompareReadyFixture()

        setContent(state = state, rivalRoster = listOf("aria"), showPlayer = false)

        composeRule.onNodeWithText("aria").assertExists()
        composeRule.onNodeWithContentDescription(pendingChipText("aria")).assertDoesNotExist()
    }

    /**
     * The rule itself, without a composition: a roster name is pending when the loaded frame has
     * neither a curve nor a failure for it. Case-insensitively, because a name is re-spelled the
     * way RetroAchievements spells it once a response says so - "aria" typed, "Aria" loaded, and
     * one chip, not a pending one beside a loaded one.
     */
    @Test
    fun `pending is roster minus what the chart knows about, whatever the case`() {
        val loaded = spanCompareReadyFixture()

        assertEquals(emptyList<String>(), pendingRivals(listOf("ARIA"), loaded))
        assertEquals(listOf("brix"), pendingRivals(listOf("aria", "brix"), loaded))
        assertEquals(
            "an errored rival has been tried, so it is not pending",
            emptyList<String>(),
            pendingRivals(listOf("bob"), readyFixture().copy(rivalErrors = listOf("bob"))),
        )
        assertEquals(
            "with nothing loaded at all, every name is pending",
            listOf("aria"),
            pendingRivals(listOf("aria"), JourneyUiState.Idle),
        )
    }

    // ---- the export outlives the state it was started from ----

    private fun exportRequest(ready: JourneyUiState.Ready) = JourneyExportRequest(
        layout = ready.compactLayout,
        frame = ready.frame,
        style = ChartStyle(
            background = Color.Black, panel = Color.DarkGray, grid = Color.Gray,
            ink = Color.White, inkDim = Color.LightGray, accent = Color.Cyan,
            seriesColors = listOf(Color.Magenta), rivalColors = listOf(Color.Green),
            typeColors = AchievementType.values().associateWith { Color.Red },
            silver = Color.LightGray, gold = Color.Yellow, labelFont = FontFamily.Default,
        ),
        bitmaps = emptyMap(),
        art = emptyMap(),
        userName = "tester",
        speed = 1f,
    )

    /**
     * An encode is minutes of software rendering. It used to be owned by the Ready branch, so
     * pressing Load - the button directly under its own progress row - cancelled it, and the
     * "Export cancelled." notice was written into an object that was already being discarded, so
     * nothing on screen ever said it had died.
     */
    @Test
    fun `an export outlives the load that takes the screen off the ready state`() {
        val gate = CompletableDeferred<Unit>()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val export = JourneyExportState(
            exportVideo = { _, _, _ -> gate.await(); Uri.parse("content://video/1") },
            exportImage = { _, _ -> Uri.parse("content://image/1") },
            scope = scope,
        )
        val ready = readyFixture()
        val state = mutableStateOf<JourneyUiState>(ready)
        setContent(stateHolder = state, export = export, showPlayer = false)

        export.start(exportRequest(ready))
        composeRule.waitForIdle()
        composeRule.onNodeWithText(EXPORTING_LABEL).assertExists()

        // Load: the screen goes back to the loading panel, and the encode must not go with it.
        composeRule.runOnUiThread { state.value = JourneyUiState.Loading(PREPARING_LABEL, 0, 0) }
        composeRule.waitForIdle()

        assertTrue("the encode must still be running", export.progress != null)
        composeRule.onNodeWithText(EXPORTING_LABEL).assertExists()
        composeRule.onNodeWithText("Cancel").assertExists()
        gate.complete(Unit)
        scope.cancel()
    }

    /**
     * The frame counter is gone: a ring says "still going" without asking anyone to read figures.
     *
     * In the notice the ring is described by the line right beside it, so it carries no content
     * description of its own - a screen reader that read both would say the same sentence twice.
     */
    @Test
    fun `a running export shows a ring rather than a frame count`() {
        val gate = CompletableDeferred<Unit>()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val export = JourneyExportState(
            exportVideo = { _, _, _ -> gate.await(); Uri.parse("content://video/1") },
            exportImage = { _, _ -> Uri.parse("content://image/1") },
            scope = scope,
        )
        val ready = readyFixture()
        val state = mutableStateOf<JourneyUiState>(ready)
        setContent(stateHolder = state, export = export, showPlayer = false)

        export.start(exportRequest(ready))
        composeRule.waitForIdle()

        val progress = export.progress!!
        composeRule.onAllNodesWithText("${progress.first}/${progress.second}").assertCountEquals(0)
        composeRule.onAllNodesWithText("Exporting frame", substring = true).assertCountEquals(0)
        composeRule.onNodeWithText(EXPORTING_LABEL).assertExists()
        composeRule.onAllNodesWithContentDescription(EXPORTING_LABEL).assertCountEquals(0)
        gate.complete(Unit)
        scope.cancel()
    }

    /** On the player's bar the ring is all there is, so there it is the one that speaks. */
    @Test
    fun `the player bar's export ring keeps its content description`() {
        val gate = CompletableDeferred<Unit>()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val export = JourneyExportState(
            exportVideo = { _, _, _ -> gate.await(); Uri.parse("content://video/1") },
            exportImage = { _, _ -> Uri.parse("content://image/1") },
            scope = scope,
        )
        val ready = readyFixture()
        val state = mutableStateOf<JourneyUiState>(ready)
        setContent(stateHolder = state, export = export, showPlayer = true)

        export.start(exportRequest(ready))
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription(EXPORTING_LABEL).assertExists()
        gate.complete(Unit)
        scope.cancel()
    }

    // ---- the game picker ----

    private fun playedGame(id: Int) = CompletionRow(
        gameId = id, title = "Game $id", iconPath = null,
        numAwarded = 10, numAwardedHardcore = 0,
        mostRecentAwardedDate = null, highestAwardKind = null, highestAwardDate = null,
    )

    /**
     * Scrolls the picker's list to [index] and waits for it to settle.
     *
     * The list is lazy, so a row outside the viewport is not merely off screen - it is not in the
     * semantics tree at all, and `assertExists` on it fails. That is the point of the change (a
     * search over a large library used to compose every match at once, which is what made the first
     * keystroke freeze), so the tests that reach past the visible handful scroll to it the way a
     * person would.
     */
    private fun scrollPickerTo(index: Int) {
        composeRule.onNode(hasScrollToIndexAction()).performScrollToIndex(index)
        composeRule.waitForIdle()
    }

    /**
     * The picker opens on the last handful of games played rather than on the player's whole
     * history, which for anyone who has been on the site a while is hundreds of rows in one menu.
     */
    @Test
    fun `the picker opens on the last five games played`() {
        setContent(
            state = JourneyUiState.Idle,
            controls = JourneyControls(mode = JourneyMode.GAME),
            playedGames = (1..20).map(::playedGame),
        )

        composeRule.onNodeWithText("Choose a game").performClick()

        // The fifth game is the last the first page reveals, and the sixth is past its end: the
        // list can be scrolled to index 4 and not to index 5.
        scrollPickerTo(PICKER_FIRST_PAGE - 1)
        composeRule.onNodeWithText("Game 5").assertExists()
        composeRule.onNodeWithText("Game 6").assertDoesNotExist()
        composeRule.onNodeWithText("Show 15 more").assertExists()
    }

    /**
     * UI feedback: the expand row is pinned under the list's own scroll area rather than riding
     * at the bottom of an ever-growing menu - which put it below the fold after one press, where
     * it read as sometimes-not-selectable - and each press reveals twenty-five games, saying how
     * many it will actually show when fewer remain.
     */
    @Test
    fun `show more is pinned and reveals twenty-five at a time`() {
        setContent(
            state = JourneyUiState.Idle,
            controls = JourneyControls(mode = JourneyMode.GAME),
            playedGames = (1..40).map(::playedGame),
        )

        composeRule.onNodeWithText("Choose a game").performClick()
        composeRule.onNodeWithText("Show 25 more").performClick()

        scrollPickerTo(29)
        composeRule.onNodeWithText("Game 30", substring = false).assertExists()
        composeRule.onNodeWithText("Game 31").assertDoesNotExist()
        // Ten left, and the pinned row says so.
        composeRule.onNodeWithText("Show 10 more").assertExists()
    }

    /**
     * And the press moves the list to where the new rows joined on. Appending them below a bounded
     * viewport was invisible: on device the press read as having done nothing at all.
     */
    @Test
    fun `show more scrolls the new games into view`() {
        setContent(
            state = JourneyUiState.Idle,
            controls = JourneyControls(mode = JourneyMode.GAME),
            playedGames = (1..40).map(::playedGame),
        )

        composeRule.onNodeWithText("Choose a game").performClick()
        // Before the press the list is at the top, on the first game.
        composeRule.onNodeWithText("Game 1").assertExists()

        composeRule.onNodeWithText("Show 25 more").performClick()
        composeRule.waitForIdle()

        // After it, the list has moved off the top: the last game of the old page is in view and
        // the first game is not.
        composeRule.onNodeWithText("Game 1").assertDoesNotExist()
        composeRule.onNodeWithText("Game 5").assertExists()
        composeRule.onNodeWithText("Game 6").assertExists()
    }

    /** And it goes away once there is nothing left to reveal. */
    @Test
    fun `show more disappears at the end of the list`() {
        setContent(
            state = JourneyUiState.Idle,
            controls = JourneyControls(mode = JourneyMode.GAME),
            playedGames = (1..12).map(::playedGame),
        )

        composeRule.onNodeWithText("Choose a game").performClick()
        composeRule.onNodeWithText("Show 7 more").performClick()

        scrollPickerTo(11)
        composeRule.onNodeWithText("Game 12").assertExists()
        composeRule.onNodeWithText("more", substring = true).assertDoesNotExist()
    }

    /**
     * Search runs over every played game, not over the handful currently revealed. A search that
     * could only find what was already on screen would be worse than none, and it is the way to
     * reach a game a hundred rows down without pressing "Show more" ten times.
     */
    @Test
    fun `search reaches a game far past the revealed page`() {
        setContent(
            state = JourneyUiState.Idle,
            controls = JourneyControls(mode = JourneyMode.GAME),
            playedGames = (1..20).map(::playedGame) + listOf(
                playedGame(99).copy(title = "Chrono Trigger"),
            ),
        )

        composeRule.onNodeWithText("Choose a game").performClick()
        composeRule.onNodeWithText("Chrono Trigger").assertDoesNotExist()

        composeRule.onNodeWithText("Search games").performTextInput("chrono")
        // Typing settles before the list follows it, so results arrive a debounce later. The wait
        // is the assertion: without the query ever being applied this never returns.
        composeRule.waitUntil(timeoutMillis = 2_000) {
            composeRule.onAllNodesWithText("Chrono Trigger").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText("Chrono Trigger").assertExists()
        // Searching is not paged, so nothing offers to reveal more of it.
        composeRule.onNodeWithText("more", substring = true).assertDoesNotExist()
    }

    /** While the list is still catching up with what has been typed, the field says so. */
    @Test
    fun `the search field shows a wheel until the list matches what was typed`() {
        setContent(
            state = JourneyUiState.Idle,
            controls = JourneyControls(mode = JourneyMode.GAME),
            playedGames = (1..20).map(::playedGame) + listOf(
                playedGame(99).copy(title = "Chrono Trigger"),
            ),
        )

        composeRule.onNodeWithText("Choose a game").performClick()
        composeRule.onNodeWithContentDescription(PICKER_SEARCHING_LABEL, useUnmergedTree = true)
            .assertDoesNotExist()

        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithText("Search games").performTextInput("chrono")
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithContentDescription(PICKER_SEARCHING_LABEL, useUnmergedTree = true)
            .assertExists()

        composeRule.mainClock.autoAdvance = true
        composeRule.waitUntil(timeoutMillis = 2_000) {
            composeRule.onAllNodesWithText("Chrono Trigger").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription(PICKER_SEARCHING_LABEL, useUnmergedTree = true)
            .assertDoesNotExist()
    }

    /** Nobody knows their RetroAchievements game ids; the picker and the search are the way in. */
    @Test
    fun `the picker no longer offers manual id entry`() {
        setContent(
            state = JourneyUiState.Idle,
            controls = JourneyControls(mode = JourneyMode.GAME),
            playedGames = listOf(playedGame(1)),
        )

        composeRule.onNodeWithText("Choose a game").performClick()

        composeRule.onNodeWithText("Enter id manually").assertDoesNotExist()
        composeRule.onNodeWithText("Game id").assertDoesNotExist()
    }

    // ---- the rival field ----

    /**
     * Rivals already in the cache are offered as chips so a returning user picks a name instead of
     * retyping it, and each carries a dismiss: the recents are a memory of what has been loaded,
     * and a name nobody wants back should not sit there for good (user request).
     */
    @Test
    fun `recent rivals can be added with a press and forgotten with the dismiss`() {
        val added = mutableListOf<String>()
        val forgotten = mutableListOf<String>()
        setContent(
            state = JourneyUiState.Idle,
            knownRivals = listOf("aria", "brix"),
            onAddRival = { added += it },
            onForgetRival = { forgotten += it },
        )

        composeRule.onNodeWithText("aria").performClick()
        assertEquals(listOf("aria"), added)

        composeRule.onNodeWithContentDescription(forgetRivalLabel("brix")).performClick()
        assertEquals(listOf("brix"), forgotten)
    }

    /** Enter adds the rival, exactly as the plus button beside it does. */
    @Test
    fun `pressing done on the rival field adds the rival`() {
        val added = mutableListOf<String>()
        setContent(
            state = JourneyUiState.Idle,
            onAddRival = { added += it },
        )

        // The field opens on a press now (NAV/EDIT, like the settings screen's fields), so the
        // test walks the same two steps a finger or a controller does.
        composeRule.onNodeWithText("Username").performClick()
        composeRule.onNodeWithText("Username").performTextInput("aria")
        composeRule.onNodeWithText("aria").performImeAction()

        assertEquals(listOf("aria"), added)
    }

    @Test
    fun `an empty rival field submits nothing`() {
        val added = mutableListOf<String>()
        setContent(
            state = JourneyUiState.Idle,
            onAddRival = { added += it },
        )

        composeRule.onNodeWithText("Username").performClick()
        composeRule.onNodeWithText("Username").performImeAction()

        assertTrue("an empty field must not add a rival", added.isEmpty())
    }

    // ---- the date chip's copy ----

    /**
     * A journey that fits in a session or two carries the clock as well as the date: every unlock
     * in it lands on the same day, so a date-only readout would not move for the whole replay.
     */
    @Test
    fun `a short journey's date chip carries the time of day`() {
        val state = readyFixture()
        val hour = 3_600_000L

        assertEquals(
            "Jan 1, 1970 05:00",
            JourneyStripText.whenLabel(state.frame, state.layout, journeyDateMillis = 5 * hour, completed = false),
        )
    }

    @Test
    fun `a long journey's date chip is the date alone`() {
        val day = 86_400_000L
        val unlock = Unlock(
            id = 1, title = "t", description = "d", points = 10, badgeName = "b",
            type = AchievementType.STANDARD, earnedAtMillis = 0L, numAwarded = 1, gameId = 1,
        )
        val main = PlayerJourney(
            name = "tester",
            unlocks = listOf(unlock, unlock.copy(id = 2, earnedAtMillis = 40 * day)),
            games = mapOf(1 to GameRef(1, "G", null, emptyList(), 0, 10, null)),
            medals = emptyList(),
        )
        val frame = JourneyFrame(JourneyMode.SPAN, main, emptyList(), 0L, 40 * day)
        val layout = LayoutEngine.layout(frame, includeNodes = true)

        assertEquals(
            "Jan 6, 1970",
            JourneyStripText.whenLabel(frame, layout, journeyDateMillis = 5 * day, completed = false),
        )
        assertEquals(
            "Jan 1, 1970 to Feb 10, 1970",
            JourneyStripText.whenLabel(frame, layout, journeyDateMillis = 40 * day, completed = true),
        )
    }

    /**
     * The end state and the time-of-day rule combine: a weekend journey that ended
     * "Feb 12, 2026 to Feb 12, 2026" has not said anything at all.
     */
    @Test
    fun `a short journey's end state carries the clock at both ends`() {
        val state = readyFixture()

        assertEquals(
            "Jan 1, 1970 00:00 to Jan 1, 1970 00:00",
            JourneyStripText.whenLabel(state.frame, state.layout, journeyDateMillis = 3_000L, completed = true),
        )
    }

    @Test
    fun `before the first unlock the date chip says nothing`() {
        val state = readyFixture()

        assertEquals("-", JourneyStripText.whenLabel(state.frame, state.layout, journeyDateMillis = null, completed = false))
    }

    /**
     * The chart panel has to be an opaque surface. This app's theme defines `surfaceVariant` as
     * the foreground colour at 12% alpha, and painting that straight onto a canvas is what made the
     * tooltip's ink-coloured text vanish into an ink-coloured panel.
     */
    @Test
    fun `the chart panel colour is opaque and unlike the ink it carries`() {
        val ink = Color(0xFFEFEFEF)
        val background = Color(0xFF101014)
        val panel = chartPanelColor(ink.copy(alpha = 0.12f), background)

        assertEquals(1f, panel.alpha, 0.0001f)
        val distance = abs(panel.red - ink.red) + abs(panel.green - ink.green) + abs(panel.blue - ink.blue)
        assertTrue("panel $panel is too close to the ink it carries", distance > 1.5f)
        assertTrue("panel $panel must read as a lift off the background", panel.red > background.red)
    }
}
