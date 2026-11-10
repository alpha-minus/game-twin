package com.twinlore.app.ui.license

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.twinlore.app.data.AppIcon
import com.twinlore.app.data.PIXEL_UNLOCK_TAP_SCORE
import com.twinlore.app.data.license.LicenseTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ThemePreviewDialogTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `locked supporter icon offers checkout and key entry, not apply`() {
        var checkout = false
        compose.setContent {
            ThemePreviewDialog(
                icon = AppIcon.GOLD, tier = LicenseTier.NONE, tapHighScore = 0,
                onGetEdition = { checkout = true }, onEnterKey = {}, onApply = {}, onDismiss = {},
            )
        }
        compose.onNodeWithTag("theme_preview_enter_key", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("theme_preview_apply", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("theme_preview_get_edition", useUnmergedTree = true).performClick()
        assertTrue(checkout)
    }

    @Test
    fun `locked pixel icon explains the tap game instead of selling`() {
        compose.setContent {
            ThemePreviewDialog(
                icon = AppIcon.PIXEL, tier = LicenseTier.NONE,
                tapHighScore = PIXEL_UNLOCK_TAP_SCORE - 1,
                onGetEdition = {}, onEnterKey = {}, onApply = {}, onDismiss = {},
            )
        }
        // The preview column scrolls; the hint sits below the splash mock, so
        // bring it into view the way a user would before asserting visibility.
        compose.onNodeWithTag("theme_preview_pixel_hint", useUnmergedTree = true)
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithTag("theme_preview_get_edition", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("theme_preview_apply", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `unlocked icon offers apply`() {
        var applied: AppIcon? = null
        compose.setContent {
            ThemePreviewDialog(
                icon = AppIcon.GOLD, tier = LicenseTier.TIER1, tapHighScore = 0,
                onGetEdition = {}, onEnterKey = {}, onApply = { applied = it }, onDismiss = {},
            )
        }
        compose.onNodeWithTag("theme_preview_get_edition", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("theme_preview_apply", useUnmergedTree = true).performClick()
        assertEquals(AppIcon.GOLD, applied)
    }

    @Test
    fun `unlocked icon without an apply route offers no apply button`() {
        compose.setContent {
            ThemePreviewDialog(
                icon = AppIcon.GOLD, tier = LicenseTier.TIER1, tapHighScore = 0,
                onGetEdition = {}, onEnterKey = {}, onApply = null, onDismiss = {},
            )
        }
        compose.onNodeWithTag("theme_preview_apply", useUnmergedTree = true).assertDoesNotExist()
        compose.onAllNodesWithTag("theme_preview_close", useUnmergedTree = true).assertCountEquals(1)
        // No apply route, so no promise about closing the app either.
        compose.onNodeWithText("close Game Twin", substring = true, useUnmergedTree = true)
            .assertDoesNotExist()
    }

    /**
     * Applying used to raise a second confirmation dialog; now the preview is the only dialog,
     * so it must carry the close warning itself and apply directly on the button.
     */
    @Test
    fun `apply state warns that applying closes the app`() {
        compose.setContent {
            ThemePreviewDialog(
                icon = AppIcon.GOLD, tier = LicenseTier.TIER1, tapHighScore = 0,
                onGetEdition = {}, onEnterKey = {}, onApply = {}, onDismiss = {},
            )
        }
        compose.onNodeWithText("close Game Twin", substring = true, useUnmergedTree = true)
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithText("Apply and close", useUnmergedTree = true).assertIsDisplayed()
    }
}
