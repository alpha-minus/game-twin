package com.twinlore.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PageBookmarkDao {

    @Query("SELECT * FROM page_bookmarks WHERE saved_tab_id = :savedTabId ORDER BY created_at ASC")
    fun getForTab(savedTabId: String): Flow<List<PageBookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: PageBookmarkEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<PageBookmarkEntity>)

    @Query("DELETE FROM page_bookmarks WHERE id = :id")
    suspend fun deleteById(id: String)
}
