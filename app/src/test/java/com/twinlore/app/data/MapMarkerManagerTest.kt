package com.twinlore.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.twinlore.app.domain.MapMarker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MapMarkerManagerTest {

    private lateinit var context: Context
    private lateinit var manager: MapMarkerManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        manager = MapMarkerManager(context)
    }

    private fun makeMarker(id: String) = MapMarker(
        id = id,
        normalizedX = 0.5f,
        normalizedY = 0.5f,
        zoom = 1f,
        offsetX = 0f,
        offsetY = 0f,
        styleId = 1
    )

    @Test
    fun `getMarkerCounts returns empty map for empty input`() {
        val result = manager.getMarkerCounts("vault1", emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `getMarkerCounts treats missing key as absent (count 0)`() {
        // No markers stored for map-A or map-B
        val result = manager.getMarkerCounts("vault1", listOf("map-A", "map-B"))
        // Missing ids should not appear in the map; callers default absent keys to 0
        assertFalse(result.containsKey("map-A"))
        assertFalse(result.containsKey("map-B"))
        assertEquals(0, result.getOrDefault("map-A", 0))
        assertEquals(0, result.getOrDefault("map-B", 0))
    }

    @Test
    fun `getMarkerCounts matches getMarkers size across 30 ids`() {
        val vaultId = "vault-batch"
        val mapIds = (1..30).map { "map-$it" }

        // Store different counts: map-1 gets 1 marker, map-2 gets 2, ..., map-5 gets 5, rest get 0.
        for (i in 1..5) {
            val mapId = "map-$i"
            repeat(i) { idx ->
                manager.saveMarker(vaultId, makeMarker("marker-$i-$idx"), mapId)
            }
        }

        val counts = manager.getMarkerCounts(vaultId, mapIds)

        for (i in 1..5) {
            val mapId = "map-$i"
            val expected = manager.getMarkers(vaultId, mapId).size
            assertEquals(
                "count for $mapId should match getMarkers().size",
                expected,
                counts.getOrDefault(mapId, 0)
            )
        }
        // Maps 6..30 have no markers; they should be absent from the result
        for (i in 6..30) {
            val mapId = "map-$i"
            assertEquals(
                "map $mapId should have 0 markers",
                0,
                counts.getOrDefault(mapId, 0)
            )
        }
    }

    @Test
    fun `getMarkerCounts result count matches manual count for same vault and map`() {
        val vaultId = "vault-single"
        val mapId = "only-map"
        manager.saveMarker(vaultId, makeMarker("m1"), mapId)
        manager.saveMarker(vaultId, makeMarker("m2"), mapId)
        manager.saveMarker(vaultId, makeMarker("m3"), mapId)

        val counts = manager.getMarkerCounts(vaultId, listOf(mapId))
        assertEquals(3, counts.getOrDefault(mapId, 0))
        assertEquals(3, manager.getMarkers(vaultId, mapId).size)
    }
}
