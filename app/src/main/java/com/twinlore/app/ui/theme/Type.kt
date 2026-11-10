package com.twinlore.app.ui.theme

import android.content.Context
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.twinlore.app.data.AppFont

/** Default typography used when no font override is selected. */
val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    )
)

/** Cached font families for asset-loaded fonts (created once per font). */
private var cachedCourierFamily: FontFamily? = null
private var cachedJetBrainsFamily: FontFamily? = null
private var cachedPixelFamily: FontFamily? = null
private var cachedGothicFamily: FontFamily? = null
private var cachedSpaceFamily: FontFamily? = null

/**
 * Resolve the FontFamily for a given AppFont.
 * System font (Default) uses system monospace; all others load .ttf assets.
 */
fun AppFont.toFontFamily(context: Context? = null): FontFamily = when (this) {
    AppFont.DEFAULT -> FontFamily.Monospace
    AppFont.COURIER -> {
        cachedCourierFamily ?: context?.let {
            FontFamily(Font("fonts/courier_prime.ttf", it.assets))
                .also { family -> cachedCourierFamily = family }
        } ?: FontFamily.Monospace
    }
    AppFont.JETBRAINS -> {
        cachedJetBrainsFamily ?: context?.let {
            FontFamily(Font("fonts/jetbrains_mono.ttf", it.assets))
                .also { family -> cachedJetBrainsFamily = family }
        } ?: FontFamily.Monospace
    }
    AppFont.PIXEL -> {
        cachedPixelFamily ?: context?.let {
            FontFamily(Font("fonts/press_start_2p.ttf", it.assets))
                .also { family -> cachedPixelFamily = family }
        } ?: FontFamily.Monospace
    }
    AppFont.GOTHIC -> {
        cachedGothicFamily ?: context?.let {
            FontFamily(Font("fonts/share_tech_mono.ttf", it.assets))
                .also { family -> cachedGothicFamily = family }
        } ?: FontFamily.Monospace
    }
    AppFont.SPACE -> {
        cachedSpaceFamily ?: context?.let {
            FontFamily(Font("fonts/space_mono.ttf", it.assets))
                .also { family -> cachedSpaceFamily = family }
        } ?: FontFamily.Monospace
    }
}

/**
 * Build a Typography with the given font family applied to all text styles.
 * Pixel font (Press Start 2P) renders inherently larger, so sizes are scaled
 * down to visually match the other fonts.
 */
fun buildTypography(fontFamily: FontFamily, appFont: AppFont = AppFont.DEFAULT): Typography {
    val base = Typography()
    val scale = if (appFont == AppFont.PIXEL) 0.7f else 1f
    fun TextStyle.applyFont(): TextStyle = copy(
        fontFamily = fontFamily,
        fontSize = fontSize * scale,
        lineHeight = lineHeight * scale,
    )
    return Typography(
        displayLarge = base.displayLarge.applyFont(),
        displayMedium = base.displayMedium.applyFont(),
        displaySmall = base.displaySmall.applyFont(),
        headlineLarge = base.headlineLarge.applyFont(),
        headlineMedium = base.headlineMedium.applyFont(),
        headlineSmall = base.headlineSmall.applyFont(),
        titleLarge = base.titleLarge.applyFont(),
        titleMedium = base.titleMedium.applyFont(),
        titleSmall = base.titleSmall.applyFont(),
        bodyLarge = base.bodyLarge.applyFont(),
        bodyMedium = base.bodyMedium.applyFont(),
        bodySmall = base.bodySmall.applyFont(),
        labelLarge = base.labelLarge.applyFont(),
        labelMedium = base.labelMedium.applyFont(),
        labelSmall = base.labelSmall.applyFont(),
    )
}
