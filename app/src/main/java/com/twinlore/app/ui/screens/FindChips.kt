package com.twinlore.app.ui.screens

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.twinlore.app.ui.utils.FeedbackEvent
import com.twinlore.app.ui.utils.FeedbackManager

/**
 * The find views' recent searches: one chip per query, newest first, in a
 * row that scrolls sideways when it runs out of room. D-pad LEFT / RIGHT
 * walks the chips; UP and DOWN leave the row the usual way.
 *
 * [onPick] gets the query and whether a controller key (rather than a
 * touch) picked it, so the caller can hand focus on once the row goes away.
 */
@Composable
internal fun RecentFindSearchRow(
    queries: List<String>,
    focusColor: Color,
    onPick: (query: String, viaKey: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.History,
            contentDescription = "Recent searches",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        queries.forEach { q ->
            FindChip(
                label = q,
                focusColor = focusColor,
                onClick = { viaKey -> onPick(q, viaKey) },
            )
        }
    }
}

/**
 * A small focusable pill, shared by the recent-search row and the page
 * group's "All pages" / "This page" switch. [selected] fills it with the
 * focus colour's tint. Draws the same 2dp focus-colour ring the find bar's
 * buttons use.
 *
 * A confirm press fires once: the Thor sends BUTTON_A and then DPAD_CENTER
 * for one press, and a recent-search pick runs a search, so a second fire
 * would step to the next match. The key-up halves are swallowed too, so
 * clickable's own Enter / Center handling never runs on top.
 */
@Composable
internal fun FindChip(
    label: String,
    focusColor: Color,
    onClick: (viaKey: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    leadingIcon: ImageVector? = null,
) {
    val view = LocalView.current
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    LaunchedEffect(isFocused) {
        if (isFocused) FeedbackManager.play(view, FeedbackEvent.FOCUS)
    }
    // Uptime of the last key-driven fire; one physical press lands inside it.
    val lastKeyFire = remember { longArrayOf(0L) }
    val shape = RoundedCornerShape(16.dp)
    val bg = when {
        selected -> focusColor.copy(alpha = 0.22f)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    Row(
        modifier = modifier
            .background(bg, shape)
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused || selected) focusColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                shape = shape,
            )
            .onPreviewKeyEvent { keyEvent ->
                val native = keyEvent.nativeKeyEvent
                when (native.keyCode) {
                    KeyEvent.KEYCODE_BUTTON_A,
                    KeyEvent.KEYCODE_DPAD_CENTER,
                    KeyEvent.KEYCODE_ENTER,
                    KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                        if (native.action == KeyEvent.ACTION_DOWN && native.repeatCount == 0) {
                            val now = android.os.SystemClock.uptimeMillis()
                            if (now - lastKeyFire[0] > KEY_PRESS_WINDOW_MS) {
                                lastKeyFire[0] = now
                                FeedbackManager.play(view, FeedbackEvent.CONFIRM)
                                onClick(true)
                            }
                        }
                        true
                    }
                    else -> false
                }
            }
            .clickable(interactionSource = interactionSource, indication = null) {
                FeedbackManager.play(view, FeedbackEvent.CONFIRM)
                onClick(false)
            }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = if (selected || isFocused) focusColor else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Long enough to cover the Thor's A + CENTER pair, short enough for a real second press. */
private const val KEY_PRESS_WINDOW_MS = 250L
