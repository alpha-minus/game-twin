package com.twinlore.app.domain

import java.util.Date

/**
 * Domain model for a saved browser tab
 */
data class SavedTab(
    val id: String,
    val vaultId: String,
    val title: String,
    val url: String,
    val scrollPosition: Int = 0,
    val zoomLevel: Float = 1.0f,
    val isFavorite: Boolean = false,
    val isMap: Boolean = false,
    val isCurrentContent: Boolean = false,
    val order: Int = 0,
    val createdAt: Date,
    val lastAccessed: Date,
    val fileType: String = "html",
    val fileName: String? = null,
    val lastPdfPage: Int = 0,
    val pdfScrollOffset: Float = 0f,
    val resourceId: String? = null,
    val scrollRestoreData: ScrollRestoreData? = null,
    val faviconBytes: ByteArray? = null,
    val groupActiveEntryId: String? = null
) {
    /**
     * Get display title for the tab
     */
    val displayTitle: String
        get() = when {
            title.isNotBlank() -> title
            url.isNotBlank() -> {
                // Extract domain or last part of URL
                try {
                    val urlObj = java.net.URL(url)
                    urlObj.host ?: urlObj.path.takeIf { it.isNotBlank() } ?: "New Tab"
                } catch (e: Exception) {
                    url.take(30) + if (url.length > 30) "..." else ""
                }
            }
            else -> "New Tab"
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SavedTab) return false
        if (id != other.id) return false
        if (vaultId != other.vaultId) return false
        if (title != other.title) return false
        if (url != other.url) return false
        if (scrollPosition != other.scrollPosition) return false
        if (zoomLevel != other.zoomLevel) return false
        if (isFavorite != other.isFavorite) return false
        if (isMap != other.isMap) return false
        if (isCurrentContent != other.isCurrentContent) return false
        if (order != other.order) return false
        if (createdAt != other.createdAt) return false
        if (lastAccessed != other.lastAccessed) return false
        if (fileType != other.fileType) return false
        if (fileName != other.fileName) return false
        if (lastPdfPage != other.lastPdfPage) return false
        if (pdfScrollOffset != other.pdfScrollOffset) return false
        if (resourceId != other.resourceId) return false
        if (scrollRestoreData != other.scrollRestoreData) return false
        if (faviconBytes != null) {
            if (other.faviconBytes == null) return false
            if (!faviconBytes.contentEquals(other.faviconBytes)) return false
        } else if (other.faviconBytes != null) return false
        if (groupActiveEntryId != other.groupActiveEntryId) return false
        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + vaultId.hashCode()
        result = 31 * result + title.hashCode()
        result = 31 * result + url.hashCode()
        result = 31 * result + scrollPosition
        result = 31 * result + zoomLevel.hashCode()
        result = 31 * result + isFavorite.hashCode()
        result = 31 * result + isMap.hashCode()
        result = 31 * result + isCurrentContent.hashCode()
        result = 31 * result + order
        result = 31 * result + createdAt.hashCode()
        result = 31 * result + lastAccessed.hashCode()
        result = 31 * result + fileType.hashCode()
        result = 31 * result + (fileName?.hashCode() ?: 0)
        result = 31 * result + lastPdfPage
        result = 31 * result + pdfScrollOffset.hashCode()
        result = 31 * result + (resourceId?.hashCode() ?: 0)
        result = 31 * result + (scrollRestoreData?.hashCode() ?: 0)
        result = 31 * result + (faviconBytes?.contentHashCode() ?: 0)
        result = 31 * result + (groupActiveEntryId?.hashCode() ?: 0)
        return result
    }
}

