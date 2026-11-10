package com.twinlore.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twinlore.app.data.retroachievements.RaAchievementDisplay
import com.twinlore.app.data.retroachievements.RaCommentDto
import com.twinlore.app.data.retroachievements.RaGameProgress
import com.twinlore.app.data.retroachievements.RaGameCacheRepository
import com.twinlore.app.data.retroachievements.RetroAchievementsCredentialsRepository
import com.twinlore.app.data.retroachievements.RetroAchievementsApiClient
import com.twinlore.app.data.retroachievements.GameMatcher
import com.twinlore.app.data.VaultRepository
import com.twinlore.app.data.NetworkConnectivityManager
import com.twinlore.app.data.retroachievements.RaLocalConsole
import com.twinlore.app.data.retroachievements.RaLocalGame
import com.twinlore.app.data.retroachievements.RaLocalDataRepository
import com.twinlore.app.data.ChecklistRepository
import com.twinlore.app.domain.ChecklistTab
import com.twinlore.app.domain.RaAchievementSortField
import com.twinlore.app.domain.SortDirection
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
class AchievementDrawerViewModel @Inject constructor(
    private val raGameCacheRepository: RaGameCacheRepository,
    private val credentialsRepository: RetroAchievementsCredentialsRepository,
    private val apiClient: RetroAchievementsApiClient,
    private val vaultRepository: VaultRepository,
    private val gameMatcher: GameMatcher,
    private val networkConnectivityManager: NetworkConnectivityManager,
    private val raLocalDataRepository: RaLocalDataRepository,
    private val checklistRepository: ChecklistRepository,
    private val commentsRepository: com.twinlore.app.data.retroachievements.RaCommentsRepository
) : ViewModel() {

    private val _progress = MutableStateFlow<RaGameProgress?>(null)
    val progress: StateFlow<RaGameProgress?> = _progress.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _raUsername = MutableStateFlow<String?>(null)
    val raUsername: StateFlow<String?> = _raUsername.asStateFlow()

    private val _isNetworkAvailable = MutableStateFlow(true)
    val isNetworkAvailable: StateFlow<Boolean> = _isNetworkAvailable.asStateFlow()

    private val _autoAssignInProgress = MutableStateFlow(false)
    val autoAssignInProgress: StateFlow<Boolean> = _autoAssignInProgress.asStateFlow()

    private val _guideUrl = MutableStateFlow<String?>(null)
    val guideUrl: StateFlow<String?> = _guideUrl.asStateFlow()

    /** One-shot event: set to true after linking a game when a guide is available. UI should show prompt then reset. */
    private val _promptGuideDownload = MutableStateFlow(false)
    val promptGuideDownload: StateFlow<Boolean> = _promptGuideDownload.asStateFlow()
    fun clearGuidePrompt() { _promptGuideDownload.value = false }

    /** Call when drawer is shown to refresh connectivity state. */
    fun checkConnectivity() {
        _isNetworkAvailable.value = networkConnectivityManager.isNetworkAvailable()
    }

    private val _sortField = MutableStateFlow(RaAchievementSortField.UNLOCK_TIME)
    val sortField: StateFlow<RaAchievementSortField> = _sortField.asStateFlow()

    private val _sortDirection = MutableStateFlow(RaAchievementSortField.UNLOCK_TIME.defaultDirection)
    val sortDirection: StateFlow<SortDirection> = _sortDirection.asStateFlow()

    /** Picking the current field flips its direction; picking another selects it at its default. */
    fun selectSort(field: RaAchievementSortField) {
        if (_sortField.value == field) {
            _sortDirection.value = _sortDirection.value.toggle()
        } else {
            _sortField.value = field
            _sortDirection.value = field.defaultDirection
        }
    }

    // One chip cycles all -> progression -> missable, so the drawer needs no second control.
    // Session-scoped like the sort and locked-only filters: reopening the drawer starts at ALL.
    private val _achievementTypeFilter =
        MutableStateFlow(com.twinlore.app.domain.RaAchievementTypeFilter.ALL)
    val achievementTypeFilter: StateFlow<com.twinlore.app.domain.RaAchievementTypeFilter> =
        _achievementTypeFilter.asStateFlow()
    fun cycleAchievementTypeFilter() {
        _achievementTypeFilter.value = _achievementTypeFilter.value.next()
    }

    private val _filterLockedOnly = MutableStateFlow(false)
    val filterLockedOnly: StateFlow<Boolean> = _filterLockedOnly.asStateFlow()
    fun setFilterLockedOnly(value: Boolean) { _filterLockedOnly.value = value }

    /**
     * Free-text filter over the achievement list: title and description, case-insensitive.
     *
     * Session-scoped like the sort, type and locked-only filters above, and for the same reason -
     * a query is about the question being asked right now, not a setting. It lives here rather
     * than in the drawer so that it survives what the drawer does not: the RA/Steam source chip
     * disposes the whole achievements composable, and so does closing and reopening the drawer. A
     * query held in the composable would be dropped by those the way a sort never is.
     */
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    fun setSearchQuery(value: String) { _searchQuery.value = value }

    private val _scrollIndex = MutableStateFlow(0)
    val scrollIndex: StateFlow<Int> = _scrollIndex.asStateFlow()
    private val _scrollOffset = MutableStateFlow(0)
    val scrollOffset: StateFlow<Int> = _scrollOffset.asStateFlow()
    fun setScrollPosition(index: Int, offset: Int) {
        _scrollIndex.value = index
        _scrollOffset.value = offset
    }

    /** Whether the achievement badge grid (wallpaper) overlay is shown. Persisted so it survives orientation change. */
    private val _showWallpaperView = MutableStateFlow(false)
    val showWallpaperView: StateFlow<Boolean> = _showWallpaperView.asStateFlow()
    fun setShowWallpaperView(show: Boolean) { _showWallpaperView.value = show }

    fun load(vaultId: String, raGameId: Int) {
        viewModelScope.launch {
            _guideUrl.value = null
            _isNetworkAvailable.value = networkConnectivityManager.isNetworkAvailable()
            _loading.value = true
            _error.value = null
            _raUsername.value = credentialsRepository.getCredentials()?.username
            try {
                _progress.value = raGameCacheRepository.getGameProgress(vaultId, raGameId, forceRefresh = false)
                if (_progress.value == null) _error.value = NOT_CONNECTED
            } catch (e: Exception) {
                _error.value = RaErrorMessages.forLoad(e)
            }
            _loading.value = false
            // Fetch guide URL in background (non-blocking)
            launch {
                try {
                    val apiKey = credentialsRepository.getCredentials()?.apiKey
                        ?: RetroAchievementsCredentialsRepository.BUNDLED_API_KEY
                    val extended = apiClient.api.getGameExtended(apiKey, raGameId)
                    _guideUrl.value = extended.guideUrl
                } catch (_: Exception) { /* silent */ }
            }
        }
    }

    fun refresh(vaultId: String, raGameId: Int, minRefreshIntervalMs: Long? = null) {
        viewModelScope.launch {
            _isNetworkAvailable.value = networkConnectivityManager.isNetworkAvailable()
            // Cache-first: paint the cached snapshot instantly instead of clearing to a
            // spinner over the background, so opening the tab is snappy (no flash). Then
            // refresh below and swap in the fresh data when it lands.
            val cached = raGameCacheRepository.getCachedProgress(vaultId)
            if (cached != null) {
                _progress.value = cached
                _loading.value = false
            } else {
                _progress.value = null
                _loading.value = true
            }
            _error.value = null
            _raUsername.value = credentialsRepository.getCredentials()?.username
            try {
                val fresh = raGameCacheRepository.getGameProgress(vaultId, raGameId, forceRefresh = true, minRefreshIntervalMs = minRefreshIntervalMs)
                if (fresh != null) _progress.value = fresh
                else if (_progress.value == null) _error.value = NOT_CONNECTED
            } catch (e: Exception) {
                if (_progress.value == null) _error.value = RaErrorMessages.forLoad(e)
            }
            _loading.value = false
            // Fetch guide URL in background
            launch {
                try {
                    val apiKey = credentialsRepository.getCredentials()?.apiKey
                        ?: RetroAchievementsCredentialsRepository.BUNDLED_API_KEY
                    val extended = apiClient.api.getGameExtended(apiKey, raGameId)
                    _guideUrl.value = extended.guideUrl
                } catch (e: Exception) {
                    android.util.Log.w("AchievementDrawerVM", "Failed to fetch guide URL", e)
                }
            }
        }
    }

    /** Refresh the currently displayed game progress. */
    fun refreshCurrentSet(vaultId: String) {
        _progress.value?.raGameId?.let { gameId ->
            viewModelScope.launch {
                _isNetworkAvailable.value = networkConnectivityManager.isNetworkAvailable()
                _loading.value = true
                _error.value = null
                try {
                    _progress.value = raGameCacheRepository.getGameProgress(vaultId, gameId, forceRefresh = true)
                    if (_progress.value == null) _error.value = NOT_CONNECTED
                } catch (e: Exception) {
                    _error.value = RaErrorMessages.forLoad(e)
                }
                _loading.value = false
            }
        }
    }

    // ----- Achievement Comments -----
    sealed class CommentsState {
        data object Idle : CommentsState()
        data object Loading : CommentsState()
        data class Success(val comments: List<RaCommentDto>) : CommentsState()
        data class Error(val message: String) : CommentsState()
    }

    private val _selectedCommentAchievement = MutableStateFlow<RaAchievementDisplay?>(null)
    val selectedCommentAchievement: StateFlow<RaAchievementDisplay?> = _selectedCommentAchievement.asStateFlow()

    private val _commentsState = MutableStateFlow<CommentsState>(CommentsState.Idle)
    val commentsState: StateFlow<CommentsState> = _commentsState.asStateFlow()

    fun showCommentsForAchievement(achievement: RaAchievementDisplay) {
        _selectedCommentAchievement.value = achievement
        _commentsState.value = CommentsState.Loading
        viewModelScope.launch {
            try {
                _commentsState.value = CommentsState.Success(commentsRepository.getComments(achievement.id))
            } catch (e: Exception) {
                android.util.Log.e("AchievementDrawerVM", "Failed to load comments for achievement ${achievement.id}", e)
                _commentsState.value = CommentsState.Error(RaErrorMessages.forComments(e))
            }
        }
    }

    fun dismissComments() {
        _selectedCommentAchievement.value = null
        _commentsState.value = CommentsState.Idle
    }

    fun retryComments() {
        _selectedCommentAchievement.value?.let { showCommentsForAchievement(it) }
    }

    fun clearError() { _error.value = null }

    /** Validate game ID via API and persist to vault. Call when user enters manual Game ID. */
    suspend fun validateAndLinkGame(vaultId: String, gameId: Int): Result<Unit> {
        val creds = credentialsRepository.getCredentials() ?: return Result.failure(Exception(NOT_CONNECTED))
        return try {
            // Throws RaGameNotFoundException for an ID RetroAchievements does not have, which it
            // answers with an empty `[]` rather than an error.
            raGameCacheRepository.requireGame(gameId)
            vaultRepository.updateRaGameId(vaultId, gameId.toString())
            checkGuideAfterLink(gameId, creds.apiKey)
            Result.success(Unit)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(Exception(RaErrorMessages.forLink(e)))
        }
    }

    /** Try to auto-match vault display name to an RA game; if found, persist and return the game ID.
     * First tries recently played games; if none match, tries closest name in the local RA database (all consoles). */
    suspend fun tryAutoMatch(vaultId: String, displayName: String): Int? {
        return runCatching {
            var gameId = gameMatcher.findRaGameId(displayName)
            if (gameId == null) {
                gameId = gameMatcher.findRaGameIdFromLocalDatabase(displayName)
            }
            if (gameId == null) return@runCatching null
            vaultRepository.updateRaGameId(vaultId, gameId.toString())
            gameId
        }.getOrNull()
    }

    /** Run auto-assign and update loading state. Call from UI. */
    fun runAutoAssign(vaultId: String, displayName: String, onResult: (Int?) -> Unit) {
        viewModelScope.launch {
            _autoAssignInProgress.value = true
            _isNetworkAvailable.value = networkConnectivityManager.isNetworkAvailable()
            val result = runCatching { tryAutoMatch(vaultId, displayName) }.getOrNull()
            _autoAssignInProgress.value = false
            runCatching { onResult(result) }
            // Check for guide after successful auto-assign
            if (result != null) {
                val apiKey = credentialsRepository.getCredentials()?.apiKey
                    ?: RetroAchievementsCredentialsRepository.BUNDLED_API_KEY
                checkGuideAfterLink(result, apiKey)
            }
        }
    }

    /** Unlink RetroAchievements game from this vault: clear cache, set raGameId to null. */
    fun unlinkGame(vaultId: String) {
        viewModelScope.launch {
            raGameCacheRepository.clearCacheForVault(vaultId)
            vaultRepository.updateRaGameId(vaultId, null)
            _progress.value = null
            _error.value = null
        }
    }

    // ----- Choose from list (local data) -----
    private val _consoles = MutableStateFlow<List<RaLocalConsole>>(emptyList())
    val consoles: StateFlow<List<RaLocalConsole>> = _consoles.asStateFlow()
    private val _games = MutableStateFlow<List<RaLocalGame>>(emptyList())
    val games: StateFlow<List<RaLocalGame>> = _games.asStateFlow()
    private val _selectedConsoleId = MutableStateFlow<Int?>(null)
    val selectedConsoleId: StateFlow<Int?> = _selectedConsoleId.asStateFlow()

    fun loadConsoles() {
        viewModelScope.launch {
            runCatching {
                _consoles.value = raLocalDataRepository.getConsoles()
            }.onFailure {
                _consoles.value = emptyList()
            }
            _selectedConsoleId.value = null
            _games.value = emptyList()
        }
    }

    fun selectConsole(consoleId: Int?) {
        _selectedConsoleId.value = consoleId
        if (consoleId != null) {
            viewModelScope.launch {
                _games.value = runCatching { raLocalDataRepository.getGamesForConsole(consoleId) }.getOrElse { emptyList() }
            }
        } else {
            _games.value = emptyList()
        }
    }

    /** Link vault to a game by ID (from local list). Updates vault and clears progress so drawer refreshes. */
    fun linkGameFromList(vaultId: String, gameId: Int) {
        viewModelScope.launch {
            vaultRepository.updateRaGameId(vaultId, gameId.toString())
            _progress.value = null
            _error.value = null
            val apiKey = credentialsRepository.getCredentials()?.apiKey
                ?: RetroAchievementsCredentialsRepository.BUNDLED_API_KEY
            checkGuideAfterLink(gameId, apiKey)
        }
    }

    private suspend fun checkGuideAfterLink(gameId: Int, apiKey: String) {
        try {
            val extended = apiClient.api.getGameExtended(apiKey, gameId)
            _guideUrl.value = extended.guideUrl
            if (extended.guideUrl != null) {
                // Small delay to ensure composable has recomposed after link
                kotlinx.coroutines.delay(500)
                _promptGuideDownload.value = true
            }
        } catch (_: Exception) { /* silent */ }
    }

    fun copyAchievementsToChecklist(
        vaultId: String,
        achievements: List<RaAchievementDisplay>,
    ) {
        viewModelScope.launch {
            try {
                val md = buildRaMarkdown(achievements)
                val result = checklistRepository.importFromMarkdown(
                    vaultId = vaultId,
                    activeTabId = null,
                    markdown = md,
                )
                android.util.Log.d(
                    "AchievementDrawerVM",
                    "Imported ${result.itemsAdded} items · ${result.itemsSkipped} dup · ${result.tabsCreated} tabs · ${result.sectionsCreated} sections",
                )
            } catch (e: Exception) {
                android.util.Log.e("AchievementDrawerVM", "Failed to copy to checklist", e)
            }
        }
    }

    private fun buildRaMarkdown(achievements: List<RaAchievementDisplay>): String {
        val grouped: Map<String, List<RaAchievementDisplay>> = achievements.groupBy { raBucketName(it.type) }
        val tabOrder = listOf("Progression", "Missable", "Achievements")
        val sb = StringBuilder()
        for (tabName in tabOrder) {
            val list = grouped[tabName] ?: continue
            if (list.isEmpty()) continue
            sb.appendLine("# $tabName")
            for (ach in list) {
                sb.appendLine("## ${ach.title}")
                val mark = if (ach.dateAwarded != null) "x" else " "
                val desc = ach.description.ifBlank { ach.title }
                sb.appendLine("- [$mark] $desc")
                sb.appendLine()
            }
        }
        return sb.toString()
    }

    private fun raBucketName(type: String?): String = when (type?.lowercase()?.trim()) {
        "progression", "win_condition" -> "Progression"
        "missable" -> "Missable"
        else -> "Achievements"
    }

    /** Add a single achievement to the first existing tab, creating one if needed. */
    fun addSingleAchievementToFirstTab(vaultId: String, achievement: RaAchievementDisplay) {
        viewModelScope.launch {
            try {
                val tabId = resolveOrCreateFirstTab(vaultId)
                val sb = StringBuilder()
                sb.appendLine("## ${achievement.title}")
                val mark = if (achievement.dateAwarded != null) "x" else " "
                val desc = achievement.description.ifBlank { achievement.title }
                sb.appendLine("- [$mark] $desc")
                checklistRepository.importFromMarkdown(vaultId, activeTabId = tabId, markdown = sb.toString())
            } catch (e: Exception) {
                android.util.Log.e("AchievementDrawerVM", "Failed to add single achievement", e)
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

    private companion object {
        /** getGameProgress returns null only when no account is connected. */
        const val NOT_CONNECTED = "Connect your RetroAchievements account in Settings first."
    }
}
