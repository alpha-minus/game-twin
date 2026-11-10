package com.twinlore.app.data.esde

import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract

/**
 * Direct filesystem access to the ES-DE folder. When the user has granted All-Files access,
 * library artwork resolution ([com.twinlore.app.data.library.ResourceResolver]), the Game
 * Stats screen, and [ESDEArtworkResolver] read artwork straight off disk via java.io.File
 * through here instead of SAF. Without the permission, each of those falls back to SAF.
 */
object EsdeFsAccess {
    /** True when direct java.io.File access to shared storage is available. */
    fun isGranted(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()

    /** Absolute filesystem path of the ES-DE folder behind [folderUri], or null if underivable. */
    fun rootPath(folderUri: Uri): String? {
        val docId = try {
            DocumentsContract.getTreeDocumentId(folderUri)
        } catch (_: Throwable) { return null }
        return docIdToFsPath(docId)
    }

    /**
     * Pure: SAF tree document id → filesystem path.
     * "primary:ES-DE" → /storage/emulated/0/ES-DE ; "1A2B-3C4D:ES-DE" → /storage/1A2B-3C4D/ES-DE.
     */
    fun docIdToFsPath(docId: String): String? {
        val colon = docId.indexOf(':')
        if (colon < 0) return null
        val volume = docId.substring(0, colon)
        val relative = docId.substring(colon + 1)
        val base = if (volume.equals("primary", ignoreCase = true)) "/storage/emulated/0" else "/storage/$volume"
        return if (relative.isEmpty()) base else "$base/$relative"
    }
}
