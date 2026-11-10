package com.twinlore.app.domain

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.ui.graphics.vector.ImageVector

enum class FilePickerViewMode {
    LIST, GRID;

    val icon: ImageVector
        get() = when (this) {
            LIST -> Icons.Default.ViewList
            GRID -> Icons.Default.GridView
        }
}

enum class FilePickerSortField(val label: String) {
    NAME("Name"),
    LAST_OPENED("Last opened"),
    DATE_ADDED("Date added"),
    SIZE("Size"),
    TYPE("Type");
}

enum class SortDirection {
    ASC, DESC;

    fun toggle(): SortDirection = if (this == ASC) DESC else ASC
}
