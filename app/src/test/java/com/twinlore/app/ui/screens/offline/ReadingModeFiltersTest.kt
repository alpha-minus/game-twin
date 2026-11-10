package com.twinlore.app.ui.screens.offline

import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Pins per-host reading-mode selectors against real saved-page fixtures in
 * `app/src/test/resources/fixtures/reading-mode/`. If a site redesigns the
 * shape we lean on, the matching test fails and points at the entry that
 * needs updating — no silent rot.
 *
 * Each fixture was captured by saving a real page in the app, then
 * extracting the HTML part from the resulting MHT. URLs below come from
 * the MHT `Content-Location` header.
 *
 * Robolectric is here only for Android's `Uri.parse`; no Android views.
 */
@RunWith(RobolectricTestRunner::class)
class ReadingModeFiltersTest {

    // -- Negative cases (no fixture needed) -------------------------------

    @Test
    fun `null url returns null filter`() {
        assertNull(filterForHost(null))
    }

    @Test
    fun `blank url returns null filter`() {
        assertNull(filterForHost(""))
        assertNull(filterForHost("   "))
    }

    @Test
    fun `unknown host returns null filter`() {
        assertNull(filterForHost("https://example.com/anything"))
        // store.steampowered.com is NOT steamcommunity.com — distinct host.
        assertNull(filterForHost("https://store.steampowered.com/app/12345"))
    }

    @Test
    fun `malformed url returns null filter`() {
        // android.net.Uri.parse swallows most input as opaque, but the
        // host extraction returns null for non-hierarchical schemes —
        // we want null filter rather than a thrown exception.
        assertNull(filterForHost("not-a-url-at-all"))
        assertNull(filterForHost("javascript:void(0)"))
    }

    // -- Prelude no-op when filter is null --------------------------------

    @Test
    fun `applyChromeStripPrelude is a no-op for unknown host`() {
        val html = "<html><body><div>hi</div></body></html>"
        assertEquals(html, applyChromeStripPrelude(html, "https://example.com/x"))
        assertEquals(html, applyChromeStripPrelude(html, null))
    }

    @Test
    fun `applyChromeStripPrelude splices script before body close for known host`() {
        val html = "<html><body><div id=\"faqwrap\">guide</div></body></html>"
        val out = applyChromeStripPrelude(html, "https://gamefaqs.gamespot.com/snes/x/faqs/1")
        assertTrue("script id should be present", out.contains("id=\"gt-reading-mode\""))
        // Script lands immediately before </body>, after the div.
        val scriptIdx = out.indexOf("<script id=\"gt-reading-mode\"")
        val bodyClose = out.indexOf("</body>")
        assertTrue(scriptIdx in 0 until bodyClose)
    }

    // -- Per-host fixture pins --------------------------------------------

    @Test
    fun `gamefaqs HTML FAQ resolves and body selector matches`() {
        val url = "https://gamefaqs.gamespot.com/ps4/168566-dark-souls-iii/faqs/73376"
        assertSelectorPasses(url, "gamefaqs", "gamefaqs-html-faq.html")
    }

    @Test
    fun `gamefaqs text FAQ resolves and body selector matches`() {
        val url = "https://gamefaqs.gamespot.com/snes/588741-super-metroid/faqs/39375"
        assertSelectorPasses(url, "gamefaqs", "gamefaqs-text-faq.html")
    }

    @Test
    fun `gamefaqs removeWithin selectors match expected chrome`() {
        val html = loadFixture("gamefaqs-html-faq.html")
        val doc = Jsoup.parse(html)
        assertNotNull("Recommend overlay must exist for removeWithin pin",
            doc.selectFirst("#contrib_rec"))
    }

    @Test
    fun `gamefaqs keepAlso selector matches the guide-info title block`() {
        // The h2.title inside .body.ffaq carries the "Guide and Walkthrough
        // by <author>" line and the PayPal Donate button. Sibling-above
        // of #faqwrap, so without keepAlso the keep-chain hides it.
        val html = loadFixture("gamefaqs-html-faq.html")
        val doc = Jsoup.parse(html)
        val matched = doc.selectFirst(".body.ffaq > h2.title")
        assertNotNull(".body.ffaq > h2.title must exist for keepAlso pin", matched)
        // Sanity: it contains the tipjar form (Donate button).
        assertNotNull("h2.title must wrap the tipjar form",
            matched!!.selectFirst(".tipjar_form"))
    }

    @Test
    fun `gamefaqs keepAlso selector keeps prev-next on paginated FAQs`() {
        // Paginated HTML FAQs have `<ul class="paginate">` above AND
        // below #faqwrap carrying Previous / ToC / Next chevrons.
        // Without keepAlso the keep-chain would hide both as siblings.
        val html = loadFixture("gamefaqs-html-faq-paged.html")
        val doc = Jsoup.parse(html)
        val paginates = doc.select("ul.paginate")
        assertEquals("paginated FAQ has prev/next at top + bottom",
            2, paginates.size)
        // Sanity: each block carries a "Previous" or "Next" link.
        for (p in paginates) {
            assertTrue("ul.paginate must contain Previous/Next text",
                p.text().contains("Previous") || p.text().contains("Next"))
        }
    }

    @Test
    fun `gamefaqs removeWithin hides the ToC button but keeps prev-next`() {
        // In ul.paginate the "Table of Contents" item is an <a> with NO href;
        // Previous / Next carry real hrefs. `ul.paginate a:not([href])` must
        // match exactly the ToC anchor(s) and leave Prev/Next alone.
        val html = loadFixture("gamefaqs-html-faq-paged.html")
        val doc = Jsoup.parse(html)
        val tocButtons = doc.select("ul.paginate a:not([href])")
        assertTrue("at least one hrefless ToC button matched", tocButtons.isNotEmpty())
        for (b in tocButtons) {
            assertTrue("ToC button text", b.text().contains("Table of Contents"))
        }
        // Prev/Next (hrefs) are NOT matched by the removeWithin selector.
        for (a in doc.select("ul.paginate a[href]")) {
            assertTrue("prev/next links survive",
                a.text().contains("Previous") || a.text().contains("Next"))
        }
    }

    @Test
    fun `gamefaqs text FAQ has no ul-paginate (selector is no-op)`() {
        // Text FAQs are single-page (no Prev/Next), so `.paginate` is
        // absent — keepAlso on `ul.paginate` is a clean no-op for them.
        val html = loadFixture("gamefaqs-text-faq.html")
        val doc = Jsoup.parse(html)
        assertEquals("text FAQ has no .paginate elements",
            0, doc.select("ul.paginate").size)
    }

    @Test
    fun `neoseeker walkthrough resolves and body selector matches`() {
        val url = "https://www.neoseeker.com/dark-souls-iii/walkthrough"
        assertSelectorPasses(url, "neoseeker", "neoseeker-walkthrough.html")
    }

    @Test
    fun `omegametroid hub URL hits the hub entry`() {
        val url = "https://omegametroid.com/super-metroid-walkthrough/"
        val filter = filterForHost(url)
        assertNotNull(filter)
        assertEquals("omegametroid", filter!!.name)
        assertTrue("hub entry has no chapter-strip removeWithin",
            filter.removeWithin.isEmpty())
        assertSelectorPasses(url, "omegametroid", "omegametroid.html")
    }

    @Test
    fun `omegametroid chapter URL hits the chapter entry`() {
        val url = "https://omegametroid.com/metroid-zero-mission-walkthrough/return-to-zebes/"
        val filter = filterForHost(url)
        assertNotNull(filter)
        assertEquals("omegametroid-chapter", filter!!.name)
    }

