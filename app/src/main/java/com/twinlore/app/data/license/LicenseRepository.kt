package com.twinlore.app.data.license

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.twinlore.app.data.security.AppSecret
import com.twinlore.app.data.security.AppSecretsStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for storing and retrieving license data using DataStore Preferences. The supporter
 * key and the customer email live in the encrypted [AppSecretsStore] instead; the DataStore keeps
 * only the non-secret activation bookkeeping.
 */
@Singleton
class LicenseRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val secrets: AppSecretsStore = AppSecretsStore(context),
) {
    companion object {
        @VisibleForTesting
        internal val Context.licenseDataStore: DataStore<Preferences> by preferencesDataStore(name = "license")
        
        // Storage keys
        private val LICENSE_KEY = stringPreferencesKey("license_key")
        private val INSTANCE_ID_KEY = stringPreferencesKey("instance_id")
        private val STORE_ID_KEY = longPreferencesKey("store_id")
        private val PRODUCT_ID_KEY = longPreferencesKey("product_id")
        private val VARIANT_ID_KEY = longPreferencesKey("variant_id")
        private val CUSTOMER_EMAIL_KEY = stringPreferencesKey("customer_email")
        private val LICENSE_STATUS_KEY = stringPreferencesKey("license_status")
        private val ACTIVATION_TIMESTAMP_KEY = longPreferencesKey("activation_timestamp")
        private val LAST_VALIDATION_TIMESTAMP_KEY = longPreferencesKey("last_validation_timestamp")
        private val LICENSE_EXPIRES_AT_KEY = stringPreferencesKey("license_expires_at")
        private val DEVICE_FINGERPRINT_KEY = stringPreferencesKey("device_fingerprint")
        private val ACTIVATION_LIMIT_KEY = longPreferencesKey("activation_limit")
        private val ACTIVATION_USAGE_KEY = longPreferencesKey("activation_usage")
    }
    
    private val dataStore = context.licenseDataStore

    /**
     * The key and email lived in this DataStore as plaintext until 1.0.x. Every path that touches
     * them moves any remaining plaintext copies into [AppSecretsStore] first, so an upgraded
     * install keeps its activation and loses the unencrypted copies on first use.
     */
    private val secretsMigrationMutex = Mutex()
    @Volatile
    private var secretsMigrated = false

    private suspend fun migrateSecretsIfNeeded() {
        if (secretsMigrated) return
        secretsMigrationMutex.withLock {
            if (secretsMigrated) return
            val prefs = dataStore.data.first()
            val moves = listOf(
                LICENSE_KEY to AppSecret.LicenseKey,
                CUSTOMER_EMAIL_KEY to AppSecret.CustomerEmail,
            ).filter { (plaintextKey, _) -> prefs.contains(plaintextKey) }
            for ((plaintextKey, secret) in moves) {
                secrets.set(secret, prefs[plaintextKey])
            }
            if (moves.isNotEmpty()) {
                dataStore.edit { editable ->
                    moves.forEach { (plaintextKey, _) -> editable.remove(plaintextKey) }
                }
            }
            secretsMigrated = true
        }
    }

    /**
     * Get stored license data
     * Returns null if data is missing or corrupted (allows re-activation)
     */
    val licenseData: Flow<StoredLicenseData?> = flow {
        migrateSecretsIfNeeded()
        emitAll(
            combine(
                dataStore.data,
                secrets.flow(AppSecret.LicenseKey),
                secrets.flow(AppSecret.CustomerEmail),
            ) { preferences, licenseKey, customerEmail ->
                buildStoredLicenseData(preferences, licenseKey, customerEmail)
            }
        )
    }

    private fun buildStoredLicenseData(
        preferences: Preferences,
        licenseKey: String?,
        customerEmail: String?,
    ): StoredLicenseData? {
        return try {
            val instanceId = preferences[INSTANCE_ID_KEY]

            if (licenseKey == null || instanceId == null || licenseKey.isBlank() || instanceId.isBlank()) {
                null
            } else {
                StoredLicenseData(
                    licenseKey = licenseKey,
                    instanceId = instanceId,
                    storeId = preferences[STORE_ID_KEY] ?: 0L,
                    productId = preferences[PRODUCT_ID_KEY] ?: 0L,
                    variantId = preferences[VARIANT_ID_KEY] ?: 0L,
                    customerEmail = customerEmail,
                    licenseStatus = preferences[LICENSE_STATUS_KEY] ?: "active",
                    activationTimestamp = preferences[ACTIVATION_TIMESTAMP_KEY] ?: 0L,
                    lastValidationTimestamp = preferences[LAST_VALIDATION_TIMESTAMP_KEY],
                    licenseExpiresAt = preferences[LICENSE_EXPIRES_AT_KEY],
                    deviceFingerprint = preferences[DEVICE_FINGERPRINT_KEY],
                    activationLimit = preferences[ACTIVATION_LIMIT_KEY]?.toInt() ?: 0,
                    activationUsage = preferences[ACTIVATION_USAGE_KEY]?.toInt() ?: 0
                )
            }
        } catch (e: Exception) {
            // Data corruption detected - return null to allow re-activation
            android.util.Log.e("LicenseRepository", "Error reading license data (corrupted?), allowing re-activation", e)
            null
        }
    }
    
    /**
     * Save license data from activation response
     * @return true if save was successful, false otherwise
     */
    suspend fun saveLicenseData(response: ActivationResponse, deviceFingerprint: String): Boolean {
        return try {
            migrateSecretsIfNeeded()
            secrets.set(AppSecret.LicenseKey, response.licenseKey.key)
            response.meta?.customerEmail?.let { secrets.set(AppSecret.CustomerEmail, it) }
            dataStore.edit { preferences ->
                response.instance?.id?.let { preferences[INSTANCE_ID_KEY] = it }
                response.meta?.storeId?.let { preferences[STORE_ID_KEY] = it }
                response.meta?.productId?.let { preferences[PRODUCT_ID_KEY] = it }
                response.meta?.variantId?.let { preferences[VARIANT_ID_KEY] = it }
                preferences[LICENSE_STATUS_KEY] = response.licenseKey.status
                response.instance?.createdAt?.let { 
                    // Parse timestamp from ISO string, or use current time
                    preferences[ACTIVATION_TIMESTAMP_KEY] = System.currentTimeMillis()
                } ?: run {
                    preferences[ACTIVATION_TIMESTAMP_KEY] = System.currentTimeMillis()
                }
                preferences[LAST_VALIDATION_TIMESTAMP_KEY] = System.currentTimeMillis()
                response.licenseKey.expiresAt?.let { preferences[LICENSE_EXPIRES_AT_KEY] = it }
                preferences[DEVICE_FINGERPRINT_KEY] = deviceFingerprint
                preferences[ACTIVATION_LIMIT_KEY] = response.licenseKey.activationLimit.toLong()
                preferences[ACTIVATION_USAGE_KEY] = response.licenseKey.activationUsage.toLong()
            }
            true
        } catch (e: Exception) {
            android.util.Log.e("LicenseRepository", "Error saving license data", e)
            false
        }
    }
    
    /**
     * Update license data from validation response
     */
    suspend fun updateLicenseData(response: ValidationResponse) {
        dataStore.edit { preferences ->
            response.licenseKey?.let { licenseKey ->
                android.util.Log.d("LicenseRepository", "Updating license status to: ${licenseKey.status}")
                preferences[LICENSE_STATUS_KEY] = licenseKey.status
                licenseKey.expiresAt?.let { preferences[LICENSE_EXPIRES_AT_KEY] = it }
                preferences[ACTIVATION_LIMIT_KEY] = licenseKey.activationLimit.toLong()
                preferences[ACTIVATION_USAGE_KEY] = licenseKey.activationUsage.toLong()
            } ?: run {
                android.util.Log.w("LicenseRepository", "Validation response has no licenseKey - status not updated")
            }
            response.meta?.variantId?.let { preferences[VARIANT_ID_KEY] = it }
            preferences[LAST_VALIDATION_TIMESTAMP_KEY] = System.currentTimeMillis()
        }
    }
    
    /**
     * Clear all license data
     */
    suspend fun clearLicenseData() {
        migrateSecretsIfNeeded()
        secrets.set(AppSecret.LicenseKey, null)
        secrets.set(AppSecret.CustomerEmail, null)
        dataStore.edit { preferences ->
            preferences.remove(LICENSE_KEY)
            preferences.remove(INSTANCE_ID_KEY)
            preferences.remove(STORE_ID_KEY)
            preferences.remove(PRODUCT_ID_KEY)
            preferences.remove(VARIANT_ID_KEY)
            preferences.remove(CUSTOMER_EMAIL_KEY)
            preferences.remove(LICENSE_STATUS_KEY)
            preferences.remove(ACTIVATION_TIMESTAMP_KEY)
            preferences.remove(LAST_VALIDATION_TIMESTAMP_KEY)
            preferences.remove(LICENSE_EXPIRES_AT_KEY)
            preferences.remove(DEVICE_FINGERPRINT_KEY)
            preferences.remove(ACTIVATION_LIMIT_KEY)
            preferences.remove(ACTIVATION_USAGE_KEY)
        }
    }
    
    /**
     * Remove any orphan grace_period_start key left over from the
     * pre-0.7.x license model. Called once by [LicenseMigrationV2] at
     * first launch of the new version. Idempotent: safe to call when
     * the key has already been removed.
     */
    suspend fun clearOrphanGracePeriodField() {
        dataStore.edit { preferences ->
            preferences.remove(longPreferencesKey("grace_period_start"))
        }
    }

    /**
     * Set license status to disabled (used when API returns 404 or instance not found)
     */
    suspend fun setLicenseStatusDisabled() {
        dataStore.edit { preferences ->
            preferences[LICENSE_STATUS_KEY] = "disabled"
            preferences[LAST_VALIDATION_TIMESTAMP_KEY] = System.currentTimeMillis()
            android.util.Log.d("LicenseRepository", "License status set to disabled (404/not found)")
        }
    }
}
