package com.twinlore.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onPreviewKeyEvent
import android.view.KeyEvent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.twinlore.app.ui.components.GlowingAwardCircle
import com.twinlore.app.ui.components.achievementBarBrush
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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.FilterQuality
import com.twinlore.app.ui.LocalThemePalette
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.twinlore.app.data.ArtDisplayMode
import com.twinlore.app.ui.utils.ImagePathUtils
import com.twinlore.app.data.FocusColor
import com.twinlore.app.data.GridDisplayRatio
import com.twinlore.app.domain.AchievementAward
import com.twinlore.app.domain.LibraryAchievementCalc
import com.twinlore.app.domain.Vault
import com.twinlore.app.ui.navigation.longPressHandler
import com.twinlore.app.ui.theme.getFocusContainer
import com.twinlore.app.data.HapticFeedbackIntensity
import com.twinlore.app.data.LedTurnOffTime
import com.twinlore.app.thor.led.ThorLedManager
import com.twinlore.app.ui.utils.FeedbackEvent
import com.twinlore.app.ui.utils.FeedbackManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val GRID_CACHE_MAX = 128

/** LRU-bounded cache of ImageRequests for library grid items. */
private val gridRequestCache = object : android.util.LruCache<String, ImageRequest>(GRID_CACHE_MAX) {}

fun invalidateGridCacheForVault(vaultId: String) {
    val keysToRemove = gridRequestCache.snapshot().keys.filter { it.startsWith("grid_${vaultId}|") }
    keysToRemove.forEach { gridRequestCache.remove(it) }
}

/**
 * Stable [gridRequestCache] key — MUST include the decode size: Coil's explicit
 * memoryCacheKey is the complete key and does NOT append size, so two `.size(...)`
 * requests sharing a key would deliver whichever bitmap landed first regardless of fit.
 */
internal fun gridArtRequestKey(vaultId: String, rid: String?, sizeTag: String, busterTs: Long): String =
    "grid_${vaultId}|${rid ?: ""}|$sizeTag|$busterTs"

/** Resolve (IO) + build the grid ImageRequest for a vault; null if the artwork is unresolvable. */
internal suspend fun buildGridArtRequest(
    context: android.content.Context,
    resolver: com.twinlore.app.data.library.ResourceResolver,
    vaultId: String,
    rid: String,
    decodeWidth: Int,
    decodeHeight: Int,
    busterTs: Long,
    thumbnailCache: com.twinlore.app.data.library.ArtworkThumbnailCache,
): ImageRequest? {
    val data = withContext(Dispatchers.IO) {
        // Thumb-first, matching VaultCarouselItemFixed. A hit skips resolver.resolve
        // entirely, so this is strictly LESS IO than decoding the original source the
        // way this used to — which is what makes the larger decode sizes from
        // gridDecodeSize affordable: multi-MB source art leaves the scroll path.
        //
        // Skipped when the request is wider than the thumb, since serving the thumb there
        // would be an upsample, not a saving. No shipped mode does this any more: LIST used
        // to ask for 960 and paid for it with full source decodes on the scroll path (see
        // LIST_WIDTH_PX in LibraryArtworkSizing).
        val thumbUsable =
            decodeWidth <= com.twinlore.app.data.library.ArtworkThumbnailCache.TARGET_WIDTH_PX
        if (thumbUsable) {
            thumbnailCache.pathFor(vaultId)?.let { return@withContext it }
        }
        val uri = resolver.resolve(rid) ?: return@withContext null
        if (thumbUsable) {
            // Render from the source THIS time and generate in the background, matching
            // VaultCarouselItemFixed. Generating inline would block this tile's first
            // paint behind a decode + rescale + encode, and on the first render after a
            // cache-generation bump every visible tile misses at once — so inline would
            // mean N simultaneous multi-megabyte decodes, the exact cold-start spike the
            // cache exists to prevent. generateAsync bounds and dedupes them.
            thumbnailCache.generateAsync(vaultId, uri)
        }
        ImagePathUtils.toImageData(uri.toString(), vaultId)
    } ?: return null
    return ImageRequest.Builder(context)
        .data(data)
        .memoryCacheKey("grid_art_${vaultId}_${rid}_${decodeWidth}x${decodeHeight}_$busterTs")
        .size(coil.size.Size(decodeWidth, decodeHeight))
        .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
        .diskCachePolicy(coil.request.CachePolicy.ENABLED)
        .crossfade(false)
        .allowHardware(true)
        .build()
}

