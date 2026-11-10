package com.twinlore.app.ui.screens.checklist

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.twinlore.app.data.checklist.MarkdownChecklistParser
import com.twinlore.app.domain.ChecklistPromptTemplate
import com.twinlore.app.ui.screens.ChecklistViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val EXAMPLE_FORMAT = """# Tab Title
## Section Title
- [ ] Item
- [x] Completed item
* Another item
- Plain item

# Second tab
Item without a section becomes a section."""

private val CompactButtonPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)

/** How long the "✓ Copied" button-label swap stays on screen after a copy. */
private const val COPIED_FLASH_MS = 1_200L

/** Readable-on-both-themes green for the "items detected" confirmation line. */
private val DETECTED_GREEN = androidx.compose.ui.graphics.Color(0xFF66BB6A)

// Single copy button now — the prompt/guide split lives in the "Linked
// guides" picker above, so the click target is unified.

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChecklistPasteSheet(
    vaultName: String,
    templates: List<ChecklistPromptTemplate>,
    selectedTemplate: ChecklistPromptTemplate,
    isSelectedDefault: Boolean,
    /** All saved guides the AI can actually open (PDFs already filtered out). */
    availableGuides: List<ChecklistViewModel.GuideOption>,
    /** Ids the user has explicitly picked. Empty = quick-copy uses the most
     *  recently opened guide as a sensible default. */
    selectedGuideIds: Set<String>,
    onSelectedGuidesChange: (Set<String>) -> Unit,
    onSelectTemplate: (id: String) -> Unit,
    onSaveSelected: (name: String, content: String) -> Unit,
    onResetSelectedToDefault: () -> Unit,
    onCreateTemplate: (name: String) -> Unit,
    onDeleteSelectedTemplate: () -> Unit,
    onCopyPrompt: (substituted: String) -> Unit,
    onCopyPromptWithGuide: () -> Unit,
    onPasteFromClipboard: (text: String) -> Unit,
    onCancel: () -> Unit,
    /** When true, Step 1 (Generate with AI) starts expanded. Used by the
     *  empty-state "AI prompt" shortcut so users jump straight to the prompt. */
    initialAiStepExpanded: Boolean = false,
) {
    val context = LocalContext.current
    val clipboard = remember { context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager }
    val haptic = LocalHapticFeedback.current
    val scroll = rememberScrollState()

    // Draft state. Sync whenever the selectedTemplate object changes — that
    // catches both template switches (id changes) AND in-place updates like
    // Reset-to-default (same id, new content). Without this effect, Reset
    // would update the VM but leave the editor showing the pre-reset text.
    var contentDraft by remember { mutableStateOf(selectedTemplate.content) }
    var nameDraft by remember { mutableStateOf(selectedTemplate.name) }
    LaunchedEffect(selectedTemplate) {
        contentDraft = selectedTemplate.content
        nameDraft = selectedTemplate.name
    }
    var exampleExpanded by remember { mutableStateOf(false) }
    // Step 1 (AI generation) is optional — start collapsed unless the caller
    // asked us to open there (e.g., the empty-state "AI prompt" shortcut).
    var aiStepExpanded by remember { mutableStateOf(initialAiStepExpanded) }
    var templateDropdownExpanded by remember { mutableStateOf(false) }
    var renameDialogOpen by remember { mutableStateOf(false) }
    var resetConfirmOpen by remember { mutableStateOf(false) }
    var deleteConfirmOpen by remember { mutableStateOf(false) }
    var createDialogOpen by remember { mutableStateOf(false) }

    // "✓ Copied" button-swap flash after the user taps the copy action.
    var copiedFlash by remember { mutableStateOf(false) }
    LaunchedEffect(copiedFlash) {
        if (copiedFlash) {
            delay(COPIED_FLASH_MS)
            copiedFlash = false
        }
    }

    // Clipboard peek — we dry-run the markdown parser so the Paste button can
    // show a meaningful item count (rather than an uninformative char count)
    // and we can preview the import in a confirmation dialog. Re-read on
    // ON_RESUME (returning from an AI app) since the clipboard-change listener
    // doesn't fire reliably while our process is in the background.
    val parser = remember { MarkdownChecklistParser() }
    var clipboardText by remember { mutableStateOf(readClipboardText(clipboard, context)) }
    var pasteConfirmOpen by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, clipboard) {
        fun refresh() {
            clipboardText = readClipboardText(clipboard, context)
        }
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        val clipListener = ClipboardManager.OnPrimaryClipChangedListener { refresh() }
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
        clipboard.addPrimaryClipChangedListener(clipListener)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
            clipboard.removePrimaryClipChangedListener(clipListener)
        }
    }
    // Off-thread parse with a short debounce so a 50k-char paste doesn't
    // block the main thread, and rapid clipboard-change bursts (e.g. an
    // editor app pushing intermediate states) collapse into a single parse.
    var parsePreview by remember { mutableStateOf<PastePreviewCounts?>(null) }
    LaunchedEffect(clipboardText) {
        if (clipboardText.isBlank()) {
            parsePreview = null
        } else {
            delay(150)
            parsePreview = withContext(Dispatchers.Default) {
                parser.parse(clipboardText).summarize()
            }
        }
    }

    // File-import path — sources markdown from a SAF-picked file. Unlike the
    // clipboard path, a deliberate file pick is unambiguous, so we skip the
    // "are you sure?" confirmation dialog and import immediately. The user
    // gets the same snackbar-style ImportComplete feedback the clipboard
    // path's confirm-button also routes to.
    //
    // We launch the read/parse/import directly from the picker callback via
    // a composable-scoped coroutine, NOT via a LaunchedEffect keyed on a
    // pendingUri state. Writing back to that state inside the effect (to
    // reset it for the next pick) queues a recomposition that can cancel
    // the in-flight coroutine mid-`withContext` switch, dropping the import
    // call silently and forcing the user to re-pick.
    val sheetScope = rememberCoroutineScope()
    // Restrict to plain-text + markdown — anything else won't parse cleanly,
    // and the SAF picker filters by MIME so users don't even see binary files.
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        sheetScope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        stream.bufferedReader().readText()
                    }
                }.getOrNull().orEmpty()
            }
            if (text.isBlank()) {
                android.widget.Toast.makeText(
                    context,
                    "Couldn't read file or file is empty",
                    android.widget.Toast.LENGTH_SHORT,
                ).show()
                return@launch
            }
            val preview = withContext(Dispatchers.Default) {
                parser.parse(text).summarize()
            }
            if (preview.items == 0 && preview.sections == 0) {
                android.widget.Toast.makeText(
                    context,
                    "No checklist content found in this file",
                    android.widget.Toast.LENGTH_SHORT,
                ).show()
                return@launch
            }
            // Persist any in-flight prompt edits, then import directly — same
            // callback the clipboard confirm button uses. The VM closes the
            // sheet on success.
            onSaveSelected(nameDraft, contentDraft)
            onPasteFromClipboard(text)
        }
    }

    // Full-screen Dialog (rather than a ModalBottomSheet) so the user can't
    // swipe-dismiss it by accident mid-edit. Back button still works; outside
    // clicks don't close it.
    Dialog(
        onDismissRequest = {
            onSaveSelected(nameDraft, contentDraft)
            onCancel()
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false,
            dismissOnBackPress = true,
        ),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header row — title + explicit close affordance.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Add items",
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "Paste markdown you have, or generate one with AI.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = {
                        onSaveSelected(nameDraft, contentDraft)
                        onCancel()
                    }) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scroll)
                        .padding(horizontal = 16.dp),
                ) {
                    Spacer(Modifier.height(8.dp))

                    // ── Step 1 — Generate with AI (collapsible, optional) ──
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    ) {
                        // Header: tap anywhere on the row to expand/collapse.
                        val chevronRot by animateFloatAsState(
                            targetValue = if (aiStepExpanded) 180f else 0f,
                            label = "aiChevron",
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { aiStepExpanded = !aiStepExpanded }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Generate with AI",
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    "Optional — copy a prompt, paste the AI's reply.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Icon(
                                Icons.Default.KeyboardArrowDown,
                                contentDescription = if (aiStepExpanded) "Collapse" else "Expand",
                                modifier = Modifier.rotate(chevronRot),
                            )
                        }
                        AnimatedVisibility(
                            visible = aiStepExpanded,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut(),
                        ) {
                            AiStepBody(
                                vaultName = vaultName,
                                templates = templates,
                                selectedTemplate = selectedTemplate,
                                isSelectedDefault = isSelectedDefault,
                                availableGuides = availableGuides,
                                selectedGuideIds = selectedGuideIds,
                                onSelectedGuidesChange = onSelectedGuidesChange,
                                contentDraft = contentDraft,
                                copiedFlash = copiedFlash,
                                templateDropdownExpanded = templateDropdownExpanded,
                                onDropdownExpandedChange = { templateDropdownExpanded = it },
                                onSelectTemplate = onSelectTemplate,
                                onContentChange = { contentDraft = it },
                                onRename = { renameDialogOpen = true },
                                onReset = { resetConfirmOpen = true },
                                onDelete = { deleteConfirmOpen = true },
                                onCreateNew = { createDialogOpen = true },
                                onCopyTap = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onSaveSelected(nameDraft, contentDraft)
                                    // Selection drives the variant: empty
                                    // selection = plain prompt, otherwise the
                                    // VM appends the picked guide URLs. Either
                                    // way the user sees one consistent button.
                                    if (selectedGuideIds.isEmpty()) {
                                        val substituted = contentDraft.replace("{vault}", vaultName)
                                        onCopyPrompt(substituted)
                                    } else {
                                        onCopyPromptWithGuide()
                                    }
                                    copiedFlash = true
                                },
                                onOpenExternal = { url -> openExternal(context, url) },
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // ── Step 2 — Paste your text (always open, the primary path) ──
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    ) {
                        Column(Modifier.padding(horizontal = 14.dp, vertical = 14.dp)) {
                            Text("Paste your text", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(10.dp))

                            val itemCount = parsePreview?.items ?: 0
                            val sectionCount = parsePreview?.sections ?: 0
                            val canPaste = itemCount > 0 || sectionCount > 0
                            val totalCount = itemCount + sectionCount
                            val buttonLabel = when {
                                !canPaste -> "Paste from clipboard"
                                totalCount == 1 -> "Import 1 item"
                                else -> "Import $totalCount items"
                            }
                            Button(
                                onClick = {
                                    onSaveSelected(nameDraft, contentDraft)
                                    pasteConfirmOpen = true
                                },
                                enabled = canPaste,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                ),
                            ) {
                                Text(buttonLabel)
                            }
                            // Read-from-file alternative — same destination as
                            // Paste, just sourced from a SAF-picked file.
                            // Restrict to .txt / .md MIME types so users can't
                            // accidentally pick a binary file that'd parse to
                            // garbage.
                            Spacer(Modifier.height(6.dp))
                            OutlinedButton(
                                onClick = {
                                    onSaveSelected(nameDraft, contentDraft)
                                    filePicker.launch(arrayOf("text/plain", "text/markdown"))
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(
                                    Icons.Default.FileOpen,
                                    contentDescription = null,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Read .md or .txt file")
                            }
                            // Status line under the button — green when the
                            // parser found importable content so users get a
                            // clear "yes, this will work" cue.
                            Spacer(Modifier.height(4.dp))
                            val preview = parsePreview
                            val hasDetections = preview != null && (preview.items > 0 || preview.sections > 0)
                            val status: String = when {
                                clipboardText.isBlank() -> "Clipboard empty"
                                !hasDetections ->
                                    "Nothing importable detected in clipboard"
                                else -> preview!!.describeShort()
                            }
                            Text(
                                text = status,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (hasDetections) DETECTED_GREEN
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )

                            Spacer(Modifier.height(10.dp))

                            // Collapsible expected-format reference — right next to the button it describes.
                            val chevronRot by animateFloatAsState(
                                targetValue = if (exampleExpanded) 180f else 0f,
                                label = "exampleChevron",
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { exampleExpanded = !exampleExpanded },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "Expected format",
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.weight(1f),
                                )
                                Icon(
                                    Icons.Default.KeyboardArrowDown,
                                    contentDescription = if (exampleExpanded) "Collapse example" else "Expand example",
                                    modifier = Modifier.rotate(chevronRot),
                                )
                            }
                            AnimatedVisibility(
                                visible = exampleExpanded,
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut(),
                            ) {
                                Column {
                                    Spacer(Modifier.height(4.dp))
                                    // Example shown as a read-only text field so the content is
                                    // selectable + copyable directly. Tapping "Copy example"
                                    // pushes it to the clipboard, which the paste sheet's
                                    // clipboard observer picks up instantly — the Paste button
                                    // above flips to "X items detected" in green so users can
                                    // see the format works before using their own text.
                                    OutlinedTextField(
                                        value = EXAMPLE_FORMAT,
                                        onValueChange = {},
                                        readOnly = true,
                                        label = { Text("Example") },
                                        textStyle = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                        ),
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    var exampleCopied by remember { mutableStateOf(false) }
                                    LaunchedEffect(exampleCopied) {
                                        if (exampleCopied) {
                                            delay(COPIED_FLASH_MS)
                                            exampleCopied = false
                                        }
                                    }
                                    // The Paste button's status line above already
                                    // reports the detected counts once the clipboard
                                    // picks this up, so just offer the copy action.
                                    TextButton(
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            clipboard.setPrimaryClip(
                                                android.content.ClipData.newPlainText(
                                                    "Checklist example",
                                                    EXAMPLE_FORMAT,
                                                ),
                                            )
                                            exampleCopied = true
                                        },
                                        contentPadding = CompactButtonPadding,
                                    ) {
                                        Icon(
                                            if (exampleCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                                            contentDescription = null,
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(if (exampleCopied) "Copied" else "Copy example")
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    val legendColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    val legendStyle = MaterialTheme.typography.labelSmall
                                    Text("•  # — tab heading", style = legendStyle, color = legendColor)
                                    Text("•  ## — section heading", style = legendStyle, color = legendColor)
                                    Text("•  - [ ] / - [x] — item (checkbox state respected)", style = legendStyle, color = legendColor)
                                    Text("•  * or - — item (unchecked)", style = legendStyle, color = legendColor)
                                    Text(
                                        "•  Lines without markers become sections, or items if a ## was just defined.",
                                        style = legendStyle,
                                        color = legendColor,
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }

    // ── Dialogs ─────────────────────────────────────────────────────────────

    if (renameDialogOpen) {
        AlertDialog(
            // Back / outside-tap dismiss reverts the draft so a half-typed
            // name doesn't get auto-saved when the sheet closes later.
            onDismissRequest = {
                nameDraft = selectedTemplate.name
                renameDialogOpen = false
            },
            title = { Text("Rename template") },
            text = {
                OutlinedTextField(
                    value = nameDraft,
                    onValueChange = { nameDraft = it.take(60) },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onSaveSelected(nameDraft, contentDraft)
                    renameDialogOpen = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = {
                    nameDraft = selectedTemplate.name
                    renameDialogOpen = false
                }) { Text("Cancel") }
            },
        )
    }

    if (resetConfirmOpen) {
        AlertDialog(
            onDismissRequest = { resetConfirmOpen = false },
            title = { Text("Reset ${selectedTemplate.name}?") },
            text = {
                Text(
                    "Your edits to this template will be replaced with the " +
                        "built-in default. You can still edit it again after.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onResetSelectedToDefault()
                    resetConfirmOpen = false
                }) { Text("Reset") }
            },
            dismissButton = {
                TextButton(onClick = { resetConfirmOpen = false }) { Text("Cancel") }
            },
        )
    }

    if (deleteConfirmOpen) {
        AlertDialog(
            onDismissRequest = { deleteConfirmOpen = false },
            title = { Text("Delete ${selectedTemplate.name}?") },
            text = { Text("This custom template will be removed. The built-in templates stay.") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteSelectedTemplate()
                    deleteConfirmOpen = false
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmOpen = false }) { Text("Cancel") }
            },
        )
    }

    val confirmPreview = parsePreview
    if (pasteConfirmOpen && confirmPreview != null) {
        AlertDialog(
            onDismissRequest = { pasteConfirmOpen = false },
            title = { Text("Import this checklist?") },
            text = {
                Text(
                    "This will add:\n\n" + confirmPreview.describeLong() +
                        "\n\nExisting items in this vault won't be touched.",
                )
            },
            confirmButton = {
                Button(onClick = {
                    pasteConfirmOpen = false
                    onPasteFromClipboard(clipboardText)
                }) { Text("Import") }
            },
            dismissButton = {
                TextButton(onClick = { pasteConfirmOpen = false }) { Text("Cancel") }
            },
        )
    }

    if (createDialogOpen) {
        var newName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { createDialogOpen = false },
            title = { Text("New template") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it.take(60) },
                    label = { Text("Template name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = newName.isNotBlank(),
                    onClick = {
                        onCreateTemplate(newName.trim())
                        createDialogOpen = false
                    },
                ) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { createDialogOpen = false }) { Text("Cancel") }
            },
        )
    }
}

