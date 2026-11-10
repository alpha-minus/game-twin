package com.twinlore.app.data.retroachievements

import com.twinlore.app.data.local.RaGameCacheDao
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * What a failed RetroAchievements progress request reports.
 *
 * Probed 2026-09-17: RetroAchievements answers an unknown game ID and an unknown
 * username alike with HTTP 200 and `[]`, a bad key with 401, and bursts with a
 * 429 page. The repository used to swallow all of it into null, so the drawer
 * could only ever say "Could not load achievements". Served through a real
 * Retrofit + Gson stack so the `[]` goes through the same parsing as on device.
 */
class RaGameCacheRepositoryErrorTest {

    private lateinit var server: MockWebServer

    @Before fun setUp() { server = MockWebServer() }
    @After fun tearDown() { server.shutdown() }

    private fun serve(progress: MockResponse, game: MockResponse = MockResponse().setBody("[]")) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.path.orEmpty().contains("API_GetGameInfoAndUserProgress") -> progress
                request.path.orEmpty().contains("API_GetGame.php") -> game
                else -> MockResponse().setBody("[]")
            }
        }
        server.start()
    }

    private fun repo(): RaGameCacheRepository {
        val api = Retrofit.Builder()
            .baseUrl(server.url("/API/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(RetroAchievementsApi::class.java)
        val client = mock<RetroAchievementsApiClient> { on { this.api } doReturn api }
        val creds = mock<RetroAchievementsCredentialsRepository> {
            onBlocking { getCredentials() } doReturn RaCredentials("nobodyhere", "key")
        }
        val dao = mock<RaGameCacheDao> { onBlocking { getByVaultId(any()) } doReturn null }
        return RaGameCacheRepository(dao, client, creds)
    }

    private suspend fun failureOf(block: suspend () -> Unit): Exception {
        try {
            block()
        } catch (e: Exception) {
            return e
        }
        fail("expected a failure")
        throw AssertionError()
    }

    @Test
    fun `an empty answer for a game that exists means the user does not`() = runTest {
        serve(
            progress = MockResponse().setBody("[]"),
            game = MockResponse().setBody("""{"Title":"Sonic the Hedgehog","ConsoleID":1}"""),
        )
        val e = failureOf { repo().getGameProgress("v1", 1, forceRefresh = true) }
        assertEquals(RaUserNotFoundException::class, e::class)
        assertEquals("nobodyhere", (e as RaUserNotFoundException).username)
    }

    @Test
    fun `an empty answer for a game that does not exist means the game does not`() = runTest {
        serve(progress = MockResponse().setBody("[]"), game = MockResponse().setBody("[]"))
        val e = failureOf { repo().getGameProgress("v1", 99999999, forceRefresh = true) }
        assertEquals(RaGameNotFoundException::class, e::class)
        assertEquals(99999999, (e as RaGameNotFoundException).gameId)
    }

    @Test
    fun `a rejected key is reported, not swallowed`() = runTest {
        serve(progress = MockResponse().setResponseCode(401).setBody("""{"message":"Unauthenticated."}"""))
        val e = failureOf { repo().getGameProgress("v1", 1, forceRefresh = true) }
        assertEquals(401, (e as HttpException).code())
    }

    @Test
    fun `rate limiting with nothing cached surfaces the 429`() = runTest {
        serve(progress = MockResponse().setResponseCode(429).setBody("<html>429 Too Many Requests</html>"))
        val e = failureOf { repo().getGameProgress("v1", 1, forceRefresh = true) }
        assertEquals(429, (e as HttpException).code())
    }

    @Test
    fun `a manual game id that does not exist is reported as not found`() = runTest {
        serve(progress = MockResponse().setBody("[]"), game = MockResponse().setBody("[]"))
        val e = failureOf { repo().requireGame(99999999) }
        assertEquals(RaGameNotFoundException::class, e::class)
    }

    @Test
    fun `a manual game id that exists returns its title`() = runTest {
        serve(
            progress = MockResponse().setBody("[]"),
            game = MockResponse().setBody("""{"Title":"Sonic the Hedgehog","ConsoleID":1}"""),
        )
        assertEquals("Sonic the Hedgehog", repo().requireGame(1).title)
    }
}
