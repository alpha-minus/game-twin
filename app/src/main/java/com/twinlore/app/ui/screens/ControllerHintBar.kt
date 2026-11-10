package com.twinlore.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material.icons.filled.ViewDay
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import com.twinlore.app.data.ButtonGlyphStyle
import com.twinlore.app.data.LibraryViewMode
import com.twinlore.app.domain.LibrarySortField
import com.twinlore.app.domain.SortDirection
import com.twinlore.app.ui.LocalThemePalette

/**
 * Total vertical footprint of the bottom hint bar in landscape, including
 * its own internal padding. Library content composables (vault grid,
 * launcher grid, carousel) reserve this much space at the bottom of
 * their layout so the hint bar overlay doesn't clip the last row.
 *
 * Hardcoded vs measured (`onSizeChanged`) because the hint bar is
 * landscape-only with a stable Row layout — actual rendered height
 * is ≈42dp (Row vertical padding 6dp+6dp + pill row ≈30dp). The 48dp
 * constant adds a small safety margin and is conservative enough that
 * a future glyph/font tweak won't suddenly clip again. Nav-bar inset
 * is handled by `windowInsetsPadding` on the bar itself; the constant
 * doesn't need to include it.
 */
val ControllerHintBarReservedHeight = 48.dp

/**
 * Per-view-mode glyph for the L1 view-mode chip + its dropdown rows.
 * Mapping restored from the deleted top-bar cycle button at commit
 * `1c2bb75d~1`, with the `LAUNCHER → ViewDay` correction from `bc3acfdd`.
 */
private fun LibraryViewMode.icon(): ImageVector = when (this) {
    LibraryViewMode.CAROUSEL -> Icons.Filled.ViewCarousel
    LibraryViewMode.LIST -> Icons.Filled.ViewList
    LibraryViewMode.GRID_EXTRA_LARGE -> Icons.Filled.GridView
    LibraryViewMode.GRID_MEDIUM -> Icons.Filled.ViewModule
    LibraryViewMode.GRID_SMALL -> Icons.Filled.Apps
    LibraryViewMode.LAUNCHER -> Icons.Filled.ViewDay
}

/** Per-sort-field glyph for the R1 sort chip + its dropdown rows. */
private fun LibrarySortField.icon(): ImageVector = when (this) {
    LibrarySortField.NAME -> Icons.Filled.SortByAlpha
    LibrarySortField.DATE_ADDED -> Icons.Filled.Schedule
    LibrarySortField.LAST_OPENED -> Icons.Filled.History
    LibrarySortField.ACHIEVEMENTS -> Icons.Filled.EmojiEvents
}

private fun SortDirection.icon(): ImageVector =
    if (this == SortDirection.ASC) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward

