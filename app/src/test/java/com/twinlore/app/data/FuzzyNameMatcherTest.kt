package com.twinlore.app.data

import com.twinlore.app.data.scraping.GameNameSanitizer
import com.twinlore.app.domain.Vault
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Date

class FuzzyNameMatcherTest {
    private val matcher = FuzzyNameMatcher(GameNameSanitizer())

    private fun vault(name: String) = Vault(
        id = name, name = name, romPath = null, romFilename = null,
        systemeid = null, gameId = null, source = null,
        lastOpened = null, createdAt = Date(),
    )

    private fun match(candidate: String, vararg existing: String) =
        matcher.findBestMatch(candidate, existing.map { vault(it) })

    // --- Sequel-number guard: differ only by number → NOT a near-duplicate ---

    @Test fun base_game_does_not_match_a_numbered_sequel() {
        assertNull(match("God of War", "God of War 2"))
    }

    @Test fun different_arabic_sequels_do_not_match() {
        assertNull(match("Final Fantasy 7", "Final Fantasy 8"))
    }

    @Test fun different_roman_sequels_do_not_match() {
        assertNull(match("Final Fantasy VII", "Final Fantasy VIII"))
    }

    @Test fun arabic_and_roman_of_same_number_still_match() {
        // "God of War 2" and "God of War II" are the same game → still warn.
        val r = match("God of War 2", "God of War II")
        assertNotNull(r)
        assertEquals("God of War II", r?.vault?.displayName)
    }

    // --- Existing behavior preserved when numbers are equal/absent ---

    @Test fun exact_name_still_matches() {
        val r = match("Chrono Trigger", "Chrono Trigger")
        assertNotNull(r)
        assertEquals(1.0, r!!.similarity, 0.0001)
    }

    @Test fun typo_near_duplicate_still_matches() {
        // No numbers either side → guard is a no-op, Levenshtein still catches it.
        assertNotNull(match("Chrono Triger", "Chrono Trigger"))
    }

    @Test fun region_tag_difference_still_matches() {
        // Region tag carries no number; same {} signature → similarity decides.
        assertNotNull(match("Final Fantasy VII", "Final Fantasy VII (USA)"))
    }

    @Test fun unrelated_names_do_not_match() {
        assertNull(match("Tetris", "Super Mario World"))
    }

    @Test fun blank_candidate_returns_null() {
        assertNull(match("   ", "Halo"))
    }

    // --- Edit cap: a fixed ratio is too generous once names get long ---
    // At 80% alone, a 37-character name may differ by 7 characters, which is a whole subtitle.
    // These two pairs cleared the ratio (0.865 and 0.846) and are different games.

    @Test fun subtitled_siblings_do_not_match_despite_clearing_the_ratio() {
        assertNull(
            match(
                "The Legend of Zelda: Oracle of Seasons",
                "The Legend of Zelda: Oracle of Ages",
            )
        )
        assertNull(match("Castlevania: Aria of Sorrow", "Castlevania: Dawn of Sorrow"))
    }

    @Test fun long_names_still_match_across_punctuation_and_edition_tags() {
        // 0 edits once punctuation is normalized away.
        assertNotNull(
            match(
                "The Legend of Zelda: Ocarina of Time",
                "The Legend of Zelda Ocarina of Time",
            )
        )
        // 3 edits: within the cap, and the pair is the same game.
        assertNotNull(match("Resident Evil 4", "Resident Evil 4 HD"))
        assertNotNull(match("Final Fantasy VII", "Final Fantasy 7"))
    }

    @Test fun short_names_keep_the_ratio_floor() {
        // "Halo" vs "Halo 2" is caught by the number guard; this pair has no numbers and is
        // 3 edits apart, which the cap alone would allow. The 80% ratio is what rejects it.
        assertNull(match("Ico", "Rico"))
        assertNull(match("Myst", "Mist Land"))
    }
}
