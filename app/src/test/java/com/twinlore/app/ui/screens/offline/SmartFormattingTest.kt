package com.twinlore.app.ui.screens.offline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartFormattingTest {

    @Test
    fun `toggling to raw sets the class and re-runs the fit`() {
        val js = buildSmartFormatJs(smart = false)
        assertTrue(js, js.contains("'gt-raw',true"))
        assertTrue(js, js.contains("gtApplyFormat"))
    }

    @Test
    fun `toggling to smart clears the class`() {
        assertTrue(buildSmartFormatJs(smart = true).contains("'gt-raw',false"))
    }

    @Test
    fun `raw prelude marks the body before first paint`() {
        val html = """<html><head></head><body data-gt-textfaq="1" data-maxcols="80">x</body></html>"""
        val out = applySmartFormatPrelude(html, smart = false)
        assertTrue(out, out.contains("""class="gt-raw""""))
    }

    @Test
    fun `smart prelude leaves the document untouched`() {
        val html = """<html><head></head><body data-gt-textfaq="1" data-maxcols="80">x</body></html>"""
        assertEquals(html, applySmartFormatPrelude(html, smart = true))
    }

    @Test
    fun `text zoom is applied as an explicit font size`() {
        // WebView.settings.textZoom is inert on converted guides: Chromium routes
        // it through the text autosizer, which the converter's
        // text-size-adjust:100% (GH #25) opts out of. Measured on device, driving
        // textZoom across 50/60/90/170/190 left the computed body font-size fixed
        // at 18.9678px. An explicit author font-size is not gated that way.
        val js = buildTextZoomJs(1.5f)
        assertTrue(js, js.contains("style.fontSize"))
        assertTrue(js, js.contains("*1.5"))
    }

    @Test
    fun `text zoom scales from a fixed base, not a measured one`() {
        // Measuring the document's own size was tried three ways and each failed
        // differently: reading under a live override captured the override
        // (17.0682 as the base of an 18.9678px document); reading after clearing
        // it returned the pre-autosizing 16px even with a forced layout flush;
        // and capturing only at page load left the slider inert when that call
        // did not land. The reading depends on autosizer timing, so a constant is
        // the only stable base.
        val js = buildTextZoomJs(1.5f)
        assertTrue(js, js.contains("${GUIDE_BASE_FONT_PX}*1.5"))
        assertTrue("must not measure the document", !js.contains("getComputedStyle"))
        assertTrue("must not stash state on the document", !js.contains("dataset"))
    }

    @Test
    fun `text zoom is idempotent and order independent`() {
        // Applying twice must land on the same size, so no call site has to know
        // whether another already ran.
        assertEquals(buildTextZoomJs(0.8f), buildTextZoomJs(0.8f))
        assertTrue(buildTextZoomJs(1.0f).contains("${GUIDE_BASE_FONT_PX}*1.0"))
    }

    @Test
    fun `text zoom depends on no hook inside the page`() {
        // Page-group chunks are MHT files written at split time, so a guide split
        // months ago carries whatever script it was born with. The zoom injection
        // has to stand alone; gtApplyFormat is refreshed only when it exists.
        val js = buildTextZoomJs(1.0f)
        assertTrue(js, js.contains("if(window.gtApplyFormat)"))
        assertTrue("must not require a page-side zoom hook", !js.contains("window.gtSetZoom"))
    }

    @Test
    fun `zoom prelude sizes the body before its content is parsed`() {
        // The page-finished pass sets the size after the first paint, and the
        // reader watches the text jump to it. Splicing the same script right
        // behind the body's open tag runs it while the parser is still there,
        // before a single line of the guide has been laid out.
        val html = """<html><head></head><body data-gt-textfaq="1">first line</body></html>"""
        val out = applyTextZoomPrelude(html, 1.5f)
        val script = out.indexOf("<script>")
        assertTrue(out, script >= 0)
        assertTrue("runs before the content", script < out.indexOf("first line"))
        assertTrue("runs after the body opens", script > out.indexOf("<body"))
        assertTrue(out, out.contains("${GUIDE_BASE_FONT_PX}*1.5"))
    }

    @Test
    fun `zoom prelude applies exactly what the page-finished pass applies`() {
        // Same script, so the later pass lands on a size that is already there
        // and nothing moves. A second formula here would be a second answer.
        val html = """<html><head></head><body>x</body></html>"""
        assertTrue(applyTextZoomPrelude(html, 0.8f).contains(buildTextZoomJs(0.8f)))
    }

    @Test
    fun `zoom prelude leaves a document with no body untouched`() {
        val html = "<p>fragment</p>"
        assertEquals(html, applyTextZoomPrelude(html, 1.5f))
    }

    @Test
    fun `prelude ignores documents that are not converted text guides`() {
        // EpubParser sets data-gamefaqs-converted too, but its output has no runs
        // or scroll-blocks, so raw mode has nothing to act on there.
        val html = """<html><head></head><body data-gamefaqs-converted="true">epub</body></html>"""
        assertFalse(applySmartFormatPrelude(html, smart = false).contains("gt-raw"))
    }
}
