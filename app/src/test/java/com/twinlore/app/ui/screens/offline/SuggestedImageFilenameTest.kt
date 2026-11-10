package com.twinlore.app.ui.screens.offline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SuggestedImageFilenameTest {

    @Test
    fun `uses url basename when it has an extension`() {
        assertEquals(
            "Pikachu_OS.png",
            suggestedImageFilename(
                "https://archives.bulbagarden.net/media/upload/2/2c/Pikachu_OS.png",
                sourceExtension = "png"
            )
        )
    }

    @Test
    fun `strips query and fragment from url basename`() {
        assertEquals(
            "Pikachu_OS.png",
            suggestedImageFilename(
                "https://example.com/Pikachu_OS.png?w=64&fmt=auto#frag",
                sourceExtension = "png"
            )
        )
    }

    @Test
    fun `falls back to timestamped name when basename has no extension`() {
        // CDN-style URL that ends with a slash or an opaque token — there's
        // nothing useful in the path, so we synthesise a name from the
        // source extension.
        val name = suggestedImageFilename(
            "https://cdn.example.com/transform/",
            sourceExtension = "jpg"
        )
        assertTrue("expected synthesised name with .jpg, got '$name'", name.endsWith(".jpg"))
        assertTrue("expected 'image-' prefix, got '$name'", name.startsWith("image-"))
    }

    @Test
    fun `falls back to png when source extension is null`() {
        val name = suggestedImageFilename(
            "https://cdn.example.com/no-extension-here",
            sourceExtension = null
        )
        assertTrue("expected fallback .png, got '$name'", name.endsWith(".png"))
    }
}
