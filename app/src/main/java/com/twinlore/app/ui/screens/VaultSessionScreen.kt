package com.twinlore.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.focusable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.toArgb
import com.twinlore.app.ui.theme.ensureReadableAccent
import kotlinx.coroutines.delay
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.twinlore.app.data.SearchEngine
import com.twinlore.app.data.StatusBarMode
import com.twinlore.app.domain.VaultTab
import com.twinlore.app.ui.MainViewModel
import com.twinlore.app.ui.navigation.BottomBarItem
import com.twinlore.app.ui.navigation.CompactBottomBar
import com.twinlore.app.ui.navigation.FocusableTabContainer
import com.twinlore.app.ui.navigation.NavigableIconButton
import com.twinlore.app.ui.navigation.TabFocusRestorationManager
import com.twinlore.app.ui.screens.guide.GuideTabContent
import com.twinlore.app.ui.screens.browser.BrowserTabContent
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.twinlore.app.util.PerfLog

/**
 * Vault Session screen: single host with 4-5 tabs (Guide, Map, [Achievements], Checklist, Browser).
 *
 * All tabs are always composed (kept alive). Focus isolation via FocusableTabContainer
 * prevents inactive tabs from receiving D-pad focus or touch input.
 * Tab content is only reconstructed when vaultId changes (vault switch).
 * Achievements tab is only visible when RA or Steam credentials are configured.
 *
 * Bottom bar: tab buttons. Content area: all tabs stacked, only active one visible.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun VaultSessionScreen(
    vaultId: String,
    activeTab: VaultTab,
    mainViewModel: MainViewModel,
    modifier: Modifier = Modifier,
    preResolvedArtworkUri: String? = null,
    preResolvedAccentArgb: Int? = null
) {
    // Immersive landscape like the library: hide the system status bar across
    // all vault-session tabs (guide viewer, image viewer, …) while landscape.
    com.twinlore.app.ui.utils.HideStatusBarInLandscape()

    // Create ViewModels at screen level so they are not created inside conditional tab content (avoids crash when switching to Achievements tab)
    val guideViewModel: GuideViewerScreenViewModel = hiltViewModel()
    val achievementViewModel: AchievementDrawerViewModel = hiltViewModel()
    val steamAchievementViewModel: SteamAchievementDrawerViewModel = hiltViewModel()
    val settingsViewModel: SettingsViewModel = hiltViewModel()

    LaunchedEffect(Unit) {
        PerfLog.mark("VaultSessionScreen composed vaultId=$vaultId")
    }

    // A cold start with overlay mode on folds straight back from here. See
    // OverlayRestore for why it waits for the session's first frames.
    com.twinlore.app.ui.fold.OverlayRestore(vaultId)

    // And a vault coming back from overlay re-reads where the panel left its
    // guide and its image. The tabs below stay composed while the panel is
    // up, so they have to be told; see FoldController.overlayReturns.
    val positionSyncKey = com.twinlore.app.ui.fold.overlayReturnCount()

    // Pre-load vault once at session level so tab switches don't show "Loading..." (same flow, single source of truth)
    val vault by guideViewModel.getVault(vaultId).collectAsState(initial = null)
    // Artwork is resolved through resource_files; render-time URI cached so
    // both the blurred achievements bg and the foreground game logo see the same path.
    val sessionArtworkUri = com.twinlore.app.ui.utils.rememberResourceImage(vault?.artworkResourceId)
    val sessionArtworkPath = sessionArtworkUri?.toString()

    // Pre-resolved artwork URI from openVault(), passed as parameter from NavigationHost.
    // sessionArtworkPath (from rememberResourceImage) takes over once it resolves,
    // handling mid-session artwork changes.
    val effectiveArtworkPath = sessionArtworkPath ?: preResolvedArtworkUri

    // When Achievement wallpaper (logos) is shown, hide top and bottom bars for full-screen screensaver-like view; tap dismisses.
    // Both VMs are Activity-scoped, so showWallpaperView from the prior
    // vault carries over until something flips it back. Gate each on the
    // current vault actually having the corresponding link — otherwise an
    // unlinked vault inherits the wallpaper-shown flag from the previously
    // opened vault and the top/bottom bars stay hidden for no reason.
    val rawShowWallpaperView by achievementViewModel.showWallpaperView.collectAsState()
    val rawShowSteamWallpaperView by steamAchievementViewModel.showWallpaperView.collectAsState()
    val showWallpaperView = rawShowWallpaperView && vault?.raGameId != null
    val showSteamWallpaperView = rawShowSteamWallpaperView && vault?.steamAppId != null
    val anyWallpaperView = showWallpaperView || showSteamWallpaperView

    val focusColorState by settingsViewModel.focusColor.collectAsState(initial = com.twinlore.app.data.FocusColor.CRIMSON)

    // Adaptive accent, live from inside the session. The detected color is
    // normally persisted to rawAccentColorArgb only by LibraryScreen (when a
    // vault is focused). Toggling Adaptive on from the in-session display sheet
    // never hit that path, so the accent stayed on the last manual color until
    // you revisited the library. Push the current vault's pre-extracted vibrant
    // color (readability-adjusted) into rawAccentColorArgb here too, so the
    // MainActivity collector lights up LocalCachedFocusColor immediately.
    val sessionIsDark = com.twinlore.app.ui.LocalThemePalette.current.isDark
    val autoFocusColorDetection by settingsViewModel.autoFocusColorDetection.collectAsState(initial = false)
    LaunchedEffect(autoFocusColorDetection, vault?.artworkVibrantArgb, sessionIsDark) {
        if (!autoFocusColorDetection) return@LaunchedEffect
        val argb = vault?.artworkVibrantArgb ?: preResolvedAccentArgb ?: return@LaunchedEffect
        val readable = androidx.compose.ui.graphics.Color(argb)
            .ensureReadableAccent(sessionIsDark)
        settingsViewModel.setRawAccentColorArgb(readable.toArgb())
    }

    // Use the vault's pre-extracted vibrant color (passed from openVault) as the
    // accent color. Falls back to the settings focus color for vaults without artwork.
    val chipAccentColor = if (preResolvedAccentArgb != null) {
        androidx.compose.ui.graphics.Color(preResolvedAccentArgb)
    } else {
        focusColorState.color
    }
    val currentSearchEngine by settingsViewModel.searchEngine.collectAsState(initial = SearchEngine.GOOGLE)

    // Credentials to determine which achievement tabs to show
    val raCredentials by settingsViewModel.raCredentials.collectAsState(initial = null)
    val steamCredentials by settingsViewModel.steamCredentials.collectAsState(initial = null)
    val hasRaCreds = raCredentials != null
    val hasSteamCreds = steamCredentials != null
    val hasAchievementCredentials = hasRaCreds || hasSteamCreds

    // Show the AI assistant as a bottom-bar tab (gated, hideable).
    val aiBottomTab by mainViewModel.aiBottomTabVisible.collectAsState()
    val achievementsTabVisible by settingsViewModel.achievementsTabVisible.collectAsState()
    val statusBarMode by settingsViewModel.statusBarMode.collectAsState(initial = com.twinlore.app.data.StatusBarMode.STANDARD)
    // Achievements tab shows whenever the toggle is on — no longer auto-hidden when
    // credentials are missing (the tab's empty state lets the user add a key there).
    // Null until the settings value arrives, so the visibility guard below can
    // tell "Achievements is off" apart from "settings not loaded yet".
    val achievementsTabVisibleState by settingsViewModel.achievementsTabVisibleOrNull.collectAsState()
    val visibleTabs = remember(achievementsTabVisible, aiBottomTab) {
        buildList {
            add(VaultTab.Guide)
            add(VaultTab.Map)
            if (achievementsTabVisible) add(VaultTab.Achievements)
            add(VaultTab.Checklist)
            add(VaultTab.Browser)
            if (aiBottomTab) add(VaultTab.Ai)
        }
    }

    // Keep MainViewModel in sync so L1/R1 cycling uses visible tabs only
    androidx.compose.runtime.LaunchedEffect(visibleTabs) {
        mainViewModel.setVisibleVaultTabs(visibleTabs)
    }

    // A restored tab can be one that is not currently offered (a tab switched off
    // in Settings) — fall back to Guide rather than sit on a tab with no button.
    // Waits for the settings to load so the initial null does not kick a
    // legitimately restored tab.
    androidx.compose.runtime.LaunchedEffect(activeTab, visibleTabs, achievementsTabVisibleState) {
        if (achievementsTabVisibleState != null && activeTab !in visibleTabs) {
            mainViewModel.setVaultSessionTab(vaultId, VaultTab.Guide)
        }
    }

    // Achievement source tab selection: "ra" or "steam"
    var achievementSourceTab by remember { mutableStateOf(if (hasRaCreds) "ra" else "steam") }
    // Auto-select tab: if a game is linked to a specific source, show that source.
    // If no game linked, default based on available credentials.
    val vaultRaGameId = vault?.raGameId?.toIntOrNull()
    val vaultSteamAppId = vault?.steamAppId?.toIntOrNull()
    androidx.compose.runtime.LaunchedEffect(hasRaCreds, hasSteamCreds, vaultRaGameId, vaultSteamAppId) {
        when {
            vaultRaGameId != null -> achievementSourceTab = "ra"
            vaultSteamAppId != null -> achievementSourceTab = "steam"
            // Nothing linked yet: RetroAchievements leads whenever it is available at all,
            // including when BOTH accounts are connected. That case used to match no branch, so
            // the tab kept whatever the initial remember picked -- and the credential flows have
            // not emitted by first composition, so hasRaCreds reads false there and it opened on
            // Steam. Ordering RA first also makes the RA chip the selected (highlighted) one.
            hasRaCreds -> achievementSourceTab = "ra"
            hasSteamCreds -> achievementSourceTab = "steam"
        }
    }

    // Controller actions for Achievements tab are now routed via onPreviewKeyEvent
    // on the content Box (see below). The actions still flow through mainViewModel.controllerAction
    // and are consumed by the RetroAchievementContent/SteamAchievementContent composables.

    // Measured height of VaultSessionTopBar so tab content can offset itself to avoid overlap.
    val density = LocalDensity.current
    var topBarHeightDp by remember { mutableStateOf(0.dp) }

    // Content focus requester: each tab's primary focusable element can attach this.
    val contentFocusRequester = remember { FocusRequester() }

    // Bottom bar button requesters — declared here so content area can use activeTabButtonRequester
    val backRequester = remember { FocusRequester() }
    val guideRequester = remember { FocusRequester() }
    val mapRequester = remember { FocusRequester() }
    val achievementsRequester = remember { FocusRequester() }
    val checklistRequester = remember { FocusRequester() }
    val browserRequester = remember { FocusRequester() }
    val aiRequester = remember { FocusRequester() }
    val sleepRequester = remember { FocusRequester() }
    // D-pad DOWN from content goes directly to the active tab's button
    val activeTabButtonRequester = when (activeTab) {
        VaultTab.Guide -> guideRequester
        VaultTab.Map -> mapRequester
        VaultTab.Achievements -> achievementsRequester
        VaultTab.Checklist -> checklistRequester
        VaultTab.Browser -> browserRequester
        VaultTab.Ai -> aiRequester
    }
    // Bottom bar requester: for fallback focus requests
    val bottomBarFocusRequester = activeTabButtonRequester
    val focusManager = LocalFocusManager.current

    // Focus restoration manager: saves/restores focus state per tab within vault session
    val focusRestorationManager = remember { TabFocusRestorationManager() }

    // Focus model: bottom bar is the initial focus target, and the active vault
    // tab's button gets the focus ring on entry / on L1-R1 tab switch.
    // withFrameNanos waits one frame so the AnimatedVisibility-wrapped bottom bar
    // has been laid out before we request focus; otherwise the request fires
    // before the FocusRequester is bound and is silently dropped, leaving focus
    // wherever Compose preserved it from the previous screen.
    androidx.compose.runtime.LaunchedEffect(activeTab) {
        androidx.compose.runtime.withFrameNanos { }
        try { activeTabButtonRequester.requestFocus() } catch (_: Throwable) {}
    }

    // When chrome reappears after being hidden, re-focus the active tab's bottom bar button.
    // Without this, focus is lost (previously focused element was removed from composition
    // when chrome hid) and D-pad events leak to inactive tabs.
    var chromeFocusRequestTrigger by remember { mutableStateOf(0) }
    androidx.compose.runtime.LaunchedEffect(chromeFocusRequestTrigger) {
        if (chromeFocusRequestTrigger > 0) {
            kotlinx.coroutines.delay(50) // Wait for AnimatedVisibility to compose the bottom bar
            try { activeTabButtonRequester.requestFocus() } catch (_: Throwable) {}
        }
    }

        // Restore focus to bottom bar when requested (e.g. after TOC/find-on-page closes)
        androidx.compose.runtime.LaunchedEffect(Unit) {
            mainViewModel.requestBottomBarFocus.collect {
                chromeFocusRequestTrigger++
            }
        }

    val backFallback: () -> Unit = { mainViewModel.handleBackNavigation() }

    // Collected at the top level so the Browser/Checklist tabs' animated top
    // padding and the bottom bar's activeChromeVisible calculation can read the
    // same instance. (Checklist needs it up here because its tab block — below
    // — drives an animated padding too; without that the Checklist content
    // keeps a static top inset equal to the hidden top bar, leaving a dead
    // strip below the system status bar when chrome hides on scroll.)
    val browserChromeVisible by mainViewModel.vaultSessionBrowserChromeVisible.collectAsState()
    val checklistChromeVisible by mainViewModel.vaultSessionChecklistChromeVisible.collectAsState()
    // Hoisted for the Achievements tab's animated top padding — same dead-strip
    // fix as Checklist: the content slides up to fill the strip the top bar
    // leaves behind when chrome hides on scroll, instead of keeping a static
    // inset that strands an empty band below the system status bar.
    val achievementsChromeVisible by mainViewModel.vaultSessionAchievementsChromeVisible.collectAsState()
    val aiChromeVisible by mainViewModel.vaultSessionAiChromeVisible.collectAsState()
    // Hoisted for the Guide tab's animated top padding (page-group viewer):
    // when chrome hides on scroll, the WebView slides up to fill the strip
    // the top bar leaves behind. Without this the body would render with a
    // permanent dead band below the system status bar, same dead-strip bug
    // the Checklist comment above describes.
    val guideChromeVisible by mainViewModel.vaultSessionGuideChromeVisible.collectAsState()
    val mapChromeVisible by mainViewModel.vaultSessionMapChromeVisible.collectAsState()
    // Hide bottom bar when software keyboard is open (e.g. typing in edit fields).
    // Read the IME inset through derivedStateOf, not directly: its bottom is
    // snapshot state that ticks once per frame for the whole keyboard slide
    // animation, and a raw read here would invalidate this entire (very large)
    // composable body on every one of those frames. Only the open/closed flip
    // matters, so derive the boolean and let Compose skip the frames in between.
    // Same value, far fewer recompositions. Mirrors GuideScreen/BrowserScreen.
    // Not the IME inset alone: on the Thor's Android 13 the window resizes
    // instead and the inset reads zero; see rememberKeyboardVisible.
    val isKeyboardVisible by com.twinlore.app.ui.utils.rememberKeyboardVisible()
    // Exception: when the user is actively typing in the browser's URL field, keep the
    // top bar visible despite the IME. They're in the middle of searching and need the
    // page-name strip for context.
    val browserUrlFieldFocused by mainViewModel.browserUrlFieldFocused.collectAsState()
    // Hide system status bar (clock, battery, etc.) when chrome is hidden for immersion
    val activeChromeVisible = when (activeTab) {
        VaultTab.Guide -> guideChromeVisible
        VaultTab.Map -> mapChromeVisible
        VaultTab.Browser -> browserChromeVisible
        VaultTab.Achievements -> achievementsChromeVisible
        VaultTab.Checklist -> checklistChromeVisible
        VaultTab.Ai -> aiChromeVisible
    }
    val keepTopBarVisible by settingsViewModel.keepTopBarVisible.collectAsState(initial = false)
    // Whether VaultSessionTopBar's outer chrome gate is genuinely satisfied for
    // the active tab right now. Computed once, here -- before the tab content
    // below and before HideStatusBarWhen -- so both of them read the exact
    // same signal the bar's own AnimatedVisibility (further down) uses,
    // instead of each re-deriving it from the raw per-tab flag and drifting
    // out of sync with keepTopBarVisible (or the keyboard/URL-field exception,
    // or the Achievements wallpaper). See vaultSessionTopBarChromeVisible's
    // doc in VaultSessionChrome.kt for the full rule.
    val topBarVisible = vaultSessionTopBarChromeVisible(
        isKeyboardVisible = isKeyboardVisible,
        browserUrlFieldFocused = browserUrlFieldFocused,
        activeTab = activeTab,
        anyWallpaperView = anyWallpaperView,
        activeTabChromeVisible = activeChromeVisible,
        keepTopBarVisible = keepTopBarVisible,
    )

    // Portrait immersion for the content tabs: when a tab's own top chrome
    // auto-hides on scroll, also hide the system status bar (clock /
    // notifications) for a distraction-free view. Covers the readers (PDF /
    // single / page-group guide viewers, all under the Guide tab) and the
    // Browser. Landscape is already covered by HideStatusBarInLandscape above;
    // this adds the portrait case. Map keeps its status bar (its own immersive
    // handling).
    //
    // Checklist and Achievements are intentionally EXCLUDED: toggling the system
    // status bar on scroll resizes the window inset and shifts their (Compose,
    // statically-padded) content up/down — the "sliding"/sluggish movement. The
    // top game-name bar still fades on scroll; only the status bar stays put.
    //
    // Guide/Browser read topBarVisible (not their raw per-tab flag) here: the
    // vault bar reserves the system status bar's inset via
    // windowInsetsPadding(statusBars), so if this hid the status bar while
    // topBarVisible was still true -- e.g. keepTopBarVisible pinning the bar
    // past its tab's own auto-hide -- the bar's measured height, and therefore
    // topBarHeightDp, would shrink out from under it.
    com.twinlore.app.ui.utils.HideStatusBarWhen(
        hidden = (activeTab == VaultTab.Guide || activeTab == VaultTab.Browser) && !topBarVisible,
    )

    // System back / controller B for Achievements tab (no internal back logic)
    androidx.activity.compose.BackHandler(
        enabled = activeTab == VaultTab.Achievements
    ) {
        backFallback()
    }

    Box(modifier = modifier
        .fillMaxSize()
        .onFocusChanged { focusState ->
            //"Root Box focus: hasFocus=${focusState.hasFocus}, isFocused=${focusState.isFocused}")
            mainViewModel.updateComposeFocusState(focusState.hasFocus)
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                // Key/button presses generally show chrome (top/bottom bars) and reset the
                // auto-hide timer. Exception: in Achievements/Checklist, when chrome is
                // already visible, D-pad and A/B nav keys do not force-show; the per-tab
                // snapshotFlow scroll-direction logic decides chrome visibility for those.
                // Left/right analog sticks (scroll/pan) go through dispatchGenericMotionEvent
                // and never reach here.
                .onPreviewKeyEvent { keyEvent ->
                    if (keyEvent.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN) {
                        // A guide overlay (TOC) forces chrome hidden while it owns dpad focus;
                        // both the wake-and-consume and the show-on-any-key branches must stand
                        // down so its keys reach the overlay's focusables.
                        val guideOverlayOpen = activeTab == VaultTab.Guide &&
                            mainViewModel.vaultSessionGuideOverlayOpen.value
                        val chromeWasHidden = when (activeTab) {
                            VaultTab.Guide -> !mainViewModel.vaultSessionGuideChromeVisible.value && !guideOverlayOpen
                            VaultTab.Map -> !mainViewModel.vaultSessionMapChromeVisible.value
                            VaultTab.Browser -> !mainViewModel.vaultSessionBrowserChromeVisible.value
                            VaultTab.Achievements -> !mainViewModel.vaultSessionAchievementsChromeVisible.value
                            VaultTab.Checklist -> !mainViewModel.vaultSessionChecklistChromeVisible.value
                            VaultTab.Ai -> !mainViewModel.vaultSessionAiChromeVisible.value
                        }
                        val keyCode = keyEvent.nativeKeyEvent.keyCode
                        val isFaceButton = keyCode in listOf(
                            android.view.KeyEvent.KEYCODE_BUTTON_X,
                            android.view.KeyEvent.KEYCODE_BUTTON_Y,
                            android.view.KeyEvent.KEYCODE_BUTTON_START,
                            android.view.KeyEvent.KEYCODE_BUTTON_SELECT,
                            android.view.KeyEvent.KEYCODE_MENU,
                            android.view.KeyEvent.KEYCODE_BUTTON_MODE,
                            android.view.KeyEvent.KEYCODE_BUTTON_R2,
                            android.view.KeyEvent.KEYCODE_BUTTON_L2,
                            android.view.KeyEvent.KEYCODE_BUTTON_THUMBL,
                            android.view.KeyEvent.KEYCODE_BUTTON_THUMBR,
                        )
                        val isNavKey = keyCode in listOf(
                            android.view.KeyEvent.KEYCODE_DPAD_UP,
                            android.view.KeyEvent.KEYCODE_DPAD_DOWN,
                            android.view.KeyEvent.KEYCODE_DPAD_LEFT,
                            android.view.KeyEvent.KEYCODE_DPAD_RIGHT,
                            android.view.KeyEvent.KEYCODE_DPAD_CENTER,
                            android.view.KeyEvent.KEYCODE_BUTTON_A,
                            android.view.KeyEvent.KEYCODE_BUTTON_B,
                        )
                        val isAchOrCl = activeTab == VaultTab.Achievements || activeTab == VaultTab.Checklist

                        if (chromeWasHidden) {
                            // Wake chrome (existing first-press-after-hidden behavior).
                            when (activeTab) {
                                VaultTab.Guide -> mainViewModel.setVaultSessionGuideChromeVisible(true)
                                VaultTab.Map -> mainViewModel.setVaultSessionMapChromeVisible(true)
                                VaultTab.Browser -> mainViewModel.setVaultSessionBrowserChromeVisible(true)
                                VaultTab.Achievements -> mainViewModel.setVaultSessionAchievementsChromeVisible(true)
                                VaultTab.Checklist -> mainViewModel.setVaultSessionChecklistChromeVisible(true)
                                VaultTab.Ai -> mainViewModel.setVaultSessionAiChromeVisible(true)
                            }
                            mainViewModel.notifyChromeInteraction()
                            chromeFocusRequestTrigger++
                            if (!isFaceButton) return@onPreviewKeyEvent true
                        } else if (!guideOverlayOpen && (!isAchOrCl || !isNavKey)) {
                            // For Guide/Map/Browser keep "any keypress shows chrome". For
                            // Achievements/Checklist with chrome visible, only non-nav keys
                            // (face buttons) trigger the show; nav keys are deferred to the
                            // per-tab snapshotFlow(listState) scroll-direction logic.
                            when (activeTab) {
                                VaultTab.Guide -> mainViewModel.setVaultSessionGuideChromeVisible(true)
                                VaultTab.Map -> mainViewModel.setVaultSessionMapChromeVisible(true)
                                VaultTab.Browser -> mainViewModel.setVaultSessionBrowserChromeVisible(true)
                                VaultTab.Achievements -> mainViewModel.setVaultSessionAchievementsChromeVisible(true)
                                VaultTab.Checklist -> mainViewModel.setVaultSessionChecklistChromeVisible(true)
                                VaultTab.Ai -> mainViewModel.setVaultSessionAiChromeVisible(true)
                            }
                            mainViewModel.notifyChromeInteraction()
                        }
                    }
                    // Tab-level face button handler: routes X, Y, Start, Select, L2/R2
                    // to the correct controller action based on the active tab.
                    val code = keyEvent.nativeKeyEvent.keyCode
                    val action = keyEvent.nativeKeyEvent.action
                    if (action != android.view.KeyEvent.ACTION_DOWN) {
                        // Consume UP for handled buttons to prevent leakage
                        return@onPreviewKeyEvent code in listOf(
                            android.view.KeyEvent.KEYCODE_BUTTON_X,
                            android.view.KeyEvent.KEYCODE_BUTTON_Y,
                            android.view.KeyEvent.KEYCODE_BUTTON_START,
                            android.view.KeyEvent.KEYCODE_BUTTON_SELECT,
                            android.view.KeyEvent.KEYCODE_BUTTON_R2,
                            android.view.KeyEvent.KEYCODE_BUTTON_L2,
                            android.view.KeyEvent.KEYCODE_BUTTON_THUMBL,
                            android.view.KeyEvent.KEYCODE_BUTTON_THUMBR,
                            android.view.KeyEvent.KEYCODE_MENU,
                            android.view.KeyEvent.KEYCODE_BUTTON_MODE
                        )
                    }
                    when (activeTab) {
                        VaultTab.Guide -> when (code) {
                            android.view.KeyEvent.KEYCODE_BUTTON_X -> {
                                mainViewModel.triggerControllerAction(MainViewModel.ControllerAction.OPEN_FIND_ON_PAGE); true
                            }
                            android.view.KeyEvent.KEYCODE_BUTTON_Y -> {
                                mainViewModel.triggerControllerAction(MainViewModel.ControllerAction.OPEN_TOC); true
                            }
                            android.view.KeyEvent.KEYCODE_BUTTON_START, android.view.KeyEvent.KEYCODE_MENU -> {
                                mainViewModel.triggerControllerAction(MainViewModel.ControllerAction.BROWSER_SCROLL_MARKER_NEXT); true
                            }
                            android.view.KeyEvent.KEYCODE_BUTTON_SELECT, android.view.KeyEvent.KEYCODE_BUTTON_MODE -> {
                                mainViewModel.triggerControllerAction(MainViewModel.ControllerAction.BROWSER_SCROLL_MARKER_PREV); true
                            }
                            // L2/R2 bookmark switching disabled in Guide viewer (user uses picker instead)
                            android.view.KeyEvent.KEYCODE_BUTTON_THUMBL -> {
                                mainViewModel.triggerControllerAction(MainViewModel.ControllerAction.BROWSER_SCROLL_TO_TOP); true
                            }
                            android.view.KeyEvent.KEYCODE_BUTTON_THUMBR -> {
                                mainViewModel.triggerControllerAction(MainViewModel.ControllerAction.BROWSER_SCROLL_TO_END); true
                            }
                            else -> false
                        }
                        VaultTab.Map -> when (code) {
                            android.view.KeyEvent.KEYCODE_BUTTON_START, android.view.KeyEvent.KEYCODE_MENU -> {
                                mainViewModel.navigateMarker(MainViewModel.MarkerNavigationDirection.NEXT); true
                            }
                            android.view.KeyEvent.KEYCODE_BUTTON_SELECT, android.view.KeyEvent.KEYCODE_BUTTON_MODE -> {
                                mainViewModel.navigateMarker(MainViewModel.MarkerNavigationDirection.PREVIOUS); true
                            }
                            android.view.KeyEvent.KEYCODE_BUTTON_R2 -> {
                                mainViewModel.triggerControllerAction(MainViewModel.ControllerAction.MAP_NEXT_FILE); true
                            }
                            android.view.KeyEvent.KEYCODE_BUTTON_L2 -> {
                                mainViewModel.triggerControllerAction(MainViewModel.ControllerAction.MAP_PREV_FILE); true
                            }
                            android.view.KeyEvent.KEYCODE_BUTTON_THUMBL -> {
                                mainViewModel.triggerAddMarkerAtCenter(); true
                            }
                            else -> false
                        }
                        VaultTab.Achievements -> when (code) {
                            android.view.KeyEvent.KEYCODE_BUTTON_X -> {
                                mainViewModel.triggerControllerAction(MainViewModel.ControllerAction.ACHIEVEMENTS_REFRESH); true
                            }
                            android.view.KeyEvent.KEYCODE_BUTTON_Y -> {
                                mainViewModel.triggerControllerAction(MainViewModel.ControllerAction.ACHIEVEMENTS_WALLPAPER); true
                            }
                            else -> false
                        }
                        VaultTab.Browser -> when (code) {
                            android.view.KeyEvent.KEYCODE_BUTTON_START, android.view.KeyEvent.KEYCODE_MENU -> {
                                mainViewModel.triggerControllerAction(MainViewModel.ControllerAction.BROWSER_FORWARD); true
                            }
                            android.view.KeyEvent.KEYCODE_BUTTON_SELECT, android.view.KeyEvent.KEYCODE_BUTTON_MODE -> {
                                mainViewModel.triggerControllerAction(MainViewModel.ControllerAction.BROWSER_BACK); true
                            }
                            else -> false
                        }
                        VaultTab.Checklist -> when (code) {
                            android.view.KeyEvent.KEYCODE_BUTTON_Y -> {
                                mainViewModel.triggerControllerAction(MainViewModel.ControllerAction.CHECKLIST_ADD_ITEM); true
                            }
                            else -> false
                        }
                        VaultTab.Ai -> false
                    }
                }
        ) {
            val isGuide = activeTab == VaultTab.Guide
            val isMap = activeTab == VaultTab.Map
            val isAchievements = activeTab == VaultTab.Achievements
            val isChecklist = activeTab == VaultTab.Checklist
            val isBrowser = activeTab == VaultTab.Browser
            val isAi = activeTab == VaultTab.Ai


            // --- Guide Tab ---
            // Animated top padding so the WebView slides up to fill the space
            // the top bar leaves behind when chrome hides on scroll. A static
            // topBarHeightDp would leave a dead strip below the system status
            // bar because the top bar hides via AnimatedVisibility (alpha-only)
            // while topBarHeightDp retains its measured value. Same pattern as
            // the Checklist tab below.
            FocusableTabContainer(isActive = isGuide) {
                // While Guide is the active tab, key this off topBarVisible (the
                // bar's real on-screen state, hoisted above) rather than the raw
                // per-tab flag alone -- otherwise keepTopBarVisible (or the
                // keyboard/URL-field exception) can hold the bar up while this
                // padding still collapses to 0, sliding the WebView out from
                // under a bar that's still there. While Guide is NOT active this
                // still reads the raw flag, which the reset LaunchedEffect above
                // always holds true while backgrounded, so switching tabs stays
                // glitch-free (no spurious pad animation on tab switch).
                val guideTopPad by androidx.compose.animation.core.animateDpAsState(
                    targetValue = if (if (isGuide) topBarVisible else guideChromeVisible) topBarHeightDp else 0.dp,
                    animationSpec = androidx.compose.animation.core.tween(durationMillis = 200),
                    label = "guide_tab_top_padding"
                )
                GuideTabContent(
                    vaultId = vaultId,
                    mainViewModel = mainViewModel,
                    sessionVault = vault,
                    isActive = isGuide,
                    onBackFallback = backFallback,
                    contentFocusRequester = if (isGuide) contentFocusRequester else null,
                    bottomBarFocusRequester = if (isGuide) activeTabButtonRequester else null,
                    positionSyncKey = positionSyncKey,
                    modifier = Modifier.fillMaxSize().padding(top = guideTopPad)
                )
            }

            // --- Map Tab ---
            FocusableTabContainer(isActive = isMap) {
                MapTabContent(
                    vaultId = vaultId,
                    mainViewModel = mainViewModel,
                    onBackToLibrary = { mainViewModel.handleBackNavigation() },
                    sessionVault = vault,
                    isTabVisible = isMap,
                    contentFocusRequester = if (isMap) contentFocusRequester else null,
                    bottomBarFocusRequester = if (isMap) activeTabButtonRequester else null,
                    positionSyncKey = positionSyncKey,
                    modifier = Modifier.fillMaxSize().padding(top = topBarHeightDp)
                )
            }

            // --- Achievements Tab ---
            FocusableTabContainer(isActive = isAchievements) {
                var showUnlinkConfirm by remember { mutableStateOf(false) }
                val raGameId = vault?.raGameId?.toIntOrNull()
                val steamAppId = vault?.steamAppId?.toIntOrNull()
                val hasLinkedRa = raGameId != null
                val hasLinkedSteam = steamAppId != null
                // Only show source tabs when both credentials exist AND no game is linked yet
                val showBothTabs = hasRaCreds && hasSteamCreds && !hasLinkedRa && !hasLinkedSteam
                val isRaTab = achievementSourceTab == "ra"
                val isSteamTab = achievementSourceTab == "steam"

                // The blur background fills the full tab; only the foreground
                // content (in the inner Box below) is inset by the top bar height.
                // The inset is ANIMATED on chrome visibility (like the AI tab): when
                // the bar hides on scroll the content slides up to fill the freed
                // strip instead of leaving a dead band where the bar used to be.
                // See the Guide tab's guideTopPad comment above: while Achievements
                // is active, key off topBarVisible (already accounts for
                // keepTopBarVisible and the wallpaper suppression) instead of the
                // raw per-tab flag alone, so this padding never drifts from the
                // bar's real on-screen state. Inactive reads the raw flag, which
                // stays true while backgrounded.
                val achievementsTopPad by androidx.compose.animation.core.animateDpAsState(
                    targetValue = if (if (isAchievements) topBarVisible else achievementsChromeVisible) topBarHeightDp else 0.dp,
                    label = "achievementsTopPad",
                )
                Box(modifier = Modifier.fillMaxSize()) {
                    // No blurred art behind the list any more, in either mode: a
                    // solid ground reads better under two hundred small rows.
                    // Foreground content carries the animated top inset; the blur
                    // background above fills the whole tab.
                    Box(modifier = Modifier.fillMaxSize().padding(top = achievementsTopPad)) {
                    // Focus requester for achievement content's first focusable (header or auto-assign button).
                    // Source chips DOWN links to this; content UP links back to source chips.
                    val achievementContentFirstFocus = remember { FocusRequester() }
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Source tabs row (only when both credentials are configured)
                        if (showBothTabs && !anyWallpaperView) {
                            val paletteChip = com.twinlore.app.ui.LocalThemePalette.current
                            val isDarkTheme = paletteChip.isDark
                            val chipBg = paletteChip.background
                            val chipBgAlpha = chipBg.copy(alpha = 0.95f)
                            val chipTextColor = paletteChip.foreground
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                                    .onPreviewKeyEvent { keyEvent ->
                                        if (keyEvent.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN &&
                                            keyEvent.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN
                                        ) {
                                            try { achievementContentFirstFocus.requestFocus() } catch (_: Throwable) {}
                                            true
                                        } else keyEvent.nativeKeyEvent.action == android.view.KeyEvent.ACTION_UP &&
                                            keyEvent.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN
                                    },
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                com.twinlore.app.ui.navigation.NavigableButton(
                                    text = "RetroAchievements",
                                    onClick = { achievementSourceTab = "ra" },
                                    focusId = "source_chip_ra",
                                    externalFocusRequester = contentFocusRequester,
                                    containerColor = if (isRaTab) chipAccentColor.copy(alpha = 0.25f) else chipBgAlpha,
                                    contentColor = if (isRaTab) chipAccentColor else chipTextColor,
                                    compact = true
                                )
                                com.twinlore.app.ui.navigation.NavigableButton(
                                    text = "Steam",
                                    onClick = { achievementSourceTab = "steam" },
                                    focusId = "source_chip_steam",
                                    containerColor = if (isSteamTab) chipAccentColor.copy(alpha = 0.25f) else chipBgAlpha,
                                    contentColor = if (isSteamTab) chipAccentColor else chipTextColor,
                                    compact = true
                                )
                            }
                        }
                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            if (vault != null) {
                                val v = vault!!
                                val gameLogoModel = com.twinlore.app.ui.utils.ImagePathUtils.toImageData(sessionArtworkPath, vaultId)
                                if (isRaTab) {
                                    RetroAchievementContent(
                                        vaultId = vaultId,
                                        gameTitle = v.displayName,
                                        raGameId = v.raGameId?.toIntOrNull(),
                                        gameLogoModel = gameLogoModel,
                                        onOpenInBrowser = { url ->
                                            mainViewModel.setPendingUrlForBrowserTab(vaultId, url)
                                            mainViewModel.setVaultSessionTab(vaultId, VaultTab.Browser)
                                        },
                                        onDismiss = { },
                                        viewModel = achievementViewModel,
                                        settingsViewModel = settingsViewModel,
                                        showHeaderActionRow = true,
                                        mainViewModel = mainViewModel,
                                        isTabVisible = isAchievements,
                                        contentFocusRequester = if (isAchievements && isRaTab) (if (showBothTabs) achievementContentFirstFocus else contentFocusRequester) else null,
                                        sourceChipsFocusRequester = if (showBothTabs) contentFocusRequester else null,
                                        bottomBarFocusRequester = if (isAchievements) activeTabButtonRequester else null
                                    )
                                } else {
                                    SteamAchievementContent(
                                        vaultId = vaultId,
                                        gameTitle = v.displayName,
                                        steamAppId = v.steamAppId?.toIntOrNull(),
                                        gameLogoModel = gameLogoModel,
                                        onOpenInBrowser = { url ->
                                            mainViewModel.setPendingUrlForBrowserTab(vaultId, url)
                                            mainViewModel.setVaultSessionTab(vaultId, VaultTab.Browser)
                                        },
                                        onDismiss = { },
                                        viewModel = steamAchievementViewModel,
                                        settingsViewModel = settingsViewModel,
                                        showHeaderActionRow = true,
                                        mainViewModel = mainViewModel,
                                        isTabVisible = isAchievements,
                                        contentFocusRequester = if (isAchievements && isSteamTab) (if (showBothTabs) achievementContentFirstFocus else contentFocusRequester) else null,
                                        sourceChipsFocusRequester = if (showBothTabs) contentFocusRequester else null,
                                        bottomBarFocusRequester = if (isAchievements) activeTabButtonRequester else null
                                    )
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("Loading...", style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                        }
                    }
                    } // close the inset content box (blur background above fills the full tab)
                    // No Steam or RetroAchievements account connected — cover the tab
                    // (incl. the RA auto-assign UI) with a single connect prompt.
                    if (!hasRaCreds && !hasSteamCreds) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background)
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                        ) {
                            Text("Connect an account", style = MaterialTheme.typography.titleLarge)
                            Text(
                                "Add your Steam or RetroAchievements account to see achievements here. It works best with a personal key.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                            Button(onClick = { mainViewModel.navigateToSettings(expandAchievements = true) }) {
                                Text("Add account")
                            }
                            TextButton(onClick = {
                                settingsViewModel.setAchievementsTabVisible(false)
                                mainViewModel.setVaultSessionTab(vaultId, VaultTab.Guide)
                            }) {
                                Text("Hide Achievements tab")
                            }
                            Text(
                                "You can turn it back on in Settings.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
                            )
                        }
                    }
                    if (showUnlinkConfirm) {
                        AlertDialog(
                            onDismissRequest = { showUnlinkConfirm = false },
                            containerColor = MaterialTheme.colorScheme.surface,
                            title = { Text(if (isRaTab) "Unlink RetroAchievements game?" else "Unlink Steam game?") },
                            text = {
                                Text(if (isRaTab)
                                    "This will unlink the RetroAchievements game. You can link a different game later."
                                else
                                    "This will unlink the Steam game. You can link a different game later."
                                )
                            },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        if (isRaTab) achievementViewModel.unlinkGame(vaultId)
                                        else steamAchievementViewModel.unlinkGame(vaultId)
                                        showUnlinkConfirm = false
                                    }
                                ) { Text("Unlink") }
                            },
                            dismissButton = {
                                TextButton(onClick = { showUnlinkConfirm = false }) { Text("Cancel") }
                            }
                        )
                    }
                    // Solid cap behind the shared top bar so the achievements tab's top
                    // strip reads as a solid bar (matching the session chrome) instead of
                    // showing the blurred artwork through the transparent bar. Height
                    // tracks the animated inset, so it collapses to nothing when the
                    // chrome hides on scroll.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(achievementsTopPad)
                            .align(Alignment.TopCenter)
                            .background(com.twinlore.app.ui.LocalThemePalette.current.background)
                    )
                }
            }

            // --- Checklist Tab ---
            // Animated top inset (like the AI tab): reserve the top bar's height
            // while the chrome is visible, then animate to 0 when it hides on
            // scroll so the content slides up to fill the freed strip instead of
            // leaving a dead band where the bar used to be.
            // See the Guide tab's guideTopPad comment above: while Checklist is
            // active, key off topBarVisible instead of the raw per-tab flag alone
            // so this padding never drifts from the bar's real on-screen state.
            // Inactive reads the raw flag, which stays true while backgrounded.
            val checklistTopPad by androidx.compose.animation.core.animateDpAsState(
                targetValue = if (if (isChecklist) topBarVisible else checklistChromeVisible) topBarHeightDp else 0.dp,
                label = "checklistTopPad",
            )
            FocusableTabContainer(isActive = isChecklist) {
                ChecklistScreen(
                    vaultId = vaultId,
                    isTabVisible = isChecklist,
                    onNavigateToBrowser = { query ->
                        val url = buildSearchUrlForEngine(currentSearchEngine, query)
                        mainViewModel.setPendingUrlForBrowserTab(vaultId, url)
                        mainViewModel.setVaultSessionTab(vaultId, VaultTab.Browser)
                    },
                    mainViewModel = mainViewModel,
                    contentFocusRequester = if (isChecklist) contentFocusRequester else null,
                    // Inset the scrollable content (not the whole screen) so the
                    // blurred background fills the strip behind the top bar; when
                    // the bar hides on scroll the inset animates to 0 and the
                    // content fills the freed strip.
                    topContentInset = checklistTopPad
                )
            }

            // --- Browser Tab ---
            // WebView is edge-to-edge — no Compose padding around it. The two
            // top chrome bars (VaultSessionTopBar here + GuideBrowserTopBar
            // inside BrowserScreen) and the CompactBottomBar are pure fade
            // overlays at the top and bottom; they sit visually on top of the
            // WebView when visible.
            //
            // Standard mobile-browser model (Chrome on Android, Safari on iOS):
            // the WebView never resizes, so the document never shifts when
            // chrome appears or disappears. When chrome is visible it briefly
            // overlaps the very top/bottom strip of visible content; the
            // chrome auto-hides aggressively on scroll-down (see the
            // scroll-direction LaunchedEffect in BrowserScreen) so reading is
            // full-screen most of the time.
            FocusableTabContainer(isActive = isBrowser) {
                BrowserTabContent(
                    vaultId = vaultId,
                    mainViewModel = mainViewModel,
                    sessionVault = vault,
                    isActive = isBrowser,
                    onBackFallback = backFallback,
                    contentFocusRequester = if (isBrowser) contentFocusRequester else null,
                    // Pass the outer game-name bar's measured height down so
                    // BrowserScreen's URL bar stacks under it instead of
                    // overlapping at Y=0. WebView itself stays edge-to-edge.
                    vaultTopBarHeightDp = topBarHeightDp,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // --- AI Tab ---
            // Animated top/bottom padding so messages fill the space the vault
            // chrome (top bar + bottom tab bar) leaves when it hides on scroll.
            // Top padding: see the Guide tab's guideTopPad comment above -- while
            // AI is active, key off topBarVisible (the shared top bar's real
            // on-screen state) instead of the raw per-tab flag alone. Bottom
            // padding is about the separate bottom nav bar, not the shared top
            // bar keepTopBarVisible pins, so it keeps reading the raw flag.
            val aiTopPad by androidx.compose.animation.core.animateDpAsState(
                targetValue = if (if (isAi) topBarVisible else aiChromeVisible) topBarHeightDp else 0.dp,
                label = "aiTopPad",
            )
            val aiBottomPad by androidx.compose.animation.core.animateDpAsState(
                targetValue = if (aiChromeVisible) 64.dp else 0.dp,
                label = "aiBottomPad",
            )
            FocusableTabContainer(isActive = isAi) {
                com.twinlore.app.ui.screens.ai.AiAssistantScreen(
                    vaultId = vaultId,
                    onBack = backFallback,
                    onHide = { mainViewModel.setVaultSessionTab(vaultId, VaultTab.Guide) },
                    bottomInset = aiBottomPad,
                    chromeVisible = aiChromeVisible,
                    onChromeVisibilityChange = { mainViewModel.setVaultSessionAiChromeVisible(it) },
                    mainViewModel = mainViewModel,
                    isTabVisible = isAi,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = aiTopPad),
                )
            }
        }

        // Bottom bar: Back to Library + dynamic tab buttons + Sleep (landscape only)
        val configuration = androidx.compose.ui.platform.LocalConfiguration.current
        val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

        // mapChromeVisible/guideChromeVisible, isKeyboardVisible, browserUrlFieldFocused,
        // activeChromeVisible, keepTopBarVisible and topBarVisible are all hoisted to
        // the top of the function (next to `density`) because HideStatusBarWhen
        // and the tab blocks' animated top padding also need them.
        // System status bar is hidden globally in MainActivity.hideSystemBars()

        // Reset chrome for tabs we're leaving (so they start visible next time)
        androidx.compose.runtime.LaunchedEffect(activeTab) {
            if (activeTab != VaultTab.Map) mainViewModel.setVaultSessionMapChromeVisible(true)
            if (activeTab != VaultTab.Guide) mainViewModel.setVaultSessionGuideChromeVisible(true)
            if (activeTab != VaultTab.Browser) mainViewModel.setVaultSessionBrowserChromeVisible(true)
            if (activeTab != VaultTab.Achievements) mainViewModel.setVaultSessionAchievementsChromeVisible(true)
            if (activeTab != VaultTab.Checklist) mainViewModel.setVaultSessionChecklistChromeVisible(true)
            if (activeTab != VaultTab.Ai) mainViewModel.setVaultSessionAiChromeVisible(true)
        }

        // Map each VaultTab to its icon, label, and focus requester
        val tabIconMap = mapOf(
            VaultTab.Guide to Triple(Icons.Default.MenuBook, "Guide", guideRequester),
            VaultTab.Map to Triple(Icons.Default.Map, "Image", mapRequester),
            VaultTab.Achievements to Triple(Icons.Default.EmojiEvents, "Achieve", achievementsRequester),
            VaultTab.Checklist to Triple(Icons.Default.Checklist, "Checklist", checklistRequester),
            VaultTab.Browser to Triple(Icons.Default.Language, "Browser", browserRequester),
            VaultTab.Ai to Triple(Icons.Default.AutoAwesome, "AI", aiRequester),
        )
        val items = buildList {
            add(BottomBarItem(
                icon = Icons.Default.ArrowBack,
                label = "Library",
                onClick = { backFallback() },
                focusRequester = backRequester
            ))
            visibleTabs.forEachIndexed { index, tab ->
                val (icon, label, requester) = tabIconMap[tab]!!
                add(BottomBarItem(
                    icon = icon,
                    label = label,
                    onClick = {
                        if (activeTab == tab) {
                            mainViewModel.emitTabReselected(tab)
                        } else {
                            mainViewModel.setVaultSessionTab(vaultId, tab)
                        }
                    },
                    focusRequester = requester
                ))
            }
            if (isLandscape) {
                add(BottomBarItem(
                    icon = Icons.Default.Bedtime,
                    label = "Sleep",
                    onClick = { mainViewModel.enterSleepMode() },
                    focusRequester = sleepRequester
                ))
            }
        }
        // --- Top status bar: game name (left) + clock/battery/wifi (right) ---
        // See vaultSessionTopBarChromeVisible's doc (VaultSessionChrome.kt) for the
        // full rule -- GuideScreen/BrowserScreen call the same function so their
        // fold buttons stay in sync with this bar's real visibility. topBarVisible
        // itself is computed once, near the top of this function (see there for
        // why), and reused here rather than recomputed.
        androidx.compose.animation.AnimatedVisibility(
            visible = topBarVisible,
            enter = androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .zIndex(24f)
                .onGloballyPositioned { coordinates ->
                    with(density) { topBarHeightDp = coordinates.size.height.toDp() }
                }
        ) {
            // What the top bar renders depends on which tab is active:
            //  - Guide/Map/Browser: the active viewer's published doc title (when any),
            //    else the vault's display name. NOT joined with the game name — when a
            //    document is open it owns the line.
            //  - Achievements: the linked achievement set's game title (Steam first,
            //    then RetroAchievements), which can differ from vault.displayName.
            //    Falls back to vault.displayName when nothing is linked / synced.
            //  - Other tabs (Checklist, …): vault.displayName.
            //
            // Steam/RA progress flows are Activity-scoped and hold the prior
            // vault's value until something clears them. Gate each title on
            // the current vault actually having the corresponding link, so a
            // newly-opened unlinked vault doesn't display the previous
            // vault's game name. (Screen-side filter; VM left untouched.)
            val activeDocTitleMap by mainViewModel.activeDocTitle.collectAsState()
            val raProgress by achievementViewModel.progress.collectAsState()
            val steamProgress by steamAchievementViewModel.progress.collectAsState()
            val gameDisplayName = vault?.displayName ?: ""
            val topBarTitle = when (activeTab) {
                VaultTab.Achievements -> {
                    val steamTitle = vault?.steamAppId
                        ?.let { steamProgress?.gameTitle?.takeIf { t -> t.isNotBlank() } }
                    val raTitle = vault?.raGameId
                        ?.let { raProgress?.gameTitle?.takeIf { t -> t.isNotBlank() } }
                    steamTitle ?: raTitle ?: gameDisplayName
                }
                else -> {
                    val doc = activeDocTitleMap[vaultId to activeTab]?.takeIf { it.isNotBlank() }
                    doc ?: gameDisplayName
                }
            }
            // Doc-switcher dropdown source: every SavedTab in this vault,
            // sorted newest-first. Only surfaced while the Guide tab is
            // active — the other tabs (Map / Browser / Achievements /
            // Checklist) don't render docs, so the switcher would have
            // nothing meaningful to swap to. Caller gates by emptying the
            // list outside Guide; the top bar's chevron + tap target
            // disappear automatically.
            val allVaultDocs by remember(vaultId) {
                mainViewModel.getTabsForVault(vaultId)
            }.collectAsState(initial = emptyList())
            val docsForSwitcher = remember(allVaultDocs, activeTab) {
                if (activeTab != VaultTab.Guide) emptyList()
                else allVaultDocs.sortedByDescending { it.lastAccessed.time }
            }
            // No inner status-bar gate any more: Hidden is gone, so the bar
            // is a function of the chrome alone. The enclosing
            // AnimatedVisibility still owns that, and still carries the
            // onGloballyPositioned that records the bar's measured height.
            VaultSessionTopBar(
                gameName = topBarTitle,
                accentColor = chipAccentColor,
                statusBarMode = statusBarMode,
                vaultDocs = docsForSwitcher,
                onSwitchDoc = { doc ->
                    mainViewModel.switchActiveDoc(vaultId, doc)
                },
                onChromeInteraction = { mainViewModel.notifyChromeInteraction() },
            )
        }

        CompactBottomBar(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .zIndex(24f),
            items = items,
            visible = !isKeyboardVisible &&
                !(activeTab == VaultTab.Achievements && anyWallpaperView) &&
                (activeTab != VaultTab.Map || mapChromeVisible) &&
                (activeTab != VaultTab.Guide || guideChromeVisible) &&
                (activeTab != VaultTab.Browser || browserChromeVisible) &&
                (activeTab != VaultTab.Achievements || achievementsChromeVisible) &&
                (activeTab != VaultTab.Checklist || checklistChromeVisible) &&
                (activeTab != VaultTab.Ai || aiChromeVisible),
            onInteraction = { mainViewModel.notifyChromeInteraction() },
            selectedIndex = visibleTabs.indexOf(activeTab) + 1,
            onMoveToTabsRow = {
                // D-pad UP from bottom bar: ensure chrome (top bar) is visible, then focus content.
                when (activeTab) {
                    VaultTab.Guide -> mainViewModel.setVaultSessionGuideChromeVisible(true)
                    VaultTab.Map -> mainViewModel.setVaultSessionMapChromeVisible(true)
                    VaultTab.Browser -> mainViewModel.setVaultSessionBrowserChromeVisible(true)
                    VaultTab.Achievements -> mainViewModel.setVaultSessionAchievementsChromeVisible(true)
                    VaultTab.Checklist -> mainViewModel.setVaultSessionChecklistChromeVisible(true)
                    VaultTab.Ai -> mainViewModel.setVaultSessionAiChromeVisible(true)
                }
                mainViewModel.notifyChromeInteraction()
                // Try the screen-supplied focus anchor first (e.g. Achievements' Refresh button,
                // Checklist empty-state primary button). If no anchor is attached for the current
                // tab state, fall back to Compose's spatial focus traversal so the user still
                // moves UP into the nearest content focusable.
                val anchored = runCatching { contentFocusRequester.requestFocus() }.isSuccess
                if (!anchored) {
                    focusManager.moveFocus(FocusDirection.Up)
                }
            }
        )
    }
}

/**
 * Custom top bar for vault session.
 * Game name on the left; clock/wifi/battery on the right in landscape.
 * Sits below the Android system status bar via windowInsetsPadding.
 */
