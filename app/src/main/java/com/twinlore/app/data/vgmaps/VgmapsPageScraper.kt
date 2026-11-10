package com.twinlore.app.data.vgmaps

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fetches a vgmaps.com per-system page and extracts the map list for one
 * game (identified by its anchor). Stateless except for the OkHttpClient
 * dependency.
 *
 * Strategy:
 *   1. GET https://www.vgmaps.com/Atlas/{system}/index.htm
 *   2. Find <a name="{anchor}">.
 *   3. Walk up to the enclosing <table> (vgmaps convention: one game per
 *      table row block). Fall back to "siblings between this anchor and the
 *      next anchor" if no table ancestor.
 *   4. Inside that scope, collect <a href> matching
 *      `Atlas/{system}/...png|jpg|gif`. Dedupe by URL.
 *   5. For each hit, derive a caption: trimmed link text, or if blank or
 *      bracketed-digit, URL-basename-without-extension, CamelCase split.
 */
@Singleton
class VgmapsPageScraper @Inject constructor(
    private val httpClient: OkHttpClient,
    private val sourceIconCache: com.twinlore.app.data.browser.SourceIconCache,
) {
    /**
     * Harvests the vgmaps.com favicon once, piggybacking on a picker session the user has
     * already started. The picker never goes through the WebView, so the browser's
     * onReceivedIcon harvest can never fire for this host and its menu row stayed a
     * monogram no matter how much the user browsed or downloaded (user report). Same
     * privacy footing as every other harvested icon: one extra request to the host the
     * user is actively fetching pages from, never a third party.
     */
    private suspend fun harvestFaviconIfMissing() = withContext(Dispatchers.IO) {
        if (sourceIconCache.fileFor("vgmaps.com") != null) return@withContext
        runCatching {
            val req = Request.Builder()
                .url("https://www.vgmaps.com/favicon.ico")
                .header("User-Agent", USER_AGENT)
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use
                val bytes = resp.body?.bytes() ?: return@use
                sourceIconCache.storeAnyFormat("https://www.vgmaps.com/", bytes)
            }
        }
    }
    /**
     * Fetches and parses the per-system page for [entry], returning the full
     * game details (maps and contributor credits). Errors (network, non-2xx,
     * parse failure) come back as [Result.failure] with a short human-readable
     * message.
     */
    suspend fun fetchGameDetails(entry: VgmapsEntry): Result<VgmapsGameDetails> = withContext(Dispatchers.IO) {
        harvestFaviconIfMissing()
        val pageUrl = "https://www.vgmaps.com/Atlas/${entry.system}/index.htm"
        val req = Request.Builder()
            .url(pageUrl)
            .header("User-Agent", USER_AGENT)
            .build()
        try {
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(IllegalStateException("HTTP ${resp.code} from $pageUrl"))
                }
                val body = resp.body?.string()
                    ?: return@withContext Result.failure(IllegalStateException("Empty body from $pageUrl"))
                Result.success(parseGameDetailsFromHtml(body, entry.system, entry.anchor))
            }
        } catch (ce: kotlinx.coroutines.CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    companion object {
        const val USER_AGENT = "GameTwin/vgmaps-import (+https://github.com/alpha-minus/game-twin)"

        // vgmaps' sister poster-store domains. Links to these are "Order
        // poster maps of this game from <Site>" promos, never submitters.
        private val SISTER_STORE_HOST_REGEX = Regex(
            """\b(?:nes|snes|gb|gba|gbc|n64|nds|psx)maps\.com\b""",
        )

        // Imperative-verb openers on a link's text are dead giveaways that the
        // link is a call-to-action ("Order poster maps..."), not a credit.
        private val CALL_TO_ACTION_PREFIX_REGEX = Regex(
            """^(?:Order|Buy|Get|Visit|See|Subscribe|Click|Download|Donate|Support|Follow|View)\b""",
            RegexOption.IGNORE_CASE,
        )

        /**
         * Pure HTML to game details (maps + credits). Public so tests can exercise
         * it without a network.
         */
        fun parseGameDetailsFromHtml(html: String, system: String, anchor: String): VgmapsGameDetails {
            val doc = Jsoup.parse(html, "https://www.vgmaps.com/Atlas/$system/")
            // Anchors in the bundled catalog are word-character identifiers, so direct
            // string interpolation is safe today. If catalog drift ever introduces CSS
            // metacharacters, switch to `doc.select("a").firstOrNull { it.attr("name") == anchor }`.
            val anchorEl = doc.selectFirst("a[name=$anchor]") ?: return VgmapsGameDetails(emptyList(), emptyList())
            val scope: Element = anchorEl.parents().firstOrNull { it.tagName() == "table" }
                ?: collectSiblingsUntilNextAnchor(anchorEl)
                ?: return VgmapsGameDetails(emptyList(), emptyList())

            val hrefRegex = Regex(
                "(?i)^https?://www\\.vgmaps\\.com/Atlas/${Regex.escape(system)}/.+\\.(png|jpe?g|gif)$"
            )
            val seen = LinkedHashMap<String, VgmapsMap>()
            for (a in scope.select("a[href]")) {
                val href = a.absUrl("href").ifEmpty { a.attr("href") }
                if (!hrefRegex.matches(href)) continue
                if (seen.containsKey(href)) continue
                val raw = a.text().trim()
                val caption = when {
                    raw.isNotEmpty() && !raw.matches(Regex("""^\[?\d+\]?$""")) -> raw
                    else -> deriveCaptionFromUrl(href)
                }
                seen[href] = VgmapsMap(caption = caption, imageUrl = href)
            }

            // Credits: combination of mailto:, profile-link, and parenthetical-text extraction.
            val credits = LinkedHashMap<String, VgmapsCredit>()

            fun addCredit(name: String, email: String?) {
                val cleanName = stripWrappingBrackets(name.trim().let(::normalizeEmail)).take(64)
                if (cleanName.isBlank()) return
                if (cleanName.contains("http", ignoreCase = true)) return
                if (cleanName.all { it.isDigit() }) return
                val cleanEmail = email?.trim()?.let(::normalizeEmail)?.takeIf { it.isNotBlank() }
                val key = "$cleanName|${cleanEmail ?: ""}"
                if (key !in credits) credits[key] = VgmapsCredit(name = cleanName, email = cleanEmail)
            }

            // 1) mailto: links — kept exactly as before, gives email.
            for (a in scope.select("a[href^=mailto:]")) {
                val rawHref = a.attr("href")
                val email = rawHref.removePrefix("mailto:")
                    .substringBefore("?")
                    .trim()
                    .let(::normalizeEmail)
                    .ifBlank { null }
                val rawName = a.text().trim().let(::normalizeEmail)
                val name = when {
                    rawName.isNotEmpty() -> rawName
                    email != null -> email
                    else -> continue
                }
                addCredit(name, email)
            }

            // 2) Non-image, non-anchor <a> links — likely profile/author links.
            for (a in scope.select("a[href]")) {
                val href = a.absUrl("href").ifEmpty { a.attr("href") }
                if (href.startsWith("mailto:")) continue
                val lower = href.lowercase()
                if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
                    lower.endsWith(".gif") || lower.endsWith(".bmp")) continue
                if (href.startsWith("#") || href.isEmpty()) continue
                // Skip anchors that point into vgmaps Atlas image/navigation pages.
                if (lower.contains("/atlas/")) continue
                // Skip vgmaps news/feature pages (e.g. "Maps Of The Month") — those
                // are the bolded cell title, not a submitter name.
                if (lower.contains("/newsarchives/")) continue
                // Skip vgmaps sister poster-store domains (nesmaps.com /
                // snesmaps.com / gbamaps.com / n64maps.com / gbcmaps.com).
                // Their links are "Order poster maps of this game from ..."
                // promotions, never submitter credits.
                if (SISTER_STORE_HOST_REGEX.containsMatchIn(lower)) continue
                // Section headers are always wrapped in <b>; contributor links are not.
                // Belt-and-suspenders alongside the /newsarchives/ filter above.
                if (a.parents().any { it.tagName() == "b" }) continue
                val text = a.text().trim()
                if (text.isEmpty()) continue
                // Submitter names are short. Anything sentence-shaped (>4 words
                // or >40 chars after bracket-stripping) is promo copy, not a name.
                val unbracketed = stripWrappingBrackets(text)
                if (unbracketed.length > 40) continue
                if (unbracketed.split(Regex("\\s+")).size > 4) continue
                // Call-to-action prefixes are dead giveaways for promo links.
                if (CALL_TO_ACTION_PREFIX_REGEX.containsMatchIn(unbracketed)) continue
                addCredit(text, null)
            }

            // 3) Parenthetical-text extraction from credit-flavored rows.
            val creditMarkers = listOf("Map by", "Maps by", "Submitted by", "thanks to", "Maps Of The Month")
            for (td in scope.select("td")) {
                val text = td.text()
                val hasMarker = creditMarkers.any { text.contains(it, ignoreCase = true) }
                if (!hasMarker) continue
                // Pull whatever is in parentheses; commas/semicolons split names.
                val parenRegex = Regex("""\(([^)]+)\)""")
                for (match in parenRegex.findAll(text)) {
                    val group = match.groupValues[1]
                    for (raw in group.split(',', ';')) {
                        val candidate = raw
                            .replace(Regex("""\s+with\s+thanks\s+to\s+""", RegexOption.IGNORE_CASE), "")
                            .replace(Regex("""^\s*and\s+""", RegexOption.IGNORE_CASE), "")
                            .trim()
                        if (candidate.isNotBlank()) addCredit(candidate, null)
                    }
                }
                // Also extract bare "Map by X" / "Maps by X" patterns outside parens.
                val byRegex = Regex(
                    """\b(?:Map(?:s)? by|Submitted by)\s+([A-Za-z0-9_][^,.()]{0,62})""",
                    RegexOption.IGNORE_CASE,
                )
                for (match in byRegex.findAll(text)) {
                    addCredit(match.groupValues[1].trim(), null)
                }
            }

            return VgmapsGameDetails(maps = seen.values.toList(), credits = credits.values.toList())
        }

        /**
         * Back-compat shim: existing tests call this directly. Delegates to
         * [parseGameDetailsFromHtml] and returns only the maps.
         */
        fun parseMapsFromHtml(html: String, system: String, anchor: String): List<VgmapsMap> =
            parseGameDetailsFromHtml(html, system, anchor).maps

        /**
         * Synthesizes a parent element containing every sibling between the anchor
         * and the next `<a name="...">` (any name). Used when the anchor isn't
         * wrapped in a <table>. Returns null if there's nothing to collect.
         */
        private fun collectSiblingsUntilNextAnchor(anchor: Element): Element? {
            val container = anchor.ownerDocument()?.createElement("div") ?: return null
            var node: org.jsoup.nodes.Node? = anchor
            while (node != null) {
                if (node !== anchor) {
                    if (node is Element && node.tagName() == "a" && node.hasAttr("name")) break
                    container.appendChild(node.clone())
                }
                node = node.nextSibling()
            }
            return if (container.children().isEmpty() && container.select("a[href]").isEmpty()) null
            else container
        }

        private fun deriveCaptionFromUrl(url: String): String {
            val basename = url.substringAfterLast('/')
                .substringBeforeLast('.')
                .ifEmpty { return "Untitled" }
            // Replace separators with spaces, then split CamelCase.
            val spaced = basename.replace(Regex("[-_]+"), " ")
            val camelSplit = spaced.replace(Regex("(?<=[a-z])(?=[A-Z])"), " ")
            return camelSplit.replace(Regex("\\s+"), " ").trim().ifEmpty { "Untitled" }
        }

        /**
         * Strips a single pair of wrapping `()` or `[]` from a credit name.
         * vgmaps sometimes styles a header-row submitter link with parens around
         * the displayed name, e.g. `<a>(Jane Doe)</a>`. The parens are visual,
         * not part of the name. Idempotent — does nothing if the string isn't
         * fully wrapped.
         */
        internal fun stripWrappingBrackets(raw: String): String {
            val s = raw.trim()
            if (s.length < 2) return s
            val first = s.first()
            val last = s.last()
            val wrapped = (first == '(' && last == ')') || (first == '[' && last == ']')
            return if (wrapped) s.substring(1, s.length - 1).trim() else s
        }

        /**
         * Normalizes common email-obfuscation patterns vgmaps.com uses in mailto:
         * link hrefs (and link text). Reverses `(at)` and `[at]` back to `@`. Does
         * not attempt heuristic " at " replacement since that creates false positives
         * on names like "Jacob At Home".
         */
        internal fun normalizeEmail(raw: String): String =
            raw.replace(" (at) ", "@")
                .replace("(at)", "@")
                .replace(" [at] ", "@")
                .replace("[at]", "@")
                .replace(" (dot) ", ".")
                .replace("(dot)", ".")
    }
}
