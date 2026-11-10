package com.twinlore.app.data.local

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.util.Date

/**
 * In-memory Room tests for [VaultDao.count].
 */
@RunWith(RobolectricTestRunner::class)
class VaultDaoCountTest {

    @get:Rule val instantRule = InstantTaskExecutorRule()

    private lateinit var db: VaultDatabase
    private lateinit var dao: VaultDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            VaultDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.vaultDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `count returns zero when empty`() = runTest {
        assertEquals(0, dao.count())
    }

    @Test
    fun `count returns one after single insert`() = runTest {
        dao.insertVault(testVault(id = "v1", name = "Vault 1"))
        assertEquals(1, dao.count())
    }

    @Test
    fun `count returns two after two inserts`() = runTest {
        dao.insertVault(testVault(id = "v1", name = "Vault 1"))
        dao.insertVault(testVault(id = "v2", name = "Vault 2"))
        assertEquals(2, dao.count())
    }

    @Test
    fun `count decrements after delete`() = runTest {
        dao.insertVault(testVault(id = "v1", name = "Vault 1"))
        dao.insertVault(testVault(id = "v2", name = "Vault 2"))
        dao.deleteVaultById("v1")
        assertEquals(1, dao.count())
    }

    private fun testVault(id: String, name: String): VaultEntity = VaultEntity(
        id = id,
        name = name,
        romPath = null,
        romFilename = null,
        artworkVibrantArgb = null,
        systemeid = null,
        gameId = null,
        source = null,
        raGameId = null,
        steamAppId = null,
        lastOpened = null,
        createdAt = Date(0),
    )
}
