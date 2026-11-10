package com.twinlore.app.data.steam

import com.twinlore.app.data.local.SteamGameCacheDao
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.fail
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import retrofit2.HttpException
import retrofit2.Response

/**
 * Which failure a refused achievements request reports.
 *
 * Seen 2026-09-17 with a profile whose game details are private linking Hades: Steam
 * answered GetPlayerAchievements with 403 "Profile is not public", and the
 * owned-games check that follows came back with no games at all, because a
 * private profile shows none. That empty list was read as "not owned" and
 * checked first, so the user was told the game was not in their library.
 */
class SteamGameCacheRepositoryErrorTest {

    private val creds = mock<SteamCredentialsRepository> {
        onBlocking { getCredentials() } doReturn SteamCredentials(vanityUrl = "example-user", steamId = "1")
    }

    private fun httpError(code: Int, body: String) = HttpException(
        Response.error<Any>(code, body.toResponseBody("application/json".toMediaType()))
    )

    private fun repo(playerError: HttpException, owned: SteamOwnedGamesResponse): SteamGameCacheRepository {
        val api = mock<SteamApi> {
            onBlocking { getSchemaForGame(eq(1145360), any()) } doReturn SteamSchemaResponse(game = SteamSchemaGame(gameName = "Hades"))
            onBlocking { getPlayerAchievements(any(), eq(1145360), any()) } doSuspendableAnswer { throw playerError }
            onBlocking { getOwnedGames(any(), any(), any()) } doReturn owned
        }
        val client = mock<SteamApiClient> { on { this.api } doReturn api }
        val dao = mock<SteamGameCacheDao> { onBlocking { getByVaultId(any()) } doReturn null }
        return SteamGameCacheRepository(dao, client, creds)
    }

    private suspend inline fun <reified T : Exception> assertThrows(crossinline block: suspend () -> Unit) {
        try {
            block()
            fail("expected ${T::class.simpleName}")
        } catch (e: Exception) {
            if (e !is T) fail("expected ${T::class.simpleName} but was $e")
        }
    }

    @Test
    fun `a private profile is reported as private, not as a game you do not own`() = runTest {
        val repo = repo(
            httpError(403, """{"playerstats":{"error":"Profile is not public","success":false}}"""),
            SteamOwnedGamesResponse(SteamOwnedGamesResult()),
        )
        assertThrows<SteamProfilePrivateException> { repo.getGameProgress("v1", 1145360, forceRefresh = true) }
    }

    @Test
    fun `a game missing from a visible library is still not owned`() = runTest {
        val repo = repo(
            httpError(400, """{"playerstats":{"error":"Requested app has no stats","success":false}}"""),
            SteamOwnedGamesResponse(SteamOwnedGamesResult(gameCount = 1, games = listOf(SteamOwnedGame(appId = 10, name = "Other")))),
        )
        assertThrows<SteamGameNotOwnedException> { repo.getGameProgress("v1", 1145360, forceRefresh = true) }
    }
}
