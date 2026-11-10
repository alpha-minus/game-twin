package com.twinlore.app.ui.screens

import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuideAnchorScriptTest {

    /** Everything outside JS string literals: what an attacker would need to reach. */
    private fun codeOutsideStrings(js: String): String =
        js.replace(Regex("\"(?:[^\"\\\\]|\\\\.)*\""), "\"\"")

    @Test
    fun `a plain fragment appears only as a quoted literal`() {
        val js = anchorScrollScript("chapter-2")
        assertTrue(js.contains(JSONObject.quote("chapter-2")))
        assertTrue(js.contains(JSONObject.quote("user-content-chapter-2")))
    }

    @Test
    fun `quotes and backslashes in the fragment cannot leave the string literal`() {
        val payload = "');fetch('file:///x');})();//"
        val js = anchorScrollScript(payload)
        val code = codeOutsideStrings(js)
        assertFalse(code.contains("fetch"))
        assertFalse(code.contains("//"))
        assertTrue(js.contains(JSONObject.quote(payload)))
    }

    @Test
    fun `a backslash-quote fragment is still one literal`() {
        val payload = "ZQ1\\'ZQ2\"ZQ3"
        val code = codeOutsideStrings(anchorScrollScript(payload))
        assertFalse(code.contains("ZQ"))
    }

    @Test
    fun `the selector uses CSS escape, not string interpolation`() {
        val js = anchorScrollScript("x")
        assertTrue(js.contains("CSS.escape("))
    }
}
