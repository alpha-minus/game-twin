package com.twinlore.app.data.license

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

/**
 * Turns a Lemon Squeezy activation failure into something worth showing a user.
 *
 * The API answers a bad key with a NON-2xx status and a JSON body that says exactly what is
 * wrong — a key that does not exist is `404` + `{"activated": false, "error": "license_key not
 * found"}`. Retrofit raises those as `HttpException`, so the body has to be dug out and parsed
 * by hand; miss that and every rejected key reads as "Server error (404). Please try again
 * later.", which points the user at our servers instead of at their key.
 *
 * Kept apart from [LicenseManager] so the mapping is unit-testable without a network stack.
 */
object LicenseActivationError {

    private val gson = Gson()

    private data class ErrorBody(
        @SerializedName("error") val error: String? = null,
    )

    /** The `error` field of an API error body, or null if the body is absent or unparseable. */
    fun parseApiError(body: String?): String? {
        if (body.isNullOrBlank()) return null
        return try {
            gson.fromJson(body, ErrorBody::class.java)?.error?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    /** Fallback when the response carried no usable error text. */
    fun serverError(statusCode: Int): String =
        "Server error ($statusCode). Please try again later."

    /**
     * Rewrite the API's wording into something actionable. [usage] and [limit] are only known
     * when the failure arrived as a 2xx body, so the activation-limit message adapts to whether
     * we actually have the numbers.
     */
    fun friendly(apiError: String, usage: Int? = null, limit: Int? = null): String = when {
        apiError.contains("activation limit", ignoreCase = true) ||
            apiError.contains("too many", ignoreCase = true) -> {
            val counts = if (usage != null && limit != null) " ($usage/$limit devices)" else ""
            "Activation limit reached$counts. " +
                "You can deactivate devices from your Lemon Squeezy account dashboard. " +
                "Go to your account, then Licenses, select your license and deactivate unused " +
                "devices. After deactivating, try activating again."
        }
        apiError.contains("not found", ignoreCase = true) ->
            "License key not found. Please check that you entered it correctly."
        apiError.contains("expired", ignoreCase = true) ->
            "This license key has expired."
        apiError.contains("disabled", ignoreCase = true) ->
            "This license key has been disabled."
        apiError.contains("invalid", ignoreCase = true) &&
            apiError.contains("license", ignoreCase = true) ->
            "Invalid license key. Please check that you entered it correctly."
        else -> apiError
    }
}
