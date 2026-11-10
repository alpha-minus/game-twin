package com.twinlore.app.data.privacy

import java.net.URI
import java.util.Locale

/**
 * Host- and path-pattern matching for the privacy / ads-and-banners blocking layer.
 *
 * Three roles, deliberately separated:
 *
 * - [isBlockedSubresource] matches a wider host list. It covers Quantcast / InMobi Choice CMP
 *   hosts plus a curated set of common ad-network and ad-verification hosts. Any subresource
 *   request whose host (suffix-aware, case-insensitive) lands in this list gets returned as
 *   204 by the interceptor, so ads + trackers never load.
 *
 * - [isBlockedSubresourcePath] catches cookie-consent plugins served from the page's OWN
 *   origin, where host-suffix matching would have to block the whole site to reach them. WP
 *   plugin slugs are stable across installs (`/wp-content/plugins/<slug>/`), so a contains
 *   check on the URL path is reliable. Blocking the plugin's CSS + JS suppresses the banner
 *   without affecting the rest of the page.
 *
 * - [htmlContainsQuantcastSignature] only looks for Quantcast-family hostnames. This is
 *   intentional: the HTML-rewrite path injects a TCF / GPP consent stub, and that stub is
 *   meaningful only on pages whose own scripts gate on Quantcast's consent signal. Other ad
 *   networks do not use the TCF API in the same way, so injecting the stub there would just
 *   add a no-op script to every page on the internet.
 */
internal object BlockedHosts {

    // CMP (Consent Management Platform) hosts. The TCF / GPP API stub
    // [htmlContainsQuantcastSignature] gates on is an IAB standard, not
    // Quantcast-specific, so it applies to every CMP vendor that gates the
    // page's scripts on a consent signal. consentmanager.net (used by
    // Bulbapedia, AdThrive sites etc.) qualifies — its banner DOM lives
    // under #cmpwrapper / #cmpbox; the cosmetic-filter layer hides those
    // when the script raced ahead of the block or the markup was already
    // baked into a saved MHT.
    private val QUANTCAST_SUFFIXES: List<String> = listOf(
        "consensu.org",
        "quantcast.com",
        "quantserve.com",
        "quantcount.com",
        "choice.inmobi.com",
        "cmp.inmobi.com",
        "consentmanager.net",
    )

    private val AD_NETWORK_SUFFIXES: List<String> = listOf(
        // Google Ads + Analytics
        "doubleclick.net",
        "googleadservices.com",
        "googlesyndication.com",
        "googletagmanager.com",
        "google-analytics.com",
        // Facebook
        "facebook.net",
        "connect.facebook.net",
        // Amazon Ads
        "amazon-adsystem.com",
        // Content / native ads
        "taboola.com",
        "outbrain.com",
        // Ad verification + analytics
        "adsafeprotected.com",
        "moatads.com",
        "scorecardresearch.com",
        // Programmatic ad exchanges
        "adnxs.com",
        "criteo.com",
        "pubmatic.com",
    )

    internal val BLOCKED_SUFFIXES: List<String> = QUANTCAST_SUFFIXES + AD_NETWORK_SUFFIXES

    /**
     * Path fragments for cookie-consent / GDPR-banner plugins that ship from the page's own
     * origin (typically WordPress plugins). Matched as substrings of the URL path so any
     * version / build asset under the plugin's folder is blocked. Patterns include leading
     * and trailing slashes so they only catch the plugin's own folder, not arbitrary text.
     */
    private val BLOCKED_PATH_PATTERNS: List<String> = listOf(
        "/wp-content/plugins/wpconsent-cookies-banner-privacy-suite/",
        "/wp-content/plugins/cookie-notice/",
        "/wp-content/plugins/complianz-gdpr/",
        "/wp-content/plugins/gdpr-cookie-compliance/",
        "/wp-content/plugins/cookie-law-info/",
    )

    /** Host-suffix match for the subresource-blocking decision. Suffix-aware, case-insensitive. */
    fun isBlockedSubresource(url: String): Boolean {
        val host = parseHost(url)?.lowercase(Locale.ROOT) ?: return false
        return BLOCKED_SUFFIXES.any { suffix ->
            host == suffix || host.endsWith(".$suffix")
        }
    }

    /**
     * URL-path substring match for cookie-consent plugins served from the page's own origin.
     * Case-insensitive. Returns false for URLs we can't parse.
     */
    fun isBlockedSubresourcePath(url: String): Boolean {
        val path = parsePath(url)?.lowercase(Locale.ROOT) ?: return false
        return BLOCKED_PATH_PATTERNS.any { path.contains(it) }
    }

    /**
     * Quantcast-only signature scan over an HTML prefix, used to decide whether to inject the
     * consent stub. Intentionally narrower than the subresource blocklist.
     */
    fun htmlContainsQuantcastSignature(htmlPrefix: String): Boolean {
        val lower = htmlPrefix.lowercase(Locale.ROOT)
        return QUANTCAST_SUFFIXES.any { lower.contains(it) }
    }

    private fun parseHost(url: String): String? = try {
        URI(url).host
    } catch (e: Exception) {
        null
    }

    private fun parsePath(url: String): String? = try {
        URI(url).path
    } catch (e: Exception) {
        null
    }
}
