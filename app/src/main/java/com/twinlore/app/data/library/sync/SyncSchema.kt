package com.twinlore.app.data.library.sync

/**
 * The vault-scoped tables that ride with a vault during sync (fingerprint +
 * wholesale copy/delete). [where] is a SQL condition selecting one vault's
 * rows; `{p}` is replaced with a DB alias prefix ("" or "remote.") so the
 * same clause works for local deletes and ATTACH-based copies; the single `?`
 * binds the vaultId.
 *
 * Deliberately ABSENT:
 *  - browser_tabs   — vault-scoped but device-local by design;
 *                     preserved across wholesale replace by the engine.
 *  - ra_game_cache / steam_game_cache — background achievement sync mutates
 *                     them; in the fingerprint they'd cause false "updated"
 *                     diffs. Copied additively (INSERT OR REPLACE) on merge.
 *  - media_search_* / favorite_apps / library_meta — global.
 */
/** A table participating in vault sync, with a vault-scoping WHERE clause. */
data class SyncTable(val name: String, val where: String)

object SyncSchema {
    /**
     * Order matters: a table appears before any table that FKs into it, so a
     * consumer iterating in order inserts root-to-leaf (the engine also
     * defers FK enforcement, but don't rely on that when reordering).
     */
    val VAULT_SCOPED: List<SyncTable> = listOf(
        SyncTable("saved_tabs", "vaultId = ?"),
        SyncTable("saved_maps", "vaultId = ?"),
        SyncTable("checklist_tabs", "vaultId = ?"),
        SyncTable("checklist_sections", "vaultId = ?"),
        SyncTable("checklist_tags", "vaultId = ?"),
        SyncTable("checklist_items", "vaultId = ?"),
        SyncTable("resource_files", "vault_id = ?"),
        SyncTable(
            "map_markers",
            "resource_file_id IN (SELECT id FROM {p}resource_files WHERE vault_id = ?)"
        ),
        SyncTable(
            "page_bookmarks",
            "saved_tab_id IN (SELECT id FROM {p}saved_tabs WHERE vaultId = ?)"
        ),
        SyncTable(
            "scroll_markers",
            "saved_tab_id IN (SELECT id FROM {p}saved_tabs WHERE vaultId = ?)"
        ),
        SyncTable(
            "page_group_entries",
            "group_tab_id IN (SELECT id FROM {p}saved_tabs WHERE vaultId = ?)"
        ),
        SyncTable(
            "page_group_entry_anchors",
            "page_group_entry_id IN (SELECT id FROM {p}page_group_entries " +
                "WHERE group_tab_id IN (SELECT id FROM {p}saved_tabs WHERE vaultId = ?))"
        ),
    )

    /** Additive caches copied with INSERT OR REPLACE when their vault is copied. */
    val ADDITIVE_CACHES: List<SyncTable> = listOf(
        SyncTable("ra_game_cache", "vaultId = ?"),
        SyncTable("steam_game_cache", "vaultId = ?"),
    )

    /**
     * Columns excluded from fingerprints because they mutate without user intent
     * or are inherently device-local (so the same file on two devices would
     * otherwise hash differently and over-report the vault as "changed").
     *
     *  - vaults.lastOpened            — bumped every time the vault is opened.
     *  - resource_files.last_seen_at  — bumped on every scan that re-finds the file.
     *  - resource_files.absolute_path — the device's SAF tree URI / filesystem path;
     *                                   differs per device for the identical file.
     *  - resource_files.created_at    — when *this* device first scanned the file;
     *                                   differs per device for the identical file.
     */
    val VOLATILE_COLUMNS: Map<String, Set<String>> = mapOf(
        "vaults" to setOf("lastOpened"),
        "resource_files" to setOf("last_seen_at", "absolute_path", "created_at"),
    )

    fun whereFor(table: SyncTable, prefix: String): String =
        table.where.replace("{p}", prefix)
}
