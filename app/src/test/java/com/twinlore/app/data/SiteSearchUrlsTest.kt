package com.twinlore.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The Guide tab and the Image tab reach Neoseeker through one builder.
 *
 * They used to have one each, identical but for the sanitising step the Image
 * tab's copy left out, so the same vault searched Neoseeker two different ways
 * depending on which tab you asked from - and the punctuation-carrying one is
 * the one that came back with nothing.
 */
class SiteSearchUrlsTest {

    private val preamble =
        "https://www.neoseeker.com/seeker.php" +
            "?cx=016579983352691578828%3A8lybbedigmo" +
            "&cof=FORID%3A9" +
            "&ie=UTF-8" +
            "&sa=Search" +
            "&q="

    @Test
    fun `subtitle separators and bracketed regions are dropped before the query`() {
        assertEquals(
            "Castlevania Symphony of the Night USA",
            sanitiseSiteSearchName("Castlevania: Symphony of the Night (USA)"),
        )
    }

    @Test
    fun `a punctuated vault name reaches Neoseeker as words`() {
        assertEquals(
            preamble + "Castlevania+Symphony+of+the+Night+USA",
            neoseekerSearchUrl("Castlevania: Symphony of the Night (USA)"),
        )
    }

    @Test
    fun `a name that is nothing but punctuation still searches for something`() {
        assertEquals(preamble + "walkthrough", neoseekerSearchUrl(" - "))
    }
}
