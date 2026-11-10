package com.twinlore.app.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Switches the launcher icon by enabling exactly one activity-alias and
 * disabling the rest. Uses DONT_KILL_APP so the running process survives.
 *
 * NOTE: the aliases are MAIN/LAUNCHER only. Game Twin is deliberately not a home
 * app, so flipping aliases cannot disturb the device's launcher association.
 * CATEGORY_HOME was removed along with the last of ES-DE Home Router mode; the
 * router trampoline itself went in 17187478.
 */
@Singleton
class AppIconManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /**
     * Whether the launcher already shows [selected]. False after an icon was
     * picked in Overlay Mode and the app has not closed since.
     */
    fun isLauncherIconApplied(selected: AppIcon): Boolean {
        val pm = context.packageManager
        return launcherAliasPlan(selected).all { (aliasClass, enabled) ->
            val state = pm.getComponentEnabledSetting(ComponentName(context.packageName, aliasClass))
            // The manifest default counts as whatever the manifest says; only
            // the default alias ships enabled.
            val effectivelyEnabled = when (state) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> aliasClass == AppIcon.DEFAULT.aliasClass
                else -> false
            }
            effectivelyEnabled == enabled
        }
    }

    fun applyLauncherIcon(selected: AppIcon) {
        val pm = context.packageManager
        launcherAliasPlan(selected).forEach { (aliasClass, enabled) ->
            val newState = if (enabled) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            val component = ComponentName(context.packageName, aliasClass)
            if (pm.getComponentEnabledSetting(component) != newState) {
                pm.setComponentEnabledSetting(
                    component,
                    newState,
                    PackageManager.DONT_KILL_APP,
                )
            }
        }
    }
}

/**
 * The order to flip the launcher aliases in: the selected one enabled FIRST,
 * every other one disabled after.
 *
 * Order is load-bearing. Disabling the outgoing alias first leaves the package
 * with no enabled MAIN/LAUNCHER component for as long as the next call takes,
 * and a launcher that rescans in that window can drop the app permanently.
 */
internal fun launcherAliasPlan(selected: AppIcon): List<Pair<String, Boolean>> =
    listOf(selected.aliasClass to true) +
        AppIcon.entries.filter { it != selected }.map { it.aliasClass to false }
