package com.twinlore.app.data

import com.twinlore.app.data.local.ChecklistItemDao
import com.twinlore.app.data.local.ChecklistItemEntityMapper
import com.twinlore.app.data.local.ChecklistTagDao
import com.twinlore.app.data.local.ChecklistTabDao
import com.twinlore.app.data.local.ChecklistSectionDao
import com.twinlore.app.data.local.ChecklistTabEntity
import com.twinlore.app.data.local.ChecklistSectionEntity
import com.twinlore.app.data.local.ChecklistTabEntityMapper
import com.twinlore.app.data.local.ChecklistSectionEntityMapper
import com.twinlore.app.ui.theme.ChecklistColorPalette
import com.twinlore.app.data.local.VaultDatabase
import com.twinlore.app.domain.ChecklistItem
import com.twinlore.app.domain.ChecklistTab
import com.twinlore.app.domain.ChecklistSection
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

data class ImportResult(
    val itemsAdded: Int,
    val itemsSkipped: Int,
    val tabsCreated: Int,
    val sectionsCreated: Int,
    /** Id of the last tab the import wrote into — used by the UI to switch
     *  the active tab to whatever the user just imported, so they land on
     *  their freshly added content rather than the tab they were on before. */
    val lastTabId: String? = null,
) {
    val itemsImported: Int get() = itemsAdded
}

