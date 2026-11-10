package com.twinlore.app.journey.data

import com.twinlore.app.data.retroachievements.RaAchievementOfTheWeekResponse
import com.twinlore.app.data.retroachievements.RaCommentsResponse
import com.twinlore.app.data.retroachievements.RaGameExtendedResponse
import com.twinlore.app.data.retroachievements.RaGameInfoAndUserProgressResponse
import com.twinlore.app.data.retroachievements.RaGameProgressionResponse
import com.twinlore.app.data.retroachievements.RaGameSummaryResponse
import com.twinlore.app.data.retroachievements.RaRecentlyPlayedGame
import com.twinlore.app.data.retroachievements.RaUserCompletionProgressResponse
import com.twinlore.app.data.retroachievements.RaUserGameRankAndScoreEntry
import com.twinlore.app.data.retroachievements.RaUserSummaryResponse
import com.twinlore.app.data.retroachievements.RetroAchievementsApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okhttp3.Protocol
import okhttp3.ResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class RaJourneyClientTest {

    private fun httpException(code: Int, retryAfterSeconds: Long? = null): HttpException {
        val request = okhttp3.Request.Builder()
            .url("https://retroachievements.org/API/API_GetUserSummary.php?u=test&y=SECRETKEY")
            .build()
        val responseBuilder = okhttp3.Response.Builder()
            .code(code)
            .protocol(Protocol.HTTP_1_1)
            .message("")
            .request(request)
        if (retryAfterSeconds != null) {
            responseBuilder.header("Retry-After", retryAfterSeconds.toString())
        }
        val body = ResponseBody.create(null, "")
        return HttpException(Response.error<String>(body, responseBuilder.build()))
    }

    private class FakeRetroAchievementsApi(
        private val getUserSummaryImpl: suspend () -> RaUserSummaryResponse,
    ) : RetroAchievementsApi {

        override suspend fun getUserSummary(
            username: String,
            apiKey: String,
            recentGamesCount: Int,
            recentAchievementsCount: Int
        ): RaUserSummaryResponse = getUserSummaryImpl()

        override suspend fun getRecentlyPlayedGames(
            username: String,
            apiKey: String,
            count: Int
        ): List<RaRecentlyPlayedGame> = TODO()

        override suspend fun getGameInfoAndUserProgress(
            username: String,
            apiKey: String,
            gameId: Int,
            includeUserAwards: Int
        ): RaGameInfoAndUserProgressResponse = TODO()

        override suspend fun getGame(apiKey: String, gameId: Int): RaGameSummaryResponse = TODO()

        override suspend fun getUserGameRankAndScore(
            username: String,
            apiKey: String,
            gameId: Int
        ): List<RaUserGameRankAndScoreEntry> = TODO()

        override suspend fun getUserCompletionProgress(
            username: String,
            apiKey: String,
            count: Int,
            offset: Int
        ): RaUserCompletionProgressResponse = TODO()

        override suspend fun getGameExtended(apiKey: String, gameId: Int): RaGameExtendedResponse = TODO()

        override suspend fun getAchievementOfTheWeek(apiKey: String): RaAchievementOfTheWeekResponse = TODO()

        override suspend fun getGameProgression(apiKey: String, gameId: Int): RaGameProgressionResponse = TODO()

        override suspend fun getAchievementComments(
            apiKey: String,
            type: Int,
            achievementId: Int,
            sort: String
        ): RaCommentsResponse = TODO()
    }

    @Test
    fun `serializes calls with 350ms gap`() = runTest {
        val timestamps = mutableListOf<Long>()
        val fakeApi = FakeRetroAchievementsApi(
            getUserSummaryImpl = {
                timestamps.add(testScheduler.currentTime)
                RaUserSummaryResponse()
            }
        )
        val client = RaJourneyClient(fakeApi, nowMillis = { testScheduler.currentTime })

        launch { client.call { it.getUserSummary("u", "k") } }
        launch { client.call { it.getUserSummary("u", "k") } }

        advanceUntilIdle()

        assertEquals(2, timestamps.size)
        assertTrue(
            "expected >= 350ms gap between calls, got ${timestamps[1] - timestamps[0]}",
            timestamps[1] - timestamps[0] >= 350
        )
    }

    @Test
    fun `retries 429 with doubling backoff then succeeds`() = runTest {
        var attempts = 0
        val fakeApi = FakeRetroAchievementsApi(
            getUserSummaryImpl = {
                attempts++
                if (attempts <= 2) throw httpException(429) else RaUserSummaryResponse()
            }
        )
        val client = RaJourneyClient(fakeApi, nowMillis = { testScheduler.currentTime })

        val result = client.call { it.getUserSummary("u", "k") }

        assertEquals(RaUserSummaryResponse(), result)
        assertEquals(3, attempts)
        assertTrue(
            "expected elapsed virtual time >= 2700ms, got ${testScheduler.currentTime}",
            testScheduler.currentTime >= 900 + 1800
        )
    }

    @Test
    fun `throws RaJourneyException after four failures`() = runTest {
        var attempts = 0
        val fakeApi = FakeRetroAchievementsApi(
            getUserSummaryImpl = {
                attempts++
                throw httpException(500)
            }
        )
        val client = RaJourneyClient(fakeApi, nowMillis = { testScheduler.currentTime })

        try {
            client.call { it.getUserSummary("u", "k") }
            throw AssertionError("expected RaJourneyException")
        } catch (e: RaJourneyException) {
            assertEquals("RetroAchievements is rate limiting requests, try again shortly", e.message)
        }

        assertEquals(4, attempts)
    }

    @Test
    fun `honors Retry-After`() = runTest {
        val timestamps = mutableListOf<Long>()
        var attempts = 0
        val fakeApi = FakeRetroAchievementsApi(
            getUserSummaryImpl = {
                timestamps.add(testScheduler.currentTime)
                attempts++
                if (attempts == 1) throw httpException(429, retryAfterSeconds = 5) else RaUserSummaryResponse()
            }
        )
        val client = RaJourneyClient(fakeApi, nowMillis = { testScheduler.currentTime })

        client.call { it.getUserSummary("u", "k") }

        assertEquals(2, timestamps.size)
        assertTrue(
            "expected second attempt >= 5000ms after first, got ${timestamps[1] - timestamps[0]}",
            timestamps[1] - timestamps[0] >= 5000
        )
    }

    @Test
    fun `non-retryable code becomes RaJourneyException immediately`() = runTest {
        var attempts = 0
        val fakeApi = FakeRetroAchievementsApi(
            getUserSummaryImpl = {
                attempts++
                throw httpException(404)
            }
        )
        val client = RaJourneyClient(fakeApi, nowMillis = { testScheduler.currentTime })

        try {
            client.call { it.getUserSummary("u", "k") }
            throw AssertionError("expected RaJourneyException")
        } catch (e: RaJourneyException) {
            val message = requireNotNull(e.message)
            assertFalse("message must not contain query string: $message", message.contains("y="))
            assertFalse("message must not contain URL: $message", message.contains("http"))
            assertFalse("a 4xx the client will not retry must not be offered as retryable", e.retryable)
        }

        assertEquals(1, attempts)
    }

    /**
     * A rate limit is a wait, not a wall: it is retried here, and if it still has not cleared after
     * the last attempt the screen may still offer another press of the button.
     */
    @Test
    fun `a rate limit stays retryable`() = runTest {
        val fakeApi = FakeRetroAchievementsApi(getUserSummaryImpl = { throw httpException(429) })
        val client = RaJourneyClient(fakeApi, nowMillis = { testScheduler.currentTime })

        try {
            client.call { it.getUserSummary("u", "k") }
            throw AssertionError("expected RaJourneyException")
        } catch (e: RaJourneyException) {
            assertTrue(e.retryable)
        }
    }
}
