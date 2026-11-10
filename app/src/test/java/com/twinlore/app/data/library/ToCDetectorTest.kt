package com.twinlore.app.data.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests the URL-pattern chapter detector. A link is a chapter under
 * one of three patterns: (a) tail-contains -- its path contains the
 * current page's last 1-2 path segments wrapped as `/<seg>/`;
 * (b) parent-prefix -- its path starts with the current page's parent
 * directory (catches wiki-style sibling pages and content-bound
 * depth-2 hubs); or (c) same-path-different-query -- its path equals
 * the current page's path but its query differs (catches paginated
 * FAQ guides). Whichever pattern yields more matches wins; tie order
 * tail > parent > query. Self-links and fragment-only duplicates
 * collapse out. On bare-host pages the result is empty.
 */
class ToCDetectorTest {

    @Test
    fun prefix_matched_subpath_links_form_chapter_list_in_order() {
        val links = listOf(
            RawLink("Page A", "https://ex.com/walk/page-a/"),
            RawLink("Page B", "https://ex.com/walk/page-b/"),
            RawLink("Page C", "https://ex.com/walk/page-c/")
        )
        val result = ToCDetector.detect(links, "https://ex.com/walk/", "Walkthrough")
        val deduped = result.entries.distinctBy { it.savedHref }
        assertEquals(3, deduped.size)
        assertEquals("Page A", deduped[0].label)
        assertEquals("Page B", deduped[1].label)
        assertEquals("Page C", deduped[2].label)
    }

    @Test
    fun cross_origin_links_excluded() {
        val links = listOf(
            RawLink("Same origin", "https://ex.com/walk/page-a/"),
            RawLink("Other host", "https://other.com/walk/page-b/")
        )
        val result = ToCDetector.detect(links, "https://ex.com/walk/", "Walkthrough")
        val deduped = result.entries.distinctBy { it.savedHref }
        assertEquals(1, deduped.size)
        assertEquals("Same origin", deduped[0].label)
    }

    @Test
    fun self_link_excluded() {
        val links = listOf(
            RawLink("Home (self)", "https://ex.com/walk/"),
            RawLink("Page A", "https://ex.com/walk/page-a/")
        )
        val result = ToCDetector.detect(links, "https://ex.com/walk/", "Walkthrough")
        val deduped = result.entries.distinctBy { it.savedHref }
        assertEquals(1, deduped.size)
        assertEquals("Page A", deduped[0].label)
    }

    @Test
    fun non_subpath_same_string_prefix_excluded() {
        // Current page path is "/walk"; "/walk-tips" shares the leading
        // string but is not a strict subpath. The detector must reject it.
        val links = listOf(
            RawLink("Tips (sibling)", "https://ex.com/walk-tips"),
            RawLink("Page A", "https://ex.com/walk/page-a/")
        )
        val result = ToCDetector.detect(links, "https://ex.com/walk", "Walkthrough")
        val deduped = result.entries.distinctBy { it.savedHref }
        assertEquals(1, deduped.size)
        assertEquals("Page A", deduped[0].label)
    }

    @Test
    fun trailing_slash_differences_normalized() {
        // Current page has trailing slash; link does not. Both must match.
        val links = listOf(
            RawLink("Page A", "https://ex.com/walk/page-a"),
            RawLink("Page B", "https://ex.com/walk/page-b/")
        )
        val result = ToCDetector.detect(links, "https://ex.com/walk/", "Walkthrough")
        assertEquals(2, result.entries.distinctBy { it.savedHref }.size)
    }

    @Test
    fun fragment_only_duplicates_dedupe_keeping_first_text() {
        // /walk/page-b/ and /walk/page-b/#section-x produce identical
        // offline-saved HTML; T5 semantics: both emit TocEntry; dedup
        // via distinctBy(savedHref) collapses to one entry.
        val links = listOf(
            RawLink("Page B", "https://ex.com/walk/page-b/"),
            RawLink("Section X", "https://ex.com/walk/page-b/#section-x")
        )
        val result = ToCDetector.detect(links, "https://ex.com/walk/", "Walkthrough")
        val deduped = result.entries.distinctBy { it.savedHref }
        assertEquals(1, deduped.size)
        assertEquals("Page B", deduped[0].label)
        assertEquals("https://ex.com/walk/page-b/", deduped[0].savedHref)
    }

