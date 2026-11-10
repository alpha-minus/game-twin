package com.twinlore.app.ui.screens

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Wifi1Bar
import androidx.compose.material.icons.filled.Wifi2Bar
import androidx.compose.foundation.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.twinlore.app.data.NetworkStatus
import com.twinlore.app.di.NetworkConnectivityEntryPoint
import com.twinlore.app.services.ScreenCaptureService
import dagger.hilt.android.EntryPointAccessors
import com.twinlore.app.ui.theme.toFontFamily
import androidx.compose.ui.res.painterResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.twinlore.app.R
import com.twinlore.app.data.AppFont
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.twinlore.app.data.StatusBarMode
import com.twinlore.app.data.ThemePalette
import com.twinlore.app.ui.LocalThemePalette
import com.twinlore.app.ui.components.BatteryReading
import com.twinlore.app.ui.components.PerfMeters
import com.twinlore.app.ui.components.StatusReadouts
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Status row: clock, wifi, battery.
 * Renders only in landscape (in portrait the Android system bar provides this).
 * [tint] overrides the palette foreground — for hosts that draw the row over artwork
 * where the theme's foreground may not be readable.
 * [perfMetersEnabled] opts this host into the Performance status-bar mode — only the
 * vault session bar shows the meters; other bars render the standard cluster regardless
 * of mode.
 */
@Composable
fun LibraryStatusIndicators(
    modifier: Modifier = Modifier,
    tint: Color? = null,
    perfMetersEnabled: Boolean = false,
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
    if (!isLandscape) return

    val context = LocalContext.current
    val palette: ThemePalette = LocalThemePalette.current
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val appFont by settingsViewModel.appFont.collectAsState(initial = AppFont.DEFAULT)
    val isPixelFont = appFont == AppFont.PIXEL

    // Global status-cluster mode: Off renders nothing at all; Performance adds
    // the CPU/GPU/RAM/PWR/TMP meters — but only for hosts that opted in via
    // [perfMetersEnabled] (currently just the vault session bar). Tap anywhere
    // on the cluster to flip Standard ↔ Performance, also opt-in only (Off is
    // only reachable from Settings).
    val statusBarMode by settingsViewModel.statusBarMode.collectAsState(initial = StatusBarMode.STANDARD)
    if (statusBarMode == StatusBarMode.OFF) return
    val metricsSnapshot by settingsViewModel.metricsSnapshot.collectAsState()

    // --- Screen-capture state (REC pill + stop-confirm dialog) ---
    val isCapturing by ScreenCaptureService.isRunningFlow.collectAsState()
    var showStopCaptureDialog by remember { mutableStateOf(false) }

    // --- Clock ---
    var currentTime by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        while (true) {
            currentTime = sdf.format(Date())
            delay(60_000L - (System.currentTimeMillis() % 60_000L))
        }
    }

    // --- Battery ---
    var batteryPercent by remember { mutableIntStateOf(-1) }
    var isCharging by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        // Treat both "actively charging" and "full while plugged in" as
        // charging — the pill should keep the bolt while the cable is
        // connected, even when the level has topped out.
        fun chargingFromIntent(intent: Intent): Boolean {
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            if (status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
            ) return true
            val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
            return plugged != 0
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent == null) return
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                batteryPercent = if (level >= 0 && scale > 0) (level * 100) / scale else -1
                isCharging = chargingFromIntent(intent)
            }
        }
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val stickyIntent = context.registerReceiver(receiver, filter)
        if (stickyIntent != null) {
            val level = stickyIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = stickyIntent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            batteryPercent = if (level >= 0 && scale > 0) (level * 100) / scale else -1
            isCharging = chargingFromIntent(stickyIntent)
        }
        onDispose { context.unregisterReceiver(receiver) }
    }

    // --- WiFi (signal level from the default network's capabilities; fallback
    // to mid-bars when OEM reports SIGNAL_STRENGTH_UNSPECIFIED so we don't show a
    // misleading "no signal" on Android 10+ without ACCESS_FINE_LOCATION).
    // Followed through NetworkConnectivityManager.status, not read here: this
    // used to be a snapshot retaken only when the battery level changed, which
    // after a wake meant a snapshot taken before Wi-Fi was back, kept. ---
    val networkStatus by remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            NetworkConnectivityEntryPoint::class.java,
        ).networkConnectivityManager().status
    }.collectAsState()
    val baseColor = tint ?: palette.foreground

    Row(
        verticalAlignment = Alignment.CenterVertically,
        // Packed to the right. The cluster fills the middle of the bar it sits
        // in, so what it draws has to be pushed to the end of that space or it
        // floats away from the clock and the way out beside it.
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .then(
                if (perfMetersEnabled) {
                    Modifier.clickable(onClickLabel = "Toggle performance meters") {
                        settingsViewModel.tapToggleStatusBarMode()
                    }
                } else Modifier
            )
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        val showMeters = perfMetersEnabled && statusBarMode == StatusBarMode.PERFORMANCE
        if (showMeters) {
            // The meters are what yields. Weighted, they are measured after
            // everything beside them, so the readouts take their size first
            // and the meters fit themselves into what is left.
            PerfMeters(
                metricsSnapshot,
                modifier = Modifier.weight(1f, fill = false),
                textColor = baseColor,
                pixelFont = isPixelFont,
            )
        }
        // REC pill: persistent reminder that screen capture is active. Tap to stop.
        if (isCapturing) {
            RecordingPill(pixelated = isPixelFont, onClick = { showStopCaptureDialog = true })
        }
        // Connection, time, battery: the same group, drawn the same way, in
        // every bar and every mode (see [StatusReadouts]). Performance only
        // adds the meters to its left, so nothing here moves on a switch.
        StatusReadouts(
            network = networkStatus,
            time = currentTime,
            battery = if (batteryPercent >= 0) BatteryReading(batteryPercent, isCharging) else null,
            color = baseColor,
            pixelFont = isPixelFont,
        )
    }

    if (showStopCaptureDialog) {
        StopCaptureDialog(
            onConfirm = {
                ScreenCaptureService.stop(context)
                showStopCaptureDialog = false
            },
            onDismiss = { showStopCaptureDialog = false },
        )
    }
}

