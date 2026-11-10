package com.twinlore.app.domain

import com.twinlore.app.data.LibraryViewMode
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryViewModeNavTest {

    @Test
    fun `next cycles in declaration order`() {
        assertEquals(LibraryViewMode.LIST, LibraryViewMode.CAROUSEL.next())
        assertEquals(LibraryViewMode.GRID_EXTRA_LARGE, LibraryViewMode.LIST.next())
        assertEquals(LibraryViewMode.GRID_MEDIUM, LibraryViewMode.GRID_EXTRA_LARGE.next())
        assertEquals(LibraryViewMode.GRID_SMALL, LibraryViewMode.GRID_MEDIUM.next())
        assertEquals(LibraryViewMode.LAUNCHER, LibraryViewMode.GRID_SMALL.next())
    }

    @Test
    fun `next wraps from last to first`() {
        assertEquals(LibraryViewMode.CAROUSEL, LibraryViewMode.LAUNCHER.next())
    }

    @Test
    fun `previous cycles in reverse declaration order`() {
        assertEquals(LibraryViewMode.GRID_SMALL, LibraryViewMode.LAUNCHER.previous())
        assertEquals(LibraryViewMode.GRID_MEDIUM, LibraryViewMode.GRID_SMALL.previous())
        assertEquals(LibraryViewMode.GRID_EXTRA_LARGE, LibraryViewMode.GRID_MEDIUM.previous())
        assertEquals(LibraryViewMode.LIST, LibraryViewMode.GRID_EXTRA_LARGE.previous())
        assertEquals(LibraryViewMode.CAROUSEL, LibraryViewMode.LIST.previous())
    }

    @Test
    fun `previous wraps from first to last`() {
        assertEquals(LibraryViewMode.LAUNCHER, LibraryViewMode.CAROUSEL.previous())
    }
}
