package com.twinlore.app.ui.screens.group

import com.twinlore.app.data.local.PageGroupEntryEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The page-group viewer's back/forward history: the stack rules in
 * [PageGroupHistory], and which entry a snapshot files the reader under
 * ([historyLocationEntryId]).
 *
 * The second half is the regression. In the app a group opened a second time
 * composes with its view model already holding the last chapter, and link taps
 * (pushed from the WebView client, built once in the AndroidView factory)
 * snapshotted that composition-time chapter forever. Back then loaded the page
 * the group was opened on instead of the page the link was tapped on. The
 * snapshot now resolves from live state at the moment of the tap.
 */
class PageGroupHistoryTest {

    private fun entry(id: String, mht: String? = "mht_$id") = PageGroupEntryEntity(
        id = id,
        groupTabId = "g",
        position = id.last().code,
        label = "Page $id",
        savedHref = "https://example.com/$id.html",
        fragment = null,
        mhtResourceId = mht,
    )

    private val entries = listOf(entry("a"), entry("b"), entry("c"))

    private fun stateOn(activeId: String?) =
        PageGroupViewerState(groupTabId = "g", entries = entries, activeEntryId = activeId)

    @Test fun backThenForward_returnsToEachPlace() {
        val onA = HistoryEntry("a", 100)
        val onB = HistoryEntry("b", 200)
        val onC = HistoryEntry("c", 0)
        // a -> b -> c by two jumps.
        val h = PageGroupHistory().pushBeforeJump(onA).pushBeforeJump(onB)

        val back1 = h.stepBack(onC)!!
        assertEquals(onB, back1.target)
        val back2 = back1.history.stepBack(onB)!!
        assertEquals(onA, back2.target)
        assertNull(back2.history.stepBack(onA))

        val fwd1 = back2.history.stepForward(onA)!!
        assertEquals(onB, fwd1.target)
        val fwd2 = fwd1.history.stepForward(onB)!!
        assertEquals(onC, fwd2.target)
        assertNull(fwd2.history.stepForward(onC))
        assertEquals(listOf(onA, onB), fwd2.history.back)
    }

    @Test fun oneStepPerPress_neverSkips() {
        val h = PageGroupHistory()
            .pushBeforeJump(HistoryEntry("a", 0))
            .pushBeforeJump(HistoryEntry("b", 0))
            .pushBeforeJump(HistoryEntry("c", 0))
        val step = h.stepBack(HistoryEntry("c", 500))!!
        assertEquals("c", step.target.entryId)
        assertEquals(2, step.history.back.size)
        assertEquals(listOf(HistoryEntry("c", 500)), step.history.forward)
    }

    @Test fun freshJumpAfterBack_dropsForwardBranch() {
        val h = PageGroupHistory()
            .pushBeforeJump(HistoryEntry("a", 0))
            .stepBack(HistoryEntry("b", 0))!!.history
        assertEquals(1, h.forward.size)
        val jumped = h.pushBeforeJump(HistoryEntry("a", 0))
        assertTrue(jumped.forward.isEmpty())
    }

    @Test fun identicalSnapshot_isNotPushedTwice() {
        val here = HistoryEntry("a", 40)
        val h = PageGroupHistory().pushBeforeJump(here).pushBeforeJump(here)
        assertEquals(listOf(here), h.back)
        // Same entry, different place IS a new event.
        assertEquals(2, h.pushBeforeJump(HistoryEntry("a", 41)).back.size)
    }

    @Test fun noLocation_stepsWithoutFilingOne() {
        val h = PageGroupHistory().pushBeforeJump(HistoryEntry("a", 0))
        val step = h.stepBack(null)!!
        assertTrue(step.history.forward.isEmpty())
    }

    @Test fun location_isTheDisplayedChapterWhenScrollHasRefinedIt() {
        assertEquals("b", historyLocationEntryId("b", stateOn("a")))
    }

    @Test fun location_fallsBackToActiveEntry() {
        assertEquals("c", historyLocationEntryId(null, stateOn("c")))
        // An id the group no longer has is not filed.
        assertEquals("c", historyLocationEntryId("gone", stateOn("c")))
    }

    @Test fun location_isNullBeforeTheGroupHasLoaded() {
        assertNull(historyLocationEntryId(null, PageGroupViewerState(groupTabId = "g")))
    }

    /**
     * The reported bug, end to end at this level: the group reopens on page
     * a, the reader moves to page c, taps a link there. Back must return to c,
     * where the link was tapped, not to a, where the group was opened.
     */
    @Test fun linkTapAfterReopen_filesThePageTheLinkWasOn() {
        val reopenedOnA = stateOn("a")
        val nowOnC = reopenedOnA.copy(activeEntryId = "c")

        val here = HistoryEntry(historyLocationEntryId(null, nowOnC)!!, 300)
        val h = PageGroupHistory().pushBeforeJump(here)

        val step = h.stepBack(HistoryEntry("b", 0))!!
        assertEquals("c", step.target.entryId)
        assertEquals(300, step.target.scrollY)
    }
}
