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

/**
 * Help dialog for RetroArch last-played detection setup.
 *
 * Single content — no per-state branches. Shown when the user taps
 * the help icon in Settings, or auto-opened after the folder picker
 * if the just-picked folder didn't yield a parseable
 * `content_history.lpl`.
 */
@Composable
fun RetroArchHelpDialog(
    onPickFolder: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enable RetroArch History tracking") },
        text = {
            Column {
                Text(
                    text = "To detect your last-played RetroArch game:",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                Text("1. Open RetroArch")
                Text("2. Go to Settings → Playlists")
                Text("3. Turn on History")
                Text("4. Play any game once")
                Text("5. Return here and tap Refresh")
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onPickFolder()
                onDismiss()
            }) {
                Text("Pick folder")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}
