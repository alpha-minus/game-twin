package com.twinlore.app.ui.screens

import android.view.KeyEvent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import android.util.Log
import com.twinlore.app.data.FocusColor
import com.twinlore.app.ui.LocalCachedFocusColor
import com.twinlore.app.domain.SavedTab
import com.twinlore.app.ui.navigation.FocusManager
import com.twinlore.app.ui.navigation.NavigableButton
import com.twinlore.app.ui.navigation.NavigableMenuItem
import com.twinlore.app.ui.navigation.NavigableTextField
import com.twinlore.app.ui.screens.SettingsViewModel
import com.twinlore.app.data.HapticFeedbackIntensity
import com.twinlore.app.data.LedTurnOffTime
import com.twinlore.app.ui.theme.getContrastingTextColor
import com.twinlore.app.ui.theme.getFocusContainer
import com.twinlore.app.ui.utils.FeedbackEvent
import com.twinlore.app.ui.utils.runWithHapticAndLed
import com.twinlore.app.ui.LocalThemePalette
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

/**
 * Bookmark Management Dialog - displays the saved guides (bookmarks) list and ways to add a new one.
 * This is the pre-refactor "bookmark list" menu. PDF / generic "local files" are not supported yet;
 * all entries are saved HTML guides (bookmarks). Online/offline toggle lives only in the top bar when viewing an HTML guide.
 */
