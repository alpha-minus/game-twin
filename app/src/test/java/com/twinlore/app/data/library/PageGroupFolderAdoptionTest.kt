package com.twinlore.app.data.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the disk half of guide adoption: a `<VaultFolder>/<Group>/page-N.mht`
 * folder that Syncthing replicated from another device has no rows in this
 * device's database, so the Guides tab shows nothing. Worse, the binder that
 * skips group-owned pages reads its group list FROM the database, so without a
 * group row those pages would each be registered as a standalone guide.
 */
class PageGroupFolderAdoptionTest {

    private fun page(n: Int, size: Long = 1000L) =
        PageGroupFolderAdoption.DiskFile("page-$n.mht", size)

    @Test
    fun `adopts a page-group folder and orders pages numerically`() {
        // Deliberately shuffled, and page-10 must NOT sort between page-1 and page-2.
        val subfolders = mapOf(
            "A Link to the Past Walkthrough - Zelda Dungeon" to
                listOf(page(2), page(10), page(1), page(14), page(9)),
        )

        val groups = PageGroupFolderAdoption.planGroups(subfolders, existingGroupFolders = emptySet())

        assertEquals(1, groups.size)
        val g = groups.single()
        assertEquals("A Link to the Past Walkthrough - Zelda Dungeon", g.folderName)
        assertEquals(
            listOf("page-1.mht", "page-2.mht", "page-9.mht", "page-10.mht", "page-14.mht"),
            g.pages.map { it.filename },
        )
    }

    @Test
    fun `skips a folder already registered as a group`() {
        val subfolders = mapOf("Zelda Walkthrough" to listOf(page(1), page(2)))
        val groups = PageGroupFolderAdoption.planGroups(
            subfolders,
            existingGroupFolders = setOf("Zelda Walkthrough"),
        )
        assertTrue(groups.isEmpty())
    }

    @Test
    fun `existing-group check is case-insensitive`() {
        val subfolders = mapOf("Zelda Walkthrough" to listOf(page(1)))
        val groups = PageGroupFolderAdoption.planGroups(
            subfolders,
            existingGroupFolders = setOf("zelda walkthrough"),
        )
        assertTrue(groups.isEmpty())
    }

    @Test
    fun `ignores folders that are not page groups`() {
        val subfolders = mapOf(
            "Screenshots" to listOf(PageGroupFolderAdoption.DiskFile("shot.png", 10L)),
            "Notes" to listOf(PageGroupFolderAdoption.DiskFile("walkthrough.mht", 10L)),
            "Empty" to emptyList(),
        )
        assertTrue(PageGroupFolderAdoption.planGroups(subfolders, emptySet()).isEmpty())
    }

    @Test
    fun `ignores non-page files inside a group folder`() {
        val subfolders = mapOf(
            "Guide" to listOf(
                page(1),
                page(2),
                PageGroupFolderAdoption.DiskFile("manifest.json", 20L),
                PageGroupFolderAdoption.DiskFile("cover.png", 30L),
            ),
        )
        val g = PageGroupFolderAdoption.planGroups(subfolders, emptySet()).single()
        assertEquals(listOf("page-1.mht", "page-2.mht"), g.pages.map { it.filename })
    }

    @Test
    fun `carries size through for the resource row`() {
        val subfolders = mapOf("Guide" to listOf(page(1, size = 1_736_067L)))
        val g = PageGroupFolderAdoption.planGroups(subfolders, emptySet()).single()
        assertEquals(1_736_067L, g.pages.single().sizeBytes)
    }

    @Test
    fun `isGroupPageFilename recognises only page-N files`() {
        assertTrue(PageGroupFolderAdoption.isGroupPageFilename("page-1.mht"))
        assertTrue(PageGroupFolderAdoption.isGroupPageFilename("PAGE-12.MHT"))
        assertTrue(PageGroupFolderAdoption.isGroupPageFilename("page-3.txt"))
        assertTrue(!PageGroupFolderAdoption.isGroupPageFilename("page.mht"))
        assertTrue(!PageGroupFolderAdoption.isGroupPageFilename("page-one.mht"))
        assertTrue(!PageGroupFolderAdoption.isGroupPageFilename("walkthrough.mht"))
    }

