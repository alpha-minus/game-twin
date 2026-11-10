package com.twinlore.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.twinlore.app.data.FocusColor
import com.twinlore.app.ui.LocalThemePalette
import com.twinlore.app.data.HapticFeedbackIntensity
import com.twinlore.app.data.LedTurnOffTime
import com.twinlore.app.ui.LocalCachedFocusColor
import com.twinlore.app.ui.utils.runWithHapticAndLed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SaveTypeBottomSheet(
    saveAllAvailable: Boolean,
    saveAllChapterCount: Int,
    saveAllPageCount: Int = 0,
    onSaveOffline: () -> Unit,
    onSaveAllPages: () -> Unit,
    onSaveAsLink: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val view = LocalView.current
    val palette = LocalThemePalette.current
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val focusColorState by settingsViewModel.focusColor.collectAsState(initial = FocusColor.CRIMSON)
    val cachedFocusColor = LocalCachedFocusColor.current?.value
    val focusColor = cachedFocusColor ?: focusColorState.color
    val hapticFeedbackIntensity by settingsViewModel.hapticFeedbackIntensity.collectAsState(initial = HapticFeedbackIntensity.MEDIUM)
    val ledEnabled by settingsViewModel.ledEnabled.collectAsState(initial = true)
    val ledBrightness by settingsViewModel.ledBrightness.collectAsState(initial = 127)
    val ledSaturationBoost by settingsViewModel.ledSaturationBoost.collectAsState(initial = 0.75f)
    val ledTurnOffTime by settingsViewModel.ledTurnOffTime.collectAsState(initial = LedTurnOffTime.SEC_15)
    val isDarkTheme = palette.isDark
    val surfaceColor = palette.background
    val textColor = palette.foreground
    val secondaryTextColor = textColor.copy(alpha = 0.7f)
    val cardBg = if (isDarkTheme) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = surfaceColor,
        scrimColor = Color.Black.copy(alpha = 0.5f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Save",
                style = MaterialTheme.typography.titleMedium,
                color = focusColor
            )
            Spacer(modifier = Modifier.height(16.dp))
            SaveTypeOption(
                icon = Icons.Default.Download,
                title = "Save guide",
                description = AnnotatedString("Keep this page for offline reading."),
                cardBg = cardBg,
                focusColor = focusColor,
                textColor = textColor,
                secondaryTextColor = secondaryTextColor,
                onClick = {
                    runWithHapticAndLed(view, hapticFeedbackIntensity, ledEnabled, focusColor, ledBrightness, ledSaturationBoost, ledTurnOffTime.inactivityMs) {
                        onSaveOffline()
                    }
                }
            )
            Spacer(modifier = Modifier.height(10.dp))
            // When available, bold the chapter count in focus color so the
            // "20 chapters detected" badge pops without making the whole
            // option look more clickable than its siblings. When unavailable,
            // the SaveTypeOption's enabled=false path dims the whole card
            // uniformly via Modifier.alpha so the per-color tuning that
            // used to live here goes away.
            val saveAllSubtitle = if (saveAllAvailable && saveAllChapterCount > 0 && saveAllPageCount > 0) {
                buildAnnotatedString {
                    append("Save ")
                    withStyle(SpanStyle(color = focusColor, fontWeight = FontWeight.Bold)) {
                        append("$saveAllChapterCount chapter${if (saveAllChapterCount != 1) "s" else ""}")
                    }
                    append(" in ")
                    withStyle(SpanStyle(color = focusColor, fontWeight = FontWeight.Bold)) {
                        append("$saveAllPageCount page${if (saveAllPageCount != 1) "s" else ""}")
                    }
                    append(".")
                }
            } else if (saveAllAvailable) {
                AnnotatedString("Save all pages linked within this page for offline reading.")
            } else {
                AnnotatedString("Works when this page links to a list of related pages.")
            }
            SaveTypeOption(
                icon = Icons.Default.Download,
                title = "Save guide set",
                description = saveAllSubtitle,
                cardBg = cardBg,
                focusColor = focusColor,
                textColor = textColor,
                secondaryTextColor = secondaryTextColor,
                enabled = saveAllAvailable,
                onClick = {
                    runWithHapticAndLed(view, hapticFeedbackIntensity, ledEnabled, focusColor, ledBrightness, ledSaturationBoost, ledTurnOffTime.inactivityMs) {
                        onSaveAllPages()
                    }
                }
            )
            Spacer(modifier = Modifier.height(10.dp))
            SaveTypeOption(
                icon = Icons.Default.BookmarkBorder,
                title = "Save link",
                description = AnnotatedString("Save link for online access."),
                cardBg = cardBg,
                focusColor = focusColor,
                textColor = textColor,
                secondaryTextColor = secondaryTextColor,
                onClick = {
                    runWithHapticAndLed(view, hapticFeedbackIntensity, ledEnabled, focusColor, ledBrightness, ledSaturationBoost, ledTurnOffTime.inactivityMs) {
                        onSaveAsLink()
                    }
                }
            )
        }
    }
}

@Composable
private fun SaveTypeOption(
    icon: ImageVector,
    title: String,
    description: AnnotatedString,
    cardBg: Color,
    focusColor: Color,
    textColor: Color,
    secondaryTextColor: Color,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    // Disabled state: dim the whole option uniformly via Modifier.alpha
    // (covers the icon, title, description, and card background in one
    // pass) and skip clickable so there's no ripple feedback on a
    // non-actionable card.
    val surfaceModifier = Modifier
        .fillMaxWidth()
        .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
        .then(if (enabled) Modifier else Modifier.alpha(0.35f))
    Surface(
        modifier = surfaceModifier,
        shape = RoundedCornerShape(14.dp),
        color = cardBg
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = focusColor
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = textColor
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = secondaryTextColor
                )
            }
        }
    }
}