    @Test
    fun `omegametroid stripPreambleAnchorId wires through to the JS payload`() {
        val filter = filterForHost("https://omegametroid.com/super-metroid-walkthrough/a-derelict-ship/")
        assertNotNull(filter)
        assertEquals("omegametroid-chapter", filter!!.name)
        assertEquals("walkthrough", filter.stripPreambleAnchorId)
        val js = buildReadingModeJs(filter)
        assertTrue("preamble JS must carry the walkthrough anchor id",
            js.contains("\"walkthrough\""))
    }

    @Test
    fun `omegametroid mobile-theme fixture matches the dual-selector body`() {
        // WPtouch mobile theme has no `.main-content article` — it wraps
        // the post in `.post.section` instead. The comma-list body
        // selector must match. Anchor-based preamble strip still works
        // because `#walkthrough` is anchor-driven (theme-independent).
        val html = loadFixture("omegametroid-chapter-mobile.html")
        val doc = Jsoup.parse(html)
        // Body selector alternative branches:
        assertNull("mobile theme has no .main-content article",
            doc.selectFirst(".main-content article"))
        assertNotNull("mobile theme wraps post in .post.section",
            doc.selectFirst(".post.section"))
        // Dual selector must return a single hit regardless of theme:
        assertNotNull("dual selector must hit on mobile theme",
            doc.selectFirst(".main-content article, .post.section"))
        // Walkthrough anchor still works.
        val anchor = doc.selectFirst("#walkthrough")
        assertNotNull("mobile fixture must still carry #walkthrough", anchor)
        val heading = anchor!!.parents().toList()
            .firstOrNull { it.tagName().matches(Regex("h[1-6]")) }
        assertNotNull("#walkthrough must be inside a heading", heading)
    }

    @Test
    fun `omegametroid desktop fixtures still match on the dual-selector body`() {
        // Regression check: changing bodySelector from `.main-content
        // article` to `.main-content article, .post.section` mustn't
        // change which element gets picked on the desktop theme.
        for (fixture in listOf("omegametroid-chapter.html", "omegametroid-chapter-h2.html")) {
            val doc = Jsoup.parse(loadFixture(fixture))
            val picked = doc.selectFirst(".main-content article, .post.section")
            assertNotNull("$fixture must match the dual selector", picked)
            assertEquals("$fixture: must resolve to <article>", "article", picked!!.tagName())
        }
    }

    @Test
    fun `OmegaMetroid chapter fixtures both carry id=walkthrough anchor`() {
        // The anchor-based preamble strip leans on WordPress's
        // Auto-Anchor-Links plugin generating `id="walkthrough"` on the
        // heading slug. Verify the marker exists in both heading-level
        // shapes (h3 on Zero Mission, h2 on Super Metroid) — the JS uses
        // closest() to walk up to whichever heading wraps it.
        for (fixture in listOf("omegametroid-chapter.html", "omegametroid-chapter-h2.html")) {
            val doc = Jsoup.parse(loadFixture(fixture))
            val anchor = doc.selectFirst("#walkthrough")
            assertNotNull("$fixture must carry #walkthrough", anchor)
            val heading = anchor!!.parents().toList().firstOrNull { it.tagName().matches(Regex("h[1-6]")) }
            assertNotNull("$fixture: #walkthrough must be inside a heading", heading)
            assertTrue("$fixture: heading text must include Walkthrough",
                heading!!.text().contains("Walkthrough"))
        }
    }

    @Test
    fun `OmegaMetroid SM chapter banner heading is NOT the walkthrough heading`() {
        // Super Metroid Derelict Ship has the chapter banner image inside
        // the FIRST h2 (no anchor id). The "Walkthrough" heading is the
        // SECOND h2. The anchor-based strip lands on the second h2 only,
        // so the banner stays visible. Pins that distinction.
        val doc = Jsoup.parse(loadFixture("omegametroid-chapter-h2.html"))
        val anchor = doc.selectFirst("#walkthrough")!!
        val anchorHeading = anchor.parents().toList().first { it.tagName().matches(Regex("h[1-6]")) }
        val postContent = doc.selectFirst(".post-content")!!
        val headings = postContent.children().toList().filter { it.tagName().matches(Regex("h[1-6]")) }
        assertTrue("first heading is the banner heading (NOT the walkthrough one)",
            headings.indexOf(anchorHeading) >= 1)
        val bannerHeading = headings.first()
        assertNotNull("first heading must contain an <img> (chapter banner)",
            bannerHeading.selectFirst("img"))
    }

    @Test
    fun `default HostFilter has empty stripPreambleAnchorId`() {
        val noStrip = HostFilter(
            name = "x",
            matches = { false },
            bodySelector = "body",
        )
        assertEquals("", noStrip.stripPreambleAnchorId)
        assertTrue(buildReadingModeJs(noStrip).contains("ai=\"\""))
    }

    @Test
    fun `omegametroid chapter-page bottom colorbox is pinned`() {
        val html = loadFixture("omegametroid-chapter.html")
        val doc = Jsoup.parse(html)
        val colorbox = doc.selectFirst("[id^=\"colorbox_main_container\"]")
        assertNotNull("bottom colorbox panel must exist for removeWithin pin",
            colorbox)
    }

    @Test
    fun `bulbapedia resolves and body selector matches`() {
        val url = "https://bulbapedia.bulbagarden.net/wiki/Browse:Walkthroughs"
        assertSelectorPasses(url, "bulbapedia", "bulbapedia.html")
    }

    @Test
    fun `bulbapedia hideButReserveSpace site header matches the bv-header band`() {
        val html = loadFixture("bulbapedia.html")
        val doc = Jsoup.parse(html)
        assertNotNull("header.bv-header must exist for hideButReserveSpace pin",
            doc.selectFirst("header.bv-header"))
    }


    @Test
    fun `MediaWiki firstHeading exists in both wiki fixtures`() {
        // Article title element shared across MediaWiki skins. Pins the
        // keepAlso selector for both bulbapedia + zeldadungeon-wiki.
        for (fixture in listOf("bulbapedia.html", "zeldadungeon-wiki.html")) {
            val doc = Jsoup.parse(loadFixture(fixture))
            assertNotNull("$fixture must have #firstHeading for keepAlso pin",
                doc.selectFirst("#firstHeading"))
        }
    }

    @Test
    fun `bulbapedia removeWithin selectors match expected chrome`() {
        val html = loadFixture("bulbapedia.html")
        val doc = Jsoup.parse(html)
        assertNotNull(".printfooter must exist for removeWithin pin",
            doc.selectFirst(".printfooter"))
        assertNotNull(".catlinks must exist for removeWithin pin",
            doc.selectFirst(".catlinks"))
    }

    // -- Bulbapedia header is hideButReserveSpace, not keepAlso -----------

    @Test
    fun `bulbapedia keepAlso no longer carries header bv-header`() {
        val filter = filterForHost("https://bulbapedia.bulbagarden.net/wiki/Browse:Walkthroughs")
        assertNotNull(filter)
        assertEquals("bulbapedia", filter!!.name)
        assertFalse("header.bv-header must NOT be in keepAlso anymore",
            filter.keepAlso.contains("header.bv-header"))
        // #firstHeading must stay — that's the article title.
        assertTrue("firstHeading must still be in keepAlso",
            filter.keepAlso.contains("#firstHeading"))
    }

