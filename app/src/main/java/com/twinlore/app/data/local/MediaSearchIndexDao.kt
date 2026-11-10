package com.twinlore.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface MediaSearchIndexDao {

    @Query("SELECT COUNT(*) FROM media_search_index")
    suspend fun count(): Int

    @Query("SELECT * FROM media_search_index WHERE search_path_id = :spId")
    suspend fun getForPath(spId: String): List<MediaSearchIndexEntity>

    @Query("""
        SELECT * FROM media_search_index
        WHERE search_path_id = :spId
          AND (
              relative_path = :relDir
              OR (relative_path LIKE :relDir || '/%' AND relative_path NOT LIKE :relDir || '/%/%')
          )
    """)
    suspend fun getForDir(spId: String, relDir: String): List<MediaSearchIndexEntity>

    @Query("SELECT * FROM media_search_index WHERE search_path_id = :spId AND relative_path NOT LIKE '%/%'")
    suspend fun getFilesInRoot(spId: String): List<MediaSearchIndexEntity>

    @Query("SELECT * FROM media_search_index WHERE normalized_basename = :needle")
    suspend fun findByNormalizedBasename(needle: String): List<MediaSearchIndexEntity>

    @Query("""
        SELECT * FROM media_search_index
        WHERE search_path_id = :spId
          AND (relative_path = :dir OR relative_path LIKE :dir || '/%')
    """)
    suspend fun findUnderDir(spId: String, dir: String): List<MediaSearchIndexEntity>

    @Query("SELECT * FROM media_search_index WHERE filename = :filename AND size_bytes = :size LIMIT 1")
    suspend fun findByFilenameAndSize(filename: String, size: Long): MediaSearchIndexEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<MediaSearchIndexEntity>)

    @Query("DELETE FROM media_search_index WHERE search_path_id = :spId")
    suspend fun deleteForPath(spId: String)

    @Query("DELETE FROM media_search_index WHERE id = :id")
    suspend fun deleteById(id: String)

    /**
     * Remove every index row whose [relative_path] equals [dir] or lives inside
     * the [dir] subtree. Used when a vault folder is deleted from disk so the
     * index doesn't keep handing phantom paths to LocalMediaMatcher.
     */
    @Query("""
        DELETE FROM media_search_index
        WHERE search_path_id = :spId
          AND (relative_path = :dir OR relative_path LIKE :dir || '/%')
    """)
    suspend fun deleteUnderDir(spId: String, dir: String)
}
