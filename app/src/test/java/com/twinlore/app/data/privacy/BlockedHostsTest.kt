package com.twinlore.app.data.privacy

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockedHostsTest {

    // --- isBlockedSubresource: Quantcast / CMP family ---

    @Test
    fun `matches consensu_org host`() {
        assertTrue(BlockedHosts.isBlockedSubresource("https://cmp.consensu.org/cmp.js"))
    }

    @Test
    fun `matches quantcast_com host`() {
        assertTrue(BlockedHosts.isBlockedSubresource("https://quantcast.com/foo"))
        assertTrue(BlockedHosts.isBlockedSubresource("https://www.quantcast.com/foo"))
    }

    @Test
    fun `matches quantserve_com host`() {
        assertTrue(BlockedHosts.isBlockedSubresource("https://pixel.quantserve.com/pixel"))
    }

    @Test
    fun `matches quantcount_com host`() {
        assertTrue(BlockedHosts.isBlockedSubresource("https://api.quantcount.com/foo"))
    }

    @Test
    fun `matches choice_inmobi_com host`() {
        assertTrue(BlockedHosts.isBlockedSubresource("https://choice.inmobi.com/sdk.js"))
    }

    @Test
    fun `matches cmp_inmobi_com host`() {
        assertTrue(BlockedHosts.isBlockedSubresource("https://cmp.inmobi.com/sdk.js"))
    }

    @Test
    fun `matches consentmanager_net host`() {
        // Used by Bulbapedia (and many AdThrive sites). Banner DOM lives in a
        // Shadow DOM under #cmpwrapper; the script that builds it is served
        // from CDN subdomains like eu-us-cdn.consentmanager.net.
        assertTrue(BlockedHosts.isBlockedSubresource("https://consentmanager.net/foo"))
        assertTrue(BlockedHosts.isBlockedSubresource("https://eu-us-cdn.consentmanager.net/delivery/flags/en.gif"))
        assertTrue(BlockedHosts.isBlockedSubresource("https://delivery.consentmanager.net/delivery/cmp.js"))
    }

    @Test
    fun `htmlContainsQuantcastSignature catches consentmanager`() {
        // Consent stub is gated on TCF/GPP, an IAB standard implemented by every
        // major CMP — including consentmanager.net.
        assertTrue(
            BlockedHosts.htmlContainsQuantcastSignature(
                "<script src=\"https://delivery.consentmanager.net/cmp.js\"></script>"
            )
        )
    }

    // --- isBlockedSubresource: ad networks ---

    @Test
    fun `matches google ad network hosts`() {
        assertTrue(BlockedHosts.isBlockedSubresource("https://stats.g.doubleclick.net/g/collect"))
        assertTrue(BlockedHosts.isBlockedSubresource("https://googleadservices.com/pagead"))
        assertTrue(BlockedHosts.isBlockedSubresource("https://pagead2.googlesyndication.com/pagead/ads"))
        assertTrue(BlockedHosts.isBlockedSubresource("https://www.googletagmanager.com/gtm.js"))
        assertTrue(BlockedHosts.isBlockedSubresource("https://www.google-analytics.com/analytics.js"))
    }

    @Test
    fun `matches facebook tracking hosts`() {
        assertTrue(BlockedHosts.isBlockedSubresource("https://connect.facebook.net/en_US/fbevents.js"))
        assertTrue(BlockedHosts.isBlockedSubresource("https://www.facebook.net/tr"))
    }

    @Test
    fun `matches amazon ad hosts`() {
        assertTrue(BlockedHosts.isBlockedSubresource("https://c.amazon-adsystem.com/aax2/apstag.js"))
    }

    @Test
    fun `matches content recommendation networks`() {
        assertTrue(BlockedHosts.isBlockedSubresource("https://cdn.taboola.com/libtrc/foo.js"))
        assertTrue(BlockedHosts.isBlockedSubresource("https://widgets.outbrain.com/outbrain.js"))
    }

    @Test
    fun `matches ad verification hosts`() {
        assertTrue(BlockedHosts.isBlockedSubresource("https://pixel.adsafeprotected.com/jsapi"))
        assertTrue(BlockedHosts.isBlockedSubresource("https://z.moatads.com/foo"))
        assertTrue(BlockedHosts.isBlockedSubresource("https://sb.scorecardresearch.com/beacon.js"))
    }

    @Test
    fun `matches programmatic ad exchanges`() {
        assertTrue(BlockedHosts.isBlockedSubresource("https://ib.adnxs.com/ut/v3/prebid"))
        assertTrue(BlockedHosts.isBlockedSubresource("https://static.criteo.com/runtime.js"))
        assertTrue(BlockedHosts.isBlockedSubresource("https://image8.pubmatic.com/AdServer/foo"))
    }

    // --- isBlockedSubresource: boundary conditions ---

    @Test
    fun `case insensitive match`() {
        assertTrue(BlockedHosts.isBlockedSubresource("https://QUANTCAST.COM/foo"))
        assertTrue(BlockedHosts.isBlockedSubresource("https://DOUBLECLICK.NET/foo"))
    }

    @Test
    fun `matches host with non-standard port`() {
        assertTrue(BlockedHosts.isBlockedSubresource("https://quantcast.com:8080/foo"))
        assertTrue(BlockedHosts.isBlockedSubresource("https://cmp.consensu.org:443/x.js"))
    }

    @Test
    fun `rejects look-alike hosts`() {
        assertFalse(BlockedHosts.isBlockedSubresource("https://notquantcast.com/foo"))
        assertFalse(BlockedHosts.isBlockedSubresource("https://quantcastfoo.com/foo"))
        assertFalse(BlockedHosts.isBlockedSubresource("https://example.com/quantcast.com/foo"))
        assertFalse(BlockedHosts.isBlockedSubresource("https://notdoubleclick.net/foo"))
    }

    @Test
    fun `rejects unrelated hosts`() {
        assertFalse(BlockedHosts.isBlockedSubresource("https://gamefaqs.com/"))
        assertFalse(BlockedHosts.isBlockedSubresource("https://google.com/"))
        assertFalse(BlockedHosts.isBlockedSubresource("https://wikipedia.org/"))
    }

    @Test
    fun `rejects malformed urls without crashing`() {
        assertFalse(BlockedHosts.isBlockedSubresource("not a url"))
        assertFalse(BlockedHosts.isBlockedSubresource(""))
    }

    // --- htmlContainsQuantcastSignature: still Quantcast-only ---

    @Test
    fun `quantcast signature found in script src`() {
        val html = """<html><head><script src="https://cmp.consensu.org/x.js"></script></head></html>"""
        assertTrue(BlockedHosts.htmlContainsQuantcastSignature(html))
    }

    @Test
    fun `quantcast signature absent when only bare name without tld is present`() {
        val html = """<html><head><meta name="quantcast" content="foo"></head></html>"""
        assertFalse(BlockedHosts.htmlContainsQuantcastSignature(html))
    }

    @Test
    fun `quantcast signature found in plain text body reference`() {
        val html = """<html><body><p>We use consensu.org for consent management.</p></body></html>"""
        assertTrue(BlockedHosts.htmlContainsQuantcastSignature(html))
    }

    @Test
    fun `quantcast signature absent on plain html`() {
        val html = """<html><head><title>Hello</title></head><body><p>Hi</p></body></html>"""
        assertFalse(BlockedHosts.htmlContainsQuantcastSignature(html))
    }

    @Test
    fun `quantcast signature found case insensitively`() {
        val html = """<html><head><script src="HTTPS://CMP.CONSENSU.ORG/x.js"></script></head></html>"""
        assertTrue(BlockedHosts.htmlContainsQuantcastSignature(html))
    }

    @Test
    fun `quantcast signature found for every quantcast hostname`() {
        val hostnames = listOf(
            "consensu.org",
            "quantcast.com",
            "quantserve.com",
            "quantcount.com",
            "choice.inmobi.com",
            "cmp.inmobi.com",
        )
        for (host in hostnames) {
            val html = """<html><head><script src="https://$host/x.js"></script></head></html>"""
            assertTrue(
                "expected signature match for $host",
                BlockedHosts.htmlContainsQuantcastSignature(html)
            )
        }
    }

    // --- isBlockedSubresourcePath: WordPress cookie-consent plugins ---

    @Test
    fun `blocks wpconsent plugin assets`() {
        assertTrue(BlockedHosts.isBlockedSubresourcePath(
            "https://omegametroid.com/wp-content/plugins/wpconsent-cookies-banner-privacy-suite/build/frontend.css?ver=abc"
        ))
        assertTrue(BlockedHosts.isBlockedSubresourcePath(
            "https://example.org/wp-content/plugins/wpconsent-cookies-banner-privacy-suite/build/frontend.js"
        ))
    }

    @Test
    fun `blocks cookie-notice plugin assets`() {
        assertTrue(BlockedHosts.isBlockedSubresourcePath(
            "https://example.com/wp-content/plugins/cookie-notice/js/front.min.js"
        ))
    }

    @Test
    fun `blocks complianz-gdpr plugin assets`() {
        assertTrue(BlockedHosts.isBlockedSubresourcePath(
            "https://example.com/wp-content/plugins/complianz-gdpr/assets/css/banner.css"
        ))
    }

    @Test
    fun `blocks gdpr-cookie-compliance plugin assets`() {
        assertTrue(BlockedHosts.isBlockedSubresourcePath(
            "https://example.com/wp-content/plugins/gdpr-cookie-compliance/dist/main.js"
        ))
    }

    @Test
    fun `blocks cookie-law-info plugin assets`() {
        assertTrue(BlockedHosts.isBlockedSubresourcePath(
            "https://example.com/wp-content/plugins/cookie-law-info/legacy/js/cookielawinfo.js"
        ))
    }

    @Test
    fun `path block is case-insensitive`() {
        assertTrue(BlockedHosts.isBlockedSubresourcePath(
            "https://Example.com/WP-Content/Plugins/Cookie-Notice/js/front.min.js"
        ))
    }

    @Test
    fun `does NOT block unrelated wp-content paths`() {
        assertFalse(BlockedHosts.isBlockedSubresourcePath(
            "https://omegametroid.com/wp-content/uploads/2022/02/t1.png"
        ))
        assertFalse(BlockedHosts.isBlockedSubresourcePath(
            "https://example.com/wp-content/themes/twentytwentyfour/style.css"
        ))
        assertFalse(BlockedHosts.isBlockedSubresourcePath(
            "https://example.com/wp-content/plugins/some-other-plugin/asset.js"
        ))
    }

    @Test
    fun `does NOT block a similarly named plugin slug that lacks the trailing slash`() {
        // Pattern requires the trailing slash to avoid matching a longer slug that begins
        // with our short name (e.g. "/cookie-notice-pro/" should not match "/cookie-notice/").
        assertFalse(BlockedHosts.isBlockedSubresourcePath(
            "https://example.com/wp-content/plugins/cookie-notice-pro/js/front.min.js"
        ))
    }

    @Test
    fun `returns false for url with no path`() {
        assertFalse(BlockedHosts.isBlockedSubresourcePath("https://example.com"))
        assertFalse(BlockedHosts.isBlockedSubresourcePath("not a url"))
    }

    @Test
    fun `ad-network only HTML does NOT trigger consent stub injection`() {
        // Subresource blocklist includes doubleclick + facebook + taboola + etc, but the HTML
        // signature scanner intentionally does NOT match them. Pages that load these without
        // also using Quantcast's TCF should not have the consent stub injected (the stub is
        // a no-op for non-TCF pages, so injecting it would just be useless overhead).
        val html = """<html><head>
            <script src="https://www.googletagmanager.com/gtm.js"></script>
            <script src="https://connect.facebook.net/fbevents.js"></script>
            <script src="https://cdn.taboola.com/libtrc/foo.js"></script>
            </head></html>"""
        assertFalse(BlockedHosts.htmlContainsQuantcastSignature(html))
    }
}
