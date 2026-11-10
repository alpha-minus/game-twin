package com.twinlore.app.ui.fold.guide

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.twinlore.app.data.StatusBarMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The panel's top bar follows the app's status bar setting, and carries Fold
 * where the app's own bar carries the button that opens the mode.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class FoldGuideTopBarTest {

    @get:Rule
    val compose = createComposeRule()

    // The app's LibraryStatusIndicators returns early for Off; the panel's
    // readouts do the same, or "empty the status bar" would empty it in one
    // window and not the other.
    @Test
    fun `readouts follow the app's status bar mode`() {
        assertTrue(statusReadoutsVisible(StatusBarMode.STANDARD))
        assertTrue(statusReadoutsVisible(StatusBarMode.PERFORMANCE))
        assertFalse(statusReadoutsVisible(StatusBarMode.OFF))
    }

    /**
     * Return is the way out, at the busiest end of the bar. In Performance
     * mode it sits against five meters and a clock, and an 18.dp mark there
     * read as smaller than everything it was competing with. It may be bigger
     * than the panel's other marks; it may not be smaller.
     */
    @Test
    fun `the return mark is never smaller than the bar's own`() {
        assertTrue(
            "return ${FOLD_RETURN_ICON} < bar ${FOLD_BAR_ENTRY_ICON}",
            FOLD_RETURN_ICON >= FOLD_BAR_ENTRY_ICON,
        )
    }

    /**
     * The connection mark shows wherever the readouts do, Performance
     * included: the meters yield width to it, and a lost connection is worth
     * knowing in any mode.
     */
    @Test
    fun `the connection mark shows in standard and performance mode`() {
        assertTrue(connectionReadoutVisible(StatusBarMode.STANDARD))
        assertTrue(connectionReadoutVisible(StatusBarMode.PERFORMANCE))
        StatusBarMode.entries.forEach {
            assertEquals(statusReadoutsVisible(it), connectionReadoutVisible(it))
        }
    }

    /**
     * Performance mode drops the title too.
     *
     * The five meters take most of this bar, and what was left of the title
     * was a few characters and an ellipsis: the name of a file, cut short
     * enough to name nothing. Better to give the width to the readings that
     * mode exists for than to spend it saying "Chrono Trigger/Walkthr...".
     */
    @Test
    fun `the title is dropped in performance mode`() {
        assertTrue(panelTitleVisible(StatusBarMode.STANDARD))
        assertTrue(panelTitleVisible(StatusBarMode.OFF))
        assertFalse(panelTitleVisible(StatusBarMode.PERFORMANCE))
    }

    // The corner holds Return now, not Fold. Fold moved to the handle, which
    // is the only control this bar cannot hide, and Return took the corner the
    // app keeps for the button that opens the mode.
    @Test
    fun `the return button renders and reports a tap once`() {
        var clicks = 0
        compose.setContent {
            ReturnTopBarButton(onClick = { clicks++ }, tint = Color.White, iconSize = 18.dp)
        }
        compose.onNodeWithTag("fold_return_button", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("fold_return_button", useUnmergedTree = true).performClick()
        assertEquals(1, clicks)
    }

    /**
     * Held, Return brings the app back on the other screen. The same promise
     * the app's own fold button keeps when it is held, on the button that
     * wears the same glyph, so one mark means one thing in both modes.
     */
    @Test
    fun `the return button reports a hold once, and not as a tap`() {
        var clicks = 0
        var holds = 0
        compose.setContent {
            ReturnTopBarButton(
                onClick = { clicks++ },
                onLongClick = { holds++ },
                tint = Color.White,
                iconSize = 18.dp,
            )
        }

        compose.onNodeWithTag("fold_return_button", useUnmergedTree = true)
            .performTouchInput { longClick() }

        assertEquals(1, holds)
        assertEquals("one press must not be both actions", 0, clicks)
    }

    @Test
    fun `the return button still reports a tap when a hold is wired`() {
        var clicks = 0
        var holds = 0
        compose.setContent {
            ReturnTopBarButton(
                onClick = { clicks++ },
                onLongClick = { holds++ },
                tint = Color.White,
                iconSize = 18.dp,
            )
        }

        compose.onNodeWithTag("fold_return_button", useUnmergedTree = true).performClick()

        assertEquals(1, clicks)
        assertEquals(0, holds)
    }
}
