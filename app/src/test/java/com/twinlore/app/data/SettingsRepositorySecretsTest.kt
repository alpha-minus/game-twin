package com.twinlore.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.twinlore.app.data.security.AppSecretsStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * API keys move out of the plaintext settings DataStore into [AppSecretsStore]. The settings
 * DataStore is a process-wide singleton in the test JVM (same discipline as
 * [SettingsRepositorySupportPromptStateTest]), so every test seeds its own baseline first.
 */
@RunWith(RobolectricTestRunner::class)
class SettingsRepositorySecretsTest {

    private lateinit var context: Context

    private val plaintextKeys = listOf(
        "gemini_api_key", "steam_api_key", "steamgrid_api_key", "thegamesdb_api_key",
    ).map { stringPreferencesKey(it) }

    @Before
    fun setUp() = runTest {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().commit()
        with(SettingsRepository) {
            context.dataStore.edit { prefs -> plaintextKeys.forEach { prefs.remove(it) } }
        }
    }

    private fun secrets() = AppSecretsStore(context) { ctx ->
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    private fun repo() = SettingsRepository(context, secrets())

    private suspend fun seedPlaintext(name: String, value: String) {
        with(SettingsRepository) {
            context.dataStore.edit { it[stringPreferencesKey(name)] = value }
        }
    }

    private suspend fun dataStoreContains(name: String): Boolean = with(SettingsRepository) {
        context.dataStore.data.first().contains(stringPreferencesKey(name))
    }

    private fun secretsFileContains(name: String): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).contains(name)

    @Test
    fun `a plaintext gemini key moves into the secrets store on first read`() = runTest {
        seedPlaintext("gemini_api_key", "old-gemini-key")

        assertEquals("old-gemini-key", repo().geminiApiKey.first())

        assertFalse("plaintext copy must be deleted", dataStoreContains("gemini_api_key"))
        assertTrue("secret must be in the secrets file", secretsFileContains("gemini_api_key"))
    }

    @Test
    fun `reading one key migrates all four`() = runTest {
        seedPlaintext("gemini_api_key", "g")
        seedPlaintext("steam_api_key", "s")
        seedPlaintext("steamgrid_api_key", "sg")
        seedPlaintext("thegamesdb_api_key", "t")

        assertEquals("s", repo().steamApiKeyOverride.first())

        for (key in listOf("gemini_api_key", "steam_api_key", "steamgrid_api_key", "thegamesdb_api_key")) {
            assertFalse("plaintext $key must be deleted", dataStoreContains(key))
            assertTrue("$key must be in the secrets file", secretsFileContains(key))
        }
        assertEquals("g", repo().geminiApiKey.first())
        assertEquals("sg", repo().steamGridApiKey.first())
        assertEquals("t", repo().theGamesDbApiKeyOverride.first())
    }

    @Test
    fun `a newly set key lands in the secrets store and never in the DataStore`() = runTest {
        val repo = repo()
        repo.setGeminiApiKey("fresh-key")

        assertEquals("fresh-key", repo.geminiApiKey.first())
        assertFalse(dataStoreContains("gemini_api_key"))
        assertTrue(secretsFileContains("gemini_api_key"))
    }

    @Test
    fun `an unset steam override still reads as empty string`() = runTest {
        assertEquals("", repo().steamApiKeyOverride.first())
        assertEquals("", repo().theGamesDbApiKeyOverride.first())
    }

    @Test
    fun `clearing the gemini key removes it from the secrets store`() = runTest {
        val repo = repo()
        repo.setGeminiApiKey("to-be-removed")

        repo.setGeminiApiKey(null)

        assertNull(repo.geminiApiKey.first())
        assertFalse(secretsFileContains("gemini_api_key"))
    }

    @Test
    fun `a migrated key can be overwritten and cleared`() = runTest {
        seedPlaintext("steamgrid_api_key", "old")
        val repo = repo()
        assertEquals("old", repo.steamGridApiKey.first())

        repo.setSteamGridApiKey("new")
        assertEquals("new", repo.steamGridApiKey.first())

        repo.setSteamGridApiKey(null)
        assertNull(repo.steamGridApiKey.first())
        assertFalse(secretsFileContains("steamgrid_api_key"))
    }

    private companion object {
        const val PREFS = "settings_secrets_test"
    }
}
