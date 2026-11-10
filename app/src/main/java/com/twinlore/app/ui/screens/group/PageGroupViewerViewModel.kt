package com.twinlore.app.ui.screens.group

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twinlore.app.data.PageGroupRepository
import com.twinlore.app.data.local.PageGroupEntryDao
import com.twinlore.app.data.local.PageGroupEntryEntity
import com.twinlore.app.data.local.SavedTabDao
import com.twinlore.app.data.local.ScrollMarkerEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * UI-facing state for the page-group viewer. The active entry id is the
 * row from `saved_tabs.group_active_entry_id` (the user's resume position)
 * unless the user just tapped a different ToC entry, in which case the
 * local override takes precedence until [PageGroupViewerViewModel.onEntryTapped]
 * persists it back to Room.
 *
 * If [activeEntryId] is null (e.g. a brand-new group whose first save
 * failed before active_entry could be recorded), the screen falls back to
 * the first entry whose `mhtResourceId != null`.
 */
data class PageGroupViewerState(
    val groupTabId: String,
    val title: String = "",
    val entries: List<PageGroupEntryEntity> = emptyList(),
    val activeEntryId: String? = null,
    // Monotonic counter bumped on every [onEntryTapped] call. The screen
    // keys its active-entry LaunchedEffect on this in addition to
    // activeEntryId so that a tap on a chapter that is ALREADY the active
    // one (typical after the user has scrolled back from a previously-
    // tapped chapter) still triggers the jump path — the data-class copy
    // produces a fresh state object each tap and StateFlow emits.
    val lastTapSerial: Long = 0L
) {
    val activeIndex: Int?
        get() {
            if (entries.isEmpty()) return null
            val byId = activeEntryId?.let { id -> entries.indexOfFirst { it.id == id }.takeIf { it >= 0 } }
            if (byId != null) return byId
            // Fallback: first entry that actually has a saved file backing it.
            val firstSaved = entries.indexOfFirst { it.mhtResourceId != null }
            return firstSaved.takeIf { it >= 0 }
        }

    val activeEntry: PageGroupEntryEntity?
        get() = activeIndex?.let { entries[it] }
}

/**
 * Setter-style ViewModel: caller invokes [bind] from a LaunchedEffect keyed
 * on the route's `groupTabId`. Mirrors BrowserTabsViewModel's pattern and
 * sidesteps the SavedStateHandle wiring overhead. Re-calling [bind] with
 * the same id is a no-op.
 *
 * The state combines three sources:
 *   1. `page_group_entries` rows — observed via DAO Flow (Room writes
 *      from PageGroupRepository propagate automatically).
 *   2. `title` — seeded from `saved_tabs.title` on bind; overwritten on
 *      [rename].
 *   3. `activeEntryId` — seeded from `saved_tabs.group_active_entry_id`
 *      on bind; overwritten optimistically on [onEntryTapped] so the
 *      WebView responds to taps without waiting on the DB round-trip.
 */
@HiltViewModel
class PageGroupViewerViewModel @Inject constructor(
    private val savedTabDao: SavedTabDao,
    private val pageGroupEntryDao: PageGroupEntryDao,
    private val repository: PageGroupRepository
) : ViewModel() {

    private var groupTabId: String? = null
    private var observeJob: Job? = null
    private var markersJob: Job? = null

    private val _state = MutableStateFlow(PageGroupViewerState(groupTabId = ""))
    val state: StateFlow<PageGroupViewerState> = _state.asStateFlow()

    private val _markers =
        MutableStateFlow<List<ScrollMarkerEntity>>(emptyList())
    val markers: StateFlow<List<ScrollMarkerEntity>> = _markers

    /**
     * Marker/anchor to scroll to after the next cross-file load completes.
     * Consumed by the screen's onPageFinished restore path (precedence over
     * the entry's own scroll_ratio). Cleared after firing.
     *
     * The label is the load-bearing field — the page-group viewer hands it
     * to [scrollToMarkerByText] which walks the DOM to find the Nth text
     * occurrence and centers IT in the viewport. The integer `scrollY` is
     * the fallback used only if the text is missing (e.g. the page rendered
     * differently than at save time because of an MHT parse difference).
     */
    private val _pendingScrollToMarker = MutableStateFlow<PendingScrollMarker?>(null)
    val pendingScrollToMarker: StateFlow<PendingScrollMarker?> = _pendingScrollToMarker

    /**
     * Fragment (#anchor) carried by the LAST cross-document link tap. Single-
     * shot: the screen's cross-file load path consumes it and scrolls to the
     * anchor with precedence OVER the destination entry's saved scroll_ratio —
     * an explicit anchor tap means "take me to this section", not "resume where
     * I last left this page". Reset on EVERY [onEntryTapped] (a plain chapter /
     * Prev-Next tap carries no fragment) so a stale anchor can't bleed into an
     * unrelated navigation. Null when the tap was not anchored.
     */
    private val _pendingTappedFragment = MutableStateFlow<String?>(null)
    val pendingTappedFragment: StateFlow<String?> = _pendingTappedFragment

    /**
     * Binds the VM to a route's groupTabId. Idempotent: re-binding with the
     * same id is a no-op so config-change recompositions don't reload.
     */
    fun bind(groupTabId: String) {
        if (this.groupTabId == groupTabId) return
        this.groupTabId = groupTabId
        _state.value = PageGroupViewerState(groupTabId = groupTabId)

        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            // Seed parent metadata (title + active entry) before the entries
            // stream emits, so the first frame can render the header.
            val parent = savedTabDao.getTabById(groupTabId)
            _state.value = _state.value.copy(
                title = parent?.title.orEmpty(),
                activeEntryId = parent?.groupActiveEntryId
            )
            // Touch lastAccessed so the picker's "most recently viewed" sort
            // (and any future auto-resume that derives from it) reflects group
            // views. GuideScreen does the same via SavedTabRepository.updateLastAccessed
            // when a single-page bookmark opens; this is the parity write.
            savedTabDao.updateLastAccessed(groupTabId, java.util.Date())
            pageGroupEntryDao.observeForGroup(groupTabId).collect { entries ->
                _state.value = _state.value.copy(entries = entries)
            }
        }

        // Stream group markers (cheap; one Room query, one Flow).
        markersJob?.cancel()
        markersJob = viewModelScope.launch {
            repository.observeGroupMarkers(groupTabId).collect { _markers.value = it }
        }
    }

    /**
     * Walks to the previous ToC entry (no wrap-around).
     *
     * [fromEntryId] lets the caller pivot on the *currently displayed* chapter
     * (i.e. the one the scroll listener has stamped into scrollDisplayEntryId)
     * rather than the last-tapped activeEntryId. Without it, manual scrolling
     * past several chapters and then tapping Prev/Next walks from the OLD tap,
     * not from where the user is actually reading — feels like a multi-chapter
     * jump. Caller passes null to fall back to activeEntryId.
     */
    fun goPrev(fromEntryId: String? = null) {
        val entries = _state.value.entries
        val pivot = fromEntryId ?: _state.value.activeEntryId
        val idx = entries.indexOfFirst { it.id == pivot }
        if (idx > 0) onEntryTapped(entries[idx - 1].id)
    }

    /** Walks to the next ToC entry (no wrap-around). See [goPrev] for fromEntryId. */
    fun goNext(fromEntryId: String? = null) {
        val entries = _state.value.entries
        val pivot = fromEntryId ?: _state.value.activeEntryId
        val idx = entries.indexOfFirst { it.id == pivot }
        if (idx in 0 until entries.lastIndex) onEntryTapped(entries[idx + 1].id)
    }

    /**
     * Optimistically updates the in-memory active id (so the WebView reacts
     * immediately), then persists it to Room. Persistence rewrites
     * manifest.json on disk via the repository.
     */
    fun onEntryTapped(entryId: String, fragment: String? = null) {
        // Reset on every tap: only a cross-document anchor link passes a
        // non-blank fragment; plain chapter / Prev-Next / ToC taps clear it so
        // a previously-queued anchor can't fire against this navigation.
        _pendingTappedFragment.value = fragment?.takeIf { it.isNotBlank() }
        val id = groupTabId ?: return
        _state.value = _state.value.copy(
            activeEntryId = entryId,
            lastTapSerial = _state.value.lastTapSerial + 1
        )
        viewModelScope.launch { repository.updateActiveEntry(id, entryId) }
    }

    /**
     * Persists scroll position for the OUTGOING entry. Called from the
     * screen on entry transitions and ON_PAUSE. anchor + occurrence are
     * always (null, 0) in v1 — only the ratio is meaningful.
     *
     * The write itself is not cancellable. The last call comes from the
     * screen's dispose, and in the Always on Top panel that dispose is
     * followed on the same call stack by the window's view model store
     * being cleared, which cancels this scope. A cancellable write was
     * cancelled before the IO thread reached it, every time, and the
     * panel's final position for the chapter was lost with it.
     */
    fun persistScrollRatio(entryId: String, ratio: Double) {
        val id = groupTabId ?: return
        viewModelScope.launch {
            withContext(NonCancellable) {
                repository.updateEntryScroll(
                    groupTabId = id,
                    entryId = entryId,
                    anchor = null,
                    occurrenceIndex = 0,
                    ratio = ratio
                )
            }
        }
    }

    /**
     * Where the store says this group is open, read fresh: the active
     * chapter and its saved ratio. Null until bound, or when the group has
     * no active chapter recorded.
     *
     * For the app's viewer coming back from Always on Top. The panel hosted
     * its own copy of this viewer over the same rows and wrote to them, and
     * this viewer's in-memory state knows nothing of that: [bind] seeds the
     * active entry once and the entries stream carries the ratio, but the
     * WebView is still where it was left.
     */
    suspend fun storedPosition(): StoredPosition? {
        val id = groupTabId ?: return null
        val entryId = savedTabDao.getTabById(id)?.groupActiveEntryId ?: return null
        val ratio = pageGroupEntryDao.getById(entryId)?.scrollRatio ?: return null
        return StoredPosition(entryId, ratio)
    }

    /**
     * Follows the store to [entryId] without writing it back. Not a tap: the
     * store already says this, and it was the panel that said it. The
     * screen's entry effect takes it from here as it would a tap, minus the
     * capture of the outgoing chapter, which the caller suppresses because
     * the WebView's position for that chapter is older than the store's.
     */
    fun adoptStoredEntry(entryId: String) {
        if (_state.value.activeEntryId == entryId) return
        _state.value = _state.value.copy(activeEntryId = entryId)
    }

    /** One chapter and how far down it the reader was. */
    data class StoredPosition(val entryId: String, val ratio: Double)

    /** Renames the group. Updates UI state immediately; DB write is async. */
    fun rename(newTitle: String) {
        val id = groupTabId ?: return
        _state.value = _state.value.copy(title = newTitle)
        viewModelScope.launch { repository.renameGroup(id, newTitle) }
    }

    /** Removes a single entry from the group. */
    fun removeEntry(entryId: String) {
        val id = groupTabId ?: return
        viewModelScope.launch { repository.removeEntry(id, entryId) }
    }

    /**
     * Deletes the group atomically (Room transaction + on-disk cleanup).
     * Suspends until the deletion is committed; callers can safely navigate
     * away after it returns. The screen is responsible for the calling
     * coroutine.
     */
    suspend fun delete() {
        val id = groupTabId ?: return
        repository.deleteGroup(id)
    }

    suspend fun addMarker(label: String, scrollPosition: Int, occurrenceIndex: Int) {
        val tabId = groupTabId ?: return
        val entryId = _state.value.activeEntryId ?: return
        // Result ignored; UI error feedback deferred to a future task.
        repository.addMarker(tabId, entryId, label, scrollPosition, occurrenceIndex)
    }

    suspend fun deleteMarker(markerId: String) {
        repository.deleteMarker(markerId)
    }

    fun enqueueScrollToMarker(
        label: String,
        scrollY: Int,
        occurrenceIndex: Int,
        flashHex: String? = null
    ) {
        _pendingScrollToMarker.value = PendingScrollMarker(label, scrollY, occurrenceIndex, flashHex)
    }

    fun consumePendingScrollToMarker(): PendingScrollMarker? {
        val v = _pendingScrollToMarker.value
        _pendingScrollToMarker.value = null
        return v
    }

    /**
     * Clear any queued scroll target WITHOUT consuming it as a value. Called
     * when the viewer screen disposes so a pending marker tap that never
     * landed doesn't fire against an unrelated entry the next time the viewer
     * opens (the VM survives the screen via Hilt's Activity scope).
     */
    fun clearPendingScrollToMarker() {
        _pendingScrollToMarker.value = null
    }

    /** Drains the queued cross-document anchor fragment (single-shot). */
    fun consumePendingTappedFragment(): String? {
        val v = _pendingTappedFragment.value
        _pendingTappedFragment.value = null
        return v
    }

    /** Clears the queued anchor fragment without consuming it (screen dispose). */
    fun clearPendingTappedFragment() {
        _pendingTappedFragment.value = null
    }

    /**
     * Deletes every scroll marker in the group. Group markers share
     * `saved_tab_id = groupTabId`, so a single `deleteForTab` covers all of
     * them. Not transactional with any other write, but it's the only path
     * that touches `scroll_markers` rows for this group while it runs
     * (sequential UI use), so a concurrent insert is not expected.
     */
    suspend fun deleteAllMarkersInGroup() {
        val id = groupTabId ?: return
        repository.deleteAllMarkersForGroup(id)
    }
}

/**
 * Captured by [PageGroupViewerViewModel.enqueueScrollToMarker] and consumed
 * by the screen's onPageFinished. The label drives [scrollToMarkerByText];
 * `fallbackScrollY` is used only if the text can't be found in the DOM.
 */
data class PendingScrollMarker(
    val label: String,
    val fallbackScrollY: Int,
    val occurrenceIndex: Int,
    /**
     * When non-null, the WebView consumer should pass this through to
     * [scrollToMarkerByText]'s `flashFocusHex` so the landed-on element
     * briefly tints with the focus colour. Saved-marker taps leave this
     * null (the F3 highlight span already marks the spot); the page-group
     * find pick sets it so the user can see the specific tapped match.
     */
    val flashHex: String? = null
)
