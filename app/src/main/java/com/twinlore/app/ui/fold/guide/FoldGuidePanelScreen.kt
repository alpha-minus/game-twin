package com.twinlore.app.ui.fold.guide

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import android.view.WindowInsets
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import com.twinlore.app.ui.navigation.FocusableTabContainer

/**
 * The navigation bar's height, straight from the window manager.
 *
 * `WindowInsets.navigationBars` reports zero in an overlay window: nothing
 * dispatches insets to one. `currentWindowMetrics` is not a dispatch, it is a
 * query, and it answers.
 */
@Composable
private fun systemBottomInset(): Dp {
    val context = LocalContext.current
    val density = LocalDensity.current
    return remember(context, density) {
        // The query first, for the device that answers it.
        val queried = runCatching {
            context.getSystemService(WindowManager::class.java)
                ?.currentWindowMetrics
                ?.windowInsets
                ?.getInsets(WindowInsets.Type.navigationBars())
                ?.bottom
        }.getOrNull() ?: 0
        // And the platform's own dimension for the device that does not. The
        // Thor is the second kind: an overlay window there reports no insets at
        // all, while the display itself is 1080 tall with 1025 usable.
        val declared = runCatching {
            val id = context.resources
                .getIdentifier("navigation_bar_height", "dimen", "android")
            if (id > 0) context.resources.getDimensionPixelSize(id) else 0
        }.getOrNull() ?: 0
        val px = maxOf(queried, declared)
        with(density) { px.toDp() }
    }
}

/** What the panel can be asked to do. Every one of them belongs to the host. */
data class FoldGuideActions(
    val onOpenApp: () -> Unit = {},
    /** Return, held: back to the app on the screen it is not on. */
    val onOpenAppOnOtherScreen: () -> Unit = {},
    val onSwap: () -> Unit = {},
    val onToggleBar: () -> Unit = {},
    val onSetBarVisible: (Boolean) -> Unit = {},
    val onTogglePin: () -> Unit = {},
    val onSelectSurface: (FoldPanelSurface) -> Unit = {},
    val onHandleSizePicked: (Int) -> Unit = {},
    val onHandleAlphaPicked: (Float) -> Unit = {},
    /** How small the handle is drawn while this panel is over it. */
    val onHandleRecessPicked: (Float) -> Unit = {},
    /** The drag ended: write what the two sliders settled on. */
    val onHandleStyleCommitted: () -> Unit = {},
    /** The size slider is held, so draw the handle at true size while it is. */
    val onHandleSizePreviewing: (Boolean) -> Unit = {},
    /** Leave the mode and land the app on its own settings screen. */
    val onOpenAppSettings: () -> Unit = {},
    val onTapStatus: () -> Unit = {},
    val onDismissIntro: () -> Unit = {},
    val onCloseApp: () -> Unit = {},
    /**
     * Leave the mode for the whole app, from the held handle's menu. Not
     * [onOpenApp]: that one keeps the panel's place, pointing the app at the
     * guide being read so Return lands where you were. This one is an exit
     * from the mode, so it goes the way the app's own unfold goes.
     */
    val onLeaveOverlayForApp: () -> Unit = {},
    /**
     * The bar's Library entry. Also an exit from the mode, like
     * [onLeaveOverlayForApp], but it names where the app lands: the library,
     * rather than the game the panel was open on.
     */
    val onReturnToLibrary: () -> Unit = {},
    val onDismissCloseApp: () -> Unit = {},
    /** The held handle's Swap: to the other screen, staying in the mode. */
    val onSwapFromHold: () -> Unit = {},
    val onSwitchDetectedGame: () -> Unit = {},
    val onDismissDetectedGame: () -> Unit = {},
    val onSupportFromPanel: () -> Unit = {},
    val onTextZoomPicked: (Float) -> Unit = {},
    val onThemeInjectPicked: (Boolean) -> Unit = {},
    val onThemePalettePicked: (com.twinlore.app.data.ThemePalette) -> Unit = {},
    val onHandleClockBatteryPicked: (Boolean) -> Unit = {},
    val onAppIconPicked: (com.twinlore.app.data.AppIcon) -> Unit = {},
    val onAppFontPicked: (com.twinlore.app.data.AppFont) -> Unit = {},
    val onSmartFormattingPicked: (Boolean) -> Unit = {},
    val onRefreshAwards: () -> Unit = {},
    val onCycleAwardTypeFilter: () -> Unit = {},
    val onToggleAwardsLockedOnly: () -> Unit = {},
    val onAwardSearchChanged: (String) -> Unit = {},
    val onCycleAwardSort: () -> Unit = {},
    val onToggleAwardSortDirection: () -> Unit = {},
    val onShowComments: (com.twinlore.app.data.retroachievements.RaAchievementDisplay) -> Unit = {},
    val onDismissComments: () -> Unit = {},
    val onAwardScrollChanged: (Int, Int) -> Unit = { _, _ -> },
    val onTakeScreenshot: () -> Unit = {},
    /** The bin: raises the question. The next two answer it. */
    val onDeleteImage: () -> Unit = {},
    val onConfirmDeleteImage: () -> Unit = {},
    val onDismissDeleteImage: () -> Unit = {},
    val onImageViewChanged: (FoldImageView) -> Unit = {},
    val onPreviousImage: () -> Unit = {},
    val onNextImage: () -> Unit = {},
    val onExternalLink: (String) -> Unit = {},
    val onDismissBlockedLink: () -> Unit = {},
    val onWantsKeyboard: (Boolean) -> Unit = {},
    /** Focus with no keyboard: a text selection in the reader. */
    val onWantsFocus: (Boolean) -> Unit = {},
    /** The software keyboard came up over the panel (true) or went away (false). */
    val onKeyboardVisible: (Boolean) -> Unit = {},
)

