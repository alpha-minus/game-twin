package com.twinlore.app.data.legal

import com.twinlore.app.RepoFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `docs/legal/website/` holds a Markdown copy of every shipped legal asset for game-twin.app.
 * The two are meant to be word for word the same text, differing only in presentation: the
 * Markdown adds `#` headings and lets paragraphs run unwrapped. Nothing kept them in step, so an
 * edit to an asset could ship in the app while the website kept serving the superseded wording,
 * which for a privacy policy is the kind of drift that matters.
 *
 * Normalisation is deliberately shallow. It removes only heading markers and the difference
 * between a hard-wrapped and an unwrapped paragraph. It does not touch words, punctuation or
 * order, so a reworded sentence on either side still fails.
 */
class WebsiteCopyParityTest {

    private val headingMarker = Regex("(?m)^ {0,3}#{1,6}[ \t]*")
    private val whitespaceRun = Regex("\\s+")

    private fun normalise(text: String): String =
        text.replace(headingMarker, "").replace(whitespaceRun, " ").trim()

    private fun websitePath(doc: LegalDocument, german: Boolean): String {
        // legal/privacy_de.txt -> docs/legal/website/privacy_de.md
        val name = doc.assetPath(german).substringAfterLast('/').removeSuffix(".txt")
        return "docs/legal/website/$name.md"
    }

    @Test
    fun `every website copy carries the same text as the shipped asset`() {
        for (doc in LegalDocument.entries) {
            for (german in listOf(true, false)) {
                val asset = RepoFiles.asset(doc.assetPath(german))
                val website = RepoFiles.at(websitePath(doc, german))
                assertTrue("missing asset: ${doc.assetPath(german)}", asset.isFile)
                assertTrue("missing website copy: ${websitePath(doc, german)}", website.isFile)

                assertEquals(
                    "${websitePath(doc, german)} has drifted from ${doc.assetPath(german)}; " +
                        "any edit to one must be mirrored into the other",
                    normalise(asset.readText()),
                    normalise(website.readText()),
                )
            }
        }
    }

    /**
     * Guards the normaliser itself: if it ever grew lenient enough to ignore wording, the test
     * above would keep passing over genuinely divergent documents.
     */
    @Test
    fun `normalisation forgives layout but not wording`() {
        val wrapped = "## 1. Controller\n\nThe controller within the meaning of\nArticle 4(7) GDPR is:\n"
        val unwrapped = "1. Controller\nThe controller within the meaning of Article 4(7) GDPR is:"
        assertEquals(normalise(wrapped), normalise(unwrapped))

        val reworded = unwrapped.replace("controller", "processor")
        assertTrue(normalise(wrapped) != normalise(reworded))
    }
}
