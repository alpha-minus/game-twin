package com.twinlore.app.data

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.annotation.VisibleForTesting
import com.twinlore.app.data.fold.FoldHandleStyle
import com.twinlore.app.data.security.AppSecret
import com.twinlore.app.data.security.AppSecretsStore
import com.twinlore.app.domain.Category
import com.twinlore.app.domain.DeviceType
import com.twinlore.app.domain.FilePickerViewMode
import com.twinlore.app.domain.FilePickerSortField
import com.twinlore.app.domain.SortDirection
import com.twinlore.app.domain.LibrarySortField
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for managing app settings using DataStore Preferences
 */
@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val secrets: AppSecretsStore = AppSecretsStore(context),
) {
    companion object {
        @VisibleForTesting
        internal val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
            name = "settings",
            produceMigrations = { listOf(removeOrphanRetroArchHistoryFileUri()) }
        )

        private fun removeOrphanRetroArchHistoryFileUri(): androidx.datastore.core.DataMigration<Preferences> =
            object : androidx.datastore.core.DataMigration<Preferences> {
                private val orphanKey = stringPreferencesKey("retroarch_history_file_uri")
                override suspend fun shouldMigrate(currentData: Preferences): Boolean =
                    currentData.contains(orphanKey)
                override suspend fun migrate(currentData: Preferences): Preferences =
                    currentData.toMutablePreferences().apply { remove(orphanKey) }.toPreferences()
                override suspend fun cleanUp() {}
            }
        
        // Settings keys
        private val THEME_KEY = stringPreferencesKey("theme")
        private val FONT_KEY = stringPreferencesKey("font")
        private val SELECTED_APP_ICON_KEY = stringPreferencesKey("selected_app_icon")
        private val ART_DISPLAY_MODE_KEY = stringPreferencesKey("art_display_mode")
        private val AUTO_BOX_ART_SCRAPING_KEY = booleanPreferencesKey("auto_box_art_scraping")
        private val RETROARCH_HISTORY_FOLDER_KEY = stringPreferencesKey("retroarch_history_folder")
        // Separate display path so the UI can show "RetroArch" instead of the raw
        // SAF tree URI. Mirrors esde_folder_path / cocoon_folder_path. Users who
        // picked their folder before this key existed have a URI but no path —
        // UI falls back to "Not Set", one re-pick repopulates.
        private val RETROARCH_HISTORY_FOLDER_PATH_KEY = stringPreferencesKey("retroarch_history_folder_path")
        private val RETROARCH_AUTO_REFRESH_KEY = booleanPreferencesKey("retroarch_auto_refresh")
        private val PREFER_SCRAPER_FOR_ARTWORK_KEY = booleanPreferencesKey("prefer_scraper_for_artwork")
        private val SURFACE_LOCAL_IMAGES_IN_MAPS_TAB_KEY = booleanPreferencesKey("surface_local_images_in_maps_tab")
        private val CREATE_VAULT_IF_NOT_FOUND_KEY = booleanPreferencesKey("create_vault_if_not_found")
        private val OPEN_LAST_PLAYED_ON_LAUNCH_KEY = booleanPreferencesKey("open_last_played_on_launch")
        private val NOW_PLAYING_VAULT_ID_KEY = stringPreferencesKey("now_playing_vault_id")
        private val LAST_HANDLED_LAUNCH_MARKER_KEY = stringPreferencesKey("last_handled_launch_marker")
        private val LAST_PLAYED_SOURCE_KEY = stringPreferencesKey("last_played_source")
        private val ESDE_FOLDER_URI_KEY = stringPreferencesKey("esde_folder_uri")
        private val ESDE_FOLDER_PATH_KEY = stringPreferencesKey("esde_folder_path")
        private val COCOON_FOLDER_URI_KEY = stringPreferencesKey("cocoon_folder_uri")
        private val COCOON_FOLDER_PATH_KEY = stringPreferencesKey("cocoon_folder_path")
        private val COCOON_ICON_PACK_HINT_SHOWN_KEY = booleanPreferencesKey("cocoon_icon_pack_hint_shown")
        private val ROMS_FOLDER_URI_KEY = stringPreferencesKey("roms_folder_uri")
        private val FOCUS_COLOR_KEY = stringPreferencesKey("focus_color")
        private val GRID_DISPLAY_RATIO_KEY = stringPreferencesKey("grid_display_ratio")
        private val STEAMGRID_API_KEY = stringPreferencesKey("steamgrid_api_key")
        private val STEAM_API_KEY = stringPreferencesKey("steam_api_key")
        private val THEGAMESDB_API_KEY = stringPreferencesKey("thegamesdb_api_key")
        private val DEFAULT_ASSET_TYPE_KEY = stringPreferencesKey("default_asset_type")
        private val POWER_SAVER_MODE_KEY = booleanPreferencesKey("power_saver_mode")
        private val DEFAULT_PINS_SEEDED_KEY = booleanPreferencesKey("default_pinned_apps_seeded")
        private val ESDE_LOGO_FOLDER_KEY = stringPreferencesKey("esde_custom_logo_folder")
        private val RANDOM_ANIMATION_EXCLUDED_KEY = stringSetPreferencesKey("random_animation_excluded")
        private val AUTO_FOCUS_COLOR_DETECTION_KEY = booleanPreferencesKey("auto_focus_color_detection")
        private val HAPTIC_FEEDBACK_INTENSITY_KEY = stringPreferencesKey("haptic_feedback_intensity")
        private val FOCUS_SOUND_ENABLED_KEY = booleanPreferencesKey("focus_sound_enabled")
        private val VAULT_STORAGE_FOLDER_URI_KEY = stringPreferencesKey("vault_storage_folder_uri")
        private val HOME_FOLDER_URI_KEY = stringPreferencesKey("home_folder_uri")
        // The Achievement Journey's presentation choices. They describe how a replay looks rather
        // than what it charts, so they outlive a visit the way the app's other look-and-feel
        // settings do (user request) - and none of them can invalidate a cached journey.
        private val JOURNEY_SPEED_KEY = floatPreferencesKey("journey_speed")
        private val JOURNEY_FOLLOW_MODE_KEY = booleanPreferencesKey("journey_follow_mode")
        private val JOURNEY_AMBIENCE_KEY = stringPreferencesKey("journey_ambience")
        private val JOURNEY_PLAYER_COLOR_KEY = intPreferencesKey("journey_player_color")
        private val JOURNEY_RIVAL_COLOR_KEY = intPreferencesKey("journey_rival_color")
        private val JOURNEY_BACKGROUND_KEY = stringPreferencesKey("journey_background")
        private val JOURNEY_BACKDROP_DIM_KEY = intPreferencesKey("journey_backdrop_dim")
        private val JOURNEY_IDLE_SIGNS_KEY = booleanPreferencesKey("journey_idle_signs")
        private val DEVICE_ID_KEY = stringPreferencesKey("device_id")
        private fun landingLabelKey(i: Int) = stringPreferencesKey("landing_${i}_label")
        private fun landingAppendKey(i: Int) = stringPreferencesKey("landing_${i}_append")
        private fun landingImageKey(i: Int) = booleanPreferencesKey("landing_${i}_image")
        private fun landingSiteKey(i: Int) = stringPreferencesKey("landing_${i}_site")
        private fun landingFiletypeKey(i: Int) = stringPreferencesKey("landing_${i}_filetype")
        private val LANDING_TEMPLATES_V2_INITIALIZED_KEY = booleanPreferencesKey("landing_templates_v2_initialized")
        private val LED_ENABLED_KEY = booleanPreferencesKey("led_enabled")
        private val LED_BRIGHTNESS_KEY = intPreferencesKey("led_brightness")
        private val LED_SATURATION_BOOST_KEY = floatPreferencesKey("led_saturation_boost")
        private val LIBRARY_WALLPAPER_DIM_KEY = floatPreferencesKey("library_wallpaper_dim")
        private val LIBRARY_WALLPAPER_BLUR_KEY = floatPreferencesKey("library_wallpaper_blur")
        private val LED_TURN_OFF_KEY = stringPreferencesKey("led_turn_off")
        private val SHOW_LIBRARY_ITEM_NAMES_KEY = booleanPreferencesKey("show_library_item_names")
        private val LIBRARY_HINT_BAR_VISIBLE_KEY = booleanPreferencesKey("library_hint_bar_visible")
        private val RA_FEATURE_ENABLED_KEY = booleanPreferencesKey("ra_feature_enabled")
        private val LIBRARY_VIEW_MODE_KEY = stringPreferencesKey("library_view_mode")
        private val LIBRARY_SORT_FIELD_KEY = stringPreferencesKey("library_sort_field")
        private val LIBRARY_SORT_DIRECTION_KEY = stringPreferencesKey("library_sort_direction")
        private val LIBRARY_ACHIEVEMENT_PROGRESS_KEY = booleanPreferencesKey("library_achievement_progress")
        private val LIBRARY_GRID_COLUMNS_KEY = intPreferencesKey("library_grid_columns")
        private val LIBRARY_BACKGROUND_STYLE_KEY = stringPreferencesKey("library_background_style")
        private val RAW_ACCENT_COLOR_ARGB_KEY = intPreferencesKey("raw_accent_color_argb")
        private val SKIPPED_UPDATE_VERSION_KEY = stringPreferencesKey("skipped_update_version")
        private val LAST_REFRESH_SKIPPED_HASH_KEY = stringPreferencesKey("last_refresh_skipped_hash")
        private val LAST_ACHIEVEMENT_SYNC_AT_KEY = longPreferencesKey("last_achievement_sync_at")
        private val LAST_SUPPORT_PROMPT_AT_KEY = longPreferencesKey("last_support_prompt_at")
        private val SUPPORT_PROMPT_COUNT_KEY = intPreferencesKey("support_prompt_count")
        private val JOURNEY_EXPORT_COUNT_KEY = intPreferencesKey("journey_export_count")
        private val SUPPORT_MILESTONES_CONSUMED_KEY =
            stringSetPreferencesKey("support_milestones_consumed")
        private val LICENSE_MIGRATION_V2_KEY = booleanPreferencesKey("license_migration_v2")
        private val LEGACY_MIGRATION_COMPLETED_KEY = booleanPreferencesKey("legacy_migration_completed")
        private val ALL_FILES_PROMPT_DECLINED_KEY = booleanPreferencesKey("all_files_prompt_declined")
        private val CHECKLIST_CUSTOM_AI_PROMPT_KEY = stringPreferencesKey("checklist_custom_ai_prompt")
        private val CHECKLIST_PROMPT_TEMPLATES_JSON_KEY =
            stringPreferencesKey("checklist_prompt_templates_json")
        private val CHECKLIST_SELECTED_PROMPT_ID_KEY =
            stringPreferencesKey("checklist_selected_prompt_id")
        private val CHECKLIST_TAP_GAME_HIGH_SCORE_KEY =
            intPreferencesKey("checklist_tap_game_high_score")
        private val GUIDE_THEME_INJECT_KEY = booleanPreferencesKey("guide_theme_inject")
        private val GUIDE_SMART_FORMATTING_KEY = booleanPreferencesKey("guide_smart_formatting")
        private val PRIVACY_BLOCKING_ENABLED_KEY = booleanPreferencesKey("privacy_blocking_enabled")
        private val GUIDE_SCROLL_MARKERS_BAR_VISIBLE_KEY =
            booleanPreferencesKey("guide_scroll_markers_bar_visible")
        private val LAST_APP_STATE_KEY = stringPreferencesKey("last_app_state")
        private val LAUNCHER_THEME_ICONS_KEY = booleanPreferencesKey("launcher_apply_theme_to_icons")
        private val LAUNCHER_SHOW_NAMES_KEY = booleanPreferencesKey("launcher_show_app_names")
        private val LAUNCHER_ICON_SIZE_KEY = stringPreferencesKey("launcher_icon_size")
        private val LAUNCHER_AUTO_DETECT_KEY = booleanPreferencesKey("launcher_auto_detect_games")
        private val LAUNCHER_SHOW_CLOCK_KEY = booleanPreferencesKey("launcher_show_clock")
        private val LAUNCHER_CLOCK_SIZE_KEY = stringPreferencesKey("launcher_clock_size")
        private val LAUNCHER_APPS_COLLAPSED_KEY = booleanPreferencesKey("launcher_apps_section_collapsed")
        private val GRID_RATIO_EXPLICITLY_SET_KEY = booleanPreferencesKey("grid_ratio_explicitly_set")
        private val BUTTON_GLYPH_STYLE_KEY = stringPreferencesKey("button_glyph_style")
        private val SWAP_FACE_BUTTONS_KEY = booleanPreferencesKey("swap_face_buttons")
        private val CATALOG_SELECTED_TAB_KEY = stringPreferencesKey("catalog_selected_tab")
        private val CATALOG_DEVICE_TYPE_KEY = stringPreferencesKey("catalog_device_type")

        // File picker preferences (guide + images tabs)
        private val GUIDE_PICKER_VIEW_MODE_KEY = stringPreferencesKey("guide_picker_view_mode")
        private val GUIDE_PICKER_SORT_FIELD_KEY = stringPreferencesKey("guide_picker_sort_field")
        private val GUIDE_PICKER_SORT_DIRECTION_KEY = stringPreferencesKey("guide_picker_sort_direction")
        private val GUIDE_PICKER_SHOW_LINKS_KEY = booleanPreferencesKey("guide_picker_show_links")
        private val IMAGES_PICKER_VIEW_MODE_KEY = stringPreferencesKey("images_picker_view_mode")
        private val IMAGES_PICKER_SORT_FIELD_KEY = stringPreferencesKey("images_picker_sort_field")
        private val IMAGES_PICKER_SORT_DIRECTION_KEY = stringPreferencesKey("images_picker_sort_direction")

        // Cross-device sync preferences
        private val SYNC_MERGED_MARKERS_KEY = stringPreferencesKey("sync_merged_markers") // JSON {deviceId: millis}
        private val SYNC_DECLINED_CHANGESET_KEY = stringPreferencesKey("sync_declined_changeset")
        private val SYNC_UPDATE_NOTICE_DISMISSED_KEY = stringPreferencesKey("sync_update_notice_dismissed") // app versionName

        // AI assistant preferences
        private val GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")
        private val GEMINI_MODEL_KEY = stringPreferencesKey("gemini_model")
        private val AI_TEMPLATES_KEY = stringPreferencesKey("ai_templates_json")
        private val AI_AUTO_SCREENSHOT_KEY = booleanPreferencesKey("ai_auto_screenshot")
        private val AI_BOTTOM_TAB_KEY = booleanPreferencesKey("ai_bottom_tab_visible")
        private val ACHIEVEMENTS_TAB_KEY = booleanPreferencesKey("achievements_tab_visible")
        // Stored as "lens_global_image" because the Lens tab (since removed) introduced it; the
        // name stays so existing wallpapers survive.
        private val APP_WALLPAPER_IMAGE_KEY = stringPreferencesKey("lens_global_image")
        private val STATUS_BAR_MODE_KEY = stringPreferencesKey("status_bar_mode")

        // Fold to edge
        private val FOLD_POSITION_X_KEY = floatPreferencesKey("fold_position_x")
        private val FOLD_POSITION_Y_KEY = floatPreferencesKey("fold_position_y")
        // The enum names two releases of the handle stored. Read once, as the
        // seed for the numeric keys below, and never written again.
        private val FOLD_DIM_KEY = stringPreferencesKey("fold_dim")
        private val FOLD_HANDLE_ALPHA_KEY = floatPreferencesKey("fold_handle_alpha")
        private val FOLD_SWAP_HINT_KEY = booleanPreferencesKey("fold_swap_hint_seen")
        private val FOLD_OVERLAY_HINT_KEY = booleanPreferencesKey("fold_overlay_hint_seen")
        private val OVERLAY_ENTERED_BEFORE_KEY = booleanPreferencesKey("overlay_entered_before")
        private val FOLD_TAB_SIZE_KEY = stringPreferencesKey("fold_tab_size")
        private val FOLD_HANDLE_SIZE_DP_KEY = intPreferencesKey("fold_handle_size_dp")
        private val FOLD_HANDLE_RECESS_KEY = floatPreferencesKey("fold_handle_recess")
        private val FOLD_HANDLE_CLOCK_BATTERY_KEY = booleanPreferencesKey("fold_handle_clock_battery")
        private val OVERLAY_MODE_KEY = booleanPreferencesKey("overlay_mode")
        private val KEEP_TOP_BAR_VISIBLE_KEY = booleanPreferencesKey("keep_top_bar_visible")
        private val FOLD_PANEL_BAR_COLLAPSED_KEY =
            booleanPreferencesKey("fold_panel_bar_collapsed")
        private val FOLD_PANEL_INTRO_SEEN_KEY = booleanPreferencesKey("fold_panel_intro_seen")
        private val FOLD_PANEL_SURFACE_KEY = stringPreferencesKey("fold_panel_surface")
        private val FOLD_PANEL_AWARD_SORT_KEY = stringPreferencesKey("fold_panel_award_sort")
        private val FOLD_PANEL_AWARD_SORT_DIR_KEY =
            stringPreferencesKey("fold_panel_award_sort_dir")
        private val FOLD_PANEL_AWARD_TYPE_KEY = stringPreferencesKey("fold_panel_award_type")
        private val FOLD_PANEL_AWARD_LOCKED_KEY =
            booleanPreferencesKey("fold_panel_award_locked_only")
        private val FOLD_PANEL_AWARD_SCROLL_INDEX_KEY =
            intPreferencesKey("fold_panel_award_scroll_index")
        private val FOLD_PANEL_AWARD_SCROLL_OFFSET_KEY =
            intPreferencesKey("fold_panel_award_scroll_offset")

        /**
         * The fold defaults, in one place.
         *
         * These are not only DataStore fallbacks. `FoldButton` hands its
         * `collectAsState(initial = ...)` values straight to
         * `FoldController.fold()`, so on a cold-start press, before DataStore
         * has emitted, these numbers are what the first fold of the session
         * actually uses. Four sites once spelled them out separately and
         * nothing crossed that seam, so a changed default here would silently
         * leave the first fold on the old value.
         */
        const val DEFAULT_FOLD_POSITION_X = 1f
        const val DEFAULT_FOLD_POSITION_Y = 0.5f

        val DEFAULT_AI_TEMPLATES = listOf(
            "I'm stuck here. What should I do next?",
            "Give me a tip or strategy for this part.",
            "What's on screen, and what are my options?",
            "How do I beat or get past this?",
            "What's the best move or choice right now?",
            "Anything important here I shouldn't miss?",
        )
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun resetLegacyLandingPrefsIfNeeded() {
        // One-shot wipe of pre-V2 landing template prefs.
        // Old data had `landing_<i>_icon` plus `site:…` / `filetype:…` tokens
        // baked into `landing_<i>_append`. We reset, not migrate.
        GlobalScope.launch {
            val prefs = context.dataStore.data.first()
            if (prefs[LANDING_TEMPLATES_V2_INITIALIZED_KEY] != true) {
                context.dataStore.edit { editor ->
                    for (i in 0..9) {
                        editor.remove(landingLabelKey(i))
                        editor.remove(landingAppendKey(i))
                        editor.remove(landingImageKey(i))
                        editor.remove(landingSiteKey(i))
                        editor.remove(landingFiletypeKey(i))
                        editor.remove(stringPreferencesKey("landing_${i}_icon"))
                    }
                    editor[LANDING_TEMPLATES_V2_INITIALIZED_KEY] = true
                }
            }
        }
    }

    init {
        resetLegacyLandingPrefsIfNeeded()
    }

    private val dataStore = context.dataStore
    
    /**
     * Get theme palette setting. Returns null for SYSTEM mode.
     * Migrates legacy "LIGHT"→PAPER, "DARK"→OBSIDIAN, "SYSTEM"→null.
     */
    val themePalette: Flow<ThemePalette?> = dataStore.data.map { preferences ->
        when (val value = preferences[THEME_KEY]) {
            null, "SYSTEM" -> null
            "LIGHT" -> ThemePalette.LOBBY
            "DARK" -> ThemePalette.OBSIDIAN
            else -> try {
                ThemePalette.valueOf(value)
            } catch (_: IllegalArgumentException) {
                null
            }
        }
    }

    /**
     * Set theme palette. Pass null for SYSTEM mode.
     */
    suspend fun setThemePalette(palette: ThemePalette?) {
        dataStore.edit { preferences ->
            preferences[THEME_KEY] = palette?.name ?: "SYSTEM"
        }
    }

    /**
     * Get font setting
     */
    val appFont: Flow<AppFont> = dataStore.data.map { preferences ->
        val fontValue = preferences[FONT_KEY] ?: AppFont.DEFAULT.name
        try {
            AppFont.valueOf(fontValue)
        } catch (_: IllegalArgumentException) {
            AppFont.DEFAULT
        }
    }

    /**
     * Set font
     */
    suspend fun setAppFont(font: AppFont) {
        dataStore.edit { preferences ->
            preferences[FONT_KEY] = font.name
        }
    }

    /** Stored (ungated) app-icon selection. Entitlement is applied by consumers. */
    val appIcon: Flow<AppIcon> = dataStore.data.map { preferences ->
        AppIcon.fromId(preferences[SELECTED_APP_ICON_KEY])
    }

    suspend fun setSelectedAppIcon(icon: AppIcon) {
        dataStore.edit { preferences ->
            preferences[SELECTED_APP_ICON_KEY] = icon.id
        }
    }
    
    /**
     * Get art display mode
     */
    val artDisplayMode: Flow<ArtDisplayMode> = dataStore.data.map { preferences ->
        val modeValue = preferences[ART_DISPLAY_MODE_KEY] ?: ArtDisplayMode.FIT.name
        ArtDisplayMode.valueOf(modeValue)
    }
    
    /**
     * Set art display mode
     */
    suspend fun setArtDisplayMode(mode: ArtDisplayMode) {
        dataStore.edit { preferences ->
            preferences[ART_DISPLAY_MODE_KEY] = mode.name
        }
    }

    /**
     * Global status-cluster mode: what the landscape top bars show in the
     * clock/battery corner — standard indicators, performance meters, or nothing.
     */
    val statusBarMode: Flow<StatusBarMode> = dataStore.data.map { preferences ->
        StatusBarMode.fromName(preferences[STATUS_BAR_MODE_KEY])
    }

    suspend fun setStatusBarMode(mode: StatusBarMode) {
        dataStore.edit { preferences ->
            preferences[STATUS_BAR_MODE_KEY] = mode.name
        }
    }

    /**
     * Get auto box art scraping setting
     */
    val autoBoxArtScraping: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[AUTO_BOX_ART_SCRAPING_KEY] ?: true // Default: enabled
    }
    
    /**
     * Set auto box art scraping
     */
    suspend fun setAutoBoxArtScraping(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[AUTO_BOX_ART_SCRAPING_KEY] = enabled
        }
    }
    
    /**
     * Get RetroArch history folder path
     */
    val retroArchHistoryFolder: Flow<String?> = dataStore.data.map { preferences ->
        preferences[RETROARCH_HISTORY_FOLDER_KEY]
    }
    
    /**
     * Set RetroArch history folder path
     */
    suspend fun setRetroArchHistoryFolder(path: String?) {
        dataStore.edit { preferences ->
            if (path != null) {
                preferences[RETROARCH_HISTORY_FOLDER_KEY] = path
            } else {
                preferences.remove(RETROARCH_HISTORY_FOLDER_KEY)
            }
        }
    }

    /**
     * RetroArch folder display path (last segment of the picked SAF tree). Used
     * by the Settings UI in place of the raw URI. Mirrors [esdeFolderPath] /
     * [cocoonFolderPath].
     */
    val retroArchHistoryFolderPath: Flow<String?> = dataStore.data.map { preferences ->
        preferences[RETROARCH_HISTORY_FOLDER_PATH_KEY]
    }

    /**
     * Set RetroArch folder display path.
     */
    suspend fun setRetroArchHistoryFolderPath(path: String?) {
        dataStore.edit { preferences ->
            if (path != null) {
                preferences[RETROARCH_HISTORY_FOLDER_PATH_KEY] = path
            } else {
                preferences.remove(RETROARCH_HISTORY_FOLDER_PATH_KEY)
            }
        }
    }

    /**
     * Get RetroArch/ES-DE auto-refresh setting (watch history file and sync/focus on change).
     * Default: OFF — file watching (polling every 5s) can impact battery life.
     */
    val retroArchAutoRefresh: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[RETROARCH_AUTO_REFRESH_KEY] ?: false
    }

    /**
     * Set RetroArch auto-refresh.
     */
    suspend fun setRetroArchAutoRefresh(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[RETROARCH_AUTO_REFRESH_KEY] = enabled
        }
    }

    /**
     * When ON, the remote scraper always runs for create/refresh and its
     * artwork wins the vault.artworkResourceId slot. Local images still get
     * indexed and surface as priority entries in the artwork picker (and as
     * candidates in the maps tab) — they're just not auto-bound. PDFs/guides
     * keep binding locally regardless. Default: OFF (local match short-
     * circuits the scrape).
     */
    val preferScraperForArtwork: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[PREFER_SCRAPER_FOR_ARTWORK_KEY] ?: false
    }

    suspend fun setPreferScraperForArtwork(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PREFER_SCRAPER_FOR_ARTWORK_KEY] = enabled
        }
    }

    /**
     * When ON, every locally-bound ARTWORK match also creates a SavedMap row
     * so it shows up in the maps tab's "Saved images" picker. Default: OFF
     * because a heavy library can flood the maps tab with covers/fanart.
     */
    val surfaceLocalImagesInMapsTab: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[SURFACE_LOCAL_IMAGES_IN_MAPS_TAB_KEY] ?: false
    }

    suspend fun setSurfaceLocalImagesInMapsTab(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[SURFACE_LOCAL_IMAGES_IN_MAPS_TAB_KEY] = enabled
        }
    }

    /**
     * Get "create new vault if not found" setting (when auto-refresh detects a game with no matching vault).
     */
    val createVaultIfNotFound: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[CREATE_VAULT_IF_NOT_FOUND_KEY] ?: true
    }

    /**
     * Set create vault if not found.
     */
    suspend fun setCreateVaultIfNotFound(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[CREATE_VAULT_IF_NOT_FOUND_KEY] = enabled
        }
    }

    /**
     * When ON, app start and return-to-foreground detect the last played game and
     * present it via the Library's fade + fullscreen showcase, which auto-enters the
     * vault after a short dwell. Cold start and a foreground while already on the
     * Library always run; a foreground from elsewhere in the app runs only when a new
     * launch happened since the last handled one. Default: ON - configuring a
     * detection source is what people configure it *for*, and an install that
     * sets one up and never opens Settings again should get the behaviour.
     */
    val openLastPlayedOnLaunch: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[OPEN_LAST_PLAYED_ON_LAUNCH_KEY] ?: true
    }

    /**
     * Set open last played on launch.
     */
    suspend fun setOpenLastPlayedOnLaunch(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[OPEN_LAST_PLAYED_ON_LAUNCH_KEY] = enabled
        }
    }

    /**
     * Where the folded tab sits, as a fraction of each travel axis. Clamped on
     * write so a drag that overshoots cannot persist a tab off the display.
     *
     * Defaults to the right edge at mid-height, which is where the old
     * edge-based default put it. Centre-screen would drop the very first fold
     * dead in the middle of whatever game is running.
     */
    val foldPositionX: Flow<Float> =
        dataStore.data.map { (it[FOLD_POSITION_X_KEY] ?: DEFAULT_FOLD_POSITION_X).coerceIn(0f, 1f) }

    suspend fun setFoldPositionX(fraction: Float) {
        dataStore.edit { it[FOLD_POSITION_X_KEY] = fraction.coerceIn(0f, 1f) }
    }

    val foldPositionY: Flow<Float> =
        dataStore.data.map { (it[FOLD_POSITION_Y_KEY] ?: DEFAULT_FOLD_POSITION_Y).coerceIn(0f, 1f) }

    suspend fun setFoldPositionY(fraction: Float) {
        dataStore.edit { it[FOLD_POSITION_Y_KEY] = fraction.coerceIn(0f, 1f) }
    }

    /**
     * Whether overlay mode is on: entered, and not left through one of its
     * own ways back into the app. While it is on, every start of the app goes
     * back into overlay. Stored as the key's absence when off.
     */
    val overlayMode: Flow<Boolean> =
        dataStore.data.map { it[OVERLAY_MODE_KEY] ?: false }

    suspend fun setOverlayMode(on: Boolean) {
        dataStore.edit {
            if (on) it[OVERLAY_MODE_KEY] = true
            else it.remove(OVERLAY_MODE_KEY)
        }
    }

    /**
     * How solid the folded handle is drawn, 0f..1f.
     *
     * Constant now rather than a value the handle settles to: it does not fade
     * on idle and does not brighten under a finger, so this is simply its
     * opacity. Falls back to whatever the old `fold_dim` enum name meant
     * before falling back to the default, so an existing handle keeps the
     * look it was left with.
     */
    val foldHandleAlpha: Flow<Float> = dataStore.data.map { prefs ->
        val stored = prefs[FOLD_HANDLE_ALPHA_KEY]
            ?: FoldHandleStyle.alphaFromLegacyName(prefs[FOLD_DIM_KEY])
            ?: FoldHandleStyle.DEFAULT_ALPHA
        FoldHandleStyle.alpha(stored)
    }

    suspend fun setFoldHandleAlpha(alpha: Float) {
        dataStore.edit { it[FOLD_HANDLE_ALPHA_KEY] = FoldHandleStyle.alpha(alpha) }
    }

    /**
     * Whether the "hold for the other screen" hint has been shown. Once,
     * before the second entry on a device with a second screen: the first
     * press is for seeing what the mode is, and [overlayEnteredBefore] is
     * what says that press has happened.
     */
    val foldSwapHintSeen: Flow<Boolean> =
        dataStore.data.map { it[FOLD_SWAP_HINT_KEY] ?: false }

    suspend fun setFoldSwapHintSeen(seen: Boolean) {
        dataStore.edit { it[FOLD_SWAP_HINT_KEY] = seen }
    }

    /**
     * Whether Overlay Mode has been entered from the button at least once.
     * Written on a successful entry only, so a first press that ran into the
     * permission screen does not count as having seen the mode.
     */
    val overlayEnteredBefore: Flow<Boolean> =
        dataStore.data.map { it[OVERLAY_ENTERED_BEFORE_KEY] ?: false }

    suspend fun setOverlayEnteredBefore(entered: Boolean) {
        dataStore.edit { it[OVERLAY_ENTERED_BEFORE_KEY] = entered }
    }

    /**
     * Whether the one-time word about Overlay Mode has been said. Unlike the
     * swap hint, any dismissal records it: that one sits in the middle of a
     * press it has to answer, and this one interrupts nothing.
     */
    val foldOverlayHintSeen: Flow<Boolean> =
        dataStore.data.map { it[FOLD_OVERLAY_HINT_KEY] ?: false }

    suspend fun setFoldOverlayHintSeen(seen: Boolean) {
        dataStore.edit { it[FOLD_OVERLAY_HINT_KEY] = seen }
    }


    /**
     * How big the folded handle is in dp, which is also how big its tap
     * target is. Same legacy fallback as the opacity above.
     */
    val foldHandleSizeDp: Flow<Int> = dataStore.data.map { prefs ->
        val stored = prefs[FOLD_HANDLE_SIZE_DP_KEY]
            ?: FoldHandleStyle.sizeDpFromLegacyName(prefs[FOLD_TAB_SIZE_KEY])
            ?: FoldHandleStyle.DEFAULT_SIZE_DP
        FoldHandleStyle.sizeDp(stored)
    }

    suspend fun setFoldHandleSizeDp(dp: Int) {
        dataStore.edit { it[FOLD_HANDLE_SIZE_DP_KEY] = FoldHandleStyle.sizeDp(dp) }
    }

    /**
     * What fraction of its size the handle is drawn at while the panel is open.
     *
     * No legacy fallback, because there was never a setting: the handle shrank
     * to a hardcoded 0.62 and that was that. The default is 1f, so it now
     * stays the size it was chosen at unless somebody asks for less.
     */
    val foldHandleRecess: Flow<Float> = dataStore.data.map { prefs ->
        FoldHandleStyle.recess(prefs[FOLD_HANDLE_RECESS_KEY] ?: FoldHandleStyle.DEFAULT_RECESS)
    }

    suspend fun setFoldHandleRecess(scale: Float) {
        dataStore.edit { it[FOLD_HANDLE_RECESS_KEY] = FoldHandleStyle.recess(scale) }
    }

    /**
     * Whether the folded handle shows the time above it and the battery as a
     * ring around it. Off by default, so a handle nobody asked to change does
     * not grow.
     */
    val foldHandleClockBattery: Flow<Boolean> = dataStore.data.map {
        it[FOLD_HANDLE_CLOCK_BATTERY_KEY] ?: false
    }

    suspend fun setFoldHandleClockBattery(on: Boolean) {
        dataStore.edit { it[FOLD_HANDLE_CLOCK_BATTERY_KEY] = on }
    }

    /**
     * Whether the guide panel's bar is collapsed to its strip.
     *
     * Collapsing is manual and remembered, never automatic. This project has
     * already learned once that chrome which hides itself hides the control you
     * were reaching for, which is why `KEEP_TOP_BAR_VISIBLE` exists at all.
     */
    val foldPanelBarCollapsed: Flow<Boolean> =
        dataStore.data.map { it[FOLD_PANEL_BAR_COLLAPSED_KEY] ?: false }

    suspend fun setFoldPanelBarCollapsed(collapsed: Boolean) {
        dataStore.edit { it[FOLD_PANEL_BAR_COLLAPSED_KEY] = collapsed }
    }

    /** Whether the panel's first-open card has been dismissed. Once, ever. */
    val foldPanelIntroSeen: Flow<Boolean> =
        dataStore.data.map { it[FOLD_PANEL_INTRO_SEEN_KEY] ?: false }

    suspend fun setFoldPanelIntroSeen(seen: Boolean) {
        dataStore.edit { it[FOLD_PANEL_INTRO_SEEN_KEY] = seen }
    }

    /**
     * Which panel surface was last open, by enum name.
     *
     * Persisted rather than session state, because the session ends every time
     * the panel folds: opening the images, folding to look at the game and
     * unfolding put you back on the guide, and the surface you were actually
     * using had to be picked again every single time. Stored as a string so an
     * enum that gains or loses a case degrades to the default rather than
     * crashing on an index that no longer means what it meant.
     */
    val foldPanelSurface: Flow<String?> =
        dataStore.data.map { it[FOLD_PANEL_SURFACE_KEY] }

    suspend fun setFoldPanelSurface(name: String) {
        dataStore.edit { it[FOLD_PANEL_SURFACE_KEY] = name }
    }

    /**
     * How the panel's achievement list was last narrowed, sorted and scrolled.
     *
     * The app's own drawer keeps all of this in its view model on purpose - a
     * filter is about the question being asked right now, not a setting. The
     * panel cannot follow that rule, because its session ends every time the
     * user folds: the drawer's "right now" is a visit to a tab, and the panel's
     * is a glance between two turns of a game. Re-narrowing a 200-achievement
     * list on every unfold is not the same act as re-narrowing it on every
     * visit.
     *
     * Enums are stored by name, so a case added or removed later degrades to
     * the default rather than resolving to whatever now sits at that ordinal.
     */
    val foldPanelAwardSort: Flow<String?> =
        dataStore.data.map { it[FOLD_PANEL_AWARD_SORT_KEY] }

    val foldPanelAwardSortDirection: Flow<String?> =
        dataStore.data.map { it[FOLD_PANEL_AWARD_SORT_DIR_KEY] }

    val foldPanelAwardTypeFilter: Flow<String?> =
        dataStore.data.map { it[FOLD_PANEL_AWARD_TYPE_KEY] }

    val foldPanelAwardLockedOnly: Flow<Boolean> =
        dataStore.data.map { it[FOLD_PANEL_AWARD_LOCKED_KEY] ?: false }

    /** Row and pixel offset, the pair `LazyListState` restores from. */
    val foldPanelAwardScroll: Flow<Pair<Int, Int>> =
        dataStore.data.map {
            (it[FOLD_PANEL_AWARD_SCROLL_INDEX_KEY] ?: 0) to
                (it[FOLD_PANEL_AWARD_SCROLL_OFFSET_KEY] ?: 0)
        }

    suspend fun setFoldPanelAwardSort(field: String, direction: String) {
        dataStore.edit {
            it[FOLD_PANEL_AWARD_SORT_KEY] = field
            it[FOLD_PANEL_AWARD_SORT_DIR_KEY] = direction
        }
    }

    suspend fun setFoldPanelAwardTypeFilter(name: String) {
        dataStore.edit { it[FOLD_PANEL_AWARD_TYPE_KEY] = name }
    }

    suspend fun setFoldPanelAwardLockedOnly(lockedOnly: Boolean) {
        dataStore.edit { it[FOLD_PANEL_AWARD_LOCKED_KEY] = lockedOnly }
    }

    suspend fun setFoldPanelAwardScroll(index: Int, offset: Int) {
        dataStore.edit {
            it[FOLD_PANEL_AWARD_SCROLL_INDEX_KEY] = index
            it[FOLD_PANEL_AWARD_SCROLL_OFFSET_KEY] = offset
        }
    }

    /**
     * Pins the vault-session top bar so the per-tab auto-hide cannot take it
     * away. The fold button lives in that bar, and a fold button you cannot
     * reach while reading a guide is a fold button you will not use.
     */
    val keepTopBarVisible: Flow<Boolean> =
        dataStore.data.map { it[KEEP_TOP_BAR_VISIBLE_KEY] ?: false }

    suspend fun setKeepTopBarVisible(visible: Boolean) {
        dataStore.edit { it[KEEP_TOP_BAR_VISIBLE_KEY] = visible }
    }

    /**
     * The vault of the game most recently known to be playing. Written by every
     * successful detection resolution and by app-initiated launches (which never
     * touch the emulator frontend's log). Read by the open-last-played flow when
     * the tracked log has nothing new to say. Null when nothing was ever played.
     */
    val nowPlayingVaultId: Flow<String?> = dataStore.data.map { preferences ->
        preferences[NOW_PLAYING_VAULT_ID_KEY]
    }

    suspend fun setNowPlayingVaultId(vaultId: String?) {
        dataStore.edit { preferences ->
            if (vaultId != null) preferences[NOW_PLAYING_VAULT_ID_KEY] = vaultId
            else preferences.remove(NOW_PLAYING_VAULT_ID_KEY)
        }
    }

    /**
     * The last tracked-file launch marker the open-last-played flow handled.
     * Persisted (not in-memory) so a cold start can tell "nothing new since last
     * handled" and reopen the recorded game instead of re-running detection.
     */
    val lastHandledLaunchMarker: Flow<String?> = dataStore.data.map { preferences ->
        preferences[LAST_HANDLED_LAUNCH_MARKER_KEY]
    }

    suspend fun setLastHandledLaunchMarker(marker: String?) {
        dataStore.edit { preferences ->
            if (marker != null) preferences[LAST_HANDLED_LAUNCH_MARKER_KEY] = marker
            else preferences.remove(LAST_HANDLED_LAUNCH_MARKER_KEY)
        }
    }

    /**
     * Get last played source.
     */
    val lastPlayedSource: Flow<LastPlayedSource> = dataStore.data.map { preferences ->
        val value = preferences[LAST_PLAYED_SOURCE_KEY] ?: LastPlayedSource.NOT_CONFIGURED.name
        // Migrate removed enum values to ACHIEVEMENTS
        val migrated = when (value) {
            "RETRO_ACHIEVEMENTS", "STEAM" -> "ACHIEVEMENTS"
            else -> value
        }
        try {
            LastPlayedSource.valueOf(migrated)
        } catch (e: IllegalArgumentException) {
            LastPlayedSource.NOT_CONFIGURED
        }
    }

    /**
     * Set last played source.
     */
    suspend fun setLastPlayedSource(source: LastPlayedSource) {
        dataStore.edit { preferences ->
            preferences[LAST_PLAYED_SOURCE_KEY] = source.name
        }
    }

    /**
     * Save [source] as the user's chosen method, along with the auto-refresh
     * default that goes with it. Auto-refresh applies only to ES-DE and Cocoon
     * (RetroArch is excluded — it's refresh-button-
     * only, like Achievements), so the toggle is turned on for those and off for
     * the rest, matching the prior UX where picking ES-DE / Cocoon turned
     * auto-detect on for the user.
     */
    suspend fun selectLastPlayedSource(source: LastPlayedSource) {
        setRetroArchAutoRefresh(source == LastPlayedSource.ES_DE || source == LastPlayedSource.COCOON)
        setLastPlayedSource(source)
    }

    /**
     * Get ES-DE folder URI (for last played detection when source is ES-DE).
     */
    val esdeFolderUri: Flow<String?> = dataStore.data.map { preferences ->
        preferences[ESDE_FOLDER_URI_KEY]
    }

    /**
     * Set ES-DE folder URI.
     */
    suspend fun setEsdeFolderUri(uri: String?) {
        dataStore.edit { preferences ->
            if (uri != null) {
                preferences[ESDE_FOLDER_URI_KEY] = uri
            } else {
                preferences.remove(ESDE_FOLDER_URI_KEY)
            }
        }
    }

    /**
     * Get ES-DE folder display path (for Settings UI).
     */
    val esdeFolderPath: Flow<String?> = dataStore.data.map { preferences ->
        preferences[ESDE_FOLDER_PATH_KEY]
    }

    /**
     * Set ES-DE folder display path.
     */
    suspend fun setEsdeFolderPath(path: String?) {
        dataStore.edit { preferences ->
            if (path != null) {
                preferences[ESDE_FOLDER_PATH_KEY] = path
            } else {
                preferences.remove(ESDE_FOLDER_PATH_KEY)
            }
        }
    }

    /**
     * Get Cocoon folder URI (for last played detection when source is Cocoon).
     */
    val cocoonFolderUri: Flow<String?> = dataStore.data.map { preferences ->
        preferences[COCOON_FOLDER_URI_KEY]
    }

    /**
     * Set Cocoon folder URI.
     */
    suspend fun setCocoonFolderUri(uri: String?) {
        dataStore.edit { preferences ->
            if (uri != null) {
                preferences[COCOON_FOLDER_URI_KEY] = uri
            } else {
                preferences.remove(COCOON_FOLDER_URI_KEY)
            }
        }
    }

    /**
     * Get Cocoon folder display path (for Settings UI).
     */
    val cocoonFolderPath: Flow<String?> = dataStore.data.map { preferences ->
        preferences[COCOON_FOLDER_PATH_KEY]
    }

    /**
     * Set Cocoon folder display path.
     */
    suspend fun setCocoonFolderPath(path: String?) {
        dataStore.edit { preferences ->
            if (path != null) {
                preferences[COCOON_FOLDER_PATH_KEY] = path
            } else {
                preferences.remove(COCOON_FOLDER_PATH_KEY)
            }
        }
    }

    /**
     * Whether the one-time "show it in Cocoon too" hint (offered when applying
     * an icon while Cocoon is the detection source) has already been shown.
     * Gates [shouldShowCocoonIconPackHint] so the hint interrupts "Apply and
     * close" at most once.
     */
    val cocoonIconPackHintShown: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[COCOON_ICON_PACK_HINT_SHOWN_KEY] ?: false
    }

    /**
     * Record that the Cocoon icon pack hint has been shown, so it never shows again.
     */
    suspend fun setCocoonIconPackHintShown() {
        dataStore.edit { preferences ->
            preferences[COCOON_ICON_PACK_HINT_SHOWN_KEY] = true
        }
    }

    /**
     * Get ROMs folder URI (for granting URI permission to emulators at launch time).
     */
    val romsFolderUri: Flow<String?> = dataStore.data.map { preferences ->
        preferences[ROMS_FOLDER_URI_KEY]
    }

    /**
     * Set ROMs folder URI.
     */
    suspend fun setRomsFolderUri(uri: String?) {
        dataStore.edit { preferences ->
            if (uri != null) {
                preferences[ROMS_FOLDER_URI_KEY] = uri
            } else {
                preferences.remove(ROMS_FOLDER_URI_KEY)
            }
        }
    }

    /**
     * Get focus color setting
     */
    val focusColor: Flow<FocusColor> = dataStore.data.map { preferences ->
        val colorValue = preferences[FOCUS_COLOR_KEY] ?: FocusColor.CRIMSON.name
        try {
            FocusColor.valueOf(colorValue)
        } catch (e: IllegalArgumentException) {
            FocusColor.CRIMSON // Default fallback
        }
    }
    
    /**
     * Set focus color
     */
    suspend fun setFocusColor(color: FocusColor) {
        dataStore.edit { preferences ->
            preferences[FOCUS_COLOR_KEY] = color.name
        }
    }

    /**
     * Raw accent color (readability-adjusted ARGB) persisted by Library when
     * auto-focus-color-detection is on.  Null when detection is off or no
     * vault has been focused yet.
     */
    val rawAccentColorArgb: Flow<Int?> = dataStore.data.map { preferences ->
        preferences[RAW_ACCENT_COLOR_ARGB_KEY]
    }

    suspend fun setRawAccentColorArgb(argb: Int?) {
        dataStore.edit { preferences ->
            if (argb != null) {
                preferences[RAW_ACCENT_COLOR_ARGB_KEY] = argb
            } else {
                preferences.remove(RAW_ACCENT_COLOR_ARGB_KEY)
            }
        }
    }

    /**
     * The accent pair from ONE subscription, so a collector can never assemble the quantized
     * colour of one commit with the raw colour of another.  Combining [focusColor] and
     * [rawAccentColorArgb] separately can — each is its own collector on the DataStore, so the
     * pairing at the combine site briefly mixes commits.
     */
    val accentColors: Flow<Pair<FocusColor, Int?>> = dataStore.data.map { preferences ->
        val colorValue = preferences[FOCUS_COLOR_KEY] ?: FocusColor.CRIMSON.name
        val quantized = try {
            FocusColor.valueOf(colorValue)
        } catch (e: IllegalArgumentException) {
            FocusColor.CRIMSON
        }
        quantized to preferences[RAW_ACCENT_COLOR_ARGB_KEY]
    }

    /**
     * [effectiveAccentColor] for the settings as they are now: the one colour
     * to drive the LED with from a coroutine that has no composition to read
     * the cached accent from.
     */
    suspend fun currentAccentColor(): Color {
        val (quantized, rawArgb) = accentColors.first()
        return effectiveAccentColor(quantized, rawArgb, autoFocusColorDetection.first())
    }

    /**
     * Persist a detected accent — the quantized [FocusColor] plus the exact raw colour — as a
     * single transaction.  Written as two edits, the intermediate commit pairs the new quantized
     * colour with the PREVIOUS raw colour, and every accent surface flashes the old colour for a
     * few frames when the collector in MainActivity repaints from it.
     */
    suspend fun setDetectedAccent(color: FocusColor, rawArgb: Int) {
        dataStore.edit { preferences ->
            preferences[FOCUS_COLOR_KEY] = color.name
            preferences[RAW_ACCENT_COLOR_ARGB_KEY] = rawArgb
        }
    }

    /**
     * Get grid display ratio setting
     */
    val gridDisplayRatio: Flow<GridDisplayRatio> = dataStore.data.map { preferences ->
        val ratioValue = preferences[GRID_DISPLAY_RATIO_KEY] ?: GridDisplayRatio.RATIO_2_3.name
        try {
            GridDisplayRatio.valueOf(ratioValue)
        } catch (e: IllegalArgumentException) {
            GridDisplayRatio.RATIO_2_3 // Default fallback
        }
    }
    
    /**
     * Set grid display ratio
     */
    suspend fun setGridDisplayRatio(ratio: GridDisplayRatio) {
        dataStore.edit { preferences ->
            preferences[GRID_DISPLAY_RATIO_KEY] = ratio.name
        }
    }
    
    val gridRatioExplicitlySet: Flow<Boolean> = dataStore.data
        .map { it[GRID_RATIO_EXPLICITLY_SET_KEY] ?: false }
        .distinctUntilChanged()

    suspend fun setGridRatioExplicitlySet(value: Boolean) {
        dataStore.edit { preferences ->
            preferences[GRID_RATIO_EXPLICITLY_SET_KEY] = value
        }
    }

    // --- User-entered API keys, held in the encrypted secrets store ---

    /**
     * These values lived in this DataStore as plaintext until 1.0.x. Every accessor moves any
     * remaining plaintext copies into [AppSecretsStore] before touching the secrets, so an
     * upgraded install keeps its keys and loses the unencrypted copies on first use.
     */
    private val secretsMigrationMutex = Mutex()
    @Volatile
    private var secretsMigrated = false

    private suspend fun migrateSecretsIfNeeded() {
        if (secretsMigrated) return
        secretsMigrationMutex.withLock {
            if (secretsMigrated) return
            val prefs = dataStore.data.first()
            val moves = listOf(
                GEMINI_API_KEY to AppSecret.GeminiApiKey,
                STEAM_API_KEY to AppSecret.SteamApiKey,
                STEAMGRID_API_KEY to AppSecret.SteamGridApiKey,
                THEGAMESDB_API_KEY to AppSecret.TheGamesDbApiKey,
            ).filter { (plaintextKey, _) -> prefs.contains(plaintextKey) }
            for ((plaintextKey, secret) in moves) {
                secrets.set(secret, prefs[plaintextKey])
            }
            if (moves.isNotEmpty()) {
                dataStore.edit { editable ->
                    moves.forEach { (plaintextKey, _) -> editable.remove(plaintextKey) }
                }
            }
            secretsMigrated = true
        }
    }

    private fun secretFlow(secret: AppSecret): Flow<String?> = flow {
        migrateSecretsIfNeeded()
        emitAll(secrets.flow(secret))
    }

    /**
     * Get SteamGridDB API key
     * Returns null if not set (will use default in client)
     */
    val steamGridApiKey: Flow<String?> = secretFlow(AppSecret.SteamGridApiKey)

    /**
     * Set SteamGridDB API key
     */
    suspend fun setSteamGridApiKey(apiKey: String?) {
        migrateSecretsIfNeeded()
        secrets.set(AppSecret.SteamGridApiKey, apiKey)
    }

    /**
     * Steam Web API key override (BYOK). Empty string when not set.
     */
    val steamApiKeyOverride: Flow<String> = secretFlow(AppSecret.SteamApiKey).map { it ?: "" }

    suspend fun setSteamApiKeyOverride(value: String) {
        migrateSecretsIfNeeded()
        secrets.set(AppSecret.SteamApiKey, value)
    }

    /**
     * TheGamesDB API key override (BYOK). Empty string when not set.
     */
    val theGamesDbApiKeyOverride: Flow<String> = secretFlow(AppSecret.TheGamesDbApiKey).map { it ?: "" }

    suspend fun setTheGamesDbApiKeyOverride(value: String) {
        migrateSecretsIfNeeded()
        secrets.set(AppSecret.TheGamesDbApiKey, value)
    }
    
    /**
     * Get default asset type for scraping
     */
    val defaultAssetType: Flow<DefaultAssetType> = dataStore.data.map { preferences ->
        val typeValue = preferences[DEFAULT_ASSET_TYPE_KEY] ?: DefaultAssetType.GRID.name
        try {
            DefaultAssetType.valueOf(typeValue)
        } catch (e: IllegalArgumentException) {
            DefaultAssetType.GRID // Default fallback
        }
    }
    
    /**
     * Set default asset type for scraping
     */
    suspend fun setDefaultAssetType(type: DefaultAssetType) {
        dataStore.edit { preferences ->
            preferences[DEFAULT_ASSET_TYPE_KEY] = type.name
        }
    }
    
    /**
     * Get power saver mode setting
     */
    val powerSaverMode: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[POWER_SAVER_MODE_KEY] ?: false // Default: disabled
    }
    
    /**
     * Set power saver mode
     */
    /**
     * Whether the starter set of pinned apps has been laid down.
     *
     * A flag rather than "is the pin list empty", because an empty list is a legitimate choice:
     * someone who unpins everything must not have the defaults handed back on the next launch.
     */
    val defaultPinsSeeded: Flow<Boolean> =
        dataStore.data.map { it[DEFAULT_PINS_SEEDED_KEY] ?: false }

    suspend fun setDefaultPinsSeeded(seeded: Boolean) {
        dataStore.edit { it[DEFAULT_PINS_SEEDED_KEY] = seeded }
    }

    /**
     * SAF tree the user picked for custom system logo overrides, holding `<system>.{svg,png,webp,
     * jpg,jpeg}` named by ES-DE system short name. Null when unset. Consumed by the Game Stats
     * screen ([com.twinlore.app.ui.screens.stats.GameStatsViewModel.ensureSystemLogo]) so a
     * user's own logos take precedence over downloaded ones there.
     */
    val esdeCustomLogoFolder: Flow<String?> = dataStore.data.map { it[ESDE_LOGO_FOLDER_KEY] }

    /**
     * [com.twinlore.app.data.ambience.AmbienceAnimation] names Random must not pick, app-wide (every
     * ambience host draws from the same pool). Empty means Random may pick anything.
     */
    val randomAnimationExcluded: Flow<Set<String>> = dataStore.data.map { preferences ->
        preferences[RANDOM_ANIMATION_EXCLUDED_KEY] ?: emptySet()
    }

    suspend fun setPowerSaverMode(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[POWER_SAVER_MODE_KEY] = enabled
        }
    }
    
    /**
     * Get auto focus color detection setting
     */
    val autoFocusColorDetection: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[AUTO_FOCUS_COLOR_DETECTION_KEY] ?: true // Default: enabled
    }
    
    /**
     * Set auto focus color detection
     */
    suspend fun setAutoFocusColorDetection(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[AUTO_FOCUS_COLOR_DETECTION_KEY] = enabled
        }
    }
    
    /**
     * Get haptic feedback intensity setting
     */
    val hapticFeedbackIntensity: Flow<HapticFeedbackIntensity> = dataStore.data.map { preferences ->
        val intensityValue = preferences[HAPTIC_FEEDBACK_INTENSITY_KEY] ?: HapticFeedbackIntensity.MEDIUM.name
        try {
            HapticFeedbackIntensity.valueOf(intensityValue)
        } catch (e: IllegalArgumentException) {
            HapticFeedbackIntensity.MEDIUM // Default fallback
        }
    }
    
    /**
     * Set haptic feedback intensity
     */
    suspend fun setHapticFeedbackIntensity(intensity: HapticFeedbackIntensity) {
        dataStore.edit { preferences ->
            preferences[HAPTIC_FEEDBACK_INTENSITY_KEY] = intensity.name
        }
    }

    /** Face-button glyph style for the on-screen controller hints (Xbox letters vs PS shapes). */
    val buttonGlyphStyle: Flow<ButtonGlyphStyle> = dataStore.data.map { preferences ->
        try {
            ButtonGlyphStyle.valueOf(preferences[BUTTON_GLYPH_STYLE_KEY] ?: ButtonGlyphStyle.XBOX.name)
        } catch (e: IllegalArgumentException) {
            ButtonGlyphStyle.XBOX
        }
    }

    suspend fun setButtonGlyphStyle(style: ButtonGlyphStyle) {
        dataStore.edit { preferences ->
            preferences[BUTTON_GLYPH_STYLE_KEY] = style.name
        }
    }

    /** Swap A↔B and X↔Y at input (Nintendo-style controllers) + flip the on-screen hints. */
    val swapFaceButtons: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[SWAP_FACE_BUTTONS_KEY] ?: false // Default: standard Xbox layout
    }

    suspend fun setSwapFaceButtons(swap: Boolean) {
        dataStore.edit { preferences ->
            preferences[SWAP_FACE_BUTTONS_KEY] = swap
        }
    }

    /**
     * Get focus sound enabled setting
     */
    val focusSoundEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[FOCUS_SOUND_ENABLED_KEY] ?: true // Default: enabled
    }
    
    /**
     * Set focus sound enabled
     */
    suspend fun setFocusSoundEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[FOCUS_SOUND_ENABLED_KEY] = enabled
        }
    }

    /**
     * Get vault storage folder URI
     * Returns null if not set (will use default app storage)
     */
    val vaultStorageFolderUri: Flow<String?> = dataStore.data.map { preferences ->
        preferences[VAULT_STORAGE_FOLDER_URI_KEY]
    }.distinctUntilChanged()
    
    // ---- Achievement Journey: how a replay looks ----
    //
    // Presentation only. Every one of these restyles the chart in place - none of them changes
    // what was fetched or invalidates a cached journey - which is exactly why they can be
    // remembered between visits without anything having to be reloaded (user request). Each is
    // read once when the journey's ViewModel starts and written whenever the player changes it.

    val journeySpeed: Flow<Float> = dataStore.data.map { it[JOURNEY_SPEED_KEY] ?: 1f }
        .distinctUntilChanged()

    suspend fun setJourneySpeed(speed: Float) {
        dataStore.edit { it[JOURNEY_SPEED_KEY] = speed }
    }

    /** Null until the user has chosen: the player then follows the chart's own default. */
    val journeyFollowMode: Flow<Boolean?> = dataStore.data.map { it[JOURNEY_FOLLOW_MODE_KEY] }
        .distinctUntilChanged()

    suspend fun setJourneyFollowMode(on: Boolean) {
        dataStore.edit { it[JOURNEY_FOLLOW_MODE_KEY] = on }
    }

    /** The ambience animation drawn behind the chart, by enum name; unknown names fall back. */
    val journeyAmbience: Flow<String?> = dataStore.data.map { it[JOURNEY_AMBIENCE_KEY] }
        .distinctUntilChanged()

    suspend fun setJourneyAmbience(name: String) {
        dataStore.edit { it[JOURNEY_AMBIENCE_KEY] = name }
    }

    /** The signed-in player's own curve colour; -1 is the theme accent. */
    val journeyPlayerColor: Flow<Int> = dataStore.data.map { it[JOURNEY_PLAYER_COLOR_KEY] ?: -1 }
        .distinctUntilChanged()

    suspend fun setJourneyPlayerColor(index: Int) {
        dataStore.edit { it[JOURNEY_PLAYER_COLOR_KEY] = index }
    }

    val journeyRivalColor: Flow<Int> = dataStore.data.map { it[JOURNEY_RIVAL_COLOR_KEY] ?: 0 }
        .distinctUntilChanged()

    suspend fun setJourneyRivalColor(index: Int) {
        dataStore.edit { it[JOURNEY_RIVAL_COLOR_KEY] = index }
    }

    /** Which RetroAchievements art the stage rests on, by enum name; unknown names fall back. */
    val journeyBackground: Flow<String?> = dataStore.data.map { it[JOURNEY_BACKGROUND_KEY] }
        .distinctUntilChanged()

    suspend fun setJourneyBackground(name: String) {
        dataStore.edit { it[JOURNEY_BACKGROUND_KEY] = name }
    }

    /**
     * How strong the dimmed backdrop art reads behind the chart, as a percent (art alpha = this /
     * 100f); null until the user has ever touched the control. See [com.twinlore.app.journey.ui
     * .JourneyBackdrop]'s `dimAlpha` and the export pipeline's `backdropDim`, which both unify on
     * this one stored value.
     */
    val journeyBackdropDim: Flow<Int?> = dataStore.data.map { it[JOURNEY_BACKDROP_DIM_KEY] }
        .distinctUntilChanged()

    suspend fun setJourneyBackdropDim(value: Int) {
        dataStore.edit { it[JOURNEY_BACKDROP_DIM_KEY] = value }
    }

    /** Whether a player's face carries a sign while their line is flat; on by default. */
    val journeyIdleSigns: Flow<Boolean> = dataStore.data.map { it[JOURNEY_IDLE_SIGNS_KEY] ?: true }
        .distinctUntilChanged()

    suspend fun setJourneyIdleSigns(on: Boolean) {
        dataStore.edit { it[JOURNEY_IDLE_SIGNS_KEY] = on }
    }

    /**
     * Home folder URI for the library. Replaces vaultStorageFolderUri
     * once that refactor lands. Null until the user picks a folder at first launch.
     */
    val homeFolderUri: Flow<String?> = dataStore.data.map { it[HOME_FOLDER_URI_KEY] }.distinctUntilChanged()

    suspend fun setHomeFolderUri(uri: String?) {
        dataStore.edit { preferences ->
            if (uri != null) preferences[HOME_FOLDER_URI_KEY] = uri
            else preferences.remove(HOME_FOLDER_URI_KEY)
        }
    }

    /**
     * Sticky flag: true once this install has confirmed there is no 0.7.x
     * legacy filesystem data to migrate (either because none ever existed, or
     * because the migration finished cleanly). When true, evaluateStartup
     * short-circuits and skips ~400 ms of internalize/scan/listFiles on every
     * cold start.
     *
     * Per-migration boolean, named after the migration. Matches the existing
     * `license_migration_v2` precedent in this repo. If a future release ships
     * another one-off filesystem migration, add a new sibling key (e.g.
     * `legacy_data_v3_completed`) rather than retrofitting a version counter.
     */
    val legacyMigrationCompleted: Flow<Boolean> = dataStore.data
        .map { it[LEGACY_MIGRATION_COMPLETED_KEY] ?: false }
        .distinctUntilChanged()

    suspend fun setLegacyMigrationCompleted(done: Boolean) {
        dataStore.edit { preferences -> preferences[LEGACY_MIGRATION_COMPLETED_KEY] = done }
    }

    /**
     * True once the user has said "Not now" to the All-Files access prompt. Sticky on
     * purpose: the prompt is a one-time offer, not a recurring nag.
     */
    val allFilesPromptDeclined: Flow<Boolean> = dataStore.data
        .map { it[ALL_FILES_PROMPT_DECLINED_KEY] ?: false }
        .distinctUntilChanged()

    suspend fun setAllFilesPromptDeclined(declined: Boolean) {
        dataStore.edit { preferences -> preferences[ALL_FILES_PROMPT_DECLINED_KEY] = declined }
    }

    /**
     * Stable UUID identifying this install. Generated lazily on first read,
     * persists across launches (but not across uninstalls). Used by the library
     * snapshot's metadata row to identify the device that last wrote it.
     */
    val deviceId: Flow<String> = dataStore.data.map { prefs ->
        prefs[DEVICE_ID_KEY] ?: ""
    }.distinctUntilChanged()

    /**
     * Read the current deviceId; generate-and-persist a random UUID if none yet.
     * Deliberately NOT ANDROID_ID: reading it for the snapshot id is § 25 TDDG
     * terminal-equipment access without a consent exemption (privacy policy §5).
     * The UUID is in the Auto Backup set, so most reinstalls restore it and keep
     * the snapshot filename; without a restore, the old snapshot merges back in
     * as a foreign snapshot via the normal sync scan.
     * Suspend because the generation needs a DataStore edit.
     */
    suspend fun getOrCreateDeviceId(): String {
        var result = ""
        dataStore.edit { prefs ->
            val existing = prefs[DEVICE_ID_KEY]
            if (!existing.isNullOrBlank()) {
                result = existing
            } else {
                val newId = java.util.UUID.randomUUID().toString()
                prefs[DEVICE_ID_KEY] = newId
                result = newId
            }
        }
        return result
    }

    /**
     * Overwrite the stored device id. Called by [com.twinlore.app.data.library.DeviceIdMigrator]
     * after renaming a legacy ANDROID_ID-named snapshot to its new UUID name.
     */
    suspend fun overwriteDeviceId(newId: String) {
        dataStore.edit { it[DEVICE_ID_KEY] = newId }
    }

    /**
     * Search engine (fixed to Google).
     */
    val searchEngine: Flow<SearchEngine> = flowOf(SearchEngine.GOOGLE)

    /**
     * Get landing entries (10 slots). Uses defaults when a key is missing.
     */
    val landingEntries: Flow<List<LandingEntry>> = dataStore.data.map { preferences ->
        val defaults = defaultLandingEntries()
        LandingSection.ordered.mapIndexed { index, _ ->
            val def = defaults.getOrNull(index) ?: LandingEntry("", "", false)
            LandingEntry(
                label = preferences[landingLabelKey(index)] ?: def.label,
                appendWord = preferences[landingAppendKey(index)] ?: def.appendWord,
                useImageSearch = preferences[landingImageKey(index)] ?: def.useImageSearch,
                site = preferences[landingSiteKey(index)] ?: def.site,
                fileType = preferences[landingFiletypeKey(index)] ?: def.fileType
            )
        }
    }

    /**
     * Set one landing entry by slot index (0..9).
     */
    suspend fun setLandingEntry(slotIndex: Int, entry: LandingEntry) {
        if (slotIndex !in 0..9) return
        dataStore.edit { preferences ->
            preferences[landingLabelKey(slotIndex)] = entry.label
            preferences[landingAppendKey(slotIndex)] = entry.appendWord
            preferences[landingImageKey(slotIndex)] = entry.useImageSearch
            preferences[landingSiteKey(slotIndex)] = entry.site
            preferences[landingFiletypeKey(slotIndex)] = entry.fileType
        }
    }

    /**
     * Get LED Control enabled (analog stick rings)
     */
    val ledEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[LED_ENABLED_KEY] ?: false
    }

    /**
     * Set LED Control enabled
     */
    suspend fun setLedEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[LED_ENABLED_KEY] = enabled
        }
    }

    /**
     * Get LED brightness (0-255). Default 255 (full) so the LED shows the focus colour's own
     * brightness untouched until the user dials it down in Look & Feel.
     */
    val ledBrightness: Flow<Int> = dataStore.data.map { preferences ->
        preferences[LED_BRIGHTNESS_KEY] ?: 255
    }

    /**
     * Set LED brightness
     */
    suspend fun setLedBrightness(brightness: Int) {
        dataStore.edit { preferences ->
            preferences[LED_BRIGHTNESS_KEY] = brightness.coerceIn(0, 255)
        }
    }

    /**
     * Get LED saturation boost (0f-1f). Default 0 (no boost) so the LED shows the focus colour's
     * own saturation untouched until the user raises it in Look & Feel.
     */
    val ledSaturationBoost: Flow<Float> = dataStore.data.map { preferences ->
        preferences[LED_SATURATION_BOOST_KEY] ?: 0f
    }

    /**
     * Set LED saturation boost
     */
    suspend fun setLedSaturationBoost(boost: Float) {
        dataStore.edit { preferences ->
            preferences[LED_SATURATION_BOOST_KEY] = boost.coerceIn(0f, 1f)
        }
    }

    /**
     * Get LED turn-off time (inactivity timeout). Default: 15 sec.
     */
    val ledTurnOffTime: Flow<LedTurnOffTime> = dataStore.data.map { preferences ->
        val value = preferences[LED_TURN_OFF_KEY] ?: LedTurnOffTime.SEC_15.name
        try {
            LedTurnOffTime.valueOf(value)
        } catch (_: IllegalArgumentException) {
            LedTurnOffTime.SEC_15
        }
    }

    /**
     * Get whether to show library item (vault) names in the vault library view.
     * Default: true (titles shown) — a library of box art alone reads as a wall of
     * pictures, so the shipped look names every game until the user turns it off.
     */
    val showLibraryItemNames: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[SHOW_LIBRARY_ITEM_NAMES_KEY] ?: true
    }

    /**
     * Set whether to show library item names in the vault library view.
     */
    suspend fun setShowLibraryItemNames(show: Boolean) {
        dataStore.edit { preferences ->
            preferences[SHOW_LIBRARY_ITEM_NAMES_KEY] = show
        }
    }

    /**
     * Whether the bottom controller hint bar is visible in the library view.
     * Default: true (existing users see the hint bar).
     */
    val libraryHintBarVisible: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[LIBRARY_HINT_BAR_VISIBLE_KEY] ?: true
    }

    suspend fun setLibraryHintBarVisible(visible: Boolean) {
        dataStore.edit { preferences ->
            preferences[LIBRARY_HINT_BAR_VISIBLE_KEY] = visible
        }
    }

    /**
     * RetroAchievements feature enabled (credentials validated successfully).
     * Set true after successful validation in Settings; set false when credentials are cleared.
     */
    val retroAchievementsFeatureEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[RA_FEATURE_ENABLED_KEY] ?: false
    }

    suspend fun setRetroAchievementsFeatureEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[RA_FEATURE_ENABLED_KEY] = enabled
        }
    }

    /**
     * Library view mode: carousel or grid (extra large / medium / small).
     * Legacy "GRID" is mapped using stored column count; once user picks from menu we persist new enum.
     */
    val libraryViewMode: Flow<LibraryViewMode> = dataStore.data.map { preferences ->
        val value = preferences[LIBRARY_VIEW_MODE_KEY] ?: LibraryViewMode.GRID_EXTRA_LARGE.name
        when (value) {
            "GRID" -> {
                val cols = (preferences[LIBRARY_GRID_COLUMNS_KEY] ?: 4).coerceIn(3, 9)
                when (cols) {
                    3 -> LibraryViewMode.GRID_EXTRA_LARGE
                    5 -> LibraryViewMode.GRID_MEDIUM
                    9 -> LibraryViewMode.GRID_SMALL
                    6 -> LibraryViewMode.GRID_SMALL
                    else -> LibraryViewMode.GRID_MEDIUM
                }
            }
            else -> try {
                LibraryViewMode.valueOf(value)
            } catch (e: IllegalArgumentException) {
                LibraryViewMode.GRID_EXTRA_LARGE
            }
        }
    }

    suspend fun setLibraryViewMode(mode: LibraryViewMode) {
        dataStore.edit { preferences ->
            preferences[LIBRARY_VIEW_MODE_KEY] = mode.name
        }
    }

    /**
     * The library's full-screen backdrop (round two, task 2). Treats a background as a choice
     * rather than something unconditional. Defaults to [LibraryBackgroundStyle.BLURRED_ART]
     * (it had moved to SOLID for being the cheapest backdrop, but
     * the per-game blurred art is the look the library is meant to open with). Anyone who has
     * already chosen a style keeps it; only installs that never touched the setting move.
     */
    val libraryBackgroundStyle: Flow<LibraryBackgroundStyle> = dataStore.data.map { preferences ->
        val value = preferences[LIBRARY_BACKGROUND_STYLE_KEY] ?: LibraryBackgroundStyle.BLURRED_ART.name
        try {
            LibraryBackgroundStyle.valueOf(value)
        } catch (_: IllegalArgumentException) {
            LibraryBackgroundStyle.BLURRED_ART
        }
    }

    /**
     * Dim and blur for [LibraryBackgroundStyle.WALLPAPER], 0f-1f each.
     *
     * The wallpaper's OWN treatment, deliberately separate from anything the blurred-art style
     * does: that style blurs per-game cover art because a cover is not a backdrop, whereas a
     * wallpaper was chosen to be looked at. Defaults dim it by half so grid text stays readable
     * and leave it unblurred, since blurring a picture the user picked on purpose is a choice
     * rather than a correction.
     */
    val libraryWallpaperDim: Flow<Float> = dataStore.data.map { preferences ->
        preferences[LIBRARY_WALLPAPER_DIM_KEY] ?: 0.5f
    }

    val libraryWallpaperBlur: Flow<Float> = dataStore.data.map { preferences ->
        preferences[LIBRARY_WALLPAPER_BLUR_KEY] ?: 0f
    }

    suspend fun setLibraryWallpaperDim(dim: Float) {
        dataStore.edit { preferences ->
            preferences[LIBRARY_WALLPAPER_DIM_KEY] = dim.coerceIn(0f, 1f)
        }
    }

    suspend fun setLibraryWallpaperBlur(blur: Float) {
        dataStore.edit { preferences ->
            preferences[LIBRARY_WALLPAPER_BLUR_KEY] = blur.coerceIn(0f, 1f)
        }
    }

    suspend fun setLibraryBackgroundStyle(style: LibraryBackgroundStyle) {
        dataStore.edit { preferences ->
            preferences[LIBRARY_BACKGROUND_STYLE_KEY] = style.name
        }
    }

    /** Library sort field (Name / Date added / Last opened / Achievements). Default Date added. */
    val librarySortField: Flow<LibrarySortField> = dataStore.data.map { prefs ->
        try { LibrarySortField.valueOf(prefs[LIBRARY_SORT_FIELD_KEY] ?: LibrarySortField.DATE_ADDED.name) }
        catch (_: IllegalArgumentException) { LibrarySortField.DATE_ADDED }
    }

    suspend fun setLibrarySortField(field: LibrarySortField) {
        dataStore.edit { it[LIBRARY_SORT_FIELD_KEY] = field.name }
    }

    /** Library sort direction. Default DESC (newest / highest first). */
    val librarySortDirection: Flow<SortDirection> = dataStore.data.map { prefs ->
        try { SortDirection.valueOf(prefs[LIBRARY_SORT_DIRECTION_KEY] ?: SortDirection.DESC.name) }
        catch (_: IllegalArgumentException) { SortDirection.DESC }
    }

    suspend fun setLibrarySortDirection(dir: SortDirection) {
        dataStore.edit { it[LIBRARY_SORT_DIRECTION_KEY] = dir.name }
    }

    /** Whether to show the achievement progress bar + frame on grid items during achievement sort. Default true. */
    val libraryAchievementProgress: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[LIBRARY_ACHIEVEMENT_PROGRESS_KEY] ?: true
    }

    suspend fun setLibraryAchievementProgress(enabled: Boolean) {
        dataStore.edit { it[LIBRARY_ACHIEVEMENT_PROGRESS_KEY] = enabled }
    }

    // --- File picker preferences ---

    val guidePickerViewMode: Flow<FilePickerViewMode> = dataStore.data.map { prefs ->
        try { FilePickerViewMode.valueOf(prefs[GUIDE_PICKER_VIEW_MODE_KEY] ?: FilePickerViewMode.LIST.name) }
        catch (_: IllegalArgumentException) { FilePickerViewMode.LIST }
    }

    suspend fun setGuidePickerViewMode(mode: FilePickerViewMode) {
        dataStore.edit { it[GUIDE_PICKER_VIEW_MODE_KEY] = mode.name }
    }

    val guidePickerSortField: Flow<FilePickerSortField> = dataStore.data.map { prefs ->
        try { FilePickerSortField.valueOf(prefs[GUIDE_PICKER_SORT_FIELD_KEY] ?: FilePickerSortField.DATE_ADDED.name) }
        catch (_: IllegalArgumentException) { FilePickerSortField.DATE_ADDED }
    }

    suspend fun setGuidePickerSortField(field: FilePickerSortField) {
        dataStore.edit { it[GUIDE_PICKER_SORT_FIELD_KEY] = field.name }
    }

    val guidePickerSortDirection: Flow<SortDirection> = dataStore.data.map { prefs ->
        try { SortDirection.valueOf(prefs[GUIDE_PICKER_SORT_DIRECTION_KEY] ?: SortDirection.DESC.name) }
        catch (_: IllegalArgumentException) { SortDirection.DESC }
    }

    suspend fun setGuidePickerSortDirection(dir: SortDirection) {
        dataStore.edit { it[GUIDE_PICKER_SORT_DIRECTION_KEY] = dir.name }
    }

    val guidePickerShowLinks: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[GUIDE_PICKER_SHOW_LINKS_KEY] ?: true
    }

    suspend fun setGuidePickerShowLinks(show: Boolean) {
        dataStore.edit { it[GUIDE_PICKER_SHOW_LINKS_KEY] = show }
    }

    val imagesPickerViewMode: Flow<FilePickerViewMode> = dataStore.data.map { prefs ->
        try { FilePickerViewMode.valueOf(prefs[IMAGES_PICKER_VIEW_MODE_KEY] ?: FilePickerViewMode.GRID.name) }
        catch (_: IllegalArgumentException) { FilePickerViewMode.GRID }
    }

    suspend fun setImagesPickerViewMode(mode: FilePickerViewMode) {
        dataStore.edit { it[IMAGES_PICKER_VIEW_MODE_KEY] = mode.name }
    }

    val imagesPickerSortField: Flow<FilePickerSortField> = dataStore.data.map { prefs ->
        try { FilePickerSortField.valueOf(prefs[IMAGES_PICKER_SORT_FIELD_KEY] ?: FilePickerSortField.DATE_ADDED.name) }
        catch (_: IllegalArgumentException) { FilePickerSortField.DATE_ADDED }
    }

    suspend fun setImagesPickerSortField(field: FilePickerSortField) {
        dataStore.edit { it[IMAGES_PICKER_SORT_FIELD_KEY] = field.name }
    }

    val imagesPickerSortDirection: Flow<SortDirection> = dataStore.data.map { prefs ->
        try { SortDirection.valueOf(prefs[IMAGES_PICKER_SORT_DIRECTION_KEY] ?: SortDirection.DESC.name) }
        catch (_: IllegalArgumentException) { SortDirection.DESC }
    }

    suspend fun setImagesPickerSortDirection(dir: SortDirection) {
        dataStore.edit { it[IMAGES_PICKER_SORT_DIRECTION_KEY] = dir.name }
    }

    val skippedUpdateVersion: Flow<String?> = dataStore.data.map { preferences ->
        preferences[SKIPPED_UPDATE_VERSION_KEY]
    }.distinctUntilChanged()

    suspend fun setSkippedUpdateVersion(version: String?) {
        dataStore.edit { preferences ->
            if (version != null) {
                preferences[SKIPPED_UPDATE_VERSION_KEY] = version
            } else {
                preferences.remove(SKIPPED_UPDATE_VERSION_KEY)
            }
        }
    }

    /**
     * Hash of the most recent set of game names that the refresh skipped
     * because the user is at the free vault cap. Used by the Library
     * screen to suppress re-displaying the "New games detected" summary
     * dialog when the same set of skipped names recurs unchanged.
     */
    val lastRefreshSkippedHash: Flow<String?> = dataStore.data.map { preferences ->
        preferences[LAST_REFRESH_SKIPPED_HASH_KEY]
    }.distinctUntilChanged()

    suspend fun setLastRefreshSkippedHash(value: String?) {
        dataStore.edit { preferences ->
            if (value != null) {
                preferences[LAST_REFRESH_SKIPPED_HASH_KEY] = value
            } else {
                preferences.remove(LAST_REFRESH_SKIPPED_HASH_KEY)
            }
        }
    }

    /**
     * Epoch-millis timestamp of the most recent successful achievement bulk sync
     * (RA + Steam recent), used by [com.twinlore.app.data.AchievementSyncCoordinator]
     * to gate cold-start re-runs to once per 6h.
     */
    val lastAchievementSyncAt: Flow<Long> = dataStore.data.map { preferences ->
        preferences[LAST_ACHIEVEMENT_SYNC_AT_KEY] ?: 0L
    }

    suspend fun setLastAchievementSyncAt(value: Long) {
        dataStore.edit { preferences ->
            preferences[LAST_ACHIEVEMENT_SYNC_AT_KEY] = value
        }
    }

    /** Epoch-millis of the last "support the development" prompt shown (0 = never). */
    val lastSupportPromptAt: Flow<Long> = dataStore.data.map { preferences ->
        preferences[LAST_SUPPORT_PROMPT_AT_KEY] ?: 0L
    }

    suspend fun setLastSupportPromptAt(value: Long) {
        dataStore.edit { preferences ->
            preferences[LAST_SUPPORT_PROMPT_AT_KEY] = value
        }
    }

    /** How many automatic support prompts have been shown, ever (cap: 2). */
    val supportPromptCount: Flow<Int> = dataStore.data.map { preferences ->
        preferences[SUPPORT_PROMPT_COUNT_KEY] ?: 0
    }

    /** Lifetime count of successful journey exports (image or video). */
    val journeyExportCount: Flow<Int> = dataStore.data.map { preferences ->
        preferences[JOURNEY_EXPORT_COUNT_KEY] ?: 0
    }

    /** Storage values of the one-shot prompt milestones already consumed. */
    val supportMilestonesConsumed: Flow<Set<String>> = dataStore.data.map { preferences ->
        preferences[SUPPORT_MILESTONES_CONSUMED_KEY] ?: emptySet()
    }

    suspend fun incrementJourneyExportCount() {
        dataStore.edit { preferences ->
            preferences[JOURNEY_EXPORT_COUNT_KEY] = (preferences[JOURNEY_EXPORT_COUNT_KEY] ?: 0) + 1
        }
    }

    suspend fun setJourneyExportCount(value: Int) {
        dataStore.edit { preferences ->
            preferences[JOURNEY_EXPORT_COUNT_KEY] = value
        }
    }

    /** One atomic edit per prompt shown: count, timestamp and consumed credit move together. */
    suspend fun recordSupportPromptShown(milestone: String, nowMs: Long) {
        dataStore.edit { preferences ->
            preferences[SUPPORT_PROMPT_COUNT_KEY] = (preferences[SUPPORT_PROMPT_COUNT_KEY] ?: 0) + 1
            preferences[LAST_SUPPORT_PROMPT_AT_KEY] = nowMs
            preferences[SUPPORT_MILESTONES_CONSUMED_KEY] =
                (preferences[SUPPORT_MILESTONES_CONSUMED_KEY] ?: emptySet()) + milestone
        }
    }

    /** Overwrites count and consumed set without touching the timestamp (legacy migration, tests). */
    suspend fun setSupportPromptState(count: Int, consumed: Set<String>) {
        dataStore.edit { preferences ->
            preferences[SUPPORT_PROMPT_COUNT_KEY] = count
            preferences[SUPPORT_MILESTONES_CONSUMED_KEY] = consumed
        }
    }

    /**
     * One-time license-migration-V2 marker. Set to true after the first
     * launch of the new freemium model, so the migration never re-runs.
     * Used by [com.twinlore.app.data.license.LicenseMigrationV2].
     */
    val licenseMigrationV2Done: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[LICENSE_MIGRATION_V2_KEY] ?: false
    }.distinctUntilChanged()

    suspend fun setLicenseMigrationV2Done() {
        dataStore.edit { preferences ->
            preferences[LICENSE_MIGRATION_V2_KEY] = true
        }
    }

    /** Custom AI prompt template for checklist generation. Null/blank = use default. */
    val customChecklistAiPrompt: Flow<String?> = dataStore.data
        .map { prefs -> prefs[CHECKLIST_CUSTOM_AI_PROMPT_KEY]?.takeIf { it.isNotBlank() } }

    /** User's saved prompt templates as JSON. Null = no overrides; callers merge with defaults. */
    val checklistPromptTemplatesJson: Flow<String?> = dataStore.data
        .map { it[CHECKLIST_PROMPT_TEMPLATES_JSON_KEY] }

    suspend fun setChecklistPromptTemplatesJson(json: String?) {
        dataStore.edit { prefs ->
            if (json.isNullOrBlank()) prefs.remove(CHECKLIST_PROMPT_TEMPLATES_JSON_KEY)
            else prefs[CHECKLIST_PROMPT_TEMPLATES_JSON_KEY] = json
        }
    }

    /** Id of the currently selected prompt template. Null = use first template. */
    val checklistSelectedPromptId: Flow<String?> = dataStore.data
        .map { it[CHECKLIST_SELECTED_PROMPT_ID_KEY] }

    suspend fun setChecklistSelectedPromptId(id: String?) {
        dataStore.edit { prefs ->
            if (id.isNullOrBlank()) prefs.remove(CHECKLIST_SELECTED_PROMPT_ID_KEY)
            else prefs[CHECKLIST_SELECTED_PROMPT_ID_KEY] = id
        }
    }

    /** High score for the checklist tap-the-ring easter egg game. Shared across all vaults. */
    val checklistTapGameHighScore: Flow<Int> = dataStore.data
        .map { it[CHECKLIST_TAP_GAME_HIGH_SCORE_KEY] ?: 0 }

    suspend fun setChecklistTapGameHighScore(score: Int) {
        dataStore.edit { prefs ->
            prefs[CHECKLIST_TAP_GAME_HIGH_SCORE_KEY] = score
        }
    }

    val guideThemeInject: Flow<Boolean> = dataStore.data
        .map { it[GUIDE_THEME_INJECT_KEY] ?: true }

    suspend fun setGuideThemeInject(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[GUIDE_THEME_INJECT_KEY] = enabled
        }
    }

    /** Smart formatting reflows hard-wrapped prose and fits each ASCII block to
     *  the screen on its own. Off renders the guide exactly as written, at one
     *  document-wide size. Default on, which is the long-standing behavior. */
    val guideSmartFormatting: Flow<Boolean> = dataStore.data
        .map { it[GUIDE_SMART_FORMATTING_KEY] ?: true }

    suspend fun setGuideSmartFormatting(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[GUIDE_SMART_FORMATTING_KEY] = enabled
        }
    }

    val privacyBlockingEnabled: Flow<Boolean> = dataStore.data
        .map { it[PRIVACY_BLOCKING_ENABLED_KEY] ?: true }

    suspend fun setPrivacyBlockingEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[PRIVACY_BLOCKING_ENABLED_KEY] = enabled
        }
    }

    val guideScrollMarkersBarVisible: Flow<Boolean> = dataStore.data
        .map { it[GUIDE_SCROLL_MARKERS_BAR_VISIBLE_KEY] ?: false }

    suspend fun setGuideScrollMarkersBarVisible(visible: Boolean) {
        dataStore.edit { prefs ->
            prefs[GUIDE_SCROLL_MARKERS_BAR_VISIBLE_KEY] = visible
        }
    }

    val lastAppState: Flow<String?> = dataStore.data.map { preferences ->
        preferences[LAST_APP_STATE_KEY]
    }

    suspend fun setLastAppState(stateString: String) {
        dataStore.edit { preferences ->
            preferences[LAST_APP_STATE_KEY] = stateString
        }
    }

    val launcherApplyThemeToIcons: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[LAUNCHER_THEME_ICONS_KEY] ?: false
    }.distinctUntilChanged()

    val launcherShowAppNames: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[LAUNCHER_SHOW_NAMES_KEY] ?: true
    }.distinctUntilChanged()

    val launcherIconSize: Flow<LauncherIconSize> = dataStore.data.map { preferences ->
        val value = preferences[LAUNCHER_ICON_SIZE_KEY] ?: LauncherIconSize.M.name
        try { LauncherIconSize.valueOf(value) } catch (_: Exception) { LauncherIconSize.M }
    }.distinctUntilChanged()

    val launcherAutoDetectGames: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[LAUNCHER_AUTO_DETECT_KEY] ?: true
    }.distinctUntilChanged()

    val launcherShowClock: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[LAUNCHER_SHOW_CLOCK_KEY] ?: true
    }.distinctUntilChanged()

    val launcherClockSize: Flow<LauncherClockSize> = dataStore.data.map { preferences ->
        val value = preferences[LAUNCHER_CLOCK_SIZE_KEY] ?: LauncherClockSize.M.name
        try { LauncherClockSize.valueOf(value) } catch (_: Exception) { LauncherClockSize.M }
    }.distinctUntilChanged()

    /**
     * Whether the regular "All apps" section in the launcher is collapsed
     * (showing only pinned apps + emulators). Persisted across app sessions.
     * Default: false (expanded), preserving prior behavior.
     */
    val appsSectionCollapsed: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[LAUNCHER_APPS_COLLAPSED_KEY] ?: false
    }.distinctUntilChanged()

    suspend fun setAppsSectionCollapsed(collapsed: Boolean) {
        dataStore.edit { it[LAUNCHER_APPS_COLLAPSED_KEY] = collapsed }
    }

    val catalogSelectedTab: Flow<Category> = dataStore.data.map { preferences ->
        val value = preferences[CATALOG_SELECTED_TAB_KEY] ?: Category.Emulator.name
        try { Category.valueOf(value) } catch (_: Exception) { Category.Emulator }
    }.distinctUntilChanged()

    suspend fun setCatalogSelectedTab(tab: Category) {
        dataStore.edit { it[CATALOG_SELECTED_TAB_KEY] = tab.name }
    }

    val catalogDeviceType: Flow<DeviceType> = dataStore.data.map { preferences ->
        val value = preferences[CATALOG_DEVICE_TYPE_KEY] ?: DeviceType.SingleScreen.name
        try { DeviceType.valueOf(value) } catch (_: Exception) { DeviceType.SingleScreen }
    }.distinctUntilChanged()

    suspend fun setCatalogDeviceType(type: DeviceType) {
        dataStore.edit { it[CATALOG_DEVICE_TYPE_KEY] = type.name }
    }

    // --- Cross-device sync preferences ---

    /** deviceId -> lastWrittenAtMillis of the newest snapshot already merged from that device. */
    suspend fun getSyncMergedMarkers(): Map<String, Long> {
        val raw = dataStore.data.first()[SYNC_MERGED_MARKERS_KEY] ?: return emptyMap()
        return runCatching {
            val o = org.json.JSONObject(raw)
            // optLong so one corrupt entry drops only itself, not the whole map
            // (a total drop would re-merge already-merged snapshots).
            o.keys().asSequence()
                .associateWith { o.optLong(it, -1L) }
                .filterValues { it >= 0L }
        }.getOrDefault(emptyMap())
    }

    suspend fun setSyncMergedMarker(deviceId: String, lastWrittenAtMillis: Long) {
        dataStore.edit { prefs ->
            val o = runCatching { org.json.JSONObject(prefs[SYNC_MERGED_MARKERS_KEY] ?: "{}") }
                .getOrDefault(org.json.JSONObject())
            o.put(deviceId, lastWrittenAtMillis)
            prefs[SYNC_MERGED_MARKERS_KEY] = o.toString()
        }
    }

    suspend fun getDeclinedSyncChangeSet(): String? =
        dataStore.data.first()[SYNC_DECLINED_CHANGESET_KEY]

    suspend fun setDeclinedSyncChangeSet(key: String?) {
        dataStore.edit { prefs ->
            if (key == null) prefs.remove(SYNC_DECLINED_CHANGESET_KEY)
            else prefs[SYNC_DECLINED_CHANGESET_KEY] = key
        }
    }

    suspend fun getSyncUpdateNoticeDismissedVersion(): String? =
        dataStore.data.first()[SYNC_UPDATE_NOTICE_DISMISSED_KEY]

    suspend fun setSyncUpdateNoticeDismissedVersion(version: String) {
        dataStore.edit { it[SYNC_UPDATE_NOTICE_DISMISSED_KEY] = version }
    }

    // --- AI assistant (Gemini BYOK) ---

    val geminiApiKey: Flow<String?> = secretFlow(AppSecret.GeminiApiKey)

    suspend fun setGeminiApiKey(key: String?) {
        migrateSecretsIfNeeded()
        secrets.set(AppSecret.GeminiApiKey, key)
    }

    val geminiModel: Flow<String> = dataStore.data.map { prefs ->
        prefs[GEMINI_MODEL_KEY]?.takeIf { it.isNotBlank() } ?: "gemini-flash-latest"
    }

    suspend fun setGeminiModel(model: String) {
        dataStore.edit { it[GEMINI_MODEL_KEY] = model.ifBlank { "gemini-flash-latest" } }
    }

    val aiTemplates: Flow<List<String>> = dataStore.data.map { prefs ->
        val raw = prefs[AI_TEMPLATES_KEY]
        if (raw.isNullOrBlank()) DEFAULT_AI_TEMPLATES
        else try {
            com.google.gson.Gson().fromJson(raw, Array<String>::class.java)?.toList() ?: DEFAULT_AI_TEMPLATES
        } catch (_: Exception) {
            DEFAULT_AI_TEMPLATES
        }
    }

    suspend fun setAiTemplates(templates: List<String>) {
        dataStore.edit { it[AI_TEMPLATES_KEY] = com.google.gson.Gson().toJson(templates) }
    }

    val aiAutoScreenshot: Flow<Boolean> = dataStore.data.map { it[AI_AUTO_SCREENSHOT_KEY] ?: false }

    suspend fun setAiAutoScreenshot(enabled: Boolean) {
        dataStore.edit { it[AI_AUTO_SCREENSHOT_KEY] = enabled }
    }

    // Show the AI assistant as a bottom-bar tab (default on).
    val aiBottomTabVisible: Flow<Boolean> = dataStore.data.map { it[AI_BOTTOM_TAB_KEY] ?: true }

    suspend fun setAiBottomTabVisible(visible: Boolean) {
        dataStore.edit { it[AI_BOTTOM_TAB_KEY] = visible }
    }

    val achievementsTabVisible: Flow<Boolean> = dataStore.data.map { it[ACHIEVEMENTS_TAB_KEY] ?: true }

    suspend fun setAchievementsTabVisible(visible: Boolean) {
        dataStore.edit { it[ACHIEVEMENTS_TAB_KEY] = visible }
    }

    /**
     * The app wallpaper: a picture or animation the user picked in Settings, drawn behind the
     * library (and the Journey stage). Null when none set.
     */
    val appWallpaperImage: Flow<String?> = dataStore.data.map { it[APP_WALLPAPER_IMAGE_KEY] }

    suspend fun setAppWallpaperImage(uri: String?) {
        dataStore.edit {
            if (uri == null) it.remove(APP_WALLPAPER_IMAGE_KEY) else it[APP_WALLPAPER_IMAGE_KEY] = uri
        }
    }

}

