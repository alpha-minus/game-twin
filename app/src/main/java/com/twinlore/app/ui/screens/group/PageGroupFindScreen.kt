package com.twinlore.app.ui.screens.group

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.twinlore.app.data.library.PageGroupFindEngine
import com.twinlore.app.data.local.PageGroupEntryEntity
import com.twinlore.app.ui.screens.RecentFindSearchRow
import com.twinlore.app.ui.screens.RecentFindSearches
import kotlinx.coroutines.delay

/**
 * Full-screen cross-page full-text search for a page group. J1 converted
 * this from a `ModalBottomSheet` to a standalone screen so it mirrors
 * [PageGroupTocViewScreen]: the back arrow shares the row with the query
 * input, the LazyColumn fills the rest of the body with bottom padding
 * that clears the vault-session bottom bar.
 *
 * Streams results from [PageGroupFindEngine.find] and groups them by their
 * attributed ToC entry, preserving the group's display order. Tapping a
 * snippet row calls [onPick] with the entry id, the current query, and the
 * 0-based per-entry occurrence index; the viewer can then route through
 * [PageGroupViewerViewModel.enqueueScrollToMarker] so the existing
 * `onPageFinished` consumer scrolls to AND flashes the exact match.
 *
 * Search is debounced 200 ms to avoid hammering the engine on every keystroke.
 * State is fully local — no ViewModel — since the screen is ephemeral.
 *
 * Recent searches ([RecentFindSearches]) list under the field while it is
 * empty. Because every keystroke searches, a query is recorded when it is
 * acted on (the keyboard's search key, a result pick, Clear) or is still in
 * the field when the screen goes, never per keystroke.
 *
 * With [onScopeChange] set, an All pages / This page switch sits under the
 * field; This page hands over to the find bar on the open page.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun PageGroupFindScreen(
    entries: List<PageGroupEntryEntity>,
    engine: PageGroupFindEngine,
    focusColor: Color,
    query: String,
    onQueryChange: (String) -> Unit,
    onDismiss: () -> Unit,
    /** Room above the search row. The app's 24dp clears the vault-session
     *  top bar; the fold panel already pads the viewer below its own. */
    topSpacing: Dp = 24.dp,
    onScopeChange: ((PageGroupFindScope) -> Unit)? = null,
    onPick: (entryId: String, query: String, occurrenceInEntry: Int) -> Unit
) {
    val currentQuery by rememberUpdatedState(query)
    val recents by RecentFindSearches.queries.collectAsState()
    DisposableEffect(Unit) { onDispose { RecentFindSearches.record(currentQuery) } }

    // K2: `query` is hoisted up to PageGroupViewerScreen so it persists
    // across close-reopen. Results, totals, and the searching flag remain
    // local — they re-derive from `query` in the LaunchedEffect below on
    // every re-mount, so persisting them would just be redundant.
    var results by remember { mutableStateOf(listOf<PageGroupFindEngine.Result>()) }
    var totalMatches by remember { mutableStateOf(0) }
    var filesWithMatches by remember { mutableStateOf(0) }
    var searching by remember { mutableStateOf(false) }

    LaunchedEffect(query) {
        // Reset on every new query, including blank.
        results = emptyList()
        totalMatches = 0
        filesWithMatches = 0
        if (query.isBlank()) {
            searching = false
            return@LaunchedEffect
        }
        delay(200)
        searching = true
        val seenFiles = mutableSetOf<String>()
        try {
            // Use collect (not collectLatest) — we're accumulating, not replacing.
            engine.find(entries, query).collect { r ->
                results = results + r
                totalMatches += 1
                if (seenFiles.add(r.mhtResourceId)) filesWithMatches += 1
            }
        } finally {
            searching = false
        }
    }

    // Focus the search field as soon as the screen opens so the keyboard
    // appears without a second tap. Mirrors the single-guide viewer's
    // FindOnPageUI behaviour (which routes through NavigableTextField's
    // startInEditMode focus path).
    val searchFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        // One-frame delay so the OutlinedTextField is laid out before we
        // ask for focus — requesting too early can drop the IME show.
        delay(50)
        searchFocusRequester.requestFocus()
        keyboardController?.show()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 24dp top breathing room so the OutlinedTextField clears the
        // vault-session top bar with a tighter margin (matches the ToC
        // overlay's spacing).
        Spacer(Modifier.height(topSpacing))
        // Back button shares the row with the query input so the screen
        // mirrors PageGroupTocViewScreen's chrome. The vault-session top
        // bar still names the doc above us.
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                label = { Text("Search") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    RecentFindSearches.record(query)
                    keyboardController?.hide()
                }),
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = {
                            RecentFindSearches.record(query)
                            onQueryChange("")
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear")
                        }
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp, top = 4.dp, bottom = 4.dp)
                    .focusRequester(searchFocusRequester)
            )
        }
        if (onScopeChange != null) {
            PageGroupFindScopeSwitch(
                scope = PageGroupFindScope.ALL_PAGES,
                focusColor = focusColor,
                onScopeChange = onScopeChange,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }
        if (query.isBlank() && recents.isNotEmpty()) {
            RecentFindSearchRow(
                queries = recents,
                focusColor = focusColor,
                onPick = { picked, _ ->
                    // The query drives the search below. Focus is left
                    // alone: the field would bring the keyboard back up,
                    // and the next D-pad press finds the result rows.
                    RecentFindSearches.record(picked)
                    onQueryChange(picked)
                    // Out of the way of the results it brings up.
                    keyboardController?.hide()
                },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }
        if (query.isNotBlank()) {
            Text(
                text = if (searching) "Searching… $totalMatches matches so far"
                       else "$totalMatches matches across $filesWithMatches pages",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }
        Divider()

        // Group results by entry, preserving the group's ToC order.
        val grouped = results.groupBy { it.entryId }
        val orderedKeys = entries.mapNotNull { e ->
            if (grouped.containsKey(e.id)) e.id else null
        }

        // Bottom contentPadding clears the vault-session bottom bar (same
        // pattern as G3 in PageGroupTocViewScreen.ContentsTab).
        val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = bottomInset + 64.dp)
        ) {
            orderedKeys.forEach { entryId ->
                val entry = entries.first { it.id == entryId }
                val hits = grouped.getValue(entryId)
                item(key = "header_$entryId") {
                    Text(
                        text = "${entry.position + 1}. ${entry.label} (${hits.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
                itemsIndexed(hits, key = { _, r -> "${r.entryId}_${r.matchOffset}" }) { idx, r ->
                    val annotated = remember(r.snippet, query, focusColor) {
                        highlightQuery("…${r.snippet}…", query, focusColor)
                    }
                    Text(
                        text = annotated,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                RecentFindSearches.record(query)
                                onPick(entry.id, query, r.occurrenceInEntry)
                            }
                            .padding(horizontal = 24.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (idx < hits.lastIndex) {
                        Divider(
                            thickness = 0.5.dp,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                    }
                }
            }

            if (query.isNotBlank() && !searching && results.isEmpty()) {
                item {
                    val fileCount = entries.distinctBy { it.mhtResourceId }.size
                    Text(
                        text = "No matches in $fileCount pages",
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

/**
 * Tints every case-insensitive occurrence of [query] inside [text] with a
 * translucent focus-colour background + SemiBold weight. Pure visual: the
 * snippet's own search-match emphasis lives inside the row text, not from
 * the engine. Returns the input unstyled if [query] is blank.
 */
private fun highlightQuery(text: String, query: String, focus: Color): AnnotatedString {
    if (query.isBlank()) return AnnotatedString(text)
    return buildAnnotatedString {
        var i = 0
        val lower = text.lowercase()
        val ql = query.lowercase()
        while (i < text.length) {
            val match = lower.indexOf(ql, i)
            if (match < 0) {
                append(text.substring(i))
                break
            }
            append(text.substring(i, match))
            withStyle(
                SpanStyle(
                    background = focus.copy(alpha = 0.35f),
                    fontWeight = FontWeight.SemiBold
                )
            ) {
                append(text.substring(match, match + query.length))
            }
            i = match + query.length
        }
    }
}
