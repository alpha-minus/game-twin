package com.twinlore.app.ui.screens.guide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests the [parseAnchorClusterJson] function — the Kotlin half of the
 * extractor pipeline. The JS half (anchor_cluster_extract.js) is exercised
 * by the on-device checklist; Robolectric's WebView shadow doesn't execute
 * JS, so a JVM unit test of the JS itself isn't possible without adding
 * Rhino, which would be overkill.
 */
class AnchorClusterExtractorTest {

    @Test
    fun parsesValidJsonArray() {
        // Note: evaluateJavascript wraps the return value in quotes and escapes
        // inner quotes. The parser strips these. We test both the "raw" and the
        // "wrapped" shape.
        val raw = "[{\"label\":\"Story\",\"fragment\":\"story\",\"targetScrollY\":120}," +
            "{\"label\":\"Bosses\",\"fragment\":\"bosses\",\"targetScrollY\":1400}]"
        val result = parseAnchorClusterJson(raw)
        assertEquals(2, result.size)
        assertEquals(SingleGuideTocEntry("Story", "story", 120), result[0])
        assertEquals(SingleGuideTocEntry("Bosses", "bosses", 1400), result[1])
    }

    @Test
    fun unwrapsEvalJavascriptQuoting() {
        // What WebView.evaluateJavascript actually delivers: a quoted, escaped
        // JSON string. e.g. JSON `[{"a":1}]` arrives as `"[{\"a\":1}]"`.
        val wrapped =
            "\"[{\\\"label\\\":\\\"Story\\\",\\\"fragment\\\":\\\"s\\\",\\\"targetScrollY\\\":10}]\""
        val result = parseAnchorClusterJson(wrapped)
        assertEquals(1, result.size)
        assertEquals(SingleGuideTocEntry("Story", "s", 10), result[0])
    }

    @Test
    fun returnsEmptyForEmptyArray() {
        assertTrue(parseAnchorClusterJson("[]").isEmpty())
        assertTrue(parseAnchorClusterJson("\"[]\"").isEmpty())
    }

    @Test
    fun returnsEmptyForNullOrBlank() {
        assertTrue(parseAnchorClusterJson(null).isEmpty())
        assertTrue(parseAnchorClusterJson("").isEmpty())
        assertTrue(parseAnchorClusterJson("   ").isEmpty())
    }

    @Test
    fun returnsEmptyForMalformedJson() {
        assertTrue(parseAnchorClusterJson("not json").isEmpty())
        assertTrue(parseAnchorClusterJson("{\"not\":\"array\"}").isEmpty())
    }

    @Test
    fun dropsRowsWithEmptyLabel() {
        val raw = "[{\"label\":\"\",\"fragment\":\"s\",\"targetScrollY\":10}," +
            "{\"label\":\"Good\",\"fragment\":\"g\",\"targetScrollY\":20}]"
        val result = parseAnchorClusterJson(raw)
        assertEquals(1, result.size)
        assertEquals("Good", result[0].label)
    }

    @Test
    fun dropsRowsWithEmptyFragment() {
        val raw = "[{\"label\":\"A\",\"fragment\":\"\",\"targetScrollY\":10}," +
            "{\"label\":\"B\",\"fragment\":\"b\",\"targetScrollY\":20}]"
        val result = parseAnchorClusterJson(raw)
        assertEquals(1, result.size)
        assertEquals("B", result[0].label)
    }

    @Test
    fun dropsRowsWithNegativeTargetScrollY() {
        val raw = "[{\"label\":\"A\",\"fragment\":\"a\",\"targetScrollY\":-1}," +
            "{\"label\":\"B\",\"fragment\":\"b\",\"targetScrollY\":0}]"
        val result = parseAnchorClusterJson(raw)
        assertEquals(1, result.size)
        assertEquals("B", result[0].label)
    }

    @Test
    fun trimsLabelAndFragmentWhitespace() {
        val raw = "[{\"label\":\"  Story  \",\"fragment\":\" story \",\"targetScrollY\":0}]"
        val result = parseAnchorClusterJson(raw)
        assertEquals(1, result.size)
        assertEquals("Story", result[0].label)
        assertEquals("story", result[0].fragment)
    }
}
