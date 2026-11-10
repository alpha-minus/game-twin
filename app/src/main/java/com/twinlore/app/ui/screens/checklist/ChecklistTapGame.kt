package com.twinlore.app.ui.screens.checklist

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.twinlore.app.ui.LocalCachedFocusColor
import com.twinlore.app.ui.utils.ImagePathUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

private const val TOTAL_MILLIS = 10_000L
private const val COUNTDOWN_SECONDS = 3
private const val URGENCY_MILLIS = 3_000L // final-stretch color shift

// Tap-count milestones that fire an "impact" animation + stronger haptic.
// Index in the array = intensity level (0 = pre-first-milestone).
private val MILESTONES = intArrayOf(50, 100, 125, 150)

/**
 * Intensity level based on tap count. Higher = bigger visual impact and
 * stronger haptic. 0 is the default (no milestone yet).
 */
private fun intensityFor(tapCount: Int): Int {
    var level = 0
    for (m in MILESTONES) if (tapCount >= m) level += 1
    return level
}

private enum class Phase { Countdown, Running, Finished }

/**
 * Transient visual on each tap — an expanding ripple ring + a floating "+1" number
 * that fades out over ~650 ms.
 */
private data class TapEffect(
    val id: Long,
    val position: Offset,
    val createdAt: Long,
)

private const val EFFECT_LIFETIME_MS = 650L

