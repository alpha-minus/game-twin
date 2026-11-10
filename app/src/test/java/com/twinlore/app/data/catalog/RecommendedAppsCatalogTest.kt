package com.twinlore.app.data.catalog

import com.twinlore.app.domain.Category
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock

/**
 * Tests for the upstream JSON parser. The parser is pure — doesn't touch
 * Context or OkHttpClient — so the test passes Mockito mocks for those
 * constructor params.
 */
class RecommendedAppsCatalogTest {

    private val parser = RecommendedAppsCatalog(
        mock<android.content.Context>(),
        mock<okhttp3.OkHttpClient>()
    )

    @Test
    fun `parses Obtainium pack entries into RecommendedApp`() {
        val json = """
            {
              "apps": [
                {
                  "id": "aenu.aps3e",
                  "url": "https://github.com/aenu1/aps3e",
                  "author": "aenu1",
                  "name": "aPS3e",
                  "categories": ["Emulator"],
                  "additionalSettings": "{\"about\":\"PS3 emulator for Android\"}"
                },
                {
                  "id": "com.winlator",
                  "url": "https://github.com/brunodev85/winlator",
                  "author": "brunodev85",
                  "name": "Winlator",
                  "categories": ["PC Emulation"],
                  "additionalSettings": "{\"about\":\"Windows app and game emulator\"}"
                }
              ]
            }
        """.trimIndent()
        val result = parser.parse(json)
        assertEquals(2, result.size)
        val ps3 = result[0]
        assertEquals("aenu.aps3e", ps3.id)
        assertEquals("aPS3e", ps3.name)
        assertEquals("aenu1", ps3.author)
        assertEquals(Category.Emulator, ps3.category)
        assertEquals("https://github.com/aenu1/aps3e", ps3.sourceUrl)
        assertEquals("PS3 emulator for Android", ps3.about)
        assertTrue("entryJson should hold the raw entry", ps3.entryJson.contains("aenu.aps3e"))
        // PC Emulation folds into Emulator
        assertEquals(Category.Emulator, result[1].category)
    }

    @Test
    fun `skips entries with blank id`() {
        val json = """{ "apps": [ { "id": "", "name": "x" }, { "id": "ok", "name": "ok" } ] }"""
        val result = parser.parse(json)
        assertEquals(1, result.size)
        assertEquals("ok", result[0].id)
    }

    @Test
    fun `defaults to Utility category when categories array is missing`() {
        val json = """{ "apps": [ { "id": "x", "name": "x" } ] }"""
        val result = parser.parse(json)
        assertEquals(Category.Utility, result[0].category)
    }

    @Test
    fun `tolerates malformed additionalSettings`() {
        val json = """{ "apps": [ { "id": "x", "name": "x", "additionalSettings": "not-json" } ] }"""
        val result = parser.parse(json)
        assertEquals(1, result.size)
        assertEquals("", result[0].about)
    }

    @Test
    fun `empty apps array yields empty list`() {
        assertEquals(emptyList<Any>(), parser.parse("""{ "apps": [] }"""))
    }
}
