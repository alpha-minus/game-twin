package com.twinlore.app.ui.screens.group

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.Brush
import com.twinlore.app.data.AppFont
import com.twinlore.app.data.FocusColor
import com.twinlore.app.data.ThemePalette
import com.twinlore.app.ui.screens.SettingsViewModel
import com.twinlore.app.ui.theme.toFontFamily

/**
 * Unified Display sheet for the page-group viewer. Surfaces four reading
 * controls in one place — Text size, Theme palette, Apply-theme switch,
 * Font — opened directly from a top-bar Display IconButton (no kebab).
 *
 * Layout mirrors the Settings palette/font row design: 40dp rounded
 * squares with a centered "A" in the palette's foreground color, focus-
 * color border on the active selection.
 *
 * Any active slider drag fades the entire sheet to 0.15 alpha so the user
 * can see the live textZoom / palette / font change apply to the WebView
 * behind.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PageGroupDisplaySheet(
    settingsViewModel: SettingsViewModel,
    palette: ThemePalette,
    focusColor: Color,
    textZoomFraction: Float,
    onTextZoomChange: (Float) -> Unit,
    /** Whether the guide on screen came through convertTxtToGameFaqHtml. The
     *  Smart Formatting row has nothing to act on in saved web guides or EPUBs,
     *  so it shows dimmed and inert there rather than silently doing nothing. */
    isTextGuide: Boolean,
    onDismiss: () -> Unit
) {
    val currentPalette by settingsViewModel.themePalette.collectAsState(initial = null)
    val guideThemeInject by settingsViewModel.guideThemeInject.collectAsState(initial = true)
    val smartFormatting by settingsViewModel.guideSmartFormatting.collectAsState(initial = true)
    val appFont by settingsViewModel.appFont.collectAsState(initial = AppFont.DEFAULT)

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val sliderInteractionSource = remember { MutableInteractionSource() }
    val isDragging by sliderInteractionSource.collectIsDraggedAsState()
    val sheetAlpha by animateFloatAsState(
        targetValue = if (isDragging) 0.15f else 1f,
        label = "display_sheet_alpha"
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = palette.background,
        contentColor = palette.foreground,
        modifier = Modifier.alpha(sheetAlpha)
    ) {
        val configuration = LocalConfiguration.current
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // Cap the height so the sheet doesn't swallow the whole screen,
                // and scroll when the controls (incl. the accent-color row)
                // exceed it — especially in landscape on the handheld.
                .heightIn(max = (configuration.screenHeightDp * 0.72f).dp)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp)
        ) {
            // ── 1. Text size (top) ──
            SectionHeader(text = "Text size", contentColor = palette.foreground)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${(textZoomFraction * 100).toInt()}%",
                    style = MaterialTheme.typography.titleMedium,
                    color = palette.foreground,
                    modifier = Modifier.padding(end = 12.dp)
                )
                Slider(
                    value = textZoomFraction,
                    onValueChange = onTextZoomChange,
                    valueRange = 0.5f..2.0f,
                    steps = 14,
                    interactionSource = sliderInteractionSource,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { onTextZoomChange(1.0f) }) {
                    Text("Reset", color = focusColor)
                }
            }
            Spacer(modifier = Modifier.height(20.dp))

            // ── 2. Theme palette (Settings-style "A" swatches) ──
            SectionHeader(text = "Theme", contentColor = palette.foreground)
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(ThemePalette.values().toList()) { paletteOption ->
                    PaletteSwatch(
                        palette = paletteOption,
                        isSelected = paletteOption == currentPalette,
                        focusColor = focusColor,
                        onClick = { settingsViewModel.setThemePalette(paletteOption) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(20.dp))

            // ── 3. Accent color (under Theme): Adaptive option + the global
            // focus-color swatches. Writes the same app-wide accent settings the
            // main Settings screen does (autoFocusColorDetection + focusColor),
            // so the change applies everywhere, not just here.
            SectionHeader(text = "Accent color", contentColor = palette.foreground)
            val accentSelection by settingsViewModel.focusColor.collectAsState(initial = FocusColor.CRIMSON)
            val adaptiveAccent by settingsViewModel.autoFocusColorDetection.collectAsState(initial = false)
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                item {
                    AdaptiveAccentSwatch(
                        isSelected = adaptiveAccent,
                        focusColor = focusColor,
                        contentColor = palette.foreground,
                        onClick = { settingsViewModel.setAutoFocusColorDetection(true) }
                    )
                }
                items(FocusColor.selectableFor(palette.isDark)) { colorOption ->
                    AccentColorSwatch(
                        color = colorOption,
                        isSelected = !adaptiveAccent && colorOption == accentSelection,
                        focusColor = focusColor,
                        contentColor = palette.foreground,
                        onClick = {
                            settingsViewModel.setAutoFocusColorDetection(false)
                            settingsViewModel.setFocusColor(colorOption)
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.height(20.dp))

            // ── 4. Font picker (preview-in-font pills) ──
            SectionHeader(text = "Font", contentColor = palette.foreground)
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(AppFont.values().toList()) { font ->
                    FontPill(
                        font = font,
                        isSelected = font == appFont,
                        focusColor = focusColor,
                        contentColor = palette.foreground,
                        onClick = { settingsViewModel.setAppFont(font) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(20.dp))

            // ── 5. Formatting toggles (bottom): least-used controls, parked last
            // so the visual reading controls stay at the top of the sheet.
            ToggleRow(
                title = "Smart Formatting",
                subtitle = if (isTextGuide) {
                    "Fit ASCII art and formatting to the screen. Text size will adapt."
                } else {
                    "Text based guides only."
                },
                checked = smartFormatting,
                enabled = isTextGuide,
                contentColor = palette.foreground,
                focusColor = focusColor,
                onCheckedChange = { settingsViewModel.setGuideSmartFormatting(it) },
            )
            ToggleRow(
                title = "Reader Mode",
                subtitle = null,
                checked = guideThemeInject,
                enabled = true,
                contentColor = palette.foreground,
                focusColor = focusColor,
                onCheckedChange = { settingsViewModel.setGuideThemeInject(it) },
            )

            Spacer(modifier = Modifier.height(20.dp))
            // ── LED ──
            com.twinlore.app.ui.screens.LedControlsSection(
                settingsViewModel = settingsViewModel,
                accent = focusColor,
                contentColor = palette.foreground,
            )
        }
    }
}

/**
 * Label (plus optional explanation) on the left, switch on the right. A disabled
 * row dims and stops taking input, so a control that cannot apply to the guide
 * on screen still reads as present rather than going missing between guides.
 */
@Composable
private fun ToggleRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    enabled: Boolean,
    contentColor: Color,
    focusColor: Color,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .alpha(if (enabled) 1f else 0.45f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = contentColor
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedTrackColor = focusColor)
        )
    }
}

