package com.twinlore.app.data.local

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * In-memory Room tests for [JourneyCacheDao] (journey_game_cache + journey_completion_cache).
 */
@RunWith(RobolectricTestRunner::class)
class JourneyCacheDaoTest {

    @get:Rule val instantRule = InstantTaskExecutorRule()

    private lateinit var db: VaultDatabase
    private lateinit var dao: JourneyCacheDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            VaultDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.journeyCacheDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun gameEntity(
        userName: String = "player1",
        gameId: Int = 1,
        fetchedAt: Long = 1_000L,
        validatorJson: String = "{}",
        payloadJson: String = "{\"id\":1}",
    ) = JourneyGameCacheEntity(
        userName = userName,
        gameId = gameId,
        fetchedAt = fetchedAt,
        validatorJson = validatorJson,
        payloadJson = payloadJson,
    )

    private fun completionEntity(
        userName: String = "player1",
        fetchedAt: Long = 1_000L,
        payloadJson: String = "[]",
    ) = JourneyCompletionCacheEntity(
        userName = userName,
        fetchedAt = fetchedAt,
        payloadJson = payloadJson,
    )

    @Test
    fun `getGame returns null when absent`() = runTest {
        assertNull(dao.getGame("player1", 1))
    }

    @Test
    fun `upsertGame then getGame round trips`() = runTest {
        val entity = gameEntity()
        dao.upsertGame(entity)
        assertEquals(entity, dao.getGame("player1", 1))
    }

    @Test
    fun `upsertGame replaces existing row for same key`() = runTest {
        dao.upsertGame(gameEntity(fetchedAt = 1_000L, payloadJson = "{\"id\":1}"))
        dao.upsertGame(gameEntity(fetchedAt = 2_000L, payloadJson = "{\"id\":2}"))

        val result = dao.getGame("player1", 1)
        assertEquals(2_000L, result?.fetchedAt)
        assertEquals("{\"id\":2}", result?.payloadJson)
        assertEquals(1, dao.countGames())
    }

    @Test
    fun `getCompletion returns null when absent`() = runTest {
        assertNull(dao.getCompletion("player1"))
    }

    @Test
    fun `upsertCompletion then getCompletion round trips`() = runTest {
        val entity = completionEntity()
        dao.upsertCompletion(entity)
        assertEquals(entity, dao.getCompletion("player1"))
    }

    @Test
    fun `upsertCompletion replaces existing row for same userName`() = runTest {
        dao.upsertCompletion(completionEntity(fetchedAt = 1_000L, payloadJson = "[1]"))
        dao.upsertCompletion(completionEntity(fetchedAt = 2_000L, payloadJson = "[1,2]"))

        val result = dao.getCompletion("player1")
        assertEquals(2_000L, result?.fetchedAt)
        assertEquals("[1,2]", result?.payloadJson)
    }

    @Test
    fun `evictOldestGames removes exactly the oldest N by fetchedAt`() = runTest {
        dao.upsertGame(gameEntity(gameId = 1, fetchedAt = 100L))
        dao.upsertGame(gameEntity(gameId = 2, fetchedAt = 300L))
        dao.upsertGame(gameEntity(gameId = 3, fetchedAt = 200L))
        dao.upsertGame(gameEntity(gameId = 4, fetchedAt = 400L))

        dao.evictOldestGames(2)

        assertEquals(2, dao.countGames())
        assertNull(dao.getGame("player1", 1))
        assertNull(dao.getGame("player1", 3))
        assertEquals(300L, dao.getGame("player1", 2)?.fetchedAt)
        assertEquals(400L, dao.getGame("player1", 4)?.fetchedAt)
    }

    @Test
    fun `evictOldestGames with zero removes nothing`() = runTest {
        dao.upsertGame(gameEntity(gameId = 1, fetchedAt = 100L))
        dao.evictOldestGames(0)
        assertEquals(1, dao.countGames())
    }

    @Test
    fun `clearGames wipes journey_game_cache only`() = runTest {
        dao.upsertGame(gameEntity())
        dao.upsertCompletion(completionEntity())

        dao.clearGames()

        assertEquals(0, dao.countGames())
        assertNull(dao.getGame("player1", 1))
        assertEquals("player1", dao.getCompletion("player1")?.userName)
    }

    @Test
    fun `clearCompletion wipes journey_completion_cache only`() = runTest {
        dao.upsertGame(gameEntity())
        dao.upsertCompletion(completionEntity())

        dao.clearCompletion()

        assertNull(dao.getCompletion("player1"))
        assertEquals(1, dao.countGames())
    }
}
