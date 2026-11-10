package com.twinlore.app.ui.screens.ai

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twinlore.app.data.SavedMapRepository
import com.twinlore.app.data.SettingsRepository
import com.twinlore.app.data.VaultRepository
import com.twinlore.app.data.library.ResourceResolver
import com.twinlore.app.domain.SavedMap
import dagger.hilt.android.qualifiers.ApplicationContext
import com.twinlore.app.data.ai.AiMessage
import com.twinlore.app.data.ai.AiRole
import com.twinlore.app.data.ai.AiSession
import com.twinlore.app.data.ai.AiSessionStore
import com.twinlore.app.data.ai.GeminiChunk
import com.twinlore.app.data.ai.GeminiClient
import com.twinlore.app.data.ai.GeminiErrorKind
import com.twinlore.app.data.retroachievements.RaGameCacheRepository
import com.twinlore.app.data.retroachievements.RaGameProgress
import com.twinlore.app.data.retroachievements.RaProfileRepository
import com.twinlore.app.data.steam.SteamGameCacheRepository
import com.twinlore.app.data.steam.SteamGameProgress
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class AiAssistantUiState(
    val messages: List<AiMessage> = emptyList(),
    val input: String = "",
    val pendingAttachmentPath: String? = null,
    val isStreaming: Boolean = false,
    val templates: List<String> = emptyList(),
    val gameName: String = "",
    val model: String = "",
    val artworkResourceId: String? = null,
    val vaultImages: List<SavedMap> = emptyList(),
    val apiKeyConfigured: Boolean = true,
    val autoScreenshot: Boolean = false,
    val errorBanner: String? = null,
    /** Shown under the typing indicator while the first token is unusually late; null otherwise. */
    val slowNotice: String? = null,
    val showClearedUndo: Boolean = false,
)

/** How long the "Cleared · UNDO" banner stays after clearing the conversation. */
private const val CLEAR_UNDO_WINDOW_MS = 5000L

