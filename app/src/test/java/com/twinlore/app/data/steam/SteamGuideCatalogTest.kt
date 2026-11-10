package com.twinlore.app.data.steam

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SteamGuideCatalogTest {
    private val json = """
        [
          {"title":"Hollow Knight","guideId":"100","appId":367520},
          {"title":"Hollow Knight: Silksong","guideId":"101","appId":1030300},
          {"title":"Risk of Rain","guideId":"200","appId":248820},
          {"title":"Risk of Rain 2","guideId":"201","appId":632360},
          {"title":"Final Fantasy VI","guideId":"400"},
          {"title":"The Binding of Isaac","guideId":"300"},
          {"title":"The Binding of Isaac: Rebirth","guideId":"301","appId":250900}
        ]
    """.trimIndent()
    private val catalog = SteamGuideCatalog.fromJson(json)

    @Test fun exact_name_matches() {
        assertEquals("100", catalog.match("Hollow Knight")?.guideId)
    }

    @Test fun builds_full_guide_url() {
        assertEquals(
            "https://steamcommunity.com/sharedfiles/filedetails/?id=100",
            catalog.match("Hollow Knight")?.guideUrl,
        )
    }

    @Test fun base_game_does_not_match_numbered_sequel() {
        assertEquals("200", catalog.match("Risk of Rain")?.guideId)
    }

    @Test fun numbered_sequel_matches_numbered_entry() {
        assertEquals("201", catalog.match("Risk of Rain 2")?.guideId)
    }

    @Test fun strips_region_tags_from_rom_name() {
        assertEquals("100", catalog.match("Hollow Knight (USA)")?.guideId)
    }

    @Test fun unrelated_title_returns_null() {
        assertNull(catalog.match("Microsoft Excel 2019"))
    }

    @Test fun blank_input_returns_null() {
        assertNull(catalog.match("   "))
    }

    @Test fun roman_numeral_entry_matches_arabic_input() {
        // canonicalMatchTokens normalizes "VI" and "6" to the same token.
        assertEquals("400", catalog.match("Final Fantasy 6")?.guideId)
    }

    @Test fun entry_without_appId_parses_and_matches() {
        val e = catalog.match("The Binding of Isaac")
        assertEquals("300", e?.guideId)
        assertNull(e?.appId)
    }
}
