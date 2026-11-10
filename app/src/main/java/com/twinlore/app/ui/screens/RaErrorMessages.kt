package com.twinlore.app.ui.screens

import com.twinlore.app.data.retroachievements.RaGameNotFoundException
import com.twinlore.app.data.retroachievements.RaUserNotFoundException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * What the RetroAchievements drawer says when loading or linking fails.
 *
 * Each cause gets its own sentence. The generic "please try again" is only for
 * failures nothing more specific is known about: RetroAchievements reports a
 * missing game and a missing user as the same empty answer, and the repository
 * tells them apart before they get here.
 */
internal object RaErrorMessages {

    private const val REJECTED_KEY =
        "RetroAchievements rejected the API key. Check your username and API key in Settings."
    private const val RATE_LIMITED =
        "RetroAchievements is receiving too many requests right now. Wait a minute and try again."

    /** Loading a linked game's achievements. */
    fun forLoad(e: Throwable): String = shared(e) ?: when {
        e is RaGameNotFoundException ->
            "RetroAchievements has no game with ID ${e.gameId} any more. Unlink it and link the game again."
        e is retrofit2.HttpException && e.code() == 404 ->
            "RetroAchievements account not found. Check your username in Settings."
        else -> "Could not load achievements. Please try again."
    }

    /** Linking a game by its ID. */
    fun forLink(e: Throwable): String = shared(e) ?: when {
        e is RaGameNotFoundException ->
            "There is no RetroAchievements game with ID ${e.gameId}. Check the number in the game's address on retroachievements.org."
        e is retrofit2.HttpException && e.code() == 404 ->
            "No matching game found on RetroAchievements. Try a different Game ID."
        else -> "Could not link game. Please try again."
    }

    /** Loading an achievement's comments. */
    fun forComments(e: Throwable): String = shared(e) ?: "Could not load comments. Please try again."

    private fun shared(e: Throwable): String? = when {
        e is RaUserNotFoundException ->
            "RetroAchievements has no user named \"${e.username}\". Check the username in Settings."
        e is retrofit2.HttpException && e.code() in listOf(401, 403) -> REJECTED_KEY
        e is retrofit2.HttpException && e.code() == 429 -> RATE_LIMITED
        e is retrofit2.HttpException && e.code() in 500..599 ->
            "RetroAchievements is temporarily unavailable. Try again later."
        e is UnknownHostException || e is ConnectException ->
            "Could not connect to RetroAchievements. Check your internet connection."
        e is SocketTimeoutException ->
            "RetroAchievements is not responding. Try again later."
        e is retrofit2.HttpException ->
            "Request to RetroAchievements failed (HTTP ${e.code()}). Try again later."
        e is java.io.IOException ->
            "Connection to RetroAchievements failed. Check your internet connection."
        else -> null
    }
}
