package com.twinlore.app.data.license

import com.twinlore.app.data.AppIcon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LicenseTierTest {

    @Test
    fun `no valid license resolves to NONE`() {
        val t = resolveLicenseTier(variantId = 5L, isValid = false,
            tier1VariantIds = setOf(10L), tier2VariantIds = setOf(20L), tier3VariantIds = setOf(30L))
        assertEquals(LicenseTier.NONE, t)
    }

    @Test
    fun `tier1 variant resolves to TIER1`() {
        val t = resolveLicenseTier(variantId = 10L, isValid = true,
            tier1VariantIds = setOf(10L), tier2VariantIds = setOf(20L), tier3VariantIds = setOf(30L))
        assertEquals(LicenseTier.TIER1, t)
    }

    @Test
    fun `tier2 variant resolves to TIER2`() {
        val t = resolveLicenseTier(variantId = 20L, isValid = true,
            tier1VariantIds = setOf(10L), tier2VariantIds = setOf(20L), tier3VariantIds = setOf(30L))
        assertEquals(LicenseTier.TIER2, t)
    }

    @Test
    fun `tier3 variant resolves to TIER3`() {
        val t = resolveLicenseTier(variantId = 30L, isValid = true,
            tier1VariantIds = setOf(10L), tier2VariantIds = setOf(20L), tier3VariantIds = setOf(30L))
        assertEquals(LicenseTier.TIER3, t)
    }

    @Test
    fun `valid license with unmatched variant unlocks nothing`() {
        val t = resolveLicenseTier(variantId = 777L, isValid = true,
            tier1VariantIds = setOf(10L), tier2VariantIds = setOf(20L), tier3VariantIds = setOf(30L))
        assertEquals(LicenseTier.NONE, t)
    }

    @Test
    fun `missing variant does not collide with an unconfigured tier id`() {
        // LicenseRepository stores 0L when a response carries no variant. A tier left at 0L
        // must not swallow that and hand out cosmetics.
        assertEquals(
            LicenseTier.NONE,
            resolveLicenseTier(variantId = 0L, isValid = true,
                tier1VariantIds = setOf(0L), tier2VariantIds = setOf(0L), tier3VariantIds = setOf(0L)),
        )
        assertEquals(
            LicenseTier.NONE,
            resolveLicenseTier(variantId = null, isValid = true,
                tier1VariantIds = setOf(10L), tier2VariantIds = setOf(20L), tier3VariantIds = setOf(30L)),
        )
    }

    @Test
    fun `entitlement compares ordinals across three tiers`() {
        assertTrue(isIconTierUnlocked(required = LicenseTier.NONE, current = LicenseTier.NONE))
        assertTrue(isIconTierUnlocked(required = LicenseTier.TIER1, current = LicenseTier.TIER2))
        assertTrue(isIconTierUnlocked(required = LicenseTier.TIER3, current = LicenseTier.TIER3))
        assertFalse(isIconTierUnlocked(required = LicenseTier.TIER1, current = LicenseTier.NONE))
        assertFalse(isIconTierUnlocked(required = LicenseTier.TIER3, current = LicenseTier.TIER2))
    }

    // --- The shipped store configuration -------------------------------------------------
    //
    // These pin the live Lemon Squeezy ids to the logo sets they are sold as. A wrong id here
    // ships as "I paid for Noir and got nothing", which no test above would catch.

    private fun tierFor(variantId: Long) = resolveLicenseTier(
        variantId = variantId,
        isValid = true,
        tier1VariantIds = LicenseConfig.TIER1_VARIANT_IDS,
        tier2VariantIds = LicenseConfig.TIER2_VARIANT_IDS,
        tier3VariantIds = LicenseConfig.TIER3_VARIANT_IDS,
    )

    @Test
    fun `Gold Edition unlocks the Gold logo set only`() {
        val tier = tierFor(1920423L)
        assertEquals(LicenseTier.TIER1, tier)
        assertTrue(AppIcon.GOLD.isUnlockedBy(tier))
        assertFalse(AppIcon.CYBER.isUnlockedBy(tier))
        assertFalse(AppIcon.NOIR.isUnlockedBy(tier))
    }

    @Test
    fun `Cyber-Demon Edition unlocks Cyber and Demonic on top of Gold`() {
        val tier = tierFor(1920429L)
        assertEquals(LicenseTier.TIER2, tier)
        assertTrue(AppIcon.GOLD.isUnlockedBy(tier))
        assertTrue(AppIcon.CYBER.isUnlockedBy(tier))
        assertTrue(AppIcon.DEMONIC.isUnlockedBy(tier))
        assertFalse(AppIcon.ADVENTURE.isUnlockedBy(tier))
        assertFalse(AppIcon.NOIR.isUnlockedBy(tier))
    }

    @Test
    fun `Noir-Adventure Edition unlocks every logo set`() {
        val tier = tierFor(1920433L)
        assertEquals(LicenseTier.TIER3, tier)
        assertTrue(AppIcon.entries.all { it.isUnlockedBy(tier) })
    }

    @Test
    fun `the retired store's variant unlocks nothing`() {
        // TwinLore product 771963, variant 1215594. The cutover to the GameTwin store was a
        // clean break: activation rejects the key on store id, and even if one were stored
        // from before, it resolves to no cosmetics.
        assertEquals(LicenseTier.NONE, tierFor(1215594L))
    }

    /**
     * The store sells through two products: the original one, created while the store ran in
     * test mode (beta activations hold its keys), and the public product that went live with
     * 1.0. Same editions, different variant ids; both must resolve to the same tiers.
     */
    @Test
    fun `live product variants resolve to the same tiers as the test-mode ones`() {
        assertEquals(LicenseTier.TIER1, tierFor(2050246L)) // Gold Edition, live
        assertEquals(LicenseTier.TIER2, tierFor(2050252L)) // Cyber-Demon Edition, live
        assertEquals(LicenseTier.TIER3, tierFor(2050253L)) // Noir-Adventure Edition, live
    }

    @Test
    fun `activation accepts both the test-mode and the live product`() {
        assertTrue(LicenseConfig.PRODUCT_ID in LicenseConfig.ACCEPTED_PRODUCT_IDS)
        assertTrue(LicenseConfig.LIVE_PRODUCT_ID in LicenseConfig.ACCEPTED_PRODUCT_IDS)
        assertEquals(1311060L, LicenseConfig.LIVE_PRODUCT_ID)
        assertEquals(2, LicenseConfig.ACCEPTED_PRODUCT_IDS.size)
    }

    @Test
    fun `every paid icon is reachable from some edition`() {
        val reachable = listOf(tierFor(1920423L), tierFor(1920429L), tierFor(1920433L))
        for (icon in AppIcon.entries.filter { it.requiredTier != LicenseTier.NONE }) {
            assertTrue(
                "${icon.displayName} is sold by no edition",
                reachable.any { icon.isUnlockedBy(it) },
            )
        }
    }

    @Test
    fun `tier names match the editions the store sells`() {
        assertEquals("Free", LicenseTier.NONE.tierDisplayName())
        assertEquals("Gold Edition", LicenseTier.TIER1.tierDisplayName())
        assertEquals("Cyber-Demon Edition", LicenseTier.TIER2.tierDisplayName())
        assertEquals("Noir-Adventure Edition", LicenseTier.TIER3.tierDisplayName())
    }
}
