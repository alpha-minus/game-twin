package com.twinlore.app.data

import com.twinlore.app.data.local.SavedMapDao
import com.twinlore.app.data.local.SavedMapEntityMapper
import com.twinlore.app.domain.SavedMap
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SavedMapRepository @Inject constructor(
    private val savedMapDao: SavedMapDao
) {
    fun getMapsByVaultId(vaultId: String): Flow<List<SavedMap>> =
        savedMapDao.getMapsByVaultId(vaultId).map { SavedMapEntityMapper.toDomainList(it) }

    suspend fun getMapsByVaultIdOnce(vaultId: String): List<SavedMap> =
        SavedMapEntityMapper.toDomainList(savedMapDao.getMapsByVaultIdOnce(vaultId))

    suspend fun getMapById(mapId: String): SavedMap? =
        savedMapDao.getMapById(mapId)?.let { SavedMapEntityMapper.toDomain(it) }

    suspend fun getMapCount(vaultId: String): Int = savedMapDao.getMapCount(vaultId)

    suspend fun addMap(vaultId: String, title: String, resourceId: String?): SavedMap {
        val count = savedMapDao.getMapCount(vaultId)
        val map = SavedMap(
            id = UUID.randomUUID().toString(),
            vaultId = vaultId,
            title = title,
            resourceId = resourceId,
            order = count,
            createdAt = Date(),
            lastAccessed = Date()
        )
        savedMapDao.insertMap(SavedMapEntityMapper.toEntity(map))
        return map
    }

    suspend fun deleteMap(mapId: String) = savedMapDao.deleteMap(mapId)

    suspend fun deleteAllMapsForVault(vaultId: String) = savedMapDao.deleteAllMapsForVault(vaultId)

    suspend fun updateLastAccessed(mapId: String) = savedMapDao.updateLastAccessed(mapId, Date())

    suspend fun renameMap(mapId: String, newTitle: String) {
        val entity = savedMapDao.getMapById(mapId) ?: return
        savedMapDao.updateMap(entity.copy(title = newTitle))
    }
}
