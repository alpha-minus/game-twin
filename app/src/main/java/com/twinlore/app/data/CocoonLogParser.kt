package com.twinlore.app.data

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Parses Cocoon's launch_debug.log to detect the most recently launched game.
 * Extracts game name, ROM path, emulator package/activity, and core name to build a launch intent.
 */
@Singleton
class CocoonLogParser @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "CocoonLogParser"
        private const val LOG_FILENAME = "launch_debug.log"

        // ══════ Launch: <Game Name> ══════  /  ══════ Swap: <Game Name> ══════
        // Cocoon writes a Swap block when the user changes game without
        // relaunching the emulator. For "what is being played now" a swap is a
        // game start just like a launch, so both open a block.
        private val LAUNCH_HEADER_REGEX = Regex("""══+ (?:Launch|Swap): (.+?) ══+""")
        // Game: <name> | File: <filename>
        private val GAME_LINE_REGEX = Regex("""Game: (.+?) \| File: (.+)""")
        // Player: RetroArch - vba_next (com.retroarch/com.retroarch.browser.retroactivity.RetroActivityFuture)
        private val PLAYER_REGEX = Regex("""Player: .+? - (\S+) \(([^/]+)/([^)]+)\)""")
        // Standalone emulator: Player: CEMU (info.cemu.cemu/.emulation.EmulationActivity)
        private val STANDALONE_PLAYER_REGEX = Regex("""Player: \S+(?:\s\S+)* \(([^/]+)/([^)]+)\)""")
        // Intent: action=android.intent.action.VIEW, data=content://..., component=ComponentInfo{pkg/act}
        private val INTENT_LINE_REGEX = Regex("""Intent: action=([^,]+), data=([^,]+), component=ComponentInfo\{([^/]+)/([^}]+)\}""")
        // {file.path} → /storage/emulated/0/ROMs/gba/game.zip
        private val FILE_PATH_REGEX = Regex("""\{file\.path\} → (.+)""")
        // Resolved file: /storage/.../<file> (exists=true, hasRealPath=true)
        private val RESOLVED_FILE_REGEX = Regex("""Resolved file: (.+?) \(exists=""")
        // Launch SUCCESS / Swap SUCCESS
        private val LAUNCH_SUCCESS_REGEX = Regex("""(?:Launch|Swap) SUCCESS""")
    }

    data class LaunchEntry(
        val gameName: String,
        val romFilename: String?,
        val romPath: String?,
        val coreName: String?,
        val packageName: String?,
        val activityClass: String?,
        val launchIntent: ESDELogParser.ParsedLaunchIntent?
    )

    /**
     * Find the launch_debug.log file in the given folder or its subfolders.
     * Returns the DocumentFile if found, null otherwise.
     */
    private fun findLogFile(folderUri: Uri): DocumentFile? {
        val treeDoc = DocumentFile.fromTreeUri(context, folderUri) ?: return null
        if (!treeDoc.exists() || !treeDoc.canRead()) return null

        // Check root first
        treeDoc.findFile(LOG_FILENAME)?.let { file ->
            if (file.exists() && file.canRead()) return file
        }

        // Search subfolders (one level deep)
        treeDoc.listFiles().forEach { child ->
            if (child.isDirectory) {
                child.findFile(LOG_FILENAME)?.let { file ->
                    if (file.exists() && file.canRead()) return file
                }
            }
        }
        return null
    }

    /**
     * Diagnostic state of the Cocoon folder. Cocoon always writes its launch
     * log automatically, so a missing log file means either the wrong folder
     * was selected, the folder is inaccessible, or no game has been launched
     * from Cocoon yet.
     */
    sealed class CocoonFolderState {
        /** The folder URI cannot be opened. */
        object FolderUnreachable : CocoonFolderState()
        /** No launch log found anywhere in the folder tree (wrong folder OR no game launched yet). */
        object NoLaunchLog : CocoonFolderState()
        /** A readable launch_debug.log exists. */
        object Valid : CocoonFolderState()
    }

    /**
     * Classify the Cocoon folder so the UI can show the matching banner/dialog.
     */
    suspend fun checkLogFolder(folderUri: Uri): CocoonFolderState = withContext(Dispatchers.IO) {
        try {
            val treeDoc = DocumentFile.fromTreeUri(context, folderUri)
                ?: return@withContext CocoonFolderState.FolderUnreachable
            if (!treeDoc.exists() || !treeDoc.canRead()) return@withContext CocoonFolderState.FolderUnreachable
            if (findLogFile(folderUri) != null) CocoonFolderState.Valid
            else CocoonFolderState.NoLaunchLog
        } catch (e: Exception) {
            Log.w(TAG, "checkLogFolder failed for $folderUri", e)
            CocoonFolderState.FolderUnreachable
        }
    }

    /**
     * Parse the Cocoon log file and return the most recently launched game.
     * Returns null if file doesn't exist, is empty, or has no successful launch entries.
     */
    suspend fun findLastLaunchedGame(folderUri: Uri): LaunchEntry? = withContext(Dispatchers.IO) {
        try {
            val logFile = findLogFile(folderUri)
            if (logFile == null) {
                Log.w(TAG, "launch_debug.log not found in folder or subfolders")
                return@withContext null
            }

            val inputStream = context.contentResolver.openInputStream(logFile.uri)
            if (inputStream == null) {
                Log.w(TAG, "Cannot open launch_debug.log")
                return@withContext null
            }

            // Read all lines and find the last successful launch block
            val allLines = inputStream.bufferedReader().use { it.readLines() }

            val entry = findLastLaunchedGame(allLines)
            if (entry == null) {
                Log.w(TAG, "No successful launch entries found in launch_debug.log")
            } else {
                Log.d(TAG, "Last launched: '${entry.gameName}', pkg=${entry.packageName}, core=${entry.coreName}")
            }
            entry
        } catch (e: Exception) {
            Log.e(TAG, "Error reading Cocoon log", e)
            null
        }
    }

    /**
     * Split the log into blocks, one per launch header, and return the most
     * recent block that reported success. Pure function over the log lines so
     * it can be exercised without SAF.
     */
    internal fun findLastLaunchedGame(allLines: List<String>): LaunchEntry? {
        val blocks = splitBlocks(allLines)
        if (blocks.isEmpty()) return null

        // Walk backwards to find the last SUCCESSFUL launch block
        for (blockLines in blocks.reversed()) {
            // Only use this block if it succeeded
            if (blockLines.none { LAUNCH_SUCCESS_REGEX.containsMatchIn(it) }) continue
            parseBlock(blockLines)?.let { return it }
        }
        return null
    }

    /**
     * Split the log into one list of lines per block, each bounded by the next
     * block header or end of file. Bounding by the next header (rather than a
     * fixed line count) keeps a block from swallowing the head of the following
     * one, which otherwise let the next block's `Game:` line overwrite this
     * block's game name in [parseBlock].
     */
    internal fun splitBlocks(allLines: List<String>): List<List<String>> {
        val blockStarts = allLines.indices.filter { LAUNCH_HEADER_REGEX.containsMatchIn(allLines[it]) }
        return blockStarts.mapIndexed { i, startIdx ->
            val endIdx = blockStarts.getOrNull(i + 1) ?: allLines.size
            allLines.subList(startIdx, endIdx)
        }
    }

    internal fun parseBlock(lines: List<String>): LaunchEntry? {
        var gameName: String? = null
        var romFilename: String? = null
        var romPath: String? = null
        var coreName: String? = null
        var packageName: String? = null
        var activityClass: String? = null
        var intentAction: String? = null
        var intentDataUri: String? = null
        var intentPkg: String? = null
        var intentAct: String? = null

        for (line in lines) {
            LAUNCH_HEADER_REGEX.find(line)?.let {
                gameName = it.groupValues[1].trim()
            }

            GAME_LINE_REGEX.find(line)?.let {
                gameName = it.groupValues[1].trim()
                romFilename = it.groupValues[2].trim()
            }

            PLAYER_REGEX.find(line)?.let {
                coreName = it.groupValues[1].trim()
                packageName = it.groupValues[2].trim()
                activityClass = it.groupValues[3].trim()
            }

            // Only try standalone regex if RetroArch regex didn't match
            if (coreName == null) {
                STANDALONE_PLAYER_REGEX.find(line)?.let {
                    packageName = it.groupValues[1].trim()
                    activityClass = it.groupValues[2].trim()
                }
            }

            INTENT_LINE_REGEX.find(line)?.let {
                intentAction = it.groupValues[1].trim()
                val data = it.groupValues[2].trim()
                intentDataUri = data.takeIf { d -> d != "null" }
                intentPkg = it.groupValues[3].trim()
                intentAct = it.groupValues[4].trim()
            }

            FILE_PATH_REGEX.find(line)?.let {
                romPath = it.groupValues[1].trim()
            }

            if (romPath == null) {
                RESOLVED_FILE_REGEX.find(line)?.let {
                    romPath = it.groupValues[1].trim()
                }
            }
        }

        if (gameName == null) return null

        val launchIntent = if (coreName != null && packageName != null && romPath != null) {
            // RetroArch path: derive core path and build RA intent
            val derivedCorePath = "/data/data/$packageName/cores/${coreName}_libretro_android.so"
            GameLauncher.buildRetroArchIntent(romPath!!, derivedCorePath, packageName!!, null)
        } else if (intentPkg != null && intentAct != null) {
            // Standalone emulator: build intent from the Intent line
            ESDELogParser.ParsedLaunchIntent(
                packageName = intentPkg!!,
                activityClass = intentAct!!,
                action = intentAction,
                dataUri = intentDataUri,
                extras = emptyMap(),
                romPath = romPath
            )
        } else null

        return LaunchEntry(
            gameName = gameName!!,
            romFilename = romFilename,
            romPath = romPath,
            coreName = coreName,
            packageName = packageName ?: intentPkg,
            activityClass = activityClass ?: intentAct,
            launchIntent = launchIntent
        )
    }

    /**
     * Extract ROM filename from the full ROM path.
     */
    fun extractRomFilename(romPath: String?): String? {
        if (romPath.isNullOrBlank()) return null
        val normalized = romPath.replace('\\', '/')
        return normalized.substringAfterLast('/').trim().takeIf { it.isNotBlank() }
    }
}
