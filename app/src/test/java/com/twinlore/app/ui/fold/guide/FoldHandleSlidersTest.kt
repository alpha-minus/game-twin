package com.twinlore.app.ui.fold.guide

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import com.twinlore.app.data.fold.FoldHandleStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The handle's own two settings, as sliders rather than three chips each.
 *
 * The behaviour worth pinning here is not the geometry (that is
 * [com.twinlore.app.data.fold.FoldHandleStyleTest]) but the split between
 * applying and persisting: the panel applies on every value the slider
 * reports, because that is what makes the handle change under your thumb, and
 * writes once on release, because a DataStore write per frame is both wasted
 * and slower than the drag.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class FoldHandleSlidersTest {

    @get:Rule
    val compose = createComposeRule()

    private val sizes = mutableListOf<Int>()
    private val alphas = mutableListOf<Float>()
    private val recesses = mutableListOf<Float>()
    private var commits = 0
    private val previews = mutableListOf<Boolean>()
    private var openedAppSettings = 0

    @Test
    fun `all three rows read out what they are set to`() {
        render(sizeDp = 72, alpha = 0.6f, recess = 0.45f)

        compose.onNodeWithText(FOLD_SETTINGS_SIZE).assertExists()
        compose.onNodeWithText("72dp").assertExists()
        compose.onNodeWithText(FOLD_SETTINGS_VISIBILITY).assertExists()
        compose.onNodeWithText("60%").assertExists()
        compose.onNodeWithText(FOLD_SETTINGS_READING_SIZE).assertExists()
        compose.onNodeWithText("45%").assertExists()
    }

    /**
     * The reading size needs no preview of any kind, unlike the size slider
     * above it: the handle is recessed exactly when the panel is open, and the
     * panel is open whenever this row is on screen, so dragging it moves the
     * thing itself.
     */
    @Test
    fun `the reading size slider applies what it lands on and writes it once`() {
        render(sizeDp = 56, alpha = 0.35f, recess = 1f)

        drag(FOLD_SETTINGS_SLIDER_HANDLE_READING, 0.4f)

        assertEquals(FoldHandleStyle.recessFromFraction(0.4f), recesses.last(), 1e-4f)
        assertEquals(1, commits)
        assertEquals("it never previews, because it never needs to", emptyList<Boolean>(), previews)
    }

    @Test
    fun `the size slider applies the size it lands on and writes it once`() {
        render(sizeDp = FoldHandleStyle.MIN_SIZE_DP, alpha = 0.35f)

        drag(FOLD_SETTINGS_SLIDER_HANDLE_SIZE, 0.5f)

        assertTrue("the handle must resize under the thumb", sizes.isNotEmpty())
        assertEquals(FoldHandleStyle.sizeDpFromFraction(0.5f), sizes.last())
        assertEquals("one write per gesture, not one per frame", 1, commits)
    }

    @Test
    fun `the visibility slider applies the opacity it lands on and writes it once`() {
        render(sizeDp = 56, alpha = 0f)

        drag(FOLD_SETTINGS_SLIDER_HANDLE_ALPHA, 0.62f)

        assertEquals(0.6f, alphas.last(), 1e-4f)
        assertEquals(1, commits)
    }

    /**
     * The handle is drawn at 62% while the panel is up so it does not cover
     * the page, which would make every size on this slider look like 62% of
     * itself. It goes to true size for exactly as long as the slider is held,
     * and must come back down afterwards: a handle left at true size over a
     * page is the overlap the recessing exists to avoid.
     */
    @Test
    fun `the size slider previews the true size while it is held and puts it back`() {
        render(sizeDp = 56, alpha = 0.35f)

        drag(FOLD_SETTINGS_SLIDER_HANDLE_SIZE, 0.8f)

        assertEquals(listOf(true, false), previews)
    }

    @Test
    fun `the visibility slider does not disturb the size preview`() {
        render(sizeDp = 56, alpha = 0.35f)

        drag(FOLD_SETTINGS_SLIDER_HANDLE_ALPHA, 0.8f)

        assertEquals("only the size slider previews", emptyList<Boolean>(), previews)
    }

    @Test
    fun `the row out to the app says what it costs and asks for it`() {
        render(sizeDp = 56, alpha = 0.35f)

        compose.onNodeWithText(FOLD_SETTINGS_OPEN_APP_NOTE).assertExists()
        // It is the last row on a scrolling surface, so it can be off the
        // bottom of the test's window and a click would land on nothing.
        compose.onNodeWithText(FOLD_SETTINGS_OPEN_APP).performScrollTo().performClick()

        assertEquals(1, openedAppSettings)
    }

    private fun drag(slider: Int, to: Float) {
        foldSettingsSlider(compose, slider)
            .performSemanticsAction(SemanticsActions.SetProgress) { it(to) }
    }

    private fun render(sizeDp: Int, alpha: Float, recess: Float = 1f) {
        compose.setContent {
            FoldSettingsContent(
                handleSizeDp = sizeDp,
                handleAlpha = alpha,
                handleRecess = recess,
                textZoom = 1f,
                themeInject = true,
                smartFormatting = true,
                onHandleSizePicked = { sizes += it },
                onHandleAlphaPicked = { alphas += it },
                onHandleRecessPicked = { recesses += it },
                onHandleStyleCommitted = { commits++ },
                onHandleSizePreviewing = { previews += it },
                onOpenAppSettings = { openedAppSettings++ },
                onTextZoomPicked = {},
                onThemeInjectPicked = {},
                onSmartFormattingPicked = {},
            )
        }
    }
}
