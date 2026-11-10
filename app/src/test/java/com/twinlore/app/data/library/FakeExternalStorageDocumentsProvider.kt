package com.twinlore.app.data.library

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.DocumentsContract

/**
 * Minimal SAF document provider stub, registered with Robolectric for tests
 * only.
 *
 * [DocumentFile.fromSingleUri]'s `isDirectory` reads the "mime_type" column
 * for the single document URI via a real `ContentResolver.query`. With no
 * provider registered for the authority, Robolectric's query returns null and
 * `DocumentFile` reports every document as a non-directory -- which is why
 * the [LibraryFolderManager.isFileUnderHome] SAF directory guard cannot be
 * exercised without this stub. It answers that one query using a per-test
 * mime-type map keyed by document id (set by the test before the call),
 * defaulting to an ordinary file mime type for anything unregistered.
 */
class FakeExternalStorageDocumentsProvider : ContentProvider() {

    companion object {
        const val AUTHORITY = "com.android.externalstorage.documents"

        /** documentId -> mime type, cleared and populated by each test that needs it. */
        val mimeTypesByDocId = mutableMapOf<String, String>()
    }

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val docId = DocumentsContract.getDocumentId(uri)
        val mimeType = mimeTypesByDocId[docId] ?: "text/html"
        return MatrixCursor(arrayOf(DocumentsContract.Document.COLUMN_MIME_TYPE)).apply {
            addRow(arrayOf(mimeType))
        }
    }

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0
}
