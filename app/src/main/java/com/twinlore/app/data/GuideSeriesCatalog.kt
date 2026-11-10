package com.twinlore.app.data

import java.text.Normalizer

/**
 * One walkthrough entry within a series catalog: the canonical title and the direct URL
 * to that title's walkthrough page on the series' chosen host. `mapUrl`, when present,
 * points to a dedicated maps page for the title on the same host (currently populated
 * only for Metroid entries — Omega Metroid has per-title /map/ pages).
 */
data class SeriesEntry(val title: String, val url: String, val mapUrl: String? = null)

/**
 * Series with a curated walkthrough catalog. `keyword` is the normalized substring we
 * look for in the vault game name to gate the Hub fallback (so "Zelda Romhack 9001"
 * still gets a Zelda Dungeon row even though no curated title matches); `hubUrl` is
 * the target for that Hub fallback — preferably a walkthroughs index page, else the
 * site homepage whose nav surfaces walkthroughs. Direct matches do NOT require the
 * keyword.
 *
 * Catalog refresh is manual — add new entries here when new mainline titles ship.
 */
enum class GuideSeries(
    val displayName: String,
    val keyword: String,
    val hubUrl: String,
    val entries: List<SeriesEntry>,
) {
    Metroid(
        displayName = "Omega Metroid",
        keyword = "metroid",
        // No dedicated /walkthroughs/ index on omegametroid.com; homepage exposes the dropdown.
        hubUrl = "https://omegametroid.com/",
        entries = listOf(
            SeriesEntry("Metroid Dread",
                "https://omegametroid.com/metroid-dread-walkthrough/",
                mapUrl = "https://omegametroid.com/metroid-dread-walkthrough/map/"),
            SeriesEntry("Metroid Fusion",
                "https://omegametroid.com/metroid-fusion-walkthrough/",
                mapUrl = "https://omegametroid.com/metroid-fusion-walkthrough/map"),
            SeriesEntry("Metroid II Return of Samus",
                "https://omegametroid.com/metroid-ii-return-of-samus-walkthrough/",
                // Map article uses a different slug (no "ii-", trailing "-map").
                mapUrl = "https://omegametroid.com/metroid-return-of-samus-walkthrough-map/"),
            SeriesEntry("Metroid Prime",
                "https://omegametroid.com/metroid-prime-walkthrough/",
                mapUrl = "https://omegametroid.com/metroid-prime-walkthrough/map/"),
            SeriesEntry("Metroid Prime 2 Echoes",
                "https://omegametroid.com/metroid-prime-2-echoes-walkthrough/",
                mapUrl = "https://omegametroid.com/metroid-prime-2-echoes-walkthrough/map/"),
            SeriesEntry("Metroid Prime 3 Corruption",
                "https://omegametroid.com/metroid-prime-3-corruption-walkthrough/",
                mapUrl = "https://omegametroid.com/metroid-prime-3-corruption-walkthrough/map/"),
            SeriesEntry("Metroid Prime 4 Beyond",
                "https://omegametroid.com/metroid-prime-4-beyond-walkthrough/",
                mapUrl = "https://omegametroid.com/metroid-prime-4-beyond-walkthrough/map/"),
            SeriesEntry("Metroid Prime Hunters",
                "https://omegametroid.com/metroid-prime-hunters-walkthrough/",
                // Map article omits the "-walkthrough" segment.
                mapUrl = "https://omegametroid.com/metroid-prime-hunters/map/"),
            SeriesEntry("Metroid Samus Returns",
                "https://omegametroid.com/metroid-samus-returns-walkthrough/",
                mapUrl = "https://omegametroid.com/metroid-samus-returns-walkthrough/map/"),
            SeriesEntry("Metroid Zero Mission",
                "https://omegametroid.com/metroid-zero-mission-walkthrough/",
                mapUrl = "https://omegametroid.com/metroid-zero-mission-walkthrough/map/"),
            SeriesEntry("Super Metroid",
                "https://omegametroid.com/super-metroid-walkthrough/",
                mapUrl = "https://omegametroid.com/super-metroid-walkthrough/map"),
        ),
    ),
    Zelda(
        displayName = "Zelda Dungeon",
        keyword = "zelda",
        // Cloudflare blocks server-side probes; homepage surfaces walkthroughs in nav.
        hubUrl = "https://www.zeldadungeon.net/",
        entries = listOf(
            SeriesEntry("A Link Between Worlds", "https://www.zeldadungeon.net/a-link-between-worlds-walkthrough/"),
            SeriesEntry("A Link to the Past", "https://www.zeldadungeon.net/a-link-to-the-past-walkthrough/"),
            SeriesEntry("Breath of the Wild", "https://www.zeldadungeon.net/breath-of-the-wild-walkthrough/"),
            SeriesEntry("Echoes of Wisdom", "https://www.zeldadungeon.net/echoes-of-wisdom-walkthrough-guide/"),
            SeriesEntry("Four Swords Adventures", "https://www.zeldadungeon.net/four-swords-adventures-walkthrough/"),
            SeriesEntry("Link the Faces of Evil", "https://www.zeldadungeon.net/link-the-faces-of-evil-walkthrough/"),
            SeriesEntry("Link's Awakening", "https://www.zeldadungeon.net/links-awakening-walkthrough/"),
            SeriesEntry("Majora's Mask", "https://www.zeldadungeon.net/majoras-mask-walkthrough/"),
            SeriesEntry("Ocarina of Time", "https://www.zeldadungeon.net/ocarina-of-time-walkthrough/"),
            SeriesEntry("Oracle of Ages", "https://www.zeldadungeon.net/oracle-of-ages-walkthrough/"),
            SeriesEntry("Oracle of Seasons", "https://www.zeldadungeon.net/oracle-of-seasons-walkthrough/"),
            SeriesEntry("Phantom Hourglass", "https://www.zeldadungeon.net/phantom-hourglass-walkthrough/"),
            SeriesEntry("Skyward Sword", "https://www.zeldadungeon.net/skyward-sword-walkthrough/"),
            SeriesEntry("Spirit Tracks", "https://www.zeldadungeon.net/spirit-tracks-walkthrough/"),
            SeriesEntry("Tears of the Kingdom", "https://www.zeldadungeon.net/tears-of-the-kingdom-walkthrough/"),
            SeriesEntry("The Adventure of Link", "https://www.zeldadungeon.net/the-adventure-of-link-walkthrough/"),
            SeriesEntry("The Legend of Zelda", "https://www.zeldadungeon.net/the-legend-of-zelda-walkthrough/"),
            SeriesEntry("The Minish Cap", "https://www.zeldadungeon.net/the-minish-cap-walkthrough/"),
            SeriesEntry("The Wind Waker", "https://www.zeldadungeon.net/the-wind-waker-walkthrough/"),
            SeriesEntry("Tri Force Heroes", "https://www.zeldadungeon.net/tri-force-heroes-walkthrough/"),
            SeriesEntry("Twilight Princess", "https://www.zeldadungeon.net/twilight-princess-walkthrough/"),
            SeriesEntry("Zelda the Wand of Gamelon", "https://www.zeldadungeon.net/zelda-the-wand-of-gamelon-walkthrough/"),
        ),
    ),
    Pokemon(
        displayName = "Bulbapedia",
        keyword = "pokemon",
        hubUrl = "https://bulbapedia.bulbagarden.net/wiki/Browse:Walkthroughs",
        entries = listOf(
            SeriesEntry("Pokemon Red and Blue", "https://bulbapedia.bulbagarden.net/wiki/Walkthrough:Pok%C3%A9mon_Red_and_Blue"),
            SeriesEntry("Pokemon Yellow", "https://bulbapedia.bulbagarden.net/wiki/Walkthrough:Pok%C3%A9mon_Yellow"),
            SeriesEntry("Pokemon Gold and Silver", "https://bulbapedia.bulbagarden.net/wiki/Walkthrough:Pok%C3%A9mon_Gold_and_Silver"),
            SeriesEntry("Pokemon Crystal", "https://bulbapedia.bulbagarden.net/wiki/Walkthrough:Pok%C3%A9mon_Crystal"),
            SeriesEntry("Pokemon Ruby and Sapphire", "https://bulbapedia.bulbagarden.net/wiki/Walkthrough:Pok%C3%A9mon_Ruby_and_Sapphire"),
            SeriesEntry("Pokemon Emerald", "https://bulbapedia.bulbagarden.net/wiki/Walkthrough:Pok%C3%A9mon_Emerald"),
            SeriesEntry("Pokemon FireRed and LeafGreen", "https://bulbapedia.bulbagarden.net/wiki/Walkthrough:Pok%C3%A9mon_FireRed_and_LeafGreen"),
            SeriesEntry("Pokemon Diamond and Pearl", "https://bulbapedia.bulbagarden.net/wiki/Walkthrough:Pok%C3%A9mon_Diamond_and_Pearl"),
            SeriesEntry("Pokemon Platinum", "https://bulbapedia.bulbagarden.net/wiki/Walkthrough:Pok%C3%A9mon_Platinum"),
            SeriesEntry("Pokemon HeartGold and SoulSilver", "https://bulbapedia.bulbagarden.net/wiki/Walkthrough:Pok%C3%A9mon_HeartGold_and_SoulSilver"),
            SeriesEntry("Pokemon Black and White", "https://bulbapedia.bulbagarden.net/wiki/Walkthrough:Pok%C3%A9mon_Black_and_White"),
            SeriesEntry("Pokemon Black 2 and White 2", "https://bulbapedia.bulbagarden.net/wiki/Walkthrough:Pok%C3%A9mon_Black_2_and_White_2"),
            SeriesEntry("Pokemon X and Y", "https://bulbapedia.bulbagarden.net/wiki/Walkthrough:Pok%C3%A9mon_X_and_Y"),
            SeriesEntry("Pokemon Omega Ruby and Alpha Sapphire", "https://bulbapedia.bulbagarden.net/wiki/Walkthrough:Pok%C3%A9mon_Omega_Ruby_and_Alpha_Sapphire"),
            SeriesEntry("Pokemon Sun and Moon", "https://bulbapedia.bulbagarden.net/wiki/Walkthrough:Pok%C3%A9mon_Sun_and_Moon"),
            SeriesEntry("Pokemon Ultra Sun and Ultra Moon", "https://bulbapedia.bulbagarden.net/wiki/Walkthrough:Pok%C3%A9mon_Ultra_Sun_and_Ultra_Moon"),
            SeriesEntry("Pokemon Let's Go Pikachu and Let's Go Eevee", "https://bulbapedia.bulbagarden.net/wiki/Walkthrough:Pok%C3%A9mon:_Let%27s_Go,_Pikachu!_and_Let%27s_Go,_Eevee!"),
            SeriesEntry("Pokemon Sword and Shield", "https://bulbapedia.bulbagarden.net/wiki/Walkthrough:Pok%C3%A9mon_Sword_and_Shield"),
            SeriesEntry("Pokemon Brilliant Diamond and Shining Pearl", "https://bulbapedia.bulbagarden.net/wiki/Walkthrough:Pok%C3%A9mon_Brilliant_Diamond_and_Shining_Pearl"),
            SeriesEntry("Pokemon Legends Arceus", "https://bulbapedia.bulbagarden.net/wiki/Walkthrough:Pok%C3%A9mon_Legends:_Arceus"),
            SeriesEntry("Pokemon Scarlet and Violet", "https://bulbapedia.bulbagarden.net/wiki/Walkthrough:Pok%C3%A9mon_Scarlet_and_Violet"),
            SeriesEntry("Pokemon Legends Z-A", "https://bulbapedia.bulbagarden.net/wiki/Walkthrough:Pok%C3%A9mon_Legends:_Z-A"),
        ),
    ),
    FireEmblem(
        displayName = "Fire Emblem WoD",
        // Two-word keyword: matched as a normalized substring (never as a single
        // token), so it gates the Hub fallback for any "Fire Emblem …" name while
        // "fire"/"emblem" drop out of Direct scoring as they appear in every entry
        // (the dynamic-frequency filter in matchEntryByTokens catches them).
        keyword = "fire emblem",
        // English index; surfaces every game's guide for the Hub fallback.
        hubUrl = "https://www.fireemblemwod.com/english.htm",
        // Each url is the game's English hub page (ENG_fe<N>.htm) — the "main
        // section" the user lands on. It carries the game's guide index and the
        // full walkthrough chapter list, so the fireemblemwod branch in
        // ToCDetector can recognize every chapter for "Save All Pages", and the
        // reader-mode filter (ReadingModeFilters) trims the site chrome. Titles
        // carry the distinctive tokens that disambiguate each RetroAchievements
        // title (e.g. FE1 "…Shadow Dragon and the Blade of Light" vs FE11
        // "Shadow Dragon").
        entries = listOf(
            SeriesEntry("Fire Emblem: Shadow Dragon and the Blade of Light", "https://www.fireemblemwod.com/ENG_fe1.htm"),
            SeriesEntry("Fire Emblem Gaiden", "https://www.fireemblemwod.com/ENG_fe2.htm"),
            SeriesEntry("Fire Emblem: Monshou no Nazo", "https://www.fireemblemwod.com/ENG_fe3.htm"),
            SeriesEntry("Fire Emblem: Seisen no Keifu", "https://www.fireemblemwod.com/ENG_fe4.htm"),
            SeriesEntry("Fire Emblem: Thracia 776", "https://www.fireemblemwod.com/ENG_fe5.htm"),
            SeriesEntry("Fire Emblem: Fuuin no Tsurugi", "https://www.fireemblemwod.com/ENG_fe6.htm"),
            SeriesEntry("Fire Emblem: The Blazing Blade", "https://www.fireemblemwod.com/ENG_fe7.htm"),
            SeriesEntry("Fire Emblem: The Sacred Stones", "https://www.fireemblemwod.com/ENG_fe8.htm"),
            SeriesEntry("Fire Emblem: Path of Radiance", "https://www.fireemblemwod.com/ENG_fe9.htm"),
            SeriesEntry("Fire Emblem: Radiant Dawn", "https://www.fireemblemwod.com/ENG_fe10.htm"),
            SeriesEntry("Fire Emblem: Shadow Dragon", "https://www.fireemblemwod.com/ENG_fe11.htm"),
            // FE12-FE15 were missing outright, so Awakening and Fates fell back to the Hub
            // index even though the site carries full walkthroughs for them.
            SeriesEntry("Fire Emblem: New Mystery of the Emblem", "https://www.fireemblemwod.com/ENG_fe12.htm"),
            SeriesEntry("Fire Emblem Awakening", "https://www.fireemblemwod.com/ENG_fe13.htm"),
            SeriesEntry("Fire Emblem Fates", "https://www.fireemblemwod.com/ENG_fe14.htm"),
            SeriesEntry("Fire Emblem Echoes: Shadows of Valentia", "https://www.fireemblemwod.com/ENG_fe15.htm"),

            // WESTERN TITLES for games catalogued under their Japanese names. The matcher scores
            // tokens against the entry title, so "Genealogy of the Holy War" scored nothing
            // against "Seisen no Keifu" even though they are the same game. Same URL, second
            // name — cheaper and more obvious than an alias field on SeriesEntry.
            SeriesEntry("Fire Emblem: Mystery of the Emblem", "https://www.fireemblemwod.com/ENG_fe3.htm"),
            // The bundled game list spells this "Monsho No Nazo" (no 'u'), which shares no token
            // with "Monshou".
            SeriesEntry("Fire Emblem: Monsho no Nazo", "https://www.fireemblemwod.com/ENG_fe3.htm"),
            SeriesEntry("Fire Emblem: Genealogy of the Holy War", "https://www.fireemblemwod.com/ENG_fe4.htm"),
            SeriesEntry("Fire Emblem: Binding Blade", "https://www.fireemblemwod.com/ENG_fe6.htm"),

            // Spin-offs the site covers and the game list carries. These do not match
            // FEWOD_HUB_PATH (/ENG_fe<N>.htm), so reading mode gives them the generic
            // host filter rather than the hub-specific one — the guide still opens and
            // still strips site chrome, it just keeps the page preamble.
            SeriesEntry("BS Fire Emblem: Akaneia Senkihen", "https://www.fireemblemwod.com/ENG_bsfe.htm"),
            // NO "Fire Emblem Warriors" entry, deliberately. The site has that guide, but the
            // one-distinctive-token title ("warriors") also swallows "Fire Emblem Warriors:
            // Three Hopes" — a different game the site does NOT cover — and sending a Three
            // Hopes player into the 2017 Warriors chapters is worse than the honest index.
            // Both fall back to the Hub instead. Re-add only alongside a matcher that demands
            // higher recall from short entry titles.
        ),
    ),
}

