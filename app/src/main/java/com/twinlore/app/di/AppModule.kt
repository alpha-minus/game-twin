package com.twinlore.app.di

import com.twinlore.app.data.LastPlayedSource
import com.twinlore.app.data.SettingsRepository
import com.twinlore.app.data.TrackedFileSource
import com.twinlore.app.data.privacy.OkHttpPrivacyHttpFetcher
import com.twinlore.app.data.privacy.PrivacyHttpFetcher
import com.twinlore.app.data.privacy.PrivacyPreferences
import com.twinlore.app.domain.StateManager
import com.twinlore.app.ui.navigation.GlobalFocusActionTracker
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import okhttp3.OkHttpClient
import javax.inject.Singleton

/**
 * Application-level dependency injection module
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    // StateManager is already annotated with @Singleton and @Inject
    // No additional bindings needed here for now

    @Provides
    @Singleton
    fun provideGlobalFocusActionTracker(): GlobalFocusActionTracker {
        return GlobalFocusActionTracker()
    }

    /**
     * Application-wide OkHttpClient used for ad-hoc HTTP downloads
     * (e.g. browser image saves, file downloads from BrowserSaveCoordinator).
     * Scraper modules construct their own clients locally with different timeouts.
     */
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build()

    /**
     * Source for the file or folder used by auto-refresh and startup focus.
     * When LastPlayedSource is RETROARCH: uriFlow is RetroArch folder URI (watcher tracks content_history.lpl inside; file URI is not used so change detection works with tree permission).
     * When LastPlayedSource is ES_DE: uriFlow is ES-DE folder URI (or null if not set).
     * When LastPlayedSource is ACHIEVEMENTS or NOT_CONFIGURED: uriFlow is null (no file to watch); auto-refresh is off.
     */
    @Provides
    @Singleton
    fun provideTrackedFileSource(settingsRepository: SettingsRepository): TrackedFileSource {
        val uriFlow = combine(
            settingsRepository.lastPlayedSource,
            settingsRepository.retroArchHistoryFolder,
            settingsRepository.esdeFolderUri,
            settingsRepository.cocoonFolderUri
        ) { source, raFolderUri, esdeUri, cocoonUri ->
            when (source) {
                LastPlayedSource.RETROARCH -> raFolderUri
                LastPlayedSource.ES_DE -> esdeUri
                LastPlayedSource.COCOON -> cocoonUri
                LastPlayedSource.ACHIEVEMENTS -> null
                LastPlayedSource.NOT_CONFIGURED -> null
            }
        }
        // Auto-refresh applies to file-based detection sources only: ES-DE
        // and Cocoon. RetroArch is excluded
        // — its detection fires only when the user presses Refresh
        // (matches the Achievements source). DataStore key
        // `retroarch_auto_refresh` is kept verbatim (no migration); only
        // the gating logic changed.
        val enabledFlow = combine(
            settingsRepository.lastPlayedSource,
            settingsRepository.retroArchAutoRefresh
        ) { source, auto ->
            auto && (source == LastPlayedSource.ES_DE || source == LastPlayedSource.COCOON)
        }
        return TrackedFileSource(
            uriFlow = uriFlow,
            enabledFlow = enabledFlow,
            sourceFlow = settingsRepository.lastPlayedSource
        )
    }

    @Provides
    @Singleton
    internal fun providePrivacyPreferences(
        settingsRepository: SettingsRepository,
        @ApplicationScope scope: CoroutineScope,
    ): PrivacyPreferences =
        PrivacyPreferences(
            enabledFlow = settingsRepository.privacyBlockingEnabled,
            scope = scope,
        )

    @Provides
    @Singleton
    internal fun providePrivacyHttpFetcher(
        impl: OkHttpPrivacyHttpFetcher,
    ): PrivacyHttpFetcher = impl

    /**
     * PageGroupSaveCoordinator owns one offscreen WebView per invocation and
     * runs save jobs sequentially. Singleton scope so the in-flight job
     * survives BrowserScreen recomposition and the StateFlow is shared by
     * the selection sheet + progress banner.
     */
    @Provides
    @Singleton
    fun providePageGroupSaveCoordinator(
        @ApplicationContext context: android.content.Context,
        sink: com.twinlore.app.data.library.PageGroupSaveSink,
        sidecarCache: com.twinlore.app.data.PageGroupSidecarCache
    ): com.twinlore.app.data.library.PageGroupSaveCoordinator =
        com.twinlore.app.data.library.PageGroupSaveCoordinator(
            save = sink,
            mainDispatcher = kotlinx.coroutines.Dispatchers.Main,
            cacheDir = java.io.File(context.cacheDir, "page-group-save"),
            sidecarCache = sidecarCache
        )

    /**
     * Explicit provider so the optional sidecar cache constructor parameter
     * resolves cleanly (Hilt does not honor default values on `@Inject`
     * constructors). Tests construct the repository directly and pass null.
     */
    @Provides
    @Singleton
    fun providePageGroupRepository(
        savedTabDao: com.twinlore.app.data.local.SavedTabDao,
        resourceFileDao: com.twinlore.app.data.local.ResourceFileDao,
        pageGroupEntryDao: com.twinlore.app.data.local.PageGroupEntryDao,
        pageGroupEntryAnchorDao: com.twinlore.app.data.local.PageGroupEntryAnchorDao,
        scrollMarkerDao: com.twinlore.app.data.local.ScrollMarkerDao,
        groupsFolderResolver: com.twinlore.app.data.GroupsFolderResolver,
        database: com.twinlore.app.data.local.VaultDatabase,
        sidecarCache: com.twinlore.app.data.PageGroupSidecarCache
    ): com.twinlore.app.data.PageGroupRepository =
        com.twinlore.app.data.PageGroupRepository(
            savedTabDao = savedTabDao,
            resourceFileDao = resourceFileDao,
            pageGroupEntryDao = pageGroupEntryDao,
            pageGroupEntryAnchorDao = pageGroupEntryAnchorDao,
            scrollMarkerDao = scrollMarkerDao,
            groupsFolderResolver = groupsFolderResolver,
            database = database,
            sidecarCache = sidecarCache
        )

    /**
     * Resolves the on-disk folder for a page-group given its groupTabId.
     * PageGroupRepository uses this for manifest writes + best-effort
     * file deletion. For file:// homes the path is real; for SAF homes
     * the returned File is a cache-dir placeholder (SAF mutations go
     * through DocumentFile elsewhere — see PageGroupSaveTargets).
     */
    @Provides
    @Singleton
    fun provideGroupsFolderResolver(
        @ApplicationContext context: android.content.Context,
        folder: com.twinlore.app.data.library.LibraryFolderManager,
        savedTabDao: com.twinlore.app.data.local.SavedTabDao,
        vaultDao: com.twinlore.app.data.local.VaultDao
    ): com.twinlore.app.data.GroupsFolderResolver =
        object : com.twinlore.app.data.GroupsFolderResolver {
            override suspend fun resolve(groupTabId: String): java.io.File {
                val tab = savedTabDao.getTabById(groupTabId)
                val vault = tab?.vaultId?.let { vaultDao.getVaultById(it) }
                val vaultName = vault?.let { com.twinlore.app.data.library.LibraryFolderManager.resolveFolderName(it) }
                return when {
                    vaultName == null ->
                        java.io.File(java.io.File(context.cacheDir, "page-groups"), groupTabId)
                    tab?.groupFolderName != null ->
                        com.twinlore.app.data.library.PageGroupSaveTargets.groupFolder(
                            context, folder, vaultName, tab.groupFolderName
                        )
                    else ->
                        com.twinlore.app.data.library.PageGroupSaveTargets.legacyGroupFolder(
                            context, folder, vaultName, groupTabId
                        )
                }
            }

            override suspend fun writeManifest(groupTabId: String, json: String) {
                val tab = savedTabDao.getTabById(groupTabId)
                val vault = tab?.vaultId?.let { vaultDao.getVaultById(it) }
                val vaultName = vault?.let { com.twinlore.app.data.library.LibraryFolderManager.resolveFolderName(it) }
                // Legacy groups (no folder name) keep the File-based path: their
                // folder is groups/<groupTabId>/, which only exists on file:// homes.
                if (vaultName == null || tab?.groupFolderName == null) {
                    super.writeManifest(groupTabId, json)
                    return
                }
                val written = com.twinlore.app.data.library.PageGroupSaveTargets.writeGroupTextFile(
                    context = context, folder = folder, vaultName = vaultName,
                    groupFolderName = tab.groupFolderName,
                    filename = "manifest.json", mimeType = "application/json", content = json
                )
                if (!written) {
                    android.util.Log.w("GroupsFolderResolver", "manifest write failed for group $groupTabId")
                }
            }

            override suspend fun deleteOnDisk(groupTabId: String) {
                val tab = savedTabDao.getTabById(groupTabId) ?: return
                val vault = tab.vaultId.let { vaultDao.getVaultById(it) } ?: return
                val vaultName = com.twinlore.app.data.library.LibraryFolderManager.resolveFolderName(vault)
                if (tab.groupFolderName != null) {
                    com.twinlore.app.data.library.PageGroupSaveTargets.deleteGroupFolder(
                        context, folder, vaultName, tab.groupFolderName
                    )
                } else {
                    com.twinlore.app.data.library.PageGroupSaveTargets.legacyDeleteGroupFolder(
                        context, folder, vaultName, groupTabId
                    )
                }
            }
        }
}

