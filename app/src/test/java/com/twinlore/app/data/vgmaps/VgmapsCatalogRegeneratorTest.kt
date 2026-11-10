package com.twinlore.app.data.vgmaps

import org.jsoup.Jsoup
import org.junit.Ignore
import org.junit.Test
import java.io.File

/**
 * Regenerator for `app/src/main/assets/vgmaps_catalog.json`. NOT a CI test;
 * this is `@Ignore`d by default and run manually when the bundled catalog
 * should be refreshed.
 *
 * To refresh:
 *   1. Save the directory page from vgmaps.com as MHT (desktop Chrome,
 *      File > Save Page As > Webpage, Single File).
 *   2. Save it as `app/catalog-sources/vgmaps/directory.mht` (git-ignored;
 *      the test fixture of the same name is a dummy page).
 *   3. Comment out the @Ignore below.
 *   4. Run this single test from your IDE or:
 *      ./gradlew :app:testDebugUnitTest --tests "*VgmapsCatalogRegeneratorTest*"
 *   5. Verify `app/src/main/assets/vgmaps_catalog.json` was updated.
 *   6. Re-add @Ignore.
 *   7. Commit the new JSON (not the capture).
 */
@Ignore("Regenerator: un-ignore to refresh the catalog, then re-ignore before commit")
class VgmapsCatalogRegeneratorTest {

    @Test fun regenerate() {
        val mhtFile = File("catalog-sources/vgmaps/directory.mht")
        require(mhtFile.exists()) { "Missing source MHT: $mhtFile" }

        val html = extractHtmlFromMht(mhtFile.readBytes())
        val doc = Jsoup.parse(html, "https://www.vgmaps.com/")

        // Directory links look like:
        //   <a href="https://www.vgmaps.com/Atlas/PSX/index.htm#Alundra">Alundra</a>
        // Filter to that exact shape, dedupe on (title, system, anchor).
        val pattern = Regex("""^https?://www\.vgmaps\.com/Atlas/([^/]+)/index\.htm#(.+)$""")
        val seen = LinkedHashSet<Triple<String, String, String>>()
        for (a in doc.select("a[href]")) {
            val href = a.attr("href")
            val m = pattern.matchEntire(href) ?: continue
            val system = m.groupValues[1]
            val anchor = m.groupValues[2].trim()
            if (anchor.isEmpty()) continue
            // Skip section anchors like #A / #B / #BM0 (jump-to-letter), not games.
            if (anchor.length <= 3 && anchor.matches(Regex("""[A-Z]|BM\d|\d"""))) continue
            val title = a.text().trim()
            if (title.isEmpty()) continue
            seen += Triple(title, system, anchor)
        }

        // Apply the same orphan-title filter + (system, anchor) dedupe that
        // VgmapsCatalog.cleanAndDedupe applies at runtime, so the bundled JSON
        // doesn't ship the ~30% bloat that scraping artifacts add.
        val asEntries = seen.map { (t, s, a) -> VgmapsEntry(title = t, system = s, anchor = a) }
        val cleaned = VgmapsCatalog.cleanAndDedupe(asEntries)
        // Stable order: alphabetical by title (case-insensitive), then system.
        val sorted = cleaned
            .map { Triple(it.title, it.system, it.anchor) }
            .sortedWith(compareBy({ it.first.lowercase() }, { it.second }))
        val sb = StringBuilder()
        sb.append("[\n")
        sorted.forEachIndexed { i, (t, s, a) ->
            sb.append("""  {"t":${escapeJson(t)},"s":${escapeJson(s)},"a":${escapeJson(a)}}""")
            if (i < sorted.size - 1) sb.append(",")
            sb.append("\n")
        }
        sb.append("]\n")

        val out = File("src/main/assets/vgmaps_catalog.json")
        out.parentFile.mkdirs()
        out.writeText(sb.toString())
        println("Wrote ${sorted.size} entries to ${out.absolutePath}")
    }

    /**
     * Pulls the `text/html` part body out of a multipart/related MHT file.
     * MHT files Chrome produces use binary encoding so the body is verbatim
     * UTF-8/Windows-1252 between the part-headers-blank-line and the next
     * `--<boundary>` separator.
     */
    private fun extractHtmlFromMht(bytes: ByteArray): String {
        // Use ISO-8859-1 to round-trip arbitrary bytes losslessly through String.
        val text = String(bytes, Charsets.ISO_8859_1)
        // Boundary lives on the top-level Content-Type header.
        val boundary = Regex("""boundary="([^"]+)"""").find(text)?.groupValues?.get(1)
            ?: error("No multipart boundary in MHT")
        val sep = "--$boundary"
        // Split into parts on the separator. First chunk is the preamble, last
        // chunk after the closing "--$boundary--" is trash.
        val chunks = text.split(sep)
        for (chunk in chunks) {
            val headersEnd = chunk.indexOf("\n\n").let {
                if (it >= 0) it else chunk.indexOf("\r\n\r\n")
            }
            if (headersEnd < 0) continue
            val headers = chunk.substring(0, headersEnd).lowercase()
            if (!Regex("content-type:\\s*text/html").containsMatchIn(headers)) continue
            val bodyStart = chunk.indexOf("\n\n", headersEnd).let { if (it >= 0) it + 2 else chunk.indexOf("\r\n\r\n", headersEnd) + 4 }
            val bodyLatin1 = chunk.substring(bodyStart).trim()
            // Re-decode the body as UTF-8 (Chrome emits text/html as UTF-8 by default; we
            // read the MHT as ISO-8859-1 only to round-trip arbitrary bytes through String).
            return String(bodyLatin1.toByteArray(Charsets.ISO_8859_1), Charsets.UTF_8)
        }
        error("No text/html part found in MHT")
    }

    private fun escapeJson(s: String): String {
        val sb = StringBuilder().append('"')
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> if (c < ' ') sb.append("\\u%04x".format(c.code)) else sb.append(c)
            }
        }
        sb.append('"')
        return sb.toString()
    }
}
