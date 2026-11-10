package com.twinlore.app.data.scraping

import com.twinlore.app.data.SettingsRepository
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class TheGamesDbArtworkScraperTest {

    private lateinit var server: MockWebServer
    private lateinit var settings: SettingsRepository
    private lateinit var scraper: TheGamesDbArtworkScraper

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()

        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(OkHttpClient())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(TheGamesDbApi::class.java)

        settings = mock()
        whenever(settings.theGamesDbApiKeyOverride).thenReturn(flowOf("test-key"))

        scraper = TheGamesDbArtworkScraper(api, settings)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `happy path returns normalized artwork`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "code": 200, "status": "Success",
                  "data": {
                    "count": 1,
                    "games": [{"id": 1234, "game_title": "Some Obscure Game"}]
                  },
                  "include": {}, "pages": {}
                }
                """.trimIndent()
            )
        )
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "code": 200, "status": "Success",
                  "data": {
                    "count": 5,
                    "base_url": {
                      "original": "https://cdn.thegamesdb.net/images/original/",
                      "large": "https://cdn.thegamesdb.net/images/large/"
                    },
                    "images": {
                      "1234": [
                        {"id":1,"type":"boxart","side":"front","filename":"boxart/front/1234-1.jpg","resolution":"600x900"},
                        {"id":2,"type":"boxart","side":"back","filename":"boxart/back/1234-2.jpg","resolution":"600x900"},
                        {"id":3,"type":"fanart","filename":"fanart/1234-3.jpg","resolution":"1920x1080"},
                        {"id":4,"type":"clearlogo","filename":"clearlogo/1234-4.png","resolution":"800x400"},
                        {"id":5,"type":"screenshot","filename":"screenshot/1234-5.jpg","resolution":"1920x1080"}
                      ]
                    }
                  },
                  "include": {}, "pages": {}
                }
                """.trimIndent()
            )
        )

        val result = scraper.scrape(
            "some obscure game",
            setOf(ArtworkType.GRID, ArtworkType.HERO, ArtworkType.LOGO)
        )

        assertNotNull(result)
        assertEquals("thegamesdb", result!!.provider)
        assertEquals("1234", result.gameId)
        assertEquals("Some Obscure Game", result.gameName)

        // boxart back is dropped, only front maps to grid
        assertEquals(1, result.grids.size)
        assertEquals("https://cdn.thegamesdb.net/images/original/boxart/front/1234-1.jpg", result.grids[0].url)
        assertEquals(600, result.grids[0].width)
        assertEquals(900, result.grids[0].height)

        assertEquals(1, result.heroes.size)
        assertEquals("https://cdn.thegamesdb.net/images/original/fanart/1234-3.jpg", result.heroes[0].url)

        assertEquals(1, result.logos.size)
        assertEquals("https://cdn.thegamesdb.net/images/original/clearlogo/1234-4.png", result.logos[0].url)
    }

    @Test
    fun `empty search returns null`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"code":200,"status":"Success","data":{"count":0,"games":[]},"include":{},"pages":{}}"""
            )
        )

        val result = scraper.scrape("nothing", setOf(ArtworkType.GRID))
        assertNull(result)
    }

    @Test
    fun `429 maps to RateLimited`() = runTest {
        server.enqueue(MockResponse().setResponseCode(429))
        assertThrows(ScrapeError.RateLimited::class.java) {
            runBlocking { scraper.scrape("x", setOf(ArtworkType.GRID)) }
        }
    }

    @Test
    fun `503 maps to Unavailable`() = runTest {
        server.enqueue(MockResponse().setResponseCode(503))
        assertThrows(ScrapeError.Unavailable::class.java) {
            runBlocking { scraper.scrape("x", setOf(ArtworkType.GRID)) }
        }
    }

    @Test
    fun `user override key takes precedence`() = runTest {
        whenever(settings.theGamesDbApiKeyOverride).thenReturn(flowOf("override-key"))
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"code":200,"status":"Success","data":{"count":0,"games":[]},"include":{},"pages":{}}"""
            )
        )

        scraper.scrape("x", setOf(ArtworkType.GRID))

        val req = server.takeRequest()
        assertTrue(req.path!!.contains("apikey=override-key"))
    }
}