/**
 * Result of matching a vault name against the series catalog.
 *
 * - [Direct] — a curated title is contained in the normalized name. Series keyword
 *   is NOT required (so "Phantom Hourglass" alone matches the Zelda entry).
 * - [Hub]    — no curated title matched, but the series keyword IS present in the name;
 *   the series' [hubUrl] is the fallback target.
 * - null returned by [matchSeries] — no entry matched and no series keyword present.
 */
sealed class SeriesMatchResult {
    abstract val series: GuideSeries

    data class Direct(override val series: GuideSeries, val entry: SeriesEntry) : SeriesMatchResult()
    data class Hub(override val series: GuideSeries) : SeriesMatchResult()
}

/**
 * Match a vault game name against the curated catalog.
 *
 * Returns:
 * - `Direct(series, entry)` when distinctive game-name tokens overlap an entry's
 *   tokens. The previous substring approach failed when the entry was longer than
 *   the game name (e.g. vault "Pokemon HeartGold" against entry "Pokemon HeartGold
 *   and SoulSilver"). Token overlap with series-keyword filtering catches both:
 *
 *     1. Normalize both game name and entry title via [normalizeForMatching].
 *     2. Split each into whitespace tokens.
 *     3. Drop the series keyword from the game tokens (so just "Zelda" alone
 *        never produces a Direct match — it falls to Hub).
 *     4. Also drop any token that appears in >= 50% of this series' entries
 *        (catches things like "metroid" being non-distinctive within the Metroid
 *        catalog without hardcoding per-series stopword lists).
 *     5. If no distinctive game tokens remain, no Direct match.
 *     6. Score each entry by `|distinctiveGameTokens ∩ entryTokens|`.
 *     7. Pick the entry with the highest score; tiebreak by shorter title
 *        (so "Pokemon Diamond" picks "Pokemon Diamond and Pearl" over the longer
 *        "Pokemon Brilliant Diamond and Shining Pearl" when both score 1).
 *
 * - `Hub(series)` if no Direct match but the series keyword is present in the name.
 * - `null` if no entry matches and no series keyword is present.
 *
 * Series are tested in enum declaration order (Metroid, Zelda, Pokemon); first match wins.
 */
