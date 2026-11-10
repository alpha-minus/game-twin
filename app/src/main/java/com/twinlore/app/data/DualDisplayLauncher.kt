package com.twinlore.app.data

import android.app.ActivityOptions
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Helper for launching activities on the secondary display when the device has one.
 * Extracted from [GameLauncher] so other surfaces (e.g. the recommended-apps catalog)
 * can route their outbound activities through the same logic.
 */
@Singleton
class DualDisplayLauncher @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /**
     * Returns the displayId of the *other* display, or [currentDisplayId]
     * itself when the device has only one display.
     */
    fun findOtherDisplayId(currentDisplayId: Int): Int {
        val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        return displayManager.displays.firstOrNull { it.displayId != currentDisplayId }?.displayId
            ?: currentDisplayId
    }

    /**
     * [ActivityOptions] with `launchDisplayId` set to the other display when one exists.
     * Falls back to default options on single-display devices.
     */
    fun buildLaunchOptions(currentDisplayId: Int): ActivityOptions {
        val options = ActivityOptions.makeBasic()
        val targetDisplayId = findOtherDisplayId(currentDisplayId)
        if (targetDisplayId != currentDisplayId) {
            options.launchDisplayId = targetDisplayId
            Log.d(TAG, "Targeting display $targetDisplayId (from $currentDisplayId)")
        }
        return options
    }

    /**
     * Convenience: fires `ACTION_VIEW` with [intent] on the other display. Adds
     * NEW_TASK + LAUNCH_ADJACENT flags. Returns false when no activity resolves.
     */
    fun startActivityOnOtherDisplay(intent: Intent, currentDisplayId: Int): Boolean {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT)
        return try {
            val options = buildLaunchOptions(currentDisplayId)
            context.startActivity(intent, options.toBundle())
            true
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "No activity resolves: ${intent.data}", e)
            false
        }
    }

    companion object {
        private const val TAG = "DualDisplayLauncher"
    }
}
