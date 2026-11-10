package com.twinlore.app.data.library

import com.twinlore.app.data.local.MapMarkerDao
import com.twinlore.app.data.local.MapMarkerEntity
import com.twinlore.app.data.local.PageBookmarkDao
import com.twinlore.app.data.local.PageBookmarkEntity
import com.twinlore.app.data.local.ScrollMarkerDao
import com.twinlore.app.data.local.ScrollMarkerEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import javax.inject.Inject

class JsonSidecarImporter @Inject constructor(
    private val mapMarkerDao: MapMarkerDao,
    private val pageBookmarkDao: PageBookmarkDao,
    private val scrollMarkerDao: ScrollMarkerDao
) {

    suspend fun importMapMarkers(mapDir: File, resourceFileId: String): Int = withContext(Dispatchers.IO) {
        val file = File(mapDir, "markers.json")
        if (!file.exists()) return@withContext 0
        val arr = runCatching { JSONArray(file.readText()) }.getOrNull() ?: return@withContext 0
        val rows = mutableListOf<MapMarkerEntity>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            rows += MapMarkerEntity(
                id = obj.optString("id").ifEmpty { java.util.UUID.randomUUID().toString() },
                resourceFileId = resourceFileId,
                label = obj.optString("label").takeIf { it.isNotEmpty() },
                xNorm = obj.optDouble("xNorm", 0.0).toFloat(),
                yNorm = obj.optDouble("yNorm", 0.0).toFloat(),
                color = if (obj.has("color")) obj.optInt("color") else null,
                createdAt = obj.optLong("createdAt", System.currentTimeMillis())
            )
        }
        if (rows.isNotEmpty()) {
            mapMarkerDao.insertAll(rows)
        }
        file.delete()
        rows.size
    }

    suspend fun importPageBookmarks(bookmarkDir: File, savedTabId: String): Int = withContext(Dispatchers.IO) {
        val file = File(bookmarkDir, "page_bookmarks.json")
        if (!file.exists()) return@withContext 0
        val arr = runCatching { JSONArray(file.readText()) }.getOrNull() ?: return@withContext 0
        val rows = mutableListOf<PageBookmarkEntity>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            rows += PageBookmarkEntity(
                id = obj.optString("id").ifEmpty { java.util.UUID.randomUUID().toString() },
                savedTabId = savedTabId,
                label = obj.optString("label").takeIf { it.isNotEmpty() },
                scrollPosition = obj.optInt("scrollPosition", 0),
                createdAt = obj.optLong("createdAt", System.currentTimeMillis())
            )
        }
        if (rows.isNotEmpty()) pageBookmarkDao.insertAll(rows)
        file.delete()
        rows.size
    }

    suspend fun importScrollMarkers(bookmarkDir: File, savedTabId: String): Int = withContext(Dispatchers.IO) {
        // bookmark_metadata.json is intentionally NOT deleted after import (it may carry other
        // legacy fields), and records without an "id" generate a fresh UUID per call. The
        // orchestrator (MigrationCoordinator.runFullMigration) pre-calls
        // ScrollMarkerDao.deleteForTab(savedTabId) before invoking us so re-runs don't duplicate.
        val file = File(bookmarkDir, "bookmark_metadata.json")
        if (!file.exists()) return@withContext 0
        val obj = runCatching { JSONObject(file.readText()) }.getOrNull() ?: return@withContext 0
        val arr = obj.optJSONArray("scrollMarkers") ?: return@withContext 0
        val rows = mutableListOf<ScrollMarkerEntity>()
        for (i in 0 until arr.length()) {
            val m = arr.getJSONObject(i)
            // Legacy JSON was Gson-serialized from the old `ScrollMarker` domain class:
            //   id, label, scrollY, order, occurrenceIndex
            // Field name `scrollY` does NOT match the Room column `scroll_position`; fall
            // back to `scrollPosition` for any post-migration variant that may have used it.
            rows += ScrollMarkerEntity(
                id = m.optString("id").ifEmpty { java.util.UUID.randomUUID().toString() },
                savedTabId = savedTabId,
                scrollPosition = m.optInt("scrollY", m.optInt("scrollPosition", 0)),
                markerOrder = m.optInt("order", i),
                createdAt = m.optLong("createdAt", System.currentTimeMillis()),
                label = m.optString("label"),
                occurrenceIndex = m.optInt("occurrenceIndex", 0)
            )
        }
        if (rows.isNotEmpty()) scrollMarkerDao.insertAll(rows)
        // Note: do NOT delete bookmark_metadata.json — only the scrollMarkers field
        // was consumed; legacy file may carry other fields. Cleanup happens in a later pass.
        rows.size
    }
}
