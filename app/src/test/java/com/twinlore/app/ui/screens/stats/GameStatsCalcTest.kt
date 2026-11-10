package com.twinlore.app.ui.screens.stats

import com.twinlore.app.data.esde.GamelistEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameStatsCalcTest {

    private fun entry(
        system: String = "snes",
        rom: String = "./Game.sfc",
        name: String? = "Game",
        playCount: Int = 0,
        playTime: Long? = null,
        lastPlayed: String? = null,
        genre: String? = null,
        releaseYear: Int? = null,
    ) = GamelistEntry(
        system = system, romPath = rom, name = name, favorite = false,
        lastPlayed = lastPlayed, playCount = playCount,
        playTimeSeconds = playTime, genre = genre, releaseYear = releaseYear,
    )

    // ---- top games -------------------------------------------------------

    @Test
    fun `top games are ranked by playtime then playcount and capped`() {
        val entries = (1..12).map {
            entry(rom = "./g$it.sfc", name = "G$it", playTime = it * 100L)
        }
        val top = GameStatsCalc.topGames(entries)
        assertEquals(10, top.size)
        assertEquals("G12", top.first().name)
        assertTrue(top.zipWithNext().all { (a, b) -> a.playTimeSeconds >= b.playTimeSeconds })
    }

    @Test
    fun `games with no play signal are excluded`() {
        val entries = listOf(entry(rom = "./unplayed.sfc", playTime = null, playCount = 0))
        assertTrue(GameStatsCalc.topGames(entries).isEmpty())
    }

    @Test
    fun `count-only games rank below timed ones but still appear`() {
        val entries = listOf(
            entry(rom = "./timed.sfc", name = "Timed", playTime = 500, playCount = 1),
            entry(rom = "./counted.sfc", name = "Counted", playTime = null, playCount = 9),
        )
        val top = GameStatsCalc.topGames(entries)
        assertEquals(listOf("Timed", "Counted"), top.map { it.name })
    }

    // ---- totals ----------------------------------------------------------

    @Test
    fun `totals sum playtime and derive the average from launches`() {
        val entries = listOf(
            entry(rom = "./a.sfc", playTime = 1000, playCount = 2),
            entry(rom = "./b.sfc", playTime = 2000, playCount = 3),
        )
        val t = GameStatsCalc.totals(entries)
        assertEquals(3000, t.totalPlaySeconds)
        assertEquals(5, t.timesPlayed)
        assertEquals(2, t.gamesPlayed)
        assertEquals(600, t.avgSessionSeconds) // 3000 / 5 launches
    }

    // ---- buckets ---------------------------------------------------------

    @Test
    fun `system buckets rank systems by playtime`() {
        val entries = listOf(
            entry(system = "snes", rom = "./a.sfc", playTime = 3000),
            entry(system = "psx", rom = "./b.chd", playTime = 1000),
        )
        val buckets = GameStatsCalc.systemBuckets(entries)
        assertEquals("snes", buckets.first().label)
        assertEquals(3000, buckets.first().seconds)
    }

    // ---- shared derivation -----------------------------------------------

    /**
     * The stats screen derives [GameStatsCalc.playedGames] once and feeds it to the three
     * `*Of` overloads instead of walking the whole library three times. They must agree with
     * the entry points they replaced.
     */
    @Test
    fun `derived-list overloads match the whole-library entry points`() {
        val entries = listOf(
            entry(system = "snes", rom = "./a.sfc", name = "A", playTime = 3000, playCount = 2),
            entry(system = "psx", rom = "./b.chd", name = "B", playTime = 1000, playCount = 4),
            entry(system = "snes", rom = "./c.sfc", name = "C", playTime = null, playCount = 3),
            entry(system = "psx", rom = "./unplayed.chd", name = "U"),
        )
        val played = GameStatsCalc.playedGames(entries)

        assertEquals(3, played.size)
        assertEquals(GameStatsCalc.topGames(entries), GameStatsCalc.rank(played))
        assertEquals(GameStatsCalc.totals(entries), GameStatsCalc.totalsOf(played))
        assertEquals(GameStatsCalc.systemBuckets(entries), GameStatsCalc.systemBucketsOf(played))
    }

    /** The genre-card backdrop now reads the ranked game's own genre, so it must be carried. */
    @Test
    fun `ranked games carry the gamelist genre and release year`() {
        val entries = listOf(
            entry(rom = "./a.sfc", genre = "Action / Platformer", releaseYear = 1994, playTime = 900),
        )
        val top = GameStatsCalc.topGames(entries).single()
        assertEquals("Action / Platformer", top.genre)
        assertEquals(1994, top.releaseYear)
    }

    // ---- genres ----------------------------------------------------------

    @Test
    fun `genre shares split combined genres and weight by playtime`() {
        val entries = listOf(
            entry(rom = "./a.sfc", genre = "Action / Platformer", playTime = 4000),
            entry(rom = "./b.sfc", genre = "Action", playTime = 6000),
        )
        val shares = GameStatsCalc.genreShares(entries)
        assertEquals("Action", shares.first().genre)
        // Action: 2000 (half of a) + 6000 (b) = 8000; Platformer: 2000
        assertEquals(8000, shares.first().weightSeconds)
        assertEquals(2000, shares[1].weightSeconds)
    }

    @Test
    fun `genre weighting falls back to playcount when no playtime exists`() {
        val entries = listOf(
            entry(rom = "./a.sfc", genre = "RPG", playCount = 2),
            entry(rom = "./b.sfc", genre = "Puzzle", playCount = 1),
        )
        val shares = GameStatsCalc.genreShares(entries)
        assertEquals("RPG", shares.first().genre)
        assertTrue(shares.first().weightSeconds > shares[1].weightSeconds)
    }

    @Test
    fun `genres beyond the cap fold into Other`() {
        val entries = ('a'..'h').mapIndexed { i, c ->
            entry(rom = "./$c.sfc", genre = "Genre$c", playTime = (i + 1) * 100L)
        }
        val shares = GameStatsCalc.genreShares(entries, maxGenres = 5)
        assertEquals(6, shares.size)
        assertEquals("Other", shares.last().genre)
    }

    // ---- era -------------------------------------------------------------

    @Test
    fun `golden year is the play-time-weighted median release year`() {
        val entries = listOf(
            entry(rom = "./a.sfc", releaseYear = 1994, playTime = 8000),
            entry(rom = "./b.sfc", releaseYear = 1994, playTime = 2000),
            entry(rom = "./c.sfc", releaseYear = 2017, playTime = 9000),
        )
        val era = GameStatsCalc.eraStats(entries, currentYear = 2026)
        // Half of 19000s is reached inside the 1994 games.
        assertEquals(1994, era.goldenYear)
        assertEquals(3, era.gamesCounted)
    }

    @Test
    fun `median golden year resists a single heavy outlier`() {
        val entries = listOf(
            entry(rom = "./a.sfc", releaseYear = 1992, playTime = 3000),
            entry(rom = "./b.sfc", releaseYear = 1994, playTime = 3000),
            entry(rom = "./c.sfc", releaseYear = 1996, playTime = 3000),
            // One monster playtime in a recent year: a mode would flip to 2020,
            // the median stays in the 90s cluster.
            entry(rom = "./d.sfc", releaseYear = 2020, playTime = 8000),
        )
        val era = GameStatsCalc.eraStats(entries, currentYear = 2026)
        assertEquals(1996, era.goldenYear) // cumulative 9000/17000 crosses half at 1996
    }

    @Test
    fun `average game age is play-time weighted`() {
        val entries = listOf(
            entry(rom = "./a.sfc", releaseYear = 2016, playTime = 3000), // age 10
            entry(rom = "./b.sfc", releaseYear = 2006, playTime = 1000), // age 20
        )
        val era = GameStatsCalc.eraStats(entries, currentYear = 2026)
        // (10*3000 + 20*1000) / 4000 = 12.5 → 13
        assertEquals(13, era.avgGameAgeYears)
    }

    @Test
    fun `unplayed games and missing release dates stay out of era stats`() {
        val entries = listOf(
            entry(rom = "./a.sfc", releaseYear = 1990, playTime = 0, playCount = 0), // unplayed
            entry(rom = "./b.sfc", releaseYear = null, playTime = 5000),             // undated
        )
        val era = GameStatsCalc.eraStats(entries, currentYear = 2026)
        assertNull(era.goldenYear)
        assertEquals(0, era.gamesCounted)
    }

    @Test
    fun `even splits land the median on the older year`() {
        val entries = listOf(
            entry(rom = "./a.sfc", releaseYear = 2001, playTime = 5000),
            entry(rom = "./b.sfc", releaseYear = 1997, playTime = 5000),
        )
        val era = GameStatsCalc.eraStats(entries, currentYear = 2026)
        assertEquals(1997, era.goldenYear)
    }

    // ---- era: which games get a vote ---------------------------------------

    /**
     * The point of the hour threshold: a shelf of briefly sampled games should not out-vote the
     * ones actually played. Three modern titles opened for ten minutes each would otherwise drag
     * the answer forward.
     */
    @Test
    fun `games played under an hour do not vote when enough games clear it`() {
        val entries = listOf(
            entry(rom = "./a.sfc", releaseYear = 1994, playTime = 7200),
            entry(rom = "./b.sfc", releaseYear = 1995, playTime = 7200),
            entry(rom = "./c.sfc", releaseYear = 1996, playTime = 7200),
            entry(rom = "./d.sfc", releaseYear = 2021, playTime = 600),
            entry(rom = "./e.sfc", releaseYear = 2022, playTime = 600),
            entry(rom = "./f.sfc", releaseYear = 2023, playTime = 600),
        )
        val era = GameStatsCalc.eraStats(entries, currentYear = 2026)
        assertEquals(EraBasis.SUBSTANTIAL, era.basis)
        assertEquals("only the three long-played games should count", 3, era.gamesCounted)
        assertEquals(1995, era.goldenYear)
    }

    @Test
    fun `too few long-played games falls back to everything played`() {
        val entries = listOf(
            entry(rom = "./a.sfc", releaseYear = 1994, playTime = 7200),
            entry(rom = "./b.sfc", releaseYear = 1995, playTime = 7200),
            entry(rom = "./c.sfc", releaseYear = 2020, playTime = 600),
        )
        val era = GameStatsCalc.eraStats(entries, currentYear = 2026)
        // Two is not a median worth having, so the claim is made on the whole played set and
        // says so rather than quietly presenting one basis as the other.
        assertEquals(EraBasis.ALL_PLAYED, era.basis)
        assertEquals(3, era.gamesCounted)
    }

    @Test
    fun `exactly an hour is not more than an hour`() {
        val entries = List(3) { i ->
            entry(rom = "./$i.sfc", releaseYear = 1990 + i, playTime = GameStatsCalc.SUBSTANTIAL_PLAY_SECONDS)
        }
        val era = GameStatsCalc.eraStats(entries, currentYear = 2026)
        assertEquals(EraBasis.ALL_PLAYED, era.basis)
    }

    /** Play count stands in for time when ES-DE was not recording it, and counts the same way. */
    @Test
    fun `play count can carry a game past the hour on its own`() {
        val entries = List(3) { i ->
            entry(rom = "./$i.sfc", releaseYear = 1990 + i, playCount = 3) // 3 x 1800s = 5400s
        }
        val era = GameStatsCalc.eraStats(entries, currentYear = 2026)
        assertEquals(EraBasis.SUBSTANTIAL, era.basis)
        assertEquals(3, era.gamesCounted)
    }

    @Test
    fun `an empty library reports the fallback basis rather than claiming a filtered one`() {
        val era = GameStatsCalc.eraStats(emptyList(), currentYear = 2026)
        assertNull(era.goldenYear)
        assertEquals(EraBasis.ALL_PLAYED, era.basis)
    }
}
