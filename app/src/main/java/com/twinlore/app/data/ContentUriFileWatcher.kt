package com.twinlore.app.data

import android.content.Context
import android.net.Uri
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * File watcher for auto-detection of the currently playing game.
 *
 * Watches specific known files per source type instead of walking the entire
 * folder tree: content_history.lpl for RetroArch, logs/es_log.txt for ES-DE,
 * launch_debug.log for Cocoon.
 */
@Singleton
class ContentUriFileWatcher @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val TAG = "ContentUriFileWatcher"
        private const val FALLBACK_POLL_INTERVAL_MS = 5000L
        private const val ESDE_LOG_FILE_NAME = "es_log.txt"
        private const val COCOON_LOG_FILE_NAME = "launch_debug.log"
        private const val RETROARCH_HISTORY_FILE_NAME = "content_history.lpl"
        private const val RETROARCH_PLAYLISTS_DIR = "playlists"
        private const val RETROARCH_BUILTIN_DIR = "builtin"
        private const val SIGNATURE_IGNORED = "ignored"
    }

    /**
     * Emits when the tracked file has changed.
     * Only active when [enabledFlow] emits true and [uriFlow] emits a non-blank URI.
     */
    fun fileChanges(
        uriFlow: Flow<String?>,
        enabledFlow: Flow<Boolean>,
        sourceFlow: Flow<LastPlayedSource>
    ): Flow<Unit> = combine(
        enabledFlow,
        uriFlow,
        sourceFlow
    ) { enabled, uri, source ->
        FileWatchConfig(enabled, uri, source)
    }.distinctUntilChanged().flatMapLatest { config ->
        if (!config.enabled || config.uri.isNullOrBlank()) {
            emptyFlow()
        } else {
            watchForChanges(Uri.parse(config.uri), config.source)
        }
    }

    private data class FileWatchConfig(
        val enabled: Boolean,
        val uri: String?,
        val source: LastPlayedSource
    )

    private data class FileSignature(val marker: String)

    /**
     * One-shot launch marker for "did a new game launch happen since I last looked".
     * Same lastModified:length signature scheme as the change watcher, but exposed
     * as a nullable string: null means "no marker available" (blank URI, missing or
     * unreadable file, or a source that has no tracked file). Callers compare
     * markers for equality; the sentinel FileSignature(SIGNATURE_IGNORED) used internally
     * by the watcher must never leak out of here, because two consecutive failures
     * would then look like "nothing changed" with a value that also matches a
     * different source's failure.
     */
    suspend fun readLaunchMarker(
        uriString: String?,
        source: LastPlayedSource
    ): String? = withContext(Dispatchers.IO) {
        if (uriString.isNullOrBlank()) return@withContext null
        try {
            val uri = Uri.parse(uriString)
            when (source) {
                LastPlayedSource.ES_DE ->
                    readEsdeSignature(uri).marker.takeIf { it != SIGNATURE_IGNORED }
                LastPlayedSource.COCOON ->
                    readCocoonSignature(uri).marker.takeIf { it != SIGNATURE_IGNORED }
                LastPlayedSource.RETROARCH -> readRetroArchMarker(uri)
                else -> null
            }
        } catch (e: Exception) {
            Log.w(TAG, "readLaunchMarker failed for $source", e)
            null
        }
    }

    /**
     * RetroArch: stat playlists/builtin/content_history.lpl, falling back to the
     * legacy playlists/content_history.lpl (same lookup order as
     * RetroArchHistoryParser).
     */
    private fun readRetroArchMarker(folderUri: Uri): String? {
        val tree = DocumentFile.fromTreeUri(context, folderUri) ?: return null
        val playlists = tree.findFile(RETROARCH_PLAYLISTS_DIR) ?: return null
        val historyFile = playlists.findFile(RETROARCH_BUILTIN_DIR)?.findFile(RETROARCH_HISTORY_FILE_NAME)
            ?: playlists.findFile(RETROARCH_HISTORY_FILE_NAME)
            ?: return null
        if (!historyFile.exists() || !historyFile.canRead()) return null
        return "retroarch_history:${historyFile.lastModified()}:${historyFile.length()}"
    }

    private fun watchForChanges(uri: Uri, source: LastPlayedSource): Flow<Unit> {
        val signalFlow = merge(
            contentObserverSignals(uri),
            fallbackPollSignals()
        )
        return signalFlow
            .map { readSignatureTargeted(uri, source) }
            .distinctUntilChanged()
            .drop(1)
            .map { Unit }
    }

    private fun fallbackPollSignals(): Flow<Unit> = flow {
        while (true) {
            delay(FALLBACK_POLL_INTERVAL_MS)
            emit(Unit)
        }
    }

    private fun contentObserverSignals(uri: Uri): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
            override fun onChange(selfChange: Boolean, changedUri: Uri?) {
                trySend(Unit)
            }
        }
        val registered = try {
            context.contentResolver.registerContentObserver(uri, true, observer)
            true
        } catch (_: Exception) {
            false
        }
        if (registered) {
            trySend(Unit)
        }
        awaitClose {
            if (registered) {
                try {
                    context.contentResolver.unregisterContentObserver(observer)
                } catch (_: Exception) { }
            }
        }
    }.buffer(Channel.CONFLATED)

    /**
     * Targeted signature read: checks only the specific known file(s) for each
     * source type instead of walking the entire folder tree.
     */
    private suspend fun readSignatureTargeted(
        uri: Uri,
        source: LastPlayedSource
    ): FileSignature = withContext(Dispatchers.IO) {
        try {
            when (source) {
                LastPlayedSource.ES_DE -> readEsdeSignature(uri)
                LastPlayedSource.COCOON -> readCocoonSignature(uri)
                else -> FileSignature(SIGNATURE_IGNORED)
            }
        } catch (e: Exception) {
            Log.w(TAG, "readSignatureTargeted failed for $source", e)
            FileSignature(SIGNATURE_IGNORED)
        }
    }

    /**
     * ES-DE: stat logs/es_log.txt at the known fixed path.
     */
    private fun readEsdeSignature(folderUri: Uri): FileSignature {
        val tree = DocumentFile.fromTreeUri(context, folderUri)
            ?: return FileSignature(SIGNATURE_IGNORED)
        val logsDir = tree.findFile("logs")
            ?: return FileSignature(SIGNATURE_IGNORED)
        val logFile = logsDir.findFile(ESDE_LOG_FILE_NAME)
            ?: return FileSignature(SIGNATURE_IGNORED)
        if (!logFile.exists() || !logFile.canRead()) return FileSignature(SIGNATURE_IGNORED)
        return FileSignature("esde_log:${logFile.lastModified()}:${logFile.length()}")
    }

    /**
     * Cocoon: stat launch_debug.log directly in the selected folder.
     */
    private fun readCocoonSignature(folderUri: Uri): FileSignature {
        val tree = DocumentFile.fromTreeUri(context, folderUri)
            ?: return FileSignature(SIGNATURE_IGNORED)
        val logFile = tree.findFile(COCOON_LOG_FILE_NAME)
            ?: return FileSignature(SIGNATURE_IGNORED)
        if (!logFile.exists() || !logFile.canRead()) return FileSignature(SIGNATURE_IGNORED)
        return FileSignature("cocoon_log:${logFile.lastModified()}:${logFile.length()}")
    }
}