/**
 * Bottom-anchored on-screen control bar for the library views (carousel / grid / launcher).
 *
 * Landscape (controller layout): a full hint bar.
 *   Left cluster (pills): Settings, Refresh, L1 = view mode, R1 = sort.
 *   Right cluster (circles): Y = Edit, A = Open.
 *
 * Portrait (touch layout): the controller bar is dropped entirely; only the view-mode and
 * sort pills remain, as icon chips with no bumper/face-button glyphs — nothing controller
 * related is shown.
 *
 * Items are touch-tappable but not focusable by the controller.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ControllerHintBar(
    libraryViewMode: LibraryViewMode,
    librarySortField: LibrarySortField,
    librarySortDirection: SortDirection,
    isScraping: Boolean,
    onSettings: () -> Unit,
    onRefresh: () -> Unit,
    onEdit: () -> Unit,
    onOpen: () -> Unit,
    onViewModeSelected: (LibraryViewMode) -> Unit,
    onSortFieldSelected: (LibrarySortField) -> Unit,
    onSortDirectionToggle: () -> Unit,
    buttonGlyphStyle: ButtonGlyphStyle = ButtonGlyphStyle.XBOX,
    swapFaceButtons: Boolean = false,
    /** Touch-only jump back to the top of the page. Absent in carousel, which has no scroll. */
    showBackToTop: Boolean = false,
    onBackToTop: () -> Unit = {},
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp

    // Library views only — defensive (caller already gates, but cheap to enforce).
    val isLibrary = libraryViewMode == LibraryViewMode.CAROUSEL ||
        libraryViewMode == LibraryViewMode.LIST ||
        libraryViewMode == LibraryViewMode.GRID_EXTRA_LARGE ||
        libraryViewMode == LibraryViewMode.GRID_MEDIUM ||
        libraryViewMode == LibraryViewMode.GRID_SMALL ||
        libraryViewMode == LibraryViewMode.LAUNCHER
    if (!isLibrary) return

    val palette = LocalThemePalette.current
    val fillColor = palette.background.copy(alpha = 0.85f)
    val borderColor = palette.foreground.copy(alpha = 0.5f)
    val textColor = palette.foreground
    val dimmedFill = palette.background.copy(alpha = 0.55f)
    val dimmedText = palette.foreground.copy(alpha = 0.45f)
    val dimmedBorder = palette.foreground.copy(alpha = 0.25f)
    val accentColor = palette.foreground

    if (!isLandscape) {
        // Portrait: no controller chrome. Just the view + sort pills, floated bottom-end.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility)
                .padding(horizontal = 12.dp, vertical = 8.dp)
                // Collapse the whole status bar (pills + their dropdowns' anchor row) to one
                // property-less semantics node. Nothing here is controller-focusable (docstring
                // above: "touch-tappable but not focusable by the controller"), so there is no
                // focus/key modifier on this subtree to disturb. See VaultGridItem's tile-level
                // clearAndSetSemantics for the same pattern.
                .clearAndSetSemantics {},
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Placed first, not appended, because this row is end-aligned: an appended item
            // pushes everything before it leftward when it appears (and back when it
            // disappears). Placing the pill first keeps the two selectors pinned to the end and
            // the pill simply appears to their left without moving them.
            if (showBackToTop) {
                HintPill(
                    glyph = Icons.Filled.KeyboardArrowUp,
                    label = "Top",
                    textColor = textColor,
                    borderColor = borderColor,
                    fillColor = fillColor,
                    onClick = onBackToTop,
                    large = true,
                )
            }
            HintViewModeSelector(
                bumperLabel = null,
                currentMode = libraryViewMode,
                onSelected = onViewModeSelected,
                textColor = textColor, borderColor = borderColor, fillColor = fillColor,
                accentColor = accentColor,
                large = true,
            )
            HintSortSelector(
                bumperLabel = null,
                currentField = librarySortField,
                currentDirection = librarySortDirection,
                onFieldSelected = onSortFieldSelected,
                onDirectionToggle = onSortDirectionToggle,
                textColor = textColor, borderColor = borderColor, fillColor = fillColor,
                accentColor = accentColor,
                large = true,
            )
        }
        return
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        palette.background.copy(alpha = 0.6f),
                        palette.background.copy(alpha = 0.85f)
                    )
                )
            )
            .windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility)
            .padding(horizontal = 12.dp, vertical = 6.dp)
            // Collapse the whole hint bar (Settings/Refresh/L1/R1 pills + Y/A face buttons) to
            // one property-less semantics node. Nothing here is controller-focusable (docstring
            // above: "touch-tappable but not focusable by the controller"), so there is no
            // focus/key modifier on this subtree to disturb. See VaultGridItem's tile-level
            // clearAndSetSemantics for the same pattern.
            .clearAndSetSemantics {},
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left cluster: pills + L1 view selector + R1 sort selector
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            HintPill(
                glyph = Icons.Filled.Menu,
                label = "Settings",
                textColor = textColor,
                borderColor = borderColor,
                fillColor = fillColor,
                onClick = onSettings
            )
            HintPill(
                glyph = Icons.Filled.PlayArrow,
                label = "Refresh",
                textColor = if (isScraping) dimmedText else textColor,
                borderColor = if (isScraping) dimmedBorder else borderColor,
                fillColor = if (isScraping) dimmedFill else fillColor,
                onClick = if (isScraping) ({}) else onRefresh
            )
            HintViewModeSelector(
                bumperLabel = "L1",
                currentMode = libraryViewMode,
                onSelected = onViewModeSelected,
                textColor = textColor, borderColor = borderColor, fillColor = fillColor,
                accentColor = accentColor,
            )
            HintSortSelector(
                bumperLabel = "R1",
                currentField = librarySortField,
                currentDirection = librarySortDirection,
                onFieldSelected = onSortFieldSelected,
                onDirectionToggle = onSortDirectionToggle,
                textColor = textColor, borderColor = borderColor, fillColor = fillColor,
                accentColor = accentColor,
            )
            // Appended, not prepended: this cluster is start-packed (left-aligned) inside a
            // SpaceBetween row, so an appended item extends the row rightward without moving any
            // of the pills or selectors before it. Touch-only: HintPill is a tap target,
            // not a focus target, same as every other pill in this legend.
            if (showBackToTop) {
                HintPill(
                    glyph = Icons.Filled.KeyboardArrowUp,
                    label = "Top",
                    textColor = textColor,
                    borderColor = borderColor,
                    fillColor = fillColor,
                    onClick = onBackToTop
                )
            }
        }

        // Right cluster: circles
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            HintFaceButton(letter = "Y", label = "Edit",   textColor = textColor, borderColor = borderColor, fillColor = fillColor, style = buttonGlyphStyle, swap = swapFaceButtons, onClick = onEdit)
            HintFaceButton(letter = "A", label = "Open",   textColor = textColor, borderColor = borderColor, fillColor = fillColor, style = buttonGlyphStyle, swap = swapFaceButtons, onClick = onOpen)
        }
    }
}

