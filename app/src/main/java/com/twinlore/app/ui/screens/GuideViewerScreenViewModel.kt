package com.twinlore.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twinlore.app.data.GuideViewStateManager
import com.twinlore.app.data.VaultRepository
import com.twinlore.app.domain.Vault
import com.twinlore.app.ui.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for Guide Viewer screen
 */
@HiltViewModel
class GuideViewerScreenViewModel @Inject constructor(
    private val vaultRepository: VaultRepository,
    val stateManager: GuideViewStateManager
) : BaseViewModel() {
    
    fun getVault(vaultId: String): Flow<Vault?> {
        return vaultRepository.getVaultByIdFlow(vaultId)
    }
    
    fun updateArtworkResourceId(vaultId: String, resourceId: String?) {
        viewModelScope.launch {
            vaultRepository.updateArtworkResourceId(vaultId, resourceId)
        }
    }

    fun updateMapResourceId(vaultId: String, resourceId: String?) {
        viewModelScope.launch {
            vaultRepository.updateMapResourceId(vaultId, resourceId)
        }
    }

    fun updateGuideResourceId(vaultId: String, resourceId: String?) {
        viewModelScope.launch {
            vaultRepository.updateGuideResourceId(vaultId, resourceId)
        }
    }
}

