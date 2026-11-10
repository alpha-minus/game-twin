package com.twinlore.app.data

/**
 * Shared game-name canonicalization used by the fuzzy matchers
 * ([com.twinlore.app.data.strategywiki.StrategyWikiCatalog] and
 * [com.twinlore.app.data.vgmaps.VgmapsCatalog]).
 *
 * On top of [normalizeForMatching] it does two things that matter for
 * distinguishing sequels:
 *   1. strips parenthetical region / revision tags ("God of War 2 (USA)"),
 *   2. converts standalone Roman numerals to Arabic ("God of War II" → "…2"),
 * so "2" and "II" compare equal while "2" and "3" stay distinct. Callers pair
 * this with [numberSignature] to reject matches whose sequel numbers differ.
 */

private val PARENS_TAG = Regex("""\([^)]*\)""")

// Tokens made only of the Roman-numeral letters i / v / x (covers sequel
// numbers 1..39 — "v", "vi", "xiii", …). Restricting to i/v/x keeps real
// words containing l/c/d/m (e.g. "mix", "civil", "mild") from being mistaken
// for numerals.
private val ROMAN_IVX = Regex("^[ivx]+$")

/**
 * Canonical match tokens for [name]: region tags stripped, normalized, and
 * standalone Roman numerals converted to Arabic digit strings.
 */
internal fun canonicalMatchTokens(name: String): List<String> {
    val cleaned = PARENS_TAG.replace(name, " ")
    return normalizeForMatching(cleaned)
        .split(' ')
        .filter { it.isNotEmpty() }
        .map { tok -> romanToArabic(tok)?.toString() ?: tok }
}

/** The set of sequel/ordinal numbers in a canonical token list. */
internal fun numberSignature(tokens: List<String>): Set<Int> =
    tokens.mapNotNull { it.toIntOrNull() }.toSet()

/** Parse a strict i/v/x Roman numeral in 1..39, else null. */
private fun romanToArabic(s: String): Int? {
    if (!ROMAN_IVX.matches(s)) return null
    val value = mapOf('i' to 1, 'v' to 5, 'x' to 10)
    var total = 0
    var prev = 0
    for (c in s.reversed()) {
        val v = value[c] ?: return null
        if (v < prev) total -= v else { total += v; prev = v }
    }
    // Reject non-canonical forms ("iiii", "vv", "ixi") via round-trip.
    return if (total in 1..39 && toRoman(total) == s) total else null
}

private fun toRoman(n: Int): String {
    val tens = arrayOf("", "x", "xx", "xxx")
    val ones = arrayOf("", "i", "ii", "iii", "iv", "v", "vi", "vii", "viii", "ix")
    return tens[n / 10] + ones[n % 10]
}
