package com.twinlore.app.ui.screens.group

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PageGroupFindRequeryTest {

    @Test
    fun `the bar's query is searched again on the new page`() {
        assertEquals("boss key", pageFindRequeryOnLoad("boss key", pending = null))
    }

    @Test
    fun `a blank query queues nothing`() {
        assertNull(pageFindRequeryOnLoad("", pending = null))
        assertNull(pageFindRequeryOnLoad("   ", pending = null))
    }

    @Test
    fun `a query already pending from a cross-page pick is kept`() {
        assertEquals("chest", pageFindRequeryOnLoad("boss key", pending = "chest"))
        assertEquals("chest", pageFindRequeryOnLoad("", pending = "chest"))
    }
}
