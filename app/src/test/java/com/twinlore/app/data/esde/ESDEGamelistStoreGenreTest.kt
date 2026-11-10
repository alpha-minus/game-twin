package com.twinlore.app.data.esde

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ESDEGamelistStoreGenreTest {

    @Test
    fun `parse reads genre with entity decoding`() {
        val xml = """
            <gameList>
              <game>
                <path>./Sonic.md</path>
                <name>Sonic</name>
                <genre>Action &amp; Platformer</genre>
              </game>
            </gameList>
        """.trimIndent()
        val entries = ESDEGamelistStore.parse(xml, "megadrive")
        assertEquals("Action & Platformer", entries.single().genre)
    }

    @Test
    fun `parse reads a plausible release year and rejects junk`() {
        val xml = """
            <gameList>
              <game><path>./A.md</path><releasedate>19940318T000000</releasedate></game>
              <game><path>./B.md</path><releasedate>10000101T000000</releasedate></game>
              <game><path>./C.md</path></game>
            </gameList>
        """.trimIndent()
        val entries = ESDEGamelistStore.parse(xml, "megadrive")
        assertEquals(1994, entries[0].releaseYear)
        assertNull(entries[1].releaseYear)
        assertNull(entries[2].releaseYear)
    }

    @Test
    fun `parse leaves genre null when tag is missing or empty`() {
        val xml = """
            <gameList>
              <game><path>./A.md</path><genre></genre></game>
              <game><path>./B.md</path></game>
            </gameList>
        """.trimIndent()
        val entries = ESDEGamelistStore.parse(xml, "megadrive")
        assertNull(entries[0].genre)
        assertNull(entries[1].genre)
    }
}
