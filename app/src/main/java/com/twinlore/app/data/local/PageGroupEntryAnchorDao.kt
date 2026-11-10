package com.twinlore.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PageGroupEntryAnchorDao {

    @Query("SELECT * FROM page_group_entry_anchors WHERE page_group_entry_id = :entryId ORDER BY position ASC")
    suspend fun getForEntry(entryId: String): List<PageGroupEntryAnchorEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(anchors: List<PageGroupEntryAnchorEntity>)
}
