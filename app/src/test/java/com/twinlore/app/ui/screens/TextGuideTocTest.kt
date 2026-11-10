package com.twinlore.app.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for contents-block detection ([detectGuideTocBlock]) and TOC-guided
 * section matching. Every fixture format below mirrors a real GameFAQs guide
 * style from the curated-catalog corpus.
 */
class TextGuideTocTest {

    // ── header recognition ──

    @Test
    fun `recognizes plain and ornamented toc headers`() {
        assertTrue(isTocHeaderLine("Table of Contents"))
        assertTrue(isTocHeaderLine("TABLE OF CONTENTS"))
        assertTrue(isTocHeaderLine("CONTENTS"))
        assertTrue(isTocHeaderLine("********OUTLINE********"))
        assertTrue(isTocHeaderLine("O==<   Table of Contents   >==O"))
        assertTrue(isTocHeaderLine("=-=- - T A B L E  O F  C O N T E N T S - -=-="))
        assertTrue(isTocHeaderLine("-------------------- Table of Contents --------------------"))
    }

    @Test
    fun `prose mentioning contents is not a header`() {
        assertFalse(isTocHeaderLine("You can find each section in the table of contents listed above, use Ctrl+F to jump."))
        assertFalse(isTocHeaderLine("The contents of the chest are random."))
        assertFalse(isTocHeaderLine(""))
    }

    // ── heading keys ──

    @Test
    fun `heading key strips numbering ornaments and codes`() {
        assertEquals("introduction", guideHeadingKey("1. Introduction"))
        assertEquals("therunninggame", guideHeadingKey("  2-1. THE RUNNING GAME"))
        assertEquals("missions1120", guideHeadingKey("3.02. Missions 11-20"))
        assertEquals("story", guideHeadingKey("A. Story"))
        assertEquals("notes", guideHeadingKey("- 1.1 Notes"))
        assertEquals("introduction", guideHeadingKey("Section I:  Introduction"))
        assertEquals("whatsnew", guideHeadingKey("-=  1. What's New -="))
        assertEquals("controls", guideHeadingKey("1. - Controls"))
        assertEquals("sequence1peaceatlast", guideHeadingKey("Sequence 1: Peace at Last [4.1]"))
    }

    @Test
    fun `heading key survives titles that start with numbers`() {
        // 4-digit numbers are not enumeration
        assertEquals("1602adbasics", guideHeadingKey("1602 A.D. Basics"))
    }

    // ── entry parsing per corpus format ──

    private fun toc(vararg body: String): List<String> = body.toList()

    @Test
    fun `parses plain numbered contents list`() {
        val lines = toc(
            "Some Game FAQ", "",
            "Table of Contents", "",
            "1. Introduction",
            "2. Controls",
            "3. Walkthrough",
            "4. Credits",
            "", "",
            "1. INTRODUCTION", "body text",
        )
        val block = detectGuideTocBlock(lines)
        assertNotNull(block)
        assertEquals(
            listOf("introduction", "controls", "walkthrough", "credits"),
            block!!.entries.map { it.key },
        )
        // block ends right after its last entry line
        assertEquals(8, block.endLine)
    }

    @Test
    fun `parses outline style with indentation blank lines and subentries`() {
        val lines = toc(
            "********OUTLINE********", "",
            "  1. INTRODUCTION", "",
            "  2. CONTROLS AND RULES", "",
            "      2-1. THE RUNNING GAME", "",
            "  3. LEVELS/TEAMS", "",
            "  4. CREDITS", "",
            "  1. INTRODUCTION", "body",
        )
        val block = detectGuideTocBlock(lines)
        assertNotNull(block)
        assertEquals(5, block!!.entries.size)
        assertEquals("therunninggame", block.entries[2].key)
    }

    @Test
    fun `parses dot leader entries with page codes and stops at prose`() {
        val lines = toc(
            "------ Table of Contents ------",
            "===========================================================================",
            "          1. - Controls..........................................0001      ",
            "          2. - Walkthrough.......................................0002      ",
            "          3. - Passwords.........................................0003      ",
            "          4. - Disclaimer........................................0004      ",
            "",
            "   Please Note - This whole FAQ is searchable. That means you could easily ",
            "   find whichever section you are looking for. Just press ctrl+f and type  ",
            "   the name of the section you want to find quickly and easily today.      ",
        )
        val block = detectGuideTocBlock(lines)
        assertNotNull(block)
        assertEquals(
            listOf("controls", "walkthrough", "passwords", "disclaimer"),
            block!!.entries.map { it.key },
        )
        // page numbers are not search codes
        assertEquals(listOf(null, null, null, null), block.entries.map { it.code })
    }

