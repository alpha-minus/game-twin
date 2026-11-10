package com.twinlore.app.ui.screens.pdf

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.twinlore.app.domain.PdfPageBookmark
import com.twinlore.app.ui.screens.offline.ContentsTabRow

/**
 * Full-screen 2-tab overlay for the PDF viewer (Contents + Bookmarks).
 *
 * Mirrors `SingleGuideTocViewScreen` / `PageGroupTocViewScreen` shape; adapted
 * for the PDF data model — Contents come from the pdf.js outline (resolved to
 * page numbers), Bookmarks are page-pinned (no text anchor).
 *
 * Rendered as an in-screen overlay inside [PdfGuideViewer]: the viewer flips
 * `showTocView` and renders this Composable over the WebView body.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PdfTocViewScreen(
    outline: List<PdfOutlineEntry>,
    bookmarks: List<PdfPageBookmark>,
    currentPage: Int,
    focusColor: Color,
    tabIndex: Int,
    onTabIndexChange: (Int) -> Unit,
    onClose: () -> Unit,
    onTocEntryTapped: (pageNumber: Int) -> Unit,
    onBookmarkTapped: (PdfPageBookmark) -> Unit,
    onBookmarkLongPressDelete: (PdfPageBookmark) -> Unit,
    /** Room above the back button and tabs. The app's 24dp clears the
     *  vault-session top bar; the fold panel already pads below its own. */
    topSpacing: Dp = 24.dp,
) {
    // tabIndex hoisted to the caller (PdfGuideViewer) so the user's last
    // choice (Contents vs Bookmarks) survives closing and reopening the
    // overlay within the same PDF session — mirrors the offline guide
    // viewers' ToC overlay.

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 24dp top breathing room so the back button + tab row sit clearly
        // below the vault-session top bar (matches the other viewers).
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
                    Text("Bookmarks", modifier = Modifier.padding(12.dp))
                }
            }
        }
        when (tabIndex) {
            0 -> ContentsTab(
                outline = outline,
                currentPage = currentPage,
                focusColor = focusColor,
                onTap = onTocEntryTapped
            )
            1 -> BookmarksTab(
                bookmarks = bookmarks,
                currentPage = currentPage,
                focusColor = focusColor,
                onTap = onBookmarkTapped,
                onLongPressDelete = onBookmarkLongPressDelete
            )
        }
    }
}

@Composable
private fun ContentsTab(
    outline: List<PdfOutlineEntry>,
    currentPage: Int,
    focusColor: Color,
    onTap: (Int) -> Unit,
) {
    if (outline.isEmpty()) {
        EmptyText("No table of contents detected.")
        return
    }
    val flat = remember(outline) { flattenOutline(outline) }
    val listState = rememberLazyListState()
    LaunchedEffect(Unit) {
        val activeIndex = flat.indexOfFirst { it.first.pageNumber == currentPage }
        if (activeIndex < 0) return@LaunchedEffect
        val firstVisible = listState.firstVisibleItemIndex
        val visibleCount = listState.layoutInfo.visibleItemsInfo.size
        val lastVisible = firstVisible + visibleCount - 1
        if (activeIndex < firstVisible || activeIndex > lastVisible) {
            listState.scrollToItem(activeIndex)
        }
    }
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(bottom = bottomInset + 64.dp)
    ) {
        items(flat.size, key = { i -> "${flat[i].first.title}#${flat[i].first.pageNumber}#$i" }) { i ->
            val (entry, depth) = flat[i]
            if (i > 0) Divider(thickness = 0.5.dp)
            // Indent label by depth × 12dp via outer Row start-padding (ContentsTabRow
            // has its own 16dp horizontal padding).
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = (depth * 12).dp)
            ) {
                ContentsTabRow(
                    index = i,
                    label = entry.title,
                    isActive = entry.pageNumber > 0 && entry.pageNumber == currentPage,
                    failed = false,
                    focusColor = focusColor,
                    onTap = { if (entry.pageNumber > 0) onTap(entry.pageNumber) },
                    onLongPress = { /* no-op: TOC entries can't be deleted */ }
                )
            }
        }
    }
}

@Composable
private fun BookmarksTab(
    bookmarks: List<PdfPageBookmark>,
    currentPage: Int,
    focusColor: Color,
    onTap: (PdfPageBookmark) -> Unit,
    onLongPressDelete: (PdfPageBookmark) -> Unit,
) {
    if (bookmarks.isEmpty()) {
        EmptyText("No bookmarks yet. Tap the + icon in the top bar to save the current page.")
        return
    }
    val sorted = remember(bookmarks) { bookmarks.sortedBy { it.order } }
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = bottomInset + 64.dp)
    ) {
        items(sorted, key = { it.id }) { bm ->
            val isCurrent = bm.pageNumber == currentPage
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(bm.id) {
                        detectTapGestures(
                            onTap = { onTap(bm) },
                            onLongPress = { onLongPressDelete(bm) }
                        )
                    }
                    .background(
                        if (isCurrent) focusColor.copy(alpha = 0.12f) else Color.Transparent
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    text = bm.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Page ${bm.pageNumber}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
