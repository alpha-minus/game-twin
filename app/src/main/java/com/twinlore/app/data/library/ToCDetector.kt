package com.twinlore.app.data.library

import java.net.URI
import kotlin.coroutines.resume

/**
 * Raw link emitted by the JS-side extractor (`assets/toc_extract.js`) and
 * also constructed by tests. The extractor pre-filters to same-origin,
 * http(s), non-bare-fragment, text-bounded anchors before the data
 * reaches this side of the bridge.
 *
 * [standalone] is the DOM-semantic signal that the link dominates its
 * parent element's text content (ratio >= 0.65 on the JS side), or is
 * the first text-bearing anchor of a link-dense parent (multi-link ToC
 * rows like `<a>Energy Tanks</a> (<a>By Progression</a>)` — in `<li>`s
 * on desktop layouts, `<h3><span>`s on mobile — where variant links
 * dilute every anchor's own ratio). ToC items in
 * `<li><a>...</a></li>` are standalone; inline body anchor
 * references inside `<p>` / `<td>` sentences are not. The detector
 * drops `!standalone` links before pattern matching so the Y-cluster
 * heuristic operates on a cleaner set. Defaults to `true` so existing
 * test fixtures (and any future caller that doesn't pre-compute the
 * ratio) keep matching their original behavior.
 */
data class RawLink(
    val text: String,
    val href: String,
    val y: Int = -1,
    val standalone: Boolean = true,
)

/** One ToC entry. Multiple entries can share [savedHref] (anchor sub-entries).
 *
 * [synthetic] is true for entries discovered in pass 3 — same-path-different-query
 * URLs that appear only via inline body links (not as a standalone ToC anchor).
 * Synthetic entries persist alongside named chapters so bulk save covers the full
 * page span and in-group link nav stays offline, but they are filtered from the
 * displayed ToC and from the picker's "N chapters" subtitle so the user still
 * sees only the page's actual chapter labels.
 */
data class TocEntry(
    val label: String,
    val savedHref: String,
    val fragment: String?,
    val synthetic: Boolean = false
)

/**
 * Result of one chapter-detection pass. [entries] is the DOM-ordered
 * list of ToC entries including anchor sub-entries; empty means no
 * cluster was found and the "Save All Pages" button stays grey.
 * Use [entries].distinctBy { it.savedHref } to get unique save targets.
 */
data class DetectionResult(
    val pageUrl: String,
    val pageTitle: String,
    val entries: List<TocEntry>
)

/**
 * URL-pattern chapter detector. A link is a chapter under one of three
 * complementary patterns; the detector returns whichever pattern
 * yields more matches (tie order tail > parent > query, more-specific
 * signals winning when counts are equal):
 *
 *  - **Tail-contains**: link's path contains the current page's last
 *    1-2 path segments wrapped as `/<seg>/`. Catches strict subpaths
 *    (omegametroid `/walk/page-a`) AND the GameFAQs cross-game-id
 *    pattern where chapters share the FAQ-id tail but live under a
 *    different platform/game-id prefix.
 *
 *  - **Parent-prefix**: link's path starts with the current page's
 *    parent directory (all path segments except the last). Catches
 *    sibling-page guide sites: IGN wikis (depth 3:
 *    `/wikis/<game>/Walkthrough`) and content-bound depth-2 hubs
 *    (omegametroid `/metroid-zero-mission-walkthrough/zero-mission`).
 *    Always applies when the parent has multiple segments. At depth 2
 *    the parent is one segment; applies only if that segment looks
 *    slug-like (contains a hyphen OR length > 12) so generic
 *    categories like `/wikis/` or `/blog/` on a depth-2 page don't
 *    pull in every unrelated sibling on the site.
 *
 *  - **Same-path-different-query**: link's path equals the current
 *    page's path but its query differs (e.g. paginated FAQ guides
 *    with `?page=0`, `?page=1`, ...). Catches GameFAQs multi-page
 *    walkthroughs where each chapter in the sidebar points at
 *    `?page=N#anchor` on the current page itself. Fragment-strip
 *    dedup collapses multiple anchors on the same `?page=N` to one
 *    save target.
 *
 * All matches must share the current page's host and scheme. Self-
 * links are excluded. Fragment-only duplicates collapse to one entry
 * (kept entry's display text is the first occurrence). Query strings
 * stay distinct in the dedup key so paginated subpaths like `?page=1`
 * and `?page=2` survive.
 *
 * On a bare-host URL (the current page's path is empty after trim)
 * the cluster is empty: every same-origin link would otherwise
 * match, and offering "Save All Pages" on a homepage is not a useful
 * default.
 */
