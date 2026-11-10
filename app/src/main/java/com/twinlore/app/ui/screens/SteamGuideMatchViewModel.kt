package com.twinlore.app.ui.screens

import androidx.lifecycle.ViewModel
import com.twinlore.app.data.steam.SteamGuideCatalog
import com.twinlore.app.data.steam.SteamGuideEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * Thin Hilt bridge so the [GuideFindMenu] composable can query the
 * `@Singleton` [SteamGuideCatalog] (which needs constructor injection and can't
 * be obtained directly inside a @Composable).
 */
@HiltViewModel
class SteamGuideMatchViewModel @Inject constructor(
    private val catalog: SteamGuideCatalog,
) : ViewModel() {
    fun match(gameName: String): SteamGuideEntry? = catalog.match(gameName)
}
