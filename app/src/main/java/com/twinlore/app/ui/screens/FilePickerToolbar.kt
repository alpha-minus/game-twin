package com.twinlore.app.ui.screens

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.twinlore.app.data.FocusColor
import com.twinlore.app.data.HapticFeedbackIntensity
import com.twinlore.app.data.LedTurnOffTime
import com.twinlore.app.domain.FilePickerSortField
import com.twinlore.app.domain.FilePickerViewMode
import com.twinlore.app.domain.SortDirection
import com.twinlore.app.thor.led.ThorLedManager
import com.twinlore.app.ui.LocalCachedFocusColor
import com.twinlore.app.ui.LocalThemePalette
import com.twinlore.app.ui.utils.FeedbackEvent
import com.twinlore.app.ui.utils.FeedbackManager
import com.twinlore.app.ui.utils.performHapticFeedback

@Composable
fun FilePickerToolbar(
    viewMode: FilePickerViewMode,
    onViewModeChange: (FilePickerViewMode) -> Unit,
    sortField: FilePickerSortField,
    sortDirection: SortDirection,
    onSortChange: (FilePickerSortField, SortDirection) -> Unit,
    onSyncClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusColor: Color = Color.Unspecified,
    textColor: Color = Color.Unspecified,
    showLinks: Boolean = true,
    onToggleShowLinks: (() -> Unit)? = null,
    extraContent: @Composable (() -> Unit)? = null,
) {
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val focusSoundEnabled by settingsViewModel.focusSoundEnabled.collectAsState(initial = true)
    val hapticFeedbackIntensity by settingsViewModel.hapticFeedbackIntensity.collectAsState(initial = HapticFeedbackIntensity.MEDIUM)
    val ledEnabled by settingsViewModel.ledEnabled.collectAsState(initial = true)
    val ledBrightness by settingsViewModel.ledBrightness.collectAsState(initial = 127)
    val ledSaturationBoost by settingsViewModel.ledSaturationBoost.collectAsState(initial = 0.75f)
    val ledTurnOffTime by settingsViewModel.ledTurnOffTime.collectAsState(initial = LedTurnOffTime.SEC_15)

    val resolvedFocusColor = if (focusColor == Color.Unspecified) {
        LocalCachedFocusColor.current?.value
            ?: settingsViewModel.focusColor.collectAsState(initial = FocusColor.CRIMSON).value.color
    } else focusColor
    val resolvedTextColor = if (textColor == Color.Unspecified) {
        LocalThemePalette.current.foreground
    } else textColor

    var viewModeMenuExpanded by remember { mutableStateOf(false) }
    var sortMenuExpanded by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // View mode toggle button (cycles LIST <-> GRID)
            ToolbarIconButton(
                icon = viewMode.icon,
                contentDescription = "View mode: ${viewMode.name}",
                focusColor = resolvedFocusColor,
                textColor = resolvedTextColor,
                focusSoundEnabled = focusSoundEnabled,
                hapticFeedbackIntensity = hapticFeedbackIntensity,
                ledEnabled = ledEnabled,
                ledBrightness = ledBrightness,
                ledSaturationBoost = ledSaturationBoost,
                ledTurnOffMs = ledTurnOffTime.inactivityMs,
                onClick = {
                    val next = if (viewMode == FilePickerViewMode.LIST) FilePickerViewMode.GRID else FilePickerViewMode.LIST
                    onViewModeChange(next)
                }
            )

            Spacer(Modifier.width(4.dp))

            // Sort field dropdown
            Box {
                ToolbarChipButton(
                    label = sortField.label,
                    focusColor = resolvedFocusColor,
                    textColor = resolvedTextColor,
                    focusSoundEnabled = focusSoundEnabled,
                    hapticFeedbackIntensity = hapticFeedbackIntensity,
                    ledEnabled = ledEnabled,
                    ledBrightness = ledBrightness,
                    ledSaturationBoost = ledSaturationBoost,
                    ledTurnOffMs = ledTurnOffTime.inactivityMs,
                    onClick = { sortMenuExpanded = true }
                )
                DropdownMenu(
                    expanded = sortMenuExpanded,
                    onDismissRequest = { sortMenuExpanded = false }
                ) {
                    FilePickerSortField.entries.forEach { field ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    field.label,
                                    color = if (field == sortField) resolvedFocusColor else Color.Unspecified
                                )
                            },
                            onClick = {
                                sortMenuExpanded = false
                                onSortChange(field, sortDirection)
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.width(4.dp))

            // Sort direction toggle button
            val directionIcon = if (sortDirection == SortDirection.ASC) {
                Icons.Default.ArrowUpward
            } else {
                Icons.Default.ArrowDownward
            }
            ToolbarIconButton(
                icon = directionIcon,
                contentDescription = if (sortDirection == SortDirection.ASC) "Ascending" else "Descending",
                focusColor = resolvedFocusColor,
                textColor = resolvedTextColor,
                focusSoundEnabled = focusSoundEnabled,
                hapticFeedbackIntensity = hapticFeedbackIntensity,
                ledEnabled = ledEnabled,
                ledBrightness = ledBrightness,
                ledSaturationBoost = ledSaturationBoost,
                ledTurnOffMs = ledTurnOffTime.inactivityMs,
                onClick = { onSortChange(sortField, sortDirection.toggle()) }
            )

            // Links on/off filter toggle (only when host provides a handler)
            if (onToggleShowLinks != null) {
                Spacer(Modifier.width(4.dp))
                ToolbarIconButton(
                    icon = if (showLinks) Icons.Default.Link else Icons.Default.LinkOff,
                    contentDescription = if (showLinks) "Hide saved links" else "Show saved links",
                    focusColor = resolvedFocusColor,
                    textColor = if (showLinks) resolvedTextColor else resolvedTextColor.copy(alpha = 0.4f),
                    focusSoundEnabled = focusSoundEnabled,
                    hapticFeedbackIntensity = hapticFeedbackIntensity,
                    ledEnabled = ledEnabled,
                    ledBrightness = ledBrightness,
                    ledSaturationBoost = ledSaturationBoost,
                    ledTurnOffMs = ledTurnOffTime.inactivityMs,
                    onClick = { onToggleShowLinks() }
                )
            }

            Spacer(Modifier.weight(1f))

            // Sync icon
            ToolbarIconButton(
                icon = Icons.Default.Sync,
                contentDescription = "Sync",
                focusColor = resolvedFocusColor,
                textColor = resolvedTextColor,
                focusSoundEnabled = focusSoundEnabled,
                hapticFeedbackIntensity = hapticFeedbackIntensity,
                ledEnabled = ledEnabled,
                ledBrightness = ledBrightness,
                ledSaturationBoost = ledSaturationBoost,
                ledTurnOffMs = ledTurnOffTime.inactivityMs,
                event = FeedbackEvent.REFRESH,
                onClick = { onSyncClick() }
            )
        }

        // Extra content slot (e.g. type filter chips)
        extraContent?.invoke()
    }
}

