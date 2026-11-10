package com.twinlore.app.data.local

import com.twinlore.app.domain.HistoryEntry
import org.junit.Assert.assertEquals
import org.junit.Test

class BrowserTabHistorySerializerTest {

    @Test fun `round-trip preserves entries in order`() {
        val entries = listOf(
            HistoryEntry("https://a.example/", "Page A"),
            HistoryEntry("https://b.example/", "Page B"),
            HistoryEntry("https://c.example/?q=1#x", "")
        )
        val json = BrowserTabHistorySerializer.toJson(entries)
        val parsed = BrowserTabHistorySerializer.fromJson(json, fallbackUrl = "x", fallbackTitle = "x")
        assertEquals(entries, parsed)
    }

    @Test fun `empty list serializes to empty array`() {
        assertEquals("[]", BrowserTabHistorySerializer.toJson(emptyList()))
    }

    @Test fun `parsing empty array returns single fallback entry`() {
        val parsed = BrowserTabHistorySerializer.fromJson(
            "[]",
            fallbackUrl = "https://fallback/",
            fallbackTitle = "Fallback"
        )
        assertEquals(listOf(HistoryEntry("https://fallback/", "Fallback")), parsed)
    }

    @Test fun `parsing garbage returns single fallback entry`() {
        val parsed = BrowserTabHistorySerializer.fromJson(
            "not json at all",
            fallbackUrl = "https://fallback/",
            fallbackTitle = "Fallback"
        )
        assertEquals(listOf(HistoryEntry("https://fallback/", "Fallback")), parsed)
    }

    @Test fun `parsing entries with quotes and unicode round-trips`() {
        val entries = listOf(HistoryEntry("https://x/?q=\"hi\"&n=1", "It's \"awesome\" — ✓"))
        val json = BrowserTabHistorySerializer.toJson(entries)
        val parsed = BrowserTabHistorySerializer.fromJson(json, "x", "x")
        assertEquals(entries, parsed)
    }

    @Test fun `parsing entry missing title field defaults to empty string`() {
        val parsed = BrowserTabHistorySerializer.fromJson(
            """[{"url":"https://x/"}]""",
            fallbackUrl = "x",
            fallbackTitle = "x"
        )
        assertEquals(listOf(HistoryEntry("https://x/", "")), parsed)
    }

    @Test fun `partial corruption skips bad entries and keeps good ones`() {
        // Array with a valid entry, a JSON null, a string, and another valid entry.
        // Bad elements should be skipped; valid ones survive.
        val parsed = BrowserTabHistorySerializer.fromJson(
            """[{"url":"https://a/","title":"A"},null,"corrupt",{"url":"https://b/","title":"B"}]""",
            fallbackUrl = "x",
            fallbackTitle = "x"
        )
        assertEquals(
            listOf(
                HistoryEntry("https://a/", "A"),
                HistoryEntry("https://b/", "B")
            ),
            parsed
        )
    }
}
