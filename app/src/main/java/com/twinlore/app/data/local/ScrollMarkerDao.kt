package com.twinlore.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ScrollMarkerDao {

    @Query("SELECT * FROM scroll_markers WHERE saved_tab_id = :savedTabId ORDER BY marker_order ASC")
    fun getForTab(savedTabId: String): Flow<List<ScrollMarkerEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ScrollMarkerEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<ScrollMarkerEntity>)

    @Query("DELETE FROM scroll_markers WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM scroll_markers WHERE saved_tab_id = :savedTabId")
    suspend fun deleteForTab(savedTabId: String)

    @Query("SELECT MAX(marker_order) FROM scroll_markers WHERE saved_tab_id = :savedTabId")
    suspend fun getMaxOrderForTab(savedTabId: String): Int?

    /**
     * Deletes all markers pointing at one page-group entry. Called from
     * PageGroupRepository.removeEntry — the entry's own FK cascade only
     * covers anchors; marker.page_group_entry_id has no FK (single-page
     * bookmarks need it null) so cleanup is explicit.
     */
    @Query("DELETE FROM scroll_markers WHERE page_group_entry_id = :entryId")
    suspend fun deleteForGroupEntry(entryId: String)
}
