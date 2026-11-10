package com.twinlore.app.data.local

import com.twinlore.app.domain.SavedMap

object SavedMapEntityMapper {
    fun toDomain(entity: SavedMapEntity): SavedMap = SavedMap(
        id = entity.id,
        vaultId = entity.vaultId,
        title = entity.title,
        resourceId = entity.resourceId,
        order = entity.order,
        createdAt = entity.createdAt,
        lastAccessed = entity.lastAccessed
    )

    fun toEntity(domain: SavedMap): SavedMapEntity = SavedMapEntity(
        id = domain.id,
        vaultId = domain.vaultId,
        title = domain.title,
        resourceId = domain.resourceId,
        order = domain.order,
        createdAt = domain.createdAt,
        lastAccessed = domain.lastAccessed
    )

    fun toDomainList(entities: List<SavedMapEntity>): List<SavedMap> = entities.map { toDomain(it) }
}
