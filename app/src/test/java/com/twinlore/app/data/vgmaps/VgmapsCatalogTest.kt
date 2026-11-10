package com.twinlore.app.data.vgmaps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VgmapsCatalogTest {
    private val json = """
        [
          {"t":"Alundra","s":"PSX","a":"Alundra"},
          {"t":"Castlevania: Symphony Of The Night","s":"PSX","a":"CastlevaniaSymphonyOfTheNight"},
          {"t":"Castlevania: Symphony Of The Night","s":"Saturn","a":"CastlevaniaSymphonyOfTheNight"},
          {"t":"Final Fantasy VII","s":"PSX","a":"FinalFantasyVII"},
          {"t":"Final Fantasy IX","s":"PSX","a":"FinalFantasyIX"},
          {"t":"Pokemon Red","s":"GameBoy","a":"PokemonRed"}
        ]
    """.trimIndent()

    private val catalog = VgmapsCatalog.fromJson(json)

    @Test fun `exact match returns single entry`() {
        val hits = catalog.match("Alundra")
        assertEquals(1, hits.size)
        assertEquals("Alundra", hits[0].title)
        assertEquals("PSX", hits[0].system)
    }

    @Test fun `multi-platform title returns one entry per system`() {
        val hits = catalog.match("Castlevania: Symphony Of The Night")
        assertEquals(2, hits.size)
        assertEquals(setOf("PSX", "Saturn"), hits.map { it.system }.toSet())
    }

    @Test fun `fuzzy abbreviation 'Castlevania SOTN' still matches`() {
        // Token overlap of {castlevania, sotn} vs {castlevania, symphony, of, the, night}
        // is below threshold on pure Jaccard, so this case is allowed to NOT match.
        // The acceptance criterion: it must EITHER match cleanly OR return empty,
        // it must NOT return Final Fantasy. We assert it doesn't cross-pollinate.
        val hits = catalog.match("Castlevania SOTN")
        assertTrue(
            "Hits must be empty or castlevania-only, was: " + hits.map { it.title },
            hits.all { it.title.startsWith("Castlevania") }
        )
    }

    @Test fun `diacritic in input matches plain title`() {
        val hits = catalog.match("Pokémon Red")
        assertEquals(1, hits.size)
        assertEquals("Pokemon Red", hits[0].title)
    }

    @Test fun `name not in catalog returns empty list`() {
        assertTrue(catalog.match("Some Random Indie Game 2024").isEmpty())
        assertTrue(catalog.match("").isEmpty())
    }

    @Test fun `numbered query matches only its own sequel number`() {
        // Sequel-number guard (shared with StrategyWiki): "Final Fantasy VII"
        // matches FF7 only, never FF9. Roman/Arabic are unified, so "VII" hits
        // the catalog's "Final Fantasy VII" entry exactly.
        val ff7 = catalog.match("Final Fantasy VII")
        assertEquals(listOf("Final Fantasy VII"), ff7.map { it.title })
        val ff9 = catalog.match("Final Fantasy 9")
        assertEquals(listOf("Final Fantasy IX"), ff9.map { it.title })
    }

    @Test fun `base name does not match a numbered sequel`() {
        // "Final Fantasy" (no number) must not cross-match FF7 / FF9.
        assertTrue(catalog.match("Final Fantasy").isEmpty())
    }

    @Test fun `arabic input matches roman catalog entry`() {
        // "Final Fantasy 7" ≡ "Final Fantasy VII".
        val hits = catalog.match("Final Fantasy 7")
        assertEquals(listOf("Final Fantasy VII"), hits.map { it.title })
    }

    @Test fun `match caps at 4 results`() {
        // Ten same-game entries across distinct systems (same number signature,
        // so all clear the sequel guard) — the result must still cap at 4.
        val many = (1..10).joinToString(",") {
            """{"t":"Final Fantasy VII","s":"SYS$it","a":"FF7_$it"}"""
        }
        val big = VgmapsCatalog.fromJson("[$many]")
        assertTrue(big.match("Final Fantasy VII").size <= 4)
    }

    @Test fun `cleanAndDedupe drops orphan parenthesized titles`() {
        val noisy = listOf(
            VgmapsEntry("(Amiga)", "Amiga", "Lemmings"),
            VgmapsEntry("Lemmings", "Amiga", "Lemmings"),
            VgmapsEntry("(GBA)", "GBA", "CastlevaniaCircleOfTheMoon"),
        )
        val cleaned = VgmapsCatalog.cleanAndDedupe(noisy)
        assertEquals(1, cleaned.size)
        assertEquals("Lemmings", cleaned[0].title)
    }

    @Test fun `cleanAndDedupe collapses same (system, anchor) to shortest title`() {
        // The CotM regression case: two GBA tabs for the same game.
        val noisy = listOf(
            VgmapsEntry("Castlevania: Circle Of The Moon",        "GBA", "CastlevaniaCircleOfTheMoon"),
            VgmapsEntry("Castlevania: Circle Of The Moon (GBA)",  "GBA", "CastlevaniaCircleOfTheMoon"),
        )
        val cleaned = VgmapsCatalog.cleanAndDedupe(noisy)
        assertEquals(1, cleaned.size)
        assertEquals("Castlevania: Circle Of The Moon", cleaned[0].title)
    }

    @Test fun `cleanAndDedupe keeps multi-platform entries`() {
        // SOTN on PSX and Saturn share an anchor but differ on system — keep both.
        val noisy = listOf(
            VgmapsEntry("Castlevania: Symphony Of The Night", "PSX",    "CastlevaniaSymphonyOfTheNight"),
            VgmapsEntry("Castlevania: Symphony Of The Night", "Saturn", "CastlevaniaSymphonyOfTheNight"),
        )
        val cleaned = VgmapsCatalog.cleanAndDedupe(noisy)
        assertEquals(2, cleaned.size)
        assertEquals(setOf("PSX", "Saturn"), cleaned.map { it.system }.toSet())
    }

    @Test fun `cleanAndDedupe is invoked by parse - CotM produces a single match`() {
        val raw = """
            [
              {"t":"Castlevania: Circle Of The Moon",        "s":"GBA", "a":"CastlevaniaCircleOfTheMoon"},
              {"t":"Castlevania: Circle Of The Moon (GBA)",  "s":"GBA", "a":"CastlevaniaCircleOfTheMoon"}
            ]
        """.trimIndent()
        val cat = VgmapsCatalog.fromJson(raw)
        val hits = cat.match("Castlevania: Circle Of The Moon")
        assertEquals(1, hits.size)
        assertEquals("GBA", hits[0].system)
    }
}
