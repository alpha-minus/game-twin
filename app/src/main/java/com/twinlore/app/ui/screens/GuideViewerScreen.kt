package com.twinlore.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.twinlore.app.ui.MainViewModel
import com.twinlore.app.ui.screens.guide.GuideScreen

/**
 * Guide Viewer screen - displays HTML guide browser with offline support.
 * Thin wrapper kept for NavigationHost compatibility; delegates to GuideScreen.
 */
@Composable
fun GuideViewerScreen(
    vaultId: String,
    mainViewModel: MainViewModel,
    guideViewModel: GuideViewerScreenViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    // Immersive landscape like the library/vault session.
    com.twinlore.app.ui.utils.HideStatusBarInLandscape()
    GuideScreen(
        vaultId = vaultId,
        mainViewModel = mainViewModel,
        guideViewModel = guideViewModel,
        // Standalone route: no VaultSessionTopBar around this screen, so
        // GuideBrowserTopBar's own fold button is the only one available.
        isEmbeddedInVaultSession = false,
        modifier = modifier
    )
}
