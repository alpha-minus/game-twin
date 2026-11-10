package com.twinlore.app.ui.utils

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult

/**
 * Utility for extracting vibrant colors from game artwork using Android Palette API.
 * Use a shared ImageLoader when calling from UI to reuse cache; small decode size for color sampling only.
 */
object PaletteExtractor {
    
    /** Decode size for palette extraction; 256 gives better color detection than 128 while staying cheap. */
    private const val PALETTE_DECODE_SIZE = 256
    
    /**
     * Extract vibrant color from an image URL/path.
     * Returns null if extraction fails or image cannot be loaded.
     * @param imageLoader Optional shared loader (e.g. LocalImageLoader.current); if null, a new loader is created.
     */
    suspend fun extractVibrantColor(
        context: android.content.Context,
        imageData: Any?,
        imageLoader: ImageLoader? = null
    ): Color? = withContext(Dispatchers.IO) {
        if (imageData == null) return@withContext null
        
        try {
            val loader = imageLoader ?: ImageLoader(context)
            val request = ImageRequest.Builder(context)
                .data(imageData)
                .size(PALETTE_DECODE_SIZE, PALETTE_DECODE_SIZE)
                .allowHardware(false) // Need software bitmap for Palette
                .build()
            
            val result = loader.execute(request)
            if (result !is SuccessResult) return@withContext null
            
            val drawable = result.drawable
            val bitmap = drawable.toBitmap()
            
            // Extract palette
            val palette = Palette.from(bitmap).generate()
            
            // Get vibrant color, fallback to dominant color
            val vibrantSwatch = palette.vibrantSwatch
                ?: palette.mutedSwatch
                ?: palette.dominantSwatch
            
            vibrantSwatch?.let { swatch ->
                Color(
                    red = swatch.rgb shr 16 and 0xFF,
                    green = swatch.rgb shr 8 and 0xFF,
                    blue = swatch.rgb and 0xFF
                )
            }
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Convert Drawable to Bitmap
     */
    private fun android.graphics.drawable.Drawable.toBitmap(): Bitmap {
        if (this is android.graphics.drawable.BitmapDrawable && this.bitmap != null) {
            return this.bitmap
        }
        
        val width = if (intrinsicWidth > 0) intrinsicWidth else 1
        val height = if (intrinsicHeight > 0) intrinsicHeight else 1
        
        val bitmap = Bitmap.createBitmap(
            width,
            height,
            Bitmap.Config.ARGB_8888
        )
        val canvas = android.graphics.Canvas(bitmap)
        setBounds(0, 0, width, height)
        draw(canvas)
        return bitmap
    }
}

