package com.twinlore.app.ui.screens

import androidx.lifecycle.ViewModel
import com.twinlore.app.data.gamefaqs.GameFaqsCatalog
import com.twinlore.app.data.gamefaqs.GameFaqsEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * Thin Hilt bridge so the [GuideFindMenu] composable can query the
 * `@Singleton` [GameFaqsCatalog] (which needs constructor injection and can't be
 * obtained directly inside a @Composable).
 */
@HiltViewModel
class GameFaqsMatchViewModel @Inject constructor(
    private val catalog: GameFaqsCatalog,
) : ViewModel() {
    fun match(gameName: String): GameFaqsEntry? = catalog.match(gameName)
}
