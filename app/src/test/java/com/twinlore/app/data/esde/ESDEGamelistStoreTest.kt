package com.twinlore.app.data.esde

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ESDEGamelistStoreTest {

    private val xml = """
        <?xml version="1.0"?>
        <gameList>
            <game>
                <path>./Sonic &amp; Knuckles.md</path>
                <name>Sonic &amp; Knuckles</name>
                <favorite>true</favorite>
                <lastplayed>20260729T210102</lastplayed>
                <playcount>7</playcount>
            </game>
            <game>
                <path>./Streets of Rage 2.md</path>
                <name>Streets of Rage 2</name>
            </game>
            <game>
                <desc>No path — must be skipped</desc>
            </game>
        </gameList>
    """.trimIndent()

    @Test
    fun `parses entries with all fields`() {
        val entries = ESDEGamelistStore.parse(xml, "genesis")
        assertEquals(2, entries.size)
        val sonic = entries[0]
        assertEquals("./Sonic & Knuckles.md", sonic.romPath)
        assertEquals("Sonic & Knuckles", sonic.name)
        assertTrue(sonic.favorite)
        assertEquals("20260729T210102", sonic.lastPlayed)
        assertEquals(7, sonic.playCount)
        assertEquals("genesis", sonic.system)
    }

    @Test
    fun `parses description and gametime when present`() {
        val doc = """
            <gameList><game>
                <path>./X.zip</path>
                <desc>A &amp; B adventure.</desc>
                <gametime>11520</gametime>
            </game></gameList>
        """.trimIndent()
        val e = ESDEGamelistStore.parse(doc, "snes").single()
        assertEquals("A & B adventure.", e.description)
        assertEquals(11520L, e.playTimeSeconds)
    }

    @Test
    fun `missing optional fields default sanely`() {
        val sor2 = ESDEGamelistStore.parse(xml, "genesis")[1]
        assertEquals(false, sor2.favorite)
        assertNull(sor2.lastPlayed)
        assertEquals(0, sor2.playCount)
    }

    @Test
    fun `lastplayed timestamps sort lexicographically as chronologically`() {
        val older = "20250101T120000"
        val newer = "20260730T090000"
        assertTrue(newer > older)
    }

    @Test
    fun `auto collection display names are capitalized`() {
        assertEquals("Favorites", esdeSystemDisplayName("favorites", "favorites"))
        assertEquals("Last Played", esdeSystemDisplayName("recent", "last played"))
        assertEquals("All Games", esdeSystemDisplayName("all", "all games"))
        assertEquals("Sega Genesis", esdeSystemDisplayName("genesis", "Sega Genesis"))
    }
}
