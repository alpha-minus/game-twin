package com.twinlore.app.ui.screens

import org.junit.Assert.assertTrue
import org.junit.Test

class LocalLinkMarkerJsOmTest {

    @Test
    fun group_img_wrap_skip_present_for_om_first_href() {
        val js = buildLocalLinkMarkerJs(listOf("https://omegametroid.com/page-1/", "https://omegametroid.com/page-2/"), "#FF4081")
        assertTrue(
            "Expected universal image-wrap anchor skip (firstChild.tagName === 'IMG')",
            js.contains("firstChild.tagName === 'IMG'")
        )
    }

    @Test
    fun group_emptyList_emits_non_blank_js() {
        val js = buildLocalLinkMarkerJs(emptyList(), "#FF4081")
        assertTrue("buildLocalLinkMarkerJs must emit non-blank JS on empty input",
            js.isNotBlank())
    }

    @Test
    fun group_normalized_matching_handles_relative_and_www_variants() {
        // Relative hrefs resolve against the localhost.invalid document base,
        // so they must be matched by PATH against the saved pages — exact
        // string comparison against the original https://… URLs can never hit.
        val js = buildLocalLinkMarkerJs(listOf("https://www.omegametroid.com/page-1/"), "#FF4081")
        assertTrue(
            "localhost.invalid-resolved links must match by path",
            js.contains("rp.host === 'localhost.invalid'") && js.contains("pathSet[rp.rest]")
        )
        assertTrue(
            "www. must be stripped during normalization",
            js.contains("indexOf('www.') === 0")
        )
        assertTrue(
            "bare fragments are same-page anchors, always local",
            js.contains("rawHref.charAt(0) === '#'")
        )
    }

    @Test
    fun group_accent_hex_embedded_and_internal_badge_dropped() {
        val js = buildLocalLinkMarkerJs(listOf("https://omegametroid.com/page-1/"), "#FF4081")
        assertTrue(
            "Expected accent hex applied via style.setProperty with important priority",
            js.contains("setProperty('color', \"#FF4081\", 'important')")
        )
        assertTrue(
            "Expected internal anchors decorated without a badge (null svgInner)",
            js.contains("gtDecorate(a, null, 'data-pg-local-marked')")
        )
    }
}
