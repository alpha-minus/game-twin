package com.twinlore.app.data.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserSaveCoordinatorTest {

    @Test
    fun parseFilename_null_or_blank_header_returns_null() {
        assertNull(BrowserSaveCoordinator.parseFilename(null))
        assertNull(BrowserSaveCoordinator.parseFilename(""))
        assertNull(BrowserSaveCoordinator.parseFilename("   "))
    }

    @Test
    fun parseFilename_extracts_plain_quoted_filename() {
        assertEquals(
            "hello.pdf",
            BrowserSaveCoordinator.parseFilename("attachment; filename=\"hello.pdf\"")
        )
    }

    @Test
    fun parseFilename_extracts_plain_unquoted_filename() {
        assertEquals(
            "hello.pdf",
            BrowserSaveCoordinator.parseFilename("attachment; filename=hello.pdf")
        )
    }

    @Test
    fun parseFilename_decodes_rfc5987_utf8_extended_form() {
        // r%C3%A9sum%C3%A9.pdf is "résumé.pdf" under UTF-8.
        assertEquals(
            "résumé.pdf",
            BrowserSaveCoordinator.parseFilename(
                "attachment; filename*=UTF-8''r%C3%A9sum%C3%A9.pdf"
            )
        )
    }

    @Test
    fun parseFilename_decodes_percent_encoded_space_not_plus() {
        // RFC 5987 uses %20 for space; literal '+' must round-trip unchanged.
        assertEquals(
            "hello world.pdf",
            BrowserSaveCoordinator.parseFilename("attachment; filename*=UTF-8''hello%20world.pdf")
        )
        assertEquals(
            "1+2.pdf",
            BrowserSaveCoordinator.parseFilename("attachment; filename*=UTF-8''1+2.pdf")
        )
    }

    @Test
    fun parseFilename_prefers_extended_form_when_both_present() {
        assertEquals(
            "real.pdf",
            BrowserSaveCoordinator.parseFilename(
                "attachment; filename=\"fallback.pdf\"; filename*=UTF-8''real.pdf"
            )
        )
        assertEquals(
            "real.pdf",
            BrowserSaveCoordinator.parseFilename(
                "attachment; filename*=UTF-8''real.pdf; filename=\"fallback.pdf\""
            )
        )
    }

    @Test
    fun parseFilename_decodes_iso_8859_1_extended_form() {
        // r%E9sum%E9.pdf is "résumé.pdf" under ISO-8859-1.
        assertEquals(
            "résumé.pdf",
            BrowserSaveCoordinator.parseFilename(
                "attachment; filename*=ISO-8859-1''r%E9sum%E9.pdf"
            )
        )
    }

    @Test
    fun parseFilename_returns_null_when_no_filename_param() {
        assertNull(BrowserSaveCoordinator.parseFilename("inline"))
        assertNull(BrowserSaveCoordinator.parseFilename("attachment"))
    }

    /**
     * [BrowserSaveCoordinator.saveImage] has no `Content-Disposition` to lean
     * on -- the name comes straight from the page-supplied URL. A URL ending
     * in `/..` must not land on disk as a literal `..`, and a segment that
     * smuggles a backslash must not survive as a multi-segment path;
     * [LibrarySaveHelpers.sanitizeDownloadFilename] is what closes both, same
     * as it does for the download path.
     */
    @Test
    fun deriveImageFilename_rejects_a_bare_parent_directory_reference() {
        assertEquals("download", BrowserSaveCoordinator.deriveImageFilename("https://example.com/.."))
    }

    @Test
    fun deriveImageFilename_drops_a_backslash_separator_instead_of_keeping_a_multi_segment_name() {
        assertEquals(
            "secret.png",
            BrowserSaveCoordinator.deriveImageFilename("https://example.com/folder/..\\..\\secret.png")
        )
    }

    @Test
    fun deriveImageFilename_leaves_an_ordinary_name_unchanged() {
        assertEquals("cover.png", BrowserSaveCoordinator.deriveImageFilename("https://example.com/cover.png"))
    }

    /** An empty URL tail keeps its own timestamped fallback, not the sanitizer's generic "download". */
    @Test
    fun deriveImageFilename_falls_back_to_a_timestamped_name_when_the_tail_is_empty() {
        val name = BrowserSaveCoordinator.deriveImageFilename("https://example.com/")
        assertTrue(name.startsWith("image-"))
    }
}
