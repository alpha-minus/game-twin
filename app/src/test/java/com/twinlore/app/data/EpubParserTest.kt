package com.twinlore.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class EpubParserTest {

    @get:Rule val tmp = TemporaryFolder()

    private fun fixture(name: String): File {
        val resource = javaClass.classLoader!!.getResource("epub/$name")
            ?: error("Fixture not found: epub/$name")
        return File(resource.toURI())
    }

    @Test fun `resolveSpine returns ordered chapter hrefs for EPUB 3 fixture`() {
        val parser = EpubParser()
        val spine = parser.resolveSpine(fixture("minimal-epub3.epub"))
        assertNotNull(spine)
        spine!!
        assertEquals(2, spine.chapters.size)
        assertEquals("ch1.xhtml", spine.chapters[0].opfRelativeHref)
        assertEquals("ch2.xhtml", spine.chapters[1].opfRelativeHref)
        assertEquals("OEBPS/", spine.opfBaseInArchive)
    }

    @Test fun `resolveSpine works for EPUB 2 fixture`() {
        val parser = EpubParser()
        val spine = parser.resolveSpine(fixture("minimal-epub2.epub"))
        assertNotNull(spine)
        assertEquals(2, spine!!.chapters.size)
        assertEquals("ch1.html", spine.chapters[0].opfRelativeHref)
        assertEquals("ch2.html", spine.chapters[1].opfRelativeHref)
        assertEquals("OEBPS/", spine.opfBaseInArchive)
    }

    @Test fun `parse extracts assets and concatenates spine`() {
        val parser = EpubParser()
        val cacheDir = tmp.newFolder("assets")
        val result = parser.parse(fixture("minimal-epub3.epub"), cacheDir)
        assertNotNull(result)
        result!!
        // Concatenated HTML contains both chapters
        assertTrue("Should contain ch1 title", result.html.contains("Chapter One"))
        assertTrue("Should contain ch2 title", result.html.contains("Chapter Two"))
        // Each chapter is wrapped in <section class="toc-heading" id="sec-N" data-title="...">
        assertTrue("section id sec-0", result.html.contains("id=\"sec-0\""))
        assertTrue("section id sec-1", result.html.contains("id=\"sec-1\""))
        assertTrue("body marker", result.html.contains("data-gamefaqs-converted=\"true\""))
        // PNG was extracted to cacheDir
        assertTrue("png extracted", File(cacheDir, "img/test.png").exists())
        // Manifest maps the original href to the extracted asset path
        assertTrue(
            "manifest contains image mapping",
            result.manifest.values.any { it.endsWith("img/test.png") }
        )
    }

    @Test fun `parse rejects encrypted EPUBs`() {
        // Build a tiny "encrypted" EPUB by adding META-INF/encryption.xml to a copy
        val src = fixture("minimal-epub3.epub")
        val out = File(tmp.newFolder(), "encrypted.epub")
        out.outputStream().use { fos ->
            java.util.zip.ZipOutputStream(fos).use { zos ->
                java.util.zip.ZipFile(src).use { zin ->
                    val entries = zin.entries()
                    while (entries.hasMoreElements()) {
                        val e = entries.nextElement()
                        // Always use DEFLATED to avoid having to pre-set size/crc for STORED entries.
                        val newEntry = java.util.zip.ZipEntry(e.name).apply {
                            method = java.util.zip.ZipEntry.DEFLATED
                        }
                        zos.putNextEntry(newEntry)
                        zin.getInputStream(e).copyTo(zos)
                        zos.closeEntry()
                    }
                }
                zos.putNextEntry(java.util.zip.ZipEntry("META-INF/encryption.xml"))
                zos.write("<encrypted/>".toByteArray())
                zos.closeEntry()
            }
        }
        val parser = EpubParser()
        val result = parser.parse(out, tmp.newFolder())
        assertEquals(null, result)
    }

    @Test fun `parse uses EPUB 2 toc-ncx labels`() {
        val parser = EpubParser()
        val cacheDir = tmp.newFolder("assets")
        val result = parser.parse(fixture("minimal-epub2.epub"), cacheDir)
        assertNotNull(result)
        result!!
        // NCX provides "Chapter One" / "Chapter Two" labels
        assertTrue("nav label propagates to data-title", result.html.contains("data-title=\"Chapter One\""))
        assertTrue("nav label propagates to data-title", result.html.contains("data-title=\"Chapter Two\""))
    }
}
