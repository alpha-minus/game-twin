package com.twinlore.app.ui.screens.guide

import android.webkit.WebView
import com.twinlore.app.data.LandingEntry
import com.twinlore.app.domain.SavedTab
import com.twinlore.app.domain.ScrollMarker
import com.twinlore.app.domain.ScrollRestoreData
import com.twinlore.app.ui.screens.SaveProgress
import com.twinlore.app.ui.screens.TocEntry
import kotlinx.coroutines.Job
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.Stable

/**
 * Flat mutable state for GuideScreen. Guide-only — no Browser fields.
 */
@Stable
class GuideUiState {
    // Mode: picker or viewer
    val showGuidePicker: MutableState<Boolean> = mutableStateOf(false)

    // Current guide
    val currentBookmark: MutableState<SavedTab?> = mutableStateOf(null)
    val activePdfGuide: MutableState<SavedTab?> = mutableStateOf(null)
    val activeWheelGuide: MutableState<SavedTab?> = mutableStateOf(null)

    // WebView
    val webView: MutableState<WebView?> = mutableStateOf(null)

    // Asset serving (manifest for shouldInterceptRequest)
    val assetManifest: MutableState<Map<String, String>> = mutableStateOf(emptyMap())
    val assetBaseDir: MutableState<String?> = mutableStateOf(null)
    val isDataUrlLoaded: MutableState<Boolean> = mutableStateOf(false)

    // Scroll restore
    val isRestoringScroll: MutableState<Boolean> = mutableStateOf(false)
    val scrollRestoreCompletedAt: MutableState<Long> = mutableStateOf(0L)
    val pendingScrollRestore: MutableState<ScrollRestoreData?> = mutableStateOf(null)
    val scrollCaptureJob: MutableState<Job?> = mutableStateOf(null)

    // Scroll markers
    val scrollMarkers: MutableState<List<ScrollMarker>> = mutableStateOf(emptyList())
    val focusedScrollMarkerIndex: MutableState<Int?> = mutableStateOf(null)
    val showScrollMarkersBar: MutableState<Boolean> = mutableStateOf(false)
    val showScrollMarkerDeleteAllMenu: MutableState<Boolean> = mutableStateOf(false)
    val scrollMarkerLongPressed: MutableState<ScrollMarker?> = mutableStateOf(null)
    val showScrollMarkerDeleteAllConfirm: MutableState<Boolean> = mutableStateOf(false)
    val scrollMarkerDeleteAllBookmarkId: MutableState<String?> = mutableStateOf(null)
    val bookmarkToDeleteFromStar: MutableState<SavedTab?> = mutableStateOf(null)

    // Bookmark save
    val isSavingBookmark: MutableState<Boolean> = mutableStateOf(false)
    val saveBookmarkJob: MutableState<Job?> = mutableStateOf(null)
    val saveProgress: MutableState<SaveProgress?> = mutableStateOf(null)
    val showBookmarkManagementDialog: MutableState<Boolean> = mutableStateOf(false)
    val bookmarksForDialog: MutableState<List<SavedTab>?> = mutableStateOf(null)

    // Offline-save staging (Guide saves MHT directly; no bottom sheet / link path)
    val pendingSaveTitle: MutableState<String> = mutableStateOf("")
    val pendingSaveUrl: MutableState<String> = mutableStateOf("")
    val showSaveWhileLoadingConfirm: MutableState<Boolean> = mutableStateOf(false)

    // Top bar
    val showTopBar: MutableState<Boolean> = mutableStateOf(true)
    val lastInteractionTime: MutableState<Long> = mutableStateOf(System.currentTimeMillis())
    val isUrlFieldFocused: MutableState<Boolean> = mutableStateOf(false)

    // Navigation
    val urlInput: MutableState<String> = mutableStateOf("")

    // Search settings
    val appendGameName: MutableState<Boolean> = mutableStateOf(true)

    // Landing entries (needed by WebViewContent for vault://section/ links)
    val landingEntriesRef: MutableState<List<LandingEntry>> = mutableStateOf(emptyList())

    // Text FAQ conversion overlay
    val isConvertingTextFaq: MutableState<Boolean> = mutableStateOf(false)

    // Find on page
    val showFindOnPage: MutableState<Boolean> = mutableStateOf(false)
    val findQuery: MutableState<String> = mutableStateOf("")
    val findMatchesFound: MutableState<Boolean?> = mutableStateOf(null)
    val findMatchCount: MutableState<Int> = mutableStateOf(0)
    val findCurrentIndex: MutableState<Int> = mutableStateOf(0)
    val findSearchTrigger: MutableState<Int> = mutableStateOf(0)
    val findIsSearching: MutableState<Boolean> = mutableStateOf(false)
    val findOpenedWithSelection: MutableState<Boolean> = mutableStateOf(false)

    // Image context menu
    val showImageContextMenu: MutableState<Boolean> = mutableStateOf(false)
    val selectedImageUrl: MutableState<String?> = mutableStateOf(null)
    val pendingSaveImageUrl: MutableState<String?> = mutableStateOf(null)
    val isAssigningImage: MutableState<Boolean> = mutableStateOf(false)
    val assigningImageLabel: MutableState<String> = mutableStateOf("")

    // Guide-mode image zoom viewer (tap on inlined <img> in a saved guide).
    // Non-null = open; the value is either the manifest-resolved `file://…` path
    // or the original HTTP URL as a fallback.
    val guideImageZoomModel: MutableState<String?> = mutableStateOf(null)

    // TOC sidebar
    val tocEntries: MutableState<List<TocEntry>> = mutableStateOf(emptyList())
    val showTocDrawer: MutableState<Boolean> = mutableStateOf(false)
    val textFaqTocActive: MutableState<Boolean> = mutableStateOf(false)
    val pendingScrollToContent: MutableState<Boolean> = mutableStateOf(false)
    val tocBasicView: MutableState<Boolean> = mutableStateOf(true)

    // Map bottom sheet
    val showMapBottomSheet: MutableState<Boolean> = mutableStateOf(false)
    val mapUpdateVersion: MutableState<Int> = mutableStateOf(0)
    val mapZoom: MutableState<Float> = mutableStateOf(1f)
    val mapOffsetX: MutableState<Float> = mutableStateOf(0f)
    val mapOffsetY: MutableState<Float> = mutableStateOf(0f)

    // RetroAchievements drawer
    val showAchievementDrawer: MutableState<Boolean> = mutableStateOf(false)

    // Toast
    val toastMessage: MutableState<String?> = mutableStateOf(null)
    val toastShowCheckmark: MutableState<Boolean> = mutableStateOf(false)

}
