package com.twinlore.app.data.library

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

private const val TAG = "MapThumbnailCache"
private const val TARGET_WIDTH_PX = 512
private const val WEBP_QUALITY = 85
// Caps simultaneous decode+encode operations; 4 lets the pool stay busy without
// OOM-pressure from loading multiple multi-MB PNGs concurrently.
private const val GENERATION_CONCURRENCY = 4

/**
 * On-disk cache of small WebP thumbnails (one per map resource file) used to feed the
 * Maps picker grid and list tiles. Source maps can be multi-MB PNGs; decoding them on
 * every recompose caused a sustained CPU spike. A pre-rendered 512-px-wide WebP is a
 * fraction of the bytes and decodes much faster.
 *
 * Thread-safety: [generate] is safe on any thread; [getOrGenerate] deduplicates concurrent
 * callers via [inFlight] + [inFlightMutex] and caps simultaneous decodes via [semaphore].
 *
 * Parameterized via [cacheDirProvider] and [cacheScope] so tests can inject a temp directory
 * and a TestScope for deterministic coroutine control.
 *
 * Writes are hooked at:
 *  - [VaultRepository.addFile] for kind == MAP (eager warm generation).
 *  - [MapViewerScreenViewModel.deleteSavedMap] (single-id invalidation).
 *  - [VaultRepository.deleteVault] (batch invalidation by vault's map resource ids).
 *  - [LibraryImporter.importFrom] (wipeAll on snapshot swap).
 */
