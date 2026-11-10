package com.twinlore.app.journey.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twinlore.app.data.retroachievements.RetroAchievementsCredentialsRepository
import com.twinlore.app.journey.data.JourneyDataSource
import com.twinlore.app.journey.data.PlayerFetchResult
import com.twinlore.app.journey.data.RaJourneyException
import com.twinlore.app.journey.domain.BoardAchievements
import com.twinlore.app.journey.domain.ChartLayout
import com.twinlore.app.journey.domain.IconBudget
import com.twinlore.app.journey.domain.JourneyBuilder
import com.twinlore.app.journey.domain.LayoutEngine
import com.twinlore.app.journey.domain.model.BoardAchievement
import com.twinlore.app.journey.domain.model.CompletionRow
import com.twinlore.app.journey.domain.model.JourneyFrame
import com.twinlore.app.journey.domain.model.JourneyMode
import com.twinlore.app.journey.domain.model.PlayerJourney
import com.twinlore.app.journey.domain.pluralize
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Labels speak in months, not day counts ("Last month", not "Last 30 days") - owner request,
// matching the axis span label's own rounded units. The day values and enum names are unchanged:
// the names are what the persisted preference stores.
enum class SpanPreset(val days: Int, val label: String) {
    D30(30, "Last month"), D90(90, "Last 3 months"), M6(183, "Last 6 months"),
    Y1(365, "Last year"), ALL(0, "All time"), CUSTOM(-1, "Custom range")
}
data class JourneyControls(
    val mode: JourneyMode = JourneyMode.SPAN,
    val gameIdText: String = "",
    /**
     * Display label for [gameIdText]. The picker is the only thing that ever sets the two, and it
     * sets them together.
     *
     * A vault entry does not come through here at all any more (see
     * [JourneyViewModel.lockedGameId]): it used to overwrite [gameIdText] with the vault's game and
     * leave this title alone, so the library panel came back afterwards reading "Sonic" over the
     * vault's id and charted a game the user never picked on that screen.
     */
    val selectedGameTitle: String? = null,
    val spanPreset: SpanPreset = SpanPreset.D30,
    val customFromDaysAgo: Int = 365, val customToDaysAgo: Int = 0,
    val speed: Float = 1f,
    /**
     * Whether the player follows the runner. Null until the user says: the player then starts on
     * [com.twinlore.app.journey.ui.chart.FollowCamera.defaultOn]'s answer for the journey it is
     * about to replay, which is the right default for a long or dense one and the wrong one to
     * remember once someone has chosen for themselves.
     */
    val followMode: Boolean? = null,
)

/**
 * The one closing card the end state shows: where this journey leaves the player standing.
 *
 * Resolved here rather than in the player because which rank is the right one is a question about
 * what was loaded (a single game, a preset span, a hand-picked range), and the player only knows
 * about the chart.
 */
data class RankChip(val title: String, val value: String)

/**
 * What the chart on screen was actually loaded for, as opposed to what the panel's controls
 * currently read.
 *
 * The two part company the moment someone changes a control without pressing Load, and the screen
 * has one question that turns on it: whether the loaded journey is still the one the controls
 * describe, and so whether "Watch again" can re-enter the player instead of refetching everything.
 * The view settings (speed, follow) are deliberately not here - they change how the journey is
 * played, never what is in it.
 */
data class JourneyContext(
    val mode: JourneyMode = JourneyMode.SPAN,
    val gameId: Int? = null,
    val spanPreset: SpanPreset = SpanPreset.D30,
    val customFromDaysAgo: Int = 365,
    val customToDaysAgo: Int = 0,
) {
    /** Whether [controls] still describe this chart, i.e. whether Load would fetch the same thing. */
    fun matches(controls: JourneyControls): Boolean = when {
        mode != controls.mode -> false
        mode == JourneyMode.GAME -> gameId != null && gameId == controls.gameIdText.trim().toIntOrNull()
        spanPreset != controls.spanPreset -> false
        spanPreset == SpanPreset.CUSTOM ->
            customFromDaysAgo == controls.customFromDaysAgo && customToDaysAgo == controls.customToDaysAgo
        else -> true
    }
}

/**
 * Why the journey cannot run yet. Both are one setting away from a working screen, and they are
 * different settings, so the locked card names the one that is actually missing.
 *
 * They became two the moment the API key started persisting on its own: a key can now be stored
 * with no account configured, and such a user used to clear the gate (which only asked about the
 * key) and land on a raw "No RetroAchievements account configured" error from the data layer.
 */
enum class LockedReason {
    /** No API key of the user's own: the screen is too API-hungry to run on the bundled one. */
    NO_API_KEY,

    /** A key, but no RetroAchievements username: there is no history to chart. */
    NO_ACCOUNT,
}

sealed interface JourneyUiState {
    data class Locked(val reason: LockedReason) : JourneyUiState
    data object Idle : JourneyUiState
    data class Loading(val label: String, val done: Int, val total: Int) : JourneyUiState
    data class Ready(
        val frame: JourneyFrame,
        /** The chart the player replays: widened for a dense journey, explored by pan and follow. */
        val layout: ChartLayout,
        /**
         * The same chart on one screenful ([LayoutEngine.W] wide), for the two places that cannot
         * pan: the summary's still preview and the exported video. Identical to [layout] whenever
         * the journey did not earn any extra width.
         */
        val compactLayout: ChartLayout,
        /**
         * How many of this journey's games could not be fetched. A partial failure still renders a
         * chart, so this is the only place the gap is admitted to: the player says so once per
         * load and offers a retry. Zero is the ordinary case.
         */
        val failedCount: Int,
        val rivalErrors: List<String>,                    // rival names whose fetch failed
        /**
         * Whether any of [rivalErrors] failed for a reason a second attempt could get past.
         *
         * The notice used to advise "Remove and re-add to retry" for every failure alike, which is
         * an instruction that costs a trip and fixes nothing when the API refused the request
         * outright. True is the safe default: it is what an unmodelled failure gets.
         */
        val rivalErrorsRetryable: Boolean = true,
        /**
         * Where the player stands, for the one card the end state shows when the journey finishes:
         * the game's leaderboard in single-game mode, the site-wide rank over a preset span, and
         * nothing at all over a hand-picked range (no population it would be a rank in).
         *
         * Null whenever the number did not arrive: it is a flourish on the end state, never a
         * reason for a load to fail.
         */
        val rank: RankChip? = null,
        /**
         * Where each player on a compare chart stands, keyed by player name: on that game's
         * leaderboard in single-game mode, and on the site over a span (a window of time has no
         * leaderboard of its own). Missing an entry for anyone whose rank did not arrive - the
         * compare row then draws their line without it - and empty on a chart with nobody to
         * compare against.
         */
        val playerRanks: Map<String, Int> = emptyMap(),
        /**
         * What this chart was loaded for. The configure panel compares it with the live controls to
         * decide whether the loaded journey can simply be watched again.
         */
        val context: JourneyContext = JourneyContext(),
        /**
         * Every badge the journey's game has, for the grid the player can open over the chart.
         * Empty on a span, which has no single achievement list. See [BoardAchievements].
         */
        val gameAchievements: List<BoardAchievement> = emptyList(),
    ) : JourneyUiState
    data class Empty(val reason: String) : JourneyUiState
    data class Error(val message: String, val retryable: Boolean) : JourneyUiState
}

/**
 * Achievement Journey screen state. `load()` is the only function that touches the network;
 * every toggle recomputes purely from the cached [PlayerFetchResult]s captured at the last
 * `load()`, so flipping them back and forth never re-fetches.
 *
 * The rebuild itself is not cheap (it runs the layout engine's relaxation pass), so it happens on
 * [computeDispatcher] and only the finished state is written back on the caller's dispatcher.
 */
