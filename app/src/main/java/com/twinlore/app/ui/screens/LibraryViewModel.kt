package com.twinlore.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import android.net.Uri
import com.twinlore.app.data.ArtDisplayMode
import dagger.hilt.android.qualifiers.ApplicationContext
import com.twinlore.app.data.ESDELogParser
import com.twinlore.app.data.LibraryViewMode
import com.twinlore.app.data.LastPlayedSource
import com.twinlore.app.data.SyncLatestPlayedGameUseCase
import com.twinlore.app.data.VaultRepository
import com.twinlore.app.data.library.LocalMediaBinder
import com.twinlore.app.data.library.ResourceResolver
import com.twinlore.app.util.PerfLog
import com.twinlore.app.data.scraping.VaultScrapeCoordinator
import com.twinlore.app.data.remote.SteamGridAsset
import com.twinlore.app.domain.AchievementSortInfo
import com.twinlore.app.domain.LibraryAchievementCalc
import com.twinlore.app.domain.AddFileResult
import com.twinlore.app.domain.AssetType
import com.twinlore.app.domain.ResourceKind
import com.twinlore.app.data.retroachievements.RetroAchievementsCredentialsRepository
import com.twinlore.app.data.local.ResourceFileDao
import com.twinlore.app.data.local.toDomain
import com.twinlore.app.domain.ResourceFile
import com.twinlore.app.domain.ResourceSource
import com.twinlore.app.domain.Result
import com.twinlore.app.domain.Vault
import com.twinlore.app.ui.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.io.FileOutputStream
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.URL
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import java.io.File

/** Per-category caps for the artwork picker: keep only the best-ratio candidates
 *  so the picker stays focused instead of showing dozens of near-duplicates. */
internal const val GRID_OPTION_CAP = 15
internal const val HERO_OPTION_CAP = 15
internal const val LOGO_OPTION_CAP = 10

/**
 * Data class for artwork option from scraping
 */
data class ArtworkOption(
    val url: String,
    val thumbnailUrl: String?,
    val type: String,                   // "Grid", "Logo", "Hero", or "Local"
    val index: Int,                     // 1-based index within type
    val isLocal: Boolean = false,
    val resourceFileId: String? = null, // populated when isLocal=true
    val width: Int? = null,
    val height: Int? = null,
)

/** Sort artwork options so the candidate whose aspect ratio is closest to
 *  [targetRatio] (width/height) comes first; options with unknown dimensions
 *  sort to the end (stable within each group). Mirrors the auto-pick target
 *  (VaultScrapeCoordinator gridDisplayRatio) so the picker's first item is
 *  what auto-assign would have chosen. */
internal fun sortArtworkOptionsByRatio(options: List<ArtworkOption>, targetRatio: Float): List<ArtworkOption> {
    if (targetRatio <= 0f) return options
    return options.sortedBy { o ->
        val w = o.width; val h = o.height
        if (w != null && h != null && w > 0 && h > 0) kotlin.math.abs(w.toFloat() / h - targetRatio)
        else Float.MAX_VALUE
    }
}

/**
 * Result of loading artwork options for the "Scrape Artwork" picker
 * dialog. Distinguishes success (possibly empty — no match) from a
 * typed error that the dialog surfaces to the user.
 */
sealed class ArtworkOptionsResult {
    data class Success(val options: List<ArtworkOption>) : ArtworkOptionsResult()
    data class Error(val message: String) : ArtworkOptionsResult()
}


