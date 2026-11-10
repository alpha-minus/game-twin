package com.twinlore.app.data.license

import android.util.Log
import com.twinlore.app.BuildConfig
import com.twinlore.app.data.NetworkConnectivityManager
import com.twinlore.app.domain.Result
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Main coordinator for license verification
 * Coordinates between repository, API client, and validator
 */
@Singleton
class LicenseManager @Inject constructor(
    private val licenseRepository: LicenseRepository,
    private val apiClient: LemonSqueezyApiClient,
    private val deviceFingerprintGenerator: DeviceFingerprintGenerator,
    private val networkConnectivityManager: NetworkConnectivityManager,
    private val settingsRepository: com.twinlore.app.data.SettingsRepository
) : LicenseManagerUiStateContract {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _licenseUiState = MutableStateFlow<LicenseUiState>(LicenseUiState.Unknown)
    override val licenseUiState: StateFlow<LicenseUiState> = _licenseUiState.asStateFlow()

    private val _licenseTier = MutableStateFlow(LicenseTier.NONE)
    override val licenseTier: StateFlow<LicenseTier> = _licenseTier.asStateFlow()

    override fun isLicensedNow(): Boolean =
        _licenseUiState.value is LicenseUiState.Licensed

    init {
        // Run one-time license-V2 migration before subscribing so any
        // orphan grace-period state is cleared before the first emission.
        scope.launch {
            try {
                LicenseMigrationV2(licenseMigrationRepoAdapter()).runIfNeeded()
            } catch (e: Exception) {
                android.util.Log.e(TAG, "License migration V2 failed", e)
            }
        }

        // Keep UI state + cosmetic tier in sync with the persisted license.
        licenseRepository.licenseData
            .onEach { stored ->
                val state = if (stored == null) LicenseState.NotActivated else determineState(stored)
                _licenseUiState.value = state.toUiState()
                _licenseTier.value = resolveLicenseTier(
                    variantId = stored?.variantId,
                    isValid = state is LicenseState.Valid,
                    tier1VariantIds = LicenseConfig.TIER1_VARIANT_IDS,
                    tier2VariantIds = LicenseConfig.TIER2_VARIANT_IDS,
                    tier3VariantIds = LicenseConfig.TIER3_VARIANT_IDS,
                )
            }
            .launchIn(scope)
    }

    private fun licenseMigrationRepoAdapter(): LicenseMigrationV2.Repo =
        object : LicenseMigrationV2.Repo {
            override suspend fun isMigrationDone(): Boolean =
                settingsRepository.licenseMigrationV2Done.first()
            override suspend fun setMigrationDone() {
                settingsRepository.setLicenseMigrationV2Done()
            }
            override suspend fun clearOrphanGracePeriodField() {
                licenseRepository.clearOrphanGracePeriodField()
            }
        }

    companion object {
        private const val TAG = "LicenseManager"
        private const val VALIDATION_INTERVAL_DAYS = 7L
        
        // Key shape lives in LicenseKeyFormat so it can be tested without the network stack.
        private fun isValidLicenseKeyFormat(key: String): Boolean = LicenseKeyFormat.isValid(key)

        private fun normalizeLicenseKey(key: String): String = LicenseKeyFormat.normalize(key)
    }
    
    /**
     * Get current license state as a Flow derived from the persisted
     * repository data. Replaces the old LicenseValidator.getLicenseStateFlow().
     */
    fun getLicenseState(): Flow<LicenseState> {
        return licenseRepository.licenseData.map { storedData ->
            when {
                storedData == null -> LicenseState.NotActivated
                else -> determineState(storedData)
            }
        }
    }

    private fun determineState(data: StoredLicenseData): LicenseState {
        return when (data.licenseStatus) {
            "active", "inactive" -> LicenseState.Valid(
                customerEmail = data.customerEmail,
                expiresAt = null,
                activationUsage = data.activationUsage,
                activationLimit = data.activationLimit
            )
            "expired", "disabled" -> LicenseState.Expired(
                expiresAt = null,
                gracePeriodRemainingDays = null
            )
            else -> LicenseState.Error("Unknown license status: ${data.licenseStatus}")
        }
    }

    /**
     * Check whether a background re-validation should run. Returns true when
     * the license was last validated more than [VALIDATION_INTERVAL_DAYS] days
     * ago.
     */
    suspend fun shouldPerformValidation(): Boolean {
        val storedData = licenseRepository.licenseData.first() ?: return false
        val lastValidation = storedData.lastValidationTimestamp ?: return true
        val daysSinceValidation = java.util.concurrent.TimeUnit.MILLISECONDS.toDays(
            System.currentTimeMillis() - lastValidation
        )
        return daysSinceValidation >= VALIDATION_INTERVAL_DAYS
    }
    
    /**
     * Activate a license key
     * @param licenseKey The license key UUID
     * @param email Optional customer email for verification
     * @return Result with ActivationResponse or error
     */
    suspend fun activateLicense(licenseKey: String, email: String?): Result<ActivationResponse> {
        return try {
            // Note: We don't pre-check network connectivity here because NET_CAPABILITY_VALIDATED
            // can be unreliable (fails even when internet is available during network transitions).
            // Instead, we let the API call proceed and handle network errors naturally.
            // The existing error handling will catch network issues (timeouts, IO errors, etc.)
            // and show appropriate error messages.
            
            // Validate and normalize license key format
            val normalizedKey = normalizeLicenseKey(licenseKey)
            if (!isValidLicenseKeyFormat(normalizedKey)) {
                return Result.failure(LicenseKeyFormat.ERROR_MESSAGE)
            }
            
            // Check if this license key is already activated locally on this device
            val existingData = licenseRepository.licenseData.first()
            if (existingData != null && existingData.licenseKey.equals(normalizedKey, ignoreCase = true)) {
                // License already activated on this device - validate it instead of creating new instance
                Log.d(TAG, "License already activated on this device (instance_id=${existingData.instanceId}), validating existing activation")
                val validationResult = try {
                    apiClient.api.validateLicense(
                        licenseKey = existingData.licenseKey,
                        instanceId = existingData.instanceId
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Error validating existing activation, proceeding with new activation", e)
                    null
                }
                
                if (validationResult != null && validationResult.valid && validationResult.licenseKey != null && validationResult.instance != null) {
                    // Update stored data with fresh validation
                    licenseRepository.updateLicenseData(validationResult)
                    
                    // Convert ValidationResponse to ActivationResponse for consistency
                    val activationResponse = ActivationResponse(
                        activated = true,
                        error = null,
                        licenseKey = validationResult.licenseKey,
                        instance = validationResult.instance,
                        meta = validationResult.meta
                    )
                    
                    Log.d(TAG, "Existing license activation is valid, returning existing instance. Activation usage: ${validationResult.licenseKey.activationUsage}/${validationResult.licenseKey.activationLimit}")
                    return Result.success(activationResponse)
                } else {
                    // Existing activation is invalid, proceed with new activation
                    Log.d(TAG, "Existing activation is invalid (valid=${validationResult?.valid}, error=${validationResult?.error}), proceeding with new activation")
                }
            }
            
            // IMPORTANT: Lemon Squeezy API Limitation
            // The API does NOT support querying instances by device fingerprint or instance_name.
            // When validating without instance_id, the instance field is null (only license-level data is returned).
            // Every activation call creates a new instance, even with the same instance_name.
            // 
            // Check activation limit before attempting activation to provide better user feedback
            Log.d(TAG, "No local license data found. Checking activation limit before proceeding...")
            
            // Validate license key first to check activation limit
            val limitCheck = try {
                apiClient.api.validateLicense(
                    licenseKey = normalizedKey,
                    instanceId = null
                )
            } catch (e: Exception) {
                Log.d(TAG, "Could not check activation limit (network error), proceeding with activation", e)
                null
            }
            
            // Check if activation limit is already reached
            if (limitCheck != null && limitCheck.valid && limitCheck.licenseKey != null) {
                val usage = limitCheck.licenseKey.activationUsage
                val limit = limitCheck.licenseKey.activationLimit
                
                if (usage >= limit && limit > 0) {
                    Log.w(TAG, "Activation limit already reached: $usage/$limit")
                    return Result.failure(
                        "Activation limit reached ($usage/$limit devices). " +
                        "You can deactivate devices from your Lemon Squeezy account dashboard. " +
                        "Go to your account → Licenses → select your license → deactivate unused devices. " +
                        "After deactivating, try activating again."
                    )
                }
            }
            
            Log.d(TAG, "Activation limit check passed. Proceeding with activation...")
            
            // No existing instance found (or validation failed) - proceed with new activation
            // Include device fingerprint in instance_name for server-side deduplication
            // This helps detect same device reactivations and prevent unnecessary instance creation
            val deviceName = deviceFingerprintGenerator.generateDeviceName()
            val deviceFingerprint = deviceFingerprintGenerator.generateFingerprint()
            // Include fingerprint in instance_name so server can deduplicate same device
            // Format: "Android Device (Model) [FINGERPRINT]"
            val instanceNameWithFingerprint = "$deviceName [$deviceFingerprint]"
            
            Log.d(TAG, "No existing instance found. Calling activation API.")
            Log.d(TAG, "API endpoint: https://api.lemonsqueezy.com/v1/licenses/activate")
            val networkAvailable = networkConnectivityManager.isNetworkAvailable()
            Log.d(TAG, "Network connectivity check: $networkAvailable")
            if (!networkAvailable) {
                Log.w(TAG, "WARNING: Network connectivity check returned false, but proceeding anyway (may be unreliable)")
            }
            
            // Try DNS resolution test on IO thread
            val dnsResolutionSuccess = try {
                withContext(Dispatchers.IO) {
                    val hostAddress = java.net.InetAddress.getByName("api.lemonsqueezy.com")
                    Log.d(TAG, "DNS resolution test: api.lemonsqueezy.com resolves to ${hostAddress.hostAddress}")
                    true
                }
            } catch (e: Exception) {
                Log.e(TAG, "DNS resolution test FAILED: Cannot resolve api.lemonsqueezy.com", e)
                Log.e(TAG, "Error type: ${e.javaClass.name}, Message: ${e.message}")
                Log.e(TAG, "This indicates a network/DNS issue, not an app configuration problem")
                false
            }
            
            if (!dnsResolutionSuccess) {
                return Result.failure(
                    "Cannot resolve server address (DNS error).\n\n" +
                    "Troubleshooting steps:\n" +
                    "1. Check internet connection\n" +
                    "2. Try switching networks (WiFi ↔ Mobile data)\n" +
                    "3. Check DNS settings:\n" +
                    "   Settings → Network → Private DNS\n" +
                    "   Try: Automatic or 8.8.8.8\n" +
                    "4. Restart device\n" +
                    "5. Check firewall/proxy settings"
                )
            }
            
            // Call activation API
            val response = try {
                Log.d(TAG, "Making activation API call...")
                val result = apiClient.api.activateLicense(
                    licenseKey = normalizedKey,
                    instanceName = instanceNameWithFingerprint
                )
                Log.d(TAG, "API call completed successfully. Response activated: ${result.activated}")
                result
            } catch (e: java.net.SocketTimeoutException) {
                Log.e(TAG, "Connection timeout during activation", e)
                Log.e(TAG, "Timeout details: ${e.message}")
                return Result.failure("Connection timeout. Please check your internet connection and try again.")
            } catch (e: java.net.UnknownHostException) {
                Log.e(TAG, "Cannot resolve host 'api.lemonsqueezy.com'", e)
                Log.e(TAG, "Host resolution failed: ${e.message}")
                Log.e(TAG, "This is a DNS/network issue, not an app configuration problem")
                Log.e(TAG, "Troubleshooting steps:")
                Log.e(TAG, "1. Check device has internet connection")
                Log.e(TAG, "2. Try opening https://api.lemonsqueezy.com in browser")
                Log.e(TAG, "3. Check DNS settings (Settings > Network > DNS)")
                Log.e(TAG, "4. Try switching WiFi networks or using mobile data")
                return Result.failure("Cannot resolve server address (DNS error).\n\nPlease check:\n• Internet connection is active\n• DNS settings are correct\n• Try switching networks (WiFi ↔ Mobile data)\n• Firewall/proxy is not blocking DNS")
            } catch (e: javax.net.ssl.SSLException) {
                Log.e(TAG, "SSL error during activation", e)
                Log.e(TAG, "SSL error details: ${e.message}")
                return Result.failure("SSL connection error. Please check your internet connection and try again.")
            } catch (e: java.io.IOException) {
                Log.e(TAG, "IO error during activation", e)
                Log.e(TAG, "IO error details: ${e.message}")
                Log.e(TAG, "Error class: ${e.javaClass.name}")
                return Result.failure("Network error: ${e.message ?: "Please check your internet connection and try again."}")
            } catch (e: retrofit2.HttpException) {
                // A rejected key arrives here, not in the success path below: Lemon Squeezy
                // answers an unknown key with 404 and a JSON body saying so. The body is the
                // whole point — without parsing it every bad key looks like our server is down.
                // errorBody() is a one-shot stream, so read it exactly once.
                val body = try { e.response()?.errorBody()?.string() } catch (_: Exception) { null }
                val apiError = LicenseActivationError.parseApiError(body)
                Log.e(TAG, "HTTP error during activation: status=${e.code()} apiError=$apiError")
                // The body can echo the submitted key, so it stays out of a shipping log.
                if (BuildConfig.DEBUG) Log.e(TAG, "HTTP error body: $body")
                return Result.failure(
                    apiError?.let { LicenseActivationError.friendly(it) }
                        ?: LicenseActivationError.serverError(e.code())
                )
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error during activation", e)
                Log.e(TAG, "Error class: ${e.javaClass.name}")
                Log.e(TAG, "Error message: ${e.message}")
                Log.e(TAG, "Stack trace:", e)
                return Result.failure("Activation failed: ${e.message ?: "Unknown error"}")
            }
            
            // Validate response
            // A 2xx that still reports failure. Same wording as the HttpException path above,
            // with the activation counts filled in since this response carries them.
            if (!response.activated || response.error != null) {
                return Result.failure(
                    LicenseActivationError.friendly(
                        apiError = response.error ?: "Activation failed",
                        usage = response.licenseKey.activationUsage,
                        limit = response.licenseKey.activationLimit,
                    )
                )
            }
            
            // Validate license key status
            if (response.licenseKey.status !in listOf("active", "inactive")) {
                return Result.failure("License key status is ${response.licenseKey.status}")
            }
            
            // Validate store_id and product_id (critical security check)
            val meta = response.meta
            if (meta == null) {
                return Result.failure("Missing metadata in activation response")
            }
            
            // A key from another store (including the retired TwinLore one) is not ours.
            if (meta.storeId != LicenseConfig.STORE_ID) {
                Log.e(TAG, "Store ID mismatch: expected ${LicenseConfig.STORE_ID}, got ${meta.storeId}")
                return Result.failure("Invalid store ID. License key is not for this store.")
            }

            // Two products are ours: the test-mode era one (beta keys) and the live one.
            if (meta.productId !in LicenseConfig.ACCEPTED_PRODUCT_IDS) {
                Log.e(TAG, "Product ID mismatch: expected one of ${LicenseConfig.ACCEPTED_PRODUCT_IDS}, got ${meta.productId}")
                return Result.failure("Invalid product ID. License key is not for this product.")
            }
            
            // Verify email if provided
            if (email != null && meta.customerEmail.lowercase() != email.lowercase()) {
                return Result.failure("Email does not match purchase email")
            }
            
            // Validate instance ID is present
            val instance = response.instance
            if (instance == null) {
                return Result.failure("Activation failed: no instance ID returned")
            }
            
            // Save license data - ensure it completes successfully
            // Note: deviceFingerprint already generated above for instance_name
            val saveSuccess = licenseRepository.saveLicenseData(response, deviceFingerprint)
            
            if (!saveSuccess) {
                Log.e(TAG, "Failed to save license data to storage")
                return Result.failure("License activated but failed to save locally. Please try again or contact support.")
            }
            
            Log.d(TAG, "License activated successfully: instance_id=${instance.id}")
            Result.success(response)
        } catch (e: java.net.SocketTimeoutException) {
            Log.e(TAG, "Connection timeout during license activation", e)
            Result.failure("Connection timeout. Please check your internet connection and try again.")
        } catch (e: java.net.UnknownHostException) {
            Log.e(TAG, "Cannot reach license server", e)
            Result.failure("Cannot reach license server. Please check your internet connection.")
        } catch (e: java.io.IOException) {
            Log.e(TAG, "Network error during license activation", e)
            Result.failure("Network error: ${e.message ?: "Please check your internet connection and try again."}")
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error activating license", e)
            Result.failure("Activation failed: ${e.message ?: "Unknown error. Please try again."}")
        }
    }
    
    /**
     * Validate license with Lemon Squeezy API
     * @return Result with ValidationResponse or error
     */
    suspend fun validateLicense(): Result<ValidationResponse> {
        return try {
            val storedData = licenseRepository.licenseData.first()
                ?: return Result.failure("No license data found")
            
            Log.d(TAG, "Calling validation API for stored instance.")
            
            // Call validation API with instance_id
            val response = try {
                apiClient.api.validateLicense(
                    licenseKey = storedData.licenseKey,
                    instanceId = storedData.instanceId
                )
            } catch (e: retrofit2.HttpException) {
                // Handle HTTP errors
                // According to Lemon Squeezy API docs:
                // 400 = An error occurred (license key disabled/invalid, activation limit exceeded, etc.)
                // 404 = An item could not be found (instance/license not found - deactivated or disabled)
                // 422 = A required field was invalid or missing (could indicate invalid license key)
                val errorCode = e.code()
                val errorBody = e.response()?.errorBody()?.string()
                Log.e(TAG, "HTTP error during validation: $errorCode")
                // Same reason the activation path gates this: the body can echo the licence key
                // and the buyer's details, and Log calls are not stripped by R8.
                if (BuildConfig.DEBUG) Log.e(TAG, "HTTP error body: $errorBody", e)
                
                // All 4XX errors (400, 404, 422) could indicate license is disabled/invalid
                // Treat them as license disabled to block the app
                if (errorCode in listOf(400, 404, 422)) {
                    val errorMsg = when (errorCode) {
                        404 -> "instance or license not found (deactivated or disabled)"
                        400 -> "bad request - license key may be disabled or invalid"
                        422 -> "invalid or missing field - license key may be invalid"
                        else -> "error occurred"
                    }
                    Log.w(TAG, "Validation returned $errorCode - $errorMsg. Treating as disabled.")
                    // Update license status to disabled
                    licenseRepository.setLicenseStatusDisabled()
                    // Verify the status was updated
                    val updatedData = licenseRepository.licenseData.first()
                    Log.d(TAG, "License status after $errorCode update: ${updatedData?.licenseStatus}")
                    return Result.failure("License validation failed ($errorCode) - license may have been disabled")
                } else {
                    // Other HTTP errors (5XX, etc.) - don't treat as disabled, might be server issue
                    Log.e(TAG, "Unexpected HTTP error: $errorCode")
                    return Result.failure("HTTP error $errorCode: ${e.message()}")
                }
            }
            
            Log.d(TAG, "Validation API response received: valid=${response.valid}, error=${response.error}, licenseKey=${response.licenseKey != null}, status=${response.licenseKey?.status}, instance=${response.instance != null}")
            
            // Check if instance was deactivated (instance is null when instance_id was provided)
            if (response.instance == null && storedData.instanceId.isNotEmpty()) {
                Log.w(TAG, "Instance is null - instance was deactivated. This might indicate license was disabled or instance removed.")
            }
            
            // Update stored data based on response
            if (response.valid && response.error == null && response.instance != null) {
                // Valid license with valid instance - update data and clear grace period
                Log.d(TAG, "License is valid - updating data")
                licenseRepository.updateLicenseData(response)
                Result.success(response)
            } else {
                // Invalid license or instance deactivated - update status immediately (no grace period)
                Log.d(TAG, "License is invalid or instance deactivated - updating status. valid=${response.valid}, error=${response.error}, status=${response.licenseKey?.status}, instance=${response.instance != null}")
                
                val errorMessage = response.error
                if (errorMessage != null) {
                    Log.w(TAG, "Validation response contains error: $errorMessage")
                    if (errorMessage.contains("disabled", ignoreCase = true) ||
                        errorMessage.contains("not found", ignoreCase = true) ||
                        errorMessage.contains("expired", ignoreCase = true)) {
                        Log.w(TAG, "Error message indicates license is disabled/invalid - setting status to disabled")
                        licenseRepository.setLicenseStatusDisabled()
                    }
                }
                
                if (response.licenseKey != null) {
                    licenseRepository.updateLicenseData(response)
                    if (response.licenseKey.status == "disabled") {
                        Log.d(TAG, "License disabled - app will be blocked immediately")
                    } else if (response.instance == null && storedData.instanceId.isNotEmpty()) {
                        Log.d(TAG, "Instance deactivated - instance was removed. License status: ${response.licenseKey.status}")
                        // Instance was deactivated on this device - treat as disabled
                        // regardless of overall license status (license may still be active on other devices)
                        Log.w(TAG, "Instance null with stored instanceId - treating as disabled for this device")
                        licenseRepository.setLicenseStatusDisabled()
                    }
                } else {
                    Log.w(TAG, "Validation response has no licenseKey field - cannot update status")
                    if (errorMessage != null) {
                        Log.w(TAG, "No licenseKey but error present - setting status to disabled")
                        licenseRepository.setLicenseStatusDisabled()
                    }
                }
                
                val failureReason = errorMessage
                    ?: if (response.instance == null) "License instance no longer valid"
                    else "License validation failed"
                Result.failure(failureReason)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error validating license", e)
            Result.failure(e)
        }
    }
    
    /**
     * Trigger background validation if conditions are met
     * Non-blocking, runs asynchronously
     * Never throws - always returns false on error to avoid blocking app
     */
    suspend fun startBackgroundValidation(): Boolean {
        return try {
            val storedData = licenseRepository.licenseData.first()
            if (storedData == null) {
                return false // No license to validate
            }

            if (!shouldPerformValidation()) {
                return false // Too soon to validate again
            }
            
            // Note: We don't pre-check network connectivity here because NET_CAPABILITY_VALIDATED
            // can be unreliable. Instead, we let the API call proceed - it will fail gracefully
            // with timeout/IO errors if there's no network, and we catch those errors.
            
            // Perform validation - catch all errors to prevent blocking
            val result = try {
                validateLicense()
            } catch (e: Exception) {
                Log.e(TAG, "Background validation failed (non-blocking)", e)
                return false // Return false, don't throw
            }
            
            result.isSuccess()
        } catch (e: Exception) {
            Log.e(TAG, "Error in background validation (non-blocking)", e)
            false // Never throw - background validation should never block app
        }
    }
    
    /**
     * Deactivate license instance (for freeing up activation slots)
     * This removes this device from the license's activation count
     */
    suspend fun deactivateInstance(): Result<DeactivationResponse> {
        return try {
            val storedData = licenseRepository.licenseData.first()
                ?: return Result.failure("No license data found")
            
            // Note: We don't pre-check network connectivity here because NET_CAPABILITY_VALIDATED
            // can be unreliable (fails even when internet is available during network transitions).
            // Instead, we let the API call proceed and handle network errors naturally.
            // The existing error handling will catch network issues (timeouts, IO errors, etc.)
            // and show appropriate error messages.
            
            Log.d(TAG, "Deactivating license instance: instance_id=${storedData.instanceId}, current usage: ${storedData.activationUsage}/${storedData.activationLimit}")
            
            // Call deactivation API
            val response = try {
                apiClient.api.deactivateInstance(
                    licenseKey = storedData.licenseKey,
                    instanceId = storedData.instanceId
                )
            } catch (e: java.net.SocketTimeoutException) {
                return Result.failure("Connection timeout. Please check your internet connection and try again.")
            } catch (e: java.net.UnknownHostException) {
                return Result.failure("Cannot reach license server. Please check your internet connection.")
            } catch (e: java.io.IOException) {
                return Result.failure("Network error: ${e.message ?: "Please check your internet connection and try again."}")
            } catch (e: retrofit2.HttpException) {
                Log.e(TAG, "HTTP error during deactivation: status=${e.code()}")
                val errorBody = e.response()?.errorBody()?.string()
                if (BuildConfig.DEBUG) Log.e(TAG, "HTTP error body: $errorBody", e)
                
                // Provide user-friendly error messages based on status code
                val errorMsg = when (e.code()) {
                    404 -> "License instance not found. It may have already been deactivated."
                    400 -> "Invalid deactivation request. Please try again or contact support."
                    else -> "Server error (${e.code()}). Please try again later."
                }
                return Result.failure(errorMsg)
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error during deactivation", e)
                return Result.failure("Deactivation failed: ${e.message ?: "Unknown error"}")
            }
            
            // Check if deactivation was successful
            if (response.deactivated && response.error == null) {
                Log.d(TAG, "License instance deactivated successfully")
                
                // Log updated activation usage if available in response
                response.licenseKey?.let { licenseKey ->
                    Log.d(TAG, "Updated activation usage: ${licenseKey.activationUsage}/${licenseKey.activationLimit}")
                }
                
                // Clear local license data since this device is no longer activated
                // Even if clear fails, we consider deactivation successful (server-side is done)
                try {
                    licenseRepository.clearLicenseData()
                    Log.d(TAG, "Local license data cleared. Activation slot freed.")
                } catch (e: Exception) {
                    Log.e(TAG, "Error clearing local license data after deactivation", e)
                    // Don't fail the whole operation - server-side deactivation succeeded
                }
            } else {
                Log.e(TAG, "Deactivation failed: ${response.error}")
                return Result.failure(response.error ?: "Deactivation failed")
            }
            
            Result.success(response)
        } catch (e: java.net.SocketTimeoutException) {
            Log.e(TAG, "Connection timeout during license deactivation", e)
            Result.failure("Connection timeout. Please check your internet connection and try again.")
        } catch (e: java.net.UnknownHostException) {
            Log.e(TAG, "Cannot reach license server during deactivation", e)
            Result.failure("Cannot reach license server. Please check your internet connection.")
        } catch (e: java.io.IOException) {
            Log.e(TAG, "Network error during license deactivation", e)
            Result.failure("Network error: ${e.message ?: "Please check your internet connection and try again."}")
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error deactivating instance", e)
            Result.failure("Deactivation failed: ${e.message ?: "Unknown error. Please try again."}")
        }
    }
}
