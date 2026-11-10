package com.twinlore.app.data.strategywiki

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class StrategyWikiCatalogTest {
    private val json = """
        [
          {"t":"Luigi's Mansion","a":"Luigi%27s_Mansion","s":4},
          {"t":"Super Metroid","a":"Super_Metroid","s":5},
          {"t":"Life Force","a":"Life_Force","s":4},
          {"t":"Final Fantasy VI","a":"Final_Fantasy_VI","s":3},
          {"t":"Castlevania: Circle of the Moon","a":"Castlevania:_Circle_of_the_Moon","s":5},
          {"t":"God of War","a":"God_of_War","s":3},
          {"t":"God of War II","a":"God_of_War_II","s":3},
          {"t":"God of War III","a":"God_of_War_III","s":3},
          {"t":"Zwackery","a":"Zwackery","s":2}
        ]
    """.trimIndent()
    private val catalog = StrategyWikiCatalog.fromJson(json)

    @Test fun exact_name_matches() {
        assertEquals("Luigi%27s_Mansion", catalog.match("Luigi's Mansion")?.pagePath)
    }

    @Test fun strips_region_tags_from_rom_name() {
        assertEquals("Super_Metroid", catalog.match("Super Metroid (USA)")?.pagePath)
    }

    @Test fun fuzzy_edition_suffix_still_matches() {
        // "Final Fantasy VI Advance" (a real GBA port name) shares 3/4 tokens
        // with "Final Fantasy VI" → Jaccard 0.75 ≥ 0.6.
        assertEquals("Final_Fantasy_VI", catalog.match("Final Fantasy VI Advance")?.pagePath)
    }

    @Test fun colon_titled_game_matches_full_entry() {
        assertEquals(
            "Castlevania:_Circle_of_the_Moon",
            catalog.match("Castlevania: Circle of the Moon (USA)")?.pagePath,
        )
    }

    @Test fun unrelated_title_returns_null() {
        assertNull(catalog.match("Microsoft Excel 2019"))
    }

    // --- Sequel-number guard: numerals must match exactly ---

    @Test fun base_game_does_not_match_a_numbered_sequel() {
        // "God of War" must resolve to the base entry, never God of War II/III.
        assertEquals("God_of_War", catalog.match("God of War")?.pagePath)
    }

    @Test fun base_game_without_entry_returns_null_not_a_sequel() {
        // If only the sequels existed, "Tekken" must not fall onto "Tekken 2".
        val c = StrategyWikiCatalog.fromJson(
            """[{"t":"Tekken 2","a":"Tekken_2","s":3}]""",
        )
        assertNull(c.match("Tekken"))
    }

    @Test fun arabic_sequel_matches_roman_entry() {
        // "God of War 2" must hit "God of War II" (2 ≡ II), not the base or III.
        assertEquals("God_of_War_II", catalog.match("God of War 2")?.pagePath)
    }

    @Test fun roman_sequel_matches_roman_entry() {
        assertEquals("God_of_War_II", catalog.match("God of War II")?.pagePath)
    }

    @Test fun roman_input_matches_arabic_entry() {
        val c = StrategyWikiCatalog.fromJson(
            """[{"t":"Final Fantasy 7","a":"Final_Fantasy_7","s":4}]""",
        )
        assertEquals("Final_Fantasy_7", c.match("Final Fantasy VII")?.pagePath)
    }

    @Test fun sequel_three_matches_only_the_third_entry() {
        assertEquals("God_of_War_III", catalog.match("God of War 3")?.pagePath)
        assertEquals("God_of_War_III", catalog.match("God of War III (USA)")?.pagePath)
    }

    @Test fun sequel_two_never_matches_sequel_three() {
        // Belt-and-suspenders: 2 must not slip onto the III entry.
        assertEquals("God_of_War_II", catalog.match("God of War 2")?.pagePath)
    }

    @Test fun absent_sequel_number_returns_null() {
        // No God of War IV entry exists → no match (not II or III).
        assertNull(catalog.match("God of War IV"))
    }

    @Test fun blank_returns_null() {
        assertNull(catalog.match("   "))
    }

    // --- Completion-stage floor: "Maturing" pages are not offered ---

    /**
     * A stage-2 ("Maturing") page is a half-written walkthrough, and the vault presents a match as
     * ready-made help rather than as a search result - so an exact-name hit on one must still come
     * back as "no guide". "Zwackery" is in the fixture above at stage 2 and nowhere else.
     */
    @Test fun maturing_entry_never_matches_even_on_an_exact_name() {
        assertNull(catalog.match("Zwackery"))
    }

    /**
     * And the bundled asset the app actually ships carries none of them either - the filter above
     * is the guarantee, this is the file being clean so the filter has nothing to do. Read from
     * the module's own source tree, the same way `StrategyWikiCatalogRegeneratorTest` writes it.
     */
    @Test fun the_bundled_catalog_has_no_maturing_entries() {
        val asset = File("src/main/assets/${StrategyWikiCatalog.ASSET_PATH}")
        assertTrue("Missing asset: ${asset.absolutePath}", asset.exists())
        val text = asset.readText()
        // Parsed and matched against, not only scanned: a guard that only greps would pass on a
        // file the app cannot read at all, which is a worse failure than a stage-2 entry surviving.
        assertEquals(
            "Luigi%27s_Mansion",
            StrategyWikiCatalog.fromJson(text).match("Luigi's Mansion")?.pagePath,
        )
        val entries = Regex(""""s":(\d+)""").findAll(text).map { it.groupValues[1].toInt() }.toList()
        assertTrue("The bundled catalog is empty", entries.isNotEmpty())
        assertEquals(
            "the bundled catalog still carries ${entries.count { it < 3 }} sub-Thorough entries",
            0,
            entries.count { it < 3 },
        )
    }

    @Test fun builds_walkthrough_url() {
        assertEquals(
            "https://strategywiki.org/wiki/Luigi%27s_Mansion/Walkthrough",
            catalog.match("Luigi's Mansion")?.walkthroughUrl,
        )
    }
}
