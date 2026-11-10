package com.twinlore.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GuideFileTypeHtmlZipTest {
    @Test fun `htmlzip column maps to HTMLZIP`() {
        assertEquals(GuideFileType.HTMLZIP, GuideFileType.fromFileTypeColumn("htmlzip"))
    }

    @Test fun `htmlzip label and offline`() {
        assertEquals("HTML", GuideFileType.HTMLZIP.displayLabel)
        assertTrue(GuideFileType.HTMLZIP.isOfflineOnly)
    }
}
