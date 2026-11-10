package com.twinlore.app.ui.screens.browser

/**
 * State emitted by [com.twinlore.app.data.library.PageGroupSaveCoordinator]
 * as the bulk-save queue runs. The selection / progress sheet collects this
 * as a [kotlinx.coroutines.flow.StateFlow] and renders accordingly.
 */
sealed class BulkSaveState {
    object Idle : BulkSaveState()

    /** Robots.txt fetch + offscreen WebView warmup. */
    data class Preparing(val total: Int) : BulkSaveState()

    /**
     * Currently working on chapter at [index] (0-based) out of [total].
     * [completed] grows as each chapter finishes so the progress sheet can
     * render a per-chapter checklist.
     */
    data class Saving(
        val index: Int,
        val total: Int,
        val currentTitle: String,
        val completed: List<ChapterResult>
    ) : BulkSaveState()

    data class Done(
        val saved: Int,
        val skipped: Int,
        val failed: List<ChapterResult>
    ) : BulkSaveState()

    data class Cancelled(val savedSoFar: Int) : BulkSaveState()
}

data class ChapterResult(
    val url: String,
    val title: String,
    val outcome: Outcome,
    val bytes: Long? = null,
    val failureReason: String? = null,
    val savedTabId: String? = null
)

enum class Outcome {
    SAVED,
    SKIPPED_DUPLICATE,
    FAILED_HTTP,
    FAILED_LOAD_TIMEOUT,
    BLOCKED_ROBOTS;

    fun isFailure(): Boolean = this == FAILED_HTTP || this == FAILED_LOAD_TIMEOUT
}
