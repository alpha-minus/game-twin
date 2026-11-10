package com.twinlore.app.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.twinlore.app.data.HapticFeedbackIntensity
import com.twinlore.app.data.LedTurnOffTime
import com.twinlore.app.domain.MarkerStyle
import com.twinlore.app.domain.MapMarker
import com.twinlore.app.ui.navigation.NavigableMenuItem
import java.util.UUID
import android.util.Log
import androidx.hilt.navigation.compose.hiltViewModel
import com.twinlore.app.data.FocusColor
import com.twinlore.app.ui.LocalThemePalette
import com.twinlore.app.ui.screens.SettingsViewModel
import androidx.compose.runtime.collectAsState
import com.twinlore.app.ui.LocalCachedFocusColor
import com.twinlore.app.ui.theme.getFocusContainer
import com.twinlore.app.ui.utils.performHapticFeedback
import com.twinlore.app.ui.utils.FeedbackEvent
import com.twinlore.app.ui.utils.runWithHapticAndLed

/**
 * Utility functions for coordinate transformations between screen and normalized coordinates
 */
object MapMarkerCoordinateUtils {
    /**
     * Convert screen coordinates to normalized coordinates (0.0-1.0)
     * Accounts for ContentScale.Fit and current zoom/pan state
     */
    fun screenToNormalized(
        screenX: Float,
        screenY: Float,
        screenWidth: Float,
        screenHeight: Float,
        imageWidth: Float,
        imageHeight: Float,
        zoom: Float,
        offsetX: Float,
        offsetY: Float
    ): Pair<Float, Float> {
        // Calculate visible image bounds with ContentScale.Fit
        val imageAspect = imageWidth / imageHeight
        val screenAspect = screenWidth / screenHeight
        
        val (visibleImageWidth, visibleImageHeight) = if (imageAspect > screenAspect) {
            // Image is wider - fit to width
            Pair(screenWidth, screenWidth / imageAspect)
        } else {
            // Image is taller - fit to height
            Pair(screenHeight * imageAspect, screenHeight)
        }
        
        // Account for zoom and pan
        val centerX = screenWidth / 2f
        val centerY = screenHeight / 2f
        
        // Convert screen coordinates to image-relative coordinates
        val imageRelativeX = (screenX - centerX - offsetX) / zoom
        val imageRelativeY = (screenY - centerY - offsetY) / zoom
        
        // Convert to normalized (0.0-1.0)
        val normalizedX = (imageRelativeX + visibleImageWidth / 2f) / visibleImageWidth
        val normalizedY = (imageRelativeY + visibleImageHeight / 2f) / visibleImageHeight
        
        return Pair(
            normalizedX.coerceIn(0f, 1f),
            normalizedY.coerceIn(0f, 1f)
        )
    }
    
    /**
     * Convert normalized coordinates to screen coordinates
     */
    fun normalizedToScreen(
        normalizedX: Float,
        normalizedY: Float,
        screenWidth: Float,
        screenHeight: Float,
        imageWidth: Float,
        imageHeight: Float,
        zoom: Float,
        offsetX: Float,
        offsetY: Float
    ): Offset {
        // Calculate visible image bounds with ContentScale.Fit
        val imageAspect = imageWidth / imageHeight
        val screenAspect = screenWidth / screenHeight
        
        val (visibleImageWidth, visibleImageHeight) = if (imageAspect > screenAspect) {
            Pair(screenWidth, screenWidth / imageAspect)
        } else {
            Pair(screenHeight * imageAspect, screenHeight)
        }
        
        val centerX = screenWidth / 2f
        val centerY = screenHeight / 2f
        
        // Convert normalized to image-relative
        val imageRelativeX = (normalizedX - 0.5f) * visibleImageWidth
        val imageRelativeY = (normalizedY - 0.5f) * visibleImageHeight
        
        // Apply zoom and pan
        val screenX = centerX + imageRelativeX * zoom + offsetX
        val screenY = centerY + imageRelativeY * zoom + offsetY
        
        return Offset(screenX, screenY)
    }
    
