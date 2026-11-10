package com.twinlore.app.data.library

import org.json.JSONArray
import org.json.JSONObject

/**
 * On-disk mirror of a page-group's Room state. Lives at
 * `<vault-folder>/groups/<groupTabId>/manifest.json`. Room is authoritative
 * at runtime; the manifest is rewritten after every Room mutation that
 * touches the group. Disk is therefore consistent or one transaction
 * behind, never ahead. Unknown JSON fields are ignored on read so future
 * versions of the app can extend the schema without breaking older builds.
 */
data class PageGroupManifest(
    val version: Int = 1,
    val groupTabId: String,
    val title: String,
    val indexUrl: String,
    val indexPageTitle: String,
    val activeEntryId: String?,
    val createdAt: Long,
    val lastAccessed: Long,
    val entries: List<Entry>
) {
    data class Anchor(
        val position: Int,
        val label: String,
        val scrollPosition: Int
    )

    data class Entry(
        val id: String,
        val position: Int,
        val label: String,
        val savedHref: String,
        val fragment: String?,
        /** Plain filename relative to the group folder. No path separators. */
        val pageFile: String?,
        /** Plain filename relative to the group folder. No path separators. */
        val textFile: String?,
        val scrollAnchor: String?,
        val scrollOccurrenceIndex: Int,
        val scrollRatio: Double,
        val anchors: List<Anchor> = emptyList(),
        /** Mirrors PageGroupEntryEntity.synthetic. Default false so legacy
         *  manifests written before the flag existed parse as named entries. */
        val synthetic: Boolean = false
    )

    companion object {
        fun toJson(m: PageGroupManifest): String {
            val root = JSONObject().apply {
                put("version", m.version)
                put("groupTabId", m.groupTabId)
                put("title", m.title)
                put("indexUrl", m.indexUrl)
                put("indexPageTitle", m.indexPageTitle)
                put("activeEntryId", m.activeEntryId ?: JSONObject.NULL)
                put("createdAt", m.createdAt)
                put("lastAccessed", m.lastAccessed)
                put("entries", JSONArray().apply {
                    m.entries.forEach { e ->
                        put(JSONObject().apply {
                            put("id", e.id)
                            put("position", e.position)
                            put("label", e.label)
                            put("savedHref", e.savedHref)
                            put("fragment", e.fragment ?: JSONObject.NULL)
                            put("pageFile", e.pageFile ?: JSONObject.NULL)
                            put("textFile", e.textFile ?: JSONObject.NULL)
                            put("scrollAnchor", e.scrollAnchor ?: JSONObject.NULL)
                            put("scrollOccurrenceIndex", e.scrollOccurrenceIndex)
                            put("scrollRatio", e.scrollRatio)
                            put("synthetic", e.synthetic)
                            put("anchors", JSONArray().apply {
                                e.anchors.forEach { a ->
                                    put(JSONObject().apply {
                                        put("position", a.position)
                                        put("label", a.label)
                                        put("scrollPosition", a.scrollPosition)
                                    })
                                }
                            })
                        })
                    }
                })
            }
            return root.toString(2)
        }

        fun fromJson(raw: String): PageGroupManifest? = runCatching {
            val o = JSONObject(raw)
            val entriesJson = o.getJSONArray("entries")
            val entries = (0 until entriesJson.length()).map { i ->
                val e = entriesJson.getJSONObject(i)
                val pageFile = e.optStringOrNull("pageFile")
                val textFile = e.optStringOrNull("textFile")
                if (pageFile != null && !isPlainFilename(pageFile)) return@runCatching null
                if (textFile != null && !isPlainFilename(textFile)) return@runCatching null
                val anchorsJson = e.optJSONArray("anchors")
                val anchors = if (anchorsJson == null) emptyList() else
                    (0 until anchorsJson.length()).map { ai ->
                        val ao = anchorsJson.getJSONObject(ai)
                        Anchor(
                            position = ao.getInt("position"),
                            label = ao.getString("label"),
                            scrollPosition = ao.getInt("scrollPosition")
                        )
                    }
                Entry(
                    id = e.getString("id"),
                    position = e.getInt("position"),
                    label = e.getString("label"),
                    savedHref = e.getString("savedHref"),
                    fragment = e.optStringOrNull("fragment"),
                    pageFile = pageFile,
                    textFile = textFile,
                    scrollAnchor = e.optStringOrNull("scrollAnchor"),
                    scrollOccurrenceIndex = e.optInt("scrollOccurrenceIndex", 0),
                    scrollRatio = e.optDouble("scrollRatio", 0.0),
                    anchors = anchors,
                    synthetic = e.optBoolean("synthetic", false)
                )
            }
            PageGroupManifest(
                version = o.optInt("version", 1),
                groupTabId = o.getString("groupTabId"),
                title = o.getString("title"),
                indexUrl = o.getString("indexUrl"),
                indexPageTitle = o.getString("indexPageTitle"),
                activeEntryId = o.optStringOrNull("activeEntryId"),
                createdAt = o.getLong("createdAt"),
                lastAccessed = o.getLong("lastAccessed"),
                entries = entries
            )
        }.getOrNull()

        private fun isPlainFilename(name: String): Boolean =
            name.isNotEmpty() && '/' !in name && '\\' !in name && name != "." && name != ".."

        /**
         * Returns null when [key] is absent or its value is JSON `null`. Also
         * collapses empty-string values to null — the current writer never
         * emits `""` for any optional field (it uses [JSONObject.NULL]), so
         * empty in is treated as missing. Callers that need to distinguish
         * `""` from null should not use this helper.
         */
        private fun JSONObject.optStringOrNull(key: String): String? =
            if (isNull(key)) null else optString(key, "").ifEmpty { null }
    }
}
