package com.twinlore.app.ui.fold.guide

import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import androidx.compose.runtime.Composable
import com.twinlore.app.services.OverlayNetworkService
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the panel's overlay window.
 *
 * Separate from `AndroidFoldPlatform`, which already owns the handle and is
 * long enough. The split is also the safety boundary: the two windows are added
 * and removed by different objects, so a panel that is refused, or that blows up
 * while composing, cannot take the handle down with it. The handle is the only
 * way back to the app, and it outranks everything here.
 */
@Singleton
class FoldGuidePanelHost @Inject constructor() {

    private var root: OverlayComposeRoot? = null
    private var windowManager: WindowManager? = null
    private var params: WindowManager.LayoutParams? = null

    /** Set while [OverlayNetworkService] runs: from a window being added to its removal. */
    private var networkContext: Context? = null

    /** Folded away but kept: see [park]. */
    var isParked: Boolean = false
        private set
    private var parkedDisplayId: Int? = null
    private var appContext: Context? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val releaseParked = Runnable { if (isParked) hideNow() }

    /**
     * Releases a parked panel when the system is short of memory. A hidden
     * WebView next to a running game is exactly what is worth giving back.
     * [ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN] is deliberately not one of
     * them: it arrives as the app goes behind the game, which is every fold.
     */
    private val memoryCallbacks = object : android.content.ComponentCallbacks2 {
        override fun onTrimMemory(level: Int) {
            val releases = level == android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW ||
                level == android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL ||
                level >= android.content.ComponentCallbacks2.TRIM_MEMORY_BACKGROUND
            if (releases) onMain { if (isParked) hideNow() }
        }
        override fun onLowMemory() = onMain { if (isParked) hideNow() }
        override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {}
    }

    /** Up and visible; a parked panel is not showing. */
    val isShowing: Boolean get() = root != null && !isParked

    /**
     * Puts the panel up, full screen, on whichever display [windowContext]
     * belongs to.
     *
     * False means the window was refused and nothing was added, which the
     * controller turns into the tap's old behaviour rather than into nothing
     * happening at all.
     */
    fun show(
        windowContext: Context,
        onUnhandledBack: () -> Unit = {},
        content: @Composable () -> Unit,
    ): Boolean {
        // Replace rather than stack. Two panels would mean two windows and one
        // reference, and the one that lost the reference could never be
        // removed again.
        if (root != null) hide()

        val wm = windowContext.getSystemService(WindowManager::class.java) ?: return false

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // Not focusable while reading. The game underneath keeps its
            // buttons, which is the whole point of reading beside it rather
            // than in front of it. Focus is taken only while a search field is
            // open, and given straight back; see [setFocusable].
            //
            // No FLAG_WATCH_OUTSIDE_TOUCH: a touch outside the panel belongs to
            // the game, and closing a guide because somebody tapped the game
            // would be worse than having no outside dismissal at all.
            //
            // And no FLAG_LAYOUT_NO_LIMITS, unlike the handle's window. The
            // handle wants to sit flush against a cutout edge; a full screen
            // reader does not, and with NO_LIMITS the window extends under the
            // gesture bar while an overlay gets no insets dispatched to pad it
            // back. Seen on the Thor: the bar's App and Fold labels drawn
            // through by the home indicator.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            // Translucent so the reader's own image viewer can dim the page
            // behind it. The panel's root draws an opaque background itself.
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            // Default cutout handling, for the same reason: text laid out into
            // a cutout is text nobody can read. The handle's window keeps
            // ALWAYS because a 44dp mark against the edge is the point of it.
        }

        val composeRoot = OverlayComposeRoot(windowContext, onUnhandledBack)
        composeRoot.setContent(content)