    /**
     * Calculate offsetX and offsetY to center a normalized point on screen at a given zoom level
     * @return Pair of (offsetX, offsetY) to center the marker
     */
    fun calculateOffsetToCenter(
        normalizedX: Float,
        normalizedY: Float,
        screenWidth: Float,
        screenHeight: Float,
        imageWidth: Float,
        imageHeight: Float,
        zoom: Float
    ): Pair<Float, Float> {
        // Calculate visible image bounds with ContentScale.Fit
        val imageAspect = imageWidth / imageHeight
        val screenAspect = screenWidth / screenHeight
        
        val (visibleImageWidth, visibleImageHeight) = if (imageAspect > screenAspect) {
            Pair(screenWidth, screenWidth / imageAspect)
        } else {
            Pair(screenHeight * imageAspect, screenHeight)
        }
        
        // Convert normalized to image-relative coordinates
        val imageRelativeX = (normalizedX - 0.5f) * visibleImageWidth
        val imageRelativeY = (normalizedY - 0.5f) * visibleImageHeight
        
        // Calculate offset to center this point on screen
        // Similar to double-tap zoom: offset = -pointInImage * scale
        val offsetX = -imageRelativeX * zoom
        val offsetY = -imageRelativeY * zoom
        
        return Pair(offsetX, offsetY)
    }
}

/**
 * Overlay that renders markers on the map
 */
@Composable
fun MapMarkerOverlay(
    markers: List<MapMarker>,
    screenWidth: Float,
    screenHeight: Float,
    imageWidth: Float,
    imageHeight: Float,
    zoom: Float,
    offsetX: Float,
    offsetY: Float,
    onMarkerTap: (MapMarker) -> Unit,
    modifier: Modifier = Modifier
) {
    // Focus color and theme from settings. Not a ViewModel: this overlay is
    // also drawn in the fold panel's window, which has no activity for
    // hiltViewModel() to scope one to. See AppUiSettings.
    val focusColorState by com.twinlore.app.ui.rememberAppUiSettings().focusColor
        .collectAsState(initial = FocusColor.CRIMSON)
    val cachedFocusColor = LocalCachedFocusColor.current?.value
    val focusColor = cachedFocusColor ?: focusColorState.color
    val paletteOverlay = LocalThemePalette.current
    val isDarkTheme = paletteOverlay.isDark

    val density = LocalDensity.current
    val markerSizePx = with(density) { 40.dp.toPx() } // Increased from 32dp to 40dp for better visibility
    
    // CRITICAL: Make overlay truly non-interactive so gestures pass through to underlying image
    // Do NOT add any pointer handlers to the Box - a Box without handlers allows events to pass through
    // Only the individual marker icons are clickable, and they're small so they won't block most gestures
    Box(
        modifier = modifier.fillMaxSize()
    ) {
        markers.forEach { marker ->
            val screenPos = MapMarkerCoordinateUtils.normalizedToScreen(
                marker.normalizedX,
                marker.normalizedY,
                screenWidth,
                screenHeight,
                imageWidth,
                imageHeight,
                zoom,
                offsetX,
                offsetY
            )
            
            // Draw marker at calculated position
            // Anchor point is bottom-center
            val markerIcon = when (marker.style) {
                MarkerStyle.PIN -> Icons.Default.Place
                MarkerStyle.FLAG -> Icons.Default.Flag
                MarkerStyle.STAR -> Icons.Default.RadioButtonUnchecked // Circle icon
                MarkerStyle.CROSSHAIR -> Icons.Default.Person // Person icon
                null -> Icons.Default.Place
            }
            
            // Only make the marker itself clickable, not the entire overlay
            Box(
                modifier = Modifier
                    .offset(
                        x = with(density) { (screenPos.x - markerSizePx / 2).toDp() },
                        y = with(density) { (screenPos.y - markerSizePx).toDp() }
                    )
                    .size(with(density) { markerSizePx.toDp() })
                    .clickable { onMarkerTap(marker) }
            ) {
                // Contrasting background circle for better visibility
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            color = if (isDarkTheme) {
                                androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.5f)
                            } else {
                                androidx.compose.ui.graphics.Color.White.copy(alpha = 0.5f)
                            },
                            shape = CircleShape
                        )
                )
                
                // Icon on top of background
                Icon(
                    imageVector = markerIcon,
                    contentDescription = "Marker ${marker.id}",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(6.dp), // Padding to make icon slightly smaller than background
                    tint = focusColor
                )
            }
        }
    }
}

