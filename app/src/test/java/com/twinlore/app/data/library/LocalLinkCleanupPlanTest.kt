package com.twinlore.app.data.library

import com.twinlore.app.data.local.ResourceFileEntity
import com.twinlore.app.domain.ResourceSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-helper tests for the duplicate-registration fixes in LocalMediaBinder:
 *  - [isInsideGroupFolder]: page-group ownership detection (Fix B1)
 *  - [findLogicalDuplicate]: (filename lowercase, size) dedupe gate (Fix B2)
 *  - [planLocalLinkCleanup]: one-time cleanup classifier (Fix D)
 *
 * Path shapes mirror the real device DB from the bug report: SAF document
 * URIs under `primary:GameTwin/Lic/<vault dir variant>/…`.
 */
class LocalLinkCleanupPlanTest {

    // ── real-shape path fixtures ─────────────────────────────────────────────

    private val safPrefix =
        "content://com.android.externalstorage.documents/tree/primary%3AGameTwin/document/primary%3AGameTwin%2FLic"

    private fun safPath(vararg segments: String): String =
        safPrefix + segments.joinToString(separator = "") { "%2F" + it.replace(" ", "%20") }

    private fun row(
        id: String,
        path: String?,
        filename: String,
        size: Long = 100L,
        source: String = ResourceSource.LOCAL_LINK.raw,
        kind: String = "guide",
    ) = ResourceFileEntity(
        id = id, vaultId = "v1", kind = kind, filename = filename,
        sizeBytes = size, homeRelativePath = null, absolutePath = path,
        mimeType = null, source = source, lastSeenAt = 0, createdAt = 0,
    )

    // ── isInsideGroupFolder ──────────────────────────────────────────────────

    @Test
    fun groupFolder_matches_inside_encoded_saf_uri() {
        val path = safPath("Super Metroid", "Super Metroid Walkthrough", "page-2.mht")
        assertTrue(isInsideGroupFolder(path, listOf("Super Metroid Walkthrough")))
    }

    @Test
    fun groupFolder_name_as_file_prefix_is_not_a_segment_match() {
        // The folder name appears as a PREFIX of a longer dir segment — no match.
        val path = "super metroid (6)/Group Name Extra/page-2.mht"
        assertFalse(isInsideGroupFolder(path, listOf("Group Name")))
        // And as a filename prefix — also no match.
        val path2 = "super metroid (6)/Group Name page-2.mht"
        assertFalse(isInsideGroupFolder(path2, listOf("Group Name")))
    }

    @Test
    fun groupFolder_matches_plain_relative_path() {
        assertTrue(isInsideGroupFolder("super metroid (6)/Group Name/page-2.mht", listOf("Group Name")))
    }

    @Test
    fun groupFolder_match_is_case_insensitive() {
        assertTrue(isInsideGroupFolder("vault/group name/page-1.mht", listOf("Group Name")))
    }

    @Test
    fun groupFolder_as_last_segment_is_the_filename_not_a_dir() {
        assertFalse(isInsideGroupFolder("vault/Group Name", listOf("Group Name")))
    }

    @Test
    fun null_or_empty_path_is_never_inside_a_group_folder() {
        assertFalse(isInsideGroupFolder(null, listOf("Group Name")))
        assertFalse(isInsideGroupFolder("", listOf("Group Name")))
    }

    @Test
    fun empty_group_folders_never_match() {
        assertFalse(isInsideGroupFolder("vault/Group Name/page-1.mht", emptyList()))
    }

    // ── findLogicalDuplicate ─────────────────────────────────────────────────

    @Test
    fun same_name_and_size_blocks_regardless_of_source_and_path() {
        val existing = listOf(
            row("rf-bs", "/saved/Super Metroid - GameFAQs.mht", "Super Metroid - GameFAQs.mht",
                size = 5000L, source = ResourceSource.BROWSER_SAVE.raw),
        )
        val dup = findLogicalDuplicate(existing, "Super Metroid - GameFAQs.mht", 5000L)
        assertNotNull(dup)
        assertEquals("rf-bs", dup!!.id)
    }