/**
 * 36dp icon button with CircleShape focus border/background, matching the
 * LibraryTopMenuBar icon button pattern.
 */
@Composable
private fun ToolbarIconButton(
    icon: ImageVector,
    contentDescription: String,
    focusColor: Color,
    textColor: Color,
    focusSoundEnabled: Boolean,
    hapticFeedbackIntensity: HapticFeedbackIntensity,
    ledEnabled: Boolean,
    ledBrightness: Int,
    ledSaturationBoost: Float,
    ledTurnOffMs: Long?,
    event: FeedbackEvent = FeedbackEvent.SWITCH,
    onClick: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val context = LocalContext.current
    val view = LocalView.current

    LaunchedEffect(isFocused) {
        if (isFocused) {
            FeedbackManager.playSound(context, FeedbackEvent.FOCUS, focusSoundEnabled)
            view.performHapticFeedback(hapticFeedbackIntensity)
            if (ledEnabled) {
                ThorLedManager.setColor(focusColor, true, ledBrightness, ledSaturationBoost, ledTurnOffMs)
            }
        }
    }

    Box(
        modifier = Modifier
            .size(36.dp)
            .focusRequester(focusRequester)
            .focusable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                focusRequester.requestFocus()
                FeedbackManager.play(view, event)
                onClick()
            }
            .then(
                if (isFocused) Modifier.background(focusColor.copy(alpha = 0.22f), CircleShape)
                else Modifier
            )
            .then(
                if (isFocused) Modifier.border(2.dp, focusColor, CircleShape)
                else Modifier
            )
            .onKeyEvent { keyEvent ->
                val nativeKeyEvent = keyEvent.nativeKeyEvent
                if (nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                    when (nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_CENTER,
                        KeyEvent.KEYCODE_BUTTON_A,
                        KeyEvent.KEYCODE_ENTER,
                        KeyEvent.KEYCODE_SPACE -> {
                            FeedbackManager.play(view, event)
                            if (ledEnabled) {
                                ThorLedManager.setColor(
                                    focusColor, true, ledBrightness, ledSaturationBoost, ledTurnOffMs
                                )
                            }
                            onClick()
                            true
                        }
                        else -> false
                    }
                } else {
                    false
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(22.dp),
            tint = if (isFocused) focusColor else textColor
        )
    }
}

/**
 * Chip-style button with text + trailing icon, RoundedCornerShape(8.dp) focus border,
 * matching the NavigableButton styling pattern.
 */
@Composable
private fun ToolbarChipButton(
    label: String,
    focusColor: Color,
    textColor: Color,
    focusSoundEnabled: Boolean,
    hapticFeedbackIntensity: HapticFeedbackIntensity,
    ledEnabled: Boolean,
    ledBrightness: Int,
    ledSaturationBoost: Float,
    ledTurnOffMs: Long?,
    event: FeedbackEvent = FeedbackEvent.SWITCH,
    onClick: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val context = LocalContext.current
    val view = LocalView.current

    LaunchedEffect(isFocused) {
        if (isFocused) {
            FeedbackManager.playSound(context, FeedbackEvent.FOCUS, focusSoundEnabled)
            view.performHapticFeedback(hapticFeedbackIntensity)
            if (ledEnabled) {
                ThorLedManager.setColor(focusColor, true, ledBrightness, ledSaturationBoost, ledTurnOffMs)
            }
        }
    }

    Surface(
        modifier = Modifier
            .focusRequester(focusRequester)
            .focusable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                focusRequester.requestFocus()
                FeedbackManager.play(view, event)
                onClick()
            }
            .then(
                if (isFocused) Modifier.border(2.dp, focusColor, RoundedCornerShape(8.dp))
                else Modifier
            )
            .onKeyEvent { keyEvent ->
                val nativeKeyEvent = keyEvent.nativeKeyEvent
                if (nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                    when (nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_CENTER,
                        KeyEvent.KEYCODE_BUTTON_A,
                        KeyEvent.KEYCODE_ENTER,
                        KeyEvent.KEYCODE_SPACE -> {
                            FeedbackManager.play(view, event)
                            if (ledEnabled) {
                                ThorLedManager.setColor(
                                    focusColor, true, ledBrightness, ledSaturationBoost, ledTurnOffMs
                                )
                            }
                            onClick()
                            true
                        }
                        else -> false
                    }
                } else {
                    false
                }
            },
        shape = RoundedCornerShape(8.dp),
        color = if (isFocused) focusColor.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            text = label,
            color = if (isFocused) focusColor else textColor,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}
