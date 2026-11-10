package com.twinlore.app.data.gamefaqs

import com.twinlore.app.data.canonicalMatchTokens
import org.junit.Ignore
import org.junit.Test
import java.io.File

/**
 * Regenerator for `app/src/main/assets/gamefaqs_catalog.json`. NOT a CI test;
 * `@Ignore`d by default, run manually to refresh the bundled directory.
 *
 * Sources, all under `app/catalog-sources/gamefaqs/`. Page captures are
 * git-ignored and stay local; only the hand-curated `supplemental.html` is
 * committed. The test fixtures under `src/test/resources/gamefaqs` are dummy
 * pages and must never feed this regenerator.
 *   1. the top-level dir (mht/html): Contributor Central "FAQ of the Month"
 *      snapshot(s) plus `supplemental.html`.
 *   2. the `community` subdir (mht/html): community contributions pages
 *      (wanted guide types only, HTML preferred per game).
 *
 * When a game appears in more than one source, the highest-priority entry wins
 * (ties keep the first offered):
 *   supplemental > community HTML > FOTM > community text.
 * The FOTM page doesn't expose a guide's format, so a known-HTML community guide
 * is preferred over a FOTM entry; supplemental stays on top so deliberate
 * curation (e.g. the Dragon Quest I/II split) is never overridden.
 *
 * To refresh:
 *   1. Save the pages (desktop Chrome, Save Page As > Webpage, Single File)
 *      into the dirs above.
 *   2. Comment out the @Ignore below.
 *   3. ./gradlew :app:testDebugUnitTest --tests "*GameFaqsCatalogRegeneratorTest*"
 *   4. Verify `app/src/main/assets/gamefaqs_catalog.json` updated.
 *   5. Re-add @Ignore and commit the JSON (not the captures).
 */
@Ignore("Regenerator: un-ignore to refresh the catalog, then re-ignore before commit")
class GameFaqsCatalogRegeneratorTest {

    private val PRI_COMMUNITY_TEXT = 0
    private val PRI_FOTM = 1
    private val PRI_COMMUNITY_HTML = 2
    private val PRI_SUPPL = 3

    @Test fun regenerate() {
        val dir = File("catalog-sources/gamefaqs")
        require(dir.exists()) { "Missing capture dir: $dir" }

        // Per-game best pick by priority (higher wins; ties keep the first
        // offered). Format-known HTML community guides outrank FOTM entries
        // (whose format the FOTM page doesn't expose); hand-curated supplemental
        // entries stay on top so e.g. the Dragon Quest I/II split survives.
        val best = LinkedHashMap<String, Pair<GameFaqsEntry, Int>>()
        fun offer(entry: GameFaqsEntry, priority: Int) {
            val key = canonicalMatchTokens(entry.title).joinToString(" ")
            if (key.isEmpty()) return
            val cur = best[key]
            if (cur == null || priority > cur.second) best[key] = entry to priority
        }

        // (1) "Our list": FOTM snapshot(s) (PRI_FOTM) + supplemental (PRI_SUPPL).
        dir.listFiles { f -> f.name.endsWith(".mht") || f.name.endsWith(".html") }
            ?.sortedBy { it.name }
            ?.forEach { f ->
                val pri = if (f.name == "supplemental.html") PRI_SUPPL else PRI_FOTM
                for (e in GameFaqsContributorParser.extractEntries(f.readText())) offer(e, pri)
            }

        // (2) Community contributions: wanted types only, HTML preferred per
        // game. An HTML guide outranks FOTM; a text guide stays below it.
        val communityDir = File(dir, "community")
        val communityGuides = communityDir.listFiles { f ->
            f.name.endsWith(".mht") || f.name.endsWith(".html")
        }?.sortedBy { it.name }?.flatMap {
            GameFaqsContributionsParser.extractGuides(it.readText())
        }.orEmpty()
        for (g in GameFaqsContributionsParser.selectGuides(communityGuides)) {
            offer(
                GameFaqsEntry(g.title, g.platform, g.faqPath),
                if (g.isHtml) PRI_COMMUNITY_HTML else PRI_COMMUNITY_TEXT,
            )
        }

        val sorted = best.values.map { it.first }
            .sortedWith(compareBy({ it.title.lowercase() }, { it.title }))
        val sb = StringBuilder("[\n")
        sorted.forEachIndexed { i, e ->
            sb.append(
                """  {"t":${escapeJson(e.title)},"p":${escapeJson(e.platform)},"u":${escapeJson(e.faqPath)}}""",
            )
            if (i < sorted.size - 1) sb.append(",")
            sb.append("\n")
        }
        sb.append("]\n")

        val out = File("src/main/assets/gamefaqs_catalog.json")
        out.parentFile?.mkdirs()
        out.writeText(sb.toString())
        println("Wrote ${sorted.size} entries to ${out.absolutePath}")
    }

    private fun escapeJson(s: String): String {
        val sb = StringBuilder().append('"')
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> if (c < ' ') sb.append("\\u%04x".format(c.code)) else sb.append(c)
            }
        }
        return sb.append('"').toString()
    }
}