    @Test
    fun `bulbapedia hideButReserveSpace carries header bv-header`() {
        val filter = filterForHost("https://bulbapedia.bulbagarden.net/wiki/Browse:Walkthroughs")
        assertNotNull(filter)
        assertEquals(listOf("header.bv-header"), filter!!.hideButReserveSpace)
    }

    @Test
    fun `zeldadungeon wiki removeWithin selectors match expected chrome`() {
        val html = loadFixture("zeldadungeon-wiki.html")
        val doc = Jsoup.parse(html)
        assertNotNull(".navbox must exist for removeWithin pin",
            doc.selectFirst(".navbox"))
        assertNotNull(".printfooter must exist for removeWithin pin",
            doc.selectFirst(".printfooter"))
    }

    @Test
    fun `neoseeker removeWithin selector matches expected chrome`() {
        val html = loadFixture("neoseeker-walkthrough.html")
        val doc = Jsoup.parse(html)
        assertNotNull(".social_share must exist for removeWithin pin",
            doc.selectFirst(".social_share"))
    }

    @Test
    fun `neoseeker keepAlso selector matches the site header`() {
        val html = loadFixture("neoseeker-walkthrough.html")
        val doc = Jsoup.parse(html)
        assertNotNull("#entireheader must exist for keepAlso pin",
            doc.selectFirst("#entireheader"))
    }

    @Test
    fun `zeldadungeon wiki removeWithin includes auto-ToC`() {
        val html = loadFixture("zeldadungeon-wiki.html")
        val doc = Jsoup.parse(html)
        assertNotNull("#toc must exist for removeWithin pin",
            doc.selectFirst("#toc"))
    }

    @Test
    fun `steamcommunity host resolves to hide-only entry`() {
        val filter = filterForHost("https://steamcommunity.com/sharedfiles/filedetails/?id=12345")
        assertNotNull(filter)
        assertEquals("steamcommunity", filter!!.name)
        // Hide-only mode — bodySelector = "body" makes the keep-chain a
        // no-op so wrong removeWithin selectors can't blank the page.
        assertEquals("body", filter.bodySelector)
        assertFalse("removeWithin must declare at least one sidebar selector",
            filter.removeWithin.isEmpty())
    }

    @Test
    fun `steamcommunity removeWithin selectors match the side rail`() {
        val html = loadFixture("steamcommunity-guide.html")
        val doc = Jsoup.parse(html)
        // The right-edge chip ("tab"): always-visible toggle that
        // survives if not explicitly hidden.
        assertNotNull(".responsive_local_menu_tab must exist for removeWithin pin",
            doc.selectFirst(".responsive_local_menu_tab"))
        // The local-menu column wrapper. Tightened class selector — must
        // NOT match the .mainmenu variant which is Steam's site top nav.
        assertNotNull(".responsive_page_menu_ctn.localmenu must exist",
            doc.selectFirst(".responsive_page_menu_ctn.localmenu"))
        // The rail wrapper that includes the ToC + Created-by block.
        assertNotNull("#rightContents must exist for removeWithin pin",
            doc.selectFirst("#rightContents"))
        // The ToC panel itself.
        assertNotNull("#GuideTableOfContents must exist for removeWithin pin",
            doc.selectFirst("#GuideTableOfContents"))
        // Sanity: the mainmenu variant must ALSO exist (so we know our
        // tightened selector is meaningful — there's a sibling to NOT hide).
        assertNotNull(".responsive_page_menu_ctn.mainmenu (site top nav) must exist",
            doc.selectFirst(".responsive_page_menu_ctn.mainmenu"))
    }

    @Test
    fun `steamcommunity removeWithin nukes comments and bottom footer`() {
        val html = loadFixture("steamcommunity-guide.html")
        val doc = Jsoup.parse(html)
        // The 90-comments block at the bottom of the guide.
        assertNotNull(".commentthread_area must exist for removeWithin pin",
            doc.selectFirst(".commentthread_area"))
        // Steam's site footer (LEGAL, MORE, Get Steam links).
        val footers = doc.select("footer")
        assertEquals("exactly one <footer> in the doc — bare tag selector is safe",
            1, footers.size)
        // No <header> tags — we are not at risk of the corresponding
        // bare `header` selector hitting anything we'd want to keep.
        assertEquals("doc has no <header> tags", 0, doc.select("header").size)
    }

    @Test
    fun `zeldadungeon wiki path matches the wiki entry (not blog)`() {
        val url = "https://www.zeldadungeon.net/wiki/The_Legend_of_Zelda:_A_Link_to_the_Past"
        val filter = filterForHost(url)
        assertNotNull(filter)
        assertEquals("zeldadungeon-wiki", filter!!.name)
        assertSelectorPasses(url, "zeldadungeon-wiki", "zeldadungeon-wiki.html")
    }

    @Test
    fun `zeldadungeon non-wiki path matches the blog entry`() {
        val url = "https://www.zeldadungeon.net/phantom-hourglass-walkthrough/"
        val filter = filterForHost(url)
        assertNotNull(filter)
        assertEquals("zeldadungeon-blog", filter!!.name)
        assertSelectorPasses(url, "zeldadungeon-blog", "zeldadungeon-blog.html")
    }

    @Test
    fun `zeldadungeon blog chapter-page chapter-list selector hides only the list`() {
        // Chapter sub-page fixture: A Link to the Past — Eastern Palace.
        // `.walkthrough.wt2 > ul` must hit the single direct <ul> child of
        // the body wrapper — that's the chapter list. The wrapper itself
        // is the body content and MUST stay; this was the bug that
        // wiped all body text in the previous attempt.
        val html = loadFixture("zeldadungeon-blog-chapter.html")
        val doc = Jsoup.parse(html)
        val uls = doc.select(".walkthrough.wt2 > ul")
        // The chapter sub-page actually repeats the same chapter list
        // twice (top + bottom of the wrapper). Both are direct UL
        // children → both get hidden, which is what we want.
        assertEquals("Eastern Palace shows chapter list at top + bottom",
            2, uls.size)
        // Sanity: both contain chapter-link text.
        for (ul in uls) {
            assertTrue("each matched UL must carry 'Chapter 1' text",
                ul.text().contains("Chapter 1"))
        }
        // The wrapper itself must NOT be matched by the selector — body
        // content stays.
        val wrappers = doc.select(".walkthrough.wt2")
        assertEquals(1, wrappers.size)
        // The wrapper carries an h2 like "2.1 Lost Woods..." after the UL —
        // proof that body content lives inside the wrapper.
        assertFalse("wrapper must contain body section headings beyond the UL",
            wrappers.first()!!.select("h2").isEmpty())
    }

    @Test
    fun `zeldadungeon blog hub chapter-list selector does not blank screenshots`() {
        // PH hub fixture: 13x `.walkthrough.wt2` wrapping screenshot
        // grids. Their direct children are `<div class="scrn cols-3">`,
        // not <ul>. The `> ul` qualifier must NOT match them.
        val html = loadFixture("zeldadungeon-blog.html")
        val doc = Jsoup.parse(html)
        assertTrue("hub page must have many .walkthrough.wt2 instances",
            doc.select(".walkthrough.wt2").size > 5)
        assertEquals("`> ul` must not match hub-page screenshot grids",
            0, doc.select(".walkthrough.wt2 > ul").size)
    }

    @Test
    fun `zeldadungeon blog removeWithin nukes comments and related`() {
        // Both fixtures carry the social/share block + Disqus thread.
        for (fixture in listOf("zeldadungeon-blog-chapter.html", "zeldadungeon-blog.html")) {
            val doc = Jsoup.parse(loadFixture(fixture))
            assertNotNull("$fixture must have .news-readmore for removeWithin pin",
                doc.selectFirst(".news-readmore"))
            assertNotNull("$fixture must have #disqus_thread for removeWithin pin",
                doc.selectFirst("#disqus_thread"))
        }
    }

