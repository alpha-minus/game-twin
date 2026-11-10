package com.twinlore.app.ui.utils

import android.content.Context
import android.content.Intent

/**
 * Relaunches the app's launcher activity with a fresh task and kills the current
 * process. Used after operations that swap the live Room database file out from
 * under the running Hilt graph (e.g. library import) — every cached DAO and
 * repository instance still references the now-replaced database, so a clean
 * graph rebuild is the only safe path forward.
 */
fun restartApp(context: Context) {
    val pm = context.packageManager
    val intent = pm.getLaunchIntentForPackage(context.packageName)?.apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    }
    if (intent != null) {
        context.startActivity(intent)
    }
    Runtime.getRuntime().exit(0)
}
