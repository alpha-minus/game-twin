package com.twinlore.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorite_apps")
data class FavoriteAppEntity(
    @PrimaryKey val packageName: String,
    val addedAt: Long = System.currentTimeMillis(),
    // Strip/launcher order. Appended on pin (MAX+1), rewritten as a block on reorder.
    // The SQL default mirrors MIGRATION_36_37's ALTER TABLE, or Room's identity check
    // rejects the migrated table on first open.
    @ColumnInfo(defaultValue = "0")
    val position: Int = 0,
)
