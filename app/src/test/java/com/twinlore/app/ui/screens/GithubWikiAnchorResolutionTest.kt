package com.twinlore.app.ui.screens

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * GitHub wiki renders heading/author-anchor ids with a `user-content-` prefix
 * AND lowercases them, while the in-page ToC/cross-reference links keep the
 * author's original case (e.g. href `#ShowdownChallenges` -> id
 * `user-content-showdownchallenges`). GitHub's client JS bridges the two online
 * but never runs in our offline viewers, so every fragment->element lookup must
 * also try the LOWERCASED prefixed id. These pins guard the directly-testable
 * JS surfaces; the inline per-viewer scroll handlers are covered by reading +
 * device verification.
 */
class GithubWikiAnchorResolutionTest {

    @Test
    fun save_time_toc_extractor_resolves_user_content_prefixed_targets() {
        assertTrue(
            "GameFAQs/hash ToC pass must fall back to the lowercased user-content- id prefix",
            TOC_EXTRACTION_JS.contains("document.getElementById('user-content-' + hash.toLowerCase())")
        )
        assertTrue(
            "Generic ToC pass must fall back to the lowercased user-content- id prefix",
            TOC_EXTRACTION_JS.contains("document.getElementById('user-content-' + id.toLowerCase())")
        )
    }
}
