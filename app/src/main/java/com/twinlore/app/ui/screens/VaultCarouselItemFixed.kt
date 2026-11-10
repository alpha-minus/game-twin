package com.twinlore.app.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.ui.input.key.onKeyEvent
import android.view.KeyEvent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.view.HapticFeedbackConstants
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.twinlore.app.data.ArtDisplayMode
import com.twinlore.app.ui.utils.ImagePathUtils
import com.twinlore.app.data.FocusColor
import com.twinlore.app.data.GridDisplayRatio
import com.twinlore.app.domain.Vault
import com.twinlore.app.ui.navigation.longPressHandler
import com.twinlore.app.ui.theme.getContrastingTextColor
import com.twinlore.app.ui.theme.getFocusContainer
import com.twinlore.app.ui.utils.FeedbackEvent
import com.twinlore.app.ui.utils.FeedbackManager
import com.twinlore.app.data.HapticFeedbackIntensity
import com.twinlore.app.data.LedTurnOffTime
import com.twinlore.app.thor.led.ThorLedManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val CAROUSEL_CACHE_MAX = 32

/** LRU-bounded cache of carousel ImageRequests so idle→regular transition reuses the same request. */
private val carouselRequestCache = object : android.util.LruCache<String, ImageRequest>(CAROUSEL_CACHE_MAX) {}

fun invalidateCarouselCacheForVault(vaultId: String) {
    val keysToRemove = carouselRequestCache.snapshot().keys.filter { it.startsWith("$vaultId|") }
    keysToRemove.forEach { carouselRequestCache.remove(it) }
}

/**
 * Fixed-size carousel item with completely fixed dimensions
 * No animations, no calculations - ensures neighbors are always identical
 */
