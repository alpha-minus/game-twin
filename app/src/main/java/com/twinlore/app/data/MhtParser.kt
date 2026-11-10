package com.twinlore.app.data

import android.util.Base64
import android.util.Log
import java.io.BufferedReader
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.FileReader
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Parses MHT (MIME HTML Archive) files and extracts the main HTML content.
 *
 * MHT files are MIME multipart/related documents. Chromium WebView renders
 * them in a sandboxed frame that blocks all JavaScript (including
 * evaluateJavascript), so we can't use loadUrl() directly. Instead, this
 * parser extracts the HTML part so the content can be loaded via
 * loadDataWithBaseURL() where JS works normally.
 *
 * Uses streaming I/O to handle large MHT files (Steam guides can be 200MB+)
 * without loading the entire file into memory. Small resources (CSS, icons)
 * are inlined as data: URIs. Large resources (images) are extracted to a
 * cache directory and served via shouldInterceptRequest.
 */
@Singleton
class MhtParser @Inject constructor() {

    companion object {
        private const val TAG = "MhtParser"
        /** All resources go to disk — never inline as data: URIs.
         *  Inlining inflates the HTML string (base64 ~1.33x per resource) and the
         *  String.replace() loop runs N times on the growing string, causing 15+ second
         *  save times on pages with 200+ resources (e.g. GitHub pages).
         *  Resources are served from disk via WebView shouldInterceptRequest. */
        private const val MAX_INLINE_BYTES = 0
        private const val MAX_TOTAL_INLINE_BYTES = 0L
    }

    data class ParseResult(
        val html: String,
        val baseUrl: String?,
        /** Content-Location URL → local relative path (for shouldInterceptRequest) */
        val manifest: Map<String, String>,
        /** Absolute path to the directory containing extracted assets */
        val assetDir: String?
    )

