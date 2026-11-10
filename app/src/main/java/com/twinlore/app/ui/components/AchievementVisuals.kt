package com.twinlore.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Shared RetroAchievements / Steam completion visuals — the glossy gradient bar
 * fill and the glowing award knob shown at the end of a mastered / completed
 * bar. Styled to mirror the RetroAchievements site (gradient sheen + gold glow).
 *
 * Used by the library tile bar ([VaultGridItem]), the post-refresh showcase
 * overlay, and the RA / Steam achievement tabs so all four read consistently.
 */

// Base award colours (kept identical to the values the call sites used before).
val AchievementGold = Color(0xFFFFD700)
val AchievementSilver = Color(0xFFC0C0C0)

private val GoldBright = Color(0xFFFFE873)
private val GoldDeep = Color(0xFFE6A700)
private val SilverBright = Color(0xFFE8E8E8)
private val SilverDeep = Color(0xFF9AA0A6)

/**
 * Horizontal gradient for a completion-bar fill (deep → base → bright, left to
 * right) so the bar reads with a glossy sheen instead of a flat block.
 */
fun achievementBarBrush(mastered: Boolean): Brush =
    if (mastered) Brush.horizontalGradient(listOf(GoldDeep, AchievementGold, GoldBright))
    else Brush.horizontalGradient(listOf(SilverDeep, AchievementSilver, SilverBright))

/** Accent-tinted gradient fill for the single-colour Steam bar. */
fun accentBarBrush(accent: Color): Brush =
    Brush.horizontalGradient(
        listOf(lerp(accent, Color.Black, 0.18f), accent, lerp(accent, Color.White, 0.30f))
    )

/**
 * Glowing award knob. Footprint stays exactly [size] — the radial glow is drawn
 * behind and overflows the bounds (no extra layout space), so it can be dropped
 * at the end of a bar without shifting the layout. [filled] draws a solid
 * gradient disc (mastered / beaten-hardcore); otherwise a gradient ring
 * (completed / beaten-softcore).
 */
@Composable
fun GlowingAwardCircle(
    size: Dp,
    base: Color,
    bright: Color = base,
    deep: Color = base,
    filled: Boolean = true,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(size)
            .drawBehind {
                val radius = this.size.minDimension
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(base.copy(alpha = 0.55f), Color.Transparent),
                        center = center,
                        radius = radius
                    ),
                    radius = radius,
                    center = center
                )
            }
    ) {
        val coreBrush = Brush.linearGradient(listOf(bright, deep))
        if (filled) {
            Box(Modifier.size(size).clip(CircleShape).background(coreBrush))
        } else {
            Box(
                Modifier
                    .size(size)
                    .border(width = maxOf(size * 0.16f, 2.dp), brush = coreBrush, shape = CircleShape)
            )
        }
    }
}

/** Gold or silver award knob, picking the matching bright/deep gradient shades. */
@Composable
fun GlowingAwardCircle(
    size: Dp,
    mastered: Boolean,
    filled: Boolean = true,
    modifier: Modifier = Modifier
) {
    GlowingAwardCircle(
        size = size,
        base = if (mastered) AchievementGold else AchievementSilver,
        bright = if (mastered) GoldBright else SilverBright,
        deep = if (mastered) GoldDeep else SilverDeep,
        filled = filled,
        modifier = modifier
    )
}

/**
 * Glowing award badge for a RetroAchievements `highestAwardKind`: filled disc for
 * mastered / beaten-hardcore, gradient ring for completed / beaten-softcore; gold for
 * the mastered tier, silver for the beaten tier. Renders nothing for null/unknown.
 */
@Composable
fun HighestAwardIcon(highestAwardKind: String?, modifier: Modifier = Modifier, size: Dp = 20.dp) {
    when (highestAwardKind) {
        "mastered" -> GlowingAwardCircle(size = size, mastered = true, filled = true, modifier = modifier)
        "completed" -> GlowingAwardCircle(size = size, mastered = true, filled = false, modifier = modifier)
        "beaten-hardcore" -> GlowingAwardCircle(size = size, mastered = false, filled = true, modifier = modifier)
        "beaten-softcore" -> GlowingAwardCircle(size = size, mastered = false, filled = false, modifier = modifier)
        else -> {}
    }
}
