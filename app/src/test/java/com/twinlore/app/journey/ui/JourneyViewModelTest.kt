package com.twinlore.app.journey.ui

import com.twinlore.app.data.retroachievements.RaCredentials
import com.twinlore.app.data.retroachievements.RetroAchievementsCredentialsRepository
import com.twinlore.app.journey.data.JourneyDataSource
import com.twinlore.app.journey.data.PlayerFetchResult
import com.twinlore.app.journey.data.PlayerSummary
import com.twinlore.app.journey.data.RaJourneyException
import com.twinlore.app.journey.domain.GridIconLayout
import com.twinlore.app.journey.domain.IconBudget
import com.twinlore.app.journey.domain.LayoutEngine
import com.twinlore.app.journey.domain.model.AchSnapshot
import com.twinlore.app.journey.domain.model.CompletionRow
import com.twinlore.app.journey.domain.model.GameSnapshot
import com.twinlore.app.journey.domain.model.JourneyMode
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class JourneyViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    /**
     * The rebuild hops to a compute dispatcher off the main thread in production; the tests inject
     * the same test dispatcher so a toggle's effect is still observable on the next line.
     */
    private fun viewModel(
        repository: JourneyDataSource,
        credentials: RetroAchievementsCredentialsRepository,
    ) = JourneyViewModel(repository, credentials, dispatcher)

    private val DAY = 86_400_000L
    private val sqlFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    private fun ts(millisAgo: Long): String =
        sqlFormat.format(Instant.ofEpochMilli(System.currentTimeMillis() - millisAgo).atZone(ZoneOffset.UTC))

    private fun ach(
        id: Int,
        points: Int = 10,
        earnedMillisAgo: Long = DAY,
        hardcore: Boolean = false,
        numAwarded: Int = 50,
    ) = AchSnapshot(
        id = id, title = "A$id", description = "desc$id", points = points,
        badgeName = "badge$id", type = "standard",
        dateEarned = ts(earnedMillisAgo),
        dateEarnedHardcore = if (hardcore) ts(earnedMillisAgo) else null,
        numAwarded = numAwarded,
    )

    private fun snapshot(gameId: Int, title: String = "Game$gameId", achievements: List<AchSnapshot>, players: Int = 100) =
        GameSnapshot(gameId = gameId, title = title, iconPath = "/icon$gameId.png", artPaths = emptyList(), players = players, achievements = achievements)

    /** Hand-rolled fake with per-method call counters; the ViewModel must never touch these on toggles. */
    private class FakeJourneyDataSource : JourneyDataSource {
        var fetchOwnCalls = 0
        var fetchRivalCalls = 0
        var fetchGameRankCalls = 0
        var siteRankCalls = 0
        var clearSessionCacheCalls = 0

        var lastFetchOwnGameId: Int? = null
        var lastFetchOwnMode: JourneyMode? = null
        var lastFetchOwnSpanFrom: Long? = null
        var lastFetchRivalGameId: Int? = null
        /** What each rival fetch was keyed on: the request, never the resolved wall-clock bounds. */
        val rivalSpanKeys = mutableListOf<String>()
        var lastFetchGameRankGameId: Int? = null
        val gameRankUsers = mutableListOf<String?>()
        var gameRank: Int? = null
        val rivalGameRanks = mutableMapOf<String, Int>()
        /**
         * What a rank lookup throws for a given player, if anything; the empty string stands for
         * the signed-in player (the `null` name).
         *
         * The production repository now answers null for anything that goes wrong, credential read
         * and dispatcher hop included, so this hook models a contract violation on purpose: it is
         * how the ViewModel's own best-effort guards are held to the promise the callers make.
         */
        val gameRankExceptions = mutableMapOf<String, Throwable>()
        var siteRank: Int? = null
        /** The same hook for the span modes' site rank. */
        var siteRankException: Throwable? = null

        var ownResult: PlayerFetchResult = PlayerFetchResult(emptyList(), emptyMap(), 0)
        var ownException: RaJourneyException? = null
        /** An unmodelled failure (Room, Gson) escaping the data layer. */
        var ownThrowable: Throwable? = null
        var ownProgressSteps: List<Triple<String, Int, Int>> = emptyList()
        /** Test hook: invoked synchronously right after the ViewModel's uiState is updated for a progress step. */
        var afterOwnProgress: (() -> Unit)? = null
        /** Test hook: invoked synchronously right when fetchOwn is entered (after the initial Loading state is set). */
        var onFetchOwnStart: (() -> Unit)? = null
        /**
         * Per-call suspension gates (indexed by call order, 0-based): if set for the current call
         * index, fetchOwn suspends on it before returning — lets tests hold a fetch "in flight" to
         * exercise cancellation of a stale load() by a newer one.
         */
        var ownGates: MutableList<CompletableDeferred<Unit>> = mutableListOf()
        /**
         * Models the device's most awkward failure: a request cancelled underneath the data layer
         * that comes back as an ordinary modelled error rather than as a CancellationException.
         *
         * On Android a cancelled OkHttp call raises an IOException, and `RaJourneyClient.call`
         * turns an IOException into a retryable [RaJourneyException]. Nothing about the exception
         * that reaches the ViewModel says the coroutine is dead. The gate is awaited inside
         * `NonCancellable` so the fake behaves the same way: it resumes even though its caller has
         * been cancelled, and then throws something perfectly ordinary.
         */
        var ownCancellationDisguise = false

        /** The same disguise on the rival loop, per rival name. */
        val rivalCancellationDisguise = mutableSetOf<String>()

        /**
         * The other shape of the same hazard: a rival fetch that RETURNS after its caller was
         * cancelled, rather than failing. Nothing about the value says the coroutine is dead, so
         * only the ViewModel asking its own job can keep it out of the next visit's roster.
         */
        val rivalResumeAfterCancel = mutableSetOf<String>()
        /** Per-call results (indexed by call order, 0-based); falls back to [ownResult] when absent. */
        var ownResultsSequential: MutableList<PlayerFetchResult> = mutableListOf()

        val rivalResults = mutableMapOf<String, PlayerFetchResult>()
        val rivalExceptions = mutableMapOf<String, RaJourneyException>()
        /** Holds a named rival's fetch in flight, so a test can look at the ViewModel mid-load. */
        val rivalGates = mutableMapOf<String, CompletableDeferred<Unit>>()
        /** Per-game steps a rival's span fetch reports, the same shape [ownProgressSteps] takes. */
        var rivalProgressSteps: List<Triple<String, Int, Int>> = emptyList()
        /** Invoked right after the ViewModel's uiState is updated for one of those steps. */
        var afterRivalProgress: (() -> Unit)? = null

        var playedGamesCalls = 0
        var playedGamesResult: List<CompletionRow> = emptyList()
        var playedGamesException: Throwable? = null
        /** Lets a test hold a playedGames() call in flight, to exercise the ViewModel's job guard. */
        var playedGamesGate: CompletableDeferred<Unit>? = null

        override suspend fun fetchOwn(
            mode: JourneyMode,
            gameId: Int?,
            spanFromMillis: Long,
            spanToMillis: Long,
            onProgress: (String, Int, Int) -> Unit,
        ): PlayerFetchResult {
            val callIndex = fetchOwnCalls
            fetchOwnCalls++
            lastFetchOwnGameId = gameId
            lastFetchOwnMode = mode
            lastFetchOwnSpanFrom = spanFromMillis
            onFetchOwnStart?.invoke()
            if (ownCancellationDisguise) {
                withContext(NonCancellable) { ownGates.getOrNull(callIndex)?.await() }
                // Steps reported after the caller was cancelled, which is what a request already in
                // flight on the IO dispatcher does when someone leaves the screen.
                ownProgressSteps.forEach { (label, done, total) -> onProgress(label, done, total) }
                throw RaJourneyException("cancelled underneath us, and it does not say so")
            }
            ownGates.getOrNull(callIndex)?.await()
            ownProgressSteps.forEach { (label, done, total) ->
                onProgress(label, done, total)
                afterOwnProgress?.invoke()
            }
            ownException?.let { throw it }
            ownThrowable?.let { throw it }
            return ownResultsSequential.getOrNull(callIndex) ?: ownResult
        }

        override suspend fun fetchRival(
            name: String,
            mode: JourneyMode,
            gameId: Int?,
            spanFromMillis: Long,
            spanToMillis: Long,
            spanKey: String,
            onProgress: (String, Int, Int) -> Unit,
        ): PlayerFetchResult {
            fetchRivalCalls++
            lastFetchRivalGameId = gameId
            rivalSpanKeys += spanKey
            if (name in rivalResumeAfterCancel) {
                withContext(NonCancellable) { rivalGates[name]?.await() }
                return rivalResults[name] ?: PlayerFetchResult(emptyList(), emptyMap(), 0)
            }
            if (name in rivalCancellationDisguise) {
                withContext(NonCancellable) { rivalGates[name]?.await() }
                throw RaJourneyException("cancelled underneath us, and it does not say so")
            }
            rivalGates[name]?.await()
            rivalProgressSteps.forEach { (label, done, total) ->
                onProgress(label, done, total)
                afterRivalProgress?.invoke()
            }
            rivalExceptions[name]?.let { throw it }
            return rivalResults[name] ?: PlayerFetchResult(emptyList(), emptyMap(), 0)
        }

        override suspend fun fetchGameRank(gameId: Int, userName: String?): Int? {
            fetchGameRankCalls++
            lastFetchGameRankGameId = gameId
            gameRankUsers += userName
            gameRankExceptions[userName.orEmpty()]?.let { throw it }
            return if (userName == null) gameRank else rivalGameRanks[userName]
        }

        override suspend fun siteRank(): Int? {
            siteRankCalls++
            siteRankException?.let { throw it }
            return siteRank
        }

        override suspend fun playedGames(): List<CompletionRow> {
            playedGamesCalls++
            playedGamesGate?.await()
            playedGamesException?.let { throw it }
            return playedGamesResult
        }

        /**
         * The spelling RA answers with for a given lowercased name. Empty by default: a name
         * nothing has echoed back has no canonical form to adopt. [playerSummary] fills it the way
         * the real repository does, so a test that wants a spelling adopted sets the summary.
         */
        val canonicalNames = mutableMapOf<String, String>()

        /** Summaries by lowercased name; absent means the call answered nothing (best effort). */
        val summaries = mutableMapOf<String, PlayerSummary>()
        val summaryCalls = mutableListOf<String>()

        override suspend fun playerSummary(name: String): PlayerSummary? {
            summaryCalls += name
            val summary = summaries[name.trim().lowercase()] ?: return null
            summary.canonicalName?.let { canonicalNames[name.trim().lowercase()] = it }
            return summary
        }

        override fun canonicalName(name: String): String? = canonicalNames[name.trim().lowercase()]

        var knownRivals: List<String> = emptyList()
        override suspend fun knownRivals(): List<String> = knownRivals

        val forgotten = mutableListOf<String>()
        override suspend fun forgetRival(name: String) {
            forgotten += name
            knownRivals = knownRivals.filterNot { it.equals(name, ignoreCase = true) }
        }

        var clearCacheCalls = 0
        override suspend fun clearCache() {
            clearCacheCalls++
            knownRivals = emptyList()
        }

        override fun clearSessionCache() { clearSessionCacheCalls++ }
    }

    /**
     * [hasAccount] false is a key stored with no username: the repository answers null for the
     * credentials (it has no username to build them from) while still reporting a custom key, which
     * is exactly the state the settings screen can now leave behind.
     */
    private fun creds(
        hasCustomKey: Boolean = true,
        username: String = "me",
        apiKey: String = "key",
        hasAccount: Boolean = true,
    ): RetroAchievementsCredentialsRepository =
        mock<RetroAchievementsCredentialsRepository> {
            on { hasCustomApiKey() } doReturn hasCustomKey
            onBlocking { getCredentials() } doReturn
                if (hasCustomKey && hasAccount) RaCredentials(username, apiKey) else null
        }

    // --- start() ---

    @Test fun `start - no custom key yields Locked`() = runTest {
        val vm = viewModel(FakeJourneyDataSource(), creds(hasCustomKey = false))
        vm.start(null)
        advanceUntilIdle()
        assertEquals(JourneyUiState.Locked(LockedReason.NO_API_KEY), vm.uiState.value)
    }

    /**
     * A key with no account behind it. Possible since the key started persisting on its own, and
     * before this gate asked the second question such a user walked straight into the data layer's
     * "No RetroAchievements account configured" - a retryable error for something retrying cannot
     * fix. The vault's Achievements tab reaches this without ever opening the library journey.
     */
    @Test fun `start - a key with no username yields Locked, not an error`() = runTest {
        val fake = FakeJourneyDataSource()
        val vm = viewModel(fake, creds(hasCustomKey = true, hasAccount = false))
        vm.start(null)
        advanceUntilIdle()
        assertEquals(JourneyUiState.Locked(LockedReason.NO_ACCOUNT), vm.uiState.value)
        assertEquals("nothing may be fetched without an account", 0, fake.fetchOwnCalls)
    }

    @Test fun `start - a locked game with a key but no username yields Locked, not an error`() = runTest {
        val fake = FakeJourneyDataSource()
        val vm = viewModel(fake, creds(hasCustomKey = true, hasAccount = false))
        vm.start(42)
        advanceUntilIdle()
        assertEquals(JourneyUiState.Locked(LockedReason.NO_ACCOUNT), vm.uiState.value)
        assertEquals(0, fake.fetchOwnCalls)
    }

    @Test fun `start - custom key, no locked game yields Idle`() = runTest {
        val vm = viewModel(FakeJourneyDataSource(), creds())
        vm.start(null)
        advanceUntilIdle()
        assertEquals(JourneyUiState.Idle, vm.uiState.value)
    }

    @Test fun `start - locked game auto-loads that game over all time`() = runTest {
        val fake = FakeJourneyDataSource()
        fake.ownResult = PlayerFetchResult(listOf(snapshot(42, achievements = listOf(ach(1)))), emptyMap(), 0)
        val vm = viewModel(fake, creds())
        vm.start(42)
        advanceUntilIdle()
        assertEquals(1, fake.fetchOwnCalls)
        assertEquals(JourneyMode.GAME, fake.lastFetchOwnMode)
        assertEquals(42, fake.lastFetchOwnGameId)
        // A vault journey is "this game, start to finish": it must not inherit whatever window the
        // library panel was last left on.
        assertEquals(0L, fake.lastFetchOwnSpanFrom)
        assertTrue("expected Ready, was ${vm.uiState.value}", vm.uiState.value is JourneyUiState.Ready)
    }

    /**
     * The vault entry composes no controls at all, so it must not write to the ones the library
     * panel shows. It used to overwrite the game id and leave the picker's title alone: the next
     * library visit opened in single-game mode, with the picker button reading the last game the
     * user picked there and the vault's id behind it, and Load charted the vault's game.
     */
    @Test fun `start - a locked entry leaves the library controls alone`() = runTest {
        val fake = FakeJourneyDataSource()
        fake.ownResult = PlayerFetchResult(listOf(snapshot(24, achievements = listOf(ach(1)))), emptyMap(), 0)
        val vm = viewModel(fake, creds())
        vm.start(null)
        advanceUntilIdle()
        vm.setMode(JourneyMode.GAME)
        vm.selectGame(24, "Sonic 2")

        vm.start(1447)
        advanceUntilIdle()
        assertEquals(1447, fake.lastFetchOwnGameId)

        // Back out to the library journey: the panel reads what the user last chose on it, and
        // Load charts exactly that.
        vm.start(null)
        advanceUntilIdle()
        assertEquals(JourneyMode.GAME, vm.controls.value.mode)
        assertEquals("24", vm.controls.value.gameIdText)
        assertEquals("Sonic 2", vm.controls.value.selectedGameTitle)
        vm.load()
        advanceUntilIdle()
        assertEquals(24, fake.lastFetchOwnGameId)
    }

    /**
     * The vault session's journey is about one game and composes no rival controls at all, so a
     * roster built in the library's journey has nothing on that screen to remove it. This ViewModel
     * is resolved against the activity rather than a nav entry, so the roster was still here when
     * the locked journey started: it drew rival curves nobody asked for, and refetched every name
     * against the locked game to get them.
     */
    @Test fun `start - locked game drops a roster built in the library journey`() = runTest {
        val fake = FakeJourneyDataSource()
        fake.ownResult = PlayerFetchResult(listOf(snapshot(1, achievements = listOf(ach(1)))), emptyMap(), 0)
        fake.rivalResults["Rival1"] = PlayerFetchResult(listOf(snapshot(2, achievements = listOf(ach(2)))), emptyMap(), 0)
        val vm = viewModel(fake, creds())
        vm.setSpanPreset(SpanPreset.ALL)
        vm.start(null)
        advanceUntilIdle()
        vm.addRival("Rival1")
        vm.load()
        advanceUntilIdle()
        assertEquals(
            "fixture must actually have a rival on the library chart",
            listOf("Rival1"),
            (vm.uiState.value as JourneyUiState.Ready).frame.rivals.map { it.name },
        )

        val rivalFetchesBefore = fake.fetchRivalCalls
        fake.ownResult = PlayerFetchResult(listOf(snapshot(42, achievements = listOf(ach(3)))), emptyMap(), 0)
        vm.start(42)
        advanceUntilIdle()

        val ready = vm.uiState.value as JourneyUiState.Ready
        assertTrue("the locked journey is compare-free", ready.frame.rivals.isEmpty())
        assertTrue(ready.rivalErrors.isEmpty())
        assertEquals("no rival is refetched for the locked view", rivalFetchesBefore, fake.fetchRivalCalls)
        assertTrue(
            "the repository's session rival cache must not be left to answer the locked view",
            fake.clearSessionCacheCalls >= 1,
        )
    }

    // --- start(): one honest reset per visit ---
    //
    // This ViewModel is resolved against the activity rather than a nav entry, so every field on it
    // outlives the screen. The composition does not: a visit re-runs start() and rebuilds the whole
    // subtree. Everything below is a symptom of that mismatch, and they all have the same fix.

    /**
     * The reset happens on the caller's thread, before start() has suspended for anything. It has
     * to: the screen calls this before it reads uiState, and an Idle that only arrives a dispatch
     * later is an Idle the first composition never sees - it composes the previous visit's Ready
     * instead, which opens the full-screen player and starts autoplaying a journey nobody asked for.
     */
    @Test fun `start - drops the previous visit's journey before it returns`() = runTest {
        val fake = FakeJourneyDataSource()
        fake.ownResult = PlayerFetchResult(listOf(snapshot(1, achievements = listOf(ach(1)))), emptyMap(), 0)
        val vm = viewModel(fake, creds())
        vm.start(null)
        advanceUntilIdle()
        vm.load()
        advanceUntilIdle()
        assertTrue(vm.uiState.value is JourneyUiState.Ready)

        vm.start(null)

        assertEquals(
            "the stale Ready must be gone by the time start() returns",
            JourneyUiState.Idle,
            vm.uiState.value,
        )
    }

    /**
     * Backing out of a loading panel used to leave the fetch running: the library path wrote Idle
     * and cancelled nothing. A second or two into the next visit the orphan flipped the configure
     * panel back to Loading and then into the player, discarding whatever the user had just chosen.
     */
    @Test fun `start - a load left running by the previous visit cannot land on this one`() = runTest {
        val fake = FakeJourneyDataSource()
        fake.ownResult = PlayerFetchResult(listOf(snapshot(1, achievements = listOf(ach(1)))), emptyMap(), 0)
        val gate = CompletableDeferred<Unit>()
        fake.ownGates = mutableListOf(gate)
        val vm = viewModel(fake, creds())
        vm.start(null)
        advanceUntilIdle()
        vm.load()
        assertTrue("the fixture must hold the fetch in flight", vm.uiState.value is JourneyUiState.Loading)

        vm.start(null)
        assertEquals(JourneyUiState.Idle, vm.uiState.value)

        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(
            "an orphaned load must not throw the next visit into a journey",
            JourneyUiState.Idle,
            vm.uiState.value,
        )
    }

    /**
     * The same load, arriving over a locked screen. The gate returned before anything was
     * cancelled, so a load started while the account still had credentials could publish a full
     * journey on top of the "add your username in Settings" card.
     */
    @Test fun `start - a load left running cannot land on top of a Locked screen`() = runTest {
        val fake = FakeJourneyDataSource()
        fake.ownResult = PlayerFetchResult(listOf(snapshot(1, achievements = listOf(ach(1)))), emptyMap(), 0)
        val gate = CompletableDeferred<Unit>()
        fake.ownGates = mutableListOf(gate)
        val credentials = creds()
        val vm = viewModel(fake, credentials)
        vm.start(null)
        advanceUntilIdle()
        vm.load()
        assertTrue(vm.uiState.value is JourneyUiState.Loading)

        // Settings, on the way past: the key is gone by the time the screen is opened again.
        whenever(credentials.hasCustomApiKey()).thenReturn(false)
        vm.start(null)
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(
            "a journey must not be drawn for an account that no longer has credentials",
            JourneyUiState.Locked(LockedReason.NO_API_KEY),
            vm.uiState.value,
        )
    }

    /**
     * The owner's own repro, as a test: put a rival on the roster, press Load, leave the screen
     * while that rival's fetch is still in flight, come back.
     *
     * Everything a visit accumulates has to go, and the chart and the roster have to agree that it
     * did - the reported symptom was a rival that came back on the chart with no chip on the panel
     * to remove it. Two surfaces are checked here for exactly that reason: [JourneyViewModel.roster]
     * is what the panel draws chips from, and the frame is what the chart draws curves from.
     *
     * The rival's fetch is completed *after* the return, so the coroutine that would write it back
     * resumes into a session that has already been reset. Nothing it holds may reach either surface.
     */
    @Test fun `start - a visit abandoned mid-load leaves no rival on the chart or the roster`() = runTest {
        val fake = FakeJourneyDataSource()
        fake.ownResult = PlayerFetchResult(listOf(snapshot(1, achievements = listOf(ach(1)))), emptyMap(), 0)
        fake.rivalResults["aria"] = PlayerFetchResult(listOf(snapshot(2, achievements = listOf(ach(2)))), emptyMap(), 0)
        fake.rivalGates["aria"] = CompletableDeferred()
        val vm = viewModel(fake, creds())
        vm.setSpanPreset(SpanPreset.ALL)
        vm.start(null)
        advanceUntilIdle()
        vm.addRival("aria")
        assertEquals("the fixture must have the name on the roster", listOf("aria"), vm.roster.value)
        vm.load()
        advanceUntilIdle()
        assertTrue("the fixture must hold the rival's fetch in flight", vm.uiState.value is JourneyUiState.Loading)

        // Out to the library and back: the visit ends, and a new one begins.
        vm.start(null)
        fake.rivalGates["aria"]!!.complete(Unit)
        advanceUntilIdle()

        assertEquals("the roster must be empty on the panel", emptyList<String>(), vm.roster.value)
        assertEquals("and nothing may be published over the fresh visit", JourneyUiState.Idle, vm.uiState.value)

        // And the load the returning visit runs charts the player alone: no rival name survived to
        // be silently refetched, and none is on the frame.
        vm.load()
        advanceUntilIdle()
        val ready = vm.uiState.value as JourneyUiState.Ready
        assertTrue("the chart must agree with the roster", ready.frame.rivals.isEmpty())
        assertTrue(ready.rivalErrors.isEmpty())
    }

    /**
     * The other half of the same repro, and the mechanism that made it possible.
     *
     * `start()` cancels the visit's work, but cancellation is not always what the data layer
     * reports. A cancelled OkHttp call raises an IOException and `RaJourneyClient` models an
     * IOException as an ordinary retryable failure, so the abandoned load arrived at the ViewModel
     * looking exactly like a network error - and the catch that handles those cleared the session
     * and published an error screen over the visit that had already replaced it. The fixture
     * reproduces that shape precisely (see `ownCancellationDisguise`).
     *
     * Nothing an abandoned visit does may reach the screen: the returning visit's Idle stands.
     */
    @Test fun `start - a load cancelled by leaving cannot publish an error over the next visit`() = runTest {
        val fake = FakeJourneyDataSource()
        fake.ownResult = PlayerFetchResult(listOf(snapshot(1, achievements = listOf(ach(1)))), emptyMap(), 0)
        fake.ownCancellationDisguise = true
        fake.ownGates = mutableListOf(CompletableDeferred())
        val vm = viewModel(fake, creds())
        vm.setSpanPreset(SpanPreset.ALL)
        vm.start(null)
        advanceUntilIdle()
        vm.addRival("aria")
        vm.load()
        advanceUntilIdle()
        assertTrue("the fixture must hold the load in flight", vm.uiState.value is JourneyUiState.Loading)

        // Out to the library and back, then the abandoned request finally answers.
        vm.start(null)
        advanceUntilIdle()
        fake.ownGates[0].complete(Unit)
        advanceUntilIdle()

        assertEquals("the returning visit owns the screen", JourneyUiState.Idle, vm.uiState.value)
        assertEquals("and its empty roster", emptyList<String>(), vm.roster.value)
    }

    /**
     * The same disguise inside the rival loop, which is its own catch and its own set of writes.
     *
     * A rival fetch cancelled by someone leaving the screen comes back as an ordinary retryable
     * failure, so without a guard the abandoned load flagged that rival as errored - and, before
     * the write moved behind its own `ensureActive`, could commit their results - into the session
     * that had already replaced it.
     */
    @Test fun `start - a rival fetch cancelled by leaving writes nothing into the next visit`() = runTest {
        val fake = FakeJourneyDataSource()
        fake.ownResult = PlayerFetchResult(listOf(snapshot(1, achievements = listOf(ach(1)))), emptyMap(), 0)
        fake.rivalResults["aria"] = PlayerFetchResult(listOf(snapshot(2, achievements = listOf(ach(2)))), emptyMap(), 0)
        fake.rivalCancellationDisguise += "aria"
        fake.rivalGates["aria"] = CompletableDeferred()
        val vm = viewModel(fake, creds())
        vm.setSpanPreset(SpanPreset.ALL)
        vm.start(null)
        advanceUntilIdle()
        vm.addRival("aria")
        vm.load()
        advanceUntilIdle()
        assertTrue("the fixture must hold the rival's fetch in flight", vm.uiState.value is JourneyUiState.Loading)

        vm.start(null)
        advanceUntilIdle()
        fake.rivalGates["aria"]!!.complete(Unit)
        advanceUntilIdle()

        assertEquals("the returning visit owns the screen", JourneyUiState.Idle, vm.uiState.value)
        assertEquals(emptyList<String>(), vm.roster.value)

        // And the returning visit's own load carries no error flag from the abandoned one.
        fake.rivalCancellationDisguise.clear()
        fake.rivalGates.remove("aria")
        vm.addRival("aria")
        vm.load()
        advanceUntilIdle()
        val ready = vm.uiState.value as JourneyUiState.Ready
        assertTrue("no failure survived the visit that suffered it", ready.rivalErrors.isEmpty())
    }

    /**
     * And the same for a rival fetch that RETURNS after the visit ended rather than failing.
     *
     * Nothing about a perfectly good `PlayerFetchResult` says the coroutine that asked for it is
     * dead, so without a check in front of the write the abandoned load committed its rival into
     * the roster of the visit that replaced it - which is the exact shape of the bug the owner
     * reported: a rival on the chart with no chip on the panel to remove it.
     */
    @Test fun `start - a rival fetch that returns after leaving is not committed to the next visit`() = runTest {
        val fake = FakeJourneyDataSource()
        fake.ownResult = PlayerFetchResult(listOf(snapshot(1, achievements = listOf(ach(1)))), emptyMap(), 0)
        fake.rivalResults["aria"] = PlayerFetchResult(listOf(snapshot(2, achievements = listOf(ach(2)))), emptyMap(), 0)
        fake.rivalResumeAfterCancel += "aria"
        fake.rivalGates["aria"] = CompletableDeferred()
        val vm = viewModel(fake, creds())
        vm.setSpanPreset(SpanPreset.ALL)
        vm.start(null)
        advanceUntilIdle()
        vm.addRival("aria")
        vm.load()
        advanceUntilIdle()
        assertTrue("the fixture must hold the rival's fetch in flight", vm.uiState.value is JourneyUiState.Loading)

        vm.start(null)
        advanceUntilIdle()
        fake.rivalGates["aria"]!!.complete(Unit)
        advanceUntilIdle()

        assertEquals("the returning visit owns the screen", JourneyUiState.Idle, vm.uiState.value)
        assertEquals(emptyList<String>(), vm.roster.value)

        // The returning visit's own load charts the player alone: nothing was committed behind it.
        vm.load()
        advanceUntilIdle()
        val ready = vm.uiState.value as JourneyUiState.Ready
        assertTrue("the chart must agree with the roster", ready.frame.rivals.isEmpty())
    }

    /**
     * A progress callback that lands after the visit has ended must not flip the screen back to
     * Loading. The repository calls them from the IO dispatcher, which is not the thread that
     * cancels anything, so one already in flight when someone leaves arrives afterwards - and
     * `setLoading` writes uiState unconditionally, which is a dead load taking the live visit
     * hostage on a screen it can never leave.
     */
    @Test fun `start - a progress callback from an abandoned load cannot reopen the loading screen`() = runTest {
        val fake = FakeJourneyDataSource()
        fake.ownResult = PlayerFetchResult(listOf(snapshot(1, achievements = listOf(ach(1)))), emptyMap(), 0)
        fake.ownProgressSteps = listOf(Triple("Some Game", 1, 2))
        // The fetch reports its steps after its caller has been cancelled, which is exactly what a
        // request already in flight on the IO dispatcher does when somebody leaves the screen.
        fake.ownCancellationDisguise = true
        fake.ownGates = mutableListOf(CompletableDeferred())
        val vm = viewModel(fake, creds())
        vm.setSpanPreset(SpanPreset.ALL)
        vm.start(null)
        advanceUntilIdle()
        vm.load()
        advanceUntilIdle()

        vm.start(null)
        advanceUntilIdle()
        fake.ownGates[0].complete(Unit)
        advanceUntilIdle()

        assertEquals("the returning visit's Idle stands", JourneyUiState.Idle, vm.uiState.value)
    }

    /**
     * The roster is a configure control: a name goes on it with nothing loaded, nothing fetched and
     * no journey published. The panel used to refuse ("Load your journey first, then add rivals")
     * because there was nowhere to put a name that had no curve yet, and every add that got past
     * the gate cost its own round trip.
     */
    @Test fun `addRival - on a fresh panel takes the name with no fetch and no journey`() = runTest {
        val fake = FakeJourneyDataSource()
        fake.ownResult = PlayerFetchResult(listOf(snapshot(1, achievements = listOf(ach(1)))), emptyMap(), 0)
        fake.rivalResults["aria"] = PlayerFetchResult(listOf(snapshot(2, achievements = listOf(ach(2)))), emptyMap(), 0)
        val vm = viewModel(fake, creds())
        vm.start(null)
        advanceUntilIdle()

        vm.addRival("aria")
        advanceUntilIdle()

        assertEquals("the chip appears at once", listOf("aria"), vm.roster.value)
        assertNull("and says nothing about loading first", vm.notice.value)
        assertEquals("nothing is fetched for it", 0, fake.fetchRivalCalls)
        assertEquals("and no journey is published", JourneyUiState.Idle, vm.uiState.value)

        // One press of Load, and the roster is fetched with the player's own data.
        vm.load()
        advanceUntilIdle()
        assertEquals(1, fake.fetchRivalCalls)
        assertEquals(
            listOf("aria"),
            (vm.uiState.value as JourneyUiState.Ready).frame.rivals.map { it.name },
        )
    }

    /**
     * The roster that could not be seen, could not be removed and still blocked you. It survived
     * the visit while the panel's chips came from the Ready state alone, so a fresh panel rejected
     * names for duplicating rivals with no chip on screen and refused new ones against a cap it
     * would not show.
     */
    @Test fun `start - the roster does not survive the visit`() = runTest {
        val fake = FakeJourneyDataSource()
        fake.ownResult = PlayerFetchResult(listOf(snapshot(1, achievements = listOf(ach(1)))), emptyMap(), 0)
        fake.rivalResults["aria"] = PlayerFetchResult(listOf(snapshot(2, achievements = listOf(ach(2)))), emptyMap(), 0)
        val vm = viewModel(fake, creds())
        vm.start(null)
        advanceUntilIdle()
        vm.addRival("aria") // the one slot rival compare has
        vm.load()
        advanceUntilIdle()
        assertEquals(1, (vm.uiState.value as JourneyUiState.Ready).frame.rivals.size)

        vm.start(null)
        advanceUntilIdle()
        assertEquals("the panel opens on an empty roster", emptyList<String>(), vm.roster.value)
        val rivalFetchesBefore = fake.fetchRivalCalls
        vm.load()
        advanceUntilIdle()

        val ready = vm.uiState.value as JourneyUiState.Ready
        assertTrue("a load must not silently refetch an invisible roster", ready.frame.rivals.isEmpty())
        assertEquals(rivalFetchesBefore, fake.fetchRivalCalls)
    }

    /** A notice describes the journey that was loaded; it has nothing to say about the next visit. */
    @Test fun `start - the previous visit's notice does not follow it`() = runTest {
        val fake = FakeJourneyDataSource()
        fake.ownResult = PlayerFetchResult(listOf(snapshot(1, achievements = listOf(ach(1)))), emptyMap(), 0)
        val vm = viewModel(fake, creds())
        vm.start(null)
        advanceUntilIdle()
        vm.load()
        advanceUntilIdle()
        vm.addRival("me") // the signed-in player: rejected with a notice
        advanceUntilIdle()
        assertNotNull(vm.notice.value)

        vm.start(null)
        advanceUntilIdle()

        assertNull(vm.notice.value)
    }

    /** The picker's fetch is a visit's work too: it must not land on the next one. */
    @Test fun `start - cancels an in-flight played games fetch`() = runTest {
        val fake = FakeJourneyDataSource()
        fake.playedGamesGate = CompletableDeferred()
        fake.playedGamesResult = listOf(completionRow(1))
        val vm = viewModel(fake, creds())
        vm.loadPlayedGames()

        vm.start(null)
        fake.playedGamesGate!!.complete(Unit)
        advanceUntilIdle()

        assertTrue(vm.playedGames.value.isEmpty())
    }

    /** A failed picker fetch is a fact about one attempt, not a standing verdict on the account. */
    @Test fun `start - clears a sticky played-games error`() = runTest {
        val fake = FakeJourneyDataSource()
        fake.playedGamesException = RaJourneyException("network down")
        val vm = viewModel(fake, creds())
        vm.loadPlayedGames()
        advanceUntilIdle()
        assertTrue(vm.playedGamesError.value)

        vm.start(null)
        advanceUntilIdle()

        assertFalse(vm.playedGamesError.value)
    }

    /**
     * The same staleness inside one visit. A failed load takes the previous journey off the screen
     * and leaves no way back to it, so the cached results it left behind were good for exactly one
     * thing: an add from the error panel replaced the error message with the *previous* chart and
     * opened the player on it.
     */
    @Test fun `addRival - after a failed load, does not resurrect the chart that failed to be replaced`() = runTest {
        val fake = FakeJourneyDataSource()
        fake.ownResult = PlayerFetchResult(listOf(snapshot(1, achievements = listOf(ach(1)))), emptyMap(), 0)
        fake.rivalResults["aria"] = PlayerFetchResult(listOf(snapshot(2, achievements = listOf(ach(2)))), emptyMap(), 0)
        val vm = viewModel(fake, creds())
        vm.start(null)
        advanceUntilIdle()
        vm.load()
        advanceUntilIdle()
        assertTrue(vm.uiState.value is JourneyUiState.Ready)

        fake.ownException = RaJourneyException("network down")
        vm.setSpanPreset(SpanPreset.Y1)
        vm.load()
        advanceUntilIdle()
        assertTrue(vm.uiState.value is JourneyUiState.Error)

        vm.addRival("aria")
        advanceUntilIdle()

        assertEquals("the name is taken, as configure state", listOf("aria"), vm.roster.value)
        assertEquals("but nothing is fetched for it", 0, fake.fetchRivalCalls)
        assertTrue(
            "the error must not be replaced by the journey that failed to be replaced",
            vm.uiState.value is JourneyUiState.Error,
        )
    }

    // --- what the chart was loaded for ---

    @Test fun `load - the Ready state carries the context it was loaded for`() = runTest {
        val fake = FakeJourneyDataSource()
        fake.ownResult = PlayerFetchResult(listOf(snapshot(7, achievements = listOf(ach(1)))), emptyMap(), 0)
        val vm = viewModel(fake, creds())
        vm.start(null)
        advanceUntilIdle()
        vm.setMode(JourneyMode.GAME)
        vm.selectGame(7, "Game7")
        vm.load()
        advanceUntilIdle()

        val ready = vm.uiState.value as JourneyUiState.Ready
        assertEquals(JourneyMode.GAME, ready.context.mode)
        assertEquals(7, ready.context.gameId)
        assertTrue("the controls still describe this chart", ready.context.matches(vm.controls.value))

        // Change what the panel asks for and the loaded chart is no longer it.
        vm.selectGame(8, "Game8")
        assertFalse(ready.context.matches(vm.controls.value))
    }

    @Test fun `journeyContext - a span chart is described by its preset, not by the speed`() {
        // The panel opens on the 30-day span now, so the default-controls match is a D30 one.
        val context = JourneyContext(mode = JourneyMode.SPAN, spanPreset = SpanPreset.D30)
        assertTrue(context.matches(JourneyControls()))
        // View settings say nothing about what is in the frame.
        assertTrue(context.matches(JourneyControls(speed = 2f, followMode = true)))
        assertFalse(context.matches(JourneyControls(spanPreset = SpanPreset.Y1)))
        assertFalse(context.matches(JourneyControls(mode = JourneyMode.GAME, gameIdText = "7")))
    }

    @Test fun `journeyContext - a hand-picked range is described by both its ends`() {
        val context = JourneyContext(
            mode = JourneyMode.SPAN, spanPreset = SpanPreset.CUSTOM,
            customFromDaysAgo = 100, customToDaysAgo = 10,
        )
        val controls = JourneyControls(
            spanPreset = SpanPreset.CUSTOM, customFromDaysAgo = 100, customToDaysAgo = 10,
        )
        assertTrue(context.matches(controls))
        assertFalse(context.matches(controls.copy(customFromDaysAgo = 99)))
        assertFalse(context.matches(controls.copy(customToDaysAgo = 0)))
    }

    @Test fun `journeyContext - a single-game chart is described by the game`() {
        val context = JourneyContext(mode = JourneyMode.GAME, gameId = 7)
        assertTrue(context.matches(JourneyControls(mode = JourneyMode.GAME, gameIdText = "7")))
        assertFalse(context.matches(JourneyControls(mode = JourneyMode.GAME, gameIdText = "8")))
        assertFalse(context.matches(JourneyControls(mode = JourneyMode.GAME, gameIdText = "")))
    }

    // --- load() happy path with progress ---

    @Test fun `load - happy path emits Loading with progress labels then Ready`() = runTest {
        val fake = FakeJourneyDataSource()
        fake.ownResult = PlayerFetchResult(listOf(snapshot(1, achievements = listOf(ach(1)))), emptyMap(), 0)
        fake.ownProgressSteps = listOf(Triple("Game A", 1, 2), Triple("Game B", 2, 2))

        lateinit var vm: JourneyViewModel
        val startStates = mutableListOf<JourneyUiState>()
        val progressStates = mutableListOf<JourneyUiState>()
        fake.onFetchOwnStart = { startStates += vm.uiState.value }
        fake.afterOwnProgress = { progressStates += vm.uiState.value }

        vm = viewModel(fake, creds())
        vm.setSpanPreset(SpanPreset.ALL)
        vm.start(null)
        advanceUntilIdle()

        vm.load()
        advanceUntilIdle()

        assertEquals(1, startStates.size)
        assertEquals(JourneyUiState.Loading(PREPARING_LABEL, 0, 0), startStates[0])

        assertEquals(2, progressStates.size)
        assertEquals(JourneyUiState.Loading("Fetching 1 of 2: Game A", 1, 2), progressStates[0])
        assertEquals(JourneyUiState.Loading("Fetching 2 of 2: Game B", 2, 2), progressStates[1])

        assertTrue("expected Ready, was ${vm.uiState.value}", vm.uiState.value is JourneyUiState.Ready)
    }

    /**
     * The loading screen draws a fixed heading with the step's label under it, so a step that says
     * what the heading already says is one line printed twice. A single game reports no per-game
     * progress at all, so that duplicate was the whole of what the screen said while it loaded.
     */
    @Test fun `load - the loading label never repeats itself back to back`() = runTest {
        val fake = FakeJourneyDataSource()
        fake.ownResult = PlayerFetchResult(listOf(snapshot(1, achievements = listOf(ach(1)))), emptyMap(), 0)
        // The same game reported twice (a retried step) must not become two identical screens.
        fake.ownProgressSteps = listOf(
            Triple("Game A", 1, 2), Triple("Game A", 1, 2), Triple("Game B", 2, 2),
        )

        lateinit var vm: JourneyViewModel
        val seen = mutableListOf<JourneyUiState>()
        fake.onFetchOwnStart = { seen += vm.uiState.value }
        fake.afterOwnProgress = { seen += vm.uiState.value }

        vm = viewModel(fake, creds())
        vm.setSpanPreset(SpanPreset.ALL)
        vm.start(null)
        advanceUntilIdle()
        vm.load()
        advanceUntilIdle()

        assertEquals(4, seen.size)
        assertEquals(
            listOf(PREPARING_LABEL, "Fetching 1 of 2: Game A", "Fetching 1 of 2: Game A", "Fetching 2 of 2: Game B"),
            seen.map { (it as JourneyUiState.Loading).label },
        )
        // Same instance, not merely an equal one: the repeated step published nothing at all, so no
        // collector of uiState ever saw a second identical loading screen.
        assertSame("a repeated step must not publish a second identical screen", seen[1], seen[2])
        assertTrue(
            "no consecutive pair may be a distinct state carrying the same label",
            seen.zipWithNext().none { (a, b) -> a !== b && a == b },
        )
    }

    /**
     * A single-game load reports no per-game progress: the repository has one game and never calls
     * back. Its one loading label must therefore not be the loading screen's own heading, which is
     * drawn above it - that duplicate was the whole of what the screen said while it loaded.
     */
    @Test fun `load - single game says one thing, and not the heading`() = runTest {
        val fake = FakeJourneyDataSource()
        fake.ownResult = PlayerFetchResult(listOf(snapshot(42, achievements = listOf(ach(1)))), emptyMap(), 0)
        fake.ownProgressSteps = emptyList()

        lateinit var vm: JourneyViewModel
        val seen = mutableListOf<JourneyUiState>()
        fake.onFetchOwnStart = { seen += vm.uiState.value }
        fake.afterOwnProgress = { seen += vm.uiState.value }

        vm = viewModel(fake, creds())
        vm.start(42)
        advanceUntilIdle()

        // One step, none of them done. The total is not decoration: without it the loading screen
        // has no denominator and falls back to Material's indeterminate bar, which invalidates draw
        // every vsync for the whole fetch.
        assertEquals(listOf(JourneyUiState.Loading(PREPARING_LABEL, 0, SINGLE_GAME_TOTAL)), seen)
        assertNotEquals(LOADING_TITLE, PREPARING_LABEL)
    }

    // --- overlapping load() calls: a newer load must win, not race ---

    @Test fun `load - a newer load cancels a slower stale load instead of racing it`() = runTest {
        val fake = FakeJourneyDataSource()
        val gate1 = CompletableDeferred<Unit>()
        val gate2 = CompletableDeferred<Unit>()
        fake.ownGates = mutableListOf(gate1, gate2)
        fake.ownResultsSequential = mutableListOf(
            PlayerFetchResult(listOf(snapshot(1, achievements = listOf(ach(1)))), emptyMap(), 0), // stale, span A
            PlayerFetchResult(listOf(snapshot(2, achievements = listOf(ach(2)))), emptyMap(), 0), // winner, span B
        )
        val vm = viewModel(fake, creds())
        vm.setSpanPreset(SpanPreset.ALL)
        vm.start(null)
        advanceUntilIdle()

        vm.load() // job1 (stale, span A): enters fetchOwn, suspends on gate1
        vm.load() // job2 (winner, span B): cancels job1 mid-suspension, enters fetchOwn, suspends on gate2

        // The winner finishes FIRST and publishes Ready for span B.
        gate2.complete(Unit)
        advanceUntilIdle()
        val afterWinner = vm.uiState.value as JourneyUiState.Ready
        assertEquals("winner (span B) must be visible as soon as it lands", setOf(2), afterWinner.frame.main.games.keys)

        // The stale job then tries to finish LAST, after the winner already landed. If it weren't
        // actually cancelled (e.g. only loadJob?.cancel() without the suspension actually being
        // interrupted, or the ensureActive() checkpoints removed), this resumes job1, lets it
        // finish fetchOwn, and overwrites the winner's Ready state with span A's stale data.
        gate1.complete(Unit)
        advanceUntilIdle()

        assertEquals(2, fake.fetchOwnCalls)
        val finalState = vm.uiState.value as JourneyUiState.Ready
        assertEquals(
            "the stale job finishing last must NOT overwrite the winner's Ready state",
            setOf(2),
            finalState.frame.main.games.keys,
        )
    }

    @Test fun `load - clears a stale notice`() = runTest {
        val fake = FakeJourneyDataSource()
        fake.ownResult = PlayerFetchResult(listOf(snapshot(1, achievements = listOf(ach(1)))), emptyMap(), 0)
        val vm = viewModel(fake, creds())
        vm.start(null)
        advanceUntilIdle()
        vm.load()
        advanceUntilIdle()

        vm.addRival("me") // rejected as own name -> sets a notice
        advanceUntilIdle()
        assertNotNull(vm.notice.value)

        vm.load()
        advanceUntilIdle()
        assertNull(vm.notice.value)
    }

    // --- one unlock rule, no hardcore mode ---

    /**
     * The hardcore toggle is gone: a journey is everything the player earned, in either mode, and
     * an achievement earned both ways carries its hardcore date. Filtering the softcore-only
     * unlocks out was the only thing the toggle ever did, and it made the chart tell less than the
     * truth about the same journey.
     */
    @Test fun `load - includes softcore and hardcore unlocks alike`() = runTest {
        val fake = FakeJourneyDataSource()
        fake.ownResult = PlayerFetchResult(
            listOf(snapshot(1, achievements = listOf(ach(1, hardcore = false), ach(2, hardcore = true)))),
            emptyMap(), 0,
        )
        val vm = viewModel(fake, creds())
        vm.setSpanPreset(SpanPreset.ALL)
        vm.start(null)
        advanceUntilIdle()
        vm.load()
        advanceUntilIdle()

        val ready = vm.uiState.value as JourneyUiState.Ready
        assertEquals(2, ready.frame.main.unlocks.size)
        assertEquals(listOf(false, true), ready.frame.main.unlocks.map { it.hardcore })
        assertEquals(1, fake.fetchOwnCalls)
    }

    // --- badges, awards and the width of the timeline ---

    /**
     * There used to be a Badges/Awards toggle, then an "auto" mode that dropped every badge past
     * 120 unlocks, then a density rule that fell back to awards-only. All three are gone: the icon
     * budget, the width factor and the grid cap are one system, and no journey has its badges taken
     * away wholesale any more. This fixture sits exactly on the budget's own unlock threshold (300,
     * inclusive), so it keeps the default 200-icon cap.
     */
    @Test fun `a big journey draws badges and budgets the icons`() = runTest {
        val achievements = (1..300).map { ach(it, earnedMillisAgo = it.toLong() * 1000) }
        val fake = FakeJourneyDataSource()
        fake.ownResult = PlayerFetchResult(listOf(snapshot(1, achievements = achievements)), emptyMap(), 0)
        val vm = viewModel(fake, creds())
        vm.setSpanPreset(SpanPreset.ALL)
        vm.start(null)
        advanceUntilIdle()
        vm.load()
        advanceUntilIdle()

        val ready = vm.uiState.value as JourneyUiState.Ready
        assertEquals(300, ready.frame.main.unlocks.size)
        // 200 icons is more than two screenfuls, so the timeline is widened to carry them.
        assertEquals(IconBudget.TOTAL_ICONS, IconBudget.budgetFor(ready.frame))
        assertEquals(LayoutEngine.W * 3f, ready.layout.width, 0.5f)
        // What reaches the stage is the stage's own capacity: this journey's 300 unlocks land in
        // five minutes of axis, and each division of the widened grid carries as many icons as
        // fit above it. The widened stage also earns a widened budget (UI feedback: the extended
        // view shows more badges), so the wide chart may out-draw the one-screenful base budget -
        // its ceiling is the width-scaled one.
        assertTrue("the chart drew nothing", ready.layout.nodes.isNotEmpty())
        assertTrue(
            "the width-scaled budget is the wide chart's ceiling",
            ready.layout.nodes.size <= IconBudget.budgetFor(ready.frame, widthFactor = 3f),
        )
        // The preview and the video always get the one-screenful chart of the same journey. Its
        // grid is coarser, so it draws fewer icons - a subset of the wide chart's, which is what
        // the preloaded bitmaps were sourced from.
        assertEquals(LayoutEngine.W, ready.compactLayout.width, 0.5f)
        assertTrue(ready.compactLayout.nodes.size <= ready.layout.nodes.size)
        val wideIds = ready.layout.nodes.map { it.unlock.id }.toSet()
        assertTrue(wideIds.containsAll(ready.compactLayout.nodes.map { it.unlock.id }))
    }

    /** One more unlock than the previous test crosses [IconBudget.LONG_MAIN_UNLOCKS]: the budget doubles. */
    @Test fun `a journey past the unlock threshold gets the expanded budget`() = runTest {
        val achievements = (1..301).map { ach(it, earnedMillisAgo = it.toLong() * 1000) }
        val fake = FakeJourneyDataSource()
        fake.ownResult = PlayerFetchResult(listOf(snapshot(1, achievements = achievements)), emptyMap(), 0)
        val vm = viewModel(fake, creds())
        vm.setSpanPreset(SpanPreset.ALL)
        vm.start(null)
        advanceUntilIdle()
        vm.load()
        advanceUntilIdle()

        val ready = vm.uiState.value as JourneyUiState.Ready
        assertEquals(301, ready.frame.main.unlocks.size)
        // The budget is now 400, so it drops none of the 301 unlocks; four screenfuls is 44
        // divisions, and the per-division height budget is what thins them from there.
        assertEquals(IconBudget.EXPANDED_ICONS, IconBudget.budgetFor(ready.frame))
        assertEquals(LayoutEngine.W * 4f, ready.layout.width, 0.5f)
        assertTrue("the chart drew nothing", ready.layout.nodes.isNotEmpty())
        // The compact chart of the same journey is rationed by eleven-ish divisions instead, so it
        // draws far fewer - a subset, which is what the wide layout's preloaded art covers.
        assertTrue(ready.compactLayout.nodes.size < ready.layout.nodes.size)
        assertTrue(
            ready.layout.nodes.map { it.unlock.id }.toSet()
                .containsAll(ready.compactLayout.nodes.map { it.unlock.id }),
        )
    }

    @Test fun `a small journey keeps the unstretched stage`() = runTest {
        val achievements = listOf(ach(1, earnedMillisAgo = 400 * DAY), ach(2, earnedMillisAgo = DAY))
        val fake = FakeJourneyDataSource()
        fake.ownResult = PlayerFetchResult(listOf(snapshot(1, achievements = achievements)), emptyMap(), 0)
        val vm = viewModel(fake, creds())
        vm.setSpanPreset(SpanPreset.ALL)
        vm.start(null)
        advanceUntilIdle()
        vm.load()
        advanceUntilIdle()

        val ready = vm.uiState.value as JourneyUiState.Ready
        assertEquals(2, ready.layout.nodes.size)
        assertEquals(LayoutEngine.W, ready.layout.width, 0.5f)
        // Nothing to compact: the same layout serves both.
        assertSame(ready.layout, ready.compactLayout)
    }

    /**
     * The journey that used to trip the awards-only fallback. There is no such fallback any more:
     * the grid cap draws ten icons per division of the axis instead of taking every badge away, so
     * even a 500-unlock burst is a chart with badges on it.
     */
    @Test fun `a burst too dense for four screenfuls still draws badges`() = runTest {
        val achievements = (1..500).map { ach(it, earnedMillisAgo = it.toLong() * 1000) }
        val fake = FakeJourneyDataSource()
        fake.ownResult = PlayerFetchResult(listOf(snapshot(1, achievements = achievements)), emptyMap(), 0)
        val vm = viewModel(fake, creds())
        vm.setSpanPreset(SpanPreset.ALL)
        vm.start(null)
        advanceUntilIdle()
        vm.load()
        advanceUntilIdle()

        val ready = vm.uiState.value as JourneyUiState.Ready
        assertTrue(ready.layout.nodes.isNotEmpty())
        // Badges to space out, so the timeline stretches to its limit to carry them.
        assertEquals(LayoutEngine.W * 4f, ready.layout.width, 0.5f)
        assertEquals(LayoutEngine.W, ready.compactLayout.width, 0.5f)
    }

    /**
     * Compare mode draws no badges at all (two players' badges on one stage was never readable), so
     * it stays one screenful wide however dense either journey is.
     */
    @Test fun `a compare chart keeps the unstretched stage`() = runTest {
        val fake = FakeJourneyDataSource()
        fake.ownResult = PlayerFetchResult(
            listOf(snapshot(1, achievements = (1..300).map { ach(it, earnedMillisAgo = it.toLong() * 1000) })),
            emptyMap(), 0,
        )
        fake.rivalResults["Rival1"] = PlayerFetchResult(
            listOf(snapshot(2, achievements = (1..50).map { ach(it, earnedMillisAgo = it.toLong() * 1000) })),
            emptyMap(), 0,
        )
        val vm = viewModel(fake, creds())
        vm.setSpanPreset(SpanPreset.ALL)
        vm.start(null)
        advanceUntilIdle()
        vm.load()
        advanceUntilIdle()
        // Solo, this journey earns a 3x timeline.
        assertEquals(LayoutEngine.W * 3f, (vm.uiState.value as JourneyUiState.Ready).layout.width, 0.5f)

        vm.addRival("Rival1")
        vm.load()
        advanceUntilIdle()

        val ready = vm.uiState.value as JourneyUiState.Ready
        assertTrue("fixture must actually be in compare mode", ready.frame.rivals.single().unlocks.isNotEmpty())
        // Badges are drawn (one a day per player) and the stage is still one screenful: the extra
        // width exists to space out a dense badge set, and compare mode no longer has one.
        assertTrue("a compare chart draws badges", ready.layout.nodes.isNotEmpty())
        assertTrue("both players' badges", ready.layout.nodes.any { it.ownerName != null })
        assertEquals(LayoutEngine.W, ready.layout.width, 0.5f)
    }

    // --- rivals ---

    /**
     * The loading screen names the rival whose data is being fetched, and the game inside that
     * rival's own fetch that it has got to.
     *
     * This is the regression the owner reported after the restructuring waves: rival fetches were
     * handed the same progress callback the player's own data used, so a compare load read
     * "Fetching 4 of 9: Some Game" throughout and nothing anywhere said whose games those were.
     * Three players' worth of steps looked like one player's, and a stalled rival looked like a
     * stalled load.
     */
    @Test fun `load - the loading panel names the rival being fetched and the game inside it`() = runTest {
        val fake = FakeJourneyDataSource()
        fake.ownResult = PlayerFetchResult(listOf(snapshot(1, achievements = listOf(ach(1)))), emptyMap(), 0)
        fake.rivalResults["aria"] = PlayerFetchResult(listOf(snapshot(2, achievements = listOf(ach(2)))), emptyMap(), 0)
        fake.rivalProgressSteps = listOf(Triple("Sonic", 1, 2), Triple("Streets of Rage", 2, 2))
        val labels = mutableListOf<String>()
        lateinit var vm: JourneyViewModel
        fake.afterRivalProgress = { (vm.uiState.value as? JourneyUiState.Loading)?.let { labels += it.label } }
        vm = viewModel(fake, creds())
        vm.setSpanPreset(SpanPreset.ALL)
        vm.start(null)
        advanceUntilIdle()
        vm.addRival("aria")
        vm.load()
        advanceUntilIdle()

        assertEquals(
            listOf("Fetching aria: Sonic (1/2)", "Fetching aria: Streets of Rage (2/2)"),
            labels,
        )
        assertTrue(vm.uiState.value is JourneyUiState.Ready)
    }

    /**
     * A GAME-mode rival reports no per-game progress at all (there is one game, and the repository
     * does not call back for it), so the label named before the fetch starts is the whole of what
     * that step has to say - and it still carries a denominator, which is what keeps the panel off
     * Material's indeterminate bar and its every-vsync invalidation.
     */
    @Test fun `load - a single-game rival fetch still names the rival, with a determinate bar`() = runTest {
        val fake = FakeJourneyDataSource()
        fake.ownResult = PlayerFetchResult(listOf(snapshot(9, achievements = listOf(ach(1)))), emptyMap(), 0)
        fake.rivalResults["aria"] = PlayerFetchResult(listOf(snapshot(9, achievements = listOf(ach(2)))), emptyMap(), 0)
        fake.rivalGates["aria"] = CompletableDeferred()
        val vm = viewModel(fake, creds())
        vm.start(null)
        advanceUntilIdle()
        vm.setMode(JourneyMode.GAME)
        vm.selectGame(9, "Game9")
        vm.addRival("aria")
        vm.load()
        advanceUntilIdle()

        val loading = vm.uiState.value as JourneyUiState.Loading
        assertEquals("Fetching aria", loading.label)
        assertEquals("a bar with a denominator draws statically", SINGLE_GAME_TOTAL, loading.total)

        fake.rivalGates["aria"]!!.complete(Unit)
        advanceUntilIdle()
        assertTrue(vm.uiState.value is JourneyUiState.Ready)
    }

    /**
     * The player's own single-game step is named after the game the picker chose, and finishes on a
     * full bar rather than jumping from zero straight into the player.
     */
    @Test fun `load - a single-game load names the picked game and completes its one step`() = runTest {
        val fake = FakeJourneyDataSource()
        fake.ownResult = PlayerFetchResult(listOf(snapshot(9, achievements = listOf(ach(1)))), emptyMap(), 0)
        fake.ownGates = mutableListOf(CompletableDeferred())
        val vm = viewModel(fake, creds())
        vm.start(null)
        advanceUntilIdle()
        vm.setMode(JourneyMode.GAME)
        vm.selectGame(9, "Game9")
        vm.load()
        advanceUntilIdle()

        val loading = vm.uiState.value as JourneyUiState.Loading
        assertEquals("Fetching Game9", loading.label)
        assertEquals(0, loading.done)
        assertEquals(SINGLE_GAME_TOTAL, loading.total)

        fake.ownGates[0].complete(Unit)
        advanceUntilIdle()
        assertTrue(vm.uiState.value is JourneyUiState.Ready)
    }

    @Test fun `load - a rival's fetch failure keeps the roster entry with an error flag`() = runTest {
        val fake = FakeJourneyDataSource()
        fake.ownResult = PlayerFetchResult(listOf(snapshot(1, achievements = listOf(ach(1)))), emptyMap(), 0)
        fake.rivalExceptions["Rival1"] = RaJourneyException("rival down")
        val vm = viewModel(fake, creds())
        vm.setSpanPreset(SpanPreset.ALL)
        vm.start(null)
        advanceUntilIdle()

        vm.addRival("Rival1")
        vm.load()
        advanceUntilIdle()

        val ready = vm.uiState.value as JourneyUiState.Ready
        assertEquals(listOf("Rival1"), ready.rivalErrors)
        assertEquals("the chip stays on the panel too", listOf("Rival1"), vm.roster.value)
        val rivalJourney = ready.frame.rivals.single { it.name == "Rival1" }
        assertTrue(rivalJourney.error)
        assertTrue(rivalJourney.unlocks.isEmpty())
    }

    /**
     * The roster is committed before the rank fetch and the relayout that follow it, and the
     * catch-all below them was written for the shape this function had when nothing had been
     * committed by the time it could run. A rank lookup that throws - which is a real surface, the
     * repository guards only the network call inside it - used to run that catch and append the
     * same name a second time: one player, two curves, two chips, and two of the three slots the
     * GAME cap allows.
     */
    @Test fun `load - a rank lookup that throws leaves one roster entry, not an errored second`() = runTest {
        val fake = FakeJourneyDataSource()
        fake.ownResult = PlayerFetchResult(listOf(snapshot(9, achievements = listOf(ach(1)))), emptyMap(), 0)
        fake.rivalResults["aria"] = PlayerFetchResult(listOf(snapshot(9, achievements = listOf(ach(2)))), emptyMap(), 0)
        fake.gameRankExceptions["aria"] = IllegalStateException("keystore read failed")
        val vm = viewModel(fake, creds())
        vm.start(null)
        advanceUntilIdle()
        vm.setMode(JourneyMode.GAME)
        vm.selectGame(9, "Game9")
        vm.addRival("aria")
        vm.load()
        advanceUntilIdle()

        val ready = vm.uiState.value as JourneyUiState.Ready
        assertEquals("one roster name is one roster entry", 1, ready.frame.rivals.count { it.name == "aria" })
        assertTrue("a missing rank is not the rival failing", ready.rivalErrors.isEmpty())
        val rival = ready.frame.rivals.single { it.name == "aria" }
        assertFalse(rival.error)
        assertTrue("the data that did arrive is still on the chart", rival.unlocks.isNotEmpty())
        assertNull("no rank arrived, so none is claimed", ready.playerRanks["aria"])
    }

    /**
     * The same guarantee from the other side: the cap counts players, and a duplicated entry used
     * to burn a slot per failure until the roster looked full after two adds.
     */
    @Test fun `load - a rank lookup that throws does not burn a cap slot`() = runTest {
        val fake = FakeJourneyDataSource()
        fake.ownResult = PlayerFetchResult(listOf(snapshot(9, achievements = listOf(ach(1)))), emptyMap(), 0)
        fake.rivalResults["aria"] = PlayerFetchResult(listOf(snapshot(9, achievements = listOf(ach(2)))), emptyMap(), 0)
        fake.gameRankExceptions["aria"] = IllegalStateException("keystore read failed")
        val vm = viewModel(fake, creds())
        vm.start(null)
        advanceUntilIdle()
        vm.setMode(JourneyMode.GAME)
        vm.selectGame(9, "Game9")
        vm.addRival("aria")
        vm.load()
        advanceUntilIdle()

        val ready = vm.uiState.value as JourneyUiState.Ready
        assertEquals(listOf("aria"), ready.frame.rivals.map { it.name })
        assertNull("the slot was not burned", vm.notice.value)
    }

    /**
     * The load path makes the same promise `addRival` does. A rank is the last thing asked for
     * after everything the journey is made of has already arrived, so a lookup that blows up must
     * cost the closing card its line and nothing else - not the journey, and not a rival whose
     * data is sitting in hand.
     */
    @Test fun `load - a rank lookup that throws still publishes the journey and its rivals`() = runTest {
        val fake = FakeJourneyDataSource()
        fake.ownResult = PlayerFetchResult(listOf(snapshot(9, achievements = listOf(ach(1)))), emptyMap(), 0)
        fake.rivalResults["aria"] = PlayerFetchResult(listOf(snapshot(9, achievements = listOf(ach(2)))), emptyMap(), 0)
        val vm = viewModel(fake, creds())
        vm.start(null)
        advanceUntilIdle()
        vm.setMode(JourneyMode.GAME)
        vm.selectGame(9, "Game9")
        vm.addRival("aria")
        vm.load()
        advanceUntilIdle()

        // Both lookups fail on the reload: the signed-in player's and the roster rival's.
        fake.gameRankExceptions[""] = IllegalStateException("keystore read failed")
        fake.gameRankExceptions["aria"] = IllegalStateException("keystore read failed")
        vm.load()
        advanceUntilIdle()

        val ready = vm.uiState.value as JourneyUiState.Ready
        assertTrue("a missing rank is not the rival failing", ready.rivalErrors.isEmpty())
        val rival = ready.frame.rivals.single { it.name == "aria" }
        assertFalse(rival.error)
        assertTrue("the data that did arrive is still on the chart", rival.unlocks.isNotEmpty())
        assertNull("no rank arrived, so the closing card shows no chip", ready.rank)
        assertTrue("and no player claims one either", ready.playerRanks.isEmpty())
    }

    /**
     * A rival typed in lowercase is re-spelled the way RetroAchievements spells them, so their
     * avatar URL resolves.
     *
     * `media.retroachievements.org/UserPic/{name}.png` is case sensitive and every RA lookup is
     * not, which is why a rival added as "aria" charted perfectly and then wore the initial-letter
     * fallback disc at the end of their curve. Every surface that draws a face - the curve end, the
     * roster chip, the compare row - builds the URL from this name, so re-spelling it here fixes
     * all three, and the spelling comes off the leaderboard payload the compare row already fetches
     * rather than out of a request of its own.
     */
    @Test fun `load - a rival is re-spelled the way RetroAchievements spells them`() = runTest {
        val fake = FakeJourneyDataSource()
        fake.ownResult = PlayerFetchResult(listOf(snapshot(9, achievements = listOf(ach(1)))), emptyMap(), 0)
        fake.rivalResults["aria"] = PlayerFetchResult(listOf(snapshot(9, achievements = listOf(ach(2)))), emptyMap(), 0)
        fake.gameRank = 42
        fake.rivalGameRanks["aria"] = 7
        fake.canonicalNames["aria"] = "Aria"
        fake.canonicalNames["me"] = "Me"
        val vm = viewModel(fake, creds())
        vm.start(null)
        advanceUntilIdle()
        vm.setMode(JourneyMode.GAME)
        vm.selectGame(9, "Game9")
        vm.addRival("aria")
        vm.load()
        advanceUntilIdle()

        val ready = vm.uiState.value as JourneyUiState.Ready
        assertEquals("the chart draws the avatar off this name", listOf("Aria"), ready.frame.rivals.map { it.name })
        assertEquals("and so does the roster chip", listOf("Aria"), vm.roster.value)
        assertEquals(
            "the ranks move with the names, or the compare row loses them",
            mapOf("Me" to 42, "Aria" to 7),
            ready.playerRanks,
        )
        assertEquals("the signed-in player is re-spelled too", "Me", vm.userName.value)
        // Removing them still works under whatever the user typed.
        vm.removeRival("aria")
        assertEquals(emptyList<String>(), vm.roster.value)
    }

    /**
     * A roster name nobody has tried to fetch is absent from the frame, not present and flagged as
     * failed.
     *
     * It used to be the latter: `buildRivalJourney` had no third answer, so a name typed after the
     * last Load came back as an errored rival - a red chip and a "Could not fetch data for X"
     * notice for a player nothing had asked about. The panel styles those names as pending off the
     * roster instead (see `pendingRivals`).
     */
    @Test fun `a roster name added since the load is neither drawn nor errored`() = runTest {
        val fake = FakeJourneyDataSource()
        fake.ownResult = PlayerFetchResult(listOf(snapshot(1, achievements = listOf(ach(1)))), emptyMap(), 0)
        fake.rivalResults["aria"] = PlayerFetchResult(listOf(snapshot(2, achievements = listOf(ach(2)))), emptyMap(), 0)
        val vm = viewModel(fake, creds())
        vm.setSpanPreset(SpanPreset.ALL)
        vm.start(null)
        advanceUntilIdle()
        vm.addRival("aria")
        vm.load()
        advanceUntilIdle()

        // Swapped on the panel afterwards, with no Load behind the new name: the one slot is
        // freed and retaken, and the chart keeps drawing the rival it actually fetched.
        vm.removeRival("aria")
        advanceUntilIdle()
        vm.addRival("brix")
        advanceUntilIdle()

        val ready = vm.uiState.value as JourneyUiState.Ready
        assertEquals("the roster holds the new name", listOf("brix"), vm.roster.value)
        assertTrue("nothing was fetched for it yet", ready.frame.rivals.none { it.name == "brix" })
        assertTrue("and nothing is reported as failed", ready.rivalErrors.isEmpty())
    }

    /**
     * The empty-window notice names players the way the chart does. It used to be built from the
     * name the user typed while the fetch was still running, which by publication time may have
     * been re-spelled - so the notice named a player the roster no longer had.
     */
    @Test fun `load - the empty-window notice names the player the chart does`() = runTest {
        val fake = FakeJourneyDataSource()
        fake.ownResult = PlayerFetchResult(listOf(snapshot(1, achievements = listOf(ach(1)))), emptyMap(), 0)
        fake.rivalResults["aria"] = PlayerFetchResult(emptyList(), emptyMap(), 0)
        fake.summaries["aria"] = PlayerSummary("Aria", null)
        val vm = viewModel(fake, creds())
        vm.setSpanPreset(SpanPreset.ALL)
        vm.start(null)
        advanceUntilIdle()
        vm.addRival("aria")
        vm.load()
        advanceUntilIdle()

        assertEquals(emptyRivalNotice(listOf("Aria"), JourneyMode.SPAN), vm.notice.value)
        assertEquals(listOf("Aria"), vm.roster.value)
    }

    /**
     * A span is where the avatars were most broken, and where the old harvesting could not reach:
     * a span load asks for no leaderboard, so nothing it fetched echoed a name back and both the
     * runner and every rival end marker fell back to an initial. One `GetUserSummary` per player
     * fixes it, and brings the site rank a compare span's rows need along with it.
     */
    @Test fun `load - a span adopts the spellings and the site ranks from one summary per player`() = runTest {
        val fake = FakeJourneyDataSource()
        fake.ownResult = PlayerFetchResult(listOf(snapshot(1, achievements = listOf(ach(1)))), emptyMap(), 0)
        fake.rivalResults["aria"] = PlayerFetchResult(listOf(snapshot(2, achievements = listOf(ach(2)))), emptyMap(), 0)
        fake.summaries["me"] = PlayerSummary("Me", 4)
        fake.summaries["aria"] = PlayerSummary("Aria", 91)
        val vm = viewModel(fake, creds())
        vm.setSpanPreset(SpanPreset.ALL)
        vm.start(null)
        advanceUntilIdle()
        vm.addRival("aria")
        vm.load()
        advanceUntilIdle()

        val ready = vm.uiState.value as JourneyUiState.Ready
        assertEquals("the runner's avatar url is built from this", "Me", vm.userName.value)
        assertEquals(listOf("Aria"), ready.frame.rivals.map { it.name })
        assertEquals("and so is the roster chip's", listOf("Aria"), vm.roster.value)
        assertEquals(
            "a compare span's rows carry site ranks, since a window has no leaderboard",
            mapOf("Me" to 4, "Aria" to 91),
            ready.playerRanks,
        )
        assertEquals("one summary per player, no more", listOf("me", "aria"), fake.summaryCalls)
        assertEquals("and no game-rank lookup on a span", 0, fake.fetchGameRankCalls)
    }

    /** A solo span asks for the player's own summary too: their runner is an avatar as well. */
    @Test fun `load - a solo span still adopts the signed-in player's spelling`() = runTest {
        val fake = FakeJourneyDataSource()
        fake.ownResult = PlayerFetchResult(listOf(snapshot(1, achievements = listOf(ach(1)))), emptyMap(), 0)
        fake.summaries["me"] = PlayerSummary("Me", 4)
        val vm = viewModel(fake, creds())
        vm.setSpanPreset(SpanPreset.ALL)
        vm.start(null)
        advanceUntilIdle()
        vm.load()
        advanceUntilIdle()

        assertEquals("Me", vm.userName.value)
        assertEquals(listOf("me"), fake.summaryCalls)
        assertTrue(
            "nobody to compare with, so no rank rows",
            (vm.uiState.value as JourneyUiState.Ready).playerRanks.isEmpty(),
        )
    }

    /**
     * A summary that never arrived costs a face and a rank and nothing else: the name stays as it
     * was typed, the avatar falls back to an initial, and the journey is published regardless.
     */
    @Test fun `load - a summary that answers nothing leaves the typed name and no rank`() = runTest {
        val fake = FakeJourneyDataSource()
        fake.ownResult = PlayerFetchResult(listOf(snapshot(1, achievements = listOf(ach(1)))), emptyMap(), 0)
        fake.rivalResults["aria"] = PlayerFetchResult(listOf(snapshot(2, achievements = listOf(ach(2)))), emptyMap(), 0)
        val vm = viewModel(fake, creds())
        vm.setSpanPreset(SpanPreset.ALL)
        vm.start(null)
        advanceUntilIdle()
        vm.addRival("aria")
        vm.load()
        advanceUntilIdle()

        val ready = vm.uiState.value as JourneyUiState.Ready
        assertEquals("me", vm.userName.value)
        assertEquals(listOf("aria"), ready.frame.rivals.map { it.name })
        assertTrue("no rank is claimed for anybody", ready.playerRanks.isEmpty())
        assertTrue("and the rival is not an error", ready.rivalErrors.isEmpty())
    }

    /**
     * Only ever a change of case. A response naming somebody else - which would mean the API
     * answered about a different player - must not quietly swap who is on the chart.
     */
    @Test fun `load - a spelling that names a different player is ignored`() = runTest {
        val fake = FakeJourneyDataSource()
        fake.ownResult = PlayerFetchResult(listOf(snapshot(9, achievements = listOf(ach(1)))), emptyMap(), 0)
        fake.rivalResults["aria"] = PlayerFetchResult(listOf(snapshot(9, achievements = listOf(ach(2)))), emptyMap(), 0)
        fake.canonicalNames["aria"] = "SomebodyElse"
        val vm = viewModel(fake, creds())
        vm.start(null)
        advanceUntilIdle()
        vm.setMode(JourneyMode.GAME)
        vm.selectGame(9, "Game9")
        vm.addRival("aria")
        vm.load()
        advanceUntilIdle()

        assertEquals(listOf("aria"), vm.roster.value)
        assertEquals(listOf("aria"), (vm.uiState.value as JourneyUiState.Ready).frame.rivals.map { it.name })
    }

    /** The span modes' half of the same promise. */
    @Test fun `load - a site rank lookup that throws still publishes the journey`() = runTest {
        val fake = FakeJourneyDataSource()
        fake.ownResult = PlayerFetchResult(listOf(snapshot(9, achievements = listOf(ach(1)))), emptyMap(), 0)
        fake.siteRankException = IllegalStateException("keystore read failed")
        val vm = viewModel(fake, creds())
        vm.start(null)
        advanceUntilIdle()
        vm.setMode(JourneyMode.SPAN)
        vm.load()
        advanceUntilIdle()

        val ready = vm.uiState.value as JourneyUiState.Ready
        assertNull("no rank arrived, so the closing card shows no chip", ready.rank)
    }

    /**
     * A rival the API refused outright cannot be fixed by removing and re-adding them, and saying
     * so costs the reader a trip that ends in the same message.
     */
    @Test fun `load - a permanent refusal is not advertised as retryable`() = runTest {
        val fake = FakeJourneyDataSource()
        fake.ownResult = PlayerFetchResult(listOf(snapshot(1, achievements = listOf(ach(1)))), emptyMap(), 0)
        fake.rivalExceptions["Rival1"] = RaJourneyException("refused", statusCode = 404, retryable = false)
        val vm = viewModel(fake, creds())
        vm.setSpanPreset(SpanPreset.ALL)
        vm.start(null)
        advanceUntilIdle()

        vm.addRival("Rival1")
        vm.load()
        advanceUntilIdle()

        val ready = vm.uiState.value as JourneyUiState.Ready
        assertEquals(listOf("Rival1"), ready.rivalErrors)
        assertFalse(ready.rivalErrorsRetryable)
        assertEquals("Could not fetch data for Rival1.", rivalErrorText(ready.rivalErrors, ready.rivalErrorsRetryable))
    }

    @Test fun `load - a failure that could pass next time still offers the retry`() = runTest {
        val fake = FakeJourneyDataSource()
        fake.ownResult = PlayerFetchResult(listOf(snapshot(1, achievements = listOf(ach(1)))), emptyMap(), 0)
        fake.rivalExceptions["Rival1"] = RaJourneyException("timed out")
        val vm = viewModel(fake, creds())
        vm.setSpanPreset(SpanPreset.ALL)
        vm.start(null)
        advanceUntilIdle()

        vm.addRival("Rival1")
        vm.load()
        advanceUntilIdle()

        val ready = vm.uiState.value as JourneyUiState.Ready
        assertTrue(ready.rivalErrorsRetryable)
        assertTrue(
            rivalErrorText(ready.rivalErrors, ready.rivalErrorsRetryable).contains("Remove and re-add to retry"),
        )
    }

    @Test fun `load - a rival with nothing in the window keeps its chip and gets a notice`() = runTest {
        val fake = FakeJourneyDataSource()
        fake.ownResult = PlayerFetchResult(listOf(snapshot(1, achievements = listOf(ach(1)))), emptyMap(), 0)
        fake.rivalResults["Rival1"] = PlayerFetchResult(emptyList(), emptyMap(), 0)
        val vm = viewModel(fake, creds())
        vm.setSpanPreset(SpanPreset.ALL)
        vm.start(null)
        advanceUntilIdle()
        vm.addRival("Rival1")
        vm.load()
        advanceUntilIdle()

        assertEquals("Rival1 has no unlocks in this window.", vm.notice.value)
        val ready = vm.uiState.value as JourneyUiState.Ready
        // Not an error - the fetch was clean, the player simply did not play in this window - so
        // the chip stays without a failure flag and the curve is empty.
        assertTrue(ready.rivalErrors.isEmpty())
        assertEquals(listOf("Rival1"), vm.roster.value)
        assertTrue(ready.frame.rivals.single { it.name == "Rival1" }.unlocks.isEmpty())

        vm.dismissNotice()
        assertNull(vm.notice.value)
    }

    /**
     * Single-game mode has no window: JourneyBuilder passes windowFilter=false, so an empty rival
     * there means the rival has never earned anything for this game. The old copy still said
     * "no unlocks in this window", which read as false to anyone who knew the rival's history on
     * other games - the notice looked wrong even when the chart was right.
     */
    @Test fun `load - a game-mode rival with nothing says the game, not a window`() = runTest {
        val fake = FakeJourneyDataSource()
        fake.ownResult = PlayerFetchResult(listOf(snapshot(24, achievements = listOf(ach(1)))), emptyMap(), 0)
        fake.rivalResults["Rival1"] = PlayerFetchResult(emptyList(), emptyMap(), 0)
        val vm = viewModel(fake, creds())
        vm.setMode(JourneyMode.GAME)
        vm.selectGame(24, "Sonic 2")
        vm.start(null)
        advanceUntilIdle()
        vm.addRival("Rival1")
        vm.load()
        advanceUntilIdle()

        assertEquals("Rival1 has no achievements for this game.", vm.notice.value)
    }

    /** The plural form of both notices, pinned as copy rather than through the helper. */
    @Test fun `the empty-rival notices read as sentences in both modes`() {
        assertEquals(
            "aria, brix have no unlocks in this window.",
            emptyRivalNotice(listOf("aria", "brix"), JourneyMode.SPAN),
        )
        assertEquals(
            "aria, brix have no achievements for this game.",
            emptyRivalNotice(listOf("aria", "brix"), JourneyMode.GAME),
        )
    }

    @Test fun `addRival - duplicate name (case-insensitive) is rejected without a fetch`() = runTest {
        val fake = FakeJourneyDataSource()
        fake.ownResult = PlayerFetchResult(listOf(snapshot(1, achievements = listOf(ach(1)))), emptyMap(), 0)
        fake.rivalResults["Rival1"] = PlayerFetchResult(listOf(snapshot(2, achievements = listOf(ach(2)))), emptyMap(), 0)
        val vm = viewModel(fake, creds())
        vm.setSpanPreset(SpanPreset.ALL)
        vm.start(null)
        advanceUntilIdle()
        vm.addRival("Rival1")
        assertEquals(listOf("Rival1"), vm.roster.value)

        vm.addRival("rival1")
        assertEquals("a duplicate must not take a second slot", listOf("Rival1"), vm.roster.value)
        assertNotNull(vm.notice.value)

        vm.load()
        advanceUntilIdle()
        assertEquals("and only one player is ever fetched", 1, fake.fetchRivalCalls)
    }

    @Test fun `addRival - own name is rejected without a fetch`() = runTest {
        val fake = FakeJourneyDataSource()
        fake.ownResult = PlayerFetchResult(listOf(snapshot(1, achievements = listOf(ach(1)))), emptyMap(), 0)
        val vm = viewModel(fake, creds(username = "Me"))
        vm.setSpanPreset(SpanPreset.ALL)
        vm.start(null)
        advanceUntilIdle()
        vm.load()
        advanceUntilIdle()

        vm.addRival("me")
        advanceUntilIdle()
        assertEquals(0, fake.fetchRivalCalls)
        assertNotNull(vm.notice.value)
    }

    /**
     * Rival compare takes exactly one rival, in every mode: the chart, the two-line bar and the
     * colour picker are built for a duel. A second name is blocked with the notice, nothing is
     * fetched for it, and a mode switch never has a roster to trim.
     */
    @Test fun `addRival - rival compare takes one rival in every mode`() = runTest {
        val fake = FakeJourneyDataSource()
        fake.ownResult = PlayerFetchResult(listOf(snapshot(1, achievements = listOf(ach(1)))), emptyMap(), 0)
        fake.rivalResults["R1"] = PlayerFetchResult(listOf(snapshot(2, achievements = listOf(ach(2)))), emptyMap(), 0)
        fake.rivalResults["R2"] = PlayerFetchResult(listOf(snapshot(3, achievements = listOf(ach(3)))), emptyMap(), 0)
        val vm = viewModel(fake, creds())
        vm.setSpanPreset(SpanPreset.ALL)
        vm.start(null)
        advanceUntilIdle()
        vm.addRival("R1")
        assertEquals(listOf("R1"), vm.roster.value)

        vm.addRival("R2")
        assertEquals("the second name must be blocked", listOf("R1"), vm.roster.value)
        assertEquals(capNotice(), vm.notice.value)

        // The cap does not follow the mode: one rival on a single game too, and switching
        // modes with a rival set leaves the roster exactly as it was.
        vm.setMode(JourneyMode.GAME)
        assertEquals(listOf("R1"), vm.roster.value)
        vm.setMode(JourneyMode.SPAN)

        vm.load()
        advanceUntilIdle()
        assertEquals("only the one rival is fetched", 1, fake.fetchRivalCalls)
        assertEquals(1, (vm.uiState.value as JourneyUiState.Ready).frame.rivals.size)

        // Removing the rival frees the one slot.
        vm.removeRival("R1")
        vm.addRival("R2")
        assertEquals(listOf("R2"), vm.roster.value)
    }

    /**
     * The rival's colour is a preference: defaulted, clamped, and free of the roster.
     * Defaults are plain red for the rival and plain blue for the player: the theme-accent/adaptive default made the chart's colours drift with
     * whatever the library was showing, which reads as noise on a chart about one player.
     */
    @Test fun `setRivalColor clamps into the picker's choices`() = runTest {
        val vm = viewModel(FakeJourneyDataSource(), creds())

        assertEquals("default is the plain red", 0, vm.rivalColorIndex.value)
        assertEquals("player default is the plain blue", 2, vm.playerColorIndex.value)
        vm.setRivalColor(2)
        assertEquals(2, vm.rivalColorIndex.value)
        vm.setRivalColor(99)
        assertEquals(JourneyPalette.RIVAL_CHOICES.lastIndex, vm.rivalColorIndex.value)
        vm.setRivalColor(-1)
        assertEquals(0, vm.rivalColorIndex.value)
    }

    @Test fun `addRival - the signed-in player cannot be their own rival`() = runTest {
        val fake = FakeJourneyDataSource()
        val vm = viewModel(fake, creds())
        vm.start(null)
        advanceUntilIdle()

        vm.addRival("ME")

        assertEquals(emptyList<String>(), vm.roster.value)
        assertEquals(0, fake.fetchRivalCalls)
        assertEquals("ME is already in this journey.", vm.notice.value)
    }

    @Test fun `addRival - clears a stale notice on a successful add`() = runTest {
        val fake = FakeJourneyDataSource()
        fake.ownResult = PlayerFetchResult(listOf(snapshot(1, achievements = listOf(ach(1)))), emptyMap(), 0)
        fake.rivalResults["R1"] = PlayerFetchResult(listOf(snapshot(2, achievements = listOf(ach(2)))), emptyMap(), 0)
        val vm = viewModel(fake, creds())
        vm.setSpanPreset(SpanPreset.ALL)
        vm.start(null)
        advanceUntilIdle()
        vm.load()
        advanceUntilIdle()

        vm.addRival("me") // rejected as own name -> sets a notice
        advanceUntilIdle()
        assertNotNull(vm.notice.value)

        vm.addRival("R1") // proceeds past the guards -> must clear the stale notice
        advanceUntilIdle()
        assertNull(vm.notice.value)
    }

    @Test fun `removeRival - drops from roster and rebuilds`() = runTest {
        val fake = FakeJourneyDataSource()
        fake.ownResult = PlayerFetchResult(listOf(snapshot(1, achievements = listOf(ach(1)))), emptyMap(), 0)
        fake.rivalResults["Rival1"] = PlayerFetchResult(listOf(snapshot(2, achievements = listOf(ach(2)))), emptyMap(), 0)
        val vm = viewModel(fake, creds())
        vm.setSpanPreset(SpanPreset.ALL)
        vm.start(null)
        advanceUntilIdle()
        vm.addRival("Rival1")
        vm.load()
        advanceUntilIdle()
        assertTrue((vm.uiState.value as JourneyUiState.Ready).frame.rivals.any { it.name == "Rival1" })

        vm.removeRival("Rival1")
        assertEquals("the chip goes with it", emptyList<String>(), vm.roster.value)
        val ready = vm.uiState.value as JourneyUiState.Ready
        assertTrue(ready.frame.rivals.none { it.name == "Rival1" })
    }

    /**
     * The rank goes with the player. `clearRivalState` has always dropped it; `removeRival` left it
     * standing, so a name removed as "aria" and re-added as "Aria" was served the departed entry's
     * number until a refetch replaced it, and the map grew across add/remove cycles within a visit.
     */
    @Test fun `removeRival - drops the rival's rank with them`() = runTest {
        val fake = FakeJourneyDataSource()
        fake.ownResult = PlayerFetchResult(listOf(snapshot(9, achievements = listOf(ach(1)))), emptyMap(), 0)
        fake.rivalResults["aria"] = PlayerFetchResult(listOf(snapshot(9, achievements = listOf(ach(2)))), emptyMap(), 0)
        fake.gameRank = 42
        fake.rivalGameRanks["aria"] = 7
        val vm = viewModel(fake, creds())
        vm.start(null)
        advanceUntilIdle()
        vm.setMode(JourneyMode.GAME)
        vm.selectGame(9, "Game9")
        vm.addRival("aria")
        vm.load()
        advanceUntilIdle()
        assertEquals(7, (vm.uiState.value as JourneyUiState.Ready).playerRanks["aria"])

        vm.removeRival("ARIA")
        advanceUntilIdle()

        val ready = vm.uiState.value as JourneyUiState.Ready
        assertNull("a player who is not on the chart has no rank on it", ready.playerRanks["aria"])
        assertEquals("the signed-in player keeps theirs", 42, ready.playerRanks["me"])
    }

    /**
     * The rebuild runs on a dispatcher of its own while the roster keeps being mutated by other
     * coroutines - an add and a remove are separate jobs and neither cancels the other. Everything
     * a rebuild reads must therefore be snapshotted before the hop: here an add's rebuild is held
     * queued on the compute dispatcher, a rival is removed underneath it, and the state it
     * publishes must still describe the roster as it was when it started. Reading `rivalResults`
     * live instead would have rendered R1 as a failed rival.
     */
    @Test fun `rebuild uses inputs snapshotted before the compute hop`() = runTest {
        val fake = FakeJourneyDataSource()
        fake.ownResult = PlayerFetchResult(listOf(snapshot(1, achievements = listOf(ach(1, hardcore = true)))), emptyMap(), 0)
        fake.rivalResults["R1"] = PlayerFetchResult(listOf(snapshot(2, achievements = listOf(ach(2, hardcore = true)))), emptyMap(), 0)
        fake.rivalResults["R2"] = PlayerFetchResult(listOf(snapshot(3, achievements = listOf(ach(3, hardcore = true)))), emptyMap(), 0)

        // A queueing dispatcher, so a rebuild can be left pending while the roster changes.
        val vm = JourneyViewModel(fake, creds(), StandardTestDispatcher(testScheduler))
        vm.setSpanPreset(SpanPreset.ALL)
        vm.start(null)
        advanceUntilIdle()
        vm.addRival("R1")

        val seen = mutableListOf<JourneyUiState.Ready>()
        val collector = launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect { if (it is JourneyUiState.Ready) seen += it }
        }

        vm.load()               // snapshots the roster (R1), then parks on the compute dispatcher
        vm.removeRival("R1")    // mutates rivalNames/rivalResults underneath the pending rebuild
        advanceUntilIdle()
        collector.cancel()

        val fromSnapshot = seen[0]
        assertEquals(
            "the pending rebuild must publish the roster it snapshotted",
            listOf("R1"),
            fromSnapshot.frame.rivals.map { it.name },
        )
        val r1 = fromSnapshot.frame.rivals.single { it.name == "R1" }
        assertFalse("R1 was still in the snapshot, so it must not be rendered as failed", r1.error)

        val afterRemoval = seen.last()
        assertTrue("the later rebuild must see the removal", afterRemoval.frame.rivals.none { it.name == "R1" })
    }

    // --- empty / error ---

    @Test fun `load - zero unlocks in window yields Empty`() = runTest {
        val fake = FakeJourneyDataSource()
        fake.ownResult = PlayerFetchResult(emptyList(), emptyMap(), 0)
        val vm = viewModel(fake, creds())
        vm.start(null)
        advanceUntilIdle()
        vm.load()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue("expected Empty, was $state", state is JourneyUiState.Empty)
        assertEquals("No unlocks found here. Try another game or time span.", (state as JourneyUiState.Empty).reason)
    }

    // "Nothing came back" must not be reported as "nothing happened here": the first is a fetch
    // problem worth retrying, the second is a real answer about the window.
    @Test fun `load - zero unlocks with failed fetches yields a retryable Error, not Empty`() = runTest {
        val fake = FakeJourneyDataSource()
        fake.ownResult = PlayerFetchResult(emptyList(), emptyMap(), failedCount = 3)
        val vm = viewModel(fake, creds())
        vm.start(null)
        advanceUntilIdle()
        vm.load()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue("expected Error, was $state", state is JourneyUiState.Error)
        state as JourneyUiState.Error
        assertEquals("3 games couldn't be fetched. Try again.", state.message)
        assertTrue(state.retryable)
    }

    @Test fun `load - a single failed fetch is reported in the singular`() = runTest {
        val fake = FakeJourneyDataSource()
        fake.ownResult = PlayerFetchResult(emptyList(), emptyMap(), failedCount = 1)
        val vm = viewModel(fake, creds())
        vm.start(null)
        advanceUntilIdle()
        vm.load()
        advanceUntilIdle()

        assertEquals(
            "1 game couldn't be fetched. Try again.",
            (vm.uiState.value as JourneyUiState.Error).message,
        )
    }

    // Only RaJourneyException used to be caught, so a Room or Gson failure took the app down.
    @Test fun `load - an unmodelled failure yields a retryable Error instead of crashing`() = runTest {
        val fake = FakeJourneyDataSource()
        fake.ownThrowable = IllegalStateException("Room blew up")
        val vm = viewModel(fake, creds())
        vm.start(null)
        advanceUntilIdle()
        vm.load()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue("expected Error, was $state", state is JourneyUiState.Error)
        state as JourneyUiState.Error
        assertEquals("Something went wrong loading the journey. Try again.", state.message)
        assertTrue(state.retryable)

        fake.ownThrowable = null
        fake.ownResult = PlayerFetchResult(listOf(snapshot(1, achievements = listOf(ach(1)))), emptyMap(), 0)
        vm.retry()
        advanceUntilIdle()
        assertTrue(vm.uiState.value is JourneyUiState.Ready)
    }

    @Test fun `load - RaJourneyException yields retryable Error, retry() re-fetches`() = runTest {
        val fake = FakeJourneyDataSource()
        fake.ownException = RaJourneyException("Network down")
        val vm = viewModel(fake, creds())
        vm.start(null)
        advanceUntilIdle()
        vm.load()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue("expected Error, was $state", state is JourneyUiState.Error)
        state as JourneyUiState.Error
        assertEquals("Network down", state.message)
        assertTrue(state.retryable)
        assertEquals(1, fake.fetchOwnCalls)

        fake.ownException = null
        fake.ownResult = PlayerFetchResult(listOf(snapshot(1, achievements = listOf(ach(1)))), emptyMap(), 0)
        vm.retry()
        advanceUntilIdle()
        assertEquals(2, fake.fetchOwnCalls)
        assertTrue(vm.uiState.value is JourneyUiState.Ready)
    }

    /**
     * A permanently refused request. The data layer does not retry a 4xx that is not a 429, and
     * the screen used to show its raw "RetroAchievements API error 404" next to a Try again button
     * that could only fail again. Neither the code nor the button is any use to the reader.
     */
    private suspend fun TestScope.permanentError(exception: RaJourneyException): JourneyUiState.Error {
        val fake = FakeJourneyDataSource()
        fake.ownException = exception
        val vm = viewModel(fake, creds())
        vm.start(null)
        advanceUntilIdle()
        vm.load()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue("expected Error, was $state", state is JourneyUiState.Error)
        state as JourneyUiState.Error
        assertFalse("a request the API refused must not offer a retry", state.retryable)
        assertFalse("no status code in front of the reader", state.message.contains(Regex("\\d{3}")))
        return state
    }

    @Test fun `load - a rejected key sends the reader to Settings`() = runTest {
        val state = permanentError(RaJourneyException("RetroAchievements API error 401", retryable = false, statusCode = 401))
        assertEquals(PERMANENT_ERROR_KEY, state.message)
        assertEquals(
            PERMANENT_ERROR_KEY,
            permanentError(RaJourneyException("RetroAchievements API error 403", retryable = false, statusCode = 403)).message,
        )
    }

    /**
     * A 404 is a game the API does not have, not a key problem. Sending the reader to check a key
     * that is working costs them a trip and fixes nothing.
     */
    @Test fun `load - a refusal that is not about the key does not mention the key`() = runTest {
        val state = permanentError(RaJourneyException("RetroAchievements API error 404", retryable = false, statusCode = 404))
        assertEquals(PERMANENT_ERROR_GENERIC, state.message)
        assertFalse("a working key must not be blamed", state.message.contains("API key"))
    }

    /**
     * The data layer's own preconditions carry no status code and say exactly what is missing, so
     * their message is kept - they simply lose the retry that could never have helped.
     */
    @Test fun `load - a permanent failure of our own keeps its message`() = runTest {
        val state = permanentError(RaJourneyException("No RetroAchievements account configured", retryable = false))
        assertEquals("No RetroAchievements account configured", state.message)
    }


    /**
     * A failed load keeps the roster the user typed, and drops everything it fetched for it.
     *
     * The two used to go together: the roster belonged to the chart, so a failure took the names
     * with it and the reader had to retype every rival before pressing Retry. The names are the
     * panel's own state now; the results, the error flags and the ranks are one load's and go.
     */
    @Test fun `load - a failure keeps the roster and drops what it fetched for it`() = runTest {
        val fake = FakeJourneyDataSource()
        fake.ownResult = PlayerFetchResult(listOf(snapshot(1, achievements = listOf(ach(1)))), emptyMap(), 0)
        fake.rivalResults["aria"] = PlayerFetchResult(listOf(snapshot(2, achievements = listOf(ach(2)))), emptyMap(), 0)
        val vm = viewModel(fake, creds())
        vm.setSpanPreset(SpanPreset.ALL)
        vm.start(null)
        advanceUntilIdle()
        vm.addRival("aria")
        vm.load()
        advanceUntilIdle()
        assertTrue(vm.uiState.value is JourneyUiState.Ready)

        fake.ownException = RaJourneyException("Network down")
        vm.load()
        advanceUntilIdle()
        assertTrue(vm.uiState.value is JourneyUiState.Error)
        assertEquals("the typed roster survives the failure", listOf("aria"), vm.roster.value)

        // Retry: the same roster is fetched again, and the chart comes back whole.
        fake.ownException = null
        vm.retry()
        advanceUntilIdle()
        assertEquals(
            listOf("aria"),
            (vm.uiState.value as JourneyUiState.Ready).frame.rivals.map { it.name },
        )
    }

    /**
     * The rival cache is keyed on the request, not on the millis the request resolves to: those
     * come off the wall clock at each load, so a cache keyed on them was a guaranteed miss on
     * every second Load of the same preset.
     */
    @Test fun `load - rivals are fetched under the requested span, not the resolved bounds`() = runTest {
        val fake = FakeJourneyDataSource()
        fake.ownResult = PlayerFetchResult(listOf(snapshot(1, achievements = listOf(ach(1)))), emptyMap(), 0)
        fake.rivalResults["Rival1"] = PlayerFetchResult(listOf(snapshot(2, achievements = listOf(ach(2)))), emptyMap(), 0)
        val vm = viewModel(fake, creds())
        vm.setSpanPreset(SpanPreset.ALL)
        vm.start(null)
        advanceUntilIdle()
        vm.addRival("Rival1")
        vm.load()
        advanceUntilIdle()

        vm.load()
        advanceUntilIdle()
        assertEquals(listOf("ALL", "ALL"), fake.rivalSpanKeys)

        vm.setSpanPreset(SpanPreset.CUSTOM)
        vm.setCustomRange(fromDaysAgo = 30, toDaysAgo = 0)
        vm.load()
        advanceUntilIdle()
        assertEquals("a hand-picked range is keyed by both its ends", "CUSTOM:30:0", fake.rivalSpanKeys.last())
    }

    // --- GAME mode gameId parsing ---

    /**
     * GAME mode with nothing picked. The picker is the only way a game id is set now, so this is
     * what an unset one looks like: the mode toggled and Load pressed before choosing anything.
     */
    @Test fun `load - GAME mode with no game picked yields Error without fetching`() = runTest {
        val fake = FakeJourneyDataSource()
        val vm = viewModel(fake, creds())
        vm.start(null)
        advanceUntilIdle()
        vm.setMode(JourneyMode.GAME)
        vm.load()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue("expected Error, was $state", state is JourneyUiState.Error)
        state as JourneyUiState.Error
        // Names the picker, which is the only way to choose a game now: the manual id field went
        // when the picker landed, so the old copy asked for something the panel cannot enter.
        assertEquals(NO_GAME_PICKED, state.message)
        assertFalse(state.retryable)
        assertEquals(0, fake.fetchOwnCalls)
    }

    // --- controls: span preset / mode changes never fetch ---

    @Test fun `setSpanPreset and setMode only update controls, no fetch until load()`() = runTest {
        val fake = FakeJourneyDataSource()
        fake.ownResult = PlayerFetchResult(listOf(snapshot(1, achievements = listOf(ach(1)))), emptyMap(), 0)
        val vm = viewModel(fake, creds())
        vm.start(null)
        advanceUntilIdle()
        vm.load()
        advanceUntilIdle()
        assertEquals(1, fake.fetchOwnCalls)

        vm.setSpanPreset(SpanPreset.Y1)
        vm.setMode(JourneyMode.GAME)
        vm.selectGame(7, "Game7")
        assertEquals("controls-only changes must not fetch", 1, fake.fetchOwnCalls)
        assertTrue("state stays as-is until load()", vm.uiState.value is JourneyUiState.Ready)
    }

    /**
     * The follow camera is a view setting: it says nothing about which unlocks are in the frame,
     * so it must neither fetch nor rebuild, and it must outlive the player it was toggled in.
     */
    @Test fun `setFollowMode - pins the choice in controls without fetching or rebuilding`() = runTest {
        val fake = FakeJourneyDataSource()
        fake.ownResult = PlayerFetchResult(listOf(snapshot(1, achievements = listOf(ach(1)))), emptyMap(), 0)
        val vm = viewModel(fake, creds())
        vm.start(null)
        advanceUntilIdle()
        vm.load()
        advanceUntilIdle()
        val ready = vm.uiState.value
        assertNull("nothing has been chosen yet", vm.controls.value.followMode)

        vm.setFollowMode(true)
        advanceUntilIdle()
        assertEquals(true, vm.controls.value.followMode)

        vm.setFollowMode(false)
        advanceUntilIdle()
        assertEquals(false, vm.controls.value.followMode)
        assertEquals("a view setting must not fetch", 1, fake.fetchOwnCalls)
        assertSame("a view setting must not rebuild the chart", ready, vm.uiState.value)
    }

    /**
     * A partial fetch failure still renders a chart, so the count has to survive onto the state:
     * the player is the only surface left that can admit what is missing from it.
     */
    @Test fun `load - a partial failure renders the chart and carries the failed count`() = runTest {
        val fake = FakeJourneyDataSource()
        fake.ownResult = PlayerFetchResult(
            listOf(snapshot(1, achievements = listOf(ach(1)))), emptyMap(), failedCount = 3,
        )
        val vm = viewModel(fake, creds())
        vm.start(null)
        advanceUntilIdle()
        vm.load()
        advanceUntilIdle()

        val ready = vm.uiState.value as JourneyUiState.Ready
        assertEquals(3, ready.failedCount)
    }

    // --- the end state's rank card: which rank, and best effort either way ---

    @Test fun `load - GAME mode fetches the game rank and carries it into Ready`() = runTest {
        val fake = FakeJourneyDataSource()
        fake.ownResult = PlayerFetchResult(listOf(snapshot(9, achievements = listOf(ach(1)))), emptyMap(), 0)
        fake.gameRank = 42
        val vm = viewModel(fake, creds())
        vm.start(null)
        advanceUntilIdle()
        vm.setMode(JourneyMode.GAME)
        vm.selectGame(9, "Game9")
        vm.load()
        advanceUntilIdle()

        assertEquals(1, fake.fetchGameRankCalls)
        assertEquals(9, fake.lastFetchGameRankGameId)
        assertEquals(0, fake.siteRankCalls)
        assertEquals(RankChip(RANK_LABEL, "#42"), (vm.uiState.value as JourneyUiState.Ready).rank)
    }

    /**
     * A single-game compare chart puts every player's standing on that game's leaderboard in one
     * row, so every player's rank is asked for: the signed-in player's (which the closing card
     * uses too) and each rival's, by name.
     */
    @Test fun `load - GAME mode fetches a rank for every player on the chart`() = runTest {
        val fake = FakeJourneyDataSource()
        fake.ownResult = PlayerFetchResult(listOf(snapshot(9, achievements = listOf(ach(1)))), emptyMap(), 0)
        fake.rivalResults["aria"] = PlayerFetchResult(listOf(snapshot(9, achievements = listOf(ach(2)))), emptyMap(), 0)
        fake.gameRank = 42
        fake.rivalGameRanks["aria"] = 7
        val vm = viewModel(fake, creds())
        vm.start(null)
        advanceUntilIdle()
        vm.setMode(JourneyMode.GAME)
        vm.selectGame(9, "Game9")
        vm.addRival("aria")
        vm.load()
        advanceUntilIdle()

        val ready = vm.uiState.value as JourneyUiState.Ready
        assertEquals(mapOf("me" to 42, "aria" to 7), ready.playerRanks)
        assertTrue("a rival's rank is asked for by name", fake.gameRankUsers.contains("aria"))
    }

    /** A span has no leaderboard for "what these players did between two dates", so nobody is ranked. */
    @Test fun `load - a span carries no per-player ranks`() = runTest {
        val fake = FakeJourneyDataSource()
        fake.ownResult = PlayerFetchResult(listOf(snapshot(1, achievements = listOf(ach(1)))), emptyMap(), 0)
        fake.rivalResults["aria"] = PlayerFetchResult(listOf(snapshot(1, achievements = listOf(ach(2)))), emptyMap(), 0)
        val vm = viewModel(fake, creds())
        vm.start(null)
        advanceUntilIdle()
        vm.load()
        advanceUntilIdle()
        vm.addRival("aria")
        advanceUntilIdle()

        assertEquals(emptyMap<String, Int>(), (vm.uiState.value as JourneyUiState.Ready).playerRanks)
        assertEquals(0, fake.fetchGameRankCalls)
    }

    /** A span has no single game to be ranked on, so the card names where the player stands on the site. */
    @Test fun `load - a preset span fetches the site rank instead of a game rank`() = runTest {
        val fake = FakeJourneyDataSource()
        fake.ownResult = PlayerFetchResult(listOf(snapshot(1, achievements = listOf(ach(1)))), emptyMap(), 0)
        fake.siteRank = 1234
        val vm = viewModel(fake, creds())
        vm.start(null)
        advanceUntilIdle()
        vm.load()
        advanceUntilIdle()

        assertEquals(0, fake.fetchGameRankCalls)
        assertEquals(1, fake.siteRankCalls)
        assertEquals(RankChip(RANK_LABEL, "#1234"), (vm.uiState.value as JourneyUiState.Ready).rank)
    }

    @Test fun `load - all time is a preset and gets the site rank too`() = runTest {
        val fake = FakeJourneyDataSource()
        fake.ownResult = PlayerFetchResult(listOf(snapshot(1, achievements = listOf(ach(1)))), emptyMap(), 0)
        fake.siteRank = 7
        val vm = viewModel(fake, creds())
        vm.start(null)
        advanceUntilIdle()
        vm.setSpanPreset(SpanPreset.ALL)
        vm.load()
        advanceUntilIdle()

        assertEquals(RankChip(RANK_LABEL, "#7"), (vm.uiState.value as JourneyUiState.Ready).rank)
    }

    /** Nothing ranks "what this player did between two dates I picked", so the card stays away. */
    @Test fun `load - a custom range gets no rank card at all`() = runTest {
        val fake = FakeJourneyDataSource()
        fake.ownResult = PlayerFetchResult(listOf(snapshot(1, achievements = listOf(ach(1)))), emptyMap(), 0)
        fake.siteRank = 7
        val vm = viewModel(fake, creds())
        vm.start(null)
        advanceUntilIdle()
        vm.setSpanPreset(SpanPreset.CUSTOM)
        vm.load()
        advanceUntilIdle()

        assertEquals(0, fake.siteRankCalls)
        assertNull((vm.uiState.value as JourneyUiState.Ready).rank)
    }

    /**
     * Which rank the card shows follows the chart on screen, not the dropdown. Changing the preset
     * without pressing Load leaves a span chart up, and a card that switched to "no rank" for it
     * would be describing a window nothing has been fetched for.
     */
    @Test fun `the rank card follows the loaded window, not the live preset`() = runTest {
        val fake = FakeJourneyDataSource()
        fake.ownResult = PlayerFetchResult(listOf(snapshot(1, achievements = listOf(ach(1)))), emptyMap(), 0)
        fake.siteRank = 5
        val vm = viewModel(fake, creds())
        vm.start(null)
        advanceUntilIdle()
        vm.load()
        advanceUntilIdle()

        vm.setSpanPreset(SpanPreset.CUSTOM)
        vm.addRival("rival")            // any rebuild, without a fetch
        advanceUntilIdle()

        assertEquals(RankChip(RANK_LABEL, "#5"), (vm.uiState.value as JourneyUiState.Ready).rank)
    }

    /** No rank is a card that is simply absent, never a load that failed. */
    @Test fun `load - a missing game rank still renders the journey`() = runTest {
        val fake = FakeJourneyDataSource()
        fake.ownResult = PlayerFetchResult(listOf(snapshot(9, achievements = listOf(ach(1)))), emptyMap(), 0)
        fake.gameRank = null
        val vm = viewModel(fake, creds())
        vm.start(null)
        advanceUntilIdle()
        vm.setMode(JourneyMode.GAME)
        vm.selectGame(9, "Game9")
        vm.load()
        advanceUntilIdle()

        val ready = vm.uiState.value as JourneyUiState.Ready
        assertNull(ready.rank)
    }

    // --- played-games picker ---

    private fun completionRow(gameId: Int, title: String = "Game$gameId") = CompletionRow(
        gameId = gameId, title = title, iconPath = "/icon$gameId.png",
        numAwarded = 1, numAwardedHardcore = 0,
        mostRecentAwardedDate = null, highestAwardKind = null, highestAwardDate = null,
    )

    // --- an account switch, which is not the same event as a visit ---
    //
    // This ViewModel is resolved against the activity, and the RA account is changed in Settings
    // without the activity being recreated. resetSession drops what a *visit* accumulated; the
    // picker's played-games list and the game the panel has selected deliberately outlive a visit,
    // and both are one account's history.

    /** Credentials whose account the test can change between visits, the way Settings does. */
    private fun switchableCreds(name: () -> String): RetroAchievementsCredentialsRepository =
        mock<RetroAchievementsCredentialsRepository> {
            on { hasCustomApiKey() } doReturn true
            onBlocking { getCredentials() } doSuspendableAnswer { RaCredentials(name(), "key") }
        }

    @Test fun `start - a different account drops the picker's list and its chosen game`() = runTest {
        var user = "alice"
        val fake = FakeJourneyDataSource()
        fake.playedGamesResult = listOf(completionRow(1), completionRow(2))
        val vm = viewModel(fake, switchableCreds { user })
        vm.start(null)
        advanceUntilIdle()
        vm.loadPlayedGames()
        advanceUntilIdle()
        vm.selectGame(9, "Sonic 3")
        assertEquals(2, vm.playedGames.value.size)
        assertEquals("Sonic 3", vm.controls.value.selectedGameTitle)

        user = "bob"
        vm.start(null)
        advanceUntilIdle()

        assertEquals("bob", vm.userName.value)
        assertTrue("the picker must not open on the departed account's library", vm.playedGames.value.isEmpty())
        assertFalse(vm.playedGamesError.value)
        assertNull("the button must not name a game this account may never have played", vm.controls.value.selectedGameTitle)
        assertEquals("", vm.controls.value.gameIdText)
    }

    /**
     * And the ordinary visit keeps them: the list is what makes the picker open instantly the
     * second time, and re-picking the same game every visit is not an improvement.
     */
    @Test fun `start - the same account keeps its games and its selection`() = runTest {
        val fake = FakeJourneyDataSource()
        fake.playedGamesResult = listOf(completionRow(1), completionRow(2))
        val vm = viewModel(fake, switchableCreds { "alice" })
        vm.start(null)
        advanceUntilIdle()
        vm.loadPlayedGames()
        advanceUntilIdle()
        vm.selectGame(9, "Sonic 3")
        vm.setSpanPreset(SpanPreset.Y1)

        vm.start(null)
        advanceUntilIdle()

        assertEquals(2, vm.playedGames.value.size)
        assertEquals("Sonic 3", vm.controls.value.selectedGameTitle)
        assertEquals("9", vm.controls.value.gameIdText)
        assertEquals("a window of time is the user's choice, not the account's", SpanPreset.Y1, vm.controls.value.spanPreset)
    }

    /**
     * Signing out is a switch too: the departed account's games must not still be sitting in the
     * picker behind a locked screen.
     */
    @Test fun `start - clearing the account drops its games as well`() = runTest {
        var user = "alice"
        val fake = FakeJourneyDataSource()
        fake.playedGamesResult = listOf(completionRow(1))
        val vm = viewModel(fake, switchableCreds { user })
        vm.start(null)
        advanceUntilIdle()
        vm.loadPlayedGames()
        advanceUntilIdle()
        assertEquals(1, vm.playedGames.value.size)

        user = ""
        vm.start(null)
        advanceUntilIdle()

        assertEquals(JourneyUiState.Locked(LockedReason.NO_ACCOUNT), vm.uiState.value)
        assertTrue(vm.playedGames.value.isEmpty())
    }

    @Test fun `loadPlayedGames - populates playedGames from the repository`() = runTest {
        val fake = FakeJourneyDataSource()
        fake.playedGamesResult = listOf(completionRow(1), completionRow(2))
        val vm = viewModel(fake, creds())

        vm.loadPlayedGames()
        advanceUntilIdle()

        assertEquals(fake.playedGamesResult, vm.playedGames.value)
        assertFalse(vm.playedGamesError.value)
        assertEquals(1, fake.playedGamesCalls)
    }

    @Test fun `loadPlayedGames - a call already in flight is not duplicated`() = runTest {
        val fake = FakeJourneyDataSource()
        fake.playedGamesGate = CompletableDeferred()
        fake.playedGamesResult = listOf(completionRow(1))
        val vm = viewModel(fake, creds())

        vm.loadPlayedGames() // enters playedGames(), suspends on the gate
        vm.loadPlayedGames() // must be a no-op: the first call is still in flight

        assertEquals(1, fake.playedGamesCalls)

        fake.playedGamesGate!!.complete(Unit)
        advanceUntilIdle()
        assertEquals(listOf(completionRow(1)), vm.playedGames.value)
    }

    @Test fun `loadPlayedGames - failure sets the error flag and keeps whatever was cached`() = runTest {
        val fake = FakeJourneyDataSource()
        fake.playedGamesResult = listOf(completionRow(1))
        val vm = viewModel(fake, creds())
        vm.loadPlayedGames()
        advanceUntilIdle()
        assertEquals(1, vm.playedGames.value.size)

        fake.playedGamesException = RaJourneyException("network down")
        vm.loadPlayedGames()
        advanceUntilIdle()

        assertTrue(vm.playedGamesError.value)
        // Silent fallback: the previously loaded list stays on screen.
        assertEquals(1, vm.playedGames.value.size)
    }

    @Test fun `selectGame - sets gameIdText and the display title, never fetches played games`() = runTest {
        val fake = FakeJourneyDataSource()
        val vm = viewModel(fake, creds())

        vm.selectGame(1234, "Chrono Trigger")

        assertEquals("1234", vm.controls.value.gameIdText)
        assertEquals("Chrono Trigger", vm.controls.value.selectedGameTitle)
        assertEquals(0, fake.playedGamesCalls)
    }

    /** Picking a second game replaces the first, id and display title together. */
    @Test fun `selectGame - a later pick replaces the earlier one outright`() = runTest {
        val fake = FakeJourneyDataSource()
        val vm = viewModel(fake, creds())
        vm.selectGame(1234, "Chrono Trigger")
        assertEquals("Chrono Trigger", vm.controls.value.selectedGameTitle)

        vm.selectGame(5678, "Secret of Mana")

        assertEquals("5678", vm.controls.value.gameIdText)
        assertEquals("Secret of Mana", vm.controls.value.selectedGameTitle)
    }

    // --- backdrop dim ---
    //
    // The stored value is the art's ALPHA percent; the slider speaks dim = 100 - alpha.
    // Dim defaults to 85% and moves inside 40..95%, so alpha defaults to 15 inside 5..60.

    @Test fun `backdrop dim defaults to an 85 percent dim`() = runTest {
        val vm = viewModel(FakeJourneyDataSource(), creds())
        assertEquals(15, vm.backdropDim.value)
    }

    @Test fun `setBackdropDim clamps the stored alpha to the 40 to 95 dim window`() = runTest {
        val vm = viewModel(FakeJourneyDataSource(), creds())
        vm.setBackdropDim(80)   // dim 20, below the 40% floor
        assertEquals(60, vm.backdropDim.value)
        vm.setBackdropDim(2)    // dim 98, above the 95% ceiling
        assertEquals(5, vm.backdropDim.value)
        vm.setBackdropDim(30)   // dim 70, inside the window
        assertEquals(30, vm.backdropDim.value)
    }

    /** The drag preview drives the stage live; it clamps like the commit does. */
    @Test fun `previewBackdropDim moves the stage value and clamps`() = runTest {
        val vm = viewModel(FakeJourneyDataSource(), creds())
        vm.previewBackdropDim(40)
        assertEquals(40, vm.backdropDim.value)
        vm.previewBackdropDim(99)
        assertEquals(60, vm.backdropDim.value)
    }

    // --- lifecycle ---

    @Test fun `onCleared - clears repository session cache`() = runTest {
        val fake = FakeJourneyDataSource()
        val vm = viewModel(fake, creds())
        val method = androidx.lifecycle.ViewModel::class.java.getDeclaredMethod("onCleared")
        method.isAccessible = true
        method.invoke(vm)
        assertEquals(1, fake.clearSessionCacheCalls)
    }
}
