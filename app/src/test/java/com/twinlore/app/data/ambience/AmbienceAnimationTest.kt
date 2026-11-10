package com.twinlore.app.data.ambience

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AmbienceAnimationTest {

    @Test
    fun `the random pool holds every concrete effect and neither meta value`() {
        val pool = concreteAnimations()
        assertFalse(pool.contains(AmbienceAnimation.NONE))
        assertFalse(pool.contains(AmbienceAnimation.RANDOM))
        assertEquals(AmbienceAnimation.entries.size - 2, pool.size)
        assertTrue(pool.contains(AmbienceAnimation.SCANLINES))
    }

    @Test
    fun `an unknown saved name still reads as None at the model level`() {
        assertEquals(AmbienceAnimation.NONE, AmbienceAnimation.fromName("FLAME"))
        assertNull(AmbienceAnimation.decodeOrNull("FLAME"))
    }

    @Test
    fun `the pre-rename NOIRE spelling still decodes to Noir`() {
        assertEquals(AmbienceAnimation.NOIR, AmbienceAnimation.fromName("NOIRE"))
        assertEquals(AmbienceAnimation.NOIR, AmbienceAnimation.decodeOrNull("NOIRE"))
    }

    @Test
    fun `Noir is spelled without the trailing e everywhere the user sees it`() {
        assertEquals("Noir", AmbienceAnimation.NOIR.displayName)
    }

    @Test
    fun `the random pool drops excluded effects`() {
        val pool = randomPool(setOf(AmbienceAnimation.SAKURA.name, AmbienceAnimation.SNOW.name))
        assertFalse(pool.contains(AmbienceAnimation.SAKURA))
        assertFalse(pool.contains(AmbienceAnimation.SNOW))
        assertEquals(concreteAnimations().size - 2, pool.size)
    }

    @Test
    fun `an empty exclusion set leaves the pool whole`() {
        assertEquals(concreteAnimations(), randomPool(emptySet()))
    }

    @Test
    fun `excluding everything is ignored rather than leaving nothing to draw`() {
        val all = concreteAnimations().map { it.name }.toSet()
        assertEquals(concreteAnimations(), randomPool(all))
    }

    @Test
    fun `names that are no longer effects do not shrink the pool`() {
        assertEquals(concreteAnimations(), randomPool(setOf("FLAME", "CURATED")))
    }
}