    @Test
    fun bare_host_returns_empty_chapters() {
        // On https://ex.com/ every same-origin link is technically a child.
        // The bare-host gate fires and the cluster is empty.
        val links = listOf(
            RawLink("Foo", "https://ex.com/foo/"),
            RawLink("Bar", "https://ex.com/bar/")
        )
        val result = ToCDetector.detect(links, "https://ex.com/", "Home")
        assertTrue(result.entries.distinctBy { it.savedHref }.isEmpty())
    }

    @Test
    fun query_string_subpaths_kept_distinct() {
        // Both links are strict subpaths and differ by query string. The
        // dedup key keeps the query, so both remain in the cluster.
        val links = listOf(
            RawLink("Section page 1", "https://ex.com/guide/section?page=1"),
            RawLink("Section page 2", "https://ex.com/guide/section?page=2")
        )
        val result = ToCDetector.detect(links, "https://ex.com/guide", "Guide")
        assertEquals(2, result.entries.distinctBy { it.savedHref }.size)
    }

    @Test
    fun empty_pageUrl_returns_empty_chapters() {
        val result = ToCDetector.detect(
            listOf(RawLink("X", "https://ex.com/foo/")),
            pageUrl = "",
            pageTitle = ""
        )
        assertTrue(result.entries.distinctBy { it.savedHref }.isEmpty())
    }

    @Test
    fun cross_game_id_chapter_links_match_via_path_tail() {
        // Real GameFAQs case: the current page is the GBA Four Swords
        // wrapper around FAQ 77032, but the canonical FAQ lives under
        // the SNES "A Link to the Past" game-id. Chapter URLs share the
        // /faqs/77032/ path-tail with the current page but differ in
        // the platform + game-id portion. Strict prefix would miss
        // these; the tail-contains matcher catches them.
        val links = listOf(
            RawLink(
                "Search for Sahasrahla",
                "https://gamefaqs.gamespot.com/snes/588436-the-legend-of-zelda-a-link-to-the-past/faqs/77032/search-for-sahasrahla"
            ),
            RawLink(
                "The Bomb Battle",
                "https://gamefaqs.gamespot.com/snes/588436-the-legend-of-zelda-a-link-to-the-past/faqs/77032/the-bomb-battle"
            ),
            RawLink(
                "The Tower of Hera",
                "https://gamefaqs.gamespot.com/snes/588436-the-legend-of-zelda-a-link-to-the-past/faqs/77032/the-tower-of-hera"
            )
        )
        val pageUrl = "https://gamefaqs.gamespot.com/gba/561559-the-legend-of-zelda-a-link-to-the-past-four-swords/faqs/77032"
        val result = ToCDetector.detect(links, pageUrl, "A Link to the Past Four Swords FAQ")
        val deduped = result.entries.distinctBy { it.savedHref }
        assertEquals(3, deduped.size)
        assertEquals("Search for Sahasrahla", deduped[0].label)
        assertEquals("The Bomb Battle", deduped[1].label)
        assertEquals("The Tower of Hera", deduped[2].label)
    }

