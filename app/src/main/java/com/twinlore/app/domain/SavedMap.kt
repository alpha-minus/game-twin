package com.twinlore.app.domain

import java.util.Date

data class SavedMap(
    val id: String,
    val vaultId: String,
    val title: String,
    val resourceId: String? = null,
    val order: Int = 0,
    val createdAt: Date,
    val lastAccessed: Date
)
