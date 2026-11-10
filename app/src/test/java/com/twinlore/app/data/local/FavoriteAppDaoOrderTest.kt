package com.twinlore.app.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Pin order: getAll honors position, pin() appends, setOrder rewrites as one block. */
@RunWith(RobolectricTestRunner::class)
class FavoriteAppDaoOrderTest {

    private lateinit var db: VaultDatabase
    private lateinit var dao: FavoriteAppDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            VaultDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.favoriteAppDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun getAll_orders_by_position_not_addedAt() = runTest {
        // Newest addedAt but first position must come first.
        dao.insert(FavoriteAppEntity("app.b", addedAt = 200, position = 1))
        dao.insert(FavoriteAppEntity("app.a", addedAt = 900, position = 0))

        assertEquals(listOf("app.a", "app.b"), dao.getAll().first().map { it.packageName })
    }

    @Test
    fun pin_appends_at_the_end() = runTest {
        dao.insert(FavoriteAppEntity("app.a", addedAt = 100, position = 0))
        dao.insert(FavoriteAppEntity("app.b", addedAt = 200, position = 1))

        dao.pin("app.c")

        assertEquals(
            listOf("app.a", "app.b", "app.c"),
            dao.getAll().first().map { it.packageName },
        )
    }

    @Test
    fun pin_into_empty_table_starts_at_zero() = runTest {
        dao.pin("app.solo")
        assertEquals(0, dao.getAll().first().single().position)
    }

    @Test
    fun setOrder_rewrites_positions() = runTest {
        dao.pin("app.a")
        dao.pin("app.b")
        dao.pin("app.c")

        dao.setOrder(listOf("app.c", "app.a", "app.b"))

        assertEquals(
            listOf("app.c", "app.a", "app.b"),
            dao.getAll().first().map { it.packageName },
        )
    }
}
