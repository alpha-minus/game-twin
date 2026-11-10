package com.twinlore.app.data.codewheel

import android.util.Log
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CodeWheelCacheManager @Inject constructor() {

    private val webExtensions = setOf(
        "html", "htm", "css", "js", "png", "jpg", "jpeg", "gif", "svg", "webp",
        "bmp", "ico", "woff", "woff2", "ttf", "otf", "eot", "mp3", "ogg", "wav",
        "m4a", "json", "xml",
    )

    /** Extracts [zipStream] into [bookmarkDir]; returns the entry HTM, or null. */
    fun extract(zipStream: InputStream, bookmarkDir: File): File? {
        bookmarkDir.deleteRecursively()
        bookmarkDir.mkdirs()
        val root = bookmarkDir.canonicalFile
        val htmlFiles = mutableListOf<File>()

        ZipInputStream(zipStream).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val ext = entry.name.substringAfterLast('.', "").lowercase()
                    val target = File(bookmarkDir, entry.name).canonicalFile
                    val inside = target.path == root.path ||
                        target.path.startsWith(root.path + File.separator)
                    if (ext in webExtensions && inside) {
                        target.parentFile?.mkdirs()
                        target.outputStream().use { zis.copyTo(it) }
                        if (ext == "htm" || ext == "html") htmlFiles.add(target)
                    } else if (!inside) {
                        Log.w(TAG, "Skipping zip-slip entry: ${entry.name}")
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        if (htmlFiles.isEmpty()) return null
        // Prefer a root-level htm whose name hints at the wheel; else first by name.
        val rootLevel = htmlFiles.filter { it.parentFile?.canonicalFile == root }
            .ifEmpty { htmlFiles }
        return rootLevel.sortedBy { it.name.lowercase() }
            .firstOrNull { it.name.lowercase().let { n -> "wheel" in n || "code" in n } }
            ?: rootLevel.minByOrNull { it.name.lowercase() }
    }

    companion object { private const val TAG = "CodeWheelCache" }
}