    // ---- MHT Subject header -> entry label ----

    @Test
    fun `reads a plain Subject header as the page title`() {
        val mht = """
            From: <Saved by Blink>
            Snapshot-Content-Location: https://zeldadungeon.net/x
            Subject: Dark World - Zelda Dungeon
            Date: Mon, 6 Jul 2026 20:39:00 +0200
            MIME-Version: 1.0
        """.trimIndent()
        assertEquals("Dark World - Zelda Dungeon", PageGroupFolderAdoption.subjectFrom(mht))
    }

    @Test
    fun `unfolds a Subject header wrapped across lines`() {
        val mht = "Subject: A Link to the Past Walkthrough\r\n\t- Zelda Dungeon\r\nDate: today\r\n"
        assertEquals(
            "A Link to the Past Walkthrough - Zelda Dungeon",
            PageGroupFolderAdoption.subjectFrom(mht),
        )
    }

    @Test
    fun `decodes an RFC 2047 encoded Subject`() {
        // =?utf-8?Q?...?= with an encoded en-dash, as Blink writes for non-ASCII titles.
        val mht = "Subject: =?utf-8?Q?Kakariko_Village_=E2=80=93_Zelda?=\r\nDate: today\r\n"
        assertEquals("Kakariko Village – Zelda", PageGroupFolderAdoption.subjectFrom(mht))
    }

    @Test
    fun `decodes a base64 encoded Subject`() {
        val mht = "Subject: =?UTF-8?B?RGFyayBXb3JsZA==?=\r\nDate: today\r\n"
        assertEquals("Dark World", PageGroupFolderAdoption.subjectFrom(mht))
    }

    @Test
    fun `returns null when there is no Subject header`() {
        assertNull(PageGroupFolderAdoption.subjectFrom("From: <Saved by Blink>\r\nDate: today\r\n"))
        assertNull(PageGroupFolderAdoption.subjectFrom("Subject:   \r\n"))
    }

    // ---- Source URL: what reading mode picks its filter from ----

    // The bug: an adopted group had no URL in the database, so reading mode
    // matched no host and the set came back with the site's search bar and ad
    // slots still in it. The archive records where it came from.
    @Test
    fun `reads the snapshot location Blink writes`() {
        val mht = "From: <Saved by Blink>\r\n" +
            "Snapshot-Content-Location: https://gamefaqs.gamespot.com/guide/123\r\n" +
            "Subject: Chapter 1\r\nDate: today\r\n"
        assertEquals(
            "https://gamefaqs.gamespot.com/guide/123",
            PageGroupFolderAdoption.sourceUrlFrom(mht),
        )
    }

    // What MhtParser.extractOriginalUrl reads, for archives without the
    // snapshot header.
    @Test
    fun `falls back to the first part's content location`() {
        val mht = "Subject: Chapter 1\r\nMIME-Version: 1.0\r\n\r\n" +
            "------Boundary\r\nContent-Type: text/html\r\n" +
            "Content-Location: https://www.neoseeker.com/guide\r\n"
        assertEquals(
            "https://www.neoseeker.com/guide",
            PageGroupFolderAdoption.sourceUrlFrom(mht),
        )
    }

    // A wrong host picks the wrong filter, which is worse than none.
    @Test
    fun `anything that is not http is refused`() {
        assertNull(PageGroupFolderAdoption.sourceUrlFrom("Subject: Chapter 1\r\nDate: today\r\n"))
        assertNull(PageGroupFolderAdoption.sourceUrlFrom("Content-Location: cid:frame-1\r\n"))
        assertNull(PageGroupFolderAdoption.sourceUrlFrom("Content-Location: file:///tmp/x.html\r\n"))
    }

    @Test
    fun `unfolds a location wrapped across lines`() {
        val mht = "Snapshot-Content-Location: https://gamefaqs.gamespot.com/guide/\r\n\t123456\r\nDate: today\r\n"
        assertEquals(
            "https://gamefaqs.gamespot.com/guide/123456",
            PageGroupFolderAdoption.sourceUrlFrom(mht),
        )
    }

