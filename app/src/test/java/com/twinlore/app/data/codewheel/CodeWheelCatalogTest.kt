package com.twinlore.app.data.codewheel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CodeWheelCatalogTest {
    private val sample = listOf(
        CodeWheelEntry("The Secret of Monkey Island", "Secret of Monkey Island, The (1990)_extra_interactive-code-wheel_v2.5.zip"),
        CodeWheelEntry("Monkey Island 2 - LeChuck's Revenge", "Monkey Island 2 - LeChuck's Revenge (1991)_extra_interactive-code-wheel.zip"),
        CodeWheelEntry("Chip's Challenge", "Chip's Challenge (1990)_interactive-codewheel.zip"),
        CodeWheelEntry("Pool of Radiance", "Pool of Radiance (1988)_extra_interactive-code-wheel.zip"),
    )
    private val catalog = CodeWheelCatalog(null)

    @Test fun `matches exact title`() {
        assertEquals("Chip's Challenge", catalog.bestMatch(sample, "Chip's Challenge")?.title)
    }

    @Test fun `matches with region and year noise`() {
        assertEquals(
            "The Secret of Monkey Island",
            catalog.bestMatch(sample, "The Secret of Monkey Island (USA)")?.title
        )
    }

    @Test fun `sequel guard picks correct numbered entry`() {
        // Full sequel title matches its own entry, not the no-number "Secret of Monkey Island".
        assertEquals(
            "Monkey Island 2 - LeChuck's Revenge",
            catalog.bestMatch(sample, "Monkey Island 2 - LeChuck's Revenge")?.title
        )
    }

    @Test fun `sequel guard rejects wrong sequel number`() {
        // A number-3 query must not collapse onto the number-2 entry (guard).
        assertNull(catalog.bestMatch(sample, "Monkey Island 3"))
    }

    @Test fun `no false positive for unrelated game`() {
        assertNull(catalog.bestMatch(sample, "Super Mario Bros"))
    }

    @Test fun `downloadUrl encodes spaces and parens`() {
        val url = sample[2].downloadUrl
        assertEquals(
            "https://archive.org/download/code-wheels/Chip%27s%20Challenge%20%281990%29_interactive-codewheel.zip",
            url
        )
    }
}
