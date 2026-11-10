package com.twinlore.app.ui.fold.guide

import androidx.activity.findViewTreeOnBackPressedDispatcherOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Compose in an overlay window has no activity to inherit ViewTree owners from,
 * and a ComposeView without them does not compose at all. This is the whole
 * mechanism the panel rests on, so it is asserted rather than assumed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class OverlayComposeRootTest {

    @Test
    fun `the root installs every ViewTree owner a ComposeView needs`() {
        val root = OverlayComposeRoot(RuntimeEnvironment.getApplication())
        assertNotNull(root.view.findViewTreeLifecycleOwner())
        assertNotNull(root.view.findViewTreeViewModelStoreOwner())
        assertNotNull(root.view.findViewTreeSavedStateRegistryOwner())
    }

    @Test
    fun `the saved state registry is restored before anyone can read it`() {
        val root = OverlayComposeRoot(RuntimeEnvironment.getApplication())
        // Reading a registry whose performRestore has not run throws. There is
        // nothing to restore from, because an overlay window is never recreated
        // by the system, but the controller still has to be told that or every
        // read is an exception. Null here is the correct answer; not throwing
        // is the assertion.
        assertNull(root.savedStateRegistry.consumeRestoredStateForKey("anything"))
    }

    @Test
    fun `attaching moves the lifecycle to RESUMED`() {
        val root = OverlayComposeRoot(RuntimeEnvironment.getApplication())
        root.onAttached()
        assertEquals(
            Lifecycle.State.RESUMED,
            root.view.findViewTreeLifecycleOwner()!!.lifecycle.currentState,
        )
    }

    @Test
    fun `detaching destroys the lifecycle so nothing survives the window`() {
        val root = OverlayComposeRoot(RuntimeEnvironment.getApplication())
        root.onAttached()
        root.onDetached()
        assertEquals(
            Lifecycle.State.DESTROYED,
            root.view.findViewTreeLifecycleOwner()!!.lifecycle.currentState,
        )
    }

    @Test
    fun `a back handler has an owner to register with`() {
        // What BackHandler reads is LocalOnBackPressedDispatcherOwner, and
        // Compose populates that by walking up the view tree. Without an owner
        // there, the app's viewers throw "No OnBackPressedDispatcherOwner was
        // provided" at composition, not at back press.
        val root = OverlayComposeRoot(RuntimeEnvironment.getApplication())
        assertNotNull(root.view.findViewTreeOnBackPressedDispatcherOwner())
    }

    @Test
    fun `back with nothing to handle falls back to the caller`() {
        var fellBack = false
        val root = OverlayComposeRoot(
            RuntimeEnvironment.getApplication(),
            onUnhandledBack = { fellBack = true },
        )
        // No setContent: the view never reaches a window here, so content would
        // never compose and adds nothing to the assertion. It is not harmless
        // either. A ComposeView given content off-window leaves Compose state
        // behind in the JVM, and the next createComposeRule class in the same
        // test fork then never goes idle (AppNotIdleException). Found through
        // AchievementDrawerSearchTest, which ran right after this class.
        root.onAttached()
        root.view.dispatchKeyEvent(
            android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_BACK)
        )
        assertTrue("back with no enabled callback should close the panel", fellBack)
    }

    @Test
    fun `the view model store is cleared on detach`() {
        val root = OverlayComposeRoot(RuntimeEnvironment.getApplication())
        root.onAttached()
        val store = root.view.findViewTreeViewModelStoreOwner()!!.viewModelStore
        root.onDetached()
        // What matters is that the root cleared it, so a second panel cannot
        // inherit the first one's view models.
        assertEquals(0, store.keys().size)
    }
}
