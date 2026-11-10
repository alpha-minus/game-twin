package com.twinlore.app.data.local

/** Row of [VaultDao.getContentCounts]. */
data class VaultContentCount(
    val vaultId: String,
    val contentCount: Int,
    val guideCount: Int = 0,
    val imageCount: Int = 0,
    val totalBytes: Long = 0,
    val guideImageBytes: Long = 0,
)
