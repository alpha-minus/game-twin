package com.twinlore.app.ui.screens.launcher

import android.content.Context
import android.content.Intent
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.viewModelScope
import android.widget.Toast
import com.twinlore.app.data.DualDisplayLauncher
import com.twinlore.app.data.GameLauncher
import com.twinlore.app.data.LauncherClockSize
import com.twinlore.app.data.LauncherIconSize
import com.twinlore.app.data.SettingsRepository
import com.twinlore.app.data.VaultRepository
import com.twinlore.app.data.catalog.ConsoleMapping
import com.twinlore.app.data.catalog.RecommendedAppsCatalog
import com.twinlore.app.data.local.FavoriteAppDao
import com.twinlore.app.data.local.FavoriteAppEntity
import com.twinlore.app.domain.Category
import com.twinlore.app.domain.RecommendedApp
import com.twinlore.app.domain.Vault
import com.twinlore.app.ui.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LauncherApp(
    val packageName: String,
    val label: String,
    val icon: Drawable
)

/**
 * An installed app surfaced in the launcher's Emulators section, bundled
 * with the short console phrasing pulled from the matched catalog entry's
 * `about` field (e.g. "PlayStation 3", "GameCube + Wii"). The subtitle is
 * already shortened — "emulator" / "for Android" suffixes stripped.
 */
data class EmulatorEntry(
    val app: LauncherApp,
    val about: String
)

