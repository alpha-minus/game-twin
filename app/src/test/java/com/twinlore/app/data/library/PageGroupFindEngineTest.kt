package com.twinlore.app.data.library

import com.twinlore.app.data.local.PageGroupEntryEntity
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PageGroupFindEngineTest {

    private fun tempSidecar(text: String): File =
        File.createTempFile("sidecar", ".txt").apply { writeText(text); deleteOnExit() }

    @Test
    fun match_after_anchor_attributed_to_that_anchor_entry() = runTest {
        val sidecar = tempSidecar(
            "[[anchor:throne-room]]\nThe throne room is dimly lit. master sword wielders may notice.\n" +
            "[[anchor:sewers]]\nThe sewers below are flooded.\n"
        )
        val entries = listOf(
            PageGroupEntryEntity("e0", "g1", 0, "Hyrule Castle", "u1", null, "r1"),
            PageGroupEntryEntity("e1", "g1", 1, "Throne Room", "u1", "#throne-room", "r1"),
            PageGroupEntryEntity("e2", "g1", 2, "Sewers", "u1", "#sewers", "r1")
        )
        val engine = PageGroupFindEngine(sidecarResolver = { _ -> sidecar })
        val results = engine.find(entries, "master sword").toList()
        assertEquals(1, results.size)
        assertEquals("e1", results[0].entryId) // throne-room
    }

    @Test
    fun match_before_first_anchor_attributed_to_first_bare_entry() = runTest {
        val sidecar = tempSidecar(
            "Preamble mentions sword.\n[[anchor:body]]\nMore body text.\n"
        )
        val entries = listOf(
            PageGroupEntryEntity("e0", "g1", 0, "Chapter", "u1", null, "r1"),
            PageGroupEntryEntity("e1", "g1", 1, "Body", "u1", "#body", "r1")
        )
        val engine = PageGroupFindEngine(sidecarResolver = { _ -> sidecar })
        val results = engine.find(entries, "sword").toList()
        assertEquals("e0", results.single().entryId)
    }

    @Test
    fun multi_file_aggregation_preserves_toc_order() = runTest {
        val s1 = tempSidecar("page one has match here.\n")
        val s2 = tempSidecar("page two also has the match.\n")
        val entries = listOf(
            PageGroupEntryEntity("e0", "g1", 0, "C1", "u1", null, "r1"),
            PageGroupEntryEntity("e1", "g1", 1, "C2", "u2", null, "r2")
        )
        val engine = PageGroupFindEngine(sidecarResolver = { rid ->
            if (rid == "r1") s1 else s2
        })
        val results = engine.find(entries, "match").toList()
        assertEquals(listOf("e0", "e1"), results.map { it.entryId })
    }

    @Test
    fun occurrence_in_entry_is_zero_based_and_counts_per_entry() = runTest {
        // Two matches attributed to e0, two to e1. The counter resets per
        // entry, so each pair runs 0, 1.
        val sidecar = tempSidecar(
            "Preamble has match and another match here.\n" +
            "[[anchor:body]]\nBody has a match. Last match in body.\n"
        )
        val entries = listOf(
            PageGroupEntryEntity("e0", "g1", 0, "Pre", "u1", null, "r1"),
            PageGroupEntryEntity("e1", "g1", 1, "Body", "u1", "#body", "r1")
        )
        val engine = PageGroupFindEngine(sidecarResolver = { _ -> sidecar })
        val results = engine.find(entries, "match").toList()
        assertEquals(4, results.size)
        assertEquals(listOf("e0", "e0", "e1", "e1"), results.map { it.entryId })
        assertEquals(listOf(0, 1, 0, 1), results.map { it.occurrenceInEntry })
    }

    @Test
    fun anchor_markers_are_not_matchable_text() = runTest {
        val sidecar = tempSidecar("[[anchor:throne-room]]\nUnrelated body.\n")
        val entries = listOf(
            PageGroupEntryEntity("e0", "g1", 0, "Throne", "u1", "#throne-room", "r1")
        )
        val engine = PageGroupFindEngine(sidecarResolver = { _ -> sidecar })
        val results = engine.find(entries, "anchor").toList()
        assertTrue(results.isEmpty())
    }

    @Test
    fun empty_query_emits_nothing() = runTest {
        val sidecar = tempSidecar("body")
        val entries = listOf(PageGroupEntryEntity("e0", "g1", 0, "X", "u1", null, "r1"))
        val engine = PageGroupFindEngine(sidecarResolver = { _ -> sidecar })
        assertTrue(engine.find(entries, "").toList().isEmpty())
        assertTrue(engine.find(entries, "   ").toList().isEmpty())
    }
}
