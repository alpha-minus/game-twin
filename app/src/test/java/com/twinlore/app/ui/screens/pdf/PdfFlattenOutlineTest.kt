package com.twinlore.app.ui.screens.pdf

import org.junit.Assert.assertEquals
import org.junit.Test

class PdfFlattenOutlineTest {

    @Test
    fun emptyInputProducesEmptyOutput() {
        assertEquals(emptyList<Pair<PdfOutlineEntry, Int>>(), flattenOutline(emptyList()))
    }

    @Test
    fun flatListPreservesOrderAndDepthZero() {
        val input = listOf(
            PdfOutlineEntry("A", 1, emptyList()),
            PdfOutlineEntry("B", 2, emptyList()),
            PdfOutlineEntry("C", 3, emptyList()),
        )
        val out = flattenOutline(input)
        assertEquals(3, out.size)
        assertEquals("A", out[0].first.title); assertEquals(0, out[0].second)
        assertEquals("B", out[1].first.title); assertEquals(0, out[1].second)
        assertEquals("C", out[2].first.title); assertEquals(0, out[2].second)
    }

    @Test
    fun nestedTreeIsPreorderTraversedWithDepthIncrement() {
        val input = listOf(
            PdfOutlineEntry("Chapter 1", 1, listOf(
                PdfOutlineEntry("1.1", 2, emptyList()),
                PdfOutlineEntry("1.2", 4, listOf(
                    PdfOutlineEntry("1.2.1", 5, emptyList()),
                )),
            )),
            PdfOutlineEntry("Chapter 2", 8, emptyList()),
        )
        val out = flattenOutline(input)
        val expected = listOf(
            "Chapter 1" to 0,
            "1.1" to 1,
            "1.2" to 1,
            "1.2.1" to 2,
            "Chapter 2" to 0,
        )
        assertEquals(expected, out.map { it.first.title to it.second })
    }

    @Test
    fun explicitDepthArgumentShiftsBase() {
        val input = listOf(PdfOutlineEntry("X", 1, listOf(PdfOutlineEntry("Y", 2, emptyList()))))
        val out = flattenOutline(input, depth = 5)
        assertEquals(listOf("X" to 5, "Y" to 6), out.map { it.first.title to it.second })
    }
}
