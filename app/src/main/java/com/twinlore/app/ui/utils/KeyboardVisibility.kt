package com.twinlore.app.ui.utils

import android.graphics.Rect
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowManager
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Whether the software keyboard is up over this composable's window.
 *
 * `WindowInsets.ime` alone is not enough. It only reports when the window is
 * edge-to-edge: on the Thor (Android 13, where targetSdk 35 does not force
 * edge-to-edge) `adjustResize` shrinks the window instead and the inset reads
 * zero, so anything anchored to the bottom rides up on top of the keyboard. And
 * the fold panel's overlay window has no decor to dispatch Compose insets at all.
 *
 * So the window is asked as well, on every layout pass: whether its insets say
 * the IME is shown, and whether its visible frame has lost a keyboard-sized
 * strip at the bottom. Any of the three counts.
 *
 * Derived, so the per-frame ticks of the keyboard's slide do not invalidate the
 * caller; only the open/closed flip does.
 */
@Composable
fun rememberKeyboardVisible(): State<Boolean> {
    val view = LocalView.current
    val density = LocalDensity.current
    val imeInsets = WindowInsets.ime
    val windowSaysVisible = remember(view) { mutableStateOf(windowKeyboardVisible(view)) }
    DisposableEffect(view) {
        val observer = view.viewTreeObserver
        val listener = ViewTreeObserver.OnGlobalLayoutListener {
            windowSaysVisible.value = windowKeyboardVisible(view)
        }
        observer.addOnGlobalLayoutListener(listener)
        onDispose {
            val live = if (observer.isAlive) observer else view.viewTreeObserver
            live.removeOnGlobalLayoutListener(listener)
        }
    }
    return remember(imeInsets, density) {
        derivedStateOf { imeInsets.getBottom(density) > 0 || windowSaysVisible.value }
    }
}

/** A strip this much of the window tall is a keyboard; a navigation bar is not. */
private const val KEYBOARD_MIN_FRACTION = 0.15f

private fun windowKeyboardVisible(view: View): Boolean {
    if (!view.isAttachedToWindow) return false
    val root = view.rootView
    val insetsVisible = ViewCompat.getRootWindowInsets(root)
        ?.isVisible(WindowInsetsCompat.Type.ime()) == true
    if (insetsVisible) return true
    val bounds = runCatching {
        view.context.getSystemService(WindowManager::class.java)?.currentWindowMetrics?.bounds
    }.getOrNull() ?: return false
    if (bounds.height() <= 0) return false
    val visible = Rect()
    root.getWindowVisibleDisplayFrame(visible)
    val covered = bounds.bottom - visible.bottom
    return covered > bounds.height() * KEYBOARD_MIN_FRACTION
}
