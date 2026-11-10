package com.twinlore.app.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import com.twinlore.app.domain.Vault
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GameLauncher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dualDisplayLauncher: DualDisplayLauncher
) {
    companion object {
        private const val TAG = "GameLauncher"

        /**
         * Derive the Android package name from a RetroArch core path.
         * Handles /data/data/<pkg>/cores/ and /data/user/0/<pkg>/cores/ prefixes.
         */
        fun derivePackageFromCorePath(corePath: String?): String? {
            if (corePath.isNullOrBlank()) return null
            val dataDataPrefix = "/data/data/"
            val dataUserPrefix = "/data/user/0/"
            val stripped = when {
                corePath.startsWith(dataDataPrefix) -> corePath.removePrefix(dataDataPrefix)
                corePath.startsWith(dataUserPrefix) -> corePath.removePrefix(dataUserPrefix)
                else -> return null
            }
            val packageName = stripped.substringBefore("/")
            return packageName.takeIf { it.isNotBlank() && it.contains(".") }
        }

        /**
         * Build a ParsedLaunchIntent from RetroArch history data.
         * This converts separate RA fields into the unified intent JSON format.
         */
        fun buildRetroArchIntent(
            romPath: String,
            corePath: String,
            emulatorPackage: String,
            configPath: String?
        ): ESDELogParser.ParsedLaunchIntent {
            val activityClass = "$emulatorPackage.browser.retroactivity.RetroActivityFuture"
            val resolvedConfig = configPath
                ?: "/storage/emulated/0/Android/data/$emulatorPackage/files/retroarch.cfg"
            val extras = mapOf(
                "ROM" to ESDELogParser.ParsedLaunchIntent.TypedExtra("string", romPath),
                "LIBRETRO" to ESDELogParser.ParsedLaunchIntent.TypedExtra("string", corePath),
                "CONFIGFILE" to ESDELogParser.ParsedLaunchIntent.TypedExtra("string", resolvedConfig)
            )
            return ESDELogParser.ParsedLaunchIntent(
                packageName = emulatorPackage,
                activityClass = activityClass,
                extras = extras,
                romPath = romPath
            )
        }
    }

    sealed class LaunchResult {
        object Success : LaunchResult()
        object NoLaunchData : LaunchResult()
        object EmulatorNotFound : LaunchResult()
        object NeedsRomsFolder : LaunchResult()
        data class LaunchFailed(val error: String) : LaunchResult()
    }

    /**
     * Launch a game from a vault using the stored launch intent JSON.
     *
     * @param currentDisplayId the display the launcher is on, supplied from a
     *   visual context (e.g. `LocalView.current.display?.displayId`). Used to
     *   pick the *other* display on multi-screen devices.
     * @param romsFolderUri persisted SAF tree URI for the ROMs folder; needed
     *   to grant URI permission to standalone emulators that receive a data URI.
     */
    fun launch(vault: Vault, currentDisplayId: Int, romsFolderUri: String? = null): LaunchResult {
        if (vault.launchIntentJson == null) {
            return LaunchResult.NoLaunchData
        }

        val parsed = ESDELogParser.ParsedLaunchIntent.fromJson(vault.launchIntentJson)
        if (parsed == null) {
            Log.w(TAG, "Failed to parse stored launch intent JSON")
            return LaunchResult.NoLaunchData
        }

        val packageManager = context.packageManager
        if (packageManager.getLaunchIntentForPackage(parsed.packageName) == null) {
            Log.w(TAG, "App not installed: ${parsed.packageName}")
            return LaunchResult.EmulatorNotFound
        }

        // If the intent needs a data URI and we don't have a ROMs folder grant, ask for one
        if (parsed.dataUri != null && romsFolderUri == null) {
            Log.w(TAG, "Launch needs data URI but no ROMs folder configured")
            return LaunchResult.NeedsRomsFolder
        }

        // Default to ACTION_VIEW when data URI is present (emulators expect VIEW for file URIs)
        val resolvedAction = parsed.action
            ?: if (parsed.dataUri != null) Intent.ACTION_VIEW else Intent.ACTION_MAIN
        val useImplicit = parsed.dataUri != null || (resolvedAction != Intent.ACTION_MAIN)

        val intent = Intent(resolvedAction).apply {
            if (useImplicit) {
                // Use implicit intent with package restriction for non-standard actions or data URIs.
                // Explicit ComponentName skips intent-filter matching which can fail.
                setPackage(parsed.packageName)
            } else {
                component = ComponentName(parsed.packageName, parsed.activityClass)
            }
            if (parsed.dataUri != null && romsFolderUri != null) {
                // Build a SAF document URI from our ROMs tree so we can grant permission
                val docUri = buildDocumentUriFromTree(romsFolderUri, vault.romPath)
                if (docUri != null) {
                    data = docUri
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    Log.d(TAG, "Using document URI from ROMs tree: $docUri")
                } else {
                    // Fallback: use the stored URI without grant flag (emulator needs own access)
                    data = android.net.Uri.parse(parsed.dataUri)
                    Log.d(TAG, "Falling back to stored dataUri (no grant)")
                }
            }
            for ((key, extra) in parsed.extras) {
                when (extra.type) {
                    "int" -> putExtra(key, extra.value.toInt())
                    "long" -> putExtra(key, extra.value.toLong())
                    "float" -> putExtra(key, extra.value.toFloat())
                    "bool" -> putExtra(key, extra.value.toBoolean())
                    else -> putExtra(key, extra.value)
                }
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT)
        }

        return try {
            val options = dualDisplayLauncher.buildLaunchOptions(currentDisplayId)
            Log.d(TAG, "Launching ${vault.displayName}: pkg=${parsed.packageName}, act=${parsed.activityClass}, action=${resolvedAction}, dataUri=${intent.data != null}, extras=${parsed.extras.keys}")
            context.startActivity(intent, options.toBundle())
            LaunchResult.Success
        } catch (e: android.content.ActivityNotFoundException) {
            // Retry with explicit component (no implicit resolution) and no display targeting
            Log.w(TAG, "Implicit launch failed, retrying with explicit component: ${e.message}")
            try {
                intent.component = ComponentName(parsed.packageName, parsed.activityClass)
                intent.setPackage(null)
                context.startActivity(intent)
                LaunchResult.Success
            } catch (e2: Exception) {
                Log.e(TAG, "Retry also failed", e2)
                LaunchResult.LaunchFailed(e2.message ?: "Unknown error")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch game", e)
            LaunchResult.LaunchFailed(e.message ?: "Unknown error")
        }
    }

    /**
     * Build a SAF document URI under our tree from the vault's file path.
     * E.g. tree=primary:ROMs + path=/storage/emulated/0/ROMs/psp/game.chd
     * → content://.../tree/primary%3AROMs/document/primary%3AROMs%2Fpsp%2Fgame.chd
     */
    private fun buildDocumentUriFromTree(treeUriString: String, filePath: String?): android.net.Uri? {
        if (filePath == null) return null
        val treeUri = android.net.Uri.parse(treeUriString)
        val treeDocId = android.provider.DocumentsContract.getTreeDocumentId(treeUri)
        val colonIdx = treeDocId.indexOf(':')
        if (colonIdx < 0) return null
        val treeRootPath = treeDocId.substring(colonIdx + 1) // e.g. "ROMs"

        val rootIdx = filePath.indexOf("/$treeRootPath/")
        if (rootIdx < 0) return null
        val relativePath = filePath.substring(rootIdx + treeRootPath.length + 2)
        val documentId = "$treeDocId/$relativePath"
        return android.provider.DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
    }
}