@Composable
fun BookmarkManagementDialog(
    bookmarks: List<SavedTab>,
    onBookmarkClick: (SavedTab) -> Unit,  // Load bookmark
    onBookmarkLoadOffline: (SavedTab) -> Unit,  // Load bookmark (offline) - used when opening from list based on global toggle
    onBookmarkRename: (SavedTab, String) -> Unit,
    onBookmarkDelete: (SavedTab) -> Unit,
    onDismiss: () -> Unit,
    vaultId: String,
    onSearchOnline: (() -> Unit)? = null,  // When set (Guide mode), show "Search online" to add a bookmark
    bookmarksLoading: Boolean = false,
    modifier: Modifier = Modifier
) {
    // Get focus color and haptic/LED from settings
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val focusColorState by settingsViewModel.focusColor.collectAsState(initial = FocusColor.CRIMSON)
    val cachedFocusColor = LocalCachedFocusColor.current?.value
    val focusColor = cachedFocusColor ?: focusColorState.color
    val view = LocalView.current
    val hapticFeedbackIntensity by settingsViewModel.hapticFeedbackIntensity.collectAsState(initial = HapticFeedbackIntensity.MEDIUM)
    val ledEnabled by settingsViewModel.ledEnabled.collectAsState(initial = true)
    val ledBrightness by settingsViewModel.ledBrightness.collectAsState(initial = 127)
    val ledSaturationBoost by settingsViewModel.ledSaturationBoost.collectAsState(initial = 0.75f)
    val ledTurnOffTime by settingsViewModel.ledTurnOffTime.collectAsState(initial = LedTurnOffTime.SEC_15)

    // Get theme palette for background and text colors
    val palette = LocalThemePalette.current
    
    val cardBackgroundColor = MaterialTheme.colorScheme.surface
    val textColor = MaterialTheme.colorScheme.onSurface
    val secondaryTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    
    var focusedIndex by remember { mutableIntStateOf(0) }
    val focusRequesters = remember(bookmarks.size) {
        List(bookmarks.size) { FocusRequester() }
    }
    val listState = rememberLazyListState()
    val focusManager = remember { FocusManager() }
    val coroutineScope = rememberCoroutineScope()
    
    // Bookmark action dialogs state (Rename and Delete shown on each entry; no context menu)
    var bookmarkToRename by remember { mutableStateOf<SavedTab?>(null) }
    var bookmarkToDelete by remember { mutableStateOf<SavedTab?>(null) }
    // Focus first item on open
    LaunchedEffect(Unit) {
        if (focusRequesters.isNotEmpty()) {
            focusRequesters[0].requestFocus()
        }
    }
    
    // Scroll to keep focused item visible when focus changes (keyboard/D-pad/remote)
    LaunchedEffect(focusedIndex) {
        if (bookmarks.isNotEmpty() && focusedIndex in bookmarks.indices) {
            listState.animateScrollToItem(focusedIndex)
        }
    }
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = modifier
                .fillMaxWidth(0.85f)
                .fillMaxHeight(0.7f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = cardBackgroundColor
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Title: saved guides (bookmark list)
                Text(
                    text = "Guides",
                    style = MaterialTheme.typography.headlineSmall,
                    color = textColor,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Add bookmark: Search online (go to Browser to save a page)
                if (onSearchOnline != null) {
                    Text(
                        text = "Add bookmark",
                        style = MaterialTheme.typography.labelMedium,
                        color = secondaryTextColor,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        NavigableMenuItem(
                            text = "Search online",
                            onClick = onSearchOnline,
                            focusId = "bookmark_dialog_search_online"
                        )
                    }
                }

                // Saved guides list (no global Load Offline toggle here — that's in the top bar when viewing an HTML guide)
                if (bookmarksLoading) {
                    // Loading: show spinner so we don't show empty list before synced data arrives
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = focusColor)
                    }
                } else if (bookmarks.isEmpty()) {
                    // Empty state
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No saved guides.\nUse Search online to open the browser and save a page as a bookmark.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = secondaryTextColor,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                } else {
                    // Saved guides list
                    Text(
                        text = "Saved guides",
                        style = MaterialTheme.typography.labelMedium,
                        color = secondaryTextColor,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        itemsIndexed(bookmarks) { index, bookmark ->
                            val isFocused = focusedIndex == index
                            val focusRequester = focusRequesters.getOrNull(index) ?: remember { FocusRequester() }
                            val interactionSource = remember(index) { MutableInteractionSource() }
                            
                            LaunchedEffect(isFocused) {
                                if (isFocused) {
                                    focusRequester.requestFocus()
                                }
                            }
                            
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focusRequester)
                                    .focusable(interactionSource = interactionSource)
                                    .onKeyEvent { keyEvent ->
                                        val nativeKeyEvent = keyEvent.nativeKeyEvent
                                        if (nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                                            when (nativeKeyEvent.keyCode) {
                                                KeyEvent.KEYCODE_DPAD_DOWN -> {
                                                    if (index < bookmarks.size - 1) {
                                                        focusedIndex = index + 1
                                                        true
                                                    } else {
                                                        coroutineScope.launch {
                                                            focusManager.requestFocus("bookmark_dialog_close")
                                                        }
                                                        true
                                                    }
                                                }
                                                KeyEvent.KEYCODE_DPAD_UP -> {
                                                    if (index > 0) {
                                                        focusedIndex = index - 1
                                                        true
                                                    } else {
                                                        true
                                                    }
                                                }
                                                KeyEvent.KEYCODE_DPAD_CENTER,
                                                KeyEvent.KEYCODE_BUTTON_A,
                                                KeyEvent.KEYCODE_ENTER -> {
                                                    onBookmarkClick(bookmark)
                                                    true
                                                }
                                                KeyEvent.KEYCODE_DPAD_LEFT,
                                                KeyEvent.KEYCODE_DPAD_RIGHT -> false
                                                else -> false
                                            }
                                        } else {
                                            false
                                        }
                                    },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isFocused) {
                                    focusColor.getFocusContainer()
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .pointerInput(bookmark.id) {
                                                detectTapGestures(
                                                    onTap = { onBookmarkClick(bookmark) }
                                                )
                                            },
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text(
                                            text = bookmark.displayTitle,
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            color = textColor
                                        )
                                        Text(
                                            text = bookmark.url.take(60) + if (bookmark.url.length > 60) "..." else "",
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            color = secondaryTextColor
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            runWithHapticAndLed(view, hapticFeedbackIntensity, ledEnabled, focusColor, ledBrightness, ledSaturationBoost, ledTurnOffTime.inactivityMs) {
                                                bookmarkToRename = bookmark
                                            }
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Edit,
                                            contentDescription = "Rename",
                                            modifier = Modifier.size(20.dp),
                                            tint = focusColor
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            runWithHapticAndLed(view, hapticFeedbackIntensity, ledEnabled, focusColor, ledBrightness, ledSaturationBoost, ledTurnOffTime.inactivityMs) {
                                                bookmarkToDelete = bookmark
                                            }
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Delete",
                                            modifier = Modifier.size(20.dp),
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                
                // Dismiss button (DPAD_UP from here moves focus to last bookmark)
                Spacer(modifier = Modifier.height(8.dp))
                NavigableMenuItem(
                    text = "Close",
                    onClick = onDismiss,
                    focusId = "bookmark_dialog_close",
                    focusManager = focusManager,
                    onKeyEvent = { keyEvent ->
                        if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                            keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_UP &&
                            bookmarks.isNotEmpty()
                        ) {
                            focusedIndex = bookmarks.size - 1
                            coroutineScope.launch {
                                focusRequesters.getOrNull(bookmarks.size - 1)?.requestFocus()
                            }
                            true
                        } else {
                            false
                        }
                    }
                )
            }
        }
    }
    
    // Rename dialog
    bookmarkToRename?.let { bookmark ->
        BookmarkRenameDialog(
            currentName = bookmark.title.ifBlank { bookmark.displayTitle },
            onDismiss = { bookmarkToRename = null },
            onConfirm = { newName ->
                onBookmarkRename(bookmark, newName)
                bookmarkToRename = null
            }
        )
    }
    
    // Delete confirmation dialog
    bookmarkToDelete?.let { bookmark ->
        DeleteTabDialog(
            tab = bookmark,
            onDismiss = { bookmarkToDelete = null },
            onConfirm = {
                onBookmarkDelete(bookmark)
                bookmarkToDelete = null
            }
        )
    }
}

