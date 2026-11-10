package com.twinlore.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

enum class RemoveSearchPathMode { REMOVE, DISABLE }

@Composable
fun RemoveSearchPathDialog(
    pathLabel: String,
    affectedVaultCount: Int,
    mode: RemoveSearchPathMode,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val title = when (mode) {
        RemoveSearchPathMode.REMOVE -> "Remove this search path?"
        RemoveSearchPathMode.DISABLE -> "Disable this search path?"
    }
    val confirmText = when (mode) {
        RemoveSearchPathMode.REMOVE -> "Remove"
        RemoveSearchPathMode.DISABLE -> "Disable"
    }
    val body = if (affectedVaultCount > 0) {
        val plural = if (affectedVaultCount == 1) "vault" else "vaults"
        "$pathLabel will unlink files from $affectedVaultCount $plural. The files on disk are not deleted."
    } else {
        "$pathLabel won't unlink anything from your vaults. The files on disk are not deleted."
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Column { Text(body) } },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmText) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
