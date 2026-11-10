package com.twinlore.app.data.library

import com.twinlore.app.data.local.MapMarkerDao
import com.twinlore.app.data.local.MapMarkerEntity
import com.twinlore.app.data.local.PageBookmarkDao
import com.twinlore.app.data.local.ScrollMarkerDao
import com.twinlore.app.data.local.ScrollMarkerEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class JsonSidecarImporterTest {

    @get:Rule val tempFolder = TemporaryFolder()

    private val mapMarkerDao: MapMarkerDao = mock()
    private val pageBookmarkDao: PageBookmarkDao = mock()
    private val scrollMarkerDao: ScrollMarkerDao = mock()

    @Test
    fun imports_markers_json_into_map_marker_rows() = runTest {
        val importer = JsonSidecarImporter(mapMarkerDao, pageBookmarkDao, scrollMarkerDao)
        val mapDir = tempFolder.newFolder("v1", "map", "main")
        val markersFile = java.io.File(mapDir, "markers.json")
        markersFile.writeText(
            """
            [
              {"id":"m1","label":"Boss","xNorm":0.25,"yNorm":0.75,"color":-65536,"createdAt":100},
              {"id":"m2","label":"Chest","xNorm":0.5,"yNorm":0.5,"createdAt":200}
            ]
            """.trimIndent()
        )

        importer.importMapMarkers(mapDir, resourceFileId = "rf-1")

        val captor = argumentCaptor<List<MapMarkerEntity>>()
        verify(mapMarkerDao).insertAll(captor.capture())
        val rows = captor.firstValue
        assertEquals(2, rows.size)
        assertEquals("rf-1", rows[0].resourceFileId)
        assertEquals(0.25f, rows[0].xNorm, 0.001f)
        assertEquals(false, markersFile.exists()) // deleted after import
    }

    @Test
    fun imports_scroll_markers_with_legacy_field_names() = runTest {
        val importer = JsonSidecarImporter(mapMarkerDao, pageBookmarkDao, scrollMarkerDao)
        val bookmarkDir = tempFolder.newFolder("v1", "guide", "tab-1")
        // Legacy Gson-serialized format from old `ScrollMarker` data class:
        // id, label, scrollY (NOT scrollPosition), order, occurrenceIndex.
        java.io.File(bookmarkDir, "bookmark_metadata.json").writeText(
            """
            {
              "url":"x", "title":"y",
              "scrollMarkers":[
                {"id":"a","label":"Chapter 1","scrollY":1200,"order":0,"occurrenceIndex":1},
                {"id":"b","label":"Boss Fight","scrollY":4800,"order":1,"occurrenceIndex":2}
              ]
            }
            """.trimIndent()
        )

        importer.importScrollMarkers(bookmarkDir, savedTabId = "tab-1")

        val captor = argumentCaptor<List<ScrollMarkerEntity>>()
        verify(scrollMarkerDao).insertAll(captor.capture())
        val rows = captor.firstValue
        assertEquals(2, rows.size)
        assertEquals("Chapter 1", rows[0].label)
        assertEquals(1200, rows[0].scrollPosition)
        assertEquals(1, rows[0].occurrenceIndex)
        assertEquals("Boss Fight", rows[1].label)
        assertEquals(4800, rows[1].scrollPosition)
    }
}
