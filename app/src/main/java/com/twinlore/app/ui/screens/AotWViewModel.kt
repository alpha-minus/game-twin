package com.twinlore.app.ui.screens

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twinlore.app.data.retroachievements.RetroAchievementsApiClient
import com.twinlore.app.data.retroachievements.RetroAchievementsCredentialsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

sealed class AotWUiState {
    data object Loading : AotWUiState()

    data class Loaded(
        val gameId: Int,
        val gameTitle: String,
        val consoleName: String,
        val achievementTitle: String,
        val achievementDescription: String,
        val achievementPoints: Int,
        val achievementAuthor: String,
        val achievementBadgeUrl: String?,
        val totalPlayers: Int,
        val unlocksCount: Int,
        val unlocksHardcoreCount: Int,
        val numAchievements: Int,
        val medianTimeToBeatSeconds: Int?,
        val medianTimeToBeatHardcoreSeconds: Int?,
        val medianTimeToCompleteSeconds: Int?,
        val medianTimeToMasterSeconds: Int?,
        val genre: String?,
        val developer: String?,
        val publisher: String?,
        val released: String?,
        val imageIcon: String?,
        val imageTitle: String?,
        val imageIngame: String?,
        val imageBoxArt: String?,
        val endsAtMillis: Long?
    ) : AotWUiState()

    data object Error : AotWUiState()
}

@HiltViewModel
class AotWViewModel @Inject constructor(
    private val apiClient: RetroAchievementsApiClient
) : ViewModel() {

    private val _aotWState = MutableStateFlow<AotWUiState>(AotWUiState.Loading)
    val aotWState: StateFlow<AotWUiState> = _aotWState.asStateFlow()

    init {
        loadAotW()
    }

    private fun loadAotW() {
        val cached = cachedState
        if (cached != null && System.currentTimeMillis() - cachedAtMillis < CACHE_TTL_MS) {
            _aotWState.value = cached
            return
        }

        viewModelScope.launch {
            _aotWState.value = AotWUiState.Loading
            try {
                val loaded = withContext(Dispatchers.IO) {
                    val apiKey = RetroAchievementsCredentialsRepository.BUNDLED_API_KEY
                    val aotw = apiClient.api.getAchievementOfTheWeek(apiKey)

                    val gameId = aotw.game?.id
                        ?: throw IllegalStateException("AotW response missing game ID")
                    val achievementId = aotw.achievement?.id

                    val gameDeferred = async { apiClient.api.getGameExtended(apiKey, gameId) }
                    val progressionDeferred = async { apiClient.api.getGameProgression(apiKey, gameId) }

                    val game = gameDeferred.await()
                    val progression = progressionDeferred.await()

                    val badgeName = achievementId?.let { id ->
                        game.achievements?.get(id.toString())?.badgeName
                    }

                    val endsAtMillis = aotw.startAt?.let { parseEndsAt(it) }

                    AotWUiState.Loaded(
                        gameId = gameId,
                        gameTitle = aotw.game.title ?: game.title ?: "",
                        consoleName = aotw.console?.title ?: game.consoleName ?: "",
                        achievementTitle = aotw.achievement?.title ?: "",
                        achievementDescription = aotw.achievement?.description ?: "",
                        achievementPoints = aotw.achievement?.points ?: 0,
                        achievementAuthor = aotw.achievement?.author ?: "",
                        achievementBadgeUrl = badgeName?.let { "$RA_BADGE_BASE$it.png" },
                        totalPlayers = aotw.totalPlayers ?: 0,
                        unlocksCount = aotw.unlocksCount ?: 0,
                        unlocksHardcoreCount = aotw.unlocksHardcoreCount ?: 0,
                        numAchievements = game.numAchievements ?: 0,
                        medianTimeToBeatSeconds = progression.medianTimeToBeat,
                        medianTimeToBeatHardcoreSeconds = progression.medianTimeToBeatHardcore,
                        medianTimeToCompleteSeconds = progression.medianTimeToComplete,
                        medianTimeToMasterSeconds = progression.medianTimeToMaster,
                        genre = game.genre?.takeIf { it.isNotBlank() },
                        developer = game.developer?.takeIf { it.isNotBlank() },
                        publisher = game.publisher?.takeIf { it.isNotBlank() },
                        released = game.released?.takeIf { it.isNotBlank() }?.take(10),
                        imageIcon = game.imageIcon,
                        imageTitle = game.imageTitle,
                        imageIngame = game.imageIngame,
                        imageBoxArt = game.imageBoxArt,
                        endsAtMillis = endsAtMillis
                    )
                }
                cachedState = loaded
                cachedAtMillis = System.currentTimeMillis()
                _aotWState.value = loaded
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load Achievement of the Week", e)
                _aotWState.value = AotWUiState.Error
            }
        }
    }

    companion object {
        private const val TAG = "AotWViewModel"
        private const val CACHE_TTL_MS = 60 * 60 * 1000L // 1 hour
        private const val RA_BADGE_BASE = com.twinlore.app.data.retroachievements.RaUrls.BADGE_BASE

        @Volatile private var cachedState: AotWUiState.Loaded? = null
        @Volatile private var cachedAtMillis: Long = 0L

        fun clearCache() {
            cachedState = null
            cachedAtMillis = 0L
        }

        fun formatMedianTime(seconds: Int?): String? {
            if (seconds == null || seconds == 0) return null
            val totalMinutes = seconds / 60
            val hours = totalMinutes / 60
            val minutes = totalMinutes % 60
            return when {
                hours == 0 && minutes == 0 -> "<1m"
                hours == 0 -> "~${minutes}m"
                minutes == 0 -> "~${hours}h"
                else -> "~${hours}h ${minutes}m"
            }
        }

        private const val WEEK_MS = 7L * 24 * 60 * 60 * 1000

        private fun parseEndsAt(startAt: String): Long? {
            return try {
                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
                sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
                val start = sdf.parse(startAt) ?: return null
                start.time + WEEK_MS
            } catch (_: Exception) { null }
        }

        fun formatCountdown(endsAtMillis: Long?): String? {
            if (endsAtMillis == null) return null
            val remaining = endsAtMillis - System.currentTimeMillis()
            if (remaining <= 0) return "Ended"
            val days = remaining / (24 * 60 * 60 * 1000)
            val hours = (remaining % (24 * 60 * 60 * 1000)) / (60 * 60 * 1000)
            return when {
                days > 0 -> "${days}d ${hours}h left"
                else -> "${hours}h left"
            }
        }
    }
}
