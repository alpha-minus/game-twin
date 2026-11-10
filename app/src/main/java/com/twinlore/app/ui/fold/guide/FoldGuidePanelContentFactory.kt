package com.twinlore.app.ui.fold.guide

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.twinlore.app.data.AppFont
import com.twinlore.app.data.SettingsRepository
import com.twinlore.app.data.VaultRepository
import com.twinlore.app.data.effectiveAccentColor
import com.twinlore.app.thor.led.ThorLedManager
import com.twinlore.app.ui.LocalCachedFocusColor
import com.twinlore.app.ui.LocalThemePalette
import com.twinlore.app.ui.ThemedContent
import com.twinlore.app.ui.theme.ensureReadableAccent
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import com.twinlore.app.ui.fold.AndroidFoldPlatform
import com.twinlore.app.ui.fold.FoldController
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * What the window host is handed, so nothing in the fold's Android half has to
 * import Compose.
 */
interface FoldGuidePanelContentFactory {
    /** Built fresh each time the panel opens. */
    fun content(): @Composable () -> Unit

    /** The window came down for good, as opposed to being rebuilt. */
    fun onPanelClosed()

    /** A parked panel is showing again, with its composition intact. */
    fun onPanelResumed()

    /**
     * Where the handle is sitting, so the panel can open out of it.
     *
     * The handle stays on screen while the panel is open and floats above it,
     * which is what makes folding always reachable. Nothing is reserved for it:
     * it shrinks instead, because it can be parked anywhere and a strip down
     * one side would cost every screen something to avoid an overlap that is
     * small and in a corner the user chose.
     */
    fun onHandleGeometry(onRight: Boolean, yFraction: Float)

    /**
     * The handle was held: put the close question on the panel. [openedPanel]
     * says the hold is what brought the panel up, so a Swap answered from the
     * question folds it again on the other screen.
     */
    fun onCloseRequested(openedPanel: Boolean = false)
}

/**
 * The panel's composition root, and the only place that knows about both the
 * state and the machinery.
 *
 * Everything it reaches for is app-scoped, never the activity. The activity is
 * backgrounded whenever the panel exists, and next to a running emulator its
 * process may have been reaped outright.
 */
