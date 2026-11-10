package com.twinlore.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "media_search_paths")
data class MediaSearchPathEntity(
    @PrimaryKey val id: String,
    val uri: String,
    val label: String,
    val origin: String,                                        // AUTO_ESDE | AUTO_COCOON | AUTO_HOME | MANUAL
    @ColumnInfo(name = "last_scanned_at") val lastScannedAt: Long?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "disabled", defaultValue = "0") val disabled: Boolean = false,
)
