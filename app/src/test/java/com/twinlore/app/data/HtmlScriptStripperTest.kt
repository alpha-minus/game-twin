package com.twinlore.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlScriptStripperTest {

    @Test
    fun `inline and external scripts are removed, body text survives`() {
        val out = HtmlScriptStripper.strip(
            "<html><head><script src=\"a.js\"></script></head>" +
                "<body><script>alert(1)</script><p>Chapter 1</p></body></html>"
        )
        assertFalse(out.contains("<script", ignoreCase = true))
        assertTrue(out.contains("Chapter 1"))
    }

    @Test
    fun `json data scripts are kept`() {
        val out = HtmlScriptStripper.strip(
            "<script type=\"application/ld+json\">{\"a\":1}</script>" +
                "<script type=\"application/json\">{\"b\":2}</script>" +
                "<script type=\"module\">import x from 'y'</script>"
        )
        assertTrue(out.contains("{\"a\":1}"))
        assertTrue(out.contains("{\"b\":2}"))
        assertFalse(out.contains("import x"))
    }

    @Test
    fun `inline event handlers are removed`() {
        val out = HtmlScriptStripper.strip("<img src=\"x.png\" onerror=\"alert(1)\" alt=\"map\">")
        assertFalse(out.contains("onerror", ignoreCase = true))
        assertTrue(out.contains("alt=\"map\""))
    }

    @Test
    fun `javascript urls are removed from href and src`() {
        val out = HtmlScriptStripper.strip(
            "<a href=\" JavaScript:alert(1)\">x</a><iframe src=\"javascript:alert(2)\"></iframe>"
        )
        assertFalse(out.contains("javascript:", ignoreCase = true))
    }

    @Test
    fun `base and meta refresh and srcdoc are removed, noscript is unwrapped`() {
        val out = HtmlScriptStripper.strip(
            "<head><base href=\"https://evil/\"><meta http-equiv=\"Refresh\" content=\"0;url=javascript:1\"></head>" +
                "<body><iframe srcdoc=\"<script>1</script>\"></iframe><noscript><p>plain</p></noscript></body>"
        )
        assertFalse(out.contains("<base", ignoreCase = true))
        assertFalse(out.contains("http-equiv", ignoreCase = true))
        assertFalse(out.contains("srcdoc", ignoreCase = true))
        assertFalse(out.contains("<noscript", ignoreCase = true))
        assertTrue(out.contains("<p>plain</p>"))
    }

    @Test
    fun `output is not pretty printed so byte offsets stay close to the input`() {
        val out = HtmlScriptStripper.strip("<p>a</p><p>b</p>")
        assertEquals("<html><head></head><body><p>a</p><p>b</p></body></html>", out)
    }

    @Test
    fun `javascript urls with embedded control characters are removed`() {
        val out = HtmlScriptStripper.strip(
            "<a href=\"jav\tascript:alert(1)\">x</a>" +
                "<iframe src=\"jav\nascript:alert(2)\"></iframe>" +
                "<a href=\"javascript:alert(3)\">y</a>"
        )
        assertFalse(out.contains("alert(1)"))
        assertFalse(out.contains("alert(2)"))
        assertFalse(out.contains("alert(3)"))
    }
}
