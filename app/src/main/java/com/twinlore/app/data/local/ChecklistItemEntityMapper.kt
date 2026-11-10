package com.twinlore.app.data.local

import com.twinlore.app.domain.ChecklistItem

object ChecklistItemEntityMapper {
    fun toDomain(entity: ChecklistItemEntity): ChecklistItem = ChecklistItem(
        id = entity.id,
        vaultId = entity.vaultId,
        tagId = entity.tagId,
        sectionId = entity.sectionId,
        title = entity.title,
        completed = entity.completed,
        sortOrder = entity.sortOrder,
        createdAt = entity.createdAt,
        updatedAt = entity.updatedAt
    )

    fun toEntity(domain: ChecklistItem): ChecklistItemEntity = ChecklistItemEntity(
        id = domain.id,
        vaultId = domain.vaultId,
        tagId = domain.tagId,
        sectionId = domain.sectionId,
        title = domain.title,
        completed = domain.completed,
        sortOrder = domain.sortOrder,
        createdAt = domain.createdAt,
        updatedAt = domain.updatedAt
    )

    fun toDomainList(entities: List<ChecklistItemEntity>): List<ChecklistItem> =
        entities.map { toDomain(it) }
}
