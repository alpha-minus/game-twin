package com.twinlore.app.ui.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import com.twinlore.app.data.library.sync.SyncPromptUi

@Composable
fun SyncPromptDialog(
    prompt: SyncPromptUi,
    onSync: () -> Unit,
    onNotNow: () -> Unit,
) {
    val notNowFocus = remember { FocusRequester() }
    // try/catch matches every other requestFocus call site in the app: the
    // dialog can be dismissed (prompt nulled) before this effect runs, in
    // which case the requester is no longer attached and would throw.
    LaunchedEffect(Unit) { runCatching { notNowFocus.requestFocus() } }
    AlertDialog(
        onDismissRequest = onNotNow,
        title = {
            Text(
                if (prompt.deviceLabels.size > 1) "Changes from ${prompt.deviceLabels.size} devices"
                else "Changes from ${prompt.deviceLabels.firstOrNull() ?: "another device"}"
            )
        },
        text = {
            // Scroll when the change summary (vault lists + one line per
            // conflicting vault) is long — a big multi-device merge would
            // otherwise overflow the dialog and clip the buttons.
            Column(
                modifier = Modifier
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (prompt.newVaults.isNotEmpty()) Text("New: ${truncate(prompt.newVaults)}")
                if (prompt.updatedVaults.isNotEmpty()) Text("Updated: ${truncate(prompt.updatedVaults)}")
                if (prompt.deletedVaults.isNotEmpty()) Text(
                    "Deleted: ${truncate(prompt.deletedVaults)}",
                    color = MaterialTheme.colorScheme.error,
                )
                prompt.conflictLines.forEach {
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = { TextButton(onClick = onSync) { Text("Sync") } },
        dismissButton = {
            TextButton(
                onClick = onNotNow,
                modifier = Modifier.focusRequester(notNowFocus),
            ) { Text("Not now") }
        },
    )
}

private fun truncate(names: List<String>, max: Int = 3): String =
    if (names.size <= max) names.joinToString(", ")
    else names.take(max).joinToString(", ") + " + ${names.size - max} more"
