package com.twinlore.app.data.retroachievements

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import androidx.annotation.VisibleForTesting
import com.twinlore.app.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Achievement comment threads, cached on disk per achievement.
 *
 * RetroAchievements has no batch endpoint for comments, so prefetching a whole
 * game costs one request per achievement. On the bundled key, which most users
 * share, that is not ours to spend: a thread is stored the first time someone
 * opens it, reopening it within [FRESH_MS] costs nothing, and after that a
 * failed refresh (offline, rate-limited) still serves the stored copy.
 *
 * Someone who brought their own key is spending only their own allowance, so
 * for them [prefetchInBackground] fills the whole game's threads, one request
 * every [PREFETCH_GAP_MS], and stops at the first failure.
 *
 * Cache location: filesDir/ra_comments/{achievementId}.json, capped at
 * [MAX_ENTRIES] files, oldest written first out.
 */
@Singleton
class RaCommentsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val apiClient: RetroAchievementsApiClient,
    private val credentialsRepository: RetroAchievementsCredentialsRepository,
    @ApplicationScope private val appScope: CoroutineScope,
) {
    companion object {
        private const val TAG = "RaCommentsRepository"
        private const val DIR_NAME = "ra_comments"
        const val FRESH_MS = 12 * 60 * 60 * 1000L
        const val MAX_ENTRIES = 500

        /**
         * Between two prefetch requests. RetroAchievements publishes no limit;
         * this keeps a whole set well under anything a web client would do.
         */
        const val PREFETCH_GAP_MS = 1_500L
    }

    /** One prefetch at a time: a second game's list arriving mid-run waits for the next refresh. */
    private val prefetching = Mutex()

    private val gson = Gson()
    private val dir: File get() = File(context.filesDir, DIR_NAME)

    /**
     * The thread for [achievementId], newest first. Served from disk when it was
     * stored within [FRESH_MS] unless [forceRefresh]; otherwise fetched and stored.
     * A failed fetch falls back to any stored copy and rethrows only when there is none.
     */
    suspend fun getComments(achievementId: Int, forceRefresh: Boolean = false): List<RaCommentDto> =
        withContext(Dispatchers.IO) {
            val file = File(dir, "$achievementId.json")
            if (!forceRefresh && isFresh(file)) {
                read(file)?.let { return@withContext it }
            }
            try {
                fetchAndStore(achievementId)
            } catch (e: Exception) {
                read(file) ?: throw e
            }
        }

    private suspend fun fetchAndStore(achievementId: Int): List<RaCommentDto> = withContext(Dispatchers.IO) {
        val apiKey = credentialsRepository.getCredentials()?.apiKey
            ?: RetroAchievementsCredentialsRepository.BUNDLED_API_KEY
        val comments = apiClient.api.getAchievementComments(apiKey = apiKey, achievementId = achievementId)
            .results.orEmpty()
        write(File(dir, "$achievementId.json"), comments)
        comments
    }

    /**
     * Fills the cache for [achievementIds] off the caller's back, own key only.
     * Called when a game's progress has just been fetched.
     */
    fun prefetchInBackground(achievementIds: List<Int>) {
        if (achievementIds.isEmpty() || !credentialsRepository.hasCustomApiKey()) return
        appScope.launch { prefetch(achievementIds) }
    }

    /**
     * Fetches each thread that is not already fresh on disk, spaced by
     * [PREFETCH_GAP_MS]. A failure ends the run rather than retrying through
     * the rest: offline or rate-limited, the next request would fail too.
     * Returns how many threads were fetched.
     */
    @VisibleForTesting
    internal suspend fun prefetch(achievementIds: List<Int>): Int {
        if (!credentialsRepository.hasCustomApiKey()) return 0
        if (!prefetching.tryLock()) return 0
        var fetched = 0
        try {
            for (id in achievementIds.distinct()) {
                if (isFresh(File(dir, "$id.json"))) continue
                if (fetched > 0) delay(PREFETCH_GAP_MS)
                try {
                    fetchAndStore(id)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.i(TAG, "Comment prefetch stopped at $id after $fetched", e)
                    break
                }
                fetched++
            }
        } finally {
            prefetching.unlock()
        }
        return fetched
    }

    private fun isFresh(file: File): Boolean =
        file.exists() && System.currentTimeMillis() - file.lastModified() < FRESH_MS

    private fun read(file: File): List<RaCommentDto>? =
        if (!file.exists()) null
        else runCatching { gson.fromJson(file.readText(), Array<RaCommentDto>::class.java)?.toList() }
            .onFailure { Log.w(TAG, "Unreadable cached comments ${file.name}", it) }
            .getOrNull()

    private fun write(file: File, comments: List<RaCommentDto>) {
        runCatching {
            dir.mkdirs()
            val tmp = File(dir, "${file.name}.tmp")
            tmp.writeText(gson.toJson(comments))
            if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
            trim()
        }.onFailure { Log.w(TAG, "Could not cache comments ${file.name}", it) }
    }

    private fun trim() {
        val files = dir.listFiles { f -> f.name.endsWith(".json") } ?: return
        if (files.size <= MAX_ENTRIES) return
        files.sortedBy { it.lastModified() }.take(files.size - MAX_ENTRIES).forEach { it.delete() }
    }
}