    @Test
    fun `zeldadungeon chapter sub-page URL resolves to the blog entry`() {
        // Chapter sub-pages (e.g. /a-link-to-the-past-walkthrough/eastern-palace/)
        // are NOT under /wiki/ — must hit the blog entry, not the wiki entry.
        val url = "https://www.zeldadungeon.net/a-link-to-the-past-walkthrough/eastern-palace/"
        val filter = filterForHost(url)
        assertNotNull(filter)
        assertEquals("zeldadungeon-blog", filter!!.name)
    }

    // -- Neoseeker keepAlso is empty --------------------------------------

    @Test
    fun `neoseeker keepAlso no longer carries entireheader`() {
        val filter = filterForHost("https://www.neoseeker.com/dark-souls-iii/walkthrough")
        assertNotNull(filter)
        assertEquals("neoseeker", filter!!.name)
        assertEquals("keepAlso must be empty so #entireheader is hidden",
            emptyList<String>(), filter.keepAlso)
    }

    @Test
    fun `neoseeker entireheader fixture pin still holds`() {
        // We're not keeping it any more, but the selector still has to
        // match the fixture's DOM so we can be sure it'll be in scope
        // when the sibling-hide pass runs.
        val html = loadFixture("neoseeker-walkthrough.html")
        val doc = Jsoup.parse(html)
        assertNotNull("#entireheader must exist for sibling-hide pass to nuke it",
            doc.selectFirst("#entireheader"))
    }

    // -- JS payload sanity -------------------------------------------------

    @Test
    fun `buildReadingModeJs encodes selector and removeWithin safely`() {
        val filter = HostFilter(
            name = "weird",
            matches = { false },
            bodySelector = "div[data-tricky=\"with \\ slash\"]",
            keepAlso = listOf("nav[aria-label=\"main\"]"),
            removeWithin = listOf(".one", "div[id^=\"a\"]"),
        )
        val js = buildReadingModeJs(filter)
        // The selector contains a `"` — verify we encoded it via JSONObject.quote
        // rather than letting Kotlin's string-template paste it raw.
        assertFalse("raw double-quote inside selector would break the IIFE",
            js.contains("\"with \" slash\""))
        // The removeWithin and keepAlso arrays get JSON-encoded.
        assertTrue(js.contains("[\".one\""))
        // keepAlso entry survives JSON encoding (double-quote becomes \").
        assertTrue("keepAlso must reach the JS payload",
            js.contains("nav[aria-label=") && js.contains("\\\"main\\\""))
    }

    @Test
    fun `REMOVE_READING_MODE_JS is a self-contained IIFE`() {
        // Smoke: it parses as a function expression that takes no args.
        assertTrue(REMOVE_READING_MODE_JS.startsWith("(function()"))
        assertTrue(REMOVE_READING_MODE_JS.endsWith("})();"))
        assertTrue(REMOVE_READING_MODE_JS.contains("data-gametwin-hidden"))
        assertTrue(REMOVE_READING_MODE_JS.contains("data-gametwin-keep"))
    }

    // -- hideButReserveSpace ----------------------------------------------

    @Test
    fun `default HostFilter has empty hideButReserveSpace`() {
        val f = HostFilter(
            name = "x",
            matches = { false },
            bodySelector = "body",
        )
        assertEquals(emptyList<String>(), f.hideButReserveSpace)
    }

    @Test
    fun `buildReadingModeJs wires hideButReserveSpace into the payload`() {
        val f = HostFilter(
            name = "x",
            matches = { true },
            bodySelector = "body",
            hideButReserveSpace = listOf("header.x-band"),
        )
        val js = buildReadingModeJs(f)
        assertTrue("payload must carry the hbs selector literal",
            js.contains("\"header.x-band\""))
        assertTrue("payload must mark elements data-gametwin-spaced",
            js.contains("gametwinSpaced"))
        assertTrue("payload must set visibility hidden on spaced elements",
            js.contains("visibility"))
        assertTrue("payload must check computed position for fixed fallback",
            js.contains("position"))
        assertTrue("payload must create a spacer when position fixed",
            js.contains("gametwinSpacer"))
    }

    @Test
    fun `REMOVE_READING_MODE_JS clears spaced markers and removes spacers`() {
        assertTrue("must clear data-gametwin-spaced",
            REMOVE_READING_MODE_JS.contains("data-gametwin-spaced"))
        assertTrue("must remove data-gametwin-spacer elements",
            REMOVE_READING_MODE_JS.contains("data-gametwin-spacer"))
    }

    // -- preTransformsJs --------------------------------------------------

    @Test
    fun `default HostFilter has blank preTransformsJs`() {
        val f = HostFilter(
            name = "x",
            matches = { false },
            bodySelector = "body",
        )
        assertEquals("", f.preTransformsJs)
    }

    @Test
    fun `buildReadingModeJs splices preTransformsJs at the head of the IIFE`() {
        val sentinel = "/*GT-PRE-TRANSFORM-SENTINEL*/"
        val f = HostFilter(
            name = "x",
            matches = { true },
            bodySelector = "body",
            preTransformsJs = sentinel,
        )
        val js = buildReadingModeJs(f)
        val sentinelIdx = js.indexOf(sentinel)
        assertTrue("sentinel must appear in the payload", sentinelIdx >= 0)
        // Sentinel must come BEFORE document.querySelector(sel) — pre-
        // transforms exist precisely to mutate the DOM the bodySelector
        // is about to read. If a regression moves the splice after the
        // querySelector / early-return, the rewrite is too late.
        val querySelIdx = js.indexOf("document.querySelector(sel)")
        assertTrue("preTransformsJs must run before bodySelector query",
            sentinelIdx in 0 until querySelIdx)
    }

    @Test
    fun `buildReadingModeJs omits preTransformsJs when blank`() {
        // No accidental empty `;;` or stray characters when the field is
        // its default value.
        val f = HostFilter(
            name = "x",
            matches = { true },
            bodySelector = "body",
        )
        val js = buildReadingModeJs(f)
        // Sanity: payload still wraps the try/catch IIFE.
        assertTrue(js.startsWith("(function(){try{"))
    }

    // -- GameFAQs tipjar form rewrite -------------------------------------

    @Test
    fun `gamefaqs filter has a non-blank preTransformsJs`() {
        val filter = filterForHost("https://gamefaqs.gamespot.com/snes/x/faqs/1")
        assertNotNull(filter)
        assertEquals("gamefaqs", filter!!.name)
        assertTrue("preTransformsJs must be non-blank",
            filter.preTransformsJs.isNotBlank())
    }

    @Test
    fun `gamefaqs preTransformsJs targets form tipjar_form and is idempotent`() {
        val filter = filterForHost("https://gamefaqs.gamespot.com/snes/x/faqs/1")!!
        val pre = filter.preTransformsJs
        assertTrue("snippet must select form.tipjar_form",
            pre.contains("form.tipjar_form"))
        assertTrue("snippet must read input[type=\"image\"]",
            pre.contains("input[type=\"image\"]"))
        assertTrue("snippet must guard on data-gametwin-rewrite=\"tipjar\"",
            pre.contains("tipjar"))
        // The rewritten anchor must carry safety attrs so an external
        // navigation can't read the opener / leak referrer.
        assertTrue("snippet must set rel noopener noreferrer",
            pre.contains("noopener") && pre.contains("noreferrer"))
        assertTrue("snippet must set target _blank",
            pre.contains("_blank"))
    }

