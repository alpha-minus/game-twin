package com.twinlore.app.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import com.twinlore.app.data.ContentUriFileWatcher
import com.twinlore.app.data.LastPlayedSource
import com.twinlore.app.data.PersistedFolderValidator
import com.twinlore.app.data.SettingsRepository
import com.twinlore.app.data.SyncLatestPlayedGameUseCase
import com.twinlore.app.data.TrackedFileSource
import com.twinlore.app.data.ValidationResult
import com.twinlore.app.data.VaultModeStateManager
import com.twinlore.app.data.VaultRepository
import com.twinlore.app.data.library.DeviceIdMigrator
import com.twinlore.app.data.library.FileRelocator
import com.twinlore.app.data.library.HomeFolderSetupCoordinator
import com.twinlore.app.data.library.JsonSidecarImporter
import com.twinlore.app.data.library.BrowserSaveCoordinator
import com.twinlore.app.data.library.LibraryAutoSaveObserver
import com.twinlore.app.data.library.SaveResult
import com.twinlore.app.data.library.LibraryFolderManager
import com.twinlore.app.data.library.LibraryImporter
import com.twinlore.app.data.library.ResourceResolver
import com.twinlore.app.data.library.LibraryMigrationProgress
import com.twinlore.app.data.library.sync.LibrarySyncCoordinator
import com.twinlore.app.data.library.sync.SyncPromptUi
import com.twinlore.app.data.library.LibraryReindexProgress
import com.twinlore.app.data.library.LocalMediaBinder
import com.twinlore.app.data.library.MediaIndexer
import com.twinlore.app.data.library.MigrationCoordinator
import com.twinlore.app.data.license.LicenseConfig
import com.twinlore.app.data.license.LicenseManager
import com.twinlore.app.data.license.LicenseState
import com.twinlore.app.data.license.LicenseValidationWorker
import com.twinlore.app.domain.AppState
import com.twinlore.app.domain.StateManager
import com.twinlore.app.domain.VaultTab
import com.twinlore.app.data.update.UpdateManager
import com.twinlore.app.data.update.UpdateState
import com.twinlore.app.util.PerfLog
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.stateIn
import android.net.Uri
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * Main ViewModel that manages application state and navigation
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val stateManager: StateManager,
    private val foldHandover: com.twinlore.app.ui.fold.FoldHandover,
    private val vaultModeStateManager: VaultModeStateManager,
    private val licenseManager: LicenseManager,
    private val licenseValidationWorker: LicenseValidationWorker,
    private val settingsRepository: SettingsRepository,
    private val trackedFileSource: TrackedFileSource,
    private val contentUriFileWatcher: ContentUriFileWatcher,
    private val syncLatestPlayedGameUseCase: SyncLatestPlayedGameUseCase,
    private val updateManager: UpdateManager,
    @ApplicationContext private val appContext: Context,
    private val libraryFolderManager: LibraryFolderManager,
    private val migrationCoordinator: MigrationCoordinator,
    private val sidecarImporter: JsonSidecarImporter,
    private val fileRelocator: FileRelocator,
    private val vaultRepository: VaultRepository,
    private val mediaIndexer: MediaIndexer,
    private val localMediaBinder: LocalMediaBinder,
    private val vaultFolderAdopter: com.twinlore.app.data.library.VaultFolderAdopter,
    private val pageGroupFolderAdopter: com.twinlore.app.data.library.PageGroupFolderAdopter,
    private val libraryAutoSaveObserver: LibraryAutoSaveObserver,
    private val libraryImporter: LibraryImporter,
    private val libraryLoadCoordinator: com.twinlore.app.data.library.LibraryLoadCoordinator,
    private val libraryMetaDao: com.twinlore.app.data.local.LibraryMetaDao,
    private val folderValidator: PersistedFolderValidator,
    val homeFolderSetupCoordinator: HomeFolderSetupCoordinator,
    private val resourceResolver: ResourceResolver,
    private val achievementSyncCoordinator: com.twinlore.app.data.AchievementSyncCoordinator,
    private val guideViewStateManager: com.twinlore.app.data.GuideViewStateManager,
    private val savedTabRepository: com.twinlore.app.data.SavedTabRepository,
    private val librarySyncCoordinator: LibrarySyncCoordinator,
    private val deviceIdMigrator: DeviceIdMigrator,
    private val textGuideSplitter: com.twinlore.app.data.library.TextGuideSplitter,
    private val browserSaveCoordinator: BrowserSaveCoordinator,
    /**
     * Read for one question only: whether the Achievement Journey is still a place this app offers
     * to go, when back out of Settings would otherwise restore it. See [exitSettings].
     */
    private val raCredentialsRepository:
        com.twinlore.app.data.retroachievements.RetroAchievementsCredentialsRepository,
    private val overlayMemory: com.twinlore.app.ui.fold.OverlayMemory,
) : BaseViewModel() {

    /**
     * Backing flag for [allFilesAccessPrompt]. Declared here, above `init`, rather than
     * next to the public flow further down: `init` writes it from a coroutine, and the
     * only reason a later declaration compiles at all is that the write sits behind a
     * `delay`. Keeping the declaration first means that stays true by construction.
     */
    private val _allFilesAccessPrompt = MutableStateFlow(false)

    /**
     * One-shot flag: an action elsewhere needs the library storage folder and the user hasn't
     * picked one yet. The library screen owns that whole flow (pick → snapshot check → migrate
     * → restart), so everywhere else navigates there and raises this instead of growing a second
     * picker. A latch, not an event: the library isn't composed when the request is made.
     */
    private val _homeFolderSetupRequest = MutableStateFlow(false)
    val homeFolderSetupRequest: StateFlow<Boolean> = _homeFolderSetupRequest.asStateFlow()

    fun consumeHomeFolderSetupRequest() { _homeFolderSetupRequest.value = false }

    /**
     * First-launch / migration gate. While not [StartupGate.Ready] the activity
     * renders a setup or migration screen on top of the navigation host so the
     * user can't interact with the rest of the app.
     */
    sealed class StartupGate {
        object Loading : StartupGate()
        object NeedsMigrationFolder : StartupGate()
        data class Migrating(val progress: LibraryMigrationProgress) : StartupGate()
        data class Reindexing(val progress: LibraryReindexProgress) : StartupGate()
        object Ready : StartupGate()
    }

    private val _startupGate = MutableStateFlow<StartupGate>(StartupGate.Loading)
    val startupGate: StateFlow<StartupGate> = _startupGate.asStateFlow()

    /** Library-focused vault id (reported by LibraryScreen). Used to derive current active vault when state is Library. */
    private val _libraryFocusedVaultId = MutableStateFlow<String?>(null)

    /** One-shot request to open the context menu for the focused vault in Library (e.g. from controller Start/Menu button). */
    private val _requestOpenLibraryContextMenu = MutableSharedFlow<Unit>()
    val requestOpenLibraryContextMenu: SharedFlow<Unit> = _requestOpenLibraryContextMenu.asSharedFlow()

    /** One-shot request to cycle the library view mode. Boolean = forward (true) or backward (false). */
    private val _requestCycleLibraryViewMode = MutableSharedFlow<Boolean>()
    val requestCycleLibraryViewMode: SharedFlow<Boolean> = _requestCycleLibraryViewMode.asSharedFlow()

    /** One-shot request to cycle the library sort field. Boolean = forward (true) or backward (false). */
    private val _requestCycleLibrarySort = MutableSharedFlow<Boolean>()
    val requestCycleLibrarySort: SharedFlow<Boolean> = _requestCycleLibrarySort.asSharedFlow()

    /** One-shot request to refresh the vault list in Library (e.g. from controller X button). */
    private val _requestRefreshLibrary = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val requestRefreshLibrary: SharedFlow<Unit> = _requestRefreshLibrary.asSharedFlow()

    /** One-shot request to launch the focused vault in Library (e.g. from controller X button).
     *  LibraryScreen collects this and routes it to launcherViewModel.launchGame()
     *  (or launchApp() when an app cell is focused in Launcher mode). */
    private val _requestLaunchFocusedVault = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val requestLaunchFocusedVault: SharedFlow<Unit> = _requestLaunchFocusedVault.asSharedFlow()

    /** Emitted when user tries to open a vault whose files no longer exist on disk. */
    private val _vaultMissing = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val vaultMissing: SharedFlow<String> = _vaultMissing.asSharedFlow()

    /**
     * Latest result of [PersistedFolderValidator] from cold start, or null if validation
     * is not pending UI acknowledgment (clean start, or already acknowledged).
     *
     * Using [StateFlow] rather than a one-shot [SharedFlow] guarantees late subscribers
     * (e.g. MainActivity's LaunchedEffect mounted after VM init) still observe a loss
     * result. The UI MUST call [acknowledgeFolderValidation] after handling the dialog
     * so config changes do not redisplay it.
     */
    private val _folderValidationResult = MutableStateFlow<ValidationResult?>(null)
    val folderValidationResult: StateFlow<ValidationResult?> = _folderValidationResult.asStateFlow()

    /** UI calls this after the dialog has been shown so config changes do not redisplay it. */
    fun acknowledgeFolderValidation() {
        _folderValidationResult.value = null
    }

    /**
     * Versions for the cold-start "library was saved by a newer app" dialog.
     * Set when [releaseStartupGate]'s auto-restore catches
     * [com.twinlore.app.data.library.LibrarySnapshotTooNewException]; cleared
     * by [acknowledgeLibrarySnapshotTooNew] after the dialog is shown.
     *
     * Persisting the values (vs a one-shot SharedFlow) means MainActivity's
     * recomposition-survivable dialog wiring works the same as the
     * folder-validation dialog.
     */
    data class LibrarySnapshotTooNew(val savedVersion: Int, val supportedVersion: Int)

    private val _librarySnapshotTooNew = MutableStateFlow<LibrarySnapshotTooNew?>(null)
    val librarySnapshotTooNew: StateFlow<LibrarySnapshotTooNew?> = _librarySnapshotTooNew.asStateFlow()

    /** UI calls this once the too-new dialog has been dismissed. */
    fun acknowledgeLibrarySnapshotTooNew() {
        _librarySnapshotTooNew.value = null
    }

    /**
     * "Pick a different folder" action on the too-new dialog. Clears the dialog
     * and loads the library from [uri] through the guarded coordinator (import
     * first, switch only on success), restarting on success. If the newly
     * picked folder is ALSO too new, re-surface the dialog rather than switching
     * to it — same no-corruption guarantee as the cold-start path.
     */
    fun onLibrarySnapshotTooNewPickFolder(uri: Uri) {
        _librarySnapshotTooNew.value = null
        viewModelScope.launch {
            try {
                libraryAutoSaveObserver.exclusive {
                    libraryLoadCoordinator.loadFromFolder(uri)
                }
                com.twinlore.app.ui.utils.restartApp(appContext)
            } catch (t: com.twinlore.app.data.library.LibrarySnapshotTooNewException) {
                _librarySnapshotTooNew.value = LibrarySnapshotTooNew(
                    savedVersion = t.savedVersion,
                    supportedVersion = t.supportedVersion,
                )
            } catch (_: Exception) {
                // Best-effort, matching the cold-start auto-restore contract.
            }
        }
    }

    val updateState: StateFlow<UpdateState> = updateManager.state

    fun downloadUpdate(downloadUrl: String, version: String) {
        viewModelScope.launch { updateManager.downloadUpdate(downloadUrl, version) }
    }

    /**
     * Force an update check. Surfaces the global update dialog when a newer
     * build exists. Used by the "Update" action on the too-new library dialog.
     */
    fun checkForUpdate() {
        viewModelScope.launch { updateManager.checkForUpdate(force = true) }
    }

    fun installUpdate(file: java.io.File): android.content.Intent {
        return updateManager.createInstallIntent(file)
    }

    fun skipUpdateVersion(version: String) {
        viewModelScope.launch { updateManager.skipVersion(version) }
    }

    fun dismissUpdate() {
        updateManager.dismiss()
    }

    private val autoRefreshSyncMutex = Mutex()
    
    init {
        PerfLog.mark("MainViewModel.init begin id=${System.identityHashCode(this)}")
        // Freemium model: the app always opens to Library. No startup gate,
        // no license routing. The freemium model only gates app cosmetics.
        //
        // Re-validate a stored license in the background so its state stays fresh. Failure is
        // fine — LicenseManager tolerates network errors without flipping the state.
        // Defer the four network-touching startup checks by ~2 s so the
        // OkHttp pool + DNS / TLS handshakes don't contend with first-frame
        // Main-thread work or the cold-start file IO. Each is gated by a
        // mutex / TTL / NotActivated check internally so re-checking later
        // is fine; the only thing we lose is "first millisecond of the
        // process" responsiveness, which nothing user-visible cares about.
        // [achievementSyncCoordinator.syncAtStartup is below in the same
        // group; evaluateStartup / stateManager.collect / fileChanges stay
        // immediate because they gate the StartupGate or watch live
        // signals.]
        viewModelScope.launch {
            kotlinx.coroutines.delay(2000L)
            PerfLog.mark("license activate/validate begin")
            try {
                // Nothing to refresh until the user has entered a key of their own.
                if (licenseManager.getLicenseState().first() !is LicenseState.NotActivated) {
                    licenseManager.validateLicense()
                }
            } catch (e: Exception) {
                // Non-blocking background activation/validation
            }
            PerfLog.mark("license activate/validate end")
        }
        viewModelScope.launch {
            kotlinx.coroutines.delay(2000L)
            PerfLog.mark("licenseValidationWorker begin")
            try {
                licenseValidationWorker.triggerValidation()
            } catch (e: Exception) {
                // Non-blocking background validation
            }
            PerfLog.mark("licenseValidationWorker end")
        }

        // Check for app updates (non-blocking), then re-check daily: this is a kiosk-style
        // device that can run for days as an always-on display, so a startup-only check would
        // never surface an update until a restart. Non-forced -- skipped versions stay skipped
        // and failures stay silent. On Default, NOT the main dispatcher: under runTest the main
        // dispatcher's virtual clock fast-forwards through every delay, so this forever-loop
        // makes advanceUntilIdle() spin unboundedly (recording millions of mock invocations
        // until the test JVM OOMs).
        viewModelScope.launch(Dispatchers.Default) {
            kotlinx.coroutines.delay(2000L)
            PerfLog.mark("updateManager.checkForUpdate begin")
            updateManager.checkForUpdate()
            PerfLog.mark("updateManager.checkForUpdate end")
            while (true) {
                kotlinx.coroutines.delay(24 * 60 * 60 * 1000L)
                updateManager.checkForUpdate()
            }
        }

        // Cold start always lands on Library regardless of last-visited tab.
        // Persist subsequent state transitions only — the persisted value is no
        // longer read on launch.
        viewModelScope.launch {
            stateManager.currentState.collect { state ->
                settingsRepository.setLastAppState(stateManager.serializeState(state))
            }
        }

        // Startup detection (openLastPlayedOnLaunch) is launched from the init block
        // at the end of the class: see there.

        // Auto-refresh: when tracked file changes, trigger the same refresh flow as the Refresh button (unified experience).
        // Debounce so multiple file writes in quick succession trigger only one refresh.
        contentUriFileWatcher.fileChanges(
            uriFlow = trackedFileSource.uriFlow,
            enabledFlow = trackedFileSource.enabledFlow,
            sourceFlow = trackedFileSource.sourceFlow
        ).debounce(2000)
        .onEach {
            autoRefreshSyncMutex.withLock {
                val uri = trackedFileSource.uriFlow.first()
                val vaultId = syncLatestPlayedGameUseCase.execute(uriOverride = uri)
                val state = stateManager.getCurrentState()
                val currentActiveVaultId = when (state) {
                    is AppState.VaultSession -> state.vaultId
                    is AppState.MapViewer -> state.vaultId
                    is AppState.GuideViewer -> state.vaultId
                    is AppState.Library -> _libraryFocusedVaultId.value
                    else -> null
                }
                // Set trigger when vault to show differs, or when sync returned null (e.g. game has no vault yet) so scrapeAllBoxArt can create/focus.
                // Skip when user just pressed back — they explicitly chose to return to library with the current vault.
                // Also check timestamp: file watcher debounce (2s) can outlive the boolean flag.
                val recentBackPress = System.currentTimeMillis() - backPressNavigatingTimestamp < 5000
                if ((vaultId != currentActiveVaultId || vaultId == null) && !_backPressNavigating.value && !recentBackPress) {
                    _triggerRefreshWhenOnLibrary.value = true
                    // Only set vaultToFocus when navigating to Library; when already on Library, detection effect applies focus under black (avoids visible focus change before transition)
                    if (state !is AppState.Library && vaultId != null) {
                        _vaultToFocusInLibrary.value = vaultId
                        navigateToLibrary(focusedVaultId = vaultId)
                    }
                }
            }
        }
        .launchIn(viewModelScope)

        // Determine first-launch / migration state. Runs once on VM creation;
        // re-runs after the user picks a home folder via [onHomeFolderPicked].
        viewModelScope.launch { evaluateStartup() }

        // Library hygiene: remove duplicate local-asset registrations
        // (Syncthing .stversions copies, group-owned page-N files bound as
        // standalone guides, variant-dir duplicates). Idempotent. Runs every
        // start so duplicates merged in from another device's snapshot
        // self-heal; the deletions propagate back out via the vault fingerprint
        // bump at the next snapshot save.
        //
        // It is NOT cheap. This comment used to claim "single-digit ms across
        // the library"; measured on the Thor with 14 vaults (release build,
        // scripts/perfprobe-threads.sh over an otherwise-idle window) it is
        // 0.33-0.35 s of CPU on Dispatchers.Default, ~14% of the app's ~2.4 s
        // cold-start CPU, on a clean library where every pass is a no-op. The
        // per-vault cost is two indexed queries, but it runs them for every
        // vault plus two whole-library adopter passes.
        //
        // Undelayed, it started at +76 ms and ran until +3.3 s, straddling the
        // first frame and the grid artwork preload and competing with both.
        // Deferring it by 2 s — the same treatment every other startup task in
        // this file already gets, for the same stated reason — cut the CPU
        // burned before the library is on screen from ~130 to ~121 ticks (-7%).
        // It does not reduce total CPU; the work still happens. To actually
        // remove it, gate it behind a DataStore TTL the way
        // achievementSyncCoordinator does, so a healthy library skips the pass
        // entirely on most cold starts.
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            kotlinx.coroutines.delay(2000L)
            PerfLog.mark("startup library hygiene begin")
            // Re-point vaults whose folder_name names a directory that was never
            // created because the same game carries the article at the other end
            // on another device ("Legend of Zelda, The: X" vs "The Legend of
            // Zelda: X"). Runs before the cleanup below so the duplicate scan
            // sees the adopted folder. No-op on a healthy library.
            runCatching { vaultFolderAdopter.adoptOrphanedFolders() }
                .onSuccess { if (it > 0) android.util.Log.i("MainViewModel", "startup adopted $it orphaned vault folder(s)") }
                .onFailure { android.util.Log.w("MainViewModel", "startup vault-folder adoption failed", it) }

            // Register page-group folders sitting in a vault folder with no rows to
            // describe them (a guide bulk-saved on another device, replicated here by
            // Syncthing). Must run BEFORE the bind below: the binder decides what is a
            // group-owned page by reading the group rows this writes.
            runCatching { pageGroupFolderAdopter.adoptOrphanedGroups() }
                .onSuccess { if (it > 0) android.util.Log.i("MainViewModel", "startup adopted $it orphaned page group(s)") }
                .onFailure { android.util.Log.w("MainViewModel", "startup page-group adoption failed", it) }

            runCatching {
                var syncArtifacts = 0; var groupOwned = 0; var logicalDuplicates = 0
                for (vault in vaultRepository.getAllVaults()) {
                    runCatching { localMediaBinder.cleanupLocalLinkDuplicates(vault.id) }
                        .onSuccess { s ->
                            syncArtifacts += s.syncArtifacts
                            groupOwned += s.groupOwned
                            logicalDuplicates += s.logicalDuplicates
                        }
                        .onFailure {
                            android.util.Log.w("MainViewModel", "local-asset cleanup failed for vault ${vault.id}", it)
                        }
                }
                val total = syncArtifacts + groupOwned + logicalDuplicates
                if (total > 0) {
                    android.util.Log.i(
                        "MainViewModel",
                        "startup local-asset cleanup removed $total duplicate registration(s): " +
                            "syncArtifacts=$syncArtifacts groupOwned=$groupOwned logicalDuplicates=$logicalDuplicates"
                    )
                }
            }.onFailure {
                android.util.Log.w("MainViewModel", "startup local-asset cleanup failed", it)
            }
            PerfLog.mark("startup library hygiene end")
        }

        // Proactive achievement progress sync. 6h DataStore TTL gates warm cold-restarts.
        // Non-blocking; library bars update reactively when the coordinator writes rows.
        // Same 2 s deferral as the other network checks above so the first
        // frame paints before we contend for OkHttp / DNS.
        viewModelScope.launch {
            kotlinx.coroutines.delay(2000L)
            PerfLog.mark("achievementSync begin")
            runCatching { achievementSyncCoordinator.syncAtStartup() }
                .onFailure { android.util.Log.w("MainViewModel", "achievementSync failed", it) }
            PerfLog.mark("achievementSync end")
        }
        // Deferred like the other post-first-frame work: this must never contend with
        // the first frame, and a scan that imports vaults mid-session should not throw a
        // permission dialog over its progress. One shot per cold start, by design.
        viewModelScope.launch {
            kotlinx.coroutines.delay(2000L)
            runCatching {
                val show = com.twinlore.app.data.AllFilesAccessPromptDecision.shouldPrompt(
                    sdkInt = android.os.Build.VERSION.SDK_INT,
                    granted = com.twinlore.app.data.esde.EsdeFsAccess.isGranted(),
                    // COUNT(*), not getAllVaults().size — the latter would deserialize
                    // every row just to read a length, on a library big enough to
                    // trigger the prompt in the first place.
                    vaultCount = vaultRepository.getVaultCount(),
                    declined = settingsRepository.allFilesPromptDeclined.first(),
                )
                if (show) _allFilesAccessPrompt.value = true
            }.onFailure {
                android.util.Log.w("MainViewModel", "all-files prompt check failed", it)
            }
        }
        PerfLog.mark("MainViewModel.init end id=${System.identityHashCode(this)}")
    }

    /**
     * Re-runs the first-launch evaluation. Call after the user finishes
     * picking a home folder in [com.twinlore.app.ui.screens.setup.MigrationFolderSetupScreen]
     * so migration (if any) can kick off and the gate releases.
     */
    fun onHomeFolderPicked() {
        viewModelScope.launch { evaluateStartup() }
    }

    fun onRecoveryFolderPicked(uri: android.net.Uri) {
        viewModelScope.launch {
            homeFolderSetupCoordinator.useSafTree(uri)
            runReindexAfterHomeFolderPicked()
        }
    }

    private suspend fun runReindexAfterHomeFolderPicked() {
        val vaults = vaultRepository.getAllVaults()
        if (vaults.isEmpty()) {
            evaluateStartup()
            return
        }

        _startupGate.value = StartupGate.Reindexing(LibraryReindexProgress.Idle)
        PerfLog.mark("reindex begin (vaults=${vaults.size})")

        runCatching {
            mediaIndexer.rebuildAll { _, label ->
                _startupGate.value = StartupGate.Reindexing(
                    LibraryReindexProgress.Scanning(label),
                )
            }
        }.onFailure {
            PerfLog.mark("reindex scan failed: ${it.message}")
        }

        vaults.forEachIndexed { i, vault ->
            _startupGate.value = StartupGate.Reindexing(
                LibraryReindexProgress.Binding(
                    current = i + 1,
                    total = vaults.size,
                    vaultName = vault.displayName,
                ),
            )
            runCatching {
                // Clean duplicate registrations first so the bind below doesn't
                // re-evaluate (or re-bind slots against) rows the cleanup is
                // about to delete. Idempotent.
                localMediaBinder.cleanupLocalLinkDuplicates(vault.id)
                localMediaBinder.bindForVault(vault, preserveExistingSlots = false)
            }.onFailure {
                PerfLog.mark("reindex bind failed for ${vault.id}: ${it.message}")
            }
        }

        PerfLog.mark("reindex done")
        evaluateStartup()
    }

    /**
     * Scope check, so future readers don't conflate two separate migration tracks:
     *
     *   1. THIS code (evaluateStartup) handles per-device FILESYSTEM-LAYOUT
     *      migrations — moving 0.7.x's `getExternalFilesDir/vaults` and any
     *      custom-storage URI content into the 0.8.x home-folder layout.
     *      State lives in DataStore (per-install) because filesystem layout is
     *      per-device and doesn't travel.
     *
     *   2. Room (VaultDatabaseMigration + SCHEMA_VERSION) handles per-snapshot
     *      DATABASE-SCHEMA migrations on the imported `.gametwin` file. When
     *      LibraryImporter swaps in an older snapshot, Room reads its
     *      user_version and runs whichever MIGRATION_N_M chain is needed —
     *      automatically, on next DB open. That work does NOT route through
     *      here.
     *
     * So loading a library folder written by an older app version is fully
     * handled by Room. This code does not need to re-run when the home folder
     * changes — the device's legacy paths aren't affected by which snapshot
     * is loaded.
     */
    private suspend fun evaluateStartup() {
        PerfLog.mark("evaluateStartup begin")
        val validation = folderValidator.validate()
        if (validation.anyLost) {
            _folderValidationResult.value = validation
            PerfLog.mark("evaluateStartup: persisted folder validation reported losses")
        }
        // Fast path: this install already ran the legacy migration to a clean
        // result, so skip the ~400ms of internalize/scan/listFiles on every
        // cold start. If we ever ship a NEW one-off filesystem migration,
        // introduce a sibling boolean (e.g. legacy_data_v3_completed) rather
        // than retrofitting a version counter — matches the existing
        // license_migration_v2 precedent.
        if (settingsRepository.legacyMigrationCompleted.first()) {
            PerfLog.mark("evaluateStartup skipped (legacy_migration_completed=true)")
            releaseStartupGate()
            return
        }
        // Bring 0.7.x's custom-storage-folder vault data into the app-private
        // vaults dir so the existing migration code can pick it up. Idempotent
        // — skips vaults already internalized.
        withContext(Dispatchers.IO) { internalizeLegacyCustomStorage() }
        PerfLog.mark("evaluateStartup internalizeLegacy done")
        val externalVaults = withContext(Dispatchers.IO) {
            appContext.getExternalFilesDir(null)?.let { File(it, "vaults") }
                ?.takeIf { it.exists() && it.listFiles().orEmpty().isNotEmpty() }
        }
        val internalVaults = withContext(Dispatchers.IO) {
            File(appContext.filesDir, "vaults")
                .takeIf { it.exists() && it.listFiles().orEmpty().isNotEmpty() }
        }
        val legacyRoots = listOfNotNull(externalVaults, internalVaults)
        PerfLog.mark("evaluateStartup legacy-roots-check done (legacy=${legacyRoots.size})")
        if (legacyRoots.isEmpty()) {
            // No legacy data ever existed (or it was already migrated and
            // cleaned up by a previous run). Record so future cold starts can
            // skip this whole block via the fast path above.
            settingsRepository.setLegacyMigrationCompleted(true)
            releaseStartupGate()
            return
        }
        // Legacy data present -- need a home folder to migrate into.
        val homeUri = settingsRepository.homeFolderUri.first()
        if (homeUri == null) {
            _startupGate.value = StartupGate.NeedsMigrationFolder
            return
        }
        runMigration(legacyRoots)
    }

    /**
     * 0.7.x let the user point vault storage at an arbitrary folder via
     * `vault_storage_folder_uri`. That URI may be a real path (`file://`) or
     * a persisted SAF tree (`content://`). Either way, the data sits at
     * `<chosenRoot>/vaults/<vault-id>/{vault_metadata.json, art/, map/, guide/}`,
     * NOT in `getExternalFilesDir(null)/vaults`, so the rest of the migration
     * doesn't see it.
     *
     * This helper copies each unseen legacy vault dir into the app-private
     * `getExternalFilesDir(null)/vaults` so the existing Phase A0/B/C code can
     * find and process it like any other legacy install. Idempotent: vaults
     * already present at the destination are skipped, so repeat startups are
     * cheap. The source under `<chosenRoot>/vaults` is left intact — Phase C
     * cleanup only deletes the internalized copies.
     */
    private suspend fun internalizeLegacyCustomStorage() {
        val rawUri = settingsRepository.vaultStorageFolderUri.first()
            ?.takeIf { it.isNotBlank() } ?: return
        val uri = runCatching { Uri.parse(rawUri) }.getOrNull() ?: return
        val destRoot = appContext.getExternalFilesDir(null)?.let { File(it, "vaults") } ?: return
        when (uri.scheme) {
            "file" -> {
                val srcRoot = File(uri.path ?: return, "vaults")
                    .takeIf { it.isDirectory } ?: return
                destRoot.mkdirs()
                srcRoot.listFiles()?.forEach { vaultDir ->
                    if (!vaultDir.isDirectory) return@forEach
                    val target = File(destRoot, vaultDir.name)
                    if (target.exists()) return@forEach
                    runCatching { vaultDir.copyRecursively(target, overwrite = false) }
                }
            }
            "content" -> {
                val tree = androidx.documentfile.provider.DocumentFile
                    .fromTreeUri(appContext, uri) ?: return
                val vaultsDoc = tree.findFile("vaults")
                    ?.takeIf { it.isDirectory } ?: return
                destRoot.mkdirs()
                for (vaultDoc in vaultsDoc.listFiles()) {
                    if (!vaultDoc.isDirectory) continue
                    val name = vaultDoc.name ?: continue
                    val target = File(destRoot, name)
                    if (target.exists()) continue
                    runCatching { copySafTreeIntoFileDir(vaultDoc, target) }
                }
            }
        }
    }

    private fun copySafTreeIntoFileDir(
        srcDoc: androidx.documentfile.provider.DocumentFile,
        dest: File
    ) {
        dest.mkdirs()
        for (child in srcDoc.listFiles()) {
            val name = child.name ?: continue
            when {
                child.isDirectory -> copySafTreeIntoFileDir(child, File(dest, name))
                child.isFile -> File(dest, name).outputStream().use { out ->
                    appContext.contentResolver.openInputStream(child.uri)?.use { it.copyTo(out) }
                }
            }
        }
    }

    private suspend fun runMigration(legacyRoots: List<File>) {
        _startupGate.value = StartupGate.Migrating(LibraryMigrationProgress.Idle)
        var migrationFailed = false
        try {
            migrationCoordinator.runFullMigration(
                legacyVaultsRoots = legacyRoots,
                sidecarImporter = sidecarImporter,
                fileRelocator = fileRelocator
            ) { progress ->
                _startupGate.value = StartupGate.Migrating(progress)
            }
        } catch (_: Throwable) {
            // Per-vault failures are surfaced via the progress callback; an
            // outer throw here means the orchestrator itself failed.
            // Release the gate either way so the app remains usable.
            migrationFailed = true
        }
        // Only mark migration sticky-complete when the orchestrator returned
        // cleanly. A failure leaves the flag false so the next launch retries
        // the same migration code from scratch.
        if (!migrationFailed) {
            settingsRepository.setLegacyMigrationCompleted(true)
        }
        releaseStartupGate()
    }

    /**
     * Mark the startup gate as Ready and kick off the cold-start auto-restore
     * check: if the internal Room DB is empty and the library folder has any
     * snapshot (per-device or legacy bare library.gametwin), import the newest
     * one automatically so a fresh install on a new device with the copied
     * library folder just works.
     */
    private fun releaseStartupGate() {
        PerfLog.mark("releaseStartupGate (gate=Ready)")
        _startupGate.value = StartupGate.Ready
        viewModelScope.launch {
            runCatching { deviceIdMigrator.migrateIfNeeded() }
            PerfLog.mark("auto-restore check begin")
            runCatching {
                // Auto-restore the newest snapshot from <library-folder> only when
                // the internal Room DB has never been initialized in this install
                // — the fresh-install-on-Device-B path. library_meta is null on
                // a truly fresh DB and populated on the first save, so it's a
                // reliable proxy. This prevents a loop where the user deletes
                // their last vault, ON_PAUSE saves the empty state out, and the
                // next launch re-imports the empty state forever.
                val hasMeta = withContext(kotlinx.coroutines.Dispatchers.IO) {
                    libraryMetaDao.getMeta() != null
                }
                if (hasMeta) return@runCatching
                val vaultCount = withContext(kotlinx.coroutines.Dispatchers.IO) {
                    vaultRepository.getVaultCount()
                }
                if (vaultCount > 0) return@runCatching
                val homeUri = settingsRepository.homeFolderUri.first()?.let(Uri::parse)
                    ?: return@runCatching
                // Catch the too-new case BEFORE the success branch so the
                // dialog state lives across the would-be restart (which we
                // also suppress here). Other failures fall through to a
                // silent skip the way they did before — auto-restore has
                // always been best-effort.
                val result: Result<Unit> = libraryAutoSaveObserver.exclusive {
                    runCatching { libraryImporter.importFrom(homeUri) }
                }
                result.fold(
                    onSuccess = {
                        com.twinlore.app.ui.utils.restartApp(appContext)
                    },
                    onFailure = { t ->
                        if (t is com.twinlore.app.data.library.LibrarySnapshotTooNewException) {
                            // Surface to the user and DO NOT restart. Leaving
                            // the DB empty + the home folder URI set means
                            // every future cold start will hit this same
                            // branch until the user updates the app or
                            // re-points the home folder — which is fine: the
                            // dialog will re-render each time, no loop.
                            _librarySnapshotTooNew.value = LibrarySnapshotTooNew(
                                savedVersion = t.savedVersion,
                                supportedVersion = t.supportedVersion,
                            )
                        }
                        // Other failures: keep the legacy silent-skip
                        // behaviour. Auto-restore has never been guaranteed.
                    },
                )
            }
            PerfLog.mark("auto-restore check end")
            // Inside the launch block so the scan is ordered after
            // deviceIdMigrator.migrateIfNeeded() above (the coordinator also
            // self-orders via its own idempotent migrate call, but keeping the
            // happy path sequential avoids a redundant racing scan).
            librarySyncCoordinator.checkAsync()
        }
        // Media-index scanning no longer runs at startup. The cached index from
        // the previous session remains valid for displaying existing artwork
        // links. The index is refreshed on demand when a new vault is created
        // and its first local-media bind comes back empty (see
        // LocalMediaBinder.bindForVaultWithRescanIfEmpty), and on user-driven
        // actions: Settings "Rescan Local Media" and the ES-DE/Cocoon folder
        // pickers in LibraryViewModel. Removing the startup scan eliminates
        // ~2s of background IO that previously contended with first-frame work.
    }

    /**
     * Current application state observable by UI
     */
    val currentState: StateFlow<AppState> = stateManager.currentState

    /**
     * Sync prompt surfaces only on the library and settings screens —
     * whitelist, not blacklist, so it can never interrupt a VaultSession,
     * the vault-scoped Map/Guide viewers, Sleep, license activation, etc.
     * [LibrarySyncCoordinator] keeps [promptState] set so the dialog surfaces
     * automatically when the user returns to an allowed screen.
     */
    val syncPrompt: StateFlow<SyncPromptUi?> = combine(
        librarySyncCoordinator.promptState, stateManager.currentState
    ) { prompt, state ->
        prompt?.takeIf { state is AppState.Library || state is AppState.Settings }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // runCatching: a sync failure (torn snapshot, revoked SAF tree) must never
    // crash the app from a button tap; the coordinator's per-candidate
    // tolerance + idempotent re-merge recover on the next scan.
    fun onSyncAccepted() { viewModelScope.launch { runCatching { librarySyncCoordinator.sync() } } }
    fun onSyncDeclined() { viewModelScope.launch { runCatching { librarySyncCoordinator.decline() } } }

    /**
     * Pending All-Files access offer. Set once per cold start by the deferred check in
     * init; gated on screen the same way [syncPrompt] is, so it can never interrupt a
     * VaultSession, the vault-scoped viewers, Sleep or license activation.
     */
    val allFilesAccessPrompt: StateFlow<Boolean> = combine(
        _allFilesAccessPrompt, stateManager.currentState
    ) { pending, state ->
        pending && (state is AppState.Library || state is AppState.Settings)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /**
     * The user tapped Grant. Clear immediately rather than waiting on a resume
     * callback: if they grant, the next cold start's shouldPrompt sees it; if they back
     * out of Settings, they are not trapped behind a dialog they just acted on.
     */
    fun onAllFilesPromptOpened() { _allFilesAccessPrompt.value = false }

    /** The user tapped Not now. Sticky, so the offer never returns. */
    fun onAllFilesPromptDeclined() {
        _allFilesAccessPrompt.value = false
        viewModelScope.launch {
            runCatching { settingsRepository.setAllFilesPromptDeclined(true) }
        }
    }

    /**
     * Previous state before entering sleep mode
     */
    private var stateBeforeSleep: AppState? = null

    /**
     * When true, map bottom sheet was open when entering sleep (GuideViewer only).
     * Used to restore showMapBottomSheet when exiting sleep.
     */
    private var mapBottomSheetOpenBeforeSleep: Boolean = false

    /** One-shot: set to true when exiting sleep to GuideViewer with map sheet was open. UI clears after applying. */
    private val _restoreMapBottomSheetOpen = MutableStateFlow(false)
    val restoreMapBottomSheetOpen: StateFlow<Boolean> = _restoreMapBottomSheetOpen.asStateFlow()

    /**
     * Vault ID to focus when navigating to Library
     * This is set when navigating from a vault/browser/map back to library
     */
    private val _vaultToFocusInLibrary = MutableStateFlow<String?>(null)
    val vaultToFocusInLibrary: StateFlow<String?> = _vaultToFocusInLibrary.asStateFlow()
    
    /**
     * Clear the vault to focus in Library (called after it's been used)
     */
    fun clearVaultToFocusInLibrary() {
        _vaultToFocusInLibrary.value = null
    }

    /**
     * When box art is assigned from the browser, set this so Library can refresh and invalidate artwork for that vault.
     * LibraryScreen collects this and calls LibraryViewModel.notifyArtworkAssignedForVault then clears.
     */
    private val _pendingArtworkAssignedVaultId = MutableStateFlow<String?>(null)
    val pendingArtworkAssignedVaultId: StateFlow<String?> = _pendingArtworkAssignedVaultId.asStateFlow()

    fun notifyArtworkAssigned(vaultId: String) {
        _pendingArtworkAssignedVaultId.value = vaultId
    }

    /** Consumed by LibraryScreen; returns current value and clears it. */
    fun getAndClearArtworkAssignedVaultId(): String? {
        val current = _pendingArtworkAssignedVaultId.value
        _pendingArtworkAssignedVaultId.value = null
        return current
    }

    /** True once startup detection (sync latest played) has completed. Library waits for this before showing content so focus is correct from first frame. */
    private val _startupFocusComplete = MutableStateFlow(true)
    val startupFocusComplete: StateFlow<Boolean> = _startupFocusComplete.asStateFlow()

    /** When true, Library screen should run the same refresh as the Refresh button (unified auto-refresh experience). */
    private val _triggerRefreshWhenOnLibrary = MutableStateFlow(false)
    val triggerRefreshWhenOnLibrary: StateFlow<Boolean> = _triggerRefreshWhenOnLibrary.asStateFlow()

    fun clearTriggerRefreshWhenOnLibrary() {
        _triggerRefreshWhenOnLibrary.value = false
    }

    /**
     * Lightweight trigger: focus this vault on the Library and raise the showcase
     * without running detection. Used when the tracked log has nothing new and the
     * now-playing record already names the game. Consumed by LibraryScreen.
     */
    private val _vaultToShowcase = MutableStateFlow<String?>(null)
    val vaultToShowcase: StateFlow<String?> = _vaultToShowcase.asStateFlow()

    /**
     * The Always on Top panel's Support button asked for the support dialog,
     * which its window cannot show. Raised on the foreground that follows the
     * handover, cleared by the dialog. See [com.twinlore.app.ui.fold.FoldHandover.armSupport].
     */
    private val _supportPromptRequested = MutableStateFlow(false)
    val supportPromptRequested: StateFlow<Boolean> = _supportPromptRequested.asStateFlow()

    fun dismissSupportPrompt() {
        _supportPromptRequested.value = false
    }

    fun clearVaultToShowcase() {
        _vaultToShowcase.value = null
    }

    /** When true, the current navigation to Library was triggered by a user back-press; suppress pending auto-refresh. */
    private val _backPressNavigating = MutableStateFlow(false)
    val backPressNavigating: StateFlow<Boolean> = _backPressNavigating.asStateFlow()

    /**
     * Title of the document currently open in each vault-session viewer, keyed by
     * (vaultId, tab). Per-tab buckets — not a single shared slot — because Crossfade
     * keeps the outgoing viewer composed during the transition; if all three viewers
     * wrote to the same key, the disposing one's clear would race past the new one's
     * publish. VaultSessionScreen reads the bucket for the current activeTab.
     * Null means no document is active for that viewer (picker open, landing page,
     * no map selected, etc.).
     */
    private val _activeDocTitle = MutableStateFlow<Map<Pair<String, VaultTab>, String?>>(emptyMap())
    val activeDocTitle: StateFlow<Map<Pair<String, VaultTab>, String?>> = _activeDocTitle.asStateFlow()
    fun setActiveDocTitle(vaultId: String, tab: VaultTab, title: String?) {
        _activeDocTitle.value = _activeDocTitle.value + ((vaultId to tab) to title)
    }
    /** Timestamp of last back-press navigation — file watcher debounce can outlive the boolean flag. */
    private var backPressNavigatingTimestamp = 0L

    fun clearBackPressNavigating() {
        _backPressNavigating.value = false
    }

    /**
     * Called from MainActivity.onResume for every resume after the first frame.
     * Cold start is NOT driven from here: on a cold start onResume fires before the
     * ViewModel exists (it is acquired during composition), so init owns the
     * cold-start pass and this only ever sees genuine returns to the foreground.
     */
    fun onAppForegrounded() {
        if (foldHandover.consumeSupport()) _supportPromptRequested.value = true
        viewModelScope.launch { handleOpenLastPlayed(isColdStart = false) }
    }

    /**
     * Open-last-played-on-launch: decide whether an automatic detection pass should
     * run, and whether the result should be shown as a full Library refresh or a
     * lightweight showcase of the already-recorded vault. Cold start always proceeds,
     * and so does a foreground while already on the Library (it always reopens the
     * current or recorded game there). A foreground from elsewhere in the app only
     * proceeds when a new launch happened since the last handled one, and is skipped
     * entirely from the Settings/AppCatalog configuration surfaces. The 5s back-press
     * guard is deliberately not applied here: it protects against in-app back
     * navigation racing the watcher, which cannot coincide with an app-level
     * background/foreground cycle.
     */
    private suspend fun handleOpenLastPlayed(isColdStart: Boolean) {
        // A return the fold panel asked for is already aimed somewhere. Reading
        // this clears it, so exactly one pass stands down: the one racing the
        // handover it was left for.
        if (!isColdStart && foldHandover.consume()) return
        // Overlay mode was on, so a cold start goes back there when it can name
        // the vault. See resumeOverlayOnColdStart.
        val resumeOverlay = isColdStart && overlayMemory.wasOverlayMode()
        val source = if (settingsRepository.openLastPlayedOnLaunch.first()) {
            settingsRepository.lastPlayedSource.first()
        } else {
            LastPlayedSource.NOT_CONFIGURED
        }
        if (source == LastPlayedSource.NOT_CONFIGURED) {
            // No detection to ask, so the game last recorded is the answer.
            if (resumeOverlay) resumeOverlayOnColdStart(settingsRepository.nowPlayingVaultId.first())
            return
        }
        autoRefreshSyncMutex.withLock {
            val isFileSource = source == LastPlayedSource.ES_DE ||
                source == LastPlayedSource.COCOON ||
                source == LastPlayedSource.RETROARCH
            val folderUri = when (source) {
                LastPlayedSource.ES_DE -> settingsRepository.esdeFolderUri.first()
                LastPlayedSource.COCOON -> settingsRepository.cocoonFolderUri.first()
                LastPlayedSource.RETROARCH -> settingsRepository.retroArchHistoryFolder.first()
                else -> null
            }
            val marker = if (isFileSource) {
                contentUriFileWatcher.readLaunchMarker(folderUri, source)
            } else null
            val handledMarker = settingsRepository.lastHandledLaunchMarker.first()
            // Achievements has no file to pre-check; its "is anything new" question is
            // answered by running detection, so it always takes the detection branches.
            val newLaunch = isFileSource && marker != null && marker != handledMarker
            if (isFileSource) {
                // Attempt-time, success or failure: a broken log must not retry forever.
                settingsRepository.setLastHandledLaunchMarker(marker)
            }

            if (resumeOverlay) {
                val detected = if (newLaunch || !isFileSource) {
                    syncLatestPlayedGameUseCase.resolveExisting(uriOverride = folderUri)
                } else {
                    settingsRepository.nowPlayingVaultId.first()
                }
                if (syncLatestPlayedGameUseCase.pendingFuzzyMatch.value != null) {
                    // A guess needs the library's confirmation dialog, which the
                    // panel cannot host. Dropped here so the Library's own
                    // detection below raises it again, with creation allowed.
                    syncLatestPlayedGameUseCase.dismissFuzzyMatch()
                    overlayMemory.setOverlayMode(false)
                } else if (resumeOverlayOnColdStart(detected)) {
                    return
                }
                // Not in the library yet, or nothing to show: the ordinary
                // cold start, which adds it with the app in front.
            }

            val state = stateManager.getCurrentState()
            if (isColdStart || state is AppState.Library) {
                if (newLaunch || !isFileSource) {
                    // Full detection transition on the Library. scrapeAllBoxArt owns
                    // resolution, creation, the fuzzy dialog, and the now-playing record.
                    _triggerRefreshWhenOnLibrary.value = true
                } else {
                    // Nothing new: always reopen the recorded game on the Library.
                    val recorded = settingsRepository.nowPlayingVaultId.first() ?: return
                    _vaultToShowcase.value = recorded
                }
                return
            }

            // Somewhere else (vault session, viewers, settings): find the candidate.
            // Settings and AppCatalog are configuration surfaces the user is actively
            // using; an activity-result round trip from their own SAF folder picker or
            // a permission dialog fires onResume just like a real app switch, and is
            // indistinguishable from one here. Bouncing the user into a vault while
            // they are mid-configuration would be jarring, so skip entirely.
            if (state is AppState.Settings || state is AppState.AppCatalog) return

            val candidate = if (newLaunch || !isFileSource) {
                syncLatestPlayedGameUseCase.execute(uriOverride = folderUri)
            } else {
                settingsRepository.nowPlayingVaultId.first()
            } ?: return
            if (syncLatestPlayedGameUseCase.pendingFuzzyMatch.value != null) {
                // An unconfirmed fuzzy candidate must not be showcased as certain; the
                // Library owns the confirmation dialog.
                return
            }
            val currentVaultId = when (state) {
                is AppState.VaultSession -> state.vaultId
                is AppState.MapViewer -> state.vaultId
                is AppState.GuideViewer -> state.vaultId
                else -> null
            }
            if (candidate == currentVaultId) return
            _vaultToShowcase.value = candidate
            // Verify the bounce actually landed: isValidTransition has no rule for some
            // origin states (e.g. RetroAchievementsProfile -> Library), so setState can
            // silently fail. Leaving the signal live in that case would fire minutes
            // later when the user eventually navigates to Library themselves.
            if (!navigateToLibrarySuspend(focusedVaultId = candidate)) {
                _vaultToShowcase.value = null
            }
        }
    }

    /**
     * Cold start with overlay mode on: opens [vaultId]'s session marked for
     * overlay, so the session's OverlayRestore folds it straight back with the
     * panel up. Launched from a frontend beside a dual-screen
     * game, the activity is under the game's presentation window, and the
     * overlay is the only part of the app that can be seen there.
     *
     * Only for a vault that exists. Anything else clears the flag and returns
     * false, and the caller carries on with the ordinary cold start: the app
     * comes up as it would have, and overlay mode is over.
     */
    private suspend fun resumeOverlayOnColdStart(vaultId: String?): Boolean {
        if (vaultId == null || vaultRepository.getVaultById(vaultId) == null) {
            overlayMemory.setOverlayMode(false)
            return false
        }
        foldHandover.armColdStartFold(vaultId)
        openVault(vaultId)
        return true
    }

    /**
     * Called by LibraryScreen when the focused vault in the library carousel changes.
     * Used to know the "active vault" when state is Library for auto-refresh (same vault = no navigate).
     */
    fun setLibraryFocusedVaultId(vaultId: String?) {
        _libraryFocusedVaultId.value = vaultId
    }

    /**
     * Request to open the context menu for the currently focused vault in Library.
     * Used when the user presses the controller button that in GuideViewer toggles map/browser (Start/Menu).
     */
    fun requestOpenLibraryContextMenu() {
        viewModelScope.launch {
            _requestOpenLibraryContextMenu.emit(Unit)
        }
    }

    fun requestCycleLibraryViewMode(forward: Boolean = true) {
        viewModelScope.launch {
            _requestCycleLibraryViewMode.emit(forward)
        }
    }

    fun requestCycleLibrarySort(forward: Boolean = true) {
        viewModelScope.launch {
            _requestCycleLibrarySort.emit(forward)
        }
    }

    fun requestRefreshLibrary() {
        viewModelScope.launch {
            _requestRefreshLibrary.emit(Unit)
        }
    }

    fun requestLaunchFocusedVault() {
        viewModelScope.launch {
            _requestLaunchFocusedVault.emit(Unit)
        }
    }

    /** One-shot request to restore focus to the bottom bar (e.g. after TOC/find-on-page closes). */
    private val _requestBottomBarFocus = MutableSharedFlow<Unit>()
    val requestBottomBarFocus: SharedFlow<Unit> = _requestBottomBarFocus.asSharedFlow()

    fun requestBottomBarFocus() {
        viewModelScope.launch {
            _requestBottomBarFocus.emit(Unit)
        }
    }

    /**
     * Controller input state for map panning and zooming
     * Pan values: -1.0 to 1.0 (left stick X/Y and D-pad)
     * Zoom value: -1.0 to 1.0 (right stick Y, negative = zoom in, positive = zoom out)
     */
    data class ControllerInputState(
        val panX: Float = 0f,
        val panY: Float = 0f,
        val zoom: Float = 0f
    )
    
    private val _controllerInput = MutableStateFlow(ControllerInputState())
    val controllerInput: StateFlow<ControllerInputState> = _controllerInput.asStateFlow()
    
    /**
     * Update controller input state (called from MainActivity.onGenericMotionEvent)
     */
    fun updateControllerInput(panX: Float, panY: Float, zoom: Float) {
        _controllerInput.value = ControllerInputState(panX, panY, zoom)
    }
    
    /**
     * Marker navigation direction for R1/L1 button presses
     */
    enum class MarkerNavigationDirection {
        NEXT, PREVIOUS, NONE
    }
    
    private val _markerNavigation = MutableStateFlow(MarkerNavigationDirection.NONE)
    val markerNavigation: StateFlow<MarkerNavigationDirection> = _markerNavigation.asStateFlow()
    
    /**
     * Trigger marker navigation (called from MainActivity on R1/L1 button press)
     */
    fun navigateMarker(direction: MarkerNavigationDirection) {
        _markerNavigation.value = direction
        // Reset to NONE after a short delay to allow single press detection
        viewModelScope.launch {
            kotlinx.coroutines.delay(100)
            _markerNavigation.value = MarkerNavigationDirection.NONE
        }
    }
    
    /**
     * Marker filter navigation direction for L2/R2 button presses (map bottom sheet only).
     * NEXT = next filter (e.g. R2), PREVIOUS = previous filter (e.g. L2).
     */
    enum class MarkerFilterNavigationDirection {
        NEXT, PREVIOUS, NONE
    }
    
    private val _markerFilterNavigation = MutableStateFlow(MarkerFilterNavigationDirection.NONE)
    val markerFilterNavigation: StateFlow<MarkerFilterNavigationDirection> = _markerFilterNavigation.asStateFlow()
    
    /** One-shot trigger for L3 "add marker at center" (map bottom sheet only). */
    private val _addMarkerAtCenterTrigger = MutableStateFlow(false)
    val addMarkerAtCenterTrigger: StateFlow<Boolean> = _addMarkerAtCenterTrigger.asStateFlow()
    
    fun triggerAddMarkerAtCenter() {
        _addMarkerAtCenterTrigger.value = true
        viewModelScope.launch {
            kotlinx.coroutines.delay(100)
            _addMarkerAtCenterTrigger.value = false
        }
    }
    
    /**
     * Global controller actions for quick navigation
     */
    enum class ControllerAction {
        TOGGLE_MAP_BROWSER,
        OPEN_FAVORITE_MENU,
        FOCUS_SEARCH_BAR,
        OPEN_FIND_ON_PAGE,
        OPEN_TOC,
        FIND_PREVIOUS_RESULT,
        FIND_NEXT_RESULT,
        BROWSER_BACK,
        BROWSER_FORWARD,
        BROWSER_SCROLL_MARKER_PREV,
        BROWSER_SCROLL_MARKER_NEXT,
        BROWSER_PREV_BOOKMARK,
        BROWSER_NEXT_BOOKMARK,
        BROWSER_SCROLL_TO_TOP,
        BROWSER_SCROLL_TO_END,
        BROWSER_RESET_ZOOM,
        MAP_NEXT_FILE,
        MAP_PREV_FILE,
        ACHIEVEMENTS_REFRESH,
        ACHIEVEMENTS_WALLPAPER,
        CHECKLIST_ADD_ITEM,
        CHECKLIST_TAB_NEXT,
        CHECKLIST_TAB_PREV,
        NONE
    }
    
    private val _controllerAction = MutableStateFlow(ControllerAction.NONE)
    val controllerAction: StateFlow<ControllerAction> = _controllerAction.asStateFlow()
    
    /**
     * Trigger a controller action (called from MainActivity)
     */
    fun triggerControllerAction(action: ControllerAction) {
        _controllerAction.value = action
        // Reset to NONE after a short delay to allow single press detection
        viewModelScope.launch {
            kotlinx.coroutines.delay(100)
            _controllerAction.value = ControllerAction.NONE
        }
    }
    
    /**
     * Whether the map bottom sheet is visible (GuideViewer with map shown).
     * Used to route D-pad/stick: map visible → controllerInput (pan/zoom), else → browser scroll/pan.
     */
    private val _isMapBottomSheetVisible = MutableStateFlow(false)
    val isMapBottomSheetVisible: StateFlow<Boolean> = _isMapBottomSheetVisible.asStateFlow()
    
    /**
     * Whether the bookmark management dialog is visible (GuideViewer).
     * When true, X is not mapped to OPEN_FAVORITE_MENU so the dialog can use X for delete.
     */
    private val _isBookmarkDialogVisible = MutableStateFlow(false)
    val isBookmarkDialogVisible: StateFlow<Boolean> = _isBookmarkDialogVisible.asStateFlow()

    fun setBookmarkDialogVisible(visible: Boolean) {
        _isBookmarkDialogVisible.value = visible
    }

    /**
     * Whether the Find on Page overlay is visible (GuideViewer).
     * When true, Y is not used to open it; L1/R1 map to previous/next result instead of browser back/forward.
     */
    private val _isFindOnPageVisible = MutableStateFlow(false)
    val isFindOnPageVisible: StateFlow<Boolean> = _isFindOnPageVisible.asStateFlow()

    fun setFindOnPageVisible(visible: Boolean) {
        _isFindOnPageVisible.value = visible
        if (!visible) _isFindOnPageImeVisible.value = false
    }

    /** When true, find-on-page menu keys (X, L1, R1) are not consumed by the activity so the IME can use them (e.g. X = backspace, A = confirm). */
    private val _isFindOnPageImeVisible = MutableStateFlow(false)
    val isFindOnPageImeVisible: StateFlow<Boolean> = _isFindOnPageImeVisible.asStateFlow()

    /** When navigating to RA Profile, vault ID to return to (null = return to Library). */
    private val _raProfileReturnToVaultId = MutableStateFlow<String?>(null)

    fun setFindOnPageImeVisible(visible: Boolean) {
        _isFindOnPageImeVisible.value = visible
    }

    /** Callbacks for L1/R1 and Square (save marker) when Find on Page is visible; set by GuideBrowserScreen. */
    private var findOnPageOnNext: (() -> Unit)? = null
    private var findOnPageOnPrevious: (() -> Unit)? = null
    private var findOnPageOnSaveMarker: (() -> Unit)? = null

    fun setFindOnPageActions(onNext: (() -> Unit)?, onPrevious: (() -> Unit)?, onSaveMarker: (() -> Unit)? = null) {
        findOnPageOnNext = onNext
        findOnPageOnPrevious = onPrevious
        findOnPageOnSaveMarker = onSaveMarker
    }

    fun triggerFindNext() {
        findOnPageOnNext?.invoke()
    }

    fun triggerFindPrevious() {
        findOnPageOnPrevious?.invoke()
    }

    /**
     * Whether the "No Map Available" menu is visible (map sheet or MapViewer with no map).
     * When true, D-pad/left stick are not used for map pan; they propagate for focus navigation.
     */
    private val _isNoMapMenuVisible = MutableStateFlow(false)
    val isNoMapMenuVisible: StateFlow<Boolean> = _isNoMapMenuVisible.asStateFlow()

    /**
     * Vault session Map tab: when false, top bar + bottom bar are hidden (immersive).
     * Reset when leaving the Map tab.
     */
    private val _vaultSessionMapChromeVisible = MutableStateFlow(true)
    val vaultSessionMapChromeVisible: StateFlow<Boolean> = _vaultSessionMapChromeVisible.asStateFlow()

    fun setVaultSessionMapChromeVisible(visible: Boolean) {
        _vaultSessionMapChromeVisible.value = visible
    }

    /**
     * Vault session Guide tab: when false, bottom bar is hidden (same as top bar).
     * Reset when leaving the Guide tab.
     */
    private val _vaultSessionGuideChromeVisible = MutableStateFlow(true)
    val vaultSessionGuideChromeVisible: StateFlow<Boolean> = _vaultSessionGuideChromeVisible.asStateFlow()

    fun setVaultSessionGuideChromeVisible(visible: Boolean) {
        _vaultSessionGuideChromeVisible.value = visible
    }

    /**
     * Vault session Guide tab: true while a guide overlay (TOC drawer / full-screen TOC) is
     * open. These overlays force the chrome hidden while they own dpad focus, so the
     * "chrome hidden → first key press wakes chrome and is consumed" logic in MainActivity
     * and VaultSessionScreen must stand down — otherwise every dpad press is swallowed and
     * focus can never move inside the overlay.
     */
    private val _vaultSessionGuideOverlayOpen = MutableStateFlow(false)
    val vaultSessionGuideOverlayOpen: StateFlow<Boolean> = _vaultSessionGuideOverlayOpen.asStateFlow()

    fun setVaultSessionGuideOverlayOpen(open: Boolean) {
        _vaultSessionGuideOverlayOpen.value = open
    }

    /**
     * Vault session Browser tab: when false, bottom bar is hidden (same as top bar).
     * Reset when leaving the Browser tab.
     */
    private val _vaultSessionBrowserChromeVisible = MutableStateFlow(true)
    val vaultSessionBrowserChromeVisible: StateFlow<Boolean> = _vaultSessionBrowserChromeVisible.asStateFlow()

    fun setVaultSessionBrowserChromeVisible(visible: Boolean) {
        _vaultSessionBrowserChromeVisible.value = visible
    }

    /**
     * True when the browser's URL field is currently focused. VaultSessionScreen uses this to
     * keep the outer game-name top bar visible even when the IME is up (the default
     * !isKeyboardVisible gate would otherwise hide it, dropping the page-name context while
     * the user is mid-search).
     */
    private val _browserUrlFieldFocused = MutableStateFlow(false)
    val browserUrlFieldFocused: StateFlow<Boolean> = _browserUrlFieldFocused.asStateFlow()

    fun setBrowserUrlFieldFocused(focused: Boolean) {
        _browserUrlFieldFocused.value = focused
    }

    /**
     * Vault session Achievements tab: when false, bottom bar is hidden.
     * Auto-hides after inactivity, shown on touch/D-pad interaction.
     */
    private val _vaultSessionAchievementsChromeVisible = MutableStateFlow(true)
    val vaultSessionAchievementsChromeVisible: StateFlow<Boolean> = _vaultSessionAchievementsChromeVisible.asStateFlow()

    fun setVaultSessionAchievementsChromeVisible(visible: Boolean) {
        _vaultSessionAchievementsChromeVisible.value = visible
    }

    /**
     * Vault session Checklist tab: when false, bottom bar is hidden.
     * Auto-hides after inactivity, shown on touch/D-pad interaction.
     */
    private val _vaultSessionChecklistChromeVisible = MutableStateFlow(true)
    val vaultSessionChecklistChromeVisible: StateFlow<Boolean> = _vaultSessionChecklistChromeVisible.asStateFlow()

    fun setVaultSessionChecklistChromeVisible(visible: Boolean) {
        _vaultSessionChecklistChromeVisible.value = visible
    }

    private val _vaultSessionAiChromeVisible = MutableStateFlow(true)
    val vaultSessionAiChromeVisible: StateFlow<Boolean> = _vaultSessionAiChromeVisible.asStateFlow()

    fun setVaultSessionAiChromeVisible(visible: Boolean) {
        _vaultSessionAiChromeVisible.value = visible
    }

    /**
     * The currently visible vault tabs (set by VaultSessionScreen based on credentials).
     * Used by [cycleVaultSessionTab] to skip hidden tabs (e.g. Achievements when no credentials).
     */
    private val _visibleVaultTabs = MutableStateFlow(VaultTab.entries.toList())
    val visibleVaultTabs: StateFlow<List<VaultTab>> = _visibleVaultTabs.asStateFlow()

    fun setVisibleVaultTabs(tabs: List<VaultTab>) {
        _visibleVaultTabs.value = tabs
    }

    /**
     * Emitted when user interacts with top or bottom menu (e.g. bottom bar button).
     * Tabs observe this to reset their auto-hide timer.
     */
    private val _chromeInteractionTrigger = MutableStateFlow(0)
    val chromeInteractionTrigger: StateFlow<Int> = _chromeInteractionTrigger.asStateFlow()

    fun notifyChromeInteraction() {
        _chromeInteractionTrigger.value++
    }

    /** Signal the Activity to stop the hat D-pad repeat handler. */
    private val _stopHatRepeatTrigger = MutableStateFlow(0)
    val stopHatRepeatTrigger: StateFlow<Int> = _stopHatRepeatTrigger.asStateFlow()

    /**
     * Automatically tracked by a root-level focus observer on VaultSessionScreen.
     * When ANY Compose element (or its children) has focus, D-pad events fall through to
     * Compose for focus traversal. When nothing has focus, D-pad is intercepted for scroll/pan.
     * Replaces the old manual `dpadFocusMode` toggle.
     */
    private val _hasComposeFocus = MutableStateFlow(false)
    val hasComposeFocus: StateFlow<Boolean> = _hasComposeFocus.asStateFlow()

    fun updateComposeFocusState(hasFocus: Boolean) {
        _hasComposeFocus.value = hasFocus
    }

    /**
     * Browser scroll Y: positive = scroll down, negative = scroll up.
     */
    private val _browserScroll = MutableStateFlow(0f)
    val browserScroll: StateFlow<Float> = _browserScroll.asStateFlow()

    fun updateBrowserScroll(scrollValue: Float) {
        _browserScroll.value = scrollValue
    }

    /**
     * Left-stick Y axis for the vault session Achievements and Checklist tabs.
     * Range roughly [-1, 1]; consumers apply a deadzone and a px/frame multiplier.
     * Cleared to 0f when sticks are at rest by MainActivity.dispatchGenericMotionEvent.
     */
    private val _vaultSessionListScroll = MutableStateFlow(0f)
    val vaultSessionListScroll: StateFlow<Float> = _vaultSessionListScroll.asStateFlow()

    fun updateVaultSessionListScroll(value: Float) {
        _vaultSessionListScroll.value = value
    }

    /**
     * WebView current scroll position (pixels) — mirrored from GuideBrowserViewModel
     * so dispatchKeyEvent can check scroll boundaries for focus escape.
     */
    private val _webViewScrollY = MutableStateFlow(0)
    val webViewScrollY: StateFlow<Int> = _webViewScrollY.asStateFlow()

    private val _webViewMaxScrollY = MutableStateFlow(0)
    val webViewMaxScrollY: StateFlow<Int> = _webViewMaxScrollY.asStateFlow()

    fun updateWebViewScrollState(scrollY: Int, maxScrollY: Int) {
        _webViewScrollY.value = scrollY
        _webViewMaxScrollY.value = maxScrollY.coerceAtLeast(0)
    }
    
    /**
     * Browser pan X: positive = pan right, negative = pan left (horizontal scroll when zoomed).
     */
    private val _browserPanX = MutableStateFlow(0f)
    val browserPanX: StateFlow<Float> = _browserPanX.asStateFlow()
    
    fun updateBrowserPan(panX: Float) {
        _browserPanX.value = panX
    }
    
    /**
     * Browser zoom from right stick: negative = zoom in, positive = zoom out (same as map).
     */
    private val _browserZoom = MutableStateFlow(0f)
    val browserZoom: StateFlow<Float> = _browserZoom.asStateFlow()
    
    fun updateBrowserZoom(zoomValue: Float) {
        _browserZoom.value = zoomValue
    }

    /**
     * Navigate to Library screen
     * @param focusedVaultId Optional vault ID to focus when navigating to Library
     */
    fun navigateToLibrary(focusedVaultId: String? = null) {
        viewModelScope.launch { navigateToLibrarySuspend(focusedVaultId) }
    }

    /**
     * Suspend core of [navigateToLibrary], returning whether the transition actually
     * took. Callers that need to know (e.g. [handleOpenLastPlayed], which must not
     * leave an armed signal live when the bounce silently failed) call this directly
     * instead of the fire-and-forget wrapper.
     */
    private suspend fun navigateToLibrarySuspend(focusedVaultId: String? = null): Boolean {
        // Clear pending URL for the vault we're leaving so the map doesn't grow when returning without opening Browser tab
        if (focusedVaultId != null) {
            _pendingUrlForBrowserTab.value = _pendingUrlForBrowserTab.value - focusedVaultId
        }
        // Set the vault to focus if provided
        if (focusedVaultId != null) {
            _vaultToFocusInLibrary.value = focusedVaultId
        }

        return stateManager.setState(AppState.Library)
    }

    /**
     * Open Vault - opens Vault Session with 4 tabs (Guide, Map, Achievements, Browser).
     * Restores last active tab from vaultModeStateManager (saved on tab switch or when leaving).
     * @param vaultId The unique identifier of the vault
     */
    fun openVault(vaultId: String) {
        viewModelScope.launch {
            PerfLog.mark("openVault begin vaultId=$vaultId")
            // Pre-flight: emit _vaultMissing if the vault row was deleted out from
            // under us. Resource-file existence is checked lazily by ResourceResolver
            // when individual screens render.
            val vault = vaultRepository.getVaultById(vaultId)
            if (vault == null) {
                _vaultMissing.tryEmit(vaultId)
                return@launch
            }
            val tab = vaultModeStateManager.getLastTab(vaultId) ?: VaultTab.Guide
            val artworkUri = vault.artworkResourceId?.let {
                kotlinx.coroutines.withContext(Dispatchers.IO) {
                    resourceResolver.resolve(it)?.toString()
                }
            }
            PerfLog.mark("openVault artworkResolved uri=${artworkUri != null}")
            stateManager.setState(
                AppState.VaultSession(vaultId, tab, artworkUri, vault.artworkVibrantArgb)
            )
            PerfLog.mark("openVault stateSet")
        }
    }

    /**
     * Navigate to Vault Session (used e.g. when returning from RA profile).
     */
    fun navigateToVaultSession(vaultId: String, tab: VaultTab = VaultTab.Guide) {
        viewModelScope.launch {
            stateManager.setState(AppState.VaultSession(vaultId, tab))
        }
    }

    /**
     * Switch active tab within current Vault Session. No-op if not in VaultSession or vaultId mismatch.
     * Saves the selected tab so it is restored when returning from the library.
     */
    fun setVaultSessionTab(vaultId: String, tab: VaultTab) {
        viewModelScope.launch {
            val current = stateManager.getCurrentState()
            if (current is AppState.VaultSession && current.vaultId == vaultId) {
                vaultModeStateManager.saveLastTab(vaultId, tab)
                stateManager.setState(AppState.VaultSession(vaultId, tab, current.artworkUri, current.accentArgb))
            }
        }
    }

    /**
     * Cycle to next/previous tab within current Vault Session (Start/Select quick switch).
     * Emits [tabSwitchedViaController] so the UI can move focus to the content area.
     */
    fun cycleVaultSessionTab(vaultId: String, forward: Boolean) {
        viewModelScope.launch {
            val current = stateManager.getCurrentState()
            if (current is AppState.VaultSession && current.vaultId == vaultId) {
                val tabs = _visibleVaultTabs.value
                val curIndex = tabs.indexOf(current.activeTab).coerceAtLeast(0)
                val nextIndex = if (forward) (curIndex + 1) % tabs.size else (curIndex - 1 + tabs.size) % tabs.size
                val nextTab = tabs[nextIndex]
                vaultModeStateManager.saveLastTab(vaultId, nextTab)
                stateManager.setState(AppState.VaultSession(vaultId, nextTab, current.artworkUri, current.accentArgb))
                _tabSwitchedViaController.tryEmit(nextTab)
            }
        }
    }

    /** One-shot event: tab was switched via controller (Start/Select). UI should move focus to content area. */
    private val _tabSwitchedViaController = MutableSharedFlow<VaultTab>(extraBufferCapacity = 1)
    val tabSwitchedViaController: SharedFlow<VaultTab> = _tabSwitchedViaController.asSharedFlow()

    /** Fired when the user taps the already-active tab in the bottom bar (one-shot event). */
    private val _tabReselected = MutableSharedFlow<VaultTab>(extraBufferCapacity = 1)
    val tabReselected: SharedFlow<VaultTab> = _tabReselected.asSharedFlow()

    fun emitTabReselected(tab: VaultTab) {
        _tabReselected.tryEmit(tab)
    }

    /**
     * Page-group currently open inside each vault's Guide tab (vaultId → groupTabId).
     * When set, `GuideTabContent` renders the page-group viewer in the Guide-tab slot
     * so the vault session's top bar + bottom bar + chrome plumbing surround it like
     * any other Guide content. Cleared by [closePageGroupForVault] (or the viewer's
     * back button).
     */
    private val _openPageGroupByVault = MutableStateFlow<Map<String, String>>(emptyMap())
    val openPageGroupByVault: StateFlow<Map<String, String>> = _openPageGroupByVault.asStateFlow()

    fun closePageGroupForVault(vaultId: String) {
        _openPageGroupByVault.value = _openPageGroupByVault.value - vaultId
    }

    /**
     * Tracks which vaults have already had their "open last guide" dispatch
     * run during this process lifetime. Guards [maybeRestoreActiveGroupForVault]
     * against re-firing on rotation / config changes while still allowing a
     * fresh dispatch after process death.
     */
    private val autoRestoredGroupVaults = mutableSetOf<String>()

    /**
     * Per-vault gate: true once `maybeRestoreActiveGroupForVault(vaultId)` has
     * finished its async lookup. Used by GuideTabContent to suppress mounting
     * the OLD GuideScreen while the restore is in flight — without this gate
     * the OLD viewer flashes briefly between vault-entry and the new viewer
     * taking over.
     */
    private val _groupRestoreDone = MutableStateFlow<Set<String>>(emptySet())
    val groupRestoreDone: StateFlow<Set<String>> = _groupRestoreDone.asStateFlow()

    /**
     * If the vault's persisted active bookmark is a page-group, dispatch to
     * the page-group viewer (which sets [_openPageGroupByVault]). No-op for
     * single-page bookmarks — [GuideScreen]'s own SINGLE RESTORE PATH handles
     * those. One-shot per process per vault.
     *
     * Called from [com.twinlore.app.ui.screens.guide.GuideTabContent] on
     * first vault entry so a group resume happens before [GuideScreen]
     * mounts and tries to render the group as an HTML bookmark.
     */
    fun maybeRestoreActiveGroupForVault(vaultId: String) {
        if (vaultId in autoRestoredGroupVaults) return
        autoRestoredGroupVaults.add(vaultId)
        viewModelScope.launch {
            try {
                // If a group is already open for this vault (e.g. user navigated
                // here from the picker before this effect ran), nothing to do.
                if (_openPageGroupByVault.value.containsKey(vaultId)) return@launch
                val bookmarkId = guideViewStateManager.getActiveBookmarkId(vaultId) ?: return@launch
                val tab = savedTabRepository.getTabById(bookmarkId) ?: return@launch
                if (tab.fileType.equals("group", ignoreCase = true)) {
                    _openPageGroupByVault.value =
                        _openPageGroupByVault.value + (vaultId to bookmarkId)
                }
            } finally {
                _groupRestoreDone.value = _groupRestoreDone.value + vaultId
            }
        }
    }

    /**
     * Single-guide bookmark currently open inside each vault's Guide tab
     * (vaultId → SavedTab). When set, [com.twinlore.app.ui.screens.guide.GuideTabContent]
     * renders [com.twinlore.app.ui.screens.guide.SingleGuideViewerScreen] in
     * the Guide-tab slot. Cleared by [closeSingleGuideForVault] or the
     * viewer's back button.
     */
    private val _openSingleGuideByVault = MutableStateFlow<Map<String, com.twinlore.app.domain.SavedTab>>(emptyMap())
    val openSingleGuideByVault: StateFlow<Map<String, com.twinlore.app.domain.SavedTab>> =
        _openSingleGuideByVault.asStateFlow()

    fun openSingleGuideForVault(vaultId: String, bookmark: com.twinlore.app.domain.SavedTab) {
        // Apply the in-memory state changes synchronously so the very next
        // composition (e.g. after a navigateToVaultSession from a save-banner's
        // Open tap) sees the populated map. Without this, GuideTabContent
        // recomposes before the launch lands and falls through to GuideScreen
        // (black screen until the launch finally lands).
        _openPageGroupByVault.value = _openPageGroupByVault.value - vaultId
        _openSingleGuideByVault.value = _openSingleGuideByVault.value + (vaultId to bookmark)
        viewModelScope.launch {
            guideViewStateManager.saveActiveBookmarkId(vaultId, bookmark.id)
            savedTabRepository.updateLastAccessed(bookmark.id)
        }
    }

    /**
     * Open a freshly-downloaded PDF in this vault's Guide tab.
     *
     * PDFs render through [com.twinlore.app.ui.screens.guide.GuideScreen]'s
     * `activePdfGuide` slot, not the MHT-only [SingleGuideViewerScreen]. So
     * the post-save Open banner can't reuse [openSingleGuideForVault] — that
     * would route the PDF through the MHT cache parser and the user would
     * see "Guide not available".
     *
     * Order matters for perceived latency:
     *  1. Clear both viewer dispatch maps synchronously — GuideTabContent
     *     will mount GuideScreen on the next composition.
     *  2. Queue the bookmark id via the pending pattern so GuideScreen's
     *     reactive consumer loads it once mounted.
     *  3. Switch to Guide tab *eagerly* (preserving artwork/accent) rather
     *     than letting GuideScreen's consumer do it after the file polls
     *     resolve. Without the eager switch, the user sees the Browser tab
     *     for multiple seconds while SAF resolves the just-downloaded PDF.
     */
    fun openPdfForVault(vaultId: String, bookmark: com.twinlore.app.domain.SavedTab) {
        _openPageGroupByVault.value = _openPageGroupByVault.value - vaultId
        _openSingleGuideByVault.value = _openSingleGuideByVault.value - vaultId
        // Mark the vault as "loading a PDF" so GuideTabContent can paint a
        // Loading overlay over the empty Guide tab until PdfGuideViewer's
        // first composition calls [notifyPdfReady]. Set synchronously so the
        // overlay is visible on the very next composition (same frame as the
        // eager tab switch below).
        _pdfLoadingByVault.value = _pdfLoadingByVault.value + vaultId
        setPendingGuideBookmarkId(vaultId, bookmark.id)
        viewModelScope.launch {
            val current = stateManager.getCurrentState()
            if (current is AppState.VaultSession && current.vaultId == vaultId &&
                current.activeTab != VaultTab.Guide
            ) {
                vaultModeStateManager.saveLastTab(vaultId, VaultTab.Guide)
                stateManager.setState(
                    AppState.VaultSession(
                        vaultId,
                        VaultTab.Guide,
                        current.artworkUri,
                        current.accentArgb
                    )
                )
            }
            guideViewStateManager.saveActiveBookmarkId(vaultId, bookmark.id)
            savedTabRepository.updateLastAccessed(bookmark.id)
        }
        // Safety: clear the loading flag after a generous timeout so a failed
        // PDF load (file missing, decoder error, etc.) doesn't strand the
        // user on a forever-loading screen.
        viewModelScope.launch {
            kotlinx.coroutines.delay(PDF_LOADING_SAFETY_TIMEOUT_MS)
            _pdfLoadingByVault.value = _pdfLoadingByVault.value - vaultId
        }
    }

    /**
     * Set of vaults currently waiting for [com.twinlore.app.ui.screens.pdf.PdfGuideViewer]
     * to render its first frame after [openPdfForVault]. GuideTabContent shows a
     * Loading overlay while the vaultId is in this set.
     */
    private val _pdfLoadingByVault = MutableStateFlow<Set<String>>(emptySet())
    val pdfLoadingByVault: StateFlow<Set<String>> = _pdfLoadingByVault.asStateFlow()

    /**
     * Called by PdfGuideViewer's first composition to dismiss the Loading
     * overlay. Idempotent — safe to call from a LaunchedEffect that may
     * re-fire on configuration changes.
     */
    fun notifyPdfReady(vaultId: String) {
        if (vaultId in _pdfLoadingByVault.value) {
            _pdfLoadingByVault.value = _pdfLoadingByVault.value - vaultId
        }
    }

    private companion object {
        /** Generous upper bound for "PDF should have rendered by now". */
        private const val PDF_LOADING_SAFETY_TIMEOUT_MS: Long = 8_000L
    }

    // -------------------------------------------------------------------------
    // Code-wheel download + open orchestration
    // -------------------------------------------------------------------------

    /** A code-wheel zip queued to download in the Browser tab. Key = vaultId. */
    data class PendingCodeWheelDownload(val downloadUrl: String, val filename: String)

    private val _pendingCodeWheelDownloadByVault =
        MutableStateFlow<Map<String, PendingCodeWheelDownload>>(emptyMap())
    val pendingCodeWheelDownloadByVault: StateFlow<Map<String, PendingCodeWheelDownload>> =
        _pendingCodeWheelDownloadByVault.asStateFlow()

    /**
     * Route a matched code wheel to the Browser tab: open the archive.org details
     * page (same URL as the Find-menu Support chip) and queue the zip download so
     * the browser's standard download banner drives it. The download lands as a
     * playable "wheel" guide via [BrowserSaveCoordinator.handleDownload].
     */
    fun openCodeWheelInBrowser(
        vaultId: String,
        entry: com.twinlore.app.data.codewheel.CodeWheelEntry,
    ) {
        setPendingUrlForBrowserTab(vaultId, com.twinlore.app.data.codewheel.CodeWheelEntry.SUPPORT_URL)
        _pendingCodeWheelDownloadByVault.value =
            _pendingCodeWheelDownloadByVault.value +
                (vaultId to PendingCodeWheelDownload(entry.downloadUrl, "${entry.title} Code Wheel.zip"))
        setVaultSessionTab(vaultId, VaultTab.Browser)
    }

    /** Consume and return the queued code-wheel download for the given vault, if any. */
    fun consumePendingCodeWheelDownload(vaultId: String): PendingCodeWheelDownload? {
        val pending = _pendingCodeWheelDownloadByVault.value[vaultId]
        if (pending != null) {
            _pendingCodeWheelDownloadByVault.value = _pendingCodeWheelDownloadByVault.value - vaultId
        }
        return pending
    }

    fun openWheelForVault(vaultId: String, bookmark: com.twinlore.app.domain.SavedTab) {
        _openPageGroupByVault.value = _openPageGroupByVault.value - vaultId
        _openSingleGuideByVault.value = _openSingleGuideByVault.value - vaultId
        setPendingGuideBookmarkId(vaultId, bookmark.id)
        viewModelScope.launch {
            val current = stateManager.getCurrentState()
            if (current is AppState.VaultSession && current.vaultId == vaultId &&
                current.activeTab != VaultTab.Guide
            ) {
                vaultModeStateManager.saveLastTab(vaultId, VaultTab.Guide)
                stateManager.setState(
                    AppState.VaultSession(vaultId, VaultTab.Guide, current.artworkUri, current.accentArgb)
                )
            }
            guideViewStateManager.saveActiveBookmarkId(vaultId, bookmark.id)
            savedTabRepository.updateLastAccessed(bookmark.id)
        }
    }

    /**
     * Close the new single-guide viewer for this vault AND clear the
     * persisted active bookmark id. Without the id clear, GuideScreen's
     * fallback path re-loads the same bookmark via the old WebView code
     * and the user sees the old viewer flash before getting to the picker.
     *
     * Tab switches don't call this — the viewer stays mounted across
     * tab navigation. Only an explicit Folder-tap or Guide-tab re-tap
     * routes here.
     */
    fun closeSingleGuideForVault(vaultId: String) {
        _openSingleGuideByVault.value = _openSingleGuideByVault.value - vaultId
        viewModelScope.launch {
            guideViewStateManager.saveActiveBookmarkId(vaultId, null)
        }
    }

    private val autoRestoredSingleGuideVaults = mutableSetOf<String>()

    /**
     * Per-vault gate: true once `maybeRestoreActiveSingleGuideForVault(vaultId)`
     * has finished its async lookup. See [groupRestoreDone] for why this exists.
     */
    private val _singleGuideRestoreDone = MutableStateFlow<Set<String>>(emptySet())
    val singleGuideRestoreDone: StateFlow<Set<String>> = _singleGuideRestoreDone.asStateFlow()

    /**
     * On first vault entry per process: if the vault's persisted active
     * bookmark is a single-page guide (mht/html/txt), dispatch to the
     * single-guide viewer. No-op for page-group (its own restore handles
     * those) or PDF (existing GuideScreen path).
     */
    fun maybeRestoreActiveSingleGuideForVault(vaultId: String) {
        if (vaultId in autoRestoredSingleGuideVaults) return
        autoRestoredSingleGuideVaults.add(vaultId)
        viewModelScope.launch {
            try {
                if (_openSingleGuideByVault.value.containsKey(vaultId)) return@launch
                if (_openPageGroupByVault.value.containsKey(vaultId)) return@launch
                val bookmarkId = guideViewStateManager.getActiveBookmarkId(vaultId) ?: return@launch
                val tab = savedTabRepository.getTabById(bookmarkId) ?: return@launch
                val ft = tab.fileType.lowercase()
                if (ft == "mht" || ft == "html" || ft == "txt") {
                    _openSingleGuideByVault.value =
                        _openSingleGuideByVault.value + (vaultId to tab)
                }
            } finally {
                _singleGuideRestoreDone.value = _singleGuideRestoreDone.value + vaultId
            }
        }
    }

    /** Pending URL to load when Browser tab is shown (e.g. link opened from Guide tab). Key = vaultId. */
    private val _pendingUrlForBrowserTab = MutableStateFlow<Map<String, String>>(emptyMap())
    val pendingUrlForBrowserTab: StateFlow<Map<String, String>> = _pendingUrlForBrowserTab.asStateFlow()

    /** Set a URL to load when the Browser tab is shown for this vault. Call before setVaultSessionTab(vaultId, Browser). */
    fun setPendingUrlForBrowserTab(vaultId: String, url: String) {
        _pendingUrlForBrowserTab.value = _pendingUrlForBrowserTab.value + (vaultId to url)
    }

    /** Consume and return the pending URL for the given vault if any. Clears it after reading. */
    fun consumePendingUrlForBrowserTab(vaultId: String): String? {
        val url = _pendingUrlForBrowserTab.value[vaultId]
        if (url != null) {
            _pendingUrlForBrowserTab.value = _pendingUrlForBrowserTab.value - vaultId
        }
        return url
    }

    /** Deferred tab switch: switch to Guide tab only after the guide finishes rendering. Key = vaultId. */
    private val _pendingGuideTabSwitch = MutableStateFlow<Set<String>>(emptySet())

    /** Request a deferred switch to Guide tab — the actual switch happens when the guide finishes loading. */
    fun requestGuideTabSwitch(vaultId: String) {
        _pendingGuideTabSwitch.value = _pendingGuideTabSwitch.value + vaultId
    }

    /** Consume (and clear) a pending tab-switch request. Returns true if one was pending. */
    fun consumePendingGuideTabSwitch(vaultId: String): Boolean {
        val has = vaultId in _pendingGuideTabSwitch.value
        if (has) {
            _pendingGuideTabSwitch.value = _pendingGuideTabSwitch.value - vaultId
        }
        return has
    }

    /** Pending guide bookmark ID to load when Guide tab is shown (e.g. saved from Browser tab). Key = vaultId. */
    private val _pendingGuideBookmarkId = MutableStateFlow<Map<String, String>>(emptyMap())
    val pendingGuideBookmarkId: StateFlow<Map<String, String>> = _pendingGuideBookmarkId.asStateFlow()

    /** Set a bookmark ID to load when the Guide tab is shown. Call before setVaultSessionTab(vaultId, Guide). */
    fun setPendingGuideBookmarkId(vaultId: String, bookmarkId: String) {
        _pendingGuideBookmarkId.value = _pendingGuideBookmarkId.value + (vaultId to bookmarkId)
    }

    /** Consume and return the pending guide bookmark ID for the given vault if any. Clears it after reading. */
    fun consumePendingGuideBookmarkId(vaultId: String): String? {
        val id = _pendingGuideBookmarkId.value[vaultId]
        if (id != null) {
            _pendingGuideBookmarkId.value = _pendingGuideBookmarkId.value - vaultId
        }
        return id
    }

    /**
     * Navigate to Map Viewer screen
     * @param vaultId The unique identifier of the vault
     */
    fun navigateToMapViewer(vaultId: String) {
        viewModelScope.launch {
            // Check license before allowing navigation
            run {
                // Save that Map mode is now active
                vaultModeStateManager.saveLastMode(vaultId, isBrowser = false)
                stateManager.setState(AppState.MapViewer(vaultId))
            }
        }
    }

    /**
     * Open a page-group inside the given vault's Guide tab. The viewer is
     * rendered as a Guide-tab sub-state (not a separate AppState) so the
     * vault session's top bar + bottom bar + chrome-visibility plumbing
     * surround it like any other Guide content.
     */
    /**
     * Switch the active document for a vault from inside the Guide tab.
     * Routes group fileTypes through [navigateToPageGroupViewer]; everything
     * else (pdf / mht / html / epub / txt / link) clears the page-group slot
     * for this vault and writes the new active bookmark id so the next
     * GuideScreen mount picks it up via SINGLE_RESTORE_PATH.
     */
    fun switchActiveDoc(vaultId: String, doc: com.twinlore.app.domain.SavedTab) {
        val ft = doc.fileType.lowercase()
        // Delegate to the canonical openers so dispatch-map updates happen
        // synchronously in one block. The previous async pattern (clear group
        // → suspending getTabById → set single-guide) opened a ~200 ms window
        // where GuideTabContent fell through to GuideScreen and the legacy
        // GuideBrowserTopBar flashed before SingleGuideViewerScreen took
        // over. The caller already holds the SavedTab, so no DB lookup is
        // needed.
        when (ft) {
            "group" -> navigateToPageGroupViewer(vaultId, doc.id)
            "mht", "html", "txt" -> openSingleGuideForVault(vaultId, doc)
            "pdf" -> openPdfForVault(vaultId, doc)
            "wheel", "htmlzip" -> openWheelForVault(vaultId, doc)
            "link" -> {
                // LINK docs are external URLs — open them in the Browser tab,
                // never a guide viewer. Don't touch the guide dispatch maps or
                // persist the link as the active doc, so the Guide tab's current
                // state stays intact when the user returns to it.
                setPendingUrlForBrowserTab(vaultId, doc.url)
                setVaultSessionTab(vaultId, VaultTab.Browser)
            }
            else -> {
                // EPUB / link / legacy HTML — GuideScreen handles these.
                // Sync clear of the new-viewer dispatch maps so the next
                // composition mounts GuideScreen without bouncing through the
                // restore gate.
                _openPageGroupByVault.value = _openPageGroupByVault.value - vaultId
                _openSingleGuideByVault.value = _openSingleGuideByVault.value - vaultId
                viewModelScope.launch {
                    guideViewStateManager.saveActiveBookmarkId(vaultId, doc.id)
                    savedTabRepository.updateLastAccessed(doc.id)
                }
            }
        }
    }

    /**
     * Stream of every SavedTab in a vault. Thin pass-through to
     * [SavedTabRepository] so the vault session top bar can render a doc-
     * switcher dropdown without re-resolving the repository via a separate
     * EntryPoint at the call site. Sort + filter are the caller's concern.
     */
    fun getTabsForVault(vaultId: String): kotlinx.coroutines.flow.Flow<List<com.twinlore.app.domain.SavedTab>> =
        savedTabRepository.getTabsForVault(vaultId)

    fun navigateToPageGroupViewer(vaultId: String, groupTabId: String) {
        // Apply both dispatch-map updates synchronously, symmetric with
        // [openSingleGuideForVault] and [openPdfForVault]. Without the
        // single-guide clear, switching from an open single guide to a group
        // leaves the prior SavedTab lurking in _openSingleGuideByVault[V];
        // GuideTabContent hides it (page-group is checked first), but the
        // next time the group is closed (tab-reselect → onBack, or back
        // gesture), the dispatch falls through to the stale single-guide
        // entry and SingleGuideViewerScreen remounts with the previously-
        // viewed bookmark instead of showing the picker.
        _openSingleGuideByVault.value = _openSingleGuideByVault.value - vaultId
        _openPageGroupByVault.value = _openPageGroupByVault.value + (vaultId to groupTabId)
        viewModelScope.launch {
            // Persist the group as the vault's active "guide" so a cold start
            // (or any vault entry) resumes into the same group, matching
            // single-page-guide behaviour. The picker's group tap, the bulk-
            // save handoff (BrowserScreen), and the picker tab-reselect path
            // all route through here so this is the single persistence point.
            guideViewStateManager.saveActiveBookmarkId(vaultId, groupTabId)
            // Make sure the user lands on Guide so the viewer is visible.
            val current = stateManager.currentState.value
            if (current !is AppState.VaultSession || current.vaultId != vaultId || current.activeTab != VaultTab.Guide) {
                stateManager.setState(AppState.VaultSession(vaultId, VaultTab.Guide))
            }
        }
    }

    /**
     * If [text] (a single text guide's content) is too large to render as one
     * WebView document, split it into a memory-safe page-group, replace the
     * original tab in place, navigate to the group, and return true so the
     * caller stops rendering the single guide. Returns false for normal-size
     * guides (render as usual). Safe to call on every open: once split, the
     * original tab is gone so it can't re-trigger.
     */
    suspend fun splitHugeTextGuideToGroup(
        vaultId: String,
        bookmark: com.twinlore.app.domain.SavedTab,
        text: String,
    ): Boolean {
        if (!textGuideSplitter.shouldSplit(text)) return false
        val groupId = textGuideSplitter.createGroupFromText(
            vaultId = vaultId,
            title = bookmark.title,
            indexUrl = bookmark.url,
            text = text,
            replaceTabId = bookmark.id,
        ) ?: return false
        navigateToPageGroupViewer(vaultId, groupId)
        return true
    }

    /**
     * Navigate to Guide Viewer screen
     * @param vaultId The unique identifier of the vault
     * @param tabId Optional tab ID to open specific saved tab (deprecated - will be removed)
     */
    fun navigateToGuideViewer(vaultId: String, tabId: String? = null) {
        viewModelScope.launch {
            // Check license before allowing navigation
            run {
                // Save that Browser mode is now active
                vaultModeStateManager.saveLastMode(vaultId, isBrowser = true)
                stateManager.setState(AppState.GuideViewer(vaultId, tabId))
            }
        }
    }

    /**
     * Navigate to Settings screen
     */
    /** One-shot flag: auto-expand Achievements section when entering Settings from the setup dialog. */
    private val _expandAchievementsOnce = kotlinx.coroutines.flow.MutableStateFlow(false)
    val expandAchievementsOnce: kotlinx.coroutines.flow.StateFlow<Boolean> = _expandAchievementsOnce

    fun consumeExpandAchievements() { _expandAchievementsOnce.value = false }

    /**
     * The Journey screen Settings was opened from, so back returns to it rather than to the library.
     *
     * The locked journey's only call to action is "Open settings" - it is how a user without an API
     * key or a username unlocks the screen - and back from there used to land in the library, with
     * the whole vault path to walk again to see the journey they had just unlocked. Same shape as
     * [journeyReturnState], one level up: that one remembers where the journey was entered from,
     * this one remembers that Settings was entered from the journey. The journey's own return state
     * is untouched by the round trip, so back from the restored journey still reaches the vault.
     */
    @Volatile
    private var settingsReturnState: AppState.Journey? = null

    /**
     * Leave Settings the way the user came in. Both exits route through here: the system back
     * gesture and the screen's own back button, which used to call [navigateToLibrary] directly
     * and so always landed in the library no matter where Settings was opened from.
     */
    fun exitSettings() {
        val backToJourney = settingsReturnState
        settingsReturnState = null
        when {
            backToJourney != null -> viewModelScope.launch {
                // Settings is where the journey's own doorways are switched off, and the two things
                // it can be switched off with are both reachable from the screen this hop returns
                // to: turning Achievements off takes the library button away (and the vault tab),
                // and clearing the account takes the history the journey charts. Restoring the
                // screen after either one puts the user back on a feature they have just removed,
                // on a screen with no way back to it.
                if (journeyStillAvailable()) stateManager.setState(backToJourney) else navigateToLibrary()
            }
            else -> navigateToLibrary()
        }
    }

    /**
     * Whether the Achievement Journey is still offered, asked with the same two questions the
     * library's own button asks (`LibraryTopMenuBar`): the Achievements setting, and an account.
     *
     * Deliberately not part of the journey screen's own `lockedReason`. A screen that has been
     * navigated to working is fine; what the setting governs is whether the app offers to go
     * there, which is a question about doorways rather than about the screen.
     */
    private suspend fun journeyStillAvailable(): Boolean =
        settingsRepository.achievementsTabVisible.first() &&
            !raCredentialsRepository.getCredentials()?.username.isNullOrBlank()

    fun navigateToSettings(expandAchievements: Boolean = false) {
        val from = stateManager.getCurrentState()
        // Captured on the way in, and only for the journey: every other entry point is the library,
        // which is where back already goes.
        settingsReturnState = from as? AppState.Journey
        viewModelScope.launch {
            // Check license before allowing navigation
            run {
                _expandAchievementsOnce.value = expandAchievements
                stateManager.setState(AppState.Settings)
            }
        }
    }

    /** Whether the AI assistant appears as a bottom-bar tab. */
    val aiBottomTabVisible: StateFlow<Boolean> = settingsRepository.aiBottomTabVisible
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    /** Navigate to the ES-DE play statistics screen. */
    fun navigateToGameStats() {
        viewModelScope.launch {
            stateManager.setState(AppState.GameStats)
        }
    }

    /** State to return to when leaving the Achievement Journey (captured on entry). */
    private var journeyReturnState: AppState? = null

    /**
     * Navigate to the Achievement Journey screen, optionally locked to a single game.
     * @param lockedGameId If non-null, the journey is scoped to this game.
     * @param lockedGameTitle Display title for the locked game, if any.
     */
    fun navigateToJourney(lockedGameId: Int? = null, lockedGameTitle: String? = null) {
        viewModelScope.launch {
            journeyReturnState = stateManager.currentState.value
            stateManager.setState(AppState.Journey(lockedGameId, lockedGameTitle))
        }
    }

    /**
     * Navigate to the Recommended Apps catalog screen.
     */
    fun navigateToAppCatalog() {
        viewModelScope.launch {
            stateManager.setState(AppState.AppCatalog)
        }
    }

    /**
     * Enter sleep mode - saves current state and transitions to Sleep.
     * LEDs are intentionally left ON: sleep dims the screen but keeps the
     * ambient focus lighting lit. [SleepScreen] holds the LEDs at the focus
     * colour with no inactivity timeout for the duration of sleep.
     * @param mapBottomSheetOpen When true, we are entering sleep from the map top bar (map bottom sheet is open). Restored on wake.
     */
    fun enterSleepMode(mapBottomSheetOpen: Boolean = false) {
        viewModelScope.launch {
            val current = stateManager.getCurrentState()
            if (current !is AppState.Sleep) {
                stateBeforeSleep = current
                mapBottomSheetOpenBeforeSleep = mapBottomSheetOpen
                stateManager.setState(AppState.Sleep, validate = false)
            }
        }
    }

    /**
     * Exit sleep mode - restores previous state and, if it was GuideViewer with map sheet open, signals UI to restore it.
     */
    fun exitSleepMode() {
        viewModelScope.launch {
            val previousState = stateBeforeSleep ?: AppState.Library
            val hadMapSheetOpen = mapBottomSheetOpenBeforeSleep && previousState is AppState.GuideViewer
            stateBeforeSleep = null
            mapBottomSheetOpenBeforeSleep = false
            stateManager.setState(previousState, validate = false)
            if (hadMapSheetOpen) {
                _restoreMapBottomSheetOpen.value = true
            }
        }
    }

    /**
     * Handle back navigation
     * Returns true if navigation was handled, false if should use system back
     */
    fun handleBackNavigation(): Boolean {
        val current = stateManager.getCurrentState()
        return when (current) {
            // Nothing to intercept from Library — fall through to system back (exit app).
            is AppState.Library -> false
            is AppState.Settings -> {
                exitSettings()
                true
            }
            is AppState.VaultSession -> {
                PerfLog.mark("handleBackNavigation VaultSession begin vaultId=${current.vaultId}")
                vaultModeStateManager.saveLastTab(current.vaultId, current.activeTab)
                _backPressNavigating.value = true
                backPressNavigatingTimestamp = System.currentTimeMillis()
                _triggerRefreshWhenOnLibrary.value = false
                navigateToLibrary(focusedVaultId = current.vaultId)
                true
            }
            is AppState.MapViewer -> {
                // Switch to Browser mode (same vault)
                navigateToGuideViewer(current.vaultId)
                true
            }
            is AppState.GuideViewer -> {
                // Return to Library with focus on the current vault
                // WebView back navigation is handled in GuideBrowserScreen before this is called
                _backPressNavigating.value = true
                backPressNavigatingTimestamp = System.currentTimeMillis()
                _triggerRefreshWhenOnLibrary.value = false
                navigateToLibrary(focusedVaultId = current.vaultId)
                true
            }
            is AppState.Sleep -> {
                exitSleepMode()
                true
            }
            is AppState.RetroAchievementsProfile -> {
                val returnToVaultId = _raProfileReturnToVaultId.value
                _raProfileReturnToVaultId.value = null
                if (returnToVaultId != null) {
                    navigateToVaultSession(returnToVaultId, VaultTab.Achievements)
                } else {
                    navigateToLibrary()
                }
                true
            }
            is AppState.AppCatalog -> {
                navigateToLibrary()
                true
            }
            is AppState.GameStats -> {
                navigateToLibrary()
                true
            }
            is AppState.Journey -> {
                val ret = journeyReturnState
                journeyReturnState = null
                if (ret != null && ret !is AppState.Journey) {
                    viewModelScope.launch { stateManager.setState(ret) }
                } else {
                    navigateToLibrary()
                }
                true
            }
        }
    }

    // Must stay the last initializer in the class. viewModelScope is Main.immediate,
    // so this launch runs inside the constructor until its first real suspension,
    // and the achievements source has none before it writes the refresh trigger.
    // Launched from the main init block it hit fields not yet initialized: an NPE
    // on every cold start while openLastPlayedOnLaunch was on.
    init {
        // Startup detection is opt-in: when openLastPlayedOnLaunch is ON, cold start
        // resolves the last played game and presents it via the Library's fade +
        // fullscreen showcase.
        viewModelScope.launch { handleOpenLastPlayed(isColdStart = true) }
    }
}
