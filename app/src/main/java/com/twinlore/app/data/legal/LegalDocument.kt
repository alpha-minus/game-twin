package com.twinlore.app.data.legal

/**
 * A long-form legal text that ships in German and English.
 *
 * The prose lives in `assets/legal/` rather than in `strings.xml`: these documents are pages
 * long, change on their own schedule, and exist in two languages the user picks between at read
 * time rather than the two the device locale picks for them.
 */
enum class LegalDocument(
    val titleDe: String,
    val titleEn: String,
    private val assetDe: String,
    private val assetEn: String,
) {
    IMPRESSUM(
        "Impressum", "Impressum",
        "legal/impressum_de.txt", "legal/impressum_en.txt",
    ),
    PRIVACY(
        "Datenschutzerklärung", "Privacy Policy",
        "legal/privacy_de.txt", "legal/privacy_en.txt",
    ),
    TERMS(
        "Nutzungsbedingungen", "Terms of Use",
        "legal/terms_de.txt", "legal/terms_en.txt",
    ),
    ;

    fun assetPath(german: Boolean): String = if (german) assetDe else assetEn

    fun title(german: Boolean): String = if (german) titleDe else titleEn
}
