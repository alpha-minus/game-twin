package com.twinlore.app.data.checklist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownChecklistParserTest {

    private val parser = object : MarkdownChecklistParser() {
        override fun currentDate(): String = "2026-04-28"
    }

    @Test
    fun empty_input_returns_empty() {
        val result = parser.parse("")
        assertTrue(result.tabs.isEmpty())
    }

    @Test
    fun parses_single_tab_with_section_and_items() {
        val input = """
            # FF7
            ## Bosses
            - [ ] Sephiroth
            - [x] Midgar Zolom
        """.trimIndent()
        val result = parser.parse(input)
        assertEquals(1, result.tabs.size)
        val tab = result.tabs[0]
        assertEquals("FF7", tab.name)
        assertEquals(1, tab.sections.size)
        val section = tab.sections[0]
        assertEquals("Bosses", section.name)
        assertEquals(2, section.items.size)
        assertEquals("Sephiroth", section.items[0].title)
        assertFalse(section.items[0].completed)
        assertEquals("Midgar Zolom", section.items[1].title)
        assertTrue(section.items[1].completed)
    }

    @Test
    fun capital_X_is_also_completed() {
        val result = parser.parse("""
            # A
            ## S
            - [X] Done
        """.trimIndent())
        assertTrue(result.tabs[0].sections[0].items[0].completed)
    }

    @Test
    fun asterisk_and_plus_bullets_become_items() {
        val input = """
            # A
            ## S
            * Foo
            + Bar
            - Baz
        """.trimIndent()
        val items = parser.parse(input).tabs[0].sections[0].items
        assertEquals(listOf("Foo", "Bar", "Baz"), items.map { it.title })
        assertTrue(items.all { !it.completed })
    }

    @Test
    fun plain_text_lines_become_items_lenient() {
        val input = """
            # A
            ## S
            Plain line one
            Plain line two
        """.trimIndent()
        val items = parser.parse(input).tabs[0].sections[0].items
        assertEquals(listOf("Plain line one", "Plain line two"), items.map { it.title })
    }

    @Test
    fun triple_hash_or_deeper_becomes_section_not_subsection() {
        val input = """
            # A
            ### Still a section
            - item
        """.trimIndent()
        val result = parser.parse(input)
        assertEquals("Still a section", result.tabs[0].sections[0].name)
    }

    @Test
    fun section_with_no_tab_creates_fallback_tab_with_null_name() {
        val input = """
            ## Orphan
            - item
        """.trimIndent()
        val result = parser.parse(input)
        assertEquals(1, result.tabs.size)
        assertNull(result.tabs[0].name) // null = use active tab; resolved by repository
        assertEquals("Orphan", result.tabs[0].sections[0].name)
    }

    @Test
    fun orphan_bullet_under_tab_only_becomes_its_own_section() {
        val input = """
            # A
            - orphan item
        """.trimIndent()
        val sections = parser.parse(input).tabs[0].sections
        assertEquals(1, sections.size)
        assertEquals("orphan item", sections[0].name)
        assertTrue(sections[0].items.isEmpty())
    }

    @Test
    fun single_bullet_with_no_tab_no_section_becomes_item_in_imported_section() {
        val input = "- just this"
        val result = parser.parse(input)
        val tab = result.tabs[0]
        assertNull(tab.name)
        assertEquals(1, tab.sections.size)
        assertEquals("Imported 2026-04-28", tab.sections[0].name)
        assertEquals(1, tab.sections[0].items.size)
        assertEquals("just this", tab.sections[0].items[0].title)
    }

    @Test
    fun emoji_preserved_in_names() {
        val input = """
            # 🕒 Permanent Missables
            ## 👥 Bosses
            - Godrick
        """.trimIndent()
        val tab = parser.parse(input).tabs[0]
        assertEquals("🕒 Permanent Missables", tab.name)
        assertEquals("👥 Bosses", tab.sections[0].name)
    }

    @Test
    fun tab_name_truncated_to_50() {
        val input = "# " + "x".repeat(80)
        val tab = parser.parse(input).tabs[0]
        assertEquals(50, tab.name!!.length)
    }

    @Test
    fun section_name_truncated_to_50() {
        val input = """
            # A
            ## ${"s".repeat(80)}
            - item
        """.trimIndent()
        assertEquals(50, parser.parse(input).tabs[0].sections[0].name.length)
    }

    @Test
    fun item_title_truncated_to_max() {
        // MAX_ITEM_TITLE was raised from 200 → 400 (commit 0b57af98) so users
        // can paste longer achievement descriptions before they get clipped.
        val input = """
            # A
            ## S
            - ${"y".repeat(500)}
        """.trimIndent()
        assertEquals(
            MarkdownChecklistParser.MAX_ITEM_TITLE,
            parser.parse(input).tabs[0].sections[0].items[0].title.length,
        )
    }

    @Test
    fun blank_lines_ignored() {
        val input = """
            # A


            ## S


            - x


        """.trimIndent()
        val tab = parser.parse(input).tabs[0]
        assertEquals(1, tab.sections.size)
        assertEquals(1, tab.sections[0].items.size)
    }

    @Test
    fun achievement_heuristic_triggers_on_flat_dash_list() {
        val input = """
            Find the Secret Passage - Discover the hidden shortcut in Level 3
            Speedrunner - Complete the campaign in under 2 hours
            Collector - Find all 50 hidden items
        """.trimIndent()
        val tab = parser.parse(input).tabs[0]
        assertNull(tab.name)
        assertEquals(3, tab.sections.size)
        assertEquals("Find the Secret Passage", tab.sections[0].name)
        assertEquals("Discover the hidden shortcut in Level 3", tab.sections[0].items[0].title)
        assertFalse(tab.sections[0].items[0].completed)
        assertEquals("Collector", tab.sections[2].name)
        assertEquals("Find all 50 hidden items", tab.sections[2].items[0].title)
    }

    @Test
    fun achievement_heuristic_line_without_dash_becomes_self_named_section() {
        val input = """
            A - desc A
            Lone title
            B - desc B
        """.trimIndent()
        val sections = parser.parse(input).tabs[0].sections
        assertEquals(3, sections.size)
        assertEquals("Lone title", sections[1].name)
        assertEquals("Lone title", sections[1].items[0].title)
    }

    @Test
    fun markdown_headers_suppress_achievement_heuristic() {
        val input = """
            # Tab
            ## Sec
            - Item - with dash in text
        """.trimIndent()
        val tab = parser.parse(input).tabs[0]
        assertEquals("Tab", tab.name)
        assertEquals("Sec", tab.sections[0].name)
        assertEquals("Item - with dash in text", tab.sections[0].items[0].title)
    }

    @Test
    fun multi_tab_paste_creates_multiple_tabs() {
        val input = """
            # Tab A
            ## S1
            - a
            # Tab B
            ## S2
            - b
        """.trimIndent()
        val result = parser.parse(input)
        assertEquals(2, result.tabs.size)
        assertEquals("Tab A", result.tabs[0].name)
        assertEquals("Tab B", result.tabs[1].name)
    }

    @Test
    fun bullets_without_a_section_header_become_sections_when_only_tab_header_exists() {
        val input = """
            # My Tab
            - First
            - Second
            * Third
        """.trimIndent()
        val result = parser.parse(input)
        assertEquals(1, result.tabs.size)
        val tab = result.tabs[0]
        assertEquals("My Tab", tab.name)
        assertEquals(3, tab.sections.size)
        assertEquals("First", tab.sections[0].name)
        assertTrue(tab.sections[0].items.isEmpty())
        assertEquals("Second", tab.sections[1].name)
        assertEquals("Third", tab.sections[2].name)
    }

    @Test
    fun plain_lines_with_no_headings_or_dashes_become_items_in_imported_section() {
        val input = """
            Alpha
            Beta
            Gamma
        """.trimIndent()
        val result = parser.parse(input)
        assertEquals(1, result.tabs.size)
        val tab = result.tabs[0]
        assertNull(tab.name)
        assertEquals(1, tab.sections.size)
        assertEquals("Imported 2026-04-28", tab.sections[0].name)
        assertEquals(3, tab.sections[0].items.size)
        assertEquals("Alpha", tab.sections[0].items[0].title)
        assertEquals("Beta", tab.sections[0].items[1].title)
        assertEquals("Gamma", tab.sections[0].items[2].title)
    }

    @Test
    fun existing_behavior_preserved_when_section_headers_exist() {
        val input = """
            # Tab
            ## Section
            - item1
            - item2
        """.trimIndent()
        val result = parser.parse(input)
        val sections = result.tabs[0].sections
        assertEquals(1, sections.size)
        assertEquals("Section", sections[0].name)
        assertEquals(2, sections[0].items.size)
    }

    @Test
    fun plain_text_one_line_one_item() {
        val input = "Item A      Item B    Item C"
        val result = parser.parse(input)
        val items = result.tabs[0].sections[0].items
        assertEquals(1, items.size)
        assertEquals("Item A Item B Item C", items[0].title)
    }

    @Test
    fun plain_text_strips_special_characters() {
        val input = "[Boss Fight]: Stage ^1"
        val result = parser.parse(input)
        val items = result.tabs[0].sections[0].items
        assertEquals(1, items.size)
        assertEquals("Boss Fight Stage 1", items[0].title)
    }

    @Test
    fun plain_text_strips_pipes_braces_and_joins_with_spaces() {
        val input = "Health|100 {max} (base)"
        val result = parser.parse(input)
        val items = result.tabs[0].sections[0].items
        assertEquals(1, items.size)
        assertEquals("Health 100 max base", items[0].title)
    }

    @Test
    fun plain_text_tabs_cleaned_and_joined() {
        val input = "Shot\nA\tJump\nB\tDash"
        val result = parser.parse(input)
        val items = result.tabs[0].sections[0].items
        assertEquals(3, items.size)
        assertEquals("Shot", items[0].title)
        assertEquals("A Jump", items[1].title)
        assertEquals("B Dash", items[2].title)
    }

    @Test
    fun plain_text_literal_newlines_create_items() {
        // Literal two-char \n sequences become real newlines → separate items
        val input = "Shot\\nA\\tJump\\nB\\tDash"
        val result = parser.parse(input)
        val items = result.tabs[0].sections[0].items
        assertEquals(3, items.size)
        assertEquals("Shot", items[0].title)
        assertEquals("A Jump", items[1].title)
        assertEquals("B Dash", items[2].title)
    }

    @Test
    fun markdown_not_corrupted_by_normalization() {
        val input = "# Tab\n## Section\n- [ ] Unchecked\n- [x] Checked"
        val result = parser.parse(input)
        val items = result.tabs[0].sections[0].items
        assertEquals(2, items.size)
        assertEquals("Unchecked", items[0].title)
        assertFalse(items[0].completed)
        assertEquals("Checked", items[1].title)
        assertTrue(items[1].completed)
    }

    @Test
    fun plain_text_single_entry_recognized() {
        val input = "item 1"
        val result = parser.parse(input)
        assertEquals(1, result.tabs.size)
        val section = result.tabs[0].sections[0]
        assertEquals("Imported 2026-04-28", section.name)
        assertEquals(1, section.items.size)
        assertEquals("item 1", section.items[0].title)
    }
}
