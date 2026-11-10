package com.twinlore.app.domain

data class ChecklistSection(
    val id: String,
    val vaultId: String,
    val tabId: String,
    val name: String,
    val expanded: Boolean,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long
)
