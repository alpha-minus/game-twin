package com.twinlore.app.ui.screens.guide

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.content.res.Configuration
import com.twinlore.app.ui.navigation.NavigableIconButton

/**
 * 6-icon floating top bar for the single-guide viewer:
 *   [📂 Files]  N/Total  Chapter label    [<][>][≡][🔍][🎨]
 *
 * Mirrors `PageGroupTopBar`. Custom Row (not Material3 TopAppBar) so the
 * bar is ~40dp tall instead of 64dp — Material3's small TopAppBar
 * reserves vertical space for an optional second line of text, leaving a
 * visible gap between this bar and the vault-session top bar above it.
 */
@Composable
internal fun SingleGuideTopBar(
    bookmarkTitle: String,
    activeChapterLabel: String?,
    activeChapterIndex: Int?,
    totalChapters: Int,
    canPrev: Boolean,
    canNext: Boolean,
    onBack: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onTocClicked: () -> Unit,
    onFindClicked: () -> Unit,
    onDisplayClicked: () -> Unit,
    /** False where there is nothing behind the viewer to go back to. */
    showBack: Boolean = true,
    /** False where the display sheet cannot open, so the button is not shown. */
    showDisplay: Boolean = true,
    // D-pad UP from the vault-session bottom bar targets this requester so focus
    // lands on the first top-bar button; null outside the vault session.
    firstButtonFocusRequester: FocusRequester? = null,
    // D-pad DOWN from anywhere in the top bar returns focus here (the bottom bar)
    // so the body WebView — which has no Compose focusable — can't trap focus.
    bottomBarFocusRequester: FocusRequester? = null
) {
    // Match the browser/picker top bar (GuideBrowserTopBar) icon metrics so the
    // focus ring looks identical across guide surfaces.
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val iconSize = if (isLandscape) 24.dp else 20.dp
    val iconPadding = 4.dp
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 40.dp)
                .padding(horizontal = 4.dp)
                .focusProperties {
                    down = bottomBarFocusRequester ?: FocusRequester.Default
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showBack) {
                NavigableIconButton(
                    imageVector = Icons.Default.FolderOpen,
                    contentDescription = "Files",
                    onClick = onBack,
                    focusId = "single_guide_files",
                    iconSize = iconSize,
                    contentPadding = iconPadding,
                    externalFocusRequester = firstButtonFocusRequester
                )
            }
            val label = activeChapterLabel?.ifBlank { null } ?: bookmarkTitle.ifBlank { "Guide" }
            val prefix = if (activeChapterIndex != null && totalChapters > 0)
                "${activeChapterIndex + 1}/$totalChapters  " else ""
            Text(
                text = prefix + label,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    // A tap on the chapter opens the contents, the same as the
                    // list button. Touch only: D-pad already has that button,
                    // and a focus stop on a label would only get in the way.
                    .pointerInput(onTocClicked) { detectTapGestures { onTocClicked() } }
                    .padding(horizontal = 8.dp)
            )
            Row(
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                NavigableIconButton(
                    imageVector = Icons.Filled.KeyboardArrowLeft,
                    contentDescription = "Previous chapter",
                    onClick = onPrev,
                    enabled = canPrev,
                    focusId = "single_guide_prev",
                    iconSize = iconSize,
                    contentPadding = iconPadding
                )
                NavigableIconButton(
                    imageVector = Icons.Filled.KeyboardArrowRight,
                    contentDescription = "Next chapter",
                    onClick = onNext,
                    enabled = canNext,
                    focusId = "single_guide_next",
                    iconSize = iconSize,
                    contentPadding = iconPadding
                )
                NavigableIconButton(
                    imageVector = Icons.Filled.Menu,
                    contentDescription = "Contents",
                    onClick = onTocClicked,
                    focusId = "single_guide_toc",
                    iconSize = iconSize,
                    contentPadding = iconPadding
                )
                NavigableIconButton(
                    imageVector = Icons.Filled.Search,
                    contentDescription = "Find in guide",
                    onClick = onFindClicked,
                    focusId = "single_guide_find",
                    iconSize = iconSize,
                    contentPadding = iconPadding
                )
                if (showDisplay) {
                    NavigableIconButton(
                        imageVector = Icons.Default.Palette,
                        contentDescription = "Display settings",
                        onClick = onDisplayClicked,
                        focusId = "single_guide_display",
                        iconSize = iconSize,
                        contentPadding = iconPadding
                    )
                }
            }
        }
    }
}