    @Test
    fun `parses spaced dot leaders with bracketed search codes`() {
        val lines = toc(
            "=-=- - T A B L E  O F  C O N T E N T S - -=-=",
            "",
            "   Section I:  Introduction  . . . . . . . . . . . . . . . . . . [IN00]",
            "",
            "  Section II:  Basics  . . . . . . . . . . . . . . . . . . . . . [BS00]",
            "",
            "      - Controls . . . . . . . . . . . . . . . . . . . . . . . . [BS01]",
            "      - Items  . . . . . . . . . . . . . . . . . . . . . . . . . [BS03]",
        )
        val block = detectGuideTocBlock(lines)
        assertNotNull(block)
        assertEquals(listOf("IN00", "BS00", "BS01", "BS03"), block!!.entries.map { it.code })
        assertEquals("Controls", block.entries[2].title)
    }

    @Test
    fun `parses leading-code entries under a bracket-toc banner`() {
        // AbsoluteSteve style: ASCII-art banner ending in [TOC], curly-brace
        // codes BEFORE the titles, body sections repeating the code in a frame.
        val lines = toc(
            "________________________________________[TOC]",
            "",
            " Use Ctrl+F to find what you're looking for, and copy/paste the brackets {}.",
            "",
            "I) Walkthrough: {WAL}",
            "=====================",
            "   {1} The Reactor #1 raid",
            "   {2} Sector 7: At the hideout",
            "   {OPT-2} Wutai",
            "",
            "____________________________________{WAL}",
            "body of walkthrough intro",
            "||      {1} The Reactor #1 raid      ||",
            "body", "",
            "||      {2} Sector 7: At the hideout ||",
            "body", "",
            "||      {OPT-2} Wutai                ||",
            "body",
        )
        val block = detectGuideTocBlock(lines)
        assertNotNull(block)
        assertEquals(listOf("WAL", "1", "2", "OPT-2"), block!!.entries.map { it.code })
        val sections = matchTocEntriesToBody(lines, block.entries, block.endLine)
        assertEquals(listOf(10, 12, 15, 18), sections.map { it.startLine })
        assertEquals("The Reactor #1 raid", sections[1].title)
    }

    @Test
    fun `returns null when no contents block exists`() {
        val lines = toc(
            "My Guide", "",
            "Just some prose about the game with no contents list at all.",
            "More prose.", "SOME HEADING", "text", "ANOTHER HEADING", "text",
        )
        assertNull(detectGuideTocBlock(lines))
    }

    // ── body matching ──

    @Test
    fun `matches entries to body headings monotonically and skips unmatched`() {
        val lines = toc(
            "Contents", "",
            "1. Introduction",
            "2. Secret Chapter",
            "3. Walkthrough",
            "4. Boss List",
            "5. Credits",
            "",
            "=====",
            "1. INTRODUCTION", "text", "",
            "=====",
            "3. WALKTHROUGH", "text", "",
            "=====",
            "4. BOSS LIST", "text", "",
            "=====",
            "5. CREDITS", "bye",
        )
        val block = detectGuideTocBlock(lines)
        assertNotNull(block)
        val sections = matchTocEntriesToBody(lines, block!!.entries, block.endLine)
        assertEquals(
            listOf("1. INTRODUCTION", "3. WALKTHROUGH", "4. BOSS LIST", "5. CREDITS"),
            sections.map { it.title },
        )
        assertEquals(sections.map { it.startLine }, sections.map { it.startLine }.sorted())
    }

    @Test
    fun `matches by search code when body anchor is a bare code line`() {
        val lines = toc(
            "Table of Contents", "",
            "Intro . . . . . . [IN00]",
            "Basics  . . . . . [BS00]",
            "Weapons . . . . . [WP00]",
            "Credits . . . . . [CR00]",
            "",
            "[IN00]", "=====", "ascii art title", "intro text",
            "[BS00]", "=====", "basics text",
            "[WP00]", "=====", "weapons text",
            "[CR00]", "=====", "credits text",
        )
        val block = detectGuideTocBlock(lines)
        assertNotNull(block)
        val sections = matchTocEntriesToBody(lines, block!!.entries, block.endLine)
        assertEquals(listOf("Intro", "Basics", "Weapons", "Credits"), sections.map { it.title })
        assertEquals(listOf(7, 11, 14, 17), sections.map { it.startLine })
    }