/**
 * LED inactivity turn-off presets. NEVER = LED stays on until app backgrounded or focus change.
 */
enum class LedTurnOffTime(val inactivityMs: Long?) {
    NEVER(null),
    SEC_5(5_000L),
    SEC_15(15_000L),
    SEC_30(30_000L),
    MIN_1(60_000L),
    MIN_5(5L * 60 * 1000L)
}

/**
 * Source for detecting last played game.
 */
enum class LastPlayedSource {
    NOT_CONFIGURED,
    RETROARCH,
    ES_DE,
    COCOON,
    ACHIEVEMENTS
}

/**
 * Whether applying an app icon should be interrupted by the one-time
 * "show it in Cocoon too" hint: only when Cocoon is the detection source
 * (the icon pack Cocoon reads from is only useful there) and only if the
 * hint has never been shown before.
 */
fun shouldShowCocoonIconPackHint(source: LastPlayedSource, alreadyShown: Boolean): Boolean =
    source == LastPlayedSource.COCOON && !alreadyShown

/**
 * Theme palette options — coordinated background/foreground color sets.
 * SYSTEM mode is handled separately (null selection in ViewModel).
 */
enum class ThemePalette(
    val background: Color,
    val foreground: Color,
    val isDark: Boolean,
    val displayName: String
) {
    LOBBY(Color.White, Color.Black, false, "Lobby"),
    OBSIDIAN(Color.Black, Color.White, true, "Obsidian"),

    // --- Dark — darkest to lightest background ---
    CONSOLE(Color(0xFF000000), Color(0xFF00FF41), true, "Console"),
    NEXUS(Color(0xFF0A0020), Color(0xFF7C4DFF), true, "Nexus"),
    BLOOD_MOON(Color(0xFF1A0000), Color(0xFFFF1744), true, "Blood Moon"),
    NEON(Color(0xFF0A0A0A), Color(0xFFFF00FF), true, "Neon"),
    POWER_UP(Color(0xFF1A0018), Color(0xFFFF4081), true, "Power-Up"),
    WARP_ZONE(Color(0xFF0A0A14), Color(0xFF1DE9B6), true, "Warp Zone"),
    EMBER(Color(0xFF1A0800), Color(0xFFFF6E40), true, "Ember"),
    CRITICAL_HIT(Color(0xFF1A0A0A), Color(0xFFFF8A80), true, "Critical Hit"),
    LOOT(Color(0xFF1C0A1A), Color(0xFFFFD700), true, "Loot"),
    WITCH(Color(0xFF1F0530), Color(0xFFE040FB), true, "Witch"),
    BONFIRE(Color(0xFF1A1008), Color(0xFFE8A33C), true, "Bonfire"),
    ADVANCE(Color(0xFF1A0A2E), Color(0xFFD4C5F9), true, "Advance"),
    DUNGEON(Color(0xFF1C1014), Color(0xFFE8D5C4), true, "Dungeon"),
    RESPAWN(Color(0xFF0A1628), Color(0xFF00E5FF), true, "Respawn"),
    MIDNIGHT(Color(0xFF0D1B2A), Color(0xFFE0E1DD), true, "Midnight"),
    STEALTH(Color(0xFF1A1A20), Color(0xFF78909C), true, "Stealth"),
    POTION(Color(0xFF0F380F), Color(0xFF9BBC0F), true, "Potion"),
    CARTRIDGE(Color(0xFF2D2D2D), Color(0xFFACA494), true, "Cartridge"),

    // --- Light — darkest to lightest background ---
    PLASMA(Color(0xFFF3E5F5), Color(0xFF7B1FA2), false, "Plasma"),
    OASIS(Color(0xFFE0F2F1), Color(0xFF00695C), false, "Oasis"),
    ICE_KINGDOM(Color(0xFFE8F4FD), Color(0xFF0D47A1), false, "Ice Kingdom"),
    FAIRY(Color(0xFFFFF0F5), Color(0xFFFF1493), false, "Fairy"),
    SAKURA(Color(0xFFFFF0F5), Color(0xFF4A2040), false, "Sakura"),
    IVORY_TOWER(Color(0xFFFDF6E3), Color(0xFF3B2F2F), false, "Ivory Tower"),
    PHOENIX(Color(0xFFFFF8E1), Color(0xFFFF6F00), false, "Phoenix"),
    SLIME(Color(0xFFF0FFF0), Color(0xFF1B5E20), false, "Slime"),
    HEAL(Color(0xFFF1FFF0), Color(0xFF00C853), false, "Heal"),
}