    @Test
    fun ign_wiki_sibling_pages_match_via_parent_prefix() {
        // IGN wiki guides put each chapter as a sibling of the
        // Walkthrough index page rather than a child. Chapter URLs
        // share the /wikis/<game>/ parent path; only the leaf segment
        // differs. The tail-contains pass finds nothing (no link
        // contains /the-legend-of-zelda-a-link-to-the-past/Walkthrough/);
        // the parent-prefix pass catches all siblings.
        val links = listOf(
            RawLink(
                "Eastern Palace",
                "https://www.ign.com/wikis/the-legend-of-zelda-a-link-to-the-past/Eastern_Palace"
            ),
            RawLink(
                "Hyrule Castle",
                "https://www.ign.com/wikis/the-legend-of-zelda-a-link-to-the-past/Hyrule_Castle"
            ),
            RawLink(
                "Sanctuary",
                "https://www.ign.com/wikis/the-legend-of-zelda-a-link-to-the-past/Sanctuary"
            )
        )
        val pageUrl = "https://www.ign.com/wikis/the-legend-of-zelda-a-link-to-the-past/Walkthrough"
        val result = ToCDetector.detect(links, pageUrl, "Walkthrough")
        val deduped = result.entries.distinctBy { it.savedHref }
        assertEquals(3, deduped.size)
        assertEquals("Eastern Palace", deduped[0].label)
        assertEquals("Hyrule Castle", deduped[1].label)
        assertEquals("Sanctuary", deduped[2].label)
    }

    @Test
    fun omegametroid_depth2_sibling_chapters_match_via_slug_parent() {
        // Real omegametroid case: current page is at depth 2
        // (/metroid-zero-mission-walkthrough/zero-mission), and the
        // chapter list lives as siblings sharing the same parent.
        // The parent segment is hyphenated and long, so it qualifies
        // as a slug-like parent and the parent-prefix gate fires
        // despite depth being only 2. Tail-contains finds nothing
        // because no chapter URL contains /zero-mission/.
        val links = listOf(
            RawLink(
                "Return to Zebes",
                "https://omegametroid.com/metroid-zero-mission-walkthrough/return-to-zebes/"
            ),
            RawLink(
                "Kraid's Lair",
                "https://omegametroid.com/metroid-zero-mission-walkthrough/kraids-lair/"
            ),
            RawLink(
                "Ridley's Lair",
                "https://omegametroid.com/metroid-zero-mission-walkthrough/ridleys-lair/"
            )
        )
        val pageUrl = "https://omegametroid.com/metroid-zero-mission-walkthrough/zero-mission/"
        val result = ToCDetector.detect(links, pageUrl, "Zero Mission")
        val deduped = result.entries.distinctBy { it.savedHref }
        assertEquals(3, deduped.size)
        assertEquals("Return to Zebes", deduped[0].label)
        assertEquals("Kraid's Lair", deduped[1].label)
        assertEquals("Ridley's Lair", deduped[2].label)
    }

    @Test
    fun gamefaqs_paginated_chapters_emitted_with_fragment_stripped_href() {
        // Real GameFAQs case: chapter URLs in the sidebar TOC point at
        // ?page=0, ?page=1, ... on the same path as the current page.
        // The self-link guard would have rejected them in the
        // strict-prefix era; the queryMatches pass admits them.
        val links = listOf(
            RawLink(
                "Introduction",
                "https://gamefaqs.gamespot.com/snes/588741-super-metroid/faqs/70645?page=0#Introduction"
            ),
            RawLink(
                "Crateria Revisited",
                "https://gamefaqs.gamespot.com/snes/588741-super-metroid/faqs/70645?page=1#Crateria%20Revisited"
            ),
            RawLink(
                "Lower Norfair",
                "https://gamefaqs.gamespot.com/snes/588741-super-metroid/faqs/70645?page=2#Lower%20Norfair"
            ),
            RawLink(
                "Escape from Zebes",
                "https://gamefaqs.gamespot.com/snes/588741-super-metroid/faqs/70645?page=3#Escape%20from%20Zebes"
            )
        )
        val pageUrl = "https://gamefaqs.gamespot.com/snes/588741-super-metroid/faqs/70645"
        val result = ToCDetector.detect(links, pageUrl, "Super Metroid FAQ")
        val deduped = result.entries.distinctBy { it.savedHref }
        assertEquals(4, deduped.size)
        assertEquals("Introduction", deduped[0].label)
        // Stored href has the fragment stripped (saver hits the page,
        // not the anchor).
        assertEquals(
            "https://gamefaqs.gamespot.com/snes/588741-super-metroid/faqs/70645?page=0",
            deduped[0].savedHref
        )
    }

