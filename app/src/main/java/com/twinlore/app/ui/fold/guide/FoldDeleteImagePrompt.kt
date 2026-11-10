package com.twinlore.app.ui.fold.guide

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
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

const val FOLD_DELETE_TITLE = "Delete this image?"
const val FOLD_DELETE_NOTE = "It leaves the vault along with its markers. This cannot be undone."
const val FOLD_DELETE_CONFIRM = "Delete"
const val FOLD_DELETE_CANCEL = "Keep it"

/**
 * The question the bin asks.
 *
 * Drawn in the panel's own tree and never as a Dialog, for the reason
 * [FoldCloseAppPrompt] gives: this window has no token for a sub-window and a
 * Dialog here throws over a running game. Same scrim, same card, and the scrim
 * is not an answer: the button that sits under a resting thumb is exactly the
 * one whose question must be answered on purpose.
 *
 * Keeping is first and plainest, in the theme's own colour; deleting wears
 * the error colour, as the bin does, so the two read as the same control
 * asking and being answered.
 */
@Composable
fun FoldDeleteImagePrompt(
    /** What is about to go, so the question names it. */
    imageTitle: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("fold_delete_prompt")
            .background(Color.Black.copy(alpha = 0.6f))
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
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = FOLD_DELETE_TITLE,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            if (imageTitle.isNotBlank()) {
                Text(
                    text = imageTitle,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
            Text(
                text = FOLD_DELETE_NOTE,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("fold_delete_cancel"),
                ) { Text(FOLD_DELETE_CANCEL) }
                TextButton(
                    onClick = onConfirm,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("fold_delete_confirm"),
                ) { Text(FOLD_DELETE_CONFIRM) }
            }
        }
    }
}