/**
 * Scroll-ahead prewarm: prebuild + cache the request (so the tile reads it synchronously
 * as its initial value — no placeholder/resolve frame) AND warm Coil's bitmap cache.
 * busterTs = 0 (the non-invalidated case the prefetch targets). Cheap to repeat: the
 * request cache + Coil dedupe make a second call a near no-op.
 */
internal suspend fun prewarmGridArtwork(
    context: android.content.Context,
    imageLoader: coil.ImageLoader,
    resolver: com.twinlore.app.data.library.ResourceResolver,
    vaultId: String,
    rid: String,
    decodeWidth: Int,
    decodeHeight: Int,
    thumbnailCache: com.twinlore.app.data.library.ArtworkThumbnailCache,
) {
    val key = gridArtRequestKey(vaultId, rid, "${decodeWidth}x${decodeHeight}", 0L)
    val req = gridRequestCache.get(key)
        ?: buildGridArtRequest(
            context, resolver, vaultId, rid, decodeWidth, decodeHeight, 0L, thumbnailCache,
        )?.also { gridRequestCache.put(key, it) }
        ?: return
    imageLoader.enqueue(req)
}

/**
 * Grid item for displaying a vault in the library grid
 * @param blurArtworkInvalidateTimestamp When set (from library), cache key includes timestamp so new artwork shows after assign
 */
