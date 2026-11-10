package com.twinlore.app.data.steam

import android.content.Context
import com.twinlore.app.data.canonicalMatchTokens
import com.twinlore.app.data.numberSignature
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory directory of curated Steam Community walkthrough guides, loaded once from a bundled JSON asset. Fuzzy-matches a
 * vault game name to the single best [SteamGuideEntry], or null.
 *
 * Mirrors [com.twinlore.app.data.gamefaqs.GameFaqsCatalog] exactly — same
 * canonical tokenization, sequel-number guard, threshold and tiebreaks.
 */
@Singleton
class SteamGuideCatalog @Inject constructor(
    @ApplicationContext private val appContext: Context,
) {
    private val entries: List<SteamGuideEntry> by lazy {
        val json = appContext.assets.open(ASSET_PATH).bufferedReader().use { it.readText() }
        parse(json)
    }

    fun match(gameName: String): SteamGuideEntry? = bestMatch(entries, gameName)

    companion object {
        const val ASSET_PATH = "steam_guides_catalog.json"
        const val MATCH_THRESHOLD = 0.6

        /** Test-only factory that bypasses the assets pipeline. */
        fun fromJson(json: String): SteamGuideCatalogTesting =
            SteamGuideCatalogTesting(parse(json))

        internal fun parse(json: String): List<SteamGuideEntry> {
            val arr = JSONArray(json)
            val out = ArrayList<SteamGuideEntry>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out += SteamGuideEntry(
                    title = o.getString("title"),
                    guideId = o.getString("guideId"),
                    appId = if (o.has("appId")) o.getLong("appId") else null,
                )
            }
            return out
        }

        internal fun bestMatch(entries: List<SteamGuideEntry>, gameName: String): SteamGuideEntry? {
            if (gameName.isBlank()) return null
            val inputTokenList = canonicalMatchTokens(gameName)
            val inputTokens = inputTokenList.toSet()
            if (inputTokens.isEmpty()) return null
            val inputNumbers = numberSignature(inputTokenList)
            val normInput = inputTokenList.joinToString(" ")

            data class Scored(
                val entry: SteamGuideEntry,
                val score: Double,
                val isExact: Boolean,
                val isPrefix: Boolean,
                val titleLen: Int,
            )

            val scored = entries.mapNotNull { e ->
                val titleTokenList = canonicalMatchTokens(e.title)
                val titleTokens = titleTokenList.toSet()
                // Sequel guard: numbers must match exactly so a base game never
                // matches a numbered sequel and "2" never matches "3".
                if (numberSignature(titleTokenList) != inputNumbers) return@mapNotNull null
                val union = inputTokens.union(titleTokens).size
                if (union == 0) return@mapNotNull null
                val score = inputTokens.intersect(titleTokens).size.toDouble() / union
                if (score < MATCH_THRESHOLD) return@mapNotNull null
                val normTitle = titleTokenList.joinToString(" ")
                Scored(e, score, normTitle == normInput, normTitle.startsWith(normInput), normTitle.length)
            }

            return scored
                .sortedWith(
                    compareByDescending<Scored> { it.isExact }
                        .thenByDescending { it.score }
                        .thenByDescending { it.isPrefix }
                        .thenBy { it.titleLen }
                        .thenBy { it.entry.title }
                )
                .firstOrNull()
                ?.entry
        }
    }
}

/**
 * Test-only wrapper exposing the matcher without an Android Context.
 * Kept in this file so it can call the internal bestMatch helper.
 */
class SteamGuideCatalogTesting internal constructor(private val entries: List<SteamGuideEntry>) {
    fun match(gameName: String): SteamGuideEntry? = SteamGuideCatalog.bestMatch(entries, gameName)
}
