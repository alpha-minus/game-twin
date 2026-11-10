package com.twinlore.app.ui.screens

import com.twinlore.app.data.AppFont
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * The palette CSS asks for the bundled guide fonts by
 * `file:///android_asset/fonts/…`, and guide documents load from a
 * `file://` cache path. With `setAllowFileAccessFromFileURLs(false)` every
 * `file:` URL is an opaque origin, and a CSS `@font-face` fetch is made in
 * CORS-anonymous mode, so that request is cross-origin with nothing to allow
 * it: the engine drops the response and the guide falls back to plain
 * monospace. [bundledFontResponse] answers the request from the APK's own
 * assets with an allow-all header, so the font never depends on
 * file-from-file access.
 */
@RunWith(RobolectricTestRunner::class)
class BundledFontResponseTest {

    private val context get() = RuntimeEnvironment.getApplication()

    @Test
    fun serves_a_bundled_font_with_an_allow_all_header() {
        val response = bundledFontResponse(
            context,
            "file:///android_asset/fonts/jetbrains_mono.ttf"
        )

        assertNotNull("the bundled font must be served, not left to the file: origin", response)
        assertEquals("*", response!!.responseHeaders?.get("Access-Control-Allow-Origin"))
        assertEquals(200, response.statusCode)
        assertTrue("the response must carry real font bytes", response.data.readBytes().isNotEmpty())
    }

    /**
     * Every font the palette CSS can reference must be one the interceptor
     * actually serves; a font added to [buildPaletteCss] without a matching
     * asset entry would silently go back to being blocked.
     */
    @Test
    fun every_font_face_url_the_palette_emits_is_served() {
        val urlPattern = Regex("src:url\\(([^)]+)\\)")
        for (font in AppFont.entries) {
            val css = buildPaletteCss("#101010", "#E0E0E0", font)
            val urls = urlPattern.findAll(css).map { it.groupValues[1] }.toList()
            if (font == AppFont.DEFAULT) {
                assertTrue("the default font must emit no @font-face at all", urls.isEmpty())
                continue
            }
            assertEquals("expected exactly one @font-face url for $font", 1, urls.size)
            assertNotNull(
                "palette CSS for $font references ${urls.first()}, which nothing serves",
                bundledFontResponse(context, urls.first())
            )
        }
    }

    @Test
    fun refuses_a_name_that_escapes_the_fonts_directory() {
        assertNull(
            bundledFontResponse(
                context,
                "file:///android_asset/fonts/../../databases/app_database"
            )
        )
        assertNull(bundledFontResponse(context, "file:///android_asset/fonts/not_a_bundled_font.ttf"))
    }

    @Test
    fun ignores_urls_that_are_not_bundled_font_requests() {
        assertNull(bundledFontResponse(context, "https://example.com/fonts/space_mono.ttf"))
        assertNull(bundledFontResponse(context, "file:///data/user/0/com.twinlore.app/cache/index.html"))
    }
}
