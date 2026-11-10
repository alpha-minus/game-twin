package com.twinlore.app.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.webkit.WebView
import androidx.compose.ui.graphics.Color
import com.twinlore.app.data.AppFont
import com.twinlore.app.data.LandingEntry
import com.twinlore.app.ui.theme.getContrastingTextColor
import com.twinlore.app.data.buildLandingNavHtml
import com.twinlore.app.data.buildLandingQuickSearchTemplatesHtml
import com.twinlore.app.data.buildRecentSearchesHtml
import com.twinlore.app.data.SavedTabRepository
import com.twinlore.app.data.SearchEngine
import com.twinlore.app.domain.ScrollMarker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URL

/** View tag key for last long-press coordinates (floatArrayOf(x, y)) used to resolve high-quality image. */
const val TAG_KEY_IMAGE_RESOLVE_COORDS = 0x0f017000

/** Format Compose Color as CSS hex string (e.g. #ff0000). */
fun colorToHex(color: Color): String =
    "#%02x%02x%02x".format(
        (color.red * 255).toInt().coerceIn(0, 255),
        (color.green * 255).toInt().coerceIn(0, 255),
        (color.blue * 255).toInt().coerceIn(0, 255)
    )

/** Format Compose Color as CSS rgba string. */
fun colorToRgba(color: Color, alpha: Float = 0.55f): String =
    "rgba(${(color.red * 255).toInt().coerceIn(0, 255)}, ${(color.green * 255).toInt().coerceIn(0, 255)}, ${(color.blue * 255).toInt().coerceIn(0, 255)}, $alpha)"

/** Hex string for a contrasting text color (white or black) on the given background. */
fun colorToContrastHex(color: Color): String = colorToHex(color.getContrastingTextColor())

/** Returns the Activity from a Context, or null if not found. */
tailrec fun contextAsActivity(ctx: Context): Activity? = when (ctx) {
    is Activity -> ctx
    is ContextWrapper -> contextAsActivity(ctx.baseContext)
    else -> null
}

fun isValidUrl(input: String): Boolean {
    if (input.isBlank()) return false
    return try {
        val url = URL(input)
        url.protocol in listOf("http", "https", "file")
    } catch (e: Exception) {
        input.contains(".") && !input.contains(" ") &&
            (input.startsWith("http://") || input.startsWith("https://") ||
                input.startsWith("www.") || input.matches(Regex("^[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}.*")))
    }
}

/**
 * Strict URL policy for in-app WebView navigation.
 * Blocks executable/custom schemes such as javascript:, intent:, and data:.
 */
fun isAllowedWebViewUrl(url: String): Boolean {
    val trimmed = url.trim()
    if (trimmed.isEmpty()) return false
    return trimmed.startsWith("http://")
            || trimmed.startsWith("https://")
            || trimmed.startsWith("file://")
            || trimmed.startsWith("content://")
            || trimmed.startsWith("vault://section/")
            || trimmed == "about:blank"
}

fun buildSearchUrlForEngine(engine: SearchEngine, query: String): String {
    val encoded = java.net.URLEncoder.encode(query, "UTF-8")
    return when (engine) {
        SearchEngine.GOOGLE -> "https://www.google.com/search?q=$encoded"
        SearchEngine.DUCKDUCKGO -> "https://duckduckgo.com/?q=$encoded"
        SearchEngine.BRAVE -> "https://search.brave.com/search?q=$encoded"
        SearchEngine.STARTPAGE -> "https://www.startpage.com/search?q=$encoded"
        SearchEngine.QWANT -> "https://www.qwant.com/?q=$encoded"
        SearchEngine.ECOSIA -> "https://www.ecosia.org/search?q=$encoded"
        SearchEngine.BING -> "https://www.bing.com/search?q=$encoded"
        SearchEngine.BAIDU -> "https://www.baidu.com/s?wd=$encoded"
        SearchEngine.YANDEX -> "https://yandex.com/search/?text=$encoded"
    }
}

/**
 * Detect if a file is a text FAQ by reading only the first 16KB.
 * Never loads the full file. Checks for GameFAQs/NeoSeeker markers.
 */
fun isTextFaqFile(file: java.io.File): Boolean {
    if (!file.exists()) return false
    return try {
        file.bufferedReader(Charsets.UTF_8).use { reader ->
            val buf = CharArray(16 * 1024)
            val n = reader.read(buf)
            if (n <= 0) return false
            val head = String(buf, 0, n)
            if ("data-gamefaqs-converted" in head) return false
            if ("game_faq_text" in head) return true
            if ("id=\"faqtext\"" in head) return true
            if ("id='faqtext'" in head) return true
            if ("id=\"faqtxt\"" in head) return true
            if ("id='faqtxt'" in head) return true
            false
        }
    } catch (e: Exception) { false }
}

private val HTML_TAG_REGEX = Regex("<[^>]+>")

private fun decodeHtmlEntities(text: String): String {
    var result = text
    result = result.replace("&amp;", "&")
    result = result.replace("&lt;", "<")
    result = result.replace("&gt;", ">")
    result = result.replace("&quot;", "\"")
    result = result.replace("&#39;", "'")
    result = result.replace("&nbsp;", " ")
    result = result.replace("&#x27;", "'")
    result = result.replace("&apos;", "'")
    return result
}

/**
 * Stream-extract text from a text FAQ HTML file, line by line.
 * Never holds the full HTML in memory. Scans for the #faqtext/#faqtxt
 * container, collects text from <pre> blocks inside it, strips tags,
 * decodes entities.
 *
 * Memory usage: proportional to the extracted TEXT, not the HTML file size.
 */
fun extractTextFaqFromFile(file: java.io.File): String? {
    if (!file.exists()) return null
    return try {
        val result = StringBuilder()
        var insideContainer = false
        var insidePre = false
        var containerDepth = 0
        var containerTagName = ""
        val preBuffer = StringBuilder()

        file.bufferedReader(Charsets.UTF_8).useLines { lines ->
            for (line in lines) {
                if (!insideContainer) {
                    // Look for the container opening: <div id="faqtext"> or <div id="faqtxt">
                    val lowerLine = line.lowercase()
                    val idMatch = when {
                        "id=\"faqtext\"" in lowerLine || "id='faqtext'" in lowerLine -> true
                        "id=\"faqtxt\"" in lowerLine || "id='faqtxt'" in lowerLine -> true
                        else -> false
                    }
                    if (idMatch) {
                        // Extract tag name from this line
                        val tagMatch = Regex("""<(\w+)[^>]*id=["']faq""", RegexOption.IGNORE_CASE).find(line)
                        containerTagName = tagMatch?.groupValues?.get(1)?.lowercase() ?: "div"
                        insideContainer = true
                        containerDepth = 1
                        // Process remainder of this line after the opening tag
                        val afterTag = line.substringAfter(">", "")
                        if (afterTag.isNotEmpty()) {
                            processContainerLine(afterTag, containerTagName, result, preBuffer,
                                { insidePre = it }, { insidePre }, { containerDepth += it },
                                { containerDepth })
                            if (containerDepth <= 0) {
                                insideContainer = false
                            }
                        }
                    }
                } else {
                    // Inside the container — extract text from <pre> blocks
                    processContainerLine(line, containerTagName, result, preBuffer,
                        { insidePre = it }, { insidePre }, { containerDepth += it },
                        { containerDepth })
                    if (containerDepth <= 0) break
                }
            }
        }
        // Flush any remaining pre buffer
        if (preBuffer.isNotEmpty()) {
            val text = HTML_TAG_REGEX.replace(preBuffer.toString(), "")
            result.append(decodeHtmlEntities(text))
        }
        result.toString().takeIf { it.isNotBlank() }
    } catch (e: Exception) {
        android.util.Log.e("TextFaqExtractor", "Error extracting text FAQ", e)
        null
    }
}

private fun processContainerLine(
    line: String,
    containerTagName: String,
    result: StringBuilder,
    preBuffer: StringBuilder,
    setInsidePre: (Boolean) -> Unit,
    getInsidePre: () -> Boolean,
    adjustDepth: (Int) -> Unit,
    getDepth: () -> Int
) {
    // An empty source line inside a <pre> is a paragraph separator and must
    // survive extraction — the scan loop below never runs for a zero-length
    // line, which used to silently drop these, collapsing every paragraph
    // boundary in the guide (GH #25).
    if (line.isEmpty()) {
        if (getInsidePre()) preBuffer.append('\n')
        return
    }
    // Scan the line left-to-right, handling EVERY transition in order:
    // container open/close (nested <div>/</div>) and <pre>/</pre>.
    //
    // Large GameFAQs FAQs are split into many sibling <pre id="faqspan-N">
    // blocks whose boundaries land on a single line, e.g.
    //   …end of block N</pre><pre id="faqspan-N+1">start of block N+1…
    // A one-transition-per-line scan would consume the </pre>, miss the
    // following <pre>, and silently drop every other ~50KB block — the text
    // then reads as scrambled/out-of-order. So we loop until the line is
    // exhausted (or the container closes), never assuming a single transition.
    val openTag = "<$containerTagName"
    val closeTag = "</$containerTagName"
    val lowerLine = line.lowercase()
    var pos = 0
    while (pos < line.length) {
        if (getInsidePre()) {
            // Inside a <pre>: only </pre> ends it; ignore any nested tags.
            val end = lowerLine.indexOf("</pre>", pos)
            if (end < 0) {
                preBuffer.append(line, pos, line.length)
                preBuffer.append("\n")
                return
            }
            preBuffer.append(line, pos, end)
            val text = HTML_TAG_REGEX.replace(preBuffer.toString(), "")
            result.append(decodeHtmlEntities(text))
            result.append("\n")
            preBuffer.clear()
            setInsidePre(false)
            pos = end + 6 // length of "</pre>"
        } else {
            // Between blocks: advance to the nearest of <pre, nested open, or
            // container close.
            val nextPre = lowerLine.indexOf("<pre", pos)
            val nextOpen = lowerLine.indexOf(openTag, pos)
            val nextClose = lowerLine.indexOf(closeTag, pos)
            val next = listOf(nextPre, nextOpen, nextClose).filter { it >= 0 }.minOrNull() ?: return
            when (next) {
                nextClose -> {
                    adjustDepth(-1)
                    if (getDepth() <= 0) return
                    pos = nextClose + closeTag.length
                }
                nextOpen -> {
                    adjustDepth(1)
                    pos = nextOpen + openTag.length
                }
                else -> { // nextPre — enter the <pre>, skipping its opening tag
                    val gt = line.indexOf('>', next)
                    if (gt < 0) return // tag attributes wrap to next line (rare)
                    setInsidePre(true)
                    pos = gt + 1
                }
            }
        }
    }
}

/**
 * Convert raw TXT content to HTML with auto-detected section headings for TOC navigation.
 * Uses a multi-strategy detection pipeline: search-code anchors, separator fences,
 * numbered/Roman sections, bracketed headings, ALL CAPS headings, and double-blank breaks.
 */
/** Max characters of guide text rendered into a single WebView document, and the
 *  threshold above which a guide is split into a memory-safe page-group. Beyond
 *  this the whole-document DOM (built in native Blink memory) can grow large
 *  enough that lmkd reaps the foreground app on a memory-pressured device.
 *  Tuned generously so only genuinely huge guides split: most guides (e.g. Final
 *  Fantasy V ~666K) render as a single page; the FFVII FAQ (~1.9M) and bigger
 *  split. Raising this means more guides render whole — lower it if a mid-size
 *  guide ever crashes. */
const val MAX_TEXT_GUIDE_RENDER_CHARS = 1_500_000

