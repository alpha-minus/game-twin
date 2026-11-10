package com.twinlore.app.ui.fold

import com.twinlore.app.data.SettingsRepository
import com.twinlore.app.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether overlay mode is on, across process deaths.
 *
 * A port on [FoldController] rather than a DataStore call inside it, for the
 * same reason [FoldPlatform] is: the state machine is tested without Android.
 * The controller holds the mode for this process; this is only what survives
 * it, for a cold start to read.
 */
interface OverlayMemory {
    /** Overlay mode was entered or left. */
    fun setOverlayMode(on: Boolean)

    /**
     * Whether the last run left overlay mode on. Read from the store, so it
     * is only meaningful on a cold start, before this process has folded.
     */
    suspend fun wasOverlayMode(): Boolean
}

@Singleton
class DataStoreOverlayMemory @Inject constructor(
    private val settingsRepository: SettingsRepository,
    @ApplicationScope private val appScope: CoroutineScope,
) : OverlayMemory {

    /**
     * The mode as this process last set it. Each write stores whatever this
     * holds when it runs, under [writes], so an enter and a leave in quick
     * succession cannot land in the store in reverse order.
     */
    @Volatile
    private var on = false
    private val writes = Mutex()

    override fun setOverlayMode(on: Boolean) {
        this.on = on
        appScope.launch { writes.withLock { settingsRepository.setOverlayMode(this@DataStoreOverlayMemory.on) } }
    }

    override suspend fun wasOverlayMode(): Boolean =
        runCatching { settingsRepository.overlayMode.first() }.getOrDefault(false)
}