@HiltViewModel
class AiAssistantViewModel @Inject constructor(
    private val store: AiSessionStore,
    private val settings: SettingsRepository,
    private val vaults: VaultRepository,
    private val gemini: GeminiClient,
    private val raCache: RaGameCacheRepository,
    private val steamCache: SteamGameCacheRepository,
    private val raProfile: RaProfileRepository,
    private val savedMaps: SavedMapRepository,
    private val resourceResolver: ResourceResolver,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AiAssistantUiState())
    val uiState: StateFlow<AiAssistantUiState> = _uiState.asStateFlow()

    private var vaultId: String = ""
    private var started: Boolean = false

    /** Extra context (achievement progress) injected into the system prompt. */
    private var gameContext: String = ""

    /** Bind to a vault: load its session, game name, and templates. Idempotent. */
    fun start(vaultId: String) {
        if (started && this.vaultId == vaultId) return
        started = true
        this.vaultId = vaultId
        val session = store.load(vaultId)
        viewModelScope.launch {
            val vault = vaults.getVaultById(vaultId)
            val name = vault?.displayName ?: ""
            val templates = settings.aiTemplates.first()
            val model = settings.geminiModel.first()
            // Locally-cached achievement progress (no network) plus the RA "now
            // playing" rich-presence give the AI a lightweight sense of where the
            // player currently is, without a big to-do dump.
            val ra = runCatching { raCache.getCachedProgress(vaultId) }.getOrNull()
            val steam = runCatching { steamCache.getCachedProgress(vaultId) }.getOrNull()
            val raPresence = runCatching {
                val profile = raProfile.load(forceRefresh = false)
                val vaultRaId = vault?.raGameId?.toIntOrNull()
                if (vaultRaId != null && profile?.lastGameId == vaultRaId) {
                    profile.richPresenceMsg?.takeIf { it.isNotBlank() }
                } else null
            }.getOrNull()
            gameContext = buildGameContext(ra, raPresence, steam)
            _uiState.value = _uiState.value.copy(
                messages = session.messages,
                gameName = name,
                templates = templates,
                model = model,
                artworkResourceId = vault?.artworkResourceId,
                isStreaming = false, // never inherit a stale streaming flag
            )
        }
        // Keep the vault's saved images live so the picker reflects additions made
        // in the Images tab while this (retained) ViewModel is still alive.
        viewModelScope.launch {
            savedMaps.getMapsByVaultId(vaultId).collect { imgs ->
                _uiState.value = _uiState.value.copy(vaultImages = imgs)
            }
        }
        // Track whether a key is set so the no-key banner disappears the moment the
        // user adds one in Settings and returns.
        viewModelScope.launch {
            settings.geminiApiKey.collect { key ->
                _uiState.value = _uiState.value.copy(apiKeyConfigured = !key.isNullOrBlank())
            }
        }
        viewModelScope.launch {
            settings.aiAutoScreenshot.collect { on ->
                _uiState.value = _uiState.value.copy(autoScreenshot = on)
            }
        }
    }

    /** Toggle auto-attaching a screenshot when a template is picked. */
    fun setAutoScreenshot(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(autoScreenshot = enabled)
        viewModelScope.launch { settings.setAiAutoScreenshot(enabled) }
    }

    /** Save a Gemini API key entered inline (e.g. from the bottom-tab no-key state). */
    fun setApiKey(key: String) {
        viewModelScope.launch { settings.setGeminiApiKey(key.ifBlank { null }) }
    }

    /** Hide the AI bottom tab (experiment); re-enable from Settings. */
    fun hideBottomTab() {
        viewModelScope.launch { settings.setAiBottomTabVisible(false) }
    }

    /** Attach one of this vault's saved images (from the Map tab) by its resourceId. */
    fun attachVaultImage(resourceId: String) {
        viewModelScope.launch {
            val ok = runCatching {
                val uri = resourceResolver.resolve(resourceId) ?: return@runCatching false
                val tmp = File(context.cacheDir, "ai_vault_${System.currentTimeMillis()}.png")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    tmp.outputStream().use { input.copyTo(it) }
                }
                if (tmp.exists() && tmp.length() > 0) {
                    val dest = store.copyImageIntoSession(vaultId, tmp, System.currentTimeMillis())
                    _uiState.value = _uiState.value.copy(pendingAttachmentPath = dest)
                    tmp.delete()
                    true
                } else false
            }.getOrDefault(false)
            if (!ok) {
                _uiState.value = _uiState.value.copy(errorBanner = "Couldn't attach that image.")
            }
        }
    }

    /** Change the Gemini model from the AI view (persists to settings). */
    fun setModel(model: String) {
        _uiState.value = _uiState.value.copy(model = model)
        viewModelScope.launch { settings.setGeminiModel(model) }
    }

    fun onInputChange(text: String) {
        _uiState.value = _uiState.value.copy(input = text)
    }

    fun insertTemplate(template: String) {
        val current = _uiState.value.input
        val joined = if (current.isBlank()) template else "$current $template"
        _uiState.value = _uiState.value.copy(input = joined)
    }

    fun saveTemplates(templates: List<String>) {
        _uiState.value = _uiState.value.copy(templates = templates)
        viewModelScope.launch { settings.setAiTemplates(templates) }
    }

    /** Copy a captured/picked image into the session and stage it for the next send. */
    fun attachImage(src: File, timestamp: Long) {
        val path = runCatching { store.copyImageIntoSession(vaultId, src, timestamp) }.getOrNull()
        _uiState.value = if (path != null) {
            _uiState.value.copy(pendingAttachmentPath = path)
        } else {
            _uiState.value.copy(errorBanner = "Couldn't attach that image.")
        }
    }

    fun removePendingAttachment() {
        _uiState.value = _uiState.value.copy(pendingAttachmentPath = null)
    }

    /** Dismiss the error card without retrying. */
    fun dismissError() {
        _uiState.value = _uiState.value.copy(errorBanner = null)
    }

    private var clearedBackup: List<AiMessage>? = null
    private var clearUndoJob: kotlinx.coroutines.Job? = null

    /** Clears the conversation immediately but keeps a backup so the UNDO banner can
     *  restore it. The undo window closes after [CLEAR_UNDO_WINDOW_MS]. */
    fun clearSession() {
        val backup = _uiState.value.messages
        store.clear(vaultId)
        clearedBackup = backup
        _uiState.value = _uiState.value.copy(
            messages = emptyList(),
            errorBanner = null,
            showClearedUndo = backup.isNotEmpty(),
        )
        clearUndoJob?.cancel()
        if (backup.isNotEmpty()) {
            clearUndoJob = viewModelScope.launch {
                delay(CLEAR_UNDO_WINDOW_MS)
                clearedBackup = null
                _uiState.value = _uiState.value.copy(showClearedUndo = false)
            }
        }
    }

    /** Restore the conversation cleared by [clearSession], if still within the window. */
    fun undoClearSession() {
        clearUndoJob?.cancel()
        val backup = clearedBackup ?: return
        clearedBackup = null
        _uiState.value = _uiState.value.copy(messages = backup, showClearedUndo = false)
        persist(backup)
    }

    fun send() {
        val state = _uiState.value
        val text = state.input.trim()
        if (state.isStreaming) return
        if (text.isEmpty() && state.pendingAttachmentPath == null) return

        val now = System.currentTimeMillis()
        val userMsg = AiMessage(AiRole.USER, text, state.pendingAttachmentPath, now)
        val withUser = state.messages + userMsg
        _uiState.value = state.copy(
            messages = withUser,
            input = "",
            pendingAttachmentPath = null,
            isStreaming = true,
            errorBanner = null,
        )
        persist(withUser)

        val gameNameSnapshot = _uiState.value.gameName
        viewModelScope.launch {
            val apiKey = settings.geminiApiKey.first().orEmpty()
            val model = settings.geminiModel.first()
            val system = systemInstruction(gameNameSnapshot)

            // Decouple reception from display: deltas accumulate into [full]; a
            // revealer paces text into the UI so the answer appears progressively
            // (typewriter), even when the network delivers it in one burst.
            var full = ""
            var shown = 0
            var modelAdded = false
            var streamDone = false
            var banner: String? = null

            fun renderShown() {
                val base = _uiState.value.messages
                if (base.isEmpty()) return // session cleared mid-stream
                val visible = full.substring(0, shown)
                val updated = if (!modelAdded || base.last().role != AiRole.MODEL) {
                    modelAdded = true
                    base + AiMessage(AiRole.MODEL, visible, null, System.currentTimeMillis())
                } else {
                    base.dropLast(1) + base.last().copy(text = visible)
                }
                _uiState.value = _uiState.value.copy(messages = updated)
            }

            val revealer = launch {
                while (true) {
                    if (shown < full.length) {
                        // Reveal a slice proportional to the backlog so bursts catch
                        // up smoothly while genuinely-streamed text stays gentle.
                        shown += maxOf(1, (full.length - shown) / 10)
                        renderShown()
                        delay(18)
                    } else if (streamDone) {
                        break
                    } else {
                        delay(18)
                    }
                }
            }

            // First-token watchdog. A stream that connects but never answers used to leave the
            // user staring at the typing dots indefinitely (observed: three minutes) - the only
            // guard was OkHttp's 120s read timeout. Now: a notice under the dots at 12s, and the
            // attempt is abandoned at 60s with a banner. Google's own error body, when there is
            // one, still arrives as a GeminiChunk.Error and takes the banner over the timeout
            // text. The deferred (rather than joining the stream job) is what lets the UI move
            // on immediately at the timeout: the client's blocking SSE read cannot be
            // interrupted mid-read, so the cancelled job may linger until OkHttp's own timeout,
            // and nothing here waits on it.
            var sawFirstToken = false
            var timedOut = false
            val streamFinished = kotlinx.coroutines.CompletableDeferred<Unit>()
            val streamJob = launch {
                try {
                    gemini.stream(apiKey, model, system, withUser).collect { chunk ->
                        when (chunk) {
                            is GeminiChunk.Delta -> {
                                if (!sawFirstToken) {
                                    sawFirstToken = true
                                    _uiState.value = _uiState.value.copy(slowNotice = null)
                                }
                                full += chunk.text
                            }
                            is GeminiChunk.Done -> {}
                            is GeminiChunk.Error -> banner = errorText(chunk)
                        }
                    }
                } finally {
                    streamFinished.complete(Unit)
                }
            }
            val watchdog = launch {
                delay(SLOW_NOTICE_AFTER_MS)
                if (!sawFirstToken) {
                    _uiState.value = _uiState.value.copy(
                        slowNotice = "Gemini is taking longer than usual to answer…"
                    )
                }
                delay(FIRST_TOKEN_TIMEOUT_MS - SLOW_NOTICE_AFTER_MS)
                if (!sawFirstToken) {
                    timedOut = true
                    streamJob.cancel()
                    streamFinished.complete(Unit)
                }
            }
            streamFinished.await()
            watchdog.cancel()
            if (timedOut && banner == null) {
                banner = "No answer from Gemini after ${FIRST_TOKEN_TIMEOUT_MS / 1000} seconds " +
                    "and no error reported by Google - the model may be overloaded. " +
                    "Try again, or pick a different model."
            }
            streamDone = true
            revealer.join()
            // Reveal anything still buffered, then finalize + persist.
            if (shown < full.length) {
                shown = full.length
                renderShown()
            }
            _uiState.value = _uiState.value.copy(
                isStreaming = false,
                errorBanner = banner,
                slowNotice = null,
            )
            persist(_uiState.value.messages)
        }
    }

    /** Re-send the last user turn (used by the error banner's Retry). */
    fun retry() {
        val msgs = _uiState.value.messages
        val lastUser = msgs.lastOrNull { it.role == AiRole.USER } ?: return
        // Drop a trailing model message if the previous attempt produced a partial one.
        val trimmed = if (msgs.lastOrNull()?.role == AiRole.MODEL) msgs.dropLast(1) else msgs
        _uiState.value = _uiState.value.copy(
            messages = trimmed.dropLast(1), // remove the lastUser; send() re-appends it
            input = lastUser.text,
            pendingAttachmentPath = lastUser.imagePath,
            errorBanner = null,
        )
        send()
    }

    private fun persist(messages: List<AiMessage>) {
        store.save(AiSession(vaultId, messages))
    }

    private fun systemInstruction(gameName: String): String =
        "You are a concise, helpful in-game assistant inside the Game Twin app. " +
            (if (gameName.isNotBlank()) "The player is currently playing \"$gameName\". " else "") +
            "Answer directly and briefly, using any attached screenshot of their screen. " +
            "Be explicit about uncertainty: if you are not sure, say so plainly instead of guessing, " +
            "and never invent game facts. If you don't have enough to answer well — for example you " +
            "can't tell which game, screen, item, or character the player means — say what's missing " +
            "and ask them to attach a screenshot or give a specific detail. Prefer a short accurate " +
            "answer over a long speculative one." +
            gameContext

    /**
     * Lightweight "where is the player" metadata from locally-cached Steam/RA
     * data: overall completion, the 3 most-recent unlocks (a stage anchor), and
     * the RA "now playing" rich-presence text. This is a hint for the model to
     * gauge the player's stage — not a to-do list to recite. Empty when no cached
     * progress exists.
     */
    private fun buildGameContext(
        ra: RaGameProgress?,
        raPresence: String?,
        steam: SteamGameProgress?,
    ): String {
        fun recent(titles: List<String>) =
            titles.take(3).joinToString(", ") { "\"$it\"" }

        val sb = StringBuilder()
        if ((ra != null && ra.numAchievements > 0) || (steam != null && steam.numAchievements > 0)) {
            sb.append("\n\n[Player progress — use only to gauge their stage, do not recite it back.]")
        }
        if (ra != null && ra.numAchievements > 0) {
            sb.append("\nRetroAchievements: ${ra.userNumAchieved}/${ra.numAchievements} unlocked")
            ra.userCompletionPercent?.takeIf { it.isNotBlank() }?.let { sb.append(" ($it)") }
            sb.append(".")
            val recent = recent(ra.recentUnlocks.map { it.title })
            if (recent.isNotBlank()) sb.append(" Recently earned (newest first): $recent.")
            raPresence?.let { sb.append(" Now playing: $it.") }
        }
        if (steam != null && steam.numAchievements > 0) {
            sb.append("\nSteam: ${steam.numUnlocked}/${steam.numAchievements} unlocked.")
            val recent = recent(steam.recentUnlocks.map { it.title })
            if (recent.isNotBlank()) sb.append(" Recently earned (newest first): $recent.")
        }
        return sb.toString()
    }

    private fun errorText(error: GeminiChunk.Error): String {
        // GeminiClient already cleans the detail to a human sentence (the Gemini
        // error.message) where possible. For actionable kinds we keep a short
        // headline + the detail; for generic failures the detail alone reads best.
        val detail = error.message.takeIf { it.isNotBlank() }
        return when (error.kind) {
            GeminiErrorKind.Auth ->
                "Invalid or missing API key — check it in Settings." + (detail?.let { "\n$it" } ?: "")
            GeminiErrorKind.Network ->
                "Network error — check your connection." + (detail?.let { "\n$it" } ?: "")
            GeminiErrorKind.Quota -> detail ?: "Rate limit or quota exceeded. Try again later."
            GeminiErrorKind.Unknown -> detail ?: "Something went wrong. Try again."
        }
    }
}

/** The typing dots gain a "taking longer than usual" line after this much first-token silence. */
private const val SLOW_NOTICE_AFTER_MS = 12_000L

/** With no first token by here, the attempt is abandoned and the error banner says so. */
private const val FIRST_TOKEN_TIMEOUT_MS = 60_000L
