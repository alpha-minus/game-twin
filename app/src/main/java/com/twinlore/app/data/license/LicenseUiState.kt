package com.twinlore.app.data.license

/**
 * UI-layer projection of [LicenseState]. Used by ViewModels and the
 * premium-feature gates; the data layer uses [LicenseState] directly.
 */
sealed class LicenseUiState {
    /** Initial state before persisted license data has been read. */
    object Unknown : LicenseUiState()

    /** Unlicensed, expired, or errored. All non-Licensed states collapse here. */
    object Free : LicenseUiState()

    /** Currently activated license. [email] may be null if not stored. */
    data class Licensed(val email: String?) : LicenseUiState()
}

/**
 * Map a data-layer [LicenseState] onto the UI projection.
 *
 * - [LicenseState.Valid] -> [LicenseUiState.Licensed]
 * - every other state   -> [LicenseUiState.Free]
 *
 * [LicenseUiState.Unknown] is a UI-layer-only sentinel; it is never
 * produced by this function.
 */
fun LicenseState.toUiState(): LicenseUiState = when (this) {
    is LicenseState.Valid -> LicenseUiState.Licensed(email = customerEmail)
    else -> LicenseUiState.Free
}
