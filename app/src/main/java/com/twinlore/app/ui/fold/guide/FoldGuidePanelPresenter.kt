package com.twinlore.app.ui.fold.guide

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.twinlore.app.data.SettingsRepository
import com.twinlore.app.data.StatusBarMode
import com.twinlore.app.data.metrics.SystemMetrics
import com.twinlore.app.data.retroachievements.RaAchievementDisplay
import com.twinlore.app.domain.RaAchievementSortField
import com.twinlore.app.domain.RaAchievementTypeFilter
import com.twinlore.app.domain.SortDirection
import com.twinlore.app.services.ScreenCaptureConsentActivity
import com.twinlore.app.services.ScreenCaptureService
import com.twinlore.app.ui.fold.FoldHandover
import com.twinlore.app.di.ApplicationScope
import com.twinlore.app.domain.AppState
import com.twinlore.app.domain.GuideFileType
import com.twinlore.app.domain.SavedTab
import com.twinlore.app.domain.StateManager
import com.twinlore.app.domain.VaultTab
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The panel's state, which is now almost nothing.
 *
 * A plain class on the application scope, not an androidx `ViewModel`, because
 * there is no activity to scope one to: the panel exists precisely while the
 * app is backgrounded.
 *
 * It answers one question, "which guide", and then gets out of the way. The
 * reading position, the chapter, the zoom, the search and the caching all
 * belong to the app's own viewers, which the panel hosts. The version this
 * replaces kept its own copy of each, and every copy was a way for the panel
 * and the app to disagree about where you had got to.
 *
 * The vault is snapshotted when the panel opens rather than observed. The app
 * is in the background, so its state is not moving underneath.
 */