@HiltViewModel
class JourneyViewModel internal constructor(
    private val repository: JourneyDataSource,
    private val credentials: RetroAchievementsCredentialsRepository,
    private val computeDispatcher: CoroutineDispatcher,
    /**
     * Where the presentation choices live between visits. Null in the tests that predate them:
     * every one of these settings restyles the chart in place, so a ViewModel without a store
     * simply keeps them for the session the way they always were.
     */
    private val settings: com.twinlore.app.data.SettingsRepository? = null,
    /** Null in tests that predate the support prompt; a gate-less ViewModel never prompts. */
    private val supportGate: com.twinlore.app.ui.license.SupportPromptGate? = null,
) : ViewModel() {

    @Inject constructor(
        repository: JourneyDataSource,
        credentials: RetroAchievementsCredentialsRepository,
        settings: com.twinlore.app.data.SettingsRepository,
        supportGate: com.twinlore.app.ui.license.SupportPromptGate,
    ) : this(repository, credentials, Dispatchers.Default, settings, supportGate)

    /** Writes one of the presentation settings, if there is anywhere to write it. */
    private fun persist(write: suspend com.twinlore.app.data.SettingsRepository.() -> Unit) {
        val store = settings ?: return
        viewModelScope.launch { runCatching { store.write() } }
    }

    private val _controls = MutableStateFlow(JourneyControls())
    val controls: StateFlow<JourneyControls> = _controls.asStateFlow()

    private val _uiState = MutableStateFlow<JourneyUiState>(JourneyUiState.Idle)
    val uiState: StateFlow<JourneyUiState> = _uiState.asStateFlow()

    private val _userName = MutableStateFlow("")
    val userName: StateFlow<String> = _userName.asStateFlow()

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    // Support prompt, fired by finished exports (the journey's value events).
    private val _supportPrompt =
        MutableStateFlow<com.twinlore.app.ui.license.SupportPromptGate.Prompt?>(null)
    val supportPrompt: StateFlow<com.twinlore.app.ui.license.SupportPromptGate.Prompt?> =
        _supportPrompt.asStateFlow()

    /** Called once per successfully finished export (see JourneyExportState). */
    fun onExportSucceeded() {
        val gate = supportGate ?: return
        viewModelScope.launch {
            // The prompt is cosmetic; a DataStore IOException here must never
            // crash the export flow.
            runCatching { gate.onExportCompleted() }.getOrNull()?.let { _supportPrompt.value = it }
        }
    }

    fun dismissSupportPrompt() {
        _supportPrompt.value = null
    }

    private val _playedGames = MutableStateFlow<List<CompletionRow>>(emptyList())
    val playedGames: StateFlow<List<CompletionRow>> = _playedGames.asStateFlow()

    private val _playedGamesError = MutableStateFlow(false)
    val playedGamesError: StateFlow<Boolean> = _playedGamesError.asStateFlow()

    /**
     * The compare roster the user has typed, in insertion order: a *configure* control, like the
     * mode and the span.
     *
     * It used to be read off the loaded chart (`Ready.frame.rivals`), which is why adding a rival
     * had to fetch one: there was nowhere to put a name that had no curve yet. So the panel gated
     * the field behind "Load your journey first, then add rivals", every add cost its own round
     * trip, and the roster could not be assembled before pressing Load once. Now the names go here
     * the moment they are typed and [load] fetches the lot in one pass.
     */
    private val _roster = MutableStateFlow<List<String>>(emptyList())
    val roster: StateFlow<List<String>> = _roster.asStateFlow()

    /**
     * Rivals this account has compared against before, offered by the panel as recents so a
     * returning user picks a name instead of retyping it. Read off the persisted cache, refreshed
     * when the screen opens and whenever a load has added to it.
     */
    private val _knownRivals = MutableStateFlow<List<String>>(emptyList())
    val knownRivals: StateFlow<List<String>> = _knownRivals.asStateFlow()

    private var knownRivalsJob: Job? = null

    /** Drops a rival from the recents, cached data and all. See [JourneyDataSource.forgetRival]. */
    fun forgetRival(name: String) {
        viewModelScope.launch {
            runCatching { repository.forgetRival(name) }
            refreshKnownRivals()
        }
    }

    private fun refreshKnownRivals() {
        // Best effort, and tracked like every other coroutine here so leaving the screen ends it:
        // recents are a convenience, and a cache read that fails must never be the reason a
        // journey does not load.
        knownRivalsJob?.cancel()
        knownRivalsJob = viewModelScope.launch {
            runCatching { _knownRivals.value = repository.knownRivals() }
        }
    }

    /**
     * Which of [JourneyPalette.RIVAL_CHOICES] the rival's curve and every mark that follows it
     * (badges' owner tint, the hardcore ring, the compare bar's line) is drawn in. A preference
     * rather than configure state: changing it restyles the loaded chart in place, requires no
     * Load, and deliberately survives [start]'s reset the way the playback speed does.
     */
    // Plain red by default, paired with the player's plain blue.
    // Index 0 since the palette moved to spectrum order.
    private val _rivalColorIndex = MutableStateFlow(0)
    val rivalColorIndex: StateFlow<Int> = _rivalColorIndex.asStateFlow()

    fun setRivalColor(index: Int) {
        val picked = index.coerceIn(0, JourneyPalette.RIVAL_CHOICES.lastIndex)
        _rivalColorIndex.value = picked
        persist { setJourneyRivalColor(picked) }
    }

    /**
     * The signed-in player's own colour on the chart, as an index into
     * [JourneyPalette.RIVAL_CHOICES], or -1 for the theme accent (the default). Same shape and
     * same lifetime as [rivalColorIndex].
     */
    // Plain blue by default: -1 (the theme accent) made the
    // chart's colour follow whatever the library happened to be showing. The accent stays a
    // choice in the picker; it is just not the default any more.
    private val _playerColorIndex = MutableStateFlow(2)
    val playerColorIndex: StateFlow<Int> = _playerColorIndex.asStateFlow()

    fun setPlayerColor(index: Int) {
        // No -1 any more: the theme-accent swatch is retired from the picker.
        val picked = index.coerceIn(0, JourneyPalette.RIVAL_CHOICES.lastIndex)
        _playerColorIndex.value = picked
        persist { setJourneyPlayerColor(picked) }
    }

    /**
     * The ambience drawn between the backdrop art and the chart, straight from the ambience
     * catalogue. A presentation preference like
     * the rival colour: it survives [start]'s reset and is never persisted.
     */
    private val _playerAmbience = MutableStateFlow(com.twinlore.app.data.ambience.AmbienceAnimation.NONE)
    val playerAmbience: StateFlow<com.twinlore.app.data.ambience.AmbienceAnimation> = _playerAmbience.asStateFlow()

    fun setPlayerAmbience(animation: com.twinlore.app.data.ambience.AmbienceAnimation) {
        _playerAmbience.value = animation
        persist { setJourneyAmbience(animation.name) }
    }

    /**
     * Which RetroAchievements art a shared video or picture rests on; see [ExportBackground].
     * A presentation preference like the ambience: survives [start]'s reset, never persisted.
     */
    private val _exportBackground = MutableStateFlow(ExportBackground.TITLE)
    val exportBackground: StateFlow<ExportBackground> = _exportBackground.asStateFlow()

    fun setExportBackground(source: ExportBackground) {
        _exportBackground.value = source
        persist { setJourneyBackground(source.name) }
    }

    /**
     * How strong the dimmed backdrop art reads behind the chart, as a percent - the stage's art
     * alpha and the export's follow this exact value (see [com.twinlore.app.journey.ui
     * .JourneyBackdrop] and `JourneyExportRequest`). A presentation preference like the background
     * source it sits beside: survives [start]'s reset, restored from the store below.
     */
    private val _backdropDim = MutableStateFlow(BACKDROP_DEFAULT_ALPHA)
    val backdropDim: StateFlow<Int> = _backdropDim.asStateFlow()

    fun setBackdropDim(value: Int) {
        val coerced = value.coerceIn(BACKDROP_MIN_ALPHA, BACKDROP_MAX_ALPHA)
        _backdropDim.value = coerced
        persist { setJourneyBackdropDim(coerced) }
    }

    /**
     * The slider's live drag: moves the stage's backdrop immediately, but writes nothing.
     * The DataStore write stays on the release edge ([setBackdropDim]), so scrubbing the
     * slider does not commit a preference on every frame.
     */
    fun previewBackdropDim(value: Int) {
        _backdropDim.value = value.coerceIn(BACKDROP_MIN_ALPHA, BACKDROP_MAX_ALPHA)
    }

    /**
     * Whether a player's face carries a small sign - asleep, at work, holding a baby - while their
     * line is flat. Decoration, so it is a setting; see [com.twinlore.app.journey.domain.IdleSigns].
     */
    private val _idleSigns = MutableStateFlow(true)
    val idleSigns: StateFlow<Boolean> = _idleSigns.asStateFlow()

    fun setIdleSigns(on: Boolean) {
        _idleSigns.value = on
        persist { setJourneyIdleSigns(on) }
    }

    /**
     * Restores the standing presentation choices - speed, follow, the two colours, the ambience,
     * the backdrop art, the idle signs - from the settings store.
     *
     * These outlive a session the way the rest of the app's look-and-feel does (user request), and
     * none of them can invalidate a loaded journey, so nothing here triggers a fetch.
     *
     * Declared AFTER every flow it writes, and the `init` that runs it is the last thing in the
     * class, because [viewModelScope] is `Main.immediate`: constructed on the main thread, a
     * `launch` here runs its body inline up to the first suspension point, i.e. still inside the
     * constructor. An `init` placed above these declarations therefore read backing fields that
     * had not been assigned yet - it threw on the first line, `runCatching` swallowed it, and
     * every one of these settings was written on change and never read back.
     */
    private fun restorePreferences() {
        val store = settings ?: return
        viewModelScope.launch {
            // Per read, so one unreadable preference cannot cost the others theirs.
            suspend fun <T> read(of: suspend () -> T): T? = runCatching { of() }.getOrNull()

            read { store.journeySpeed.first() }
                // Only a speed the player offers. A number written by another build - or a future
                // one with a wider set - would otherwise be restored verbatim into a control that
                // has no button for it.
                ?.takeIf { it in SPEEDS }
                ?.let { _controls.value = _controls.value.copy(speed = it) }
            read { store.journeyFollowMode.first() }?.let { follow ->
                _controls.value = _controls.value.copy(followMode = follow)
            }
            read { store.journeyPlayerColor.first() }
                // -1 was the retired theme-accent option; installs that had it move to the
                // blue default rather than restoring a swatch the picker no longer offers.
                ?.let { _playerColorIndex.value = if (it < 0) 2 else it.coerceIn(0, JourneyPalette.RIVAL_CHOICES.lastIndex) }
            read { store.journeyRivalColor.first() }
                ?.let { _rivalColorIndex.value = it.coerceIn(0, JourneyPalette.RIVAL_CHOICES.lastIndex) }
            read { store.journeyAmbience.first() }?.let { name ->
                com.twinlore.app.data.ambience.AmbienceAnimation.entries
                    .firstOrNull { it.name == name }
                    ?.let { _playerAmbience.value = it }
            }
            read { store.journeyBackground.first() }?.let { name ->
                ExportBackground.entries.firstOrNull { it.name == name }
                    ?.let { _exportBackground.value = it }
            }
            read { store.journeyBackdropDim.first() }
                ?.let { _backdropDim.value = it.coerceIn(BACKDROP_MIN_ALPHA, BACKDROP_MAX_ALPHA) }
            read { store.journeyIdleSigns.first() }?.let { _idleSigns.value = it }
        }
    }

    // Guards against launching a second fetch while one is already in flight; repeat opens once a
    // fetch has landed are cheap on their own, because the repository's own completion cache TTL
    // (fetchOwn's exact path) answers them without a network call.
    private var playedGamesJob: Job? = null

    // Every other coroutine this ViewModel owns, so that start() can genuinely end a visit. They
    // were bare viewModelScope.launch calls held by nothing: work fired just before backing out
    // landed minutes later and published Ready over whatever the new visit was showing - including
    // a locked screen.
    private var removeRivalJob: Job? = null
    private var startJob: Job? = null

    // Raw fetch results cached from the last load(); rebuild() reads ONLY these, never the network.
    private var ownResult: PlayerFetchResult? = null
    private val rivalNames = mutableListOf<String>()                 // roster, insertion order
    private val rivalResults = mutableMapOf<String, PlayerFetchResult>()
    // Errored rivals, and whether the failure was one a second attempt could get past. The notice
    // the panel draws is the only reader of the flag: a wall and a wait deserve different advice.
    private val rivalErrorNames = mutableMapOf<String, Boolean>()
    private var gameRank: Int? = null
    private var siteRank: Int? = null
    // Per-player game-leaderboard ranks, for the single-game compare row. Keyed by player name,
    // the main player included, and only ever populated in GAME mode.
    private val playerRanks = mutableMapOf<String, Int>()

    // Span bounds + context actually used for the cached fetch above; toggles reuse these.
    private var fetchedSpanFrom = 0L
    private var fetchedSpanTo = 0L
    // The same window as the user asked for it, for the rival cache's key: see spanKeyOf.
    private var fetchedSpanKey = spanKeyOf(JourneyControls())
    private var fetchedMode = JourneyMode.SPAN
    private var fetchedGameId: Int? = null
    // Which window the chart on screen is of, as opposed to what the dropdown currently reads.
    // The end state's card depends on it (a hand-picked range gets none), and the two part company
    // the moment someone changes the preset without pressing Load.
    private var fetchedSpanPreset = SpanPreset.D90
    // The same idea, as one value the Ready state carries: what the chart on screen was loaded for.
    private var fetchedContext = JourneyContext()

    // Guards against overlapping load()/retry() calls: a newer load cancels any in-flight one
    // before it can write stale ownResult/fetched*/uiState over a fresher request's results.
    private var loadJob: Job? = null

    /**
     * The game a vault entry is scoped to, or null for the library entry.
     *
     * Held here rather than pushed into [JourneyControls] because the locked screen composes no
     * controls at all: writing the vault's game id into the panel's own state left the library
     * journey, on the next visit, holding a game id the user never picked on that screen under a
     * picker button still labelled with whatever they picked before. The locked path reads this and
     * the library path reads the controls; neither can corrupt the other.
     */
    private var lockedGameId: Int? = null

    /**
     * The RetroAchievements name the last visit resolved, or null before any visit has.
     *
     * A visit is not the granularity an account switch happens at. This ViewModel is resolved
     * against the activity, and the account is changed in Settings without the activity being
     * recreated, so [resetSession] - which drops what one *visit* accumulated - is not enough on
     * its own: the picker's played-games list and the game it had selected are account-derived and
     * deliberately outlive a visit. Left alone, account B returned to the panel reading account A's
     * chosen game over account A's id, and opened the picker on account A's library.
     */
    private var lastResolvedUser: String? = null

    /**
     * Begins a visit to the screen. Called once per entry, from either doorway.
     *
     * Deliberately not a coroutine as a whole. This ViewModel is resolved against the activity, so
     * every field on it outlives the screen, and the reset below has to have happened *before* the
     * composition reads [uiState] - otherwise the previous visit's `Ready` is what the first frame
     * composes, which opens the full-screen player and starts autoplaying somebody else's journey
     * (and, when the credentials have since gone, shows it to a user who can no longer load it).
     * Only the credential read and the auto-load need to suspend.
     *
     * The reset is total: a visit starts with no journey, no roster, no ranks and no notice. The
     * alternative - carrying the last journey over - is only honest if the screen shows it, and the
     * panel showed neither the chart nor the roster it was still enforcing a cap against.
     */
    fun start(lockedGameId: Int?) {
        cancelVisitWork()
        this.lockedGameId = lockedGameId
        resetSession()
        _uiState.value = if (lockedGameId != null) {
            // A vault entry loads itself, so it has no configure panel to sit on: opening on
            // Loading keeps the empty locked panel out of the frames before load() speaks.
            //
            // One step, not done: a vault entry is a single game with no roster behind it, and
            // [SINGLE_GAME_TOTAL] is what keeps the loading screen off its indeterminate bar.
            JourneyUiState.Loading(PREPARING_LABEL, 0, SINGLE_GAME_TOTAL)
        } else {
            JourneyUiState.Idle
        }
        startJob = viewModelScope.launch {
            // The name is resolved before the gate rather than alongside it: "is there an account"
            // is one of the two questions the gate asks, and the answer is the same read.
            resolveUserName()
            refreshKnownRivals()
            dropStateOfDepartedAccount()
            lockedReason()?.let {
                _uiState.value = JourneyUiState.Locked(it)
                return@launch
            }
            if (lockedGameId != null) load()
        }
    }

    /**
     * Ends the previous visit's work. Every coroutine this ViewModel owns, not just the load: an
     * add-rival or a picker fetch that outlives the entry switch lands on the next visit's state.
     */
    private fun cancelVisitWork() {
        startJob?.cancel()
        loadJob?.cancel()
        removeRivalJob?.cancel()
        playedGamesJob?.cancel()
    }

    /**
     * Drops everything the previous visit accumulated, so the configure panel a visit opens on is
     * telling the truth: nothing loaded, nobody in the roster, nothing to say.
     */
    private fun resetSession() {
        clearRivalState()
        ownResult = null
        gameRank = null
        siteRank = null
        fetchedSpanFrom = 0L
        fetchedSpanTo = 0L
        fetchedSpanKey = spanKeyOf(JourneyControls())
        fetchedMode = JourneyMode.SPAN
        fetchedGameId = null
        fetchedSpanPreset = SpanPreset.D90
        fetchedContext = JourneyContext()
        _notice.value = null
        // A failed picker fetch is a fact about one attempt, not about the account: left standing,
        // it made the next visit's picker open on "Could not load your games" before it had tried.
        _playedGamesError.value = false
        // The repository's session caches are keyed on neither the account nor the wall clock in
        // any way that survives a visit; answering a new one from them is how one account's ranks
        // end up on another's chart.
        repository.clearSessionCache()
    }

    /**
     * Drops the state that belongs to the account, rather than to the visit, when the name this
     * visit resolved is not the one the last visit resolved.
     *
     * Two things are in neither [resetSession]'s list nor the repository's cache wipe, because both
     * are meant to survive a visit: the picker's played-games list, and the game the panel has
     * selected (its id and the title on the button). Both are one account's history, and neither is
     * corrected by anything short of a fetch - a failed picker load keeps whatever list it already
     * had - so account B was shown account A's games until one landed, and Load charted account A's
     * chosen game under account B's name.
     *
     * The span preset stays. It is a question about a window of time, which is the user's choice
     * rather than the account's, and it means the same thing whoever is signed in.
     */
    private fun dropStateOfDepartedAccount() {
        val resolved = _userName.value
        val previous = lastResolvedUser
        lastResolvedUser = resolved
        // Case-folded, matching the repository's own account key: "Aria" and "aria" are one account.
        if (previous == null || previous.equals(resolved, ignoreCase = true)) return
        _playedGames.value = emptyList()
        _playedGamesError.value = false
        _controls.value = _controls.value.copy(gameIdText = "", selectedGameTitle = null)
    }

    /**
     * What is missing before this screen can run, or null when nothing is.
     *
     * Both halves are asked for: the key alone used to be the whole gate, and once the key started
     * persisting independently of the account, a user who had entered one without a username walked
     * through it and straight into the data layer's own "No RetroAchievements account configured"
     * failure - a retryable error screen for something no amount of retrying fixes. The vault's
     * Achievements tab makes that reachable without ever visiting the library journey.
     */
    private fun lockedReason(): LockedReason? = when {
        !credentials.hasCustomApiKey() -> LockedReason.NO_API_KEY
        _userName.value.isBlank() -> LockedReason.NO_ACCOUNT
        else -> null
    }

    /** Drops the whole compare roster: names, cached results, error flags and per-player ranks. */
    private fun clearRivalState() {
        rivalNames.clear()
        _roster.value = emptyList()
        clearRivalData()
    }

    /**
     * Drops what was *fetched* for the roster, leaving the names the user typed standing.
     *
     * The two are different lifetimes now. The names are configure state - the user put them there
     * and only the user (or a new visit) takes them away - while the results, the error flags and
     * the ranks all belong to one load of one window, and a load that failed or is starting over
     * has none of them.
     */
    private fun clearRivalData() {
        rivalResults.clear()
        rivalErrorNames.clear()
        playerRanks.clear()
    }

    fun load() {
        // A newer load always wins: cancel any in-flight attempt before it can write stale
        // results over this one. Cancellation is cooperative — the suspending repository calls
        // are cancellable, and ensureActive() closes the gaps between them and the pure writes.
        //
        // The roster's own coroutine goes with it: a rebuild for the chart this load is replacing
        // has nothing left to say, and letting it land writes the old chart over the new one's
        // loading screen.
        loadJob?.cancel()
        removeRivalJob?.cancel()
        loadJob = viewModelScope.launch {
            _notice.value = null
            setLoading(PREPARING_LABEL, 0, 0)
            resolveUserName()
            ensureActive()

            val c = effectiveControls()
            val gameId = when (c.mode) {
                JourneyMode.GAME -> c.gameIdText.trim().toIntOrNull()
                JourneyMode.SPAN -> null
            }
            if (c.mode == JourneyMode.GAME && gameId == null) {
                // Names the control that is actually on screen: the manual game-id field went when
                // the picker landed, so "enter a valid game id" asked for something this panel has
                // no way to enter.
                _uiState.value = JourneyUiState.Error(NO_GAME_PICKED, retryable = false)
                return@launch
            }
            // A single game reports no per-game progress (there is one game, and the repository
            // never calls back for it), so without this the loading screen has no total and falls
            // to an indeterminate bar - a Material indicator that invalidates draw every vsync for
            // the whole fetch, on a screen with nothing else moving on it. One step, not yet done,
            // is both true and static, and the game the panel picked is what that step is named
            // after when there is a name for it (a vault entry has no panel and so no title).
            //
            // The bar then restarts per rival, each pass named after whose it is: see the note on
            // [SINGLE_GAME_TOTAL] for why the denominator cannot be cumulative.
            val ownGameLabel = c.selectedGameTitle?.takeIf { it.isNotBlank() }
                ?.let { "Fetching $it" } ?: PREPARING_LABEL
            if (c.mode == JourneyMode.GAME) setLoading(ownGameLabel, 0, SINGLE_GAME_TOTAL)

            val (spanFrom, spanTo) = computeSpanBounds(c)
            // Every progress callback is gated on this load's own job. The repository calls them
            // from the IO dispatcher, which is not the thread that cancels anything, so a callback
            // already in flight when someone leaves the screen lands afterwards - and setLoading
            // writes uiState unconditionally, which is a cancelled load flipping the visit that
            // replaced it back to a Loading screen it can never leave.
            val job = coroutineContext.job
            val onProgress: (String, Int, Int) -> Unit = { label, done, total ->
                if (job.isActive) setLoading("Fetching $done of $total: $label", done, total)
            }

            try {
                val result = repository.fetchOwn(c.mode, gameId, spanFrom, spanTo, onProgress)
                ensureActive()
                // The one step a single-game load has, now done: without this the bar sat at zero
                // for the whole fetch and then jumped straight to the player.
                if (c.mode == JourneyMode.GAME) {
                    setLoading(ownGameLabel, SINGLE_GAME_TOTAL, SINGLE_GAME_TOTAL)
                }
                ownResult = result
                fetchedSpanFrom = spanFrom
                fetchedSpanTo = spanTo
                fetchedSpanKey = spanKeyOf(c)
                fetchedMode = c.mode
                fetchedGameId = gameId
                fetchedSpanPreset = c.spanPreset
                fetchedContext = JourneyContext(
                    mode = c.mode,
                    gameId = gameId,
                    spanPreset = c.spanPreset,
                    customFromDaysAgo = c.customFromDaysAgo,
                    customToDaysAgo = c.customToDaysAgo,
                )

                // The closing card's rank. Best-effort by construction (the repository answers null
                // rather than throwing), and asked for here rather than at the end of playback so
                // the payoff is already in hand when the journey reaches it. A single game asks the
                // game's leaderboard; a preset span asks the site. A hand-picked range asks neither
                // - no ranking exists for "what this player did between these two dates".
                playerRanks.clear()

                // One summary call for the signed-in player, in every mode, before anything is
                // keyed on the name the settings screen holds. It answers two things at once: how
                // RetroAchievements spells this player (which is what makes their own avatar
                // resolve - the media host's UserPic path is case sensitive and a lowercase
                // username left the runner drawing its own initial, on spans especially, where
                // nothing else this load fetches echoes a name back) and where they stand on the
                // site (which is the rank a compare span's rows show).
                val ownSummary = repository.playerSummary(_userName.value)
                ensureActive()
                adoptOwnCanonicalName()

                gameRank = if (c.mode == JourneyMode.GAME && gameId != null) {
                    bestEffortRank { repository.fetchGameRank(gameId) }.also { rank ->
                        // The same number the closing card uses, kept under the player's name as
                        // well: a compare row puts every player's rank in one place, and the main
                        // player is one of them.
                        if (rank != null) playerRanks[_userName.value] = rank
                    }
                } else {
                    null
                }
                siteRank = if (c.mode == JourneyMode.SPAN && c.spanPreset != SpanPreset.CUSTOM) {
                    // The profile's own cached number first (it usually costs nothing), and the
                    // summary just fetched as the fallback rather than a second request.
                    bestEffortRank { repository.siteRank() } ?: ownSummary?.siteRank
                } else {
                    null
                }
                // On a compare span the main player's row needs a rank too, and the site rank is
                // the only one that exists for a window of time. Taken from the summary rather than
                // from [siteRank] so a hand-picked range - which has no closing card - still puts
                // every player's row on the same footing.
                if (c.mode == JourneyMode.SPAN && rivalNames.isNotEmpty()) {
                    ownSummary?.siteRank?.let { playerRanks[_userName.value] = it }
                }
                ensureActive()

                // Every rival on the roster, fetched for this window; a failure keeps the roster
                // entry and flags it. This is the only place rival data is ever fetched: adding a
                // name to the roster is a pure edit (see [addRival]).
                for (name in rivalNames.toList()) {
                    ensureActive()
                    // Who is being fetched, before the first per-game step for them arrives - and
                    // the whole of what a GAME-mode rival fetch has to say, since one game reports
                    // no per-game progress at all. A rival's steps used to go through the same
                    // callback the player's own did, so three rivals' fetches read as "Fetching 4
                    // of 9: Some Game" with nothing anywhere saying whose games those were.
                    setLoading(rivalStepLabel(name), 0, rivalStepTotal(c.mode))
                    val onRivalProgress: (String, Int, Int) -> Unit = { label, done, total ->
                        if (job.isActive) setLoading(rivalStepLabel(name, label, done, total), done, total)
                    }
                    try {
                        val rr = repository.fetchRival(name, c.mode, gameId, spanFrom, spanTo, spanKeyOf(c), onRivalProgress)
                        // Before the write, not after: a fetch that returned because the visit was
                        // cancelled must not put its rival into the session that replaced it.
                        ensureActive()
                        rivalResults[name] = rr
                        rivalErrorNames.remove(name)
                        // This rival is now in the persisted cache, so the panel's recents can
                        // offer them next time without the name being retyped.
                        refreshKnownRivals()
                        // A single game reports no per-game steps, so the bar would sit at zero for
                        // this rival and then jump to the next one's. One step, now done.
                        if (c.mode == JourneyMode.GAME) {
                            setLoading(rivalStepLabel(name), SINGLE_GAME_TOTAL, SINGLE_GAME_TOTAL)
                        }
                        // Where this player stands, and how their name is spelled. A single game
                        // has a leaderboard, so its rank is the game's; a span has none, so the
                        // rank on a compare row is the site's and comes off the same summary call
                        // that carries the spelling. Both are outside this rival's failure story on
                        // purpose - their data has already arrived, and a rank or a spelling that
                        // did not is a missing column, not a rival that failed to load.
                        if (c.mode == JourneyMode.GAME && gameId != null) {
                            bestEffortRank { repository.fetchGameRank(gameId, name) }
                                ?.let { playerRanks[name] = it }
                        }
                        repository.playerSummary(name)?.let { summary ->
                            if (c.mode == JourneyMode.SPAN) {
                                summary.siteRank?.let { playerRanks[name] = it }
                            }
                        }
                        ensureActive()
                        // Last, so everything keyed on the typed name has been written by the time
                        // it is re-keyed.
                        adoptCanonicalName(name)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (t: Throwable) {
                        // A cancellation does not always arrive as a CancellationException. A
                        // cancelled OkHttp call surfaces as an IOException, and [RaJourneyClient]
                        // models an IOException as an ordinary retryable RaJourneyException - which
                        // is indistinguishable here from a rival that genuinely failed. Asking the
                        // job settles it, so an abandoned visit cannot write an error flag into the
                        // session that replaced it. See the same guard on the outer catches below.
                        ensureActive()
                        rivalErrorNames[name] = retryableFailure(t)
                    }
                }
                ensureActive()
                rebuild()
                // Read off the chart that was just built rather than probed per rival while it was
                // being fetched. A second JourneyBuilder.buildPlayer per rival on the main
                // dispatcher was the cost of the old way, and it answered under the name the user
                // typed - which by this point may have been re-spelled, so the notice named a
                // player the roster no longer had.
                noticeEmptyRivals()
            } catch (e: RaJourneyException) {
                // Before anything is cleared or published: a cancelled request comes back from
                // OkHttp as an IOException, which the client turns into exactly this exception, so
                // a load abandoned by someone leaving the screen was liable to arrive here rather
                // than on the cancellation path below - and put an error screen over whatever the
                // visit that replaced it was showing.
                ensureActive()
                clearLoadedJourney()
                _uiState.value = if (e.retryable) {
                    JourneyUiState.Error(e.message ?: "Could not load data.", retryable = true)
                } else {
                    // A permanent refusal used to arrive as "RetroAchievements API error 404" next
                    // to a Try again button that could only fail again. Neither the code nor the
                    // button is any use to the reader.
                    JourneyUiState.Error(permanentErrorFor(e), retryable = false)
                }
            } catch (e: CancellationException) {
                // A newer load() cancelled this one; it owns the state now. Emphatically nothing is
                // cleared here: the results this would drop are the newer load's, not this one's.
                throw e
            } catch (t: Throwable) {
                // Anything the data layer did not model as an RaJourneyException (a Room failure,
                // a malformed cached payload) must still land as a retryable screen, not a crash.
                // Same guard, same reason: an unmodelled failure raised by a cancellation belongs
                // to a visit that is over.
                ensureActive()
                clearLoadedJourney()
                _uiState.value = JourneyUiState.Error(GENERIC_ERROR, retryable = true)
            }
        }
    }

    /**
     * Forgets the chart a rebuild could still be built from, and the ranks that went with it.
     *
     * Called when a load fails. The failed load has already taken the previous journey off the
     * screen, and there is no way back to it, so what these fields were still good for was one
     * thing: an `addRival` from the error panel published the *previous* journey as if it had just
     * been loaded, ranks and all, and threw the user into the player on it.
     *
     * What was FETCHED for the roster goes with it, and the typed names stay. They are the panel's
     * own state now - the user put them there and is about to press Retry - so dropping them made
     * every rival have to be retyped after a failure. What must not survive is a rival's results,
     * error flag or rank, all of which belong to the load that just failed.
     */
    private fun clearLoadedJourney() {
        ownResult = null
        gameRank = null
        siteRank = null
        clearRivalData()
        fetchedContext = JourneyContext()
    }

    fun retry() = load()

    /**
     * Puts a name on the compare roster. A pure edit: no network, no chart, no gate.
     *
     * Adding a rival used to fetch that rival on the spot, which is why it could only be done after
     * a load ("Load your journey first, then add rivals"), why each name cost its own round trip
     * with a spinner to sit through, and why an add left in flight by someone leaving the screen
     * was a coroutine racing a reset for the roster it wrote into. The roster is now assembled
     * first and [load] fetches every name on it in one pass, so all three go away together.
     *
     * The three refusals stay, because they are all answerable without asking the API anything: a
     * name already on the roster, the signed-in player's own name, and the cap for the mode. A name
     * that turns out not to exist is found out at load time and reported as an errored roster entry
     * the way a failed refetch always has been.
     */
    fun addRival(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return

        if (trimmed.equals(_userName.value, ignoreCase = true) ||
            rivalNames.any { it.equals(trimmed, ignoreCase = true) }
        ) {
            _notice.value = "$trimmed is already in this journey."
            return
        }
        if (rivalNames.size >= RIVAL_CAP) {
            _notice.value = capNotice()
            return
        }

        _notice.value = null
        rivalNames.add(trimmed)
        _roster.value = rivalNames.toList()
    }

    /**
     * Says so when a roster name loaded cleanly with nothing in the window.
     *
     * Read off the frame the rebuild just published, so it names players by the spelling the chart
     * uses and costs no second pass over anybody's unlocks. Not an error - the fetch was clean, the
     * player simply did not play here - so the chip stays without a failure flag; left unsaid, a
     * chip on the panel and no curve on the chart is a rival the user cannot account for.
     */
    private fun noticeEmptyRivals() {
        val frame = (_uiState.value as? JourneyUiState.Ready)?.frame ?: return
        val empty = frame.rivals.filter { !it.error && it.unlocks.isEmpty() }.map { it.name }
        if (empty.isNotEmpty()) _notice.value = emptyRivalNotice(empty, frame.mode)
    }

    /**
     * Re-spells a roster entry the way RetroAchievements spells it, when a response this load
     * already made has said so.
     *
     * The point is the avatar. `media.retroachievements.org/UserPic/{name}.png` is case sensitive
     * while every RA lookup is not, so a rival typed as "aria" charts perfectly and then shows the
     * initial-letter fallback disc where their face should be - on the curve end, on the roster
     * chip and on the compare row alike, since all three build the URL from the roster name. Taking
     * the canonical spelling fixes all three at once and costs no request: it rides on the
     * leaderboard lookup the compare row already makes.
     *
     * Only ever a change of case, never of identity: a response naming a different player is
     * ignored rather than quietly swapping who is on the chart.
     */
    private fun adoptCanonicalName(typed: String) {
        val canonical = repository.canonicalName(typed) ?: return
        if (canonical == typed || !canonical.equals(typed, ignoreCase = true)) return
        val index = rivalNames.indexOf(typed)
        if (index < 0) return
        rivalNames[index] = canonical
        _roster.value = rivalNames.toList()
        rivalResults.remove(typed)?.let { rivalResults[canonical] = it }
        rivalErrorNames.remove(typed)?.let { rivalErrorNames[canonical] = it }
        playerRanks.remove(typed)?.let { playerRanks[canonical] = it }
    }

    /**
     * The same, for the signed-in player. Their name comes from the settings screen rather than
     * from a roster field, and it reaches the media host by the identical case-sensitive path: a
     * username entered in lowercase left the runner - the one avatar the chart is about - drawing
     * its own initial instead of a face.
     */
    private fun adoptOwnCanonicalName() {
        val typed = _userName.value
        val canonical = repository.canonicalName(typed) ?: return
        if (canonical == typed || !canonical.equals(typed, ignoreCase = true)) return
        _userName.value = canonical
    }

    fun removeRival(name: String) {
        val trimmed = name.trim()
        val removed = rivalNames.removeAll { it.equals(trimmed, ignoreCase = true) }
        if (removed) {
            _roster.value = rivalNames.toList()
            rivalResults.keys.removeAll { it.equals(trimmed, ignoreCase = true) }
            rivalErrorNames.keys.removeAll { it.equals(trimmed, ignoreCase = true) }
            // The rank goes with the player, exactly as clearRivalState drops it: leaving it behind
            // let a name removed as "aria" and re-added as "Aria" be served the departed entry's
            // number until the refetch replaced it, and grew the map across add/remove cycles.
            playerRanks.keys.removeAll { it.equals(trimmed, ignoreCase = true) }
            // A rebuild for a roster two removals ago has nothing to say, and letting it finish is
            // how a name taken off the chart came back on it.
            removeRivalJob?.cancel()
            if (ownResult != null) removeRivalJob = viewModelScope.launch { rebuild() }
        }
    }

    /**
     * Switches what is charted, trimming the roster to what the new mode allows.
     *
     * The caps differ by mode (three on a single game, two over a span) and the roster is now
     * filled in before any load, so a full GAME roster survived a switch to SPAN and would have
     * been loaded one rival over the cap. Trimming here says so at the moment it happens, on the
     * panel showing the chips, rather than silently at load time.
     */
    fun setMode(mode: JourneyMode) {
        // The cap is one in every mode now, so a mode switch can never strand a roster over it
        // and the trim-and-tell machinery that used to live here is gone with the per-mode caps.
        _controls.value = _controls.value.copy(mode = mode)
    }

    /**
     * Lazily fetches the played-games list for the picker. Safe to call every time the picker
     * opens: a fetch already in flight is not duplicated, and once one has landed, repeat opens
     * within the repository's own completion cache TTL cost nothing extra there either.
     */
    fun loadPlayedGames() {
        if (playedGamesJob?.isActive == true) return
        playedGamesJob = viewModelScope.launch {
            try {
                val games = repository.playedGames()
                _playedGames.value = games
                _playedGamesError.value = false
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                // Silent fallback: whatever is already in _playedGames (possibly empty, possibly a
                // stale list from an earlier successful load) stays on screen; only the error flag
                // moves, so the picker can show a line when there is truly nothing to show.
                _playedGamesError.value = true
            }
        }
    }

    fun selectGame(gameId: Int, title: String) {
        _controls.value = _controls.value.copy(gameIdText = gameId.toString(), selectedGameTitle = title)
    }

    fun setSpanPreset(p: SpanPreset) {
        _controls.value = _controls.value.copy(spanPreset = p)
    }

    fun setCustomRange(fromDaysAgo: Int, toDaysAgo: Int) {
        _controls.value = _controls.value.copy(customFromDaysAgo = fromDaysAgo, customToDaysAgo = toDaysAgo)
    }

    fun setSpeed(s: Float) {
        _controls.value = _controls.value.copy(speed = s)
        persist { setJourneySpeed(s) }
    }

    /**
     * Pins the follow camera on or off for the rest of the session. Purely a view setting: it
     * changes nothing about the frame, so it never rebuilds and never fetches.
     */
    fun setFollowMode(on: Boolean) {
        _controls.value = _controls.value.copy(followMode = on)
        persist { setJourneyFollowMode(on) }
    }

    fun dismissNotice() {
        _notice.value = null
    }

    override fun onCleared() {
        super.onCleared()
        repository.clearSessionCache()
    }

    /**
     * Publishes a loading step, skipping one that says exactly what is already on screen.
     *
     * The loading screen draws a fixed heading and the label under it, so a label that repeats what
     * was already there is a line the reader sees twice. A single-game load reports no per-game
     * progress at all (there is one game, and the repository does not call back for it), so the
     * duplicate was the whole of what that screen said for as long as the fetch ran.
     */
    private fun setLoading(label: String, done: Int, total: Int) {
        val current = _uiState.value
        if (current is JourneyUiState.Loading &&
            current.label == label && current.done == done && current.total == total
        ) {
            return
        }
        _uiState.value = JourneyUiState.Loading(label, done, total)
    }

    private suspend fun resolveUserName() {
        _userName.value = credentials.getCredentials()?.username ?: ""
    }

    /**
     * [bestEffort] for something that answers a number: a rank that could not be had is null.
     *
     * The repository already promises not to throw here. This is the second belt on the load path,
     * where a rank sits between a fetch that succeeded and the state that publishes it: without it,
     * one unmodelled failure inside a rank lookup turns a journey that arrived into an error
     * screen, or a rival whose data is in hand into an errored roster entry.
     */
    private suspend fun <T> bestEffortRank(block: suspend () -> T?): T? =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            null
        }

    /**
     * Runs [block] for what it is worth, and lets a failure pass.
     *
     * For the work that happens *after* something has already been committed, where the failure of
     * a flourish must not be reported as the failure of the thing it decorates. Cancellation is
     * emphatically not swallowed: it is how a newer request ends an older one.
     */
    private suspend fun bestEffort(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            // Nothing to say: the caller has already published everything that did succeed.
        }
    }

    /**
     * What [load] charts: the panel's controls on the library path, and a vault entry's own fixed
     * request on the locked one.
     *
     * A locked entry reads none of the panel's state, because the panel is not on screen for it to
     * have been set on. The window is all time rather than the library's current preset: a vault
     * journey is "this game, start to finish", and taking whatever span the library visit happened
     * to leave behind made the same game chart differently from one entry to the next - or come
     * back empty, on a panel with no span control to widen.
     */
    private fun effectiveControls(): JourneyControls {
        val locked = lockedGameId ?: return _controls.value
        return JourneyControls(
            mode = JourneyMode.GAME,
            gameIdText = locked.toString(),
            spanPreset = SpanPreset.ALL,
        )
    }

    /**
     * The window as the user asked for it, as one string: the preset's name, or the custom range's
     * two day offsets.
     *
     * The rival cache is keyed on this rather than on the millis [computeSpanBounds] resolves.
     * Those come off the wall clock at each load, so "last 90 days" is a different pair of numbers
     * every time it is asked for, and a cache keyed on them never once answered a second Load - it
     * only accumulated a full set of game snapshots per rival per press.
     */
    private fun spanKeyOf(c: JourneyControls): String = when (c.spanPreset) {
        SpanPreset.CUSTOM -> "CUSTOM:${c.customFromDaysAgo}:${c.customToDaysAgo}"
        else -> c.spanPreset.name
    }

    private fun computeSpanBounds(c: JourneyControls): Pair<Long, Long> {
        val now = System.currentTimeMillis()
        return when (c.spanPreset) {
            SpanPreset.ALL -> 0L to now
            SpanPreset.CUSTOM -> {
                val fromMillis = now - c.customFromDaysAgo * DAY_MILLIS
                val toMillis = now - c.customToDaysAgo * DAY_MILLIS
                if (fromMillis <= toMillis) fromMillis to toMillis else toMillis to fromMillis
            }
            else -> (now - c.spanPreset.days * DAY_MILLIS) to now
        }
    }

    /**
     * Derives the frame + layout + facts from cached fetch results only, no network. The build
     * itself (layout relaxation is O(n^2) in unlocks) runs on [computeDispatcher]; only the
     * finished state is published back on the caller's dispatcher.
     *
     * Every input is snapshotted here, on the caller's dispatcher, and handed to a top-level pure
     * function. That is not tidiness: `addRival`, `removeRival` and the toggles are independent
     * coroutines (only a newer `load()` cancels an older one), so the roster maps and the fetched*
     * fields keep being mutated while a rebuild is in flight. Reading them from the compute thread
     * is a genuine data race on a plain HashMap/HashSet.
     */
    private suspend fun rebuild() {
        val own = ownResult ?: return
        val inputs = RebuildInputs(
            own = own,
            controls = _controls.value,
            mainName = _userName.value,
            roster = rivalNames.toList(),
            rivalResults = rivalResults.toMap(),
            rivalErrors = rivalErrorNames.toMap(),
            mode = fetchedMode,
            spanFromMillis = fetchedSpanFrom,
            spanToMillis = fetchedSpanTo,
            spanPreset = fetchedSpanPreset,
            gameRank = gameRank,
            siteRank = siteRank,
            playerRanks = playerRanks.toMap(),
            context = fetchedContext,
        )

        val next = withContext(computeDispatcher) { computeJourneyState(inputs) }
        _uiState.value = next
    }

    // Last in the class on purpose. See [restorePreferences]: on Main.immediate this body runs
    // inline inside the constructor, so it must not be reached before the flows it writes exist.
    init {
        restorePreferences()
    }
}

