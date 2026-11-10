package com.twinlore.app.data.vgmaps

/**
 * One entry in the vgmaps.com directory: a game title on a specific system.
 * The same game can appear under multiple systems (e.g. Castlevania: SOTN on
 * PSX and Saturn) and each is its own [VgmapsEntry].
 */
data class VgmapsEntry(
    /** Display title (link text from the directory page, trimmed). */
    val title: String,
    /** System code, the segment between Atlas/ and /index.htm. */
    val system: String,
    /** Anchor fragment (the part after `#` in the directory link). */
    val anchor: String,
) {
    /** Full URL to the per-system maps page anchored at this game. */
    val pageUrl: String get() = "https://www.vgmaps.com/Atlas/$system/index.htm#$anchor"
}

/**
 * One credit on a vgmaps.com per-game page: the link text and, when available,
 * the contact email pulled from a &lt;a href="mailto:"&gt; element.
 */
data class VgmapsCredit(
    val name: String,
    val email: String?,
)

/**
 * Full result of scraping a per-game page: the maps to display in the picker
 * grid and the contributor credits to show as an attribution footer.
 */
data class VgmapsGameDetails(
    val maps: List<VgmapsMap>,
    val credits: List<VgmapsCredit>,
)

/**
 * One map image on a per-game vgmaps page.
 */
data class VgmapsMap(
    /**
     * Human-readable map name. Either the original `<a>` link text trimmed,
     * or, if that text is empty or a split-map indicator like `[1]` / `[2]`,
     * the URL basename with the extension stripped and CamelCase split.
     */
    val caption: String,
    /** Absolute https URL to the image asset. */
    val imageUrl: String,
)
