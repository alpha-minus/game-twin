package com.twinlore.app.data.library

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/**
 * MHT main-HTML → stripped plain text with `[[anchor:<id>]]` markers
 * inserted at the positions where matching `<* id="…">` elements lived.
 *
 * Used by the cross-page find sheet: a match offset M in the sidecar
 * is attributed to the ToC entry whose fragment's id is the nearest
 * marker offset ≤ M.
 */
object PageGroupTextIndex {

    fun buildSidecar(html: String, referencedAnchorIds: Set<String>): String {
        if (html.isBlank()) return ""
        val doc = Jsoup.parse(html)
        val body = doc.body() ?: return ""
        val sb = StringBuilder()
        walk(body, referencedAnchorIds, sb)
        return collapseWhitespace(sb.toString())
    }

    private fun walk(node: Node, anchorIds: Set<String>, sb: StringBuilder) {
        if (node is Element) {
            val id = node.id()
            if (id.isNotEmpty() && id in anchorIds) {
                if (sb.isNotEmpty() && sb.last() != '\n') sb.append('\n')
                sb.append("[[anchor:").append(id).append("]]")
            }
            // Skip script/style entirely — their textContent is noise.
            val tag = node.tagName().lowercase()
            if (tag == "script" || tag == "style" || tag == "noscript") return
            for (child in node.childNodes()) walk(child, anchorIds, sb)
            // Block-level tags add a newline so collapsed text doesn't run
            // headings into the next paragraph.
            if (BLOCK_TAGS.contains(tag)) sb.append('\n')
        } else if (node is TextNode) {
            sb.append(node.text())
        }
    }

    private val BLOCK_TAGS = setOf(
        "p", "div", "section", "article", "h1", "h2", "h3", "h4", "h5", "h6",
        "li", "ul", "ol", "table", "tr", "td", "th", "blockquote", "pre",
        "header", "footer", "nav", "aside", "br", "hr"
    )

    /** Collapses whitespace WITHOUT touching the anchor markers. */
    private fun collapseWhitespace(s: String): String {
        // Tokenize on the marker boundary so the inside of `[[anchor:…]]`
        // is preserved verbatim and the surrounding text gets collapsed.
        val out = StringBuilder()
        var i = 0
        val marker = "[[anchor:"
        while (i < s.length) {
            val nextMarker = s.indexOf(marker, i)
            if (nextMarker == -1) {
                out.append(collapsePlain(s.substring(i)))
                break
            }
            out.append(collapsePlain(s.substring(i, nextMarker)))
            val close = s.indexOf("]]", nextMarker)
            if (close == -1) {
                out.append(s.substring(nextMarker)); break
            }
            // Ensure a newline before the marker, none after — find engine
            // expects the marker on its own logical line.
            if (out.isNotEmpty() && out.last() != '\n') out.append('\n')
            out.append(s, nextMarker, close + 2)
            out.append('\n')
            i = close + 2
        }
        return out.toString().trim()
    }

    private fun collapsePlain(s: String): String =
        s.replace(Regex("[\\s\\u00A0]+"), " ")
}