@Singleton
class AndroidFoldGuidePanelContentFactory @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val vaultRepository: VaultRepository,
    private val presenter: FoldGuidePanelPresenter,
    // Providers, because the platform holds this factory and this factory
    // drives the platform. The same cycle-breaking the platform already uses
    // for the controller.
    private val platform: Provider<AndroidFoldPlatform>,
    private val controller: Provider<FoldController>,
) : FoldGuidePanelContentFactory {

    override fun content(): @Composable () -> Unit {
        // Read once, when the panel opens rather than on every recomposition:
        // the panel is built from scratch each time it is shown, so this is
        // exactly as fresh as the window it belongs to.
        presenter.setSwapAvailable(platform.get().hasAnotherDisplay())
        presenter.onPanelOpened()

        return {
            val palette by settingsRepository.themePalette.collectAsState(initial = null)
            val font by settingsRepository.appFont.collectAsState(initial = AppFont.DEFAULT)
            val state by presenter.state.collectAsState()
            // The display the panel's own window is on, read fresh: on a
            // two-screen device a screenshot of "the screen" has no single
            // right answer, and the panel is the thing that knows which one
            // the user is looking at.
            val panelView = androidx.compose.ui.platform.LocalView.current
            val captureDisplayId =
                panelView.display?.displayId ?: android.view.Display.DEFAULT_DISPLAY

            // The app's adaptive accent, provided the way MainActivity provides
            // it. Without it every Navigable control in the panel fell back to
            // the chosen preset, so focusing the guide's search field turned
            // the rings from the vault's colour to the preset's.
            val accent = remember { mutableStateOf<Color?>(null) }
            LaunchedEffect(Unit) {
                combine(
                    settingsRepository.accentColors,
                    settingsRepository.autoFocusColorDetection,
                ) { (fc, rawArgb), autoDetect ->
                    effectiveAccentColor(fc, rawArgb, autoDetect)
                }.collect { accent.value = it }
            }
            CompositionLocalProvider(LocalCachedFocusColor provides accent) {
                // The app's own theme, from the app's own settings. The panel drew
                // its own approximation once and it never quite matched.
                ThemedContent(paletteSelection = palette, appFont = font) {
                    OverlayLedSync(vaultId = state.vaultId)
                    FoldGuidePanelScreen(
                        state = state,
                        pixelFont = font == AppFont.PIXEL,
                        actions = FoldGuideActions(
                            onOpenApp = {
                                presenter.openAppOnCurrentGuide {
                                    controller.get().unfold()
                                }
                            },
                            // The hold. Same hand-over as Return, aimed at the
                            // screen the app is not normally returned to.
                            onOpenAppOnOtherScreen = {
                                presenter.openAppOnCurrentGuide {
                                    controller.get().unfoldToOtherScreen()
                                }
                            },
                            onSelectSurface = presenter::selectSurface,
                            // Size and visibility go through the platform: it owns
                            // the live handle, and applying them to it is how you
                            // see what you picked. Applied per frame of a drag and
                            // written once on release, because the apply is a
                            // window update and the write is DataStore.
                            onHandleSizePicked = { platform.get().resizeTab(it) },
                            onHandleAlphaPicked = { platform.get().applyAlpha(it) },
                            onHandleRecessPicked = { platform.get().applyRecess(it) },
                            onHandleStyleCommitted = { platform.get().persistHandleStyle() },
                            onHandleSizePreviewing = { platform.get().setSizePreviewing(it) },
                            // The rest of the settings are in the app, and the app
                            // cannot be shown over a game: this leaves the mode
                            // the same way Library does, and says so on the row.
                            onOpenAppSettings = {
                                presenter.openAppOnSettings {
                                    controller.get().unfold()
                                }
                            },
                            onTapStatus = presenter::tapToggleStatusBarMode,
                            onDismissIntro = presenter::dismissIntro,
                            onCloseApp = { controller.get().closeApp() },
                            // The same unfold the handle's hold used to do on its
                            // own, now that the hold asks first.
                            onLeaveOverlayForApp = {
                                presenter.dismissCloseApp()
                                controller.get().unfold()
                            },
                            // The bar's Library entry: out of the mode and onto
                            // the library, rather than back to the game the panel
                            // was open on.
                            onReturnToLibrary = {
                                presenter.openAppOnLibrary {
                                    controller.get().unfold()
                                }
                            },
                            onDismissCloseApp = presenter::dismissCloseApp,
                            onSwapFromHold = {
                                presenter.swapFromCloseApp(
                                    swap = { controller.get().swapGuidePanelDisplay() },
                                    fold = { controller.get().foldGuidePanel() },
                                )
                            },
                            onSwitchDetectedGame = presenter::switchToDetectedGame,
                            onDismissDetectedGame = presenter::dismissDetectedGame,
                            // Hand over the way Return does, with a note that the
                            // app reads on arrival and answers with the dialog.
                            onSupportFromPanel = {
                                presenter.armSupportRequest()
                                presenter.openAppOnCurrentGuide { controller.get().unfold() }
                            },
                            onTextZoomPicked = presenter::setTextZoom,
                            onThemeInjectPicked = presenter::setThemeInject,
                            onThemePalettePicked = presenter::setThemePalette,
                            onHandleClockBatteryPicked = presenter::setHandleClockBattery,
                            onAppIconPicked = presenter::setAppIcon,
                            onAppFontPicked = presenter::setAppFont,
                            onSmartFormattingPicked = presenter::setSmartFormatting,
                            onPreviousImage = { presenter.stepImage(-1) },
                            onNextImage = { presenter.stepImage(1) },
                            onExternalLink = presenter::onExternalLink,
                            onDismissBlockedLink = presenter::dismissBlockedLink,
                            // Swap moves the fold, panel and handle together, to
                            // the other screen. It does not bring the app back:
                            // reading beside a game on the other display is the
                            // thing being asked for, not a way out of the panel.
                            onSwap = { controller.get().swapGuidePanelDisplay() },
                            onToggleBar = { presenter.setBarCollapsed(!state.barCollapsed) },
                            onSetBarVisible = { visible -> presenter.setBarCollapsed(!visible) },
                            onTogglePin = { presenter.setPinned(!state.pinned) },
                            onRefreshAwards = { presenter.refreshAwards() },
                            onCycleAwardTypeFilter = { presenter.cycleAwardTypeFilter() },
                            onToggleAwardsLockedOnly = { presenter.toggleAwardsLockedOnly() },
                            onAwardSearchChanged = presenter::setAwardSearch,
                            onCycleAwardSort = { presenter.cycleAwardSort() },
                            onToggleAwardSortDirection = { presenter.toggleAwardSortDirection() },
                            onShowComments = { presenter.showComments(it) },
                            onDismissComments = { presenter.dismissComments() },
                            onAwardScrollChanged = { i, o -> presenter.setAwardScroll(i, o) },
                            onTakeScreenshot = { presenter.takeScreenshot(captureDisplayId) },
                            onDeleteImage = presenter::askDeleteImage,
                            onConfirmDeleteImage = presenter::confirmDeleteImage,
                            onDismissDeleteImage = presenter::dismissDeleteImage,
                            onImageViewChanged = { presenter.onImageViewChanged(it) },
                            onWantsKeyboard = { wants -> platform.get().setGuidePanelFocusable(wants) },
                            onWantsFocus = { wants -> platform.get().setGuidePanelFocusableWithoutKeyboard(wants) },
                            onKeyboardVisible = { up -> platform.get().setHandleHiddenForKeyboard(up) },
                        ),
                    )
                }
            }
        }
    }

    /**
     * The rings follow the panel the way they follow the app.
     *
     * The app lets go of the LEDs when it goes to the back, so while it sits
     * behind the overlay nothing drives them: a game may have changed them,
     * or the inactivity timer put them out. The panel coming up is the app's
     * wake-up here, so it puts the vault's colour back, the way returning to
     * the app does. A switch to a newly detected game is an open of that
     * vault, so it breathes into the new vault's colour, the way focusing it
     * in the library does.
     *
     * The device waking is a wake-up too. The app's own screen-on receiver
     * only relights the rings while its activity is resumed, and behind an
     * open panel it never is, so a device put to sleep on the panel came back
     * with whatever the rings were left at. The panel listens for the same
     * broadcast and, if it is the thing on screen, puts the colour back after
     * the same settle the app gives the hardware.
     */
    @Composable
    private fun OverlayLedSync(vaultId: String?) {
        val panelOpen by controller.get().isGuidePanelOpen.collectAsState()
        val isDark = LocalThemePalette.current.isDark
        val lastVault = remember { mutableStateOf<String?>(null) }
        val wakes = remember { mutableStateOf(0) }
        val seenWakes = remember { mutableStateOf(0) }
        val context = LocalContext.current
        DisposableEffect(context) {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context?, intent: Intent?) {
                    if (intent?.action == Intent.ACTION_SCREEN_ON) wakes.value++
                }
            }
            val registered = runCatching {
                context.registerReceiver(
                    receiver,
                    IntentFilter(Intent.ACTION_SCREEN_ON),
                    Context.RECEIVER_NOT_EXPORTED,
                )
            }.isSuccess
            onDispose { if (registered) runCatching { context.unregisterReceiver(receiver) } }
        }
        LaunchedEffect(panelOpen, vaultId, isDark, wakes.value) {
            if (!panelOpen) return@LaunchedEffect
            // Only a wake waits: the rings are relit after the same settle the
            // app's own receiver gives the hardware. A colour change is not a
            // wake, and neither is a panel reopening after one.
            val woke = wakes.value != seenWakes.value
            seenWakes.value = wakes.value
            if (woke) delay(SCREEN_ON_LED_SETTLE_MS)
            if (!settingsRepository.ledEnabled.first()) return@LaunchedEffect
            val color = vaultAccent(vaultId, isDark)
            val brightness = settingsRepository.ledBrightness.first()
            val saturationBoost = settingsRepository.ledSaturationBoost.first()
            val switched = lastVault.value != null && lastVault.value != vaultId
            lastVault.value = vaultId
            // No inactivity timer: touches in the panel never reach the
            // activity that resets it, so it would put the rings out mid-read.
            if (switched) {
                ThorLedManager.onFocusGained(color, true, brightness, saturationBoost)
            } else {
                ThorLedManager.setColor(color, true, brightness, saturationBoost)
            }
        }
    }

    /**
     * The colour the app would give [vaultId]: its artwork's accent when
     * adaptive colour is on, readable on the theme, else the chosen colour.
     * Persisted the way the vault session does, so the app comes back on the
     * vault the panel followed in the same colour the rings already show.
     */
    private suspend fun vaultAccent(vaultId: String?, isDark: Boolean): Color {
        val chosen = settingsRepository.focusColor.first().color
        if (!settingsRepository.autoFocusColorDetection.first()) return chosen
        val argb = vaultId?.let { vaultRepository.getVaultById(it)?.artworkVibrantArgb }
            ?: return settingsRepository.rawAccentColorArgb.first()?.let { Color(it) } ?: chosen
        val readable = Color(argb).ensureReadableAccent(isDark)
        settingsRepository.setRawAccentColorArgb(readable.toArgb())
        return readable
    }

    override fun onPanelClosed() {
        presenter.onPanelClosed()
    }

    private companion object {
        /** MainActivity's screen-on receiver waits this long before relighting; so does the panel. */
        const val SCREEN_ON_LED_SETTLE_MS = 800L
    }

    override fun onPanelResumed() {
        presenter.setSwapAvailable(platform.get().hasAnotherDisplay())
        presenter.onPanelResumed()
    }

    override fun onHandleGeometry(onRight: Boolean, yFraction: Float) {
        presenter.setHandleGeometry(onRight, yFraction)
    }

    override fun onCloseRequested(openedPanel: Boolean) {
        presenter.askCloseApp(openedPanel)
    }
}