    @Test
    fun filename_match_is_case_insensitive() {
        val existing = listOf(row("rf-1", "/a/Guide.mht", "Guide.mht", size = 7L))
        assertNotNull(findLogicalDuplicate(existing, "guide.mht", 7L))
    }

    @Test
    fun different_size_does_not_block() {
        val existing = listOf(row("rf-1", "/a/guide.mht", "guide.mht", size = 5000L))
        assertNull(findLogicalDuplicate(existing, "guide.mht", 5001L))
    }

    @Test
    fun different_name_does_not_block() {
        val existing = listOf(row("rf-1", "/a/guide.mht", "guide.mht", size = 5000L))
        assertNull(findLogicalDuplicate(existing, "other.mht", 5000L))
    }

    // ── planLocalLinkCleanup ─────────────────────────────────────────────────

    @Test
    fun rule1_deletes_rows_under_dot_prefixed_dirs_in_encoded_saf_uris() {
        val rows = listOf(
            row("rf-st", safPath(".stversions", "super metroid (2)", "guide.mht"), "guide.mht"),
            row("rf-ok", safPath("Super Metroid", "guide2.mht"), "guide2.mht"),
        )
        val plan = planLocalLinkCleanup(rows, emptyList())
        assertEquals(listOf("rf-st"), plan.syncArtifactIds)
        assertTrue(plan.groupOwnedIds.isEmpty())
        assertTrue(plan.duplicateIds.isEmpty())
    }

    @Test
    fun rule1_deletes_rows_under_dot_prefixed_dirs_in_plain_paths() {
        val rows = listOf(
            row("rf-st", "/storage/emulated/0/GameTwin/Lic/.stversions/super metroid (2)/guide.mht", "guide.mht"),
            row("rf-tr", "/storage/emulated/0/GameTwin/Lic/Super Metroid/.trashed-123-map.png", ".trashed-123-map.png", kind = "artwork"),
            row("rf-ok", "/storage/emulated/0/GameTwin/Lic/Super Metroid/guide2.mht", "guide2.mht"),
        )
        val plan = planLocalLinkCleanup(rows, emptyList())
        assertEquals(setOf("rf-st", "rf-tr"), plan.syncArtifactIds.toSet())
    }

    @Test
    fun rule2_deletes_rows_inside_group_folders_across_vault_dir_variants() {
        // The real bug: 21 page-N tabs x2 — pages of a saved group bound as
        // standalone guides, found via two vault-dir variants.
        val rows = listOf(
            row("rf-p2a", safPath("Super Metroid", "Super Metroid Walkthrough", "page-2.mht"), "page-2.mht", size = 11L),
            row("rf-p2b", safPath("super metroid (3)", "Super Metroid Walkthrough", "page-2.mht"), "page-2.mht", size = 11L),
            row("rf-ok", safPath("Super Metroid", "standalone.mht"), "standalone.mht"),
        )
        val plan = planLocalLinkCleanup(rows, listOf("Super Metroid Walkthrough"))
        assertEquals(setOf("rf-p2a", "rf-p2b"), plan.groupOwnedIds.toSet())
        assertTrue(plan.duplicateIds.isEmpty())
        assertFalse(plan.allIds.contains("rf-ok"))
    }

