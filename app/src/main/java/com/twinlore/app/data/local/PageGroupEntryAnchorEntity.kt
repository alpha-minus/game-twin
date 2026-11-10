package com.twinlore.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One header / anchor extracted from a page-group entry's saved page at bulk
 * save time. Tap in the new ToC view's Anchors tab scrolls the WebView to
 * [scrollPosition]. Anchors are NEVER edited; they're written once at save
 * time and read forever after. Cascade-deleted with their owning entry.
 */
@Entity(
    tableName = "page_group_entry_anchors",
    foreignKeys = [ForeignKey(
        entity = PageGroupEntryEntity::class,
        parentColumns = ["id"],
        childColumns = ["page_group_entry_id"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("page_group_entry_id")]
)
data class PageGroupEntryAnchorEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "page_group_entry_id") val pageGroupEntryId: String,
    val position: Int,
    val label: String,
    @ColumnInfo(name = "scroll_position") val scrollPosition: Int
)
