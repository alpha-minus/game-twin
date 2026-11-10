package com.twinlore.app.ui.screens.pdf

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twinlore.app.data.SavedTabRepository
import com.twinlore.app.data.library.ResourceResolver
import com.twinlore.app.domain.PdfPageBookmark
import com.twinlore.app.domain.SavedTab
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import org.json.JSONArray
import java.io.File
import javax.inject.Inject

private const val TAG = "PdfGuideVM"
private const val LOAD_WATCHDOG_MS = 10_000L

@HiltViewModel
class PdfGuideViewModel @Inject constructor(
    private val savedTabRepository: SavedTabRepository,
    private val resourceResolver: ResourceResolver,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _currentPage = MutableStateFlow(0)
    val currentPage: StateFlow<Int> = _currentPage.asStateFlow()

    private val _totalPages = MutableStateFlow(0)
    val totalPages: StateFlow<Int> = _totalPages.asStateFlow()

    private val _outline = MutableStateFlow<List<PdfOutlineEntry>>(emptyList())
    val outline: StateFlow<List<PdfOutlineEntry>> = _outline.asStateFlow()

    private val _findCurrentMatch = MutableStateFlow(0)
    val findCurrentMatch: StateFlow<Int> = _findCurrentMatch.asStateFlow()

    private val _findTotalMatches = MutableStateFlow(0)
    val findTotalMatches: StateFlow<Int> = _findTotalMatches.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _documentLoaded = MutableStateFlow(false)
    val documentLoaded: StateFlow<Boolean> = _documentLoaded.asStateFlow()

    private val _pageBookmarks = MutableStateFlow<List<PdfPageBookmark>>(emptyList())
    val pageBookmarks: StateFlow<List<PdfPageBookmark>> = _pageBookmarks.asStateFlow()

    private val _hasExtractableText = MutableStateFlow(true)
    val hasExtractableText: StateFlow<Boolean> = _hasExtractableText.asStateFlow()

    private var saveStateJob: Job? = null
    private var loadWatchdogJob: Job? = null
    private var guideId: String? = null
    private var vaultId: String? = null

    fun onPageChanged(page: Int, total: Int) {
        _currentPage.value = page
        _totalPages.value = total
        debounceSaveState()
    }

    fun onDocumentLoaded(total: Int) {
        Log.d(TAG, "onDocumentLoaded guideId=$guideId total=$total (isLoading true→false)")
        _totalPages.value = total
        _documentLoaded.value = true
        _isLoading.value = false
        loadWatchdogJob?.cancel()
        loadWatchdogJob = null
    }

    fun onFindResult(current: Int, total: Int) {
        _findCurrentMatch.value = current
        _findTotalMatches.value = total
    }

    fun onScrollPositionChanged(page: Int, offset: Float) {
        _currentPage.value = page
        debounceSaveState()
    }

    fun onOutlineReady(json: String) {
        try {
            val arr = JSONArray(json)
            _outline.value = parseOutlineArray(arr)
        } catch (_: Exception) {
            _outline.value = emptyList()
        }
    }

    fun onTextProbeResult(hasText: Boolean) {
        _hasExtractableText.value = hasText
    }

    private fun parseOutlineArray(arr: JSONArray): List<PdfOutlineEntry> {
        val result = mutableListOf<PdfOutlineEntry>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val title = obj.optString("title", "")
            val pageNumber = obj.optInt("pageNumber", 0)
            val childrenArr = obj.optJSONArray("children")
            val children = if (childrenArr != null) parseOutlineArray(childrenArr) else emptyList()
            if (title.isNotBlank()) {
                result.add(PdfOutlineEntry(title, pageNumber, children))
            }
        }
        return result
    }

    fun setGuide(guide: SavedTab) {
        Log.d(TAG, "setGuide id=${guide.id} page=${guide.lastPdfPage} (isLoading→true)")
        guideId = guide.id
        vaultId = guide.vaultId
        _currentPage.value = guide.lastPdfPage
        _totalPages.value = 0
        _documentLoaded.value = false
        _isLoading.value = true
        _outline.value = emptyList()
        _hasExtractableText.value = true
        loadPageBookmarks()
        // Safety net: if onDocumentLoaded never fires (WebView GPU crash, pdf.js init
        // failure, etc.) force-clear the spinner so the user is never stuck forever.
        loadWatchdogJob?.cancel()
        val watchdogForGuide = guide.id
        loadWatchdogJob = viewModelScope.launch {
            delay(LOAD_WATCHDOG_MS)
            if (guideId == watchdogForGuide && _isLoading.value && !_documentLoaded.value) {
                Log.w(TAG, "Load watchdog fired for guide=$watchdogForGuide after ${LOAD_WATCHDOG_MS}ms — forcing isLoading=false")
                _isLoading.value = false
            }
        }
    }

    /**
     * Writes the page now. Called from the viewer's dispose, and in the
     * Always on Top panel that dispose is followed on the same call stack by
     * the window's view model store being cleared, which cancels this scope.
     * The write is not cancellable for that reason: cancellable, it was
     * cancelled before the IO thread reached it, and the page the panel was
     * left on was lost every time.
     */
    fun savePdfState(guideId: String) {
        viewModelScope.launch {
            withContext(NonCancellable) {
                savedTabRepository.updatePdfState(
                    guideId = guideId,
                    lastPage = _currentPage.value,
                    scrollOffset = 0f
                )
            }
        }
    }

    /**
     * The page the store has for this guide, read fresh. Null until a guide
     * is set. For the app's viewer coming back from Always on Top, where
     * the panel's own copy of this viewer wrote the page it was left on.
     */
    suspend fun storedPage(): Int? {
        val gid = guideId ?: return null
        return savedTabRepository.getTabById(gid)?.lastPdfPage
    }

    private fun debounceSaveState() {
        saveStateJob?.cancel()
        val gid = guideId ?: return
        saveStateJob = viewModelScope.launch {
            // The wait is cancellable, so a newer page supersedes this one;
            // the write is not, for the reason savePdfState gives.
            delay(500)
            withContext(NonCancellable) {
                savedTabRepository.updatePdfState(
                    guideId = gid,
                    lastPage = _currentPage.value,
                    scrollOffset = 0f
                )
            }
        }
    }

    suspend fun loadPdfFilePath(guide: SavedTab): Pair<String, Int>? = withContext(Dispatchers.IO) {
        // PdfRenderer requires a real filesystem path. For new-flow saves (resourceId != null)
        // that may live behind a content:// URI in the SAF home folder, we copy bytes into
        // cacheDir/pdf_source/<guideId>.pdf and hand back that path. The cache file is reused
        // when present with non-zero length so repeated page changes don't re-read SAF.
        val resourceId = guide.resourceId
        if (resourceId == null) {
            Log.w(TAG, "loadPdfFilePath: guide=${guide.id} has no resourceId; legacy storage path no longer supported")
            return@withContext null
        }
        val uri = resourceResolver.resolve(resourceId)
        if (uri == null) {
            Log.w(TAG, "loadPdfFilePath: resourceResolver returned null for resourceId=$resourceId guide=${guide.id}")
            return@withContext null
        }
        val path: String? = when (uri.scheme) {
            "file" -> uri.path
            "content" -> {
                val tmp = File(context.cacheDir, "pdf_source/${guide.id}.pdf")
                tmp.parentFile?.mkdirs()
                if (!tmp.exists() || tmp.length() == 0L) {
                    val copied = context.contentResolver.openInputStream(uri)?.use { input ->
                        tmp.outputStream().use { input.copyTo(it) }
                        true
                    } ?: false
                    if (!copied) {
                        Log.w(TAG, "loadPdfFilePath: failed to open content uri=$uri for guide=${guide.id}")
                        return@withContext null
                    }
                }
                tmp.absolutePath
            }
            else -> null
        }
        if (path == null) {
            Log.w(TAG, "loadPdfFilePath: could not resolve file path for guide=${guide.id}")
            return@withContext null
        }
        val file = File(path)
        if (!file.exists()) {
            Log.w(TAG, "loadPdfFilePath: file does not exist at $path for guide=${guide.id}")
            return@withContext null
        }
        val initialPage = guide.lastPdfPage.coerceAtLeast(0)
        Pair(path, initialPage)
    }

    private fun loadPageBookmarks() {
        val vid = vaultId ?: return
        val gid = guideId ?: return
        viewModelScope.launch {
            _pageBookmarks.value = savedTabRepository.getPdfPageBookmarks(vid, gid)
        }
    }

    fun addPageBookmark(name: String, pageNumber: Int) {
        val vid = vaultId ?: return
        val gid = guideId ?: return
        viewModelScope.launch {
            val result = savedTabRepository.addPdfPageBookmark(vid, gid, name, pageNumber)
            if (result is com.twinlore.app.domain.Result.Success) {
                _pageBookmarks.value = savedTabRepository.getPdfPageBookmarks(vid, gid)
            }
        }
    }

    fun deletePageBookmark(bookmarkId: String) {
        val vid = vaultId ?: return
        val gid = guideId ?: return
        viewModelScope.launch {
            savedTabRepository.deletePdfPageBookmark(vid, gid, bookmarkId)
            _pageBookmarks.value = savedTabRepository.getPdfPageBookmarks(vid, gid)
        }
    }

    override fun onCleared() {
        super.onCleared()
        loadWatchdogJob?.cancel()
        saveStateJob?.cancel()
    }
}
