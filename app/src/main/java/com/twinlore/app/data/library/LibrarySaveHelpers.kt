package com.twinlore.app.data.library

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.twinlore.app.data.local.ResourceFileDao
import com.twinlore.app.data.local.ResourceFileEntity
import com.twinlore.app.data.local.SavedTabDao
import com.twinlore.app.data.local.SavedTabEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Date
import java.util.UUID

/**
 * Shared filesystem + DB helpers for browser/library save flows.
 *
 * Both [BrowserSaveCoordinator] (single-page) and the page-group save
 * pipeline (multi-page) write a cache file into the user's home folder
 * and register a resource_files row (+ optionally a saved_tabs row for
 * guides). The mechanics are identical, so they live here to avoid drift.
 *
 * All filesystem I/O hops to [Dispatchers.IO]; callers can be on Main.
 */
internal object LibrarySaveHelpers {

    /**
     * Moves [cacheFile] into the vault folder under the home tree and registers
     * a [ResourceFileEntity]. When [kind] == "guide", also inserts a
     * [SavedTabEntity] so the chapter appears in the vault's saved-guides list.
     *
     * Returns [SaveResult.Success] with the new resource_files id (and
     * saved_tabs id for guides) or [SaveResult.Failed] on storage failure.
     */
    suspend fun moveIntoHomeAndAttach(
        context: Context,
        folder: LibraryFolderManager,
        resourceDao: ResourceFileDao,
        savedTabDao: SavedTabDao,
        cacheFile: File,
        suggestedFilename: String,
        vaultId: String,
        vaultName: String,
        kind: String,
        mimeType: String,
        webUrl: String? = null,
        forcedFileType: String? = null
    ): SaveResult = withContext(Dispatchers.IO) {
        val homeUri = folder.homeFolderUri()
            ?: return@withContext SaveResult.Failed(IllegalStateException("Storage folder is not set"))
        val outcome = when (homeUri.scheme) {
            "file" -> writeToFileHome(homeUri, vaultName, suggestedFilename, cacheFile)
            "content" -> writeToSafHome(context, folder, homeUri, vaultName, suggestedFilename, mimeType, cacheFile)
            else -> return@withContext SaveResult.Failed(IllegalStateException("Unsupported home folder scheme: ${homeUri.scheme}"))
        } ?: return@withContext SaveResult.Failed(IllegalStateException("Could not write to home folder"))

        val rfId = UUID.randomUUID().toString()
        resourceDao.insert(
            ResourceFileEntity(
                id = rfId,
                vaultId = vaultId,
                kind = kind,
                filename = outcome.finalFilename,
                sizeBytes = outcome.sizeBytes,
                homeRelativePath = outcome.homeRelativePath,
                absolutePath = null,
                mimeType = mimeType,
                source = "browser_save",
                lastSeenAt = System.currentTimeMillis(),
                createdAt = System.currentTimeMillis()
            )
        )

        val savedTabId = if (kind == "guide") {
            val id = UUID.randomUUID().toString()
            savedTabDao.insertTab(
                SavedTabEntity(
                    id = id,
                    vaultId = vaultId,
                    title = outcome.finalFilename.substringBeforeLast('.'),
                    url = webUrl ?: outcome.uri.toString(),
                    createdAt = Date(),
                    lastAccessed = Date(),
                    fileType = forcedFileType
                        ?: if (outcome.finalFilename.endsWith(".mht", true)) "mht"
                           else outcome.finalFilename.substringAfterLast('.', "html"),
                    fileName = outcome.finalFilename,
                    resourceId = rfId
                )
            )
            id
        } else null

        SaveResult.Success(rfId, savedTabId)
    }

    private fun writeToFileHome(
        homeUri: Uri,
        vaultName: String,
        suggestedFilename: String,
        cacheFile: File
    ): SaveOutcome? {
        val home = File(homeUri.path ?: return null)
        val targetDir = File(home, vaultName).apply { mkdirs() }
        val target = pickUniqueTarget(targetDir, suggestedFilename)
        if (!cacheFile.renameTo(target)) {
            cacheFile.copyTo(target, overwrite = false)
            cacheFile.delete()
        }
        return SaveOutcome(
            homeRelativePath = target.relativeTo(home).path,
            sizeBytes = target.length(),
            uri = Uri.fromFile(target),
            finalFilename = target.name
        )
    }

    private suspend fun writeToSafHome(
        context: Context,
        folder: LibraryFolderManager,
        homeUri: Uri,
        vaultName: String,
        suggestedFilename: String,
        mimeType: String,
        cacheFile: File
    ): SaveOutcome? {
        val vaultDocUri = folder.findOrCreateChildDir(homeUri, vaultName) ?: return null
        val vaultDoc = DocumentFile.fromTreeUri(context, vaultDocUri) ?: return null
        val uniqueName = pickUniqueDocName(vaultDoc, suggestedFilename)
        val targetDoc = vaultDoc.createFile(mimeType.ifBlank { "application/octet-stream" }, uniqueName) ?: return null
        val ok = context.contentResolver.openOutputStream(targetDoc.uri)?.use { out ->
            cacheFile.inputStream().use { it.copyTo(out) }
            true
        } ?: false
        if (!ok) {
            targetDoc.delete()
            return null
        }
        cacheFile.delete()
        val storedName = targetDoc.name ?: uniqueName
        return SaveOutcome(
            homeRelativePath = "$vaultName/$storedName",
            sizeBytes = targetDoc.length(),
            uri = targetDoc.uri,
            finalFilename = storedName
        )
    }

    /**
     * Returns [name] under [dir] if it does not exist; otherwise appends
     * `-2`, `-3`, ... before the extension until a free slot is found.
     */
    internal fun pickUniqueTarget(dir: File, name: String): File {
        var candidate = File(dir, name)
        if (!candidate.exists()) return candidate
        val stem = name.substringBeforeLast('.')
        val ext = if ('.' in name) "." + name.substringAfterLast('.') else ""
        var idx = 2
        while (candidate.exists()) {
            candidate = File(dir, "$stem-$idx$ext")
            idx++
        }
        return candidate
    }

    /**
     * Returns [name] under SAF [dir] if no doc with that name exists; otherwise
     * appends `-2`, `-3`, ... before the extension.
     */
    internal fun pickUniqueDocName(dir: DocumentFile, name: String): String {
        if (dir.findFile(name) == null) return name
        val stem = name.substringBeforeLast('.')
        val ext = if ('.' in name) "." + name.substringAfterLast('.') else ""
        var idx = 2
        while (true) {
            val candidate = "$stem-$idx$ext"
            if (dir.findFile(candidate) == null) return candidate
            idx++
        }
    }

    /**
     * Replaces filesystem-unsafe characters with spaces and collapses runs of
     * whitespace into a single space. Caller appends an extension.
     */
    fun sanitizeTitleForFilename(title: String): String =
        title.replace(Regex("[\\\\/:*?\"<>|]"), " ").replace("\\s+".toRegex(), " ")

    /**
     * One path segment for a file that arrived over the network, named by a
     * Content-Disposition header or a URL tail. Anything before the last
     * separator is dropped, control characters go, and a name that would be
     * empty or a directory reference becomes "download".
     */
    fun sanitizeDownloadFilename(raw: String?): String {
        val leaf = raw.orEmpty()
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .filter { it.code >= 0x20 && it.code != 0x7f }
            .trim()
        return if (leaf.isEmpty() || leaf == "." || leaf == "..") "download" else leaf
    }

    private data class SaveOutcome(
        val homeRelativePath: String,
        val sizeBytes: Long,
        val uri: Uri,
        val finalFilename: String
    )
}
