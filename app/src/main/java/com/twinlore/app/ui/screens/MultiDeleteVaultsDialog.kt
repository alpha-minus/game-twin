package com.twinlore.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.twinlore.app.data.FocusColor
import com.twinlore.app.domain.VaultCleanupInfo
import com.twinlore.app.domain.formatVaultSize
import com.twinlore.app.ui.LocalCachedFocusColor
import com.twinlore.app.ui.LocalThemePalette
import com.twinlore.app.ui.navigation.NavigableButton
import com.twinlore.app.ui.navigation.NavigableMenuItem

/**
 * Which vault ids a quick-select filter tap leaves selected: adds every match, or,
 * when all matches are already ticked, takes them back out so the same button undoes
 * itself. Pure so it can be tested without Compose.
 */
internal fun applyQuickSelect(selected: Set<String>, matching: Collection<String>): Set<String> =
    if (matching.isNotEmpty() && selected.containsAll(matching)) selected - matching.toSet()
    else selected + matching

/** "3 guides · 1 image", "No guides or images". */
internal fun guideImageSummary(guides: Int, images: Int): String {
    if (guides == 0 && images == 0) return "No guides or images"
    val g = if (guides == 1) "1 guide" else "$guides guides"
    val i = if (images == 1) "1 image" else "$images images"
    return "$g · $i"
}

/** Rows by the space deleting them frees, largest first; stable, so ties keep library order. */
internal fun sortBySize(rows: List<VaultCleanupInfo>, guidesOnly: Boolean): List<VaultCleanupInfo> =
    rows.sortedByDescending { if (guidesOnly) it.guideImageBytes else it.totalBytes }

/**
 * Full-screen picker behind the vault menu's "Delete multiple": tick vaults by hand or
 * with the quick-select filters (all, not opened in 3+ months, missing files, empty),
 * then delete them all with one confirmation. [initiallySelectedId] (the vault the menu
 * was opened on) starts ticked.
 */
