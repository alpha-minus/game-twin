package com.twinlore.app.ui.screens.ai

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.twinlore.app.data.SettingsRepository
import com.twinlore.app.data.VaultRepository
import com.twinlore.app.data.ai.AiRole
import com.twinlore.app.data.ai.AiSessionStore
import com.twinlore.app.data.ai.GeminiChunk
import com.twinlore.app.data.ai.GeminiClient
import com.twinlore.app.data.ai.AiMessage
import com.twinlore.app.domain.Vault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import com.twinlore.app.data.ai.GeminiErrorKind
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.kotlin.wheneverBlocking
import com.twinlore.app.data.ai.AiSession
import java.util.Date

@OptIn(ExperimentalCoroutinesApi::class)
class AiAssistantViewModelTest {
    @get:Rule val rule = InstantTaskExecutorRule()
    private val dispatcher = StandardTestDispatcher()

    private lateinit var store: AiSessionStore
    private lateinit var settings: SettingsRepository
    private lateinit var vaults: VaultRepository
    private lateinit var raCache: com.twinlore.app.data.retroachievements.RaGameCacheRepository
    private lateinit var steamCache: com.twinlore.app.data.steam.SteamGameCacheRepository
    private lateinit var raProfile: com.twinlore.app.data.retroachievements.RaProfileRepository
    private lateinit var savedMaps: com.twinlore.app.data.SavedMapRepository
    private lateinit var resourceResolver: com.twinlore.app.data.library.ResourceResolver
    private lateinit var appContext: android.content.Context

