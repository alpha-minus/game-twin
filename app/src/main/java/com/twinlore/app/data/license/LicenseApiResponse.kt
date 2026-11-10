package com.twinlore.app.data.license

import com.google.gson.annotations.SerializedName

/**
 * API response models matching Lemon Squeezy License API structure
 * Documentation: https://docs.lemonsqueezy.com/guides/tutorials/license-keys
 */


/**
 * License key information from API responses
 */
data class LicenseKeyResponse(
    val id: Long,
    val status: String, // "inactive", "active", "expired", "disabled"
    val key: String,
    @SerializedName("activation_limit")
    val activationLimit: Int,
    @SerializedName("activation_usage")
    val activationUsage: Int,
    @SerializedName("created_at")
    val createdAt: String,
    @SerializedName("expires_at")
    val expiresAt: String? = null
)

/**
 * License instance information from API responses
 */
data class LicenseInstanceResponse(
    val id: String,
    val name: String,
    @SerializedName("created_at")
    val createdAt: String
)

/**
 * Metadata information from API responses
 */
data class LicenseMetaResponse(
    @SerializedName("store_id")
    val storeId: Long,
    @SerializedName("order_id")
    val orderId: Long,
    @SerializedName("order_item_id")
    val orderItemId: Long,
    @SerializedName("product_id")
    val productId: Long,
    @SerializedName("product_name")
    val productName: String,
    @SerializedName("variant_id")
    val variantId: Long,
    @SerializedName("variant_name")
    val variantName: String,
    @SerializedName("customer_id")
    val customerId: Long,
    @SerializedName("customer_name")
    val customerName: String,
    @SerializedName("customer_email")
    val customerEmail: String
)

/**
 * Activation API response
 */
data class ActivationResponse(
    val activated: Boolean,
    val error: String? = null,
    @SerializedName("license_key")
    val licenseKey: LicenseKeyResponse,
    val instance: LicenseInstanceResponse? = null,
    val meta: LicenseMetaResponse? = null
)

/**
 * Validation API response
 */
data class ValidationResponse(
    val valid: Boolean,
    val error: String? = null,
    @SerializedName("license_key")
    val licenseKey: LicenseKeyResponse? = null,
    val instance: LicenseInstanceResponse? = null,
    val meta: LicenseMetaResponse? = null
)

/**
 * Deactivation API response
 */
data class DeactivationResponse(
    val deactivated: Boolean,
    val error: String? = null,
    @SerializedName("license_key")
    val licenseKey: LicenseKeyResponse? = null,
    val meta: LicenseMetaResponse? = null
)
