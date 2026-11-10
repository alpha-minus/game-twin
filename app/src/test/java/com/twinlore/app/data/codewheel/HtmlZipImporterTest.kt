package com.twinlore.app.data.codewheel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class HtmlZipImporterTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun makeZip(file: File, entries: Map<String, ByteArray>) {
        ZipOutputStream(file.outputStream()).use { zos ->
            entries.forEach { (name, bytes) ->
                zos.putNextEntry(ZipEntry(name)); zos.write(bytes); zos.closeEntry()
            }
        }
    }

    @Test fun `wheel markers classify as wheel`() {
        val src = tmp.newFile("w.zip")
        makeZip(src, mapOf(
            "wheel.htm" to "<html><script>jQuery.rotate; AnimateWheel(1)</script></html>".toByteArray(),
            "art_wheel0.png" to byteArrayOf(1, 2, 3),
        ))
        val dest = tmp.newFile("wslim.zip").also { it.delete() }

        val outcome = HtmlZipImporter.classify(src, dest)

        assertTrue(outcome is HtmlZipImporter.Outcome.Bundle)
        assertEquals("wheel", (outcome as HtmlZipImporter.Outcome.Bundle).fileType)
        assertTrue(dest.exists())
    }

    @Test fun `plain html classifies as htmlzip`() {
        val src = tmp.newFile("p.zip")
        makeZip(src, mapOf(
            "index.html" to "<html><body><h1>Saved page</h1></body></html>".toByteArray(),
            "style.css" to "body{}".toByteArray(),
        ))
        val dest = tmp.newFile("pslim.zip").also { it.delete() }

        val outcome = HtmlZipImporter.classify(src, dest)

        assertEquals("htmlzip", (outcome as HtmlZipImporter.Outcome.Bundle).fileType)
    }

    @Test fun `zip with no html is not a bundle`() {
        val src = tmp.newFile("n.zip")
        makeZip(src, mapOf("only.png" to byteArrayOf(9)))
        val dest = tmp.newFile("nslim.zip").also { it.delete() }

        assertEquals(HtmlZipImporter.Outcome.NotAnHtmlBundle, HtmlZipImporter.classify(src, dest))
        assertTrue("destZip deleted when not a bundle", !dest.exists())
    }
}
