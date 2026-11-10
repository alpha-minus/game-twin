package com.twinlore.app.ui.screens.checklist

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.twinlore.app.domain.ChecklistTab
import com.twinlore.app.ui.theme.ChecklistColorPalette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Single circular progress ring that toggles between two views on tap:
 *
 * **Overall** (default): segmented ring where every tab contributes an arc
 * proportional to its share of the vault's total item count, colored with the
 * tab's palette color, filled to that tab's completion ratio. Center shows
 * the vault-wide percent.
 *
 * **Active tab**: single arc in the active tab's palette color, filled to
 * the tab's own completion ratio. Center shows the tab's percent.
 *
 * Single tap toggles between the two views. Double tap fires [onDoubleTap].
 */
@Composable
fun ChecklistProgressRing(
    tabs: List<ChecklistTab>,
    completionByTabId: Map<String, Pair<Int, Int>>,
    activeTabId: String?,
    modifier: Modifier = Modifier,
    ringSize: Dp = 72.dp,
    strokeWidth: Dp = 6.dp,
    /** Hide the "X/Y items" caption under the ring: useful for the
     *  compact, in-collapsed-header variant where vertical space is tight. */
    showCaption: Boolean = true,
    /** Skip the single-tap pulse + "double-tap me!" hint. Disabled in
     *  compact contexts so the hint can't push layout around. */
    showHint: Boolean = true,
    onDoubleTap: () -> Unit = {},
) {
    val activeTab = tabs.firstOrNull { it.id == activeTabId }
    val (activeDone, activeTotal) = activeTabId?.let { completionByTabId[it] } ?: (0 to 0)
    val activePercent = if (activeTotal == 0) 0 else (activeDone * 100) / activeTotal

    val overallDone = completionByTabId.values.sumOf { it.first }
    val overallTotal = completionByTabId.values.sumOf { it.second }
    val overallPercent = if (overallTotal == 0) 0 else (overallDone * 100) / overallTotal

    // Toggle between overall and active-tab display on single tap.
    var showActiveTab by rememberSaveable { mutableStateOf(false) }

    // Single-tap hint: a brief pulse + "double-tap me!" caption. Teaches the
    // easter-egg gesture without the noise of an animation on every tab change.
    val ringScale = remember { Animatable(1f) }
    var hintVisible by remember { mutableStateOf(false) }
    var lastTapAt by remember { mutableStateOf(0L) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(lastTapAt) {
        if (lastTapAt == 0L) return@LaunchedEffect
        hintVisible = true
        delay(1_800)
        hintVisible = false
    }

    val onSingleTap = {
        showActiveTab = !showActiveTab
        lastTapAt = System.currentTimeMillis()
        scope.launch {
            // Subtle, symmetric breathe: small peak, ease-in-out both ways,
            // slightly longer decay for an organic "settle" feel. Animatable
            // auto-cancels in-flight animations so rapid re-taps look clean.
            ringScale.animateTo(
                targetValue = 1.08f,
                animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
            )
            ringScale.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
            )
        }
        Unit
    }

    val tabColor = activeTab?.color?.let {
        ChecklistColorPalette.colors[it.coerceIn(0, 11)]
    } ?: MaterialTheme.colorScheme.primary

    // Hint sits to the LEFT of the ring (animates in/out horizontally)
    // so the ring stays visually anchored to its right-edge home
    // instead of getting pushed down by a row beneath it.
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End,
    ) {
        AnimatedVisibility(
            visible = hintVisible && showHint,
            enter = fadeIn() + expandHorizontally(),
            exit = fadeOut() + shrinkHorizontally(),
        ) {
            Text(
                text = "double-tap me!",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f),
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(end = 8.dp),
            )
        }
        Box(modifier = Modifier.scale(ringScale.value)) {
            if (showActiveTab) {
                RingWithCaption(
                    percent = activePercent,
                    caption = "$activeDone/$activeTotal",
                    textColor = tabColor,
                    ringSize = ringSize,
                    showCaption = showCaption,
                    onSingleTap = onSingleTap,
                    onDoubleTap = onDoubleTap,
                ) {
                    drawSingleArc(
                        color = tabColor,
                        filledRatio = if (activeTotal == 0) 0f else activeDone.toFloat() / activeTotal,
                        strokeWidth = strokeWidth,
                    )
                }
            } else {
                RingWithCaption(
                    percent = overallPercent,
                    caption = "$overallDone/$overallTotal",
                    textColor = MaterialTheme.colorScheme.onBackground,
                    ringSize = ringSize,
                    showCaption = showCaption,
                    onSingleTap = onSingleTap,
                    onDoubleTap = onDoubleTap,
                ) {
                    drawSegmentedOverall(
                        tabs = tabs,
                        completionByTabId = completionByTabId,
                        strokeWidth = strokeWidth,
                        overallTotal = overallTotal,
                    )
                }
            }
        }
    }
}

