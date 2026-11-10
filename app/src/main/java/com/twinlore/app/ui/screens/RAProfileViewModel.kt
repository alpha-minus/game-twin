package com.twinlore.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twinlore.app.data.retroachievements.RaProfileDisplay
import com.twinlore.app.data.retroachievements.RaProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RAProfileViewModel @Inject constructor(
    private val profileRepository: RaProfileRepository
) : ViewModel() {

    private val _profile = MutableStateFlow<RaProfileDisplay?>(null)
    val profile: StateFlow<RaProfileDisplay?> = _profile.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        profileRepository.profile.onEach { _profile.value = it }.launchIn(viewModelScope)
    }

    fun load() {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            val result = profileRepository.load(forceRefresh = false)
            _profile.value = result ?: _profile.value
            if (result == null && _profile.value == null) _error.value = "Unable to load profile"
            _loading.value = false
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            val result = profileRepository.refresh()
            _profile.value = result ?: _profile.value
            if (result == null) _error.value = "Unable to refresh"
            _loading.value = false
        }
    }
}
