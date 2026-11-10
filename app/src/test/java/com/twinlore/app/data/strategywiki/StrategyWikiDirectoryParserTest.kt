package com.twinlore.app.data.strategywiki

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class StrategyWikiDirectoryParserTest {
    private fun fixture(name: String): String =
        File("src/test/resources/strategywiki/$name").readText()

    @Test fun extracts_member_pages_from_api_json() {
        val entries = StrategyWikiDirectoryParser.extractEntries(fixture("stage5-1.json"), stage = 5)

        // The Featured category has 17 members.
        assertEquals(17, entries.size)
        // Every entry carries the requested stage.
        assertTrue(entries.all { it.stage == 5 })
        // Page paths are bare (no origin, no /wiki/ prefix).
        assertTrue(entries.none { it.pagePath.contains("/wiki/") || it.pagePath.startsWith("http") })
        // A known member; colon stays literal in the canonical page path.
        val cv = entries.firstOrNull { it.title == "Castlevania: Circle of the Moon" }
        assertEquals("Castlevania:_Circle_of_the_Moon", cv?.pagePath)
        assertEquals(
            "https://strategywiki.org/wiki/Castlevania:_Circle_of_the_Moon/Walkthrough",
            cv?.walkthroughUrl,
        )
        // Apostrophe is percent-encoded in the canonical page path.
        val dog = entries.firstOrNull { it.title == "Dog's Life" }
        assertEquals("Dog%27s_Life", dog?.pagePath)
    }
}
