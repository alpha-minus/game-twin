package com.twinlore.app.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Thin seam over SAF [ContentResolver] queries.
 *
 * Two operations the validator needs:
 *  - [hasPersistedPermission]: synchronous, in-memory check of [ContentResolver.getPersistedUriPermissions].
 *  - [probeIsDirectory]: single cursor query asking whether the URI still resolves to a directory.
 *
 * String-based to keep [PersistedFolderValidatorTest] JVM-pure (no Robolectric needed).
 */
interface UriProbe {
    fun hasPersistedPermission(uriString: String): Boolean

    /**
     * True when [uriString] is still covered by a grant this app holds -- either the
     * exact URI, or a tree URI it hangs off. Unlike [hasPersistedPermission] this
     * accepts the `.../tree/<tree>/document/<doc>` URIs stored on `resource_files`
     * rows, whose covering grant is the bare tree.
     *
     * Used to tell "the file is gone" apart from "we lost the grant that let us look",
     * which are indistinguishable at the ContentResolver (both surface as an empty
     * cursor or a SecurityException) but must never be treated alike: a reinstall
     * drops every persisted grant, and deleting rows on that basis throws away links
     * to files that are still sitting on disk.
     */
    fun hasCoveringPersistedPermission(uriString: String): Boolean

    /**
     * Returns true if the URI resolves to an accessible directory.
     * Returns false if the cursor is empty or the mime type isn't a directory.
     * Any exception (I/O failure, [SecurityException], malformed URI, etc.) is propagated
     * to the validator, which treats it as "URI lost" via outer runCatching + withTimeoutOrNull.
     * Suspending so the validator's [kotlinx.coroutines.withTimeoutOrNull] can bound it
     * and so tests can `delay()` under virtual time.
     */
    suspend fun probeIsDirectory(uriString: String): Boolean
}

@Singleton
class DocumentsContractUriProbe @Inject constructor(
    @ApplicationContext private val context: Context,
) : UriProbe {

    override fun hasPersistedPermission(uriString: String): Boolean {
        val target = runCatching { Uri.parse(uriString) }.getOrNull() ?: return false
        return context.contentResolver.persistedUriPermissions.any { it.uri == target }
    }

    override fun hasCoveringPersistedPermission(uriString: String): Boolean {
        val target = runCatching { Uri.parse(uriString) }.getOrNull() ?: return false
        val targetStr = target.toString()
        return context.contentResolver.persistedUriPermissions.any { held ->
            val heldStr = held.uri.toString()
            // The trailing separator keeps a grant on ".../tree/primary%3AROMs" from
            // appearing to cover ".../tree/primary%3AROMs2".
            targetStr == heldStr || targetStr.startsWith("$heldStr/")
        }
    }

    override suspend fun probeIsDirectory(uriString: String): Boolean =
        withContext(Dispatchers.IO) {
            val treeUri = Uri.parse(uriString)
            val docId = DocumentsContract.getTreeDocumentId(treeUri)
            val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
            val projection = arrayOf(DocumentsContract.Document.COLUMN_MIME_TYPE)
            context.contentResolver
                .query(documentUri, projection, null, null, null)
                ?.use { cursor ->
                    if (!cursor.moveToFirst()) return@use false
                    cursor.getString(0) == DocumentsContract.Document.MIME_TYPE_DIR
                } ?: false
        }
}
