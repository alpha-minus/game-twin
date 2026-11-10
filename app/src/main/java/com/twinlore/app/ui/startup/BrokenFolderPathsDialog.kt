package com.twinlore.app.ui.startup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.twinlore.app.data.AssetSource
import com.twinlore.app.data.ValidationResult

@Composable
fun BrokenFolderPathsDialog(
    result: ValidationResult,
    onSelectLibraryFolder: () -> Unit,
    onDismiss: () -> Unit,
) {
    val cancelable = !result.lostHome
    AlertDialog(
        onDismissRequest = { if (cancelable) onDismiss() },
        title = { Text("Some folders couldn't be found") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (result.lostHome) {
                    Text(
                        "Your library folder is no longer accessible. " +
                            "It may have been deleted, renamed, or moved. " +
                            "You'll need to select a library folder to continue.",
                    )
                }
                if (AssetSource.ESDE in result.lostAssetSources) {
                    Text(
                        "Your ES-DE media folder is no longer accessible " +
                            "and has been disconnected. Set it again in Settings.",
                    )
                }
                if (AssetSource.COCOON in result.lostAssetSources) {
                    Text(
                        "Your Cocoon media folder is no longer accessible " +
                            "and has been disconnected. Set it again in Settings.",
                    )
                }
            }
        },
        confirmButton = {
            if (result.lostHome) {
                TextButton(onClick = onSelectLibraryFolder) {
                    Text("Select library folder")
                }
            } else {
                TextButton(onClick = onDismiss) { Text("OK") }
            }
        },
        // No dismiss button: a single-action dialog.
        properties = DialogProperties(
            dismissOnBackPress = cancelable,
            dismissOnClickOutside = cancelable,
        ),
        modifier = Modifier.padding(8.dp),
    )
}
