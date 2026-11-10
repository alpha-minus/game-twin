package com.twinlore.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteAppDao {
    @Query("SELECT * FROM favorite_apps ORDER BY position ASC, addedAt ASC")
    fun getAll(): Flow<List<FavoriteAppEntity>>

    @Query("SELECT packageName FROM favorite_apps")
    fun getAllPackageNames(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: FavoriteAppEntity)

    @Query("SELECT COALESCE(MAX(position) + 1, 0) FROM favorite_apps")
    suspend fun nextPosition(): Int

    /** Pin at the end of the strip. All pin paths go through here so order stays dense. */
    @Transaction
    suspend fun pin(packageName: String) {
        insert(FavoriteAppEntity(packageName, position = nextPosition()))
    }

    @Query("UPDATE favorite_apps SET position = :position WHERE packageName = :packageName")
    suspend fun updatePosition(packageName: String, position: Int)

    /** Commit a full reorder as one transaction so observers never see a half-applied order. */
    @Transaction
    suspend fun setOrder(orderedPackageNames: List<String>) {
        orderedPackageNames.forEachIndexed { index, packageName ->
            updatePosition(packageName, index)
        }
    }

    @Query("DELETE FROM favorite_apps WHERE packageName = :packageName")
    suspend fun deleteByPackageName(packageName: String)
}
