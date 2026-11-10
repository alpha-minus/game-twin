package com.twinlore.app.ui.screens

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester

/**
 * Offers All-Files access once the library is big enough for the SAF path to cost
 * real time. Mirrors SyncPromptDialog: focus lands on the dismissing button so a
 * reflexive A press never launches a system settings screen.
 */
@Composable
fun AllFilesAccessPromptDialog(
    onGrant: () -> Unit,
    onNotNow: () -> Unit,
) {
    val notNowFocus = remember { FocusRequester() }
    // runCatching matches every other requestFocus call site: the dialog can be
    // dismissed before this effect runs, leaving the requester unattached.
    LaunchedEffect(Unit) { runCatching { notNowFocus.requestFocus() } }
    AlertDialog(
        onDismissRequest = onNotNow,
        title = { Text("Load artwork faster") },
        text = {
            Text(
                "Your library is now big enough that reading cover art through Android's " +
                    "storage layer costs noticeable time. Granting all files access lets " +
                    "Game Twin read those files directly, which is faster. You can change " +
                    "this any time in Android settings."
            )
        },
        confirmButton = { TextButton(onClick = onGrant) { Text("Grant access") } },
        dismissButton = {
            TextButton(
                onClick = onNotNow,
                modifier = Modifier.focusRequester(notNowFocus),
            ) { Text("Not now") }
        },
    )
}
