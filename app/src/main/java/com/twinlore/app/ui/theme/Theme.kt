package com.twinlore.app.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.twinlore.app.data.AppFont
import com.twinlore.app.data.ThemePalette

@Composable
fun TwinLoreTheme(
    palette: ThemePalette,
    appFont: AppFont = AppFont.DEFAULT,
    content: @Composable () -> Unit
) {
    val baseColorScheme = if (palette.isDark) {
        darkColorScheme(primary = Purple80, secondary = PurpleGrey80, tertiary = Pink80)
    } else {
        lightColorScheme(primary = Purple40, secondary = PurpleGrey40, tertiary = Pink40)
    }

    val bg = palette.background
    val fg = palette.foreground
    val surfaceVariant = if (palette.isDark) {
        fg.copy(alpha = 0.12f)
    } else {
        fg.copy(alpha = 0.08f)
    }
    val outline = if (palette.isDark) {
        fg.copy(alpha = 0.45f)
    } else {
        fg.copy(alpha = 0.35f)
    }

    val colorScheme = baseColorScheme.copy(
        background = bg,
        surface = bg,
        onBackground = fg,
        onSurface = fg,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = fg.copy(alpha = 0.7f),
        outline = outline,
        primary = fg,
        onPrimary = bg,
        primaryContainer = surfaceVariant,
        onPrimaryContainer = fg,
        secondary = fg,
        onSecondary = bg,
        secondaryContainer = surfaceVariant,
        onSecondaryContainer = fg,
        tertiary = fg,
        onTertiary = bg,
        tertiaryContainer = surfaceVariant,
        onTertiaryContainer = fg
    )

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            // Nullable, not a cast. The fold panel composes this same theme in
            // a TYPE_APPLICATION_OVERLAY window, whose context is a window
            // context and not an Activity, so the old unchecked cast threw the
            // moment the panel opened. There are no system bars to tint in an
            // overlay anyway, so having nothing to do here is the right answer
            // rather than a case to work around.
            val window = view.context.findActivity()?.window
            if (window != null) {
                window.statusBarColor = Color.Transparent.toArgb()
                window.navigationBarColor = Color.Transparent.toArgb()
                WindowCompat.getInsetsController(window, view).apply {
                    hide(WindowInsetsCompat.Type.navigationBars())
                    systemBarsBehavior =
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            }
        }
    }

    val context = LocalContext.current
    val fontFamily = appFont.toFontFamily(context)
    val typography = buildTypography(fontFamily, appFont)

    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
        content = content
    )
}

/**
 * The Activity behind a Context, or null when there is none.
 *
 * There is genuinely none in a TYPE_APPLICATION_OVERLAY window: the fold panel
 * builds its context with `createWindowContext`, which wraps a display rather
 * than an activity. Walking the wrapper chain is the standard way to ask, and
 * returning null is a real answer here rather than a failure.
 */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