/**
 * App font options for global typography.
 */
enum class AppFont(val displayName: String) {
    DEFAULT("System"),
    COURIER("Courier"),
    JETBRAINS("JetBrains"),
    PIXEL("Pixel"),
    GOTHIC("Gothic"),
    SPACE("Space"),
}

/**
 * Art display mode options
 */
enum class ArtDisplayMode {
    FIT,
    CROP,
    FILL
}

/**
 * Global status-cluster mode for the landscape top bars (see
 * LibraryStatusIndicators). Tap on the cluster flips Standard ↔ Performance;
 * Off is reachable only from Settings because an empty cluster has no tap
 * target.
 *
 * There was a fourth, Hidden, which removed the vault-session top bar
 * outright. That bar now carries the way into Always on Top, so hiding it
 * hides the feature. Stored "HIDDEN" values decode to [STANDARD] through
 * [fromName]'s fallback, which is the whole of the migration.
 */
enum class StatusBarMode(val displayName: String) {
    STANDARD("Standard"),
    PERFORMANCE("Performance"),
    OFF("Off");

    /** Tap on the status cluster: Standard ↔ Performance. Off stays put. */
    fun tapToggled(): StatusBarMode = when (this) {
        STANDARD -> PERFORMANCE
        PERFORMANCE -> STANDARD
        OFF -> OFF
    }

