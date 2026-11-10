package com.twinlore.app.ui.screens

import android.content.res.Configuration
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.twinlore.app.data.FocusColor
import com.twinlore.app.data.HapticFeedbackIntensity
import com.twinlore.app.data.LedTurnOffTime
import com.twinlore.app.ui.LocalCachedFocusColor
import com.twinlore.app.ui.navigation.NavigableTextField
import com.twinlore.app.ui.utils.FeedbackEvent
import com.twinlore.app.ui.utils.runWithHapticAndLed
import kotlinx.coroutines.launch

/**
 * Find on Page UI overlay
 *
 * With [recentSearches] on (the guide readers), the bar records what gets
 * searched in [RecentFindSearches] and, while the field is empty, lists
 * those queries under it; picking one fills the field and searches it.
 * A query counts once it is actually searched: submitted, stepped through
 * with Previous / Next, or handed in pre-filled with [requestFocusOnOpen]
 * false (the selection menu's find, which searches straight away). A
 * [liveSearch] caller searches on every keystroke instead, so for it the
 * query left in the field when the bar closes counts too; recording each
 * keystroke would only fill the list with prefixes.
 *
 * [extraContent] goes under the button row, above the recent searches
 * (the page group's All pages / This page switch).
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun FindOnPageUI(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClose: () -> Unit,
    onSaveAsScrollMarker: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    matchesFound: Boolean? = null, // null = not searched, true = found, false = not found
    matchCount: Int = 0, // Total number of matches
    currentIndex: Int = 0, // Current match index (1-based)
    searchTrigger: Int = 0, // Counter that increments when search is performed
    isSearching: Boolean = false, // Whether search is currently in progress
    requestFocusOnOpen: Boolean = true, // When false (e.g. opened from selection menu), do not show keyboard; run search immediately
    onImeVisibilityChange: (Boolean) -> Unit = {}, // Report IME visibility so activity can let keys go to keyboard when open
    compact: Boolean = false,
    recentSearches: Boolean = false,
    liveSearch: Boolean = false,
    extraContent: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val textFieldFocusRequester = remember { FocusRequester() }
    val saveMarkerButtonFocusRequester = remember { FocusRequester() }
    val upButtonFocusRequester = remember { FocusRequester() }
    val nextButtonFocusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

    // Recent searches. Reads go through rememberUpdatedState so a submit
    // fired in the same frame as the last keystroke still records the
    // query the caller searches, and the dispose below sees the final one.
    val currentQuery by rememberUpdatedState(query)
    val currentOnSearch by rememberUpdatedState(onSearch)
    val recents by RecentFindSearches.queries.collectAsState()
    fun recordQuery() {
        if (recentSearches) RecentFindSearches.record(currentQuery)
    }
    val search: () -> Unit = {
        recordQuery()
        currentOnSearch()
    }
    if (recentSearches && !requestFocusOnOpen) {
        LaunchedEffect(Unit) { recordQuery() }
    }
    if (recentSearches && liveSearch) {
        DisposableEffect(Unit) { onDispose { recordQuery() } }
    }

    // Get focus color and haptic/LED from settings
    // Not a ViewModel: this is composed inside the fold panel's overlay
    // window too, where there is no activity for hiltViewModel() to scope
    // one to. See AppUiSettings.
    val uiSettings = com.twinlore.app.ui.rememberAppUiSettings()
    val focusColorState by uiSettings.focusColor.collectAsState(initial = FocusColor.CRIMSON)
    val cachedFocusColor = LocalCachedFocusColor.current?.value
    val focusColor = cachedFocusColor ?: focusColorState.color
    val view = LocalView.current
    val hapticFeedbackIntensity by uiSettings.hapticFeedbackIntensity.collectAsState(initial = HapticFeedbackIntensity.MEDIUM)
    val ledEnabled by uiSettings.ledEnabled.collectAsState(initial = true)
    val ledBrightness by uiSettings.ledBrightness.collectAsState(initial = 127)
    val ledSaturationBoost by uiSettings.ledSaturationBoost.collectAsState(initial = 0.75f)
    val ledTurnOffTime by uiSettings.ledTurnOffTime.collectAsState(initial = LedTurnOffTime.SEC_15)

    // Find bar layout adapts to orientation like the browser top bar (narrower in portrait).
    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT
    // Compact mode (caller opts in) shaves ~20dp off the bar's total height
    // by halving the outer padding + tightening the text-field min/max.
    // Used by the single-guide viewer; the Browser tab keeps the default.
    val surfacePadding = if (compact) 4.dp else (if (isPortrait) 12.dp else 16.dp)
    val columnPadding = if (compact) 2.dp else (if (isPortrait) 6.dp else 8.dp)
    val rowSpacing = if (isPortrait) 6.dp else 8.dp
    val findBarButtonSize = if (compact) 32.dp else (if (isPortrait) 36.dp else 40.dp)

    // On open: focus text field. When startInEditMode=true (requestFocusOnOpen),
    // NavigableTextField handles its own focus internally — skip external requestFocus
    // to avoid crash (the NAV mode box isn't composed when starting in edit mode).
    if (!requestFocusOnOpen) {
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(80)
            textFieldFocusRequester.requestFocus()
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(surfacePadding),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        shadowElevation = 4.dp,
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(columnPadding)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(rowSpacing),
                verticalAlignment = Alignment.CenterVertically
            ) {
                NavigableTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    placeholder = "Search",
                    imeAction = ImeAction.Search,
                    onSubmit = { search() },
                    externalFocusRequester = textFieldFocusRequester,
                    startInEditMode = requestFocusOnOpen,
                    // A 40dp cap leaves Material's 16dp padding no room for the text.
                    dense = compact,
                    modifier = Modifier
                        .weight(1f)
                        .then(
                            when {
                                compact -> Modifier.heightIn(min = 36.dp, max = 40.dp)
                                isPortrait -> Modifier.heightIn(min = 44.dp, max = 52.dp)
                                else -> Modifier.heightIn(min = 48.dp)
                            }
                        )
                )
                // Match count — inline next to search field
                if (matchesFound == true && matchCount > 0 && query.isNotBlank()) {
                    Text(
                        text = "$currentIndex / $matchCount",
                        style = MaterialTheme.typography.bodySmall,
                        color = focusColor,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                } else if (matchesFound == false && query.isNotBlank()) {
                    Text(
                        text = "0",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
                val saveMarkerInteraction = remember { MutableInteractionSource() }
                val saveMarkerFocused by saveMarkerInteraction.collectIsFocusedAsState()
                IconButton(
                    onClick = {
                        runWithHapticAndLed(view, hapticFeedbackIntensity, ledEnabled, focusColor, ledBrightness, ledSaturationBoost, ledTurnOffTime.inactivityMs, action = onSaveAsScrollMarker)
                    },
                    modifier = Modifier
                        .size(findBarButtonSize)
                        .focusRequester(saveMarkerButtonFocusRequester)
                        .focusable(interactionSource = saveMarkerInteraction)
                        .then(if (saveMarkerFocused) Modifier.border(2.dp, focusColor, RoundedCornerShape(8.dp)) else Modifier)
                        .focusProperties {
                            left = textFieldFocusRequester
                            right = upButtonFocusRequester
                        }
                ) {
                    Icon(
                        imageVector = Icons.Default.BookmarkAdd,
                        contentDescription = "Save as scroll marker (Square)",
                        tint = focusColor,
                        modifier = Modifier.size(if (isPortrait) 18.dp else 22.dp)
                    )
                }
                val prevInteraction = remember { MutableInteractionSource() }
                val prevFocused by prevInteraction.collectIsFocusedAsState()
                IconButton(
                    onClick = {
                        runWithHapticAndLed(view, hapticFeedbackIntensity, ledEnabled, focusColor, ledBrightness, ledSaturationBoost, ledTurnOffTime.inactivityMs, event = FeedbackEvent.SWITCH) {
                            recordQuery()
                            onPrevious()
                        }
                    },
                    modifier = Modifier
                        .size(findBarButtonSize)
                        .focusRequester(upButtonFocusRequester)
                        .focusable(interactionSource = prevInteraction)
                        .then(if (prevFocused) Modifier.border(2.dp, focusColor, RoundedCornerShape(8.dp)) else Modifier)
                        .focusProperties {
                            left = saveMarkerButtonFocusRequester
                            right = nextButtonFocusRequester
                        }
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowUpward,
                        contentDescription = "Previous",
                        tint = if (prevFocused) focusColor else focusColor.copy(alpha = 0.7f),
                        modifier = Modifier.size(if (isPortrait) 18.dp else 22.dp)
                    )
                }
                val nextInteraction = remember { MutableInteractionSource() }
                val nextFocused by nextInteraction.collectIsFocusedAsState()
                IconButton(
                    onClick = {
                        runWithHapticAndLed(view, hapticFeedbackIntensity, ledEnabled, focusColor, ledBrightness, ledSaturationBoost, ledTurnOffTime.inactivityMs, event = FeedbackEvent.SWITCH) {
                            recordQuery()
                            onNext()
                        }
                    },
                    modifier = Modifier
                        .size(findBarButtonSize)
                        .focusRequester(nextButtonFocusRequester)
                        .focusable(interactionSource = nextInteraction)
                        .then(if (nextFocused) Modifier.border(2.dp, focusColor, RoundedCornerShape(8.dp)) else Modifier)
                        .focusProperties {
                            left = upButtonFocusRequester
                            right = textFieldFocusRequester
                        }
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowDownward,
                        contentDescription = "Next",
                        tint = if (nextFocused) focusColor else focusColor.copy(alpha = 0.7f),
                        modifier = Modifier.size(if (isPortrait) 18.dp else 22.dp)
                    )
                }
            }
            if (extraContent != null) {
                extraContent()
            }
            // Only while the field is empty: once there is a query the bar
            // is about its matches, and the list would just push the page
            // down further.
            if (recentSearches && query.isBlank() && recents.isNotEmpty()) {
                RecentFindSearchRow(
                    queries = recents,
                    focusColor = focusColor,
                    onPick = { picked, viaKey ->
                        onQueryChange(picked)
                        RecentFindSearches.record(picked)
                        currentOnSearch()
                        // A tap leaves the field focused with its keyboard
                        // up; the pick is the search, so put it away.
                        keyboardController?.hide()
                        // The row goes away with the filled field, taking the
                        // focused chip with it. Hand a controller user on to
                        // Next so the following press steps through matches.
                        if (viaKey) {
                            scope.launch {
                                kotlinx.coroutines.delay(150)
                                try { nextButtonFocusRequester.requestFocus() } catch (_: Exception) {}
                            }
                        }
                    },
                    modifier = Modifier.padding(top = rowSpacing)
                )
            }
        }
    }
}
