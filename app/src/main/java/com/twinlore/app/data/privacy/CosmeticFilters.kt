package com.twinlore.app.data.privacy

/**
 * Cosmetic / element-hide layer for the privacy-blocking feature.
 *
 * The request-layer block in [PrivacyInterceptor] stops blocked subresources from loading
 * but leaves their DOM placeholders (iframes, imgs, wrapping containers) in the page so
 * empty rectangles show up where ads used to sit. This object generates a CSS stylesheet
 * that hides those placeholders with `display: none !important;`, plus one site-specific
 * rule for the GameFAQs "Recommend this Guide?" overlay.
 *
 * The stylesheet is injected into every page via [injectionJs] at `onPageStarted` and
 * `onPageFinished`. CSS rules apply to elements as they're parsed, so a single early
 * injection covers the whole page lifetime — no MutationObserver, no per-element JS.
 *
 * Idempotent: the injected `<style>` element carries [STYLE_ELEMENT_ID] and the JS guard
 * bails early if an element with that id already exists.
 *
 * Gated by [PrivacyPreferences.enabled]; the caller is responsible for checking the flag
 * before invoking [injectionJs].
 */
internal object CosmeticFilters {

    /** Id used as an idempotency guard for the injected `<style>` element. */
    const val STYLE_ELEMENT_ID = "gametwin-cosmetic-filters"

    /** The full stylesheet. Built once at class load from [BlockedHosts.BLOCKED_SUFFIXES]. */
    val CSS_RULES: String = buildCssRules()

    /**
     * Returns a self-contained JS snippet that injects [CSS_RULES] into the current document
     * exactly once, then runs the Google consent click-unblocker. Safe to call multiple times
     * on the same document.
     *
     * The two parts have separate idempotency guards: the `<style>` injection bails if its
     * element already exists, but [GOOGLE_CONSENT_UNBLOCK_JS] is allowed to run on every call
     * (onPageStarted AND onPageFinished) because the consent backdrop can be added to the DOM
     * after the first injection — it just needs to find and neutralize it whenever it appears.
     */
    fun injectionJs(): String = STYLE_INJECT_JS + "\n" + GOOGLE_CONSENT_UNBLOCK_JS

    private val STYLE_INJECT_JS: String = """
        (function(){
            try {
                if (document.getElementById('$STYLE_ELEMENT_ID')) return;
                var s = document.createElement('style');
                s.id = '$STYLE_ELEMENT_ID';
                s.textContent = ${jsStringLiteral(CSS_RULES)};
                (document.head || document.documentElement).appendChild(s);
            } catch (e) {}
        })();
    """.trimIndent()

