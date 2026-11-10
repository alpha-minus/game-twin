package com.twinlore.app.ui.screens.browser

import android.webkit.WebView
import com.twinlore.app.domain.SavedTab
import com.twinlore.app.ui.screens.SaveProgress
import com.twinlore.app.data.LandingEntry
import kotlinx.coroutines.Job
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.Stable

/**
 * Flat mutable state for BrowserScreen. Minimal — no scroll restore, no TOC, no markers.
 */
@Stable
class BrowserUiState {
    // WebView
    val webView: MutableState<WebView?> = mutableStateOf(null)

    // URL
    val urlInput: MutableState<String> = mutableStateOf("")

    // Top bar
    val showTopBar: MutableState<Boolean> = mutableStateOf(true)
    val isUrlFieldFocused: MutableState<Boolean> = mutableStateOf(false)

    // Bookmark save (save page as guide)
    val isSavingBookmark: MutableState<Boolean> = mutableStateOf(false)
    val saveBookmarkJob: MutableState<Job?> = mutableStateOf(null)
    val saveProgress: MutableState<SaveProgress?> = mutableStateOf(null)
    val showSaveTypeSheet: MutableState<Boolean> = mutableStateOf(false)
    val pendingSaveTitle: MutableState<String> = mutableStateOf("")
    val pendingSaveUrl: MutableState<String> = mutableStateOf("")
    val pendingSaveZoom: MutableState<Float> = mutableStateOf(1f)
    val showSaveWhileLoadingConfirm: MutableState<Boolean> = mutableStateOf(false)
    /** Set after a successful single-page MHT/HTML/TXT save so the overlay can
     *  surface an "Open" button. Cleared when user taps Open or Close. */
    val savedBookmarkForOverlay: MutableState<SavedTab?> = mutableStateOf(null)

    // Bulk save (Save All Pages)
    val showBulkSaveProgressSheet: MutableState<Boolean> = mutableStateOf(false)
    val bulkSaveDetection: MutableState<com.twinlore.app.data.library.DetectionResult?> = mutableStateOf(null)
    val bulkSaveChapterCount: MutableState<Int> = mutableStateOf(0)

    // Image context menu (assign as box art / map)
    val showImageContextMenu: MutableState<Boolean> = mutableStateOf(false)
    val selectedImageUrl: MutableState<String?> = mutableStateOf(null)
    val pendingSaveImageUrl: MutableState<String?> = mutableStateOf(null)
    val isAssigningImage: MutableState<Boolean> = mutableStateOf(false)
    val assigningImageLabel: MutableState<String> = mutableStateOf("")

    // Link context menu (long-press on <a>) — Browser mode only.
    val selectedLinkUrl: MutableState<String?> = mutableStateOf(null)
    val selectedLinkText: MutableState<String?> = mutableStateOf(null)
    val showLinkContextMenu: MutableState<Boolean> = mutableStateOf(false)

    // PDF download
    val pdfDownloadInProgress: MutableState<Boolean> = mutableStateOf(false)
    /**
     * Job tracking for the in-flight PDF download so it can be cancelled from
     * the PdfDownloadOverlay's Cancel button.
     */
    val pdfDownloadJob: MutableState<Job?> = mutableStateOf(null)
    /**
     * When non-null, the PDF download finished and the user is being prompted
     * with an Open / Close affordance over the Browser tab. Set by the success
     * branch in startDownload; cleared by tap on Open / Close.
     */
    val pdfDownloadDoneBookmark: MutableState<SavedTab?> = mutableStateOf(null)
    /**
     * True while the in-flight download routed through the PdfDownloadOverlay is
     * an HTML-bundle zip (code wheel / htmlzip) rather than a PDF — drives the
     * overlay's "Downloading…/Saved" labels and the Open action's routing.
     */
    val downloadIsBundle: MutableState<Boolean> = mutableStateOf(false)

    // Image download
    /**
     * Image download (long-press Save Image or implicit content-type download).
     * Tracked the same way as the PDF flow so we can Cancel mid-download and
     * surface an Open/Close banner on success.
     */
    val imageDownloadInProgress: MutableState<Boolean> = mutableStateOf(false)
    val imageDownloadJob: MutableState<Job?> = mutableStateOf(null)
    /**
     * When non-null, the image download finished. Surface an Open / Close banner
     * whose Open switches to the Map tab and selects this map. Cleared on Open / Close.
     */
    val imageDownloadDoneMapName: MutableState<String?> = mutableStateOf(null)

    // Toast
    val toastMessage: MutableState<String?> = mutableStateOf(null)
    val toastShowCheckmark: MutableState<Boolean> = mutableStateOf(false)

    // Search / landing
    val appendGameName: MutableState<Boolean> = mutableStateOf(true)
    val focusSearchBarTrigger: MutableState<Boolean> = mutableStateOf(false)
    val landingEntriesRef: MutableState<List<LandingEntry>> = mutableStateOf(emptyList())

    // Loading state
    val isUserNavigation: MutableState<Boolean> = mutableStateOf(false)

    // Find on page (mirrors GuideUiState's findOnPage states; no scroll-marker save in browser).
    val showFindOnPage: MutableState<Boolean> = mutableStateOf(false)
    val findQuery: MutableState<String> = mutableStateOf("")
    val findMatchesFound: MutableState<Boolean?> = mutableStateOf(null)
    val findMatchCount: MutableState<Int> = mutableStateOf(0)
    val findCurrentIndex: MutableState<Int> = mutableStateOf(0)
    val findSearchTrigger: MutableState<Int> = mutableStateOf(0)
    val findIsSearching: MutableState<Boolean> = mutableStateOf(false)
}
