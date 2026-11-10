package com.twinlore.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Pins the parser against the historical `content_history.lpl` JSON shape.
 * The two fixtures under `fixtures/retroarch/` follow the shape of real RetroArch
 * history files (the same fixtures the deleted RetroArchHistoryParser used).
 *
 * Robolectric only for [android.net.Uri] in [findLastPlayedGame];
 * the pure-JSON parser is platform-free, but the test runner is shared.
 */
@RunWith(RobolectricTestRunner::class)
class RetroArchHistoryParserTest {

    // -- parseLpl: pure JSON --------------------------------------------------

    @Test
    fun `parseLpl returns null for null items field`() {
        val json = """{"version":"1.5"}"""
        assertNull(RetroArchHistoryParser.parseLpl(json))
    }

    @Test
    fun `parseLpl returns null for empty items array`() {
        val json = """{"version":"1.5","items":[]}"""
        assertNull(RetroArchHistoryParser.parseLpl(json))
    }

    @Test
    fun `parseLpl returns null for malformed JSON`() {
        assertNull(RetroArchHistoryParser.parseLpl("{not-json"))
        assertNull(RetroArchHistoryParser.parseLpl(""))
        assertNull(RetroArchHistoryParser.parseLpl("not even close"))
    }

    @Test
    fun `parseLpl reads first item from real example_content_history fixture`() {
        val text = loadFixture("example_content_history.lpl")
        val entry = RetroArchHistoryParser.parseLpl(text)
        assertNotNull("first item must parse", entry)
        // Fixture's first item has blank `label`, so gameName falls back to
        // the filename-without-extension (stripped of archive-tail and .ext).
        assertEquals(
            "Legend of Zelda, The - A Link to the Past & Four Swords (USA)",
            entry!!.gameName,
        )
        assertTrue("romPath ends with the zip", entry.romPath!!.endsWith(".zip"))
        assertNotNull("romFilename must be present", entry.romFilename)
        assertEquals("Nintendo - Game Boy Advance (mGBA)", entry.coreName)
        assertEquals("com.retroarch.ra32", entry.packageName)
        assertNotNull("launchIntent must resolve (packageName + corePath both present)", entry.launchIntent)
    }

    @Test
    fun `parseLpl second fixture also resolves`() {
        val text = loadFixture("example_content_history_2.lpl")
        val entry = RetroArchHistoryParser.parseLpl(text)
        assertNotNull("second fixture must parse", entry)
        // Second fixture's first item is the same Zelda title — same fallback gameName.
        assertEquals(
            "Legend of Zelda, The - A Link to the Past & Four Swords (USA)",
            entry!!.gameName,
        )
        assertEquals("Nintendo - Game Boy Advance (mGBA)", entry.coreName)
    }

    @Test
    fun `parseLpl falls back to filename when label is blank`() {
        val json = """
            {"version":"1.5","items":[
              {"path":"/storage/Game.gba","label":"","core_path":"","core_name":""}
            ]}
        """.trimIndent()
        val entry = RetroArchHistoryParser.parseLpl(json)
        assertNotNull(entry)
        assertEquals("filename without extension", "Game", entry!!.gameName)
    }

    @Test
    fun `parseLpl extracts romFilename even when path contains archive marker`() {
        // RetroArch represents archive entries as "/path/Archive.zip#Inner.gba".
        val json = """
            {"version":"1.5","items":[
              {"path":"/storage/Roms/Archive.zip#Inner.gba","label":"Inner Game"}
            ]}
        """.trimIndent()
        val entry = RetroArchHistoryParser.parseLpl(json)
        assertNotNull(entry)
        // romFilename is "everything after the last /" — INCLUDES the # tail,
        // because RetroArch's launch intent expects the archive-relative form.
        assertEquals("Archive.zip#Inner.gba", entry!!.romFilename)
        assertEquals("Inner Game", entry.gameName)
    }

    @Test
    fun `parseLpl tolerates missing optional fields`() {
        // Minimal entry: just `path` and `label`. core_path / core_name absent.
        val json = """
            {"version":"1.5","items":[
              {"path":"/storage/Game.gba","label":"Game"}
            ]}
        """.trimIndent()
        val entry = RetroArchHistoryParser.parseLpl(json)
        assertNotNull(entry)
        assertEquals("Game", entry!!.gameName)
        assertNull("no core_path → packageName null", entry.packageName)
        assertNull("no core_path → launchIntent null", entry.launchIntent)
    }

    // -- helpers --------------------------------------------------------------

    private fun loadFixture(name: String): String {
        val stream = javaClass.classLoader!!
            .getResourceAsStream("fixtures/retroarch/$name")
            ?: error("Missing fixture: fixtures/retroarch/$name")
        return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }
}