/**
 * ViewModel for Library screen
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val vaultRepository: VaultRepository,
    private val vaultScrapeCoordinator: VaultScrapeCoordinator,
    private val retroArchHistoryParser: com.twinlore.app.data.RetroArchHistoryParser,
    private val esdeLogParser: com.twinlore.app.data.ESDELogParser,
    private val cocoonLogParser: com.twinlore.app.data.CocoonLogParser,
    private val settingsRepository: com.twinlore.app.data.SettingsRepository,
    private val steamGridClient: com.twinlore.app.data.scraping.SteamGridClient,
    private val gameNameSanitizer: com.twinlore.app.data.scraping.GameNameSanitizer,
    private val licenseManager: com.twinlore.app.data.license.LicenseManager,
    private val syncLatestPlayedGameUseCase: SyncLatestPlayedGameUseCase,
    private val raCredentialsRepository: RetroAchievementsCredentialsRepository,
    private val steamCredentialsRepository: com.twinlore.app.data.steam.SteamCredentialsRepository,
    private val steamApiClient: com.twinlore.app.data.steam.SteamApiClient,
    val raGameCacheRepository: com.twinlore.app.data.retroachievements.RaGameCacheRepository,
    val steamGameCacheRepository: com.twinlore.app.data.steam.SteamGameCacheRepository,
    val raProfileRepository: com.twinlore.app.data.retroachievements.RaProfileRepository,
    private val fuzzyNameMatcher: com.twinlore.app.data.FuzzyNameMatcher,
    private val resourceFileDao: ResourceFileDao,
    private val localMediaBinder: LocalMediaBinder,
    private val mediaSearchPathRepository: com.twinlore.app.data.library.MediaSearchPathRepository,
    private val mediaIndexer: com.twinlore.app.data.library.MediaIndexer,
    private val libraryAutoSaveObserver: com.twinlore.app.data.library.LibraryAutoSaveObserver,
    private val libraryFolderManager: com.twinlore.app.data.library.LibraryFolderManager,
    private val resourceResolver: ResourceResolver,
    private val artworkThumbnailCache: com.twinlore.app.data.library.ArtworkThumbnailCache,
    private val supportPromptGate: com.twinlore.app.ui.license.SupportPromptGate,
    @ApplicationContext private val context: Context
) : BaseViewModel() {

    val hasRaCredentials: StateFlow<Boolean> = raCredentialsRepository.credentials
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    // Cache the last known vault list to use as initial state in stateIn
    // This prevents empty state flash when StateFlow is first created
    private val _cachedVaults = MutableStateFlow<List<Vault>>(emptyList())


    /** URI for which discoverVaultsFromFileSystem() has already completed.
     *  Prevents re-running the expensive SAF walk on every return from vault view. */
    private var discoveredForUri: String? = null

    // Refresh trigger to force UI updates when new vaults are created
    private val _vaultsRefreshTrigger = MutableStateFlow(0)
    
    /** When artwork is assigned we set (vaultId, timestamp) so blur requests use a new cache key and load fresh (path often unchanged e.g. art/artwork.jpg). */
    private val _blurArtworkInvalidateTimestamp = MutableStateFlow<Pair<String, Long>?>(null)
    val blurArtworkInvalidateTimestamp: StateFlow<Pair<String, Long>?> = _blurArtworkInvalidateTimestamp

    /** Persisted across Library screen visits so we only delay 500ms on first load, not every time we return from vault. */
    private val _hasSeenDatabaseLoad = MutableStateFlow(false)
    val hasSeenDatabaseLoad: StateFlow<Boolean> = _hasSeenDatabaseLoad.asStateFlow()

    /** True once the first batch of grid artwork images have been preloaded by Coil.
     *  Persisted in ViewModel so returning from vault view doesn't re-trigger the preload gate. */
    private val _imagesPreloaded = MutableStateFlow(false)
    val imagesPreloaded: StateFlow<Boolean> = _imagesPreloaded.asStateFlow()

    fun setImagesPreloaded(value: Boolean) {
        _imagesPreloaded.value = value
    }

    /** Vault ids already handed to [warmArtworkThumbnails], so a re-entry is a no-op. */
    private val thumbnailWarmRequested = java.util.Collections.newSetFromMap(
        java.util.concurrent.ConcurrentHashMap<String, Boolean>()
    )

    /**
     * Queue thumbnail generation for EVERY vault with artwork, up front and off the scroll
     * path, instead of leaving it to happen lazily inside whichever tile scrolls into view
     * first.
     *
     * The thumbnail cache already self-heals — [VaultGridItem] calls `generateAsync` on a
     * miss — but only for tiles the user actually scrolls past, so coverage depends on
     * browsing habits. This makes it deterministic instead.
     *
     * What was measured on the S24 (39 vaults, thumbnail dir wiped, five runs each):
     *   - coverage 25s after launch WITHOUT scrolling: 22 thumbnails without this, 39 with it.
     *   - startup CPU over the first 25s: 13.74s vs 13.98s cold, 9.39-9.61s vs 9.27s warm —
     *     i.e. unchanged within run-to-run noise. It does not buy startup time and does not
     *     cost any either.
     *   - scroll smoothness on that device: NO measurable difference. Both sit at 5-7% janky
     *     frames with ~95% of frames presented on consecutive vsyncs. Do not claim a scroll
     *     win for this; the honest justification is deterministic coverage, so a user who
     *     never scrolls to the bottom is not left with a permanently cold tail.
     *
     * Why the ViewModel and not LibraryScreen's preload LaunchedEffect: that effect is keyed
     * on (vaults, isLoading) and genuinely is cancelled and restarted during startup, and it
     * early-returns on `imagesPreloaded`, so work its first pass did not finish was never
     * retried. viewModelScope outlives any single composition.
     * [ArtworkThumbnailCache.generateAsync] is itself bounded and deduplicated, so handing it
     * the whole library does not start N simultaneous decodes.
     */
    fun warmArtworkThumbnails(vaults: List<Vault>) {
        val pending = vaults.filter {
            it.hasArtwork && it.artworkResourceId != null && thumbnailWarmRequested.add(it.id)
        }
        if (pending.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            var queued = 0
            for (vault in pending) {
                // Cooperative cancellation. The preload path this complements used to catch
                // Exception, which swallowed CancellationException and let a dead coroutine
                // walk the whole batch doing nothing.
                ensureActive()
                if (artworkThumbnailCache.pathFor(vault.id) != null) continue
                val rid = vault.artworkResourceId ?: continue
                // Not runCatching: that would swallow CancellationException exactly the way
                // the preload path used to.
                val uri = try {
                    resourceResolver.resolve(rid)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                } ?: continue
                artworkThumbnailCache.generateAsync(vault.id, uri)
                queued++
            }
            if (queued > 0) {
                android.util.Log.d("LibraryPreload", "thumbnail warm queued $queued of ${pending.size}")
            }
        }
    }

    fun setHasSeenDatabaseLoad(value: Boolean) {
        _hasSeenDatabaseLoad.value = value
    }

    /** True once the vaults flow has emitted at least once (initial DB query completed). */
    private val _vaultsInitialLoadComplete = MutableStateFlow(false)
    val vaultsInitialLoadComplete: StateFlow<Boolean> = _vaultsInitialLoadComplete.asStateFlow()
    
    /**
     * All vaults from the database, served directly without disk-existence checks.
     * The DB is the source of truth — if the storage folder changes, the DB is cleared
     * and re-populated by discovery. If a vault's files are missing on disk, the user
     * is warned when they try to open it (lazy validation).
     *
     * Uses StateFlow with stateIn to keep state hot in ViewModel, preventing reinitialization
     * when returning to Library screen from Browser/Settings.
     */
    val vaults: StateFlow<List<Vault>> = _vaultsRefreshTrigger
        .flatMapLatest {
            vaultRepository.getAllVaultsSortedByLastOpened()
                .map { allVaults ->
                    if (!_vaultsInitialLoadComplete.value) {
                        PerfLog.mark("LibraryViewModel first vaults emit (count=${allVaults.size})")
                    }
                    _cachedVaults.value = allVaults
                    _vaultsInitialLoadComplete.value = true

                    // If the currently focused vault no longer exists in the list, clear focus.
                    val currentFocusedId = _focusedVaultId.value
                    val focusedVaultStillExists = currentFocusedId != null && allVaults.any { it.id == currentFocusedId }
                    if (currentFocusedId != null && !focusedVaultStillExists) {
                        _focusedVaultId.value = null
                    }

                    allVaults
                }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = _cachedVaults.value
        )

    /**
     * Per-vault achievement completion, derived live from the new count columns
     * in `ra_game_cache` and `steam_game_cache` (no JSON parsing on the hot path).
     * Emits whenever vaults change OR either cache table is written
     * (cold-start bulk, drawer fetch, vault delete).
     */
    val achievementSortInfo: StateFlow<Map<String, AchievementSortInfo>> = kotlinx.coroutines.flow.combine(
        vaults,
        raGameCacheRepository.countsFlow(),
        steamGameCacheRepository.countsFlow(),
    ) { vaultList, raRows, steamRows ->
        if (vaultList.isEmpty()) return@combine emptyMap()
        val raByVault = raRows.associateBy { it.vaultId }
        val steamByVault = steamRows.associateBy { it.vaultId }
        vaultList.associate { vault ->
            val ra = raByVault[vault.id]
            val steam = steamByVault[vault.id]
            val raSoft = ra?.let { LibraryAchievementCalc.fraction(it.numAwardedSoft, it.numAchievements) } ?: 0f
            val raHard = ra?.let { LibraryAchievementCalc.fraction(it.numAwardedHardcore, it.numAchievements) } ?: 0f
            val steamFrac = steam?.let { LibraryAchievementCalc.fraction(it.numUnlocked, it.numAchievements) } ?: 0f
            vault.id to AchievementSortInfo(
                softcoreFraction = maxOf(raSoft, steamFrac),
                hardcoreFraction = maxOf(raHard, steamFrac),
                raAwardKind = ra?.highestAwardKind,
            )
        }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyMap(),
        )

    /**
     * Get art display mode from settings
     */
    val artDisplayMode: Flow<ArtDisplayMode> = settingsRepository.artDisplayMode
    
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    
    private val _scrapingProgress = MutableStateFlow<VaultScrapeCoordinator.ScrapingProgress?>(null)
    val scrapingProgress: StateFlow<VaultScrapeCoordinator.ScrapingProgress?> = _scrapingProgress.asStateFlow()

    private val _scrapeError = MutableStateFlow<com.twinlore.app.data.scraping.ScrapeError?>(null)
    val scrapeError: StateFlow<com.twinlore.app.data.scraping.ScrapeError?> = _scrapeError.asStateFlow()

    fun onScrapeErrorShown() { _scrapeError.value = null }

    /**
     * True while vault discovery/sync is running after a storage folder change.
     *
     * Declared above [init] on purpose, and it has to stay there. The block
     * below launches on `viewModelScope` - `Dispatchers.Main.immediate`, which
     * starts running synchronously - and that coroutine reaches this flow via
     * discoverVaultsFromFileSystem(). Kotlin initialises properties in source
     * order, so declared below the block it is still null when the coroutine
     * gets to it, and the app dies on the first frame with an NPE out of
     * MutableStateFlow.setValue.
     */
    private val _isDiscovering = MutableStateFlow(false)
    val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()

    init {
        PerfLog.mark("LibraryViewModel.init begin")
        // Scrape errors are surfaced by the progress dialog itself (persistent,
        // with Retry / Dismiss), so they're no longer mirrored into a snackbar
        // — the duplicate banner read as noise, especially on a failed retry.

        // Observe storage folder changes and trigger vault discovery.
        // Runs in the ViewModel (not a composable LaunchedEffect) so returning
        // from vault view never re-triggers — the ViewModel survives Crossfade
        // recomposition. DataStore emits the real value on first collection,
        // avoiding the null→real transient that collectAsState(initial=null) had.
        viewModelScope.launch {
            settingsRepository.vaultStorageFolderUri.collect { uri ->
                val effectiveUri = uri ?: "default"
                discoverVaultsFromFileSystem(uri = effectiveUri)
            }
        }
        PerfLog.mark("LibraryViewModel.init end")
    }
    
    private val _isScraping = MutableStateFlow(false)
    val isScraping: StateFlow<Boolean> = _isScraping.asStateFlow()

    /**
     * Fires when a refresh or folder pick lands on a RetroArch folder
     * where no parseable content_history.lpl was found. The UI auto-opens
     * RetroArchHelpDialog with the setup-steps reminder. Consumed via
     * dismissRetroArchHelpDialog.
     */
    private val _pendingRetroArchHelpDialog = MutableStateFlow(false)
    val pendingRetroArchHelpDialog: StateFlow<Boolean> =
        _pendingRetroArchHelpDialog.asStateFlow()

    fun dismissRetroArchHelpDialog() { _pendingRetroArchHelpDialog.value = false }

    private val _pendingESDEHelpDialog =
        MutableStateFlow<com.twinlore.app.data.ESDELogParser.ESDEFolderState?>(null)
    val pendingESDEHelpDialog: StateFlow<com.twinlore.app.data.ESDELogParser.ESDEFolderState?> =
        _pendingESDEHelpDialog.asStateFlow()

    fun dismissESDEHelpDialog() { _pendingESDEHelpDialog.value = null }

    private val _pendingCocoonHelpDialog =
        MutableStateFlow<com.twinlore.app.data.CocoonLogParser.CocoonFolderState?>(null)
    val pendingCocoonHelpDialog: StateFlow<com.twinlore.app.data.CocoonLogParser.CocoonFolderState?> =
        _pendingCocoonHelpDialog.asStateFlow()

    fun dismissCocoonHelpDialog() { _pendingCocoonHelpDialog.value = null }

    /**
     * Set when a folder picked from the choose-method dialog held none of that
     * launcher's files and, per [discardsFailedSourceSetup], nothing was saved.
     * The UI reopens the choose-method dialog showing this message so the user
     * can retry. Consumed via [clearSourceSetupFailure].
     */
    private val _sourceSetupFailure = MutableStateFlow<String?>(null)
    val sourceSetupFailure: StateFlow<String?> = _sourceSetupFailure.asStateFlow()

    fun clearSourceSetupFailure() { _sourceSetupFailure.value = null }

    /** Vault IDs currently being scraped (new vault or refresh). Used to show loading overlay on carousel items. */
    val scrapingVaultIds: StateFlow<Set<String>> = vaultScrapeCoordinator.scrapingVaultIds
    
    private val _needsFolderSelection = MutableStateFlow(false)
    val needsFolderSelection: StateFlow<Boolean> = _needsFolderSelection.asStateFlow()

    /** When true, last-played source needs setup (no credentials or not configured); show setup dialog. */
    private val _needsSourceSetup = MutableStateFlow(false)
    val needsSourceSetup: StateFlow<Boolean> = _needsSourceSetup.asStateFlow()

    fun clearNeedsSourceSetup() {
        _needsSourceSetup.value = false
    }

    /** One-shot toast emissions from per-vault local-media re-scan (long-press menu). */
    private val _localBindToast = MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 4)
    val localBindToast: SharedFlow<String> = _localBindToast.asSharedFlow()

    fun rescanLocalMediaForVault(vaultId: String) {
        viewModelScope.launch {
            val vault = vaultRepository.getVaultById(vaultId) ?: return@launch
            // Manual rescan never overwrites an existing artwork or guide. It only
            // fills empty slots. Users have explicit control via Assign Artwork.
            val report = localMediaBinder.bindForVault(vault, preserveExistingSlots = true)
            _localBindToast.emit(formatLocalMediaBindToast(report, vault.displayName))
        }
    }

    
    // Explicit focus state management for carousel
    private val _focusedVaultId = MutableStateFlow<String?>(null)
    val focusedVaultId: StateFlow<String?> = _focusedVaultId.asStateFlow()
    
    /** Pending fuzzy match dialog for manual creation: (existingVault, similarity, newName) — null when no dialog. */
    data class FuzzyMatchPrompt(val existingVault: com.twinlore.app.domain.Vault, val similarity: Double, val newName: String)
    private val _fuzzyMatchPrompt = MutableStateFlow<FuzzyMatchPrompt?>(null)
    val fuzzyMatchPrompt: StateFlow<FuzzyMatchPrompt?> = _fuzzyMatchPrompt.asStateFlow()

    /** Pending fuzzy match from auto-detection via SyncLatestPlayedGameUseCase (Achievements path). */
    val autoDetectFuzzyMatch: StateFlow<SyncLatestPlayedGameUseCase.PendingFuzzyMatch?> = syncLatestPlayedGameUseCase.pendingFuzzyMatch

    /** Pending fuzzy match from scrapeAllBoxArt detection (RetroArch/ES-DE path). */
    data class AutoDetectFuzzyMatchForScrape(
        val existingVault: com.twinlore.app.domain.Vault,
        val newName: String,
        val similarity: Double,
        val romPath: String?,
        val romFilename: String?,
        val source: String
    )
    private val _autoDetectFuzzyMatchForScrape = MutableStateFlow<AutoDetectFuzzyMatchForScrape?>(null)
    val autoDetectFuzzyMatchForScrape: StateFlow<AutoDetectFuzzyMatchForScrape?> = _autoDetectFuzzyMatchForScrape.asStateFlow()

    /** True briefly when focus was set by detection (auto-refresh). UI uses for LED breathe after fade-back. */
    private val _justFocusedByDetection = MutableStateFlow(false)
    val justFocusedByDetection: StateFlow<Boolean> = _justFocusedByDetection.asStateFlow()

    /**
     * Monotonic counter raised every time [_justFocusedByDetection] is set true (see
     * [raiseJustFocusedByDetection]). StateFlow is conflated, so a collector already
     * observing `true` sees no emission when the flag is set true again without an
     * intervening false (e.g. re-arming while a previous run left it sticky-true via
     * the Skip/detectionCancelled early return). LibraryScreen keys its raising
     * LaunchedEffect on this counter instead of the boolean so it reliably restarts.
     */
    private val _justFocusedByDetectionVersion = MutableStateFlow(0L)
    val justFocusedByDetectionVersion: StateFlow<Long> = _justFocusedByDetectionVersion.asStateFlow()

    private fun raiseJustFocusedByDetection() {
        _justFocusedByDetection.value = true
        _justFocusedByDetectionVersion.value++
    }

    /** Emits runId when detection (scrapeAllBoxArt) has applied focus. UI waits for its runId so a new detection run does not react to a previous run's emit. */
    private val _detectionFocusApplied = MutableSharedFlow<Long>(replay = 1)
    val detectionFocusApplied: SharedFlow<Long> = _detectionFocusApplied.asSharedFlow()

    /** True when detection was cancelled by the user (Skip). Detection LaunchedEffect checks this to skip entering idle mode. */
    private val _detectionCancelled = MutableStateFlow(false)
    val detectionCancelled: StateFlow<Boolean> = _detectionCancelled.asStateFlow()

    /** Monotonically increasing run id for detection; avoids collision when two runs start in the same millisecond. */
    private val nextDetectionRunId = AtomicLong(0L)
    fun getNextDetectionRunId(): Long = nextDetectionRunId.incrementAndGet()

    fun clearJustFocusedByDetection() {
        _justFocusedByDetection.value = false
    }

    /**
     * Focus [vaultId] and raise the showcase without running detection: the
     * lightweight open-last-played path for "nothing new, record known".
     * Returns false when the vault is not in the library (stale record).
     */
    fun focusVaultForShowcase(vaultId: String): Boolean {
        if (vaults.value.none { it.id == vaultId }) return false
        _focusedVaultId.value = vaultId
        raiseJustFocusedByDetection()
        return true
    }

    // Track if top bar is focused
    private val _isTopBarFocused = MutableStateFlow(false)
    val isTopBarFocused: StateFlow<Boolean> = _isTopBarFocused.asStateFlow()

    // Track if search modal is open
    private val _isSearchModalOpen = MutableStateFlow(false)
    val isSearchModalOpen: StateFlow<Boolean> = _isSearchModalOpen.asStateFlow()

    // "Support the development" prompt: fired by SupportPromptGate at value
    // events (vault created; journey exports fire on the Journey screen), at
    // most twice per install, at least a week apart.
    private val _supportPrompt =
        MutableStateFlow<com.twinlore.app.ui.license.SupportPromptGate.Prompt?>(null)
    val supportPrompt: StateFlow<com.twinlore.app.ui.license.SupportPromptGate.Prompt?> =
        _supportPrompt.asStateFlow()

    fun dismissSupportPrompt() {
        _supportPrompt.value = null
    }

    /**
     * Refresh summary for cap-skipped detections. Set by [LibraryViewModel.refresh]
     * after [SyncLatestPlayedGameUseCase] reports that one or more
     * detected games could not be persisted because the user is at the cap.
     */
    private val _refreshSummary = MutableStateFlow<RefreshSummary?>(null)
    val refreshSummary: StateFlow<RefreshSummary?> = _refreshSummary.asStateFlow()

    fun dismissRefreshSummary() {
        _refreshSummary.value = null
    }

    data class RefreshSummary(
        val skippedByLicenseCap: List<String>,
        val localBindEntries: List<LocalBindEntry> = emptyList(),
    )

    /** A vault's local-bind result with the vault display name resolved (UI-ready). */
    data class LocalBindEntry(
        val vaultName: String,
        val artworkBound: String?,    // filename, null if slot was already filled
        val guideBound: String?,
        val artworkAvailable: Int,
        val guideAvailable: Int,
    )

    /**
     * Emit a refresh summary if the new signals differ from the last persisted state.
     * Suppresses re-display when the same games would re-skip or re-bind on the next refresh.
     */
    private suspend fun maybeEmitRefreshSummary(
        skipped: List<String>,
        bindReports: List<com.twinlore.app.data.library.BindReport> = emptyList(),
    ) {
        if (skipped.isEmpty() && bindReports.isEmpty()) return

        // Hydrate bind reports with vault display names.
        val entries = bindReports.map { rep ->
            val vaultName = vaultRepository.getVaultById(rep.vaultId)?.displayName ?: ""
            LocalBindEntry(
                vaultName = vaultName,
                artworkBound = rep.artworkBound,
                guideBound = rep.guideBound,
                artworkAvailable = rep.artworkAvailable,
                guideAvailable = rep.guideAvailable,
            )
        }.filter { it.vaultName.isNotBlank() }

        // Dedup key includes both signals so we don't suppress a fresh local-bind result.
        val newHash = com.twinlore.app.ui.license.refreshSummaryDedupKey(skipped) +
            "::" + entries.joinToString(",") { "${it.vaultName}|${it.artworkBound ?: "-"}|${it.guideBound ?: "-"}" }
        val previousHash = settingsRepository.lastRefreshSkippedHash.first()
        if (newHash == previousHash) return
        settingsRepository.setLastRefreshSkippedHash(newHash)
        _refreshSummary.value = RefreshSummary(skippedByLicenseCap = skipped, localBindEntries = entries)
    }

    /**
     * Collect the use-case's last-run reports, bulk-bind any vaults not already covered by
     * detection, emit a refresh summary, and clear the use-case state.
     *
     * Call this at every site that previously called maybeEmitRefreshSummary(...) after
     * syncLatestPlayedGameUseCase.execute(), so the refresh button is useful even when
     * detection returns null (e.g. no network, ES-DE log empty).
     */
    /**
     * Bind reports collected during the RetroArch/ES-DE/Cocoon refresh path
     * (which doesn't use SyncLatestPlayedGameUseCase). The use case has its
     * own state flow that the ACHIEVEMENTS path drains.
     */
    private val _localBindReports = MutableStateFlow<List<com.twinlore.app.data.library.BindReport>>(emptyList())

    /**
     * Surface a "Linked N local assets" step in the same scraping progress dialog
     * the user sees during refresh, so auto-linking is visible inline rather than
     * via the separate post-refresh summary dialog. Only emits when something was
     * newly bound or rebound on this run (artworkBound / guideBound non-null);
     * silent when the bind confirmed an existing pick or found no candidates.
     */
    private suspend fun emitLocalBindStep(vaultId: String, report: com.twinlore.app.data.library.BindReport) {
        val newlyBound = listOfNotNull(report.artworkBound, report.guideBound).size
        if (newlyBound <= 0) return
        _scrapingProgress.value = VaultScrapeCoordinator.ScrapingProgress.Processing(
            vaultId = vaultId,
            message = "Linked $newlyBound local asset${if (newlyBound != 1) "s" else ""}"
        )
        kotlinx.coroutines.delay(900)
    }

    private suspend fun emitRefreshSummaryWithBulkBind() {
        val skipped = syncLatestPlayedGameUseCase.lastRunSkippedByCap.value
        val detectedFromUseCase = syncLatestPlayedGameUseCase.lastRunLocalBindReports.value
        val detectedFromLocalPath = _localBindReports.value
        val detectedReports = detectedFromUseCase + detectedFromLocalPath
        val detectedVaultIds = detectedReports.map { it.vaultId }.toSet()
        val extraReports = vaultRepository.getAllVaults()
            .filter { it.id !in detectedVaultIds }
            .mapNotNull { v -> localMediaBinder.bindForVault(v).takeIf { it.hasAny() } }
        maybeEmitRefreshSummary(skipped, detectedReports + extraReports)
        syncLatestPlayedGameUseCase.clearLastRunSkippedByCap()
        syncLatestPlayedGameUseCase.clearLastRunLocalBindReports()
        _localBindReports.value = emptyList()
    }

    // Scraping job for cancellation
    private var scrapingJob: Job? = null
    
    /**
     * Force vault list to re-query (e.g. after sync import so new vaults appear before setting focus).
     */
    fun refreshVaultList() {
        _vaultsRefreshTrigger.value++
    }

    /**
     * Call when artwork was assigned from outside the library (e.g. browser). Refreshes list and invalidates
     * blur/carousel cache for that vault so the new image shows without restart.
     */
    fun notifyArtworkAssignedForVault(vaultId: String) {
        refreshVaultList()
        _blurArtworkInvalidateTimestamp.value = vaultId to System.currentTimeMillis()
        com.twinlore.app.ui.utils.ImagePathUtils.invalidateCacheForVault(vaultId)
        invalidateCarouselCacheForVault(vaultId)
        invalidateGridCacheForVault(vaultId)
        // The caller only tells us which vault changed, not which resourceFileId
        // (assign flows mint a fresh id, so there's normally nothing stale to
        // drop). invalidateAll() is cheap here since this fires on a user action,
        // not the navigation hot path.
        resourceResolver.invalidateAll()
    }

    /**
     * Set the focused vault ID (explicit focus state).
     * Skips exiting idle mode when called during detection transition
     * (grid focus callbacks can fire while detection is applying focus under the black overlay).
     */
    fun setFocusedVault(vaultId: String?) {
        _focusedVaultId.value = vaultId
    }
    
    /**
     * Move focus to previous vault
     */
    fun focusPreviousVault(vaults: List<Vault>) {
        if (vaults.isEmpty()) return

        val currentId = _focusedVaultId.value
        val currentIndex = vaults.indexOfFirst { it.id == currentId }
        val newIndex = if (currentIndex <= 0) {
            vaults.size - 1 // Wrap around to last
        } else {
            currentIndex - 1
        }

        // Set new focused vault ID - this will trigger recomposition
        _focusedVaultId.value = vaults[newIndex].id
    }
    
    /**
     * Move focus to next vault
     */
    fun focusNextVault(vaults: List<Vault>) {
        if (vaults.isEmpty()) return

        val currentId = _focusedVaultId.value
        val currentIndex = vaults.indexOfFirst { it.id == currentId }
        val newIndex = if (currentIndex < 0 || currentIndex >= vaults.size - 1) {
            0 // Wrap around to first
        } else {
            currentIndex + 1
        }

        // Set new focused vault ID - this will trigger recomposition
        _focusedVaultId.value = vaults[newIndex].id
    }
    
    /**
     * Called when any input is received.
     */
    fun onInputReceived() {
        // No-op: idle mode removed.
    }

    /**
     * Set top bar focus state
     */
    fun setTopBarFocused(focused: Boolean) {
        _isTopBarFocused.value = focused
    }

    /**
     * Set search modal open state
     */
    fun setSearchModalOpen(open: Boolean) {
        _isSearchModalOpen.value = open
    }
    
    /**
     * Initialize focus to the most recently opened vault when vaults are loaded.
     * Falls back to the first vault (newest by creation date) if none have been opened.
     */
    fun initializeFocus(vaults: List<Vault>) {
        if (vaults.isNotEmpty() && _focusedVaultId.value == null) {
            val lastOpened = vaults.filter { it.lastOpened != null }.maxByOrNull { it.lastOpened!! }
            _focusedVaultId.value = (lastOpened ?: vaults.first()).id
        }
    }
    
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }
    
    fun updateLastOpened(vaultId: String) {
        viewModelScope.launch {
            vaultRepository.updateLastOpened(vaultId)
        }
    }

    /** Update lastOpened after a delay so the grid doesn't re-sort during the screen transition crossfade. */
    fun updateLastOpenedDeferred(vaultId: String) {
        viewModelScope.launch {
            delay(350) // after 250ms navigation crossfade completes
            vaultRepository.updateLastOpened(vaultId)
        }
    }

    /**
     * Persist extracted vibrant ARGB for a vault. Used when lazily computing color on first focus for vaults without stored metadata.
     */
    suspend fun persistVibrantArgb(vaultId: String, argb: Int): Result<Unit> =
        vaultRepository.updateArtworkVibrantArgb(vaultId, argb)
    
    /**
     * Discover vaults from the file system when the storage folder changes.
     * This ensures that vaults in the folder are represented in the database,
     * enabling the app to work when copying folders to a new device.
     */
    /**
     * Discover vaults from the file system. Skips if already completed for the
     * given [uri] (avoids repeating the expensive SAF walk on every return from vault).
     * Pass [force] = true when the URI genuinely changed (folder picker).
     */
    fun discoverVaultsFromFileSystem(uri: String? = null, force: Boolean = false) {
        if (!force && uri != null && uri == discoveredForUri) return // already done for this URI
        viewModelScope.launch {
            PerfLog.mark("discoverVaultsFromFileSystem begin (uri=${uri?.take(40)})")
            // On first call, if the DB already has vaults, trust the cache and skip.
            // This prevents the expensive SAF walk on normal app startup.
            // After a folder change the DB is cleared, so the count will be 0 and discovery runs.
            if (!force && discoveredForUri == null) {
                val hasVaults = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    vaultRepository.getVaultCount() > 0
                }
                if (hasVaults) {
                    discoveredForUri = uri
                    PerfLog.mark("discoverVaultsFromFileSystem skip (DB cache hit)")
                    return@launch
                }
            }

            _isDiscovering.value = true
            _imagesPreloaded.value = false
            try {
                // Small delay to allow downstream caches to settle
                delay(200)

                val t0 = android.os.SystemClock.elapsedRealtime()
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    vaultRepository.discoverVaultsFromFileSystem()
                }
                PerfLog.mark("discoverVaultsFromFileSystem walked in ${android.os.SystemClock.elapsedRealtime() - t0}ms")

                if (uri != null) discoveredForUri = uri

                // Always refresh after discovery — existing vaults may have had asset paths updated
                refreshVaultList()
            } finally {
                _isDiscovering.value = false
                PerfLog.mark("discoverVaultsFromFileSystem end")
            }
        }
    }
    
    /**
     * Create a new vault with game name and platform
     * Performs name-based search, game selection, and media scraping
     * Vault creation is guaranteed to complete even if scraping is cancelled or fails
     */
    fun createVault(gameName: String) {
        createVaultInternal(gameName, source = "Manual")
    }

    fun createVaultWithRaLink(gameName: String, raGameId: Int?) {
        if (raGameId == null) {
            createVault(gameName)
            return
        }
        createVaultInternal(gameName, source = "AotW", raGameId = raGameId.toString())
    }

    private fun createVaultInternal(gameName: String, source: String, raGameId: String? = null) {
        scrapingJob?.cancel()

        viewModelScope.launch(NonCancellable) {
            _isScraping.value = true
            _scrapingProgress.value = VaultScrapeCoordinator.ScrapingProgress.Processing(
                vaultId = "new",
                message = VaultScrapeCoordinator.SCRAPING_MESSAGE
            )

            try {
                val createResult = vaultRepository.createVaultWithFuzzyCheck(
                    name = gameName,
                    source = source,
                    raGameId = raGameId
                )

                when (createResult) {
                    is com.twinlore.app.domain.Result.Success -> {
                        val vault = createResult.data
                        // Vault directories no longer exist; data lives in the library home folder.

                        _vaultsRefreshTrigger.value++

                        // A created vault is a value event; the gate decides if the
                        // support prompt fires. The dialog itself waits until the
                        // scraping dialog is gone (see LibraryScreen). The prompt is
                        // cosmetic, so a DataStore IOException from the gate must
                        // never crash vault creation.
                        launch {
                            runCatching { supportPromptGate.onVaultCreated() }.getOrNull()?.let { _supportPrompt.value = it }
                        }

                        // Try local-media bind first. If it fills the artwork slot, skip
                        // the remote scrape entirely (cheap when index has matches; saves
                        // a network round-trip). Refreshes the media index automatically
                        // if no matches exist — this is the on-demand replacement for
                        // the removed startup scanDelta loop.
                        val bindReport = localMediaBinder.bindForVaultWithRescanIfEmpty(vault)
                        if (bindReport.artworkBound != null) {
                            _vaultsRefreshTrigger.value++
                            kotlinx.coroutines.delay(300)
                            _isScraping.value = false
                            _scrapingProgress.value = null
                            _focusedVaultId.value = vault.id
                            raiseJustFocusedByDetection()
                            // Persist the new vault (and bound artwork) to the
                            // library snapshot so a folder switch / force-quit
                            // before ON_PAUSE doesn't lose it.
                            libraryAutoSaveObserver.saveAsync()
                            return@launch
                        }

                        scrapingJob = viewModelScope.launch {
                            try {
                                vaultScrapeCoordinator.scrapeVault(vault.id).collect { progress ->
                                    _scrapingProgress.value = progress

                                    when (progress) {
                                        is VaultScrapeCoordinator.ScrapingProgress.Complete -> {
                                            _vaultsRefreshTrigger.value++
                                            kotlinx.coroutines.delay(1500)
                                            _isScraping.value = false
                                            _scrapingProgress.value = null
                                            _focusedVaultId.value = vault.id
                                            raiseJustFocusedByDetection()
                                            // Persist the new vault (and scraped artwork) to the snapshot.
                                            libraryAutoSaveObserver.saveAsync()
                                        }
                                        is VaultScrapeCoordinator.ScrapingProgress.Error -> {
                                            android.util.Log.e("LibraryViewModel", "Scraping error: ${progress.error.message}")
                                            // Keep the dialog open (Retry / Dismiss) instead of auto-dismissing.
                                            scrapeRetryAction = { rescrapeVaultForDialog(vault.id) }
                                            _focusedVaultId.value = vault.id
                                            raiseJustFocusedByDetection()
                                            // Vault row exists even if scrape failed — save so it's not lost.
                                            libraryAutoSaveObserver.saveAsync()
                                        }
                                        else -> {}
                                    }
                                }
                            } catch (e: kotlinx.coroutines.CancellationException) {
                                _isScraping.value = false
                                _scrapingProgress.value = null
                            } catch (e: Exception) {
                                android.util.Log.e("LibraryViewModel", "Exception during scraping", e)
                                _isScraping.value = false
                                _scrapingProgress.value = null
                            } finally {
                                scrapingJob = null
                            }
                        }
                    }
                    is com.twinlore.app.domain.Result.Failure -> {
                        android.util.Log.e("LibraryViewModel", "Failed to create vault: ${createResult.error.message}")
                        _isScraping.value = false
                        _scrapingProgress.value = null
                    }
                    is com.twinlore.app.domain.Result.FuzzyMatch -> {
                        _isScraping.value = false
                        _scrapingProgress.value = null
                        _fuzzyMatchPrompt.value = FuzzyMatchPrompt(
                            existingVault = createResult.data,
                            similarity = createResult.similarity,
                            newName = gameName
                        )
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("LibraryViewModel", "Exception creating vault", e)
                _scrapingProgress.value = VaultScrapeCoordinator.ScrapingProgress.Processing(
                    vaultId = "new",
                    message = classifyRefreshError(e)
                )
                kotlinx.coroutines.delay(3000)
                _isScraping.value = false
                _scrapingProgress.value = null
            }
        }
    }

    /** User confirmed using the existing fuzzy-matched vault — rename it to the new name. */
    fun confirmFuzzyMatch() {
        val prompt = _fuzzyMatchPrompt.value ?: return
        _fuzzyMatchPrompt.value = null
        viewModelScope.launch {
            vaultRepository.renameVault(prompt.existingVault.id, prompt.newName)
            _focusedVaultId.value = prompt.existingVault.id
            _vaultsRefreshTrigger.value++
            libraryAutoSaveObserver.saveAsync()
        }
    }

    /** User declined the fuzzy match — create a new vault with the original name. */
    fun declineFuzzyMatch() {
        val prompt = _fuzzyMatchPrompt.value ?: return
        _fuzzyMatchPrompt.value = null
        // Create vault normally (bypasses fuzzy check)
        viewModelScope.launch(kotlinx.coroutines.NonCancellable) {
            _isScraping.value = true
            _scrapingProgress.value = VaultScrapeCoordinator.ScrapingProgress.Processing(
                vaultId = "new",
                message = VaultScrapeCoordinator.SCRAPING_MESSAGE
            )
            val result = vaultRepository.createVault(name = prompt.newName, source = "Manual")
            if (result is com.twinlore.app.domain.Result.Success) {
                val vault = result.data
                _focusedVaultId.value = vault.id
                _vaultsRefreshTrigger.value++
                libraryAutoSaveObserver.saveAsync()
                scrapingJob = viewModelScope.launch {
                    try {
                        vaultScrapeCoordinator.scrapeVault(vault.id).collect { progress ->
                            _scrapingProgress.value = progress
                            when (progress) {
                                is VaultScrapeCoordinator.ScrapingProgress.Complete -> {
                                    _vaultsRefreshTrigger.value++
                                    kotlinx.coroutines.delay(1500)
                                    _isScraping.value = false
                                    _scrapingProgress.value = null
                                    libraryAutoSaveObserver.saveAsync()
                                }
                                is VaultScrapeCoordinator.ScrapingProgress.Error -> {
                                    // Keep the dialog open (Retry / Dismiss) instead of auto-dismissing.
                                    scrapeRetryAction = { rescrapeVaultForDialog(vault.id) }
                                    libraryAutoSaveObserver.saveAsync()
                                }
                                else -> {}
                            }
                        }
                    } catch (_: kotlinx.coroutines.CancellationException) {
                        _isScraping.value = false; _scrapingProgress.value = null
                    } catch (_: Exception) {
                        _isScraping.value = false; _scrapingProgress.value = null
                    }
                }
            } else {
                _isScraping.value = false
                _scrapingProgress.value = null
            }
        }
    }

    /** User confirmed auto-detected fuzzy match — rename existing vault. */
    fun confirmAutoDetectFuzzyMatch() {
        viewModelScope.launch {
            val pending = syncLatestPlayedGameUseCase.pendingFuzzyMatch.value ?: return@launch
            syncLatestPlayedGameUseCase.confirmFuzzyMatch()
            _focusedVaultId.value = pending.existingVault.id
            _vaultsRefreshTrigger.value++
            libraryAutoSaveObserver.saveAsync()
        }
    }

    /** User declined auto-detected fuzzy match — create new vault. */
    fun declineAutoDetectFuzzyMatch() {
        viewModelScope.launch {
            _isScraping.value = true
            _scrapingProgress.value = VaultScrapeCoordinator.ScrapingProgress.Processing(vaultId = "new", message = "Creating entry...")
            val vaultId = syncLatestPlayedGameUseCase.declineFuzzyMatch()
            _isScraping.value = false
            _scrapingProgress.value = null
            if (vaultId != null) {
                _focusedVaultId.value = vaultId
                _vaultsRefreshTrigger.value++
                libraryAutoSaveObserver.saveAsync()
            }
        }
    }

    /** User dismissed auto-detected fuzzy match without choosing. */
    fun dismissAutoDetectFuzzyMatch() {
        syncLatestPlayedGameUseCase.dismissFuzzyMatch()
    }

    /** User confirmed scrape-path fuzzy match — rename existing vault. */
    fun confirmScrapePathFuzzyMatch() {
        val pending = _autoDetectFuzzyMatchForScrape.value ?: return
        _autoDetectFuzzyMatchForScrape.value = null
        viewModelScope.launch {
            vaultRepository.renameVault(pending.existingVault.id, pending.newName)
            _focusedVaultId.value = pending.existingVault.id
            _vaultsRefreshTrigger.value++
            libraryAutoSaveObserver.saveAsync()
        }
    }

    /** User declined scrape-path fuzzy match — create new vault. */
    fun declineScrapePathFuzzyMatch() {
        val pending = _autoDetectFuzzyMatchForScrape.value ?: return
        _autoDetectFuzzyMatchForScrape.value = null
        viewModelScope.launch(kotlinx.coroutines.NonCancellable) {
            _isScraping.value = true
            _scrapingProgress.value = VaultScrapeCoordinator.ScrapingProgress.Processing(vaultId = "new", message = "Creating entry: ${pending.newName}...")
            val createResult = vaultRepository.createVault(
                name = pending.newName,
                romPath = pending.romPath,
                romFilename = pending.romFilename,
                source = pending.source
            )
            if (createResult is Result.Success) {
                val vault = createResult.data
                _vaultsRefreshTrigger.value++
                _focusedVaultId.value = vault.id
                onInputReceived()
                raiseJustFocusedByDetection()
                libraryAutoSaveObserver.saveAsync()
                scrapingJob = viewModelScope.launch {
                    try {
                        vaultScrapeCoordinator.scrapeVault(vault.id).collect { progress ->
                            _scrapingProgress.value = progress
                            when (progress) {
                                is VaultScrapeCoordinator.ScrapingProgress.Complete -> {
                                    _vaultsRefreshTrigger.value++
                                    kotlinx.coroutines.delay(1500)
                                    _isScraping.value = false
                                    _scrapingProgress.value = null
                                    libraryAutoSaveObserver.saveAsync()
                                }
                                is VaultScrapeCoordinator.ScrapingProgress.Error -> {
                                    // Keep the dialog open (Retry / Dismiss) instead of auto-dismissing.
                                    scrapeRetryAction = { rescrapeVaultForDialog(vault.id) }
                                    libraryAutoSaveObserver.saveAsync()
                                }
                                else -> {}
                            }
                        }
                    } catch (_: kotlinx.coroutines.CancellationException) {
                        _isScraping.value = false; _scrapingProgress.value = null
                    } catch (_: Exception) {
                        _isScraping.value = false; _scrapingProgress.value = null
                    }
                }
            } else {
                _isScraping.value = false
                _scrapingProgress.value = null
            }
        }
    }

    /** User dismissed scrape-path fuzzy match without choosing. */
    fun dismissScrapePathFuzzyMatch() {
        _autoDetectFuzzyMatchForScrape.value = null
    }

    /**
     * Rename a vault
     */
    fun renameVault(vaultId: String, newName: String) {
        viewModelScope.launch {
            vaultRepository.renameVault(vaultId, newName)
            // Clear stale gameId so next scrape searches by the new name
            vaultRepository.getVaultById(vaultId)?.let { vault ->
                if (vault.gameId != null) {
                    vaultRepository.updateVault(vault.copy(gameId = null))
                }
            }
            // Persist the rename to the library snapshot in the folder so
            // a folder switch / force-quit doesn't resurrect the old name.
            libraryAutoSaveObserver.saveAsync()
        }
    }
    
    /**
     * Delete a vault (both storage and database)
     */
    fun deleteVault(vaultId: String) {
        // Before deleting, move focus to the nearest neighbor so the
        // grid doesn't jump to a random position after removal.
        if (_focusedVaultId.value == vaultId) {
            val currentVaults = vaults.value
            val idx = currentVaults.indexOfFirst { it.id == vaultId }
            val neighborId = if (idx >= 0) {
                currentVaults.getOrNull(idx + 1)?.id
                    ?: currentVaults.getOrNull(idx - 1)?.id
            } else null
            _focusedVaultId.value = neighborId
        }

        viewModelScope.launch {
            deleteVaultNow(vaultId)
            // Persist the delete to the library snapshot in the folder so a
            // folder switch / force-quit doesn't resurrect the deleted vault.
            libraryAutoSaveObserver.saveAsync()
        }
    }

    private val _bulkDeleteProgress = MutableStateFlow<BulkDeleteProgress?>(null)
    /** Non-null while [deleteVaults] runs. */
    val bulkDeleteProgress: StateFlow<BulkDeleteProgress?> = _bulkDeleteProgress.asStateFlow()

    data class BulkDeleteProgress(val done: Int, val total: Int)

    /**
     * Delete several vaults (Delete multiple in the library). Each goes through the same
     * teardown as [deleteVault]; the library snapshot is saved once at the end.
     * [onComplete] gets the number actually deleted.
     */
    fun deleteVaults(vaultIds: Collection<String>, onComplete: (Int) -> Unit = {}) {
        val ids = vaultIds.distinct()
        if (ids.isEmpty()) {
            onComplete(0)
            return
        }
        val doomed = ids.toSet()
        val focused = _focusedVaultId.value
        if (focused != null && focused in doomed) {
            val current = vaults.value
            val idx = current.indexOfFirst { it.id == focused }
            _focusedVaultId.value = if (idx >= 0) {
                (current.drop(idx + 1).firstOrNull { it.id !in doomed }
                    ?: current.take(idx).lastOrNull { it.id !in doomed })?.id
            } else null
        }
        viewModelScope.launch {
            var deleted = 0
            ids.forEachIndexed { i, id ->
                _bulkDeleteProgress.value = BulkDeleteProgress(i, ids.size)
                if (deleteVaultNow(id)) deleted++
            }
            _bulkDeleteProgress.value = null
            libraryAutoSaveObserver.saveAsync()
            onComplete(deleted)
        }
    }

    /** Full teardown of one vault. Returns true when the database row was removed. */
    private suspend fun deleteVaultNow(vaultId: String): Boolean {
        run {
            android.util.Log.i("VAULT_RESIDUE", "deleteVault start vaultId=$vaultId")
            // Capture the display name BEFORE the DB delete (the row is gone after).
            val vaultSnapshot = vaultRepository.getVaultById(vaultId)
            val displayName = vaultSnapshot?.displayName
            when (val dbResult = vaultRepository.deleteVault(vaultId)) {
                is Result.Success -> {
                    val name = dbResult.data
                    android.util.Log.i("VAULT_RESIDUE", "deleteVault dbRow=ok name='$name'")
                    // The repository delete removes the vault's dedicated on-disk
                    // folder (its app-written artwork/guides/maps); files linked from
                    // OTHER folders (local_link, the user's own media) stay put. Here
                    // we additionally unwind the media-index BINDINGS this vault would
                    // re-match — across every search path, by the same basename/folder
                    // logic LocalMediaMatcher uses to create them — so a same-name
                    // recreate can't resurrect a stale binding to a file still sitting
                    // in one of those other folders.
                    if (!displayName.isNullOrBlank()) {
                        val removed = runCatching {
                            localMediaBinder.removeBindingsForVaultName(displayName)
                        }.getOrDefault(0)
                        android.util.Log.i(
                            "VAULT_RESIDUE",
                            "deleteVault bindingRowsRemoved=$removed name='$displayName'"
                        )
                    }
                    // Belt-and-suspenders: clean orphan cache tables (no FK
                    // CASCADE on ra_game_cache / steam_game_cache). UUIDs make
                    // collisions unlikely in practice, but a future
                    // backup/restore flow that reuses IDs would surface stale
                    // achievement counts on the next vault that inherits the id.
                    runCatching { raGameCacheRepository.clearCacheForVault(vaultId) }
                    runCatching { steamGameCacheRepository.clearCacheForVault(vaultId) }
                    return true
                }
                is Result.Failure -> {
                    android.util.Log.e(
                        "LibraryViewModel",
                        "Failed to delete vault from database: ${dbResult.error.message}"
                    )
                }
                else -> {
                    android.util.Log.w(
                        "LibraryViewModel",
                        "Unexpected result deleting vault from database: $dbResult"
                    )
                }
            }
        }
        return false
    }

    /**
     * Remove artwork from a vault
     */
    fun removeArtwork(vaultId: String) {
        viewModelScope.launch {
            vaultRepository.updateArtworkResourceId(vaultId, null)
        }
    }

    /**
     * Remove map from a vault
     */
    fun removeMap(vaultId: String) {
        viewModelScope.launch {
            vaultRepository.updateMapResourceId(vaultId, null)
        }
    }
    
    /**
     * Assign artwork from a file URI (file picker). Always copies the chosen
     * file into the home folder; the original is left untouched.
     */
    fun assignArtworkFromFile(
        vaultId: String,
        fileUri: Uri,
        context: android.content.Context,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val inputStream = context.contentResolver.openInputStream(fileUri)
                    val tempFile = File.createTempFile("artwork_${vaultId}_", ".jpg", context.cacheDir)
                    inputStream?.use { input ->
                        FileOutputStream(tempFile).use { output -> input.copyTo(output) }
                    }
                    if (tempFile.exists()) {
                        runArtworkAssign(vaultId, tempFile, onSuccess, onError)
                    } else {
                        withContext(Dispatchers.Main) { onError("Failed to process image file") }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("LibraryViewModel", "Error assigning artwork from file", e)
                withContext(Dispatchers.Main) { onError(e.message ?: "Unknown error") }
            }
        }
    }
    
    /**
     * Bind an already-indexed LOCAL_LINK resource_files row as the vault's artwork.
     * No download or copy: just updates the artworkResourceId pointer.
     */
    fun assignArtworkFromResource(
        vaultId: String,
        resourceFileId: String,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {},
    ) {
        viewModelScope.launch {
            finishArtworkAssign(vaultId, resourceFileId, tempFile = null, onSuccess, onError)
        }
    }

    /**
     * Assign a local image identified by a file:// or content:// URI that isn't yet
     * tracked as a resource row (e.g. an image the user dropped straight into the
     * vault folder). Reads it via the content resolver — which handles internal SAF
     * URIs that java.net.URL cannot — and copies it into the vault folder.
     */
    fun assignArtworkFromLocalUri(
        vaultId: String,
        uriString: String,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {},
    ) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val uri = Uri.parse(uriString)
                    val ext = uriString.substringAfterLast('.', "jpg").substringBefore('?').take(5).ifBlank { "jpg" }
                    val tempFile = File.createTempFile("artwork_${vaultId}_", ".$ext", context.cacheDir)
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(tempFile).use { output -> input.copyTo(output) }
                    }
                    if (tempFile.exists() && tempFile.length() > 0) {
                        runArtworkAssign(vaultId, tempFile, onSuccess, onError)
                    } else {
                        tempFile.delete()
                        withContext(Dispatchers.Main) { onError("Failed to read image file") }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("LibraryViewModel", "Error assigning artwork from local URI", e)
                withContext(Dispatchers.Main) { onError(e.message ?: "Unknown error") }
            }
        }
    }

    /**
     * Assign box art from image URL (per PRD: Long-press image → "Assign Image as Box Art")
     * Downloads image from URL and saves it as box art for the vault
     */
    fun assignBoxArtFromUrl(vaultId: String, imageUrl: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    // Download image from URL
                    val url = URL(imageUrl)
                    val connection = url.openConnection()
                    connection.connectTimeout = 10000
                    connection.readTimeout = 10000
                    connection.connect()
                    
                    val inputStream = connection.getInputStream()
                    val tempFile = File.createTempFile("boxart_${vaultId}_", ".jpg")
                    
                    FileOutputStream(tempFile).use { output ->
                        inputStream.use { input ->
                            input.copyTo(output)
                        }
                    }
                    
                    if (tempFile.exists()) {
                        runArtworkAssign(vaultId, tempFile, onSuccess, onError)
                    } else {
                        withContext(Dispatchers.Main) {
                            onError("Failed to download image")
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("LibraryViewModel", "Error assigning box art from URL", e)
                withContext(Dispatchers.Main) {
                    onError(e.message ?: "Unknown error")
                }
            }
        }
    }

    /** Shared add-file dispatch for both file-picker and URL flows. Runs on IO. */
    private suspend fun runArtworkAssign(
        vaultId: String,
        tempFile: File,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        when (val addResult = vaultRepository.addFile(vaultId, tempFile, ResourceKind.ARTWORK,
                mimeType = null)) {
            is AddFileResult.Copied -> finishArtworkAssign(vaultId, addResult.resourceFileId, tempFile, onSuccess, onError)
            is AddFileResult.Failed -> {
                tempFile.delete()
                withContext(Dispatchers.Main) { onError(addResult.cause.message ?: "Failed to save image") }
            }
        }
    }

    /** Shared finalizer for artwork assigns: writes the resource id pointer, refreshes UI caches. */
    private suspend fun finishArtworkAssign(
        vaultId: String,
        resourceFileId: String,
        tempFile: File?,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val updateResult = vaultRepository.updateArtworkResourceId(vaultId, resourceFileId)
        tempFile?.delete()
        if (updateResult.isSuccess()) {
            refreshVaultList()
            withContext(Dispatchers.Main) {
                delay(150)
                _blurArtworkInvalidateTimestamp.value = vaultId to System.currentTimeMillis()
                com.twinlore.app.ui.utils.ImagePathUtils.invalidateCacheForVault(vaultId)
                invalidateCarouselCacheForVault(vaultId)
                invalidateGridCacheForVault(vaultId)
                resourceResolver.invalidate(resourceFileId)
                onSuccess()
            }
        } else {
            val error = (updateResult as? Result.Failure)?.error?.message ?: "Failed to update game"
            withContext(Dispatchers.Main) {
                onError(error)
            }
        }
    }

    /**
     * Refresh: Check latest played game, create vault if missing, or focus existing vault
     * Manual refresh button logic:
     * 1. Check if RetroArch folder is set
     * 2. If not set, trigger folder selection request
     * 3. If set, read log file and get latest played game
     * 4. Extract game name and check if vault exists by name
     * 5. If vault doesn't exist: create vault and scrape image
     * 6. If vault exists: focus it
     * @param retroArchFolderUriOverride When non-null and source is RetroArch, use this folder URI instead of settings.
     * @param esdeFolderUriOverride When non-null and source is ES-DE, use this folder URI instead of settings.
     * @param cocoonFolderUriOverride When non-null and source is Cocoon, use this folder URI instead of settings.
     * @param detectionRunId When non-null, emitted on completion so the detection effect only proceeds on this run's completion (avoids reacting to a previous run's emit).
     */
    fun scrapeAllBoxArt(retroArchFolderUriOverride: Uri? = null, esdeFolderUriOverride: Uri? = null, cocoonFolderUriOverride: Uri? = null, detectionRunId: Long? = null) {
        // Cancel any existing scraping job
        scrapingJob?.cancel()
        _detectionCancelled.value = false
        val emitValue = detectionRunId ?: 0L

        scrapingJob = viewModelScope.launch {
            val thisJob = coroutineContext[Job]
            // Set once a launcher-log entry resolved. The source branches above that
            // point return on their own (and the Achievements one finishes there), and
            // never ran the bulk-bind summary; the try only widened to catch their throws.
            var resolvedLauncherEntry = false
            try {
                // Show immediate feedback so user knows refresh started
                _isScraping.value = true
                _scrapingProgress.value = VaultScrapeCoordinator.ScrapingProgress.Processing(
                    vaultId = "refresh",
                    message = "Detecting game..."
                )

                // Refresh respects Last played source setting (RetroArch or ES-DE) from Settings
                val source = settingsRepository.lastPlayedSource.first()
                // Rich-presence prefetch for the fullscreen view: one getUserSummary so the
                // showcase can show the RA rich-presence line when the detected game is the
                // RA last-played game. For the ACHIEVEMENTS source the detection call already
                // refreshes the profile (see SyncLatestPlayedGameUseCase), so skip it here to
                // avoid a redundant call. Fire-and-forget — the showcase reads the profile flow.
                if (source != LastPlayedSource.ACHIEVEMENTS && raCredentialsRepository.getCredentials() != null) {
                    viewModelScope.launch { runCatching { raProfileRepository.refresh() } }
                }
                val raUriString = settingsRepository.retroArchHistoryFolder.first()
                val esdeUriString = settingsRepository.esdeFolderUri.first()
                val cocoonUriString = settingsRepository.cocoonFolderUri.first()
                val noLocalFolderSet = raUriString.isNullOrBlank() && esdeUriString.isNullOrBlank() && cocoonUriString.isNullOrBlank()

                var rawGameName: String
                var romPath: String?
                var romFilename: String?
                var effectiveSource: String
                var esdeLaunchIntent: ESDELogParser.ParsedLaunchIntent? = null

                when (source) {
                    LastPlayedSource.RETROARCH -> {
                        val folderUri = retroArchFolderUriOverride
                            ?: raUriString?.let { Uri.parse(it) }
                        if (folderUri == null) {
                            _isScraping.value = true
                            _scrapingProgress.value = VaultScrapeCoordinator.ScrapingProgress.Processing(
                                vaultId = "refresh",
                                message = if (noLocalFolderSet) "No launcher folder configured. Select a folder to continue." else "RetroArch folder not set. Select a folder to continue."
                            )
                            kotlinx.coroutines.delay(2000)
                            _isScraping.value = false
                            _scrapingProgress.value = null
                            _needsFolderSelection.value = true
                            _detectionFocusApplied.tryEmit(emitValue)
                            return@launch
                        }
                        val logEntry = retroArchHistoryParser.findLastPlayedGame(folderUri)
                        if (logEntry != null) {
                            android.util.Log.d("LibraryViewModel", "RetroArch lpl: last played '${logEntry.gameName}'")
                            rawGameName = logEntry.gameName
                            romPath = logEntry.romPath
                            romFilename = logEntry.romFilename
                            effectiveSource = "RetroArch"
                            esdeLaunchIntent = logEntry.launchIntent
                        } else {
                            android.util.Log.w("LibraryViewModel", "RetroArch content_history.lpl not found or empty (folder=$folderUri)")
                            // No diagnostic split — the help dialog now has single
                            // content regardless of why detection failed.
                            _pendingRetroArchHelpDialog.value = true
                            _isScraping.value = false
                            _scrapingProgress.value = null
                            _detectionFocusApplied.tryEmit(emitValue)
                            return@launch
                        }
                    }
                    LastPlayedSource.ES_DE -> {
                        val folderUri = esdeFolderUriOverride
                            ?: esdeUriString?.let { Uri.parse(it) }
                        if (folderUri == null) {
                            _isScraping.value = true
                            _scrapingProgress.value = VaultScrapeCoordinator.ScrapingProgress.Processing(
                                vaultId = "refresh",
                                message = if (noLocalFolderSet) "No launcher folder configured. Select a folder to continue." else "ES-DE folder not set. Select a folder to continue."
                            )
                            kotlinx.coroutines.delay(2000)
                            _isScraping.value = false
                            _scrapingProgress.value = null
                            _needsFolderSelection.value = true
                            _detectionFocusApplied.tryEmit(emitValue)
                            return@launch
                        }

                        // Primary: read es_log.txt for the last launched game
                        val logEntry = esdeLogParser.findLastLaunchedGame(folderUri)
                        if (logEntry != null) {
                            android.util.Log.d("LibraryViewModel", "ES-DE log: last launched '${logEntry.gameName}' from '${logEntry.system}'")
                            rawGameName = logEntry.gameName
                            romPath = logEntry.romPath
                            romFilename = esdeLogParser.extractRomFilename(logEntry.romPath)
                            effectiveSource = "ES-DE"
                            esdeLaunchIntent = logEntry.launchIntent
                        } else {
                            // Classify the folder so the auto-opened help dialog matches the cause.
                            android.util.Log.w("LibraryViewModel", "ES-DE log: no launch entries (folder=$folderUri)")
                            val state: com.twinlore.app.data.ESDELogParser.ESDEFolderState = try {
                                esdeLogParser.checkLogFolder(folderUri)
                            } catch (e: Exception) {
                                com.twinlore.app.data.ESDELogParser.ESDEFolderState.FolderUnreachable
                            }
                            if (state !is com.twinlore.app.data.ESDELogParser.ESDEFolderState.Valid) {
                                _pendingESDEHelpDialog.value = state
                            }
                            _isScraping.value = false
                            _scrapingProgress.value = null
                            _detectionFocusApplied.tryEmit(emitValue)
                            return@launch
                        }
                    }
                    LastPlayedSource.COCOON -> {
                        val folderUri = cocoonFolderUriOverride
                            ?: cocoonUriString?.let { Uri.parse(it) }
                        if (folderUri == null) {
                            _isScraping.value = true
                            _scrapingProgress.value = VaultScrapeCoordinator.ScrapingProgress.Processing(
                                vaultId = "refresh",
                                message = "Cocoon folder not set. Select a folder to continue."
                            )
                            kotlinx.coroutines.delay(2000)
                            _isScraping.value = false
                            _scrapingProgress.value = null
                            _needsFolderSelection.value = true
                            _detectionFocusApplied.tryEmit(emitValue)
                            return@launch
                        }

                        val logEntry = cocoonLogParser.findLastLaunchedGame(folderUri)
                        if (logEntry != null) {
                            android.util.Log.d("LibraryViewModel", "Cocoon log: last launched '${logEntry.gameName}'")
                            rawGameName = logEntry.gameName
                            romPath = logEntry.romPath
                            romFilename = cocoonLogParser.extractRomFilename(logEntry.romPath)
                            effectiveSource = "Cocoon"
                            esdeLaunchIntent = logEntry.launchIntent
                        } else {
                            // Classify the folder so the auto-opened help dialog matches the cause.
                            android.util.Log.w("LibraryViewModel", "Cocoon log: no launch entries (folder=$folderUri)")
                            val state: com.twinlore.app.data.CocoonLogParser.CocoonFolderState = try {
                                cocoonLogParser.checkLogFolder(folderUri)
                            } catch (e: Exception) {
                                com.twinlore.app.data.CocoonLogParser.CocoonFolderState.FolderUnreachable
                            }
                            if (state !is com.twinlore.app.data.CocoonLogParser.CocoonFolderState.Valid) {
                                _pendingCocoonHelpDialog.value = state
                            }
                            _isScraping.value = false
                            _scrapingProgress.value = null
                            _detectionFocusApplied.tryEmit(emitValue)
                            return@launch
                        }
                    }
                    LastPlayedSource.NOT_CONFIGURED -> {
                        _isScraping.value = true
                        _scrapingProgress.value = VaultScrapeCoordinator.ScrapingProgress.Processing(
                            vaultId = "refresh",
                            message = "No detection source set."
                        )
                        kotlinx.coroutines.delay(1500)
                        _isScraping.value = false
                        _scrapingProgress.value = null
                        _needsSourceSetup.value = true
                        _detectionFocusApplied.tryEmit(emitValue)
                        return@launch
                    }
                    LastPlayedSource.ACHIEVEMENTS -> {
                        val raCreds = raCredentialsRepository.getCredentials()
                        val steamCreds = steamCredentialsRepository.getCredentials()
                        if (raCreds == null && steamCreds == null) {
                            _isScraping.value = true
                            _scrapingProgress.value = VaultScrapeCoordinator.ScrapingProgress.Processing(
                                vaultId = "refresh",
                                message = "No achievement credentials configured."
                            )
                            kotlinx.coroutines.delay(1500)
                            _isScraping.value = false
                            _scrapingProgress.value = null
                            _needsSourceSetup.value = true
                            _detectionFocusApplied.tryEmit(emitValue)
                            return@launch
                        }
                        val hasRa = raCreds != null
                        val hasSteam = steamCreds != null
                        _isScraping.value = true
                        _scrapingProgress.value = VaultScrapeCoordinator.ScrapingProgress.Processing(
                            vaultId = "refresh",
                            message = when {
                                hasRa && hasSteam -> "Checking achievements for recently played games…"
                                hasSteam -> "Checking Steam…"
                                else -> "Checking RetroAchievements…"
                            }
                        )
                        val vaultId = syncLatestPlayedGameUseCase.execute()
                        // Refresh summary (including bulk-bind across non-detected vaults) is
                        // emitted in the finally block at the end of scrapeAllBoxArt so it
                        // covers every detection source uniformly.
                        if (vaultId != null) {
                            val existingVault = vaultRepository.getVaultById(vaultId)
                            if (existingVault != null) {
                                if (!existingVault.hasArtwork) {
                                    _vaultsRefreshTrigger.value++
                                    delay(200)
                                    onInputReceived()
                                    raiseJustFocusedByDetection()
                                    _focusedVaultId.value = existingVault.id
                                    _detectionFocusApplied.tryEmit(emitValue)
                                    vaultScrapeCoordinator.scrapeVault(existingVault.id).collect { progress ->
                                        when (progress) {
                                            is VaultScrapeCoordinator.ScrapingProgress.Complete -> {
                                                _vaultsRefreshTrigger.value++
                                                delay(300)
                                            }
                                            is VaultScrapeCoordinator.ScrapingProgress.Error -> {
                                                android.util.Log.e("LibraryViewModel", "Scraping error: ${progress.error.message}")
                                            }
                                            else -> { }
                                        }
                                    }
                                } else {
                                    raiseJustFocusedByDetection()
                                    _focusedVaultId.value = existingVault.id
                                    _isScraping.value = false
                                    _scrapingProgress.value = null
                                    _detectionFocusApplied.tryEmit(emitValue)
                                }
                            } else {
                                _isScraping.value = false
                                _scrapingProgress.value = null
                                _detectionFocusApplied.tryEmit(emitValue)
                            }
                        } else {
                            // No game found — build appropriate error message
                            val steamWarning = if (hasSteam) "No recently played games found from Steam. Make sure your Steam profile and game details are set to Public in your Steam privacy settings." else null
                            val raWarning = if (hasRa) "No recently played games found from RetroAchievements." else null
                            val message = listOfNotNull(raWarning, steamWarning).joinToString("\n\n")
                            _scrapingProgress.value = VaultScrapeCoordinator.ScrapingProgress.Processing(
                                vaultId = "refresh",
                                message = message
                            )
                            kotlinx.coroutines.delay(3000)
                            _isScraping.value = false
                            _scrapingProgress.value = null
                            _detectionFocusApplied.tryEmit(emitValue)
                        }
                        return@launch
                    }
                }

                resolvedLauncherEntry = true
                val sanitizedGameName = gameNameSanitizer.sanitize(rawGameName)

                // The DB is the source of truth; no filesystem-based filtering.
                val allDbVaults = vaultRepository.getAllVaults()
                val storageVaults = allDbVaults

                // Step 3: Check if vault exists
                // 3a) By ROM path / filename (vaults from refresh or import)
                var existingVault = vaultRepository.resolveVaultIdentity(
                    romPath = romPath,
                    romFilename = romFilename,
                    name = sanitizedGameName
                )
                // 3b) Fallback: by sanitized name (manually-created vaults without romPath)
                if (existingVault == null) {
                    val normalized = sanitizedGameName.trim().lowercase()
                    existingVault = storageVaults.firstOrNull { v ->
                        gameNameSanitizer.sanitize(v.displayName).trim().lowercase() == normalized
                    }
                }

                // Step 3c: Fuzzy match fallback before creating new vault
                if (existingVault == null) {
                    val fuzzyMatch = fuzzyNameMatcher.findBestMatch(sanitizedGameName, storageVaults)
                    if (fuzzyMatch != null && fuzzyMatch.similarity < 1.0) {
                        // Show confirmation dialog and STOP — don't create vault
                        android.util.Log.d("LibraryViewModel", "Fuzzy match: '${fuzzyMatch.vault.displayName}' (${(fuzzyMatch.similarity * 100).toInt()}%) for '$sanitizedGameName'")
                        _isScraping.value = false
                        _scrapingProgress.value = null
                        _autoDetectFuzzyMatchForScrape.value = AutoDetectFuzzyMatchForScrape(
                            existingVault = fuzzyMatch.vault,
                            newName = sanitizedGameName,
                            similarity = fuzzyMatch.similarity,
                            romPath = romPath,
                            romFilename = romFilename,
                            source = effectiveSource
                        )
                        _detectionFocusApplied.tryEmit(emitValue)
                        return@launch
                    } else if (fuzzyMatch != null) {
                        // 100% normalized match — use silently
                        existingVault = fuzzyMatch.vault
                    }
                }

                // Store ES-DE launch intent if available (existing vault)
                if (existingVault != null && esdeLaunchIntent != null) {
                    vaultRepository.updateLaunchIntent(
                        existingVault.id, esdeLaunchIntent.toJson(), romPath, romFilename
                    )
                }

                if (existingVault == null) {
                    // Step 4: Vault doesn't exist - respect "Create new vault if not found" setting
                    val createIfNotFound = settingsRepository.createVaultIfNotFound.first()
                    if (!createIfNotFound) {
                        _isScraping.value = true
                        _scrapingProgress.value = VaultScrapeCoordinator.ScrapingProgress.Processing(
                            vaultId = "refresh",
                            message = "No matching game found. Auto-create is OFF."
                        )
                        kotlinx.coroutines.delay(2000)
                        _isScraping.value = false
                        _scrapingProgress.value = null
                        _detectionFocusApplied.tryEmit(emitValue)
                        return@launch
                    }
                    _isScraping.value = true
                    _scrapingProgress.value = VaultScrapeCoordinator.ScrapingProgress.Processing(
                        vaultId = "new",
                        message = "Creating entry: $sanitizedGameName..."
                    )
                    val createResult = vaultRepository.createVault(
                        name = sanitizedGameName,
                        romPath = romPath,
                        romFilename = romFilename,
                        source = effectiveSource
                    )

                    when (createResult) {
                        is Result.Success -> {
                            val vault = createResult.data
                            // Store ES-DE launch intent if available (new vault)
                            if (esdeLaunchIntent != null) {
                                vaultRepository.updateLaunchIntent(
                                    vault.id, esdeLaunchIntent.toJson(), romPath, romFilename
                                )
                            }
                            // Vault directories no longer exist; data lives in the library home folder.

                            // Trigger UI refresh to ensure vault list updates
                            _vaultsRefreshTrigger.value++
                            
                            // Wait for vaults list to update and include the new vault
                            // This ensures the carousel can find the vault when we set focus
                            // Poll the vaults list to verify the vault appears (with timeout)
                            var vaultFound = false
                            var attempts = 0
                            val maxAttempts = 20 // 2 seconds total (20 * 100ms)
                            while (!vaultFound && attempts < maxAttempts) {
                                delay(100)
                                val currentVaults = vaults.value
                                vaultFound = currentVaults.any { it.id == vault.id }
                                attempts++
                            }
                            if (!vaultFound) {
                                android.util.Log.w("LibraryViewModel", "Vault ${vault.id} not found in list after ${maxAttempts * 100}ms - proceeding anyway")
                            }
                            
                            onInputReceived()

                            // Focus the new vault (UI fades to black first, then we apply focus, then fade back)
                            raiseJustFocusedByDetection()
                            _focusedVaultId.value = vault.id
                            _detectionFocusApplied.tryEmit(emitValue)
                            settingsRepository.setNowPlayingVaultId(vault.id)

                            // Try local-media bind first. If it fills the artwork slot,
                            // skip the remote scrape entirely (user-requested behavior).
                            // The bind result for a newly created vault is implicit, so
                            // we don't accumulate it into the refresh summary.
                            _scrapingProgress.value = VaultScrapeCoordinator.ScrapingProgress.Processing(
                                vaultId = vault.id,
                                message = "Checking for local assets..."
                            )
                            val bindReport = localMediaBinder.bindForVaultWithRescanIfEmpty(vault)
                            emitLocalBindStep(vault.id, bindReport)
                            if (bindReport.artworkBound != null) {
                                autoSetGridRatioFromArtwork(vault.id)
                                _vaultsRefreshTrigger.value++
                                delay(300)
                                _isScraping.value = false
                                _scrapingProgress.value = null
                            } else {
                                // No local match — fall through to the remote scrape.
                                vaultScrapeCoordinator.scrapeVault(vault.id).collect { progress ->
                                    _scrapingProgress.value = progress

                                    when (progress) {
                                        is VaultScrapeCoordinator.ScrapingProgress.Complete -> {
                                            autoSetGridRatioFromArtwork(vault.id)
                                            _vaultsRefreshTrigger.value++
                                            delay(300)
                                            kotlinx.coroutines.delay(1500)
                                            _isScraping.value = false
                                            _scrapingProgress.value = null
                                        }
                                        is VaultScrapeCoordinator.ScrapingProgress.Error -> {
                                            android.util.Log.e("LibraryViewModel", "Scraping error: ${progress.error.message}")
                                            // Keep the dialog open (Retry / Dismiss) instead of auto-dismissing.
                                            scrapeRetryAction = { rescrapeVaultForDialog(vault.id) }
                                        }
                                        else -> { }
                                    }
                                }
                            }
                        }
                        is Result.Failure -> {
                            android.util.Log.e("LibraryViewModel", "Failed to create vault: ${createResult.error.message}")
                            _isScraping.value = false
                            _scrapingProgress.value = null
                            _detectionFocusApplied.tryEmit(emitValue)
                        }
                        else -> {
                            android.util.Log.w("LibraryViewModel", "Unexpected result creating vault: $createResult")
                            _isScraping.value = false
                            _scrapingProgress.value = null
                            _detectionFocusApplied.tryEmit(emitValue)
                        }
                    }
                } else {
                    // Step 5: Vault exists. Always try local-media bind first so a freshly
                    // indexed library populates the slot without a network round-trip. If
                    // the slot is already filled, the binder still surfaces extra matches as
                    // alternatives in the picker.
                    _vaultsRefreshTrigger.value++
                    delay(200)
                    onInputReceived()
                    raiseJustFocusedByDetection()
                    _focusedVaultId.value = existingVault.id
                    _detectionFocusApplied.tryEmit(emitValue)
                    settingsRepository.setNowPlayingVaultId(existingVault.id)

                    _isScraping.value = true
                    _scrapingProgress.value = VaultScrapeCoordinator.ScrapingProgress.Processing(
                        vaultId = existingVault.id,
                        message = "Checking for local assets..."
                    )
                    val bindReport = localMediaBinder.bindForVault(existingVault)
                    emitLocalBindStep(existingVault.id, bindReport)
                    if (bindReport.hasAny()) {
                        _localBindReports.value = _localBindReports.value + bindReport
                    }
                    if (bindReport.artworkBound != null) {
                        autoSetGridRatioFromArtwork(existingVault.id)
                    }

                    // Snapshot the pre-refresh artwork state: if the vault already had
                    // artwork when refresh started, we must never fall through to a remote
                    // scrape — a failed scrape (e.g. no network) would leave the slot empty
                    // and silently drop the user's assigned artwork.
                    val hadArtworkBeforeRefresh = existingVault.hasArtwork
                    val refreshedVault = vaultRepository.getVaultById(existingVault.id)
                    val nowHasArtwork = refreshedVault?.hasArtwork == true
                    if (!nowHasArtwork && !hadArtworkBeforeRefresh) {
                        // Bind didn't fill the slot AND vault still has no artwork; fall back to remote scrape.
                        // Mirror the new-vault path: surface every progress emission to _scrapingProgress
                        // so the user sees "Searching for game...", "Scraping box art...", etc.
                        _isScraping.value = true
                        vaultScrapeCoordinator.scrapeVault(existingVault.id).collect { progress ->
                            _scrapingProgress.value = progress
                            when (progress) {
                                is VaultScrapeCoordinator.ScrapingProgress.Complete -> {
                                    autoSetGridRatioFromArtwork(existingVault.id)
                                    _vaultsRefreshTrigger.value++
                                    delay(300)
                                    kotlinx.coroutines.delay(1000)
                                    _isScraping.value = false
                                    _scrapingProgress.value = null
                                }
                                is VaultScrapeCoordinator.ScrapingProgress.Error -> {
                                    android.util.Log.e("LibraryViewModel", "Scraping error (existing vault): ${progress.error.message}")
                                    // Keep the dialog open (Retry / Dismiss) instead of auto-dismissing.
                                    scrapeRetryAction = { rescrapeVaultForDialog(existingVault.id) }
                                }
                                else -> { }
                            }
                        }
                    } else {
                        _vaultsRefreshTrigger.value++
                        _isScraping.value = false
                        _scrapingProgress.value = null
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Skip (cancelScraping) already reset the progress UI and released the
                // Library's wait; reporting it as a refresh error would undo that.
                throw e
            } catch (e: Exception) {
                android.util.Log.e("LibraryViewModel", "Error during refresh", e)
                try {
                    _scrapingProgress.value = VaultScrapeCoordinator.ScrapingProgress.Processing(
                        vaultId = "refresh",
                        message = classifyRefreshError(e)
                    )
                    kotlinx.coroutines.delay(3000)
                } catch (_: Exception) { }
                _detectionFocusApplied.tryEmit(emitValue)
            } finally {
                // Surface the refresh summary (cap-skipped + local-bind reports) for every
                // path that resolved a launcher-log entry (RetroArch, ES-DE or Cocoon).
                if (resolvedLauncherEntry) {
                    try { emitRefreshSummaryWithBulkBind() } catch (_: Exception) { }
                }

                // Safety net: if this job is still the active one and the scrape UI is still showing,
                // clear it so the progress dialog can never get stuck (e.g. silent flow completion).
                // Skip when a newer scrapeAllBoxArt call has already replaced scrapingJob, or when
                // the job was cancelled by cancelScraping (_detectionCancelled handles its own cleanup).
                if (scrapingJob === thisJob && _isScraping.value && !_detectionCancelled.value) {
                    _isScraping.value = false
                    _scrapingProgress.value = null
                }
                if (scrapingJob === thisJob) {
                    scrapingJob = null
                }
            }
        }
    }

    /** Helper: poll Steam for most recently played game */
    /**
     * Handle folder selection result from UI
     * Called when user selects a folder via folder picker.
     * Behavior depends on last played source: RetroArch (save folder, check log, scrape) or ES-DE (save folder, then scrape latest).
     * @param folderUri The selected folder URI
     * @param chosenSource The method picked in the choose-method dialog, or null when the
     *   pick re-targets the already saved source. A chosen source is only saved once the
     *   folder checks out; see [discardsFailedSourceSetup] for what a bad folder saves.
     */
    fun handleFolderSelection(folderUri: Uri, chosenSource: LastPlayedSource? = null) {
        viewModelScope.launch {
            _needsFolderSelection.value = false
            val savedSource = settingsRepository.lastPlayedSource.first()
            val source = chosenSource ?: savedSource
            val discardOnFailure = chosenSource != null &&
                discardsFailedSourceSetup(savedSource, libraryEmpty = vaultRepository.getVaultCount() == 0)
            suspend fun commitChosenSource() {
                if (chosenSource != null) settingsRepository.selectLastPlayedSource(chosenSource)
            }

            when (source) {
                LastPlayedSource.RETROARCH -> {
                    // Probe the new parser. Success → kick off scrape.
                    // Failure → surface the help dialog (single content,
                    // no state enum).
                    val foundEntry = retroArchHistoryParser.findLastPlayedGame(folderUri)
                    if (foundEntry == null && discardOnFailure) {
                        android.util.Log.w("LibraryViewModel", "RetroArch setup pick had no content_history.lpl; nothing saved")
                        _sourceSetupFailure.value = sourceSetupFailureMessage(LastPlayedSource.RETROARCH)
                        return@launch
                    }
                    settingsRepository.setRetroArchHistoryFolder(folderUri.toString())
                    commitChosenSource()
                    if (foundEntry != null) {
                        _isScraping.value = true
                        _scrapingProgress.value = VaultScrapeCoordinator.ScrapingProgress.Processing(
                            vaultId = "folder",
                            message = "RetroArch folder set ✓"
                        )
                        kotlinx.coroutines.delay(1500)
                        _isScraping.value = false
                        _scrapingProgress.value = null
                        scrapeAllBoxArt(retroArchFolderUriOverride = folderUri)
                    } else {
                        android.util.Log.w("LibraryViewModel", "RetroArch content_history.lpl not found after folder pick")
                        _pendingRetroArchHelpDialog.value = true
                    }
                }
                LastPlayedSource.ES_DE -> {
                    val state: com.twinlore.app.data.ESDELogParser.ESDEFolderState = try {
                        esdeLogParser.checkLogFolder(folderUri)
                    } catch (e: Exception) {
                        android.util.Log.e("LibraryViewModel", "Error validating ES-DE folder", e)
                        com.twinlore.app.data.ESDELogParser.ESDEFolderState.FolderUnreachable
                    }
                    if (state !is com.twinlore.app.data.ESDELogParser.ESDEFolderState.Valid && discardOnFailure) {
                        android.util.Log.w("LibraryViewModel", "ES-DE setup pick returned $state; nothing saved")
                        _sourceSetupFailure.value = sourceSetupFailureMessage(LastPlayedSource.ES_DE)
                        return@launch
                    }
                    settingsRepository.setEsdeFolderUri(folderUri.toString())
                    val doc = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, folderUri)
                    settingsRepository.setEsdeFolderPath(doc?.name ?: folderUri.toString())
                    commitChosenSource()
                    if (state is com.twinlore.app.data.ESDELogParser.ESDEFolderState.Valid) {
                        _isScraping.value = true
                        _scrapingProgress.value = VaultScrapeCoordinator.ScrapingProgress.Processing(
                            vaultId = "folder",
                            message = "ES-DE folder set ✓"
                        )
                        kotlinx.coroutines.delay(1000)
                        _scrapingProgress.value = VaultScrapeCoordinator.ScrapingProgress.Processing(
                            vaultId = "folder",
                            message = "Scanning for local game assets (images, manuals)..."
                        )
                        mediaSearchPathRepository.syncAutoFromSettings()
                        val autoPath = mediaSearchPathRepository.getAll()
                            .firstOrNull { it.origin == com.twinlore.app.domain.MediaSearchPath.Origin.AUTO_ESDE }
                        if (autoPath != null) mediaIndexer.scanDelta(autoPath.id)
                        _scrapingProgress.value = VaultScrapeCoordinator.ScrapingProgress.Processing(
                            vaultId = "folder",
                            message = "Local assets indexed. They will be linked to your games automatically."
                        )
                        kotlinx.coroutines.delay(1500)
                        _isScraping.value = false
                        _scrapingProgress.value = null
                        scrapeAllBoxArt(esdeFolderUriOverride = folderUri)
                    } else {
                        android.util.Log.w("LibraryViewModel", "ES-DE folder check after picker returned $state")
                        _pendingESDEHelpDialog.value = state
                    }
                }
                LastPlayedSource.COCOON -> {
                    val state: com.twinlore.app.data.CocoonLogParser.CocoonFolderState = try {
                        cocoonLogParser.checkLogFolder(folderUri)
                    } catch (e: Exception) {
                        android.util.Log.e("LibraryViewModel", "Error validating Cocoon folder", e)
                        com.twinlore.app.data.CocoonLogParser.CocoonFolderState.FolderUnreachable
                    }
                    if (state !is com.twinlore.app.data.CocoonLogParser.CocoonFolderState.Valid && discardOnFailure) {
                        android.util.Log.w("LibraryViewModel", "Cocoon setup pick returned $state; nothing saved")
                        _sourceSetupFailure.value = sourceSetupFailureMessage(LastPlayedSource.COCOON)
                        return@launch
                    }
                    settingsRepository.setCocoonFolderUri(folderUri.toString())
                    val doc = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, folderUri)
                    settingsRepository.setCocoonFolderPath(doc?.name ?: folderUri.toString())
                    commitChosenSource()
                    if (state is com.twinlore.app.data.CocoonLogParser.CocoonFolderState.Valid) {
                        _isScraping.value = true
                        _scrapingProgress.value = VaultScrapeCoordinator.ScrapingProgress.Processing(
                            vaultId = "folder",
                            message = "Cocoon folder set ✓"
                        )
                        kotlinx.coroutines.delay(1000)
                        _scrapingProgress.value = VaultScrapeCoordinator.ScrapingProgress.Processing(
                            vaultId = "folder",
                            message = "Scanning for local game assets (images, manuals)..."
                        )
                        mediaSearchPathRepository.syncAutoFromSettings()
                        val autoPath = mediaSearchPathRepository.getAll()
                            .firstOrNull { it.origin == com.twinlore.app.domain.MediaSearchPath.Origin.AUTO_COCOON }
                        if (autoPath != null) mediaIndexer.scanDelta(autoPath.id)
                        _scrapingProgress.value = VaultScrapeCoordinator.ScrapingProgress.Processing(
                            vaultId = "folder",
                            message = "Local assets indexed. They will be linked to your games automatically."
                        )
                        kotlinx.coroutines.delay(1500)
                        _isScraping.value = false
                        _scrapingProgress.value = null
                        scrapeAllBoxArt(cocoonFolderUriOverride = folderUri)
                    } else {
                        android.util.Log.w("LibraryViewModel", "Cocoon folder check after picker returned $state")
                        _pendingCocoonHelpDialog.value = state
                    }
                }
                LastPlayedSource.ACHIEVEMENTS -> {
                    // No folder to set; credentials are configured in Settings → Achievements.
                }
                LastPlayedSource.NOT_CONFIGURED -> {
                    // No folder to set; source needs to be configured first.
                }
            }
        }
    }
    
    /**
     * On first-ever artwork bind, read the image dimensions and set the grid
     * display ratio to the closest match. Only runs once (flag is persisted).
     */
    private suspend fun autoSetGridRatioFromArtwork(vaultId: String) {
        val tag = "AutoGridRatio"
        val alreadySet = settingsRepository.gridRatioExplicitlySet.first()
        if (alreadySet) {
            android.util.Log.d(tag, "skip: grid ratio already explicitly set")
            return
        }
        val vault = vaultRepository.getVaultById(vaultId)
        if (vault == null) { android.util.Log.d(tag, "skip: vault not found"); return }
        val artResId = vault.artworkResourceId
        if (artResId == null) { android.util.Log.d(tag, "skip: no artworkResourceId on vault ${vault.id}"); return }
        val resFile = resourceFileDao.getById(artResId)
        if (resFile == null) { android.util.Log.d(tag, "skip: resource file not found for id=$artResId"); return }

        // Resolve file path: try absolutePath first, then homeRelativePath
        var filePath = resFile.absolutePath
        if (filePath == null && resFile.homeRelativePath != null) {
            val homeUri = settingsRepository.homeFolderUri.first()
            android.util.Log.d(tag, "absolutePath=null, homeRelativePath=${resFile.homeRelativePath}, homeUri=$homeUri")
            if (homeUri != null) {
                val parsed = android.net.Uri.parse(homeUri)
                if (parsed.scheme == "file" && parsed.path != null) {
                    val candidate = java.io.File(parsed.path!!, resFile.homeRelativePath)
                    android.util.Log.d(tag, "resolved candidate: ${candidate.absolutePath} exists=${candidate.exists()}")
                    if (candidate.exists()) filePath = candidate.absolutePath
                } else {
                    android.util.Log.d(tag, "home folder is not file:// scheme: ${parsed.scheme}")
                }
            }
        }
        if (filePath == null) { android.util.Log.d(tag, "skip: could not resolve file path"); return }

        android.util.Log.d(tag, "reading dimensions from: $filePath")
        val (w, h) = withContext(kotlinx.coroutines.Dispatchers.IO) {
            val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            if (filePath.startsWith("content://")) {
                try {
                    context.contentResolver.openInputStream(android.net.Uri.parse(filePath))?.use { stream ->
                        android.graphics.BitmapFactory.decodeStream(stream, null, opts)
                    }
                } catch (e: Exception) {
                    android.util.Log.w(tag, "failed to open content URI", e)
                }
            } else {
                android.graphics.BitmapFactory.decodeFile(filePath, opts)
            }
            opts.outWidth to opts.outHeight
        }
        if (w <= 0 || h <= 0) { android.util.Log.d(tag, "skip: invalid dimensions w=$w h=$h"); return }

        val imageRatio = w.toFloat() / h.toFloat()
        val closest = com.twinlore.app.data.GridDisplayRatio.entries
            .minByOrNull { kotlin.math.abs(it.aspectRatio - imageRatio) } ?: return
        android.util.Log.d(tag, "image ${w}x${h} ratio=$imageRatio -> closest=${closest.name} (${closest.aspectRatio})")
        settingsRepository.setGridDisplayRatio(closest)
        settingsRepository.setGridRatioExplicitlySet(true)
        android.util.Log.d(tag, "grid ratio set to ${closest.displayName}")
    }

    /**
     * Cancel folder selection request.
     * Just returns to vault library without running scraping or showing progress.
     */
    fun cancelFolderSelection() {
        _needsFolderSelection.value = false
    }
    
    /**
     * Cancel scraping operation
     */
    fun cancelScraping() {
        scrapingJob?.cancel()
        scrapingJob = null
        _isScraping.value = false
        _scrapingProgress.value = null
        _detectionCancelled.value = true
        // Emit detectionFocusApplied so the detection LaunchedEffect doesn't wait for the 15s timeout
        _detectionFocusApplied.tryEmit(nextDetectionRunId.get())
    }

    // How to re-run the most recent artwork scrape, captured when a scrape
    // errors so the dialog's Retry button can replay it. Null when there's
    // nothing to retry.
    private var scrapeRetryAction: (() -> Unit)? = null

    /** Retry the scrape that just failed in the progress dialog. */
    fun retryScrapeArtwork() {
        scrapeRetryAction?.invoke()
    }

    /** Dismiss the scrape progress dialog after a failure (leaves the entry as-is). */
    fun dismissScrapeProgress() {
        scrapingJob?.cancel()
        scrapingJob = null
        scrapeRetryAction = null
        _isScraping.value = false
        _scrapingProgress.value = null
    }

    /**
     * Re-run the box-art scrape for one vault and surface progress in the
     * dialog. Used only by the Retry button — [forceFresh] bypasses the
     * debounce so an immediate retry actually hits the network. On a fresh
     * failure the dialog stays open (no auto-dismiss) so Retry/Dismiss remain.
     */
    private fun rescrapeVaultForDialog(vaultId: String) {
        scrapingJob?.cancel()
        scrapeRetryAction = { rescrapeVaultForDialog(vaultId) }
        _isScraping.value = true
        // Distinct "Retrying…" label so the dialog visibly leaves the error
        // state (buttons gone, spinner back) before it either succeeds or
        // re-surfaces the error — instead of re-rendering the error in place.
        _scrapingProgress.value = VaultScrapeCoordinator.ScrapingProgress.Processing(
            vaultId = vaultId,
            message = "Retrying…"
        )
        scrapingJob = viewModelScope.launch {
            try {
                vaultScrapeCoordinator.scrapeVault(vaultId, forceFresh = true).collect { progress ->
                    _scrapingProgress.value = progress
                    when (progress) {
                        is VaultScrapeCoordinator.ScrapingProgress.Complete -> {
                            autoSetGridRatioFromArtwork(vaultId)
                            _vaultsRefreshTrigger.value++
                            kotlinx.coroutines.delay(1200)
                            _isScraping.value = false
                            _scrapingProgress.value = null
                            _focusedVaultId.value = vaultId
                            raiseJustFocusedByDetection()
                            libraryAutoSaveObserver.saveAsync()
                        }
                        is VaultScrapeCoordinator.ScrapingProgress.Error -> {
                            // Leave the dialog open; Retry/Dismiss drive it.
                            libraryAutoSaveObserver.saveAsync()
                        }
                        else -> {}
                    }
                }
            } catch (_: kotlinx.coroutines.CancellationException) {
                // Cancelled by a new Retry / Dismiss — caller owns the state.
            } catch (e: Exception) {
                _scrapingProgress.value = VaultScrapeCoordinator.ScrapingProgress.Error(vaultId, e)
            } finally {
                scrapingJob = null
            }
        }
    }

    /**
     * Internal method to scrape vault assets using unified pipeline
     * Scrapes box art, map, and guide for vaults that need them
     */
    private fun scrapeAllBoxArtInternal() {
        viewModelScope.launch {
            _isScraping.value = true
            _scrapingProgress.value = VaultScrapeCoordinator.ScrapingProgress.Processing(
                vaultId = "all",
                message = "Preparing to scrape games..."
            )
            
            try {
                // Get all vaults from repository
                val allVaults = vaultRepository.getAllVaults()
                // Filter vaults that need scraping (only artwork, maps are no longer scraped)
                // NOTE: Guides are manual only - never scraped automatically
                val vaultsNeedingScraping = allVaults.filter { vault ->
                    !vault.hasArtwork
                }
                
                if (vaultsNeedingScraping.isEmpty()) {
                    // Show a brief message that scraping is complete
                    _scrapingProgress.value = VaultScrapeCoordinator.ScrapingProgress.Complete(
                        vaultId = "all",
                        results = listOf(
                            VaultScrapeCoordinator.AssetScrapeResult.Skipped(
                                com.twinlore.app.domain.AssetType.ART,
                                "All games already have assets"
                            )
                        )
                    )
                    kotlinx.coroutines.delay(2000) // Show message for 2 seconds
                    _isScraping.value = false
                    _scrapingProgress.value = null
                    return@launch
                }
                
                // Update progress with vault count
                _scrapingProgress.value = VaultScrapeCoordinator.ScrapingProgress.Processing(
                    vaultId = "all",
                    message = "Scraping ${vaultsNeedingScraping.size} entries..."
                )
                
                // Scrape all assets for each vault sequentially using unified pipeline
                for ((index, vault) in vaultsNeedingScraping.withIndex()) {
                    // Update progress to show which vault is being scraped
                    _scrapingProgress.value = VaultScrapeCoordinator.ScrapingProgress.Processing(
                        vaultId = vault.id,
                        message = "Scraping ${vault.displayName} (${index + 1}/${vaultsNeedingScraping.size})..."
                    )
                    
                    vaultScrapeCoordinator.scrapeVault(vault.id).collect { progress ->
                        // Update progress with vault context
                        _scrapingProgress.value = when (progress) {
                            is VaultScrapeCoordinator.ScrapingProgress.Processing -> {
                                VaultScrapeCoordinator.ScrapingProgress.Processing(
                                    vaultId = vault.id,
                                    message = "[${index + 1}/${vaultsNeedingScraping.size}] ${vault.displayName}: ${progress.message}"
                                )
                            }
                            is VaultScrapeCoordinator.ScrapingProgress.AssetResult -> {
                                VaultScrapeCoordinator.ScrapingProgress.AssetResult(
                                    vaultId = vault.id,
                                    result = progress.result
                                )
                            }
                            is VaultScrapeCoordinator.ScrapingProgress.Complete -> {
                                VaultScrapeCoordinator.ScrapingProgress.Complete(
                                    vaultId = vault.id,
                                    results = progress.results
                                )
                            }
                            is VaultScrapeCoordinator.ScrapingProgress.Error -> {
                                VaultScrapeCoordinator.ScrapingProgress.Error(
                                    vaultId = vault.id,
                                    error = progress.error
                                )
                            }
                        }
                        
                        when (progress) {
                            is VaultScrapeCoordinator.ScrapingProgress.Complete -> {
                                return@collect
                            }
                            is VaultScrapeCoordinator.ScrapingProgress.Error -> {
                                android.util.Log.e("LibraryViewModel", "Error scraping ${vault.displayName}: ${progress.error.message}")
                                return@collect
                            }
                            else -> {
                                // Processing and AssetResult progress handled by UI state updates
                            }
                        }
                    }
                }
                
                // Refresh vaults to show updated artwork
                _vaultsRefreshTrigger.value++
                _isScraping.value = false
                _scrapingProgress.value = null
            } catch (e: Exception) {
                android.util.Log.e("LibraryViewModel", "Exception in scrapeAllBoxArtInternal", e)
                _scrapingProgress.value = VaultScrapeCoordinator.ScrapingProgress.Processing(
                    vaultId = "all",
                    message = classifyRefreshError(e)
                )
                kotlinx.coroutines.delay(3000)
                _isScraping.value = false
                _scrapingProgress.value = null
            }
        }
    }
    
