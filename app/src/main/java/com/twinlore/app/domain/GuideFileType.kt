package com.twinlore.app.domain

/**
 * Type of guide file (HTML, TXT, PDF, MHT, EPUB, LINK, GROUP, WHEEL, or HTMLZIP).
 * Used to route to the correct viewer and to show type badges in the guide picker.
 *
 * Loading behavior:
 * - **TXT, PDF, MHT, EPUB, WHEEL, HTMLZIP**: Always offline. No online/offline toggle, no reload on tab switch.
 * - **HTML**: Supports online/offline toggle; user can view saved offline copy or fetch live URL.
 *   NOTE: HTML save path is legacy — new saves use MHT. HTML loading retained for existing guides.
 * - **LINK**: URL shortcut (e.g. YouTube). Opens the URL in the browser tab instead of rendering offline.
 * - **GROUP**: Page-group bundle. Picker entry is the group; opens its own viewer instead of routing to GuideScreen.
 */
enum class GuideFileType(val extension: String) {
    HTML("html"),
    TXT("txt"),
    PDF("pdf"),
    MHT("mht"),
    EPUB("epub"),
    LINK("link"),
    GROUP("group"),
    WHEEL("wheel"),
    HTMLZIP("htmlzip");

    /** True for TXT, PDF, MHT, EPUB, GROUP, WHEEL, HTMLZIP: always offline, no online/offline toggle. */
    val isOfflineOnly: Boolean get() = this != HTML && this != LINK

    /**
     * Short uppercase badge used in the guide picker rows and the vault
     * top-bar doc-switcher dropdown. MHT renders as "WEB" (user-facing
     * name for saved web pages) and GROUP as "SET" (user-facing name for
     * page-group bundles); everything else mirrors the extension.
     */
    val displayLabel: String get() = when (this) {
        HTML -> "HTML"
        TXT -> "TXT"
        PDF -> "PDF"
        MHT -> "WEB"
        EPUB -> "EPUB"
        LINK -> "LINK"
        GROUP -> "SET"
        WHEEL -> "WHEEL"
        HTMLZIP -> "HTML"
    }

    companion object {
        fun fromFileName(name: String): GuideFileType = when {
            name.endsWith(".txt", ignoreCase = true) -> TXT
            name.endsWith(".pdf", ignoreCase = true) -> PDF
            name.endsWith(".mht", ignoreCase = true) -> MHT
            name.endsWith(".mhtml", ignoreCase = true) -> MHT
            name.endsWith(".epub", ignoreCase = true) -> EPUB
            else -> HTML
        }

        fun fromFileTypeColumn(value: String?): GuideFileType = when (value?.lowercase()) {
            "txt" -> TXT
            "pdf" -> PDF
            "mht" -> MHT
            "epub" -> EPUB
            "link" -> LINK
            "group" -> GROUP
            "wheel" -> WHEEL
            "htmlzip" -> HTMLZIP
            else -> HTML
        }
    }
}
