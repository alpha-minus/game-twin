package com.twinlore.app.domain

/**
 * Domain model for an open browser tab inside a vault session.
 *
 * Distinct from [SavedTab], which models bookmarks / offline content.
 * Browser tabs are transient open URLs scoped per vault; persisted as
 * URL + title + favicon + scrollY so they reappear on next vault open.
 */
data class BrowserTab(
    val id: String,
    val vaultId: String,
    val url: String,
    val title: String,
    val faviconBytes: ByteArray?,
    val scrollY: Int = 0,
    val tabOrder: Int = 0,
    val lastAccessed: Long,
    val createdAt: Long,
    /**
     * Chronological back/forward stack for this tab; always non-empty in
     * persisted rows. `tab.url` is a denormalized cache of
     * `history[historyIndex].url`, kept in sync by [BrowserTabRepository].
     */
    val history: List<HistoryEntry> = emptyList(),
    val historyIndex: Int = 0
) {
    val displayTitle: String
        get() = when {
            title.isNotBlank() -> title
            url.isNotBlank() -> runCatching { java.net.URL(url).host }.getOrNull() ?: url.take(50)
            else -> "New Tab"
        }

    val canGoBack: Boolean get() = historyIndex > 0
    val canGoForward: Boolean get() = historyIndex < history.lastIndex

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BrowserTab) return false
        if (id != other.id) return false
        if (vaultId != other.vaultId) return false
        if (url != other.url) return false
        if (title != other.title) return false
        if (faviconBytes != null) {
            if (other.faviconBytes == null) return false
            if (!faviconBytes.contentEquals(other.faviconBytes)) return false
        } else if (other.faviconBytes != null) return false
        if (scrollY != other.scrollY) return false
        if (tabOrder != other.tabOrder) return false
        if (lastAccessed != other.lastAccessed) return false
        if (createdAt != other.createdAt) return false
        if (history != other.history) return false
        if (historyIndex != other.historyIndex) return false
        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + vaultId.hashCode()
        result = 31 * result + url.hashCode()
        result = 31 * result + title.hashCode()
        result = 31 * result + (faviconBytes?.contentHashCode() ?: 0)
        result = 31 * result + scrollY
        result = 31 * result + tabOrder
        result = 31 * result + lastAccessed.hashCode()
        result = 31 * result + createdAt.hashCode()
        result = 31 * result + history.hashCode()
        result = 31 * result + historyIndex
        return result
    }
}