    companion object {
        /** Unknown or corrupt stored values fall back to [STANDARD]. */
        fun fromName(raw: String?): StatusBarMode =
            entries.firstOrNull { it.name == raw } ?: STANDARD
    }
}

/**
 * Focus color presets with modern, distinct colors
 * Includes vibrant colors and darker shades (blue, brown, etc.) for various focus color preferences
 */
enum class FocusColor(val color: Color, val displayName: String) {
    // Primary vibrant colors
    ELECTRIC_BLUE(Color(0xFF00D4FF), "Electric Blue"),
    NEON_GREEN(Color(0xFF39FF14), "Neon Green"),
    VIBRANT_PURPLE(Color(0xFFB026FF), "Vibrant Purple"),
    SUNSET_ORANGE(Color(0xFFFF6B35), "Sunset Orange"),
    CYBER_PINK(Color(0xFFFF1493), "Cyber Pink"),
    ELECTRIC_YELLOW(Color(0xFFFFEA00), "Electric Yellow"),
    AQUA_CYAN(Color(0xFF00FFFF), "Aqua Cyan"),
    CRIMSON(Color(0xFFDC143C), "Crimson"),
    TURQUOISE(Color(0xFF40E0D0), "Turquoise"),
    VIOLET(Color(0xFF8A2BE2), "Violet"),
    
