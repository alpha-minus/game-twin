package com.twinlore.app.data.library

import com.twinlore.app.RepoFiles
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guide sites (omegametroid.com) list reference guides as multi-link rows:
 * `<a>Power Bomb Expansions</a> (<a>By Progression</a>) (<a>By Area</a>)` —
 * inside `<li>`s on the desktop layout and inside `<h3><span>`s on the mobile
 * layout the WebView actually renders. No anchor in such a row reaches the
 * 0.65 text-dominance ratio, so the plain standalone filter dropped every one
 * of them and the guide pages never joined the detected chapter set. The
 * extractor therefore carries a fallback rule: the first TEXT-BEARING anchor
 * of a link-dense parent (combined anchor text >= 0.65 of the parent's text)
 * counts as standalone. The link-density bound keeps prose sentences and
 * list items filtered (their text is mostly non-link), the first-anchor bound
 * keeps the variant links ("By Progression" / "By Area") out so duplicates of
 * the same guide page can't enter the chapter set, and "text-bearing" skips
 * the text-less svg heading-anchor links some themes prepend to rows.
 *
 * The JS runs only inside a WebView, so these pins guard the rule's presence
 * in the asset; behavior is exercised by the Node harness used during
 * development and device verification.
 */
class TocExtractStandaloneRuleTest {

    private val js = RepoFiles.asset("toc_extract.js").readText()

    @Test
    fun link_dense_row_rule_admits_only_the_first_text_bearing_anchor() {
        assertTrue(
            "only the row's first text-bearing anchor may become standalone",
            js.contains("firstTextAnchor === a")
        )
    }

    @Test
    fun link_dense_row_rule_skips_text_less_icon_anchors() {
        assertTrue(
            "text-less icon anchors must not claim the first-anchor slot",
            js.contains("rowText.length >= 2) firstTextAnchor = rowAnchors[j]")
        )
    }

    @Test
    fun link_dense_row_rule_requires_link_dense_parent_text() {
        assertTrue(
            "parent must be mostly link text before the fallback fires",
            js.contains("linkChars / parentText.length) >= 0.65")
        )
    }
}