    @Test
    fun rule3_nonlocal_twin_kills_all_local_link_copies() {
        val rows = listOf(
            row("rf-bs", "/data/vaults/v1/guides/Super Metroid - GameFAQs.mht",
                "Super Metroid - GameFAQs.mht", size = 5000L, source = ResourceSource.BROWSER_SAVE.raw),
            row("rf-ll1", safPath("Super Metroid", "Super Metroid - GameFAQs.mht"), "Super Metroid - GameFAQs.mht", size = 5000L),
            row("rf-ll2", safPath("super metroid (6)", "Super Metroid - GameFAQs.mht"), "Super Metroid - GameFAQs.mht", size = 5000L),
        )
        val plan = planLocalLinkCleanup(rows, emptyList())
        assertEquals(setOf("rf-ll1", "rf-ll2"), plan.duplicateIds.toSet())
        // Slots pointing at deleted copies should be repointed at the surviving twin.
        assertEquals("rf-bs", plan.replacements["rf-ll1"])
        assertEquals("rf-bs", plan.replacements["rf-ll2"])
        // The non-local row itself is never deleted.
        assertFalse(plan.allIds.contains("rf-bs"))
    }

    @Test
    fun rule3_keep_one_prefers_shortest_decoded_path() {
        val base = safPath("Super Metroid", "guide.mht")
        val var3 = safPath("super metroid (3)", "guide.mht")
        val var6 = safPath("super metroid (6)", "guide.mht")
        val rows = listOf(
            row("rf-3", var3, "guide.mht", size = 42L),
            row("rf-base", base, "guide.mht", size = 42L),
            row("rf-6", var6, "guide.mht", size = 42L),
        )
        val plan = planLocalLinkCleanup(rows, emptyList())
        assertEquals(setOf("rf-3", "rf-6"), plan.duplicateIds.toSet())
        assertEquals("rf-base", plan.replacements["rf-3"])
        assertEquals("rf-base", plan.replacements["rf-6"])
    }

    @Test
    fun rule3_different_sizes_are_not_duplicates() {
        val rows = listOf(
            row("rf-1", safPath("Super Metroid", "guide.mht"), "guide.mht", size = 42L),
            row("rf-2", safPath("super metroid (3)", "guide.mht"), "guide.mht", size = 43L),
        )
        val plan = planLocalLinkCleanup(rows, emptyList())
        assertTrue(plan.allIds.isEmpty())
    }

    @Test
    fun clean_vault_yields_empty_plan() {
        val rows = listOf(
            row("rf-1", safPath("Super Metroid", "guide.mht"), "guide.mht"),
            row("rf-2", "/data/vaults/v1/art/cover.png", "cover.png",
                size = 9L, source = ResourceSource.AUTO_SCRAPE.raw, kind = "artwork"),
        )
        val plan = planLocalLinkCleanup(rows, listOf("Some Group"))
        assertTrue(plan.isEmpty)
    }

    @Test
    fun cleanup_is_idempotent_replanning_survivors_is_empty() {
        val rows = listOf(
            row("rf-st", safPath(".stversions", "super metroid (2)", "guide.mht"), "guide.mht", size = 42L),
            row("rf-p2", safPath("Super Metroid", "Walkthrough", "page-2.mht"), "page-2.mht", size = 11L),
            row("rf-base", safPath("Super Metroid", "guide.mht"), "guide.mht", size = 42L),
            row("rf-6", safPath("super metroid (6)", "guide.mht"), "guide.mht", size = 42L),
        )
        val groupFolders = listOf("Walkthrough")
        val plan = planLocalLinkCleanup(rows, groupFolders)
        assertEquals(setOf("rf-st", "rf-p2", "rf-6"), plan.allIds.toSet())

        val survivors = rows.filter { it.id !in plan.allIds.toSet() }
        val replan = planLocalLinkCleanup(survivors, groupFolders)
        assertTrue(replan.isEmpty)
    }

    @Test
    fun rules_never_touch_non_local_link_rows() {
        val rows = listOf(
            row("rf-bs", "/data/vaults/v1/.hidden/guide.mht", "guide.mht",
                source = ResourceSource.BROWSER_SAVE.raw),
            row("rf-ua", safPath("Super Metroid", "Walkthrough", "page-2.mht"), "page-2.mht",
                size = 11L, source = ResourceSource.USER_ADD.raw),
        )
        val plan = planLocalLinkCleanup(rows, listOf("Walkthrough"))
        assertTrue(plan.isEmpty)
    }
}
