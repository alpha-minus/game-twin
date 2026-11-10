package com.twinlore.app.ui.fold.guide

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Text size is a slider now, not five chips.
 *
 * Five preset percentages is a coarse instrument for the one setting people
 * actually fiddle with, and it was the only row whose values did not fit the
 * panel's width. The slider spans the same range as the app's own guide zoom
 * so a guide reads the same size in both places.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class FoldTextZoomSliderTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `the range is the app's own guide zoom range`() {
        assertEquals(0.5f, FOLD_TEXT_ZOOM_MIN, 1e-4f)
        assertEquals(3.0f, FOLD_TEXT_ZOOM_MAX, 1e-4f)
    }

    @Test
    fun `values land on whole five percent steps`() {
        assertEquals(1.0f, foldTextZoomStep(0.9903f), 1e-4f)
        assertEquals(1.25f, foldTextZoomStep(1.2361f), 1e-4f)
        assertEquals(0.85f, foldTextZoomStep(0.8712f), 1e-4f)
    }

    @Test
    fun `a value off either end is pulled back into range`() {
        assertEquals(FOLD_TEXT_ZOOM_MIN, foldTextZoomStep(0.1f), 1e-4f)
        assertEquals(FOLD_TEXT_ZOOM_MAX, foldTextZoomStep(9f), 1e-4f)
    }

    @Test
    fun `the row reads out the size it is set to`() {
        renderAt(1.25f)
        compose.onNodeWithText("125%").assertExists()
    }

    @Test
    fun `dragging reports a stepped value`() {
        var reported: Float? = null
        renderAt(1.0f) { reported = it }
        compose.onNodeWithText(FOLD_SETTINGS_TEXT_SIZE).assertExists()
        foldSettingsSlider(compose, FOLD_SETTINGS_SLIDER_TEXT_ZOOM)
            .performSemanticsAction(SemanticsActions.SetProgress) { it(1.4713f) }
        assertEquals(1.45f, reported!!, 1e-4f)
    }

    private fun renderAt(zoom: Float, onPicked: (Float) -> Unit = {}) {
        compose.setContent {
            FoldSettingsContent(
                handleSizeDp = 56,
                handleAlpha = 0.35f,
                handleRecess = 1f,
                textZoom = zoom,
                themeInject = true,
                smartFormatting = true,
                onHandleSizePicked = {},
                onHandleAlphaPicked = {},
                onHandleRecessPicked = {},
                onHandleStyleCommitted = {},
                onHandleSizePreviewing = {},
                onOpenAppSettings = {},
                onTextZoomPicked = onPicked,
                onThemeInjectPicked = {},
                onSmartFormattingPicked = {},
            )
        }
    }
}

/**
 * The settings surface carries four sliders now, so a bare "the node with a
 * SetProgress action" no longer names one. They are ordered top to bottom:
 * text size, then the handle's size, its size while reading, and its
 * visibility.
 */
const val FOLD_SETTINGS_SLIDER_TEXT_ZOOM = 0
const val FOLD_SETTINGS_SLIDER_HANDLE_SIZE = 1
const val FOLD_SETTINGS_SLIDER_HANDLE_READING = 2
const val FOLD_SETTINGS_SLIDER_HANDLE_ALPHA = 3

fun foldSettingsSlider(
    compose: androidx.compose.ui.test.junit4.ComposeContentTestRule,
    index: Int,
) = compose.onAllNodes(
    androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress),
)[index]
