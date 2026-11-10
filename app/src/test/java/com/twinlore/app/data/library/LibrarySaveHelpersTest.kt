package com.twinlore.app.data.library

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class LibrarySaveHelpersTest {

    @Test
    fun sanitize_title_replaces_unsafe_chars() {
        val out = LibrarySaveHelpers.sanitizeTitleForFilename("Zelda: OoT / Walkthrough")
        assertEquals("Zelda OoT Walkthrough", out.trim())
    }

    @Test
    fun pick_unique_target_appends_suffix_when_collision() {
        val home = File.createTempFile("home", "").apply { delete(); mkdirs() }
        try {
            File(home, "guide.mht").createNewFile()
            val unique = LibrarySaveHelpers.pickUniqueTarget(home, "guide.mht")
            assertEquals("guide-2.mht", unique.name)
        } finally {
            home.deleteRecursively()
        }
    }

    @Test
    fun pick_unique_target_returns_original_when_free() {
        val home = File.createTempFile("home", "").apply { delete(); mkdirs() }
        try {
            val unique = LibrarySaveHelpers.pickUniqueTarget(home, "fresh.mht")
            assertEquals("fresh.mht", unique.name)
        } finally {
            home.deleteRecursively()
        }
    }

    @Test
    fun sanitize_download_filename_keeps_only_the_leaf() {
        assertEquals("x.html", LibrarySaveHelpers.sanitizeDownloadFilename("../../../../data/data/com.twinlore.app/cache/x.html"))
        assertEquals("x.html", LibrarySaveHelpers.sanitizeDownloadFilename("..\\..\\x.html"))
        assertEquals("guide.pdf", LibrarySaveHelpers.sanitizeDownloadFilename("guide.pdf"))
    }

    @Test
    fun sanitize_download_filename_never_returns_an_empty_or_dot_name() {
        assertEquals("download", LibrarySaveHelpers.sanitizeDownloadFilename(null))
        assertEquals("download", LibrarySaveHelpers.sanitizeDownloadFilename(""))
        assertEquals("download", LibrarySaveHelpers.sanitizeDownloadFilename("."))
        assertEquals("download", LibrarySaveHelpers.sanitizeDownloadFilename(".."))
        assertEquals("download", LibrarySaveHelpers.sanitizeDownloadFilename("a/"))
    }

    @Test
    fun sanitize_download_filename_drops_control_characters() {
        assertEquals("ab.txt", LibrarySaveHelpers.sanitizeDownloadFilename("a\u0000b\u007f.txt"))
    }
}