/**
 * The panel: the app's reader or the vault's images, with a bar underneath.
 *
 * The split is deliberate and is the whole shape of this feature. Everything
 * about the guide is the reader's, drawn by the app's own viewer with the app's
 * own chrome. Everything about the fold is the bar's. Nothing in between.
 */
@Composable
fun FoldGuidePanelScreen(
    state: FoldGuideUiState,
    actions: FoldGuideActions,
    modifier: Modifier = Modifier,
    /** The app's Pixel font is on, for the top bar's meters. */
    pixelFont: Boolean = false,
) {
    // A Surface, not a Column with a background colour.
    //
    // The colour is the smaller half of it: what a Surface also does is provide
    // LocalContentColor, and every screen the panel hosts leans on that. An app
    // screen inherits it from the vault session's own Surface; the panel had no
    // equivalent, so Material's default of black applied, and on this app's dark
    // palettes the contents list, the search results and their match highlights
    // were all drawn black on black. Seen on the Thor: legible only by the
    // focus ring around the row the D-pad happened to be on.
    // The panel grows out of the handle.
    //
    // The handle stays on screen and the panel opens over it, so without this
    // the page simply replaces the screen and the two read as unrelated
    // things. Scaling up from the handle's own corner says where this came
    // from and, on the way back, where tapping the handle will put it.
    //
    // Opening only. Closing is a window teardown, and holding the window up
    // for the length of an animation would put a frame of delay between the
    // tap and the game showing through, which is the one thing folding is for.
    val opening = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) {
        opening.animateTo(
            targetValue = 1f,
            animationSpec = androidx.compose.animation.core.tween(durationMillis = 180),
        )
    }
    Surface(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                // From most of the way out rather than from nothing: a panel
                // that starts at zero reads as a pop, and the reader's first
                // frame is text, which is unpleasant to watch scale far.
                val grow = 0.92f + 0.08f * opening.value
                scaleX = grow
                scaleY = grow
                alpha = opening.value
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(
                    pivotFractionX = if (state.handleOnRight) 1f else 0f,
                    pivotFractionY = state.handleYFraction,
                )
            },
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
    // Bars over the content, not beside it.
    //
    // A Column gave the bars their own rows, so showing them shrank the content
    // and everything inside relaid out. On the guide that is what you want:
    // text must not sit under chrome. On a picture it is not - tapping to bring
    // the bars back re-fitted the image and moved what you were looking at,
    // which is the opposite of what a tap should do to a photo.
    //
    // So the content always fills the panel and the bars are drawn on top of
    // it; what changes per surface is whether the content is padded clear of
    // the top bar. The bar reports its own height, so the padding is whatever
    // it actually measured and collapses to nothing when it is hidden.
    //
    // Only the top bar, though. The bottom bar is a centred pill a few entries
    // wide, and padding the surface clear of it took a full-width strip off
    // the bottom of the panel for a bar that only ever covers the middle of
    // it. The app's own vault session floats its bottom bar over edge-to-edge
    // content for the same reason, so the panel does the same: every surface
    // runs to the bottom edge and the pill sits over it, the way it already
    // did on the picture.
    //
    // The padding is each surface's own, not the box's. It was on the one box
    // all four share, keyed on which surface was active, so leaving the image
    // padded the image in the same frame, while the dissolve still had it
    // fully opaque for the next 220ms. Recorded on the Thor: the map jumped
    // up and shrank, the list faded in over it, then it went. A surface
    // that is padded the same whether or not it is active never moves on a
    // switch, and the dissolve is the only thing that happens.
    val density = LocalDensity.current
    var topBarHeight by remember { mutableStateOf(0.dp) }
    // Still measured, for the picture's shutter to sit above it.
    var bottomBarHeight by remember { mutableStateOf(0.dp) }
    // A search field's keyboard pushes the panel's window up from below, and
    // the bar went up with it, sitting on top of the keys. It goes while the
    // keyboard is up, the way the app's own bottom bar does.
    val keyboardVisible by com.twinlore.app.ui.utils.rememberKeyboardVisible()
    // The handle sits above the panel in its own window and lands on the keys
    // just as easily, so it is told too, and steps aside while typing.
    androidx.compose.runtime.LaunchedEffect(keyboardVisible) {
        actions.onKeyboardVisible(keyboardVisible)
    }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { actions.onKeyboardVisible(false) }
    }
    // The bar is a centred pill, not a full-width slab, so its right end is
    // nowhere near the screen's. Both are measured so the shutter can sit
    // just above the bar and line up with where it actually stops.
    var panelWidth by remember { mutableStateOf(0.dp) }
    var bottomBarWidth by remember { mutableStateOf(0.dp) }
    val belowTopBar = Modifier.padding(top = topBarHeight)
    Box(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {
        // Every surface stays composed, and only the active one is drawn.
        //
        // A `when` here disposed whichever surface you left, which for the
        // guide meant destroying its WebView and reloading the page on the way
        // back: the panel is a reader, and losing your place because you
        // glanced at a map is the one thing a reader must not do. This is the
        // app's own FocusableTabContainer, doing for these four surfaces what
        // it already does for the vault session's seven tabs, so both places
        // keep their tabs alive by the same mechanism.
        Box(modifier = Modifier.fillMaxSize()) {
            // Tucked a little under the bar. Its buttons are 48dp around 24dp
            // icons and the reader's own top bar leaves room above its icons
            // too, so flush, the two rows of icons stood a band of nothing
            // apart. What is tucked under is only that slack, never a control.
            FocusableTabContainer(
                isActive = state.surface == FoldPanelSurface.GUIDE,
                modifier = Modifier.padding(top = (topBarHeight - GUIDE_TUCK_UNDER_BAR).coerceAtLeast(0.dp)),
            ) {
                FoldGuideContent(
                    guide = state.guide,
                    loading = state.loading,
                    onOpenApp = actions.onOpenApp,
                    // A tap on the page hides the reader's chrome; the bar goes
                    // with it, so one tap clears everything off the guide. The
                    // reader reports the state it landed in, never a flip, so
                    // the two cannot drift out of phase.
                    onSetChrome = actions.onSetBarVisible,
                    // And it starts where that bar already is, so the two
                    // agree before anything is tapped rather than after.
                    chromeVisible = !state.barCollapsed,
                    onWantsKeyboard = actions.onWantsKeyboard,
                    onWantsFocus = actions.onWantsFocus,
                    onExternalLink = actions.onExternalLink,
                    textZoom = state.textZoom,
                    // The bar floats over the page, so the history strip is
                    // lifted clear of it and whatever banner rides on it.
                    chromeBelow = bottomBarHeight,
                )
            }

            // Under the top bar too, not below it: the picture is the whole
            // point here, and both bars float over it. See [FoldImageContent].
            FocusableTabContainer(isActive = state.surface == FoldPanelSurface.IMAGE) {
                FoldImageContent(
                    images = state.images,
                    index = state.imageIndex,
                    vaultId = state.vaultId,
                    bottomBarHeight = bottomBarHeight,
                    // Line the shutter up with the end of the bar rather than
                    // the end of the screen: the bar stops well short of it.
                    shutterEndPadding = ((panelWidth - bottomBarWidth) / 2)
                        .coerceAtLeast(16.dp),
                    // The bar and the chevrons come and go together: on this
                    // surface the picture is the whole point, and a tap that
                    // cleared only half the chrome off it would be worse than
                    // one that cleared none.
                    chromeVisible = !state.barCollapsed,
                    capturing = state.capturing,
                    view = state.imageView,
                    onPrevious = actions.onPreviousImage,
                    onNext = actions.onNextImage,
                    onToggleChrome = actions.onToggleBar,
                    onOpenApp = actions.onOpenApp,
                    onTakeScreenshot = actions.onTakeScreenshot,
                    onDeleteImage = actions.onDeleteImage,
                    onViewChanged = actions.onImageViewChanged,
                )
            }

            FocusableTabContainer(
                isActive = state.surface == FoldPanelSurface.AWARDS,
                modifier = belowTopBar.testTag(FOLD_SURFACE_AWARDS_TAG),
            ) {
                FoldAchievementsContent(
                    awards = state.awards,
                    loading = state.awardsLoading,
                    refreshing = state.awardsRefreshing,
                    typeFilter = state.awardTypeFilter,
                    lockedOnly = state.awardsLockedOnly,
                    searchQuery = state.awardSearch,
                    onSearchChanged = actions.onAwardSearchChanged,
                    onWantsKeyboard = actions.onWantsKeyboard,
                    sortField = state.awardSortField,
                    sortDirection = state.awardSortDirection,
                    steamSortField = state.steamAwardSortField,
                    steamSortDirection = state.steamAwardSortDirection,
                    commentsFor = state.commentsFor,
                    comments = state.comments,
                    commentsLoading = state.commentsLoading,
                    onRefresh = actions.onRefreshAwards,
                    onCycleTypeFilter = actions.onCycleAwardTypeFilter,
                    onToggleLockedOnly = actions.onToggleAwardsLockedOnly,
                    onCycleSort = actions.onCycleAwardSort,
                    onToggleSortDirection = actions.onToggleAwardSortDirection,
                    onShowComments = actions.onShowComments,
                    onDismissComments = actions.onDismissComments,
                    // A link in a comment goes where a link in a guide goes:
                    // the banner that says this window cannot follow it.
                    onBlockedLink = actions.onExternalLink,
                    onToggleChrome = actions.onToggleBar,
                    onOpenApp = actions.onOpenApp,
                    scroll = state.awardScroll,
                    onScrollChanged = actions.onAwardScrollChanged,
                )
            }

            // A surface of its own, kept composed like the rest. It was a
            // layer over the page, which on this window meant behind it: the
            // reader is a WebView and Compose drawn after one does not
            // reliably sit on top of it.
            FocusableTabContainer(isActive = state.surface == FoldPanelSurface.SETTINGS, modifier = belowTopBar) {
                FoldSettingsContent(
                    handleSizeDp = state.handleSizeDp,
                    handleAlpha = state.handleAlpha,
                    handleRecess = state.handleRecess,
                    handleClockBattery = state.handleClockBattery,
                    appIcon = state.appIcon,
                    selectableIcons = state.selectableIcons,
                    appFont = state.appFont,
                    onAppIconPicked = actions.onAppIconPicked,
                    onAppFontPicked = actions.onAppFontPicked,
                    textZoom = state.textZoom,
                    themeInject = state.themeInject,
                    themePalette = state.themePalette,
                    smartFormatting = state.smartFormatting,
                    onHandleSizePicked = actions.onHandleSizePicked,
                    onHandleAlphaPicked = actions.onHandleAlphaPicked,
                    onHandleRecessPicked = actions.onHandleRecessPicked,
                    onHandleStyleCommitted = actions.onHandleStyleCommitted,
                    onHandleSizePreviewing = actions.onHandleSizePreviewing,
                    onOpenAppSettings = actions.onOpenAppSettings,
                    onTextZoomPicked = actions.onTextZoomPicked,
                    onThemeInjectPicked = actions.onThemeInjectPicked,
                    onThemePalettePicked = actions.onThemePalettePicked,
                    onToggleChrome = actions.onToggleBar,
                    onHandleClockBatteryPicked = actions.onHandleClockBatteryPicked,
                    onSmartFormattingPicked = actions.onSmartFormattingPicked,
                )
            }
        }
        }

        // The panel's own chrome, over the reader's. Hidden with everything
        // else when the bar is collapsed, so a tap on the page still clears
        // the screen down to the guide - unless it is pinned, which is the
        // whole of what the pin means and the reason it is on this bar.
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .onSizeChanged { topBarHeight = with(density) { it.height.toDp() } },
        ) {
        if (!state.barCollapsed || state.pinned) {
            FoldGuideTopBar(
                title = state.barTitle,
                pinned = state.pinned,
                onTogglePin = actions.onTogglePin,
                statusBarMode = state.statusBarMode,
                onTapStatus = actions.onTapStatus,
                onOpenApp = actions.onOpenApp,
                metrics = state.metrics,
                pixelFont = pixelFont,
                // Absent rather than inert on a one-screen device, the rule
                // the bottom bar's Swap already follows: a control that can
                // never do anything is worse than one that is not offered.
                onOpenAppOnOtherScreen =
                    if (state.swapAvailable) actions.onOpenAppOnOtherScreen else null,
            )
        }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .onSizeChanged {
                    bottomBarHeight = with(density) { it.height.toDp() }
                    panelWidth = with(density) { it.width.toDp() }
                },
        ) {
        // Above the bar, below the content: a link that was refused is about
        // the thing being read, and the answer to it is the App button one row
        // further down.
        // Above the blocked-link banner, in the same slot and for the same
        // reason: it is about something that just happened, and the answer to
        // it is a button rather than the page.
        state.detectedGame?.let { game ->
            FoldDetectedGameBanner(
                game = game,
                supportEmblem = state.supportEmblem,
                onSwitch = actions.onSwitchDetectedGame,
                onDismiss = actions.onDismissDetectedGame,
                onSupport = actions.onSupportFromPanel,
            )
        }
        state.blockedLink?.let { link ->
            FoldBlockedLinkBanner(
                link = link,
                onDismiss = actions.onDismissBlockedLink,
            )
        }
        if (!keyboardVisible) FoldGuideBottomBar(
            // The inset goes on the bar, not around the whole panel. Padding
            // the column left the bar floating with a strip of page below it;
            // padding the bar lets its own colour run to the bottom edge and
            // keeps only the labels clear of the gesture bar.
            bottomInset = systemBottomInset(),
            surface = state.surface,
            collapsed = state.barCollapsed,
            swapAvailable = state.swapAvailable,
            onSelectSurface = actions.onSelectSurface,
            onToggleBar = actions.onToggleBar,
            onSwap = actions.onSwap,
            onReturnToLibrary = actions.onReturnToLibrary,
            onBarWidth = { bottomBarWidth = it },
        )
        }

        // Over the surface and its bars: the bin's question, which must be
        // answered before the picture goes anywhere.
        if (state.deletePrompt) {
            FoldDeleteImagePrompt(
                imageTitle = state.currentImage?.title.orEmpty(),
                onConfirm = actions.onConfirmDeleteImage,
                onDismiss = actions.onDismissDeleteImage,
            )
        }

        // Over everything, bars included, the first time only.
        if (!state.introSeen) {
            FoldPanelIntro(onDismiss = actions.onDismissIntro)
        }

        // Over even that: a held handle is asking to close, and an answer to
        // the intro is not an answer to this.
        if (state.closePrompt) {
            FoldCloseAppPrompt(
                onOpenApp = actions.onLeaveOverlayForApp,
                onConfirm = actions.onCloseApp,
                onDismiss = actions.onDismissCloseApp,
                onSwap = if (state.swapAvailable) actions.onSwapFromHold else null,
            )
        }
    }
    }
}

/** The achievements surface's box, so a test can hold its edges to the panel's. */
internal const val FOLD_SURFACE_AWARDS_TAG = "fold_surface_awards"

/** How far the guide reader sits under the panel's top bar; see its use. */
private val GUIDE_TUCK_UNDER_BAR = 8.dp
