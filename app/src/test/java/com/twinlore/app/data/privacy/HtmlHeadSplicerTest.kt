package com.twinlore.app.data.privacy

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.charset.StandardCharsets

class HtmlHeadSplicerTest {

    private val charset = StandardCharsets.UTF_8
    private val injection = "<script>X</script>"

    @Test
    fun `splices after lowercase head`() {
        val input = "<html><head><title>t</title></head></html>".toByteArray(charset)
        val out = HtmlHeadSplicer.spliceAfterHead(input, charset, injection)!!.toString(charset)
        assertEquals("<html><head><script>X</script><title>t</title></head></html>", out)
    }

    @Test
    fun `splices after uppercase HEAD`() {
        val input = "<HTML><HEAD><TITLE>t</TITLE></HEAD></HTML>".toByteArray(charset)
        val out = HtmlHeadSplicer.spliceAfterHead(input, charset, injection)!!.toString(charset)
        assertEquals("<HTML><HEAD><script>X</script><TITLE>t</TITLE></HEAD></HTML>", out)
    }

    @Test
    fun `splices after head with attributes`() {
        val input = """<html><head lang="en"><title>t</title></head></html>""".toByteArray(charset)
        val out = HtmlHeadSplicer.spliceAfterHead(input, charset, injection)!!.toString(charset)
        assertEquals(
            """<html><head lang="en"><script>X</script><title>t</title></head></html>""",
            out
        )
    }

    @Test
    fun `splices after head with leading whitespace and newlines`() {
        val input = "<!doctype html>\n<html>\n  <head>\n    <title>t</title>\n  </head>\n</html>".toByteArray(charset)
        val out = HtmlHeadSplicer.spliceAfterHead(input, charset, injection)!!.toString(charset)
        assertEquals(
            "<!doctype html>\n<html>\n  <head><script>X</script>\n    <title>t</title>\n  </head>\n</html>",
            out
        )
    }

    @Test
    fun `returns null when head is missing`() {
        val input = "<html><body><p>no head here</p></body></html>".toByteArray(charset)
        val out = HtmlHeadSplicer.spliceAfterHead(input, charset, injection)
        assertNull(out)
    }

    @Test
    fun `does not false-positive on header tag`() {
        val input = "<html><body><header>hi</header></body></html>".toByteArray(charset)
        val out = HtmlHeadSplicer.spliceAfterHead(input, charset, injection)
        assertNull(out)
    }

    @Test
    fun `does not false-positive on heading tag`() {
        val input = "<html><body><heading>hi</heading></body></html>".toByteArray(charset)
        val out = HtmlHeadSplicer.spliceAfterHead(input, charset, injection)
        assertNull(out)
    }

    @Test
    fun `preserves bytes after splice point exactly`() {
        val tail = "<title>t</title><meta charset=\"utf-8\"></head></html>"
        val input = "<html><head>$tail".toByteArray(charset)
        val out = HtmlHeadSplicer.spliceAfterHead(input, charset, injection)!!
        val expected = "<html><head>$injection$tail".toByteArray(charset)
        assertArrayEquals(expected, out)
    }

    @Test
    fun `splices when meta charset already inside head`() {
        val input = """<html><head><meta charset="utf-8"><title>t</title></head></html>""".toByteArray(charset)
        val out = HtmlHeadSplicer.spliceAfterHead(input, charset, injection)!!.toString(charset)
        assertEquals(
            """<html><head><script>X</script><meta charset="utf-8"><title>t</title></head></html>""",
            out
        )
    }

    @Test
    fun `skips head token inside an HTML comment and finds the real head`() {
        val input = "<!-- example: <head>...</head> --><html><head><title>t</title></head></html>".toByteArray(charset)
        val out = HtmlHeadSplicer.spliceAfterHead(input, charset, injection)!!.toString(charset)
        assertEquals(
            "<!-- example: <head>...</head> --><html><head><script>X</script><title>t</title></head></html>",
            out
        )
    }

    @Test
    fun `returns null when the only head token is inside an unclosed comment`() {
        val input = "<!-- bogus <head> with no close".toByteArray(charset)
        val out = HtmlHeadSplicer.spliceAfterHead(input, charset, injection)
        assertNull(out)
    }
}
