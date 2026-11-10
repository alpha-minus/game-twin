package com.twinlore.app.ui.screens.guide

import org.junit.Assert.assertTrue
import org.junit.Test

class AnchorBadgeJsOmTest {

    @Test
    fun single_guide_img_wrap_skip_present_for_om_base() {
        val js = buildInternalAnchorBadgeJs("https://omegametroid.com/metroid-fusion-walkthrough/", "#FF4081")
        assertTrue(
            "Expected universal image-wrap anchor skip (firstChild.tagName === 'IMG')",
            js.contains("firstChild.tagName === 'IMG'")
        )
    }

    @Test
    fun single_guide_accent_hex_embedded_and_internal_badge_dropped() {
        val js = buildInternalAnchorBadgeJs("https://omegametroid.com/metroid-fusion-walkthrough/", "#FF4081")
        assertTrue(
            "Expected accent hex applied via style.setProperty with important priority",
            js.contains("setProperty('color', \"#FF4081\", 'important')")
        )
        assertTrue(
            "Expected internal anchors decorated without a badge (null svgInner)",
            js.contains("gtDecorate(a, null, 'data-pg-local-marked')")
        )
    }

    @Test
    fun single_guide_resolves_github_user_content_prefixed_anchor_targets() {
        // GitHub wiki renders heading/author-anchor ids as `user-content-<slug>`
        // AND lowercases them, while the in-page links keep the author's original
        // case (e.g. href `#ShowdownChallenges` -> id `user-content-showdownchallenges`).
        // GitHub's client JS normally bridges the two, but it doesn't run in the
        // offline viewer — so the fragment lookup must try the LOWERCASED
        // `user-content-` prefixed id, otherwise every mixed-case in-page anchor
        // is misclassified as external (open_in_new badge, no focus colour) and
        // never scrolls.
        val js = buildInternalAnchorBadgeJs(
            "https://github.com/RetroAchievements/guides/wiki/Legend-of-Zelda,-The:-A-Link-to-the-Past-(SNES)",
            "#FF4081",
        )
        assertTrue(
            "Expected fragment lookup to fall back to the lowercased user-content- id prefix",
            js.contains("document.getElementById('user-content-' + fragment.toLowerCase())")
        )
    }
}
