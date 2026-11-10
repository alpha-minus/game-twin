package com.twinlore.app.ui.fold.guide

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Switching surfaces must not move the surface being left.
 *
 * The top bar floats over the picture and sits above everything else, so the
 * content box was padded by its height on every surface but the image.
 * That padding was on the one box all four surfaces share, so a switch from
 * the image re-laid the picture out in the same frame, while the dissolve
 * still had it fully opaque for the next 220ms. Recorded on the Thor: the map
 * jumps up and shrinks, then the achievements fade in over it, then it goes.
 * Read as "it appears and then changes size", which is the one thing a
 * dissolve exists to avoid.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class FoldSurfaceSwitchTest {

    @get:Rule
    val compose = createComposeRule()

    private var state by mutableStateOf(
        FoldGuideUiState(
            surface = FoldPanelSurface.IMAGE,
            introSeen = true,
            // Linked and empty, so the achievements surface shows its own
            // message and can be measured like the image's.
            awards = FoldAwards(linked = true),
        ),
    )

    private fun render() {
        compose.setContent {
            // The same wrapper the factory puts around the real panel.
            com.twinlore.app.ui.ThemedContent(
                paletteSelection = null,
                appFont = com.twinlore.app.data.AppFont.DEFAULT,
            ) {
                FoldGuidePanelScreen(state = state, actions = FoldGuideActions())
            }
        }
    }

    @Test
    fun `leaving the image surface does not move the picture`() {
        render()
        compose.waitForIdle()
        val before = compose.onNodeWithText(FOLD_IMAGE_NONE).getUnclippedBoundsInRoot()

        state = state.copy(surface = FoldPanelSurface.AWARDS)
        compose.waitForIdle()

        val after = compose.onNodeWithText(FOLD_IMAGE_NONE).getUnclippedBoundsInRoot()
        assertEquals("the surface being left must stay where it was", before, after)
    }

    @Test
    fun `the top bar still keeps clear of the achievements`() {
        // The other half of the same rule: the padding moved, it did not go.
        // The image runs under the top bar; everything else sits below it.
        state = state.copy(surface = FoldPanelSurface.AWARDS)
        render()
        compose.waitForIdle()
        val image = compose.onNodeWithText(FOLD_IMAGE_NONE).getUnclippedBoundsInRoot()
        val awards = compose.onNodeWithText(FOLD_AWARDS_NONE).getUnclippedBoundsInRoot()
        // Both are centred in their own surface, so a padded surface centres
        // its message somewhere the unpadded one does not.
        assertTrue(
            "awards centre ${awards.top} should sit below the image centre ${image.top}",
            awards.top > image.top,
        )
    }

    @Test
    fun `the bottom bar floats over the surface rather than shortening it`() {
        // The bar is a centred pill a few entries wide. Padding the whole
        // surface clear of it took a full-width strip off the bottom of the
        // panel for a bar that only ever covers the middle of it, and the
        // app's own vault session floats its bar over edge-to-edge content.
        // So the surface reaches the bottom of the panel and the bar sits on
        // top of it, the way it already does on the image.
        state = state.copy(surface = FoldPanelSurface.AWARDS)
        render()
        compose.waitForIdle()
        val root = compose.onRoot().getUnclippedBoundsInRoot()
        val surface = compose.onNodeWithTag(FOLD_SURFACE_AWARDS_TAG).getUnclippedBoundsInRoot()
        val bar = compose.onNodeWithText(FOLD_GUIDE_LIBRARY_LABEL).getUnclippedBoundsInRoot()
        assertEquals("the surface must reach the bottom of the panel", root.bottom, surface.bottom)
        assertTrue(
            "the bar ${bar.top}..${bar.bottom} must sit over the surface ending at ${surface.bottom}",
            bar.bottom <= surface.bottom && bar.top >= surface.top,
        )
    }
}
