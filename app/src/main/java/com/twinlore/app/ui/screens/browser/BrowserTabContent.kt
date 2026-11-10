package com.twinlore.app.ui.screens.browser

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.twinlore.app.domain.Vault
import com.twinlore.app.ui.MainViewModel

/**
 * Orchestrator composable for the Browser tab within a vault session.
 * Delegates to BrowserScreen.
 *
 * `vaultTopBarHeightDp` is the measured height of the outer VaultSessionTopBar
 * (game name). BrowserScreen uses it to offset its own internal URL bar so the
 * two top chrome bars stack vertically instead of rendering on top of each
 * other. Required because the WebView is edge-to-edge: without it, the URL bar
 * and the game-name bar would both anchor to Y=0 (just under the system status
 * bar) and visually overlap.
 */
@Composable
fun BrowserTabContent(
    vaultId: String,
    mainViewModel: MainViewModel,
    sessionVault: Vault?,
    isActive: Boolean,
    onBackFallback: () -> Unit,
    contentFocusRequester: FocusRequester?,
    vaultTopBarHeightDp: Dp = 0.dp,
    modifier: Modifier = Modifier
) {
    BrowserScreen(
        vaultId = vaultId,
        mainViewModel = mainViewModel,
        sessionVault = sessionVault,
        isTabVisible = isActive,
        onBackFallback = onBackFallback,
        contentFocusRequester = contentFocusRequester,
        vaultTopBarHeightDp = vaultTopBarHeightDp,
        modifier = modifier
    )
}
