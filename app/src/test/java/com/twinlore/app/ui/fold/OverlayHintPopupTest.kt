package com.twinlore.app.ui.fold

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.twinlore.app.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The one-time word about Overlay Mode.
 *
 * Its own dismissal is the only thing it does, and the thing that must work:
 * a hint that cannot be got rid of is worse than one that never appears, and
 * the dismissal is also what records it as said.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class OverlayHintPopupTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `the hint says what the mode does and dismisses itself`() {
        var dismissed = 0
        compose.setContent {
            MaterialTheme { OverlayHintPopup(onDismiss = { dismissed++ }) }
        }
        val context = RuntimeEnvironment.getApplication()

        compose.onNodeWithTag("fold_overlay_hint").assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.fold_overlay_hint_body)).assertIsDisplayed()

        compose.onNodeWithTag("fold_overlay_hint_dismiss").performClick()

        assertEquals(1, dismissed)
    }
}
