package com.twinlore.app.data

import android.util.Log
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.io.File
import java.util.zip.ZipFile
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Parses EPUB (zip of XHTML+resources) files. Produces the same shape of
 * output as MhtParser so the rest of the guide pipeline (cache writer,
 * shouldInterceptRequest, WebView load) treats EPUBs identically.
 *
 * Supports EPUB 2 (toc.ncx) and EPUB 3 (nav.xhtml). DRM and fixed-layout
 * EPUBs are out of scope; encrypted archives are rejected at resolveSpine
 * time.
 */
@Singleton
class EpubParser @Inject constructor() {

    companion object {
        private const val TAG = "EpubParser"
    }

    /** One spine entry — a content document, in reading order. */
    data class Chapter(
        /** Path of the chapter file relative to the OPF directory, e.g. "ch1.xhtml". */
        val opfRelativeHref: String,
        /** Path within the archive, e.g. "OEBPS/ch1.xhtml". */
        val archiveEntryName: String,
        /** Manifest media-type, e.g. "application/xhtml+xml". */
        val mediaType: String?
    )

    /** Resolution result — spine + OPF location for relative path math. */
    data class Spine(
        val chapters: List<Chapter>,
        /** Directory prefix inside the archive that the OPF lives in, with trailing slash, e.g. "OEBPS/". Empty string if the OPF is at the archive root. */
        val opfBaseInArchive: String,
        /** Manifest items keyed by id — needed later for resolving asset references. */
        val manifestById: Map<String, ManifestItem>,
        /** Path to nav doc within archive (EPUB 3) — empty if EPUB 2 or missing. */
        val navHrefInArchive: String,
        /** Path to NCX within archive (EPUB 2) — empty if EPUB 3 or missing. */
        val ncxHrefInArchive: String
    )

    data class ManifestItem(
        val id: String,
        /** OPF-relative href, e.g. "img/test.png" or "ch1.xhtml". */
        val opfRelativeHref: String,
        val mediaType: String?
    )

    /**
     * Output of [parse], shape-compatible with MhtParser.ParseResult:
     * - [html]: concatenated synthetic document for the WebView to load.
     * - [manifest]: original (OPF-relative) href → cache-dir-relative path.
     *               Used by GuideScreen's shouldInterceptRequest hook.
     * - [baseUrl]: always null for EPUBs (no original site URL).
     * - [assetDir]: absolute path of the directory holding extracted assets.
     */
    data class ParseResult(
        val html: String,
        val baseUrl: String?,
        val manifest: Map<String, String>,
        val assetDir: String?
    )

