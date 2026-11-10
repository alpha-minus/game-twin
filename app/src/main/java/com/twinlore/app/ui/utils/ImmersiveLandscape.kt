package com.twinlore.app.ui.utils

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Refcounted status-bar arbiter. Screens transition with a dissolve, so the
 * OUTGOING screen's dispose runs AFTER the incoming screen's effect — a naive
 * hide-on-enter/show-on-dispose per screen would re-show the bar right after
 * the next screen hid it. The bar only re-appears when NO composed screen
 * wants it hidden.
 */
private object ImmersiveStatusBarArbiter {
    private var holders = 0

    fun acquire(controller: WindowInsetsControllerCompat) {
        holders++
        controller.hide(WindowInsetsCompat.Type.statusBars())
    }

    fun release(controller: WindowInsetsControllerCompat) {
        holders = (holders - 1).coerceAtLeast(0)
        if (holders == 0) {
            controller.show(WindowInsetsCompat.Type.statusBars())
        }
    }
}

/**
 * Hide the system status bar while this composable is in composition AND the
 * device is in landscape; restore it in portrait / on dispose (subject to the
 * arbiter — see above). Used by the library and the vault session so landscape
 * play is immersive on handhelds.
 */
@Composable
fun HideStatusBarInLandscape() {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
    val context = LocalContext.current
    DisposableEffect(isLandscape) {
        val window = (context as? Activity)?.window
        val controller =
            window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        val acquired = controller != null && isLandscape
        if (acquired) ImmersiveStatusBarArbiter.acquire(controller!!)
        onDispose {
            if (acquired) ImmersiveStatusBarArbiter.release(controller!!)
        }
    }
}

/**
 * Hide the system status bar whenever [hidden] is true, in any orientation,
 * via the same refcounted arbiter as [HideStatusBarInLandscape] (so the two
 * compose without fighting). The readers and the browser pass `chromeHidden`
 * here so portrait reading goes fully immersive once their own top chrome
 * auto-hides; landscape immersion is already handled by
 * [HideStatusBarInLandscape].
 */
@Composable
fun HideStatusBarWhen(hidden: Boolean) {
    val context = LocalContext.current
    DisposableEffect(hidden) {
        val window = (context as? Activity)?.window
        val controller =
            window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        val acquired = controller != null && hidden
        if (acquired) ImmersiveStatusBarArbiter.acquire(controller!!)
        onDispose {
            if (acquired) ImmersiveStatusBarArbiter.release(controller!!)
        }
    }
}
