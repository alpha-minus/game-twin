package com.twinlore.app.data.license

import org.junit.Assert.assertEquals
import org.junit.Test

class LicenseUiStateMapperTest {

    @Test
    fun `Valid maps to Licensed with customer email`() {
        val state = LicenseState.Valid(
            customerEmail = "alice@example.com",
            expiresAt = null,
            activationUsage = 1,
            activationLimit = 5,
        )
        assertEquals(
            LicenseUiState.Licensed(email = "alice@example.com"),
            state.toUiState(),
        )
    }

    @Test
    fun `Valid with null email maps to Licensed with null email`() {
        val state = LicenseState.Valid(
            customerEmail = null,
            expiresAt = null,
            activationUsage = 1,
            activationLimit = 5,
        )
        assertEquals(LicenseUiState.Licensed(email = null), state.toUiState())
    }

    @Test
    fun `NotActivated maps to Free`() {
        assertEquals(LicenseUiState.Free, LicenseState.NotActivated.toUiState())
    }

    @Test
    fun `Expired maps to Free`() {
        val state = LicenseState.Expired(expiresAt = null, gracePeriodRemainingDays = null)
        assertEquals(LicenseUiState.Free, state.toUiState())
    }

    @Test
    fun `Error maps to Free`() {
        val state = LicenseState.Error("network down")
        assertEquals(LicenseUiState.Free, state.toUiState())
    }
}
