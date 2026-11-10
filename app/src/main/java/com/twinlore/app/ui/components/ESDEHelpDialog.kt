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
import com.twinlore.app.data.ESDELogParser.ESDEFolderState

/**
 * State-aware help dialog for ES-DE last-played detection failures.
 * Unified title and body across states; FolderUnreachable gets a different
 * intro. [Pick folder] is always offered.
 */
@Composable
fun ESDELoggingHelpDialog(
    state: ESDEFolderState?,
    onPickFolder: () -> Unit,
    onDismiss: () -> Unit
) {
    val intro = when (state) {
        ESDEFolderState.FolderUnreachable ->
            "The previously selected folder is no longer accessible. To re-enable detection:"
        else -> "To enable ES-DE detection:"
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("ES-DE detection isn't ready") },
        text = {
            Column {
                Text(intro)
                Spacer(modifier = Modifier.height(8.dp))
                Text("1. Pick ES-DE's parent folder (it contains a \"gamelists\" subfolder).")
                Spacer(modifier = Modifier.height(4.dp))
                Text("2. In ES-DE, open Settings → Other Settings and:")
                Text("   • Turn on \"Debug Mode\"")
                Spacer(modifier = Modifier.height(4.dp))
                Text("3. Launch any game once.")
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
const val ESDE_BANNER_COPY = "⚠ ES-DE detection isn't ready."
