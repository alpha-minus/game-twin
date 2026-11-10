package com.twinlore.app.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "checklist_sections",
    foreignKeys = [
        ForeignKey(
            entity = VaultEntity::class,
            parentColumns = ["id"],
            childColumns = ["vaultId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ChecklistTabEntity::class,
            parentColumns = ["id"],
            childColumns = ["tabId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["vaultId"]), Index(value = ["tabId"])]
)
data class ChecklistSectionEntity(
    @PrimaryKey val id: String,
    val vaultId: String,
    val tabId: String,
    val name: String,
    val expanded: Boolean,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long
)