/**
 * Body of the "Generate with AI" card — extracted so the outer Dialog stays
 * readable. Renders the template picker row, the prompt editor with its
 * `{vault}` preview, and the two action rows (clipboard actions +
 * external-AI-site launchers).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AiStepBody(
    vaultName: String,
    templates: List<ChecklistPromptTemplate>,
    selectedTemplate: ChecklistPromptTemplate,
    isSelectedDefault: Boolean,
    availableGuides: List<ChecklistViewModel.GuideOption>,
    selectedGuideIds: Set<String>,
    onSelectedGuidesChange: (Set<String>) -> Unit,
    contentDraft: String,
    copiedFlash: Boolean,
    templateDropdownExpanded: Boolean,
    onDropdownExpandedChange: (Boolean) -> Unit,
    onSelectTemplate: (String) -> Unit,
    onContentChange: (String) -> Unit,
    onRename: () -> Unit,
    onReset: () -> Unit,
    onDelete: () -> Unit,
    onCreateNew: () -> Unit,
    onCopyTap: () -> Unit,
    onOpenExternal: (String) -> Unit,
) {
    Column(Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
        // Template picker row — dropdown, rename, and reset/delete.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ExposedDropdownMenuBox(
                expanded = templateDropdownExpanded,
                onExpandedChange = onDropdownExpandedChange,
                modifier = Modifier.weight(1f),
            ) {
                OutlinedTextField(
                    value = selectedTemplate.name,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Template") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(
                            expanded = templateDropdownExpanded,
                        )
                    },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyMedium,
                )
                androidx.compose.material3.DropdownMenu(
                    expanded = templateDropdownExpanded,
                    onDismissRequest = { onDropdownExpandedChange(false) },
                ) {
                    templates.forEach { tmpl ->
                        DropdownMenuItem(
                            text = { Text(tmpl.name) },
                            onClick = {
                                onSelectTemplate(tmpl.id)
                                onDropdownExpandedChange(false)
                            },
                        )
                    }
                    androidx.compose.material3.Divider()
                    DropdownMenuItem(
                        text = { Text("New template…") },
                        leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                        onClick = {
                            onDropdownExpandedChange(false)
                            onCreateNew()
                        },
                    )
                }
            }
            // Single overflow kebab consolidates rename + reset/delete so the
            // template-management chrome doesn't crowd the dropdown selector.
            var templateMenuOpen by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { templateMenuOpen = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Template actions")
                }
                androidx.compose.material3.DropdownMenu(
                    expanded = templateMenuOpen,
                    onDismissRequest = { templateMenuOpen = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                        onClick = {
                            templateMenuOpen = false
                            onRename()
                        },
                    )
                    if (isSelectedDefault) {
                        DropdownMenuItem(
                            text = { Text("Reset to default") },
                            leadingIcon = { Icon(Icons.Default.Restore, contentDescription = null) },
                            onClick = {
                                templateMenuOpen = false
                                onReset()
                            },
                        )
                    } else {
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                            onClick = {
                                templateMenuOpen = false
                                onDelete()
                            },
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // Prompt editor. The trailing edit glyph is a small affordance —
        // OutlinedTextField looks like a read-only display by default, so
        // hinting "you can write here" reduces the wait-and-see moment.
        OutlinedTextField(
            value = contentDraft,
            onValueChange = onContentChange,
            label = { Text("AI prompt — tap to edit") },
            trailingIcon = {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 140.dp, max = 240.dp),
            textStyle = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            buildAnnotatedString {
                append("Preview: ")
                withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) { append("{vault}") }
                append(" becomes ")
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(vaultName) }
                append(". Edits auto-save.")
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(16.dp))

        // ── Linked guides — picker over saved web guides (PDFs filtered out). ──
        // Empty selection means "no guide attached"; this is the explicit
        // signal that drives the single Copy button below.
        var guidePickerOpen by remember { mutableStateOf(false) }
        val picked: List<ChecklistViewModel.GuideOption> =
            availableGuides.filter { it.id in selectedGuideIds }
        GuideSelectionRow(
            availableGuides = availableGuides,
            picked = picked,
            onOpenPicker = { guidePickerOpen = true },
            onClear = { onSelectedGuidesChange(emptySet()) },
        )
        if (guidePickerOpen) {
            GuideMultiSelectDialog(
                allGuides = availableGuides,
                initiallySelected = selectedGuideIds,
                onConfirm = { ids ->
                    onSelectedGuidesChange(ids)
                    guidePickerOpen = false
                },
                onDismiss = { guidePickerOpen = false },
            )
        }

        Spacer(Modifier.height(16.dp))

        // ── Single copy button — label folds the guide count in, so the user
        // doesn't need a separate "+ Guide" button to predict what'll land
        // on the clipboard.
        val copyLabel = when {
            copiedFlash -> "Copied"
            picked.size > 1 -> "Copy prompt + ${picked.size} guides"
            picked.size == 1 -> "Copy prompt + guide"
            else -> "Copy prompt"
        }
        FilledTonalButton(
            onClick = onCopyTap,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Icon(
                if (copiedFlash) Icons.Default.Check else Icons.Default.ContentCopy,
                contentDescription = null,
            )
            Spacer(Modifier.width(8.dp))
            Text(copyLabel)
        }

        Spacer(Modifier.height(16.dp))

        // ── External AI launchers — kept as a thin secondary row. ──
        Text(
            "Open AI in browser",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ExternalAiButton("ChatGPT") { onOpenExternal("https://chat.openai.com/") }
            ExternalAiButton("Gemini") { onOpenExternal("https://gemini.google.com/") }
            ExternalAiButton("Claude") { onOpenExternal("https://claude.ai/new") }
        }
        Spacer(Modifier.height(12.dp))
    }
}

/**
 * Tappable row showing what guides are attached to the prompt.
 *
 * Three visual states:
 *  - **No guides saved** — disabled, hint copy explains how to add one.
 *  - **Nothing picked** — call-to-action ("Attach a guide") so the user knows
 *    the picker is here. Empty selection = plain prompt copy.
 *  - **Picks present** — title + summary; small `×` clears the selection
 *    without re-opening the dialog.
 */
