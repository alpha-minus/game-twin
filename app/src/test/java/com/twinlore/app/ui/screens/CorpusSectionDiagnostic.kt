package com.twinlore.app.ui.screens

import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Diagnostic (not a regression test): runs detectTextSections over a local
 * corpus of real text guides and prints per-guide section counts + titles.
 * Skips silently when the corpus directory is absent (e.g. on CI).
 *
 * Point CORPUS_DIR at a directory of .txt guide dumps to use it.
 */
class CorpusSectionDiagnostic {
    companion object {
        val CORPUS_DIR: String = System.getenv("GUIDE_CORPUS_DIR") ?: ""
    }

    @Test
    fun `summarize detection over big corpus`() {
        val dir = File("$CORPUS_DIR/bigcorpus")
        assumeTrue(dir.isDirectory)
        val files = dir.listFiles { f -> f.name.endsWith(".txt") }?.sortedBy { it.name } ?: emptyList()
        assumeTrue(files.isNotEmpty())
        var tocGuided = 0
        var zero = 0
        val counts = mutableListOf<Int>()
        for (f in files) {
            val lines = f.readText().lines()
            val toc = detectGuideTocBlock(lines)
            val sections = detectTextSections(lines)
            val tocUsed = toc != null &&
                matchTocEntriesToBody(lines, toc.entries, toc.endLine).size >= 4
            if (tocUsed) tocGuided++
            if (sections.isEmpty()) zero++
            counts.add(sections.size)
            println("SUMMARY ${f.name} lines=${lines.size} sections=${sections.size} toc=${toc?.entries?.size ?: 0} tocUsed=$tocUsed")
        }
        println("TOTAL guides=${files.size} tocGuided=$tocGuided zeroSections=$zero " +
            "median=${counts.sorted()[counts.size / 2]} max=${counts.max()}")
    }

    @Test
    fun `print detected sections per corpus guide`() {
        val dir = File(CORPUS_DIR)
        assumeTrue(dir.isDirectory)
        val files = dir.listFiles { f -> f.name.endsWith(".txt") && f.length() > 1000 }
            ?.sortedBy { it.name } ?: emptyList()
        assumeTrue(files.isNotEmpty())
        for (f in files) {
            val lines = f.readText().lines()
            val sections = detectTextSections(lines)
            println("==== ${f.name} (${lines.size} lines) -> ${sections.size} sections")
            for (s in sections) {
                println("  L${s.startLine}: ${s.title}")
            }
        }
    }
}
