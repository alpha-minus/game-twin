package com.twinlore.app.data.retroachievements

import com.twinlore.app.data.local.RaGameCacheDao
import com.twinlore.app.data.local.RaGameCacheEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** RetroAchievements has no game with this ID: it answers such a request with an empty `[]`. */
class RaGameNotFoundException(val gameId: Int) : Exception("No RetroAchievements game with ID $gameId")

/** RetroAchievements has no user with this name: an existing game's progress for them comes back as `[]`. */
class RaUserNotFoundException(val username: String) : Exception("No RetroAchievements user named $username")

@Singleton
class RaGameCacheRepository @Inject constructor(
    private val raGameCacheDao: RaGameCacheDao,
    private val apiClient: RetroAchievementsApiClient,
    private val credentialsRepository: RetroAchievementsCredentialsRepository,
    /** Handed each freshly fetched set, to fill its comment threads on an own key. */
    private val commentsRepository: RaCommentsRepository? = null,
) {
    private val gson = com.google.gson.Gson()
    companion object {
        const val CACHE_TTL_MS = 15 * 60 * 1000L
    }

    /**
     * Load game progress for a vault. Uses cache if fresh (within TTL), else fetches from API.
     * On API/network failure, falls back to stale cache for this vault/game when available (offline).
     * ForceRefresh overrides TTL.
     *
     * Throws for failures a cache cannot paper over, so the caller can name them:
     * [RaGameNotFoundException], [RaUserNotFoundException], and a rejected key (HTTP 401/403).
     * Any other failure with nothing cached rethrows the original error rather than returning
     * null, so "no connection" and "too many requests" reach the user as themselves.
     * Null only when no account is connected.
     */
    suspend fun getGameProgress(
        vaultId: String,
        raGameId: Int,
        forceRefresh: Boolean = false,
        /** When [forceRefresh], still serve cache if it was refreshed within this window
         *  (e.g. reopening a tab) — avoids back-to-back network refreshes. */
        minRefreshIntervalMs: Long? = null
    ): RaGameProgress? = withContext(Dispatchers.IO) {
        val creds = credentialsRepository.getCredentials() ?: return@withContext null
        if (!forceRefresh) {
            val cached = raGameCacheDao.getByVaultId(vaultId)
            if (cached != null && cached.raGameId == raGameId && (System.currentTimeMillis() - cached.lastSyncedAt) < CACHE_TTL_MS) {
                val progress = parseCachedSnapshot(cached.jsonSnapshot, cached.raGameId, cached.lastSyncedAt, cached.highestAwardKind, cached.userRank)
                return@withContext progress
            }
        } else if (minRefreshIntervalMs != null) {
            // Forced refresh, but a full snapshot was fetched very recently — reuse it.
            val cached = raGameCacheDao.getByVaultId(vaultId)
            if (cached != null && cached.raGameId == raGameId && !cached.jsonSnapshot.isNullOrEmpty() &&
                (System.currentTimeMillis() - cached.lastSyncedAt) < minRefreshIntervalMs) {
                return@withContext parseCachedSnapshot(cached.jsonSnapshot, cached.raGameId, cached.lastSyncedAt, cached.highestAwardKind, cached.userRank)
            }
        }
        try {
            val response = apiClient.api.getGameInfoAndUserProgress(
                username = creds.username,
                apiKey = creds.apiKey,
                gameId = raGameId
            )
            val progress = mapResponseToProgress(response, raGameId, System.currentTimeMillis())
            val rankEntry = runCatching {
                apiClient.api.getUserGameRankAndScore(creds.username, creds.apiKey, raGameId)
            }.getOrNull()?.firstOrNull()
            var progressWithRank = rankEntry?.userRank?.let { rank ->
                progress.copy(userRank = rank)
            } ?: progress
            val completionEntry = runCatching {
                apiClient.api.getUserCompletionProgress(creds.username, creds.apiKey, 500, 0)
            }.getOrNull()?.results?.firstOrNull { it.gameId == raGameId }
            val highestAwardKind = completionEntry?.highestAwardKind
            progressWithRank = progressWithRank.copy(highestAwardKind = highestAwardKind)
            raGameCacheDao.insert(
                RaGameCacheEntity(
                    vaultId = vaultId,
                    raGameId = raGameId,
                    jsonSnapshot = gson.toJson(response),
                    lastSyncedAt = progressWithRank.lastSyncedAt,
                    highestAwardKind = highestAwardKind,
                    userRank = progressWithRank.userRank,
                    numAwardedSoft = progressWithRank.userNumAchieved,
                    numAwardedHardcore = progressWithRank.userNumHardcore,
                    numAchievements = progressWithRank.numAchievements,
                )
            )
            commentsRepository?.prefetchInBackground(progressWithRank.achievements.map { it.id })
            progressWithRank
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            definitiveFailure(e, raGameId, creds)?.let { throw it }
            // On network/API failure, use stale cache for this vault/game if available (offline fallback)
            val cached = raGameCacheDao.getByVaultId(vaultId)
            val stale = if (cached != null && cached.raGameId == raGameId) {
                parseCachedSnapshot(cached.jsonSnapshot, cached.raGameId, cached.lastSyncedAt, cached.highestAwardKind, cached.userRank)
            } else null
            stale ?: throw e
        }
    }

    /** The game's summary, for validating a manually entered ID. Throws [RaGameNotFoundException] when there is none. */
    suspend fun requireGame(gameId: Int): RaGameSummaryResponse = withContext(Dispatchers.IO) {
        val apiKey = credentialsRepository.getCredentials()?.apiKey
            ?: RetroAchievementsCredentialsRepository.BUNDLED_API_KEY
        try {
            apiClient.api.getGame(apiKey = apiKey, gameId = gameId)
        } catch (e: Exception) {
            if (isEmptyAnswer(e)) throw RaGameNotFoundException(gameId)
            throw e
        }
    }

    /**
     * The failure to report instead of falling back to the cache, or null for a transient one.
     *
     * An empty answer is ambiguous (unknown game and unknown user look the same), so the game
     * alone is asked for once to tell them apart. If that probe fails for another reason, the
     * failure stays transient.
     */
    private suspend fun definitiveFailure(e: Exception, raGameId: Int, creds: RaCredentials): Exception? {
        if (e is retrofit2.HttpException && e.code() in listOf(401, 403)) return e
        if (!isEmptyAnswer(e)) return null
        return try {
            apiClient.api.getGame(apiKey = creds.apiKey, gameId = raGameId)
            RaUserNotFoundException(creds.username)
        } catch (probe: kotlinx.coroutines.CancellationException) {
            throw probe
        } catch (probe: Exception) {
            if (isEmptyAnswer(probe)) RaGameNotFoundException(raGameId) else null
        }
    }

    /** RetroAchievements' "nothing here" is `[]` where an object was expected, which Gson rejects. */
    private fun isEmptyAnswer(e: Throwable): Boolean =
        e is com.google.gson.JsonSyntaxException ||
            (e is IllegalStateException && e.message?.contains("BEGIN_ARRAY") == true)

    /** Load from cache only (for offline). */
    suspend fun getCachedProgress(vaultId: String): RaGameProgress? = withContext(Dispatchers.IO) {
        val cached = raGameCacheDao.getByVaultId(vaultId) ?: return@withContext null
        parseCachedSnapshot(cached.jsonSnapshot, cached.raGameId, cached.lastSyncedAt, cached.highestAwardKind, cached.userRank)
    }

    /** Lightweight, reactive count projection for the library hot path. */
    fun countsFlow(): kotlinx.coroutines.flow.Flow<List<com.twinlore.app.data.local.RaCountsRow>> =
        raGameCacheDao.countsFlow()

    /** Clear cached progress for a vault (e.g. when user unlinks the game). */
    suspend fun clearCacheForVault(vaultId: String) = withContext(Dispatchers.IO) {
        raGameCacheDao.deleteByVaultId(vaultId)
    }

    private fun parseCachedSnapshot(json: String?, raGameId: Int, lastSyncedAt: Long, highestAwardKind: String? = null, userRank: Int? = null): RaGameProgress? {
        if (json.isNullOrEmpty()) return null
        return runCatching {
            val response = gson.fromJson(json, RaGameInfoAndUserProgressResponse::class.java)
            mapResponseToProgress(response, raGameId, lastSyncedAt).copy(highestAwardKind = highestAwardKind, userRank = userRank)
        }.getOrNull()
    }

    private fun mapResponseToProgress(response: RaGameInfoAndUserProgressResponse, raGameId: Int, lastSyncedAt: Long): RaGameProgress {
        val achievementsMap = response.achievements ?: emptyMap()
        val userAchievements = response.userProgress?.achievements ?: emptyMap()
        val list = mutableListOf<RaAchievementDisplay>()
        val totalPlayers = (response.numDistinctPlayersCasual ?: response.numDistinctPlayersHardcore ?: 0).coerceAtLeast(1)
        for ((idStr, dto) in achievementsMap) {
            val id = dto.id ?: idStr.toIntOrNull() ?: continue
            val user = userAchievements[idStr]
            // Prefer inline user progress (DateEarned/DateEarnedHardcore) from API; fallback to legacy UserProgress.Achievements
            val dateAwarded = dto.dateEarned ?: user?.dateAwarded
            val hardcoreAchieved = (dto.dateEarnedHardcore != null) || ((user?.hardcoreAchieved ?: 0) != 0)
            val numAwarded = dto.numAwarded ?: 0
            val numAwardedHardcore = dto.numAwardedHardcore ?: 0
            val raritySoftcore = if (totalPlayers > 0) 100.0 * numAwarded / totalPlayers else null
            val rarityHardcore = if (totalPlayers > 0) 100.0 * numAwardedHardcore / totalPlayers else null
            list.add(
                RaAchievementDisplay(
                    id = id,
                    title = dto.title ?: "",
                    description = dto.description ?: "",
                    points = dto.points ?: 0,
                    trueRatio = dto.trueRatio ?: dto.points ?: 0,
                    badgeName = dto.badgeName ?: "",
                    rarityPercent = raritySoftcore,
                    rarityHardcorePercent = rarityHardcore,
                    dateAwarded = dateAwarded,
                    hardcoreAchieved = hardcoreAchieved,
                    displayOrder = dto.displayOrder ?: list.size,
                    type = dto.type ?: dto.typePascal,
                    numAwarded = numAwarded,
                    numAwardedHardcore = numAwardedHardcore,
                    author = dto.author
                )
            )
        }
        list.sortBy { it.displayOrder }
        val unlocked = list.filter { it.dateAwarded != null }
        val recentUnlocks = unlocked.sortedByDescending { it.dateAwarded }.take(10)
        val userProgress = response.userProgress
        val userNumAchieved = response.numAwardedToUser ?: userProgress?.numAchieved ?: unlocked.size
        val userPoints = userProgress?.softcorePoints ?: unlocked.sumOf { it.points }
        val userHardcorePoints = userProgress?.hardcorePoints ?: list.filter { it.hardcoreAchieved }.sumOf { it.points }
        val userNumHardcore = response.numAwardedToUserHardcore ?: unlocked.count { it.hardcoreAchieved }
        val totalPoints = (response.points?.takeIf { it > 0 } ?: list.sumOf { it.points }).coerceAtLeast(1)
        return RaGameProgress(
            raGameId = response.id ?: raGameId,
            gameTitle = response.title ?: "",
            totalPoints = totalPoints,
            numAchievements = list.size,
            userPoints = userPoints,
            userNumAchieved = userNumAchieved,
            userHardcorePoints = userHardcorePoints,
            userNumHardcore = userNumHardcore,
            recentUnlocks = recentUnlocks,
            achievements = list,
            lastSyncedAt = lastSyncedAt,
            consoleName = response.consoleName,
            gameImageIcon = response.imageIcon,
            userTotalPlaytimeMinutes = response.userTotalPlaytime?.let { totalSeconds ->
                // API returns UserTotalPlaytime in seconds; store as minutes for display (e.g. 1h 10m).
                totalSeconds / 60
            },
            numDistinctPlayers = response.numDistinctPlayersCasual ?: response.numDistinctPlayersHardcore,
            userCompletionPercent = response.userCompletion,
            userCompletionHardcorePercent = response.userCompletionHardcore,
            userRank = null
        )
    }
}