    @Test
    fun `gamefaqs fixture carries form tipjar_form with an input type image`() {
        // Fixture pin: if GameFAQs swaps their donate-button shape the
        // selector + transform need to be revisited.
        val html = loadFixture("gamefaqs-html-faq.html")
        val doc = Jsoup.parse(html)
        val form = doc.selectFirst("form.tipjar_form")
        assertNotNull("form.tipjar_form must exist", form)
        val input = form!!.selectFirst("input[type=image]")
        assertNotNull("input[type=image] inside form.tipjar_form must exist", input)
        val action = form.attr("action")
        assertTrue("form action must be a PayPal URL",
            action.contains("paypal.com"))
    }

    @Test
    fun `buildReadingModeJs for gamefaqs splices the tipjar rewrite at the head`() {
        val filter = filterForHost("https://gamefaqs.gamespot.com/snes/x/faqs/1")!!
        val js = buildReadingModeJs(filter)
        // Snippet must be present AND must come before the
        // bodySelector query — otherwise the rewrite happens after
        // the early-return check on un-transformed DOM.
        val tipjarIdx = js.indexOf("tipjar")
        val querySelIdx = js.indexOf("document.querySelector(sel)")
        assertTrue("tipjar snippet must appear in the payload", tipjarIdx >= 0)
        assertTrue("tipjar rewrite must run before bodySelector query",
            tipjarIdx in 0 until querySelIdx)
    }

    // -- Neoseeker FAQ-page format support --------------------------------

    @Test
    fun `neoseeker bodySelector accepts both walkthrough and FAQ shapes`() {
        val filter = filterForHost("https://www.neoseeker.com/super-metroid/faqs/965915-p.html")
        assertNotNull(filter)
        assertEquals("neoseeker", filter!!.name)
        // Comma-list selector covers both `/<game>/walkthrough`
        // (#walkthrough_page_wrapper) and `/<game>/faqs/<id>-p.html`
        // (#markupfaq).
        assertTrue("bodySelector must include #walkthrough_page_wrapper",
            filter.bodySelector.contains("#walkthrough_page_wrapper"))
        assertTrue("bodySelector must include #markupfaq",
            filter.bodySelector.contains("#markupfaq"))
    }

    @Test
    fun `neoseeker FAQ URL resolves to the neoseeker filter`() {
        // FAQ-page format that wasn't covered by the original filter.
        val faqUrl = "https://www.neoseeker.com/super-metroid/faqs/965915-p.html"
        val filter = filterForHost(faqUrl)
        assertNotNull(filter)
        assertEquals("neoseeker", filter!!.name)
    }

    @Test
    fun `neoseeker removeWithin carries product-hub-nav`() {
        val filter = filterForHost("https://www.neoseeker.com/super-metroid/faqs/965915-p.html")!!
        assertTrue("product-hub-nav (Cheats / FAQs / Forum / Media / Reviews bar) must be in removeWithin",
            filter.removeWithin.contains("#product-hub-nav"))
    }

    // -- Zelda Dungeon blog top-chrome cleanup ----------------------------

    @Test
    fun `zeldadungeon-blog removeWithin includes top site chrome`() {
        val filter = filterForHost("https://www.zeldadungeon.net/a-link-to-the-past-walkthrough/eastern-palace/")
        assertNotNull(filter)
        assertEquals("zeldadungeon-blog", filter!!.name)
        // Top site chrome that leaks through the sibling-hide pass on-device.
        // Listed explicitly so removeWithin (document.querySelectorAll-based)
        // guarantees `display: none` regardless of layout drift.
        for (sel in listOf("#mobilenav", "#header", "#sitenav")) {
            assertTrue("removeWithin must include $sel",
                filter.removeWithin.contains(sel))
        }
    }

    @Test
    fun `zeldadungeon-blog top-chrome IDs exist in fixtures`() {
        for (fixture in listOf("zeldadungeon-blog.html", "zeldadungeon-blog-chapter.html")) {
            val doc = Jsoup.parse(loadFixture(fixture))
            for (id in listOf("mobilenav", "header", "sitenav")) {
                assertNotNull("$fixture must carry #$id for removeWithin pin",
                    doc.selectFirst("#$id"))
            }
        }
    }

    // -- StrategyWiki ---------------------------------------------------------

    @Test
    fun `strategywiki filter resolves and body selector matches`() {
        val url = "https://strategywiki.org/wiki/Luigi%27s_Mansion/Walkthrough"
        assertSelectorPasses(url, "strategywiki", "strategywiki-walkthrough.html")
    }

    @Test
    fun `strategywiki subdomain also resolves`() {
        // e.g. en.strategywiki.org (potential future subdomain)
        val filter = filterForHost("https://en.strategywiki.org/wiki/Some_Game")
        assertNotNull(filter)
        assertEquals("strategywiki", filter!!.name)
    }

    @Test
    fun `strategywiki firstHeading exists in the fixture`() {
        val html = loadFixture("strategywiki-walkthrough.html")
        val doc = Jsoup.parse(html)
        assertNotNull("#firstHeading must exist for keepAlso pin",
            doc.selectFirst("#firstHeading"))
    }

    @Test
    fun `strategywiki removeWithin selectors match expected chrome`() {
        val html = loadFixture("strategywiki-walkthrough.html")
        val doc = Jsoup.parse(html)
        assertNotNull(".printfooter must exist for removeWithin pin",
            doc.selectFirst(".printfooter"))
        assertNotNull(".catlinks must exist for removeWithin pin",
            doc.selectFirst(".catlinks"))
        assertNotNull(".mw-editsection must exist for removeWithin pin",
            doc.selectFirst(".mw-editsection"))
        assertNotNull("[id^=div-gpt-ad] must exist for removeWithin pin",
            doc.selectFirst("[id^=div-gpt-ad]"))
    }

    @Test
    fun `strategywiki removeWithin does not include absent selectors`() {
        // #toc and .navbox were absent from the Luigi's Mansion/Walkthrough
        // fixture — not shipped speculatively.
        val filter = filterForHost("https://strategywiki.org/wiki/Luigi%27s_Mansion/Walkthrough")!!
        assertFalse("#toc was absent from the fixture and must not be in removeWithin",
            filter.removeWithin.contains("#toc"))
        assertFalse(".navbox was absent from the fixture and must not be in removeWithin",
            filter.removeWithin.contains(".navbox"))
    }

    @Test
    fun `strategywiki auto-TOC presence varies page to page so bare #toc stays out`() {
        // Two sibling pages of ONE guide disagree: Castle Corridor has enough
        // sections for MediaWiki to auto-generate its .toc box, Chapel doesn't.
        // A third page shape uses id="toc" for a content table instead. That
        // spread is the reason removeWithin can't carry a bare "#toc" — it
        // would delete real content on the third kind of page.
        val withAutoToc = Jsoup.parse(loadFixture("strategywiki-autotoc-mobile.html"))
        assertNotNull(
            "Castle Corridor must carry a standard MediaWiki auto-TOC",
            withAutoToc.selectFirst("#toc.toc"),
        )
        assertNotNull("auto-TOC must carry .toctitle", withAutoToc.selectFirst("#toc .toctitle"))

        val withoutAutoToc = Jsoup.parse(loadFixture("strategywiki-chapter-mobile.html"))
        assertNull(
            "Chapel has too few sections for an auto-TOC — the shape is not uniform",
            withoutAutoToc.selectFirst("#toc"),
        )

        val filter = filterForHost("https://strategywiki.org/wiki/Castlevania:_Aria_of_Sorrow/Castle_Corridor")!!
        assertFalse(
            "bare #toc must stay out of removeWithin — it means content on some pages",
            filter.removeWithin.contains("#toc"),
        )
    }

