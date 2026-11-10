package com.twinlore.app.data

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.annotations.SerializedName
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads RetroArch's `content_history.lpl` for the most-recently-played game.
 *
 * The file is looked up in two places (some RetroArch installs — particularly
 * on retro handhelds like AYN Thor — skip the `builtin/` subdir):
 *   1. `<picked-folder>/playlists/builtin/content_history.lpl`  (canonical
 *      since commit `a8a36c77` `fix(watcher): find RetroArch history at
 *      playlists/builtin/ path`)
 *   2. `<picked-folder>/playlists/content_history.lpl`          (legacy
 *      layout / minimal RetroArch builds)
 *
 * Replaces the 2026-05-22 log-file detection ([RetroArchLogParser]) which
 * required the user to enable Logging Verbosity + Log to File in RetroArch.
 * The lpl approach uses Settings → Playlists → History, which is on by
 * default in stock RetroArch.
 *
 * The first item in `items` is the most recent (RetroArch prepends). The
 * supplementary `.state*` / `.lrtl` activity-hint from the original
 * `RetroArchHistoryFileFinder` is NOT restored — the refresh button is the
 * sole detection trigger now, so same-ROM replays surface the same vault
 * (which IS the last played game).
 */
@Singleton
class RetroArchHistoryParser @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val TAG = "RetroArchHistoryParser"
        private const val PLAYLISTS_DIR = "playlists"
        private const val BUILTIN_DIR = "builtin"
        private const val HISTORY_FILE_NAME = "content_history.lpl"

        private val gson = Gson()

        /**
         * Pure parser. Parses the JSON text of a `content_history.lpl` file
         * and returns the most recent entry (the first item), or null when
         * the JSON is malformed, `items` is missing/empty, or the first
         * item has no usable `path`.
         *
         * Exposed companion-static so unit tests can exercise the parse
         * logic without instantiating the Hilt-injected class.
         */
        fun parseLpl(text: String): LaunchEntry? {
            if (text.isBlank()) return null
            val root: HistoryJson = try {
                gson.fromJson(text, HistoryJson::class.java)
            } catch (e: JsonSyntaxException) {
                Log.w(TAG, "Malformed content_history.lpl", e)
                return null
            } catch (e: Exception) {
                Log.w(TAG, "Unexpected parse failure", e)
                return null
            } ?: return null
            val item = root.items?.firstOrNull() ?: return null
            val path = item.path?.takeIf { it.isNotBlank() } ?: return null
            return buildLaunchEntry(item, path)
        }

        private fun buildLaunchEntry(item: HistoryItem, path: String): LaunchEntry {
            val romFilename = path.substringAfterLast('/').takeIf { it.isNotBlank() }
            // `gameName` prefers `label` when present; otherwise the
            // filename-without-extension (and without archive-entry tail).
            val labelGameName = item.label?.trim()?.takeIf { it.isNotBlank() }
            val derivedGameName = path
                .substringBefore('#')          // strip archive-entry tail
                .substringAfterLast('/')       // basename
                .substringBeforeLast('.')      // strip extension
            val gameName = labelGameName ?: derivedGameName
            val corePath = item.corePath?.takeIf { it.isNotBlank() }
            val coreName = item.coreName?.takeIf { it.isNotBlank() }
            val packageName = corePath?.let { GameLauncher.derivePackageFromCorePath(it) }
            val activityClass = packageName?.let { "$it.browser.retroactivity.RetroActivityFuture" }
            val launchIntent = if (packageName != null && corePath != null) {
                GameLauncher.buildRetroArchIntent(path, corePath, packageName, null)
            } else null
            return LaunchEntry(
                gameName = gameName,
                romFilename = romFilename,
                romPath = path,
                coreName = coreName,
                packageName = packageName,
                activityClass = activityClass,
                launchIntent = launchIntent,
            )
        }

        // -- Gson DTOs ---------------------------------------------------------
        //
        // SerializedName is required because RetroArch uses snake_case field
        // names; default Gson naming policy is camelCase.

        // Defaults (`= null`) make Kotlin synthesize a Java no-arg constructor
        // (the `<init>()V` bridge), which Gson's ConstructorConstructor finds
        // via reflection without needing `sun.misc.Unsafe`. R8 release builds
        // strip the Unsafe-allocation path for these obfuscated classes, which
        // surfaces as `JsonIOException: Abstract classes can't be instantiated`
        // at runtime — release-only, since debug skips R8. See proguard-rules.pro
        // for the generic `@SerializedName` keep rule that protects future DTOs.
        private data class HistoryJson(
            @SerializedName("items") val items: List<HistoryItem>? = null,
        )

        private data class HistoryItem(
            @SerializedName("path") val path: String? = null,
            @SerializedName("label") val label: String? = null,
            @SerializedName("core_path") val corePath: String? = null,
            @SerializedName("core_name") val coreName: String? = null,
        )
    }

    /**
     * Same shape as the deleted `RetroArchLogParser.LaunchEntry` so
     * [SyncLatestPlayedGameUseCase.executeRetroArch] is a one-line swap.
     */
    data class LaunchEntry(
        val gameName: String,
        val romFilename: String?,
        val romPath: String?,
        val coreName: String?,
        val packageName: String?,
        val activityClass: String?,
        val launchIntent: ESDELogParser.ParsedLaunchIntent?,
    )

    /**
     * Three-state classification of the picked RetroArch folder, mirroring
     * [ESDELogParser.ESDEFolderState] / [CocoonLogParser.CocoonFolderState]
     * so [SettingsViewModel.retroArchFolderState] can drive the same
     * "✓ when valid" UI affordance the other sources use.
     *
     * - [FolderUnreachable]: tree URI doesn't resolve, or doesn't represent
     *   a readable directory (revoked permission, removed SD card, etc.)
     * - [FileNotFound]: directory walks fine but neither
     *   `playlists/builtin/content_history.lpl` nor `playlists/content_history.lpl`
     *   exists. Usually means the wrong parent was picked.
     * - [Valid]: file is located, readable, and `parseLpl` returns a
     *   [LaunchEntry] from its first `items` row.
     */
    sealed class RetroArchFolderState {
        data object FolderUnreachable : RetroArchFolderState()
        data object FileNotFound : RetroArchFolderState()
        data object Valid : RetroArchFolderState()
    }

    /**
     * Classify the picked folder without returning the parsed entry.
     * Used by Settings UI to show a status checkmark. Same lookup paths
     * as [findLastPlayedGame].
     */
    suspend fun checkLogFolder(folderUri: Uri): RetroArchFolderState =
        withContext(Dispatchers.IO) {
            val lookup = locateLpl(folderUri)
            when (lookup) {
                LplLookup.FolderUnreachable -> RetroArchFolderState.FolderUnreachable
                LplLookup.FileNotFound -> RetroArchFolderState.FileNotFound
                is LplLookup.Found -> {
                    val text = lookup.read() ?: return@withContext RetroArchFolderState.FileNotFound
                    if (Companion.parseLpl(text) != null) RetroArchFolderState.Valid
                    else RetroArchFolderState.FileNotFound
                }
            }
        }

    /**
     * Locates `<folderUri>/playlists/builtin/content_history.lpl` via SAF
     * and parses its first item. Returns null on any folder-walk or parse
     * failure — the caller treats all four "no game" cases (unreachable
     * folder, missing path, empty file, empty items) as a single unified
     * empty-state.
     */
    suspend fun findLastPlayedGame(folderUri: Uri): LaunchEntry? =
        withContext(Dispatchers.IO) {
            val text = readLplText(folderUri) ?: return@withContext null
            Companion.parseLpl(text)
        }

    /**
     * Walk result for the two-path lookup. Separating "directory missing"
     * from "file missing" lets [checkLogFolder] surface distinct states
     * without duplicating the SAF walk.
     */
    private sealed class LplLookup {
        data object FolderUnreachable : LplLookup()
        data object FileNotFound : LplLookup()
        class Found(val read: () -> String?) : LplLookup()
    }

    private fun locateLpl(folderUri: Uri): LplLookup {
        val tree = DocumentFile.fromTreeUri(context, folderUri)
        if (tree == null || !tree.exists() || !tree.isDirectory) {
            Log.w(Companion.TAG, "lpl lookup failed: folder unreachable: $folderUri")
            return LplLookup.FolderUnreachable
        }
        val playlists = tree.findFile(Companion.PLAYLISTS_DIR)
        if (playlists == null || !playlists.isDirectory) {
            Log.w(Companion.TAG, "lpl lookup failed: '${Companion.PLAYLISTS_DIR}/' subdir not found under $folderUri")
            return LplLookup.FileNotFound
        }
        val builtin = playlists.findFile(Companion.BUILTIN_DIR)
        val file = if (builtin != null && builtin.isDirectory) {
            builtin.findFile(Companion.HISTORY_FILE_NAME)
        } else {
            Log.w(Companion.TAG, "lpl lookup: '${Companion.PLAYLISTS_DIR}/${Companion.BUILTIN_DIR}/' subdir missing — falling back to '${Companion.PLAYLISTS_DIR}/' direct lookup")
            null
        } ?: playlists.findFile(Companion.HISTORY_FILE_NAME)
        if (file == null || !file.exists() || !file.canRead()) {
            Log.w(Companion.TAG, "lpl lookup failed: '${Companion.HISTORY_FILE_NAME}' not found in either playlists/builtin/ or playlists/ under $folderUri")
            return LplLookup.FileNotFound
        }
        return LplLookup.Found {
            try {
                context.contentResolver.openInputStream(file.uri)?.use { stream ->
                    stream.bufferedReader().readText()
                }
            } catch (e: Exception) {
                Log.w(Companion.TAG, "lpl lookup: file located at ${file.uri} but read failed", e)
                null
            }
        }
    }

    /**
     * Thin adapter over [locateLpl] for callers that just want the text or
     * null. Diagnostic `Log.w` lines are emitted by [locateLpl] itself so
     * `adb logcat | grep RetroArchHistoryParser` still shows exactly which
     * step failed.
     */
    private fun readLplText(folderUri: Uri): String? = when (val lookup = locateLpl(folderUri)) {
        LplLookup.FolderUnreachable, LplLookup.FileNotFound -> null
        is LplLookup.Found -> lookup.read()
    }
}