    // Additional vibrant colors
    LIME_GREEN(Color(0xFF32CD32), "Lime Green"),
    HOT_PINK(Color(0xFFFF69B4), "Hot Pink"),
    GOLD(Color(0xFFFFD700), "Gold"),
    CORAL(Color(0xFFFF7F50), "Coral"),
    SKY_BLUE(Color(0xFF87CEEB), "Sky Blue"),
    MAGENTA(Color(0xFFFF00FF), "Magenta"),
    EMERALD(Color(0xFF50C878), "Emerald"),
    AMBER(Color(0xFFFFBF00), "Amber"),
    MUSTARD(Color(0xFFE6B800), "Mustard"),
    INDIGO(Color(0xFF4B0082), "Indigo"),
    SALMON(Color(0xFFFF6B6B), "Salmon"),
    MINT(Color(0xFF98FF98), "Mint"),
    LAVENDER(Color(0xFFE6E6FA), "Lavender"),
    ORANGE_RED(Color(0xFFFF4500), "Orange Red"),
    SPRING_GREEN(Color(0xFF00FF7F), "Spring Green"),
    
    // Darker shades for focus colors
    DARK_BLUE(Color(0xFF1E3A8A), "Dark Blue"),
    NAVY_BLUE(Color(0xFF001F3F), "Navy Blue"),
    DARK_BROWN(Color(0xFF5C4033), "Dark Brown"),
    CHOCOLATE(Color(0xFF3D2817), "Chocolate"),
    FOREST_GREEN(Color(0xFF1B4332), "Forest Green"),
    DARK_GREEN(Color(0xFF0F5132), "Dark Green"),
    DARK_PURPLE(Color(0xFF6A0DAD), "Dark Purple"),
    DEEP_PURPLE(Color(0xFF2D1B3D), "Deep Purple"),
    MAROON(Color(0xFF800000), "Maroon"),
    DARK_RED(Color(0xFF8B0000), "Dark Red"),
    DARK_TEAL(Color(0xFF004D4D), "Dark Teal"),
    CHARCOAL(Color(0xFF36454F), "Charcoal"),
    DARK_GRAY(Color(0xFF2C2C2C), "Dark Gray"),
    BURNT_ORANGE(Color(0xFFCC5500), "Burnt Orange"),
    DARK_CYAN(Color(0xFF008B8B), "Dark Cyan"),
    DARK_MAGENTA(Color(0xFF8B008B), "Dark Magenta"),
    SLATE_BLUE(Color(0xFF483D8B), "Slate Blue"),
    DARK_OLIVE(Color(0xFF3D3D1F), "Dark Olive"),
    DARK_SLATE_GRAY(Color(0xFF2F4F4F), "Dark Slate Gray"),
    
