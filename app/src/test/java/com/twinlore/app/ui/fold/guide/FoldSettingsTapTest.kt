package com.twinlore.app.ui.fold.guide

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.twinlore.app.data.ThemePalette
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Settings moves the bars on a tap, as every other surface does, and its own
 * controls keep their taps: a swatch picks a theme and does nothing else.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class FoldSettingsTapTest {

    @get:Rule
    val compose = createComposeRule()

    private var toggles = 0
    private var picked: ThemePalette? = null
    private var themeInject: Boolean? = null

    private fun render(selected: ThemePalette? = null) {
        compose.setContent {
            FoldSettingsContent(
                handleSizeDp = 44,
                handleAlpha = 0.35f,
                handleRecess = 1f,
                textZoom = 1f,
                themeInject = true,
                smartFormatting = true,
                onHandleSizePicked = {},
                onHandleAlphaPicked = {},
                onHandleRecessPicked = {},
                onHandleStyleCommitted = {},
                onHandleSizePreviewing = {},
                onOpenAppSettings = {},
                onTextZoomPicked = {},
                onThemeInjectPicked = { themeInject = it },
                onSmartFormattingPicked = {},
                themePalette = selected,
                onThemePalettePicked = { picked = it },
                onToggleChrome = { toggles++ },
            )
        }
    }

    @Test
    fun `a tap between the rows moves the bars`() {
        render()
        compose.onNodeWithText(FOLD_SETTINGS_APP_THEME).performClick()
        assertEquals(1, toggles)
    }

    @Test
    fun `a swatch picks its theme and leaves the bars alone`() {
        render(selected = ThemePalette.OBSIDIAN)
        compose.onNodeWithContentDescription(ThemePalette.LOBBY.displayName).performClick()
        assertEquals(ThemePalette.LOBBY, picked)
        assertEquals(0, toggles)
    }

    @Test
    fun `a chip keeps its tap too`() {
        render()
        // The guide theme's Off is the first Off on the surface.
        compose.onAllNodesWithText(FOLD_SETTINGS_OFF)[0].performClick()
        assertEquals(false, themeInject)
        assertEquals(0, toggles)
    }
}
