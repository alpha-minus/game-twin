package com.twinlore.app.data.library

import com.twinlore.app.data.local.ResourceFileDao
import com.twinlore.app.data.local.ResourceFileEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * Moves a [ResourceFileEntity]'s file from its `absolutePath` (legacy
 * location) into the configured home folder. After a successful copy the
 * row's `homeRelativePath` is set, `absolutePath` is cleared, and the
 * legacy source file is deleted.
 *
 * Both `file://` and `content://` home folders are supported via
 * [LibraryFolderManager.copyFileIntoTree].
 */
class FileRelocator @Inject constructor(
    private val resourceDao: ResourceFileDao,
    private val folder: LibraryFolderManager
) {

    /**
     * Relocate one resource. Returns true if the file lands in the new home
     * AND the DB row is updated; false if the source is missing, the home
     * folder is unconfigured, or the copy fails.
     *
     * - [safeVaultName] is the vault's name pre-sanitized via
     *   [LibraryFolderManager.sanitizeForPath].
     * - [subdir] is the per-file subdirectory beneath the vault dir, e.g.
     *   `"art"`, `"map"`, `"guide/<bookmark-id>"`.
     */
    suspend fun relocate(
        row: ResourceFileEntity,
        safeVaultName: String,
        subdir: String
    ): Boolean = withContext(Dispatchers.IO) {
        val src = row.absolutePath?.let(::File) ?: return@withContext false
        if (!src.isFile) return@withContext false

        val fullSubdir = "$safeVaultName/$subdir"
        folder.copyFileIntoTree(src, fullSubdir, src.name) ?: return@withContext false

        val newRelative = "$fullSubdir/${src.name}"
        resourceDao.update(
            row.copy(
                homeRelativePath = newRelative,
                absolutePath = null
            )
        )
        src.delete()
        true
    }
}
