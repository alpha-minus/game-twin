package com.twinlore.app.domain

data class ChecklistTab(
    val id: String,
    val vaultId: String,
    val name: String,
    val color: Int,        // 0..11 index into ChecklistColorPalette
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long
)
