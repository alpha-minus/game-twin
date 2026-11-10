package com.twinlore.app.ui.screens

import com.twinlore.app.data.retroachievements.RaGameNotFoundException
import com.twinlore.app.data.retroachievements.RaUserNotFoundException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

/** The RetroAchievements drawer's messages: each failure names its own cause. */
class RaErrorMessagesTest {

    private fun http(code: Int) = HttpException(Response.error<Any>(code, "".toResponseBody("text/html".toMediaType())))

    @Test
    fun `an unknown game id on link names the id`() {
        assertEquals(
            "There is no RetroAchievements game with ID 99999999. Check the number in the game's address on retroachievements.org.",
            RaErrorMessages.forLink(RaGameNotFoundException(99999999)),
        )
    }

    @Test
    fun `a linked game that no longer exists says to relink`() {
        assertEquals(
            "RetroAchievements has no game with ID 99999999 any more. Unlink it and link the game again.",
            RaErrorMessages.forLoad(RaGameNotFoundException(99999999)),
        )
    }

    @Test
    fun `an unknown user names the user in both places`() {
        val expected = "RetroAchievements has no user named \"nobodyhere\". Check the username in Settings."
        assertEquals(expected, RaErrorMessages.forLoad(RaUserNotFoundException("nobodyhere")))
        assertEquals(expected, RaErrorMessages.forLink(RaUserNotFoundException("nobodyhere")))
    }

    @Test
    fun `a rejected key and rate limiting are explained on link and load`() {
        val key = "RetroAchievements rejected the API key. Check your username and API key in Settings."
        val busy = "RetroAchievements is receiving too many requests right now. Wait a minute and try again."
        assertEquals(key, RaErrorMessages.forLoad(http(401)))
        assertEquals(key, RaErrorMessages.forLink(http(401)))
        assertEquals(busy, RaErrorMessages.forLoad(http(429)))
        assertEquals(busy, RaErrorMessages.forLink(http(429)))
    }

    @Test
    fun `comments name comments, not achievements, and carry no raw http line`() {
        assertEquals("Could not load comments. Please try again.", RaErrorMessages.forComments(IllegalStateException()))
        assertEquals(
            "RetroAchievements is receiving too many requests right now. Wait a minute and try again.",
            RaErrorMessages.forComments(http(429)),
        )
    }

    @Test
    fun `no message has an em dash`() {
        listOf(
            RaGameNotFoundException(1), RaUserNotFoundException("u"), http(401), http(429), http(500), http(404),
            java.net.SocketTimeoutException(), java.io.IOException(), IllegalStateException(),
        ).forEach { e ->
            assertFalse(RaErrorMessages.forLoad(e).contains('—'))
            assertFalse(RaErrorMessages.forLink(e).contains('—'))
            assertFalse(RaErrorMessages.forComments(e).contains('—'))
        }
    }
}