/**
 * An immutable snapshot of everything a rebuild reads. Passing this instead of letting the compute
 * step reach back into the ViewModel is what makes the cross-thread race structurally impossible
 * rather than merely avoided by convention.
 */
private class RebuildInputs(
    val own: PlayerFetchResult,
    val controls: JourneyControls,
    val mainName: String,
    val roster: List<String>,
    val rivalResults: Map<String, PlayerFetchResult>,
    /** Errored rival names, each mapped to whether a second attempt could get past the failure. */
    val rivalErrors: Map<String, Boolean>,
    val mode: JourneyMode,
    val spanFromMillis: Long,
    val spanToMillis: Long,
    val spanPreset: SpanPreset,
    val gameRank: Int?,
    val siteRank: Int?,
    val playerRanks: Map<String, Int>,
    val context: JourneyContext,
)

/**
 * Pure: same inputs, same state, no instance to read. Deliberately top-level rather than a private
 * method - it runs on a background dispatcher, and being outside the class means it *cannot* touch
 * live ViewModel state even by accident.
 */
private fun computeJourneyState(input: RebuildInputs): JourneyUiState {
    val mainJourney = JourneyBuilder.buildPlayer(
        input.mainName, input.mode, input.spanFromMillis, input.spanToMillis,
        input.own.snapshots, input.own.completionByGame, input.own.failedCount,
    )

    // Pending names - on the roster, never fetched - are deliberately absent from the frame rather
    // than present and flagged as failed. A name typed after the last Load has not failed at
    // anything; drawing it as an errored rival put a red chip and a "Could not fetch data for X"
    // notice on the panel for a player nobody had tried to fetch yet. The panel shows those names
    // in their own pending style, off the roster (see JourneyScreen.pendingRivals).
    val rivalJourneys = input.roster.mapNotNull { name -> buildRivalJourney(name, input) }

    val frame = JourneyFrame(
        input.mode, mainJourney, rivalJourneys,
        input.spanFromMillis, input.spanToMillis,
    )

    if (mainJourney.unlocks.isEmpty()) {
        // "Nothing came back" is a different problem from "nothing happened in this window",
        // and only the first one is worth offering a retry for.
        if (mainJourney.failedFetches > 0) {
            return JourneyUiState.Error(
                "${pluralize(mainJourney.failedFetches, "game")} couldn't be fetched. Try again.",
                retryable = true,
            )
        }
        return JourneyUiState.Empty("No unlocks found here. Try another game or time span.")
    }

    // Whether the main player's whole selected badge set goes on the stage, which is the question
    // the stage's WIDTH turns on rather than whether anything is drawn at all.
    //
    // A compare chart draws badges again - one a day per player, which the layout engine selects
    // itself (see LayoutEngine.layout) - but a handful of per-day badges is no reason to hand the
    // journey four screenfuls of timeline to pan across, and the extra width would only spread two
    // curves further apart. So compare mode still answers "no" here, and still gets one screenful.
    val includeNodes = rivalJourneys.all { it.unlocks.isEmpty() }

    // Order matters: what gets drawn decides how much room it needs, never the other way round. A
    // chart with no badges on it (compare mode) stays one screenful wide, because the stretch is
    // there to space icons out and there are barely any to space.
    val widthFactor = LayoutEngine.widthFactorFor(frame, includeNodes)
    // A dense journey gets a wider virtual timeline rather than a more crowded one; the player's
    // pan, zoom and follow camera are what explore the extra width.
    val wideBudget = IconBudget.budgetFor(frame, widthFactor)
    val layout = LayoutEngine.layout(frame, includeNodes, widthFactor, iconBudget = wideBudget)
    // The still preview and the video have no way to pan, so they always draw the one-screenful
    // chart instead of a letterboxed ribbon. The compact chart's grid is coarser, so it draws a
    // subset of the wide one's icons: the bitmaps are preloaded from the WIDE layout, which is
    // therefore always the superset, and anything a lookup still misses falls back to the game's
    // icon and then to a plain disc. Recomputed rather than shared when the chart is already
    // compact - under the SAME wide budget, so both pick from one candidate list and the coarser
    // grid alone is what thins the dense view (the containment invariant depends on it).
    val compactLayout =
        if (widthFactor == 1f) layout
        else LayoutEngine.layout(frame, includeNodes, 1f, iconBudget = wideBudget)

    return JourneyUiState.Ready(
        frame = frame,
        layout = layout,
        compactLayout = compactLayout,
        failedCount = mainJourney.failedFetches,
        rivalErrors = input.roster.filter { input.rivalErrors.containsKey(it) },
        // The advice is offered when it can help anyone on the list: one rival the API refused
        // outright does not make a retry pointless for the one that timed out beside it.
        rivalErrorsRetryable = input.roster.any { input.rivalErrors[it] == true },
        rank = rankChipFor(input),
        // Both modes now. A single game ranks its players on its own leaderboard; a span has no
        // leaderboard for a window of time, so a compare span's rows carry the site rank that came
        // back on the same summary call the players' name spellings did.
        playerRanks = input.playerRanks,
        context = input.context,
        // The id the journey was loaded FOR, not one read back out of the built frame: games is a
        // 0-or-1 map keyed by the fetched snapshot's id, an invariant JourneyBuilder.buildGamePlayer
        // owns and this file cannot see. context.gameId is set from the same variable that drove
        // the fetch (see load()), so it names the game directly instead of trusting that shape.
        gameAchievements = BoardAchievements.forGame(
            input.mode,
            input.context.gameId,
            input.own.snapshots,
        ),
    )
}

