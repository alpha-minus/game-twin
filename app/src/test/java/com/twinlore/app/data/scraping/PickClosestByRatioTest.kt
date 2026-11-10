package com.twinlore.app.data.scraping

import org.junit.Assert.assertEquals
import org.junit.Test

class PickClosestByRatioTest {

    private fun c(w: Int?, h: Int?, tag: String) = AssetCandidate(url = tag, width = w, height = h)

    /** The bug: within the preferred type, the CLOSEST-ratio candidate must
     *  win — not an arbitrary first(). target 3:1 should pick the 3.1 hero,
     *  not the 2.46 one. */
    @Test
    fun picks_closest_within_preferred_type() {
        val art = ScrapedArtwork(
            provider = "t", gameId = "1", gameName = "g",
            grids = listOf(c(600, 900, "grid")),
            heroes = listOf(c(1600, 650, "hero_246"), c(1860, 600, "hero_310")),
            logos = emptyList(),
        )
        val picked = art.pickClosestByRatio(targetRatio = 3.0f, preferredType = ArtworkType.HERO)
        assertEquals("hero_310", picked?.url)
    }

    /** The preferred type is prioritized even when it is dimensionless and
     *  another type has a perfect-ratio candidate — user picked HERO, so a
     *  hero is assigned. */
    @Test
    fun preferred_type_used_even_when_dimensionless() {
        val art = ScrapedArtwork(
            provider = "t", gameId = "1", gameName = "g",
            grids = listOf(c(600, 900, "grid_23")),
            heroes = listOf(c(null, null, "hero_nodims")),
            logos = emptyList(),
        )
        val picked = art.pickClosestByRatio(targetRatio = 0.667f, preferredType = ArtworkType.HERO)
        assertEquals("hero_nodims", picked?.url)
    }

    /** Preferred type wins even when another type is a closer ratio match:
     *  preferred HERO (3.1) is kept over a logo sitting at exactly 3.0. */
    @Test
    fun preferred_type_prioritized_over_closer_other_type() {
        val art = ScrapedArtwork(
            provider = "t", gameId = "1", gameName = "g",
            grids = emptyList(),
            heroes = listOf(c(1860, 600, "hero_310")),
            logos = listOf(c(900, 300, "logo_300")),
        )
        val picked = art.pickClosestByRatio(targetRatio = 3.0f, preferredType = ArtworkType.HERO)
        assertEquals("hero_310", picked?.url)
    }

    /** Falls back to other types (GRID, LOGO, HERO order) only when the
     *  preferred type is empty. */
    @Test
    fun falls_back_to_other_types_when_preferred_empty() {
        val art = ScrapedArtwork(
            provider = "t", gameId = "1", gameName = "g",
            grids = listOf(c(600, 900, "grid_23")),
            heroes = emptyList(),
            logos = emptyList(),
        )
        val picked = art.pickClosestByRatio(targetRatio = 0.667f, preferredType = ArtworkType.HERO)
        assertEquals("grid_23", picked?.url)
    }

    /** All dimensionless → preferred type's first candidate (preserves the old
     *  fallback when no provider metadata exists at all). */
    @Test
    fun all_dimensionless_falls_back_to_preferred_type_first() {
        val art = ScrapedArtwork(
            provider = "t", gameId = "1", gameName = "g",
            grids = listOf(c(null, null, "grid1")),
            heroes = listOf(c(null, null, "hero1"), c(null, null, "hero2")),
            logos = emptyList(),
        )
        val picked = art.pickClosestByRatio(targetRatio = 3.0f, preferredType = ArtworkType.HERO)
        assertEquals("hero1", picked?.url)
    }

    @Test
    fun empty_artwork_returns_null() {
        val art = ScrapedArtwork("t", "1", "g", emptyList(), emptyList(), emptyList())
        assertEquals(null, art.pickClosestByRatio(0.667f, ArtworkType.GRID))
    }
}
