package com.twinlore.app.data.library

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import androidx.documentfile.provider.DocumentFile
import com.twinlore.app.data.esde.EsdeFsAccess
import com.twinlore.app.data.library.LibraryFolderManager.Companion.findChildIgnoreCase
import com.twinlore.app.data.local.ResourceFileDao
import com.twinlore.app.data.local.ResourceFileEntity
import com.twinlore.app.data.local.VaultDao
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for turning a `resource_files` row into a usable [Uri].
 * Implements filename + size recovery: if the remembered path no longer resolves,
 * scans the row's vault subdirectory under the home folder for a matching
 * filename and size, then rewrites the row.
 */
@Singleton
class ResourceResolver(
    private val context: Context,
    private val dao: ResourceFileDao,
    private val vaultDao: VaultDao,
    private val folder: LibraryFolderManager,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val elapsedRealtime: () -> Long = { SystemClock.elapsedRealtime() },
    private val isAllFilesAccessGranted: () -> Boolean = { EsdeFsAccess.isGranted() },
    /**
     * One level of SAF child lookup, injectable so [dirDocCache]'s hit/miss behaviour
     * can be asserted without standing up a real DocumentsProvider.
     */
    private val findChild: (DocumentFile, String) -> DocumentFile? =
        { parent, name -> parent.findChildIgnoreCase(name) }
) {

    /**
     * Hilt-friendly constructor. The lambda-typed `clock` parameter cannot be expressed as
     * a Dagger binding without a custom @Provides, so the @Inject ctor delegates to the
     * primary one with the production clock baked in.
     */
    @Inject
    constructor(
        @ApplicationContext context: Context,
        dao: ResourceFileDao,
        vaultDao: VaultDao,
        folder: LibraryFolderManager
    ) : this(
        context,
        dao,
        vaultDao,
        folder,
        clock = { System.currentTimeMillis() },
        elapsedRealtime = { SystemClock.elapsedRealtime() }
    )

    /**
     * Memo of resolveFileId -> (resolved Uri, elapsedRealtime it was cached at), populated
     * only on success. Keys are `resource_files.id`s, values are
     * small (an id, a Uri, and a Long each), and the key space is bounded by the size of that
     * table, so an unbounded map is fine: a library with thousands of resources still amounts
     * to a few hundred KB at most, and there is no unbounded/streaming key source that could
     * grow this without limit.
     *
     * Cache hits (within [CACHE_TTL_MILLIS]) must do zero IO (no Room read, no SAF query, no
     * Room write) — that is the entire point of this cache, since `resolve()` was measured at
     * ~28ms of CPU and ~10 ContentResolver.query calls per call before this existed.
     *
     * Entries expire after [CACHE_TTL_MILLIS] so that a file deleted, renamed, or replaced
     * outside the app (Files app, a scraper) is picked back up by the recovery walk within a
     * minute, rather than staying broken for the life of the process. The timestamp is
     * [SystemClock.elapsedRealtime], not wall-clock time, so a clock change cannot strand
     * entries as permanently cached or permanently expired.
     */
    private val resolvedUriCache = ConcurrentHashMap<String, CacheEntry>()

    private data class CacheEntry(val uri: Uri, val cachedAtElapsedMs: Long)

    /**
     * Memo of "<home uri>|<home-relative directory path>" -> the [DocumentFile] handle for
     * that directory.
     *
     * [preferredUri] used to walk from the tree root for every row it resolved, and each
     * step of that walk is a full children query into the documents provider. A library
     * whose rows are `<vault>/<page group>/<file>` therefore re-listed the home folder and
     * the vault folder once per row -- on a rescan of a few hundred rows that is the bulk
     * of the SAF traffic, and the reason an audit could run for minutes.
     *
     * Only directories are memoized, never the leaf: the leaf lookup is what proves the
     * file still exists, and an audit that trusted a cached leaf would report "fine" for a
     * file that had been deleted. Only successful lookups are stored, so a directory
     * created after a miss is still found on the next call.
     *
     * Entries carry the same [CACHE_TTL_MILLIS] as [resolvedUriCache], refreshed on each
     * hit so a directory touched throughout a long rescan stays cached for its duration.
     * A directory deleted and recreated under the same name leaves a stale handle whose
     * child lookups fail, which falls through to [legacyResolve]'s recovery walk -- the
     * same path a genuinely moved file takes.
     */
    private val dirDocCache = ConcurrentHashMap<String, DirCacheEntry>()

    private class DirCacheEntry(val doc: DocumentFile, @Volatile var cachedAtElapsedMs: Long)

    /**
     * The home folder's derived filesystem root, computed once and reused (round two,
     * task 1). It changes only when the home folder itself changes, which already
     * calls [invalidateAll] at every one of its call sites (see
     * HomeFolderSetupCoordinator, StorageFolderMigrationCoordinator,
     * PersistedFolderValidator, LibraryIntegrityChecker), so clearing this alongside
     * [resolvedUriCache] there is sufficient -- no separate invalidation path needed.
     *
     * Wrapped in a box so "not yet computed" (null box) is distinguishable from
     * "computed and did not derive" (box holding a null [File]); the latter must not
     * be recomputed on every call, or a device where the root never derives (SAF
     * fallback in permanent use) would pay the derivation cost on every resolve.
     */
    @Volatile private var fsRootBox: FsRootBox? = null

    private class FsRootBox(val root: File?)

    /**
     * Last [elapsedRealtime] a fast-path hit wrote `lastSeenAt` for a given id, so that
     * write can be throttled (round two, task 1). [fastPathResolve] re-checks
     * [File.exists] on every call -- that is the point of the fast path -- but
     * `dao.touchLastSeen` is a Room `UPDATE` that also fires the invalidation tracker
     * for any observer of `resource_files`. Doing that write on every call (the fast
     * path has no Uri-level cache the way [legacyResolve] does, so nothing else spaces
     * these out) would reintroduce exactly the "avoidable IO on every call" cost the
     * TTL cache existed to prevent. Throttling only this bookkeeping write -- not the
     * existence check -- keeps self-healing instant while keeping the write cadence
     * the same as before this change.
     */
    private val lastTouchedElapsedMs = ConcurrentHashMap<String, Long>()

    /** Resolve a `resource_files` row id to a usable URI, running recovery on miss. */
    suspend fun resolve(resourceFileId: String): Uri? {
        if (isAllFilesAccessGranted()) {
            fastPathResolve(resourceFileId)?.let { return it }
        }
        return legacyResolve(resourceFileId)
    }

    /**
     * Direct-filesystem fast path (round two, task 1): when All-Files access is
     * granted and the home folder's tree URI converts to an absolute path, turn the
     * row's stored home-relative path into a real [File] and check
     * [File.exists] -- a stat syscall, not a Binder round trip, so unlike
     * [legacyResolve] there is no reason to trust a cached Uri for up to
     * [CACHE_TTL_MILLIS]: existence is re-checked on every call, which is what makes
     * this branch heal instantly from a file renamed or deleted outside the app.
     *
     * Returns null (not a broken Uri) on anything that keeps this path from being
     * trustworthy -- root didn't derive, row has no homeRelativePath, or the file
     * isn't at the expected path -- so the caller falls through to
     * [legacyResolve]'s recovery walk.
     */
    private suspend fun fastPathResolve(resourceFileId: String): Uri? {
        val root = fsRoot() ?: return null
        val row = dao.getById(resourceFileId) ?: return null
        val rel = row.homeRelativePath ?: return null
        if (!isPlainRelativePath(rel)) return null
        val file = File(root, rel)
        if (!file.exists()) return null
        maybeTouchLastSeen(row.id)
        return Uri.fromFile(file)
    }

    /**
     * The read-compare-suspend-write shape this used to have (`get`, compare, `suspend`
     * for the Room write, then `put`) was not atomic: two coroutines resolving the same
     * id concurrently -- e.g. the same artwork composed at once in a grid tile, a
     * carousel background and a showcase blur -- could both read a stale/absent `last`,
     * both pass the throttle, and both issue the `UPDATE` plus its invalidation-tracker
     * broadcast, defeating the throttle in exactly the concurrent burst it exists for.
     *
     * [ConcurrentHashMap.compute] claims the throttle slot atomically (its per-bin
     * locking serializes concurrent callers for the same key) *before* the suspending
     * Room write, so only the coroutine that wins the claim performs the write. If that
     * write then throws, the claim is rolled back so a later call can retry rather than
     * being throttled for a failed write.
     */
    private suspend fun maybeTouchLastSeen(resourceFileId: String) {
        val now = elapsedRealtime()
        var claimed = false
        lastTouchedElapsedMs.compute(resourceFileId) { _, last ->
            if (last != null && now - last < CACHE_TTL_MILLIS) {
                last
            } else {
                claimed = true
                now
            }
        }
        if (!claimed) return
        try {
            dao.touchLastSeen(resourceFileId, clock())
        } catch (t: Throwable) {
            lastTouchedElapsedMs.remove(resourceFileId)
            throw t
        }
    }

    private suspend fun fsRoot(): File? {
        fsRootBox?.let { return it.root }
        val root = folder.homeFolderFsRoot()
        fsRootBox = FsRootBox(root)
        return root
    }

    /**
     * The path this class had before round two, task 1: a Uri cache with a
     * [CACHE_TTL_MILLIS] TTL feeding SAF traversal (or a `file://` home,
     * see [preferredUri]) and filename+size recovery. Kept for every device where
     * the fast path above doesn't apply -- access not granted, or the home tree's
     * document id doesn't convert to an absolute path (e.g. an SD card, or a
     * provider other than `com.android.externalstorage`).
     */
    private suspend fun legacyResolve(resourceFileId: String): Uri? {
        val now = elapsedRealtime()
        resolvedUriCache[resourceFileId]?.let { entry ->
            if (now - entry.cachedAtElapsedMs < CACHE_TTL_MILLIS) return entry.uri
        }

        val row = dao.getById(resourceFileId) ?: return null

        val cached = preferredUri(row)
        if (cached != null && folder.exists(cached)) {
            dao.touchLastSeen(row.id, clock())
            resolvedUriCache[resourceFileId] = CacheEntry(cached, now)
            return cached
        }

        val vaultId = row.vaultId ?: return null
        val vault = vaultDao.getVaultById(vaultId) ?: return null
        val vaultDir = LibraryFolderManager.resolveFolderName(vault)
        val hit = folder.findByNameAndSizeInVault(vaultDir, row.filename, row.sizeBytes) ?: return null
        dao.update(
            row.copy(
                homeRelativePath = hit.relativePath,
                absolutePath = null,
                lastSeenAt = clock()
            )
        )
        resolvedUriCache[resourceFileId] = CacheEntry(hit.uri, now)
        return hit.uri
    }

    /**
     * Drop the cached Uri for [resourceFileId], forcing the next [resolve] to walk
     * SAF again. Call this whenever the file backing an id can have changed:
     * artwork reassignment, a rescan/integrity check, or a home-folder switch.
     */
    fun invalidate(resourceFileId: String) {
        resolvedUriCache.remove(resourceFileId)
        lastTouchedElapsedMs.remove(resourceFileId)
    }

    /**
     * Drop every cached Uri. Call this on events that can invalidate the whole
     * cache at once, e.g. the home folder's SAF tree Uri changing (permission
     * re-grant or storage-folder migration) or a full library rescan.
     *
     * Also drops the cached fast-path fs root (round two, task 1): every call site
     * of this method is already an event where the home folder itself may have
     * changed, which is the only thing that can make a previously-derived root
     * stale.
     */
    fun invalidateAll() {
        resolvedUriCache.clear()
        fsRootBox = null
        lastTouchedElapsedMs.clear()
        dirDocCache.clear()
    }

    companion object {
        /**
         * How long a resolved Uri is trusted before the next [legacyResolve] re-checks
         * existence. Only [legacyResolve] (SAF traversal, or a `file://` home) pays for
         * this TTL; [fastPathResolve] re-checks [File.exists] on every call instead,
         * because that check is a stat syscall rather than a Binder round trip, so there
         * is nothing left for a TTL to save (round two, task 1).
         *
         * Chosen in the 30-60s range: a d-pad navigation burst fires ~3 resolves per keypress
         * for the same handful of on-screen ids, so within any 45s window almost every one of
         * those resolves still lands inside the TTL and costs zero IO (the SAF-walk win this
         * cache exists for). Past 45s of no re-resolve for a given id, the next hit re-checks
         * existence and heals if the backing file moved, was renamed, or was deleted.
         */
        private const val CACHE_TTL_MILLIS = 45_000L

        /** A path that stays under the folder it is joined to: no leading slash, no `..` segment. */
        internal fun isPlainRelativePath(rel: String): Boolean =
            rel.isNotEmpty() && !rel.startsWith("/") && rel.split('/').none { it == ".." }
    }

    /**
     * Walks [segments] down from [root], serving each directory from [dirDocCache] when a
     * live entry is present and caching every directory it does have to look up. Returns
     * null as soon as a segment is absent, matching the previous walk's behaviour.
     *
     * ignore-case throughout: the FS is case-insensitive, so a vault re-created with
     * different casing reuses the old dir (see LibraryFolderManager.findChildIgnoreCase).
     */
    private fun resolveDir(home: Uri, root: DocumentFile, segments: List<String>): DocumentFile? {
        if (segments.isEmpty()) return root
        val now = elapsedRealtime()
        var doc = root
        val path = StringBuilder()
        for (segment in segments) {
            if (path.isNotEmpty()) path.append('/')
            path.append(segment)
            val key = "$home|$path"
            val cached = dirDocCache[key]
            if (cached != null && now - cached.cachedAtElapsedMs < CACHE_TTL_MILLIS) {
                cached.cachedAtElapsedMs = now
                doc = cached.doc
                continue
            }
            val child = findChild(doc, segment) ?: return null
            dirDocCache[key] = DirCacheEntry(child, now)
            doc = child
        }
        return doc
    }

    private suspend fun preferredUri(row: ResourceFileEntity): Uri? {
        row.homeRelativePath?.takeIf(::isPlainRelativePath)?.let { rel ->
            val home = folder.homeFolderUri() ?: return@let
            return when (home.scheme) {
                "file" -> Uri.parse("$home/$rel")
                "content" -> {
                    val root = DocumentFile.fromTreeUri(context, home) ?: return@let null
                    val segments = rel.split('/').filter { it.isNotEmpty() }
                    // No row carries an empty relative path, and one that did would
                    // otherwise resolve to the home directory itself, i.e. a folder
                    // handed back where a file is expected. Treat it as unresolvable.
                    if (segments.isEmpty()) return@let null
                    val parent = resolveDir(home, root, segments.dropLast(1)) ?: return@let null
                    // The leaf is always looked up fresh -- see dirDocCache's contract.
                    findChild(parent, segments.last())?.uri
                }
                else -> null
            }
        }
        row.absolutePath?.let {
            return Uri.parse(if (it.startsWith("content://")) it else "file://$it")
        }
        return null
    }
}
