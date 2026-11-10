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

@Singleton
class ESDELogParser @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "ESDELogParser"

        /**
         * Derive a file path from a content URI string.
         * Handles SAF document URIs (com.android.externalstorage.documents) and
         * ES-DE FileProvider URIs (org.es_de.frontend.files/external/...).
         * Uses string parsing to avoid android.net.Uri dependency in unit tests.
         */
        fun deriveFilePathFromUri(uriString: String): String? {
            if (!uriString.startsWith("content://")) return null

            val withoutScheme = uriString.removePrefix("content://")
            val slashIdx = withoutScheme.indexOf('/')
            if (slashIdx < 0) return null
            val authority = withoutScheme.substring(0, slashIdx)
            val path = withoutScheme.substring(slashIdx)

            if (authority == "com.android.externalstorage.documents") {
                // SAF document URI: extract document ID and convert to path
                // e.g. .../document/primary%3AROMs%2Fpsp%2Fgame.chd → /storage/emulated/0/ROMs/psp/game.chd
                val docSegment = "/document/"
                val docIdx = path.indexOf(docSegment)
                if (docIdx < 0) return null
                val encodedDocId = path.substring(docIdx + docSegment.length)
                val docId = java.net.URLDecoder.decode(encodedDocId, "UTF-8")
                val colonIdx = docId.indexOf(':')
                if (colonIdx < 0) return null
                val relativePath = docId.substring(colonIdx + 1)
                return "/storage/emulated/0/$relativePath"
            }

            if (authority.contains("es_de") || authority.contains("es-de")) {
                // ES-DE FileProvider: /external/<path> maps to /storage/emulated/0/<path>
                val prefix = "/external/"
                if (path.startsWith(prefix)) {
                    val encoded = path.substring(prefix.length)
                    return "/storage/emulated/0/${java.net.URLDecoder.decode(encoded, "UTF-8")}"
                }
            }

            return null
        }

        private val LAUNCH_REGEX = Regex("""Launching game "(.+?)" from system "(.+?)".*""")
        private val ROM_EXPANDED_REGEX = Regex("""%ROM% expanded: (.+)""")
        // Expanded arguments block — these are the resolved values ES-DE actually passes.
        // Lines have timestamp prefix like "May 04 07:25:46 Info:   Package: com.retroarch"
        private val PACKAGE_LINE_REGEX = Regex("""Package: (.+)""")
        private val ACTIVITY_LINE_REGEX = Regex("""Activity: (.+)""")
        private val ACTION_LINE_REGEX = Regex("""Action: (.+)""")
        private val DATA_LINE_REGEX = Regex("""Data: (.+)""")
        // Matches any %PLACEHOLDER% expanded: line EXCEPT %ROM% (e.g. %ROMPROVIDER%, %ROMSAF%)
        private val DATA_EXPANDED_REGEX = Regex("""%(?!ROM%)\w+% expanded: (.+)""")
        private val EXTRA_NAME_REGEX = Regex("""Extra name: (.+)""")
        private val EXTRA_VALUE_REGEX = Regex("""Extra value: (.+)""")
        private val EXTRA_INT_NAME_REGEX = Regex("""Extra integer name: (.+)""")
        private val EXTRA_INT_VALUE_REGEX = Regex("""Extra integer value: (.+)""")

        /**
         * Parse the expanded launch arguments from the context window.
         * ES-DE logs the exact intent it fires:
         *   Package: com.retroarch
         *   Activity: com.retroarch.browser.retroactivity.RetroActivityFuture
         *   Extra name: CONFIGFILE
         *   Extra value: /storage/.../retroarch.cfg
         *   Extra name: LIBRETRO
         *   Extra value: /data/.../mgba_libretro_android.so
         *   Extra name: ROM
         *   Extra value: %ROM%
         *   %ROM% expanded: /storage/.../game.zip
         */
        /**
         * Parse the "Expanded emulator launch arguments" block from ES-DE log lines.
         * Captures the complete intent: package, activity, and ALL extras (not just RetroArch ones).
         * The ROM extra value is "%ROM%" placeholder — we substitute it with the expanded path.
         */
        fun parseLaunchCommandFromLines(lines: List<String>): ParsedLaunchIntent? {
            var packageName: String? = null
            var activityClass: String? = null
            var action: String? = null
            var dataUri: String? = null
            var dataHasPlaceholder = false
            var romPath: String? = null
            var lastExtraName: String? = null
            var lastIntExtraName: String? = null
            // Track which extra holds a placeholder so the expanded line can update it
            var extraWithPlaceholder: String? = null
            val extras = mutableMapOf<String, ParsedLaunchIntent.TypedExtra>()

            for (line in lines) {
                PACKAGE_LINE_REGEX.find(line)?.let {
                    packageName = it.groupValues[1].trim()
                }

                ACTIVITY_LINE_REGEX.find(line)?.let {
                    activityClass = it.groupValues[1].trim()
                }

                ACTION_LINE_REGEX.find(line)?.let {
                    action = it.groupValues[1].trim()
                }

                DATA_LINE_REGEX.find(line)?.let {
                    val value = it.groupValues[1].trim()
                    dataUri = value
                    dataHasPlaceholder = value.startsWith("%")
                }

                EXTRA_NAME_REGEX.find(line)?.let {
                    lastExtraName = it.groupValues[1].trim()
                }

                EXTRA_VALUE_REGEX.find(line)?.let {
                    val value = it.groupValues[1].trim()
                    val name = lastExtraName
                    if (name != null) {
                        extras[name] = ParsedLaunchIntent.TypedExtra("string", value)
                        if (value.startsWith("%")) {
                            extraWithPlaceholder = name
                        }
                    }
                    lastExtraName = null
                }

                EXTRA_INT_NAME_REGEX.find(line)?.let {
                    lastIntExtraName = it.groupValues[1].trim()
                }

                EXTRA_INT_VALUE_REGEX.find(line)?.let {
                    val value = it.groupValues[1].trim()
                    val name = lastIntExtraName
                    if (name != null) {
                        extras[name] = ParsedLaunchIntent.TypedExtra("int", value)
                    }
                    lastIntExtraName = null
                }

                // %PLACEHOLDER% expanded: resolves either Data: or Extra value: placeholder
                DATA_EXPANDED_REGEX.find(line.trimStart())?.let {
                    val expanded = it.groupValues[1].trim()
                    if (dataHasPlaceholder) {
                        dataUri = expanded
                        dataHasPlaceholder = false
                    } else if (extraWithPlaceholder != null) {
                        extras[extraWithPlaceholder!!] = ParsedLaunchIntent.TypedExtra("string", expanded)
                        extraWithPlaceholder = null
                    }
                }

                ROM_EXPANDED_REGEX.find(line.trimStart())?.let {
                    romPath = it.groupValues[1].trim()
                }
            }

            if (packageName == null || activityClass == null) return null

            // Replace %ROM% placeholder with actual expanded path
            if (romPath != null && extras.containsKey("ROM")) {
                extras["ROM"] = ParsedLaunchIntent.TypedExtra("string", romPath!!)
            }

            // Derive romPath from dataUri or extras if not already set
            val resolvedDataUri = dataUri?.takeIf { !it.startsWith("%") }
            if (romPath == null) {
                romPath = resolvedDataUri?.let { deriveFilePathFromUri(it) }
            }
            if (romPath == null) {
                // Check extras that hold content URIs (e.g. MelonDS "uri" extra)
                for ((_, extra) in extras) {
                    if (extra.value.startsWith("content://")) {
                        romPath = deriveFilePathFromUri(extra.value)
                        if (romPath != null) break
                    }
                }
            }

            return ParsedLaunchIntent(
                packageName = packageName!!,
                activityClass = activityClass!!,
                action = action,
                dataUri = resolvedDataUri,
                extras = extras,
                romPath = romPath
            )
        }
    }

    /**
     * Complete intent definition parsed from ES-DE/Cocoon logs.
     * Stores the exact package, activity, action, data URI, and all typed extras
     * so any emulator can be launched.
     */
    data class ParsedLaunchIntent(
        val packageName: String,
        val activityClass: String,
        val action: String? = null,
        val dataUri: String? = null,
        val extras: Map<String, TypedExtra>,
        val romPath: String? = null
    ) {
        data class TypedExtra(val type: String, val value: String)

        /** Serialize to a storable JSON string */
        fun toJson(): String {
            val obj = org.json.JSONObject()
            obj.put("pkg", packageName)
            obj.put("act", activityClass)
            if (action != null) obj.put("action", action)
            if (dataUri != null) obj.put("dataUri", dataUri)
            val extrasObj = org.json.JSONObject()
            for ((key, extra) in extras) {
                val extraObj = org.json.JSONObject()
                extraObj.put("type", extra.type)
                extraObj.put("value", extra.value)
                extrasObj.put(key, extraObj)
            }
            obj.put("extras", extrasObj)
            return obj.toString()
        }

        companion object {
            /** Deserialize from stored JSON string */
            fun fromJson(json: String): ParsedLaunchIntent? {
                return try {
                    val obj = org.json.JSONObject(json)
                    val pkg = obj.getString("pkg")
                    val act = obj.getString("act")
                    val action = obj.optString("action").takeIf { it.isNotEmpty() }
                    val dataUri = obj.optString("dataUri").takeIf { it.isNotEmpty() }
                    val extrasObj = obj.getJSONObject("extras")
                    val extras = mutableMapOf<String, TypedExtra>()
                    extrasObj.keys().forEach { key ->
                        val v = extrasObj.get(key)
                        if (v is org.json.JSONObject) {
                            extras[key] = TypedExtra(
                                type = v.getString("type"),
                                value = v.getString("value")
                            )
                        } else if (v is String) {
                            // Legacy format: plain string value
                            extras[key] = TypedExtra("string", v)
                        }
                    }
                    ParsedLaunchIntent(pkg, act, action, dataUri, extras, null)
                } catch (e: Exception) {
                    null
                }
            }
        }
    }

    data class LaunchEntry(
        val gameName: String,
        val system: String,
        val romPath: String?,
        val launchIntent: ParsedLaunchIntent? = null
    )

    /**
     * Diagnostic state of the ES-DE folder, used to drive accurate UI banners
     * and the help dialog. ES-DE only writes the log file when Debug Mode is
     * on (Settings → Other Settings → Debug Mode).
     */
    sealed class ESDEFolderState {
        /** The folder URI cannot be opened (revoked permission, deleted, unmounted). */
        object FolderUnreachable : ESDEFolderState()
        /** No `logs/` (or `gamelists/`) subdirectory — almost certainly not the ES-DE parent folder. */
        object NotESDEFolder : ESDEFolderState()
        /** Subdirectory exists but `logs/es_log.txt` is missing — Debug Mode is off. */
        object DebugModeNotEnabled : ESDEFolderState()
        /** A readable `es_log.txt` exists. */
        object Valid : ESDEFolderState()
    }

    /**
     * Classify the ES-DE folder so the UI can show the matching banner/dialog.
     */
    suspend fun checkLogFolder(esFolderUri: Uri): ESDEFolderState = withContext(Dispatchers.IO) {
        try {
            val treeDoc = DocumentFile.fromTreeUri(context, esFolderUri)
                ?: return@withContext ESDEFolderState.FolderUnreachable
            if (!treeDoc.exists() || !treeDoc.isDirectory) return@withContext ESDEFolderState.FolderUnreachable
            val logsDir = treeDoc.findFile("logs")
            val gamelistsDir = treeDoc.findFile("gamelists")
            val looksLikeESDE = (logsDir != null && logsDir.isDirectory) ||
                (gamelistsDir != null && gamelistsDir.isDirectory)
            if (!looksLikeESDE) return@withContext ESDEFolderState.NotESDEFolder
            val logFile = logsDir?.findFile("es_log.txt")
            if (logFile == null || !logFile.exists() || !logFile.canRead()) {
                return@withContext ESDEFolderState.DebugModeNotEnabled
            }
            ESDEFolderState.Valid
        } catch (e: Exception) {
            Log.w(TAG, "checkLogFolder failed for $esFolderUri", e)
            ESDEFolderState.FolderUnreachable
        }
    }

    /**
     * Parse the ES-DE log file and return the most recently launched game.
     * Reads from <esFolderUri>/logs/es_log.txt via SAF.
     * Returns null if file doesn't exist, is empty, or has no launch entries.
     */
    suspend fun findLastLaunchedGame(esFolderUri: Uri): LaunchEntry? = withContext(Dispatchers.IO) {
        try {
            val treeDoc = DocumentFile.fromTreeUri(context, esFolderUri)
            if (treeDoc == null || !treeDoc.exists() || !treeDoc.canRead()) {
                Log.w(TAG, "Cannot access ES-DE folder: $esFolderUri")
                return@withContext null
            }

            val logsDir = treeDoc.findFile("logs")
            if (logsDir == null || !logsDir.exists() || !logsDir.isDirectory) {
                Log.w(TAG, "No 'logs' directory found in ES-DE folder")
                return@withContext null
            }

            val logFile = logsDir.findFile("es_log.txt")
            if (logFile == null || !logFile.exists() || !logFile.canRead()) {
                Log.w(TAG, "es_log.txt not found or not readable")
                return@withContext null
            }

            val inputStream = context.contentResolver.openInputStream(logFile.uri)
            if (inputStream == null) {
                Log.w(TAG, "Cannot open es_log.txt")
                return@withContext null
            }

            // Read all lines to find the last game launch entry
            val allLines = mutableListOf<String>()
            var lastGameName: String? = null
            var lastSystem: String? = null
            var lastLaunchLineIndex = -1

            inputStream.bufferedReader().use { reader ->
                reader.forEachLine { line ->
                    val index = allLines.size
                    allLines.add(line)
                    val trimmed = line.trimStart()

                    val launchMatch = LAUNCH_REGEX.find(trimmed)
                    if (launchMatch != null) {
                        lastGameName = launchMatch.groupValues[1].trim()
                        lastSystem = launchMatch.groupValues[2].trim()
                        lastLaunchLineIndex = index
                    }
                }
            }

            if (lastGameName != null && lastLaunchLineIndex >= 0) {
                // Grab ~20 lines after the launch entry — contains the full
                // expanded arguments block with package, extras, and ROM path.
                val endIndex = minOf(lastLaunchLineIndex + 20, allLines.size)
                val contextLines = allLines.subList(lastLaunchLineIndex, endIndex)
                val parsed = parseLaunchCommandFromLines(contextLines)

                Log.d(TAG, "Last launched: '$lastGameName', intent=${parsed?.toJson()}")

                LaunchEntry(
                    gameName = lastGameName!!,
                    system = lastSystem ?: "",
                    romPath = parsed?.romPath,
                    launchIntent = parsed
                )
            } else {
                Log.w(TAG, "No game launch entries found in es_log.txt")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading ES-DE log", e)
            null
        }
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
