package com.twinlore.app.data.esde

import android.net.Uri

/** ES-DE `downloaded_media` subfolders mapped to semantic display slots. */
enum class MediaSlot(val esdeFolder: String) {
    HERO("fanart"),
    LOGO("marquees"),
    SCREENSHOT("screenshots"),
    TITLE("titlescreens"),
    BOX_FRONT("covers"),
    BOX_3D("3dboxes"),
    MIX("miximages"),

    /** The cartridge, disc or box the game shipped on. */
    PHYSICAL_MEDIA("physicalmedia"),
}

/**
 * All artwork resolved for a game. Any slot may be null when that media was not scraped.
 * Display templates compose these slots and degrade per-slot when one is missing.
 */
data class GameMedia(
    val hero: Uri? = null,
    val logo: Uri? = null,
    val screenshot: Uri? = null,
    val titleScreen: Uri? = null,
    val boxFront: Uri? = null,
    val box3d: Uri? = null,
    val mix: Uri? = null,
    val physicalMedia: Uri? = null,
    /** ES-DE preview video (downloaded_media/<system>/videos/<rom>.<ext>), if scraped. */
    val video: Uri? = null,
)
