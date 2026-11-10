package com.twinlore.app.data.vgmaps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class VgmapsPageScraperTest {
    private fun load(name: String): String =
        File("src/test/resources/vgmaps/fixtures/$name").readText()

    @Test fun `castlevania fixture yields multiple PNG maps with captions`() {
        val maps = VgmapsPageScraper.parseMapsFromHtml(
            html = load("castlevania.html"),
            system = "PSX",
            anchor = "CastlevaniaSymphonyOfTheNight",
        )
        assertTrue("Castlevania SOTN should have at least 20 maps, found ${maps.size}", maps.size >= 20)
        assertTrue(
            "Every imageUrl must be absolute https",
            maps.all { it.imageUrl.startsWith("https://www.vgmaps.com/Atlas/PSX/") }
        )
        assertTrue("Every imageUrl must be png/jpg/gif", maps.all {
            it.imageUrl.endsWith(".png", true) ||
            it.imageUrl.endsWith(".jpg", true) ||
            it.imageUrl.endsWith(".gif", true)
        })
        assertTrue("Captions must be non-blank", maps.all { it.caption.isNotBlank() })
    }

    @Test fun `xenogears fixture yields multiple descriptive captions, no bare digits`() {
        val maps = VgmapsPageScraper.parseMapsFromHtml(
            html = load("xenogears.html"),
            system = "PSX",
            anchor = "Xenogears",
        )
        assertTrue("Should find at least 1 map, found ${maps.size}", maps.isNotEmpty())
        // No caption is a literal "[1]" / "[2]" / "1" -- captured PSX page has no such cases.
        val bracketed = maps.filter { it.caption.matches(Regex("""^\[?\d+\]?$""")) }
        assertTrue("No captions should be bare digits/brackets in this fixture, was: $bracketed", bracketed.isEmpty())
        assertTrue("All captions must be non-blank", maps.all { it.caption.isNotBlank() })
    }

    @Test fun `missing-anchor returns empty list`() {
        val maps = VgmapsPageScraper.parseMapsFromHtml(
            html = load("missing-anchor.html"),
            system = "PSX",
            anchor = "ThisAnchorDoesNotExist",
        )
        assertTrue("Should be empty, was ${maps.size}", maps.isEmpty())
    }

    @Test fun `bracketed-index link text falls back to URL basename`() {
        // Synthetic page that DOES have [1]/[2] link text, to exercise the
        // caption-fallback code path. (The captured PSX MHT has no such cases.)
        val html = """
            <html><body>
              <table>
                <tr><td><a name="Foo"><img src="t.png"></a></td></tr>
                <tr><td>
                  <a href="https://www.vgmaps.com/Atlas/PSX/Foo-Overworld.png">[1]</a>
                  <a href="https://www.vgmaps.com/Atlas/PSX/Foo-Underworld.png">[2]</a>
                  <a href="https://www.vgmaps.com/Atlas/PSX/Foo-Castle.png">3</a>
                </td></tr>
              </table>
            </body></html>
        """.trimIndent()
        val maps = VgmapsPageScraper.parseMapsFromHtml(html, "PSX", "Foo")
        assertEquals(3, maps.size)
        // None of the captions should be the literal "[1]" / "[2]" / "3" -- they
        // should have been replaced with URL-derived names.
        assertTrue(
            "Captions should be URL-derived, were: ${maps.map { it.caption }}",
            maps.none { it.caption.matches(Regex("""^\[?\d+\]?$""")) }
        )
        // Expected fallbacks: "Foo Overworld", "Foo Underworld", "Foo Castle" (or similar CamelCase-split forms).
        assertTrue(
            "Captions should mention 'Overworld', 'Underworld', 'Castle'; were: ${maps.map { it.caption }}",
            maps.any { it.caption.contains("Overworld", ignoreCase = true) } &&
            maps.any { it.caption.contains("Underworld", ignoreCase = true) } &&
            maps.any { it.caption.contains("Castle", ignoreCase = true) }
        )
    }

    @Test fun `parser deduplicates identical imageUrls`() {
        val html = """
            <html><body>
              <table>
                <tr><td><a name="Foo"><img src="t.png"></a></td></tr>
                <tr><td>
                  <a href="https://www.vgmaps.com/Atlas/PSX/Foo-A.png">A</a>
                  <a href="https://www.vgmaps.com/Atlas/PSX/Foo-A.png">A (dup)</a>
                  <a href="https://www.vgmaps.com/Atlas/PSX/Foo-B.png">B</a>
                </td></tr>
              </table>
            </body></html>
        """.trimIndent()
        val maps = VgmapsPageScraper.parseMapsFromHtml(html, "PSX", "Foo")
        assertEquals(2, maps.size)
        assertEquals(listOf("A", "B"), maps.map { it.caption })
    }

    @Test fun `parser filters out hrefs to other systems`() {
        val html = """
            <html><body>
              <table>
                <tr><td><a name="Foo"><img src="t.png"></a></td></tr>
                <tr><td>
                  <a href="https://www.vgmaps.com/Atlas/PSX/Foo-Right.png">Right</a>
                  <a href="https://www.vgmaps.com/Atlas/SNES/Bar-Wrong.png">Wrong</a>
                  <a href="https://example.com/garbage.png">Garbage</a>
                </td></tr>
              </table>
            </body></html>
        """.trimIndent()
        val maps = VgmapsPageScraper.parseMapsFromHtml(html, "PSX", "Foo")
        assertEquals(1, maps.size)
        assertEquals("Right", maps[0].caption)
    }

    @Test fun `castlevania fixture extracts contributor emails`() {
        val details = VgmapsPageScraper.parseGameDetailsFromHtml(
            html = load("castlevania.html"),
            system = "PSX",
            anchor = "CastlevaniaSymphonyOfTheNight",
        )
        assertTrue("Should extract at least one credit, found ${details.credits}", details.credits.isNotEmpty())
        assertTrue(
            "Every credit must have a non-blank name",
            details.credits.all { it.name.isNotBlank() }
        )
        val withEmail = details.credits.count { it.email != null }
        assertTrue("Most credits should have emails, was $withEmail of ${details.credits.size}", withEmail > 0)
    }

    @Test fun `castlevania fixture excludes Maps Of The Month section header from credits`() {
        // Regression: the bolded "Maps Of The Month: ..." section header is
        // structurally a sibling <a> alongside contributor links, but it is
        // the table-cell title, not a submitter. It must not appear in credits.
        val details = VgmapsPageScraper.parseGameDetailsFromHtml(
            html = load("castlevania.html"),
            system = "PSX",
            anchor = "CastlevaniaSymphonyOfTheNight",
        )
        val offenders = details.credits.filter {
            it.name.contains("Map of the Month", ignoreCase = true) ||
                it.name.contains("Maps of the Month", ignoreCase = true)
        }
        assertTrue(
            "Section header should not appear in credits, found: $offenders",
            offenders.isEmpty(),
        )
    }

    @Test fun `super-metroid fixture credits are only submitter names`() {
        // Regression for live-page bug: on Super Metroid, the "Maps by:" row
        // showed a parenthesised submitter name (with literal parens), the
        // promotional store link "Order poster maps of this game from SNESMaps",
        // and the legitimate per-map submitters. Only the legitimate submitters
        // (dummy names in the fixture) should remain, with parens stripped.
        val details = VgmapsPageScraper.parseGameDetailsFromHtml(
            html = load("super-metroid.html"),
            system = "SuperNES",
            anchor = "SuperMetroid",
        )
        val names = details.credits.map { it.name }
        assertTrue("Gize Kerod must be present, was $names", names.contains("Gize Kerod"))
        assertTrue("Mesahasi must be present, was $names", names.contains("Mesahasi"))
        assertTrue(
            "Wrapping parens must be stripped, was $names",
            names.none { it.startsWith("(") || it.endsWith(")") },
        )
        assertTrue(
            "Section header must not appear, was $names",
            names.none { it.contains("Map of the Month", ignoreCase = true) ||
                         it.contains("Maps of the Month", ignoreCase = true) },
        )
        assertTrue(
            "Poster-store promo link must not appear, was $names",
            names.none { it.contains("Order ", ignoreCase = true) ||
                         it.contains("poster", ignoreCase = true) ||
                         it.contains("SNESMaps", ignoreCase = true) },
        )
    }

    @Test fun `wrapping parens are stripped from credit names`() {
        val html = """<html><body><table>
            <tr><td><a name="Foo"><img src="t.png"></a></td></tr>
            <tr><td>
              <a href="https://example.com/profile">(Alex Mapper)</a>
            </td></tr>
            <tr><td><a href="https://www.vgmaps.com/Atlas/PSX/Foo-A.png">A</a></td></tr>
        </table></body></html>""".trimIndent()
        val details = VgmapsPageScraper.parseGameDetailsFromHtml(html, "PSX", "Foo")
        val names = details.credits.map { it.name }
        assertTrue("Should contain bare Alex Mapper, was $names", names.contains("Alex Mapper"))
        assertTrue("Must not contain parenthesised form, was $names", !names.contains("(Alex Mapper)"))
    }

    @Test fun `sister-store promotional links are not treated as credits`() {
        val html = """<html><body><table>
            <tr><td><a name="Foo"><img src="t.png"></a></td></tr>
            <tr><td>
              <a href="http://www.nesmaps.com/nesmapsstore.html#FooMap">Order poster maps of this game from <b>SNESMaps</b></a>
            </td></tr>
            <tr><td><a href="https://www.vgmaps.com/Atlas/PSX/Foo-A.png">A</a></td></tr>
        </table></body></html>""".trimIndent()
        val details = VgmapsPageScraper.parseGameDetailsFromHtml(html, "PSX", "Foo")
        val names = details.credits.map { it.name }
        assertTrue("Store link must be filtered, was $names",
            names.none { it.contains("Order", ignoreCase = true) ||
                         it.contains("SNESMaps", ignoreCase = true) ||
                         it.contains("poster", ignoreCase = true) })
    }

    @Test fun `bolded section-header link is not treated as a credit`() {
        // Synthetic minimum repro: a <b>-wrapped news-archive link followed by
        // a parenthesized list of real contributors. Only the latter should
        // show up in credits.
        val html = """<html><body><table>
            <tr><td><a name="Foo"><img src="t.png"></a></td></tr>
            <tr><td>
              <b><a href="https://www.vgmaps.com/NewsArchives/MapsOfTheMonth-2020.htm#01">Maps Of The Month: January 2020</a></b>
              (<a href="mailto:cartographer (at) example.com">Cartographer</a>, with thanks to
               <a href="http://example.com/edsword">Pathfinder</a>)
            </td></tr>
            <tr><td><a href="https://www.vgmaps.com/Atlas/PSX/Foo-A.png">A</a></td></tr>
        </table></body></html>""".trimIndent()
        val details = VgmapsPageScraper.parseGameDetailsFromHtml(html, "PSX", "Foo")
        val names = details.credits.map { it.name }
        assertTrue("Cartographer must be present, was $names", names.any { it.equals("Cartographer", ignoreCase = true) })
        assertTrue("Pathfinder must be present, was $names", names.any { it.equals("Pathfinder", ignoreCase = true) })
        assertTrue(
            "Section header must be filtered out, was $names",
            names.none { it.contains("Maps Of The Month", ignoreCase = true) },
        )
    }

    @Test fun `synthetic page with no mailto links returns empty credits`() {
        val details = VgmapsPageScraper.parseGameDetailsFromHtml(
            html = """<html><body><table><tr><td>
                <a name="Foo"><img src="t.png"></a>
                <a href="https://www.vgmaps.com/Atlas/PSX/Foo-A.png">A</a>
            </td></tr></table></body></html>""".trimIndent(),
            system = "PSX",
            anchor = "Foo",
        )
        assertTrue(details.credits.isEmpty())
        assertEquals(1, details.maps.size)
    }

    @Test fun `credits extraction picks up parenthetical names without mailto`() {
        val html = """<html><body><table>
            <tr><td><a name="Foo"><img src="t.png"></a></td></tr>
            <tr><td><b>Maps Of The Month: January 2020</b> (Cartographer, with thanks to Pathfinder, tilewalker)</td></tr>
            <tr><td><a href="https://www.vgmaps.com/Atlas/PSX/Foo-A.png">A</a></td></tr>
        </table></body></html>""".trimIndent()
        val details = VgmapsPageScraper.parseGameDetailsFromHtml(html, "PSX", "Foo")
        val names = details.credits.map { it.name }
        assertTrue("Should extract Cartographer, was $names", names.any { it.equals("Cartographer", ignoreCase = true) })
        assertTrue("Should extract Pathfinder, was $names", names.any { it.equals("Pathfinder", ignoreCase = true) })
        assertTrue("Should extract tilewalker, was $names", names.any { it.equals("tilewalker", ignoreCase = true) })
    }

    @Test fun `credits extraction picks up Map by pattern`() {
        val html = """<html><body><table>
            <tr><td><a name="Bar"><img src="t.png"></a></td></tr>
            <tr><td>Map by Jane Doe</td></tr>
            <tr><td><a href="https://www.vgmaps.com/Atlas/PSX/Bar-A.png">A</a></td></tr>
        </table></body></html>""".trimIndent()
        val details = VgmapsPageScraper.parseGameDetailsFromHtml(html, "PSX", "Bar")
        assertTrue(
            "Should extract Jane Doe, was ${details.credits.map { it.name }}",
            details.credits.any { it.name == "Jane Doe" }
        )
    }

    @Test fun `email obfuscation - at - is normalized in credit extraction`() {
        val html = """<html><body><table><tr><td>
            <a name="Foo"><img src="t.png"></a>
            <a href="mailto:cartographer (at) example.com?subject=VGMaps">Cartographer</a>
            <a href="mailto:foo [at] example.org">foo [at] example.org</a>
            <a href="https://www.vgmaps.com/Atlas/PSX/Foo-A.png">A</a>
        </td></tr></table></body></html>""".trimIndent()
        val details = VgmapsPageScraper.parseGameDetailsFromHtml(html, "PSX", "Foo")
        assertEquals(2, details.credits.size)
        assertEquals("cartographer@example.com", details.credits[0].email)
        assertEquals("Cartographer", details.credits[0].name)
        assertEquals("foo@example.org", details.credits[1].email)
        assertEquals("foo@example.org", details.credits[1].name)
    }
}
