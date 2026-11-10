package com.twinlore.app.ui.screens.guide

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import com.twinlore.app.domain.Vault
import com.twinlore.app.domain.VaultTab
import com.twinlore.app.ui.LocalThemePalette
import com.twinlore.app.ui.MainViewModel
import com.twinlore.app.ui.screens.group.PageGroupViewerScreen

/**
 * Orchestrator composable for the Guide tab within a vault session.
 *
 * When the vault has an open page-group (see
 * `MainViewModel.openPageGroupForVault`), renders [PageGroupViewerScreen]
 * in the tab slot so the vault session's top bar + bottom bar + chrome-
 * visibility plumbing surround it like any other Guide-tab content.
 * Otherwise delegates to [GuideScreen] for the standard picker / saved
 * bookmark view.
 */
@Composable
fun GuideTabContent(
    vaultId: String,
    mainViewModel: MainViewModel,
    sessionVault: Vault?,
    isActive: Boolean,
    onBackFallback: () -> Unit,
    contentFocusRequester: FocusRequester?,
    bottomBarFocusRequester: FocusRequester?,
    /**
     * Moves each time the app comes back from Always on Top. Every viewer
     * below re-reads its position from the store on each move, because the
     * panel hosted its own copy of that viewer over the same store and wrote
     * where it got to. See [com.twinlore.app.ui.fold.FoldController.overlayReturns].
     */
    positionSyncKey: Int = 0,
    modifier: Modifier = Modifier
) {
    // Auto-resume: on first vault entry within this process, if the vault's
    // persisted active bookmark is a page-group, surface the group viewer
    // before [GuideScreen] would otherwise try to load it as a single-page
    // bookmark (its loadGuideBookmark `when` has no "group" arm). MainViewModel
    // guards against re-firing on rotation/config changes via an internal
    // per-vault flag, so this LaunchedEffect can safely key on vaultId.
    LaunchedEffect(vaultId) {
        mainViewModel.maybeRestoreActiveGroupForVault(vaultId)
        mainViewModel.maybeRestoreActiveSingleGuideForVault(vaultId)
    }

    val openPageGroups by mainViewModel.openPageGroupByVault.collectAsState()
    val openSingleGuides by mainViewModel.openSingleGuideByVault.collectAsState()
    val openSingleGuide = openSingleGuides[vaultId]
    val contentKey = when {
        openPageGroups[vaultId] != null -> "pg_${openPageGroups[vaultId]}"
        openSingleGuide != null -> "sg_${openSingleGuide.id}"
        else -> "screen"
    }
    // Snapshot the single-guide bookmark per key so the OUTGOING viewer keeps
    // rendering while it fades out (the live dispatch map has already dropped
    // its entry by then — rendering from the live map would blank it instantly).
    val lastSingleGuideForKey =
        remember { mutableMapOf<String, com.twinlore.app.domain.SavedTab>() }
    if (openSingleGuide != null) {
        lastSingleGuideForKey["sg_${openSingleGuide.id}"] = openSingleGuide
    }

    // Same top-layer dissolve as NavigationHost: viewer↔picker switches inside
    // the Guide tab don't pass through the top-level navigation. The outgoing
    // content stays FULLY OPAQUE underneath while the incoming one composes and
    // fades in on top — a symmetric crossfade dips combined opacity and flashes
    // the theme-coloured background through.
    val zCounter = remember { floatArrayOf(0f) }
    AnimatedContent(
        targetState = contentKey,
        transitionSpec = {
            zCounter[0] += 1f
            (
                fadeIn(tween(durationMillis = 200, delayMillis = 90)) togetherWith
                    fadeOut(tween(durationMillis = 80, delayMillis = 300))
                ).apply { targetContentZIndex = zCounter[0] }
        },
        modifier = modifier,
        label = "GuideTabContentSwitch"
    ) { key ->
        when {
            key.startsWith("pg_") -> GuideTabGroupViewer(
                vaultId = vaultId,
                groupTabId = key.removePrefix("pg_"),
                mainViewModel = mainViewModel,
                isActive = isActive,
                contentFocusRequester = contentFocusRequester,
                bottomBarFocusRequester = bottomBarFocusRequester,
                positionSyncKey = positionSyncKey,
            )
            key.startsWith("sg_") -> {
                // Render from the snapshot — never the live map (see above).
                lastSingleGuideForKey[key]?.let { bookmark ->
                    GuideTabSingleViewer(
                        vaultId = vaultId,
                        bookmark = bookmark,
                        mainViewModel = mainViewModel,
                        isActive = isActive,
                        contentFocusRequester = contentFocusRequester,
                        bottomBarFocusRequester = bottomBarFocusRequester,
                        positionSyncKey = positionSyncKey,
                    )
                }
            }
            else -> GuideTabScreen(
                vaultId = vaultId,
                mainViewModel = mainViewModel,
                sessionVault = sessionVault,
                isActive = isActive,
                onBackFallback = onBackFallback,
                contentFocusRequester = contentFocusRequester,
                bottomBarFocusRequester = bottomBarFocusRequester,
                positionSyncKey = positionSyncKey,
            )
        }
    }
}

