package com.twinlore.app.ui.fold

import android.app.Activity
import android.app.ActivityOptions
import android.content.Context
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.annotation.VisibleForTesting
import com.twinlore.app.MainActivity
import com.twinlore.app.data.HapticFeedbackIntensity
import com.twinlore.app.data.NetworkConnectivityManager
import com.twinlore.app.data.SettingsRepository
import com.twinlore.app.data.SplashIconCache
import com.twinlore.app.data.effectiveAccentColor
import com.twinlore.app.data.effectiveAppIconFlow
import com.twinlore.app.data.fold.FoldHandleLook
import com.twinlore.app.data.fold.FoldHandleStyle
import com.twinlore.app.data.fold.FoldTabGeometry
import com.twinlore.app.di.ApplicationScope
import com.twinlore.app.ui.fold.guide.FoldGuidePanelContentFactory
import com.twinlore.app.ui.fold.guide.FoldGuidePanelHost
import dagger.hilt.android.qualifiers.ApplicationContext
import com.twinlore.app.thor.led.ThorLedManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * The Android half of folding. No decisions live here; [FoldController] owns
 * those. This only knows how to put a window on a particular display, take it
 * down, and move the task around.
 */
@Singleton
class AndroidFoldPlatform @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val appScope: CoroutineScope,
    private val settingsRepository: SettingsRepository,
    private val splashIconCache: SplashIconCache,
    private val licenseManager: com.twinlore.app.data.license.LicenseManagerUiStateContract,
    private val guidePanelHost: FoldGuidePanelHost,
    // Providers, not the objects themselves: the controller depends on this
    // class, and the panel's content factory drives it. Asking for both lazily
    // is what keeps those cycles resolvable.
    private val guidePanelContentFactory: Provider<FoldGuidePanelContentFactory>,
    private val foldController: Provider<FoldController>,
    private val connectivity: NetworkConnectivityManager,
    private val appIconManager: com.twinlore.app.data.AppIconManager,
) : FoldPlatform {

    /**
     * moveTaskToBack is the one call that genuinely needs an activity. Held
     * weakly so a destroyed activity cannot be leaked by the singleton.
     *
     * Deliberately not cleared in onPause: on a device where an activity on a
     * secondary display can be visible and interactive while PAUSED (the Thor
     * is exactly that class of device), clearing it there would make
     * [moveAppToBack] return false and refuse the whole feature. Liveness is
     * what this reference tracks; whether the activity is resumed is a separate
     * question, answered by [activityResumed].
     */
    var currentActivity: Activity?
        get() = activityRef?.get()
        set(value) {
            activityRef = value?.let { WeakReference(it) }
        }

    private var activityRef: WeakReference<Activity>? = null

    /**
     * True between onResume and onPause. Reported out of [bringAppToFront] so
     * the controller can tell "the app is coming back, wait for the resume"
     * from "the app never left, no resume is coming". No decision is made here.
     */
    var activityResumed: Boolean = false

    private var tabView: FoldTabView? = null

    @VisibleForTesting
    internal val shownTabView: FoldTabView? get() = tabView
    private var tabWindowManager: WindowManager? = null
    private var tabParams: WindowManager.LayoutParams? = null
    private var tabDisplayWidth = 0
    private var tabDisplayHeight = 0

    /**
     * What the tab is currently showing, mirrored here because a display swap
     * is a window rebuild and the rebuild has to be given everything [showTab]
     * was given the first time.
     *
     * [lastFractionX] and [lastFractionY] track the *current* position, not the
     * one the tab was first shown at: they are updated on every drag end
     * alongside the persistence, so a resize after a drag keeps the tab where
     * it now is rather than snapping it back.
     *
     * [tabSizePx] is read by the drag handlers on every move. It has to be the
     * field and not the local [showTab] computed, because [resizeTab] changes
     * it under a window that is never rebuilt: a captured local would go on
     * placing a resized handle with the old size's geometry.
     */
    @VisibleForTesting
    internal var tabSizePx = 0
        private set
    private var tabDisplayId = 0
    private var lastFractionX = 0f
    private var lastFractionY = 0f
    private var currentLook: FoldHandleLook = FoldHandleLook()

    /**
     * True while the size slider is being dragged.
     *
     * The handle is drawn small while the panel is up (see
     * [FoldTabView.recessed]), which would make a size slider lie about what
     * it is picking. While this is set the handle is drawn at true size
     * instead, so the thing being sized is the thing on screen.
     */
    private var sizePreviewing = false

    /**
     * The themed launcher icon, mirrored here so [showTab] can read it without
     * suspending. Two separate staleness windows land on this field, and each
     * is handled differently:
     *
     * - Startup: [effectiveAppIconFlow] combines [SettingsRepository.appIcon],
     *   a cold flow over DataStore with no synchronous initial value, so it
     *   cannot emit until DataStore's first disk read completes. Seeding this
     *   field at the hardcoded default and waiting on the flow would leave it
     *   wrong for however long that read takes, and a fold landing in that
     *   window would show the wrong mark. [SplashIconCache] already exists to
     *   solve exactly this problem for the startup splash (a
     *   SharedPreferences-backed synchronous mirror of the same
     *   effective-icon computation, kept warm by [MainActivity]'s writes), so
     *   this field is seeded from it below instead of from the default.
     * - A theme change while already folded: the panel's own settings pick
     *   the icon, so the flow collection in [init] pushes it onto the handle
     *   that is up ([applyIcon]). It's also what corrects the seed above if
     *   the cache and DataStore ever disagree (e.g. a fresh install with no
     *   cache entry yet).
     */
    @VisibleForTesting
    @Volatile
    internal var themedIconRes: Int = splashIconCache.read().launcherMipmap

    /** The same icon's accent, for the battery ring. Mirrored for the same reasons. */
    @Volatile
    private var themedAccent: Int = splashIconCache.read().handleAccent.toInt()

    /**
     * The effective icon itself, for the launcher. An icon picked from the
     * panel changes the handle at once but not the launcher: flipping the
     * launcher alias takes the app's task down with it, which over a running
     * game would be the app closing on the user. [closeApp] flips it instead,
     * on the way out, when the task is going anyway.
     */
    @Volatile
    private var themedIcon: com.twinlore.app.data.AppIcon = splashIconCache.read()

    /** Whether the app is set in the pixel font, for the handle's clock. */
    @Volatile
    private var pixelFont: Boolean = false

    /**
     * The user's haptic setting, mirrored here because the handle is a plain
     * view in an overlay window with no Compose locals to read it from. Seeded
     * at the stored default.
     */
    @Volatile
    private var hapticIntensity: HapticFeedbackIntensity = HapticFeedbackIntensity.MEDIUM

    /**
     * Whether the handle shows the clock and battery, mirrored for the same
     * reason. Seeded off, and corrected live when the store emits: a handle
     * that comes up a moment before the setting arrives shows its clock in
     * place rather than being rebuilt.
     */
    @Volatile
    private var clockAndBattery: Boolean = false

    init {
        appScope.launch {
            effectiveAppIconFlow(settingsRepository, licenseManager)
                .distinctUntilChanged()
                .collect { applyIcon(it) }
        }
        appScope.launch {
            settingsRepository.appFont.map { it == com.twinlore.app.data.AppFont.PIXEL }
                .distinctUntilChanged()
                .collect { applyPixelFont(it) }
        }
        appScope.launch {
            settingsRepository.hapticFeedbackIntensity.collect { hapticIntensity = it }
        }
        // Distinct, because the store re-emits every key on any write, and a
        // drag writes the position: without it every drag relaid the handle.
        appScope.launch {
            settingsRepository.foldHandleClockBattery.distinctUntilChanged()
                .collect { applyClockAndBattery(it) }
        }
    }

    override fun canDrawOverlays(): Boolean = Settings.canDrawOverlays(context)

    override fun showTab(
        displayId: Int,
        xFraction: Float,
        yFraction: Float,
        look: FoldHandleLook,
    ): Boolean {
        if (tabView != null) hideTab()

        tabDisplayId = displayId
        lastFractionX = xFraction
        lastFractionY = yFraction
        currentLook = FoldHandleLook.of(look.alpha, look.sizeDp, look.recessScale)

        val displayManager = context.getSystemService(DisplayManager::class.java)
        // No fallback to another display on purpose. A tab on a screen the
        // controller does not think it folded from is worse than no tab: the
        // caller can still refuse to background the app.
        val display = displayManager.getDisplay(displayId)
        if (display == null) {
            Log.w(TAG, "No display $displayId to put the tab on")
            return false
        }

        // A window context bound to the target display is what puts the tab on
        // the Thor's second screen. Without it the window lands on the default
        // display no matter which screen the app was showing on.
        val windowContext = context.createDisplayContext(display)
            .createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
        val wm = windowContext.getSystemService(WindowManager::class.java)

        val metrics = wm.currentWindowMetrics.bounds
        tabDisplayWidth = metrics.width()
        tabDisplayHeight = metrics.height()

        val density = windowContext.resources.displayMetrics.density
        tabDensity = density
        val sizePx = (currentLook.sizeDp * density).toInt()
        tabSizePx = sizePx

        val placement = tabPlacement()

        val params = WindowManager.LayoutParams(
            sizePx,
            sizePx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // Not focusable: a focusable overlay would take input away from the
            // game running underneath. NO_LIMITS lets the tab sit flush against
            // a display cutout edge.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            x = placement.x
            y = placement.y
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }

        val view = FoldTabView(windowContext, themedIconRes, themedAccent).apply {
            this.markSizePx = sizePx
            this.clockAndBattery = this@AndroidFoldPlatform.clockAndBattery
            this.pixelFont = this@AndroidFoldPlatform.pixelFont
            this.handleAlpha = currentLook.alpha
            this.recessScale = currentLook.recessScale
            this.hapticIntensity = this@AndroidFoldPlatform.hapticIntensity
            // Every callback goes to singletons, never to the activity. The
            // tab outlives the activity by design, so anything it held would
            // be a leak, and a dead activity's scope would drop the drag write.
            //
            // A tap opens the panel; a hold is the destination past it, the
            // whole app. Both are rules the controller owns.
            onTap = { foldController.get().onTabTapped() }
            // A hold closes the app, and closing asks first. The panel is
            // where the question can be drawn, so a hold with the panel down
            // opens it: this window cannot host a dialog of its own, and a
            // hold that silently did nothing while folded would read as a
            // gesture that had stopped working. Return is the way back to the
            // app now, in the panel's top corner.
            //
            // Whether the hold is what opened the panel goes along with the
            // question: the menu's Swap stays in the mode, and a swap asked
            // for from a folded handle should leave it folded on arrival.
            onHold = {
                val wasOpen = foldController.get().isGuidePanelOpen.value
                foldController.get().openGuidePanel()
                guidePanelContentFactory.get().onCloseRequested(openedPanel = !wasOpen)
            }
            // [tabSizePx], never the local above: a resize changes the size
            // under this same view, and a captured local would drag a resized
            // handle by the old size's geometry.
            onDragTo = { rawX, rawY -> moveTabTo(rawX, rawY) }
            onDragEnd = { rawX, rawY ->
                moveTabTo(rawX, rawY)
                val dragged = fractionFromTouch(rawX, rawY)
                // Beside the persistence, not instead of it: a resize is a
                // rebuild from these fields, and a rebuild after a drag has to
                // use where the tab now is.
                lastFractionX = dragged.x
                lastFractionY = dragged.y
                // Dragged while the panel is up, so the point the panel folds
                // back into has moved with it.
                if (guidePanelHost.isShowing) reportHandleGeometry()
                appScope.launch {
                    settingsRepository.setFoldPositionX(dragged.x)
                    settingsRepository.setFoldPositionY(dragged.y)
                }
            }
        }

        // A rebuild while the panel is up (a display swap) makes a new view,
        // and it has to arrive in the state the old one was in.
        view.recessed = guidePanelHost.isShowing && !sizePreviewing
        view.readoutHidden = guidePanelHost.isShowing
        try {
            wm.addView(view, params)
        } catch (e: WindowManager.BadTokenException) {
            Log.e(TAG, "Overlay window refused; is the permission still granted?", e)
            return false
        }
        tabView = view
        tabWindowManager = wm
        tabParams = params
        registerWakeReceiver()
        watchConnection(view)
        return true
    }

    /**
     * Relights the rings when the device wakes over a folded handle.
     *
     * The app's own screen-on receiver only acts while its activity is resumed,
     * and the open panel has its own; folded to the handle, neither is in
     * play, so a device put to sleep over a game came back with whatever the
     * rings were left at until the panel was opened. Registered with the
     * handle, so it lives exactly as long as overlay mode does.
     */
    private var wakeReceiver: BroadcastReceiver? = null

    private fun registerWakeReceiver() {
        if (wakeReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_SCREEN_ON) appScope.launch { relightAfterWake() }
            }
        }
        val registered = runCatching {
            context.registerReceiver(receiver, IntentFilter(Intent.ACTION_SCREEN_ON), Context.RECEIVER_NOT_EXPORTED)
        }.onFailure { Log.w(TAG, "Could not listen for the screen coming on", it) }.isSuccess
        if (registered) wakeReceiver = receiver
    }

    private fun unregisterWakeReceiver() {
        val receiver = wakeReceiver ?: return
        wakeReceiver = null
        runCatching { context.unregisterReceiver(receiver) }
    }

    private var connectionWatch: Job? = null

    /**
     * Shows a lost connection on [view] for as long as it is up.
     *
     * Followed through [NetworkConnectivityManager.status], which is a
     * default-network callback and so hears a loss as it happens. That alone
     * is not trusted over a game: the handle can sit there for hours, and a
     * callback missed while the process was frozen would leave it wrong until
     * something else happened to re-ask. So it is re-asked every
     * [CONNECTION_RECHECK_MS] while the mark could be seen.
     *
     * A loss is shown only once it has lasted [OFFLINE_GRACE_MS]. A re-ask
     * starts from activeNetwork, which is null for a backgrounded app whose
     * network the system holds blocked, and reports offline for the moment
     * before the fresh callback names the network. Without the grace every
     * re-ask behind a game would flash the mark. A connection coming back is
     * shown at once.
     */
    private fun watchConnection(view: FoldTabView) {
        connectionWatch?.cancel()
        connectionWatch = appScope.launch(Dispatchers.Main.immediate) {
            launch {
                while (true) {
                    delay(CONNECTION_RECHECK_MS)
                    // Only while the mark could be on screen: with the panel up
                    // its own bar shows the connection, and a re-ask's moment of
                    // offline would blink that bar's icon.
                    if (clockAndBattery && !guidePanelHost.isShowing) {
                        connectivity.recheck("overlay handle")
                    }
                }
            }
            connectivity.status
                .map { !it.hasInternet }
                .distinctUntilChanged()
                .collectLatest { lost ->
                    if (lost) delay(OFFLINE_GRACE_MS)
                    if (tabView === view) view.offline = lost
                }
        }
    }

    @VisibleForTesting
    internal suspend fun relightAfterWake() {
        // The same settle the app's receiver gives the hardware.
        delay(SCREEN_ON_LED_SETTLE_MS)
        val controller = foldController.get()
        // An open panel relights the rings itself, in its vault's colour.
        if (!controller.isFolded.value || controller.isGuidePanelOpen.value) return
        if (!settingsRepository.ledEnabled.first()) return
        val color = effectiveAccentColor(
            settingsRepository.focusColor.first(),
            settingsRepository.rawAccentColorArgb.first(),
            settingsRepository.autoFocusColorDetection.first(),
        )
        // No inactivity timer, as on the panel: nothing over a game resets it.
        ThorLedManager.setColor(
            color,
            true,
            settingsRepository.ledBrightness.first(),
            settingsRepository.ledSaturationBoost.first(),
        )
    }

    private fun moveTabTo(rawX: Float, rawY: Float) {
        val view = tabView ?: return
        val wm = tabWindowManager ?: return
        val params = tabParams ?: return
        val fraction = fractionFromTouch(rawX, rawY)
        val placement = tabPlacement(fraction.x, fraction.y)
        params.x = placement.x
        params.y = placement.y
        runCatching { wm.updateViewLayout(view, params) }
            .onFailure { Log.w(TAG, "Could not move the tab", it) }
    }

    private fun tabPlacement(
        xFraction: Float = lastFractionX,
        yFraction: Float = lastFractionY,
    ) = FoldTabGeometry.placement(
        xFraction = xFraction,
        yFraction = yFraction,
        displayWidthPx = tabDisplayWidth,
        displayHeightPx = tabDisplayHeight,
        tabWidthPx = tabSizePx,
        tabHeightPx = tabSizePx,
    )

    /** Where a finger on the handle puts it, taking the finger to be on the icon. */
    private fun fractionFromTouch(rawX: Float, rawY: Float) =
        FoldTabGeometry.fractionFromTouch(
            rawX, rawY, tabDisplayWidth, tabDisplayHeight, tabSizePx, tabSizePx,
        )

    /**
     * The clock and battery turned on or off, applied to the handle that is
     * already up. Drawn on the icon, so the window keeps its size and nothing
     * has to be laid out again.
     */
    @VisibleForTesting
    internal fun applyClockAndBattery(on: Boolean) {
        clockAndBattery = on
        val view = tabView ?: return
        // The store emits on appScope's threads, and only the main thread may
        // touch the handle: applying it there directly crashed on device.
        // Posted through the view, and dropped if the handle was rebuilt in
        // between: the new one reads [clockAndBattery] itself.
        if (Looper.myLooper() == Looper.getMainLooper()) {
            view.clockAndBattery = on
        } else {
            view.post { if (tabView === view) view.clockAndBattery = clockAndBattery }
        }
    }

    /**
     * A new effective icon, applied to the handle that is already up and
     * mirrored into the splash cache, so the next cold start's splash and
     * [closeApp]'s launcher flip agree with it.
     */
    @VisibleForTesting
    internal fun applyIcon(icon: com.twinlore.app.data.AppIcon) {
        themedIcon = icon
        themedIconRes = icon.launcherMipmap
        themedAccent = icon.handleAccent.toInt()
        splashIconCache.write(icon)
        val view = tabView ?: return
        // Main thread only, as with [applyClockAndBattery].
        if (Looper.myLooper() == Looper.getMainLooper()) {
            view.setIcon(themedIconRes, themedAccent)
        } else {
            view.post { if (tabView === view) view.setIcon(themedIconRes, themedAccent) }
        }
    }

    /** The app's font turned pixel or back, applied to the handle's clock. */
    @VisibleForTesting
    internal fun applyPixelFont(on: Boolean) {
        pixelFont = on
        val view = tabView ?: return
        if (Looper.myLooper() == Looper.getMainLooper()) {
            view.pixelFont = on
        } else {
            view.post { if (tabView === view) view.pixelFont = pixelFont }
        }
    }

    override fun hideTab() {
        // The panel goes first and unconditionally: a full screen reader over
        // a game with no handle left to close it is the exact state this
        // feature exists to prevent.
        hideGuidePanel()
        val view = tabView ?: return
        runCatching { tabWindowManager?.removeView(view) }
            .onFailure { Log.w(TAG, "Could not remove the tab", it) }
        tabView = null
        tabWindowManager = null
        tabParams = null
        unregisterWakeReceiver()
        connectionWatch?.cancel()
        connectionWatch = null
    }

    override fun moveAppToBack(): Boolean = currentActivity?.moveTaskToBack(true) ?: false

    override fun bringAppToFront(displayId: Int): BringToFrontResult {
        // Sampled before the relaunch: afterwards it says nothing useful.
        val wasAlreadyInFront = activityResumed
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        }
        val options = ActivityOptions.makeBasic().apply { launchDisplayId = displayId }
        // Reported rather than swallowed. A throw here means nothing at all
        // happened, and a caller that toasts "moved" on the strength of a call
        // that threw is telling the user something untrue.
        // Swallowed rather than surfaced: a relaunch that throws leaves the
        // tab up, and the tab is the way to try again.
        runCatching { context.startActivity(intent, options.toBundle()) }
            .onFailure { Log.e(TAG, "Could not bring the app back to display $displayId", it) }
            .isSuccess
        return BringToFrontResult(wasAlreadyInFront = wasAlreadyInFront)
    }

    override fun closeApp() {
        // The windows first. Killing the process takes them with it either
        // way, but a visible handle during the teardown reads as a close that
        // did not work.
        hideGuidePanel()
        hideTab()
        // finishAffinity then exit, the same pair the app's own close uses:
        // the task goes so the launcher does not reopen mid-teardown, and the
        // process goes because an overlay app that leaves one behind is one
        // the user cannot tell has closed.
        runCatching { currentActivity?.finishAffinity() }
        // An icon picked from the panel reaches the launcher here, where the
        // task is ending anyway. A no-op when it already matches.
        runCatching { appIconManager.applyLauncherIcon(themedIcon) }
            .onFailure { Log.w(TAG, "Could not apply the launcher icon", it) }
        Runtime.getRuntime().exit(0)
    }

    override fun showGuidePanel(): Boolean {
        // The tab's own window context, so the panel lands on the display the
        // handle is on rather than the default one. No handle, no panel: the
        // panel is opened by tapping the handle.
        val windowContext = tabView?.context ?: return false
        // A parked panel on this display comes straight back, reader and all.
        if (guidePanelHost.unpark(windowContext)) {
            raiseTabAbovePanel()
            tabView?.recessed = true
            tabView?.readoutHidden = true
            reportHandleGeometry()
            guidePanelContentFactory.get().onPanelResumed()
            return true
        }
        val shown = guidePanelHost.show(
            windowContext = windowContext,
            // Back with nothing to close inside the panel closes the panel.
            // Through the controller, not the host, because the controller owns
            // the open flag.
            onUnhandledBack = { foldController.get().foldGuidePanel() },
            content = guidePanelContentFactory.get().content(),
        )
        if (!shown) return false
        // The handle stays, and comes up above the panel.
        //
        // It is the only control in this mode that the panel's own chrome
        // cannot take away: the bars hide to give the page room, and folding is
        // the thing you reach for most, so the one affordance that is always
        // there has to be the one that folds. The panel used to hide the handle
        // and carry a Fold button in its top bar, which meant the most frequent
        // action vanished exactly when someone had cleared the chrome to read.
        //
        // Same window type, so the stacking is insertion order and the panel
        // went up second: re-adding the handle now puts it back on top. The
        // View instance is reused, so nothing about the handle is rebuilt.
        raiseTabAbovePanel()
        // Small while the panel is up: it only has to be tappable here, not
        // findable against a game. See [FoldTabView.recessed].
        tabView?.recessed = true
        tabView?.readoutHidden = true
        reportHandleGeometry()
        return true
    }

    /**
     * Tells the panel where the handle is, so the panel can open out of it.
     *
     * No room is reserved for it any more. The handle can be parked anywhere,
     * so its position said nothing useful about how much of the panel to keep
     * clear, and a strip down one whole side was a fixed cost for an overlap
     * that is small and in a corner the user picked. The handle shrinks
     * instead. What is left here is the pivot the opening animation grows
     * from.
     */
    private fun reportHandleGeometry() {
        guidePanelContentFactory.get()
            .onHandleGeometry(onRight = lastFractionX >= 0.5f, yFraction = lastFractionY)
    }

    /**
     * Puts the handle's window back on top of the panel's.
     *
     * Both are TYPE_APPLICATION_OVERLAY, where z-order follows the order the
     * windows were added, so the only way to get above a window added later is
     * to be added later still. Remove and re-add rather than rebuild: the same
     * [FoldTabView] goes back with the same params, so its position, size, dim
     * and drag callbacks all survive.
     */
    private fun raiseTabAbovePanel() {
        val view = tabView ?: return
        val wm = tabWindowManager ?: return
        val params = tabParams ?: return
        runCatching {
            wm.removeViewImmediate(view)
            wm.addView(view, params)
        }.onFailure {
            // A handle that could not be re-added is worse than one behind the
            // panel, so put it back the simple way and carry on.
            Log.w(TAG, "Could not raise the handle above the panel", it)
            runCatching { wm.addView(view, params) }
        }
    }

    override fun parkGuidePanel() {
        if (!guidePanelHost.isShowing) return
        if (!guidePanelHost.park()) {
            // Could not be parked: fold it the old way rather than leave it up.
            hideGuidePanel()
            return
        }
        // Back to full size: it is alone over a game again and has to be found.
        tabView?.recessed = false
        tabView?.readoutHidden = false
        tabView?.visibility = View.VISIBLE
        guidePanelContentFactory.get().onPanelClosed()
    }

    override fun hideGuidePanel() {
        val wasShowing = guidePanelHost.isShowing
        dropGuidePanelWindow()
        // Only when something actually came down. This is called
        // unconditionally from [hideTab] and from several controller paths, and
        // telling the panel it closed when it was never open would tear down
        // state nobody built.
        if (wasShowing) guidePanelContentFactory.get().onPanelClosed()
    }

    /**
     * Takes the panel's window down without telling the panel it closed.
     *
     * The difference matters: a swap between displays rebuilds the window while
     * the user is still reading, and routing that through [hideGuidePanel]
     * would drop the guide's state and reload it from scratch.
     */
    private fun dropGuidePanelWindow() {
        guidePanelHost.hide()
        // Back to full size: it is alone over a game again and has to be found.
        tabView?.recessed = false
        tabView?.readoutHidden = false
        // The handle is never hidden any more, so there is nothing to restore
        // here. Kept explicit against a handle left invisible by an older
        // build's state, which would otherwise be unrecoverable.
        tabView?.visibility = View.VISIBLE
    }

    /** Focus, for as long as a search field in the reader needs it. */
    fun setGuidePanelFocusable(focusable: Boolean) {
        guidePanelHost.setFocusable(focusable)
    }

    /**
     * Steps the handle aside while the keyboard is up over the panel.
     *
     * The handle floats above the panel and, wherever it is parked, the
     * keyboard can slide up underneath it, so a tap meant for a key folds the
     * panel instead. Invisible rather than removed: the window, its position
     * and its drag state stay, and an invisible window takes no touches. Only
     * while the panel is actually up; a parked or closed panel always leaves
     * the handle showing, since then it is the only way back.
     */
    fun setHandleHiddenForKeyboard(hidden: Boolean) {
        val view = tabView ?: return
        val hide = hidden && guidePanelHost.isShowing
        view.visibility = if (hide) View.INVISIBLE else View.VISIBLE
    }

    /** Focus with no keyboard, for as long as a text selection in the reader needs it. */
    fun setGuidePanelFocusableWithoutKeyboard(focusable: Boolean) {
        guidePanelHost.setFocusableWithoutKeyboard(focusable)
    }

    /**
     * Moves Game Twin's presence, the handle and the open panel, to
     * [displayId].
     *
     * Between the teardown and the build there is no handle at all, and on a
     * second screen the handle is the only way back, so a move that fails puts
     * everything back where it was, and a restore that also fails hands the app
     * to the controller to force forward.
     */
    override fun moveToDisplay(displayId: Int): Boolean {
        val panelWasOpen = guidePanelHost.isShowing
        val previousDisplay = tabDisplayId
        dropGuidePanelWindow()
        hideTab()
        if (showTab(displayId, lastFractionX, lastFractionY, currentLook)) {
            if (panelWasOpen) showGuidePanel()
            return true
        }
        Log.w(TAG, "Could not move the handle to display $displayId; restoring $previousDisplay")
        if (!showTab(previousDisplay, lastFractionX, lastFractionY, currentLook)) {
            foldController.get().forceUnfold()
        } else if (panelWasOpen) {
            showGuidePanel()
        }
        return false
    }

    override fun mainDisplayId(): Int? {
        val displayManager = context.getSystemService(DisplayManager::class.java)
        // The built-in display, by its documented id. Checked for validity
        // rather than assumed: a caller that folds onto a display the manager
        // has retired would strand the app behind a tab that never appears.
        return displayManager.getDisplay(android.view.Display.DEFAULT_DISPLAY)
            ?.takeIf { it.isValid }
            ?.displayId
    }

    override fun otherDisplayId(currentDisplayId: Int): Int? {
        val displayManager = context.getSystemService(DisplayManager::class.java)
        return displayManager.displays
            .filter { it.isValid && it.displayId != currentDisplayId }
            // Nothing is filtered on FLAG_PRESENTATION here. The second screen
            // is the interesting case, but the built-in one has to be reachable
            // too, and it is in fact the one the swap exists to reach: it is
            // the only display no presentation can be shown on. These devices
            // have two, so the lowest other id is the answer.
            .minByOrNull { it.displayId }
            ?.displayId
    }

    /** The other display, if the device has one. Null hides Swap and the hold's hint. */
    fun hasAnotherDisplay(): Boolean = otherDisplayId(tabDisplayId) != null

    /**
     * The density of the display the handle is actually on, captured when the
     * window went up.
     *
     * Not the application context's. These devices have two screens and they
     * do not share a density, so sizing a handle on the second screen from the
     * first screen's metrics is how a 56dp handle comes out the wrong size.
     *
     * [showTab] sets it, and the only reader runs after a window is up, so the
     * seed here is never the number anything is drawn from. It is deliberately
     * a constant rather than the context's density: this class is constructed
     * long before any display is chosen, and reading resources at construction
     * is a dependency on a context that has them.
     */
    private var tabDensity: Float = 1f

    private fun dpToPx(dp: Int): Int = (dp * tabDensity).toInt()

    /**
     * A size change, applied to the window that is already up.
     *
     * This used to be a rebuild: take the handle down, put a new one up at the
     * new size. That worked when the size was one of three chips, and it is
     * wrong twice over now. [hideTab] closes the guide panel first and
     * unconditionally, so changing the size from the panel's own settings
     * closed the settings; and a slider is dragged, so a rebuild per size would
     * mean tearing a window down and building another one every few pixels of
     * travel.
     *
     * A [WindowManager.LayoutParams] carries the size, and updateViewLayout
     * applies a new one to a live window, so nothing has to come down. The
     * position is a fraction of travel, which is why the handle stays where it
     * was put rather than drifting as it grows.
     */
    fun resizeTab(sizeDp: Int) {
        val snapped = FoldHandleStyle.sizeDp(sizeDp)
        // The field moves either way: a size chosen while nothing is folded is
        // still the size the next fold has to come up at.
        currentLook = currentLook.copy(sizeDp = snapped)
        val view = tabView ?: return
        val wm = tabWindowManager ?: return
        val params = tabParams ?: return

        val sizePx = dpToPx(snapped)
        if (sizePx == tabSizePx) return
        tabSizePx = sizePx
        view.markSizePx = sizePx
        val placement = tabPlacement()
        params.width = sizePx
        params.height = sizePx
        params.x = placement.x
        params.y = placement.y
        // No [reportHandleGeometry] here, unlike a drag. What the panel is
        // told is which side the handle is on and how far down, both fractions
        // of travel, and a resize moves neither.
        runCatching { wm.updateViewLayout(view, params) }
            .onFailure { Log.w(TAG, "Could not resize the handle", it) }
    }

    /**
     * Draws the handle at its true size while the size slider is held, and
     * puts it back to its reading size on release.
     *
     * Only meaningful with the panel up, which is the only time the handle is
     * recessed and the only place the slider lives.
     */
    fun setSizePreviewing(previewing: Boolean) {
        sizePreviewing = previewing
        tabView?.recessed = guidePanelHost.isShowing && !previewing
    }

    /**
     * An opacity change, applied to the handle that is already up.
     *
     * Alpha on a view, so there is nothing to rebuild and nothing to animate:
     * the handle is drawn at exactly this from the next frame, and stays there.
     */
    fun applyAlpha(alpha: Float) {
        val snapped = FoldHandleStyle.alpha(alpha)
        currentLook = currentLook.copy(alpha = snapped)
        tabView?.handleAlpha = snapped
    }

    /**
     * A reading-size change, applied to the handle that is already up.
     *
     * Nothing has to be previewed here, unlike [resizeTab]: this is the size
     * the handle is drawn at while the panel is open, and the panel is open
     * whenever this slider is on screen. Dragging it moves the thing itself.
     */
    fun applyRecess(scale: Float) {
        val snapped = FoldHandleStyle.recess(scale)
        currentLook = currentLook.copy(recessScale = snapped)
        tabView?.recessScale = snapped
    }

    /**
     * Writes what the sliders settled on.
     *
     * Separate from applying it: [resizeTab] and [applyAlpha] run per frame of
     * a drag, and a DataStore write per frame is both wasted and slower than
     * the drag. The caller applies live and persists on release.
     */
    fun persistHandleStyle() {
        val look = currentLook
        appScope.launch {
            settingsRepository.setFoldHandleAlpha(look.alpha)
            settingsRepository.setFoldHandleSizeDp(look.sizeDp)
            settingsRepository.setFoldHandleRecess(look.recessScale)
        }
    }

    /**
     * The panel was dragged or resized.
     *
     * Moved in place with updateViewLayout rather than rebuilt: a rebuild per
     * drag frame would tear the WebView down and reload the guide on every
     * pixel. The frame is persisted on release, not on every event.
     */
    /** Called when a drag or resize ends, so the frame survives the fold. */
    companion object {
        private const val TAG = "FoldPlatform"

        /** MainActivity's screen-on receiver waits this long before relighting; so does the handle. */
        private const val SCREEN_ON_LED_SETTLE_MS = 800L

        /** How often the handle re-asks for the connection while it can show it. */
        @VisibleForTesting
        internal const val CONNECTION_RECHECK_MS = 30_000L

        /** How long a loss has to last before the handle shows it. */
        @VisibleForTesting
        internal const val OFFLINE_GRACE_MS = 3_000L
    }
}
