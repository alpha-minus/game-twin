package com.twinlore.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Full-cover blurred box-art mask shown while a guide is downloading, so the
 * page's save-time sweep-scroll (single page) or page-by-page load (guide set)
 * isn't visible or distracting. Mirrors the guide loading screen's blurred
 * backdrop for visual consistency.
 *
 * Layer stack (bottom → top):
 *  1. Opaque theme-background base — fills instantly so nothing shows through
 *     before the art decodes (no flash of the scrolling page).
 *  2. Blurred vault box art (`artworkPath`), cropped to fill. Skipped when no
 *     art is available — the base colour stands in.
 *  3. A dark tint for a calm backdrop + legibility of the progress card above.
 *
 * The mask consumes taps so the hidden page can't be interacted with mid-save.
 * Callers render their progress card/banner ON TOP of this.
 */
@Composable
internal fun BlurredSaveMask(
    artworkPath: String?,
    vaultId: String?,
    modifier: Modifier = Modifier,
    // Dark tint over the blurred art — matched to the landing page's
    // brightness(0.5) darkening so the backdrop reads calm and deep.
    tintAlpha: Float = 0.55f,
    // Double-tap the mask to launch the tap-game easter egg (single-page save
    // has no progress ring to carry it). The mask still swallows single taps.
    onDoubleTap: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    // Resolve the box art off the main thread; null until ready / when absent.
    var artData by remember(artworkPath, vaultId) { mutableStateOf<Any?>(null) }
    LaunchedEffect(artworkPath, vaultId) {
        artData = if (artworkPath != null && vaultId != null) {
            withContext(Dispatchers.IO) {
                com.twinlore.app.ui.utils.ImagePathUtils.toImageData(artworkPath, vaultId)
            }
        } else null
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(onDoubleTap) {
                detectTapGestures(
                    onTap = {},
                    onLongPress = {},
                    onDoubleTap = onDoubleTap?.let { cb -> { cb() } },
                )
            }
    ) {
        artData?.let { art ->
            val blurMod = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                Modifier.graphicsLayer {
                    renderEffect = android.graphics.RenderEffect
                        .createBlurEffect(200f, 200f, android.graphics.Shader.TileMode.CLAMP)
                        .asComposeRenderEffect()
                }
            } else Modifier.blur(radius = 24.dp)
            AsyncImage(
                model = ImageRequest.Builder(context).data(art).crossfade(180).build(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().then(blurMod),
                contentScale = ContentScale.Crop
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = tintAlpha))
        )
    }
}
