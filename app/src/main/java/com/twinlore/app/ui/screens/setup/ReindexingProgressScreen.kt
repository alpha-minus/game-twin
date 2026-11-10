package com.twinlore.app.ui.screens.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.twinlore.app.data.library.LibraryReindexProgress

@Composable
fun ReindexingProgressScreen(progress: LibraryReindexProgress) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (progress) {
            is LibraryReindexProgress.Idle -> {
                Text("Updating library", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(8.dp))
                Text("Preparing...", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(24.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            is LibraryReindexProgress.Scanning -> {
                Text("Scanning media", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(8.dp))
                Text(progress.label, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(24.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            is LibraryReindexProgress.Binding -> {
                Text("Updating vaults", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(8.dp))
                Text(
                    "${progress.vaultName} (${progress.current}/${progress.total})",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(24.dp))
                LinearProgressIndicator(
                    progress = progress.current.toFloat() / progress.total.coerceAtLeast(1),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