/**
 * Scrape artwork options for a vault via the unified scraper fallback
 * (SteamGridDB → TheGamesDB). Surfaces typed errors so the picker
 * dialog can render a meaningful message instead of the generic
 * "No artwork found".
 */
fun scrapeArtworkOptions(vaultId: String): Flow<ArtworkOptionsResult> = flow {
    val vault = vaultRepository.getVaultById(vaultId)
    if (vault == null) {
        android.util.Log.e("LibraryViewModel", "Vault not found: $vaultId")
        emit(ArtworkOptionsResult.Error("Vault not found"))
        return@flow
    }

    // Target ratio mirrors the auto-pick (VaultScrapeCoordinator gridDisplayRatio)
    // so the picker's first item is what auto-assign would have chosen.
    val targetRatio = settingsRepository.gridDisplayRatio.first().aspectRatio

    // 1. Local artwork.
    //
    // (a) Every image physically sitting in the vault's own library folder
    //     (<home>/<GameName>/), enumerated directly so it works even when the
    //     media index is stale or the filename doesn't match the game name.
    //     Screenshots and maps live in that same flat folder, so they are
    //     excluded by their tracked resource kind.
    // (b) External LOCAL_LINK artwork matched from OTHER media search folders
    //     (files not copied into the vault folder — homeRelativePath is null).
    fun decodeDims(uriString: String): Pair<Int?, Int?> = runCatching {
        val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(android.net.Uri.parse(uriString))?.use {
            android.graphics.BitmapFactory.decodeStream(it, null, opts)
        }
        if (opts.outWidth > 0 && opts.outHeight > 0) opts.outWidth to opts.outHeight else null to null
    }.getOrNull() ?: (null to null)

    val artworkRows = resourceFileDao.getForVaultAndKind(vaultId, ResourceKind.ARTWORK.raw).first()
    val screenshotRows = resourceFileDao.getForVaultAndKind(vaultId, ResourceKind.SCREENSHOT.raw).first()
    val mapRows = resourceFileDao.getForVaultAndKind(vaultId, ResourceKind.MAP.raw).first()
    // Non-artwork files in the vault folder to keep out of the picker.
    val excludeRelPaths = (screenshotRows + mapRows).mapNotNull { it.homeRelativePath }.toSet()
    // Tracked artwork rows keyed by their in-folder path, so a vault-folder image
    // that already has a row reuses its id (cheap reassign, no copy).
    val artworkByRelPath = artworkRows.mapNotNull { r -> r.homeRelativePath?.let { it to r } }.toMap()

    val vaultDirName = com.twinlore.app.data.library.LibraryFolderManager.resolveFolderName(vault)
    val folderOptions = libraryFolderManager.listImagesInVault(vaultDirName)
        .filter { it.relativePath !in excludeRelPaths }
        .distinctBy { it.relativePath }
        .map { img ->
            val uriString = img.uri.toString()
            val (w, h) = decodeDims(uriString)
            ArtworkOption(
                url = uriString,
                thumbnailUrl = uriString,
                type = "Local",
                index = 0,
                isLocal = true,
                resourceFileId = artworkByRelPath[img.relativePath]?.id,
                width = w,
                height = h,
            )
        }

    val externalLinkOptions = artworkRows
        .filter { it.source == ResourceSource.LOCAL_LINK.raw && it.homeRelativePath == null && it.absolutePath != null }
        .map { e ->
            val path = e.absolutePath!!
            val uriString = if (path.startsWith("content://") || path.startsWith("file://")) path else "file://$path"
            val (w, h) = decodeDims(uriString)
            ArtworkOption(
                url = uriString,
                thumbnailUrl = uriString,
                type = "Local",
                index = 0,
                isLocal = true,
                resourceFileId = e.id,
                width = w,
                height = h,
            )
        }

    val localOptions = (folderOptions + externalLinkOptions)
        .distinctBy { it.url }
        .mapIndexed { idx, o -> o.copy(index = idx + 1) }

    // 2. Remote scrape (existing flow). If it fails AND we have local entries, still emit Success.
    try {
        // No cache — users explicitly invoking "Scrape Artwork" want a
        // fresh fetch every time, especially after changing BYOK keys.
        val artwork = vaultScrapeCoordinator.collectArtworkOptions(vault.displayName)
        fun candidatesToOptions(list: List<com.twinlore.app.data.scraping.AssetCandidate>, type: String) =
            list.mapIndexed { index, candidate ->
                ArtworkOption(
                    url = candidate.url,
                    thumbnailUrl = candidate.thumbnailUrl ?: candidate.url,
                    type = type,
                    index = index + 1,
                    width = candidate.width,
                    height = candidate.height,
                )
            }
        // Keep only the best-ratio candidates per category so the picker stays
        // focused: too many near-duplicate options are noise. Local artwork is the
        // user's own files, so it is never capped.
        val gridOptions = sortArtworkOptionsByRatio(candidatesToOptions(artwork.grids, "Grid"), targetRatio).take(GRID_OPTION_CAP)
        val heroOptions = sortArtworkOptionsByRatio(candidatesToOptions(artwork.heroes, "Hero"), targetRatio).take(HERO_OPTION_CAP)
        val logoOptions = sortArtworkOptionsByRatio(candidatesToOptions(artwork.logos, "Logo"), targetRatio).take(LOGO_OPTION_CAP)

        // Persist gameId so future scrapes skip the search step.
        val currentGameId = vault.gameId?.takeIf { it.isNotBlank() }
        if (currentGameId != artwork.gameId) {
            vaultRepository.updateVault(vault.copy(gameId = artwork.gameId))
        }

        // Emit already grouped/ratio-sorted per category; the picker renders one
        // horizontal row per category and preserves this within-category order.
        emit(ArtworkOptionsResult.Success(
            sortArtworkOptionsByRatio(localOptions, targetRatio) + gridOptions + heroOptions + logoOptions
        ))
    } catch (e: com.twinlore.app.data.scraping.ScrapeError) {
        android.util.Log.e("LibraryViewModel", "Scrape error loading artwork options: ${e.userMessage}", e)
        if (localOptions.isNotEmpty()) {
            emit(ArtworkOptionsResult.Success(sortArtworkOptionsByRatio(localOptions, targetRatio)))
        } else {
            emit(ArtworkOptionsResult.Error(e.userMessage))
        }
    } catch (e: Exception) {
        android.util.Log.e("LibraryViewModel", "Unexpected error loading artwork options", e)
        if (localOptions.isNotEmpty()) {
            emit(ArtworkOptionsResult.Success(sortArtworkOptionsByRatio(localOptions, targetRatio)))
        } else {
            emit(ArtworkOptionsResult.Error(e.message ?: "Unknown error while loading artwork."))
        }
    }
}

    /**
     * Batch version: fetches multiple [ResourceFile]s in a single Room round-trip.
     * Returns only the ones that exist. Order is not guaranteed.
     */
    suspend fun getResourceFiles(resourceIds: List<String>): List<ResourceFile> =
        if (resourceIds.isEmpty()) emptyList()
        else resourceFileDao.getByIds(resourceIds).map { it.toDomain() }

    /** Classify a refresh/scraping error into a user-friendly message. */
    private fun classifyRefreshError(e: Throwable): String = when {
        e is UnknownHostException || e is ConnectException ->
            "No internet connection. Connect to the internet and try again."
        e is SocketTimeoutException ->
            "Server not responding. Try again later."
        e is retrofit2.HttpException && e.code() in listOf(401, 403) ->
            "Credentials expired or invalid. Update them in Settings \u2192 Achievements."
        e is retrofit2.HttpException && e.code() in 500..599 ->
            "Server temporarily unavailable. Try again later."
        e is java.io.IOException ->
            "Connection failed. Check your internet connection and try again."
        else ->
            "Something went wrong. Please try again."
    }
}

