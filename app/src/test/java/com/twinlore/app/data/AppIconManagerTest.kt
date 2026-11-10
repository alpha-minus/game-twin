package com.twinlore.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The order the aliases are flipped in, which is the whole of the risk here.
 *
 * Disabling the outgoing alias before enabling the incoming one leaves the
 * package with zero enabled MAIN/LAUNCHER components. A launcher or frontend
 * that rescans on PACKAGE_CHANGED inside that window sees an app with no
 * launcher entry and can drop it for good, which no later flip undoes.
 */
class AppIconManagerTest {

    @Test
    fun the_selected_alias_is_enabled_before_anything_is_disabled() {
        val plan = launcherAliasPlan(AppIcon.GOLD)
        assertEquals(AppIcon.GOLD.aliasClass to true, plan.first())
    }

    @Test
    fun every_other_alias_is_disabled() {
        val plan = launcherAliasPlan(AppIcon.GOLD)
        val rest = plan.drop(1)
        assertEquals(AppIcon.entries.size - 1, rest.size)
        assertTrue("something other than GOLD was left enabled", rest.none { it.second })
        assertEquals(
            AppIcon.entries.filter { it != AppIcon.GOLD }.map { it.aliasClass }.toSet(),
            rest.map { it.first }.toSet(),
        )
    }

    @Test
    fun the_plan_covers_every_alias_exactly_once() {
        // Checked for every AppIcon, not just GOLD: an implementation that special-cased
        // GOLD's ordering would otherwise still pass. The selected alias must be enabled
        // FIRST for every possible selection, because a plan that enables it last (or
        // anywhere but first) leaves the package with zero enabled MAIN/LAUNCHER
        // components in between, which a rescanning launcher can treat as an app with no
        // launcher entry and drop for good.
        AppIcon.entries.forEach { selected ->
            val plan = launcherAliasPlan(selected)
            assertEquals(AppIcon.entries.size, plan.size)
            assertEquals(AppIcon.entries.size, plan.map { it.first }.toSet().size)
            assertEquals(selected.aliasClass to true, plan.first())
            assertTrue(
                "something other than $selected was left enabled",
                plan.drop(1).none { it.second },
            )
        }
    }
}
