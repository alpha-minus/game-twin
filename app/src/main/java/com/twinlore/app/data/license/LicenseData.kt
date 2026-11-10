package com.twinlore.app.data.license

/**
 * Data classes for license storage and state management
 */

/**
 * Stored license data (all fields needed for local storage)
 */
data class StoredLicenseData(
    val licenseKey: String,
    val instanceId: String,
    val storeId: Long,
    val productId: Long,
    val variantId: Long,
    val customerEmail: String?,
    val licenseStatus: String, // "active", "expired", "disabled"
    val activationTimestamp: Long,
    val lastValidationTimestamp: Long?,
    val licenseExpiresAt: String?, // ISO timestamp or null
    val deviceFingerprint: String?,
    val activationLimit: Int,
    val activationUsage: Int
)

/**
 * License state for UI and business logic
 */
sealed class LicenseState {
    object NotActivated : LicenseState()
    data class Valid(
        val customerEmail: String?,
        val expiresAt: String?,
        val activationUsage: Int,
        val activationLimit: Int
    ) : LicenseState()
    data class Expired(
        val expiresAt: String?,
        val gracePeriodRemainingDays: Int?
    ) : LicenseState()
    data class Error(
        val message: String
    ) : LicenseState()
}

