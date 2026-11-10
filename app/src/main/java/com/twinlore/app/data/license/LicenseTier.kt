package com.twinlore.app.data.license

/**
 * Cosmetic entitlement tier derived from the validated Lemon Squeezy license.
 * Ordinal order is load-bearing: a higher tier unlocks everything a lower one does.
 *
 * Paid tiers map one-to-one onto the store's editions: TIER1 = Gold, TIER2 = Cyber-Demon,
 * TIER3 = Noir-Adventure.
 */
enum class LicenseTier { NONE, TIER1, TIER2, TIER3 }

/** Human-readable tier name for the store/paywall UI, matching the edition the user bought. */
fun LicenseTier.tierDisplayName(): String = when (this) {
    LicenseTier.NONE -> "Free"
    LicenseTier.TIER1 -> "Gold Edition"
    LicenseTier.TIER2 -> "Cyber-Demon Edition"
    LicenseTier.TIER3 -> "Noir-Adventure Edition"
}

/**
 * Pure resolver from persisted license facts to a [LicenseTier].
 *
 * Rules (first match wins):
 *  - not valid                     -> NONE
 *  - variant in tier3VariantIds    -> TIER3
 *  - variant in tier2VariantIds    -> TIER2
 *  - variant in tier1VariantIds    -> TIER1
 *  - otherwise (valid, unmatched)  -> NONE
 *
 * Each tier takes a set of variant ids because the store sells the same edition through more
 * than one product: the original test-mode product (beta keys) and the public live product.
 *
 * Unmatched valid keys unlock nothing. This used to fail open to TIER3 during the beta, while
 * the real variant ids were still placeholders; now that they are known, an unrecognised variant
 * means a variant this build was never taught about, and guessing would hand out the full set.
 *
 * The 0L guard keeps the "response carried no variant" value [LicenseRepository] stores from
 * matching a tier whose set was misconfigured to contain 0L.
 */
fun resolveLicenseTier(
    variantId: Long?,
    isValid: Boolean,
    tier1VariantIds: Set<Long>,
    tier2VariantIds: Set<Long>,
    tier3VariantIds: Set<Long>,
): LicenseTier {
    if (!isValid) return LicenseTier.NONE
    if (variantId == null || variantId == 0L) return LicenseTier.NONE
    return when (variantId) {
        in tier3VariantIds -> LicenseTier.TIER3
        in tier2VariantIds -> LicenseTier.TIER2
        in tier1VariantIds -> LicenseTier.TIER1
        else -> LicenseTier.NONE
    }
}

/** True if an icon requiring [required] is unlocked at the [current] tier. */
fun isIconTierUnlocked(required: LicenseTier, current: LicenseTier): Boolean =
    current.ordinal >= required.ordinal
