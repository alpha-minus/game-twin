package com.twinlore.app.data.scraping

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class GameNameSanitizerNormalizeTest {

    private val sanitizer = GameNameSanitizer()

    @Test
    fun colon_and_dash_subtitle_separators_fold_to_space() {
        assertEquals(sanitizer.normalize("Mario : Super Party"), sanitizer.normalize("Mario - Super Party"))
        assertEquals("mario super party", sanitizer.normalize("Mario : Super Party"))
    }

    @Test
    fun trailing_digit_stays_distinct() {
        assertNotEquals(sanitizer.normalize("Shinobi"), sanitizer.normalize("Shinobi 2"))
        assertNotEquals(sanitizer.normalize("Shinobi"), sanitizer.normalize("Shinobi III"))
    }

    @Test
    fun whitespace_collapses_only_on_compare_side() {
        assertEquals(sanitizer.normalize("Super   Mario   World"), sanitizer.normalize("Super Mario World"))
    }

    @Test
    fun parenthetical_metadata_strips_generically() {
        assertEquals(sanitizer.normalize("Final Fantasy VII"), sanitizer.normalize("Final Fantasy VII (Disc 1)"))
        assertEquals(sanitizer.normalize("Sonic the Hedgehog"), sanitizer.normalize("Sonic the Hedgehog (Rev 2)"))
    }

    @Test
    fun bracketed_rom_flavors_strip_generically() {
        assertEquals(sanitizer.normalize("Super Mario World"), sanitizer.normalize("Super Mario World [Hack]"))
        assertEquals(sanitizer.normalize("Tetris"), sanitizer.normalize("Tetris [Beta]"))
    }

    @Test
    fun underscores_become_spaces() {
        assertEquals(sanitizer.normalize("Super Mario World"), sanitizer.normalize("super_mario_world"))
    }

    @Test
    fun normalize_is_idempotent() {
        val once = sanitizer.normalize("Mario : Super Party (Europe) [!]")
        val twice = sanitizer.normalize(once)
        assertEquals(once, twice)
    }
}
