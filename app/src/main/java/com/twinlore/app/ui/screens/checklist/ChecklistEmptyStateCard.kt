package com.twinlore.app.ui.screens.checklist

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Empty-state card shown when the active tab has no sections yet. */
@Composable
fun ChecklistEmptyStateCard(
    onAddSection: () -> Unit,
    onPaste: () -> Unit,
    onBrowse: () -> Unit,
    onAiPrompt: () -> Unit,
    modifier: Modifier = Modifier,
    primaryButtonFocusRequester: FocusRequester? = null,
) {
    Card(modifier = modifier.fillMaxWidth().padding(16.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Primary action: prominent filled button so first-timers know
            // where to start. The three alternates are stacked full-width
            // text buttons that read as a quiet menu of "other ways" rather
            // than a competing row of equal-weight controls.
            Button(
                onClick = onAddSection,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (primaryButtonFocusRequester != null) Modifier.focusRequester(primaryButtonFocusRequester)
                        else Modifier
                    ),
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Add section")
            }
            Spacer(Modifier.height(8.dp))
            EmptyStateAltAction(
                icon = Icons.Default.ContentPaste,
                // Matches the kebab menu's "Import checklist" entry — the
                // sheet this opens offers both clipboard paste and file read.
                label = "Import checklist",
                onClick = onPaste,
            )
            EmptyStateAltAction(
                icon = Icons.Default.Language,
                label = "Search the web",
                onClick = onBrowse,
            )
            EmptyStateAltAction(
                icon = Icons.Default.AutoAwesome,
                label = "Generate with AI",
                onClick = onAiPrompt,
            )
        }
    }
}

/**
 * Single alternate-action row used under the primary "Add section" button —
 * a quiet TextButton with leading icon, full width, left-aligned. Stacking
 * three of these reads as a list of "or you can also…" choices instead of a
 * cluttered row of competing buttons.
 */
@Composable
private fun EmptyStateAltAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = 12.dp,
            vertical = 10.dp,
        ),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Start,
        )
    }
}

/**
 * Simple confirmation shown after the AI prompt (or prompt + guide reference) has
 * been copied to the clipboard. Replaces the earlier long-form dialog that dumped
 * the entire prompt text on screen — users don't need to re-read what they just
 * copied, they need the quick confirmation so they can switch to their AI tool.
 */
@Composable
fun AiPromptCopiedDialog(
    title: String,
    body: String,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("OK") }
        },
    )
}
