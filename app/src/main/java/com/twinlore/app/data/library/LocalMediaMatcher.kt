package com.twinlore.app.data.library

import com.twinlore.app.data.local.MediaSearchDirDao
import com.twinlore.app.data.local.MediaSearchIndexDao
import com.twinlore.app.data.local.MediaSearchIndexEntity
import com.twinlore.app.data.scraping.GameNameSanitizer
import com.twinlore.app.domain.LocalMediaMatch
import com.twinlore.app.domain.ResourceKind
import com.twinlore.app.domain.Vault
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Strict-equality matcher: a row matches a vault iff
 *  - the file's normalized basename equals the vault's normalized name, OR
 *  - any folder in the file's path, when normalized, equals the vault's name
 *    (recursive; files anywhere under the matched folder count).
 *
 * Fuzzy/numeric-guard logic from the previous implementation is dropped:
 * equality already excludes "Shinobi 2" when the vault is "Shinobi".
 */
@Singleton
class LocalMediaMatcher @Inject constructor(
    private val indexDao: MediaSearchIndexDao,
    private val dirDao: MediaSearchDirDao,
    private val sanitizer: GameNameSanitizer,
) {

    suspend fun findForVault(vault: Vault): List<LocalMediaMatch> {
        val needle = sanitizer.normalize(vault.displayName)
        if (needle.isBlank()) return emptyList()

        // 1. File-level equality.
        val byBasename = indexDao.findByNormalizedBasename(needle)

        // 2. Folder-level equality. Find dirs whose normalized last segment
        //    equals the needle; pull every file under each.
        val matchingDirs = dirDao.findByNormalizedName(needle)
        val byFolder = matchingDirs.flatMap { dir ->
            indexDao.findUnderDir(dir.searchPathId, dir.relativeDir)
        }

        // Dedupe (a file matched by both basename and folder appears twice).
        val seen = mutableSetOf<String>()
        return (byBasename + byFolder)
            .filter { seen.add(it.id) }
            .map { it.toMatch() }
    }

    /**
     * Inverse of [findForVault]: removes every media-index row this matcher would
     * re-bind for a vault of [displayName] — basename matches, plus every entry
     * under any folder whose name matches — across ALL search paths, and the
     * matching dir-mtime rows so a later delta-scan re-walks them fresh.
     *
     * DB-only: touches no files on disk. Called on vault delete so a same-name
     * recreate can't resurrect stale bindings. (The prior cleanup only cleared the
     * AUTO_HOME path under the exact folder name, so entries indexed under any other
     * search path survived and re-matched.) Returns the number of index rows removed.
     *
     * If two live vaults normalize to the same name, this unwinds both — but that's
     * benign and self-healing: the post-delete delta-scan re-walks the dirs (the
     * dir-mtime rows are removed, forcing a fresh walk) and [findForVault] re-binds the
     * surviving vault on next access.
     */
    suspend fun removeBindingsForVaultName(displayName: String): Int {
        val needle = sanitizer.normalize(displayName)
        if (needle.isBlank()) return 0
        val ids = mutableSetOf<String>()
        indexDao.findByNormalizedBasename(needle).forEach { ids.add(it.id) }
        val dirs = dirDao.findByNormalizedName(needle)
        dirs.forEach { dir ->
            indexDao.findUnderDir(dir.searchPathId, dir.relativeDir).forEach { ids.add(it.id) }
        }
        ids.forEach { indexDao.deleteById(it) }
        dirs.forEach { dirDao.deleteUnderDir(it.searchPathId, it.relativeDir) }
        return ids.size
    }

    private fun MediaSearchIndexEntity.toMatch(): LocalMediaMatch {
        val kind = if (extLower in GUIDE_EXTS) ResourceKind.GUIDE else ResourceKind.ARTWORK
        val tag = if (kind == ResourceKind.ARTWORK) classifyTag() else "guide"
        return LocalMediaMatch(
            resourceKind = kind,
            artworkTag = tag,
            absolutePath = absolutePath,
            contentUriString = if (absolutePath == null) relativePath else null,
            filename = filename,
            sizeBytes = sizeBytes,
            extLower = extLower,
            searchPathId = searchPathId,
            relativePath = relativePath,
        )
    }

    private fun MediaSearchIndexEntity.classifyTag(): String {
        val pathTokens = normalizedPathTokens.split(' ').filter { it.isNotBlank() }.toSet()
        val basenameTokens = normalizedBasename.split(' ').filter { it.isNotBlank() }.toSet()
        for ((triggers, tag) in TAG_RULES) {
            if (pathTokens.any { it in triggers } || basenameTokens.any { it in triggers }) {
                return tag
            }
        }
        return "other"
    }

    companion object {
        private val GUIDE_EXTS = setOf("pdf", "txt", "mht", "mhtml", "html")

        // Tag classification: maps folder/filename tokens to a stable tag string.
        private val TAG_RULES: List<Pair<Set<String>, String>> = listOf(
            setOf("cover", "covers", "grid", "grids") to "cover",
            setOf("3dbox", "3dboxes") to "3dbox",
            setOf("fanart", "background", "backdrop", "hero", "heroes") to "fanart",
            setOf("miximage", "miximages", "mix") to "miximage",
            setOf("logo", "logos", "marquee", "marquees") to "marquee",
            setOf("screenshot", "screenshots", "titlescreen", "titlescreens",
                  "physicalmedia", "backcover", "backcovers") to "screenshot",
        )
    }
}
