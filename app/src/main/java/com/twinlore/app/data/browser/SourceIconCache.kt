package com.twinlore.app.data.browser

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Host-keyed cache of source favicons, fed exclusively by pages the user opens in the
 * built-in browser (WebChromeClient.onReceivedIcon). The guide and map source menus
 * read from here instead of Google's favicon service: fetching icons from Google sent
 * the user's IP and the offered hosts to a third party merely for opening a list
 * (privacy policy §3.3), while an icon harvested from a visit the user chose adds no
 * connection at all. Hosts without a cached icon get a monogram in the UI.
 */
@Singleton
class SourceIconCache @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val dir: File get() = File(context.filesDir, DIR_NAME)

    /** Stores [png] for the host of [pageUrl]; silently drops unusable input. */
    suspend fun store(pageUrl: String?, png: ByteArray): Unit = withContext(Dispatchers.IO) {
        val host = normalize(hostOf(pageUrl)) ?: return@withContext
        runCatching {
            dir.mkdirs()
            val tmp = File(dir, "$host.png.tmp")
            tmp.writeBytes(png)
            if (!tmp.renameTo(File(dir, "$host.png"))) tmp.delete()
        }
    }

    /** The cached icon for [host], or null if the user has never visited it. */
    fun fileFor(host: String?): File? {
        val h = normalize(host) ?: return null
        return File(dir, "$h.png").takeIf { it.isFile }
    }

    /**
     * Stores a favicon fetched by a native (non-WebView) flow, such as the VGMaps picker:
     * those flows never pass through WebChromeClient.onReceivedIcon, so the sites they visit
     * stayed monograms forever. Same privacy footing as the browser harvest - the bytes come
     * from the very host the user chose to browse, never a third party.
     *
     * Raw favicons are usually ICO, which BitmapFactory cannot read; ImageDecoder can, so
     * the bytes are decoded (ICO/PNG/anything Android knows) and re-encoded as PNG to match
     * what [fileFor] serves. Unusable input is silently dropped, like [store].
     */
    suspend fun storeAnyFormat(pageUrl: String?, bytes: ByteArray): Unit = withContext(Dispatchers.IO) {
        val host = normalize(hostOf(pageUrl)) ?: return@withContext
        runCatching {
            val source = android.graphics.ImageDecoder.createSource(java.nio.ByteBuffer.wrap(bytes))
            val bitmap = android.graphics.ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
            }
            val png = java.io.ByteArrayOutputStream().also {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }.toByteArray()
            dir.mkdirs()
            val tmp = File(dir, "$host.png.tmp")
            tmp.writeBytes(png)
            if (!tmp.renameTo(File(dir, "$host.png"))) tmp.delete()
        }
    }

    companion object {
        private const val DIR_NAME = "source-icons"
        private val SAFE_HOST = Regex("^[a-z0-9.-]+$")

        /**
         * One key per site: lowercase and without the www prefix, so the menus'
         * bare hosts ("neoseeker.com") match pages served from "www.neoseeker.com".
         * Anything that couldn't safely be a file name is rejected.
         */
        fun normalize(host: String?): String? {
            val h = host?.trim()?.lowercase()?.removePrefix("www.") ?: return null
            return h.takeIf { it.isNotBlank() && SAFE_HOST.matches(it) }
        }

        fun hostOf(url: String?): String? =
            runCatching { java.net.URI(url ?: return null).host }.getOrNull()
    }
}
