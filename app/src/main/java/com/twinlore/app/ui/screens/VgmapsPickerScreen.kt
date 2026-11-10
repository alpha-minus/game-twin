@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package com.twinlore.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import com.twinlore.app.ui.utils.FeedbackEvent
import com.twinlore.app.ui.utils.FeedbackManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.twinlore.app.data.vgmaps.VgmapsCredit
import com.twinlore.app.data.vgmaps.VgmapsEntry
import com.twinlore.app.data.vgmaps.VgmapsMap
import com.twinlore.app.ui.components.ImageZoomViewer

// Extra bottom space so the last grid row clears the system gesture bar and
// the vault-session chrome below the Dialog. systemBars insets aren't always
// propagated reliably inside Dialog scopes (observed on Galaxy S24: ~24dp),
// so this hardcoded value is intentionally larger.
private const val GRID_BOTTOM_PADDING_DP = 96

/**
 * Full-screen picker dialog: search vgmaps for the current vault's game,
 * show the maps, let the user select a subset and trigger a bulk download.
 *
 * @param gameName vault game name (used for the match and dialog title).
 * @param onDismiss closes the picker without starting a download.
 * @param onDownload called with the user's selected maps; the caller is
 *   responsible for dismissing the dialog and starting the downloader.
 * @param onOpenDirectory called when the user taps "Open VGMaps directory"
 *   from the no-match state.
 * @param onOpenHomepage called when the user taps the homepage icon in the top bar;
 *   should open https://www.vgmaps.com/ in the Browser tab.
 */
@Composable
fun VgmapsPickerScreen(
    gameName: String,
    onDismiss: () -> Unit,
    onDownload: (List<VgmapsMap>) -> Unit,
    onOpenDirectory: () -> Unit,
    onOpenHomepage: () -> Unit = {},
) {
    val hiltVm: HiltVgmapsPickerViewModel = hiltViewModel()
    val vm = remember(gameName) { hiltVm.forGame(gameName) }
    val state by vm.state.collectAsState()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
        ),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                val ready = state as? VgmapsPickerState.Ready
                TopBar(
                    gameName = gameName,
                    onBack = onDismiss,
                    onOpenHomepage = onOpenHomepage,
                    // Download is pinned in the top bar so it stays visible while
                    // the map grid scrolls. Only surfaced once maps are Ready.
                    selectedCount = ready?.selectedUrls?.size,
                    onDownload = ready?.let { { onDownload(vm.selectedMaps()) } },
                )
                when (val s = state) {
                    is VgmapsPickerState.Loading -> LoadingState(gameName)
                    is VgmapsPickerState.NoMatch -> NoMatchState(gameName, onOpenDirectory)
                    is VgmapsPickerState.FetchError -> FetchErrorState(s.message)
                    is VgmapsPickerState.Ready -> ReadyState(
                        state = s,
                        onSelectEntry = vm::onSelectEntry,
                        onToggle = vm::onToggle,
                        onSelectAll = vm::onSelectAll,
                        onClear = vm::onClear,
                    )
                }
            }
        }
    }
}

