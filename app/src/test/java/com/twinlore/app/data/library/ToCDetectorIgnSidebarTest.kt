package com.twinlore.app.data.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests the IGN structured-sidebar chapter pass. IGN wiki walkthroughs render
 * their chapter list as `<button title="...">` elements (no href, client-side
 * React nav), so the URL-pattern detector sees nothing. This pass takes the
 * sidebar button LABELS (in DOM order) and recovers each chapter's URL by an
 * exact-text join to the page's content anchors. The join is self-filtering:
 * tool/header buttons (Find in guide, Walkthrough, …) have no matching content
 * anchor and drop out.
 *
 * Fixture is the real `www.ign.com/wikis/persona-5/Walkthrough` page. Note the
 * page is served under the alias slug `persona-5` while the chapter anchors use
 * the canonical slug `shin-megami-tensei-persona-5` — the sidebar pass doesn't
 * care, which is the whole point.
 */
class ToCDetectorIgnSidebarTest {

    private val pageUrl = "https://www.ign.com/wikis/persona-5/Walkthrough"

    // Sidebar `sidebar-item` titles in DOM order: 5 tool/header buttons, then
    // 13 real chapters.
    private val sidebarLabels = listOf(
        "Find in guide", "Task Search", "Checklists", "Game Info", "Walkthrough",
        "Introduction", "April", "May", "June", "July", "August", "September",
        "October", "November", "December", "January to March", "Palaces", "Secret Boss"
    )

    private val base = "https://www.ign.com/wikis/shin-megami-tensei-persona-5"

    // Content anchors as the JS extractor would emit them (text = a.textContent).
    private val anchors = listOf(
        RawLink("Introduction", "$base/Introduction"),
        RawLink("April", "$base/April"),
        RawLink("May", "$base/May"),
        RawLink("June", "$base/June"),
        RawLink("July", "$base/July"),
        RawLink("August", "$base/August"),
        RawLink("September", "$base/September"),
        RawLink("October", "$base/October"),
        RawLink("November", "$base/November"),
        RawLink("December", "$base/December"),
        RawLink("January to March", "$base/January_to_March"),
        RawLink("Palaces", "$base/Palaces"),
        // Secret Boss only exists as an anchor under the alias slug — still a
        // valid chapter; the pass is slug-agnostic.
        RawLink("Secret Boss", "https://www.ign.com/wikis/persona-5/Secret_Boss"),
        // Self-link to the current page: a "Walkthrough" label must NOT resolve
        // to this (it's the page itself, not a chapter).
        RawLink("Walkthrough", "https://www.ign.com/wikis/persona-5/Walkthrough"),
        // Noise that must be ignored: off-host, a sub-page anchor whose text
        // isn't a sidebar label, and a non-content (game hub) path.
        RawLink("April", "https://example.com/wikis/persona-5/April"),
        RawLink("Kamoshida's Castle", "$base/Kamoshida%27s_Castle"),
        RawLink("Game Info", "https://www.ign.com/games/shin-megami-tensei-persona-5")
    )

    @Test
    fun chapters_resolve_in_sidebar_order_tools_dropped_current_page_included() {
        val entries = ToCDetector.detectIgnSidebar(sidebarLabels, anchors, pageUrl)
        val labels = entries.map { it.label }
        // "Walkthrough" is the current page; it now resolves (self-links are no
        // longer excluded) and keeps its sidebar slot, ahead of Introduction.
        assertEquals(
            listOf(
                "Walkthrough", "Introduction", "April", "May", "June", "July", "August",
                "September", "October", "November", "December", "January to March", "Palaces", "Secret Boss"
            ),
            labels
        )
    }

    @Test
    fun tool_and_header_buttons_are_dropped() {
        val entries = ToCDetector.detectIgnSidebar(sidebarLabels, anchors, pageUrl)
        val labels = entries.map { it.label }.toSet()
        // The current page ("Walkthrough") is kept now; the genuine tool buttons
        // with no matching content anchor still drop out.
        listOf("Find in guide", "Task Search", "Checklists", "Game Info")
            .forEach { assertTrue("$it must be dropped", it !in labels) }
    }

    @Test
    fun resolved_urls_strip_fragment_and_keep_canonical_slug() {
        val entries = ToCDetector.detectIgnSidebar(sidebarLabels, anchors, pageUrl)
        assertEquals("$base/April", entries.first { it.label == "April" }.savedHref)
        assertEquals(
            "https://www.ign.com/wikis/persona-5/Secret_Boss",
            entries.first { it.label == "Secret Boss" }.savedHref
        )
    }

    @Test
    fun current_page_is_included() {
        // The page the user is on must be part of the saved set. Here it's a
        // sidebar item ("Walkthrough"), so it resolves to the page URL itself.
        val entries = ToCDetector.detectIgnSidebar(sidebarLabels, anchors, pageUrl)
        assertTrue(entries.any { it.savedHref == pageUrl })
        // The off-host "April" must still not win over the same-host one.
        assertEquals("$base/April", entries.first { it.label == "April" }.savedHref)
    }

    @Test
    fun current_page_prepended_when_absent_from_sidebar() {
        // User is on a hub page that isn't itself listed in the sidebar. The
        // detected chapters resolve; the current page is prepended so bulk save
        // covers it, labelled from its URL slug.
        val hubUrl = "https://www.ign.com/wikis/persona-5/Walkthrough_Hub"
        val entries = ToCDetector.detectIgnSidebar(sidebarLabels, anchors, hubUrl)
        // Prepended at the front, labelled from its URL slug.
        assertEquals(hubUrl, entries.first().savedHref)
        assertEquals("Walkthrough Hub", entries.first().label)
        // The sidebar chapters still resolve and follow.
        assertEquals("$base/April", entries.first { it.label == "April" }.savedHref)
    }

    @Test
    fun deduped_by_saved_href_first_label_wins() {
        val dupAnchors = anchors + RawLink("April", "$base/April")
        val labelsWithDup = sidebarLabels + "April"
        val entries = ToCDetector.detectIgnSidebar(labelsWithDup, dupAnchors, pageUrl)
        assertEquals(1, entries.count { it.savedHref == "$base/April" })
    }

    @Test
    fun no_chapters_when_no_labels_match() {
        val entries = ToCDetector.detectIgnSidebar(
            listOf("Find in guide", "Task Search", "Checklists"),
            anchors,
            pageUrl
        )
        assertTrue(entries.isEmpty())
    }
}
