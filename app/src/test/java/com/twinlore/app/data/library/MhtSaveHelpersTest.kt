package com.twinlore.app.data.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MhtSaveHelpersTest {

    @Test
    fun stashIframeSrcsJs_sets_data_original_src_only_when_missing() {
        val js = MhtSaveHelpers.STASH_IFRAME_SRCS_JS
        // The script must check getAttribute('data-original-src') before
        // overwriting — otherwise a re-run on the same WebView would clobber
        // a value the user might later rely on.
        assertTrue(
            "Expected idempotency guard on data-original-src",
            js.contains("getAttribute('data-original-src')") ||
                js.contains("getAttribute(\"data-original-src\")")
        )
        assertTrue("Expected setAttribute('data-original-src', ...)", js.contains("setAttribute('data-original-src'"))
        assertTrue("Expected iframe selector", js.contains("querySelectorAll('iframe')"))
    }

    @Test
    fun stripLazyAndRearmJs_strips_lazy_and_reassigns_src_on_incomplete_images() {
        val js = MhtSaveHelpers.STRIP_LAZY_AND_REARM_JS
        assertTrue("Expected img selector", js.contains("querySelectorAll('img')"))
        assertTrue("Expected loading=lazy strip", js.contains("removeAttribute('loading')"))
        assertTrue("Expected eager re-arm", js.contains("loading = 'eager'") || js.contains("loading=\"eager\""))
        assertTrue("Expected !img.complete guard", js.contains("!img.complete") || js.contains("img.complete"))
        assertTrue("Expected src re-assignment", js.contains("img.src ="))
    }

    @Test
    fun allImagesCompleteJs_returns_boolean_expression() {
        val js = MhtSaveHelpers.ALL_IMAGES_COMPLETE_JS
        // evaluateJavascript returns the last expression — must be a single
        // expression that resolves to a boolean. No statements, no semicolons.
        assertTrue(
            "Expected document.images traversal",
            js.contains("document.images")
        )
        assertTrue("Expected .every(...) call", js.contains(".every("))
        assertTrue("Expected i.complete check", js.contains("i.complete"))
        assertEquals("Expected no semicolons in expression-only JS", -1, js.indexOf(';'))
    }

    @Test
    fun scrollToYJs_template_produces_valid_call() {
        val js = MhtSaveHelpers.scrollToYJs(1234)
        assertEquals("window.scrollTo(0, 1234)", js)
    }

    @Test
    fun readScrollYJs_returns_single_expression() {
        val js = MhtSaveHelpers.READ_SCROLL_Y_JS
        assertTrue(js.contains("window.pageYOffset") || js.contains("window.scrollY"))
        assertEquals(-1, js.indexOf(';'))
    }

    @Test
    fun readScrollHeightJs_returns_single_expression() {
        val js = MhtSaveHelpers.READ_SCROLL_HEIGHT_JS
        assertTrue(js.contains("scrollHeight"))
        assertEquals(-1, js.indexOf(';'))
    }
}