@Singleton
class MapThumbnailCache @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /**
     * Internal constructor for tests to inject a custom cache dir and/or coroutine scope.
     * Using [CoroutineScope] here instead of [TestScope] so the signature stays stable.
     */
    internal constructor(
        context: Context,
        cacheDirProvider: () -> File,
        cacheScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    ) : this(context) {
        this.cacheDirProviderOverride = cacheDirProvider
        this.cacheScopeOverride = cacheScope
    }

    private var cacheDirProviderOverride: (() -> File)? = null
    private var cacheScopeOverride: CoroutineScope? = null

    private val cacheDir: File
        get() = cacheDirProviderOverride?.invoke() ?: File(context.filesDir, "map-thumbs")

    private val cacheScope: CoroutineScope
        get() = cacheScopeOverride ?: defaultCacheScope

    // Lazily initialized so the production default scope is only created when needed.
    private val defaultCacheScope by lazy { CoroutineScope(SupervisorJob() + Dispatchers.IO) }

    private val semaphore = Semaphore(permits = GENERATION_CONCURRENCY)
    private val inFlight = ConcurrentHashMap<String, Deferred<File?>>()
    private val inFlightMutex = Mutex()

    private fun thumbFile(resourceId: String): File = File(cacheDir, "$resourceId.webp")

    /**
     * Returns the thumb file for [resourceId] if it exists on disk, else null.
     * One stat syscall per call; safe to call from IO dispatcher, avoid bulk use on main thread.
     */
    fun pathFor(resourceId: String): File? = thumbFile(resourceId).takeIf { it.exists() }

    /**
     * Decode the source URI, downsample to ~512 px wide, encode as WebP, write atomically.
     * Returns the resulting File on success, null on any failure. Safe to call from Dispatchers.IO.
     */
    fun generate(resourceId: String, sourceUri: Uri): File? {
        ensureCacheDir()
        val target = thumbFile(resourceId)
        val tmp = File.createTempFile("$resourceId-", ".webp.tmp", cacheDir)
        var finalBitmap: Bitmap? = null
        return try {
            val (srcW, _) = readSourceBounds(sourceUri)
                ?: return fail(tmp, "readSourceBounds null resourceId=$resourceId uri=$sourceUri")
            if (srcW <= 0) return fail(tmp, "srcW=$srcW resourceId=$resourceId uri=$sourceUri")

            val sample = computeInSampleSize(srcW, TARGET_WIDTH_PX)
            val decoded = decodeSource(sourceUri, sample)
                ?: return fail(tmp, "decodeSource null resourceId=$resourceId sample=$sample")

            finalBitmap = if (decoded.width > TARGET_WIDTH_PX) {
                val targetH = (decoded.height.toLong() * TARGET_WIDTH_PX / decoded.width).toInt()
                val scaled = Bitmap.createScaledBitmap(decoded, TARGET_WIDTH_PX, targetH, true)
                if (scaled !== decoded) decoded.recycle()
                scaled
            } else decoded

            val encoded = tmp.outputStream().use { out ->
                finalBitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, WEBP_QUALITY, out)
            }
            if (!encoded) return fail(tmp, "compress returned false resourceId=$resourceId")
            if (!tmp.renameTo(target)) return fail(tmp, "renameTo returned false resourceId=$resourceId")
            target
        } catch (e: Exception) {
            fail(tmp, "generate threw resourceId=$resourceId: ${e.message}")
        } finally {
            finalBitmap?.recycle()
        }
    }

    /**
     * Suspend wrapper around [generate] that deduplicates concurrent callers for the same
     * [resourceId] and caps simultaneous decode operations via [semaphore].
     *
     * Fast path: if the thumb already exists it is returned immediately without acquiring the
     * semaphore or creating a Deferred.
     *
     * Dedup path: if another coroutine is already generating the same [resourceId], this call
     * joins that Deferred and returns the same result.
     */
    suspend fun getOrGenerate(resourceId: String, sourceProvider: suspend () -> Uri?): File? {
        // Fast path: already cached.
        pathFor(resourceId)?.let { return it }

        val deferred = inFlightMutex.withLock {
            // Re-check after acquiring lock in case another coroutine finished between
            // the fast-path check above and reaching here.
            pathFor(resourceId)?.let { return@withLock CompletableDeferred(it) }

            inFlight.getOrPut(resourceId) {
                cacheScope.async(start = CoroutineStart.LAZY) {
                    try {
                        semaphore.withPermit {
                            val uri = sourceProvider() ?: return@withPermit null
                            generate(resourceId, uri)
                        }
                    } finally {
                        inFlight.remove(resourceId)
                    }
                }
            }.also { it.start() }
        }

        return runCatching { deferred.await() }.getOrNull()
    }

    /**
     * Fire-and-forget warm generation. Does not block the caller; errors are discarded.
     * Used from [VaultRepository.addFile] right after a new MAP resource is inserted.
     */
    fun warmGenerateAsync(resourceId: String, sourceProvider: suspend () -> Uri?) {
        cacheScope.launch { getOrGenerate(resourceId, sourceProvider) }
    }

    /** Delete the thumb file for [resourceId]. No-op when missing. */
    fun invalidate(resourceId: String) {
        thumbFile(resourceId).delete()
    }

    /** Delete thumb files for a collection of resource ids. No-op for missing entries. */
    fun invalidateAll(resourceIds: Collection<String>) {
        for (id in resourceIds) thumbFile(id).delete()
    }

    /**
     * Remove every thumb in the cache. Called from LibraryImporter when a snapshot replaces
     * the live database: the old resource ids may no longer exist and would orphan their thumbs.
     */
    fun wipeAll() {
        cacheDir.listFiles()?.forEach { it.delete() }
    }

    private fun fail(tmp: File, reason: String): File? {
        Log.w(TAG, reason)
        tmp.delete()
        return null
    }

    private fun ensureCacheDir() {
        if (!cacheDir.exists()) cacheDir.mkdirs()
    }

    private fun readSourceBounds(uri: Uri): Pair<Int, Int>? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val stream = context.contentResolver.openInputStream(uri) ?: return null
        stream.use { BitmapFactory.decodeStream(it, null, opts) }
        if (opts.outWidth <= 0 || opts.outHeight <= 0) return null
        return opts.outWidth to opts.outHeight
    }

    private fun decodeSource(uri: Uri, sample: Int): Bitmap? {
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        }
    }

    /** Largest power-of-two inSampleSize that still leaves the decoded width >= 2x target. */
    private fun computeInSampleSize(srcW: Int, reqW: Int): Int {
        var sample = 1
        while ((srcW / sample) > reqW * 2) sample *= 2
        return sample
    }
}
