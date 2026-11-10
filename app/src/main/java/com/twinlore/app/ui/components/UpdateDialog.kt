package com.twinlore.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.twinlore.app.data.update.UpdateState
import java.io.File

@Composable
fun UpdateDialog(
    updateState: UpdateState,
    onDownload: (downloadUrl: String, version: String) -> Unit,
    onInstall: (File) -> Unit,
    onDismiss: (version: String) -> Unit,
    onRetry: () -> Unit
) {
    when (updateState) {
        is UpdateState.Available -> {
            AlertDialog(
                onDismissRequest = { onDismiss(updateState.version) },
                title = {
                    Text("Update available: v${updateState.version}")
                },
                text = {
                    if (!updateState.releaseNotes.isNullOrBlank()) {
                        val blocks = remember(updateState.releaseNotes) {
                            splitReleaseNotesBlocks(updateState.releaseNotes)
                        }
                        Column(
                            modifier = Modifier
                                .heightIn(max = 300.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            blocks.forEach { block ->
                                when (block) {
                                    is ReleaseNotesBlock.Md -> {
                                        val styled = remember(block.markdown) { parseMarkdown(block.markdown) }
                                        if (styled.text.isNotBlank()) {
                                            Text(
                                                text = styled,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        }
                                    }
                                    is ReleaseNotesBlock.Image -> {
                                        AsyncImage(
                                            model = block.url,
                                            contentDescription = block.alt.ifBlank { null },
                                            contentScale = ContentScale.FillWidth,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { onDownload(updateState.downloadUrl, updateState.version) }) {
                        Text("Download")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { onDismiss(updateState.version) }) {
                        Text("Later")
                    }
                }
            )
        }

        is UpdateState.Downloading -> {
            AlertDialog(
                onDismissRequest = onRetry,
                title = { Text("Downloading update…") },
                text = {
                    Column {
                        Text(
                            text = "${(updateState.progress * 100).toInt()}%",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        LinearProgressIndicator(
                            progress = updateState.progress,
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                },
                confirmButton = { }
            )
        }

        is UpdateState.ReadyToInstall -> {
            AlertDialog(
                onDismissRequest = onRetry,
                title = { Text("Update ready") },
                text = { Text("The update has been downloaded and is ready to install.") },
                confirmButton = {
                    TextButton(onClick = { onInstall(updateState.file) }) {
                        Text("Install")
                    }
                },
                dismissButton = {
                    TextButton(onClick = onRetry) {
                        Text("Later")
                    }
                }
            )
        }

        is UpdateState.Error -> {
            AlertDialog(
                onDismissRequest = { onRetry() },
                title = { Text("Update error") },
                text = { Text(updateState.message) },
                confirmButton = {
                    TextButton(onClick = onRetry) {
                        Text("Retry")
                    }
                },
                dismissButton = {
                    TextButton(onClick = onRetry) {
                        Text("Dismiss")
                    }
                }
            )
        }

        else -> { /* Idle, Checking — no dialog */ }
    }
}

private sealed interface ReleaseNotesBlock {
    data class Md(val markdown: String) : ReleaseNotesBlock
    data class Image(val url: String, val alt: String) : ReleaseNotesBlock
}

private val MD_IMAGE_LINE = Regex("""^\s*!\[([^\]]*)\]\((https?://\S+?)\)\s*$""")
private val HTML_IMG_LINE = Regex("""^\s*<img\b[^>]*\bsrc=["'](https?://[^"']+)["'][^>]*>\s*$""", RegexOption.IGNORE_CASE)

private fun splitReleaseNotesBlocks(markdown: String): List<ReleaseNotesBlock> {
    val blocks = mutableListOf<ReleaseNotesBlock>()
    val current = StringBuilder()
    fun flushText() {
        if (current.isNotEmpty()) {
            blocks += ReleaseNotesBlock.Md(current.toString())
            current.clear()
        }
    }
    for (line in markdown.lines()) {
        val mdImg = MD_IMAGE_LINE.matchEntire(line)
        val htmlImg = if (mdImg == null) HTML_IMG_LINE.matchEntire(line) else null
        when {
            mdImg != null -> {
                flushText()
                blocks += ReleaseNotesBlock.Image(url = mdImg.groupValues[2], alt = mdImg.groupValues[1])
            }
            htmlImg != null -> {
                flushText()
                blocks += ReleaseNotesBlock.Image(url = htmlImg.groupValues[1], alt = "")
            }
            else -> current.appendLine(line)
        }
    }
    flushText()
    return blocks
}

private fun parseMarkdown(markdown: String): AnnotatedString = buildAnnotatedString {
    val lines = markdown.lines()
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trimStart()

        when {
            // Headers
            trimmed.startsWith("### ") -> {
                if (length > 0) append("\n")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 14.sp)) {
                    appendInline(trimmed.removePrefix("### "))
                }
                append("\n")
            }
            trimmed.startsWith("## ") -> {
                if (length > 0) append("\n")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp)) {
                    appendInline(trimmed.removePrefix("## "))
                }
                append("\n")
            }
            trimmed.startsWith("# ") -> {
                if (length > 0) append("\n")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 18.sp)) {
                    appendInline(trimmed.removePrefix("# "))
                }
                append("\n")
            }
            // Bullet items
            trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                if (length > 0 && !endsWith("\n")) append("\n")
                append("  •  ")
                appendInline(trimmed.drop(2))
                append("\n")
            }
            // Blank line
            trimmed.isBlank() -> {
                if (length > 0 && !endsWith("\n\n")) append("\n")
            }
            // Regular text
            else -> {
                if (length > 0 && !endsWith("\n")) append("\n")
                appendInline(trimmed)
                append("\n")
            }
        }
        i++
    }
    // Trim trailing newlines
    val text = toAnnotatedString()
    if (text.text.endsWith("\n")) {
        // Can't trim in-place, but trailing whitespace is fine for display
    }
}

private fun AnnotatedString.Builder.appendInline(text: String) {
    var pos = 0
    while (pos < text.length) {
        when {
            // Bold: **text**
            text.startsWith("**", pos) -> {
                val end = text.indexOf("**", pos + 2)
                if (end != -1) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        appendInline(text.substring(pos + 2, end))
                    }
                    pos = end + 2
                } else {
                    append("**")
                    pos += 2
                }
            }
            // Italic: *text* (but not **)
            text[pos] == '*' && (pos + 1 >= text.length || text[pos + 1] != '*') -> {
                val end = text.indexOf('*', pos + 1)
                if (end != -1) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        appendInline(text.substring(pos + 1, end))
                    }
                    pos = end + 1
                } else {
                    append('*')
                    pos++
                }
            }
            // Inline code: `text`
            text[pos] == '`' -> {
                val end = text.indexOf('`', pos + 1)
                if (end != -1) {
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) {
                        append(text.substring(pos + 1, end))
                    }
                    pos = end + 1
                } else {
                    append('`')
                    pos++
                }
            }
            else -> {
                append(text[pos])
                pos++
            }
        }
    }
}

private fun AnnotatedString.Builder.endsWith(suffix: String): Boolean {
    val currentLength = length
    if (currentLength < suffix.length) return false
    val built = toAnnotatedString().text
    return built.endsWith(suffix)
}
