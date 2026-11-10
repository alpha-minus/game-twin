package com.twinlore.app.ui.screens

import com.twinlore.app.RepoFiles
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every detection branch of [LibraryViewModel.scrapeAllBoxArt] runs inside the
 * job's try/catch/finally.
 *
 * The job lives on viewModelScope, which has no exception handler, so anything
 * thrown outside the try kills the app instead of showing the refresh error.
 * The source `when` (including the whole Achievements branch, with its vault
 * lookup and scrape) used to sit above the try: a throw there crashed Auto
 * Detect, skipped the finally's safety net, and left the Library's detection
 * transition waiting out its timeout.
 *
 * A structure check, like [LibraryViewModelInitOrderTest]: the view model has
 * dozens of collaborators, and the property that matters is where the try opens.
 */
class LibraryRefreshErrorContainmentTest {

    private val source by lazy {
        RepoFiles.at("app/src/main/java/com/twinlore/app/ui/screens/LibraryViewModel.kt").readText()
    }

    @Test
    fun `the detection source branches are inside the job's try`() {
        val funAt = source.indexOf("fun scrapeAllBoxArt(")
        assertTrue("scrapeAllBoxArt not found", funAt > 0)
        val launchAt = source.indexOf("scrapingJob = viewModelScope.launch {", funAt)
        assertTrue("scrapeAllBoxArt's job launch not found", launchAt > 0)
        val branchesAt = source.indexOf("when (source) {", launchAt)
        assertTrue("detection source branches not found", branchesAt > launchAt)
        val tryAt = source.indexOf("try {", launchAt)
        assertTrue(
            "scrapeAllBoxArt's detection branches run before its try opens, so a throw " +
                "in them crashes the app. Open the try above `when (source)`.",
            tryAt in launchAt until branchesAt,
        )
    }
}
