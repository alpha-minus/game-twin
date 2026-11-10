package com.twinlore.app.ui.utils

import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The library top bar tells the screen whether it holds focus, and the grid's
 * auto-focus effect pulls focus back to a tile the moment that reads false.
 *
 * Its buttons are an outer `focusable` wrapping an `IconButton`, which carries
 * its own inner `clickable` focus target. A Confirm press moves focus from the
 * outer node to that inner one. Reporting `isFocused` (the node itself) drops to
 * false on that move even though focus never left the button, and the grid then
 * steals it: Confirm on Settings opened a vault. `hasFocus` (the subtree) is the
 * value that means "the bar still has it".
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ReportSubtreeFocusTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun moving_focus_into_a_nested_clickable_never_reports_false() {
        val reports = mutableListOf<Boolean>()
        val outer = FocusRequester(); val inner = FocusRequester(); val elsewhere = FocusRequester()
        rule.setContent {
            Row {
                // Same shape as a top-bar button: outer focusable, inner clickable.
                Box(Modifier.size(48.dp).reportSubtreeFocus { reports += it }
                    .focusRequester(outer).focusable()) {
                    Box(Modifier.size(24.dp).focusRequester(inner).clickable {})
                }
                Box(Modifier.size(48.dp).focusRequester(elsewhere).focusable())
            }
        }
        rule.waitForIdle()
        reports.clear() // the attach-time report; every variant emits it

        rule.runOnIdle { outer.requestFocus() }; rule.waitForIdle()
        assertEquals(listOf(true), reports)

        rule.runOnIdle { inner.requestFocus() }; rule.waitForIdle()
        assertFalse("focus moved within the button; nothing should read as leaving", reports.contains(false))
        assertEquals(true, reports.last())

        rule.runOnIdle { elsewhere.requestFocus() }; rule.waitForIdle()
        assertEquals("leaving the button must still be reported", false, reports.last())
    }
}
