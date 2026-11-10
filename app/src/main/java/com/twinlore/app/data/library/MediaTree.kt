package com.twinlore.app.data.library

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import java.io.File

/**
 * One directory entry with everything [MediaIndexer]'s walkers read, fetched in the same
 * pass as the listing. [location] is what lands in `media_search_index.absolute_path`: the
 * absolute path under a `file://` root, the document URI string under a SAF tree.
 */
internal class MediaTreeEntry(
    val name: String,
    val isDirectory: Boolean,
    val isFile: Boolean,
    val size: Long,
    val mtime: Long,
    val location: String,
    val handle: Any,
)

/**
 * The directory listing [MediaIndexer] walks a search path through.
 *
 * Exists because of the SAF case. DocumentFile answers every property (name, type, size,
 * mtime) with its own ContentResolver query, so a walk that read five properties per file
 * cost about five binder round trips per file: ~2,200 queries for a 400-image test tree,
 * tens of thousands for a real ES-DE downloaded_media folder. That walk ran in full the
 * first time a freshly picked folder was scanned, which is why the first local-artwork
 * lookup after a folder pick took minutes and every later one was quick.
 * [SafMediaTree] reads a whole directory, with every column, in one children query.
 */
internal abstract class MediaTree {
    abstract val root: MediaTreeEntry

    /** Children queries (or listFiles calls) issued so far. Feeds the scan timing log. */
    var listings = 0
        private set

    fun list(dir: MediaTreeEntry): List<MediaTreeEntry> {
        listings++
        return listChildren(dir)
    }

    protected abstract fun listChildren(dir: MediaTreeEntry): List<MediaTreeEntry>

    /**
     * The subdirectories of [dir] named in [names]. Defaults to one listing; [FileMediaTree]
     * overrides it with direct stats so an unchanged directory is never listed.
     */
    open fun subdirs(dir: MediaTreeEntry, names: Set<String>): List<MediaTreeEntry> =
        list(dir).filter { it.isDirectory && it.name in names }

    companion object {
        /** The tree behind a search-path URI, or null for an unusable one. */
        fun forUri(resolver: ContentResolver, uri: Uri): MediaTree? = when (uri.scheme) {
            "file" -> uri.path?.let { File(it) }?.takeIf { it.isDirectory }?.let(::FileMediaTree)
            "content" -> SafMediaTree.create(resolver, uri)
            else -> null
        }
    }
}

internal class FileMediaTree(rootDir: File) : MediaTree() {
    override val root = entry(rootDir)

    override fun listChildren(dir: MediaTreeEntry): List<MediaTreeEntry> =
        (dir.handle as File).listFiles().orEmpty().map(::entry)

    override fun subdirs(dir: MediaTreeEntry, names: Set<String>): List<MediaTreeEntry> =
        names.map { File(dir.handle as File, it) }.filter { it.isDirectory }.map(::entry)

    private fun entry(f: File) = MediaTreeEntry(
        name = f.name,
        isDirectory = f.isDirectory,
        isFile = f.isFile,
        size = f.length(),
        mtime = f.lastModified(),
        location = f.absolutePath,
        handle = f,
    )
}

/**
 * A SAF tree read through [DocumentsContract] children queries with a full projection.
 *
 * Document URIs are built the way DocumentFile 1.0.1 builds them (fromTreeUri, then
 * buildDocumentUriUsingTree per child), so the strings stored in the index are identical
 * to the ones earlier builds stored. resource_files rows fingerprint on that string, so a
 * different spelling would register every local link a second time.
 */
internal class SafMediaTree private constructor(
    private val resolver: ContentResolver,
    private val rootDocUri: Uri,
    rootDocId: String,
) : MediaTree() {

    override val root = MediaTreeEntry(
        name = "",
        isDirectory = true,
        isFile = false,
        size = 0L,
        // DocumentFile.lastModified() read the same column and also fell back to 0.
        mtime = queryLastModified(),
        location = rootDocUri.toString(),
        handle = rootDocId,
    )

    override fun listChildren(dir: MediaTreeEntry): List<MediaTreeEntry> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(rootDocUri, dir.handle as String)
        val out = mutableListOf<MediaTreeEntry>()
        try {
            resolver.query(childrenUri, PROJECTION, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val docId = c.getString(0) ?: continue
                    // DocumentFile.getName() returned null here and the walkers skipped it.
                    val name = c.getString(1) ?: continue
                    val mime = c.getString(2)
                    val isDir = mime == DocumentsContract.Document.MIME_TYPE_DIR
                    out += MediaTreeEntry(
                        name = name,
                        isDirectory = isDir,
                        // DocumentFile.isFile(): any non-empty type that is not a directory.
                        isFile = !isDir && !mime.isNullOrEmpty(),
                        size = if (c.isNull(3)) 0L else c.getLong(3),
                        mtime = if (c.isNull(4)) 0L else c.getLong(4),
                        location = DocumentsContract.buildDocumentUriUsingTree(rootDocUri, docId).toString(),
                        handle = docId,
                    )
                }
            }
        } catch (e: Exception) {
            // Same outcome as DocumentFile.listFiles(), which logged and returned what it had.
            android.util.Log.w("MediaIndexer", "children query failed for $childrenUri", e)
        }
        return out
    }

    private fun queryLastModified(): Long = try {
        resolver.query(
            rootDocUri, arrayOf(DocumentsContract.Document.COLUMN_LAST_MODIFIED), null, null, null,
        )?.use { c -> if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else 0L } ?: 0L
    } catch (_: Exception) {
        0L
    }

    companion object {
        private val PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )

        /**
         * Accepts a plain tree URI and a document-under-tree URI alike: AUTO_ESDE rows store
         * `tree.findFile("downloaded_media").uri`, which is the latter. Path segments are read
         * directly rather than through DocumentsContract.isDocumentUri, whose provider lookup
         * adds a PackageManager call and gives the same answer for any tree URI.
         */
        fun create(resolver: ContentResolver, uri: Uri): SafMediaTree? = try {
            val segs = uri.pathSegments
            val docId = if (segs.size >= 4 && segs[0] == "tree" && segs[2] == "document") {
                DocumentsContract.getDocumentId(uri)
            } else {
                DocumentsContract.getTreeDocumentId(uri)
            }
            SafMediaTree(resolver, DocumentsContract.buildDocumentUriUsingTree(uri, docId), docId)
        } catch (_: Exception) {
            null
        }
    }
}

/**
 * A DocumentFile-backed tree. Pays DocumentFile's per-property queries, so production walks
 * use [SafMediaTree]; this keeps [MediaIndexer.rebuildOneFromDocumentFile] for tests.
 */
internal class DocumentFileMediaTree(rootDoc: DocumentFile) : MediaTree() {
    override val root = MediaTreeEntry(
        name = "",
        isDirectory = true,
        isFile = false,
        size = 0L,
        mtime = rootDoc.lastModified(),
        location = rootDoc.uri.toString(),
        handle = rootDoc,
    )

    override fun listChildren(dir: MediaTreeEntry): List<MediaTreeEntry> =
        (dir.handle as DocumentFile).listFiles().mapNotNull { child ->
            val name = child.name ?: return@mapNotNull null
            MediaTreeEntry(
                name = name,
                isDirectory = child.isDirectory,
                isFile = child.isFile,
                size = child.length(),
                mtime = child.lastModified(),
                location = child.uri.toString(),
                handle = child,
            )
        }
}