/**
 * The end state's card, or null when this journey has no rank worth naming.
 *
 * The denominator is deliberately absent from both lines. The obvious number to hand for a game -
 * its distinct player count - is a different population from the leaderboard the rank comes off,
 * and the site rank has no total the API reports at all, so "of N players" would read exact and be
 * wrong either way.
 */
private fun rankChipFor(input: RebuildInputs): RankChip? = when {
    input.mode == JourneyMode.GAME -> input.gameRank?.let { RankChip(RANK_LABEL, "#$it") }
    input.spanPreset == SpanPreset.CUSTOM -> null
    else -> input.siteRank?.let { RankChip(RANK_LABEL, "#$it") }
}

/**
 * One label for the rank chip in every mode.
 *
 * It read "Leaderboard  Rank #12" on a game and "RetroAchievements  Site rank #4211" over a span,
 * which is a two-word label in front of a three-word value in a row of five chips that has to fit a
 * handheld held in landscape - and the distinction it spelled out (a game's leaderboard versus the
 * site's) is one the chart around it already makes. A rank is a rank; where it is a rank of is what
 * the journey is.
 */
internal const val RANK_LABEL = "Rank"

/**
 * One rival's curve, an errored placeholder when their fetch failed, or null when nothing has been
 * fetched for them at all (a name added to the roster since the last Load).
 */
