package com.twinlore.app.data.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A secret the store holds. The storage key doubles as the name the value had in the plaintext
 * DataStore it migrated from, so the owning repository can match old entries to their new home.
 */
enum class AppSecret(internal val storageKey: String) {
    GeminiApiKey("gemini_api_key"),
    SteamApiKey("steam_api_key"),
    SteamGridApiKey("steamgrid_api_key"),
    TheGamesDbApiKey("thegamesdb_api_key"),
    LicenseKey("license_key"),
    CustomerEmail("customer_email"),
}

/**
 * Stores user-entered API keys and the supporter license in EncryptedSharedPreferences, the same
 * store the RetroAchievements and Steam credentials already live in. Do not log secrets.
 *
 * Everything is lazy: the encrypted file (and with it the Keystore) is only opened on the first
 * read or write, so code paths that never touch a secret never pay for it.
 */
@Singleton
class AppSecretsStore internal constructor(
    private val context: Context,
    /**
     * Where the stored secrets live. Production hands over the encrypted store below; a test
     * hands over a plain SharedPreferences, since the Keystore this class would otherwise reach
     * for does not exist off-device.
     */
    private val prefsFactory: (Context) -> SharedPreferences,
) {
    @Inject constructor(@ApplicationContext context: Context) : this(context, ::createEncryptedSecretsPrefs)

    private val mutex = Mutex()
    private val bgScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val encryptedPrefs by lazy { prefsFactory(context) }

    private val states = AppSecret.entries.associateWith { MutableStateFlow<String?>(null) }
    private val loaded = CompletableDeferred<Unit>()
    private val loadStarted = AtomicBoolean(false)

    private suspend fun awaitLoaded() {
        if (loadStarted.compareAndSet(false, true)) {
            bgScope.launch {
                try {
                    for ((secret, state) in states) {
                        state.value = runCatching {
                            encryptedPrefs.getString(secret.storageKey, null)
                        }.getOrNull()
                    }
                } finally {
                    loaded.complete(Unit)
                }
            }
        }
        loaded.await()
    }

    /** The stored value, or null while the secret is not set. Emits once the store has loaded. */
    fun flow(secret: AppSecret): Flow<String?> = flow {
        awaitLoaded()
        emitAll(states.getValue(secret))
    }

    suspend fun get(secret: AppSecret): String? {
        awaitLoaded()
        return states.getValue(secret).value
    }

    /** Stores the value; null or blank removes the secret. */
    suspend fun set(secret: AppSecret, value: String?) {
        awaitLoaded()
        val stored = value?.takeUnless { it.isBlank() }
        mutex.withLock {
            withContext(Dispatchers.IO) {
                val editor = encryptedPrefs.edit()
                if (stored == null) editor.remove(secret.storageKey)
                else editor.putString(secret.storageKey, stored)
                editor.apply()
            }
            states.getValue(secret).value = stored
        }
    }
}

/**
 * The production store: EncryptedSharedPreferences over a Keystore master key, recreated from
 * scratch if the file cannot be opened (which happens when the app's signing key changes, e.g.
 * debug over release).
 */
private fun createEncryptedSecretsPrefs(context: Context): SharedPreferences {
    val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    return try {
        EncryptedSharedPreferences.create(
            context,
            "app_secrets",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        android.util.Log.w("AppSecretsStore", "EncryptedSharedPreferences corrupted, resetting", e)
        context.getSharedPreferences("app_secrets", Context.MODE_PRIVATE).edit().clear().apply()
        try { context.deleteSharedPreferences("app_secrets") } catch (_: Exception) {}
        EncryptedSharedPreferences.create(
            context,
            "app_secrets",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
}
