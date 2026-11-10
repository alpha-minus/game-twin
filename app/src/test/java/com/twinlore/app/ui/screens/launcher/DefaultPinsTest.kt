package com.twinlore.app.ui.screens.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The starter pins are laid down once on first run and then belong to the user, so getting the
 * set wrong is not something a later launch corrects.
 */
class DefaultPinsTest {

    @Test
    fun `a fully stocked device gets all four, in order`() {
        val pins = defaultPins(
            setOf(
                "com.google.android.youtube",
                "com.android.chrome",
                "com.google.android.documentsui",
                "com.android.settings",
                "com.example.unrelated",
            )
        )
        assertEquals(
            listOf(
                "com.android.settings",
                "com.google.android.documentsui",
                "com.android.chrome",
                "com.google.android.youtube",
            ),
            pins,
        )
    }

    /** A slot nothing satisfies is skipped, not left as a hole. */
    @Test
    fun `missing apps are skipped rather than pinned blind`() {
        val pins = defaultPins(setOf("com.android.settings", "com.google.android.youtube"))
        assertEquals(listOf("com.android.settings", "com.google.android.youtube"), pins)
    }

    @Test
    fun `a device with none of them gets nothing`() {
        assertTrue(defaultPins(setOf("com.example.a", "com.example.b")).isEmpty())
        assertTrue(defaultPins(emptySet()).isEmpty())
    }

    /** One slot never yields two pins, however many of its candidates are installed. */
    @Test
    fun `each slot contributes at most one app`() {
        val pins = defaultPins(
            setOf(
                "com.google.android.documentsui",
                "com.android.documentsui",
                "com.android.chrome",
                "com.chrome.beta",
            )
        )
        assertEquals(listOf("com.google.android.documentsui", "com.android.chrome"), pins)
        assertEquals("a package was pinned twice", pins.size, pins.toSet().size)
    }

    /** The preferred candidate wins when both are present. */
    @Test
    fun `the first candidate in a slot is preferred`() {
        assertEquals(
            listOf("com.google.android.documentsui"),
            defaultPins(setOf("com.android.documentsui", "com.google.android.documentsui")),
        )
    }
}