fun matchSeries(gameName: String): SeriesMatchResult? {
    if (gameName.isBlank()) return null
    val normalized = normalizeForMatching(gameName)
    if (normalized.isEmpty()) return null
    val gameTokens = normalized.split(' ').filter { it.isNotBlank() }.toSet()
    // Try series whose keyword appears in the game name first. Otherwise
    // declaration order. Without this, vault "Pokemon Sword" would land on
    // Zelda's "Skyward Sword" entry (matched on the bare "sword" token)
    // because the Zelda series is iterated before Pokemon.
    val orderedSeries = GuideSeries.values().sortedByDescending {
        if (normalized.contains(it.keyword)) 1 else 0
    }
    for (series in orderedSeries) {
        val keywordInName = normalized.contains(series.keyword)
        val direct = matchEntryByTokens(series, gameTokens, keywordInName)
        if (direct != null) return SeriesMatchResult.Direct(series, direct)
        if (keywordInName) return SeriesMatchResult.Hub(series)
    }
    return null
}

/**
 * Generic stopwords filtered from both game tokens and entry tokens before
 * scoring. Covers English filler (the, of, a, an, ...) and common version/
 * release suffix words on vault names ("Pokemon Yellow Version Special
 * Pikachu Edition" should match "Pokemon Yellow" despite the noise).
 */
