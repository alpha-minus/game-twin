package com.twinlore.app.data.codewheel

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/** Recognizes an HTML-bundle zip (code wheel or generic) for the download /
 *  add-file on-ramps. Pure file IO — no Android dependencies. */
object HtmlZipImporter {

    sealed interface Outcome {
        data class Bundle(val slimZip: File, val fileType: String) : Outcome
        object NotAnHtmlBundle : Outcome
    }

    private val WHEEL_MARKERS = listOf(
        "jquery.rotate", "jqueryrotate", "animatewheel", "wheel_w",
        "interactive-code-wheel", "dial-a-pirate", "target.rotate(",
    )

    /** Slim [srcZip] into [destZip]; classify the result. */
    fun classify(srcZip: File, destZip: File): Outcome {
        val hasHtml = CodeWheelSlimmer.slim(srcZip, destZip)
        if (!hasHtml) {
            destZip.delete()
            return Outcome.NotAnHtmlBundle
        }
        val isWheel = sniffWheel(destZip)
        return Outcome.Bundle(destZip, if (isWheel) "wheel" else "htmlzip")
    }

    private fun sniffWheel(zip: File): Boolean {
        ZipFile(zip).use { zf ->
            val entry = pickEntry(zf) ?: return false
            val text = zf.getInputStream(entry).bufferedReader().use { it.readText() }.lowercase()
            return WHEEL_MARKERS.any { it in text }
        }
    }

    // Same entry-selection rule as CodeWheelCacheManager: root-level .htm/.html,
    // prefer a name containing "wheel"/"code", else first by name.
    private fun pickEntry(zf: ZipFile): ZipEntry? {
        val htmls = zf.entries().asSequence()
            .filter { !it.isDirectory }
            .filter { val e = it.name.substringAfterLast('.', "").lowercase(); e == "htm" || e == "html" }
            .toList()
        if (htmls.isEmpty()) return null
        val rootLevel = htmls.filter { !it.name.trimEnd('/').contains('/') }.ifEmpty { htmls }
        return rootLevel.sortedBy { it.name.lowercase() }
            .firstOrNull { val n = it.name.lowercase(); "wheel" in n || "code" in n }
            ?: rootLevel.minByOrNull { it.name.lowercase() }
    }
}
