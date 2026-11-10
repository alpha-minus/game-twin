package com.twinlore.app.journey.data

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.twinlore.app.data.local.JourneyCacheDao
import com.twinlore.app.data.local.JourneyCompletionCacheEntity
import com.twinlore.app.data.local.JourneyGameCacheEntity
import com.twinlore.app.data.retroachievements.RaProfileRepository
import com.twinlore.app.data.retroachievements.RetroAchievementsCredentialsRepository
import com.twinlore.app.journey.domain.JourneyBuilder
import com.twinlore.app.journey.domain.JourneyParsing
import com.twinlore.app.journey.domain.model.CompletionRow
import com.twinlore.app.journey.domain.model.GameSnapshot
import com.twinlore.app.journey.domain.model.JourneyMode
import java.util.Collections
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The two things about a player that one `API_GetUserSummary` call answers: how
 * RetroAchievements spells their name, and where they stand on the site.
 *
 * They travel together because they arrive together. The spelling is what makes a profile picture
 * resolve (`/UserPic/{name}.png` is case sensitive while every RA lookup is not), and the rank is
 * what a compare row shows next to a player who has no single game to be ranked on - so one call,
 * asked for with `g=0&a=0`, covers both for the price of the cheaper one.
 */
data class PlayerSummary(val canonicalName: String?, val siteRank: Int?)

/** Result of fetching one player's (own or rival) games for a journey render. */
data class PlayerFetchResult(
    val snapshots: List<GameSnapshot>,
    val completionByGame: Map<Int, CompletionRow>,
    val failedCount: Int,
)

/**
 * Own- and rival-data access for the Achievement Journey, injected into the
 * ViewModel so tests can substitute a fake without pulling in Room/Retrofit.
 */
interface JourneyDataSource {
    /**
     * Own data: persistent cache + validator revalidation. onProgress(label, done, total).
     *
     * Deliberately not parameterised by hardcore: span candidates are always picked on the
     * softcore award count, which is a superset (a hardcore earn is also a softcore earn, so
     * NumAwarded >= NumAwardedHardcore). The hardcore filter is applied at build time instead,
     * so toggling it renders the full chart without refetching.
     */
    suspend fun fetchOwn(
        mode: JourneyMode,
        gameId: Int?,
        spanFromMillis: Long,
        spanToMillis: Long,
        onProgress: (String, Int, Int) -> Unit,
    ): PlayerFetchResult

    /**
     * Rival data: cached like the own data now (user request) - completion list and per-game
     * snapshots persisted with the same TTL and validator scheme, namespaced under the signed-in
     * account so an account switch takes the roster's rows with it. A session LRU still fronts it.
     *
     * [spanKey] identifies the window as the user *asked* for it (the preset, or the custom range's
     * day offsets) rather than as it resolved. The resolved millis come off the wall clock at each
     * load, so keying the cache on them meant two loads of the same window were always different
     * keys: the cache never hit, and every entry it made was retained for the life of the process.
     */
    suspend fun fetchRival(
        name: String,
        mode: JourneyMode,
        gameId: Int?,
        spanFromMillis: Long,
        spanToMillis: Long,
        spanKey: String,
        onProgress: (String, Int, Int) -> Unit,
    ): PlayerFetchResult

    /**
     * A player's rank on a game's leaderboard, or null when it cannot be had. [userName] names a
     * rival; null means the signed-in player.
     *
     * Game mode only, and deliberately best-effort: it is one line on the journey's closing card
     * and one column of the compare row, so a failure (no credentials, a rate limit, a player with
     * no rank on this game) is answered with null rather than an exception. Cached in memory per
     * game and player for the session, since the number does not move while a journey is being
     * watched.
     */
    suspend fun fetchGameRank(gameId: Int, userName: String? = null): Int?

    /**
     * The signed-in player's site-wide RetroAchievements rank, or null when it cannot be had.
     *
     * The span modes' answer to [fetchGameRank]: a chart of everything a player did in a window has
     * no single game to be ranked on, so the closing card names where they stand on the site. Read
     * off the profile the rest of the app already keeps ([SiteRankSource], backed by the cached
     * profile), so it costs no request of its own in the ordinary case, and best-effort in exactly
     * the same way - a failure is a missing card, never a failed load.
     */
    suspend fun siteRank(): Int?

    /**
     * The signed-in player's played-games list, most recent activity first, for the game-id picker.
     * Reuses the exact same cached-completion path [fetchOwn] uses (30-min TTL, stale fallback), so
     * a fresh call costs nothing extra beyond what a `fetchOwn` would already have paid for.
     */
    suspend fun playedGames(): List<CompletionRow>

    /**
     * One player's public summary: the spelling of their name, and their site rank.
     *
     * Best effort - a failure answers null rather than throwing, exactly as the rank lookups do,
     * because everything it feeds is a flourish (a face instead of an initial, a rank on a compare
     * row). Session-cached per case-folded name, so a roster is one call per player per visit even
     * across repeated loads, and it goes through the same serialized, rate-limited client every
     * other request does.
     *
     * The canonical spelling it brings back is also recorded for [canonicalName].
     */
    suspend fun playerSummary(name: String): PlayerSummary?

    /**
     * How RetroAchievements itself spells [name], if any response this session has already had
     * said so, and null otherwise.
     *
     * The media host's `/UserPic/{name}.png` path is case sensitive while every RA *lookup* is not,
     * so a rival typed in lowercase fetches perfectly and then 404s on their picture - which is why
     * rivals showed the initial-letter fallback disc on their curve ends instead of a face.
     *
     * Filled in by [playerSummary], and as a bonus by any `API_GetUserGameRankAndScore` the compare
     * row already asks for (its `User` field says the same thing for free). Session scoped, and
     * dropped with the rest by [clearSessionCache].
     */
    fun canonicalName(name: String): String?

