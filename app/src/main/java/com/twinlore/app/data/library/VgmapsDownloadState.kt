package com.twinlore.app.data.library

import com.twinlore.app.data.vgmaps.VgmapsMap

sealed class VgmapsDownloadState {
    object Idle : VgmapsDownloadState()
    data class Downloading(
        val done: Int,
        val total: Int,
        val currentCaption: String,
        val failures: List<Failure>,
    ) : VgmapsDownloadState()
    data class Complete(val saved: Int, val failures: List<Failure>) : VgmapsDownloadState()
    data class Cancelled(val savedSoFar: Int) : VgmapsDownloadState()

    data class Failure(val map: VgmapsMap, val reason: String)
}