private fun buildRivalJourney(name: String, input: RebuildInputs): PlayerJourney? {
    if (input.rivalErrors.containsKey(name)) {
        return PlayerJourney(
            name = name, unlocks = emptyList(), games = emptyMap(),
            medals = emptyList(), failedFetches = 0, error = true,
        )
    }
    val rr = input.rivalResults[name] ?: return null

    val pj = JourneyBuilder.buildPlayer(
        name, input.mode, input.spanFromMillis, input.spanToMillis,
        rr.snapshots, rr.completionByGame, rr.failedCount,
    )
    return pj.copy(medals = pj.medals.map { it.copy(ownerName = name, title = "$name · ${it.title}") })
}

/**
 * Whether a failed rival fetch is worth attempting again.
 *
 * The data layer already draws the line between a wall and a wait ([RaJourneyException.retryable]:
 * false for any non-429 4xx), and `load()` has honoured it since the M wave; the roster's own
 * failures flattened both into one message advising a retry. Anything the data layer did not model
 * is treated as retryable, which is the same benefit of the doubt `load()`'s own catch-all gives.
 */
private fun retryableFailure(t: Throwable): Boolean =
    (t as? RaJourneyException)?.retryable ?: true

private const val GENERIC_ERROR = "Something went wrong loading the journey. Try again."

