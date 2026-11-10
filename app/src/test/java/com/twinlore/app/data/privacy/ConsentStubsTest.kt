package com.twinlore.app.data.privacy

import org.junit.Assert.assertTrue
import org.junit.Test

class ConsentStubsTest {

    @Test
    fun `stub script is wrapped in a script tag`() {
        val tag = ConsentStubs.STUB_SCRIPT_TAG
        assertTrue("starts with <script>", tag.trimStart().startsWith("<script>"))
        assertTrue("ends with </script>", tag.trimEnd().endsWith("</script>"))
    }

    @Test
    fun `stub defines window dot __tcfapi`() {
        assertTrue(ConsentStubs.STUB_SCRIPT_TAG.contains("window.__tcfapi"))
    }

    @Test
    fun `stub defines window dot __gpp`() {
        assertTrue(ConsentStubs.STUB_SCRIPT_TAG.contains("window.__gpp"))
    }

    @Test
    fun `stub answers tcloaded for addEventListener`() {
        assertTrue(ConsentStubs.STUB_SCRIPT_TAG.contains("tcloaded"))
    }

    @Test
    fun `stub reports gdprApplies false`() {
        assertTrue(ConsentStubs.STUB_SCRIPT_TAG.contains("gdprApplies: false"))
    }

    @Test
    fun `stub reports cmpLoaded true`() {
        assertTrue(ConsentStubs.STUB_SCRIPT_TAG.contains("cmpLoaded: true"))
    }

    @Test
    fun `stub has idempotency guard`() {
        assertTrue(
            "guard checks existing __tcfapi before redefining",
            ConsentStubs.STUB_SCRIPT_TAG.contains("if (window.__tcfapi)")
        )
    }

    @Test
    fun `stub is wrapped in an IIFE`() {
        val body = ConsentStubs.STUB_SCRIPT_TAG
        assertTrue(body.contains("(function"))
        assertTrue(body.contains(")();") || body.contains(")()"))
    }
}