/**
 * Dialog for renaming a bookmark. Internal so Guide Picker can reuse it.
 */
@Composable
internal fun BookmarkRenameDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // Get focus color and haptic/LED from settings
    val view = LocalView.current
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val focusColorState by settingsViewModel.focusColor.collectAsState(initial = FocusColor.CRIMSON)
    val cachedFocusColor = LocalCachedFocusColor.current?.value
    val focusColor = cachedFocusColor ?: focusColorState.color
    val hapticFeedbackIntensity by settingsViewModel.hapticFeedbackIntensity.collectAsState(initial = HapticFeedbackIntensity.MEDIUM)
    val ledEnabled by settingsViewModel.ledEnabled.collectAsState(initial = true)
    val ledBrightness by settingsViewModel.ledBrightness.collectAsState(initial = 127)
    val ledSaturationBoost by settingsViewModel.ledSaturationBoost.collectAsState(initial = 0.75f)
    val ledTurnOffTime by settingsViewModel.ledTurnOffTime.collectAsState(initial = LedTurnOffTime.SEC_15)

    // Get theme palette for background color
    val paletteRename = LocalThemePalette.current

    val containerColor = MaterialTheme.colorScheme.surface

    var newName by remember { mutableStateOf(currentName) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val textFieldFocusRequester = remember { FocusRequester() }

    AlertDialog(
        containerColor = containerColor,
        onDismissRequest = onDismiss,
        title = { Text("Rename Bookmark") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                NavigableTextField(
                    value = newName,
                    onValueChange = {
                        newName = it
                        errorMessage = null
                    },
                    label = "Name",
                    isError = errorMessage != null,
                    modifier = Modifier.fillMaxWidth(),
                    externalFocusRequester = textFieldFocusRequester
                )
                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                LaunchedEffect(Unit) {
                    textFieldFocusRequester.requestFocus()
                }
            }
        },
        confirmButton = {
            NavigableButton(
                text = "Rename",
                onClick = {
                    runWithHapticAndLed(view, hapticFeedbackIntensity, ledEnabled, focusColor, ledBrightness, ledSaturationBoost, ledTurnOffTime.inactivityMs) {
                        val trimmedName = newName.trim()
                        when {
                            trimmedName.isBlank() -> {
                                errorMessage = "Name cannot be empty"
                            }
                            trimmedName.length > 100 -> {
                                errorMessage = "Name is too long (max 100 characters)"
                            }
                            else -> {
                                onConfirm(trimmedName)
                                onDismiss()
                            }
                        }
                    }
                },
                containerColor = Color.Transparent,
                contentColor = focusColor,
                compact = true
            )
        },
        dismissButton = {
            NavigableButton(
                text = "Cancel",
                onClick = { runWithHapticAndLed(view, hapticFeedbackIntensity, ledEnabled, focusColor, ledBrightness, ledSaturationBoost, ledTurnOffTime.inactivityMs, event = FeedbackEvent.BACK, action = onDismiss) },
                containerColor = Color.Transparent,
                contentColor = focusColor,
                compact = true
            )
        },
        modifier = modifier
    )
}
