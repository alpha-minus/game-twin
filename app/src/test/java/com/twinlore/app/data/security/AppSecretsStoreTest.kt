package com.twinlore.app.data.security

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The secrets store, driven over a plain SharedPreferences rather than the encrypted one: the
 * Keystore the production factory reaches for does not exist off-device, and nothing here is
 * about the encryption. Same discipline as [RetroAchievementsCredentialsRepositoryTest].
 */
@RunWith(RobolectricTestRunner::class)
class AppSecretsStoreTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun store() = AppSecretsStore(context) { ctx ->
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    @Test
    fun `a stored secret round-trips`() = runTest {
        val secrets = store()
        secrets.set(AppSecret.GeminiApiKey, "my-gemini-key")

        assertEquals("my-gemini-key", secrets.get(AppSecret.GeminiApiKey))
        assertEquals("my-gemini-key", secrets.flow(AppSecret.GeminiApiKey).first())
    }

    @Test
    fun `a secret survives a new store instance`() = runTest {
        store().set(AppSecret.LicenseKey, "AAAA-BBBB")

        assertEquals("AAAA-BBBB", store().get(AppSecret.LicenseKey))
    }

    @Test
    fun `an unset secret reads as null`() = runTest {
        assertNull(store().get(AppSecret.SteamApiKey))
        assertNull(store().flow(AppSecret.SteamApiKey).first())
    }

    @Test
    fun `null removes a stored secret from the backing preferences`() = runTest {
        val secrets = store()
        secrets.set(AppSecret.CustomerEmail, "player@example.com")

        secrets.set(AppSecret.CustomerEmail, null)

        assertNull(secrets.get(AppSecret.CustomerEmail))
        assertFalse(
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .contains(AppSecret.CustomerEmail.storageKey)
        )
    }

    @Test
    fun `a blank value removes rather than storing whitespace`() = runTest {
        val secrets = store()
        secrets.set(AppSecret.TheGamesDbApiKey, "real-key")

        secrets.set(AppSecret.TheGamesDbApiKey, "   ")

        assertNull(secrets.get(AppSecret.TheGamesDbApiKey))
        assertNull(secrets.flow(AppSecret.TheGamesDbApiKey).first())
    }

    @Test
    fun `secrets are independent of each other`() = runTest {
        val secrets = store()
        secrets.set(AppSecret.SteamGridApiKey, "grid-key")

        assertEquals("grid-key", secrets.get(AppSecret.SteamGridApiKey))
        assertNull(secrets.get(AppSecret.SteamApiKey))
        assertNull(secrets.get(AppSecret.LicenseKey))
    }

    @Test
    fun `the flow follows a change made after first collection`() = runTest {
        val secrets = store()
        assertNull(secrets.flow(AppSecret.GeminiApiKey).first())

        secrets.set(AppSecret.GeminiApiKey, "late-key")

        assertEquals("late-key", secrets.flow(AppSecret.GeminiApiKey).first())
    }

    private companion object {
        const val PREFS = "app_secrets_test"
    }
}