        return runCatching { wm.addView(composeRoot.view, layoutParams) }
            .onSuccess {
                composeRoot.onAttached()
                root = composeRoot
                windowManager = wm
                params = layoutParams
                // Started once the window is up: a visible overlay window is what
                // lets the service go foreground from behind the game. Kept while
                // parked, which is bounded and saves a start from the background.
                networkContext = windowContext.applicationContext
                    .also { OverlayNetworkService.start(it) }
            }
            .onFailure { Log.w(TAG, "Could not open the guide panel", it) }
            .isSuccess
    }

    /**
     * Takes the keyboard, or gives it back.
     *
     * A search field cannot receive a character in a window that refuses focus,
     * and a game cannot receive a button press in one that takes it. Neither is
     * negotiable, so the window is one or the other at a time: focusable while a
     * search is open, and not focusable the rest of the time, which is nearly
     * all of it.
     */
    fun setFocusable(focusable: Boolean) {
        wantsKeyboard = focusable
        applyFocus(showKeyboard = focusable)
    }

    /**
     * Focus with no keyboard, for a text selection: the WebView hides its drag
     * handles in a window that has never had focus, and a selection is not a
     * request to type.
     */
    fun setFocusableWithoutKeyboard(focusable: Boolean) {
        wantsFocusOnly = focusable
        applyFocus(showKeyboard = false)
    }

    private var wantsKeyboard = false
    private var wantsFocusOnly = false

    private fun applyFocus(showKeyboard: Boolean) {
        // A parked panel stays out of the game's way whatever a field in it asks.
        if (isParked) return
        val composeRoot = root ?: return
        val lp = params ?: return
        val wm = windowManager ?: return
        val focusable = wantsKeyboard || wantsFocusOnly
        if (!wantsKeyboard) cancelPendingKeyboard()
        val notFocusable = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        val updated = if (focusable) lp.flags and notFocusable.inv() else lp.flags or notFocusable
        if (updated == lp.flags) {
            // Already focusable for a selection; a field now wants to type.
            if (showKeyboard) showKeyboardOnWindowFocus(composeRoot.view)
            return
        }
        lp.flags = updated
        runCatching { wm.updateViewLayout(composeRoot.view, lp) }
            .onFailure { Log.w(TAG, "Could not change the panel's focus", it) }
            .onSuccess { if (focusable && showKeyboard) showKeyboardOnWindowFocus(composeRoot.view) }
    }

    private var pendingKeyboard: Pair<View, ViewTreeObserver.OnWindowFocusChangeListener>? = null

    /**
     * The tap that focused the field already asked for the keyboard, and was
     * refused: the window could not hold focus yet, because it only becomes
     * focusable in answer to that very focus. Nothing asks again, so it took a
     * second tap. Once the window does gain focus, the keyboard is asked for
     * on the field's behalf.
     */
    private fun showKeyboardOnWindowFocus(view: View) {
        cancelPendingKeyboard()
        if (view.hasWindowFocus()) {
            showKeyboard(view)
            return
        }
        val listener = object : ViewTreeObserver.OnWindowFocusChangeListener {
            override fun onWindowFocusChanged(hasFocus: Boolean) {
                if (!hasFocus) return
                cancelPendingKeyboard()
                // Posted: the input method binds to the focused view in this
                // same focus pass, and a request ahead of that is dropped too.
                view.post { showKeyboard(view) }
            }
        }
        view.viewTreeObserver.addOnWindowFocusChangeListener(listener)
        pendingKeyboard = view to listener
    }

    private fun cancelPendingKeyboard() {
        val (view, listener) = pendingKeyboard ?: return
        pendingKeyboard = null
        runCatching { view.viewTreeObserver.removeOnWindowFocusChangeListener(listener) }
    }

    private fun showKeyboard(view: View) {
        // Only for a field that still has focus: one blurred in the meantime
        // has already handed the window back.
        val focused = view.findFocus() ?: return
        val imm = view.context.getSystemService(InputMethodManager::class.java) ?: return
        if (!imm.showSoftInput(focused, 0)) {
            focused.windowInsetsController?.show(android.view.WindowInsets.Type.ime())
        }
    }

    /**
     * Everything here touches a window and a lifecycle, both of which are the
     * main thread's alone.
     *
     * The panel is driven from touch handlers, which are already on it, and
     * from the controller, which is not always: `openAppOnCurrentGuide` runs on
     * the application scope, which is `Dispatchers.IO`, and pressing App killed
     * the process with "Method setCurrentState must be called on the main
     * thread". That call site is fixed, and this is here so the next one cannot
     * repeat it.
     */
    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block()
        else Handler(Looper.getMainLooper()).post(block)
    }

    fun hide() = onMain {
        hideNow()
    }

    /**
     * Folds the panel away without removing its window: the view goes GONE and
     * the window stops taking touches, so the game underneath is untouched,
     * while the composition, and with it the reader's WebView and page, stays.
     * Released on its own after [PARK_TIMEOUT_MS] or under memory pressure.
     * False when there is nothing showing to park.
     */
    fun park(): Boolean {
        val composeRoot = root ?: return false
        val lp = params ?: return false
        val wm = windowManager ?: return false
        if (isParked) return true
        lp.flags = lp.flags or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        return runCatching {
            composeRoot.view.visibility = android.view.View.GONE
            wm.updateViewLayout(composeRoot.view, lp)
        }.onFailure {
            Log.w(TAG, "Could not park the guide panel", it)
        }.onSuccess {
            composeRoot.onParked()
            isParked = true
            parkedDisplayId = composeRoot.view.display?.displayId
            appContext = composeRoot.view.context.applicationContext
                ?.also { it.registerComponentCallbacks(memoryCallbacks) }
            mainHandler.postDelayed(releaseParked, PARK_TIMEOUT_MS)
        }.isSuccess
    }

    /**
     * Shows a parked panel again if it is parked on [windowContext]'s display.
     * A panel parked elsewhere is released, and false tells the caller to build
     * a new one.
     */
    fun unpark(windowContext: Context): Boolean {
        if (!isParked) return false
        val composeRoot = root ?: return false
        val lp = params ?: return false
        val wm = windowManager ?: return false
        val here = runCatching { windowContext.display?.displayId }.getOrNull()
        if (here == null || here != parkedDisplayId) {
            hideNow()
            return false
        }
        // Back to the reading flags: touchable, and not focusable, which is how
        // every panel starts (focus is only taken for a search, see setFocusable).
        lp.flags = (lp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()) or
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        return runCatching {
            composeRoot.view.visibility = android.view.View.VISIBLE
            wm.updateViewLayout(composeRoot.view, lp)
        }.onFailure {
            Log.w(TAG, "Could not unpark the guide panel", it)
            hideNow()
        }.onSuccess {
            clearParking()
            composeRoot.onUnparked()
        }.isSuccess
    }

    private fun clearParking() {
        mainHandler.removeCallbacks(releaseParked)
        appContext?.let { runCatching { it.unregisterComponentCallbacks(memoryCallbacks) } }
        appContext = null
        isParked = false
        parkedDisplayId = null
    }

    private fun hideNow() {
        clearParking()
        cancelPendingKeyboard()
        val composeRoot = root ?: return
        val wm = windowManager
        // Cleared before the removal, and whether or not it throws. Before,
        // because removeView disposes the composition synchronously and the
        // content's disposers read [isShowing] on their way out: the handle-size
        // slider's hands the handle back its reading size, which is small while
        // the panel shows. Read mid-teardown it said true, and a parked panel
        // released by its timeout or by memory pressure left the handle shrunk
        // over the game with no panel up. And regardless of the outcome, because
        // a reference kept to a window the manager has already lost would
        // refuse every later show.
        root = null
        windowManager = null
        params = null
        runCatching { wm?.removeView(composeRoot.view) }
            .onFailure { Log.w(TAG, "Could not remove the guide panel", it) }
        composeRoot.onDetached()
        networkContext?.let { OverlayNetworkService.stop(it) }
        networkContext = null
    }

    private companion object {
        const val TAG = "FoldGuidePanel"

        /** How long a folded panel is kept warm before it is released. */
        const val PARK_TIMEOUT_MS = 10L * 60 * 1000
    }
}
