package com.twinlore.app.ui.screens

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.twinlore.app.data.FocusColor
import com.twinlore.app.ui.LocalCachedFocusColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Result of fetching image metadata (dimensions + type). */
internal data class ImageInfo(val width: Int, val height: Int, val mimeType: String?)

/** Quality tiers by total pixels: good >= 1MP, medium >= ~0.3MP (640×480), low below. */
internal fun imageQualityColor(pixels: Long): Color {
    return when {
        pixels >= 1_000_000 -> Color(0xFF2E7D32)
        pixels >= 300_000 -> Color(0xFFF9A825)
        else -> Color(0xFFC62828)
    }
}

internal fun imageQualityLabel(pixels: Long): String = when {
    pixels >= 1_000_000 -> "High"
    pixels >= 300_000 -> "Medium"
    else -> "Low"
}

internal fun formatImageType(mimeType: String?): String = when {
    mimeType == null -> "Unknown"
    mimeType.endsWith("jpeg", ignoreCase = true) || mimeType.endsWith("jpg", ignoreCase = true) -> "JPEG"
    mimeType.endsWith("png", ignoreCase = true) -> "PNG"
    mimeType.endsWith("webp", ignoreCase = true) -> "WebP"
    mimeType.endsWith("gif", ignoreCase = true) -> "GIF"
    mimeType.endsWith("bmp", ignoreCase = true) -> "BMP"
    else -> mimeType.substringAfterLast('/').uppercase().ifBlank { "Unknown" }
}

/** Fetch image dimensions and type from URL on IO; returns null on failure. */
internal suspend fun fetchImageInfo(context: Context, imageUrl: String, referer: String? = null): ImageInfo? = withContext(Dispatchers.IO) {
    try {
        when {
            imageUrl.startsWith("content://") || imageUrl.startsWith("file://") -> {
                // Local decode — used by both the file picker (content://)
                // and by the offline guide viewers when long-pressing an
                // image whose src has been resolved through the MHT
                // manifest to a file:// URL.
                val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(Uri.parse(imageUrl))?.use { stream ->
                    android.graphics.BitmapFactory.decodeStream(stream, null, options)
                }
                val w = options.outWidth
                val h = options.outHeight
                if (w > 0 && h > 0) ImageInfo(w, h, options.outMimeType) else null
            }
            imageUrl.startsWith("http://") || imageUrl.startsWith("https://") -> {
                // Route HTTP fetches through BrowserSaveCoordinator so we use the same
                // OkHttp client + headers + cookies as the working save path. The bare
                // HttpURLConnection client is 403'd by Cloudflare even with identical
                // headers (TLS/HTTP-2 fingerprint differs).
                val coordinator = dagger.hilt.android.EntryPointAccessors
                    .fromApplication(
                        context.applicationContext,
                        com.twinlore.app.di.BrowserSaveCoordinatorEntryPoint::class.java
                    )
                    .browserSaveCoordinator()
                coordinator.fetchImageMetadata(imageUrl, referer)?.let { ImageInfo(it.width, it.height, it.mimeType) }
            }
            else -> null
        }
    } catch (_: Exception) {
        null
    }
}

/**
 * Context menu for browser images (long-press)
 */
@Composable
internal fun BrowserImageContextMenu(
    imageUrl: String,
    onAssignAsBoxArt: () -> Unit,
    onAssignAsMap: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    pageUrl: String? = null,
    // When non-null, shown below the quality row INSTEAD of the
    // browser-only reload hint. Offline callers (single-guide and
    // page-group viewers) pass the image's filename — the
    // "reopen for higher quality" advice doesn't apply once the
    // image has been baked into a saved MHT.
    displayName: String? = null,
) {
    // Get focus color from settings to match app theme
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val focusColorState by settingsViewModel.focusColor.collectAsState(initial = FocusColor.CRIMSON)
    val cachedFocusColor = LocalCachedFocusColor.current?.value
    val focusColor = cachedFocusColor ?: focusColorState.color
    val context = LocalContext.current
    var imageInfo by remember(imageUrl) { mutableStateOf<ImageInfo?>(null) }
    LaunchedEffect(imageUrl) {
        imageInfo = null
        imageInfo = fetchImageInfo(context, imageUrl, referer = pageUrl)
    }
    val pixels = imageInfo?.let { it.width.toLong() * it.height } ?: 0L
    val qualityColor = when (imageInfo) {
        null -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> imageQualityColor(pixels)
    }
    val showReloadHint = imageInfo != null && pixels < 1_000_000 // Low or Medium quality
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null
                ) { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = modifier
                    .fillMaxWidth(0.9f)
                    .padding(16.dp)
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null
                    ) { /* consume click so it doesn't dismiss */ },
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.padding(8.dp)
                ) {
                    Text(
                        text = "Image Options",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(16.dp)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(qualityColor, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when (val info = imageInfo) {
                                null -> "Image quality: Loading…"
                                else -> "Image quality: ${imageQualityLabel(pixels)} (${info.width} × ${info.height}) · ${formatImageType(info.mimeType)}"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (imageInfo != null) qualityColor else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (displayName != null) {
                        Text(
                            text = displayName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    } else if (showReloadHint) {
                        Text(
                            text = "Going back and reopening the image may load a higher quality version.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }
                    TextButton(
                        onClick = onAssignAsBoxArt,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = focusColor
                        )
                    ) {
                        Text("Assign as Artwork")
                    }
                    TextButton(
                        onClick = onAssignAsMap,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = focusColor
                        )
                    ) {
                        Text("Save Image")
                    }
                    TextButton(
                        onClick = onDismiss,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Text("Cancel")
                    }
                }
            }
        }
    }
}