@JvmOverloads
internal fun convertTxtToGameFaqHtml(
    input: String,
    tocEntries: List<GuideTocEntry>? = null,
): String {
    // Cap oversized guides at a line boundary so they open (showing the first part)
    // instead of crash-looping the app. See MAX_TEXT_GUIDE_RENDER_CHARS.
    val rawContent = if (input.length > MAX_TEXT_GUIDE_RENDER_CHARS) {
        val cut = input.lastIndexOf('\n', MAX_TEXT_GUIDE_RENDER_CHARS)
            .takeIf { it > 0 } ?: MAX_TEXT_GUIDE_RENDER_CHARS
        input.substring(0, cut) +
            "\n\n==============================================================\n" +
            "[ This guide is very large. To avoid running out of memory, only\n" +
            "  the first part is shown for now. Full-guide paging is coming. ]\n"
    } else {
        input
    }
    val lines = rawContent.lines()
    val sections = detectTextSections(lines, tocEntries)
    val structuredLines = detectStructuredLineIndices(lines)

    // Build section anchor map (may be empty if no TOC was detected)
    val sectionAnchors = mutableMapOf<Int, Pair<Int, String>>()
    for ((sectionIndex, section) in sections.withIndex()) {
        sectionAnchors[section.startLine] = sectionIndex to section.title
    }

    val sb = StringBuilder()

    sb.append("<!DOCTYPE html><html><head>")
    sb.append("<meta charset=\"UTF-8\">")
    sb.append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
    sb.append("<style>")
    // text-size-adjust:100% opts this page out of TEXT_AUTOSIZING font boosting:
    // joined paragraph lines are thousands of px wide pre-wrap, and Chromium's
    // autosizer inflates exactly those clusters on some screens (GH #25) —
    // per-block, so the page renders in two different text sizes.
    sb.append("html,body{overflow-x:hidden;max-width:100vw;margin:0;touch-action:pan-y;-webkit-overflow-scrolling:touch;-webkit-text-size-adjust:100%;text-size-adjust:100%}")
    sb.append("body{font-family:ui-monospace,monospace;padding:12px;box-sizing:border-box;line-height:1.25}")
    sb.append("pre{margin:0}")
    // Smart formatting (the default). A run holds its lines with their real
    // newlines; white-space:normal collapses those into spaces and drops the
    // segment's common indent, and --ind re-adds the run's indent RELATIVE to
    // that common one, so nesting survives the reflow.
    sb.append(".prose{white-space:normal}")
    sb.append(".run{display:block;white-space:normal;word-wrap:break-word;overflow-wrap:break-word;max-width:100%;min-height:1.25em;padding-left:calc(var(--ind,0)*1ch)}")
    sb.append(".scroll-block{overflow-x:auto;-webkit-overflow-scrolling:touch}")
    sb.append(".scroll-block pre{white-space:pre}")
    sb.append(".scroll-block.fitted{overflow-x:visible;transform-origin:top left}")
    // Raw formatting: every line exactly as written, at whatever size the reader
    // picked. pre-wrap keeps the original line breaks and indentation and wraps
    // only what overflows, so nothing is ever clipped or scrolled off-screen —
    // turn the text size down far enough and a wide ASCII table lines up again.
    // Nothing here rescales anything: the text-size slider is the only thing
    // that sets size in this mode. !important neutralises the inline per-block
    // transforms smart mode leaves behind, so toggling never clears inline styles.
    sb.append("body.gt-raw .run{white-space:pre-wrap;padding-left:0}")
    sb.append("body.gt-raw .scroll-block{overflow-x:hidden}")
    sb.append("body.gt-raw .scroll-block pre{white-space:pre-wrap}")
    sb.append("body.gt-raw .scroll-block.fitted{transform:none!important;margin-bottom:0!important}")
    sb.append("span.toc-heading{display:inline;font-size:0;line-height:0;height:0;width:0;overflow:hidden}")
    sb.append("a{color:inherit;text-decoration:underline;text-underline-offset:2px}")
    sb.append("</style>")
    // data-gt-textfaq marks a guide that went through THIS converter, so the
    // Smart Formatting toggle knows it has runs and scroll-blocks to act on.
    // data-gamefaqs-converted cannot serve that role: EpubParser sets it too, on
    // reflowed EPUB HTML with none of this markup.
    sb.append("</head><body data-gamefaqs-converted=\"true\" data-gt-textfaq=\"1\">")

    // Group lines into prose / structured segments
    val segments = groupIntoSegments(lines, structuredLines)
    for (seg in segments) {
        if (seg.structured) {
            sb.append("<div class=\"scroll-block\"><pre>")
            for (i in seg.start until seg.end) {
                val anchor = sectionAnchors[i]
                if (anchor != null) {
                    val (secIdx, title) = anchor
                    sb.append("<span class=\"toc-heading\" id=\"sec-$secIdx\" data-title=\"${escapeHtmlAttr(title)}\"></span>")
                }
                sb.append(linkifyUrls(escapeHtml(lines[i])))
                if (i < seg.end - 1) sb.append("\n")
            }
            sb.append("</pre></div>")
        } else {
            // Common leading whitespace of the segment. Lines keep their original
            // indent in the text (raw mode shows it verbatim); smart mode collapses
            // it via white-space:normal and re-adds the RELATIVE indent as --ind.
            val minIndent = (seg.start until seg.end)
                .filter { lines[it].trimStart().isNotEmpty() }
                .minOfOrNull { idx ->
                    lines[idx].takeWhile { it == ' ' || it == '\t' }.length
                } ?: 0

            sb.append("<div class=\"prose\">")
            var runStart = seg.start
            while (runStart < seg.end) {
                // Extend the run while the join heuristic says these lines are one
                // hard-wrapped paragraph. This is the SAME test that used to append
                // a space; it now decides run boundaries so the newline survives.
                var runEnd = runStart
                while (runEnd < seg.end - 1 && joinsWithNext(lines, runEnd)) runEnd++

                val indent = lines[runStart].takeWhile { it == ' ' || it == '\t' }.length
                val relIndent = (indent - minIndent).coerceAtLeast(0)
                sb.append("<div class=\"run\" style=\"--ind:$relIndent\">")
                for (i in runStart..runEnd) {
                    val anchor = sectionAnchors[i]
                    if (anchor != null) {
                        val (secIdx, title) = anchor
                        sb.append("<span class=\"toc-heading\" id=\"sec-$secIdx\" data-title=\"${escapeHtmlAttr(title)}\"></span>")
                    }
                    sb.append(linkifyUrls(escapeHtml(lines[i])))
                    if (i < runEnd) sb.append("\n")
                }
                sb.append("</div>")
                runStart = runEnd + 1
            }
            sb.append("</div>")
        }
    }

    // Scroll blocks: auto-fit to screen width on load, tap to expand/shrink, recalc on zoom
    sb.append("<script>")
    sb.append("(function(){")
    sb.append("var busy=false,rt=null;")
    sb.append("function raw(){return document.body.classList.contains('gt-raw')}")
    // Smart mode: per-block fit, in three strict phases — clear stale
    // transforms, read EVERYTHING, then write EVERYTHING. A read after a write
    // forces a synchronous layout of the whole document; interleaving them per
    // block cost ~2174 layouts on the 1.46MB Chrono Trigger guide, twice over,
    // because the observer below re-fired on the margins this pass just set.
    // scrollHeight moves ahead of the transform write safely: transform does
    // not affect the layout box scrollHeight reports.
    sb.append("function fitBlocks(){")
    sb.append("var vw=document.documentElement.clientWidth-24;")
    sb.append("var bs=[].slice.call(document.querySelectorAll('.scroll-block')).filter(function(b){return !b.dataset.expanded});")
    sb.append("bs.forEach(function(b){if(b.classList.contains('fitted')){b.style.transform='';b.style.marginBottom='';}});")
    sb.append("var m=bs.map(function(b){return [b.scrollWidth,b.scrollHeight];});")
    sb.append("bs.forEach(function(b,i){")
    sb.append("var sw=m[i][0],sh=m[i][1];")
    sb.append("if(sw<=vw){b.classList.remove('fitted');b.dataset.fitScale='';b.style.transform='';b.style.marginBottom='';return;}")
    sb.append("var scale=vw/sw;")
    sb.append("b.dataset.fitScale=scale;")
    sb.append("b.classList.add('fitted');")
    sb.append("b.style.transform='scale('+scale+')';")
    sb.append("b.style.marginBottom=-(sh*(1-scale))+'px';")
    sb.append("});")
    sb.append("}")
    // Raw mode deliberately measures and rescales NOTHING. The text-size slider
    // is the only thing that sets size there, and CSS pre-wrap handles anything
    // too wide. An earlier version auto-fitted the document to its widest line,
    // which silently ate the slider: the fit was computed from a probe that had
    // already been through WebView textZoom, so it cancelled the zoom back out
    // and every slider position rendered at the same size.
    sb.append("function apply(){")
    sb.append("busy=true;")
    sb.append("if(!raw())fitBlocks();")
    sb.append("requestAnimationFrame(function(){requestAnimationFrame(function(){busy=false;});});")
    sb.append("}")
    // The hook Kotlin calls after flipping the gt-raw class (see SmartFormatting.kt).
    sb.append("window.gtApplyFormat=apply;")
    sb.append("apply();")
    // Body-size changes still drive refits, because textZoom does not change the
    // viewport width. The busy flag stops the pass from re-triggering itself on
    // the margin / font-size writes it just made.
    sb.append("new ResizeObserver(function(){if(busy)return;clearTimeout(rt);rt=setTimeout(apply,100);}).observe(document.body);")
    // Click handler: toggle expand/shrink. Inert in raw mode, where nothing is
    // scaled per block.
    sb.append("document.addEventListener('click',function(e){")
    sb.append("if(raw())return;")
    sb.append("var b=e.target.closest('.scroll-block');")
    sb.append("if(!b||!b.dataset.fitScale)return;")
    // Notify native layer to suppress top-bar toggle for this tap
    sb.append("if(window.Android&&Android.onBlockToggled)Android.onBlockToggled();")
    sb.append("var top=b.getBoundingClientRect().top;")
    sb.append("if(b.classList.contains('fitted')){")
    sb.append("b.dataset.expanded='1';")
    sb.append("b.classList.remove('fitted');")
    sb.append("b.style.transform='';")
    sb.append("b.style.marginBottom='';")
    sb.append("}else{")
    sb.append("delete b.dataset.expanded;")
    sb.append("var s=parseFloat(b.dataset.fitScale);")
    sb.append("b.classList.add('fitted');")
    sb.append("b.style.transform='scale('+s+')';")
    sb.append("b.style.marginBottom=-(b.scrollHeight*(1-s))+'px';")
    sb.append("}")
    sb.append("var d=b.getBoundingClientRect().top-top;")
    sb.append("if(d)window.scrollBy(0,d);")
    sb.append("});")
    sb.append("})();")
    sb.append("</script>")

    sb.append("</body></html>")
    return sb.toString()
}

/**
 * True when line [i] and line [i+1] are one hard-wrapped paragraph and should
 * reflow into a single line in smart mode. Identical to the old inline join
 * test: same indent, current line near wrap width (short lines are intentional
 * breaks — headings, TOC entries, paragraph ends), and the next line does not
 * start its own list item (GH #25).
 */
private fun joinsWithNext(lines: List<String>, i: Int): Boolean {
    val cur = lines[i].trimEnd()
    val next = lines[i + 1]
    val curContent = cur.trimStart()
    val nextContent = next.trimStart()
    if (curContent.isBlank() || nextContent.isBlank()) return false
    val curIndent = lines[i].takeWhile { it == ' ' || it == '\t' }.length
    val nextIndent = next.takeWhile { it == ' ' || it == '\t' }.length
    if (curIndent != nextIndent) return false
    if (curContent.trim().length < JOIN_MIN_LINE_CHARS) return false
    return LIST_MARKER_REGEX.find(nextContent) == null
}

private data class TextSegment(val start: Int, val end: Int, val structured: Boolean)

/** Group lines into contiguous prose / structured segments. */
private fun groupIntoSegments(lines: List<String>, structuredLines: Set<Int>): List<TextSegment> {
    if (lines.isEmpty()) return listOf(TextSegment(0, 0, false))
    val segments = mutableListOf<TextSegment>()
    var segStart = 0
    var segStructured = structuredLines.contains(0)
    for (i in 1..lines.size) {
        val curStructured = if (i < lines.size) structuredLines.contains(i) else !segStructured
        if (curStructured != segStructured) {
            segments.add(TextSegment(segStart, i, segStructured))
            segStart = i
            segStructured = curStructured
        }
    }
    return segments
}

/**
 * Detect line indices belonging to structured blocks (ASCII art, tables, box art).
 * These lines get horizontal scroll instead of wrapping.
 */
private fun detectStructuredLineIndices(lines: List<String>): Set<Int> {
    // First pass: mark individual structured lines
    val isStructured = BooleanArray(lines.size) { i ->
        val trimmed = lines[i].trimEnd()
        if (trimmed.isBlank()) return@BooleanArray false
        // Table/box borders (2+ pipe characters)
        if (trimmed.count { it == '|' } >= 2) return@BooleanArray true
        // Table corner patterns: +---+ or o---o
        if (trimmed.contains(TABLE_CORNER_REGEX)) return@BooleanArray true
        // Separator lines (===, ---, etc.)
        if (SEPARATOR_REGEX.matches(trimmed)) return@BooleanArray true
        // Interior tabs or 3+ consecutive spaces between text – column alignment.
        // A single leading list marker followed by a tab/wide gap ("-\tSome
        // sentence…", "1.\tLocations") is a prose bullet, not columnar data, so
        // only gaps AFTER the marker's text count ("1. Sword   2. Shield" and
        // "-\tWooden Sword   500 yen" stay structured for horizontal layout).
        val interior = trimmed.trimStart()
        val columnBody = LIST_MARKER_REGEX.find(interior)
            ?.let { interior.substring(it.value.length) } ?: interior
        if (columnBody.contains('\t') || columnBody.contains("   ")) return@BooleanArray true
        // Slash-based ASCII art: /\ or \/
        if (trimmed.contains("/\\") || trimmed.contains("\\/")) return@BooleanArray true
        // ASCII map nodes: bracket-dash patterns like [---04--]
        if (BRACKET_DASH_REGEX.containsMatchIn(trimmed)) return@BooleanArray true
        // ASCII map connectors: line content starts with |
        if (interior.startsWith('|')) return@BooleanArray true
        // ALL-CAPS section headers (common in FAQ table of contents)
        if (interior.length >= 4 && ALL_CAPS_HEADER_REGEX.matches(interior)) return@BooleanArray true
        // ASCII art: wide lines with high non-text character density
        if (trimmed.length >= 20) {
            val textChars = trimmed.count { it.isLetterOrDigit() || it == ' ' }
            if (textChars.toFloat() / trimmed.length < 0.4f) return@BooleanArray true
        }
        // Indentation outlier: doesn't match nearest non-blank neighbor on either side
        // Require ≥ 6 spaces — small indents (1-5) are normal paragraph formatting
        val myIndent = trimmed.length - interior.length
        if (myIndent >= 6) {
            var prevIndent: Int? = null
            for (p in i - 1 downTo 0) {
                val pt = lines[p].trimEnd()
                if (pt.isNotBlank()) { prevIndent = pt.length - pt.trimStart().length; break }
            }
            var nextIndent: Int? = null
            for (n in i + 1 until lines.size) {
                val nt = lines[n].trimEnd()
                if (nt.isNotBlank()) { nextIndent = nt.length - nt.trimStart().length; break }
            }
            if (prevIndent != null && nextIndent != null
                && myIndent != prevIndent && myIndent != nextIndent) {
                return@BooleanArray true
            }
        }
        false
    }

    // Second pass: group structured lines, bridging small gaps (≤ 2 non-structured lines)
    val result = mutableSetOf<Int>()
    var i = 0
    while (i < lines.size) {
        if (isStructured[i]) {
            val blockStart = i
            var blockEnd = i
            var j = i + 1
            while (j < lines.size) {
                if (isStructured[j]) {
                    blockEnd = j
                    j++
                } else if (lines[j].trimEnd().isBlank()) {
                    // Blank line breaks the block
                    break
                } else {
                    // Bridge non-blank non-structured lines (max gap of 2)
                    var k = j
                    while (k < lines.size && k - j < 3 && !isStructured[k] && lines[k].trimEnd().isNotBlank()) k++
                    if (k < lines.size && isStructured[k]) {
                        blockEnd = k
                        j = k + 1
                    } else break
                }
            }
            for (idx in blockStart..blockEnd) result.add(idx)
            i = blockEnd + 1
        } else {
            i++
        }
    }
    return result
}

