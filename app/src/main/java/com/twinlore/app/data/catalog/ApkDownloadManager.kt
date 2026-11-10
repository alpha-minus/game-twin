package com.twinlore.app.data.catalog

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import com.twinlore.app.domain.RecommendedApp
import com.twinlore.app.ui.screens.catalog.ApkDownloadState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApkDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val githubResolver: GitHubApkResolver,
    private val codebergResolver: CodebergApkResolver,
    private val htmlResolver: HtmlApkResolver,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val supportedAbis: Array<String> = Build.SUPPORTED_ABIS
) {

    companion object {
        fun isHttpsUrl(url: String): Boolean = url.startsWith("https://", ignoreCase = true)
    }

    private val _states = MutableStateFlow<Map<String, ApkDownloadState>>(emptyMap())
    val states: StateFlow<Map<String, ApkDownloadState>> = _states.asStateFlow()

    private val concurrency = Semaphore(3)
    private var scope: CoroutineScope? = null
    private var parentJob: Job? = null

    /** Maps Android DownloadManager download IDs to app package IDs. */
    private val downloadIds = mutableMapOf<Long, DownloadInfo>()

    private data class DownloadInfo(val appId: String, val fileName: String, val version: String?)

    private val systemDownloadManager: DownloadManager
        get() = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    val downloadDir: File
        get() = File(context.externalCacheDir, "apk-downloads").apply { mkdirs() }

    private var receiver: BroadcastReceiver? = null

    /**
     * Scans the download dir for cached APKs and marks matching apps as Ready.
     * Called when the catalog loads so previously downloaded APKs are immediately installable.
     */
    fun restoreCachedDownloads(apps: List<RecommendedApp>) {
        val files = downloadDir.listFiles() ?: return
        if (files.isEmpty()) return
        val newStates = _states.value.toMutableMap()
        for (app in apps) {
            val cached = files.firstOrNull { it.name.startsWith("${app.id}_") && it.length() > 0 }
            if (cached != null && newStates[app.id] == null) {
                newStates[app.id] = ApkDownloadState.Ready(cached, null)
            }
        }
        if (newStates != _states.value) {
            _states.value = newStates
        }
    }

    fun downloadApps(apps: List<RecommendedApp>) {
        parentJob?.cancel()
        val job = Job()
        scope = CoroutineScope(dispatcher + job)
        parentJob = job

        // Merge new apps into existing states (don't clobber already-downloading ones)
        val newStates = _states.value.toMutableMap()
        apps.forEach { newStates[it.id] = ApkDownloadState.Resolving }
        _states.value = newStates

        registerCompletionReceiver()

        apps.forEach { app ->
            scope!!.launch {
                concurrency.acquire()
                try {
                    processApp(app)
                } finally {
                    concurrency.release()
                }
            }
        }
    }

    private suspend fun processApp(app: RecommendedApp) {
        // If an APK is already downloaded for this app, skip resolution and go straight to Ready.
        val existing = downloadDir.listFiles()?.firstOrNull { it.name.startsWith("${app.id}_") && it.length() > 0 }
        if (existing != null) {
            updateState(app.id, ApkDownloadState.Ready(existing, null))
            return
        }

        updateState(app.id, ApkDownloadState.Resolving)

        val entryJson = JSONObject(app.entryJson)
        val sourceType = entryJson.optString("overrideSource", "GitHub")

        val resolveResult = when (sourceType) {
            "GitHub" -> githubResolver.resolve(entryJson, supportedAbis)
            "Codeberg" -> codebergResolver.resolve(entryJson, supportedAbis)
            "HTML" -> htmlResolver.resolve(entryJson, supportedAbis)
            else -> ApkResolveResult.Failure("Unknown source type: $sourceType")
        }

        when (resolveResult) {
            is ApkResolveResult.Failure -> {
                updateState(app.id, ApkDownloadState.Failed(resolveResult.reason))
            }
            is ApkResolveResult.Success -> {
                enqueueDownload(app.id, resolveResult)
            }
        }
    }

    private fun enqueueDownload(appId: String, resolved: ApkResolveResult.Success) {
        if (!isHttpsUrl(resolved.downloadUrl)) {
            updateState(appId, ApkDownloadState.Failed("Download link is not HTTPS"))
            return
        }
        updateState(appId, ApkDownloadState.Downloading(null))

        val fileName = "${appId}_${resolved.fileName}"
        val destFile = File(downloadDir, fileName)
        if (destFile.exists()) destFile.delete()

        val request = DownloadManager.Request(Uri.parse(resolved.downloadUrl)).apply {
            setTitle(resolved.fileName)
            setDescription("Downloading for Game Twin")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            setDestinationUri(Uri.fromFile(destFile))
            addRequestHeader("User-Agent", "game-twin-apk-resolver/1")
        }

        val downloadId = try {
            systemDownloadManager.enqueue(request)
        } catch (e: Exception) {
            updateState(appId, ApkDownloadState.Failed(e.message ?: "Failed to start download"))
            return
        }
        downloadIds[downloadId] = DownloadInfo(appId, fileName, resolved.version)

        // Start progress polling for this download
        scope?.launch {
            pollProgress(downloadId, appId)
        }
    }

    private suspend fun pollProgress(downloadId: Long, appId: String) {
        val query = DownloadManager.Query().setFilterById(downloadId)
        while (true) {
            val state = _states.value[appId]
            if (state !is ApkDownloadState.Downloading) break

            val cursor = systemDownloadManager.query(query)
            if (cursor != null && cursor.moveToFirst()) {
                val statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                val bytesIdx = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                val totalIdx = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)

                if (statusIdx >= 0) {
                    val status = cursor.getInt(statusIdx)
                    if (status == DownloadManager.STATUS_FAILED) {
                        val reasonIdx = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)
                        val reason = if (reasonIdx >= 0) cursor.getInt(reasonIdx) else 0
                        updateState(appId, ApkDownloadState.Failed("Download failed (code $reason)"))
                        cursor.close()
                        break
                    }
                }

                if (bytesIdx >= 0 && totalIdx >= 0) {
                    val bytes = cursor.getLong(bytesIdx)
                    val total = cursor.getLong(totalIdx)
                    val progress = if (total > 0) bytes.toFloat() / total else null
                    updateState(appId, ApkDownloadState.Downloading(progress, bytes, total))
                }
                cursor.close()
            } else {
                cursor?.close()
            }
            delay(500)
        }
    }

    private fun registerCompletionReceiver() {
        if (receiver != null) return
        receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                val info = downloadIds.remove(id) ?: return
                onDownloadComplete(id, info)
            }
        }
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
    }

    private fun onDownloadComplete(downloadId: Long, info: DownloadInfo) {
        val query = DownloadManager.Query().setFilterById(downloadId)
        val cursor = systemDownloadManager.query(query)
        if (cursor != null && cursor.moveToFirst()) {
            val statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
            val status = if (statusIdx >= 0) cursor.getInt(statusIdx) else -1
            cursor.close()

            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                val file = File(downloadDir, info.fileName)
                if (file.exists()) {
                    updateState(info.appId, ApkDownloadState.Ready(file, info.version))
                } else {
                    updateState(info.appId, ApkDownloadState.Failed("File not found after download"))
                }
            } else {
                updateState(info.appId, ApkDownloadState.Failed("Download failed"))
            }
        } else {
            cursor?.close()
            updateState(info.appId, ApkDownloadState.Failed("Download status unknown"))
        }
        // Unregister receiver if no more active system downloads
        if (downloadIds.isEmpty()) {
            unregisterReceiver()
        }
    }

    /**
     * Re-checks the system DownloadManager for the current status of a download.
     * Called when resuming from background to reconcile state.
     */
    fun recheckDownloadStatus(appId: String) {
        val entry = downloadIds.entries.firstOrNull { it.value.appId == appId } ?: return
        val query = DownloadManager.Query().setFilterById(entry.key)
        val cursor = systemDownloadManager.query(query)
        if (cursor != null && cursor.moveToFirst()) {
            val statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
            val status = if (statusIdx >= 0) cursor.getInt(statusIdx) else -1
            when (status) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    val info = entry.value
                    val file = File(downloadDir, info.fileName)
                    if (file.exists()) {
                        downloadIds.remove(entry.key)
                        updateState(appId, ApkDownloadState.Ready(file, info.version))
                    }
                }
                DownloadManager.STATUS_FAILED -> {
                    downloadIds.remove(entry.key)
                    updateState(appId, ApkDownloadState.Failed("Download failed"))
                }
                DownloadManager.STATUS_RUNNING -> {
                    val bytesIdx = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                    val totalIdx = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                    if (bytesIdx >= 0 && totalIdx >= 0) {
                        val bytes = cursor.getLong(bytesIdx)
                        val total = cursor.getLong(totalIdx)
                        val progress = if (total > 0) bytes.toFloat() / total else null
                        updateState(appId, ApkDownloadState.Downloading(progress, bytes, total))
                    }
                    // Restart polling
                    scope?.launch { pollProgress(entry.key, appId) }
                }
                DownloadManager.STATUS_PENDING -> {
                    updateState(appId, ApkDownloadState.Downloading(null))
                }
            }
            cursor.close()
        } else {
            cursor?.close()
            // Download ID not found in system - it may have been cleared
            updateState(appId, ApkDownloadState.Failed("Download lost"))
        }
    }

    /**
     * Downloads an APK from a known URL without resolution (e.g., Obtainium itself).
     */
    fun downloadDirectUrl(appId: String, downloadUrl: String, fileName: String, version: String?) {
        registerCompletionReceiver()
        if (scope == null) {
            val job = Job()
            scope = CoroutineScope(dispatcher + job)
            parentJob = job
        }
        enqueueDownload(appId, ApkResolveResult.Success(downloadUrl, fileName, version))
    }

    fun retryApp(app: RecommendedApp) {
        if (scope == null) {
            val job = Job()
            scope = CoroutineScope(dispatcher + job)
            parentJob = job
        }
        // Registration is idempotent, and the previous batch unregisters on its last completion:
        // without this a retry would enqueue a download nobody ever marks Ready.
        registerCompletionReceiver()
        scope!!.launch {
            concurrency.acquire()
            try {
                processApp(app)
            } finally {
                concurrency.release()
            }
        }
    }

    fun markInstalling(appId: String) {
        updateState(appId, ApkDownloadState.Installing)
    }

    /**
     * Hands a downloaded APK to the system installer. No-op unless the app is [ApkDownloadState.Ready].
     */
    fun installDownloaded(appId: String) {
        val state = _states.value[appId] as? ApkDownloadState.Ready ?: return
        try {
            markInstalling(appId)
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                state.file,
            )
            context.startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            )
        } catch (e: Exception) {
            markReady(appId, state.file, state.version)
        }
    }

    fun markInstalled(appId: String) {
        updateState(appId, ApkDownloadState.Installed)
        // Clean up the APK file after successful install
        downloadDir.listFiles()?.filter { it.name.startsWith("${appId}_") }?.forEach { it.delete() }
    }

    fun markReady(appId: String, file: File, version: String?) {
        updateState(appId, ApkDownloadState.Ready(file, version))
    }

    fun cancelApp(appId: String) {
        val entry = downloadIds.entries.firstOrNull { it.value.appId == appId }
        if (entry != null) {
            systemDownloadManager.remove(entry.key)
            downloadIds.remove(entry.key)
        }
        val current = _states.value.toMutableMap()
        current.remove(appId)
        _states.value = current
        downloadDir.listFiles()?.filter { it.name.startsWith("${appId}_") }?.forEach { it.delete() }
    }

    fun cancelAll() {
        parentJob?.cancel()
        parentJob = null
        scope = null
        downloadIds.keys.toList().forEach { systemDownloadManager.remove(it) }
        downloadIds.clear()
        downloadDir.listFiles()?.forEach { it.delete() }
        _states.value = emptyMap()
        unregisterReceiver()
    }

    private fun unregisterReceiver() {
        receiver?.let {
            try { context.unregisterReceiver(it) } catch (_: Exception) {}
        }
        receiver = null
    }

    private fun updateState(appId: String, state: ApkDownloadState) {
        _states.value = _states.value.toMutableMap().apply { put(appId, state) }
    }
}
