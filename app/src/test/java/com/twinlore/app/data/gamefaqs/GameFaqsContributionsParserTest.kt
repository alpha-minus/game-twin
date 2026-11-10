package com.twinlore.app.data.gamefaqs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameFaqsContributionsParserTest {

    // Mirrors the real contributions markup: a bold game header followed by one
    // or more guide rows, each optionally HTML-flaired.
    private val html = """
        <li>
          <a class="bold float_l" href="https://gamefaqs.gamespot.com/gamecube/561645-beyond-good-and-evil/faqs">Beyond Good &amp; Evil</a>
          <div class="meta float_l">• <a class="link_color" href="https://gamefaqs.gamespot.com/gamecube/561645-beyond-good-and-evil/faqs/28092">Guide and Walkthrough</a></div>
          <div class="meta float_r">v.1.0, 100KB, 01/01/2005</div>
        </li>
        <li>
          <a class="bold float_l" href="https://gamefaqs.gamespot.com/wii-u/718915-super-mario-3d-world/faqs">Super Mario 3D World</a>
          <div class="meta float_l">• <a class="link_color" href="https://gamefaqs.gamespot.com/wii-u/718915-super-mario-3d-world/faqs/68329">Guide and Walkthrough</a> <span class="flair">HTML</span> <b>*Most Recommended*</b></div>
          <div class="meta float_r">v.Final, 161KB, 12/28/2013</div>
        </li>
        <li>
          <a class="bold float_l" href="https://gamefaqs.gamespot.com/ps3/666508-killer-is-dead/faqs">Killer is Dead</a>
          <div class="meta float_l">• <a class="link_color" href="https://gamefaqs.gamespot.com/ps3/666508-killer-is-dead/faqs/67768">Video Walkthrough</a></div>
          <div class="meta float_r">v.Kev, 144KB, 09/27/2013</div>
        </li>
    """.trimIndent()

    @Test fun extracts_guides_with_type_platform_and_html_flag() {
        val guides = GameFaqsContributionsParser.extractGuides(html)
        assertEquals(3, guides.size)

        val bge = guides[0]
        assertEquals("Beyond Good & Evil", bge.title)
        assertEquals("GAMECUBE", bge.platform)
        assertEquals("gamecube/561645-beyond-good-and-evil/faqs/28092", bge.faqPath)
        assertEquals("Guide and Walkthrough", bge.type)
        assertFalse(bge.isHtml)

        val smw = guides[1]
        assertEquals("Super Mario 3D World", smw.title)
        assertTrue(smw.isHtml)

        val kid = guides[2]
        assertEquals("Video Walkthrough", kid.type)
    }

    @Test fun selectEntries_drops_unwanted_types() {
        val entries = GameFaqsContributionsParser.selectEntries(
            GameFaqsContributionsParser.extractGuides(html),
        )
        // "Video Walkthrough" is filtered out; the two allowed guides survive.
        assertEquals(2, entries.size)
        assertNull(entries.firstOrNull { it.title == "Killer is Dead" })
    }

    @Test fun selectEntries_prefers_html_for_same_game() {
        val guides = listOf(
            ContributionGuide("Chrono Trigger", "SNES", "snes/1-chrono-trigger/faqs/100", "FAQ", isHtml = false),
            ContributionGuide("Chrono Trigger", "DS", "ds/2-chrono-trigger/faqs/200", "Guide and Walkthrough", isHtml = true),
        )
        val entries = GameFaqsContributionsParser.selectEntries(guides)
        assertEquals(1, entries.size)
        assertEquals("ds/2-chrono-trigger/faqs/200", entries[0].faqPath)
    }

    @Test fun selectEntries_keeps_first_when_no_html() {
        val guides = listOf(
            ContributionGuide("Chrono Trigger", "SNES", "snes/1-chrono-trigger/faqs/100", "FAQ", isHtml = false),
            ContributionGuide("Chrono Trigger", "DS", "ds/2-chrono-trigger/faqs/200", "Guide", isHtml = false),
        )
        val entries = GameFaqsContributionsParser.selectEntries(guides)
        assertEquals(1, entries.size)
        assertEquals("snes/1-chrono-trigger/faqs/100", entries[0].faqPath)
    }

    @Test fun parses_a_bundled_community_fixture() {
        val mht = readResource("/gamefaqs/sample-contributions.mht")
        val guides = GameFaqsContributionsParser.extractGuides(mht)
        assertTrue("expected guides, got ${guides.size}", guides.size > 20)
        // Every row binds to a non-blank game title.
        assertTrue(guides.all { it.title.isNotBlank() })
    }

    private fun readResource(path: String): String =
        GameFaqsContributionsParserTest::class.java.getResourceAsStream(path)!!
            .bufferedReader().use { it.readText() }
}
