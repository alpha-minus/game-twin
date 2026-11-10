package com.twinlore.app.data.library.sync

import android.content.Context
import android.net.Uri
import com.twinlore.app.data.SettingsRepository
import com.twinlore.app.data.library.LibraryAutoSaveObserver
import com.twinlore.app.data.library.LibraryFolderManager
import com.twinlore.app.data.library.LibrarySnapshotMeta
import com.twinlore.app.data.library.findSnapshotMetasNewestFirst
import com.twinlore.app.data.local.SCHEMA_VERSION
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class SyncPromptUi(
    val deviceLabels: List<String>,
    val newVaults: List<String>,
    val updatedVaults: List<String>,
    val deletedVaults: List<String>,
    val conflictLines: List<String>,
    internal val candidates: List<LibrarySnapshotMeta>,
    internal val changeSetKey: String,
)

/** A pre-sync whole-library backup surfaced as a restore point in Settings. */
data class SyncRestorePoint(val file: java.io.File, val atMillis: Long)

data class SyncUpdateNotice(val deviceLabel: String)

/**
 * Orchestrates cross-device sync over the shared library folder: scans for
 * foreign snapshots, previews diffs, drives the prompt/undo UI state, applies
 * merges through [LibrarySyncEngine], surfaces pre-sync backups as
 * whole-library restore points, and surfaces "update the app" notices for
 * snapshots written by newer builds (those are never opened or merged — see
 * [classify]). Nothing is EVER deleted from the user's library folder.
 *
 * Threading: every entry point serializes on [syncMutex] (the engine is
 * single-flight by contract). [LibraryAutoSaveObserver.saveNow] takes its own
 * non-reentrant mutex — it must NEVER be called inside
 * [LibraryAutoSaveObserver.exclusive].
 */
