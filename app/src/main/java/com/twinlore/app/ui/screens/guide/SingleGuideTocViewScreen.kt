package com.twinlore.app.ui.screens.guide

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.twinlore.app.domain.ScrollMarker
import com.twinlore.app.ui.screens.offline.ContentsTabRow
import com.twinlore.app.ui.screens.titleHasOwnEnumeration

/**
 * Full-screen 2-tab overlay for single-guide (Contents + Markers).
 *
 * Mirrors `PageGroupTocViewScreen` shape; adapted for single-guide data
 * model — entries come from the live JS anchor-cluster extractor, markers
 * are scoped to one bookmark (no entry-label column).
 *
 * Rendered as an in-screen overlay: viewer flips `showTocView` and
 * renders this Composable over the WebView body.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SingleGuideTocViewScreen(
    entries: List<SingleGuideTocEntry>,
    activeFragment: String?,
    markers: List<ScrollMarker>,
    focusColor: Color,
    tabIndex: Int,
    onTabIndexChange: (Int) -> Unit,
    onClose: () -> Unit,
    onEntryTapped: (fragment: String) -> Unit,
    onMarkerTapped: (ScrollMarker) -> Unit,
    onMarkerLongPressDelete: (ScrollMarker) -> Unit,
    /** Room above the back button and tabs. The app's 24dp clears the
     *  vault-session top bar; the fold panel already pads below its own. */
    topSpacing: Dp = 24.dp,
) {
    // tabIndex is hoisted into the viewer so the user's last tab choice
    // (Contents vs Markers) survives closing and reopening the overlay.

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 24dp top breathing room so the back button + tab row sit clearly
        // below the vault-session top bar (matches PageGroupTocViewScreen).
        Spacer(Modifier.height(topSpacing))
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
                activeFragment = activeFragment,
                focusColor = focusColor,
                onTap = onEntryTapped
            )
            1 -> MarkersTab(
                markers = markers,
                onTap = onMarkerTapped,
                onLongPressDelete = onMarkerLongPressDelete
            )
        }
    }
}

@Composable
private fun ContentsTab(
    entries: List<SingleGuideTocEntry>,
    activeFragment: String?,
    focusColor: Color,
    onTap: (String) -> Unit
) {
    if (entries.isEmpty()) {
        EmptyText("No chapters detected.")
        return
    }
    val listState = rememberLazyListState()
    // Anchored on the active chapter row (or the first) so dpad up/down works the
    // moment the overlay opens, instead of the first press landing on default focus.
    val initialFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    val focusIndex = entries.indexOfFirst { it.fragment == activeFragment }.coerceAtLeast(0)
    LaunchedEffect(Unit) {
        if (activeFragment != null) {
            val activeIndex = entries.indexOfFirst { it.fragment == activeFragment }
            if (activeIndex >= 0) {
                val firstVisible = listState.firstVisibleItemIndex
                val visibleCount = listState.layoutInfo.visibleItemsInfo.size
                val lastVisible = firstVisible + visibleCount - 1
                if (activeIndex < firstVisible || activeIndex > lastVisible) {
                    listState.scrollToItem(activeIndex)
                }
            }
        }
        // One frame so the (possibly just-scrolled-in) row's FocusRequester is attached.
        withFrameNanos {}
        try { initialFocus.requestFocus() } catch (_: Throwable) {}
    }
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // Guide chapters often carry the guide's own numbering ("II. Weapons") —
    // adding our running index on top reads as double numbering.
    val showIndex = entries.count { titleHasOwnEnumeration(it.label) } < entries.size / 2 + 1
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(bottom = bottomInset + 64.dp)
    ) {
        items(entries.size, key = { i -> entries[i].fragment }) { i ->
            val e = entries[i]
            if (i > 0) Divider(thickness = 0.5.dp)
            ContentsTabRow(
                index = i,
                label = e.label,
                isActive = e.fragment == activeFragment,
                failed = false,
                focusColor = focusColor,
                onTap = { onTap(e.fragment) },
                onLongPress = { /* no remove action for single-guide */ },
                showIndex = showIndex,
                modifier = if (i == focusIndex) {
                    Modifier.focusRequester(initialFocus)
                } else Modifier,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MarkersTab(
    markers: List<ScrollMarker>,
    onTap: (ScrollMarker) -> Unit,
    onLongPressDelete: (ScrollMarker) -> Unit
) {
    if (markers.isEmpty()) {
        EmptyText("No markers yet. Long-press any text in the page and choose Save Marker.")
        return
    }
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = bottomInset + 64.dp)
    ) {
        items(markers, key = { it.id }) { m ->
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