/** Recording color used by the REC pill and its dot — deliberately red so it reads as
 *  "capture is live" regardless of the theme palette. */
private val RecordingRed = Color(0xFFE53935)

/**
 * Small "● REC" chip shown in the status row while a MediaProjection session is active.
 * Tapping it opens the stop-confirm dialog. Styled to sit alongside the clock/wifi/battery
 * indicators and to match pixel-font chrome when that font is active.
 */
@Composable
private fun RecordingPill(
    pixelated: Boolean,
    onClick: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(if (pixelated) 1.dp else 6.dp))
            .background(RecordingRed.copy(alpha = 0.16f))
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Screen capture active. Tap to stop." }
            .padding(horizontal = 6.dp, vertical = 3.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(RecordingRed)
        )
        Text(
            text = "REC",
            color = RecordingRed,
            fontWeight = FontWeight.Bold,
            fontFamily = if (pixelated) {
                com.twinlore.app.data.AppFont.PIXEL.toFontFamily(context)
            } else FontFamily.Default,
            fontSize = if (pixelated) 7.sp else 11.sp,
            maxLines = 1,
        )
    }
}

/**
 * Confirm dialog for ending the active capture session. The copy warns that the system
 * permission dialog reappears on the next screenshot — the intended privacy affordance.
 */
@Composable
private fun StopCaptureDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Stop screen capture?") },
        text = {
            Text(
                "Screen capture will end and the app can no longer read the screen. " +
                    "You'll be asked for permission again next time you take a screenshot."
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Stop") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
