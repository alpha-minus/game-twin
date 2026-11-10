package com.twinlore.app.data.library

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the walker exclusion predicate: any dot-prefixed name is skipped so
 * sync artifacts (Syncthing `.stversions` / `.stfolder`), hidden caches
 * (`.thumbnails`) and Android trash files (`.trashed-*`) never enter the
 * media-search index and therefore never become local_link resources.
 */
class MediaIndexerExclusionTest {

    @Test
    fun stversions_is_excluded() {
        assertTrue(MediaIndexer.isExcludedDirName(".stversions"))
    }

    @Test
    fun stfolder_is_excluded() {
        assertTrue(MediaIndexer.isExcludedDirName(".stfolder"))
    }

    @Test
    fun any_dot_prefixed_name_is_excluded() {
        assertTrue(MediaIndexer.isExcludedDirName(".x"))
        assertTrue(MediaIndexer.isExcludedDirName(".sync"))
        assertTrue(MediaIndexer.isExcludedDirName(".thumbnails"))
        assertTrue(MediaIndexer.isExcludedDirName(".trashed-12345-cover.png"))
    }

    @Test
    fun plain_names_are_not_excluded() {
        assertFalse(MediaIndexer.isExcludedDirName("stversions"))
        assertFalse(MediaIndexer.isExcludedDirName("Super Metroid"))
        assertFalse(MediaIndexer.isExcludedDirName("super metroid (6)"))
        assertFalse(MediaIndexer.isExcludedDirName("maps.v2"))
    }

    @Test
    fun empty_name_is_not_excluded() {
        assertFalse(MediaIndexer.isExcludedDirName(""))
    }
}
