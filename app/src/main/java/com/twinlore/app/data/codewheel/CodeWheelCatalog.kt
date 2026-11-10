package com.twinlore.app.data.codewheel

import android.content.Context
import com.twinlore.app.data.canonicalMatchTokens
import com.twinlore.app.data.numberSignature
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory directory of code-wheel archives loaded once from a bundled JSON asset.
 * Fuzzy-matches a vault game name to the single best [CodeWheelEntry], or null.
 *
 * The [Context] is nullable so pure-JVM unit tests can construct it without an
 * Android runtime (pass null; [bestMatch] with an explicit list still works).
 */
@Singleton
class CodeWheelCatalog @Inject constructor(
    @ApplicationContext private val appContext: Context?,
) {
    private val entries: List<CodeWheelEntry> by lazy {
        val ctx = appContext ?: return@lazy emptyList()
        val json = ctx.assets.open(ASSET_PATH).bufferedReader().use { it.readText() }
        parse(json)
    }

    fun bestMatch(gameName: String): CodeWheelEntry? = bestMatch(entries, gameName)

    internal fun bestMatch(entries: List<CodeWheelEntry>, gameName: String): CodeWheelEntry? {
        if (gameName.isBlank()) return null
        val inputTokenList = canonicalMatchTokens(gameName)
        val inputTokens = inputTokenList.toSet()
        if (inputTokens.isEmpty()) return null
        val inputNumbers = numberSignature(inputTokenList)
        val normInput = inputTokenList.joinToString(" ")

        data class Scored(
            val entry: CodeWheelEntry,
            val score: Double,
            val isExact: Boolean,
            val isPrefix: Boolean,
            val titleLen: Int,
        )

        val scored = entries.mapNotNull { e ->
            val titleTokenList = canonicalMatchTokens(e.title)
            val titleTokens = titleTokenList.toSet()
            // Sequel guard: the set of numbers must match exactly, so a
            // base game never matches a numbered sequel and "2" never
            // matches "3" (Roman/Arabic already unified by canonicalMatchTokens).
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

    internal fun parse(json: String): List<CodeWheelEntry> {
        val arr = JSONArray(json)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            CodeWheelEntry(o.getString("title"), o.getString("zipFilename"))
        }
    }

    companion object {
        const val ASSET_PATH = "code_wheels.json"
        const val MATCH_THRESHOLD = 0.6
    }
}