    /**
     * Parse an MHT file and return the main HTML with resources either inlined
     * or extracted to [cacheDir] for serving via shouldInterceptRequest.
     */
    fun parse(mhtFile: File, cacheDir: File): ParseResult? {
        if (!mhtFile.exists()) return null
        try {
            return streamParse(mhtFile, cacheDir)
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "OOM parsing MHT (${mhtFile.length()} bytes), falling back to HTML-only extraction", e)
            return tryExtractHtmlOnly(mhtFile)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing MHT", e)
            return tryExtractHtmlOnly(mhtFile)
        }
    }

    /**
     * Extract the original URL from an MHT file without full parsing.
     * Reads only the preamble + first MIME part headers (typically <1KB).
     */
    fun extractOriginalUrl(mhtFile: File): String? {
        if (!mhtFile.exists()) return null
        try {
            BufferedReader(FileReader(mhtFile, Charsets.UTF_8), 8 * 1024).use { reader ->
                val preambleHeaders = readHeaders(reader)
                val contentType = preambleHeaders["content-type"] ?: ""
                val boundaryMatch = Regex("""boundary="?([^"\r\n;]+)"?""").find(contentType)
                val boundary = boundaryMatch?.groupValues?.get(1)?.trim() ?: return null

                // Skip to first boundary
                var line: String? = reader.readLine()
                while (line != null && !line.startsWith("--$boundary")) {
                    line = reader.readLine()
                }
                if (line == null) return null

                // Read the first part's headers — this is the HTML part with Content-Location
                val partHeaders = readHeaders(reader)
                val url = partHeaders["content-location"]
                return url?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to extract URL from MHT", e)
            return null
        }
    }

    private fun streamParse(mhtFile: File, cacheDir: File): ParseResult? {
        val assetDir = File(cacheDir, "mht_assets")
        assetDir.deleteRecursively()
        assetDir.mkdirs()

        val reader = BufferedReader(FileReader(mhtFile, Charsets.UTF_8), 64 * 1024)
        reader.use {
            val preambleHeaders = readHeaders(reader)
            val contentType = preambleHeaders["content-type"] ?: ""
            val boundaryMatch = Regex("""boundary="?([^"\r\n;]+)"?""").find(contentType)
            val boundary = boundaryMatch?.groupValues?.get(1)?.trim()

            if (boundary == null) {
                Log.w(TAG, "No MIME boundary found in MHT, treating as plain HTML")
                val html = mhtFile.readText(Charsets.UTF_8)
                return ParseResult(html = html, baseUrl = null, manifest = emptyMap(), assetDir = null)
            }

            var mainHtml: String? = null
            var baseUrl: String? = null
            val inlineMap = mutableMapOf<String, String>()
            val fileManifest = mutableMapOf<String, String>()
            // Chrome's saveWebArchive extracts every page <style> block into a separate
            // MIME part with a `cid:` Content-Location and rewrites the original tag to
            // <link rel="stylesheet" href="cid:<uuid>@mhtml.blink">. Those cid: URLs
            // only resolve when the WebView opens the .mht via loadUrl("file://…mht"),
            // not under our loadDataWithBaseURL flow (the cid: scheme isn't fetchable
            // and shouldInterceptRequest never fires for it). We collect the cid CSS
            // bodies here and splice them back into the HTML as <style> blocks below.
            val cidCssMap = mutableMapOf<String, String>()
            var totalInlinedBytes = 0L
            var extractedFileIndex = 0

            // Skip to first boundary
            var line: String? = reader.readLine()
            while (line != null && !line.startsWith("--$boundary")) {
                line = reader.readLine()
            }

            // Process each MIME part
            while (line != null) {
                if (line == "--$boundary--") break

                val partHeaders = readHeaders(reader)
                if (partHeaders.isEmpty()) {
                    line = reader.readLine()
                    continue
                }

                val partContentType = partHeaders["content-type"] ?: "application/octet-stream"
                val contentLocation = partHeaders["content-location"]
                val transferEncoding = (partHeaders["content-transfer-encoding"] ?: "7bit").lowercase()
                val isHtml = partContentType.contains("text/html", ignoreCase = true)
                val isBase64 = transferEncoding == "base64"
                val mimeType = partContentType.substringBefore(';').trim()
                val extension = extensionForMime(mimeType)

                val isCidCss = contentLocation != null &&
                    contentLocation.startsWith("cid:", ignoreCase = true) &&
                    mimeType.equals("text/css", ignoreCase = true)

                if (isHtml && mainHtml == null) {
                    // HTML part: always accumulate in memory
                    val bodyBuilder = StringBuilder()
                    line = reader.readLine()
                    while (line != null && !(line ?: "").startsWith("--$boundary")) {
                        bodyBuilder.appendLine(line)
                        line = reader.readLine()
                    }
                    mainHtml = decodeBody(bodyBuilder.toString().trimEnd(), transferEncoding)
                    baseUrl = contentLocation
                } else if (isCidCss) {
                    // cid: CSS — decode in memory and remember for the post-loop splice.
                    // CSS parts are small (the file we observed averages ~1.5KB each, 45KB
                    // total across 29 parts) so memory pressure is negligible. Never written
                    // to disk: the file would be unreachable anyway because cid: URLs aren't
                    // fetchable from loadDataWithBaseURL.
                    val bodyBuilder = StringBuilder()
                    line = reader.readLine()
                    while (line != null && !(line ?: "").startsWith("--$boundary")) {
                        bodyBuilder.appendLine(line)
                        line = reader.readLine()
                    }
                    cidCssMap[contentLocation!!] = decodeBody(bodyBuilder.toString().trimEnd(), transferEncoding)
                } else if (contentLocation != null) {
                    // Resource part: stream-decode to file, then decide inline vs disk
                    val fileName = "asset_${extractedFileIndex++}.$extension"
                    val outFile = File(assetDir, fileName)
                    var decodedSize = 0L

                    if (isBase64) {
                        // Stream-decode base64 line by line directly to file
                        val os = BufferedOutputStream(FileOutputStream(outFile), 64 * 1024)
                        try {
                            val chunkBuilder = StringBuilder(4096)
                            line = reader.readLine()
                            while (line != null && !(line ?: "").startsWith("--$boundary")) {
                                chunkBuilder.append((line ?: "").trim())
                                if (chunkBuilder.length >= 4096) {
                                    val usable = chunkBuilder.length - (chunkBuilder.length % 4)
                                    if (usable > 0) {
                                        val decoded = Base64.decode(chunkBuilder.substring(0, usable), Base64.DEFAULT)
                                        os.write(decoded)
                                        decodedSize += decoded.size
                                        chunkBuilder.delete(0, usable)
                                    }
                                }
                                line = reader.readLine()
                            }
                            if (chunkBuilder.isNotEmpty()) {
                                val decoded = Base64.decode(chunkBuilder.toString(), Base64.DEFAULT)
                                os.write(decoded)
                                decodedSize += decoded.size
                            }
                        } finally {
                            os.close()
                        }
                    } else {
                        // Non-base64 (quoted-printable, 7bit, 8bit): accumulate then write
                        val bodyBuilder = StringBuilder()
                        line = reader.readLine()
                        while (line != null && !(line ?: "").startsWith("--$boundary")) {
                            bodyBuilder.appendLine(line)
                            line = reader.readLine()
                        }
                        val bytes = decodeToBytes(bodyBuilder.toString().trimEnd(), transferEncoding)
                        outFile.writeBytes(bytes)
                        decodedSize = bytes.size.toLong()
                    }

                    // Decide: inline small resources, keep large ones as files
                    if (decodedSize <= MAX_INLINE_BYTES && totalInlinedBytes + decodedSize <= MAX_TOTAL_INLINE_BYTES) {
                        try {
                            val bytes = outFile.readBytes()
                            val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                            inlineMap[contentLocation] = "data:$mimeType;base64,$b64"
                            totalInlinedBytes += bytes.size
                            outFile.delete()
                        } catch (e: Exception) {
                            fileManifest[contentLocation] = fileName
                            Log.w(TAG, "Failed to inline, using file: $contentLocation", e)
                        }
                    } else {
                        fileManifest[contentLocation] = fileName
                    }
                } else {
                    // No content-location — skip this part
                    line = reader.readLine()
                    while (line != null && !(line ?: "").startsWith("--$boundary")) {
                        line = reader.readLine()
                    }
                }
            }

            if (mainHtml == null) {
                Log.w(TAG, "No HTML part found in MHT")
                return null
            }

            // Replace inline resource URLs in the HTML
            var result: String = mainHtml!!

            // Splice cid: CSS bodies back into the HTML as <style> blocks, replacing the
            // <link rel="stylesheet" href="cid:…@mhtml.blink"> tags Chrome's saveWebArchive
            // emitted. Without this, ~30 of the page's inline stylesheets silently fail to
            // load under loadDataWithBaseURL (cid: isn't fetchable) and rules like
            // `.slideout-menu { display: none }` never apply — the off-canvas mobile menu
            // ends up rendering as a stack of bare links at the top of the page.
            //
            // Skip the splice entirely for text-FAQ pages (GameFAQs / NeoSeeker). They
            // bypass the WebView's normal CSS rendering anyway (GuideScreen runs them
            // through extractTextFaqFromFile + a custom monospace presentation), so the
            // inlined CSS is dead weight. Worse: prepending ~45KB of CSS to the head
            // pushes the `id="faqtext"` / `id="faqtxt"` / `game_faq_text` marker out of
            // the 16KB window that isTextFaqFile reads, which would silently demote
            // text FAQs into the rich-HTML render path. Detection here mirrors that
            // function's marker list.
            val looksLikeTextFaq = cidCssMap.isNotEmpty() && (
                result.contains("id=\"faqtext\"") ||
                result.contains("id='faqtext'") ||
                result.contains("id=\"faqtxt\"") ||
                result.contains("id='faqtxt'") ||
                result.contains("game_faq_text")
            )
            if (looksLikeTextFaq) {
                Log.d(TAG, "Skipping cid CSS splice for text-FAQ page (${cidCssMap.size} cid blocks left out)")
            }
            var cidHits = 0
            var cidMisses = 0
            for ((cidUrl, cssBody) in if (looksLikeTextFaq) emptyMap() else cidCssMap) {
                // Match the <link> tag carrying this exact href, regardless of attribute order.
                val pattern = Regex(
                    """<link\b[^>]*\bhref="${Regex.escape(cidUrl)}"[^>]*/?\s*>""",
                    RegexOption.IGNORE_CASE
                )
                // Guard against the rare CSS that contains the literal "</style" sequence
                // (e.g. inside a content: '' rule) which would prematurely close the block.
                val safeCss = cssBody.replace(Regex("</style", RegexOption.IGNORE_CASE), "<\\/style")
                val styleTag = "<style>$safeCss</style>"
                // Lambda form avoids Regex.replace's $-backreference interpretation, which
                // would corrupt any CSS body containing dollar signs.
                val replaced = pattern.replace(result) { styleTag }
                if (replaced !== result && replaced != result) {
                    cidHits++
                    result = replaced
                } else {
                    cidMisses++
                    Log.w(TAG, "cid CSS MISS (no <link> tag found): $cidUrl")
                }
            }
            if (cidCssMap.isNotEmpty()) {
                Log.d(TAG, "cid CSS spliced: $cidHits inlined, $cidMisses unmatched (of ${cidCssMap.size})")
            }

            var inlineHits = 0
            for ((url, dataUri) in inlineMap.entries.sortedByDescending { it.key.length }) {
                val before = result.length
                result = result.replace(url, dataUri)
                if (result.length != before) {
                    inlineHits++
                } else {
                    Log.w(TAG, "Inline MISS (URL not found in HTML): $url")
                }
            }

            val hasExtractedAssets = fileManifest.isNotEmpty()
            Log.d(TAG, "Parsed MHT: ${inlineMap.size} inlined ($inlineHits matched, ${inlineMap.size - inlineHits} missed, ${totalInlinedBytes / 1024}KB), " +
                "${fileManifest.size} extracted to disk, HTML ${result.length} chars")
            fileManifest.forEach { (url, file) ->
                Log.d(TAG, "  disk: $url -> $file")
            }

            return ParseResult(
                html = result,
                baseUrl = baseUrl,
                manifest = fileManifest,
                assetDir = if (hasExtractedAssets) assetDir.absolutePath else null
            )
        }
    }

    /** OOM fallback: extract only the HTML part, no resources. Text content still renders. */
    private fun tryExtractHtmlOnly(mhtFile: File): ParseResult? {
        try {
            val reader = BufferedReader(FileReader(mhtFile, Charsets.UTF_8), 64 * 1024)
            reader.use {
                val preambleHeaders = readHeaders(reader)
                val contentType = preambleHeaders["content-type"] ?: ""
                val boundaryMatch = Regex("""boundary="?([^"\r\n;]+)"?""").find(contentType)
                val boundary = boundaryMatch?.groupValues?.get(1)?.trim() ?: return null

                var line: String? = reader.readLine()
                while (line != null && !line.startsWith("--$boundary")) {
                    line = reader.readLine()
                }

                while (line != null) {
                    if (line == "--$boundary--") break
                    val partHeaders = readHeaders(reader)
                    val partContentType = partHeaders["content-type"] ?: ""
                    val transferEncoding = (partHeaders["content-transfer-encoding"] ?: "7bit").lowercase()

                    val bodyBuilder = StringBuilder()
                    line = reader.readLine()
                    while (line != null && !line.startsWith("--$boundary")) {
                        bodyBuilder.appendLine(line)
                        line = reader.readLine()
                    }

                    if (partContentType.contains("text/html", ignoreCase = true)) {
                        val html = decodeBody(bodyBuilder.toString().trimEnd(), transferEncoding)
                        Log.d(TAG, "Fallback HTML-only extraction: ${html.length} chars")
                        return ParseResult(
                            html = html,
                            baseUrl = partHeaders["content-location"],
                            manifest = emptyMap(),
                            assetDir = null
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fallback extraction also failed", e)
        }
        return null
    }

    private fun readHeaders(reader: BufferedReader): Map<String, String> {
        val headers = mutableMapOf<String, String>()
        var currentKey = ""
        var line = reader.readLine()
        while (line != null && line.isNotEmpty()) {
            if (line.startsWith(" ") || line.startsWith("\t")) {
                headers[currentKey] = (headers[currentKey] ?: "") + " " + line.trim()
            } else {
                val colonIdx = line.indexOf(':')
                if (colonIdx > 0) {
                    currentKey = line.substring(0, colonIdx).trim().lowercase()
                    headers[currentKey] = line.substring(colonIdx + 1).trim()
                }
            }
            line = reader.readLine()
        }
        return headers
    }

    private fun decodeBody(body: String, encoding: String): String = when (encoding) {
        "base64" -> String(Base64.decode(body.replace(Regex("\\s"), ""), Base64.DEFAULT), Charsets.UTF_8)
        "quoted-printable" -> String(decodeQuotedPrintableBytes(body), Charsets.UTF_8)
        else -> body
    }

    private fun decodeToBytes(body: String, encoding: String): ByteArray = when (encoding) {
        "base64" -> Base64.decode(body.replace(Regex("\\s"), ""), Base64.DEFAULT)
        "quoted-printable" -> decodeQuotedPrintableBytes(body)
        else -> body.toByteArray(Charsets.UTF_8)
    }

    private fun decodeQuotedPrintableBytes(input: String): ByteArray {
        val out = mutableListOf<Byte>()
        var i = 0
        val chars = input.replace("=\r\n", "").replace("=\n", "")
        while (i < chars.length) {
            if (chars[i] == '=' && i + 2 < chars.length) {
                try {
                    out.add(chars.substring(i + 1, i + 3).toInt(16).toByte())
                    i += 3
                    continue
                } catch (_: NumberFormatException) { }
            }
            out.add(chars[i].code.toByte())
            i++
        }
        return out.toByteArray()
    }

    private fun extensionForMime(mimeType: String): String = when {
        mimeType.contains("jpeg") || mimeType.contains("jpg") -> "jpg"
        mimeType.contains("png") -> "png"
        mimeType.contains("gif") -> "gif"
        mimeType.contains("webp") -> "webp"
        mimeType.contains("svg") -> "svg"
        mimeType.contains("css") -> "css"
        mimeType.contains("javascript") -> "js"
        mimeType.contains("html") -> "html"
        else -> "bin"
    }
}
