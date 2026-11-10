package com.twinlore.app.domain

data class ChecklistItem(
    val id: String,
    val vaultId: String,
    val tagId: String,
    val sectionId: String,
    val title: String,
    val completed: Boolean,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long
)
