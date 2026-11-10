package com.twinlore.app.ui.fold.guide

import com.twinlore.app.data.retroachievements.RaAchievementDisplay
import com.twinlore.app.data.steam.SteamAchievementDisplay
import com.twinlore.app.domain.SavedTab

/**
 * The few things the panel has to look up for itself.
 *
 * Everything a reader needs (the file, its cache, the chapter, the reading
 * position, the zoom) belongs to the app's viewers, which the panel hosts
 * rather than reimplements. What is left is which guide is open, which images
 * the vault has, and the one write that hands a blocked link to the app.
 *
 * This seam exists so those rules are unit tests rather than a device walk.
 */
interface FoldGuideData {

    /**
     * The guide bookmark the app has open for [vaultId], by the app's own
     * definition of open, or null when there is none.
     */
    suspend fun currentGuide(vaultId: String): SavedTab?

    /**
     * The vault's saved images, in the order the app's Image tab shows them.
     */
    suspend fun images(vaultId: String): List<FoldImage>

    /**
     * The image the app's Image tab is open on for [vaultId], by the app's
     * own record of it, or null when it has never chosen one.
     */
    suspend fun currentImage(vaultId: String): String?

    /**
     * Makes [imageId] the image [vaultId] is open on, and the most recently
     * opened one. The same record the app's Image tab writes when it selects,
     * so paging in the panel is where the app opens next, and the reverse.
     */
    suspend fun touchImage(vaultId: String, imageId: String)

    /** The guide's text size, in the column the app's own slider writes. */
    suspend fun saveTextZoom(bookmarkId: String, zoom: Float)

    /**
     * Where the app last left this image, so the panel opens on the same part
     * of it. Null when it has never been opened.
     */
    suspend fun imageView(vaultId: String, imageId: String): FoldImageView?

    /**
     * Hands the view back, so the app opens where the panel left off. The same
     * manager the app's Image tab writes to, in both directions.
     */
    suspend fun saveImageView(vaultId: String, imageId: String, view: FoldImageView)

    /**
     * The achievements as they are already cached, with no network read.
     *
     * Opening a surface over a running game is not the moment to start a sync:
     * the process is already under memory pressure from the game, and somebody
     * glancing at a list mid-game did not ask for a round trip. The cache is
     * the app's own, so what shows here is what the app last saw.
     */
    suspend fun awards(vaultId: String): FoldAwards

    /**
     * Records that [vaultId] was opened, the way the library does when a
     * vault is opened from it: the Always on Top switch is an open too, and
     * the library's last-opened order must say so.
     */
    suspend fun markOpened(vaultId: String)

    /**
     * A game started since the last one this feature handled, or null.
     *
     * The app follows what you launch on its own resume, and while the panel
     * is up that resume never comes: the activity is behind a running game.
     * So the panel asks the same question at the moment it opens.
     *
     * Null when there is nothing to say, which is most opens: detection off,
     * no source configured, no new launch since the last handled one, an
     * unconfirmed fuzzy match (the library owns that dialog and the panel must
     * not guess), or a game that is already the one on screen.
     */
    suspend fun detectNewlyPlayedGame(currentVaultId: String?): FoldDetectedGame?

    /**
     * Records [marker] as handled, so neither the panel nor the app asks about
     * this launch again. Called once the user has answered, either way.
     */
    suspend fun markLaunchHandled(marker: String?)

    /**
     * The same forced refresh the app's drawer runs, through the same cache, so
     * refreshing in either place is a refresh for both. RetroAchievements or
     * Steam, whichever the vault is linked to, as [awards] reads.
     *
     * Null when the vault has no linked game or the fetch failed, in which case
     * the caller keeps showing what it had rather than blanking the list.
     */
    suspend fun refreshAwards(vaultId: String): FoldAwards?

    /**
     * One achievement's comments, newest last. A network read, and the only one
     * on this surface that is not a refresh, so it happens on a tap and never
     * on open.
     */
    suspend fun awardComments(achievementId: Int): List<FoldComment>

    /**
     * Files a captured screenshot into the vault as an image, and returns it.
     *
     * The same two writes the app's own screenshot path makes, in the same
     * order: the bytes into the vault's folder, then the row that points at
     * them. So a shot taken from the panel is a vault image like any other -
     * it is in the app's Image tab, and in this panel's own list, without
     * either side importing anything.
     *
     * Null when the copy or the row failed, which the caller reports rather
     * than silently dropping.
     */
    suspend fun saveScreenshot(vaultId: String, file: java.io.File): FoldImage?

    /**
     * Takes [imageId] out of [vaultId]: the row, its markers, and the app's
     * selection if it was on it. The same three things the app's own Image
     * tab removes, so the picture is gone from both places at once. The file
     * itself is left where it is, as the app leaves it: it is the user's, in
     * their folder.
     */
    suspend fun deleteImage(vaultId: String, imageId: String)
}

/** One comment on an achievement, flattened to what the panel draws. */
data class FoldComment(
    val author: String,
    val text: String,
    val submitted: String,
)

/** Which service a vault's achievements come from. RetroAchievements wins when linked to both, as in the app. */
enum class FoldAwardSource { RA, STEAM }

/**
 * The achievements and everything the header above them says.
 *
 * Carries [RaAchievementDisplay] rather than a flattened copy, because the row
 * that draws it is the app's own and takes exactly that. The counts are kept
 * apart from the list for the same reason the app keeps them apart: soft and
 * hardcore are two different bars over one set.
 *
 * A Steam set fills [steamAchievements] instead, with [source] saying which
 * list is the real one. Steam has no points, hardcore, award or rank, so those
 * stay at their defaults and the header does not draw them.
 */
data class FoldAwards(
    val source: FoldAwardSource = FoldAwardSource.RA,
    val achievements: List<RaAchievementDisplay> = emptyList(),
    val steamAchievements: List<SteamAchievementDisplay> = emptyList(),
    val gameTitle: String = "",
    /**
     * The game's image, already a full URL: the square RA game icon, or for
     * Steam the wide store banner. Null when the set has none.
     */
    val gameIconUrl: String? = null,
    val totalPlayers: Int = 0,
    val unlocked: Int = 0,
    val unlockedHardcore: Int = 0,
    val total: Int = 0,
    val points: Int = 0,
    val totalPoints: Int = 0,
    /** "mastered", "completed", "beaten-hardcore", "beaten-softcore", or null. */
    val highestAwardKind: String? = null,
    val rank: Int? = null,
    /** Total playtime in minutes, as the app's own header shows it. Null when unknown. */
    val playtimeMinutes: Int? = null,
    /** Whether a linked game was found at all, as opposed to one with no data. */
    val linked: Boolean = false,
) {
    val isEmpty: Boolean
        get() = when (source) {
            FoldAwardSource.RA -> achievements.isEmpty()
            FoldAwardSource.STEAM -> steamAchievements.isEmpty()
        }
}

/** Where an image is panned and zoomed to. */
data class FoldImageView(val zoom: Float, val offsetX: Float, val offsetY: Float)

/** One saved image, as the panel needs it. */
data class FoldImage(val id: String, val title: String, val uri: String?)