@Composable
private fun TopBar(
    gameName: String,
    onBack: () -> Unit,
    onOpenHomepage: () -> Unit = {},
    selectedCount: Int? = null,
    onDownload: (() -> Unit)? = null,
) {
    val view = LocalView.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
        }
        Text(
            text = "VGMaps for $gameName",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .padding(start = 4.dp)
                .weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        // Visit vgmaps.com in the browser.
        IconButton(onClick = onOpenHomepage) {
            Icon(
                imageVector = Icons.Filled.OpenInBrowser,
                contentDescription = "Open vgmaps.com",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Download pinned here (top bar) so it stays reachable no matter how far
        // the map grid is scrolled. Present only in the Ready state; disabled
        // until at least one map is selected.
        if (onDownload != null) {
            Button(
                onClick = {
                    FeedbackManager.play(view, FeedbackEvent.CONFIRM)
                    onDownload()
                },
                enabled = (selectedCount ?: 0) > 0,
                contentPadding = PaddingValues(horizontal = 12.dp),
                modifier = Modifier.padding(start = 4.dp, end = 4.dp),
            ) {
                Text("Download (${selectedCount ?: 0})")
            }
        }
    }
}

@Composable
private fun LoadingState(gameName: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Text(
                text = "Searching VGMaps for $gameName",
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

@Suppress("UNUSED_PARAMETER")
@Composable
private fun NoMatchState(gameName: String, onOpenDirectory: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(24.dp),
        ) {
            Text(
                text = "Game not found",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Button(onClick = onOpenDirectory) {
                Icon(Icons.Filled.OpenInBrowser, contentDescription = null)
                Text("Open VGMaps", modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun FetchErrorState(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(24.dp),
        ) {
            Text(
                text = "Couldn't load maps",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(text = message, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ReadyState(
    state: VgmapsPickerState.Ready,
    onSelectEntry: (VgmapsEntry) -> Unit,
    onToggle: (String) -> Unit,
    onSelectAll: () -> Unit,
    onClear: () -> Unit,
) {
    // The entire picker body below the top bar is one scrollable surface: chips,
    // action bar (Select all / Clear), credits, and the map grid all live inside a
    // single LazyVerticalGrid with full-span header items. Earlier layout kept
    // chips/action-bar/credits as siblings of a weighted grid; once the chip labels
    // grew (title + system) the grid was crowded to a sliver. The Download button is
    // pinned in the top bar (see TopBar) so it stays reachable while this scrolls.
    val view = LocalView.current
    var zoomTarget by remember { mutableStateOf<String?>(null) }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 160.dp),
        contentPadding = PaddingValues(start = 8.dp, top = 8.dp, end = 8.dp, bottom = GRID_BOTTOM_PADDING_DP.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        // Disambiguation chips (full-span header), only if 2+ entries. Labels are
        // "<title> (<system>)" so the user disambiguates by game (the matcher ranks
        // across all consoles); the parenthesized system is only the secondary
        // tiebreaker for same-title entries (e.g. SOTN on PSX vs Saturn). FlowRow
        // because full titles overflow a single line once you have 2-4 of them.
        if (state.entries.size >= 2) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    state.entries.forEach { e ->
                        FilterChip(
                            selected = e == state.selectedEntry,
                            onClick = {
                                FeedbackManager.play(view, FeedbackEvent.SWITCH)
                                onSelectEntry(e)
                            },
                            label = { Text("${e.title} (${e.system})") },
                            colors = FilterChipDefaults.filterChipColors(),
                        )
                    }
                }
            }
        }
        // Action bar (full-span header).
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(onClick = onSelectAll) { Text("Select all") }
                TextButton(onClick = onClear) { Text("Clear") }
            }
        }
        // Credits row (full-span header), capped at 12 to avoid pushing the grid
        // down on long lists.
        if (state.credits.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                val displayCredits = if (state.credits.size > 12) state.credits.take(12) else state.credits
                val overflow = state.credits.size - displayCredits.size
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        "Maps by:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    displayCredits.forEachIndexed { index, credit ->
                        // Comma stays attached to its name so the FlowRow never
                        // wraps it onto a line by itself. Skipped on the last item
                        // unless a "+N more" tail follows.
                        val isLastVisible = index == displayCredits.lastIndex && overflow == 0
                        val suffix = if (isLastVisible) "" else ","
                        Text(
                            text = credit.name + (if (credit.email != null) " <${credit.email}>" else "") + suffix,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                        )
                    }
                    if (overflow > 0) {
                        Text(
                            text = "+$overflow more",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        // Map tiles.
        items(state.maps, key = { it.imageUrl }) { m ->
            Tile(
                map = m,
                selected = m.imageUrl in state.selectedUrls,
                onToggle = { onToggle(m.imageUrl) },
                onZoom = { zoomTarget = m.imageUrl },
            )
        }
    }
    zoomTarget?.let { url ->
        // Browse-only fetch: don't persist the full-resolution VGMaps image to
        // Coil's disk cache. The picker is exploratory — most images the user
        // zooms into never get downloaded. A single full-res poster can be
        // 150 MB+ and was previously monopolising image_cache. Memory cache
        // stays enabled so re-opening the same zoom within the session is free.
        val context = LocalContext.current
        val request = remember(url) {
            ImageRequest.Builder(context)
                .data(url)
                .diskCachePolicy(CachePolicy.DISABLED)
                .build()
        }
        ImageZoomViewer(model = request, onDismiss = { zoomTarget = null })
    }
}

@Composable
private fun Tile(
    map: VgmapsMap,
    selected: Boolean,
    onToggle: () -> Unit,
    onZoom: () -> Unit,
) {
    val view = LocalView.current
    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable {
                    FeedbackManager.play(view, FeedbackEvent.CONFIRM)
                    onZoom()
                },
        ) {
            // Same browse-only policy as the zoom viewer below: thumbnails get
            // memory-cached for the session but never written to image_cache. The
            // user has either downloaded the map (then the VgmapsBulkDownloader
            // owns the bytes) or they haven't, and we shouldn't be quietly hoarding
            // dozens of thumbs after a single "open VGMaps for this game" excursion.
            val context = LocalContext.current
            val thumbRequest = remember(map.imageUrl) {
                ImageRequest.Builder(context)
                    .data(map.imageUrl)
                    .diskCachePolicy(CachePolicy.DISABLED)
                    .build()
            }
            AsyncImage(
                model = thumbRequest,
                contentDescription = map.caption,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.45f)),
            ) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = {
                        FeedbackManager.play(view, FeedbackEvent.CONFIRM)
                        onToggle()
                    },
                )
            }
        }
        Text(
            text = map.caption,
            style = MaterialTheme.typography.bodySmall,
            fontSize = 12.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
