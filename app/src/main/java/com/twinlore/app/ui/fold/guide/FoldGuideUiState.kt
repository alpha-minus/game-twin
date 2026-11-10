package com.twinlore.app.ui.fold.guide

import com.twinlore.app.data.AppIcon
import com.twinlore.app.data.StatusBarMode
import com.twinlore.app.data.fold.FoldHandleStyle
import com.twinlore.app.data.metrics.SystemMetricsSnapshot
import com.twinlore.app.data.retroachievements.RaAchievementDisplay
import com.twinlore.app.domain.RaAchievementSortField
import com.twinlore.app.domain.RaAchievementTypeFilter
import com.twinlore.app.domain.SortDirection
import com.twinlore.app.domain.SavedTab

/**
 * Which of the panel's two surfaces is showing.
 *
 * Two, and there is no plan for a third. The panel that was deleted carried
 * five, and the reason it grew was that each one looked cheap next to the one
 * before it.
 */
enum class FoldPanelSurface(val label: String) {
    GUIDE("Guide"),
    IMAGE("Image"),
    // Short, because this label sits in a bar of five and the full word
    // "Achievements" pushed the actions off the end of it. The app's own tab
    // still says the whole word; there is room for it there.
    AWARDS("Achieve"),

    /**
     * A surface of its own, not a layer over the page.
     *
     * It was a layer, so that dragging the text slider could fade it and show
     * the guide underneath at the size being chosen. It never worked here: the
     * reader is a WebView, a real view in the hierarchy, and Compose drawn
     * "after" it does not reliably land on top of it, so the settings came up
     * behind the guide. A surface cannot lose that argument, and the sample
     * sentence on the size row gives back what the fade was for.
     */
    SETTINGS("Settings"),
}

/**
 * What the panel has to render, resolved before composition so the composable
 * never has to know about file types.
 *
 * Every branch carries the bookmark and nothing else. The panel used to carry a
 * resolved URI, a scroll ratio and a chapter list of its own; all three were its
 * own worse answers to questions the app's viewers already answer, and each one
 * was a place for the two surfaces to disagree.
 */
sealed interface FoldGuide {

    /** HTML, a saved web page, an EPUB or a TXT FAQ: the single-guide reader. */
    data class Web(val tab: SavedTab) : FoldGuide

    /** A page group, which is most guides. The group viewer owns its chapters. */
    data class Group(val tab: SavedTab) : FoldGuide

    /** A PDF, opened by the app's PDF viewer, which owns the page it is on. */
    data class Pdf(val tab: SavedTab) : FoldGuide

    /**
     * A code wheel, an HTMLZIP or a link. Whole screens with their own
     * navigation in the app, and naming the type with a way into the app beats
     * a blank surface that looks broken.
     */
    data class Unsupported(val typeLabel: String) : FoldGuide
}

/** A link the reader refused to follow, named so you know which one it was. */
data class FoldBlockedLink(val url: String)

/**
 * A game detected as started while the panel was up.
 *
 * [launchMarker] is what the app's own pass stores to remember a launch has
 * been dealt with; it travels with the answer so that answering here settles
 * it for the app too.
 */
data class FoldDetectedGame(
    val vaultId: String,
    val title: String,
    val launchMarker: String?,
    /** Its cover art, as something Coil can load, so the offer shows the game and not just a name. */
    val artworkUri: String? = null,
)

/**
 * Everything the panel draws, in one value.
 *
 * There is no palette or font here. The viewers are the app's own and read both
 * from `SettingsRepository` through `ThemedContent` and `AppUiSettings`, so the
 * panel does not carry a second copy of the theme.
 */