private fun escapeHtml(text: String): String = text
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")

private fun escapeHtmlAttr(text: String): String = escapeHtml(text)
    .replace("'", "&#39;")

/** Regex to match URLs in already-HTML-escaped text. Handles &amp; entities in query strings. */
private val URL_REGEX = Regex("""(https?://(?:[^\s<>"']|&amp;)+|www\.(?:[^\s<>"']|&amp;)+)""")

/** Convert plain-text URLs in already-escaped HTML into clickable <a> links. */
private fun linkifyUrls(escapedHtml: String): String {
    return URL_REGEX.replace(escapedHtml) { match ->
        var url = match.value
        // Strip trailing HTML entities that leaked in
        while (url.endsWith("&amp;") || url.endsWith("&lt;") || url.endsWith("&gt;") || url.endsWith("&quot;")) {
            val drop = when {
                url.endsWith("&quot;") -> 6
                url.endsWith("&amp;") -> 5
                else -> 4 // &lt; or &gt;
            }
            url = url.dropLast(drop)
        }
        // Strip trailing punctuation that's likely sentence-ending
        while (url.isNotEmpty() && url.last() in ".,;:!?") url = url.dropLast(1)
        // Balance trailing parentheses
        while (url.endsWith(")") && url.count { it == '(' } < url.count { it == ')' }) {
            url = url.dropLast(1)
        }
        if (url.length < 10) return@replace match.value
        val href = url.replace("&amp;", "&")
        val fullHref = if (url.startsWith("www.")) "https://$href" else href
        "<a href=\"$fullHref\">$url</a>"
    }
}

internal data class DetectedSection(
    val title: String,
    val startLine: Int,
    val contentStartLine: Int
)

// ── Regex patterns for structured block detection ──

/** Leading list marker: bullet (- * • +) or enumerator ("1.", "2)", "3.1",
 *  "IV.", "a)") followed by whitespace. A bare number without punctuation is
 *  NOT a marker — "9 days of watering…" is a hard-wrapped continuation. */
private val LIST_MARKER_REGEX =
    Regex("""^(?:[-*•+]|\d{1,3}(?:\.\d{1,3}){1,3}\.?|\d{1,3}[.)]|[IVXivx]{1,5}[.)]|[A-Za-z][.)])[ \t]+""")

/** Minimum content length for a prose line to count as hard-wrapped and be
 *  joined with its successor. Text FAQs wrap at ~60-80 columns, so every
 *  interior paragraph line is near that width; shorter lines are intentional
 *  breaks (headings, TOC entries, paragraph-final fragments). */
private const val JOIN_MIN_LINE_CHARS = 45

private val TABLE_CORNER_REGEX = Regex("[+o.'|][-=]{3,}[+o.'|]")
private val BRACKET_DASH_REGEX = Regex("\\[-{2,}|-{2,}]")
private val ALL_CAPS_HEADER_REGEX = Regex("^[A-Z][A-Z \\-&/':]+$")

// ── Regex patterns for section detection ──

private val SEPARATOR_REGEX = Regex("^[=\\-~*#]{5,}\\s*$")
// Full-line box borders: o------o, +======+, .------., '-------', |======| etc.
private val BOX_BORDER_REGEX = Regex("^[+o.'|][-=]{3,}[+o.'|]?\\s*$")

/** Check whether a line is a separator fence (pure separator OR box border). */
internal fun isSeparatorLine(text: String): Boolean =
    SEPARATOR_REGEX.matches(text) || BOX_BORDER_REGEX.matches(text)

/** A frame border: first and last chars are non-alphanumeric, content is mostly non-letter. */
internal fun isFrameBorder(text: String): Boolean {
    val t = text.trim()
    if (t.length < 3) return false
    if (t.first().isLetterOrDigit() || t.last().isLetterOrDigit()) return false
    return t.count { it.isLetter() } <= 2
}

private val BRACKET_HEADING_REGEX = Regex("^\\[([^\\]]{3,80})\\]\\s*$")
// Allow common FAQ anchor code forms like [COTM01], [CotM-INT], {BOSS_01}.
private val SEARCH_CODE_REGEX = Regex("\\[([A-Za-z0-9][A-Za-z0-9_-]{2,15})\\]|\\{([A-Za-z0-9][A-Za-z0-9_-]{2,15})\\}")
private val NUMBERED_HEADING_REGEX = Regex("^(\\d{1,3}[.):]|[IVXivx]{1,6}[.):]|[A-Z][.)])\\s+.{3,100}$")
private val ROMAN_NUMERAL_HEADING_REGEX = Regex("^([IVXivx]{1,6})[.):]\\s+(.{3,100})$")
private val PADDED_TITLE_REGEX = Regex("^[\\s\\-]{2,}(.{3,80})[\\s\\-]{2,}$")
// Hierarchical numbered sections: 1.0, 2.1, 3.2.1, etc.
private val HIERARCHICAL_NUM_REGEX = Regex("^(\\d{1,3}(?:\\.\\d{1,3}){1,3})[.):\\s]\\s*(.{3,100})$")
// Decorated headings: =Title=, =:-Title-:=, -:+:TITLE:+:-, -Title-, {<>} Title {<>}, etc.
private val DECORATED_HEADING_REGEX = Regex("^[=\\-~*#:+.|<>{}\\s]{1,10}(.{3,80})[=\\-~*#:+.|<>{}\\s]{1,10}$")
// Ornamental title embedded in separator: -----.+.---WALKTHROUGH---.+.-----
private val ORNAMENTAL_TITLE_REGEX = Regex("^[\\-=~*#.+|\\s]{3,}([A-Za-z][A-Za-z0-9 &'\\-]{2,40})[\\-=~*#.+|\\s]{3,}$")
// Dot-leader TOC line: "Section Name..........15" or "Section Name . . . . 15"
private val DOT_LEADER_TOC_REGEX = Regex("^\\s*(.{3,60})\\s*[.·]{3,}\\s*\\d+\\s*$")

/**
 * Multi-strategy section detection pipeline for plain-text FAQs:
 *
 * Strategy TOC (highest priority): the guide's own contents block — find a
 *   "Table of Contents"/"CONTENTS"/"OUTLINE" header (see [detectGuideTocBlock]),
 *   parse its entries, and match them against body headings. When 4+ entries
 *   match, that IS the chapter list: nothing inside the TOC block and nothing
 *   absent from it can become a chapter, which suppresses the duplicated
 *   TOC-line detections and ASCII-art noise the pattern strategies produce.
 *
 * Strategy A: Search-code anchors — bracketed codes like [WLKTH] appearing
 *   exactly twice (once in the first 15% as a TOC entry, once later as the section anchor).
 *
 * Strategy B: Separator fences — lines of ===, ---, ***, etc. with adjacent text as headings.
 *
 * Strategy C: Numbered / Roman-numeral top-level sections — "1. Title" or "IV. Title" lines
 *   preceded by a blank line or at file start.
 *
 * Strategy D: [BRACKETED HEADINGS] on their own line.
 *
 * Strategy E: ALL CAPS standalone lines (5-80 chars, 3+ alpha).
 *
 * Strategy F: Dot-leader TOC matching — find TOC lines like "Section Name.......15",
 *   extract section names, then locate matching headings in the body.
 *
 * Strategy G (fallback): Double-blank-line major breaks — a line followed by 2+ empty lines
 *   indicates a major section boundary.
 *
 * Each strategy runs independently. If the best strategy yields fewer than 4
 * sections, the next one is tried. When a contents block was found but didn't
 * produce 4+ matches, the fallback strategies still drop any candidate inside
 * the block — those lines are the contents list, not chapters.
 *
 * [externalTocEntries] (from [detectVerifiedGuideTocEntries] over the WHOLE
 * guide) makes the TOC strategy authoritative for a chunk of a larger guide:
 * whatever subset of entries matches this chunk is returned as-is — even an
 * empty list — because falling through to pattern strategies on a TOC-verified
 * guide only reintroduces the noise the TOC ruled out.
 */
internal fun detectTextSections(
    lines: List<String>,
    externalTocEntries: List<GuideTocEntry>? = null,
): List<DetectedSection> {
    val localToc = detectGuideTocBlock(lines)

    // ── Strategy TOC: the guide's own contents block ──
    // No filterLongTitles here: TOC-verified titles are the guide's own
    // chapter names — dropping a long one loses a real chapter (the UI
    // ellipsizes long labels anyway).
    if (externalTocEntries != null) {
        val scanFrom = localToc?.endLine ?: 0
        return matchTocEntriesToBody(lines, externalTocEntries, scanFrom)
    }
    if (localToc != null) {
        val matched = matchTocEntries(lines, localToc.entries, localToc.endLine)
        if (matched.size >= 4) {
            // Hierarchical contents: keep main chapters, drop sub-chapters
            // (unless mains alone are too few for a guide this size).
            return selectTocDepth(matched, lines.size)
                .map { DetectedSection(it.title, it.line, it.line + 1) }
        }
    }

    // Drop fallback candidates that sit inside the contents block: those lines
    // are TOC entries, and detecting them duplicates every real chapter. The
    // range starts 2 lines above the header because fence-based candidates
    // anchor at the separator/frame line above their heading.
    fun outsideToc(sections: List<DetectedSection>): List<DetectedSection> =
        if (localToc == null) sections
        else sections.filter { it.startLine < localToc.headerLine - 2 || it.startLine >= localToc.endLine }

    // ── Strategy A: Search-code anchors ──
    val searchCodeSections = outsideToc(filterLongTitles(detectBySearchCodes(lines)))
    if (searchCodeSections.size >= 4) return searchCodeSections

    // ── Strategies B-E: structural patterns (run together, produce one merged list) ──
    val structuralSections =
        outsideToc(filterMinDistance(filterLongTitles(detectByStructuralPatterns(lines))))
    if (structuralSections.size >= 4) return structuralSections

    // ── Strategy F: Dot-leader TOC matching ──
    val dotLeaderSections = filterMinDistance(filterLongTitles(detectByDotLeaderToc(lines)))
    if (dotLeaderSections.size >= 4) return dotLeaderSections

    // ── Strategy G: double-blank-line breaks ──
    val doubleBlankSections =
        outsideToc(filterMinDistance(filterLongTitles(detectByDoubleBlankLines(lines))))
    if (doubleBlankSections.size >= 4) return doubleBlankSections

    return emptyList()
}

/** Discard detected sections whose title exceeds the character cap or has fewer than 3 letters. */
private fun filterLongTitles(sections: List<DetectedSection>, maxLen: Int = 50): List<DetectedSection> =
    sections.filter { it.title.length <= maxLen && it.title.count { c -> c.isLetter() } >= 3 }

/** Keep only the first section within each 3-line window to suppress dense false positives. */
private fun filterMinDistance(sections: List<DetectedSection>, minGap: Int = 3): List<DetectedSection> {
    if (sections.isEmpty()) return sections
    val result = mutableListOf(sections.first())
    for (i in 1 until sections.size) {
        if (sections[i].startLine - result.last().startLine >= minGap) {
            result.add(sections[i])
        }
    }
    return result
}

/**
 * Strategy A: Find bracketed/braced codes that appear at least twice with a meaningful gap
 * between the first occurrence (in the TOC block) and a later occurrence (section anchor).
 *
 * Instead of a fixed % boundary, dynamically detects the TOC block end by clustering the
 * first occurrences of all multi-occurrence codes. The TOC block is the dense region where
 * many codes' first occurrences cluster together.
 */
