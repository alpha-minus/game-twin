package com.twinlore.app.data

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The one store the app's Image tab and the Always on Top panel share for
 * where an image is and which image is open. Both surfaces read and write
 * it, so what is written by one has to come back unchanged to the other.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MapViewStateManagerTest {

    private lateinit var manager: MapViewStateManager

    @Before
    fun setUp() {
        manager = MapViewStateManager(ApplicationProvider.getApplicationContext())
    }

    @Test
    fun `the selected image is per vault and starts unset`() {
        assertNull(manager.getSelectedMapId("v1"))

        manager.saveSelectedMapId("v1", "map-a")
        manager.saveSelectedMapId("v2", "map-b")

        assertEquals("map-a", manager.getSelectedMapId("v1"))
        assertEquals("map-b", manager.getSelectedMapId("v2"))
    }

    @Test
    fun `saving null forgets the selection`() {
        manager.saveSelectedMapId("v1", "map-a")
        manager.saveSelectedMapId("v1", null)
        assertNull(manager.getSelectedMapId("v1"))
    }

    @Test
    fun `a pan is kept per image and a never-panned image reads as null`() {
        manager.saveState("v1", zoom = 2.5f, offsetX = 10f, offsetY = -4f, mapId = "map-a")

        assertEquals(MapViewState(2.5f, 10f, -4f), manager.getState("v1", "map-a"))
        assertNull(manager.getState("v1", "map-b"))
    }
}
