package com.twinlore.app.ui.screens.catalog

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.twinlore.app.data.DualDisplayLauncher
import com.twinlore.app.data.SettingsRepository
import com.twinlore.app.data.catalog.ApkDownloadManager
import com.twinlore.app.data.catalog.RecommendedAppsCatalog
import com.twinlore.app.domain.Category
import com.twinlore.app.domain.DeviceType
import com.twinlore.app.domain.RecommendedApp
import com.twinlore.app.ui.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.net.URLEncoder
import javax.inject.Inject

@HiltViewModel
class AppCatalogViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val dualDisplayLauncher: DualDisplayLauncher,
    private val catalog: RecommendedAppsCatalog,
    private val apkDownloadManager: ApkDownloadManager
) : BaseViewModel() {

    private val _allApps = MutableStateFlow<List<RecommendedApp>>(emptyList())
    val allApps: StateFlow<List<RecommendedApp>> = _allApps.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    /**
     * Selected row keys. Within a single device-type catalog, package id is
     * unique, so we key selection by [RecommendedApp.id].
     */
    private val _selectedKeys = MutableStateFlow<LinkedHashSet<String>>(linkedSetOf())
    val selectedKeys: StateFlow<Set<String>> = _selectedKeys.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    val selectedTab: StateFlow<Category> = settingsRepository.catalogSelectedTab
        .stateIn(viewModelScope, SharingStarted.Eagerly, Category.Emulator)

    val deviceType: StateFlow<DeviceType> = settingsRepository.catalogDeviceType
        .stateIn(viewModelScope, SharingStarted.Eagerly, DeviceType.SingleScreen)

    val filteredApps: StateFlow<List<RecommendedApp>> = combine(
        _allApps,
        selectedTab,
        _searchQuery
    ) { apps, tab, query ->
        apps.asSequence()
            .filter { it.category == tab }
            .filter { matchesSearch(it, query) }
            .sortedBy { it.name.lowercase() }
            .toList()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        // Reload the in-memory list whenever the device-type setting changes.
        viewModelScope.launch {
            deviceType.collect { type ->
                val apps = catalog.load(type)
                _allApps.value = apps
                _selectedKeys.value = linkedSetOf()
                apkDownloadManager.restoreCachedDownloads(apps)
            }
        }
        // Auto-refresh catalog from GitHub on entry.
        refresh {}
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun onTabSelected(tab: Category) {
        viewModelScope.launch { settingsRepository.setCatalogSelectedTab(tab) }
    }

    fun onDeviceTypeSelected(type: DeviceType) {
        viewModelScope.launch { settingsRepository.setCatalogDeviceType(type) }
    }

    fun clearFilters() {
        _searchQuery.value = ""
    }

    fun toggleSelection(key: String) {
        val next = LinkedHashSet(_selectedKeys.value)
        if (!next.remove(key)) next.add(key)
        _selectedKeys.value = next
    }

    fun clearSelection() {
        _selectedKeys.value = linkedSetOf()
    }

    /**
     * Toggle the "select all visible" state. If every currently-filtered row
     * is already selected, removes them all from the selection set; otherwise
     * adds every visible row to the selection. Selections outside the current
     * filter are untouched in both directions.
     */
    fun toggleSelectAllFiltered() {
        val visible = filteredApps.value
        if (visible.isEmpty()) return
        val current = _selectedKeys.value
        val visibleIds = visible
            .filter { !isAppInstalled(it.id) }
            .mapTo(LinkedHashSet()) { it.id }
        if (visibleIds.isEmpty()) return
        val allSelected = visibleIds.all { it in current }
        val next = LinkedHashSet(current)
        if (allSelected) {
            next.removeAll(visibleIds)
        } else {
            next.addAll(visibleIds)
        }
        _selectedKeys.value = next
    }

    /** Refreshes both standard and dual-screen catalogs from GitHub. */
    fun refresh(onResult: (RecommendedAppsCatalog.RefreshResult) -> Unit) {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                val result = catalog.refresh()
                if (result is RecommendedAppsCatalog.RefreshResult.Success) {
                    // Reload the in-memory list for the active device type.
                    _allApps.value = catalog.load(deviceType.value)
                }
                onResult(result)
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    // --- Multi-select export -------------------------------------------------

    /**
     * Build an Obtainium-Import-format JSON document containing one entry per
     * currently-selected app. Each entry's raw upstream JSON object is included
     * verbatim and enriched with the local-state defaults Obtainium expects on
     * import (installedVersion=null, pinned=false, apkUrls="[]", …).
     */
    fun buildObtainiumImportJson(): String {
        val byKey = _allApps.value.associateBy { it.id }
        val appsArray = org.json.JSONArray()
        for (key in _selectedKeys.value) {
            val app = byKey[key] ?: continue
            val obj = org.json.JSONObject(app.entryJson)
            if (!obj.has("installedVersion")) obj.put("installedVersion", org.json.JSONObject.NULL)
            if (!obj.has("latestVersion")) obj.put("latestVersion", org.json.JSONObject.NULL)
            if (!obj.has("lastUpdateCheck")) obj.put("lastUpdateCheck", 0L)
            if (!obj.has("pinned")) obj.put("pinned", false)
            if (!obj.has("releaseDate")) obj.put("releaseDate", org.json.JSONObject.NULL)
            if (!obj.has("changeLog")) obj.put("changeLog", org.json.JSONObject.NULL)
            if (!obj.has("pendingRepoRenameUrl")) obj.put("pendingRepoRenameUrl", org.json.JSONObject.NULL)
            if (obj.isNull("apkUrls")) obj.put("apkUrls", "[]")
            if (obj.isNull("otherAssetUrls")) obj.put("otherAssetUrls", "[]")
            appsArray.put(obj)
        }
        return org.json.JSONObject().apply { put("apps", appsArray) }.toString(2)
    }

    fun writeObtainiumImportFile(uri: Uri): Int {
        val count = _selectedKeys.value.size
        if (count == 0) return 0
        return try {
            val payload = buildObtainiumImportJson()
            context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                out.write(payload.toByteArray(Charsets.UTF_8))
            } ?: return 0
            clearSelection()
            count
        } catch (e: Exception) {
            android.util.Log.e("AppCatalogViewModel", "Failed to write import JSON", e)
            0
        }
    }

    // --- Download & Install ---------------------------------------------------

    val downloadStates: StateFlow<Map<String, ApkDownloadState>> = apkDownloadManager.states

    fun downloadSelected() {
        val byKey = _allApps.value.associateBy { it.id }
        val apps = _selectedKeys.value
            .mapNotNull { byKey[it] }
            .filter { it.category != Category.TrackOnly && !isAppInstalled(it.id) }
        if (apps.isEmpty()) return
        clearSelection()
        apkDownloadManager.downloadApps(apps)
    }

    fun installApp(appId: String) = apkDownloadManager.installDownloaded(appId)

    fun installAllReady() {
        val firstReady = apkDownloadManager.states.value.entries
            .firstOrNull { it.value is ApkDownloadState.Ready }
        if (firstReady != null) {
            installApp(firstReady.key)
        }
    }

    fun retryApp(appId: String) {
        val byKey = _allApps.value.associateBy { it.id }
        val app = byKey[appId] ?: return
        apkDownloadManager.retryApp(app)
    }

    fun cancelDownloads() {
        apkDownloadManager.cancelAll()
    }

    fun cancelApp(appId: String) {
        apkDownloadManager.cancelApp(appId)
    }

    fun downloadSingleApp(appId: String) {
        val byKey = _allApps.value.associateBy { it.id }
        val app = byKey[appId] ?: return
        apkDownloadManager.downloadApps(listOf(app))
    }

    fun isAppInstalled(appId: String): Boolean {
        // Try catalog ID first
        if (isPackageInstalled(appId)) return true
        // Common applicationIdSuffix used by F-Droid / source-built debug variants
        if (isPackageInstalled("$appId.debug")) return true
        // Try real package name from the APK if we have one cached
        val realPkg = resolveRealPackageName(appId)
        return realPkg != null && isPackageInstalled(realPkg)
    }

    private fun isPackageInstalled(packageName: String): Boolean = try {
        context.packageManager.getPackageInfo(packageName, 0)
        true
    } catch (_: Exception) {
        false
    }

    /**
     * Reads the actual package name from a downloaded APK file, since the
     * catalog ID may differ from the real package name (typos, numeric IDs).
     */
    private fun resolveRealPackageName(appId: String): String? {
        val file = apkDownloadManager.downloadDir.listFiles()
            ?.firstOrNull { it.name.startsWith("${appId}_") }
            ?: return null
        return context.packageManager.getPackageArchiveInfo(file.absolutePath, 0)
            ?.packageName
    }

    /**
     * Called on ON_RESUME to reconcile download states after backgrounding.
     * Retries failed downloads (likely transient network errors from backgrounding),
     * checks installing apps for completion, and refreshes downloading states.
     */
    fun refreshDownloadStates() {
        val byKey = _allApps.value.associateBy { it.id }
        apkDownloadManager.states.value.forEach { (appId, state) ->
            when (state) {
                is ApkDownloadState.Installing -> checkInstallResult(appId)
                is ApkDownloadState.Downloading -> {
                    apkDownloadManager.recheckDownloadStatus(appId)
                }
                is ApkDownloadState.Failed -> {
                    // Auto-retry failed downloads on resume, except rate-limit errors
                    if (state.reason.contains("rate limit", ignoreCase = true)) return@forEach
                    val app = byKey[appId] ?: return@forEach
                    apkDownloadManager.retryApp(app)
                }
                is ApkDownloadState.Resolving -> {
                    // Stuck in resolving: the coroutine likely died; retry
                    val app = byKey[appId] ?: return@forEach
                    apkDownloadManager.retryApp(app)
                }
                else -> {}
            }
        }
    }

    fun checkInstallResult(appId: String) {
        val state = apkDownloadManager.states.value[appId] ?: return
        if (state !is ApkDownloadState.Installing) return
        if (isAppInstalled(appId)) {
            apkDownloadManager.markInstalled(appId)
        } else {
            val file = apkDownloadManager.downloadDir.listFiles()?.firstOrNull { it.name.startsWith("${appId}_") }
            if (file != null) {
                apkDownloadManager.markReady(appId, file, null)
            }
        }
    }

    // --- Per-row actions ------------------------------------------------------

    fun openSource(app: RecommendedApp, currentDisplayId: Int): Boolean =
        launchView(Uri.parse(app.sourceUrl), currentDisplayId)

    sealed class ObtainiumLaunchResult {
        object Success : ObtainiumLaunchResult()
        object ObtainiumMissing : ObtainiumLaunchResult()
    }

    fun openObtainium(app: RecommendedApp, currentDisplayId: Int): ObtainiumLaunchResult {
        // Build a single-app obtainium:// deeplink on demand from the raw entry.
        val encoded = URLEncoder.encode(app.entryJson, "UTF-8")
        val uri = Uri.parse("obtainium://app/$encoded")
        val ok = launchView(uri, currentDisplayId)
        return if (ok) ObtainiumLaunchResult.Success else ObtainiumLaunchResult.ObtainiumMissing
    }

    fun openGoogle(app: RecommendedApp, currentDisplayId: Int): Boolean =
        launchView(Uri.parse(googleSearchUrl(app.name)), currentDisplayId)

    fun openYouTube(app: RecommendedApp, currentDisplayId: Int): Boolean =
        launchView(Uri.parse(youtubeSearchUrl(app.name)), currentDisplayId)

    fun downloadObtainium() {
        apkDownloadManager.downloadDirectUrl(
            appId = "dev.imranr.obtainium",
            downloadUrl = "https://github.com/ImranR98/Obtainium/releases/latest/download/app-release.apk",
            fileName = "obtainium.apk",
            version = null
        )
    }

    fun openObtainiumApp(currentDisplayId: Int): Boolean {
        val pm = context.packageManager
        val probe = Intent(Intent.ACTION_VIEW, Uri.parse("obtainium://app/"))
        val resolved = pm.resolveActivity(probe, 0) ?: return false
        val launchIntent = pm.getLaunchIntentForPackage(resolved.activityInfo.packageName)
            ?: return false
        return dualDisplayLauncher.startActivityOnOtherDisplay(launchIntent, currentDisplayId)
    }

    private fun launchView(uri: Uri, currentDisplayId: Int): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, uri)
        return dualDisplayLauncher.startActivityOnOtherDisplay(intent, currentDisplayId)
    }

    private fun matchesSearch(app: RecommendedApp, query: String): Boolean {
        if (query.isBlank()) return true
        val q = query.trim().lowercase()
        return app.name.lowercase().contains(q) || app.about.lowercase().contains(q)
    }

    companion object {
        fun googleSearchUrl(name: String): String =
            "https://www.google.com/search?udm=14&q=" + URLEncoder.encode("$name android setup guide", "UTF-8")

        fun youtubeSearchUrl(name: String): String =
            "https://www.youtube.com/results?search_query=" + URLEncoder.encode("$name android setup guide", "UTF-8")
    }
}
