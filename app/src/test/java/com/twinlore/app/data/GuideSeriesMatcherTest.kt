package com.twinlore.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GuideSeriesMatcherTest {

    @Test
    fun `unrelated name returns null`() {
        assertNull(matchSeries("Final Fantasy X"))
        assertNull(matchSeries(""))
        assertNull(matchSeries("   "))
    }

    @Test
    fun `zelda direct match — Phantom Hourglass`() {
        val r = matchSeries("Phantom Hourglass") as SeriesMatchResult.Direct
        assertEquals(GuideSeries.Zelda, r.series)
        assertEquals("Phantom Hourglass", r.entry.title)
        assertEquals("https://www.zeldadungeon.net/phantom-hourglass-walkthrough/", r.entry.url)
    }

    @Test
    fun `zelda direct match — full title containing the entry`() {
        val r = matchSeries("The Legend of Zelda: Phantom Hourglass") as SeriesMatchResult.Direct
        assertEquals(GuideSeries.Zelda, r.series)
        assertEquals("Phantom Hourglass", r.entry.title)
    }

    @Test
    fun `zelda keyword without title — falls back to hub`() {
        val r = matchSeries("Zelda Romhack 9001") as SeriesMatchResult.Hub
        assertEquals(GuideSeries.Zelda, r.series)
        assertEquals("https://www.zeldadungeon.net/", r.series.hubUrl)
    }

    @Test
    fun `zelda position tiebreak — later but shorter wins over earlier but longer`() {
        // "Twilight Princess" (len 17) at position 0 vs "Skyward Sword" (len 13) at position 26.
        // Spec: later position wins over earlier longer match.
        val r = matchSeries("Twilight Princess HD plus Skyward Sword") as SeriesMatchResult.Direct
        assertEquals(GuideSeries.Zelda, r.series)
        assertEquals("Skyward Sword", r.entry.title)
    }

    @Test
    fun `super in name without metroid does not match Super Metroid`() {
        // "Super Metroid" reduces to just "super" once the keyword is dropped;
        // a game that shares only "super" but not "metroid" must NOT be offered
        // the Omega Metroid row.
        assertNull(matchSeries("Super Mario Galaxy"))
        assertNull(matchSeries("Super Mario Odyssey"))
    }

    @Test
    fun `metroid direct match — Super Metroid (keyword present, single token ok)`() {
        val r = matchSeries("Super Metroid") as SeriesMatchResult.Direct
        assertEquals(GuideSeries.Metroid, r.series)
        assertEquals("Super Metroid", r.entry.title)
    }

    @Test
    fun `metroid direct match — Metroid Dread`() {
        val r = matchSeries("Metroid Dread") as SeriesMatchResult.Direct
        assertEquals(GuideSeries.Metroid, r.series)
        assertEquals("Metroid Dread", r.entry.title)
        assertEquals("https://omegametroid.com/metroid-dread-walkthrough/", r.entry.url)
    }

    @Test
    fun `metroid keyword without title — falls back to hub`() {
        val r = matchSeries("fake metroid game") as SeriesMatchResult.Hub
        assertEquals(GuideSeries.Metroid, r.series)
        assertEquals("https://omegametroid.com/", r.series.hubUrl)
    }

    @Test
    fun `pokemon direct match — diacritic and ampersand normalized`() {
        val r = matchSeries("Pokémon Scarlet & Violet") as SeriesMatchResult.Direct
        assertEquals(GuideSeries.Pokemon, r.series)
        assertTrue(
            "expected Scarlet and Violet entry, got ${r.entry.title}",
            r.entry.title.contains("Scarlet") && r.entry.title.contains("Violet")
        )
    }

    @Test
    fun `pokemon direct match — no diacritic, no 'and'`() {
        val r = matchSeries("pokemon scarlet violet") as SeriesMatchResult.Direct
        assertEquals(GuideSeries.Pokemon, r.series)
        assertTrue(r.entry.title.contains("Scarlet"))
    }

    @Test
    fun `pokemon longest match wins — Black and White over Black`() {
        val r = matchSeries("Pokemon Black and White") as SeriesMatchResult.Direct
        assertEquals(GuideSeries.Pokemon, r.series)
        assertTrue(
            "expected Black and White entry, got ${r.entry.title}",
            r.entry.title.contains("Black and White") || r.entry.title.contains("Black & White")
        )
    }

    @Test
    fun `pokemon keyword without title — falls back to hub`() {
        val r = matchSeries("pokemon fan project alpha") as SeriesMatchResult.Hub
        assertEquals(GuideSeries.Pokemon, r.series)
        assertEquals("https://bulbapedia.bulbagarden.net/wiki/Browse:Walkthroughs", r.series.hubUrl)
    }

    @Test
    fun `pokemon keyword matched after diacritic strip — falls back to hub`() {
        // Diacritic on the keyword itself; no curated title in the name.
        val r = matchSeries("Pokémon Romhack") as SeriesMatchResult.Hub
        assertEquals(GuideSeries.Pokemon, r.series)
    }

    @Test
    fun `display names are stable`() {
        assertEquals("Omega Metroid", GuideSeries.Metroid.displayName)
        assertEquals("Zelda Dungeon", GuideSeries.Zelda.displayName)
        assertEquals("Bulbapedia", GuideSeries.Pokemon.displayName)
    }

    @Test
    fun `Metroid Direct match exposes the curated mapUrl`() {
        val r = matchSeries("Metroid Dread") as SeriesMatchResult.Direct
        assertEquals(
            "https://omegametroid.com/metroid-dread-walkthrough/map/",
            r.entry.mapUrl,
        )
    }

    @Test
    fun `every Metroid title has a curated mapUrl`() {
        val titles = listOf(
            "Metroid Dread", "Metroid Fusion", "Metroid II Return of Samus",
            "Metroid Prime", "Metroid Prime 2 Echoes", "Metroid Prime 3 Corruption",
            "Metroid Prime 4 Beyond", "Metroid Prime Hunters", "Metroid Samus Returns",
            "Metroid Zero Mission", "Super Metroid",
        )
        titles.forEach { title ->
            val r = matchSeries(title) as SeriesMatchResult.Direct
            assertNotNull(
                "$title is missing a mapUrl — Maps tab Direct row would fall back to Google search",
                r.entry.mapUrl,
            )
            assertTrue(
                "$title mapUrl should be on omegametroid.com, was ${r.entry.mapUrl}",
                r.entry.mapUrl!!.startsWith("https://omegametroid.com/"),
            )
        }
    }

    @Test
    fun `Zelda and Pokemon Direct matches do not have a curated mapUrl`() {
        // mapUrl is currently populated only for Metroid; the Maps tab series row is
        // Metroid-only, so Zelda/Pokemon entries don't need it. If that changes, populate
        // them and adjust this test.
        val zelda = matchSeries("Phantom Hourglass") as SeriesMatchResult.Direct
        assertNull(zelda.entry.mapUrl)
        val pokemon = matchSeries("Pokemon Scarlet and Violet") as SeriesMatchResult.Direct
        assertNull(pokemon.entry.mapUrl)
    }

    // The token-overlap matcher (rewritten 2026-05) adds matches for vault
    // names whose game is one half of a paired Bulbapedia entry.

    @Test
    fun `pokemon paired match — HeartGold maps to HeartGold and SoulSilver`() {
        val r = matchSeries("Pokemon HeartGold") as SeriesMatchResult.Direct
        assertEquals(GuideSeries.Pokemon, r.series)
        assertEquals("Pokemon HeartGold and SoulSilver", r.entry.title)
    }

    @Test
    fun `pokemon paired match — SoulSilver Version maps to HeartGold and SoulSilver`() {
        val r = matchSeries("Pokemon SoulSilver Version") as SeriesMatchResult.Direct
        assertEquals("Pokemon HeartGold and SoulSilver", r.entry.title)
    }

    @Test
    fun `pokemon paired match — Yellow Version Special Pikachu Edition maps to Yellow`() {
        val r = matchSeries("Pokemon Yellow Version Special Pikachu Edition") as SeriesMatchResult.Direct
        assertEquals(
            "expected the Yellow entry to win the tie against the 'Let's Go Pikachu' entry on shorter title",
            "Pokemon Yellow",
            r.entry.title,
        )
    }

    @Test
    fun `pokemon paired match — Diamond picks the original DP over BDSP`() {
        val r = matchSeries("Pokemon Diamond") as SeriesMatchResult.Direct
        assertEquals("Pokemon Diamond and Pearl", r.entry.title)
    }

    @Test
    fun `pokemon paired match — Brilliant Diamond picks BDSP`() {
        val r = matchSeries("Pokemon Brilliant Diamond") as SeriesMatchResult.Direct
        assertEquals("Pokemon Brilliant Diamond and Shining Pearl", r.entry.title)
    }

    @Test
    fun `pokemon paired match — Ultra Sun picks USUM over SuMo`() {
        val r = matchSeries("Pokemon Ultra Sun") as SeriesMatchResult.Direct
        assertEquals("Pokemon Ultra Sun and Ultra Moon", r.entry.title)
    }

    @Test
    fun `pokemon paired match — bare Sun picks shorter SuMo`() {
        val r = matchSeries("Pokemon Sun") as SeriesMatchResult.Direct
        assertEquals("Pokemon Sun and Moon", r.entry.title)
    }

    @Test
    fun `pokemon paired match — every half of every paired entry resolves correctly`() {
        // Sweep: for each paired Bulbapedia entry, both halves of the pair
        // should land on that entry. Covers the full catalog so regressions
        // surface immediately when entries are added or renamed.
        val cases = listOf(
            "Pokemon Red" to "Pokemon Red and Blue",
            "Pokemon Blue" to "Pokemon Red and Blue",
            "Pokemon Gold" to "Pokemon Gold and Silver",
            "Pokemon Silver" to "Pokemon Gold and Silver",
            "Pokemon Ruby" to "Pokemon Ruby and Sapphire",
            "Pokemon Sapphire" to "Pokemon Ruby and Sapphire",
            "Pokemon FireRed" to "Pokemon FireRed and LeafGreen",
            "Pokemon LeafGreen" to "Pokemon FireRed and LeafGreen",
            "Pokemon Diamond" to "Pokemon Diamond and Pearl",
            "Pokemon Pearl" to "Pokemon Diamond and Pearl",
            "Pokemon HeartGold" to "Pokemon HeartGold and SoulSilver",
            "Pokemon SoulSilver" to "Pokemon HeartGold and SoulSilver",
            "Pokemon Black" to "Pokemon Black and White",
            "Pokemon White" to "Pokemon Black and White",
            "Pokemon Black 2" to "Pokemon Black 2 and White 2",
            "Pokemon White 2" to "Pokemon Black 2 and White 2",
            "Pokemon X" to "Pokemon X and Y",
            "Pokemon Y" to "Pokemon X and Y",
            "Pokemon Omega Ruby" to "Pokemon Omega Ruby and Alpha Sapphire",
            "Pokemon Alpha Sapphire" to "Pokemon Omega Ruby and Alpha Sapphire",
            "Pokemon Sword" to "Pokemon Sword and Shield",
            "Pokemon Shield" to "Pokemon Sword and Shield",
            "Pokemon Brilliant Diamond" to "Pokemon Brilliant Diamond and Shining Pearl",
            "Pokemon Shining Pearl" to "Pokemon Brilliant Diamond and Shining Pearl",
            "Pokemon Scarlet" to "Pokemon Scarlet and Violet",
            "Pokemon Violet" to "Pokemon Scarlet and Violet",
            "Pokemon Ultra Moon" to "Pokemon Ultra Sun and Ultra Moon",
            "Pokemon Moon" to "Pokemon Sun and Moon",
            // Singletons: should match themselves.
            "Pokemon Yellow" to "Pokemon Yellow",
            "Pokemon Crystal" to "Pokemon Crystal",
            "Pokemon Emerald" to "Pokemon Emerald",
            "Pokemon Platinum" to "Pokemon Platinum",
            "Pokemon Legends Arceus" to "Pokemon Legends Arceus",
        )
        for ((vault, expectedTitle) in cases) {
            val r = matchSeries(vault)
            assertTrue(
                "vault '$vault' should resolve to Direct, got $r",
                r is SeriesMatchResult.Direct,
            )
            val direct = r as SeriesMatchResult.Direct
            assertEquals(
                "vault '$vault' should resolve to entry '$expectedTitle', got '${direct.entry.title}'",
                expectedTitle,
                direct.entry.title,
            )
        }
    }

    @Test
    fun `pokemon paired match — Ruby does NOT match Omega Ruby and Alpha Sapphire`() {
        // Defensive: even with shared 'ruby' token, the bare-Ruby vault should
        // land on the GBA original (Ruby and Sapphire) and never on the 3DS
        // remake (Omega Ruby and Alpha Sapphire) which has lower F1 coverage.
        val r = matchSeries("Pokemon Ruby") as SeriesMatchResult.Direct
        assertEquals("Pokemon Ruby and Sapphire", r.entry.title)
    }

    @Test
    fun `pokemon paired match — Diamond does NOT match Brilliant Diamond and Shining Pearl`() {
        // Same: bare Diamond → DP, not BDSP.
        val r = matchSeries("Pokemon Diamond") as SeriesMatchResult.Direct
        assertEquals("Pokemon Diamond and Pearl", r.entry.title)
    }

    // --- Fire Emblem (fireemblemwod.com) ---------------------------------

    @Test
    fun `fire emblem direct match — Blazing Blade routes to fe7 hub`() {
        val r = matchSeries("Fire Emblem: The Blazing Blade") as SeriesMatchResult.Direct
        assertEquals(GuideSeries.FireEmblem, r.series)
        assertEquals("Fire Emblem: The Blazing Blade", r.entry.title)
        assertEquals("https://www.fireemblemwod.com/ENG_fe7.htm", r.entry.url)
    }

    @Test
    fun `fire emblem disambiguates FE1 from FE11 Shadow Dragon`() {
        // FE1 (NES) carries the full "…and the Blade of Light" subtitle; FE11
        // (DS) is the bare "Shadow Dragon" remake. Each must route to its own
        // hub — the shared shadow/dragon tokens must not cross them.
        val fe1 = matchSeries("Fire Emblem: Shadow Dragon and the Blade of Light") as SeriesMatchResult.Direct
        assertEquals("https://www.fireemblemwod.com/ENG_fe1.htm", fe1.entry.url)
        val fe11 = matchSeries("Fire Emblem: Shadow Dragon") as SeriesMatchResult.Direct
        assertEquals("https://www.fireemblemwod.com/ENG_fe11.htm", fe11.entry.url)
    }

    @Test
    fun `fire emblem routes the post-FE11 games to their own guides`() {
        // These four were absent from the catalog, so every one of them fell through to the
        // Hub index even though fireemblemwod.com carries a full walkthrough for each.
        // Awakening is the one that was reported.
        fun urlFor(name: String) = (matchSeries(name) as SeriesMatchResult.Direct).entry.url
        assertEquals("https://www.fireemblemwod.com/ENG_fe12.htm", urlFor("Fire Emblem: New Mystery of the Emblem"))
        assertEquals("https://www.fireemblemwod.com/ENG_fe13.htm", urlFor("Fire Emblem Awakening"))
        assertEquals("https://www.fireemblemwod.com/ENG_fe14.htm", urlFor("Fire Emblem Fates"))
        assertEquals("https://www.fireemblemwod.com/ENG_fe15.htm", urlFor("Fire Emblem Echoes: Shadows of Valentia"))
    }

    @Test
    fun `fire emblem matches western names for japanese-titled entries`() {
        // The bundled game list names FE4 "Genealogy of the Holy War" and FE6 "Binding Blade",
        // which share no distinctive token with "Seisen no Keifu" / "Fuuin no Tsurugi".
        fun urlFor(name: String) = (matchSeries(name) as SeriesMatchResult.Direct).entry.url
        assertEquals("https://www.fireemblemwod.com/ENG_fe4.htm", urlFor("Fire Emblem - Genealogy of the Holy War"))
        assertEquals("https://www.fireemblemwod.com/ENG_fe4.htm", urlFor("Fire Emblem: Seisen No Keifu"))
        assertEquals("https://www.fireemblemwod.com/ENG_fe6.htm", urlFor("Fire Emblem: Binding Blade"))
        assertEquals("https://www.fireemblemwod.com/ENG_fe6.htm", urlFor("Fire Emblem: Fuuin no Tsurugi"))
        assertEquals("https://www.fireemblemwod.com/ENG_bsfe.htm", urlFor("BS Fire Emblem: Akaneia Senkihen"))
    }

    @Test
    fun `fire emblem games the site does not cover fall back to the hub`() {
        // Three Houses, Three Hopes and Engage have no guide on fireemblemwod.com. The index is
        // the honest answer; a near-miss Direct would drop the player into the wrong game's
        // chapters. Three Hopes in particular must NOT be captured by a bare "Warriors" entry.
        listOf(
            "Fire Emblem: Three Houses",
            "Fire Emblem Warriors: Three Hopes",
            "Fire Emblem Engage",
        ).forEach { name ->
            val r = matchSeries(name)
            assertTrue("$name should fall back to the Hub, got $r", r is SeriesMatchResult.Hub)
        }
    }

    @Test
    fun `fire emblem tolerates RA spelling drift — Monsho no Nazo`() {
        // RetroAchievements spells it "Monsho No Nazo"; the entry is
        // "Monshou no Nazo". The F1 floor still admits the {no, nazo} overlap
        // and rejects the {no}-only collisions with Seisen / Fuuin.
        val r = matchSeries("Fire Emblem: Monsho No Nazo") as SeriesMatchResult.Direct
        assertEquals("https://www.fireemblemwod.com/ENG_fe3.htm", r.entry.url)
    }

    @Test
    fun `fire emblem numeric title — Thracia 776`() {
        val r = matchSeries("Fire Emblem: Thracia 776") as SeriesMatchResult.Direct
        assertEquals("https://www.fireemblemwod.com/ENG_fe5.htm", r.entry.url)
    }

    @Test
    fun `fire emblem keyword without a catalogued title — falls back to hub`() {
        // A romhack has no guide anywhere, so it should offer the English index rather than a
        // wrong game's walkthrough.
        //
        // This used to assert the same for BS Fire Emblem: Akaneia Senkihen, on the premise
        // that the Satellaview title had no per-game guide. It does -- ENG_bsfe.htm, four
        // chapters -- so that premise was wrong rather than the behaviour; BS FE is now
        // catalogued and asserted as a Direct match above.
        val hack = matchSeries("~Hack~ Fire Emblem: Vision Quest") as SeriesMatchResult.Hub
        assertEquals(GuideSeries.FireEmblem, hack.series)
        assertEquals("https://www.fireemblemwod.com/english.htm", hack.series.hubUrl)
    }

    @Test
    fun `fire emblem does not steal non-fire-emblem titles`() {
        // "blade" / "dragon" appear in FE entries but must not pull in unrelated
        // games that lack the "fire emblem" keyword.
        assertNull(matchSeries("Blade Strangers"))
        assertNull(matchSeries("Dragon Quest III"))
    }
}
