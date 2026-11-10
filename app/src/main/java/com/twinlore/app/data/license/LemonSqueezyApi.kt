package com.twinlore.app.data.license

import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

/**
 * Retrofit interface for Lemon Squeezy License API
 * Documentation: https://docs.lemonsqueezy.com/guides/tutorials/license-keys
 * Base URL: https://api.lemonsqueezy.com/v1/licenses/
 */
interface LemonSqueezyApi {
    /**
     * Activate a license key
     * POST /activate
     * 
     * @param licenseKey The license key UUID provided by the customer
     * @param instanceName Human-readable name for this device/activation
     * @return ActivationResponse with license key, instance, and metadata
     */
    @FormUrlEncoded
    @POST("activate")
    suspend fun activateLicense(
        @Field("license_key") licenseKey: String,
        @Field("instance_name") instanceName: String
    ): ActivationResponse
    
    /**
     * Validate a license key or instance
     * POST /validate
     * 
     * @param licenseKey The license key UUID
     * @param instanceId Optional instance ID - if provided, validates specific instance
     * @return ValidationResponse with license key status and instance info
     */
    @FormUrlEncoded
    @POST("validate")
    suspend fun validateLicense(
        @Field("license_key") licenseKey: String,
        @Field("instance_id") instanceId: String? = null
    ): ValidationResponse
    
    /**
     * Deactivate a license key instance
     * POST /deactivate
     * 
     * @param licenseKey The license key UUID
     * @param instanceId The instance ID to deactivate
     * @return DeactivationResponse confirming deactivation
     */
    @FormUrlEncoded
    @POST("deactivate")
    suspend fun deactivateInstance(
        @Field("license_key") licenseKey: String,
        @Field("instance_id") instanceId: String
    ): DeactivationResponse
}