/**
 * Backdrop dim bounds, in the STORED unit: the art's alpha percent. The slider speaks
 * dim = 100 - alpha and moves inside 40..95% with an 85% default, so the stored alpha
 * defaults to 15 inside 5..60. Values a previous build stored outside the window are
 * clamped back into it on restore.
 */
private const val BACKDROP_MIN_ALPHA = 5       // dim 95%
private const val BACKDROP_MAX_ALPHA = 60      // dim 40%
private const val BACKDROP_DEFAULT_ALPHA = 15  // dim 85%

/**
 * What a permanently refused request says. Shown without a Retry beside it: the request is not
 * going to be accepted on a second press.
 *
 * Only a 401 or a 403 sends the reader to Settings, because only those two are about the key. A
 * 404 is a game the API does not have and a 400 is a request it would not parse; telling someone
 * to check a key that is working is an instruction that costs them a trip and fixes nothing.
 * Anything else non-retryable is the data layer's own precondition (no account configured, no game
 * chosen), which already says exactly what is missing - that message is kept.
 */
internal const val PERMANENT_ERROR_KEY = "RetroAchievements rejected the request. Check your API key in Settings."
internal const val PERMANENT_ERROR_GENERIC = "RetroAchievements rejected the request."

private fun permanentErrorFor(e: RaJourneyException): String = when {
    e.statusCode == null -> e.message ?: PERMANENT_ERROR_GENERIC
    e.statusCode == 401 || e.statusCode == 403 -> PERMANENT_ERROR_KEY
    else -> PERMANENT_ERROR_GENERIC
}