private fun detectBySearchCodes(lines: List<String>): List<DetectedSection> {
    val fullText = lines.joinToString("\n")
    val allMatches = SEARCH_CODE_REGEX.findAll(fullText).toList()
    if (allMatches.isEmpty()) return emptyList()

    val codeOccurrences = mutableMapOf<String, MutableList<Int>>()
    for (match in allMatches) {
        val code = match.groupValues[1].ifEmpty { match.groupValues[2] }
        val lineIndex = fullText.substring(0, match.range.first).count { it == '\n' }
        codeOccurrences.getOrPut(code) { mutableListOf() }.add(lineIndex)
    }

    // Only keep codes that appear at least twice
    val multiCodes = codeOccurrences.filter { it.value.size >= 2 }
    if (multiCodes.size < 4) return emptyList()

    // Determine TOC block boundary dynamically:
    // Collect the first occurrence line of each multi-occurrence code.
    // The TOC block is the contiguous area where these first occurrences cluster.
    val firstOccurrences = multiCodes.mapValues { (_, positions) -> positions.min() }
    val sortedFirstLines = firstOccurrences.values.sorted()

    // TOC boundary = just past the last first-occurrence in the cluster.
    // Use a gap-based approach: walk sorted first occurrences; if there's a gap > threshold,
    // the TOC cluster ends before the gap.
    val minGap = (lines.size / 50).coerceIn(10, 100)
    var tocEndLine = sortedFirstLines.last()
    for (i in 1 until sortedFirstLines.size) {
        if (sortedFirstLines[i] - sortedFirstLines[i - 1] > minGap) {
            tocEndLine = sortedFirstLines[i - 1]
            break
        }
    }
    val tocBoundary = tocEndLine + minGap.coerceAtLeast(5)

    val anchorCodes = multiCodes.mapNotNull { (code, positions) ->
        val sorted = positions.sorted()
        val firstPos = sorted.first()
        // First occurrence must be within the TOC cluster
        if (firstPos > tocBoundary) return@mapNotNull null
        // Find the first occurrence after the TOC boundary
        val anchorPos = sorted.firstOrNull { it > tocBoundary }
        if (anchorPos != null) {
            code to anchorPos
        } else {
            // Fallback: if only 2 occurrences and they're separated by a meaningful gap,
            // treat the second as the anchor even if both are within the "TOC boundary"
            if (sorted.size == 2 && (sorted[1] - sorted[0]) >= minGap) {
                code to sorted[1]
            } else null
        }
    }
    if (anchorCodes.size < 4) return emptyList()

    val sections = mutableListOf<DetectedSection>()
    for ((code, anchorLine) in anchorCodes.sortedBy { it.second }) {
        val titleLine = lines[anchorLine].trim()
        val cleanTitle = titleLine
            .replace("[$code]", "")
            .replace("{$code}", "")
            .trim()
            .trimStart('=', '-', '~', '*', '#', ' ')
            .trimEnd('=', '-', '~', '*', '#', ' ')
            .ifBlank { code }
        sections.add(DetectedSection(cleanTitle, anchorLine, anchorLine + 1))
    }
    return sections
}

/**
 * Strategies B–E combined: separator fences, numbered headings, bracketed headings, ALL CAPS.
 * Processes lines sequentially, preferring fence-based detection.
 */
private fun detectByStructuralPatterns(lines: List<String>): List<DetectedSection> {
    val candidates = mutableListOf<DetectedSection>()
    val usedLines = mutableSetOf<Int>()
    var i = 0
    while (i < lines.size) {
        val trimmed = lines[i].trim()

        // B0: 3-line frame — border / heading / border
        // Detects any bordered heading regardless of specific corner characters.
        if (i + 2 < lines.size && isFrameBorder(trimmed)) {
            val midTrimmed = lines[i + 1].trim()
            val botTrimmed = lines[i + 2].trim()
            if (midTrimmed.count { it.isLetter() } >= 3
                && midTrimmed.length in 3..120
                && isFrameBorder(botTrimmed)
            ) {
                val title = stripOrnaments(midTrimmed)
                if (title.length in 3..80) {
                    candidates.add(DetectedSection(title, i, i + 3))
                    usedLines.addAll(i..i + 2)
                    i += 3
                    continue
                }
            }
        }

        // B1: separator line with heading on the line BELOW
        if (isSeparatorLine(trimmed) && i + 1 < lines.size) {
            val nextTrimmed = lines[i + 1].trim()
            if (nextTrimmed.isNotBlank() && !isSeparatorLine(nextTrimmed) && nextTrimmed.length in 3..120) {
                val contentStart = if (i + 2 < lines.size && isSeparatorLine(lines[i + 2].trim())) i + 3 else i + 2
                candidates.add(DetectedSection(stripOrnaments(nextTrimmed), i, contentStart))
                usedLines.addAll(i until contentStart)
                i = contentStart
                continue
            }
        }

        // B2: heading line ABOVE a separator (only if preceded by blank/separator/start-of-file)
        if (trimmed.isNotBlank() && !isSeparatorLine(trimmed) && trimmed.length in 3..120
            && i + 1 < lines.size && isSeparatorLine(lines[i + 1].trim())
            && (i == 0 || lines[i - 1].trim().isBlank() || isSeparatorLine(lines[i - 1].trim()))
            && i !in usedLines
        ) {
            val contentStart = i + 2
            candidates.add(DetectedSection(stripOrnaments(trimmed), i, contentStart))
            usedLines.addAll(i until contentStart)
            i = contentStart
            continue
        }

        // C: Numbered / Roman-numeral heading (e.g. "1. Introduction", "IV. Walkthrough")
        if (NUMBERED_HEADING_REGEX.matches(trimmed)
            && (i == 0 || lines[i - 1].trim().isBlank() || isSeparatorLine(lines[i - 1].trim()))
            && i !in usedLines
        ) {
            candidates.add(DetectedSection(trimmed, i, i + 1))
            usedLines.add(i)
            i++
            continue
        }

        // C2: Hierarchical numbered heading (e.g. "1.0 Introduction", "2.1.3 Boss Guide")
        val hierMatch = HIERARCHICAL_NUM_REGEX.matchEntire(trimmed)
        if (hierMatch != null
            && (i == 0 || lines[i - 1].trim().isBlank() || isSeparatorLine(lines[i - 1].trim()))
            && i !in usedLines
        ) {
            candidates.add(DetectedSection(trimmed, i, i + 1))
            usedLines.add(i)
            i++
            continue
        }

        // D: [BRACKETED HEADING] on its own line
        val bracketMatch = BRACKET_HEADING_REGEX.matchEntire(trimmed)
        if (bracketMatch != null && i !in usedLines) {
            candidates.add(DetectedSection(bracketMatch.groupValues[1].trim(), i, i + 1))
            usedLines.add(i)
            i++
            continue
        }

        // D2: Ornamental title embedded in separator line (e.g. "----.+.---WALKTHROUGH---.+.----")
        val ornamentalMatch = ORNAMENTAL_TITLE_REGEX.matchEntire(trimmed)
        if (ornamentalMatch != null && i !in usedLines && trimmed.length >= 15
            && trimmed.split(Regex("\\s{3,}")).count { it.isNotBlank() } <= 1
        ) {
            val title = ornamentalMatch.groupValues[1].trim()
            if (title.length in 3..40 && title.any { it.isLetter() }) {
                candidates.add(DetectedSection(title, i, i + 1))
                usedLines.add(i)
                i++
                continue
            }
        }

        // D3: Decorated headings (e.g. "=:-Title-:=", "-:+:LEVEL-1:+:-", "=Title=", "{<>} Title {<>}")
        val decoratedMatch = DECORATED_HEADING_REGEX.matchEntire(trimmed)
        if (decoratedMatch != null && i !in usedLines
            && (i == 0 || lines[i - 1].trim().isBlank() || isSeparatorLine(lines[i - 1].trim()))
            && trimmed.split(Regex("\\s{3,}")).count { it.isNotBlank() } <= 1
        ) {
            val title = decoratedMatch.groupValues[1].trim()
            if (title.length in 3..80 && title.any { it.isLetter() }
                && !isSeparatorLine(title)) {
                candidates.add(DetectedSection(stripOrnaments(title), i, i + 1))
                usedLines.add(i)
                i++
                continue
            }
        }

        // B3: Padded / centered title (e.g. "---  Boss Guide  ---" or "     WALKTHROUGH     ")
        val paddedMatch = PADDED_TITLE_REGEX.matchEntire(trimmed)
        if (paddedMatch != null && i !in usedLines
            && (i == 0 || lines[i - 1].trim().isBlank())
            && (i + 1 >= lines.size || lines[i + 1].trim().isBlank() || isSeparatorLine(lines[i + 1].trim()))
            && trimmed.split(Regex("\\s{3,}")).count { it.isNotBlank() } <= 1
        ) {
            val title = paddedMatch.groupValues[1].trim()
            if (title.length in 3..80) {
                candidates.add(DetectedSection(title, i, i + 1))
                usedLines.add(i)
                i++
                continue
            }
        }

        // E: ALL CAPS standalone line
        if (isAllCapsHeading(trimmed)
            && (i == 0 || lines[i - 1].trim().isBlank() || isSeparatorLine(lines[i - 1].trim()))
            && (i + 1 >= lines.size || lines[i + 1].trim().isBlank() || isSeparatorLine(lines[i + 1].trim()))
            && trimmed.split(Regex("\\s{3,}")).count { it.isNotBlank() } <= 1
            && i !in usedLines
        ) {
            candidates.add(DetectedSection(trimmed, i, i + 1))
            usedLines.add(i)
            i++
            continue
        }

        i++
    }
    return if (candidates.size >= 4) candidates else emptyList()
}

/**
 * Strategy F: Dot-leader TOC matching — find TOC lines like "Section Name.......15",
 * extract section titles, then locate matching headings in the body text.
 */
private fun detectByDotLeaderToc(lines: List<String>): List<DetectedSection> {
    // First pass: collect dot-leader TOC entries (typically clustered in first 20% of file)
    val tocEntries = mutableListOf<String>()
    val tocBoundary = (lines.size * 0.25).toInt().coerceAtLeast(50)
    for (i in 0 until minOf(tocBoundary, lines.size)) {
        val match = DOT_LEADER_TOC_REGEX.matchEntire(lines[i].trim())
        if (match != null) {
            val title = match.groupValues[1].trim()
            if (title.length in 3..60) tocEntries.add(title)
        }
    }
    if (tocEntries.size < 4) return emptyList()

    // Second pass: find matching section headings in the body (after the TOC block)
    val sections = mutableListOf<DetectedSection>()
    for (title in tocEntries) {
        val normalizedTitle = title.lowercase().trim()
        for (j in tocBoundary until lines.size) {
            val lineTrimmed = lines[j].trim()
            val lineNorm = stripOrnaments(lineTrimmed).lowercase().trim()
            if (lineNorm == normalizedTitle && lineTrimmed.length <= title.length + 20) {
                sections.add(DetectedSection(stripOrnaments(lineTrimmed), j, j + 1))
                break
            }
        }
    }
    return if (sections.size >= 4) sections else emptyList()
}

/**
 * Strategy G: Lines followed by 2+ consecutive blank lines mark major section boundaries.
 * The first non-blank line after the gap becomes the section title.
 */
private fun detectByDoubleBlankLines(lines: List<String>): List<DetectedSection> {
    val sections = mutableListOf<DetectedSection>()
    var i = 0
    while (i < lines.size) {
        // Look for 2+ consecutive blank lines
        if (lines[i].trim().isBlank()) {
            var blankCount = 0
            var j = i
            while (j < lines.size && lines[j].trim().isBlank()) { blankCount++; j++ }
            if (blankCount >= 2 && j < lines.size) {
                val titleLine = lines[j].trim()
                if (titleLine.isNotBlank() && titleLine.length in 3..120) {
                    sections.add(DetectedSection(stripOrnaments(titleLine), j, j + 1))
                }
            }
            i = j
            continue
        }
        i++
    }
    return if (sections.size >= 4) sections else emptyList()
}

private val SPACED_LETTERS_REGEX = Regex("(?<![A-Za-z])([A-Za-z])(?: ([A-Za-z])){2,}(?![A-Za-z])")

internal fun stripOrnaments(text: String): String {
    return text.trim()
        .trimStart('=', '-', '~', '*', '#', '+', '|', '/', '\\', ':', ' ')
        .trimEnd('=', '-', '~', '*', '#', '+', '|', '/', '\\', ':', ' ')
        .trim()
        // Collapse spaced-out letters (S E C T I O N → SECTION) before multi-space collapse
        .replace(SPACED_LETTERS_REGEX) { it.value.replace(" ", "") }
        .replace(Regex("\\s{2,}"), " ")
        .ifBlank { text.trim() }
}

private fun isAllCapsHeading(text: String): Boolean {
    if (text.length !in 5..80) return false
    val alphaCount = text.count { it.isLetter() }
    if (alphaCount < 3) return false
    return text.all { !it.isLetter() || it.isUpperCase() }
}

fun buildImageSearchUrlForEngine(engine: SearchEngine, query: String): String {
    val encoded = java.net.URLEncoder.encode(query, "UTF-8")
    return when (engine) {
        SearchEngine.GOOGLE -> "https://www.google.com/search?q=$encoded&tbm=isch"
        SearchEngine.DUCKDUCKGO -> "https://duckduckgo.com/?q=$encoded&iax=images&ia=images"
        SearchEngine.BING -> "https://www.bing.com/images/search?q=$encoded"
        SearchEngine.BRAVE -> "https://search.brave.com/search?q=$encoded"
        SearchEngine.STARTPAGE -> "https://www.startpage.com/search?q=$encoded"
        SearchEngine.QWANT -> "https://www.qwant.com/?q=$encoded"
        SearchEngine.ECOSIA -> "https://www.ecosia.org/search?q=$encoded"
        SearchEngine.BAIDU -> "https://www.baidu.com/s?wd=$encoded"
        SearchEngine.YANDEX -> "https://yandex.com/search/?text=$encoded"
    }
}

