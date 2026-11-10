package com.twinlore.app.ui.fold.guide

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The first-open card: in the panel's tree, dismissed by its own button and nothing else. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class FoldPanelIntroTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `got it dismisses, and the scrim does not`() {
        var dismissed = 0
        compose.setContent { FoldPanelIntro(onDismiss = { dismissed++ }) }
        compose.onNodeWithTag("fold_panel_intro").assertIsDisplayed()

        compose.onNodeWithTag("fold_panel_intro").performClick()
        assertEquals(0, dismissed)

        compose.onNodeWithTag("fold_panel_intro_dismiss", useUnmergedTree = true).performClick()
        assertEquals(1, dismissed)
    }
}
