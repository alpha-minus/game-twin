package com.twinlore.app.data.codewheel

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

object CodeWheelSlimmer {
    private val WEB_EXTENSIONS = setOf(
        "html", "htm", "css", "js", "png", "jpg", "jpeg", "gif", "svg", "webp",
        "bmp", "ico", "woff", "woff2", "ttf", "otf", "eot", "mp3", "ogg", "wav",
        "m4a", "json", "xml",
    )

    /** Returns true iff at least one .htm/.html entry was written to [destZip]. */
    fun slim(srcZip: File, destZip: File): Boolean {
        var htmlWritten = false
        ZipFile(srcZip).use { zf ->
            ZipOutputStream(destZip.outputStream()).use { zos ->
                val entries = zf.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.isDirectory) continue
                    val name = entry.name
                    if (!isSafe(name)) continue
                    val ext = name.substringAfterLast('.', "").lowercase()
                    if (ext !in WEB_EXTENSIONS) continue
                    zos.putNextEntry(ZipEntry(name))
                    zf.getInputStream(entry).use { it.copyTo(zos) }
                    zos.closeEntry()
                    if (ext == "htm" || ext == "html") htmlWritten = true
                }
            }
        }
        return htmlWritten
    }

    // Reject absolute paths and any entry that escapes the root via "..".
    private fun isSafe(name: String): Boolean {
        if (name.startsWith("/") || name.startsWith("\\")) return false
        val normalized = File("/root", name).canonicalPath
        return normalized.startsWith(File("/root").canonicalPath + File.separator)
    }
}