object ToCDetector {

    fun detect(
        links: List<RawLink>,
        pageUrl: String,
        pageTitle: String
    ): DetectionResult {
        val current = runCatching { URI(pageUrl) }.getOrNull()
            ?: return DetectionResult(pageUrl, pageTitle, emptyList())
        val host = current.host ?: return DetectionResult(pageUrl, pageTitle, emptyList())
        val scheme = current.scheme ?: return DetectionResult(pageUrl, pageTitle, emptyList())

        // Site-specific: fireemblemwod.com (Fire Emblem: Wars of Dragons). Each
        // game's English walkthrough is a set of chapter pages under
        // /fe<N>/<guide-dir>/ENG_...htm, linked from the game hub page — which
        // sits at the site root, so the generic tail/parent passes below can't
        // relate the hub to its chapters. Recognize those chapter links by their
        // path shape instead. Scoped to this host only; every other site keeps
        // the generic URL-pattern detection.
        if (host.equals("fireemblemwod.com", ignoreCase = true) ||
            host.equals("www.fireemblemwod.com", ignoreCase = true)
        ) {
            return detectFireEmblemWod(links, pageUrl, pageTitle, host, scheme)
        }

        val rawPath = current.rawPath.orEmpty().trimEnd('/')
        val currentQuery = current.rawQuery.orEmpty()
        if (rawPath.isEmpty()) return DetectionResult(pageUrl, pageTitle, emptyList())
        val segments = rawPath.trim('/').split('/').filter { it.isNotEmpty() }
        if (segments.isEmpty()) return DetectionResult(pageUrl, pageTitle, emptyList())

        val tailToken = "/${segments.takeLast(2).joinToString("/")}/"
        val parentSegments = segments.dropLast(1)
        val parentPrefix: String? = when {
            parentSegments.isEmpty() -> null
            parentSegments.size >= 2 -> "/${parentSegments.joinToString("/")}/"
            else -> {
                val sole = parentSegments[0]
                if (sole.contains('-') || sole.length > 12) "/$sole/" else null
            }
        }

        // Pass 1: identify chapter URLs using tail/parent/query (existing logic,
        // but only collecting savedHref strings — no display ordering yet).
        val tailUrls = mutableSetOf<String>()
        val parentUrls = mutableSetOf<String>()
        val queryUrls = mutableSetOf<String>()
        for (link in links) {
            if (isPaginationText(link.text)) continue
            if (!link.standalone) continue
            val u = runCatching { URI(link.href) }.getOrNull() ?: continue
            if (u.host != host) continue
            if ((u.scheme ?: continue) != scheme) continue
            val linkPath = u.rawPath.orEmpty()
            if (isWikiSpecialPath(linkPath)) continue
            val linkQuery = u.rawQuery.orEmpty()
            val isSamePath = linkPath.trimEnd('/') == rawPath
            if (isSamePath && linkQuery == currentQuery) continue
            val saved = link.href.substringBefore('#')
            if (linkPath.contains(tailToken)) tailUrls += saved
            if (parentPrefix != null &&
                linkPath.startsWith(parentPrefix) &&
                linkPath.length > parentPrefix.length
            ) parentUrls += saved
            if (isSamePath && linkQuery.isNotEmpty() && linkQuery != currentQuery) queryUrls += saved
        }
        val chapterUrls = listOf(tailUrls, parentUrls, queryUrls).maxByOrNull { it.size } ?: emptySet()

        // Cluster filter: pages with both a top-of-page ToC AND scattered
        // anchor links elsewhere in the body produce 30+ chapters when only
        // the top ~7 belong to the ToC cluster. Walk the chapter-link
        // occurrences sorted by absolute document Y; the top cluster is the
        // contiguous run starting from the topmost link where each
        // consecutive gap is <= max(5 * median, 250 px). The 250 px floor
        // covers ToCs with very tight per-row spacing (~15-20 px) where
        // 5x median would be tighter than a section break inside the ToC.
        //
        // No-ops (cluster = null, all chapter-link occurrences emit) when:
        //   - any chapter-link occurrence has y < 0 (old extractor cache or
        //     test fixtures using default y = -1),
        //   - fewer than 3 chapter-link occurrences exist (too few to
        //     compute a meaningful median),
        //   - the computed cluster degenerates to fewer than 3 links
        //     (better an oversaturated picker than an empty one).
        val chapterIndexed = mutableListOf<Triple<Int, RawLink, Int>>()
        var anyMissingY = false
        for ((idx, link) in links.withIndex()) {
            if (link.href.substringBefore('#') !in chapterUrls) continue
            if (link.y < 0) { anyMissingY = true; break }
            chapterIndexed += Triple(idx, link, link.y)
        }
        val cluster: Set<Int>? = if (anyMissingY || chapterIndexed.size < 3) {
            null
        } else {
            val sorted = chapterIndexed.sortedBy { it.third }
            val gaps = sorted.zipWithNext { a, b -> (b.third - a.third).coerceAtLeast(0) }
            val sortedGaps = gaps.sorted()
            val median = if (sortedGaps.isEmpty()) 0 else sortedGaps[sortedGaps.size / 2]
            val threshold = maxOf(5 * median, 250)
            val included = mutableListOf(sorted[0])
            for (i in 1 until sorted.size) {
                val gap = sorted[i].third - sorted[i - 1].third
                if (gap <= threshold) included += sorted[i] else break
            }
            if (included.size < 3) null else included.map { it.first }.toSet()
        }

        // Pass 2: DOM-ordered ToC. Every link whose stripped href is in the
        // chapter set emits one TocEntry. Anchor sub-entries land here too:
        // their stripped URL is the chapter URL, their fragment is preserved.
        val seen = mutableSetOf<Pair<String, String?>>()
        val entries = mutableListOf<TocEntry>()
        for ((index, link) in links.withIndex()) {
            if (isPaginationText(link.text)) continue
            if (!link.standalone) continue
            val u = runCatching { URI(link.href) }.getOrNull() ?: continue
            if (u.host != host) continue
            if ((u.scheme ?: continue) != scheme) continue
            if (isWikiSpecialPath(u.rawPath.orEmpty())) continue
            val saved = link.href.substringBefore('#')
            if (saved !in chapterUrls) continue
            if (cluster != null && index !in cluster) continue
            val fragment = link.href.substringAfter('#', missingDelimiterValue = "").let {
                if (it.isEmpty()) null else "#$it"
            }
            val key = saved to fragment
            if (seen.add(key)) {
                entries += TocEntry(label = link.text, savedHref = saved, fragment = fragment)
            }
        }

        // Pass 3: synthesize entries for same-path-different-query URLs that
        // appear only inline in body content. Pass 2's standalone filter drops
        // those links from the ToC display (correct: they're not chapters in
        // the page's own ToC). But the bulk save needs to fetch every distinct
        // ?page=N URL the guide references so cross-page link nav stays
        // offline. We rescan ALL links (standalone or not) for the
        // same-path-different-query shape and emit one synthetic entry per
        // new save target — same MHT file ends up on disk, just without a
        // named ToC anchor to land at. Only fires for the query-pattern case;
        // tail/parent are handled by pass 2's broader filter behavior.
        val existingSavedHrefs = entries.map { it.savedHref }.toSet()
        val syntheticUrls = linkedSetOf<String>()
        for (link in links) {
            if (isPaginationText(link.text)) continue
            val u = runCatching { URI(link.href) }.getOrNull() ?: continue
            if (u.host != host) continue
            if ((u.scheme ?: continue) != scheme) continue
            val linkPath = u.rawPath.orEmpty()
            if (isWikiSpecialPath(linkPath)) continue
            val linkQuery = u.rawQuery.orEmpty()
            val isSamePath = linkPath.trimEnd('/') == rawPath
            if (!isSamePath) continue
            if (linkQuery.isEmpty() || linkQuery == currentQuery) continue
            val saved = link.href.substringBefore('#')
            if (saved in existingSavedHrefs) continue
            syntheticUrls += saved
        }
        if (syntheticUrls.isNotEmpty()) {
            val syntheticEntries = syntheticUrls
                .map { url -> TocEntry(label = labelForSyntheticUrl(url), savedHref = url, fragment = null, synthetic = true) }
            entries += syntheticEntries
        }

        // Final pass: when entries are paginated (?page=N), sort by page
        // number so labeled and synthetic entries interleave in natural
        // reading order rather than labeled-then-synthetic. Stable sort:
        // entries without ?page=N keep their DOM-discovery order.
        val ordered = if (syntheticUrls.isNotEmpty()) {
            entries
                .withIndex()
                .sortedWith(
                    compareBy(
                        { syntheticPageNumber(it.value.savedHref) ?: Int.MAX_VALUE },
                        { it.index },
                    )
                )
                .map { it.value }
        } else {
            entries
        }

        android.util.Log.d(
            "ToCDetector",
            "detect pageUrl=$pageUrl rawLinks=${links.size} chapters=${chapterUrls.size} entries=${ordered.size} synthetic=${syntheticUrls.size} tailToken=$tailToken parentPrefix=$parentPrefix"
        )
        return DetectionResult(pageUrl, pageTitle, ordered)
    }

