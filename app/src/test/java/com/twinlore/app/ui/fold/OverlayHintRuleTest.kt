package com.twinlore.app.ui.fold

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When the app volunteers that Overlay Mode exists.
 *
 * Once, and only with something worth reading over a game: told before the
 * first guide or image lands there is nothing to show over the game, and the
 * hint is about a button that has nothing to do yet.
 *
 * Landscape for the same reason the button itself is landscape-only
 * ([shouldShowFoldButton]): a tooltip pointing at a control that is not on
 * screen points at nothing.
 */
class OverlayHintRuleTest {

    @Test
    fun `the first guide or image in landscape earns the hint`() {
        assertTrue(shouldShowOverlayHint(hasContent = true, seen = false, isLandscape = true))
    }

    @Test
    fun `an empty vault is told nothing`() {
        assertFalse(shouldShowOverlayHint(hasContent = false, seen = false, isLandscape = true))
    }

    @Test
    fun `it is said once`() {
        assertFalse(shouldShowOverlayHint(hasContent = true, seen = true, isLandscape = true))
    }

    // The button is not drawn in portrait, so there is nothing to point at.
    @Test
    fun `portrait keeps quiet`() {
        assertFalse(shouldShowOverlayHint(hasContent = true, seen = false, isLandscape = false))
    }
}