    @Test
    fun paginated_anchors_flow_through_without_detector_dedup() {
        // Multiple anchors on the same ?page=N each emit a separate
        // TocEntry so the picker can show every section the user sees
        // in the page's TOC. The savedHref is still fragment-stripped so
        // multiple-anchor rows sharing a URL collapse at save time;
        // the picker indicates the grouping via a caption.
        val links = listOf(
            RawLink("Brinstar", "https://ex.com/faqs/123?page=0#Brinstar"),
            RawLink("Boss: Kraid", "https://ex.com/faqs/123?page=0#Boss"),
            RawLink("Crateria", "https://ex.com/faqs/123?page=0#Crateria"),
            RawLink("Lower Norfair", "https://ex.com/faqs/123?page=1#Lower")
        )
        val pageUrl = "https://ex.com/faqs/123"
        val result = ToCDetector.detect(links, pageUrl, "Test FAQ")
        // All four anchors emitted; three share the ?page=0 save target.
        assertEquals(4, result.entries.size)
        assertEquals("Brinstar", result.entries[0].label)
        assertEquals("Boss: Kraid", result.entries[1].label)
        assertEquals("Crateria", result.entries[2].label)
        assertEquals("Lower Norfair", result.entries[3].label)
        // savedHrefs are all fragment-stripped.
        assertEquals("https://ex.com/faqs/123?page=0", result.entries[0].savedHref)
        assertEquals("https://ex.com/faqs/123?page=0", result.entries[1].savedHref)
        assertEquals("https://ex.com/faqs/123?page=0", result.entries[2].savedHref)
        assertEquals("https://ex.com/faqs/123?page=1", result.entries[3].savedHref)
    }

    @Test
    fun depth2_with_generic_parent_does_not_match_unrelated_siblings() {
        // Pages like /wikis/zelda (short, no hyphen) should NOT match
        // every other wiki on the site as siblings. The parent
        // segment fails both the hyphen and length-12 checks, so
        // parent-prefix is null at depth 2. Tail-contains is the only
        // active pass.
        val links = listOf(
            // Sibling under the same /wikis/ parent — should be REJECTED
            // because the parent is too generic for a depth-2 page.
            RawLink(
                "Unrelated other-game wiki",
                "https://example.com/wikis/super-metroid"
            ),
            // A genuine subpath of the current page — should match via
            // tail-contains.
            RawLink(
                "Walkthrough",
                "https://example.com/wikis/zelda/Walkthrough"
            )
        )
        val pageUrl = "https://example.com/wikis/zelda"
        val result = ToCDetector.detect(links, pageUrl, "Zelda Wiki")
        val deduped = result.entries.distinctBy { it.savedHref }
        assertEquals(1, deduped.size)
        assertEquals("Walkthrough", deduped[0].label)
    }

