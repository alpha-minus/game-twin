package com.twinlore.app.data.retroachievements

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Loads RetroAchievements consoles and games from bundled data (data/retroachievements).
 * Used for "Choose from list" in the achievement drawer. Data is merged into assets
 * via build.gradle sourceSets from project data/retroachievements folder.
 */
@Singleton
class RaLocalDataRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val gson = com.google.gson.Gson()

    companion object {
        private const val TAG = "RaLocalData"
        private const val ASSET_CONSOLES = "consoles.json"
        private const val ASSET_GAMES_DIR = "games"
    }

    /** Load list of consoles. Returns empty list if file missing or invalid. */
    suspend fun getConsoles(): List<RaLocalConsole> = withContext(Dispatchers.IO) {
        runCatching {
            context.assets.open(ASSET_CONSOLES).use { stream ->
                val reader = InputStreamReader(stream, Charsets.UTF_8)
                val array = gson.fromJson(reader, Array<RaLocalConsole>::class.java)
                array?.toList()?.filter { it.isGameSystem != false } ?: emptyList()
            }
        }.getOrElse { e ->
            Log.w(TAG, "Could not load $ASSET_CONSOLES", e)
            emptyList()
        }
    }

    /** Load games for a console. Returns empty list if file missing or invalid. */
    suspend fun getGamesForConsole(consoleId: Int): List<RaLocalGame> = withContext(Dispatchers.IO) {
        runCatching {
            val path = "$ASSET_GAMES_DIR/$consoleId.json"
            context.assets.open(path).use { stream ->
                val reader = InputStreamReader(stream, Charsets.UTF_8)
                val file = gson.fromJson(reader, RaLocalGamesFile::class.java)
                file?.games ?: emptyList()
            }
        }.getOrElse { e ->
            Log.w(TAG, "Could not load games for console $consoleId", e)
            emptyList()
        }
    }

    /** Load all games across all consoles. Used for fallback auto-assign (closest name match). */
    suspend fun getAllGames(): List<RaLocalGame> = withContext(Dispatchers.IO) {
        val consoles = getConsoles()
        consoles.flatMap { getGamesForConsole(it.id) }
    }
}
