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
 * Shown once, before the first entry, and only on a device with a second
 * screen. Telling someone to hold for a screen they do not have would be worse
 * than saying nothing.
 *
 * One button: the press that opened this was a request to enter overlay, and
 * it is not thrown away. Dismissing with the system back gesture cancels
 * without recording the hint as seen, so the next press teaches it again.
 */
@Composable
fun FoldSwapHintDialog(
    onContinue: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.fold_swap_hint_title)) },
        text = { Text(stringResource(R.string.fold_swap_hint_body)) },
        confirmButton = {
            TextButton(
                onClick = onContinue,
                modifier = Modifier.testTag("fold_swap_hint_continue"),
            ) { Text(stringResource(R.string.fold_swap_hint_continue)) }
        },
    )
}
