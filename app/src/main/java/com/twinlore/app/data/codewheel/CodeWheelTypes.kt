package com.twinlore.app.data.codewheel

import java.net.URLEncoder

data class CodeWheelEntry(
    val title: String,
    val zipFilename: String,
) {
    val downloadUrl: String
        get() = DOWNLOAD_BASE + URLEncoder.encode(zipFilename, "UTF-8").replace("+", "%20")

    companion object {
        const val DOWNLOAD_BASE = "https://archive.org/download/code-wheels/"
        const val SUPPORT_URL = "https://archive.org/details/code-wheels"
    }
}