fun homeUrlForEngine(engine: SearchEngine): String = when (engine) {
    SearchEngine.GOOGLE -> "https://www.google.com"
    SearchEngine.DUCKDUCKGO -> "https://duckduckgo.com"
    SearchEngine.BRAVE -> "https://search.brave.com"
    SearchEngine.STARTPAGE -> "https://www.startpage.com"
    SearchEngine.QWANT -> "https://www.qwant.com"
    SearchEngine.ECOSIA -> "https://www.ecosia.org"
    SearchEngine.BING -> "https://www.bing.com"
    SearchEngine.BAIDU -> "https://www.baidu.com"
    SearchEngine.YANDEX -> "https://yandex.com"
}

fun isSearchUrlForEngine(engine: SearchEngine, url: String): Boolean {
    val prefixes = listOf(
        "https://www.google.com/search?q=",
        "https://duckduckgo.com/?q=",
        "https://search.brave.com/search?q=",
        "https://www.startpage.com/search?q=",
        "https://www.qwant.com/?q=",
        "https://www.ecosia.org/search?q=",
        "https://www.bing.com/search?q=",
        "https://www.baidu.com/s?wd=",
        "https://yandex.com/search/?text="
    )
    return prefixes.any { prefix -> url.startsWith(prefix) }
}

fun unescapeJsonString(jsonString: String?): String {
    if (jsonString == null || jsonString.isBlank()) return ""
    try {
        val trimmed = jsonString.trim()
        val jsonToParse = if (trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            "{\"value\":$trimmed}"
        } else {
            "{\"value\":\"$trimmed\"}"
        }
        val jsonObject = JSONObject(jsonToParse)
        return jsonObject.getString("value")
    } catch (e: Exception) {
        android.util.Log.e("GuideBrowser", "Error unescaping JSON string using JSONObject, trying manual unescape", e)
        try {
            var result = jsonString.trim()
            if (result.startsWith("\"") && result.endsWith("\"")) {
                result = result.removeSurrounding("\"")
            }
            result = result.replace("\\\\u([0-9a-fA-F]{4})".toRegex()) { matchResult ->
                try {
                    val codePoint = matchResult.groupValues[1].toInt(16)
                    if (codePoint in 0..0x10FFFF) codePoint.toChar().toString() else matchResult.value
                } catch (e: Exception) { matchResult.value }
            }
            result = result.replace("\\\"", "\"")
            result = result.replace("\\\\", "\\")
            result = result.replace("\\n", "\n")
            result = result.replace("\\r", "\r")
            result = result.replace("\\t", "\t")
            result = result.replace("\\/", "/")
            return result
        } catch (e2: Exception) {
            android.util.Log.e("GuideBrowser", "Manual unescape also failed", e2)
            return jsonString
        }
    }
}

fun tryFullSizeImageUrl(url: String): String {
    if (url.isBlank()) return url
    val lower = url.lowercase()
    if (!lower.contains("google") && !lower.contains("gstatic") && !lower.contains("googleusercontent")) return url
    return url.replace(Regex("=s\\d+"), "=s0")
}

fun resolveBestImageUrlForDownload(
    webView: WebView?,
    pageUrl: String,
    onResolved: (String) -> Unit
) {
    if (webView == null || pageUrl.isBlank()) {
        onResolved(pageUrl)
        return
    }
    val coords = webView.getTag(TAG_KEY_IMAGE_RESOLVE_COORDS) as? FloatArray
    val usedCoords = coords != null && coords.size >= 2
    val density = webView.context.resources.displayMetrics.density
    val script = if (usedCoords) {
        val x = coords!![0]
        val y = coords[1]
        webView.setTag(TAG_KEY_IMAGE_RESOLVE_COORDS, null)
        "(function(){ var viewX = $x; var viewY = $y; var density = $density; var x = viewX / density; var y = viewY / density; var el = document.elementFromPoint(x, y); var img = (el && el.closest && el.closest('img')) || (el && el.tagName === 'IMG' ? el : null); if (!img) return null; if (typeof window.__getBestImageUrl === 'function') { var best = window.__getBestImageUrl(img); return (best && best.trim()) ? best : (img.src || img.currentSrc || '').trim() || null; } return (img.src || img.currentSrc || '').trim() || null; })()"
    } else {
        val escaped = JSONObject.quote(pageUrl)
        "(function(){ try { var u = $escaped; if (typeof window.__resolveBestImageUrlFromPageUrl === 'function') return window.__resolveBestImageUrlFromPageUrl(u); return u; } catch(e) { return null; } })()"
    }
    webView.evaluateJavascript(script) { result ->
        val raw = result?.trim() ?: ""
        var resolved = when {
            raw.isEmpty() || raw == "null" -> pageUrl
            else -> unescapeJsonString(raw).trim().takeIf { it.isNotBlank() } ?: pageUrl
        }
        resolved = tryFullSizeImageUrl(resolved)
        android.os.Handler(android.os.Looper.getMainLooper()).post { onResolved(resolved) }
    }
}

fun toFileUrl(filePath: String): String =
    if (filePath.startsWith("file://")) filePath else "file://$filePath"

fun fromFileUrl(fileUrl: String): String =
    if (fileUrl.startsWith("file://")) fileUrl.removePrefix("file://").substringBefore("?") else fileUrl

fun validateFileExists(fileUrl: String): Boolean = try {
    val filePath = fromFileUrl(fileUrl)
    val file = File(filePath)
    file.exists() && file.isFile
} catch (e: Exception) {
    false
}

fun isImageOrNonDocumentUrl(url: String?): Boolean {
    if (url.isNullOrBlank()) return false
    val lower = url.lowercase()
    return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") ||
        lower.endsWith(".gif") || lower.endsWith(".webp") || lower.endsWith(".bmp") ||
        lower.endsWith(".svg") || lower.contains("/imgres") || lower.contains("imgurl=")
}

fun isImageOrImageResultUrl(url: String?): Boolean {
    if (url.isNullOrBlank()) return false
    if (isImageOrNonDocumentUrl(url)) return true
    val lower = url.lowercase()
    return lower.contains("search.brave.com") &&
        (lower.contains("redirect") || lower.contains("safe") || lower.contains("img") || lower.contains("image"))
}

/**
 * Resolves the artwork URI for the landing page into a File inside [guideDir],
 * copying the source bytes if needed. Handles only the two URI shapes any
 * caller produces today (file:// and content://). Returns null on missing
 * source, unsupported scheme, or copy failure.
 */
internal fun resolveLandingArtworkFile(
    context: Context,
    artworkPath: String?,
    guideDir: File
): File? {
    if (artworkPath.isNullOrBlank()) return null
    val uri = Uri.parse(artworkPath)
    return when (uri.scheme) {
        "file" -> {
            val sourceFile = uri.path?.let(::File)
            if (sourceFile == null || !sourceFile.exists()) {
                android.util.Log.w("GuideBrowser", "file:// artwork missing: $artworkPath")
                null
            } else {
                try {
                    val target = File(guideDir, sourceFile.name)
                    if (!target.exists() || target.lastModified() < sourceFile.lastModified()) {
                        sourceFile.copyTo(target, overwrite = true)
                    }
                    target
                } catch (e: Exception) {
                    android.util.Log.w("GuideBrowser", "Failed to copy file:// artwork for landing page", e)
                    null
                }
            }
        }
        "content" -> {
            try {
                val target = File(guideDir, "landing-artwork.png")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    java.io.FileOutputStream(target).use { output -> input.copyTo(output) }
                }
                if (target.exists()) target else {
                    android.util.Log.w("GuideBrowser", "SAF artwork copy produced no file")
                    null
                }
            } catch (e: Exception) {
                android.util.Log.w("GuideBrowser", "Failed to copy SAF artwork for landing page", e)
                null
            }
        }
        else -> {
            android.util.Log.w("GuideBrowser", "unsupported artwork URI scheme: ${uri.scheme}")
            null
        }
    }
}

/**
 * Prepare and return the game landing page file URL. Call from both root screen and WebViewContent.
 * [recentSearches] optional list (e.g. last 3 from search history) to show in "Recent searches" section.
 */
fun getLandingPageUrl(
    context: Context,
    vaultId: String,
    artworkPath: String?,
    entries: List<LandingEntry>,
    recentSearches: List<String> = emptyList(),
    savedLinks: List<com.twinlore.app.domain.SavedTab> = emptyList(),
    paletteBgHex: String = "#000000",
    paletteFgHex: String = "#FFFFFF",
    gameName: String? = null
): String? {
    return try {
        // Vault directories no longer exist; landing pages now live in
        // app cache, scoped per vault id. They're regenerated each call anyway.
        val guideDir = File(context.cacheDir, "landing/$vaultId").apply { mkdirs() }
        val landingPageFile = File(guideDir, "landing.html")
        if (landingPageFile.exists()) landingPageFile.delete()
        val htmlContent = context.assets.open("game-landing.html").bufferedReader().use { it.readText() }
            // Use the vault's game name as the document title — this is what
            // the WebView reports via `wv.title` and what gets persisted onto
            // the browser tab row, so the switcher / top bar display the
            // game name instead of "Game Content Hub".
            .replace(
                "<title>Game Content Hub</title>",
                "<title>" + (gameName?.takeIf { it.isNotBlank() }?.let { android.text.Html.escapeHtml(it) } ?: "Game") + "</title>"
            )
        val cssContent = context.assets.open("game-landing.css").bufferedReader().use { it.readText() }
        val quickSearchHtml = buildLandingQuickSearchTemplatesHtml(entries)
        val recentSearchesHtml = buildRecentSearchesHtml(recentSearches)
        var htmlWithCss = htmlContent.replace(
            "<link rel=\"stylesheet\" href=\"game-landing.css\">",
            "<style>$cssContent</style>"
        )
        htmlWithCss = htmlWithCss.replace(
            "<!-- QUICK_SEARCH_TEMPLATES -->",
            quickSearchHtml
        )
        htmlWithCss = htmlWithCss.replace(
            "<!-- RECENT_SEARCHES -->",
            recentSearchesHtml
        )
        val savedLinksHtml = com.twinlore.app.data.buildSavedLinksHtml(savedLinks)
        htmlWithCss = htmlWithCss.replace(
            "<!-- SAVED_LINKS -->",
            savedLinksHtml
        )
        // Hide the section entirely when empty so the header doesn't render.
        if (savedLinksHtml.isBlank()) {
            htmlWithCss = htmlWithCss.replace(
                "<section class=\"saved-links-section\" id=\"saved-links-section\">",
                "<section class=\"saved-links-section\" id=\"saved-links-section\" style=\"display: none;\">"
            )
        }
        val guideArtworkFile = resolveLandingArtworkFile(context, artworkPath, guideDir)
        val finalHtml = if (guideArtworkFile != null) {
            val artworkFileUrl = "file://${guideArtworkFile.absolutePath}"
            htmlWithCss
                .replace("src=\"placeholder-game-art.jpg\"", "src=\"$artworkFileUrl\"")
                .replace("placeholder-game-art.jpg", artworkFileUrl)
                .replace(
                    "id=\"central-image\" style=\"display: none;\"",
                    "id=\"central-image\""
                )
        } else {
            htmlWithCss
                .replace(
                    "src=\"placeholder-game-art.jpg\"",
                    "src=\"data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='1' height='1'%3E%3Crect fill='transparent' width='1' height='1'/%3E%3C/svg%3E\""
                )
                .replace(
                    "id=\"central-image\"",
                    "id=\"central-image\" style=\"display: none;\""
                )
        }
        // Inject palette theme colors into the landing page.
        // Themes text, buttons, overlay tint, and body background while
        // preserving the blurred artwork image itself.
        val paletteStyle = "<style>" +
            "body,html{background-color:${paletteBgHex}!important}" +
            ".background-overlay{background:${paletteBgHex}40!important}" +
            ".background-image{background:linear-gradient(135deg,${paletteBgHex},${paletteBgHex})!important;filter:blur(16px) brightness(0.5)!important}" +
            ".content-container,.landing-scroll,.quick-search-section,.recent-searches-section{background-color:transparent!important}" +
            ".section-title,.section-desc,.quick-search-label,.recent-searches-empty{color:${paletteFgHex}!important}" +
            ".quick-search-item{background:${paletteBgHex}66!important;border-color:${paletteFgHex}26!important;color:${paletteFgHex}!important}" +
            ".quick-search-item:active,.quick-search-item:hover{background:${paletteBgHex}88!important;border-color:${paletteFgHex}4D!important}" +
            ".recent-search-item{background:${paletteBgHex}59!important;border-color:${paletteFgHex}1F!important;color:${paletteFgHex}E6!important}" +
            ".recent-search-item:active,.recent-search-item:hover{background:${paletteBgHex}80!important}" +
            "</style>"
        val themedHtml = finalHtml.replace("</head>", "$paletteStyle</head>")
        landingPageFile.writeText(themedHtml, Charsets.UTF_8)
        "file://${landingPageFile.absolutePath}?v=${System.currentTimeMillis()}"
    } catch (e: Exception) {
        android.util.Log.e("GuideBrowser", "Error preparing landing page", e)
        null
    }
}

/**
 * Centers the Nth occurrence of [label] text in the WebView viewport. Falls
 * back to [fallbackScrollY] if the text isn't found. Optionally applies a
 * brief text-color + bold flash to the matched text so the user can see what
 * they landed on (page-group viewer calls this after cross-entry navigation).
 *
 * @param flashFocusHex when non-null, the matched text is coloured with this
 *   hex value (!important) and bolded for ~1s, then transitions back to the
 *   palette colour. No flash fires on the fallback path.
 * @param alignAbove when > 0, scroll so the match top is this many px below
 *   the viewport top (useful to avoid floating app bars). When 0 (default)
 *   the match is centred in the viewport.
 */
