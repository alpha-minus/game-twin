package com.twinlore.app.data.steam

import com.twinlore.app.data.local.SteamGameCacheDao
import com.twinlore.app.data.local.SteamGameCacheEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

class SteamProfilePrivateException : Exception("Steam profile game details are set to private")
class SteamGameNoAchievementsException : Exception("This game has no achievements on Steam")
class SteamGameNotOwnedException : Exception("This game is not in your Steam library")

private data class SteamErrorPlayerStats(val error: String? = null)
private data class SteamErrorBody(@com.google.gson.annotations.SerializedName("playerstats") val playerStats: SteamErrorPlayerStats? = null)

@Singleton
class SteamGameCacheRepository @Inject constructor(
    private val steamGameCacheDao: SteamGameCacheDao,
    private val apiClient: SteamApiClient,
    private val credentialsRepository: SteamCredentialsRepository
) {
    private val gson = com.google.gson.Gson()

    companion object {
        const val CACHE_TTL_MS = 15 * 60 * 1000L
    }

    suspend fun getGameProgress(
        vaultId: String,
        steamAppId: Int,
        forceRefresh: Boolean = false,
        /** When [forceRefresh], still serve cache if it was refreshed within this window
         *  (e.g. reopening a tab) — avoids back-to-back network refreshes. */
        minRefreshIntervalMs: Long? = null
    ): SteamGameProgress? = withContext(Dispatchers.IO) {
        val creds = credentialsRepository.getCredentials() ?: return@withContext null
        if (!forceRefresh) {
            val cached = steamGameCacheDao.getByVaultId(vaultId)
            if (cached != null && cached.steamAppId == steamAppId &&
                (System.currentTimeMillis() - cached.lastSyncedAt) < CACHE_TTL_MS
            ) {
                return@withContext parseCachedSnapshot(cached.jsonSnapshot, cached.steamAppId, cached.lastSyncedAt)
            }
        } else if (minRefreshIntervalMs != null) {
            val cached = steamGameCacheDao.getByVaultId(vaultId)
            if (cached != null && cached.steamAppId == steamAppId && !cached.jsonSnapshot.isNullOrEmpty() &&
                (System.currentTimeMillis() - cached.lastSyncedAt) < minRefreshIntervalMs
            ) {
                return@withContext parseCachedSnapshot(cached.jsonSnapshot, cached.steamAppId, cached.lastSyncedAt)
            }
        }
        runCatching {
            android.util.Log.d("SteamCache", "Fetching schema for appId=$steamAppId")
            // Optional: the player's own response carries every achievement's name
            // and description too, so a schema that does not arrive costs the icons
            // and the hidden flag rather than the whole list.
            val schema = runCatching { apiClient.api.getSchemaForGame(appId = steamAppId) }
                .onFailure { android.util.Log.w("SteamCache", "Schema unavailable for appId=$steamAppId", it) }
                .getOrNull()
            android.util.Log.d("SteamCache", "Schema OK, fetching player achievements for steamId=${creds.steamId}")
            val playerAchievements = apiClient.api.getPlayerAchievements(
                steamId = creds.steamId, appId = steamAppId
            )
            android.util.Log.d("SteamCache", "Player achievements OK, fetching global percentages")
            val globalPercentages = try {
                apiClient.api.getGlobalAchievementPercentages(appId = steamAppId)
            } catch (_: Exception) { null }
            android.util.Log.d("SteamCache", "Mapping progress")
            val now = System.currentTimeMillis()
            val progress = mapToProgress(schema, playerAchievements, steamAppId, now, globalPercentages)
            // The percentages ride along: without them every read that serves the
            // cache came back with no rarity, so "N% of players" only appeared
            // after a Refresh and was gone again on the next open.
            val snapshot = CachedSteamSnapshot(
                schema = schema,
                player = playerAchievements,
                global = globalPercentages,
            )
            steamGameCacheDao.insert(
                SteamGameCacheEntity(
                    vaultId = vaultId,
                    steamAppId = steamAppId,
                    jsonSnapshot = gson.toJson(snapshot),
                    lastSyncedAt = now,
                    numUnlocked = progress.numUnlocked,
                    numAchievements = progress.numAchievements,
                )
            )
            android.util.Log.d("SteamCache", "Cache saved, ${progress.numUnlocked}/${progress.numAchievements} unlocked")
            progress
        }.onFailure { e ->
            android.util.Log.e("SteamCache", "Failed to load achievements for appId=$steamAppId", e)
            if (e is retrofit2.HttpException) {
                val errorBody = runCatching { e.response()?.errorBody()?.string() }.getOrNull()
                android.util.Log.e("SteamCache", "HTTP ${e.code()} body: $errorBody")
                val steamError = runCatching {
                    gson.fromJson(errorBody, SteamErrorBody::class.java)?.playerStats?.error
                }.getOrNull()
                // Steam's own "not public" comes first: a private profile also hides its
                // library, so the ownership check below would call every game unowned.
                if (steamError?.contains("not public", ignoreCase = true) == true) {
                    throw SteamProfilePrivateException()
                }
                // Check if game is owned to give better error messages. A library that
                // comes back with no games at all is hidden, not empty: assume owned.
                val isOwned = runCatching {
                    val ownedResp = apiClient.api.getOwnedGames(
                        steamId = creds.steamId, includeAppInfo = 0
                    )
                    val games = ownedResp.response.games
                    games.isNullOrEmpty() || games.any { it.appId == steamAppId }
                }.getOrDefault(true) // Assume owned if check fails
                when {
                    !isOwned -> throw SteamGameNotOwnedException()
                    steamError?.contains("no stats", ignoreCase = true) == true ->
                        throw SteamGameNoAchievementsException()
                    e.code() == 400 -> throw SteamGameNoAchievementsException()
                    e.code() == 403 -> throw SteamProfilePrivateException()
                }
            }
        }.getOrNull() ?: run {
            val cached = steamGameCacheDao.getByVaultId(vaultId)
            if (cached != null && cached.steamAppId == steamAppId) {
                parseCachedSnapshot(cached.jsonSnapshot, cached.steamAppId, cached.lastSyncedAt)
            } else null
        }
    }

    /** Cache-only read (no network); null if absent. Carries lastSyncedAt. */
    suspend fun getCachedProgress(vaultId: String): SteamGameProgress? = withContext(Dispatchers.IO) {
        val cached = steamGameCacheDao.getByVaultId(vaultId) ?: return@withContext null
        parseCachedSnapshot(cached.jsonSnapshot, cached.steamAppId, cached.lastSyncedAt)
    }

    suspend fun clearCacheForVault(vaultId: String) = withContext(Dispatchers.IO) {
        steamGameCacheDao.deleteByVaultId(vaultId)
    }

    /** Drop every cached Steam row (e.g. on account switch). */
    suspend fun clearAllCache() = withContext(Dispatchers.IO) {
        steamGameCacheDao.clearAll()
    }

    /** Lightweight, reactive count projection for the library hot path. */
    fun countsFlow(): kotlinx.coroutines.flow.Flow<List<com.twinlore.app.data.local.SteamCountsRow>> =
        steamGameCacheDao.countsFlow()

    private data class CachedSteamSnapshot(
        /** Null when the schema call failed; the player's own names carry the list. */
        val schema: SteamSchemaResponse?,
        val player: SteamPlayerAchievementsResponse,
        /** Null in snapshots written before this was cached, and after a fetch that had none. */
        val global: SteamGlobalAchievementPercentagesResponse? = null,
    )

    private fun parseCachedSnapshot(json: String, steamAppId: Int, lastSyncedAt: Long): SteamGameProgress? {
        return runCatching {
            val snapshot = gson.fromJson(json, CachedSteamSnapshot::class.java)
            mapToProgress(snapshot.schema, snapshot.player, steamAppId, lastSyncedAt, snapshot.global)
        }.getOrNull()
    }

    private fun mapToProgress(
        schema: SteamSchemaResponse?,
        player: SteamPlayerAchievementsResponse,
        steamAppId: Int,
        lastSyncedAt: Long,
        globalPercentages: SteamGlobalAchievementPercentagesResponse? = null
    ): SteamGameProgress {
        val schemaAchievements = schema?.game?.availableGameStats?.achievements ?: emptyList()
        val playerAchievements = player.playerStats?.achievements ?: emptyList()
        val playerMap = playerAchievements.associateBy { it.apiname }
        val globalMap = globalPercentages?.achievementPercentages?.achievements
            ?.associateBy { it.name } ?: emptyMap()

        val list = if (schemaAchievements.isNotEmpty()) {
            schemaAchievements.map { sa ->
                val pa = playerMap[sa.name]
                SteamAchievementDisplay(
                    apiName = sa.name,
                    title = sa.displayName?.takeIf { it.isNotBlank() }
                        ?: pa?.name?.takeIf { it.isNotBlank() }
                        ?: sa.name,
                    description = if (sa.hidden == 1 && pa?.achieved != 1) null
                    else sa.description?.takeIf { it.isNotBlank() } ?: pa?.description,
                    iconUrl = sa.icon,
                    iconGrayUrl = sa.iconGray,
                    achieved = pa?.achieved == 1,
                    unlockTime = pa?.unlocktime?.takeIf { it > 0 },
                    hidden = sa.hidden == 1,
                    globalPercent = globalMap[sa.name]?.percent
                )
            }
        } else {
            // No schema: the list the player's own response describes. No icons and
            // no hidden flag to honour, so nothing is withheld either.
            playerAchievements.map { pa ->
                SteamAchievementDisplay(
                    apiName = pa.apiname,
                    title = pa.name?.takeIf { it.isNotBlank() } ?: pa.apiname,
                    description = pa.description,
                    iconUrl = null,
                    iconGrayUrl = null,
                    achieved = pa.achieved == 1,
                    unlockTime = pa.unlocktime.takeIf { it > 0 },
                    hidden = false,
                    globalPercent = globalMap[pa.apiname]?.percent
                )
            }
        }

        val unlocked = list.filter { it.achieved }
        val recentUnlocks = unlocked.sortedByDescending { it.unlockTime ?: 0 }.take(10)

        return SteamGameProgress(
            steamAppId = steamAppId,
            gameTitle = player.playerStats?.gameName ?: schema?.game?.gameName ?: "",
            numAchievements = list.size,
            numUnlocked = unlocked.size,
            achievements = list,
            recentUnlocks = recentUnlocks,
            lastSyncedAt = lastSyncedAt,
            headerImageUrl = "https://cdn.akamai.steamstatic.com/steam/apps/$steamAppId/header.jpg"
        )
    }
}