    @Test
    fun `matches when body heading extends the toc title`() {
        val lines = toc(
            "Contents", "",
            "1. Sequence 1",
            "2. Sequence 2",
            "3. Sequence 3",
            "4. Sequence 4",
            "",
            "Sequence 1: Peace at Last", "text",
            "Sequence 2: A Wilderness", "text",
            "Sequence 3: The Fighter", "text",
            "Sequence 4: Den of Thieves", "text",
        )
        val block = detectGuideTocBlock(lines)
        assertNotNull(block)
        val sections = matchTocEntriesToBody(lines, block!!.entries, block.endLine)
        assertEquals(4, sections.size)
        assertEquals("Sequence 1: Peace at Last", sections[0].title)
    }

    // ── full pipeline ──

    @Test
    fun `detectTextSections returns only toc-validated chapters`() {
        val lines = toc(
            "Great Game FAQ", "",
            "Table of Contents", "",
            "1. Introduction",
            "2. Walkthrough",
            "3. Items",
            "4. Credits",
            "", "",
            "===============",
            "1. INTRODUCTION",
            "===============",
            "welcome to the guide", "",
            "SOME RANDOM ALL CAPS LINE", "",
            "===============",
            "2. WALKTHROUGH",
            "===============",
            "go left", "",
            "===============",
            "3. ITEMS",
            "===============",
            "a sword", "",
            "===============",
            "4. CREDITS",
            "===============",
            "thanks",
        )
        val sections = detectTextSections(lines)
        assertEquals(
            listOf("1. INTRODUCTION", "2. WALKTHROUGH", "3. ITEMS", "4. CREDITS"),
            sections.map { it.title },
        )
    }

    @Test
    fun `toc entries themselves never become chapters in fallback strategies`() {
        // Contents block exists but body headings are too mangled to match 4+
        // entries — fallback strategies run, but must not detect the TOC lines.
        val lines = toc(
            "Table of Contents", "",
            "1. Alpha Part",
            "2. Beta Part",
            "3. Gamma Part",
            "4. Delta Part",
            "", "",
            "=====================",
            "The First Area (alpha)",
            "=====================",
            "text", "",
            "=====================",
            "The Second Area (beta)",
            "=====================",
            "text", "",
            "=====================",
            "The Third Area (gamma)",
            "=====================",
            "text", "",
            "=====================",
            "The Fourth Area (delta)",
            "=====================",
            "text",
        )
        val sections = detectTextSections(lines)
        assertTrue(sections.isNotEmpty())
        for (s in sections) {
            assertFalse("TOC entry leaked as chapter: ${s.title}", s.title.contains("Part"))
        }
    }

    @Test
    fun `guides without a toc keep legacy structural detection`() {
        val lines = toc(
            "My Guide", "",
            "=====================",
            "First Section",
            "=====================",
            "text", "",
            "=====================",
            "Second Section",
            "=====================",
            "text", "",
            "=====================",
            "Third Section",
            "=====================",
            "text", "",
            "=====================",
            "Fourth Section",
            "=====================",
            "text",
        )
        val sections = detectTextSections(lines)
        assertEquals(
            listOf("First Section", "Second Section", "Third Section", "Fourth Section"),
            sections.map { it.title },
        )
    }

    @Test
    fun `external toc entries are authoritative for a chunk`() {
        val entries = listOf(
            GuideTocEntry("Chapter Seven", "chapterseven", null, 0),
            GuideTocEntry("Chapter Eight", "chaptereight", null, 1),
        )
        val chunk = toc(
            "mid-guide text", "",
            "CHAPTER SEVEN", "more text", "",
            "SOME NOISE HEADING", "",
            "CHAPTER EIGHT", "more text",
        )
        val sections = detectTextSections(chunk, entries)
        // No fallback noise even though matches are below the usual 4-minimum
        assertEquals(listOf(2, 7), sections.map { it.startLine })
    }

    @Test
    fun `survives intro prose and spaced-letter banners inside the contents block`() {
        // vinheim style: navigation prose after the header, "N]" enumerators,
        // spaced-letter era banners between sub-entries, dotted code anchors.
        val lines = toc(
            "                Table of Contents",
            "=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=",
            "",
            " To navigate much easier through this guide, I added the search system,",
            "works just by following these simple steps:",
            "",
            "- Highlight the \"Section Code\" of the section which you wish to go and copy it",
            "  (CTRL+C).",
            "",
            "1] Introduction..........................[vb100]",
            "    1.1] Contact Rules . . . . . . .[vb101]",
            "",
            "                        P R E S E N T   ( 1 0 0 0 A D )",
            "    1.2] Donations . . . . . . . . .[vb102]",
            "2] Game Basics...........................[vb200]",
            "",
            "=====",
            " ___________________[vb100]___________________",
            "intro text", "",
            "        ~-~ ~-~  1.1] Contact Rules  ~-~ ~-~",
            "=-=-=-=-=-=-=-=-=-=-.vb101.-=-=-=-=-=-=-=-=-=",
            "contact text", "",
            "=-=-=-=-=-=-=-=-=-=-.vb102.-=-=-=-=-=-=-=-=-=",
            "donation text", "",
            " ___________________[vb200]___________________",
            "basics text",
        )
        val block = detectGuideTocBlock(lines)
        assertNotNull(block)
        assertEquals(listOf("vb100", "vb101", "vb102", "vb200"), block!!.entries.map { it.code })
        val sections = matchTocEntriesToBody(lines, block.entries, block.endLine)
        assertEquals(listOf(17, 20, 24, 27), sections.map { it.startLine })
    }

