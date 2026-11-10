package com.twinlore.app

import android.app.Activity
import android.os.Bundle

/**
 * Host for the icon-pack intent filters (see AndroidManifest).
 *
 * It exists so the pack contract does not hang off [MainActivity]: some
 * icon-pack managers launch a pack's activity to preview it, and opening the
 * whole library app from an icon-pack picker would be wrong. This one bounces
 * to the app and finishes, so tapping "Game Twin" in such a list lands
 * somewhere sensible instead of on a blank screen.
 */
class IconPackActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        packageManager.getLaunchIntentForPackage(packageName)?.let { intent ->
            startActivity(intent)
        }
        finish()
    }
}