    @Test
    fun pagination_anchors_dropped_so_sequence_follows_toc_order() {
        // Real Dark Souls 3 case: page 0 has top-of-page pagination
        // ("Next Page" -> ?page=1, "Last Page" -> ?page=11) BEFORE
        // the sidebar TOC ("Cathedral of the Deep" -> ?page=1#X). If
        // the detector kept the pagination anchors, ?page=1 and
        // ?page=11 would land in the result before ?page=2..?page=10.
        // Filtering pagination text drops them, so the order is the
        // TOC's order.
        val links = listOf(
            // Top-of-page pagination noise
            RawLink("Next Page", "https://ex.com/faqs/73376?page=1"),
            RawLink("Last Page", "https://ex.com/faqs/73376?page=11"),
            // Sidebar TOC entries, in reading order
            RawLink("Introduction", "https://ex.com/faqs/73376?page=0#Intro"),
            RawLink("Cathedral", "https://ex.com/faqs/73376?page=1#Cathedral"),
            RawLink("Crucifixion Woods", "https://ex.com/faqs/73376?page=2#Woods"),
            RawLink("Bestiary", "https://ex.com/faqs/73376?page=11#Bestiary")
        )
        val pageUrl = "https://ex.com/faqs/73376"
        val result = ToCDetector.detect(links, pageUrl, "Dark Souls 3 FAQ")
        // Pagination anchors dropped (2). Sidebar TOC anchors kept (4),
        // in DOM order so ?page=11 appears LAST (after ?page=2), not
        // second as it would under the unfiltered behaviour.
        assertEquals(4, result.entries.size)
        assertEquals("Introduction", result.entries[0].label)
        assertEquals("Cathedral", result.entries[1].label)
        assertEquals("Crucifixion Woods", result.entries[2].label)
        assertEquals("Bestiary", result.entries[3].label)
    }

    @Test
    fun pagination_only_url_drops_out_entirely() {
        // If a save URL is reachable ONLY via a pagination anchor (no
        // sidebar TOC entry points at it), it does not survive the
        // filter. The picker stays in TOC order; the missing URL is
        // an acceptable trade because the sidebar TOC is authoritative.
        val links = listOf(
            RawLink("Last Page", "https://ex.com/faqs/123?page=12"),
            RawLink("Introduction", "https://ex.com/faqs/123?page=0#Intro"),
            RawLink("Chapter 1", "https://ex.com/faqs/123?page=1#Ch1")
        )
        val pageUrl = "https://ex.com/faqs/123"
        val result = ToCDetector.detect(links, pageUrl, "Test FAQ")
        assertEquals(2, result.entries.size)
        assertEquals("Introduction", result.entries[0].label)
        assertEquals("Chapter 1", result.entries[1].label)
    }

    @Test
    fun parent_prefix_does_not_match_the_parent_url_itself() {
        // Real Neoseeker case: the walkthrough page links to the bare
        // /dark-souls-iii/ game profile in its header. The parent
        // prefix for /dark-souls-iii/walkthrough is /dark-souls-iii/,
        // and a link to /dark-souls-iii/ technically starts with that
        // prefix. The strict-subpath check (linkPath longer than
        // prefix) excludes the parent itself.
        val links = listOf(
            RawLink("Game profile", "https://www.neoseeker.com/dark-souls-iii/"),
            RawLink("Achievements", "https://www.neoseeker.com/dark-souls-iii/Achievements_and_Trophies"),
            RawLink("Covenants", "https://www.neoseeker.com/dark-souls-iii/Covenants/Way_of_Blue")
        )
        val pageUrl = "https://www.neoseeker.com/dark-souls-iii/walkthrough"
        val result = ToCDetector.detect(links, pageUrl, "Dark Souls III Walkthrough")
        val deduped = result.entries.distinctBy { it.savedHref }
        assertEquals(2, deduped.size)
        assertEquals("Achievements", deduped[0].label)
        assertEquals("Covenants", deduped[1].label)
    }

    @Test
    fun mediawiki_special_namespace_links_are_dropped() {
        // Pages under Special:, Talk:, User:, Category:, File:,
        // Template:, Help: are MediaWiki admin / meta surfaces. They
        // are never chapters and should be filtered before any match
        // pass.
        val links = listOf(
            RawLink(
                "Create your walkthrough",
                "https://www.neoseeker.com/dark-souls-iii/Special:CreatePage?type=walkthrough"
            ),
            RawLink(
                "Achievements",
                "https://www.neoseeker.com/dark-souls-iii/Achievements_and_Trophies"
            ),
            RawLink(
                "Talk: Achievements",
                "https://www.neoseeker.com/dark-souls-iii/Talk:Achievements"
            ),
            RawLink(
                "Covenants",
                "https://www.neoseeker.com/dark-souls-iii/Covenants/Way_of_Blue"
            )
        )
        val pageUrl = "https://www.neoseeker.com/dark-souls-iii/walkthrough"
        val result = ToCDetector.detect(links, pageUrl, "Dark Souls III Walkthrough")
        assertEquals(2, result.entries.distinctBy { it.savedHref }.size)
        assertEquals("Achievements", result.entries.distinctBy { it.savedHref }[0].label)
        assertEquals("Covenants", result.entries.distinctBy { it.savedHref }[1].label)
    }

