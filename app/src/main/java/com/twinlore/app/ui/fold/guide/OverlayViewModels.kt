package com.twinlore.app.ui.fold.guide

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.twinlore.app.di.PanelViewerDependenciesEntryPoint
import dagger.hilt.android.EntryPointAccessors

/**
 * A view model built by hand, kept in the overlay's own store.
 *
 * The store belongs to [OverlayComposeRoot], which clears it when the panel's
 * window comes down, so a view model built here gets its onCleared and its
 * viewModelScope cancelled at the same moment the window does. That matters
 * more here than in the app: the panel lives next to a running game, and a
 * collector left running against a window nobody can see is exactly the kind
 * of thing that gets this process reaped.
 *
 * [key] is what the store files it under, so two panels of the same kind over
 * different documents get one view model each rather than sharing one.
 */
@Composable
inline fun <reified T : ViewModel> rememberOverlayViewModel(
    key: String,
    crossinline create: () -> T,
): T {
    val owner = checkNotNull(LocalViewModelStoreOwner.current) {
        "No ViewModelStoreOwner. The panel installs OverlayComposeRoot as one."
    }
    val factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <V : ViewModel> create(modelClass: Class<V>): V = create() as V
    }
    // ViewModelProvider caches by key in the store, so this is stable across
    // recompositions without a remember of its own.
    return ViewModelProvider(owner, factory)[key, T::class.java]
}

/** The singletons the app's guide view models are constructed from. */
@Composable
fun rememberPanelViewerDependencies(): PanelViewerDependenciesEntryPoint {
    val context = LocalContext.current
    return remember(context) {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            PanelViewerDependenciesEntryPoint::class.java,
        )
    }
}
