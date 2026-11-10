package com.twinlore.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "map_markers",
    foreignKeys = [ForeignKey(
        entity = ResourceFileEntity::class,
        parentColumns = ["id"],
        childColumns = ["resource_file_id"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["resource_file_id"])]
)
data class MapMarkerEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "resource_file_id") val resourceFileId: String,
    val label: String?,
    @ColumnInfo(name = "x_norm") val xNorm: Float,
    @ColumnInfo(name = "y_norm") val yNorm: Float,
    val color: Int?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    val zoom: Float = 1.0f,
    @ColumnInfo(name = "offset_x") val offsetX: Float = 0.0f,
    @ColumnInfo(name = "offset_y") val offsetY: Float = 0.0f,
    @ColumnInfo(name = "style_id") val styleId: Int = 1
)
