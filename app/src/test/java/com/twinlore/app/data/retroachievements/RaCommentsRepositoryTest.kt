package com.twinlore.app.data.retroachievements

import android.content.Context
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.mock
import java.io.File
import java.io.IOException

/**
 * A comment thread costs one request per achievement, so it is stored the
 * first time it is opened and reused, and a failed refresh serves the copy.
 */
class RaCommentsRepositoryTest {

    @get:Rule val tmp = TemporaryFolder()

    private var calls = 0
    private var fail = false
    private var ownKey = false
    private val requested = mutableListOf<Int>()

    private fun TestScope.repo(): RaCommentsRepository {
        val api = mock<RetroAchievementsApi> {
            onBlocking { getAchievementComments(any(), any(), any(), any()) } doSuspendableAnswer {
                calls++
                requested += it.getArgument<Int>(2)
                if (fail) throw IOException("offline")
                RaCommentsResponse(results = listOf(RaCommentDto(user = "u", submitted = "s", commentText = "call $calls")))
            }
        }
        val client = mock<RetroAchievementsApiClient> { on { this.api } doReturn api }
        val creds = mock<RetroAchievementsCredentialsRepository> {
            onBlocking { getCredentials() } doReturn null
            on { hasCustomApiKey() } doAnswer { ownKey }
        }
        val context = mock<Context> { on { filesDir } doReturn tmp.root }
        return RaCommentsRepository(context, client, creds, backgroundScope)
    }

    @Test
    fun `a thread opened twice costs one request`() = runTest {
        val repo = repo()
        assertEquals("call 1", repo.getComments(7).single().commentText)
        assertEquals("call 1", repo.getComments(7).single().commentText)
        assertEquals(1, calls)
    }

    @Test
    fun `a stale thread is refetched`() = runTest {
        val repo = repo()
        repo.getComments(7)
        File(tmp.root, "ra_comments/7.json").setLastModified(System.currentTimeMillis() - RaCommentsRepository.FRESH_MS - 1000)
        assertEquals("call 2", repo.getComments(7).single().commentText)
    }

    @Test
    fun `a failed refresh serves the stored thread`() = runTest {
        val repo = repo()
        repo.getComments(7)
        fail = true
        assertEquals("call 1", repo.getComments(7, forceRefresh = true).single().commentText)
    }

    @Test
    fun `a failure with nothing stored rethrows`() = runTest {
        val repo = repo()
        fail = true
        try {
            repo.getComments(7)
            fail("expected the fetch error")
        } catch (e: IOException) {
            assertEquals("offline", e.message)
        }
    }

    // ----- Prefetch -----

    @Test
    fun `no prefetch on the bundled key`() = runTest {
        val repo = repo()
        assertEquals(0, repo.prefetch(listOf(1, 2, 3)))
        assertEquals(0, calls)
    }

    @Test
    fun `an own key fills every thread, spaced out`() = runTest {
        ownKey = true
        val repo = repo()
        val start = currentTime
        assertEquals(3, repo.prefetch(listOf(1, 2, 3)))
        assertEquals(listOf(1, 2, 3), requested)
        assertEquals("two gaps between three requests", 2 * RaCommentsRepository.PREFETCH_GAP_MS, currentTime - start)
        // And a thread opened afterwards costs nothing.
        repo.getComments(2)
        assertEquals(3, calls)
    }

    @Test
    fun `a prefetch skips threads already fresh on disk`() = runTest {
        ownKey = true
        val repo = repo()
        repo.getComments(2)
        requested.clear()
        assertEquals(2, repo.prefetch(listOf(1, 2, 3)))
        assertEquals(listOf(1, 3), requested)
    }

    @Test
    fun `a prefetch stops at the first failure`() = runTest {
        ownKey = true
        val repo = repo()
        fail = true
        assertEquals(0, repo.prefetch(listOf(1, 2, 3)))
        assertEquals("one failed request, not three", 1, calls)
    }
}
