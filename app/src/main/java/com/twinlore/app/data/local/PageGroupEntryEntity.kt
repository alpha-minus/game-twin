package com.twinlore.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One ToC entry in a page-group. Multiple entries can share the same
 * `mht_resource_id` — anchor sub-entries of the same saved file. Entries
 * are ordered within their group by [position] (dense 0..N-1).
 *
 * An entry with `mht_resource_id = null` is a "failed save" placeholder:
 * its file never made it to disk. The UI renders it disabled with a
 * warning badge but it still occupies its slot in prev/next.
 */
@Entity(
    tableName = "page_group_entries",
    foreignKeys = [
        ForeignKey(
            entity = SavedTabEntity::class,
            parentColumns = ["id"],
            childColumns = ["group_tab_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ResourceFileEntity::class,
            parentColumns = ["id"],
            childColumns = ["mht_resource_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("group_tab_id"), Index("mht_resource_id")]
)
data class PageGroupEntryEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "group_tab_id") val groupTabId: String,
    val position: Int,
    val label: String,
    @ColumnInfo(name = "saved_href") val savedHref: String,
    val fragment: String?,
    @ColumnInfo(name = "mht_resource_id") val mhtResourceId: String?,
    @ColumnInfo(name = "scroll_anchor") val scrollAnchor: String? = null,
    @ColumnInfo(name = "scroll_occurrence_index", defaultValue = "0") val scrollOccurrenceIndex: Int = 0,
    @ColumnInfo(name = "scroll_ratio", defaultValue = "0.0") val scrollRatio: Double = 0.0,
    /**
     * True for body-only-discovered page entries (see ToCDetector pass 3).
     * Persisted so the on-disk manifest round-trip preserves the distinction;
     * filtered from the Contents tab and from the picker's "N pages" badge so
     * the user sees only named chapters in displays. Synthetic entries still
     * participate in prev/next navigation since they're real saved pages —
     * just without a chapter label in the original ToC.
     */
    @ColumnInfo(name = "synthetic", defaultValue = "0") val synthetic: Boolean = false
)
