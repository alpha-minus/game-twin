package com.twinlore.app.data.retroachievements

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.whenever

/**
 * The profile cache belongs to an account.
 *
 * It was a 15-minute TTL and nothing else, and it is where the Achievement Journey reads the site
 * rank on its closing card from: switch RetroAchievements accounts inside that window and the new
 * account was shown the old one's profile, points and rank, with no network call to notice.
 */
class RaProfileRepositoryAccountTest {

    @get:Rule val tempFolder = TemporaryFolder()

    private val dataStoreScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @After fun tearDown() = dataStoreScope.cancel()

    private fun dataStore() = PreferenceDataStoreFactory.create(scope = dataStoreScope) {
        File(tempFolder.root, "ra_profile.preferences_pb")
    }

    private fun summary(user: String, rank: Int) =
        RaUserSummaryResponse(user = user, totalPoints = 100, rank = rank)

    private fun credentialsFor(username: String) = mock<RetroAchievementsCredentialsRepository> {
        onBlocking { getCredentials() } doReturn RaCredentials(username, "key")
        // The profile flow combines with this one: a credential change is the only signal an
        // account switch produces, since the account does not live in the profile DataStore.
        on { credentials } doReturn MutableStateFlow(RaCredentials(username, "key"))
    }

    @Test
    fun `a fresh cache for the same account costs no call`() = runTest {
        val api = mock<RetroAchievementsApi> {
            onBlocking { getUserSummary(any(), any(), any(), any()) } doReturn summary("userA", 10)
        }
        val client = mock<RetroAchievementsApiClient> { on { this.api } doReturn api }
        val repository = RaProfileRepository(client, credentialsFor("userA"), dataStore())

        assertEquals(10, repository.load()?.rank)
        assertEquals(10, repository.load()?.rank)

        verifyBlocking(api, times(1)) { getUserSummary(any(), any(), any(), any()) }
    }

    @Test
    fun `a cache written for another account is a miss, however fresh`() = runTest {
        val api = mock<RetroAchievementsApi> {
            onBlocking { getUserSummary(any(), any(), any(), any()) } doReturn summary("userA", 10)
        }
        val client = mock<RetroAchievementsApiClient> { on { this.api } doReturn api }
        val store = dataStore()

        assertEquals(10, RaProfileRepository(client, credentialsFor("userA"), store).load()?.rank)

        // Same store, same TTL window, different signed-in account.
        whenever(api.getUserSummary(any(), any(), any(), any())).thenReturn(summary("userB", 4242))
        val asUserB = RaProfileRepository(client, credentialsFor("userB"), store).load()

        assertEquals("the new account must not be served the old one's rank", 4242, asUserB?.rank)
        assertEquals("userB", asUserB?.username)
    }

    /**
     * The flow the profile screen and the library's points line read is a separate gate from
     * [RaProfileRepository.load]'s: it deliberately serves a blob long past the TTL, so freshness
     * protects it not at all. A blob written for the previous account would simply stay on screen,
     * points and rank included, until some refresh happened to replace it.
     */
    @Test
    fun `the flow stops serving a blob once the account has changed`() = runTest {
        val api = mock<RetroAchievementsApi> {
            onBlocking { getUserSummary(any(), any(), any(), any()) } doReturn summary("userA", 10)
        }
        val client = mock<RetroAchievementsApiClient> { on { this.api } doReturn api }
        val store = dataStore()

        val asUserA = RaProfileRepository(client, credentialsFor("userA"), store)
        asUserA.load()
        assertEquals(10, asUserA.profile.first()?.rank)

        val asUserB = RaProfileRepository(client, credentialsFor("userB"), store)
        assertNull("the new account must be shown nothing rather than the old one's profile", asUserB.profile.first())

        whenever(api.getUserSummary(any(), any(), any(), any())).thenReturn(summary("userB", 4242))
        asUserB.refresh()
        assertEquals(4242, asUserB.profile.first()?.rank)
    }

