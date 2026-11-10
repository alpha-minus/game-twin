package com.twinlore.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PageGroupEntryDao {
    @Query("SELECT * FROM page_group_entries WHERE group_tab_id = :groupTabId ORDER BY position ASC")
    fun observeForGroup(groupTabId: String): Flow<List<PageGroupEntryEntity>>

    @Query("SELECT * FROM page_group_entries WHERE group_tab_id = :groupTabId ORDER BY position ASC")
    suspend fun getForGroup(groupTabId: String): List<PageGroupEntryEntity>

    @Query("SELECT * FROM page_group_entries WHERE id = :id")
    suspend fun getById(id: String): PageGroupEntryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<PageGroupEntryEntity>)

    @Query(
        """
        UPDATE page_group_entries
        SET scroll_anchor = :anchor,
            scroll_occurrence_index = :occurrence,
            scroll_ratio = :ratio
        WHERE id = :id
        """
    )
    suspend fun updateScroll(id: String, anchor: String?, occurrence: Int, ratio: Double)

    @Query("DELETE FROM page_group_entries WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM page_group_entries WHERE group_tab_id = :groupTabId")
    suspend fun deleteForGroup(groupTabId: String)

    @Query("UPDATE page_group_entries SET position = :position WHERE id = :id")
    suspend fun updatePosition(id: String, position: Int)

    /**
     * Returns the count of entries in the group whose `mht_resource_id`
     * matches the given resource id. Used during entry removal to decide
     * whether the underlying file is still referenced.
     *
     * References are scoped to this group only. The save coordinator creates one
     * `resource_files` row per saved page per group, so the same URL saved in
     * two different groups gets two distinct `resource_files` rows. If that
     * invariant ever changes, this query must become a cross-group count.
     */
    @Query("SELECT COUNT(*) FROM page_group_entries WHERE group_tab_id = :groupTabId AND mht_resource_id = :resourceId")
    suspend fun countReferences(groupTabId: String, resourceId: String): Int
}