/** GAME mode with nothing chosen. Internal so the test names the same string the screen shows. */
internal const val NO_GAME_PICKED = "Choose a game first."

/**
 * The first thing a load says, before any per-game step has been reported. Deliberately not the
 * loading screen's own heading: the screen draws both, one above the other.
 *
 * A progress label rather than a sentence, so it takes no full stop - the same register as
 * `LOADING_TITLE` above it and as "Fetching 3 of 9: Some Game" after it. The error and status
 * strings in this file follow the other half of the rule: a complete sentence ends in a period.
 */
internal const val PREPARING_LABEL = "Preparing your journey"

/**
 * The total one single-game fetch reports: one game, for the player whose turn it is.
 *
 * It exists so the loading screen always has a denominator and can draw its determinate bar. The
 * indeterminate one is a Material indicator built on an `InfiniteTransition`, which invalidates
 * draw every vsync for as long as it is composed - minutes of 60 Hz wakeups on a stalled fetch,
 * on a device whose idle floor is already the problem.
 *
 * The denominator is per PLAYER, not per load: a single-game compare load runs the bar from empty
 * to full once for the signed-in player and once per rival, each step named after whose it is. A
 * cumulative denominator would be the honest alternative, but a rival's game count is not known
 * until their fetch reports it, so the only total available up front is the one in hand.
 */