fun scrollToMarkerByText(
    webView: WebView?,
    label: String,
    fallbackScrollY: Int,
    occurrenceIndex: Int = 0,
    flashFocusHex: String? = null,
    alignAbove: Int = 0,
    onScrollPosition: (Int) -> Unit
) {
    if (webView == null) {
        onScrollPosition(fallbackScrollY)
        return
    }
    val searchText = label.trim().take(80)
        .replace("\\", "\\\\")
        .replace("'", "\\'")
        .replace("\r", "")
        .replace("\n", " ")
    if (searchText.isBlank()) {
        webView.evaluateJavascript("window.scrollTo(0, $fallbackScrollY);", null)
        onScrollPosition(fallbackScrollY)
        return
    }
    val useNth = occurrenceIndex.coerceIn(0, Int.MAX_VALUE)
    // Flash colour interpolated directly into the JS literal. Empty string
    // disables the flash branch entirely so the fallback path stays silent.
    val flashHexLiteral = flashFocusHex?.replace("'", "")?.replace("\\", "") ?: ""
    val script = """
        (function() {
            function isTextNodeVisible(tn) {
                var el = tn.parentElement;
                if (!el) return false;
                while (el && el !== document.body) {
                    var tag = (el.tagName || '').toUpperCase();
                    if (tag === 'SCRIPT' || tag === 'STYLE' || tag === 'NOSCRIPT' || tag === 'HEAD') return false;
                    var cs = window.getComputedStyle(el);
                    if (cs.display === 'none' || cs.visibility === 'hidden') return false;
                    el = el.parentElement;
                }
                return true;
            }
            var query = '$searchText';
            var fallbackScrollY = $fallbackScrollY;
            var useNth = $useNth;
            var flashHex = '$flashHexLiteral';
            var queryLower = query.toLowerCase();
            if (!query || query.trim() === '') {
                window.scrollTo(0, fallbackScrollY);
                return String(fallbackScrollY);
            }
            var walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT, null, false);
            var textNode;
            var candidates = [];
            while (textNode = walker.nextNode()) {
                if (!isTextNodeVisible(textNode)) continue;
                var text = textNode.textContent;
                if (!text) continue;
                var nodeTextLower = text.toLowerCase();
                var idx = 0;
                while ((idx = nodeTextLower.indexOf(queryLower, idx)) !== -1) {
                    var top = textNode.parentElement ? (function() {
                        var r = document.createRange();
                        r.setStart(textNode, idx);
                        r.setEnd(textNode, idx + query.length);
                        return r.getBoundingClientRect().top + window.pageYOffset;
                    })() : 0;
                    candidates.push({ textNode: textNode, start: idx, end: idx + query.length, top: top });
                    idx += query.length;
                }
            }
            if (candidates.length === 0) {
                window.scrollTo(0, fallbackScrollY);
                return String(window.pageYOffset || fallbackScrollY);
            }
            var best;
            if (useNth > 0 && useNth <= candidates.length) {
                best = candidates[useNth - 1];
            } else {
                candidates.sort(function(a, b) {
                    return Math.abs(a.top - fallbackScrollY) - Math.abs(b.top - fallbackScrollY);
                });
                best = candidates[0];
            }
            var range = document.createRange();
            range.setStart(best.textNode, best.start);
            range.setEnd(best.textNode, best.end);
            var rect = range.getBoundingClientRect();
            var alignAbove = $alignAbove;
            var targetY;
            if (alignAbove > 0) {
                targetY = window.pageYOffset + rect.top - alignAbove;
            } else {
                targetY = window.pageYOffset + rect.top - (window.innerHeight / 2) + (rect.height / 2);
            }
            targetY = Math.max(0, Math.min(targetY, document.documentElement.scrollHeight - window.innerHeight));
            window.scrollTo(0, targetY);
            // Wrap JUST the matched word(s) in a span so the flash hugs the
            // text instead of tinting the whole containing block. The range
            // is wholly inside one text node, so surroundContents is safe.
            // Text-color flash (not background) so it beats palette CSS that
            // applies `color: … !important` to `*,span`.
            if (flashHex && flashHex.length > 0) {
                try {
                    var flashRange = document.createRange();
                    flashRange.setStart(best.textNode, best.start);
                    flashRange.setEnd(best.textNode, best.end);
                    var span = document.createElement('span');
                    span.style.cssText = 'color:' + flashHex + ' !important;transition:color 1s ease-out';
                    flashRange.surroundContents(span);
                    setTimeout(function() {
                        span.style.setProperty('color', 'inherit', 'important');
                    }, 100);
                    setTimeout(function() {
                        var parent = span.parentNode;
                        if (parent) {
                            parent.replaceChild(document.createTextNode(span.textContent), span);
                            parent.normalize();
                        }
                    }, 1500);
                } catch (e) { /* surroundContents threw — silent fail */ }
            }
            return String(window.pageYOffset);
        })();
    """.trimIndent()
    webView.evaluateJavascript(script) { result ->
        val scrollY = result?.removeSurrounding("\"")?.toIntOrNull() ?: fallbackScrollY
        onScrollPosition(scrollY)
    }
}

/**
 * Build the IIFE that applies scroll-marker highlights to the page DOM.
 * Used in two ways:
 *  - injected via [highlightScrollMarkerInPage] after page load (handles
 *    mid-session marker changes), and
 *  - spliced into the HTML as an inline `<script>` before
 *    `loadDataWithBaseURL` so initial highlights appear with the first
 *    frame instead of after a perceptible delay.
 */
internal fun buildScrollMarkerHighlightJs(
    markers: List<ScrollMarker>,
    focusColorHex: String,
    focusContrastHex: String
): String {
    val markersJson = if (markers.isEmpty()) "[]" else JSONArray(markers.map { m ->
        JSONObject().put("label", m.label.trim().take(80)).put("occurrenceIndex", m.occurrenceIndex.coerceIn(0, Int.MAX_VALUE))
    }).toString()
    return """
        (function() {
            var markers = $markersJson;
            var focusHex = '$focusColorHex';
            var focusContrastHex = '$focusContrastHex';
            document.querySelectorAll('.scroll-marker-highlight').forEach(function(el) {
                var parent = el.parentNode;
                if (parent) {
                    var textNode = document.createTextNode(el.textContent);
                    parent.replaceChild(textNode, el);
                    parent.normalize();
                }
            });
            var styleEl = document.getElementById('scroll-marker-highlight-style');
            if (styleEl) styleEl.remove();
            if (markers.length === 0) return;
            var style = document.createElement('style');
            style.id = 'scroll-marker-highlight-style';
            // !important on background-color/color/box-shadow so the palette
            // theme-injection CSS (which uses *,span{color:...!important}) doesn't
            // override the focus color on highlighted markers.
            style.textContent = '.scroll-marker-highlight { background-color: ' + focusHex + ' !important; color: ' + focusContrastHex + ' !important; border-radius: 4px; padding: 1px 2px; box-shadow: 0 0 0 3px ' + focusHex + '99 !important; }';
            document.head.appendChild(style);
            function isTextNodeVisible(tn) {
                var el = tn.parentElement;
                if (!el) return false;
                while (el && el !== document.body) {
                    var tag = (el.tagName || '').toUpperCase();
                    if (tag === 'SCRIPT' || tag === 'STYLE' || tag === 'NOSCRIPT' || tag === 'HEAD') return false;
                    var cs = window.getComputedStyle(el);
                    if (cs.display === 'none' || cs.visibility === 'hidden') return false;
                    el = el.parentElement;
                }
                return true;
            }
            function findMatch(query, useNth) {
                var queryLower = query.toLowerCase();
                if (!query || query.trim() === '') return null;
                var walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT, null, false);
                var textNode;
                var candidates = [];
                while (textNode = walker.nextNode()) {
                    if (!isTextNodeVisible(textNode)) continue;
                    var text = textNode.textContent;
                    if (!text) continue;
                    var nodeTextLower = text.toLowerCase();
                    var idx = 0;
                    while ((idx = nodeTextLower.indexOf(queryLower, idx)) !== -1) {
                        candidates.push({ textNode: textNode, start: idx, end: idx + query.length });
                        idx += query.length;
                    }
                }
                if (candidates.length === 0) return null;
                if (useNth > 0 && useNth <= candidates.length) return candidates[useNth - 1];
                return candidates[0];
            }
            var results = [];
            for (var i = 0; i < markers.length; i++) {
                var m = markers[i];
                var label = (m.label || '').toString().replace(/\s+/g, ' ').trim();
                var useNth = typeof m.occurrenceIndex === 'number' ? m.occurrenceIndex : 0;
                var best = findMatch(label, useNth);
                if (best) results.push({ textNode: best.textNode, start: best.start, end: best.end });
            }
            results.sort(function(a, b) {
                var r1 = document.createRange(), r2 = document.createRange();
                r1.setStart(a.textNode, a.start); r1.setEnd(a.textNode, a.end);
                r2.setStart(b.textNode, b.start); r2.setEnd(b.textNode, b.end);
                return r1.compareBoundaryPoints(Range.START_TO_START, r2);
            });
            for (var j = results.length - 1; j >= 0; j--) {
                var item = results[j];
                var markerIndex = j;
                try {
                    var range = document.createRange();
                    range.setStart(item.textNode, item.start);
                    range.setEnd(item.textNode, item.end);
                    var span = document.createElement('span');
                    span.className = 'scroll-marker-highlight';
                    span.setAttribute('data-marker-index', String(markerIndex));
                    span.textContent = item.textNode.textContent.substring(item.start, item.end);
                    range.surroundContents(span);
                } catch(e) {
                    var parent = item.textNode.parentNode;
                    if (!parent) continue;
                    var text = item.textNode.textContent;
                    var before = text.substring(0, item.start);
                    var matchText = text.substring(item.start, item.end);
                    var after = text.substring(item.end);
                    var span = document.createElement('span');
                    span.className = 'scroll-marker-highlight';
                    span.setAttribute('data-marker-index', String(markerIndex));
                    span.textContent = matchText;
                    var fragment = document.createDocumentFragment();
                    if (before) fragment.appendChild(document.createTextNode(before));
                    fragment.appendChild(span);
                    if (after) fragment.appendChild(document.createTextNode(after));
                    parent.replaceChild(fragment, item.textNode);
                }
            }
            document.querySelectorAll('.scroll-marker-highlight').forEach(function(span) {
                span.addEventListener('click', function(e) {
                    e.preventDefault();
                    e.stopPropagation();
                    var idx = span.getAttribute('data-marker-index');
                    if (idx !== null && idx !== '' && window.Android && typeof window.Android.onScrollMarkerHighlightClicked === 'function') {
                        window.Android.onScrollMarkerHighlightClicked(parseInt(idx, 10));
                    }
                });
            });
        })();
    """.trimIndent()
}

fun highlightScrollMarkerInPage(
    webView: WebView?,
    markers: List<ScrollMarker>,
    focusedIndex: Int?,
    focusColorHex: String,
    focusContrastHex: String
) {
    if (webView == null) return
    val script = buildScrollMarkerHighlightJs(markers, focusColorHex, focusContrastHex)
    webView.evaluateJavascript(script, null)
}

fun clearScrollMarkerHighlight(webView: WebView?) {
    if (webView == null) return
    webView.evaluateJavascript("""
        (function() {
            document.querySelectorAll('.scroll-marker-highlight').forEach(function(el) {
                var parent = el.parentNode;
                if (parent) {
                    var textNode = document.createTextNode(el.textContent);
                    parent.replaceChild(textNode, el);
                    parent.normalize();
                }
            });
        })();
    """.trimIndent(), null)
}

/** Prefix of the `@font-face` URLs [buildPaletteCss] emits for the bundled fonts. */
internal const val BUNDLED_FONT_URL_PREFIX = "file:///android_asset/fonts/"

/**
 * The bundled font files [buildPaletteCss] is allowed to reference. Serves as
 * the allowlist for [bundledFontResponse]: only these names are ever opened,
 * so the interceptor can never be steered at another asset or out of the
 * `fonts/` directory.
 */
internal val BUNDLED_FONT_ASSETS: Set<String> = setOf(
    "courier_prime.ttf",
    "jetbrains_mono.ttf",
    "press_start_2p.ttf",
    "share_tech_mono.ttf",
    "space_mono.ttf",
)

/**
 * Serve one bundled font out of the APK's assets for a
 * `file:///android_asset/fonts/…` request, or null when [requestUrl] is
 * anything else.
 *
 * Guide documents are loaded from a `file://` cache path, and with
 * `setAllowFileAccessFromFileURLs(false)` every `file:` URL is an opaque
 * origin. A CSS `@font-face` fetch is made in CORS-anonymous mode, so the
 * palette's font request is cross-origin against an origin that sends no
 * allow header, and the engine drops the response — the guide silently falls
 * back to plain monospace. Answering the request here, with an explicit
 * allow-all header, takes the origin question off the table entirely: the
 * bytes are ours, they are the same bytes the asset URL would have served,
 * and nothing about the emitted CSS changes.
 */
