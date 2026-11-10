package com.twinlore.app.ui.screens

import com.twinlore.app.data.AppFont
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PaletteCssShadowTest {

    @Test
    fun `palette css does not force a text shadow onto every element`() {
        val css = buildPaletteCss("#101010", "#E0E0E0", AppFont.DEFAULT)
        assertFalse(
            "blanket text-shadow makes light-theme text fuzzy; shadows must only be normalized where sites define them",
            css.contains("text-shadow:0 1px 2px"),
        )
        // The img reset is fine to keep; what must be gone is the forced shadow.
        assertTrue(css.contains("background-color:#101010"))
        assertTrue(css.contains("color:#E0E0E0"))
    }

    @Test
    fun `palette css caps oversized images to the content width`() {
        // Saved pages frequently lose the source stylesheet that constrained
        // images (GitHub wiki banner art, MediaWiki sprites). Without a cap an
        // oversized image overflows the text column and forces horizontal
        // scroll, so its right edge no longer aligns with the text. Cap to the
        // content width (never enlarge) and keep aspect ratio.
        val css = buildPaletteCss("#101010", "#E0E0E0", AppFont.DEFAULT)
        assertTrue("images must be capped to 100% of the content width",
            css.contains("max-width:100%!important"))
        assertTrue("capped images must preserve aspect ratio",
            css.contains("height:auto!important"))
        // A long unbroken token (URL, hex dump) must wrap instead of forcing
        // the whole page to scroll sideways.
        assertTrue("long unbroken strings must wrap, not force horizontal scroll",
            css.contains("overflow-wrap:break-word"))
    }

    @Test
    fun `palette js includes the shadow normalizer with document-scoped idempotency`() {
        val js = buildPaletteCssJs("#FFFFFF", "#101010", AppFont.DEFAULT)
        // Recolors EXISTING site shadows to black (rgb/rgba, alpha preserved),
        // instead of stamping shadows on everything.
        assertTrue(js.contains("textShadow"))
        assertTrue(js.contains("rgba(0, 0, 0,"))
        assertTrue(js.contains("rgb(0, 0, 0)"))
        // Document-scoped flag per the WebView injection convention (NOT window —
        // loadDataWithBaseURL swaps documents but keeps window alive).
        assertTrue(js.contains("document.gtShadowNormalized"))
        assertFalse(js.contains("window.gtShadowNormalized"))
    }
}