@Singleton
class LibrarySyncCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val autoSaveObserver: LibraryAutoSaveObserver,
    private val engine: LibrarySyncEngine,
    private val folder: LibraryFolderManager,
    private val libraryMetaDao: com.twinlore.app.data.local.LibraryMetaDao,
    private val deviceIdMigrator: com.twinlore.app.data.library.DeviceIdMigrator,
    private val vaultDao: com.twinlore.app.data.local.VaultDao,
    private val mediaMatcher: com.twinlore.app.data.library.LocalMediaMatcher,
    private val artworkThumbnailCache: com.twinlore.app.data.library.ArtworkThumbnailCache,
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val syncMutex = Mutex()

    private val _promptState = MutableStateFlow<SyncPromptUi?>(null)
    val promptState: StateFlow<SyncPromptUi?> = _promptState.asStateFlow()

    private val _updateNotices = MutableStateFlow<List<SyncUpdateNotice>>(emptyList())
    val updateNotices: StateFlow<List<SyncUpdateNotice>> = _updateNotices.asStateFlow()

    /** Conflict vaults of the last applied merge, for "Restore previous version". */
    private val _restorableConflicts = MutableStateFlow<List<VaultRef>>(emptyList())
    val restorableConflicts: StateFlow<List<VaultRef>> = _restorableConflicts.asStateFlow()

    /** Pre-sync backups on this device, newest first, for Settings restore points. */
    private val _restorePoints = MutableStateFlow<List<SyncRestorePoint>>(emptyList())
    val restorePoints: StateFlow<List<SyncRestorePoint>> = _restorePoints.asStateFlow()

    private var backupFile: File? = null

    /** Spec §3: a declined change-set re-offers on the next cold launch. */
    private val firstScanOfProcess = java.util.concurrent.atomic.AtomicBoolean(true)

    /** Wall-clock millis of the last completed folder scan, for the foreground debounce. */
    @Volatile private var lastScanAtMillis = 0L

    fun checkAsync() {
        scope.launch { runCatching { checkForChanges() } }
    }

    /**
     * Scans the library folder for foreign changes. Cheap when nothing
     * changed: guards, one folder listing and the per-snapshot meta reads —
     * NO library fingerprinting / snapshot save (see [LibraryAutoSaveObserver.saveNow])
     * happens unless changed candidates exist. [force] (manual "Sync now")
     * bypasses the foreground debounce.
     */
    suspend fun checkForChanges(force: Boolean = false): Unit = syncMutex.withLock {
        if (firstScanOfProcess.compareAndSet(true, false)) {
            // Declines are per-session: re-offer on the next cold launch.
            // Deliberately BEFORE the debounce return so the clear is never
            // skipped by an early-debounced first scan.
            settingsRepository.setDeclinedSyncChangeSet(null)
        }
        if (!force && System.currentTimeMillis() - lastScanAtMillis < SCAN_DEBOUNCE_MILLIS) return
        val homeUri = folder.homeFolderUri() ?: return
        // CRITICAL fresh-install guard: an uninitialized library (no
        // library_meta row yet) means cold-start auto-restore has not run.
        // Proceeding would saveNow() an EMPTY snapshot into the home folder —
        // newest by timestamp — which auto-restore would then import over the
        // user's real library (the SM-S921B corruption pattern). Bail; the
        // post-restore restart re-triggers the scan on an initialized DB.
        if (libraryMetaDao.getMeta() == null) return
        // Idempotent device-id migration BEFORE any save: without this, a scan
        // racing the startup migrator could saveNow() under the old UUID in
        // the window between the snapshot rename and the id overwrite,
        // permanently recreating the orphan file the migration just removed.
        // Running it here orders every trigger path (onStart, startup gate,
        // manual Sync now) after the migration; completed migrations bail in
        // two cheap reads.
        runCatching { deviceIdMigrator.migrateIfNeeded() }
        pruneAndRefreshRestorePoints()
        val ownDeviceId = settingsRepository.getOrCreateDeviceId()
        val metas = findSnapshotMetasNewestFirst(context, homeUri)
        val markers = settingsRepository.getSyncMergedMarkers()
        val now = System.currentTimeMillis()
        val c = classify(metas, ownDeviceId, markers, SCHEMA_VERSION, now)

        val dismissedVersion = settingsRepository.getSyncUpdateNoticeDismissedVersion()
        _updateNotices.value =
            if (dismissedVersion == currentAppVersionName()) emptyList()
            else c.tooNew.map { SyncUpdateNotice(it.deviceLabel.ifEmpty { "another device" }) }
        // The scan proper is done; stamp the debounce window. Set here (not on
        // the early debounce return) so a debounced call never extends the
        // window, and forced scans reset it too.
        lastScanAtMillis = System.currentTimeMillis()
        if (c.changed.isEmpty()) {
            _promptState.value = null
            return
        }

        val key = changeSetKey(c.changed)
        if (settingsRepository.getDeclinedSyncChangeSet() == key) return

        // ONLY now — changed candidates exist and weren't declined — refresh
        // local fingerprints/lastChangedAt (and our snapshot) so unsaved local
        // session changes can't silently lose a conflict in the previews below.
        autoSaveObserver.saveNow()

        // Preview each candidate against the live DB. Display approximation:
        // sync() re-previews each snapshot just before applying it.
        val newNames = mutableListOf<String>()
        val updNames = mutableListOf<String>()
        val delNames = mutableListOf<String>()
        val conflictLines = mutableListOf<String>()
        var anyAction = false
        val previewed = mutableListOf<LibrarySnapshotMeta>()
        for (m in c.changed) {
            previewSnapshot(m, markers, now)?.let { diff ->
                previewed += m
                if (!diff.isEmpty) anyAction = true
                newNames += diff.newVaults.map { it.name }
                updNames += diff.updatedVaults.map { it.name }
                delNames += diff.toDelete.map { it.name }
                conflictLines += diff.conflicts.map { ref ->
                    val remoteWon = diff.toCopy.any { it.vaultId == ref.vaultId }
                    val keep =
                        if (remoteWon) m.deviceLabel.ifEmpty { "the other device" }
                        else "this device"
                    "${ref.name} changed on both devices. Keeping the newer version from $keep"
                }
            }
        }
        if (!anyAction) {
            // Advance markers silently ONLY for snapshots that were actually
            // inspected and found empty. A failed preview (unreadable /
            // corrupt / mid-transfer snapshot) must NOT advance its marker —
            // that would permanently suppress changes we never read; it gets
            // retried on the next scan instead. License-blocked snapshots are
            // never in [previewed]: their changes must merge after activation.
            for (m in previewed) {
                settingsRepository.setSyncMergedMarker(m.deviceId, m.lastWrittenAtMillis)
            }
            _promptState.value = null
            return
        }
        val allowed = c.changed
        _promptState.value = SyncPromptUi(
            deviceLabels = allowed
                .map { it.deviceLabel.ifEmpty { it.deviceId.take(8) } }
                .distinct(),
            newVaults = newNames.distinct(),
            updatedVaults = updNames.distinct(),
            deletedVaults = delNames.distinct(),
            conflictLines = conflictLines.distinct(),
            candidates = allowed,
            changeSetKey = key,
        )
    }

    suspend fun sync(): Unit = syncMutex.withLock {
        val prompt = _promptState.value ?: return
        _promptState.value = null
        val markers = settingsRepository.getSyncMergedMarkers()
        val now = System.currentTimeMillis()
        val applied = mutableListOf<Pair<LibrarySnapshotMeta, SyncDiff>>()
        val conflicts = mutableListOf<VaultRef>()
        autoSaveObserver.exclusive {
            backupFile = engine.backupLiveDb()
            // Rotate the oldest restore point out as soon as the new one
            // exists (count-based retention, newest MAX_RESTORE_POINTS kept).
            engine.pruneBackups()
            // Oldest -> newest so the newest writer wins where they overlap.
            for (m in prompt.candidates.sortedBy { it.lastWrittenAtMillis }) {
                val local = materializeToFile(m.uri) ?: continue
                try {
                    // Per-candidate tolerance: a snapshot the sync tool is
                    // rewriting mid-prompt (torn/corrupt) must skip, not crash
                    // the whole merge. Its marker isn't advanced (only
                    // `applied` entries reach finalize), so it retries on the
                    // next scan. Mirrors previewSnapshot's tolerance.
                    runCatching {
                        val remote = engine.openRemote(local)
                        try {
                            val diff = engine.preview(remote, markers[m.deviceId] ?: 0L, now)
                            if (diff.isEmpty) {
                                remote.close()
                            } else {
                                engine.apply(diff, remote)
                                // apply() closed the Room handle; close() also
                                // removes the temp copy from the databases dir.
                                remote.close()
                            }
                            applied.add(m to diff)
                            conflicts += diff.conflicts
                        } catch (e: Exception) {
                            runCatching { remote.close() }
                            throw e
                        }
                    }.onFailure {
                        android.util.Log.w(TAG, "sync: skipping unreadable snapshot ${m.fileName}", it)
                    }
                } finally {
                    if (m.uri.scheme == "content") local.delete()
                }
            }
        }
        _restorableConflicts.value = conflicts.distinctBy { it.vaultId }
        refreshRestorePoints()
        settingsRepository.setDeclinedSyncChangeSet(null)
        val added = applied.sumOf { it.second.newVaults.size }
        val updated = applied.sumOf { it.second.updatedVaults.size }
        val deleted = applied.sumOf { it.second.toDelete.size }
        // Post-merge cleanup for tombstone-deleted vaults: their DB rows are gone, but the
        // device-local media index isn't synced — its bindings for them survive, and a
        // same-name recreate would re-match the stale entries (the index only binds to
        // existing vaults, so they don't auto-resurrect, but a recreate would). Unwind them
        // by name here, mirroring the local-delete path. DB-only; the post-restart startup
        // scan refreshes the index from disk. Files are deliberately left untouched.
        for (name in applied.flatMap { it.second.toDelete }.map { it.name }.distinct()) {
            runCatching { mediaMatcher.removeBindingsForVaultName(name) }
        }
        // The carousel/grid artwork thumbnail cache is keyed by VAULT ID (one
        // `<vaultId>.webp`), and is only invalidated on a *local* artwork change. A merge
        // can swap a vault's artwork (new artwork_resource_id) without touching that
        // device's thumbnail, so the receiving device keeps rendering the old cover. Drop
        // the thumb for every copied/updated vault so it regenerates from the merged
        // artwork. (Map thumbnails are keyed by resource id, so they self-invalidate.)
        for (vaultId in applied.flatMap { it.second.toCopy }.map { it.vaultId }.distinct()) {
            runCatching { artworkThumbnailCache.invalidate(vaultId) }
        }
        if (added + updated + deleted == 0) {
            // Everything re-previewed empty (live DB changed since the prompt):
            // just converge markers right away.
            autoSaveObserver.saveNow()
            for ((meta, _) in applied) {
                settingsRepository.setSyncMergedMarker(meta.deviceId, meta.lastWrittenAtMillis)
            }
            return
        }
        // Finalize inline: save snapshot, advance markers, then restart.
        // Rollback is covered by the pre-sync restore points in Settings.
        // On-device finding: the in-place merge does not reliably refresh all
        // UI state (the invalidationTracker poke is insufficient). Restart —
        // same pattern as folder import — so the merged library is presented.
        autoSaveObserver.saveNow()
        for ((meta, _) in applied) {
            settingsRepository.setSyncMergedMarker(meta.deviceId, meta.lastWrittenAtMillis)
        }
        runCatching { com.twinlore.app.ui.utils.restartApp(context) }
    }

    suspend fun decline(): Unit = syncMutex.withLock {
        val prompt = _promptState.value ?: return
        settingsRepository.setDeclinedSyncChangeSet(prompt.changeSetKey)
        _promptState.value = null
    }

    /** "Restore previous version" for one conflicted vault. */
    suspend fun restoreVaultFromBackup(vaultId: String): Unit = syncMutex.withLock {
        val backup = backupFile?.takeIf { it.exists() } ?: return
        val ref = _restorableConflicts.value.firstOrNull { it.vaultId == vaultId } ?: return
        autoSaveObserver.exclusive {
            engine.restoreVaultWholesale(backup, ref)
        }
        // The restore is a fresh local change: the save's SyncStateRefresher
        // stamps a new lastChangedAt, so it propagates to other devices.
        autoSaveObserver.saveNow()
        _restorableConflicts.value = _restorableConflicts.value.filterNot { it.vaultId == vaultId }
        refreshRestorePoints()
    }

    /**
     * Restores the WHOLE library from one pre-sync restore point. The engine
     * closes Room and replaces the live DB file, so the app must restart —
     * [com.twinlore.app.ui.utils.restartApp] kills the process; nothing after
     * it runs.
     */
    suspend fun restoreToPoint(point: SyncRestorePoint): Unit = syncMutex.withLock {
        autoSaveObserver.exclusive {
            engine.restoreLibraryFromBackup(point.file)
        }
        runCatching { com.twinlore.app.ui.utils.restartApp(context) }
        refreshRestorePoints() // unreachable in production (restartApp exits); keeps tests honest
    }

    suspend fun dismissUpdateNotices() {
        settingsRepository.setSyncUpdateNoticeDismissedVersion(currentAppVersionName())
        _updateNotices.value = emptyList()
    }

    /** Rotates out backups past the newest-3 retention, then refreshes [restorePoints]. */
    private fun pruneAndRefreshRestorePoints() {
        engine.pruneBackups()
        // If the conflict-restore backup of this process's last sync was just
        // pruned, the per-vault restore offers are dead too.
        if (backupFile?.exists() == false) {
            backupFile = null
            _restorableConflicts.value = emptyList()
        }
        refreshRestorePoints()
    }

    private fun refreshRestorePoints() {
        _restorePoints.value = engine.listBackups().mapNotNull { f ->
            LibrarySyncEngine.backupMillis(f)?.let { SyncRestorePoint(f, it) }
        }
    }

    private suspend fun previewSnapshot(
        meta: LibrarySnapshotMeta,
        markers: Map<String, Long>,
        nowMillis: Long,
    ): SyncDiff? {
        val local = materializeToFile(meta.uri) ?: return null
        return try {
            val remote = engine.openRemote(local)
            try {
                engine.preview(remote, markers[meta.deviceId] ?: 0L, nowMillis)
            } finally {
                remote.close()
            }
        } catch (_: Exception) {
            null
        } finally {
            if (meta.uri.scheme == "content") local.delete()
        }
    }

    private fun materializeToFile(uri: Uri): File? = when (uri.scheme) {
        "file" -> uri.path?.let(::File)?.takeIf { it.isFile }
        "content" -> runCatching {
            val tmp = File.createTempFile("sync_snap", ".gametwin", context.cacheDir)
            val ok = context.contentResolver.openInputStream(uri)?.use { input ->
                tmp.outputStream().use { input.copyTo(it) }
                true
            } ?: false
            if (ok) tmp else { tmp.delete(); null }
        }.getOrNull()
        else -> null
    }

    private fun currentAppVersionName(): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
    }.getOrDefault("")

    companion object {
        private const val TAG = "LibrarySync"

        /** Foreground scans within this window of the last completed scan are skipped. */
        const val SCAN_DEBOUNCE_MILLIS = 30_000L

        data class Classified(
            val foreign: List<LibrarySnapshotMeta>,
            val changed: List<LibrarySnapshotMeta>,
            val tooNew: List<LibrarySnapshotMeta>,
        )

        /**
         * Foreign snapshots not written within this window are never merge
         * candidates. Guards fresh installs (empty markers): orphan
         * snapshots from dead installs would otherwise merge once,
         * resurrecting months-old vault states whose file sets then DELETE
         * live files via the wholesale per-vault merge (Syncthing archives
         * them to .stversions — the Castlevania incident). A real device
         * offline longer than this rewrites its snapshot on its next save
         * and becomes mergeable again.
         */
        const val STALE_SNAPSHOT_MAX_AGE_MILLIS: Long = 30L * 24 * 60 * 60 * 1000

        /**
         * Pure classification of folder snapshots. Snapshots written by a
         * NEWER app build ([LibrarySnapshotMeta.schemaVersion] above ours) are
         * never opened or merged — they only produce an "update the app"
         * notice. Blank deviceIds (corrupt meta) are ignored entirely. No
         * snapshot is ever deleted from the folder, however old or dormant;
         * snapshots older than [STALE_SNAPSHOT_MAX_AGE_MILLIS] stay listed in
         * [Classified.foreign] (device list UI) but never merge.
         */
        fun classify(
            metas: List<LibrarySnapshotMeta>,
            ownDeviceId: String,
            markers: Map<String, Long>,
            supportedSchemaVersion: Int,
            nowMillis: Long,
        ): Classified {
            val foreign = metas.filter { it.deviceId != ownDeviceId && it.deviceId.isNotEmpty() }
            val tooNew = foreign.filter { it.schemaVersion > supportedSchemaVersion }
            val readable = foreign - tooNew.toSet()
            val changed = readable.filter {
                it.lastWrittenAtMillis > (markers[it.deviceId] ?: 0L) &&
                    nowMillis - it.lastWrittenAtMillis <= STALE_SNAPSHOT_MAX_AGE_MILLIS
            }
            return Classified(foreign, changed, tooNew)
        }

        fun changeSetKey(candidates: List<LibrarySnapshotMeta>): String =
            candidates.map { "${it.deviceId}:${it.lastWrittenAtMillis}" }.sorted().joinToString("|")
    }
}