@Composable
private fun SectionHeader(text: String, contentColor: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = contentColor.copy(alpha = 0.7f),
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

/**
 * 40dp rounded square swatch with a centered "A" in the palette's
 * foreground color. Mirrors SettingsScreen.PaletteSwatch so the picker
 * reads consistently across the two surfaces. 3dp focus-color border on
 * the active selection; muted 1dp border otherwise.
 */
@Composable
private fun PaletteSwatch(
    palette: ThemePalette,
    isSelected: Boolean,
    focusColor: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(palette.background)
            .then(
                if (isSelected) {
                    Modifier.border(3.dp, focusColor, RoundedCornerShape(8.dp))
                } else {
                    // Inactive border picks up the palette's own accent so each
                    // swatch reads as a paired bg+fg pill rather than a flat
                    // colored square.
                    Modifier.border(1.dp, palette.foreground, RoundedCornerShape(8.dp))
                }
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "A",
            color = palette.foreground,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * 32dp circular accent swatch (mirrors SettingsScreen.ColorSwatch). 3dp focus-
 * color border when this is the active accent, muted 1dp border otherwise.
 */
@Composable
private fun AccentColorSwatch(
    color: FocusColor,
    isSelected: Boolean,
    focusColor: Color,
    contentColor: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(color.color)
            .then(
                if (isSelected) Modifier.border(3.dp, focusColor, CircleShape)
                else Modifier.border(1.dp, contentColor.copy(alpha = 0.3f), CircleShape)
            )
            .clickable(onClick = onClick)
    )
}

/**
 * The "Adaptive" accent option: a rainbow swatch with a sparkle glyph, signalling
 * the accent is auto-derived from the cover art. Selected = auto-detection on.
 */
@Composable
private fun AdaptiveAccentSwatch(
    isSelected: Boolean,
    focusColor: Color,
    contentColor: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(
                Brush.sweepGradient(
                    listOf(
                        Color(0xFFDC143C), Color(0xFFFFD700), Color(0xFF00C853),
                        Color(0xFF00D4FF), Color(0xFF7C4DFF), Color(0xFFDC143C)
                    )
                )
            )
            .then(
                if (isSelected) Modifier.border(3.dp, focusColor, CircleShape)
                else Modifier.border(1.dp, contentColor.copy(alpha = 0.3f), CircleShape)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.AutoAwesome,
            contentDescription = "Adaptive accent color",
            tint = Color.White,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun FontPill(
    font: AppFont,
    isSelected: Boolean,
    focusColor: Color,
    contentColor: Color,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val fontFamily = font.toFontFamily(context)
    val borderColor = if (isSelected) focusColor else Color.Transparent
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = font.displayName,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = fontFamily,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = contentColor
        )
    }
}
