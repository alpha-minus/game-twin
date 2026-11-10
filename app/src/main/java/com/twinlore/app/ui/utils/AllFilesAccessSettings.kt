package com.twinlore.app.ui.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/**
 * Opens the system "All files access" screen for this app, falling back to the
 * global list when the app-specific screen is unavailable. Shared by the ES-DE Live
 * setup screen and the library's All-Files prompt.
 */
fun openAllFilesAccessSettings(context: Context) {
    val pkgUri = Uri.parse("package:${context.packageName}")
    val appSpecific = Intent(
        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, pkgUri
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(appSpecific)
    } catch (_: Exception) {
        try {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) { /* no settings activity available */ }
    }
}
