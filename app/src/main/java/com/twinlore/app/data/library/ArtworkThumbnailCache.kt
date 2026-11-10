package com.twinlore.app.data.library

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ArtworkThumbnailCache"

/**
 * On-disk cache of small WebP thumbnails (one per vault) used to feed the
 * Library carousel and grid. Source artwork can be multi-MB; decoding it every
 * cold start was the cause of a sustained CPU spike. A pre-rendered
 * [TARGET_WIDTH_PX]-wide WebP is a fraction of the bytes and decodes ~10× faster.
 *
 * Writes are funnelled through [VaultRepository.updateArtworkResourceId]
 * (covering scrape, auto-sync, browser-assign, remove) and [VaultRepository.deleteVault].
 * The Library carousel preload in [LibraryScreen] generates missing thumbs
 * lazily while the in-app preload splash is visible. [LibraryImporter] wipes
 * the whole cache after swapping in a new snapshot (the imported vault ids
 * may not match).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class ArtworkThumbnailCache @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val cacheDir: File
        get() = File(context.filesDir, CACHE_DIR_NAME)

    private fun thumbFile(vaultId: String): File = File(cacheDir, "$vaultId.webp")

    /**
     * Deleting the previous cache generation is deliberately hung off [generate]
     * rather than off construction or [pathFor]: this is filesystem IO and must not
     * run in a @Singleton's init (main thread), nor be added to pathFor's per-tile
     * hot path. The first miss after an upgrade calls generate, so the purge lands
     * on the first library render and costs one rmdir.
     */
    @Volatile private var legacyPurged = false

    /**
     * Application-lifetime scope for [generateAsync], capped at
     * [GENERATE_PARALLELISM] threads. Not tied to any composition or ViewModel:
     * a thumb is worth finishing even if the tile that asked for it scrolled away,
     * because the next render of that vault would otherwise pay the source decode
     * all over again.
     */
    private val generateScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO.limitedParallelism(GENERATE_PARALLELISM)
    )

    /** Vaults with a generation in flight, so N tiles asking at once queue one job. */
    private val inFlight = ConcurrentHashMap.newKeySet<String>()

    private fun purgeLegacyOnce() {
        if (legacyPurged) return
        legacyPurged = true
        runCatching { File(context.filesDir, LEGACY_CACHE_DIR_NAME).deleteRecursively() }
    }

    /**
     * Returns the thumb file for [vaultId] if it exists on disk, else null.
     * One `stat` syscall per call — fine on Dispatchers.IO, avoid bulk use
     * from the main thread.
     */
    fun pathFor(vaultId: String): File? = thumbFile(vaultId).takeIf { it.exists() }

    /**
     * Fire-and-forget thumbnail generation, bounded to [GENERATE_PARALLELISM]
     * concurrent decodes and deduplicated per vault.
     *
     * Callers that are rendering a tile must use this rather than [generate]: the
     * first render after a cache-generation bump misses on every visible tile at
     * once, and each tile's own coroutine would otherwise start a multi-megabyte
     * source decode simultaneously. That is exactly the cold-start CPU spike this
     * class exists to prevent. Bounding it here rather than at each call site means
     * no caller can reintroduce the storm.
     *
     * Runs on an internal application-lifetime scope so a tile scrolling out of
     * composition does not cancel a generation that is already worth finishing.
     */
    fun generateAsync(vaultId: String, sourceUri: Uri) {
        if (!inFlight.add(vaultId)) return
        generateScope.launch {
            try {
                generate(vaultId, sourceUri)
            } finally {
                inFlight.remove(vaultId)
            }
        }
    }

    /**
     * Decode the source, downsample to [TARGET_WIDTH_PX] wide, encode as WebP, write
     * atomically. Returns the resulting File on success, null on any failure.
     * Safe to call from Dispatchers.IO. Blocking and unbounded — prefer
     * [generateAsync] from any path that runs once per visible tile.
     */
    fun generate(vaultId: String, sourceUri: Uri): File? {
        purgeLegacyOnce()
        ensureCacheDir()
        val target = thumbFile(vaultId)
        // Per-call unique tmp file so concurrent generates on the same vault
        // never share an intermediate path. The rename below is atomic on the
        // same filesystem so the loser of a race just gets dropped cleanly.
        val tmp = File.createTempFile("$vaultId-", ".webp.tmp", cacheDir)
        var finalBitmap: Bitmap? = null
        return try {
            val (srcW, _) = readSourceBounds(sourceUri) ?: return fail(tmp, "readSourceBounds null vault=$vaultId uri=$sourceUri")
            if (srcW <= 0) return fail(tmp, "srcW=$srcW vault=$vaultId uri=$sourceUri")

            val sample = computeInSampleSize(srcW, TARGET_WIDTH_PX)
            val decoded = decodeSource(sourceUri, sample) ?: return fail(tmp, "decodeSource null vault=$vaultId sample=$sample")

            finalBitmap = if (decoded.width > TARGET_WIDTH_PX) {
                val targetH = (decoded.height.toLong() * TARGET_WIDTH_PX / decoded.width).toInt()
                val scaled = Bitmap.createScaledBitmap(decoded, TARGET_WIDTH_PX, targetH, true)
                if (scaled !== decoded) decoded.recycle()
                scaled
            } else decoded

            val encoded = tmp.outputStream().use { out ->
                finalBitmap!!.compress(Bitmap.CompressFormat.WEBP_LOSSY, WEBP_QUALITY, out)
            }
            if (!encoded) return fail(tmp, "compress returned false vault=$vaultId")
            if (!tmp.renameTo(target)) return fail(tmp, "renameTo returned false vault=$vaultId")
            target
        } catch (e: Exception) {
            fail(tmp, "generate threw vault=$vaultId: ${e.message}")
        } finally {
            finalBitmap?.recycle()
        }
    }

    /** Delete the thumb file for [vaultId]. No-op when missing. */
    fun invalidate(vaultId: String) {
        thumbFile(vaultId).delete()
    }

    /**
     * Remove every thumb in the cache. Called from LibraryImporter when a
     * snapshot replaces the live database: the old vault ids may no longer
     * exist and would orphan their thumbs.
     */
    fun wipeAll() {
        // Also here, not only in generate: an install where every vault already has a
        // v2 thumb (or has no artwork at all) never calls generate, and would keep the
        // stranded legacy directory on disk indefinitely.
        purgeLegacyOnce()
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
        // In bounds-mode decodeStream ALWAYS returns null — it skips bitmap
        // allocation and populates opts.outWidth/outHeight instead. Don't
        // Elvis-chain off the use-block's return value: that would misread
        // "bounds populated successfully" as "stream open failed."
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

    /** Largest power-of-two inSampleSize that still leaves the decoded width >= 2× target. */
    private fun computeInSampleSize(srcW: Int, reqW: Int): Int {
        var sample = 1
        while ((srcW / sample) > reqW * 2) sample *= 2
        return sample
    }

    companion object {
        /**
         * Thumb width in pixels. Sized to cover the widest grid tile the app can show
         * (3-column GRID_EXTRA_LARGE is ~578 px on a 1920-wide panel) and the
         * carousel's 768-px card, so neither has to upsample. Was 480, which forced a
         * 2.25× upsample on the default grid mode.
         */
        const val TARGET_WIDTH_PX = 768

        /**
         * Cache generation. The directory NAME is the generation marker: bumping it
         * strands the previous generation's thumbs, which [purgeLegacyOnce] then
         * deletes. Without this, an upgraded install would keep serving 480-px thumbs
         * at the new larger decode size and look worse than before the change.
         */
        const val CACHE_DIR_NAME = "carousel-thumbs-v2"

        private const val LEGACY_CACHE_DIR_NAME = "carousel-thumbs"
        private const val WEBP_QUALITY = 85

        /**
         * Concurrent generations allowed by [generateAsync]. Two, not more: each one
         * holds a fully decoded source bitmap (multi-megabyte) while it downsamples,
         * so this bounds transient memory as much as it bounds CPU.
         */
        private const val GENERATE_PARALLELISM = 2
    }
}
