package com.twinlore.app.data

import android.util.Log
import com.twinlore.app.data.local.BrowserTabDao
import com.twinlore.app.data.local.BrowserTabEntityMapper
import com.twinlore.app.data.local.BrowserTabHistorySerializer
import com.twinlore.app.domain.BrowserTab
import com.twinlore.app.domain.HistoryEntry
import com.twinlore.app.domain.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Per-vault open-browser-tab persistence. Distinct from SavedTabRepository,
 * which models offline bookmarks / guides.
 */
@Singleton
class BrowserTabRepository @Inject constructor(
    private val dao: BrowserTabDao
) : BaseRepository() {
    companion object {
        private const val TAG = "BrowserTabRepository"
        const val MAX_TABS_PER_VAULT = 50
        const val MAX_HISTORY_PER_TAB = 50
    }

    fun observeForVault(vaultId: String): Flow<List<BrowserTab>> =
        dao.observeForVault(vaultId).map(BrowserTabEntityMapper::toDomainList)

    suspend fun getActive(vaultId: String): BrowserTab? = withContext(Dispatchers.IO) {
        dao.getActive(vaultId)?.let(BrowserTabEntityMapper::toDomain)
    }

    /**
     * Creates a new tab for [vaultId].
     *
     * Foreground tabs receive lastAccessed = now(), becoming the head.
     * Background tabs receive lastAccessed = min(now, headLastAccessed) - 1,
     * sorting just below the current active tab without stealing focus.
     */
    suspend fun createTab(
        vaultId: String,
        url: String,
        title: String = "",
        background: Boolean = false
    ): Result<BrowserTab> = withContext(Dispatchers.IO) {
        try {
            val count = dao.getCountForVault(vaultId)
            if (count >= MAX_TABS_PER_VAULT) {
                return@withContext Result.failure(
                    IllegalStateException("Tab cap of $MAX_TABS_PER_VAULT reached")
                )
            }
            val now = System.currentTimeMillis()
            val lastAccessed = if (background) {
                val head = dao.getActive(vaultId)
                val ceiling = head?.lastAccessed ?: now
                minOf(now, ceiling) - 1
            } else {
                now
            }
            val nextOrder = (dao.getMaxOrder(vaultId) ?: -1) + 1
            val tab = BrowserTab(
                id = UUID.randomUUID().toString(),
                vaultId = vaultId,
                url = url,
                title = title,
                faviconBytes = null,
                scrollY = 0,
                tabOrder = nextOrder,
                lastAccessed = lastAccessed,
                createdAt = now,
                // Seed history so the 'always non-empty' invariant holds from row 1
                // (rather than relying on the mapper's defensive parse on first read).
                history = listOf(HistoryEntry(url, title)),
                historyIndex = 0
            )
            dao.insert(BrowserTabEntityMapper.toEntity(tab))
            Result.success(tab)
        } catch (e: Exception) {
            Log.e(TAG, "createTab failed", e)
            Result.failure(e)
        }
    }

    suspend fun closeTab(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        try { dao.deleteById(id); Result.success(Unit) }
        catch (e: Exception) { Result.failure(e) }
    }

    /**
     * Re-inserts a previously-closed tab, preserving its id and all fields.
     * Used by the undo affordance; the closed tab keeps its position in the
     * switcher's lastAccessed-DESC order naturally because its lastAccessed
     * is the original (older) value, not now().
     */
    suspend fun restoreTab(tab: BrowserTab): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            dao.insert(BrowserTabEntityMapper.toEntity(tab))
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun touch(id: String) = withContext(Dispatchers.IO) {
        dao.touchLastAccessed(id, System.currentTimeMillis())
    }

    /**
     * Update the URL and title at the tab's CURRENT history entry. Used by
     * onPageFinished for switch-reload / back-replay (where url is unchanged
     * from the current entry) and by onTitleReceived (where the URL is the
     * current entry and only the title needs refreshing).
     *
     * Rewrites both the denormalized cache columns AND the title at
     * history[historyIndex] so a subsequent back/forward replay restores
     * the freshest title, not the stale one captured at first navigation.
     *
     * If the url does NOT match the current entry's url (contract violation —
     * shouldn't happen in the new model), we still write the columns but
     * leave history_json untouched, since rewriting URL inside an entry
     * silently would be a worse bug than the title being stale.
     */
    suspend fun updateUrl(id: String, url: String, title: String) = withContext(Dispatchers.IO) {
        val row = dao.getById(id) ?: return@withContext
        val existing = BrowserTabHistorySerializer.fromJson(row.historyJson, row.url, row.title)
        val clampedIdx = row.historyIndex.coerceIn(0, existing.lastIndex)

        if (existing[clampedIdx].url == url) {
            // URL matches the current entry: refresh title in both places.
            val updated = existing.toMutableList()
            updated[clampedIdx] = HistoryEntry(url, title)
            dao.updateHistory(
                id = id,
                json = BrowserTabHistorySerializer.toJson(updated),
                idx = clampedIdx,
                url = url,
                title = title
            )
        } else {
            // URL drift (shouldn't happen). Update columns only; leave JSON alone.
            // Log so the inconsistency is diagnosable if a future code change
            // accidentally produces this path — the symptom would be back/forward
            // navigating to the old URL despite a fresh title being displayed.
            Log.w(TAG, "updateUrl: URL drift for tab $id: history='${existing[clampedIdx].url}', incoming='$url'")
            dao.updateUrl(id, url, title)
        }
    }

    suspend fun updateScrollY(id: String, y: Int) = withContext(Dispatchers.IO) {
        dao.updateScrollY(id, y)
    }

    suspend fun updateFavicon(id: String, bytes: ByteArray?) = withContext(Dispatchers.IO) {
        dao.updateFavicon(id, bytes)
    }

    /**
     * Returns the head tab if any exist. Otherwise creates a default landing
     * tab and returns it. Idempotent.
     */
    suspend fun ensureAtLeastOne(vaultId: String, defaultUrl: String): BrowserTab = withContext(Dispatchers.IO) {
        val current = dao.getForVaultSync(vaultId)
        if (current.isNotEmpty()) {
            return@withContext BrowserTabEntityMapper.toDomain(current.first())
        }
        when (val created = createTab(vaultId, defaultUrl)) {
            is Result.Success -> created.data
            else -> error("ensureAtLeastOne failed: createTab returned $created")
        }
    }

    /**
     * Record a genuine new navigation onto the tab's history. Truncates the
     * forward stack, collapses consecutive-duplicate URLs (e.g. reload), and
     * enforces the [MAX_HISTORY_PER_TAB] cap by dropping the oldest entry
     * when full (decrementing the index so it still points at the newest).
     *
     * No-op if the incoming URL equals the current entry URL (reload).
     */
    suspend fun recordNavigation(id: String, url: String, title: String) = withContext(Dispatchers.IO) {
        val row = dao.getById(id) ?: return@withContext
        val existing = BrowserTabHistorySerializer.fromJson(row.historyJson, row.url, row.title)
        val clampedIdx = row.historyIndex.coerceIn(0, existing.lastIndex)

        // Dedupe consecutive duplicates: treat as reload, no push.
        if (existing[clampedIdx].url == url) return@withContext

        // Truncate forward, append.
        val truncated = existing.take(clampedIdx + 1)
        var next = truncated + HistoryEntry(url, title)
        var nextIdx = next.lastIndex

        // Cap: drop oldest, decrement index so it stays on the new entry.
        if (next.size > MAX_HISTORY_PER_TAB) {
            next = next.drop(1)
            nextIdx -= 1
        }

        dao.updateHistory(
            id = id,
            json = BrowserTabHistorySerializer.toJson(next),
            idx = nextIdx,
            url = url,
            title = title
        )
    }

    /**
     * Move the tab's history pointer to [newIndex]. Used by back/forward
     * replay: the VM computes the target index, the repo writes index +
     * denormalized url/title in one UPDATE so they stay in sync.
     *
     * No-op if [newIndex] is outside the persisted history bounds.
     */
    suspend fun setHistoryIndex(id: String, newIndex: Int) = withContext(Dispatchers.IO) {
        val row = dao.getById(id) ?: return@withContext
        val existing = BrowserTabHistorySerializer.fromJson(row.historyJson, row.url, row.title)
        if (newIndex !in existing.indices) return@withContext
        val entry = existing[newIndex]
        dao.updateHistory(
            id = id,
            json = row.historyJson,  // unchanged
            idx = newIndex,
            url = entry.url,
            title = entry.title
        )
    }
}
