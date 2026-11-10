package com.twinlore.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Feature tips displayed on guide loading screens, rotating every 5 seconds.
 * Each tip highlights a useful but non-obvious app feature.
 */
object LoadingTips {

    val tips: List<String> = listOf(
        // Scroll markers
        "Tip: Hold on text to save a scroll marker. Jump back with L1/R1.",
        // Quick search
        "Tip: Hold on text to search for it online or on YouTube.",
        // YouTube links
        "Tip: Save YouTube pages as link bookmarks to keep your progress.",
        // YouTube cookies
        "Tip: Enable cookies or sign in to continue YouTube videos where you left off.",
        // Auto TOC
        "Tip: Table of Contents is auto-detected for text guides as well.",
        // Scroll helper
        "Tip: No TOC found? A quick scroll helper bar is displayed instead.",
        // ASCII blocks
        "Tip: ASCII art, maps, and tables are auto-detected. Tap to enlarge.",
        // Save as link
        "Tip: Save any page as a link bookmark for quick access later.",
        // Hold image to save
        "Tip: Hold on an image in the browser to save it as a map or cover art.",
        // Screenshots
        "Tip: Capture your game screen directly into the vault with the screenshot button.",
        // Themes & fonts
        "Tip: Change color themes and fonts in Settings.",
        // Focus color
        "Tip: Focus color is auto-detected from cover art and applied to the UI.",
        // Focus color LEDs
        "Tip: Focus color also controls joystick LEDs on supported devices.",
        // Achievements
        "Tip: Add your RetroAchievements or Steam username in Settings to enable the achievements tab.",
        // Achievements to checklist
        "Tip: Hold on an achievement to turn it into a trackable checklist.",
        // Checklist markdown
        "Tip: Import checklists via copy-paste in markdown format.",
        // Checklist AI
        "Tip: Use AI to generate checklists from your existing guides.",
        // Checklist progress
        "Tip: Double-tap on a checklist progress ring and see what happens.",
        // Scroll markers from text
        "Tip: Save your current text selection as a scroll marker for quick navigation.",
        // Offline saving
        "Tip: Offline saved guides need no internet. Saved links still do.",
        // Landing page
        "Tip: Customize landing page search templates to match your workflow.",
        // Map markers
        "Tip: Hold on a point on an image to add a marker.",
        // Haptic feedback
        "Tip: Adjust haptic feedback intensity in Settings.",
        // Library sync
        "Tip: Sync your game library over Wi-Fi using the sync button in the library.",
        // Cover art
        "Tip: Hold on a library item for more cover art scraping options.",
        // RetroArch / ES-DE
        "Tip: Connect RetroArch or ES-DE in Settings to auto-detect launched games.",
        // Link bookmarks
        "Tip: Access your link bookmarks from the search bar or landing page.",
        // Search history
        "Tip: Recent searches are saved per game for quick re-runs.",
        // PDF bookmarks
        "Tip: Bookmark specific pages in PDF guides for quick navigation.",
        // Art display
        "Tip: Switch between Fit, Crop, or Fill artwork modes in Settings.",
        // Grid ratio
        "Tip: Adjust library grid ratio and size in Settings.",
        // LED control
        "Tip: On AYN devices, control joystick LED color and brightness from Settings.",
    )
}

/** How long each [LoadingWithTips] tip, and each staged status line, stays on screen. */
private const val LOADING_STEP_MS = 5000L

/**
 * Status line for splitting a big text guide into pages: an explanation first, then two
 * acknowledgements at five-second intervals. The last one stays put however long the job runs.
 */
val TEXT_GUIDE_CONVERSION_STATUS = listOf(
    "This guide is big!",
    "Working on it...",
    "Just a moment",
)

/**
 * A loading indicator with a title and (optionally) rotating feature tips underneath. Tips
 * crossfade every 5 seconds. The list is shuffled per session so tips feel fresh. Set
 * [showTips] = false for a spinner-only display (short-lived overlays where tips just create
 * visual noise).
 *
 * [statusMessages] replaces the fixed "Loading" title with a sequence that advances every 5
 * seconds and then STAYS on the last entry — for work that is legitimately slow, where a static
 * word reads as a hang. Unlike tips it does not cycle: going back to the first message after the
 * last would suggest the job restarted.
 */
@Composable
fun LoadingWithTips(
    focusColor: Color,
    modifier: Modifier = Modifier,
    showTips: Boolean = true,
    statusMessages: List<String> = emptyList(),
) {
    val shuffledTips = remember { LoadingTips.tips.shuffled() }
    var tipIndex by remember { mutableStateOf(0) }
    var statusIndex by remember { mutableStateOf(0) }

    if (showTips) {
        LaunchedEffect(Unit) {
            while (true) {
                kotlinx.coroutines.delay(LOADING_STEP_MS)
                tipIndex = (tipIndex + 1) % shuffledTips.size
            }
        }
    }
    if (statusMessages.size > 1) {
        LaunchedEffect(statusMessages) {
            statusIndex = 0
            while (statusIndex < statusMessages.lastIndex) {
                kotlinx.coroutines.delay(LOADING_STEP_MS)
                statusIndex++
            }
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.padding(horizontal = 32.dp),
    ) {
        CircularProgressIndicator(
            color = focusColor,
            strokeWidth = 3.dp,
            modifier = Modifier.size(36.dp),
        )
        Spacer(modifier = Modifier.height(16.dp))
        AnimatedContent(
            targetState = statusMessages.getOrNull(statusIndex) ?: "Loading",
            transitionSpec = {
                fadeIn(animationSpec = androidx.compose.animation.core.tween(400)) togetherWith
                    fadeOut(animationSpec = androidx.compose.animation.core.tween(400))
            },
            label = "loading-status",
        ) { status ->
            Text(
                text = status,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
        }
        if (showTips) {
            Spacer(modifier = Modifier.height(12.dp))
            AnimatedContent(
                targetState = tipIndex,
                transitionSpec = {
                    fadeIn(animationSpec = androidx.compose.animation.core.tween(400)) togetherWith
                        fadeOut(animationSpec = androidx.compose.animation.core.tween(400))
                },
                label = "loading-tip",
            ) { index ->
                Text(
                    text = shuffledTips[index],
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
