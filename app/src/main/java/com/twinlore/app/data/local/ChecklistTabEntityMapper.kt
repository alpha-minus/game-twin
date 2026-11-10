package com.twinlore.app.data.local

import com.twinlore.app.domain.ChecklistTab

object ChecklistTabEntityMapper {
    fun toDomain(entity: ChecklistTabEntity): ChecklistTab = ChecklistTab(
        id = entity.id,
        vaultId = entity.vaultId,
        name = entity.name,
        color = entity.color,
        sortOrder = entity.sortOrder,
        createdAt = entity.createdAt,
        updatedAt = entity.updatedAt
    )

    fun toEntity(domain: ChecklistTab): ChecklistTabEntity = ChecklistTabEntity(
        id = domain.id,
        vaultId = domain.vaultId,
        name = domain.name,
        color = domain.color,
        sortOrder = domain.sortOrder,
        createdAt = domain.createdAt,
        updatedAt = domain.updatedAt
    )

    fun toDomainList(entities: List<ChecklistTabEntity>): List<ChecklistTab> =
        entities.map { toDomain(it) }
}