@Composable
fun VaultCarouselItemFixed(
    vault: Vault,
    isFocused: Boolean,
    isNeighbor: Boolean,
    isScraping: Boolean = false,
    onClick: () -> Unit,
    onDoubleTap: (() -> Unit)? = null,
    onLongPress: () -> Unit,
    onFocusRequest: () -> Unit,
    artDisplayMode: ArtDisplayMode,
    isIdle: Boolean,
    displayFocusColor: FocusColor,
    displayAccentColorRaw: Color? = null,
    carouselScale: Float = 1f,
    focusRequester: FocusRequester = remember { FocusRequester() },
    modifier: Modifier = Modifier,
    blurArtworkInvalidateTimestamp: Pair<String, Long>? = null,
    gridAspectRatio: Float = GridDisplayRatio.RATIO_3_4.aspectRatio,
    hapticFeedbackIntensity: HapticFeedbackIntensity = HapticFeedbackIntensity.MEDIUM,
    ledEnabled: Boolean = true,
    ledBrightness: Int = 127,
    ledSaturationBoost: Float = 0.75f,
    ledTurnOffTime: LedTurnOffTime = LedTurnOffTime.SEC_15,
    showLibraryItemNames: Boolean = false,
    isDarkTheme: Boolean = false,
    powerSaverMode: Boolean = false
) {
    val context = LocalContext.current
    val view = LocalView.current
    val touchScope = rememberCoroutineScope()
    var touchActivated by remember { mutableStateOf(false) }
    var pendingOpenJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val resourceResolver = remember(context) {
        dagger.hilt.android.EntryPointAccessors
            .fromApplication(
                context.applicationContext,
                com.twinlore.app.di.ResourceResolverEntryPoint::class.java
            )
            .resourceResolver()
    }
    val thumbnailCache = remember(context) {
        dagger.hilt.android.EntryPointAccessors
            .fromApplication(
                context.applicationContext,
                com.twinlore.app.di.ArtworkThumbnailCacheEntryPoint::class.java
            )
            .artworkThumbnailCache()
    }

    // Build request off main thread. Reuse cached request when same vault (e.g. idle→regular) to avoid flash.
    // Keep previous request visible until new one is ready to avoid image disappearance during transition.
    val carouselBusterTs = if (blurArtworkInvalidateTimestamp?.first == vault.id) blurArtworkInvalidateTimestamp!!.second else 0L
    val cacheKey = "${vault.id}|${vault.artworkResourceId ?: ""}|$carouselBusterTs"
    var carouselImageRequest by remember { mutableStateOf<ImageRequest?>(carouselRequestCache.get(cacheKey)) }
    LaunchedEffect(vault.id, vault.artworkResourceId, carouselBusterTs) {
        val key = "${vault.id}|${vault.artworkResourceId ?: ""}|$carouselBusterTs"
        val cached = carouselRequestCache.get(key)
        if (cached != null) {
            carouselImageRequest = cached
            return@LaunchedEffect
        }
        val rid = vault.artworkResourceId
        if (rid == null) {
            carouselImageRequest = null
            return@LaunchedEffect
        }
        // Try the pre-rendered thumb first — it decodes ~10× faster than the
        // full artwork on warm start. Fall back to the original (slow) Uri
        // when the thumb is missing, and lazy-regenerate so the next render
        // hits the thumb.
        val thumbFile = withContext(Dispatchers.IO) { thumbnailCache.pathFor(vault.id) }
        val data: Any? = if (thumbFile != null) {
            thumbFile
        } else {
            val uri = withContext(Dispatchers.IO) { resourceResolver.resolve(rid) }
            if (uri != null) {
                launch(Dispatchers.IO) {
                    runCatching { thumbnailCache.generate(vault.id, uri) }
                }
                ImagePathUtils.toImageData(uri.toString(), vault.id)
            } else null
        }
        val req = if (data == null) null
        else ImageRequest.Builder(context)
            .data(data)
            .memoryCacheKey("art_${vault.id}_${rid}_$carouselBusterTs")
            .size(coil.size.Size(768, 1024))
            .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
            .diskCachePolicy(coil.request.CachePolicy.ENABLED)
            .crossfade(false)
            .allowHardware(true)
            .build()
        req?.let { carouselRequestCache.put(key, it) }
        carouselImageRequest = req
    }
    
    // Use stored vibrant color from vault metadata (no per-item extraction).
    val vibrantColor: Color? = remember(vault.artworkVibrantArgb) {
        vault.artworkVibrantArgb?.let { argb ->
            Color(
                red = (argb shr 16 and 0xFF) / 255f,
                green = (argb shr 8 and 0xFF) / 255f,
                blue = (argb and 0xFF) / 255f
            )
        }
    }
    // Use displayAccentColorRaw when focused so carousel matches grid (readability-adjusted). Else raw vibrant or enum.
    val focusColor = when {
        isFocused && displayAccentColorRaw != null -> displayAccentColorRaw
        isFocused && vibrantColor != null -> vibrantColor
        else -> displayFocusColor.color
    }

    // Noir library mode: desaturate the side/idle covers, keep the focused card in colour.
    // See [rememberNoirColorFilter] — render-time filter, no re-decode / no extra cache entry.
    val noirColorFilter = rememberNoirColorFilter(
        active = com.twinlore.app.ui.LocalNoirLibraryMode.current,
        inColor = isFocused,
    )

    // Dimensions scaled by carouselScale; neighbours at 75% (126) with content cropped as if it didn't fit
    val shouldScaleInIdle = isIdle && isFocused && gridAspectRatio >= 0.95f
    val neighborVisibleBaseDp = 126f   // 75% of original 168 - visible slot in carousel
    val neighborContentBaseDp = 168f  // full card size - drawn then cropped to visible
    val baseWidth = when {
        shouldScaleInIdle -> 320f
        isNeighbor -> neighborVisibleBaseDp
        else -> 238f
    }
    val itemWidth = (baseWidth * carouselScale).dp
    val itemHeight = itemWidth / gridAspectRatio
    val neighborContentWidth = (neighborContentBaseDp * carouselScale).dp
    val neighborContentHeight = neighborContentWidth / gridAspectRatio
    val alpha = if (isNeighbor) 0.6f else 1.0f
    
    val cornerRadius = (16 * carouselScale).dp
    
    // 3D Carousel transforms
    val baseScale = when {
        shouldScaleInIdle -> 1.3f
        isFocused -> 1.1f
        isNeighbor -> 0.85f
        else -> 1.0f
    }
    val rotationYDegrees = if (isNeighbor && !isFocused) 30f else 0f
    
    // Glance animation - subtle pulse when item gains focus
    val glanceScale = remember { Animatable(1f) }
    LaunchedEffect(isFocused) {
        if (isFocused && !isIdle) {
            glanceScale.animateTo(
                targetValue = 1.02f,
                animationSpec = tween(durationMillis = 120, easing = FastOutSlowInEasing)
            )
            glanceScale.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 120, easing = FastOutSlowInEasing)
            )
        }
    }
    val scale = baseScale * glanceScale.value
    
    // Idle mode: Render image directly without any Box container - NO RECTANGLE
    // But apply rounded corners to match active mode styling
    // Respect the selected display mode (FIT, CROP, FILL) just like active mode
    // Key the content based on artworkPath to force recomposition when artwork is removed
    androidx.compose.runtime.key(vault.id, vault.artworkResourceId) {
        if (isIdle) {
            Box(
                modifier = modifier
                    .width(itemWidth)
                    .height(itemHeight)
                    .alpha(alpha)
                    .scale(scale)
                    .graphicsLayer {
                        rotationY = rotationYDegrees
                    }
                    .clip(RoundedCornerShape(cornerRadius))
            ) {
                if (vault.hasArtwork && vault.artworkResourceId != null) {
                    val contentScale = when (artDisplayMode) {
                        ArtDisplayMode.FIT -> ContentScale.Fit
                        ArtDisplayMode.CROP -> ContentScale.Crop
                        ArtDisplayMode.FILL -> ContentScale.FillBounds
                    }
                    androidx.compose.runtime.key(vault.artworkResourceId ?: "no_artwork_${vault.id}") {
                        if (vault.artworkResourceId != null && carouselImageRequest != null) {
                            AsyncImage(
                                model = carouselImageRequest,
                                contentDescription = vault.displayName,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(cornerRadius)),
                                contentScale = contentScale,
                                colorFilter = noirColorFilter,
                                placeholder = painterResource(android.R.drawable.ic_menu_gallery),
                                error = painterResource(android.R.drawable.ic_menu_gallery)
                            )
                        } else {
                            Image(
                                painter = painterResource(android.R.drawable.ic_menu_gallery),
                                contentDescription = "No artwork",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(cornerRadius))
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = vault.displayName.take(1).uppercase(),
                            style = MaterialTheme.typography.displayLarge,
                            color = if (isDarkTheme) Color.White else Color.Black
                        )
                    }
                }
                if (isScraping) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(cornerRadius))
                            .background(Color.Black.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.width(48.dp).height(48.dp)
                        )
                    }
                }
            }
        } else {
            // Active mode: Use Box container with styling
            Box(
            modifier = modifier
                .width(itemWidth) // Single width - no outer/inner split
                .alpha(alpha)
                .scale(scale)
                .graphicsLayer {
                    rotationY = rotationYDegrees
                    // Removed shadow elevation for focused items
                }
                .then(
                    if (isFocused) {
                        Modifier
                            .focusRequester(focusRequester)
                            .focusable()
                            // Only handle confirm keys - let D-pad navigation pass through to parent
                            .onKeyEvent { keyEvent ->
                                if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                                    when (keyEvent.nativeKeyEvent.keyCode) {
                                        KeyEvent.KEYCODE_DPAD_CENTER,
                                        KeyEvent.KEYCODE_BUTTON_A,
                                        KeyEvent.KEYCODE_ENTER -> {
                                            FeedbackManager.play(view, FeedbackEvent.CONFIRM)
                                            if (ledEnabled) {
                                                ThorLedManager.setColor(
                                                    focusColor,
                                                    true,
                                                    ledBrightness,
                                                    ledSaturationBoost,
                                                    ledTurnOffTime.inactivityMs
                                                )
                                            }
                                            onClick()
                                            true // Consume confirm keys
                                        }
                                        // Explicitly let D-pad navigation keys pass through - don't consume them
                                        KeyEvent.KEYCODE_DPAD_LEFT,
                                        KeyEvent.KEYCODE_DPAD_RIGHT,
                                        KeyEvent.KEYCODE_DPAD_UP,
                                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                                            false // Don't consume - let parent handle navigation
                                        }
                                        else -> false
                                    }
                                } else {
                                    false
                                }
                            }
                    } else {
                        Modifier
                    }
                )
                .pointerInput(vault.id) {
                    detectTapGestures(
                        onTap = {
                            if (touchActivated && onDoubleTap != null) {
                                pendingOpenJob?.cancel()
                                touchActivated = false
                                onDoubleTap()
                            } else {
                                touchActivated = true
                                onFocusRequest()
                                pendingOpenJob = touchScope.launch {
                                    delay(800)
                                    touchActivated = false
                                    onClick()
                                }
                            }
                        },
                        onLongPress = {
                            pendingOpenJob?.cancel()
                            touchActivated = false
                            onFocusRequest()
                            onLongPress()
                        }
                    )
                }
                .longPressHandler(
                    onLongPress = {
                        onFocusRequest()
                        onLongPress()
                    },
                    onPress = {
                        onFocusRequest()
                        onClick()
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            // Glow: vibrant color from artwork only
            if (isFocused && !isIdle && !powerSaverMode && vibrantColor != null) {
                val glowWidth = itemWidth * 1.0f
                val glowHeight = glowWidth / gridAspectRatio
                val blurRadius = (itemWidth.value * 0.08f).dp
                val color = vibrantColor!!
                Box(
                    modifier = Modifier
                        .width(glowWidth)
                        .height(glowHeight)
                        .align(Alignment.Center)
                        .blur(blurRadius)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    color.copy(alpha = 0.5f),
                                    color.copy(alpha = 0.25f),
                                    color.copy(alpha = 0.1f),
                                    color.copy(alpha = 0.05f),
                                    Color.Transparent
                                ),
                                radius = 800f
                            ),
                            shape = RoundedCornerShape(cornerRadius)
                        )
                )
            }
            
            // Box art container - use item size directly
            // Apply rounded corners to container Box to ensure all corners are rounded uniformly
            Box(
                modifier = Modifier
                    .width(itemWidth) // Single width - no outer/inner split
                    .height(itemHeight) // Fixed height from aspect ratio
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(cornerRadius)) // Rounded corners on all sides (top and bottom)
                    .then(
                        if (!vault.hasArtwork || vault.artworkResourceId == null) {
                            // Apply background with same rounded corners to ensure uniform appearance
                            Modifier.background(focusColor.getFocusContainer(), RoundedCornerShape(cornerRadius))
                        } else {
                            // Always transparent background - no rectangles
                            Modifier.background(Color.Transparent)
                        }
                    )
                    .then(
                        // Border: vibrant color from artwork only
                        if (isFocused && !isIdle && !powerSaverMode && vibrantColor != null) {
                            Modifier.border(
                                width = 3.dp,
                                color = vibrantColor!!,
                                shape = RoundedCornerShape(cornerRadius)
                            )
                        } else {
                            Modifier
                        }
                    )
                    .then(
                        if (isNeighbor && !powerSaverMode) {
                            // Depth of field blur for side items
                            Modifier.blur(10.dp)
                        } else if (isNeighbor) {
                            Modifier.blur(4.dp)
                        } else {
                            Modifier
                        }
                    )
                ,
                contentAlignment = Alignment.Center
            ) {
                // Neighbour: draw full card (168 dp) inside visible slot (126 dp) so it appears cropped
                val contentWrapper: @Composable () -> Unit = {
                    androidx.compose.runtime.key(vault.artworkResourceId ?: "no_artwork_${vault.id}") {
                        if (vault.hasArtwork && vault.artworkResourceId != null && carouselImageRequest != null) {
                            val contentScale = when (artDisplayMode) {
                                ArtDisplayMode.FIT -> ContentScale.Fit
                                ArtDisplayMode.CROP -> ContentScale.Crop
                                ArtDisplayMode.FILL -> ContentScale.FillBounds
                            }
                            AsyncImage(
                                model = carouselImageRequest,
                                contentDescription = vault.displayName,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(cornerRadius)), // Shared corner radius
                                contentScale = contentScale,
                                colorFilter = noirColorFilter,
                                placeholder = painterResource(android.R.drawable.ic_menu_gallery),
                                error = painterResource(android.R.drawable.ic_menu_gallery)
                            )
                            
                            // Game name overlaid on the image at the BOTTOM - only when setting is on
                            if (showLibraryItemNames) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize(),
                                contentAlignment = Alignment.BottomCenter
                            ) {
                                // Subtle gradient overlay - no visible box, just smooth fade
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(100.dp) // Taller for smoother fade
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(
                                                    Color.Transparent,
                                                    Color.Black.copy(alpha = 0.6f), // Less opaque
                                                    Color.Black.copy(alpha = 0.85f)
                                                ),
                                                startY = 0f,
                                                endY = Float.POSITIVE_INFINITY
                                            )
                                        )
                                )
                                // Text directly over gradient - no nested box
                                Text(
                                    text = vault.displayName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 16.dp),
                                    color = Color.White,
                                    textAlign = TextAlign.Center
                                )
                            }
                            }
                        } else {
                            // Show initial letter centered; vault name at bottom only when setting is on
                            Box(
                                modifier = Modifier.fillMaxSize()
                            ) {
                                // Initial letter centered
                                val initialLetter = vault.displayName.take(1).uppercase()
                                Text(
                                    text = initialLetter,
                                    style = MaterialTheme.typography.displayLarge,
                                    color = if (isDarkTheme) Color.White else Color.Black,
                                    modifier = Modifier.align(Alignment.Center)
                                )
                                // Vault name at bottom only when show game title is enabled
                                if (showLibraryItemNames) {
                                Text(
                                    text = vault.displayName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 16.dp),
                                    color = if (isDarkTheme) Color.White else Color.Black,
                                    textAlign = TextAlign.Center
                                )
                                }
                            }
                        }
                    }
                }
                if (isNeighbor) {
                    // Full card (168 dp) centered in visible slot (126 dp) - outer Box clips to show cropped view
                    Box(
                        modifier = Modifier
                            .width(neighborContentWidth)
                            .height(neighborContentHeight)
                            .align(Alignment.Center),
                        contentAlignment = Alignment.Center
                    ) {
                        contentWrapper()
                    }
                } else {
                    contentWrapper()
                }
                // Scraping-in-progress overlay (new vault / box art refresh)
                if (isScraping && !isIdle) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(cornerRadius))
                            .background(Color.Black.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.width(48.dp).height(48.dp)
                        )
                    }
                }
                // Touch-to-open overlay
                if (touchActivated && !isScraping) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(cornerRadius))
                            .background(Color.Black.copy(alpha = 0.6f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Opening\u2026",
                            color = vibrantColor ?: focusColor,
                            fontSize = 14.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                        )
                        if (onDoubleTap != null) {
                            Text(
                                text = "Double tap",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp)
                            )
                            Text(
                                text = "to Launch!",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp)
                            )
                        }
                    }
                }
            }
            }
        }
    }
}