    /**
     * The rivals this account has loaded before, most useful first, read off the persisted cache.
     *
     * The panel offers them as recents so a returning user picks a name instead of retyping it.
     * Empty until somebody has actually been compared against, and it never reaches beyond the
     * signed-in account: rival rows are namespaced under it (see the repository's rival cache key).
     */
    suspend fun knownRivals(): List<String>

    /**
     * Forgets one rival: their cached rows go, and so does their entry in this session's memory,
     * so the panel's recents drop them and a later compare starts from a clean fetch.
     */
    suspend fun forgetRival(name: String)

    /**
     * Drops everything this layer holds - both cached tables and the session's memory - for every
     * account, not merely the signed-in one.
     *
     * For the moment the account goes: disconnecting RetroAchievements in Settings, or signing a
     * different name in. The lazy [wipeCacheIfAccountChanged] is not enough on its own for either.
     * It runs on the next own-data load, so a user who disconnects and never opens the journey
     * again leaves the whole cache on disk - including rivals' progress, which is another
     * identified person's data on this device, and which the privacy notice says goes when the
     * account does. Deletion promised at the moment the user asks for it has to happen then.
     */
    suspend fun clearCache()

    /** Called when leaving the screen: drops rival + rank in-memory state. */
    fun clearSessionCache()
}

/**
 * Where the signed-in player's site-wide rank comes from.
 *
 * A one-function seam rather than a direct dependency on
 * [com.twinlore.app.data.retroachievements.RaProfileRepository]: that class needs a Context and a
 * DataStore, neither of which a repository unit test can supply, and all this layer wants from it
 * is one nullable number.
 */
fun interface SiteRankSource {
    suspend fun siteRank(): Int?
}

/**
 * The API-frugality core of the Achievement Journey: every own-data fetch is
 * validator-checked against the on-disk cache before touching the network, and
 * rival data lives only in memory for the current session.
 *
 * Every cache here is keyed by the account it belongs to, in-memory ones included: this class is
 * an app-scoped singleton, so a RetroAchievements account switch happens *underneath* it, and an
 * unkeyed entry is one account's number shown under another's name. The on-disk tables go further
 * and are wiped outright when the signed-in account changes ([wipeCacheIfAccountChanged]), so a
 * previous account's rows neither linger nor spend the row budget the current one is capped by.
 */
