package com.twinlore.app.data.license

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.twinlore.app.data.security.AppSecretsStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * The supporter key and customer email move out of the plaintext license DataStore into
 * [AppSecretsStore]. The DataStore is a process-wide singleton in the test JVM, so every test
 * seeds its own baseline first.
 */
@RunWith(RobolectricTestRunner::class)
class LicenseRepositorySecretsTest {

    private lateinit var context: Context

    @Before
    fun setUp() = runTest {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().commit()
        with(LicenseRepository) {
            context.licenseDataStore.edit { it.clear() }
        }
    }

    private fun secrets() = AppSecretsStore(context) { ctx ->
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    private fun repo() = LicenseRepository(context, secrets())

    private fun secretsFileContains(name: String): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).contains(name)

    private suspend fun licenseStoreContains(name: String): Boolean = with(LicenseRepository) {
        context.licenseDataStore.data.first().contains(stringPreferencesKey(name))
    }

    private fun activation(key: String, email: String?) = ActivationResponse(
        activated = true,
        licenseKey = LicenseKeyResponse(
            id = 1L,
            status = "active",
            key = key,
            activationLimit = 3,
            activationUsage = 1,
            createdAt = "2026-08-23T00:00:00Z",
        ),
        instance = LicenseInstanceResponse(
            id = "instance-1",
            name = "device",
            createdAt = "2026-08-23T00:00:00Z",
        ),
        meta = email?.let {
            LicenseMetaResponse(
                storeId = 10L,
                orderId = 11L,
                orderItemId = 12L,
                productId = 13L,
                productName = "Game Twin",
                variantId = 14L,
                variantName = "Supporter",
                customerId = 15L,
                customerName = "Player",
                customerEmail = it,
            )
        },
    )

    @Test
    fun `a saved activation puts key and email in the secrets store, not the DataStore`() = runTest {
        val repo = repo()

        assertTrue(repo.saveLicenseData(activation("AAAA-BBBB", "player@example.com"), "fingerprint"))

        assertFalse(licenseStoreContains("license_key"))
        assertFalse(licenseStoreContains("customer_email"))
        assertTrue(secretsFileContains("license_key"))
        assertTrue(secretsFileContains("customer_email"))

        val stored = repo.licenseData.first()
        assertEquals("AAAA-BBBB", stored?.licenseKey)
        assertEquals("player@example.com", stored?.customerEmail)
        assertEquals("instance-1", stored?.instanceId)
    }

    @Test
    fun `plaintext key and email migrate into the secrets store on first read`() = runTest {
        with(LicenseRepository) {
            context.licenseDataStore.edit {
                it[stringPreferencesKey("license_key")] = "OLD-KEY"
                it[stringPreferencesKey("instance_id")] = "old-instance"
                it[stringPreferencesKey("customer_email")] = "old@example.com"
            }
        }

        val stored = repo().licenseData.first()

        assertEquals("OLD-KEY", stored?.licenseKey)
        assertEquals("old@example.com", stored?.customerEmail)
        assertFalse(licenseStoreContains("license_key"))
        assertFalse(licenseStoreContains("customer_email"))
        assertTrue(secretsFileContains("license_key"))
        assertTrue(secretsFileContains("customer_email"))
    }

    @Test
    fun `no license reads as null`() = runTest {
        assertNull(repo().licenseData.first())
    }

    @Test
    fun `clearing the license removes key and email from the secrets store`() = runTest {
        val repo = repo()
        repo.saveLicenseData(activation("AAAA-BBBB", "player@example.com"), "fingerprint")

        repo.clearLicenseData()

        assertNull(repo.licenseData.first())
        assertFalse(secretsFileContains("license_key"))
        assertFalse(secretsFileContains("customer_email"))
    }

    @Test
    fun `an activation without meta keeps a previously stored email`() = runTest {
        val repo = repo()
        repo.saveLicenseData(activation("AAAA-BBBB", "player@example.com"), "fingerprint")

        repo.saveLicenseData(activation("AAAA-BBBB", null), "fingerprint")

        assertEquals("player@example.com", repo.licenseData.first()?.customerEmail)
    }

    private companion object {
        const val PREFS = "license_secrets_test"
    }
}
