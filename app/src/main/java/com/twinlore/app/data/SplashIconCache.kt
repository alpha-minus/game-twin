package com.twinlore.app.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Synchronous mirror of the effective app-icon selection, used to seed the
 * startup splash on its very first frame.
 *
 * The authoritative selection lives in DataStore ([SettingsRepository.appIcon]),
 * which is read asynchronously — so the first composition always falls back to
 * [AppIcon.DEFAULT] until DataStore emits, flashing the default purple splash +
 * default logo before flipping to the themed icon. Both splash surfaces
 * (MainActivity's startup gate and LibraryScreen's preload overlay) hit this,
 * producing a double flicker.
 *
 * SharedPreferences is backed by an in-memory map after the first (cheap) load,
 * so [read] returns without a DataStore/coroutine round-trip and can run on the
 * main thread during `onCreate` before `setContent`. [write] is called whenever
 * the effective icon changes so the NEXT cold start seeds correctly.
 */
@Singleton
class SplashIconCache @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Last persisted effective icon, or [AppIcon.DEFAULT] if none was written. */
    fun read(): AppIcon = AppIcon.fromId(prefs.getString(KEY_ICON_ID, null))

    /** Persist [icon] so the next cold start seeds the splash without a flicker. */
    fun write(icon: AppIcon) {
        prefs.edit().putString(KEY_ICON_ID, icon.id).apply()
    }

    private companion object {
        const val PREFS_NAME = "splash_icon_cache"
        const val KEY_ICON_ID = "effective_app_icon_id"
    }
}
