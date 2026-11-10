package com.twinlore.app.data.scraping

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Utility for sanitizing game names before ScreenScraper API calls
 */
@Singleton
class GameNameSanitizer @Inject constructor() {
    companion object {
        private const val TAG = "GameNameSanitizer"
        
        // Common file extensions to remove
        private val FILE_EXTENSIONS = listOf(
            ".zip", ".7z", ".rar", ".iso", ".bin", ".cue", ".chd",
            ".nes", ".snes", ".smc", ".gba", ".gbc", ".gb", ".n64",
            ".z64", ".gcm", ".md", ".gen", ".psx", ".ps1", ".ps2"
        )
        
        // Region tags to remove (includes ES-DE-style language/region tags)
        private val REGION_TAGS = listOf(
            "(USA)", "(Europe)", "(Japan)", "(World)", "(EU)", "(US)", "(JP)",
            "(En)", "(En,Fr,De,Es,It)", "(En,Fr,De)", "(En,Fr)", "(Fr)", "(De)", "(Es)", "(It)",
            "[!]", "[U]", "[E]", "[J]", "[W]", "[PAL]", "[NTSC]", "[NTSC-U]", "[NTSC-J]"
        )
    }
    
    /**
     * Sanitize game name for ScreenScraper API
     * 
     * Rules:
     * 1. Remove file extensions
     * 2. Remove region tags
     * 3. Replace underscores with spaces, " - " title separators with ": "
     * 4. Normalize casing and whitespace
     */
    fun sanitize(gameName: String): String {
        var sanitized = gameName.trim()
        
        // Remove file extensions (case-insensitive)
        FILE_EXTENSIONS.forEach { ext ->
            if (sanitized.endsWith(ext, ignoreCase = true)) {
                sanitized = sanitized.substring(0, sanitized.length - ext.length)
            }
        }
        
        // Strip ALL parenthetical and bracketed content (generalizes the REGION_TAGS list
        // so non-listed metadata like (Disc 1), [Hack], (Rev 2) also drops out). This is
        // the contract used by the local-media-bind matcher.
        sanitized = sanitized.replace(Regex("\\([^)]*\\)"), "")
        sanitized = sanitized.replace(Regex("\\[[^\\]]*\\]"), "")

        // Remove region tags (case-insensitive)
        REGION_TAGS.forEach { tag ->
            // Escape special regex characters in the tag (parentheses, brackets, etc.)
            val escapedTag = Regex.escape(tag)
            val regex = Regex(escapedTag, RegexOption.IGNORE_CASE)
            sanitized = regex.replace(sanitized, "")
        }
        
        // Replace underscores with spaces
        sanitized = sanitized.replace("_", " ")
        // Replace " - " title separators with ": " (common convention for game subtitles)
        sanitized = sanitized.replace(" - ", ": ")
        
        // Normalize whitespace (trim, collapse multiple spaces)
        sanitized = sanitized.trim()
            .replace(Regex("\\s+"), " ")
        
        // Normalize casing (title case preferred, but keep original if it looks intentional)
        // For now, just ensure first letter is uppercase if it was lowercase
        if (sanitized.isNotEmpty() && sanitized[0].isLowerCase()) {
            sanitized = sanitized[0].uppercaseChar() + sanitized.substring(1)
        }
        
        Log.d(TAG, "Sanitized '$gameName' -> '$sanitized'")
        return sanitized
    }

    /**
     * Normalize a game name for comparison purposes (not display).
     * Strips all punctuation differences so "Castlevania: Circle of the Moon"
     * and "Castlevania - Circle of the Moon" produce the same normalized form.
     */
    fun normalize(gameName: String): String {
        // Diacritic folding via NFD: "Pokémon" → "Pokemon", "Persona Q" stays
        // "Persona Q". Keeps diacritic-spelled names matching their ASCII
        // counterparts on disk.
        val folded = java.text.Normalizer.normalize(sanitize(gameName), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        return folded
            .lowercase()
            .replace(Regex("[:\\-–—]"), " ")
            .replace(Regex("[^a-z0-9 ]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}

