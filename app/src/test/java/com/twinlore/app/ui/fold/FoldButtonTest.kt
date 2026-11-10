package com.twinlore.app.ui.fold

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyPress
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.semantics.SemanticsActions
import com.twinlore.app.data.StatusBarMode
import com.twinlore.app.domain.VaultTab
import com.twinlore.app.ui.navigation.LONG_PRESS_THRESHOLD_MS
import com.twinlore.app.ui.screens.vaultSessionTopBarChromeVisible
import com.twinlore.app.ui.screens.vaultSessionTopBarRenders
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The fold button is landscape-only and unconditional: there is no setting
 * that gates it, so it appears for everyone whenever the host screen is in
 * landscape. Portrait has no room for it, so it renders nothing there.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class FoldButtonTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `shows when visible`() {
        compose.setContent { FoldButtonContent(visible = true, onClick = {}) }
        compose.onNodeWithTag("fold_button", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `renders nothing when not visible`() {
        compose.setContent { FoldButtonContent(visible = false, onClick = {}) }
        compose.onNodeWithTag("fold_button", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `a tap reports once`() {
        var clicks = 0
        compose.setContent { FoldButtonContent(visible = true, onClick = { clicks++ }) }
        compose.onNodeWithTag("fold_button", useUnmergedTree = true).performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun `visibility needs only landscape`() {
        assertEquals(true, shouldShowFoldButton(isLandscape = true))
        assertEquals(false, shouldShowFoldButton(isLandscape = false))
    }

    @Test
    fun `the permission dialog offers a way forward and a way out`() {
        var continued = 0
        var dismissed = 0
        compose.setContent {
            FoldPermissionDialog(onContinue = { continued++ }, onDismiss = { dismissed++ })
        }
        compose.onNodeWithTag("fold_permission_continue", useUnmergedTree = true).performClick()
        assertEquals(1, continued)

        compose.onNodeWithTag("fold_permission_dismiss", useUnmergedTree = true).performClick()
        assertEquals(1, dismissed)
    }

    // The onClick dispatch that reads a FoldResult and decides what to do lives
    // inside a Composable lambda, unreachable from a plain unit test. This pure
    // mirror is what makes that mapping testable: every FoldResult must land on
    // exactly the right FoldAction, with FOLDED/ALREADY_FOLDED doing nothing and
    // PERMISSION_MISSING/TAB_FAILED never getting swapped.
    @Test
    fun `folded and already-folded need no action`() {
        assertEquals(FoldAction.NONE, foldResultAction(FoldResult.FOLDED))
        assertEquals(FoldAction.NONE, foldResultAction(FoldResult.ALREADY_FOLDED))
    }

    @Test
    fun `permission missing requests the permission`() {
        assertEquals(FoldAction.REQUEST_PERMISSION, foldResultAction(FoldResult.PERMISSION_MISSING))
    }

    @Test
    fun `tab failed reports the failure`() {
        assertEquals(FoldAction.REPORT_FAILURE, foldResultAction(FoldResult.TAB_FAILED))
    }

    // The press decision, pure so it can be tested without a Hilt-backed UI.
    // One notice, before the second entry on a device that has a second
    // screen: the hold that moves the app over there is otherwise only
    // findable by accident. The first press is left alone so it can show
    // what the mode is, with the one-time word already under the button.

    @Test
    fun `the first press on a two screen device enters without a word`() {
        assertEquals(
            FoldPress.ENTER,
            foldPressAction(swapHintSeen = false, canSwapFromHere = true, enteredBefore = false),
        )
    }

    @Test
    fun `the second press on a two screen device teaches the hold`() {
        assertEquals(
            FoldPress.SHOW_SWAP_HINT,
            foldPressAction(swapHintSeen = false, canSwapFromHere = true, enteredBefore = true),
        )
    }

    // Telling someone to hold for a screen they do not have is worse than
    // saying nothing, so the hint is gated on there being somewhere to go.
    @Test
    fun `a device with one screen is never taught the hold`() {
        assertEquals(
            FoldPress.ENTER,
            foldPressAction(swapHintSeen = false, canSwapFromHere = false, enteredBefore = true),
        )
    }

    @Test
    fun `once the hint has been seen every press enters`() {
        assertEquals(
            FoldPress.ENTER,
            foldPressAction(swapHintSeen = true, canSwapFromHere = true, enteredBefore = true),
        )
    }

    /**
     * The hold's own decision, kept out of the composable for the reason
     * [foldPressAction] is: the `when` it replaces sat inside a function that
     * needs `hiltViewModel()` and could not be tested at all.
     *
     * A hold moves the app to the other screen. With no other screen there is
     * nothing to move it to, and the button says so rather than doing nothing
     * -- the user pressed something and held it, and silence reads as broken.
     */
    @Test
    fun `a hold swaps the screen when there is one to swap to`() {
        assertEquals(
            FoldLongPress.SWAP,
            foldLongPressAction(canSwapFromHere = true),
        )
    }

    @Test
    fun `a hold with no second screen says so`() {
        assertEquals(
            FoldLongPress.SAY_NO_SECOND_SCREEN,
            foldLongPressAction(canSwapFromHere = false),
        )
    }

    @Test
    fun `the swap hint acknowledges and moves on`() {
        var continued = 0
        compose.setContent {
            FoldSwapHintDialog(onContinue = { continued++ }, onDismiss = {})
        }

        compose.onNodeWithTag("fold_swap_hint_continue").performClick()
        assertEquals(1, continued)
    }

    // GuideBrowserTopBar's own fold button (site 5) must stay quiet whenever
    // VaultSessionTopBar (site 4) is genuinely on screen to own the button, but
    // step in the moment that bar is not there for ANY of its several independent
    // reasons -- Hidden mode, the keyboard open without the browser URL field
    // focused, the Achievements wallpaper, or per-tab auto-hide --
    // otherwise a vault session can be left with zero fold buttons (the bug this
    // pins down; round 1 of this fix only modeled the Hidden-mode reason).
    //
    // These tests import vaultSessionTopBarChromeVisible / vaultSessionTopBarRenders
    // straight from com.twinlore.app.ui.screens.VaultSessionChrome.kt -- the same
    // functions VaultSessionScreen itself calls to decide whether
    // VaultSessionTopBar renders -- rather than a hand-copied expression, so a
    // future edit to the real gate can't silently drift out of sync with this test.

    @Test
    fun `standalone always shows its own button, regardless of the vault session top bar`() {
        for (vaultSessionTopBarVisible in listOf(true, false)) {
            assertEquals(
                "standalone should show the button regardless of vaultSessionTopBarVisible=$vaultSessionTopBarVisible",
                true,
                shouldShowFoldButtonInHostTopBar(isEmbeddedInVaultSession = false, vaultSessionTopBarVisible),
            )
        }
    }

    @Test
    fun `embedded defers to VaultSessionTopBar only when it is actually visible`() {
        assertEquals(false, shouldShowFoldButtonInHostTopBar(isEmbeddedInVaultSession = true, vaultSessionTopBarVisible = true))
        assertEquals(true, shouldShowFoldButtonInHostTopBar(isEmbeddedInVaultSession = true, vaultSessionTopBarVisible = false))
    }

    // Pins the exact regression: Guide tab, STANDARD mode, keyboard open (e.g. the
    // guide top bar's own search field focused) -- GuideScreen never sets
    // browserUrlFieldFocused, so the keyboard exception never applies to it, and
    // VaultSessionTopBar disappears for a reason statusBarMode cannot express.
    @Test
    fun `guide tab keyboard open without url field focus loses VaultSessionTopBar and gets its own button back`() {
        val chromeVisible = vaultSessionTopBarChromeVisible(
            isKeyboardVisible = true,
            browserUrlFieldFocused = false,
            activeTab = VaultTab.Guide,
            anyWallpaperView = false,
            activeTabChromeVisible = true,
        )
        val vaultSessionTopBarVisible = vaultSessionTopBarRenders(chromeVisible)
        assertEquals(false, vaultSessionTopBarVisible)
        assertEquals(true, shouldShowFoldButtonInHostTopBar(isEmbeddedInVaultSession = true, vaultSessionTopBarVisible))
    }

    // Same shape on Browser: a web-page form field opening the IME (not the URL
    // field) should also surface the embedded fold button.
    @Test
    fun `browser tab keyboard open without url field focus loses VaultSessionTopBar and gets its own button back`() {
        val chromeVisible = vaultSessionTopBarChromeVisible(
            isKeyboardVisible = true,
            browserUrlFieldFocused = false,
            activeTab = VaultTab.Browser,
            anyWallpaperView = false,
            activeTabChromeVisible = true,
        )
        val vaultSessionTopBarVisible = vaultSessionTopBarRenders(chromeVisible)
        assertEquals(false, vaultSessionTopBarVisible)
        assertEquals(true, shouldShowFoldButtonInHostTopBar(isEmbeddedInVaultSession = true, vaultSessionTopBarVisible))
    }

    // The one keyboard exception: the browser URL field itself keeps the bar (and
    // its fold button) up, so the embedded button must stay quiet.
    @Test
    fun `browser url field focus keeps VaultSessionTopBar up despite the keyboard`() {
        val chromeVisible = vaultSessionTopBarChromeVisible(
            isKeyboardVisible = true,
            browserUrlFieldFocused = true,
            activeTab = VaultTab.Browser,
            anyWallpaperView = false,
            activeTabChromeVisible = true,
        )
        val vaultSessionTopBarVisible = vaultSessionTopBarRenders(chromeVisible)
        assertEquals(true, vaultSessionTopBarVisible)
        assertEquals(false, shouldShowFoldButtonInHostTopBar(isEmbeddedInVaultSession = true, vaultSessionTopBarVisible))
    }

    @Test
    fun `achievements wallpaper suppresses the shared top bar, but only on the Achievements tab`() {
        val onAchievements = vaultSessionTopBarChromeVisible(
            isKeyboardVisible = false,
            browserUrlFieldFocused = false,
            activeTab = VaultTab.Achievements,
            anyWallpaperView = true,
            activeTabChromeVisible = true,
        )
        assertEquals(false, onAchievements)

        // A stale/irrelevant wallpaper flag from a different tab must not leak
        // into this tab's own visibility.
        val onGuide = vaultSessionTopBarChromeVisible(
            isKeyboardVisible = false,
            browserUrlFieldFocused = false,
            activeTab = VaultTab.Guide,
            anyWallpaperView = true,
            activeTabChromeVisible = true,
        )
        assertEquals(true, onGuide)
    }

    /** Whether VaultSessionTopBar (site 4) is reachable at all: it doesn't exist
     * outside a vault session, and even embedded it only renders when
     * [vaultSessionTopBarRenders] says so -- both read from production, not
     * restated. */
    private fun vaultSessionTopBarReachable(isEmbeddedInVaultSession: Boolean, vaultSessionTopBarVisible: Boolean): Boolean =
        isEmbeddedInVaultSession && vaultSessionTopBarVisible

    @Test
    fun `exactly one fold button is reachable across the full combination space`() {
        // keepTopBarVisible is included as its own dimension so the invariant is
        // pinned for both settings values, not just the `= false` default --
        // otherwise a call site that forgot to thread the setting could still
        // pass this sweep. Note this still derives BOTH sides (hostTopBarShowsIt
        // and vaultSessionTopBarShowsIt) from the single `vaultSessionTopBarVisible`
        // value computed here, so it cannot catch a real call site passing a
        // different keepTopBarVisible than VaultSessionScreen's own bar -- see
        // task-7-report.md for that logged deferral.
        for (isEmbedded in listOf(true, false)) {
            for (statusBarMode in StatusBarMode.entries) {
                for (isKeyboardVisible in listOf(true, false)) {
                    for (browserUrlFieldFocused in listOf(true, false)) {
                        for (activeTab in VaultTab.entries) {
                            for (anyWallpaperView in listOf(true, false)) {
                                for (activeTabChromeVisible in listOf(true, false)) {
                                    for (keepTopBarVisible in listOf(true, false)) {
                                        val chromeVisible = vaultSessionTopBarChromeVisible(
                                            isKeyboardVisible, browserUrlFieldFocused, activeTab,
                                            anyWallpaperView, activeTabChromeVisible, keepTopBarVisible,
                                        )
                                        val vaultSessionTopBarVisible = vaultSessionTopBarRenders(chromeVisible)
                                        val hostTopBarShowsIt = shouldShowFoldButtonInHostTopBar(isEmbedded, vaultSessionTopBarVisible)
                                        val vaultSessionTopBarShowsIt = vaultSessionTopBarReachable(isEmbedded, vaultSessionTopBarVisible)
                                        assertTrue(
                                            "expected exactly one fold button for isEmbedded=$isEmbedded " +
                                                "statusBarMode=$statusBarMode isKeyboardVisible=$isKeyboardVisible " +
                                                "browserUrlFieldFocused=$browserUrlFieldFocused activeTab=$activeTab " +
                                                "anyWallpaperView=$anyWallpaperView activeTabChromeVisible=$activeTabChromeVisible " +
                                                "keepTopBarVisible=$keepTopBarVisible, " +
                                                "got hostTopBar=$hostTopBarShowsIt vaultSessionTopBar=$vaultSessionTopBarShowsIt",
                                            hostTopBarShowsIt != vaultSessionTopBarShowsIt,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }


    @Test
    fun `a long press reports a long press`() {
        var clicks = 0
        var longs = 0
        compose.setContent {
            FoldButtonContent(
                visible = true,
                onClick = { clicks++ },
                onLongClick = { longs++ },
            )
        }

        compose.onNodeWithTag("fold_button", useUnmergedTree = true).performTouchInput {
            longClick()
        }

        assertEquals(1, longs)
        assertEquals("one press must not be both actions", 0, clicks)
    }

    @Test
    fun `a tap still reports only a tap when a long press is wired`() {
        var clicks = 0
        var longs = 0
        compose.setContent {
            FoldButtonContent(
                visible = true,
                onClick = { clicks++ },
                onLongClick = { longs++ },
            )
        }

        compose.onNodeWithTag("fold_button", useUnmergedTree = true).performClick()

        assertEquals(1, clicks)
        assertEquals(0, longs)
    }

    @Test
    fun `a held controller press reports exactly one long press and zero clicks`() {
        var clicks = 0
        var longs = 0
        compose.setContent {
            FoldButtonContent(
                visible = true,
                onClick = { clicks++ },
                onLongClick = { longs++ },
            )
        }
        focusFoldButton()

        pressButtonA(down = true)
        compose.waitForIdle()
        // longPressHandler's threshold fires from a LaunchedEffect + delay(), not
        // a withFrameNanos animation, so the clock has to be walked past it AND
        // the composition given a chance to observe that (the waitForIdle
        // between the press and the advance, and again after) -- a single
        // advanceTimeBy with no intervening idle leaves the effect's coroutine
        // never resumed, and the assertion below fails silently at longs=0
        // rather than erroring, which is exactly the kind of "didn't actually
        // test the path" failure task-6-report.md is required to rule out.
        compose.mainClock.advanceTimeBy(LONG_PRESS_THRESHOLD_MS + 100)
        compose.waitForIdle()
        pressButtonA(down = false)
        compose.waitForIdle()

        assertEquals("a held controller press must fire exactly one long press", 1, longs)
        assertEquals("a held controller press must never also fire a click", 0, clicks)
    }

    @Test
    fun `a short controller press reports exactly one click and zero long presses`() {
        var clicks = 0
        var longs = 0
        compose.setContent {
            FoldButtonContent(
                visible = true,
                onClick = { clicks++ },
                onLongClick = { longs++ },
            )
        }
        focusFoldButton()

        pressButtonA(down = true)
        compose.waitForIdle()
        pressButtonA(down = false)
        compose.waitForIdle()

        assertEquals("a short controller press must fire exactly one click", 1, clicks)
        assertEquals("a short controller press must never also fire a long press", 0, longs)
    }

    /**
     * `longPressHandler`'s controller path only answers key events on a focused
     * node (dispatch starts at Compose's `FocusOwner`'s current focus target).
     * There is no `SemanticsNodeInteraction.requestFocus()` in this project's
     * pinned compose-ui-test (1.5.4; that convenience extension arrived
     * later), so focus is requested the way that version exposes: invoking
     * the `RequestFocus` accessibility action directly, the same action a
     * real a11y service uses to focus a view programmatically.
     */
    private fun focusFoldButton() {
        compose.onNodeWithTag("fold_button", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onNodeWithTag("fold_button", useUnmergedTree = true).assertIsFocused()
    }

    private fun pressButtonA(down: Boolean) =
        pressKey(android.view.KeyEvent.KEYCODE_BUTTON_A, down)

    private fun pressKey(keyCode: Int, down: Boolean) {
        compose.onNodeWithTag("fold_button", useUnmergedTree = true).performKeyPress(
            androidx.compose.ui.input.key.KeyEvent(
                android.view.KeyEvent(
                    if (down) android.view.KeyEvent.ACTION_DOWN else android.view.KeyEvent.ACTION_UP,
                    keyCode,
                )
            )
        )
    }

    /**
     * One held press on [keyCode], asserted to be a long press and nothing else.
     *
     * Every confirm key `longPressHandler` claims has to reach it. The risk this
     * pins down is a stacking one rather than a key-set one: `combinedClickable`
     * installs its own key handler that consumes DirectionCenter, Enter,
     * NumPadEnter and Spacebar on both down and up and fires plain `onClick`,
     * and it does not know about ButtonA. Whichever of the two modifiers sits
     * innermost answers first, so three of these four keys can silently take the
     * click path and never arm the hold.
     */
    private fun assertHeldPressIsALongPress(keyCode: Int) {
        var clicks = 0
        var longs = 0
        compose.setContent {
            FoldButtonContent(
                visible = true,
                onClick = { clicks++ },
                onLongClick = { longs++ },
            )
        }
        focusFoldButton()

        pressKey(keyCode, down = true)
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(LONG_PRESS_THRESHOLD_MS + 100)
        compose.waitForIdle()
        pressKey(keyCode, down = false)
        compose.waitForIdle()

        assertEquals("key $keyCode held must fire exactly one long press", 1, longs)
        assertEquals("key $keyCode held must never also fire a click", 0, clicks)
    }

    @Test
    fun `holding BUTTON_A is a long press`() {
        assertHeldPressIsALongPress(android.view.KeyEvent.KEYCODE_BUTTON_A)
    }

    @Test
    fun `holding DPAD_CENTER is a long press`() {
        assertHeldPressIsALongPress(android.view.KeyEvent.KEYCODE_DPAD_CENTER)
    }

    @Test
    fun `holding ENTER is a long press`() {
        assertHeldPressIsALongPress(android.view.KeyEvent.KEYCODE_ENTER)
    }

    @Test
    fun `holding SPACE is a long press`() {
        assertHeldPressIsALongPress(android.view.KeyEvent.KEYCODE_SPACE)
    }
}