    // Beige tones
    LIGHT_BEIGE(Color(0xFFE6D5B8), "Light Beige"),
    DARK_BEIGE(Color(0xFF8B7355), "Dark Beige"),
    
    // Neutral colors
    WHITE(Color(0xFFFFFFFF), "White"),
    BLACK(Color(0xFF000000), "Black");
    
    /**
     * Find the closest FocusColor to a given Color using Euclidean distance in RGB space
     * @param targetColor The color to match
     * @param isDarkTheme If true, excludes very dark palette colors so focus stays readable on dark background. If false, excludes black and very dark colors.
     */
    companion object {
        /**
         * Curated subset shown in the user-facing accent pickers (Settings +
         * guide display sheet). The full enum is intentionally kept for
         * backward-compatible stored values and adaptive [findClosest] matching;
         * only the manual picker UI is trimmed to ~16 distinct hues + neutrals
         * so the row isn't a wall of near-duplicate colors.
         */
        val SELECTABLE: List<FocusColor> = listOf(
            CRIMSON, SUNSET_ORANGE, GOLD, ELECTRIC_YELLOW,
            NEON_GREEN, EMERALD, TURQUOISE, ELECTRIC_BLUE,
            SKY_BLUE, DARK_BLUE, VIBRANT_PURPLE, INDIGO,
            CYBER_PINK, MAGENTA, WHITE, BLACK,
        )

        /**
         * [SELECTABLE] minus the accents that would be unreadable on the current
         * theme background — dark/near-black accents on a dark theme, white/near-
         * white accents on a light theme. Uses the same brightness thresholds as
         * [findClosest] so the manual picker and adaptive matching agree.
         */
        fun selectableFor(isDarkTheme: Boolean): List<FocusColor> = SELECTABLE.filter { fc ->
            if (isDarkTheme) !isTooDarkForDarkThemeBackground(fc.color)
            else !isVeryLightColor(fc.color)
        }

        fun findClosest(targetColor: Color, isDarkTheme: Boolean = false): FocusColor {
            // Check if the target color itself is inappropriate for the theme
            // In dark theme: if target is dark (use looser threshold so more colors fall back to white), default to white
            if (isDarkTheme && isTooDarkForDarkThemeBackground(targetColor)) {
                return WHITE
            }
            // In light theme: if target is very light/whitish, default to black
            if (!isDarkTheme && isVeryLightColor(targetColor)) {
                return BLACK
            }
            
            var minDistance = Double.MAX_VALUE
            var closest = CRIMSON // Default fallback
            
            values().forEach { focusColor ->
                // Skip colors that don't work well with the current theme
                // Dark theme: skip very dark palette colors so we only pick light/mid tones (readable on black). Allow white and light colors.
                // Light theme: skip black and very dark colors (except yellows)
                if (isDarkTheme && (focusColor != WHITE && isVeryDarkColor(focusColor.color) && !isYellowTone(focusColor.color))) {
                    return@forEach // Skip very dark colors in dark theme (except yellows)
                }
                if (!isDarkTheme && (focusColor == BLACK || (isVeryDarkColor(focusColor.color) && !isYellowTone(focusColor.color)))) {
                    return@forEach // Skip black and very dark colors in light theme (except yellows)
                }
                
                val distance = calculateColorDistance(targetColor, focusColor.color)
                if (distance < minDistance) {
                    minDistance = distance
                    closest = focusColor
                }
            }
            
            return closest
        }
        
        /**
         * Check if a color is very light (brightness > 0.85)
         */
        private fun isVeryLightColor(color: Color): Boolean {
            // Calculate perceived brightness using relative luminance formula
            val r = color.red
            val g = color.green
            val b = color.blue
            val brightness = (0.299 * r + 0.587 * g + 0.114 * b)
            return brightness > 0.85
        }
        
        /**
         * Check if a color is very dark (brightness < 0.15)
         */
        private fun isVeryDarkColor(color: Color): Boolean {
            // Calculate perceived brightness using relative luminance formula
            val r = color.red
            val g = color.green
            val b = color.blue
            val brightness = (0.299 * r + 0.587 * g + 0.114 * b)
            return brightness < 0.15
        }

        /**
         * In dark theme, treat detected colors as "too dark" so they fall back to white for readability.
         * Threshold 0.35: dark colors become white, but crimson/reddish (~0.33) stay above and keep matching to CRIMSON.
         */
        private fun isTooDarkForDarkThemeBackground(color: Color): Boolean {
            val r = color.red
            val g = color.green
            val b = color.blue
            val brightness = (0.5 * r + 0.587 * g + 0.114 * b)
            return brightness < 0.4
        }
        
        /**
         * Check if a color is a yellow/mustard tone (has high red and green, low blue)
         * This helps preserve yellow colors even if they're bright or dark
         * Yellow tones are defined as: high red+green values, low blue, and green is at least 60% of red
         */
        private fun isYellowTone(color: Color): Boolean {
            val r = color.red
            val g = color.green
            val b = color.blue
            // Yellow tones have: high red+green, low blue, and green >= red * 0.6 (more permissive)
            // This catches: Electric Yellow, Gold, Amber, Mustard, and similar tones
            return (r > 0.6 && g > 0.5 && b < 0.6 && g >= r * 0.6)
        }
        
        /**
         * Calculate perceptually accurate distance between two colors
         * Uses weighted RGB distance (human eyes are more sensitive to green) 
         * combined with hue similarity for better color matching
         */
        private fun calculateColorDistance(color1: Color, color2: Color): Double {
            // Weighted RGB distance (human eyes are more sensitive to green)
            val r1 = color1.red * 255
            val g1 = color1.green * 255
            val b1 = color1.blue * 255
            val r2 = color2.red * 255
            val g2 = color2.green * 255
            val b2 = color2.blue * 255
            
            val deltaR = r1 - r2
            val deltaG = g1 - g2
            val deltaB = b1 - b2
            
            // Weighted RGB distance (weights: R=0.3, G=0.59, B=0.11 - based on human eye sensitivity)
            val weightedRgbDistance = kotlin.math.sqrt(
                (0.3 * deltaR * deltaR + 0.59 * deltaG * deltaG + 0.11 * deltaB * deltaB).toDouble()
            )
            
            // Calculate hue similarity in HSV space for better color family matching
            val hueDistance = calculateHueDistance(color1, color2)
            
            // Combine weighted RGB distance (70%) with hue distance (30%)
            // This ensures we match colors that are both close in RGB and similar in hue
            return weightedRgbDistance * 0.7 + hueDistance * 0.3
        }
        
        /**
         * Calculate hue distance in HSV space
         * Hue is the most important aspect for color perception
         */
        private fun calculateHueDistance(color1: Color, color2: Color): Double {
            val hsv1 = rgbToHsv(color1)
            val hsv2 = rgbToHsv(color2)
            
            val hue1 = hsv1.first
            val hue2 = hsv2.first
            
            // Calculate circular distance for hue (0-360 degrees)
            var hueDiff = kotlin.math.abs(hue1 - hue2)
            if (hueDiff > 180) {
                hueDiff = 360 - hueDiff // Take the shorter path around the circle
            }
            
            // Normalize to 0-1 range and scale to match RGB distance range
            return (hueDiff / 180.0) * 255.0
        }
        
        /**
         * Convert RGB Color to HSV (Hue, Saturation, Value)
         * Returns triple: (Hue 0-360, Saturation 0-1, Value 0-1)
         */
        private fun rgbToHsv(color: Color): Triple<Double, Double, Double> {
            // Convert Float RGB values (0.0f-1.0f) to Double for calculations
            val r = color.red.toDouble()
            val g = color.green.toDouble()
            val b = color.blue.toDouble()
            
            val max = kotlin.math.max(kotlin.math.max(r, g), b)
            val min = kotlin.math.min(kotlin.math.min(r, g), b)
            val delta = max - min
            
            val value = max
            val saturation = if (max == 0.0) 0.0 else delta / max
            
            val hue = when {
                delta == 0.0 -> 0.0
                max == r -> {
                    var h = 60.0 * ((g - b) / delta)
                    // Handle negative hue values
                    while (h < 0) h += 360.0
                    h
                }
                max == g -> {
                    60.0 * ((b - r) / delta + 2.0)
                }
                else -> {
                    60.0 * ((r - g) / delta + 4.0)
                }
            }
            
            return Triple(hue, saturation, value)
        }
    }
}