@Composable
private fun HintPill(
    glyph: ImageVector,
    label: String,
    textColor: Color,
    borderColor: Color,
    fillColor: Color,
    onClick: () -> Unit,
    // Touch-first layouts (portrait, no controller chrome) render the pill a bit
    // larger so it's an easier tap target; landscape keeps the compact controller size.
    // Same padding/icon-size scale as HintSelectorPill's `large`; that one leaves the
    // label's text style untouched too, so this follows suit rather than inventing a
    // second sizing scheme for text.
    large: Boolean = false,
) {
    val shape = RoundedCornerShape(8.dp)
    val hPad = if (large) 12.dp else 8.dp
    val vPad = if (large) 8.dp else 4.dp
    val glyphSize = if (large) 20.dp else 14.dp
    Row(
        modifier = Modifier
            .clickable(onClick = onClick)
            .background(color = fillColor, shape = shape)
            .border(width = 1.dp, color = borderColor, shape = shape)
            .padding(horizontal = hPad, vertical = vPad),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = glyph,
            contentDescription = null,
            tint = textColor,
            modifier = Modifier.size(glyphSize)
        )
        Text(
            text = label,
            color = textColor,
            style = MaterialTheme.typography.labelMedium
        )
    }
}

/**
 * Collapsed pill showing an optional bumper tag ("L1"/"R1", null in portrait) + a leading and
 * an optional trailing icon; tapping opens [menuContent] as a dropdown anchored above the bar.
 * Shared shell for the view-mode and sort selectors so both pills read identically.
 */
