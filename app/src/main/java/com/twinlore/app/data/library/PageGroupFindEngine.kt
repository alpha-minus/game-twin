package com.twinlore.app.data.library

import com.twinlore.app.data.local.PageGroupEntryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File

/**
 * Cross-page find across one page-group. Streams matches as they're
 * found, attributed to the most-specific ToC entry whose anchor
 * precedes the match offset.
 *
 * `sidecarResolver(mhtResourceId)` returns the on-disk `.txt` sidecar
 * file for the given resource. Tests pass a stub; production maps
 * resourceId → `<vault>/groups/<groupTabId>/page-N.txt`.
 */
class PageGroupFindEngine(
    private val sidecarResolver: suspend (mhtResourceId: String) -> File?
) {
    data class Result(
        val entryId: String,
        val mhtResourceId: String,
        val matchOffset: Int,
        val snippet: String,
        /**
         * 0-based count of matches whose `entryId` is the same as this one,
         * in emission order. Lets the viewer scroll to the SPECIFIC tapped
         * occurrence (via [scrollToMarkerByText]'s `occurrenceIndex`, which
         * is 1-based — callers convert with `+ 1`).
         */
        val occurrenceInEntry: Int
    )

    fun find(entries: List<PageGroupEntryEntity>, query: String): Flow<Result> = flow {
        val q = query.trim()
        if (q.isEmpty()) return@flow

        val byResource: Map<String, List<PageGroupEntryEntity>> =
            entries.filter { it.mhtResourceId != null }.groupBy { it.mhtResourceId!! }

        val tocOrder = entries.mapIndexed { idx, e -> e.id to idx }.toMap()

        val resourceOrder = byResource.entries.sortedBy { (_, rows) ->
            rows.minOf { tocOrder.getValue(it.id) }
        }

        // Per-entry occurrence counter so the viewer can scroll to the
        // SPECIFIC tapped match (not just any match of the same query).
        // Persists across resources because two ToC entries on different
        // pages still have distinct entry ids.
        val occByEntry = mutableMapOf<String, Int>()

        for ((resourceId, rows) in resourceOrder) {
            val sidecar = sidecarResolver(resourceId) ?: continue
            if (!sidecar.exists()) continue
            val text = sidecar.readText()
            val anchorPositions = collectAnchorPositions(text)
            val rowsByFragment: Map<String?, PageGroupEntryEntity> = rows.associateBy { it.fragment?.removePrefix("#") }
            val rowsInTocOrder = rows.sortedBy { tocOrder.getValue(it.id) }
            val firstBareRow = rowsInTocOrder.firstOrNull { it.fragment == null }

            var i = 0
            while (i < text.length) {
                val match = indexOfIgnoreCaseSkippingMarkers(text, q, i)
                if (match < 0) break
                val attributedEntryId = attribute(
                    matchOffset = match,
                    anchorPositions = anchorPositions,
                    rowsByFragment = rowsByFragment,
                    firstBareRow = firstBareRow,
                    fallback = rowsInTocOrder.first()
                )
                val occ = occByEntry.getOrDefault(attributedEntryId, 0)
                occByEntry[attributedEntryId] = occ + 1
                emit(
                    Result(
                        entryId = attributedEntryId,
                        mhtResourceId = resourceId,
                        matchOffset = match,
                        snippet = makeSnippet(text, match, q.length),
                        occurrenceInEntry = occ
                    )
                )
                i = match + q.length
            }
        }
    }.flowOn(Dispatchers.Default)

    private data class Marker(val offset: Int, val id: String)

    private fun collectAnchorPositions(text: String): List<Marker> {
        val out = mutableListOf<Marker>()
        var i = 0
        val prefix = "[[anchor:"
        while (true) {
            val start = text.indexOf(prefix, i)
            if (start == -1) break
            val close = text.indexOf("]]", start)
            if (close == -1) break
            val id = text.substring(start + prefix.length, close)
            out += Marker(start, id)
            i = close + 2
        }
        return out
    }

    /**
     * Find the next occurrence of [needle] in [text] starting at [from],
     * skipping any region that lies entirely inside a `[[anchor:...]]` marker.
     */
    private fun indexOfIgnoreCaseSkippingMarkers(text: String, needle: String, from: Int): Int {
        var i = from
        while (i <= text.length - needle.length) {
            val candidate = text.regionMatches(i, needle, 0, needle.length, ignoreCase = true)
            if (candidate) {
                if (!fallsInsideMarker(text, i, i + needle.length)) return i
            }
            i++
        }
        return -1
    }

    private fun fallsInsideMarker(text: String, start: Int, end: Int): Boolean {
        var j = start - 1
        while (j >= 0) {
            if (text.regionMatches(j, "]]", 0, 2, false)) return false
            if (text.regionMatches(j, "[[anchor:", 0, 9, false)) {
                val close = text.indexOf("]]", j)
                return close >= end
            }
            j--
        }
        return false
    }

    private fun attribute(
        matchOffset: Int,
        anchorPositions: List<Marker>,
        rowsByFragment: Map<String?, PageGroupEntryEntity>,
        firstBareRow: PageGroupEntryEntity?,
        fallback: PageGroupEntryEntity
    ): String {
        val nearest = anchorPositions.lastOrNull { it.offset <= matchOffset }
        if (nearest != null) {
            rowsByFragment[nearest.id]?.let { return it.id }
        }
        return firstBareRow?.id ?: fallback.id
    }

    private fun makeSnippet(text: String, matchOffset: Int, queryLen: Int): String {
        // ±100 chars around the match: 40 was too tight for the find screen's
        // 3-line layout (snippets ended mid-second-line). 200 + query fills
        // 3 lines of bodyMedium on typical phone widths.
        val pre = (matchOffset - 100).coerceAtLeast(0)
        val post = (matchOffset + queryLen + 100).coerceAtMost(text.length)
        return text.substring(pre, post)
            .replace(Regex("\\[\\[anchor:[^]]*]]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
