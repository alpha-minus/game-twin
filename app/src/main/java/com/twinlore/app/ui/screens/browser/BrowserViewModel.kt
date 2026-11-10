package com.twinlore.app.ui.screens.browser

import com.twinlore.app.ui.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * ViewModel for Browser tab. Simple: tracks current URL, loading state,
 * and WebView scroll position (used by the scroll-direction chrome watcher).
 * No scroll restore. No bookmark ID persistence.
 */
@HiltViewModel
class BrowserViewModel @Inject constructor() : BaseViewModel() {

    private val _currentUrl = MutableStateFlow<String?>(null)
    val currentUrl: StateFlow<String?> = _currentUrl.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _scrollPosition = MutableStateFlow(0)
    val scrollPosition: StateFlow<Int> = _scrollPosition.asStateFlow()

    fun setUrl(url: String?) { _currentUrl.value = url }
    fun setLoading(loading: Boolean) { _isLoading.value = loading }
    fun setScrollPosition(position: Int) { _scrollPosition.value = position }
}
