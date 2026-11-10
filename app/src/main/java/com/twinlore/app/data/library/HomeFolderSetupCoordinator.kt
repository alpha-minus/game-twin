package com.twinlore.app.data.library

import android.content.Context
import android.net.Uri
import com.twinlore.app.data.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HomeFolderSetupCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    private val mediaSearchPaths: MediaSearchPathRepository,
    private val resourceResolver: ResourceResolver,
) {
    suspend fun useSafTree(uri: Uri) {
        context.contentResolver.takePersistableUriPermission(
            uri,
            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        settings.setHomeFolderUri(uri.toString())
        mediaSearchPaths.syncAutoFromSettings()
        // The resolved tree Uri itself just changed; every cached Uri was built
        // against the old tree and may no longer be valid.
        resourceResolver.invalidateAll()
    }
}
