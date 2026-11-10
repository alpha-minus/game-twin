package com.twinlore.app.ui.screens.browser

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import kotlinx.coroutines.launch

/**
 * Big circular progress ring shown on the guide-set download mask. Double-tap
 * launches the tap game (`onDoubleTap`) so there's something to do while the
 * set downloads.
 *
 * [progress] is the completed fraction (0..1). When null the ring is
 * indeterminate (a sweeping arc rotates) — used during the Preparing phase
 * before any page has finished. [centerLabel] is drawn inside the ring.
 */
@Composable
internal fun DownloadProgressRing(
    progress: Float?,
    centerLabel: String,
    color: Color,
    onDoubleTap: () -> Unit,
    modifier: Modifier = Modifier,
    ringSize: Dp = 200.dp,
    strokeWidth: Dp = 14.dp,
    // Optional content rendered between the ring and the "double-tap" hint —
    // used by the single-page overlay to host the download status + reassurance
    // text so the bottom banner can be dropped. Empty (default) for the guide-set
    // ring, which keeps its own banner.
    belowRing: @Composable () -> Unit = {},
    // Travelling-dot "actively working" cue under the count. Useful for the
    // guide-set ring where the determinate arc can sit still between page
    // completions. Off for single-page downloads — the ring's own progress
    // animation already signals activity, so the dots are redundant noise.
    showProgressDots: Boolean = true,
) {
    // Single-tap pulse: brief symmetric breathe (mirrors the checklist ring).
    val scope = rememberCoroutineScope()
    val ringScale = remember { Animatable(1f) }
    val pulse = {
        scope.launch {
            ringScale.animateTo(1.08f, tween(260, easing = FastOutSlowInEasing))
            ringScale.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
        }
        Unit
    }

    // Indeterminate sweep rotation (only consulted when progress == null).
    val infinite = rememberInfiniteTransition(label = "dl-ring-indeterminate")
    val sweepStart by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing)),
        label = "dl-ring-rotation",
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Tap-game hint above the ring.
        Text(
            text = "Double-tap to play while you wait",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(14.dp))
        Box(
            modifier = Modifier
                .size(ringSize)
                .scale(ringScale.value)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { pulse() },
                        onDoubleTap = { onDoubleTap() },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = strokeWidth.toPx()
                val diameter = size.minDimension - stroke
                val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
                val arcSize = Size(diameter, diameter)
                // Faded background track.
                drawArc(
                    color = color.copy(alpha = 0.25f),
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke),
                )
                if (progress != null) {
                    if (progress > 0f) {
                        drawArc(
                            color = color,
                            startAngle = -90f,
                            sweepAngle = 360f * progress.coerceIn(0f, 1f),
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = stroke),
                        )
                    }
                } else {
                    // Indeterminate: a 90° arc rotates around the track.
                    drawArc(
                        color = color,
                        startAngle = sweepStart - 90f,
                        sweepAngle = 90f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = stroke),
                    )
                }
            }
            Text(
                text = centerLabel,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
        }
        // Animated dots under the count to signal active progress.
        if (showProgressDots) {
            Spacer(Modifier.height(10.dp))
            AnimatedProgressDots(color)
        }
        belowRing()
    }
}

/** Three dots with a travelling highlight — an "actively working" cue shown
 *  under the ring's count. Fixed width (always 3 dots) so it doesn't jitter. */
@Composable
private fun AnimatedProgressDots(color: Color) {
    val transition = rememberInfiniteTransition(label = "dl-dots")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
        label = "dl-dots-phase",
    )
    val active = phase.toInt().coerceIn(0, 2)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (i in 0..2) {
            Text(
                text = "•",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = color.copy(alpha = if (i == active) 1f else 0.3f),
            )
        }
    }
}