    /**
     * The check has to *re-run*, not merely be correct when it runs.
     *
     * It lived inside a `map` over `dataStore.data`, and the account lives in
     * EncryptedSharedPreferences rather than in that DataStore - so switching or clearing it
     * produced no upstream emission at all. Every collector that outlived the switch (the profile
     * screen's StateFlow mirror, the library's points line) kept the departed account's blob, with
     * its points and its site rank, until some unrelated refresh happened to write.
     *
     * Real time and real threads rather than `runTest`: the flow hops to Dispatchers.IO, so virtual
     * time cannot drive it. Emphatically nothing writes the DataStore between the two assertions.
     */
    @Test
    fun `a live collector stops serving the blob the moment the account changes`() = runBlocking {
        val api = mock<RetroAchievementsApi> {
            onBlocking { getUserSummary(any(), any(), any(), any()) } doReturn summary("userA", 10)
        }
        val client = mock<RetroAchievementsApiClient> { on { this.api } doReturn api }
        val credentialsFlow = MutableStateFlow<RaCredentials?>(RaCredentials("userA", "key"))
        val credentials = mock<RetroAchievementsCredentialsRepository> {
            on { this.credentials } doReturn credentialsFlow
            onBlocking { getCredentials() } doSuspendableAnswer { credentialsFlow.value }
        }
        val repository = RaProfileRepository(client, credentials, dataStore())
        repository.refresh()

        val seen = Channel<RaProfileDisplay?>(Channel.UNLIMITED)
        val collector = launch(Dispatchers.Default) { repository.profile.collect { seen.send(it) } }
        assertEquals(10, withTimeout(5_000) { seen.receive() }?.rank)

        credentialsFlow.value = RaCredentials("userB", "key")

        assertNull(
            "the departed account's profile must not stay on screen until an unrelated write",
            withTimeout(5_000) { seen.receive() },
        )
        collector.cancel()
    }

    /** Signing out is the same event, and the same emission has to follow it. */
    @Test
    fun `clearing the account stops the flow too`() = runBlocking {
        val api = mock<RetroAchievementsApi> {
            onBlocking { getUserSummary(any(), any(), any(), any()) } doReturn summary("userA", 10)
        }
        val client = mock<RetroAchievementsApiClient> { on { this.api } doReturn api }
        val credentialsFlow = MutableStateFlow<RaCredentials?>(RaCredentials("userA", "key"))
        val credentials = mock<RetroAchievementsCredentialsRepository> {
            on { this.credentials } doReturn credentialsFlow
            onBlocking { getCredentials() } doSuspendableAnswer { credentialsFlow.value }
        }
        val repository = RaProfileRepository(client, credentials, dataStore())
        repository.refresh()

        val seen = Channel<RaProfileDisplay?>(Channel.UNLIMITED)
        val collector = launch(Dispatchers.Default) { repository.profile.collect { seen.send(it) } }
        assertEquals(10, withTimeout(5_000) { seen.receive() }?.rank)

        credentialsFlow.value = null

        assertNull(withTimeout(5_000) { seen.receive() })
        collector.cancel()
    }

    /**
     * A blob written before the account marker existed cannot be shown to belong to anyone, so it
     * is a miss - once. The refresh it costs writes the marker, and the next load is a hit again.
     */
    @Test
    fun `a blob with no account marker misses once and then hits`() = runTest {
        val api = mock<RetroAchievementsApi> {
            onBlocking { getUserSummary(any(), any(), any(), any()) } doReturn summary("userA", 10)
        }
        val client = mock<RetroAchievementsApiClient> { on { this.api } doReturn api }
        val store = dataStore()
        store.edit { prefs ->
            prefs[stringPreferencesKey("profile_json")] = """{"username":"userA","rank":99}"""
            prefs[longPreferencesKey("profile_sync_time")] = System.currentTimeMillis()
        }
        val repository = RaProfileRepository(client, credentialsFor("userA"), store)

        assertEquals("an unowned blob is not served", 10, repository.load()?.rank)
        assertEquals(10, repository.load()?.rank)

        verifyBlocking(api, times(1)) { getUserSummary(any(), any(), any(), any()) }
    }
}
