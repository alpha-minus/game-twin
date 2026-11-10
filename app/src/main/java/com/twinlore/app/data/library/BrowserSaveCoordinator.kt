package com.twinlore.app.data.library

import android.content.Context
import android.net.Uri
import android.webkit.WebView
import androidx.documentfile.provider.DocumentFile
import com.twinlore.app.data.codewheel.HtmlZipImporter
import com.twinlore.app.data.local.ResourceFileDao
import com.twinlore.app.data.local.ResourceFileEntity
import com.twinlore.app.data.local.SavedTabDao
import com.twinlore.app.data.local.SavedTabEntity
import com.twinlore.app.data.local.VaultDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Result of a browser save operation. [Success] carries the new resource_files row id
 * and (for guides) the SavedTab row id created alongside it.
 */
sealed class SaveResult {
    data class Success(val resourceFileId: String, val savedTabId: String?) : SaveResult()
    data class Failed(val cause: Throwable) : SaveResult()
}

/**
 * Coordinator that takes content from a [WebView] (or HTTP/data URLs)
 * and writes it into the user's home folder, registering a row in [ResourceFileDao]
 * and (for guides) a [SavedTabEntity] for browser-tab restoration.
 */
@Singleton
class BrowserSaveCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val httpClient: OkHttpClient,
    private val folder: LibraryFolderManager,
    private val resourceDao: ResourceFileDao,
    private val savedTabDao: SavedTabDao,
    private val vaultDao: VaultDao
) {
    /** 0..1 progress of the current save (used by future download UI). */
    val progress = MutableStateFlow(0f)

    /**
     * Apply browser-like headers + the WebView's cookies for [url] to [builder].
     * Bare OkHttp requests are fingerprinted as bots by Cloudflare/Akamai (no UA,
     * no Accept, no cookies → HTTP 400 with a "Request Blocked" challenge page),
     * even when the matching WebView load succeeded a moment earlier.
     */
    private fun Request.Builder.applyBrowserHeaders(url: String, referer: String?): Request.Builder {
        header("User-Agent", BROWSER_USER_AGENT)
        header("Accept", "image/webp,image/apng,image/*,*/*;q=0.8")
        header("Accept-Language", "en-US,en;q=0.9")
        if (!referer.isNullOrBlank()) header("Referer", referer)
        val cookies = runCatching { android.webkit.CookieManager.getInstance().getCookie(url) }.getOrNull()
        if (!cookies.isNullOrBlank()) header("Cookie", cookies)
        return this
    }

    /**
     * Saves the current page of [webView] as an MHT archive into the vault's
     * sub-folder under the home folder, and records resource_files + saved_tabs rows.
     *
     * Must be called from a coroutine; the [WebView.saveWebArchive] callback runs on
     * the main thread, so we hop to Main for the WebView interaction and switch to
     * IO inside [moveIntoHomeAndAttach] for filesystem and DB work.
     */
    suspend fun saveCurrentPage(webView: WebView, vaultId: String): SaveResult = withContext(Dispatchers.Main) {
        val vault = vaultDao.getVaultById(vaultId)
            ?: return@withContext SaveResult.Failed(IllegalStateException("Vault not found"))
        val cacheFile = File(context.cacheDir, "save_${UUID.randomUUID()}.mht")
        val pageTitle = webView.title.orEmpty().ifEmpty { "Untitled" }
        val webUrl = webView.url
        var handedOff = false
        try {
            // Stash iframe srcs into data-original-src, strip lazy on every <img>,
            // sweep-scroll the page, and wait until every image reports .complete
            // (capped by an 8s budget + 1s settle). See [MhtSaveHelpers].
            MhtSaveHelpers.preloadForArchive(webView)

            val written: File? = withTimeoutOrNull(30_000L) {
                suspendCancellableCoroutine<File?> { cont ->
                    // The WebView callback can still fire and write bytes after
                    // the continuation is cancelled — delete the temp on cancel
                    // so we don't leak it into cacheDir.
                    cont.invokeOnCancellation { runCatching { cacheFile.delete() } }
                    webView.saveWebArchive(cacheFile.absolutePath, false) { resultPath ->
                        if (cont.isActive) cont.resume(resultPath?.let(::File))
                    }
                }
            }
            if (written == null) {
                return@withContext SaveResult.Failed(
                    RuntimeException("saveWebArchive returned null or timed out")
                )
            }

            val result = LibrarySaveHelpers.moveIntoHomeAndAttach(
                context = context,
                folder = folder,
                resourceDao = resourceDao,
                savedTabDao = savedTabDao,
                cacheFile = written,
                suggestedFilename = LibrarySaveHelpers.sanitizeTitleForFilename(pageTitle) + ".mht",
                vaultId = vault.id,
                vaultName = LibraryFolderManager.sanitizeForPath(vault.name),
                kind = "guide",
                mimeType = "multipart/related",
                webUrl = webUrl
            )
            // moveIntoHomeAndAttach renames or deletes cacheFile on its success
            // path; mark handedOff so the finally branch skips its no-op delete.
            if (result is SaveResult.Success) handedOff = true
            return@withContext result
        } finally {
            if (!handedOff) runCatching { cacheFile.delete() }
        }
    }

    /**
     * Fetch image dimensions + mime over HTTP without downloading the full body.
     * Uses the same OkHttp client + browser-like headers + WebView cookies as
     * [saveImage], so Cloudflare-protected images that pass the save flow also
     * surface metadata here. Returns null on network/decoder failure.
     *
     * Routed through this class (rather than HttpURLConnection in the UI layer)
     * because Cloudflare's bot detection 403s the JDK HttpURLConnection client
     * even with identical headers — OkHttp's TLS/HTTP-2 fingerprint is what gets
     * waved through.
     */
    suspend fun fetchImageMetadata(url: String, referer: String?): ImageDimensions? = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url).applyBrowserHeaders(url, referer).build()
        try {
            executeCancellable(req).use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val body = resp.body ?: return@withContext null
                val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                body.byteStream().use { stream ->
                    android.graphics.BitmapFactory.decodeStream(stream, null, options)
                }
                val w = options.outWidth
                val h = options.outHeight
                if (w > 0 && h > 0) ImageDimensions(w, h, options.outMimeType) else null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Downloads the image at [url] over HTTP and stores it in the vault folder under
     * [kind] (typically "screenshot" or another image kind). [referer] is forwarded
     * as the HTTP `Referer` header when set, so hot-linked images that gate on it
     * still resolve.
     */
    suspend fun saveImage(
        url: String,
        kind: String,
        vaultId: String,
        referer: String?
    ): SaveResult = withContext(Dispatchers.IO) {
        val vault = vaultDao.getVaultById(vaultId)
            ?: return@withContext SaveResult.Failed(IllegalStateException("Vault not found"))
        val req = Request.Builder().url(url).applyBrowserHeaders(url, referer).build()
        executeCancellable(req).use { resp ->
            if (!resp.isSuccessful) return@withContext SaveResult.Failed(RuntimeException("HTTP ${resp.code}"))
            val body = resp.body ?: return@withContext SaveResult.Failed(RuntimeException("Empty body"))
            val tmp = File(context.cacheDir, "img_${UUID.randomUUID()}")
            tmp.outputStream().use { body.byteStream().copyTo(it) }
            val urlName = deriveImageFilename(url)
            return@withContext LibrarySaveHelpers.moveIntoHomeAndAttach(
                context = context,
                folder = folder,
                resourceDao = resourceDao,
                savedTabDao = savedTabDao,
                cacheFile = tmp,
                suggestedFilename = urlName,
                vaultId = vault.id,
                vaultName = LibraryFolderManager.resolveFolderName(vault),
                kind = kind,
                mimeType = resp.header("Content-Type") ?: "image/*",
                webUrl = url
            )
        }
    }

    /**
     * Routes a WebView download (from `setDownloadListener`) to the right destination.
     *
     * Guides (`pdf`, `txt`, `html`/`htm`) and screenshot-typed images are stored in the
     * vault folder via [moveIntoHomeAndAttach] and registered in the resource_files
     * table. Anything else lands in `<home>/_unfiled/` with no DB row, returning
     * [SaveResult.Success] with an empty `resourceFileId` as the sentinel.
     */
    suspend fun handleDownload(
        url: String,
        contentType: String?,
        contentDisposition: String?,
        vaultId: String,
        referer: String?
    ): SaveResult = withContext(Dispatchers.IO) {
        val vault = vaultDao.getVaultById(vaultId)
            ?: return@withContext SaveResult.Failed(IllegalStateException("Vault not found"))
        val req = Request.Builder().url(url).applyBrowserHeaders(url, referer).build()
        executeCancellable(req).use { resp ->
            if (!resp.isSuccessful) return@withContext SaveResult.Failed(RuntimeException("HTTP ${resp.code}"))
            val body = resp.body ?: return@withContext SaveResult.Failed(RuntimeException("Empty body"))
            val tmp = File(context.cacheDir, "dl_${UUID.randomUUID()}")
            tmp.outputStream().use { body.byteStream().copyTo(it) }
            val filename = LibrarySaveHelpers.sanitizeDownloadFilename(
                parseFilename(contentDisposition) ?: url.substringAfterLast('/').substringBefore('?')
            )
            val type = contentType?.lowercase() ?: ""
            val isZip = type.startsWith("application/zip") ||
                type == "application/x-zip-compressed" ||
                filename.endsWith(".zip", ignoreCase = true)
            if (isZip) {
                val slim = File(context.cacheDir, "htmlzip_${UUID.randomUUID()}.zip")
                val outcome = try {
                    HtmlZipImporter.classify(tmp, slim)
                } catch (e: Throwable) {
                    tmp.delete(); slim.delete()
                    return@withContext SaveResult.Failed(e)
                }
                when (outcome) {
                    is HtmlZipImporter.Outcome.Bundle -> {
                        try {
                            return@withContext LibrarySaveHelpers.moveIntoHomeAndAttach(
                                context = context,
                                folder = folder,
                                resourceDao = resourceDao,
                                savedTabDao = savedTabDao,
                                cacheFile = slim,
                                suggestedFilename = filename,
                                vaultId = vault.id,
                                vaultName = LibraryFolderManager.resolveFolderName(vault),
                                kind = "guide",
                                mimeType = "application/zip",
                                webUrl = url,
                                forcedFileType = outcome.fileType,
                            )
                        } finally {
                            tmp.delete(); slim.delete()
                        }
                    }
                    HtmlZipImporter.Outcome.NotAnHtmlBundle -> {
                        slim.delete() // fall through to the existing _unfiled logic (tmp survives)
                    }
                }
            }
            val kind = when {
                type.startsWith("application/pdf") || filename.endsWith(".pdf", true) -> "guide"
                type.startsWith("text/plain") || filename.endsWith(".txt", true) -> "guide"
                type.startsWith("text/html") || filename.endsWith(".html", true) || filename.endsWith(".htm", true) -> "guide"
                type.startsWith("image/") -> "map" // mirror long-press "Save Image": image surfaces in Map tab
                else -> "_unfiled"
            }
            if (kind == "_unfiled") {
                val homeUri = folder.homeFolderUri()
                    ?: return@withContext SaveResult.Failed(IllegalStateException("Storage folder is not set"))
                when (homeUri.scheme) {
                    "file" -> {
                        val home = File(homeUri.path
                            ?: return@withContext SaveResult.Failed(IllegalStateException("Bad home path")))
                        val unfiled = File(home, "_unfiled").apply { mkdirs() }
                        val target = LibrarySaveHelpers.pickUniqueTarget(unfiled, filename)
                        if (!tmp.renameTo(target)) {
                            tmp.copyTo(target, overwrite = false)
                            tmp.delete()
                        }
                    }
                    "content" -> {
                        val unfiledDocUri = folder.findOrCreateChildDir(homeUri, "_unfiled")
                            ?: return@withContext SaveResult.Failed(IllegalStateException("Could not create _unfiled"))
                        val unfiledDoc = DocumentFile.fromTreeUri(context, unfiledDocUri)
                            ?: return@withContext SaveResult.Failed(IllegalStateException("Bad _unfiled tree"))
                        val uniqueName = LibrarySaveHelpers.pickUniqueDocName(unfiledDoc, filename)
                        val targetDoc = unfiledDoc.createFile(type.ifBlank { "application/octet-stream" }, uniqueName)
                            ?: return@withContext SaveResult.Failed(IllegalStateException("Could not create file"))
                        val ok = context.contentResolver.openOutputStream(targetDoc.uri)?.use { out ->
                            tmp.inputStream().use { it.copyTo(out) }
                            true
                        } ?: false
                        if (!ok) {
                            targetDoc.delete()
                            return@withContext SaveResult.Failed(IllegalStateException("Could not open output"))
                        }
                        tmp.delete()
                    }
                    else -> return@withContext SaveResult.Failed(IllegalStateException("Unsupported scheme"))
                }
                return@withContext SaveResult.Success(resourceFileId = "", savedTabId = null)
            }
            return@withContext LibrarySaveHelpers.moveIntoHomeAndAttach(
                context = context,
                folder = folder,
                resourceDao = resourceDao,
                savedTabDao = savedTabDao,
                cacheFile = tmp,
                suggestedFilename = filename,
                vaultId = vault.id,
                vaultName = LibraryFolderManager.resolveFolderName(vault),
                kind = kind,
                mimeType = type,
                webUrl = url
            )
        }
    }

    /**
     * Executes [req] on [httpClient] and returns the [Response], propagating
     * coroutine cancellation to [Call.cancel]. Two hooks are installed:
     *
     *   1. [suspendCancellableCoroutine.invokeOnCancellation] — fires while the
     *      continuation is suspended (i.e. before the response arrives), so a
     *      cancel during the connect/headers phase aborts the call.
     *   2. A parent-job [invokeOnCompletion] handler — outlives the suspend
     *      point and stays armed while the caller streams the response body.
     *      A cancel mid-body-read calls [Call.cancel], which closes the socket
     *      and surfaces an [IOException] from the body's [okio.Source]. On
     *      successful completion the handler still fires but the call is
     *      already done, so the second cancel is a no-op.
     */
    private suspend fun executeCancellable(req: Request): Response {
        val call = httpClient.newCall(req)
        currentCoroutineContext().job.invokeOnCompletion {
            try { call.cancel() } catch (_: Throwable) { /* best effort */ }
        }
        return suspendCancellableCoroutine { cont ->
            cont.invokeOnCancellation {
                try { call.cancel() } catch (_: Throwable) { /* best effort */ }
            }
            call.enqueue(object : Callback {
                override fun onResponse(call: Call, response: Response) {
                    cont.resume(response)
                }
                override fun onFailure(call: Call, e: IOException) {
                    if (cont.isActive) cont.resumeWithException(e)
                }
            })
        }
    }

    companion object {
        /**
         * Filename for a page-supplied image with no `Content-Disposition` to
         * lean on: the URL's last path segment, run through
         * [LibrarySaveHelpers.sanitizeDownloadFilename] so it can't carry a
         * separator or a parent-directory reference onto disk. An empty tail
         * keeps its original, image-specific fallback (a timestamped name)
         * rather than the sanitizer's generic "download".
         */
        @JvmStatic
        fun deriveImageFilename(url: String): String {
            val rawTail = url.substringAfterLast('/').substringBefore('?')
            return if (rawTail.isEmpty()) {
                "image-${System.currentTimeMillis()}"
            } else {
                LibrarySaveHelpers.sanitizeDownloadFilename(rawTail)
            }
        }

        /**
         * Extracts the filename from a `Content-Disposition` header.
         *
         * RFC 6266 §4.3 says clients MUST prefer the `filename*=` (RFC 5987
         * extended) form over plain `filename=` when both are present, since the
         * extended form carries the charset and percent-encoding needed to
         * round-trip non-ASCII names. The previous implementation matched both
         * with one regex but never percent-decoded, so a header like
         * `filename*=UTF-8''r%C3%A9sum%C3%A9.pdf` landed on disk as
         * `r%C3%A9sum%C3%A9.pdf`.
         *
         * Decoding uses our own [percentDecode] rather than
         * [java.net.URLDecoder]: URLDecoder treats `+` as a space
         * (form-encoding), which is wrong for RFC 5987 where literal `+` must
         * round-trip unchanged.
         */
        @JvmStatic
        fun parseFilename(contentDisposition: String?): String? {
            if (contentDisposition.isNullOrBlank()) return null

            Regex("""filename\*\s*=\s*([^;]+)""", RegexOption.IGNORE_CASE)
                .find(contentDisposition)
                ?.let { match ->
                    val raw = match.groupValues[1].trim().trim('"')
                    // RFC 5987: charset'language'percent-encoded-value
                    val parts = raw.split('\'', limit = 3)
                    if (parts.size == 3) {
                        val charset = parts[0].ifBlank { "UTF-8" }
                        val encoded = parts[2]
                        val decoded = runCatching { percentDecode(encoded, charset) }.getOrNull()
                        if (!decoded.isNullOrEmpty()) return decoded
                    }
                    // Malformed extended form — fall through to plain filename below.
                }

            return Regex("""filename\s*=\s*"?([^";]+)"?""", RegexOption.IGNORE_CASE)
                .find(contentDisposition)
                ?.groupValues?.getOrNull(1)
                ?.trim()
                ?.ifEmpty { null }
        }

        /**
         * Decode RFC 3986 percent-encoded byte sequences in [s] into a string
         * under [charset]. Only `%XX` triples and ASCII pass through; `+` is
         * preserved verbatim (unlike form-encoding). Invalid `%XX` triples are
         * passed through as the literal `%`.
         */
        private fun percentDecode(s: String, charset: String): String {
            val out = java.io.ByteArrayOutputStream(s.length)
            var i = 0
            while (i < s.length) {
                val c = s[i]
                if (c == '%' && i + 2 < s.length) {
                    val hi = Character.digit(s[i + 1], 16)
                    val lo = Character.digit(s[i + 2], 16)
                    if (hi >= 0 && lo >= 0) {
                        out.write((hi shl 4) or lo)
                        i += 3
                        continue
                    }
                }
                out.write(c.code)
                i++
            }
            return out.toString(charset)
        }
    }
}

/**
 * Browser-like UA sent on image/file fetches. CDNs (Cloudflare/Akamai) classify
 * `okhttp/...` UAs as bots and block them, so we must look like a real browser.
 */
private const val BROWSER_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

/** Image header info returned by [BrowserSaveCoordinator.fetchImageMetadata]. */
data class ImageDimensions(val width: Int, val height: Int, val mimeType: String?)
