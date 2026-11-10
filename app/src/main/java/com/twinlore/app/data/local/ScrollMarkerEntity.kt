package com.twinlore.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "scroll_markers",
    foreignKeys = [ForeignKey(
        entity = SavedTabEntity::class,
        parentColumns = ["id"],
        childColumns = ["saved_tab_id"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["saved_tab_id"])]
)
data class ScrollMarkerEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "saved_tab_id") val savedTabId: String,
    @ColumnInfo(name = "scroll_position") val scrollPosition: Int,
    @ColumnInfo(name = "marker_order") val markerOrder: Int,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    val label: String = "",
    @ColumnInfo(name = "occurrence_index") val occurrenceIndex: Int = 0,

    /**
     * For markers saved inside a page-group, the page_group_entries.id of the
     * entry that was active when the marker was saved. Null for single-page
     * saved-tab markers (the pre-v33 use case, no behavior change).
     */
    @ColumnInfo(name = "page_group_entry_id")
    val pageGroupEntryId: String? = null
)
