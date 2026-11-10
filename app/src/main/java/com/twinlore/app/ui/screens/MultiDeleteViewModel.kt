package com.twinlore.app.ui.screens

import androidx.lifecycle.viewModelScope
import com.twinlore.app.data.SavedTabRepository
import com.twinlore.app.data.library.LibraryAutoSaveObserver
import com.twinlore.app.data.library.LibraryIntegrityChecker
import com.twinlore.app.data.library.VaultImageCleaner
import com.twinlore.app.data.local.VaultDao
import com.twinlore.app.domain.Vault
import com.twinlore.app.domain.VaultCleanupClassifier
import com.twinlore.app.domain.VaultCleanupInfo
import com.twinlore.app.ui.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs the library's Delete multiple picker: tags every vault with the cleanup
 * reasons its quick-select filters use, and runs "Guides and images only" cleanups. Deleting
 * whole vaults goes through [LibraryViewModel.deleteVaults], which owns the
 * per-vault teardown.
 */
@HiltViewModel
class MultiDeleteViewModel internal constructor(
    private val vaultDao: VaultDao,
    private val integrityChecker: LibraryIntegrityChecker,
    private val savedTabRepository: SavedTabRepository,
    private val imageCleaner: VaultImageCleaner,
    private val libraryAutoSaveObserver: LibraryAutoSaveObserver,
    private val clock: () -> Long,
) : BaseViewModel() {

    @Inject
    constructor(
        vaultDao: VaultDao,
        integrityChecker: LibraryIntegrityChecker,
        savedTabRepository: SavedTabRepository,
        imageCleaner: VaultImageCleaner,
        libraryAutoSaveObserver: LibraryAutoSaveObserver,
    ) : this(vaultDao, integrityChecker, savedTabRepository, imageCleaner, libraryAutoSaveObserver, { System.currentTimeMillis() })

    /** What a "Guides and images only" run removed. */
    data class CleanupResult(val guides: Int, val images: Int)

    sealed interface State {
        data object Idle : State
        /** Rows are usable (stale and empty are known); missing files still being checked. */
        data class Checking(val rows: List<VaultCleanupInfo>) : State
        data class Ready(val rows: List<VaultCleanupInfo>) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _guideCleanupProgress = MutableStateFlow<LibraryViewModel.BulkDeleteProgress?>(null)
    /** Non-null while [deleteGuides] runs. */
    val guideCleanupProgress: StateFlow<LibraryViewModel.BulkDeleteProgress?> = _guideCleanupProgress.asStateFlow()

    /**
     * Scan [vaults] (kept in the order given, i.e. the library's). The file check walks
     * storage and can take a while on a big library, so the list shows first and the
     * "Missing files" counts fill in after.
     */
    fun load(vaults: List<Vault>) {
        viewModelScope.launch {
            val rows = runCatching { vaultDao.getContentCounts() }.getOrDefault(emptyList())
            val content = rows.associate { it.vaultId to it.contentCount }
            val guides = rows.associate { it.vaultId to it.guideCount }
            val images = rows.associate { it.vaultId to it.imageCount }
            val sizes = rows.associate { it.vaultId to (it.totalBytes to it.guideImageBytes) }
            val now = clock()
            _state.value = State.Checking(
                VaultCleanupClassifier.classify(vaults, content, emptyMap(), now, guides, images, sizes)
            )
            val missing = runCatching { integrityChecker.missingCountsByVault() }
                .onFailure { android.util.Log.w("MultiDelete", "missing-file scan failed", it) }
                .getOrDefault(emptyMap())
            _state.value = State.Ready(
                VaultCleanupClassifier.classify(vaults, content, missing, now, guides, images, sizes)
            )
        }
    }

    /**
     * "Guides and images only": remove every guide and image (screenshots, added images,
     * maps; never artwork) from [vaultIds] and keep the vaults themselves.
     */
    fun deleteGuides(vaultIds: Collection<String>, onComplete: (CleanupResult) -> Unit = {}) {
        val ids = vaultIds.distinct()
        viewModelScope.launch {
            var guides = 0
            var images = 0
            ids.forEachIndexed { i, id ->
                _guideCleanupProgress.value = LibraryViewModel.BulkDeleteProgress(i, ids.size)
                guides += runCatching { savedTabRepository.deleteAllGuidesForVault(id) }
                    .onFailure { android.util.Log.w("MultiDelete", "guide cleanup failed for $id", it) }
                    .getOrDefault(0)
                images += runCatching { imageCleaner.deleteAllImagesForVault(id) }
                    .onFailure { android.util.Log.w("MultiDelete", "image cleanup failed for $id", it) }
                    .getOrDefault(0)
            }
            _guideCleanupProgress.value = null
            if (ids.isNotEmpty()) libraryAutoSaveObserver.saveAsync()
            onComplete(CleanupResult(guides, images))
        }
    }
}
