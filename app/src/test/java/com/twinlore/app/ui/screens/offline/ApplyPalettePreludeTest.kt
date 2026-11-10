package com.twinlore.app.ui.screens.offline

import com.twinlore.app.data.AppFont
import org.junit.Assert.assertTrue
import org.junit.Test

class ApplyPalettePreludeTest {

    @Test
    fun splicesStyleTagAfterHeadOpenTag() {
        val html = "<html><head><title>x</title></head><body>hi</body></html>"
        val out = applyPalettePrelude(html, "#111111", "#eeeeee", AppFont.DEFAULT)
        val idx = out.indexOf("<style id=\"palette-override\">")
        val headEnd = out.indexOf("</head>")
        assertTrue("style block present", idx > 0)
        assertTrue("style block sits inside head", idx in 0..headEnd)
        // Palette rules should reference the bg + fg hex
        assertTrue(out.contains("#111111"))
        assertTrue(out.contains("#eeeeee"))
    }

    @Test
    fun prependsWhenHeadAbsent() {
        val html = "<html><body>just body</body></html>"
        val out = applyPalettePrelude(html, "#000000", "#ffffff", AppFont.DEFAULT)
        assertTrue(out.startsWith("<style id=\"palette-override\">"))
    }

    @Test
    fun caseInsensitiveHeadMatch() {
        val html = "<HTML><HEAD></HEAD><BODY></BODY></HTML>"
        val out = applyPalettePrelude(html, "#000000", "#ffffff", AppFont.DEFAULT)
        val headIdx = out.indexOf("<HEAD>", ignoreCase = false)
        val styleIdx = out.indexOf("<style id=\"palette-override\">")
        assertTrue(styleIdx > headIdx)
    }
}
