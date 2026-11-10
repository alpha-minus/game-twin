package com.twinlore.app.journey.domain

import com.twinlore.app.journey.domain.model.AchievementType
import com.twinlore.app.journey.domain.model.AwardKind
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

object JourneyParsing {

    fun normType(raw: String?): AchievementType {
        if (raw.isNullOrBlank()) return AchievementType.STANDARD
        val t = raw.lowercase().replace(Regex("[\\s-]+"), "_")
        return when {
            t.contains("miss") -> AchievementType.MISSABLE
            t.contains("prog") -> AchievementType.PROGRESSION
            t.contains("win") -> AchievementType.WIN_CONDITION
            else -> AchievementType.STANDARD
        }
    }

    private val sqlFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    fun parseWhen(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        return runCatching {
            if (raw.contains("T")) OffsetDateTime.parse(raw).toInstant().toEpochMilli()
            else LocalDateTime.parse(raw, sqlFormat).toInstant(ZoneOffset.UTC).toEpochMilli()
        }.getOrNull()
    }

    /** Case- and whitespace-tolerant like [normType]: the API's casing is not a contract. */
    fun parseAwardKind(raw: String?): AwardKind? {
        val k = raw?.trim()?.lowercase()
        return when {
            k.isNullOrEmpty() -> null
            // The site's two beaten kinds are two different awards, not one: softcore is the
            // hollow silver circle its own progression row draws.
            k == "beaten-softcore" -> AwardKind.BEATEN_CASUAL
            k.startsWith("beaten") -> AwardKind.BEATEN
            k == "completed" -> AwardKind.COMPLETED
            k == "mastered" -> AwardKind.MASTERED
            else -> null
        }
    }

    /**
     * Media ids at or below this are RetroAchievements' own placeholder assets rather than a
     * game's art.
     *
     * The site answers a game with no screenshot by handing back a picture that *says* it has
     * no screenshot - "/Images/000010.png" is the "No screenshot found" card, 000001 the older
     * blank - rather than by leaving the field empty, so a chart that trusted the field drew
     * the apology as though it were the game.
     *
     * A ceiling rather than a list of exact ids: the placeholders are the first handful of
     * assets the site ever served, while real game media runs to five and six digits (the
     * sample this was written against carries 067895, 054993 and 051872 beside a placeholder
     * 000010), so everything genuine sits far above this line.
     */
    private const val PLACEHOLDER_MAX_ID = 10

    // The whole filename has to be the id, which is the shape the site actually serves
    // ("/Images/000010.png"). A trailing digit run anywhere in the name would have read
    // "/Images/some-game-2.png" as media id 2 and thrown the art away.
    private val MEDIA_ID = Regex("""/(\d+)\.[A-Za-z]+$""")

    /** Whether [path] is one of the site's placeholders rather than a game's own art. */
    fun isPlaceholderArt(path: String): Boolean {
        val id = MEDIA_ID.find(path)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: return false
        return id <= PLACEHOLDER_MAX_ID
    }

    /** A usable media path, or null when it is missing, blank or a no-art placeholder. */
    fun artPath(raw: String?): String? =
        raw?.takeIf { it.isNotBlank() && !isPlaceholderArt(it) }

    fun artList(
        imageIngame: String?, imageTitle: String?, imageBoxArt: String?,
        imageIcon: String?, fallbackIcon: String?,
    ): List<String> =
        listOfNotNull(imageIngame, imageTitle, imageBoxArt, imageIcon, fallbackIcon)
            .distinct()
            .filterNot { isPlaceholderArt(it) }
}
