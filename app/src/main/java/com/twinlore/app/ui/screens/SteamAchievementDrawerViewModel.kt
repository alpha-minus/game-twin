package com.twinlore.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twinlore.app.data.steam.SteamAchievementDisplay
import com.twinlore.app.data.steam.SteamGameProgress
import com.twinlore.app.data.steam.SteamGameCacheRepository
import com.twinlore.app.data.steam.SteamCredentialsRepository
import com.twinlore.app.data.steam.SteamApiClient
import com.twinlore.app.data.steam.SteamGameMatcher
import com.twinlore.app.data.steam.SteamOwnedGame
import com.twinlore.app.data.VaultRepository
import com.twinlore.app.data.ChecklistRepository
import com.twinlore.app.data.NetworkConnectivityManager
import com.twinlore.app.domain.ChecklistTab
import com.twinlore.app.domain.SortDirection
import com.twinlore.app.domain.SteamAchievementSortField
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject

@HiltViewModel
class SteamAchievementDrawerViewModel @Inject constructor(
    private val steamGameCacheRepository: SteamGameCacheRepository,
    private val credentialsRepository: SteamCredentialsRepository,
    private val apiClient: SteamApiClient,
    private val vaultRepository: VaultRepository,
    private val gameMatcher: SteamGameMatcher,
    private val networkConnectivityManager: NetworkConnectivityManager,
    private val checklistRepository: ChecklistRepository
) : ViewModel() {

    private val _progress = MutableStateFlow<SteamGameProgress?>(null)
    val progress: StateFlow<SteamGameProgress?> = _progress.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _isNetworkAvailable = MutableStateFlow(true)
    val isNetworkAvailable: StateFlow<Boolean> = _isNetworkAvailable.asStateFlow()

    private val _autoAssignInProgress = MutableStateFlow(false)
    val autoAssignInProgress: StateFlow<Boolean> = _autoAssignInProgress.asStateFlow()

    fun checkConnectivity() {
        _isNetworkAvailable.value = networkConnectivityManager.isNetworkAvailable()
    }

    private val _sortField = MutableStateFlow(SteamAchievementSortField.UNLOCK_TIME)
    val sortField: StateFlow<SteamAchievementSortField> = _sortField.asStateFlow()

    private val _sortDirection = MutableStateFlow(SteamAchievementSortField.UNLOCK_TIME.defaultDirection)
    val sortDirection: StateFlow<SortDirection> = _sortDirection.asStateFlow()

    /** Picking the current field flips its direction; picking another selects it at its default. */
    fun selectSort(field: SteamAchievementSortField) {
        if (_sortField.value == field) {
            _sortDirection.value = _sortDirection.value.toggle()
        } else {
            _sortField.value = field
            _sortDirection.value = field.defaultDirection
        }
    }

    private val _scrollIndex = MutableStateFlow(0)
    val scrollIndex: StateFlow<Int> = _scrollIndex.asStateFlow()
    private val _scrollOffset = MutableStateFlow(0)
    val scrollOffset: StateFlow<Int> = _scrollOffset.asStateFlow()
    fun setScrollPosition(index: Int, offset: Int) {
        _scrollIndex.value = index
        _scrollOffset.value = offset
    }

    private val _filterLockedOnly = MutableStateFlow(false)
    val filterLockedOnly: StateFlow<Boolean> = _filterLockedOnly.asStateFlow()
    fun setFilterLockedOnly(value: Boolean) { _filterLockedOnly.value = value }

    /**
     * Free-text filter over the achievement list: title and description, case-insensitive. The
     * RA drawer's twin - see `AchievementDrawerViewModel.searchQuery` for why it lives here rather
     * than in the composable.
     */
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    fun setSearchQuery(value: String) { _searchQuery.value = value }

    private val _showWallpaperView = MutableStateFlow(false)
    val showWallpaperView: StateFlow<Boolean> = _showWallpaperView.asStateFlow()
    fun setShowWallpaperView(show: Boolean) { _showWallpaperView.value = show }

    /** Error shown on the assignment (unlinked) page after auto-assign or manual link fails */
    private val _assignError = MutableStateFlow<String?>(null)
    val assignError: StateFlow<String?> = _assignError.asStateFlow()

    fun load(vaultId: String, steamAppId: Int) {
        viewModelScope.launch {
            _isNetworkAvailable.value = networkConnectivityManager.isNetworkAvailable()
            _loading.value = true
            _error.value = null
            try {
                _progress.value = steamGameCacheRepository.getGameProgress(vaultId, steamAppId, forceRefresh = false)
                if (_progress.value == null) _error.value = "Could not load Steam achievements"
            } catch (e: com.twinlore.app.data.steam.SteamGameNotOwnedException) {
                _error.value = "This game is not in your Steam library. Add it to your library first to view achievements."
            } catch (e: com.twinlore.app.data.steam.SteamProfilePrivateException) {
                _error.value = "Your Steam profile game details are set to private. Set them to Public in Steam → Edit Profile → Privacy Settings to view achievements."
            } catch (e: com.twinlore.app.data.steam.SteamGameNoAchievementsException) {
                _error.value = "This game has no achievements on Steam."
            } catch (e: Exception) {
                _error.value = classifySteamAchievementError(e)
            }
            _loading.value = false
        }
    }

    fun refresh(vaultId: String, steamAppId: Int, minRefreshIntervalMs: Long? = null) {
        viewModelScope.launch {
            _isNetworkAvailable.value = networkConnectivityManager.isNetworkAvailable()
            // Cache-first: paint THIS vault's cached snapshot instantly (snappy, no flash;
            // never the previous vault's data). Null only when there's no cache.
            val cached = steamGameCacheRepository.getCachedProgress(vaultId)
            if (cached != null) {
                _progress.value = cached
                _loading.value = false
            } else {
                _progress.value = null
                _loading.value = true
            }
            _error.value = null
            var errMsg: String? = null
            try {
                val fresh = steamGameCacheRepository.getGameProgress(vaultId, steamAppId, forceRefresh = true, minRefreshIntervalMs = minRefreshIntervalMs)
                if (fresh != null) _progress.value = fresh
            } catch (e: com.twinlore.app.data.steam.SteamGameNotOwnedException) {
                errMsg = "This game is not in your Steam library. Add it to your library first to view achievements."
            } catch (e: com.twinlore.app.data.steam.SteamProfilePrivateException) {
                errMsg = "Your Steam profile game details are set to private. Set them to Public in Steam → Edit Profile → Privacy Settings to view achievements."
            } catch (e: com.twinlore.app.data.steam.SteamGameNoAchievementsException) {
                errMsg = "This game has no achievements on Steam."
            } catch (e: Exception) {
                errMsg = classifySteamAchievementError(e)
            }
            // Only surface an error if we have nothing to show (keep the cached view otherwise).
            if (_progress.value == null) _error.value = errMsg ?: "Could not load Steam achievements"
            _loading.value = false
        }
    }

    fun refreshCurrentSet(vaultId: String) {
        _progress.value?.steamAppId?.let { appId ->
            viewModelScope.launch {
                _isNetworkAvailable.value = networkConnectivityManager.isNetworkAvailable()
                _loading.value = true
                _error.value = null
                try {
                    _progress.value = steamGameCacheRepository.getGameProgress(vaultId, appId, forceRefresh = true)
                    if (_progress.value == null) _error.value = "Could not load Steam achievements"
                } catch (e: com.twinlore.app.data.steam.SteamGameNotOwnedException) {
                    _error.value = "This game is not in your Steam library. Add it to your library first to view achievements."
                } catch (e: com.twinlore.app.data.steam.SteamProfilePrivateException) {
                    _error.value = "Your Steam profile game details are set to private. Set them to Public in Steam → Edit Profile → Privacy Settings to view achievements."
                } catch (e: com.twinlore.app.data.steam.SteamGameNoAchievementsException) {
                    _error.value = "This game has no achievements on Steam."
                } catch (e: Exception) {
                    _error.value = "Could not load Steam achievements: ${e.message ?: "Unknown error"}"
                }
                _loading.value = false
            }
        }
    }

    fun clearError() { _error.value = null }

    suspend fun validateAndLinkGame(vaultId: String, appId: Int): kotlin.Result<Unit> {
        _assignError.value = null
        credentialsRepository.getCredentials() ?: return kotlin.Result.failure(Exception("Not signed in"))
        return try {
            apiClient.api.getSchemaForGame(appId = appId)
            vaultRepository.updateSteamAppId(vaultId, appId.toString())
            kotlin.Result.success(Unit)
        } catch (e: Exception) {
            _assignError.value = classifySteamLinkError(e)
            kotlin.Result.failure(e)
        }
    }

    suspend fun tryAutoMatch(vaultId: String, displayName: String): Int? {
        return runCatching {
            val appId = gameMatcher.findSteamAppId(displayName) ?: return@runCatching null
            vaultRepository.updateSteamAppId(vaultId, appId.toString())

            appId
        }.getOrNull()
    }

    fun runAutoAssign(vaultId: String, displayName: String, onResult: (Int?) -> Unit) {
        viewModelScope.launch {
            _assignError.value = null
            _autoAssignInProgress.value = true
            _isNetworkAvailable.value = networkConnectivityManager.isNetworkAvailable()
            val result = runCatching { tryAutoMatch(vaultId, displayName) }.getOrNull()
            _autoAssignInProgress.value = false
            if (result == null) {
                _assignError.value = if (!_isNetworkAvailable.value)
                    "Could not connect to Steam. Check your internet connection."
                else
                    "No matching game found in your Steam library. Try a different search term or link manually."
            }
            runCatching { onResult(result) }
        }
    }

    fun unlinkGame(vaultId: String) {
        viewModelScope.launch {
            steamGameCacheRepository.clearCacheForVault(vaultId)
            vaultRepository.updateSteamAppId(vaultId, null)
            _progress.value = null
            _error.value = null
        }
    }

    // ----- Choose from owned games -----
    private val _ownedGamesState = MutableStateFlow<SteamOwnedGamesState>(SteamOwnedGamesState.Loading)
    val ownedGamesState: StateFlow<SteamOwnedGamesState> = _ownedGamesState.asStateFlow()

    fun loadOwnedGames() {
        _ownedGamesState.value = SteamOwnedGamesState.Loading
        viewModelScope.launch {
            val creds = credentialsRepository.getCredentials()
            if (creds == null) {
                _ownedGamesState.value = SteamOwnedGamesState.Failed("Connect a Steam account in Settings first.")
                return@launch
            }
            _ownedGamesState.value = try {
                val games = apiClient.api.getOwnedGames(creds.steamId, includeAppInfo = 1)
                    .response.games
                    ?.sortedBy { it.name?.lowercase() }
                    .orEmpty()
                // Steam answers a private "Game details" setting with no games at all,
                // not an error: the dialog has to say so rather than keep spinning.
                if (games.isEmpty()) SteamOwnedGamesState.Empty else SteamOwnedGamesState.Loaded(games)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                SteamOwnedGamesState.Failed(classifySteamLinkError(e))
            }
        }
    }

    private val _duplicateLinkMessage = MutableStateFlow<String?>(null)
    val duplicateLinkMessage: StateFlow<String?> = _duplicateLinkMessage.asStateFlow()
    fun clearDuplicateLinkMessage() { _duplicateLinkMessage.value = null }

    fun linkGameFromList(vaultId: String, appId: Int) {
        viewModelScope.launch {
            // If the vault already has this game linked, keep current data and notify user
            if (_progress.value?.steamAppId == appId) {
                _duplicateLinkMessage.value = "This game's achievements are already displayed"
                return@launch
            }
            vaultRepository.updateSteamAppId(vaultId, appId.toString())

            _progress.value = null
            _error.value = null
        }
    }

    /** Classify a Steam achievement loading error into a user-friendly message. */
    private fun classifySteamAchievementError(e: Throwable): String = when {
        e is com.twinlore.app.data.steam.SteamGameNotOwnedException ->
            "This game is not in your Steam library. Add it to your library first to view achievements."
        e is com.twinlore.app.data.steam.SteamProfilePrivateException ->
            "Your Steam profile game details are set to private. Set them to Public in Steam \u2192 Edit Profile \u2192 Privacy Settings to view achievements."
        e is com.twinlore.app.data.steam.SteamGameNoAchievementsException ->
            "This game has no achievements on Steam."
        e is UnknownHostException || e is ConnectException ->
            "Could not connect to Steam. Check your internet connection."
        e is SocketTimeoutException ->
            "Steam is not responding. Try again later."
        e is retrofit2.HttpException && e.code() in listOf(401, 403) ->
            "Steam credentials expired or invalid. Update them in Settings."
        e is retrofit2.HttpException && e.code() in 500..599 ->
            "Steam is temporarily unavailable. Try again later."
        e is java.io.IOException ->
            "Connection to Steam failed. Check your internet connection."
        else ->
            "Could not load Steam achievements. Please try again."
    }

    /** Classify a Steam game linking error into a user-friendly message. */
    private fun classifySteamLinkError(e: Throwable): String = when {
        e is UnknownHostException || e is ConnectException ->
            "Could not connect to Steam. Check your internet connection."
        e is SocketTimeoutException ->
            "Steam is not responding. Try again later."
        e is retrofit2.HttpException && e.code() in 500..599 ->
            "Steam is temporarily unavailable. Try again later."
        e is retrofit2.HttpException && e.code() == 404 ->
            "No matching game found on Steam. Try a different search term."
        e is retrofit2.HttpException ->
            "Request to Steam failed (HTTP ${e.code()}). Try again later."
        e is java.io.IOException ->
            "Connection to Steam failed. Check your internet connection."
        else ->
            "Could not link game. Please try again."
    }

    fun copyAchievementsToChecklist(
        vaultId: String,
        achievements: List<SteamAchievementDisplay>,
    ) {
        viewModelScope.launch {
            try {
                val md = buildSteamMarkdown(achievements)
                checklistRepository.importFromMarkdown(
                    vaultId = vaultId,
                    activeTabId = null,
                    markdown = md,
                )
            } catch (e: Exception) {
                android.util.Log.e("SteamAchievementDrawerVM", "Failed to copy to checklist", e)
            }
        }
    }

    private fun buildSteamMarkdown(achievements: List<SteamAchievementDisplay>): String {
        val sb = StringBuilder()
        sb.appendLine("# Steam")
        for (ach in achievements) {
            sb.appendLine("## ${ach.title}")
            val earnedMark = if (ach.achieved) "x" else " "
            val desc = ach.description?.ifBlank { ach.title } ?: ach.title
            sb.appendLine("- [$earnedMark] $desc")
            sb.appendLine()
        }
        return sb.toString()
    }

    /** Add a single achievement to the first existing tab, creating one if needed. */
    fun addSingleAchievementToFirstTab(vaultId: String, achievement: SteamAchievementDisplay) {
        viewModelScope.launch {
            try {
                val tabId = resolveOrCreateFirstTab(vaultId)
                val sb = StringBuilder()
                sb.appendLine("## ${achievement.title}")
                val mark = if (achievement.achieved) "x" else " "
                val desc = achievement.description?.ifBlank { achievement.title } ?: achievement.title
                sb.appendLine("- [$mark] $desc")
                checklistRepository.importFromMarkdown(vaultId, activeTabId = tabId, markdown = sb.toString())
            } catch (e: Exception) {
                android.util.Log.e("SteamAchievementDrawerVM", "Failed to add single achievement", e)
            }
        }
    }

    /** Returns the first tab's ID, creating a "Tab 1" tab if none exist. */
    private suspend fun resolveOrCreateFirstTab(vaultId: String): String {
        val tabs = checklistRepository.getTabsForVaultOnce(vaultId)
        return tabs.minByOrNull { it.sortOrder }?.id
            ?: checklistRepository.createTab(vaultId, "Tab 1", 0).id
    }

    /** Snapshot of the vault's checklist tabs for the picker. */
    suspend fun getChecklistTabs(vaultId: String): List<ChecklistTab> =
        checklistRepository.getTabsForVaultOnce(vaultId)
}

/** The "Choose from library" dialog's list: loading, loaded, nothing Steam will share, or failed. */
sealed interface SteamOwnedGamesState {
    data object Loading : SteamOwnedGamesState
    data class Loaded(val games: List<SteamOwnedGame>) : SteamOwnedGamesState
    data object Empty : SteamOwnedGamesState
    data class Failed(val message: String) : SteamOwnedGamesState
}
