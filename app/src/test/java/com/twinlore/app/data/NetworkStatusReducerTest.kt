package com.twinlore.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkStatusReducerTest {

    private val wifi = NetworkStatus(hasInternet = true, isWifi = true, signalStrength = -55)
    private val cell = NetworkStatus(hasInternet = true, isWifi = false, signalStrength = NetworkStatus.SIGNAL_UNKNOWN)

    @Test
    fun `starts offline`() {
        assertEquals(NetworkStatus.OFFLINE, NetworkStatusReducer<String>().status)
    }

    @Test
    fun `available then capabilities is online`() {
        val r = NetworkStatusReducer<String>()
        r.onAvailable("A")
        assertEquals(wifi, r.onCapabilitiesChanged("A", wifi))
    }

    @Test
    fun `old network lost after new one became default stays online`() {
        // The wake case: Wi-Fi reassociates as a new network before the old
        // one's loss is delivered.
        val r = NetworkStatusReducer<String>()
        r.onCapabilitiesChanged("A", wifi)
        r.onAvailable("B")
        r.onCapabilitiesChanged("B", wifi)
        val after = r.onLost("A")
        assertTrue(after.hasInternet)
        assertEquals(wifi, after)
    }

    @Test
    fun `losing the default goes offline`() {
        val r = NetworkStatusReducer<String>()
        r.onCapabilitiesChanged("A", wifi)
        assertFalse(r.onLost("A").hasInternet)
    }

    @Test
    fun `lost on sleep then back on wake comes online again`() {
        val r = NetworkStatusReducer<String>()
        r.onCapabilitiesChanged("A", wifi)
        r.onLost("A")
        r.onAvailable("B")
        assertTrue(r.onCapabilitiesChanged("B", wifi).hasInternet)
    }

    @Test
    fun `capabilities without onAvailable still make the network current`() {
        val r = NetworkStatusReducer<String>()
        assertTrue(r.onCapabilitiesChanged("A", wifi).hasInternet)
    }

    @Test
    fun `capability updates replace the earlier ones`() {
        // Internet capability arriving after the network does, and the signal
        // moving, both come in as capability changes.
        val r = NetworkStatusReducer<String>()
        r.onAvailable("A")
        assertFalse(r.onCapabilitiesChanged("A", wifi.copy(hasInternet = false)).hasInternet)
        assertEquals(wifi, r.onCapabilitiesChanged("A", wifi))
        assertEquals(-70, r.onCapabilitiesChanged("A", wifi.copy(signalStrength = -70)).signalStrength)
    }

    @Test
    fun `switching default from wifi to cell reads the new network`() {
        val r = NetworkStatusReducer<String>()
        r.onCapabilitiesChanged("W", wifi)
        r.onAvailable("C")
        assertEquals(cell, r.onCapabilitiesChanged("C", cell))
        assertEquals(cell, r.onLost("W"))
    }

    @Test
    fun `reset seeds from the system and drops stale networks`() {
        val r = NetworkStatusReducer<String>()
        r.onCapabilitiesChanged("A", wifi)
        assertEquals(cell, r.reset("C", cell))
        // A's loss arriving after the reset does nothing.
        assertEquals(cell, r.onLost("A"))
    }

    @Test
    fun `reset with no default network is offline until a callback says otherwise`() {
        val r = NetworkStatusReducer<String>()
        r.onCapabilitiesChanged("A", wifi)
        assertEquals(NetworkStatus.OFFLINE, r.reset(null, null))
        r.onAvailable("B")
        assertEquals(wifi, r.onCapabilitiesChanged("B", wifi))
    }

    @Test
    fun `library bar shows no bars off Wi-Fi or offline, three for an unreported signal`() {
        assertEquals(-1, com.twinlore.app.ui.components.wifiSignalLevel(NetworkStatus.OFFLINE))
        assertEquals(-1, com.twinlore.app.ui.components.wifiSignalLevel(cell))
        assertEquals(-1, com.twinlore.app.ui.components.wifiSignalLevel(wifi.copy(hasInternet = false)))
        assertEquals(
            3,
            com.twinlore.app.ui.components.wifiSignalLevel(wifi.copy(signalStrength = NetworkStatus.SIGNAL_UNKNOWN)),
        )
    }
}
