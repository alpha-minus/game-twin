package com.twinlore.app.data.codewheel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class CodeWheelCacheManagerTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun zipBytes(entries: Map<String, ByteArray>): ByteArray {
        val bos = java.io.ByteArrayOutputStream()
        ZipOutputStream(bos).use { zos ->
            entries.forEach { (n, b) ->
                zos.putNextEntry(ZipEntry(n)); zos.write(b); zos.closeEntry()
            }
        }
        return bos.toByteArray()
    }

    @Test fun `extracts assets and finds wheel entry`() {
        val bytes = zipBytes(mapOf(
            "Monkey Island_extra_interactive-code-wheel.htm" to "<html></html>".toByteArray(),
            "wheel0.png" to byteArrayOf(1, 2, 3),
        ))
        val dir = tmp.newFolder("cache")
        val mgr = CodeWheelCacheManager()

        val entry = mgr.extract(bytes.inputStream(), dir)

        assertNotNull(entry)
        assertTrue(entry!!.name.endsWith(".htm"))
        assertTrue(File(dir, "wheel0.png").exists())
    }

    @Test fun `returns null when no html present`() {
        val bytes = zipBytes(mapOf("img.png" to byteArrayOf(9)))
        val dir = tmp.newFolder("cache2")
        assertEquals(null, CodeWheelCacheManager().extract(bytes.inputStream(), dir))
    }

    @Test fun `zip-slip entry is skipped`() {
        val bytes = zipBytes(mapOf("../escape.html" to "<html></html>".toByteArray()))
        val dir = tmp.newFolder("cache3")
        val result = CodeWheelCacheManager().extract(bytes.inputStream(), dir)
        assertEquals(null, result)
        assertFalse(File(dir.parentFile, "escape.html").exists())
    }
}
