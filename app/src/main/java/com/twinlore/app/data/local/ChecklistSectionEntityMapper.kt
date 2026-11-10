package com.twinlore.app.data.local

import com.twinlore.app.domain.ChecklistSection

object ChecklistSectionEntityMapper {
    fun toDomain(entity: ChecklistSectionEntity): ChecklistSection = ChecklistSection(
        id = entity.id,
        vaultId = entity.vaultId,
        tabId = entity.tabId,
        name = entity.name,
        expanded = entity.expanded,
        sortOrder = entity.sortOrder,
        createdAt = entity.createdAt,
        updatedAt = entity.updatedAt
    )

    fun toEntity(domain: ChecklistSection): ChecklistSectionEntity = ChecklistSectionEntity(
        id = domain.id,
        vaultId = domain.vaultId,
        tabId = domain.tabId,
        name = domain.name,
        expanded = domain.expanded,
        sortOrder = domain.sortOrder,
        createdAt = domain.createdAt,
        updatedAt = domain.updatedAt
    )

    fun toDomainList(entities: List<ChecklistSectionEntity>): List<ChecklistSection> =
        entities.map { toDomain(it) }
}
