package com.twinlore.app.data.codewheel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class CodeWheelSlimmerTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun makeZip(file: File, entries: Map<String, ByteArray>) {
        ZipOutputStream(file.outputStream()).use { zos ->
            entries.forEach { (name, bytes) ->
                zos.putNextEntry(ZipEntry(name))
                zos.write(bytes)
                zos.closeEntry()
            }
        }
    }

    private fun names(zip: File): Set<String> =
        ZipFile(zip).use { z -> z.entries().asSequence().map { it.name }.toSet() }

    @Test fun `keeps web assets and drops source bloat`() {
        val src = tmp.newFile("src.zip")
        makeZip(src, mapOf(
            "wheel.htm" to "<html></html>".toByteArray(),
            "art_wheel0.png" to byteArrayOf(1, 2, 3),
            "source.psd" to ByteArray(1024),
            "scan.tif" to ByteArray(2048),
        ))
        val dest = tmp.newFile("dest.zip").also { it.delete() }

        val hasHtml = CodeWheelSlimmer.slim(src, dest)

        assertTrue(hasHtml)
        assertEquals(setOf("wheel.htm", "art_wheel0.png"), names(dest))
    }

    @Test fun `rejects zip-slip entries`() {
        val src = tmp.newFile("evil.zip")
        makeZip(src, mapOf(
            "ok.html" to "<html></html>".toByteArray(),
            "../escape.html" to "<html></html>".toByteArray(),
        ))
        val dest = tmp.newFile("dest2.zip").also { it.delete() }

        CodeWheelSlimmer.slim(src, dest)

        assertEquals(setOf("ok.html"), names(dest))
    }

    @Test fun `returns false when no html survives`() {
        val src = tmp.newFile("nohtml.zip")
        makeZip(src, mapOf("only.psd" to ByteArray(16)))
        val dest = tmp.newFile("dest3.zip").also { it.delete() }

        assertFalse(CodeWheelSlimmer.slim(src, dest))
    }
}
