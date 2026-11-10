package com.twinlore.app.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class ScrapeArtworkSortTest {

    private fun opt(type: String, index: Int, w: Int?, h: Int?) = ArtworkOption(
        url = "$type-$index",
        thumbnailUrl = null,
        type = type,
        index = index,
        width = w,
        height = h,
    )

    @Test
    fun `closest ratio to target sorts first`() {
        val square = opt("Square", 1, 100, 100)   // 1.0
        val portrait = opt("Portrait", 1, 200, 300) // 0.667 (2:3)
        val landscape = opt("Landscape", 1, 300, 200) // 1.5 (3:2)

        // target 3:4 = 0.75; 2:3 (0.667) is closest.
        val sorted = sortArtworkOptionsByRatio(listOf(square, landscape, portrait), 0.75f)

        assertEquals(listOf("Portrait-1", "Square-1", "Landscape-1"), sorted.map { it.url })
    }

    @Test
    fun `option with null dimensions sorts last`() {
        val unknown = opt("Unknown", 1, null, null)
        val portrait = opt("Portrait", 1, 200, 300) // 0.667
        val square = opt("Square", 1, 100, 100)      // 1.0

        val sorted = sortArtworkOptionsByRatio(listOf(unknown, square, portrait), 0.75f)

        assertEquals("Unknown-1", sorted.last().url)
    }

    @Test
    fun `non-positive target returns input order unchanged`() {
        val a = opt("A", 1, 300, 200)
        val b = opt("B", 1, 200, 300)
        val input = listOf(a, b)

        assertEquals(input, sortArtworkOptionsByRatio(input, 0f))
        assertEquals(input, sortArtworkOptionsByRatio(input, -1f))
    }
}