@Singleton
class FoldGuidePanelPresenter @Inject constructor(
    private val stateManager: StateManager,
    private val data: FoldGuideData,
    private val settingsRepository: SettingsRepository,
    private val systemMetrics: SystemMetrics,
    private val handover: FoldHandover,
    private val licenseManager: com.twinlore.app.data.license.LicenseManagerUiStateContract,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
    @ApplicationScope private val appScope: CoroutineScope,
) {
    private val _state = MutableStateFlow(FoldGuideUiState())
    val state: StateFlow<FoldGuideUiState> = _state.asStateFlow()

    /** Cancelled on close, so a slow read cannot land in the next panel. */
    private var loadJob: Job? = null

    /** Detection, cancelled on close: it resolves and scrapes, over a running game, for a panel that is gone. */
    private var detectJob: Job? = null

    /**
     * Whether the achievements have already fetched this open: by opening onto
     * them, or by arriving at them. Once per open: leaving for the guide and
     * coming straight back is not a new reason to go to the network over a
     * running game. The Refresh button is still there for someone who wants it now.
     */
    private var awardsFetchedThisOpen = false

    /** The vault the panel opened on, held for the App action. */
    private var vaultId: String? = null

    /**
     * Whether a panel is logically open, as opposed to a window being on
     * screen. The two differ: a swap between displays tears the window down and
     * builds a new one while the user is still reading, and without this that
     * rebuild would reload the guide underneath them.
     */
    @Volatile
    private var open = false

    /** Whether the global cluster is in Performance mode. Read on open. */
    @Volatile
    private var metersWanted = false

    /**
     * Persisted settings, written in the order they were asked for.
     *
     * A launch per write on [appScope] is a launch per write on a pool of
     * threads, and two quick taps on the same control could reach DataStore in
     * reverse order; the collectors in [init] would then push the earlier
     * value back over the later one. Enqueued synchronously here and drained
     * by one coroutine, the order taps happen in is the order they land in.
     */
    private val settingsWrites =
        kotlinx.coroutines.channels.Channel<suspend () -> Unit>(kotlinx.coroutines.channels.Channel.UNLIMITED)

    private fun writeSetting(write: suspend () -> Unit) {
        settingsWrites.trySend(write)
    }

    fun onPanelOpened() = open(resumed = false)

    /**
     * A parked panel came back. Its reader is still composed on the guide it
     * had, so the same vault keeps its state as it is: [beginVault] would put
     * the guide through null, dispose the reader and load the page again. A
     * different vault, or a load the fold cut short, loads as an open does.
     */
    fun onPanelResumed() = open(resumed = true)

    private fun open(resumed: Boolean) {
        if (open) return
        open = true
        // The app's own poller is owned by MainActivity's lifecycle scope, and
        // the panel is up precisely when that activity is not in front. Asking
        // here is what keeps the meters moving; the count in [SystemMetrics] is
        // what stops this from fighting the activity over the same job.
        if (metersWanted) systemMetrics.start(this)
        awardsFetchedThisOpen = false

        val vault = (stateManager.currentState.value as? AppState.VaultSession)?.vaultId
        val keep = resumed && vault == vaultId && !_state.value.loading
        if (!keep) {
            beginVault(vault)
        } else if (vault != null && _state.value.awardsLoading) {
            // The guide landed but the fold cut the achievements read after it.
            appScope.launch {
                val awards = runCatching { data.awards(vault) }.getOrDefault(FoldAwards())
                _state.update { if (it.vaultId == vault) it.copy(awards = awards, awardsLoading = false) else it }
                refreshAwardsOnArrival(afterLoad = true)
            }
        }
        // A panel that opens onto the achievements is one being opened to read
        // them, so they are made true the way arriving at them does.
        refreshAwardsOnArrival()
        // And, once the panel is showing something, whether the game behind it
        // is still the one it is showing. After the load, never before: the
        // guide is what the panel opened for.
        detectJob?.cancel()
        detectJob = appScope.launch {
            val found = runCatching { data.detectNewlyPlayedGame(vaultId) }.getOrNull() ?: return@launch
            // The panel may have folded, or moved on, while detection was out.
            if (!open || found.vaultId == vaultId) return@launch
            _state.update { it.copy(detectedGame = found) }
        }
    }

    /**
     * Points the panel at [vault] and loads it: the open path, and the path a
     * switch to a newly detected game takes.
     */
    private fun beginVault(vault: String?) {
        // Fold, glance at the game, unfold is the same vault, and it is the
        // most common thing that happens to this window. Only a different
        // vault takes the old one's list, thread, images and shutter with it.
        val vaultChanged = vault != vaultId
        vaultId = vault
        _state.update {
            val base = it.copy(
                // Where you were, not where the panel likes to start. Folding to
                // glance at the game and unfolding was landing on the guide
                // every time regardless of what you had been reading.
                surface = lastSurface,
                detectedGame = null,
                guide = null,
                blockedLink = null,
                title = "",
                vaultId = vault,
                loading = vault != null,
            )
            if (!vaultChanged) {
                base
            } else {
                // Awards load after the guide, and without this the
                // achievements surface - which a persisted lastSurface can open
                // straight onto - showed the previous vault's list for the
                // whole of the guide read.
                base.copy(
                    images = emptyList(),
                    imageIndex = 0,
                    imageView = null,
                    awards = FoldAwards(),
                    awardsLoading = vault != null,
                    awardSearch = "",
                    // A fetch still in flight for the old vault is dropped when
                    // it lands; it must not hold this vault's button either.
                    awardsRefreshing = false,
                    commentsFor = null,
                    comments = emptyList(),
                    commentsLoading = false,
                    // Same for a shot: it files into the vault it was taken
                    // for, and this one's shutter is not waiting on it.
                    capturing = false,
                    // And a question asked about the old vault's image must
                    // not be answered on the new vault's.
                    deletePrompt = false,
                )
            }
        }
        if (vault == null) return

        loadJob?.cancel()
        loadJob = appScope.launch {
            // A pan still settling belongs on disk before anything reads it.
            flushImageView()
            val bookmark = data.currentGuide(vault)
            val guide = bookmark?.let { resolveGuide(it) }
            val images = data.images(vault)
            // Seeded from the guide itself, so the Settings surface opens
            // showing the size the reader is actually at.
            bookmark?.zoomLevel?.let { zoom -> _state.update { it.copy(textZoom = zoom) } }
            _state.update { it.copy(title = bookmark?.displayTitle.orEmpty()) }
            // The image you were on, if the same vault still has it. Otherwise
            // the one the app is open on, which is the same record this panel
            // writes when it pages: folding from the Image tab lands on the
            // picture that was on screen, not on the top of a list.
            val keptId = if (vaultChanged) null else _state.value.currentImage?.id
            val keptIndex = keptId
                ?.let { id -> images.indexOfFirst { it.id == id } }
                ?.takeIf { it >= 0 }
            val storedIndex = if (keptIndex != null) null else {
                runCatching { data.currentImage(vault) }.getOrNull()
                    ?.let { id -> images.indexOfFirst { it.id == id } }
                    ?.takeIf { it >= 0 }
            }
            val index = keptIndex ?: storedIndex ?: 0
            _state.update {
                it.copy(
                    guide = guide,
                    images = images,
                    imageIndex = index,
                    // A kept image keeps its live pan; anything else reads its own.
                    imageView = if (keptIndex != null) it.imageView else null,
                    loading = false,
                )
            }
            images.getOrNull(index)?.let { shown ->
                if (keptIndex != null && _state.value.imageView != null) return@let
                val view = runCatching { data.imageView(vault, shown.id) }.getOrNull()
                _state.update { if (it.currentImage?.id == shown.id) it.copy(imageView = view) else it }
            }
            // After the guide, not beside it: this is a disk read the guide
            // does not wait on, and the guide is what the panel opens showing.
            val awards = runCatching { data.awards(vault) }.getOrDefault(FoldAwards())
            _state.update { it.copy(awards = awards, awardsLoading = false) }
            refreshAwardsOnArrival(afterLoad = true)
        }
    }

    /**
     * The once-per-open fetch, when the achievements are what is showing.
     *
     * Held back while the cached list is still being read: that read lands
     * after a fast refresh and would put the cache back over it. The read
     * calls this again when it lands, as [afterLoad]. A reload of the same
     * vault keeps its list on screen and never says it is loading, so the
     * load job itself is the signal there.
     */
    private fun refreshAwardsOnArrival(afterLoad: Boolean = false) {
        val s = _state.value
        if (!open || awardsFetchedThisOpen || s.surface != FoldPanelSurface.AWARDS) return
        if (s.awardsLoading || (!afterLoad && loadJob?.isActive == true)) return
        awardsFetchedThisOpen = true
        refreshAwards()
    }

    /**
     * The same forced refresh the app's drawer runs, against the same cache, so
     * pulling fresh here is fresh in both places.
     *
     * A failed or unlinked refresh leaves the list alone. The panel is over a
     * running game and the achievements it already has are still true; blanking
     * them to report a network problem trades something useful for something
     * that is not.
     */
    fun refreshAwards() {
        val vault = vaultId ?: return
        if (_state.value.awardsRefreshing) return
        _state.update { it.copy(awardsRefreshing = true) }
        appScope.launch {
            val fresh = runCatching { data.refreshAwards(vault) }.getOrNull()
            _state.update {
                // Only for the vault it was fetched for. The panel may have
                // folded and reopened on another game while this was out.
                if (vaultId != vault) it
                else if (fresh != null) it.copy(awards = fresh, awardsRefreshing = false)
                else it.copy(awardsRefreshing = false)
            }
        }
    }

    /**
     * Opens one achievement's comments over the list.
     *
     * In place of the list rather than in the sheet the app uses: a
     * ModalBottomSheet is a Dialog, and a Dialog cannot be shown from an
     * overlay window.
     */
    fun showComments(achievement: RaAchievementDisplay) {
        _state.update {
            it.copy(commentsFor = achievement, comments = emptyList(), commentsLoading = true)
        }
        appScope.launch {
            val comments = runCatching { data.awardComments(achievement.id) }
                .getOrDefault(emptyList())
            _state.update {
                // Only if the same thread is still the open one: a fast tap
                // through two achievements must not land the first one's
                // comments under the second one's title.
                if (it.commentsFor?.id == achievement.id) {
                    it.copy(comments = comments, commentsLoading = false)
                } else {
                    it
                }
            }
        }
    }

    fun dismissComments() {
        _state.update { it.copy(commentsFor = null, comments = emptyList(), commentsLoading = false) }
    }

    /**
     * The list's four narrowings, each a tap that moves to the next value.
     *
     * Session state, exactly as the app's drawer holds them: a filter is about
     * the question being asked right now, not a setting to be remembered.
     */
    fun cycleAwardTypeFilter() {
        val next = _state.value.awardTypeFilter.next()
        _state.update { it.copy(awardTypeFilter = next) }
        writeSetting { settingsRepository.setFoldPanelAwardTypeFilter(next.name) }
    }

    /** The awards search, kept for the panel's life and cleared with the vault. */
    fun setAwardSearch(query: String) {
        _state.update { it.copy(awardSearch = query) }
    }

    fun toggleAwardsLockedOnly() {
        val locked = !_state.value.awardsLockedOnly
        _state.update { it.copy(awardsLockedOnly = locked) }
        writeSetting { settingsRepository.setFoldPanelAwardLockedOnly(locked) }
    }

    /**
     * Where the list was left. Written on a settled scroll, not per frame: the
     * caller debounces, because this is a disk write and a LazyColumn emits a
     * new position every row it passes.
     */
    fun setAwardScroll(index: Int, offset: Int) {
        _state.update { it.copy(awardScroll = index to offset) }
        writeSetting { settingsRepository.setFoldPanelAwardScroll(index, offset) }
    }

    /** Cycles the field, landing each one on its own default direction. */
    fun cycleAwardSort() {
        if (_state.value.awards.source == FoldAwardSource.STEAM) {
            val steamFields = com.twinlore.app.domain.SteamAchievementSortField.entries
            val next = steamFields[(_state.value.steamAwardSortField.ordinal + 1) % steamFields.size]
            _state.update { it.copy(steamAwardSortField = next, steamAwardSortDirection = next.defaultDirection) }
            return
        }
        val fields = RaAchievementSortField.entries
        val next = fields[(_state.value.awardSortField.ordinal + 1) % fields.size]
        _state.update { it.copy(awardSortField = next, awardSortDirection = next.defaultDirection) }
        writeSetting {
            settingsRepository.setFoldPanelAwardSort(next.name, next.defaultDirection.name)
        }
    }

    fun toggleAwardSortDirection() {
        if (_state.value.awards.source == FoldAwardSource.STEAM) {
            _state.update { it.copy(steamAwardSortDirection = it.steamAwardSortDirection.toggle()) }
            return
        }
        val direction = _state.value.awardSortDirection.toggle()
        val field = _state.value.awardSortField
        _state.update { it.copy(awardSortDirection = direction) }
        writeSetting { settingsRepository.setFoldPanelAwardSort(field.name, direction.name) }
    }

    /**
     * The bar's surfaces. Session state: the panel opens on the guide.
     *
     * Leaving the achievements surface closes an open comment thread, so coming
     * back lands on the list rather than on whatever was being read when the
     * surface was last abandoned.
     */
    fun selectSurface(surface: FoldPanelSurface) {
        val wasOn = _state.value.surface
        _state.update {
            if (surface == FoldPanelSurface.AWARDS) it.copy(surface = surface)
            else it.copy(surface = surface, commentsFor = null, comments = emptyList())
        }
        rememberSurface(surface)
        // Arriving at the achievements is the moment the list is being read,
        // so it is the moment to make it true. Opening the panel on another
        // surface still does not fetch: that happens over a running game and
        // nobody asked for it. Only on arrival, once per open, and
        // [refreshAwards] drops it anyway if one is already in flight.
        if (surface == FoldPanelSurface.AWARDS && wasOn != FoldPanelSurface.AWARDS) refreshAwardsOnArrival()
    }

    /** The surface the next panel opens on, followed to the store. */
    private fun rememberSurface(surface: FoldPanelSurface) {
        // Settings is somewhere you go and come back from, not somewhere you
        // were reading. A panel that reopened on it would have thrown away the
        // guide you left, which is the one thing this memory exists to keep.
        if (surface == FoldPanelSurface.SETTINGS) return
        lastSurface = surface
        writeSetting { settingsRepository.setFoldPanelSurface(surface.name) }
    }

    /** The surface a new panel opens on. Seeded from the store, then followed. */
    private var lastSurface: FoldPanelSurface = FoldPanelSurface.GUIDE

    /**
     * Steps [delta] images and tells the app where the panel got to.
     *
     * `touchImage` writes the same selection the app's Image tab restores
     * to, so paging here is where the app opens next, exactly as paging a
     * group's chapters is.
     */
    fun stepImage(delta: Int) {
        val snapshot = _state.value
        val target = snapshot.imageIndex + delta
        if (target !in snapshot.images.indices) return
        // Null first, then whatever was stored: the next image must not open at
        // the previous one's pan while its own is being read.
        _state.update { it.copy(imageIndex = target, imageView = null) }
        val image = snapshot.images[target]
        val vault = vaultId ?: return
        appScope.launch {
            // The pan just made on the image being left lands on disk before
            // the next one is read: stepping back inside the settle window
            // would otherwise restore the pan from before that gesture.
            flushImageView()
            data.touchImage(vault, image.id)
            run {
                val view = runCatching { data.imageView(vault, image.id) }.getOrNull()
                _state.update {
                    if (it.images.getOrNull(it.imageIndex)?.id == image.id) {
                        it.copy(imageView = view)
                    } else {
                        it
                    }
                }
            }
        }
    }

    private var imageViewWrite: Job? = null

    /** The pan waiting to be written: vault, image, and where it is. */
    @Volatile
    private var pendingImageView: Triple<String, String, FoldImageView>? = null

    /**
     * Where the shown image has been panned to, on its way to the same manager
     * the app's Image tab reads.
     *
     * The state takes it at once. `MapZoomableImage` re-applies whatever it was
     * handed as its initial values on every finger lift, so the state has to
     * carry the live pan or every release snaps the picture back to the last
     * saved one. The app's own map tab does exactly this.
     *
     * Only the disk write is debounced, because the caller cannot: this arrives
     * once per pointer sample, from inside `detectTransformGestures`, and
     * undebounced it was a SharedPreferences commit per frame of every pan,
     * over a running game. The app's map tab settles the identical write by the
     * same half second.
     */
    fun onImageViewChanged(view: FoldImageView) {
        val vault = vaultId ?: return
        val image = _state.value.currentImage ?: return
        _state.update { it.copy(imageView = view) }
        pendingImageView = Triple(vault, image.id, view)
        imageViewWrite?.cancel()
        imageViewWrite = appScope.launch {
            delay(IMAGE_VIEW_WRITE_DELAY_MS)
            flushImageView()
        }
    }

    /**
     * Writes the pan still waiting, now. Called before anything reads a pan
     * back and when the panel closes, so the disk never answers with the
     * position from before the gesture that was just made.
     */
    private suspend fun flushImageView() {
        val pending = pendingImageView ?: return
        pendingImageView = null
        val (vault, imageId, view) = pending
        runCatching { data.saveImageView(vault, imageId, view) }
    }

    /**
     * A link the reader refused to follow.
     *
     * Nothing is queued and nothing is promised: this window cannot follow a
     * link, and an Open button that only reached the app was a worse answer
     * than saying so.
     */
    fun onExternalLink(url: String) {
        _state.update { it.copy(blockedLink = FoldBlockedLink(url)) }
    }

    fun dismissBlockedLink() {
        _state.update { it.copy(blockedLink = null) }
    }

    fun onPanelClosed() {
        if (!open) return
        open = false
        loadJob?.cancel()
        detectJob?.cancel()
        imageViewWrite?.cancel()
        appScope.launch { flushImageView() }
        systemMetrics.stop(this)
        // Not while a shot is in flight. The broadcast is the only thing that
        // files the image and the only thing that deletes the full-screen PNG
        // under cacheDir, so tearing the receiver down here would leak a file
        // per fold-right-after-the-shutter. The watchdog closes it instead.
        if (!_state.value.capturing) unregisterCaptureReceiver()
    }

    // ----- Screenshots -----

    /**
     * Takes a screenshot of [displayId] and files it into the vault.
     *
     * The consent sheet is the one thing this window cannot ask for: an
     * overlay has no activity to return a result to. What it does NOT need is
     * the app - [ScreenCaptureConsentActivity] is a translucent trampoline that
     * hosts the sheet over whatever is on screen and finishes, so the panel
     * never moves. And it is asked for once: the service keeps the projection,
     * so later shots go straight to a capture with no sheet at all.
     *
     * [displayId] is a parameter because a two-screen device has no single
     * right answer, and the caller is the only thing that knows which screen
     * the panel is on.
     */
    fun takeScreenshot(displayId: Int) {
        val vault = vaultId ?: return
        if (_state.value.capturing) return
        registerCaptureReceiver()
        captureVault = vault
        _state.update { it.copy(capturing = true) }
        ScreenCaptureConsentActivity.capture(context, displayId, SCREENSHOT_TAG_PANEL)
        // A refused consent sends no broadcast at all, and a spinner that can
        // only be cleared by success is a spinner that stays forever.
        captureWatchdog?.cancel()
        captureWatchdog = appScope.launch {
            delay(CAPTURE_TIMEOUT_MS)
            withContext(Dispatchers.Main) { captureSettled() }
        }
    }

    private var captureReceiver: BroadcastReceiver? = null
    private var captureWatchdog: Job? = null

    /** The vault a shot was asked for, which is where it is filed. */
    private var captureVault: String? = null

    /**
     * A capture stopped being in flight, however it stopped.
     *
     * The spinner goes, and a panel that has already folded gives up its
     * receiver: [onPanelClosed] deliberately leaves one up so a shot still on
     * its way can be filed and its temporary PNG deleted, and this is where
     * that debt is settled. On the main thread, where the panel opens, closes
     * and takes shots, so a shutter tap cannot slip between the check and the
     * teardown and lose its own receiver.
     */
    private fun captureSettled() {
        _state.update { it.copy(capturing = false) }
        // [captureVault] is deliberately left alone. It is the record of which
        // vault asked for the shot, and a shot can still land after this: the
        // watchdog gives up on the spinner at thirty seconds, but a slow
        // consent answered at thirty-one still files, and it files where it
        // was asked for rather than wherever the panel has since moved.
        if (!open) unregisterCaptureReceiver()
    }

    /**
     * Registered on the first shot rather than on open: most panels never take
     * one, and a receiver is not free.
     */
    private fun registerCaptureReceiver() {
        if (captureReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                when (intent?.action) {
                    ScreenCaptureService.ACTION_SCREENSHOT_TAKEN -> {
                        // The broadcast is package-wide, so the app's own Image
                        // tab hears this one too. The tag is what keeps a shot
                        // taken there from being filed twice.
                        val tag = intent.getStringExtra(ScreenCaptureService.EXTRA_REQUEST_TAG)
                        if (tag != SCREENSHOT_TAG_PANEL) return
                        val path = intent
                            .getStringExtra(ScreenCaptureService.EXTRA_FILE_PATH) ?: return
                        onScreenshotTaken(java.io.File(path))
                    }
                    ScreenCaptureService.ACTION_PROJECTION_LOST -> {
                        // Android 15 stops the projection when the device
                        // locks. Nothing to repair: the next shot asks again.
                        // Through the same settle as a landed shot, so a panel
                        // that has since folded closes its receiver here too
                        // rather than holding one until the next open.
                        captureSettled()
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(ScreenCaptureService.ACTION_SCREENSHOT_TAKEN)
            addAction(ScreenCaptureService.ACTION_PROJECTION_LOST)
        }
        runCatching {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        }.onSuccess { captureReceiver = receiver }
    }

    private fun unregisterCaptureReceiver() {
        captureReceiver?.let { receiver ->
            runCatching { context.unregisterReceiver(receiver) }
        }
        captureReceiver = null
        captureWatchdog?.cancel()
        captureWatchdog = null
        _state.update { it.copy(capturing = false) }
    }

    /**
     * What the broadcast does once a shot has landed, reachable without one so
     * the filing and the jump to the new image are unit tests rather than a
     * device walk.
     */
    internal fun onScreenshotTaken(file: java.io.File) {
        // The vault the shot was asked for, not the one the panel is on now:
        // the receiver is left up across a fold so a shot in flight still
        // lands, and the panel may have reopened on another game meanwhile.
        val vault = captureVault ?: vaultId
        captureWatchdog?.cancel()
        appScope.launch {
            val image = vault?.let { runCatching { data.saveScreenshot(it, file) }.getOrNull() }
            // The temp under cacheDir is dead weight once the bytes are in the
            // vault, and it is a full-screen PNG. Deleted whatever happened.
            runCatching { file.delete() }
            withContext(Dispatchers.Main) {
                captureSettled()
                // Another vault's shot has nothing to say to this panel.
                if (vault != vaultId) return@withContext
                _state.update { state ->
                    if (image == null) {
                        state
                    } else {
                        // Straight to the new shot: somebody who just took one
                        // is looking for it, not for where they were. Not
                        // appended twice if a reopen's own read already has it.
                        val images =
                            if (state.images.any { it.id == image.id }) state.images
                            else state.images + image
                        state.copy(
                            images = images,
                            imageIndex = images.indexOfFirst { it.id == image.id },
                            // Null, for the same reason stepping nulls it: a
                            // fresh capture must not open at the pan of
                            // whatever was being looked at before it.
                            imageView = null,
                            surface = FoldPanelSurface.IMAGE,
                            capturing = false,
                        )
                    }
                }
                // The next panel opens on the shot too, as if Image had been
                // picked from the bar, and so does the app's Image tab: its
                // own screenshot path selects the new shot, and this is the
                // same selection.
                if (image != null && vault != null) {
                    rememberSurface(FoldPanelSurface.IMAGE)
                    runCatching { data.touchImage(vault, image.id) }
                }
            }
        }
    }

    /**
     * Only the platform knows how many displays there are, so it says. False
     * hides Swap entirely rather than offering a control with nowhere to go.
     */
    /**
     * A hold on the handle: ask before closing.
     *
     * The hold used to return to the app, which one tap undid. It closes now,
     * and a hold is easy to do by accident with a thumb resting on a small
     * mark, so nothing happens until the question is answered.
     */
    fun askCloseApp(openedPanel: Boolean = false) {
        closePromptOpenedPanel = openedPanel
        _state.update { it.copy(closePrompt = true) }
    }

    fun dismissCloseApp() {
        closePromptOpenedPanel = false
        _state.update { it.copy(closePrompt = false) }
    }

    /** Whether the hold that asked the question is what opened the panel. */
    private var closePromptOpenedPanel = false

    /**
     * The held handle's Swap: the handle, and the panel with it, to the other
     * screen, staying in the mode. A panel that only came up to ask the
     * question folds again once it lands, so a hold and a Swap from a folded
     * handle leave a folded handle on the other screen.
     */
    fun swapFromCloseApp(swap: () -> Boolean, fold: () -> Unit) {
        val foldAfter = closePromptOpenedPanel
        dismissCloseApp()
        if (swap() && foldAfter) fold()
    }

    /**
     * The bin beside the shutter: ask before the shown image goes.
     *
     * Same shape as the close question, for the same reason. A delete is
     * one tap on a button that sits where a thumb rests, it cannot be undone,
     * and this window cannot show a Dialog, so the question is state and the
     * panel draws it.
     */
    fun askDeleteImage() {
        if (_state.value.currentImage == null) return
        _state.update { it.copy(deletePrompt = true) }
    }

    fun dismissDeleteImage() {
        _state.update { it.copy(deletePrompt = false) }
    }

    /**
     * The answer was yes. The list moves on at once, to the image after the
     * deleted one, or the one before if it was last; the store follows, and
     * the app's own selection is pointed at the same neighbour so its Image
     * tab agrees about what is on screen.
     */
    fun confirmDeleteImage() {
        val vault = vaultId
        val snapshot = _state.value
        val image = snapshot.currentImage
        if (vault == null || image == null) {
            dismissDeleteImage()
            return
        }
        val remaining = snapshot.images.filterNot { it.id == image.id }
        val index = snapshot.imageIndex.coerceIn(0, (remaining.size - 1).coerceAtLeast(0))
        // A pan still waiting for the deleted image has nowhere to go.
        if (pendingImageView?.second == image.id) {
            imageViewWrite?.cancel()
            pendingImageView = null
        }
        _state.update {
            it.copy(deletePrompt = false, images = remaining, imageIndex = index, imageView = null)
        }
        appScope.launch {
            runCatching { data.deleteImage(vault, image.id) }
            val next = remaining.getOrNull(index) ?: return@launch
            runCatching { data.touchImage(vault, next.id) }
            val view = runCatching { data.imageView(vault, next.id) }.getOrNull()
            _state.update {
                if (it.currentImage?.id == next.id) it.copy(imageView = view) else it
            }
        }
    }

    /** Where the handle is, so the page can sit clear of it. See the UI state. */
    fun setHandleGeometry(onRight: Boolean, yFraction: Float) {
        _state.update {
            it.copy(handleOnRight = onRight, handleYFraction = yFraction.coerceIn(0f, 1f))
        }
    }

    fun setSwapAvailable(available: Boolean) {
        _state.update { it.copy(swapAvailable = available) }
    }

    /** The bar's collapsed state, remembered across folds. */
    fun setBarCollapsed(collapsed: Boolean) {
        _state.update { it.copy(barCollapsed = collapsed) }
        writeSetting { settingsRepository.setFoldPanelBarCollapsed(collapsed) }
    }


    /**
     * Switch: the panel follows the game that was started behind it.
     *
     * The app's own state moves too, so pressing App afterwards lands on this
     * game rather than the one the panel was opened on.
     */
    fun switchToDetectedGame() {
        val target = _state.value.detectedGame ?: return
        _state.update { it.copy(detectedGame = null) }
        appScope.launch {
            runCatching { data.markLaunchHandled(target.launchMarker) }
            // An open, as far as the library is concerned: its last-opened
            // order has to move, or the game being played sits below the one
            // that was merely read about.
            runCatching { data.markOpened(target.vaultId) }
            runCatching {
                // validate = false, deliberately. The rule refuses a hop
                // straight from one vault session to another because in-app
                // navigation always goes back through the library first. This
                // is not navigation: the game running behind the panel has
                // changed, and the panel is following it.
                stateManager.setState(
                    AppState.VaultSession(target.vaultId, tabForSurface(_state.value.surface)),
                    validate = false,
                )
            }
            beginVault(target.vaultId)
        }
    }

    /**
     * The offer's Support button. Leaves the note the app reads on arrival;
     * the caller hands over, exactly as Return does, and the app shows the
     * dialog this window cannot. The offer itself is left standing: coming
     * back to it is a decision about the game, not about supporting.
     */
    fun armSupportRequest() {
        handover.armSupport()
    }

    /**
     * Stay: keep reading what is open. The launch is still marked handled, so
     * returning to the app does not ask the same question again about the same
     * game: it was asked, and answered.
     */
    fun dismissDetectedGame() {
        val target = _state.value.detectedGame ?: return
        _state.update { it.copy(detectedGame = null) }
        appScope.launch { runCatching { data.markLaunchHandled(target.launchMarker) } }
    }

    /** The app tab that shows what a panel surface shows. */
    private fun tabForSurface(surface: FoldPanelSurface): VaultTab = when (surface) {
        FoldPanelSurface.IMAGE -> VaultTab.Map
        FoldPanelSurface.AWARDS -> VaultTab.Achievements
        else -> VaultTab.Guide
    }

    /** The first-open card's button. Written once; the card never comes back. */
    fun dismissIntro() {
        _state.update { it.copy(introSeen = true) }
        writeSetting { settingsRepository.setFoldPanelIntroSeen(true) }
    }

    /**
     * The status cluster was tapped. Standard and Performance trade places and
     * Off and Hidden stay put, which is the app's own rule, and the write goes
     * to the app's own setting so both bars change together.
     */
    fun tapToggleStatusBarMode() {
        val next = _state.value.statusBarMode.tapToggled()
        _state.update { it.copy(statusBarMode = next) }
        writeSetting { settingsRepository.setStatusBarMode(next) }
    }

    init {
        // One drain for every persisted setting, in the order it was asked.
        appScope.launch {
            for (write in settingsWrites) runCatching { write() }
        }
        // The offer's Support button, only while there is something to ask
        // for. Unknown shows nothing: a button that appears and vanishes as
        // the license record loads is worse than one that arrives late.
        appScope.launch {
            kotlinx.coroutines.flow.combine(
                com.twinlore.app.data.effectiveAppIconFlow(settingsRepository, licenseManager),
                licenseManager.licenseUiState,
            ) { icon, license ->
                if (license is com.twinlore.app.data.license.LicenseUiState.Free) icon else null
            }.collect { emblem -> _state.update { it.copy(supportEmblem = emblem) } }
        }
        appScope.launch {
            settingsRepository.foldPanelIntroSeen.collect { seen ->
                _state.update { it.copy(introSeen = seen) }
            }
        }
        appScope.launch {
            settingsRepository.foldPanelBarCollapsed.collect { collapsed ->
                _state.update { it.copy(barCollapsed = collapsed) }
            }
        }
        // Read once into [lastSurface] rather than pushed into state: a panel
        // that is already open must not jump surfaces because the store emitted.
        // Collected into state, not read once like the surface: these are the
        // controls on the surface itself, so the value that comes back is the
        // one this panel just wrote and there is nothing to jump.
        appScope.launch {
            settingsRepository.foldPanelAwardSort.collect { name ->
                val field = RaAchievementSortField.entries.firstOrNull { it.name == name }
                    ?: RaAchievementSortField.UNLOCK_TIME
                _state.update { it.copy(awardSortField = field) }
            }
        }
        appScope.launch {
            settingsRepository.foldPanelAwardSortDirection.collect { name ->
                val direction = SortDirection.entries.firstOrNull { it.name == name }
                _state.update {
                    it.copy(awardSortDirection = direction ?: it.awardSortField.defaultDirection)
                }
            }
        }
        appScope.launch {
            settingsRepository.foldPanelAwardTypeFilter.collect { name ->
                val filter = RaAchievementTypeFilter.entries.firstOrNull { it.name == name }
                    ?: RaAchievementTypeFilter.ALL
                _state.update { it.copy(awardTypeFilter = filter) }
            }
        }
        appScope.launch {
            settingsRepository.foldPanelAwardLockedOnly.collect { locked ->
                _state.update { it.copy(awardsLockedOnly = locked) }
            }
        }
        appScope.launch {
            settingsRepository.foldPanelAwardScroll.collect { (index, offset) ->
                _state.update { it.copy(awardScroll = index to offset) }
            }
        }
        appScope.launch {
            settingsRepository.foldPanelSurface.collect { name ->
                lastSurface = FoldPanelSurface.entries
                    .firstOrNull { it.name == name } ?: FoldPanelSurface.GUIDE
            }
        }
        // The handle's own settings, now that they are shown on a surface
        // rather than in a menu built on a touch. Collected rather than read
        // once: the panel is where they are changed from.
        //
        // The sliders apply and persist through the platform, which owns the
        // live handle, so what comes back here is the value this panel just
        // wrote. Their rows hold their own value while a drag is in flight, so
        // a store that emits mid-drag cannot drag the thumb back.
        appScope.launch {
            settingsRepository.foldHandleSizeDp.collect { dp ->
                _state.update { it.copy(handleSizeDp = dp) }
            }
        }
        appScope.launch {
            settingsRepository.foldHandleAlpha.collect { alpha ->
                _state.update { it.copy(handleAlpha = alpha) }
            }
        }
        appScope.launch {
            settingsRepository.foldHandleRecess.collect { recess ->
                _state.update { it.copy(handleRecess = recess) }
            }
        }
        appScope.launch {
            settingsRepository.foldHandleClockBattery.collect { on ->
                _state.update { it.copy(handleClockBattery = on) }
            }
        }
        // The icon and the font, for the Settings surface's two rows. The
        // icons offered are only the ones this user can already have: buying
        // or earning one happens in the app, not over a game.
        appScope.launch {
            kotlinx.coroutines.flow.combine(
                com.twinlore.app.data.effectiveAppIconFlow(settingsRepository, licenseManager),
                licenseManager.licenseTier,
                settingsRepository.checklistTapGameHighScore,
            ) { icon, tier, tapHighScore ->
                icon to com.twinlore.app.data.selectableAppIcons(tier, tapHighScore)
            }.collect { (icon, selectable) ->
                _state.update { it.copy(appIcon = icon, selectableIcons = selectable) }
            }
        }
        appScope.launch {
            settingsRepository.appFont.collect { font ->
                _state.update { it.copy(appFont = font) }
            }
        }
        // The guide's appearance, which the app keeps in a bottom sheet this
        // window cannot host. These two are global and the viewers collect them
        // live, so changing one here shows immediately in the reader.
        appScope.launch {
            settingsRepository.guideThemeInject.collect { inject ->
                _state.update { it.copy(themeInject = inject) }
            }
        }
        // The app's palette, which the panel's own theme already follows; held
        // here only so the Settings surface can mark the one in use.
        appScope.launch {
            settingsRepository.themePalette.collect { palette ->
                _state.update { it.copy(themePalette = palette) }
            }
        }
        appScope.launch {
            settingsRepository.guideSmartFormatting.collect { smart ->
                _state.update { it.copy(smartFormatting = smart) }
            }
        }
        // The same setting the app's own bar pins itself with, so the two
        // agree: pinning here and unfolding leaves the app's bar pinned too.
        appScope.launch {
            settingsRepository.keepTopBarVisible.collect { pinned ->
                _state.update { it.copy(pinned = pinned) }
            }
        }
        // Meters follow the global status-cluster mode, and the poll follows
        // them: nothing is sampled unless a bar is actually drawing the numbers.
        appScope.launch {
            settingsRepository.statusBarMode.collect { mode ->
                _state.update { it.copy(statusBarMode = mode) }
                metersWanted = mode == StatusBarMode.PERFORMANCE
                if (metersWanted && open) systemMetrics.start(this@FoldGuidePanelPresenter)
                if (!metersWanted) {
                    systemMetrics.stop(this@FoldGuidePanelPresenter)
                    _state.update { it.copy(metrics = null) }
                }
            }
        }
        appScope.launch {
            systemMetrics.snapshot.collect { snapshot ->
                _state.update { if (metersWanted) it.copy(metrics = snapshot) else it }
            }
        }
    }

    /**
     * Pins the top bar so a tap on the page cannot take it away.
     *
     * Writes the app's own `keepTopBarVisible`, not a panel-local flag: it is
     * the same question about the same kind of bar, and two settings for it
     * would disagree the first time someone used both surfaces.
     */
    fun setPinned(pinned: Boolean) {
        _state.update { it.copy(pinned = pinned) }
        writeSetting { settingsRepository.setKeepTopBarVisible(pinned) }
    }

    /**
     * Whether the handle shows the clock and battery. Only written here: the
     * platform follows the same setting and resizes the live handle. Neither
     * shows while this panel is open, so the change is seen on the next fold.
     */
    fun setHandleClockBattery(on: Boolean) {
        _state.update { it.copy(handleClockBattery = on) }
        writeSetting { settingsRepository.setFoldHandleClockBattery(on) }
    }

    /**
     * The app icon, the same selection Settings writes. The handle follows it
     * at once (the platform collects the same setting); the launcher icon is
     * left for the app's next close, since flipping it takes the task down.
     */
    fun setAppIcon(icon: com.twinlore.app.data.AppIcon) {
        if (icon !in _state.value.selectableIcons) return
        _state.update { it.copy(appIcon = icon) }
        writeSetting { settingsRepository.setSelectedAppIcon(icon) }
    }

    /** The app's font, the same one Settings writes. The panel and the handle's clock follow it live. */
    fun setAppFont(font: com.twinlore.app.data.AppFont) {
        _state.update { it.copy(appFont = font) }
        writeSetting { settingsRepository.setAppFont(font) }
    }

    fun setThemeInject(inject: Boolean) {
        _state.update { it.copy(themeInject = inject) }
        writeSetting { settingsRepository.setGuideThemeInject(inject) }
    }

    /**
     * The app's palette, the same one Settings writes. The panel is
     * themed from it, so the change shows here at once and in the app on return.
     */
    fun setThemePalette(palette: com.twinlore.app.data.ThemePalette) {
        _state.update { it.copy(themePalette = palette) }
        writeSetting { settingsRepository.setThemePalette(palette) }
    }

    fun setSmartFormatting(smart: Boolean) {
        _state.update { it.copy(smartFormatting = smart) }
        writeSetting { settingsRepository.setGuideSmartFormatting(smart) }
    }

    /**
     * Text size, which is per guide rather than global.
     *
     * Held in the panel's state and handed to the viewer, which owns the
     * WebView that applies it, and written to the bookmark so the app opens at
     * the same size. The write is the same column the app's own slider writes.
     */
    fun setTextZoom(zoom: Float) {
        _state.update { it.copy(textZoom = zoom) }
        val bookmark = (_state.value.guide as? FoldGuide.Web)?.tab
            ?: (_state.value.guide as? FoldGuide.Group)?.tab
            ?: return
        appScope.launch { data.saveTextZoom(bookmark.id, zoom) }
    }

    /**
     * Points the app at what the panel is showing, then runs [thenOpen].
     *
     * The app renders from `StateManager`, and a fold from the library leaves
     * it on the library: pressing App there would come back to the list rather
     * than to what was being read. Sequential rather than fire and forget, so
     * the state is set before the activity is asked forward.
     *
     */
    fun openAppOnCurrentGuide(thenOpen: () -> Unit) {
        val vault = vaultId
        if (vault == null) {
            handover.arm()
            thenOpen()
            return
        }
        // Say so before the activity comes forward: its resume runs the
        // open-last-played pass, which would otherwise decide this return was
        // an ordinary one and bounce to the library's showcase.
        handover.arm()
        val tab = tabForSurface(_state.value.surface)
        appScope.launch {
            runCatching { stateManager.setState(AppState.VaultSession(vault, tab)) }
            // On the main thread, always. [appScope] is Dispatchers.IO, and
            // what this callback does is take a window down and destroy a
            // lifecycle: "Method setCurrentState must be called on the main
            // thread", which killed the process the moment App was pressed.
            withContext(Dispatchers.Main) { thenOpen() }
        }
    }

    /**
     * Points the app at the library, then runs [thenOpen].
     *
     * The mirror of [openAppOnCurrentGuide]: that one keeps your place in a
     * game, this one is for being done with it. No vault to look up, but the
     * note still has to be left. The resume that follows runs the
     * open-last-played pass, and a pass that cannot tell a deliberate return
     * from an incidental one would reopen the very game somebody just asked to
     * walk away from.
     *
     * Sequential and back on the main thread for the same reasons as its
     * sibling: the state must be set before the activity is asked forward, and
     * [thenOpen] takes a window down and destroys a lifecycle.
     */
    fun openAppOnLibrary(thenOpen: () -> Unit) {
        handover.arm()
        appScope.launch {
            runCatching { stateManager.setState(AppState.Library) }
            withContext(Dispatchers.Main) { thenOpen() }
        }
    }

    /**
     * Points the app at its own settings screen, then runs [thenOpen].
     *
     * The panel's Settings surface carries the few settings worth changing
     * without putting the game down, and deliberately not the rest: this
     * window is a reader over something else, not a second copy of the app.
     * This is the row that admits it, and it has to leave the mode to do it,
     * because the app's settings screen is the app.
     *
     * Same shape as [openAppOnLibrary], and for the same reasons: the state is
     * set before the activity is asked forward, the handover is armed so the
     * resume's open-last-played pass does not read this as an ordinary return
     * and bounce to a game, and [thenOpen] runs on the main thread because it
     * takes a window down.
     */
    fun openAppOnSettings(thenOpen: () -> Unit) {
        handover.arm()
        appScope.launch {
            runCatching { stateManager.setState(AppState.Settings) }
            withContext(Dispatchers.Main) { thenOpen() }
        }
    }

    private companion object {
        /**
         * Distinguishes this panel's captures from the app's Image tab, which
         * listens for the same package-wide broadcast.
         */
        const val SCREENSHOT_TAG_PANEL = "fold-panel"

        /** Long enough for the consent sheet to be read and answered. */
        const val CAPTURE_TIMEOUT_MS = 30_000L

        /** The same half second the app's own map tab settles a pan for. */
        const val IMAGE_VIEW_WRITE_DELAY_MS = 500L
    }

    private fun resolveGuide(tab: SavedTab): FoldGuide =
        when (val type = GuideFileType.fromFileTypeColumn(tab.fileType)) {
            GuideFileType.HTML,
            GuideFileType.MHT,
            GuideFileType.EPUB,
            GuideFileType.TXT,
            -> FoldGuide.Web(tab)

            GuideFileType.PDF -> FoldGuide.Pdf(tab)

            // Most guides are groups, so this one is rendered rather than
            // named. The app's group viewer resolves its own chapters.
            GuideFileType.GROUP -> FoldGuide.Group(tab)

            // These have their own whole screen with its own navigation, and
            // HTMLZIP belongs here rather than with the web types: the app
            // routes it to the code-wheel viewer, not a WebView.
            GuideFileType.WHEEL,
            GuideFileType.HTMLZIP,
            GuideFileType.LINK,
            -> FoldGuide.Unsupported(type.displayLabel)
        }
}
