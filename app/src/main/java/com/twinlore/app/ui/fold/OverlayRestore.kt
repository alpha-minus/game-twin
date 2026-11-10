package com.twinlore.app.ui.fold

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.hilt.navigation.compose.hiltViewModel
import com.twinlore.app.data.fold.FoldHandleLook
import com.twinlore.app.di.FoldControllerEntryPoint
import com.twinlore.app.ui.screens.SettingsViewModel
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first

/**
 * Folds a cold start straight back into overlay, panel up.
 *
 * Only when the cold start asked for it: it found overlay mode on and opened
 * this vault's session for the purpose (see [FoldHandover.armColdStartFold]).
 * Placed in the vault session's tree, because the panel shows the session's
 * vault and the fold needs a drawn view to say which display it is on. That
 * is the display the app was started on, which for a frontend starting it
 * beside a dual-screen game is the one under the game.
 *
 * Two frames first, not a `LaunchedEffect(Unit)` that fires at once. This
 * backgrounds the app during session startup, which is where this codebase
 * has had first-frame crashes and dialogs racing the splash; waiting for the
 * session to have drawn and survived is the cheap way to stay out of that. A
 * fold that does not happen ends overlay mode: the app is in front, and the
 * next start should not try again.
 */
@Composable
fun OverlayRestore(vaultId: String) {
    val view = LocalView.current
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val appContext = LocalContext.current.applicationContext
    val entryPoint = remember {
        EntryPointAccessors.fromApplication(appContext, FoldControllerEntryPoint::class.java)
    }
    LaunchedEffect(vaultId) {
        if (!entryPoint.foldHandover().consumeColdStartFold(vaultId)) return@LaunchedEffect
        val controller = entryPoint.foldController()
        if (controller.isFolded.value) return@LaunchedEffect
        withFrameNanos {}
        withFrameNanos {}
        // Same rule as the button: no guessing at a display. A view that
        // cannot say where it is does not fold.
        val displayId = view.display?.displayId
        val result = displayId?.let {
            controller.enterOverlay(
                displayId = it,
                xFraction = settingsViewModel.foldPositionX.first(),
                yFraction = settingsViewModel.foldPositionY.first(),
                look = FoldHandleLook.of(
                    alpha = settingsViewModel.foldHandleAlpha.first(),
                    sizeDp = settingsViewModel.foldHandleSizeDp.first(),
                    recessScale = settingsViewModel.foldHandleRecess.first(),
                ),
            )
        }
        if (result != FoldResult.FOLDED) entryPoint.overlayMemory().setOverlayMode(false)
    }
}