internal const val SINGLE_GAME_TOTAL = 1
private const val DAY_MILLIS = 86_400_000L

/**
 * Rival compare takes exactly one rival, in every mode. The feature is a duel now: one curve
 * against one curve, a two-line bar, and a colour the user picks for the other player - none of
 * which reads at three players, and the serial per-rival fetch never has to be three fetches.
 */
internal const val RIVAL_CAP = 1

/** What the panel says when a rival is already set and another name is offered. */
internal fun capNotice(): String = "Compare takes one rival. Remove the current one first."

/**
 * What a load says about roster names whose data arrived with nothing on the chart.
 *
 * They are not failures - the fetch was clean, the player simply did not play here - so they keep
 * their roster chip without an error flag. Left unsaid, a chip on the panel and no curve on the
 * chart is a rival the user has no way to account for.
 *
 * The copy follows the mode because the truth does: a span chart really is a window and the rival
 * may well have unlocks outside it, but single-game mode charts the game's whole history
 * (JourneyBuilder passes windowFilter=false), so an empty rival there has no achievements for the
 * game at all - and "in this window" read as false to anyone who knew the rival's wider record.
 */
internal fun emptyRivalNotice(names: List<String>, mode: JourneyMode): String {
    val who = names.joinToString(", ")
    val have = if (names.size == 1) "has" else "have"
    return when (mode) {
        JourneyMode.GAME -> "$who $have no achievements for this game."
        JourneyMode.SPAN -> "$who $have no unlocks in this window."
    }
}

/**
 * What the loading screen says while one rival's data is being fetched.
 *
 * Named after the rival first, because on a load with a roster the reader's question is whose turn
 * it is, and the count that follows only means anything once that is answered. The bare form (no
 * [game]) is what a rival gets before their first per-game step lands, and the whole of what a
 * single-game fetch has to say - there is one game, and the repository does not call back for it.
 *
 * Internal so the ViewModel's tests pin the string the screen actually shows.
 */
internal fun rivalStepLabel(rival: String, game: String? = null, done: Int = 0, total: Int = 0): String =
    if (game == null) "Fetching $rival" else "Fetching $rival: $game ($done/$total)"

/**
 * The denominator a rival's step starts on: one for a single game, none yet for a span (the first
 * per-game callback is what supplies it). Keeps the bar determinate wherever a total is knowable.
 */
internal fun rivalStepTotal(mode: JourneyMode): Int =
    if (mode == JourneyMode.GAME) SINGLE_GAME_TOTAL else 0