private val MATCHER_STOPWORDS = setOf(
    // English filler.
    "the", "of", "a", "an", "in", "on", "at", "for", "to", "with", "by", "plus",
    // Version / release suffix words seen on real vault names.
    "version", "edition", "deluxe", "remastered", "remaster", "hd", "definitive",
    "complete", "ultimate", "enhanced", "directors", "cut", "anniversary",
)

/**
 * Minimum F1 score (harmonic mean of precision and recall over distinctive
 * tokens) for a Direct match. F1 naturally rejects sparse single-token
 * coincidences ("Final Fantasy X" matching "Pokemon X and Y" on bare "x")
 * without needing to enumerate every English filler word as a stopword.
 *
 * 0.45 lets through real but noisy vault names like "Pokemon Yellow Version
 * Special Pikachu Edition" (F1 ~0.50 against "Pokemon Yellow") while filtering
 * "pokemon fan project alpha" against ORAS (F1 ~0.29).
 */
private const val MIN_F1 = 0.45

/**
 * Token-overlap matcher with series-keyword + dynamic-frequency + stopword
 * filtering and a coverage-ratio floor. See [matchSeries] KDoc.
 */
private fun matchEntryByTokens(
    series: GuideSeries,
    gameTokens: Set<String>,
    keywordInName: Boolean,
): SeriesEntry? {
    val entryTokenMap = series.entries.associateWith {
        normalizeForMatching(it.title).split(' ').filter { tok -> tok.isNotBlank() }.toSet()
    }
    if (entryTokenMap.isEmpty()) return null

    // When the series keyword is absent from the game name, a single shared
    // token is too weak to anchor a Direct match: the keyword itself is dropped
    // from both sides (below), so "Super Metroid" reduces to just "super", which
    // any "Super <X>" game (e.g. "Super Mario Galaxy") would hit. Require >= 2
    // distinctive overlapping tokens in that case — a genuinely distinctive
    // multi-word title ("Phantom Hourglass") still matches without the keyword,
    // but a generic single-token coincidence does not. With the keyword present
    // a single distinctive token still suffices ("Pokemon Yellow" on "yellow").
    val minScore = if (keywordInName) 1 else 2

    // Tokens that appear in at least half of the series' entries are
    // non-distinctive within this series (catches "metroid" being noise inside
    // the Metroid catalog without hand-curated per-series lists).
    val halfThreshold = (entryTokenMap.size + 1) / 2
    val tokenCounts = entryTokenMap.values.flatten().groupingBy { it }.eachCount()
    val dynamicNondistinctive = tokenCounts.filterValues { it >= halfThreshold }.keys

    fun distinctive(tokens: Set<String>): Set<String> =
        tokens - series.keyword - dynamicNondistinctive - MATCHER_STOPWORDS

    val gameDistinctive = distinctive(gameTokens)
    if (gameDistinctive.isEmpty()) return null

    return entryTokenMap.entries
        .mapNotNull { (entry, tokens) ->
            val entryDistinctive = distinctive(tokens)
            if (entryDistinctive.isEmpty()) return@mapNotNull null
            val score = gameDistinctive.intersect(entryDistinctive).size
            if (score < minScore) return@mapNotNull null
            val precision = score.toDouble() / gameDistinctive.size
            val recall = score.toDouble() / entryDistinctive.size
            val f1 = 2 * precision * recall / (precision + recall)
            if (f1 < MIN_F1) return@mapNotNull null
            Triple(entry, score, normalizeForMatching(entry.title).length)
        }
        // Highest score wins; tied scores tiebreak by shorter entry title
        // (so "Pokemon Diamond" picks DP over BDSP when both score 1).
        // compareBy(score) ascending + maxWithOrNull returns largest score;
        // thenByDescending(length) for tied scores returns smallest length.
        .maxWithOrNull(
            compareBy<Triple<SeriesEntry, Int, Int>> { it.second }
                .thenByDescending { it.third }
        )
        ?.first
}

/**
 * Normalize a name for relaxed substring matching. Shared between the guide
 * series matcher (this file) and the VGMaps catalog matcher.
 *
 * 1. Strip Unicode combining marks (Pokémon to Pokemon)
 * 2. Lowercase
 * 3. Replace any run of non-alphanumeric ASCII with a single space
 * 4. Drop the connective word "and"
 * 5. Collapse whitespace and trim
 */
internal fun normalizeForMatching(s: String): String {
    val decomposed = Normalizer.normalize(s, Normalizer.Form.NFD)
    val stripped = decomposed.replace(Regex("\\p{Mn}+"), "")
    val lowered = stripped.lowercase()
    val ascii = lowered.replace(Regex("[^a-z0-9]+"), " ")
    val noAnd = ascii.replace(Regex("\\band\\b"), " ")
    return noAnd.replace(Regex("\\s+"), " ").trim()
}
