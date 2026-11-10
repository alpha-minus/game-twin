package com.twinlore.app.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "checklist_tabs",
    foreignKeys = [
        ForeignKey(
            entity = VaultEntity::class,
            parentColumns = ["id"],
            childColumns = ["vaultId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["vaultId"])]
)
data class ChecklistTabEntity(
    @PrimaryKey val id: String,
    val vaultId: String,
    val name: String,
    val color: Int,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long
)
