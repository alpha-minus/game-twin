package com.twinlore.app.data.util

import java.util.regex.Pattern

/**
 * Normalizes game titles for matching: lowercase, remove punctuation,
 * remove parentheses content, remove region markers, trim.
 */
object TitleNormalizer {

    private val regionPattern = Pattern.compile(
        "\\s*\\(\\s*(USA|Europe|Japan|World|EU|JP|US|UK|Korea|Asia|Brazil|Australia)\\s*\\)\\s*",
        Pattern.CASE_INSENSITIVE
    )

    /**
     * Normalize a title for similarity comparison.
     * - Lowercase
     * - Remove punctuation (replace with space, then collapse spaces)
     * - Remove parentheses and their content
     * - Remove region markers like (USA), (Europe)
     * - Trim and collapse multiple spaces
     */
    fun normalize(title: String): String {
        if (title.isBlank()) return ""
        var s = title.trim().lowercase()
        // Remove region markers first
        s = regionPattern.matcher(s).replaceAll(" ")
        // Remove any remaining parenthesized content
        s = s.replace(Regex("\\([^)]*\\)"), " ")
        // Remove punctuation (keep alphanumeric and spaces)
        s = s.replace(Regex("[^a-z0-9\\s]"), " ")
        // Collapse spaces and trim
        return s.replace(Regex("\\s+"), " ").trim()
    }
}
