package com.twinlore.app.data.license

import com.twinlore.app.data.AppIcon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The three checkout links are indistinguishable by eye, so a copy-paste swap would quietly sell
 * the wrong edition and only show up as a support ticket. Pin each one to its tier.
 */
class LicenseConfigCheckoutTest {

    /** These are the LIVE product's share links; the test-mode product's retired ones must not
     *  come back, since a test checkout cannot take a real payment. */
    @Test
    fun `each tier maps to its own live checkout`() {
        assertEquals(
            "https://gametwin.lemonsqueezy.com/checkout/buy/5954c978-f5ac-4ece-87e0-47b85bafed92",
            LicenseConfig.checkoutUrlFor(LicenseTier.TIER1),
        )
        assertEquals(
            "https://gametwin.lemonsqueezy.com/checkout/buy/eaa9b86f-ad48-41ec-a5a5-5e5ea03ce4c7",
            LicenseConfig.checkoutUrlFor(LicenseTier.TIER2),
        )
        assertEquals(
            "https://gametwin.lemonsqueezy.com/checkout/buy/28b76c89-e801-434d-9c54-3751b3e32bb7",
            LicenseConfig.checkoutUrlFor(LicenseTier.TIER3),
        )
    }

    @Test
    fun `the three checkouts are distinct`() {
        val urls = listOf(LicenseTier.TIER1, LicenseTier.TIER2, LicenseTier.TIER3)
            .map { LicenseConfig.checkoutUrlFor(it) }
        assertEquals(3, urls.toSet().size)
    }

    @Test
    fun `NONE has nothing to sell and falls back to the default checkout`() {
        assertEquals(LicenseConfig.CHECKOUT_URL, LicenseConfig.checkoutUrlFor(LicenseTier.NONE))
    }

    @Test
    fun `the default checkout is the cheapest edition`() {
        assertEquals(LicenseConfig.TIER1_CHECKOUT_URL, LicenseConfig.CHECKOUT_URL)
    }

    /** The locked-icon dialog resolves its link this way, so every paid icon needs a real one. */
    @Test
    fun `every paid app icon resolves to an edition checkout`() {
        val paidIcons = AppIcon.entries.filter { it.requiredTier != LicenseTier.NONE }
        assertTrue("expected some paid icons", paidIcons.isNotEmpty())
        paidIcons.forEach { icon ->
            val url = LicenseConfig.checkoutUrlFor(icon.requiredTier)
            assertTrue(
                "${icon.displayName} resolves to a non-checkout url: $url",
                url.startsWith("https://gametwin.lemonsqueezy.com/checkout/buy/"),
            )
        }
    }

    @Test
    fun `paid icons across different editions do not share a checkout`() {
        val byTier = AppIcon.entries
            .filter { it.requiredTier != LicenseTier.NONE }
            .groupBy { it.requiredTier }
            .mapValues { (tier, _) -> LicenseConfig.checkoutUrlFor(tier) }
        assertEquals(byTier.size, byTier.values.toSet().size)
    }
}