/**
 * Dialog for selecting marker style on long-press
 */
@Composable
fun MarkerStyleSelectionDialog(
    onStyleSelected: (MarkerStyle) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Get focus color, haptic/LED, and system theme from settings
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val focusColorState by settingsViewModel.focusColor.collectAsState(initial = FocusColor.CRIMSON)
    val focusColor = focusColorState.color
    val view = LocalView.current
    val hapticFeedbackIntensity by settingsViewModel.hapticFeedbackIntensity.collectAsState(initial = HapticFeedbackIntensity.MEDIUM)
    val ledEnabled by settingsViewModel.ledEnabled.collectAsState(initial = true)
    val ledBrightness by settingsViewModel.ledBrightness.collectAsState(initial = 127)
    val ledSaturationBoost by settingsViewModel.ledSaturationBoost.collectAsState(initial = 0.75f)
    val ledTurnOffTime by settingsViewModel.ledTurnOffTime.collectAsState(initial = LedTurnOffTime.SEC_15)
    val paletteStyle = LocalThemePalette.current
    val isDarkTheme = paletteStyle.isDark
    val backgroundColor = MaterialTheme.colorScheme.surface

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = modifier
                .fillMaxWidth(0.7f)
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = backgroundColor
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Select Marker Style",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    MarkerStyleButton(
                        style = MarkerStyle.PIN,
                        icon = Icons.Default.Place,
                        focusColor = focusColor,
                        onClick = { runWithHapticAndLed(view, hapticFeedbackIntensity, ledEnabled, focusColor, ledBrightness, ledSaturationBoost, ledTurnOffTime.inactivityMs) { onStyleSelected(MarkerStyle.PIN) } }
                    )
                    MarkerStyleButton(
                        style = MarkerStyle.FLAG,
                        icon = Icons.Default.Flag,
                        focusColor = focusColor,
                        onClick = { runWithHapticAndLed(view, hapticFeedbackIntensity, ledEnabled, focusColor, ledBrightness, ledSaturationBoost, ledTurnOffTime.inactivityMs) { onStyleSelected(MarkerStyle.FLAG) } }
                    )
                    MarkerStyleButton(
                        style = MarkerStyle.STAR,
                        icon = Icons.Default.RadioButtonUnchecked, // Beacon icon
                        focusColor = focusColor,
                        onClick = { runWithHapticAndLed(view, hapticFeedbackIntensity, ledEnabled, focusColor, ledBrightness, ledSaturationBoost, ledTurnOffTime.inactivityMs) { onStyleSelected(MarkerStyle.STAR) } }
                    )
                    MarkerStyleButton(
                        style = MarkerStyle.CROSSHAIR,
                        icon = Icons.Default.Person, // Person icon
                        focusColor = focusColor,
                        onClick = { runWithHapticAndLed(view, hapticFeedbackIntensity, ledEnabled, focusColor, ledBrightness, ledSaturationBoost, ledTurnOffTime.inactivityMs) { onStyleSelected(MarkerStyle.CROSSHAIR) } }
                    )
                }
                
                TextButton(
                    onClick = { runWithHapticAndLed(view, hapticFeedbackIntensity, ledEnabled, focusColor, ledBrightness, ledSaturationBoost, ledTurnOffTime.inactivityMs, event = FeedbackEvent.BACK, action = onDismiss) },
                    modifier = Modifier.padding(top = 16.dp),
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = focusColor
                    )
                ) {
                    Text("Cancel")
                }
            }
        }
    }
}

@Composable
private fun MarkerStyleButton(
    style: MarkerStyle,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    focusColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = icon,
            contentDescription = style.displayName,
            modifier = Modifier.size(48.dp),
            tint = focusColor
        )
    }
}

/**
 * Horizontal carousel for markers in top bar
 */