    private fun vault(id: String) = Vault(
        id = id,
        name = "Zelda",
        romPath = null,
        romFilename = null,
        systemeid = null,
        gameId = null,
        source = null,
        lastOpened = null,
        createdAt = Date(),
    )

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        store = mock()
        settings = mock()
        vaults = mock()
        raCache = mock()
        steamCache = mock()
        raProfile = mock()
        savedMaps = mock()
        resourceResolver = mock()
        appContext = mock()
        whenever(store.load(any())).thenReturn(AiSession("v1"))
        whenever(settings.geminiApiKey).thenReturn(flowOf("key"))
        whenever(settings.geminiModel).thenReturn(flowOf("gemini-flash-latest"))
        whenever(settings.aiTemplates).thenReturn(flowOf(listOf("t1")))
        whenever(settings.aiAutoScreenshot).thenReturn(flowOf(false))
    }

    @After fun tearDown() = Dispatchers.resetMain()

    private fun vmWith(client: GeminiClient): AiAssistantViewModel {
        // getVaultById / getCachedProgress are suspend funs — stub with wheneverBlocking.
        wheneverBlocking { vaults.getVaultById("v1") }.thenReturn(vault("v1"))
        wheneverBlocking { raCache.getCachedProgress("v1") }.thenReturn(null)
        wheneverBlocking { steamCache.getCachedProgress("v1") }.thenReturn(null)
        wheneverBlocking { raProfile.load(any()) }.thenReturn(null)
        whenever(savedMaps.getMapsByVaultId("v1")).thenReturn(flowOf(emptyList()))
        return AiAssistantViewModel(
            store, settings, vaults, client, raCache, steamCache, raProfile,
            savedMaps, resourceResolver, appContext,
        )
    }

    @Test
    fun send_appendsUserAndStreamedModelMessage() = runTest(dispatcher) {
        val fake = object : GeminiClient() {
            override fun stream(
                apiKey: String, model: String, systemInstruction: String, messages: List<AiMessage>,
            ): Flow<GeminiChunk> = flowOf(GeminiChunk.Delta("Hel"), GeminiChunk.Delta("lo"), GeminiChunk.Done)
        }
        val vm = vmWith(fake)
        vm.start("v1")
        advanceUntilIdle()
        vm.onInputChange("where now?")
        vm.send()
        advanceUntilIdle()

        val msgs = vm.uiState.value.messages
        assertEquals(2, msgs.size)
        assertEquals(AiRole.USER, msgs[0].role)
        assertEquals("where now?", msgs[0].text)
        assertEquals(AiRole.MODEL, msgs[1].role)
        assertEquals("Hello", msgs[1].text)
        assertFalse(vm.uiState.value.isStreaming)
    }

    /**
     * A stream that never produces a first token must not leave the user staring at three
     * dots forever (observed live: three minutes with no feedback). A notice appears after
     * ~12s; at 60s the attempt is abandoned with a banner that says so.
     */
    @Test
    fun send_silentStream_showsSlowNoticeThenTimesOutWithBanner() = runTest(dispatcher) {
        val fake = object : GeminiClient() {
            override fun stream(
                apiKey: String, model: String, systemInstruction: String, messages: List<AiMessage>,
            ): Flow<GeminiChunk> = kotlinx.coroutines.flow.flow { kotlinx.coroutines.awaitCancellation() }
        }
        val vm = vmWith(fake)
        vm.start("v1"); advanceUntilIdle()
        vm.onInputChange("hello"); vm.send()

        advanceTimeBy(15_000); runCurrent()
        assertNotNull("slow notice after 15s of silence", vm.uiState.value.slowNotice)
        org.junit.Assert.assertTrue(vm.uiState.value.isStreaming)

        advanceTimeBy(50_000); runCurrent()
        assertFalse("the attempt is abandoned at the timeout", vm.uiState.value.isStreaming)
        assertNull(vm.uiState.value.slowNotice)
        assertNotNull(vm.uiState.value.errorBanner)
        advanceUntilIdle()
    }

    /** The notice is about the wait, not the answer: the first delta clears it. */
    @Test
    fun send_firstDeltaClearsSlowNotice() = runTest(dispatcher) {
        val fake = object : GeminiClient() {
            override fun stream(
                apiKey: String, model: String, systemInstruction: String, messages: List<AiMessage>,
            ): Flow<GeminiChunk> = kotlinx.coroutines.flow.flow {
                kotlinx.coroutines.delay(20_000)
                emit(GeminiChunk.Delta("hi")); emit(GeminiChunk.Done)
            }
        }
        val vm = vmWith(fake)
        vm.start("v1"); advanceUntilIdle()
        vm.onInputChange("hello"); vm.send()

        advanceTimeBy(15_000); runCurrent()
        assertNotNull(vm.uiState.value.slowNotice)

        advanceUntilIdle()
        assertNull("the delta cleared the notice", vm.uiState.value.slowNotice)
        assertNull("a late answer is not an error", vm.uiState.value.errorBanner)
        assertFalse(vm.uiState.value.isStreaming)
        assertEquals("hi", vm.uiState.value.messages.last().text)
    }

    @Test
    fun send_setsErrorBannerAndStopsStreamingOnError() = runTest(dispatcher) {
        val fake = object : GeminiClient() {
            override fun stream(
                apiKey: String, model: String, systemInstruction: String, messages: List<AiMessage>,
            ): Flow<GeminiChunk> = flowOf(GeminiChunk.Error(GeminiErrorKind.Auth, "bad"))
        }
        val vm = vmWith(fake)
        vm.start("v1"); advanceUntilIdle()
        vm.onInputChange("hi"); vm.send(); advanceUntilIdle()
        assertFalse(vm.uiState.value.isStreaming)
        assertNotNull(vm.uiState.value.errorBanner)
    }

    @Test
    fun retry_resendsLastUserTurnAfterError() = runTest(dispatcher) {
        val responses = ArrayDeque(
            listOf<Flow<GeminiChunk>>(
                flowOf(GeminiChunk.Error(GeminiErrorKind.Network, "x")),
                flowOf(GeminiChunk.Delta("ok"), GeminiChunk.Done),
            ),
        )
        val fake = object : GeminiClient() {
            override fun stream(
                apiKey: String, model: String, systemInstruction: String, messages: List<AiMessage>,
            ): Flow<GeminiChunk> = responses.removeFirst()
        }
        val vm = vmWith(fake)
        vm.start("v1"); advanceUntilIdle()
        vm.onInputChange("where?"); vm.send(); advanceUntilIdle()
        assertNotNull(vm.uiState.value.errorBanner)

        vm.retry(); advanceUntilIdle()
        val msgs = vm.uiState.value.messages
        assertEquals(2, msgs.size)
        assertEquals("where?", msgs[0].text)
        assertEquals("ok", msgs[1].text)
        assertNull(vm.uiState.value.errorBanner)
    }

    @Test
    fun clearSession_wipesMessages() = runTest(dispatcher) {
        val fake = object : GeminiClient() {
            override fun stream(
                apiKey: String, model: String, systemInstruction: String, messages: List<AiMessage>,
            ): Flow<GeminiChunk> = flowOf(GeminiChunk.Delta("x"), GeminiChunk.Done)
        }
        val vm = vmWith(fake)
        vm.start("v1")
        advanceUntilIdle()
        vm.onInputChange("hi"); vm.send(); advanceUntilIdle()
        vm.clearSession(); advanceUntilIdle()
        assertEquals(0, vm.uiState.value.messages.size)
    }
}
