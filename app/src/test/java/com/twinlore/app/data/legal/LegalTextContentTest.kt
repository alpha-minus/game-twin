package com.twinlore.app.data.legal

import com.twinlore.app.RepoFiles
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the elements these documents are legally required to contain. It cannot judge whether
 * the prose is any good, but it can stop a required element from being dropped in an edit.
 */
class LegalTextContentTest {

    private fun text(doc: LegalDocument, german: Boolean): String =
        RepoFiles.asset(doc.assetPath(german)).readText()

    @Test
    fun `the privacy policy names the controller and the supervisory authority`() {
        val de = text(LegalDocument.PRIVACY, german = true)
        assertTrue("Art. 13 requires the controller", de.contains("Verantwortlicher"))
        assertTrue(de.contains("Mert Aslantürk"))
        assertTrue(
            "the competent authority must be nameable",
            de.contains("Berliner Beauftragte für Datenschutz und Informationsfreiheit"),
        )
    }

    @Test
    fun `the privacy policy discloses the Lemon Squeezy transfer`() {
        for (german in listOf(true, false)) {
            val t = text(LegalDocument.PRIVACY, german)
            assertTrue("recipient must be named", t.contains("Lemon Squeezy"))
            assertTrue("the hashed device id must be disclosed", t.contains("Android"))
        }
    }

    /**
     * GeminiClient posts the user's message text and the most recent attached image to
     * generativelanguage.googleapis.com. An Art. 13 notice that omits it is wrong, and the
     * omission is invisible without an assertion because nothing else in the policy mentions
     * Google.
     */
    @Test
    fun `the privacy policy discloses the AI assistant transfer to Google`() {
        for (german in listOf(true, false)) {
            val t = text(LegalDocument.PRIVACY, german)
            assertTrue("the recipient must be named", t.contains("Google"))
            assertTrue("the feature must be identifiable", t.contains("Gemini"))
            assertTrue("it needs its own processing block", t.contains("3.7"))
        }
    }

    /**
     * ANDROID_ID is read at exactly one site, supporter-key activation, and section 5 says so.
     * The library snapshot's id is a random UUID (DeviceIdMigrator moved old installs off the
     * Android ID); the policy describes it in section 3.6 as a plain fact about the snapshot
     * file, since the provider never receives it and no § 25 TDDG reasoning is needed for it.
     * The policy must keep disclosing the identifier and its random, non-device-derived nature.
     */
    @Test
    fun `the policy discloses the snapshot identifier and its random nature`() {
        val de = text(LegalDocument.PRIVACY, german = true)
        assertTrue(de.contains("Bibliotheks-Sicherung"))
        assertTrue("its random nature must be stated", de.contains("Zufallskennung"))
        val en = text(LegalDocument.PRIVACY, german = false)
        assertTrue(en.contains("library snapshot"))
        assertTrue("its random nature must be stated", en.contains("random identifier"))
    }

    @Test
    fun `the privacy policy states the legal bases it relies on`() {
        val de = text(LegalDocument.PRIVACY, german = true)
        assertTrue(de.contains("Art. 6 Abs. 1 lit. b"))
        assertTrue(de.contains("Art. 6 Abs. 1 lit. f"))
        assertTrue("terminal equipment access needs its reasoning", de.contains("§ 25"))
    }

    @Test
    fun `the terms carry a liability clause that survives section 309 Nr 7 BGB`() {
        val de = text(LegalDocument.TERMS, german = true)
        assertTrue("intent and gross negligence", de.contains("grobe Fahrlässigkeit"))
        assertTrue("life, body, health", de.contains("Leben, Körper oder Gesundheit"))
        assertTrue("product liability act", de.contains("Produkthaftungsgesetz"))
        assertTrue("cardinal duties", de.contains("wesentlichen Vertragspflicht"))
    }

    @Test
    fun `the terms preserve the non-waivable decompilation rights`() {
        val de = text(LegalDocument.TERMS, german = true)
        assertTrue(de.contains("69e UrhG"))
    }

    @Test
    fun `the third-party notices file has no unfilled placeholder`() {
        val license = RepoFiles.at("THIRD_PARTY_NOTICES.txt").readText()
        assertFalse("governing law was left blank", license.contains("__________"))
        assertFalse(license.contains("COMPLETE OR REMOVE BEFORE PUBLISHING"))
    }
}
