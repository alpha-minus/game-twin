package com.twinlore.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.rememberLazyListState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.zIndex
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.twinlore.app.ui.utils.FeedbackEvent
import com.twinlore.app.ui.utils.FeedbackManager
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.twinlore.app.domain.ChecklistItem
import com.twinlore.app.domain.ChecklistSection
import com.twinlore.app.domain.ChecklistTab
import com.twinlore.app.ui.LocalCachedFocusColor
import com.twinlore.app.ui.MainViewModel
import androidx.compose.foundation.shape.RoundedCornerShape
import com.twinlore.app.ui.screens.checklist.AiPromptCopiedDialog
import com.twinlore.app.ui.screens.checklist.ChecklistEmptyStateCard
import com.twinlore.app.ui.screens.checklist.ChecklistItemRow
import com.twinlore.app.ui.screens.checklist.ChecklistPasteSheet
import com.twinlore.app.ui.screens.checklist.ChecklistProgressRing
import com.twinlore.app.ui.screens.checklist.ChecklistSectionCard
import com.twinlore.app.ui.screens.checklist.ChecklistTabBar
import com.twinlore.app.ui.screens.checklist.ChecklistTapGame
import com.twinlore.app.ui.theme.ChecklistColorPalette
import com.twinlore.app.ui.utils.ImagePathUtils

