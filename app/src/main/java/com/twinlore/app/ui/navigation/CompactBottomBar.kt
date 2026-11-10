package com.twinlore.app.ui.navigation

import android.view.KeyEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tab
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import com.twinlore.app.ui.LocalCachedFocusColor
import android.util.Log
import com.twinlore.app.BuildConfig
import kotlinx.coroutines.delay
import com.twinlore.app.data.AppFont
import com.twinlore.app.data.FocusColor
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.twinlore.app.data.HapticFeedbackIntensity
import com.twinlore.app.data.LedTurnOffTime
import com.twinlore.app.thor.led.ThorLedManager
import com.twinlore.app.ui.utils.FeedbackEvent
import com.twinlore.app.ui.utils.runWithHapticAndLed

/**
 * Compact horizontal bottom bar with icon buttons
 * Designed to take minimal space (~10-15% of screen height)
 * Maintains full controller navigation support.
 * Touch: only the tab/action buttons are clickable; the bar does not consume touches outside the buttons (no full-screen tap-to-show layer).
 */
@Composable
fun CompactBottomBar(
    items: List<BottomBarItem>,
    modifier: Modifier = Modifier,
    visible: Boolean = true,
    onInteraction: (() -> Unit)? = null, // Callback to reset activity timer
    onMoveToTabsRow: (() -> Unit)? = null, // Callback to move focus to tabs row
    selectedIndex: Int = -1 // Currently open/active tab index; its icon uses focus color (avoids flicker during load)
) {
    /** Match guide browser / PDF top bar (surface @ 0.95f) */
    val barFillAlpha = 0.95f
    val glassBorder = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val context = LocalContext.current
    val view = LocalView.current
    // Not a ViewModel: this is composed inside the fold panel's overlay
    // window too, where there is no activity for hiltViewModel() to scope
    // one to. See AppUiSettings.
    val uiSettings = com.twinlore.app.ui.rememberAppUiSettings()
    val focusSoundEnabled by uiSettings.focusSoundEnabled.collectAsState(initial = true)
    val hapticFeedbackIntensity by uiSettings.hapticFeedbackIntensity.collectAsState(initial = HapticFeedbackIntensity.MEDIUM)
    val ledEnabled by uiSettings.ledEnabled.collectAsState(initial = true)
    val ledBrightness by uiSettings.ledBrightness.collectAsState(initial = 127)
    val ledSaturationBoost by uiSettings.ledSaturationBoost.collectAsState(initial = 0.75f)
    val ledTurnOffTime by uiSettings.ledTurnOffTime.collectAsState(initial = LedTurnOffTime.SEC_15)
    val focusColorState by uiSettings.focusColor.collectAsState(initial = FocusColor.CRIMSON)
    val appFont by uiSettings.appFont.collectAsState(initial = AppFont.DEFAULT)
    // Press Start 2P is a very wide glyph font; 9sp overflows the SpaceEvenly row.
    // Shrink only for pixel — other fonts fit fine at 9sp.
    val labelFontSize = if (appFont == AppFont.PIXEL) 6.sp else 9.sp
    val cachedFocusState = LocalCachedFocusColor.current
    val focusColor = cachedFocusState?.value ?: focusColorState.color

    // Bottom bar does NOT auto-focus its buttons on composition or focusedIndex change.
    // Focus is only requested when user explicitly navigates here (D-pad DOWN from content,
    // or focusProperties routes focus here). This prevents stealing focus from tab content.
    
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = barFillAlpha))
                .border(1.dp, glassBorder, RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                ,
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    // Lift content above system nav bar; extra gap so buttons stay clearly visible
                    .navigationBarsPadding()
                    .padding(bottom = 8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items.forEachIndexed { index, item ->
                    CompactIconButton(
                        icon = item.icon,
                        label = item.label,
                        contentDescription = item.label,
                        onClick = {
                            runWithHapticAndLed(view, hapticFeedbackIntensity, ledEnabled, focusColor, ledBrightness, ledSaturationBoost, ledTurnOffTime.inactivityMs, event = FeedbackEvent.SWITCH) {
                                item.onClick()
                                onInteraction?.invoke()
                            }
                        },
                        focusRequester = item.focusRequester,
                        isSelected = index == selectedIndex,
                        hasActiveSelection = selectedIndex >= 0,
                        focusColor = focusColor,
                        labelFontSize = labelFontSize,
                        modifier = Modifier
                            .onKeyEvent { keyEvent ->
                                val nativeKeyEvent = keyEvent.nativeKeyEvent
                                if (nativeKeyEvent.action == KeyEvent.ACTION_DOWN || nativeKeyEvent.action == KeyEvent.ACTION_UP) {
                                    when (nativeKeyEvent.keyCode) {
                                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                                            if (nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                                                val newIndex = if (index > 0) index - 1 else items.size - 1
                                                try { items[newIndex].focusRequester.requestFocus() } catch (_: Exception) {}
                                                onInteraction?.invoke()
                                            }
                                            true
                                        }
                                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                            if (nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                                                val newIndex = if (index < items.size - 1) index + 1 else 0
                                                try { items[newIndex].focusRequester.requestFocus() } catch (_: Exception) {}
                                                onInteraction?.invoke()
                                            }
                                            true
                                        }
                                        KeyEvent.KEYCODE_DPAD_UP -> {
                                            if (onMoveToTabsRow != null) {
                                                if (nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                                                    onMoveToTabsRow.invoke()
                                                    onInteraction?.invoke()
                                                }
                                                true
                                            } else {
                                                false // Let Compose focus traversal move to content above
                                            }
                                        }
                                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                                            true // Block — bottom bar is the lowest element
                                        }
                                        KeyEvent.KEYCODE_DPAD_CENTER,
                                        KeyEvent.KEYCODE_BUTTON_A,
                                        KeyEvent.KEYCODE_ENTER,
                                        KeyEvent.KEYCODE_SPACE -> {
                                            if (nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                                                runWithHapticAndLed(view, hapticFeedbackIntensity, ledEnabled, focusColor, ledBrightness, ledSaturationBoost, ledTurnOffTime.inactivityMs, event = FeedbackEvent.SWITCH) {
                                                    item.onClick()
                                                    onInteraction?.invoke()
                                                }
                                            }
                                            true
                                        }
                                        else -> false
                                    }
                                } else {
                                    false
                                }
                            }
                    )
                }
                }
            }
        }
    }
}

