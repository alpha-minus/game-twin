package com.twinlore.app.data.gamefaqs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameFaqsContributorParserTest {

    @Test fun extracts_title_platform_and_path() {
        val html =
            """<a href="https://gamefaqs.gamespot.com/ps5/383909-silent-hill-2/faqs/81517">Silent Hill 2 (PS5)</a>"""
        val entries = GameFaqsContributorParser.extractEntries(html)
        assertEquals(1, entries.size)
        assertEquals("Silent Hill 2", entries[0].title)
        assertEquals("PS5", entries[0].platform)
        assertEquals("ps5/383909-silent-hill-2/faqs/81517", entries[0].faqPath)
        assertEquals("https://gamefaqs.gamespot.com/ps5/383909-silent-hill-2/faqs/81517", entries[0].faqUrl)
    }

    @Test fun decodes_html_entities_in_title() {
        val html =
            """<a href="https://gamefaqs.gamespot.com/snes/564868-dragon-quest-i-and-ii/faqs/82562">Dragon Quest I &amp; II (SNES)</a>"""
        val entries = GameFaqsContributorParser.extractEntries(html)
        assertEquals("Dragon Quest I & II", entries[0].title)
        assertEquals("SNES", entries[0].platform)
    }

    @Test fun keeps_inner_parentheses_strips_only_trailing_platform() {
        val html =
            """<a href="https://gamefaqs.gamespot.com/ps4/219968-resident-evil-7-biohazard-gold-edition/faqs/82509">Resident Evil 7: biohazard - Gold Edition (PS4)</a>"""
        val entries = GameFaqsContributorParser.extractEntries(html)
        assertEquals("Resident Evil 7: biohazard - Gold Edition", entries[0].title)
        assertEquals("PS4", entries[0].platform)
    }

    @Test fun dedupes_by_canonical_title_first_wins() {
        // Same game featured twice (different FAQ ids) — first occurrence wins.
        val html = """
            <a href="https://gamefaqs.gamespot.com/psp/933072-silent-hill-origins/faqs/50993">Silent Hill: Origins (PSP)</a>
            <a href="https://gamefaqs.gamespot.com/psp/933072-silent-hill-origins/faqs/50757">Silent Hill: Origins (PSP)</a>
        """.trimIndent()
        val entries = GameFaqsContributorParser.extractEntries(html)
        assertEquals(1, entries.size)
        assertEquals("psp/933072-silent-hill-origins/faqs/50993", entries[0].faqPath)
    }

    @Test fun ignores_non_faq_links() {
        val html = """
            <a href="https://gamefaqs.gamespot.com/contribute/fotm">FAQ of the Month</a>
            <a href="https://gamefaqs.gamespot.com/ps/198641-silent-hill/boards">Boards</a>
            <a href="https://gamefaqs.gamespot.com/ps/198641-silent-hill/faqs/26311">Silent Hill (PS)</a>
        """.trimIndent()
        val entries = GameFaqsContributorParser.extractEntries(html)
        assertEquals(1, entries.size)
        assertEquals("Silent Hill", entries[0].title)
    }

    @Test fun parses_the_bundled_fotm_fixture() {
        val mht = readResource("/gamefaqs/contributor-fotm.mht")
        val entries = GameFaqsContributorParser.extractEntries(mht)
        // The FOTM snapshot has well over a thousand featured FAQs.
        assertTrue("expected many entries, got ${entries.size}", entries.size > 1000)
        // Spot-check a known entry survives parsing + suffix stripping.
        val dq = entries.firstOrNull { it.title == "Dragon Quest I & II" }
        assertEquals("SNES", dq?.platform)
        // Silent Hill 2 is NOT in the FOTM snapshot (added via supplemental).
        assertNull(entries.firstOrNull { it.title == "Silent Hill 2" })
    }

    private fun readResource(path: String): String =
        GameFaqsContributorParserTest::class.java.getResourceAsStream(path)!!
            .bufferedReader().use { it.readText() }
}
