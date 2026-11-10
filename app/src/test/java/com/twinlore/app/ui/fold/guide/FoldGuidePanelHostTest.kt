package com.twinlore.app.ui.fold.guide

import androidx.compose.runtime.DisposableEffect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

/**
 * What the panel's own content sees of the panel while it is being torn down.
 *
 * Content reads [FoldGuidePanelHost.isShowing] on its way out: the handle-size
 * slider's dispose hands the handle back its reading size, and the reading
 * size is "small if the panel is showing". Removing the window disposes the
 * composition synchronously, inside removeView, so whatever [isShowing] says
 * at that moment is what the handle is left at. It used to say true for a
 * parked panel being released (the park flag was already cleared and the root
 * not yet), which left the handle shrunk over the game with no panel up.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class FoldGuidePanelHostTest {

    private val host = FoldGuidePanelHost()

    /** Shows a panel whose content records [FoldGuidePanelHost.isShowing] as it is disposed. */
    private fun showRecordingDispose(record: (Boolean) -> Unit) {
        val shown = host.show(RuntimeEnvironment.getApplication()) {
            DisposableEffect(Unit) { onDispose { record(host.isShowing) } }
        }
        assertTrue("the panel's window has to be up for this to mean anything", shown)
        // The first composition runs on the next frame.
        ShadowLooper.idleMainLooper()
        assertTrue(host.isShowing)
    }

    @Test
    fun `a panel being hidden is not showing to its own content`() {
        val seen = mutableListOf<Boolean>()
        showRecordingDispose { seen += it }

        host.hide()

        assertEquals("the content has to actually be disposed", 1, seen.size)
        assertFalse(seen.single())
        assertFalse(host.isShowing)
    }

    /**
     * The case that was seen: folded, the panel parked, and later released
     * (the park timeout, or memory pressure behind a game) without anyone
     * asking the platform to hide it.
     */
    @Test
    fun `a parked panel being released is not showing to its own content`() {
        val seen = mutableListOf<Boolean>()
        showRecordingDispose { seen += it }
        assertTrue(host.park())
        assertFalse(host.isShowing)

        host.hide()

        assertEquals("the content has to actually be disposed", 1, seen.size)
        assertFalse(seen.single())
        assertFalse(host.isShowing)
    }
}
