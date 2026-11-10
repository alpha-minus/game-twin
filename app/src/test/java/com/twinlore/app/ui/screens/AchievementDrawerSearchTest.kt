package com.twinlore.app.ui.screens

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyPress
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performSemanticsAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import android.view.KeyEvent as NativeKeyEvent

/**
 * The achievements drawers' always-visible search. What matters is the split between resting on
 * it and typing in it: no text field (so no keyboard) until it is activated, and a clear that
 * empties the query.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AchievementDrawerSearchTest {

    @get:Rule
    val compose = createComposeRule()

    private var query by mutableStateOf("")

    private fun setContent(initial: String = "") {
        query = initial
        compose.setContent {
            AchievementDrawerSearch(
                query = query,
                onQueryChanged = { query = it },
                accentColor = Color.Cyan,
                textColor = Color.White,
            )
        }
    }

    private fun key(code: Int, action: Int) = KeyEvent(
        NativeKeyEvent(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), action, code, 0)
    )

    @Test
    fun `opening shows the field without a text input, so no keyboard`() {
        setContent()
        compose.onNodeWithTag(DRAWER_SEARCH_FIELD_TAG).assertExists()
        compose.onNodeWithTag(DRAWER_SEARCH_EDIT_TAG).assertDoesNotExist()
        compose.onNodeWithText(DRAWER_SEARCH_PLACEHOLDER).assertExists()
    }

    @Test
    fun `a tap swaps in a focused text field that types into the query`() {
        setContent()
        compose.onNodeWithTag(DRAWER_SEARCH_FIELD_TAG).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(DRAWER_SEARCH_EDIT_TAG).assertIsFocused()
        compose.onNodeWithTag(DRAWER_SEARCH_EDIT_TAG).performTextInput("boss")
        assertEquals("boss", query)
    }

    @Test
    fun `controller focus rests on the field without opening it, confirm opens it`() {
        setContent()
        compose.onNodeWithTag(DRAWER_SEARCH_FIELD_TAG).performSemanticsAction(SemanticsActions.RequestFocus)
        compose.waitForIdle()
        compose.onNodeWithTag(DRAWER_SEARCH_EDIT_TAG).assertDoesNotExist()

        compose.onNodeWithTag(DRAWER_SEARCH_FIELD_TAG)
            .performKeyPress(key(NativeKeyEvent.KEYCODE_BUTTON_A, NativeKeyEvent.ACTION_DOWN))
        compose.waitForIdle()
        compose.onNodeWithTag(DRAWER_SEARCH_EDIT_TAG).assertIsFocused()

        // The Thor's Confirm sends DPAD_CENTER right behind the A; it must not close the field.
        compose.onNodeWithTag(DRAWER_SEARCH_EDIT_TAG)
            .performKeyPress(key(NativeKeyEvent.KEYCODE_DPAD_CENTER, NativeKeyEvent.ACTION_DOWN))
        compose.waitForIdle()
        compose.onNodeWithTag(DRAWER_SEARCH_EDIT_TAG).assertExists()

        // B closes it and hands focus back to the resting field.
        compose.onNodeWithTag(DRAWER_SEARCH_EDIT_TAG)
            .performKeyPress(key(NativeKeyEvent.KEYCODE_BUTTON_B, NativeKeyEvent.ACTION_DOWN))
        compose.waitForIdle()
        compose.onNodeWithTag(DRAWER_SEARCH_EDIT_TAG).assertDoesNotExist()
        compose.onNodeWithTag(DRAWER_SEARCH_FIELD_TAG).assertIsFocused()
    }

    @Test
    fun `the clear action shows only for a query and empties it`() {
        setContent()
        compose.onNodeWithTag(DRAWER_SEARCH_CLEAR_TAG).assertDoesNotExist()

        query = "boss"
        compose.waitForIdle()
        compose.onNodeWithTag(DRAWER_SEARCH_CLEAR_TAG).assertExists()
        compose.onNodeWithText("boss").assertExists()

        compose.onNodeWithTag(DRAWER_SEARCH_CLEAR_TAG).performClick()
        compose.waitForIdle()
        assertEquals("", query)
        compose.onNodeWithTag(DRAWER_SEARCH_CLEAR_TAG).assertDoesNotExist()
    }

    @Test
    fun `no-match text quotes the trimmed query, as the overlay does`() {
        assertEquals("No achievements match \"boss\"", drawerSearchNoMatchText("  boss "))
        assertFalse(drawerSearchNoMatchText("x").contains('—'))
        assertFalse(DRAWER_SEARCH_PLACEHOLDER.contains('—'))
    }
}
