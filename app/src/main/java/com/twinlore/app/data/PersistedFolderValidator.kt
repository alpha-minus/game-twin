package com.twinlore.app.data

import android.util.Log
import com.twinlore.app.data.library.MediaSearchPathRepository
import com.twinlore.app.data.library.ResourceResolver
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

enum class AssetSource { ESDE, COCOON }

/**
 * Outcome of [PersistedFolderValidator.validate].
 *
 * [lostHome] and [lostAssetSources] are separate categories with different UX treatment.
 */
data class ValidationResult(
    val lostHome: Boolean,
    val lostAssetSources: Set<AssetSource>,
) {
    val anyLost: Boolean get() = lostHome || lostAssetSources.isNotEmpty()

    companion object {
        val EMPTY = ValidationResult(lostHome = false, lostAssetSources = emptySet())
    }
}

@Singleton
class PersistedFolderValidator @Inject constructor(
    private val settings: SettingsRepository,
    private val mediaSearchPaths: MediaSearchPathRepository,
    private val probe: UriProbe,
    private val resourceResolver: ResourceResolver,
) {
    /** Wall-clock cap for each probe attempt. */
    @androidx.annotation.VisibleForTesting
    internal var probeTimeoutMs: Long = DEFAULT_PROBE_TIMEOUT_MS

    /** Delay between the first and second probe attempt. */
    @androidx.annotation.VisibleForTesting
    internal var retryDelayMs: Long = DEFAULT_RETRY_DELAY_MS

    suspend fun validate(): ValidationResult = withContext(Dispatchers.IO) {
        try {
            val homeUri = settings.homeFolderUri.first()
            val esdeUri = settings.esdeFolderUri.first()
            val cocoonUri = settings.cocoonFolderUri.first()

            // Probe in parallel; each probe is independent IO-bound work.
            val homeOk = async { homeUri == null || checkUri(homeUri) }
            val esdeOk = async { esdeUri == null || checkUri(esdeUri) }
            val cocoonOk = async { cocoonUri == null || checkUri(cocoonUri) }

            val lostHome = homeUri != null && !homeOk.await()
            val lostEsde = esdeUri != null && !esdeOk.await()
            val lostCocoon = cocoonUri != null && !cocoonOk.await()

            if (lostHome) {
                settings.setHomeFolderUri(null)
                // The resolved tree Uri itself just changed (to "none"), so every
                // Uri cached by ResourceResolver against the old home folder is
                // now stale. See ResourceResolver.kt's invalidation contract.
                resourceResolver.invalidateAll()
            }
            val lostAssets = mutableSetOf<AssetSource>()
            if (lostEsde) {
                settings.setEsdeFolderUri(null)
                lostAssets += AssetSource.ESDE
            }
            if (lostCocoon) {
                settings.setCocoonFolderUri(null)
                lostAssets += AssetSource.COCOON
            }
            if (lostHome || lostAssets.isNotEmpty()) {
                mediaSearchPaths.syncAutoFromSettings()
            }

            ValidationResult(lostHome = lostHome, lostAssetSources = lostAssets)
        } catch (ce: kotlinx.coroutines.CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Log.w(TAG, "validate() failed; degrading to EMPTY", t)
            ValidationResult.EMPTY
        }
    }

    /** Returns true if the URI is reachable. One retry on transient failure, each attempt bounded by [probeTimeoutMs]. */
    private suspend fun checkUri(uriString: String): Boolean {
        if (!probe.hasPersistedPermission(uriString)) return false
        val first = withTimeoutOrNull(probeTimeoutMs) {
            runCatching { probe.probeIsDirectory(uriString) }.getOrNull()
        }
        if (first != null) return first
        delay(retryDelayMs)
        val second = withTimeoutOrNull(probeTimeoutMs) {
            runCatching { probe.probeIsDirectory(uriString) }.getOrNull()
        }
        return second ?: false
    }

    private companion object {
        const val TAG = "PersistedFolderValidator"
        const val DEFAULT_PROBE_TIMEOUT_MS = 750L
        const val DEFAULT_RETRY_DELAY_MS = 250L
    }
}
