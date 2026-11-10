package com.twinlore.app.data.library

/** Progress states for the post-home-folder-pick reindex. */
sealed class LibraryReindexProgress {
    object Idle : LibraryReindexProgress()
    data class Scanning(val label: String) : LibraryReindexProgress()
    data class Binding(val current: Int, val total: Int, val vaultName: String) : LibraryReindexProgress()
}
