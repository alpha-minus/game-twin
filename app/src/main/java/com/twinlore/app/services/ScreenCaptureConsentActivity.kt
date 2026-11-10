package com.twinlore.app.services

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.view.Display
import java.io.File

/**
 * The one thing an overlay window cannot do for itself.
 *
 * `createScreenCaptureIntent()` has to be launched for a result, and only an
 * Activity can do that: a Service has no `onActivityResult` and neither has a
 * TYPE_APPLICATION_OVERLAY window. The fold panel is an overlay, so a screenshot
 * from it needs an activity somewhere - but it emphatically does not need *the
 * app*, which would take the whole screen off a running game to ask one
 * question.
 *
 * So this: no layout, no theme of its own beyond being translucent, alive only
 * long enough for the system's consent sheet to appear over whatever is already
 * on screen. It hands the token to [ScreenCaptureService] and finishes, and the
 * panel is still exactly where it was.
 *
 * It is only needed once per projection. [ScreenCaptureService] keeps the
 * projection alive, so later shots go straight to `ACTION_CAPTURE` and this
 * never runs. Android 15 stops a projection when the device locks, which is
 * when it runs again.
 */
class ScreenCaptureConsentActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Only on a first creation. A recreation - a rotation, a fold, a
        // configuration change behind the sheet - already has one sheet up,
        // and asking again would stack a second over it whose result the
        // first activity instance would never see.
        if (savedInstanceState != null) return
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        runCatching {
            startActivityForResult(manager.createScreenCaptureIntent(), REQUEST_CONSENT)
        }.onFailure { finish() }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_CONSENT) {
            finish()
            return
        }
        if (resultCode == RESULT_OK && data != null) {
            val service = Intent(this, ScreenCaptureService::class.java).apply {
                putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, resultCode)
                putExtra(ScreenCaptureService.EXTRA_DATA, data)
                putExtra(ScreenCaptureService.EXTRA_SAVE_DIR, intent.getStringExtra(EXTRA_SAVE_DIR))
                putExtra(
                    ScreenCaptureService.EXTRA_DISPLAY_ID,
                    intent.getIntExtra(EXTRA_DISPLAY_ID, Display.DEFAULT_DISPLAY),
                )
                putExtra(
                    ScreenCaptureService.EXTRA_REQUEST_TAG,
                    intent.getStringExtra(EXTRA_REQUEST_TAG),
                )
            }
            runCatching { startForegroundService(service) }
        }
        // Either way. A refused consent is an answer, not an error, and there is
        // nothing for this activity to say about it: the panel is still up.
        finish()
        // No animation, or the trampoline announces itself as a screen that
        // came and went over the game.
        overridePendingTransition(0, 0)
    }

    companion object {
        private const val REQUEST_CONSENT = 4801

        const val EXTRA_SAVE_DIR = "consent.saveDir"
        const val EXTRA_DISPLAY_ID = "consent.displayId"
        const val EXTRA_REQUEST_TAG = "consent.requestTag"

        /**
         * Takes a screenshot, asking for consent first only if there is no live
         * projection to take one with.
         *
         * [displayId] is the display to capture, which on a two-screen device is
         * not a constant: the caller passes the one it is actually on.
         */
        fun capture(context: Context, displayId: Int, requestTag: String) {
            val saveDir = File(context.cacheDir, "screenshots").absolutePath
            if (ScreenCaptureService.isRunning) {
                val service = Intent(context, ScreenCaptureService::class.java).apply {
                    action = ScreenCaptureService.ACTION_CAPTURE
                    putExtra(ScreenCaptureService.EXTRA_SAVE_DIR, saveDir)
                    putExtra(ScreenCaptureService.EXTRA_DISPLAY_ID, displayId)
                    putExtra(ScreenCaptureService.EXTRA_REQUEST_TAG, requestTag)
                }
                runCatching { context.startForegroundService(service) }
                return
            }
            // NEW_TASK because the caller is an overlay window with no task of
            // its own to start an activity in.
            //
            // CLEAR_TASK because nothing else finishes a trampoline whose sheet
            // was abandoned with Home: it sits in this hidden task, and without
            // this the next request stacks a second sheet on top of the stale
            // one. Answering the stale one later would hand a fresh token to a
            // service that already holds a projection, which reads it as a
            // capture request and files a screenshot nobody asked for.
            val consent = Intent(context, ScreenCaptureConsentActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                putExtra(EXTRA_SAVE_DIR, saveDir)
                putExtra(EXTRA_DISPLAY_ID, displayId)
                putExtra(EXTRA_REQUEST_TAG, requestTag)
            }
            runCatching { context.startActivity(consent) }
        }
    }
}
