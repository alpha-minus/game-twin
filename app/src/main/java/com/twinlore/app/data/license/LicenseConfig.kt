package com.twinlore.app.data.license

/**
 * Identity of the Lemon Squeezy store, product and variants this build sells.
 *
 * These are public identifiers, safe to ship: license activation and validation go through
 * Lemon Squeezy's unauthenticated License API, so the app never carries an account API key.
 * (An API key is an account-wide admin credential and must never be embedded in the APK.)
 *
 * [LicenseManager] rejects any key whose activation response reports a different store or
 * product, so these two constants are the security boundary; the variant ids below only decide
 * which cosmetic tier a valid key unlocks.
 */
object LicenseConfig {
    /**
     * Lemon Squeezy store "GameTwin" (gametwin.lemonsqueezy.com).
     *
     * Keys issued by the earlier TwinLore store (272819) are deliberately NOT accepted: the
     * cutover to this store was a clean break, so an old key fails activation with a store
     * mismatch rather than silently granting cosmetics.
     */
    const val STORE_ID: Long = 433353L

    /**
     * Product "Game Twin: A save state for everything around the game", created while the
     * store ran in test mode. Beta-era activations hold keys for this product, so it stays
     * accepted alongside [LIVE_PRODUCT_ID].
     */
    const val PRODUCT_ID: Long = 1228315L

    /** The public product the store sells since going live with 1.0. Same three editions. */
    const val LIVE_PRODUCT_ID: Long = 1311060L

    /** Every product whose keys activation accepts. [LicenseManager] checks against this. */
    val ACCEPTED_PRODUCT_IDS: Set<Long> = setOf(PRODUCT_ID, LIVE_PRODUCT_ID)

    /**
     * Variant ids for the three editions, one per product ([PRODUCT_ID] test-mode era and
     * [LIVE_PRODUCT_ID] live). Each unlocks its own logo set through [resolveLicenseTier] and
     * [com.twinlore.app.data.AppIcon]:
     *
     *  - Gold Edition (EUR 4.59)          -> TIER1 -> Gold
     *  - Cyber-Demon Edition (EUR 8.99)   -> TIER2 -> Cyber, Demonic
     *  - Noir-Adventure Edition (EUR 14.99) -> TIER3 -> Adventure, Noir
     *
     * A valid key for any other variant unlocks nothing, so a variant added in the dashboard
     * needs a matching entry here (and an app update) before it sells.
     */
    const val TIER1_VARIANT_ID: Long = 1920423L  // Gold Edition, test-mode product
    const val TIER2_VARIANT_ID: Long = 1920429L  // Cyber-Demon Edition, test-mode product
    const val TIER3_VARIANT_ID: Long = 1920433L  // Noir-Adventure Edition, test-mode product

    const val LIVE_TIER1_VARIANT_ID: Long = 2050246L  // Gold Edition, live product
    const val LIVE_TIER2_VARIANT_ID: Long = 2050252L  // Cyber-Demon Edition, live product
    const val LIVE_TIER3_VARIANT_ID: Long = 2050253L  // Noir-Adventure Edition, live product

    val TIER1_VARIANT_IDS: Set<Long> = setOf(TIER1_VARIANT_ID, LIVE_TIER1_VARIANT_ID)
    val TIER2_VARIANT_IDS: Set<Long> = setOf(TIER2_VARIANT_ID, LIVE_TIER2_VARIANT_ID)
    val TIER3_VARIANT_IDS: Set<Long> = setOf(TIER3_VARIANT_ID, LIVE_TIER3_VARIANT_ID)

    // The checkout links belong to the LIVE product ([LIVE_PRODUCT_ID]); the test-mode
    // product's links stayed behind when the store went public and cannot take a real payment.

    /** Direct checkout for the Gold Edition (TIER1). */
    const val TIER1_CHECKOUT_URL: String =
        "https://gametwin.lemonsqueezy.com/checkout/buy/5954c978-f5ac-4ece-87e0-47b85bafed92"

    /** Direct checkout for the Cyber-Demon Edition (TIER2). */
    const val TIER2_CHECKOUT_URL: String =
        "https://gametwin.lemonsqueezy.com/checkout/buy/eaa9b86f-ad48-41ec-a5a5-5e5ea03ce4c7"

    /** Direct checkout for the Noir-Adventure Edition (TIER3). */
    const val TIER3_CHECKOUT_URL: String =
        "https://gametwin.lemonsqueezy.com/checkout/buy/28b76c89-e801-434d-9c54-3751b3e32bb7"

    /**
     * Default checkout when no edition is implied (the "Chip in" row, the paywall's Buy button):
     * the cheapest way in. Every other entry point that knows which edition the user is after
     * should use [checkoutUrlFor] instead of this.
     */
    const val CHECKOUT_URL: String = TIER1_CHECKOUT_URL

    /**
     * Checkout for the edition that grants [tier]. [LicenseTier.NONE] has nothing to sell, so it
     * falls back to the default checkout rather than inventing a link.
     */
    fun checkoutUrlFor(tier: LicenseTier): String = when (tier) {
        LicenseTier.TIER1 -> TIER1_CHECKOUT_URL
        LicenseTier.TIER2 -> TIER2_CHECKOUT_URL
        LicenseTier.TIER3 -> TIER3_CHECKOUT_URL
        LicenseTier.NONE -> CHECKOUT_URL
    }

    /**
     * Store front page. No call site links here any more: every support and chip-in entry point
     * now opens a checkout directly. Kept as the store's identity, and for any future "browse
     * the editions" link.
     */
    const val SUPPORT_DEV_URL: String = "https://gametwin.lemonsqueezy.com"
}
