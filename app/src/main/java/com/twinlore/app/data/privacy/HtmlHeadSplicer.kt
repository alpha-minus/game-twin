package com.twinlore.app.data.privacy

import java.nio.charset.Charset
import java.util.Locale

internal object HtmlHeadSplicer {

    /**
     * Locates the first <head ...> opening tag (case-insensitive, with an identifier-boundary
     * check so <header> and <heading> are not matched, and skipping <head tokens inside HTML
     * comment spans) and returns a new byte array with `injection` inserted immediately after
     * the closing `>` of that tag.
     *
     * Returns null when no <head> is found. Bytes after the splice point are preserved
     * exactly when the input round-trips losslessly through `charset`. Byte sequences that
     * are invalid under the charset may be substituted by the JVM's standard decoder
     * (typically with U+FFFD); this is not a concern for well-formed HTML responses but
     * is worth knowing if you feed in mojibake.
     */
    fun spliceAfterHead(html: ByteArray, charset: Charset, injection: String): ByteArray? {
        val text = String(html, charset)
        val lower = text.lowercase(Locale.ROOT)
        var searchFrom = 0
        while (true) {
            val idx = lower.indexOf("<head", searchFrom)
            if (idx < 0) return null

            // Skip matches inside HTML comments. If the most recent <!-- before this match has
            // not been closed by --> before this match, we are inside a comment and must skip.
            val commentStart = lower.lastIndexOf("<!--", idx)
            if (commentStart >= 0) {
                val commentEnd = lower.indexOf("-->", commentStart)
                if (commentEnd < 0 || commentEnd > idx) {
                    searchFrom = if (commentEnd < 0) text.length else commentEnd + 3
                    continue
                }
            }

            val charAfter = lower.getOrNull(idx + 5)
            // Identifier boundary: the next char must be `>`, an HTML5 whitespace char, `/`,
            // or end of string. HTML5 whitespace is space, tab, LF, CR, FF.
            if (charAfter == null ||
                charAfter == '>' || charAfter == ' ' || charAfter == '\t' ||
                charAfter == '\n' || charAfter == '\r' || charAfter == '' ||
                charAfter == '/'
            ) {
                val closeIdx = text.indexOf('>', idx + 5)
                if (closeIdx < 0) return null
                val before = text.substring(0, closeIdx + 1)
                val after = text.substring(closeIdx + 1)
                return (before + injection + after).toByteArray(charset)
            }
            searchFrom = idx + 5
        }
    }
}