@Composable
private fun GuideSelectionRow(
    availableGuides: List<ChecklistViewModel.GuideOption>,
    picked: List<ChecklistViewModel.GuideOption>,
    onOpenPicker: () -> Unit,
    onClear: () -> Unit,
) {
    val empty = availableGuides.isEmpty()
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !empty) { onOpenPicker() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Game guide reference",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(2.dp))
                when {
                    empty -> Text(
                        text = "No web guides saved — open one in the Guide tab. PDFs aren't supported.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    )
                    picked.isEmpty() -> Text(
                        text = "Attach a guide to let the AI tailor the checklist.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    else -> Text(
                        text = picked.joinToString(" · ") { it.title },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            if (picked.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Clear attached guides",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else if (!empty) {
                TextButton(onClick = onOpenPicker, contentPadding = CompactButtonPadding) {
                    Text("Choose")
                }
            }
        }
    }
}

/**
 * Multi-select dialog over [allGuides]. Confirm returns the picked id set —
 * empty means "no explicit pick", which the VM treats as "use the most
 * recently opened guide" (so the quick-copy path keeps working without forcing
 * the user to interact with this dialog every time).
 */
@Composable
private fun GuideMultiSelectDialog(
    allGuides: List<ChecklistViewModel.GuideOption>,
    initiallySelected: Set<String>,
    onConfirm: (Set<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember { mutableStateOf(initiallySelected) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Attach guides") },
        text = {
            Column(modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                Text(
                    "Pick which saved guides the AI should consult. The references " +
                        "are added to the prompt as URLs — works only with AI tools that can browse the web.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                allGuides.forEach { guide ->
                    val checked = guide.id in draft
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                draft = if (checked) draft - guide.id else draft + guide.id
                            }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        androidx.compose.material3.Checkbox(
                            checked = checked,
                            onCheckedChange = {
                                draft = if (checked) draft - guide.id else draft + guide.id
                            },
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                guide.title,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                            )
                            Text(
                                guide.url,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(draft) }) { Text("Use selection") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun ExternalAiButton(label: String, onClick: () -> Unit) {
    // OutlinedButton (vs the previous TextButton) reads as a tappable
    // control rather than an inline link — important since these leave the
    // app and we want users to commit deliberately.
    OutlinedButton(onClick = onClick, contentPadding = CompactButtonPadding) {
        Text(label)
        Spacer(Modifier.width(4.dp))
        Icon(
            Icons.Default.OpenInNew,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
        )
    }
}

private fun readClipboardText(clipboard: ClipboardManager, context: Context): String {
    val item = clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)
    return item?.coerceToText(context)?.toString().orEmpty()
}

/** Compact counts derived from a dry-run markdown parse — used for the Paste
 *  button's status line and the confirmation dialog. */
private data class PastePreviewCounts(val tabs: Int, val sections: Int, val items: Int) {
    fun describeShort(): String {
        val parts = buildList {
            if (tabs > 1) add("$tabs tabs")
            add(plural(sections, "section", "sections"))
            add(plural(items, "item", "items"))
        }
        return parts.joinToString(" · ") + " detected"
    }

    fun describeLong(): String {
        val lines = buildList {
            if (tabs > 1) add("• ${plural(tabs, "tab", "tabs")}")
            add("• ${plural(sections, "section", "sections")}")
            add("• ${plural(items, "item", "items")}")
        }
        return lines.joinToString("\n")
    }

    private fun plural(n: Int, singular: String, plural: String): String =
        "$n ${if (n == 1) singular else plural}"
}

private fun MarkdownChecklistParser.ParsedChecklist.summarize(): PastePreviewCounts {
    var sections = 0
    var items = 0
    val tabCount = tabs.size
    for (t in tabs) {
        sections += t.sections.size
        for (s in t.sections) items += s.items.size
    }
    return PastePreviewCounts(tabs = tabCount, sections = sections, items = items)
}

private fun openExternal(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
}
