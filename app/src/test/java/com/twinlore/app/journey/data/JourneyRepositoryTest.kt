package com.twinlore.app.journey.data

import com.twinlore.app.data.local.JourneyCacheDao
import com.twinlore.app.data.local.JourneyCompletionCacheEntity
import com.twinlore.app.data.local.JourneyGameCacheEntity
import com.twinlore.app.data.retroachievements.RaAchievementOfTheWeekResponse
import com.twinlore.app.data.retroachievements.RaCommentsResponse
import com.twinlore.app.data.retroachievements.RaCredentials
import com.twinlore.app.data.retroachievements.RaGameExtendedResponse
import com.twinlore.app.data.retroachievements.RaGameInfoAndUserProgressResponse
import com.twinlore.app.data.retroachievements.RaGameProgressionResponse
import com.twinlore.app.data.retroachievements.RaGameSummaryResponse
import com.twinlore.app.data.retroachievements.RaRecentlyPlayedGame
import com.twinlore.app.data.retroachievements.RaUserCompletionProgressEntry
import com.twinlore.app.data.retroachievements.RaUserCompletionProgressResponse
import com.twinlore.app.data.retroachievements.RaUserGameRankAndScoreEntry
import com.twinlore.app.data.retroachievements.RaUserSummaryResponse
import com.twinlore.app.data.retroachievements.RetroAchievementsApi
import com.twinlore.app.data.retroachievements.RetroAchievementsCredentialsRepository
import com.twinlore.app.journey.domain.model.JourneyMode
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

@OptIn(ExperimentalCoroutinesApi::class)
class JourneyRepositoryTest {