    @Test
    fun `strategywiki header nav box is stripped on the auto-TOC page too`() {
        // The page the on-device check landed on (4/20, Castle Corridor).
        val doc = Jsoup.parse(loadFixture("strategywiki-autotoc-mobile.html"))
        val first = doc.selectFirst(".mw-parser-output")!!.children().first()
        assertNotNull(".mw-parser-output must have children", first)
        assertTrue(
            "expected .nav_box.header_nav first, got ${first!!.className()}",
            first.hasClass("nav_box") && first.hasClass("header_nav"),
        )
        val filter = filterForHost("https://strategywiki.org/wiki/Castlevania:_Aria_of_Sorrow/Castle_Corridor")!!
        assertFalse(
            "the header nav box must match a removeWithin selector",
            filter.removeWithin.none { doc.select(it).any { e -> e === first } },
        )
    }

    // -- StrategyWiki: the guide-nav box between title and body ------------
    //
    // Pinned against the mobile-UA saves the WebView actually produces on
    // device (Castlevania: Aria of Sorrow, page group), not just the desktop
    // Luigi's Mansion capture. The mobile DOM differs: MediaWiki has already
    // run its collapsible JS (`mw-made-collapsible` + an "Expand" button) and
    // it carries four `.section-ad` wrappers instead of one.

    @Test
    fun `strategywiki mobile chapter save resolves and body selector matches`() {
        val url = "https://strategywiki.org/wiki/Castlevania:_Aria_of_Sorrow/Chapel"
        assertSelectorPasses(url, "strategywiki", "strategywiki-chapter-mobile.html")
    }

    @Test
    fun `strategywiki header nav box is the first thing after the title`() {
        // The complaint this filter answers: a link rectangle (<game> · Table
        // of Contents · Gameplay · Walkthrough) sat between the page title and
        // the first line of prose. It is the FIRST element child of
        // .mw-parser-output on every guide page, so removing it makes the body
        // text start directly under the heading.
        for (fixture in listOf("strategywiki-chapter-mobile.html", "strategywiki-toc-mobile.html")) {
            val doc = Jsoup.parse(loadFixture(fixture))
            val first = doc.selectFirst(".mw-parser-output")!!.children().first()
            assertNotNull("$fixture: .mw-parser-output must have children", first)
            assertTrue(
                "$fixture: expected .nav_box.header_nav first, got ${first!!.className()}",
                first.hasClass("nav_box") && first.hasClass("header_nav"),
            )
        }
    }

    @Test
    fun `strategywiki removeWithin strips the header nav box on mobile and desktop`() {
        val filter = filterForHost("https://strategywiki.org/wiki/Castlevania:_Aria_of_Sorrow/Chapel")!!
        assertTrue(
            ".nav_box.header_nav must be in removeWithin",
            filter.removeWithin.contains(".nav_box.header_nav"),
        )
        for (fixture in listOf(
            "strategywiki-chapter-mobile.html",
            "strategywiki-toc-mobile.html",
            "strategywiki-walkthrough.html",
        )) {
            val doc = Jsoup.parse(loadFixture(fixture))
            assertFalse(
                "$fixture: .nav_box.header_nav must exist for the removeWithin pin",
                doc.select(".nav_box.header_nav").isEmpty(),
            )
        }
    }

    @Test
    fun `strategywiki keeps the footer prev-next nav box`() {
        // The bottom .nav_box.footer_nav (Go to top + prev / here / next) stays
        // — it's the in-page way to walk chapters. The class-qualified
        // header_nav selector must not reach it.
        val doc = Jsoup.parse(loadFixture("strategywiki-chapter-mobile.html"))
        val footer = doc.selectFirst(".nav_box.footer_nav")
        assertNotNull("fixture must carry a footer_nav to make this test meaningful", footer)
        val filter = filterForHost("https://strategywiki.org/wiki/Castlevania:_Aria_of_Sorrow/Chapel")!!
        for (sel in filter.removeWithin) {
            assertFalse(
                "removeWithin selector $sel must not match the footer nav box",
                doc.select(sel).any { it === footer },
            )
        }
    }

    @Test
    fun `strategywiki header nav removal leaves the Table of Contents page body intact`() {
        // A /Table_of_Contents page's real body is a standalone .t_colbox that
        // is a SIBLING of the nav box, not a descendant. Removing the nav box
        // must not blank that page.
        val doc = Jsoup.parse(loadFixture("strategywiki-toc-mobile.html"))
        val parser = doc.selectFirst(".mw-parser-output")!!
        assertTrue(
            "ToC page body (.t_colbox sibling of the nav box) must exist",
            parser.children().any { it.hasClass("t_colbox") },
        )
        doc.select(".nav_box.header_nav").remove()
        assertFalse(
            "removing .nav_box.header_nav must leave the ToC page body behind",
            doc.select(".mw-parser-output > .t_colbox").isEmpty(),
        )
    }

    @Test
    fun `strategywiki strips ad wrappers as well as the inner gpt slots`() {
        val filter = filterForHost("https://strategywiki.org/wiki/Castlevania:_Aria_of_Sorrow/Chapel")!!
        assertTrue(
            ".section-ad must be in removeWithin so the wrapper can't reserve a blank band",
            filter.removeWithin.contains(".section-ad"),
        )
        assertTrue(
            "[id^=div-gpt-ad] stays — desktop ships slots outside a .section-ad wrapper",
            filter.removeWithin.contains("[id^=\"div-gpt-ad\"]"),
        )
        val doc = Jsoup.parse(loadFixture("strategywiki-chapter-mobile.html"))
        assertFalse(
            ".section-ad must exist in the mobile fixture for the removeWithin pin",
            doc.select(".section-ad").isEmpty(),
        )
        // #leaderboard-ad-mobile-top rides inside #mw-content-text, above the
        // nav box — the one most likely to leave a gap under the title.
        val top = doc.selectFirst("#leaderboard-ad-mobile-top")
        assertNotNull("mobile leaderboard wrapper must exist for the pin", top)
        assertTrue("leaderboard wrapper must carry .section-ad", top!!.hasClass("section-ad"))
    }

    @Test
    fun `strategywiki removeWithin selectors never match the kept body element`() {
        // Safety net: a removeWithin selector that hit #mw-content-text (or an
        // ancestor) would be skipped by the keep-chain guard at runtime, but
        // the intent is that none of them come close.
        val filter = filterForHost("https://strategywiki.org/wiki/Castlevania:_Aria_of_Sorrow/Chapel")!!
        for (fixture in listOf("strategywiki-chapter-mobile.html", "strategywiki-toc-mobile.html")) {
            val doc = Jsoup.parse(loadFixture(fixture))
            val body = doc.selectFirst(filter.bodySelector)!!
            for (sel in filter.removeWithin) {
                assertFalse(
                    "$fixture: removeWithin selector $sel must not match the kept body element",
                    doc.select(sel).any { it === body || body.parents().any { p -> p === it } },
                )
            }
        }
    }

    // -- IGN wiki ----------------------------------------------------------

    @Test
    fun `ign filter resolves and body selector matches`() {
        val url = "https://www.ign.com/wikis/god-of-war-ii/Rhodes"
        assertSelectorPasses(url, "ign", "ign-wiki.html")
    }

