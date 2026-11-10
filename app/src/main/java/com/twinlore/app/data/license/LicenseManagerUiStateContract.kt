package com.twinlore.app.data.license

import kotlinx.coroutines.flow.StateFlow

/**
 * The UI-facing slice of [LicenseManager]. Factored out as an interface
 * so ViewModels and gates don't depend on the full concrete class (which
 * brings in Retrofit, DataStore, and other heavy deps for tests).
 */
interface LicenseManagerUiStateContract {
    /**
     * Current license state as a StateFlow. Starts at [LicenseUiState.Unknown]
     * during cold start and resolves to [LicenseUiState.Free] or
     * [LicenseUiState.Licensed] once the persisted license record has been
     * read.
     */
    val licenseUiState: StateFlow<LicenseUiState>

    /**
     * Synchronous snapshot of [licenseUiState]. Returns `true` only when the
     * current value is [LicenseUiState.Licensed]. [LicenseUiState.Unknown] is
     * treated as not-licensed (fail-closed) so an unlicensed user cannot sneak
     * past the cap via a race at cold start.
     */
    fun isLicensedNow(): Boolean

    /** Current cosmetic entitlement tier. */
    val licenseTier: StateFlow<LicenseTier>
}