    /**
     * Open [epubFile], resolve container → OPF → spine. Reads only — does not
     * extract any resources to disk; that happens in [parse].
     */
    fun resolveSpine(epubFile: File): Spine? {
        if (!epubFile.exists()) return null
        return try {
            ZipFile(epubFile).use { zip ->
                // Reject DRM-protected archives outright.
                if (zip.getEntry("META-INF/encryption.xml") != null) {
                    Log.w(TAG, "Encrypted EPUB rejected: ${epubFile.name}")
                    return null
                }
                val containerEntry = zip.getEntry("META-INF/container.xml") ?: return null
                val containerXml = zip.getInputStream(containerEntry).bufferedReader().readText()
                val opfPath = parseOpfPath(containerXml) ?: return null

                val opfEntry = zip.getEntry(opfPath) ?: return null
                val opfXml = zip.getInputStream(opfEntry).bufferedReader().readText()
                val opfBase = opfPath.substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" }

                parseOpf(opfXml, opfBase)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resolve EPUB spine", e)
            null
        }
    }

    private fun parseOpfPath(containerXml: String): String? {
        val doc = Jsoup.parse(containerXml, "", Parser.xmlParser())
        val rootfile = doc.selectFirst("rootfile") ?: return null
        return rootfile.attr("full-path").ifEmpty { null }
    }

    private fun parseOpf(opfXml: String, opfBase: String): Spine? {
        val doc = Jsoup.parse(opfXml, "", Parser.xmlParser())

        // Manifest: id -> ManifestItem
        val manifestById = doc.select("manifest > item")
            .mapNotNull { item ->
                val id = item.attr("id").ifEmpty { return@mapNotNull null }
                val href = item.attr("href").ifEmpty { return@mapNotNull null }
                id to ManifestItem(
                    id = id,
                    opfRelativeHref = href,
                    mediaType = item.attr("media-type").ifEmpty { null }
                )
            }
            .toMap(mutableMapOf())

        // Spine: itemref order
        val spineEl = doc.selectFirst("spine") ?: return null
        val chapters = spineEl.select("itemref").mapNotNull { ref ->
            val idref = ref.attr("idref").ifEmpty { return@mapNotNull null }
            val item = manifestById[idref] ?: return@mapNotNull null
            Chapter(
                opfRelativeHref = item.opfRelativeHref,
                archiveEntryName = opfBase + item.opfRelativeHref,
                mediaType = item.mediaType
            )
        }
        if (chapters.isEmpty()) return null

        // Nav (EPUB 3): manifest item with properties containing "nav"
        val navItem = doc.select("manifest > item")
            .firstOrNull { it.attr("properties").split(' ').contains("nav") }
        val navHref = navItem?.attr("href")?.let { opfBase + it } ?: ""

        // NCX (EPUB 2): spine[toc=id] -> manifest item
        val ncxId = spineEl.attr("toc")
        val ncxHref = if (ncxId.isNotEmpty()) {
            manifestById[ncxId]?.opfRelativeHref?.let { opfBase + it } ?: ""
        } else ""

        return Spine(
            chapters = chapters,
            opfBaseInArchive = opfBase,
            manifestById = manifestById,
            navHrefInArchive = navHref,
            ncxHrefInArchive = ncxHref
        )
    }

    /**
     * Open [epubFile], extract all manifest resources (other than the spine documents
     * themselves and the nav/NCX files) to [cacheDir], rewrite chapter HTML so href/src
     * reference the extracted paths, concatenate all chapters into one synthetic index
     * document, and return the result. Returns null on any unrecoverable error,
     * including any archive entry whose canonical path would escape [cacheDir].
     *
     * The manifest map keys are the ORIGINAL OPF-relative hrefs (so shouldInterceptRequest
     * can look them up by request URL). Values are paths relative to bookmarkDir,
     * prefixed with "assets/" to match MhtCacheManager's convention.
     */
    fun parse(epubFile: File, cacheDir: File): ParseResult? {
        val spine = resolveSpine(epubFile) ?: return null
        return try {
            ZipFile(epubFile).use { zip ->
                // Scrub any previous partial extraction so a mid-loop failure (e.g. zip-slip)
                // leaves an empty cacheDir, not a half-populated one. MhtParser does the same.
                cacheDir.deleteRecursively()
                cacheDir.mkdirs()

                // Items we skip during extraction: the spine documents themselves
                // (they get inlined into the synthetic HTML) and the nav/NCX files.
                val spineHrefs = spine.chapters.map { it.opfRelativeHref }.toSet()
                val navOpfRelative = spine.navHrefInArchive.removePrefix(spine.opfBaseInArchive)
                    .takeIf { it.isNotEmpty() }
                val ncxOpfRelative = spine.ncxHrefInArchive.removePrefix(spine.opfBaseInArchive)
                    .takeIf { it.isNotEmpty() }
                val skipHrefs = spineHrefs + listOfNotNull(navOpfRelative, ncxOpfRelative)

                // 1. Extract every non-skip manifest entry, with zip-slip protection.
                val manifest = mutableMapOf<String, String>()
                for (item in spine.manifestById.values) {
                    if (item.opfRelativeHref in skipHrefs) continue
                    val entry = zip.getEntry(spine.opfBaseInArchive + item.opfRelativeHref) ?: continue
                    val outFile = resolveInsideCache(cacheDir, item.opfRelativeHref) ?: run {
                        Log.w(TAG, "zip-slip rejected: ${item.opfRelativeHref}")
                        return@use null
                    }
                    outFile.parentFile?.mkdirs()
                    zip.getInputStream(entry).use { input -> outFile.outputStream().use { out -> input.copyTo(out) } }
                    manifest[item.opfRelativeHref] = "assets/${item.opfRelativeHref}"
                }

                // 2. Resolve nav labels (EPUB 3 nav.xhtml or EPUB 2 toc.ncx).
                val navTitles = resolveNavTitles(zip, spine)

                // 3. For each chapter: read XHTML, JSoup-rewrite href/src to manifest-mapped
                //    paths, extract <body>, wrap in <section class="toc-heading" ...>.
                val chapterSections = StringBuilder()
                spine.chapters.forEachIndexed { index, chapter ->
                    val entry = zip.getEntry(chapter.archiveEntryName) ?: return@forEachIndexed
                    val xhtml = zip.getInputStream(entry).bufferedReader().readText()
                    val doc = Jsoup.parse(xhtml, "", Parser.htmlParser())

                    val chapterDir = chapter.opfRelativeHref.substringBeforeLast('/', "")
                        .let { if (it.isEmpty()) "" else "$it/" }
                    for (el in doc.select("[href], [src]")) {
                        for (attr in listOf("href", "src")) {
                            val raw = el.attr(attr)
                            if (raw.isEmpty() || raw.startsWith("http://") || raw.startsWith("https://") ||
                                raw.startsWith("data:") || raw.startsWith("#")) continue
                            val resolved = resolveOpfRelative(chapterDir, raw)
                            val mapped = manifest[resolved] ?: continue
                            el.attr(attr, mapped)
                        }
                    }

                    val bodyHtml = doc.selectFirst("body")?.html() ?: doc.html()
                    val title = navTitles[chapter.opfRelativeHref]
                        ?: doc.selectFirst("title")?.text()?.takeIf { it.isNotBlank() }
                        ?: doc.selectFirst("h1, h2, h3")?.text()?.takeIf { it.isNotBlank() }
                        ?: "Chapter ${index + 1}"

                    chapterSections.append("<section class=\"toc-heading\" id=\"sec-").append(index)
                        .append("\" data-title=\"").append(escapeHtmlAttr(title)).append("\">")
                    // Horizontal rule between chapters (skip before the very first one).
                    // The chapter title is kept on the section's data-title for the TOC drawer
                    // but not rendered inline — most EPUBs already have their own heading.
                    if (index > 0) chapterSections.append("<hr class=\"epub-chapter-separator\"/>")
                    chapterSections.append(bodyHtml)
                    chapterSections.append("</section>")
                }

                val finalHtml = buildString {
                    append("<!DOCTYPE html><html lang=\"en\"><head>")
                    append("<meta charset=\"utf-8\"/>")
                    append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"/>")
                    append("<style>body{font-family:sans-serif;line-height:1.5;padding:1em;max-width:42em;margin:0 auto;}")
                    append("img{max-width:100%;height:auto;}section.toc-heading{margin-bottom:2em;}")
                    append("hr.epub-chapter-separator{border:0;border-top:1px solid currentColor;opacity:0.25;margin:3em auto;width:40%;}</style>")
                    append("</head><body data-gamefaqs-converted=\"true\">")
                    append(chapterSections)
                    append("</body></html>")
                }

                ParseResult(
                    html = finalHtml,
                    baseUrl = null,
                    manifest = manifest,
                    assetDir = cacheDir.absolutePath
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse EPUB", e)
            null
        }
    }

    /** Resolve to a File guaranteed to live under [cacheDir]. Returns null on zip-slip. */
    private fun resolveInsideCache(cacheDir: File, relativePath: String): File? {
        val target = File(cacheDir, relativePath).canonicalFile
        val root = cacheDir.canonicalFile
        return if (target.path == root.path || target.path.startsWith(root.path + File.separator)) target else null
    }

    /** Resolve "ch1/sub.xhtml" against base "ch1/" + ref "../img/x.png" → "img/x.png". */
    private fun resolveOpfRelative(baseDir: String, ref: String): String {
        val combined = baseDir + ref
        val parts = ArrayDeque<String>()
        for (segment in combined.split('/')) {
            when (segment) {
                "", "." -> {}
                ".." -> if (parts.isNotEmpty()) parts.removeLast()
                else -> parts.addLast(segment)
            }
        }
        return parts.joinToString("/")
    }

    /** Pull chapter-href → title map from EPUB 3 nav or EPUB 2 NCX, whichever exists. */
    private fun resolveNavTitles(zip: ZipFile, spine: Spine): Map<String, String> {
        val out = mutableMapOf<String, String>()
        val opfBase = spine.opfBaseInArchive
        // Derive the OPF-relative directory of whichever nav file is present.
        // navHrefInArchive / ncxHrefInArchive already include the opfBase prefix,
        // so strip it first, then take the parent directory.
        val navArchivePath = spine.navHrefInArchive.ifEmpty { spine.ncxHrefInArchive }
        val navOpfRelativePath = navArchivePath.removePrefix(opfBase)
        val navDir = navOpfRelativePath.substringBeforeLast('/', "")
            .let { if (it.isEmpty()) "" else "$it/" }
        fun opfRelative(href: String): String {
            val noFragment = href.substringBefore('#')
            return resolveOpfRelative(navDir, noFragment)
        }

        if (spine.navHrefInArchive.isNotEmpty()) {
            zip.getEntry(spine.navHrefInArchive)?.let { entry ->
                val xml = zip.getInputStream(entry).bufferedReader().readText()
                val doc = Jsoup.parse(xml, "", Parser.xmlParser())
                // JSoup xmlParser stores "epub:type" as the literal attribute name.
                // Try namespace-qualified attribute first, then plain "type", then any nav.
                val anchors = doc.select("nav[epub:type=toc] a").ifEmpty {
                    doc.select("nav[type=toc] a").ifEmpty {
                        doc.select("nav a")
                    }
                }
                for (a in anchors) {
                    val href = a.attr("href")
                    if (href.isEmpty()) continue
                    val title = a.text()
                    if (title.isBlank()) continue
                    out.putIfAbsent(opfRelative(href), title)
                }
            }
        } else if (spine.ncxHrefInArchive.isNotEmpty()) {
            zip.getEntry(spine.ncxHrefInArchive)?.let { entry ->
                val xml = zip.getInputStream(entry).bufferedReader().readText()
                val doc = Jsoup.parse(xml, "", Parser.xmlParser())
                for (point in doc.select("navPoint")) {
                    val label = point.selectFirst("navLabel > text")?.text()?.ifBlank { null } ?: continue
                    val src = point.selectFirst("content")?.attr("src")?.ifEmpty { null } ?: continue
                    out.putIfAbsent(opfRelative(src), label)
                }
            }
        }
        return out
    }

    private fun escapeHtmlAttr(s: String): String = s
        .replace("&", "&amp;")
        .replace("\"", "&quot;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
}
