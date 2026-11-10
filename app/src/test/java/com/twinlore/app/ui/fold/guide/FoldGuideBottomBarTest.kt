package com.twinlore.app.ui.fold.guide

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The bar decides what is on screen next: the content surfaces, Settings now
 * among them, Swap, and Library. Return is still not here; it is in the top
 * bar's corner, and Fold belongs to the handle, which is the only control the
 * bar cannot hide when it collapses to give the page room.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class FoldGuideBottomBarTest {

    @get:Rule
    val compose = createComposeRule()

    // Settings is a surface rather than a layer over the page: as a layer it
    // was drawn behind the guide, because the reader is a WebView and Compose
    // drawn after one does not reliably land on top of it.
    @Test
    fun `the surfaces are the content kinds, with settings among them`() {
        assertEquals(
            listOf(
                FoldPanelSurface.GUIDE,
                FoldPanelSurface.IMAGE,
                FoldPanelSurface.AWARDS,
                FoldPanelSurface.SETTINGS,
            ),
            FoldPanelSurface.entries,
        )
    }

    // Settings sits last on purpose: the three you read from come first, and
    // the one you go to in order to change them follows.
    @Test
    fun `settings is the last surface`() {
        assertEquals(FoldPanelSurface.SETTINGS, FoldPanelSurface.entries.last())
    }

    // Named after where they take you.
    @Test
    fun `the actions say where they go`() {
        assertEquals("Settings", FOLD_GUIDE_SETTINGS_LABEL)
        assertEquals("Swap", FOLD_GUIDE_SWAP_LABEL)
        assertEquals("Return", FOLD_GUIDE_RETURN_LABEL)
        assertEquals("Library", FOLD_GUIDE_LIBRARY_LABEL)
    }

    /**
     * The same mark the app's own vault bottom bar puts on Library
     * (`VaultSessionScreen.kt:1043`). Both bars sit under the same game and do
     * the same job, and a back arrow says "out of here" where a grid says
     * "another view of this".
     */
    @Test
    fun `the library entry wears the app's own back arrow`() {
        assertEquals(Icons.Default.ArrowBack, foldLibraryIcon())
    }

    /**
     * Library is a way out, not a surface, so it reports its tap and never
     * draws itself as the selected entry.
     */
    @Test
    fun `the library entry reports its tap`() {
        var toLibrary = 0
        compose.setContent { Bar(onReturnToLibrary = { toLibrary++ }) }

        compose.onNodeWithText(FOLD_GUIDE_LIBRARY_LABEL).performClick()

        assertEquals(1, toLibrary)
    }

    /**
     * First, ahead of the surfaces.
     *
     * The bar reads left to right as "leave, then what you can look at while
     * you stay", and the entry that leaves the game entirely is the one that
     * earns the first slot.
     */
    @Test
    fun `the library is the bar's first entry`() {
        compose.setContent { Bar() }

        val library = compose.onNodeWithText(FOLD_GUIDE_LIBRARY_LABEL).getUnclippedBoundsInRoot()
        val guide = compose.onNodeWithText(FoldPanelSurface.GUIDE.label).getUnclippedBoundsInRoot()

        assertTrue(
            "library (${library.left}) must sit left of guide (${guide.left})",
            library.left < guide.left,
        )
    }

    /**
     * On the bottom edge, not floating above it.
     *
     * A pill hovering a gesture bar's height above the bottom, with the page
     * showing through the gap below it and around it, read as one more thing
     * on the page. The bar rests on the edge instead: the panel's own bottom
     * bar is a bar, and a bar has a floor.
     */
    @Test
    fun `the bar rests on the bottom edge`() {
        compose.setContent { Bar() }

        val root = compose.onRoot().getUnclippedBoundsInRoot()
        val bar = compose.onNodeWithTag(FOLD_BOTTOM_BAR_TAG).getUnclippedBoundsInRoot()

        assertEquals("bar bottom must be the edge", root.bottom, bar.bottom)
    }

    /**
     * The app's own size. The entries went larger for a while, to give a
     * thumb over a game more to press, and the bar they made was a third
     * taller than the vault session's. This holds them to the app's
     * proportions: at least the platform's touch target, and no taller than
     * the app's bar.
     */
    @Test
    // The Thor's width, where the entries have the room to be their full
    // size; Robolectric's default screen is narrow enough to shrink them.
    @Config(sdk = [33], qualifiers = "w520dp-h292dp-land")
    fun `an entry is the app's size`() {
        compose.setContent { Bar() }

        val entry = compose.onNodeWithText(FOLD_GUIDE_LIBRARY_LABEL).getUnclippedBoundsInRoot()

        // Robolectric's legacy font metrics wrap a label a glyph to a line,
        // so the label's height is nonsense here; the rest of the entry, the
        // disc and the padding, is what this holds to the app's 32dp + 4dp.
        val label = compose.onNodeWithText(FOLD_GUIDE_LIBRARY_LABEL, useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        val aboveLabel = entry.height - label.height
        assertTrue("entry width ${entry.width} must be at least 48dp", entry.width >= 48.dp)
        assertTrue("disc and padding $aboveLabel must be at least 36dp", aboveLabel >= 36.dp)
        assertTrue("disc and padding $aboveLabel must be at most 42dp", aboveLabel <= 42.dp)
    }

    /**
     * Room at the sides. The handle is dragged along the panel's edges, and a
     * pill that reached most of the way across left it nowhere to go along
     * the bottom. With every entry present, the pill still leaves a handle's
     * width free on each side at the Thor's width.
     */
    @Test
    @Config(sdk = [33], qualifiers = "w520dp-h292dp-land")
    fun `the bar leaves the sides free for the handle`() {
        compose.setContent { Bar(swapAvailable = true) }

        val root = compose.onRoot().getUnclippedBoundsInRoot()
        val bar = compose.onNodeWithTag(FOLD_BOTTOM_BAR_TAG).getUnclippedBoundsInRoot()

        assertTrue("left gap ${bar.left} must be at least 48dp", bar.left >= 48.dp)
        assertTrue("right gap ${root.right - bar.right} must be at least 48dp", root.right - bar.right >= 48.dp)
    }

    @Composable
    private fun Bar(onReturnToLibrary: () -> Unit = {}, swapAvailable: Boolean = false) {
        MaterialTheme {
            FoldGuideBottomBar(
                bottomInset = 0.dp,
                surface = FoldPanelSurface.GUIDE,
                collapsed = false,
                swapAvailable = swapAvailable,
                onSelectSurface = {},
                onToggleBar = {},
                onSwap = {},
                onReturnToLibrary = onReturnToLibrary,
            )
        }
    }
}