internal fun bundledFontResponse(
    context: Context,
    requestUrl: String
): android.webkit.WebResourceResponse? {
    if (!requestUrl.startsWith(BUNDLED_FONT_URL_PREFIX)) return null
    val name = requestUrl.removePrefix(BUNDLED_FONT_URL_PREFIX)
        .substringBefore('?')
        .substringBefore('#')
    if (name !in BUNDLED_FONT_ASSETS) return null
    return try {
        android.webkit.WebResourceResponse(
            "font/ttf",
            null,
            200,
            "OK",
            mapOf(
                "Access-Control-Allow-Origin" to "*",
                "Cache-Control" to "max-age=31536000",
            ),
            context.assets.open("fonts/$name")
        )
    } catch (_: Exception) {
        null
    }
}

/**
 * Build the palette-override CSS rules as a plain CSS string (no `<style>`
 * wrapper, no JS). Used by [buildPaletteCssJs] for runtime DOM injection and
 * by callers that prepend `<style id="palette-override">…</style>` directly to
 * the HTML string they pass to `loadDataWithBaseURL` (kills the flash between
 * initial render and the `onPageFinished` re-injection — the browser parses
 * the CSS before first paint).
 *
 * The `@font-face` URLs it emits are answered by [bundledFontResponse] from
 * the guide WebView's `shouldInterceptRequest`, not by the engine's own
 * `file://` handling. [AppFont.DEFAULT] emits no `@font-face` at all.
 */
internal fun buildPaletteCss(bgHex: String, fgHex: String, font: AppFont): String {
    val fontFace = when (font) {
        AppFont.COURIER ->
            "@font-face{font-family:CourierPrime;src:url(file:///android_asset/fonts/courier_prime.ttf)} "
        AppFont.JETBRAINS ->
            "@font-face{font-family:JetBrainsMono;src:url(file:///android_asset/fonts/jetbrains_mono.ttf)} "
        AppFont.PIXEL ->
            "@font-face{font-family:PressStart2P;src:url(file:///android_asset/fonts/press_start_2p.ttf)} "
        AppFont.GOTHIC ->
            "@font-face{font-family:ShareTechMono;src:url(file:///android_asset/fonts/share_tech_mono.ttf)} "
        AppFont.SPACE ->
            "@font-face{font-family:SpaceMono;src:url(file:///android_asset/fonts/space_mono.ttf)} "
        else -> ""
    }
    val fontFamily = when (font) {
        AppFont.DEFAULT -> "monospace"
        AppFont.COURIER -> "CourierPrime,monospace"
        AppFont.JETBRAINS -> "JetBrainsMono,monospace"
        AppFont.PIXEL -> "PressStart2P,monospace"
        AppFont.GOTHIC -> "ShareTechMono,monospace"
        AppFont.SPACE -> "SpaceMono,monospace"
    }
    // No single/double quotes inside the CSS itself (font names are unquoted) —
    // [buildPaletteCssJs] embeds this string in a double-quoted JS literal.
    //
    // NOTE on text-shadow: this CSS deliberately does NOT touch it. A blanket
    // black shadow made every glyph fuzzy on light palettes. Site-defined
    // shadows that turn into same-colour halos (currentColor resolving to the
    // forced palette foreground — the Zelda guide's "Outro" heading) are
    // handled by the shadow NORMALIZER in [buildPaletteCssJs], which recolors
    // only EXISTING computed shadows to black.
    // The img rule additionally caps oversized images to the content width
    // (max-width:100%, never enlarges) and keeps aspect ratio (height:auto).
    // Saved pages often lose the source stylesheet that constrained images —
    // a GitHub wiki banner or a MediaWiki sprite then overflows the text column
    // and forces horizontal scroll, so its right edge no longer aligns with the
    // text. The body overflow-wrap stops a long unbroken token (URL, hex dump)
    // from doing the same.
    return "${fontFace}*,body,html{background-color:${bgHex}!important;color:${fgHex}!important;font-family:${fontFamily}!important}" +
        " body,html{overflow-wrap:break-word}" +
        " a{color:${fgHex}!important;text-decoration:underline}" +
        " img{filter:none!important;background-color:transparent!important;text-shadow:none!important;max-width:100%!important;height:auto!important}" +
        " table,th,td{border-color:${fgHex}66!important;color:${fgHex}!important}" +
        " p,span,div,li,h1,h2,h3,h4,h5,h6,pre,code,blockquote,strong,em,b,i,u,dt,dd,label,figcaption,summary,details{color:${fgHex}!important}"
}

/**
 * Build a JavaScript snippet that injects palette-override CSS into the page.
 * Called both on palette change (LaunchedEffect) and on every page load
 * (onPageFinished). Shared by the single-page guide viewer and the page-group
 * viewer. For the no-flash initial render, see [buildPaletteCss] and the
 * page-group viewer's `<style>`-prepend in `loadDataWithBaseURL`.
 */
internal fun buildPaletteCssJs(bgHex: String, fgHex: String, font: AppFont): String {
    val css = buildPaletteCss(bgHex, fgHex, font)
    return """(function(){var s=document.getElementById("palette-override");if(!s){s=document.createElement("style");s.id="palette-override";document.head.appendChild(s)}s.textContent="$css";document.body.style.setProperty("background-color","$bgHex","important");document.documentElement.style.setProperty("background-color","$bgHex","important")})();""" +
        SHADOW_NORMALIZER_JS
}

/**
 * Recolors EXISTING site-defined text-shadows to black, preserving offsets,
 * blur, alpha, and multi-shadow lists. Replaces the old blanket
 * `text-shadow:0 1px 2px #000` rule that fuzzed every glyph on light
 * palettes: shadows now appear only where the source page defined them, but
 * always in black so currentColor shadows can't become same-colour halos
 * around the forced palette foreground (the Zelda guide "Outro" heading).
 *
 * Runs after the palette CSS in the same evaluate, so computed shadows
 * already reflect the forced foreground. Idempotency flag on `document`,
 * not `window` — cross-file loads swap the document but keep the window.
 * One pass over body elements; per-element inline override with !important.
 */
internal const val SHADOW_NORMALIZER_JS =
    """(function(){
    try {
        if (document.gtShadowNormalized) return;
        document.gtShadowNormalized = true;
        var els = document.querySelectorAll('body *');
        for (var i = 0; i < els.length; i++) {
            var el = els[i];
            var cs;
            try { cs = window.getComputedStyle(el); } catch (e) { continue; }
            var ts = cs && cs.textShadow;
            if (!ts || ts === 'none') continue;
            var fixed = ts
                .replace(/rgba\(\s*\d+\s*,\s*\d+\s*,\s*\d+\s*,/g, 'rgba(0, 0, 0,')
                .replace(/rgb\(\s*\d+\s*,\s*\d+\s*,\s*\d+\s*\)/g, 'rgb(0, 0, 0)');
            if (fixed !== ts) {
                el.style.setProperty('text-shadow', fixed, 'important');
            }
        }
    } catch (e) {}
})();"""

/** JavaScript snippet that removes the palette-override style element (used when theme injection is toggled off). */
internal const val REMOVE_PALETTE_CSS_JS =
    """(function(){var s=document.getElementById("palette-override");if(s)s.remove();document.body.style.removeProperty("background-color");document.documentElement.style.removeProperty("background-color")})();"""

/** JavaScript to install the selectionchange listener that feeds SelectionRectBridge so the
 *  custom action-mode menu can be positioned next to the selected text. Inject once per page in
 *  onPageFinished.
 *
 *  The idempotency guard is tagged on `document` rather than `window`: page-group cross-file
 *  loads swap the document but keep the window alive, so a window-scoped flag survived loads
 *  and stopped the selectionchange listener from being re-registered on each new document.
 *  Symptom: in the page-group viewer the custom action-mode menu floated at the bottom of the
 *  WebView instead of next to the selection — the listener was bound to a stale document and
 *  the rect never got updated. */
val SELECTION_RECT_JS = """
(function() {
    if (document.__selectionRectInjected) return;
    document.__selectionRectInjected = true;
    function reportSelectionRect() {
        if (typeof SelectionRectBridge === 'undefined' || !SelectionRectBridge.setSelectionRect) return;
        var sel = window.getSelection();
        if (!sel || sel.rangeCount === 0) return;
        var r = sel.getRangeAt(0);
        var rect = r.getBoundingClientRect();
        SelectionRectBridge.setSelectionRect(rect.left, rect.top, rect.right, rect.bottom);
    }
    document.addEventListener('selectionchange', reportSelectionRect);
})();
""".trimIndent()

/** JavaScript to extract table of contents: Steam Guide Index, GameFAQs .ftoc, generic TOC links, h1–h6 headers, or 7 evenly spaced positions. Returns JSON array of {label, scrollY}. */
val TOC_EXTRACTION_JS = """
(function() {
    var out = [];
    function scrollYOf(el) {
        if (!el) return 0;
        var r = el.getBoundingClientRect();
        var pageY = window.pageYOffset || document.documentElement.scrollTop || 0;
        return Math.round(r.top + pageY);
    }
    function addEntry(label, y) {
        label = (label || '').trim().substring(0, 120);
        if (label) out.push({ label: label, scrollY: y });
    }
    var i, el, id, label, target, href, hash, aList;

    // 1) Steam Community Guide: #GuideTableOfContents with .guideSubSectionSelection; target = div.subSection#id
    var steamToc = document.getElementById('GuideTableOfContents');
    if (steamToc) {
        var steamItems = steamToc.querySelectorAll('.guideSubSectionSelection');
        for (i = 0; i < steamItems.length; i++) {
            el = steamItems[i];
            id = (el.id || '').replace('guideSectionSelection_', '');
            if (id === '-1') continue;
            var linkSpan = el.querySelector('.guideSubSectionSelectionLink span');
            label = linkSpan ? (linkSpan.textContent || '').trim() : '';
            if (!label) continue;
            if (id === '0') {
                addEntry(label, 0);
                continue;
            }
            target = document.getElementById(id);
            if (target && target.classList && target.classList.contains('subSection')) {
                addEntry(label, scrollYOf(target));
            }
        }
    }

    if (out.length === 0) {
        // 2) GameFAQs: .ftoc or #faq_toc_header .toc_menu — links with href containing # (full URL or hash)
        var gameFaqsToc = document.querySelector('.ftoc, #faq_toc_header .toc_menu, #faq_toc_header');
        if (gameFaqsToc) {
            aList = gameFaqsToc.querySelectorAll('a[href*="#"]');
            for (i = 0; i < aList.length; i++) {
                var a = aList[i];
                href = (a.getAttribute('href') || '').trim();
                hash = href.indexOf('#') >= 0 ? href.split('#').pop().split('?')[0] : '';
                if (!hash) continue;
                target = document.getElementById(hash) || document.getElementById('user-content-' + hash.toLowerCase()) || document.querySelector('a[name="' + hash.replace(/"/g, '\\\\"') + '"]');
                if (!target) target = document.querySelector('[id="' + hash.replace(/"/g, '\\\\"') + '"]');
                if (target) {
                    label = (a.textContent || '').trim();
                    if (label) addEntry(label, scrollYOf(target));
                }
            }
        }
    }

    if (out.length === 0) {
        // 3) Generic TOC: #toc, .toc, etc. — links with href^="#"
        var toc = document.querySelector('#toc, #table-of-contents, .toc, .table-of-contents, [id*="toc"], [class*="toc"]');
        if (toc) {
            aList = toc.querySelectorAll('a[href^="#"]');
            for (i = 0; i < aList.length; i++) {
                var a2 = aList[i];
                href = (a2.getAttribute('href') || '').trim();
                id = href.indexOf('#') >= 0 ? href.split('#')[1] : href;
                if (!id) continue;
                target = document.getElementById(id) || document.getElementById('user-content-' + id.toLowerCase()) || document.querySelector('[id="' + id.replace(/"/g, '\\\\"') + '"]');
                if (target) {
                    label = (a2.textContent || '').trim();
                    if (label) addEntry(label, scrollYOf(target));
                }
            }
        }
    }

    if (out.length === 0) {
        var headers = document.querySelectorAll('h1, h2, h3, h4, h5, h6');
        for (var j = 0; j < headers.length; j++) {
            el = headers[j];
            label = (el.textContent || '').trim();
            if (label) addEntry(label, scrollYOf(el));
        }
    }
    if (out.length <= 3) {
        out = [];
    }
    if (out.length === 0) {
        var h = Math.max(document.body.scrollHeight || 0, document.documentElement.scrollHeight || 0);
        if (h > 0) {
            var step = h / 6;
            var labels = ['Top', 'Section 2', 'Section 3', 'Section 4', 'Section 5', 'Section 6', 'Bottom'];
            for (var k = 0; k <= 6; k++) {
                out.push({ label: labels[k], scrollY: Math.round(k * step) });
            }
        }
    }
    return JSON.stringify(out);
})();
""".trimIndent()

/**
 * Load scroll markers for a bookmark and compute focused index. Call from a coroutine.
 * Returns (markers, focusedIndex) where focusedIndex is null when list is empty.
 */
suspend fun loadScrollMarkersForBookmark(
    vaultId: String,
    bookmarkId: String,
    repository: SavedTabRepository
): Pair<List<ScrollMarker>, Int?> = withContext(Dispatchers.IO) {
    val (markers, focusedId) = repository.getScrollMarkersWithFocusedId(vaultId, bookmarkId)
    val focusedIndex = when {
        markers.isEmpty() -> null
        focusedId != null -> markers.indexOfFirst { it.id == focusedId }.takeIf { it >= 0 } ?: 0
        else -> 0
    }
    Pair(markers, focusedIndex)
}

/**
 * Run TOC extraction JS in the WebView and parse result to [TocEntry] list. Calls [onResult] on main thread.
 */
