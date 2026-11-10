package com.twinlore.app.ui.fold.guide

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

const val FOLD_HOLD_TITLE = "Leave Overlay Mode?"

// What the two ways out actually do, because they differ in what survives.
// Both end the mode. One keeps Game Twin running and hands you the whole app;
// the other ends Game Twin altogether. Neither touches the game behind the
// panel; a body line used to say so, and was cut as noise nobody asked for.
const val FOLD_HOLD_OPEN_APP = "Open in the app"
const val FOLD_HOLD_OPEN_APP_NOTE = "Leaves Overlay Mode and shows the full app."
const val FOLD_HOLD_CLOSE = "Close Game Twin"
const val FOLD_HOLD_CLOSE_NOTE = "Quits the app and takes the panel with it."
const val FOLD_HOLD_CANCEL = "Stay here"

// The one choice that does not leave: the handle, and the panel if it is up,
// go to the other screen. Only offered on a device that has one.
const val FOLD_HOLD_SWAP = "Swap screen"
const val FOLD_HOLD_SWAP_NOTE = "Stays in Overlay Mode and moves the handle to the other screen."

/**
 * The menu a held handle opens.
 *
 * A hold is easy to do by accident with a thumb resting on a small mark, and
 * one of the things it offers cannot be undone, so nothing happens until it is
 * answered. Drawn in the panel's own tree and never as a Dialog, for the
 * reason [FoldPanelIntro] gives: this window has no token for a sub-window and
 * a Dialog here throws over a running game.
 *
 * The two destinations are rows rather than a row of buttons: they need a line
 * of explanation each, since "open the app" and "close the app" both end the
 * mode and only one of them is recoverable. Staying is last and plainest,
 * because it is the one you reach for when the hold was a mistake.
 */
@Composable
fun FoldCloseAppPrompt(
    onOpenApp: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onSwap: (() -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("fold_close_prompt")
            .background(Color.Black.copy(alpha = 0.6f))
            // A press on the scrim is not a press on the page under it, and it
            // is not an answer either: this one has to be answered on purpose.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .widthIn(max = 420.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.background)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = FOLD_HOLD_TITLE,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            // First, because it is the one that keeps you where you are: a
            // hold made to move the handle should not have to read past two
            // ways out to find it.
            if (onSwap != null) {
                HoldChoice(
                    label = FOLD_HOLD_SWAP,
                    note = FOLD_HOLD_SWAP_NOTE,
                    tint = MaterialTheme.colorScheme.onBackground,
                    testTag = "fold_hold_swap",
                    onClick = onSwap,
                )
            }
            HoldChoice(
                label = FOLD_HOLD_OPEN_APP,
                note = FOLD_HOLD_OPEN_APP_NOTE,
                tint = MaterialTheme.colorScheme.onBackground,
                testTag = "fold_hold_open_app",
                onClick = onOpenApp,
            )
            HoldChoice(
                label = FOLD_HOLD_CLOSE,
                note = FOLD_HOLD_CLOSE_NOTE,
                tint = MaterialTheme.colorScheme.error,
                testTag = "fold_close_confirm",
                onClick = onConfirm,
            )
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.End).testTag("fold_close_cancel"),
            ) { Text(FOLD_HOLD_CANCEL) }
        }
    }
}

/** One destination: what it is called, and what it costs. */
@Composable
private fun HoldChoice(
    label: String,
    note: String,
    tint: Color,
    testTag: String,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().testTag(testTag),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = tint,
            )
            Text(
                text = note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