    @Test
    fun anchor_entries_survive_only_when_their_stripped_url_is_a_chapter() {
        val pageUrl = "https://www.zeldadungeon.net/a-link-to-the-past-walkthrough/"
        val pageTitle = "A Link to the Past Walkthrough"
        val raw = listOf(
            RawLink("Index",          "https://www.zeldadungeon.net/a-link-to-the-past-walkthrough/index/"),
            RawLink("Hyrule Castle",  "https://www.zeldadungeon.net/a-link-to-the-past-walkthrough/hyrule-castle/"),
            RawLink("Throne Room",    "https://www.zeldadungeon.net/a-link-to-the-past-walkthrough/hyrule-castle/#throne-room"),
            RawLink("Sewers",         "https://www.zeldadungeon.net/a-link-to-the-past-walkthrough/hyrule-castle/#sewers"),
            RawLink("Eastern Palace", "https://www.zeldadungeon.net/a-link-to-the-past-walkthrough/eastern-palace/"),
            RawLink("Top",            "https://www.zeldadungeon.net/a-link-to-the-past-walkthrough/#top"),
            RawLink("Contact",        "https://www.zeldadungeon.net/contact/")
        )

        val result = ToCDetector.detect(raw, pageUrl, pageTitle)

        // 5 entries survive: Index, Hyrule Castle, Throne Room, Sewers, Eastern Palace.
        assertEquals(
            listOf(
                "https://www.zeldadungeon.net/a-link-to-the-past-walkthrough/index/" to null,
                "https://www.zeldadungeon.net/a-link-to-the-past-walkthrough/hyrule-castle/" to null,
                "https://www.zeldadungeon.net/a-link-to-the-past-walkthrough/hyrule-castle/" to "#throne-room",
                "https://www.zeldadungeon.net/a-link-to-the-past-walkthrough/hyrule-castle/" to "#sewers",
                "https://www.zeldadungeon.net/a-link-to-the-past-walkthrough/eastern-palace/" to null
            ),
            result.entries.map { it.savedHref to it.fragment }
        )
    }

    @Test
    fun duplicate_savedHref_fragment_pairs_collapse() {
        val pageUrl = "https://example.com/walkthrough/"
        val raw = listOf(
            RawLink("First",  "https://example.com/walkthrough/chapter-1/#intro"),
            RawLink("Repeat", "https://example.com/walkthrough/chapter-1/#intro"),
            RawLink("Other",  "https://example.com/walkthrough/chapter-1/#boss")
        )
        val result = ToCDetector.detect(raw, pageUrl, "X")
        val pairs = result.entries.map { it.savedHref to it.fragment }
        assertEquals(
            listOf(
                "https://example.com/walkthrough/chapter-1/" to "#intro",
                "https://example.com/walkthrough/chapter-1/" to "#boss"
            ),
            pairs
        )
    }

    @Test
    fun first_label_wins_on_fragment_dedup() {
        val raw = listOf(
            RawLink("First label",  "https://example.com/walkthrough/c1/#x"),
            RawLink("Second label", "https://example.com/walkthrough/c1/#x")
        )
        val result = ToCDetector.detect(raw, "https://example.com/walkthrough/", "X")
        val xEntry = result.entries.first { it.fragment == "#x" }
        assertEquals("First label", xEntry.label)
    }

