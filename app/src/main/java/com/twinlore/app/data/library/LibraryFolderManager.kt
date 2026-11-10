package com.twinlore.app.data.library

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.twinlore.app.data.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Found-file metadata returned by [LibraryFolderManager.findByNameAndSizeInVault].
 *
 * @property uri an opener-ready URI (file:// for direct file paths, content:// for SAF)
 * @property absolutePath the absolute filesystem path if available, else the URI string
 * @property relativePath the path relative to the home folder root (forward slashes)
 */
data class FoundFile(val uri: Uri, val absolutePath: String, val relativePath: String)

/**
 * Owns the user-picked home folder URI. All file IO into and within the home folder
 * goes through this class so the rest of the codebase never touches SAF or File directly.
 *
 * The home folder may be either a real filesystem path (file://) or an SAF tree URI
 * (content://). file:// is the fast path; content:// uses DocumentFile traversal.
 */
@Singleton
class LibraryFolderManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository
) {

    companion object {
        /**
         * Single source of truth for vault directory naming. Strips characters
         * that FAT/exFAT filesystems and most SAF DocumentsProviders forbid
         * (`/ \ : * ? " < > |`), collapses runs of whitespace, and falls back
         * to "Unnamed" if the result is empty.
         *
         * IMPORTANT: every site that writes into the storage folder must pass
         * the sanitized name through `findOrCreateChildDir` (or the equivalent
         * SAF/file logic). If two sites disagree on sanitization, they create
         * duplicate `Game Name (1)` directories on each save.
         */
        fun sanitizeForPath(name: String): String =
            name.replace(Regex("[/\\\\:*?\"<>|]"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
                // "." and ".." survive the character strip above and would name
                // the home folder or its parent on a file:// home.
                .let { if (it.isEmpty() || it == "." || it == "..") "Unnamed" else it }

        /**
         * Resolve the actual on-disk folder name for a vault.
         *
         * Prefers the persisted `folder_name` column when set (rows created on
         * SCHEMA_VERSION >= 29), which is the stable identity captured at
         * vault creation and never updated by rename. Falls back to sanitizing
         * the current name for legacy rows where the column is null — those
         * vaults have the pre-fix behavior, which is correct as long as they
         * were never renamed.
         *
         * Overloaded for domain `Vault` and persistence `VaultEntity` so
         * neither layer has to round-trip through a mapper just to read one
         * computed field.
         */
        fun resolveFolderName(vault: com.twinlore.app.domain.Vault): String =
            vault.folderName ?: sanitizeForPath(vault.name)

        fun resolveFolderName(entity: com.twinlore.app.data.local.VaultEntity): String =
            entity.folderName ?: sanitizeForPath(entity.name)

        /**
         * Titles that carry the article at the end ("Legend of Zelda, The: A
         * Link to the Past") are the RetroArch / No-Intro convention; the same
         * game named by hand or by Steam keeps the article in front. Both are
         * valid vault names and both sanitize cleanly, but to DIFFERENT folder
         * names — so a vault created under one convention cannot see the folder
         * created under the other. These two functions convert between the
         * spellings so [VaultFolderAdoption] can recognise them as the same
         * game. The article must be followed by the end of the title or a
         * subtitle separator (`:` / `-`), which keeps ordinary mid-title commas
         * ("Sonic, Tails and Knuckles") from being rewritten.
         */
        private val TRAILING_ARTICLE = Regex("^(.+?),\\s+(The|A|An)(\\s*[:\\-].*)?$", RegexOption.IGNORE_CASE)
        private val LEADING_ARTICLE = Regex("^(The|A|An)\\s+(.+?)(\\s*[:\\-].*)?$", RegexOption.IGNORE_CASE)

        /** "Legend of Zelda, The: X" -> "The Legend of Zelda: X". Unchanged when there is no trailing article. */
        fun normalizeArticle(name: String): String =
            TRAILING_ARTICLE.matchEntire(name.trim())?.let { m ->
                val (title, article, rest) = m.destructured
                "$article $title$rest"
            } ?: name

        /** "The Legend of Zelda: X" -> "Legend of Zelda, The: X". Unchanged when there is no leading article. */
        fun trailingArticle(name: String): String =
            LEADING_ARTICLE.matchEntire(name.trim())?.let { m ->
                val (article, title, rest) = m.destructured
                "$title, $article$rest"
            } ?: name

        /**
         * Case-insensitive child lookup for SAF trees. Android's emulated
         * storage is case-INSENSITIVE but SAF name matching is case-SENSITIVE:
         * a vault deleted as "Super Metroid" and re-created as "super metroid"
         * silently reuses the SAME on-disk directory (which keeps its original
         * casing), so an exact [DocumentFile.findFile] misses it and every
         * resource in the vault fails to resolve. Exact match first (fast
         * path), then an ignore-case scan over the listing.
         */
        fun DocumentFile.findChildIgnoreCase(name: String): DocumentFile? =
            findFile(name)
                ?: listFiles().firstOrNull { it.name?.equals(name, ignoreCase = true) == true }

        val IMAGE_EXTS = setOf("png", "jpg", "jpeg", "webp")

        fun isImageFileName(name: String): Boolean =
            name.substringAfterLast('.', "").lowercase() in IMAGE_EXTS
    }

    /**
     * Returns the configured home folder URI, or null if the user has not picked one yet.
     * MainViewModel.evaluateStartup blocks the rest of the UI behind MigrationFolderSetupScreen
     * until this is non-null, so callers reached after the gate releases can rely on it.
     */
    suspend fun homeFolderUri(): Uri? = withContext(Dispatchers.IO) {
        settings.homeFolderUri.first()?.let(Uri::parse)
    }

    /** Convenience: home folder as a [File] when the URI is a real path; null for SAF trees. */
    suspend fun homeFolderAsFile(): File? = withContext(Dispatchers.IO) {
        val uri = homeFolderUri() ?: return@withContext null
        if (uri.scheme == "file") File(uri.path ?: return@withContext null) else null
    }

    /**
     * Filesystem root of the home folder, when the tree URI converts to an absolute
     * path: either it is already a `file://` URI ([homeFolderAsFile]), or it is a
     * `content://` SAF tree URI whose document id converts via
     * [com.twinlore.app.data.esde.EsdeFsAccess.docIdToFsPath] -- the same
     * conversion the ES-DE folder uses. Works for the primary external-storage
     * provider; returns null for anything that doesn't convert (a provider other
     * than `com.android.externalstorage`, an SD-card volume with an unusual
     * document id shape, or no home folder set at all).
     *
     * Pure derivation only -- it does not check whether direct `java.io.File` reads
     * are actually permitted. Callers must gate on
     * [com.twinlore.app.data.esde.EsdeFsAccess.isGranted] first, the same way
     * [com.twinlore.app.data.esde.EsdeFsAccess.rootPath]'s callers do.
     */
    suspend fun homeFolderFsRoot(): File? = withContext(Dispatchers.IO) {
        val uri = homeFolderUri() ?: return@withContext null
        when (uri.scheme) {
            "file" -> uri.path?.let(::File)
            "content" -> {
                val docId = try {
                    DocumentsContract.getTreeDocumentId(uri)
                } catch (_: Throwable) {
                    return@withContext null
                }
                com.twinlore.app.data.esde.EsdeFsAccess.docIdToFsPath(docId)?.let(::File)
            }
            else -> null
        }
    }

    /** A file inside a vault subfolder, with the size a resource row needs. */
    data class FoundEntry(
        val filename: String,
        val sizeBytes: Long,
        val uri: Uri,
        val relativePath: String,
    )

    /**
     * Immediate subfolders of `<home>/<vaultDirName>/` mapped to the files they
     * contain (non-recursive). Page groups live exactly one level down as
     * `<VaultFolder>/<Group Name>/page-N.mht`, so this is what
     * [VaultFolderAdoption]'s guide-side counterpart needs to spot one on disk.
     */
    suspend fun listSubfolderFiles(vaultDirName: String): Map<String, List<FoundEntry>> =
        withContext(Dispatchers.IO) {
            val rootUri = homeFolderUri() ?: return@withContext emptyMap()
            fun isVaultDir(name: String) = name.equals(vaultDirName, ignoreCase = true)

            if (rootUri.scheme == "file") {
                val root = File(rootUri.path ?: return@withContext emptyMap())
                val vaultDir = root.listFiles()?.firstOrNull { it.isDirectory && isVaultDir(it.name) }
                    ?: return@withContext emptyMap()
                return@withContext vaultDir.listFiles()
                    ?.filter { it.isDirectory && !it.name.startsWith(".") }
                    ?.associate { sub ->
                        sub.name to (
                            sub.listFiles()?.filter { it.isFile }?.map { f ->
                                FoundEntry(
                                    filename = f.name,
                                    sizeBytes = f.length(),
                                    uri = Uri.fromFile(f),
                                    relativePath = f.relativeTo(root).invariantSeparatorsPath,
                                )
                            }.orEmpty()
                        )
                    }
                    ?: emptyMap()
            }

            val docRoot = DocumentFile.fromTreeUri(context, rootUri) ?: return@withContext emptyMap()
            val vaultDoc = docRoot.listFiles().firstOrNull { it.isDirectory && it.name?.let(::isVaultDir) == true }
                ?: return@withContext emptyMap()
            val vaultName = vaultDoc.name ?: vaultDirName
            vaultDoc.listFiles()
                .filter { it.isDirectory && it.name?.startsWith(".") == false }
                .associate { sub ->
                    val subName = sub.name!!
                    subName to sub.listFiles().filter { it.isFile }.mapNotNull { f ->
                        val fname = f.name ?: return@mapNotNull null
                        FoundEntry(
                            filename = fname,
                            sizeBytes = f.length(),
                            uri = f.uri,
                            relativePath = "$vaultName/$subName/$fname",
                        )
                    }
                }
        }

    /** Names of the top-level directories under the home folder (the vault folders). */
    suspend fun listVaultDirNames(): List<String> = withContext(Dispatchers.IO) {
        val rootUri = homeFolderUri() ?: return@withContext emptyList()
        if (rootUri.scheme == "file") {
            val rootPath = rootUri.path ?: return@withContext emptyList()
            return@withContext File(rootPath).listFiles()
                ?.filter { it.isDirectory }
                ?.map { it.name }
                ?: emptyList()
        }
        val docRoot = DocumentFile.fromTreeUri(context, rootUri) ?: return@withContext emptyList()
        docRoot.listFiles().filter { it.isDirectory }.mapNotNull { it.name }
    }

    /**
     * Walk the vault subdirectory under the home folder and return the first file
     * whose name and size match. Used by the resource resolver's recovery path
     * when a remembered path no longer resolves but the same file exists somewhere
     * else within the vault's own subtree.
     *
     * Scoped per-vault by design: two vaults with identically-named cover.jpg files
     * of the same size must not match each other's rows. Callers without a vault
     * dir name (e.g. cross-vault scans) should iterate vault dirs themselves.
     */
    suspend fun findByNameAndSizeInVault(
        vaultDirName: String,
        filename: String,
        sizeBytes: Long
    ): FoundFile? = withContext(Dispatchers.IO) {
        val rootUri = homeFolderUri() ?: return@withContext null
        // The vault dir itself plus provider-suffixed variants ("Name (2)"):
        // before folder creation matched ignore-case, a re-created vault whose
        // dir differed only by casing made the SAF provider auto-suffix a
        // sibling, stranding saves there while rows pointed at the base name.
        // Searching the variants (and rewriting the row at the call site)
        // heals those libraries without moving any files.
        fun isVaultDirOrVariant(name: String): Boolean =
            name.equals(vaultDirName, ignoreCase = true) ||
                name.matches(Regex("(?i)${Regex.escape(vaultDirName)} \\(\\d+\\)"))
        // Fast path: real filesystem
        if (rootUri.scheme == "file") {
            val rootPath = rootUri.path ?: return@withContext null
            val root = File(rootPath)
            val candidateDirs = root.listFiles()
                ?.filter { it.isDirectory && isVaultDirOrVariant(it.name) }
                ?.sortedBy { it.name.length } // base dir before "(N)" variants
                ?: emptyList()
            for (vaultDir in candidateDirs) {
                vaultDir.walkTopDown()
                    .firstOrNull { it.isFile && it.name == filename && it.length() == sizeBytes }
                    ?.let { hit ->
                        return@withContext FoundFile(
                            uri = Uri.fromFile(hit),
                            absolutePath = hit.absolutePath,
                            relativePath = hit.relativeTo(root).invariantSeparatorsPath
                        )
                    }
            }
            return@withContext null
        }
        // SAF tree fallback: locate the vault child (and variants), search inside.
        val docRoot = DocumentFile.fromTreeUri(context, rootUri) ?: return@withContext null
        val candidateDocs = docRoot.listFiles()
            .filter { it.isDirectory && it.name?.let(::isVaultDirOrVariant) == true }
            .sortedBy { it.name?.length ?: Int.MAX_VALUE } // base dir before "(N)" variants
        for (vaultDoc in candidateDocs) {
            searchSaf(vaultDoc, filename, sizeBytes, prefix = vaultDoc.name ?: vaultDirName)
                ?.let { return@withContext it }
        }
        null
    }

    /**
     * List every image file (png/jpg/jpeg/webp) that physically lives under this
     * vault's own library folder `<home>/<vaultDirName>/` (and provider-suffixed
     * "Name (2)" variants). Works for both file:// and SAF content:// homes.
     * The returned [FoundFile.relativePath] is home-relative (e.g. "Game/box.png"),
     * matching a resource row's `homeRelativePath` so callers can cross-reference.
     *
     * Unlike media-index matching, this does NOT depend on a prior scan or on the
     * filename matching the game name — every image in the folder is returned.
     */
    suspend fun listImagesInVault(vaultDirName: String): List<FoundFile> = withContext(Dispatchers.IO) {
        val rootUri = homeFolderUri() ?: return@withContext emptyList()
        fun isVaultDirOrVariant(name: String): Boolean =
            name.equals(vaultDirName, ignoreCase = true) ||
                name.matches(Regex("(?i)${Regex.escape(vaultDirName)} \\(\\d+\\)"))
        val out = mutableListOf<FoundFile>()
        if (rootUri.scheme == "file") {
            val rootPath = rootUri.path ?: return@withContext emptyList()
            val root = File(rootPath)
            val candidateDirs = root.listFiles()
                ?.filter { it.isDirectory && isVaultDirOrVariant(it.name) }
                ?.sortedBy { it.name.length }
                ?: emptyList()
            for (vaultDir in candidateDirs) {
                vaultDir.walkTopDown()
                    .filter { it.isFile && isImageFileName(it.name) }
                    .forEach { hit ->
                        out += FoundFile(
                            uri = Uri.fromFile(hit),
                            absolutePath = hit.absolutePath,
                            relativePath = hit.relativeTo(root).invariantSeparatorsPath,
                        )
                    }
            }
            return@withContext out
        }
        val docRoot = DocumentFile.fromTreeUri(context, rootUri) ?: return@withContext emptyList()
        val candidateDocs = docRoot.listFiles()
            .filter { it.isDirectory && it.name?.let(::isVaultDirOrVariant) == true }
            .sortedBy { it.name?.length ?: Int.MAX_VALUE }
        for (vaultDoc in candidateDocs) {
            collectImagesSaf(vaultDoc, prefix = vaultDoc.name ?: vaultDirName, out)
        }
        out
    }

    private fun collectImagesSaf(dir: DocumentFile, prefix: String, out: MutableList<FoundFile>) {
        for (child in dir.listFiles()) {
            val childName = child.name ?: continue
            val rel = if (prefix.isEmpty()) childName else "$prefix/$childName"
            if (child.isFile && isImageFileName(childName)) {
                out += FoundFile(uri = child.uri, absolutePath = child.uri.toString(), relativePath = rel)
            } else if (child.isDirectory) {
                collectImagesSaf(child, rel, out)
            }
        }
    }

    private fun searchSaf(dir: DocumentFile, name: String, size: Long, prefix: String): FoundFile? {
        for (child in dir.listFiles()) {
            val childName = child.name ?: continue
            val rel = if (prefix.isEmpty()) childName else "$prefix/$childName"
            if (child.isFile && childName == name && child.length() == size) {
                return FoundFile(uri = child.uri, absolutePath = child.uri.toString(), relativePath = rel)
            }
            if (child.isDirectory) {
                searchSaf(child, name, size, rel)?.let { return it }
            }
        }
        return null
    }

    /**
     * Outcome of [copyInto]. The relative path is always slash-delimited and includes
     * the vault subfolder, e.g. `Game Title/cover.jpg`.
     */
    data class CopyOutcome(
        val homeRelativePath: String,
        val absolutePath: String?,
        val uri: Uri,
        val sizeBytes: Long,
        val finalFilename: String
    )

    /**
     * Copies [sourceFile] into `<storageFolder>/<vaultDirName>/<suggestedFilename>`,
     * uniquifying on collision. Works for both file:// and content:// (SAF) storage
     * folders. Returns null if the storage folder is unset or the write fails.
     *
     * Note: this is a copy (the source file is not deleted). Callers performing a
     * "move" should delete the source after a successful return.
     */
    suspend fun copyInto(
        sourceFile: File,
        vaultDirName: String,
        suggestedFilename: String,
        mimeType: String?
    ): CopyOutcome? = withContext(Dispatchers.IO) {
        val homeUri = homeFolderUri() ?: return@withContext null
        when (homeUri.scheme) {
            "file" -> {
                val home = File(homeUri.path ?: return@withContext null)
                val vaultDir = File(home, vaultDirName).apply { mkdirs() }
                val target = pickUniqueFileTarget(vaultDir, suggestedFilename)
                sourceFile.copyTo(target, overwrite = false)
                CopyOutcome(
                    homeRelativePath = target.relativeTo(home).invariantSeparatorsPath,
                    absolutePath = target.absolutePath,
                    uri = Uri.fromFile(target),
                    sizeBytes = target.length(),
                    finalFilename = target.name
                )
            }
            "content" -> {
                val vaultDocUri = findOrCreateChildDir(homeUri, vaultDirName) ?: return@withContext null
                val vaultDoc = DocumentFile.fromTreeUri(context, vaultDocUri) ?: return@withContext null
                val uniqueName = pickUniqueDocName(vaultDoc, suggestedFilename)
                val targetDoc = vaultDoc.createFile(
                    mimeType?.takeIf { it.isNotBlank() } ?: "application/octet-stream",
                    uniqueName
                ) ?: return@withContext null
                val ok = context.contentResolver.openOutputStream(targetDoc.uri)?.use { out ->
                    sourceFile.inputStream().use { it.copyTo(out) }
                    true
                } ?: false
                if (!ok) {
                    targetDoc.delete()
                    return@withContext null
                }
                val storedName = targetDoc.name ?: uniqueName
                CopyOutcome(
                    homeRelativePath = "$vaultDirName/$storedName",
                    absolutePath = null,
                    uri = targetDoc.uri,
                    sizeBytes = targetDoc.length(),
                    finalFilename = storedName
                )
            }
            else -> null
        }
    }

    /**
     * Find or create an immediate child directory of [treeUri] with the given
     * [displayName]. Uses [DocumentsContract] queries directly rather than
     * [DocumentFile.findFile] because the latter occasionally misses existing
     * children when the underlying SAF provider's listing cache is stale —
     * which then leads `createDirectory` to trigger the provider's auto-suffix
     * logic and produce `Game Name (1)`, `Game Name (2)`, etc. duplicates.
     *
     * Returns the existing or newly-created directory's tree-document URI, or
     * null if neither resolution nor creation succeeded.
     */
    suspend fun findOrCreateChildDir(treeUri: Uri, displayName: String): Uri? = withContext(Dispatchers.IO) {
        val parentDocId = try {
            DocumentsContract.getTreeDocumentId(treeUri)
        } catch (_: Throwable) { return@withContext null }
        val parentDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, parentDocId)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        try {
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
                    // ignoreCase: the FS is case-insensitive, so a dir whose
                    // casing differs IS the same dir — adopting it here keeps
                    // writes landing in the existing folder instead of letting
                    // the provider auto-suffix a "name (1)" duplicate.
                    if (c.getString(1).equals(displayName, ignoreCase = true) &&
                        c.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR
                    ) {
                        return@withContext DocumentsContract.buildDocumentUriUsingTree(treeUri, c.getString(0))
                    }
                }
            }
        } catch (_: Throwable) {
            // fall through to create
        }
        runCatching {
            DocumentsContract.createDocument(
                context.contentResolver,
                parentDocUri,
                DocumentsContract.Document.MIME_TYPE_DIR,
                displayName
            )
        }.getOrNull()
    }

    /**
     * Resolve a homeRelativePath like `"Foo/cover.jpg"` to an opener-ready URI under
     * [treeUri]. Returns null if the path no longer exists in the tree.
     *
     * Used by the storage-folder migration: rows in `resource_files` know their
     * relative path under the old tree; this turns that into a real URI we can
     * stream from regardless of whether the old tree was file:// or content://.
     */
    suspend fun resolveInTree(treeUri: Uri, homeRelativePath: String): Uri? = withContext(Dispatchers.IO) {
        when (treeUri.scheme) {
            "file" -> {
                val root = File(treeUri.path ?: return@withContext null)
                val target = File(root, homeRelativePath)
                if (target.exists()) Uri.fromFile(target) else null
            }
            "content" -> {
                var doc: DocumentFile? = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext null
                for (seg in homeRelativePath.split('/').filter { it.isNotEmpty() }) {
                    doc = doc?.findChildIgnoreCase(seg) ?: return@withContext null
                }
                doc?.uri
            }
            else -> null
        }
    }

    /**
     * Copy [sourceUri] (file:// or content://) into [destTreeUri] (file:// or content://)
     * under `<vaultDirName>/<suggestedFilename>`. Uniquifies on collision. Returns the
     * resulting outcome, or null on any failure.
     *
     * Unlike [copyInto], the destination tree is passed explicitly rather than read
     * from settings — used by the storage-folder migration where we copy from the
     * about-to-be-replaced tree to the newly picked one before the URI is committed.
     */
    suspend fun copyUriIntoTree(
        sourceUri: Uri,
        destTreeUri: Uri,
        vaultDirName: String,
        suggestedFilename: String,
        mimeType: String?
    ): CopyOutcome? = withContext(Dispatchers.IO) {
        val input = try {
            context.contentResolver.openInputStream(sourceUri)
        } catch (_: Exception) { null } ?: return@withContext null
        input.use { stream ->
            when (destTreeUri.scheme) {
                "file" -> {
                    val root = File(destTreeUri.path ?: return@withContext null)
                    val vaultDir = File(root, vaultDirName).apply { mkdirs() }
                    val target = pickUniqueFileTarget(vaultDir, suggestedFilename)
                    target.outputStream().use { out -> stream.copyTo(out) }
                    CopyOutcome(
                        homeRelativePath = target.relativeTo(root).invariantSeparatorsPath,
                        absolutePath = target.absolutePath,
                        uri = Uri.fromFile(target),
                        sizeBytes = target.length(),
                        finalFilename = target.name
                    )
                }
                "content" -> {
                    val vaultDocUri = findOrCreateChildDir(destTreeUri, vaultDirName) ?: return@withContext null
                    val vaultDoc = DocumentFile.fromTreeUri(context, vaultDocUri) ?: return@withContext null
                    val uniqueName = pickUniqueDocName(vaultDoc, suggestedFilename)
                    val targetDoc = vaultDoc.createFile(
                        mimeType?.takeIf { it.isNotBlank() } ?: "application/octet-stream",
                        uniqueName
                    ) ?: return@withContext null
                    val ok = context.contentResolver.openOutputStream(targetDoc.uri)?.use { out ->
                        stream.copyTo(out)
                        true
                    } ?: false
                    if (!ok) {
                        targetDoc.delete()
                        return@withContext null
                    }
                    val storedName = targetDoc.name ?: uniqueName
                    CopyOutcome(
                        homeRelativePath = "$vaultDirName/$storedName",
                        absolutePath = null,
                        uri = targetDoc.uri,
                        sizeBytes = targetDoc.length(),
                        finalFilename = storedName
                    )
                }
                else -> null
            }
        }
    }

    /**
     * Like [findOrCreateChildDir] but for nested traversal under an already-
     * resolved document URI. The [treeUri] must remain the root tree URI for
     * permission scoping; [parentDocUri] is the URI returned by an earlier
     * [findOrCreateChildDir] call (or [DocumentsContract.buildDocumentUriUsingTree]).
     *
     * Required when walking into a SAF folder more than one level deep —
     * [findOrCreateChildDir] only works against the tree's root because
     * [DocumentsContract.getTreeDocumentId] always returns the root doc id,
     * so chaining its result as the next call's `treeUri` argument would
     * create the new child at the tree root instead of under the intended
     * parent. See `PageGroupSaveTargets` for a multi-level use.
     */
    suspend fun findOrCreateChildDirUnder(
        treeUri: Uri,
        parentDocUri: Uri,
        displayName: String
    ): Uri? = withContext(Dispatchers.IO) {
        val parentDocId = runCatching { DocumentsContract.getDocumentId(parentDocUri) }.getOrNull()
            ?: return@withContext null
        val docId = findOrCreateChildDirDocId(treeUri, parentDocId, displayName) ?: return@withContext null
        DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
    }

    /**
     * Find-or-create variant that walks within a single tree by parent doc id
     * instead of by tree URI. Used by [copyToSafHome] to create nested
     * subdirectories. Returns the doc id of the existing or newly-created
     * child directory, or null on failure.
     */
    private suspend fun findOrCreateChildDirDocId(
        treeUri: Uri,
        parentDocId: String,
        displayName: String
    ): String? = withContext(Dispatchers.IO) {
        val parentDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, parentDocId)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        try {
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
                        return@withContext c.getString(0)
                    }
                }
            }
        } catch (_: Throwable) {
            // fall through to create
        }
        val createdUri = runCatching {
            DocumentsContract.createDocument(
                context.contentResolver,
                parentDocUri,
                DocumentsContract.Document.MIME_TYPE_DIR,
                displayName
            )
        }.getOrNull() ?: return@withContext null
        runCatching { DocumentsContract.getDocumentId(createdUri) }.getOrNull()
    }

    private fun pickUniqueFileTarget(dir: File, name: String): File {
        val candidate = File(dir, name)
        if (!candidate.exists()) return candidate
        val stem = name.substringBeforeLast('.')
        val ext = if ('.' in name) "." + name.substringAfterLast('.') else ""
        var idx = 2
        while (true) {
            val attempt = File(dir, "$stem-$idx$ext")
            if (!attempt.exists()) return attempt
            idx++
        }
    }

    private fun pickUniqueDocName(dir: DocumentFile, name: String): String {
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
     * Copy [srcFile]'s bytes into the configured home folder at the relative
     * path `<subdir>/<filename>`. Works with both `file://` and `content://`
     * home schemes. Uses a `.partial` → rename atomic-commit pattern.
     *
     * Returns the destination URI on success, null when the home folder isn't
     * configured or the copy / rename fails.
     *
     * - `subdir` may contain `/` to create nested directories
     *   (e.g. `"MyVault/guide/bookmark-xyz"`). Directory components are
     *   created on demand.
     * - If a file with the same name already exists at the destination, it
     *   gets replaced.
     * - Orphan `.partial` files in the destination directory are swept first.
     */
    suspend fun copyFileIntoTree(
        srcFile: File,
        subdir: String,
        filename: String
    ): Uri? = withContext(Dispatchers.IO) {
        val homeUri = homeFolderUri() ?: return@withContext null
        when (homeUri.scheme) {
            "file" -> copyToFileHome(homeUri, srcFile, subdir, filename)
            "content" -> copyToSafHome(homeUri, srcFile, subdir, filename)
            else -> null
        }
    }

    private fun copyToFileHome(
        homeUri: Uri,
        srcFile: File,
        subdir: String,
        filename: String
    ): Uri? {
        val home = homeUri.path?.let(::File) ?: return null
        val targetDir = File(home, subdir).apply { mkdirs() }
        sweepPartialsInFileDir(targetDir, filename)
        val partial = File(targetDir, "$filename.partial")
        val target = File(targetDir, filename)
        // Pre-delete any existing target so ATOMIC_MOVE (without REPLACE_EXISTING)
        // succeeds — matches LibraryFileStore.saveToFileHome's pattern.
        if (target.exists()) target.delete()
        return runCatching {
            srcFile.copyTo(partial, overwrite = true)
            try {
                java.nio.file.Files.move(
                    partial.toPath(), target.toPath(),
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE
                )
            } catch (_: Exception) {
                partial.delete()
                return@runCatching null
            }
            Uri.fromFile(target)
        }.getOrNull()
    }

    private fun sweepPartialsInFileDir(dir: File, filename: String) {
        dir.listFiles { _, name -> name == "$filename.partial" }?.forEach { it.delete() }
    }

    private suspend fun copyToSafHome(
        homeUri: Uri,
        srcFile: File,
        subdir: String,
        filename: String
    ): Uri? {
        // Walk subdir components within the tree, tracking the parent doc id
        // explicitly. Re-passing each returned doc URI as a "tree URI" to
        // findOrCreateChildDir collapses every segment back to root because
        // DocumentsContract.getTreeDocumentId always yields the tree's root
        // doc id, not the deeper one we just navigated into.
        var parentDocId = try {
            DocumentsContract.getTreeDocumentId(homeUri)
        } catch (_: Throwable) { return null }
        for (segment in subdir.split('/').filter { it.isNotEmpty() }) {
            parentDocId = findOrCreateChildDirDocId(homeUri, parentDocId, segment) ?: return null
        }
        val parentDocUri = DocumentsContract.buildDocumentUriUsingTree(homeUri, parentDocId)
        val parentDoc = DocumentFile.fromTreeUri(context, parentDocUri)
            ?: return null

        // Sweep matching partials.
        parentDoc.listFiles().filter { it.name == "$filename.partial" }.forEach { it.delete() }
        // Replace existing file with the same name.
        parentDoc.findFile(filename)?.delete()

        val partialName = "$filename.partial"
        val partialDoc = parentDoc.createFile("application/octet-stream", partialName) ?: return null
        val ok = context.contentResolver.openOutputStream(partialDoc.uri)?.use { out ->
            srcFile.inputStream().use { it.copyTo(out) }
            true
        } ?: false
        if (!ok) {
            partialDoc.delete()
            return null
        }
        val renamedUri = runCatching {
            DocumentsContract.renameDocument(context.contentResolver, partialDoc.uri, filename)
        }.getOrNull()
        // If renameDocument returns null, the provider either failed or (rare)
        // succeeded but signalled nothing. Deleting partialDoc.uri is safe: if
        // rename actually succeeded, the URI is already stale and the delete is
        // a no-op. The caller retries on the next launch.
        if (renamedUri == null) {
            partialDoc.delete()
            return null
        }
        return renamedUri
    }

    /**
     * Best-effort delete of the file or directory referenced by [uri]. Used to
     * complete a "move" — copy succeeded, now drop the source. file:// uses
     * [File.delete] / [File.deleteRecursively]; content:// uses
     * [DocumentsContract.deleteDocument]. Returns true if the underlying call
     * reported success; callers should not retry on false.
     */
    suspend fun deleteAt(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            when (uri.scheme) {
                "file" -> {
                    val f = uri.path?.let(::File) ?: return@withContext false
                    if (f.isDirectory) f.deleteRecursively() else f.delete()
                }
                "content" -> DocumentsContract.deleteDocument(context.contentResolver, uri)
                else -> false
            }
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Delete one regular file that sits inside the home folder, and nothing
     * else. This is what bookmark removal uses. Unlike [deleteAt], which a
     * storage migration calls on a whole old tree, this refuses directories,
     * the home folder itself, and any path that resolves outside it: the
     * URI it is handed comes from a resource_files row, and rows arrive from
     * synced snapshots as well as from this device.
     */
    suspend fun deleteFileUnderHome(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val home = homeFolderUri() ?: return@withContext false
        // The normal setup is a SAF (`content://`) home plus All Files Access, and
        // ResourceResolver's fast path then hands us a `file://` URI derived from
        // homeFolderFsRoot(). Resolve that same root so the mixed-scheme pair has a
        // filesystem home to be contained by; without it every such delete is refused
        // and the guide comes straight back on the next rescan.
        val homeFsRoot = if (uri.scheme == "file" && home.scheme == "content") homeFolderFsRoot() else null
        if (!isFileUnderHome(uri, home, homeFsRoot)) {
            android.util.Log.w("LibraryFolderManager", "refusing delete outside home or of a directory: $uri")
            return@withContext false
        }
        deleteAt(uri)
    }

    /**
     * True only for a regular file strictly inside [home].
     *
     * [homeFsRoot] is the filesystem root of [home] as resolved by
     * [homeFolderFsRoot]; it is only consulted for the mixed `file://` target /
     * `content://` home pair, which is what the All-Files fast path produces
     * against a SAF library home. Callers that cannot resolve it (or do not need
     * it) leave it null, and that pair then fails closed.
     */
    internal fun isFileUnderHome(uri: Uri, home: Uri, homeFsRoot: File? = null): Boolean = when {
        uri.scheme == "file" && home.scheme == "file" -> {
            val homeCanonical = home.path?.let(::File)?.canonicalFile
            val target = uri.path?.let(::File)?.canonicalFile
            homeCanonical != null && target != null &&
                target.isFile &&
                target.path.startsWith(homeCanonical.path + File.separator)
        }
        uri.scheme == "content" && home.scheme == "content" -> {
            val homeDocId = runCatching { DocumentsContract.getTreeDocumentId(home) }.getOrNull()
            val docId = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull()
            homeDocId != null && docId != null &&
                docId != homeDocId &&
                docId.startsWith("$homeDocId/") &&
                runCatching { DocumentFile.fromSingleUri(context, uri)?.isDirectory == false }.getOrDefault(false)
        }
        uri.scheme == "file" && home.scheme == "content" -> {
            val homeCanonical = homeFsRoot?.canonicalFile
            val target = uri.path?.let(::File)?.canonicalFile
            homeCanonical != null && target != null &&
                target.isFile &&
                target.path.startsWith(homeCanonical.path + File.separator)
        }
        else -> false
    }

    /**
     * Delete every immediate child of [treeUri]. Used by the "Reset library"
     * → "Also delete files" flow to wipe the storage folder while keeping the
     * folder itself intact (so the persistable SAF permission survives).
     * Returns the number of children that reported successful deletion.
     */
    suspend fun deleteAllChildrenIn(treeUri: Uri): Int = withContext(Dispatchers.IO) {
        var deleted = 0
        when (treeUri.scheme) {
            "file" -> {
                val root = File(treeUri.path ?: return@withContext 0)
                root.listFiles()?.forEach { child ->
                    val ok = if (child.isDirectory) child.deleteRecursively() else child.delete()
                    if (ok) deleted++
                }
            }
            "content" -> {
                val rootDoc = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext 0
                rootDoc.listFiles().forEach { child ->
                    if (try { child.delete() } catch (_: Throwable) { false }) deleted++
                }
            }
        }
        deleted
    }

    /**
     * Delete an immediate child directory of the home folder named [name].
     * Returns true if the directory didn't exist or was successfully deleted.
     * Returns false if the home folder is unset or deletion failed.
     */
    suspend fun deleteChildDir(name: String): Boolean = withContext(Dispatchers.IO) {
        val safeName = sanitizeForPath(name)
        val homeUri = homeFolderUri()
        if (homeUri == null) {
            android.util.Log.i("VAULT_RESIDUE", "deleteChildDir homeUri=null safeName='$safeName' name='$name'")
            return@withContext false
        }
        android.util.Log.i(
            "VAULT_RESIDUE",
            "deleteChildDir scheme=${homeUri.scheme} safeName='$safeName' name='$name' homeUri=$homeUri"
        )
        when (homeUri.scheme) {
            "file" -> {
                val home = homeUri.path?.let(::File) ?: return@withContext false
                val child = File(home, safeName).canonicalFile
                val homeCanonical = home.canonicalFile
                if (!child.path.startsWith(homeCanonical.path + File.separator)) {
                    android.util.Log.w("LibraryFolderManager",
                        "deleteChildDir refusing to delete outside home: $safeName")
                    return@withContext false
                }
                val existedBefore = child.exists()
                android.util.Log.i(
                    "VAULT_RESIDUE",
                    "deleteChildDir[file] path=${child.path} existedBefore=$existedBefore"
                )
                if (!existedBefore) return@withContext true
                val deleted = child.deleteRecursively()
                val stillExists = child.exists()
                android.util.Log.i(
                    "VAULT_RESIDUE",
                    "deleteChildDir[file] deleteRecursively=$deleted POST-CHECK still-exists=$stillExists"
                )
                deleted
            }
            "content" -> {
                val rootDoc = DocumentFile.fromTreeUri(context, homeUri)
                if (rootDoc == null) {
                    android.util.Log.i("VAULT_RESIDUE", "deleteChildDir[saf] rootDoc=null homeUri=$homeUri")
                    return@withContext false
                }
                // Case-insensitive lookup: the emulated FS is case-insensitive but
                // SAF findFile is case-sensitive, so a casing drift between the
                // stored folder name and the on-disk dir would otherwise "treat as
                // already gone" and strand the folder (the resurrection bug).
                val child = rootDoc.findChildIgnoreCase(safeName)
                if (child == null) {
                    android.util.Log.i(
                        "VAULT_RESIDUE",
                        "deleteChildDir[saf] findChildIgnoreCase='$safeName' returned null (treating as already gone)"
                    )
                    return@withContext true
                }
                val isDir = child.isDirectory
                val childCount = if (isDir) try { child.listFiles().size } catch (_: Throwable) { -1 } else -1
                android.util.Log.i(
                    "VAULT_RESIDUE",
                    "deleteChildDir[saf] found=true isDirectory=$isDir childCount=$childCount uri=${child.uri}"
                )
                val deleteReturned = try {
                    child.delete()
                } catch (t: Throwable) {
                    android.util.Log.i("VAULT_RESIDUE", "deleteChildDir[saf] delete() threw: ${t.message}")
                    false
                }
                // Re-resolve from the tree (fresh DocumentFile) so we don't trust a cached node.
                val postCheck = DocumentFile.fromTreeUri(context, homeUri)?.findChildIgnoreCase(safeName)
                val stillExists = postCheck != null
                val postChildCount = if (stillExists && postCheck!!.isDirectory) {
                    try { postCheck.listFiles().size } catch (_: Throwable) { -1 }
                } else -1
                android.util.Log.i(
                    "VAULT_RESIDUE",
                    "deleteChildDir[saf] delete()=$deleteReturned POST-CHECK still-exists=$stillExists postChildCount=$postChildCount"
                )
                deleteReturned
            }
            else -> false
        }
    }

    /** Returns true if the file referenced by [uri] currently exists. */
    suspend fun exists(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        when (uri.scheme) {
            "file" -> uri.path?.let(::File)?.exists() == true
            "content" -> {
                // ContentResolver.query works for both single-document and tree-document URIs;
                // returns 0 rows for missing files. DocumentFile.fromSingleUri does NOT work
                // reliably for tree-document URIs returned by DocumentFile.findFile traversal.
                try {
                    context.contentResolver.query(
                        uri,
                        arrayOf(android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID),
                        null, null, null
                    )?.use { cursor -> cursor.count > 0 } ?: false
                } catch (_: Throwable) {
                    false
                }
            }
            else -> false
        }
    }
}
