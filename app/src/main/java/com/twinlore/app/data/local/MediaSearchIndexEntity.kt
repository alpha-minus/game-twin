package com.twinlore.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "media_search_index",
    foreignKeys = [
        ForeignKey(
            entity = MediaSearchPathEntity::class,
            parentColumns = ["id"],
            childColumns = ["search_path_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["search_path_id"]),
        Index(value = ["search_path_id", "normalized_basename"]),
        Index(value = ["search_path_id", "normalized_path_tokens"]),
        Index(value = ["filename", "size_bytes"])
    ]
)
data class MediaSearchIndexEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "search_path_id") val searchPathId: String,
    @ColumnInfo(name = "absolute_path") val absolutePath: String?,
    @ColumnInfo(name = "relative_path") val relativePath: String?,
    val filename: String,
    @ColumnInfo(name = "normalized_basename") val normalizedBasename: String,
    @ColumnInfo(name = "normalized_path_tokens") val normalizedPathTokens: String,
    @ColumnInfo(name = "ext_lower") val extLower: String,
    @ColumnInfo(name = "size_bytes") val sizeBytes: Long,
    val mtime: Long,
)
