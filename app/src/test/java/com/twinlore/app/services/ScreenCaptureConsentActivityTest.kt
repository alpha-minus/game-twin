package com.twinlore.app.services

import android.app.Application
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The trampoline that hosts the consent sheet. It lives in its own task with
 * nothing to finish it if the sheet is abandoned with Home, so the next request
 * has to clear whatever was left behind rather than stack on it: a stale sheet
 * answered later hands a fresh token to a service that already has one, and
 * that reads as a capture request nobody made.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ScreenCaptureConsentActivityTest {

    private val app: Application get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `a new consent request clears any trampoline left behind`() {
        ScreenCaptureConsentActivity.capture(app, displayId = 0, requestTag = "test")

        val started = shadowOf(app).nextStartedActivity
        assertNotNull(started)
        assertTrue(started.flags and Intent.FLAG_ACTIVITY_CLEAR_TASK != 0)
    }

    @Test
    fun `a recreated trampoline does not ask twice`() {
        val intent = Intent(app, ScreenCaptureConsentActivity::class.java)
        val controller = Robolectric.buildActivity(ScreenCaptureConsentActivity::class.java, intent).setup()
        assertNotNull("the first creation asks", shadowOf(app).nextStartedActivity)

        controller.recreate()
        assertNull("a recreation must not", shadowOf(app).nextStartedActivity)
    }
}
