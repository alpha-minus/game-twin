package com.twinlore.app.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twinlore.app.data.ThemePalette
import com.twinlore.app.data.AppFont
import com.twinlore.app.data.ArtDisplayMode
import com.twinlore.app.data.DefaultAssetType
import com.twinlore.app.data.GridDisplayRatio
import com.twinlore.app.data.LibraryViewMode
import com.twinlore.app.data.LibraryBackgroundStyle
import com.twinlore.app.data.HapticFeedbackIntensity
import com.twinlore.app.data.CocoonLogParser
import com.twinlore.app.data.LastPlayedSource
import com.twinlore.app.data.LandingEntry
import com.twinlore.app.data.SearchEngine
import com.twinlore.app.data.LedTurnOffTime
import com.twinlore.app.data.SettingsRepository
import com.twinlore.app.data.StatusBarMode
import com.twinlore.app.data.VaultRepository
import com.twinlore.app.data.metrics.SystemMetrics
import com.twinlore.app.data.metrics.SystemMetricsSnapshot
import com.twinlore.app.data.library.HomeFolderSetupCoordinator
import com.twinlore.app.data.library.LibraryAutoSaveObserver
import com.twinlore.app.data.library.LibraryFileSaveOutcome
import com.twinlore.app.data.library.sync.LibrarySyncCoordinator
import com.twinlore.app.data.license.LicenseManager
import com.twinlore.app.data.license.LicenseState
import com.twinlore.app.data.retroachievements.RaCredentials
import com.twinlore.app.data.retroachievements.RetroAchievementsApiClient
import com.twinlore.app.data.retroachievements.RetroAchievementsCredentialsRepository
import com.twinlore.app.data.steam.SteamApiClient
import com.twinlore.app.data.steam.SteamCredentials
import com.twinlore.app.data.steam.SteamCredentialsRepository
import com.twinlore.app.data.update.UpdateManager
import com.twinlore.app.data.update.UpdateState
import com.twinlore.app.domain.FilePickerViewMode
import com.twinlore.app.domain.FilePickerSortField
import com.twinlore.app.domain.LibrarySortField
import com.twinlore.app.domain.SortDirection
import com.twinlore.app.domain.Result
import com.twinlore.app.ui.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import com.twinlore.app.thor.led.ThorLedManager
import java.io.File
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val licenseManager: LicenseManager,
    private val retroArchHistoryParser: com.twinlore.app.data.RetroArchHistoryParser,
    private val esdeLogParser: com.twinlore.app.data.ESDELogParser,
    private val cocoonLogParser: CocoonLogParser,
    private val vaultRepository: VaultRepository,
    private val retroAchievementsApiClient: RetroAchievementsApiClient,
    private val raCredentialsRepository: RetroAchievementsCredentialsRepository,
    private val steamApiClient: SteamApiClient,
    private val steamCredentialsRepository: SteamCredentialsRepository,
    private val updateManager: UpdateManager,
    private val homeFolderSetupCoordinator: HomeFolderSetupCoordinator,
    private val storageFolderMigrationCoordinator: com.twinlore.app.data.library.StorageFolderMigrationCoordinator,
    private val libraryFolderManager: com.twinlore.app.data.library.LibraryFolderManager,
    private val libraryAutoSaveObserver: LibraryAutoSaveObserver,
    private val libraryLoadCoordinator: com.twinlore.app.data.library.LibraryLoadCoordinator,
    private val libraryIntegrityChecker: com.twinlore.app.data.library.LibraryIntegrityChecker,
    private val mediaSearchPathRepository: com.twinlore.app.data.library.MediaSearchPathRepository,
    private val mediaIndexer: com.twinlore.app.data.library.MediaIndexer,
    private val localMediaBinder: com.twinlore.app.data.library.LocalMediaBinder,
    private val mediaSearchIndexDao: com.twinlore.app.data.local.MediaSearchIndexDao,
    private val resourceResolver: com.twinlore.app.data.library.ResourceResolver,
    private val libraryFileStore: com.twinlore.app.data.library.LibraryFileStore,
    private val librarySyncCoordinator: LibrarySyncCoordinator,
    private val achievementSyncCoordinator: com.twinlore.app.data.AchievementSyncCoordinator,
    private val appIconManager: com.twinlore.app.data.AppIconManager,
    private val splashIconCache: com.twinlore.app.data.SplashIconCache,
    private val systemMetrics: SystemMetrics,
    private val journeyDataSource: com.twinlore.app.journey.data.JourneyDataSource,
) : BaseViewModel() {

    /** Live integrity audit state. Idle until the user taps Rescan. */
    val integrityState: StateFlow<com.twinlore.app.domain.LibraryIntegrityState> =
        libraryIntegrityChecker.state

    private val _isIntegrityRescanning = MutableStateFlow(false)
    val isIntegrityRescanning: StateFlow<Boolean> = _isIntegrityRescanning.asStateFlow()

    /**
     * Run the audit. Held over a 700ms minimum so the spinner is actually
     * visible on small libraries where the audit completes in a few ms.
     */
    fun runIntegrityCheck() {
        viewModelScope.launch {
            _isIntegrityRescanning.value = true
            val started = System.currentTimeMillis()
            try {
                libraryIntegrityChecker.runCheck()
            } finally {
                val elapsed = System.currentTimeMillis() - started
                if (elapsed < 700L) delay(700L - elapsed)
                _isIntegrityRescanning.value = false
            }
        }
    }

    /** Apply removal policy on the current Issues report and re-run the audit. */
    fun cleanupMissingFiles() {
        viewModelScope.launch {
            val current = libraryIntegrityChecker.state.value
            if (current is com.twinlore.app.domain.LibraryIntegrityState.Issues) {
                libraryIntegrityChecker.applyRemoval(current.report)
            }
        }
    }

    companion object {
        private const val TAG = "SettingsViewModel"
        /** Shown in API key field when credentials are already stored (real key never displayed). */
        const val RA_API_KEY_PLACEHOLDER = "••••••••"
    }
    
    val updateState: StateFlow<UpdateState> = updateManager.state

    private val _isCheckingForUpdate = MutableStateFlow(false)
    val isCheckingForUpdate: StateFlow<Boolean> = _isCheckingForUpdate.asStateFlow()

    /**
     * True when the RetroArch help dialog should be auto-opened (typically
     * because the just-picked folder didn't yield a parseable
     * content_history.lpl). UI consumes via dismissRetroArchHelpDialog so
     * the dialog doesn't reopen on configuration change.
     */
    private val _pendingRetroArchHelpDialog = MutableStateFlow(false)
    val pendingRetroArchHelpDialog: StateFlow<Boolean> =
        _pendingRetroArchHelpDialog.asStateFlow()

    fun dismissRetroArchHelpDialog() { _pendingRetroArchHelpDialog.value = false }

    /** Auto-popup state for ES-DE help dialog after a folder-picker callback. */
    private val _pendingESDEHelpDialog =
        MutableStateFlow<com.twinlore.app.data.ESDELogParser.ESDEFolderState?>(null)
    val pendingESDEHelpDialog: StateFlow<com.twinlore.app.data.ESDELogParser.ESDEFolderState?> =
        _pendingESDEHelpDialog.asStateFlow()

    fun dismissESDEHelpDialog() { _pendingESDEHelpDialog.value = null }

    /** Auto-popup state for Cocoon help dialog after a folder-picker callback. */
    private val _pendingCocoonHelpDialog =
        MutableStateFlow<com.twinlore.app.data.CocoonLogParser.CocoonFolderState?>(null)
    val pendingCocoonHelpDialog: StateFlow<com.twinlore.app.data.CocoonLogParser.CocoonFolderState?> =
        _pendingCocoonHelpDialog.asStateFlow()

    fun dismissCocoonHelpDialog() { _pendingCocoonHelpDialog.value = null }

    fun checkForUpdate() {
        viewModelScope.launch {
            _isCheckingForUpdate.value = true
            updateManager.checkForUpdate(force = true)
            _isCheckingForUpdate.value = false
        }
    }

    val themePalette: Flow<ThemePalette?> = settingsRepository.themePalette
    val appFont: Flow<AppFont> = settingsRepository.appFont

    val licenseTier: kotlinx.coroutines.flow.StateFlow<com.twinlore.app.data.license.LicenseTier> =
        licenseManager.licenseTier

    val appIcon: kotlinx.coroutines.flow.StateFlow<com.twinlore.app.data.AppIcon> =
        com.twinlore.app.data.effectiveAppIconFlow(settingsRepository, licenseManager)
            .stateIn(
                viewModelScope,
                kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
                // Seed from the synchronous cache so the LibraryScreen preload
                // splash renders the themed icon on its first frame instead of
                // flashing the default before DataStore emits.
                splashIconCache.read(),
            )

    val lastPlayedSource: Flow<LastPlayedSource> = settingsRepository.lastPlayedSource
    val esdeFolderUri: Flow<String?> = settingsRepository.esdeFolderUri
    val esdeFolderPath: Flow<String?> = settingsRepository.esdeFolderPath
    val cocoonFolderUri: Flow<String?> = settingsRepository.cocoonFolderUri
    val cocoonFolderPath: Flow<String?> = settingsRepository.cocoonFolderPath
    val romsFolderUri: Flow<String?> = settingsRepository.romsFolderUri
    val artDisplayMode: Flow<ArtDisplayMode> = settingsRepository.artDisplayMode
    val statusBarMode: Flow<StatusBarMode> = settingsRepository.statusBarMode

    /** Live meter data for the Performance status cluster (polled by StatusBarMetricsController). */
    val metricsSnapshot: StateFlow<SystemMetricsSnapshot> = systemMetrics.snapshot
    val autoBoxArtScraping: Flow<Boolean> = settingsRepository.autoBoxArtScraping
    val retroArchHistoryFolder: Flow<String?> = settingsRepository.retroArchHistoryFolder
    val retroArchHistoryFolderPath: Flow<String?> = settingsRepository.retroArchHistoryFolderPath
    val retroArchAutoRefresh: Flow<Boolean> = settingsRepository.retroArchAutoRefresh
    val createVaultIfNotFound: Flow<Boolean> = settingsRepository.createVaultIfNotFound
    val openLastPlayedOnLaunch: Flow<Boolean> = settingsRepository.openLastPlayedOnLaunch
    val foldPositionX: Flow<Float> = settingsRepository.foldPositionX
    val foldPositionY: Flow<Float> = settingsRepository.foldPositionY
    val foldHandleAlpha: Flow<Float> = settingsRepository.foldHandleAlpha
    val foldHandleSizeDp: Flow<Int> = settingsRepository.foldHandleSizeDp
    val foldHandleRecess: Flow<Float> = settingsRepository.foldHandleRecess
    val foldHandleClockBattery: Flow<Boolean> = settingsRepository.foldHandleClockBattery

    /** Whether the hold-to-swap hint has been shown before the second fold. */
    val foldSwapHintSeen: Flow<Boolean> = settingsRepository.foldSwapHintSeen
    val foldOverlayHintSeen: Flow<Boolean> = settingsRepository.foldOverlayHintSeen

    /** Whether the button has entered Overlay Mode once already. */
    val overlayEnteredBefore: Flow<Boolean> = settingsRepository.overlayEnteredBefore

    fun setOverlayEnteredBefore(entered: Boolean) {
        viewModelScope.launch { settingsRepository.setOverlayEnteredBefore(entered) }
    }

    fun setFoldOverlayHintSeen(seen: Boolean) {
        viewModelScope.launch { settingsRepository.setFoldOverlayHintSeen(seen) }
    }

    fun setFoldSwapHintSeen(seen: Boolean) {
        viewModelScope.launch { settingsRepository.setFoldSwapHintSeen(seen) }
    }

    /** Whether the one-time "show it in Cocoon too" hint has already been shown. */
    val cocoonIconPackHintShown: Flow<Boolean> = settingsRepository.cocoonIconPackHintShown

    val keepTopBarVisible: Flow<Boolean> = settingsRepository.keepTopBarVisible
    /**
     * Classification of the ES-DE folder for state-aware banner + dialog copy.
     * Null when no folder is set yet.
     */
    val esdeFolderState: Flow<com.twinlore.app.data.ESDELogParser.ESDEFolderState?> =
        settingsRepository.esdeFolderUri
            .flatMapLatest { uri ->
                flow {
                    if (uri.isNullOrBlank()) {
                        emit(null)
                    } else {
                        try {
                            emit(esdeLogParser.checkLogFolder(android.net.Uri.parse(uri)))
                        } catch (e: Exception) {
                            emit(com.twinlore.app.data.ESDELogParser.ESDEFolderState.FolderUnreachable)
                        }
                    }
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /**
     * Classification of the picked RetroArch parent folder for the same
     * ✓-when-valid status affordance the ES-DE / Cocoon settings use. Null
     * until a folder is set. Re-validates on every URI change because the
     * file can appear (after first RetroArch launch) or disappear (user
     * cleared history) without any picker round-trip.
     */
    val retroArchFolderState: Flow<com.twinlore.app.data.RetroArchHistoryParser.RetroArchFolderState?> =
        settingsRepository.retroArchHistoryFolder
            .flatMapLatest { uri ->
                flow {
                    if (uri.isNullOrBlank()) {
                        emit(null)
                    } else {
                        try {
                            emit(retroArchHistoryParser.checkLogFolder(android.net.Uri.parse(uri)))
                        } catch (e: Exception) {
                            emit(com.twinlore.app.data.RetroArchHistoryParser.RetroArchFolderState.FolderUnreachable)
                        }
                    }
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /**
     * Classification of the Cocoon folder. Cocoon writes its log automatically,
     * so the only failure modes are "wrong folder / no launch yet" or
     * "folder unreachable".
     */
    val cocoonFolderState: Flow<com.twinlore.app.data.CocoonLogParser.CocoonFolderState?> =
        settingsRepository.cocoonFolderUri
            .flatMapLatest { uri ->
                flow {
                    if (uri.isNullOrBlank()) {
                        emit(null)
                    } else {
                        try {
                            emit(cocoonLogParser.checkLogFolder(android.net.Uri.parse(uri)))
                        } catch (e: Exception) {
                            emit(com.twinlore.app.data.CocoonLogParser.CocoonFolderState.FolderUnreachable)
                        }
                    }
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    /** Library home folder URI. Null until first-launch picker runs. */
    val homeFolderUri: Flow<String?> = settingsRepository.homeFolderUri
    val focusColor: Flow<com.twinlore.app.data.FocusColor> = settingsRepository.focusColor
    val rawAccentColorArgb: Flow<Int?> = settingsRepository.rawAccentColorArgb
    val gridDisplayRatio: Flow<GridDisplayRatio> = settingsRepository.gridDisplayRatio
    val steamGridApiKey: Flow<String?> = settingsRepository.steamGridApiKey
    val defaultAssetType: Flow<DefaultAssetType> = settingsRepository.defaultAssetType
    val powerSaverMode: Flow<Boolean> = settingsRepository.powerSaverMode
    val autoFocusColorDetection: Flow<Boolean> = settingsRepository.autoFocusColorDetection
    val hapticFeedbackIntensity: Flow<HapticFeedbackIntensity> = settingsRepository.hapticFeedbackIntensity
    val buttonGlyphStyle: Flow<com.twinlore.app.data.ButtonGlyphStyle> = settingsRepository.buttonGlyphStyle
    val swapFaceButtons: Flow<Boolean> = settingsRepository.swapFaceButtons
    val focusSoundEnabled: Flow<Boolean> = settingsRepository.focusSoundEnabled
    val searchEngine: Flow<SearchEngine> = settingsRepository.searchEngine
    val landingEntries: Flow<List<LandingEntry>> = settingsRepository.landingEntries
    val ledEnabled: Flow<Boolean> = settingsRepository.ledEnabled
    val ledBrightness: Flow<Int> = settingsRepository.ledBrightness
    val ledSaturationBoost: Flow<Float> = settingsRepository.ledSaturationBoost

    /** Globally-shared tap-game high score — same value as the checklist easter egg. */
    val tapGameHighScore: StateFlow<Int> = settingsRepository.checklistTapGameHighScore
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    fun setTapGameHighScore(score: Int) {
        viewModelScope.launch { settingsRepository.setChecklistTapGameHighScore(score) }
    }
    /**
     * LED auto-off is permanently locked to NEVER. Every consumer of this
     * flow gets NEVER regardless of what's persisted in the repository. The repository column is
     * retained for backwards compatibility but the setter is no longer wired to UI; cleanup deferred.
     */
    val ledTurnOffTime: Flow<LedTurnOffTime> = flow { emit(LedTurnOffTime.NEVER) }
    val guideThemeInject: Flow<Boolean> = settingsRepository.guideThemeInject
    val guideSmartFormatting: Flow<Boolean> = settingsRepository.guideSmartFormatting
    val privacyBlockingEnabled: Flow<Boolean> = settingsRepository.privacyBlockingEnabled
    val guideScrollMarkersBarVisible: Flow<Boolean> = settingsRepository.guideScrollMarkersBarVisible
    val showLibraryItemNames: Flow<Boolean> = settingsRepository.showLibraryItemNames
    val libraryHintBarVisible: Flow<Boolean> = settingsRepository.libraryHintBarVisible
    val libraryViewMode: Flow<LibraryViewMode> = settingsRepository.libraryViewMode
    val libraryBackgroundStyle: Flow<LibraryBackgroundStyle> = settingsRepository.libraryBackgroundStyle
    val libraryWallpaperDim: Flow<Float> = settingsRepository.libraryWallpaperDim
    val libraryWallpaperBlur: Flow<Float> = settingsRepository.libraryWallpaperBlur
    val librarySortField: Flow<LibrarySortField> = settingsRepository.librarySortField
    val librarySortDirection: Flow<SortDirection> = settingsRepository.librarySortDirection
    val libraryAchievementProgress: Flow<Boolean> = settingsRepository.libraryAchievementProgress
    val guidePickerViewMode: Flow<FilePickerViewMode> = settingsRepository.guidePickerViewMode
    val guidePickerSortField: Flow<FilePickerSortField> = settingsRepository.guidePickerSortField
    val guidePickerSortDirection: Flow<SortDirection> = settingsRepository.guidePickerSortDirection
    val guidePickerShowLinks: Flow<Boolean> = settingsRepository.guidePickerShowLinks
    val imagesPickerViewMode: Flow<FilePickerViewMode> = settingsRepository.imagesPickerViewMode
    val imagesPickerSortField: Flow<FilePickerSortField> = settingsRepository.imagesPickerSortField
    val imagesPickerSortDirection: Flow<SortDirection> = settingsRepository.imagesPickerSortDirection

    // ── Cross-device library sync ──────────────────────────────────────────────

    val syncUpdateNotices: StateFlow<List<com.twinlore.app.data.library.sync.SyncUpdateNotice>> =
        librarySyncCoordinator.updateNotices
    val syncRestorableConflicts: StateFlow<List<com.twinlore.app.data.library.sync.VaultRef>> =
        librarySyncCoordinator.restorableConflicts
    val syncRestorePoints: StateFlow<List<com.twinlore.app.data.library.sync.SyncRestorePoint>> =
        librarySyncCoordinator.restorePoints

    // runCatching: a scan/restore failure (revoked SAF tree, torn snapshot)
    // must never crash the app from a Settings tap; the coordinator retries
    // on the next scan.
    fun syncNow() = viewModelScope.launch {
        runCatching {
            settingsRepository.setDeclinedSyncChangeSet(null)
            // force = true: a deliberate user action must bypass the
            // coordinator's foreground-scan debounce.
            librarySyncCoordinator.checkForChanges(force = true)
        }
    }

    fun restorePreviousVersion(vaultId: String) = viewModelScope.launch {
        runCatching { librarySyncCoordinator.restoreVaultFromBackup(vaultId) }
    }

    /** Whole-library restore from a pre-sync backup; the coordinator restarts the app. */
    fun restoreToPoint(point: com.twinlore.app.data.library.sync.SyncRestorePoint) =
        viewModelScope.launch {
            runCatching { librarySyncCoordinator.restoreToPoint(point) }
        }

    fun dismissSyncUpdateNotices() = viewModelScope.launch {
        librarySyncCoordinator.dismissUpdateNotices()
    }

    // RetroAchievements: credentials (username for display only), validation state, and feature enabled
    val raCredentials: Flow<RaCredentials?> = raCredentialsRepository.credentials
    val retroAchievementsFeatureEnabled: Flow<Boolean> = settingsRepository.retroAchievementsFeatureEnabled
    /** True when RA is configured and last validation succeeded; gates trophy icon and profile entry. */
    val isRetroAchievementsValid: Flow<Boolean> = combine(
        raCredentialsRepository.credentials,
        settingsRepository.retroAchievementsFeatureEnabled
    ) { creds, enabled -> creds != null && enabled }

    // Steam: credentials, validation state
    val steamCredentials: Flow<SteamCredentials?> = steamCredentialsRepository.credentials
    /** True when Steam credentials are configured (Steam ID resolved). */
    val isSteamValid: Flow<Boolean> = steamCredentialsRepository.credentials.map { it != null }

    sealed class SteamValidationState { object Idle : SteamValidationState(); object Loading : SteamValidationState(); data class Success(val message: String = "Valid") : SteamValidationState(); data class Error(val message: String) : SteamValidationState() }
    private val _steamValidationState = MutableStateFlow<SteamValidationState>(SteamValidationState.Idle)
    val steamValidationState: StateFlow<SteamValidationState> = _steamValidationState.asStateFlow()

    /** Shown once a Steam profile connects while the bundled Web API key is in use; null otherwise. */
    private val _steamOwnKeyPrompt = MutableStateFlow<SteamOwnKeyPrompt?>(null)
    internal val steamOwnKeyPrompt: StateFlow<SteamOwnKeyPrompt?> = _steamOwnKeyPrompt.asStateFlow()

    fun dismissSteamOwnKeyPrompt() {
        _steamOwnKeyPrompt.value = null
    }

    sealed class RaValidationState { object Idle : RaValidationState(); object Loading : RaValidationState(); data class Success(val message: String = "Valid") : RaValidationState(); data class Error(val message: String) : RaValidationState() }
    private val _raValidationState = MutableStateFlow<RaValidationState>(RaValidationState.Idle)
    val raValidationState: StateFlow<RaValidationState> = _raValidationState.asStateFlow()

    /**
     * Current RA API key override — empty string when using the bundled default.
     *
     * Read from the repository's own key flow rather than derived from [raCredentials]. A key can
     * be stored with no account configured, and credentials are null until there is a username, so
     * going through them meant a key saved on its own could never be shown (the row kept reading
     * "Using default"). The flow is also what defers the encrypted-prefs read to the repository's
     * background init, which is why it was routed that way to begin with.
     */
    val raApiKeyOverride: StateFlow<String> = raCredentialsRepository.apiKeyOverride
        .map { it ?: "" }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    /**
     * Save or clear the RA API key override. Empty string clears (falls back to bundled).
     *
     * Saved on its own rather than through setCredentials(username, key): that call needs a
     * username to write one, so with no account configured this used to return early and the key
     * was silently dropped. Setting a username first made the very same edit stick, which is what
     * the bug looked like from the outside.
     */
    fun setRaApiKeyOverride(value: String) {
        viewModelScope.launch {
            raCredentialsRepository.setApiKeyOverride(value.ifBlank { null })
        }
    }

    /**
     * Validate and save RetroAchievements credentials. Call when both username and API key are entered.
     * If apiKey is the placeholder (credentials already stored), validation uses the stored key without re-saving.
     * On success saves to encrypted storage and enables RA feature. On failure sets error state.
     */
    fun validateAndSaveRetroAchievementsCredentials(username: String, apiKey: String) {
        val u = username.trim()
        if (u.isBlank()) {
            _raValidationState.value = RaValidationState.Error("Enter username")
            return
        }
        viewModelScope.launch {
            // Use stored custom key if set, otherwise bundled default.
            // The apiKey param is only non-blank when called from legacy code paths.
            val k = apiKey.trim()
            val effectiveKey = when {
                k == RA_API_KEY_PLACEHOLDER -> raCredentialsRepository.getCredentials()?.apiKey
                    ?: RetroAchievementsCredentialsRepository.BUNDLED_API_KEY
                k.isNotBlank() -> k
                else -> raCredentialsRepository.getCustomApiKey()
                    ?: RetroAchievementsCredentialsRepository.BUNDLED_API_KEY
            }
            _raValidationState.value = RaValidationState.Loading
            // Username before this save, to detect an actual account change below.
            val previousUsername = raCredentialsRepository.getCredentials()?.username
            runCatching {
                retroAchievementsApiClient.api.getUserSummary(u, effectiveKey)
            }.fold(
                onSuccess = {
                    // Only store username — apiKey is managed separately via ApiKeyRow
                    val existingCustomKey = raCredentialsRepository.getCustomApiKey()
                    raCredentialsRepository.setCredentials(u, existingCustomKey)
                    settingsRepository.setRetroAchievementsFeatureEnabled(true)
                    _raValidationState.value = RaValidationState.Success()
                    // Account changed → refresh every linked tile from one bulk
                    // completion-progress call so the library reflects the new user.
                    if (!u.equals(previousUsername, ignoreCase = true)) {
                        achievementSyncCoordinator.resyncRaNow()
                        // The departed account's journey cache goes at the moment of the switch,
                        // for the reason spelled out in clearRetroAchievementsCredentials. Its own
                        // wipe would get there too, but only on the next own-data load.
                        runCatching { journeyDataSource.clearCache() }
                    }
                },
                onFailure = { e ->
                    raCredentialsRepository.clearCredentials()
                    settingsRepository.setRetroAchievementsFeatureEnabled(false)
                    _raValidationState.value = RaValidationState.Error(
                        classifyRaValidationError(e)
                    )
                }
            )
        }
    }

    fun clearRetroAchievementsCredentials() {
        viewModelScope.launch {
            raCredentialsRepository.clearCredentials()
            settingsRepository.setRetroAchievementsFeatureEnabled(false)
            // And the Achievement Journey's cache with them, now rather than lazily. The journey
            // caches retrieved account content on disk - including the public progress of anyone
            // the user has compared themselves against, which is another identified person's data
            // sitting on this device - and its own wipe only fires on the next own-data load, so a
            // user who disconnects and never opens the journey again would keep the lot. The
            // privacy notice says it goes when the account does, and this is where the user says
            // the account is going.
            runCatching { journeyDataSource.clearCache() }
            _raValidationState.value = RaValidationState.Idle
        }
    }

    fun clearRaValidationState() {
        _raValidationState.value = RaValidationState.Idle
    }

    /**
     * Validate and save Steam credentials. Resolves vanity URL to Steam ID, then validates
     * by calling GetOwnedGames. On success saves to encrypted storage. On failure sets error state.
     */
    fun validateAndSaveSteamCredentials(vanityUrl: String) {
        val v = vanityUrl.trim()
        if (v.isBlank()) {
            _steamValidationState.value = SteamValidationState.Error("Enter your Steam profile URL or vanity name")
            return
        }
        viewModelScope.launch {
            _steamValidationState.value = SteamValidationState.Loading
            // Steam id before this save, to detect an actual account change below.
            val previousSteamId = steamCredentialsRepository.getCredentials()?.steamId
            runCatching {
                // Extract vanity name from full URL if needed
                val vanityName = extractVanityName(v)
                // Step 1: Resolve vanity URL to Steam ID
                val vanityResponse = steamApiClient.api.resolveVanityURL(vanityName)
                if (vanityResponse.response.success != 1 || vanityResponse.response.steamid.isNullOrBlank()) {
                    throw Exception("Could not resolve Steam profile '$vanityName'. Check the vanity URL.")
                }
                val steamId = vanityResponse.response.steamid!!
                // Step 2: Validate by fetching owned games
                steamApiClient.api.getOwnedGames(steamId)
                // Both calls succeeded — save
                steamCredentialsRepository.setCredentials(vanityName, steamId)
                steamId
            }.fold(
                onSuccess = { newSteamId ->
                    _steamValidationState.value = SteamValidationState.Success()
                    _steamOwnKeyPrompt.value = steamOwnKeyPromptFor(
                        apiKeyOverride = settingsRepository.steamApiKeyOverride.first(),
                        raConnected = raCredentialsRepository.getCredentials() != null,
                    )
                    // Account changed → clear stale Steam cache + refresh the recent 5.
                    if (newSteamId != previousSteamId) {
                        achievementSyncCoordinator.resyncSteamNow()
                    }
                },
                onFailure = { e ->
                    steamCredentialsRepository.clearCredentials()
                    _steamValidationState.value = SteamValidationState.Error(
                        classifySteamValidationError(e)
                    )
                }
            )
        }
    }

    /** Extract vanity name from a Steam profile URL or return the input as-is. */
    private fun extractVanityName(input: String): String {
        // Handle: https://steamcommunity.com/id/myname or steamcommunity.com/id/myname/
        val regex = Regex("""(?:https?://)?steamcommunity\.com/id/([^/]+)/?""")
        return regex.find(input)?.groupValues?.get(1) ?: input
    }

    fun clearSteamCredentials() {
        viewModelScope.launch {
            steamCredentialsRepository.clearCredentials()
            _steamValidationState.value = SteamValidationState.Idle
        }
    }

    fun clearSteamValidationState() {
        _steamValidationState.value = SteamValidationState.Idle
    }

    fun setThemePalette(palette: ThemePalette?) {
        viewModelScope.launch {
            settingsRepository.setThemePalette(palette)
        }
    }

    fun setAppFont(font: AppFont) {
        viewModelScope.launch {
            settingsRepository.setAppFont(font)
        }
    }

    /** Whether the launcher already shows [icon]; false while an Overlay Mode pick waits for a close. */
    fun isLauncherIconApplied(icon: com.twinlore.app.data.AppIcon): Boolean =
        runCatching { appIconManager.isLauncherIconApplied(icon) }.getOrDefault(true)

    fun setAppIcon(icon: com.twinlore.app.data.AppIcon, onApplied: () -> Unit = {}) {
        viewModelScope.launch {
            settingsRepository.setSelectedAppIcon(icon)
            appIconManager.applyLauncherIcon(icon)
            onApplied() // runs after the DataStore write + alias flip complete
        }
    }

    /** Records the Cocoon icon-pack hint as shown, THEN applies the icon. One coroutine, in order: the hint flag must be on disk before applying closes the process. */
    fun applyAppIconAfterCocoonHint(icon: com.twinlore.app.data.AppIcon, onApplied: () -> Unit = {}) {
        viewModelScope.launch {
            settingsRepository.setCocoonIconPackHintShown()
            settingsRepository.setSelectedAppIcon(icon)
            appIconManager.applyLauncherIcon(icon)
            onApplied()
        }
    }

    fun setArtDisplayMode(mode: ArtDisplayMode) {
        viewModelScope.launch {
            settingsRepository.setArtDisplayMode(mode)
        }
    }

    fun setStatusBarMode(mode: StatusBarMode) {
        viewModelScope.launch {
            settingsRepository.setStatusBarMode(mode)
        }
    }

    /** Tap on the status cluster: Standard ↔ Performance (Off stays Off). */
    fun tapToggleStatusBarMode() {
        viewModelScope.launch {
            val current = settingsRepository.statusBarMode.first()
            settingsRepository.setStatusBarMode(current.tapToggled())
        }
    }

    fun setAutoBoxArtScraping(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAutoBoxArtScraping(enabled)
        }
    }
    
    fun setRetroArchHistoryFolder(path: String?) {
        viewModelScope.launch {
            settingsRepository.setRetroArchHistoryFolder(path)
        }
    }

    /**
     * Called when the user picks a RetroArch folder from the SAF folder
     * picker. Persists the folder URI, then probes via the new parser:
     * if no content_history.lpl is found there, auto-opens the help
     * dialog so the user gets the setup-steps reminder right away.
     */
    fun setRetroArchFolderFromPicker(uri: Uri, displayPath: String?) {
        viewModelScope.launch {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: SecurityException) {
                Log.w(TAG, "Could not take persistable URI permission for $uri", e)
            }
            settingsRepository.setRetroArchHistoryFolder(uri.toString())
            settingsRepository.setRetroArchHistoryFolderPath(displayPath)
            // Probe: if the just-picked folder doesn't yield a parseable
            // content_history.lpl, surface the help dialog. We don't need
            // a separate state enum — the help dialog now has single
            // content regardless of WHY the probe failed.
            val foundEntry = retroArchHistoryParser.findLastPlayedGame(uri)
            if (foundEntry == null) {
                _pendingRetroArchHelpDialog.value = true
            }
        }
    }

    fun setLastPlayedSource(source: LastPlayedSource) {
        viewModelScope.launch { settingsRepository.selectLastPlayedSource(source) }
    }

    fun setEsdeFolderFromPicker(uri: Uri, displayPath: String?) {
        viewModelScope.launch {
            settingsRepository.setEsdeFolderUri(uri.toString())
            settingsRepository.setEsdeFolderPath(displayPath)
            // The grant this folder's rows depend on only just came back. Anything
            // the resolver cached while it was missing was resolved without it.
            resourceResolver.invalidateAll()
            mediaSearchPathRepository.syncAutoFromSettings()
            val auto = mediaSearchPathRepository.getAll()
                .firstOrNull { it.origin == com.twinlore.app.domain.MediaSearchPath.Origin.AUTO_ESDE }
            if (auto != null) mediaIndexer.startScanForPath(auto.id)
            val state: com.twinlore.app.data.ESDELogParser.ESDEFolderState = try {
                esdeLogParser.checkLogFolder(uri)
            } catch (e: Exception) {
                com.twinlore.app.data.ESDELogParser.ESDEFolderState.FolderUnreachable
            }
            if (state !is com.twinlore.app.data.ESDELogParser.ESDEFolderState.Valid) {
                _pendingESDEHelpDialog.value = state
            }
        }
    }

    fun setCocoonFolderFromPicker(uri: Uri, displayPath: String?) {
        viewModelScope.launch {
            settingsRepository.setCocoonFolderUri(uri.toString())
            settingsRepository.setCocoonFolderPath(displayPath)
            // See setEsdeFolderFromPicker: re-granting a folder is exactly the event
            // that makes previously-unresolvable rows resolvable again.
            resourceResolver.invalidateAll()
            mediaSearchPathRepository.syncAutoFromSettings()
            val auto = mediaSearchPathRepository.getAll()
                .firstOrNull { it.origin == com.twinlore.app.domain.MediaSearchPath.Origin.AUTO_COCOON }
            if (auto != null) mediaIndexer.startScanForPath(auto.id)
            val state: com.twinlore.app.data.CocoonLogParser.CocoonFolderState = try {
                cocoonLogParser.checkLogFolder(uri)
            } catch (e: Exception) {
                com.twinlore.app.data.CocoonLogParser.CocoonFolderState.FolderUnreachable
            }
            if (state !is com.twinlore.app.data.CocoonLogParser.CocoonFolderState.Valid) {
                _pendingCocoonHelpDialog.value = state
            }
        }
    }

    fun setRomsFolderUri(uri: String) {
        viewModelScope.launch { settingsRepository.setRomsFolderUri(uri) }
    }

    fun setRetroArchAutoRefresh(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setRetroArchAutoRefresh(enabled)
        }
    }

    fun setCreateVaultIfNotFound(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setCreateVaultIfNotFound(enabled)
        }
    }

    fun setOpenLastPlayedOnLaunch(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setOpenLastPlayedOnLaunch(enabled)
        }
    }

    fun setFoldHandleSizeDp(dp: Int) {
        viewModelScope.launch { settingsRepository.setFoldHandleSizeDp(dp) }
    }

    fun setFoldHandleAlpha(alpha: Float) {
        viewModelScope.launch { settingsRepository.setFoldHandleAlpha(alpha) }
    }

    fun setFoldHandleRecess(scale: Float) {
        viewModelScope.launch { settingsRepository.setFoldHandleRecess(scale) }
    }

    fun setFoldHandleClockBattery(on: Boolean) {
        viewModelScope.launch { settingsRepository.setFoldHandleClockBattery(on) }
    }

    fun setKeepTopBarVisible(visible: Boolean) {
        viewModelScope.launch { settingsRepository.setKeepTopBarVisible(visible) }
    }

    fun setFocusColor(color: com.twinlore.app.data.FocusColor, updateLed: Boolean = true) {
        viewModelScope.launch {
            settingsRepository.setFocusColor(color)
            if (updateLed) {
                val ledEnabled = settingsRepository.ledEnabled.first()
                val ledBrightness = settingsRepository.ledBrightness.first()
                val ledSaturationBoost = settingsRepository.ledSaturationBoost.first()
                val turnOff = settingsRepository.ledTurnOffTime.first()
                ThorLedManager.onFocusGained(color.color, ledEnabled, ledBrightness, ledSaturationBoost, turnOff.inactivityMs)
            }
        }
    }

    fun setRawAccentColorArgb(argb: Int?) {
        viewModelScope.launch {
            settingsRepository.setRawAccentColorArgb(argb)
        }
    }

    /** See [SettingsRepository.currentAccentColor]: the one colour to light the rings with. */
    suspend fun currentAccentColor(): androidx.compose.ui.graphics.Color =
        settingsRepository.currentAccentColor()

    /**
     * Both accent keys in one transaction; separate writes let observers pair the new quantized
     * colour with the old raw colour and flash the previous accent. Never drives the LED — the
     * detection paths handle the LED themselves.
     */
    fun setDetectedAccent(color: com.twinlore.app.data.FocusColor, rawArgb: Int) {
        viewModelScope.launch {
            settingsRepository.setDetectedAccent(color, rawArgb)
        }
    }

    fun setGridDisplayRatio(ratio: GridDisplayRatio) {
        viewModelScope.launch {
            settingsRepository.setGridDisplayRatio(ratio)
            settingsRepository.setGridRatioExplicitlySet(true)
        }
    }

    fun setLibraryViewMode(mode: LibraryViewMode) {
        viewModelScope.launch {
            settingsRepository.setLibraryViewMode(mode)
        }
    }

    fun setLibraryBackgroundStyle(style: LibraryBackgroundStyle) {
        viewModelScope.launch {
            settingsRepository.setLibraryBackgroundStyle(style)
        }
    }

    fun setLibraryWallpaperDim(dim: Float) {
        viewModelScope.launch { settingsRepository.setLibraryWallpaperDim(dim) }
    }

    fun setLibraryWallpaperBlur(blur: Float) {
        viewModelScope.launch { settingsRepository.setLibraryWallpaperBlur(blur) }
    }

    fun setGuidePickerViewMode(mode: FilePickerViewMode) {
        viewModelScope.launch { settingsRepository.setGuidePickerViewMode(mode) }
    }
    fun setGuidePickerSortField(field: FilePickerSortField) {
        viewModelScope.launch { settingsRepository.setGuidePickerSortField(field) }
    }
    fun setGuidePickerSortDirection(dir: SortDirection) {
        viewModelScope.launch { settingsRepository.setGuidePickerSortDirection(dir) }
    }
    fun setGuidePickerShowLinks(show: Boolean) {
        viewModelScope.launch { settingsRepository.setGuidePickerShowLinks(show) }
    }
    fun setImagesPickerViewMode(mode: FilePickerViewMode) {
        viewModelScope.launch { settingsRepository.setImagesPickerViewMode(mode) }
    }
    fun setImagesPickerSortField(field: FilePickerSortField) {
        viewModelScope.launch { settingsRepository.setImagesPickerSortField(field) }
    }
    fun setImagesPickerSortDirection(dir: SortDirection) {
        viewModelScope.launch { settingsRepository.setImagesPickerSortDirection(dir) }
    }

    fun setSteamGridApiKey(apiKey: String?) {
        viewModelScope.launch {
            settingsRepository.setSteamGridApiKey(apiKey)
        }
    }

    // --- BYOK overrides exposed to the Settings "API keys" section ---

    val steamApiKeyOverride: StateFlow<String> = settingsRepository.steamApiKeyOverride
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val steamGridApiKeyOverride: StateFlow<String> = settingsRepository.steamGridApiKey
        .map { it ?: "" }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val theGamesDbApiKeyOverride: StateFlow<String> = settingsRepository.theGamesDbApiKeyOverride
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    fun setSteamApiKey(value: String) {
        viewModelScope.launch { settingsRepository.setSteamApiKeyOverride(value) }
    }

    fun setSteamGridApiKeyOverride(value: String) {
        viewModelScope.launch {
            settingsRepository.setSteamGridApiKey(if (value.isBlank()) null else value)
        }
    }

    fun setTheGamesDbApiKey(value: String) {
        viewModelScope.launch { settingsRepository.setTheGamesDbApiKeyOverride(value) }
    }

    // --- AI assistant ---
    val aiBottomTabVisible: StateFlow<Boolean> = settingsRepository.aiBottomTabVisible
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    fun setAiBottomTabVisible(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAiBottomTabVisible(enabled) }
    }

    val achievementsTabVisible: StateFlow<Boolean> = settingsRepository.achievementsTabVisible
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    /**
     * The same setting, null until the stored value actually arrives.
     *
     * [achievementsTabVisible] starts on the default (true) while DataStore is still being read,
     * which is right for a toggle row (it shows the default it would apply) and wrong for anything
     * that hides itself when the feature is off: a user who had turned it off saw the control flash
     * on at every cold start. Null lets such a caller tell "off" from "not known yet".
     */
    val achievementsTabVisibleOrNull: StateFlow<Boolean?> = settingsRepository.achievementsTabVisible
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun setAchievementsTabVisible(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAchievementsTabVisible(enabled) }
    }

    val appWallpaperImage: StateFlow<String?> = settingsRepository.appWallpaperImage
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun setAppWallpaperImage(uri: String?) {
        viewModelScope.launch { settingsRepository.setAppWallpaperImage(uri) }
    }

    val geminiApiKey: StateFlow<String> = settingsRepository.geminiApiKey
        .map { it ?: "" }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    fun setGeminiApiKey(value: String) {
        viewModelScope.launch {
            settingsRepository.setGeminiApiKey(if (value.isBlank()) null else value)
            // Entering a key implies the user wants the assistant — surface the tab.
            if (value.isNotBlank()) settingsRepository.setAiBottomTabVisible(true)
        }
    }

    val geminiModel: StateFlow<String> = settingsRepository.geminiModel
        .stateIn(viewModelScope, SharingStarted.Eagerly, "gemini-flash-latest")

    fun setGeminiModel(value: String) {
        viewModelScope.launch { settingsRepository.setGeminiModel(value) }
    }

    fun setDefaultAssetType(type: DefaultAssetType) {
        viewModelScope.launch {
            settingsRepository.setDefaultAssetType(type)
        }
    }
    
    fun setPowerSaverMode(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setPowerSaverMode(enabled)
        }
    }
    
    fun setAutoFocusColorDetection(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAutoFocusColorDetection(enabled)
            // Drive the hardware straight away rather than leaving it on the colour the accent
            // just stopped being. Turning adaptive OFF restores the manual accent, which is
            // exactly what this reapplies; turning it ON shows that accent until real artwork
            // supplies one, which the live-preview collector then takes over.
            reapplyLedPreview()
        }
    }
    
    fun setButtonGlyphStyle(style: com.twinlore.app.data.ButtonGlyphStyle) {
        viewModelScope.launch {
            settingsRepository.setButtonGlyphStyle(style)
        }
    }

    fun setSwapFaceButtons(swap: Boolean) {
        viewModelScope.launch {
            settingsRepository.setSwapFaceButtons(swap)
        }
    }

    fun setHapticFeedbackIntensity(intensity: HapticFeedbackIntensity) {
        viewModelScope.launch {
            settingsRepository.setHapticFeedbackIntensity(intensity)
        }
    }
    
    fun setFocusSoundEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setFocusSoundEnabled(enabled)
        }
    }

    fun setLandingEntry(slotIndex: Int, entry: LandingEntry) {
        viewModelScope.launch {
            settingsRepository.setLandingEntry(slotIndex, entry)
        }
    }

    fun setLedEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setLedEnabled(enabled)
            if (enabled) {
                // The colour the rings would show anyway, not the palette
                // match: with adaptive colour on those differ, and the toggle
                // used to relight them in the match.
                val accent = settingsRepository.currentAccentColor()
                val ledBrightness = settingsRepository.ledBrightness.first()
                val ledSaturationBoost = settingsRepository.ledSaturationBoost.first()
                val turnOff = settingsRepository.ledTurnOffTime.first()
                ThorLedManager.onFocusGained(
                    accent,
                    true,
                    ledBrightness,
                    ledSaturationBoost,
                    turnOff.inactivityMs
                )
            } else {
                ThorLedManager.releaseControl()
            }
        }
    }

    fun setLedBrightness(brightness: Int) {
        viewModelScope.launch {
            settingsRepository.setLedBrightness(brightness)
            reapplyLedPreview()
        }
    }

    fun setLedSaturationBoost(boost: Float) {
        viewModelScope.launch {
            settingsRepository.setLedSaturationBoost(boost)
            reapplyLedPreview()
        }
    }

    /**
     * Re-apply the LED with the current focus colour + LED settings so a brightness/saturation
     * tweak is visible immediately. No-op when the LED is disabled.
     */
    private suspend fun reapplyLedPreview() {
        if (!settingsRepository.ledEnabled.first()) return
        val accent = settingsRepository.currentAccentColor()
        val ledBrightness = settingsRepository.ledBrightness.first()
        val ledSaturationBoost = settingsRepository.ledSaturationBoost.first()
        val turnOff = settingsRepository.ledTurnOffTime.first()
        ThorLedManager.setColor(
            accent,
            true,
            ledBrightness,
            ledSaturationBoost,
            turnOff.inactivityMs
        )
    }

    fun setGuideThemeInject(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setGuideThemeInject(enabled)
        }
    }

    fun setGuideSmartFormatting(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setGuideSmartFormatting(enabled)
        }
    }

    fun setPrivacyBlockingEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setPrivacyBlockingEnabled(enabled)
        }
    }

    fun setGuideScrollMarkersBarVisible(visible: Boolean) {
        viewModelScope.launch {
            settingsRepository.setGuideScrollMarkersBarVisible(visible)
        }
    }

    fun setShowLibraryItemNames(show: Boolean) {
        viewModelScope.launch {
            settingsRepository.setShowLibraryItemNames(show)
        }
    }

    fun setLibraryHintBarVisible(visible: Boolean) {
        viewModelScope.launch {
            settingsRepository.setLibraryHintBarVisible(visible)
        }
    }

    fun setLibrarySortField(field: LibrarySortField) {
        viewModelScope.launch {
            settingsRepository.setLibrarySortField(field)
        }
    }

    fun setLibrarySortDirection(dir: SortDirection) {
        viewModelScope.launch {
            settingsRepository.setLibrarySortDirection(dir)
        }
    }

    fun setLibraryAchievementProgress(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setLibraryAchievementProgress(enabled)
        }
    }

    // ── Library folder refactor ──

    fun setHomeFolderUri(uri: String?) {
        viewModelScope.launch {
            settingsRepository.setHomeFolderUri(uri)
        }
    }

    /** Persist a SAF tree URI as the storage folder and take persistable permission. */
    suspend fun pickHomeFolder(uri: Uri) = homeFolderSetupCoordinator.useSafTree(uri)

    /** Number of resource_files rows that would be migrated (homeRelativePath set). */
    suspend fun countMigratableFiles(): Int = storageFolderMigrationCoordinator.countMigratableFiles()

    /**
     * Copy every storage-rooted file from [oldTreeUri] to [newTreeUri], updating
     * `resource_files` rows to point at their new location, and persist [newTreeUri]
     * as the active storage folder. The caller must have already taken persistable
     * permission for [newTreeUri] (the SAF picker callback does this).
     */
    suspend fun migrateStorageFolder(
        oldTreeUri: Uri,
        newTreeUri: Uri,
        progress: (com.twinlore.app.data.library.StorageFolderMigrationCoordinator.Progress) -> Unit
    ): Int = storageFolderMigrationCoordinator.migrate(oldTreeUri, newTreeUri, progress)

    /**
     * Switch the active storage folder to [newTreeUri] without copying any files.
     * Existing rows pinned to the old tree will fail to resolve until the user
     * moves them manually. Caller must have warned the user.
     */
    suspend fun switchStorageWithoutMigration(newTreeUri: Uri) =
        storageFolderMigrationCoordinator.switchWithoutMigration(newTreeUri)

    /**
     * Wipe all library metadata: vaults, saved tabs, markers, bookmarks, etc.
     * Cascade FKs handle child rows. Also deletes this device's per-device
     * snapshot from the library folder so the storage folder reflects the
     * cleared state immediately. Other devices' snapshots in the same folder
     * are not ours to touch. Per-vault file folders are NOT touched — call
     * [deleteAllFilesInStorageFolder] separately if the user also asked to
     * delete the on-disk content.
     */
    suspend fun resetLibraryMetadata() {
        vaultRepository.deleteAllVaults()
        runCatching {
            val deviceId = settingsRepository.getOrCreateDeviceId()
            libraryFileStore.deleteIfPresent(deviceId)
        }
        com.twinlore.app.ui.utils.ImagePathUtils.invalidateAllCache()
    }

    /**
     * Delete every immediate child of the current storage folder (file://
     * tree or content:// SAF tree). The folder itself is preserved so the
     * persistable SAF permission survives. Returns the number of children
     * deleted. Returns 0 silently if no storage folder is configured.
     */
    suspend fun deleteAllFilesInStorageFolder(): Int {
        val uri = settingsRepository.homeFolderUri.first()?.let(Uri::parse) ?: return 0
        return libraryFolderManager.deleteAllChildrenIn(uri)
    }

    /**
     * Save the library snapshot to this device's per-device file in the
     * library folder. Returns the outcome on success, or null if the library
     * folder is unset.
     */
    suspend fun exportLibraryNow(): LibraryFileSaveOutcome? =
        libraryAutoSaveObserver.saveNow()

    /**
     * Load the newest snapshot in [source] into the live DB, then adopt [source]
     * as the home folder. The import runs first so a too-new / unreadable
     * snapshot is refused ([com.twinlore.app.data.library.LibrarySnapshotTooNewException])
     * BEFORE the home folder is switched — a refused load never strands the app
     * on an unreadable folder. Caller must restart on success.
     */
    suspend fun loadLibraryFromFolder(source: Uri) =
        libraryLoadCoordinator.loadFromFolder(source)

    /**
     * Returns how many library snapshots exist inside [folderUri] (file:// or
     * content://). Zero means none; one means the common solo-device case; more
     * than one means the user has a shared synced folder. The newest will be
     * imported; callers may use the count to inform dialog wording.
     */
    suspend fun findLibrarySnapshot(folderUri: Uri): Int =
        com.twinlore.app.data.library
            .findSnapshotsNewestFirst(context, folderUri, currentShortDeviceId = "")
            .size

    /**
     * Effective schema version of the newest snapshot in [folderUri] (the one a
     * load would import), or null if the folder has no readable snapshot. The
     * value is authoritative (max of library_meta.schemaVersion and Room's
     * PRAGMA user_version), so the Settings load flow can refuse a too-new
     * library up front instead of switching storage, copying it, and
     * destructive-migrating into the cold-start restart loop.
     */
    suspend fun newestSnapshotSchemaVersion(folderUri: Uri): Int? {
        val newest = com.twinlore.app.data.library
            .findSnapshotsNewestFirst(context, folderUri, currentShortDeviceId = "")
            .firstOrNull() ?: return null
        return com.twinlore.app.data.library.readSnapshotSchemaVersion(context, newest)
    }

    /** Run [block] under the auto-save mutex so import and ON_PAUSE saves can't race. */
    suspend fun <T> runExclusiveSave(block: suspend () -> T): T =
        libraryAutoSaveObserver.exclusive(block)

    /** Classify a RetroAchievements validation error into a user-friendly message. */
    private fun classifyRaValidationError(e: Throwable): String = when {
        e is UnknownHostException || e is ConnectException ->
            "Could not reach RetroAchievements. Check your internet connection and try again."
        e is SocketTimeoutException ->
            "Could not reach RetroAchievements. Check your internet connection and try again."
        e is retrofit2.HttpException && e.code() == 404 ->
            "RetroAchievements account not found. Check the username and try again."
        e is retrofit2.HttpException && e.code() in listOf(401, 403) ->
            "Invalid username or API key. Check your credentials in RetroAchievements settings."
        e is retrofit2.HttpException && e.code() in 500..599 ->
            "RetroAchievements server is temporarily unavailable. Try again later."
        e is retrofit2.HttpException ->
            "Request to RetroAchievements failed (HTTP ${e.code()}). Try again later."
        e is java.io.IOException ->
            "Connection failed. Check your internet connection."
        else ->
            "Validation failed. Please try again."
    }

    /** Classify a Steam validation error into a user-friendly message. */
    private fun classifySteamValidationError(e: Throwable): String = when {
        e is UnknownHostException || e is ConnectException ->
            "Could not reach Steam. Check your internet connection and try again."
        e is SocketTimeoutException ->
            "Could not reach Steam. Check your internet connection and try again."
        e is retrofit2.HttpException && e.code() in listOf(401, 403) ->
            "Invalid Steam API key or profile. Check your credentials."
        e is retrofit2.HttpException && e.code() in 500..599 ->
            "Steam server is temporarily unavailable. Try again later."
        e is retrofit2.HttpException ->
            "Request to Steam failed (HTTP ${e.code()}). Try again later."
        e is java.io.IOException ->
            "Connection failed. Check your internet connection."
        e.message?.contains("Could not resolve Steam profile") == true ->
            e.message!!
        else ->
            "Validation failed. Please try again."
    }

    // ─── Library media sources section ─────────────────────────────────────────

    private val _searchPaths = MutableStateFlow<List<com.twinlore.app.domain.MediaSearchPath>>(emptyList())
    val searchPaths: StateFlow<List<com.twinlore.app.domain.MediaSearchPath>> = _searchPaths.asStateFlow()

    private val _indexFileCount = MutableStateFlow(0)
    val indexFileCount: StateFlow<Int> = _indexFileCount.asStateFlow()

    /** Live scan state, sourced directly from MediaIndexer (background-scope). */
    val scanState: StateFlow<com.twinlore.app.data.library.MediaIndexer.ScanState> = mediaIndexer.state

    val preferScraperForArtwork: Flow<Boolean> = settingsRepository.preferScraperForArtwork

    fun setPreferScraperForArtwork(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setPreferScraperForArtwork(enabled) }
    }

    val surfaceLocalImagesInMapsTab: Flow<Boolean> = settingsRepository.surfaceLocalImagesInMapsTab

    fun setSurfaceLocalImagesInMapsTab(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setSurfaceLocalImagesInMapsTab(enabled) }
    }

    private val _searchPathStatuses =
        MutableStateFlow<Map<String, com.twinlore.app.domain.MediaSearchPathStatus>>(emptyMap())
    val searchPathStatuses: StateFlow<Map<String, com.twinlore.app.domain.MediaSearchPathStatus>> =
        _searchPathStatuses.asStateFlow()

    sealed class RemoveDialogState {
        object Hidden : RemoveDialogState()
        data class Shown(
            val pathId: String,
            val pathLabel: String,
            val mode: RemoveSearchPathMode,
            val affectedCount: Int,
        ) : RemoveDialogState()
    }

    private val _removeDialogState = MutableStateFlow<RemoveDialogState>(RemoveDialogState.Hidden)
    val removeDialogState: StateFlow<RemoveDialogState> = _removeDialogState.asStateFlow()

    fun requestRemoveSearchPath(id: String) {
        viewModelScope.launch {
            val row = mediaSearchPathRepository.getAll().firstOrNull { it.id == id } ?: return@launch
            val count = mediaSearchPathRepository.countAffectedVaults(id)
            val mode = if (row.origin == com.twinlore.app.domain.MediaSearchPath.Origin.MANUAL)
                RemoveSearchPathMode.REMOVE
            else
                RemoveSearchPathMode.DISABLE
            _removeDialogState.value = RemoveDialogState.Shown(id, row.label, mode, count)
        }
    }

    fun confirmRemoveDialog() {
        val s = _removeDialogState.value as? RemoveDialogState.Shown ?: return
        viewModelScope.launch {
            when (s.mode) {
                RemoveSearchPathMode.REMOVE -> mediaSearchPathRepository.removeWithCleanup(s.pathId)
                RemoveSearchPathMode.DISABLE -> mediaSearchPathRepository.disableWithCleanup(s.pathId)
            }
            _removeDialogState.value = RemoveDialogState.Hidden
            reloadSearchPaths()
        }
    }

    fun dismissRemoveDialog() { _removeDialogState.value = RemoveDialogState.Hidden }

    fun enableSearchPath(id: String) {
        viewModelScope.launch {
            mediaSearchPathRepository.enable(id)
            reloadSearchPaths()
            mediaIndexer.startScanForPath(id)
        }
    }

    fun reloadSearchPaths() {
        viewModelScope.launch {
            mediaSearchPathRepository.syncAutoFromSettings()
            _searchPaths.value = mediaSearchPathRepository.getAll()
            _indexFileCount.value = mediaSearchIndexDao.count()
            _searchPathStatuses.value = mediaSearchPathRepository.validate()
        }
    }

    fun relabelSearchPath(id: String, newLabel: String) {
        viewModelScope.launch {
            mediaSearchPathRepository.relabel(id, newLabel)
            reloadSearchPaths()
        }
    }

    fun addManualSearchPath(uri: Uri, label: String) {
        viewModelScope.launch {
            val path = mediaSearchPathRepository.add(uri, label)
            mediaIndexer.startScanForPath(path.id)
            reloadSearchPaths()
        }
    }

    /** Triggered by the Scan button in Settings. Runs in appScope; toast on done. */
    fun startScan() { mediaIndexer.startScanAll() }

    /** Cancel currently-running scan (Scan button → Cancel while running). */
    fun cancelScan() { mediaIndexer.cancel() }

    // License management
    val licenseState: Flow<LicenseState> = licenseManager.getLicenseState()

    /**
     * UI-projection of the license state. Used by the new License section
     * and the premium-feature paywall handlers.
     */
    val licenseUiState: StateFlow<com.twinlore.app.data.license.LicenseUiState> =
        licenseManager.licenseUiState

    /**
     * Activate a license key
     */
    suspend fun activateLicense(licenseKey: String, email: String?): Result<com.twinlore.app.data.license.ActivationResponse> {
        return licenseManager.activateLicense(licenseKey, email)
    }

    /**
     * Validate license (manual trigger)
     */
    suspend fun validateLicense(): Result<com.twinlore.app.data.license.ValidationResponse> {
        return licenseManager.validateLicense()
    }

    /**
     * Deactivate license instance (frees up activation slot)
     */
    suspend fun deactivateLicense(): Result<com.twinlore.app.data.license.DeactivationResponse> {
        return licenseManager.deactivateInstance()
    }
}



