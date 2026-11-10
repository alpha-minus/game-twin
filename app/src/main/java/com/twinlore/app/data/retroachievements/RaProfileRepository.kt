package com.twinlore.app.data.retroachievements

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RaProfileRepository internal constructor(
    private val apiClient: RetroAchievementsApiClient,
    private val credentialsRepository: RetroAchievementsCredentialsRepository,
    private val dataStore: DataStore<Preferences>,
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        apiClient: RetroAchievementsApiClient,
        credentialsRepository: RetroAchievementsCredentialsRepository,
    ) : this(apiClient, credentialsRepository, context.raProfileDataStore)

    companion object {
        private val Context.raProfileDataStore: DataStore<Preferences> by preferencesDataStore(name = "ra_profile")
        private val PROFILE_JSON_KEY = stringPreferencesKey("profile_json")
        private val PROFILE_SYNC_TIME_KEY = longPreferencesKey("profile_sync_time")

        /**
         * Which account the cached blob was fetched for. Absent in anything written before this
         * key existed, which reads as a mismatch and costs one refresh - the right answer for a
         * cache whose owner cannot be established.
         */
        private val PROFILE_USER_KEY = stringPreferencesKey("profile_user")
        private const val PROFILE_CACHE_TTL_MS = 15 * 60 * 1000L

        /**
         * Whether a cached blob is the signed-in account's at all.
         *
         * Asked separately from freshness because the two readers want different things: [profile]
         * will show a stale blob (that is the point of a cache the app can open offline on) but
         * must never show somebody else's, while [load] wants both.
         */
        internal fun belongsTo(storedUser: String?, currentUser: String?): Boolean =
            storedUser != null && currentUser != null && storedUser.equals(currentUser, ignoreCase = true)

        /**
         * Whether a cached profile may be served without a call: it has to be fresh AND belong to
         * the account that is asking.
         *
         * The TTL was the whole test until now, and the blob it gates is the source of the site
         * rank the Achievement Journey puts on its closing card. Switch RetroAchievements accounts
         * inside the 15-minute window and the new account was shown the old one's profile and rank,
         * with no network call to notice the difference.
         */
        internal fun cacheUsable(storedUser: String?, currentUser: String?, syncTime: Long, now: Long): Boolean =
            now - syncTime < PROFILE_CACHE_TTL_MS && belongsTo(storedUser, currentUser)
    }

    private val gson = Gson()

    /**
     * Flow of the cached profile; emits null when there is no cache, when it cannot be parsed, or
     * when it belongs to another account.
     *
     * That last one is not the same check as [load]'s. This flow deliberately keeps serving a blob
     * long past the TTL - it is what the profile screen and the library's points line open on
     * before any refresh lands - so the freshness gate that protects [load] does nothing for it. A
     * blob written for the previous account would simply stay on screen, points, rank and all,
     * until a refresh happened to replace it. Emitting null instead leaves those readers with the
     * empty state they already draw for a profile that has never synced.
     *
     * Combined with the credentials rather than mapped over the DataStore alone. The ownership test
     * reads the account, and the account does not live in this DataStore - it lives in
     * EncryptedSharedPreferences - so a `map` over `dataStore.data` never re-ran when it changed:
     * clearing or switching the account produced no upstream emission at all, and every collector
     * that outlived the switch (the profile screen's mirror, the library's points line) kept the
     * departed account's blob on screen until some unrelated `refresh()` happened to write. The
     * credentials flow is the missing trigger; the value is still read through
     * [RetroAchievementsCredentialsRepository.getCredentials], which is authoritative from the
     * first collection rather than only once the repository's own background load has landed.
     */
    val profile: Flow<RaProfileDisplay?> =
        combine(dataStore.data, credentialsRepository.credentials) { prefs, _ -> prefs }
            .map { prefs ->
                val json = prefs[PROFILE_JSON_KEY] ?: return@map null
                val syncTime = prefs[PROFILE_SYNC_TIME_KEY] ?: 0L
                if (!belongsTo(prefs[PROFILE_USER_KEY], credentialsRepository.getCredentials()?.username)) {
                    return@map null
                }
                runCatching { gson.fromJson(json, RaProfileDisplay::class.java)?.copy(lastSyncedAt = syncTime) }.getOrNull()
            }
            // The trigger fires once more than the answer changes: the credentials flow starts null
            // and publishes again the moment its background load lands, which is the same profile
            // twice for every collector on the screen at cold start.
            .distinctUntilChanged()
            .flowOn(Dispatchers.IO)

    /** Fetch profile from API and cache; returns updated profile or null on error. */
    suspend fun refresh(): RaProfileDisplay? = withContext(Dispatchers.IO) {
        val creds = credentialsRepository.getCredentials() ?: return@withContext null
        runCatching {
            val response = apiClient.api.getUserSummary(
                username = creds.username,
                apiKey = creds.apiKey,
                recentGamesCount = 10,
                recentAchievementsCount = 10
            )
            val display = mapSummaryToDisplay(response)
            dataStore.edit { prefs ->
                prefs[PROFILE_JSON_KEY] = gson.toJson(display)
                prefs[PROFILE_SYNC_TIME_KEY] = display.lastSyncedAt
                // The account this blob is of, taken from the credentials it was fetched with
                // rather than from the response, so the comparison on the way back out is against
                // the same thing either way.
                prefs[PROFILE_USER_KEY] = creds.username
            }
            display
        }.getOrNull()
    }

    /** Load profile: use the cache if it is fresh and belongs to the signed-in account, else fetch. */
    suspend fun load(forceRefresh: Boolean = false): RaProfileDisplay? = withContext(Dispatchers.IO) {
        if (!forceRefresh) {
            val prefs = dataStore.data.first()
            val syncTime = prefs[PROFILE_SYNC_TIME_KEY] ?: 0L
            val storedUser = prefs[PROFILE_USER_KEY]
            val currentUser = credentialsRepository.getCredentials()?.username
            if (cacheUsable(storedUser, currentUser, syncTime, System.currentTimeMillis())) {
                val json = prefs[PROFILE_JSON_KEY] ?: return@withContext null
                return@withContext gson.fromJson(json, RaProfileDisplay::class.java)?.copy(lastSyncedAt = syncTime)
            }
        }
        refresh()
    }

    private fun mapSummaryToDisplay(response: RaUserSummaryResponse): RaProfileDisplay {
        val now = System.currentTimeMillis()
        val recentAchievements = mutableListOf<RaRecentAchievementDisplay>()
        response.recentAchievements?.forEach { (gameIdStr, entries) ->
            val gameId = gameIdStr.toIntOrNull() ?: 0
            entries.forEach { e ->
                recentAchievements.add(
                    RaRecentAchievementDisplay(
                        gameId = gameId,
                        gameTitle = null,
                        title = e.title ?: "",
                        points = e.points ?: 0,
                        badgeName = e.badgeName,
                        date = e.date,
                        hardcoreAchieved = (e.hardcoreAchieved ?: 0) != 0
                    )
                )
            }
        }
        val recentlyPlayed = (response.recentlyPlayed ?: emptyList()).map { g ->
            RaRecentlyPlayedDisplay(
                gameId = g.gameId,
                title = g.title,
                consoleName = g.consoleName,
                numAchieved = g.numAchieved,
                numPossible = g.numPossibleAchievements,
                scoreAchieved = g.scoreAchieved,
                possibleScore = g.possibleScore,
                lastPlayed = g.lastPlayed,
                imageIcon = g.imageIcon
            )
        }
        return RaProfileDisplay(
            username = response.user ?: "",
            totalPoints = response.totalPoints ?: 0,
            totalSoftcorePoints = response.totalSoftcorePoints ?: 0,
            rank = response.rank,
            memberSince = response.memberSince,
            recentAchievements = recentAchievements.take(10),
            recentlyPlayed = recentlyPlayed,
            lastSyncedAt = now,
            lastGameId = response.lastGameId,
            richPresenceMsg = response.richPresenceMsg?.trim()?.takeIf { it.isNotBlank() }
        )
    }
}
