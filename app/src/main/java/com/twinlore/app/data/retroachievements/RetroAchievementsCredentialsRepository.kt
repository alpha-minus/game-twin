package com.twinlore.app.data.retroachievements

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.twinlore.app.BuildConfig
import com.twinlore.app.data.security.ObfuscatedKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores RetroAchievements username and API key in EncryptedSharedPreferences.
 * Do not log credentials.
 */
@Singleton
class RetroAchievementsCredentialsRepository internal constructor(
    private val context: Context,
    /**
     * Where the stored credentials live. Production hands over the encrypted store below; a test
     * hands over a plain SharedPreferences, since the Keystore this class would otherwise reach for
     * does not exist off-device.
     */
    private val prefsFactory: (Context) -> SharedPreferences,
) {
    @Inject constructor(@ApplicationContext context: Context) : this(context, ::createEncryptedPrefs)

    companion object {
        /**
         * Bundled default RA Web API key, used when the user has not entered
         * their own. Decoded from BuildConfig the same way as the Steam,
         * SteamGridDB and TheGamesDB keys; empty in a build without
         * `retroAchievementsApiKey` in local.properties.
         */
        val BUNDLED_API_KEY: String by lazy {
            ObfuscatedKey.decode(BuildConfig.RA_API_KEY_ENCODED, BuildConfig.KEY_OBFUSCATION_MASK)
        }
    }

    private val mutex = Mutex()
    private val bgScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Defer MasterKey + EncryptedSharedPreferences init to first background access.
    private val encryptedPrefs by lazy { prefsFactory(context) }

    private val keyUsername = "ra_username"
    private val keyApiKey = "ra_api_key"

    private val _credentialsFlow = MutableStateFlow<RaCredentials?>(null)
    val credentials: Flow<RaCredentials?> = _credentialsFlow.asStateFlow()

    /**
     * The user's own API key, or null while the bundled default is in use.
     *
     * Its own flow rather than something read off [credentials], because a key can be stored with
     * no username set: [credentials] is null until an account is configured, and a settings screen
     * reading the key through it could never show one that had been saved on its own.
     */
    private val _apiKeyOverrideFlow = MutableStateFlow<String?>(null)
    val apiKeyOverride: Flow<String?> = _apiKeyOverrideFlow.asStateFlow()

    init {
        // Load credentials off the main thread — EncryptedSharedPreferences + Keystore init is slow.
        bgScope.launch {
            _credentialsFlow.value = loadCredentialsSync()
            _apiKeyOverrideFlow.value = getCustomApiKey()
        }
    }

    private fun loadCredentialsSync(): RaCredentials? {
        return try {
            val username = encryptedPrefs.getString(keyUsername, null) ?: return null
            val apiKey = encryptedPrefs.getString(keyApiKey, null) ?: BUNDLED_API_KEY
            RaCredentials(username = username, apiKey = apiKey).takeIf { it.isValid() }
        } catch (e: Exception) {
            android.util.Log.w("RaCredsRepo", "Failed to load credentials", e)
            null
        }
    }

    /** True when the user has stored their own API key (not using the bundled default). */
    fun hasCustomApiKey(): Boolean {
        return try {
            encryptedPrefs.getString(keyApiKey, null) != null
        } catch (_: Exception) {
            false
        }
    }

    /** Returns the user's stored API key, or null if using the bundled default. */
    fun getCustomApiKey(): String? {
        return try {
            encryptedPrefs.getString(keyApiKey, null)
        } catch (_: Exception) {
            null
        }
    }

    suspend fun getCredentials(): RaCredentials? = mutex.withLock { loadCredentialsSync() }

    suspend fun setCredentials(username: String, apiKey: String?) = mutex.withLock {
        val editor = encryptedPrefs.edit()
            .putString(keyUsername, username.trim())
        if (apiKey != null) {
            editor.putString(keyApiKey, apiKey)
        } else {
            editor.remove(keyApiKey)
        }
        editor.apply()
        _credentialsFlow.value = loadCredentialsSync()
        _apiKeyOverrideFlow.value = getCustomApiKey()
    }

    /**
     * Stores (or, with a blank/null value, clears) the user's own API key, leaving the username
     * alone.
     *
     * Deliberately independent of whether an account is configured. The key row used to save
     * through [setCredentials], which needs a username to write, so a key entered before an account
     * had been set up was silently dropped: the dialog closed, the row still read "Using default",
     * and the only way to make a key stick was to set a username first. The key is a credential of
     * its own - the Journey screen unlocks on it alone - so it persists on its own.
     */
    suspend fun setApiKeyOverride(apiKey: String?) = mutex.withLock {
        val editor = encryptedPrefs.edit()
        if (apiKey.isNullOrBlank()) editor.remove(keyApiKey) else editor.putString(keyApiKey, apiKey)
        editor.apply()
        _credentialsFlow.value = loadCredentialsSync()
        _apiKeyOverrideFlow.value = getCustomApiKey()
    }

    /**
     * Disconnects the account: the username goes, the user's own API key stays.
     *
     * This used to clear the whole preferences file, which was harmless while the key could only be
     * written alongside a username. It is not harmless now that the key stands on its own: this
     * runs on a failed validation as well as on the account row's Clear, so a mistyped username
     * deleted the key the user had just saved, and the Journey screen locked itself again for a
     * reason nobody had chosen. Use [setApiKeyOverride] with null to remove the key itself.
     */
    suspend fun clearCredentials() = mutex.withLock {
        encryptedPrefs.edit().remove(keyUsername).apply()
        _credentialsFlow.value = null
    }
}

/**
 * The production store: EncryptedSharedPreferences over a Keystore master key, recreated from
 * scratch if the file cannot be opened (which happens when the app's signing key changes, e.g.
 * debug over release).
 */
private fun createEncryptedPrefs(context: Context): SharedPreferences {
    val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    return try {
        EncryptedSharedPreferences.create(
            context,
            "ra_credentials",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        android.util.Log.w("RaCredsRepo", "EncryptedSharedPreferences corrupted, resetting", e)
        context.getSharedPreferences("ra_credentials", Context.MODE_PRIVATE).edit().clear().apply()
        try { context.deleteSharedPreferences("ra_credentials") } catch (_: Exception) {}
        EncryptedSharedPreferences.create(
            context,
            "ra_credentials",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
}
