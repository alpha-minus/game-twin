package com.twinlore.app.data.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PageGroupManifestTest {

    private fun sampleManifest(): PageGroupManifest = PageGroupManifest(
        version = 1,
        groupTabId = "g1",
        title = "A Link to the Past Walkthrough",
        indexUrl = "https://www.zeldadungeon.net/a-link-to-the-past-walkthrough/",
        indexPageTitle = "A Link to the Past Walkthrough — Zelda Dungeon",
        activeEntryId = "e2",
        createdAt = 1735690000000L,
        lastAccessed = 1735690500000L,
        entries = listOf(
            PageGroupManifest.Entry(
                id = "e1", position = 0, label = "Index",
                savedHref = "https://www.zeldadungeon.net/a-link-to-the-past-walkthrough/",
                fragment = null, pageFile = "page-1.mht", textFile = "page-1.txt",
                scrollAnchor = null, scrollOccurrenceIndex = 0, scrollRatio = 0.0
            ),
            PageGroupManifest.Entry(
                id = "e2", position = 1, label = "Throne Room",
                savedHref = "https://www.zeldadungeon.net/.../hyrule-castle/",
                fragment = "#throne-room", pageFile = "page-2.mht", textFile = "page-2.txt",
                scrollAnchor = "throneroomtorch", scrollOccurrenceIndex = 1, scrollRatio = 0.42
            )
        )
    )

    @Test
    fun round_trip() {
        val m = sampleManifest()
        val json = PageGroupManifest.toJson(m)
        val parsed = PageGroupManifest.fromJson(json)
        assertEquals(m, parsed)
    }

    @Test
    fun null_active_entry_tolerated() {
        val m = sampleManifest().copy(activeEntryId = null)
        val json = PageGroupManifest.toJson(m)
        val parsed = PageGroupManifest.fromJson(json)
        assertNull(parsed?.activeEntryId)
    }

    @Test
    fun unknown_fields_ignored() {
        val raw = """
            {
              "version": 1,
              "groupTabId": "g1",
              "title": "X",
              "indexUrl": "https://a.example/",
              "indexPageTitle": "X",
              "activeEntryId": null,
              "createdAt": 0,
              "lastAccessed": 0,
              "futureField": "ignored",
              "entries": []
            }
        """.trimIndent()
        val parsed = PageGroupManifest.fromJson(raw)
        assertEquals("g1", parsed?.groupTabId)
        assertTrue(parsed?.entries?.isEmpty() == true)
    }

    @Test
    fun rejects_traversal_in_pageFile() {
        val raw = """
            {
              "version": 1, "groupTabId": "g1", "title": "X",
              "indexUrl": "https://a/", "indexPageTitle": "X",
              "activeEntryId": null, "createdAt": 0, "lastAccessed": 0,
              "entries": [{
                "id": "e1", "position": 0, "label": "I",
                "savedHref": "https://a/", "fragment": null,
                "pageFile": "../oops.mht", "textFile": "page-1.txt",
                "scrollAnchor": null, "scrollOccurrenceIndex": 0, "scrollRatio": 0.0
              }]
            }
        """.trimIndent()
        assertNull(PageGroupManifest.fromJson(raw))
    }

    @Test
    fun rejects_traversal_in_textFile() {
        val raw = """
            {
              "version": 1, "groupTabId": "g1", "title": "X",
              "indexUrl": "https://a/", "indexPageTitle": "X",
              "activeEntryId": null, "createdAt": 0, "lastAccessed": 0,
              "entries": [{
                "id": "e1", "position": 0, "label": "I",
                "savedHref": "https://a/", "fragment": null,
                "pageFile": "page-1.mht", "textFile": "../oops.txt",
                "scrollAnchor": null, "scrollOccurrenceIndex": 0, "scrollRatio": 0.0
              }]
            }
        """.trimIndent()
        assertNull(PageGroupManifest.fromJson(raw))
    }

    @Test
    fun anchors_round_trip() {
        val m = sampleManifest().copy(
            entries = listOf(
                PageGroupManifest.Entry(
                    id = "e1", position = 0, label = "Index",
                    savedHref = "https://x/", fragment = null,
                    pageFile = "page-1.mht", textFile = null,
                    scrollAnchor = null, scrollOccurrenceIndex = 0, scrollRatio = 0.0,
                    anchors = listOf(
                        PageGroupManifest.Anchor(position = 0, label = "Top", scrollPosition = 0),
                        PageGroupManifest.Anchor(position = 1, label = "Mid", scrollPosition = 400)
                    )
                )
            )
        )
        val json = PageGroupManifest.toJson(m)
        val parsed = PageGroupManifest.fromJson(json)
        assertEquals(m, parsed)
    }

    @Test
    fun entry_without_anchors_field_parses_as_empty_list() {
        val raw = """
            {
              "version": 1, "groupTabId": "g1", "title": "X",
              "indexUrl": "https://x/", "indexPageTitle": "X",
              "activeEntryId": null, "createdAt": 0, "lastAccessed": 0,
              "entries": [{
                "id": "e1", "position": 0, "label": "I",
                "savedHref": "https://x/", "fragment": null,
                "pageFile": "page-1.mht", "textFile": null,
                "scrollAnchor": null, "scrollOccurrenceIndex": 0, "scrollRatio": 0.0
              }]
            }
        """.trimIndent()
        val parsed = PageGroupManifest.fromJson(raw)!!
        assertTrue(parsed.entries[0].anchors.isEmpty())
    }
}
