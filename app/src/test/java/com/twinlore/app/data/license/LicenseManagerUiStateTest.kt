package com.twinlore.app.data.license

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LicenseManagerUiStateTest {

    @Test
    fun `isLicensedNow returns false when state is Unknown`() = runTest {
        val mgr = FakeLicenseManager(initialState = LicenseUiState.Unknown)
        assertFalse(mgr.isLicensedNow())
    }

    @Test
    fun `isLicensedNow returns false when state is Free`() = runTest {
        val mgr = FakeLicenseManager(initialState = LicenseUiState.Free)
        assertFalse(mgr.isLicensedNow())
    }

    @Test
    fun `isLicensedNow returns true when state is Licensed`() = runTest {
        val mgr = FakeLicenseManager(
            initialState = LicenseUiState.Licensed(email = "a@b.co")
        )
        assertTrue(mgr.isLicensedNow())
    }

    @Test
    fun `licenseUiState flow emits initial Unknown then resolves`() = runTest {
        val mgr = FakeLicenseManager(initialState = LicenseUiState.Unknown)
        assertEquals(LicenseUiState.Unknown, mgr.licenseUiState.first())

        mgr.setState(LicenseUiState.Licensed(email = "a@b.co"))
        assertEquals(
            LicenseUiState.Licensed(email = "a@b.co"),
            mgr.licenseUiState.first(),
        )
    }

    private class FakeLicenseManager(
        initialState: LicenseUiState,
    ) : LicenseManagerUiStateContract {
        private val flow = MutableStateFlow(initialState)
        override val licenseUiState = flow
        override val licenseTier =
            kotlinx.coroutines.flow.MutableStateFlow(LicenseTier.NONE)
        override fun isLicensedNow(): Boolean =
            flow.value is LicenseUiState.Licensed
        fun setState(s: LicenseUiState) { flow.value = s }
    }
}
