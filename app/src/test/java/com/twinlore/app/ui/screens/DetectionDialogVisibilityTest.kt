package com.twinlore.app.ui.screens

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The detection dialog waits for the splash to finish.
 *
 * "Detecting game..." is a Dialog, which is its own window, so no amount of
 * ordering inside the Library's Compose tree puts the splash over it. With
 * Show Last Played on Launch on - which is now the default - detection starts
 * during the preload window, and the dialog landed on top of the logo on every
 * cold start. The rule is a value rather than an expression buried in the
 * screen so it can be stated once and asserted here.
 */
class DetectionDialogVisibilityTest {

    @Test
    fun `it is hidden while the splash is up`() {
        assertFalse(detectionDialogVisible(isScraping = true, splashAlpha = 1f))
    }

    @Test
    fun `it stays hidden through the splash's fade`() {
        assertFalse(detectionDialogVisible(isScraping = true, splashAlpha = 0.5f))
        assertFalse(detectionDialogVisible(isScraping = true, splashAlpha = 0.01f))
    }

    @Test
    fun `it shows once the splash is gone`() {
        assertTrue(detectionDialogVisible(isScraping = true, splashAlpha = 0f))
    }

    @Test
    fun `nothing to show when nothing is being detected`() {
        assertFalse(detectionDialogVisible(isScraping = false, splashAlpha = 0f))
        assertFalse(detectionDialogVisible(isScraping = false, splashAlpha = 1f))
    }
}
