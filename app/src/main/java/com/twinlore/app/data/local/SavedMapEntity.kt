package com.twinlore.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

@Entity(
    tableName = "saved_maps",
    foreignKeys = [ForeignKey(
        entity = VaultEntity::class,
        parentColumns = ["id"],
        childColumns = ["vaultId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["vaultId"])]
)
data class SavedMapEntity(
    @PrimaryKey val id: String,
    val vaultId: String,
    val title: String,
    @ColumnInfo(name = "resource_id")
    val resourceId: String? = null,
    @ColumnInfo(name = "map_order") val order: Int = 0,
    val createdAt: Date,
    val lastAccessed: Date
)