    /**
     * Google consent auto-resolver.
     *
     * The cosmetic CSS `display:none`s the visible consent dialog
     * (`#xe7COe` / `#CXQnmb` / `.HTjtHe`), but *hiding* the banner never *resolves*
     * the consent state. Google Search/Images then sits in a consent-pending mode
     * where its own grid JS refuses to open an image preview when you tap a
     * thumbnail — diagnosed on-device: the tap reaches the real <img> with no
     * overlay and `defaultPrevented=false`, yet nothing happens. Clicking
     * "Reject all" manually fixes it because that writes Google's consent cookie.
     *
     * So instead of fighting the symptom we reproduce the working action: click
     * the "Reject all" button. Its long-stable id is `#W0wltc` (accept-all is
     * `#L2AGLb`); as a fallback we scan the consent dialog's own buttons for one
     * whose label looks like "reject". A `.click()` fires the handler even though
     * the button is `display:none`'d by the cosmetic CSS, since programmatic
     * clicks aren't hit-tested. Scoped to google.* hosts, run once per document,
     * with a short bounded watcher because the dialog can mount after
     * onPageStarted.
     */
    val GOOGLE_CONSENT_UNBLOCK_JS: String = """
        (function(){
            try {
                var host = location.hostname || '';
                if (host.indexOf('google.') === -1) return;
                if (window.__gtGConsentDone) return;
                // The consent UI is present while any of these exist.
                function consentPresent(){
                    return !!document.querySelector('#xe7COe, #CXQnmb, .HTjtHe, #W0wltc, #L2AGLb');
                }
                function findReject(){
                    var btn = document.querySelector('#W0wltc');
                    if (btn) return btn;
                    // Fallback: scan the consent dialog's own buttons for a reject-like
                    // label across a few common locales. Scoped to the consent
                    // containers only (NOT a generic div[role=dialog]) so unrelated
                    // Google dialogs are never auto-clicked.
                    var dialog = document.querySelector('#xe7COe, #CXQnmb, .HTjtHe');
                    if (!dialog) return null;
                    var btns = dialog.querySelectorAll('button, [role="button"]');
                    for (var i = 0; i < btns.length; i++) {
                        var label = ((btns[i].getAttribute('aria-label') || '') + ' ' + (btns[i].textContent || '')).toLowerCase();
                        if (/reject|decline|refuse|necessary|ablehnen|rechazar|refuser|rifiuta|weiger|avvisa|odrzuc/.test(label)) return btns[i];
                    }
                    return null;
                }
                // Returns true only once consent is actually RESOLVED (the UI is
                // gone after we clicked) — never just because we clicked. Google's
                // "Reject all" is a jsaction-delegated button whose handler may not
                // be bound when we first click, so a single early click can no-op.
                // We therefore re-click on every tick until the consent UI
                // disappears, instead of marking done on first click.
                function tick(){
                    if (window.__gtGConsentDone) return true;
                    if (!consentPresent()){
                        // Only conclude "resolved" if we had actually clicked it;
                        // otherwise the banner just hasn't rendered yet — keep waiting.
                        if (window.__gtGConsentClicked){ window.__gtGConsentDone = true; return true; }
                        return false;
                    }
                    var btn = findReject();
                    if (btn){
                        try { btn.click(); } catch (e) {}
                        window.__gtGConsentClicked = true;
                        document.documentElement.style.setProperty('overflow', 'visible', 'important');
                        if (document.body) document.body.style.setProperty('overflow', 'visible', 'important');
                    }
                    return false;
                }
                tick();
                if (!window.__gtGConsentWatch) {
                    window.__gtGConsentWatch = true;
                    var tries = 0;
                    var iv = setInterval(function(){
                        if (tick() || ++tries > 80) clearInterval(iv);  // ~12s
                    }, 150);
                }
            } catch (e) {}
        })();
    """.trimIndent()

    /**
     * Test-only accessor for [jsStringLiteral]. Kept here (rather than making the function
     * public) so the production surface stays minimal.
     */
    internal fun jsStringLiteralForTest(s: String): String = jsStringLiteral(s)

