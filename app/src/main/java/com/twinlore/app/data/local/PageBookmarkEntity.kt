package com.twinlore.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "page_bookmarks",
    foreignKeys = [ForeignKey(
        entity = SavedTabEntity::class,
        parentColumns = ["id"],
        childColumns = ["saved_tab_id"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["saved_tab_id"])]
)
data class PageBookmarkEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "saved_tab_id") val savedTabId: String,
    val label: String?,
    @ColumnInfo(name = "scroll_position") val scrollPosition: Int,
    @ColumnInfo(name = "created_at") val createdAt: Long
)
