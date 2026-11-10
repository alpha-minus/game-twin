package com.twinlore.app.data.library

import android.content.Context
import com.twinlore.app.data.SettingsRepository
import com.twinlore.app.data.library.sync.SyncStateRefresher
import com.twinlore.app.data.local.LibraryMetaDao
import com.twinlore.app.data.local.LibraryMetaEntity
import com.twinlore.app.data.local.SCHEMA_VERSION
import com.twinlore.app.data.local.VaultDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Saves the library snapshot to <library-folder>/library.<shortDeviceId>.gametwin:
 *   1. Update the library_meta row in Room.
 *   2. PRAGMA wal_checkpoint(TRUNCATE) on the live DB.
 *   3. Copy DB file into the library folder via LibraryFileStore.
 *
 * Called by:
 *   - ProcessLifecycleObserver on ON_PAUSE (auto-save) via [saveAsync].
 *   - SettingsScreen "Save Library" button (explicit) via [saveNow].
 *   - SettingsScreen library-folder switch flow via [exclusive].
 *
 * Saves are serialized by [mutex]. [saveAsync] additionally deduplicates
 * rapid triggers: at most one queued save at any time. The [exclusive]
 * helper lets the library-load flow take the same lock so an in-flight
 * or queued auto-save can't race the close-DB + file-swap sequence.
 */
@Singleton
class LibraryAutoSaveObserver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: LibraryFileStore,
    private val settingsRepository: SettingsRepository,
    private val libraryMetaDao: LibraryMetaDao,
    private val database: VaultDatabase,
    private val syncStateRefresher: SyncStateRefresher
) {
    private val savePending = java.util.concurrent.atomic.AtomicBoolean(false)
    private val mutex = Mutex()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    suspend fun <T> exclusive(block: suspend () -> T): T = mutex.withLock { block() }

    suspend fun saveNow(): LibraryFileSaveOutcome? = exclusive {
        withContext(Dispatchers.IO) {
            runCatching { syncStateRefresher.refresh(database, System.currentTimeMillis()) }
            val deviceId = settingsRepository.getOrCreateDeviceId()
            val label = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}".trim()
            libraryMetaDao.upsertMeta(
                LibraryMetaEntity(
                    schemaVersion = SCHEMA_VERSION,
                    lastWrittenAtMillis = System.currentTimeMillis(),
                    lastWrittenDeviceId = deviceId,
                    lastWrittenDeviceLabel = label
                )
            )
            database.openHelper.writableDatabase
                .query("PRAGMA wal_checkpoint(TRUNCATE)")
                .use { it.moveToFirst() }
            val dbFile = context.getDatabasePath("vault_database")
            if (!dbFile.exists()) return@withContext null
            store.save(dbFile, deviceId)
        }
    }

    fun saveAsync() {
        if (!savePending.compareAndSet(false, true)) return
        scope.launch {
            try {
                runCatching { saveNow() }
            } finally {
                savePending.set(false)
            }
        }
    }
}
