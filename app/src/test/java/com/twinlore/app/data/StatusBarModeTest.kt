package com.twinlore.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class StatusBarModeTest {

    @Test
    fun `fromName decodes stored names`() {
        assertEquals(StatusBarMode.STANDARD, StatusBarMode.fromName("STANDARD"))
        assertEquals(StatusBarMode.PERFORMANCE, StatusBarMode.fromName("PERFORMANCE"))
        assertEquals(StatusBarMode.OFF, StatusBarMode.fromName("OFF"))
    }

    @Test
    fun `fromName falls back to STANDARD on missing or unknown`() {
        assertEquals(StatusBarMode.STANDARD, StatusBarMode.fromName(null))
        assertEquals(StatusBarMode.STANDARD, StatusBarMode.fromName("garbage"))
        assertEquals(StatusBarMode.STANDARD, StatusBarMode.fromName(""))
    }

    // The whole of the Hidden migration: an install that stored it before the
    // mode was removed reads back as Standard, with its top bar and its way
    // into Always on Top intact.
    @Test
    fun `a stored Hidden from an older install decodes as Standard`() {
        assertEquals(StatusBarMode.STANDARD, StatusBarMode.fromName("HIDDEN"))
    }

    @Test
    fun `tap toggles standard and performance and never leaves off`() {
        assertEquals(StatusBarMode.PERFORMANCE, StatusBarMode.STANDARD.tapToggled())
        assertEquals(StatusBarMode.STANDARD, StatusBarMode.PERFORMANCE.tapToggled())
        assertEquals(StatusBarMode.OFF, StatusBarMode.OFF.tapToggled())
    }
}
