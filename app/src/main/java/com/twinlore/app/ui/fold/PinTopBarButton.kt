package com.twinlore.app.ui.fold

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.twinlore.app.ui.LocalCachedFocusColor
import com.twinlore.app.ui.screens.SettingsViewModel
import com.twinlore.app.ui.utils.gamepadFocusable

/** Pure so the two labels can be asserted without a Compose host. */
fun pinTopBarContentDescription(pinned: Boolean): String =
    if (pinned) "Unpin the top bar" else "Pin the top bar"

// The D-pad confirm handling comes from FoldButton.kt's `activateOnConfirmKey`
// (same package, now internal). This button sits immediately beside
// FoldButtonContent in the same bar, so it needs exactly the same behavior;
// a verbatim second copy lived here until that one could be shared.

/**
 * The presentational half. State is a parameter so it is testable without Hilt.
 *
 * Focus ring, D-pad confirm handling and gamepad focusability follow the same
 * pattern as [FoldButtonContent]: every other control in these bars paints a
 * focus ring, and this one sits directly beside them.
 */
@Composable
fun PinTopBarButtonContent(
    pinned: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color? = null,
    iconSize: Dp = 20.dp,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val focusColor = LocalCachedFocusColor.current?.value ?: MaterialTheme.colorScheme.primary
    IconButton(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = modifier
            .size(32.dp)
            .testTag("pin_top_bar_button")
            .gamepadFocusable()
            .activateOnConfirmKey(onClick)
            .then(
                if (isFocused) {
                    Modifier
                        .background(focusColor.copy(alpha = 0.22f), CircleShape)
                        .border(2.dp, focusColor, CircleShape)
                } else {
                    Modifier
                }
            ),
    ) {
        Icon(
            imageVector = if (pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
            contentDescription = pinTopBarContentDescription(pinned),
            tint = tint ?: MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(iconSize),
        )
    }
}

/**
 * Pins the vault session top bar so the per-tab auto-hide cannot take it away.
 * This is where the setting formerly known as "Keep the Top Bar on Screen"
 * lives now: on the bar it controls, rather than four screens away.
 *
 * [onInputReceived], when supplied, resets the host screen's idle clock the way
 * its other chrome controls do. It matters most on the press that unpins:
 * without it the auto-hide resumes from whenever the last unrelated
 * interaction was, so the bar can vanish in the same frame as the tap that
 * released it. Left null at call sites with no such callback to plumb.
 */
@Composable
fun PinTopBarButton(
    tint: Color? = null,
    iconSize: Dp = 20.dp,
    onInputReceived: (() -> Unit)? = null,
) {
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val pinned by settingsViewModel.keepTopBarVisible.collectAsState(initial = false)
    PinTopBarButtonContent(
        pinned = pinned,
        onClick = {
            onInputReceived?.invoke()
            settingsViewModel.setKeepTopBarVisible(!pinned)
        },
        tint = tint,
        iconSize = iconSize,
    )
}