    // ---- Shared parts of a group's titles ----

    // The bug this fixes: a group adopted from disk is labelled from each
    // MHT's Subject header, which is the whole browser page title, so every
    // row repeats the game and the chapter hides at the end of the line.
    @Test
    fun `the game name every page shares is dropped`() {
        assertEquals(
            listOf("Chapter 1", "Chapter 2", "Chapter 12"),
            PageGroupFolderAdoption.withoutSharedAffixes(
                listOf(
                    "Chrono Trigger - Chapter 1",
                    "Chrono Trigger - Chapter 2",
                    "Chrono Trigger - Chapter 12",
                )
            ),
        )
    }

    // Sites put their own name at the end, so both ends are trimmed.
    @Test
    fun `a shared prefix and a shared suffix both go`() {
        assertEquals(
            listOf("Dark World", "Kakariko Village"),
            PageGroupFolderAdoption.withoutSharedAffixes(
                listOf(
                    "A Link to the Past - Dark World - Zelda Dungeon",
                    "A Link to the Past - Kakariko Village - Zelda Dungeon",
                )
            ),
        )
    }

    // Whole segments only. Trimming by common character count would leave
    // "Chapter 1" and "Chapter 12" as "" and "2".
    @Test
    fun `a partial word is never cut`() {
        assertEquals(
            listOf("Chapter 1", "Chapter 12"),
            PageGroupFolderAdoption.withoutSharedAffixes(
                listOf("Zelda - Chapter 1", "Zelda - Chapter 12")
            ),
        )
    }

    // Something must survive: a group whose pages are all called the same
    // thing keeps that name rather than becoming a list of blanks.
    @Test
    fun `titles that are entirely shared are left alone`() {
        val same = listOf("Chrono Trigger Walkthrough", "Chrono Trigger Walkthrough")
        assertEquals(same, PageGroupFolderAdoption.withoutSharedAffixes(same))

        val twoParts = listOf("Chrono Trigger - Guide", "Chrono Trigger - Guide")
        assertEquals(listOf("Guide", "Guide"), PageGroupFolderAdoption.withoutSharedAffixes(twoParts))
    }

    @Test
    fun `titles with nothing in common are left alone`() {
        val titles = listOf("Dark World", "Kakariko Village")
        assertEquals(titles, PageGroupFolderAdoption.withoutSharedAffixes(titles))
    }

    // One page cannot tell you what is redundant, so nothing is guessed.
    @Test
    fun `a single page keeps its whole title`() {
        assertEquals(
            listOf("Chrono Trigger - Chapter 1"),
            PageGroupFolderAdoption.withoutSharedAffixes(listOf("Chrono Trigger - Chapter 1")),
        )
    }

    // Pages with no Subject stay null and fall through to "Page N".
    @Test
    fun `missing subjects stay missing and do not shift the others`() {
        assertEquals(
            listOf("Chapter 1", null, "Chapter 2"),
            PageGroupFolderAdoption.withoutSharedAffixes(
                listOf("Zelda - Chapter 1", null, "Zelda - Chapter 2")
            ),
        )
    }

    // The separators sites actually use, including the en dash the encoded
    // Subject test above already shows turning up in real titles.
    @Test
    fun `en dashes, pipes and colons all separate`() {
        assertEquals(
            listOf("Dark World", "Light World"),
            PageGroupFolderAdoption.withoutSharedAffixes(
                listOf("Zelda \u2013 Dark World", "Zelda \u2013 Light World")
            ),
        )
        assertEquals(
            listOf("Part 1", "Part 2"),
            PageGroupFolderAdoption.withoutSharedAffixes(
                listOf("FF7 | Part 1", "FF7 | Part 2")
            ),
        )
    }

    @Test
    fun `labelFor falls back to the page number`() {
        assertEquals("Page 7", PageGroupFolderAdoption.labelFor(subject = null, pageNumber = 7))
        assertEquals("Page 7", PageGroupFolderAdoption.labelFor(subject = "  ", pageNumber = 7))
        assertEquals("Dark World", PageGroupFolderAdoption.labelFor(subject = "Dark World", pageNumber = 7))
    }
}
