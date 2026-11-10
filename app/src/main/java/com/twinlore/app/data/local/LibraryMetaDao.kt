package com.twinlore.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface LibraryMetaDao {
    @Query("SELECT * FROM library_meta WHERE id = :id LIMIT 1")
    suspend fun getMeta(id: Int = LibraryMetaEntity.SINGLETON_ID): LibraryMetaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMeta(meta: LibraryMetaEntity)
}
