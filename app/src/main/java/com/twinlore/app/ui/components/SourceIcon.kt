package com.twinlore.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import coil.compose.AsyncImage
import com.twinlore.app.data.browser.SourceIconCache
import com.twinlore.app.di.SourceIconCacheEntryPoint
import dagger.hilt.android.EntryPointAccessors
import java.io.File

/**
 * Icon for an entry in the guide and map source menus. Shows the site's favicon when the
 * built-in browser has harvested one from a visit the user made ([SourceIconCache]); until
 * then, a monogram of the host. Deliberately never fetches anything itself: the icons used
 * to come from Google's favicon service, which told Google the user's IP and the offered
 * hosts merely for opening a list (privacy policy §3.3, since removed).
 */
@Composable
fun SourceIcon(host: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val iconFile: File? = remember(host) {
        EntryPointAccessors.fromApplication(
            context.applicationContext, SourceIconCacheEntryPoint::class.java
        ).sourceIconCache().fileFor(host)
    }
    if (iconFile != null) {
        AsyncImage(model = iconFile, contentDescription = null, modifier = modifier)
    } else {
        SourceMonogram(host = host, modifier = modifier)
    }
}

/** First letter of the host on a hue derived from it, so each source keeps a stable colour. */
@Composable
fun SourceMonogram(host: String, modifier: Modifier = Modifier) {
    val normalized = SourceIconCache.normalize(host) ?: host.lowercase()
    val letter = normalized.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar() ?: '?'
    val hue = (normalized.hashCode().toUInt() % 360u).toFloat()
    val bg = Color.hsl(hue, 0.35f, 0.30f)
    Box(modifier.background(bg), contentAlignment = Alignment.Center) {
        Text(
            text = letter.toString(),
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}