    private fun ts(millis: Long): String =
        java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .format(java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneOffset.UTC))

    private val base = 1_760_000_000_000L
    private val D = 86_400_000L

    private fun entry(
        gameId: Int,
        title: String = "G$gameId",
        numAwarded: Int = 1,
        numAwardedHardcore: Int = 0,
        mostRecentAwardedDate: Long? = base,
        highestAwardKind: String? = null,
        highestAwardDate: Long? = null,
    ) = RaUserCompletionProgressEntry(
        gameId = gameId,
        title = title,
        imageIcon = "/icon$gameId.png",
        numAwarded = numAwarded,
        numAwardedHardcore = numAwardedHardcore,
        mostRecentAwardedDate = mostRecentAwardedDate?.let(::ts),
        highestAwardKind = highestAwardKind,
        highestAwardDate = highestAwardDate?.let(::ts),
    )

    private fun completionResponse(vararg entries: RaUserCompletionProgressEntry) =
        RaUserCompletionProgressResponse(total = entries.size, results = entries.toList())

    /**
     * A game as the API answers for it, playtime included by default: a GAME-mode load treats a
     * cached snapshot with no `UserTotalPlaytime` as a miss (see the test below), so a fixture
     * without one would refetch on every call and say nothing about the caching being tested.
     */
    private fun gameInfo(
        gameId: Int,
        title: String = "G$gameId",
        userTotalPlaytime: Int? = 3600,
    ) = RaGameInfoAndUserProgressResponse(
        id = gameId, title = title, imageIcon = "/icon$gameId.png", achievements = emptyMap(),
        userTotalPlaytime = userTotalPlaytime,
    )

    private class FakeRetroAchievementsApi : RetroAchievementsApi {
        var completionResponse: RaUserCompletionProgressResponse =
            RaUserCompletionProgressResponse(total = 0, results = emptyList())
        var completionCalls = 0
        var completionFailure: (() -> Nothing)? = null
        /** Lets a test hold a completion call in flight, to exercise concurrent-caller coalescing. */
        var completionGate: CompletableDeferred<Unit>? = null
        /** Per-username override, so a multi-user test can tell whose rows came back. */
        val completionResponsesByUser = mutableMapOf<String, RaUserCompletionProgressResponse>()
        val completionCallUsernames = mutableListOf<String>()

        val gameInfoCalls = mutableListOf<Int>()
        var gameInfoHandler: (Int) -> RaGameInfoAndUserProgressResponse = { error("unconfigured gameId $it") }

        val gameProgressionCalls = mutableListOf<Int>()
        var gameProgressionHandler: (Int) -> RaGameProgressionResponse = { error("unconfigured gameId $it") }

        val gameRankCalls = mutableListOf<Int>()
        val gameRankUsers = mutableListOf<String>()
        var gameRankHandler: (Int) -> List<RaUserGameRankAndScoreEntry> = { error("unconfigured gameId $it") }

        val summaryCalls = mutableListOf<String>()
        /** What the summary endpoint answers, per username asked for. Throws when unconfigured. */
        var summaryHandler: (String) -> RaUserSummaryResponse = { error("unconfigured summary for $it") }
        /** What `g` and `a` were asked for: the journey wants neither list and must say so. */
        val summaryCounts = mutableListOf<Pair<Int, Int>>()

        override suspend fun getUserSummary(username: String, apiKey: String, recentGamesCount: Int, recentAchievementsCount: Int): RaUserSummaryResponse {
            summaryCalls += username
            summaryCounts += recentGamesCount to recentAchievementsCount
            return summaryHandler(username)
        }

        override suspend fun getRecentlyPlayedGames(username: String, apiKey: String, count: Int): List<RaRecentlyPlayedGame> = TODO()

        override suspend fun getGameInfoAndUserProgress(
            username: String,
            apiKey: String,
            gameId: Int,
            includeUserAwards: Int
        ): RaGameInfoAndUserProgressResponse {
            gameInfoCalls += gameId
            return gameInfoHandler(gameId)
        }

        override suspend fun getGame(apiKey: String, gameId: Int): RaGameSummaryResponse = TODO()

        override suspend fun getUserGameRankAndScore(username: String, apiKey: String, gameId: Int): List<RaUserGameRankAndScoreEntry> {
            gameRankCalls += gameId
            gameRankUsers += username
            return gameRankHandler(gameId)
        }

        override suspend fun getUserCompletionProgress(username: String, apiKey: String, count: Int, offset: Int): RaUserCompletionProgressResponse {
            completionCalls++
            completionCallUsernames += username
            completionGate?.await()
            completionFailure?.invoke()
            return completionResponsesByUser[username] ?: completionResponse
        }

        override suspend fun getGameExtended(apiKey: String, gameId: Int): RaGameExtendedResponse = TODO()

        override suspend fun getAchievementOfTheWeek(apiKey: String): RaAchievementOfTheWeekResponse = TODO()

        override suspend fun getGameProgression(apiKey: String, gameId: Int): RaGameProgressionResponse {
            gameProgressionCalls += gameId
            return gameProgressionHandler(gameId)
        }

        override suspend fun getAchievementComments(apiKey: String, type: Int, achievementId: Int, sort: String): RaCommentsResponse = TODO()
    }

    private class FakeJourneyCacheDao : JourneyCacheDao {
        val games = mutableMapOf<String, JourneyGameCacheEntity>()
        val completions = mutableMapOf<String, JourneyCompletionCacheEntity>()
        var totalCalls = 0

        private fun key(userName: String, gameId: Int) = "$userName|$gameId"

        override suspend fun getGame(userName: String, gameId: Int): JourneyGameCacheEntity? {
            totalCalls++
            return games[key(userName, gameId)]
        }

        override suspend fun upsertGame(entity: JourneyGameCacheEntity) {
            totalCalls++
            games[key(entity.userName, entity.gameId)] = entity
        }

        override suspend fun countGames(): Int {
            totalCalls++
            return games.size
        }

        override suspend fun evictOldestGames(n: Int) {
            totalCalls++
            games.values.sortedBy { it.fetchedAt }.take(n).forEach { games.remove(key(it.userName, it.gameId)) }
        }

        override suspend fun cachedGameUsers(): List<String> {
            totalCalls++
            return games.values.map { it.userName }.distinct()
        }

        override suspend fun cachedCompletionUsers(): List<String> {
            totalCalls++
            // Newest first, as the real ORDER BY fetchedAt DESC gives it: the recents list is
            // truncated, so the order is part of what the repository promises.
            return completions.values.sortedByDescending { it.fetchedAt }.map { it.userName }
        }

        override suspend fun countCompletion(): Int {
            totalCalls++
            return completions.size
        }

        override suspend fun evictOldestCompletion(n: Int) {
            totalCalls++
            completions.values.sortedBy { it.fetchedAt }.take(n).forEach { completions.remove(it.userName) }
        }

        override suspend fun getCompletion(userName: String): JourneyCompletionCacheEntity? {
            totalCalls++
            return completions[userName]
        }

        override suspend fun upsertCompletion(entity: JourneyCompletionCacheEntity) {
            totalCalls++
            completions[entity.userName] = entity
        }

        override suspend fun deleteGamesFor(userName: String) {
            totalCalls++
            // Matched on the row's OWN userName, not on a prefix of the composite map key. The
            // real query is `WHERE userName = :userName`, so deleting "me" must leave "me|rival"
            // alone; a prefix match here would let a test pass against semantics the database
            // does not have.
            games.values.filter { it.userName == userName }
                .forEach { games.remove(key(it.userName, it.gameId)) }
        }

        override suspend fun deleteCompletionFor(userName: String) {
            totalCalls++
            completions.remove(userName)
        }

        override suspend fun clearGames() {
            totalCalls++
            games.clear()
        }

        override suspend fun clearCompletion() {
            totalCalls++
            completions.clear()
        }
    }

    private fun fakeCredentials(username: String = "me", apiKey: String = "key"): RetroAchievementsCredentialsRepository =
        mock<RetroAchievementsCredentialsRepository> {
            onBlocking { getCredentials() } doReturn RaCredentials(username, apiKey)
        }

    /**
     * The repository hops to an IO dispatcher in production; here it gets the test scheduler's own
     * dispatcher instead, so the client's inter-call rate limiting keeps running on virtual time.
     */
    private fun TestScope.repo(
        api: FakeRetroAchievementsApi,
        dao: FakeJourneyCacheDao,
        credentials: RetroAchievementsCredentialsRepository = fakeCredentials(),
        nowMillis: () -> Long,
        siteRankSource: SiteRankSource? = null,
        gameCacheCap: Int = 20_000,
        completionCacheCap: Int = 64,
    ): JourneyRepository = JourneyRepository(
        RaJourneyClient(api, nowMillis), credentials, dao, nowMillis,
        StandardTestDispatcher(testScheduler), siteRankSource, gameCacheCap, completionCacheCap,
    )

    /** Credentials that fail to be read at all - a keystore or DataStore failure, not a null user. */
    private fun throwingCredentials(): RetroAchievementsCredentialsRepository =
        mock<RetroAchievementsCredentialsRepository> {
            onBlocking { getCredentials() } doAnswer { throw IllegalStateException("keystore read failed") }
        }

    /** A [SiteRankSource] that counts how often it was asked, standing in for the profile cache. */
    private class FakeSiteRankSource(var answer: Int?, var failWith: Throwable? = null) : SiteRankSource {
        var calls = 0
        override suspend fun siteRank(): Int? {
            calls++
            failWith?.let { throw it }
            return answer
        }
    }

    // --- 1: completion cache under 30min skips network ---

    @Test
    fun `completion cache under 30min skips network`() = runTest {
        val api = FakeRetroAchievementsApi()
        api.completionResponse = completionResponse(entry(1))
        api.gameInfoHandler = { gameInfo(it) }
        val dao = FakeJourneyCacheDao()
        val repository = repo(api, dao, nowMillis = { testScheduler.currentTime })

        repository.fetchOwn(JourneyMode.GAME, gameId = 1, spanFromMillis = 0, spanToMillis = 0, onProgress = { _, _, _ -> })
        val completionCallsAfterFirst = api.completionCalls
        val gameCallsAfterFirst = api.gameInfoCalls.size
        assertEquals(1, completionCallsAfterFirst)
        assertEquals(1, gameCallsAfterFirst)

        repository.fetchOwn(JourneyMode.GAME, gameId = 1, spanFromMillis = 0, spanToMillis = 0, onProgress = { _, _, _ -> })

        assertEquals("expected no new completion calls", completionCallsAfterFirst, api.completionCalls)
        assertEquals("expected no new game calls", gameCallsAfterFirst, api.gameInfoCalls.size)
    }

    // --- 2: validator match skips per-game refetch (span mode) ---

    @Test
    fun `validator match skips per-game refetch`() = runTest {
        val api = FakeRetroAchievementsApi()
        api.completionResponse = completionResponse(entry(1), entry(2), entry(3))
        api.gameInfoHandler = { gameInfo(it) }
        val dao = FakeJourneyCacheDao()
        val repository = repo(api, dao, nowMillis = { testScheduler.currentTime })

        val from = base - D
        val to = base + D

        repository.fetchOwn(JourneyMode.SPAN, gameId = null, spanFromMillis = from, spanToMillis = to, onProgress = { _, _, _ -> })
        assertEquals(1, api.completionCalls)
        assertEquals(3, api.gameInfoCalls.size)

        // Expire the completion TTL (30 min) without changing any row.
        kotlinx.coroutines.delay(31 * 60 * 1000L)

        repository.fetchOwn(JourneyMode.SPAN, gameId = null, spanFromMillis = from, spanToMillis = to, onProgress = { _, _, _ -> })

        assertEquals("expected exactly one more completion call", 2, api.completionCalls)
        assertEquals("expected no new game calls (validators unchanged)", 3, api.gameInfoCalls.size)
    }

    // --- 3: changed MostRecentAwardedDate triggers single game refetch ---

    @Test
    fun `changed MostRecentAwardedDate triggers single game refetch`() = runTest {
        val api = FakeRetroAchievementsApi()
        api.completionResponse = completionResponse(entry(1, mostRecentAwardedDate = base))
        api.gameInfoHandler = { gameInfo(it) }
        val dao = FakeJourneyCacheDao()
        val repository = repo(api, dao, nowMillis = { testScheduler.currentTime })

        repository.fetchOwn(JourneyMode.GAME, gameId = 1, spanFromMillis = 0, spanToMillis = 0, onProgress = { _, _, _ -> })
        assertEquals(1, api.gameInfoCalls.size)

        kotlinx.coroutines.delay(31 * 60 * 1000L)
        api.completionResponse = completionResponse(entry(1, mostRecentAwardedDate = base + D, numAwarded = 2))

        repository.fetchOwn(JourneyMode.GAME, gameId = 1, spanFromMillis = 0, spanToMillis = 0, onProgress = { _, _, _ -> })

        assertEquals("expected the game to be refetched after its validator changed", 2, api.gameInfoCalls.size)
    }

    // --- a GAME-mode row cached before UserTotalPlaytime existed is refetched once ---

    /**
     * The owner's report: a finished single-game journey said "Achieved over 2 days" and never the
     * hours, forever.
     *
     * The validator only sees what the completion list says - how many awards, and when the last one
     * landed - so a snapshot written before `UserTotalPlaytime` was threaded through matched it on
     * every load and the header's "Played 42h 10m over 2 days" quietly lost its first half for the
     * life of that row. A GAME-mode load now treats a playtime-less cached row as a miss: one
     * refetch, and the write stores the refreshed payload under the same validator, so the load
     * after it is a cache hit again.
     */
    @Test
    fun `a GAME row cached without playtime is refetched exactly once`() = runTest {
        val api = FakeRetroAchievementsApi()
        api.completionResponse = completionResponse(entry(1))
        // The state the owner's cache is in: a perfectly valid row from before the field existed.
        api.gameInfoHandler = { gameInfo(it, userTotalPlaytime = null) }
        val dao = FakeJourneyCacheDao()
        val repository = repo(api, dao, nowMillis = { testScheduler.currentTime })

        repository.fetchOwn(JourneyMode.GAME, gameId = 1, spanFromMillis = 0, spanToMillis = 0, onProgress = { _, _, _ -> })
        assertEquals(1, api.gameInfoCalls.size)

        // The API has the hours now; the validator has not moved.
        api.gameInfoHandler = { gameInfo(it, userTotalPlaytime = 5400) }

        val refetched = repository.fetchOwn(JourneyMode.GAME, gameId = 1, spanFromMillis = 0, spanToMillis = 0, onProgress = { _, _, _ -> })

        assertEquals("the stale row is refetched", 2, api.gameInfoCalls.size)
        assertEquals(5400, refetched.snapshots.single().userTotalPlaytimeSeconds)

        val again = repository.fetchOwn(JourneyMode.GAME, gameId = 1, spanFromMillis = 0, spanToMillis = 0, onProgress = { _, _, _ -> })

        assertEquals("and once only: the refreshed payload is what is cached", 2, api.gameInfoCalls.size)
        assertEquals(5400, again.snapshots.single().userTotalPlaytimeSeconds)
    }

    /**
     * And a refetch that cannot be made falls back to the row it set aside. The journey the cached
     * snapshot draws is still the right one; only the hours are missing from it, which is exactly
     * the state it was already in.
     */
    @Test
    fun `a failed playtime refetch falls back to the cached row`() = runTest {
        val api = FakeRetroAchievementsApi()
        api.completionResponse = completionResponse(entry(1))
        api.gameInfoHandler = { gameInfo(it, userTotalPlaytime = null) }
        val dao = FakeJourneyCacheDao()
        val repository = repo(api, dao, nowMillis = { testScheduler.currentTime })

        repository.fetchOwn(JourneyMode.GAME, gameId = 1, spanFromMillis = 0, spanToMillis = 0, onProgress = { _, _, _ -> })
        api.gameInfoHandler = { throw IOException("simulated network failure") }

        val result = repository.fetchOwn(JourneyMode.GAME, gameId = 1, spanFromMillis = 0, spanToMillis = 0, onProgress = { _, _, _ -> })

        assertEquals(1, result.snapshots.size)
        assertEquals(1, result.snapshots.single().gameId)
        assertNull("nothing invents the hours", result.snapshots.single().userTotalPlaytimeSeconds)
    }

    // --- GAME mode with no gameId throws RaJourneyException (uniform failure type for the ViewModel) ---

    @Test
    fun `GAME mode with null gameId throws RaJourneyException`() = runTest {
        val api = FakeRetroAchievementsApi()
        api.completionResponse = completionResponse(entry(1))
        val dao = FakeJourneyCacheDao()
        val repository = repo(api, dao, nowMillis = { testScheduler.currentTime })

        try {
            repository.fetchOwn(JourneyMode.GAME, gameId = null, spanFromMillis = 0, spanToMillis = 0, onProgress = { _, _, _ -> })
            org.junit.Assert.fail("expected RaJourneyException")
        } catch (e: RaJourneyException) {
            val message = requireNotNull(e.message)
            assertFalse("message must not contain query string: $message", message.contains("y="))
            assertFalse("message must not contain URL: $message", message.contains("http"))
        }
    }

    // --- stale completion fallback: network failure with a stale cached row uses the stale row, no throw ---

    @Test
    fun `stale completion fallback used when network fails`() = runTest {
        val api = FakeRetroAchievementsApi()
        api.completionResponse = completionResponse(entry(1, mostRecentAwardedDate = base))
        api.gameInfoHandler = { gameInfo(it) }
        val dao = FakeJourneyCacheDao()
        val repository = repo(api, dao, nowMillis = { testScheduler.currentTime })

        // Prime the completion cache with a successful fetch.
        repository.fetchOwn(JourneyMode.GAME, gameId = 1, spanFromMillis = 0, spanToMillis = 0, onProgress = { _, _, _ -> })
        assertEquals(1, api.completionCalls)

        // Expire the completion TTL, then make the network unreachable for the completion refetch.
        kotlinx.coroutines.delay(31 * 60 * 1000L)
        api.completionFailure = { throw IOException("simulated network failure") }

        val result = repository.fetchOwn(JourneyMode.GAME, gameId = 1, spanFromMillis = 0, spanToMillis = 0, onProgress = { _, _, _ -> })

        assertEquals(1, result.snapshots.size)
        assertEquals(1, result.completionByGame.size)
        assertEquals(1, result.completionByGame.getValue(1).gameId)
    }

    // --- 4: partial failure surfaces failedCount and keeps other games ---

    @Test
    fun `partial failure surfaces failedCount and keeps other games`() = runTest {
        val api = FakeRetroAchievementsApi()
        api.completionResponse = completionResponse(entry(1), entry(2), entry(3))
        api.gameInfoHandler = { id ->
            if (id == 2) throw IOException("simulated network failure") else gameInfo(id)
        }
        val dao = FakeJourneyCacheDao()
        val repository = repo(api, dao, nowMillis = { testScheduler.currentTime })

        val progressCalls = mutableListOf<Triple<String, Int, Int>>()
        val result = repository.fetchOwn(
            JourneyMode.SPAN, gameId = null,
            spanFromMillis = base - D, spanToMillis = base + D,
            onProgress = { label, done, total -> progressCalls += Triple(label, done, total) },
        )

        assertEquals(1, result.failedCount)
        assertEquals(2, result.snapshots.size)
        assertTrue(result.snapshots.none { it.gameId == 2 })
        assertEquals(3, progressCalls.size)
    }

    // --- 5: rival data persists like the own data / session cache still fronts it ---

    /**
     * Rival rows land on disk (user request), namespaced under the signed-in account's own key
     * with a separator no username can carry - which is what lets the account-change wipe tell a
     * rival row from a departed account's row.
     */
    @Test
    fun `rival rows persist under the signed-in account's namespace`() = runTest {
        val api = FakeRetroAchievementsApi()
        api.completionResponse = completionResponse(entry(10), entry(11))
        api.gameInfoHandler = { gameInfo(it) }
        val dao = FakeJourneyCacheDao()
        val repository = repo(api, dao, nowMillis = { testScheduler.currentTime })

        repository.fetchRival(
            "Rival", JourneyMode.SPAN, gameId = null,
            spanFromMillis = base - D, spanToMillis = base + D, spanKey = "D90",
            onProgress = { _, _, _ -> },
        )

        assertTrue(dao.games.containsKey("me|rival|10"))
        assertTrue(dao.games.containsKey("me|rival|11"))
        assertTrue(dao.completions.containsKey("me|rival"))
    }

    @Test
    fun `rival session cache hit costs zero calls`() = runTest {
        val api = FakeRetroAchievementsApi()
        api.completionResponse = completionResponse(entry(10), entry(11))
        api.gameInfoHandler = { gameInfo(it) }
        val dao = FakeJourneyCacheDao()
        val repository = repo(api, dao, nowMillis = { testScheduler.currentTime })

        val params = arrayOf(base - D, base + D)
        repository.fetchRival("rival", JourneyMode.SPAN, null, params[0], params[1], "D90", onProgress = { _, _, _ -> })
        val completionCallsAfterFirst = api.completionCalls
        val gameCallsAfterFirst = api.gameInfoCalls.size

        repository.fetchRival("rival", JourneyMode.SPAN, null, params[0], params[1], "D90", onProgress = { _, _, _ -> })

        assertEquals(completionCallsAfterFirst, api.completionCalls)
        assertEquals(gameCallsAfterFirst, api.gameInfoCalls.size)
    }

    // The span bounds come from the wall clock at each load, so a cache keyed on the resolved
    // millis was a miss on every second Load of the same preset - however soon after the first,
    // and however unchanged the request. Two loads a day apart, same preset: one fetch.
    @Test
    fun `rival session cache hits a second load of the same span preset`() = runTest {
        val api = FakeRetroAchievementsApi()
        api.completionResponse = completionResponse(entry(10), entry(11))
        api.gameInfoHandler = { gameInfo(it) }
        val dao = FakeJourneyCacheDao()
        val repository = repo(api, dao, nowMillis = { testScheduler.currentTime })

        repository.fetchRival("rival", JourneyMode.SPAN, null, base - D, base, "D90", onProgress = { _, _, _ -> })
        val completionCallsAfterFirst = api.completionCalls
        val gameCallsAfterFirst = api.gameInfoCalls.size

        // The same preset, resolved a day later: both bounds have moved, the request has not.
        repository.fetchRival("rival", JourneyMode.SPAN, null, base, base + D, "D90", onProgress = { _, _, _ -> })

        assertEquals("a second Load of the same preset must not refetch", completionCallsAfterFirst, api.completionCalls)
        assertEquals(gameCallsAfterFirst, api.gameInfoCalls.size)
    }

    /**
     * A different window is a different question - the session result cache misses - but the
     * per-game rows persisted by the first load answer it: nothing changed in the game, so the
     * validator serves it from disk and the network is not asked again.
     */
    @Test
    fun `a changed window re-asks the question but persisted games still serve`() = runTest {
        val api = FakeRetroAchievementsApi()
        api.completionResponse = completionResponse(entry(10))
        api.gameInfoHandler = { gameInfo(it) }
        val repository = repo(api, FakeJourneyCacheDao(), nowMillis = { testScheduler.currentTime })

        repository.fetchRival("rival", JourneyMode.SPAN, null, base - D, base, "D90", onProgress = { _, _, _ -> })
        repository.fetchRival("rival", JourneyMode.SPAN, null, base - D, base, "CUSTOM:30:0", onProgress = { _, _, _ -> })

        assertEquals(1, api.gameInfoCalls.size)
    }

    /**
     * The session result cache is still a bounded LRU (one entry is a whole span's worth of
     * snapshots), but eviction from it is invisible to the network now: a window it dropped is
     * rebuilt from the persisted per-game rows, whose validators say nothing changed.
     */
    @Test
    fun `a window evicted from the session cache is served back from the disk cache`() = runTest {
        val api = FakeRetroAchievementsApi()
        api.completionResponse = completionResponse(entry(10))
        api.gameInfoHandler = { gameInfo(it) }
        val repository = repo(api, FakeJourneyCacheDao(), nowMillis = { testScheduler.currentTime })

        suspend fun load(key: String) =
            repository.fetchRival("rival", JourneyMode.SPAN, null, base - D, base, key, onProgress = { _, _, _ -> })

        repeat(9) { load("W$it") }
        val callsAfterNine = api.gameInfoCalls.size

        load("W8")
        assertEquals(callsAfterNine, api.gameInfoCalls.size)
        // W0 was pushed out of the session LRU by the ninth window; the disk rows answer it.
        load("W0")
        assertEquals(callsAfterNine, api.gameInfoCalls.size)
    }

    // Span candidates are picked on the softcore award count, which is a superset of the hardcore
    // one: loading with hardcore on then toggling it off used to render an incomplete chart,
    // because the softcore-only games were never fetched in the first place.
    @Test
    fun `span candidates cover softcore-only and hardcore games alike`() = runTest {
        val api = FakeRetroAchievementsApi()
        api.completionResponse = completionResponse(
            entry(1, numAwarded = 4, numAwardedHardcore = 0),   // softcore only
            entry(2, numAwarded = 3, numAwardedHardcore = 3),   // hardcore
        )
        api.gameInfoHandler = { gameInfo(it) }
        val dao = FakeJourneyCacheDao()
        val repository = repo(api, dao, nowMillis = { testScheduler.currentTime })

        val result = repository.fetchOwn(
            JourneyMode.SPAN, gameId = null,
            spanFromMillis = base - D, spanToMillis = base + D, onProgress = { _, _, _ -> },
        )

        assertEquals(listOf(1, 2), api.gameInfoCalls.sorted())
        assertEquals(2, result.snapshots.size)
    }

    // --- 6: eviction bounds the table without eating the pass that is writing it ---

    /**
     * A span bigger than the cap keeps every row it just wrote. Rows are stamped with the time
     * they were fetched, one network call apart, so this pass's own earliest rows were the oldest
     * in the table and the first ones deleted: games on the chart the user was watching had
     * already lost their cache entry before the load finished, and the next Load refetched them.
     */
    @Test
    fun `a span larger than the cap keeps the rows it just wrote`() = runTest {
        val api = FakeRetroAchievementsApi()
        val entries = (1..301).map { entry(it, mostRecentAwardedDate = base) }.toTypedArray()
        api.completionResponse = completionResponse(*entries)
        api.gameInfoHandler = { gameInfo(it) }
        val dao = FakeJourneyCacheDao()
        val repository = repo(api, dao, nowMillis = { testScheduler.currentTime }, gameCacheCap = 300)

        val result = repository.fetchOwn(
            JourneyMode.SPAN, gameId = null,
            spanFromMillis = base - D, spanToMillis = base + D, onProgress = { _, _, _ -> },
        )

        assertEquals(301, result.snapshots.size)
        assertEquals(301, dao.games.size)
        assertTrue("the first game of this span must still be cached", dao.games.containsKey("me|1"))
        assertTrue(dao.games.containsKey("me|301"))
    }

    /** What the cap does evict is an earlier pass, oldest first. */
    @Test
    fun `eviction trims the older pass once the table is over the cap`() = runTest {
        val api = FakeRetroAchievementsApi()
        api.completionResponse = completionResponse(*(1..250).map { entry(it, mostRecentAwardedDate = base) }.toTypedArray())
        api.gameInfoHandler = { gameInfo(it) }
        val dao = FakeJourneyCacheDao()
        val repository = repo(api, dao, nowMillis = { testScheduler.currentTime }, gameCacheCap = 300)

        repository.fetchOwn(JourneyMode.SPAN, null, base - D, base + D, onProgress = { _, _, _ -> })
        assertEquals(250, dao.games.size)

        // A second window, a different 100 games, once the completion TTL has expired.
        kotlinx.coroutines.delay(31 * 60 * 1000L)
        api.completionResponse = completionResponse(*(1001..1100).map { entry(it, mostRecentAwardedDate = base) }.toTypedArray())
        repository.fetchOwn(JourneyMode.SPAN, null, base - D, base + D, onProgress = { _, _, _ -> })

        assertEquals(300, dao.games.size)
        assertFalse("the oldest row of the first pass goes", dao.games.containsKey("me|1"))
        assertTrue("nothing the second pass wrote goes", dao.games.containsKey("me|1001"))
        assertTrue(dao.games.containsKey("me|1100"))
    }

    /**
     * Single-game mode writes a row per distinct game viewed and used to check the cap nowhere at
     * all, so a player who only ever opens a vault's Achievements tab grew the table forever.
     */
    @Test
    fun `game mode evicts once the table is over the cap`() = runTest {
        val api = FakeRetroAchievementsApi()
        api.completionResponse = completionResponse(entry(9001))
        api.gameInfoHandler = { gameInfo(it) }
        val dao = FakeJourneyCacheDao()
        // 300 rows already cached for this account, oldest first.
        (1..300).forEach { id ->
            dao.games["me|$id"] = JourneyGameCacheEntity("me", id, id.toLong(), "{}", "{}")
        }
        val repository = repo(api, dao, nowMillis = { testScheduler.currentTime + 1_000_000 }, gameCacheCap = 300)

        repository.fetchOwn(JourneyMode.GAME, gameId = 9001, spanFromMillis = 0, spanToMillis = 0, onProgress = { _, _, _ -> })

        assertEquals(300, dao.games.size)
        assertFalse("the oldest row goes", dao.games.containsKey("me|1"))
        assertTrue("the game just fetched stays", dao.games.containsKey("me|9001"))
    }

    // --- 7: game rank is best effort, and asked for once a session ---

    @Test
    fun `game rank comes back once and is then answered from memory`() = runTest {
        val api = FakeRetroAchievementsApi()
        api.gameRankHandler = { listOf(RaUserGameRankAndScoreEntry(user = "me", userRank = 12)) }
        val repository = repo(api, FakeJourneyCacheDao(), nowMillis = { testScheduler.currentTime })

        assertEquals(12, repository.fetchGameRank(42))
        assertEquals(12, repository.fetchGameRank(42))

        assertEquals(listOf(42), api.gameRankCalls)
    }

    @Test
    fun `game rank failure returns null and is not cached`() = runTest {
        val api = FakeRetroAchievementsApi()
        api.gameRankHandler = { throw IOException("simulated failure") }
        val repository = repo(api, FakeJourneyCacheDao(), nowMillis = { testScheduler.currentTime })

        assertNull(repository.fetchGameRank(42))

        api.gameRankHandler = { listOf(RaUserGameRankAndScoreEntry(user = "me", userRank = 7)) }
        assertEquals(7, repository.fetchGameRank(42))
    }

    /**
     * A rival's rank on the same game is a different question from the signed-in player's, so it
     * is a different call and a different cache entry - the compare row would otherwise show one
     * player's standing under everybody's name.
     */
    @Test
    fun `a rival's game rank is fetched for that rival and cached apart from the player's own`() = runTest {
        val api = FakeRetroAchievementsApi()
        api.gameRankHandler = { listOf(RaUserGameRankAndScoreEntry(user = "who", userRank = 3)) }
        val repository = repo(api, FakeJourneyCacheDao(), nowMillis = { testScheduler.currentTime })

        assertEquals(3, repository.fetchGameRank(42))
        api.gameRankHandler = { listOf(RaUserGameRankAndScoreEntry(user = "who", userRank = 88)) }
        assertEquals(88, repository.fetchGameRank(42, "aria"))
        // Both are then answered from memory, each with its own number.
        assertEquals(3, repository.fetchGameRank(42))
        assertEquals(88, repository.fetchGameRank(42, "aria"))

        assertEquals(listOf(42, 42), api.gameRankCalls)
        assertEquals(listOf("me", "aria"), api.gameRankUsers)
    }

    /**
     * The rank payload is also where the journey learns how a name is *spelled*.
     *
     * `media.retroachievements.org/UserPic/{name}.png` is case sensitive while every RA lookup is
     * not, so a rival typed as "aria" fetches perfectly and then 404s on their picture - which is
     * the bug that left rivals wearing the initial-letter fallback disc on their curve ends. The
     * `User` field on this response is the cheapest reliable source of the real spelling in
     * anything the journey already fetches, so it is harvested here rather than paid for.
     */
    @Test
    fun `a rank lookup records the spelling RetroAchievements answers with`() = runTest {
        val api = FakeRetroAchievementsApi()
        api.gameRankHandler = { listOf(RaUserGameRankAndScoreEntry(user = "AriaTheFirst", userRank = 3)) }
        val repository = repo(api, FakeJourneyCacheDao(), nowMillis = { testScheduler.currentTime })

        repository.fetchGameRank(42, "ariathefirst")

        assertEquals("AriaTheFirst", repository.canonicalName("ariathefirst"))
        assertEquals("asked for in any case at all", "AriaTheFirst", repository.canonicalName("ARIATHEFIRST"))
        assertNull("and nothing is invented for a name nobody has echoed", repository.canonicalName("bob"))
    }

    /**
     * The two facts are independent: a player with no rank on a game still has a name, and their
     * avatar is exactly as case-sensitive as a ranked player's.
     */
    @Test
    fun `a spelling is kept even when the rank itself is not`() = runTest {
        val api = FakeRetroAchievementsApi()
        api.gameRankHandler = { listOf(RaUserGameRankAndScoreEntry(user = "Brix", userRank = 0)) }
        val repository = repo(api, FakeJourneyCacheDao(), nowMillis = { testScheduler.currentTime })

        assertNull("rank zero is no rank", repository.fetchGameRank(42, "brix"))
        assertEquals("Brix", repository.canonicalName("brix"))
    }

    /** Session state, dropped with the rest of it: a name belongs to the account that asked. */
    @Test
    fun `clearing the session drops the spellings with everything else`() = runTest {
        val api = FakeRetroAchievementsApi()
        api.gameRankHandler = { listOf(RaUserGameRankAndScoreEntry(user = "Brix", userRank = 4)) }
        api.summaryHandler = { RaUserSummaryResponse(user = "Brix", rank = 9) }
        val repository = repo(api, FakeJourneyCacheDao(), nowMillis = { testScheduler.currentTime })
        repository.fetchGameRank(42, "brix")
        repository.playerSummary("brix")

        repository.clearSessionCache()

        assertNull(repository.canonicalName("brix"))
        repository.playerSummary("brix")
        assertEquals("and the summary is asked for again", 2, api.summaryCalls.size)
    }

    // --- the player summary: one call for a spelling and a site rank ---

    /**
     * The summary is where a span learns both facts it cannot get anywhere else: how a player's
     * name is spelled (which is what makes `/UserPic/{name}.png` resolve - it is case sensitive
     * while every RA lookup is not) and where they stand on the site (a window of time has no
     * leaderboard of its own). One call answers both, asked for with neither of the two lists the
     * endpoint can pad itself out with.
     */
    @Test
    fun `a player summary carries the spelling and the site rank in one call`() = runTest {
        val api = FakeRetroAchievementsApi()
        api.summaryHandler = { RaUserSummaryResponse(user = "Aria", rank = 812) }
        val repository = repo(api, FakeJourneyCacheDao(), nowMillis = { testScheduler.currentTime })

        val summary = repository.playerSummary("aria")

        assertEquals("Aria", summary?.canonicalName)
        assertEquals(812, summary?.siteRank)
        assertEquals("the spelling is recorded for everyone else too", "Aria", repository.canonicalName("aria"))
        assertEquals("neither list is asked for", listOf(0 to 0), api.summaryCounts)
    }

    /** Once a session per player, whatever the case it is asked for in. */
    @Test
    fun `a player summary is fetched once a session`() = runTest {
        val api = FakeRetroAchievementsApi()
        api.summaryHandler = { RaUserSummaryResponse(user = "Aria", rank = 812) }
        val repository = repo(api, FakeJourneyCacheDao(), nowMillis = { testScheduler.currentTime })

        repository.playerSummary("aria")
        repository.playerSummary("ARIA")

        assertEquals(listOf("aria"), api.summaryCalls)
    }

    /**
     * Best effort, exactly like the rank lookups: a failure is an answer of null, and the avatar
     * falls back to the player's initial rather than the journey falling over.
     */
    @Test
    fun `a player summary that fails answers null and records no spelling`() = runTest {
        val api = FakeRetroAchievementsApi()
        api.summaryHandler = { throw IOException("simulated failure") }
        val repository = repo(api, FakeJourneyCacheDao(), nowMillis = { testScheduler.currentTime })

        assertNull(repository.playerSummary("aria"))
        assertNull(repository.canonicalName("aria"))
    }

    /**
     * The rival caches are keyed on the name CASE-FOLDED, because the journey re-spells a rival the
     * way RA spells them the moment a response says so: the very next load asks for "Aria" where
     * the first asked for "aria". A case-sensitive key made that a guaranteed miss - the whole
     * roster refetched every Load, each spelling holding its own copy of a span's game snapshots in
     * a cache that keeps eight.
     */
    @Test
    fun `a rival re-spelled after adoption still hits the session cache`() = runTest {
        val api = FakeRetroAchievementsApi()
        api.completionResponse = completionResponse(entry(10), entry(11))
        api.gameInfoHandler = { gameInfo(it) }
        val repository = repo(api, FakeJourneyCacheDao(), nowMillis = { testScheduler.currentTime })

        repository.fetchRival("aria", JourneyMode.SPAN, null, base - D, base, "D90", onProgress = { _, _, _ -> })
        val completionCallsAfterFirst = api.completionCalls
        val gameCallsAfterFirst = api.gameInfoCalls.size

        // The next load asks under the canonical spelling, which is the same account.
        repository.fetchRival("Aria", JourneyMode.SPAN, null, base - D, base, "D90", onProgress = { _, _, _ -> })

        assertEquals(completionCallsAfterFirst, api.completionCalls)
        assertEquals(gameCallsAfterFirst, api.gameInfoCalls.size)
    }

    /** An unranked player gets an empty array, which is a card without the line rather than a zero. */
    @Test
    fun `game rank of an unranked player is null`() = runTest {
        val api = FakeRetroAchievementsApi()
        api.gameRankHandler = { emptyList() }
        val repository = repo(api, FakeJourneyCacheDao(), nowMillis = { testScheduler.currentTime })

        assertNull(repository.fetchGameRank(42))
    }

    /**
     * Best-effort means the whole lookup, not just the network call inside it. The credential read
     * in front of the request can fail on its own (a keystore or DataStore failure), and every
     * caller treats these two functions as answering rather than throwing - a rank is one line on
     * a closing card, and losing it must never cost the journey it decorates.
     */
    @Test
    fun `a game rank whose credential read throws answers null rather than propagating`() = runTest {
        val repository = repo(
            FakeRetroAchievementsApi(), FakeJourneyCacheDao(),
            credentials = throwingCredentials(), nowMillis = { testScheduler.currentTime },
        )

        assertNull(repository.fetchGameRank(42))
        assertNull(repository.fetchGameRank(42, "aria"))
    }

    // --- 7b: the span end state's site rank, same best-effort contract as the game rank ---

    @Test
    fun `site rank comes back once and is then answered from memory`() = runTest {
        val source = FakeSiteRankSource(answer = 1234)
        val repository = repo(
            FakeRetroAchievementsApi(), FakeJourneyCacheDao(),
            nowMillis = { testScheduler.currentTime }, siteRankSource = source,
        )

        assertEquals(1234, repository.siteRank())
        assertEquals(1234, repository.siteRank())

        assertEquals("the profile must only be asked once a session", 1, source.calls)
    }

    /**
     * Only answers that arrived are remembered. Caching a miss would turn one rate-limited call
     * into a card the session can never show, and asking again costs one request.
     */
    @Test
    fun `site rank failure returns null and is not cached`() = runTest {
        val source = FakeSiteRankSource(answer = null, failWith = IOException("simulated failure"))
        val repository = repo(
            FakeRetroAchievementsApi(), FakeJourneyCacheDao(),
            nowMillis = { testScheduler.currentTime }, siteRankSource = source,
        )

        assertNull(repository.siteRank())

        source.failWith = null
        source.answer = 7
        assertEquals(7, repository.siteRank())
        assertEquals(2, source.calls)
    }

    /** A profile that has never synced answers null rather than throwing, and stays uncached. */
    @Test
    fun `site rank of an unknown profile is null`() = runTest {
        val source = FakeSiteRankSource(answer = null)
        val repository = repo(
            FakeRetroAchievementsApi(), FakeJourneyCacheDao(),
            nowMillis = { testScheduler.currentTime }, siteRankSource = source,
        )

        assertNull(repository.siteRank())
        assertNull(repository.siteRank())
        assertEquals("a miss must not be cached", 2, source.calls)
    }

    /**
     * The API reports 0 for a player with no rank at all, which is not a rank; the card is absent
     * rather than reading "Site rank #0".
     */
    @Test
    fun `a site rank of zero or below is no rank`() = runTest {
        val source = FakeSiteRankSource(answer = 0)
        val repository = repo(
            FakeRetroAchievementsApi(), FakeJourneyCacheDao(),
            nowMillis = { testScheduler.currentTime }, siteRankSource = source,
        )

        assertNull(repository.siteRank())

        source.answer = -3
        assertNull(repository.siteRank())
    }

    /** No source wired at all (the seam is optional) is simply no rank, never a crash. */
    @Test
    fun `no site rank source is no rank`() = runTest {
        val repository = repo(
            FakeRetroAchievementsApi(), FakeJourneyCacheDao(),
            nowMillis = { testScheduler.currentTime },
        )

        assertNull(repository.siteRank())
    }

    /** The same contract as the game rank: the credential read is inside the best-effort, not in front of it. */
    @Test
    fun `a site rank whose credential read throws answers null rather than propagating`() = runTest {
        val source = FakeSiteRankSource(answer = 55)
        val repository = repo(
            FakeRetroAchievementsApi(), FakeJourneyCacheDao(),
            credentials = throwingCredentials(), nowMillis = { testScheduler.currentTime },
            siteRankSource = source,
        )

        assertNull(repository.siteRank())
        assertEquals("the source is never reached, so nothing is spent on it", 0, source.calls)
    }

    @Test
    fun `clearing the session cache forgets the site rank`() = runTest {
        val source = FakeSiteRankSource(answer = 55)
        val repository = repo(
            FakeRetroAchievementsApi(), FakeJourneyCacheDao(),
            nowMillis = { testScheduler.currentTime }, siteRankSource = source,
        )

        assertEquals(55, repository.siteRank())
        repository.clearSessionCache()
        assertEquals(55, repository.siteRank())

        assertEquals(2, source.calls)
    }

    // --- 8: playedGames() sorting + cache reuse for the picker ---

    @Test
    fun `playedGames sorts by most recent activity, unparseable dates last`() = runTest {
        val api = FakeRetroAchievementsApi()
        api.completionResponse = completionResponse(
            entry(1, title = "Oldest", mostRecentAwardedDate = base - D),
            entry(2, title = "Newest", mostRecentAwardedDate = base + D),
            entry(3, title = "NoDate", mostRecentAwardedDate = null),
            entry(4, title = "Middle", mostRecentAwardedDate = base),
        )
        val dao = FakeJourneyCacheDao()
        val repository = repo(api, dao, nowMillis = { testScheduler.currentTime })

        val result = repository.playedGames()

        assertEquals(listOf("Newest", "Middle", "Oldest", "NoDate"), result.map { it.title })
    }

    @Test
    fun `playedGames reuses the completion cache - zero calls when fresh`() = runTest {
        val api = FakeRetroAchievementsApi()
        api.completionResponse = completionResponse(entry(1), entry(2))
        val dao = FakeJourneyCacheDao()
        val repository = repo(api, dao, nowMillis = { testScheduler.currentTime })

        repository.playedGames()
        assertEquals(1, api.completionCalls)

        repository.playedGames()
        assertEquals("expected no new completion calls within TTL", 1, api.completionCalls)
    }

    @Test
    fun `playedGames shares the cache fetchOwn already populated`() = runTest {
        val api = FakeRetroAchievementsApi()
        api.completionResponse = completionResponse(entry(1))
        api.gameInfoHandler = { gameInfo(it) }
        val dao = FakeJourneyCacheDao()
        val repository = repo(api, dao, nowMillis = { testScheduler.currentTime })

        repository.fetchOwn(JourneyMode.GAME, gameId = 1, spanFromMillis = 0, spanToMillis = 0, onProgress = { _, _, _ -> })
        assertEquals(1, api.completionCalls)

        repository.playedGames()
        assertEquals("playedGames must reuse fetchOwn's cached completion, not refetch", 1, api.completionCalls)
    }

    // playedGames() and fetchOwn() both do a non-atomic read-check-fetch on the completion cache:
    // when the picker opens around the same time as Load, on a cold or expired cache, both can see
    // "no fresh cache" before either has written one back. Without coalescing that runs the whole
    // paged completion fetch twice; this proves it runs exactly once and both callers still get
    // the rows.
    @Test
    fun `concurrent playedGames and fetchOwn coalesce into a single completion fetch`() = runTest {
        val api = FakeRetroAchievementsApi()
        api.completionResponse = completionResponse(entry(1), entry(2))
        api.gameInfoHandler = { gameInfo(it) }
        val gate = CompletableDeferred<Unit>()
        api.completionGate = gate
        val dao = FakeJourneyCacheDao()
        val repository = repo(api, dao, nowMillis = { testScheduler.currentTime })

        val playedGamesDeferred = async { repository.playedGames() }
        val fetchOwnDeferred = async {
            repository.fetchOwn(
                JourneyMode.SPAN, gameId = null,
                spanFromMillis = base - D, spanToMillis = base + D, onProgress = { _, _, _ -> },
            )
        }

        // Let both callers reach the (now suspended) completion call before releasing it. The
        // client's own inter-call gate delays the first real call by 350ms of virtual time, so
        // this needs to advance time, not just drain immediately-runnable work.
        advanceUntilIdle()
        assertEquals("only one completion fetch should have started", 1, api.completionCalls)

        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals("coalescing must prevent a second completion fetch", 1, api.completionCalls)
        assertEquals(2, playedGamesDeferred.await().size)
        assertEquals(2, fetchOwnDeferred.await().completionByGame.size)
    }

    // The in-flight fetch is keyed by userName so an account switch mid-fetch (A signs out, B
    // signs in and opens the picker) can never hand B a Deferred that resolves to A's rows. Two
    // different users racing must each get their own fetch and their own rows, even though the
    // client's own request mutex still serializes the two network calls one after the other.
    @Test
    fun `concurrent calls for different users never share an in-flight fetch`() = runTest {
        val api = FakeRetroAchievementsApi()
        api.completionResponsesByUser["userA"] = completionResponse(entry(1, title = "A-Game"))
        api.completionResponsesByUser["userB"] = completionResponse(entry(2, title = "B-Game"))
        api.gameInfoHandler = { gameInfo(it) }
        val gate = CompletableDeferred<Unit>()
        api.completionGate = gate
        val dao = FakeJourneyCacheDao()
        val credentials = mock<RetroAchievementsCredentialsRepository> {
            onBlocking { getCredentials() } doReturn RaCredentials("userA", "keyA") doReturn RaCredentials("userB", "keyB")
        }
        val repository = repo(api, dao, credentials = credentials, nowMillis = { testScheduler.currentTime })

        val userAResult = async { repository.playedGames() }
        val userBResult = async {
            repository.fetchOwn(
                JourneyMode.SPAN, gameId = null,
                spanFromMillis = base - D, spanToMillis = base + D, onProgress = { _, _, _ -> },
            )
        }

        // User A's fetch reaches (and suspends inside) the network call; user B's own fetch is
        // queued behind the client's request mutex at this point rather than sharing A's Deferred -
        // it cannot make progress until A's call releases that mutex, same as any two unrelated
        // calls would serialize, but it is its OWN fetch, not an awaiter on A's.
        advanceUntilIdle()
        assertEquals("only user A's completion fetch should have reached the network yet", 1, api.completionCalls)

        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals("each user must get their own completion fetch, never a shared one", 2, api.completionCalls)
        assertEquals(setOf("userA", "userB"), api.completionCallUsernames.toSet())
        assertEquals(listOf("A-Game"), userAResult.await().map { it.title })
        assertEquals(listOf("B-Game"), userBResult.await().completionByGame.values.map { it.title })
    }

    /**
     * The awaiter that started the fetch being cancelled does not release it. Pressing Load twice
     * in quick succession is exactly that: the first load's await is cancelled by the second, and
     * deregistering on the awaiter's way out let the second load start a whole second paged fetch
     * of the same up-to-2000 completion rows - the API burn the gate exists to prevent.
     */
    @Test
    fun `a cancelled awaiter leaves the in-flight completion fetch for the next caller`() = runTest {
        val api = FakeRetroAchievementsApi()
        api.completionResponse = completionResponse(entry(1), entry(2))
        val gate = CompletableDeferred<Unit>()
        api.completionGate = gate
        val repository = repo(api, FakeJourneyCacheDao(), nowMillis = { testScheduler.currentTime })

        val cancelled = async { repository.playedGames() }
        advanceUntilIdle()
        assertEquals(1, api.completionCalls)

        cancelled.cancel()
        advanceUntilIdle()

        val second = async { repository.playedGames() }
        advanceUntilIdle()
        assertEquals("the second caller must join the fetch already running", 1, api.completionCalls)

        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(2, second.await().size)
        assertEquals("no second paged fetch may have been started", 1, api.completionCalls)
    }

    // --- account switching ---

    /**
     * The signed-in player asks for their own rank with a null name, which used to fold every
     * account into one cache key. This class is an app-scoped singleton, so an account switch
     * happens underneath it: account B's closing card showed account A's rank on the same game.
     */
    @Test
    fun `the own game rank is keyed by the signed-in account`() = runTest {
        val api = FakeRetroAchievementsApi()
        api.gameRankHandler = { listOf(RaUserGameRankAndScoreEntry(user = "whoever", userRank = 12)) }
        val credentials = mock<RetroAchievementsCredentialsRepository> {
            onBlocking { getCredentials() } doReturn RaCredentials("userA", "keyA") doReturn RaCredentials("userB", "keyB")
        }
        val repository = repo(api, FakeJourneyCacheDao(), credentials = credentials, nowMillis = { testScheduler.currentTime })

        assertEquals(12, repository.fetchGameRank(42))

        api.gameRankHandler = { listOf(RaUserGameRankAndScoreEntry(user = "whoever", userRank = 900)) }
        assertEquals("the new account must not be served the old one's rank", 900, repository.fetchGameRank(42))

        assertEquals(listOf("userA", "userB"), api.gameRankUsers)
    }

    /** And each account's answer stays its own, rather than the last one asked winning. */
    @Test
    fun `each account's game rank is remembered apart from the other's`() = runTest {
        val api = FakeRetroAchievementsApi()
        api.gameRankHandler = { listOf(RaUserGameRankAndScoreEntry(user = "whoever", userRank = 12)) }
        val credentials = mock<RetroAchievementsCredentialsRepository> {
            onBlocking { getCredentials() } doReturn
                RaCredentials("userA", "keyA") doReturn
                RaCredentials("userB", "keyB") doReturn
                RaCredentials("userA", "keyA")
        }
        val repository = repo(api, FakeJourneyCacheDao(), credentials = credentials, nowMillis = { testScheduler.currentTime })

        assertEquals(12, repository.fetchGameRank(42))
        api.gameRankHandler = { listOf(RaUserGameRankAndScoreEntry(user = "whoever", userRank = 900)) }
        assertEquals(900, repository.fetchGameRank(42))
        assertEquals("back on the first account, from memory", 12, repository.fetchGameRank(42))

        assertEquals(2, api.gameRankCalls.size)
    }

    /** Same story for the span end state's site rank, which was a single unkeyed field. */
    @Test
    fun `the site rank is keyed by the signed-in account`() = runTest {
        val source = FakeSiteRankSource(answer = 1234)
        val credentials = mock<RetroAchievementsCredentialsRepository> {
            onBlocking { getCredentials() } doReturn RaCredentials("userA", "keyA") doReturn RaCredentials("userB", "keyB")
        }
        val repository = repo(
            FakeRetroAchievementsApi(), FakeJourneyCacheDao(), credentials = credentials,
            nowMillis = { testScheduler.currentTime }, siteRankSource = source,
        )

        assertEquals(1234, repository.siteRank())

        source.answer = 7
        assertEquals("the new account must not be served the old one's site rank", 7, repository.siteRank())
        assertEquals(2, source.calls)
    }

    /**
     * The on-disk cache belongs to one account. Nothing wiped it before: a previous account's rows
     * sat in the completion table forever (it has no eviction at all) and spent the game table's
     * 300-row budget on games the signed-in account will never ask for.
     */
    @Test
    fun `a load for a different account wipes the previous account's cached rows`() = runTest {
        val api = FakeRetroAchievementsApi()
        api.completionResponsesByUser["userA"] = completionResponse(entry(1, title = "A-Game"))
        api.completionResponsesByUser["userB"] = completionResponse(entry(2, title = "B-Game"))
        api.gameInfoHandler = { gameInfo(it) }
        val dao = FakeJourneyCacheDao()
        val credentials = mock<RetroAchievementsCredentialsRepository> {
            onBlocking { getCredentials() } doReturn RaCredentials("userA", "keyA") doReturn RaCredentials("userB", "keyB")
        }
        val repository = repo(api, dao, credentials = credentials, nowMillis = { testScheduler.currentTime })

        repository.fetchOwn(JourneyMode.SPAN, null, base - D, base + D, onProgress = { _, _, _ -> })
        // Rows are written under the lowercased account name, whatever case the account spells
        // itself with: RetroAchievements treats the two as one account, and so does this cache.
        assertEquals(setOf("usera|1"), dao.games.keys)
        assertEquals(setOf("usera"), dao.completions.keys)

        repository.fetchOwn(JourneyMode.SPAN, null, base - D, base + D, onProgress = { _, _, _ -> })

        assertEquals("the previous account's game rows must be gone", setOf("userb|2"), dao.games.keys)
        assertEquals("and its completion row with them", setOf("userb"), dao.completions.keys)
    }

    /**
     * The other half of that rule, and the one with no second chance if it breaks: a rival's rows
     * are keyed "&lt;own account&gt;|&lt;rival&gt;", so a wipe check that only recognised an exact match read
     * every one of them as a departed account and emptied the whole cache on the very next load.
     */
    @Test
    fun `a rival's rows belong to the signed-in account and survive its next load`() = runTest {
        val api = FakeRetroAchievementsApi()
        api.completionResponsesByUser["me"] = completionResponse(entry(1, title = "Mine"))
        api.completionResponsesByUser["rival"] = completionResponse(entry(2, title = "Theirs"))
        api.gameInfoHandler = { gameInfo(it) }
        val dao = FakeJourneyCacheDao()
        val repository = repo(api, dao, nowMillis = { testScheduler.currentTime })

        repository.fetchOwn(JourneyMode.SPAN, null, base - D, base + D, onProgress = { _, _, _ -> })
        repository.fetchRival("rival", JourneyMode.SPAN, null, base - D, base + D, "D90", onProgress = { _, _, _ -> })
        assertEquals(setOf("me", "me|rival"), dao.completions.keys)
        assertEquals(setOf("me|1", "me|rival|2"), dao.games.keys)

        // The same account, loading again. Nothing here is a switch, so nothing may be wiped.
        repository.clearSessionCache()
        repository.fetchOwn(JourneyMode.SPAN, null, base - D, base + D, onProgress = { _, _, _ -> })

        assertEquals("the rival's completion row must survive", setOf("me", "me|rival"), dao.completions.keys)
        assertEquals("and their game rows with it", setOf("me|1", "me|rival|2"), dao.games.keys)
    }

    /**
     * Forgetting is the user's delete button for another person's data, so it has to take all of
     * that person's rows and none of anybody else's.
     */
    @Test
    fun `forgetting one rival takes their rows and leaves everyone else's`() = runTest {
        val api = FakeRetroAchievementsApi()
        api.completionResponsesByUser["me"] = completionResponse(entry(1, title = "Mine"))
        api.completionResponsesByUser["ann"] = completionResponse(entry(2, title = "Ann's"))
        api.completionResponsesByUser["bob"] = completionResponse(entry(3, title = "Bob's"))
        api.gameInfoHandler = { gameInfo(it) }
        val dao = FakeJourneyCacheDao()
        val repository = repo(api, dao, nowMillis = { testScheduler.currentTime })

        repository.fetchOwn(JourneyMode.SPAN, null, base - D, base + D, onProgress = { _, _, _ -> })
        repository.fetchRival("ann", JourneyMode.SPAN, null, base - D, base + D, "D90", onProgress = { _, _, _ -> })
        repository.fetchRival("bob", JourneyMode.SPAN, null, base - D, base + D, "D90", onProgress = { _, _, _ -> })
        assertEquals(listOf("bob", "ann"), repository.knownRivals())

        repository.forgetRival("ann")

        assertEquals("only bob is still a recent", listOf("bob"), repository.knownRivals())
        assertEquals(setOf("me", "me|bob"), dao.completions.keys)
        assertEquals("ann's game rows go too", setOf("me|1", "me|bob|3"), dao.games.keys)
    }

    /** Recents are recents: the newest name compared against comes first, not the first alphabetically. */
    @Test
    fun `known rivals answer newest first`() = runTest {
        val api = FakeRetroAchievementsApi()
        api.completionResponsesByUser["me"] = completionResponse(entry(1))
        listOf("zoe", "ann").forEach { api.completionResponsesByUser[it] = completionResponse(entry(2)) }
        api.gameInfoHandler = { gameInfo(it) }
        val dao = FakeJourneyCacheDao()
        val repository = repo(api, dao, nowMillis = { testScheduler.currentTime })

        repository.fetchOwn(JourneyMode.SPAN, null, base - D, base + D, onProgress = { _, _, _ -> })
        repository.fetchRival("ann", JourneyMode.SPAN, null, base - D, base + D, "D90", onProgress = { _, _, _ -> })
        testScheduler.advanceTimeBy(1000)
        repository.fetchRival("zoe", JourneyMode.SPAN, null, base - D, base + D, "D90", onProgress = { _, _, _ -> })

        assertEquals(listOf("zoe", "ann"), repository.knownRivals())
    }

    /**
     * The bug the cap was raised for: a 450-game library evicting rows the same pass had just
     * written, so every Load refetched what the last one had cached.
     */
    @Test
    fun `a span larger than the old cap keeps every row it wrote`() = runTest {
        val api = FakeRetroAchievementsApi()
        api.completionResponse = completionResponse(*(1..450).map { entry(it) }.toTypedArray())
        api.gameInfoHandler = { gameInfo(it) }
        val dao = FakeJourneyCacheDao()
        val repository = repo(api, dao, nowMillis = { testScheduler.currentTime })

        repository.fetchOwn(JourneyMode.SPAN, null, base - D, base + D, onProgress = { _, _, _ -> })

        assertEquals("every game of the span must still be cached", 450, dao.games.size)
    }

    /** And the completion table, which gains a row per rival, does not grow without bound. */
    @Test
    fun `completion rows are evicted once the cap is passed`() = runTest {
        val api = FakeRetroAchievementsApi()
        api.completionResponsesByUser["me"] = completionResponse(entry(1))
        val rivals = listOf("ann", "bob", "cal", "dee")
        rivals.forEach { api.completionResponsesByUser[it] = completionResponse(entry(2)) }
        api.gameInfoHandler = { gameInfo(it) }
        val dao = FakeJourneyCacheDao()
        val repository = repo(api, dao, nowMillis = { testScheduler.currentTime }, completionCacheCap = 3)

        repository.fetchOwn(JourneyMode.SPAN, null, base - D, base + D, onProgress = { _, _, _ -> })
        rivals.forEach {
            testScheduler.advanceTimeBy(1000)
            repository.fetchRival(it, JourneyMode.SPAN, null, base - D, base + D, "D90", onProgress = { _, _, _ -> })
        }

        assertEquals("the table stays at its cap", 3, dao.completions.size)
        assertEquals("and keeps the newest names", listOf("me|dee", "me|cal", "me|bob"), dao.cachedCompletionUsers())
    }

    /** Disconnecting the account is a delete, and it has to be one now rather than on a later load. */
    @Test
    fun `clearing the cache empties both tables`() = runTest {
        val api = FakeRetroAchievementsApi()
        api.completionResponsesByUser["me"] = completionResponse(entry(1))
        api.completionResponsesByUser["ann"] = completionResponse(entry(2))
        api.gameInfoHandler = { gameInfo(it) }
        val dao = FakeJourneyCacheDao()
        val repository = repo(api, dao, nowMillis = { testScheduler.currentTime })

        repository.fetchOwn(JourneyMode.SPAN, null, base - D, base + D, onProgress = { _, _, _ -> })
        repository.fetchRival("ann", JourneyMode.SPAN, null, base - D, base + D, "D90", onProgress = { _, _, _ -> })

        repository.clearCache()

        assertTrue("no game rows may survive", dao.games.isEmpty())
        assertTrue("and no completion rows either", dao.completions.isEmpty())
        assertEquals(emptyList<String>(), repository.knownRivals())
    }

    /**
     * A username is one account however it is spelled. Keying the cache on the spelling would give
     * "Aria" and "aria" a set of rows each, two 300-row budgets, and a wipe on every switch between
     * two ways of writing the same name.
     */
    @Test
    fun `a difference of case is the same account, not a switch`() = runTest {
        val api = FakeRetroAchievementsApi()
        api.completionResponsesByUser["Aria"] = completionResponse(entry(1))
        api.completionResponsesByUser["aria"] = completionResponse(entry(1))
        api.gameInfoHandler = { gameInfo(it) }
        val dao = FakeJourneyCacheDao()
        val credentials = mock<RetroAchievementsCredentialsRepository> {
            onBlocking { getCredentials() } doReturn RaCredentials("Aria", "key") doReturn RaCredentials("aria", "key")
        }
        val repository = repo(api, dao, credentials = credentials, nowMillis = { testScheduler.currentTime })

        repository.fetchOwn(JourneyMode.SPAN, null, base - D, base + D, onProgress = { _, _, _ -> })
        repository.fetchOwn(JourneyMode.SPAN, null, base - D, base + D, onProgress = { _, _, _ -> })

        assertEquals(setOf("aria|1"), dao.games.keys)
        assertEquals("no wipe, so the completion cache still answers", 1, api.completionCalls)
        assertEquals("and the game is still validator-cached", 1, api.gameInfoCalls.size)
    }

    /**
     * A fetch left running for the account that just went is cancelled, not merely forgotten. It
     * outlives its caller by design (that is what makes the coalescing safe), so left alone it
     * lands after the wipe and writes the departed account's rows back into the table just cleared.
     */
    @Test
    fun `a wipe cancels a completion fetch still running for the previous account`() = runTest {
        val api = FakeRetroAchievementsApi()
        api.completionResponsesByUser["userA"] = completionResponse(entry(1, title = "A-Game"))
        api.completionResponsesByUser["userB"] = completionResponse(entry(2, title = "B-Game"))
        api.gameInfoHandler = { gameInfo(it) }
        val dao = FakeJourneyCacheDao()
        val credentials = mock<RetroAchievementsCredentialsRepository> {
            onBlocking { getCredentials() } doReturn
                RaCredentials("userA", "keyA") doReturn
                RaCredentials("userA", "keyA") doReturn
                RaCredentials("userB", "keyB")
        }
        val repository = repo(api, dao, credentials = credentials, nowMillis = { testScheduler.currentTime })

        repository.fetchOwn(JourneyMode.SPAN, null, base - D, base + D, onProgress = { _, _, _ -> })
        assertEquals(setOf("usera"), dao.completions.keys)

        // A's completion refetch is in flight when the account changes.
        kotlinx.coroutines.delay(31 * 60 * 1000L)
        val gate = CompletableDeferred<Unit>()
        api.completionGate = gate
        val strandedA = async { runCatching { repository.playedGames() } }
        advanceUntilIdle()

        api.completionGate = null
        repository.fetchOwn(JourneyMode.SPAN, null, base - D, base + D, onProgress = { _, _, _ -> })
        gate.complete(Unit)
        advanceUntilIdle()
        strandedA.await()

        assertEquals("the departed account's rows must not come back", setOf("userb"), dao.completions.keys)
        assertEquals(setOf("userb|2"), dao.games.keys)
    }

    /** The same account loading again is not an account switch, and keeps its cache. */
    @Test
    fun `a second load for the same account keeps the cache`() = runTest {
        val api = FakeRetroAchievementsApi()
        api.completionResponse = completionResponse(entry(1))
        api.gameInfoHandler = { gameInfo(it) }
        val dao = FakeJourneyCacheDao()
        val repository = repo(api, dao, nowMillis = { testScheduler.currentTime })

        repository.fetchOwn(JourneyMode.SPAN, null, base - D, base + D, onProgress = { _, _, _ -> })
        kotlinx.coroutines.delay(31 * 60 * 1000L)
        repository.fetchOwn(JourneyMode.SPAN, null, base - D, base + D, onProgress = { _, _, _ -> })

        assertEquals(setOf("me|1"), dao.games.keys)
        assertEquals("the validator hit means no refetch, which needs the row to still be there", 1, api.gameInfoCalls.size)
    }
}
