package com.twinlore.app.ui.fold.guide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The reader's own chrome and the panel's bars have to agree, and the way
 * they agree is that the reader always says what state it is in rather than
 * asking the panel to flip. A flip is only right while the two already match,
 * and they do not from the first frame: the panel's collapsed state is
 * persisted and the reader's chrome always starts shown.
 */
class PanelReaderChromeTest {

    // The panel's bar state is persisted and the reader's always started
    // shown, so the two disagreed from the first frame. Seeded, they agree
    // before anything is tapped.
    @Test
    fun `starts where the panel's bar already is`() {
        val reports = mutableListOf<Boolean>()
        val chrome = PanelReaderChrome(initiallyVisible = false, onChange = { reports += it })

        assertEquals(false, chrome.visible)
        assertTrue("seeding is not a change to report", reports.isEmpty())

        chrome.toggle()
        assertTrue(chrome.visible)
        assertEquals(listOf(true), reports)
    }

    @Test
    fun `starts shown and every report is the state, never a flip`() {
        val reports = mutableListOf<Boolean>()
        val chrome = PanelReaderChrome(onChange = { reports += it })
        assertTrue(chrome.visible)

        chrome.toggle()
        chrome.toggle()
        chrome.hide()
        chrome.hide()
        chrome.reveal()

        assertEquals(listOf(false, true, false, false, true), reports)
        assertTrue(chrome.visible)
    }
}
