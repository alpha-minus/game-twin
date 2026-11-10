package com.twinlore.app.ui.license

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.twinlore.app.data.AppIcon
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SupportDevDialogTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `shows one tile per supporter icon and taps report the icon`() {
        var tapped: AppIcon? = null
        compose.setContent {
            SupportDevDialog(onSupport = {}, onDismiss = {}, onIconTap = { tapped = it })
        }
        compose.onNodeWithTag("support_dialog_icon_gold", useUnmergedTree = true).performClick()
        assertEquals(AppIcon.GOLD, tapped)
        compose.onNodeWithTag("support_dialog_icon_noire", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `final line only appears on the final prompt`() {
        compose.setContent {
            SupportDevDialog(onSupport = {}, onDismiss = {}, isFinal = false)
        }
        compose.onNodeWithTag("support_dialog_final_line", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `final prompt carries the farewell line`() {
        compose.setContent {
            SupportDevDialog(onSupport = {}, onDismiss = {}, isFinal = true)
        }
        compose.onNodeWithTag("support_dialog_final_line", useUnmergedTree = true).assertIsDisplayed()
    }
}
