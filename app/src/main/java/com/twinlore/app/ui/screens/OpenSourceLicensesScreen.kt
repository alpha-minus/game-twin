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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.twinlore.app.R
import com.twinlore.app.data.LibraryLicense
import com.twinlore.app.data.OpenSourceLicenseRepository
import com.twinlore.app.data.LicenseType
import com.twinlore.app.data.FocusColor
import com.twinlore.app.ui.screens.SettingsViewModel
import androidx.compose.runtime.collectAsState
import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for Open Source Licenses screen
 */
@HiltViewModel
class OpenSourceLicensesViewModel @Inject constructor(
    private val licenseRepository: OpenSourceLicenseRepository
) : ViewModel() {
    private val _licenses = MutableStateFlow<Map<LicenseType, List<LibraryLicense>>>(emptyMap())
    val licenses: StateFlow<Map<LicenseType, List<LibraryLicense>>> = _licenses.asStateFlow()
    
    init {
        viewModelScope.launch {
            _licenses.value = licenseRepository.getAllLicenses()
        }
    }
}

/**
 * Open Source Licenses screen displayed as a dialog
 * Shows all third-party libraries and their licenses
 */
@Composable
fun OpenSourceLicensesScreen(
    onDismiss: () -> Unit,
    viewModel: OpenSourceLicensesViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val licenses by viewModel.licenses.collectAsState()
    
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val focusColorState by settingsViewModel.focusColor.collectAsState(initial = FocusColor.CRIMSON)
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 600.dp)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Header
        Text(
            text = stringResource(R.string.open_source_licenses_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        LicenseType.entries.forEach { type ->
            val group = licenses[type].orEmpty()
            if (group.isEmpty()) return@forEach
            Text(
                text = type.displayName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
            )
            group.forEach { library ->
                LibraryLicenseCard(library = library, focusColor = focusColorState)
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
 * Card displaying a single library's license information
 */
@Composable
private fun LibraryLicenseCard(
    library: LibraryLicense,
    focusColor: FocusColor,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    
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
            // Library name and version
            Text(
                text = "${library.name} ${library.version}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            // Copyright if available
            library.copyright?.let { copyright ->
                Text(
                    text = copyright,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            
            // Expandable license text
            if (expanded) {
                Text(
                    text = library.licenseText,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                )
            }
            
            // Toggle button
            TextButton(
                onClick = { expanded = !expanded },
                colors = ButtonDefaults.textButtonColors(contentColor = focusColor.color),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Text(if (expanded) "Hide License" else "Show License")
            }
        }
    }
}
