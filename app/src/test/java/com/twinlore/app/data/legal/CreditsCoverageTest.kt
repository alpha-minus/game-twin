package com.twinlore.app.data.legal

import com.twinlore.app.RepoFiles
import org.junit.Assert.assertTrue
import org.junit.Test

class CreditsCoverageTest {

    @Test
    fun `every bundled font is credited to its designer`() {
        val fonts = RepoFiles.asset("fonts").listFiles().orEmpty().filter { it.extension == "ttf" }
        assertTrue("expected bundled fonts", fonts.isNotEmpty())
        val credited = Credits.ALL[CreditGroup.FONTS].orEmpty()
        assertTrue("no font credits", credited.isNotEmpty())
        for (font in fonts) {
            val stem = font.nameWithoutExtension.replace('_', ' ')
            assertTrue(
                "bundled font ${font.name} has no credit",
                credited.any { it.name.equals(stem, ignoreCase = true) },
            )
        }
        for (credit in credited) {
            assertTrue("${credit.name} has no designer", credit.detail.isNotBlank())
        }
    }

    /**
     * A link back is only a link back if it is reachable.
     *
     * Rescued from `ArtworkSourceAttributionTest`, which went with the GitHub-hosted platform logos
     * it was mostly about. This half of it was never about that source: it holds for every credited
     * artwork source (RetroAchievements, SteamGridDB, TheGamesDB), and the third-party notice
     * checks that survive elsewhere iterate the Maven coordinates instead, so nothing else covers
     * a credit that names a source and does not say where to find it.
     */
    @Test
    fun `every artwork source credit has a url`() {
        val sources = Credits.ALL[CreditGroup.SOURCES].orEmpty()
        assertTrue("no artwork source credits", sources.isNotEmpty())
        for (credit in sources) {
            assertTrue("${credit.name} has no url", !credit.url.isNullOrBlank())
        }
    }

    @Test
    fun `no credit line uses an em dash`() {
        for (credit in Credits.ALL.values.flatten()) {
            assertTrue(credit.name, !credit.name.contains('—'))
            assertTrue(credit.detail, !credit.detail.contains('—'))
        }
    }
}