/**
 * Library view mode: carousel or one of three grid sizes.
 * Grid column count is determined by the mode (no separate setting).
 */
enum class LibraryViewMode(val displayName: String, val gridColumns: Int?) {
    CAROUSEL("Carousel", null),
    LIST("List", 1),
    GRID_EXTRA_LARGE("Large grid", 3),
    GRID_MEDIUM("Medium grid", 5),
    GRID_SMALL("Small grid", 9),
    LAUNCHER("Apps", null);

    val isGrid: Boolean get() = gridColumns != null
}

/**
 * The library's full-screen backdrop (round two, task 2). [WALLPAPER] means the user's picked
 * "app wallpaper" image ([SettingsRepository.appWallpaperImage], set in Settings). [SOLID]
 * is named for what it draws (a flat colour): the library has no Iconic/iiSU-style family of
 * non-photo looks to distinguish a flat card from, so "solid colour" says plainly what the user
 * gets, and it is the cheapest of the three to draw: it skips the wallpaper decode, the blur,
 * the 400ms cross-dissolve, and the artwork prefetch that exists solely to keep that dissolve fed.
 */
enum class LibraryBackgroundStyle(val displayName: String) {
    BLURRED_ART("Blurred art"),
    SOLID("Solid colour"),
    WALLPAPER("Wallpaper"),
}