@Composable
fun MultiDeleteVaultsDialog(
    libraryViewModel: LibraryViewModel,
    initiallySelectedId: String?,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val viewModel: MultiDeleteViewModel = hiltViewModel()
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val focusColorState by settingsViewModel.focusColor.collectAsState(initial = FocusColor.CRIMSON)
    val accent = LocalCachedFocusColor.current?.value ?: focusColorState.color
    val palette = LocalThemePalette.current
    val textColor = palette.foreground
    val secondaryText = palette.foreground.copy(alpha = 0.65f)

    val state by viewModel.state.collectAsState()
    val vaultProgress by libraryViewModel.bulkDeleteProgress.collectAsState()
    val guideProgress by viewModel.guideCleanupProgress.collectAsState()
    val progress = vaultProgress ?: guideProgress
    LaunchedEffect(Unit) { viewModel.load(libraryViewModel.vaults.value) }

    val rows: List<VaultCleanupInfo> = when (val s = state) {
        MultiDeleteViewModel.State.Idle -> emptyList()
        is MultiDeleteViewModel.State.Checking -> s.rows
        is MultiDeleteViewModel.State.Ready -> s.rows
    }
    val checkingFiles = state !is MultiDeleteViewModel.State.Ready

    val selected = remember { mutableStateListOf<String>().apply { initiallySelectedId?.let(::add) } }
    var showConfirm by remember { mutableStateOf(false) }
    // "Guides and images only": keep the vaults, remove just their guides and images.
    var guidesOnly by remember { mutableStateOf(false) }
    val deleting = progress != null

    fun quickSelect(matching: List<String>) {
        val next = applyQuickSelect(selected.toSet(), matching)
        selected.clear()
        selected.addAll(rows.map { it.vault.id }.filter { it in next })
    }

    // Biggest first, by what the current mode would free; ties keep library order.
    val sortedRows = remember(rows, guidesOnly) { sortBySize(rows, guidesOnly) }
    val allIds = rows.map { it.vault.id }
    val staleIds = rows.filter { it.isStale }.map { it.vault.id }
    val missingIds = rows.filter { it.hasMissingFiles }.map { it.vault.id }
    val emptyIds = rows.filter { it.isEmpty }.map { it.vault.id }

    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(rows.isNotEmpty()) {
        if (rows.isNotEmpty()) runCatching { firstFocus.requestFocus() }
    }

    Dialog(
        onDismissRequest = { if (!deleting) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(palette.background)
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Delete multiple",
                    style = MaterialTheme.typography.titleLarge,
                    color = accent,
                    modifier = Modifier.weight(1f),
                )
                val selectedBytes = rows.filter { it.vault.id in selected }
                    .sumOf { if (guidesOnly) it.guideImageBytes else it.totalBytes }
                Text(
                    text = "${selected.size} of ${rows.size} selected" +
                        if (selected.isNotEmpty()) " · ${formatVaultSize(selectedBytes)}" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = secondaryText,
                )
            }
            NavigableMenuItem(
                content = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Guides and images only", style = MaterialTheme.typography.bodyLarge, color = textColor)
                            Text(
                                text = "Keeps vaults and artwork, frees space",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                                color = secondaryText,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Switch(
                            checked = guidesOnly,
                            onCheckedChange = null,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = accent,
                                checkedTrackColor = accent.copy(alpha = 0.4f),
                            ),
                        )
                    }
                },
                onClick = { if (!deleting) guidesOnly = !guidesOnly },
                focusId = "multi_delete_guides_only",
            )
            // One row, short labels: the Thor's bottom screen is only 336dp wide, and a
            // wrapped second row of filters ate a list row's worth of height.
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                val allSelected = allIds.isNotEmpty() && selected.size == allIds.size
                NavigableButton(
                    text = if (allSelected) "None" else "All",
                    onClick = { quickSelect(allIds) },
                    focusId = "multi_delete_all",
                    externalFocusRequester = firstFocus,
                    enabled = !deleting && allIds.isNotEmpty(),
                    compact = true,
                )
                NavigableButton(
                    text = "3+ months (${staleIds.size})",
                    onClick = { quickSelect(staleIds) },
                    focusId = "multi_delete_stale",
                    enabled = !deleting && staleIds.isNotEmpty(),
                    compact = true,
                )
                NavigableButton(
                    text = if (checkingFiles) "Missing (...)" else "Missing (${missingIds.size})",
                    onClick = { quickSelect(missingIds) },
                    focusId = "multi_delete_missing",
                    enabled = !deleting && !checkingFiles && missingIds.isNotEmpty(),
                    compact = true,
                )
                NavigableButton(
                    text = "Empty (${emptyIds.size})",
                    onClick = { quickSelect(emptyIds) },
                    focusId = "multi_delete_empty",
                    enabled = !deleting && emptyIds.isNotEmpty(),
                    compact = true,
                )
            }
            Spacer(Modifier.padding(top = 8.dp))
            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                items(sortedRows, key = { it.vault.id }) { row ->
                    val id = row.vault.id
                    val isChecked = id in selected
                    NavigableMenuItem(
                        content = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = null,
                                    colors = CheckboxDefaults.colors(checkedColor = accent),
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = row.vault.displayName,
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = textColor,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f),
                                        )
                                        Spacer(Modifier.width(12.dp))
                                        Text(
                                            text = formatVaultSize(if (guidesOnly) row.guideImageBytes else row.totalBytes),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = secondaryText,
                                            maxLines = 1,
                                        )
                                    }
                                    val reasons = if (guidesOnly) {
                                        listOf(guideImageSummary(row.guideCount, row.imageCount)) + row.reasonLabels
                                    } else row.reasonLabels
                                    if (reasons.isNotEmpty()) {
                                        Text(
                                            text = reasons.joinToString(" · "),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = secondaryText,
                                        )
                                    }
                                }
                            }
                        },
                        onClick = {
                            if (deleting) return@NavigableMenuItem
                            if (isChecked) selected.remove(id) else selected.add(id)
                        },
                        focusId = "multi_delete_row_$id",
                    )
                }
            }
            Spacer(Modifier.padding(top = 8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                progress?.let {
                    Text(
                        text = if (guideProgress != null) "Cleaning ${it.done + 1} of ${it.total}"
                        else "Deleting ${it.done + 1} of ${it.total}",
                        color = secondaryText,
                        modifier = Modifier.weight(1f),
                    )
                }
                NavigableButton(
                    text = "Cancel",
                    onClick = onDismiss,
                    focusId = "multi_delete_cancel",
                    enabled = !deleting,
                )
                NavigableButton(
                    text = when {
                        guidesOnly && selected.isEmpty() -> "Delete guides and images"
                        guidesOnly -> "Delete guides and images (${selected.size})"
                        selected.isEmpty() -> "Delete"
                        else -> "Delete (${selected.size})"
                    },
                    onClick = { showConfirm = true },
                    focusId = "multi_delete_confirm",
                    enabled = !deleting && selected.isNotEmpty(),
                )
            }
        }
    }

    if (showConfirm) {
        val count = selected.size
        val noun = if (count == 1) "vault" else "vaults"
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text(if (guidesOnly) "Delete guides and images in $count $noun?" else "Delete $count $noun?") },
            text = {
                Text(
                    text = if (guidesOnly) {
                        "Every guide and image (screenshots, added images and maps) in the selected vaults " +
                            "is removed, including its file in your library folder. The vaults stay, with " +
                            "their checklists and artwork. Linked files in other folders are not affected. " +
                            "This cannot be undone."
                    } else {
                        "This also removes their files from your library folder, including guides, maps, " +
                            "checklists and artwork. Linked files in other folders are not affected. " +
                            "This cannot be undone."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            containerColor = palette.background,
            confirmButton = {
                TextButton(
                    onClick = {
                        showConfirm = false
                        if (guidesOnly) {
                            viewModel.deleteGuides(selected.toList()) { result ->
                                android.widget.Toast.makeText(
                                    context,
                                    "Deleted " + guideImageSummary(result.guides, result.images).replace(" · ", " and "),
                                    android.widget.Toast.LENGTH_SHORT,
                                ).show()
                                onDismiss()
                            }
                            return@TextButton
                        }
                        libraryViewModel.deleteVaults(selected.toList()) { deleted ->
                            android.widget.Toast.makeText(
                                context,
                                if (deleted == 1) "1 vault deleted" else "$deleted vaults deleted",
                                android.widget.Toast.LENGTH_SHORT,
                            ).show()
                            onDismiss()
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = accent),
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(
                    onClick = { showConfirm = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = accent),
                ) { Text("Cancel") }
            },
        )
    }
}
