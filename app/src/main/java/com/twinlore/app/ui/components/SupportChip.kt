package com.twinlore.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Small donate-icon button shown next to a content source's favicon in
 * picker rows and in the VGMaps picker top bar. Tapping opens the source's
 * support URL (donate / subscribe / homepage) in the Browser tab via [onTap].
 */
@Composable
fun SupportChip(
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    sizeDp: Int = 24,
    contentDescription: String = "Support this source",
) {
    Box(
        modifier = modifier
            .size(sizeDp.dp)
            .clip(RoundedCornerShape(6.dp))
            .clickable { onTap() }
            .padding(2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.VolunteerActivism,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size((sizeDp - 6).dp),
        )
    }
}