@Composable
fun ChecklistTapGame(
    vaultArtworkPath: String?,
    vaultId: String,
    highScore: Int,
    onUpdateHighScore: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val accentColor = LocalCachedFocusColor.current?.value ?: MaterialTheme.colorScheme.primary
    val menuBg = MaterialTheme.colorScheme.background
    val context = LocalContext.current
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val isLandscape = LocalConfiguration.current.orientation ==
        android.content.res.Configuration.ORIENTATION_LANDSCAPE

    val artworkModel = remember(vaultArtworkPath, vaultId) {
        vaultArtworkPath?.let { ImagePathUtils.toImageData(it, vaultId) }
    }

    var phase by remember { mutableStateOf(Phase.Countdown) }
    var countdownValue by remember { mutableStateOf(COUNTDOWN_SECONDS) }
    var remainingMillis by remember { mutableStateOf(TOTAL_MILLIS) }
    var tapCount by remember { mutableStateOf(0) }
    // Captured at the instant the round ends. Stable for the result dialog —
    // `highScore` recomposes to equal `tapCount` once onUpdateHighScore lands,
    // so comparing live would make the celebration flicker off. Reset on replay.
    var achievedNewHighScore by remember { mutableStateOf(false) }
    // Pixel app-icon unlock: earned by reaching PIXEL_UNLOCK_TAP_SCORE. Captured
    // at open so we only celebrate the moment it's first crossed.
    val pixelWasLocked = remember { highScore < com.twinlore.app.data.PIXEL_UNLOCK_TAP_SCORE }
    var pixelUnlockedThisRound by remember { mutableStateOf(false) }

    val progressRatio by remember {
        derivedStateOf { (remainingMillis.toFloat() / TOTAL_MILLIS).coerceIn(0f, 1f) }
    }
    // Ring/accent color shifts toward a warm red in the final few seconds to
    // signal urgency. lerp from accent → a red based on how deep we are into
    // the urgency window.
    val urgencyColor = remember { Color(0xFFFF5252) }
    val dynamicAccent by remember {
        derivedStateOf {
            if (phase != Phase.Running || remainingMillis > URGENCY_MILLIS) accentColor
            else {
                val t = 1f - (remainingMillis.toFloat() / URGENCY_MILLIS).coerceIn(0f, 1f)
                lerp(accentColor, urgencyColor, t * 0.85f)
            }
        }
    }

    val tapScale = remember { Animatable(1f) }
    val tapEffects = remember { mutableStateListOf<TapEffect>() }
    // Captured inside the ring Box's onSizeChanged — we need it to position the
    // floating "+1" texts relative to the Box's center.
    var ringBoxSizePx by remember { mutableStateOf(0 to 0) }

    // Platform vibrator for amplitude-controlled haptic. The Compose HapticFeedback
    // API only exposes two canned effects; a Vibrator lets us scale punch by tap
    // count (small on level 0, wallop on level 4).
    val vibrator: Vibrator? = remember(context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)
                ?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    // Milestone impact state. `shakeAnim` drives a damped sine-wave translation
    // on the ring Box; `burstText` + `burstScale` + `burstAlpha` render a big
    // "50!/100!/125!/150!" callout that scales up then fades.
    val shakeAnim = remember { Animatable(0f) }
    var burstText by remember { mutableStateOf<String?>(null) }
    val burstScale = remember { Animatable(0f) }
    val burstAlpha = remember { Animatable(0f) }
    // Current shake amplitude — scales with intensity level so a level-4
    // impact genuinely feels harder than level-1.
    var shakeAmplitudePx by remember { mutableStateOf(0f) }

    // Countdown driver — increments down every 750 ms then swaps to Running.
    LaunchedEffect(phase) {
        if (phase == Phase.Countdown) {
            while (countdownValue > 0 && phase == Phase.Countdown) {
                delay(750)
                countdownValue -= 1
            }
            if (phase == Phase.Countdown) {
                phase = Phase.Running
            }
        }
    }

    // Main countdown tick.
    LaunchedEffect(phase) {
        if (phase == Phase.Running) {
            val deadline = System.currentTimeMillis() + remainingMillis
            while (phase == Phase.Running && System.currentTimeMillis() < deadline) {
                remainingMillis = (deadline - System.currentTimeMillis()).coerceAtLeast(0)
                delay(16)
            }
            if (phase == Phase.Running) {
                remainingMillis = 0
                phase = Phase.Finished
                if (tapCount > highScore) {
                    achievedNewHighScore = true
                    onUpdateHighScore(tapCount)
                }
            }
        }
    }

    // Periodically drop tap effects that have aged out so the list doesn't
    // grow unbounded during long games.
    LaunchedEffect(Unit) {
        while (true) {
            val now = System.currentTimeMillis()
            tapEffects.removeAll { now - it.createdAt > EFFECT_LIFETIME_MS }
            delay(150)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(menuBg),
        ) {
            if (artworkModel != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(artworkModel)
                        .crossfade(true)
                        .size(200)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(radius = 24.dp)
                        .alpha(0.5f),
                    contentScale = ContentScale.Crop,
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(menuBg.copy(alpha = 0.50f)),
                )
            }

            // SS:CS (seconds : centiseconds) timer — just the numbers, large.
            // Positioned immediately above the tap ring rather than at the
            // very top so the eye can snap between timer and counter easily.
            val seconds = (remainingMillis / 1000).toInt()
            val centis = ((remainingMillis % 1000) / 10).toInt()
            val timerText = "%02d:%02d".format(seconds, centis)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val headline = when (phase) {
                    Phase.Countdown -> "Get ready…"
                    Phase.Running -> "Tap as fast as you can!"
                    Phase.Finished -> ""
                }
                if (headline.isNotBlank()) {
                    Text(
                        text = headline,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                if (phase == Phase.Running) {
                    Text(
                        text = timerText,
                        style = MaterialTheme.typography.displayLarge,
                        fontWeight = FontWeight.Bold,
                        color = dynamicAccent,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                } else {
                    Spacer(modifier = Modifier.height(24.dp))
                }

                Box(
                    modifier = Modifier
                        .size(300.dp)
                        .scale(tapScale.value)
                        // Damped sine-wave X translation driven by shakeAnim.
                        // 3 full cycles across the animation, decaying linearly
                        // by (1 - shakeAnim) so the motion settles to rest.
                        .graphicsLayer {
                            val s = shakeAnim.value
                            if (s > 0f && shakeAmplitudePx > 0f) {
                                val cycles = 3f
                                translationX =
                                    (sin(s * cycles * 2f * PI) * shakeAmplitudePx * (1f - s)).toFloat()
                            }
                        }
                        .onSizeChanged { ringBoxSizePx = it.width to it.height }
                        .pointerInput(phase) {
                            detectTapGestures(
                                onTap = { offset ->
                                    if (phase == Phase.Running) {
                                        val prevLevel = intensityFor(tapCount)
                                        tapCount++
                                        val newLevel = intensityFor(tapCount)

                                        // Per-tap haptic scales with current
                                        // intensity. Fallback to Compose's
                                        // canned effect on pre-O devices / no
                                        // amplitude control.
                                        val canAmplitude = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                                            vibrator?.hasVibrator() == true
                                        if (canAmplitude) {
                                            val dur = (12L + prevLevel * 8L).coerceAtMost(48L)
                                            val amp = (70 + prevLevel * 40).coerceAtMost(255)
                                            @Suppress("DEPRECATION")
                                            vibrator?.vibrate(VibrationEffect.createOneShot(dur, amp))
                                        } else {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }

                                        tapEffects.add(
                                            TapEffect(
                                                id = System.nanoTime(),
                                                position = offset,
                                                createdAt = System.currentTimeMillis(),
                                            ),
                                        )

                                        if (newLevel > prevLevel) {
                                            // Milestone crossed — fire the
                                            // impact: big ring punch, screen
                                            // shake, burst text, strong haptic.
                                            val milestone = MILESTONES[newLevel - 1]
                                            burstText = "$milestone!"
                                            shakeAmplitudePx = with(density) {
                                                (8f + newLevel * 5f).dp.toPx()
                                            }
                                            val peak = 1f + 0.08f * newLevel
                                            scope.launch {
                                                tapScale.snapTo(1f)
                                                tapScale.animateTo(peak, tween(100))
                                                tapScale.animateTo(1f, tween(260))
                                            }
                                            scope.launch {
                                                shakeAnim.snapTo(0f)
                                                shakeAnim.animateTo(
                                                    1f,
                                                    tween(durationMillis = 380 + newLevel * 60),
                                                )
                                                shakeAnim.snapTo(0f)
                                            }
                                            scope.launch {
                                                burstScale.snapTo(0.4f)
                                                burstAlpha.snapTo(1f)
                                                burstScale.animateTo(1.6f, tween(160))
                                                burstScale.animateTo(1.1f, tween(140))
                                                burstAlpha.animateTo(0f, tween(320))
                                                burstText = null
                                            }
                                            // Milestone haptic — a longer,
                                            // stronger pulse than the per-tap.
                                            if (canAmplitude) {
                                                val dur = (90L + newLevel * 30L).coerceAtMost(220L)
                                                val amp = (200 + newLevel * 15).coerceAtMost(255)
                                                @Suppress("DEPRECATION")
                                                vibrator?.vibrate(VibrationEffect.createOneShot(dur, amp))
                                            } else {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            }
                                        } else {
                                            scope.launch {
                                                tapScale.animateTo(0.92f, tween(durationMillis = 80))
                                                tapScale.animateTo(1f, tween(durationMillis = 80))
                                            }
                                        }

                                        // Pixel app-icon unlock: fire the moment
                                        // the score first reaches the threshold.
                                        if (pixelWasLocked && !pixelUnlockedThisRound &&
                                            tapCount >= com.twinlore.app.data.PIXEL_UNLOCK_TAP_SCORE
                                        ) {
                                            pixelUnlockedThisRound = true
                                            burstText = "Pixel icon unlocked!"
                                            scope.launch {
                                                burstScale.snapTo(0.4f)
                                                burstAlpha.snapTo(1f)
                                                burstScale.animateTo(1.6f, tween(180))
                                                burstScale.animateTo(1.1f, tween(160))
                                                burstAlpha.animateTo(0f, tween(700))
                                                burstText = null
                                            }
                                        }
                                    }
                                },
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val strokeWidthPx = 16.dp.toPx()
                        val diameter = size.minDimension - strokeWidthPx
                        val topLeft = Offset(
                            (size.width - diameter) / 2f,
                            (size.height - diameter) / 2f,
                        )
                        val arcSize = Size(diameter, diameter)

                        drawArc(
                            color = dynamicAccent.copy(alpha = 0.25f),
                            startAngle = -90f,
                            sweepAngle = 360f,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = strokeWidthPx),
                        )
                        if (phase == Phase.Running && progressRatio > 0f) {
                            drawArc(
                                color = dynamicAccent,
                                startAngle = -90f,
                                sweepAngle = 360f * progressRatio,
                                useCenter = false,
                                topLeft = topLeft,
                                size = arcSize,
                                style = Stroke(width = strokeWidthPx),
                            )
                        }

                        // Expanding-ripple effect at each tap position.
                        val now = System.currentTimeMillis()
                        for (effect in tapEffects) {
                            val age = (now - effect.createdAt).toFloat()
                            val life = (age / EFFECT_LIFETIME_MS).coerceIn(0f, 1f)
                            if (life >= 1f) continue
                            val maxRadius = 90.dp.toPx()
                            val radius = maxRadius * life
                            val alpha = (1f - life) * 0.6f
                            drawCircle(
                                color = dynamicAccent.copy(alpha = alpha),
                                radius = radius,
                                center = effect.position,
                                style = Stroke(width = 4.dp.toPx()),
                            )
                        }
                    }

                    // Floating "+1" numbers that rise and fade at tap positions.
                    val now = System.currentTimeMillis()
                    val centerXPx = ringBoxSizePx.first / 2f
                    val centerYPx = ringBoxSizePx.second / 2f
                    for (effect in tapEffects) {
                        val age = (now - effect.createdAt).toFloat()
                        val life = (age / EFFECT_LIFETIME_MS).coerceIn(0f, 1f)
                        if (life >= 1f) continue
                        val yShift = -40f * life // dp-equivalent px at default density is close enough
                        Text(
                            text = "+1",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = dynamicAccent.copy(alpha = 1f - life),
                            modifier = Modifier
                                .align(Alignment.Center)
                                .offset {
                                    IntOffset(
                                        (effect.position.x - centerXPx).roundToInt(),
                                        (effect.position.y - centerYPx).roundToInt() + yShift.roundToInt(),
                                    )
                                },
                        )
                    }

                    when (phase) {
                        Phase.Countdown -> {
                            Text(
                                text = if (countdownValue > 0) countdownValue.toString() else "GO!",
                                style = MaterialTheme.typography.displayLarge,
                                fontWeight = FontWeight.Bold,
                                color = accentColor,
                            )
                        }
                        Phase.Running -> {
                            // Inside the ring: just the tap counter. The
                            // seconds:centiseconds timer lives at the top of
                            // the screen so it's always visible.
                            Text(
                                text = tapCount.toString(),
                                style = MaterialTheme.typography.displayLarge,
                                color = dynamicAccent,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Phase.Finished -> {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                                modifier = Modifier.padding(16.dp),
                            ) {
                                Column(
                                    modifier = Modifier.padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        text = if (achievedNewHighScore)
                                            "New High Score: $tapCount"
                                        else "Time's up!",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = accentColor,
                                        textAlign = TextAlign.Center,
                                    )
                                    Text(
                                        text = "You tapped $tapCount times",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    val effectiveHighScore = maxOf(highScore, tapCount)
                                    if (achievedNewHighScore) {
                                        Text(
                                            text = "New high score!",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = accentColor,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                    } else {
                                        Text(
                                            text = "High score: $effectiveHighScore",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                        )
                                    }
                                    if (pixelWasLocked &&
                                        effectiveHighScore >= com.twinlore.app.data.PIXEL_UNLOCK_TAP_SCORE
                                    ) {
                                        Text(
                                            text = "★ Pixel app icon unlocked!\nChoose it in Settings → Look & Feel.",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = accentColor,
                                            fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.Center,
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        OutlinedButton(onClick = onDismiss) { Text("Return") }
                                        Button(
                                            onClick = {
                                                tapCount = 0
                                                remainingMillis = TOTAL_MILLIS
                                                countdownValue = COUNTDOWN_SECONDS
                                                tapEffects.clear()
                                                burstText = null
                                                achievedNewHighScore = false
                                                phase = Phase.Countdown
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = accentColor,
                                            ),
                                        ) { Text("Play again") }
                                    }
                                }
                            }
                        }
                    }

                    // Milestone burst overlay — big number that scales up then
                    // fades. Drawn last so it renders on top of the counter.
                    burstText?.let { text ->
                        Text(
                            text = text,
                            style = MaterialTheme.typography.displayLarge,
                            fontWeight = FontWeight.Black,
                            color = dynamicAccent.copy(alpha = burstAlpha.value),
                            modifier = Modifier
                                .align(Alignment.Center)
                                .scale(burstScale.value),
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                contentAlignment = Alignment.TopEnd,
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }

            // High-score line — always visible so the user always knows the
            // number to beat (even while they're tapping). In portrait it sits
            // bottom-center below the ring; in landscape the vertically-centered
            // ring reaches the bottom, so it tucks into the bottom-left corner
            // (clear of the horizontally-centered ring) to stay readable.
            val displayedHighScore = maxOf(highScore, tapCount)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = if (isLandscape) 24.dp else 0.dp,
                        bottom = if (isLandscape) 24.dp else 40.dp,
                    ),
                contentAlignment = if (isLandscape) Alignment.BottomStart else Alignment.BottomCenter,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "High score:",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = displayedHighScore.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = accentColor,
                    )
                }
            }
        }
    }
}
