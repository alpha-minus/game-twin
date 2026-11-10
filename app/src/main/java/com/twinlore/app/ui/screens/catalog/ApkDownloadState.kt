package com.twinlore.app.ui.screens.catalog

import java.io.File

sealed class ApkDownloadState {
    object Idle : ApkDownloadState()
    object Resolving : ApkDownloadState()
    data class Downloading(val progress: Float?, val bytesDownloaded: Long = 0, val totalBytes: Long = 0) : ApkDownloadState()
    data class Ready(val file: File, val version: String?) : ApkDownloadState()
    object Installing : ApkDownloadState()
    object Installed : ApkDownloadState()
    data class Failed(val reason: String) : ApkDownloadState()
}
