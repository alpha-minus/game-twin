package com.twinlore.app.data.library

import android.content.Context
import com.twinlore.app.data.vgmaps.VgmapsMap
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sequential per-image fetcher for the VGMaps picker. Each map is GET'd into
 * a temp cache file and handed to [saveFile] (provided per-invocation); the
 * lambda is responsible for moving the file into the vault and registering it
 * in the maps list. Returning `false` from [saveFile] is treated as a save
 * failure (counted into [VgmapsDownloadState.Failure] but the run continues).
 *
 * Parallelism = 1, with a small politeness delay between items.
 */
@Singleton
class VgmapsBulkDownloader(
    private val httpClient: OkHttpClient,
    private val cacheDir: File,
    private val mainDispatcher: CoroutineDispatcher,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val politenessDelayMs: Long = 150L,
) {
    // Hilt entry point. The @Inject ctor delegates to the test-friendly ctor.
    @Inject constructor(
        @ApplicationContext appContext: Context,
        httpClient: OkHttpClient,
    ) : this(
        httpClient = httpClient,
        cacheDir = File(appContext.cacheDir, "vgmaps").apply { mkdirs() },
        mainDispatcher = Dispatchers.Main,
        ioDispatcher = Dispatchers.IO,
    )

    private val _state = MutableStateFlow<VgmapsDownloadState>(VgmapsDownloadState.Idle)
    val state: StateFlow<VgmapsDownloadState> = _state

    private var job: Job? = null

    /**
     * Starts a new run. If a previous run is still active, the call is a no-op.
     */
    fun start(
        maps: List<VgmapsMap>,
        saveFile: suspend (cacheFile: File, caption: String) -> Boolean,
    ) {
        if (job?.isActive == true) return
        if (maps.isEmpty()) {
            _state.value = VgmapsDownloadState.Complete(saved = 0, failures = emptyList())
            return
        }
        cacheDir.mkdirs()
        val scope = CoroutineScope(SupervisorJob() + mainDispatcher)
        job = scope.launch {
            run(maps, saveFile)
        }
    }

    fun cancel() {
        job?.cancel()
    }

    /** Resets state to Idle. Caller should only invoke when no run is active. */
    fun reset() {
        if (job?.isActive == true) return
        _state.value = VgmapsDownloadState.Idle
    }

    private suspend fun run(
        maps: List<VgmapsMap>,
        saveFile: suspend (cacheFile: File, caption: String) -> Boolean,
    ) {
        var saved = 0
        val failures = mutableListOf<VgmapsDownloadState.Failure>()
        val total = maps.size
        try {
            for ((index, map) in maps.withIndex()) {
                _state.value = VgmapsDownloadState.Downloading(
                    done = index,
                    total = total,
                    currentCaption = map.caption,
                    failures = failures.toList(),
                )
                val tempFile = File(cacheDir, "${UUID.randomUUID()}.${extOf(map.imageUrl)}")
                val fetchOk = try {
                    withContext(ioDispatcher) { fetchTo(map.imageUrl, tempFile) }
                    true
                } catch (e: CancellationException) {
                    tempFile.delete()
                    throw e
                } catch (t: Throwable) {
                    failures += VgmapsDownloadState.Failure(map, t.message ?: "fetch failed")
                    tempFile.delete()
                    false
                }
                if (fetchOk) {
                    val ok = try {
                        saveFile(tempFile, map.caption)
                    } catch (e: CancellationException) {
                        tempFile.delete()
                        throw e
                    } catch (t: Throwable) {
                        failures += VgmapsDownloadState.Failure(map, t.message ?: "save failed")
                        false
                    }
                    if (ok) saved++
                    else if (failures.none { it.map === map }) {
                        failures += VgmapsDownloadState.Failure(map, "save returned false")
                    }
                    tempFile.delete()
                }
                if (index < maps.lastIndex) delay(politenessDelayMs)
            }
            _state.value = VgmapsDownloadState.Complete(saved = saved, failures = failures.toList())
        } catch (_: CancellationException) {
            _state.value = VgmapsDownloadState.Cancelled(savedSoFar = saved)
        }
    }

    private fun fetchTo(url: String, dest: File) {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "GameTwin/vgmaps-import (+https://github.com/alpha-minus/game-twin)")
            .build()
        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("HTTP ${resp.code}")
            val body = resp.body ?: error("empty body")
            dest.outputStream().use { out -> body.byteStream().copyTo(out) }
        }
    }

    private fun extOf(url: String): String {
        val tail = url.substringAfterLast('.').lowercase()
        return when (tail) {
            "png", "jpg", "jpeg", "gif" -> tail
            else -> "png"
        }
    }
}