/**
 * Formats a [com.twinlore.app.data.library.BindReport] from a per-vault re-scan
 * into the user-facing Toast string. Top-level so unit tests can exercise the
 * three branches (newly linked / alternatives only / no matches) without having
 * to instantiate LibraryViewModel.
 */
internal fun formatLocalMediaBindToast(
    report: com.twinlore.app.data.library.BindReport,
    vaultName: String,
): String {
    val newlyLinked = listOfNotNull(
        report.artworkBound?.let { "artwork" },
        report.guideBound?.let { "guide" },
    )
    return when {
        newlyLinked.isNotEmpty() ->
            "Linked ${newlyLinked.joinToString(" and ")} to $vaultName"
        report.artworkAvailable + report.guideAvailable > 0 ->
            "No new files to link for $vaultName (alternatives in picker)"
        else ->
            "No local matches found for $vaultName"
    }
}
/**
 * Whether a method chosen in the choose-method dialog is dropped, together with
 * its folder, when the picked folder holds none of that launcher's files. During
 * first setup (no method saved yet, or nothing in the library) a bad pick saves
 * nothing, so the user lands back on the choice instead of on a half-configured
 * source. Otherwise the folder and method are saved as before and the launcher's
 * help dialog explains what is missing.
 */
internal fun discardsFailedSourceSetup(savedSource: LastPlayedSource, libraryEmpty: Boolean): Boolean =
    libraryEmpty || savedSource == LastPlayedSource.NOT_CONFIGURED

internal fun sourceSetupFailureMessage(source: LastPlayedSource): String {
    val name = when (source) {
        LastPlayedSource.RETROARCH -> "RetroArch"
        LastPlayedSource.ES_DE -> "ES-DE"
        LastPlayedSource.COCOON -> "Cocoon"
        else -> "Launcher"
    }
    return "$name setup was not successful. No $name files were found in the selected folder, " +
        "so nothing was saved. Choose a method to try again."
}
