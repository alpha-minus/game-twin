package com.twinlore.app.data.privacy

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PrivacyPreferencesTest {

    @Test
    fun `enabled defaults to true before first emission`() {
        // Source emits false. We assert enabled is still true BEFORE the dispatcher runs the
        // collector. This unambiguously proves the constructor default holds even when the
        // persisted value differs. Then advanceUntilIdle and assert the emission has been
        // tracked.
        val source = MutableStateFlow(false)
        val scope = TestScope(StandardTestDispatcher())
        val prefs = PrivacyPreferences(source, scope)
        assertEquals(true, prefs.enabled)
        scope.advanceUntilIdle()
        assertEquals(false, prefs.enabled)
    }

    @Test
    fun `enabled tracks flow emissions`() = runTest(UnconfinedTestDispatcher()) {
        val source = MutableStateFlow(true)
        val prefs = PrivacyPreferences(source, backgroundScope)
        advanceUntilIdle()
        assertEquals(true, prefs.enabled)
        source.value = false
        advanceUntilIdle()
        assertEquals(false, prefs.enabled)
        source.value = true
        advanceUntilIdle()
        assertEquals(true, prefs.enabled)
    }
}