@Composable
private fun VaultSessionTopBar(
    gameName: String,
    accentColor: androidx.compose.ui.graphics.Color,
    /** Drives [vaultSessionTitleVisible]: in Performance the game name steps
     *  aside so the meters, the clock and the buttons beside them keep their
     *  full width. The cluster's own mode handling lives in
     *  [LibraryStatusIndicators], which reads the setting itself. */
    statusBarMode: StatusBarMode,
    modifier: Modifier = Modifier,
    /** Other docs in the vault for the doc-switcher dropdown. Empty list
     *  hides the dropdown (and the chevron hint). Caller decides what to
     *  pass in — typically only populated while the Guide tab is active. */
    vaultDocs: List<com.twinlore.app.domain.SavedTab> = emptyList(),
    /** Tap handler invoked when the user picks an entry from the switcher.
     *  Null disables the dropdown even if [vaultDocs] is non-empty. */
    onSwitchDoc: ((com.twinlore.app.domain.SavedTab) -> Unit)? = null,
    /** Resets the host's chrome idle clock, the same notification
     *  CompactBottomBar's `onInteraction` sends. Controls in this bar that
     *  hand the bar back to the auto-hide need it or they can dismiss
     *  themselves. */
    onChromeInteraction: () -> Unit = {},
) {
    val cachedFocusState = com.twinlore.app.ui.LocalCachedFocusColor.current
    val focusColor = cachedFocusState?.value ?: accentColor
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
    val barFillAlpha = 0.95f
    var docSwitcherExpanded by remember { mutableStateOf(false) }
    val docSwitcherEnabled = vaultDocs.isNotEmpty() && onSwitchDoc != null
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = barFillAlpha))
    ) {
        VaultSessionTopBarRow(
            statusBarMode = statusBarMode,
            isLandscape = isLandscape,
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.statusBars)
                .padding(start = 16.dp)
                .padding(top = 2.dp, bottom = 0.dp),
            title = { titleModifier ->
            if (gameName.isNotBlank()) {
                // When the doc-switcher is enabled, wrap the title in a Box
                // so the DropdownMenu anchors directly under the tappable
                // game-name Row. The chevron is a discoverability hint;
                // tapping anywhere on the Row toggles the dropdown.
                // The slot's own share of the row is decided by
                // VaultSessionTopBarRow: the whole width in Standard, none of
                // it in Performance, where all this holds is the chevron.
                Box(modifier = titleModifier) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = if (docSwitcherEnabled) {
                            Modifier.clickable { docSwitcherExpanded = true }
                        } else Modifier
                    ) {
                        if (vaultSessionTitleVisible(statusBarMode, isLandscape)) {
                            Text(
                                text = gameName,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                                color = focusColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                        }
                        // The chevron stays behind when the name goes: it is
                        // the Guide tab's only way to change document, and a
                        // title that costs a feature to hide is too expensive.
                        if (docSwitcherEnabled) {
                            androidx.compose.material3.Icon(
                                imageVector = Icons.Filled.ArrowDropDown,
                                contentDescription = "Switch document",
                                tint = focusColor,
                                modifier = Modifier.padding(start = 2.dp)
                            )
                        }
                    }
                    if (docSwitcherEnabled) {
                        androidx.compose.material3.DropdownMenu(
                            expanded = docSwitcherExpanded,
                            onDismissRequest = { docSwitcherExpanded = false }
                        ) {
                            vaultDocs.forEach { doc ->
                                androidx.compose.material3.DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(
                                                text = doc.displayTitle.ifBlank {
                                                    doc.url.ifBlank { "(untitled)" }
                                                },
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                            Text(
                                                text = com.twinlore.app.domain.GuideFileType
                                                    .fromFileTypeColumn(doc.fileType)
                                                    .displayLabel,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    },
                                    onClick = {
                                        docSwitcherExpanded = false
                                        onSwitchDoc?.invoke(doc)
                                    }
                                )
                            }
                        }
                    }
                }
            }
            },
            pin = {
                // iconSize 20.dp, not IconButton's usual 24: this sits directly
                // beside LibraryStatusIndicators' 18dp clock/wifi/battery glyphs,
                // and 24 read as an oversized outlier against them. Deliberate,
                // not a leftover.
                if (isLandscape) com.twinlore.app.ui.fold.PinTopBarButton(
                    tint = com.twinlore.app.ui.LocalThemePalette.current.foreground.copy(alpha = 0.75f),
                    iconSize = 20.dp,
                    // Unpinning hands the bar back to the auto-hide clock. Without
                    // this the clock is already stale, so the bar can disappear on
                    // the very tap that released it.
                    onInputReceived = onChromeInteraction,
                )
            },
            divider = {
                if (isLandscape) Text(
                    text = "|",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            cluster = { clusterModifier ->
                if (isLandscape) LibraryStatusIndicators(
                    modifier = clusterModifier,
                    perfMetersEnabled = true,
                )
            },
            fold = {
                if (isLandscape) com.twinlore.app.ui.fold.FoldButton()
            },
        )
    }
}

