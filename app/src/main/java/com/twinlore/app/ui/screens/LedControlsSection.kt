package com.twinlore.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The Thor RGB-LED controls ("LED follows accent color" toggle, plus Brightness and Saturation
 * when it is on), packaged for a bottom sheet. Reads and writes the same app-wide
 * settings the main Settings screen does, so a change here applies everywhere — which is
 * why the label and the device-settings caveat are worded identically in both places.
 *
 * Used by the guide reader's display sheet.
 */
@Composable
fun LedControlsSection(
    settingsViewModel: SettingsViewModel,
    accent: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    val ledEnabled by settingsViewModel.ledEnabled.collectAsState(initial = false)
    val ledBrightness by settingsViewModel.ledBrightness.collectAsState(initial = 255)
    val ledSaturation by settingsViewModel.ledSaturationBoost.collectAsState(initial = 0f)

    Column(modifier.fillMaxWidth()) {
        Text(
            "LED",
            style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
            color = contentColor.copy(alpha = 0.7f),
            modifier = Modifier.padding(bottom = 4.dp),
        )
        // Same string the Settings screen shows above its LED block: the app can only tint an
        // LED the device itself has switched on, and the toggle looks broken without saying so.
        Text(
            text = androidx.compose.ui.res.stringResource(com.twinlore.app.R.string.led_control_explanation),
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            color = contentColor.copy(alpha = 0.7f),
            modifier = Modifier.padding(bottom = 6.dp),
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Named exactly as in Settings — it writes the same app-wide setting, so two names
            // for one switch reads as two different features.
            Text("LED follows accent color", style = androidx.compose.material3.MaterialTheme.typography.bodyLarge, color = contentColor)
            Switch(
                checked = ledEnabled,
                onCheckedChange = { settingsViewModel.setLedEnabled(it) },
                colors = SwitchDefaults.colors(checkedTrackColor = accent, checkedThumbColor = Color.White),
            )
        }
        if (ledEnabled) {
            LedSlider(
                label = "Brightness",
                valueText = LedSliderMath.brightnessPercentLabel(ledBrightness),
                value = LedSliderMath.brightnessToFraction(ledBrightness),
                steps = LedSliderMath.BRIGHTNESS_STEP_COUNT,
                accent = accent,
                contentColor = contentColor,
                onChange = { settingsViewModel.setLedBrightness(LedSliderMath.fractionToBrightness(it)) },
            )
            LedSlider(
                label = "Saturation",
                valueText = LedSliderMath.saturationLabel(ledSaturation),
                value = ledSaturation.coerceIn(0f, 1f),
                steps = LedSliderMath.SATURATION_STEP_COUNT,
                accent = accent,
                contentColor = contentColor,
                onChange = { settingsViewModel.setLedSaturationBoost(LedSliderMath.snapFraction(it, LedSliderMath.SATURATION_STEP_COUNT)) },
            )
        }
    }
}

@Composable
private fun LedSlider(
    label: String,
    valueText: String,
    value: Float,
    steps: Int,
    accent: Color,
    contentColor: Color,
    onChange: (Float) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 13.sp, color = contentColor.copy(alpha = 0.8f))
        Text(valueText, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = contentColor)
    }
    Slider(
        value = value,
        onValueChange = onChange,
        // stepCount - 1 internal steps, matching NavigableSliderRow.
        steps = (steps - 1).coerceAtLeast(0),
        colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent),
    )
}
