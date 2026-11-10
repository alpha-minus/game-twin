package com.twinlore.app.data.catalog

import org.json.JSONObject

sealed class ApkResolveResult {
    data class Success(
        val downloadUrl: String,
        val fileName: String,
        val version: String?
    ) : ApkResolveResult()

    data class Failure(val reason: String) : ApkResolveResult()
}

interface ApkResolver {
    suspend fun resolve(
        entryJson: JSONObject,
        supportedAbis: Array<String> = android.os.Build.SUPPORTED_ABIS
    ): ApkResolveResult
}
