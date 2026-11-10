package com.twinlore.app.ui.screens

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The fullscreen showcase after a detection pass presents what the pass found.
 *
 * Every pass ends by emitting detectionFocusApplied, including the ones that
 * found nothing ("No recently played games found", a missing folder, an error).
 * The showcase used to open on whichever vault already held focus in those
 * cases, which is how a private Steam profile ended in a crash: nothing was
 * detected, the old Steam vault was showcased, and its refresh threw.
 */
class DetectionShowcaseRevealTest {

    @Test
    fun `a pass that focused a vault reveals it`() {
        assertTrue(detectionShowcaseReveals(focusedVaultId = "v1", justFocusedByDetection = true))
    }

    @Test
    fun `a pass that found nothing leaves the stale focus alone`() {
        assertFalse(detectionShowcaseReveals(focusedVaultId = "v-old", justFocusedByDetection = false))
    }

    @Test
    fun `nothing focused reveals nothing`() {
        assertFalse(detectionShowcaseReveals(focusedVaultId = null, justFocusedByDetection = true))
        assertFalse(detectionShowcaseReveals(focusedVaultId = null, justFocusedByDetection = false))
    }
}
