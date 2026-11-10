package com.twinlore.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twinlore.app.data.ChecklistRepository
import com.twinlore.app.data.SavedTabRepository
import com.twinlore.app.data.SettingsRepository
import com.twinlore.app.data.VaultRepository
import com.twinlore.app.data.checklist.ChecklistPromptTemplateDefaults
import com.twinlore.app.domain.ChecklistItem
import com.twinlore.app.domain.ChecklistPromptTemplate
import com.twinlore.app.domain.ChecklistSection
import com.twinlore.app.domain.ChecklistTab
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ChecklistViewModel @Inject constructor(
    private val repo: ChecklistRepository,
    private val vaultRepository: VaultRepository,
    private val settingsRepository: SettingsRepository,
    private val savedTabRepository: SavedTabRepository,
    private val checklistViewStateManager: com.twinlore.app.data.ChecklistViewStateManager,
) : ViewModel() {

    data class SectionWithItems(
        val section: ChecklistSection,
        val items: List<ChecklistItem>,
    ) {
        val completedCount: Int get() = items.count { it.completed }
        val totalCount: Int get() = items.size
    }

    data class CompletionStats(val completed: Int, val total: Int) {
        val percent: Int get() = if (total == 0) 0 else (completed * 100) / total
    }

    sealed class OneShotEvent {
        /** Plain prompt copy — just a prompt, no guide reference. Screen shows a short confirmation. */
        data class CopiedPrompt(val promptText: String) : OneShotEvent()
        /** Prompt + guide-URL reference copy. Carries the list of guide URLs the
         *  user combined into the prompt so the confirmation dialog can speak
         *  in plurals when multiple guides were attached. */
        data class CopiedPromptWithGuide(val promptText: String, val guideUrls: List<String>) : OneShotEvent()
        data class OpenBrowserSearch(val query: String) : OneShotEvent()
        data class ImportComplete(val itemsAdded: Int, val itemsSkipped: Int, val tabsCreated: Int, val sectionsCreated: Int) : OneShotEvent()
        data class Error(val message: String) : OneShotEvent()
    }

    private val vaultIdFlow = MutableStateFlow<String?>(null)

    private val vaultFlow = vaultIdFlow
        .filterNotNull()
        .flatMapLatest { id -> vaultRepository.getVaultByIdFlow(id) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val vaultName: StateFlow<String> = vaultFlow
        .map { it?.name.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    /**
     * Resource_files row id for the vault's cover artwork. Renamed from
     * `vaultArtworkPath` when the legacy file-path columns were dropped;
     * UI should resolve via `rememberResourceImage(...)`.
     */
    val vaultArtworkResourceId: StateFlow<String?> = vaultFlow
        .map { it?.artworkResourceId }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val tabs: StateFlow<List<ChecklistTab>> = vaultIdFlow
        .filterNotNull()
        .flatMapLatest { repo.getTabsForVault(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _activeTabId = MutableStateFlow<String?>(null)
    val activeTabId: StateFlow<String?> = _activeTabId.asStateFlow()

    val sectionsWithItems: StateFlow<List<SectionWithItems>> = combine(
        vaultIdFlow.filterNotNull(),
        _activeTabId.filterNotNull()
    ) { _, tabId -> tabId }
        .flatMapLatest { tabId ->
            combine(
                repo.getSectionsForTab(tabId),
                vaultIdFlow.filterNotNull().flatMapLatest { repo.getItemsForVault(it) }
            ) { sections, items ->
                sections.map { s ->
                    val sectionItems = items.filter { it.sectionId == s.id }.sortedBy { it.sortOrder }
                    SectionWithItems(s, sectionItems)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val overallCompletion: StateFlow<CompletionStats> = vaultIdFlow
        .filterNotNull()
        .flatMapLatest { repo.getItemsForVault(it) }
        .map { items -> CompletionStats(items.count { it.completed }, items.size) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, CompletionStats(0, 0))

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _pasteSheetOpen = MutableStateFlow(false)
    val pasteSheetOpen: StateFlow<Boolean> = _pasteSheetOpen.asStateFlow()

    private val _hideCompleted = MutableStateFlow(false)
    val hideCompleted: StateFlow<Boolean> = _hideCompleted.asStateFlow()

    /** Per-tab (id -> completed/total) for the whole vault, used by the tab bar + active-tab ring. */
    val completionByTabId: StateFlow<Map<String, Pair<Int, Int>>> = combine(
        tabs,
        vaultIdFlow.filterNotNull().flatMapLatest { repo.getSectionsForVault(it) },
        vaultIdFlow.filterNotNull().flatMapLatest { repo.getItemsForVault(it) },
    ) { tabList, sections, items ->
        val sectionToTab = sections.associate { it.id to it.tabId }
        val stats = HashMap<String, IntArray>() // tabId -> [completed, total]
        for (item in items) {
            val tabId = sectionToTab[item.sectionId] ?: continue
            val counts = stats.getOrPut(tabId) { IntArray(2) }
            counts[1] += 1
            if (item.completed) counts[0] += 1
        }
        tabList.associate { t ->
            val c = stats[t.id] ?: IntArray(2)
            t.id to (c[0] to c[1])
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    private val _events = Channel<OneShotEvent>(Channel.BUFFERED)
    val events: Flow<OneShotEvent> = _events.receiveAsFlow()

    /** A saved guide the user can attach to the AI prompt. Excludes guides
     *  the AI couldn't actually visit (PDFs, file:// imports without a
     *  source URL). */
    data class GuideOption(
        val id: String,
        val title: String,
        val url: String,
        val lastAccessedMillis: Long,
    )

    /** AI-referenceable saved guides for the active vault, sorted by most
     *  recently opened. Filters out PDFs and file:// imports — the AI can't
     *  retrieve those, so listing them would set up a broken expectation. */
    val availableGuides: StateFlow<List<GuideOption>> = vaultIdFlow
        .filterNotNull()
        .flatMapLatest { savedTabRepository.getTabsForVault(it) }
        .map { tabs ->
            tabs
                .filter { tab ->
                    val u = tab.url
                    u.isNotBlank() && !u.startsWith("file://") && tab.fileType != "pdf"
                }
                .sortedByDescending { it.lastAccessed.time }
                .map {
                    GuideOption(
                        id = it.id,
                        title = it.displayTitle,
                        url = it.url,
                        lastAccessedMillis = it.lastAccessed.time,
                    )
                }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** User-picked guide ids. When empty, copyPromptWithGuide falls back to the
     *  most-recently-opened guide so the quick-copy path still works without
     *  forcing the user through the picker. */
    private val _selectedGuideIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedGuideIds: StateFlow<Set<String>> = _selectedGuideIds.asStateFlow()

    fun setSelectedGuides(ids: Set<String>) {
        _selectedGuideIds.value = ids
    }

    /** Color used by `ensureActiveTabId` when it has to provision the very
     *  first tab. Captured at load() time so the lazy creation matches the
     *  vault's accent — same as before, just deferred. */
    private var pendingDefaultColorIndex: Int = 1

    fun load(vaultId: String, defaultTabColorIndex: Int = 1) {
        vaultIdFlow.value = vaultId
        pendingDefaultColorIndex = defaultTabColorIndex
        // Reset active tab — prevents stale tab IDs from a previously
        // loaded vault bleeding into this one.
        _activeTabId.value = null
        viewModelScope.launch {
            val existingTabs = repo.getTabsForVaultOnce(vaultId)
            val savedTabId = checklistViewStateManager.getActiveTabId(vaultId)
            val tab = (savedTabId?.let { id -> existingTabs.firstOrNull { it.id == id } })
                ?: existingTabs.firstOrNull()
                ?: repo.createTab(vaultId, "Tab 1", colorIndex = defaultTabColorIndex)
            _activeTabId.value = tab.id
        }
    }

    /** Returns an active tab id, creating the default `Tab 1` tab on the
     *  fly when the vault has none yet. Returns null only if the vault id
     *  hasn't been set (i.e. load() wasn't called). */
    private suspend fun ensureActiveTabId(): String? {
        val vid = vaultIdFlow.value ?: return null
        _activeTabId.value?.let { id ->
            // Stale activeTabId can survive a tab deletion; verify before reuse.
            if (tabs.value.any { it.id == id }) return id
        }
        tabs.value.firstOrNull()?.let {
            _activeTabId.value = it.id
            return it.id
        }
        val created = repo.createTab(vid, "Tab 1", colorIndex = pendingDefaultColorIndex)
        _activeTabId.value = created.id
        return created.id
    }

    fun selectTab(tabId: String) {
        _activeTabId.value = tabId
        vaultIdFlow.value?.let { checklistViewStateManager.saveActiveTabId(it, tabId) }
    }

    fun createTab(name: String, colorIndex: Int) {
        val vid = vaultIdFlow.value ?: return
        viewModelScope.launch {
            val tab = repo.createTab(vid, name, colorIndex)
            // Tab starts with zero sections — the empty-state card will
            // prompt the user to name their first section instead of being
            // pre-polluted with a generic "General" bucket.
            _activeTabId.value = tab.id
        }
    }

    fun renameTab(tabId: String, name: String) { viewModelScope.launch { repo.renameTab(tabId, name) } }
    fun recolorTab(tabId: String, colorIndex: Int) { viewModelScope.launch { repo.setTabColor(tabId, colorIndex) } }

    fun deleteTab(tabId: String) {
        val currentTabs = tabs.value
        if (currentTabs.size <= 1) return
        viewModelScope.launch {
            repo.deleteTab(tabId)
            if (_activeTabId.value == tabId) {
                _activeTabId.value = currentTabs.firstOrNull { it.id != tabId }?.id
            }
        }
    }

    fun reorderTabs(orderedIds: List<String>) {
        val vid = vaultIdFlow.value ?: return
        viewModelScope.launch { repo.reorderTabs(vid, orderedIds) }
    }

    fun createSection(name: String) {
        val vid = vaultIdFlow.value ?: return
        viewModelScope.launch {
            val tabId = ensureActiveTabId() ?: return@launch
            repo.createSection(vid, tabId, name, expanded = true)
        }
    }

    fun renameSection(sectionId: String, name: String) { viewModelScope.launch { repo.renameSection(sectionId, name) } }

    fun toggleSectionExpanded(sectionId: String) {
        viewModelScope.launch {
            val current = sectionsWithItems.value.firstOrNull { it.section.id == sectionId }?.section ?: return@launch
            repo.setSectionExpanded(sectionId, !current.expanded)
        }
    }

    fun toggleAllSectionsExpanded() {
        val tabId = _activeTabId.value ?: return
        viewModelScope.launch {
            val anyExpanded = sectionsWithItems.value.any { it.section.expanded }
            repo.setAllSectionsExpandedInTab(tabId, expanded = !anyExpanded)
        }
    }

    fun deleteSection(sectionId: String) { viewModelScope.launch { repo.deleteSection(sectionId) } }

    fun promoteItemsToSections(sectionId: String) { viewModelScope.launch { repo.promoteItemsToSections(sectionId) } }

    fun reorderSections(orderedIds: List<String>) {
        val tabId = _activeTabId.value ?: return
        viewModelScope.launch { repo.reorderSections(tabId, orderedIds) }
    }

    fun addItem(sectionId: String, title: String) {
        val vid = vaultIdFlow.value ?: return
        if (title.isBlank()) return
        viewModelScope.launch { repo.createItemInSection(vid, sectionId, title.trim()) }
    }

    fun toggleItem(itemId: String) { viewModelScope.launch { repo.toggleItemCompletion(itemId) } }
    fun deleteItem(itemId: String) { viewModelScope.launch { repo.deleteItem(itemId) } }

    /** Delete a specific set of items by id. Also cleans up sections that
     *  become empty after the deletion. */
    fun deleteItems(itemIds: List<String>) {
        if (itemIds.isEmpty()) return
        val tabId = _activeTabId.value ?: return
        viewModelScope.launch { repo.deleteItemsAndEmptySections(itemIds, tabId) }
    }

    /** Delete shown items and shown sections — used by "Delete shown content"
     *  in the tab context menu. */
    fun deleteShownContent(itemIds: List<String>, sectionIds: List<String>) {
        if (itemIds.isEmpty() && sectionIds.isEmpty()) return
        viewModelScope.launch { repo.deleteItemsAndSections(itemIds, sectionIds) }
    }

    fun renameItem(itemId: String, newTitle: String) {
        viewModelScope.launch { repo.renameItem(itemId, newTitle) }
    }

    fun setAllItemsCompletedInSection(sectionId: String, completed: Boolean) {
        viewModelScope.launch { repo.setAllItemsCompletedInSection(sectionId, completed) }
    }

    /** Flip `completed` on an explicit set of items — used so "Mark all shown
     *  items" honors the active hide-completed / search filters. */
    fun setItemsCompleted(itemIds: List<String>, completed: Boolean) {
        if (itemIds.isEmpty()) return
        viewModelScope.launch { repo.setItemsCompleted(itemIds, completed) }
    }

    fun reorderItems(sectionId: String, orderedIds: List<String>) {
        viewModelScope.launch { repo.reorderItems(sectionId, orderedIds) }
    }

    fun saveScrollPosition(firstVisibleIndex: Int, scrollOffset: Int) {
        vaultIdFlow.value?.let {
            checklistViewStateManager.saveScrollPosition(it, firstVisibleIndex, scrollOffset)
        }
    }

    fun getSavedScrollPosition(): Pair<Int, Int>? =
        vaultIdFlow.value?.let { checklistViewStateManager.getScrollPosition(it) }

    fun setSearchQuery(q: String) { _searchQuery.value = q }
    fun toggleHideCompleted() { _hideCompleted.value = !_hideCompleted.value }
    fun openPasteSheet() { _pasteSheetOpen.value = true }
    fun closePasteSheet() { _pasteSheetOpen.value = false }

    fun importPasted(text: String) {
        val vid = vaultIdFlow.value ?: return
        val trimmed = text.trim()
        if (trimmed.isBlank()) return
        val active = _activeTabId.value
        viewModelScope.launch {
            try {
                val result = repo.importFromMarkdown(vid, active, trimmed)
                // Land the user on whatever tab we just wrote into — when a
                // paste creates a new tab (or routes to an existing-by-name
                // one), staying on the previously-active tab would hide the
                // imported content. Falls back to no-op if nothing was added.
                result.lastTabId?.let { _activeTabId.value = it }
                _events.send(OneShotEvent.ImportComplete(result.itemsAdded, result.itemsSkipped, result.tabsCreated, result.sectionsCreated))
                closePasteSheet()
            } catch (e: Exception) {
                _events.send(OneShotEvent.Error(e.message ?: "Import failed"))
            }
        }
    }

    // ── Prompt template system ────────────────────────────────────────────────

    /** Merged list: defaults overlaid with any user-saved overrides. */
    val promptTemplates: StateFlow<List<ChecklistPromptTemplate>> =
        settingsRepository.checklistPromptTemplatesJson
            .map { json -> mergeTemplates(json) }
            .stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                ChecklistPromptTemplateDefaults.templates,
            )

    private val selectedIdFlow: StateFlow<String?> = settingsRepository.checklistSelectedPromptId
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val selectedTemplate: StateFlow<ChecklistPromptTemplate> = combine(
        promptTemplates,
        selectedIdFlow,
    ) { list, id ->
        list.firstOrNull { it.id == id } ?: list.first()
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        ChecklistPromptTemplateDefaults.templates.first(),
    )

    fun selectTemplate(id: String) {
        viewModelScope.launch { settingsRepository.setChecklistSelectedPromptId(id) }
    }

    /** Save edits (name and/or content) to the currently-selected template. */
    fun saveSelectedTemplate(name: String, content: String) {
        viewModelScope.launch {
            val selected = selectedTemplate.value
            val updated = selected.copy(
                name = name.trim().ifBlank { selected.name },
                content = content,
            )
            val next = promptTemplates.value.map { if (it.id == selected.id) updated else it }
            settingsRepository.setChecklistPromptTemplatesJson(serialize(next))
        }
    }

    /** Reset the currently-selected slot to its default (by id). No-op if the id isn't a default. */
    fun resetSelectedTemplateToDefault() {
        viewModelScope.launch {
            val selected = selectedTemplate.value
            val def = ChecklistPromptTemplateDefaults.findById(selected.id) ?: return@launch
            val next = promptTemplates.value.map { if (it.id == selected.id) def else it }
            settingsRepository.setChecklistPromptTemplatesJson(serialize(next))
        }
    }

    /** Create a user-defined template with the given name, select it, and return its id. */
    fun createUserTemplate(name: String, seedContent: String = ""): String {
        val id = "custom-${java.util.UUID.randomUUID()}"
        val new = ChecklistPromptTemplate(
            id = id,
            name = name.trim().ifBlank { "Custom template" },
            content = seedContent,
        )
        val next = promptTemplates.value + new
        viewModelScope.launch {
            settingsRepository.setChecklistPromptTemplatesJson(serialize(next))
            settingsRepository.setChecklistSelectedPromptId(id)
        }
        return id
    }

    /** Delete the currently-selected template if it's user-defined (no-op on defaults). */
    fun deleteSelectedUserTemplate() {
        viewModelScope.launch {
            val selected = selectedTemplate.value
            if (ChecklistPromptTemplateDefaults.findById(selected.id) != null) return@launch
            val next = promptTemplates.value.filter { it.id != selected.id }
            settingsRepository.setChecklistPromptTemplatesJson(serialize(next))
            // Fall back to the first available template so the UI doesn't dangle.
            settingsRepository.setChecklistSelectedPromptId(next.firstOrNull()?.id)
        }
    }

    /** True when the currently-selected template is one of the built-in defaults. */
    val selectedIsDefaultTemplate: StateFlow<Boolean> = selectedTemplate
        .map { ChecklistPromptTemplateDefaults.findById(it.id) != null }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    private fun mergeTemplates(json: String?): List<ChecklistPromptTemplate> {
        if (json.isNullOrBlank()) return ChecklistPromptTemplateDefaults.templates
        val stored: List<ChecklistPromptTemplate> = runCatching { parse(json) }
            .getOrDefault(emptyList())
        val overridesById = stored.associateBy { it.id }
        val defaultIds = ChecklistPromptTemplateDefaults.templates.map { it.id }.toSet()
        // Defaults (with user overrides applied) first, then any user-created
        // templates appended at the end so the built-ins keep a stable order.
        val defaults = ChecklistPromptTemplateDefaults.templates.map { overridesById[it.id] ?: it }
        val customs = stored.filter { it.id !in defaultIds }
        return defaults + customs
    }

    private fun serialize(list: List<ChecklistPromptTemplate>): String {
        val arr = org.json.JSONArray()
        for (t in list) {
            arr.put(
                org.json.JSONObject().apply {
                    put("id", t.id)
                    put("name", t.name)
                    put("content", t.content)
                },
            )
        }
        return arr.toString()
    }

    private fun parse(json: String): List<ChecklistPromptTemplate> {
        val arr = org.json.JSONArray(json)
        val out = mutableListOf<ChecklistPromptTemplate>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(
                ChecklistPromptTemplate(
                    id = o.getString("id"),
                    name = o.optString("name"),
                    content = o.optString("content"),
                ),
            )
        }
        return out
    }

    fun aiPromptForVault(vaultName: String): String =
        selectedTemplate.value.content.replace("{vault}", vaultName)

    fun copyAiPromptText(promptText: String) {
        viewModelScope.launch { _events.send(OneShotEvent.CopiedPrompt(promptText)) }
    }

    fun requestBrowserSearch(vaultName: String) {
        viewModelScope.launch { _events.send(OneShotEvent.OpenBrowserSearch("$vaultName checklist")) }
    }

    /**
     * Copy the AI prompt with a request to analyze the vault's guide source URL.
     *
     * We previously tried bundling the guide's plain text — but even modest
     * guides run into hundreds of kilobytes after HTML stripping, making the
     * clipboard unwieldy and wasting the AI's context window. Instead we now
     * hand the AI the URL and ask it to pull the page itself. AI tools with
     * web access (ChatGPT, Gemini, Claude with browsing) do this cleanly.
     */
    // ── Tap game high score ───────────────────────────────────────────────────

    val tapGameHighScore: StateFlow<Int> = settingsRepository.checklistTapGameHighScore
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    fun setTapGameHighScore(score: Int) {
        viewModelScope.launch { settingsRepository.setChecklistTapGameHighScore(score) }
    }

    /**
     * Copy the AI prompt to the clipboard, optionally with the user's currently
     * picked guides appended as URL references. Empty selection = plain prompt
     * (this lets a single Copy button drive both flows from the UI).
     */
    fun copyPromptWithGuide(vaultName: String) {
        viewModelScope.launch {
            val promptText = aiPromptForVault(vaultName)
            val available = availableGuides.value
            val selectedIds = _selectedGuideIds.value
            val picked = if (selectedIds.isEmpty()) {
                emptyList()
            } else {
                // Preserve available-list order (most-recent first) so the
                // prompt lists guides in a stable, predictable order.
                available.filter { it.id in selectedIds }
            }
            if (picked.isEmpty()) {
                _events.send(OneShotEvent.CopiedPrompt(promptText))
                return@launch
            }
            val urls = picked.map { it.url }.distinct()
            val combined = buildString {
                append(promptText)
                append("\n\n--- Guide source")
                if (urls.size > 1) append("s")
                append(" ---\n")
                append("Before generating the list, please visit and analyse ")
                append(if (urls.size == 1) "this guide" else "these guides")
                append(" for ")
                append(vaultName)
                append(":\n")
                urls.forEach { url -> append("- $url\n") }
                append("Base the checklist on the concrete content of ")
                append(if (urls.size == 1) "that page. " else "those pages. ")
                append("If you can't load a page, reply with a brief note instead of guessing.")
            }
            _events.send(OneShotEvent.CopiedPromptWithGuide(combined, urls))
        }
    }
}