    /**
     * IGN structured-sidebar chapter pass. IGN wiki walkthroughs render their
     * chapter list as `<button title="...">` elements with no `href` (React /
     * Next.js client-side nav), so the URL-pattern [detect] sees nothing. This
     * pass takes the sidebar button [labels] (DOM order) and recovers each
     * chapter's URL by an exact-text join to the page's content [anchors]
     * (`a[href]` text == label).
     *
     * The join is deliberately self-filtering: tool / header buttons ("Find in
     * guide", "Walkthrough", "Game Info", …) have no matching same-host content
     * anchor and drop out, so no hard-coded tool blocklist is needed. The pass
     * is slug-agnostic — it does NOT require the chapter URLs to share the
     * page's own slug, which is exactly why it works when the page is served
     * under an alias slug (`/wikis/persona-5/…`) while the nav links use the
     * canonical slug (`/wikis/shin-megami-tensei-persona-5/…`).
     *
     * A content anchor qualifies only when it is same-host + same-scheme, its
     * path is a `/wikis/<slug>/<page>` content page (>= 3 segments, first
     * segment `wikis`), it is not a wiki-special namespace, and it is not the
     * current page itself (self-link). Entries are emitted in sidebar order and
     * deduped by saved href (first label wins).
     *
     * Returns every resolved chapter; the caller applies the >= 3 threshold
     * before treating the result as authoritative.
     */
    fun detectIgnSidebar(
        labels: List<String>,
        anchors: List<RawLink>,
        pageUrl: String
    ): List<TocEntry> {
        val current = runCatching { URI(pageUrl) }.getOrNull() ?: return emptyList()
        val host = current.host ?: return emptyList()
        val scheme = current.scheme ?: return emptyList()

        // Build label-text -> content-anchor-href. First valid anchor wins so a
        // later off-host / wrong duplicate can't override a good same-host hit.
        // Self-links are NOT excluded: the current page is a legitimate chapter,
        // so a sidebar item pointing at it resolves in its natural position.
        val byText = HashMap<String, String>()
        for (a in anchors) {
            val u = runCatching { URI(a.href) }.getOrNull() ?: continue
            if (u.host != host) continue
            if ((u.scheme ?: continue) != scheme) continue
            val path = u.rawPath.orEmpty()
            if (isWikiSpecialPath(path)) continue
            val segs = path.trim('/').split('/').filter { it.isNotEmpty() }
            if (segs.size < 3 || !segs[0].equals("wikis", ignoreCase = true)) continue
            val text = a.text.trim()
            if (text.isEmpty()) continue
            if (!byText.containsKey(text)) byText[text] = a.href.substringBefore('#')
        }

        val seen = HashSet<String>()
        val out = ArrayList<TocEntry>()
        for (label in labels) {
            val key = label.trim()
            val saved = byText[key] ?: continue
            if (!seen.add(saved)) continue
            out += TocEntry(label = key, savedHref = saved, fragment = null)
        }
        // Include the page the user is on alongside the detected chapters. If
        // the sidebar listed it, it already resolved above and keeps its in-order
        // slot; otherwise (e.g. a hub / landing page absent from its own sidebar)
        // prepend it so bulk save still covers the current page. Only augments a
        // real chapter set — a page where nothing matched stays empty so the
        // caller falls back to URL-pattern detection.
        val currentSaved = pageUrl.substringBefore('#')
        if (out.isNotEmpty() && currentSaved.isNotBlank() && seen.add(currentSaved)) {
            out.add(0, TocEntry(label = currentPageLabel(current), savedHref = currentSaved, fragment = null))
        }
        return out
    }

