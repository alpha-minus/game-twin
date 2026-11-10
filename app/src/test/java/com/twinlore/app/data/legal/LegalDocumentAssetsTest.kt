package com.twinlore.app.data.legal

import com.twinlore.app.RepoFiles
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LegalDocumentAssetsTest {

    private fun text(doc: LegalDocument, german: Boolean): String {
        val file = RepoFiles.asset(doc.assetPath(german))
        assertTrue("missing asset: ${doc.assetPath(german)}", file.isFile)
        return file.readText()
    }

    private val shipped = LegalDocument.entries

    @Test
    fun `every document ships in both languages and is not empty`() {
        for (doc in shipped) {
            for (german in listOf(true, false)) {
                assertTrue(
                    "${doc.name} (german=$german) is empty",
                    text(doc, german).trim().length > 100,
                )
            }
        }
    }

    @Test
    fun `the impressum cites the DDG and not the repealed TMG`() {
        val de = text(LegalDocument.IMPRESSUM, german = true)
        assertTrue("must cite § 5 DDG", de.contains("§ 5 DDG"))
        assertFalse("TMG was repealed on 14 May 2024", de.contains("TMG"))
    }

    @Test
    fun `the impressum carries the mandatory contact and VAT fields`() {
        val de = text(LegalDocument.IMPRESSUM, german = true)
        assertTrue(de.contains("Mert Aslantürk"))
        assertTrue("VAT ID is mandatory under § 5 Abs. 1 Nr. 6 DDG", de.contains("DE463889811"))
        assertTrue(de.contains("hello@game-twin.app"))
        assertTrue("§ 27a UStG reference", de.contains("§ 27a UStG"))
    }

    @Test
    fun `the old personal gmail address is gone from every legal text`() {
        for (doc in shipped) {
            for (german in listOf(true, false)) {
                assertFalse(
                    "${doc.name} still contains the old address",
                    text(doc, german).contains("twinlore.soft@gmail.com"),
                )
            }
        }
    }

    /** The ODR platform shut down on 20 July 2025; linking it is now wrong, not merely stale. */
    @Test
    fun `no legal text links the retired EU ODR platform`() {
        for (doc in shipped) {
            for (german in listOf(true, false)) {
                assertFalse(
                    "${doc.name} links the retired ODR platform",
                    text(doc, german).contains("ec.europa.eu/consumers/odr"),
                )
            }
        }
    }

    @Test
    fun `no user-facing legal text uses an em dash`() {
        for (doc in shipped) {
            for (german in listOf(true, false)) {
                assertFalse(
                    "${doc.name} (german=$german) contains an em dash",
                    text(doc, german).contains('—'),
                )
            }
        }
    }
}
