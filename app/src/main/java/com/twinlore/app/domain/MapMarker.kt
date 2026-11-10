package com.twinlore.app.domain

/**
 * Data class representing a map marker with saved view state
 */
data class MapMarker(
    val id: String,
    val normalizedX: Float, // 0.0-1.0 relative to image width
    val normalizedY: Float, // 0.0-1.0 relative to image height
    val zoom: Float,
    val offsetX: Float,
    val offsetY: Float,
    val styleId: Int // 1-4 corresponding to MarkerStyle
) {
    val style: MarkerStyle?
        get() = MarkerStyle.fromId(styleId)
}
