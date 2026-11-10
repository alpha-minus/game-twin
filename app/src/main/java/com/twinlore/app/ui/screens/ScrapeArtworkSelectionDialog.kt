package com.twinlore.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.ImageLoader
import coil.request.CachePolicy
import com.twinlore.app.data.FocusColor
import com.twinlore.app.ui.LocalThemePalette
import com.twinlore.app.data.HapticFeedbackIntensity
import com.twinlore.app.data.LedTurnOffTime
import com.twinlore.app.ui.screens.SettingsViewModel
import com.twinlore.app.ui.utils.FeedbackEvent
import com.twinlore.app.ui.utils.runWithHapticAndLed
import androidx.compose.runtime.remember
import okhttp3.OkHttpClient
import okhttp3.Dispatcher
import java.util.concurrent.TimeUnit
import com.twinlore.app.ui.LocalCachedFocusColor

/**
 * Dialog for selecting scraped artwork from SteamGridDB
 * Shows a grid of thumbnails (grids, logos, heroes) for user selection
 */
@Composable
fun ScrapeArtworkSelectionDialog(
    artworkOptions: List<ArtworkOption>,
    isLoading: Boolean,
    errorMessage: String?,
    isAssigning: Boolean = false,
    onAddFromFile: (() -> Unit)? = null,
    onRemoveArtwork: (() -> Unit)? = null,
    onArtworkSelected: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Get theme palette, focus color, and haptic/LED from settings
    val view = LocalView.current
    val palette = LocalThemePalette.current
    val isDarkTheme = palette.isDark
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val focusColorState by settingsViewModel.focusColor.collectAsState(initial = FocusColor.CRIMSON)
    val focusColor = focusColorState.color
    // What the rings show: the detected accent when adaptive colour is on.
    // The palette match above is for this dialog's own tint; driving the LED
    // with it flipped a red vault's rings to its nearest palette entry.
    val ledColor = LocalCachedFocusColor.current?.value ?: focusColor
    val hapticFeedbackIntensity by settingsViewModel.hapticFeedbackIntensity.collectAsState(initial = HapticFeedbackIntensity.MEDIUM)
    val ledEnabled by settingsViewModel.ledEnabled.collectAsState(initial = true)
    val ledBrightness by settingsViewModel.ledBrightness.collectAsState(initial = 127)
    val ledSaturationBoost by settingsViewModel.ledSaturationBoost.collectAsState(initial = 0.75f)
    val ledTurnOffTime by settingsViewModel.ledTurnOffTime.collectAsState(initial = LedTurnOffTime.SEC_15)
    // Current grid display ratio — options whose ratio matches it are highlighted green.
    val currentGridRatio by settingsViewModel.gridDisplayRatio.collectAsState(initial = null)
    val targetRatioName = currentGridRatio?.displayName

    // Create optimized ImageLoader for parallel thumbnail loading (shared across all items)
    val context = LocalContext.current
    val imageLoader = remember {
        // Create OkHttpClient with higher concurrency for parallel image loading
        val dispatcher = Dispatcher().apply {
            maxRequests = 20 // Allow up to 20 concurrent requests (default is 5)
            maxRequestsPerHost = 20 // Allow up to 20 per host (default is 5)
        }
        val okHttpClient = OkHttpClient.Builder()
            .dispatcher(dispatcher)
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS) // Increased for slow server responses
            .writeTimeout(5, TimeUnit.SECONDS)
            .build()
        
        ImageLoader.Builder(context)
            .okHttpClient(okHttpClient)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .networkCachePolicy(CachePolicy.ENABLED)
            .respectCacheHeaders(false) // Ignore server cache headers for faster loading
            .build()
    }
    
    // Use theme palette colors
    val cardBackgroundColor = palette.background
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        val configuration = LocalConfiguration.current
        val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp

        Card(
            modifier = modifier.fillMaxSize(),
            shape = RoundedCornerShape(0.dp),
            colors = CardDefaults.cardColors(
                containerColor = cardBackgroundColor
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    // Fullscreen dialog draws edge-to-edge (the app hides the nav bar
                    // and shows transient bars); keep content inside the safe area so
                    // the scrollable list's viewport ends at the visible bottom and the
                    // last category row can be scrolled into view.
                    .safeDrawingPadding()
                    .padding(if (isLandscape) 12.dp else 16.dp)
            ) {
                // Header with close button in top-right
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Select Artwork",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    if (onAddFromFile != null) {
                        IconButton(
                            onClick = {
                                runWithHapticAndLed(view, hapticFeedbackIntensity, ledEnabled, ledColor, ledBrightness, ledSaturationBoost, ledTurnOffTime.inactivityMs, action = onAddFromFile)
                            },
                            enabled = !isAssigning,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add image from file",
                                tint = focusColor,
                            )
                        }
                    }
                    if (onRemoveArtwork != null) {
                        IconButton(
                            onClick = {
                                runWithHapticAndLed(view, hapticFeedbackIntensity, ledEnabled, ledColor, ledBrightness, ledSaturationBoost, ledTurnOffTime.inactivityMs, action = onRemoveArtwork)
                            },
                            enabled = !isAssigning,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Remove artwork",
                                tint = focusColor,
                            )
                        }
                    }
                    IconButton(
                        onClick = { runWithHapticAndLed(view, hapticFeedbackIntensity, ledEnabled, ledColor, ledBrightness, ledSaturationBoost, ledTurnOffTime.inactivityMs, event = FeedbackEvent.BACK, action = onDismiss) },
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = focusColor,
                        )
                    }
                }

                // Single-line note above the image rows (both orientations).
                Text(
                    text = "Note: If servers are slow, images may take time to appear",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                )

                // Content
                when {
                    isLoading -> {
                        // Loading state
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                CircularProgressIndicator(
                                    color = focusColor
                                )
                                Text(
                                    text = "Loading artwork options...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    errorMessage != null -> {
                        // Error state
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Text(
                                    text = "Error",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    text = errorMessage,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                    artworkOptions.isEmpty() -> {
                        // Empty state
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No artwork found",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    else -> {
                        // One horizontal row per artwork category (Local, Grid, Hero,
                        // Logo). Order preserves the within-category ratio ranking the
                        // ViewModel emitted.
                        val sections = remember(artworkOptions) {
                            val order = listOf("Local", "Grid", "Hero", "Logo")
                            val byType = artworkOptions.groupBy { it.type }
                            val known = order.mapNotNull { t ->
                                byType[t]?.takeIf { it.isNotEmpty() }?.let { t to it }
                            }
                            val extras = byType.filterKeys { it !in order }
                                .map { it.key to it.value }
                            known + extras
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .weight(1f)
                        ) {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
                                verticalArrangement = Arrangement.spacedBy(if (isLandscape) 10.dp else 16.dp),
                            ) {
                                sections.forEach { (type, options) ->
                                    item(key = "hdr-$type") {
                                        CategorySectionHeader(type = type, count = options.size)
                                    }
                                    item(key = "row-$type") {
                                        LazyRow(
                                            modifier = Modifier.fillMaxWidth(),
                                            contentPadding = PaddingValues(horizontal = 4.dp),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        ) {
                                            items(options, key = { it.url }) { option ->
                                                ArtworkOptionItem(
                                                    option = option,
                                                    imageLoader = imageLoader,
                                                    onClick = {
                                                        if (!isAssigning) {
                                                            onArtworkSelected(option.url)
                                                        }
                                                    },
                                                    enabled = !isAssigning,
                                                    targetRatioName = targetRatioName,
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Loading overlay when assigning
                            if (isAssigning) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.7f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        CircularProgressIndicator(
                                            color = focusColor
                                        )
                                        Text(
                                            text = "Assigning artwork...",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                
            }
        }
    }
}

/** Header above each category's horizontal row. */
@Composable
private fun CategorySectionHeader(type: String, count: Int) {
    Text(
        text = "$type ($count)",
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 6.dp),
    )
}

/**
 * A single artwork tile inside a category's horizontal row. Tile size is tuned
 * per category (box-art/local are tall, hero/logo are wide) and images use
 * ContentScale.Fit so nothing is cropped. The label shows just the aspect
 * ratio — the category is already conveyed by the row header, and local files
 * carry a LOCAL badge on the image itself.
 */
@Composable
private fun ArtworkOptionItem(
    option: ArtworkOption,
    imageLoader: ImageLoader,
    onClick: () -> Unit,
    enabled: Boolean = true,
    targetRatioName: String? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val (tileWidth, imageHeight) = when (option.type) {
        "Hero" -> 248.dp to 140.dp
        "Logo" -> 200.dp to 116.dp
        else -> 156.dp to 208.dp   // Grid / Local box art (tall)
    }

    Card(
        modifier = modifier
            .width(tileWidth)
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.6f),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Thumbnail image
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(imageHeight)
                    .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(option.thumbnailUrl ?: option.url)
                        .size(coil.size.Size(500, 500)) // Larger tiles → sharper thumbs
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        // Browse-only fetch — user picks ONE artwork, the other N-1
                        // thumbnails should not persist to image_cache. Same rationale
                        // as the VGMaps picker. Memory cache stays on for in-session
                        // re-renders (scroll up/down keeps thumbs warm).
                        .diskCachePolicy(CachePolicy.DISABLED)
                        .networkCachePolicy(CachePolicy.ENABLED)
                        .crossfade(false) // Disable crossfade for faster rendering
                        .build(),
                    contentDescription = "${option.type} ${option.index}",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                    imageLoader = imageLoader,
                    placeholder = androidx.compose.ui.res.painterResource(android.R.drawable.ic_menu_gallery),
                    error = androidx.compose.ui.res.painterResource(android.R.drawable.ic_menu_gallery)
                )
                if (option.type == "Local") {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(4.dp)
                            .background(Color(0xFF4CAF50), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = "LOCAL",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                        )
                    }
                }
            }

            // Label — just the aspect ratio (category is in the row header).
            // Turn green when it matches the user's current grid display ratio.
            val ratioLabel = formatRatioLabel(option.width, option.height)
            val fitsCurrentRatio = ratioLabel != null && ratioLabel == targetRatioName
            Text(
                text = ratioLabel ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = if (fitsCurrentRatio) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp),
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

/** Label an artwork by the SUPPORTED library ratio (GridDisplayRatio) it is
 *  closest to — clean integer names like "2:3"/"3:4" always, never a raw GCD
 *  reduction ("22:33") or a decimal. Null when dimensions are unknown so the
 *  caller can fall back to type + index. */
private fun formatRatioLabel(w: Int?, h: Int?): String? {
    if (w == null || h == null || w <= 0 || h <= 0) return null
    val ratio = w.toFloat() / h
    return com.twinlore.app.data.GridDisplayRatio.values()
        .minByOrNull { kotlin.math.abs(it.aspectRatio - ratio) }
        ?.displayName
}

