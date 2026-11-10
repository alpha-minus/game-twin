package com.twinlore.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.twinlore.app.R
import com.twinlore.app.data.AppIcon

/**
 * Full-screen splash overlay with animated logo and rotating loading messages.
 * Background color matches `@color/ic_launcher_background` (#3A096E) so it
 * tiles seamlessly with the Android 12+ system splash and the activity's
 * windowBackground.
 *
 * Used in two windows:
 *  - MainActivity, during `StartupGate.Loading` (DataStore read).
 *  - LibraryScreen, during the Coil image preload window.
 *
 * [preloadOverlayAlpha] drives the fade-out at the end of the preload phase;
 * pass `1f` when you just want it fully visible.
 */
@Composable
fun LibraryPreloadSplash(
    preloadOverlayAlpha: Float,
    logoRes: Int = R.mipmap.ic_launcher_foreground,
    bgColor: Long = 0xFF3A096EL,
    backdropIcon: com.twinlore.app.data.AppIcon? = null,
) {
    // Three-stage copy for longer loads: style-specific opening line → achievement-sync line
    // (per-style flavour) → a final "just a moment" line. Later stages only appear on slower
    // loads (e.g. when RA/Steam sync is in flight); fast loads tear the splash down first.
    val (styleMessage, messageColor) = remember(backdropIcon) { splashCopyFor(backdropIcon) }
    var phase by remember(backdropIcon) { mutableStateOf(0) }
    LaunchedEffect(backdropIcon) {
        kotlinx.coroutines.delay(SYNC_MESSAGE_DELAY_MS)
        phase = 1
        kotlinx.coroutines.delay(SYNC_MESSAGE_DELAY_MS)
        phase = 2
    }
    val message = when (phase) {
        0 -> styleMessage
        1 -> syncMessageFor(backdropIcon)
        else -> JUST_A_MOMENT_MESSAGE
    }

    // Gentle pulse on the logo
    val infiniteTransition = rememberInfiniteTransition(label = "splashPulse")
    val logoScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "logoPulse"
    )

    // Crossfade the message text
    val messageAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "msgAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .alpha(preloadOverlayAlpha)
            .background(Color(bgColor)),
        contentAlignment = Alignment.Center
    ) {
        // Looping thematic backdrop for the selected premium icon (behind the logo).
        backdropIcon?.let { SplashBackdrop(it, Modifier.fillMaxSize()) }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Premium/pixel logos are square framed icons: clip to a smooth
            // squircle and float them on an accent glow, so the splash shows a
            // polished rounded logo instead of a hard rectangular border.
            val framed = backdropIcon != null && backdropIcon != AppIcon.DEFAULT
            if (framed) {
                val glow = when (backdropIcon) {
                    AppIcon.GOLD -> Color(0xFFF7CE5B)
                    AppIcon.CYBER -> Color(0xFF3FE0F5)
                    AppIcon.DEMONIC -> Color(0xFFE21414)
                    AppIcon.ADVENTURE -> Color(0xFFCBD3DE)
                    AppIcon.NOIR -> Color(0xFFC8CDD6)
                    AppIcon.PIXEL -> Color(0xFFA9E6B2)
                    else -> Color.White
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.graphicsLayer(scaleX = logoScale, scaleY = logoScale),
                ) {
                    Canvas(modifier = Modifier.size(158.dp)) {
                        val r = size.minDimension * 0.5f
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(glow.copy(alpha = 0.55f), glow.copy(alpha = 0.16f), Color.Transparent),
                                center = center, radius = r,
                            ),
                            radius = r, center = center,
                        )
                    }
                    Image(
                        painter = painterResource(logoRes),
                        contentDescription = "Game Twin",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(128.dp)
                            .clip(RoundedCornerShape(percent = 24)),
                    )
                }
            } else {
                Image(
                    painter = painterResource(logoRes),
                    contentDescription = "Game Twin",
                    modifier = Modifier
                        .size(120.dp)
                        .graphicsLayer(scaleX = logoScale, scaleY = logoScale),
                )
            }
            Spacer(modifier = Modifier.height(if (framed) 8.dp else 24.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = messageColor.copy(alpha = 0.85f * messageAlpha),
                textAlign = TextAlign.Center
            )
        }
    }
}

/** Delay between each splash message stage (style → sync → "just a moment"). */
private const val SYNC_MESSAGE_DELAY_MS = 3000L

/** Final stage, shown for every style once the load has run past two stages. */
private const val JUST_A_MOMENT_MESSAGE = "Just a moment…"

/** Stage-2 achievement-sync line, per style. */
private fun syncMessageFor(icon: AppIcon?): String = when (icon) {
    AppIcon.DEMONIC -> "Channeling Cheevos…"
    AppIcon.GOLD -> "Syncing Achievements…"
    else -> "Syncing cheevos…"
}

/**
 * Per-icon opening loading line + a text colour drawn from the logo's dominant hue.
 * Each style phrases "vaults are loading" in its own voice; after a few seconds every
 * style switches to [SYNC_MESSAGE].
 */
private fun splashCopyFor(icon: AppIcon?): Pair<String, Color> = when (icon) {
    AppIcon.GOLD -> "Polishing vaults…" to Color(0xFFF7CE5B)
    AppIcon.CYBER -> "Initializing vaults…" to Color(0xFF56E6F7)
    AppIcon.DEMONIC -> "Summoning vaults…" to Color(0xFFF15A5A)
    AppIcon.ADVENTURE -> "Unearthing vaults…" to Color(0xFFEBC964)
    AppIcon.NOIR -> "Cracking the vaults…" to Color(0xFFCBD0D8)
    AppIcon.PIXEL -> "Inserting the coin…" to Color(0xFFB2ECC6)
    else -> "Loading vaults…" to Color.White
}
