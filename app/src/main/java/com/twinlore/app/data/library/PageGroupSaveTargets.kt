package com.twinlore.app.data.library

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Filesystem helpers for page-group save targets. Mirrors `LibrarySaveHelpers`'
 * file:// vs SAF (content://) branching but targets
 * `<home>/<vaultName>/<groupFolderName>/page-N.mht`.
 *
 * `groupFolderName` is the sanitized group title chosen once at save time
 * (collision-resolved via [resolveUniqueGroupFolderName]) and persisted on
 * `saved_tabs.group_folder_name` so deletes can find the folder later.
 *
 * Legacy groups created before the v31→v32 migration have a null folder
 * name; the [legacyGroupFolder] helpers preserve the `groups/<groupTabId>/`
 * layout so existing data continues to load until the user re-saves.
 */
object PageGroupSaveTargets {
    data class Outcome(val pageFile: String, val homeRelativePath: String, val sizeBytes: Long)

    /**
     * Canonicalize `<home>/<segments…>` and return it only when it lands
     * strictly inside [home]. Null means "refuse".
     *
     * The segments that build a group folder path are `vaults.folder_name` and
     * `saved_tabs.group_folder_name`, both read straight off the row. Rows
     * arrive from synced snapshots as well as from this device, neither column
     * is sanitized on adoption, and [LibraryFolderManager.resolveFolderName]
     * deliberately prefers the persisted column over the sanitizer — so a
     * parent reference in either one reaches [deleteGroupFolder] intact and
     * would steer a `deleteRecursively()` at a directory of the snapshot
     * author's choosing. Same guard [LibraryFolderManager.deleteChildDir]
     * already applies, for the same reason.
     */
    internal fun resolveDirUnderHome(home: File, vararg segments: String): File? {
        var dir = home
        for (segment in segments) dir = File(dir, segment)
        return try {
            val canonical = dir.canonicalFile
            val homeCanonical = home.canonicalFile
            if (canonical.path.startsWith(homeCanonical.path + File.separator)) canonical else null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Compute a folder name unique among existing immediate children of
     * `<home>/<vaultName>/`. Starts from `sanitizeForPath(groupTitle)`,
     * appending `-2`, `-3`, ... until no collision exists.
     */
    suspend fun resolveUniqueGroupFolderName(
        context: Context,
        folder: LibraryFolderManager,
        vaultName: String,
        groupTitle: String
    ): String? = withContext(Dispatchers.IO) {
        val base = LibraryFolderManager.sanitizeForPath(groupTitle).ifBlank { "Page group" }
        val homeUri = folder.homeFolderUri() ?: return@withContext base
        when (homeUri.scheme) {
            "file" -> {
                val vaultDir = File(File(homeUri.path ?: return@withContext base), vaultName)
                if (!vaultDir.exists()) return@withContext base
                pickUnique(base) { candidate -> File(vaultDir, candidate).exists() }
            }
            "content" -> {
                val vaultDocUri = folder.findOrCreateChildDir(homeUri, vaultName) ?: return@withContext base
                val existingNames = listChildDirNames(context, homeUri, vaultDocUri)
                pickUnique(base) { candidate -> candidate in existingNames }
            }
            else -> base
        }
    }

    suspend fun moveIntoGroupFolder(
        context: Context,
        folder: LibraryFolderManager,
        vaultName: String,
        groupFolderName: String,
        pageIndex: Int,
        tempMht: File
    ): Outcome? = withContext(Dispatchers.IO) {
        val homeUri = folder.homeFolderUri() ?: return@withContext null
        when (homeUri.scheme) {
            "file" -> writeFile(homeUri, vaultName, groupFolderName, pageIndex, tempMht)
            "content" -> writeSaf(context, folder, homeUri, vaultName, groupFolderName, pageIndex, tempMht)
            else -> null
        }
    }

    /**
     * Best-effort cleanup of `<home>/<vaultName>/<groupFolderName>/`. Called
     * on cancel / fatal rollback and on group delete. Returns early if any of
     * the inputs are null (legacy rollback path before first stageSavedPage,
     * or a delete on a row with no recorded folder name — caller should fall
     * back to [legacyDeleteGroupFolder] in that case).
     */
    suspend fun deleteGroupFolder(
        context: Context,
        folder: LibraryFolderManager,
        vaultName: String?,
        groupFolderName: String?
    ): Unit = withContext(Dispatchers.IO) {
        if (vaultName == null || groupFolderName == null) return@withContext
        val homeUri = folder.homeFolderUri() ?: return@withContext
        when (homeUri.scheme) {
            "file" -> {
                val home = File(homeUri.path ?: return@withContext)
                val groupDir = resolveDirUnderHome(home, vaultName, groupFolderName)
                if (groupDir == null) {
                    android.util.Log.w(
                        "PageGroupSaveTargets",
                        "refusing recursive group-folder delete outside home"
                    )
                    return@withContext
                }
                if (groupDir.exists()) groupDir.deleteRecursively()
            }
            "content" -> {
                val vaultDocUri = findChildDirByQuery(context, homeUri, vaultName) ?: return@withContext
                val groupDocUri = findChildDirUnderDoc(context, homeUri, vaultDocUri, groupFolderName) ?: return@withContext
                // DocumentsContract.deleteDocument works on tree-document URIs
                // (the shape returned by buildDocumentUriUsingTree). DocumentFile.
                // fromTreeUri + .delete() silently no-ops on those because
                // fromTreeUri expects a tree URI, not a per-document URI.
                runCatching { DocumentsContract.deleteDocument(context.contentResolver, groupDocUri) }
            }
            else -> Unit
        }
    }

    /** Legacy: `<home>/<vaultName>/groups/<groupTabId>/`. Used for v0 groups whose row has no folder name. */
    suspend fun legacyDeleteGroupFolder(
        context: Context,
        folder: LibraryFolderManager,
        vaultName: String?,
        groupTabId: String
    ): Unit = withContext(Dispatchers.IO) {
        if (vaultName == null) return@withContext
        val homeUri = folder.homeFolderUri() ?: return@withContext
        when (homeUri.scheme) {
            "file" -> {
                val home = File(homeUri.path ?: return@withContext)
                // Same guard as [deleteGroupFolder]: vaultName is the unsanitized
                // vaults.folder_name column and can arrive from a foreign snapshot.
                val groupDir = resolveDirUnderHome(home, vaultName, "groups", groupTabId)
                if (groupDir == null) {
                    android.util.Log.w(
                        "PageGroupSaveTargets",
                        "refusing recursive legacy group-folder delete outside home"
                    )
                    return@withContext
                }
                if (groupDir.exists()) groupDir.deleteRecursively()
            }
            "content" -> {
                val vaultDocUri = findChildDirByQuery(context, homeUri, vaultName) ?: return@withContext
                val groupsDocUri = findChildDirUnderDoc(context, homeUri, vaultDocUri, "groups") ?: return@withContext
                val groupDocUri = findChildDirUnderDoc(context, homeUri, groupsDocUri, groupTabId) ?: return@withContext
                runCatching { DocumentsContract.deleteDocument(context.contentResolver, groupDocUri) }
            }
            else -> Unit
        }
    }

    /**
     * Write a small text file (in practice `manifest.json`) into
     * `<home>/<vaultName>/<groupFolderName>/`.
     *
     * [groupFolder] can only return a real path for `file://` homes — on a SAF
     * home it hands back a cache-dir placeholder, so a `File`-based manifest
     * write landed in app-private storage and never reached the library folder.
     * A group replicated to another device therefore arrived as bare page files
     * with nothing describing them. This writes through SAF so the manifest sits
     * next to its pages on every home type.
     */
    suspend fun writeGroupTextFile(
        context: Context,
        folder: LibraryFolderManager,
        vaultName: String,
        groupFolderName: String,
        filename: String,
        mimeType: String,
        content: String
    ): Boolean = withContext(Dispatchers.IO) {
        val homeUri = folder.homeFolderUri() ?: return@withContext false
        if (homeUri.scheme == "file") {
            val home = File(homeUri.path ?: return@withContext false)
            val groupDir = File(File(home, vaultName), groupFolderName).apply { mkdirs() }
            return@withContext runCatching { File(groupDir, filename).writeText(content) }.isSuccess
        }
        val vaultDocUri = folder.findOrCreateChildDir(homeUri, vaultName) ?: return@withContext false
        val groupDocUri = folder.findOrCreateChildDirUnder(homeUri, vaultDocUri, groupFolderName)
            ?: return@withContext false
        val groupDoc = DocumentFile.fromTreeUri(context, groupDocUri) ?: return@withContext false
        // createFile would uniquify into "manifest (1).json" if one is already
        // there, so replace rather than add — the manifest is rewritten on every
        // group mutation.
        groupDoc.findFile(filename)?.delete()
        val target = groupDoc.createFile(mimeType, filename) ?: return@withContext false
        runCatching {
            context.contentResolver.openOutputStream(target.uri)?.use { it.write(content.toByteArray()) }
                ?: throw java.io.IOException("openOutputStream returned null for ${target.uri}")
        }.onFailure { target.delete() }.isSuccess
    }

    /**
     * Returns the on-disk folder path for a saved page-group. For file://
     * homes this is the real folder; for SAF homes a cache-dir placeholder
     * so File-based callers don't NPE — actual SAF deletes go through
     * [deleteGroupFolder].
     */
    suspend fun groupFolder(
        context: Context,
        folder: LibraryFolderManager,
        vaultName: String,
        groupFolderName: String
    ): File = withContext(Dispatchers.IO) {
        val homeUri = folder.homeFolderUri()
        if (homeUri?.scheme == "file") {
            val home = File(homeUri.path ?: return@withContext fallbackFolder(context, groupFolderName))
            File(File(home, vaultName), groupFolderName)
        } else {
            fallbackFolder(context, groupFolderName)
        }
    }

    /** Legacy resolver matching the v0 `groups/<groupTabId>/` layout. */
    suspend fun legacyGroupFolder(
        context: Context,
        folder: LibraryFolderManager,
        vaultName: String,
        groupTabId: String
    ): File = withContext(Dispatchers.IO) {
        val homeUri = folder.homeFolderUri()
        if (homeUri?.scheme == "file") {
            val home = File(homeUri.path ?: return@withContext fallbackFolder(context, groupTabId))
            File(File(File(home, vaultName), "groups"), groupTabId)
        } else {
            fallbackFolder(context, groupTabId)
        }
    }

    private fun fallbackFolder(context: Context, key: String): File =
        File(File(context.cacheDir, "page-groups"), key)

    private fun writeFile(
        homeUri: Uri, vaultName: String, groupFolderName: String, pageIndex: Int,
        tempMht: File
    ): Outcome? {
        val home = File(homeUri.path ?: return null)
        val groupDir = File(File(home, vaultName), groupFolderName).apply { mkdirs() }
        val pageFile = "page-${pageIndex + 1}.mht"
        val target = File(groupDir, pageFile)
        if (!tempMht.renameTo(target)) {
            tempMht.copyTo(target, overwrite = true); tempMht.delete()
        }
        return Outcome(
            pageFile = pageFile,
            homeRelativePath = "$vaultName/$groupFolderName/$pageFile",
            sizeBytes = target.length()
        )
    }

    private suspend fun writeSaf(
        context: Context,
        folder: LibraryFolderManager,
        homeUri: Uri,
        vaultName: String,
        groupFolderName: String,
        pageIndex: Int,
        tempMht: File
    ): Outcome? = withContext(Dispatchers.IO) {
        // The first call resolves against the home tree root (correct use of
        // findOrCreateChildDir, since homeUri IS the tree URI). The second
        // level must use findOrCreateChildDirUnder — chaining findOrCreateChildDir
        // with the returned doc URI would collapse back to the tree root
        // because DocumentsContract.getTreeDocumentId always returns the root.
        val vaultDocUri = folder.findOrCreateChildDir(homeUri, vaultName) ?: return@withContext null
        val groupDocUri = folder.findOrCreateChildDirUnder(homeUri, vaultDocUri, groupFolderName)
            ?: return@withContext null
        val groupDoc = DocumentFile.fromTreeUri(context, groupDocUri) ?: return@withContext null

        val pageFile = "page-${pageIndex + 1}.mht"
        val target = groupDoc.createFile("multipart/related", pageFile) ?: return@withContext null
        val ok = context.contentResolver.openOutputStream(target.uri)?.use { out ->
            tempMht.inputStream().use { it.copyTo(out) }; true
        } ?: false
        if (!ok) { target.delete(); return@withContext null }
        tempMht.delete()

        Outcome(
            pageFile = pageFile,
            homeRelativePath = "$vaultName/$groupFolderName/$pageFile",
            sizeBytes = target.length()
        )
    }

    private inline fun pickUnique(base: String, exists: (String) -> Boolean): String {
        if (!exists(base)) return base
        var idx = 2
        while (true) {
            val candidate = "$base-$idx"
            if (!exists(candidate)) return candidate
            idx++
        }
    }

    private fun listChildDirNames(
        context: Context,
        treeUri: Uri,
        parentDocUri: Uri
    ): Set<String> {
        val parentDocId = try {
            DocumentsContract.getDocumentId(parentDocUri)
        } catch (_: Throwable) { return emptySet() }
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        return try {
            val out = mutableSetOf<String>()
            context.contentResolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE
                ),
                null, null, null
            )?.use { c ->
                while (c.moveToNext()) {
                    if (c.getString(1) == DocumentsContract.Document.MIME_TYPE_DIR) {
                        out += c.getString(0)
                    }
                }
            }
            out
        } catch (_: Throwable) {
            emptySet()
        }
    }

    private fun findChildDirByQuery(context: Context, treeUri: Uri, displayName: String): Uri? {
        val parentDocId = try {
            DocumentsContract.getTreeDocumentId(treeUri)
        } catch (_: Throwable) { return null }
        return findChildDirByParentDocId(context, treeUri, parentDocId, displayName)
    }

    private fun findChildDirUnderDoc(
        context: Context,
        treeUri: Uri,
        parentDocUri: Uri,
        displayName: String
    ): Uri? {
        val parentDocId = try {
            DocumentsContract.getDocumentId(parentDocUri)
        } catch (_: Throwable) { return null }
        return findChildDirByParentDocId(context, treeUri, parentDocId, displayName)
    }

    private fun findChildDirByParentDocId(
        context: Context,
        treeUri: Uri,
        parentDocId: String,
        displayName: String
    ): Uri? {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        return try {
            context.contentResolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE
                ),
                null, null, null
            )?.use { c ->
                while (c.moveToNext()) {
                    if (c.getString(1) == displayName &&
                        c.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR
                    ) {
                        return@use DocumentsContract.buildDocumentUriUsingTree(treeUri, c.getString(0))
                    }
                }
                null
            }
        } catch (_: Throwable) {
            null
        }
    }
}
