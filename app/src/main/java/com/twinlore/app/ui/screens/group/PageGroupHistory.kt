package com.twinlore.app.ui.screens.group

/**
 * The page-group viewer's back/forward stacks, as plain values.
 *
 * Browser behaviour: a snapshot of where the reader was is pushed BEFORE each
 * explicit jump (ToC tap, link, marker, find pick, next-page card). Back pops
 * the top of [back] and files where the reader is now on [forward]; Forward is
 * the mirror. A fresh jump after a Back drops the forward branch.
 */
internal data class PageGroupHistory(
    val back: List<HistoryEntry> = emptyList(),
    val forward: List<HistoryEntry> = emptyList(),
) {
    /**
     * Records [here] before a jump. A snapshot identical to the top of the
     * stack is not a new browsing event, so rapid taps on the same place do
     * not bloat it; the forward branch is dropped either way.
     */
    fun pushBeforeJump(here: HistoryEntry): PageGroupHistory {
        val last = back.lastOrNull()
        if (last != null && last.entryId == here.entryId && last.scrollY == here.scrollY) {
            return copy(forward = emptyList())
        }
        return PageGroupHistory(back = back + here, forward = emptyList())
    }

    /**
     * One step back from [here]. Null when there is nothing to go back to.
     * [here] is null only before the viewer has a location, and then nothing
     * is filed on the forward stack.
     */
    fun stepBack(here: HistoryEntry?): Step? {
        val target = back.lastOrNull() ?: return null
        return Step(
            target = target,
            history = PageGroupHistory(
                back = back.dropLast(1),
                forward = if (here != null) forward + here else forward,
            ),
        )
    }

    /** One step forward from [here]. See [stepBack]. */
    fun stepForward(here: HistoryEntry?): Step? {
        val target = forward.lastOrNull() ?: return null
        return Step(
            target = target,
            history = PageGroupHistory(
                back = if (here != null) back + here else back,
                forward = forward.dropLast(1),
            ),
        )
    }

    /** Where a back or forward step goes, and the stacks after it. */
    data class Step(val target: HistoryEntry, val history: PageGroupHistory)
}

/**
 * The entry a history snapshot files the reader under: the chapter the scroll
 * listener says is on screen, else the active entry.
 *
 * Takes its inputs as arguments on purpose. The WebView's client is built once,
 * in the AndroidView factory, and the link taps it handles push history from
 * there. A snapshot that read a value computed during composition saw the one
 * from the composition the factory ran in, forever. In the app the group's view
 * model outlives the viewer, so a group opened a second time composes with its
 * last chapter already active and every link tap after that filed the reader
 * under that chapter, whatever page they were on: Back then loaded the wrong
 * page. The panel builds a fresh view model per window, whose first composition
 * has no active entry yet, so the same code fell through to the live state and
 * worked there. Callers pass the live state at the moment of the snapshot.
 *
 * A [scrollDisplayEntryId] that is no longer in the group (the entry was
 * removed) falls back to the active entry rather than filing a dead id.
 */
internal fun historyLocationEntryId(
    scrollDisplayEntryId: String?,
    state: PageGroupViewerState,
): String? {
    val displayed = scrollDisplayEntryId?.takeIf { id -> state.entries.any { it.id == id } }
    return displayed ?: state.activeEntry?.id
}
