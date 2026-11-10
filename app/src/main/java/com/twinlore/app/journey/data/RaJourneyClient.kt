package com.twinlore.app.journey.data

import com.twinlore.app.data.retroachievements.RetroAchievementsApi
import com.twinlore.app.data.retroachievements.RetroAchievementsApiClient
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException

/**
 * A journey fetch that did not come back.
 *
 * [retryable] is the difference between a wait and a wall: a rate limit, a timeout or a 5xx is
 * worth another press of the button, while a 4xx that is not a 429 is the API refusing this
 * request as it stands (a revoked key, a game id that does not exist) and will refuse it again.
 * The screen reads this to decide whether to offer a Retry at all, so a permanent failure stops
 * being a button that is guaranteed to fail.
 *
 * [statusCode] is what the API answered, when there was an API answer at all - null for this
 * layer's own preconditions (no account configured, no game chosen), which carry their own message.
 * The screen reads it to tell "your key is not accepted" from "that game does not exist", which are
 * different things to do about it.
 */
class RaJourneyException(
    message: String,
    val retryable: Boolean = true,
    val statusCode: Int? = null,
) : Exception(message)

/**
 * Serializes, rate-limits, and retries calls against [RetroAchievementsApi].
 *
 * Callers should route every RetroAchievements request through [call] rather than
 * invoking [api] directly, so that requests never overlap and transient failures
 * (429 / 5xx / IO) are retried with backoff before surfacing to callers.
 */
@Singleton
class RaJourneyClient internal constructor(
    val api: RetroAchievementsApi,
    private val nowMillis: () -> Long,
) {

    @Inject
    constructor(apiClient: RetroAchievementsApiClient) : this(apiClient.api, System::currentTimeMillis)

    private val gateMutex = Mutex()
    private var lastCallAt = 0L

    /** Serialized, rate-limited, retrying call. Never throws HttpException; throws RaJourneyException after 4 failed attempts. */
    suspend fun <T> call(block: suspend (RetroAchievementsApi) -> T): T {
        var backoff = INITIAL_BACKOFF_MS

        repeat(MAX_ATTEMPTS) { attempt ->
            val isLastAttempt = attempt == MAX_ATTEMPTS - 1
            try {
                return gateMutex.withLock {
                    val wait = INTER_CALL_GAP_MS - (nowMillis() - lastCallAt)
                    if (wait > 0) delay(wait)
                    lastCallAt = nowMillis()
                    block(api)
                }
            } catch (e: HttpException) {
                val code = e.code()
                if (code != 429 && code < 500) {
                    // Not retried, and not worth offering a retry for either: the request as sent
                    // is what the API refused.
                    throw RaJourneyException("RetroAchievements API error $code", retryable = false, statusCode = code)
                }
                if (isLastAttempt) throw RaJourneyException(RATE_LIMIT_MESSAGE)
                val retryAfterMs = e.response()?.headers()?.get("Retry-After")?.toLongOrNull()?.times(1000)
                delay(retryAfterMs ?: backoff)
                backoff *= 2
            } catch (e: IOException) {
                if (isLastAttempt) throw RaJourneyException(RATE_LIMIT_MESSAGE)
                delay(backoff)
                backoff *= 2
            }
        }

        throw RaJourneyException(RATE_LIMIT_MESSAGE)
    }

    private companion object {
        const val INTER_CALL_GAP_MS = 350L
        const val INITIAL_BACKOFF_MS = 900L
        const val MAX_ATTEMPTS = 4
        const val RATE_LIMIT_MESSAGE = "RetroAchievements is rate limiting requests, try again shortly"
    }
}