@Composable
@OptIn(ExperimentalFoundationApi::class)
fun VaultGridItem(
    vault: Vault,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onDoubleTap: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
    focusId: String = vault.id,
    focusManager: com.twinlore.app.ui.navigation.FocusManager? = null,
    focusRequester: FocusRequester = remember { FocusRequester() },
    artDisplayMode: ArtDisplayMode = ArtDisplayMode.FIT,
    onDpadUp: (() -> Unit)? = null,
    onDpadDown: (() -> Unit)? = null,
    onDpadLeft: (() -> Unit)? = null,
    onDpadRight: (() -> Unit)? = null,
    onButtonX: (() -> Unit)? = null,
    blurArtworkInvalidateTimestamp: Pair<String, Long>? = null,
    onFocusRequested: (() -> Unit)? = null,
    isSettledFocus: Boolean = false,
    isViewModelFocused: Boolean = false,
    focusSettledGeneration: Long = 0L,
    // Providers, not values: these carry the library's 120ms accent tween, and reading an
    // animated value in composition recomposes this whole tile on every frame of it. Called
    // in the draw phase instead, where a change costs one layer's repaint.
    borderColorWhenSettled: (() -> Color)? = null,
    focusColor: () -> Color = { FocusColor.CRIMSON.color },
    // Plain settled target, not the live tween: for composition reads that cannot be moved into
    // a draw phase (e.g. Text color). Reading this creates no per-frame animation subscription.
    settledAccentColor: Color = FocusColor.CRIMSON.color,
    hapticFeedbackIntensity: HapticFeedbackIntensity = HapticFeedbackIntensity.MEDIUM,
    ledEnabled: Boolean = true,
    ledBrightness: Int = 127,
    ledSaturationBoost: Float = 0.75f,
    ledTurnOffTime: LedTurnOffTime = LedTurnOffTime.SEC_15,
    gridAspectRatio: Float = GridDisplayRatio.RATIO_3_4.aspectRatio,
    showLibraryItemNames: Boolean = false,
    isDarkTheme: Boolean = false,
    achievementSoftcore: Float? = null,
    achievementHardcore: Float? = null,
    achievementAwardKind: String? = null,
    /**
     * Coil decode dims for this item's artwork. The library grid derives these from its
     * measured container via [gridDecodeSize] and should always pass them explicitly;
     * the 256×341 default is a leftover for the carousel and launcher call sites, which
     * still hardcode their own sizes and are NOT covered by that helper.
     *
     * Width × height get embedded in [Coil's memoryCacheKey] so different decode sizes
     * for the same artwork populate separate Coil memory-cache slots — switching modes
     * doesn't deliver a stale, undersized bitmap to the larger view. The
     * [LibraryScreen] preload key must use the same `${w}x${h}` suffix or the runtime
     * read goes to an empty slot and the cold-start preload becomes dead weight.
     */
    decodeWidth: Int = 256,
    decodeHeight: Int = 341,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val palette = LocalThemePalette.current
    val view = LocalView.current
    val touchScope = rememberCoroutineScope()
    var touchActivated by remember { mutableStateOf(false) }
    var pendingOpenJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    
    // Register with focus manager if provided
    focusManager?.registerFocus(focusId, focusRequester)

    // One-shot focus animation when settled focus changes: quick scale pulse.
    val focusScale = remember { Animatable(1f) }
    var lastFocusAnimGeneration by remember { mutableStateOf(-1L) }
    LaunchedEffect(isSettledFocus, focusSettledGeneration) {
        if (isSettledFocus && focusSettledGeneration != lastFocusAnimGeneration) {
            lastFocusAnimGeneration = focusSettledGeneration
            focusScale.snapTo(1f)
            focusScale.animateTo(1.04f, animationSpec = tween(110, easing = FastOutSlowInEasing))
            focusScale.animateTo(1f, animationSpec = tween(170, easing = FastOutSlowInEasing))
        } else if (!isSettledFocus) {
            focusScale.snapTo(1f)
        }
    }
    val settledBorderColor: () -> Color = borderColorWhenSettled ?: focusColor

    // Noir library mode: desaturate every cover except the focused one. Gated on the
    // same `isSettledFocus` the border/name accent use, so the colour pop lands on the
    // exact frame the selection accent does (not mid-focus-tween). The one animated
    // saturation drives BOTH the cover image and the achievement bar/circles below, so
    // the whole tile greys/colours as a unit. See [rememberNoirSaturation].
    val noirSaturation = rememberNoirSaturation(
        active = com.twinlore.app.ui.LocalNoirLibraryMode.current,
        inColor = isSettledFocus,
    )
    val noirColorFilter = colorFilterForSaturation(noirSaturation)

    // Premium metallic shine: a one-shot band sweeps the cover when the tile settles into
    // focus — gold for the Gold icon style, silver for Adventure. Keyed on the same settle
    // generation as the scale pulse so it fires once per focus landing, then stops — no
    // infinite transition, so zero idle cost. Starts at 1f (finished/off) so nothing sweeps
    // until the first focus.
    val shimmerColor: Color? = when {
        com.twinlore.app.ui.LocalGoldLibraryMode.current -> com.twinlore.app.ui.screens.AdventureGold
        com.twinlore.app.ui.LocalAdventureLibraryMode.current -> com.twinlore.app.ui.screens.ShimmerSilver
        else -> null
    }
    val shimmer = remember { Animatable(1f) }
    // Fire on every rising edge of settled focus (false→true) rather than keying on a
    // generation counter. The carousel passes a constant generation (0L), so the old
    // `gen != lastGen` guard let the sweep run only the *first* time each vault was
    // focused — moving focus back to it never re-swept. Rising-edge re-fires the shine
    // each time the tile regains focus, consistently across all view modes.
    LaunchedEffect(isSettledFocus) {
        if (shimmerColor != null && isSettledFocus) {
            shimmer.snapTo(0f)
            shimmer.animateTo(1f, animationSpec = tween(450, easing = androidx.compose.animation.core.LinearEasing))
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp)
            .focusRequester(focusRequester)
            .focusable(interactionSource = interactionSource)
            .onFocusChanged { focusState ->
                if (!focusState.isFocused) {
                    pendingOpenJob?.cancel()
                    touchActivated = false
                }
            }
            .onPreviewKeyEvent { keyEvent ->
                val isDown = keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN
                val isUp = keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_UP
                if (isDown || isUp) {
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_UP -> {
                            if (onDpadUp != null) {
                                if (isDown) onDpadUp()
                                true // Consume both DOWN and UP to prevent Compose double-traversal
                            } else false
                        }
                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            if (onDpadDown != null) {
                                if (isDown) onDpadDown()
                                true
                            } else false
                        }
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            if (onDpadLeft != null) {
                                if (isDown) onDpadLeft()
                                true
                            } else false
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            if (onDpadRight != null) {
                                if (isDown) onDpadRight()
                                true
                            } else false
                        }
                        KeyEvent.KEYCODE_BUTTON_X -> {
                            if (onButtonX != null) {
                                if (isDown) onButtonX()
                                true
                            } else false
                        }
                        KeyEvent.KEYCODE_DPAD_CENTER,
                        KeyEvent.KEYCODE_BUTTON_A,
                        KeyEvent.KEYCODE_ENTER,
                        KeyEvent.KEYCODE_SPACE -> {
                            if (!isDown) return@onPreviewKeyEvent true // Consume UP without action
                            val canOpen = isFocused || isSettledFocus || isViewModelFocused
                            if (canOpen) {
                                FeedbackManager.play(view, FeedbackEvent.CONFIRM)
                                if (ledEnabled) {
                                    ThorLedManager.setColor(
                                        focusColor(),
                                        true,
                                        ledBrightness,
                                        ledSaturationBoost,
                                        ledTurnOffTime.inactivityMs
                                    )
                                }
                                onClick()
                                true
                            } else {
                                onFocusRequested?.invoke()
                                focusRequester.requestFocus()
                                true
                            }
                        }
                        else -> false
                    }
                } else {
                    false
                }
            }
            .then(
                if (onLongPress != null) {
                    Modifier
                        .combinedClickable(
                            onClick = {
                                if (touchActivated && onDoubleTap != null) {
                                    pendingOpenJob?.cancel()
                                    touchActivated = false
                                    onDoubleTap()
                                } else {
                                    touchActivated = true
                                    onFocusRequested?.invoke()
                                    focusRequester.requestFocus()
                                    pendingOpenJob = touchScope.launch {
                                        delay(800)
                                        onClick()
                                    }
                                }
                            },
                            onLongClick = {
                                pendingOpenJob?.cancel()
                                touchActivated = false
                                onFocusRequested?.invoke()
                                focusRequester.requestFocus()
                                onLongPress()
                            },
                            interactionSource = interactionSource,
                            indication = null,
                            role = Role.Button
                        )
                        .longPressHandler(
                            onLongPress = {
                                onFocusRequested?.invoke()
                                onLongPress()
                            },
                            onPress = {
                                onClick()
                            }
                        )
                } else {
                        Modifier.clickable(
                        onClick = {
                            if (touchActivated && onDoubleTap != null) {
                                pendingOpenJob?.cancel()
                                touchActivated = false
                                onDoubleTap()
                            } else {
                                touchActivated = true
                                onFocusRequested?.invoke()
                                focusRequester.requestFocus()
                                pendingOpenJob = touchScope.launch {
                                    delay(800)
                                    onClick()
                                }
                            }
                        },
                        role = Role.Button
                    )
                }
            )
            // Collapse the whole tile subtree (cover art, name, achievement bars, award
            // knob, glow) to one property-less semantics node instead of several. Placed
            // last in the chain, after focusRequester/focusable/onPreviewKeyEvent/
            // clickable, so it covers only the visual content below and does not sit above
            // the focus and key-event modifiers: d-pad focus and A/long-press keep working
            // exactly as before. No screen reader runs on this hardware (touch_exploration
            // is off); the two enabled accessibility services never read node contents.
            .clearAndSetSemantics {}
    ) {
        // Box art container
        Box(
            modifier = Modifier
                .fillMaxWidth()
                // Read in the LAYER phase: a graphicsLayer lambda re-runs on the frames the
                // scale pulse ticks without recomposing the tile, unlike Modifier.scale's
                // composition-time `focusScale.value` read.
                .graphicsLayer {
                    scaleX = focusScale.value
                    scaleY = focusScale.value
                }
                .aspectRatio(gridAspectRatio) // Use ratio from settings
                .clip(RoundedCornerShape(8.dp))
                .then(
                    // Only show background if no artwork (for placeholder)
                    // When artwork exists, make background transparent so art boundaries are visible
                    if (!vault.hasArtwork || vault.artworkResourceId == null) {
                        // Always use focus color for placeholder background, regardless of focus state
                        Modifier.drawBehind { drawRect(focusColor().getFocusContainer()) }
                    } else {
                        // Explicitly set transparent background when artwork exists
                        Modifier.background(Color.Transparent)
                    }
                )
                .then(
                    // Border only when settled focus (after color ready) to avoid flicker
                    // Same 4dp inset stroke Modifier.border draws, but the colour is read in
                    // the draw phase so the accent tween never recomposes the tile.
                    if (isSettledFocus) {
                        Modifier.drawWithContent {
                            drawContent()
                            val stroke = 4.dp.toPx()
                            drawRoundRect(
                                color = settledBorderColor(),
                                topLeft = androidx.compose.ui.geometry.Offset(stroke / 2f, stroke / 2f),
                                size = androidx.compose.ui.geometry.Size(
                                    size.width - stroke,
                                    size.height - stroke,
                                ),
                                // Modifier.border strokes a path whose corner radius is the shape's
                                // declared radius reduced by half the stroke width, not the raw
                                // declared radius (BorderModifierNode.drawRoundRectBorder).
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                                    (8.dp.toPx() - stroke / 2f).coerceAtLeast(0f)
                                ),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(stroke),
                            )
                        }
                    } else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            if (vault.hasArtwork && vault.artworkResourceId != null) {
                val context = LocalContext.current
                val gridResourceResolver = remember(context) {
                    dagger.hilt.android.EntryPointAccessors
                        .fromApplication(
                            context.applicationContext,
                            com.twinlore.app.di.ResourceResolverEntryPoint::class.java
                        )
                        .resourceResolver()
                }
                val gridThumbnailCache = remember(context) {
                    dagger.hilt.android.EntryPointAccessors
                        .fromApplication(
                            context.applicationContext,
                            com.twinlore.app.di.ArtworkThumbnailCacheEntryPoint::class.java
                        )
                        .artworkThumbnailCache()
                }
                val contentScale = when (artDisplayMode) {
                    ArtDisplayMode.FIT -> ContentScale.Fit
                    ArtDisplayMode.CROP -> ContentScale.Crop
                    ArtDisplayMode.FILL -> ContentScale.FillBounds
                }
                val busterTs = if (blurArtworkInvalidateTimestamp?.first == vault.id) blurArtworkInvalidateTimestamp.second else 0L
                val sizeTag = "${decodeWidth}x${decodeHeight}"
                val cacheKey = gridArtRequestKey(vault.id, vault.artworkResourceId, sizeTag, busterTs)
                // Read the cached request as the INITIAL value with the FULL key (incl. size),
                // so a tile whose request was prebuilt by the scroll-ahead prefetch (see
                // [prewarmGridArtwork]) — or shown recently — paints synchronously: no
                // placeholder/IO-resolve frame, then Coil hits the warmed bitmap. (Previously
                // the initial read used a size-less key that never matched the put.)
                var gridImageRequest by remember { mutableStateOf<ImageRequest?>(gridRequestCache.get(cacheKey)) }
                LaunchedEffect(cacheKey) {
                    gridRequestCache.get(cacheKey)?.let { gridImageRequest = it; return@LaunchedEffect }
                    val rid = vault.artworkResourceId ?: run { gridImageRequest = null; return@LaunchedEffect }
                    // Build without clearing current — keep the previous image visible until ready.
                    val req = buildGridArtRequest(
                        context, gridResourceResolver, vault.id, rid,
                        decodeWidth, decodeHeight, busterTs, gridThumbnailCache,
                    )
                    if (req != null) gridRequestCache.put(cacheKey, req)
                    gridImageRequest = req
                }
                gridImageRequest?.let { req ->
                    AsyncImage(
                        model = req,
                        contentDescription = vault.displayName,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(8.dp))
                            .then(
                                // shimmer.value is read inside metallicShimmer's drawWithContent
                                // lambda (the DRAW phase), not here at the call site — same reason
                                // as the scale read above: a live Animatable value read in
                                // composition would recompose this tile on every sweep frame.
                                if (shimmerColor != null) Modifier.metallicShimmer({ shimmer.value }, shimmerColor)
                                else Modifier
                            ),
                        contentScale = contentScale,
                        colorFilter = noirColorFilter,
                        // Compose's default FilterQuality.Low maps to no bilinear
                        // filtering on Android — fine when the decoded bitmap is
                        // larger than the cell, blocky when it's smaller (LIST 3:1
                        // upsamples 2× from 960; large-grid upsamples ~2.5× from 256).
                        // Medium = bilinear, near-free on the GPU, smooths the upsample.
                        filterQuality = FilterQuality.Medium,
                        placeholder = painterResource(android.R.drawable.ic_menu_gallery),
                        error = painterResource(android.R.drawable.ic_menu_gallery)
                    )
                }
                if (gridImageRequest == null) {
                    Image(
                        painter = painterResource(android.R.drawable.ic_menu_gallery),
                        contentDescription = "Loading",
                        modifier = Modifier.fillMaxSize()
                    )
                }
            } else {
                // Placeholder when no artwork
                Image(
                    painter = painterResource(android.R.drawable.ic_menu_gallery),
                    contentDescription = "No artwork",
                    modifier = Modifier
                        .fillMaxSize(0.5f)
                        .padding(16.dp)
                )
            }
            if (touchActivated) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Opening\u2026",
                        // Composition read: gated on touchActivated, which can be true while
                        // the accent tween is still live, so this must take the settled target
                        // rather than the live provider (which would read an animated State
                        // here and resubscribe this Text to every tween frame).
                        color = vault.artworkVibrantArgb?.let { androidx.compose.ui.graphics.Color(it) } ?: settledAccentColor,
                        fontSize = 14.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                    )
                    if (onDoubleTap != null) {
                        Text(
                            text = "Double tap",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp)
                        )
                        Text(
                            text = "to Launch!",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp)
                        )
                    }
                }
            }
        }
        // Name in a row directly UNDER the art (never on top of it). Theme foreground
        // when idle; auto-focus accent on the selected tile, matching the rest of the
        // library. Readability comes from the dimmed library background (see the
        // showLibraryItemNames scrim in LibraryScreen), not a per-text panel.
        //
        // Colour off isSettledFocus + settledBorderColor — the exact source/timing the
        // selection border uses — NOT the immediate `isFocused`/`focusColor`. focusColor
        // is the animated accent (120ms tween); reading it on the first focus frame shows
        // the PREVIOUS vault's colour mid-tween, i.e. the flash. Gating on settle (140ms,
        // after the tween lands) means the name only ever paints the final accent.
        if (showLibraryItemNames) {
            val nameColor = if (isSettledFocus) settledBorderColor() else palette.foreground
            Text(
                text = vault.displayName,
                style = MaterialTheme.typography.labelMedium,
                // Weight tracks the same settle gate as the colour — otherwise the name
                // bolds the instant focus lands and sits bold-but-uncoloured for ~140ms
                // before the accent arrives, which reads as an unnecessary flash.
                fontWeight = if (isSettledFocus)
                    androidx.compose.ui.text.font.FontWeight.Bold
                else androidx.compose.ui.text.font.FontWeight.Medium,
                // Reserve two lines so single- and two-line titles align — keeps the
                // achievement progress bar at the same vertical offset across tiles.
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                color = nameColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            )
        }
        if (achievementSoftcore != null) {
            val softcore = achievementSoftcore.coerceIn(0f, 1f)
            val hardcore = (achievementHardcore ?: 0f).coerceIn(0f, 1f)
            // Award knob mirrors RA's badge: gold = mastered/completed, silver = beaten.
            val award = LibraryAchievementCalc.award(achievementAwardKind, hardcore)
            Spacer(Modifier.height(4.dp))
            // Container tall enough to host the centered completion dot over the bar.
            // Under Noir mode the gold/silver bar + award glow desaturate in lockstep
            // with the cover (same `noirSaturation`), so an unfocused tile greys wholly.
            Box(
                modifier = Modifier.fillMaxWidth().height(12.dp).noirSaturation(noirSaturation),
                contentAlignment = Alignment.Center
            ) {
                // Dual-layer bar: silver = softcore, gold = hardcore overlaid on top.
                // Gradient fills give the bar a glossy sheen (mirrors the RA site).
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.18f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(softcore)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(2.dp))
                            .background(achievementBarBrush(mastered = false))
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(hardcore)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(2.dp))
                            .background(achievementBarBrush(mastered = true))
                    )
                }
                // Glowing award knob at the bar end: gold = mastered/completed,
                // silver = beaten (matches the RA award badge).
                if (award != AchievementAward.NONE) {
                    GlowingAwardCircle(
                        size = 11.dp,
                        mastered = award == AchievementAward.GOLD,
                        filled = LibraryAchievementCalc.awardFilled(achievementAwardKind),
                        modifier = Modifier.align(Alignment.CenterEnd)
                    )
                }
            }
        }
    }
}