@Composable
fun ChecklistScreen(
    vaultId: String,
    isTabVisible: Boolean,
    onNavigateToBrowser: (searchQuery: String) -> Unit,
    modifier: Modifier = Modifier,
    mainViewModel: MainViewModel,
    contentFocusRequester: FocusRequester? = null,
    // Top inset applied to the scrollable content only — NOT the root, so the
    // blurred background fills the full tab (incl. the strip behind the top bar).
    topContentInset: Dp = 0.dp,
    viewModel: ChecklistViewModel = hiltViewModel(),
) {
    // Pick the palette color closest to the app's current focus/accent so a
    // brand-new vault's default Checklist tab matches the visual identity
    // instead of always being blue.
    val focusColorForDefault = LocalCachedFocusColor.current?.value
        ?: MaterialTheme.colorScheme.primary
    val preferredTabColorIndex = remember(focusColorForDefault) {
        ChecklistColorPalette.closestTo(focusColorForDefault)
    }
    LaunchedEffect(vaultId) { viewModel.load(vaultId, preferredTabColorIndex) }

    val vaultNameRaw by viewModel.vaultName.collectAsState()
    val vaultName = if (vaultNameRaw.isNotBlank()) vaultNameRaw else "Checklist"
    val vaultArtworkResourceId by viewModel.vaultArtworkResourceId.collectAsState()
    val vaultArtworkUri = com.twinlore.app.ui.utils.rememberResourceImage(vaultArtworkResourceId)

    val tabs by viewModel.tabs.collectAsState()
    val activeTabId by viewModel.activeTabId.collectAsState()
    val sectionsWithItems by viewModel.sectionsWithItems.collectAsState()
    val pasteOpen by viewModel.pasteSheetOpen.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val hideCompleted by viewModel.hideCompleted.collectAsState()
    val completionByTabId by viewModel.completionByTabId.collectAsState()

    val context = LocalContext.current
    val view = LocalView.current
    val clipboard = remember {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }

    // Simple copied-to-clipboard confirmation dialog. Shown only when the
    // paste sheet is NOT open; when it is, the sheet's own button-swap flash
    // is the confirmation.
    var aiCopiedDialog by remember { mutableStateOf<Pair<String, String>?>(null) }
    // Same pattern as aiCopiedDialog — title/body shown via AlertDialog so the
    // import confirmation looks like every other in-app popup instead of a
    // hidden-behind-the-bottom-bar snackbar.
    var importDoneDialog by remember { mutableStateOf<Pair<String, String>?>(null) }
    // Remember which way the paste sheet was opened — "AI prompt" shortcut
    // wants Step 1 expanded; everything else keeps the default (collapsed).
    // Reset to false whenever the sheet closes so the next plain-open is clean.
    var pasteStartWithAi by remember { mutableStateOf(false) }
    var tapGameOpen by remember { mutableStateOf(false) }
    val tapGameHighScore by viewModel.tapGameHighScore.collectAsState()

    var createTabOpen by remember { mutableStateOf(false) }
    var showRenameTabDialog by remember { mutableStateOf(false) }
    var showRecolorTabDialog by remember { mutableStateOf(false) }
    var confirmDeleteTab by remember { mutableStateOf<ChecklistTab?>(null) }
    var confirmDeleteSection by remember { mutableStateOf<ChecklistSection?>(null) }
    var confirmDeleteItem by remember { mutableStateOf<ChecklistItem?>(null) }
    var newSectionDraft by remember(activeTabId) { mutableStateOf<String?>(null) }
    // Flag set when the user just created a section — a LaunchedEffect down
    // below watches section count and scrolls to the bottom once the new
    // section appears in the list, then clears the flag.
    var pendingScrollToNewSection by remember(activeTabId) { mutableStateOf(false) }
    var searchExpanded by remember { mutableStateOf(false) }
    // Same pattern as the item/section rename fields: onFocusChanged fires once
    // with isFocused=false before requestFocus resolves, so we guard on whether
    // focus has actually been gained before treating focus-loss as a collapse signal.
    var searchHasBeenFocused by remember { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }

    // Scroll-direction chrome: show on scroll-up, hide on scroll-down, always show at top
    var showChrome by remember { mutableStateOf(true) }
    LaunchedEffect(showChrome) {
        mainViewModel.setVaultSessionChecklistChromeVisible(showChrome)
    }

    val snackbarHost = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.events.collect { ev ->
            when (ev) {
                is ChecklistViewModel.OneShotEvent.CopiedPrompt -> {
                    clipboard.setPrimaryClip(ClipData.newPlainText("AI Prompt", ev.promptText))
                    // Dialog only when the sheet isn't already showing its
                    // own "✓ Copied" flash on the tapped button.
                    if (!pasteOpen) {
                        aiCopiedDialog = "Prompt copied" to
                            "Paste it into your AI tool of your choice."
                    }
                }
                is ChecklistViewModel.OneShotEvent.CopiedPromptWithGuide -> {
                    clipboard.setPrimaryClip(ClipData.newPlainText("AI Prompt", ev.promptText))
                    if (!pasteOpen) {
                        val n = ev.guideUrls.size
                        val title = if (n == 1) "Prompt + guide copied" else "Prompt + $n guides copied"
                        val body = if (n == 1) {
                            "Prompt + 1 guide reference copied. Paste it into an AI tool that can browse the web."
                        } else {
                            "Prompt + $n guide references copied. Paste it into an AI tool that can browse the web."
                        }
                        aiCopiedDialog = title to body
                    }
                }
                is ChecklistViewModel.OneShotEvent.OpenBrowserSearch -> onNavigateToBrowser(ev.query)
                is ChecklistViewModel.OneShotEvent.ImportComplete -> {
                    val parts = buildList {
                        if (ev.tabsCreated > 0) add("${ev.tabsCreated} ${if (ev.tabsCreated == 1) "tab" else "tabs"}")
                        if (ev.sectionsCreated > 0) add("${ev.sectionsCreated} ${if (ev.sectionsCreated == 1) "section" else "sections"}")
                        if (ev.itemsSkipped > 0) add("${ev.itemsSkipped} duplicate${if (ev.itemsSkipped == 1) "" else "s"} skipped")
                    }
                    val itemWord = if (ev.itemsAdded == 1) "item" else "items"
                    val totalCreated = ev.itemsAdded + ev.tabsCreated + ev.sectionsCreated
                    val title = if (ev.itemsAdded > 0) {
                        "Imported ${ev.itemsAdded} $itemWord"
                    } else if (totalCreated > 0) {
                        "Imported"
                    } else {
                        "Nothing imported"
                    }
                    val body = if (parts.isEmpty()) {
                        if (totalCreated > 0) "All set." else "No new items were added."
                    } else {
                        parts.joinToString(" · ")
                    }
                    importDoneDialog = title to body
                }
                is ChecklistViewModel.OneShotEvent.Error -> {
                    snackbarHost.showSnackbar("Error: ${ev.message}")
                }
            }
        }
    }

    LaunchedEffect(tabs, activeTabId, isTabVisible) {
        if (!isTabVisible) return@LaunchedEffect
        mainViewModel.controllerAction.collect { action ->
            if (tabs.isEmpty()) return@collect
            when (action) {
                MainViewModel.ControllerAction.CHECKLIST_TAB_NEXT -> {
                    val idx = tabs.indexOfFirst { it.id == activeTabId }
                    val next = if (idx < 0) 0 else (idx + 1) % tabs.size
                    viewModel.selectTab(tabs[next].id)
                }
                MainViewModel.ControllerAction.CHECKLIST_TAB_PREV -> {
                    val idx = tabs.indexOfFirst { it.id == activeTabId }
                    val prev = if (idx < 0) 0 else (idx - 1 + tabs.size) % tabs.size
                    viewModel.selectTab(tabs[prev].id)
                }
                else -> Unit
            }
        }
    }

    val activeTab = tabs.firstOrNull { it.id == activeTabId }
    val tabColor = activeTab?.let { ChecklistColorPalette.color(it.color) }
        ?: MaterialTheme.colorScheme.primary

    val menuBg = MaterialTheme.colorScheme.background
    val accentColor = LocalCachedFocusColor.current?.value ?: MaterialTheme.colorScheme.primary
    val artworkModel = remember(vaultArtworkUri, vaultId) {
        vaultArtworkUri?.let { ImagePathUtils.toImageData(it.toString(), vaultId) }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(menuBg),
    ) {
        // Blurred vault artwork background — matches AchievementDrawer pattern:
        // blur(24dp) + alpha(0.5f) on the image, then a 50% menuBg scrim on top.
        if (artworkModel != null) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(artworkModel)
                    .crossfade(true)
                    .size(200)
                    .build(),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(radius = 24.dp)
                    .alpha(0.5f),
                contentScale = ContentScale.Crop,
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(menuBg.copy(alpha = 0.50f)),
            )
        }

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            // No window-inset padding. The parent VaultSessionScreen already
            // reserves the top-bar height, so the Scaffold's default top inset
            // was an extra empty (transparent) strip stacked on top of it.
            contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
            snackbarHost = {
                // Lifted above the app's CompactBottomBar (which lives outside
                // this Scaffold) so transient errors aren't hidden behind it.
                // Success messages go through AlertDialog instead — see
                // importDoneDialog / aiCopiedDialog.
                SnackbarHost(
                    hostState = snackbarHost,
                    modifier = Modifier.padding(bottom = 96.dp),
                )
            },
        ) { padding ->
            // Shared by the FAB and scroll-to-top.
            val savedScroll = remember { viewModel.getSavedScrollPosition() }
            val listState = rememberLazyListState(
                initialFirstVisibleItemIndex = savedScroll?.first ?: 0,
                initialFirstVisibleItemScrollOffset = savedScroll?.second ?: 0,
            )
            val scrollScope = rememberCoroutineScope()

            // Persist scroll position so it survives vault session restarts.
            androidx.compose.runtime.DisposableEffect(Unit) {
                onDispose {
                    viewModel.saveScrollPosition(
                        listState.firstVisibleItemIndex,
                        listState.firstVisibleItemScrollOffset,
                    )
                }
            }

            LaunchedEffect(isTabVisible) {
                if (!isTabVisible) return@LaunchedEffect
                val analogDeadzone = 0.1f
                val pxPerFrame = 56f
                while (true) {
                    val v = mainViewModel.vaultSessionListScroll.value
                    if (kotlin.math.abs(v) > analogDeadzone) {
                        listState.scrollBy(v * pxPerFrame)
                        kotlinx.coroutines.delay(16)
                    } else {
                        kotlinx.coroutines.delay(200)
                    }
                }
            }

            // Scroll-direction chrome: show on scroll-up, hide on scroll-down, always show at top
            LaunchedEffect(listState, isTabVisible) {
                if (!isTabVisible) return@LaunchedEffect
                var prevIndex = listState.firstVisibleItemIndex
                var prevOffset = listState.firstVisibleItemScrollOffset
                snapshotFlow {
                    listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
                }.collect { (index, offset) ->
                    val next = when {
                        index == 0 && offset == 0 -> true
                        index < prevIndex || (index == prevIndex && offset < prevOffset) -> true
                        index > prevIndex || (index == prevIndex && offset > prevOffset) -> false
                        else -> showChrome
                    }
                    prevIndex = index
                    prevOffset = offset
                    if (next != showChrome) showChrome = next
                }
            }

            val filteredSections = sectionsWithItems
                .mapNotNull { swi ->
                    val sectionMatches = searchQuery.isNotBlank() &&
                        swi.section.name.contains(searchQuery, ignoreCase = true)

                    var items = swi.items
                    if (hideCompleted) items = items.filter { !it.completed }
                    // Only filter items by query when the SECTION name doesn't already match.
                    // A section-name match surfaces the whole section (all items still visible
                    // after the hide-completed filter).
                    if (searchQuery.isNotBlank() && !sectionMatches) {
                        items = items.filter { it.title.contains(searchQuery, ignoreCase = true) }
                    }
                    val keep = when {
                        // Section name match always wins when searching.
                        sectionMatches -> true
                        // Hide-completed: drop the section entirely if there
                        // are no remaining (uncompleted) items, including
                        // sections that were always empty.
                        hideCompleted -> items.isNotEmpty()
                        // Search active without a name match: only keep when
                        // some items match the query.
                        searchQuery.isNotBlank() -> items.isNotEmpty()
                        // No filters active: show everything.
                        else -> true
                    }
                    if (keep) swi.copy(items = items) else null
                }

            // Show the empty-state card only when the tab has zero sections.
            // Once at least one section exists (even empty), that section's
            // card handles its own "add item" flow — no need for a blanket
            // empty state that would hide the section card.
            val showEmptyState = sectionsWithItems.isEmpty() && newSectionDraft == null

            // Drag-reorder with live snap. A non-null `sectionDragList` means a
            // section is currently being dragged — render from it so the list
            // visibly reorders under the finger instead of waiting for release.
            var sectionDragList by remember(activeTabId) {
                mutableStateOf<List<ChecklistViewModel.SectionWithItems>?>(null)
            }
            var sectionDragIndex by remember(activeTabId) { mutableStateOf(-1) }
            var sectionDragOffset by remember(activeTabId) { mutableStateOf(0f) }
            var sectionHeightPx by remember(activeTabId) { mutableStateOf(0f) }
            var flashSectionId by remember(activeTabId) { mutableStateOf<String?>(null) }
            // Live references so the section drag pointerInput (keyed on
            // section.id, so cached across recompositions) always reads the
            // latest filteredSections rather than the list captured at its
            // first composition — same class of bug as the item/tab drag.
            val currentFilteredSections by rememberUpdatedState(filteredSections)

            LaunchedEffect(flashSectionId) {
                if (flashSectionId != null) {
                    delay(700)
                    flashSectionId = null
                }
            }

            // After a new section is created, scroll the list to the bottom
            // so the user lands on (and can immediately interact with) the
            // section they just made. Keyed on filteredSections so it fires
            // once the new section actually appears in the rendered list.
            // Number of non-section items at the top of the LazyColumn
            // (compact header + divider).
            val headerItemCount = 2
            LaunchedEffect(filteredSections, pendingScrollToNewSection) {
                if (pendingScrollToNewSection && filteredSections.isNotEmpty()) {
                    // Wait a frame for the LazyColumn to lay out the new item.
                    delay(50)
                    listState.animateScrollToItem(headerItemCount + filteredSections.size - 1)
                    pendingScrollToNewSection = false
                }
            }

            // Defensive: if the committed filteredSections already matches the
            // drag list, the gesture ended without onDragEnd/onDragCancel firing.
            // Clear drag state so nothing stays visually stuck mid-translation.
            LaunchedEffect(filteredSections) {
                val staged = sectionDragList
                if (staged != null && staged.map { it.section.id } == filteredSections.map { it.section.id }) {
                    sectionDragList = null
                    sectionDragIndex = -1
                    sectionDragOffset = 0f
                }
            }

            val renderedSections = sectionDragList ?: filteredSections

            // Tap-outside clears focus from inline fields.
            val focusManager = LocalFocusManager.current
            Box(
                modifier = Modifier
                    .padding(padding)
                    .padding(top = topContentInset)
                    .fillMaxSize()
                    .imePadding(),
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 0.dp, bottom = 140.dp),
                ) {
                    // --- Compact header: ring left, tabs + actions right ---
                    item(key = "header") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 12.dp, end = 12.dp, top = 16.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            ChecklistProgressRing(
                                tabs = tabs,
                                completionByTabId = completionByTabId,
                                activeTabId = activeTabId,
                                ringSize = 72.dp,
                                strokeWidth = 6.dp,
                                showCaption = false,
                                onDoubleTap = { tapGameOpen = true },
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                // Row 1: Tab bar
                                ChecklistTabBar(
                                    tabs = tabs,
                                    activeTabId = activeTabId,
                                    completionByTabId = completionByTabId,
                                    onSelectTab = viewModel::selectTab,
                                    onReorderTabs = { orderedIds -> viewModel.reorderTabs(orderedIds) },
                                    onAddTab = { createTabOpen = true },
                                )
                                // Row 2: Action bar icons (or search field)
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .wrapContentHeight()
                                        .padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    if (searchExpanded) {
                                        OutlinedTextField(
                                            value = searchQuery,
                                            onValueChange = viewModel::setSearchQuery,
                                            placeholder = { Text("Search\u2026") },
                                            singleLine = true,
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(52.dp)
                                                .focusRequester(searchFocusRequester)
                                                .onFocusChanged { focus ->
                                                    if (focus.isFocused) {
                                                        searchHasBeenFocused = true
                                                    } else if (searchHasBeenFocused && searchQuery.isBlank()) {
                                                        searchExpanded = false
                                                    }
                                                },
                                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                            trailingIcon = {
                                                IconButton(onClick = {
                                                    viewModel.setSearchQuery("")
                                                    searchExpanded = false
                                                }) {
                                                    Icon(Icons.Default.Close, contentDescription = "Close search")
                                                }
                                            },
                                        )
                                        LaunchedEffect(Unit) { searchFocusRequester.requestFocus() }
                                    } else {
                                        val anyExpanded = sectionsWithItems.any { it.section.expanded }
                                        IconButton(onClick = {
                                            FeedbackManager.play(view, FeedbackEvent.SWITCH)
                                            viewModel.toggleAllSectionsExpanded()
                                        }) {
                                            Icon(
                                                if (anyExpanded) Icons.Default.UnfoldLess else Icons.Default.UnfoldMore,
                                                contentDescription = if (anyExpanded) "Collapse all" else "Expand all",
                                            )
                                        }
                                        IconButton(onClick = {
                                            FeedbackManager.play(view, FeedbackEvent.SWITCH)
                                            viewModel.toggleHideCompleted()
                                        }) {
                                            Icon(
                                                if (hideCompleted) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                contentDescription = if (hideCompleted) "Show completed" else "Hide completed",
                                            )
                                        }
                                        IconButton(onClick = {
                                            searchHasBeenFocused = false
                                            searchExpanded = true
                                        }) {
                                            Icon(Icons.Default.Search, contentDescription = "Search")
                                        }
                                        IconButton(onClick = {
                                            FeedbackManager.play(view, FeedbackEvent.CONFIRM)
                                            newSectionDraft = ""
                                        }) {
                                            Icon(Icons.Default.Add, contentDescription = "Add section")
                                        }
                                        Box {
                                            var kebabExpanded by remember { mutableStateOf(false) }
                                            IconButton(onClick = {
                                                FeedbackManager.play(view, FeedbackEvent.CONFIRM)
                                                kebabExpanded = true
                                            }) {
                                                Icon(Icons.Default.MoreVert, contentDescription = "Tab options")
                                            }
                                            DropdownMenu(
                                                expanded = kebabExpanded,
                                                onDismissRequest = { kebabExpanded = false },
                                            ) {
                                                DropdownMenuItem(
                                                    text = { Text("Import checklist") },
                                                    onClick = {
                                                        kebabExpanded = false
                                                        pasteStartWithAi = false
                                                        viewModel.openPasteSheet()
                                                    },
                                                    leadingIcon = { Icon(Icons.Default.ContentPaste, contentDescription = null) },
                                                )
                                                DropdownMenuItem(
                                                    text = { Text("Rename tab") },
                                                    onClick = {
                                                        kebabExpanded = false
                                                        showRenameTabDialog = true
                                                    },
                                                    enabled = activeTab != null,
                                                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                                )
                                                DropdownMenuItem(
                                                    text = { Text("Change tab color") },
                                                    onClick = {
                                                        kebabExpanded = false
                                                        showRecolorTabDialog = true
                                                    },
                                                    enabled = activeTab != null,
                                                    leadingIcon = { Icon(Icons.Default.Palette, contentDescription = null) },
                                                )
                                                DropdownMenuItem(
                                                    text = {
                                                        val allComplete = sectionsWithItems.let { sws ->
                                                            sws.isNotEmpty() && sws.all { swi -> swi.items.all { it.completed } }
                                                        }
                                                        Text(if (allComplete) "Mark all incomplete" else "Mark all complete")
                                                    },
                                                    onClick = {
                                                        kebabExpanded = false
                                                        val shown = computeShownSections(sectionsWithItems, hideCompleted, searchQuery)
                                                        val ids = shown.flatMap { it.items.map { i -> i.id } }
                                                        if (ids.isEmpty()) {
                                                            android.widget.Toast.makeText(context, "No items to mark", android.widget.Toast.LENGTH_SHORT).show()
                                                        } else {
                                                            val allComplete = shown.all { swi -> swi.items.all { it.completed } }
                                                            viewModel.setItemsCompleted(ids, !allComplete)
                                                            val action = if (!allComplete) "complete" else "incomplete"
                                                            android.widget.Toast.makeText(context, "Marked ${ids.size} item${if (ids.size > 1) "s" else ""} $action", android.widget.Toast.LENGTH_SHORT).show()
                                                        }
                                                    },
                                                    enabled = activeTab != null,
                                                    leadingIcon = { Icon(Icons.Default.CheckCircle, contentDescription = null) },
                                                )
                                                DropdownMenuItem(
                                                    text = { Text("Delete shown content") },
                                                    onClick = {
                                                        kebabExpanded = false
                                                        val shown = computeShownSections(sectionsWithItems, hideCompleted, searchQuery)
                                                        val itemIds = shown.flatMap { it.items.map { i -> i.id } }
                                                        val sectionIds = shown.map { it.section.id }
                                                        if (shown.isEmpty()) {
                                                            android.widget.Toast.makeText(context, "Nothing to delete", android.widget.Toast.LENGTH_SHORT).show()
                                                        } else {
                                                            viewModel.deleteShownContent(itemIds, sectionIds)
                                                            val parts = mutableListOf<String>()
                                                            if (sectionIds.isNotEmpty()) parts.add("${sectionIds.size} section${if (sectionIds.size > 1) "s" else ""}")
                                                            if (itemIds.isNotEmpty()) parts.add("${itemIds.size} item${if (itemIds.size > 1) "s" else ""}")
                                                            android.widget.Toast.makeText(context, "Deleted ${parts.joinToString(", ")}", android.widget.Toast.LENGTH_SHORT).show()
                                                        }
                                                    },
                                                    enabled = activeTab != null,
                                                    leadingIcon = { Icon(Icons.Default.DeleteSweep, contentDescription = null) },
                                                )
                                                DropdownMenuItem(
                                                    text = { Text("Delete tab") },
                                                    onClick = {
                                                        kebabExpanded = false
                                                        if (tabs.size <= 1) {
                                                            android.widget.Toast.makeText(context, "Cannot delete the last tab", android.widget.Toast.LENGTH_SHORT).show()
                                                        } else {
                                                            activeTab?.let { confirmDeleteTab = it }
                                                        }
                                                    },
                                                    enabled = activeTab != null,
                                                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // --- Divider between header and content ---
                    item(key = "divider") { Divider() }

                    if (showEmptyState) {
                        item(key = "empty_state") {
                            ChecklistEmptyStateCard(
                                onAddSection = {
                                    // Route through the same inline-section
                                    // input the action bar's `+` uses so the
                                    // user names their first section instead
                                    // of landing in an auto-created "General".
                                    newSectionDraft = ""
                                },
                                onPaste = {
                                    pasteStartWithAi = false
                                    viewModel.openPasteSheet()
                                },
                                onBrowse = { viewModel.requestBrowserSearch(vaultName) },
                                onAiPrompt = {
                                    // Open the paste sheet with Step 1
                                    // (Generate with AI) already expanded
                                    // instead of copying silently — gives the
                                    // user the template picker + copy buttons
                                    // in one place.
                                    pasteStartWithAi = true
                                    viewModel.openPasteSheet()
                                },
                                primaryButtonFocusRequester = contentFocusRequester,
                            )
                        }
                    } else {
                    itemsIndexed(renderedSections, key = { _, s -> s.section.id }) { sectionIndex, swi ->
                        val isSectionDragged = sectionDragList != null && sectionIndex == sectionDragIndex
                        val flashSectionColor by animateColorAsState(
                            targetValue = if (swi.section.id == flashSectionId) {
                                tabColor.copy(alpha = 0.35f)
                            } else Color.Transparent,
                            animationSpec = tween(durationMillis = 400),
                            label = "sectionFlash",
                        )
                        val sectionModifier = Modifier
                            .background(flashSectionColor)
                            .onSizeChanged { size ->
                                if (size.height > 0 && !isSectionDragged) {
                                    sectionHeightPx = size.height.toFloat()
                                }
                            }
                            // graphicsLayer ALWAYS sets translationY (to 0
                            // when not dragging) — Modifier.offset was
                            // sometimes leaving a stale y-offset on a row
                            // after the drag ended, visually "sticking" it
                            // half-way into its neighbor. shadowElevation +
                            // shape gives the dragged card visual lift.
                            .graphicsLayer {
                                translationY = if (isSectionDragged) sectionDragOffset else 0f
                                shadowElevation = if (isSectionDragged) 12.dp.toPx() else 0f
                                shape = RoundedCornerShape(12.dp)
                            }
                            .zIndex(if (isSectionDragged) 1f else 0f)

                        ChecklistSectionCard(
                            section = swi.section,
                            completedCount = swi.completedCount,
                            totalCount = swi.totalCount,
                            tabColor = tabColor,
                            onToggleExpanded = { viewModel.toggleSectionExpanded(swi.section.id) },
                            onRename = { newName -> viewModel.renameSection(swi.section.id, newName) },
                            onSearch = { onNavigateToBrowser("$vaultName ${swi.section.name}") },
                            onMoveToTop = {
                                // Reorder this section to the front of the
                                // current tab while preserving the relative
                                // order of everything else.
                                val current = renderedSections.map { it.section.id }
                                if (current.firstOrNull() != swi.section.id) {
                                    val reordered = listOf(swi.section.id) +
                                        current.filter { it != swi.section.id }
                                    viewModel.reorderSections(reordered)
                                }
                            },
                            onPromoteItems = { viewModel.promoteItemsToSections(swi.section.id) },
                            onDelete = { confirmDeleteSection = swi.section },
                            onCheckAll = { allComplete ->
                                viewModel.setAllItemsCompletedInSection(swi.section.id, allComplete)
                            },
                            handleDragModifier = Modifier.pointerInput(swi.section.id) {
                                // detectDragGesturesAfterLongPress: mirrors the tab-chip
                                // pattern. The long-press claims the pointer stream before
                                // the outer LazyColumn scroll can steal it, so onDragEnd
                                // always fires and rows never get left half-translated.
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        val live = currentFilteredSections
                                        sectionDragList = live.toList()
                                        sectionDragIndex = live.indexOfFirst {
                                            it.section.id == swi.section.id
                                        }
                                        sectionDragOffset = 0f
                                    },
                                    onDrag = { change, delta ->
                                        change.consume()
                                        sectionDragOffset += delta.y
                                        val h = sectionHeightPx.coerceAtLeast(1f)
                                        val threshold = h * 0.5f
                                        while (
                                            sectionDragOffset > threshold &&
                                            sectionDragIndex < (sectionDragList?.size ?: 0) - 1
                                        ) {
                                            val cur = sectionDragList!!.toMutableList()
                                            val moved = cur.removeAt(sectionDragIndex)
                                            cur.add(sectionDragIndex + 1, moved)
                                            sectionDragList = cur
                                            sectionDragIndex += 1
                                            sectionDragOffset -= h
                                        }
                                        while (sectionDragOffset < -threshold && sectionDragIndex > 0) {
                                            val cur = sectionDragList!!.toMutableList()
                                            val moved = cur.removeAt(sectionDragIndex)
                                            cur.add(sectionDragIndex - 1, moved)
                                            sectionDragList = cur
                                            sectionDragIndex -= 1
                                            sectionDragOffset += h
                                        }
                                    },
                                    onDragEnd = {
                                        val finalList = sectionDragList
                                        val finalIdx = sectionDragIndex
                                        sectionDragList = null
                                        sectionDragIndex = -1
                                        sectionDragOffset = 0f
                                        if (finalList != null && finalIdx in finalList.indices) {
                                            val droppedId = finalList[finalIdx].section.id
                                            val originalOrder = currentFilteredSections.map { it.section.id }
                                            val newOrder = finalList.map { it.section.id }
                                            if (newOrder != originalOrder) {
                                                viewModel.reorderSections(newOrder)
                                            }
                                            flashSectionId = droppedId
                                        }
                                    },
                                    onDragCancel = {
                                        sectionDragList = null
                                        sectionDragIndex = -1
                                        sectionDragOffset = 0f
                                    },
                                )
                            },
                            modifier = sectionModifier,
                            accentColor = accentColor,
                            sectionFocusRequester = if (sectionIndex == 0) contentFocusRequester else null,
                        ) {
                            // Items drag-reorder — same snap pattern, scoped to this section.
                            // rememberUpdatedState gives the gesture coroutine live
                            // references to swi.items / swi.section.id / the VM
                            // callback. Without these, pointerInput (keyed on
                            // item.id) caches the closures from its first
                            // composition — so the SECOND drag would read a stale
                            // items list, "freezing" the dragged row because the
                            // snap math thinks the list is the original order.
                            val currentSectionItems by rememberUpdatedState(swi.items)
                            val currentSectionId by rememberUpdatedState(swi.section.id)
                            val currentReorderItems by rememberUpdatedState<(String, List<String>) -> Unit> { sectionId, ids ->
                                viewModel.reorderItems(sectionId, ids)
                            }

                            var itemDragList by remember(swi.section.id) {
                                mutableStateOf<List<ChecklistItem>?>(null)
                            }
                            var itemDragIndex by remember(swi.section.id) { mutableStateOf(-1) }
                            var itemDragOffset by remember(swi.section.id) { mutableStateOf(0f) }
                            var itemHeightPx by remember(swi.section.id) { mutableStateOf(0f) }
                            var flashItemId by remember(swi.section.id) { mutableStateOf<String?>(null) }

                            LaunchedEffect(flashItemId) {
                                if (flashItemId != null) {
                                    delay(700)
                                    flashItemId = null
                                }
                            }

                            // Defensive: if the committed list matches what we were
                            // dragging, the drag lifecycle ended without onDragEnd
                            // being called (e.g., scroll stole the gesture after a
                            // reorder). Clear the drag state so the row doesn't stay
                            // stuck under translationY.
                            LaunchedEffect(swi.items) {
                                val staged = itemDragList
                                if (staged != null && staged.map { it.id } == swi.items.map { it.id }) {
                                    itemDragList = null
                                    itemDragIndex = -1
                                    itemDragOffset = 0f
                                }
                            }

                            val renderedItems = itemDragList ?: swi.items

                            renderedItems.forEachIndexed { itemIndex, item ->
                                // key(item.id) gives each item stable identity across
                                // snap-reorders. Without it, a plain Column uses
                                // positional identity: when a snap moves an item from
                                // slot N to slot N+1, Compose disposes the pointerInput
                                // at slot N and re-attaches it at slot N+1, cancelling
                                // the in-flight drag gesture coroutine and leaving the
                                // drag non-functional. (Sections don't hit this because
                                // LazyColumn's `key` lambda already provides stable IDs.)
                                key(item.id) {
                                val isDragged = itemDragList != null && itemIndex == itemDragIndex
                                val flashItemColor by animateColorAsState(
                                    targetValue = if (item.id == flashItemId) {
                                        tabColor.copy(alpha = 0.35f)
                                    } else Color.Transparent,
                                    animationSpec = tween(durationMillis = 400),
                                    label = "itemFlash",
                                )
                                val itemRowModifier = Modifier
                                    .background(flashItemColor)
                                    .onSizeChanged { size ->
                                        if (size.height > 0 && !isDragged) {
                                            itemHeightPx = size.height.toFloat()
                                        }
                                    }
                                    // See matching section note — graphicsLayer
                                    // always sets translationY, so nothing stays
                                    // stuck when drag ends.
                                    .graphicsLayer {
                                        translationY = if (isDragged) itemDragOffset else 0f
                                    }
                                    .zIndex(if (isDragged) 1f else 0f)

                                ChecklistItemRow(
                                    title = item.title,
                                    completed = item.completed,
                                    tabColor = tabColor,
                                    isDragged = isDragged,
                                    handleDragModifier = Modifier.pointerInput(item.id) {
                                        // See matching section comment — long-press
                                        // claim prevents the outer scroll container
                                        // from stealing the gesture mid-drag.
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = {
                                                val live = currentSectionItems
                                                itemDragList = live.toList()
                                                itemDragIndex = live.indexOfFirst {
                                                    it.id == item.id
                                                }
                                                itemDragOffset = 0f
                                            },
                                            onDrag = { change, delta ->
                                                change.consume()
                                                itemDragOffset += delta.y
                                                val h = itemHeightPx.coerceAtLeast(1f)
                                                val threshold = h * 0.5f
                                                while (
                                                    itemDragOffset > threshold &&
                                                    itemDragIndex < (itemDragList?.size ?: 0) - 1
                                                ) {
                                                    val cur = itemDragList!!.toMutableList()
                                                    val moved = cur.removeAt(itemDragIndex)
                                                    cur.add(itemDragIndex + 1, moved)
                                                    itemDragList = cur
                                                    itemDragIndex += 1
                                                    itemDragOffset -= h
                                                }
                                                while (itemDragOffset < -threshold && itemDragIndex > 0) {
                                                    val cur = itemDragList!!.toMutableList()
                                                    val moved = cur.removeAt(itemDragIndex)
                                                    cur.add(itemDragIndex - 1, moved)
                                                    itemDragList = cur
                                                    itemDragIndex -= 1
                                                    itemDragOffset += h
                                                }
                                            },
                                            onDragEnd = {
                                                val finalList = itemDragList
                                                val finalIdx = itemDragIndex
                                                itemDragList = null
                                                itemDragIndex = -1
                                                itemDragOffset = 0f
                                                if (finalList != null && finalIdx in finalList.indices) {
                                                    val droppedId = finalList[finalIdx].id
                                                    // Compare against the LIVE list,
                                                    // not whatever swi.items was
                                                    // captured at first composition.
                                                    val originalOrder = currentSectionItems.map { it.id }
                                                    val newOrder = finalList.map { it.id }
                                                    if (newOrder != originalOrder) {
                                                        currentReorderItems(
                                                            currentSectionId,
                                                            newOrder,
                                                        )
                                                    }
                                                    flashItemId = droppedId
                                                }
                                            },
                                            onDragCancel = {
                                                itemDragList = null
                                                itemDragIndex = -1
                                                itemDragOffset = 0f
                                            },
                                        )
                                    },
                                    onToggle = { viewModel.toggleItem(item.id) },
                                    onRename = { newTitle -> viewModel.renameItem(item.id, newTitle) },
                                    onDelete = { confirmDeleteItem = item },
                                    modifier = itemRowModifier,
                                    accentColor = accentColor,
                                )
                                } // key(item.id)
                            }
                            // Inline add-item row. Pressing Enter (Done on
                            // the IME) commits the same as tapping the + —
                            // the button tap itself was occasionally being
                            // swallowed by the keyboard on some devices.
                            var addText by remember(swi.section.id) { mutableStateOf("") }
                            val commitAdd = {
                                if (addText.isNotBlank()) {
                                    viewModel.addItem(swi.section.id, addText.trim())
                                    addText = ""
                                    // Release focus so the keyboard closes
                                    // after add — users typically only add a
                                    // couple of items at a time and prefer to
                                    // see the new item land in the list,
                                    // rather than the field staying ready.
                                    focusManager.clearFocus()
                                }
                            }
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                OutlinedTextField(
                                    value = addText,
                                    onValueChange = { addText = it.take(400) },
                                    placeholder = { Text("+ Add item") },
                                    singleLine = true,
                                    supportingText = if (addText.length > 350) {
                                        { Text("${addText.length}/400") }
                                    } else null,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                    keyboardActions = KeyboardActions(onDone = { commitAdd() }),
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(onClick = {
                                    FeedbackManager.play(view, FeedbackEvent.CONFIRM)
                                    commitAdd()
                                }) {
                                    Icon(Icons.Default.Add, contentDescription = "Add item")
                                }
                            }
                        }
                    }

                    item(key = "add_section_footer") {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            TextButton(onClick = { newSectionDraft = "" }) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Add section")
                            }
                        }
                    }
                    }
                } // end LazyColumn

                // Scroll-to-top FAB — visible when scrolled past the header
                val showFab by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }
                AnimatedVisibility(
                    visible = showFab,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 80.dp, end = 16.dp),
                ) {
                    SmallFloatingActionButton(
                        onClick = { scrollScope.launch { listState.animateScrollToItem(0) } },
                        containerColor = accentColor,
                        contentColor = menuBg,
                    ) {
                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Scroll to top")
                    }
                }
            } // end Box

        // Overlays
        if (pasteOpen) {
            val promptTemplates by viewModel.promptTemplates.collectAsState()
            val selectedTemplate by viewModel.selectedTemplate.collectAsState()
            val isSelectedDefault by viewModel.selectedIsDefaultTemplate.collectAsState()
            val availableGuides by viewModel.availableGuides.collectAsState()
            val selectedGuideIds by viewModel.selectedGuideIds.collectAsState()
            ChecklistPasteSheet(
                vaultName = vaultName,
                templates = promptTemplates,
                selectedTemplate = selectedTemplate,
                isSelectedDefault = isSelectedDefault,
                availableGuides = availableGuides,
                selectedGuideIds = selectedGuideIds,
                onSelectedGuidesChange = { ids -> viewModel.setSelectedGuides(ids) },
                onSelectTemplate = { id -> viewModel.selectTemplate(id) },
                onSaveSelected = { name, content -> viewModel.saveSelectedTemplate(name, content) },
                onResetSelectedToDefault = { viewModel.resetSelectedTemplateToDefault() },
                onCreateTemplate = { name -> viewModel.createUserTemplate(name) },
                onDeleteSelectedTemplate = { viewModel.deleteSelectedUserTemplate() },
                onCopyPrompt = { substituted -> viewModel.copyAiPromptText(substituted) },
                onCopyPromptWithGuide = { viewModel.copyPromptWithGuide(vaultName) },
                onPasteFromClipboard = { text -> viewModel.importPasted(text) },
                onCancel = {
                    pasteStartWithAi = false
                    viewModel.closePasteSheet()
                },
                initialAiStepExpanded = pasteStartWithAi,
            )
        }

        aiCopiedDialog?.let { (title, body) ->
            AiPromptCopiedDialog(
                title = title,
                body = body,
                onDismiss = { aiCopiedDialog = null },
            )
        }

        importDoneDialog?.let { (title, body) ->
            AiPromptCopiedDialog(
                title = title,
                body = body,
                onDismiss = { importDoneDialog = null },
            )
        }

        if (tapGameOpen) {
            ChecklistTapGame(
                vaultArtworkPath = vaultArtworkUri?.toString(),
                vaultId = vaultId,
                highScore = tapGameHighScore,
                onUpdateHighScore = { viewModel.setTapGameHighScore(it) },
                onDismiss = { tapGameOpen = false },
            )
        }

        // Add-section dialog — opened by the action-bar `+` and the
        // empty-state shortcut. A focused single-task dialog is clearer than
        // an inline row appended to the bottom of the list.
        newSectionDraft?.let { draftInitial ->
            var draft by remember(draftInitial) { mutableStateOf(draftInitial) }
            val nameFocus = remember { FocusRequester() }
            LaunchedEffect(Unit) { nameFocus.requestFocus() }
            val commit = {
                val trimmed = draft.trim()
                if (trimmed.isNotEmpty()) {
                    viewModel.createSection(trimmed)
                    // Schedule a scroll-to-bottom — the LaunchedEffect down
                    // in the screen body fires once the new section actually
                    // shows up in `sectionsWithItems`.
                    pendingScrollToNewSection = true
                }
                newSectionDraft = null
            }
            AlertDialog(
                onDismissRequest = { newSectionDraft = null },
                title = { Text("New section") },
                text = {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it.take(50) },
                        label = { Text("Name") },
                        singleLine = true,
                        supportingText = if (draft.length > 40) {
                            { Text("${draft.length}/50") }
                        } else null,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { commit() }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(nameFocus),
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = { commit() },
                        enabled = draft.isNotBlank(),
                    ) { Text("Create") }
                },
                dismissButton = {
                    TextButton(onClick = { newSectionDraft = null }) { Text("Cancel") }
                },
            )
        }

        if (createTabOpen) {
            CreateTabSheet(
                usedColors = tabs.map { it.color }.toSet(),
                preferredColorIndex = preferredTabColorIndex,
                onCreate = { name, color ->
                    viewModel.createTab(name, color)
                    createTabOpen = false
                },
                onCancel = { createTabOpen = false },
            )
        }

        if (showRenameTabDialog) {
            activeTab?.let { tab ->
                var draft by remember { mutableStateOf(tab.name) }
                AlertDialog(
                    onDismissRequest = { showRenameTabDialog = false },
                    title = { Text("Rename tab") },
                    text = {
                        OutlinedTextField(
                            value = draft,
                            onValueChange = { draft = it.take(50) },
                            singleLine = true,
                            supportingText = if (draft.length > 40) {
                                { Text("${draft.length}/50") }
                            } else null,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                if (draft.isNotBlank()) viewModel.renameTab(tab.id, draft.trim())
                                showRenameTabDialog = false
                            },
                            enabled = draft.isNotBlank(),
                        ) { Text("Save") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showRenameTabDialog = false }) { Text("Cancel") }
                    },
                )
            }
        }

        if (showRecolorTabDialog) {
            activeTab?.let { tab ->
                AlertDialog(
                    onDismissRequest = { showRecolorTabDialog = false },
                    title = { Text("Change tab color") },
                    text = {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(vertical = 6.dp),
                        ) {
                            (0 until ChecklistColorPalette.SIZE).forEach { idx ->
                                Box(
                                    Modifier
                                        .padding(end = 6.dp)
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(ChecklistColorPalette.color(idx))
                                        .clickable {
                                            viewModel.recolorTab(tab.id, idx)
                                            showRecolorTabDialog = false
                                        },
                                )
                            }
                        }
                    },
                    confirmButton = {},
                    dismissButton = {
                        TextButton(onClick = { showRecolorTabDialog = false }) { Text("Cancel") }
                    },
                )
            }
        }

        confirmDeleteTab?.let { tab ->
            AlertDialog(
                onDismissRequest = { confirmDeleteTab = null },
                title = { Text("Delete tab?") },
                text = { Text("Delete \"${tab.name}\" and all of its sections and items?") },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.deleteTab(tab.id)
                        confirmDeleteTab = null
                    }) { Text("Delete") }
                },
                dismissButton = {
                    TextButton(onClick = { confirmDeleteTab = null }) { Text("Cancel") }
                },
            )
        }

        confirmDeleteSection?.let { section ->
            val count = sectionsWithItems.firstOrNull { it.section.id == section.id }?.items?.size ?: 0
            AlertDialog(
                onDismissRequest = { confirmDeleteSection = null },
                title = { Text("Delete section?") },
                text = { Text("Delete \"${section.name}\" and $count items?") },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.deleteSection(section.id)
                        confirmDeleteSection = null
                    }) { Text("Delete") }
                },
                dismissButton = {
                    TextButton(onClick = { confirmDeleteSection = null }) { Text("Cancel") }
                },
            )
        }

        confirmDeleteItem?.let { item ->
            AlertDialog(
                onDismissRequest = { confirmDeleteItem = null },
                title = { Text("Delete item?") },
                text = { Text("Delete \"${item.title}\"?") },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.deleteItem(item.id)
                        confirmDeleteItem = null
                    }) { Text("Delete") }
                },
                dismissButton = {
                    TextButton(onClick = { confirmDeleteItem = null }) { Text("Cancel") }
                },
            )
        }
        } // end Scaffold
    } // end outer Box (blurred background)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateTabSheet(
    usedColors: Set<Int>,
    preferredColorIndex: Int,
    onCreate: (String, Int) -> Unit,
    onCancel: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var color by remember {
        mutableStateOf(
            // Prefer the focus-color-closest palette entry when it's still
            // available, otherwise fall back to the first unused slot.
            preferredColorIndex.takeIf { it !in usedColors }
                ?: (0 until ChecklistColorPalette.SIZE).firstOrNull { it !in usedColors }
                ?: 0,
        )
    }
    ModalBottomSheet(onDismissRequest = onCancel) {
        Column(Modifier.padding(16.dp)) {
            Text("New tab", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(50) },
                label = { Text("Name") },
                singleLine = true,
                supportingText = if (name.length > 40) {
                    { Text("${name.length}/50") }
                } else null,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Text("Color", style = MaterialTheme.typography.labelLarge)
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 6.dp),
            ) {
                (0 until ChecklistColorPalette.SIZE).forEach { idx ->
                    val swatchColor = ChecklistColorPalette.color(idx)
                    val selected = idx == color
                    Box(
                        Modifier
                            .padding(end = 6.dp)
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(swatchColor)
                            .border(
                                width = if (selected) 3.dp else 1.dp,
                                color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant,
                                shape = CircleShape,
                            )
                            .clickable { color = idx },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onCancel) { Text("Cancel") }
                Button(
                    onClick = { if (name.isNotBlank()) onCreate(name.trim(), color) },
                    enabled = name.isNotBlank(),
                ) { Text("Create") }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** Compute visible sections/items applying hideCompleted + search filters. */
private fun computeShownSections(
    sectionsWithItems: List<ChecklistViewModel.SectionWithItems>,
    hideCompleted: Boolean,
    searchQuery: String,
): List<ChecklistViewModel.SectionWithItems> =
    sectionsWithItems.mapNotNull { swi ->
        val sectionMatches = searchQuery.isNotBlank() &&
            swi.section.name.contains(searchQuery, ignoreCase = true)
        var items = swi.items
        if (hideCompleted) items = items.filter { !it.completed }
        if (searchQuery.isNotBlank() && !sectionMatches) {
            items = items.filter { it.title.contains(searchQuery, ignoreCase = true) }
        }
        val keep = when {
            sectionMatches -> true
            hideCompleted -> items.isNotEmpty()
            searchQuery.isNotBlank() -> items.isNotEmpty()
            else -> true
        }
        if (keep) swi.copy(items = items) else null
    }

