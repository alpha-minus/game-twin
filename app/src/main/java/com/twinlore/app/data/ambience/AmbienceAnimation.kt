package com.twinlore.app.data.ambience

/**
 * The ambient animations drawn over a backdrop (the Journey player and its exports, the Game
 * Stats screen). Colours take the chosen accent into account.
 */
enum class AmbienceAnimation(val displayName: String) {
    NONE("None"),
    /** Resolved at render time to a random concrete animation (re-rolled per game/system). */
    RANDOM("Random"),
    // Declaration order IS the order of the pickers, which all build from `entries`. These two
    // lead because they are the ones people reach for. Nothing persists an ordinal — every saved
    // value is the constant NAME — so this list can be reordered freely.
    FIREFLIES("Fireflies"),
    SAKURA("Sakura"),
    CYBER("Rain"),
    RUNE("Rune"),
    NOIR("Noir"),
    PIXEL("Pixel"),
    WARP("Warp"),
    GLOW("Soft Glow"),
    BONFIRE("Bonfire"),
    AURA("Clouds"),
    SNOW("Snow"),
    STARFIELD("Starfield"),
    SCANLINES("Scanlines"),
    BEAM("Beam"),
    GRID("Neon Grid"),
    ;

    companion object {
        /**
         * Names this enum used to store. [NOIR] was spelled `NOIRE` until the rename, and saved
         * settings and per-scope assignments all persist the constant name, so
         * without this every existing pick would silently read as something else.
         */
        private val LEGACY_NAMES = mapOf("NOIRE" to NOIR)

        /** The animation for a stored name, or null if it matches nothing (current or legacy). */
        fun decodeOrNull(name: String?): AmbienceAnimation? =
            entries.firstOrNull { it.name == name } ?: LEGACY_NAMES[name]

        fun fromName(name: String?): AmbienceAnimation = decodeOrNull(name) ?: NONE
    }
}

/**
 * The effects [AmbienceAnimation.RANDOM] may resolve to: everything except None and
 * Random itself.
 *
 * Lives here rather than in the composable so the pool can be asserted without
 * Compose, and so the overlay and its hosts read the same list.
 */
fun concreteAnimations(): List<AmbienceAnimation> =
    AmbienceAnimation.entries.filter { it != AmbienceAnimation.NONE && it != AmbienceAnimation.RANDOM }

/**
 * The effects Random may actually pick, with the user's excluded names removed.
 *
 * An exclusion set that would leave nothing to pick is ignored rather than obeyed: Random with
 * an empty pool can only draw a blank, which reads as the app being broken. Unknown names in
 * [excluded] are ignored too, so a set saved when an effect existed does not shrink the pool
 * after that effect is removed.
 */
fun randomPool(excluded: Set<String>): List<AmbienceAnimation> {
    val pool = concreteAnimations().filterNot { it.name in excluded }
    return pool.ifEmpty { concreteAnimations() }
}