@Composable
fun MarkerCarousel(
    markers: List<MapMarker>,
    @Suppress("UNUSED_PARAMETER") maxMarkers: Int = 100, // Kept for API consistency
    onMarkerTap: (MapMarker) -> Unit,
    onMarkerLongPress: (MapMarker) -> Unit,
    modifier: Modifier = Modifier
) {
    // Get focus color and haptic/LED from settings
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val focusColorState by settingsViewModel.focusColor.collectAsState(initial = FocusColor.CRIMSON)
    val cachedFocusColor = LocalCachedFocusColor.current?.value
    val focusColor = cachedFocusColor ?: focusColorState.color
    val view = LocalView.current
    val hapticFeedbackIntensity by settingsViewModel.hapticFeedbackIntensity.collectAsState(initial = HapticFeedbackIntensity.MEDIUM)
    val ledEnabled by settingsViewModel.ledEnabled.collectAsState(initial = true)
    val ledBrightness by settingsViewModel.ledBrightness.collectAsState(initial = 127)
    val ledSaturationBoost by settingsViewModel.ledSaturationBoost.collectAsState(initial = 0.75f)
    val ledTurnOffTime by settingsViewModel.ledTurnOffTime.collectAsState(initial = LedTurnOffTime.SEC_15)

    // Only show markers that actually exist (not empty slots)
    val carouselInteraction = remember { MutableInteractionSource() }
    val carouselFocused by carouselInteraction.collectIsFocusedAsState()
    LazyRow(
        modifier = modifier
            .focusable(interactionSource = carouselInteraction)
            .then(
                if (carouselFocused) Modifier.border(2.dp, focusColor, androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
                else Modifier
            ),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 8.dp)
    ) {
        itemsIndexed(markers) { index, marker ->
            MarkerCarouselItem(
                index = index + 1,
                marker = marker,
                focusColor = focusColor,
                onClick = {
                    runWithHapticAndLed(view, hapticFeedbackIntensity, ledEnabled, focusColor, ledBrightness, ledSaturationBoost, ledTurnOffTime.inactivityMs) {
                        Log.d("MarkerCarousel", "Carousel item $index tapped, marker: ${marker.id}")
                        onMarkerTap(marker)
                    }
                },
                onLongPress = { 
                    Log.d("MarkerCarousel", "Carousel item $index long-pressed, marker: ${marker.id}")
                    onMarkerLongPress(marker) 
                }
            )
        }
    }
}

@Composable
private fun MarkerCarouselItem(
    index: Int,
    marker: MapMarker,
    focusColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    // 40dp in landscape so the carousel chips don't make the image top bar taller
    // than the guide-group / browser bars (~40dp) it stacks with; a 24dp icon still
    // has a comfortable halo in a 40dp circle.
    val itemSize = if (isLandscape) 40.dp else 28.dp
    val iconSize = if (isLandscape) 24.dp else 16.dp
    val markerIcon = marker.style?.let {
        when (it) {
            MarkerStyle.PIN -> Icons.Default.Place
            MarkerStyle.FLAG -> Icons.Default.Flag
            MarkerStyle.STAR -> Icons.Default.RadioButtonUnchecked
            MarkerStyle.CROSSHAIR -> Icons.Default.Person
        }
    } ?: Icons.Default.Place

    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val context = androidx.compose.ui.platform.LocalContext.current
    val view = LocalView.current
    val settingsVm: SettingsViewModel = hiltViewModel()
    val focusSoundEnabled by settingsVm.focusSoundEnabled.collectAsState(initial = true)
    val hapticIntensity by settingsVm.hapticFeedbackIntensity.collectAsState(initial = HapticFeedbackIntensity.MEDIUM)
    val ledEnabled by settingsVm.ledEnabled.collectAsState(initial = true)
    val ledBrightness by settingsVm.ledBrightness.collectAsState(initial = 127)
    val ledSaturationBoost by settingsVm.ledSaturationBoost.collectAsState(initial = 0.75f)
    val ledTurnOffTime by settingsVm.ledTurnOffTime.collectAsState(initial = LedTurnOffTime.SEC_15)

    androidx.compose.runtime.LaunchedEffect(isFocused) {
        if (isFocused) {
            view.performHapticFeedback(hapticIntensity)
            com.twinlore.app.ui.utils.FeedbackManager.playSound(context, com.twinlore.app.ui.utils.FeedbackEvent.FOCUS, focusSoundEnabled)
            com.twinlore.app.thor.led.ThorLedManager.setColor(focusColor, ledEnabled, ledBrightness, ledSaturationBoost, ledTurnOffTime.inactivityMs)
        }
    }

    Box(
        modifier = modifier
            .size(itemSize)
            .focusable(interactionSource = interactionSource)
            .onKeyEvent { keyEvent ->
                val nativeKeyEvent = keyEvent.nativeKeyEvent
                if (nativeKeyEvent.action == android.view.KeyEvent.ACTION_UP) {
                    when (nativeKeyEvent.keyCode) {
                        android.view.KeyEvent.KEYCODE_DPAD_CENTER,
                        android.view.KeyEvent.KEYCODE_BUTTON_A,
                        android.view.KeyEvent.KEYCODE_ENTER -> { onClick(); true }
                        else -> false
                    }
                } else if (nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN) {
                    nativeKeyEvent.keyCode in listOf(
                        android.view.KeyEvent.KEYCODE_DPAD_CENTER,
                        android.view.KeyEvent.KEYCODE_BUTTON_A,
                        android.view.KeyEvent.KEYCODE_ENTER
                    )
                } else false
            }
            .background(
                color = focusColor.getFocusContainer(),
                shape = CircleShape
            )
            .then(
                if (isFocused) Modifier.border(2.dp, focusColor, CircleShape)
                else Modifier
            )
            .pointerInput(marker.id) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { onLongPress() }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = markerIcon,
            contentDescription = "Marker $index",
            modifier = Modifier.size(iconSize),
            tint = focusColor
        )
    }
}

