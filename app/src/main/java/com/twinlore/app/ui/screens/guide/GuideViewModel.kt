package com.twinlore.app.ui.screens.guide

import com.twinlore.app.data.GuideViewStateManager
import com.twinlore.app.domain.ScrollRestoreData
import com.twinlore.app.ui.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * ViewModel for Guide tab. Single source of truth for which guide is active.
 *
 * State machine:
 *   compose → read activeBookmarkId
 *     → found → load bookmark (from cache) → restore scroll → Viewing
 *     → not found → Picker
 */
@HiltViewModel
class GuideViewModel @Inject constructor(
    val stateManager: GuideViewStateManager
) : BaseViewModel() {

    // The ONE persisted bookmark ID. This is the single source of truth
    // for what the Guide tab should display on restore.
    private val _activeBookmarkId = MutableStateFlow<String?>(null)
    val activeBookmarkId: StateFlow<String?> = _activeBookmarkId.asStateFlow()

    private val _currentUrl = MutableStateFlow<String?>(null)
    val currentUrl: StateFlow<String?> = _currentUrl.asStateFlow()

    private val _zoomLevel = MutableStateFlow(1f)
    val zoomLevel: StateFlow<Float> = _zoomLevel.asStateFlow()

    private val _minZoomLevel = MutableStateFlow(0.5f)
    val minZoomLevel: StateFlow<Float> = _minZoomLevel.asStateFlow()

    private val _scrollPosition = MutableStateFlow(0)
    val scrollPosition: StateFlow<Int> = _scrollPosition.asStateFlow()

    private val _maxScrollY = MutableStateFlow(0)
    val maxScrollY: StateFlow<Int> = _maxScrollY.asStateFlow()

    private val _scrollRestoreData = MutableStateFlow<ScrollRestoreData?>(null)
    val scrollRestoreData: StateFlow<ScrollRestoreData?> = _scrollRestoreData.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    fun setLoading(loading: Boolean) { _isLoading.value = loading }

    /** Track which vault this state belongs to. Reset when vault changes. */
    private var stateVaultId: String? = null

    /**
     * Called on composition with vaultId. Resets state if vault changed.
     * Loads persisted bookmark ID from disk.
     */
    fun initForVault(vaultId: String) {
        if (stateVaultId == vaultId) return
        if (stateVaultId != null) {
            // Vault changed — save current state before resetting
            saveState(stateVaultId!!)
        }
        stateVaultId = vaultId
        _activeBookmarkId.value = stateManager.getActiveBookmarkId(vaultId)
        _zoomLevel.value = 1f  // Per-bookmark zoom is restored when the bookmark loads
        _minZoomLevel.value = 0.5f
        _scrollRestoreData.value = null
        _scrollPosition.value = 0
        _maxScrollY.value = 0
    }

    /**
     * Set the active bookmark. Persists to disk immediately.
     */
    fun setActiveBookmark(vaultId: String, bookmarkId: String?) {
        _activeBookmarkId.value = bookmarkId
        stateManager.saveActiveBookmarkId(vaultId, bookmarkId)
    }

    fun setUrl(url: String?) { _currentUrl.value = url }
    fun setZoom(zoom: Float) { _zoomLevel.value = zoom.coerceIn(_minZoomLevel.value, 3.0f) }
    fun setMinZoomLevel(min: Float) {
        _minZoomLevel.value = min.coerceIn(0.5f, 1.0f)
        // Snap current zoom up if it fell below the effective minimum
        if (_zoomLevel.value < _minZoomLevel.value) {
            _zoomLevel.value = _minZoomLevel.value
        }
    }
    fun setScrollPosition(position: Int) { _scrollPosition.value = position }
    fun setMaxScrollY(max: Int) { _maxScrollY.value = max.coerceAtLeast(0) }
    fun setScrollRestoreData(data: ScrollRestoreData?) { _scrollRestoreData.value = data }

    /**
     * Save current state to disk. Called on vault switch and tab leave.
     * Zoom is persisted per-bookmark, not here.
     */
    fun saveState(vaultId: String) {
        stateManager.saveLastPageState(
            vaultId = vaultId,
            url = _currentUrl.value,
            scrollRestore = _scrollRestoreData.value
        )
    }
}