@Singleton
class ChecklistRepository @Inject constructor(
    private val database: VaultDatabase,
    private val tagDao: ChecklistTagDao,
    private val itemDao: ChecklistItemDao,
    private val tabDao: ChecklistTabDao,
    private val sectionDao: ChecklistSectionDao,
) {
    companion object {
        const val MAX_ITEM_TITLE_LENGTH = 400
    }

    // --- Item operations ---

    fun getItemsForVault(vaultId: String): Flow<List<ChecklistItem>> =
        itemDao.getItemsForVault(vaultId).map { ChecklistItemEntityMapper.toDomainList(it) }

    suspend fun toggleItemCompletion(itemId: String) {
        val entity = itemDao.getItemById(itemId) ?: return
        itemDao.update(entity.copy(
            completed = !entity.completed,
            updatedAt = System.currentTimeMillis()
        ))
    }

    suspend fun renameItem(itemId: String, newTitle: String) {
        val existing = itemDao.getItemById(itemId) ?: return
        val trimmed = newTitle.trim().take(MAX_ITEM_TITLE_LENGTH)
        if (trimmed.isBlank() || trimmed == existing.title) return
        itemDao.update(existing.copy(title = trimmed, updatedAt = System.currentTimeMillis()))
    }

    suspend fun setAllItemsCompletedInSection(sectionId: String, completed: Boolean) {
        val now = System.currentTimeMillis()
        database.withTransaction {
            val items = itemDao.getItemsForSectionOnce(sectionId)
            for (item in items) {
                if (item.completed != completed) {
                    itemDao.update(item.copy(completed = completed, updatedAt = now))
                }
            }
        }
    }

    /** Flip `completed` on a specific set of items — used to honor active
     *  filters so "Mark all shown items" doesn't touch hidden ones. */
    suspend fun setItemsCompleted(itemIds: List<String>, completed: Boolean) {
        if (itemIds.isEmpty()) return
        val now = System.currentTimeMillis()
        database.withTransaction {
            for (id in itemIds) {
                val existing = itemDao.getItemById(id) ?: continue
                if (existing.completed != completed) {
                    itemDao.update(existing.copy(completed = completed, updatedAt = now))
                }
            }
        }
    }

    suspend fun deleteItem(itemId: String) {
        itemDao.deleteById(itemId)
    }

    suspend fun deleteItems(itemIds: List<String>) {
        database.withTransaction {
            itemIds.chunked(999).forEach { chunk ->
                itemDao.deleteByIds(chunk)
            }
        }
    }

    /** Delete items and then remove any sections in [tabId] left with zero items. */
    suspend fun deleteItemsAndEmptySections(itemIds: List<String>, tabId: String) {
        database.withTransaction {
            itemIds.chunked(999).forEach { chunk ->
                itemDao.deleteByIds(chunk)
            }
            val sections = sectionDao.getSectionsForTabOnce(tabId)
            for (section in sections) {
                if (itemDao.getItemsForSectionOnce(section.id).isEmpty()) {
                    sectionDao.deleteById(section.id)
                }
            }
        }
    }

    /** Delete specific items and specific sections (with their remaining items). */
    suspend fun deleteItemsAndSections(itemIds: List<String>, sectionIds: List<String>) {
        database.withTransaction {
            itemIds.chunked(999).forEach { chunk ->
                itemDao.deleteByIds(chunk)
            }
            for (sectionId in sectionIds) {
                sectionDao.deleteById(sectionId)
            }
        }
    }

    /**
     * Parses `markdown` and appends to the vault. Falls back to `activeTabId` when the
     * markdown has no `#` heading. Single @Transaction — all or nothing.
     */
    suspend fun importFromMarkdown(
        vaultId: String,
        activeTabId: String?,
        markdown: String
    ): ImportResult {
        val parsed = com.twinlore.app.data.checklist.MarkdownChecklistParser().parse(markdown)
        if (parsed.tabs.isEmpty()) return ImportResult(0, 0, 0, 0)

        return database.withTransaction {
            var itemsAdded = 0
            var itemsSkipped = 0
            var tabsCreated = 0
            var sectionsCreated = 0
            var lastTabId: String? = null

            for (parsedTab in parsed.tabs) {
                val tab: ChecklistTab = when {
                    parsedTab.name != null -> {
                        // Reuse an existing tab with the same name (case-insensitive) to enable dedup.
                        val existingTabs = tabDao.getTabsForVaultOnce(vaultId)
                        val match = existingTabs.firstOrNull {
                            it.name.equals(parsedTab.name, ignoreCase = true)
                        }
                        if (match != null) {
                            ChecklistTabEntityMapper.toDomain(match)
                        } else {
                            val created = createTab(vaultId, parsedTab.name, nextUnusedColorIndex(vaultId))
                            tabsCreated++
                            created
                        }
                    }
                    activeTabId != null -> {
                        val existing = tabDao.getTabById(activeTabId)
                        if (existing != null) ChecklistTabEntityMapper.toDomain(existing)
                        else {
                            val created = createTab(vaultId, "Tab 1", nextUnusedColorIndex(vaultId))
                            tabsCreated++
                            created
                        }
                    }
                    else -> {
                        val created = createTab(vaultId, "Tab 1", nextUnusedColorIndex(vaultId))
                        tabsCreated++
                        created
                    }
                }
                lastTabId = tab.id

                for (parsedSection in parsedTab.sections) {
                    // Reuse an existing section with the same name (case-insensitive) within the tab.
                    val existingSections = sectionDao.getSectionsForTabOnce(tab.id)
                    val existingSection = existingSections.firstOrNull {
                        it.name.equals(parsedSection.name, ignoreCase = true)
                    }
                    val section = if (existingSection != null) {
                        ChecklistSectionEntityMapper.toDomain(existingSection)
                    } else {
                        sectionsCreated++
                        createSection(vaultId, tab.id, parsedSection.name)
                    }

                    for (parsedItem in parsedSection.items) {
                        val duplicate = itemDao.findByTitleAndSection(vaultId, section.id, parsedItem.title)
                        if (duplicate != null) {
                            itemsSkipped++
                            continue
                        }
                        createItemInSection(
                            vaultId = vaultId,
                            sectionId = section.id,
                            title = parsedItem.title,
                            completed = parsedItem.completed
                        )
                        itemsAdded++
                    }
                }
            }

            ImportResult(itemsAdded, itemsSkipped, tabsCreated, sectionsCreated, lastTabId)
        }
    }

    private suspend fun nextUnusedColorIndex(vaultId: String): Int {
        val used = tabDao.getTabsForVaultOnce(vaultId).map { it.color }.toSet()
        return (0 until 12).firstOrNull { it !in used } ?: (tabDao.countForVault(vaultId) % 12)
    }

    // --- Tab operations ---

    fun getTabsForVault(vaultId: String): Flow<List<ChecklistTab>> =
        tabDao.getTabsForVault(vaultId).map { list -> ChecklistTabEntityMapper.toDomainList(list) }

    suspend fun getTabsForVaultOnce(vaultId: String): List<ChecklistTab> =
        ChecklistTabEntityMapper.toDomainList(tabDao.getTabsForVaultOnce(vaultId))

    suspend fun createTab(vaultId: String, name: String, colorIndex: Int): ChecklistTab {
        val now = System.currentTimeMillis()
        val sortOrder = (tabDao.getMaxSortOrder(vaultId) ?: -1) + 1
        val entity = ChecklistTabEntity(
            id = java.util.UUID.randomUUID().toString(),
            vaultId = vaultId,
            name = name.take(50),
            color = colorIndex.coerceIn(0, 11),
            sortOrder = sortOrder,
            createdAt = now,
            updatedAt = now
        )
        tabDao.insert(entity)
        return ChecklistTabEntityMapper.toDomain(entity)
    }

    suspend fun renameTab(tabId: String, name: String) {
        val existing = tabDao.getTabById(tabId) ?: return
        tabDao.update(existing.copy(name = name.take(50), updatedAt = System.currentTimeMillis()))
    }

    suspend fun setTabColor(tabId: String, colorIndex: Int) {
        val existing = tabDao.getTabById(tabId) ?: return
        tabDao.update(existing.copy(color = colorIndex.coerceIn(0, 11), updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteTab(tabId: String) {
        tabDao.deleteById(tabId)
    }

    suspend fun reorderTabs(vaultId: String, orderedIds: List<String>) {
        database.withTransaction {
            val now = System.currentTimeMillis()
            orderedIds.forEachIndexed { index, id ->
                val existing = tabDao.getTabById(id) ?: return@forEachIndexed
                if (existing.vaultId == vaultId) {
                    tabDao.update(existing.copy(sortOrder = index, updatedAt = now))
                }
            }
        }
    }

    // --- Section operations ---

    fun getSectionsForTab(tabId: String): Flow<List<ChecklistSection>> =
        sectionDao.getSectionsForTab(tabId).map { list -> ChecklistSectionEntityMapper.toDomainList(list) }

    fun getSectionsForVault(vaultId: String): Flow<List<ChecklistSection>> =
        sectionDao.getSectionsForVault(vaultId).map { list -> ChecklistSectionEntityMapper.toDomainList(list) }

    suspend fun createSection(vaultId: String, tabId: String, name: String, expanded: Boolean = false): ChecklistSection {
        val now = System.currentTimeMillis()
        val sortOrder = (sectionDao.getMaxSortOrder(tabId) ?: -1) + 1
        val entity = ChecklistSectionEntity(
            id = java.util.UUID.randomUUID().toString(),
            vaultId = vaultId,
            tabId = tabId,
            name = name.take(50),
            expanded = expanded,
            sortOrder = sortOrder,
            createdAt = now,
            updatedAt = now
        )
        sectionDao.insert(entity)
        return ChecklistSectionEntityMapper.toDomain(entity)
    }

    suspend fun renameSection(sectionId: String, name: String) {
        val existing = sectionDao.getSectionById(sectionId) ?: return
        sectionDao.update(existing.copy(name = name.take(50), updatedAt = System.currentTimeMillis()))
    }

    suspend fun setSectionExpanded(sectionId: String, expanded: Boolean) {
        val existing = sectionDao.getSectionById(sectionId) ?: return
        sectionDao.update(existing.copy(expanded = expanded, updatedAt = System.currentTimeMillis()))
    }

    suspend fun setAllSectionsExpandedInTab(tabId: String, expanded: Boolean) {
        sectionDao.setAllExpandedForTab(tabId, expanded, System.currentTimeMillis())
    }

    suspend fun deleteSection(sectionId: String) {
        sectionDao.deleteById(sectionId)
    }

    /**
     * Promote every item inside [sectionId] to its own section (item title → section name).
     * New sections are inserted at the original section's sort-order position.
     * All later sections shift down. The original section is then deleted.
     */
    suspend fun promoteItemsToSections(sectionId: String) {
        database.withTransaction {
            val section = sectionDao.getSectionById(sectionId) ?: return@withTransaction
            val items = itemDao.getItemsForSectionOnce(sectionId)
                .sortedBy { it.sortOrder }
            if (items.isEmpty()) {
                sectionDao.deleteById(sectionId)
                return@withTransaction
            }
            val now = System.currentTimeMillis()
            val baseSortOrder = section.sortOrder

            // Shift all later sections to make room for the new ones
            val laterSections = sectionDao.getSectionsForTabOnce(section.tabId)
                .filter { it.sortOrder > baseSortOrder }
            for (later in laterSections) {
                sectionDao.update(
                    later.copy(
                        sortOrder = later.sortOrder + items.size - 1,
                        updatedAt = now,
                    )
                )
            }

            // Create new sections from items
            items.forEachIndexed { index, item ->
                val entity = ChecklistSectionEntity(
                    id = java.util.UUID.randomUUID().toString(),
                    vaultId = section.vaultId,
                    tabId = section.tabId,
                    name = item.title.take(50),
                    expanded = false,
                    sortOrder = baseSortOrder + index,
                    createdAt = now,
                    updatedAt = now,
                )
                sectionDao.insert(entity)
            }

            // Delete the original section (cascades items)
            sectionDao.deleteById(sectionId)
        }
    }

    suspend fun reorderSections(tabId: String, orderedIds: List<String>) {
        database.withTransaction {
            val now = System.currentTimeMillis()
            orderedIds.forEachIndexed { index, id ->
                val existing = sectionDao.getSectionById(id) ?: return@forEachIndexed
                if (existing.tabId == tabId) {
                    sectionDao.update(existing.copy(sortOrder = index, updatedAt = now))
                }
            }
        }
    }

    // --- Item operations (section-aware) ---

    fun getItemsForSection(sectionId: String): Flow<List<com.twinlore.app.domain.ChecklistItem>> =
        itemDao.getItemsForSection(sectionId).map { list ->
            ChecklistItemEntityMapper.toDomainList(list)
        }

    suspend fun createItemInSection(
        vaultId: String,
        sectionId: String,
        title: String,
        completed: Boolean = false
    ): com.twinlore.app.domain.ChecklistItem {
        val now = System.currentTimeMillis()
        val maxOrder = itemDao.getMaxSortOrderForSection(sectionId) ?: -1
        val section = sectionDao.getSectionById(sectionId)
            ?: throw IllegalStateException("Section $sectionId not found")
        val tab = tabDao.getTabById(section.tabId)
            ?: throw IllegalStateException("Tab ${section.tabId} not found")
        val legacyTag = resolveOrCreateLegacyTagForColor(vaultId, tab.color)
        val entity = com.twinlore.app.data.local.ChecklistItemEntity(
            id = java.util.UUID.randomUUID().toString(),
            vaultId = vaultId,
            tagId = legacyTag.id,
            sectionId = sectionId,
            title = title.take(200),
            completed = completed,
            sortOrder = maxOrder + 1,
            createdAt = now,
            updatedAt = now
        )
        itemDao.insert(entity)
        return ChecklistItemEntityMapper.toDomain(entity)
    }

    suspend fun reorderItems(sectionId: String, orderedIds: List<String>) {
        database.withTransaction {
            val now = System.currentTimeMillis()
            orderedIds.forEachIndexed { index, id ->
                val existing = itemDao.getItemById(id) ?: return@forEachIndexed
                if (existing.sectionId == sectionId) {
                    itemDao.update(existing.copy(sortOrder = index, updatedAt = now))
                }
            }
        }
    }

    /**
     * Legacy `tagId` is NOT NULL in the schema (kept for the follow-up migration that removes it).
     * This ensures every new item carries a valid tag matching its owning tab's color. Idempotent.
     */
    private suspend fun resolveOrCreateLegacyTagForColor(
        vaultId: String,
        paletteIndex: Int
    ): com.twinlore.app.data.local.ChecklistTagEntity {
        val legacyColor = (paletteIndex + 1).coerceIn(1, 7)
        val existing = tagDao.getTagsForVaultOnce(vaultId).firstOrNull { it.color == legacyColor }
        if (existing != null) return existing
        val now = System.currentTimeMillis()
        val nextSort = (tagDao.getMaxSortOrder(vaultId) ?: -1) + 1
        val newTag = com.twinlore.app.data.local.ChecklistTagEntity(
            id = java.util.UUID.randomUUID().toString(),
            vaultId = vaultId,
            name = ChecklistColorPalette.name((legacyColor - 1).coerceIn(0, 6)),
            color = legacyColor,
            sortOrder = nextSort,
            createdAt = now,
            updatedAt = now
        )
        tagDao.insert(newTag)
        return newTag
    }
}
