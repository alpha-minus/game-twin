package com.twinlore.app.data

import android.util.Log
import androidx.room.withTransaction
import com.twinlore.app.data.local.RaGameCacheDao
import com.twinlore.app.data.local.VaultDatabase
import com.twinlore.app.data.retroachievements.RetroAchievementsApiClient
import com.twinlore.app.data.retroachievements.RetroAchievementsCredentialsRepository
import com.twinlore.app.data.steam.SteamApiClient
import com.twinlore.app.data.steam.SteamCredentialsRepository
import com.twinlore.app.data.steam.SteamGameCacheRepository
import com.twinlore.app.util.PerfLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cold-start orchestrator for proactive achievement-progress sync.
 *
 * - RetroAchievements: one paginated API_GetUserCompletionProgress call,
 *   read-modify-write into ra_game_cache count columns for every vault
 *   that has a raGameId set.
 * - Steam: top-5 recently-played games, sequential with 300ms pacing,
 *   each delegated to SteamGameCacheRepository.getGameProgress
 *   (forceRefresh=true) which already writes the count columns.
 *
 * Gated by a 6h DataStore TTL (SettingsRepository.lastAchievementSyncAt).
 * Mutex prevents accidental double-fire. Library bars update reactively
 * via the DAO countsFlow()s wired in LibraryViewModel.achievementSortInfo.
 */