@Composable
private fun RingWithCaption(
    percent: Int,
    caption: String,
    textColor: Color,
    ringSize: Dp,
    showCaption: Boolean = true,
    onSingleTap: () -> Unit = {},
    onDoubleTap: () -> Unit = {},
    arcDrawer: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(ringSize)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onSingleTap() },
                        onDoubleTap = { onDoubleTap() },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.size(ringSize)) {
                arcDrawer()
            }
            // Percent text adapts to the *ring* size (not caption visibility):
            //  • Tiny rings (≤ 36 dp) use labelSmall + no `%` so "100" fits
            //    inside a 32 dp ring without touching the stroke.
            //  • Larger rings keep labelLarge + "%" for legibility.
            val isTinyRing = ringSize <= 36.dp
            val percentStyle = if (isTinyRing) {
                MaterialTheme.typography.labelSmall
            } else {
                MaterialTheme.typography.labelLarge
            }
            val percentText = if (isTinyRing) percent.toString() else "$percent%"
            Text(
                text = percentText,
                style = percentStyle,
                color = textColor,
                fontWeight = FontWeight.SemiBold,
            )
        }
        if (showCaption) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = caption,
                style = MaterialTheme.typography.labelSmall,
                color = textColor.copy(alpha = 0.85f),
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSegmentedOverall(
    tabs: List<ChecklistTab>,
    completionByTabId: Map<String, Pair<Int, Int>>,
    strokeWidth: Dp,
    overallTotal: Int,
) {
    val stroke = strokeWidth.toPx()
    val diameter = size.minDimension - stroke
    val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
    val arcSize = Size(diameter, diameter)

    if (overallTotal == 0 || tabs.isEmpty()) {
        drawArc(
            color = Color.White.copy(alpha = 0.15f),
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = stroke),
        )
        return
    }

    var cursor = -90f
    for (tab in tabs) {
        val (done, total) = completionByTabId[tab.id] ?: continue
        if (total == 0) continue
        val tabSweep = (total.toFloat() / overallTotal) * 360f
        val filledSweep = tabSweep * (done.toFloat() / total)
        val color = ChecklistColorPalette.colors[tab.color.coerceIn(0, 11)]

        drawArc(
            color = color.copy(alpha = 0.25f),
            startAngle = cursor,
            sweepAngle = tabSweep,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = stroke),
        )
        if (filledSweep > 0f) {
            drawArc(
                color = color,
                startAngle = cursor,
                sweepAngle = filledSweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke),
            )
        }
        cursor += tabSweep
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSingleArc(
    color: Color,
    filledRatio: Float,
    strokeWidth: Dp,
) {
    val stroke = strokeWidth.toPx()
    val diameter = size.minDimension - stroke
    val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
    val arcSize = Size(diameter, diameter)

    drawArc(
        color = color.copy(alpha = 0.25f),
        startAngle = -90f,
        sweepAngle = 360f,
        useCenter = false,
        topLeft = topLeft,
        size = arcSize,
        style = Stroke(width = stroke),
    )
    if (filledRatio > 0f) {
        drawArc(
            color = color,
            startAngle = -90f,
            sweepAngle = 360f * filledRatio.coerceAtMost(1f),
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = stroke),
        )
    }
}