@Composable
private fun GuideTabGroupViewer(
    vaultId: String,
    groupTabId: String,
    mainViewModel: MainViewModel,
    isActive: Boolean,
    contentFocusRequester: FocusRequester?,
    bottomBarFocusRequester: FocusRequester?,
    positionSyncKey: Int,
) {
    // Hide the viewer's inner top bar together with the outer vault-session
    // chrome so both bars react to the same scroll/tap gestures.
    val chromeVisible by mainViewModel.vaultSessionGuideChromeVisible.collectAsState()
    PageGroupViewerScreen(
        vaultId = vaultId,
        groupTabId = groupTabId,
        chromeVisible = chromeVisible,
        isActive = isActive,
        contentFocusRequester = contentFocusRequester,
        bottomBarFocusRequester = bottomBarFocusRequester,
        positionSyncKey = positionSyncKey,
        onChromeToggle = {
            mainViewModel.setVaultSessionGuideChromeVisible(!chromeVisible)
        },
        onChromeHide = {
            if (chromeVisible) mainViewModel.setVaultSessionGuideChromeVisible(false)
        },
        onChromeReveal = {
            if (!chromeVisible) mainViewModel.setVaultSessionGuideChromeVisible(true)
        },
        onBack = { mainViewModel.closePageGroupForVault(vaultId) },
        onYouTubeSearch = { text ->
            val q = text.trim()
            if (q.isNotEmpty()) {
                val url = "https://www.youtube.com/results?search_query=" +
                    android.net.Uri.encode(q)
                mainViewModel.setPendingUrlForBrowserTab(vaultId, url)
                mainViewModel.setVaultSessionTab(vaultId, VaultTab.Browser)
            }
        },
        onWebSearch = { text ->
            val q = text.trim()
            if (q.isNotEmpty()) {
                val url = "https://www.google.com/search?q=" + android.net.Uri.encode(q)
                mainViewModel.setPendingUrlForBrowserTab(vaultId, url)
                mainViewModel.setVaultSessionTab(vaultId, VaultTab.Browser)
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
private fun GuideTabSingleViewer(
    vaultId: String,
    bookmark: com.twinlore.app.domain.SavedTab,
    mainViewModel: MainViewModel,
    isActive: Boolean,
    contentFocusRequester: FocusRequester?,
    bottomBarFocusRequester: FocusRequester?,
    positionSyncKey: Int,
) {
    val chromeVisible by mainViewModel.vaultSessionGuideChromeVisible.collectAsState()
    SingleGuideViewerScreen(
        vaultId = vaultId,
        bookmark = bookmark,
        chromeVisible = chromeVisible,
        isActive = isActive,
        contentFocusRequester = contentFocusRequester,
        bottomBarFocusRequester = bottomBarFocusRequester,
        positionSyncKey = positionSyncKey,
        onChromeToggle = {
            mainViewModel.setVaultSessionGuideChromeVisible(!chromeVisible)
        },
        onChromeHide = {
            if (chromeVisible) mainViewModel.setVaultSessionGuideChromeVisible(false)
        },
        onChromeReveal = {
            if (!chromeVisible) mainViewModel.setVaultSessionGuideChromeVisible(true)
        },
        onBack = { mainViewModel.closeSingleGuideForVault(vaultId) },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
private fun GuideTabScreen(
    vaultId: String,
    mainViewModel: MainViewModel,
    sessionVault: Vault?,
    isActive: Boolean,
    onBackFallback: () -> Unit,
    contentFocusRequester: FocusRequester?,
    bottomBarFocusRequester: FocusRequester?,
    positionSyncKey: Int,
) {
    // Wait for both restores to complete before mounting GuideScreen.
    // Without this gate, vault entry briefly mounts GuideScreen with the
    // saved bookmark before maybeRestore (async) finishes and sets the
    // dispatch map — visible as a top-bar / WebView flash.
    val groupRestoreDone by mainViewModel.groupRestoreDone.collectAsState()
    val singleGuideRestoreDone by mainViewModel.singleGuideRestoreDone.collectAsState()
    val palette = LocalThemePalette.current
    if (vaultId !in groupRestoreDone || vaultId !in singleGuideRestoreDone) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(palette.background)
        )
        return
    }
    val pdfLoadingVaults by mainViewModel.pdfLoadingByVault.collectAsState()
    val isPdfLoading = vaultId in pdfLoadingVaults
    Box(modifier = Modifier.fillMaxSize()) {
        GuideScreen(
            vaultId = vaultId,
            mainViewModel = mainViewModel,
            sessionVault = sessionVault,
            isEmbeddedInVaultSession = true,
            onAddGuideViaSearch = { mainViewModel.setVaultSessionTab(vaultId, VaultTab.Browser) },
            onOpenLinkInBrowser = { url ->
                mainViewModel.setPendingUrlForBrowserTab(vaultId, url)
                mainViewModel.setVaultSessionTab(vaultId, VaultTab.Browser)
            },
            isTabVisible = isActive,
            onBackFallback = onBackFallback,
            contentFocusRequester = contentFocusRequester,
            bottomBarFocusRequester = bottomBarFocusRequester,
            positionSyncKey = positionSyncKey,
            modifier = Modifier.fillMaxSize()
        )
        // Overlay shown after openPdfForVault's eager tab switch, until
        // PdfGuideViewer's first composition calls notifyPdfReady. Covers the
        // 1-3s window where GuideScreen mounts, polls Room, and the WebView
        // initializes — otherwise the Guide tab looks empty.
        if (isPdfLoading) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(palette.background),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                androidx.compose.foundation.layout.Column(
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
                ) {
                    androidx.compose.material3.CircularProgressIndicator(
                        color = palette.foreground
                    )
                    androidx.compose.material3.Text(
                        text = "Loading…",
                        color = palette.foreground,
                        style = androidx.compose.material3.MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}
