package com.twinlore.app.services

import android.content.ComponentName
import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The overlay keeps its network only because this service holds the process at
 * foreground-service priority, the level battery managers and doze leave alone.
 * It does no work of its own, so the only things worth pinning are that it goes
 * foreground at once and that it comes and goes with the panel.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33, 34])
class OverlayNetworkServiceTest {

    private val app get() = RuntimeEnvironment.getApplication()

    @Test
    fun `the service is foreground from creation, before any start command`() {
        // In onCreate rather than onStartCommand: a panel opened and closed
        // within a frame stops the service before a start command would land,
        // and a promised foreground start that never happens kills the app.
        val service = Robolectric.buildService(OverlayNetworkService::class.java).create().get()
        val shadow = shadowOf(service)
        assertNotNull(shadow.lastForegroundNotification)
        assertFalse(shadow.isForegroundStopped)
    }

    @Test
    fun `start asks for the service as a foreground service`() {
        OverlayNetworkService.start(app)
        val started = shadowOf(app).nextStartedService
        assertEquals(ComponentName(app, OverlayNetworkService::class.java), started?.component)
    }

    @Test
    fun `stop stops the service`() {
        OverlayNetworkService.start(app)
        OverlayNetworkService.stop(app)
        val stopped: Intent? = shadowOf(app).nextStoppedService
        assertEquals(ComponentName(app, OverlayNetworkService::class.java), stopped?.component)
    }

    @Test
    fun `the service runs exactly as long as the panel's window exists`() {
        val host = com.twinlore.app.ui.fold.guide.FoldGuidePanelHost()
        val component = ComponentName(app, OverlayNetworkService::class.java)

        assertTrue(host.show(app) {})
        assertEquals(component, shadowOf(app).nextStartedService?.component)

        // Parking keeps the window, so it keeps the service too.
        assertTrue(host.park())
        assertEquals(null, shadowOf(app).nextStoppedService)

        host.hide()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertEquals(component, shadowOf(app).nextStoppedService?.component)
    }

    @Test
    fun `destroying the service takes it out of the foreground`() {
        val controller = Robolectric.buildService(OverlayNetworkService::class.java).create()
        controller.destroy()
        assertTrue(shadowOf(controller.get()).isForegroundStopped)
    }
}