data class FoldGuideUiState(
    val surface: FoldPanelSurface = FoldPanelSurface.GUIDE,
    val loading: Boolean = false,
    val guide: FoldGuide? = null,
    /**
     * The vault the panel is showing, or null outside a session.
     *
     * Carried so the image surface can hand it to the app's own viewer. That
     * viewer keys its decoded-bitmap cache on the vault, so a panel that did
     * not know the vault asked under a different key from the app and decoded
     * every picture again for itself, full size, next to a running game.
     */
    val vaultId: String? = null,
    /**
     * Which side the handle is on and how far down it, which is the point the
     * panel grows out of when it opens.
     *
     * No room is reserved for the handle. It floats above the panel, because it
     * is the only fold control the chrome cannot hide, and it shrinks while the
     * panel is up rather than pushing the page aside.
     */
    val handleOnRight: Boolean = true,
    val handleYFraction: Float = 0.5f,
    /**
     * A held handle is asking whether to close the app. Never persisted: it
     * describes a question that is on screen right now.
     */
    val closePrompt: Boolean = false,
    /**
     * The shown image is being asked about before it goes. Like
     * [closePrompt], a question on screen right now and nothing more.
     */
    val deletePrompt: Boolean = false,
    val images: List<FoldImage> = emptyList(),
    val imageIndex: Int = 0,
    val blockedLink: FoldBlockedLink? = null,
    /** A game started behind the panel, waiting to be accepted or waved off. */
    val detectedGame: FoldDetectedGame? = null,
    /**
     * The emblem the offer's Support button wears, or null when there is
     * nothing to ask for: a supporter, or a license state not yet known.
     */
    val supportEmblem: AppIcon? = null,
    val swapAvailable: Boolean = false,
    val barCollapsed: Boolean = false,
    val handleSizeDp: Int = FoldHandleStyle.DEFAULT_SIZE_DP,
    val handleAlpha: Float = FoldHandleStyle.DEFAULT_ALPHA,
    val handleRecess: Float = FoldHandleStyle.DEFAULT_RECESS,
    val handleClockBattery: Boolean = false,
    /** The effective app icon: the handle's mark, and the one the settings mark as in use. */
    val appIcon: AppIcon = AppIcon.DEFAULT,
    /** The icons this user may pick: unlocked by their license, Pixel once it is earned. */
    val selectableIcons: List<AppIcon> = listOf(AppIcon.DEFAULT),
    /** The app's font, which the panel is set in too. */
    val appFont: com.twinlore.app.data.AppFont = com.twinlore.app.data.AppFont.DEFAULT,
    val title: String = "",
    val textZoom: Float = 1f,
    val themeInject: Boolean = true,
    /** The app's palette, which the panel is themed with too. Null is System. */
    val themePalette: com.twinlore.app.data.ThemePalette? = null,
    val smartFormatting: Boolean = true,
    /** The app's own status cluster mode, which the top bar follows. */
    val statusBarMode: StatusBarMode = StatusBarMode.STANDARD,
    /**
     * True until the store says otherwise, so the intro never flashes on a
     * panel that has already seen it.
     */
    val introSeen: Boolean = true,
    /** Keeps the top bar on screen when the rest of the chrome collapses. */
    val pinned: Boolean = false,
    /**
     * The meters, or null when the status cluster is not in Performance mode.
     * Null rather than an empty snapshot: an empty one draws five dashes, which
     * reads as five broken readouts rather than as a cluster that is off.
     */
    val metrics: SystemMetricsSnapshot? = null,
    val awards: FoldAwards = FoldAwards(),
    val awardsLoading: Boolean = false,
    val awardsRefreshing: Boolean = false,
    /** The achievement whose comments have taken the surface, or null. */
    val commentsFor: RaAchievementDisplay? = null,
    val comments: List<FoldComment> = emptyList(),
    val commentsLoading: Boolean = false,
    val awardTypeFilter: RaAchievementTypeFilter = RaAchievementTypeFilter.ALL,
    val awardsLockedOnly: Boolean = false,
    /** The awards search, title or description. Session state; a new vault clears it. */
    val awardSearch: String = "",
    val awardSortField: RaAchievementSortField = RaAchievementSortField.UNLOCK_TIME,
    val awardSortDirection: SortDirection = RaAchievementSortField.UNLOCK_TIME.defaultDirection,
    /**
     * The sort for a Steam list, whose fields are Steam's own. Session state
     * rather than a stored setting: kept apart from [awardSortField] so a
     * Steam game does not overwrite the choice made on a RetroAchievements one.
     */
    val steamAwardSortField: com.twinlore.app.domain.SteamAchievementSortField =
        com.twinlore.app.domain.SteamAchievementSortField.UNLOCK_TIME,
    val steamAwardSortDirection: SortDirection =
        com.twinlore.app.domain.SteamAchievementSortField.UNLOCK_TIME.defaultDirection,
    /**
     * Where the list was left, restored once per panel rather than per frame,
     * and null until the store has answered.
     *
     * Null rather than a pair of zeroes: the list restores its position on the
     * first frame that has rows, and a zero standing in for "not loaded yet"
     * is indistinguishable from a zero meaning the top. The list would mark
     * itself restored against the placeholder and then ignore the real
     * position when it arrived a frame later.
     */
    val awardScroll: Pair<Int, Int>? = null,
    /** A screenshot has been asked for and has not landed yet. */
    val capturing: Boolean = false,
    /**
     * Where the shown image is panned and zoomed to, restored from the app's
     * own map view state so the panel opens on the part of it you were last
     * looking at, in either surface.
     */
    val imageView: FoldImageView? = null,
) {
    val currentImage: FoldImage? get() = images.getOrNull(imageIndex)

    /**
     * What the top bar names. The guide's title everywhere except the image
     * surface, where the guide is not what you are looking at - and the picture
     * has no caption of its own, so the bar is the only place its name can go.
     */
    val barTitle: String
        get() = when (surface) {
            FoldPanelSurface.IMAGE -> currentImage?.title?.takeIf { it.isNotBlank() }
                ?.let { if (images.size > 1) "$it  ${imageIndex + 1}/${images.size}" else it }
                ?: title
            // The thread's own achievement, while it owns the surface: the
            // comments are about that one, not about the guide behind them.
            FoldPanelSurface.AWARDS -> commentsFor?.title
                ?: awards.gameTitle.takeIf { it.isNotBlank() }
                ?: title
            else -> title
        }
    val hasPreviousImage: Boolean get() = imageIndex > 0
    val hasNextImage: Boolean get() = imageIndex < images.lastIndex
}