    @Test
    fun inline_body_links_dropped_by_standalone_filter() {
        // Real GameFAQs case: the ToC at the top of the page lists 5
        // chapters, each link in its own <li> (ratio ~1.0, standalone =
        // true). Below the ToC, paragraph text contains 20 inline anchor
        // references to those same chapter URLs, each inside a sentence
        // (ratio < 0.3, standalone = false). The Y-cluster heuristic
        // can't separate them because the body links start immediately
        // after the ToC. The standalone filter runs BEFORE clustering
        // and drops the inline references at the source.
        val tocLinks = (1..5).map { i ->
            RawLink("Page $i", "https://x.com/walk/p$i", y = 80 + i * 30, standalone = true)
        }
        val bodyLinks = (1..20).map { i ->
            RawLink("Page $i", "https://x.com/walk/p$i", y = 2000 + i * 100, standalone = false)
        }
        val result = ToCDetector.detect(
            links = tocLinks + bodyLinks,
            pageUrl = "https://x.com/walk/",
            pageTitle = "Walkthrough"
        )
        assertEquals(5, result.entries.size)
    }

    @Test
    fun body_only_page_urls_synthesize_entries() {
        // Top-cluster ToC has chapters on pages 0, 1, 3.
        val tocLinks = listOf(
            RawLink("Story", "https://x.com/walk/?page=0#story", y = 80, standalone = true),
            RawLink("Brinstar", "https://x.com/walk/?page=1#brinstar", y = 120, standalone = true),
            RawLink("Reference", "https://x.com/walk/?page=3#ref", y = 160, standalone = true),
        )
        // Body content links to pages 2 and 4 — not standalone (parent has lots of other text).
        val bodyLinks = listOf(
            RawLink("Energy Tank", "https://x.com/walk/?page=2#energy", y = 2000, standalone = false),
            RawLink("Power Bomb", "https://x.com/walk/?page=4#power", y = 3000, standalone = false),
        )
        val result = ToCDetector.detect(
            links = tocLinks + bodyLinks,
            pageUrl = "https://x.com/walk/",
            pageTitle = "Walkthrough"
        )
        // 3 standalone-anchored entries + 2 synthetic.
        assertEquals(5, result.entries.size)
        assertEquals(3, result.entries.count { !it.synthetic })
        assertEquals(2, result.entries.count { it.synthetic })
        // Save targets = 5 distinct pages (0, 1, 2, 3, 4).
        assertEquals(5, result.entries.distinctBy { it.savedHref }.size)
        // Synthetic labels are generated from the page number.
        val syntheticLabels = result.entries.filter { it.synthetic }.map { it.label }
        assertTrue(syntheticLabels.contains("Page 2") || syntheticLabels.contains("Page 3"))
    }

    @Test
    fun cluster_filter_drops_scattered_anchor_occurrences() {
        // Real Super Metroid case: 7 chapters in a tight ToC cluster at
        // the top of the page (y 120..400), plus another 5 anchor links
        // scattered throughout the body that point at the same chapter
        // URLs. The cluster filter walks chapter-link occurrences in
        // Y order and stops at the first gap above max(5 * median, 250 px);
        // the scattered anchors fall outside the cluster and never emit.
        val cluster = (1..7).map { i ->
            RawLink("Chapter $i", "https://x.com/walk/p$i", y = 80 + i * 40)
        }
        val scattered = listOf(
            RawLink("Chapter 1", "https://x.com/walk/p1", y = 1500),
            RawLink("Chapter 2", "https://x.com/walk/p2", y = 2500),
            RawLink("Chapter 3", "https://x.com/walk/p3", y = 3500),
            RawLink("Chapter 4", "https://x.com/walk/p4", y = 4500),
            RawLink("Chapter 5", "https://x.com/walk/p5", y = 5500)
        )
        val result = ToCDetector.detect(
            links = cluster + scattered,
            pageUrl = "https://x.com/walk/",
            pageTitle = "Walkthrough"
        )
        assertEquals(7, result.entries.size)
        assertEquals(
            listOf("Chapter 1", "Chapter 2", "Chapter 3", "Chapter 4", "Chapter 5", "Chapter 6", "Chapter 7"),
            result.entries.map { it.label }
        )
    }

