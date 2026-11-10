package com.twinlore.app.data.strategywiki

import org.junit.Ignore
import org.junit.Test
import java.io.File

/**
 * Regenerator for `app/src/main/assets/strategywiki_catalog.json`. NOT a CI
 * test; `@Ignore`d by default, run manually to refresh the bundled directory.
 *
 * To refresh:
 *   1. Re-run the MediaWiki API category crawl to refresh
 *      `app/catalog-sources/strategywiki/stage*-*.json` (git-ignored, stays local).
 *   2. Comment out the @Ignore below.
 *   3. ./gradlew :app:testDebugUnitTest --tests "*StrategyWikiCatalogRegeneratorTest*"
 *   4. Verify `app/src/main/assets/strategywiki_catalog.json` updated.
 *   5. Re-add @Ignore and commit the JSON (not the captures).
 */
@Ignore("Regenerator: un-ignore to refresh the catalog, then re-ignore before commit")
class StrategyWikiCatalogRegeneratorTest {

    @Test fun regenerate() {
        val dir = File("catalog-sources/strategywiki")
        require(dir.exists()) { "Missing fixture dir: $dir" }

        // Filename convention: stage<N>-<request>.json
        val stagePattern = Regex("""stage(\d)-\d+\.json""")
        val all = LinkedHashMap<String, StrategyWikiEntry>()
        dir.listFiles { f -> stagePattern.matches(f.name) }
            ?.sortedBy { it.name }
            ?.forEach { f ->
                val stage = stagePattern.matchEntire(f.name)!!.groupValues[1].toInt()
                // Stage 2 ("Maturing") fixtures are still captured and kept, so a later change of
                // mind needs no re-crawl, but they are not bundled: a half-written walkthrough is
                // not something the vault should offer. See StrategyWikiCatalog.MIN_STAGE, which
                // also filters them on load.
                if (stage < StrategyWikiCatalog.MIN_STAGE) return@forEach
                for (e in StrategyWikiDirectoryParser.extractEntries(f.readText(), stage)) {
                    all.putIfAbsent(e.pagePath, e)   // first stage seen wins; pages are unique
                }
            }

        val sorted = all.values.sortedWith(compareBy({ it.title.lowercase() }, { it.title }))
        val sb = StringBuilder("[\n")
        sorted.forEachIndexed { i, e ->
            sb.append("""  {"t":${escapeJson(e.title)},"a":${escapeJson(e.pagePath)},"s":${e.stage}}""")
            if (i < sorted.size - 1) sb.append(",")
            sb.append("\n")
        }
        sb.append("]\n")

        val out = File("src/main/assets/strategywiki_catalog.json")
        out.parentFile.mkdirs()
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