    /** Human label for the current page: its last path segment, URL-decoded,
     *  underscores -> spaces (`/wikis/persona-5/January_to_March` ->
     *  "January to March"). Falls back to "Current page" when there's no
     *  usable segment. */
    private fun currentPageLabel(uri: URI): String {
        val seg = uri.rawPath.orEmpty().trim('/').substringAfterLast('/')
        if (seg.isBlank()) return "Current page"
        val decoded = runCatching { java.net.URLDecoder.decode(seg, "UTF-8") }.getOrNull() ?: seg
        return decoded.replace('_', ' ').trim().ifBlank { "Current page" }
    }

    /**
     * fireemblemwod.com English walkthrough chapter path. Shape:
     * `/fe<N>/<guide-dir>/ENG_<...>.htm` where the file name carries a chapter
     * marker — `cap` (capitulo / cap / fecapitulo / gacapitulo) or `prologo`.
     * The `<guide-dir>` segment (guia, gaguia, guiafe7, …) and the chapter file
     * naming both drift per game, so the marker is what keeps class / item /
     * character `ENG_` pages — which live directly under `/fe<N>/`, not inside a
     * guide dir — out of the chapter set. The required `ENG_` prefix excludes
     * the parallel Spanish chapter pages.
     */
    private val FEWOD_CHAPTER_PATH = Regex(
        "^/fe\\d+/[^/]+/ENG_[^/]*(?:cap|prologo)[^/]*\\.htm$",
        RegexOption.IGNORE_CASE
    )