    @Test
    fun `ign filter is path-gated to wikis`() {
        // Non-wiki IGN pages (articles, reviews, videos) must NOT get the
        // wiki filter — its .wiki-html selector would be wrong there.
        val filter = filterForHost("https://www.ign.com/articles/some-review")
        assertTrue(
            "non-/wikis/ IGN pages must not resolve to the ign filter",
            filter == null || filter.name != "ign",
        )
    }

    @Test
    fun `ign page-header exists in the fixture for keepAlso pin`() {
        val doc = Jsoup.parse(loadFixture("ign-wiki.html"))
        assertNotNull("[data-cy=page-header] must exist for keepAlso pin",
            doc.selectFirst("[data-cy=page-header]"))
    }

    @Test
    fun `ign removeWithin selectors match expected chrome`() {
        val doc = Jsoup.parse(loadFixture("ign-wiki.html"))
        assertNotNull(".wiki-bobble (in-article ad block) must exist for removeWithin pin",
            doc.selectFirst(".wiki-bobble"))
        assertNotNull(".adunit-wrapper must exist for removeWithin pin",
            doc.selectFirst(".adunit-wrapper"))
        assertNotNull("[data-cy=wiki-auto-embed] must exist for removeWithin pin",
            doc.selectFirst("[data-cy=wiki-auto-embed]"))
    }

    @Test
    fun `ign wiki-bobble wraps the advertisement label and slot`() {
        // The blank band came from hiding only `.adunit-wrapper` and leaving the
        // `.wiki-bobble` wrapper (+ its "advertisement" label) reserving space.
        val doc = Jsoup.parse(loadFixture("ign-wiki.html"))
        val bobble = doc.selectFirst(".wiki-bobble")
        assertNotNull(bobble)
        assertNotNull("bobble must contain the ad slot it reserves space for",
            bobble!!.selectFirst(".adunit-wrapper"))
    }

    // -- IGN guide-header chrome (breadcrumb + byline follow button) -------
    //
    // The Rhodes fixture is a wiki SUB-page: it has a page-header/title but no
    // breadcrumb and no byline follow button. Those ride on the top-level guide
    // pages, so this group pins them against the Shovel Knight guide fixture —
    // the shape the user actually saw leaking into reader mode.

    @Test
    fun `ign guide fixture resolves and body selector matches`() {
        val url = "https://www.ign.com/wikis/shovel-knight"
        assertSelectorPasses(url, "ign", "ign-guide-header.html")
    }

    @Test
    fun `ign removeWithin strips the breadcrumb and byline follow button`() {
        val doc = Jsoup.parse(loadFixture("ign-guide-header.html"))
        val breadcrumb = doc.selectFirst("[data-cy=page-header] > .interface")
        assertNotNull(
            "the 'Home > Guides' breadcrumb must exist for the removeWithin pin",
            breadcrumb,
        )
        assertTrue(
            "the breadcrumb must be the site nav we mean to drop",
            breadcrumb!!.select("a[data-cy=site-breadcrumb]").isNotEmpty(),
        )
        val bylineAction = doc.selectFirst(".byline-action")
        assertNotNull(
            "the byline follow button ('Add IGN on Google') must exist for the removeWithin pin",
            bylineAction,
        )
        val filter = filterForHost("https://www.ign.com/wikis/shovel-knight")!!
        assertFalse(
            "the breadcrumb must match a removeWithin selector",
            filter.removeWithin.none { doc.select(it).any { e -> e === breadcrumb } },
        )
        assertFalse(
            "the byline follow button must match a removeWithin selector",
            filter.removeWithin.none { doc.select(it).any { e -> e === bylineAction } },
        )
    }

    @Test
    fun `ign keeps the title and the author byline`() {
        // What reader mode must NOT drop: the guide title and the author +
        // "Updated:" caption. Both live in the kept page-header block; no
        // removeWithin selector may reach them.
        val doc = Jsoup.parse(loadFixture("ign-guide-header.html"))
        val title = doc.selectFirst("h1.display-title")
        val authors = doc.selectFirst("section.byline .caption")
        assertNotNull("the guide title must exist", title)
        assertNotNull("the author caption must exist", authors)
        val filter = filterForHost("https://www.ign.com/wikis/shovel-knight")!!
        for (sel in filter.removeWithin) {
            assertFalse(
                "removeWithin selector $sel must not reach the title",
                doc.select(sel).any { m -> m === title || m.parents().any { it === title } },
            )
            assertFalse(
                "removeWithin selector $sel must not reach the author caption",
                doc.select(sel).any { m -> m === authors || m.parents().any { it === authors } },
            )
        }
    }

    // -- GitHub wiki -------------------------------------------------------

    @Test
    fun `github wiki filter resolves and body selector matches`() {
        val url = "https://github.com/RetroAchievements/guides/wiki/Legend-of-Zelda,-The:-A-Link-to-the-Past-(SNES)"
        assertSelectorPasses(url, "github-wiki", "github-wiki.html")
    }

    @Test
    fun `github filter is path-gated to wiki pages`() {
        // Non-wiki GitHub pages (repo code, issues, PRs, profiles) must NOT
        // get the wiki filter — its #wiki-body selector is wrong there and
        // would early-return at best, blank chrome at worst.
        for (url in listOf(
            "https://github.com/RetroAchievements/guides",
            "https://github.com/RetroAchievements/guides/issues/1",
            "https://github.com/RetroAchievements",
        )) {
            val filter = filterForHost(url)
            assertTrue(
                "non-/wiki/ GitHub page must not resolve to github-wiki: $url",
                filter == null || filter.name != "github-wiki",
            )
        }
    }

    @Test
    fun `github keepAlso selector matches the wiki page title`() {
        // h1.gh-header-title carries the guide title and is a sibling-above
        // of #wiki-body (inside .gh-header). Without keepAlso the keep-chain
        // would hide it.
        val doc = Jsoup.parse(loadFixture("github-wiki.html"))
        assertNotNull("h1.gh-header-title must exist for keepAlso pin",
            doc.selectFirst("h1.gh-header-title"))
    }

    @Test
    fun `github removeWithin selectors match expected chrome`() {
        // The keep-chain keeps .gh-header visible (it wraps the title), so
        // the Edit/New-page/Jump action row inside it survives unless
        // removeWithin nukes it. The edited-by/revisions meta + the
        // "Add a custom footer" prompt are also stripped.
        val doc = Jsoup.parse(loadFixture("github-wiki.html"))
        assertNotNull(".gh-header-actions must exist for removeWithin pin",
            doc.selectFirst(".gh-header-actions"))
        assertNotNull(".gh-header-meta must exist for removeWithin pin",
            doc.selectFirst(".gh-header-meta"))
        assertNotNull("#wiki-footer must exist for removeWithin pin",
            doc.selectFirst("#wiki-footer"))
    }

    @Test
    fun `github sidebar is hidden by the keep-chain sibling pass`() {
        // The Pages nav + "Clone this wiki locally" + custom sidebar all live
        // in .Layout-sidebar, a sibling of .Layout-main (which holds
        // #wiki-body). The keep-chain sibling-hide pass nukes it — verify the
        // selector is in scope so that pass can reach it.
        val doc = Jsoup.parse(loadFixture("github-wiki.html"))
        assertNotNull(".Layout-sidebar must exist for sibling-hide pass to nuke it",
            doc.selectFirst(".Layout-sidebar"))
        // Sanity: the Pages nav + clone box live inside it.
        assertNotNull("#wiki-pages-box must live inside the sidebar",
            doc.selectFirst(".Layout-sidebar #wiki-pages-box"))
        assertNotNull("#wiki-clone-url must live inside the sidebar",
            doc.selectFirst(".Layout-sidebar #wiki-clone-url"))
    }

