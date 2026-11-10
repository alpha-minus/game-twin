package com.twinlore.app.ui.fold.guide

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

/** The offer to follow a game started behind the panel: three buttons, the third only for the unlicensed. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class FoldDetectedGameBannerTest {

    @get:Rule
    val compose = createComposeRule()

    private val game = FoldDetectedGame("v2", "Chrono Trigger", "m1")

    @Test
    fun `switch and stay each report once`() {
        var switched = 0
        var stayed = 0
        compose.setContent {
            FoldDetectedGameBanner(game, supportEmblem = null, onSwitch = { switched++ }, onDismiss = { stayed++ }, onSupport = {})
        }
        compose.onNodeWithTag("fold_detected_switch", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("fold_detected_stay", useUnmergedTree = true).performClick()
        assertEquals(1, switched)
        assertEquals(1, stayed)
    }

    @Test
    fun `support is there only when an emblem is`() {
        var supported = 0
        compose.setContent {
            FoldDetectedGameBanner(game, supportEmblem = AppIcon.DEFAULT, onSwitch = {}, onDismiss = {}, onSupport = { supported++ })
        }
        compose.onNodeWithTag("fold_detected_support", useUnmergedTree = true).assertIsDisplayed().performClick()
        assertEquals(1, supported)
    }

    @Test
    fun `no emblem, no support button`() {
        compose.setContent {
            FoldDetectedGameBanner(game, supportEmblem = null, onSwitch = {}, onDismiss = {}, onSupport = {})
        }
        compose.onNodeWithTag("fold_detected_support", useUnmergedTree = true).assertDoesNotExist()
    }
}
