package com.twinlore.app.ui.screens.group

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.twinlore.app.data.local.PageGroupEntryEntity
import com.twinlore.app.data.local.ScrollMarkerEntity
import com.twinlore.app.ui.screens.offline.ContentsTabRow
import com.twinlore.app.ui.screens.titleHasOwnEnumeration

/**
 * Full-screen 2-tab ToC view (Contents + Markers). The Anchors tab was
 * dropped in G2 — the underlying data layer (anchor entity, manifest mirror,
 * createGroup writes) stays put as a future fallback, but the UI no longer
 * surfaces it.
 *
 * Rendered as an in-screen overlay inside [PageGroupViewerScreen]: the
 * viewer flips `showTocView` and renders this Composable on top of the
 * WebView body (vault-session top + bottom chrome remain around it).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PageGroupTocViewScreen(
    entries: List<PageGroupEntryEntity>,
    activeEntryId: String?,
    markers: List<ScrollMarkerEntity>,
    focusColor: Color,
    tabIndex: Int,
    onTabIndexChange: (Int) -> Unit,
    onClose: () -> Unit,
    onEntryTapped: (entryId: String) -> Unit,
    onEntryLongPressRemove: (entryId: String) -> Unit,
    onMarkerTapped: (ScrollMarkerEntity) -> Unit,
    onMarkerLongPressDelete: (ScrollMarkerEntity) -> Unit,
    /** Room above the back button and tabs; see PageGroupFindScreen. */
    topSpacing: Dp = 24.dp,
) {
    // tabIndex is hoisted into the viewer so the user's last tab choice
    // (Contents vs Markers) survives closing and reopening the overlay.
    // Caller resets it when the bookmark / group changes.

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 24dp top breathing room so the back button + tab row sit clearly
        // below the vault-session top bar (matches PageGroupFindScreen).
        Spacer(Modifier.height(topSpacing))
        // Back button shares the row with the tabs so it isn't shadowed by the
        // surrounding vault-session top bar. `title` is omitted entirely — the
        // vault top bar already names the doc.
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
            TabRow(
                selectedTabIndex = tabIndex,
                modifier = Modifier.weight(1f)
            ) {
                Tab(selected = tabIndex == 0, onClick = { onTabIndexChange(0) }) {
                    Text("Contents", modifier = Modifier.padding(12.dp))
                }
                Tab(selected = tabIndex == 1, onClick = { onTabIndexChange(1) }) {
                    Text("Markers", modifier = Modifier.padding(12.dp))
                }
            }
        }

        when (tabIndex) {
            0 -> ContentsTab(
                entries = entries,
                activeEntryId = activeEntryId,
                focusColor = focusColor,
                onTap = onEntryTapped,
                onLongPressRemove = onEntryLongPressRemove
            )
            1 -> MarkersTab(
                markers = markers,
                entriesById = entries.associateBy { it.id },
                onTap = onMarkerTapped,
                onLongPressDelete = onMarkerLongPressDelete
            )
        }
    }
}

@Composable
private fun ContentsTab(
    entries: List<PageGroupEntryEntity>,
    activeEntryId: String?,
    focusColor: Color,
    onTap: (String) -> Unit,
    onLongPressRemove: (String) -> Unit
) {
    // Synthetic entries (body-only-discovered page URLs) are real saved pages
    // but they have no chapter label in the original ToC. Surface them only
    // through prev/next + in-group link nav, not in the Contents list — the
    // user would see "Page 4" / "Additional page" rows mixed in with named
    // chapters, which reads as noise.
    val displayEntries = remember(entries) { entries.filter { !it.synthetic } }
    if (displayEntries.isEmpty()) {
        EmptyText("No entries.")
        return
    }
    val listState = rememberLazyListState()
    // G4: auto-scroll so the active entry is visible when the Contents tab
    // is first shown. ContentsTab only enters composition when tabIndex==0,
    // so `LaunchedEffect(Unit)` runs once per tab switch in — exactly the
    // desired trigger. We skip the animation: the user expects to LAND on
    // the active entry, not watch it scroll into view.
    LaunchedEffect(Unit) {
        if (activeEntryId == null) return@LaunchedEffect
        val activeIndex = displayEntries.indexOfFirst { it.id == activeEntryId }
        if (activeIndex < 0) return@LaunchedEffect
        val firstVisible = listState.firstVisibleItemIndex
        val visibleCount = listState.layoutInfo.visibleItemsInfo.size
        val lastVisible = firstVisible + visibleCount - 1
        // Only scroll if the active row isn't already on-screen.
        if (activeIndex < firstVisible || activeIndex > lastVisible) {
            listState.scrollToItem(activeIndex)
        }
    }
    // G3: bottom contentPadding so the last entry isn't covered by the
    // vault-session bottom bar. nav-insets + a generous gutter (covers the
    // 56dp bar + 8dp breathing room).
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // Guide chapters often carry the guide's own numbering ("II. Weapons") —
    // adding our running index on top reads as double numbering.
    val showIndex =
        displayEntries.count { titleHasOwnEnumeration(it.label) } < displayEntries.size / 2 + 1
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(bottom = bottomInset + 64.dp)
    ) {
        items(displayEntries.size, key = { i -> displayEntries[i].id }) { i ->
            val e = displayEntries[i]
            if (i > 0) Divider(thickness = 0.5.dp)
            ContentsTabRow(
                index = i,
                label = e.label,
                isActive = e.id == activeEntryId,
                failed = e.mhtResourceId == null,
                focusColor = focusColor,
                onTap = { onTap(e.id) },
                onLongPress = { onLongPressRemove(e.id) },
                showIndex = showIndex,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MarkersTab(
    markers: List<ScrollMarkerEntity>,
    entriesById: Map<String, PageGroupEntryEntity>,
    onTap: (ScrollMarkerEntity) -> Unit,
    onLongPressDelete: (ScrollMarkerEntity) -> Unit
) {
    if (markers.isEmpty()) {
        EmptyText("No markers yet. Long-press any text in the page and choose Save Marker.")
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(markers, key = { it.id }) { m ->
            val entryLabel = m.pageGroupEntryId
                ?.let { entriesById[it]?.label }
                ?: "—"
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    // combinedClickable instead of pointerInput so D-pad can
                    // focus the row; same fix as ContentsTabRow.
                    .combinedClickable(
                        onClick = { onTap(m) },
                        onLongClick = { onLongPressDelete(m) }
                    )
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(
                    text = m.label.ifBlank { "(no text)" },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = entryLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Divider(thickness = 0.5.dp)
        }
    }
}

@Composable
private fun EmptyText(message: String) {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(message, style = MaterialTheme.typography.bodyMedium)
    }
}
