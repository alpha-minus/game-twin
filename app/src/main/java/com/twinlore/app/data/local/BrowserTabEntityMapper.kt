package com.twinlore.app.data.local

import com.twinlore.app.domain.BrowserTab
import com.twinlore.app.domain.HistoryEntry

object BrowserTabEntityMapper {
    fun toDomain(entity: BrowserTabEntity): BrowserTab {
        val parsed = BrowserTabHistorySerializer.fromJson(
            entity.historyJson,
            fallbackUrl = entity.url,
            fallbackTitle = entity.title
        )
        // Defensive: clamp the persisted index to the parsed list bounds so
        // a corrupted/stale historyIndex never produces an out-of-range value.
        val clampedIndex = entity.historyIndex.coerceIn(0, parsed.lastIndex)
        return BrowserTab(
            id = entity.id,
            vaultId = entity.vaultId,
            url = entity.url,
            title = entity.title,
            faviconBytes = entity.faviconBytes,
            scrollY = entity.scrollY,
            tabOrder = entity.tabOrder,
            lastAccessed = entity.lastAccessed,
            createdAt = entity.createdAt,
            history = parsed,
            historyIndex = clampedIndex
        )
    }

    fun toEntity(tab: BrowserTab): BrowserTabEntity {
        // Always persist a non-empty history. If a caller hands us a tab
        // with empty history (e.g. legacy domain construction), synthesize
        // a single entry from (url, title) so the row matches the invariant.
        val nonEmpty = tab.history.ifEmpty { listOf(HistoryEntry(tab.url, tab.title)) }
        val safeIndex = tab.historyIndex.coerceIn(0, nonEmpty.lastIndex)
        return BrowserTabEntity(
            id = tab.id,
            vaultId = tab.vaultId,
            url = tab.url,
            title = tab.title,
            faviconBytes = tab.faviconBytes,
            scrollY = tab.scrollY,
            tabOrder = tab.tabOrder,
            historyJson = BrowserTabHistorySerializer.toJson(nonEmpty),
            historyIndex = safeIndex,
            lastAccessed = tab.lastAccessed,
            createdAt = tab.createdAt
        )
    }

    fun toDomainList(entities: List<BrowserTabEntity>): List<BrowserTab> =
        entities.map(::toDomain)
}
