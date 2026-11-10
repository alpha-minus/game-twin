package com.twinlore.app.data.esde

import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** One `<game>` entry from an ES-DE gamelist.xml — only the fields the Play Stats screen needs. */
data class GamelistEntry(
    val system: String,
    /** Path as written in the gamelist (usually `./Game.ext`). */
    val romPath: String,
    val name: String?,
    val favorite: Boolean,
    /** ES-DE timestamp (`20260730T105702`) — lexicographic order IS chronological order. */
    val lastPlayed: String?,
    val playCount: Int,
    /** Scraped game description, if present. */
    val description: String? = null,
    /** Total play time in seconds — Batocera-style `<gametime>`/`<playtime>`, when present. */
    val playTimeSeconds: Long? = null,
    /** Scraped genre (possibly combined, e.g. "Action / Platformer"), if present. */
    val genre: String? = null,
    /** Release year from the scraped `<releasedate>` stamp ("19940318T000000"), if plausible. */
    val releaseYear: Int? = null,
)

/**
 * Reads ES-DE's `gamelists/<system>/gamelist.xml` files (where ES-DE records favorites,
 * last-played timestamps and play counts). Regex-based like es-de-companion — the gamelist
 * schema is flat and stable, and this keeps parsing pure/unit-testable. Parsed files are cached
 * by (path, lastModified) so browsing systems doesn't re-read unchanged XML.
 */
@Singleton
class ESDEGamelistStore @Inject constructor() {

    private val cache = ConcurrentHashMap<String, Pair<Long, List<GamelistEntry>>>()

    /** Entries for one system, or empty if it has no gamelist. */
    fun entriesFor(fsRoot: String, systemShort: String): List<GamelistEntry> {
        val file = File(fsRoot, "gamelists/${systemShort.lowercase()}/gamelist.xml")
        if (!file.isFile) return emptyList()
        val stamp = file.lastModified()
        cache[file.path]?.let { (cachedStamp, entries) -> if (cachedStamp == stamp) return entries }
        val entries = try {
            parse(file.readText(), systemShort.lowercase())
        } catch (_: Exception) {
            emptyList()
        }
        cache[file.path] = stamp to entries
        return entries
    }

    /** Entries across every system that has a gamelist (for the auto collections). */
    fun allEntries(fsRoot: String): List<GamelistEntry> {
        val gamelists = File(fsRoot, "gamelists")
        val systems = gamelists.listFiles { f -> f.isDirectory }?.map { it.name } ?: return emptyList()
        return systems.flatMap { entriesFor(fsRoot, it) }
    }

    companion object {
        private val GAME_BLOCK = Regex("<game[\\s>].*?</game>", RegexOption.DOT_MATCHES_ALL)

        /**
         * Compiled-once pattern per tag name. Building the Regex inside [tag] meant compiling
         * one for every field of every game — ten thousand games cost ~100k pattern
         * compilations, which is what made opening the play-stats screen (it reads EVERY
         * system's gamelist at once) spike the CPU for a second.
         */
        private val TAG_PATTERNS = ConcurrentHashMap<String, Regex>()

        private fun tagPattern(tag: String): Regex = TAG_PATTERNS.getOrPut(tag) {
            Regex("<$tag>(.*?)</$tag>", RegexOption.DOT_MATCHES_ALL)
        }

        private fun tag(block: String, tag: String): String? =
            tagPattern(tag)
                .find(block)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }

        private fun decode(s: String): String = s
            .replace("&lt;", "<").replace("&gt;", ">")
            .replace("&quot;", "\"").replace("&apos;", "'")
            .replace("&amp;", "&")

        /** Pure: parse a gamelist.xml document into entries. */
        fun parse(xml: String, system: String): List<GamelistEntry> =
            GAME_BLOCK.findAll(xml).mapNotNull { m ->
                val block = m.value
                val path = tag(block, "path") ?: return@mapNotNull null
                GamelistEntry(
                    system = system,
                    romPath = decode(path),
                    name = tag(block, "name")?.let(::decode),
                    favorite = tag(block, "favorite")?.equals("true", ignoreCase = true) == true,
                    lastPlayed = tag(block, "lastplayed"),
                    playCount = tag(block, "playcount")?.toIntOrNull() ?: 0,
                    description = tag(block, "desc")?.let(::decode),
                    playTimeSeconds = (tag(block, "gametime") ?: tag(block, "playtime"))?.toLongOrNull(),
                    genre = tag(block, "genre")?.let(::decode),
                    releaseYear = tag(block, "releasedate")?.take(4)?.toIntOrNull()
                        ?.takeIf { it in 1950..2100 },
                )
            }.toList()
    }
}
