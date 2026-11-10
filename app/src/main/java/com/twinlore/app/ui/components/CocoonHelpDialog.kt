package com.twinlore.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.twinlore.app.data.CocoonLogParser.CocoonFolderState

/**
 * State-aware help dialog for Cocoon last-played detection failures.
 * Cocoon needs no in-app settings, so the body only covers parent folder and
 * launching a game.
 */
@Composable
fun CocoonLoggingHelpDialog(
    state: CocoonFolderState?,
    onPickFolder: () -> Unit,
    onDismiss: () -> Unit
) {
    val intro = when (state) {
        CocoonFolderState.FolderUnreachable ->
            "The previously selected folder is no longer accessible. To re-enable detection:"
        else -> "To enable Cocoon detection:"
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Cocoon detection isn't ready") },
        text = {
            Column {
                Text(intro)
                Spacer(modifier = Modifier.height(8.dp))
                Text("1. Pick Cocoon's parent folder (it contains an \"assets\" subfolder).")
                Spacer(modifier = Modifier.height(4.dp))
                Text("2. Launch any game from Cocoon.")
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Got it") }
        },
        dismissButton = {
            TextButton(onClick = onPickFolder) { Text("Pick folder") }
        }
    )
}

/** One-liner banner copy. Dialog has the details. */
const val COCOON_BANNER_COPY = "⚠ Cocoon detection isn't ready."
