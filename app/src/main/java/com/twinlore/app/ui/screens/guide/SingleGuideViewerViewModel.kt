package com.twinlore.app.ui.screens.guide

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twinlore.app.data.SavedTabRepository
import com.twinlore.app.domain.ScrollMarker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * State carrier for the single-guide viewer. Binds by bookmark id;
 * exposes the bookmark's markers as a StateFlow and queues
 * pending-scroll-to-marker requests that survive composition recreation
 * (find→tap on a cross-section result, etc.).
 */
@HiltViewModel
class SingleGuideViewerViewModel @Inject constructor(
    private val savedTabRepository: Repo
) : ViewModel() {

    /** Indirection so tests can pass a fake repo. */
    interface Repo {
        fun markersFor(bookmarkId: String): Flow<List<ScrollMarker>>
    }

    private val _bookmarkId = MutableStateFlow<String?>(null)
    val bookmarkId: StateFlow<String?> = _bookmarkId.asStateFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val markers: StateFlow<List<ScrollMarker>> = _bookmarkId
        .flatMapLatest { id -> if (id == null) emptyFlow() else savedTabRepository.markersFor(id) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun bind(bookmarkId: String) {
        _bookmarkId.value = bookmarkId
    }

    // ---- Pending-scroll-to-marker single-shot queue ----

    data class PendingScrollToMarker(
        val label: String,
        val fallbackScrollY: Int,
        val occurrenceIndex: Int,
        val flashHex: String?
    )

    private var pendingScrollToMarker: PendingScrollToMarker? = null

    fun enqueuePendingScrollToMarker(pm: PendingScrollToMarker) {
        pendingScrollToMarker = pm
    }

    fun consumePendingScrollToMarker(): PendingScrollToMarker? {
        val out = pendingScrollToMarker
        pendingScrollToMarker = null
        return out
    }

    fun clearPendingScrollToMarker() {
        pendingScrollToMarker = null
    }
}

/**
 * Hilt adapter: production Repo backed by SavedTabRepository.
 */
@dagger.Module
@dagger.hilt.InstallIn(dagger.hilt.android.components.ViewModelComponent::class)
object SingleGuideViewerViewModelModule {
    @dagger.Provides
    fun provideRepo(impl: SavedTabRepository): SingleGuideViewerViewModel.Repo =
        object : SingleGuideViewerViewModel.Repo {
            override fun markersFor(bookmarkId: String) =
                impl.getScrollMarkersFlow(bookmarkId)
        }
}
