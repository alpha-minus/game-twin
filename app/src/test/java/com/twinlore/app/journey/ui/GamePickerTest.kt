package com.twinlore.app.journey.ui

import com.twinlore.app.journey.domain.model.CompletionRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * The game picker's arithmetic: what a query matches, what the expand row says, and where the list
 * lands after it is pressed.
 *
 * These are the parts worth pinning. The rest of the picker is a menu, and the two things that made
 * it feel stuck on device - composing every match eagerly, and appending new rows below a bounded
 * viewport - are a `LazyColumn` and the scroll this file's [showMoreScrollTarget] aims.
 */
class GamePickerTest {

    private fun game(id: Int, title: String) = CompletionRow(
        gameId = id,
        title = title,
        iconPath = null,
        numAwarded = 0,
        numAwardedHardcore = 0,
        mostRecentAwardedDate = null,
        highestAwardKind = null,
        highestAwardDate = null,
    )

    private val library = listOf(
        game(1, "Super Metroid"),
        game(2, "Metroid Fusion"),
        game(3, "The Legend of Zelda: A Link to the Past"),
        game(4, "Metal Slug X"),
    )

    @Test
    fun `a blank query is every game, and the very same list`() {
        // Same instance, not merely an equal one: a blank query is the common case (the menu opens
        // on it) and it has no filtering to do, so it should not be paying for a copy.
        assertSame(library, pickerMatches(library, ""))
        assertSame(library, pickerMatches(library, "   "))
    }

    @Test
    fun `a query matches anywhere in the title, ignoring case`() {
        assertEquals(
            listOf("Super Metroid", "Metroid Fusion"),
            pickerMatches(library, "metroid").map { it.title },
        )
        assertEquals(listOf("Metal Slug X"), pickerMatches(library, "SLUG").map { it.title })
        assertEquals(
            listOf("The Legend of Zelda: A Link to the Past"),
            pickerMatches(library, "link to").map { it.title },
        )
    }

    @Test
    fun `a query nothing matches is empty, not everything`() {
        assertEquals(emptyList<CompletionRow>(), pickerMatches(library, "castlevania"))
    }

    @Test
    fun `the expand row promises a full page, or the remainder when that is all there is`() {
        assertEquals("Show 25 more", showMoreLabel(PICKER_PAGE_STEP))
        assertEquals("Show 25 more", showMoreLabel(400))
        assertEquals("Show 2 more", showMoreLabel(2))
        assertEquals("Show 1 more", showMoreLabel(1))
    }

    @Test
    fun `showing more lands one row above the first new one`() {
        // The first page is five rows, indices 0..4, so the sixth game is index 5 and the list
        // stops at index 4 - the last game already seen, with the new ones directly below it.
        assertEquals(PICKER_FIRST_PAGE - 1, showMoreScrollTarget(PICKER_FIRST_PAGE))
        assertEquals(29, showMoreScrollTarget(PICKER_FIRST_PAGE + PICKER_PAGE_STEP))
    }

    @Test
    fun `showing more from an empty list cannot ask for a row before the first`() {
        assertEquals(0, showMoreScrollTarget(0))
    }
}
