package com.twinlore.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface MediaSearchPathDao {

    @Query("SELECT * FROM media_search_paths ORDER BY created_at ASC")
    suspend fun getAll(): List<MediaSearchPathEntity>

    @Query("SELECT * FROM media_search_paths WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): MediaSearchPathEntity?

    @Query("SELECT * FROM media_search_paths WHERE origin = :origin LIMIT 1")
    suspend fun findByOrigin(origin: String): MediaSearchPathEntity?

    @Query("SELECT * FROM media_search_paths WHERE uri = :uri LIMIT 1")
    suspend fun findByUri(uri: String): MediaSearchPathEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: MediaSearchPathEntity)

    @Update
    suspend fun update(entity: MediaSearchPathEntity)

    @Query("DELETE FROM media_search_paths WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE media_search_paths SET last_scanned_at = :ts WHERE id = :id")
    suspend fun touchScan(id: String, ts: Long)

    /**
     * Returns ids of resource_files rows whose source is LOCAL_LINK and whose
     * (absolute_path, size_bytes) fingerprint matches an entry in this search
     * path's media_search_index. Used by cleanupLocalLinksFor to wipe rows that
     * came from a path being removed/disabled.
     *
     * The literal matches `ResourceSource.LOCAL_LINK.raw` ("local_link"), the
     * value the binder writes to the column.
     */
    @Query("""
        SELECT rf.id FROM resource_files rf
        WHERE rf.source = 'local_link'
          AND EXISTS (
            SELECT 1 FROM media_search_index msi
            WHERE msi.search_path_id = :spId
              AND msi.absolute_path = rf.absolute_path
              AND msi.size_bytes    = rf.size_bytes
          )
    """)
    suspend fun getLocalLinkResourceIdsFor(spId: String): List<String>

    /**
     * Counts distinct vaults that have at least one LOCAL_LINK resource_files
     * row from this search path bound to a slot or referenced by a saved tab/map.
     * Drives the affected-vault count in the remove/disable confirmation dialog.
     */
    @Query("""
        SELECT COUNT(DISTINCT rf.vault_id) FROM resource_files rf
        WHERE rf.source = 'local_link'
          AND EXISTS (
            SELECT 1 FROM media_search_index msi
            WHERE msi.search_path_id = :spId
              AND msi.absolute_path = rf.absolute_path
              AND msi.size_bytes    = rf.size_bytes
          )
          AND (
            EXISTS (SELECT 1 FROM vaults v WHERE v.artwork_resource_id = rf.id) OR
            EXISTS (SELECT 1 FROM vaults v WHERE v.guide_resource_id   = rf.id) OR
            EXISTS (SELECT 1 FROM vaults v WHERE v.map_resource_id     = rf.id) OR
            EXISTS (SELECT 1 FROM saved_tabs st WHERE st.resource_id = rf.id) OR
            EXISTS (SELECT 1 FROM saved_maps sm WHERE sm.resource_id = rf.id)
          )
    """)
    suspend fun countAffectedVaults(spId: String): Int
}