    private fun buildCssRules(): String {
        val genericSelectors = BlockedHosts.BLOCKED_SUFFIXES.flatMap { host ->
            listOf(
                "iframe[src*=\"$host\"]",
                "img[src*=\"$host\"]",
            )
        }.joinToString(",\n")

        val genericBlock = "$genericSelectors { display: none !important; }"

        // Well-known ad-container class and id conventions used by GameFAQs and many other
        // sites. The host-suffix rules above hide the inner iframe/img, but ad slots are
        // usually wrapped in a container with a reserved height (e.g. GameFAQs sets
        // `.ad_leader_top .ad_wrap { height: 90px; }`) — hiding only the iframe leaves the
        // wrapper as empty space. These selectors target the wrappers themselves.
        //
        // CMP banner DOM (consentmanager.net used by Bulbapedia, etc.): the visible dialog
        // lives in a Shadow DOM rooted at #cmpwrapper. Hiding the wrapper collapses the
        // shadow tree along with it, so the cookie banner is suppressed even when the CMP
        // script raced ahead of the subresource block, or when the markup is already
        // present in a saved MHT (subresource blocking is a no-op for offline pages).
        //
        // OneTrust CMP (Fandom properties incl. GameFAQs, plus thousands of others): the
        // banner, the "preference center" modal, and the dark backdrop overlay all live
        // inside #onetrust-consent-sdk; hiding that wrapper collapses every part. The
        // sibling ids are listed explicitly as a safety net for SDK configurations that
        // root them outside the wrapper.
        val containerBlock = """
            .gpt-ad,
            .ad-slot,
            .ad_slot,
            .ad_wrap,
            .ad-wrap,
            .adthrive,
            [id^="div-gpt-ad-"],
            [id^="google_ads_iframe_"],
            [id^="AdThrive_"],
            #cmpwrapper,
            .cmpwrapper,
            #cmpbox,
            .cmpbox,
            #onetrust-consent-sdk,
            #onetrust-banner-sdk,
            #onetrust-pc-sdk,
            .onetrust-pc-dark-filter { display: none !important; }
        """.trimIndent()

        val siteBlock = """
            /* GameFAQs: "Recommend this Guide?" overlay. #contrib_rec is a
             * position:absolute child of the sticky #faq_header_wrap bar that
             * slides in on scroll; that wrapper's height is hardcoded to
             * reserve room for the search row PLUS this ~54px prompt band.
             * display:none'ing the absolutely-positioned prompt frees no
             * space, so the reserved band survives and paints as a dark strip
             * under the (dark-theme) site header. Collapsing the wrapper to its
             * real content height drops the band while keeping the sticky
             * search row (which carries its own height). */
            #contrib_rec { display: none !important; }
            #faq_header_wrap { height: auto !important; }

            /* Steam Community: cookie preferences banner baked into the
             * page HTML (no external CMP script to block). Pinned to
             * bottom, breaks the natural document flow. */
            #cookiePrefPopup,
            .cookiepreferences_popup { display: none !important; }

            /* StrategyWiki: ad slots are wrapped in fixed-height containers
             * (`.leaderboard-ad` for the 728x90 top/footer banners,
             * `.section-ad` for the inline 300x250 between the nav/ToC box
             * and the article body). The generic [id^="div-gpt-ad-"] rule
             * hides the inner slot but the wrapper keeps reserving its
             * height, leaving a large blank gap. Hide the wrappers too. */
            .leaderboard-ad,
            .section-ad { display: none !important; }

            /* IGN: every ad slot (the top leaderboard between the "last
             * updated" bar and the guide title, in-article units, and the
             * sticky footer) is wrapped in `.adunit-wrapper`. Blocking the ad
             * leaves the wrapper reserving its height — hide the wrapper so
             * the gap collapses. `.wiki-bobble` is the in-article ad block
             * (an "ADVERTISEMENT" label + the slot); hide the whole block so
             * neither the label nor its reserved band remains. */
            .adunit-wrapper,
            .wiki-bobble { display: none !important; }

            /* Google: pre-search consent dialog ("Before you continue to
             * Google Search"). Server-rendered inline modal — no CMP
             * script to block at the request layer. Stable obfuscated
             * IDs / class survive Google's per-build rotations (matches
             * uBO's filter set). */
            #xe7COe,
            #CXQnmb,
            .HTjtHe { display: none !important; }

            /* Google: restore body scroll on pages that shipped the
             * consent dialog. The dialog HTML arrives server-rendered
             * with `overflow: hidden` on <html> / <body> so the modal
             * acts as a focus trap. With the modal display:none'd, the
             * lock has nothing to gate and the page is left
             * unscrollable. `:has()` scopes the unlock to pages that
             * actually shipped the dialog markup, so legitimate sites
             * that lock body scroll for their own modals are untouched.
             * Selectors stay in the DOM even when display:none, so :has
             * matches as long as the markup was rendered. WebView
             * Chromium 105+ (Android 12+) supports :has natively. */
            html:has(#xe7COe),
            html:has(#CXQnmb),
            html:has(.HTjtHe),
            body:has(#xe7COe),
            body:has(#CXQnmb),
            body:has(.HTjtHe) {
                overflow: visible !important;
                position: static !important;
                height: auto !important;
            }
        """.trimIndent()

        return "$genericBlock\n\n$containerBlock\n\n$siteBlock\n"
    }

    /**
     * Escapes [s] for embedding inside a double-quoted JavaScript string literal. Handles
     * the characters that have a different meaning inside JS strings than inside Kotlin
     * strings: backslash, double quote, carriage return, newline, tab, and the two Unicode
     * line terminators that ECMA-262 treats as line breaks even inside strings (U+2028,
     * U+2029). Other characters are passed through unchanged.
     */
    private fun jsStringLiteral(s: String): String {
        val sb = StringBuilder(s.length + 2)
        sb.append('"')
        for (c in s) {
            when (c.code) {
                '\\'.code -> sb.append("\\\\")
                '"'.code -> sb.append("\\\"")
                '\n'.code -> sb.append("\\n")
                '\r'.code -> sb.append("\\r")
                '\t'.code -> sb.append("\\t")
                0x2028 -> sb.append("\\u2028")  // LINE SEPARATOR
                0x2029 -> sb.append("\\u2029")  // PARAGRAPH SEPARATOR
                else -> sb.append(c)
            }
        }
        sb.append('"')
        return sb.toString()
    }
}
