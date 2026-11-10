package com.twinlore.app.data.update

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import com.twinlore.app.BuildConfig
import com.twinlore.app.data.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdateManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository
) {
    companion object {
        private const val TAG = "UpdateManager"
        private const val GITHUB_API_BASE = "https://api.github.com/"
        private const val UPDATES_DIR = "updates"

        /** The APK asset must come from GitHub over TLS; anything else is not an update. */
        fun isTrustedUpdateUrl(url: String): Boolean {
            val parsed = url.toHttpUrlOrNull() ?: return false
            if (parsed.scheme != "https") return false
            return parsed.host == "github.com" || parsed.host.endsWith(".githubusercontent.com")
        }

        internal fun isNewer(remote: String, current: String): Boolean {
            val remoteParts = remote.split("-").first().split(".").mapNotNull { it.toIntOrNull() }
            val currentParts = current.split("-").first().split(".").mapNotNull { it.toIntOrNull() }
            val maxLen = maxOf(remoteParts.size, currentParts.size)
            for (i in 0 until maxLen) {
                val r = remoteParts.getOrElse(i) { 0 }
                val c = currentParts.getOrElse(i) { 0 }
                if (r > c) return true
                if (r < c) return false
            }
            return false
        }
    }

    private val api: GitHubReleasesApi = Retrofit.Builder()
        .baseUrl(GITHUB_API_BASE)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(GitHubReleasesApi::class.java)

    // [isTrustedUpdateUrl] validates the first hop only. followSslRedirects
    // defaults to true, which would let a redirect walk the download off HTTPS
    // for the remaining hops — the app installs what comes back, so the
    // HTTPS-only guarantee has to hold for the whole chain.
    private val httpClient = OkHttpClient.Builder()
        .followSslRedirects(false)
        .build()

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    suspend fun checkForUpdate(force: Boolean = false) {
        if (_state.value is UpdateState.Checking) return
        _state.value = UpdateState.Checking
        try {
            val release = withContext(Dispatchers.IO) { api.getLatestRelease() }
            val remoteVersion = release.tagName.removePrefix("v")
            val currentVersion = BuildConfig.VERSION_NAME

            if (!isNewer(remoteVersion, currentVersion)) {
                _state.value = if (force) UpdateState.UpToDate else UpdateState.Idle
                return
            }

            if (!force) {
                val skipped = settingsRepository.skippedUpdateVersion.first()
                if (skipped == remoteVersion) {
                    _state.value = UpdateState.Idle
                    return
                }
            }

            val apkAsset = release.assets.firstOrNull { it.name.endsWith(".apk") }
            if (apkAsset == null) {
                _state.value = UpdateState.Error("No APK found in release")
                return
            }

            _state.value = UpdateState.Available(
                version = remoteVersion,
                downloadUrl = apkAsset.browserDownloadUrl,
                releaseNotes = release.body
            )
        } catch (e: Exception) {
            Log.e(TAG, "Update check failed", e)
            _state.value = if (force) {
                UpdateState.Error("Update check failed: ${e.message}")
            } else {
                UpdateState.Idle
            }
        }
    }

    suspend fun downloadUpdate(downloadUrl: String, version: String) {
        if (!isTrustedUpdateUrl(downloadUrl)) {
            _state.value = UpdateState.Error("Update link is not a GitHub HTTPS URL")
            return
        }
        _state.value = UpdateState.Downloading(0f)
        try {
            val file = withContext(Dispatchers.IO) {
                val updatesDir = File(context.cacheDir, UPDATES_DIR)
                updatesDir.mkdirs()
                updatesDir.listFiles()?.forEach { it.delete() }

                val targetFile = File(updatesDir, "game-twin-$version.apk")
                val request = Request.Builder().url(downloadUrl).build()
                val response = httpClient.newCall(request).execute()
                val body = response.body ?: throw Exception("Empty response body")
                val contentLength = body.contentLength()

                body.byteStream().use { input ->
                    targetFile.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Long = 0
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            bytesRead += read
                            if (contentLength > 0) {
                                _state.value = UpdateState.Downloading(
                                    bytesRead.toFloat() / contentLength.toFloat()
                                )
                            }
                        }
                    }
                }
                targetFile
            }
            _state.value = UpdateState.ReadyToInstall(file)
        } catch (e: Exception) {
            Log.e(TAG, "Download failed", e)
            _state.value = UpdateState.Error("Download failed: ${e.message}")
        }
    }

    fun createInstallIntent(file: File): Intent {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    suspend fun skipVersion(version: String) {
        settingsRepository.setSkippedUpdateVersion(version)
        _state.value = UpdateState.Idle
    }

    fun dismiss() {
        _state.value = UpdateState.Idle
    }
}