fun extractTocEntries(webView: WebView?, onResult: (List<TocEntry>) -> Unit) {
    if (webView == null) {
        onResult(emptyList())
        return
    }
    webView.evaluateJavascript(TOC_EXTRACTION_JS) { result ->
        val jsonStr = unescapeJsonString(result)?.trim()?.removeSurrounding("\"") ?: "[]"
        val list = try {
            val arr = JSONArray(jsonStr)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                TocEntry(
                    label = obj.optString("label", "").ifEmpty { "Section ${i + 1}" },
                    scrollY = obj.optInt("scrollY", 0).coerceAtLeast(0)
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
        android.os.Handler(android.os.Looper.getMainLooper()).post { onResult(list.filter { it.label.length <= 50 }) }
    }
}

/**
 * Build JS that walks every `<a href>` in the page-group viewer's saved page and decorates each
 * anchor:
 *
 *  - **Internal** — href matches a saved entry's URL in this group, so tapping will navigate
 *    inside the group viewer. Fragment-insensitive match (the JS strips `#…` before comparing)
 *    so `https://x/a.html#sec` matches an entry saved as `https://x/a.html`. No badge; the link
 *    text is colored with the user's effective focus color ([accentHex]) plus the shared dotted
 *    underline.
 *  - **External** (open_in_new icon, trailing SVG badge) — real `http(s):` link that will hand
 *    off to the browser tab. Same accent color + dotted underline as internal; the badge is the
 *    affordance distinguishing "leaves to browser". Skips `javascript:`, `mailto:`, `tel:`, empty
 *    hrefs, and bare fragments (they're dead on a saved page). See
 *    [com.twinlore.app.ui.screens.offline.EXTERNAL_HREF_CLASSIFIER_JS].
 *
 * The accent color is applied via `style.setProperty(…, 'important')` so it wins over the
 * palette-override stylesheet's `a{color:…!important}` rule. It's baked into the generated JS,
 * so a focus-color change picks up on the next page load — same lifecycle as the rest of the
 * injection.
 *
 * Idempotent: links tagged with `data-pg-local-marked` or `data-pg-external-marked` are skipped,
 * so re-injection on every onPageFinished doesn't accumulate glyphs.
 *
 * Called from PageGroupViewerScreen with `state.entries.map { it.savedHref }`.
 */
fun buildLocalLinkMarkerJs(savedHrefs: List<String>, accentHex: String): String {
    val jsonArray = JSONArray(savedHrefs).toString()
    val externalSvg = org.json.JSONObject.quote(
        com.twinlore.app.ui.screens.offline.EXTERNAL_LINK_SVG_INNER
    )
    val badgeSpanStyle = org.json.JSONObject.quote(
        com.twinlore.app.ui.screens.offline.ANCHOR_BADGE_SPAN_STYLE
    )
    val decorationStyle = org.json.JSONObject.quote(
        ";" + com.twinlore.app.ui.screens.offline.ANCHOR_DECORATION_STYLE
    )
    val accent = org.json.JSONObject.quote(accentHex)
    val externalClassifier =
        com.twinlore.app.ui.screens.offline.EXTERNAL_HREF_CLASSIFIER_JS
    return """
(function() {
    try {
        var saved = $jsonArray;
        // Normalized matching: a.href resolves RELATIVE links against the
        // document base (https://localhost.invalid/), so exact string
        // comparison against the saved pages' original URLs can never match
        // them — and fully-qualified links still differ by www./scheme/
        // trailing slash. Split into host + rest (path+query, fragment
        // dropped), lowercase the host, strip www. and trailing slash; match
        // localhost-resolved links by PATH against the saved pages' paths.
        function gtSplit(u) {
            var m = (u || '').match(/^https?:\/\/([^\/#?]+)([^#]*)/i);
            if (!m) return null;
            var host = m[1].toLowerCase();
            if (host.indexOf('www.') === 0) host = host.substring(4);
            var rest = m[2] || '';
            if (rest.length > 1 && rest.charAt(rest.length - 1) === '/') {
                rest = rest.substring(0, rest.length - 1);
            }
            if (rest === '/') rest = '';
            return { host: host, rest: rest };
        }
        var fullSet = {};
        var pathSet = {};
        for (var i = 0; i < saved.length; i++) {
            var p = gtSplit(saved[i]);
            if (!p) continue;
            fullSet[p.host + p.rest] = true;
            pathSet[p.rest] = true;
        }
        $externalClassifier
        // svgInner is null for internal anchors (accent color only) and the
        // open_in_new markup for external ones.
        function gtDecorate(a, svgInner, markedAttr) {
            if (svgInner) {
                var marker = document.createElement('span');
                // markedAttr ends with '-marked'; the badge span uses '-marker'.
                marker.setAttribute(markedAttr.replace('-marked', '-marker'), '1');
                marker.setAttribute('style', $badgeSpanStyle);
                marker.innerHTML = svgInner;
                a.appendChild(marker);
            }
            var prev = a.getAttribute('style') || '';
            a.setAttribute('style', prev + $decorationStyle);
            // 'important' so the accent wins over the palette-override
            // stylesheet's a{color:…!important} rule.
            a.style.setProperty('color', $accent, 'important');
            // Inline color on the <a> only INHERITS into children — a direct
            // rule on a child (palette CSS on span/font wrappers) always beats
            // inherited color, so the visible text stayed unpainted. Paint
            // every descendant explicitly — including the badge marker span,
            // whose SVG uses fill="currentColor" and so tints to the accent.
            var kids = a.querySelectorAll('*');
            for (var k = 0; k < kids.length; k++) {
                var kid = kids[k];
                if ((kid.tagName || '').toUpperCase() === 'IMG') continue;
                kid.style.setProperty('color', $accent, 'important');
            }
            a.setAttribute(markedAttr, '1');
        }
        var anchors = document.querySelectorAll('a[href]');
        for (var j = 0; j < anchors.length; j++) {
            var a = anchors[j];
            if (a.getAttribute('data-pg-local-marked') === '1') continue;
            if (a.getAttribute('data-pg-external-marked') === '1') continue;
            // Skip image-wrapping anchors universally: <a href="…"><img…></a>
            // shapes get treated as images by the long-press handler, so the
            // link affordance is misleading — and the badge would sit
            // underneath the image inside the anchor box anyway.
            var firstChild = a.firstElementChild;
            if (firstChild && firstChild.tagName === 'IMG') continue;
            var rawHref = a.getAttribute('href') || '';
            if (!rawHref) continue;
            // Internal classification:
            //  - bare '#frag' = same-page anchor → local;
            //  - localhost.invalid-resolved (i.e. the raw href was relative)
            //    → local when its PATH matches a saved page in this group;
            //  - fully-qualified → local when normalized host+path matches.
            var isLocal = false;
            if (rawHref.charAt(0) === '#') {
                isLocal = true;
            } else {
                var rp = gtSplit(a.href || '');
                if (rp) {
                    isLocal = rp.host === 'localhost.invalid'
                        ? pathSet[rp.rest] === true
                        : fullSet[rp.host + rp.rest] === true;
                }
            }
            if (isLocal) {
                gtDecorate(a, null, 'data-pg-local-marked');
                continue;
            }
            // External pass: real http(s) link that hands off to the
            // browser. Skips javascript:, mailto:, tel:, and bare
            // fragments (which on a saved page are dead anchors).
            if (gtClassifyExternal(rawHref)) {
                gtDecorate(a, $externalSvg, 'data-pg-external-marked');
            }
        }
    } catch (e) { /* swallow: visual cue only, never break the page */ }
})();
""".trimIndent()
}

/**
 * Build JS that gates `<iframe>` elements pointing at http(s):// sources based on network state.
 *
 * - When [online] is true, no replacement happens; the WebView is expected to have
 *   `mixedContentMode = MIXED_CONTENT_ALWAYS_ALLOW` so iframes can pull their content live.
 * - When [online] is false, each http(s) iframe is replaced with a small placeholder div so the
 *   user doesn't stare at a broken white box.
 *
 * Idempotent guard: each iframe (and each placeholder) is tagged with `data-pg-iframe-checked="1"`
 * after the first pass. Re-injection on subsequent onPageFinished calls leaves processed nodes
 * alone.
 */
fun buildIframeGateJs(online: Boolean): String {
    val onlineFlag = if (online) "true" else "false"
    return """
(function() {
    try {
        var online = $onlineFlag;
        function ytId(src) {
            // youtube.com/embed/VID, youtube.com/watch?v=VID, youtu.be/VID, youtube-nocookie.com/embed/VID
            var m = src.match(/(?:youtube(?:-nocookie)?\.com\/embed\/|youtu\.be\/|youtube\.com\/watch\?v=)([A-Za-z0-9_-]{6,})/);
            return m ? m[1] : null;
        }
        function buildYtCard(vid, online) {
            // Use the YouTube watch URL so shouldOverrideUrlLoading can detect
            // youtube.com hostnames and route to the YouTube app via Intent.
            var watchUrl = 'https://www.youtube.com/watch?v=' + vid;
            var thumbUrl = 'https://img.youtube.com/vi/' + vid + '/hqdefault.jpg';
            var a = document.createElement('a');
            a.setAttribute('href', watchUrl);
            a.setAttribute('data-pg-iframe-checked', '1');
            a.setAttribute(
                'style',
                'display:block;margin:8px 0;text-decoration:none;color:currentColor;'
            );
            var inner = '';
            if (online) {
                inner +=
                    '<div style="position:relative;width:100%;max-width:480px;aspect-ratio:16/9;background:#000 url(' + thumbUrl + ') center/cover no-repeat;border-radius:8px;overflow:hidden;">' +
                    '  <div style="position:absolute;inset:0;display:flex;align-items:center;justify-content:center;background:rgba(0,0,0,0.25);">' +
                    '    <div style="width:54px;height:54px;border-radius:50%;background:rgba(0,0,0,0.7);display:flex;align-items:center;justify-content:center;">' +
                    '      <svg width="22" height="22" viewBox="0 0 24 24" fill="#fff" aria-hidden="true"><path d="M8 5v14l11-7z"/></svg>' +
                    '    </div>' +
                    '  </div>' +
                    '</div>' +
                    '<div style="padding:6px 2px;font-family:sans-serif;font-size:13px;opacity:0.75;">▶ Watch on YouTube</div>';
            } else {
                inner +=
                    '<div style="border:1px dashed currentColor;padding:12px;border-radius:8px;text-align:center;font-family:sans-serif;font-size:14px;opacity:0.7;">' +
                    '📡 Offline — YouTube video unavailable' +
                    '</div>';
            }
            a.innerHTML = inner;
            return a;
        }
        var iframes = document.querySelectorAll('iframe');
        for (var i = 0; i < iframes.length; i++) {
            var f = iframes[i];
            if (f.getAttribute('data-pg-iframe-checked') === '1') continue;
            // Original src was stashed on data-original-src at save time
            // (AndroidPageGroupSaveDriver.saveWebArchive). The live src is
            // a cid:frame-... reference Chrome's MHT saver produced.
            var origSrc = f.getAttribute('data-original-src') || '';
            var liveSrc = f.getAttribute('src') || f.src || '';
            var bestSrc = /^https?:\/\//i.test(origSrc) ? origSrc :
                          (/^https?:\/\//i.test(liveSrc) ? liveSrc : '');
            if (!bestSrc) {
                f.setAttribute('data-pg-iframe-checked', '1');
                continue;
            }
            if (online) {
                // Restore the original URL onto src so the iframe loads fresh
                // from the network. Adds permissive `allow` + `allowfullscreen`
                // so YouTube / Twitch / Vimeo players can do fullscreen and
                // play with sound. Idempotent via data-pg-iframe-checked.
                //
                // YouTube only: rewrite youtube.com → youtube-nocookie.com so
                // EU embeds skip the consent.youtube.com redirect — that page
                // has X-Frame-Options: SAMEORIGIN and Chromium would refuse to
                // load it with ERR_BLOCKED_BY_RESPONSE, leaving an inline
                // error inside the iframe area. The nocookie host serves the
                // same embed without the consent gate.
                var loadSrc = bestSrc
                    .replace(/^(https?:\/\/)(www\.)?youtube\.com\/embed\//i, '$1www.youtube-nocookie.com/embed/');
                if (f.getAttribute('src') !== loadSrc) f.setAttribute('src', loadSrc);
                if (!f.hasAttribute('allow')) {
                    f.setAttribute('allow', 'accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share');
                }
                if (!f.hasAttribute('allowfullscreen')) f.setAttribute('allowfullscreen', '');
                f.setAttribute('data-pg-iframe-checked', '1');
            } else {
                // Offline: replace with a placeholder + (for YouTube) a
                // tappable thumbnail card so the user can still open in the
                // YouTube app if they come online later.
                var vid = ytId(bestSrc);
                if (vid) {
                    var card = buildYtCard(vid, false);
                    f.parentNode && f.parentNode.replaceChild(card, f);
                } else {
                    var div = document.createElement('div');
                    div.setAttribute('data-pg-iframe-checked', '1');
                    div.setAttribute('style', 'border: 1px dashed currentColor; padding: 12px; text-align: center; opacity: 0.7; margin: 8px 0; font-family: sans-serif; font-size: 14px;');
                    div.textContent = '📡 Offline — ' + bestSrc + ' unavailable';
                    f.parentNode && f.parentNode.replaceChild(div, f);
                }
            }
        }
    } catch (e) { /* swallow */ }
})();
""".trimIndent()
}