@Singleton
open class AchievementSyncCoordinator @Inject constructor(
    private val raCredentialsRepository: RetroAchievementsCredentialsRepository,
    private val raApiClient: RetroAchievementsApiClient,
    private val raGameCacheDao: RaGameCacheDao,
    private val steamCredentialsRepository: SteamCredentialsRepository,
    private val steamApiClient: SteamApiClient,
    private val steamGameCacheRepository: SteamGameCacheRepository,
    private val vaultRepository: VaultRepository,
    private val settingsRepository: SettingsRepository,
    private val appDatabase: VaultDatabase,
) {
    companion object {
        private const val TAG = "AchievementSync"
        const val TTL_MS = 6L * 60 * 60 * 1000L
        const val STEAM_RECENT_LIMIT = 5
        const val STEAM_INTER_GAME_DELAY_MS = 300L
        const val RA_BULK_PAGE_SIZE = 500
        const val RA_BULK_MAX_OFFSET = 5000
    }

    private val mutex = Mutex()
    // App-lifetime scope so account-change resyncs (which can sweep a whole library,
    // paced) survive the Settings screen closing instead of cancelling midway.
    private val coordinatorScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Cold-start sync. TTL-gated. No-op if not stale. */
    suspend fun syncAtStartup() = mutex.withLock {
        val now = System.currentTimeMillis()
        val lastSync = settingsRepository.lastAchievementSyncAt.first()
        if (now - lastSync < TTL_MS) {
            Log.d(TAG, "TTL not expired (last=$lastSync, now=$now); skipping")
            return@withLock
        }
        PerfLog.mark("AchievementSyncCoordinator begin")

        var raOk = false
        var steamOk = false
        coroutineScope {
            val raJob = launch {
                runCatching { syncRaBulk() }
                    .onSuccess { raOk = true }
                    .onFailure { Log.w(TAG, "RA bulk failed", it) }
            }
            val steamJob = launch {
                runCatching { syncSteamRecent() }
                    .onSuccess { steamOk = true }
                    .onFailure { Log.w(TAG, "Steam recent failed", it) }
            }
            raJob.join(); steamJob.join()
        }

        val noCreds = raCredentialsRepository.getCredentials() == null
                   && steamCredentialsRepository.getCredentials() == null
        if (raOk || steamOk || noCreds) {
            settingsRepository.setLastAchievementSyncAt(System.currentTimeMillis())
        }
        PerfLog.mark("AchievementSyncCoordinator end (raOk=$raOk steamOk=$steamOk noCreds=$noCreds)")
    }

    /**
     * Force an RA-only bulk re-sync (e.g. after the RA username changes), bypassing the
     * 6h TTL. Reuses [syncRaBulk] — one paginated API_GetUserCompletionProgress that
     * rewrites every linked vault's count columns; library bars update reactively via
     * countsFlow(). Deliberately does NOT touch the shared TTL timestamp, so a pending
     * Steam cold-start sync isn't suppressed.
     */
    /**
     * RA account switch (fire-and-forget, app-scoped): one bulk
     * API_GetUserCompletionProgress that clears stale rows then repopulates, so the
     * library reflects the new account instead of the old one's progress.
     */
    fun resyncRaNow() {
        coordinatorScope.launch {
            mutex.withLock {
                runCatching { syncRaBulk(clearStaleFirst = true) }
                    .onFailure { Log.w(TAG, "RA resync (username change) failed", it) }
            }
        }
    }

    /**
     * Steam account switch (fire-and-forget, app-scoped): clear the stale cache, then
     * refresh EVERY linked Steam game for the new account, paced at
     * [STEAM_INTER_GAME_DELAY_MS] between games so we don't hammer the API (Steam has
     * no bulk achievement endpoint — each game is a few calls). Runs app-scoped so a
     * large library completes in the background even if Settings is closed.
     */
    fun resyncSteamNow() {
        coordinatorScope.launch {
            mutex.withLock {
                runCatching {
                    steamGameCacheRepository.clearAllCache()
                    syncSteamAllLinked()
                }.onFailure { Log.w(TAG, "Steam resync (account change) failed", it) }
            }
        }
    }

    /** Refresh progress for every Steam-linked vault, paced between games. */
    private suspend fun syncSteamAllLinked() = withContext(Dispatchers.IO) {
        steamCredentialsRepository.getCredentials() ?: return@withContext
        val linked = vaultRepository.getAllVaults()
            .mapNotNull { v -> v.steamAppId?.toIntOrNull()?.let { v.id to it } }
        if (linked.isEmpty()) return@withContext
        linked.forEachIndexed { i, (vaultId, appId) ->
            if (i > 0) kotlinx.coroutines.delay(STEAM_INTER_GAME_DELAY_MS)
            runCatching {
                steamGameCacheRepository.getGameProgress(vaultId, appId, forceRefresh = true)
            }.onFailure { Log.w(TAG, "Steam sync vaultId=$vaultId appId=$appId failed", it) }
        }
    }

    private suspend fun syncRaBulk(clearStaleFirst: Boolean = false) = withContext(Dispatchers.IO) {
        val creds = raCredentialsRepository.getCredentials() ?: return@withContext

        val linkedVaults: Map<Int, String> = vaultRepository.getAllVaults()
            .mapNotNull { v -> v.raGameId?.toIntOrNull()?.let { it to v.id } }
            .toMap()
        if (linkedVaults.isEmpty()) return@withContext
        PerfLog.mark("ra-bulk: ${linkedVaults.size} linked vaults")

        val bulk = buildList {
            var offset = 0
            while (true) {
                val page = raApiClient.api.getUserCompletionProgress(
                    username = creds.username,
                    apiKey = creds.apiKey,
                    count = RA_BULK_PAGE_SIZE,
                    offset = offset,
                )
                val results = page.results.orEmpty()
                addAll(results)
                if (results.size < RA_BULK_PAGE_SIZE) break
                offset += RA_BULK_PAGE_SIZE
                if (offset >= RA_BULK_MAX_OFFSET) break
            }
        }
        PerfLog.mark("ra-bulk: ${bulk.size} games in API response")

        runInDbTransaction {
            // Account switch: wipe stale rows first (after the fetch above succeeded, so a
            // network failure never clears the cache). Repopulated below from the new user.
            if (clearStaleFirst) raGameCacheDao.clearAll()
            for (entry in bulk) {
                val gameId = entry.gameId ?: continue
                val vaultId = linkedVaults[gameId] ?: continue
                val existing = raGameCacheDao.getByVaultId(vaultId)
                val merged = (existing ?: com.twinlore.app.data.local.RaGameCacheEntity(
                    vaultId = vaultId,
                    raGameId = gameId,
                    jsonSnapshot = null,
                    lastSyncedAt = 0L,
                )).copy(
                    numAwardedSoft = entry.numAwarded ?: 0,
                    numAwardedHardcore = entry.numAwardedHardcore ?: 0,
                    numAchievements = entry.maxPossible ?: 0,
                    highestAwardKind = entry.highestAwardKind,
                )
                raGameCacheDao.insert(merged)
            }
        }
        PerfLog.mark("ra-bulk: writes complete")
    }

    /**
     * Test seam: wraps a block in a Room transaction. Real implementation delegates to
     * [VaultDatabase.withTransaction]; the unit-test subclass overrides this to skip
     * the unmockable Room extension function.
     */
    protected open suspend fun runInDbTransaction(block: suspend () -> Unit) {
        appDatabase.withTransaction { block() }
    }

    private suspend fun syncSteamRecent() = withContext(Dispatchers.IO) {
        val creds = steamCredentialsRepository.getCredentials() ?: return@withContext

        val recent = steamApiClient.api.getRecentlyPlayedGames(creds.steamId)
        val candidates = recent.response.games.orEmpty().take(STEAM_RECENT_LIMIT)
        if (candidates.isEmpty()) return@withContext

        val linked: Map<Int, String> = vaultRepository.getAllVaults()
            .mapNotNull { v -> v.steamAppId?.toIntOrNull()?.let { it to v.id } }
            .toMap()

        val toSync = candidates.mapNotNull { g ->
            linked[g.appId]?.let { vaultId -> vaultId to g.appId }
        }

        toSync.forEachIndexed { i, (vaultId, appId) ->
            if (i > 0) kotlinx.coroutines.delay(STEAM_INTER_GAME_DELAY_MS)
            runCatching {
                steamGameCacheRepository.getGameProgress(
                    vaultId = vaultId,
                    steamAppId = appId,
                    forceRefresh = true,
                )
            }.onFailure { Log.w(TAG, "Steam sync vaultId=$vaultId appId=$appId failed", it) }
        }
    }
}
