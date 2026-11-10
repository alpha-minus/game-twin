package com.twinlore.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import android.graphics.Color
import android.os.Bundle
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import com.twinlore.app.ui.license.SupportDevDialog
import com.twinlore.app.ui.screens.openSupportDevPage
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.graphics.Color as ComposeColor
import com.twinlore.app.data.AppFont
import com.twinlore.app.data.HapticFeedbackIntensity
import com.twinlore.app.data.LedTurnOffTime
import com.twinlore.app.data.SettingsRepository
import com.twinlore.app.ui.LocalCachedFocusColor
import com.twinlore.app.ui.LocalImageLoader
import com.twinlore.app.ui.MainViewModel
import com.twinlore.app.ui.NavigationHost
import com.twinlore.app.ui.ThemedContent
import com.twinlore.app.ui.navigation.GlobalFocusActionTracker
import com.twinlore.app.ui.screens.setup.MigrationFolderSetupScreen
import com.twinlore.app.ui.screens.setup.MigrationProgressScreen
import com.twinlore.app.ui.screens.setup.ReindexingProgressScreen
import com.twinlore.app.ui.startup.BrokenFolderPathsDialog
import com.twinlore.app.ui.startup.LibrarySnapshotTooNewDialog
import com.twinlore.app.ui.sync.SyncPromptDialog
import com.twinlore.app.thor.led.ThorLedManager
import com.twinlore.app.data.effectiveAccentColor
import com.twinlore.app.ui.utils.FeedbackEvent
import com.twinlore.app.ui.utils.FeedbackManager
import com.twinlore.app.util.PerfLog
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import javax.inject.Inject
import coil.ImageLoader
import coil.disk.DiskCache

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var focusActionTracker: GlobalFocusActionTracker

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @javax.inject.Inject
    lateinit var licenseManager: com.twinlore.app.data.license.LicenseManager

    @Inject
    lateinit var mediaIndexer: com.twinlore.app.data.library.MediaIndexer

    @Inject
    lateinit var splashIconCache: com.twinlore.app.data.SplashIconCache

    @Inject
    lateinit var appIconManager: com.twinlore.app.data.AppIconManager

    @Inject
    lateinit var statusBarMetricsController: com.twinlore.app.data.metrics.StatusBarMetricsController

    @Inject
    lateinit var foldController: com.twinlore.app.ui.fold.FoldController

    @Inject
    lateinit var foldPlatform: com.twinlore.app.ui.fold.AndroidFoldPlatform

    private var mainViewModel: MainViewModel? = null

    // Cached for synchronous haptic/LED on key events (updated in onResume)
    private var cachedHapticIntensity = HapticFeedbackIntensity.MEDIUM
    private var cachedFocusColor: ComposeColor? = null
    /** Compose-observable cache so bottom bar can show correct focus color on first frame (avoids flicker). */
    private var cachedFocusColorState: MutableState<ComposeColor?>? = null
    private var cachedLedEnabled = false
    private var cachedLedBrightness = 255
    private var cachedLedSaturationBoost = 0f
    private var cachedLedTurnOffMs: Long? = LedTurnOffTime.NEVER.inactivityMs
    // ABXY swap (Nintendo-style controllers): when true, dispatchKeyEvent rewrites
    // A↔B and X↔Y before any handler sees them, so confirm/back/X/Y flip app-wide.
    @Volatile private var cachedSwapFaceButtons = false

    private var wasInBackground = false
    // Hat D-pad repeat: some controllers don't send continuous motion events when hat is held.
    // We implement keyboard-style repeat: first press immediate, then initial delay, then rapid.
    private val hatRepeatHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var hatRepeatKeyCode: Int? = null
    private val hatRepeatInitialDelayMs = 300L
    private val hatRepeatIntervalMs = 100L
    // Library left stick: debounce to prevent rapid-fire from continuous motion events
    private var lastStickNavTime = 0L
    private val stickNavIntervalMs = 200L
    private val hatRepeatRunnable = object : Runnable {
        override fun run() {
            val code = hatRepeatKeyCode ?: return
            this@MainActivity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
            this@MainActivity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
            hatRepeatHandler.postDelayed(this, hatRepeatIntervalMs)
        }
    }

    private val screenOnReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_ON) {
                lifecycleScope.launch {
                    delay(800)
                    if (lifecycle.currentState != Lifecycle.State.RESUMED) return@launch
                    val focusColor = settingsRepository.focusColor.first()
                    val rawArgb = settingsRepository.rawAccentColorArgb.first()
                    val autoDetect = settingsRepository.autoFocusColorDetection.first()
                    val effective = effectiveAccentColor(focusColor, rawArgb, autoDetect)
                    val ledEnabled = settingsRepository.ledEnabled.first()
                    if (!ledEnabled) return@launch
                    val ledBrightness = settingsRepository.ledBrightness.first()
                    val ledSaturationBoost = settingsRepository.ledSaturationBoost.first()
                    ThorLedManager.onAppForegrounded(
                        true,
                        effective,
                        ledBrightness,
                        ledSaturationBoost
                    )
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        PerfLog.mark("MainActivity.onCreate begin id=${System.identityHashCode(this)} restored=${savedInstanceState != null}")
        super.onCreate(savedInstanceState)
        // Poll perf meters only while the status-cluster mode is Performance;
        // lifecycleScope ties the 1 Hz poll to this activity's lifetime.
        statusBarMetricsController.observe(lifecycleScope)
        // The OS starting-window splash is a system-neutral bg + default logo
        // (it can't be themed per app-icon — the launched activity's manifest
        // theme paints it before app code runs). Instead of letting it snap to
        // the themed in-Compose splash, cross-fade it out: the first Compose
        // frame is already the seeded themed splash (see [SplashIconCache]), so
        // fading the whole OS splash view to transparent reveals the themed bg
        // + logo underneath — one soft transition instead of a hard flip.
        // minSdk 33 ⇒ the platform SplashScreen API is always present.
        splashScreen.setOnExitAnimationListener { splashView ->
            splashView.animate()
                .alpha(0f)
                .setDuration(SPLASH_EXIT_FADE_MS)
                .withEndAction { splashView.remove() }
                .start()
        }
        hideNavigationBar()
        registerReceiver(screenOnReceiver, IntentFilter(Intent.ACTION_SCREEN_ON), Context.RECEIVER_NOT_EXPORTED)
        // Collect MediaIndexer scan events while the activity is at least STARTED;
        // toast on each Started + Finished event. Deferring via repeatOnLifecycle
        // means a scan that finishes while the activity is in background will
        // toast the next time the user comes back.
        lifecycleScope.launch {
            repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                mediaIndexer.events.collect { event ->
                    val msg = when (event) {
                        is com.twinlore.app.data.library.MediaIndexer.ScanEvent.Started ->
                            "Started file discovery for ${event.label}. Covers and guides will link to vaults automatically."
                        is com.twinlore.app.data.library.MediaIndexer.ScanEvent.Finished.Global ->
                            "Local file discovery finished"
                        is com.twinlore.app.data.library.MediaIndexer.ScanEvent.Finished.PerPath ->
                            "${event.fileCount} files detected in ${event.label}"
                    }
                    android.widget.Toast.makeText(this@MainActivity, msg, android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
        // Pre-warm the Chromium engine so the first real WebView creation is
        // fast (~8 ms instead of ~192 ms). Runs on the main thread (required
        // for View construction) after a short delay so it doesn't block the
        // initial Library render.
        lifecycleScope.launch {
            kotlinx.coroutines.delay(300) // let initial UI render first
            try {
                val t0 = System.currentTimeMillis()
                android.webkit.WebView(this@MainActivity).destroy()
                android.util.Log.d("PERF_DEBUG", "WebView pre-warm ${System.currentTimeMillis() - t0}ms")
            } catch (_: Throwable) { }
        }
        cachedFocusColorState = mutableStateOf(null)
        lifecycleScope.launch {
            cachedHapticIntensity = settingsRepository.hapticFeedbackIntensity.first()
            FeedbackManager.hapticIntensity = cachedHapticIntensity
            FeedbackManager.soundEnabled = settingsRepository.focusSoundEnabled.first()
            val focusColor = settingsRepository.focusColor.first()
            val rawArgb = settingsRepository.rawAccentColorArgb.first()
            val autoDetect = settingsRepository.autoFocusColorDetection.first()
            val effective = effectiveAccentColor(focusColor, rawArgb, autoDetect)
            cachedFocusColor = effective
            cachedFocusColorState?.value = effective
            cachedLedEnabled = settingsRepository.ledEnabled.first()
            cachedLedBrightness = settingsRepository.ledBrightness.first()
            cachedLedSaturationBoost = settingsRepository.ledSaturationBoost.first()
            cachedLedTurnOffMs = LedTurnOffTime.NEVER.inactivityMs
        }
        // Keep dispatchKeyEvent feedback settings live — Settings is in-app Compose
        // navigation, so onResume never fires on return and .first() snapshots go stale.
        lifecycleScope.launch {
            settingsRepository.focusSoundEnabled.collect { FeedbackManager.soundEnabled = it }
        }
        lifecycleScope.launch {
            settingsRepository.swapFaceButtons.collect { cachedSwapFaceButtons = it }
        }
        lifecycleScope.launch {
            settingsRepository.hapticFeedbackIntensity.collect {
                cachedHapticIntensity = it
                FeedbackManager.hapticIntensity = it
            }
        }
        // Keep LED/focus cache in sync when any screen updates settings. The quantized/raw pair
        // arrives via ONE flow — combining the two keys as separate flows briefly paired values
        // from different commits, flashing the previous accent on every adaptive-accent change.
        lifecycleScope.launch {
            kotlinx.coroutines.flow.combine(
                settingsRepository.accentColors,
                settingsRepository.autoFocusColorDetection
            ) { (fc, rawArgb), autoDetect ->
                effectiveAccentColor(fc, rawArgb, autoDetect)
            }.collect { color ->
                android.util.Log.d("PERF_DEBUG", "COLOR MainActivity combine flow emit: $color t=${System.currentTimeMillis()}")
                cachedFocusColor = color
                cachedFocusColorState?.value = color
            }
        }
        // Coalesce the three LED settings into a single combine collector
        // instead of three parallel launches; saves a couple of coroutine
        // allocations at startup and one DataStore subscription. combine
        // emits whenever ANY of the three flows changes, with the latest
        // tuple — so each cached value is reassigned (cheap) on every
        // emission.
        lifecycleScope.launch {
            kotlinx.coroutines.flow.combine(
                settingsRepository.ledEnabled,
                settingsRepository.ledBrightness,
                settingsRepository.ledSaturationBoost
            ) { enabled, brightness, saturation ->
                Triple(enabled, brightness, saturation)
            }.collect { (enabled, brightness, saturation) ->
                cachedLedEnabled = enabled
                cachedLedBrightness = brightness
                cachedLedSaturationBoost = saturation
            }
        }
        // Keep the synchronous splash-icon cache warm: every time the effective
        // icon changes, mirror it so the NEXT cold start seeds the splash with
        // the themed icon and never flashes the default splash.
        lifecycleScope.launch {
            com.twinlore.app.data.effectiveAppIconFlow(settingsRepository, licenseManager)
                .collect { splashIconCache.write(it) }
        }
        // Read synchronously (SharedPreferences, in-memory after first load) so
        // the first composition already has the right icon — no DataStore await.
        val seedAppIcon = splashIconCache.read()

        PerfLog.mark("MainActivity.setContent")
        setContent {
            PerfLog.mark("setContent body entered (first composition)")
            val imageLoader = remember {
                // Cap Coil's disk cache at 64 MB. Coil's default ceiling is ~250 MB,
                // which lets a single large remote image (e.g. a full-res VGMaps poster
                // pulled through AsyncImage at 4096-size) park >150 MB of cache bytes and
                // evict every thumbnail. The full-res map viewer in MapContent already
                // sets diskCachePolicy=DISABLED, so this cap only governs incidental fetches
                // (browser thumbnails, picker previews) where 64 MB is plenty.
                ImageLoader.Builder(applicationContext)
                    .diskCache(
                        DiskCache.Builder()
                            .directory(applicationContext.cacheDir.resolve("image_cache"))
                            .maxSizeBytes(64L * 1024 * 1024)
                            .build()
                    )
                    // Animated GIF / WebP support (used by the app wallpaper).
                    .components {
                        add(coil.decode.ImageDecoderDecoder.Factory())
                    }
                    .build()
            }
            // Read theme inputs from settingsRepository directly so ThemedContent
            // doesn't have to construct SettingsViewModel (~170ms cold start of
            // a ~20-singleton graph). SettingsViewModel still gets built later
            // by whatever composable actually needs it (bottom bar, settings
            // screen, etc.) — but that happens after first frame.
            val paletteSelection by settingsRepository.themePalette.collectAsState(initial = null)
            val appFont by settingsRepository.appFont.collectAsState(initial = AppFont.DEFAULT)
            // Seed with the synchronously-cached selection so the very first
            // splash frame is already the user's themed icon. Reading the async
            // DataStore flow with an AppIcon.DEFAULT initial would flash the
            // default purple splash + logo before the real selection arrives.
            val appIcon by com.twinlore.app.data.effectiveAppIconFlow(settingsRepository, licenseManager)
                .collectAsState(initial = seedAppIcon)
            // One Random pool for the whole app, so excluding an effect hides it everywhere it
            // could be rolled rather than only where the setting happens to be read.
            val randomExcluded by settingsRepository.randomAnimationExcluded
                .collectAsState(initial = emptySet())
            CompositionLocalProvider(
                LocalCachedFocusColor provides cachedFocusColorState,
                LocalImageLoader provides imageLoader,
                com.twinlore.app.ui.LocalRandomAnimationPool provides
                    com.twinlore.app.data.ambience.randomPool(randomExcluded),
            ) {
                ThemedContent(paletteSelection = paletteSelection, appFont = appFont) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = androidx.compose.material3.MaterialTheme.colorScheme.background
                    ) {
                        PerfLog.mark("Surface body entered (about to viewModel())")
                        val viewModel: MainViewModel = viewModel()
                        PerfLog.mark("viewModel() returned (mainViewModel acquired)")
                        mainViewModel = viewModel

                        // Handle back navigation
                        val activity = this@MainActivity
                        BackHandler {
                            val handled = viewModel.handleBackNavigation()
                            if (handled) {
                                activity.triggerBackFeedbackAndLed()
                            }
                            if (!handled) {
                                finish()
                            }
                        }

                        val validation by viewModel.folderValidationResult.collectAsState()
                        val pending = validation
                        val homeFolderPicker = rememberLauncherForActivityResult(
                            ActivityResultContracts.OpenDocumentTree(),
                        ) { uri ->
                            if (uri != null) {
                                viewModel.onRecoveryFolderPicked(uri)
                            }
                        }
                        if (pending != null && pending.anyLost) {
                            BrokenFolderPathsDialog(
                                result = pending,
                                onSelectLibraryFolder = {
                                    viewModel.acknowledgeFolderValidation()
                                    homeFolderPicker.launch(null)
                                },
                                onDismiss = { viewModel.acknowledgeFolderValidation() },
                            )
                        }

                        // Cold-start auto-restore refused to copy a newer-schema
                        // snapshot. The library DB stays empty; show the user
                        // why so they can update the app or pick a different
                        // library folder. Stacked AFTER BrokenFolderPathsDialog
                        // so if both fire simultaneously the version-incompatibility
                        // is the topmost / more actionable message.
                        val tooNew by viewModel.librarySnapshotTooNew.collectAsState()
                        val tooNewContext = androidx.compose.ui.platform.LocalContext.current
                        val tooNewFolderPicker = rememberLauncherForActivityResult(
                            ActivityResultContracts.OpenDocumentTree(),
                        ) { uri ->
                            if (uri == null) return@rememberLauncherForActivityResult
                            // Take the grant before the coordinator persists the
                            // URI, so it survives the post-load process restart.
                            try {
                                tooNewContext.contentResolver.takePersistableUriPermission(
                                    uri,
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                                )
                            } catch (e: SecurityException) {
                                Log.e("MainActivity", "Could not take persistable URI permission", e)
                                return@rememberLauncherForActivityResult
                            }
                            viewModel.onLibrarySnapshotTooNewPickFolder(uri)
                        }
                        tooNew?.let { info ->
                            LibrarySnapshotTooNewDialog(
                                savedVersion = info.savedVersion,
                                supportedVersion = info.supportedVersion,
                                onPickDifferentFolder = {
                                    viewModel.acknowledgeLibrarySnapshotTooNew()
                                    tooNewFolderPicker.launch(null)
                                },
                                onUpdate = {
                                    viewModel.acknowledgeLibrarySnapshotTooNew()
                                    viewModel.checkForUpdate()
                                },
                                onDismiss = { viewModel.acknowledgeLibrarySnapshotTooNew() },
                            )
                        }

                        val syncPrompt by viewModel.syncPrompt.collectAsState()
                        syncPrompt?.let { prompt ->
                            SyncPromptDialog(
                                prompt = prompt,
                                onSync = { viewModel.onSyncAccepted() },
                                onNotNow = { viewModel.onSyncDeclined() },
                            )
                        }

                        val allFilesPrompt by viewModel.allFilesAccessPrompt.collectAsState()
                        if (allFilesPrompt) {
                            com.twinlore.app.ui.screens.AllFilesAccessPromptDialog(
                                onGrant = {
                                    viewModel.onAllFilesPromptOpened()
                                    com.twinlore.app.ui.utils.openAllFilesAccessSettings(
                                        this@MainActivity
                                    )
                                },
                                onNotNow = { viewModel.onAllFilesPromptDeclined() },
                            )
                        }

                        // First-launch / migration gate. While the gate is not
                        // Ready, the setup or progress screen renders on top of
                        // the main UI so the user can't navigate around.
                        when (val gate = viewModel.startupGate.collectAsState().value) {
                            MainViewModel.StartupGate.Loading -> {
                                // In-Compose splash while we read DataStore so
                                // there's no gap between the Android 12 system
                                // splash and the LibraryScreen preload splash.
                                com.twinlore.app.ui.components.LibraryPreloadSplash(
                                    preloadOverlayAlpha = 1f,
                                    logoRes = appIcon.splashLogoRes,
                                    bgColor = appIcon.splashBgColor,
                                    backdropIcon = appIcon,
                                )
                            }
                            MainViewModel.StartupGate.NeedsMigrationFolder -> {
                                MigrationFolderSetupScreen(
                                    coordinator = viewModel.homeFolderSetupCoordinator,
                                    onDone = { viewModel.onHomeFolderPicked() }
                                )
                            }
                            is MainViewModel.StartupGate.Migrating -> {
                                MigrationProgressScreen(progress = gate.progress)
                            }
                            is MainViewModel.StartupGate.Reindexing -> {
                                ReindexingProgressScreen(progress = gate.progress)
                            }
                            MainViewModel.StartupGate.Ready -> {
                                androidx.compose.runtime.LaunchedEffect(Unit) {
                                    PerfLog.mark("StartupGate Ready (NavigationHost mounting)")
                                }
                                NavigationHost(viewModel = viewModel)
                                // The Always on Top panel's Support button lands
                                // here: its window cannot host a dialog, so it
                                // hands over with a note and the app answers.
                                // Dialog first and the store only from its own
                                // button, as everywhere else the app asks.
                                val supportRequested = viewModel.supportPromptRequested.collectAsState().value
                                if (supportRequested) {
                                    SupportDevDialog(
                                        onSupport = {
                                            viewModel.dismissSupportPrompt()
                                            openSupportDevPage(this@MainActivity)
                                        },
                                        onDismiss = { viewModel.dismissSupportPrompt() },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        PerfLog.mark("MainActivity.onCreate end id=${System.identityHashCode(this)}")
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        PerfLog.mark("MainActivity.onConfigurationChanged id=${System.identityHashCode(this)} diff=${describeConfig(newConfig)}")
        super.onConfigurationChanged(newConfig)
    }

    override fun onDestroy() {
        PerfLog.mark("MainActivity.onDestroy id=${System.identityHashCode(this)} isFinishing=$isFinishing isChangingConfigurations=$isChangingConfigurations")
        try {
            unregisterReceiver(screenOnReceiver)
        } catch (_: IllegalArgumentException) { }
        // Stop the screenshot capture service when the activity is destroyed
        stopService(android.content.Intent(this, com.twinlore.app.services.ScreenCaptureService::class.java))
        // The fold platform's activity reference dies with the activity, not
        // with the pause (see onPause). Identity-guarded so a new instance that
        // has already registered itself is not cleared by the old one's teardown.
        if (foldPlatform.currentActivity === this) {
            foldPlatform.currentActivity = null
            foldPlatform.activityResumed = false
        }
        // An icon picked in Overlay Mode changes the handle at once and waits
        // for here to reach the launcher: flipping the alias takes the task
        // down, so it is only done once the task is ending anyway. A no-op
        // when the launcher already matches.
        if (isFinishing && !isChangingConfigurations) {
            runCatching { appIconManager.applyLauncherIcon(splashIconCache.read()) }
        }
        super.onDestroy()
    }

    private fun describeConfig(c: android.content.res.Configuration): String =
        "uiMode=${c.uiMode} density=${c.densityDpi} smallestW=${c.smallestScreenWidthDp} screenW=${c.screenWidthDp} screenH=${c.screenHeightDp} orientation=${c.orientation} locale=${c.locales.get(0)} fontScale=${c.fontScale}"

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        PerfLog.mark("MainActivity.onWindowFocusChanged hasFocus=$hasFocus")
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideNavigationBar()
    }

    override fun onStart() {
        PerfLog.mark("MainActivity.onStart")
        super.onStart()
    }

    override fun onAttachedToWindow() {
        PerfLog.mark("MainActivity.onAttachedToWindow")
        super.onAttachedToWindow()
    }

    private fun hideNavigationBar() {
        window.navigationBarColor = Color.TRANSPARENT
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.navigationBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onPause() {
        super.onPause()
        // currentActivity is NOT cleared here. It is a WeakReference, so holding
        // it across a pause leaks nothing, and clearing it would make
        // moveAppToBack() return false on a device where an activity on a
        // secondary display stays visible and interactive while PAUSED - the
        // Thor's class of device - refusing the whole fold feature. The
        // reference is cleared in onDestroy instead. Only the resumed flag,
        // which is what bringAppToFront reports on, follows the pause.
        if (foldPlatform.currentActivity === this) foldPlatform.activityResumed = false
        wasInBackground = true
        // Release LED control when app goes to background
        if (cachedLedEnabled) {
            ThorLedManager.releaseControl()
        }
    }

    override fun onResume() {
        PerfLog.mark("MainActivity.onResume begin")
        super.onResume()
        foldPlatform.currentActivity = this
        foldPlatform.activityResumed = true
        // The tab comes down when the app is genuinely back through the
        // overlay's own way out. Any other start while overlay mode is on folds
        // straight back instead, and then there is no foreground to react to.
        val refolded = foldController.onActivityResumed()
        hideNavigationBar()
        mainViewModel?.updateComposeFocusState(false)
        // Cold start is handled by MainViewModel.init; on the first resume this is
        // still null because the ViewModel is acquired during composition.
        if (!refolded) mainViewModel?.onAppForegrounded()
        if (wasInBackground) {
            wasInBackground = false
        }
        lifecycleScope.launch {
            val focusColor = settingsRepository.focusColor.first()
            val rawArgb = settingsRepository.rawAccentColorArgb.first()
            val autoDetect = settingsRepository.autoFocusColorDetection.first()
            val effective = effectiveAccentColor(focusColor, rawArgb, autoDetect)
            val ledEnabled = settingsRepository.ledEnabled.first()
            val ledBrightness = settingsRepository.ledBrightness.first()
            val ledSaturationBoost = settingsRepository.ledSaturationBoost.first()
            cachedHapticIntensity = settingsRepository.hapticFeedbackIntensity.first()
            FeedbackManager.hapticIntensity = cachedHapticIntensity
            FeedbackManager.soundEnabled = settingsRepository.focusSoundEnabled.first()
            android.util.Log.d("PERF_DEBUG", "COLOR MainActivity onResume: $effective t=${System.currentTimeMillis()}")
            cachedFocusColor = effective
            cachedFocusColorState?.value = effective
            cachedLedEnabled = ledEnabled
            cachedLedBrightness = ledBrightness
            cachedLedSaturationBoost = ledSaturationBoost
            cachedLedTurnOffMs = LedTurnOffTime.NEVER.inactivityMs
            ThorLedManager.onAppForegrounded(
                ledEnabled,
                effective,
                ledBrightness,
                ledSaturationBoost
            )
        }
    }

    private fun wakeUpLedIfNeeded() {
        val color = cachedFocusColor ?: return
        ThorLedManager.setColor(
            color,
            cachedLedEnabled,
            cachedLedBrightness,
            cachedLedSaturationBoost,
            cachedLedTurnOffMs
        )
    }

    private fun triggerFeedback(event: FeedbackEvent) {
        FeedbackManager.play(window.decorView, event, cachedHapticIntensity)
    }

    /** Trigger back feedback + LED for back navigation (from BackHandler). */
    internal fun triggerBackFeedbackAndLed() {
        triggerFeedback(FeedbackEvent.BACK)
        wakeUpLedIfNeeded()
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        if (ev != null) {
            if (ev.action == MotionEvent.ACTION_DOWN || ev.action == MotionEvent.ACTION_MOVE) {
                if (cachedLedEnabled && cachedLedTurnOffMs != null && cachedLedTurnOffMs!! > 0L) {
                    ThorLedManager.resetInactivityTimer(cachedLedTurnOffMs)
                }
                if (ev.action == MotionEvent.ACTION_DOWN && cachedLedEnabled) {
                    wakeUpLedIfNeeded()
                }
            }
        }
        // Note: touch-to-toggle chrome is handled inside each tab's composable
        // (GuideBrowserScreen onTouch / MapContent onTouch) via tap gesture detection,
        // not here. Raw dispatchTouchEvent fires on every touch including scroll/drag,
        // which should NOT show chrome.
        return super.dispatchTouchEvent(ev)
    }

    /**
     * Global key event handler — only handles shortcuts that must work regardless of focus state.
     *
     * D-pad: Falls through to Compose for focus navigation (D-pad is ALWAYS focus, never scroll).
     * Face buttons (X, Y): Falls through to Compose tab-level onPreviewKeyEvent handlers.
     * Global shortcuts: L1/R1 (tab cycle), Start/Select (context actions), handled here.
     */
    /**
     * Rewrites A↔B and X↔Y keycodes for the ABXY-swap setting, preserving all timing/device
     * metadata so downstream repeat + long-press detection is unaffected. Non-face keys pass
     * through unchanged.
     */
    private fun swapFaceButtonKeyEvent(e: KeyEvent): KeyEvent {
        val swapped = when (e.keyCode) {
            KeyEvent.KEYCODE_BUTTON_A -> KeyEvent.KEYCODE_BUTTON_B
            KeyEvent.KEYCODE_BUTTON_B -> KeyEvent.KEYCODE_BUTTON_A
            KeyEvent.KEYCODE_BUTTON_X -> KeyEvent.KEYCODE_BUTTON_Y
            KeyEvent.KEYCODE_BUTTON_Y -> KeyEvent.KEYCODE_BUTTON_X
            else -> return e
        }
        return KeyEvent(
            e.downTime, e.eventTime, e.action, swapped,
            e.repeatCount, e.metaState, e.deviceId, e.scanCode, e.flags, e.source
        )
    }

    override fun dispatchKeyEvent(rawEvent: KeyEvent?): Boolean {
        if (rawEvent == null) return super.dispatchKeyEvent(null)
        // ABXY swap (Nintendo-style controllers): rewrite the keycode up-front so every
        // downstream handler — this method's own X/Y logic AND all Compose confirm/back
        // handlers — sees the corrected button. Symmetric A↔B / X↔Y, so applying once is safe.
        val event = if (cachedSwapFaceButtons) swapFaceButtonKeyEvent(rawEvent) else rawEvent
        val currentState = mainViewModel?.currentState?.value
        val isVaultSession = currentState is com.twinlore.app.domain.AppState.VaultSession
        val isLibrary = currentState is com.twinlore.app.domain.AppState.Library
        val isGuideViewer = currentState is com.twinlore.app.domain.AppState.GuideViewer

        // In vault session: any key press shows chrome and ensures focus exists.
        // This catches the case where chrome is hidden and no Compose element has focus,
        // so onPreviewKeyEvent in VaultSessionScreen never fires.
        if (event.action == KeyEvent.ACTION_DOWN && isVaultSession) {
            val vaultSessionTab = (currentState as? com.twinlore.app.domain.AppState.VaultSession)?.activeTab
            val chromeHidden = when (vaultSessionTab) {
                // A guide overlay (TOC) forces chrome hidden while it owns dpad focus — its keys
                // must reach Compose, not be eaten as a "wake the chrome" press.
                com.twinlore.app.domain.VaultTab.Guide -> mainViewModel?.vaultSessionGuideChromeVisible?.value == false &&
                    mainViewModel?.vaultSessionGuideOverlayOpen?.value != true
                com.twinlore.app.domain.VaultTab.Map -> mainViewModel?.vaultSessionMapChromeVisible?.value == false
                com.twinlore.app.domain.VaultTab.Browser -> mainViewModel?.vaultSessionBrowserChromeVisible?.value == false
                com.twinlore.app.domain.VaultTab.Achievements -> mainViewModel?.vaultSessionAchievementsChromeVisible?.value == false
                com.twinlore.app.domain.VaultTab.Checklist -> mainViewModel?.vaultSessionChecklistChromeVisible?.value == false
                com.twinlore.app.domain.VaultTab.Ai -> false
                null -> false
            }
            if (chromeHidden) {
                when (vaultSessionTab) {
                    com.twinlore.app.domain.VaultTab.Guide -> mainViewModel?.setVaultSessionGuideChromeVisible(true)
                    com.twinlore.app.domain.VaultTab.Map -> mainViewModel?.setVaultSessionMapChromeVisible(true)
                    com.twinlore.app.domain.VaultTab.Browser -> mainViewModel?.setVaultSessionBrowserChromeVisible(true)
                    com.twinlore.app.domain.VaultTab.Achievements -> mainViewModel?.setVaultSessionAchievementsChromeVisible(true)
                    com.twinlore.app.domain.VaultTab.Checklist -> mainViewModel?.setVaultSessionChecklistChromeVisible(true)
                    com.twinlore.app.domain.VaultTab.Ai -> {}
                    null -> {}
                }
                mainViewModel?.notifyChromeInteraction()
                // Allow face buttons to pass through so they trigger actions on the first press
                // (e.g. find-on-page, TOC). Only consume D-pad and navigation keys.
                val isFaceButton = event.keyCode in listOf(
                    KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_BUTTON_Y,
                    KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_BUTTON_SELECT,
                    KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_BUTTON_MODE,
                    KeyEvent.KEYCODE_BUTTON_R2, KeyEvent.KEYCODE_BUTTON_L2,
                    KeyEvent.KEYCODE_BUTTON_THUMBL, KeyEvent.KEYCODE_BUTTON_THUMBR,
                    KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_R1
                )
                if (!isFaceButton) {
                    return true
                }
                // Face buttons: fall through to handlers below
            }
        }

        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                // --- Tab cycling: L1/R1 ---
                KeyEvent.KEYCODE_BUTTON_R1 -> {
                    if (isLibrary) {
                        triggerFeedback(FeedbackEvent.SWITCH)
                        wakeUpLedIfNeeded()
                        mainViewModel?.requestCycleLibrarySort(forward = true)
                        return true
                    }
                    if (isVaultSession) {
                        val vsVaultId = (currentState as? com.twinlore.app.domain.AppState.VaultSession)?.vaultId
                        if (vsVaultId != null) {
                            triggerFeedback(FeedbackEvent.SWITCH)
                            wakeUpLedIfNeeded()
                            mainViewModel?.cycleVaultSessionTab(vsVaultId, forward = true)
                            return true
                        }
                    }
                    if (isGuideViewer) {
                        triggerFeedback(FeedbackEvent.SWITCH)
                        wakeUpLedIfNeeded()
                        mainViewModel?.triggerControllerAction(MainViewModel.ControllerAction.BROWSER_SCROLL_MARKER_NEXT)
                        return true
                    }
                }
                KeyEvent.KEYCODE_BUTTON_L1 -> {
                    if (isLibrary) {
                        triggerFeedback(FeedbackEvent.SWITCH)
                        wakeUpLedIfNeeded()
                        mainViewModel?.requestCycleLibraryViewMode(forward = true)
                        return true
                    }
                    if (isVaultSession) {
                        val vsVaultId = (currentState as? com.twinlore.app.domain.AppState.VaultSession)?.vaultId
                        if (vsVaultId != null) {
                            triggerFeedback(FeedbackEvent.SWITCH)
                            wakeUpLedIfNeeded()
                            mainViewModel?.cycleVaultSessionTab(vsVaultId, forward = false)
                            return true
                        }
                    }
                    if (isGuideViewer) {
                        triggerFeedback(FeedbackEvent.SWITCH)
                        wakeUpLedIfNeeded()
                        mainViewModel?.triggerControllerAction(MainViewModel.ControllerAction.BROWSER_SCROLL_MARKER_PREV)
                        return true
                    }
                }

                // --- Start button: context actions ---
                KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_MENU -> {
                    if (isLibrary) {
                        triggerFeedback(FeedbackEvent.REFRESH)
                        wakeUpLedIfNeeded()
                        mainViewModel?.requestRefreshLibrary()
                        return true
                    }
                    if (isGuideViewer) {
                        triggerFeedback(FeedbackEvent.CONFIRM)
                        wakeUpLedIfNeeded()
                        mainViewModel?.triggerControllerAction(MainViewModel.ControllerAction.TOGGLE_MAP_BROWSER)
                        return true
                    }
                    // Vault session face buttons handled by tab-level onPreviewKeyEvent
                }

                // --- Select button: navigation ---
                KeyEvent.KEYCODE_BUTTON_SELECT, KeyEvent.KEYCODE_BUTTON_MODE -> {
                    if (isLibrary) {
                        triggerFeedback(FeedbackEvent.CONFIRM)
                        wakeUpLedIfNeeded()
                        mainViewModel?.navigateToSettings()
                        return true
                    }
                    if (isGuideViewer) {
                        triggerFeedback(FeedbackEvent.BACK)
                        wakeUpLedIfNeeded()
                        mainViewModel?.navigateToLibrary()
                        return true
                    }
                    // Vault session: let tab-level handlers deal with it,
                    // but if no tab handles it, go back to library
                    if (!isVaultSession) {
                        triggerFeedback(FeedbackEvent.BACK)
                        wakeUpLedIfNeeded()
                        mainViewModel?.navigateToLibrary()
                        return true
                    }
                }

                // --- L2/R2: tab-specific actions ---
                KeyEvent.KEYCODE_BUTTON_R2 -> {
                    if (isGuideViewer) {
                        triggerFeedback(FeedbackEvent.SWITCH)
                        wakeUpLedIfNeeded()
                        mainViewModel?.triggerControllerAction(MainViewModel.ControllerAction.BROWSER_NEXT_BOOKMARK)
                        return true
                    }
                    if (isVaultSession) {
                        val tab = (currentState as? com.twinlore.app.domain.AppState.VaultSession)?.activeTab
                        when (tab) {
                            com.twinlore.app.domain.VaultTab.Guide -> {
                                triggerFeedback(FeedbackEvent.SWITCH)
                                wakeUpLedIfNeeded()
                                mainViewModel?.triggerControllerAction(MainViewModel.ControllerAction.BROWSER_NEXT_BOOKMARK)
                                return true
                            }
                            com.twinlore.app.domain.VaultTab.Map -> {
                                triggerFeedback(FeedbackEvent.SWITCH)
                                wakeUpLedIfNeeded()
                                mainViewModel?.triggerControllerAction(MainViewModel.ControllerAction.MAP_NEXT_FILE)
                                return true
                            }
                            com.twinlore.app.domain.VaultTab.Checklist -> {
                                triggerFeedback(FeedbackEvent.SWITCH)
                                wakeUpLedIfNeeded()
                                mainViewModel?.triggerControllerAction(MainViewModel.ControllerAction.CHECKLIST_TAB_NEXT)
                                return true
                            }
                            else -> {}
                        }
                    }
                }
                KeyEvent.KEYCODE_BUTTON_L2 -> {
                    if (isGuideViewer) {
                        triggerFeedback(FeedbackEvent.SWITCH)
                        wakeUpLedIfNeeded()
                        mainViewModel?.triggerControllerAction(MainViewModel.ControllerAction.BROWSER_PREV_BOOKMARK)
                        return true
                    }
                    if (isVaultSession) {
                        val tab = (currentState as? com.twinlore.app.domain.AppState.VaultSession)?.activeTab
                        when (tab) {
                            com.twinlore.app.domain.VaultTab.Guide -> {
                                triggerFeedback(FeedbackEvent.SWITCH)
                                wakeUpLedIfNeeded()
                                mainViewModel?.triggerControllerAction(MainViewModel.ControllerAction.BROWSER_PREV_BOOKMARK)
                                return true
                            }
                            com.twinlore.app.domain.VaultTab.Map -> {
                                triggerFeedback(FeedbackEvent.SWITCH)
                                wakeUpLedIfNeeded()
                                mainViewModel?.triggerControllerAction(MainViewModel.ControllerAction.MAP_PREV_FILE)
                                return true
                            }
                            com.twinlore.app.domain.VaultTab.Checklist -> {
                                triggerFeedback(FeedbackEvent.SWITCH)
                                wakeUpLedIfNeeded()
                                mainViewModel?.triggerControllerAction(MainViewModel.ControllerAction.CHECKLIST_TAB_PREV)
                                return true
                            }
                            else -> {}
                        }
                    }
                }

                // --- L3/R3: legacy GuideViewer support ---
                KeyEvent.KEYCODE_BUTTON_THUMBL -> {
                    if (isGuideViewer) {
                        triggerFeedback(FeedbackEvent.CONFIRM)
                        wakeUpLedIfNeeded()
                        val mapSheetVisible = mainViewModel?.isMapBottomSheetVisible?.value == true
                        if (mapSheetVisible) {
                            mainViewModel?.triggerAddMarkerAtCenter()
                        } else {
                            mainViewModel?.triggerControllerAction(MainViewModel.ControllerAction.BROWSER_SCROLL_TO_TOP)
                        }
                        return true
                    }
                }
                KeyEvent.KEYCODE_BUTTON_THUMBR -> {
                    if (isGuideViewer) {
                        triggerFeedback(FeedbackEvent.CONFIRM)
                        wakeUpLedIfNeeded()
                        mainViewModel?.triggerControllerAction(MainViewModel.ControllerAction.BROWSER_SCROLL_TO_END)
                        return true
                    }
                }

                // --- X/Y buttons ---
                KeyEvent.KEYCODE_BUTTON_X -> {
                    if (isLibrary) {
                        triggerFeedback(FeedbackEvent.CONFIRM)
                        wakeUpLedIfNeeded()
                        mainViewModel?.requestLaunchFocusedVault()
                        return true
                    }
                    if (isGuideViewer) {
                        triggerFeedback(FeedbackEvent.CONFIRM)
                        wakeUpLedIfNeeded()
                        mainViewModel?.triggerControllerAction(MainViewModel.ControllerAction.OPEN_FIND_ON_PAGE)
                        return true
                    }
                    if (isVaultSession) {
                        val tab = (currentState as? com.twinlore.app.domain.AppState.VaultSession)?.activeTab
                        when (tab) {
                            com.twinlore.app.domain.VaultTab.Guide -> {
                                triggerFeedback(FeedbackEvent.CONFIRM)
                                wakeUpLedIfNeeded()
                                mainViewModel?.triggerControllerAction(MainViewModel.ControllerAction.OPEN_FIND_ON_PAGE)
                                return true
                            }
                            com.twinlore.app.domain.VaultTab.Achievements -> {
                                triggerFeedback(FeedbackEvent.REFRESH)
                                wakeUpLedIfNeeded()
                                mainViewModel?.triggerControllerAction(MainViewModel.ControllerAction.ACHIEVEMENTS_REFRESH)
                                return true
                            }
                            else -> {}
                        }
                    }
                }
                KeyEvent.KEYCODE_BUTTON_Y -> {
                    if (isLibrary) {
                        triggerFeedback(FeedbackEvent.CONFIRM)
                        wakeUpLedIfNeeded()
                        mainViewModel?.requestOpenLibraryContextMenu()
                        return true
                    }
                    if (isGuideViewer) {
                        return true // No action, but consume
                    }
                    if (isVaultSession) {
                        val tab = (currentState as? com.twinlore.app.domain.AppState.VaultSession)?.activeTab
                        when (tab) {
                            com.twinlore.app.domain.VaultTab.Guide -> {
                                triggerFeedback(FeedbackEvent.CONFIRM)
                                wakeUpLedIfNeeded()
                                mainViewModel?.triggerControllerAction(MainViewModel.ControllerAction.OPEN_TOC)
                                return true
                            }
                            com.twinlore.app.domain.VaultTab.Achievements -> {
                                triggerFeedback(FeedbackEvent.CONFIRM)
                                wakeUpLedIfNeeded()
                                mainViewModel?.triggerControllerAction(MainViewModel.ControllerAction.ACHIEVEMENTS_WALLPAPER)
                                return true
                            }
                            com.twinlore.app.domain.VaultTab.Checklist -> {
                                triggerFeedback(FeedbackEvent.CONFIRM)
                                wakeUpLedIfNeeded()
                                mainViewModel?.triggerControllerAction(MainViewModel.ControllerAction.CHECKLIST_ADD_ITEM)
                                return true
                            }
                            else -> {}
                        }
                    }
                }
            }
        }

        // Consume UP events for buttons we handle on DOWN to prevent leakage
        if (event.action == KeyEvent.ACTION_UP) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_BUTTON_R1, KeyEvent.KEYCODE_BUTTON_L1 -> {
                    if (isVaultSession || isGuideViewer) return true
                }
                KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_MENU,
                KeyEvent.KEYCODE_BUTTON_SELECT, KeyEvent.KEYCODE_BUTTON_MODE -> {
                    if (isLibrary || isGuideViewer) return true
                }
                KeyEvent.KEYCODE_BUTTON_R2, KeyEvent.KEYCODE_BUTTON_L2 -> {
                    if (isGuideViewer || isVaultSession) return true
                }
                KeyEvent.KEYCODE_BUTTON_THUMBL, KeyEvent.KEYCODE_BUTTON_THUMBR -> {
                    if (isGuideViewer) return true
                }
                KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_BUTTON_Y -> {
                    if (isLibrary || isGuideViewer || isVaultSession) return true
                }
            }
        }

        // D-pad and all other keys: fall through to Compose for focus navigation
        return super.dispatchKeyEvent(event)
    }

    /**
     * Analog stick and D-pad hat input handler.
     *
     * Left stick: content scroll/pan (WebView scroll, map pan).
     * Right stick: zoom.
     * D-pad hat: synthesize key events for Compose focus navigation (always focus, never scroll).
     */
    override fun dispatchGenericMotionEvent(event: MotionEvent?): Boolean {
        if (event == null) return super.dispatchGenericMotionEvent(event)
        val isControllerMotion = (event.source and (InputDevice.SOURCE_JOYSTICK or InputDevice.SOURCE_GAMEPAD)) != 0
        if (!isControllerMotion || event.device == null) return super.dispatchGenericMotionEvent(event)

        val currentState = mainViewModel?.currentState?.value
        val isLibrary = currentState is com.twinlore.app.domain.AppState.Library
        val isAppCatalog = currentState is com.twinlore.app.domain.AppState.AppCatalog
        val isGuideViewer = currentState is com.twinlore.app.domain.AppState.GuideViewer
        val isVaultSession = currentState is com.twinlore.app.domain.AppState.VaultSession
        val vaultSessionTab = (currentState as? com.twinlore.app.domain.AppState.VaultSession)?.activeTab
        val mapSheetVisible = mainViewModel?.isMapBottomSheetVisible?.value == true
        val mapVisible = (currentState is com.twinlore.app.domain.AppState.MapViewer) ||
                (isGuideViewer && mapSheetVisible) ||
                (isVaultSession && vaultSessionTab == com.twinlore.app.domain.VaultTab.Map)
        val browserVisible = (isGuideViewer && !mapSheetVisible) ||
                (isVaultSession && (vaultSessionTab == com.twinlore.app.domain.VaultTab.Guide ||
                        vaultSessionTab == com.twinlore.app.domain.VaultTab.Browser))

        // D-pad hat: ALWAYS synthesize key events for Compose focus navigation
        val hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
        val hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
        val hatPressed = kotlin.math.abs(hatX) > 0.5f || kotlin.math.abs(hatY) > 0.5f
        if (hatPressed) {
            val keyCode = when {
                hatY < -0.5f -> KeyEvent.KEYCODE_DPAD_UP
                hatY > 0.5f -> KeyEvent.KEYCODE_DPAD_DOWN
                hatX < -0.5f -> KeyEvent.KEYCODE_DPAD_LEFT
                hatX > 0.5f -> KeyEvent.KEYCODE_DPAD_RIGHT
                else -> null
            }
            if (keyCode != null && keyCode != hatRepeatKeyCode) {
                hatRepeatHandler.removeCallbacks(hatRepeatRunnable)
                hatRepeatKeyCode = keyCode
                // If chrome is hidden in vault session, show it and consume (same as dispatchKeyEvent)
                if (isVaultSession) {
                    val chromeHidden = when (vaultSessionTab) {
                        // Same guide-overlay exemption as dispatchKeyEvent: TOC owns dpad focus.
                        com.twinlore.app.domain.VaultTab.Guide -> mainViewModel?.vaultSessionGuideChromeVisible?.value == false &&
                            mainViewModel?.vaultSessionGuideOverlayOpen?.value != true
                        com.twinlore.app.domain.VaultTab.Map -> mainViewModel?.vaultSessionMapChromeVisible?.value == false
                        com.twinlore.app.domain.VaultTab.Browser -> mainViewModel?.vaultSessionBrowserChromeVisible?.value == false
                        com.twinlore.app.domain.VaultTab.Achievements -> mainViewModel?.vaultSessionAchievementsChromeVisible?.value == false
                        com.twinlore.app.domain.VaultTab.Checklist -> mainViewModel?.vaultSessionChecklistChromeVisible?.value == false
                        com.twinlore.app.domain.VaultTab.Ai -> false
                        null -> false
                    }
                    if (chromeHidden) {
                        when (vaultSessionTab) {
                            com.twinlore.app.domain.VaultTab.Guide -> mainViewModel?.setVaultSessionGuideChromeVisible(true)
                            com.twinlore.app.domain.VaultTab.Map -> mainViewModel?.setVaultSessionMapChromeVisible(true)
                            com.twinlore.app.domain.VaultTab.Browser -> mainViewModel?.setVaultSessionBrowserChromeVisible(true)
                            com.twinlore.app.domain.VaultTab.Achievements -> mainViewModel?.setVaultSessionAchievementsChromeVisible(true)
                            com.twinlore.app.domain.VaultTab.Checklist -> mainViewModel?.setVaultSessionChecklistChromeVisible(true)
                            com.twinlore.app.domain.VaultTab.Ai -> {}
                            null -> {}
                        }
                        mainViewModel?.notifyChromeInteraction()
                        return true // Consume — chrome appearing, don't synthesize key
                    }
                }
                super.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
                super.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
                hatRepeatHandler.postDelayed(hatRepeatRunnable, hatRepeatInitialDelayMs)
            }
            // Don't return yet — still process analog sticks below
        } else if (hatRepeatKeyCode != null) {
            hatRepeatHandler.removeCallbacks(hatRepeatRunnable)
            hatRepeatKeyCode = null
        }

        // Left stick processing
        val deadzone = 0.1f
        val leftX = event.getAxisValue(MotionEvent.AXIS_X)
        val leftY = event.getAxisValue(MotionEvent.AXIS_Y)

        // Library: left stick acts as D-pad for focus navigation (debounced).
        if (isLibrary) {
            val stickPressed = kotlin.math.abs(leftX) > 0.5f || kotlin.math.abs(leftY) > 0.5f
            if (stickPressed && !hatPressed) {
                val now = System.currentTimeMillis()
                if (now - lastStickNavTime >= stickNavIntervalMs) {
                    val stickKeyCode = when {
                        leftY < -0.5f -> KeyEvent.KEYCODE_DPAD_UP
                        leftY > 0.5f -> KeyEvent.KEYCODE_DPAD_DOWN
                        leftX < -0.5f -> KeyEvent.KEYCODE_DPAD_LEFT
                        leftX > 0.5f -> KeyEvent.KEYCODE_DPAD_RIGHT
                        else -> null
                    }
                    if (stickKeyCode != null) {
                        lastStickNavTime = now
                        super.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, stickKeyCode))
                        super.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, stickKeyCode))
                    }
                }
            }
            return true
        }

        // Left stick: content interaction (scroll/pan). Right stick: zoom.
        // Only process when in a content view (not library, not settings).
        val rightY1 = event.getAxisValue(MotionEvent.AXIS_RY)
        val rightRZ = event.getAxisValue(MotionEvent.AXIS_RZ)
        val rightY = listOf(rightY1, rightRZ).maxByOrNull { kotlin.math.abs(it) } ?: 0f

        val hasStickInput = kotlin.math.abs(leftX) > deadzone || kotlin.math.abs(leftY) > deadzone || kotlin.math.abs(rightY) > deadzone

        if (hasStickInput && !isLibrary) {
            if (mapVisible) {
                var panX = 0f
                var panY = 0f
                var zoom = 0f
                if (kotlin.math.abs(leftX) > deadzone) panX = -leftX
                if (kotlin.math.abs(leftY) > deadzone) panY = -leftY
                if (kotlin.math.abs(rightY) > deadzone) zoom = rightY
                mainViewModel?.updateControllerInput(panX, panY, zoom)
                mainViewModel?.updateBrowserScroll(0f)
                mainViewModel?.updateBrowserPan(0f)
                mainViewModel?.updateBrowserZoom(0f)
            } else if (browserVisible) {
                var browserScroll = 0f
                var browserPan = 0f
                var browserZoom = 0f
                if (kotlin.math.abs(leftY) > deadzone) browserScroll = leftY
                if (kotlin.math.abs(leftX) > deadzone) browserPan = leftX
                if (kotlin.math.abs(rightY) > deadzone) browserZoom = rightY
                mainViewModel?.updateBrowserScroll(browserScroll)
                mainViewModel?.updateBrowserPan(browserPan)
                mainViewModel?.updateBrowserZoom(browserZoom)
                mainViewModel?.updateControllerInput(0f, 0f, 0f)
            } else if (isVaultSession && (vaultSessionTab == com.twinlore.app.domain.VaultTab.Achievements ||
                                          vaultSessionTab == com.twinlore.app.domain.VaultTab.Checklist ||
                                          vaultSessionTab == com.twinlore.app.domain.VaultTab.Ai)) {
                val scrollY = if (kotlin.math.abs(leftY) > deadzone) leftY else 0f
                mainViewModel?.updateVaultSessionListScroll(scrollY)
                mainViewModel?.updateBrowserScroll(0f)
                mainViewModel?.updateBrowserPan(0f)
                mainViewModel?.updateBrowserZoom(0f)
                mainViewModel?.updateControllerInput(0f, 0f, 0f)
            } else if (isAppCatalog) {
                val scrollY = if (kotlin.math.abs(leftY) > deadzone) leftY else 0f
                mainViewModel?.updateVaultSessionListScroll(scrollY)
            }
            if (cachedLedEnabled) {
                wakeUpLedIfNeeded()
                if (cachedLedTurnOffMs != null && cachedLedTurnOffMs!! > 0L) {
                    ThorLedManager.resetInactivityTimer(cachedLedTurnOffMs)
                }
            }
        } else if (!isLibrary) {
            // Sticks at rest: clear scroll/pan
            if (mapVisible) {
                mainViewModel?.updateControllerInput(0f, 0f, 0f)
            } else if (browserVisible) {
                mainViewModel?.updateBrowserScroll(0f)
                mainViewModel?.updateBrowserPan(0f)
                mainViewModel?.updateBrowserZoom(0f)
            } else if (isVaultSession && (vaultSessionTab == com.twinlore.app.domain.VaultTab.Achievements ||
                                          vaultSessionTab == com.twinlore.app.domain.VaultTab.Checklist ||
                                          vaultSessionTab == com.twinlore.app.domain.VaultTab.Ai)) {
                mainViewModel?.updateVaultSessionListScroll(0f)
            } else if (isAppCatalog) {
                mainViewModel?.updateVaultSessionListScroll(0f)
            }
        }

        // Consume so view hierarchy (e.g. WebView) never sees controller motion
        return true
    }
}

/**
 * Cross-fade duration for the OS splash exit. Long enough to read as a soft
 * dissolve into the themed in-Compose splash, short enough not to delay the
 * library. Tune on device if the handoff feels abrupt or laggy.
 */
private const val SPLASH_EXIT_FADE_MS = 280L
