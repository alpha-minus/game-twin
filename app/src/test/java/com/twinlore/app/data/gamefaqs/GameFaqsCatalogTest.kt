package com.twinlore.app.data.gamefaqs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GameFaqsCatalogTest {
    private val json = """
        [
          {"t":"Silent Hill 2","p":"PS5","u":"ps5/383909-silent-hill-2/faqs/81517"},
          {"t":"Silent Hill","p":"PS","u":"ps/198641-silent-hill/faqs/26311"},
          {"t":"Resident Evil Village","p":"PS5","u":"ps5/292820-resident-evil-village/faqs/82553"},
          {"t":"Dragon Quest I & II","p":"SNES","u":"snes/564868-dragon-quest-i-and-ii/faqs/82562"},
          {"t":"Final Fantasy XII: The Zodiac Age","p":"PS4","u":"ps4/191202-final-fantasy-xii-the-zodiac-age/faqs/82302"},
          {"t":"God of War","p":"PS4","u":"ps4/g1/faqs/1"},
          {"t":"God of War II","p":"PS2","u":"ps2/g2/faqs/2"},
          {"t":"God of War III","p":"PS3","u":"ps3/g3/faqs/3"}
        ]
    """.trimIndent()
    private val catalog = GameFaqsCatalog.fromJson(json)

    @Test fun exact_name_matches() {
        assertEquals("ps/198641-silent-hill/faqs/26311", catalog.match("Silent Hill")?.faqPath)
    }

    @Test fun sequel_resolves_to_numbered_entry_not_base() {
        assertEquals("ps5/383909-silent-hill-2/faqs/81517", catalog.match("Silent Hill 2")?.faqPath)
    }

    @Test fun strips_region_tags_from_rom_name() {
        assertEquals(
            "ps5/292820-resident-evil-village/faqs/82553",
            catalog.match("Resident Evil Village (USA)")?.faqPath,
        )
    }

    @Test fun colon_titled_game_matches_full_entry() {
        assertEquals(
            "ps4/191202-final-fantasy-xii-the-zodiac-age/faqs/82302",
            catalog.match("Final Fantasy XII: The Zodiac Age")?.faqPath,
        )
    }

    @Test fun unrelated_title_returns_null() {
        assertNull(catalog.match("Microsoft Excel 2019"))
    }

    @Test fun base_game_does_not_match_a_numbered_sequel() {
        assertEquals("ps4/g1/faqs/1", catalog.match("God of War")?.faqPath)
    }

    @Test fun arabic_sequel_matches_roman_entry() {
        assertEquals("ps2/g2/faqs/2", catalog.match("God of War 2")?.faqPath)
    }

    @Test fun roman_sequel_matches_roman_entry() {
        assertEquals("ps2/g2/faqs/2", catalog.match("God of War II")?.faqPath)
    }

    @Test fun sequel_three_matches_only_the_third_entry() {
        assertEquals("ps3/g3/faqs/3", catalog.match("God of War 3")?.faqPath)
    }

    @Test fun absent_sequel_number_returns_null() {
        assertNull(catalog.match("God of War IV"))
    }

    @Test fun blank_returns_null() {
        assertNull(catalog.match("   "))
    }

    @Test fun split_multi_game_guide_resolves_from_either_single_game() {
        // One guide covers both games, entered once per game (same URL). Each
        // individual name — and the combined name — resolves to the guide, and
        // the sequel guard keeps "I" and "II" from cross-matching.
        val shared = "snes/564868-dragon-quest-i-and-ii/faqs/82562"
        val c = GameFaqsCatalog.fromJson(
            """
            [
              {"t":"Dragon Quest I","p":"SNES","u":"$shared"},
              {"t":"Dragon Quest I & II","p":"SNES","u":"$shared"},
              {"t":"Dragon Quest II","p":"SNES","u":"$shared"}
            ]
            """.trimIndent(),
        )
        assertEquals(shared, c.match("Dragon Quest I")?.faqPath)
        assertEquals(shared, c.match("Dragon Quest II")?.faqPath)
        assertEquals(shared, c.match("Dragon Quest I & II")?.faqPath)
    }

    @Test fun builds_faq_url() {
        assertEquals(
            "https://gamefaqs.gamespot.com/ps5/383909-silent-hill-2/faqs/81517",
            catalog.match("Silent Hill 2")?.faqUrl,
        )
    }
}
