package com.twinlore.app.ui.screens

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.twinlore.app.data.AppIcon
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The emblem that replaced the pinned-apps pin in the post-refresh showcase.
 * Stateless half only: the stateful one reads a Hilt ViewModel, and the split
 * exists so these can run without one, exactly as PinTopBarButtonTest does.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ShowcaseEmblemButtonTest {

    @get:Rule
    val compose = createComposeRule()

    // DEFAULT is a monochrome mask tinted to the accent; NOIR is full artwork
    // drawn as-is. Both have to render, because the showcase paints on black
    // and does not apply the library bar's light-theme override.
    @Test
    fun `renders a tinted emblem`() {
        compose.setContent {
            ShowcaseEmblemButtonContent(
                icon = AppIcon.DEFAULT,
                accentColor = Color.Cyan,
                onClick = {},
            )
        }
        compose.onNodeWithTag("showcase_emblem_button", useUnmergedTree = true).assertIsDisplayed()
    }

    /**
     * The library bar's size, not smaller. The showcase is the one screen a
     * detected game gets, with its art and achievements at full size, and a
     * mark smaller than the library's read as an afterthought beside them.
     */
    @Test
    fun `the emblem is the library bar's size`() {
        compose.setContent {
            ShowcaseEmblemButtonContent(
                icon = AppIcon.DEFAULT,
                accentColor = Color.Cyan,
                onClick = {},
            )
        }
        val bounds = compose.onNodeWithTag("showcase_emblem_button", useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        assertTrue(
            "emblem button ${bounds.height} must be at least ${SHOWCASE_EMBLEM_HEIGHT + 8.dp}",
            bounds.height >= SHOWCASE_EMBLEM_HEIGHT + 8.dp,
        )
        assertTrue("mark ${SHOWCASE_EMBLEM_HEIGHT} must be at least 38dp", SHOWCASE_EMBLEM_HEIGHT >= 38.dp)
    }

    @Test
    fun `renders an untinted emblem`() {
        compose.setContent {
            ShowcaseEmblemButtonContent(
                icon = AppIcon.NOIR,
                accentColor = Color.Cyan,
                onClick = {},
            )
        }
        compose.onNodeWithTag("showcase_emblem_button", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `a tap reports once`() {
        var clicks = 0
        compose.setContent {
            ShowcaseEmblemButtonContent(
                icon = AppIcon.DEFAULT,
                accentColor = Color.Cyan,
                onClick = { clicks++ },
            )
        }
        compose.onNodeWithTag("showcase_emblem_button", useUnmergedTree = true).performClick()
        assertEquals(1, clicks)
    }
}