    /**
     * Recognizes the English walkthrough chapters on a fireemblemwod.com page.
     * On a game hub page (`ENG_fe<N>.htm`) this yields the full chapter set the
     * hub lists; on an individual chapter page it yields whatever sibling
     * chapters that page links (typically prev/next). DOM order is preserved and
     * fragment-stripped duplicates collapse to one save target — same contract
     * as [detect]'s generic path, so downstream bulk save is unchanged.
     */
    private fun detectFireEmblemWod(
        links: List<RawLink>,
        pageUrl: String,
        pageTitle: String,
        host: String,
        scheme: String,
    ): DetectionResult {
        val seen = LinkedHashSet<String>()
        val entries = mutableListOf<TocEntry>()
        for (link in links) {
            val u = runCatching { URI(link.href) }.getOrNull() ?: continue
            if (u.host != host) continue
            if ((u.scheme ?: continue) != scheme) continue
            if (!FEWOD_CHAPTER_PATH.matches(u.rawPath.orEmpty())) continue
            val saved = link.href.substringBefore('#')
            if (seen.add(saved)) {
                entries += TocEntry(label = link.text, savedHref = saved, fragment = null)
            }
        }
        return DetectionResult(pageUrl, pageTitle, entries)
    }

    /**
     * Parses `?page=N` (anywhere in the query string, position-tolerant) and
     * returns N as an Int. Tolerates extra params: `?id=42&page=5` returns 5;
     * `?page=5&id=42` also returns 5. Returns null for queries without a
     * `page=<digits>` token, used as the sort-fallback sentinel above.
     */
    private val PAGE_QUERY_REGEX = Regex("(?:^|[?&])page=(\\d+)", RegexOption.IGNORE_CASE)