/**
 * Compact icon button with label for bottom bar. Circular icon with optional halo/border + label text below.
 */
@Composable
private fun CompactIconButton(
    icon: ImageVector,
    label: String,
    contentDescription: String,
    onClick: () -> Unit,
    focusRequester: FocusRequester,
    isSelected: Boolean,
    hasActiveSelection: Boolean,
    focusColor: androidx.compose.ui.graphics.Color,
    labelFontSize: TextUnit = 9.sp,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    // Read real Compose focus state so the border ring tracks actual focus
    // and clears the moment focus leaves the bar (e.g., D-pad UP into content).
    val isFocused by interactionSource.collectIsFocusedAsState()
    // Halo and tint follow the selected tab (synchronous on L1/R1 tab switch,
    // avoiding the "two tabs highlighted" glitch from async Compose focus).
    // In Library (no active selection), focus drives both visuals.
    val isTinted = if (hasActiveSelection) isSelected else isFocused
    val showBorder = isFocused
    val iconTint = when {
        isTinted -> focusColor
        else -> MaterialTheme.colorScheme.onSurface
    }
    val labelColor = when {
        isTinted -> focusColor
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .focusRequester(focusRequester)
            .focusable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = rememberRipple(bounded = true, color = focusColor),
                onClick = onClick
            )
            .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .then(
                    if (isTinted) Modifier.background(focusColor.copy(alpha = 0.22f), CircleShape)
                    else Modifier
                )
                .then(
                    if (showBorder) Modifier.border(2.dp, focusColor, CircleShape)
                    else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(18.dp),
                tint = iconTint
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = labelColor,
            maxLines = 1,
            fontSize = labelFontSize
        )
    }
}

/**
 * Data class for bottom bar items
 */
data class BottomBarItem(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit,
    val focusRequester: FocusRequester
)

