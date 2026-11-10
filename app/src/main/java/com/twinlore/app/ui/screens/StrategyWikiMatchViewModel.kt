package com.twinlore.app.ui.screens

import androidx.lifecycle.ViewModel
import com.twinlore.app.data.strategywiki.StrategyWikiCatalog
import com.twinlore.app.data.strategywiki.StrategyWikiEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * Thin Hilt bridge so the [GuideFindMenu] composable can query the
 * `@Singleton` [StrategyWikiCatalog] (which needs constructor injection and
 * can't be obtained directly inside a @Composable).
 */
@HiltViewModel
class StrategyWikiMatchViewModel @Inject constructor(
    private val catalog: StrategyWikiCatalog,
) : ViewModel() {
    fun match(gameName: String): StrategyWikiEntry? = catalog.match(gameName)
}