    private fun syntheticPageNumber(href: String): Int? {
        val query = runCatching { URI(href).rawQuery }.getOrNull() ?: return null
        return PAGE_QUERY_REGEX.find(query)?.groupValues?.get(1)?.toIntOrNull()
    }

    /**
     * Label for a synthetic entry. Picks `Page N` when the URL has a
     * `?page=N` token; falls back to a generic descriptor when the query
     * shape is anything else. Synthetic entries are filtered from the
     * Contents view, so this label only surfaces in debug logs and the
     * manifest mirror today.
     */
    private fun labelForSyntheticUrl(href: String): String =
        syntheticPageNumber(href)?.let { "Page $it" } ?: "Additional page"

    /**
     * Pagination navigation anchor texts that should never be admitted
     * as chapter candidates. Matches "Next Page", "Last Page", "Prev",
     * bare page numbers ("1", "12"), and the common arrow variants
     * ("» Next", "Last »", "‹ Prev"). Filtering at the detector keeps
     * the picker in proper reading sequence: the first non-pagination
     * anchor for each save URL establishes its position, and URLs
     * found only via pagination drop out (the sidebar TOC is the
     * authoritative source for the chapter order).
     */
    private val PAGINATION_TEXT_REGEX = Regex(
        "^(\\s*[«»‹›→←]+\\s*)?(\\d+|next|prev|previous|last|first)(\\s+page)?(\\s*[«»‹›→←]+\\s*)?$",
        RegexOption.IGNORE_CASE
    )

    private fun isPaginationText(text: String): Boolean =
        PAGINATION_TEXT_REGEX.matches(text.trim())

    /**
     * MediaWiki convention: non-content pages live under namespaced
     * path segments like `/Special:`, `/Talk:`, `/User:`, etc. None of
     * these are chapters of a guide; they are admin / meta pages.
     * Filtered at the detector so they never reach the picker.
     *
     * The regex matches a slash-prefixed namespace segment followed by
     * a colon. Case-sensitive on the first letter is too strict (some
     * wikis lowercase namespaces); case-insensitive is what we use.
     */
    private val WIKI_NAMESPACE_REGEX = Regex(
        "/(Special|Talk|User|Category|File|Template|Help):",
        RegexOption.IGNORE_CASE
    )

    private fun isWikiSpecialPath(path: String): Boolean =
        WIKI_NAMESPACE_REGEX.containsMatchIn(path)

    private const val EXTRACTOR_ASSET = "toc_extract.js"
    private const val IGN_SIDEBAR_ASSET = "ign_sidebar_toc.js"