@Singleton
class JourneyRepository internal constructor(
    private val client: RaJourneyClient,
    private val credentials: RetroAchievementsCredentialsRepository,
    private val dao: JourneyCacheDao,
    private val nowMillis: () -> Long,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val siteRankSource: SiteRankSource? = null,
    /** The game table's row cap; a constructor seam so eviction stays testable at a sane size. */
    private val gameCacheCap: Int = MAX_CACHED_GAMES,
    /** The completion table's row cap; the same seam, for the same reason. */
    private val completionCacheCap: Int = MAX_CACHED_COMPLETION,
) : JourneyDataSource {

    @Inject
    constructor(
        client: RaJourneyClient,
        credentials: RetroAchievementsCredentialsRepository,
        dao: JourneyCacheDao,
        profile: RaProfileRepository,
    ) : this(
        client, credentials, dao, System::currentTimeMillis, Dispatchers.IO,
        // load(false) answers from the profile cache whenever it is fresh, so the ordinary journey
        // pays nothing for this; a cold cache costs the one summary call the profile screen would
        // have made anyway.
        SiteRankSource { profile.load(false)?.rank },
    )

    private val gson = Gson()
    private val completionListType = object : TypeToken<List<CompletionRow>>() {}.type

    // Rival + rank state: in-memory only, cleared by clearSessionCache().
    //
    // All four are synchronized maps. This class is a singleton reached from several coroutines at
    // once - a load's rival loop, an add, the picker - and a plain HashMap written from two threads
    // is a corrupt table, not a stale read. The check-then-put races that remain are benign: the
    // loser recomputes the same answer.
    //
    // The rival results are the heavy entries - a whole PlayerFetchResult, every GameSnapshot with
    // its achievement list - so that one is a bounded LRU. Access-ordered, so the window being
    // reloaded stays and the one nobody has come back to goes.
    private val rivalResultCache: MutableMap<String, PlayerFetchResult> = Collections.synchronizedMap(
        object : LinkedHashMap<String, PlayerFetchResult>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, PlayerFetchResult>): Boolean =
                size > MAX_RIVAL_RESULTS
        }
    )
    private val rivalCompletionCache: MutableMap<String, List<CompletionRow>> = Collections.synchronizedMap(mutableMapOf())
    // Both rank caches are keyed by the player the number is about, resolved: the signed-in player
    // is asked for as a null name, and folding that to one shared key served the previous account's
    // rank after a switch.
    private val gameRankCache: MutableMap<Pair<Int, String>, Int> = Collections.synchronizedMap(mutableMapOf())
    private val siteRankCache: MutableMap<String, Int> = Collections.synchronizedMap(mutableMapOf())
    // Lowercased name -> the spelling RA answered with. See [JourneyDataSource.canonicalName].
    private val canonicalNames: MutableMap<String, String> = Collections.synchronizedMap(mutableMapOf())
    // Lowercased name -> that player's summary. Only answers that arrived are kept, for the same
    // reason the rank caches do it: one rate-limited call must not cost the session a face.
    private val summaryCache: MutableMap<String, PlayerSummary> = Collections.synchronizedMap(mutableMapOf())

    // Coalesces concurrent own-completion fetches: fetchOwn() and playedGames() can both land on
    // an expired cache around the same time (e.g. the picker opened right as Load fired), and
    // without this they each run their own full paged fetch. A dedicated scope (not the caller's)
    // holds the in-flight fetch, so a caller being cancelled never cancels it out from under a
    // concurrent awaiter; the mutex only guards the small check-and-set below, never the fetch itself.
    // Keyed by the account's cache key, like every other cache here: an account switch mid-fetch
    // (A signs out, B signs in, opens the picker) must never hand B a Deferred that resolves to
    // A's rows.
    private val ownCompletionScope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val ownCompletionMutex = Mutex()
    private val ownCompletionInFlight = mutableMapOf<String, Deferred<List<CompletionRow>>>()

    /**
     * Serializes a rival's cache writes against a [forgetRival] of the same name.
     *
     * Forgetting is the user's delete button for somebody else's data, so it has to be a delete
     * rather than a delete-mostly. Without this, a span load already inside its per-game upsert
     * loop keeps writing after the delete has run: the games come back, the completion row does
     * not, and because recents are read off the completion table the rows are then invisible to
     * both the recents list and to a second forget - unreachable until an account switch.
     *
     * One lock rather than one per rival: a load writes at most [RIVAL_CAP] rivals and the section
     * it guards is a handful of indexed upserts, so the contention this could cost does not exist,
     * while a map of locks would need its own lifecycle.
     */
    private val rivalWriteMutex = Mutex()

    /**
     * The signed-in account, in both the forms this class needs it in.
     *
     * [apiName] is the account as it spells itself, which is what the API is asked with.
     * [cacheKey] is the one every cache here keys on: RetroAchievements treats a username
     * case-insensitively, so "Aria" and "aria" are one account, and letting the two spellings
     * become two cache identities meant two sets of rows, two row budgets, and a wipe that
     * fired on a difference of case alone.
     */
    private class OwnUser(val apiName: String, val apiKey: String) {
        val cacheKey: String = apiName.trim().lowercase()
    }

    private suspend fun ownUser(): OwnUser {
        val creds = credentials.getCredentials()
            // Retrying does not conjure an account; the screen's own gate catches this first in
            // every path that has one.
            ?: throw RaJourneyException("No RetroAchievements account configured", retryable = false)
        return OwnUser(creds.username, creds.apiKey)
    }

    override suspend fun playerSummary(name: String): PlayerSummary? = bestEffort {
        withContext(ioDispatcher) {
            val player = name.trim()
            if (player.isEmpty()) return@withContext null
            val key = player.lowercase()
            summaryCache[key]?.let { return@withContext it }
            val apiKey = credentials.getCredentials()?.apiKey ?: return@withContext null

            // g=0, a=0: neither the recent-games list nor the recent-achievements list is wanted,
            // and asking for them is the difference between a small response and a large one.
            val dto = client.call { it.getUserSummary(player, apiKey, 0, 0) }
            val summary = PlayerSummary(
                canonicalName = dto.user?.trim()?.takeIf { it.isNotEmpty() },
                siteRank = dto.rank?.takeIf { it > 0 },
            )
            summary.canonicalName?.let { canonicalNames[key] = it }
            summaryCache[key] = summary
            summary
        }
    }

    override fun canonicalName(name: String): String? = canonicalNames[name.trim().lowercase()]

    override suspend fun knownRivals(): List<String> = withContext(ioDispatcher) {
        val prefix = ownUser().cacheKey + "|"
        // Off the completion table rather than the game table: one row per rival either way, and
        // this one is written the moment a rival is fetched at all, including a single-game
        // compare that cached no span games. The spelling is the case-folded key the cache is
        // built on; RA looks names up case-insensitively, so re-adding one works either way, and
        // a canonical spelling seen this session is preferred when there is one.
        dao.cachedCompletionUsers()
            .filter { it.startsWith(prefix) && it.length > prefix.length }
            .map { it.removePrefix(prefix) }
            .map { canonicalNames[it] ?: it }
            .distinct()
        // Newest fetch first, as the DAO reads them: the panel shows a bounded number of chips and
        // a chip is the only way to forget one rival, so sorting these alphabetically put whoever
        // came late in the alphabet permanently out of reach of both.
    }

    override suspend fun forgetRival(name: String): Unit = withContext(ioDispatcher) {
        val key = rivalCacheKey(ownUser().cacheKey, name)
        rivalWriteMutex.withLock {
            // Bumped first, and under the same lock as the deletes: a fetch that is part way
            // through its per-game loop reads this number back before each remaining write and
            // drops the ones that would land after the delete. Without it a forgotten rival came
            // back as an orphaned pile of game rows with no completion row - invisible to recents
            // and so to a second forget. See [rivalWriteMutex].
            rivalForgetGenerations[key] = rivalGeneration(key).forgets + 1
            dao.deleteGamesFor(key)
            dao.deleteCompletionFor(key)
            // The session's copies too, or the name would keep answering from memory for the rest
            // of the visit and reappear in recents the next time anything refreshed them.
            val folded = name.trim().lowercase()
            rivalCompletionCache.remove(folded)
            rivalResultCache.keys.filter { it.startsWith("$folded|") }.forEach { rivalResultCache.remove(it) }
            canonicalNames.remove(folded)
            summaryCache.remove(folded)
        }
    }

    override suspend fun clearCache(): Unit = withContext(ioDispatcher) {
        dao.clearGames()
        dao.clearCompletion()
        clearSessionCache()
        // Everything in flight goes too, including the signed-in account's own fetch: unlike the
        // account-change wipe, nobody is left who is entitled to the answer, and a fetch allowed
        // to land would write the rows back into the tables just emptied.
        ownCompletionMutex.withLock {
            ownCompletionInFlight.values.forEach { it.cancel() }
            ownCompletionInFlight.clear()
        }
        // Any rival write still in its loop is refused for the same reason.
        rivalWriteMutex.withLock { cacheEpoch++ }
    }

    override fun clearSessionCache() {
        rivalResultCache.clear()
        rivalCompletionCache.clear()
        gameRankCache.clear()
        siteRankCache.clear()
        canonicalNames.clear()
        summaryCache.clear()
    }

    // Every entry point hops to IO: the bodies below do Room round-trips and Gson (de)serialization
    // of whole game snapshots, and the only caller is a ViewModel coroutine on the main dispatcher.
    override suspend fun fetchOwn(
        mode: JourneyMode,
        gameId: Int?,
        spanFromMillis: Long,
        spanToMillis: Long,
        onProgress: (String, Int, Int) -> Unit,
    ): PlayerFetchResult = withContext(ioDispatcher) {
        val user = ownUser()
        wipeCacheIfAccountChanged(user.cacheKey)

        val completion = getOwnCompletion(user)
        val completionByGame = completion.associateBy { it.gameId }

        when (mode) {
            JourneyMode.GAME -> fetchOwnGame(user, requireGameId(gameId), completionByGame)
            JourneyMode.SPAN -> fetchOwnSpan(user, spanFromMillis, spanToMillis, completion, completionByGame, onProgress)
        }
    }

    override suspend fun fetchRival(
        name: String,
        mode: JourneyMode,
        gameId: Int?,
        spanFromMillis: Long,
        spanToMillis: Long,
        spanKey: String,
        onProgress: (String, Int, Int) -> Unit,
    ): PlayerFetchResult = withContext(ioDispatcher) {
        // Keyed on the request, not on the clock: see the interface doc on [spanKey] - and on the
        // name CASE-FOLDED, like every other cache here. RetroAchievements treats a username
        // case-insensitively, and the journey now re-spells a rival the way RA spells them once a
        // response says so ([canonicalName]), so the very next load asks for "Aria" where the first
        // one asked for "aria". A case-sensitive key made that a guaranteed miss: the roster was
        // refetched in full every time, and each spelling kept its own copy of a whole span's game
        // snapshots in a cache that holds eight.
        val cacheKey = "${name.trim().lowercase()}|$mode|$gameId|$spanKey"
        rivalResultCache[cacheKey]?.let { return@withContext it }

        // The signed-in account, for its API key and for the cache namespace rival rows are
        // written under; a rival cannot be fetched without an own account either way.
        val user = ownUser()
        // Cheap and it closes the window where a rival is added under a new account before that
        // account has loaded anything of its own: without it the fetch writes newKey|rival rows
        // beside the departed account's, and the next own load wipes both. Two indexed selects.
        wipeCacheIfAccountChanged(user.cacheKey)

        // What "this rival has not been forgotten since we started" means, read once, before the
        // first network call. Every write below is refused if the number has moved. See
        // [rivalWriteMutex].
        val diskKey = rivalCacheKey(user.cacheKey, name)
        val gen = rivalGeneration(diskKey)

        val completion = getRivalCompletion(user, name, gen)
        val completionByGame = completion.associateBy { it.gameId }

        val result = when (mode) {
            JourneyMode.GAME -> fetchRivalGame(user, name, requireGameId(gameId), completionByGame, gen)
            JourneyMode.SPAN -> fetchRivalSpan(user, name, spanFromMillis, spanToMillis, completion, completionByGame, gen, onProgress)
        }
        writeRival(diskKey, gen) { rivalResultCache[cacheKey] = result }
        result
    }

    override suspend fun playedGames(): List<CompletionRow> = withContext(ioDispatcher) {
        val user = ownUser()
        wipeCacheIfAccountChanged(user.cacheKey)
        val completion = getOwnCompletion(user)
        // Most recent activity first; rows with an unparseable/missing date sort last rather than
        // dropping out, so a row that has never had a valid date still shows up (just at the bottom).
        completion.sortedByDescending { JourneyParsing.parseWhen(it.mostRecentAwardedDate) ?: Long.MIN_VALUE }
    }

    /**
     * Runs a rank lookup for what it is worth, answering null for anything that goes wrong.
     *
     * The whole body sits inside, not just the network call: the credential read and the dispatcher
     * hop can throw too, and every caller treats these two functions as "best-effort by
     * construction". Making that true here hardens all of them at once instead of asking each call
     * site to remember a try/catch.
     *
     * Cancellation is emphatically not swallowed - it is how a newer load ends an older one.
     */
    private inline fun bestEffortRank(block: () -> Int?): Int? = bestEffort(block)

    /**
     * The same promise for anything nullable: a failure is an answer of null.
     *
     * Cancellation is emphatically not swallowed - it is how a newer load ends an older one, and a
     * cancellation quietly turned into a null is a coroutine that carries on doing work for a visit
     * that is over.
     */
    private inline fun <T> bestEffort(block: () -> T?): T? =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            null
        }

    override suspend fun fetchGameRank(gameId: Int, userName: String?): Int? = bestEffortRank {
        withContext(ioDispatcher) {
            val creds = credentials.getCredentials()
            // Keyed on the name as RESOLVED, never as asked for: the signed-in player is asked for
            // as a null name, and folding that to one shared key is how account B's chart came to
            // show account A's rank on the same game - this class outlives any one signed-in
            // account.
            val player = userName?.trim()?.takeIf { it.isNotEmpty() } ?: creds?.username.orEmpty()
            val key = gameId to player.lowercase()
            gameRankCache[key]?.let { return@withContext it }
            if (creds == null) return@withContext null

            val entry = runCatching {
                client.call { it.getUserGameRankAndScore(player, creds.apiKey, gameId) }
            }.getOrNull()
                // The endpoint answers with a single-entry array, and with an empty one for a player
                // who has no rank on the game at all.
                ?.firstOrNull()

            // The one thing on this response nobody was reading: the name, spelled the way RA
            // spells it. It costs nothing to keep, and it is what makes a rival's avatar URL
            // resolve for someone who typed their name in lowercase. Kept even when the rank
            // itself is missing or zero - the two facts are independent.
            entry?.user?.trim()?.takeIf { it.isNotEmpty() }
                ?.let { canonicalNames[player.lowercase()] = it }

            val rank = entry?.userRank?.takeIf { it > 0 }

            // Only answers that arrived are kept: caching a miss would turn one rate-limited call
            // into a rank the session can never show, and asking again costs one call.
            if (rank != null) gameRankCache[key] = rank
            rank
        }
    }

    override suspend fun siteRank(): Int? = bestEffortRank {
        withContext(ioDispatcher) {
            // Keyed by account for the same reason the game rank is: an unkeyed field showed the
            // previous account's "Site rank #N" on every preset-span journey after a switch.
            val player = credentials.getCredentials()?.username?.trim()?.lowercase().orEmpty()
            siteRankCache[player]?.let { return@withContext it }
            // Same rule as the game rank above: only an answer that arrived is remembered, so one
            // failure does not cost the session its card.
            val rank = runCatching { siteRankSource?.siteRank() }.getOrNull()?.takeIf { it > 0 }
            if (rank != null) siteRankCache[player] = rank
            rank
        }
    }

    /**
     * Drops the on-disk journey cache when it belongs to a different RetroAchievements account
     * than the one now signed in.
     *
     * The cached rows are their own record of whose they are, so no separate marker has to be kept
     * in step with them: any row under another cache key means the account changed since it was
     * written. Both tables go, not just the other account's rows - the completion table has no
     * eviction of its own, and the game table's row cap was being shared out between accounts
     * that will never look at each other's rows.
     *
     * [cacheKey] is the lowercased name every row is written under ([OwnUser]), so this is a plain
     * equality check rather than a case-insensitive one.
     */
    private suspend fun wipeCacheIfAccountChanged(cacheKey: String) {
        val cached = dao.cachedGameUsers() + dao.cachedCompletionUsers()
        // A rival row belongs to the account whose roster fetched it - its key is the account's
        // own key with the rival's name behind a separator - so it counts as ours here. Without
        // the prefix rule every rival row read as a departed account and wiped the table on the
        // very next load.
        if (cached.all { it == cacheKey || it.startsWith("$cacheKey|") }) return
        dao.clearGames()
        dao.clearCompletion()
        // A departed account's rival fetch can be mid-loop; the bump refuses its remaining writes
        // for the same reason the in-flight completion fetch below is cancelled.
        rivalWriteMutex.withLock { cacheEpoch++ }
        // The in-memory halves are keyed by account and so would answer correctly anyway; they are
        // dropped with the rest because nothing in them is worth keeping for an account that is no
        // longer signed in.
        clearSessionCache()
        // Including anything still being fetched for the account that just went. An in-flight
        // completion fetch is the one piece of session state that outlives its caller by design -
        // that is what makes coalescing safe - so left alone it would land after this wipe and
        // write the departed account's rows straight back into the table just cleared. Nobody
        // signed in wants them, so it is cancelled rather than merely forgotten. The signed-in
        // account's own entry stays: a concurrent caller is still entitled to join it.
        ownCompletionMutex.withLock {
            val departed = ownCompletionInFlight.keys.filter { it != cacheKey }
            departed.forEach { ownCompletionInFlight.remove(it)?.cancel() }
        }
    }

    // --- own data: completion list ---

    private suspend fun getOwnCompletion(user: OwnUser): List<CompletionRow> {
        val cached = dao.getCompletion(user.cacheKey)
        if (cached != null && nowMillis() - cached.fetchedAt < COMPLETION_TTL_MS) {
            return deserializeCompletion(cached.payloadJson)
        }

        // The read-check-fetch above is not atomic, so a second caller can land here while the
        // first caller's fetch is already running (both saw the same expired/absent cache). Rather
        // than each starting its own paged fetch, the first one to get here starts it and every
        // concurrent caller for the SAME account awaits that same Deferred; a different account
        // gets (and starts) its own entry, never the other user's in-flight rows. A failure
        // completes the Deferred exceptionally, which Kotlin's coroutines replay to every awaiter,
        // not just the one that started it.
        //
        // Deregistration hangs off the Deferred's own completion rather than off an awaiter
        // leaving. Tied to the awaiter, it undid the gate on the commonest race there is: press
        // Load twice in quick succession, the first load's await is cancelled, its cleanup removed
        // the entry while the fetch itself deliberately kept running, and the second load - seeing
        // the same expired cache and an empty map - started a second full paged fetch of the same
        // up-to-2000 rows. The handler is registered before the coroutine is allowed to run, so the
        // entry is removed exactly once, by the fetch that owns it.
        val deferred = ownCompletionMutex.withLock {
            ownCompletionInFlight[user.cacheKey] ?: ownCompletionScope.async(start = CoroutineStart.LAZY) {
                try {
                    val rows = fetchAllCompletionPages(user.apiName, user.apiKey)
                    dao.upsertCompletion(JourneyCompletionCacheEntity(user.cacheKey, nowMillis(), gson.toJson(rows)))
                    rows
                } catch (e: RaJourneyException) {
                    // Better stale than dead: fall back to the last known-good cache.
                    if (cached != null) deserializeCompletion(cached.payloadJson) else throw e
                }
            }.also { started ->
                ownCompletionInFlight[user.cacheKey] = started
                started.invokeOnCompletion {
                    // The map is guarded by the mutex and this handler runs on whatever thread
                    // finished the fetch, so the removal is posted to the repository's own scope
                    // rather than done here. NonCancellable for the same reason it always was: a
                    // stale entry parked in the map forever would have every later call for this
                    // account awaiting a Deferred that already completed.
                    ownCompletionScope.launch(NonCancellable) {
                        ownCompletionMutex.withLock {
                            if (ownCompletionInFlight[user.cacheKey] === started) {
                                ownCompletionInFlight.remove(user.cacheKey)
                            }
                        }
                    }
                }
                started.start()
            }
        }

        return deferred.await()
    }

    /**
     * The key a rival's persisted rows live under: the signed-in account's own key, a separator no
     * username can carry, and the rival's case-folded name. Namespacing under the OWN account is
     * what lets [wipeCacheIfAccountChanged] tell a rival row from a departed account's row - and
     * it means an account switch takes the old roster's data with it, which is the retention rule
     * the privacy notice describes.
     */
    private fun rivalCacheKey(own: String, rival: String): String =
        // The rival half is scrubbed of the separator as well as case-folded. RA's own usernames
        // cannot carry a "|", so this changes nothing for a real name - but the field this comes
        // from is free text a user types, and a key of three parts where the code reads two is a
        // shape the prefix rules above are not written for.
        "$own|${rival.trim().lowercase().replace('|', '_')}"

    /**
     * What a rival fetch has to still be true for its writes to land: the tables have not been
     * emptied under it, and this particular rival has not been forgotten. See [rivalWriteMutex].
     *
     * Counters rather than flags, because both events are ordinary and repeatable: a rival
     * forgotten and then compared against again must be allowed to write the second time.
     */
    private data class RivalWriteToken(val cacheEpoch: Int, val forgets: Int)

    private fun rivalGeneration(diskKey: String) =
        RivalWriteToken(cacheEpoch, rivalForgetGenerations[diskKey] ?: 0)

    @Volatile private var cacheEpoch = 0
    private val rivalForgetGenerations = java.util.concurrent.ConcurrentHashMap<String, Int>()

    /** Runs [write] unless [diskKey] has been forgotten or the tables emptied since [gen] was read. */
    private suspend fun writeRival(diskKey: String, gen: RivalWriteToken, write: suspend () -> Unit) {
        rivalWriteMutex.withLock {
            if (rivalGeneration(diskKey) != gen) return
            write()
        }
    }

    /** Case-folded key, for the same reason [fetchRival]'s is: "Aria" and "aria" are one account. */
    private suspend fun getRivalCompletion(user: OwnUser, name: String, gen: RivalWriteToken): List<CompletionRow> {
        val memKey = name.trim().lowercase()
        rivalCompletionCache[memKey]?.let { return it }

        // Persisted like the own list (user request: rival data is cached across sessions now),
        // with the same TTL and the same better-stale-than-dead fallback.
        val diskKey = rivalCacheKey(user.cacheKey, name)
        val cached = dao.getCompletion(diskKey)
        if (cached != null && nowMillis() - cached.fetchedAt < COMPLETION_TTL_MS) {
            val rows = deserializeCompletion(cached.payloadJson)
            rivalCompletionCache[memKey] = rows
            return rows
        }
        val rows = try {
            val fetched = fetchAllCompletionPages(name, user.apiKey)
            writeRival(diskKey, gen) {
                dao.upsertCompletion(JourneyCompletionCacheEntity(diskKey, nowMillis(), gson.toJson(fetched)))
                // Written under the same lock as the row it belongs to: the completion table is
                // what [knownRivals] reads, so a row that survives a forget puts the name back in
                // recents, and one that does not while its games do makes those games unreachable.
                rivalCompletionCache[memKey] = fetched
                evictExcessCompletion()
            }
            fetched
        } catch (e: RaJourneyException) {
            if (cached == null) throw e
            val stale = deserializeCompletion(cached.payloadJson)
            writeRival(diskKey, gen) { rivalCompletionCache[memKey] = stale }
            stale
        }
        return rows
    }

    private suspend fun fetchAllCompletionPages(userName: String, apiKey: String): List<CompletionRow> {
        val rows = mutableListOf<CompletionRow>()
        var offset = 0
        while (offset < MAX_COMPLETION_OFFSET) {
            val response = client.call { it.getUserCompletionProgress(userName, apiKey, 500, offset) }
            val pageRows = JourneyMappers.toCompletionRows(response)
            if (pageRows.isEmpty()) break
            rows += pageRows
            offset += pageRows.size
            val total = response.total ?: Int.MAX_VALUE
            if (offset >= total) break
        }
        return rows
    }

    private fun deserializeCompletion(json: String): List<CompletionRow> =
        gson.fromJson(json, completionListType)

    // --- own data: game mode ---

    private suspend fun fetchOwnGame(
        user: OwnUser,
        gameId: Int,
        completionByGame: Map<Int, CompletionRow>,
    ): PlayerFetchResult {
        val currentRow = completionByGame[gameId]
        val currentValidator = currentRow ?: syntheticValidator(gameId)

        // A cached row that matches its validator but was written before `UserTotalPlaytime` was
        // threaded through. Held rather than returned: the row is otherwise perfectly good, so it
        // is what a failed refetch falls back to.
        var stalePlaytime: GameSnapshot? = null

        val cached = dao.getGame(user.cacheKey, gameId)
        if (cached != null) {
            val cachedValidator = gson.fromJson(cached.validatorJson, CompletionRow::class.java)
            if (validatorMatches(cachedValidator, currentValidator)) {
                val snapshot = gson.fromJson(cached.payloadJson, GameSnapshot::class.java)
                // The validator only sees what the completion list says: how many awards, and when
                // the last one landed. A game whose snapshot was cached before this field existed
                // matches it forever, so the header's "Played 42h 10m over 21 days" quietly lost
                // its first half for the rest of that row's life - and it is the field a finished
                // single-game journey is most about. One refetch per stale row fixes it for good,
                // because the write below stores the refreshed payload under the same validator.
                if (snapshot.userTotalPlaytimeSeconds != null) {
                    return PlayerFetchResult(listOf(snapshot), completionByGame, 0)
                }
                stalePlaytime = snapshot
            }
        }

        // A single-game fetch failure has nothing else to render; let it throw up to the caller -
        // unless a cached row was set aside above, in which case the journey it would have drawn is
        // still the right one, minus the hours nobody could fetch.
        val dto = try {
            client.call { it.getGameInfoAndUserProgress(user.apiName, user.apiKey, gameId, 1) }
        } catch (e: RaJourneyException) {
            val fallback = stalePlaytime ?: throw e
            return PlayerFetchResult(listOf(fallback), completionByGame, 0)
        }
        val snapshot = JourneyMappers.toGameSnapshot(dto, fallbackIconPath = currentRow?.iconPath)
        val validatorToStore = currentRow ?: syntheticValidator(gameId, title = snapshot.title, iconPath = snapshot.iconPath)
        dao.upsertGame(JourneyGameCacheEntity(user.cacheKey, gameId, nowMillis(), gson.toJson(validatorToStore), gson.toJson(snapshot)))
        // Single-game mode writes a row per distinct game viewed and used to check the cap nowhere
        // at all: a player who only ever opens a vault's Achievements tab (which drives this path
        // exclusively) grew the table forever.
        evictExcessGames(keepAtLeast = 1)
        return PlayerFetchResult(listOf(snapshot), completionByGame, 0)
    }

    private suspend fun fetchRivalGame(
        user: OwnUser,
        name: String,
        gameId: Int,
        completionByGame: Map<Int, CompletionRow>,
        gen: RivalWriteToken,
    ): PlayerFetchResult {
        // The same validator scheme the own path runs: the rival's completion row says whether
        // anything changed in this game since the row was written, and a match serves the cached
        // snapshot without a network call.
        val diskKey = rivalCacheKey(user.cacheKey, name)
        val currentRow = completionByGame[gameId]
        val currentValidator = currentRow ?: syntheticValidator(gameId)
        val cached = dao.getGame(diskKey, gameId)
        if (cached != null) {
            val cachedValidator = gson.fromJson(cached.validatorJson, CompletionRow::class.java)
            if (validatorMatches(cachedValidator, currentValidator)) {
                return PlayerFetchResult(
                    listOf(gson.fromJson(cached.payloadJson, GameSnapshot::class.java)),
                    completionByGame, 0,
                )
            }
        }
        val dto = client.call { it.getGameInfoAndUserProgress(name, user.apiKey, gameId, 1) }
        val snapshot = JourneyMappers.toGameSnapshot(dto, fallbackIconPath = currentRow?.iconPath)
        writeRival(diskKey, gen) {
            dao.upsertGame(JourneyGameCacheEntity(diskKey, gameId, nowMillis(), gson.toJson(currentValidator), gson.toJson(snapshot)))
            evictExcessGames(keepAtLeast = 1)
        }
        return PlayerFetchResult(listOf(snapshot), completionByGame, 0)
    }

    // --- span mode ---

    private suspend fun fetchOwnSpan(
        user: OwnUser,
        spanFromMillis: Long,
        spanToMillis: Long,
        completion: List<CompletionRow>,
        completionByGame: Map<Int, CompletionRow>,
        onProgress: (String, Int, Int) -> Unit,
    ): PlayerFetchResult {
        val picked = JourneyBuilder.pickSpanGames(completion, spanFromMillis, spanToMillis)
        val snapshots = mutableListOf<GameSnapshot>()
        var failedCount = 0

        picked.forEachIndexed { index, row ->
            onProgress(row.title, index + 1, picked.size)
            try {
                val cached = dao.getGame(user.cacheKey, row.gameId)
                val cachedValidator = cached?.let { gson.fromJson(it.validatorJson, CompletionRow::class.java) }
                if (cached != null && cachedValidator != null && validatorMatches(cachedValidator, row)) {
                    snapshots += gson.fromJson(cached.payloadJson, GameSnapshot::class.java)
                    return@forEachIndexed
                }
                val dto = client.call { it.getGameInfoAndUserProgress(user.apiName, user.apiKey, row.gameId, 1) }
                val snapshot = JourneyMappers.toGameSnapshot(dto, fallbackIconPath = row.iconPath)
                dao.upsertGame(JourneyGameCacheEntity(user.cacheKey, row.gameId, nowMillis(), gson.toJson(row), gson.toJson(snapshot)))
                snapshots += snapshot
            } catch (e: RaJourneyException) {
                failedCount++
            }
        }

        evictExcessGames(keepAtLeast = picked.size)

        return PlayerFetchResult(snapshots, completionByGame, failedCount)
    }

    /**
     * Trims the game cache back to its cap, without ever evicting a row the fetch that is calling
     * this just wrote.
     *
     * [keepAtLeast] is how many games the current pass is about. Span candidates are uncapped, so
     * an "all time" span for a completionist could fetch well over the row cap in one call:
     * rows are stamped with the time they were fetched, one network call apart, which made this
     * pass's own earliest rows the oldest in the table and the first ones deleted. Games the user
     * was watching at that moment had already lost their cache entry before the load finished, and
     * the next Load refetched them from the network - the accounts caching matters most for got the
     * least out of it. Raising the floor to the size of the current pass keeps the deletion to rows
     * no older call is going to want first.
     */
    private suspend fun evictExcessGames(keepAtLeast: Int) {
        val cap = maxOf(gameCacheCap, keepAtLeast)
        val count = dao.countGames()
        if (count > cap) dao.evictOldestGames(count - cap)
    }

    /**
     * Trims the completion table back to its cap.
     *
     * Called only after a *rival's* list is written, because that is the one that multiplies: the
     * signed-in account has exactly one completion row and it is refreshed in place, while a rival
     * adds a row apiece and a serialized list runs to a couple of thousand entries. Nothing evicted
     * it before rival data started persisting, so nothing needed to.
     */
    private suspend fun evictExcessCompletion() {
        val count = dao.countCompletion()
        if (count > completionCacheCap) dao.evictOldestCompletion(count - completionCacheCap)
    }

    private suspend fun fetchRivalSpan(
        user: OwnUser,
        name: String,
        spanFromMillis: Long,
        spanToMillis: Long,
        completion: List<CompletionRow>,
        completionByGame: Map<Int, CompletionRow>,
        gen: RivalWriteToken,
        onProgress: (String, Int, Int) -> Unit,
    ): PlayerFetchResult {
        val picked = JourneyBuilder.pickSpanGames(completion, spanFromMillis, spanToMillis)
        val diskKey = rivalCacheKey(user.cacheKey, name)
        val snapshots = mutableListOf<GameSnapshot>()
        var failedCount = 0

        picked.forEachIndexed { index, row ->
            onProgress(row.title, index + 1, picked.size)
            try {
                // Validator-cached exactly like the own span above: a rival game nothing changed
                // in serves from disk, so a repeat compare costs the completion pages and little
                // else.
                val cached = dao.getGame(diskKey, row.gameId)
                val cachedValidator = cached?.let { gson.fromJson(it.validatorJson, CompletionRow::class.java) }
                if (cached != null && cachedValidator != null && validatorMatches(cachedValidator, row)) {
                    snapshots += gson.fromJson(cached.payloadJson, GameSnapshot::class.java)
                    return@forEachIndexed
                }
                val dto = client.call { it.getGameInfoAndUserProgress(name, user.apiKey, row.gameId, 1) }
                val snapshot = JourneyMappers.toGameSnapshot(dto, fallbackIconPath = row.iconPath)
                writeRival(diskKey, gen) {
                    dao.upsertGame(JourneyGameCacheEntity(diskKey, row.gameId, nowMillis(), gson.toJson(row), gson.toJson(snapshot)))
                }
                snapshots += snapshot
            } catch (e: RaJourneyException) {
                failedCount++
            }
        }

        writeRival(diskKey, gen) { evictExcessGames(keepAtLeast = picked.size) }

        return PlayerFetchResult(snapshots, completionByGame, failedCount)
    }

    private fun requireGameId(gameId: Int?): Int =
        // Also permanent: pressing Try again cannot pick a game. The panel's own gate answers this
        // first, so this is the unreachable half of the same rule.
        gameId ?: throw RaJourneyException("No game selected for the journey", retryable = false)

    private fun syntheticValidator(gameId: Int, title: String = "", iconPath: String? = null) =
        CompletionRow(
            gameId = gameId, title = title, iconPath = iconPath,
            numAwarded = 0, numAwardedHardcore = 0,
            mostRecentAwardedDate = null, highestAwardKind = null, highestAwardDate = null,
        )

    private fun validatorMatches(cached: CompletionRow, current: CompletionRow): Boolean =
        cached.mostRecentAwardedDate == current.mostRecentAwardedDate &&
            cached.numAwarded == current.numAwarded &&
            cached.numAwardedHardcore == current.numAwardedHardcore

    private companion object {
        const val COMPLETION_TTL_MS = 30 * 60 * 1000L
        // Effectively "cache everything either side of a compare ever loads" (user request):
        // the eviction below stays as a runaway backstop, not a budget anyone is expected to hit.
        const val MAX_CACHED_GAMES = 20_000

        /**
         * How many completion lists are kept.
         *
         * One belongs to the signed-in account and the rest are rivals, so this is "how many
         * people you have compared against lately", and 64 is far more than a recents list of
         * eight ever shows. A cap at all because a completion list is the big row here - up to
         * [MAX_COMPLETION_OFFSET] entries serialized, a few hundred KB for a completionist - and
         * this is the one table that gains a row per new name rather than reusing one.
         */
        const val MAX_CACHED_COMPLETION = 64
        const val MAX_COMPLETION_OFFSET = 2000

        /**
         * How many rival fetch results are kept in memory. One entry is a whole span's worth of
         * game snapshots for one player, so this is the difference between a session that holds a
         * few and one that holds every window anyone ever looked at.
         */
        const val MAX_RIVAL_RESULTS = 8
    }
}
