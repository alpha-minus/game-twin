package com.twinlore.app.ui.fold

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

/**
 * The pin toggle that replaced the pinned-apps dropdown in the vault session
 * top bar. It owns the "keep the top bar on screen" setting that used to live
 * in Settings, which is why the fold button below it stays reachable.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PinTopBarButtonTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `renders in both states`() {
        compose.setContent { PinTopBarButtonContent(pinned = true, onClick = {}) }
        compose.onNodeWithTag("pin_top_bar_button", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `a tap reports once`() {
        var clicks = 0
        compose.setContent { PinTopBarButtonContent(pinned = false, onClick = { clicks++ }) }
        compose.onNodeWithTag("pin_top_bar_button", useUnmergedTree = true).performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun `the two states describe themselves differently`() {
        assertEquals("Unpin the top bar", pinTopBarContentDescription(pinned = true))
        assertEquals("Pin the top bar", pinTopBarContentDescription(pinned = false))
    }
}
