package com.twinlore.app.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "checklist_items",
    foreignKeys = [
        ForeignKey(
            entity = VaultEntity::class,
            parentColumns = ["id"],
            childColumns = ["vaultId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ChecklistTagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.NO_ACTION
        ),
        ForeignKey(
            entity = ChecklistSectionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sectionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["vaultId"]),
        Index(value = ["tagId"]),
        Index(value = ["sectionId"])
    ]
)
data class ChecklistItemEntity(
    @PrimaryKey val id: String,
    val vaultId: String,
    val tagId: String,        // kept temporarily; removed in a follow-up migration
    val sectionId: String,
    val title: String,
    val completed: Boolean,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long
)
