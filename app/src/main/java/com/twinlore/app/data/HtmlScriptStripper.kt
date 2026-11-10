package com.twinlore.app.data

import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/**
 * Removes everything that can execute from HTML the app did not write, before
 * it is stored as a guide. Saved guides are loaded from a file:// origin in a
 * JavaScript-enabled WebView (the app's own scripts for TOC, markers and
 * find-on-page run there), so a page's own scripts must not come along.
 *
 * What goes: script elements (except JSON data blocks, which sites like
 * GameFAQs use for page metadata), inline event handlers, javascript: URLs in
 * any attribute, base (so relative links cannot be redirected), meta refresh,
 * iframe srcdoc. noscript is unwrapped so its fallback content shows.
 *
 * Pretty-printing is off so the stored HTML stays as close to the source as
 * possible for scroll-position and marker offsets.
 */
object HtmlScriptStripper {

    private val keepScriptTypes = setOf("application/json", "application/ld+json")

    fun strip(html: String, baseUri: String = ""): String {
        val doc = Jsoup.parse(html, baseUri)
        doc.outputSettings().prettyPrint(false)
        strip(doc)
        return doc.outerHtml()
    }

    fun strip(doc: Document) {
        doc.select("script").forEach { script ->
            if (script.attr("type").trim().lowercase() !in keepScriptTypes) script.remove()
        }
        doc.select("noscript").forEach { it.unwrap() }
        doc.select("base").remove()
        doc.select("meta[http-equiv]").forEach { meta ->
            if (meta.attr("http-equiv").trim().equals("refresh", ignoreCase = true)) meta.remove()
        }
        doc.select("iframe[srcdoc]").forEach { it.removeAttr("srcdoc") }
        for (element in doc.allElements) {
            val doomed = element.attributes().asList()
                .filter { attr ->
                    attr.key.startsWith("on", ignoreCase = true) ||
                        isJavascriptUrl(attr.value)
                }
                .map { it.key }
            doomed.forEach { element.removeAttr(it) }
        }
    }

    /**
     * Check if a value is a javascript: URL, accounting for embedded control characters.
     * Per WHATWG URL Standard, a browser removes all ASCII tab, LF and CR from anywhere
     * in the string and strips leading/trailing C0 controls and space before parsing the
     * scheme. An attribute value like "jav<TAB>ascript:alert(1)" is bypassable without
     * this normalization.
     */
    private fun isJavascriptUrl(value: String): Boolean {
        // Remove tab, newline, carriage return from anywhere in the value
        val normalized = value
            .replace("\t", "")
            .replace("\n", "")
            .replace("\r", "")

        // Trim leading and trailing C0 controls (code points <= 0x20)
        val trimmed = normalized.trim().trimStart { it.code <= 0x20 }.trimEnd { it.code <= 0x20 }

        return trimmed.startsWith("javascript:", ignoreCase = true)
    }
}
