package com.twinlore.app.ui.fold.guide

import android.content.Context
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.setViewTreeOnBackPressedDispatcherOwner
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * A Compose surface that can live in a window with no activity behind it.
 *
 * A [ComposeView] finds its lifecycle, view model store and saved state
 * registry by walking up the view tree. In an activity those are installed by
 * the activity; in a TYPE_APPLICATION_OVERLAY window there is nobody to install
 * them, and a ComposeView without them never composes at all. So this class is
 * all three owners itself, and drives them from the window being added and
 * removed.
 *
 * What it deliberately does not solve is sub-windows. Compose's Dialog,
 * AlertDialog and ModalBottomSheet ask for a TYPE_APPLICATION window, which
 * createWindowContext will not grant an overlay, so they die with "Window type
 * mismatch". Popup and DropdownMenu are fine: a TYPE_APPLICATION_PANEL
 * sub-window accepts an overlay as its parent. Content hosted here declares
 * what it may open; see GuideViewerCapabilities.
 */
class OverlayComposeRoot(
    context: Context,
    /**
     * Called for a back press no composable claimed. The panel closes: with a
     * focusable window the panel swallows back from the game underneath, and a
     * back that does nothing at all would be worse than the tab.
     */
    private val onUnhandledBack: () -> Unit = {},
) : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner, OnBackPressedDispatcherOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore = ViewModelStore()
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateController.savedStateRegistry

    /**
     * The app's viewers register BackHandlers, and a BackHandler with no owner
     * in the tree is an IllegalStateException at composition, not at back
     * press. So the owner is here whether or not anything uses it.
     */
    override val onBackPressedDispatcher = OnBackPressedDispatcher()

    private val composeView = ComposeView(context)

    /**
     * A container around the ComposeView, only so a back press has somewhere
     * to be caught: ComposeView is final, and the window's top-level view is
     * the one the system dispatches key events to.
     */
    private val container = BackAwareContainer(context).apply { addView(composeView) }

    val view: View get() = container

    init {
        // Restore before anything can ask for the registry: the controller
        // refuses reads until performRestore has run, and there is no saved
        // state to hand it, because an overlay window is never recreated by the
        // system the way an activity is.
        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.INITIALIZED
        // On the container, not the ComposeView: the owners are found by
        // walking up the view tree, so installing them on the parent covers
        // the ComposeView and anything else that ever sits beside it.
        container.setViewTreeLifecycleOwner(this)
        container.setViewTreeViewModelStoreOwner(this)
        container.setViewTreeSavedStateRegistryOwner(this)
        container.setViewTreeOnBackPressedDispatcherOwner(this)
    }

    fun setContent(content: @Composable () -> Unit) {
        composeView.setContent(content)
    }

    /** Called once the window manager has accepted [view]. */
    fun onAttached() {
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    /**
     * The window is parked: hidden but kept. CREATED, so collectors tied to the
     * lifecycle stop and the readers get ON_PAUSE, which is where they write
     * their position back for the app.
     */
    fun onParked() {
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    /** A parked window is showing again. */
    fun onUnparked() {
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    /**
     * Called once the window manager has removed [view].
     *
     * Destroyed rather than stopped: an overlay panel is built from scratch
     * every time it opens, so nothing here is meant to survive, and a lifecycle
     * left at STARTED would keep collectors running against a window nobody can
     * see, next to a game that is already the reason this process gets reaped.
     */
    fun onDetached() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        viewModelStore.clear()
    }

    /**
     * An activity turns a back press into an [OnBackPressedDispatcher] call.
     * There is no activity here, so the view does it.
     */
    private inner class BackAwareContainer(context: Context) : FrameLayout(context) {
        override fun dispatchKeyEvent(event: KeyEvent): Boolean {
            if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                if (onBackPressedDispatcher.hasEnabledCallbacks()) {
                    onBackPressedDispatcher.onBackPressed()
                } else {
                    onUnhandledBack()
                }
                return true
            }
            return super.dispatchKeyEvent(event)
        }
    }
}
