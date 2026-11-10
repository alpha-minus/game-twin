package com.twinlore.app.data

import com.twinlore.app.domain.SavedTab
import org.junit.Assert.*
import org.junit.Test
import java.util.Date

class LandingPageTemplatesSavedLinksTest {

    private fun link(
        id: String = "id",
        title: String = "Title",
        url: String = "https://example.com",
        favicon: ByteArray? = null
    ): SavedTab = SavedTab(
        id = id,
        vaultId = "v1",
        title = title,
        url = url,
        scrollPosition = 0,
        zoomLevel = 1f,
        isFavorite = false,
        isMap = false,
        isCurrentContent = false,
        order = 0,
        createdAt = Date(),
        lastAccessed = Date(),
        fileType = "link",
        faviconBytes = favicon
    )

    @Test fun `buildSavedLinksHtml returns empty string for empty list`() {
        val out = buildSavedLinksHtml(emptyList())
        assertEquals("", out)
    }

    @Test fun `buildSavedLinksHtml escapes special characters in title`() {
        val tab = link(title = """Foo & "Bar" <baz>""")
        val out = buildSavedLinksHtml(listOf(tab))
        assertTrue(out.contains("Foo &amp; &quot;Bar&quot; &lt;baz&gt;"))
        assertFalse(out.contains("Foo & \"Bar\" <baz>"))
    }

    @Test fun `buildSavedLinksHtml falls back to URL host when title is blank`() {
        val tab = link(title = "", url = "https://gamefaqs.com/foo")
        val out = buildSavedLinksHtml(listOf(tab))
        assertTrue("got: $out", out.contains(">gamefaqs.com<"))
    }

    @Test fun `buildSavedLinksHtml emits data-favicon attr only when faviconBytes is non-null`() {
        val withFavicon = link(id = "a", favicon = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47))
        val withoutFavicon = link(id = "b", favicon = null)
        val outA = buildSavedLinksHtml(listOf(withFavicon))
        val outB = buildSavedLinksHtml(listOf(withoutFavicon))
        assertTrue("favicon-bearing tile must carry data-favicon", outA.contains("data-favicon=\"data:image/png;base64,"))
        assertFalse("favicon-less tile must NOT carry data-favicon", outB.contains("data-favicon"))
    }

    @Test fun `buildSavedLinksHtml emits saved-link-delete button with linkId`() {
        val tab = link(id = "abc-123")
        val out = buildSavedLinksHtml(listOf(tab))
        assertTrue(out.contains("class=\"saved-link-delete\""))
        assertTrue(out.contains("data-link-id=\"abc-123\""))
    }
}
