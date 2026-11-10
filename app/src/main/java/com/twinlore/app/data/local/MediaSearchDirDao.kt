package com.twinlore.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface MediaSearchDirDao {

    @Query("SELECT mtime FROM media_search_dirs WHERE search_path_id = :spId AND relative_dir = :relDir LIMIT 1")
    suspend fun getMtime(spId: String, relDir: String): Long?

    @Query("SELECT * FROM media_search_dirs WHERE search_path_id = :spId AND relative_dir LIKE :parent || '/%' AND relative_dir NOT LIKE :parent || '/%/%'")
    suspend fun getChildren(spId: String, parent: String): List<MediaSearchDirEntity>

    @Query("SELECT * FROM media_search_dirs WHERE search_path_id = :spId AND relative_dir != '' AND relative_dir NOT LIKE '%/%'")
    suspend fun getTopLevelChildren(spId: String): List<MediaSearchDirEntity>

    @Query("SELECT * FROM media_search_dirs WHERE normalized_name = :needle")
    suspend fun findByNormalizedName(needle: String): List<MediaSearchDirEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: MediaSearchDirEntity)

    /** One transaction for a whole walk's directories; see MediaIndexer.rebuildOne. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<MediaSearchDirEntity>)

    @Query("DELETE FROM media_search_dirs WHERE search_path_id = :spId")
    suspend fun deleteForPath(spId: String)

    /**
     * Remove dir-mtime rows for [dir] and every descendant. Paired with
     * MediaSearchIndexDao.deleteUnderDir for vault-folder cleanup.
     */
    @Query("""
        DELETE FROM media_search_dirs
        WHERE search_path_id = :spId
          AND (relative_dir = :dir OR relative_dir LIKE :dir || '/%')
    """)
    suspend fun deleteUnderDir(spId: String, dir: String)
}
