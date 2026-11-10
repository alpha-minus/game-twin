package com.twinlore.app.data

import com.twinlore.app.data.library.MediaSearchPathRepository
import com.twinlore.app.data.library.ResourceResolver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class PersistedFolderValidatorTest {

    private lateinit var settings: SettingsRepository
    private lateinit var mediaSearchPaths: MediaSearchPathRepository
    private lateinit var probe: FakeUriProbe
    private lateinit var resourceResolver: ResourceResolver
    private lateinit var validator: PersistedFolderValidator

    private val homeFlow = MutableStateFlow<String?>(null)
    private val esdeFlow = MutableStateFlow<String?>(null)
    private val cocoonFlow = MutableStateFlow<String?>(null)

    @Before
    fun setup() {
        settings = mock()
        mediaSearchPaths = mock()
        whenever(settings.homeFolderUri).thenReturn(homeFlow)
        whenever(settings.esdeFolderUri).thenReturn(esdeFlow)
        whenever(settings.cocoonFolderUri).thenReturn(cocoonFlow)
        probe = FakeUriProbe()
        resourceResolver = mock()
        validator = PersistedFolderValidator(settings, mediaSearchPaths, probe, resourceResolver)
    }

    @Test
    fun `all URIs null returns EMPTY and touches nothing`() = runTest {
        val result = validator.validate()

        assertEquals(ValidationResult.EMPTY, result)
        verify(settings, never()).setHomeFolderUri(any())
        verify(settings, never()).setEsdeFolderUri(any())
        verify(settings, never()).setCocoonFolderUri(any())
        verify(mediaSearchPaths, never()).syncAutoFromSettings()
        verify(resourceResolver, never()).invalidateAll()
    }

    @Test
    fun `home URI present and ok returns EMPTY and does not clear`() = runTest {
        homeFlow.value = "content://home/uri"
        probe.directoryAnswers["content://home/uri"] = { true }

        val result = validator.validate()

        assertEquals(ValidationResult.EMPTY, result)
        verify(settings, never()).setHomeFolderUri(any())
        verify(resourceResolver, never()).invalidateAll()
    }

    @Test
    fun `home URI lost reports lostHome and clears it`() = runTest {
        homeFlow.value = "content://home/uri"
        probe.directoryAnswers["content://home/uri"] = { false }

        val result = validator.validate()

        assertEquals(true, result.lostHome)
        assertEquals(emptySet<AssetSource>(), result.lostAssetSources)
        verify(settings).setHomeFolderUri(null)
        verify(mediaSearchPaths).syncAutoFromSettings()
        // The home folder's resolved tree Uri just changed (to "none"); every
        // Uri the resolver cached against the old tree is now stale.
        verify(resourceResolver).invalidateAll()
    }

    @Test
    fun `esde lost clears esde and reconciles`() = runTest {
        esdeFlow.value = "content://esde/uri"
        probe.directoryAnswers["content://esde/uri"] = { false }

        val result = validator.validate()

        assertEquals(false, result.lostHome)
        assertEquals(setOf(AssetSource.ESDE), result.lostAssetSources)
        verify(settings).setEsdeFolderUri(null)
        verify(settings, never()).setCocoonFolderUri(any())
        verify(mediaSearchPaths).syncAutoFromSettings()
    }

    @Test
    fun `cocoon lost clears cocoon and reconciles`() = runTest {
        cocoonFlow.value = "content://cocoon/uri"
        probe.directoryAnswers["content://cocoon/uri"] = { false }

        val result = validator.validate()

        assertEquals(setOf(AssetSource.COCOON), result.lostAssetSources)
        verify(settings).setCocoonFolderUri(null)
        verify(mediaSearchPaths).syncAutoFromSettings()
    }

    @Test
    fun `both asset URIs lost calls reconcile only once`() = runTest {
        esdeFlow.value = "content://esde/uri"
        cocoonFlow.value = "content://cocoon/uri"
        probe.directoryAnswers["content://esde/uri"] = { false }
        probe.directoryAnswers["content://cocoon/uri"] = { false }

        val result = validator.validate()

        assertEquals(setOf(AssetSource.ESDE, AssetSource.COCOON), result.lostAssetSources)
        verify(mediaSearchPaths).syncAutoFromSettings()
    }

    @Test
    fun `asset URI ok does not clear or reconcile`() = runTest {
        esdeFlow.value = "content://esde/uri"
        probe.directoryAnswers["content://esde/uri"] = { true }

        val result = validator.validate()

        assertEquals(ValidationResult.EMPTY, result)
        verify(settings, never()).setEsdeFolderUri(any())
        verify(mediaSearchPaths, never()).syncAutoFromSettings()
    }

    @Test
    fun `all three URIs lost reports all losses and reconciles once`() = runTest {
        homeFlow.value = "content://home/uri"
        esdeFlow.value = "content://esde/uri"
        cocoonFlow.value = "content://cocoon/uri"
        probe.directoryAnswers["content://home/uri"] = { false }
        probe.directoryAnswers["content://esde/uri"] = { false }
        probe.directoryAnswers["content://cocoon/uri"] = { false }

        val result = validator.validate()

        assertEquals(true, result.lostHome)
        assertEquals(setOf(AssetSource.ESDE, AssetSource.COCOON), result.lostAssetSources)
        verify(settings).setHomeFolderUri(null)
        verify(settings).setEsdeFolderUri(null)
        verify(settings).setCocoonFolderUri(null)
        verify(mediaSearchPaths).syncAutoFromSettings()
        verify(resourceResolver).invalidateAll()
    }

    @Test
    fun `missing persisted permission marks URI lost without cursor query`() = runTest {
        homeFlow.value = "content://home/uri"
        probe.permissions["content://home/uri"] = false
        // probeIsDirectory should never be called; track it here:
        var probeIsDirectoryInvocations = 0
        probe.directoryAnswers["content://home/uri"] = {
            probeIsDirectoryInvocations++
            true // would falsely report OK if it ran
        }

        val result = validator.validate()

        assertEquals(true, result.lostHome)
        assertEquals(0, probeIsDirectoryInvocations)
        verify(settings).setHomeFolderUri(null)
        verify(resourceResolver).invalidateAll()
    }

    @Test
    fun `probe throws once then succeeds is marked ok`() = runTest {
        homeFlow.value = "content://home/uri"
        var calls = 0
        probe.directoryAnswers["content://home/uri"] = {
            calls++
            if (calls == 1) throw RuntimeException("transient SAF stall")
            true
        }

        val result = validator.validate()

        assertEquals(ValidationResult.EMPTY, result)
        assertEquals(2, calls)
    }

    @Test
    fun `probe throws twice is marked lost`() = runTest {
        homeFlow.value = "content://home/uri"
        var calls = 0
        probe.directoryAnswers["content://home/uri"] = {
            calls++
            throw RuntimeException("permanent SAF failure")
        }

        val result = validator.validate()

        assertEquals(true, result.lostHome)
        assertEquals(2, calls)
        verify(settings).setHomeFolderUri(null)
        verify(resourceResolver).invalidateAll()
    }

    @Test
    fun `probe that exceeds timeout is marked lost`() = runTest {
        homeFlow.value = "content://home/uri"
        probe.directoryAnswers["content://home/uri"] = {
            kotlinx.coroutines.delay(10_000) // virtual time; never returns within timeout
            true
        }
        validator.probeTimeoutMs = 5L
        validator.retryDelayMs = 1L

        val result = validator.validate()

        assertEquals(true, result.lostHome)
        verify(settings).setHomeFolderUri(null)
        verify(resourceResolver).invalidateAll()
    }

    @Test
    fun `validator-internal exception degrades to EMPTY`() = runTest {
        // Make settings throw on read; simulates a corrupted DataStore.
        whenever(settings.homeFolderUri).thenThrow(RuntimeException("DataStore corrupt"))

        val result = validator.validate()

        assertEquals(ValidationResult.EMPTY, result)
        verify(settings, never()).setHomeFolderUri(any())
    }

    @Test
    fun `cancellation propagates and is not swallowed`() = runTest {
        homeFlow.value = "content://home/uri"
        probe.directoryAnswers["content://home/uri"] = {
            // Suspend forever; we will cancel the outer coroutine.
            kotlinx.coroutines.awaitCancellation()
        }

        val deferred = async { validator.validate() }
        // Let validate() reach the probe.
        kotlinx.coroutines.yield()
        deferred.cancel()

        try {
            deferred.await()
            org.junit.Assert.fail("Expected CancellationException to propagate")
        } catch (expected: kotlinx.coroutines.CancellationException) {
            // OK; cancellation propagated.
        }
    }
}

/** Fake [UriProbe] driven by per-URI maps so each test sets exactly the answers it needs. */
class FakeUriProbe : UriProbe {
    val permissions: MutableMap<String, Boolean> = mutableMapOf()
    val directoryAnswers: MutableMap<String, suspend () -> Boolean> = mutableMapOf()

    override fun hasPersistedPermission(uriString: String): Boolean =
        permissions.getOrDefault(uriString, true)

    /**
     * The validator only ever probes bare tree URIs, for which "covered by a grant"
     * and "granted outright" are the same question, so this defers to [permissions]
     * rather than growing a second map no test would set.
     */
    override fun hasCoveringPersistedPermission(uriString: String): Boolean =
        hasPersistedPermission(uriString)

    override suspend fun probeIsDirectory(uriString: String): Boolean {
        val answer = directoryAnswers[uriString] ?: { true }
        return answer()
    }
}
