package com.twinlore.app.data.steam

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
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
 * Stores Steam Web API key, vanity URL, and resolved Steam ID in EncryptedSharedPreferences.
 * Do not log credentials.
 */
@Singleton
class SteamCredentialsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val mutex = Mutex()
    private val bgScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Defer MasterKey + EncryptedSharedPreferences init to first background access.
    private val encryptedPrefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        try {
            EncryptedSharedPreferences.create(
                context,
                "steam_credentials",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            android.util.Log.w("SteamCredsRepo", "EncryptedSharedPreferences corrupted, resetting", e)
            context.getSharedPreferences("steam_credentials", Context.MODE_PRIVATE).edit().clear().apply()
            try { context.deleteSharedPreferences("steam_credentials") } catch (_: Exception) {}
            EncryptedSharedPreferences.create(
                context,
                "steam_credentials",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }
    }

    private val keyVanityUrl = "steam_vanity_url"
    private val keySteamId = "steam_id"

    private val _credentialsFlow = MutableStateFlow<SteamCredentials?>(null)
    val credentials: Flow<SteamCredentials?> = _credentialsFlow.asStateFlow()

    init {
        // Load credentials off the main thread — EncryptedSharedPreferences + Keystore init is slow.
        bgScope.launch {
            _credentialsFlow.value = loadCredentialsSync()
        }
    }

    private fun loadCredentialsSync(): SteamCredentials? {
        return try {
            val vanityUrl = encryptedPrefs.getString(keyVanityUrl, null) ?: ""
            val steamId = encryptedPrefs.getString(keySteamId, null) ?: return null
            SteamCredentials(vanityUrl = vanityUrl, steamId = steamId)
                .takeIf { it.isValid() }
        } catch (e: Exception) {
            android.util.Log.w("SteamCredsRepo", "Failed to load credentials", e)
            null
        }
    }

    suspend fun getCredentials(): SteamCredentials? = mutex.withLock { loadCredentialsSync() }

    suspend fun setCredentials(vanityUrl: String, steamId: String) = mutex.withLock {
        encryptedPrefs.edit()
            .putString(keyVanityUrl, vanityUrl.trim())
            .putString(keySteamId, steamId.trim())
            .apply()
        _credentialsFlow.value = loadCredentialsSync()
    }

    suspend fun clearCredentials() = mutex.withLock {
        encryptedPrefs.edit().clear().apply()
        _credentialsFlow.value = null
    }
}