@Composable
private fun HintSelectorPill(
    bumperLabel: String?,
    leadingIcon: ImageVector,
    leadingDescription: String,
    trailingIcon: ImageVector?,
    textColor: Color,
    borderColor: Color,
    fillColor: Color,
    // Touch-first layouts (portrait, no controller chrome) render the pill a bit
    // larger so it's an easier tap target; landscape keeps the compact controller size.
    large: Boolean = false,
    menuContent: @Composable (dismiss: () -> Unit) -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    var expanded by remember { mutableStateOf(false) }
    val hPad = if (large) 12.dp else 8.dp
    val vPad = if (large) 8.dp else 4.dp
    val leadingSize = if (large) 20.dp else 14.dp
    val trailingSize = if (large) 16.dp else 12.dp

    Box {
        Row(
            modifier = Modifier
                .clickable { expanded = true }
                .background(color = fillColor, shape = shape)
                .border(width = 1.dp, color = borderColor, shape = shape)
                .padding(horizontal = hPad, vertical = vPad),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (bumperLabel != null) {
                Text(
                    text = bumperLabel,
                    color = textColor,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                )
            }
            Icon(
                imageVector = leadingIcon,
                contentDescription = leadingDescription,
                tint = textColor,
                modifier = Modifier.size(leadingSize),
            )
            if (trailingIcon != null) {
                Icon(
                    imageVector = trailingIcon,
                    contentDescription = null,
                    tint = textColor,
                    modifier = Modifier.size(trailingSize),
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            offset = DpOffset(0.dp, (-220).dp),
        ) {
            menuContent { expanded = false }
        }
    }
}

/**
 * Selected-item highlight shared by the view-mode and sort dropdowns: an accent
 * boundary + faint fill so the active entry is obvious at a glance (bold weight
 * alone was too subtle). No-op for unselected items.
 */
private fun Modifier.selectedMenuItem(selected: Boolean, accentColor: Color): Modifier =
    if (selected) {
        val shape = RoundedCornerShape(6.dp)
        this
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .background(accentColor.copy(alpha = 0.12f), shape)
            .border(1.5.dp, accentColor, shape)
    } else {
        this
    }

@Composable
private fun HintViewModeSelector(
    bumperLabel: String?,
    currentMode: LibraryViewMode,
    onSelected: (LibraryViewMode) -> Unit,
    textColor: Color,
    borderColor: Color,
    fillColor: Color,
    accentColor: Color,
    large: Boolean = false,
) {
    HintSelectorPill(
        bumperLabel = bumperLabel,
        leadingIcon = currentMode.icon(),
        leadingDescription = currentMode.displayName,
        trailingIcon = null,
        textColor = textColor, borderColor = borderColor, fillColor = fillColor,
        large = large,
    ) { dismiss ->
        LibraryViewMode.entries.forEach { mode ->
            val selected = mode == currentMode
            // Every mode renders in the full theme colour (no dimming); the current
            // one is called out with bold weight AND an accent boundary/fill.
            DropdownMenuItem(
                modifier = Modifier.selectedMenuItem(selected, accentColor),
                text = {
                    Text(
                        mode.displayName,
                        color = textColor,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = mode.icon(),
                        contentDescription = null,
                        tint = textColor,
                        modifier = Modifier.size(20.dp),
                    )
                },
                onClick = {
                    onSelected(mode)
                    dismiss()
                },
            )
        }
    }
}

@Composable
private fun HintSortSelector(
    bumperLabel: String?,
    currentField: LibrarySortField,
    currentDirection: SortDirection,
    onFieldSelected: (LibrarySortField) -> Unit,
    onDirectionToggle: () -> Unit,
    textColor: Color,
    borderColor: Color,
    fillColor: Color,
    accentColor: Color,
    large: Boolean = false,
) {
    HintSelectorPill(
        bumperLabel = bumperLabel,
        leadingIcon = currentField.icon(),
        leadingDescription = "Sort by ${currentField.label}",
        trailingIcon = currentDirection.icon(),
        textColor = textColor, borderColor = borderColor, fillColor = fillColor,
        large = large,
    ) { dismiss ->
        LibrarySortField.entries.forEach { field ->
            val selected = field == currentField
            // Same style as the view-mode dropdown: every field in full theme colour,
            // the active one called out with bold weight AND an accent boundary/fill.
            DropdownMenuItem(
                modifier = Modifier.selectedMenuItem(selected, accentColor),
                text = {
                    Text(
                        field.label,
                        color = textColor,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = field.icon(),
                        contentDescription = null,
                        tint = textColor,
                        modifier = Modifier.size(20.dp),
                    )
                },
                // Selected field shows its current direction; tapping it toggles asc/desc.
                trailingIcon = if (selected) {
                    {
                        Icon(
                            imageVector = currentDirection.icon(),
                            contentDescription = null,
                            tint = textColor,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                } else null,
                onClick = {
                    if (selected) onDirectionToggle() else onFieldSelected(field)
                    dismiss()
                },
            )
        }
    }
}

@Composable
private fun HintFaceButton(
    letter: String,
    label: String,
    textColor: Color,
    borderColor: Color,
    fillColor: Color,
    style: ButtonGlyphStyle,
    swap: Boolean,
    onClick: () -> Unit,
) {
    // The displayed button follows the ABXY-swap so the hint matches what you physically press
    // (input is remapped in MainActivity.dispatchKeyEvent). A↔B, X↔Y.
    val shown = if (swap) when (letter) {
        "A" -> "B"; "B" -> "A"; "X" -> "Y"; "Y" -> "X"; else -> letter
    } else letter
    Row(
        modifier = Modifier.clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier.size(22.dp),
            contentAlignment = Alignment.Center
        ) {
            if (style == ButtonGlyphStyle.PLAYSTATION) {
                // Bare Sony shape (monochrome, palette-tinted), drawn so custom app fonts can't
                // turn a Unicode glyph into tofu.
                PlayStationGlyph(letter = shown, color = textColor)
            } else {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .background(color = fillColor, shape = CircleShape)
                        .border(width = 1.dp, color = borderColor, shape = CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = shown,
                        color = textColor,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                }
            }
        }
        Text(
            text = label,
            color = textColor,
            style = MaterialTheme.typography.labelMedium
        )
    }
}

/**
 * Compact preview of the four face-button glyphs in the given style — the settings row shows
 * this instead of a platform name, so users pick by the actual glyphs they'll see in-app.
 */
@Composable
fun FaceButtonGlyphPreview(
    style: ButtonGlyphStyle,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf("A", "B", "X", "Y").forEach { letter ->
            if (style == ButtonGlyphStyle.PLAYSTATION) {
                PlayStationGlyph(letter = letter, color = color)
            } else {
                Text(
                    text = letter,
                    color = color,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                )
            }
        }
    }
}

/** Draws a PlayStation face-button shape for the given Xbox-equivalent letter (A→✕, B→○, X→□, Y→△). */
@Composable
private fun PlayStationGlyph(letter: String, color: Color) {
    Canvas(modifier = Modifier.size(16.dp)) {
        val strokeW = size.minDimension * 0.11f
        val pad = strokeW * 1.2f
        val stroke = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val w = size.width
        val h = size.height
        when (letter) {
            "A" -> { // Cross ✕
                drawLine(color, Offset(pad, pad), Offset(w - pad, h - pad), strokeWidth = strokeW, cap = StrokeCap.Round)
                drawLine(color, Offset(w - pad, pad), Offset(pad, h - pad), strokeWidth = strokeW, cap = StrokeCap.Round)
            }
            "B" -> { // Circle ○
                drawCircle(color, radius = size.minDimension / 2f - pad, style = stroke)
            }
            "X" -> { // Square □
                drawRoundRect(
                    color,
                    topLeft = Offset(pad, pad),
                    size = Size(w - 2 * pad, h - 2 * pad),
                    cornerRadius = CornerRadius(strokeW, strokeW),
                    style = stroke,
                )
            }
            "Y" -> { // Triangle △
                val path = Path().apply {
                    moveTo(w / 2f, pad)
                    lineTo(w - pad, h - pad)
                    lineTo(pad, h - pad)
                    close()
                }
                drawPath(path, color, style = stroke)
            }
        }
    }
}