/**
 * Management menu for marker operations
 */
@Composable
fun MarkerManagementMenu(
    marker: MapMarker,
    onDelete: () -> Unit,
    onChangeStyle: () -> Unit,
    onDeleteAll: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Get focus color, haptic/LED, and system theme from settings
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val focusColorState by settingsViewModel.focusColor.collectAsState(initial = FocusColor.CRIMSON)
    val cachedFocusColor = LocalCachedFocusColor.current?.value
    val focusColor = cachedFocusColor ?: focusColorState.color
    val view = LocalView.current
    val hapticFeedbackIntensity by settingsViewModel.hapticFeedbackIntensity.collectAsState(initial = HapticFeedbackIntensity.MEDIUM)
    val ledEnabled by settingsViewModel.ledEnabled.collectAsState(initial = true)
    val ledBrightness by settingsViewModel.ledBrightness.collectAsState(initial = 127)
    val ledSaturationBoost by settingsViewModel.ledSaturationBoost.collectAsState(initial = 0.75f)
    val ledTurnOffTime by settingsViewModel.ledTurnOffTime.collectAsState(initial = LedTurnOffTime.SEC_15)
    val paletteMgmt = LocalThemePalette.current
    val isDarkTheme = paletteMgmt.isDark
    val backgroundColor = MaterialTheme.colorScheme.surface

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = modifier
                .fillMaxWidth(0.7f)
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = backgroundColor
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Marker Options",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                // Show marker style icon
                val markerIcon = marker.style?.let {
                    when (it) {
                        MarkerStyle.PIN -> Icons.Default.Place
                        MarkerStyle.FLAG -> Icons.Default.Flag
                        MarkerStyle.STAR -> Icons.Default.RadioButtonUnchecked // Circle icon
                        MarkerStyle.CROSSHAIR -> Icons.Default.Person // Person icon
                    }
                } ?: Icons.Default.Place
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = markerIcon,
                        contentDescription = "Marker style",
                        modifier = Modifier.size(24.dp),
                        tint = focusColor
                    )
                    Text(
                        text = "Style: ${marker.style?.displayName ?: "Unknown"}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                
                NavigableMenuItem(
                    text = "Delete Marker",
                    onClick = {
                        onDelete()
                        onDismiss()
                    },
                    focusId = "marker_delete"
                )
                
                NavigableMenuItem(
                    text = "Change Style",
                    onClick = {
                        onChangeStyle()
                        onDismiss()
                    },
                    focusId = "marker_style"
                )
                
                NavigableMenuItem(
                    text = "Delete All Markers",
                    onClick = {
                        onDeleteAll()
                        onDismiss()
                    },
                    focusId = "marker_delete_all"
                )
                
                TextButton(
                    onClick = { runWithHapticAndLed(view, hapticFeedbackIntensity, ledEnabled, focusColor, ledBrightness, ledSaturationBoost, ledTurnOffTime.inactivityMs, event = FeedbackEvent.BACK, action = onDismiss) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = focusColor
                    )
                ) {
                    Text("Cancel")
                }
            }
        }
    }
}
