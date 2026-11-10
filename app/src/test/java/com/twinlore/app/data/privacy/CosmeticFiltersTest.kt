package com.twinlore.app.data.privacy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CosmeticFiltersTest {

    @Test
    fun `CSS contains iframe rule for every blocked host suffix`() {
        val css = CosmeticFilters.CSS_RULES
        for (host in BlockedHosts.BLOCKED_SUFFIXES) {
            assertTrue(
                "missing iframe rule for $host",
                css.contains("iframe[src*=\"$host\"]"),
            )
        }
    }

    @Test
    fun `CSS contains img rule for every blocked host suffix`() {
        val css = CosmeticFilters.CSS_RULES
        for (host in BlockedHosts.BLOCKED_SUFFIXES) {
            assertTrue(
                "missing img rule for $host",
                css.contains("img[src*=\"$host\"]"),
            )
        }
    }

    @Test
    fun `CSS contains GameFAQs recommend-guide selector`() {
        assertTrue(
            "missing #contrib_rec rule",
            CosmeticFilters.CSS_RULES.contains("#contrib_rec"),
        )
    }

    @Test
    fun `CSS collapses the GameFAQs sticky-header contrib band`() {
        // #contrib_rec is position:absolute inside the sticky #faq_header_wrap
        // bar, whose hardcoded height reserves a band for the prompt. Hiding the
        // absolutely-positioned prompt frees no space, so the reserved band
        // survives as a dark strip under the dark-theme site header. The
        // wrapper must be collapsed to its real content height to drop it.
        assertTrue(
            "missing #faq_header_wrap height collapse",
            CosmeticFilters.CSS_RULES.contains("#faq_header_wrap { height: auto !important; }"),
        )
    }

    @Test
    fun `CSS contains common ad-container selectors`() {
        val css = CosmeticFilters.CSS_RULES
        // Class-based ad-container conventions widely used across guide sites.
        for (selector in listOf(".gpt-ad", ".ad-slot", ".ad_slot", ".ad_wrap", ".ad-wrap", ".adthrive")) {
            assertTrue("missing $selector rule", css.contains(selector))
        }
        // Ad-network id prefixes — match wrappers + iframes by id pattern.
        assertTrue("missing div-gpt-ad- prefix selector", css.contains("[id^=\"div-gpt-ad-\"]"))
        assertTrue("missing google_ads_iframe_ prefix selector", css.contains("[id^=\"google_ads_iframe_\"]"))
        assertTrue("missing AdThrive_ prefix selector", css.contains("[id^=\"AdThrive_\"]"))
    }

    @Test
    fun `CSS contains consentmanager CMP banner selectors`() {
        // Bulbapedia (and many AdThrive sites) ship a consentmanager.net CMP
        // whose visible dialog lives in a Shadow DOM rooted at #cmpwrapper.
        // Hiding the wrapper collapses the shadow tree along with it.
        val css = CosmeticFilters.CSS_RULES
        for (selector in listOf("#cmpwrapper", ".cmpwrapper", "#cmpbox", ".cmpbox")) {
            assertTrue("missing $selector rule", css.contains(selector))
        }
    }

    @Test
    fun `CSS contains OneTrust CMP banner selectors`() {
        // OneTrust is the CMP used by every Fandom property (GameFAQs included)
        // and thousands of other sites. #onetrust-consent-sdk wraps the banner,
        // the preference-center modal, and the dark backdrop — hiding the
        // wrapper collapses all three. Sibling ids are listed explicitly as a
        // safety net for SDK configurations that root them outside the wrapper.
        val css = CosmeticFilters.CSS_RULES
        for (selector in listOf(
            "#onetrust-consent-sdk",
            "#onetrust-banner-sdk",
            "#onetrust-pc-sdk",
            ".onetrust-pc-dark-filter",
        )) {
            assertTrue("missing $selector rule", css.contains(selector))
        }
    }

    @Test
    fun `CSS contains Steam Community cookie banner selectors`() {
        // Steam Community bakes its cookie preferences banner into the page
        // HTML (no external CMP script to block) — cosmetic hiding is the
        // only effective layer. The banner is a <div id="cookiePrefPopup"
        // class="cookiepreferences_popup"> pinned to the bottom.
        val css = CosmeticFilters.CSS_RULES
        for (selector in listOf("#cookiePrefPopup", ".cookiepreferences_popup")) {
            assertTrue("missing $selector rule", css.contains(selector))
        }
    }

    @Test
    fun `CSS contains IGN ad-slot wrapper selector`() {
        // IGN wraps every ad slot (top leaderboard, in-article, sticky footer)
        // in `.adunit-wrapper`; blocking the ad leaves the wrapper reserving
        // height, so the wrapper itself must be hidden to collapse the gap.
        assertTrue("missing .adunit-wrapper rule",
            CosmeticFilters.CSS_RULES.contains(".adunit-wrapper"))
        assertTrue("missing .wiki-bobble rule (in-article ad block)",
            CosmeticFilters.CSS_RULES.contains(".wiki-bobble"))
    }

    @Test
    fun `CSS contains StrategyWiki ad-slot wrapper selectors`() {
        // StrategyWiki wraps each ad slot in a fixed-height container
        // (`.leaderboard-ad` for the 728x90 top/footer banners, `.section-ad`
        // for the inline 300x250 between the nav/ToC box and the article body).
        // Hiding only the inner `div-gpt-ad-*` slot leaves the wrapper reserving
        // its height, so the wrappers must be hidden too to collapse the gap.
        val css = CosmeticFilters.CSS_RULES
        for (selector in listOf(".leaderboard-ad", ".section-ad")) {
            assertTrue("missing $selector rule", css.contains(selector))
        }
    }

    @Test
    fun `CSS contains Google pre-search consent dialog selectors`() {
        // google.com (and locale variants) renders an inline pre-search
        // consent dialog ("Before you continue to Google Search") as part
        // of the main HTML — no CMP script to block at the request layer.
        // Stable obfuscated IDs survive Google's per-build rotations
        // (matches uBO's filter set).
        val css = CosmeticFilters.CSS_RULES
        for (selector in listOf("#xe7COe", "#CXQnmb", ".HTjtHe")) {
            assertTrue("missing $selector rule", css.contains(selector))
        }
    }

    @Test
    fun `CSS declarations all use !important`() {
        // Every `{ ... }` block must mark each declaration as !important so it
        // beats the page's own author styles. The stylesheet has two kinds of
        // rules: element-hide (display: none) and scroll-lock recovery
        // (overflow: visible / position: static / height: auto). Both must
        // win against page-author styles, so every declaration carries the
        // !important flag.
        val blocks = Regex("\\{[^}]*}").findAll(CosmeticFilters.CSS_RULES).toList()
        assertTrue("no rule blocks found", blocks.isNotEmpty())
        for (block in blocks) {
            val declarations = block.value
                .trim('{', '}', ' ', '\n', '\r', '\t')
                .split(';')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            assertTrue("block has no declarations: ${block.value}", declarations.isNotEmpty())
            for (decl in declarations) {
                assertTrue(
                    "declaration missing !important: '$decl' in block ${block.value}",
                    decl.contains("!important"),
                )
            }
        }
    }

    @Test
    fun `CSS contains Google consent dialog scroll-lock recovery`() {
        // Hiding the consent dialog with display:none leaves Google's
        // server-rendered `overflow: hidden` lock on <html> / <body> in
        // place — the page is unscrollable until the user picks an option
        // that can no longer be tapped. `:has()` scopes the override to
        // pages that actually shipped the dialog markup, so legitimate
        // sites that lock body scroll for their own modals are untouched.
        val css = CosmeticFilters.CSS_RULES
        for (selector in listOf(
            "html:has(#xe7COe)",
            "body:has(#xe7COe)",
            "html:has(.HTjtHe)",
            "body:has(.HTjtHe)",
        )) {
            assertTrue("missing $selector rule", css.contains(selector))
        }
        assertTrue("missing overflow recovery", css.contains("overflow: visible !important"))
    }

    @Test
    fun `injectionJs contains idempotency guard`() {
        val js = CosmeticFilters.injectionJs()
        assertTrue(
            "missing idempotency guard",
            js.contains("getElementById('${CosmeticFilters.STYLE_ELEMENT_ID}')"),
        )
    }

    @Test
    fun `injectionJs wraps body in try-catch`() {
        val js = CosmeticFilters.injectionJs()
        assertTrue("missing try", js.contains("try"))
        assertTrue("missing catch", js.contains("catch"))
    }

    @Test
    fun `injectionJs appends to head or documentElement`() {
        val js = CosmeticFilters.injectionJs()
        assertTrue(
            "missing head-or-documentElement fallback",
            js.contains("document.head || document.documentElement"),
        )
    }

    @Test
    fun `injectionJs sets style id from STYLE_ELEMENT_ID`() {
        val js = CosmeticFilters.injectionJs()
        assertTrue(
            "missing style id assignment",
            js.contains("s.id = '${CosmeticFilters.STYLE_ELEMENT_ID}'"),
        )
    }

    @Test
    fun `injectionJs includes the Google consent auto-resolver`() {
        // Hiding Google's consent banner never resolves the consent state, so
        // Google Images won't open a preview on thumbnail tap (diagnosed
        // on-device: tap reaches the <img>, no overlay, defaultPrevented=false).
        // The auto-resolver reproduces the working manual action — click "Reject
        // all" — and must be part of the injected JS so it runs on page load.
        val js = CosmeticFilters.injectionJs()
        assertTrue("missing google host gate", js.contains("host.indexOf('google.')"))
        assertTrue("missing reject-all id", js.contains("'#W0wltc'"))
        assertTrue("missing bounded watcher guard", js.contains("__gtGConsentWatch"))
    }

    @Test
    fun `Google consent auto-resolver clicks reject with a labelled fallback`() {
        // Primary target is the long-stable reject id; the fallback scans the
        // consent dialog's own buttons for a reject-like label so a button-id
        // rotation still resolves consent.
        val js = CosmeticFilters.GOOGLE_CONSENT_UNBLOCK_JS
        assertTrue("missing reject id", js.contains("querySelector('#W0wltc')"))
        assertTrue("missing labelled fallback", js.contains("reject|decline|refuse"))
        assertTrue("missing programmatic click", js.contains("btn.click()"))
        assertTrue("missing run-once guard", js.contains("__gtGConsentDone"))
    }

    @Test
    fun `Google consent auto-resolver retries until the consent UI is gone`() {
        // The reject button is a jsaction-delegated control whose handler may
        // not be bound when we first click, so a one-shot click can silently
        // no-op. The resolver must re-click on each tick and only conclude
        // "done" once the consent UI has actually disappeared — never on the
        // mere act of clicking.
        val js = CosmeticFilters.GOOGLE_CONSENT_UNBLOCK_JS
        assertTrue("missing consent-presence check", js.contains("function consentPresent()"))
        assertTrue("missing clicked-then-gone gate", js.contains("__gtGConsentClicked"))
        // Watch window must be meaningfully long to catch a late banner.
        assertTrue("watch window too short", js.contains("tries > 80"))
    }

    @Test
    fun `jsStringLiteral escapes backslash`() {
        assertEquals("\"a\\\\b\"", CosmeticFilters.jsStringLiteralForTest("a\\b"))
    }

    @Test
    fun `jsStringLiteral escapes double quote`() {
        assertEquals("\"a\\\"b\"", CosmeticFilters.jsStringLiteralForTest("a\"b"))
    }

    @Test
    fun `jsStringLiteral escapes newline`() {
        assertEquals("\"a\\nb\"", CosmeticFilters.jsStringLiteralForTest("a\nb"))
    }

    @Test
    fun `jsStringLiteral escapes carriage return`() {
        assertEquals("\"a\\rb\"", CosmeticFilters.jsStringLiteralForTest("a\rb"))
    }

    @Test
    fun `jsStringLiteral escapes tab`() {
        assertEquals("\"a\\tb\"", CosmeticFilters.jsStringLiteralForTest("a\tb"))
    }

    @Test
    fun `jsStringLiteral escapes line separator U plus 2028`() {
        assertEquals("\"a\\u2028b\"", CosmeticFilters.jsStringLiteralForTest("a b"))
    }

    @Test
    fun `jsStringLiteral escapes paragraph separator U plus 2029`() {
        assertEquals("\"a\\u2029b\"", CosmeticFilters.jsStringLiteralForTest("a b"))
    }
}
