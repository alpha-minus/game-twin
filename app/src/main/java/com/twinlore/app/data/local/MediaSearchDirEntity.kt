package com.twinlore.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "media_search_dirs",
    foreignKeys = [
        ForeignKey(
            entity = MediaSearchPathEntity::class,
            parentColumns = ["id"],
            childColumns = ["search_path_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["search_path_id", "relative_dir"], unique = true),
        Index(value = ["search_path_id", "normalized_name"]),
    ]
)
data class MediaSearchDirEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "search_path_id") val searchPathId: String,
    @ColumnInfo(name = "relative_dir") val relativeDir: String,
    val mtime: Long,
    @ColumnInfo(name = "normalized_name", defaultValue = "''") val normalizedName: String = "",
)
