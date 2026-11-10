package com.twinlore.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.twinlore.app.R
import com.twinlore.app.data.FocusColor
import com.twinlore.app.data.legal.Credit
import com.twinlore.app.data.legal.CreditGroup
import com.twinlore.app.data.legal.Credits

/**
 * Credits screen displayed as a dialog. Attribution for the metadata sources Game Twin queries,
 * the fonts it bundles, and the upstream projects it integrates with. Not open-source software
 * dependencies (those live in Open Source Licenses).
 */
@Composable
fun CreditsScreen(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val focusColorState by settingsViewModel.focusColor.collectAsState(initial = FocusColor.CRIMSON)
    val uriHandler = LocalUriHandler.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 600.dp)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Header
        Text(
            text = stringResource(R.string.credits_menu_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        CreditGroup.entries.forEach { group ->
            val credits = Credits.ALL[group].orEmpty()
            if (credits.isEmpty()) return@forEach
            Text(
                text = group.displayName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
            )
            credits.forEach { credit ->
                CreditCard(
                    credit = credit,
                    onOpenUrl = { url -> uriHandler.openUri(url) },
                )
            }
        }

        // Close button
        TextButton(
            onClick = onDismiss,
            colors = ButtonDefaults.textButtonColors(contentColor = focusColorState.color),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
        ) {
            Text(stringResource(R.string.impressum_close))
        }
    }
}

/**
 * Card displaying a single credit's name and detail. Opens its URL, if any, when tapped.
 */
@Composable
private fun CreditCard(
    credit: Credit,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val url = credit.url
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Text(
                text = credit.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = credit.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
            if (url != null) {
                TextButton(
                    onClick = { onOpenUrl(url) },
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Text(url)
                }
            }
        }
    }
}
