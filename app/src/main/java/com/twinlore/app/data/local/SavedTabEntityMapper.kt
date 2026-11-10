package com.twinlore.app.data.local

import com.twinlore.app.domain.SavedTab
import com.twinlore.app.domain.ScrollRestoreData

/**
 * Mapper functions to convert between SavedTabEntity and SavedTab domain model
 */
object SavedTabEntityMapper {
    /**
     * Convert SavedTabEntity to SavedTab domain model
     */
    fun toDomain(entity: SavedTabEntity): SavedTab {
        // Surface scrollRestoreData only when at least one of the persisted
        // scroll fields holds a meaningful value, so a freshly-saved bookmark
        // doesn't trigger a scroll-to-top restore.
        val hasAnchor = !entity.scrollNormalizedAnchor.isNullOrEmpty()
        val hasRatio = entity.scrollRatio > 0.0 && entity.scrollRatio <= 1.0
        val scrollRestore = if (hasAnchor || hasRatio) {
            ScrollRestoreData(
                normalizedAnchor = entity.scrollNormalizedAnchor,
                occurrenceIndex = entity.scrollOccurrenceIndex,
                scrollRatio = entity.scrollRatio
            )
        } else null
        return SavedTab(
            id = entity.id,
            vaultId = entity.vaultId,
            title = entity.title,
            url = entity.url,
            scrollPosition = entity.scrollPosition,
            zoomLevel = entity.zoomLevel,
            isFavorite = entity.isFavorite,
            isMap = entity.isMap,
            isCurrentContent = entity.isCurrentContent,
            order = entity.order,
            createdAt = entity.createdAt,
            lastAccessed = entity.lastAccessed,
            fileType = entity.fileType,
            fileName = entity.fileName,
            lastPdfPage = entity.lastPdfPage,
            pdfScrollOffset = entity.pdfScrollOffset,
            resourceId = entity.resourceId,
            scrollRestoreData = scrollRestore,
            faviconBytes = entity.faviconBytes,
            groupActiveEntryId = entity.groupActiveEntryId
        )
    }

    /**
     * Convert SavedTab domain model to SavedTabEntity
     */
    fun toEntity(tab: SavedTab): SavedTabEntity {
        return SavedTabEntity(
            id = tab.id,
            vaultId = tab.vaultId,
            title = tab.title,
            url = tab.url,
            scrollPosition = tab.scrollPosition,
            zoomLevel = tab.zoomLevel,
            isFavorite = tab.isFavorite,
            isMap = tab.isMap,
            isCurrentContent = tab.isCurrentContent,
            order = tab.order,
            createdAt = tab.createdAt,
            lastAccessed = tab.lastAccessed,
            fileType = tab.fileType,
            fileName = tab.fileName,
            lastPdfPage = tab.lastPdfPage,
            pdfScrollOffset = tab.pdfScrollOffset,
            resourceId = tab.resourceId,
            scrollNormalizedAnchor = tab.scrollRestoreData?.normalizedAnchor,
            scrollOccurrenceIndex = tab.scrollRestoreData?.occurrenceIndex ?: 0,
            scrollRatio = tab.scrollRestoreData?.scrollRatio ?: 0.0,
            faviconBytes = tab.faviconBytes
        )
    }

    /**
     * Convert list of entities to domain models
     */
    fun toDomainList(entities: List<SavedTabEntity>): List<SavedTab> {
        return entities.map { toDomain(it) }
    }
}