    /**
     * Runs the JS extractor against the live DOM of [webView], parses the
     * raw-link JSON, and returns a [DetectionResult]. Must be called on
     * [kotlinx.coroutines.Dispatchers.Main] because
     * [android.webkit.WebView.evaluateJavascript] dispatches its callback there.
     */
    suspend fun detect(
        context: android.content.Context,
        webView: android.webkit.WebView,
        pageUrl: String,
        pageTitle: String
    ): DetectionResult {
        // IGN-only: try the structured-sidebar pass first. IGN walkthroughs
        // render chapters as <button>s (no href) so the URL-pattern path below
        // finds nothing; the sidebar pass recovers them. Only wins when it
        // resolves >= 3 chapters, otherwise fall through unchanged.
        val host = runCatching { URI(pageUrl).host }.getOrNull().orEmpty()
        if (host.equals("ign.com", true) || host.endsWith(".ign.com", true)) {
            val ign = runCatching { detectIgnSidebarFromWebView(context, webView, pageUrl, pageTitle) }
                .getOrNull()
            if (ign != null && ign.entries.size >= 2) {
                android.util.Log.d("ToCDetector", "IGN sidebar pass: ${ign.entries.size} chapters for $pageUrl")
                return ign
            }
            // Sidebar pass found <= 1 (or errored) — fall through to the regular
            // URL-pattern detection used for every other site.
            android.util.Log.d(
                "ToCDetector",
                "IGN sidebar pass found ${ign?.entries?.size ?: 0} (<2) for $pageUrl; falling back to URL-pattern detection"
            )
        }
        val js = context.assets.open(EXTRACTOR_ASSET).bufferedReader().use { it.readText() }
        val rawJson = kotlinx.coroutines.suspendCancellableCoroutine<String?> { cont ->
            webView.evaluateJavascript(js) { result -> cont.resume(result) }
        } ?: return DetectionResult(pageUrl, pageTitle, emptyList())

        val inner = decodeJsonString(rawJson) ?: return DetectionResult(pageUrl, pageTitle, emptyList())
        val arr = runCatching { org.json.JSONArray(inner) }.getOrNull()
            ?: return DetectionResult(pageUrl, pageTitle, emptyList())

        val links = (0 until arr.length()).mapNotNull { i ->
            val obj = arr.optJSONObject(i) ?: return@mapNotNull null
            val text = obj.optString("text", "")
            val href = obj.optString("href", "")
            val y = obj.optInt("y", -1)
            val standalone = obj.optBoolean("standalone", true)
            if (text.isEmpty() || href.isEmpty()) null else RawLink(text, href, y, standalone)
        }
        return detect(links, pageUrl, pageTitle)
    }

    /**
     * Runs [IGN_SIDEBAR_ASSET] against the live DOM, which returns
     * `{ labels: [...], anchors: [{text, href}, ...] }`, then resolves chapters
     * via [detectIgnSidebar]. Must be called on the main thread (see [detect]).
     */
    private suspend fun detectIgnSidebarFromWebView(
        context: android.content.Context,
        webView: android.webkit.WebView,
        pageUrl: String,
        pageTitle: String
    ): DetectionResult {
        val js = context.assets.open(IGN_SIDEBAR_ASSET).bufferedReader().use { it.readText() }
        val rawJson = kotlinx.coroutines.suspendCancellableCoroutine<String?> { cont ->
            webView.evaluateJavascript(js) { result -> cont.resume(result) }
        } ?: return DetectionResult(pageUrl, pageTitle, emptyList())
        val inner = decodeJsonString(rawJson) ?: return DetectionResult(pageUrl, pageTitle, emptyList())
        val obj = runCatching { org.json.JSONObject(inner) }.getOrNull()
            ?: return DetectionResult(pageUrl, pageTitle, emptyList())
        val labelsArr = obj.optJSONArray("labels") ?: return DetectionResult(pageUrl, pageTitle, emptyList())
        val anchorsArr = obj.optJSONArray("anchors") ?: return DetectionResult(pageUrl, pageTitle, emptyList())
        val labels = (0 until labelsArr.length()).map { labelsArr.optString(it, "") }.filter { it.isNotEmpty() }
        val anchors = (0 until anchorsArr.length()).mapNotNull { i ->
            val a = anchorsArr.optJSONObject(i) ?: return@mapNotNull null
            val text = a.optString("text", "")
            val href = a.optString("href", "")
            if (text.isEmpty() || href.isEmpty()) null else RawLink(text, href)
        }
        return DetectionResult(pageUrl, pageTitle, detectIgnSidebar(labels, anchors, pageUrl))
    }

    /**
     * `evaluateJavascript`'s callback wraps the JS return in a JSON literal --
     * `"\"[{\\\"text\\\":...}]\""` for a string return. Strip the outer quotes
     * and unescape, returning null on malformed input.
     */
    private fun decodeJsonString(raw: String): String? {
        if (raw == "null") return null
        if (!raw.startsWith('"') || !raw.endsWith('"') || raw.length < 2) return raw
        return runCatching {
            org.json.JSONArray("[$raw]").optString(0, "")
        }.getOrNull()
    }
}
