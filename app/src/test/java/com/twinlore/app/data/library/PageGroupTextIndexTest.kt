package com.twinlore.app.data.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PageGroupTextIndexTest {

    private val html = """
        <html><body>
          <h1>Hyrule Castle</h1>
          <p>You arrive at the front gate.</p>
          <h2 id="throne-room">Throne Room</h2>
          <p>The throne room is dimly lit, with a single torch flickering near the rear wall.
             Master sword wielders may notice the loose stone.</p>
          <h2 id="sewers">Sewers</h2>
          <p>The sewers below are flooded after the rains.</p>
          <div id="content">unrelated id — not in the referenced set</div>
        </body></html>
    """.trimIndent()

    @Test
    fun marks_referenced_anchor_ids_in_dom_order() {
        val out = PageGroupTextIndex.buildSidecar(html, referencedAnchorIds = setOf("throne-room", "sewers"))
        val throneIdx = out.indexOf("[[anchor:throne-room]]")
        val sewersIdx = out.indexOf("[[anchor:sewers]]")
        assertTrue(throneIdx >= 0)
        assertTrue(sewersIdx > throneIdx)
    }

    @Test
    fun unreferenced_ids_not_marked() {
        val out = PageGroupTextIndex.buildSidecar(html, referencedAnchorIds = setOf("throne-room"))
        assertTrue("[[anchor:content]]" !in out)
        assertTrue("[[anchor:sewers]]" !in out)
    }

    @Test
    fun output_has_no_html_tags_and_anchor_markers_only() {
        val out = PageGroupTextIndex.buildSidecar(html, referencedAnchorIds = setOf("throne-room"))
        assertTrue("<h2" !in out)
        assertTrue("</p>" !in out)
        assertTrue("dimly lit" in out)
    }

    @Test
    fun empty_anchor_set_yields_pure_text() {
        val out = PageGroupTextIndex.buildSidecar(html, referencedAnchorIds = emptySet())
        assertTrue("[[anchor:" !in out)
        assertTrue("Throne Room" in out)
    }

    @Test
    fun empty_html_returns_empty_string() {
        assertEquals("", PageGroupTextIndex.buildSidecar("", setOf("a")))
    }
}