@HiltViewModel
class LauncherViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val favoriteAppDao: FavoriteAppDao,
    private val vaultRepository: VaultRepository,
    private val settingsRepository: SettingsRepository,
    private val gameLauncher: GameLauncher,
    private val recommendedAppsCatalog: RecommendedAppsCatalog,
    private val dualDisplayLauncher: DualDisplayLauncher,
) : BaseViewModel() {

    private val packageManager = context.packageManager
    private val ownPackageName = context.packageName

    private val _allApps = MutableStateFlow<List<LauncherApp>>(emptyList())
    val allApps: StateFlow<List<LauncherApp>> = _allApps.asStateFlow()

    private val _isLoadingApps = MutableStateFlow(true)
    val isLoadingApps: StateFlow<Boolean> = _isLoadingApps.asStateFlow()

    private val _favoritePackageNames = favoriteAppDao.getAllPackageNames()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val favoriteApps: StateFlow<List<LauncherApp>> = combine(
        _allApps,
        favoriteAppDao.getAll()
    ) { apps, favoriteEntities ->
        val appMap = apps.associateBy { it.packageName }
        favoriteEntities.mapNotNull { appMap[it.packageName] }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val lastOpenedVault: StateFlow<Vault?> = vaultRepository.getAllVaultsSortedByLastOpened()
        .map { vaults ->
            vaults
                .filter { it.lastOpened != null }
                .maxByOrNull { it.lastOpened!! }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val applyThemeToIcons = settingsRepository.launcherApplyThemeToIcons
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val showAppNames = settingsRepository.launcherShowAppNames
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val iconSize = settingsRepository.launcherIconSize
        .stateIn(viewModelScope, SharingStarted.Eagerly, LauncherIconSize.M)
    val autoDetectGames = settingsRepository.launcherAutoDetectGames
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val showClock = settingsRepository.launcherShowClock
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val clockSize = settingsRepository.launcherClockSize
        .stateIn(viewModelScope, SharingStarted.Eagerly, LauncherClockSize.M)
    val appsSectionCollapsed = settingsRepository.appsSectionCollapsed
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /**
     * Catalog entries classified as emulators for the user's currently
     * selected device type. Rebuilt on device-type changes; empty until the
     * catalog loads at startup.
     */
    private val _emulatorCatalogEntries = MutableStateFlow<List<RecommendedApp>>(emptyList())

    /**
     * Installed apps that match a Category.Emulator catalog entry, bundled
     * with the matched entry's short `about` text for the subtitle line.
     * Matching is exact package id first, then normalized label-vs-name fuzzy
     * match (5-char minimum). Pinned apps are excluded so each row only
     * appears in one launcher section. Already alphabetical because
     * `_allApps` is sorted by label.
     */
    val emulatorApps: StateFlow<List<EmulatorEntry>> = combine(
        _allApps,
        _favoritePackageNames,
        _emulatorCatalogEntries
    ) { apps, favPkgs, catalogEntries ->
        apps.mapNotNull { app ->
            if (app.packageName in favPkgs) return@mapNotNull null
            val match = matchEmulator(app, catalogEntries) ?: return@mapNotNull null
            EmulatorEntry(
                app = app,
                about = ConsoleMapping.byPackageId[match.id].orEmpty()
            )
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Apps grid excluding favorites AND emulator-section apps. */
    val nonFavoriteApps: StateFlow<List<LauncherApp>> = combine(
        _allApps,
        _favoritePackageNames,
        _emulatorCatalogEntries
    ) { apps, favPkgs, catalogEntries ->
        apps.filter { it.packageName !in favPkgs && matchEmulator(it, catalogEntries) == null }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /**
     * Find the catalog entry (if any) that this installed app plausibly
     * corresponds to. Two passes:
     *  1. Exact package-id match (handles 95% of cases).
     *  2. Normalized label-vs-name fuzzy match (lowercase, alphanumeric-only)
     *     — an entry matches if the installed label contains the catalog
     *     name or vice versa. Catches forks tagged with suffixes like
     *     "Cemu Pro" or "PPSSPP Gold" against catalog "Cemu" / "PPSSPP".
     *
     * Length 5 minimum on both sides for the fuzzy pass: short catalog names
     * like "Play!" → "play" would otherwise collide with installed apps like
     * "Play Store". Those short entries fall back to id-match only.
     */
    private fun matchEmulator(installed: LauncherApp, catalogEntries: List<RecommendedApp>): RecommendedApp? {
        catalogEntries.firstOrNull { it.id == installed.packageName }?.let { return it }
        val installedKey = normalizeName(installed.label)
        if (installedKey.length < 5) return null
        return catalogEntries.firstOrNull { entry ->
            val entryKey = normalizeName(entry.name)
            entryKey.length >= 5 && (installedKey.contains(entryKey) || entryKey.contains(installedKey))
        }
    }

    private fun normalizeName(s: String): String =
        s.lowercase().filter { it.isLetterOrDigit() }

    /** Currently focused vault (for background theming) */
    private val _focusedVaultId = MutableStateFlow<String?>(null)
    val focusedVaultId: StateFlow<String?> = _focusedVaultId.asStateFlow()

    fun setFocusedVault(vaultId: String?) {
        _focusedVaultId.value = vaultId
    }

    fun updateLastOpened(vaultId: String) {
        viewModelScope.launch { vaultRepository.updateLastOpened(vaultId) }
    }

    init {
        refreshApps()
        seedDefaultFavorites()
        observeEmulatorPackageIds()
    }

    /**
     * Watches the user's selected device type and reloads the catalog when it
     * changes, projecting Category.Emulator entries to a Set<packageId> for
     * the emulator section in the launcher.
     */
    private fun observeEmulatorPackageIds() {
        viewModelScope.launch {
            settingsRepository.catalogDeviceType.collect { deviceType ->
                _emulatorCatalogEntries.value = recommendedAppsCatalog.load(deviceType)
                    .filter { it.category == Category.Emulator }
            }
        }
    }

    private fun seedDefaultFavorites() {
        viewModelScope.launch {
            val existing = favoriteAppDao.getAll().first()
            if (existing.isEmpty()) {
                // Seed with Settings and Files as defaults, plus common companion apps
                val defaults = listOf(
                    "com.android.settings",
                    "com.google.android.apps.nbu.files",  // Google Files
                    "com.sec.android.app.myfiles",         // Samsung My Files
                    "com.android.documentsui",             // AOSP Files
                    "com.google.android.youtube",          // YouTube
                    "com.spotify.music",                   // Spotify
                    "com.discord",                         // Discord
                )
                for (pkg in defaults) {
                    if (packageManager.getLaunchIntentForPackage(pkg) != null) {
                        favoriteAppDao.pin(pkg)
                    }
                }
            }
        }
    }

    fun refreshApps() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoadingApps.value = true
            val intent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolveInfos = packageManager.queryIntentActivities(intent, 0)
            val apps = resolveInfos
                .filter { it.activityInfo.packageName != ownPackageName }
                .map { resolveInfo ->
                    LauncherApp(
                        packageName = resolveInfo.activityInfo.packageName,
                        label = resolveInfo.loadLabel(packageManager).toString(),
                        icon = resolveInfo.loadIcon(packageManager)
                    )
                }
                .sortedBy { it.label.lowercase() }
                .distinctBy { it.packageName }
            _allApps.value = apps
            _isLoadingApps.value = false
            seedDefaultPins(apps)
        }
    }

    /**
     * Lays down a starter set of pins on first run: settings, files, a browser and YouTube, in
     * that order, skipping anything not installed.
     *
     * Runs once and records that it ran, rather than checking whether the pin list is empty —
     * unpinning everything is a legitimate choice, and re-seeding it on the next launch would
     * override the user rather than help them. Candidates are matched against the apps that
     * actually resolve a launcher intent, so nothing unlaunchable is ever pinned.
     */
    private suspend fun seedDefaultPins(apps: List<LauncherApp>) {
        if (apps.isEmpty()) return
        if (settingsRepository.defaultPinsSeeded.firstOrNull() == true) return
        val pins = defaultPins(apps.mapTo(HashSet()) { it.packageName })
        // Sequential pins, not a batch: each append takes the next position, so they land in
        // the order above rather than whatever a batch happens to produce.
        pins.forEach { favoriteAppDao.pin(it) }
        settingsRepository.setDefaultPinsSeeded(true)
    }

    fun isFavorite(packageName: String): Boolean {
        return _favoritePackageNames.value.contains(packageName)
    }

    fun toggleFavorite(packageName: String) {
        viewModelScope.launch {
            if (isFavorite(packageName)) {
                favoriteAppDao.deleteByPackageName(packageName)
            } else {
                favoriteAppDao.pin(packageName)
            }
        }
    }

    /** Pin every given package in one batch (no-op for already-pinned packages). */
    fun pinAll(packageNames: Set<String>) {
        if (packageNames.isEmpty()) return
        viewModelScope.launch {
            packageNames.forEach { pkg ->
                if (!isFavorite(pkg)) favoriteAppDao.pin(pkg)
            }
        }
    }

    /** Unpin every given package in one batch (no-op for non-pinned packages). */
    fun unpinAll(packageNames: Set<String>) {
        if (packageNames.isEmpty()) return
        viewModelScope.launch {
            packageNames.forEach { pkg ->
                favoriteAppDao.deleteByPackageName(pkg)
            }
        }
    }

    /** Commit a strip reorder. One DAO transaction; the launcher view follows the same flow. */
    fun setPinOrder(orderedPackageNames: List<String>) {
        if (orderedPackageNames.isEmpty()) return
        viewModelScope.launch { favoriteAppDao.setOrder(orderedPackageNames) }
    }

    /**
     * Launch [packageName]. [fromDisplayId] is the display the CALLER is on: pass it to put the
     * app on the other screen, leave it null to keep Android's own placement.
     *
     * Only new tasks can be placed. An app that is already running resumes on the display it is
     * already on, whatever we ask for.
     */
    fun launchApp(packageName: String, fromDisplayId: Int? = null) {
        val intent = packageManager.getLaunchIntentForPackage(packageName) ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (fromDisplayId == null) {
            context.startActivity(intent)
        } else {
            context.startActivity(
                intent,
                dualDisplayLauncher.buildLaunchOptions(fromDisplayId).toBundle(),
            )
        }
    }

    fun setAppsSectionCollapsed(collapsed: Boolean) {
        viewModelScope.launch { settingsRepository.setAppsSectionCollapsed(collapsed) }
    }

    sealed class LaunchGameResult {
        object Launched : LaunchGameResult()
        data class FallbackToVault(val vaultId: String) : LaunchGameResult()
        object NeedsRomsFolder : LaunchGameResult()
        object Error : LaunchGameResult()
    }

    /**
     * Launch a game from a vault. Returns result so caller can navigate on fallback.
     *
     * @param currentDisplayId the display the launcher is on; pass
     *   `LocalView.current.display?.displayId ?: Display.DEFAULT_DISPLAY` from
     *   the calling Composable so the game can land on the opposite screen.
     */
    fun launchGame(vault: Vault, currentDisplayId: Int): LaunchGameResult {
        android.util.Log.d("LauncherViewModel", "launchGame: vault=${vault.displayName}, hasLaunchData=${vault.hasLaunchData}")
        val romsFolderUri = kotlinx.coroutines.runBlocking { settingsRepository.romsFolderUri.first() }
        val result = gameLauncher.launch(vault, currentDisplayId, romsFolderUri)
        return when (result) {
            is GameLauncher.LaunchResult.Success -> {
                // App-initiated launches never touch the frontend's log, so record the
                // now-playing vault here or the open-last-played flow cannot see them.
                viewModelScope.launch { settingsRepository.setNowPlayingVaultId(vault.id) }
                LaunchGameResult.Launched
            }
            is GameLauncher.LaunchResult.NoLaunchData -> {
                Toast.makeText(context, "No launch data — opening vault", Toast.LENGTH_SHORT).show()
                LaunchGameResult.FallbackToVault(vault.id)
            }
            is GameLauncher.LaunchResult.EmulatorNotFound -> {
                Toast.makeText(
                    context,
                    "Cannot launch — required app not found on device.",
                    Toast.LENGTH_LONG
                ).show()
                LaunchGameResult.Error
            }
            is GameLauncher.LaunchResult.NeedsRomsFolder -> LaunchGameResult.NeedsRomsFolder
            is GameLauncher.LaunchResult.LaunchFailed -> {
                Toast.makeText(
                    context,
                    "Failed to launch game: ${result.error}",
                    Toast.LENGTH_LONG
                ).show()
                LaunchGameResult.Error
            }
        }
    }

    fun setRomsFolderUri(uri: String) {
        viewModelScope.launch { settingsRepository.setRomsFolderUri(uri) }
    }
}
