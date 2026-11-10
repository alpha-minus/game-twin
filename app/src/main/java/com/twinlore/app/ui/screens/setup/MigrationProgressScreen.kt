package com.twinlore.app.ui.screens.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.twinlore.app.data.library.LibraryMigrationProgress

@Composable
fun MigrationProgressScreen(progress: LibraryMigrationProgress) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (progress) {
            is LibraryMigrationProgress.Working -> {
                Text("Moving ${progress.vaultName}")
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(progress = progress.current.toFloat() / progress.total)
                Spacer(Modifier.height(8.dp))
                Text("${progress.current} of ${progress.total}")
            }
            is LibraryMigrationProgress.Failed -> {
                Text("Failed for vault ${progress.vaultId}: ${progress.cause.message}")
            }
            is LibraryMigrationProgress.Done -> Text("Migration complete.")
            is LibraryMigrationProgress.Idle -> Text("Preparing...")
        }
    }
}
