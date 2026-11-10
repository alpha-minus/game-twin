package com.twinlore.app.data.local

import com.twinlore.app.domain.Vault

/**
 * Mapper functions to convert between VaultEntity and Vault domain model
 */
object VaultEntityMapper {
    /**
     * Convert VaultEntity to Vault domain model
     */
    fun toDomain(entity: VaultEntity): Vault {
        return Vault(
            id = entity.id,
            name = entity.name,
            romPath = entity.romPath,
            romFilename = entity.romFilename,
            artworkVibrantArgb = entity.artworkVibrantArgb,
            systemeid = entity.systemeid,
            gameId = entity.gameId,
            source = entity.source,
            raGameId = entity.raGameId,
            steamAppId = entity.steamAppId,
            artworkResourceId = entity.artworkResourceId,
            mapResourceId = entity.mapResourceId,
            guideResourceId = entity.guideResourceId,
            launchIntentJson = entity.launchIntentJson,
            lastOpened = entity.lastOpened,
            createdAt = entity.createdAt,
            folderName = entity.folderName,
        )
    }

    /**
     * Convert Vault domain model to VaultEntity
     */
    fun toEntity(vault: Vault): VaultEntity {
        return VaultEntity(
            id = vault.id,
            name = vault.name,
            romPath = vault.romPath,
            romFilename = vault.romFilename,
            artworkVibrantArgb = vault.artworkVibrantArgb,
            systemeid = vault.systemeid,
            gameId = vault.gameId,
            source = vault.source,
            raGameId = vault.raGameId,
            steamAppId = vault.steamAppId,
            artworkResourceId = vault.artworkResourceId,
            mapResourceId = vault.mapResourceId,
            guideResourceId = vault.guideResourceId,
            launchIntentJson = vault.launchIntentJson,
            lastOpened = vault.lastOpened,
            createdAt = vault.createdAt,
            folderName = vault.folderName,
        )
    }
    
    /**
     * Convert list of entities to domain models
     */
    fun toDomainList(entities: List<VaultEntity>): List<Vault> {
        return entities.map { toDomain(it) }
    }
}


