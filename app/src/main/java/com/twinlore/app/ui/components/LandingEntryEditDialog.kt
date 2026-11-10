package com.twinlore.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.twinlore.app.data.LandingEntry
import com.twinlore.app.ui.navigation.NavigableButton
import com.twinlore.app.ui.navigation.NavigableTextField

/**
 * Dropdown options for the file type filter. "Any" means no filetype operator;
 * everything else maps to a Google `filetype:<value>` query operator.
 */
private val FILE_TYPE_OPTIONS = listOf(
    "Any" to "",
    "pdf" to "pdf",
    "txt" to "txt",
    "doc" to "doc",
    "html" to "html",
    "jpg" to "jpg",
    "png" to "png",
)

/**
 * Standalone dialog for editing a single landing page slot
 * (label, append word, site, file type, image search).
 */
@Composable
fun LandingEntryEditDialog(
    slotIndex: Int,
    entry: LandingEntry,
    onSave: (LandingEntry) -> Unit,
    onDismiss: () -> Unit,
    focusColor: Color,
    containerColor: Color
) {
    var label by remember(entry) { mutableStateOf(entry.label) }
    var appendWord by remember(entry) { mutableStateOf(entry.appendWord) }
    var useImageSearch by remember(entry) { mutableStateOf(entry.useImageSearch) }
    var site by remember(entry) { mutableStateOf(entry.site) }
    var fileType by remember(entry) { mutableStateOf(entry.fileType) }
    var fileTypeMenuExpanded by remember { mutableStateOf(false) }
    val fileTypeButtonFocusRequester = remember { FocusRequester() }
    val textOnSurface = MaterialTheme.colorScheme.onSurface
    val textFieldFocusRequester = remember { FocusRequester() }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = containerColor,
        titleContentColor = textOnSurface,
        textContentColor = textOnSurface,
        title = { Text("Edit slot ${slotIndex + 1}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Label (shown on landing page)",
                    style = MaterialTheme.typography.bodySmall,
                    color = textOnSurface
                )
                NavigableTextField(
                    value = label,
                    onValueChange = { value -> label = value },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = "Walkthrough",
                    externalFocusRequester = textFieldFocusRequester
                )
                LaunchedEffect(Unit) {
                    textFieldFocusRequester.requestFocus()
                }
                Text(
                    "Append word (search: game name + this)",
                    style = MaterialTheme.typography.bodySmall,
                    color = textOnSurface
                )
                NavigableTextField(
                    value = appendWord,
                    onValueChange = { value -> appendWord = value },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = "walkthrough"
                )
                Text(
                    "Restrict to site (optional)",
                    style = MaterialTheme.typography.bodySmall,
                    color = textOnSurface
                )
                NavigableTextField(
                    value = site,
                    onValueChange = { value -> site = value },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = "gamefaqs.gamespot.com"
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "File type",
                        style = MaterialTheme.typography.bodyMedium,
                        color = textOnSurface
                    )
                    Box {
                        val currentLabel = FILE_TYPE_OPTIONS.firstOrNull { it.second == fileType }?.first ?: "Any"
                        NavigableButton(
                            text = currentLabel,
                            onClick = { fileTypeMenuExpanded = true },
                            containerColor = Color.Transparent,
                            contentColor = focusColor,
                            compact = true,
                            externalFocusRequester = fileTypeButtonFocusRequester
                        )
                        androidx.compose.material3.DropdownMenu(
                            expanded = fileTypeMenuExpanded,
                            onDismissRequest = {
                                fileTypeMenuExpanded = false
                                try { fileTypeButtonFocusRequester.requestFocus() } catch (_: Throwable) {}
                            },
                            modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                        ) {
                            FILE_TYPE_OPTIONS.forEach { (display, value) ->
                                com.twinlore.app.ui.navigation.NavigableMenuItem(
                                    text = display,
                                    onClick = {
                                        fileType = value
                                        fileTypeMenuExpanded = false
                                        try { fileTypeButtonFocusRequester.requestFocus() } catch (_: Throwable) {}
                                    }
                                )
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Image search", style = MaterialTheme.typography.bodyMedium, color = textOnSurface)
                    Switch(
                        checked = useImageSearch,
                        onCheckedChange = { checked -> useImageSearch = checked },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = focusColor,
                            checkedTrackColor = focusColor.copy(alpha = 0.5f)
                        )
                    )
                }
            }
        },
        confirmButton = {
            NavigableButton(
                text = "Save",
                onClick = {
                    onSave(entry.copy(
                        label = label,
                        appendWord = appendWord,
                        useImageSearch = useImageSearch,
                        site = site,
                        fileType = fileType
                    ))
                },
                containerColor = Color.Transparent,
                contentColor = focusColor,
                compact = true
            )
        },
        dismissButton = {
            NavigableButton(
                text = "Cancel",
                onClick = onDismiss,
                containerColor = Color.Transparent,
                contentColor = textOnSurface,
                compact = true
            )
        }
    )
}

