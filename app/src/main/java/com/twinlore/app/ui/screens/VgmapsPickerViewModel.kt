package com.twinlore.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twinlore.app.data.vgmaps.VgmapsCatalog
import com.twinlore.app.data.vgmaps.VgmapsCredit
import com.twinlore.app.data.vgmaps.VgmapsEntry
import com.twinlore.app.data.vgmaps.VgmapsGameDetails
import com.twinlore.app.data.vgmaps.VgmapsMap
import com.twinlore.app.data.vgmaps.VgmapsPageScraper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class VgmapsPickerState {
    object Loading : VgmapsPickerState()
    object NoMatch : VgmapsPickerState()
    data class Ready(
        val entries: List<VgmapsEntry>,
        val selectedEntry: VgmapsEntry,
        val maps: List<VgmapsMap>,
        val selectedUrls: Set<String>,
        val credits: List<VgmapsCredit> = emptyList(),
    ) : VgmapsPickerState()
    data class FetchError(val message: String) : VgmapsPickerState()
}

/**
 * State holder for the VGMaps picker. Hand-constructed in tests via the
 * primary constructor (taking pure functions for matcher + scraper); produced
 * by Hilt at runtime via [HiltVgmapsPickerViewModel.forGame].
 */
open class VgmapsPickerViewModel(
    private val gameName: String,
    private val matcher: (String) -> List<VgmapsEntry>,
    private val scraper: suspend (VgmapsEntry) -> Result<VgmapsGameDetails>,
) : ViewModel() {
    private val _state = MutableStateFlow<VgmapsPickerState>(VgmapsPickerState.Loading)
    val state: StateFlow<VgmapsPickerState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val hits = matcher(gameName)
            if (hits.isEmpty()) {
                _state.value = VgmapsPickerState.NoMatch
                return@launch
            }
            loadMaps(hits, hits.first())
        }
    }

    fun onSelectEntry(entry: VgmapsEntry) {
        val cur = _state.value as? VgmapsPickerState.Ready ?: return
        if (cur.selectedEntry == entry) return
        viewModelScope.launch { loadMaps(cur.entries, entry) }
    }

    fun onToggle(url: String) {
        val cur = _state.value as? VgmapsPickerState.Ready ?: return
        val next = cur.selectedUrls.toMutableSet().apply {
            if (!add(url)) remove(url)
        }
        _state.value = cur.copy(selectedUrls = next)
    }

    fun onSelectAll() {
        val cur = _state.value as? VgmapsPickerState.Ready ?: return
        _state.value = cur.copy(selectedUrls = cur.maps.map { it.imageUrl }.toSet())
    }

    fun onClear() {
        val cur = _state.value as? VgmapsPickerState.Ready ?: return
        _state.value = cur.copy(selectedUrls = emptySet())
    }

    /** Returns the currently selected maps in display order. */
    fun selectedMaps(): List<VgmapsMap> {
        val cur = _state.value as? VgmapsPickerState.Ready ?: return emptyList()
        return cur.maps.filter { it.imageUrl in cur.selectedUrls }
    }

    private suspend fun loadMaps(entries: List<VgmapsEntry>, picked: VgmapsEntry) {
        _state.value = VgmapsPickerState.Loading
        val result = scraper(picked)
        result.fold(
            onSuccess = { details ->
                _state.value = VgmapsPickerState.Ready(
                    entries = entries,
                    selectedEntry = picked,
                    maps = details.maps,
                    selectedUrls = emptySet(),
                    credits = details.credits,
                )
            },
            onFailure = { t ->
                _state.value = VgmapsPickerState.FetchError(
                    message = t.message ?: "Couldn't load maps"
                )
            }
        )
    }
}

/**
 * Hilt-injected wrapper. The picker screen creates this via `hiltViewModel`,
 * then calls `forGame(gameName)` to build a per-game child VM. For now the
 * picker takes `gameName` as an explicit parameter and instantiates the child
 * directly; a `SavedStateHandle` / nav-graph route is not in scope.
 */
@HiltViewModel
class HiltVgmapsPickerViewModel @Inject constructor(
    val catalog: VgmapsCatalog,
    val scraper: VgmapsPageScraper,
) : ViewModel() {
    fun forGame(gameName: String): VgmapsPickerViewModel =
        VgmapsPickerViewModel(
            gameName = gameName,
            matcher = catalog::match,
            scraper = scraper::fetchGameDetails,
        )
}
