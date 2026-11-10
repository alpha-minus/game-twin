package com.twinlore.app.ui.startup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

/**
 * Shown when the newest snapshot in a library folder was written by a newer
 * build of Game Twin than the one installed — its Room schema version is
 * higher than this build can read.
 *
 * The folder is NEVER adopted as the home folder in this case: loading it would
 * corrupt the newer library (destructive migration on open) and stranding the
 * app on it would let the next auto-save overwrite it. The current library is
 * left exactly as it was; the user chooses how to proceed:
 *
 *  - [onPickDifferentFolder] — reopen the folder picker to choose another folder.
 *  - [onUpdate] — check for / install a newer build that can read this library.
 *  - [onDismiss] — cancel and keep the current library folder.
 *
 * Non-dismissable on back / outside-tap on the cold-start path (see
 * [dismissable]) so the user has to make a choice before the empty library
 * renders behind it.
 */
@Composable
fun LibrarySnapshotTooNewDialog(
    savedVersion: Int,
    supportedVersion: Int,
    onPickDifferentFolder: () -> Unit,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit,
    dismissable: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = { if (dismissable) onDismiss() },
        title = { Text("Library was saved by a newer version") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "This folder contains a library saved by a newer version of " +
                        "Game Twin than the one you have installed. Loading it would " +
                        "corrupt the file, so this folder was NOT opened and your " +
                        "current library is untouched.",
                )
                Text(
                    "Update Game Twin to open this library, or pick a different " +
                        "library folder.",
                )
                Text(
                    "File schema version: $savedVersion. " +
                        "Supported by this build: up to $supportedVersion.",
                )
            }
        },
        // Three actions don't fit the confirm/dismiss slot pair, so stack them
        // vertically in the confirm slot (Update is the primary remedy, on top).
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.End,
            ) {
                TextButton(onClick = onUpdate) { Text("Update Game Twin") }
                TextButton(onClick = onPickDifferentFolder) { Text("Pick a different folder") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = dismissable,
            dismissOnClickOutside = dismissable,
        ),
        modifier = Modifier.padding(8.dp),
    )
}
