package com.twinlore.app.data.retroachievements

import android.content.Context
import androidx.test.core.app.ApplicationProvider
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
import org.robolectric.annotation.Config

/**
 * Credential storage, driven over a plain SharedPreferences rather than the encrypted store: the
 * Keystore the production factory reaches for does not exist off-device, and nothing here is about
 * the encryption.
 *
 * The case that mattered: an API key entered before any account was configured. It used to be saved
 * through setCredentials(username, key), which needs a username to write one, so the save returned
 * early and the key was dropped without a word. Setting a username first made the identical edit
 * stick, which is what the report described.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RetroAchievementsCredentialsRepositoryTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun repository() = RetroAchievementsCredentialsRepository(context) { ctx ->
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    @Test
    fun `an api key set with no username is stored`() = runTest {
        val repo = repository()
        assertFalse(repo.hasCustomApiKey())

        repo.setApiKeyOverride("my-own-key")

        assertTrue(repo.hasCustomApiKey())
        assertEquals("my-own-key", repo.getCustomApiKey())
        // No account yet, so there are still no credentials to hand out - only a key.
        assertNull(repo.getCredentials())
    }

    @Test
    fun `a username set afterwards joins the key already stored`() = runTest {
        val repo = repository()
        repo.setApiKeyOverride("my-own-key")

        repo.setCredentials("player", "my-own-key")

        assertTrue(repo.hasCustomApiKey())
        assertEquals("my-own-key", repo.getCustomApiKey())
        val creds = repo.getCredentials()
        assertEquals("player", creds?.username)
        assertEquals("my-own-key", creds?.apiKey)
    }

    @Test
    fun `changing the key with an account set keeps the username`() = runTest {
        val repo = repository()
        repo.setCredentials("player", "first-key")

        repo.setApiKeyOverride("second-key")

        assertEquals("second-key", repo.getCustomApiKey())
        assertEquals("player", repo.getCredentials()?.username)
        assertEquals("second-key", repo.getCredentials()?.apiKey)
    }

    /**
     * BUNDLED_API_KEY is now a build-time value decoded from BuildConfig (itself sourced from
     * `retroAchievementsApiKey` in local.properties), not a hardcoded literal. On a checkout or CI
     * run with no key configured it is empty, and RaCredentials.isValid() rejects a blank api key -
     * so getCredentials() returns null outright rather than an account with an empty key. Both
     * outcomes below are correct: which one a given run hits depends only on whether the build has a
     * bundled key, not on whether the account was preserved.
     */
    @Test
    fun `clearing the key falls back to the bundled default when one is configured, otherwise drops the credentials`() = runTest {
        val repo = repository()
        repo.setCredentials("player", "first-key")

        repo.setApiKeyOverride(null)

        assertFalse(repo.hasCustomApiKey())
        assertNull(repo.getCustomApiKey())

        val bundledKey = RetroAchievementsCredentialsRepository.BUNDLED_API_KEY
        if (bundledKey.isNotEmpty()) {
            // A build with retroAchievementsApiKey set: the bundled default kicks in and the
            // account (username) survives.
            assertEquals("player", repo.getCredentials()?.username)
            assertEquals(bundledKey, repo.getCredentials()?.apiKey)
        } else {
            // No bundled key in this build: a blank api key fails RaCredentials.isValid(), so
            // getCredentials() is null entirely - not a lost account, just nothing to hand out
            // until the user supplies their own key.
            assertNull(repo.getCredentials())
        }
    }

    /** The settings row reads the key off this flow, so a save with no account must reach it. */
    @Test
    fun `the key flow carries a standalone save`() = runTest {
        val repo = repository()
        assertNull(repo.apiKeyOverride.first())

        repo.setApiKeyOverride("my-own-key")

        assertEquals("my-own-key", repo.apiKeyOverride.first())
        // And back to the bundled default when it is taken away again.
        repo.setApiKeyOverride(null)
        assertNull(repo.apiKeyOverride.first())
    }

    /**
     * Disconnecting the account must not take the key with it. clearCredentials() used to wipe the
     * whole preferences file, which was harmless while a key could only be written alongside a
     * username; now that the key stands on its own, that deleted something the user had chosen.
     */
    @Test
    fun `clearing the account keeps the key`() = runTest {
        val repo = repository()
        repo.setApiKeyOverride("my-own-key")
        repo.setCredentials("player", "my-own-key")

        repo.clearCredentials()

        assertNull("the account is gone", repo.getCredentials())
        assertTrue(repo.hasCustomApiKey())
        assertEquals("my-own-key", repo.getCustomApiKey())
        assertEquals("my-own-key", repo.apiKeyOverride.first())
    }

    /**
     * The same path a failed validation takes: a mistyped username used to delete the key the user
     * had just saved, locking the Journey screen for a reason nobody had chosen.
     */
    @Test
    fun `a failed validation clearing the account leaves the key to try again with`() = runTest {
        val repo = repository()
        repo.setApiKeyOverride("my-own-key")

        // What validateAndSaveRetroAchievementsCredentials does when the API rejects the username.
        repo.clearCredentials()

        assertEquals("my-own-key", repo.getCustomApiKey())
        assertTrue(repo.hasCustomApiKey())
    }

    /**
     * Removing the key itself is its own explicit act, and it leaves the account alone.
     *
     * The final check can't just read `getCredentials()?.username` unconditionally: like the
     * fallback test above, that goes through `RaCredentials.isValid()`, which rejects a blank
     * `BUNDLED_API_KEY` on a build with no `retroAchievementsApiKey` configured - and would fail
     * this assertion for a reason that has nothing to do with whether the account was kept. The
     * repository has no accessor that reads the stored username without going through validity, so
     * branch on the bundled key instead, the same way the fallback test does.
     */
    @Test
    fun `clearing the key explicitly keeps the account`() = runTest {
        val repo = repository()
        repo.setCredentials("player", "my-own-key")

        repo.setApiKeyOverride(null)

        assertFalse(repo.hasCustomApiKey())
        assertNull(repo.getCustomApiKey())
        assertNull(repo.apiKeyOverride.first())

        if (RetroAchievementsCredentialsRepository.BUNDLED_API_KEY.isNotEmpty()) {
            // A build with a bundled key: the account is still there, backed by that default.
            assertEquals("player", repo.getCredentials()?.username)
        } else {
            // No bundled key in this build: the blank default fails isValid(), so getCredentials()
            // is null - not evidence the account was dropped.
            assertNull(repo.getCredentials())
        }
    }

    @Test
    fun `a blank key clears rather than storing an empty string`() = runTest {
        val repo = repository()
        repo.setApiKeyOverride("my-own-key")

        repo.setApiKeyOverride("   ")

        assertFalse(repo.hasCustomApiKey())
        assertNull(repo.getCustomApiKey())
    }

    private companion object {
        const val PREFS = "ra_credentials_test"
    }
}