    // --- fireemblemwod.com site-specific recognizer ----------------------

    @Test
    fun `fireemblemwod hub recognizes english walkthrough chapters in order`() {
        // Game hub page (ENG_fe7.htm) links the full chapter set under
        // /fe7/guiafe7/. The generic tail/parent passes can't relate a
        // root-level hub to /fe7/guiafe7/ children — the site branch does.
        val links = listOf(
            RawLink("Prologue", "https://www.fireemblemwod.com/fe7/guiafe7/ENG_prologofe7.htm"),
            RawLink("Chapter 1", "https://www.fireemblemwod.com/fe7/guiafe7/ENG_capitulo1fe7.htm"),
            RawLink("Chapter 2", "https://www.fireemblemwod.com/fe7/guiafe7/ENG_capitulo2fe7.htm"),
            // Spanish twin (no ENG_ prefix) — excluded.
            RawLink("Español", "https://www.fireemblemwod.com/fe7/guiafe7/capitulo1fe7.htm"),
            // Non-chapter English section pages live directly under /fe7/ (two
            // segments) and carry no cap/prologo marker — excluded.
            RawLink("Characters", "https://www.fireemblemwod.com/fe7/ENG_personajesfe7.htm"),
            RawLink("Items", "https://www.fireemblemwod.com/ENG_fe7.htm"),
            // Cross-host — excluded.
            RawLink("Off site", "https://gamefaqs.gamespot.com/gba/468480-fire-emblem")
        )
        val result = ToCDetector.detect(
            links, "https://www.fireemblemwod.com/ENG_fe7.htm", "Fire Emblem Blazing"
        )
        assertEquals(
            listOf("Prologue", "Chapter 1", "Chapter 2"),
            result.entries.map { it.label }
        )
        assertEquals(
            listOf(
                "https://www.fireemblemwod.com/fe7/guiafe7/ENG_prologofe7.htm",
                "https://www.fireemblemwod.com/fe7/guiafe7/ENG_capitulo1fe7.htm",
                "https://www.fireemblemwod.com/fe7/guiafe7/ENG_capitulo2fe7.htm",
            ),
            result.entries.map { it.savedHref }
        )
    }

    @Test
    fun `fireemblemwod recognizes the varied guide-dir and chapter naming`() {
        // Guide dir and chapter file names differ per game: gaguia/gacapitulo
        // (FE2), guia/cap (FE4/5/9), guia/parte-1-capitulo (FE10). All carry a
        // cap/prologo marker inside /fe<N>/<dir>/ENG_*.htm.
        val links = listOf(
            RawLink("Gaiden 1", "https://www.fireemblemwod.com/fe2/gaguia/ENG_gacapitulo1.htm"),
            RawLink("Thracia 1", "https://www.fireemblemwod.com/fe5/guia/ENG_cap1.htm"),
            RawLink("Radiant 1-1", "https://www.fireemblemwod.com/fe10/guia/ENG_parte-1-capitulo-1.htm"),
            RawLink("SD Prologue", "https://www.fireemblemwod.com/fe11/guia/ENG_capitulo01.htm")
        )
        val result = ToCDetector.detect(
            links, "https://www.fireemblemwod.com/ENG_fe2.htm", "hub"
        )
        assertEquals(4, result.entries.size)
    }

    @Test
    fun `non-fireemblemwod host keeps generic detection`() {
        // The site branch must not leak: a look-alike path on another host still
        // goes through the generic tail/parent passes.
        val links = listOf(
            RawLink("Page A", "https://ex.com/walk/page-a"),
            RawLink("Page B", "https://ex.com/walk/page-b")
        )
        val result = ToCDetector.detect(links, "https://ex.com/walk/", "Walkthrough")
        assertEquals(2, result.entries.distinctBy { it.savedHref }.size)
    }
}