/**
 * Style for the on-screen controller face-button glyphs in the library hint bar.
 * XBOX renders the A/B/X/Y letters; PLAYSTATION renders the ✕ / ○ / □ / △ shapes.
 */
enum class ButtonGlyphStyle(val displayName: String) {
    XBOX("A B X Y"),
    PLAYSTATION("✕ ○ □ △"),
}

/**
 * Grid display ratio options for library grid items
 */
enum class GridDisplayRatio(val width: Float, val height: Float, val displayName: String) {
    RATIO_1_1(1f, 1f, "1:1"),
    RATIO_4_3(4f, 3f, "4:3"),
    RATIO_3_4(3f, 4f, "3:4"),
    RATIO_8_7(8f, 7f, "8:7"),
    RATIO_3_5(3f, 5f, "3:5"),
    RATIO_2_3(2f, 3f, "2:3"),
    RATIO_3_1(3f, 1f, "3:1");
    
    /**
     * Get the aspect ratio value (width/height) for use in Modifier.aspectRatio()
     */
    val aspectRatio: Float get() = width / height
}

/**
 * Default asset type for scraping
 */
enum class DefaultAssetType(val displayName: String) {
    LOGO("Logo / Marquee"),
    GRID("Grid / Cover"),
    HERO("Hero / Fanart")
}

/**
 * Haptic feedback intensity options
 */
enum class HapticFeedbackIntensity(val displayName: String) {
    NONE("No"),
    LIGHT("Light"),
    MEDIUM("Medium"),
    STRONG("Strong")
}

/**
 * Launcher icon size options.
 */
enum class LauncherIconSize(val displayName: String) {
    S("Small"), M("Medium"), L("Large")
}

enum class LauncherClockSize(val displayName: String) {
    S("Small"), M("Medium"), L("Large")
}

/**
 * The colour every accent surface, and the LED, should show right now.
 *
 * With adaptive colour on, that is the exact colour detected from the focused
 * vault's artwork ([rawArgb]); the quantized [FocusColor] the same detection
 * stores beside it is a palette match for pickers, not the colour itself. The
 * rings are the place the difference is visible: a red cover's nearest palette
 * entry is Crimson or Salmon, and a control that drove them with the match
 * turned the red pink.
 */
fun effectiveAccentColor(quantized: FocusColor, rawArgb: Int?, autoDetect: Boolean): Color =
    if (autoDetect && rawArgb != null) Color(rawArgb) else quantized.color
