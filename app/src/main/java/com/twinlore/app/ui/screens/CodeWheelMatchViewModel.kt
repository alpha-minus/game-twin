package com.twinlore.app.ui.screens

import androidx.lifecycle.ViewModel
import com.twinlore.app.data.codewheel.CodeWheelCatalog
import com.twinlore.app.data.codewheel.CodeWheelEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * Thin Hilt bridge so composables can query the `@Singleton` [CodeWheelCatalog]
 * (which needs constructor injection and can't be obtained directly inside a @Composable).
 */
@HiltViewModel
class CodeWheelMatchViewModel @Inject constructor(
    private val catalog: CodeWheelCatalog,
) : ViewModel() {
    fun match(gameName: String): CodeWheelEntry? =
        if (gameName.isBlank()) null else catalog.bestMatch(gameName)
}
