package com.twinlore.app.ui.fold

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.twinlore.app.R

/**
 * Shown the first time the fold button is pressed without the overlay
 * permission, and again any time it has been revoked.
 *
 * There is no "has seen this" flag: permission absence is the state. That keeps
 * a revoked permission self-explaining and removes a stored key rather than
 * adding one.
 */
@Composable
fun FoldPermissionDialog(
    onContinue: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.fold_permission_title)) },
        text = { Text(stringResource(R.string.fold_permission_body)) },
        confirmButton = {
            TextButton(
                onClick = onContinue,
                modifier = Modifier.testTag("fold_permission_continue"),
            ) { Text(stringResource(R.string.fold_permission_continue)) }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("fold_permission_dismiss"),
            ) { Text(stringResource(R.string.fold_permission_dismiss)) }
        },
    )
}