    @Test
    fun `github filter forces device-width layout via preTransformsJs`() {
        // The offline viewer lays the saved GitHub page out at its ~1234px
        // desktop intrinsic width and scales it down, so Primer's `.Layout`
        // renders in two-column desktop mode and the body text is squeezed
        // into a tiny narrow strip. preTransformsJs must (a) force the viewport
        // to device-width so the layout collapses to GitHub's single-column
        // mobile mode at normal size, and (b) neutralize the desktop Layout +
        // container cap as a fallback for WebViews that don't reflow on a
        // runtime viewport change.
        val filter = filterForHost("https://github.com/o/r/wiki/Page")!!
        assertEquals("github-wiki", filter.name)
        val pre = filter.preTransformsJs
        assertTrue("must force the viewport to device-width",
            pre.contains("width=device-width"))
        assertTrue("must target the viewport meta",
            pre.contains("meta[name=\"viewport\"]"))
        assertTrue("must collapse the Primer Layout grid as a fallback",
            pre.contains("#wiki-content .Layout") && pre.contains("block"))
        assertTrue("must uncap the container-xl wrapper so it can shrink",
            pre.contains("#wiki-wrapper"))
        assertTrue("must wrap wide pre blocks so they don't force horizontal scroll",
            pre.contains("pre-wrap"))
    }

    @Test
    fun `github fixture carries the Layout grid containers`() {
        // Pin the selectors the layout-neutralize step leans on.
        val doc = Jsoup.parse(loadFixture("github-wiki.html"))
        assertNotNull("#wiki-content .Layout must exist for the layout-neutralize pin",
            doc.selectFirst("#wiki-content .Layout"))
        assertNotNull(".Layout-main must wrap #wiki-body",
            doc.selectFirst("#wiki-content .Layout-main #wiki-body"))
    }

    // -- !important hides --------------------------------------------------

    @Test
    fun `buildReadingModeJs uses setProperty important for display hides`() {
        // Bootstrap-style stylesheets often write `display: block !important`
        // via responsive utility classes (.visible-xs, .visible-sm). Inline
        // style.display = "none" without !important loses that specificity
        // battle and the chrome leaks through. setProperty(...,"important")
        // beats stylesheet !important by inline-priority rules.
        val f = HostFilter(name = "x", matches = { true }, bodySelector = "body")
        val js = buildReadingModeJs(f)
        assertTrue("payload must use setProperty(\"display\",\"none\",\"important\")",
            js.contains("setProperty(\"display\",\"none\",\"important\")"))
        // No bare style.display="none" assignments should remain (they would
        // be overridden by !important stylesheet rules).
        assertFalse("payload must not contain bare style.display=\"none\"",
            js.contains("style.display=\"none\""))
    }

    @Test
    fun `buildReadingModeJs uses setProperty important for visibility hides`() {
        val f = HostFilter(
            name = "x",
            matches = { true },
            bodySelector = "body",
            hideButReserveSpace = listOf(".chrome"),
        )
        val js = buildReadingModeJs(f)
        assertTrue("payload must use setProperty(\"visibility\",\"hidden\",\"important\")",
            js.contains("setProperty(\"visibility\",\"hidden\",\"important\")"))
        assertFalse("payload must not contain bare style.visibility=\"hidden\"",
            js.contains("style.visibility=\"hidden\""))
    }

    @Test
    fun `REMOVE_READING_MODE_JS uses removeProperty to clear inline important`() {
        // style.display = "" only clears non-!important inline values.
        // removeProperty("display") clears even !important — symmetric
        // with the setProperty path in buildReadingModeJs.
        assertTrue("must use removeProperty(\"display\")",
            REMOVE_READING_MODE_JS.contains("removeProperty(\"display\")"))
        assertTrue("must use removeProperty(\"visibility\")",
            REMOVE_READING_MODE_JS.contains("removeProperty(\"visibility\")"))
        assertFalse("must not contain bare style.display=\"\"",
            REMOVE_READING_MODE_JS.contains("style.display=\"\""))
        assertFalse("must not contain bare style.visibility=\"\"",
            REMOVE_READING_MODE_JS.contains("style.visibility=\"\""))
    }

    // -- Bottom scroll padding --------------------------------------------

    @Test
    fun `SCROLL_PAD_JS sets top and bottom padding on the scroll root`() {
        assertTrue("must add ~3 lines of padding-top",
            SCROLL_PAD_JS.contains("setProperty(\"padding-top\",\"4em\",\"important\")"))
        assertTrue("must add padding-bottom",
            SCROLL_PAD_JS.contains("setProperty(\"padding-bottom\",\"4em\",\"important\")"))
    }

    @Test
    fun `SCROLL_PAD_JS targets the scrolling element`() {
        // Root padding (not a body-appended div) so it extends the scroll area
        // even in SPA-style layouts like IGN's Next.js DOM.
        assertTrue("must target the document scrolling element",
            SCROLL_PAD_JS.contains("document.scrollingElement"))
        assertTrue("must fall back to documentElement / body",
            SCROLL_PAD_JS.contains("document.documentElement") &&
                SCROLL_PAD_JS.contains("document.body"))
    }

    // -- Helpers ----------------------------------------------------------

    /**
     * Shared assertion: [url] resolves to a filter named [expectedName] and
     * that filter's body selector matches at least one element in the
     * fixture HTML at [fixtureName]. This is the core regression pin —
     * if a site redesigns away from our selector, this fails immediately.
     */
    private fun assertSelectorPasses(url: String, expectedName: String, fixtureName: String) {
        val filter = filterForHost(url)
        assertNotNull("filterForHost($url) should resolve", filter)
        assertEquals(expectedName, filter!!.name)
        val doc = Jsoup.parse(loadFixture(fixtureName))
        val matched = doc.select(filter.bodySelector)
        assertFalse(
            "Body selector ${filter.bodySelector} matched nothing in $fixtureName — " +
                "site shape may have drifted",
            matched.isEmpty()
        )
    }

    @Test
    fun `fireemblemwod hub page starts at the guide wrapper`() {
        // Game hub (ENG_fe<N>.htm): keep .inwrp so the game-info preamble and the
        // "(Change text to Spanish)" toggle above it are dropped.
        val hub = filterForHost("https://www.fireemblemwod.com/ENG_fe1.htm")
        assertNotNull("fireemblemwod hub must resolve a filter", hub)
        assertEquals("div.inwrp", hub!!.bodySelector)
    }

    @Test
    fun `fireemblemwod chapter page keeps the chapter body`() {
        // Chapter pages have no .inwrp; keep .mainsec.
        val ch = filterForHost("https://www.fireemblemwod.com/fe7/guiafe7/ENG_prologofe7.htm")
        assertNotNull("fireemblemwod chapter page must resolve a filter", ch)
        assertEquals("div.mainsec", ch!!.bodySelector)
        // Bare host (no www) also resolves.
        assertNotNull(filterForHost("https://fireemblemwod.com/fe2/gaguia/ENG_gacapitulo1.htm"))
    }

    private fun loadFixture(name: String): String {
        val stream = javaClass.classLoader!!
            .getResourceAsStream("fixtures/reading-mode/$name")
            ?: error("Missing fixture: fixtures/reading-mode/$name")
        return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }
}
