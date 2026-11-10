package com.twinlore.app.ui.screens

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * One row of an achievement sort menu. Each sort field is listed once; the currently
 * selected field carries a tick, so the open menu shows what it is sorted by.
 */
@Composable
fun AchievementSortMenuItem(
    label: String,
    selected: Boolean,
    accentColor: Color,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        text = {
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = if (selected) accentColor else Color.Unspecified
            )
        },
        trailingIcon = if (selected) {
            {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = "Selected",
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
            }
        } else null,
        onClick = onClick
    )
}
