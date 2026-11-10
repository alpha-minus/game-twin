package com.twinlore.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GuideFileTypeTest {
    @Test fun `fromFileName recognizes epub extension`() {
        assertEquals(GuideFileType.EPUB, GuideFileType.fromFileName("book.epub"))
        assertEquals(GuideFileType.EPUB, GuideFileType.fromFileName("BOOK.EPUB"))
    }

    @Test fun `fromFileTypeColumn recognizes epub`() {
        assertEquals(GuideFileType.EPUB, GuideFileType.fromFileTypeColumn("epub"))
        assertEquals(GuideFileType.EPUB, GuideFileType.fromFileTypeColumn("EPUB"))
    }

    @Test fun `EPUB is offline-only`() {
        assertTrue(GuideFileType.EPUB.isOfflineOnly)
    }

    @Test fun `wheel column maps to WHEEL`() {
        assertEquals(GuideFileType.WHEEL, GuideFileType.fromFileTypeColumn("wheel"))
    }

    @Test fun `wheel label and offline`() {
        assertEquals("WHEEL", GuideFileType.WHEEL.displayLabel)
        assertTrue(GuideFileType.WHEEL.isOfflineOnly)
    }
}
