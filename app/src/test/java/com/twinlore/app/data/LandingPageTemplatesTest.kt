package com.twinlore.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class LandingPageTemplatesTest {

    @Test
    fun query_joins_gamename_and_appendword() {
        val entry = LandingEntry("Walkthrough", "walkthrough", false)
        assertEquals("Hades walkthrough", buildSearchQuery("Hades", entry))
    }

    @Test
    fun query_appends_site_when_present() {
        val entry = LandingEntry("GameFAQs", "walkthrough", false, site = "gamefaqs.gamespot.com")
        assertEquals(
            "Hades walkthrough site:gamefaqs.gamespot.com",
            buildSearchQuery("Hades", entry)
        )
    }

    @Test
    fun query_appends_filetype_when_present() {
        val entry = LandingEntry("Manual", "game manual", false, fileType = "pdf")
        assertEquals(
            "Hades game manual filetype:pdf",
            buildSearchQuery("Hades", entry)
        )
    }

    @Test
    fun query_appends_both_site_and_filetype() {
        val entry = LandingEntry(
            "Manual",
            "game manual",
            false,
            site = "archive.org",
            fileType = "pdf"
        )
        assertEquals(
            "Hades game manual site:archive.org filetype:pdf",
            buildSearchQuery("Hades", entry)
        )
    }

    @Test
    fun query_omits_blank_parts() {
        val entry = LandingEntry("", "", false, site = "example.com")
        assertEquals("Hades site:example.com", buildSearchQuery("Hades", entry))
    }

    @Test
    fun query_handles_null_game_name() {
        val entry = LandingEntry("Walkthrough", "walkthrough", false)
        assertEquals("walkthrough", buildSearchQuery(null, entry))
    }

    @Test
    fun query_handles_blank_game_name() {
        val entry = LandingEntry("Walkthrough", "walkthrough", false)
        assertEquals("walkthrough", buildSearchQuery("   ", entry))
    }
}
