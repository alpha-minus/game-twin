package com.twinlore.app.ui.fold

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.twinlore.app.di.FoldControllerEntryPoint
import dagger.hilt.android.EntryPointAccessors

/**
 * [FoldController.overlayReturns], as a value the vault session hands its
 * readers.
 *
 * The readers stay composed behind the Always on Top panel with their
 * positions in memory, while the panel hosts its own copies of them over the
 * same store and writes to it. Each of them takes this as `positionSyncKey`
 * and re-reads the store when it moves. It moves once per return, and never
 * before the panel is down, so what is read is what the panel wrote.
 *
 * The entry point rather than a view model, for the reason [OverlayRestore]
 * uses it: the controller is a singleton, and the vault session is the only
 * thing in the app that needs to know it exists.
 */
@Composable
fun overlayReturnCount(): Int {
    val appContext = LocalContext.current.applicationContext
    val controller = remember(appContext) {
        EntryPointAccessors.fromApplication(appContext, FoldControllerEntryPoint::class.java)
            .foldController()
    }
    val returns by controller.overlayReturns.collectAsState()
    return returns
}