    // ── hierarchy depth ──

    @Test
    fun `hierarchical toc keeps only main chapters when mains suffice`() {
        // David Wonn style: roman-numeral mains (right-aligned), lettered subs.
        val lines = toc(
            "Table of contents:", "",
            "   I. About this guide",
            "  II. Weapons & Items",
            "      A. Whip",
            "      B. Dagger",
            "      C. Axe",
            " III. Walkthrough / Tips",
            "  IV. Boss strategies",
            "      A. Vampire Bat",
            "      B. Queen Medusa",
            "   V. Hidden bonus item locations",
            "  VI. Advanced tips",
            " VII. Version history",
            "VIII. Disclaimer",
            "", "",
            "I. About this guide", "text", "",
            "II. Weapons & Items", "text", "",
            "A. Whip", "text", "",
            "B. Dagger", "text", "",
            "C. Axe", "text", "",
            "III. Walkthrough / Tips", "text", "",
            "IV. Boss strategies", "text", "",
            "A. Vampire Bat", "text", "",
            "B. Queen Medusa", "text", "",
            "V. Hidden bonus item locations", "text", "",
            "VI. Advanced tips", "text", "",
            "VII. Version history", "text", "",
            "VIII. Disclaimer", "text",
        )
        val sections = detectTextSections(lines)
        assertEquals(
            listOf(
                "I. About this guide", "II. Weapons & Items", "III. Walkthrough / Tips",
                "IV. Boss strategies", "V. Hidden bonus item locations", "VI. Advanced tips",
                "VII. Version history", "VIII. Disclaimer",
            ),
            sections.map { it.title },
        )
    }

    @Test
    fun `sub chapters stay when mains alone are too few`() {
        // DMC4 style: 3 section groups, the meat lives one level down.
        val body = mutableListOf(
            "Table of Contents", "",
            "  Section I: Intro",
            "    - Mission Alpha",
            "    - Mission Bravo",
            "  Section II: Middle",
            "    - Mission Charlie",
            "    - Mission Delta",
            "  Section III: End",
            "    - Mission Echo",
            "", "",
        )
        for (t in listOf(
            "Section I: Intro", "Mission Alpha", "Mission Bravo", "Section II: Middle",
            "Mission Charlie", "Mission Delta", "Section III: End", "Mission Echo",
        )) { body.add(t.uppercase()); body.add("text"); body.add("") }
        val sections = detectTextSections(body)
        assertEquals(8, sections.size)
    }

    // ── own-enumeration detection (UI numbering suppression) ──

    @Test
    fun `titles with their own enumeration are recognized`() {
        assertTrue(titleHasOwnEnumeration("1. Introduction"))
        assertTrue(titleHasOwnEnumeration("II. Weapons & Items"))
        assertTrue(titleHasOwnEnumeration("A. Story"))
        assertTrue(titleHasOwnEnumeration("3.02. Missions 11-20"))
        assertTrue(titleHasOwnEnumeration("2-1. THE RUNNING GAME"))
        assertFalse(titleHasOwnEnumeration("Introduction"))
        assertFalse(titleHasOwnEnumeration("Mission 01"))
        assertFalse(titleHasOwnEnumeration("The Reactor #1 raid"))
        assertFalse(titleHasOwnEnumeration("Catacomb"))
    }

    @Test
    fun `verified toc entries require body matches`() {
        // TOC parses but nothing in the body matches → not trusted
        val lines = toc(
            "Contents", "",
            "1. Aaaa Bbbb",
            "2. Cccc Dddd",
            "3. Eeee Ffff",
            "4. Gggg Hhhh",
            "", "",
            "completely unrelated body text", "with no headings at all",
        )
        assertNull(detectVerifiedGuideTocEntries(lines))
    }
}
